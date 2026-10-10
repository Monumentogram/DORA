package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.poc.recovery.candidate.CandidateWriteHandle
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateStorage
import com.monumentogram.dora.poc.recovery.contract.RunId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DiagnosticStorageFenceTest {
    private val run = RunId.fromCanonicalString("00000000-0000-0000-0000-000000000001")

    private class Storage : RecoveryCandidateStorage {
        var calls = 0

        override fun openExclusiveTemp(
            runId: RunId,
            temporaryRelativeName: String,
        ): CandidateWriteHandle {
            calls++
            return object : CandidateWriteHandle {}
        }

        override fun write(
            handle: CandidateWriteHandle,
            bytes: ByteArray,
            offset: Int,
            count: Int,
        ): Int {
            calls++
            return count
        }

        override fun fsync(handle: CandidateWriteHandle) {
            calls++
        }

        override fun close(handle: CandidateWriteHandle) {
            calls++
        }

        override fun finalExists(runId: RunId, finalRelativeName: String): Boolean {
            calls++
            return false
        }

        override fun renameTempToFinal(
            runId: RunId,
            temporaryRelativeName: String,
            finalRelativeName: String,
        ) {
            calls++
        }

        override fun fsyncParent(runId: RunId, finalRelativeName: String) {
            calls++
        }
    }

    @Test
    fun rejectedRunNeverReachesFilesystem() {
        val real = Storage()
        val guarded = DiagnosticStorageFence.candidate(real) { error("denied") }
        assertThrows(IllegalStateException::class.java) {
            guarded.openExclusiveTemp(run, "unit.tmp")
        }
        assertThrows(IllegalStateException::class.java) {
            guarded.renameTempToFinal(run, "unit.tmp", "unit")
        }
        assertThrows(IllegalStateException::class.java) { guarded.fsyncParent(run, "unit") }
        assertEquals(0, real.calls)
    }

    @Test
    fun foreignOrClosedHandleCannotWrite() {
        val real = Storage()
        val guarded = DiagnosticStorageFence.candidate(real) {}
        val foreign = object : CandidateWriteHandle {}
        assertThrows(IllegalStateException::class.java) {
            guarded.write(foreign, byteArrayOf(1), 0, 1)
        }
        assertThrows(IllegalStateException::class.java) { guarded.close(foreign) }
        assertEquals(0, real.calls)
        val owned = guarded.openExclusiveTemp(run, "unit.tmp")
        guarded.close(owned)
        assertThrows(IllegalStateException::class.java) { guarded.fsync(owned) }
        assertEquals(2, real.calls)
    }

    @Test
    fun revocationStopsWritesButAllowsOwnedHandleCleanup() {
        val real = Storage()
        var allowed = true
        val guarded = DiagnosticStorageFence.candidate(real) { check(allowed) }
        val owned = guarded.openExclusiveTemp(run, "unit.tmp")
        allowed = false
        assertThrows(IllegalStateException::class.java) {
            guarded.write(owned, byteArrayOf(1), 0, 1)
        }
        guarded.close(owned)
        assertEquals(2, real.calls)
    }
}
