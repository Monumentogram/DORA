package com.monumentogram.dora.poc.recovery.coordination

import com.monumentogram.dora.poc.recovery.contract.RunId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

internal fun interface RecoveryRunWriterLease : AutoCloseable

internal fun interface RecoveryRunSingleWriterGuard {
    fun tryAcquire(runId: RunId): RecoveryRunWriterLease?
}

/** Process-wide same-run exclusion shared by every Recovery publication controller instance. */
internal object ProcessRecoveryRunSingleWriterGuard : RecoveryRunSingleWriterGuard {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    override fun tryAcquire(runId: RunId): RecoveryRunWriterLease? {
        val lock = locks.computeIfAbsent(runId.toCanonicalString()) { ReentrantLock() }
        if (!lock.tryLock()) return null
        return RecoveryRunWriterLease { lock.unlock() }
    }
}
