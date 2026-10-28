package com.monumentogram.dora.transcript

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedModelStoreTest {
    private val fixture = byteArrayOf(1, 2, 3, 4)

    private fun storeFor(directory: File): PinnedModelStore =
        PinnedModelStore(
            directory,
            fixture.size.toLong(),
            MessageDigest.getInstance("SHA-256").digest(fixture),
        )

    private fun withStore(action: (PinnedModelStore, File) -> Unit) {
        val directory = Files.createTempDirectory("model-admission").toFile()
        try {
            action(storeFor(directory), directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun exactModelIsInstalledAndMutationIsDetectedOnNextUse() = withStore { store, _ ->
        assertTrue(store.install(ByteArrayInputStream(fixture)))
        val installed = store.installed()
        assertNotNull(installed)
        installed!!.writeBytes(byteArrayOf(4, 3, 2, 1))
        assertNull(store.installed())
    }

    @Test
    fun failedReplacementPreservesTheAdmittedModelAndRemovesStaging() =
        withStore { store, directory ->
            assertTrue(store.install(ByteArrayInputStream(fixture)))
            assertFalse(store.install(ByteArrayInputStream(byteArrayOf(1, 2, 3))))
            assertFalse(store.install(ByteArrayInputStream(byteArrayOf(4, 3, 2, 1))))
            assertFalse(store.install(ByteArrayInputStream(fixture + 5)))
            assertArrayEquals(fixture, store.installed()!!.readBytes())
            assertTrue(directory.listFiles()!!.none { it.name.endsWith(".pending") })
        }

    @Test
    fun modelRemainsInstalledAfterReopeningItsStore() = withStore { store, directory ->
        assertTrue(store.install(ByteArrayInputStream(fixture)))
        val installed = store.installed()!!
        val reopened = storeFor(directory)
        assertNotNull(reopened.installed())
        assertTrue(installed.exists())
        assertArrayEquals(fixture, reopened.installed()!!.readBytes())
    }

    @Test
    fun oldModelTimestampDoesNotExpireItsVerifiedBytes() = withStore { store, directory ->
        assertTrue(store.install(ByteArrayInputStream(fixture)))
        val installed = store.installed()!!
        assertTrue(installed.setLastModified(0L))
        assertNotNull(store.installed())
        assertNotNull(storeFor(directory).installed())
        assertTrue(installed.exists())
        assertArrayEquals(fixture, installed.readBytes())
    }

    @Test
    fun oversizedStreamIsStoppedAtOneBytePastTheExpectedLength() = withStore { store, _ ->
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
    fun failedImportReadPreservesPreviouslyVerifiedModel() = withStore { store, directory ->
        assertTrue(store.install(ByteArrayInputStream(fixture)))
        val source =
            object : ByteArrayInputStream(fixture) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    throw IOException("Interrupted model import")
                }
            }
        assertFalse(store.install(source))
        assertArrayEquals(fixture, store.installed()!!.readBytes())
        assertTrue(directory.listFiles()!!.none { it.name.endsWith(".pending") })
    }

    @Test
    fun abandonedStagingDoesNotDeleteAdmittedModel() = withStore { store, directory ->
        assertTrue(store.install(ByteArrayInputStream(fixture)))
        val abandoned = File(directory, "model-abandoned.pending")
        abandoned.writeBytes(fixture)
        assertArrayEquals(fixture, store.installed()!!.readBytes())
        assertTrue(abandoned.exists())
    }
}
