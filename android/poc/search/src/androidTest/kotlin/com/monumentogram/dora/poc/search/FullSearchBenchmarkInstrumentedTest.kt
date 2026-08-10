@file:Suppress("LongMethod")

package com.monumentogram.dora.poc.search

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.poc.search.db.SearchPocDatabase
import com.monumentogram.dora.poc.search.query.SearchRepository
import java.io.File
import java.time.Instant
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullSearchBenchmarkInstrumentedTest {
    @Test
    fun runFrozenReferenceCampaign() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(
            "Full 10k/1M benchmark runs only through the explicit full workflow",
            InstrumentationRegistry.getArguments().getString(FULL_RUN_ARGUMENT) == "true",
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outputContext = instrumentation.context
        val dataset = BenchmarkContracts.readDataset(context)
        val queries = BenchmarkContracts.readQueries(context)
        val mutations = BenchmarkContracts.readMutations(context)
        verifyFrozenCampaign(dataset, queries, mutations)
        val memorySampler = MemorySampler()
        val started = SystemClock.elapsedRealtimeNanos()
        var primary: OpenReferenceDatabase? = null
        var secondary: OpenReferenceDatabase? = null

        try {
            memorySampler.sample()
            val primaryOpen =
                ReferenceDatabaseBuilder(context, dataset, memorySampler).build(PRIMARY_DATABASE)
            primary = primaryOpen
            verifyPreparation(primaryOpen.preparation, dataset)
            val primaryRepository = SearchRepository(primaryOpen.database.searchDao())
            val sqliteVersion = scalar(primaryOpen.database, "SELECT sqlite_version()")
            val ftsCreateSql =
                scalar(
                    primaryOpen.database,
                    "SELECT sql FROM sqlite_master " +
                        "WHERE type='table' AND name='transcript_segments_fts'",
                )

            val campaignRunner =
                QueryCampaignRunner(primaryRepository, primaryOpen.database.searchDao(), queries)
            val baselineCorrectness = campaignRunner.runCorrectness("reference-before-measurement")
            memorySampler.sample()
            val latency = campaignRunner.runLatency()
            memorySampler.sample()
            val rebuild =
                RebuildRunner(primaryOpen.database, primaryRepository, queries)
                    .run(baselineCorrectness)
            memorySampler.sample()
            val mutation =
                MutationRunner(
                        primaryOpen.database,
                        primaryRepository,
                        com.monumentogram.dora.poc.search.db.FtsIndexManager(primaryOpen.database),
                        mutations,
                    )
                    .run()
            memorySampler.sample()

            val secondaryOpen =
                ReferenceDatabaseBuilder(context, dataset, memorySampler).build(SECONDARY_DATABASE)
            secondary = secondaryOpen
            verifyPreparation(secondaryOpen.preparation, dataset)
            val secondaryRepository = SearchRepository(secondaryOpen.database.searchDao())
            val secondaryCorrectness =
                QueryCampaignRunner(
                        secondaryRepository,
                        secondaryOpen.database.searchDao(),
                        queries,
                    )
                    .runCorrectness("independent-second-build")
            val secondaryIndexDigest = RebuildRunner.logicalIndexDigest(secondaryOpen.database)
            val crossBuild =
                CrossBuildDeterminism(
                    primaryDatasetLogicalSha256 = primaryOpen.preparation.databaseLogicalDigest,
                    secondaryDatasetLogicalSha256 = secondaryOpen.preparation.databaseLogicalDigest,
                    primaryIndexLogicalSha256 = rebuild.baselineIndexLogicalSha256,
                    secondaryIndexLogicalSha256 = secondaryIndexDigest,
                    primaryQueryResultSha256 = baselineCorrectness.queryResultSha256,
                    secondaryQueryResultSha256 = secondaryCorrectness.queryResultSha256,
                    countsMatched =
                        primaryOpen.preparation.conversationCount ==
                            secondaryOpen.preparation.conversationCount &&
                            primaryOpen.preparation.transcriptCount ==
                                secondaryOpen.preparation.transcriptCount &&
                            primaryOpen.preparation.ftsCount == secondaryOpen.preparation.ftsCount,
                    logicalDatasetMatched =
                        primaryOpen.preparation.databaseLogicalDigest ==
                            secondaryOpen.preparation.databaseLogicalDigest,
                    logicalIndexMatched =
                        rebuild.baselineIndexLogicalSha256 == secondaryIndexDigest,
                    queryResultsMatched =
                        baselineCorrectness.allMatched &&
                            secondaryCorrectness.allMatched &&
                            baselineCorrectness.queryResultSha256 ==
                                secondaryCorrectness.queryResultSha256,
                )
            memorySampler.sample()

            val primaryPreparation = primaryOpen.preparation
            val secondaryPreparation = secondaryOpen.preparation
            primaryOpen.database.close()
            secondaryOpen.database.close()
            primary = null
            secondary = null
            val databasesDeleted =
                deleteAndVerify(context, PRIMARY_DATABASE) &&
                    deleteAndVerify(context, SECONDARY_DATABASE)
            val durationSeconds = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000_000.0
            val run =
                FullBenchmarkRun(
                    generatedAt = Instant.now().toString(),
                    durationSeconds = durationSeconds,
                    datasetManifestSha256 = assetSha256(context, "dataset-manifest.json"),
                    queryManifestSha256 = assetSha256(context, "query-manifest.json"),
                    mutationManifestSha256 = assetSha256(context, "mutation-manifest.json"),
                    dataset = dataset,
                    queryCampaign = queries,
                    mutationContract = mutations,
                    primaryPreparation = primaryPreparation,
                    secondaryPreparation = secondaryPreparation,
                    baselineCorrectness = baselineCorrectness,
                    latency = latency,
                    rebuild = rebuild,
                    mutation = mutation,
                    secondaryCorrectness = secondaryCorrectness,
                    crossBuild = crossBuild,
                    memory = memorySampler.observation(),
                    sqliteVersion = sqliteVersion,
                    ftsCreateSql = ftsCreateSql,
                    temporaryDatabasesDeleted = databasesDeleted,
                )
            val output = BenchmarkObservationWriter.write(outputContext, run)
            instrumentation.sendStatus(
                0,
                Bundle().apply { putString("pocSearchOutput", output.absolutePath) },
            )
            println("POC_SEARCH_OUTPUT=${output.absolutePath}")
        } finally {
            primary?.database?.close()
            secondary?.database?.close()
            deleteAndVerify(context, PRIMARY_DATABASE)
            deleteAndVerify(context, SECONDARY_DATABASE)
        }
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

    private fun scalar(database: SearchPocDatabase, sql: String): String =
        database.openHelper.readableDatabase.query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun assetSha256(context: Context, name: String): String =
        context.assets.open(name).use { BenchmarkDigests.sha256(it.readBytes()) }

    private fun deleteAndVerify(context: Context, name: String): Boolean {
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        return !path.exists() &&
            !File(path.path + "-wal").exists() &&
            !File(path.path + "-shm").exists()
    }

    companion object {
        private const val FULL_RUN_ARGUMENT = "pocSearchFull"
        private const val PRIMARY_DATABASE = "poc-search-reference-primary.db"
        private const val SECONDARY_DATABASE = "poc-search-reference-secondary.db"
    }
}
