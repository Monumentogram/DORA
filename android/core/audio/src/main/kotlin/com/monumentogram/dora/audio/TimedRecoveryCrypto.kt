package com.monumentogram.dora.audio

import com.monumentogram.dora.poc.recovery.candidate.RecoveryManifestPublicationRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileUnitRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryReconciliationCrypto
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value

/** Timing only: every argument/result and all accepted Recovery decisions remain unchanged. */
internal class TimedRecoveryCrypto(private val delegate: RecoveryReconciliationCrypto) :
    RecoveryReconciliationCrypto by delegate {
    override fun authenticateManifest(
        runId: RunId,
        publication: RecoveryManifestPublicationRow,
        previousDigest: Sha256Value,
        envelope: ByteArray,
        ciphertext: ByteArray,
    ) =
        AudioOperationDiagnostics.measure(AudioStage.MANIFEST_AUTH) {
            delegate.authenticateManifest(runId, publication, previousDigest, envelope, ciphertext)
        }

    override fun authenticateUnit(
        runId: RunId,
        unit: RecoveryMicrofileUnitRow,
        previousDigest: Sha256Value,
        envelope: ByteArray,
        ciphertext: ByteArray,
    ) =
        AudioOperationDiagnostics.measure(AudioStage.PCM_AUTH) {
            delegate.authenticateUnit(runId, unit, previousDigest, envelope, ciphertext)
        }
}
