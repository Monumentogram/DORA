@file:Suppress("LongMethod")

package com.monumentogram.dora.poc.search

import android.content.Context
import com.monumentogram.dora.poc.search.data.SyntheticDatasetGenerator
import com.monumentogram.dora.poc.search.db.FtsIndexManager
import com.monumentogram.dora.poc.search.db.SearchPocDatabase
import java.security.MessageDigest

class ReferenceDatabaseBuilder(
    private val context: Context,
    private val contract: DatasetContract,
    private val memorySampler: MemorySampler,
) {
    fun build(databaseName: String): OpenReferenceDatabase {
        verifyContract()
        context.deleteDatabase(databaseName)
        val totalStarted = android.os.SystemClock.elapsedRealtimeNanos()

        val (database, emptyDatabaseCreationMs) =
            BenchmarkClock.measure {
                SearchPocDatabase.open(context, databaseName).also {
                    it.openHelper.writableDatabase.query("SELECT 1").close()
                }
            }
        val dao = database.searchDao()
        val generatorDigest = MessageDigest.getInstance("SHA-256")
        memorySampler.sample()

        val (_, conversationInsertMs) =
            BenchmarkClock.measure {
                var firstId = 1
                while (firstId <= contract.conversationCount) {
                    val endExclusive =
                        minOf(
                            firstId + SyntheticDatasetGenerator.CONVERSATION_BATCH_SIZE,
                            contract.conversationCount + 1,
                        )
                    val batch =
                        (firstId until endExclusive).map { id ->
                            SyntheticDatasetGenerator.conversation(id.toLong()).also { conversation
                                ->
                                SyntheticDatasetGenerator.updateLogicalDigest(
                                    generatorDigest,
                                    conversation,
                                )
                            }
                        }
                    dao.insertConversations(batch)
                    memorySampler.sample()
                    firstId = endExclusive
                }
            }

        val (_, transcriptInsertMs) =
            BenchmarkClock.measure {
                var firstId = 1L
                while (firstId <= contract.transcriptRowCount.toLong()) {
                    val endExclusive =
                        minOf(
                            firstId + SyntheticDatasetGenerator.TRANSCRIPT_BATCH_SIZE,
                            contract.transcriptRowCount + 1L,
                        )
                    val batch =
                        (firstId until endExclusive).map { id ->
                            SyntheticDatasetGenerator.segment(id).also { segment ->
                                SyntheticDatasetGenerator.updateLogicalDigest(
                                    generatorDigest,
                                    segment,
                                )
                            }
                        }
                    dao.insertSegments(batch)
                    memorySampler.sample()
                    firstId = endExclusive
                }
            }

        val indexManager = FtsIndexManager(database)
        val (_, indexBuildMs) = BenchmarkClock.measure(indexManager::rebuildFromCanonicalRows)
        memorySampler.sample()
        val beforeCompact = DatabaseFiles.snapshot(context, databaseName)
        val (_, checkpointCompactMs) = BenchmarkClock.measure(indexManager::checkpointAndCompact)
        val afterCompact = DatabaseFiles.snapshot(context, databaseName)
        val afterCompactDatabaseSha256 =
            BenchmarkDigests.sha256(context.getDatabasePath(databaseName))
        memorySampler.sample()

        val (databaseDigest, logicalDigestReadMs) =
            BenchmarkClock.measure { logicalDatabaseDigest(database) }
        val totalPreparationMs =
            (android.os.SystemClock.elapsedRealtimeNanos() - totalStarted) / 1_000_000.0
        val expectedDigest = BenchmarkDigests.toSha256(generatorDigest)

        val preparation =
            DatabasePreparation(
                emptyDatabaseCreationMs = emptyDatabaseCreationMs,
                conversationInsertMs = conversationInsertMs,
                transcriptInsertMs = transcriptInsertMs,
                indexBuildMs = indexBuildMs,
                checkpointCompactMs = checkpointCompactMs,
                logicalDigestReadMs = logicalDigestReadMs,
                totalPreparationMs = totalPreparationMs,
                beforeCompact = beforeCompact,
                afterCompact = afterCompact,
                afterCompactDatabaseSha256 = afterCompactDatabaseSha256,
                conversationCount = dao.conversationCount(),
                transcriptCount = dao.transcriptCount(),
                ftsCount = dao.ftsCount(),
                expectedLogicalDigest = expectedDigest,
                databaseLogicalDigest = databaseDigest,
                sqliteIntegrity = indexManager.sqliteIntegrityCheck(),
                missingCanonicalMappings = indexManager.missingCanonicalMappingCount(),
                missingIndexRows = indexManager.missingIndexRowCount(),
                duplicateCanonicalRows = indexManager.duplicateCanonicalCount(),
            )
        return OpenReferenceDatabase(databaseName, database, preparation)
    }

    private fun verifyContract() {
        check(contract.datasetVersion == SyntheticDatasetGenerator.DATASET_VERSION)
        check(contract.generatorVersion == SyntheticDatasetGenerator.GENERATOR_VERSION)
        check(contract.seed == SyntheticDatasetGenerator.SEED)
        check(contract.conversationCount == SyntheticDatasetGenerator.REFERENCE_CONVERSATIONS)
        check(contract.transcriptRowCount == SyntheticDatasetGenerator.REFERENCE_TRANSCRIPT_ROWS)
        check(
            contract.segmentsPerConversation == SyntheticDatasetGenerator.SEGMENTS_PER_CONVERSATION
        )
    }

    private fun logicalDatabaseDigest(database: SearchPocDatabase): String {
        val digest = MessageDigest.getInstance("SHA-256")
        database.openHelper.readableDatabase
            .query(
                "SELECT conversation_id, title, started_at_ms, source_type, participant_label " +
                    "FROM conversations ORDER BY conversation_id"
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
        database.openHelper.readableDatabase
            .query(
                "SELECT segment_id, conversation_id, sequence, start_ms, end_ms, language, text " +
                    "FROM transcript_segments ORDER BY segment_id"
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
}
