package com.monumentogram.dora.transcript

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant

/** Optional, owner-provided bounded-alpha artifact. Never downloaded or included in the APK. */
internal class LocalAsrModel(context: Context) {
    private val store =
        PinnedModelStore(
            File(context.noBackupFilesDir, "local-asr"),
            SIZE,
            DIGEST,
            EXPIRES,
            Instant::now,
        )

    fun installed(): File? = store.installed()

    fun install(input: InputStream): Boolean = store.install(input)

    companion object {
        const val SIZE = 264_464_607L
        const val SHA256 = "49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f"
        val EXPIRES: Instant = Instant.parse("2026-10-26T00:00:00Z")
        private val DIGEST = SHA256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        fun withinRetention(): Boolean = Instant.now().isBefore(EXPIRES)

        fun verify(file: File): Boolean =
            withinRetention() && PinnedModelStore.matches(file, SIZE, DIGEST) && withinRetention()
    }
}

/**
 * Atomic, size-bounded admission; a rejected replacement cannot destroy a valid installed model.
 */
internal class PinnedModelStore(
    private val directory: File,
    private val expectedBytes: Long,
    private val expectedDigest: ByteArray,
    private val expires: Instant,
    private val now: () -> Instant,
) {
    private val model
        get() = File(directory, "ggml-small-q8_0.bin")

    @Synchronized
    fun installed(): File? {
        if (!now().isBefore(expires)) {
            removeExpiredFiles()
            return null
        }
        return model.takeIf {
            !Files.isSymbolicLink(directory.toPath()) &&
                matches(it, expectedBytes, expectedDigest) &&
                now().isBefore(expires)
        }
    }

    @Synchronized
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    fun install(input: InputStream): Boolean {
        var staging: File? = null
        return try {
            input.use { source ->
                if (!now().isBefore(expires)) {
                    removeExpiredFiles()
                    return false
                }
                if (Files.isSymbolicLink(directory.toPath())) return false
                if (!directory.isDirectory && !directory.mkdirs()) return false
                staging = File.createTempFile("model-", ".pending", directory)
                val temporary = checkNotNull(staging)
                if (!copyVerified(source, temporary)) return false
                if (!now().isBefore(expires)) return false
                Files.move(
                    temporary.toPath(),
                    model.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
                true
            }
        } catch (_: Exception) {
            false
        } finally {
            staging?.delete()
        }
    }

    private fun copyVerified(source: InputStream, destination: File): Boolean =
        FileOutputStream(destination).use { output ->
            val digest =
                consumeBounded(source, expectedBytes) { bytes, count ->
                    output.write(bytes, 0, count)
                }
            output.fd.sync()
            digest != null && MessageDigest.isEqual(expectedDigest, digest)
        }

    private fun removeExpiredFiles() {
        model.delete()
        directory
            .listFiles()
            ?.filter {
                it.isFile && it.name.startsWith("model-") && it.name.endsWith(".pending")
            }
            ?.forEach { it.delete() }
    }

    companion object {
        private const val BUFFER_BYTES = 65_536

        @Suppress("TooGenericExceptionCaught")
        fun matches(file: File, expectedBytes: Long, expectedDigest: ByteArray): Boolean =
            try {
                if (
                    !file.isFile ||
                        Files.isSymbolicLink(file.toPath()) ||
                        file.length() != expectedBytes
                ) {
                    false
                } else {
                    val digest =
                        file.inputStream().use { source ->
                            consumeBounded(source, expectedBytes) { _, _ -> }
                        }
                    digest != null && MessageDigest.isEqual(expectedDigest, digest)
                }
            } catch (_: Exception) {
                false
            }

        private fun consumeBounded(
            source: InputStream,
            expectedBytes: Long,
            consume: (ByteArray, Int) -> Unit,
        ): ByteArray? {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0L
            while (true) {
                val count =
                    source.read(
                        buffer,
                        0,
                        minOf(buffer.size.toLong(), expectedBytes - total + 1).toInt(),
                    )
                if (count == -1) break
                if (count == 0 || total + count > expectedBytes) return null
                total += count
                digest.update(buffer, 0, count)
                consume(buffer, count)
            }
            return if (total == expectedBytes) digest.digest() else null
        }
    }
}
