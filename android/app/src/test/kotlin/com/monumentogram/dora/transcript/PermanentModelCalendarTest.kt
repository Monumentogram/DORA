package com.monumentogram.dora.transcript

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same test can run against the old clock-injected store as a negative control. */
class PermanentModelCalendarTest {
    private val fixture = byteArrayOf(1, 2, 3, 4)
    private val formerDeadline = Instant.parse("2026-11-02T21:00:00Z")
    private var clock: Clock = Clock.fixed(formerDeadline.minusSeconds(1), ZoneOffset.UTC)

    private fun at(instant: Instant) {
        clock = Clock.fixed(instant, ZoneOffset.UTC)
    }

    private fun store(directory: File): PinnedModelStore {
        val constructor = PinnedModelStore::class.java.declaredConstructors.single()
        val arguments =
            mutableListOf<Any>(
                directory,
                fixture.size.toLong(),
                MessageDigest.getInstance("SHA-256").digest(fixture),
            )
        // Only the legacy implementation consumes time. Do not reintroduce a production clock
        // dependency merely to test its removal. Reflection keeps this negative control compilable.
        if (constructor.parameterCount == 5) {
            arguments.add(formerDeadline)
            arguments.add { clock.instant() }
        }
        return constructor.newInstance(*arguments.toTypedArray()) as PinnedModelStore
    }

    private fun withDirectory(action: (File) -> Unit) {
        val directory = Files.createTempDirectory("permanent-model-calendar").toFile()
        try {
            action(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun installedModelSurvivesDeadlineAndFutureReopens() = withDirectory { directory ->
        val existing = store(directory)
        assertTrue(existing.install(ByteArrayInputStream(fixture)))
        val installed = existing.installed()!!
        for (instant in
            listOf(
                formerDeadline,
                Instant.parse("2026-11-04T12:00:00Z"),
                Instant.parse("2100-01-01T00:00:00Z"),
            )) {
            at(instant)
            assertNotNull("Existing model at $instant", existing.installed())
            assertTrue("No calendar deletion at $instant", installed.isFile)
            assertArrayEquals(fixture, store(directory).installed()!!.readBytes())
            assertTrue(
                "Import at $instant",
                store(directory).install(ByteArrayInputStream(fixture)),
            )
        }
    }

    @Test
    fun importCrossingFormerDeadlineStillPromotesVerifiedBytes() = withDirectory { directory ->
        val existing = store(directory)
        val source =
            object : ByteArrayInputStream(fixture) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    at(Instant.parse("2026-11-04T12:00:00Z"))
                    return super.read(buffer, offset, length)
                }
            }
        assertTrue(existing.install(source))
        assertArrayEquals(fixture, existing.installed()!!.readBytes())
        assertTrue(directory.listFiles()!!.none { it.name.endsWith(".pending") })
    }
}
