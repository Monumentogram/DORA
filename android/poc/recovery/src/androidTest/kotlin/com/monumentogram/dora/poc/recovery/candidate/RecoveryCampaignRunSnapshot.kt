package com.monumentogram.dora.poc.recovery.candidate

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryJournalDatabase
import com.monumentogram.dora.poc.recovery.journal.RecoveryJournalSchema
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Captures every mutable candidate file and every journal row for this isolated synthetic run.
 * Keystore alias is unchanged between captures; host retains the newer rollback anchor separately.
 */
// Bounded directory and typed row traversal keeps transaction and descriptor lifetimes explicit.
@Suppress("NestedBlockDepth", "LongMethod")
internal object RecoveryCampaignRunSnapshot {
    private val tables =
        listOf(
            RecoveryJournalSchema.RUN_TABLE,
            RecoveryJournalSchema.UNIT_TABLE,
            RecoveryJournalSchema.PUBLICATION_TABLE,
            RecoveryJournalSchema.QUARANTINE_TABLE,
            RecoveryJournalSchema.STREAM_CHECKPOINT_TABLE,
            RecoveryJournalSchema.STREAM_OUTCOME_TABLE,
            RecoveryJournalSchema.STREAM_RANGE_TABLE,
        )

    fun capture(context: Context, value: KeyConfirmationValue, destination: File) {
        check(!destination.exists() && destination.mkdirs())
        val root = root(context, value)
        if (root.exists()) copyTree(root, File(destination, "run"))
        val quarantine =
            File(
                context.noBackupFilesDir,
                "poc-recovery/v1/quarantine/${value.runId.toCanonicalString()}",
            )
        if (quarantine.exists()) copyTree(quarantine, File(destination, "quarantine"))
        val rows = JSONObject()
        val database = AndroidRecoveryJournalDatabase.writable(context)
        database.beginTransactionNonExclusive()
        try {
            for (table in tables) {
                val records = JSONArray()
                database
                    .query(
                        table,
                        null,
                        "run_id=?",
                        arrayOf(value.runId.toCanonicalString()),
                        null,
                        null,
                        null,
                    )
                    .use { cursor ->
                        while (cursor.moveToNext()) {
                            val record = JSONObject()
                            cursor.columnNames.forEachIndexed { i, column ->
                                val cell = JSONObject().put("type", cursor.getType(i))
                                when (cursor.getType(i)) {
                                    Cursor.FIELD_TYPE_NULL -> cell.put("value", JSONObject.NULL)
                                    Cursor.FIELD_TYPE_INTEGER ->
                                        cell.put("value", cursor.getLong(i))
                                    Cursor.FIELD_TYPE_FLOAT ->
                                        cell
                                            .put(
                                                "encoding",
                                                RecoveryCampaignJournalRows.REAL_ENCODING,
                                            )
                                            .put(
                                                "value",
                                                RecoveryCampaignJournalRows.encodeReal(
                                                    cursor.getDouble(i)
                                                ),
                                            )
                                    Cursor.FIELD_TYPE_STRING ->
                                        cell.put("value", cursor.getString(i))
                                    Cursor.FIELD_TYPE_BLOB ->
                                        cell.put(
                                            "value",
                                            Base64.encodeToString(
                                                cursor.getBlob(i),
                                                Base64.NO_WRAP,
                                            ),
                                        )
                                    else -> error("Unexpected Recovery journal cell type")
                                }
                                record.put(column, cell)
                            }
                            records.put(record)
                        }
                    }
                rows.put(table, records)
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        FileOutputStream(File(destination, "rows.json")).use {
            it.write(rows.toString().toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
    }

    fun restore(context: Context, value: KeyConfirmationValue, source: File) {
        val root = root(context, value)
        val rows = JSONObject(File(source, "rows.json").readText(Charsets.UTF_8))
        removeTree(root, root)
        copyTree(File(source, "run"), root)
        val database = AndroidRecoveryJournalDatabase.writable(context)
        database.beginTransactionNonExclusive()
        try {
            for (table in tables.asReversed()) database.delete(
                table,
                "run_id=?",
                arrayOf(value.runId.toCanonicalString()),
            )
            for (table in tables) {
                val records = rows.getJSONArray(table)
                for (i in 0 until records.length()) {
                    val record = records.getJSONObject(i)
                    val values = ContentValues()
                    record.keys().forEach { column ->
                        val cell = record.getJSONObject(column)
                        when (cell.getInt("type")) {
                            Cursor.FIELD_TYPE_NULL -> values.putNull(column)
                            Cursor.FIELD_TYPE_INTEGER -> values.put(column, cell.getLong("value"))
                            Cursor.FIELD_TYPE_FLOAT -> {
                                check(
                                    cell.getString("encoding") ==
                                        RecoveryCampaignJournalRows.REAL_ENCODING
                                )
                                values.put(
                                    column,
                                    RecoveryCampaignJournalRows.decodeReal(cell.getString("value")),
                                )
                            }
                            Cursor.FIELD_TYPE_STRING -> values.put(column, cell.getString("value"))
                            Cursor.FIELD_TYPE_BLOB ->
                                values.put(
                                    column,
                                    Base64.decode(cell.getString("value"), Base64.NO_WRAP),
                                )
                            else -> error("Snapshot has unsupported field type")
                        }
                    }
                    check(values.getAsString("run_id") == value.runId.toCanonicalString())
                    database.insertOrThrow(table, null, values)
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun root(context: Context, value: KeyConfirmationValue) =
        File(context.noBackupFilesDir, "poc-recovery/v1/runs/${value.runId.toCanonicalString()}")

    private fun copyTree(source: File, target: File) {
        val stat = Os.lstat(source.path)
        if (OsConstants.S_ISDIR(stat.st_mode)) {
            check(target.mkdirs())
            requireNotNull(source.listFiles()).forEach { copyTree(it, File(target, it.name)) }
        } else if (OsConstants.S_ISLNK(stat.st_mode)) {
            val metadata =
                JSONObject()
                    .put("kind", "SYMLINK_TARGET_TEXT")
                    .put("sourceName", source.name)
                    .put("target", Os.readlink(source.path))
            FileOutputStream(File(target.path + ".symlink.json")).use {
                it.write(metadata.toString().toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
        } else {
            check(OsConstants.S_ISREG(stat.st_mode) && stat.st_size in 0..116000000)
            FileOutputStream(target).use { output ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                    }
                }
                output.fd.sync()
            }
            check(target.length() == stat.st_size)
        }
    }

    private fun removeTree(root: File, target: File) {
        check(target.toPath().normalize().startsWith(root.toPath().normalize()))
        val stat = Os.lstat(target.path)
        if (OsConstants.S_ISDIR(stat.st_mode))
            requireNotNull(target.listFiles()).forEach { removeTree(root, it) }
        else check(OsConstants.S_ISREG(stat.st_mode))
        Os.remove(target.path)
    }
}
