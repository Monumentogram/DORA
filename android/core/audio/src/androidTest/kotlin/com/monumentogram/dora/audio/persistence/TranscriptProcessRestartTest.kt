package com.monumentogram.dora.audio.persistence

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Invoke the two phases in distinct instrumentation processes with the same synthetic run id. */
class TranscriptProcessRestartTest {
    @Test
    fun seed() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("transcriptProcessPhase") == "SEED"
        )
        val context = context()
        check(context.noBackupFilesDir.mkdir())
        value(EncryptedAudioVault.open(context, true, {}, { it() })).use { vault ->
            value(vault.writer.create(identity))
            value(
                vault.writer.append(
                    AudioStorageUnitIdentity(identity, id(4), 0, 0),
                    AudioFormat.PCM,
                    ByteArray(320),
                )
            )
            value(vault.writer.finalize(identity))
            val source =
                (value(vault.originals.acquire(identity)) as OriginalAudioStatus.Available)
                    .reference
            value(vault.transcripts.begin(source, id(5), "ru", 1))
            val finished =
                value(
                    vault.transcripts.finish(
                        source,
                        id(5),
                        "Synthetic restart machine",
                        "small-q8",
                        2,
                    )
                )
            value(
                vault.transcripts.edit(
                    identity,
                    id(5),
                    finished.revision,
                    "Synthetic restart correction",
                    3,
                )
            )
            value(vault.transcripts.begin(source, id(6), "en", 4))
            value(vault.transcripts.running(identity, id(6)))
        }
    }

    @Test
    fun readAfterProcessRestart() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("transcriptProcessPhase") == "READ"
        )
        value(EncryptedAudioVault.openExisting(context()) {}).use { vault ->
            val before = value(vault.transcripts.load(identity))!!
            assertEquals("RUNNING", before.jobs.last().status)
            assertEquals("Synthetic restart correction", before.rendered(id(5)))
            val recovered = value(vault.transcripts.recover(identity))!!
            assertEquals("INTERRUPTED", recovered.jobs.last().status)
            assertEquals(before.versions, recovered.versions)
            assertEquals(before.edits, recovered.edits)
            assertEquals(id(5), recovered.activeId)
            assertEquals(listOf(identity), value(vault.transcripts.history()))
        }
    }

    private fun context(): Context {
        val run =
            checkNotNull(InstrumentationRegistry.getArguments().getString("transcriptProcessRun"))
        require(run.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.noBackupFilesDir, "transcript-process-$run")
        return object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this

            override fun getNoBackupFilesDir(): File = root
        }
    }

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue(result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }

    private val identity = AudioIdentity(RecordingId(id(1)), AudioAssetId(id(2)), id(3))

    private fun id(number: Int) = "00000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
}
