package com.monumentogram.dora.audio.recording

import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TechnicalRotationSessionTest {
    private class Writer : ProductAudioWriterPort {
        val units = mutableListOf<Pair<AudioStorageUnitIdentity, Int>>()
        val hash = MessageDigest.getInstance("SHA-256")

        override fun create(identity: AudioIdentity) = AudioResult.Value(Unit)

        override fun append(
            segment: AudioStorageUnitIdentity,
            format: AudioFormat,
            pcm: ByteArray,
        ): AudioResult<Unit> {
            units.add(segment to pcm.size / 2)
            hash.update(pcm)
            return AudioResult.Value(Unit)
        }

        override fun finalize(identity: AudioIdentity) = AudioResult.Value(Unit)

        override fun reconcile(identity: AudioIdentity) = AudioResult.Value(Unit)
    }

    @Test
    fun capInsideBlockPreservesHashSourceIdentityAndCaptureEpoch() {
        val writer = Writer()
        val identity =
            AudioIdentity(
                RecordingId("00000000-0000-4000-8000-000000000001"),
                AudioAssetId("00000000-0000-4000-8000-000000000002"),
                "s",
            )
        val session = RecordingSession(identity, writer)
        session.start()
        val epoch = session.captureEpochId
        val source = MessageDigest.getInstance("SHA-256")
        val total = 19_200_783L
        var first = 0L
        while (first < total) {
            val count = minOf(1234L, total - first).toInt()
            val pcm = ByteArray(count * 2) { ((first * 2 + it) % 251).toByte() }
            source.update(pcm)
            session.accept(pcm, epoch, first)
            first += count
        }
        session.requestStop()
        session.confirmStop()
        assertEquals(RecordingPhase.SAVED, session.state.phase)
        assertEquals(total, session.state.frames)
        assertEquals(total, session.state.durableFrames)
        assertEquals(epoch, session.captureEpochId)
        assertArrayEquals(source.digest(), writer.hash.digest())
        val groups = writer.units.groupBy { it.first.physicalSegmentId }.values.toList()
        assertEquals(
            listOf(9_600_000, 9_600_000, 783),
            groups.map { it.sumOf { unit -> unit.second } },
        )
        assertEquals(
            listOf(0L, 9_600_000L, 19_200_000L),
            groups.map { it.first().first.physicalFirstFrame },
        )
        var expected = 0L
        for ((unit, frames) in writer.units) {
            assertEquals(identity, unit.audio)
            assertEquals(expected, unit.firstFrame)
            assertEquals(unit.firstFrame - unit.physicalFirstFrame, unit.sourceFrameOffset)
            expected += frames
        }
        assertEquals(total, expected)
    }

    @Test
    fun pauseResumeAfterRotationUsesFreshEpochAndKeepsLogicalIdentity() {
        val writer = Writer()
        val session =
            RecordingSession(
                AudioIdentity(
                    RecordingId("00000000-0000-4000-8000-000000000001"),
                    AudioAssetId("00000000-0000-4000-8000-000000000002"),
                    "s",
                ),
                writer,
            )
        session.start()
        val epoch = session.captureEpochId
        repeat(121) { session.accept(ByteArray(160000), epoch, session.state.frames) }
        assertNotEquals(epoch, session.physicalId)
        session.pause()
        session.resume("resumed-epoch")
        session.accept(ByteArray(32), "resumed-epoch", session.state.frames)
        session.pause()
        assertEquals("resumed-epoch", writer.units.last().first.physicalSegmentId)
        assertEquals(0L, writer.units.last().first.sourceFrameOffset)
    }
}
