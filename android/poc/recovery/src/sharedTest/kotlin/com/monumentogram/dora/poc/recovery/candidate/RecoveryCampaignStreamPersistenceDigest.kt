package com.monumentogram.dora.poc.recovery.candidate

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

internal enum class RecoveryPersistedSqlType {
    NULL,
    INTEGER,
    REAL,
    TEXT,
    BLOB,
}

internal data class RecoveryPersistedCell(
    val column: String,
    val type: RecoveryPersistedSqlType,
    val value: String,
)

internal data class RecoveryPersistedRow(
    val table: String,
    val cells: List<RecoveryPersistedCell>,
) {
    fun text(column: String): String? =
        cells
            .singleOrNull { it.column == column && it.type == RecoveryPersistedSqlType.TEXT }
            ?.value

    fun integer(column: String): Long? =
        cells
            .singleOrNull { it.column == column && it.type == RecoveryPersistedSqlType.INTEGER }
            ?.value
            ?.toLongOrNull()
}

internal object RecoveryCampaignStreamPersistenceDigest {
    fun sha256(rows: List<RecoveryPersistedRow>): String {
        val encodedRows = rows.map(::encodeRow).sortedBy(::hex)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writePart("DORA_K12_STREAM_PERSISTED_STATE_V1")
            output.writeInt(encodedRows.size)
            encodedRows.forEach { row ->
                output.writeInt(row.size)
                output.write(row)
            }
        }
        return hex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()))
    }

    private fun encodeRow(row: RecoveryPersistedRow): ByteArray {
        require(row.cells.map { it.column }.distinct().size == row.cells.size)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writePart(row.table)
            output.writeInt(row.cells.size)
            row.cells
                .sortedBy { it.column }
                .forEach { cell ->
                    output.writePart(cell.column)
                    output.writePart(cell.type.name)
                    output.writePart(cell.value)
                }
        }
        return bytes.toByteArray()
    }

    private fun DataOutputStream.writePart(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toUByte().toInt()) }
}
