package com.monumentogram.dora.audio.persistence

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioIntent
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.EncryptedAudioCatalog
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.StoredAudioAsset
import com.monumentogram.dora.audio.recording.RecordingSession
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.vad.FrameRange
import com.monumentogram.dora.vad.SegmentationProfile
import com.monumentogram.dora.vad.SegmentationReducer
import com.monumentogram.dora.vad.SemanticEvent
import com.monumentogram.dora.vad.VadObservation
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic canonical PCM + real encryption/SQLCipher. Host mode proves actual process death. */
class SegmentationProcessDeathTest {
    private enum class Phase(val frames: Long, val pending: Boolean = false) {
        OPEN_SPEECH(80_000),
        BEFORE_SEMANTIC(1_519_999),
        AFTER_SEMANTIC(1_520_000),
        BEFORE_ROTATION(9_599_999),
        AFTER_ROTATION(9_600_000),
        OVERLAP_PENDING(9_680_000, true),
        DURABILITY_PENDING(1_600_000, true),
    }

    @Test
    fun verifySegmentationProcessDeath() {
        val args = InstrumentationRegistry.getArguments()
        val action = args.getString("persistenceCrashAction")
        if (action == null) {
            Phase.entries.forEach { phase ->
                Fixture(phase, UUID.randomUUID().toString()).apply {
                    prepare(false)
                    verify()
                }
            }
            return
        }
        val phase = Phase.valueOf(checkNotNull(args.getString("persistenceCrashPhase")))
        val f = Fixture(phase, "host-${phase.name}")
        when (action) {
            "PREPARE" -> f.prepare(true)
            "VERIFY" -> {
                f.verify()
                signal("VERIFIED", phase)
            }
            else -> error("Unsupported synthetic phase")
        }
    }

    private class Fixture(private val phase: Phase, suffix: String) {
        private val base = ApplicationProvider.getApplicationContext<Context>()
        private val root = File(base.noBackupFilesDir, "segmentation-death-$suffix")
        private val context =
            object : ContextWrapper(base) {
                override fun getApplicationContext(): Context = this

                override fun getNoBackupFilesDir(): File = root
            }
        private val audio = AudioIdentity(RecordingId(id(1)), AudioAssetId(id(2)), id(3))
        private var pendingReached = false
        private val committedFrames: Long
            get() = if (phase.pending) phase.frames - 80_000 else phase.frames / 80_000 * 80_000

        @Suppress(
            "ComplexCondition"
        ) // Exact append-intent trigger, never a timer-based crash approximation.
        fun prepare(host: Boolean) {
            assertTrue("Fresh synthetic namespace required", root.mkdir())
            val dependencies =
                EncryptedAudioVault.Dependencies(
                    catalog = { real ->
                        object : EncryptedAudioCatalog by real {
                            override fun reserve(
                                expected: StoredAudioAsset,
                                intent: AudioIntent,
                            ): Boolean =
                                real.reserve(expected, intent).also {
                                    if (
                                        it &&
                                            phase.pending &&
                                            intent is AudioIntent.Append &&
                                            intent.identity.firstFrame == committedFrames
                                    ) {
                                        pendingReached = true
                                        hold(host)
                                        throw SyntheticInterruption()
                                    }
                                }
                        }
                    }
                )
            open(true, dependencies).use { vault ->
                val session = RecordingSession(audio, vault.writer)
                assertTrue(session.start())
                val reducer =
                    SegmentationReducer(SegmentationProfile.FROZEN, 0, 1) { event ->
                        if (event is SemanticEvent.Closed)
                            session.retainMetadata(
                                SegmentationMetadata(
                                    SegmentationKind.SEMANTIC_CLOSE,
                                    id(10 + event.ordinal.toInt()),
                                    event.range.first,
                                    event.range.end,
                                    reason = event.reason.name,
                                    degraded = event.degraded,
                                )
                            )
                    }
                var first = 0L
                while (first < phase.frames) {
                    val count = minOf(80_000L, phase.frames - first).toInt()
                    val pcm = bytes(first, count)
                    session.accept(pcm)
                    pcm.fill(0)
                    if (!pendingReached) {
                        val speech =
                            phase in
                                setOf(
                                    Phase.BEFORE_ROTATION,
                                    Phase.AFTER_ROTATION,
                                    Phase.OVERLAP_PENDING,
                                ) || first == 0L
                        reducer.observe(VadObservation(FrameRange(first, first + count), 1, speech))
                    }
                    first += count
                }
                assertEquals(phase.pending, pendingReached)
                if (!phase.pending) hold(host)
                // No Stop/finalization: process recovery must not manufacture these events.
            }
        }

        private fun hold(host: Boolean) {
            if (host) {
                signal("READY", phase)
                CountDownLatch(1).await(120, TimeUnit.SECONDS)
                error("Host did not terminate the prepared process")
            }
        }

        fun verify() {
            assertTrue(root.isDirectory)
            open(false).use { vault ->
                val before = (vault.reader.segmentation(audio) as AudioResult.Value).value
                repeat(2) {
                    val reconciled = vault.writer.reconcile(audio)
                    if (phase.pending) assertTrue(reconciled is AudioResult.Failed)
                    else assertTrue(reconciled is AudioResult.Value)
                    assertEquals(
                        before,
                        (vault.reader.segmentation(audio) as AudioResult.Value).value,
                    )
                }
                var next = 0L
                val expectedHash = MessageDigest.getInstance("SHA-256")
                val actualHash = MessageDigest.getInstance("SHA-256")
                val read =
                    vault.reader.extract(audio) { first, pcm ->
                        assertEquals(next, first)
                        next += pcm.size / 2
                        actualHash.update(pcm)
                        val expected = bytes(first, pcm.size / 2)
                        expectedHash.update(expected)
                        expected.fill(0)
                    }
                assertTrue(read is AudioResult.Value)
                assertEquals(committedFrames, next)
                assertArrayEquals(expectedHash.digest(), actualHash.digest())
                assertEquals(
                    AudioCompletion.PARTIAL_RECOVERED,
                    (read as AudioResult.Value).value.completion,
                )
                val semantic = before.filter { it.kind == SegmentationKind.SEMANTIC_CLOSE }
                assertEquals(
                    if (phase in setOf(Phase.AFTER_SEMANTIC, Phase.DURABILITY_PENDING)) 1 else 0,
                    semantic.size,
                )
                semantic.forEach {
                    assertEquals(1_520_000L, it.endFrame)
                    assertEquals("SILENCE_90_SECONDS", it.reason)
                }
                val caps = before.filter { it.kind == SegmentationKind.TECHNICAL_CLOSE }
                assertEquals(
                    if (phase in setOf(Phase.AFTER_ROTATION, Phase.OVERLAP_PENDING)) 1 else 0,
                    caps.size,
                )
                caps.forEach {
                    assertEquals(9_600_000L, it.endFrame)
                    assertEquals("CAP", it.reason)
                }
                assertTrue(before.none { it.reason == "STOP" })
                assertTrue(before.all { it.endFrame <= committedFrames })
            }
        }

        private fun open(
            create: Boolean,
            dependencies: EncryptedAudioVault.Dependencies = EncryptedAudioVault.Dependencies(),
        ): EncryptedAudioVault =
            (EncryptedAudioVault.open(context, create, {}, { it() }, dependencies)
                    as AudioResult.Value)
                .value
    }

    private class SyntheticInterruption : RuntimeException()

    companion object {
        private fun id(value: Int) = "00000000-0000-4000-8000-${value.toString().padStart(12, '0')}"

        private fun bytes(first: Long, count: Int) =
            ByteArray(count * 2) { ((first * 2 + it) * 37 + 19).toByte() }

        private fun signal(kind: String, phase: Phase) {
            InstrumentationRegistry.getInstrumentation()
                .sendStatus(
                    2,
                    Bundle().apply {
                        putString(
                            "stream",
                            "DORA_PERSISTENCE_$kind:${phase.name}:${Process.myPid()}",
                        )
                    },
                )
        }
    }
}
