package com.monumentogram.dora.poc.recovery.storage

import com.monumentogram.dora.poc.recovery.contract.RunId
import java.io.File
import java.nio.file.InvalidPathException

internal data class RecoveryReconciliationPaths(
    val activeRunRoot: File,
    val source: File,
    val quarantineRunRoot: File,
    val objectsRoot: File,
    val destination: File,
)

internal object RecoveryReconciliationPathPolicy {
    fun paths(
        noBackupRoot: File,
        runId: RunId,
        sourceRelativeName: String,
        destinationRelativeName: String,
    ): RecoveryReconciliationPaths {
        requireRelative(sourceRelativeName)
        if (!destinationRelativeName.matches(Regex("objects/q-[0-9a-f]{64}\\.bin"))) {
            throw RecoveryUnsafePathException("Quarantine destination is not canonical")
        }
        val base = File(noBackupRoot, "poc-recovery/v1")
        val active = File(base, "runs/${runId.toCanonicalString()}")
        val quarantine = File(base, "quarantine/${runId.toCanonicalString()}")
        val objects = File(quarantine, "objects")
        val source = File(active, sourceRelativeName)
        val destination = File(quarantine, destinationRelativeName)
        requireContained(active, source)
        requireContained(quarantine, destination)
        return RecoveryReconciliationPaths(active, source, quarantine, objects, destination)
    }

    private fun requireRelative(value: String) {
        if (value.isEmpty() || value.startsWith('/') || value.startsWith('\\')) unsafeRelative()
        if ('\\' in value) unsafeRelative()
        value.split('/').forEach { component ->
            if (component.isEmpty() || component == "." || component == "..") unsafeRelative()
        }
    }

    private fun unsafeRelative(): Nothing =
        throw RecoveryUnsafePathException("Recovery source relative name is unsafe")

    internal fun requireContained(root: File, child: File) {
        try {
            val rootPath = root.toPath().toAbsolutePath().normalize()
            val childPath = child.toPath().toAbsolutePath().normalize()
            if (!(childPath.startsWith(rootPath) && childPath != rootPath)) {
                throw RecoveryUnsafePathException("Recovery path escapes its run root")
            }
        } catch (error: InvalidPathException) {
            throw RecoveryUnsafePathException("Recovery path is invalid").apply {
                addSuppressed(error)
            }
        }
    }
}
