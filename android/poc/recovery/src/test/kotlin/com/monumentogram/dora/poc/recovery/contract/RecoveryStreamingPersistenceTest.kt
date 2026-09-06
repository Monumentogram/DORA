@file:Suppress("LongMethod")

package com.monumentogram.dora.poc.recovery.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryStreamingPersistenceTest {
    @Test
    fun `checkpoint oracle snapshot and witness identities match independent K12 vectors`() {
        val runId = RunId.fromBytes(ByteArray(16) { it.toByte() })
        val oracle = ByteArray(8_137) { ((17 * it + 3) and 0xff).toByte() }
        val source = ByteArray(8_192) { ((29 * it + 7) and 0xff).toByte() }
        val checkpoint =
            RecoveryStreamingCheckpointIdentityInput(
                runId = runId,
                generation = 1UL,
                durableNonFinalSegmentCount = 2UL,
                streamCiphertextPrefixBytes = 8_192UL,
                streamCiphertextPrefixSha256 = Sha256Value.calculate(source),
                committedEnd = 4_056UL,
                checkpointRelativeName = "checkpoints/g-00000000000000000001.ct",
                checkpointBytes = 123UL,
                checkpointSha256 = Sha256Value.calculate("checkpoint-ciphertext".toByteArray()),
                checkpointEnvelopeRelativeName =
                    "key-envelopes/checkpoint-g-00000000000000000001.ks",
                checkpointEnvelopeBytes = 77UL,
                checkpointEnvelopeSha256 =
                    Sha256Value.calculate("checkpoint-envelope".toByteArray()),
                streamRelativeName = "stream/stream.ct",
                streamEnvelopeRelativeName = "key-envelopes/stream.ks",
                streamEnvelopeBytes = 88UL,
                streamEnvelopeSha256 = Sha256Value.calculate("stream-envelope".toByteArray()),
                previousCheckpointSha256 = Sha256Value.ZERO,
            )
        val checkpointIdentity = RecoveryStreamingIdentity.checkpoint(checkpoint)
        assertEquals(
            "a12456a5049063cb101139fe8d978b1f8f3eef59270879bd6266ce4026589549",
            checkpointIdentity.toLowercaseHex(),
        )
        val oracleSha = Sha256Value.calculate(oracle)
        val oracleIdentity = RecoveryStreamingIdentity.oracle(8_137UL, oracleSha, runId)
        assertEquals(
            "f0739c0b3ada861b9143a237916b7bdc684afc0e6b6740b26d9bae20c8e22145",
            oracleIdentity.toLowercaseHex(),
        )
        val witnessWithoutSnapshot =
            RecoveryStreamingWitnessInput(
                runId = runId,
                checkpointGeneration = 1UL,
                checkpointIdentity = checkpointIdentity,
                checkpointPrefixBytes = 8_192UL,
                checkpointContextEnd = 4_056UL,
                oracleIdentitySha256 = oracleIdentity,
                acceptedEnd = 8_137UL,
                oraclePlaintextSha256 = oracleSha,
                preFaultSourceBytes = 8_192UL,
                preFaultSourceSha256 = Sha256Value.calculate(source),
                controllerSnapshotSha256 = null,
            )
        val snapshot = RecoveryStreamingIdentity.controllerSnapshot(witnessWithoutSnapshot)
        assertEquals(
            "bfd41fb93f2a04230891e502101bdcaf480d1a8ef79b1edcded8b85a5f2620f5",
            snapshot.toLowercaseHex(),
        )
        val witness =
            RecoveryStreamingIdentity.witness(
                witnessWithoutSnapshot.copy(controllerSnapshotSha256 = snapshot)
            )
        assertEquals(
            "82406fdcf3536f816668e764c4209af35cc0fc96bb7ea1d41b6bb9a8e2fb9302",
            witness.toLowercaseHex(),
        )
    }

    @Test
    fun `migration digest has independent unicode golden`() {
        val row = migrationRow(intent = sha256("intent"))

        assertEquals(
            "b4322d69cd23f10f5cfabb160addd5ff15668082b6d34f4c5f0a662d95b1be01",
            RecoveryStreamingMigration.digest(listOf(row)).toHex(),
        )
    }

    @Test
    fun `migration rows sort by unsigned SQLite blob order`() {
        val low = migrationRow(intent = byteArrayOf(0x00) + ByteArray(31))
        val high = migrationRow(intent = byteArrayOf(0xff.toByte()) + ByteArray(31))

        assertEquals(
            RecoveryStreamingMigration.digest(listOf(low, high)).toHex(),
            RecoveryStreamingMigration.digest(listOf(high, low)).toHex(),
        )
        assertNotEquals(
            RecoveryStreamingMigration.digest(listOf(low)).toHex(),
            RecoveryStreamingMigration.digest(listOf(high)).toHex(),
        )
    }

    @Test
    fun `sqlite text requires its exact canonical UTF eight bytes`() {
        assertEquals(
            "units/тест.ct",
            CanonicalSqliteText.of(
                    "units/тест.ct",
                    "units/тест.ct".toByteArray(Charsets.UTF_8),
                    512,
                )
                .value,
        )
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalSqliteText.of("/", byteArrayOf(0xc0.toByte(), 0xaf.toByte()), 512)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalSqliteText.of("a\u0000b", byteArrayOf(0x61, 0x00, 0x62), 512)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalSqliteText.of("é", "é".toByteArray(Charsets.UTF_8), 1)
        }
    }

    @Test
    fun `stream boundary and admission preserve exact precedence and tail gate`() {
        assertEquals(
            StreamBoundaryDerivation.Exact(0UL),
            RecoveryStreamingRules.deriveBoundary(0UL, 0UL),
        )
        assertEquals(
            StreamBoundaryDerivation.Exact(4_096UL),
            RecoveryStreamingRules.deriveBoundary(4_056UL, 4_096UL),
        )
        assertEquals(
            StreamBoundaryDerivation.Exact(8_192UL),
            RecoveryStreamingRules.deriveBoundary(8_136UL, 8_192UL),
        )
        assertEquals(
            StreamBoundaryDerivation.NonCanonicalCandidateEnd,
            RecoveryStreamingRules.deriveBoundary(4_057UL, 8_192UL),
        )
        assertEquals(
            StreamBoundaryDerivation.ExceedsObservedSource(8_192UL),
            RecoveryStreamingRules.deriveBoundary(8_136UL, 8_191UL),
        )

        val valid =
            RecoveryStreamingRules.classifyPostIntersection(
                committedEnd = 4_056UL,
                acceptedEnd = 16_296UL,
                candidateEnd = 8_136UL,
                oracleEqual = true,
                terminal = StreamTerminal.AUTHENTICATION_FAILURE,
                observedEnd = 8_193UL,
            )
        assertEquals(StreamDecision.VALID, valid.decision)
        assertEquals(8_192UL, valid.requiredRangeStart)
        assertEquals(8_193UL, valid.requiredRangeEnd)

        val tail =
            RecoveryStreamingRules.classifyPostIntersection(
                committedEnd = 0UL,
                acceptedEnd = 8_161UL,
                candidateEnd = 0UL,
                oracleEqual = true,
                terminal = StreamTerminal.AUTHENTICATION_FAILURE,
                observedEnd = 0UL,
            )
        assertEquals(StreamDecision.REJECTED, tail.decision)
        assertEquals(StreamDiagnosticClassification.STREAM_TAIL_BOUND_EXCEEDED, tail.classification)
        assertEquals(null, tail.requiredRangeStart)

        val mismatchBeforeBelowC =
            RecoveryStreamingRules.classifyPostIntersection(
                committedEnd = 4_056UL,
                acceptedEnd = 4_056UL,
                candidateEnd = 1UL,
                oracleEqual = false,
                terminal = StreamTerminal.COMPLETED_READ_REJECTED,
                observedEnd = 1UL,
            )
        assertEquals(
            StreamDiagnosticClassification.STREAM_RETURNED_BYTE_ORACLE_MISMATCH,
            mismatchBeforeBelowC.classification,
        )
        assertEquals(StreamDecision.FATAL, mismatchBeforeBelowC.decision)
        assertEquals(
            StreamRangeCertainty.CONSERVATIVE_WHOLE_SOURCE,
            mismatchBeforeBelowC.requiredRangeCertainty,
        )
        val zeroCheckpointBoundary =
            RecoveryStreamingRules.classifyPostIntersection(
                committedEnd = 0UL,
                acceptedEnd = 5_000UL,
                candidateEnd = 4_057UL,
                oracleEqual = true,
                terminal = StreamTerminal.AUTHENTICATION_FAILURE,
                observedEnd = 8_192UL,
            )
        assertEquals(StreamDecision.FATAL, zeroCheckpointBoundary.decision)
        assertEquals(0UL, zeroCheckpointBoundary.requiredRangeStart)
        assertEquals(
            StreamRangeCertainty.CONSERVATIVE_PROVEN_CHECKPOINT_SUPERSET,
            zeroCheckpointBoundary.requiredRangeCertainty,
        )
    }

    @Test
    fun `source extent permits truncation but bounds nonnegative append`() {
        RecoveryStreamingRules.validateExtent(8_137UL, 8_192UL, 8_191UL)
        RecoveryStreamingRules.validateExtent(8_137UL, 115_654_656UL, 115_662_848UL)
        assertThrows(RecoveryContractException::class.java) {
            RecoveryStreamingRules.validateExtent(8_137UL, 115_654_656UL, 115_662_849UL)
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryStreamingRules.validateExtent(8_137UL, 8_192UL, 16_385UL)
        }
    }

    private fun migrationRow(intent: ByteArray) =
        RecoveryQuarantineMigrationRow(
            intentId = intent,
            runId = text("00010203-0405-0607-0809-0a0b0c0d0e0f", 64),
            candidateId = text("REC-MICROFILE-TINK", 64),
            bootstrapBinding = text("ABSENT", 16),
            bootstrapRunId = null,
            bootstrapCandidateId = null,
            artifactRole = text("UNKNOWN_REGULAR", 64),
            observedState = text("FINAL_ORPHAN", 64),
            sourceRelativeName = text("units/тест.ct", 512),
            destinationRelativeName = text("objects/q-test.bin", 512),
            sourceBytes = 0,
            sourceSha256 = sha256(""),
            state = text("PENDING", 16),
        )

    private fun text(value: String, maximum: Int) =
        CanonicalSqliteText.of(value, value.toByteArray(Charsets.UTF_8), maximum)

    private fun sha256(value: String): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
