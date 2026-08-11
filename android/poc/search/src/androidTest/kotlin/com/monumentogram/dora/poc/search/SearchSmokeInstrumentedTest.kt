package com.monumentogram.dora.poc.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.poc.search.data.SyntheticDatasetGenerator
import com.monumentogram.dora.poc.search.db.FtsIndexManager
import com.monumentogram.dora.poc.search.db.SearchPocDatabase
import com.monumentogram.dora.poc.search.query.CountExecution
import com.monumentogram.dora.poc.search.query.SafeFtsQueryCompiler
import com.monumentogram.dora.poc.search.query.SearchExecution
import com.monumentogram.dora.poc.search.query.SearchMode
import com.monumentogram.dora.poc.search.query.SearchRepository
import com.monumentogram.dora.poc.search.query.SearchRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchSmokeInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: SearchPocDatabase
    private lateinit var repository: SearchRepository
    private lateinit var indexManager: FtsIndexManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        database = SearchPocDatabase.open(context, DATABASE_NAME)
        database.openHelper.writableDatabase
        populateSmallDataset()
        indexManager = FtsIndexManager(database)
        indexManager.rebuildFromCanonicalRows()
        repository = SearchRepository(database.searchDao())
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun roomSchemaAndFts4IndexAreHealthy() {
        val sqliteMaster =
            database.openHelper.readableDatabase.query(
                "SELECT sql FROM sqlite_master WHERE name='transcript_segments_fts'"
            )
        val createSql = sqliteMaster.use {
            assertTrue(it.moveToFirst())
            it.getString(0)
        }

        assertTrue(createSql.contains("fts4", ignoreCase = true))
        assertEquals("ok", indexManager.sqliteIntegrityCheck())
        indexManager.ftsIntegrityCheck()
        assertEquals(SMOKE_CONVERSATIONS.toLong(), database.searchDao().conversationCount())
        assertEquals(SMOKE_SEGMENTS.toLong(), database.searchDao().transcriptCount())
        assertEquals(SMOKE_SEGMENTS.toLong(), database.searchDao().ftsCount())
        assertEquals(0, indexManager.missingCanonicalMappingCount())
        assertEquals(0, indexManager.missingIndexRowCount())
        assertEquals(0, indexManager.duplicateCanonicalCount())
    }

    @Test
    fun generatedOracleMatchesFtsResultsAndCanonicalSourceMapping() {
        val request = SearchRequest("project", SearchMode.EXACT, limit = 25)
        val expectedRows =
            (1L..SMOKE_SEGMENTS.toLong()).map(SyntheticDatasetGenerator::segment).filter {
                "project" in SafeFtsQueryCompiler.tokenize(it.text)
            }
        val count = repository.count(request)
        val search = repository.search(request)

        assertTrue(count is CountExecution.Success)
        assertEquals(expectedRows.size.toLong(), (count as CountExecution.Success).count)
        assertTrue(search is SearchExecution.Success)
        val hits = (search as SearchExecution.Success).hits
        assertEquals(expectedRows.take(25).map { it.segmentId }, hits.map { it.segmentId })
        hits.zip(expectedRows).forEach { (hit, expected) ->
            assertEquals(expected.conversationId, hit.conversationId)
            assertEquals(expected.startMs, hit.startMs)
            assertEquals(expected.endMs, hit.endMs)
            assertEquals(expected.text, hit.text)
        }
        assertEquals(hits.size, hits.map { it.segmentId }.distinct().size)
    }

    @Test
    fun matchAndSourceCountAndPageUseStreamingFts4Plans() {
        val manifest = BenchmarkContracts.readQueries(context)
        val queryCase = manifest.cases.single { it.id == "Q-SEARCH-SOURCE" }
        val request =
            SearchRequest(
                rawQuery = queryCase.rawQuery,
                mode = queryCase.mode,
                filters = queryCase.filters,
                limit = manifest.resultLimit,
            )

        val countObservation =
            FtsQueryPlanPolicy.inspect(database, repository.explainCountQuery(request))
        val pageObservation =
            FtsQueryPlanPolicy.inspect(database, repository.explainSearchQuery(request))

        assertTrue(countObservation.details.joinToString(" | "), countObservation.accepted)
        assertTrue(pageObservation.details.joinToString(" | "), pageObservation.accepted)
        val count = repository.count(request)
        assertTrue(count is CountExecution.Success)
        assertEquals(
            expectedSmallDatasetSourceCount(queryCase),
            (count as CountExecution.Success).count,
        )
    }

    @Test
    fun frozenQueryManifestAndAdversarialHandlingMatchCompilerContract() {
        val manifest = BenchmarkContracts.readQueries(context)
        assertTrue(manifest.cases.size >= 50)

        manifest.cases.forEach { queryCase ->
            val request =
                SearchRequest(
                    rawQuery = queryCase.rawQuery,
                    mode = queryCase.mode,
                    filters = queryCase.filters,
                    limit = manifest.resultLimit,
                )
            val compiled = repository.compile(request)
            assertEquals(queryCase.id, queryCase.expectedStatus, compiled.status)
            assertEquals(queryCase.id, queryCase.expectedMatch, compiled.matchExpression)
            assertEquals(queryCase.id, queryCase.expectedRejectionCode, compiled.rejectionCode)
            assertEquals(queryCase.id, queryCase.expectedTokens, compiled.tokens)

            if (queryCase.category == "adversarial" || queryCase.category == "special-characters") {
                val execution = repository.search(request)
                assertFalse(queryCase.id, execution is SearchExecution.Failure)
                assertEquals(SMOKE_CONVERSATIONS.toLong(), database.searchDao().conversationCount())
                assertEquals(SMOKE_SEGMENTS.toLong(), database.searchDao().transcriptCount())
            }
        }
    }

    @Test
    fun portableFts4DialectMatchesFrozenPrefixAndLiteralTokenOracles() {
        val dao = database.searchDao()
        val generatedRows =
            (1L..SMOKE_SEGMENTS).associateWith(SyntheticDatasetGenerator::segment).toMutableMap()
        val unicodeRow = generatedRows.getValue(1L)
        val rarePrefixRow = generatedRows.getValue(2L)
        generatedRows[1L] = unicodeRow.copy(text = "${unicodeRow.text} ёжик café 東京")
        generatedRows[2L] = rarePrefixRow.copy(text = "${rarePrefixRow.text} редкословоаврора")
        dao.updateSegmentText(1L, generatedRows.getValue(1L).text)
        dao.updateSegmentText(2L, generatedRows.getValue(2L).text)

        val manifest = BenchmarkContracts.readQueries(context)
        val regressionCases = manifest.cases.filter { it.id in FTS4_DIALECT_REGRESSION_IDS }
        assertEquals(FTS4_DIALECT_REGRESSION_IDS.size, regressionCases.size)

        regressionCases.forEach { queryCase ->
            val expectedRows =
                generatedRows.values.filter { matchesFrozenTokens(queryCase, it.text) }
            assertTrue(queryCase.id, expectedRows.isNotEmpty())

            val request =
                SearchRequest(
                    rawQuery = queryCase.rawQuery,
                    mode = queryCase.mode,
                    filters = queryCase.filters,
                    limit = manifest.resultLimit,
                )
            val count = repository.count(request)
            val search = repository.search(request)
            assertTrue(queryCase.id, count is CountExecution.Success)
            assertTrue(queryCase.id, search is SearchExecution.Success)
            assertEquals(
                queryCase.id,
                expectedRows.size.toLong(),
                (count as CountExecution.Success).count,
            )
            assertEquals(
                queryCase.id,
                expectedRows.take(manifest.resultLimit).map { it.segmentId },
                (search as SearchExecution.Success).hits.map { it.segmentId },
            )
        }
    }

    @Test
    fun updateDeleteAndIndexRebuildDoNotLeaveStaleMappings() {
        val dao = database.searchDao()
        val updateId = 1_234L
        val oldText = SyntheticDatasetGenerator.segment(updateId).text
        val newText = "$oldText smokeupdatedmarker"
        dao.updateSegmentText(updateId, newText)

        assertEquals(1, successfulCount("smokeupdatedmarker"))
        assertEquals(0, successfulCount("markerthatwasneverpresent"))

        val deletedId = 2_345L
        dao.deleteSegment(deletedId)
        assertEquals(SMOKE_SEGMENTS - 1L, dao.transcriptCount())
        assertEquals(SMOKE_SEGMENTS - 1L, dao.ftsCount())

        val createSql = indexManager.dropIndex()
        val unavailable = repository.search(SearchRequest("project", SearchMode.EXACT))
        assertTrue(unavailable is SearchExecution.Failure)
        assertEquals("SQLITE_QUERY_FAILED", (unavailable as SearchExecution.Failure).code)
        assertEquals(SMOKE_SEGMENTS - 1L, dao.transcriptCount())

        indexManager.recreateIndex(createSql)
        indexManager.rebuildFromCanonicalRows()
        assertEquals(SMOKE_SEGMENTS - 1L, dao.ftsCount())
        assertEquals(0, indexManager.missingCanonicalMappingCount())
        assertEquals(0, indexManager.missingIndexRowCount())
        assertNotNull(repository.search(SearchRequest("project", SearchMode.EXACT)))
    }

    private fun populateSmallDataset() {
        val dao = database.searchDao()
        dao.insertConversations(
            (1..SMOKE_CONVERSATIONS).map { SyntheticDatasetGenerator.conversation(it.toLong()) }
        )
        var nextId = 1L
        while (nextId <= SMOKE_SEGMENTS) {
            val endExclusive = minOf(nextId + 1_000, SMOKE_SEGMENTS + 1)
            dao.insertSegments((nextId until endExclusive).map(SyntheticDatasetGenerator::segment))
            nextId = endExclusive
        }
    }

    private fun successfulCount(rawQuery: String): Long {
        val result = repository.count(SearchRequest(rawQuery, SearchMode.EXACT))
        assertTrue(result is CountExecution.Success)
        return (result as CountExecution.Success).count
    }

    private fun expectedSmallDatasetSourceCount(queryCase: QueryCase): Long =
        (1L..SMOKE_SEGMENTS)
            .count { segmentId ->
                val segment = SyntheticDatasetGenerator.segment(segmentId)
                val conversation = SyntheticDatasetGenerator.conversation(segment.conversationId)
                queryCase.expectedTokens.all { token ->
                    token in SafeFtsQueryCompiler.tokenize(segment.text)
                } && conversation.sourceType == queryCase.filters.sourceType
            }
            .toLong()

    private fun matchesFrozenTokens(queryCase: QueryCase, text: String): Boolean {
        val textTokens = SafeFtsQueryCompiler.tokenize(text)
        return when (queryCase.mode) {
            SearchMode.PREFIX ->
                queryCase.expectedTokens.all { prefix -> textTokens.any { it.startsWith(prefix) } }
            SearchMode.EXACT -> queryCase.expectedTokens.all { token -> token in textTokens }
            SearchMode.PHRASE -> error("No phrase case in FTS4 dialect regression set")
        }
    }

    companion object {
        private const val DATABASE_NAME = "poc-search-smoke.db"
        private const val SMOKE_CONVERSATIONS = 100
        private const val SMOKE_SEGMENTS = 10_000L
        private val FTS4_DIALECT_REGRESSION_IDS =
            setOf(
                "Q-PREFIX-RU-QUANT",
                "Q-PREFIX-EN-HYPER",
                "Q-PREFIX-RU-RARE",
                "Q-PREFIX-EN-SYNTH",
                "Q-PREFIX-RU-PROJECT",
                "Q-SPECIAL-APOSTROPHE",
                "Q-SPECIAL-COLON",
                "Q-SPECIAL-HYPHEN",
                "Q-SPECIAL-UNICODE",
                "Q-ADVERSARIAL-COMMENT",
                "Q-ADVERSARIAL-CONTROL",
            )
    }
}
