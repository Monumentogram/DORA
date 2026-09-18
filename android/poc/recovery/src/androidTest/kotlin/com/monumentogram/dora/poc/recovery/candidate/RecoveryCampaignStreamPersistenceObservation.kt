package com.monumentogram.dora.poc.recovery.candidate

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryJournalDatabase
import com.monumentogram.dora.poc.recovery.journal.RecoveryJournalSchema
import org.json.JSONObject

/** Reads complete run-scoped journal rows after recovery, without changing campaign state. */
internal object RecoveryCampaignStreamPersistenceObservation {
    fun observe(context: Context, run: RunId, candidate: RecoveryCandidate): JSONObject {
        require(candidate == RecoveryCandidate.STREAM)
        val database = AndroidRecoveryJournalDatabase.writable(context)
        require(database.version == RecoveryJournalSchema.VERSION)
        val runs = rows(database, RecoveryJournalSchema.RUN_TABLE, run, candidate)
        val checkpoints =
            rows(database, RecoveryJournalSchema.STREAM_CHECKPOINT_TABLE, run, candidate)
        val outcomes = rows(database, RecoveryJournalSchema.STREAM_OUTCOME_TABLE, run, candidate)
        val ranges = rows(database, RecoveryJournalSchema.STREAM_RANGE_TABLE, run, candidate)
        val state =
            RecoveryCampaignStreamPersistenceSummary.observe(
                runs,
                checkpoints,
                outcomes,
                ranges,
            )
        return JSONObject()
            .put("schema", "DORA_K12_STREAM_PERSISTENCE_V1")
            .put("sealedValidOutcomes", state.sealedValidOutcomes)
            .put("activeRanges", state.activeRanges)
            .put("activeRangeStart", state.activeRangeStart ?: JSONObject.NULL)
            .put("activeRangeEnd", state.activeRangeEnd ?: JSONObject.NULL)
            .put("activeRangeCertainty", state.activeRangeCertainty ?: JSONObject.NULL)
            .put("persistedStateDigest", state.persistedStateDigest)
            .put("runRows", state.runRows)
            .put("checkpointRows", state.checkpointRows)
            .put("totalOutcomeRows", state.totalOutcomeRows)
            .put("rangeRows", state.rangeRows)
    }

    private fun rows(
        database: SQLiteDatabase,
        table: String,
        run: RunId,
        candidate: RecoveryCandidate,
    ): List<RecoveryPersistedRow> =
        database
            .rawQuery(
                "SELECT * FROM $table WHERE run_id=? AND candidate_id=?",
                arrayOf(run.toCanonicalString(), candidate.contractId),
            )
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            RecoveryPersistedRow(
                                table,
                                cursor.columnNames.indices.map { index ->
                                    cursor.cell(cursor.columnNames[index], index)
                                },
                            )
                        )
                    }
                }
            }

    private fun Cursor.cell(column: String, index: Int): RecoveryPersistedCell =
        when (getType(index)) {
            Cursor.FIELD_TYPE_NULL ->
                RecoveryPersistedCell(column, RecoveryPersistedSqlType.NULL, "")
            Cursor.FIELD_TYPE_INTEGER ->
                RecoveryPersistedCell(
                    column,
                    RecoveryPersistedSqlType.INTEGER,
                    getLong(index).toString(),
                )
            Cursor.FIELD_TYPE_FLOAT ->
                RecoveryPersistedCell(
                    column,
                    RecoveryPersistedSqlType.REAL,
                    java.lang.Long.toHexString(
                        java.lang.Double.doubleToRawLongBits(getDouble(index))
                    ),
                )
            Cursor.FIELD_TYPE_STRING ->
                RecoveryPersistedCell(column, RecoveryPersistedSqlType.TEXT, getString(index))
            Cursor.FIELD_TYPE_BLOB ->
                RecoveryPersistedCell(
                    column,
                    RecoveryPersistedSqlType.BLOB,
                    getBlob(index).joinToString("") { "%02x".format(it.toUByte().toInt()) },
                )
            else -> error("Unexpected SQLite value type in persisted-state observation")
        }
}
