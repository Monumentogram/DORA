package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.controller.AliasObservation
import com.monumentogram.dora.poc.recovery.controller.KeyConfirmationSnapshot
import com.monumentogram.dora.poc.recovery.crypto.KeyConfirmationDecryption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryReconciliationAcceptanceTest {
    @Test
    fun `confirmation result returns before candidate inventory observation`() {
        val source = BlockingSource(block = false, failIfCandidateLoaded = true)
        val result = controller(source).reconcile(RUN_A)
        assertTrue(result is MicrofileReconciliationResult.NoAuthenticatedPrefix)
        assertFalse(source.candidateLoaded)
    }

    @Test
    fun `same run excludes another controller while different run remains independent`() {
        val blocking = BlockingSource(block = true)
        val first = AtomicReference<MicrofileReconciliationResult>()
        val thread = Thread { first.set(controller(blocking).reconcile(RUN_A)) }
        thread.start()
        assertTrue(blocking.entered.await(5, TimeUnit.SECONDS))
        assertTrue(
            controller(BlockingSource(false)).reconcile(RUN_A)
                is MicrofileReconciliationResult.ConcurrentWriter
        )
        assertTrue(
            controller(BlockingSource(false)).reconcile(RUN_B)
                is MicrofileReconciliationResult.NoAuthenticatedPrefix
        )
        blocking.release.countDown()
        thread.join(5_000)
        assertTrue(first.get() is MicrofileReconciliationResult.NoAuthenticatedPrefix)
    }

    @Test
    fun `typed source failure returns immutable unsafe diagnostic without touching later sources`() {
        val captured =
            RecoveryFailureDiagnostic(
                RecoveryFailureCategory.UNSAFE_PARENT,
                "unsafe-parent",
                "ancestor symlink",
            )
        val source =
            object : RecoveryReconciliationSource {
                override fun loadConfirmation(runId: RunId): KeyConfirmationSnapshot =
                    throw RecoverySourceAccessException(captured, IllegalStateException("raw"))

                override fun loadCandidate(runId: RunId): RecoveryCandidateSnapshot =
                    error("must not load")

                override fun loadArtifact(
                    runId: RunId,
                    relativeName: String,
                    context: RecoveryArtifactContext,
                ) = error("must not load")
            }
        val result =
            controller(source).reconcile(RUN_A)
                as MicrofileReconciliationResult.NoAuthenticatedPrefix
        assertEquals(ReconciliationDiagnostic.UNSAFE_PATH, result.diagnostic)
        assertEquals(captured, result.failure)
        assertFalse(result.failure!!.type.contains("Throwable"))
    }

    private fun controller(source: RecoveryReconciliationSource) =
        RecoveryMicrofileReconciliationController(source, NeverCrypto)

    private class BlockingSource(
        private val block: Boolean,
        private val failIfCandidateLoaded: Boolean = false,
    ) : RecoveryReconciliationSource {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var candidateLoaded = false

        override fun loadConfirmation(runId: RunId): KeyConfirmationSnapshot {
            entered.countDown()
            if (block) check(release.await(5, TimeUnit.SECONDS))
            return KeyConfirmationSnapshot(
                KeyConfirmationValue(RecoveryCandidate.MICROFILE, runId),
                null,
                null,
                false,
                AliasObservation.ABSENT,
            )
        }

        override fun loadCandidate(runId: RunId): RecoveryCandidateSnapshot {
            candidateLoaded = true
            check(!failIfCandidateLoaded)
            error("candidate should not load for absent confirmation")
        }

        override fun loadArtifact(
            runId: RunId,
            relativeName: String,
            context: RecoveryArtifactContext,
        ) = null
    }

    private object NeverCrypto : RecoveryReconciliationCrypto {
        override fun authenticateConfirmationOrphan(
            expected: KeyConfirmationValue,
            ciphertext: ByteArray,
        ): KeyConfirmationDecryption = error("crypto must not run")

        override fun authenticateManifest(
            runId: RunId,
            publication: RecoveryManifestPublicationRow,
            previousDigest: com.monumentogram.dora.poc.recovery.contract.Sha256Value,
            envelope: ByteArray,
            ciphertext: ByteArray,
        ): ManifestAuthenticationOutcome = error("crypto must not run")

        override fun authenticateUnit(
            runId: RunId,
            unit: RecoveryMicrofileUnitRow,
            previousDigest: com.monumentogram.dora.poc.recovery.contract.Sha256Value,
            envelope: ByteArray,
            ciphertext: ByteArray,
        ): UnitAuthenticationOutcome = error("crypto must not run")
    }

    private companion object {
        val RUN_A = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
        val RUN_B = RunId.fromCanonicalString("10112233-4455-6677-8899-aabbccddeeff")
    }
}
