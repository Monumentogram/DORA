package com.monumentogram.dora.poc.search

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.poc.search.db.SearchPocDatabase
import com.monumentogram.dora.poc.search.query.CountExecution
import com.monumentogram.dora.poc.search.query.SearchExecution
import com.monumentogram.dora.poc.search.query.SearchRepository
import com.monumentogram.dora.poc.search.query.SearchRequest
import java.io.File
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

private data class TargetedContract(
    val dataset: DatasetContract,
    val queries: QueryCampaignContract,
    val queryCase: QueryCase,
    val request: SearchRequest,
)

private data class TargetedExecution(
    val plan: FtsQueryPlanObservation,
    val observedCount: Long,
    val countMs: Double,
    val searchMs: Double,
    val observedIds: List<Long>,
    val sqliteVersion: String,
    val ftsCreateSql: String,
)

@RunWith(AndroidJUnit4::class)
class TargetedScaleQueryPlanInstrumentedTest {
    @Test
    fun verifySourceFilteredCountAtFrozenScale() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(
            "The 1M-row targeted scale preflight runs only through the explicit workflow",
            InstrumentationRegistry.getArguments().getString(TARGETED_ARGUMENT) == "true",
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val contract = readFrozenContract(context)
        val memorySampler = MemorySampler()
        val started = SystemClock.elapsedRealtimeNanos()
        var opened: OpenReferenceDatabase? = null

        try {
            BenchmarkProgress.report("targeted scale: 10k/1M reference build started")
            val reference =
                ReferenceDatabaseBuilder(context, contract.dataset, memorySampler)
                    .build(DATABASE_NAME)
            opened = reference
            val execution = executeTarget(reference, contract)
            val preparation = reference.preparation
            memorySampler.sample()
            reference.database.close()
            opened = null
            val deleted = deleteAndVerify(context)
            val output =
                writeObservation(
                    context,
                    contract,
                    preparation,
                    execution,
                    memorySampler.observation(),
                    deleted,
                    elapsedSeconds(started),
                )
            instrumentation.sendStatus(
                0,
                Bundle().apply { putString("pocSearchTargetedOutput", output.absolutePath) },
            )
            println("POC_SEARCH_TARGETED_OUTPUT=${output.absolutePath}")
            check(deleted)
            verifyOperationGuards(execution)
        } finally {
            opened?.database?.close()
            deleteAndVerify(context)
        }
    }

    private fun executeTarget(
        reference: OpenReferenceDatabase,
        contract: TargetedContract,
    ): TargetedExecution {
        val repository = SearchRepository(reference.database.searchDao())
        val plan =
            FtsQueryPlanPolicy.inspect(
                reference.database,
                repository.explainCountQuery(contract.request),
            )
        plan.details.forEach { detail ->
            BenchmarkProgress.report("targeted scale: EXPLAIN $detail")
        }
        check(plan.accepted) {
            "Refusing to execute targeted count with non-FTS-driving plan: " +
                plan.details.joinToString(" | ")
        }
        BenchmarkProgress.report("targeted scale: Q-SEARCH-SOURCE count started")
        val (countExecution, countMs) =
            BenchmarkClock.measure { repository.count(contract.request) }
        BenchmarkProgress.report("targeted scale: Q-SEARCH-SOURCE count complete in $countMs ms")
        check(countExecution is CountExecution.Success)
        check(countExecution.count == contract.queryCase.expectedCount)
        BenchmarkProgress.report("targeted scale: Q-SEARCH-SOURCE page started")
        val (searchExecution, searchMs) =
            BenchmarkClock.measure { repository.search(contract.request) }
        BenchmarkProgress.report("targeted scale: Q-SEARCH-SOURCE page complete in $searchMs ms")
        check(searchExecution is SearchExecution.Success)
        val observedIds = searchExecution.hits.map { it.segmentId }
        val expectedIds = contract.queryCase.expectedMappings.map { it.segmentId }
        check(observedIds.take(expectedIds.size) == expectedIds)
        return TargetedExecution(
            plan = plan,
            observedCount = countExecution.count,
            countMs = countMs,
            searchMs = searchMs,
            observedIds = observedIds,
            sqliteVersion = scalar(reference.database, "SELECT sqlite_version()"),
            ftsCreateSql = ftsCreateSql(reference.database),
        )
    }

    @Suppress("LongParameterList")
    private fun writeObservation(
        context: Context,
        contract: TargetedContract,
        preparation: DatabasePreparation,
        execution: TargetedExecution,
        memory: MemoryObservation,
        databaseDeleted: Boolean,
        durationSeconds: Double,
    ): File {
        val outputDirectory = context.filesDir.resolve(BenchmarkObservationWriter.OUTPUT_DIRECTORY)
        check(outputDirectory.exists() || outputDirectory.mkdirs())
        val output = outputDirectory.resolve(OUTPUT_FILE)
        output.writeText(
            observationJson(
                    context,
                    contract,
                    preparation,
                    execution,
                    memory,
                    databaseDeleted,
                    durationSeconds,
                )
                .toString(2) + "\n",
            Charsets.UTF_8,
        )
        return output
    }

    @Suppress("LongParameterList")
    private fun observationJson(
        context: Context,
        contract: TargetedContract,
        preparation: DatabasePreparation,
        execution: TargetedExecution,
        memory: MemoryObservation,
        databaseDeleted: Boolean,
        durationSeconds: Double,
    ): JSONObject =
        JSONObject()
            .put("schemaVersion", 1)
            .put("pocId", "POC-SEARCH-001")
            .put("kind", "targeted-scale-query-plan-preflight")
            .put("harnessVersion", BuildConfig.POC_VERSION)
            .put("commit", BuildConfig.GIT_COMMIT)
            .put("generatedAt", Instant.now().toString())
            .put("durationSeconds", durationSeconds)
            .put("datasetId", contract.dataset.manifestId)
            .put("datasetManifestSha256", assetSha256(context, "dataset-manifest.json"))
            .put("queryManifestId", contract.queries.manifestId)
            .put("queryManifestSha256", assetSha256(context, "query-manifest.json"))
            .put("conversationCount", preparation.conversationCount)
            .put("transcriptCount", preparation.transcriptCount)
            .put("queryId", contract.queryCase.id)
            .put("expectedCount", contract.queryCase.expectedCount)
            .put("observedCount", execution.observedCount)
            .put("countMs", execution.countMs)
            .put("pageMs", execution.searchMs)
            .put("operationGuardMs", TARGET_OPERATION_MAX_MS)
            .put("observedSegmentIds", JSONArray(execution.observedIds))
            .put("plan", queryPlan(execution.plan))
            .put("binding", parameterBinding())
            .put(
                "androidEnvironment",
                BenchmarkObservationWriter.androidEnvironment(
                    context,
                    execution.sqliteVersion,
                    execution.ftsCreateSql,
                ),
            )
            .put("memory", BenchmarkObservationWriter.memory(memory))
            .put("temporaryDatabaseDeleted", databaseDeleted)
            .put(
                "passed",
                execution.plan.accepted &&
                    execution.observedCount == contract.queryCase.expectedCount &&
                    execution.countMs < TARGET_OPERATION_MAX_MS &&
                    execution.searchMs < TARGET_OPERATION_MAX_MS &&
                    databaseDeleted,
            )

    private fun queryPlan(plan: FtsQueryPlanObservation): JSONObject =
        JSONObject()
            .put("details", JSONArray(plan.details))
            .put("ftsIsDrivingTable", plan.ftsIsDrivingTable)
            .put("canonicalLookupsUseRowId", plan.canonicalLookupsUseRowId)
            .put("sourceIndexIsNotDriving", plan.sourceIndexIsNotDriving)
            .put("accepted", plan.accepted)

    private fun parameterBinding(): JSONObject =
        JSONObject()
            .put("matchBoundAsParameter", true)
            .put("sourceTypeBoundAsParameter", true)
            .put("rawValuesInterpolatedIntoSql", false)

    private fun readFrozenContract(context: Context): TargetedContract {
        val dataset = BenchmarkContracts.readDataset(context)
        val queries = BenchmarkContracts.readQueries(context)
        verifyFrozenContract(dataset, queries)
        val queryCase = queries.cases.single { it.id == SOURCE_QUERY_ID }
        return TargetedContract(
            dataset = dataset,
            queries = queries,
            queryCase = queryCase,
            request =
                SearchRequest(
                    rawQuery = queryCase.rawQuery,
                    mode = queryCase.mode,
                    filters = queryCase.filters,
                    limit = queries.resultLimit,
                ),
        )
    }

    private fun verifyFrozenContract(
        dataset: DatasetContract,
        queries: QueryCampaignContract,
    ) {
        check(dataset.conversationCount == 10_000)
        check(dataset.transcriptRowCount == 1_000_000)
        check(queries.cases.size == 61)
        check(queries.cases.count { it.latencyEligible } * queries.repetitionsPerQuery == 1_020)
    }

    private fun scalar(database: SearchPocDatabase, sql: String): String =
        database.openHelper.readableDatabase.query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun ftsCreateSql(database: SearchPocDatabase): String =
        scalar(
            database,
            "SELECT sql FROM sqlite_master " +
                "WHERE type='table' AND name='transcript_segments_fts'",
        )

    private fun verifyOperationGuards(execution: TargetedExecution) {
        check(execution.countMs < TARGET_OPERATION_MAX_MS) {
            "Targeted count took ${execution.countMs} ms; full campaign remains blocked"
        }
        check(execution.searchMs < TARGET_OPERATION_MAX_MS) {
            "Targeted page took ${execution.searchMs} ms; full campaign remains blocked"
        }
    }

    private fun assetSha256(context: Context, name: String): String =
        context.assets.open(name).use { BenchmarkDigests.sha256(it.readBytes()) }

    private fun elapsedSeconds(started: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000_000.0

    private fun deleteAndVerify(context: Context): Boolean {
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME)
        return !path.exists() &&
            !File(path.path + "-wal").exists() &&
            !File(path.path + "-shm").exists()
    }

    companion object {
        private const val TARGETED_ARGUMENT = "pocSearchTargeted"
        private const val SOURCE_QUERY_ID = "Q-SEARCH-SOURCE"
        private const val DATABASE_NAME = "poc-search-targeted-scale.db"
        private const val OUTPUT_FILE = "targeted-scale-observations.json"
        private const val TARGET_OPERATION_MAX_MS = 30_000.0
    }
}
