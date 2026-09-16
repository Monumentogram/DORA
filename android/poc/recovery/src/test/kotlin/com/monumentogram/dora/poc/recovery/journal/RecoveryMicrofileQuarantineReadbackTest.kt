package com.monumentogram.dora.poc.recovery.journal

import android.database.Cursor
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryMicrofileQuarantineReadbackTest {
    @Test
    fun `one typed completed referenced row retains exact identity`() {
        for (state in listOf("REFERENCED_REJECTED", "REFERENCED_DEPENDENT")) {
            val cells = cells().apply { this[7] = state }
            val result = RecoveryMicrofileQuarantineReadback.read(cursor(listOf(cells)))
            assertNotNull("Exact completed readback unavailable", result)
            assertEquals(state, requireNotNull(result).recordedObservedState.name)
            assertEquals(RecoveryQuarantineIntent.calculate(input()), result.intentId)
        }
        assertNull(RecoveryMicrofileQuarantineReadback.read(cursor(emptyList())))
    }

    @Test
    fun `typed readback rejects text numbers real numbers text hash null and malformed identities`() {
        val mutations =
            listOf(
                10 to "3",
                10 to 3.0,
                10 to -1L,
                11 to "s".repeat(32),
                11 to ByteArray(31),
                0 to ByteArray(31),
                0 to ByteArray(32),
                4 to null,
                5 to null,
                1 to "malformed",
                4 to "10112233-4455-6677-8899-aabbccddeeff",
                5 to "REC-STREAM-TINK",
                3 to "ABSENT",
                7 to "FINAL_ORPHAN",
                7 to "UNKNOWN",
                12 to "PENDING",
                9 to "objects/q-${"0".repeat(64)}.bin",
            )
        for ((index, value) in mutations) {
            val cells = cells().apply { this[index] = value }
            assertThrows(
                "Invalid column $index value=$value",
                RecoveryMicrofileQuarantineReadbackException::class.java,
            ) {
                RecoveryMicrofileQuarantineReadback.read(cursor(listOf(cells)))
            }
        }
    }

    @Test
    fun `two named versions are rejected even when each version has valid identity`() {
        val first = cells()
        val otherInput = input().copy(sourceBytes = 4UL)
        val second =
            cells().apply {
                this[0] = RecoveryQuarantineIntent.calculate(otherInput).toByteArray()
                this[9] = RecoveryQuarantineIntent.destination(otherInput)
                this[10] = 4L
            }
        assertThrows(RecoveryMicrofileQuarantineReadbackException::class.java) {
            RecoveryMicrofileQuarantineReadback.read(cursor(listOf(first, second)))
        }
    }

    private fun input() =
        RecoveryQuarantineIntentInput(
            RecoveryCandidate.MICROFILE,
            RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff"),
            "units/u-0000000001.ct",
            RecoveryQuarantineArtifactRole.MICROFILE_CIPHERTEXT,
            3UL,
            Sha256Value.calculate(byteArrayOf(1, 2, 3)),
        )

    private fun cells(): MutableList<Any?> {
        val input = input()
        return mutableListOf(
            RecoveryQuarantineIntent.calculate(input).toByteArray(),
            input.runId.toCanonicalString(),
            input.candidate.contractId,
            "PRESENT",
            input.runId.toCanonicalString(),
            input.candidate.contractId,
            input.artifactRole.name,
            "REFERENCED_REJECTED",
            input.sourceRelativeName,
            RecoveryQuarantineIntent.destination(input),
            3L,
            input.sourceSha256.toByteArray(),
            "COMPLETED",
        )
    }

    @Suppress("CyclomaticComplexMethod")
    private fun cursor(rows: List<List<Any?>>): Cursor {
        var position = -1
        return Proxy.newProxyInstance(
            Cursor::class.java.classLoader,
            arrayOf(Cursor::class.java),
        ) { _, method, args ->
            fun value(): Any? {
                check(position in rows.indices)
                return rows[position][args!![0] as Int]
            }
            when (method.name) {
                "moveToFirst" -> {
                    position = 0
                    rows.isNotEmpty()
                }
                "moveToNext" -> {
                    position++
                    position in rows.indices
                }
                "getColumnCount" -> 13
                "getCount" -> rows.size
                "isNull" -> value() == null
                "getType" ->
                    when (value()) {
                        null -> Cursor.FIELD_TYPE_NULL
                        is ByteArray -> Cursor.FIELD_TYPE_BLOB
                        is Long -> Cursor.FIELD_TYPE_INTEGER
                        is Double -> Cursor.FIELD_TYPE_FLOAT
                        is String -> Cursor.FIELD_TYPE_STRING
                        else -> error("Unexpected fixture storage class")
                    }
                "getString" -> value() as String
                "getLong" -> value() as Long
                "getBlob" -> (value() as ByteArray).copyOf()
                "close" -> Unit
                else -> error("Unexpected cursor call ${method.name}")
            }
        } as Cursor
    }
}
