package com.monumentogram.dora.audio.persistence

import androidx.room.RoomDatabase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

/** Observes real Room 2.8.4 refresh callbacks; never bypasses the encrypted helper or refresh. */
internal class RoomInvalidationProbe(vault: EncryptedAudioVault) {
    private val database: RoomDatabase

    init {
        val journal = field(vault, "journal").get(vault)
        database = field(journal, "database").get(journal) as RoomDatabase
    }

    @Suppress("UNCHECKED_CAST") // Pinned Room internals are observed only in instrumentation.
    fun assertRefreshOwnedByCaller(transactionRetained: Boolean) {
        val tracker = database.invalidationTracker
        val implementation = field(tracker, "implementation").get(tracker)
        val allowField = field(implementation, "onAllowRefresh")
        val completeField = field(tracker, "onRefreshCompleted")
        val realAllow = allowField.get(implementation) as () -> Boolean
        val realComplete = completeField.get(tracker) as () -> Unit
        val caller = Thread.currentThread()
        val observed = AtomicReference<Thread>()
        val completionThread = AtomicReference<Thread>()
        val completed = CountDownLatch(1)
        allowField.set(
            implementation,
            {
                observed.set(Thread.currentThread())
                realAllow()
            },
        )
        completeField.set(
            tracker,
            {
                realComplete()
                completionThread.set(Thread.currentThread())
                completed.countDown()
            },
        )
        try {
            assertEquals(transactionRetained, database.inTransaction())
            tracker.refreshAsync()
            assertTrue("Actual Room refresh completed", completed.await(5, TimeUnit.SECONDS))
            assertSame(
                "Refresh must not strand a close barrier on another thread",
                caller,
                observed.get(),
            )
            assertSame("Refresh must finish on its owning thread", caller, completionThread.get())
            assertEquals(
                "Refresh must not end the faulted transaction",
                transactionRetained,
                database.inTransaction(),
            )
        } finally {
            allowField.set(implementation, realAllow)
            completeField.set(tracker, realComplete)
        }
    }

    private fun field(owner: Any, name: String) =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
}
