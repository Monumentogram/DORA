@file:Suppress("LongParameterList", "LongMethod", "MagicNumber", "TooGenericExceptionCaught")

package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapPublicationCapability
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpoint
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpointCodec
import com.monumentogram.dora.poc.recovery.contract.RecoveryContract
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingMath
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamingAad
import com.monumentogram.dora.poc.recovery.coordination.ProcessRecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.coordination.RecoveryRunWriterLease
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAead
import com.monumentogram.dora.poc.recovery.crypto.RecoveryTinkRuntime
import java.io.OutputStream

/**
 * Calls the existing journal through a transaction-observing adapter. Observations are made at
 * actual public transaction returns, not inferred from a row readback after an ambiguous commit.
 */
internal fun interface RecoveryStreamingCheckpointCommit {
    fun publish(
        row: RecoveryStreamingCheckpointRow,
        beforeEnd: () -> Unit,
        afterEnd: () -> Unit,
    ): RecoveryStreamingJournalResult
}

/**
 * PoC append-only SSET/SCHK writer. It never resumes a keyset or an existing stream. close()
 * abandons the public encrypting stream and closes only its raw destination; it does not emit
 * Tink's final segment. A host SIGKILL remains an external operation, not this close method.
 */
internal class RecoveryStreamingPublicationWriter
private constructor(
    private val confirmation: KeyConfirmationValue,
    private val storage: RecoveryCandidateStorage,
    private val runAead: RecoveryRunAead,
    private val hashPrefix: (ULong) -> Sha256Value,
    private val commitCheckpoint: RecoveryStreamingCheckpointCommit,
    private val observer: (String) -> Unit,
    private val lease: RecoveryRunWriterLease,
) : AutoCloseable {
    private val runId = confirmation.runId
    private var destination: CandidateWriteHandle? = null
    private lateinit var encrypting: OutputStream
    private lateinit var streamEnvelope: ByteArray
    private var ciphertextBytes = 0UL
    private var previous: RecoveryStreamingCheckpointRow? = null
    private var usable = true
    private var closed = false
    private val ownerThread = Thread.currentThread()

    var acceptedEnd: ULong = 0UL
        private set

    val emittedCiphertextBytes: ULong
        get() = ciphertextBytes

    private fun setup() {
        val keyset = RecoveryTinkRuntime.newStreamingKeyset(streamEnvelopeAad(runId))
        streamEnvelope = keyset.serializeEncrypted(runAead)
        publishImmutable("key-envelopes/stream.ks", streamEnvelope, "SSET", 1)
        val handle = storage.openExclusiveTemp(runId, "stream/stream.ct.tmp")
        destination = handle
        encrypting =
            keyset.newEncryptingStream(
                object : OutputStream() {
                    override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

                    override fun write(bytes: ByteArray, offset: Int, count: Int) {
                        check(usable && !closed)
                        observer("CIPHERTEXT-BEFORE-WRITE")
                        writeFully(handle, bytes, offset, count)
                        ciphertextBytes += count.toULong()
                        observer("CIPHERTEXT-WRITE-RETURN")
                    }

                    // Tink close is deliberately never called by this writer.
                    override fun close() =
                        error("Campaign writer must not finalize Tink implicitly")
                },
                StreamingAad(RecoveryCandidate.STREAM, runId),
            )
        check(ciphertextBytes in 1UL..4_095UL) { "Unexpected initial public Tink output" }
        observer("SSET-05")
        storage.fsync(handle)
        observer("SSET-06")
        check(!storage.finalExists(runId, "stream/stream.ct"))
        storage.renameTempToFinal(runId, "stream/stream.ct.tmp", "stream/stream.ct")
        observer("SSET-07")
        storage.fsyncParent(runId, "stream/stream.ct")
        observer("SSET-08")
        observer("SSET-09")
    }

    fun write(plaintext: ByteArray) {
        requireOwnerAndUsable()
        require(plaintext.size in 1..4_080) {
            "Streaming writer calls must be bounded to 4080 bytes"
        }
        require(
            acceptedEnd + plaintext.size.toULong() <= RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN
        )
        attempt {
            observer("PLAINTEXT-CALL-START")
            encrypting.write(plaintext)
            // A excludes an in-progress call and is acknowledged before any checkpoint is begun.
            acceptedEnd += plaintext.size.toULong()
            observer("PLAINTEXT-CALL-RETURN")
        }
    }

    fun checkpoint(): RecoveryStreamingCheckpointRow {
        requireOwnerAndUsable()
        return attempt {
            storage.fsync(requireNotNull(destination))
            observer("SCHK-01")
            val q = ciphertextBytes / 4_096UL
            check(q == 0UL || ciphertextBytes == q * 4_096UL) { "Nonfinal segment is incomplete" }
            val prefixBytes = RecoveryStreamingMath.ciphertextPrefixBytes(q)
            val committed = RecoveryStreamingMath.recoveredEndExclusive(q)
            check(committed <= acceptedEnd)
            val prefixHash = hashPrefix(prefixBytes)
            val generation = (previous?.generation ?: 0UL) + 1UL
            val previousHash = previous?.checkpointSha256 ?: Sha256Value.ZERO
            val keyset =
                RecoveryTinkRuntime.newAeadKeyset(
                    checkpointEnvelopeAad(runId, generation, committed, previousHash)
                )
            val envelope = keyset.serializeEncrypted(runAead)
            observer("SCHK-02")
            val envelopeName = RecoveryRelativeNames.checkpointKeyEnvelope(generation)
            publishImmutable(envelopeName, envelope, "SCHK", 3)
            val checkpoint =
                RecoveryCheckpoint(
                    RecoveryCandidate.STREAM,
                    runId,
                    generation,
                    previousHash,
                    q,
                    prefixBytes,
                    committed,
                    streamEnvelope.size.toULong(),
                    Sha256Value.calculate(streamEnvelope),
                    "stream/stream.ct",
                    "key-envelopes/stream.ks",
                )
            val ciphertext =
                keyset.encryptPublication(
                    RecoveryCheckpointCodec.encode(checkpoint),
                    checkpointAad(runId, generation, q, committed, previousHash),
                )
            val checkpointName = RecoveryRelativeNames.checkpointCiphertext(generation)
            publishImmutable(checkpointName, ciphertext, "SCHK", 7)
            val identity =
                RecoveryStreamingCheckpointIdentityInput(
                    runId,
                    generation,
                    q,
                    prefixBytes,
                    prefixHash,
                    committed,
                    checkpointName,
                    ciphertext.size.toULong(),
                    Sha256Value.calculate(ciphertext),
                    envelopeName,
                    envelope.size.toULong(),
                    Sha256Value.calculate(envelope),
                    "stream/stream.ct",
                    "key-envelopes/stream.ks",
                    streamEnvelope.size.toULong(),
                    Sha256Value.calculate(streamEnvelope),
                    previousHash,
                )
            val row = checkpointRow(identity)
            var insertedObserved = false
            var endedObserved = false
            val result =
                commitCheckpoint.publish(
                    row,
                    {
                        check(!insertedObserved && !endedObserved)
                        insertedObserved = true
                        observer("SCHK-11")
                    },
                    {
                        check(insertedObserved && !endedObserved)
                        endedObserved = true
                        observer("SCHK-12")
                    },
                )
            check(
                insertedObserved &&
                    endedObserved &&
                    result is RecoveryStreamingJournalResult.CheckpointReceipt &&
                    !result.replayed &&
                    result.checkpointIdentity == row.checkpointIdentity
            ) {
                "Streaming checkpoint publication did not prove successful endTransaction and exact readback"
            }
            previous = row
            observer("SCHK-13")
            row
        }
    }

    // Cleanup is thrown only after success; a primary failure retains close as suppressed.
    @Suppress("ThrowingExceptionFromFinally")
    private fun publishImmutable(name: String, bytes: ByteArray, prefix: String, firstStep: Int) {
        val handle = storage.openExclusiveTemp(runId, "$name.tmp")
        var failure: Throwable? = null
        try {
            writeFully(handle, bytes, 0, bytes.size)
            observer(step(prefix, firstStep))
            storage.fsync(handle)
            observer(step(prefix, firstStep + 1))
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
                storage.close(handle)
            } catch (error: Throwable) {
                if (failure != null) failure.addSuppressed(error) else throw error
            }
        }
        check(!storage.finalExists(runId, name)) { "Immutable publication collision" }
        storage.renameTempToFinal(runId, "$name.tmp", name)
        observer(step(prefix, firstStep + 2))
        storage.fsyncParent(runId, name)
        observer(step(prefix, firstStep + 3))
    }

    private fun writeFully(
        handle: CandidateWriteHandle,
        bytes: ByteArray,
        offset: Int,
        count: Int,
    ) {
        var done = 0
        while (done < count) {
            val written = storage.write(handle, bytes, offset + done, count - done)
            check(written in 1..(count - done)) { "Invalid publication write progress" }
            done += written
        }
    }

    private fun requireOwnerAndUsable() {
        check(Thread.currentThread() === ownerThread) { "Publication writer is thread confined" }
        check(usable && !closed) { "Publication session is closed or failed" }
    }

    private fun <T> attempt(operation: () -> T): T =
        try {
            operation()
        } catch (failure: Throwable) {
            usable = false
            throw failure
        }

    override fun close() {
        check(Thread.currentThread() === ownerThread)
        if (closed) return
        closed = true
        usable = false
        try {
            destination?.let(storage::close)
        } finally {
            lease.close()
        }
    }

    companion object {
        fun open(
            confirmation: KeyConfirmationValue,
            capability: BootstrapPublicationCapability,
            storage: RecoveryCandidateStorage,
            openRunAead: (RunId) -> RecoveryRunAead,
            hashPrefix: (ULong) -> Sha256Value,
            commitCheckpoint: RecoveryStreamingCheckpointCommit,
            observer: (String) -> Unit = {},
        ): RecoveryStreamingPublicationWriter {
            require(
                confirmation.candidate == RecoveryCandidate.STREAM &&
                    capability.authorizes(confirmation)
            )
            val lease =
                checkNotNull(ProcessRecoveryRunSingleWriterGuard.tryAcquire(confirmation.runId))
            val writer =
                try {
                    RecoveryStreamingPublicationWriter(
                        confirmation,
                        storage,
                        openRunAead(confirmation.runId),
                        hashPrefix,
                        commitCheckpoint,
                        observer,
                        lease,
                    )
                } catch (failure: Throwable) {
                    lease.close()
                    throw failure
                }
            try {
                writer.attempt { writer.setup() }
                return writer
            } catch (failure: Throwable) {
                try {
                    writer.close()
                } catch (close: Throwable) {
                    failure.addSuppressed(close)
                }
                throw failure
            }
        }

        private fun step(prefix: String, number: Int) =
            "$prefix-${number.toString().padStart(2, '0')}"

        internal fun streamEnvelopeAad(runId: RunId) =
            KeyEnvelopeAad(
                RecoveryCandidate.STREAM,
                runId,
                KeyEnvelopeTargetKind.STREAM,
                1UL,
                KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                0UL,
                RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN,
                0UL,
                Sha256Value.ZERO,
            )

        internal fun checkpointEnvelopeAad(
            runId: RunId,
            generation: ULong,
            committed: ULong,
            previous: Sha256Value,
        ) =
            KeyEnvelopeAad(
                RecoveryCandidate.STREAM,
                runId,
                KeyEnvelopeTargetKind.CHECKPOINT,
                generation,
                KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                0UL,
                committed,
                0UL,
                previous,
            )

        internal fun checkpointAad(
            runId: RunId,
            generation: ULong,
            q: ULong,
            committed: ULong,
            previous: Sha256Value,
        ) =
            PublicationAad(
                RecoveryCandidate.STREAM,
                runId,
                PublicationKind.CHECKPOINT,
                generation,
                if (committed == 0UL) PublicationAad.EMPTY_TERMINAL_UNIT_INDEX else q - 1UL,
                committed,
                previous,
            )

        private fun checkpointRow(i: RecoveryStreamingCheckpointIdentityInput) =
            RecoveryStreamingCheckpointRow(
                i.runId,
                i.generation,
                i.durableNonFinalSegmentCount,
                i.streamCiphertextPrefixBytes,
                i.streamCiphertextPrefixSha256,
                i.committedEnd,
                i.checkpointRelativeName,
                i.checkpointBytes,
                i.checkpointSha256,
                i.checkpointEnvelopeRelativeName,
                i.checkpointEnvelopeBytes,
                i.checkpointEnvelopeSha256,
                i.streamRelativeName,
                i.streamEnvelopeRelativeName,
                i.streamEnvelopeBytes,
                i.streamEnvelopeSha256,
                i.previousCheckpointSha256,
                RecoveryStreamingIdentity.checkpoint(i),
            )
    }
}
