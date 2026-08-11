@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "LargeClass",
    "MagicNumber",
    "NestedBlockDepth",
    "TooManyFunctions",
)

package com.monumentogram.dora.poc.search

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.os.SystemClock
import com.monumentogram.dora.poc.search.data.SyntheticDatasetGenerator
import java.io.File
import java.security.MessageDigest

class StorageUpdateGateV02Harness(private val context: Context) {
    fun run(config: GateV02RunConfig): GateV02BuildCheckpoint {
        config.validate()
        val observations = mutableMapOf<GateV02DatabaseKind, GateV02DatabaseObservation>()
        config.pairOrder.forEach { kind -> observations[kind] = runDatabase(config, kind) }
        return GateV02BuildCheckpoint(
            config = config,
            control = requireNotNull(observations[GateV02DatabaseKind.CONTROL]),
            indexed = requireNotNull(observations[GateV02DatabaseKind.INDEXED]),
        )
    }

    private fun runDatabase(
        config: GateV02RunConfig,
        kind: GateV02DatabaseKind,
    ): GateV02DatabaseObservation {
        val databaseName =
            "poc-search-gate-v02-${config.profileId.lowercase()}-" +
                "${config.freshBuildOrdinal}-${kind.name.lowercase()}.db"
        deleteAndVerify(databaseName)
        var database: SQLiteDatabase? = null
        var observation: GateV02DatabaseObservation? = null
        var deleted = false
        try {
            database = open(databaseName)
            createCanonicalSchema(database)
            populateCanonicalFixture(database, config)
            if (kind == GateV02DatabaseKind.INDEXED) {
                createAndPopulateIndex(database)
            }
            val storage = normalizeAndClose(databaseName, database, config, kind)
            database = null

            database = open(databaseName)
            val operations = runOperationCampaign(database, config, kind)
            val sqliteVersion = scalarString(database, "SELECT sqlite_version()")
            val compileOptions = stringColumn(database, "PRAGMA compile_options")
            val finalIntegrity = scalarString(database, "PRAGMA integrity_check")
            val finalConversationCount = scalarLong(database, "SELECT COUNT(*) FROM conversations")
            val finalSegmentCount = scalarLong(database, "SELECT COUNT(*) FROM transcript_segments")
            val finalFtsCount =
                if (kind == GateV02DatabaseKind.INDEXED) {
                    scalarLong(database, "SELECT COUNT(*) FROM transcript_segments_fts")
                } else {
                    null
                }
            val finalMissingCanonicalMappings =
                if (kind == GateV02DatabaseKind.INDEXED) missingCanonicalMappings(database)
                else null
            val finalMissingIndexRows =
                if (kind == GateV02DatabaseKind.INDEXED) missingIndexRows(database) else null
            database.close()
            database = null
            deleted = deleteAndVerify(databaseName)
            observation =
                GateV02DatabaseObservation(
                    kind = kind,
                    storage = storage,
                    operations = operations,
                    sqliteVersion = sqliteVersion,
                    compileOptions = compileOptions,
                    finalIntegrityCheck = finalIntegrity,
                    finalConversationCount = finalConversationCount,
                    finalTranscriptSegmentCount = finalSegmentCount,
                    finalFtsRowCount = finalFtsCount,
                    finalMissingCanonicalMappings = finalMissingCanonicalMappings,
                    finalMissingIndexRows = finalMissingIndexRows,
                    deletedAfterRun = deleted,
                )
        } finally {
            database?.close()
            if (!deleted) deleted = deleteAndVerify(databaseName)
        }
        return requireNotNull(observation).copy(deletedAfterRun = deleted)
    }

    private fun open(databaseName: String): SQLiteDatabase {
        val path = context.getDatabasePath(databaseName)
        check(path.parentFile?.let { it.isDirectory || it.mkdirs() } == true) {
            "Unable to create the app-private database directory"
        }
        return SQLiteDatabase.openOrCreateDatabase(path, null).also {
            val journalMode = scalarString(it, "PRAGMA journal_mode=WAL")
            check(journalMode.equals("wal", ignoreCase = true))
            it.execSQL("PRAGMA synchronous=FULL")
            check(scalarLong(it, "PRAGMA wal_autocheckpoint=1000") == 1_000L)
            it.execSQL("PRAGMA foreign_keys=ON")
            it.execSQL("PRAGMA temp_store=DEFAULT")
            check(scalarLong(it, "PRAGMA foreign_keys") == 1L)
            check(scalarLong(it, "PRAGMA wal_autocheckpoint") == 1_000L)
        }
    }

    private fun createCanonicalSchema(database: SQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE conversations (" +
                "conversation_id INTEGER NOT NULL PRIMARY KEY, " +
                "title TEXT NOT NULL, started_at_ms INTEGER NOT NULL, " +
                "source_type TEXT NOT NULL, participant_label TEXT NOT NULL)"
        )
        database.execSQL(
            "CREATE INDEX index_conversations_started_at_ms " + "ON conversations(started_at_ms)"
        )
        database.execSQL(
            "CREATE INDEX index_conversations_source_type ON conversations(source_type)"
        )
        database.execSQL(
            "CREATE TABLE transcript_segments (" +
                "segment_id INTEGER NOT NULL PRIMARY KEY, " +
                "conversation_id INTEGER NOT NULL, sequence INTEGER NOT NULL, " +
                "start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL, " +
                "language TEXT NOT NULL, text TEXT NOT NULL, " +
                "FOREIGN KEY(conversation_id) REFERENCES conversations(conversation_id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        database.execSQL(
            "CREATE INDEX index_transcript_segments_conversation_id " +
                "ON transcript_segments(conversation_id)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX index_transcript_segments_conversation_id_sequence " +
                "ON transcript_segments(conversation_id, sequence)"
        )
        database.execSQL(
            "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
        )
        database.execSQL(
            "INSERT INTO room_master_table(id, identity_hash) VALUES(42, ?)",
            arrayOf(ROOM_SCHEMA_IDENTITY),
        )
    }

    private fun populateCanonicalFixture(database: SQLiteDatabase, config: GateV02RunConfig) {
        insertConversations(database, config.conversationCount)
        insertSegments(database, config.transcriptSegmentCount)
    }

    private fun insertConversations(database: SQLiteDatabase, count: Int) {
        val statement =
            database.compileStatement(
                "INSERT INTO conversations(" +
                    "conversation_id,title,started_at_ms,source_type,participant_label" +
                    ") VALUES(?,?,?,?,?)"
            )
        try {
            var first = 1
            while (first <= count) {
                val last = minOf(first + CONVERSATION_BATCH_SIZE - 1, count)
                database.beginTransaction()
                try {
                    for (id in first..last) {
                        val value = SyntheticDatasetGenerator.conversation(id.toLong())
                        statement.clearBindings()
                        statement.bindLong(1, value.conversationId)
                        statement.bindString(2, value.title)
                        statement.bindLong(3, value.startedAtMs)
                        statement.bindString(4, value.sourceType)
                        statement.bindString(5, value.participantLabel)
                        statement.executeInsert()
                    }
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
                first = last + 1
            }
        } finally {
            statement.close()
        }
    }

    private fun insertSegments(database: SQLiteDatabase, count: Int) {
        val statement =
            database.compileStatement(
                "INSERT INTO transcript_segments(" +
                    "segment_id,conversation_id,sequence,start_ms,end_ms,language,text" +
                    ") VALUES(?,?,?,?,?,?,?)"
            )
        try {
            var first = 1L
            while (first <= count.toLong()) {
                val last = minOf(first + SEGMENT_BATCH_SIZE - 1L, count.toLong())
                database.beginTransaction()
                try {
                    for (id in first..last) {
                        bindSegment(statement, SyntheticDatasetGenerator.segment(id))
                        statement.executeInsert()
                    }
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
                first = last + 1L
            }
        } finally {
            statement.close()
        }
    }

    private fun bindSegment(
        statement: SQLiteStatement,
        value: com.monumentogram.dora.poc.search.db.TranscriptSegmentEntity,
    ) {
        statement.clearBindings()
        statement.bindLong(1, value.segmentId)
        statement.bindLong(2, value.conversationId)
        statement.bindLong(3, value.sequence.toLong())
        statement.bindLong(4, value.startMs)
        statement.bindLong(5, value.endMs)
        statement.bindString(6, value.language)
        statement.bindString(7, value.text)
    }

    private fun createAndPopulateIndex(database: SQLiteDatabase) {
        database.execSQL(
            "CREATE VIRTUAL TABLE transcript_segments_fts " +
                "USING FTS4(text TEXT NOT NULL, tokenize=unicode61 `remove_diacritics=0`)"
        )
        database.execSQL(
            "INSERT INTO transcript_segments_fts(rowid, text) " +
                "SELECT segment_id, text FROM transcript_segments ORDER BY segment_id"
        )
    }

    private fun normalizeAndClose(
        databaseName: String,
        database: SQLiteDatabase,
        config: GateV02RunConfig,
        kind: GateV02DatabaseKind,
    ): GateV02NormalizedStorage {
        val canonicalDigest = canonicalLogicalDigest(database)
        val firstCheckpointBusy = checkpointBusy(database)
        database.execSQL("VACUUM")
        val secondCheckpointBusy = checkpointBusy(database)
        val pageCount = scalarLong(database, "PRAGMA page_count")
        val pageSize = scalarLong(database, "PRAGMA page_size")
        val integrity = scalarString(database, "PRAGMA integrity_check")
        val conversationCount = scalarLong(database, "SELECT COUNT(*) FROM conversations")
        val segmentCount = scalarLong(database, "SELECT COUNT(*) FROM transcript_segments")
        val ftsCount =
            if (kind == GateV02DatabaseKind.INDEXED) {
                scalarLong(database, "SELECT COUNT(*) FROM transcript_segments_fts")
            } else {
                null
            }
        val missingCanonical =
            if (kind == GateV02DatabaseKind.INDEXED) missingCanonicalMappings(database) else null
        val missingIndex =
            if (kind == GateV02DatabaseKind.INDEXED) missingIndexRows(database) else null
        check(conversationCount == config.conversationCount.toLong())
        check(segmentCount == config.transcriptSegmentCount.toLong())
        database.close()

        val mainFile = context.getDatabasePath(databaseName)
        val walFile = File(mainFile.path + "-wal")
        val shmFile = File(mainFile.path + "-shm")
        val mainBytes = mainFile.lengthOrZero()
        val walBytes = walFile.lengthOrZero()
        val shmBytes = shmFile.lengthOrZero()
        return GateV02NormalizedStorage(
            mainDatabaseBytes = mainBytes,
            pageCount = pageCount,
            pageSizeBytes = pageSize,
            walBytesAfterClose = walBytes,
            shmBytesAfterClose = shmBytes,
            integrityCheck = integrity,
            conversationCount = conversationCount,
            transcriptSegmentCount = segmentCount,
            ftsRowCount = ftsCount,
            canonicalLogicalSha256 = canonicalDigest,
            missingCanonicalMappings = missingCanonical,
            missingIndexRows = missingIndex,
            firstCheckpointBusy = firstCheckpointBusy,
            secondCheckpointBusy = secondCheckpointBusy,
            mainFileMatchesPageGeometry = mainBytes == pageCount * pageSize,
            transientFilesCleared = walBytes == 0L && shmBytes == 0L,
            freeStorageBytesAfterNormalization = mainFile.parentFile?.usableSpace ?: 0L,
        )
    }

    private fun runOperationCampaign(
        database: SQLiteDatabase,
        config: GateV02RunConfig,
        kind: GateV02DatabaseKind,
    ): List<GateV02OperationSamples> =
        GateV02OperationClass.entries.map { operationClass ->
            val total = config.warmupsPerClass + config.measuredOperationsPerClass
            val samples =
                (0 until total).map { ordinal ->
                    runOperation(database, config, kind, operationClass, ordinal)
                }
            GateV02OperationSamples(
                operationClass = operationClass,
                warmupSamples = samples.take(config.warmupsPerClass),
                measuredSamples = samples.drop(config.warmupsPerClass),
            )
        }

    private fun runOperation(
        database: SQLiteDatabase,
        config: GateV02RunConfig,
        kind: GateV02DatabaseKind,
        operationClass: GateV02OperationClass,
        ordinal: Int,
    ): GateV02OperationSample {
        val operationCount = config.warmupsPerClass + config.measuredOperationsPerClass
        val target = operationTarget(database, config, operationClass, operationCount, ordinal)
        var transactionSucceeded = false
        var crashed = false
        val started = SystemClock.elapsedRealtimeNanos()
        try {
            database.beginTransactionNonExclusive()
            mutate(database, kind, operationClass, target)
            database.setTransactionSuccessful()
            transactionSucceeded = true
        } catch (_: Throwable) {
            crashed = true
        } finally {
            if (database.inTransaction()) {
                try {
                    database.endTransaction()
                } catch (_: Throwable) {
                    crashed = true
                    transactionSucceeded = false
                }
            }
        }
        val commitNanos = SystemClock.elapsedRealtimeNanos() - started
        val result =
            if (!transactionSucceeded || crashed) {
                GateV02OperationSample(commitNanos, null, false, false, true)
            } else if (kind == GateV02DatabaseKind.CONTROL) {
                val correct = runCatching {
                    canonicalPostcondition(database, operationClass, target)
                }
                GateV02OperationSample(
                    commitNanos = commitNanos,
                    visibilityNanos = null,
                    correctnessPassed = correct.getOrDefault(false),
                    staleSuccessfulResponse = false,
                    crashed = correct.isFailure,
                )
            } else {
                val visibilityStarted = SystemClock.elapsedRealtimeNanos()
                var correct = false
                var staleSuccessfulResponse = false
                try {
                    correct = indexedPostcondition(database, operationClass, target)
                    staleSuccessfulResponse = !correct
                    while (
                        !correct &&
                            elapsedMillis(visibilityStarted) <
                                StorageUpdateGateV02Contract.VISIBILITY_DEADLINE_MS
                    ) {
                        SystemClock.sleep(StorageUpdateGateV02Contract.VISIBILITY_POLL_INTERVAL_MS)
                        correct = indexedPostcondition(database, operationClass, target)
                    }
                } catch (_: Throwable) {
                    crashed = true
                }
                val visibilityNanos =
                    if (correct) SystemClock.elapsedRealtimeNanos() - visibilityStarted else null
                val canonicalCorrect =
                    runCatching { canonicalPostcondition(database, operationClass, target) }
                        .getOrDefault(false)
                GateV02OperationSample(
                    commitNanos = commitNanos,
                    visibilityNanos = visibilityNanos,
                    correctnessPassed = correct && canonicalCorrect,
                    staleSuccessfulResponse = staleSuccessfulResponse,
                    crashed = crashed,
                )
            }
        return result
    }

    private fun operationTarget(
        database: SQLiteDatabase,
        config: GateV02RunConfig,
        operationClass: GateV02OperationClass,
        operationCount: Int,
        ordinal: Int,
    ): OperationTarget {
        val conversationId =
            when (operationClass) {
                GateV02OperationClass.ADD_CONVERSATION_100 ->
                    config.conversationCount.toLong() + ordinal + 1L
                GateV02OperationClass.UPDATE_SEGMENT_TEXT_1 -> ordinal + 1L
                GateV02OperationClass.UPDATE_CONVERSATION_FILTER_1 -> operationCount + ordinal + 1L
                GateV02OperationClass.DELETE_SEGMENT_1 -> 2L * operationCount + ordinal + 1L
                GateV02OperationClass.DELETE_CONVERSATION_100 -> 3L * operationCount + ordinal + 1L
            }
        val segmentId =
            when (operationClass) {
                GateV02OperationClass.ADD_CONVERSATION_100 ->
                    config.transcriptSegmentCount.toLong() + ordinal * 100L + 1L
                GateV02OperationClass.UPDATE_SEGMENT_TEXT_1 ->
                    SyntheticDatasetGenerator.segmentId(conversationId, 0)
                GateV02OperationClass.DELETE_SEGMENT_1 ->
                    firstSyntheticSegmentId(database, conversationId)
                else -> 0L
            }
        return OperationTarget(
            conversationId = conversationId,
            segmentId = segmentId,
            marker = "gatev02marker${operationClass.ordinal}x$ordinal",
            sourceType = "GATEV02_FILTER_$ordinal",
        )
    }

    private fun mutate(
        database: SQLiteDatabase,
        kind: GateV02DatabaseKind,
        operationClass: GateV02OperationClass,
        target: OperationTarget,
    ) {
        when (operationClass) {
            GateV02OperationClass.ADD_CONVERSATION_100 -> {
                database.execSQL(
                    "INSERT INTO conversations VALUES(?,?,?,?,?)",
                    arrayOf<Any>(
                        target.conversationId,
                        "Gate v0.2 added ${target.conversationId}",
                        SyntheticDatasetGenerator.BASE_STARTED_AT_MS + target.conversationId,
                        "GATE_V02",
                        "Synthetic gate participant",
                    ),
                )
                repeat(StorageUpdateGateV02Contract.SEGMENTS_PER_CONVERSATION) { sequence ->
                    val segmentId = target.segmentId + sequence
                    val text = "${target.marker} synthetic added segment $sequence"
                    database.execSQL(
                        "INSERT INTO transcript_segments VALUES(?,?,?,?,?,?,?)",
                        arrayOf<Any>(
                            segmentId,
                            target.conversationId,
                            sequence,
                            sequence * 1_000L,
                            sequence * 1_000L + 500L,
                            "en",
                            text,
                        ),
                    )
                    if (kind == GateV02DatabaseKind.INDEXED) {
                        database.execSQL(
                            "INSERT INTO transcript_segments_fts(rowid,text) VALUES(?,?)",
                            arrayOf<Any>(segmentId, text),
                        )
                    }
                }
            }
            GateV02OperationClass.UPDATE_SEGMENT_TEXT_1 -> {
                val text = "${target.marker} synthetic updated segment"
                database.execSQL(
                    "UPDATE transcript_segments SET text=? WHERE segment_id=?",
                    arrayOf<Any>(text, target.segmentId),
                )
                if (kind == GateV02DatabaseKind.INDEXED) {
                    database.execSQL(
                        "UPDATE transcript_segments_fts SET text=? WHERE rowid=?",
                        arrayOf<Any>(text, target.segmentId),
                    )
                }
            }
            GateV02OperationClass.UPDATE_CONVERSATION_FILTER_1 ->
                database.execSQL(
                    "UPDATE conversations SET source_type=? WHERE conversation_id=?",
                    arrayOf<Any>(target.sourceType, target.conversationId),
                )
            GateV02OperationClass.DELETE_SEGMENT_1 -> {
                if (kind == GateV02DatabaseKind.INDEXED) {
                    database.execSQL(
                        "DELETE FROM transcript_segments_fts WHERE rowid=?",
                        arrayOf(target.segmentId),
                    )
                }
                database.execSQL(
                    "DELETE FROM transcript_segments WHERE segment_id=?",
                    arrayOf(target.segmentId),
                )
            }
            GateV02OperationClass.DELETE_CONVERSATION_100 -> {
                if (kind == GateV02DatabaseKind.INDEXED) {
                    database.execSQL(
                        "DELETE FROM transcript_segments_fts WHERE rowid IN (" +
                            "SELECT segment_id FROM transcript_segments WHERE conversation_id=?)",
                        arrayOf(target.conversationId),
                    )
                }
                database.execSQL(
                    "DELETE FROM conversations WHERE conversation_id=?",
                    arrayOf(target.conversationId),
                )
            }
        }
    }

    private fun canonicalPostcondition(
        database: SQLiteDatabase,
        operationClass: GateV02OperationClass,
        target: OperationTarget,
    ): Boolean =
        when (operationClass) {
            GateV02OperationClass.ADD_CONVERSATION_100 ->
                count(
                    database,
                    "SELECT COUNT(*) FROM conversations WHERE conversation_id=?",
                    target.conversationId,
                ) == 1L &&
                    count(
                        database,
                        "SELECT COUNT(*) FROM transcript_segments WHERE conversation_id=?",
                        target.conversationId,
                    ) == 100L
            GateV02OperationClass.UPDATE_SEGMENT_TEXT_1 ->
                count(
                    database,
                    "SELECT COUNT(*) FROM transcript_segments WHERE segment_id=? AND text LIKE ?",
                    target.segmentId,
                    "%${target.marker}%",
                ) == 1L
            GateV02OperationClass.UPDATE_CONVERSATION_FILTER_1 ->
                count(
                    database,
                    "SELECT COUNT(*) FROM conversations WHERE conversation_id=? AND source_type=?",
                    target.conversationId,
                    target.sourceType,
                ) == 1L
            GateV02OperationClass.DELETE_SEGMENT_1 ->
                count(
                    database,
                    "SELECT COUNT(*) FROM transcript_segments WHERE segment_id=?",
                    target.segmentId,
                ) == 0L
            GateV02OperationClass.DELETE_CONVERSATION_100 ->
                count(
                    database,
                    "SELECT COUNT(*) FROM conversations WHERE conversation_id=?",
                    target.conversationId,
                ) == 0L &&
                    count(
                        database,
                        "SELECT COUNT(*) FROM transcript_segments WHERE conversation_id=?",
                        target.conversationId,
                    ) == 0L
        }

    private fun indexedPostcondition(
        database: SQLiteDatabase,
        operationClass: GateV02OperationClass,
        target: OperationTarget,
    ): Boolean =
        when (operationClass) {
            GateV02OperationClass.ADD_CONVERSATION_100 ->
                ftsCount(database, target.marker, "s.conversation_id=?", target.conversationId) ==
                    100L
            GateV02OperationClass.UPDATE_SEGMENT_TEXT_1 ->
                ftsCount(database, target.marker, "s.segment_id=?", target.segmentId) == 1L
            GateV02OperationClass.UPDATE_CONVERSATION_FILTER_1 ->
                ftsCount(
                    database,
                    "synthetic",
                    "c.conversation_id=? AND c.source_type=?",
                    target.conversationId,
                    target.sourceType,
                ) > 0L
            GateV02OperationClass.DELETE_SEGMENT_1 ->
                ftsCount(
                    database,
                    "synthetic",
                    "transcript_segments_fts.rowid=?",
                    target.segmentId,
                ) == 0L
            GateV02OperationClass.DELETE_CONVERSATION_100 ->
                ftsCount(database, "synthetic", "s.conversation_id=?", target.conversationId) == 0L
        }

    private fun ftsCount(
        database: SQLiteDatabase,
        match: String,
        extraPredicate: String,
        vararg arguments: Any,
    ): Long {
        val sql =
            "SELECT COUNT(*) FROM transcript_segments_fts " +
                "LEFT JOIN transcript_segments s ON s.segment_id=transcript_segments_fts.rowid " +
                "LEFT JOIN conversations c ON c.conversation_id=s.conversation_id " +
                "WHERE transcript_segments_fts MATCH ? AND $extraPredicate"
        return count(database, sql, match, *arguments)
    }

    private fun count(database: SQLiteDatabase, sql: String, vararg arguments: Any): Long =
        database.rawQuery(sql, arguments.map(Any::toString).toTypedArray()).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun firstSyntheticSegmentId(database: SQLiteDatabase, conversationId: Long): Long =
        database
            .rawQuery(
                "SELECT segment_id FROM transcript_segments " +
                    "WHERE conversation_id=? AND text LIKE '%synthetic%' " +
                    "ORDER BY segment_id LIMIT 1",
                arrayOf(conversationId.toString()),
            )
            .use { cursor ->
                check(cursor.moveToFirst())
                cursor.getLong(0)
            }

    private fun canonicalLogicalDigest(database: SQLiteDatabase): String {
        val digest = MessageDigest.getInstance("SHA-256")
        database
            .rawQuery(
                "SELECT conversation_id,title,started_at_ms,source_type,participant_label " +
                    "FROM conversations ORDER BY conversation_id",
                null,
            )
            .use { cursor ->
                while (cursor.moveToNext()) {
                    digest.update(
                        ("C|${cursor.getLong(0)}|${cursor.getString(1)}|${cursor.getLong(2)}|" +
                                "${cursor.getString(3)}|${cursor.getString(4)}\n")
                            .toByteArray(Charsets.UTF_8)
                    )
                }
            }
        database
            .rawQuery(
                "SELECT segment_id,conversation_id,sequence,start_ms,end_ms,language,text " +
                    "FROM transcript_segments ORDER BY segment_id",
                null,
            )
            .use { cursor ->
                while (cursor.moveToNext()) {
                    digest.update(
                        ("S|${cursor.getLong(0)}|${cursor.getLong(1)}|${cursor.getInt(2)}|" +
                                "${cursor.getLong(3)}|${cursor.getLong(4)}|${cursor.getString(5)}|" +
                                "${cursor.getString(6)}\n")
                            .toByteArray(Charsets.UTF_8)
                    )
                }
            }
        return BenchmarkDigests.toSha256(digest)
    }

    private fun checkpointBusy(database: SQLiteDatabase): Int =
        database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun missingCanonicalMappings(database: SQLiteDatabase): Long =
        scalarLong(
            database,
            "SELECT COUNT(*) FROM transcript_segments_fts f " +
                "LEFT JOIN transcript_segments s ON s.segment_id=f.rowid WHERE s.segment_id IS NULL",
        )

    private fun missingIndexRows(database: SQLiteDatabase): Long =
        scalarLong(
            database,
            "SELECT COUNT(*) FROM transcript_segments s " +
                "LEFT JOIN transcript_segments_fts f ON f.rowid=s.segment_id WHERE f.rowid IS NULL",
        )

    private fun scalarLong(database: SQLiteDatabase, sql: String): Long =
        database.rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun scalarString(database: SQLiteDatabase, sql: String): String =
        database.rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun stringColumn(database: SQLiteDatabase, sql: String): List<String> =
        database.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private fun elapsedMillis(started: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L

    private fun deleteAndVerify(databaseName: String): Boolean {
        context.deleteDatabase(databaseName)
        val path = context.getDatabasePath(databaseName)
        return !path.exists() &&
            !File(path.path + "-wal").exists() &&
            !File(path.path + "-shm").exists()
    }

    private fun File.lengthOrZero(): Long = if (isFile) length() else 0L

    private data class OperationTarget(
        val conversationId: Long,
        val segmentId: Long,
        val marker: String,
        val sourceType: String,
    )

    companion object {
        private const val CONVERSATION_BATCH_SIZE = 1_000
        private const val SEGMENT_BATCH_SIZE = 5_000L
        private const val ROOM_SCHEMA_IDENTITY = "poc-search-gate-v02-canonical-pair"
    }
}
