package com.monumentogram.dora.audio.persistence.auth

import com.monumentogram.dora.audio.recording.RecordingAuthority
import com.monumentogram.dora.audio.recording.RecordingResumeGrant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** PIN_FREE_LOCAL_ACCESS: no credential or fake authentication result enters this contract. */
class LocalAccessSessionTest {
    @Test
    fun coldProcessHasNoAuthorityUntilForegroundAction() {
        val session = LocalAccessSession()
        assertThrows(LocalAccessDeniedException::class.java) { session.capture() }
        session.resume()
        session.capture().requireActive()
        assertThrows(LocalAccessDeniedException::class.java) { LocalAccessSession().capture() }
    }

    @Test
    fun oldHandlesNeverReviveAfterForegroundReturn() {
        val session = LocalAccessSession()
        session.resume()
        val old = session.capture()
        session.pause()
        session.resume()
        session.capture().requireActive()
        assertThrows(LocalAccessDeniedException::class.java) { old.requireActive() }
        assertThrows(LocalAccessDeniedException::class.java) {
            old.withPlaintextDelivery { fail("Stale plaintext delivery") }
        }
    }

    @Test
    fun readinessLossRevokesHandlesWithoutForgettingResumedActivity() {
        var interactive = true
        val session = LocalAccessSession { interactive }
        session.resume()
        val old = session.capture()
        interactive = false
        assertThrows(LocalAccessDeniedException::class.java) { old.requireActive() }
        interactive = true
        session.capture().requireActive()
        assertThrows(LocalAccessDeniedException::class.java) { old.requireActive() }
        session.pause()
        assertThrows(LocalAccessDeniedException::class.java) { session.capture() }
    }

    @Test
    fun resumeBeforeInteractiveDoesNotPermanentlyDisableTheVisibleUi() {
        var interactive = false
        val session = LocalAccessSession { interactive }
        session.resume()
        assertFalse(session.isForeground)
        assertThrows(LocalAccessDeniedException::class.java) { session.capture() }
        interactive = true
        assertTrue(session.isForeground)
        session.capture().requireActive()
    }

    @Test
    fun platformFailureDeniesLocalAccess() {
        val session = LocalAccessSession { error("platform unavailable") }
        session.resume()
        assertFalse(session.isForeground)
        assertThrows(LocalAccessDeniedException::class.java) { session.capture() }
    }

    @Test
    fun screenOffKeepsWriterButDeniesReadAndStaleResume() {
        val session = LocalAccessSession()
        session.resume()
        val original = session.capture()
        val writer =
            RecordingAuthority(original::requireActive, { true }, original::withPlaintextDelivery)
        var starts = 0
        writer.activate { starts++ }
        val stale = RecordingResumeGrant { start ->
            writer.resume(original::withPlaintextDelivery, start)
        }
        session.pause()
        repeat(10) { writer.requireWriter() }
        assertThrows(LocalAccessDeniedException::class.java) {
            original.withPlaintextDelivery { fail() }
        }
        session.resume()
        assertThrows(LocalAccessDeniedException::class.java) { stale.consume { starts++ } }
        val fresh = session.capture()
        val grant = RecordingResumeGrant { start ->
            writer.resume(fresh::withPlaintextDelivery, start)
        }
        grant.consume { starts++ }
        assertThrows(LocalAccessDeniedException::class.java) { grant.consume { starts++ } }
        writer.revoke()
        assertThrows(LocalAccessDeniedException::class.java) { writer.requireWriter() }
        assertThrows(LocalAccessDeniedException::class.java) {
            writer.resume(fresh::withPlaintextDelivery) { starts++ }
        }
        assertEquals(2, starts)
    }

    @Test
    fun revocationWaitsForBoundedPlaintextBorrowThenPreventsNextDelivery() {
        val session = LocalAccessSession()
        session.resume()
        val authorization = session.capture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val revoking = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val delivery = executor.submit {
                authorization.withPlaintextDelivery {
                    entered.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val revocation = executor.submit {
                revoking.countDown()
                session.pause()
            }
            assertTrue(revoking.await(5, TimeUnit.SECONDS))
            assertFalse(revocation.isDone)
            release.countDown()
            delivery.get(5, TimeUnit.SECONDS)
            revocation.get(5, TimeUnit.SECONDS)
            assertThrows(LocalAccessDeniedException::class.java) {
                authorization.withPlaintextDelivery { fail() }
            }
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
}
