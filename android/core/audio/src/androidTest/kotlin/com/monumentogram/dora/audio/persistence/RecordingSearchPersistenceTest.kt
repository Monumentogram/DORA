@file:Suppress("MagicNumber", "LongMethod", "MaxLineLength")

package com.monumentogram.dora.audio.persistence

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSearchPersistenceTest {
    @Test
    fun activeEditsSelectionDeletionAndReopenUseEncryptedSource() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val identity = fixture.audio
        val first = UUID.randomUUID().toString()
        fixture.open().use { vault ->
            value(vault.writer.create(identity))
            value(fixture.append(vault))
            value(vault.writer.finalize(identity))
            assertEquals(1, search(vault, identity.recordingId.value.take(8)).rows.size)
            val source =
                (value(vault.originals.acquire(identity)) as OriginalAudioStatus.Available)
                    .reference
            value(vault.transcripts.begin(source, first, "ru", 1))
            val raw =
                value(
                    vault.transcripts.finish(
                        source,
                        first,
                        "Заголовок\nСегодня обсуждали конвейерное оборудование",
                        "synthetic",
                        2,
                    )
                )
            assertEquals(1, search(vault, "КОНВЕЙЕР").rows.size)
            val edited =
                value(
                    vault.transcripts.edit(
                        identity,
                        first,
                        raw.revision,
                        "Исправленный текст пользователя",
                        3,
                    )
                )
            assertTrue(search(vault, "конвейер").rows.isEmpty())
            assertEquals(1, search(vault, "пользоват").rows.size)
            val next = UUID.randomUUID().toString()
            value(vault.transcripts.begin(source, next, "en", 4))
            val second =
                value(
                    vault.transcripts.finish(
                        source,
                        next,
                        "English replacement equipment",
                        "synthetic",
                        5,
                    )
                )
            assertTrue(search(vault, "replacement").rows.isEmpty())
            value(
                vault.transcripts.select(
                    identity,
                    next,
                    second.revision,
                    edited.edits.map { it.id }.toSet(),
                    6,
                )
            )
            assertTrue(search(vault, "пользоват").rows.isEmpty())
            assertEquals(1, search(vault, "REPLACE").rows.size)
        }
        fixture.open(create = false).use { vault ->
            assertEquals(1, search(vault, "equipment").rows.size)
            val before = value(vault.transcripts.load(identity))!!
            value(vault.deleteAudio(identity)) // This test's isolated synthetic source only.
            assertTrue(search(vault, "equipment").rows.isEmpty())
            assertEquals(before, value(vault.transcripts.load(identity)))
        }
        fixture.open(create = false).use { vault ->
            assertTrue(search(vault, "equipment").rows.isEmpty())
        }
        fixture.scan()
    }

    @Test
    fun syntheticEncryptedLibraries10_100_1000() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val measurements = mutableListOf<String>()
        fixture.open().use { vault ->
            for (number in 1..1000) {
                val identity =
                    AudioIdentity(
                        RecordingId(UUID.randomUUID().toString()),
                        AudioAssetId(
                            "00000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
                        ),
                        UUID.randomUUID().toString(),
                    )
                value(vault.writer.create(identity))
                value(
                    vault.writer.append(
                        AudioStorageUnitIdentity(identity, UUID.randomUUID().toString(), 0, 0),
                        AudioFormat.PCM,
                        ByteArray(32),
                    )
                )
                value(vault.writer.finalize(identity))
                val source =
                    (value(vault.originals.acquire(identity)) as OriginalAudioStatus.Available)
                        .reference
                val job = UUID.randomUUID().toString()
                value(vault.transcripts.begin(source, job, "ru", 1))
                val text =
                    "Синтетическая запись $number\n" +
                        "Обсуждаем оборудование и план модернизации. "
                            .repeat(if (number == 1000) 2000 else 100) +
                        " terminal$number"
                value(vault.transcripts.finish(source, job, text, "synthetic-only", 2))
                if (number in setOf(10, 100, 1000)) {
                    repeat(3) { sample ->
                        for ((kind, query) in
                            listOf(
                                "no-match" to "absent-needle",
                                "first-page" to "оборуд",
                                "tail-match" to "terminal$number",
                            )) {
                            val start = SystemClock.elapsedRealtimeNanos()
                            val result = search(vault, query)
                            val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                            if (kind == "no-match") assertTrue(result.rows.isEmpty())
                            else assertTrue(result.rows.isNotEmpty())
                            measurements += "$number,$sample,$kind,$elapsed"
                        }
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "stage103c-performance.csv")
            .writeText(
                "records,sample,query_kind,elapsed_ms\n" + measurements.joinToString("\n") + "\n"
            )
    }

    private fun search(vault: EncryptedAudioVault, query: String) =
        value(vault.recordingSearchPage(query, "Запись %s", "") { false })

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue("Expected authenticated operation", result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }
}
