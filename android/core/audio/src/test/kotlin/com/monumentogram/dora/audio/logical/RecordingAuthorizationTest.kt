package com.monumentogram.dora.audio.logical

import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingAuthorizationTest {
    private val recording = RecordingId(LogicalRecordingProjectionTest.id(1))

    @Test
    fun askHasOneOpportunityAcrossOneTwoTenChunks() {
        for (count in listOf(1, 2, 10)) {
            var state = RecordingAuthorization(recording, RecordingConsentMode.ASK_EACH_RECORDING)
            var prompts = 0
            repeat(count) {
                val result = state.automaticOpportunity()
                prompts += if (result.prompt != null) 1 else 0
                state = result.state
            }
            assertEquals(1, prompts)
            assertNull(state.basis())
        }
    }

    @Test
    fun deferredIsStickyAcrossAllChunkAndSemanticEvents() {
        var state = RecordingAuthorization(recording, RecordingConsentMode.ASK_EACH_RECORDING)
        assertNotNull(state.automaticOpportunity().prompt)
        state = state.automaticOpportunity().state.defer()
        repeat(20) {
            val next = state.automaticOpportunity()
            assertNull(next.prompt)
            assertNull(next.state.basis())
            assertEquals(RecordingConsentState.DEFERRED, next.state.status)
            state = next.state
        }
    }

    @Test
    fun alwaysUsesOneRecordingBasisAcrossActualChunkProjection() {
        val source = LogicalRecordingProjectionTest.source(9600000L * 10)
        val projection =
            LogicalRecordingProjectionTest.ready(
                source,
                LogicalRecordingProjectionTest.rows(source.frames),
            )
        val state = RecordingAuthorization(recording, RecordingConsentMode.ALWAYS).authorizeAlways()
        val bases = projection.technicalChunks.map { state.basisFor(it.recordingId) }
        assertEquals(1, bases.toSet().size)
        assertEquals(projection.authorizationUnitId, bases.first()!!.unit)
        assertTrue(projection.technicalChunks.all { it.sourceAudioReference === source })
        assertNull(state.automaticOpportunity().prompt)
    }

    @Test
    fun alwaysWithoutValidGrantDoesNotAuthorize() {
        val state = RecordingAuthorization(recording, RecordingConsentMode.ALWAYS)
        assertNull(state.basis())
        assertNull(state.automaticOpportunity().prompt)
    }

    @Test
    fun manualIsOneExplicitRecordingAction() {
        val before = RecordingAuthorization(recording, RecordingConsentMode.MANUAL_ONLY)
        assertNull(before.automaticOpportunity().prompt)
        assertNull(before.basis())
        val after = before.recognizeExplicitly()
        assertEquals(
            RecordingAuthorizationBasis(
                AuthorizationUnitId(recording),
                AuthorizationOrigin.EXPLICIT_RECORDING_ACTION,
            ),
            after.basis(),
        )
        repeat(10) { assertEquals(after.basis(), after.basisFor(recording)) }
        assertNull(after.basisFor(RecordingId(LogicalRecordingProjectionTest.id(2))))
    }

    @Test
    fun askGrantIsRecordingScopedAndSuppressesFutureOpportunities() {
        val state =
            RecordingAuthorization(recording, RecordingConsentMode.ASK_EACH_RECORDING)
                .automaticOpportunity()
                .state
                .acceptPrompt()
        assertNotNull(state.basis())
        assertNull(state.automaticOpportunity().prompt)
    }

    @Test
    fun cannotAcceptUnofferedPromptOrAlwaysInAnotherMode() {
        assertThrows(IllegalStateException::class.java) {
            RecordingAuthorization(recording, RecordingConsentMode.ASK_EACH_RECORDING)
                .acceptPrompt()
        }
        assertThrows(IllegalStateException::class.java) {
            RecordingAuthorization(recording, RecordingConsentMode.MANUAL_ONLY).authorizeAlways()
        }
    }

    @Test
    fun batchCountsRecordingsNotTwelveChunks() {
        val scopes =
            listOf(1, 8, 3).flatMapIndexed { index, chunks ->
                List(chunks) {
                    AuthorizationUnitId(RecordingId(LogicalRecordingProjectionTest.id(index + 1)))
                }
            }
        assertEquals(3, RecordingAuthorization.batchUnits(scopes).size)
    }

    @Test
    fun mixedStartRotationsPauseResumeAndSemanticsStillAskOnce() {
        val cap = LogicalRecordingProjectionTest.CAP
        val rows = LogicalRecordingProjectionTest.rows(cap * 2).toMutableList()
        rows +=
            LogicalRecordingProjectionTest.pair(
                LogicalRecordingProjectionTest.id(12),
                LogicalRecordingProjectionTest.id(12),
                cap * 2,
                cap * 2 + 16000,
                "RESUME",
                "STOP",
            )
        val projected =
            LogicalRecordingProjectionTest.ready(
                LogicalRecordingProjectionTest.source(cap * 2 + 16000),
                rows,
            )
        var state =
            RecordingAuthorization(projected.recordingId, RecordingConsentMode.ASK_EACH_RECORDING)
        var prompts = 0
        for (chunk in projected.technicalChunks) {
            assertEquals(recording, chunk.recordingId)
            val next = state.automaticOpportunity()
            if (next.prompt != null) prompts++
            state = next.state
        }
        assertEquals(1, prompts)
        state = state.defer()
        assertNull(state.automaticOpportunity().prompt)
    }
}
