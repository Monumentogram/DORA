package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioReadSummary
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingRecoverySaveTest {
    private val identity =
        AudioIdentity(
            RecordingId("00000000-0000-0000-0000-000000000001"),
            AudioAssetId("00000000-0000-0000-0000-000000000002"),
            "00000000-0000-0000-0000-000000000003",
        )

    private fun recovered(frames: Long) =
        RecordingRecovery(
            identity,
            AudioReadSummary(identity, frames, AudioCompletion.PARTIAL_RECOVERED),
            null,
            true,
            1,
        )

    @Test
    fun cleanPrefixAtExactCapCanBeSavedWhileCaptureResumeRemainsUnavailable() {
        val capped = recovered(AlphaRecordingLimit.MAX_ALPHA_FRAMES)
        assertTrue(capped.canSavePrefix)
        val session = RecordingSession(identity, NoOpRecordingWriter())
        session.restore(capped.recoveredFrames, capped.nextOrdinal)
        assertFalse(session.canCapture)
        assertFalse(session.resume())
        assertTrue(recovered(80_000).canSavePrefix)
    }

    @Test
    fun emptyOversizeFailedForeignAndUncleanObservationsCannotOfferSave() {
        val clean = recovered(80_000)
        listOf(
                recovered(0),
                recovered(AlphaRecordingLimit.MAX_ALPHA_FRAMES + 1),
                clean.copy(canResume = false),
                clean.copy(failure = AudioFailure.LOCKED),
                clean.copy(summary = clean.summary!!.copy(tailFailure = AudioFailure.CORRUPT)),
                clean.copy(
                    summary = clean.summary!!.copy(identity = identity.copy(sessionId = "foreign"))
                ),
                clean.copy(completionState = RecordingCompletionState.DELETION_PENDING),
                clean.copy(summary = clean.summary!!.copy(completion = AudioCompletion.FINALIZED)),
            )
            .forEach { assertFalse(it.canSavePrefix) }
    }

    private class NoOpRecordingWriter : com.monumentogram.dora.audio.ProductAudioWriterPort {
        override fun create(identity: AudioIdentity) = unavailable()

        override fun append(
            segment: com.monumentogram.dora.audio.AudioStorageUnitIdentity,
            format: com.monumentogram.dora.audio.AudioFormat,
            pcm: ByteArray,
        ) = unavailable()

        override fun finalize(identity: AudioIdentity) = unavailable()

        override fun reconcile(identity: AudioIdentity) = unavailable()

        private fun unavailable() =
            com.monumentogram.dora.audio.AudioResult.Failed(AudioFailure.UNAVAILABLE)
    }
}
