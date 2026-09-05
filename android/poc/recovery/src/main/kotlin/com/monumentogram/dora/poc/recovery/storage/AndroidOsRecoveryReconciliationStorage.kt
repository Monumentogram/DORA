package com.monumentogram.dora.poc.recovery.storage

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.poc.recovery.candidate.QuarantinePathObservation
import com.monumentogram.dora.poc.recovery.candidate.QuarantinePathState
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineStorage
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

@Suppress("TooManyFunctions", "MagicNumber")
internal class AndroidOsRecoveryReconciliationStorage(context: Context) :
    RecoveryQuarantineStorage {
    private val root = context.applicationContext.noBackupFilesDir

    override fun prepare(runId: RunId) {
        val paths =
            RecoveryReconciliationPathPolicy.paths(
                root,
                runId,
                "key-confirmation/run.kc",
                "objects/q-${"0".repeat(64)}.bin",
            )
        val base = File(root, "poc-recovery/v1")
        requireDirectory(base)
        createDirectory(File(base, "quarantine"))
        createDirectory(paths.quarantineRunRoot)
        createDirectory(paths.objectsRoot)
    }

    override fun inspect(row: RecoveryQuarantineIntentRow): QuarantinePathObservation {
        val paths = paths(row)
        validateParents(paths.activeRunRoot, paths.source)
        validateParents(paths.quarantineRunRoot, paths.destination)
        return QuarantinePathObservation(state(paths.source, row), state(paths.destination, row))
    }

    override fun renameNoOverwrite(row: RecoveryQuarantineIntentRow) {
        val paths = paths(row)
        check(state(paths.source, row) == QuarantinePathState.EXACT) { "Quarantine source changed" }
        check(type(paths.destination) == BootstrapPathType.ABSENT) {
            "Quarantine destination occupied"
        }
        Os.rename(paths.source.path, paths.destination.path)
    }

    override fun fsyncSourceParent(row: RecoveryQuarantineIntentRow) =
        syncDirectory(paths(row).source.parentFile!!)

    override fun fsyncDestinationParent(row: RecoveryQuarantineIntentRow) =
        syncDirectory(paths(row).objectsRoot)

    private fun paths(row: RecoveryQuarantineIntentRow) =
        RecoveryReconciliationPathPolicy.paths(
            root,
            row.input.runId,
            row.input.sourceRelativeName,
            row.destinationRelativeName,
        )

    private fun state(file: File, row: RecoveryQuarantineIntentRow): QuarantinePathState =
        when (type(file)) {
            BootstrapPathType.ABSENT -> QuarantinePathState.ABSENT
            BootstrapPathType.REGULAR -> {
                val (bytes, digest) = identity(file)
                if (bytes == row.input.sourceBytes && digest == row.input.sourceSha256)
                    QuarantinePathState.EXACT
                else QuarantinePathState.OCCUPIED
            }
            else -> QuarantinePathState.UNSAFE
        }

    private fun identity(file: File): Pair<ULong, Sha256Value> {
        val descriptor =
            Os.open(
                file.path,
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                0,
            )
        return FileInputStream(descriptor).use { input ->
            check(OsConstants.S_ISREG(Os.fstat(descriptor).st_mode)) {
                "Recovery artifact is not regular"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var count = 0UL
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                check(read > 0) { "Recovery artifact read made no progress" }
                count += read.toULong()
                digest.update(buffer, 0, read)
            }
            count to Sha256Value.fromBytes(digest.digest())
        }
    }

    private fun createDirectory(directory: File) {
        when (type(directory)) {
            BootstrapPathType.ABSENT -> {
                Os.mkdir(directory.path, 0x1c0)
                requireDirectory(directory)
                syncDirectory(directory.parentFile!!)
            }
            BootstrapPathType.DIRECTORY -> Unit
            else -> error("Unsafe Recovery quarantine directory: ${directory.name}")
        }
    }

    private fun validateParents(root: File, leaf: File) {
        requireDirectory(root)
        var current = leaf.parentFile
        while (current != null && current != root) {
            requireDirectory(current)
            current = current.parentFile
        }
        check(current == root) { "Recovery path escaped its root" }
    }

    private fun requireDirectory(file: File) {
        check(type(file) == BootstrapPathType.DIRECTORY) {
            "Unsafe Recovery directory: ${file.name}"
        }
    }

    private fun syncDirectory(directory: File) {
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY or OsConstants.O_CLOEXEC, 0)
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

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
}
