package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioIntent
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.EncryptedAudioCatalog
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.StoredAudioAsset
import com.monumentogram.dora.audio.logical.LogicalRecordingResult
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.success
import com.monumentogram.dora.audio.persistence.keys.VaultKeystoreIo
import com.monumentogram.dora.audio.recording.RecordingCompletionState
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingSession
import com.monumentogram.dora.audio.recording.RecoveryMetadataState
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicalRecoveryRuntimeTest {
    @Test
    // Keep the ordered fault scenario and its assertions together for review.
    @Suppress("LongMethod")
    fun repeatedInterruptionResumesExactIdentityAndFinalSource() {
        val f = EncryptedAudioVaultFaultFixture()
        var expectedFrames = 0L
        repeat(3) { cycle ->
            f.open(cycle == 0).use { vault ->
                val session = RecordingSession(f.audio, vault.writer)
                if (cycle == 0) assertTrue(session.start())
                else {
                    val recovery = recovery(vault, f.audio)
                    assertTrue(recovery.canResume)
                    assertEquals(expectedFrames, recovery.recoveredFrames)
                    assertEquals(f.audio, recovery.identity)
                    session.restore(recovery.recoveredFrames, recovery.nextOrdinal)
                    assertEquals(RecordingPhase.PAUSED, session.state.phase)
                    assertTrue(session.resume())
                }
                session.accept(ByteArray(160000) { 7 })
                expectedFrames += 80000
                session.accept(ByteArray(3200)) // Deliberately unsealed volatile tail.
                session.interrupt()
                assertEquals(
                    AudioResult.Value(OriginalAudioStatus.NotFinalized),
                    vault.originals.acquire(f.audio),
                )
            }
        }
        f.open(false).use { vault ->
            val before = recovery(vault, f.audio)
            val rows = metadata(vault, f.audio)
            repeat(3) {
                assertEquals(before, recovery(vault, f.audio))
                assertEquals(rows, metadata(vault, f.audio))
            }
            val session = RecordingSession(f.audio, vault.writer)
            session.restore(before.recoveredFrames, before.nextOrdinal)
            assertTrue(session.resume())
            session.accept(ByteArray(320))
            session.requestStop()
            session.confirmStop()
            assertEquals(RecordingPhase.SAVED, session.state.phase)
            val ref = reference(vault, f.audio)
            assertEquals(240160L, ref.frames)
            val projection =
                ((vault.logicalRecordings.read(ref) as AudioResult.Value).value
                        as LogicalRecordingResult.Ready)
                    .recording
            assertEquals(4, projection.technicalChunks.size)
            assertEquals(
                listOf("RECOVERY", "RECOVERY", "RECOVERY", "STOP"),
                projection.technicalChunks.map { it.closeReason },
            )
            assertEquals(
                ref.frames,
                projection.technicalChunks.sumOf { it.canonicalEndFrame - it.canonicalFirstFrame },
            )
            assertEquals(f.audio.recordingId, projection.authorizationUnitId.recordingId)
            assertTrue(projection.technicalChunks.all { it.sourceAudioReference == ref })
            assertFalse(recovery(vault, f.audio).canResume)
            assertEquals(
                RecordingCompletionState.FINALIZED,
                recovery(vault, f.audio).completionState,
            )
        }
    }

    @Test
    fun recoveryLeaseFencesScanDeletionAndRecordingStart() {
        val f = EncryptedAudioVaultFaultFixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        var armed = false
        val deps =
            EncryptedAudioVault.Dependencies(
                runKeystore = { real ->
                    object : VaultKeystoreIo by real {
                        override fun open(alias: String): javax.crypto.SecretKey? {
                            if (armed) {
                                entered.countDown()
                                check(release.await(20, TimeUnit.SECONDS))
                            }
                            return real.open(alias)
                        }
                    }
                }
            )
        try {
            f.open(dependencies = deps).use { vault ->
                val session = RecordingSession(f.audio, vault.writer)
                session.start()
                session.accept(ByteArray(160000))
                session.interrupt()
                armed = true
                val scan = worker.submit<Any> { vault.recordingRecovery(f.audio) }
                try {
                    assertTrue(entered.await(20, TimeUnit.SECONDS))
                    assertEquals(
                        AudioResult.Failed(AudioFailure.BUSY),
                        vault.recordingRecovery(f.audio),
                    )
                    assertEquals(AudioResult.Failed(AudioFailure.BUSY), vault.deleteAudio(f.audio))
                    val newIdentity =
                        AudioIdentity(RecordingId(id(41)), AudioAssetId(id(42)), id(43))
                    assertTrue(vault.writer.create(newIdentity) is AudioResult.Failed)
                } finally {
                    release.countDown()
                }
                assertTrue(scan.get(20, TimeUnit.SECONDS) is AudioResult.Value<*>)
                assertTrue(recovery(vault, f.audio).canResume)
            }
        } finally {
            release.countDown()
            worker.shutdownNow()
        }
    }

    @Test
    fun finalizedMetadataGapCannotBeReportedComplete() {
        val f = EncryptedAudioVaultFaultFixture()
        val probe = DatabaseProbe()
        f.open(dependencies = EncryptedAudioVault.Dependencies(helperFactory = probe::factory))
            .use { vault ->
                val session = RecordingSession(f.audio, vault.writer)
                session.start()
                session.accept(ByteArray(160000))
                session.requestStop()
                session.confirmStop()
                val ref = reference(vault, f.audio)
                probe.database.execSQL(
                    "UPDATE audio_segmentation SET firstFrame=1 WHERE kind IN ('TECHNICAL_OPEN','TECHNICAL_CLOSE')"
                )
                val snapshot = recovery(vault, f.audio)
                assertEquals(RecordingCompletionState.FINALIZED, snapshot.completionState)
                assertFalse(snapshot.canResume)
                assertEquals(RecoveryMetadataState.MALFORMED, snapshot.technicalMetadataState)
                assertEquals(ref, reference(vault, f.audio))
            }
    }

    @Test
    fun zeroOpenIsRetainedAndAbortedWithoutZeroAudioChunk() {
        val f = EncryptedAudioVaultFaultFixture()
        f.open().use { vault ->
            success(vault.writer.create(f.audio))
            val open =
                SegmentationMetadata(
                    SegmentationKind.TECHNICAL_OPEN,
                    id(10),
                    0,
                    0,
                    id(10),
                    reason = "START",
                )
            success(vault.writer.segmentation(f.audio, open))
            assertFalse(recovery(vault, f.audio).canResume)
            val rows = metadata(vault, f.audio)
            assertTrue(open in rows)
            assertEquals(1, rows.count { it.kind == SegmentationKind.TECHNICAL_ABORT })
            assertTrue(rows.none { it.kind == SegmentationKind.TECHNICAL_CLOSE })
            recovery(vault, f.audio)
            assertEquals(rows, metadata(vault, f.audio))
        }
    }

    @Test
    fun badCandidateAndMalformedIdentityDoNotHideLaterPages() {
        val f = EncryptedAudioVaultFaultFixture()
        val probe = DatabaseProbe()
        f.open(dependencies = EncryptedAudioVault.Dependencies(helperFactory = probe::factory))
            .use { vault ->
                val identities =
                    (1..25).map {
                        AudioIdentity(RecordingId(id(it)), AudioAssetId(id(100 + it)), id(200 + it))
                    }
                identities.forEach { success(vault.writer.create(it)) }
                for (index in listOf(0, 2, 24)) {
                    val session = RecordingSession(identities[index], vault.writer)
                    session.restore(0, 0)
                    session.resume()
                    session.accept(ByteArray(160000))
                    session.interrupt()
                }
                probe.database.execSQL(
                    "UPDATE audio_asset SET sessionId='malformed' WHERE assetId=?",
                    arrayOf(identities[1].assetId.value),
                )
                probe.database.execSQL(
                    "UPDATE audio_asset SET recordingId='malformed' WHERE assetId=?",
                    arrayOf(identities[10].assetId.value),
                )
                val first = (vault.recordingRecoveryPage("") as AudioResult.Value).value
                assertEquals(20, first.size)
                assertTrue(first[0].canResume && first[2].canResume)
                assertFalse(first[1].canResume)
                assertNull(first[10].identity)
                val second =
                    (vault.recordingRecoveryPage(first.last().cursor) as AudioResult.Value).value
                assertEquals(5, second.size)
                assertTrue(second.last().canResume)
                assertEquals(25, (first + second).map { it.cursor }.distinct().size)
                assertEquals(
                    (first + second).map { it.cursor }.sorted(),
                    (first + second).map { it.cursor },
                )
            }
    }

    @Test
    fun malformedMetadataDoesNotDiscardCanonicalContinuation() {
        val f = EncryptedAudioVaultFaultFixture()
        val probe = DatabaseProbe()
        f.open(dependencies = EncryptedAudioVault.Dependencies(helperFactory = probe::factory))
            .use { vault ->
                val session = RecordingSession(f.audio, vault.writer)
                session.start()
                session.accept(ByteArray(160000))
                session.interrupt()
                probe.database.execSQL(
                    "UPDATE audio_segmentation SET reason='bad' WHERE assetId=?",
                    arrayOf(f.audio.assetId.value),
                )
                val snapshot = recovery(vault, f.audio)
                assertEquals(80000L, snapshot.recoveredFrames)
                assertTrue(snapshot.canResume)
                assertEquals(RecoveryMetadataState.MALFORMED, snapshot.technicalMetadataState)
            }
    }

    @Test
    fun deletionWinsAgainstStaleSnapshot() {
        val f = EncryptedAudioVaultFaultFixture()
        f.open().use { vault ->
            val session = RecordingSession(f.audio, vault.writer)
            session.start()
            session.accept(ByteArray(160000))
            session.interrupt()
            assertTrue(recovery(vault, f.audio).canResume)
            success(vault.deleteConfirmed(f.audio))
            val deleted = recovery(vault, f.audio)
            assertEquals(RecordingCompletionState.DELETED, deleted.completionState)
            assertFalse(deleted.canResume)
            assertEquals(0L, deleted.recoveredFrames)
            assertTrue((vault.recordingRecoveryPage("") as AudioResult.Value).value.isEmpty())
        }
    }

    @Test
    fun unpublishedReservationRetainsOldPrefixAndFencesResume() {
        val f = EncryptedAudioVaultFaultFixture()
        var armed = false
        val deps =
            EncryptedAudioVault.Dependencies(
                catalog = { real ->
                    object : EncryptedAudioCatalog by real {
                        override fun reserve(
                            expected: StoredAudioAsset,
                            intent: AudioIntent,
                        ): Boolean =
                            real.reserve(expected, intent).also {
                                if (it && armed) error("SYNTHETIC_RESERVATION_STOP")
                            }
                    }
                }
            )
        f.open(dependencies = deps).use { vault ->
            val session = RecordingSession(f.audio, vault.writer)
            session.start()
            session.accept(ByteArray(160000))
            armed = true
            session.accept(ByteArray(160000))
        }
        f.open(false).use { vault ->
            repeat(2) {
                val snapshot = recovery(vault, f.audio)
                assertEquals(80000L, snapshot.recoveredFrames)
                assertEquals(
                    RecordingCompletionState.PARTIAL_NOT_RESUMABLE,
                    snapshot.completionState,
                )
                assertFalse(snapshot.canResume)
            }
        }
    }

    @Test
    fun corruptMiddleNeverStitchesLaterUnit() {
        val f = EncryptedAudioVaultFaultFixture()
        val ids = mutableListOf<String>()
        f.open(
                dependencies =
                    EncryptedAudioVault.Dependencies(
                        catalog = { real ->
                            object : EncryptedAudioCatalog by real {
                                override fun reserve(
                                    expected: StoredAudioAsset,
                                    intent: AudioIntent,
                                ): Boolean {
                                    if (intent is AudioIntent.Append) ids += intent.identity.unitId
                                    return real.reserve(expected, intent)
                                }
                            }
                        }
                    )
            )
            .use { vault ->
                val session = RecordingSession(f.audio, vault.writer)
                session.start()
                repeat(3) { session.accept(ByteArray(160000)) }
                session.interrupt()
            }
        val middle =
            f.run(ids[1]).walkTopDown().first { it.isFile && it.parentFile?.name == "units" }
        val bytes = middle.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        middle.writeBytes(bytes)
        bytes.fill(0)
        f.open(false).use { vault ->
            val snapshot = recovery(vault, f.audio)
            assertFalse(snapshot.canResume)
            assertNull(snapshot.summary) // Accepted ADR-AUDIO-001 rejects corrupt extraction.
            assertTrue(
                snapshot.completionState in
                    setOf(
                        RecordingCompletionState.SOURCE_CORRUPT,
                        RecordingCompletionState.SOURCE_UNAVAILABLE,
                    )
            )
        }
    }

    companion object {
        fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

        internal fun recovery(vault: EncryptedAudioVault, identity: AudioIdentity) =
            (vault.recordingRecovery(identity) as AudioResult.Value).value

        internal fun metadata(vault: EncryptedAudioVault, identity: AudioIdentity) =
            (vault.reader.segmentation(identity) as AudioResult.Value).value

        internal fun reference(vault: EncryptedAudioVault, identity: AudioIdentity) =
            ((vault.originals.acquire(identity) as AudioResult.Value).value
                    as OriginalAudioStatus.Available)
                .reference
    }
}
