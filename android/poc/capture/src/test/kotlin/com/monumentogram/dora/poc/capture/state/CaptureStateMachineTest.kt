package com.monumentogram.dora.poc.capture.state

import com.monumentogram.dora.poc.capture.model.FlowPhase
import com.monumentogram.dora.poc.capture.model.LiveMetrics
import com.monumentogram.dora.poc.capture.model.ManualAnswer
import com.monumentogram.dora.poc.capture.model.ManualObservations
import com.monumentogram.dora.poc.capture.model.RunKind
import com.monumentogram.dora.poc.capture.model.ServiceState
import com.monumentogram.dora.poc.capture.testDeviceProfile
import com.monumentogram.dora.poc.capture.testOutcome
import com.monumentogram.dora.poc.capture.testReceipt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureStateMachineTest {
    @Test
    fun `start is impossible without acknowledgement`() {
        val machine = preparedMachine()
        machine.selectRun(RunKind.RUN_A)

        assertThrows(InvalidTransition::class.java) { machine.requestStart() }
        assertFalse(machine.state.acknowledgementChecked)
        assertFalse(machine.state.phase == FlowPhase.RECORDING)
    }

    @Test
    fun `checkbox resets after stop`() {
        val machine = recordingMachine(RunKind.RUN_A)

        machine.finished(testOutcome())

        assertFalse(machine.state.acknowledgementChecked)
        assertTrue(machine.state.phase == FlowPhase.REVIEW)
    }

    @Test
    fun `initial state never records automatically`() {
        val machine = CaptureStateMachine()

        assertTrue(machine.state.phase == FlowPhase.DEVICE)
        assertTrue(machine.state.liveMetrics == null)
    }

    @Test
    fun `invalid transitions are rejected`() {
        val machine = CaptureStateMachine()

        assertThrows(InvalidTransition::class.java) {
            machine.recording(LiveMetrics(RunKind.RUN_A, "run-a-test"))
        }
    }

    @Test
    fun `run B and run C unlock only in sequence`() {
        val machine = preparedMachine()
        assertFalse(machine.state.isUnlocked(RunKind.RUN_B))
        assertFalse(machine.state.isUnlocked(RunKind.RUN_C))

        completeRun(machine, RunKind.RUN_A)
        machine.backToRuns()
        assertTrue(machine.state.isUnlocked(RunKind.RUN_B))
        assertFalse(machine.state.isUnlocked(RunKind.RUN_C))

        completeRun(machine, RunKind.RUN_B)
        machine.backToRuns()
        assertTrue(machine.state.isUnlocked(RunKind.RUN_C))
    }

    @Test
    fun `hidden notification is critical and keeps run B locked`() {
        val machine = recordingMachine(RunKind.RUN_A)
        machine.finished(testOutcome())
        machine.deleted(testReceipt())
        machine.observations(completeObservations(notification = ManualAnswer.NO))
        machine.backToRuns()

        assertTrue(RunKind.RUN_A in machine.state.criticalRuns)
        assertFalse(machine.state.isUnlocked(RunKind.RUN_B))
    }

    @Test
    fun `failed deletion blocks export`() {
        val machine = recordingMachine(RunKind.RUN_A)
        machine.finished(testOutcome())

        machine.deleted(testReceipt(success = false))

        assertTrue(machine.state.phase == FlowPhase.ERROR)
        assertFalse(machine.state.phase == FlowPhase.READY_TO_EXPORT)
    }

    @Test
    fun `recording state and foreground service state change together`() {
        val machine = recordingMachine(RunKind.RUN_A)
        assertTrue(machine.state.liveMetrics?.serviceState?.name == "RECORDING")

        machine.finished(testOutcome())

        assertTrue(machine.state.liveMetrics == null)
        assertTrue(machine.state.phase == FlowPhase.REVIEW)
    }

    private fun preparedMachine(): CaptureStateMachine =
        CaptureStateMachine().also { it.prepared(testDeviceProfile(), "ready") }

    private fun recordingMachine(run: RunKind): CaptureStateMachine =
        preparedMachine().also { machine ->
            machine.selectRun(run)
            machine.setAcknowledgement(true)
            machine.requestStart()
            machine.recording(
                LiveMetrics(
                    run,
                    "${run.id}-test",
                    serviceState = ServiceState.RECORDING,
                )
            )
        }

    private fun completeRun(machine: CaptureStateMachine, run: RunKind) {
        machine.selectRun(run)
        machine.setAcknowledgement(true)
        machine.requestStart()
        machine.recording(
            LiveMetrics(
                run,
                "${run.id}-test",
                serviceState = ServiceState.RECORDING,
            )
        )
        machine.finished(testOutcome(run))
        machine.deleted(testReceipt(run = run))
        machine.observations(completeObservations())
    }

    private fun completeObservations(notification: ManualAnswer = ManualAnswer.YES) =
        ManualObservations(
            notificationVisible = notification,
            screenMostlyOff = ManualAnswer.YES,
            callOrInterruption = ManualAnswer.NO,
            phoneCharging = ManualAnswer.NO,
            overheatingOrUnexpectedStop = ManualAnswer.NO,
        )
}
