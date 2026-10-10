@file:Suppress("TooGenericExceptionCaught", "MagicNumber")

package com.monumentogram.dora.audio.persistence

import android.content.Context
import android.content.ContextWrapper
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import java.io.File
import java.io.FileDescriptor
import java.security.MessageDigest

/** Ciphertext-only capture. No database/key/runtime is opened by this boundary. */
internal object ProtectedReadOnlyAcquisition {
    data class Artifact(val bytes: Long, val sha256: String)

    private val proof = Any()

    class Copy
    internal constructor(
        val context: Context,
        private val source: File,
        private val expected: Map<String, Artifact>,
        token: Any,
    ) {
        init {
            check(token === proof)
        }

        fun verifySource() {
            check(inventory(source) == expected)
        }

        fun verifyCopy() {
            check(inventory(File(context.noBackupFilesDir, "dora-vault-v1")) == expected)
        }
    }

    fun inventory(root: File): Map<String, Artifact> = sanitized {
        // Resolve the platform's /data/user/0 <-> /data/data parent alias only.
        // The selected vault leaf and every descendant are still lstat/no-follow checked.
        val selected = File(checkNotNull(root.parentFile).canonicalFile, root.name)
        val result = sortedMapOf<String, Artifact>()
        fun visit(directory: File) {
            val before = Os.lstat(directory.path)
            check(OsConstants.S_ISDIR(before.st_mode))
            check(directory.canonicalFile == directory.absoluteFile)
            checkNotNull(directory.listFiles())
                .sortedBy { it.name }
                .forEach { file ->
                    val stat = Os.lstat(file.path)
                    if (OsConstants.S_ISDIR(stat.st_mode)) visit(file)
                    else {
                        check(result.size < 100_000)
                        val name = file.relativeTo(selected).invariantSeparatorsPath
                        result[name] = transfer(file, null)
                    }
                }
            check(same(before, Os.lstat(directory.path)))
        }
        visit(selected)
        check(result.isNotEmpty())
        result.toMap()
    }

    fun acquire(
        context: Context,
        destination: File,
        expected: Map<String, Artifact>,
        authorize: () -> Unit,
    ): Copy = sanitized {
        check(DiagnosticBuild.ENABLED)
        authorize()
        val source = File(context.noBackupFilesDir.canonicalFile, "dora-vault-v1")
        val pinned = expected.toMap()
        check(inventory(source) == pinned)
        val parent = checkNotNull(destination.parentFile).canonicalFile
        val selectedDestination = File(parent, destination.name)
        check(parent.toPath().startsWith(context.noBackupFilesDir.canonicalFile.toPath()))
        check(!selectedDestination.toPath().startsWith(source.toPath()))
        Os.mkdir(selectedDestination.path, 0x1c0)
        val root = File(selectedDestination, "dora-vault-v1")
        Os.mkdir(root.path, 0x1c0)
        pinned.forEach { (name, expectedArtifact) ->
            authorize()
            check(name.split('/').none { it.isEmpty() || it == "." || it == ".." })
            check('\\' !in name && ':' !in name)
            val target = File(root, name)
            val targetParent = checkNotNull(target.parentFile)
            check(targetParent.mkdirs() || targetParent.isDirectory)
            check(targetParent.canonicalFile == targetParent.absoluteFile)
            check(transfer(File(source, name), target) == expectedArtifact)
        }
        // Persist directory entries after every ciphertext file has reached fsync.
        root.walkBottomUp().filter { it.isDirectory }.forEach(::syncDirectory)
        syncDirectory(selectedDestination)
        syncDirectory(parent)
        val copiedContext =
            object : ContextWrapper(context) {
                override fun getNoBackupFilesDir(): File = selectedDestination

                override fun getApplicationContext(): Context = this
            }
        val copy = Copy(copiedContext, source, pinned, proof)
        copy.verifyCopy()
        copy.verifySource()
        authorize()
        copy
    }

    /** One descriptor is hashed and optionally copied; source never gets a writable handle. */
    private fun transfer(source: File, destination: File?): Artifact {
        val before = Os.lstat(source.path)
        requireRegularSingleLink(before)
        check(source.canonicalFile == source.absoluteFile)
        val fd =
            Os.open(
                source.path,
                OsConstants.O_RDONLY or
                    OsConstants.O_NOFOLLOW or
                    OsConstants.O_NONBLOCK or
                    OsConstants.O_CLOEXEC,
                0,
            )
        return try {
            check(same(before, Os.fstat(fd)))
            val output = destination?.let {
                Os.open(
                    it.path,
                    OsConstants.O_WRONLY or
                        OsConstants.O_CREAT or
                        OsConstants.O_EXCL or
                        OsConstants.O_NOFOLLOW or
                        OsConstants.O_CLOEXEC,
                    0x180,
                )
            }
            try {
                val artifact = copyAndHash(fd, output, before.st_size)
                check(same(before, Os.fstat(fd)))
                check(same(before, Os.lstat(source.path)))
                check(source.canonicalFile == source.absoluteFile)
                output?.let(Os::fsync)
                artifact
            } finally {
                output?.let(Os::close)
            }
        } finally {
            Os.close(fd)
        }
    }

    private fun copyAndHash(input: FileDescriptor, output: FileDescriptor?, size: Long): Artifact {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = Os.read(input, buffer, 0, buffer.size)
            if (count == 0) break
            check(count > 0)
            total = Math.addExact(total, count.toLong())
            check(total <= size)
            digest.update(buffer, 0, count)
            output?.let { writeFully(it, buffer, count) }
        }
        check(total == size)
        return Artifact(total, digest.digest().joinToString("") { "%02x".format(it) })
    }

    private fun writeFully(output: FileDescriptor, buffer: ByteArray, count: Int) {
        var written = 0
        while (written < count) {
            val amount = Os.write(output, buffer, written, count - written)
            check(amount > 0)
            written += amount
        }
    }

    internal fun read(file: File, limit: Int): ByteArray = sanitized {
        val before = Os.lstat(file.path)
        requireRegularSingleLink(before)
        check(before.st_size in 1..limit.toLong() && file.canonicalFile == file.absoluteFile)
        val fd =
            Os.open(
                file.path,
                OsConstants.O_RDONLY or
                    OsConstants.O_NOFOLLOW or
                    OsConstants.O_NONBLOCK or
                    OsConstants.O_CLOEXEC,
                0,
            )
        try {
            check(same(before, Os.fstat(fd)))
            val bytes = ByteArray(before.st_size.toInt())
            var offset = 0
            while (offset < bytes.size) {
                val count = Os.read(fd, bytes, offset, bytes.size - offset)
                check(count > 0)
                offset += count
            }
            check(Os.read(fd, ByteArray(1), 0, 1) == 0 && same(before, Os.fstat(fd)))
            check(same(before, Os.lstat(file.path)) && file.canonicalFile == file.absoluteFile)
            bytes
        } finally {
            Os.close(fd)
        }
    }

    internal fun requireRegularSingleLink(stat: StructStat) {
        check(OsConstants.S_ISREG(stat.st_mode) && stat.st_nlink == 1L)
    }

    private fun syncDirectory(file: File) {
        val fd = Os.open(file.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
        try {
            check(OsConstants.S_ISDIR(Os.fstat(fd).st_mode))
            Os.fsync(fd)
        } finally {
            Os.close(fd)
        }
    }

    private fun same(a: StructStat, b: StructStat) =
        a.st_dev == b.st_dev &&
            a.st_ino == b.st_ino &&
            a.st_mode == b.st_mode &&
            a.st_nlink == b.st_nlink &&
            a.st_size == b.st_size &&
            a.st_mtime == b.st_mtime &&
            a.st_ctime == b.st_ctime

    private inline fun <T> sanitized(block: () -> T): T =
        try {
            block()
        } catch (_: Exception) {
            error("Protected acquisition rejected")
        }
}
