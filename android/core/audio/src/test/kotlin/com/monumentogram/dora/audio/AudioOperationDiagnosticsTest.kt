package com.monumentogram.dora.audio

import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOperationDiagnosticsTest {
    @Test
    fun realFinalizationReportsEveryAuthenticatedUnitAndDurableCommitWithoutIdentity() {
        val fixture = AudioMemoryFixture()
        fun id() = UUID.randomUUID().toString()
        val identity = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())
        assertEquals(AudioResult.Value(Unit), fixture.bridge.create(identity))
        repeat(2) { index ->
            assertEquals(
                AudioResult.Value(Unit),
                fixture.bridge.append(
                    AudioStorageUnitIdentity(identity, id(), index, index.toLong()),
                    AudioFormat.PCM,
                    byteArrayOf(1, 2),
                ),
            )
        }
        AudioOperationDiagnostics.enable()
        try {
            assertEquals(AudioResult.Value(Unit), fixture.bridge.finalize(identity))
            val text = AudioOperationDiagnostics.summary()
            assertTrue(text.contains("audio_operation=FINALIZE status=SUCCEEDED"))
            assertTrue(text.contains("completedUnits=2 totalUnits=2"))
            assertTrue(text.contains("RECOVERY_UNIT_count=2"))
            assertTrue(text.contains("MANIFEST_AUTH_count=2"))
            assertTrue(text.contains("PCM_AUTH_count=2"))
            assertTrue(text.contains("CATALOG_COMMIT_count=1"))
            assertFalse(text.contains(identity.sessionId))
        } finally {
            AudioOperationDiagnostics.disableAndClear()
        }
    }

    @Test
    fun disabledCollectionRunsWithoutRetainingAnySnapshot() {
        val recorder = AudioDiagnosticRecorder { 0L }
        assertEquals(7, recorder.collect(AudioOperation.FINALIZE) { 7 })
        assertTrue(recorder.snapshots().isEmpty())
    }

    @Test
    fun nullableMeasuredWorkRunsExactlyOnce() {
        val recorder = AudioDiagnosticRecorder { 0L }
        recorder.enabled = true
        var calls = 0
        recorder.collect(AudioOperation.FINALIZE) {
            recorder.measure(AudioStage.KEY_OPEN) {
                calls++
                null
            }
        }
        assertEquals(1, calls)
    }

    @Test
    fun liveProgressAndNestedStageTotalsAreFixedScalars() {
        var now = 10L
        val recorder = AudioDiagnosticRecorder { now }
        recorder.enabled = true
        recorder.collect(AudioOperation.FINALIZE) {
            recorder.progress(0, 4)
            recorder.measure(AudioStage.KEY_OPEN) {
                now += 2
                recorder.measure(AudioStage.KEY_INFO) { now += 3 }
                now += 5
            }
            recorder.progress(1, 4)
            val live = recorder.snapshots().single()
            assertEquals(AudioOperationStatus.RUNNING, live.status)
            assertEquals(1L, live.completedUnits)
            assertEquals(4L, live.totalUnits)
            assertEquals(10L, live.elapsedNanos)
            now += 7
        }
        val result = recorder.snapshots().single()
        assertEquals(AudioOperationStatus.SUCCEEDED, result.status)
        assertEquals(17L, result.elapsedNanos)
        assertEquals(AudioStageTiming(10, 1), result.stages[AudioStage.KEY_OPEN])
        assertEquals(AudioStageTiming(3, 1), result.stages[AudioStage.KEY_INFO])
    }

    @Test
    fun cancellationFailureAndLateCompletionDoNotLeakOrReplaceNewerOperation() {
        var now = 0L
        val recorder = AudioDiagnosticRecorder { now }
        recorder.enabled = true
        try {
            recorder.collect(AudioOperation.AUTHENTICATED_OPEN) {
                now = 9
                throw CancellationException("private-caller-value")
            }
        } catch (_: CancellationException) {
            // Cancellation retains only the enum, never this exception or message.
        }
        assertEquals(AudioOperationStatus.CANCELLED, recorder.snapshots().single().status)
        val old = checkNotNull(recorder.begin(AudioOperation.PLAYBACK_PREPARATION))
        now = 20
        val fresh = checkNotNull(recorder.begin(AudioOperation.PLAYBACK_PREPARATION))
        fresh.finish(AudioOperationStatus.SUCCEEDED)
        old.finish(AudioOperationStatus.FAILED)
        val snapshot = recorder.snapshots().last()
        assertEquals(AudioOperationStatus.SUCCEEDED, snapshot.status)
        assertEquals(0L, snapshot.elapsedNanos)
        assertFalse(recorder.render().contains("private-caller-value"))
        recorder.collect(AudioOperation.FINALIZE) { AudioResult.Failed(AudioFailure.CORRUPT) }
        assertEquals(AudioOperationStatus.FAILED, recorder.snapshots().first().status)
    }

    @Test
    fun nestedOperationsRestoreParentAndDisablingClearsObservations() {
        val recorder = AudioDiagnosticRecorder { 0L }
        recorder.enabled = true
        recorder.collect(AudioOperation.FINALIZE) {
            recorder.collect(AudioOperation.RANGE_READ) {
                recorder.measure(AudioStage.PCM_AUTH) {}
            }
            recorder.measure(AudioStage.CATALOG_COMMIT) {}
        }
        assertEquals(setOf(AudioStage.CATALOG_COMMIT), recorder.snapshots().first().stages.keys)
        assertEquals(setOf(AudioStage.PCM_AUTH), recorder.snapshots().last().stages.keys)
        recorder.disableAndClear()
        assertTrue(recorder.snapshots().isEmpty())
    }
}
