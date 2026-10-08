package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapWriteHandle
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryBootstrapStorage
import com.monumentogram.dora.poc.recovery.candidate.CandidateWriteHandle
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateStorage
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineStorage
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.util.IdentityHashMap

/** Guards precede adapter side effects; descriptors remain closeable after auth revocation. */
internal object DiagnosticStorageFence {
    fun candidate(
        real: RecoveryCandidateStorage,
        guard: (RunId) -> Unit,
    ): RecoveryCandidateStorage =
        object : RecoveryCandidateStorage {
            private val handles = Handles<CandidateWriteHandle>(guard)

            override fun openExclusiveTemp(
                runId: RunId,
                temporaryRelativeName: String,
            ): CandidateWriteHandle {
                guard(runId)
                return real.openExclusiveTemp(runId, temporaryRelativeName).also {
                    handles.add(it, runId)
                }
            }

            override fun write(
                handle: CandidateWriteHandle,
                bytes: ByteArray,
                offset: Int,
                count: Int,
            ): Int {
                handles.check(handle)
                return real.write(handle, bytes, offset, count)
            }

            override fun fsync(handle: CandidateWriteHandle) {
                handles.check(handle)
                real.fsync(handle)
            }

            override fun close(handle: CandidateWriteHandle) {
                handles.remove(handle)
                real.close(handle)
            }

            override fun finalExists(runId: RunId, finalRelativeName: String): Boolean {
                guard(runId)
                return real.finalExists(runId, finalRelativeName)
            }

            override fun renameTempToFinal(
                runId: RunId,
                temporaryRelativeName: String,
                finalRelativeName: String,
            ) {
                guard(runId)
                real.renameTempToFinal(runId, temporaryRelativeName, finalRelativeName)
            }

            override fun fsyncParent(runId: RunId, finalRelativeName: String) {
                guard(runId)
                real.fsyncParent(runId, finalRelativeName)
            }
        }

    fun bootstrap(
        real: RecoveryBootstrapStorage,
        guard: (RunId) -> Unit,
    ): RecoveryBootstrapStorage =
        object : RecoveryBootstrapStorage {
            private val handles = Handles<BootstrapWriteHandle>(guard)

            override fun inspectNamespaces(runId: RunId) = run {
                guard(runId)
                real.inspectNamespaces(runId)
            }

            override fun openExclusiveConfirmationTemp(runId: RunId): BootstrapWriteHandle {
                guard(runId)
                return real.openExclusiveConfirmationTemp(runId).also { handles.add(it, runId) }
            }

            override fun write(
                handle: BootstrapWriteHandle,
                bytes: ByteArray,
                offset: Int,
                count: Int,
            ): Int {
                handles.check(handle)
                return real.write(handle, bytes, offset, count)
            }

            override fun fsyncTemp(handle: BootstrapWriteHandle) {
                handles.check(handle)
                real.fsyncTemp(handle)
            }

            override fun closeTemp(handle: BootstrapWriteHandle) {
                handles.remove(handle)
                real.closeTemp(handle)
            }

            override fun finalExists(runId: RunId): Boolean {
                guard(runId)
                return real.finalExists(runId)
            }

            override fun renameTempToFinal(runId: RunId) {
                guard(runId)
                real.renameTempToFinal(runId)
            }

            override fun fsyncConfirmationParent(runId: RunId) {
                guard(runId)
                real.fsyncConfirmationParent(runId)
            }
        }

    fun quarantine(
        real: RecoveryQuarantineStorage,
        guard: (RunId) -> Unit,
    ): RecoveryQuarantineStorage =
        object : RecoveryQuarantineStorage {
            override fun prepare(runId: RunId) {
                guard(runId)
                real.prepare(runId)
            }

            override fun inspect(row: RecoveryQuarantineIntentRow) = run {
                guard(row.input.runId)
                real.inspect(row)
            }

            override fun renameNoOverwrite(row: RecoveryQuarantineIntentRow) {
                guard(row.input.runId)
                real.renameNoOverwrite(row)
            }

            override fun fsyncSourceParent(row: RecoveryQuarantineIntentRow) {
                guard(row.input.runId)
                real.fsyncSourceParent(row)
            }

            override fun fsyncDestinationParent(row: RecoveryQuarantineIntentRow) {
                guard(row.input.runId)
                real.fsyncDestinationParent(row)
            }
        }

    private class Handles<T : Any>(private val guard: (RunId) -> Unit) {
        private val owned = IdentityHashMap<T, RunId>()

        fun add(handle: T, run: RunId) {
            check(owned.put(handle, run) == null)
        }

        fun check(handle: T) {
            guard(checkNotNull(owned[handle]) { "Foreign diagnostic handle" })
        }

        fun remove(handle: T) {
            checkNotNull(owned.remove(handle)) { "Foreign diagnostic handle" }
        }
    }
}
