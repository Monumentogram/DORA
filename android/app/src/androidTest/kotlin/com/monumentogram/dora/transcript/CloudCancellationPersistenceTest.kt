package com.monumentogram.dora.transcript

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCancellationPersistenceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory =
        File(base.cacheDir, "cancellation-test-${UUID.randomUUID()}").apply { mkdirs() }
    private val context =
        object : ContextWrapper(base) {
            override fun getNoBackupFilesDir() = directory
        }

    @Test
    fun encryptedTombstonesSurviveReopenAndConcurrentWriters() {
        val first = EncryptedCloudCancellations(context)
        val second = EncryptedCloudCancellations(context)
        val ids = List(4) { UUID.randomUUID().toString() }
        val executor = Executors.newFixedThreadPool(2)
        try {
            ids.mapIndexed { index, id ->
                    executor.submit {
                        (if (index % 2 == 0) first else second).record(id, DESTINATION)
                    }
                }
                .forEach { it.get() }
        } finally {
            executor.shutdown()
        }
        val reopened = EncryptedCloudCancellations(context)
        ids.forEach { assertTrue(reopened.blocked(it, DESTINATION)) }
        val raw =
            directory.resolve("cloud-cancellations-v1").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(raw.contains(DESTINATION))
        ids.forEach { assertFalse(raw.contains(it)) }
    }

    @Test
    fun corruptCiphertextIsNotTreatedAsAnEmptyOutbox() {
        val id = UUID.randomUUID().toString()
        EncryptedCloudCancellations(context).record(id, DESTINATION)
        val file = directory.resolve("cloud-cancellations-v1")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertThrows(Exception::class.java) {
            EncryptedCloudCancellations(context).blocked(id, DESTINATION)
        }
    }

    companion object {
        private const val DESTINATION = "https://cancellation-fixture.invalid"
    }
}
