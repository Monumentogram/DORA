package com.monumentogram.dora.audio.persistence.journal

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultJournalBoundaryTest {
    @Test
    fun nestedAcquisitionCannotAuthorizeAnotherOperation() {
        val lease = VaultJournalLease {}
        lease.tryAcquire("asset")!!.use {
            assertNull(lease.tryAcquire("asset"))
            assertNull(lease.tryAcquire("other"))
            lease.requireHeld("asset")
            assertThrows(IllegalStateException::class.java) { lease.requireHeld("other") }
        }
        lease.tryAcquire("other")!!.close()
    }

    @Test
    fun competingThreadCannotAcquireOrBorrowActiveOperation() {
        val lease = VaultJournalLease {}
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val future = worker.submit {
                lease.tryAcquire("asset")!!.use {
                    entered.countDown()
                    release.await()
                }
            }
            entered.await()
            assertNull(lease.tryAcquire("asset"))
            assertThrows(IllegalStateException::class.java) { lease.requireHeld("asset") }
            release.countDown()
            future.get()
            lease.tryAcquire("asset")!!.close()
        } finally {
            release.countDown()
            worker.shutdownNow()
        }
    }

    @Test
    fun revokedGatePreventsBorrowAndAcquisition() {
        var allowed = true
        val lease = VaultJournalLease { check(allowed) }
        lease.tryAcquire("asset")!!.use {
            allowed = false
            assertThrows(IllegalStateException::class.java) { lease.requireHeld("asset") }
        }
        assertThrows(IllegalStateException::class.java) { lease.tryAcquire("asset") }
    }

    @Test
    fun transactionSuccessWaitsForEndAndExactReadback() {
        val events = mutableListOf<String>()
        val transaction =
            JournalCommitBoundary(
                begin = { events += "begin" },
                markSuccessful = { events += "mark" },
                end = { events += "end" },
                isInTransaction = { false },
            )
        transaction.commit({ events += "write" }) {
            events += "read"
            true
        }
        assertEquals(listOf("begin", "write", "mark", "end", "read"), events)
    }

    @Test
    fun uncertainEndNeverReturnsSuccessOrRunsReadback() {
        var read = false
        val transaction =
            JournalCommitBoundary({}, {}, { error("synthetic end failure") }, { false })
        assertThrows(IllegalStateException::class.java) {
            transaction.commit({}) {
                read = true
                true
            }
        }
        assertFalse(read)
    }

    @Test
    fun readbackMismatchCannotReturnSuccess() {
        val transaction = JournalCommitBoundary({}, {}, {}, { false })
        assertThrows(IllegalStateException::class.java) { transaction.commit({}) { false } }
    }

    @Test
    fun writeFailureStillEndsWithoutMarkingSuccessful() {
        var marked = false
        var ended = false
        val transaction = JournalCommitBoundary({}, { marked = true }, { ended = true }, { false })
        assertThrows(IllegalStateException::class.java) {
            transaction.commit({ error("write") }) { true }
        }
        assertFalse(marked)
        assertTrue(ended)
    }

    @Test
    fun revocationCannotLeakDatabaseResourcesOrBypassAnActiveOperation() {
        var allowed = true
        var closed = false
        val lease = VaultJournalLease { check(allowed) }
        lease.tryAcquire("asset")!!.use {
            allowed = false
            assertThrows(IllegalStateException::class.java) {
                lease.withExclusiveCleanup { closed = true }
            }
            assertFalse(closed)
        }
        lease.withExclusiveCleanup { closed = true }
        assertTrue(closed)
    }

    @Test
    fun nestedCommitIsRejectedBeforeBeginMutationAndUncertainty() {
        val events = mutableListOf<String>()
        val transaction =
            JournalCommitBoundary(
                begin = { events += "begin" },
                markSuccessful = { events += "mark" },
                end = { events += "end" },
                isInTransaction = { true },
                onUncertain = { events += "uncertain" },
            )
        assertThrows(IllegalStateException::class.java) {
            transaction.commit({ events += "write" }) {
                events += "read"
                true
            }
        }
        assertTrue(events.isEmpty())
    }
}
