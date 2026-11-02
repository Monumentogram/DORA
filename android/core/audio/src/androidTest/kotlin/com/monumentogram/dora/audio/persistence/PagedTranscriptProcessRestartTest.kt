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
import com.monumentogram.dora.audio.transcript.PagedTranscriptSpec
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Select each phase explicitly in separate instrumentation processes; no native inference. */
class PagedTranscriptProcessRestartTest {
    @Test
    @Suppress("LongMethod") // This phase durably seeds one restart fixture and its prior history.
    fun seed() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("pagedTranscriptProcessPhase") ==
                "SEED"
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
            value(vault.transcripts.begin(source, id(5), "en", 1))
            val legacy =
                value(vault.transcripts.finish(source, id(5), "prior public version", "model", 2))
            value(
                vault.transcripts.edit(
                    identity,
                    id(5),
                    legacy.revision,
                    "retained owner correction",
                    3,
                )
            )
            val progress =
                value(
                    vault.transcripts.paged.begin(
                        PagedTranscriptSpec(
                            source,
                            id(6),
                            "LOCAL",
                            "en",
                            "model",
                            "local-fixed-300-v1",
                            27,
                            4,
                        )
                    )
                )
            value(
                vault.transcripts.paged.commitPart(
                    source,
                    id(6),
                    progress.attempt,
                    0,
                    "first durable part",
                    null,
                )
            )
            value(
                vault.transcripts.paged.commitPart(
                    source,
                    id(6),
                    progress.attempt,
                    1,
                    "second durable part",
                    null,
                )
            )
            assertEquals(1, value(vault.transcripts.load(identity))!!.versions.size)
        }
    }

    @Test
    fun resumeAfterProcessRestart() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("pagedTranscriptProcessPhase") ==
                "READ"
        )
        value(EncryptedAudioVault.openExisting(context()) {}).use { vault ->
            val before = value(vault.transcripts.load(identity))!!
            assertEquals("RUNNING", before.jobs.last().status)
            val recovered = value(vault.transcripts.recover(identity))!!
            assertEquals("INTERRUPTED", recovered.jobs.last().status)
            assertEquals("retained owner correction", recovered.rendered(id(5)))
            val progress = value(vault.transcripts.paged.resume(identity, id(6)))
            assertEquals(2, progress.completedParts)
            assertEquals(2, progress.nextIncompleteIndex)
            assertTrue(progress.attempt > 1)
            assertEquals(
                "first durable part",
                value(vault.transcripts.paged.page(identity, id(6), null, allowPartial = true))!!
                    .text,
            )
            for (index in 2 until 6) value(
                vault.transcripts.paged.commitPart(
                    progress.spec.source,
                    id(6),
                    progress.attempt,
                    index,
                    "remaining durable part $index",
                    null,
                )
            )
            val completed =
                value(
                    vault.transcripts.paged.finish(progress.spec.source, id(6), progress.attempt, 5)
                )
            assertEquals(2, completed.versions.size)
            assertEquals(before.versions, completed.versions.take(1))
            assertEquals(before.edits, completed.edits)
            assertEquals(before.selections, completed.selections)
            assertEquals(before.activeId, completed.activeId)
        }
    }

    private fun context(): Context {
        val run =
            checkNotNull(
                InstrumentationRegistry.getArguments().getString("pagedTranscriptProcessRun")
            )
        require(run.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.noBackupFilesDir, "paged-transcript-process-$run")
        return object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this

            override fun getNoBackupFilesDir(): File = root
        }
    }

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue("Protected paged restart operation failed", result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }

    private val identity = AudioIdentity(RecordingId(id(1)), AudioAssetId(id(2)), id(3))

    private fun id(number: Int) = "00000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
}
