package com.monumentogram.dora.poc.recovery.candidate

import android.content.Context
import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapPublicationCapability
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpoint
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpointCodec
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingMath
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamingAad
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.crypto.RecoveryTinkRuntime
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryJournalDatabase
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryStreamingJournal
import com.monumentogram.dora.poc.recovery.journal.AndroidSqliteRecoveryStreamingJournalDatabase
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryCandidateStorage
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Test-only ordinary finalization fixture. The campaign's kill writer intentionally cannot do this.
 */
internal object RecoveryCampaignNormalStream {
    @Suppress("LongMethod", "MagicNumber")
    fun publish(
        context: Context,
        confirmation: KeyConfirmationValue,
        capability: BootstrapPublicationCapability,
        plaintext: ByteArray,
    ): RecoveryStreamingCheckpointRow {
        require(
            confirmation.candidate == RecoveryCandidate.STREAM &&
                capability.authorizes(confirmation)
        )
        require(plaintext.size == 480000)
        val run = confirmation.runId
        val storage = AndroidOsRecoveryCandidateStorage(context)
        val runAead = RecoveryRunAeadProvider().openExisting(run)
        val streamKeyset =
            RecoveryTinkRuntime.newStreamingKeyset(
                RecoveryStreamingPublicationWriter.streamEnvelopeAad(run)
            )
        val streamEnvelope = streamKeyset.serializeEncrypted(runAead)
        publishImmutable(storage, run, "key-envelopes/stream.ks", streamEnvelope)

        val handle = storage.openExclusiveTemp(run, "stream/stream.ct.tmp")
        var emitted = 0UL
        var beforeFinal = 0UL
        try {
            val destination =
                object : OutputStream() {
                    override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

                    override fun write(bytes: ByteArray, offset: Int, count: Int) {
                        var done = 0
                        while (done < count) {
                            val written = storage.write(handle, bytes, offset + done, count - done)
                            check(written in 1..(count - done))
                            done += written
                        }
                        emitted += count.toULong()
                    }

                    override fun close() = Unit // The storage handle stays open through fsync.
                }
            streamKeyset
                .newEncryptingStream(destination, StreamingAad(RecoveryCandidate.STREAM, run))
                .use {
                    var offset = 0
                    while (offset < plaintext.size) {
                        val count = minOf(4080, plaintext.size - offset)
                        it.write(plaintext, offset, count)
                        offset += count
                    }
                    beforeFinal = emitted
                }
            check(emitted > beforeFinal) { "Tink final segment was not emitted" }
            storage.fsync(handle)
        } finally {
            storage.close(handle)
        }
        storage.renameTempToFinal(run, "stream/stream.ct.tmp", "stream/stream.ct")
        storage.fsyncParent(run, "stream/stream.ct")

        // Only full segments emitted before close count as durable non-final segments.
        val q = beforeFinal / RecoveryStreamingMath.CIPHERTEXT_SEGMENT_BYTES
        check(beforeFinal == q * RecoveryStreamingMath.CIPHERTEXT_SEGMENT_BYTES)
        val prefixBytes = RecoveryStreamingMath.ciphertextPrefixBytes(q)
        val committed = RecoveryStreamingMath.recoveredEndExclusive(q)
        check(q >= 2UL && committed < plaintext.size.toULong())
        check(
            plaintext.size.toULong() - committed <= RecoveryStreamingMath.MAXIMUM_BOUNDED_TAIL_BYTES
        )
        val streamFile =
            File(
                context.noBackupFilesDir,
                "poc-recovery/v1/runs/${run.toCanonicalString()}/stream/stream.ct",
            )
        check(streamFile.length().toULong() == emitted)
        val prefixHash = MessageDigest.getInstance("SHA-256")
        streamFile.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var remaining = prefixBytes.toLong()
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                check(count > 0)
                prefixHash.update(buffer, 0, count)
                remaining -= count
            }
        }

        val generation = 1UL
        val previous = Sha256Value.ZERO
        val checkpointKeyset =
            RecoveryTinkRuntime.newAeadKeyset(
                RecoveryStreamingPublicationWriter.checkpointEnvelopeAad(
                    run,
                    generation,
                    committed,
                    previous,
                )
            )
        val checkpointEnvelope = checkpointKeyset.serializeEncrypted(runAead)
        val envelopeName = RecoveryRelativeNames.checkpointKeyEnvelope(generation)
        publishImmutable(storage, run, envelopeName, checkpointEnvelope)
        val checkpoint =
            RecoveryCheckpoint(
                RecoveryCandidate.STREAM,
                run,
                generation,
                previous,
                q,
                prefixBytes,
                committed,
                streamEnvelope.size.toULong(),
                Sha256Value.calculate(streamEnvelope),
                "stream/stream.ct",
                "key-envelopes/stream.ks",
            )
        val ciphertext =
            checkpointKeyset.encryptPublication(
                RecoveryCheckpointCodec.encode(checkpoint),
                RecoveryStreamingPublicationWriter.checkpointAad(
                    run,
                    generation,
                    q,
                    committed,
                    previous,
                ),
            )
        val checkpointName = RecoveryRelativeNames.checkpointCiphertext(generation)
        publishImmutable(storage, run, checkpointName, ciphertext)
        val identity =
            RecoveryStreamingCheckpointIdentityInput(
                run,
                generation,
                q,
                prefixBytes,
                Sha256Value.fromBytes(prefixHash.digest()),
                committed,
                checkpointName,
                ciphertext.size.toULong(),
                Sha256Value.calculate(ciphertext),
                envelopeName,
                checkpointEnvelope.size.toULong(),
                Sha256Value.calculate(checkpointEnvelope),
                "stream/stream.ct",
                "key-envelopes/stream.ks",
                streamEnvelope.size.toULong(),
                Sha256Value.calculate(streamEnvelope),
                previous,
            )
        val row =
            RecoveryStreamingCheckpointRow(
                identity.runId,
                identity.generation,
                identity.durableNonFinalSegmentCount,
                identity.streamCiphertextPrefixBytes,
                identity.streamCiphertextPrefixSha256,
                identity.committedEnd,
                identity.checkpointRelativeName,
                identity.checkpointBytes,
                identity.checkpointSha256,
                identity.checkpointEnvelopeRelativeName,
                identity.checkpointEnvelopeBytes,
                identity.checkpointEnvelopeSha256,
                identity.streamRelativeName,
                identity.streamEnvelopeRelativeName,
                identity.streamEnvelopeBytes,
                identity.streamEnvelopeSha256,
                identity.previousCheckpointSha256,
                RecoveryStreamingIdentity.checkpoint(identity),
            )
        val journal =
            AndroidRecoveryStreamingJournal(
                AndroidSqliteRecoveryStreamingJournalDatabase(
                    AndroidRecoveryJournalDatabase.writable(context)
                )
            )
        val result = journal.insertCheckpoint(row)
        check(
            result is RecoveryStreamingJournalResult.CheckpointReceipt &&
                !result.replayed &&
                result.checkpointIdentity == row.checkpointIdentity
        )
        return row
    }

    private fun publishImmutable(
        storage: AndroidOsRecoveryCandidateStorage,
        run: com.monumentogram.dora.poc.recovery.contract.RunId,
        name: String,
        bytes: ByteArray,
    ) {
        val handle = storage.openExclusiveTemp(run, "$name.tmp")
        try {
            var offset = 0
            while (offset < bytes.size) {
                val written = storage.write(handle, bytes, offset, bytes.size - offset)
                check(written in 1..(bytes.size - offset))
                offset += written
            }
            storage.fsync(handle)
        } finally {
            storage.close(handle)
        }
        storage.renameTempToFinal(run, "$name.tmp", name)
        storage.fsyncParent(run, name)
    }
}
