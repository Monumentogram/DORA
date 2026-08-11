@file:Suppress("LongMethod", "TooManyFunctions")

package com.monumentogram.dora.poc.search

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.monumentogram.dora.poc.search.evidence.AndroidBuildIdentity
import com.monumentogram.dora.poc.search.evidence.AndroidRuntimeClassifier
import java.io.File
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

object StorageUpdateGateV02Writer {
    fun write(context: Context, checkpoint: GateV02BuildCheckpoint): File =
        outputFile(context, checkpoint.config).also { output ->
            output.writeText(successJson(context, checkpoint).toString(2) + "\n", Charsets.UTF_8)
        }

    fun writeFailure(context: Context, config: GateV02RunConfig, error: Throwable): File =
        outputFile(context, config).also { output ->
            val sqliteIdentity =
                runCatching {
                        android.database.sqlite.SQLiteDatabase.create(null).use { database ->
                            val version =
                                database.rawQuery("SELECT sqlite_version()", null).use { cursor ->
                                    check(cursor.moveToFirst())
                                    cursor.getString(0)
                                }
                            val compileOptions =
                                database.rawQuery("PRAGMA compile_options", null).use { cursor ->
                                    buildList {
                                        while (cursor.moveToNext()) add(cursor.getString(0))
                                    }
                                }
                            version to compileOptions
                        }
                    }
                    .getOrDefault("unavailable" to listOf("unavailable"))
            val value =
                baseJson(context, config, sqliteIdentity.first, sqliteIdentity.second)
                    .put("checkpointStatus", "FAIL")
                    .put("complete", false)
                    .put("pairOrder", JSONArray(config.pairOrder.map(GateV02DatabaseKind::name)))
                    .put(
                        "failure",
                        JSONObject()
                            .put("code", "GATE_V02_HARNESS_EXCEPTION")
                            .put("type", error.javaClass.name)
                            .put(
                                "messageSha256",
                                BenchmarkDigests.sha256(error.message ?: error.javaClass.name),
                            ),
                    )
            output.writeText(value.toString(2) + "\n", Charsets.UTF_8)
        }

    private fun successJson(context: Context, value: GateV02BuildCheckpoint): JSONObject =
        baseJson(
                context,
                value.config,
                value.indexed.sqliteVersion,
                value.indexed.compileOptions,
            )
            .put("checkpointStatus", if (value.allCorrect) "COMPLETE" else "FAIL")
            .put("complete", true)
            .put("pairOrder", JSONArray(value.config.pairOrder.map(GateV02DatabaseKind::name)))
            .put("control", databaseObservation(value.control))
            .put("indexed", databaseObservation(value.indexed))
            .put(
                "storageDelta",
                JSONObject()
                    .put("indexIncrementalBytes", value.incrementalBytes)
                    .put("indexOverheadRatio", value.overheadRatio)
                    .put("indexOverheadBytesPerSegment", value.overheadBytesPerSegment),
            )
            .put("storageAndPairingCorrect", value.storageAndPairingCorrect)
            .put("allCorrect", value.allCorrect)

    private fun baseJson(
        context: Context,
        config: GateV02RunConfig,
        sqliteVersion: String,
        compileOptions: List<String>,
    ): JSONObject =
        JSONObject()
            .put("checkpointSchemaVersion", 2)
            .put("pocId", "POC-SEARCH-001")
            .put("gateSetVersion", StorageUpdateGateV02Contract.GATE_SET_VERSION)
            .put("gateSetSha256", "sha256:${BuildConfig.GATE_V02_SHA256}")
            .put("selectedOptionId", BuildConfig.GATE_V02_SELECTED_OPTION)
            .put("benchmarkExecutionAllowedAtBuild", BuildConfig.GATE_V02_EXECUTION_ALLOWED)
            .put("harnessVersion", BuildConfig.POC_VERSION)
            .put("commit", BuildConfig.GIT_COMMIT)
            .put("generatedAt", Instant.now().toString())
            .put("formalGateEvidence", !config.smokeOnly)
            .put("config", configJson(config))
            .put("environment", environment(context, config, sqliteVersion, compileOptions))

    private fun configJson(value: GateV02RunConfig): JSONObject =
        JSONObject()
            .put("profileId", value.profileId)
            .put("freshBuildOrdinal", value.freshBuildOrdinal)
            .put("conversationCount", value.conversationCount)
            .put("transcriptSegmentCount", value.transcriptSegmentCount)
            .put("segmentsPerConversation", value.segmentsPerConversation)
            .put("warmupsPerClass", value.warmupsPerClass)
            .put("measuredOperationsPerClass", value.measuredOperationsPerClass)
            .put("smokeOnly", value.smokeOnly)
            .put("compilationMode", value.compilationMode)
            .put("cooldownMinutesBeforeFreshBuild", value.cooldownMinutesBeforeFreshBuild)

    private fun databaseObservation(value: GateV02DatabaseObservation): JSONObject =
        JSONObject()
            .put("kind", value.kind.name)
            .put("storage", storage(value.storage))
            .put(
                "operations",
                JSONArray().also { array ->
                    value.operations.forEach { array.put(operationSamples(it)) }
                },
            )
            .put("sqliteVersion", value.sqliteVersion)
            .put("compileOptions", JSONArray(value.compileOptions))
            .put("finalIntegrityCheck", value.finalIntegrityCheck)
            .put("finalConversationCount", value.finalConversationCount)
            .put("finalTranscriptSegmentCount", value.finalTranscriptSegmentCount)
            .put("finalFtsRowCount", value.finalFtsRowCount ?: JSONObject.NULL)
            .put(
                "finalMissingCanonicalMappings",
                value.finalMissingCanonicalMappings ?: JSONObject.NULL,
            )
            .put("finalMissingIndexRows", value.finalMissingIndexRows ?: JSONObject.NULL)
            .put("deletedAfterRun", value.deletedAfterRun)

    private fun storage(value: GateV02NormalizedStorage): JSONObject =
        JSONObject()
            .put("mainDatabaseBytes", value.mainDatabaseBytes)
            .put("pageCount", value.pageCount)
            .put("pageSizeBytes", value.pageSizeBytes)
            .put("walBytesAfterClose", value.walBytesAfterClose)
            .put("shmBytesAfterClose", value.shmBytesAfterClose)
            .put("integrityCheck", value.integrityCheck)
            .put("conversationCount", value.conversationCount)
            .put("transcriptSegmentCount", value.transcriptSegmentCount)
            .put("ftsRowCount", value.ftsRowCount ?: JSONObject.NULL)
            .put("canonicalLogicalSha256", value.canonicalLogicalSha256)
            .put("missingCanonicalMappings", value.missingCanonicalMappings ?: JSONObject.NULL)
            .put("missingIndexRows", value.missingIndexRows ?: JSONObject.NULL)
            .put("firstCheckpointBusy", value.firstCheckpointBusy)
            .put("secondCheckpointBusy", value.secondCheckpointBusy)
            .put("mainFileMatchesPageGeometry", value.mainFileMatchesPageGeometry)
            .put("transientFilesCleared", value.transientFilesCleared)
            .put("freeStorageBytesAfterNormalization", value.freeStorageBytesAfterNormalization)

    private fun operationSamples(value: GateV02OperationSamples): JSONObject =
        JSONObject()
            .put("operationClass", value.operationClass.id)
            .put("group", value.operationClass.group)
            .put("cardinality", value.operationClass.cardinality)
            .put("warmupSamples", samples(value.warmupSamples))
            .put("measuredSamples", samples(value.measuredSamples))

    private fun samples(values: List<GateV02OperationSample>): JSONArray =
        JSONArray().also { array ->
            values.forEach { value ->
                array.put(
                    JSONObject()
                        .put("commitNanos", value.commitNanos)
                        .put("visibilityNanos", value.visibilityNanos ?: JSONObject.NULL)
                        .put("correctnessPassed", value.correctnessPassed)
                        .put("staleSuccessfulResponse", value.staleSuccessfulResponse)
                        .put("crashed", value.crashed)
                )
            }
        }

    private fun environment(
        context: Context,
        config: GateV02RunConfig,
        sqliteVersion: String,
        compileOptions: List<String>,
    ): JSONObject {
        val runtimeKind =
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
            )
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) level * 100.0 / scale else null
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return JSONObject()
            .put("kind", runtimeKind)
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
            .put("sqliteVersion", sqliteVersion)
            .put("sqliteCompileOptions", JSONArray(compileOptions))
            .put("buildType", BuildConfig.BUILD_TYPE)
            .put("applicationDebuggable", debuggable)
            .put("profileableByShellDeclared", true)
            .put("compilationMode", config.compilationMode)
            .put("monotonicClock", "SystemClock.elapsedRealtimeNanos")
            .put(
                "thermalStatus",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    powerManager.currentThermalStatus
                } else {
                    -1
                },
            )
            .put("screenInteractive", powerManager.isInteractive)
            .put("batteryPercent", batteryPercent ?: JSONObject.NULL)
            .put("plugged", plugged != 0)
            .put(
                "airplaneMode",
                Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.AIRPLANE_MODE_ON,
                    0,
                ) == 1,
            )
            .put(
                "screenBrightnessRaw",
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    -1,
                ),
            )
            .put(
                "windowAnimationScale",
                globalFloat(context, Settings.Global.WINDOW_ANIMATION_SCALE),
            )
            .put(
                "transitionAnimationScale",
                globalFloat(context, Settings.Global.TRANSITION_ANIMATION_SCALE),
            )
            .put(
                "animatorDurationScale",
                globalFloat(context, Settings.Global.ANIMATOR_DURATION_SCALE),
            )
            .put(
                "configuredEmulatorSystemImageReference",
                JSONObject()
                    .put("imageId", BuildConfig.SYSTEM_IMAGE_PACKAGE)
                    .put("imageRevision", BuildConfig.SYSTEM_IMAGE_REVISION)
                    .put(
                        "imageArchiveSha256",
                        "sha256:${BuildConfig.SYSTEM_IMAGE_ARCHIVE_SHA256}",
                    )
                    .put("appliesOnlyWhenRuntimeIdentityMatches", true),
            )
    }

    private fun globalFloat(context: Context, name: String): Double =
        Settings.Global.getFloat(context.contentResolver, name, -1f).toDouble()

    private fun outputFile(context: Context, config: GateV02RunConfig): File {
        val directory = context.filesDir.resolve(BenchmarkObservationWriter.OUTPUT_DIRECTORY)
        check(directory.exists() || directory.mkdirs())
        return directory.resolve(
            "gate-v02-${config.profileId.lowercase()}-build-${config.freshBuildOrdinal}-checkpoint.json"
        )
    }
}
