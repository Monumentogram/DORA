package com.monumentogram.dora.poc.recovery.storage

import com.monumentogram.dora.poc.recovery.contract.RunId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryReconciliationPathPolicyTest {
    private val run = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")

    @Test
    fun `canonical source and digest destination remain in distinct run roots`() {
        val paths =
            RecoveryReconciliationPathPolicy.paths(
                File("build/test-no-backup"),
                run,
                "units/u-0000000000.ct.tmp",
                "objects/q-${"a".repeat(64)}.bin",
            )
        assertEquals("u-0000000000.ct.tmp", paths.source.name)
        assertEquals("q-${"a".repeat(64)}.bin", paths.destination.name)
        assertEquals("objects", paths.objectsRoot.name)
    }

    @Test
    fun `traversal absolute separators and noncanonical destinations fail closed`() {
        listOf("../x", "/x", "a\\b", "a//b", ".").forEach { source ->
            val failure =
                assertThrows(RecoveryUnsafePathException::class.java) {
                    RecoveryReconciliationPathPolicy.paths(
                        File("build/test-no-backup"),
                        run,
                        source,
                        "objects/q-${"a".repeat(64)}.bin",
                    )
                }
            assertEquals(
                com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureCategory.UNSAFE_PARENT,
                failure.category,
            )
        }
        assertThrows(RecoveryUnsafePathException::class.java) {
            RecoveryReconciliationPathPolicy.paths(
                File("build/test-no-backup"),
                run,
                "safe.tmp",
                "objects/q-${"A".repeat(64)}.bin",
            )
        }
    }

    @Test
    fun `invalid platform path and direct containment escape are typed unsafe`() {
        listOf(
                assertThrows(RecoveryUnsafePathException::class.java) {
                    RecoveryReconciliationPathPolicy.paths(
                        File("build/test-no-backup"),
                        run,
                        "C:/outside.bin",
                        "objects/q-${"a".repeat(64)}.bin",
                    )
                },
                assertThrows(RecoveryUnsafePathException::class.java) {
                    RecoveryReconciliationPathPolicy.requireContained(
                        File("build/test-no-backup/root"),
                        File("build/test-no-backup/outside"),
                    )
                },
            )
            .forEach {
                assertEquals(
                    com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureCategory
                        .UNSAFE_PARENT,
                    it.category,
                )
            }
    }
}
