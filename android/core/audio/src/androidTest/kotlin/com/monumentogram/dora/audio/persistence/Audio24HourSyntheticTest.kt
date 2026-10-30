package com.monumentogram.dora.audio.persistence

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.AuthenticatedAudioSource
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.transcript.ProtectedPcm
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in actual SQLCipher/Recovery/AndroidKeystore qualification, synthetic PCM only. This writes
 * 2.7648GB in 17,280 encrypted five-second units; it does not simulate wall-clock microphone
 * capture. Run with -e dora24hSynthetic true; -e doraSyntheticUnits 24 is a short harness smoke
 * test.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
class Audio24HourSyntheticTest {
    @Test
    fun encryptedTwentyFourHoursReopensSeeksStreams288BoundedWindowsAndDeletes() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Large synthetic qualification is opt-in",
            arguments.getString("dora24hSynthetic") == "true",
        )
        val units = arguments.getString("doraSyntheticUnits")?.toInt() ?: MAX_UNITS
        require(units in 2..MAX_UNITS)
        val totalFrames = units * UNIT_FRAMES
        val half = units / 2
        val fixture = EncryptedAudioVaultFaultFixture()
        val availableAtStart = fixture.context.noBackupFilesDir.usableSpace
        Log.i(TAG, "stage=preflight units=$units available_bytes=$availableAtStart")
        assumeTrue(
            "Full synthetic run needs4.6GB available including checkpoint headroom",
            units != MAX_UNITS || availableAtStart >= 4_600_000_000L,
        )
        val metrics = AudioQueryMetrics()
        val dependencies = EncryptedAudioVault.Dependencies(helperFactory = metrics::factory)
        val expected = MessageDigest.getInstance("SHA-256")
        val prefixExpected = MessageDigest.getInstance("SHA-256")
        val pcm = ByteArray(UNIT_BYTES) { baseByte(it) }
        val began = SystemClock.elapsedRealtime()
        var physical = UUID.randomUUID().toString()
        val epoch = physical
        var physicalFirst = 0L
        var vault: EncryptedAudioVault? = fixture.open(dependencies = dependencies)
        try {
            success(vault!!.writer.createLogicalRecording(fixture.audio))
            // Resolve once while the fixture is empty; metrics must not enumerate long history.
            val walFile =
                fixture.context.noBackupFilesDir.walkTopDown().first {
                    it.isFile && it.name.endsWith("-wal")
                }
            for (ordinal in 0 until units) {
                val first = ordinal * UNIT_FRAMES
                if (ordinal % ROTATION_UNITS == 0) {
                    if (ordinal != 0) physical = UUID.randomUUID().toString()
                    physicalFirst = first
                    success(
                        vault!!
                            .writer
                            .segmentation(
                                fixture.audio,
                                SegmentationMetadata(
                                    SegmentationKind.TECHNICAL_OPEN,
                                    physical,
                                    first,
                                    first,
                                    epoch,
                                    if (first == 0L) null else maxOf(0, first - 32_000),
                                    if (first == 0L) "START" else "CAP",
                                ),
                            )
                    )
                }
                mark(pcm, ordinal)
                expected.update(pcm)
                if (ordinal < half) prefixExpected.update(pcm)
                val unit =
                    AudioStorageUnitIdentity(
                        fixture.audio,
                        UUID.randomUUID().toString(),
                        ordinal,
                        first,
                        physical,
                        physicalFirst,
                        first - physicalFirst,
                    )
                success(vault!!.writer.append(unit, AudioFormat.PCM, pcm))
                val count = ordinal + 1
                if (count % ROTATION_UNITS == 0 || count == units) {
                    success(
                        vault!!
                            .writer
                            .segmentation(
                                fixture.audio,
                                SegmentationMetadata(
                                    SegmentationKind.TECHNICAL_CLOSE,
                                    physical,
                                    physicalFirst,
                                    first + UNIT_FRAMES,
                                    epoch,
                                    if (physicalFirst == 0L) null
                                    else maxOf(0, physicalFirst - 32_000),
                                    if (count == units) "STOP" else "CAP",
                                ),
                            )
                    )
                }
                if (count in CHECKPOINTS || count == units)
                    checkpoint(
                        "append",
                        count,
                        began,
                        metrics,
                        fixture.context.noBackupFilesDir.usableSpace,
                        walFile.length(),
                    )
                // Two opens may each scan metadata once. Historic row loads must not grow per
                // append.
                assertTrue(
                    "Append metadata must stay indexed",
                    metrics.catalogBulkQueries.get() <= 8,
                )
                if (count == half) {
                    vault!!.close()
                    vault = fixture.open(false, dependencies)
                    success(vault!!.writer.reconcile(fixture.audio))
                    val actualPrefix = MessageDigest.getInstance("SHA-256")
                    var next = 0L
                    val recovered =
                        vault!!.reader.extract(fixture.audio) { frame, bytes ->
                            assertEquals(next, frame)
                            assertTrue(bytes.size <= UNIT_BYTES)
                            actualPrefix.update(bytes)
                            next += bytes.size / 2
                        }
                    assertTrue(recovered is AudioResult.Value)
                    val summary = (recovered as AudioResult.Value).value
                    assertEquals(half * UNIT_FRAMES, summary.frames)
                    assertEquals(AudioCompletion.PARTIAL_RECOVERED, summary.completion)
                    assertArrayEquals(prefixExpected.digest(), actualPrefix.digest())
                    checkpoint("reopened_prefix", count, began, metrics)
                }
            }
            pcm.fill(0)
            success(vault!!.writer.finalize(fixture.audio))
            checkpoint("finalized", units, began, metrics)
            vault!!.close()
            vault = fixture.open(false, dependencies)
            val opened = vault!!.originals.openAuthenticated(fixture.audio)
            assertTrue(opened is AudioResult.Value)
            (opened as AudioResult.Value).value.use { authenticated ->
                assertEquals(totalFrames, authenticated.reference.frames)
                if (units == MAX_UNITS)
                    assertEquals(2_764_800_000L, authenticated.reference.frames * 2)
                // Random beginning/middle/end seeks use exact global Long offsets after one open.
                for (first in listOf(0L, totalFrames / 2, totalFrames - 1)) {
                    ProtectedPcm.readWindow(authenticated, first, 1) { false }
                        .use {
                            assertEquals(expectedSample(first), it.samples.single(), 0f)
                        }
                }
                val actual = MessageDigest.getInstance("SHA-256")
                var next = 0L
                val hashingSource =
                    object : AuthenticatedAudioSource by authenticated {
                        override fun extractRange(
                            firstFrame: Long,
                            frameCount: Long,
                            consume: (Long, ByteArray) -> Unit,
                        ) =
                            authenticated.extractRange(firstFrame, frameCount) { frame, bytes ->
                                assertEquals(next, frame)
                                assertTrue(bytes.size <= UNIT_BYTES)
                                actual.update(bytes)
                                next += bytes.size / 2
                                consume(frame, bytes)
                            }
                    }
                var windows = 0
                var position = 0L
                while (position < totalFrames) {
                    val count =
                        minOf(ProtectedPcm.MAX_WINDOW_FRAMES.toLong(), totalFrames - position)
                            .toInt()
                    val window = ProtectedPcm.readWindow(hashingSource, position, count) { false }
                    assertEquals(expectedSample(position), window.samples.first(), 0f)
                    assertEquals(expectedSample(position + count - 1), window.samples.last(), 0f)
                    window.close()
                    assertEquals(0f, window.samples.first(), 0f)
                    assertEquals(0f, window.samples.last(), 0f)
                    position += count
                    windows++
                    if (windows % 12 == 0) checkpoint("window_$windows", units, began, metrics)
                }
                assertEquals(totalFrames, next)
                assertEquals((units + 59) / 60, windows)
                if (units == MAX_UNITS) assertEquals(288, windows)
                assertArrayEquals(expected.digest(), actual.digest())
                checkpoint("exact_digest", units, began, metrics)
                success(vault!!.deleteConfirmed(fixture.audio))
                assertEquals(
                    AudioResult.Value(OriginalAudioStatus.SourceDeleted),
                    authenticated.checkAvailable(),
                )
                assertEquals(
                    AudioResult.Value(OriginalAudioStatus.SourceDeleted),
                    authenticated.extractRange(totalFrames - 1, 1) { _, _ -> error("Deleted PCM") },
                )
                checkpoint("deleted", units, began, metrics)
            }
            val inspection = vault!!.inspectAudioDeletion(fixture.audio)
            assertTrue(inspection is AudioResult.Value && inspection.value.complete)
        } finally {
            pcm.fill(0)
            vault?.close()
        }
    }

    @Suppress(
        "LongParameterList"
    ) // One content-free sample binds time, memory, SQL and disk metrics.
    private fun checkpoint(
        stage: String,
        units: Int,
        began: Long,
        metrics: AudioQueryMetrics,
        availableBytes: Long = -1,
        walBytes: Long = -1,
    ) {
        Log.i(
            TAG,
            "stage=$stage units=$units frames=${units * UNIT_FRAMES} bytes=${units * UNIT_FRAMES * 2} " +
                "elapsed_ms=${SystemClock.elapsedRealtime() - began} pss_kib=${Debug.getPss()} " +
                "queries=${metrics.queries.get()} catalog_bulk_queries=${metrics.catalogBulkQueries.get()} " +
                "catalog_bulk_rows=${metrics.catalogBulkRows.get()} " +
                "deletion_bulk_queries=${metrics.deletionBulkQueries.get()} " +
                "deletion_rows=${metrics.deletionRows.get()} available_bytes=$availableBytes wal_bytes=$walBytes",
        )
    }

    private fun success(result: AudioResult<*>) =
        assertTrue("Synthetic vault operation failed: $result", result is AudioResult.Value)

    private fun mark(bytes: ByteArray, ordinal: Int) {
        for (offset in 0 until 8) {
            bytes[offset] = (ordinal.toLong() ushr (offset * 8)).toByte()
            bytes[bytes.size - 8 + offset] = (ordinal.toLong().inv() ushr (offset * 8)).toByte()
        }
    }

    private fun expectedSample(frame: Long): Float {
        val unit = (frame / UNIT_FRAMES).toInt()
        val index = (frame % UNIT_FRAMES).toInt() * 2
        fun byte(offset: Int): Int =
            when {
                offset < 8 -> (unit.toLong() ushr (offset * 8)).toByte().toInt()
                offset >= UNIT_BYTES - 8 ->
                    (unit.toLong().inv() ushr ((offset - UNIT_BYTES + 8) * 8)).toByte().toInt()
                else -> baseByte(offset).toInt()
            }
        return ((byte(index) and 255) or (byte(index + 1) shl 8)).toShort() / 32768f
    }

    private companion object {
        const val TAG = "Dora24hSynthetic"
        const val MAX_UNITS = 17_280
        const val UNIT_FRAMES = 80_000L
        const val UNIT_BYTES = 160_000
        const val ROTATION_UNITS = 120
        val CHECKPOINTS =
            setOf(1, 2, 4, 8, 16, 32, 64, 120, 240, 480, 1_200, 4_320, 8_640, 12_960, 17_280)

        fun baseByte(index: Int) = (index * 37 + 19).toByte()
    }
}
