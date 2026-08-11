@file:Suppress("LongMethod")

package com.monumentogram.dora.poc.search

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.poc.search.db.FtsIndexManager
import com.monumentogram.dora.poc.search.db.SearchPocDatabase
import com.monumentogram.dora.poc.search.query.SearchRepository
import com.monumentogram.dora.poc.search.query.SearchRequest
import java.io.File
import java.time.Instant
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

private data class FrozenSearchCampaign(
    val dataset: DatasetContract,
    val queries: QueryCampaignContract,
    val mutations: MutationContract,
    val datasetManifestSha256: String,
    val queryManifestSha256: String,
    val mutationManifestSha256: String,
)

@RunWith(AndroidJUnit4::class)
class FullSearchBenchmarkInstrumentedTest {
    @Test
    fun runFrozenQueryPhase() {
        requirePhase(QUERY_PHASE)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val campaign = readFrozenCampaign(context)
        val memorySampler = MemorySampler()
        val started = SystemClock.elapsedRealtimeNanos()
        var opened: OpenReferenceDatabase? = null

        try {
            BenchmarkProgress.report("query checkpoint: primary reference build started")
            val primary =
                ReferenceDatabaseBuilder(context, campaign.dataset, memorySampler)
                    .build(QUERY_DATABASE)
            opened = primary
            verifyPreparation(primary.preparation, campaign.dataset)
            val repository = SearchRepository(primary.database.searchDao())
            val sourceCase = campaign.queries.cases.single { it.id == SOURCE_QUERY_ID }
            val sourceRequest = request(sourceCase, campaign.queries.resultLimit)
            val queryPlan = FtsQueryPlanPolicy.inspect(repository.explainCountPlan(sourceRequest))
            queryPlan.details.forEach { detail ->
                BenchmarkProgress.report("query checkpoint: EXPLAIN $detail")
            }
            check(queryPlan.accepted) { queryPlan.details.joinToString(" | ") }

            BenchmarkProgress.report("query checkpoint: baseline correctness started")
            val baseline =
                QueryCampaignRunner(repository, primary.database.searchDao(), campaign.queries)
                    .runCorrectness("reference-before-measurement")
            BenchmarkProgress.report("query checkpoint: baseline correctness complete")
            memorySampler.sample()
            BenchmarkProgress.report("query checkpoint: frozen latency campaign started")
            val latency =
                QueryCampaignRunner(repository, primary.database.searchDao(), campaign.queries)
                    .runLatency()
            BenchmarkProgress.report("query checkpoint: frozen latency campaign complete")
            val indexDigest = RebuildRunner.logicalIndexDigest(primary.database)
            val sqliteVersion = scalar(primary.database, "SELECT sqlite_version()")
            val ftsCreateSql = ftsCreateSql(primary.database)
            val preparation = primary.preparation
            memorySampler.sample()

            primary.database.close()
            opened = null
            val deleted = deleteAndVerify(context, QUERY_DATABASE)
            val checkpoint =
                BenchmarkCheckpointRun(
                    phase = QUERY_PHASE,
                    generatedAt = Instant.now().toString(),
                    durationSeconds = elapsedSeconds(started),
                    datasetManifestSha256 = campaign.datasetManifestSha256,
                    queryManifestSha256 = campaign.queryManifestSha256,
                    mutationManifestSha256 = campaign.mutationManifestSha256,
                    dataset = campaign.dataset,
                    queryCampaign = campaign.queries,
                    mutationContract = campaign.mutations,
                    preparation = preparation,
                    correctness = baseline,
                    indexLogicalSha256 = indexDigest,
                    latency = latency,
                    queryPlan = queryPlan,
                    memory = memorySampler.observation(),
                    sqliteVersion = sqliteVersion,
                    ftsCreateSql = ftsCreateSql,
                    temporaryDatabaseDeleted = deleted,
                )
            writeCheckpoint(instrumentation, checkpoint)
            check(deleted)
        } finally {
            opened?.database?.close()
            deleteAndVerify(context, QUERY_DATABASE)
        }
    }

    @Test
    fun runFrozenRebuildMutationPhase() {
        requirePhase(REBUILD_PHASE)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val campaign = readFrozenCampaign(context)
        val memorySampler = MemorySampler()
        val started = SystemClock.elapsedRealtimeNanos()
        var opened: OpenReferenceDatabase? = null

        try {
            BenchmarkProgress.report("rebuild checkpoint: reference build started")
            val primary =
                ReferenceDatabaseBuilder(context, campaign.dataset, memorySampler)
                    .build(REBUILD_DATABASE)
            opened = primary
            verifyPreparation(primary.preparation, campaign.dataset)
            val repository = SearchRepository(primary.database.searchDao())
            val runner =
                QueryCampaignRunner(repository, primary.database.searchDao(), campaign.queries)
            BenchmarkProgress.report("rebuild checkpoint: baseline correctness started")
            val baseline = runner.runCorrectness("rebuild-independent-baseline")
            BenchmarkProgress.report("rebuild checkpoint: baseline correctness complete")
            val baselineIndexDigest = RebuildRunner.logicalIndexDigest(primary.database)
            memorySampler.sample()
            BenchmarkProgress.report("rebuild checkpoint: two full-scale rebuilds started")
            val rebuild =
                RebuildRunner(context, primary.database, repository, campaign.queries).run(baseline)
            BenchmarkProgress.report("rebuild checkpoint: rebuild and recovery complete")
            memorySampler.sample()
            BenchmarkProgress.report("rebuild checkpoint: mutation campaign started")
            val mutation =
                MutationRunner(
                        primary.database,
                        repository,
                        FtsIndexManager(primary.database),
                        campaign.mutations,
                    )
                    .run()
            BenchmarkProgress.report("rebuild checkpoint: mutation campaign complete")
            val sqliteVersion = scalar(primary.database, "SELECT sqlite_version()")
            val ftsCreateSql = ftsCreateSql(primary.database)
            val preparation = primary.preparation
            memorySampler.sample()

            primary.database.close()
            opened = null
            val deleted = deleteAndVerify(context, REBUILD_DATABASE)
            val checkpoint =
                BenchmarkCheckpointRun(
                    phase = REBUILD_PHASE,
                    generatedAt = Instant.now().toString(),
                    durationSeconds = elapsedSeconds(started),
                    datasetManifestSha256 = campaign.datasetManifestSha256,
                    queryManifestSha256 = campaign.queryManifestSha256,
                    mutationManifestSha256 = campaign.mutationManifestSha256,
                    dataset = campaign.dataset,
                    queryCampaign = campaign.queries,
                    mutationContract = campaign.mutations,
                    preparation = preparation,
                    correctness = baseline,
                    indexLogicalSha256 = baselineIndexDigest,
                    rebuild = rebuild,
                    mutation = mutation,
                    memory = memorySampler.observation(),
                    sqliteVersion = sqliteVersion,
                    ftsCreateSql = ftsCreateSql,
                    temporaryDatabaseDeleted = deleted,
                )
            writeCheckpoint(instrumentation, checkpoint)
            check(deleted)
        } finally {
            opened?.database?.close()
            deleteAndVerify(context, REBUILD_DATABASE)
        }
    }

    @Test
    fun runFrozenSecondaryPhase() {
        requirePhase(SECONDARY_PHASE)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val campaign = readFrozenCampaign(context)
        val memorySampler = MemorySampler()
        val started = SystemClock.elapsedRealtimeNanos()
        var opened: OpenReferenceDatabase? = null

        try {
            BenchmarkProgress.report("secondary checkpoint: independent reference build started")
            val secondary =
                ReferenceDatabaseBuilder(context, campaign.dataset, memorySampler)
                    .build(SECONDARY_DATABASE)
            opened = secondary
            verifyPreparation(secondary.preparation, campaign.dataset)
            val repository = SearchRepository(secondary.database.searchDao())
            val correctness =
                QueryCampaignRunner(repository, secondary.database.searchDao(), campaign.queries)
                    .runCorrectness("independent-second-build")
            BenchmarkProgress.report("secondary checkpoint: correctness complete")
            val indexDigest = RebuildRunner.logicalIndexDigest(secondary.database)
            val sqliteVersion = scalar(secondary.database, "SELECT sqlite_version()")
            val ftsCreateSql = ftsCreateSql(secondary.database)
            val preparation = secondary.preparation
            memorySampler.sample()

            secondary.database.close()
            opened = null
            val deleted = deleteAndVerify(context, SECONDARY_DATABASE)
            val checkpoint =
                BenchmarkCheckpointRun(
                    phase = SECONDARY_PHASE,
                    generatedAt = Instant.now().toString(),
                    durationSeconds = elapsedSeconds(started),
                    datasetManifestSha256 = campaign.datasetManifestSha256,
                    queryManifestSha256 = campaign.queryManifestSha256,
                    mutationManifestSha256 = campaign.mutationManifestSha256,
                    dataset = campaign.dataset,
                    queryCampaign = campaign.queries,
                    mutationContract = campaign.mutations,
                    preparation = preparation,
                    correctness = correctness,
                    indexLogicalSha256 = indexDigest,
                    memory = memorySampler.observation(),
                    sqliteVersion = sqliteVersion,
                    ftsCreateSql = ftsCreateSql,
                    temporaryDatabaseDeleted = deleted,
                )
            writeCheckpoint(instrumentation, checkpoint)
            check(deleted)
        } finally {
            opened?.database?.close()
            deleteAndVerify(context, SECONDARY_DATABASE)
        }
    }

    private fun requirePhase(phase: String) {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Full benchmark phases run only through the explicit commit-bound workflow",
            arguments.getString(FULL_RUN_ARGUMENT) == "true" &&
                arguments.getString(PHASE_ARGUMENT) == phase,
        )
    }

    private fun readFrozenCampaign(context: Context): FrozenSearchCampaign {
        val dataset = BenchmarkContracts.readDataset(context)
        val queries = BenchmarkContracts.readQueries(context)
        val mutations = BenchmarkContracts.readMutations(context)
        verifyFrozenCampaign(dataset, queries, mutations)
        return FrozenSearchCampaign(
            dataset = dataset,
            queries = queries,
            mutations = mutations,
            datasetManifestSha256 = assetSha256(context, "dataset-manifest.json"),
            queryManifestSha256 = assetSha256(context, "query-manifest.json"),
            mutationManifestSha256 = assetSha256(context, "mutation-manifest.json"),
        )
    }

    private fun verifyFrozenCampaign(
        dataset: DatasetContract,
        queries: QueryCampaignContract,
        mutations: MutationContract,
    ) {
        check(dataset.manifestId == "poc-search-001-reference-dataset-v1")
        check(dataset.conversationCount == 10_000)
        check(dataset.transcriptRowCount == 1_000_000)
        check(queries.manifestId == "poc-search-001-query-campaign-v1")
        check(queries.cases.size == 61)
        check(queries.cases.count { it.latencyEligible } == 34)
        check(queries.cases.count { it.latencyEligible } * queries.repetitionsPerQuery == 1_020)
        check(mutations.manifestId == "poc-search-001-mutations-v1")
        check(mutations.operations.length() == 5)
    }

    private fun verifyPreparation(preparation: DatabasePreparation, dataset: DatasetContract) {
        check(preparation.conversationCount == dataset.conversationCount.toLong())
        check(preparation.transcriptCount == dataset.transcriptRowCount.toLong())
        check(preparation.ftsCount == dataset.transcriptRowCount.toLong())
        check(preparation.expectedLogicalDigest == dataset.logicalDatasetSha256)
        check(preparation.databaseLogicalDigest == dataset.logicalDatasetSha256)
        check(preparation.sqliteIntegrity == "ok")
        check(preparation.missingCanonicalMappings == 0L)
        check(preparation.missingIndexRows == 0L)
        check(preparation.duplicateCanonicalRows == 0L)
    }

    private fun request(queryCase: QueryCase, limit: Int): SearchRequest =
        SearchRequest(
            rawQuery = queryCase.rawQuery,
            mode = queryCase.mode,
            filters = queryCase.filters,
            limit = limit,
        )

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

    private fun assetSha256(context: Context, name: String): String =
        context.assets.open(name).use { BenchmarkDigests.sha256(it.readBytes()) }

    private fun elapsedSeconds(started: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000_000.0

    private fun writeCheckpoint(
        instrumentation: android.app.Instrumentation,
        checkpoint: BenchmarkCheckpointRun,
    ) {
        val output = BenchmarkCheckpointWriter.write(instrumentation.context, checkpoint)
        BenchmarkProgress.report("${checkpoint.phase} checkpoint: sanitized checkpoint written")
        instrumentation.sendStatus(
            0,
            Bundle().apply { putString("pocSearchCheckpoint", output.absolutePath) },
        )
        println("POC_SEARCH_CHECKPOINT=${output.absolutePath}")
    }

    private fun deleteAndVerify(context: Context, name: String): Boolean {
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        return !path.exists() &&
            !File(path.path + "-wal").exists() &&
            !File(path.path + "-shm").exists()
    }

    companion object {
        private const val FULL_RUN_ARGUMENT = "pocSearchFull"
        private const val PHASE_ARGUMENT = "pocSearchPhase"
        private const val SOURCE_QUERY_ID = "Q-SEARCH-SOURCE"
        private const val QUERY_PHASE = "query"
        private const val REBUILD_PHASE = "rebuild"
        private const val SECONDARY_PHASE = "secondary"
        private const val QUERY_DATABASE = "poc-search-reference-query.db"
        private const val REBUILD_DATABASE = "poc-search-reference-rebuild.db"
        private const val SECONDARY_DATABASE = "poc-search-reference-secondary.db"
    }
}
