package com.monumentogram.dora.poc.recovery.storage

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.poc.recovery.candidate.CandidateWriteHandle
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateStorage
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.io.File
import java.io.FileDescriptor

/** Android Os publication adapter; device durability remains a gated preflight claim. */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
internal class AndroidOsRecoveryCandidateStorage(context: Context) : RecoveryCandidateStorage {
    private val root = context.noBackupFilesDir.absoluteFile

    override fun openExclusiveTemp(
        runId: RunId,
        temporaryRelativeName: String,
    ): CandidateWriteHandle {
        val paths = paths(runId, temporaryRelativeName)
        require(temporaryRelativeName.endsWith(".tmp")) {
            "Exclusive candidate open requires a temp name"
        }
        ensureDirectoryChain(requireNotNull(paths.artifact.parentFile))
        check(type(paths.artifact) == BootstrapPathType.ABSENT) { "Candidate temp is occupied" }
        val final = File(paths.artifact.path.removeSuffix(".tmp"))
        check(type(final) == BootstrapPathType.ABSENT) { "Candidate final is occupied" }
        return Handle(
            Os.open(
                paths.artifact.path,
                OsConstants.O_CREAT or
                    OsConstants.O_EXCL or
                    OsConstants.O_WRONLY or
                    OsConstants.O_CLOEXEC,
                FILE_MODE_OWNER_ONLY,
            )
        )
    }

    override fun write(
        handle: CandidateWriteHandle,
        bytes: ByteArray,
        offset: Int,
        count: Int,
    ): Int = Os.write(handle.descriptor(), bytes, offset, count)

    override fun fsync(handle: CandidateWriteHandle) {
        Os.fsync(handle.descriptor())
    }

    override fun close(handle: CandidateWriteHandle) {
        val exact = handle as? Handle ?: error("Foreign candidate handle")
        check(!exact.closed) { "Candidate descriptor already closed" }
        try {
            Os.close(exact.descriptor)
        } finally {
            exact.closed = true
        }
    }

    override fun finalExists(runId: RunId, finalRelativeName: String): Boolean {
        val file = paths(runId, finalRelativeName).artifact
        return when (type(file)) {
            BootstrapPathType.ABSENT -> false
            BootstrapPathType.REGULAR -> true
            else ->
                throw UnsafeRecoveryBootstrapPathException("Unsafe candidate final: ${file.name}")
        }
    }

    override fun renameTempToFinal(
        runId: RunId,
        temporaryRelativeName: String,
        finalRelativeName: String,
    ) {
        check(temporaryRelativeName == "$finalRelativeName.tmp") {
            "Candidate temp/final identity mismatch"
        }
        val temporary = paths(runId, temporaryRelativeName).artifact
        val final = paths(runId, finalRelativeName).artifact
        requireDirectory(requireNotNull(temporary.parentFile))
        check(type(temporary) == BootstrapPathType.REGULAR) { "Candidate temp is not regular" }
        check(type(final) == BootstrapPathType.ABSENT) { "Candidate final collision" }
        Os.rename(temporary.path, final.path)
    }

    override fun fsyncParent(runId: RunId, finalRelativeName: String) {
        val directory = requireNotNull(paths(runId, finalRelativeName).artifact.parentFile)
        requireDirectory(directory)
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY or OsConstants.O_CLOEXEC, 0)
        var failure: Throwable? = null
        try {
            Os.fsync(descriptor)
        } catch (error: Throwable) {
            failure = error
        }
        try {
            Os.close(descriptor)
        } catch (error: Throwable) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        if (failure != null) throw failure
    }

    private fun paths(runId: RunId, relativeName: String) =
        RecoveryCandidatePathPolicy.paths(root, runId, relativeName)

    private fun ensureDirectoryChain(target: File) {
        val fixed = File(root, "poc-recovery")
        requireDirectory(root)
        for (directory in
            listOf(fixed, File(fixed, "v1"), File(fixed, "v1/runs"), target.parentFile, target)) {
            when (val existing = type(directory)) {
                BootstrapPathType.ABSENT -> {
                    Os.mkdir(directory.path, DIRECTORY_MODE_OWNER_ONLY)
                    requireDirectory(directory)
                    syncCreatedParent(requireNotNull(directory.parentFile))
                }
                else ->
                    RecoveryBootstrapPathPolicy.requireDirectoryComponent(existing, directory.name)
            }
        }
    }

    private fun syncCreatedParent(directory: File) {
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY or OsConstants.O_CLOEXEC, 0)
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private fun requireDirectory(file: File) =
        RecoveryBootstrapPathPolicy.requireDirectoryComponent(type(file), file.name)

    private fun type(file: File): BootstrapPathType =
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

    private fun CandidateWriteHandle.descriptor(): FileDescriptor {
        val exact = this as? Handle ?: error("Foreign candidate handle")
        check(!exact.closed) { "Candidate descriptor closed" }
        return exact.descriptor
    }

    private class Handle(val descriptor: FileDescriptor) : CandidateWriteHandle {
        var closed = false
    }

    private companion object {
        const val FILE_MODE_OWNER_ONLY = 0x180
        const val DIRECTORY_MODE_OWNER_ONLY = 0x1c0
    }
}
