package com.monumentogram.dora.audio.persistence

import android.os.SystemClock
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.EncryptedAudioCatalog
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.StoredAudioAsset
import com.monumentogram.dora.audio.persistence.auth.LocalAccessDeniedException
import com.monumentogram.dora.audio.persistence.keys.VaultKeystoreIo
import com.monumentogram.dora.audio.recording.RecordingLibraryState
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingLibraryProjectionTest {
    @Test
    fun listingReadsNoAudioKeysWhileFullAcquireStillRejectsCorruptTail() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val opens = AtomicInteger()
        val dependencies =
            EncryptedAudioVault.Dependencies(
                runKeystore = { delegate ->
                    object : VaultKeystoreIo by delegate {
                        override fun open(alias: String): javax.crypto.SecretKey? {
                            opens.incrementAndGet()
                            return delegate.open(alias)
                        }
                    }
                }
            )
        val next =
            fixture.unit.copy(
                unitId = EncryptedAudioVaultFaultFixture.id(),
                ordinal = 1,
                firstFrame = 80_000,
                sourceFrameOffset = 80_000,
            )
        fixture.open(dependencies = dependencies).use { vault ->
            value(vault.writer.create(fixture.audio))
            value(vault.writer.append(fixture.unit, AudioFormat.PCM, ByteArray(160_000)))
            value(vault.writer.append(next, AudioFormat.PCM, ByteArray(160_000)))
            value(vault.writer.finalize(fixture.audio))
        }
        val ciphertext =
            File(fixture.run(next.unitId), RecoveryRelativeNames.microfileCiphertext(0UL))
        val changed = ciphertext.readBytes()
        changed[changed.lastIndex] = (changed.last().toInt() xor 1).toByte()
        ciphertext.writeBytes(changed)
        fixture.open(false, dependencies).use { vault ->
            opens.set(0)
            val page = value(vault.recordingSearchPage("", "Recording %s", "") { false })
            assertEquals(1, page.rows.size)
            assertEquals(160_000L, page.rows.single().recording.committedFrames)
            assertEquals(RecordingLibraryState.FINALIZED, page.rows.single().recording.state)
            assertFalse(page.rows.single().recording.canResume)
            assertEquals("Listing must not authenticate or repair PCM", 0, opens.get())
            val acquired = value(vault.originals.acquire(fixture.audio))
            assertTrue(acquired is OriginalAudioStatus.SourceUnavailable)
            assertTrue("Full acquisition must still authenticate encrypted units", opens.get() > 0)
            opens.set(0)
            val retained = value(vault.recordingSearchPage("", "Recording %s", "") { false })
            assertEquals(RecordingLibraryState.UNAVAILABLE, retained.rows.single().recording.state)
            assertEquals(AudioFailure.CORRUPT, retained.rows.single().recording.failure)
            assertEquals(0, opens.get())
        }
    }

    @Test
    fun longSourceListingReturnsScalarMetadataWithoutOpeningKeysOrLoadingHistoricRows() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val opens = AtomicInteger()
        val metrics = AudioQueryMetrics()
        val dependencies = counted(opens).copy(helperFactory = metrics::factory)
        val pcm = ByteArray(160_000)
        fixture.open(dependencies = dependencies).use { vault ->
            value(vault.writer.create(fixture.audio))
            var physical = EncryptedAudioVaultFaultFixture.id()
            repeat(361) { ordinal ->
                if (ordinal % 120 == 0) physical = EncryptedAudioVaultFaultFixture.id()
                val first = ordinal * 80_000L
                val origin = ordinal / 120 * 9_600_000L
                value(
                    vault.writer.append(
                        fixture.unit.copy(
                            unitId = EncryptedAudioVaultFaultFixture.id(),
                            ordinal = ordinal,
                            firstFrame = first,
                            physicalSegmentId = physical,
                            physicalFirstFrame = origin,
                            sourceFrameOffset = first - origin,
                        ),
                        AudioFormat.PCM,
                        pcm,
                    )
                )
            }
            value(vault.writer.finalize(fixture.audio))
        }
        fixture.open(false, dependencies).use { vault ->
            opens.set(0)
            val before = metrics.catalogBulkQueries.get()
            val began = SystemClock.elapsedRealtime()
            val rows = value(vault.recordingSearchPage("", "Recording %s", "") { false }).rows
            val elapsed = SystemClock.elapsedRealtime() - began
            assertEquals(28_880_000L, rows.single().recording.committedFrames)
            assertEquals(RecordingLibraryState.FINALIZED, rows.single().recording.state)
            assertFalse(rows.single().recording.canResume)
            assertEquals(0, opens.get())
            assertEquals(before, metrics.catalogBulkQueries.get())
            Log.i(
                "DoraLibraryProjection",
                "units=361 pcm_bytes=57760000 list_ms=$elapsed run_key_opens=0 historic_row_lists=0",
            )
            value(vault.deleteConfirmed(fixture.audio))
        }
        pcm.fill(0)
    }

    @Test
    fun pendingAppendStaysUnreconciledAndDoesNotIncreaseDisplayedCommittedFrames() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val opens = AtomicInteger()
        val reject = AtomicBoolean()
        val dependencies =
            counted(opens)
                .copy(
                    catalog = { delegate ->
                        object : EncryptedAudioCatalog by delegate {
                            override fun compareAndSet(
                                expected: StoredAudioAsset,
                                next: StoredAudioAsset,
                            ): Boolean =
                                if (reject.compareAndSet(true, false)) false
                                else delegate.compareAndSet(expected, next)
                        }
                    }
                )
        val next =
            fixture.unit.copy(
                unitId = EncryptedAudioVaultFaultFixture.id(),
                ordinal = 1,
                firstFrame = 160,
                sourceFrameOffset = 160,
            )
        fixture.open(dependencies = dependencies).use { vault ->
            value(vault.writer.create(fixture.audio))
            value(fixture.append(vault))
            reject.set(true)
            assertEquals(AudioResult.Failed(AudioFailure.UNCERTAIN), fixture.append(vault, next))
            opens.set(0)
            val metadata =
                value(vault.recordingSearchPage("", "Recording %s", "") { false })
                    .rows
                    .single()
                    .recording
            assertEquals(160L, metadata.committedFrames)
            assertEquals(RecordingLibraryState.NEEDS_RECOVERY, metadata.state)
            assertFalse(metadata.canResume)
            assertEquals(0, opens.get())
            assertEquals(AudioResult.Failed(AudioFailure.COLLISION), fixture.append(vault, next))
            value(vault.deleteConfirmed(fixture.audio))
        }
    }

    @Test
    fun structuralMetadataCorruptionAndRevocationFailClosedWithoutAudioReads() {
        val fixture = EncryptedAudioVaultFaultFixture()
        val opens = AtomicInteger()
        var database: SupportSQLiteDatabase? = null
        val dependencies =
            counted(opens)
                .copy(
                    helperFactory = { delegate ->
                        SupportSQLiteOpenHelper.Factory { config ->
                            val helper = delegate.create(config)
                            object : SupportSQLiteOpenHelper by helper {
                                override val writableDatabase
                                    get() = helper.writableDatabase.also { database = it }
                            }
                        }
                    }
                )
        var locked = false
        value(
                EncryptedAudioVault.open(
                    fixture.context,
                    true,
                    { if (locked) throw LocalAccessDeniedException() },
                    { it() },
                    dependencies,
                )
            )
            .use { vault ->
                value(vault.writer.create(fixture.audio))
                value(fixture.append(vault))
                value(vault.writer.finalize(fixture.audio))
                opens.set(0)
                locked = true
                assertEquals(
                    AudioResult.Failed(AudioFailure.LOCKED),
                    vault.recordingSearchPage("", "Recording %s", "") { false },
                )
                locked = false
                assertEquals(
                    AudioResult.Failed(AudioFailure.CANCELLED),
                    vault.recordingSearchPage("", "Recording %s", "") { true },
                )
                checkNotNull(database)
                    .execSQL(
                        "UPDATE unit_claim SET frames=frames+1 WHERE runId=?",
                        arrayOf(fixture.unit.unitId),
                    )
                assertEquals(
                    AudioResult.Failed(AudioFailure.CORRUPT),
                    vault.recordingSearchPage("", "Recording %s", "") { false },
                )
                assertEquals(0, opens.get())
            }
    }

    private fun counted(opens: AtomicInteger) =
        EncryptedAudioVault.Dependencies(
            runKeystore = { delegate ->
                object : VaultKeystoreIo by delegate {
                    override fun open(alias: String): javax.crypto.SecretKey? {
                        opens.incrementAndGet()
                        return delegate.open(alias)
                    }
                }
            }
        )

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue("Expected authenticated operation", result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }
}
