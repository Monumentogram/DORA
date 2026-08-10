@file:Suppress("TooManyFunctions") // Each public function is one explicit audited transition.

package com.monumentogram.dora.poc.capture.state

import com.monumentogram.dora.poc.capture.model.CaptureOutcome
import com.monumentogram.dora.poc.capture.model.CaptureUiState
import com.monumentogram.dora.poc.capture.model.DeletionReceipt
import com.monumentogram.dora.poc.capture.model.DeviceProfile
import com.monumentogram.dora.poc.capture.model.FlowPhase
import com.monumentogram.dora.poc.capture.model.LiveMetrics
import com.monumentogram.dora.poc.capture.model.ManualObservations
import com.monumentogram.dora.poc.capture.model.RecoveryCandidate
import com.monumentogram.dora.poc.capture.model.RunKind

class InvalidTransition(message: String) : IllegalStateException(message)

class CaptureStateMachine(initial: CaptureUiState = CaptureUiState()) {
    var state: CaptureUiState = initial
        private set

    fun showRecovery(candidate: RecoveryCandidate): CaptureUiState =
        set(
            state.copy(
                phase = FlowPhase.RECOVERY,
                recoveryCandidate = candidate,
                acknowledgementChecked = false,
            )
        )

    fun prepared(profile: DeviceProfile, message: String): CaptureUiState =
        set(
            state.copy(
                phase = FlowPhase.RUN_SELECTION,
                preparedProfile = profile,
                preparationMessage = message,
                errorMessage = null,
            )
        )

    fun refreshProfile(profile: DeviceProfile): CaptureUiState =
        set(state.copy(preparedProfile = profile))

    fun selectRun(run: RunKind): CaptureUiState {
        requirePhase(FlowPhase.RUN_SELECTION)
        if (!state.isUnlocked(run)) {
            throw InvalidTransition("${run.title} пока заблокирован")
        }
        return set(
            state.copy(
                phase = FlowPhase.PREFLIGHT,
                selectedRun = run,
                acknowledgementChecked = false,
                fixtureEnabled = false,
                outcome = null,
                deletionReceipt = null,
                observations = ManualObservations(),
                exportMessage = null,
                errorMessage = null,
            )
        )
    }

    fun setAcknowledgement(checked: Boolean): CaptureUiState {
        requirePhase(FlowPhase.PREFLIGHT)
        return set(state.copy(acknowledgementChecked = checked))
    }

    fun setFixtureEnabled(enabled: Boolean): CaptureUiState {
        requirePhase(FlowPhase.PREFLIGHT)
        val run = requireNotNull(state.selectedRun)
        if (enabled && !run.fixtureAllowed) {
            throw InvalidTransition("Тестовый сигнал разрешён только для Run A")
        }
        return set(state.copy(fixtureEnabled = enabled))
    }

    fun requestStart(): CaptureUiState {
        requirePhase(FlowPhase.PREFLIGHT)
        if (!state.acknowledgementChecked) {
            throw InvalidTransition("Перед запуском необходимо поставить checkbox")
        }
        return set(state.copy(phase = FlowPhase.STARTING, errorMessage = null))
    }

    fun recording(metrics: LiveMetrics): CaptureUiState {
        if (state.phase != FlowPhase.STARTING && state.phase != FlowPhase.RECORDING) {
            throw InvalidTransition("Запись не может начаться из состояния ${state.phase}")
        }
        return set(state.copy(phase = FlowPhase.RECORDING, liveMetrics = metrics))
    }

    fun updateMetrics(metrics: LiveMetrics): CaptureUiState {
        requirePhase(FlowPhase.RECORDING)
        return set(state.copy(liveMetrics = metrics))
    }

    fun finished(outcome: CaptureOutcome): CaptureUiState {
        if (state.phase != FlowPhase.RECORDING && state.phase != FlowPhase.STARTING) {
            throw InvalidTransition("Нельзя завершить запись из состояния ${state.phase}")
        }
        return set(
            state.copy(
                phase = FlowPhase.REVIEW,
                acknowledgementChecked = false,
                liveMetrics = null,
                outcome = outcome,
                errorMessage = null,
            )
        )
    }

    fun deleted(receipt: DeletionReceipt): CaptureUiState {
        requirePhase(FlowPhase.REVIEW)
        if (!receipt.deletionSucceeded || !receipt.absenceVerified) {
            return fail("Raw audio не удалён; экспорт заблокирован")
        }
        return set(
            state.copy(
                phase = FlowPhase.QUESTIONNAIRE,
                deletionReceipt = receipt,
                acknowledgementChecked = false,
                errorMessage = null,
            )
        )
    }

    fun observations(value: ManualObservations): CaptureUiState {
        requirePhase(FlowPhase.QUESTIONNAIRE)
        if (!value.complete) throw InvalidTransition("Ответьте на все пять вопросов")
        val run = requireNotNull(state.selectedRun)
        val outcome = requireNotNull(state.outcome)
        val receipt = requireNotNull(state.deletionReceipt)
        val critical =
            outcome.hasCriticalCaptureFailure ||
                value.reportsCriticalFailure ||
                !receipt.deletionSucceeded ||
                !receipt.absenceVerified
        return set(
            state.copy(
                phase = FlowPhase.READY_TO_EXPORT,
                observations = value,
                completedRuns = state.completedRuns + run,
                criticalRuns = if (critical) state.criticalRuns + run else state.criticalRuns,
            )
        )
    }

    fun markExported(message: String): CaptureUiState {
        requirePhase(FlowPhase.READY_TO_EXPORT)
        return set(state.copy(exportMessage = message))
    }

    fun backToRuns(): CaptureUiState {
        if (
            state.phase == FlowPhase.RECORDING ||
                state.phase == FlowPhase.STARTING ||
                state.phase == FlowPhase.RECOVERY
        ) {
            throw InvalidTransition("Сначала безопасно завершите текущую операцию")
        }
        return set(
            state.copy(
                phase =
                    if (state.preparedProfile == null) FlowPhase.DEVICE
                    else FlowPhase.RUN_SELECTION,
                selectedRun = null,
                acknowledgementChecked = false,
                fixtureEnabled = false,
                liveMetrics = null,
                outcome = null,
                deletionReceipt = null,
                observations = ManualObservations(),
                exportMessage = null,
                errorMessage = null,
            )
        )
    }

    fun aborted(message: String): CaptureUiState {
        if (
            state.phase != FlowPhase.STARTING &&
                state.phase != FlowPhase.RECORDING &&
                state.phase != FlowPhase.REVIEW
        ) {
            throw InvalidTransition("Прерывание недоступно из состояния ${state.phase}")
        }
        return set(
            state.copy(
                phase =
                    if (state.preparedProfile == null) FlowPhase.DEVICE
                    else FlowPhase.RUN_SELECTION,
                selectedRun = null,
                acknowledgementChecked = false,
                fixtureEnabled = false,
                liveMetrics = null,
                outcome = null,
                deletionReceipt = null,
                observations = ManualObservations(),
                preparationMessage = message,
                exportMessage = null,
                errorMessage = null,
            )
        )
    }

    fun recoveryResolved(message: String): CaptureUiState =
        set(
            state.copy(
                phase =
                    if (state.preparedProfile == null) FlowPhase.DEVICE
                    else FlowPhase.RUN_SELECTION,
                recoveryCandidate = null,
                acknowledgementChecked = false,
                preparationMessage = message,
                errorMessage = null,
            )
        )

    fun fail(message: String): CaptureUiState =
        set(
            state.copy(
                phase = FlowPhase.ERROR,
                acknowledgementChecked = false,
                liveMetrics = null,
                errorMessage = message,
            )
        )

    private fun requirePhase(expected: FlowPhase) {
        if (state.phase != expected) {
            throw InvalidTransition("Ожидалось состояние $expected, получено ${state.phase}")
        }
    }

    private fun set(next: CaptureUiState): CaptureUiState {
        state = next
        return next
    }
}
