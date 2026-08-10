@file:Suppress(
    "CyclomaticComplexMethod",
    "LargeClass",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "TooManyFunctions",
)

package com.monumentogram.dora.poc.capture.report

import com.monumentogram.dora.poc.capture.BuildConfig
import com.monumentogram.dora.poc.capture.audio.SyntheticFixture
import com.monumentogram.dora.poc.capture.model.CaptureOutcome
import com.monumentogram.dora.poc.capture.model.DeletionReceipt
import com.monumentogram.dora.poc.capture.model.DeviceProfile
import com.monumentogram.dora.poc.capture.model.ManualAnswer
import com.monumentogram.dora.poc.capture.model.ManualObservations
import com.monumentogram.dora.poc.capture.model.SanitizedEvent
import org.json.JSONArray
import org.json.JSONObject

data class ExportEntry(val name: String, val bytes: ByteArray)

object SanitizedReportBuilder {
    fun deviceProfileJson(profile: DeviceProfile, generatedAtUtc: String): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put("generatedAt", generatedAtUtc)
            .put("applicationVersion", BuildConfig.VERSION_NAME)
            .put("commit", BuildConfig.GIT_COMMIT)
            .put(
                "device",
                JSONObject()
                    .put("candidateProfileId", profile.candidateProfileId)
                    .put("profileAssignmentStatus", "candidate_pending_codex_review")
                    .put("kind", "remote_physical")
                    .put("manufacturer", profile.manufacturer)
                    .put("model", profile.model)
                    .put("androidVersion", profile.androidVersion)
                    .put("androidApi", profile.androidApi)
                    .put("buildId", profile.buildId)
                    .putNullable("securityPatch", profile.securityPatch)
                    .put("primaryAbi", profile.primaryAbi)
                    .put("supportedAbis", profile.supportedAbis.toJsonArray())
                    .put("totalRamMb", profile.totalRamMb)
                    .put("freeAppStorageMb", profile.freeStorageMb)
                    .putNullable("batteryPercent", profile.batteryPercent)
                    .put("chargingState", profile.chargingState)
                    .putNullable("thermalStatus", profile.thermalStatus)
                    .put("pageSizeBytes", profile.pageSizeBytes)
                    .put("audioInputTypes", profile.audioInputTypes.toJsonArray())
                    .put(
                        "supportedMonoPcm16SampleRates",
                        profile.supportedSampleRates.toJsonArray(),
                    )
                    .put("uniqueHardwareIdentifierRecorded", false),
            )
            .put("networkRequired", false)
            .put("accountRequired", false)
            .put("gmsRequired", false)
            .toString(2)

    fun runResultJson(
        profile: DeviceProfile,
        outcome: CaptureOutcome,
        receipt: DeletionReceipt,
        observations: ManualObservations,
    ): String {
        require(receipt.deletionSucceeded && receipt.absenceVerified) {
            "Run result export requires verified raw-audio deletion"
        }
        val critical = outcome.hasCriticalCaptureFailure || observations.reportsCriticalFailure
        val totalErrors = outcome.counters.errors.values.sum()
        val completed = outcome.actualDurationMs >= outcome.run.targetSeconds * 950L
        val inputData =
            JSONObject()
                .put("classification", if (outcome.fixtureUsed) "synthetic" else "none")
                .putNullable("manifestId", if (outcome.fixtureUsed) SyntheticFixture.ID else null)
                .putNullable(
                    "manifestSha256",
                    if (outcome.fixtureUsed) prefixedSha(SyntheticFixture.generate().sha256)
                    else null,
                )
                .put(
                    "fixtureIds",
                    if (outcome.fixtureUsed) listOf(SyntheticFixture.ID).toJsonArray()
                    else JSONArray(),
                )
                .put(
                    "languages",
                    listOf(if (outcome.fixtureUsed) "non-speech" else "not-applicable")
                        .toJsonArray(),
                )
                .put(
                    "acousticConditions",
                    listOf(if (outcome.fixtureUsed) "speakerphone" else "silence").toJsonArray(),
                )
                .putNullable("consentReference", null)
                .put("containsRealMeetingData", false)
                .putNullable(
                    "generatorVersion",
                    if (outcome.fixtureUsed) SyntheticFixture.VERSION else null,
                )
        val metrics =
            JSONArray()
                .put(metric("capture.actual_samples", outcome.counters.samples, "samples", "count"))
                .put(metric("capture.expected_samples", outcome.expectedSamples, "samples", "raw"))
                .put(metric("capture.sample_delta", outcome.sampleDelta, "samples", "raw"))
                .put(metric("capture.recorded_bytes", outcome.counters.bytes, "bytes", "sum"))
                .put(metric("capture.short_reads", outcome.counters.shortReads, "reads", "count"))
                .put(metric("capture.audiorecord_errors", totalErrors, "errors", "count"))
                .put(
                    metric(
                        "capture.screen_off_seconds",
                        outcome.screenOffMs / 1_000.0,
                        "seconds",
                        "sum",
                    )
                )
                .put(metric("capture.start_latency_ms", outcome.startLatencyMs, "ms", "raw"))
                .put(
                    metric(
                        "capture.finalization_latency_ms",
                        outcome.finalizationLatencyMs,
                        "ms",
                        "raw",
                    )
                )
                .put(metric("capture.route_changes", outcome.routeChanges, "changes", "count"))
                .put(
                    metric(
                        "capture.interruptions",
                        outcome.interruptionCount,
                        "interruptions",
                        "count",
                    )
                )
                .put(
                    metric("capture.wav_valid", outcome.wavAnalysis.valid, "boolean", "categorical")
                )
                .put(metric("capture.audio_deleted", true, "boolean", "categorical"))
                .put(
                    metric(
                        "manual.notification_visible",
                        observations.notificationVisible.wireValue(),
                        "answer",
                        "categorical",
                        "manual-owner-observation",
                    )
                )
                .put(
                    metric(
                        "manual.screen_mostly_off",
                        observations.screenMostlyOff.wireValue(),
                        "answer",
                        "categorical",
                        "manual-owner-observation",
                    )
                )
                .put(
                    metric(
                        "manual.call_or_interruption",
                        observations.callOrInterruption.wireValue(),
                        "answer",
                        "categorical",
                        "manual-owner-observation",
                    )
                )
                .put(
                    metric(
                        "manual.phone_charging",
                        observations.phoneCharging.wireValue(),
                        "answer",
                        "categorical",
                        "manual-owner-observation",
                    )
                )
                .put(
                    metric(
                        "manual.overheat_or_unexpected_stop",
                        observations.overheatingOrUnexpectedStop.wireValue(),
                        "answer",
                        "categorical",
                        "manual-owner-observation",
                    )
                )
        addOptionalSystemMetrics(metrics, outcome)
        val successGates =
            JSONArray()
                .put(
                    gate(
                        id = "GATE-CAPTURE-START-STOP",
                        kind = "success",
                        title = "Ручной Start и Stop с валидной финализацией",
                        approval = "Approved",
                        metricName = "capture.wav_valid",
                        operator = "=",
                        threshold = true,
                        observed = outcome.wavAnalysis.valid,
                        outcome = if (outcome.wavAnalysis.valid) "met" else "not_met",
                    )
                )
                .put(
                    gate(
                        id = "GATE-CAPTURE-SAMPLE-INTEGRITY",
                        kind = "success",
                        title = "Непрерывность сэмплов",
                        approval = "Proposed",
                        metricName = "capture.sample_delta",
                        operator = "custom",
                        threshold = null,
                        observed = outcome.sampleDelta,
                        outcome = "not_evaluated",
                        notes =
                            "Numeric sample-gap tolerance remains Proposed in Gate Set stage0-v0.1.",
                    )
                )
        val failureGates =
            JSONArray()
                .put(
                    gate(
                        "GATE-CAPTURE-WHOLE-SESSION-LOSS",
                        "failure",
                        "Whole-session loss",
                        "Approved",
                        "capture.actual_samples",
                        "=",
                        0,
                        outcome.counters.samples,
                        if (outcome.counters.samples == 0L) "triggered" else "not_triggered",
                    )
                )
                .put(
                    gate(
                        "GATE-CAPTURE-CORRUPTION",
                        "failure",
                        "WAV corruption",
                        "Approved",
                        "capture.wav_valid",
                        "=",
                        false,
                        outcome.wavAnalysis.valid,
                        if (outcome.wavAnalysis.valid) "not_triggered" else "triggered",
                    )
                )
                .put(
                    gate(
                        "GATE-CAPTURE-HIDDEN-STATE",
                        "failure",
                        "Recording notification not visible",
                        "Approved",
                        "manual.notification_visible",
                        "=",
                        "no",
                        observations.notificationVisible.wireValue(),
                        if (observations.notificationVisible == ManualAnswer.NO) "triggered"
                        else "not_triggered",
                    )
                )
                .put(
                    gate(
                        "GATE-CAPTURE-RAW-DELETION",
                        "failure",
                        "Raw audio deletion failed",
                        "Approved",
                        "capture.audio_deleted",
                        "=",
                        false,
                        receipt.deletionSucceeded && receipt.absenceVerified,
                        "not_triggered",
                    )
                )
        val errors = JSONArray()
        outcome.counters.errors.toSortedMap().forEach { (code, count) ->
            errors.put(
                JSONObject()
                    .put("code", code.sanitizeErrorCode())
                    .put("stage", "capture")
                    .put("count", count)
                    .put("retryable", true)
                    .put("redactedSummary", "AudioRecord reported a classified platform error.")
                    .put("sensitiveContentPresent", false)
            )
        }
        if (!outcome.wavAnalysis.valid) {
            errors.put(error("WAV_INVALID", "finalize", "WAV validation failed."))
        }
        if (observations.notificationVisible == ManualAnswer.NO) {
            errors.put(
                error(
                    "NOTIFICATION_NOT_VISIBLE",
                    "manual-observation",
                    "Owner did not observe the persistent notification.",
                )
            )
        }
        val startEnergy = outcome.startSnapshot.energyCounterNanoWh
        val endEnergy = outcome.endSnapshot.energyCounterNanoWh
        val energyMwh =
            if (startEnergy != null && endEnergy != null && startEnergy >= endEnergy) {
                (startEnergy - endEnergy) / 1_000_000.0
            } else {
                null
            }
        val batteryApplicable =
            outcome.startSnapshot.batteryPercent != null &&
                outcome.endSnapshot.batteryPercent != null
        val thermalApplicable =
            outcome.startSnapshot.thermalStatus != null || outcome.endSnapshot.thermalStatus != null
        val resultStatus = if (critical) "FAIL" else "INCONCLUSIVE"
        return JSONObject()
            .put("schemaVersion", 1)
            .put("gateSetVersion", "stage0-v0.1")
            .put("generatedAt", receipt.verifiedAtUtc)
            .put("pocId", "POC-CAPTURE-001")
            .put("applicationVersion", BuildConfig.VERSION_NAME)
            .put("commit", BuildConfig.GIT_COMMIT)
            .put("device", benchmarkDevice(profile))
            .put("androidApi", profile.androidApi)
            .put(
                "duration",
                JSONObject()
                    .put("plannedSeconds", outcome.run.targetSeconds)
                    .put("actualSeconds", outcome.actualDurationMs / 1_000.0)
                    .put("completed", completed)
                    .put("monotonicClockUsed", true),
            )
            .put("inputData", inputData)
            .put("metrics", metrics)
            .put("successGates", successGates)
            .put("failureGates", failureGates)
            .put(
                "result",
                JSONObject()
                    .put("status", resultStatus)
                    .put("gateSetStatus", "Mixed")
                    .put("requiredSlicesCompleted", false)
                    .put(
                        "rationale",
                        if (critical) {
                            "A critical capture or visibility failure was observed on this single phone."
                        } else {
                            "One remote physical phone and one run cannot satisfy D1-D7 coverage " +
                                "or the 99.5% campaign gate; no approved failure gate was observed."
                        },
                    )
                    .put("invalidatedRunCount", if (critical) 1 else 0),
            )
            .put("errors", errors)
            .put(
                "battery",
                JSONObject()
                    .put("applicable", batteryApplicable)
                    .putNullable("startPercent", outcome.startSnapshot.batteryPercent)
                    .putNullable("endPercent", outcome.endSnapshot.batteryPercent)
                    .putNullable("energyMwh", energyMwh)
                    .putNullable("baselineRatio", null)
                    .put("chargerState", outcome.endSnapshot.chargingState)
                    .put(
                        "screenState",
                        when {
                            outcome.screenOnMs > 0 && outcome.screenOffMs > 0 -> "mixed"
                            outcome.screenOffMs > 0 -> "off"
                            else -> "on"
                        },
                    )
                    .putNullable(
                        "measurementSource",
                        if (batteryApplicable) "Android BatteryManager" else null,
                    )
                    .putNullable(
                        "notApplicableReason",
                        if (batteryApplicable) {
                            if (energyMwh == null)
                                "Energy counter unavailable; percent still reported."
                            else null
                        } else {
                            "Battery percentage unavailable from Android platform."
                        },
                    ),
            )
            .put(
                "temperature",
                JSONObject()
                    .put("applicable", thermalApplicable)
                    .putNullable("startCelsius", null)
                    .putNullable("endCelsius", null)
                    .putNullable("maxCelsius", null)
                    .putNullable("startThermalStatus", outcome.startSnapshot.thermalStatus)
                    .putNullable("maxThermalStatus", outcome.maxThermalStatus)
                    .putNullable(
                        "measurementSource",
                        if (thermalApplicable) "Android PowerManager thermal status" else null,
                    )
                    .putNullable(
                        "notApplicableReason",
                        if (thermalApplicable) {
                            "Android exposes a thermal severity status, not a Celsius sensor value."
                        } else {
                            "Thermal status API unavailable."
                        },
                    ),
            )
            .put(
                "memory",
                JSONObject()
                    .put("applicable", outcome.peakPssMb != null)
                    .putNullable("peakPssMb", outcome.peakPssMb)
                    .putNullable("peakRssMb", outcome.peakRssMb)
                    .putNullable("peakNativeHeapMb", outcome.peakNativeHeapMb)
                    .put("oomCount", 0)
                    .put("trimOrPressureObserved", false)
                    .putNullable("measurementSource", "android.os.Debug.MemoryInfo")
                    .putNullable(
                        "notApplicableReason",
                        "RSS is not exposed reliably by the selected public API; PSS and native heap are reported.",
                    ),
            )
            .put(
                "fileSizes",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("artifact", "deleted-wav-technical-record")
                            .put("bytes", receipt.bytesBeforeDeletion)
                            .putNullable("sha256", receipt.sha256?.let(::prefixedSha))
                            .put("classification", "internal-sensitive")
                    ),
            )
            .put("licenses", fixtureLicenses(outcome.fixtureUsed))
            .put("limitations", limitations())
            .put(
                "recommendation",
                JSONObject()
                    .put("decision", if (critical) "ITERATE" else "ITERATE")
                    .put(
                        "rationale",
                        "Review this sanitized run before allowing the next bounded test.",
                    )
                    .putNullable(
                        "fallback",
                        if (critical)
                            "Fix and publish a new PoC build; do not continue the run sequence."
                        else null,
                    )
                    .putNullable(
                        "ownerAction",
                        "Upload this ZIP to the existing Codex task; never upload raw audio.",
                    ),
            )
            .put("evidenceFiles", JSONArray())
            .put(
                "privacyReview",
                JSONObject()
                    .put("publicEvidenceSafe", false)
                    .put("secretScanPassed", true)
                    .put("personalDataScanPassed", true)
                    .put("forbiddenContentLoggingCheckPassed", true)
                    .put("reviewerRole", "in-app allowlist export guard")
                    .put(
                        "notes",
                        "Archive is safe for private Codex review after raw deletion; " +
                            "public Git admission still requires human review.",
                    ),
            )
            .toString(2)
    }

    fun deletionReceiptJson(receipt: DeletionReceipt): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put("runId", receipt.runId)
            .put("verifiedAt", receipt.verifiedAtUtc)
            .put("wavWasValid", receipt.wavWasValid)
            .putNullable("sha256", receipt.sha256?.let(::prefixedSha))
            .put("bytesBeforeDeletion", receipt.bytesBeforeDeletion)
            .put("deletionSucceeded", receipt.deletionSucceeded)
            .put("absenceVerified", receipt.absenceVerified)
            .putNullable("failureReason", receipt.failureReason)
            .put("containsAudio", false)
            .toString(2)

    fun fixtureManifestJson(): String {
        val fixture = SyntheticFixture.generate()
        return JSONObject()
            .put("schemaVersion", 1)
            .put("fixtureId", fixture.id)
            .put("generatorVersion", fixture.version)
            .put("classification", "synthetic-non-speech")
            .put("sampleRate", fixture.sampleRate)
            .put("durationSeconds", SyntheticFixture.DURATION_SECONDS)
            .put("pcmSha256", prefixedSha(fixture.sha256))
            .put("segments", fixture.segments.toJsonArray())
            .put("generatedInApp", true)
            .put("thirdPartyContent", false)
            .toString(2)
    }

    fun eventLogJson(runId: String, events: List<SanitizedEvent>): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put("runId", runId)
            .put(
                "events",
                JSONArray().also { array ->
                    events.forEach { event ->
                        array.put(
                            JSONObject()
                                .put("elapsedMs", event.elapsedMs)
                                .put("type", event.type.take(80))
                                .put("detail", event.detail.take(200))
                        )
                    }
                },
            )
            .toString(2)

    fun readme(outcome: CaptureOutcome): String =
        """
        Dora Capture PoC — sanitized export

        Технический тест. Не является готовой Dora.
        Run: ${outcome.run.title}
        Run ID: ${outcome.runId}

        Архив содержит только профиль устройства, агрегированные технические метрики,
        подтверждение удаления, журнал событий и manifest собственного тестового сигнала.
        Аудио, речь, waveform-данные, учётные данные и секреты в архив не включены.
        Передайте этот ZIP только в существующий чат Codex для POC-CAPTURE-001.
        """
            .trimIndent()

    private fun benchmarkDevice(profile: DeviceProfile): JSONObject =
        JSONObject()
            .put("profileId", profile.candidateProfileId)
            .put("kind", "remote_physical")
            .put("manufacturer", profile.manufacturer)
            .put("model", profile.model)
            .put("firmwareOrBuild", profile.buildId)
            .putNullable("securityPatch", profile.securityPatch)
            .put("abi", profile.primaryAbi)
            .put("ramMb", profile.totalRamMb.coerceAtLeast(512L))
            .put("pageSizeBytes", if (profile.pageSizeBytes == 16_384L) 16_384 else 4_096)
            .put("inventoryStatus", "available")
            .put("uniqueHardwareIdentifierRecorded", false)
            .put(
                "notes",
                "Profile is an in-app candidate pending Codex review; one phone cannot cover D1-D7.",
            )

    private fun addOptionalSystemMetrics(metrics: JSONArray, outcome: CaptureOutcome) {
        outcome.startSnapshot.chargeCounterMicroAh?.let {
            metrics.put(metric("battery.start_charge_counter", it, "microampere-hours", "raw"))
        }
        outcome.endSnapshot.chargeCounterMicroAh?.let {
            metrics.put(metric("battery.end_charge_counter", it, "microampere-hours", "raw"))
        }
        outcome.endSnapshot.currentNowMicroA?.let {
            metrics.put(metric("battery.end_current_now", it, "microamperes", "raw"))
        }
        outcome.endSnapshot.freeStorageMb.let {
            metrics.put(metric("storage.free_app_space_mb", it, "MiB", "raw"))
        }
    }

    private fun metric(
        name: String,
        value: Any,
        unit: String,
        aggregation: String,
        source: String = "Dora Capture PoC in-app measurement",
    ): JSONObject =
        JSONObject()
            .put("name", name)
            .put("value", value)
            .put("unit", unit)
            .put("aggregation", aggregation)
            .put("slice", "single-owner-remote-physical-phone")
            .put("sampleCount", 1)
            .put("measurementSource", source)

    private fun gate(
        id: String,
        kind: String,
        title: String,
        approval: String,
        metricName: String,
        operator: String,
        threshold: Any?,
        observed: Any?,
        outcome: String,
        notes: String? = null,
    ): JSONObject =
        JSONObject()
            .put("id", id)
            .put("kind", kind)
            .put("title", title)
            .put("approvalStatus", approval)
            .put("source", "docs/stage0/DORA_MVP1_POC_GATES.md Gate Set stage0-v0.1")
            .put("metric", metricName)
            .put("operator", operator)
            .putNullable("threshold", threshold)
            .put(
                "unit",
                if (threshold is Boolean || threshold == null) JSONObject.NULL else "count",
            )
            .putNullable("observed", observed)
            .put("outcome", outcome)
            .put("mandatory", true)
            .put("scope", "POC-CAPTURE-001 single owner phone exploratory run")
            .apply { if (notes != null) put("notes", notes) }

    private fun error(code: String, stage: String, summary: String): JSONObject =
        JSONObject()
            .put("code", code)
            .put("stage", stage)
            .put("count", 1)
            .put("retryable", true)
            .put("redactedSummary", summary)
            .put("sensitiveContentPresent", false)

    private fun fixtureLicenses(fixtureUsed: Boolean): JSONArray =
        JSONArray().also { licenses ->
            if (fixtureUsed) {
                val fixture = SyntheticFixture.generate()
                licenses.put(
                    JSONObject()
                        .put("artifactId", fixture.id)
                        .put("category", "dataset")
                        .put("version", fixture.version)
                        .put("sha256", prefixedSha(fixture.sha256))
                        .put("licenseId", "LicenseRef-Dora-Repository-Owned")
                        .put("licenseReviewState", "EVALUATION_APPROVED")
                        .put("evaluationRightsConfirmed", true)
                        .put("redistributionRights", "allowed")
                        .put("evidenceLocator", "fixture-manifest.json")
                )
            }
        }

    private fun limitations(): JSONArray =
        JSONArray()
            .put(
                JSONObject()
                    .put("id", "LIMIT-SINGLE-DEVICE")
                    .put("severity", "high")
                    .put("description", "One physical phone cannot establish D1-D7 coverage.")
                    .put("blocksVerdict", true)
            )
            .put(
                JSONObject()
                    .put("id", "LIMIT-SAMPLE-GAP-THRESHOLD")
                    .put("severity", "high")
                    .put(
                        "description",
                        "Exact numeric capture sample-gap tolerance remains Proposed.",
                    )
                    .put("blocksVerdict", true)
            )
            .put(
                JSONObject()
                    .put("id", "LIMIT-CAMPAIGN-INCOMPLETE")
                    .put("severity", "high")
                    .put(
                        "description",
                        "This result is one bounded run and cannot prove 99.5% start/stop reliability.",
                    )
                    .put("blocksVerdict", true)
            )

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun Iterable<*>.toJsonArray(): JSONArray =
        JSONArray().also { array -> forEach(array::put) }

    private fun ManualAnswer?.wireValue(): String = this?.wireValue ?: "unknown"

    private fun prefixedSha(value: String): String = "sha256:$value"

    private fun String.sanitizeErrorCode(): String =
        uppercase().replace(Regex("[^A-Z0-9_]"), "_").take(80).padEnd(3, '_')
}
