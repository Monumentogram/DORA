package com.monumentogram.dora.poc.capture.audio

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WavIOTest {
    @Test
    fun `WAV header is finalized and finish is idempotent`() {
        val directory = Files.createTempDirectory("dora-wav-test").toFile()
        try {
            val part = File(directory, "capture-run-a-test.wav.part")
            val samples = byteArrayOf(1, 0, 2, 0, 3, 0, 4, 0)
            val writer = WavWriter(part, WavFormat(sampleRate = 16_000))
            writer.write(samples, 0, samples.size)

            val finalized = writer.finish()
            val secondFinish = writer.finish()
            val analysis = WavAnalyzer.analyze(finalized)

            assertEquals(finalized, secondFinish)
            assertFalse(part.exists())
            assertTrue(finalized.exists())
            assertEquals(52L, finalized.length())
            assertTrue(analysis.valid)
            assertEquals(8L, analysis.dataBytes)
            assertEquals(16_000, analysis.sampleRate)
            assertNotNull(analysis.sha256)
            val stored = finalized.readBytes().copyOfRange(44, 52)
            assertArrayEquals(samples, stored)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `deletion receipt is created after actual absence verification`() {
        val directory = Files.createTempDirectory("dora-delete-test").toFile()
        try {
            val part = File(directory, "capture-run-a-delete.wav.part")
            val writer = WavWriter(part, WavFormat(sampleRate = 16_000))
            writer.write(byteArrayOf(1, 0), 0, 2)
            val wav = writer.finish()

            val receipt =
                WavAnalyzer.analyzeAndDelete(
                    wav,
                    "run-a-20260805T000000Z-12345678",
                    "2026-08-05T00:00:01Z",
                )

            assertTrue(receipt.deletionSucceeded)
            assertTrue(receipt.absenceVerified)
            assertFalse(wav.exists())
            assertNotNull(receipt.sha256)
        } finally {
            directory.deleteRecursively()
        }
    }
}
