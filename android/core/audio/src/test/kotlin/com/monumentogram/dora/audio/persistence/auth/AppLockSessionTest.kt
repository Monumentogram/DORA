package com.monumentogram.dora.audio.persistence.auth

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AppLockSessionTest {
    private var deviceReady = true

    private fun AppLockSession.deliver(
        attempt: AppLockSession.Attempt,
        proof: () -> Boolean,
    ): Boolean {
        recordResult(attempt)
        return complete(attempt, proof)
    }

    private fun session() = AppLockSession { deviceReady }

    private fun unlocked(session: AppLockSession): AppLockSession.Authorization {
        session.resume()
        val attempt = session.begin()
        assertTrue(session.deliver(attempt) { true })
        return session.capture()
    }

    private fun denied(block: () -> Unit) {
        try {
            block()
            fail("Authorization must be denied")
        } catch (_: AppLockedException) {}
    }

    @Test
    fun queuedResultExpiresEvenWhenKeystoreProofWouldStillSucceed() {
        var now = 100L
        val session = AppLockSession({ now }) { true }
        session.resume()
        val attempt = session.begin()
        assertTrue(session.recordResult(attempt))
        now = 1_100L
        assertFalse(session.deliver(attempt) { true })
        denied { session.capture() }
    }

    @Test
    fun proofCannotExtendQueuedResultDeadline() {
        var now = 100L
        val session = AppLockSession({ now }) { true }
        session.resume()
        val attempt = session.begin()
        assertTrue(session.recordResult(attempt))
        assertFalse(
            session.deliver(attempt) {
                now = 1_100L
                true
            }
        )
        denied { session.capture() }
    }

    @Test
    fun coldLaunchAndProcessReplacementHaveNoAuthority() {
        denied { session().capture() }
        unlocked(session())
        denied { session().capture() }
    }

    @Test
    fun stalePromptCannotReplaceNewerAttempt() {
        val session = session()
        session.resume()
        val old = session.begin()
        val next = session.begin()
        assertFalse(session.deliver(old) { true })
        denied { session.capture() }
        assertTrue(session.deliver(next) { true })
    }

    @Test
    fun backgroundRevokesOldCapabilityButPendingOrdinaryRequestHasNoAuthority() {
        val session = session()
        val old = unlocked(session)
        val pending = session.begin()
        session.pause()
        denied { old.requireActive() }
        denied { session.capture() }
        assertFalse(session.deliver(pending) { true })
        session.resume()
        assertTrue(session.deliver(pending) { true })
        denied { old.requireActive() }
    }

    @Test
    fun securityLockCancelsAttemptEvenAfterDeviceUnlock() {
        val session = session()
        session.resume()
        val attempt = session.begin()
        session.lock()
        session.resume()
        assertFalse(session.deliver(attempt) { true })
    }

    @Test
    fun expiredRecentAuthProofConsumesResultAndRemainsLocked() {
        val session = session()
        session.resume()
        val attempt = session.begin()
        assertFalse(session.deliver(attempt) { false })
        assertFalse(session.deliver(attempt) { true })
        denied { session.capture() }
    }

    @Test
    fun duplicateSuccessCannotRegrantAfterBackground() {
        val session = session()
        session.resume()
        val attempt = session.begin()
        assertTrue(session.deliver(attempt) { true })
        session.pause()
        session.resume()
        assertFalse(session.deliver(attempt) { true })
    }

    @Test
    fun noCredentialOrLockedDeviceCannotIssueOrUseAuthority() {
        val session = session()
        val authorization = unlocked(session)
        deviceReady = false
        denied { authorization.requireActive() }
        deviceReady = true
        denied { authorization.requireActive() }
        session.resume()
        val attempt = session.begin()
        deviceReady = false
        assertFalse(session.deliver(attempt) { true })
    }

    @Test
    fun resultBeforeResumeDoesNotExecuteProofOrGrant() {
        val session = session()
        session.resume()
        val attempt = session.begin()
        session.pause()
        assertFalse(
            session.deliver(attempt) {
                fail("Proof before resume")
                true
            }
        )
        session.resume()
        assertTrue(session.deliver(attempt) { true })
    }

    @Test
    fun revocationDuringProofCannotIssueCapability() {
        val session = session()
        session.resume()
        val attempt = session.begin()
        assertFalse(
            session.deliver(attempt) {
                session.lock()
                true
            }
        )
        denied { session.capture() }
    }

    @Test
    fun backgroundDuringProofCannotIssueCapabilityEvenIfResumedAgain() {
        val session = session()
        session.resume()
        val attempt = session.begin()
        assertFalse(
            session.deliver(attempt) {
                session.pause()
                session.resume()
                true
            }
        )
        denied { session.capture() }
    }

    @Test
    fun deviceBecomingLockedDuringProofCannotIssueCapability() {
        val session = session()
        session.resume()
        val attempt = session.begin()
        assertFalse(
            session.deliver(attempt) {
                deviceReady = false
                true
            }
        )
    }

    @Test
    fun cancelledAttemptCannotGrant() {
        val session = session()
        session.resume()
        val attempt = session.begin()
        session.cancel(attempt)
        assertFalse(session.deliver(attempt) { true })
    }

    @Test
    fun oldCapabilityDoesNotReviveInNewSessionAndCannotDeliverNextUnit() {
        val session = session()
        val first = unlocked(session)
        var deliveries = 0
        first.withPlaintextDelivery { deliveries++ }
        session.lock()
        unlocked(session)
        denied { first.withPlaintextDelivery { deliveries++ } }
        assertEquals(1, deliveries)
    }

    @Test
    fun deliveryAndRevocationAreSerializedForWholeCallback() {
        val session = session()
        val authorization = unlocked(session)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val revokeStarted = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val delivery = executor.submit {
                authorization.withPlaintextDelivery {
                    entered.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                    authorization.requireActive()
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val revocation = executor.submit {
                revokeStarted.countDown()
                session.lock()
            }
            assertTrue(revokeStarted.await(5, TimeUnit.SECONDS))
            assertFalse(revocation.isDone)
            release.countDown()
            delivery.get(5, TimeUnit.SECONDS)
            revocation.get(5, TimeUnit.SECONDS)
            denied { authorization.withPlaintextDelivery { fail("Delivery after lock") } }
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
}
