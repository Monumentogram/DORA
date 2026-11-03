package com.monumentogram.dora.audio.persistence.keys

import com.google.crypto.tink.Aead
import com.google.crypto.tink.subtle.AesGcmJce
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RunKeyFailureScopesTest {
    private fun run() = RunId.fromCanonicalString(UUID.randomUUID().toString())

    @Test
    fun successfulAndExceptionalExitRetireBorrowedPrimitiveAndRetryOpensFresh() {
        val scopes = RunKeyFailureScopes()
        val run = run()
        val raw = AesGcmJce(ByteArray(32))
        val bytes = raw.encrypt(byteArrayOf(4), byteArrayOf(5))
        var opens = 0
        var borrowed: Aead? = null
        repeat(2) { index ->
            val action = {
                scopes.observe(run, RunKeyOperation.RECONCILIATION) {
                    borrowed =
                        scopes.validatedAead(run, {}) {
                            opens++
                            raw
                        }
                    assertArrayEquals(byteArrayOf(4), borrowed!!.decrypt(bytes, byteArrayOf(5)))
                    val reused =
                        scopes.validatedAead(run, {}) {
                            opens++
                            raw
                        }
                    assertArrayEquals(byteArrayOf(4), reused.decrypt(bytes, byteArrayOf(5)))
                    if (index == 1) error("Synthetic interrupted attempt")
                }
            }
            if (index == 0) action()
            else assertThrows(IllegalStateException::class.java) { action() }
            assertThrows(KeyBoundaryException::class.java) {
                borrowed!!.decrypt(bytes, byteArrayOf(5))
            }
        }
        assertEquals(2, opens)
    }

    @Test
    fun missingAliasAndProviderFailureRetirePrimitiveEvenWhenCaughtInsideAttempt() {
        for (failure in listOf("missing", "provider", "recorded", "deletion")) {
            val scopes = RunKeyFailureScopes()
            val run = run()
            val raw = AesGcmJce(ByteArray(32))
            val bytes = raw.encrypt(byteArrayOf(4), byteArrayOf(5))
            var available = true
            var providerFailure = false
            var opens = 0
            val provider =
                object : Aead by raw {
                    override fun decrypt(
                        ciphertext: ByteArray,
                        associatedData: ByteArray,
                    ): ByteArray {
                        check(!providerFailure)
                        return raw.decrypt(ciphertext, associatedData)
                    }
                }
            scopes.observe(run, RunKeyOperation.RECONCILIATION) {
                val primitive =
                    scopes.validatedAead(run, { check(available) }) {
                        opens++
                        provider
                    }
                assertArrayEquals(byteArrayOf(4), primitive.decrypt(bytes, byteArrayOf(5)))
                when (failure) {
                    "missing" -> available = false
                    "provider" -> providerFailure = true
                    "recorded" -> scopes.record(run, KeyFailure.AUTHENTICATION_FAILED)
                    else -> scopes.invalidate(run)
                }
                assertThrows(Exception::class.java) { primitive.decrypt(bytes, byteArrayOf(5)) }
                available = true
                providerFailure = false
                assertThrows(KeyBoundaryException::class.java) {
                    primitive.decrypt(bytes, byteArrayOf(5))
                }
                val fresh =
                    scopes.validatedAead(run, {}) {
                        opens++
                        raw
                    }
                assertArrayEquals(byteArrayOf(4), fresh.decrypt(bytes, byteArrayOf(5)))
                val next =
                    scopes.validatedAead(run, {}) {
                        opens++
                        raw
                    }
                assertThrows(KeyBoundaryException::class.java) {
                    fresh.decrypt(bytes, byteArrayOf(5))
                }
                assertArrayEquals(byteArrayOf(4), next.decrypt(bytes, byteArrayOf(5)))
            }
            assertEquals(failure, 3, opens)
        }
    }

    @Test
    fun unrelatedRunsVaultsAndOperationsNeverShareValidatedPrimitive() {
        val scopes = RunKeyFailureScopes()
        val otherVault = RunKeyFailureScopes()
        val run = run()
        val otherRun = run()
        val raw = AesGcmJce(ByteArray(32))
        var opens = 0
        fun open(scope: RunKeyFailureScopes, selected: RunId) =
            scope.validatedAead(selected, {}) {
                opens++
                raw
            }
        repeat(2) { open(scopes, run) }
        for (operation in listOf(RunKeyOperation.BOOTSTRAP, RunKeyOperation.PUBLICATION)) {
            scopes.observe(run, operation) { repeat(2) { open(scopes, run) } }
        }
        assertEquals(6, opens)
        scopes.observe(run, RunKeyOperation.RECONCILIATION) {
            repeat(2) { open(scopes, run) }
            repeat(2) { open(scopes, otherRun) }
            otherVault.observe(run, RunKeyOperation.RECONCILIATION) {
                repeat(2) { open(otherVault, run) }
            }
        }
        assertEquals(10, opens)
    }

    @Test
    fun anotherThreadCannotUseBorrowedPrimitiveOrInvalidateItsOwnerAttempt() {
        val scopes = RunKeyFailureScopes()
        val run = run()
        val raw = AesGcmJce(ByteArray(32))
        val bytes = raw.encrypt(byteArrayOf(4), byteArrayOf(5))
        val workerFailure = AtomicReference<Throwable>()
        scopes.observe(run, RunKeyOperation.RECONCILIATION) {
            val primitive = scopes.validatedAead(run, {}) { raw }
            val worker = Thread {
                try {
                    assertThrows(KeyBoundaryException::class.java) {
                        primitive.decrypt(bytes, byteArrayOf(5))
                    }
                    scopes.observe(run, RunKeyOperation.RECONCILIATION) {
                        val own = scopes.validatedAead(run, {}) { raw }
                        assertArrayEquals(byteArrayOf(4), own.decrypt(bytes, byteArrayOf(5)))
                    }
                } catch (error: Throwable) {
                    workerFailure.set(error)
                }
            }
            worker.start()
            worker.join(10_000)
            assertTrue(!worker.isAlive)
            assertNull(workerFailure.get())
            assertArrayEquals(byteArrayOf(4), primitive.decrypt(bytes, byteArrayOf(5)))
        }
    }

    @Test
    fun attemptsClearOnFailureRejectNestingAndIgnoreOtherRunsAndVaults() {
        val scopes = RunKeyFailureScopes()
        val otherVault = RunKeyFailureScopes()
        val run = run()
        assertThrows(IllegalStateException::class.java) {
            scopes.observe(run, RunKeyOperation.BOOTSTRAP) {
                scopes.record(run, KeyFailure.TEMPORARILY_UNAVAILABLE)
                error("synthetic-canary")
            }
        }
        val next =
            scopes.observe(run, RunKeyOperation.RECONCILIATION) {
                scopes.record(run(), KeyFailure.AUTHENTICATION_FAILED)
                otherVault.record(run, KeyFailure.AUTHENTICATION_FAILED)
                assertThrows(IllegalStateException::class.java) {
                    scopes.observe(run, RunKeyOperation.BOOTSTRAP) {}
                }
            }
        assertNull(next.failure)
        val observed =
            scopes.observe(run, RunKeyOperation.PUBLICATION) {
                scopes.record(run, KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED)
                scopes.record(run, KeyFailure.TEMPORARILY_UNAVAILABLE)
            }
        assertEquals(KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED, observed.failure)
        assertNull(scopes.observe(run, RunKeyOperation.PUBLICATION) {}.failure)
    }

    @Test
    fun sameRunConcurrentThreadsCannotStealOrContaminateAttempts() {
        val scopes = RunKeyFailureScopes()
        val run = run()
        val ready = CountDownLatch(1)
        val done = CountDownLatch(1)
        val result = AtomicReference<RunKeyAttempt<Unit>>()
        val failure = AtomicReference<Throwable>()
        val worker = Thread {
            try {
                result.set(
                    scopes.observe(run, RunKeyOperation.RECONCILIATION) {
                        scopes.record(run, KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED)
                        ready.countDown()
                        check(done.await(10, TimeUnit.SECONDS))
                    }
                )
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        worker.start()
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            assertEquals(
                KeyFailure.TEMPORARILY_UNAVAILABLE,
                scopes
                    .observe(run, RunKeyOperation.PUBLICATION) {
                        scopes.record(run, KeyFailure.TEMPORARILY_UNAVAILABLE)
                    }
                    .failure,
            )
        } finally {
            done.countDown()
            worker.join(10000)
        }
        assertNull(failure.get())
        assertEquals(KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED, result.get().failure)
    }
}
