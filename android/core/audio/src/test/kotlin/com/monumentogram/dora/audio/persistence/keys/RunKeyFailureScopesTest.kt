package com.monumentogram.dora.audio.persistence.keys

import com.monumentogram.dora.poc.recovery.contract.RunId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RunKeyFailureScopesTest {
    private fun run() = RunId.fromCanonicalString(UUID.randomUUID().toString())

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
