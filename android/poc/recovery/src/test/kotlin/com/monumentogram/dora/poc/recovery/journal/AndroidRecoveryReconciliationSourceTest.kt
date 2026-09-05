package com.monumentogram.dora.poc.recovery.journal

import com.monumentogram.dora.poc.recovery.candidate.AndroidRecoveryMicrofileReconciliation
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineEvidenceSink
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AndroidRecoveryReconciliationSourceTest {
    @Test
    fun `inventory names receive all exact active artifact roles`() {
        val cases =
            mapOf(
                "key-confirmation/run.kc" to RecoveryQuarantineArtifactRole.KEY_CONFIRMATION,
                "units/u-0.bin" to RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                "manifests/m-1.bin" to RecoveryQuarantineArtifactRole.MANIFEST_CIPHERTEXT,
                "key-envelopes/manifest-1.bin" to
                    RecoveryQuarantineArtifactRole.MANIFEST_KEY_ENVELOPE,
                "key-envelopes/unit-0.bin" to RecoveryQuarantineArtifactRole.MICROFILE_KEY_ENVELOPE,
                "unknown.bin" to RecoveryQuarantineArtifactRole.UNKNOWN_REGULAR,
            )
        cases.forEach { (name, expected) ->
            assertEquals(expected, RecoveryInventoryClassifier.role(name))
        }
    }

    @Test
    fun `production composition exposes no caller supplied snapshot or source parameter`() {
        val create =
            AndroidRecoveryMicrofileReconciliation::class.java.declaredMethods.single {
                it.name == "create"
            }
        assertEquals(2, create.parameterCount)
        assertFalse(
            create.parameterTypes.any {
                it.name.contains("Snapshot") || it.name.contains("ReconciliationSource")
            }
        )
        assertEquals(RecoveryQuarantineEvidenceSink::class.java, create.parameterTypes.last())
    }
}
