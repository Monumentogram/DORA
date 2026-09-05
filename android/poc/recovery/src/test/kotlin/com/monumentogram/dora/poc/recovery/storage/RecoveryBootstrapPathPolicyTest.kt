package com.monumentogram.dora.poc.recovery.storage

import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapNamespaceOccupancy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryBootstrapPathPolicyTest {
    @Test
    fun `exact confirmation leaves remain beneath canonical run root`() {
        val base = File("safe-no-backup").absoluteFile
        val paths = RecoveryBootstrapPathPolicy.paths(base, "00112233-4455-6677-8899-aabbccddeeff")

        assertEquals("key-confirmation/run.kc", paths.finalRelativeName)
        assertEquals("key-confirmation/run.kc.tmp", paths.temporaryRelativeName)
        assertEquals(paths.finalFile.path + ".tmp", paths.temporaryFile.path)
        RecoveryBootstrapPathPolicy.requireContained(paths.runRoot, paths.finalFile)
        RecoveryBootstrapPathPolicy.requireContained(paths.runRoot, paths.temporaryFile)
    }

    @Test
    fun `escaping or noncanonical inputs are rejected before a path is used`() {
        val base = File("safe-no-backup").absoluteFile
        for (runId in listOf("../escape", "00112233-4455-6677-8899-AABBCCDDEEFF", "")) {
            assertThrows(IllegalArgumentException::class.java) {
                RecoveryBootstrapPathPolicy.paths(base, runId)
            }
        }
        val root = File(base, "poc-recovery/v1/runs/00112233-4455-6677-8899-aabbccddeeff")
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryBootstrapPathPolicy.requireContained(root, File(root, "../escape"))
        }
    }

    @Test
    fun `symlink and nonregular component or leaf types fail closed`() {
        for (type in
            listOf(BootstrapPathType.SYMLINK, BootstrapPathType.REGULAR, BootstrapPathType.OTHER)) {
            assertThrows(UnsafeRecoveryBootstrapPathException::class.java) {
                RecoveryBootstrapPathPolicy.requireDirectoryComponent(type, "component")
            }
        }
        for (type in
            listOf(
                BootstrapPathType.SYMLINK,
                BootstrapPathType.DIRECTORY,
                BootstrapPathType.OTHER,
            )) {
            assertThrows(UnsafeRecoveryBootstrapPathException::class.java) {
                RecoveryBootstrapPathPolicy.requireRegularOrAbsentLeaf(type, "leaf")
            }
        }
        RecoveryBootstrapPathPolicy.requireDirectoryComponent(
            BootstrapPathType.DIRECTORY,
            "component",
        )
        RecoveryBootstrapPathPolicy.requireRegularOrAbsentLeaf(BootstrapPathType.REGULAR, "leaf")
        RecoveryBootstrapPathPolicy.requireRegularOrAbsentLeaf(BootstrapPathType.ABSENT, "leaf")
    }

    @Test
    fun `namespace inspection classifies unsafe objects as occupied without opening them`() {
        assertEquals(
            BootstrapNamespaceOccupancy.ABSENT,
            RecoveryBootstrapPathPolicy.directoryNamespaceOccupancy(BootstrapPathType.ABSENT),
        )
        assertEquals(
            BootstrapNamespaceOccupancy.OCCUPIED_SAFE,
            RecoveryBootstrapPathPolicy.directoryNamespaceOccupancy(BootstrapPathType.DIRECTORY),
        )
        assertEquals(
            BootstrapNamespaceOccupancy.OCCUPIED_UNSAFE,
            RecoveryBootstrapPathPolicy.directoryNamespaceOccupancy(BootstrapPathType.SYMLINK),
        )
        assertEquals(
            BootstrapNamespaceOccupancy.OCCUPIED_SAFE,
            RecoveryBootstrapPathPolicy.leafNamespaceOccupancy(BootstrapPathType.REGULAR),
        )
        for (type in
            listOf(
                BootstrapPathType.SYMLINK,
                BootstrapPathType.DIRECTORY,
                BootstrapPathType.OTHER,
            )) {
            assertEquals(
                BootstrapNamespaceOccupancy.OCCUPIED_UNSAFE,
                RecoveryBootstrapPathPolicy.leafNamespaceOccupancy(type),
            )
        }
    }
}
