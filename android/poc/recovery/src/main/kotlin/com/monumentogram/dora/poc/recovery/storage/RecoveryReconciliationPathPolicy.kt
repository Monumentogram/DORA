package com.monumentogram.dora.poc.recovery.storage

import com.monumentogram.dora.poc.recovery.contract.RunId
import java.io.File

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
        require(destinationRelativeName.matches(Regex("objects/q-[0-9a-f]{64}\\.bin"))) {
            "Quarantine destination is not canonical"
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
        require(
            value.isNotEmpty() &&
                !value.startsWith('/') &&
                !value.startsWith('\\') &&
                '\\' !in value &&
                value.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
        ) {
            "Recovery source relative name is unsafe"
        }
    }

    private fun requireContained(root: File, child: File) {
        val rootPath = root.toPath().toAbsolutePath().normalize()
        val childPath = child.toPath().toAbsolutePath().normalize()
        require(childPath.startsWith(rootPath) && childPath != rootPath) {
            "Recovery path escapes its run root"
        }
    }
}
