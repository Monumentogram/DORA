package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryQuarantineControllerTest {
    @Test
    fun `Q01 through Q05 completes in exact order and evidence follows commit`() {
        val fixture = Fixture()
        val result = fixture.run() as QuarantineResult.Completed
        assertEquals(
            listOf(
                "prepare",
                "begin",
                "insert",
                "mark",
                "end",
                "inspect",
                "rename",
                "inspect",
                "source-fsync",
                "destination-fsync",
                "begin",
                "complete",
                "mark",
                "end",
                "load",
                "evidence",
            ),
            fixture.events,
        )
        assertTrue(result.evidenceEmitted)
        assertEquals(QuarantineOperationState.CONFIRMED, result.remainder.completionCommit)
    }

    @Test
    fun `completed exact replay is filesystem no-op but retries logical evidence`() {
        val fixture = Fixture()
        fixture.run()
        fixture.events.clear()
        val replay = fixture.run() as QuarantineResult.Completed
        assertEquals(listOf("prepare", "load", "inspect", "evidence"), fixture.events)
        assertTrue(replay.evidenceEmitted)
    }

    @Test
    fun `both present collides even when destination identity is exact`() {
        val fixture = Fixture()
        fixture.storage.observation =
            QuarantinePathObservation(QuarantinePathState.EXACT, QuarantinePathState.EXACT)
        assertTrue(fixture.run() is QuarantineResult.Collision)
        assertFalse("rename" in fixture.events)
    }

    @Test
    fun `post completion evidence failure preserves completed state`() {
        val fixture = Fixture(evidenceFails = true)
        val result = fixture.run() as QuarantineResult.Completed
        assertFalse(result.evidenceEmitted)
        assertEquals(QuarantineIntentState.COMPLETED, fixture.journal.row?.state)
        assertEquals(QuarantineOperationState.CONFIRMED, result.remainder.completionCommit)
    }

    @Test
    fun `filesystem faults report exact step and conservative remainder`() {
        listOf(
                "rename" to QuarantineStep.Q02,
                "source-fsync" to QuarantineStep.Q03,
                "destination-fsync" to QuarantineStep.Q04,
            )
            .forEach { (fault, expectedStep) ->
                val fixture = Fixture(storageFault = fault)
                val result = fixture.run() as QuarantineResult.RetryRequired
                assertEquals(expectedStep, result.failedStep)
                if (fault == "rename") {
                    assertEquals(QuarantineOperationState.OUTCOME_UNKNOWN, result.remainder.rename)
                } else {
                    assertEquals(QuarantineOperationState.CONFIRMED, result.remainder.rename)
                }
                assertEquals(
                    QuarantineOperationState.NOT_ATTEMPTED,
                    result.remainder.completionCommit,
                )
            }
    }

    @Test
    fun `all replay path states are fail closed except exact completed destination`() {
        val fixture = Fixture()
        fixture.run()
        listOf(
                QuarantinePathObservation(QuarantinePathState.EXACT, QuarantinePathState.ABSENT),
                QuarantinePathObservation(QuarantinePathState.ABSENT, QuarantinePathState.ABSENT),
            )
            .forEach { observation ->
                fixture.storage.observation = observation
                assertTrue(fixture.run() is QuarantineResult.RetryRequired)
            }
        fixture.storage.observation =
            QuarantinePathObservation(QuarantinePathState.UNSAFE, QuarantinePathState.ABSENT)
        assertTrue(fixture.run() is QuarantineResult.UnsafePath)
        fixture.storage.observation =
            QuarantinePathObservation(QuarantinePathState.OCCUPIED, QuarantinePathState.EXACT)
        assertTrue(fixture.run() is QuarantineResult.Collision)
    }

    @Test
    fun `persisted identity mismatch is rejected while new observation does not fork intent`() {
        val fixture = Fixture()
        fixture.journal.row =
            RecoveryQuarantineIntentRow(
                com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent.calculate(
                    fixture.input
                ),
                fixture.input.copy(artifactRole = RecoveryQuarantineArtifactRole.UNKNOWN_REGULAR),
                RecoveryQuarantineObservedState.FINAL_ORPHAN,
                QuarantineBootstrapBinding.PRESENT,
                com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent.destination(
                    fixture.input
                ),
                QuarantineIntentState.PENDING,
            )
        assertTrue(fixture.run() is QuarantineResult.RetryRequired)
    }

    private class Fixture(evidenceFails: Boolean = false, storageFault: String? = null) {
        val events = mutableListOf<String>()
        val input =
            RecoveryQuarantineIntentInput(
                RecoveryCandidate.MICROFILE,
                RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff"),
                "units/u-0000000000.ct.tmp",
                RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
                1UL,
                Sha256Value.calculate(byteArrayOf(1)),
            )
        val storage = Storage(events, storageFault)
        val journal = Journal(events)
        private val controller =
            RecoveryQuarantineController(storage, journal) {
                events += "evidence"
                if (evidenceFails) error("evidence")
            }

        fun run() =
            controller.quarantine(
                input,
                RecoveryQuarantineObservedState.TEMP_ONLY,
                QuarantineBootstrapBinding.PRESENT,
            )
    }

    private class Storage(
        private val events: MutableList<String>,
        private val fault: String? = null,
    ) : RecoveryQuarantineStorage {
        var observation =
            QuarantinePathObservation(QuarantinePathState.EXACT, QuarantinePathState.ABSENT)

        override fun prepare(runId: RunId) {
            events += "prepare"
        }

        override fun inspect(row: RecoveryQuarantineIntentRow): QuarantinePathObservation {
            events += "inspect"
            return observation
        }

        override fun renameNoOverwrite(row: RecoveryQuarantineIntentRow) {
            events += "rename"
            if (fault == "rename") error("rename")
            observation =
                QuarantinePathObservation(QuarantinePathState.ABSENT, QuarantinePathState.EXACT)
        }

        override fun fsyncSourceParent(row: RecoveryQuarantineIntentRow) {
            events += "source-fsync"
            if (fault == "source-fsync") error("source-fsync")
        }

        override fun fsyncDestinationParent(row: RecoveryQuarantineIntentRow) {
            events += "destination-fsync"
            if (fault == "destination-fsync") error("destination-fsync")
        }
    }

    private class Journal(private val events: MutableList<String>) : RecoveryQuarantineJournal {
        var row: RecoveryQuarantineIntentRow? = null

        override fun load(intentId: Sha256Value): RecoveryQuarantineIntentRow? {
            if (row != null) events += "load"
            return row
        }

        override fun beginNonExclusive(): RecoveryQuarantineTransaction {
            events += "begin"
            return object : RecoveryQuarantineTransaction {
                private var inserted: RecoveryQuarantineIntentRow? = null
                private var complete = false

                override fun insert(row: RecoveryQuarantineIntentRow) {
                    events += "insert"
                    inserted = row
                }

                override fun complete(intentId: Sha256Value) {
                    events += "complete"
                    complete = true
                }

                override fun markSuccessful() {
                    events += "mark"
                }

                override fun end() {
                    events += "end"
                    inserted?.let { row = it }
                    if (complete) row = row?.copy(state = QuarantineIntentState.COMPLETED)
                }
            }
        }
    }
}
