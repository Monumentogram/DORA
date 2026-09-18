package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryMicrofileReferencedIntentTest {
    @Test
    @Suppress("NestedBlockDepth")
    fun `referenced disposition requires microfile bootstrap and one of four roles`() {
        for (name in listOf("REFERENCED_REJECTED", "REFERENCED_DEPENDENT")) {
            val state = RecoveryQuarantineObservedState.entries.find { it.name == name }
            assertNotNull("Approved disposition is unavailable: $name", state)
            for (role in RecoveryQuarantineArtifactRole.entries) {
                val candidate =
                    if (role.name.startsWith("STREAM") || role.name.startsWith("CHECKPOINT"))
                        RecoveryCandidate.STREAM
                    else RecoveryCandidate.MICROFILE
                val input =
                    RecoveryQuarantineIntentInput(
                        candidate,
                        RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff"),
                        "units/u-0000000001.ct",
                        role,
                        3UL,
                        Sha256Value.calculate(byteArrayOf(1, 2, 3)),
                    )
                for (binding in QuarantineBootstrapBinding.entries) {
                    val allowed =
                        binding == QuarantineBootstrapBinding.PRESENT &&
                            role.name in
                                setOf(
                                    "MICROFILE_CIPHERTEXT",
                                    "MICROFILE_KEY_ENVELOPE",
                                    "MANIFEST_CIPHERTEXT",
                                    "MANIFEST_KEY_ENVELOPE",
                                )
                    val create = {
                        RecoveryQuarantineIntentRow(
                            RecoveryQuarantineIntent.calculate(input),
                            input,
                            requireNotNull(state),
                            binding,
                            RecoveryQuarantineIntent.destination(input),
                            QuarantineIntentState.PENDING,
                        )
                    }
                    if (allowed) assertEquals(name, create().recordedObservedState.name)
                    else assertThrows(IllegalArgumentException::class.java) { create() }
                }
            }
        }
    }
}
