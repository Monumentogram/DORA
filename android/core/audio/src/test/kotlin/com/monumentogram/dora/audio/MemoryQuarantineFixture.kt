@file:Suppress("WildcardImport", "TooManyFunctions")

package com.monumentogram.dora.audio

import com.monumentogram.dora.poc.recovery.candidate.*
import com.monumentogram.dora.poc.recovery.contract.*

/** Volatile storage/journal collaborators for the unchanged accepted quarantine controller. */
internal class MemoryQuarantineFixture(private val files: MutableMap<String, ByteArray>) {
    val rows = mutableMapOf<Sha256Value, RecoveryQuarantineIntentRow>()
    var failRename = false
    val controller =
        RecoveryQuarantineController(
            object : RecoveryQuarantineStorage {
                override fun prepare(runId: RunId) = Unit

                override fun inspect(row: RecoveryQuarantineIntentRow): QuarantinePathObservation {
                    fun state(name: String): QuarantinePathState {
                        val bytes =
                            files["${row.input.runId.toCanonicalString()}/$name"]
                                ?: return QuarantinePathState.ABSENT
                        return if (
                            bytes.size.toULong() == row.input.sourceBytes &&
                                Sha256Value.calculate(bytes) == row.input.sourceSha256
                        )
                            QuarantinePathState.EXACT
                        else QuarantinePathState.OCCUPIED
                    }
                    return QuarantinePathObservation(
                        state(row.input.sourceRelativeName),
                        state(row.destinationRelativeName),
                    )
                }

                override fun renameNoOverwrite(row: RecoveryQuarantineIntentRow) {
                    check(!failRename)
                    val root = row.input.runId.toCanonicalString()
                    check("$root/${row.destinationRelativeName}" !in files)
                    files["$root/${row.destinationRelativeName}"] =
                        requireNotNull(files.remove("$root/${row.input.sourceRelativeName}"))
                }

                override fun fsyncSourceParent(row: RecoveryQuarantineIntentRow) = Unit

                override fun fsyncDestinationParent(row: RecoveryQuarantineIntentRow) = Unit
            },
            object : RecoveryQuarantineJournal {
                override fun load(intentId: Sha256Value) = rows[intentId]

                override fun loadBySource(input: RecoveryQuarantineIntentInput) =
                    rows.values.singleOrNull { it.input == input }

                override fun loadPending(runId: RunId) = pending(runId)

                override fun beginNonExclusive() =
                    object : RecoveryQuarantineTransaction {
                        private val changes = rows.toMutableMap()
                        private var successful = false

                        override fun insert(row: RecoveryQuarantineIntentRow) {
                            check(row.intentId !in changes)
                            changes[row.intentId] = row
                        }

                        override fun complete(intentId: Sha256Value) {
                            changes[intentId] =
                                changes
                                    .getValue(intentId)
                                    .copy(state = QuarantineIntentState.COMPLETED)
                        }

                        override fun markSuccessful() {
                            successful = true
                        }

                        override fun end() {
                            if (successful) {
                                rows.clear()
                                rows.putAll(changes)
                            }
                        }
                    }
            },
            RecoveryQuarantineEvidenceSink {},
        )

    fun pending(run: RunId) =
        rows.values.filter { it.input.runId == run && it.state == QuarantineIntentState.PENDING }
}
