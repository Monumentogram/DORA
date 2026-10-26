package com.monumentogram.dora.transcript

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedModelStoreTest {
    private val fixture = byteArrayOf(1, 2, 3, 4)
    private val expiry = Instant.parse("2026-10-26T00:00:00Z")

    private fun withStore(action: (PinnedModelStore, File, (Instant) -> Unit) -> Unit) {
        val directory = Files.createTempDirectory("model-admission").toFile()
        var now = expiry.minusSeconds(1)
        val digest = MessageDigest.getInstance("SHA-256").digest(fixture)
        try {
            action(
                PinnedModelStore(directory, fixture.size.toLong(), digest, expiry) { now },
                directory,
            ) {
                now = it
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun exactModelIsInstalledAndMutationIsDetectedOnNextUse() = withStore { store, _, _ ->
        assertTrue(store.install(ByteArrayInputStream(fixture)))
        val installed = store.installed()
        assertNotNull(installed)
        installed!!.writeBytes(byteArrayOf(4, 3, 2, 1))
        assertNull(store.installed())
    }

    @Test
    fun failedReplacementPreservesTheAdmittedModelAndRemovesStaging() =
        withStore { store, directory, _ ->
            assertTrue(store.install(ByteArrayInputStream(fixture)))
            assertFalse(store.install(ByteArrayInputStream(byteArrayOf(1, 2, 3))))
            assertFalse(store.install(ByteArrayInputStream(byteArrayOf(4, 3, 2, 1))))
            assertFalse(store.install(ByteArrayInputStream(fixture + 5)))
            assertArrayEquals(fixture, store.installed()!!.readBytes())
            assertTrue(directory.listFiles()!!.none { it.name.endsWith(".pending") })
        }

    @Test
    fun deadlineRejectsInstallAndUseAndRemovesExpiredModel() = withStore { store, _, setNow ->
        assertTrue(store.install(ByteArrayInputStream(fixture)))
        val installed = store.installed()!!
        setNow(expiry)
        assertNull(store.installed())
        assertFalse(installed.exists())
        assertFalse(store.install(ByteArrayInputStream(fixture)))
    }

    @Test
    fun oversizedStreamIsStoppedAtOneBytePastTheExpectedLength() = withStore { store, _, _ ->
        var read = 0
        val endless =
            object : InputStream() {
                override fun read(): Int {
                    read += 1
                    return 7
                }
            }
        assertFalse(store.install(endless))
        org.junit.Assert.assertEquals(fixture.size + 1, read)
        assertNull(store.installed())
    }

    @Test
    fun deadlineCrossedDuringImportCannotPromoteTheModel() = withStore { store, directory, setNow ->
        val source =
            object : ByteArrayInputStream(fixture) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    setNow(expiry)
                    return super.read(buffer, offset, length)
                }
            }
        assertFalse(store.install(source))
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun expiryAlsoRemovesInterruptedImportBytes() = withStore { store, directory, setNow ->
        val abandoned = File(directory, "model-abandoned.pending")
        abandoned.writeBytes(fixture)
        setNow(expiry)
        assertNull(store.installed())
        assertFalse(abandoned.exists())
    }
}
