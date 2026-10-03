package com.monumentogram.dora.audio.logical

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.InvalidSegmentationMetadata
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedLogicalRecordingsTest {
    private val source = LogicalRecordingProjectionTest.source(16000)

    @Test
    fun allPagesReadOnlyWhileSourceLeaseHeld() {
        val port = Source()
        var calls = 0
        val rows = LogicalRecordingProjectionTest.rows(16000).sortedBy { it.key }
        val read =
            AuthenticatedLogicalRecordings(port) { _, after ->
                    assertTrue(port.held)
                    calls++
                    rows.filter { it.key > after }.take(1)
                }
                .read(source)
        assertTrue((read as AudioResult.Value).value is LogicalRecordingResult.Ready)
        assertEquals(3, calls)
        assertFalse(port.held)
    }

    @Test
    fun sourceFailureNeverReadsMetadata() {
        for (status in
            listOf(
                OriginalAudioStatus.NotFinalized,
                OriginalAudioStatus.StaleReference,
                OriginalAudioStatus.DeletionPending,
                OriginalAudioStatus.SourceDeleted,
            )) {
            val port = Source(status)
            assertEquals(
                AudioResult.Value(LogicalRecordingResult.SourceUnavailable(status)),
                AuthenticatedLogicalRecordings(port) { _, _ -> error("Unavailable source") }
                    .read(source),
            )
        }
    }

    @Test
    fun failedDeliveryCannotPublishBuiltProjection() {
        val port = Source(failAfter = true)
        val rows = LogicalRecordingProjectionTest.rows(16000).sortedBy { it.key }
        assertEquals(
            AudioResult.Failed(AudioFailure.LOCKED),
            AuthenticatedLogicalRecordings(port) { _, after -> rows.filter { it.key > after } }
                .read(source),
        )
    }

    @Test
    fun repeatedPageIsTypedIncompleteWithoutPartialChunks() {
        val rows = LogicalRecordingProjectionTest.rows(16000).sortedBy { it.key }
        val result = AuthenticatedLogicalRecordings(Source()) { _, _ -> rows }.read(source)
        assertEquals(
            AudioResult.Value(
                LogicalRecordingResult.Incomplete(ProjectionFailure.MALFORMED_METADATA)
            ),
            result,
        )
    }

    @Test
    fun decodedJournalCorruptionIsTypedIncomplete() {
        val result =
            AuthenticatedLogicalRecordings(Source()) { _, _ -> throw InvalidSegmentationMetadata() }
                .read(source)
        assertEquals(
            AudioResult.Value(
                LogicalRecordingResult.Incomplete(ProjectionFailure.MALFORMED_METADATA)
            ),
            result,
        )
    }

    private inner class Source(
        private val status: OriginalAudioStatus = OriginalAudioStatus.Available(source),
        private val failAfter: Boolean = false,
    ) : OriginalAudioPort {
        var held = false

        override fun acquire(identity: AudioIdentity): AudioResult<OriginalAudioStatus> =
            error("No latest source")

        override fun inspect(reference: OriginalAudioReference): AudioResult<OriginalAudioStatus> =
            error("No separate check")

        override fun extract(
            reference: OriginalAudioReference,
            consume: (Long, ByteArray) -> Unit,
        ): AudioResult<OriginalAudioStatus> = error("No PCM copy")

        override fun withAvailable(
            reference: OriginalAudioReference,
            action: () -> Unit,
        ): AudioResult<OriginalAudioStatus> {
            assertEquals(source, reference)
            if (status is OriginalAudioStatus.Available) {
                held = true
                try {
                    action()
                } finally {
                    held = false
                }
            }
            return if (failAfter) AudioResult.Failed(AudioFailure.LOCKED)
            else AudioResult.Value(status)
        }
    }
}
