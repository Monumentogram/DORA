package com.monumentogram.dora.poc.recovery.storage

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapNamespaceOccupancy
import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapNamespaceState
import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapWriteHandle
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryBootstrapStorage
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.io.File
import java.io.FileDescriptor

/**
 * Public-API Android Os adapter; runtime durability still requires the separately gated preflight.
 */
@Suppress("TooManyFunctions")
internal class AndroidOsRecoveryBootstrapStorage(context: Context) : RecoveryBootstrapStorage {
    private val noBackupRoot = context.noBackupFilesDir.absoluteFile

    override fun inspectNamespaces(runId: RunId): BootstrapNamespaceState {
        val paths = paths(runId)
        validateExistingDirectoryChain(paths.runRoot)
        val confirmationDirectory = namespaceDirectoryOccupancy(paths.confirmationDirectory)
        val (temporary, final) =
            when (confirmationDirectory) {
                BootstrapNamespaceOccupancy.ABSENT ->
                    BootstrapNamespaceOccupancy.ABSENT to BootstrapNamespaceOccupancy.ABSENT
                BootstrapNamespaceOccupancy.OCCUPIED_SAFE ->
                    namespaceLeafOccupancy(paths.temporaryFile) to
                        namespaceLeafOccupancy(paths.finalFile)
                BootstrapNamespaceOccupancy.OCCUPIED_UNSAFE ->
                    BootstrapNamespaceOccupancy.OCCUPIED_UNSAFE to
                        BootstrapNamespaceOccupancy.OCCUPIED_UNSAFE
            }
        return BootstrapNamespaceState(
            keyReferenceNamespace = namespaceDirectoryOccupancy(paths.keyReferenceDirectory),
            temporary = temporary,
            final = final,
        )
    }

    override fun openExclusiveConfirmationTemp(runId: RunId): BootstrapWriteHandle {
        val paths = paths(runId)
        ensureDirectoryChain(paths.confirmationDirectory)
        check(existingType(paths.temporaryFile) == BootstrapPathType.ABSENT) {
            "Recovery key-confirmation temp is occupied"
        }
        check(existingType(paths.finalFile) == BootstrapPathType.ABSENT) {
            "Recovery key-confirmation final is occupied"
        }
        val descriptor =
            Os.open(
                paths.temporaryFile.path,
                OsConstants.O_CREAT or
                    OsConstants.O_EXCL or
                    OsConstants.O_WRONLY or
                    OsConstants.O_CLOEXEC,
                FILE_MODE_OWNER_ONLY,
            )
        return AndroidBootstrapWriteHandle(descriptor)
    }

    override fun write(
        handle: BootstrapWriteHandle,
        bytes: ByteArray,
        offset: Int,
        count: Int,
    ): Int = Os.write(handle.descriptor(), bytes, offset, count)

    override fun fsyncTemp(handle: BootstrapWriteHandle) {
        Os.fsync(handle.descriptor())
    }

    override fun closeTemp(handle: BootstrapWriteHandle) {
        val androidHandle =
            handle as? AndroidBootstrapWriteHandle
                ?: throw IllegalArgumentException("Foreign bootstrap write handle")
        check(!androidHandle.closed) { "Bootstrap write descriptor is already closed" }
        try {
            Os.close(androidHandle.descriptor)
        } finally {
            androidHandle.closed = true
        }
    }

    override fun finalExists(runId: RunId): Boolean {
        val paths = paths(runId)
        validateExistingDirectoryChain(paths.runRoot)
        requireDirectory(paths.confirmationDirectory)
        return existingLeafOccupied(paths.finalFile)
    }

    override fun renameTempToFinal(runId: RunId) {
        val paths = paths(runId)
        validateExistingDirectoryChain(paths.runRoot)
        requireDirectory(paths.confirmationDirectory)
        check(existingType(paths.finalFile) == BootstrapPathType.ABSENT) {
            "Recovery key-confirmation final collision"
        }
        requireRegularLeaf(paths.temporaryFile)
        // Android Os.rename can overwrite. The process-wide same-run writer lease plus this
        // immediately preceding lstat enforce the frozen single-writer contract.
        Os.rename(paths.temporaryFile.path, paths.finalFile.path)
    }

    override fun fsyncConfirmationParent(runId: RunId) {
        val directory = paths(runId).confirmationDirectory
        validateExistingDirectoryChain(paths(runId).runRoot)
        requireDirectory(directory)
        syncDirectory(directory)
    }

    private fun syncDirectory(directory: File) {
        val descriptor =
            Os.open(
                directory.path,
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC,
                0,
            )
        var failure: ErrnoException? = null
        try {
            Os.fsync(descriptor)
        } catch (error: ErrnoException) {
            failure = error
        }
        var closeFailure: ErrnoException? = null
        try {
            Os.close(descriptor)
        } catch (error: ErrnoException) {
            closeFailure = error
        }
        if (failure != null) {
            closeFailure?.let(failure::addSuppressed)
            throw failure
        }
        if (closeFailure != null) {
            throw closeFailure
        }
    }

    private fun paths(runId: RunId): RecoveryBootstrapPaths =
        RecoveryBootstrapPathPolicy.paths(noBackupRoot, runId.toCanonicalString())

    private fun validateExistingDirectoryChain(runRoot: File) {
        val fixedRoot = File(noBackupRoot, "poc-recovery")
        for (directory in
            listOf(
                noBackupRoot,
                fixedRoot,
                File(fixedRoot, "v1"),
                File(fixedRoot, "v1/runs"),
                runRoot,
            )) {
            when (val type = existingType(directory)) {
                BootstrapPathType.ABSENT -> return
                else -> RecoveryBootstrapPathPolicy.requireDirectoryComponent(type, directory.name)
            }
        }
    }

    private fun ensureDirectoryChain(target: File) {
        val fixedRoot = File(noBackupRoot, "poc-recovery")
        requireDirectory(noBackupRoot)
        for (directory in
            listOf(
                fixedRoot,
                File(fixedRoot, "v1"),
                File(fixedRoot, "v1/runs"),
                target.parentFile,
                target,
            )) {
            when (val type = existingType(directory)) {
                BootstrapPathType.ABSENT -> {
                    Os.mkdir(directory.path, DIRECTORY_MODE_OWNER_ONLY)
                    requireDirectory(directory)
                    syncDirectory(requireNotNull(directory.parentFile))
                }
                else -> RecoveryBootstrapPathPolicy.requireDirectoryComponent(type, directory.name)
            }
        }
    }

    private fun existingLeafOccupied(file: File): Boolean = namespaceLeafOccupancy(file).occupied

    private fun namespaceDirectoryOccupancy(directory: File): BootstrapNamespaceOccupancy =
        RecoveryBootstrapPathPolicy.directoryNamespaceOccupancy(existingType(directory))

    private fun namespaceLeafOccupancy(file: File): BootstrapNamespaceOccupancy =
        RecoveryBootstrapPathPolicy.leafNamespaceOccupancy(existingType(file))

    private fun requireRegularLeaf(file: File) {
        if (existingType(file) != BootstrapPathType.REGULAR) {
            throw UnsafeRecoveryBootstrapPathException(
                "Expected regular key-confirmation leaf: ${file.name}"
            )
        }
    }

    private fun requireDirectory(file: File) {
        RecoveryBootstrapPathPolicy.requireDirectoryComponent(existingType(file), file.name)
    }

    private fun existingType(file: File): BootstrapPathType =
        try {
            val mode = Os.lstat(file.path).st_mode
            when {
                OsConstants.S_ISLNK(mode) -> BootstrapPathType.SYMLINK
                OsConstants.S_ISREG(mode) -> BootstrapPathType.REGULAR
                OsConstants.S_ISDIR(mode) -> BootstrapPathType.DIRECTORY
                else -> BootstrapPathType.OTHER
            }
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ENOENT) BootstrapPathType.ABSENT else throw error
        }

    private fun BootstrapWriteHandle.descriptor(): FileDescriptor {
        val androidHandle =
            this as? AndroidBootstrapWriteHandle
                ?: throw IllegalArgumentException("Foreign bootstrap write handle")
        check(!androidHandle.closed) { "Bootstrap write descriptor is closed" }
        return androidHandle.descriptor
    }

    private class AndroidBootstrapWriteHandle(val descriptor: FileDescriptor) :
        BootstrapWriteHandle {
        var closed: Boolean = false
    }

    private companion object {
        const val FILE_MODE_OWNER_ONLY = 0x180 // 0600
        const val DIRECTORY_MODE_OWNER_ONLY = 0x1c0 // 0700
    }
}
