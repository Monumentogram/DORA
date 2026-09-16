package com.monumentogram.dora.poc.recovery.candidate

import android.database.Cursor
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.journal.RecoveryJournalSchema
import com.monumentogram.dora.poc.recovery.journal.StreamingSqliteCell
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

internal class RecoveryCampaignJournalObservation internal constructor(identities: List<String>) {
    private val counts = identities.groupingBy { it }.eachCount()
    val rowCount = identities.size
    val sha256 = framedHash { output ->
        output.writeInt(counts.size)
        counts.toSortedMap().forEach { (identity, count) ->
            output.writeExactString(identity)
            output.writeInt(count)
        }
    }

    fun addedSince(before: RecoveryCampaignJournalObservation): Int =
        counts.entries.sumOf { (identity, count) ->
            maxOf(0, count - (before.counts[identity] ?: 0))
        }
}

/** Raw diagnostic observations never establish a committed or authenticated journal row. */
internal object RecoveryCampaignJournalRows {
    const val REAL_ENCODING = "IEEE754_BINARY64_RAW_BITS_HEX"

    fun checkpoints(
        run: RunId,
        query: (String, String, Array<String>) -> Cursor,
    ): RecoveryCampaignJournalObservation =
        query(
                RecoveryJournalSchema.STREAM_CHECKPOINT_TABLE,
                "run_id=?",
                arrayOf(run.toCanonicalString()),
            )
            .use { cursor ->
                val columns = cursor.columnNames
                identities(
                    buildList { while (cursor.moveToNext()) add(rowIdentity(cursor, columns)) }
                )
            }

    fun identities(values: List<String>) = RecoveryCampaignJournalObservation(values)

    fun encodeReal(value: Double): String =
        value.toRawBits().toULong().toString(HEX_RADIX).padStart(REAL_HEX_LENGTH, '0')

    fun decodeReal(bits: String): Double {
        require(bits.matches(Regex("[0-9a-f]{16}")))
        return Double.fromBits(bits.toULong(HEX_RADIX).toLong())
    }

    private fun rowIdentity(cursor: Cursor, columns: Array<String>): String = framedHash { output ->
        output.writeExactString("RAW_CHECKPOINT_SQLITE_ROW_V1")
        output.writeInt(columns.size)
        columns.forEachIndexed { index, name ->
            output.writeExactString(name)
            writeCell(output, cell(cursor, index))
        }
    }

    private fun cell(cursor: Cursor, column: Int): StreamingSqliteCell =
        when (cursor.getType(column)) {
            Cursor.FIELD_TYPE_NULL -> StreamingSqliteCell.Null
            Cursor.FIELD_TYPE_INTEGER -> StreamingSqliteCell.Integer(cursor.getLong(column))
            Cursor.FIELD_TYPE_FLOAT -> StreamingSqliteCell.Real(cursor.getDouble(column))
            Cursor.FIELD_TYPE_STRING -> StreamingSqliteCell.Text(cursor.getString(column))
            Cursor.FIELD_TYPE_BLOB -> StreamingSqliteCell.Blob(cursor.getBlob(column))
            else -> error("Unsupported SQLite storage class")
        }

    private fun writeCell(output: DataOutputStream, cell: StreamingSqliteCell) {
        when (cell) {
            StreamingSqliteCell.Null -> output.writeInt(Cursor.FIELD_TYPE_NULL)
            is StreamingSqliteCell.Integer -> {
                output.writeInt(Cursor.FIELD_TYPE_INTEGER)
                output.writeLong(cell.value)
            }
            is StreamingSqliteCell.Real -> {
                output.writeInt(Cursor.FIELD_TYPE_FLOAT)
                output.writeLong(cell.value.toRawBits())
            }
            is StreamingSqliteCell.Text -> {
                output.writeInt(Cursor.FIELD_TYPE_STRING)
                output.writeExactString(cell.value)
            }
            is StreamingSqliteCell.Blob -> {
                output.writeInt(Cursor.FIELD_TYPE_BLOB)
                output.writeInt(cell.value.size)
                output.write(cell.value)
            }
        }
    }

    private const val HEX_RADIX = 16
    private const val REAL_HEX_LENGTH = 16
}

private fun framedHash(write: (DataOutputStream) -> Unit): String {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use(write)
    return Sha256Value.calculate(bytes.toByteArray()).toLowercaseHex()
}

private fun DataOutputStream.writeExactString(value: String) {
    writeInt(value.length)
    value.forEach { writeChar(it.code) }
}
