package com.monumentogram.dora.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class BootstrapUiStateTest {
    @Test
    fun exposesExactlyFourOrderedNavigationDestinations() {
        assertEquals(
            listOf(
                BootstrapDestination.HOME,
                BootstrapDestination.HISTORY,
                BootstrapDestination.TASKS,
                BootstrapDestination.SETTINGS,
            ),
            BootstrapDestination.ordered,
        )
    }

    @Test
    fun selectingDestinationUpdatesOnlyCurrentSection() {
        val initial = BootstrapUiState()

        val update = initial.reduce(BootstrapAction.SelectDestination(BootstrapDestination.HISTORY))

        assertEquals(BootstrapDestination.HISTORY, update.state.selectedDestination)
        assertEquals(initial.recordingAction, update.state.recordingAction)
        assertEquals(null, update.effect)
    }

    @Test
    fun recordingActionIsAnHonestStage00Placeholder() {
        val initial = BootstrapUiState(selectedDestination = BootstrapDestination.TASKS)

        val update = initial.reduce(BootstrapAction.InvokeRecordingPlaceholder)

        assertSame(initial, update.state)
        assertEquals(
            BootstrapEffect.ShowRecordingUnavailableNotice,
            update.effect,
        )
        assertEquals(
            BootstrapRecordingAvailability.STAGE_00_PLACEHOLDER,
            update.state.recordingAction.availability,
        )
        assertFalse(update.state.recordingAction.canStartRecording)
        assertFalse(update.state.recordingAction.requestsMicrophonePermission)
    }
}
