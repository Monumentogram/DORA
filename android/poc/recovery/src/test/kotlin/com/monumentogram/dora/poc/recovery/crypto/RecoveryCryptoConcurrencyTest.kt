package com.monumentogram.dora.poc.recovery.crypto

import com.google.crypto.tink.Aead
import com.monumentogram.dora.poc.recovery.contract.RecoveryContractException
import java.security.GeneralSecurityException
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCryptoConcurrencyTest {
    @Test
    fun `barrier race permits exactly one envelope serializer`() {
        val keyset =
            RecoveryTinkRuntime.newAeadKeyset(RecoveryCryptoTestFixtures.microfileEnvelopeAad())
        val runAead =
            RecoveryRunAeadProvider(RecordingRunAeadBackend())
                .createNew(RecoveryCryptoTestFixtures.RUN_ID)

        val outcomes = concurrentAttempts { keyset.serializeEncrypted(runAead) }

        assertEquals(1, outcomes.count { it })
        assertEquals(PARTICIPANTS - 1, outcomes.count { !it })
    }

    @Test
    fun `barrier race permits exactly one encryption claimant`() {
        val envelopeAad = RecoveryCryptoTestFixtures.microfileEnvelopeAad()
        val keyset = RecoveryTinkRuntime.newAeadKeyset(envelopeAad)
        val runAead =
            RecoveryRunAeadProvider(RecordingRunAeadBackend())
                .createNew(RecoveryCryptoTestFixtures.RUN_ID)
        keyset.serializeEncrypted(runAead)

        val outcomes = concurrentAttempts {
            keyset.encryptMicrofile(
                byteArrayOf(1, 2, 3),
                RecoveryCryptoTestFixtures.microfileAad(),
            )
        }

        assertEquals(1, outcomes.count { it })
        assertEquals(PARTICIPANTS - 1, outcomes.count { !it })
    }

    @Test
    fun `failed in-flight envelope serialization poisons every concurrent claimant`() {
        val failureBarrier = CyclicBarrier(2)
        val failingBackend = RecordingRunAeadBackend(BlockingFailingEncryptAead(failureBarrier))
        val failingRunAead =
            RecoveryRunAeadProvider(failingBackend).createNew(RecoveryCryptoTestFixtures.RUN_ID)
        val keyset =
            RecoveryTinkRuntime.newAeadKeyset(RecoveryCryptoTestFixtures.microfileEnvelopeAad())
        val pool = Executors.newFixedThreadPool(2)

        try {
            val first =
                pool.submit(
                    Callable { captureFailure { keyset.serializeEncrypted(failingRunAead) } }
                )
            val second =
                pool.submit(
                    Callable { captureFailure { keyset.serializeEncrypted(failingRunAead) } }
                )
            failureBarrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val failures = listOf(first.await(), second.await())

            assertEquals(2, failures.size)
            assertTrue(failures.any { it is GeneralSecurityException })
            assertTrue(failures.any { it is RecoveryContractException })
        } finally {
            pool.shutdownNow()
        }

        val healthyRunAead =
            RecoveryRunAeadProvider(RecordingRunAeadBackend())
                .createNew(RecoveryCryptoTestFixtures.RUN_ID)
        assertThrows(RecoveryContractException::class.java) {
            keyset.serializeEncrypted(healthyRunAead)
        }
        assertThrows(RecoveryContractException::class.java) {
            keyset.encryptMicrofile(
                byteArrayOf(1),
                RecoveryCryptoTestFixtures.microfileAad(),
            )
        }
    }

    private fun concurrentAttempts(block: () -> Unit): List<Boolean> {
        val barrier = CyclicBarrier(PARTICIPANTS)
        val pool = Executors.newFixedThreadPool(PARTICIPANTS)
        return try {
            val futures =
                (0 until PARTICIPANTS).map {
                    pool.submit(
                        Callable {
                            barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            try {
                                block()
                                true
                            } catch (_: RecoveryContractException) {
                                false
                            }
                        }
                    )
                }
            futures.map { it.await() }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun captureFailure(block: () -> Unit): Throwable =
        try {
            block()
            throw AssertionError("operation unexpectedly succeeded")
        } catch (error: GeneralSecurityException) {
            error
        } catch (error: RecoveryContractException) {
            error
        }

    private fun <T> Future<T>.await(): T = get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

    private class BlockingFailingEncryptAead(private val barrier: CyclicBarrier) : Aead {
        override fun encrypt(
            plaintext: ByteArray,
            associatedData: ByteArray,
        ): ByteArray {
            barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            throw GeneralSecurityException("synthetic envelope failure")
        }

        override fun decrypt(
            ciphertext: ByteArray,
            associatedData: ByteArray,
        ): ByteArray = throw AssertionError("decrypt must not be called")
    }

    private companion object {
        const val PARTICIPANTS = 8
        const val TIMEOUT_SECONDS = 10L
    }
}
