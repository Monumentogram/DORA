package com.monumentogram.dora.poc.search

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class BenchmarkCheckpointRun(
    val phase: String,
    val generatedAt: String,
    val durationSeconds: Double,
    val datasetManifestSha256: String,
    val queryManifestSha256: String,
    val mutationManifestSha256: String,
    val dataset: DatasetContract,
    val queryCampaign: QueryCampaignContract,
    val mutationContract: MutationContract,
    val preparation: DatabasePreparation,
    val correctness: CorrectnessSummary,
    val indexLogicalSha256: String,
    val latency: LatencySummary? = null,
    val rebuild: RebuildSummary? = null,
    val mutation: MutationSummary? = null,
    val queryPlan: FtsQueryPlanObservation? = null,
    val memory: MemoryObservation,
    val sqliteVersion: String,
    val ftsCreateSql: String,
    val temporaryDatabaseDeleted: Boolean,
)

object BenchmarkCheckpointWriter {
    fun write(context: Context, run: BenchmarkCheckpointRun): File {
        require(run.phase in PHASES)
        val outputDirectory = context.filesDir.resolve(BenchmarkObservationWriter.OUTPUT_DIRECTORY)
        check(outputDirectory.exists() || outputDirectory.mkdirs())
        val output = outputDirectory.resolve("benchmark-${run.phase}-checkpoint.json")
        output.writeText(toJson(context, run).toString(2) + "\n", Charsets.UTF_8)
        return output
    }

    private fun toJson(context: Context, run: BenchmarkCheckpointRun): JSONObject =
        addPhaseData(baseJson(context, run), run)

    private fun baseJson(context: Context, run: BenchmarkCheckpointRun): JSONObject =
        JSONObject()
            .put("checkpointSchemaVersion", 1)
            .put("pocId", "POC-SEARCH-001")
            .put("checkpoint", run.phase)
            .put("completedPhases", JSONArray(listOf(run.phase)))
            .put("harnessVersion", BuildConfig.POC_VERSION)
            .put("commit", BuildConfig.GIT_COMMIT)
            .put("generatedAt", run.generatedAt)
            .put("durationSeconds", run.durationSeconds)
            .put("manifests", manifests(run))
            .put("campaign", campaign(run.queryCampaign))
            .put(
                "androidEnvironment",
                BenchmarkObservationWriter.androidEnvironment(
                    context,
                    run.sqliteVersion,
                    run.ftsCreateSql,
                ),
            )
            .put("memory", BenchmarkObservationWriter.memory(run.memory))
            .put("temporaryDatabaseDeleted", run.temporaryDatabaseDeleted)

    private fun manifests(run: BenchmarkCheckpointRun): JSONObject =
        JSONObject()
            .put("datasetId", run.dataset.manifestId)
            .put("datasetSha256", run.datasetManifestSha256)
            .put("queryId", run.queryCampaign.manifestId)
            .put("querySha256", run.queryManifestSha256)
            .put("mutationId", run.mutationContract.manifestId)
            .put("mutationSha256", run.mutationManifestSha256)

    private fun campaign(value: QueryCampaignContract): JSONObject =
        JSONObject()
            .put("warmupPerQuery", value.warmupPerQuery)
            .put("repetitionsPerQuery", value.repetitionsPerQuery)
            .put("safetyRepetitions", value.safetyRepetitions)
            .put("scheduleSeed", value.scheduleSeed)
            .put("resultLimit", value.resultLimit)
            .put("latencyEligibleQueryCount", value.cases.count { it.latencyEligible })

    private fun addPhaseData(result: JSONObject, run: BenchmarkCheckpointRun): JSONObject {
        when (run.phase) {
            "query" ->
                result
                    .put(
                        "primaryPreparation",
                        BenchmarkObservationWriter.preparation(run.preparation),
                    )
                    .put(
                        "baselineCorrectness",
                        BenchmarkObservationWriter.correctness(run.correctness),
                    )
                    .put("latency", BenchmarkObservationWriter.latency(requireNotNull(run.latency)))
                    .put("primaryIndexLogicalSha256", run.indexLogicalSha256)
                    .put("queryPlan", queryPlan(requireNotNull(run.queryPlan)))
            "rebuild" ->
                result
                    .put(
                        "rebuildPreparation",
                        BenchmarkObservationWriter.preparation(run.preparation),
                    )
                    .put(
                        "rebuildBaselineCorrectness",
                        BenchmarkObservationWriter.correctness(run.correctness),
                    )
                    .put("rebuildBaselineIndexLogicalSha256", run.indexLogicalSha256)
                    .put("rebuild", BenchmarkObservationWriter.rebuild(requireNotNull(run.rebuild)))
                    .put(
                        "mutation",
                        BenchmarkObservationWriter.mutation(requireNotNull(run.mutation)),
                    )
            "secondary" ->
                result
                    .put(
                        "secondaryPreparation",
                        BenchmarkObservationWriter.preparation(run.preparation),
                    )
                    .put(
                        "secondaryCorrectness",
                        BenchmarkObservationWriter.correctness(run.correctness),
                    )
                    .put("secondaryIndexLogicalSha256", run.indexLogicalSha256)
        }
        return result
    }

    private fun queryPlan(value: FtsQueryPlanObservation): JSONObject =
        JSONObject()
            .put("queryId", "Q-SEARCH-SOURCE")
            .put("details", JSONArray(value.details))
            .put("ftsLoopIndex", value.ftsLoopIndex)
            .put("segmentLoopIndex", value.segmentLoopIndex)
            .put("conversationLoopIndex", value.conversationLoopIndex)
            .put("ftsIsDrivingTable", value.ftsIsDrivingTable)
            .put("canonicalLookupsUseRowId", value.canonicalLookupsUseRowId)
            .put("sourceIndexIsNotDriving", value.sourceIndexIsNotDriving)
            .put("accepted", value.accepted)

    private val PHASES = setOf("query", "rebuild", "secondary")
}
