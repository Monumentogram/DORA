package com.monumentogram.dora.poc.search

import com.monumentogram.dora.poc.search.db.FtsIndexManager
import com.monumentogram.dora.poc.search.db.SearchPocDatabase
import com.monumentogram.dora.poc.search.query.CountExecution
import com.monumentogram.dora.poc.search.query.SearchExecution
import com.monumentogram.dora.poc.search.query.SearchMode
import com.monumentogram.dora.poc.search.query.SearchRepository
import com.monumentogram.dora.poc.search.query.SearchRequest
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
    val droppedIndexReturnedControlledFailure: Boolean,
    val canonicalRowsPreservedAfterDrop: Boolean,
    val dropRebuildRecovered: Boolean,
    val orphanDetected: Boolean,
    val orphanRemovedByRebuild: Boolean,
    val missingIndexRowDetected: Boolean,
    val missingIndexRowAffectedSearch: Boolean,
    val missingIndexRowRecovered: Boolean,
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
    private val database: SearchPocDatabase,
    private val repository: SearchRepository,
    private val campaign: QueryCampaignContract,
) {
    private val dao = database.searchDao()
    private val indexManager = FtsIndexManager(database)

    fun run(baselineCorrectness: CorrectnessSummary): RebuildSummary {
        val baselineIndexDigest = logicalIndexDigest(database)
        val passes =
            listOf(
                rebuildPass("REBUILD-1"),
                rebuildPass("REBUILD-2"),
            )
        val recovery = exerciseRecoveryBoundaries()
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
        val (_, latencyMs) = BenchmarkClock.measure(indexManager::rebuildFromCanonicalRows)
        indexManager.ftsIntegrityCheck()
        val correctness =
            QueryCampaignRunner(repository, dao, campaign).runCorrectness(id.lowercase())
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
        val canonicalBefore = dao.transcriptCount()
        val createSql = indexManager.dropIndex()
        val unavailable = repository.search(SearchRequest("project", SearchMode.EXACT))
        val controlledFailure = unavailable is SearchExecution.Failure
        val canonicalPreserved = dao.transcriptCount() == canonicalBefore
        indexManager.recreateIndex(createSql)
        indexManager.rebuildFromCanonicalRows()
        val dropRecovered = dao.ftsCount() == canonicalBefore && mappingsHealthy()

        indexManager.injectOrphan(ORPHAN_ROW_ID, ORPHAN_MARKER)
        val orphanDetected = indexManager.missingCanonicalMappingCount() == 1L
        indexManager.rebuildFromCanonicalRows()
        val orphanRemoved = indexManager.missingCanonicalMappingCount() == 0L && mappingsHealthy()

        indexManager.deleteIndexRow(UNIQUE_MARKER_SEGMENT_ID)
        val missingDetected = indexManager.missingIndexRowCount() == 1L
        val missingAffectedSearch = successfulCount(UNIQUE_MARKER) == 0L
        indexManager.rebuildFromCanonicalRows()
        val missingRecovered =
            indexManager.missingIndexRowCount() == 0L &&
                successfulCount(UNIQUE_MARKER) == 1L &&
                mappingsHealthy()
        return SearchRecoveryObservation(
            droppedIndexReturnedControlledFailure = controlledFailure,
            canonicalRowsPreservedAfterDrop = canonicalPreserved,
            dropRebuildRecovered = dropRecovered,
            orphanDetected = orphanDetected,
            orphanRemovedByRebuild = orphanRemoved,
            missingIndexRowDetected = missingDetected,
            missingIndexRowAffectedSearch = missingAffectedSearch,
            missingIndexRowRecovered = missingRecovered,
        )
    }

    private fun successfulCount(rawQuery: String): Long {
        val result = repository.count(SearchRequest(rawQuery, SearchMode.EXACT))
        check(result is CountExecution.Success)
        return result.count
    }

    private fun mappingsHealthy(): Boolean =
        dao.transcriptCount() == dao.ftsCount() &&
            indexManager.missingCanonicalMappingCount() == 0L &&
            indexManager.missingIndexRowCount() == 0L &&
            indexManager.duplicateCanonicalCount() == 0L

    companion object {
        private const val ORPHAN_ROW_ID = 2_000_000_001L
        private const val ORPHAN_MARKER = "syntheticorphanmarker"
        private const val UNIQUE_MARKER_SEGMENT_ID = 424_242L
        private const val UNIQUE_MARKER = "uniquemarkerquasar"

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
