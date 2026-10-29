@file:Suppress("MagicNumber", "LongMethod")

package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingDeletionPersistenceTest {
    @Test
    fun wholeRecordingDeletionPreservesOtherRecordAndSurvivesReopen() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val other = identity()
        fixture.open().use { vault ->
            seed(vault, fixture.audio)
            seed(vault, other)
            fixture.context.noBackupFilesDir
                .walkTopDown()
                .filter { it.isFile }
                .forEach {
                    val content = it.readBytes().toString(Charsets.UTF_8)
                    assertFalse(content.contains("Synthetic transcript"))
                    assertFalse(content.contains("Исправления"))
                }
            val survivor = value(vault.transcripts.load(other))
            value(vault.deleteRecordingConfirmed(fixture.audio))
            assertNull(value(vault.transcripts.load(fixture.audio)))
            assertEquals(AudioSourceState.UserDeleted, value(vault.sourceState(fixture.audio)))
            assertTrue(value(vault.inspectAudioDeletion(fixture.audio)).complete)
            assertEquals(survivor, value(vault.transcripts.load(other)))
            assertTrue(value(vault.originals.acquire(other)) is OriginalAudioStatus.Available)
            assertEquals(listOf(other), value(vault.transcripts.history()))
        }
        fixture.open(create = false).use { vault ->
            assertNull(value(vault.transcripts.load(fixture.audio)))
            assertEquals(listOf(other), value(vault.transcripts.history()))
            assertEquals(listOf(other), search(vault).map { it.recording.identity })
            value(vault.deleteRecordingConfirmed(fixture.audio))
            assertTrue(value(vault.originals.acquire(other)) is OriginalAudioStatus.Available)
        }
        fixture.scan()
    }

    @Test
    fun interruptedDeletionHasDurableIntentAndVisibleRetryAfterRestart() {
        val fixture = EncryptedAudioVaultFaultFixture()
        fixture.open().use { seed(it, fixture.audio) }
        fixture
            .open(
                false,
                EncryptedAudioVault.Dependencies(
                    deletionStep = {
                        if (it == "TOMBSTONE") error("synthetic interruption")
                    }
                ),
            )
            .use { vault ->
                assertTrue(vault.deleteRecordingConfirmed(fixture.audio) is AudioResult.Failed)
            }
        fixture.open(create = false).use { vault ->
            assertNull(value(vault.transcripts.load(fixture.audio)))
            assertTrue(value(vault.sourceState(fixture.audio)) is AudioSourceState.Deleting)
            assertEquals(listOf(fixture.audio), search(vault).map { it.recording.identity })
            value(vault.retryDeletion(fixture.audio))
            assertTrue(value(vault.inspectAudioDeletion(fixture.audio)).complete)
            assertTrue(search(vault).isEmpty())
        }
    }

    @Test
    fun audioOnlyKeepsEditsUntilExplicitWholeRecordingDeletion() {
        val fixture = EncryptedAudioVaultFaultFixture()
        fixture.open().use { vault ->
            seed(vault, fixture.audio)
            val before = value(vault.transcripts.load(fixture.audio))
            value(vault.deleteAudio(fixture.audio))
            assertEquals(before, value(vault.transcripts.load(fixture.audio)))
            value(vault.deleteRecordingConfirmed(fixture.audio))
            assertNull(value(vault.transcripts.load(fixture.audio)))
            assertTrue(value(vault.transcripts.history()).isEmpty())
        }
    }

    private fun seed(vault: EncryptedAudioVault, identity: AudioIdentity) {
        value(vault.writer.create(identity))
        value(
            vault.writer.append(
                AudioStorageUnitIdentity(identity, id(), 0, 0),
                AudioFormat.PCM,
                ByteArray(320),
            )
        )
        value(vault.writer.finalize(identity))
        val source =
            (value(vault.originals.acquire(identity)) as OriginalAudioStatus.Available).reference
        val job = id()
        value(vault.transcripts.begin(source, job, "ru", 1))
        val doc =
            value(vault.transcripts.finish(source, job, "Synthetic transcript", "synthetic", 2))
        value(
            vault.transcripts.edit(
                identity,
                job,
                doc.revision,
                "Исправления\n\nSecond paragraph",
                3,
            )
        )
    }

    private fun search(vault: EncryptedAudioVault) =
        value(vault.recordingSearchPage("", "Recording %s", "") { false }).rows

    private fun identity() = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())

    private fun id() = UUID.randomUUID().toString()

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue("Expected successful encrypted operation", result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }
}
