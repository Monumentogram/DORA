package com.monumentogram.dora.poc.recovery.journal

import com.monumentogram.dora.poc.recovery.candidate.RecoveryStreamingCheckpointCommit
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult

/** Observes the real journal transaction while retaining its exact readback/error resolution. */
internal class RecoveryStreamingCheckpointCommitAdapter(
    private val database: RecoveryStreamingJournalDatabase
) : RecoveryStreamingCheckpointCommit {
    @Suppress("TooGenericExceptionCaught")
    override fun publish(
        row: RecoveryStreamingCheckpointRow,
        beforeEnd: () -> Unit,
        afterEnd: () -> Unit,
    ): RecoveryStreamingJournalResult {
        var observationFailure: Throwable? = null
        fun observe(callback: () -> Unit) {
            if (observationFailure == null) {
                try {
                    callback()
                } catch (failure: Throwable) {
                    observationFailure = failure
                }
            }
        }
        val observed =
            object : RecoveryStreamingJournalDatabase by database {
                override fun beginTransactionNonExclusive(): RecoveryStreamingJournalTransaction {
                    val transaction = database.beginTransactionNonExclusive()
                    return object : RecoveryStreamingJournalTransaction by transaction {
                        private var inserted = false
                        private var marked = false

                        override fun insertCheckpoint(row: RecoveryStreamingCheckpointRow) {
                            transaction.insertCheckpoint(row)
                            inserted = true
                        }

                        override fun setSuccessful() {
                            transaction.setSuccessful()
                            marked = true
                        }

                        override fun end() {
                            if (inserted && marked) observe(beforeEnd)
                            // A failed/expired observer must not leave an open SQLite transaction.
                            transaction.end()
                            if (inserted && marked) observe(afterEnd)
                        }
                    }
                }
            }
        val result = AndroidRecoveryStreamingJournal(observed).insertCheckpoint(row)
        // Exact row readback may resolve a commit after an observer timeout; it cannot erase the
        // external evidence failure or manufacture a successfully observed hard-kill boundary.
        observationFailure?.let { throw it }
        return result
    }
}
