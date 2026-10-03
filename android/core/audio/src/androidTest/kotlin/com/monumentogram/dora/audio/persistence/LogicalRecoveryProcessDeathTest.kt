package com.monumentogram.dora.audio.persistence

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioIntent
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.EncryptedAudioCatalog
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.StoredAudioAsset
import com.monumentogram.dora.audio.logical.LogicalRecordingResult
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.success
import com.monumentogram.dora.audio.persistence.LogicalRecoveryRuntimeTest.Companion.id
import com.monumentogram.dora.audio.persistence.LogicalRecoveryRuntimeTest.Companion.metadata
import com.monumentogram.dora.audio.persistence.LogicalRecoveryRuntimeTest.Companion.recovery
import com.monumentogram.dora.audio.persistence.LogicalRecoveryRuntimeTest.Companion.reference
import com.monumentogram.dora.audio.recording.RecordingCompletionState
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingSession
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileJournal
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileTransaction
import com.monumentogram.dora.vad.FrameRange
import com.monumentogram.dora.vad.SegmentationProfile
import com.monumentogram.dora.vad.SegmentationReducer
import com.monumentogram.dora.vad.SemanticEvent
import com.monumentogram.dora.vad.VadObservation
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Additional Stage 8.5 host-kill phases. Only synthetic PCM; no microphone or owner vault. */
class LogicalRecoveryProcessDeathTest {
    @Test
    fun verifyLogicalRecoveryProcessDeath() {
        val args = InstrumentationRegistry.getArguments()
        val action = args.getString("persistenceCrashAction")
        if (action == null)
            // The separate host campaign executes all 21 phases with proven process absence.
            listOf("COMMITTED_OPEN").forEach { phase ->
                Fixture(phase, UUID.randomUUID().toString()).apply {
                    prepare(false)
                    verify()
                }
            }
        else {
            val phase = checkNotNull(args.getString("persistenceCrashPhase"))
            require(phase in PHASES)
            val fixture = Fixture(phase, "host-$phase")
            when (action) {
                "PREPARE" -> fixture.prepare(true)
                "VERIFY" -> {
                    fixture.verify()
                    signal("VERIFIED", phase)
                }
                else -> error("Unsupported action")
            }
        }
    }

    private class Fixture(private val phase: String, suffix: String) {
        private val base = ApplicationProvider.getApplicationContext<Context>()
        private val root = File(base.noBackupFilesDir, "logical-recovery-$suffix")
        private val context =
            object : ContextWrapper(base) {
                override fun getApplicationContext(): Context = this

                override fun getNoBackupFilesDir(): File = root
            }
        private val audio = AudioIdentity(RecordingId(id(1)), AudioAssetId(id(2)), id(3))
        private var armed = false
        private var reached = false
        private var host = false

        private fun checkpoint(selected: String) {
            if (!armed || reached || selected != phase) return
            reached = true
            if (host) {
                signal("READY", phase)
                CountDownLatch(1).await(120, TimeUnit.SECONDS)
                error("Host did not terminate prepared process")
            }
            throw SyntheticInterruption()
        }

        private fun dependencies() =
            EncryptedAudioVault.Dependencies(
                catalog = { real ->
                    object : EncryptedAudioCatalog by real {
                        override fun reserve(
                            expected: StoredAudioAsset,
                            intent: AudioIntent,
                        ): Boolean =
                            real.reserve(expected, intent).also {
                                if (it)
                                    checkpoint(
                                        if (intent is AudioIntent.Append) "APPEND_RESERVED"
                                        else "FINALIZE_RESERVED"
                                    )
                            }

                        override fun compareAndSet(
                            expected: StoredAudioAsset,
                            next: StoredAudioAsset,
                        ): Boolean {
                            val append = expected.pending is AudioIntent.Append
                            checkpoint(
                                if (append) "AUTHENTICATED_READBACK" else "FINALIZE_VERIFIED"
                            )
                            return real.compareAndSet(expected, next).also {
                                if (it)
                                    checkpoint(
                                        if (append) "CATALOG_COMMITTED" else "FINALIZE_COMMITTED"
                                    )
                            }
                        }
                    }
                },
                microfileJournal = { real ->
                    object : RecoveryMicrofileJournal by real {
                        override fun beginNonExclusive(): RecoveryMicrofileTransaction {
                            checkpoint("KEY_BOOTSTRAP")
                            val transaction = real.beginNonExclusive()
                            return object : RecoveryMicrofileTransaction by transaction {
                                override fun end() {
                                    transaction.end()
                                    checkpoint("PUBLICATION_COMMITTED")
                                }
                            }
                        }
                    }
                },
            )

        // Keep the ordered fault scenario and its assertions together for review.
        @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
        fun prepare(host: Boolean) {
            this.host = host
            check(root.mkdir())
            open(true, dependencies()).use { vault ->
                val writer =
                    object : ProductAudioWriterPort by vault.writer {
                        override fun segmentation(
                            identity: AudioIdentity,
                            row: SegmentationMetadata,
                        ): AudioResult<Unit> {
                            if (row.kind == SegmentationKind.SEMANTIC_CLOSE)
                                checkpoint("SEMANTIC_BEFORE")
                            val result = vault.writer.segmentation(identity, row)
                            if (result is AudioResult.Value)
                                when (row.kind) {
                                    SegmentationKind.TECHNICAL_OPEN ->
                                        checkpoint(
                                            if (row.reason == "CAP") "CAP_OPEN_EMPTY"
                                            else "OPEN_BEFORE_PCM"
                                        )
                                    SegmentationKind.TECHNICAL_CLOSE ->
                                        checkpoint("TECHNICAL_CLOSE")
                                    SegmentationKind.SEMANTIC_CLOSE -> checkpoint("SEMANTIC_AFTER")
                                    else -> Unit
                                }
                            return result
                        }

                        override fun finalize(identity: AudioIdentity): AudioResult<Unit> {
                            checkpoint("FINAL_TAIL_SEALED")
                            return vault.writer.finalize(identity)
                        }
                    }
                var session = RecordingSession(audio, writer)
                assertTrue(session.start())
                try {
                    if (phase == "OPEN_BEFORE_PCM") armed = true
                    session.accept(ByteArray(160000) { 7 })
                    if (reached) return@use
                    armed = true
                    when {
                        phase in
                            setOf(
                                "APPEND_RESERVED",
                                "KEY_BOOTSTRAP",
                                "PUBLICATION_COMMITTED",
                                "AUTHENTICATED_READBACK",
                                "CATALOG_COMMITTED",
                            ) -> session.accept(ByteArray(160000) { 7 })
                        phase.startsWith("FINAL") || phase == "TECHNICAL_CLOSE" -> {
                            session.accept(ByteArray(320) { 7 })
                            session.requestStop()
                            session.confirmStop()
                        }
                        phase in
                            setOf("BEFORE_CAP", "CAP_CLOSED", "CAP_OPEN_EMPTY", "CAP_OVERLAP") -> {
                            armed = false
                            repeat(118) { session.accept(ByteArray(160000) { 7 }) }
                            if (phase == "BEFORE_CAP") session.accept(ByteArray(159998) { 7 })
                            else {
                                session.accept(ByteArray(160000) { 7 })
                                armed = true
                                if (phase != "CAP_CLOSED") session.accept(ByteArray(160000) { 7 })
                            }
                            armed = true
                            checkpoint(phase)
                        }
                        phase == "PAUSE_PENDING" -> {
                            val pending = ArrayDeque<Runnable>()
                            session.interrupt()
                            val snapshot = recovery(vault, audio)
                            session =
                                RecordingSession(
                                    audio,
                                    writer,
                                    persistence = java.util.concurrent.Executor { pending.add(it) },
                                )
                            session.restore(snapshot.recoveredFrames, snapshot.nextOrdinal)
                            session.resume()
                            session.accept(ByteArray(3200))
                            session.pause()
                            assertEquals(RecordingPhase.PAUSED, session.state.phase)
                            checkpoint(phase)
                        }
                        phase == "SECOND_INTERRUPTION" -> {
                            session.interrupt()
                            val snapshot = recovery(vault, audio)
                            session = RecordingSession(audio, writer)
                            session.restore(snapshot.recoveredFrames, snapshot.nextOrdinal)
                            session.resume()
                            session.accept(ByteArray(160000) { 7 })
                            checkpoint(phase)
                        }
                        phase == "VAD_DEGRADED" -> {
                            success(
                                writer.segmentation(
                                    audio,
                                    SegmentationMetadata(
                                        SegmentationKind.DEGRADED,
                                        id(99),
                                        0,
                                        80000,
                                        reason = "INFERENCE_FAILED",
                                        degraded = true,
                                    ),
                                )
                            )
                            checkpoint(phase)
                        }
                        phase.startsWith("SEMANTIC") -> {
                            writer.segmentation(
                                audio,
                                SegmentationMetadata(
                                    SegmentationKind.SEMANTIC_CLOSE,
                                    id(98),
                                    0,
                                    80000,
                                    reason = "STOP",
                                ),
                            )
                        }
                        else -> checkpoint(phase)
                    }
                } catch (_: SyntheticInterruption) {
                    /* deterministic no-host inventory */
                }
                assertTrue("Required phase not reached: $phase", reached)
            }
        }

        // Keep the ordered fault scenario and its assertions together for review.
        @Suppress("LongMethod")
        fun verify() {
            open(false).use { vault ->
                val first = recovery(vault, audio)
                val frames =
                    when (phase) {
                        "OPEN_BEFORE_PCM" -> 0L
                        "BEFORE_CAP" -> 9520000L
                        "CAP_CLOSED",
                        "CAP_OPEN_EMPTY" -> 9600000L
                        "CAP_OVERLAP" -> 9680000L
                        "PUBLICATION_COMMITTED",
                        "AUTHENTICATED_READBACK",
                        "CATALOG_COMMITTED",
                        "SECOND_INTERRUPTION" -> 160000L
                        "TECHNICAL_CLOSE",
                        "FINAL_TAIL_SEALED",
                        "FINALIZE_RESERVED",
                        "FINALIZE_VERIFIED",
                        "FINALIZE_COMMITTED" -> 80160L
                        else -> 80000L
                    }
                assertEquals("Authenticated durable frame count", frames, first.recoveredFrames)
                assertEquals(audio, first.identity)
                val retained = metadata(vault, audio)
                repeat(2) {
                    assertEquals(first, recovery(vault, audio))
                    assertEquals(retained, metadata(vault, audio))
                }
                if (phase == "OPEN_BEFORE_PCM") {
                    assertFalse(first.canResume)
                    assertTrue(retained.any { it.kind == SegmentationKind.TECHNICAL_ABORT })
                    return@use
                }
                if (phase in setOf("APPEND_RESERVED", "KEY_BOOTSTRAP")) {
                    assertFalse(first.canResume)
                    assertEquals(
                        RecordingCompletionState.PARTIAL_NOT_RESUMABLE,
                        first.completionState,
                    )
                    return@use
                }
                if (phase.startsWith("FINALIZE_")) {
                    assertEquals(RecordingCompletionState.FINALIZED, first.completionState)
                    assertFalse(first.canResume)
                } else {
                    assertTrue(first.canResume)
                    assertEquals(
                        AudioResult.Value(OriginalAudioStatus.NotFinalized),
                        vault.originals.acquire(audio),
                    )
                    val session = RecordingSession(audio, vault.writer)
                    session.restore(frames, first.nextOrdinal)
                    assertEquals(RecordingPhase.PAUSED, session.state.phase)
                    assertTrue(session.resume())
                    session.accept(ByteArray(320) { 7 })
                    session.requestStop()
                    session.confirmStop()
                    assertEquals(RecordingPhase.SAVED, session.state.phase)
                }
                val reference = reference(vault, audio)
                assertEquals(
                    frames + if (phase.startsWith("FINALIZE_")) 0 else 160,
                    reference.frames,
                )
                val projection =
                    ((vault.logicalRecordings.read(reference) as AudioResult.Value).value
                            as LogicalRecordingResult.Ready)
                        .recording
                assertEquals(audio.recordingId, projection.authorizationUnitId.recordingId)
                assertEquals(
                    reference.frames,
                    projection.technicalChunks.sumOf {
                        it.canonicalEndFrame - it.canonicalFirstFrame
                    },
                )
                assertTrue(projection.technicalChunks.all { it.sourceAudioReference == reference })
                var next = 0L
                success(
                    vault.originals.extract(reference) { start, pcm ->
                        assertEquals(next, start)
                        next += pcm.size / 2
                        assertTrue(pcm.all { it == 7.toByte() })
                    }
                )
                assertEquals(reference.frames, next)
                if (phase == "VAD_DEGRADED")
                    assertTrue(
                        projection.degradedObservations.any { it.reason == "INFERENCE_FAILED" }
                    )
                val events = mutableListOf<SemanticEvent>()
                val fresh =
                    SegmentationReducer(
                        SegmentationProfile.FROZEN,
                        reference.frames,
                        1,
                        events::add,
                    )
                fresh.observe(
                    VadObservation(FrameRange(reference.frames, reference.frames + 16000), 1, false)
                )
                assertTrue(events.isEmpty()) // No process-local speech/silence resurrection.
            }
        }

        private fun open(
            create: Boolean,
            dependencies: EncryptedAudioVault.Dependencies = EncryptedAudioVault.Dependencies(),
        ) =
            (EncryptedAudioVault.open(context, create, {}, { it() }, dependencies)
                    as AudioResult.Value)
                .value
    }

    private class SyntheticInterruption : Error()

    companion object {
        val PHASES =
            listOf(
                "OPEN_BEFORE_PCM",
                "COMMITTED_OPEN",
                "APPEND_RESERVED",
                "KEY_BOOTSTRAP",
                "PUBLICATION_COMMITTED",
                "AUTHENTICATED_READBACK",
                "CATALOG_COMMITTED",
                "TECHNICAL_CLOSE",
                "SEMANTIC_BEFORE",
                "SEMANTIC_AFTER",
                "BEFORE_CAP",
                "CAP_CLOSED",
                "CAP_OPEN_EMPTY",
                "CAP_OVERLAP",
                "PAUSE_PENDING",
                "SECOND_INTERRUPTION",
                "VAD_DEGRADED",
                "FINAL_TAIL_SEALED",
                "FINALIZE_RESERVED",
                "FINALIZE_VERIFIED",
                "FINALIZE_COMMITTED",
            )

        private fun signal(kind: String, phase: String) =
            InstrumentationRegistry.getInstrumentation()
                .sendStatus(
                    2,
                    Bundle().apply {
                        putString("stream", "DORA_PERSISTENCE_$kind:$phase:${Process.myPid()}")
                    },
                )
    }
}
