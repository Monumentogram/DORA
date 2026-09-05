package com.monumentogram.dora.poc.recovery.storage

import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapNamespaceOccupancy
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.io.File

internal data class RecoveryBootstrapPaths(
    val runRoot: File,
    val keyReferenceDirectory: File,
    val confirmationDirectory: File,
    val finalFile: File,
    val temporaryFile: File,
) {
    val finalRelativeName: String = FINAL_RELATIVE_NAME
    val temporaryRelativeName: String = TEMPORARY_RELATIVE_NAME

    companion object {
        const val FINAL_RELATIVE_NAME = "key-confirmation/run.kc"
        const val TEMPORARY_RELATIVE_NAME = "$FINAL_RELATIVE_NAME.tmp"
    }
}

internal enum class BootstrapPathType {
    ABSENT,
    DIRECTORY,
    REGULAR,
    SYMLINK,
    OTHER,
}

internal object RecoveryBootstrapPathPolicy {
    fun paths(
        noBackupRoot: File,
        canonicalRunId: String,
    ): RecoveryBootstrapPaths {
        val validated = RunId.fromCanonicalString(canonicalRunId).toCanonicalString()
        val runsRoot = File(noBackupRoot.absoluteFile, "poc-recovery/v1/runs")
        val runRoot = File(runsRoot, validated)
        val confirmationDirectory = File(runRoot, "key-confirmation")
        val finalFile = File(runRoot, RecoveryBootstrapPaths.FINAL_RELATIVE_NAME)
        val temporaryFile = File(runRoot, RecoveryBootstrapPaths.TEMPORARY_RELATIVE_NAME)
        requireContained(runRoot, finalFile)
        requireContained(runRoot, temporaryFile)
        return RecoveryBootstrapPaths(
            runRoot = runRoot,
            keyReferenceDirectory = File(runRoot, "key-envelopes"),
            confirmationDirectory = confirmationDirectory,
            finalFile = finalFile,
            temporaryFile = temporaryFile,
        )
    }

    fun requireContained(
        runRoot: File,
        candidate: File,
    ) {
        val rootPath = runRoot.toPath().toAbsolutePath().normalize()
        val candidatePath = candidate.toPath().toAbsolutePath().normalize()
        require(candidatePath != rootPath && candidatePath.startsWith(rootPath)) {
            "Recovery bootstrap path escapes the canonical run root"
        }
    }

    fun requireDirectoryComponent(
        type: BootstrapPathType,
        name: String,
    ) {
        if (type != BootstrapPathType.DIRECTORY) {
            throw UnsafeRecoveryBootstrapPathException("Non-directory or symlink component: $name")
        }
    }

    fun requireRegularOrAbsentLeaf(
        type: BootstrapPathType,
        name: String,
    ) {
        if (type != BootstrapPathType.REGULAR && type != BootstrapPathType.ABSENT) {
            throw UnsafeRecoveryBootstrapPathException("Unsafe key-confirmation leaf: $name")
        }
    }

    fun directoryNamespaceOccupancy(type: BootstrapPathType): BootstrapNamespaceOccupancy =
        when (type) {
            BootstrapPathType.ABSENT -> BootstrapNamespaceOccupancy.ABSENT
            BootstrapPathType.DIRECTORY -> BootstrapNamespaceOccupancy.OCCUPIED_SAFE
            else -> BootstrapNamespaceOccupancy.OCCUPIED_UNSAFE
        }

    fun leafNamespaceOccupancy(type: BootstrapPathType): BootstrapNamespaceOccupancy =
        when (type) {
            BootstrapPathType.ABSENT -> BootstrapNamespaceOccupancy.ABSENT
            BootstrapPathType.REGULAR -> BootstrapNamespaceOccupancy.OCCUPIED_SAFE
            else -> BootstrapNamespaceOccupancy.OCCUPIED_UNSAFE
        }
}

internal class UnsafeRecoveryBootstrapPathException(message: String) :
    IllegalStateException(message)
