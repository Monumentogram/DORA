// Storage failures deliberately discard OS paths and original exception causes.
@file:Suppress("TooGenericExceptionCaught", "SwallowedException")

package com.monumentogram.dora.audio.persistence.keys

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import java.io.File
import java.io.FileDescriptor

internal interface VaultFileIo {
    fun lstat(path: String): StructStat

    fun fstat(fd: FileDescriptor): StructStat

    fun open(path: String, flags: Int, mode: Int): FileDescriptor

    fun mkdir(path: String, mode: Int)

    fun read(fd: FileDescriptor, bytes: ByteArray, offset: Int, count: Int): Int

    fun write(fd: FileDescriptor, bytes: ByteArray, offset: Int, count: Int): Int

    fun fsync(fd: FileDescriptor)

    fun close(fd: FileDescriptor)
}

internal object AndroidVaultFileIo : VaultFileIo {
    override fun lstat(path: String) = Os.lstat(path)

    override fun fstat(fd: FileDescriptor) = Os.fstat(fd)

    override fun open(path: String, flags: Int, mode: Int) = Os.open(path, flags, mode)

    override fun mkdir(path: String, mode: Int) = Os.mkdir(path, mode)

    override fun read(fd: FileDescriptor, bytes: ByteArray, offset: Int, count: Int) =
        Os.read(fd, bytes, offset, count)

    override fun write(fd: FileDescriptor, bytes: ByteArray, offset: Int, count: Int) =
        Os.write(fd, bytes, offset, count)

    override fun fsync(fd: FileDescriptor) = Os.fsync(fd)

    override fun close(fd: FileDescriptor) = Os.close(fd)
}

@Suppress("TooManyFunctions") // One descriptor-checked storage boundary.
internal class AndroidVaultBundleStorage(
    context: Context,
    private val io: VaultFileIo = AndroidVaultFileIo,
) : VaultBundleStorage {
    private val root = context.noBackupFilesDir.absoluteFile
    val vaultDirectory = File(root, "dora-vault-v1")

    init {
        if (context.isDeviceProtectedStorage) throw KeyBoundaryException(KeyFailure.STORAGE_FAILURE)
    }

    override fun reserve(selector: ByteArray) = storage {
        if (selector.size != SELECTOR_BYTES) corrupt()
        directory(root) { parent ->
            // mkdir is the no-overwrite reservation. Even an empty/partial namespace is occupied.
            io.mkdir(vaultDirectory.path, DIRECTORY_MODE)
            verifyDirectory(root, parent)
            io.fsync(parent)
        }
        writeExclusive("selector", selector)
        if (!selector.contentEquals(readExact("selector", SELECTOR_BYTES))) corrupt()
    }

    override fun readSelector(): ByteArray = storage { readExact("selector", SELECTOR_BYTES) }

    override fun persist(bundle: ByteArray) = storage {
        if (bundle.size != VaultEnvelope.BUNDLE_BYTES) corrupt()
        // A valid durable selector must already exist; persist cannot construct namespaces.
        readExact("selector", SELECTOR_BYTES)
        writeExclusive("vault.bundle", bundle)
    }

    override fun readBundle(): ByteArray = storage {
        readExact("vault.bundle", VaultEnvelope.BUNDLE_BYTES)
    }

    private fun writeExclusive(name: String, bytes: ByteArray) = inVault { parent ->
        val file = File(vaultDirectory, name)
        val fd =
            io.open(
                file.path,
                OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or NO_FOLLOW,
                FILE_MODE,
            )
        withDescriptor(fd) {
            verifyRegular(file, fd)
            var offset = 0
            while (offset < bytes.size) {
                val count = io.write(fd, bytes, offset, bytes.size - offset)
                if (count !in 1..(bytes.size - offset)) storageFailure()
                offset += count
            }
            if (io.fstat(fd).st_size != bytes.size.toLong()) corrupt()
            io.fsync(fd)
            verifyRegular(file, fd)
            verifyDirectory(vaultDirectory, parent)
        }
        io.fsync(parent)
    }

    private fun readExact(name: String, size: Int): ByteArray = inVault { parent ->
        val file = File(vaultDirectory, name)
        val before = io.lstat(file.path)
        if (!OsConstants.S_ISREG(before.st_mode) || before.st_size != size.toLong()) corrupt()
        val fd = io.open(file.path, OsConstants.O_RDONLY or NO_FOLLOW, 0)
        withDescriptor(fd) {
            verifyRegular(file, fd)
            if (!sameIdentity(before, io.fstat(fd)) || io.fstat(fd).st_size != size.toLong())
                corrupt()
            val bytes = ByteArray(size)
            try {
                var offset = 0
                while (offset < size) {
                    val count = io.read(fd, bytes, offset, size - offset)
                    if (count !in 1..(size - offset)) corrupt()
                    offset += count
                }
                val probe = ByteArray(1)
                if (io.read(fd, probe, 0, 1) != 0) corrupt()
                verifyRegular(file, fd)
                if (io.fstat(fd).st_size != size.toLong()) corrupt()
                verifyDirectory(vaultDirectory, parent)
                // Exact recovery of a completed but uncertain bootstrap re-establishes durability.
                io.fsync(fd)
                io.fsync(parent)
                bytes
            } catch (error: Exception) {
                bytes.fill(0)
                throw error
            }
        }
    }

    private fun <T> inVault(block: (FileDescriptor) -> T): T =
        directory(root) { rootFd ->
            directory(vaultDirectory) { vaultFd ->
                val result = block(vaultFd)
                verifyDirectory(root, rootFd)
                verifyDirectory(vaultDirectory, vaultFd)
                result
            }
        }

    private fun <T> directory(file: File, block: (FileDescriptor) -> T): T {
        val before = io.lstat(file.path)
        if (!OsConstants.S_ISDIR(before.st_mode)) corrupt()
        val fd = io.open(file.path, OsConstants.O_RDONLY or NO_FOLLOW, 0)
        return withDescriptor(fd) {
            verifyDirectory(file, fd)
            if (!sameIdentity(before, io.fstat(fd))) corrupt()
            block(fd)
        }
    }

    private fun verifyDirectory(file: File, fd: FileDescriptor) {
        val opened = io.fstat(fd)
        val current = io.lstat(file.path)
        if (
            !OsConstants.S_ISDIR(opened.st_mode) ||
                !OsConstants.S_ISDIR(current.st_mode) ||
                !sameIdentity(opened, current)
        )
            corrupt()
    }

    @Suppress("ComplexCondition") // All descriptor properties are mandatory.
    private fun verifyRegular(file: File, fd: FileDescriptor) {
        val opened = io.fstat(fd)
        val current = io.lstat(file.path)
        if (
            !OsConstants.S_ISREG(opened.st_mode) ||
                !OsConstants.S_ISREG(current.st_mode) ||
                opened.st_nlink != 1L ||
                opened.st_mode and OTHER_PERMISSIONS != 0 ||
                !sameIdentity(opened, current)
        )
            corrupt()
    }

    private fun sameIdentity(first: StructStat, second: StructStat) =
        first.st_dev == second.st_dev && first.st_ino == second.st_ino

    private fun <T> withDescriptor(fd: FileDescriptor, block: () -> T): T =
        try {
            block()
        } finally {
            io.close(fd)
        }

    private fun <T> storage(block: () -> T): T =
        try {
            block()
        } catch (error: KeyBoundaryException) {
            throw error
        } catch (error: ErrnoException) {
            throw KeyBoundaryException(
                when (error.errno) {
                    OsConstants.EEXIST -> KeyFailure.NAMESPACE_OCCUPIED
                    OsConstants.ENOENT -> KeyFailure.INCOMPLETE_BOOTSTRAP
                    OsConstants.ELOOP,
                    OsConstants.ENOTDIR -> KeyFailure.CORRUPT_CIPHERTEXT
                    else -> KeyFailure.STORAGE_FAILURE
                }
            )
        } catch (_: Exception) {
            storageFailure()
        }

    private fun corrupt(): Nothing = throw KeyBoundaryException(KeyFailure.CORRUPT_CIPHERTEXT)

    private fun storageFailure(): Nothing = throw KeyBoundaryException(KeyFailure.STORAGE_FAILURE)

    private companion object {
        const val SELECTOR_BYTES = 32
        const val FILE_MODE = 0x180
        const val DIRECTORY_MODE = 0x1c0
        const val OTHER_PERMISSIONS = 0x3f
        val NO_FOLLOW = OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW
    }
}
