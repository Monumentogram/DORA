package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.logical.LogicalRecordingResult
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.success
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LogicalRecordingRuntimeTest {
    @Test
    fun corruptPersistedProfileReturnsIncompleteWithoutDisablingOriginalAudio() {
        val f = EncryptedAudioVaultFaultFixture()
        val ref =
            f.open().use { vault ->
                record(f, vault)
                reference(f, vault)
            }
        LogicalSourceCheckpoint.read(f.context, f.audio, corruptMetadata = true)
        f.open(false).use { vault ->
            assertEquals(
                AudioResult.Value(
                    LogicalRecordingResult.Incomplete(
                        com.monumentogram.dora.audio.logical.ProjectionFailure.MALFORMED_METADATA
                    )
                ),
                vault.logicalRecordings.read(ref),
            )
            assertEquals(
                AudioResult.Value(OriginalAudioStatus.Available(ref)),
                vault.originals.inspect(ref),
            )
        }
    }

    @Test
    fun finalizedProjectionSurvivesVaultReopenExactly() {
        val f = EncryptedAudioVaultFaultFixture()
        val before =
            f.open().use { vault ->
                record(f, vault)
                projection(vault, reference(f, vault))
            }
        repeat(2) {
            f.open(false).use { vault ->
                assertEquals(before.originalAudioReference, reference(f, vault))
                assertEquals(before, projection(vault, before.originalAudioReference))
            }
        }
        f.scan()
    }

    @Test
    fun sourceFinalizedBeforeFirstProjectionStillBindsPersistedIds() {
        val f = EncryptedAudioVaultFaultFixture()
        val ref =
            f.open().use { vault ->
                record(f, vault)
                reference(f, vault)
            }
        f.open(false).use { vault ->
            val first = projection(vault, ref)
            assertEquals(first, projection(vault, ref))
            assertEquals(2, first.technicalChunks.size)
            assertEquals(320L, first.originalAudioReference.frames)
            assertEquals(1, first.technicalChunks.map { it.sourceAudioReference }.distinct().size)
        }
    }

    @Test
    fun historicalFinalizedAudioIsNotEvaluatedAndRemainsReadable() {
        val f = EncryptedAudioVaultFaultFixture()
        f.open().use { vault ->
            success(vault.writer.create(f.audio))
            success(f.append(vault))
            success(vault.writer.finalize(f.audio))
            val ref = reference(f, vault)
            assertEquals(
                AudioResult.Value(LogicalRecordingResult.NotEvaluated(ref)),
                vault.logicalRecordings.read(ref),
            )
            assertEquals(
                AudioResult.Value(OriginalAudioStatus.Available(ref)),
                vault.originals.extract(ref) { _, _ -> },
            )
        }
    }

    @Test
    fun everyStaleReferenceFieldFailsClosed() {
        val f = EncryptedAudioVaultFaultFixture()
        f.open().use { vault ->
            record(f, vault)
            val ref = reference(f, vault)
            listOf(
                    ref.copy(version = 2),
                    ref.copy(digest = "0".repeat(64)),
                    ref.copy(frames = 1),
                    ref.copy(
                        identity =
                            ref.identity.copy(sessionId = EncryptedAudioVaultFaultFixture.id())
                    ),
                )
                .forEach {
                    val result = vault.logicalRecordings.read(it)
                    assertTrue(
                        result is AudioResult.Failed ||
                            result ==
                                AudioResult.Value(
                                    LogicalRecordingResult.SourceUnavailable(
                                        OriginalAudioStatus.StaleReference
                                    )
                                )
                    )
                }
            assertEquals(ref, reference(f, vault))
        }
    }

    @Test
    fun sourceDeletionBlocksRetainedChunkAndLateCallbackAfterReopen() {
        val f = EncryptedAudioVaultFaultFixture()
        val retained =
            f.open().use { vault ->
                record(f, vault)
                val result = projection(vault, reference(f, vault))
                success(vault.deleteConfirmed(f.audio))
                assertEquals(
                    AudioResult.Value(
                        LogicalRecordingResult.SourceUnavailable(OriginalAudioStatus.SourceDeleted)
                    ),
                    vault.logicalRecordings.read(result.originalAudioReference),
                )
                result
            }
        f.open(false).use { vault ->
            val chunk = retained.technicalChunks.first()
            assertEquals(
                AudioResult.Value(OriginalAudioStatus.SourceDeleted),
                vault.originals.withAvailable(chunk.sourceAudioReference) {
                    fail("Deleted audio cannot authorize late work")
                },
            )
            assertEquals(
                AudioResult.Value(OriginalAudioStatus.SourceDeleted),
                vault.originals.extract(chunk.sourceAudioReference) { _, _ ->
                    fail("Deleted audio delivered")
                },
            )
            assertEquals(
                AudioResult.Value(
                    LogicalRecordingResult.SourceUnavailable(OriginalAudioStatus.SourceDeleted)
                ),
                vault.logicalRecordings.read(chunk.sourceAudioReference),
            )
        }
    }

    @Test
    fun incompleteMetadataNeverPublishesPartialProjection() {
        val f = EncryptedAudioVaultFaultFixture()
        f.open().use { vault ->
            val session = RecordingSession(f.audio, vault.writer)
            assertTrue(session.start())
            session.accept(ByteArray(160000))
            success(vault.writer.finalize(f.audio))
            val result =
                (vault.logicalRecordings.read(reference(f, vault)) as AudioResult.Value).value
            assertTrue(result is LogicalRecordingResult.Incomplete)
        }
    }

    @Test
    fun partialSourceCannotInventFinalizedReference() {
        val f = EncryptedAudioVaultFaultFixture()
        f.open().use { vault ->
            val session = RecordingSession(f.audio, vault.writer)
            session.start()
            session.accept(ByteArray(160000))
            session.interrupt()
            assertEquals(
                AudioResult.Value(OriginalAudioStatus.NotFinalized),
                vault.originals.acquire(f.audio),
            )
            val fake = OriginalAudioReference(1, f.audio, "a".repeat(64), 80000)
            assertEquals(
                AudioResult.Value(
                    LogicalRecordingResult.SourceUnavailable(OriginalAudioStatus.NotFinalized)
                ),
                vault.logicalRecordings.read(fake),
            )
        }
    }

    companion object {
        internal fun record(f: EncryptedAudioVaultFaultFixture, vault: EncryptedAudioVault) {
            val session = RecordingSession(f.audio, vault.writer)
            assertTrue(session.start())
            session.accept(f.pcm)
            session.pause()
            assertTrue(session.resume())
            session.accept(f.pcm)
            session.requestStop()
            session.confirmStop()
            assertEquals(RecordingPhase.SAVED, session.state.phase)
        }

        internal fun reference(f: EncryptedAudioVaultFaultFixture, vault: EncryptedAudioVault) =
            ((vault.originals.acquire(f.audio) as AudioResult.Value).value
                    as OriginalAudioStatus.Available)
                .reference

        internal fun projection(vault: EncryptedAudioVault, ref: OriginalAudioReference) =
            ((vault.logicalRecordings.read(ref) as AudioResult.Value).value
                    as LogicalRecordingResult.Ready)
                .recording
    }
}
