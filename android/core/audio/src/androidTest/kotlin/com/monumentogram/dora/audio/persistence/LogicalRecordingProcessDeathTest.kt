package com.monumentogram.dora.audio.persistence

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.logical.LogicalRecordingResult
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.success
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Four real host-kill phases; default instrumentation additionally exercises clean reopen. */
class LogicalRecordingProcessDeathTest {
    @Test
    fun verifyLogicalRecordingProcessDeath() {
        val args = InstrumentationRegistry.getArguments()
        val action = args.getString("persistenceCrashAction")
        if (action == null)
            PHASES.forEach { phase ->
                Fixture(phase, UUID.randomUUID().toString()).apply {
                    prepare(false)
                    verify()
                }
            }
        else {
            val phase = checkNotNull(args.getString("persistenceCrashPhase"))
            check(phase in PHASES)
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
        private val root = File(base.noBackupFilesDir, "logical-death-$suffix")
        private val context =
            object : ContextWrapper(base) {
                override fun getApplicationContext(): Context = this

                override fun getNoBackupFilesDir(): File = root
            }
        private val audio = AudioIdentity(RecordingId(id(1)), AudioAssetId(id(2)), id(3))

        fun prepare(host: Boolean) {
            check(root.mkdir())
            val dependencies =
                EncryptedAudioVault.Dependencies(
                    deletionStep = { step ->
                        if (phase == "DELETE_PENDING" && step == "TOMBSTONE") {
                            hold(host)
                            error("Synthetic pending deletion")
                        }
                    }
                )
            open(true, dependencies).use { vault ->
                success(vault.writer.create(audio))
                appendSyntheticChunks(vault)
                success(vault.writer.finalize(audio))
                val reference = reference(vault)
                if (phase == "PROJECTION_READ") assertReady(vault, reference)
                if (phase == "DELETE_PENDING")
                    assertTrue(vault.deleteConfirmed(audio) is AudioResult.Failed)
                else hold(host)
            }
        }

        private fun appendSyntheticChunks(vault: EncryptedAudioVault) {
            repeat(2) { ordinal ->
                val first = ordinal * 160L
                val chunk = id(10 + ordinal)
                val unit =
                    AudioStorageUnitIdentity(
                        audio,
                        id(20 + ordinal),
                        ordinal,
                        first,
                        chunk,
                        first,
                        0,
                    )
                val pcm = ByteArray(320) { (it * 17 + 3).toByte() }
                try {
                    success(vault.writer.append(unit, AudioFormat.PCM, pcm))
                } finally {
                    pcm.fill(0)
                }
                success(
                    vault.writer.segmentation(
                        audio,
                        SegmentationMetadata(
                            SegmentationKind.TECHNICAL_OPEN,
                            chunk,
                            first,
                            first,
                            chunk,
                            reason = if (ordinal == 0) "START" else "RESUME",
                        ),
                    )
                )
                if (phase != "METADATA_PARTIAL" || ordinal == 0)
                    success(
                        vault.writer.segmentation(
                            audio,
                            SegmentationMetadata(
                                SegmentationKind.TECHNICAL_CLOSE,
                                chunk,
                                first,
                                first + 160,
                                chunk,
                                reason = if (ordinal == 0) "PAUSE" else "STOP",
                            ),
                        )
                    )
            }
        }

        fun verify() {
            val expected = retainedReference()
            open(false).use { vault ->
                if (phase == "DELETE_PENDING") {
                    assertEquals(
                        AudioResult.Value(
                            LogicalRecordingResult.SourceUnavailable(
                                OriginalAudioStatus.DeletionPending
                            )
                        ),
                        vault.logicalRecordings.read(expected),
                    )
                    assertEquals(
                        AudioResult.Value(OriginalAudioStatus.DeletionPending),
                        vault.originals.withAvailable(expected) {
                            fail("Late callback resurrected source")
                        },
                    )
                    success(vault.retryDeletion(audio))
                    assertEquals(
                        AudioResult.Value(
                            LogicalRecordingResult.SourceUnavailable(
                                OriginalAudioStatus.SourceDeleted
                            )
                        ),
                        vault.logicalRecordings.read(expected),
                    )
                } else {
                    assertEquals(expected, reference(vault))
                    repeat(2) {
                        if (phase == "METADATA_PARTIAL")
                            assertTrue(
                                (vault.logicalRecordings.read(expected) as AudioResult.Value).value
                                    is LogicalRecordingResult.Incomplete
                            )
                        else assertReady(vault, expected)
                    }
                }
            }
        }

        private fun assertReady(vault: EncryptedAudioVault, expected: OriginalAudioReference) {
            val recording =
                ((vault.logicalRecordings.read(expected) as AudioResult.Value).value
                        as LogicalRecordingResult.Ready)
                    .recording
            assertEquals(expected, recording.originalAudioReference)
            assertEquals(listOf(id(10), id(11)), recording.technicalChunks.map { it.chunkId })
            assertEquals(listOf(0L, 160L), recording.technicalChunks.map { it.canonicalFirstFrame })
            assertEquals(listOf(160L, 320L), recording.technicalChunks.map { it.canonicalEndFrame })
            assertTrue(recording.technicalChunks.all { it.sourceAudioReference == expected })
            assertEquals(audio.recordingId, recording.authorizationUnitId.recordingId)
        }

        private fun reference(vault: EncryptedAudioVault) =
            ((vault.originals.acquire(audio) as AudioResult.Value).value
                    as OriginalAudioStatus.Available)
                .reference

        private fun open(
            create: Boolean,
            dependencies: EncryptedAudioVault.Dependencies = EncryptedAudioVault.Dependencies(),
        ) =
            (EncryptedAudioVault.open(context, create, {}, { it() }, dependencies)
                    as AudioResult.Value)
                .value

        private fun hold(host: Boolean) {
            if (host) {
                signal("READY", phase)
                CountDownLatch(1).await(120, TimeUnit.SECONDS)
                error("Host did not terminate synthetic process")
            }
        }

        private fun retainedReference(): OriginalAudioReference =
            LogicalSourceCheckpoint.read(context, audio)
    }

    companion object {
        private val PHASES =
            listOf("SOURCE_FINALIZED", "PROJECTION_READ", "METADATA_PARTIAL", "DELETE_PENDING")

        private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

        private fun signal(kind: String, phase: String) {
            InstrumentationRegistry.getInstrumentation()
                .sendStatus(
                    2,
                    Bundle().apply {
                        putString("stream", "DORA_PERSISTENCE_$kind:$phase:${Process.myPid()}")
                    },
                )
        }
    }
}
