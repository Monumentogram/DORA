package com.monumentogram.dora.poc.search

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.poc.search.evidence.AndroidBuildIdentity
import com.monumentogram.dora.poc.search.evidence.AndroidRuntimeClassifier
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class FullBenchmarkRun(
    val generatedAt: String,
    val durationSeconds: Double,
    val datasetManifestSha256: String,
    val queryManifestSha256: String,
    val mutationManifestSha256: String,
    val dataset: DatasetContract,
    val queryCampaign: QueryCampaignContract,
    val mutationContract: MutationContract,
    val primaryPreparation: DatabasePreparation,
    val secondaryPreparation: DatabasePreparation,
    val baselineCorrectness: CorrectnessSummary,
    val latency: LatencySummary,
    val rebuild: RebuildSummary,
    val mutation: MutationSummary,
    val secondaryCorrectness: CorrectnessSummary,
    val crossBuild: CrossBuildDeterminism,
    val memory: MemoryObservation,
    val sqliteVersion: String,
    val ftsCreateSql: String,
    val temporaryDatabasesDeleted: Boolean,
)

object BenchmarkObservationWriter {
    fun write(context: Context, run: FullBenchmarkRun): File {
        val outputDirectory =
            requireNotNull(context.getExternalFilesDir(null)).resolve(OUTPUT_DIRECTORY)
        check(outputDirectory.exists() || outputDirectory.mkdirs())
        val output = outputDirectory.resolve(OUTPUT_FILE)
        output.writeText(toJson(context, run).toString(2) + "\n", Charsets.UTF_8)
        return output
    }

    private fun toJson(context: Context, run: FullBenchmarkRun): JSONObject =
        JSONObject()
            .put("schemaVersion", 1)
            .put("pocId", "POC-SEARCH-001")
            .put("harnessVersion", BuildConfig.POC_VERSION)
            .put("commit", BuildConfig.GIT_COMMIT)
            .put("generatedAt", run.generatedAt)
            .put("durationSeconds", run.durationSeconds)
            .put(
                "manifests",
                JSONObject()
                    .put("datasetId", run.dataset.manifestId)
                    .put("datasetSha256", run.datasetManifestSha256)
                    .put("queryId", run.queryCampaign.manifestId)
                    .put("querySha256", run.queryManifestSha256)
                    .put("mutationId", run.mutationContract.manifestId)
                    .put("mutationSha256", run.mutationManifestSha256),
            )
            .put(
                "campaign",
                JSONObject()
                    .put("warmupPerQuery", run.queryCampaign.warmupPerQuery)
                    .put("repetitionsPerQuery", run.queryCampaign.repetitionsPerQuery)
                    .put("safetyRepetitions", run.queryCampaign.safetyRepetitions)
                    .put("scheduleSeed", run.queryCampaign.scheduleSeed)
                    .put("resultLimit", run.queryCampaign.resultLimit)
                    .put(
                        "latencyEligibleQueryCount",
                        run.queryCampaign.cases.count { it.latencyEligible },
                    ),
            )
            .put(
                "androidEnvironment",
                androidEnvironment(context, run.sqliteVersion, run.ftsCreateSql),
            )
            .put("primaryPreparation", preparation(run.primaryPreparation))
            .put("secondaryPreparation", preparation(run.secondaryPreparation))
            .put("baselineCorrectness", correctness(run.baselineCorrectness))
            .put("secondaryCorrectness", correctness(run.secondaryCorrectness))
            .put("latency", latency(run.latency))
            .put("rebuild", rebuild(run.rebuild))
            .put("mutation", mutation(run.mutation))
            .put("crossBuildDeterminism", crossBuild(run.crossBuild))
            .put("memory", memory(run.memory))
            .put("temporaryDatabasesDeleted", run.temporaryDatabasesDeleted)

    internal fun androidEnvironment(
        context: Context,
        sqliteVersion: String,
        ftsCreateSql: String,
    ): JSONObject {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val cpuSummary =
            runCatching {
                    File("/proc/cpuinfo")
                        .useLines { lines ->
                            lines.firstOrNull {
                                it.startsWith("model name") || it.startsWith("Hardware")
                            }
                        }
                        ?.substringAfter(':')
                        ?.trim()
                }
                .getOrNull()
        return JSONObject()
            .put(
                "kind",
                AndroidRuntimeClassifier.classify(
                    AndroidBuildIdentity(
                        fingerprint = Build.FINGERPRINT,
                        manufacturer = Build.MANUFACTURER,
                        model = Build.MODEL,
                        brand = Build.BRAND,
                        device = Build.DEVICE,
                        product = Build.PRODUCT,
                        hardware = Build.HARDWARE,
                    )
                ),
            )
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("brand", Build.BRAND)
            .put("device", Build.DEVICE)
            .put("product", Build.PRODUCT)
            .put("hardware", Build.HARDWARE)
            .put("buildFingerprint", Build.FINGERPRINT)
            .put("securityPatch", Build.VERSION.SECURITY_PATCH)
            .put("androidApi", Build.VERSION.SDK_INT)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
            .put("ramMb", memoryInfo.totalMem / (1024L * 1024L))
            .put("pageSizeBytes", Os.sysconf(OsConstants._SC_PAGESIZE))
            .put("cpuSummary", cpuSummary ?: JSONObject.NULL)
            .put("sqliteVersion", sqliteVersion)
            .put("roomVersion", "2.8.4")
            .put("systemImagePackage", BuildConfig.SYSTEM_IMAGE_PACKAGE)
            .put("systemImageRevision", BuildConfig.SYSTEM_IMAGE_REVISION)
            .put("systemImageArchiveSha256", BuildConfig.SYSTEM_IMAGE_ARCHIVE_SHA256)
            .put("ftsCreateSql", ftsCreateSql)
            .put("buildType", "${BuildConfig.BUILD_TYPE}-androidTest")
            .put("monotonicClock", "SystemClock.elapsedRealtimeNanos")
    }

    internal fun preparation(value: DatabasePreparation): JSONObject =
        JSONObject()
            .put("emptyDatabaseCreationMs", value.emptyDatabaseCreationMs)
            .put("conversationInsertMs", value.conversationInsertMs)
            .put("transcriptInsertMs", value.transcriptInsertMs)
            .put("indexBuildMs", value.indexBuildMs)
            .put("checkpointCompactMs", value.checkpointCompactMs)
            .put("logicalDigestReadMs", value.logicalDigestReadMs)
            .put("totalPreparationMs", value.totalPreparationMs)
            .put("beforeCompact", fileSnapshot(value.beforeCompact))
            .put("afterCompact", fileSnapshot(value.afterCompact))
            .put("afterCompactDatabaseSha256", value.afterCompactDatabaseSha256)
            .put("conversationCount", value.conversationCount)
            .put("transcriptCount", value.transcriptCount)
            .put("ftsCount", value.ftsCount)
            .put("expectedLogicalDigest", value.expectedLogicalDigest)
            .put("databaseLogicalDigest", value.databaseLogicalDigest)
            .put("sqliteIntegrity", value.sqliteIntegrity)
            .put("missingCanonicalMappings", value.missingCanonicalMappings)
            .put("missingIndexRows", value.missingIndexRows)
            .put("duplicateCanonicalRows", value.duplicateCanonicalRows)

    private fun fileSnapshot(value: DatabaseFileSnapshot): JSONObject =
        JSONObject()
            .put("databaseBytes", value.databaseBytes)
            .put("walBytes", value.walBytes)
            .put("shmBytes", value.shmBytes)
            .put("totalBytes", value.totalBytes)

    internal fun correctness(value: CorrectnessSummary): JSONObject =
        JSONObject()
            .put("label", value.label)
            .put("expectedCases", value.expectedCases)
            .put("matchedCases", value.matchedCases)
            .put("compilerErrors", value.compilerErrors)
            .put("countErrors", value.countErrors)
            .put("mappingErrors", value.mappingErrors)
            .put("duplicateResultErrors", value.duplicateResultErrors)
            .put("adversarialFailures", value.adversarialFailures)
            .put("specialCharacterFailures", value.specialCharacterFailures)
            .put("failureExecutions", value.failureExecutions)
            .put("crashes", value.crashes)
            .put("canonicalRowsBefore", value.canonicalRowsBefore)
            .put("canonicalRowsAfter", value.canonicalRowsAfter)
            .put("conversationsBefore", value.conversationsBefore)
            .put("conversationsAfter", value.conversationsAfter)
            .put("queryResultSha256", value.queryResultSha256)
            .put("allMatched", value.allMatched)
            .put(
                "queries",
                JSONArray().also { array ->
                    value.observations.forEach { observation ->
                        array.put(queryObservation(observation))
                    }
                },
            )

    private fun queryObservation(value: QueryObservation): JSONObject =
        JSONObject()
            .put("id", value.id)
            .put("category", value.category)
            .put("expectedStatus", value.expectedStatus)
            .put("observedStatus", value.observedStatus)
            .put("expectedCount", value.expectedCount)
            .put("observedCount", value.observedCount ?: JSONObject.NULL)
            .put("compilerContractMatched", value.compilerContractMatched)
            .put("countMatched", value.countMatched)
            .put("mappingMatched", value.mappingMatched)
            .put("duplicateFree", value.duplicateFree)
            .put("safeExecution", value.safeExecution)
            .put("observedSegmentIds", JSONArray(value.observedSegmentIds))
            .put("errorCode", value.errorCode ?: JSONObject.NULL)

    internal fun latency(value: LatencySummary): JSONObject =
        JSONObject()
            .put("warmupOperations", value.warmupOperations)
            .put("measuredOperations", value.measuredOperations)
            .put("scheduleSeed", value.scheduleSeed)
            .put("checksum", value.checksum)
            .put("overall", distribution(value.overall))
            .put(
                "byCategory",
                JSONObject().also { categories ->
                    value.byCategory.toSortedMap().forEach { (name, stats) ->
                        categories.put(name, distribution(stats))
                    }
                },
            )

    private fun distribution(value: DistributionStats): JSONObject =
        JSONObject()
            .put("count", value.count)
            .put("minMs", value.minMs)
            .put("p50Ms", value.p50Ms)
            .put("p90Ms", value.p90Ms)
            .put("p95Ms", value.p95Ms)
            .put("p99Ms", value.p99Ms)
            .put("maxMs", value.maxMs)
            .put("meanMs", value.meanMs)
            .put("standardDeviationMs", value.standardDeviationMs)

    internal fun rebuild(value: RebuildSummary): JSONObject =
        JSONObject()
            .put("baselineIndexLogicalSha256", value.baselineIndexLogicalSha256)
            .put("baselineQueryResultSha256", value.baselineQueryResultSha256)
            .put(
                "passes",
                JSONArray().also { array ->
                    value.passes.forEach { pass ->
                        array.put(
                            JSONObject()
                                .put("id", pass.id)
                                .put("latencyMs", pass.latencyMs)
                                .put("indexLogicalSha256", pass.indexLogicalSha256)
                                .put("queryResultSha256", pass.queryResultSha256)
                                .put("queryCorrectnessPassed", pass.queryCorrectnessPassed)
                                .put("conversationCount", pass.conversationCount)
                                .put("transcriptCount", pass.transcriptCount)
                                .put("ftsCount", pass.ftsCount)
                                .put("mappingsHealthy", pass.mappingsHealthy)
                        )
                    }
                },
            )
            .put(
                "recovery",
                JSONObject()
                    .put("conversationCount", value.recovery.conversationCount)
                    .put("transcriptCount", value.recovery.transcriptCount)
                    .put(
                        "droppedIndexReturnedControlledFailure",
                        value.recovery.droppedIndexReturnedControlledFailure,
                    )
                    .put(
                        "canonicalRowsPreservedAfterDrop",
                        value.recovery.canonicalRowsPreservedAfterDrop,
                    )
                    .put("dropRebuildRecovered", value.recovery.dropRebuildRecovered)
                    .put("orphanDetected", value.recovery.orphanDetected)
                    .put("orphanRemovedByRebuild", value.recovery.orphanRemovedByRebuild)
                    .put("missingIndexRowDetected", value.recovery.missingIndexRowDetected)
                    .put(
                        "missingIndexRowAffectedSearch",
                        value.recovery.missingIndexRowAffectedSearch,
                    )
                    .put("missingIndexRowRecovered", value.recovery.missingIndexRowRecovered)
                    .put("temporaryDatabaseDeleted", value.recovery.temporaryDatabaseDeleted),
            )
            .put("finalCorrectness", correctness(value.finalCorrectness))
            .put("deterministic", value.deterministic)

    internal fun mutation(value: MutationSummary): JSONObject =
        JSONObject()
            .put("manifestId", value.manifestId)
            .put(
                "operations",
                JSONArray().also { array ->
                    value.operations.forEach { operation ->
                        array.put(
                            JSONObject()
                                .put("id", operation.id)
                                .put("type", operation.type)
                                .put("latencyMs", operation.latencyMs)
                                .put("correctnessPassed", operation.correctnessPassed)
                                .put("observations", JSONObject(operation.observations))
                        )
                    }
                },
            )
            .put("staleResultErrors", value.staleResultErrors)
            .put("mappingErrors", value.mappingErrors)
            .put("crashes", value.crashes)
            .put("finalConversationCount", value.finalConversationCount)
            .put("finalTranscriptCount", value.finalTranscriptCount)
            .put("finalFtsCount", value.finalFtsCount)
            .put("allCorrect", value.allCorrect)

    internal fun crossBuild(value: CrossBuildDeterminism): JSONObject =
        JSONObject()
            .put("primaryDatasetLogicalSha256", value.primaryDatasetLogicalSha256)
            .put("secondaryDatasetLogicalSha256", value.secondaryDatasetLogicalSha256)
            .put("primaryIndexLogicalSha256", value.primaryIndexLogicalSha256)
            .put("secondaryIndexLogicalSha256", value.secondaryIndexLogicalSha256)
            .put("primaryQueryResultSha256", value.primaryQueryResultSha256)
            .put("secondaryQueryResultSha256", value.secondaryQueryResultSha256)
            .put("countsMatched", value.countsMatched)
            .put("logicalDatasetMatched", value.logicalDatasetMatched)
            .put("logicalIndexMatched", value.logicalIndexMatched)
            .put("queryResultsMatched", value.queryResultsMatched)
            .put("deterministic", value.deterministic)

    internal fun memory(value: MemoryObservation): JSONObject =
        JSONObject()
            .put("peakPssMb", value.peakPssMb)
            .put("peakNativeHeapMb", value.peakNativeHeapMb)
            .put("peakManagedHeapMb", value.peakManagedHeapMb)
            .put("peakRssMb", value.peakRssMb ?: JSONObject.NULL)
            .put("sampleCount", value.sampleCount)

    const val OUTPUT_DIRECTORY: String = "poc-search-001"
    const val OUTPUT_FILE: String = "benchmark-observations.json"
}
