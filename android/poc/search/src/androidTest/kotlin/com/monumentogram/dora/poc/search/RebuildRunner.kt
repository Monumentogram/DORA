package com.monumentogram.dora.poc.search

import android.content.Context
import com.monumentogram.dora.poc.search.data.SyntheticDatasetGenerator
import com.monumentogram.dora.poc.search.db.FtsIndexManager
import com.monumentogram.dora.poc.search.db.SearchPocDao
import com.monumentogram.dora.poc.search.db.SearchPocDatabase
import com.monumentogram.dora.poc.search.query.CountExecution
import com.monumentogram.dora.poc.search.query.SearchExecution
import com.monumentogram.dora.poc.search.query.SearchMode
import com.monumentogram.dora.poc.search.query.SearchRepository
import com.monumentogram.dora.poc.search.query.SearchRequest
import java.io.File
import java.security.MessageDigest

data class RebuildPassObservation(
    val id: String,
    val latencyMs: Double,
    val indexLogicalSha256: String,
    val queryResultSha256: String,
    val queryCorrectnessPassed: Boolean,
    val conversationCount: Long,
    val transcriptCount: Long,
    val ftsCount: Long,
    val mappingsHealthy: Boolean,
)

data class SearchRecoveryObservation(
    val conversationCount: Long,
    val transcriptCount: Long,
    val droppedIndexReturnedControlledFailure: Boolean,
    val canonicalRowsPreservedAfterDrop: Boolean,
    val dropRebuildRecovered: Boolean,
    val orphanDetected: Boolean,
    val orphanRemovedByRebuild: Boolean,
    val missingIndexRowDetected: Boolean,
    val missingIndexRowAffectedSearch: Boolean,
    val missingIndexRowRecovered: Boolean,
    val temporaryDatabaseDeleted: Boolean,
)

data class RebuildSummary(
    val baselineIndexLogicalSha256: String,
    val baselineQueryResultSha256: String,
    val passes: List<RebuildPassObservation>,
    val recovery: SearchRecoveryObservation,
    val finalCorrectness: CorrectnessSummary,
    val deterministic: Boolean,
)

data class CrossBuildDeterminism(
    val primaryDatasetLogicalSha256: String,
    val secondaryDatasetLogicalSha256: String,
    val primaryIndexLogicalSha256: String,
    val secondaryIndexLogicalSha256: String,
    val primaryQueryResultSha256: String,
    val secondaryQueryResultSha256: String,
    val countsMatched: Boolean,
    val logicalDatasetMatched: Boolean,
    val logicalIndexMatched: Boolean,
    val queryResultsMatched: Boolean,
) {
    val deterministic: Boolean =
        countsMatched && logicalDatasetMatched && logicalIndexMatched && queryResultsMatched
}

class RebuildRunner(
    private val context: Context,
    private val database: SearchPocDatabase,
    private val repository: SearchRepository,
    private val campaign: QueryCampaignContract,
) {
    private val dao = database.searchDao()
    private val indexManager = FtsIndexManager(database)

    fun run(baselineCorrectness: CorrectnessSummary): RebuildSummary {
        BenchmarkProgress.report("full campaign: baseline logical index digest started")
        val baselineIndexDigest = logicalIndexDigest(database)
        BenchmarkProgress.report("full campaign: baseline logical index digest complete")
        val passes =
            listOf(
                rebuildPass("REBUILD-1"),
                rebuildPass("REBUILD-2"),
            )
        BenchmarkProgress.report("full campaign: isolated recovery boundary started")
        val recovery = exerciseRecoveryBoundaries()
        BenchmarkProgress.report("full campaign: isolated recovery boundary complete")
        val finalCorrectness =
            QueryCampaignRunner(repository, dao, campaign).runCorrectness("post-recovery-rebuild")
        val deterministic =
            baselineCorrectness.allMatched &&
                passes.all {
                    it.queryCorrectnessPassed &&
                        it.indexLogicalSha256 == baselineIndexDigest &&
                        it.queryResultSha256 == baselineCorrectness.queryResultSha256 &&
                        it.mappingsHealthy
                } &&
                recovery.dropRebuildRecovered &&
                recovery.orphanRemovedByRebuild &&
                recovery.missingIndexRowRecovered &&
                recovery.temporaryDatabaseDeleted &&
                finalCorrectness.allMatched &&
                finalCorrectness.queryResultSha256 == baselineCorrectness.queryResultSha256 &&
                logicalIndexDigest(database) == baselineIndexDigest
        return RebuildSummary(
            baselineIndexLogicalSha256 = baselineIndexDigest,
            baselineQueryResultSha256 = baselineCorrectness.queryResultSha256,
            passes = passes,
            recovery = recovery,
            finalCorrectness = finalCorrectness,
            deterministic = deterministic,
        )
    }

    private fun rebuildPass(id: String): RebuildPassObservation {
        BenchmarkProgress.report("full campaign: $id full-scale FTS4 rebuild started")
        val (_, latencyMs) = BenchmarkClock.measure(indexManager::rebuildFromCanonicalRows)
        indexManager.ftsIntegrityCheck()
        BenchmarkProgress.report("full campaign: $id full-scale FTS4 rebuild complete")
        val correctness =
            QueryCampaignRunner(repository, dao, campaign).runCorrectness(id.lowercase())
        BenchmarkProgress.report("full campaign: $id correctness complete")
        return RebuildPassObservation(
            id = id,
            latencyMs = latencyMs,
            indexLogicalSha256 = logicalIndexDigest(database),
            queryResultSha256 = correctness.queryResultSha256,
            queryCorrectnessPassed = correctness.allMatched,
            conversationCount = dao.conversationCount(),
            transcriptCount = dao.transcriptCount(),
            ftsCount = dao.ftsCount(),
            mappingsHealthy = mappingsHealthy(),
        )
    }

    private fun exerciseRecoveryBoundaries(): SearchRecoveryObservation {
        context.deleteDatabase(RECOVERY_DATABASE)
        var recoveryDatabase: SearchPocDatabase? = null
        var deleted = false
        lateinit var observation: SearchRecoveryObservation
        try {
            val opened = createRecoveryDatabase()
            recoveryDatabase = opened
            val recoveryDao = opened.searchDao()
            val recoveryIndex = FtsIndexManager(opened)
            val recoveryRepository = SearchRepository(recoveryDao)
            val canonicalBefore = recoveryDao.transcriptCount()
            val createSql = recoveryIndex.dropIndex()
            val unavailable = recoveryRepository.search(SearchRequest("project", SearchMode.EXACT))
            val controlledFailure = unavailable is SearchExecution.Failure
            val canonicalPreserved = recoveryDao.transcriptCount() == canonicalBefore
            recoveryIndex.recreateIndex(createSql)
            recoveryIndex.rebuildFromCanonicalRows()
            val dropRecovered =
                recoveryDao.ftsCount() == canonicalBefore &&
                    mappingsHealthy(recoveryDao, recoveryIndex)

            recoveryIndex.injectOrphan(ORPHAN_ROW_ID, ORPHAN_MARKER)
            recoveryIndex.deleteIndexRow(UNIQUE_MARKER_SEGMENT_ID)
            val orphanDetected = recoveryIndex.missingCanonicalMappingCount() == 1L
            val missingDetected = recoveryIndex.missingIndexRowCount() == 1L
            val missingAffectedSearch = successfulCount(recoveryRepository, UNIQUE_MARKER) == 0L
            recoveryIndex.rebuildFromCanonicalRows()
            val orphanRemoved =
                recoveryIndex.missingCanonicalMappingCount() == 0L &&
                    mappingsHealthy(recoveryDao, recoveryIndex)
            val missingRecovered =
                recoveryIndex.missingIndexRowCount() == 0L &&
                    successfulCount(recoveryRepository, UNIQUE_MARKER) == 1L &&
                    mappingsHealthy(recoveryDao, recoveryIndex)
            observation =
                SearchRecoveryObservation(
                    conversationCount = recoveryDao.conversationCount(),
                    transcriptCount = recoveryDao.transcriptCount(),
                    droppedIndexReturnedControlledFailure = controlledFailure,
                    canonicalRowsPreservedAfterDrop = canonicalPreserved,
                    dropRebuildRecovered = dropRecovered,
                    orphanDetected = orphanDetected,
                    orphanRemovedByRebuild = orphanRemoved,
                    missingIndexRowDetected = missingDetected,
                    missingIndexRowAffectedSearch = missingAffectedSearch,
                    missingIndexRowRecovered = missingRecovered,
                    temporaryDatabaseDeleted = false,
                )
        } finally {
            recoveryDatabase?.close()
            deleted = deleteAndVerify(RECOVERY_DATABASE)
        }
        return observation.copy(temporaryDatabaseDeleted = deleted)
    }

    private fun createRecoveryDatabase(): SearchPocDatabase =
        SearchPocDatabase.open(context, RECOVERY_DATABASE).also { opened ->
            opened
                .searchDao()
                .insertConversations(
                    (1L..RECOVERY_CONVERSATIONS).map(SyntheticDatasetGenerator::conversation)
                )
            opened
                .searchDao()
                .insertSegments((1L..RECOVERY_TRANSCRIPTS).map(SyntheticDatasetGenerator::segment))
            FtsIndexManager(opened).rebuildFromCanonicalRows()
        }

    private fun successfulCount(searchRepository: SearchRepository, rawQuery: String): Long {
        val result = searchRepository.count(SearchRequest(rawQuery, SearchMode.EXACT))
        check(result is CountExecution.Success)
        return result.count
    }

    private fun mappingsHealthy(): Boolean = mappingsHealthy(dao, indexManager)

    private fun mappingsHealthy(
        searchDao: SearchPocDao,
        manager: FtsIndexManager,
    ): Boolean =
        searchDao.transcriptCount() == searchDao.ftsCount() &&
            manager.missingCanonicalMappingCount() == 0L &&
            manager.missingIndexRowCount() == 0L &&
            manager.duplicateCanonicalCount() == 0L

    private fun deleteAndVerify(databaseName: String): Boolean {
        context.deleteDatabase(databaseName)
        val path = context.getDatabasePath(databaseName)
        return !path.exists() &&
            !File(path.path + "-wal").exists() &&
            !File(path.path + "-shm").exists()
    }

    companion object {
        private const val ORPHAN_ROW_ID = 2_000_000_001L
        private const val ORPHAN_MARKER = "syntheticorphanmarker"
        private const val UNIQUE_MARKER_SEGMENT_ID = 991L
        private const val UNIQUE_MARKER = "hyperprotocol"
        private const val RECOVERY_DATABASE = "poc-search-recovery-boundary.db"
        private const val RECOVERY_CONVERSATIONS = 10L
        private const val RECOVERY_TRANSCRIPTS = 1_000L

        fun logicalIndexDigest(database: SearchPocDatabase): String {
            val digest = MessageDigest.getInstance("SHA-256")
            database.openHelper.readableDatabase
                .query("SELECT rowid, text FROM transcript_segments_fts ORDER BY rowid")
                .use { cursor ->
                    while (cursor.moveToNext()) {
                        digest.update(
                            "F|${cursor.getLong(0)}|${cursor.getString(1)}\n"
                                .toByteArray(Charsets.UTF_8)
                        )
                    }
                }
            return BenchmarkDigests.toSha256(digest)
        }
    }
}
