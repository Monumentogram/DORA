package com.monumentogram.dora.poc.recovery.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryQuarantineIntentTest {
    private val runId = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
    private val sourceSha = Sha256Value.fromLowercaseHex("11".repeat(32))

    @Test
    fun `intent identity has independent golden digest and canonical destination`() {
        val input =
            RecoveryQuarantineIntentInput(
                RecoveryCandidate.MICROFILE,
                runId,
                "units/u-0000000001.ct.tmp",
                RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                160_028UL,
                sourceSha,
            )
        val intent = RecoveryQuarantineIntent.calculate(input)
        assertEquals(
            "e0e34c361172d1851270810a9b98be959282224227ec836d5a1887f5d4c7e84a",
            intent.toLowercaseHex(),
        )
        assertEquals(
            "objects/q-${intent.toLowercaseHex()}.bin",
            RecoveryQuarantineIntent.destination(input),
        )
    }

    @Test
    fun `observed state is evidence and cannot fork stable intent`() {
        val input =
            RecoveryQuarantineIntentInput(
                RecoveryCandidate.MICROFILE,
                runId,
                "units/u-0000000001.ct.tmp",
                RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                1UL,
                sourceSha,
            )
        val first = RecoveryQuarantineIntent.calculate(input)
        val second = RecoveryQuarantineIntent.calculate(input)
        assertEquals(first, second)
        assertNotEquals(first, RecoveryQuarantineIntent.calculate(input.copy(sourceBytes = 2UL)))
    }

    @Test
    fun `quarantine roles are closed over their candidate family`() {
        RecoveryQuarantineIntentInput(
            RecoveryCandidate.STREAM,
            runId,
            "stream/stream.ct.tmp",
            RecoveryQuarantineArtifactRole.STREAM_CIPHERTEXT,
            1UL,
            sourceSha,
        )
        assertThrows(RecoveryContractException::class.java) {
            RecoveryQuarantineIntentInput(
                RecoveryCandidate.STREAM,
                runId,
                "units/u-0000000001.ct.tmp",
                RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                1UL,
                sourceSha,
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryQuarantineIntentInput(
                RecoveryCandidate.MICROFILE,
                runId,
                "stream/stream.ct.tmp",
                RecoveryQuarantineArtifactRole.STREAM_CIPHERTEXT,
                1UL,
                sourceSha,
            )
        }
    }
}
