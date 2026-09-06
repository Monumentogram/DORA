@file:Suppress("LongMethod")

package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpoint
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpointCodec
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamingAad
import com.monumentogram.dora.poc.recovery.crypto.RecordingRunAeadBackend
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.crypto.RecoveryTinkRuntime
import com.monumentogram.dora.poc.recovery.storage.RecoveryArtifactAccessException
import com.monumentogram.dora.poc.recovery.storage.RecoveryOpenedStreamingSource
import com.monumentogram.dora.poc.recovery.storage.RecoveryUnsafePathException
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryStreamingTinkPrerequisiteCryptoTest {
    @Test
    fun `Android prerequisite source maps storage failures without artifact data`() {
        val runId = RunId.fromBytes(ByteArray(16))
        val expected =
            RecoveryArtifactBytes("checkpoints/g-00000000000000000001.ct", byteArrayOf(1))
        val successful = AndroidRecoveryStreamingPrerequisiteSource { _, _, _ -> expected }
        assertTrue(
            successful.load(
                runId,
                expected.relativeName,
                RecoveryStreamingPrerequisiteArtifactKind.CHECKPOINT_CIPHERTEXT,
            ) === expected
        )

        val unsafe =
            assertThrows(RecoveryStreamingPrerequisiteSourceException::class.java) {
                AndroidRecoveryStreamingPrerequisiteSource { _, _, _ ->
                        throw RecoveryUnsafePathException("private")
                    }
                    .load(
                        runId,
                        expected.relativeName,
                        RecoveryStreamingPrerequisiteArtifactKind.CHECKPOINT_CIPHERTEXT,
                    )
            }
        assertEquals(RecoveryStreamingPrerequisiteSourceFailure.UNSAFE_PATH, unsafe.failure)

        val operational =
            assertThrows(RecoveryStreamingPrerequisiteSourceException::class.java) {
                AndroidRecoveryStreamingPrerequisiteSource { _, _, _ ->
                        throw RecoveryArtifactAccessException(
                            RecoveryArtifactPresence.PRESENT,
                            structural = false,
                            IllegalStateException("private"),
                        )
                    }
                    .load(
                        runId,
                        expected.relativeName,
                        RecoveryStreamingPrerequisiteArtifactKind.CHECKPOINT_CIPHERTEXT,
                    )
            }
        assertEquals(RecoveryStreamingPrerequisiteSourceFailure.OPERATIONAL, operational.failure)
    }

    @Test
    fun `tink prerequisite authenticates exact checkpoint and exposes descriptor stream`() {
        val fixture = tinkFixture()
        val crypto = RecoveryStreamingTinkPrerequisiteCrypto(fixture.runProvider::openExisting)

        val authentication =
            crypto.authenticate(
                fixture.row,
                fixture.checkpointEnvelope,
                fixture.checkpointCiphertext,
                fixture.streamEnvelope,
            ) as RecoveryStreamingCheckpointAuthentication.Ready
        val read =
            authentication.publicStreamOpener.open(
                opened(fixture.streamCiphertext),
                fixture.witness,
            )

        assertArrayEquals(fixture.plaintext, read.use { it.readAll() })
    }

    @Test
    fun `tink prerequisite rejects ciphertext auth and structural plaintext mismatch`() {
        val fixture = tinkFixture()
        val crypto = RecoveryStreamingTinkPrerequisiteCrypto(fixture.runProvider::openExisting)

        val tampered =
            fixture.checkpointCiphertext.copyOf().also {
                it[it.lastIndex] = (it.last() + 1).toByte()
            }
        assertTrue(
            crypto.authenticate(
                fixture.row,
                fixture.checkpointEnvelope,
                tampered,
                fixture.streamEnvelope,
            ) === RecoveryStreamingCheckpointAuthentication.Rejected
        )

        val mismatchFixture = tinkFixture(checkpointEnvelopeBytesDelta = 1UL)
        val mismatchCrypto =
            RecoveryStreamingTinkPrerequisiteCrypto(mismatchFixture.runProvider::openExisting)
        assertTrue(
            mismatchCrypto.authenticate(
                mismatchFixture.row,
                mismatchFixture.checkpointEnvelope,
                mismatchFixture.checkpointCiphertext,
                mismatchFixture.streamEnvelope,
            ) === RecoveryStreamingCheckpointAuthentication.Structural
        )
    }

    private fun RecoveryStreamingIntentBuilder.RecoveryStreamingPublicRead.readAll(): ByteArray {
        val output = ByteArrayOutputStream()
        var request = 4_056
        while (true) {
            val buffer = ByteArray(request)
            val count = read(buffer, 0, buffer.size)
            if (count == -1) return output.toByteArray()
            output.write(buffer, 0, count)
            request = 4_080
        }
    }

    private fun opened(bytes: ByteArray) =
        object : RecoveryOpenedStreamingSource {
            override val observedBytes = bytes.size.toULong()

            override fun sha256Prefix(endExclusive: ULong) =
                Sha256Value.calculate(bytes.copyOfRange(0, endExclusive.toInt()))

            override fun sha256Range(startInclusive: ULong, endExclusive: ULong) =
                Sha256Value.calculate(
                    bytes.copyOfRange(startInclusive.toInt(), endExclusive.toInt())
                )

            override fun boundedInputStream() = bytes.inputStream()
        }

    private fun tinkFixture(checkpointEnvelopeBytesDelta: ULong = 0UL): TinkFixture {
        val runId = RunId.fromBytes(ByteArray(16) { (it + 7).toByte() })
        val backend = RecordingRunAeadBackend()
        val provider = RecoveryRunAeadProvider(backend)
        val runAead = provider.createNew(runId)
        val streamAad =
            KeyEnvelopeAad(
                RecoveryCandidate.STREAM,
                runId,
                KeyEnvelopeTargetKind.STREAM,
                1UL,
                KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                0UL,
                115_200_000UL,
                0UL,
                Sha256Value.ZERO,
            )
        val streamKeyset = RecoveryTinkRuntime.newStreamingKeyset(streamAad)
        val streamEnvelope = streamKeyset.serializeEncrypted(runAead)
        val plaintext = ByteArray(8_136) { ((it * 13 + 3) and 0xff).toByte() }
        val streamDestination = ByteArrayOutputStream()
        streamKeyset
            .newEncryptingStream(
                streamDestination,
                StreamingAad(RecoveryCandidate.STREAM, runId),
            )
            .use { it.write(plaintext) }
        val streamCiphertext = streamDestination.toByteArray()
        val checkpoint =
            RecoveryCheckpoint(
                RecoveryCandidate.STREAM,
                runId,
                1UL,
                Sha256Value.ZERO,
                2UL,
                8_192UL,
                4_056UL,
                streamEnvelope.size.toULong() + checkpointEnvelopeBytesDelta,
                Sha256Value.calculate(streamEnvelope),
                "stream/stream.ct",
                "key-envelopes/stream.ks",
            )
        val checkpointEnvelopeAad =
            KeyEnvelopeAad(
                RecoveryCandidate.STREAM,
                runId,
                KeyEnvelopeTargetKind.CHECKPOINT,
                1UL,
                KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                0UL,
                4_056UL,
                0UL,
                Sha256Value.ZERO,
            )
        val checkpointKeyset = RecoveryTinkRuntime.newAeadKeyset(checkpointEnvelopeAad)
        val checkpointEnvelope = checkpointKeyset.serializeEncrypted(runAead)
        val checkpointPublicationAad =
            PublicationAad(
                RecoveryCandidate.STREAM,
                runId,
                PublicationKind.CHECKPOINT,
                1UL,
                1UL,
                4_056UL,
                Sha256Value.ZERO,
            )
        val checkpointCiphertext =
            checkpointKeyset.encryptPublication(
                RecoveryCheckpointCodec.encode(checkpoint),
                checkpointPublicationAad,
            )
        val input =
            RecoveryStreamingCheckpointIdentityInput(
                runId,
                1UL,
                2UL,
                8_192UL,
                Sha256Value.calculate(streamCiphertext.copyOfRange(0, 8_192)),
                4_056UL,
                "checkpoints/g-00000000000000000001.ct",
                checkpointCiphertext.size.toULong(),
                Sha256Value.calculate(checkpointCiphertext),
                "key-envelopes/checkpoint-g-00000000000000000001.ks",
                checkpointEnvelope.size.toULong(),
                Sha256Value.calculate(checkpointEnvelope),
                "stream/stream.ct",
                "key-envelopes/stream.ks",
                streamEnvelope.size.toULong(),
                Sha256Value.calculate(streamEnvelope),
                Sha256Value.ZERO,
            )
        val row =
            RecoveryStreamingCheckpointRow(
                input.runId,
                input.generation,
                input.durableNonFinalSegmentCount,
                input.streamCiphertextPrefixBytes,
                input.streamCiphertextPrefixSha256,
                input.committedEnd,
                input.checkpointRelativeName,
                input.checkpointBytes,
                input.checkpointSha256,
                input.checkpointEnvelopeRelativeName,
                input.checkpointEnvelopeBytes,
                input.checkpointEnvelopeSha256,
                input.streamRelativeName,
                input.streamEnvelopeRelativeName,
                input.streamEnvelopeBytes,
                input.streamEnvelopeSha256,
                input.previousCheckpointSha256,
                RecoveryStreamingIdentity.checkpoint(input),
            )
        return TinkFixture(
            provider,
            row,
            checkpointEnvelope,
            checkpointCiphertext,
            streamEnvelope,
            streamCiphertext,
            plaintext,
            controllerFixtureFor(row, plaintext),
        )
    }

    private fun controllerFixtureFor(
        row: RecoveryStreamingCheckpointRow,
        plaintext: ByteArray,
    ) =
        com.monumentogram.dora.poc.recovery.contract
            .RecoveryStreamingWitnessInput(
                row.runId,
                row.generation,
                row.checkpointIdentity,
                row.streamCiphertextPrefixBytes,
                row.committedEnd,
                RecoveryStreamingIdentity.oracle(
                    plaintext.size.toULong(),
                    Sha256Value.calculate(plaintext),
                    row.runId,
                ),
                plaintext.size.toULong(),
                Sha256Value.calculate(plaintext),
                row.streamCiphertextPrefixBytes,
                row.streamCiphertextPrefixSha256,
                null,
            )
            .let {
                it.copy(controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(it))
            }

    private data class TinkFixture(
        val runProvider: RecoveryRunAeadProvider,
        val row: RecoveryStreamingCheckpointRow,
        val checkpointEnvelope: ByteArray,
        val checkpointCiphertext: ByteArray,
        val streamEnvelope: ByteArray,
        val streamCiphertext: ByteArray,
        val plaintext: ByteArray,
        val witness: com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput,
    )
}
