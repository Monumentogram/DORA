package com.monumentogram.dora.poc.recovery.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryStreamingPersistenceTest {
    @Test
    fun `migration digest has independent unicode golden`() {
        val row = migrationRow(intent = sha256("intent"))

        assertEquals(
            "b4322d69cd23f10f5cfabb160addd5ff15668082b6d34f4c5f0a662d95b1be01",
            RecoveryStreamingMigration.digest(listOf(row)).toHex(),
        )
    }

    @Test
    fun `migration rows sort by unsigned SQLite blob order`() {
        val low = migrationRow(intent = byteArrayOf(0x00) + ByteArray(31))
        val high = migrationRow(intent = byteArrayOf(0xff.toByte()) + ByteArray(31))

        assertEquals(
            RecoveryStreamingMigration.digest(listOf(low, high)).toHex(),
            RecoveryStreamingMigration.digest(listOf(high, low)).toHex(),
        )
        assertNotEquals(
            RecoveryStreamingMigration.digest(listOf(low)).toHex(),
            RecoveryStreamingMigration.digest(listOf(high)).toHex(),
        )
    }

    @Test
    fun `sqlite text requires its exact canonical UTF eight bytes`() {
        assertEquals(
            "units/тест.ct",
            CanonicalSqliteText.of(
                    "units/тест.ct",
                    "units/тест.ct".toByteArray(Charsets.UTF_8),
                    512,
                )
                .value,
        )
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalSqliteText.of("/", byteArrayOf(0xc0.toByte(), 0xaf.toByte()), 512)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalSqliteText.of("a\u0000b", byteArrayOf(0x61, 0x00, 0x62), 512)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalSqliteText.of("é", "é".toByteArray(Charsets.UTF_8), 1)
        }
    }

    private fun migrationRow(intent: ByteArray) =
        RecoveryQuarantineMigrationRow(
            intentId = intent,
            runId = text("00010203-0405-0607-0809-0a0b0c0d0e0f", 64),
            candidateId = text("REC-MICROFILE-TINK", 64),
            bootstrapBinding = text("ABSENT", 16),
            bootstrapRunId = null,
            bootstrapCandidateId = null,
            artifactRole = text("UNKNOWN_REGULAR", 64),
            observedState = text("FINAL_ORPHAN", 64),
            sourceRelativeName = text("units/тест.ct", 512),
            destinationRelativeName = text("objects/q-test.bin", 512),
            sourceBytes = 0,
            sourceSha256 = sha256(""),
            state = text("PENDING", 16),
        )

    private fun text(value: String, maximum: Int) =
        CanonicalSqliteText.of(value, value.toByteArray(Charsets.UTF_8), maximum)

    private fun sha256(value: String): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
