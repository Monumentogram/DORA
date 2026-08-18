package com.monumentogram.dora.model

enum class BootstrapDestination(val route: String) {
    HOME("home"),
    HISTORY("history"),
    TASKS("tasks"),
    SETTINGS("settings");

    companion object {
        val ordered: List<BootstrapDestination> = entries
    }
}

enum class BootstrapRecordingAvailability {
    ALPHA_1_UNAVAILABLE
}

data class BootstrapRecordingActionState(
    val availability: BootstrapRecordingAvailability =
        BootstrapRecordingAvailability.ALPHA_1_UNAVAILABLE,
    val canStartRecording: Boolean = false,
    val requestsMicrophonePermission: Boolean = false,
)

data class BootstrapUiState(
    val selectedDestination: BootstrapDestination = BootstrapDestination.HOME,
    val recordingAction: BootstrapRecordingActionState = BootstrapRecordingActionState(),
)

sealed interface BootstrapAction {
    data class SelectDestination(val destination: BootstrapDestination) : BootstrapAction

    data object InvokeUnavailableRecording : BootstrapAction
}

sealed interface BootstrapEffect {
    data object ShowRecordingUnavailableNotice : BootstrapEffect
}

data class BootstrapUpdate(
    val state: BootstrapUiState,
    val effect: BootstrapEffect? = null,
)

fun BootstrapUiState.reduce(action: BootstrapAction): BootstrapUpdate =
    when (action) {
        is BootstrapAction.SelectDestination ->
            BootstrapUpdate(state = copy(selectedDestination = action.destination))
        BootstrapAction.InvokeUnavailableRecording ->
            BootstrapUpdate(
                state = this,
                effect = BootstrapEffect.ShowRecordingUnavailableNotice,
            )
    }
