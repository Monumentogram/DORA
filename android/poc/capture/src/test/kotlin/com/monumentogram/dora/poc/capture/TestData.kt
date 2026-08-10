package com.monumentogram.dora.poc.capture

import com.monumentogram.dora.poc.capture.model.AudioConfiguration
import com.monumentogram.dora.poc.capture.model.AudioCounters
import com.monumentogram.dora.poc.capture.model.CaptureOutcome
import com.monumentogram.dora.poc.capture.model.DeletionReceipt
import com.monumentogram.dora.poc.capture.model.DeviceProfile
import com.monumentogram.dora.poc.capture.model.RunKind
import com.monumentogram.dora.poc.capture.model.SystemSnapshot
import com.monumentogram.dora.poc.capture.model.WavAnalysis

internal fun testDeviceProfile() =
    DeviceProfile(
        manufacturer = "Dora Test",
        model = "Synthetic Phone",
        androidVersion = "16",
        androidApi = 36,
        buildId = "TEST.2026.001",
        securityPatch = "2026-07-01",
        primaryAbi = "arm64-v8a",
        supportedAbis = listOf("arm64-v8a"),
        totalRamMb = 8192,
        freeStorageMb = 16_384,
        batteryPercent = 80.0,
        chargingState = "unplugged",
        thermalStatus = "NONE",
        audioInputTypes = listOf("Встроенный микрофон"),
        supportedSampleRates = listOf(16_000),
        pageSizeBytes = 4_096,
        candidateProfileId = "D2",
    )

internal fun testSnapshot(elapsed: Long = 1_000L) =
    SystemSnapshot(
        elapsedRealtimeMs = elapsed,
        batteryPercent = 80.0,
        chargingState = "unplugged",
        chargeCounterMicroAh = 4_000_000,
        currentNowMicroA = -100_000,
        energyCounterNanoWh = null,
        thermalStatus = "NONE",
        processPssMb = 40.0,
        processRssMb = null,
        nativeHeapMb = 8.0,
        freeStorageMb = 16_384,
        screenInteractive = true,
    )

internal fun testOutcome(
    run: RunKind = RunKind.RUN_A,
    valid: Boolean = true,
    errors: Map<String, Int> = emptyMap(),
) =
    CaptureOutcome(
        run = run,
        runId = "${run.id}-20260805T000000Z-12345678",
        startedAtUtc = "2026-08-05T00:00:00Z",
        finishedAtUtc = "2026-08-05T00:03:00Z",
        actualDurationMs = run.targetSeconds * 1_000L,
        configuration =
            AudioConfiguration(
                sampleRate = 16_000,
                bufferBytes = 8_000,
                fallbackUsed = false,
            ),
        counters =
            AudioCounters(
                samples = run.targetSeconds * 16_000L,
                bytes = run.targetSeconds * 32_000L,
                fileBytes = run.targetSeconds * 32_000L + 44,
                errors = errors,
                route = "Встроенный микрофон",
            ),
        screenOnMs = if (run == RunKind.RUN_A) run.targetSeconds * 1_000L else 5_000L,
        screenOffMs = if (run == RunKind.RUN_A) 0L else run.targetSeconds * 1_000L - 5_000L,
        startSnapshot = testSnapshot(),
        endSnapshot = testSnapshot(run.targetSeconds * 1_000L + 1_000L),
        maxThermalStatus = "NONE",
        peakPssMb = 42.0,
        peakRssMb = null,
        peakNativeHeapMb = 9.0,
        startLatencyMs = 20,
        finalizationLatencyMs = 30,
        routeChanges = 0,
        interruptionCount = 0,
        fixtureUsed = run == RunKind.RUN_A,
        privateFileName = "capture-${run.id}-test.wav",
        wavAnalysis =
            WavAnalysis(
                valid = valid,
                reason = if (valid) "valid" else "invalid",
                sampleRate = 16_000,
                channelCount = 1,
                bitsPerSample = 16,
                dataBytes = run.targetSeconds * 32_000L,
                sha256 = "a".repeat(64),
            ),
    )

internal fun testReceipt(success: Boolean = true, run: RunKind = RunKind.RUN_A) =
    DeletionReceipt(
        runId = "${run.id}-20260805T000000Z-12345678",
        verifiedAtUtc = "2026-08-05T00:03:01Z",
        wavWasValid = true,
        sha256 = "a".repeat(64),
        bytesBeforeDeletion = 5_760_044,
        deletionSucceeded = success,
        absenceVerified = success,
        failureReason = if (success) null else "still exists",
    )
