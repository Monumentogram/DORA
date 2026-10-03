package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.id
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.success
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingSession
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real SQLCipher and canonical encryption; no sherpa/native VAD claim. */
class SegmentationPersistenceTest {
    @Test
    fun historicalRecordingHasNoInventedSegmentation() {
        val f = EncryptedAudioVaultFaultFixture()
        f.open().use { v ->
            success(v.writer.create(f.audio))
            success(f.append(v))
            success(v.writer.finalize(f.audio))
            assertEquals(
                AudioResult.Value(emptyList<SegmentationMetadata>()),
                v.reader.segmentation(f.audio),
            )
        }
        f.scan()
    }

    @Test
    fun metadataIsImmutableIdempotentAndCannotReferToFutureFrames() {
        val f = EncryptedAudioVaultFaultFixture()
        val m = SegmentationMetadata(SegmentationKind.SEMANTIC_CLOSE, id(), 0, 160, reason = "STOP")
        f.open().use { v ->
            success(v.writer.create(f.audio))
            success(f.append(v))
            success(v.writer.segmentation(f.audio, m))
            success(v.writer.segmentation(f.audio, m))
            assertTrue(v.writer.segmentation(f.audio, m.copy(endFrame = 159)) is AudioResult.Failed)
            assertTrue(
                v.writer.segmentation(f.audio, m.copy(segmentId = id(), endFrame = 161))
                    is AudioResult.Failed
            )
            success(v.writer.finalize(f.audio))
        }
        f.open(false).use { v ->
            assertEquals(AudioResult.Value(listOf(m)), v.reader.segmentation(f.audio))
            f.exact(v, finalized = true)
        }
        f.scan()
    }

    @Test
    fun encryptedRotationPreservesCanonicalHashAndExactOverlapAcrossReopen() {
        val f = EncryptedAudioVaultFaultFixture()
        val hash = MessageDigest.getInstance("SHA-256")
        f.open().use { v ->
            val s = RecordingSession(f.audio, v.writer)
            assertTrue(s.start())
            val pcm = ByteArray(160000) { (it * 17 + 3).toByte() }
            repeat(121) {
                hash.update(pcm)
                s.accept(pcm)
            }
            pcm.fill(0)
            s.requestStop()
            s.confirmStop()
            assertEquals(RecordingPhase.SAVED, s.state.phase)
            assertNull(s.state.segmentationFailure)
        }
        f.open(false).use { v ->
            val actual = MessageDigest.getInstance("SHA-256")
            var next = 0L
            success(
                v.reader.extract(f.audio) { first, pcm ->
                    assertEquals(next, first)
                    next += pcm.size / 2
                    actual.update(pcm)
                }
            )
            assertEquals(9_680_000L, next)
            assertArrayEquals(hash.digest(), actual.digest())
            val rows = (v.reader.segmentation(f.audio) as AudioResult.Value).value
            val opens =
                rows
                    .filter { it.kind == SegmentationKind.TECHNICAL_OPEN }
                    .sortedBy { it.firstFrame }
            val closes =
                rows
                    .filter { it.kind == SegmentationKind.TECHNICAL_CLOSE }
                    .sortedBy { it.firstFrame }
            assertEquals(2, opens.size)
            assertEquals(2, closes.size)
            assertEquals(9_568_000L, opens.last().overlapFirstFrame)
            assertEquals(9_600_000L, closes.first().endFrame)
        }
    }

    @Test
    fun interruptedOpenSourceDoesNotInventSemanticCloseAfterRecovery() {
        val f = EncryptedAudioVaultFaultFixture()
        f.open().use { v ->
            val s = RecordingSession(f.audio, v.writer)
            s.start()
            s.accept(ByteArray(160000))
            s.interrupt()
        }
        f.open(false).use { v ->
            success(v.writer.reconcile(f.audio))
            val rows = (v.reader.segmentation(f.audio) as AudioResult.Value).value
            assertEquals(1, rows.size)
            assertEquals(SegmentationKind.TECHNICAL_OPEN, rows.single().kind)
            success(v.reader.extract(f.audio) { _, _ -> })
        }
    }
}
