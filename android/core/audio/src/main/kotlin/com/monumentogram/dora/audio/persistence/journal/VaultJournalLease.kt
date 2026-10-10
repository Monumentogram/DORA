package com.monumentogram.dora.audio.persistence.journal

import com.monumentogram.dora.audio.PersistenceLatency
import java.util.concurrent.atomic.AtomicReference

/** One logical vault operation; borrowing on its owning thread never acquires another operation. */
internal class VaultJournalLease(private val operationGate: () -> Unit) {
    private data class Held(val asset: String, val thread: Thread)

    private val held = AtomicReference<Held?>()

    fun tryAcquire(asset: String): AutoCloseable? {
        operationGate()
        val token = Held(asset, Thread.currentThread())
        if (!held.compareAndSet(null, token)) return null
        return AutoCloseable {
            check(Thread.currentThread() === token.thread)
            check(held.compareAndSet(token, null))
        }
    }

    /**
     * Resource cleanup remains possible after revocation, while active operations still exclude it.
     */
    fun withExclusiveCleanup(cleanup: () -> Unit) {
        val token = Held("CLOSE", Thread.currentThread())
        check(held.compareAndSet(null, token))
        try {
            cleanup()
        } finally {
            check(held.compareAndSet(token, null))
        }
    }

    fun requireHeld(asset: String? = null) {
        operationGate()
        requireOwner(asset)
    }

    fun requireOwner(asset: String? = null) {
        val token = checkNotNull(held.get())
        check(token.thread === Thread.currentThread())
        check(asset == null || token.asset == asset)
    }

    fun ownedAsset(): String {
        requireHeld()
        return checkNotNull(held.get()).asset
    }
}

/** Completion and readback are separate durability gates. Exceptions never become false success. */
internal class JournalCommitBoundary(
    private val begin: () -> Unit,
    private val markSuccessful: () -> Unit,
    private val end: () -> Unit,
    private val isInTransaction: () -> Boolean,
    private val onUncertain: () -> Unit = {},
) {
    // Mark uncertain for every driver/readback exception, then propagate the original failure.
    @Suppress("TooGenericExceptionCaught")
    fun commit(write: () -> Unit, exactReadback: () -> Boolean) {
        // A nested Room transaction can read its writes before its outer transaction rolls back.
        // Reject before mutation; this known non-mutation is not an uncertain commit.
        check(!isInTransaction()) { "Nested journal transaction rejected" }
        try {
            commitAndRead(write, exactReadback)
        } catch (error: Exception) {
            onUncertain()
            throw error
        }
    }

    private fun commitAndRead(write: () -> Unit, exactReadback: () -> Boolean) {
        begin()
        try {
            write()
            markSuccessful()
        } finally {
            PersistenceLatency.measure("sql_commit") { end() }
        }
        check(exactReadback()) { "Journal readback rejected" }
    }
}
