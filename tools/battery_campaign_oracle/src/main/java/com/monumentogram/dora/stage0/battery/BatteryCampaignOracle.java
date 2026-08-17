package com.monumentogram.dora.stage0.battery;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * A bounded, pure-host Stage 0 oracle for checking controlled battery campaign inputs.
 *
 * <p>This class performs structural checks, exact control matching, and the approved
 * {@code capture * 4 <= baseline * 5} comparison. It never measures a device and never returns
 * a full PoC verdict.
 */
public final class BatteryCampaignOracle {
    public static final String REQUIRED_POC_ID = "POC-BATTERY-001";
    public static final String REQUIRED_GATE_SET_VERSION = "stage0-v0.1";
    public static final String VERDICT_SENTINEL = "NO_FULL_POC_VERDICT";
    public static final int MINIMUM_CONTROLLED_REPEATS = 3;
    public static final long REQUIRED_DURATION_SECONDS = 3_600L;

    private static final Pattern OPAQUE_ID = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,79}");
    private static final Pattern SHA256 = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final String ZERO_SHA256 = "sha256:" + "0".repeat(64);
    private static final Comparator<Issue> ISSUE_ORDER =
            Comparator.comparing((Issue issue) -> issue.code().name())
                    .thenComparing(Issue::subjectId);

    public BatteryCampaignOracle() {}

    public enum DeviceProfile {
        D1,
        D2,
        D3,
        D4,
        D5
    }

    public enum DeviceKind {
        PHYSICAL,
        REMOTE_PHYSICAL,
        EMULATOR
    }

    public enum Abi {
        ARM64_V8A,
        ARMEABI_V7A,
        X86_64,
        X86
    }

    public enum RunMode {
        MINIMAL_AUDIO_RECORD_FGS_BASELINE,
        DORA_CAPTURE_ONLY
    }

    public enum DurationUnit {
        SECONDS,
        MILLISECONDS
    }

    public enum ScreenState {
        ON,
        OFF,
        MIXED
    }

    public enum RadioState {
        AIRPLANE_MODE,
        WIFI_ON,
        CELLULAR_ON
    }

    public enum SignalState {
        NO_RADIO_SIGNAL,
        STABLE_LOW,
        STABLE_MEDIUM,
        STABLE_HIGH
    }

    public enum ThermalStatus {
        NONE,
        LIGHT,
        MODERATE,
        SEVERE,
        CRITICAL,
        EMERGENCY,
        SHUTDOWN,
        UNKNOWN
    }

    public enum ChargerState {
        UNPLUGGED,
        CHARGING,
        FULL,
        UNKNOWN
    }

    public enum PowerSource {
        BATTERY,
        USB,
        AC,
        WIRELESS,
        UNKNOWN
    }

    public enum RunDisposition {
        VALID,
        INVALIDATED
    }

    public enum StructuralStatus {
        VALID,
        INVALID
    }

    public enum PairStatus {
        CONTROLLED_COMPARABLE,
        CONTROLLED_NOT_EVALUATED_NO_MWH,
        EXCLUDED
    }

    public enum RatioState {
        WITHIN_5_OVER_4,
        ABOVE_5_OVER_4,
        NOT_EVALUATED_NO_MWH,
        NOT_EVALUATED_EXCLUDED
    }

    public enum LocalSliceState {
        ELIGIBLE_OBSERVATIONS_WITHIN_BOUNDARY,
        DISQUALIFIER_OBSERVED,
        INCOMPLETE
    }

    public enum IssueKind {
        INVALID_INPUT,
        EXCLUSION,
        OBSERVATION,
        COVERAGE
    }

    public enum IssueCode {
        CAMPAIGN_NULL,
        CAMPAIGN_ID_INVALID,
        POC_ID_INVALID,
        GATE_SET_VERSION_INVALID,
        RUN_LIST_NULL,
        RUN_NULL,
        RUN_ID_INVALID,
        RUN_ID_DUPLICATE,
        ATTEMPT_ID_INVALID,
        ATTEMPT_ID_DUPLICATE,
        REPEAT_ID_INVALID,
        REPEAT_ID_DUPLICATE,
        APPROVED_SLICE_ID_INVALID,
        DEVICE_PROFILE_MISSING,
        DEVICE_KIND_MISSING,
        DEVICE_INSTANCE_ID_INVALID,
        FIRMWARE_ID_INVALID,
        ANDROID_API_INVALID,
        ABI_MISSING,
        RUN_MODE_MISSING,
        REPEAT_ORDINAL_INVALID,
        REPEAT_ORDINAL_DUPLICATE,
        SCREEN_STATE_MISSING,
        BRIGHTNESS_INVALID,
        RADIO_STATE_MISSING,
        SIGNAL_STATE_MISSING,
        START_THERMAL_STATUS_MISSING,
        FIXTURE_DIGEST_INVALID,
        DURATION_UNIT_MISSING,
        PLANNED_DURATION_INVALID,
        ACTUAL_DURATION_INVALID,
        ENERGY_MICRO_WH_INVALID,
        CHARGER_STATE_MISSING,
        POWER_SOURCE_MISSING,
        DROPPED_FRAMES_INVALID,
        DROPPED_FRAMES_MISSING,
        MAX_THERMAL_STATUS_MISSING,
        RUN_DISPOSITION_MISSING,
        INVALIDATION_REASON_INVALID,
        PROTOCOL_ID_INVALID,
        MEASUREMENT_SOURCE_ID_INVALID,
        PAIR_LIST_NULL,
        PAIR_NULL,
        PAIR_ID_INVALID,
        PAIR_ID_DUPLICATE,
        PAIR_BASELINE_RUN_ID_INVALID,
        PAIR_CAPTURE_RUN_ID_INVALID,
        PAIR_DANGLING_BASELINE,
        PAIR_DANGLING_CAPTURE,
        PAIR_SELF_REFERENCE,
        RUN_REUSED_ACROSS_PAIRS,
        RUN_INVALIDATED,
        RUN_NON_PHYSICAL,
        RUN_INCOMPLETE,
        RUN_DURATION_UNIT_NOT_SECONDS,
        RUN_DURATION_NOT_ONE_HOUR,
        RUN_CHARGER_NOT_UNPLUGGED,
        RUN_POWER_SOURCE_NOT_BATTERY,
        RUN_START_THERMAL_UNKNOWN,
        RUN_MAX_THERMAL_UNKNOWN,
        PAIR_BASELINE_MODE_INVALID,
        PAIR_CAPTURE_MODE_INVALID,
        PAIR_CONTROL_MISMATCH_DEVICE_PROFILE,
        PAIR_CONTROL_MISMATCH_DEVICE_KIND,
        PAIR_CONTROL_MISMATCH_DEVICE_INSTANCE,
        PAIR_CONTROL_MISMATCH_FIRMWARE,
        PAIR_CONTROL_MISMATCH_ANDROID_API,
        PAIR_CONTROL_MISMATCH_ABI,
        PAIR_CONTROL_MISMATCH_APPROVED_SLICE,
        PAIR_CONTROL_MISMATCH_FIXTURE,
        PAIR_CONTROL_MISMATCH_DURATION_UNIT,
        PAIR_CONTROL_MISMATCH_PLANNED_DURATION,
        PAIR_CONTROL_MISMATCH_ACTUAL_DURATION,
        PAIR_CONTROL_MISMATCH_COMPLETION,
        PAIR_CONTROL_MISMATCH_SCREEN,
        PAIR_CONTROL_MISMATCH_BATTERY_SAVER,
        PAIR_CONTROL_MISMATCH_BRIGHTNESS,
        PAIR_CONTROL_MISMATCH_RADIO,
        PAIR_CONTROL_MISMATCH_SIGNAL,
        PAIR_CONTROL_MISMATCH_START_THERMAL,
        PAIR_CONTROL_MISMATCH_CHARGER,
        PAIR_CONTROL_MISMATCH_POWER_SOURCE,
        PAIR_CONTROL_MISMATCH_PROTOCOL,
        PAIR_CONTROL_MISMATCH_MEASUREMENT_SOURCE,
        PAIR_ENERGY_MISSING,
        PAIR_ABOVE_RELATIVE_BOUNDARY,
        CAPTURE_DROPPED_FRAMES_OBSERVED,
        CAPTURE_SEVERE_THERMAL_OBSERVED,
        SLICE_FEWER_THAN_THREE_VALID_PAIRS,
        NO_CONTROLLED_COMPARISONS,
        PHYSICAL_PROFILE_MISSING
    }

    public record CampaignInput(
            String campaignId,
            String pocId,
            String gateSetVersion,
            List<RunObservation> runs,
            List<ComparisonPair> explicitPairs) {
        public CampaignInput {
            runs = immutableNullableCopy(runs);
            explicitPairs = immutableNullableCopy(explicitPairs);
        }
    }

    public record RunObservation(
            String runId,
            String attemptId,
            String repeatId,
            DeviceProfile deviceProfile,
            DeviceKind deviceKind,
            String deviceInstanceId,
            String firmwareId,
            int androidApi,
            Abi abi,
            RunMode mode,
            int repeatOrdinal,
            String approvedSliceId,
            ScreenState screenState,
            boolean batterySaverEnabled,
            int brightnessPercent,
            RadioState radioState,
            SignalState signalState,
            ThermalStatus startThermalStatus,
            String fixtureSha256,
            DurationUnit durationUnit,
            long plannedDurationSeconds,
            long actualDurationSeconds,
            boolean completed,
            Long energyMicroWh,
            ChargerState chargerState,
            PowerSource powerSource,
            Long droppedFrames,
            ThermalStatus maxThermalStatus,
            RunDisposition disposition,
            String invalidationReasonCode,
            String protocolId,
            String measurementSourceId) {}

    public record ComparisonPair(String pairId, String baselineRunId, String captureRunId) {}

    public record Issue(IssueKind kind, IssueCode code, String subjectId) {
        public Issue {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(subjectId, "subjectId");
        }
    }

    public record RunAssessment(
            String runId,
            boolean eligibleForControlledComparison,
            List<IssueCode> exclusions,
            boolean droppedFramesObserved,
            boolean severeThermalObserved) {
        public RunAssessment {
            Objects.requireNonNull(runId, "runId");
            exclusions = List.copyOf(exclusions);
        }
    }

    public record PairAssessment(
            String pairId,
            String baselineRunId,
            String captureRunId,
            PairStatus status,
            RatioState ratioState,
            boolean countsTowardMinimum,
            String sliceKey,
            boolean captureDroppedFramesObserved,
            boolean captureSevereThermalObserved,
            List<IssueCode> exclusions) {
        public PairAssessment {
            Objects.requireNonNull(pairId, "pairId");
            Objects.requireNonNull(baselineRunId, "baselineRunId");
            Objects.requireNonNull(captureRunId, "captureRunId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(ratioState, "ratioState");
            exclusions = List.copyOf(exclusions);
        }
    }

    public record SliceAssessment(
            String sliceKey,
            int validPairCount,
            boolean minimumThreeControlledRepeatsObserved,
            LocalSliceState localSliceState,
            int withinBoundaryCount,
            int aboveBoundaryCount,
            int noMwhCount,
            boolean droppedFramesObserved,
            boolean severeThermalObserved) {
        public SliceAssessment {
            Objects.requireNonNull(sliceKey, "sliceKey");
            Objects.requireNonNull(localSliceState, "localSliceState");
        }
    }

    public record CampaignEvaluation(
            String campaignId,
            String pocId,
            String gateSetVersion,
            StructuralStatus structuralStatus,
            String verdict,
            List<RunAssessment> runs,
            List<PairAssessment> pairs,
            List<SliceAssessment> slices,
            List<DeviceProfile> observedPhysicalProfiles,
            List<DeviceProfile> missingPhysicalProfiles,
            List<Issue> issues) {
        public CampaignEvaluation {
            Objects.requireNonNull(campaignId, "campaignId");
            Objects.requireNonNull(pocId, "pocId");
            Objects.requireNonNull(gateSetVersion, "gateSetVersion");
            Objects.requireNonNull(structuralStatus, "structuralStatus");
            Objects.requireNonNull(verdict, "verdict");
            runs = List.copyOf(runs);
            pairs = List.copyOf(pairs);
            slices = List.copyOf(slices);
            observedPhysicalProfiles = List.copyOf(observedPhysicalProfiles);
            missingPhysicalProfiles = List.copyOf(missingPhysicalProfiles);
            issues = List.copyOf(issues);
        }

        public String canonicalJson() {
            StringBuilder output = new StringBuilder(512);
            output.append('{');
            appendJsonField(output, "campaignId", campaignId, false);
            appendJsonField(output, "pocId", pocId, true);
            appendJsonField(output, "gateSetVersion", gateSetVersion, true);
            appendJsonField(output, "structuralStatus", structuralStatus.name(), true);
            appendJsonField(output, "verdict", verdict, true);
            output.append(",\"runs\":[");
            boolean first = true;
            for (RunAssessment run : runs) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                output.append('{');
                appendJsonField(output, "runId", run.runId(), false);
                appendJsonBoolean(
                        output,
                        "eligibleForControlledComparison",
                        run.eligibleForControlledComparison(),
                        true);
                appendJsonEnums(output, "exclusions", run.exclusions(), true);
                appendJsonBoolean(output, "droppedFramesObserved", run.droppedFramesObserved(), true);
                appendJsonBoolean(output, "severeThermalObserved", run.severeThermalObserved(), true);
                output.append("}");
            }
            output.append("],\"pairs\":[");
            first = true;
            for (PairAssessment pair : pairs) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                output.append('{');
                appendJsonField(output, "pairId", pair.pairId(), false);
                appendJsonField(output, "baselineRunId", pair.baselineRunId(), true);
                appendJsonField(output, "captureRunId", pair.captureRunId(), true);
                appendJsonField(output, "status", pair.status().name(), true);
                appendJsonField(output, "ratioState", pair.ratioState().name(), true);
                appendJsonBoolean(output, "countsTowardMinimum", pair.countsTowardMinimum(), true);
                appendJsonNullableField(output, "sliceKey", pair.sliceKey(), true);
                appendJsonBoolean(
                        output,
                        "captureDroppedFramesObserved",
                        pair.captureDroppedFramesObserved(),
                        true);
                appendJsonBoolean(
                        output,
                        "captureSevereThermalObserved",
                        pair.captureSevereThermalObserved(),
                        true);
                appendJsonEnums(output, "exclusions", pair.exclusions(), true);
                output.append("}");
            }
            output.append("],\"slices\":[");
            first = true;
            for (SliceAssessment slice : slices) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                output.append('{');
                appendJsonField(output, "sliceKey", slice.sliceKey(), false);
                appendJsonNumber(output, "validPairCount", slice.validPairCount(), true);
                appendJsonBoolean(
                        output,
                        "minimumThreeControlledRepeatsObserved",
                        slice.minimumThreeControlledRepeatsObserved(),
                        true);
                appendJsonField(output, "localSliceState", slice.localSliceState().name(), true);
                appendJsonNumber(output, "withinBoundaryCount", slice.withinBoundaryCount(), true);
                appendJsonNumber(output, "aboveBoundaryCount", slice.aboveBoundaryCount(), true);
                appendJsonNumber(output, "noMwhCount", slice.noMwhCount(), true);
                appendJsonBoolean(output, "droppedFramesObserved", slice.droppedFramesObserved(), true);
                appendJsonBoolean(output, "severeThermalObserved", slice.severeThermalObserved(), true);
                output.append("}");
            }
            output.append(']');
            appendJsonEnums(output, "observedPhysicalProfiles", observedPhysicalProfiles, true);
            appendJsonEnums(output, "missingPhysicalProfiles", missingPhysicalProfiles, true);
            output.append(",\"issues\":[");
            first = true;
            for (Issue issue : issues) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                output.append('{');
                appendJsonField(output, "kind", issue.kind().name(), false);
                appendJsonField(output, "code", issue.code().name(), true);
                appendJsonField(output, "subjectId", issue.subjectId(), true);
                output.append("}");
            }
            return output.append("]}").toString();
        }
    }

    public CampaignEvaluation evaluate(CampaignInput input) {
        if (input == null) {
            return invalidEvaluation(
                    "<NULL>",
                    "<NULL>",
                    "<NULL>",
                    List.of(issue(IssueKind.INVALID_INPUT, IssueCode.CAMPAIGN_NULL, "CAMPAIGN")));
        }

        TreeSet<Issue> issues = new TreeSet<>(ISSUE_ORDER);
        validateCampaignScalars(input, issues);
        Map<String, RunObservation> runsById = validateRuns(input.runs(), issues);
        validatePairs(input.explicitPairs(), runsById, issues);
        if (containsInvalidInput(issues)) {
            return invalidEvaluation(
                    input.campaignId(),
                    input.pocId(),
                    input.gateSetVersion(),
                    List.copyOf(issues));
        }

        List<RunAssessment> runAssessments = assessRuns(runsById, issues);
        Map<String, RunAssessment> assessmentsByRunId = new LinkedHashMap<>();
        for (RunAssessment assessment : runAssessments) {
            assessmentsByRunId.put(assessment.runId(), assessment);
        }

        List<ComparisonPair> sortedPairs = new ArrayList<>(input.explicitPairs());
        sortedPairs.sort(Comparator.comparing(ComparisonPair::pairId));
        List<PairAssessment> pairAssessments = new ArrayList<>();
        for (ComparisonPair pair : sortedPairs) {
            pairAssessments.add(
                    assessPair(
                            pair,
                            runsById.get(pair.baselineRunId()),
                            runsById.get(pair.captureRunId()),
                            assessmentsByRunId,
                            issues));
        }

        List<SliceAssessment> slices = assessSlices(pairAssessments, issues);
        EnumSet<DeviceProfile> observedProfiles = EnumSet.noneOf(DeviceProfile.class);
        for (PairAssessment pair : pairAssessments) {
            if (pair.status() == PairStatus.CONTROLLED_COMPARABLE) {
                observedProfiles.add(runsById.get(pair.captureRunId()).deviceProfile());
            }
        }
        List<DeviceProfile> observed = List.copyOf(observedProfiles);
        EnumSet<DeviceProfile> missingSet = EnumSet.allOf(DeviceProfile.class);
        missingSet.removeAll(observedProfiles);
        List<DeviceProfile> missing = List.copyOf(missingSet);
        for (DeviceProfile profile : missing) {
            issues.add(
                    issue(
                            IssueKind.COVERAGE,
                            IssueCode.PHYSICAL_PROFILE_MISSING,
                            profile.name()));
        }
        if (pairAssessments.stream()
                .noneMatch(pair -> pair.status() == PairStatus.CONTROLLED_COMPARABLE)) {
            issues.add(
                    issue(
                            IssueKind.COVERAGE,
                            IssueCode.NO_CONTROLLED_COMPARISONS,
                            "CAMPAIGN"));
        }

        return new CampaignEvaluation(
                input.campaignId(),
                input.pocId(),
                input.gateSetVersion(),
                StructuralStatus.VALID,
                VERDICT_SENTINEL,
                runAssessments,
                pairAssessments,
                slices,
                observed,
                missing,
                List.copyOf(issues));
    }

    private static void validateCampaignScalars(CampaignInput input, Set<Issue> issues) {
        if (!validOpaqueId(input.campaignId())) {
            issues.add(
                    issue(
                            IssueKind.INVALID_INPUT,
                            IssueCode.CAMPAIGN_ID_INVALID,
                            "CAMPAIGN"));
        }
        if (!REQUIRED_POC_ID.equals(input.pocId())) {
            issues.add(issue(IssueKind.INVALID_INPUT, IssueCode.POC_ID_INVALID, "CAMPAIGN"));
        }
        if (!REQUIRED_GATE_SET_VERSION.equals(input.gateSetVersion())) {
            issues.add(
                    issue(
                            IssueKind.INVALID_INPUT,
                            IssueCode.GATE_SET_VERSION_INVALID,
                            "CAMPAIGN"));
        }
        if (input.runs() == null) {
            issues.add(issue(IssueKind.INVALID_INPUT, IssueCode.RUN_LIST_NULL, "CAMPAIGN"));
        }
        if (input.explicitPairs() == null) {
            issues.add(issue(IssueKind.INVALID_INPUT, IssueCode.PAIR_LIST_NULL, "CAMPAIGN"));
        }
    }

    private static Map<String, RunObservation> validateRuns(
            List<RunObservation> runs, Set<Issue> issues) {
        if (runs == null) {
            return Map.of();
        }
        Map<String, List<RunObservation>> groupedById = new TreeMap<>();
        Map<String, List<String>> runIdsByAttemptId = new TreeMap<>();
        Map<String, List<String>> runIdsByRepeatId = new TreeMap<>();
        for (RunObservation run : runs) {
            if (run == null) {
                issues.add(issue(IssueKind.INVALID_INPUT, IssueCode.RUN_NULL, "<RUN>"));
                continue;
            }
            String subject = validOpaqueId(run.runId()) ? run.runId() : "<RUN>";
            validateRun(run, subject, issues);
            if (validOpaqueId(run.runId())) {
                groupedById.computeIfAbsent(run.runId(), ignored -> new ArrayList<>()).add(run);
            }
            if (validOpaqueId(run.attemptId())) {
                runIdsByAttemptId
                        .computeIfAbsent(run.attemptId(), ignored -> new ArrayList<>())
                        .add(subject);
            }
            if (validOpaqueId(run.repeatId())) {
                runIdsByRepeatId
                        .computeIfAbsent(run.repeatId(), ignored -> new ArrayList<>())
                        .add(subject);
            }
        }
        Map<String, RunObservation> uniqueRuns = new TreeMap<>();
        for (Map.Entry<String, List<RunObservation>> entry : groupedById.entrySet()) {
            if (entry.getValue().size() != 1) {
                issues.add(
                        issue(
                                IssueKind.INVALID_INPUT,
                                IssueCode.RUN_ID_DUPLICATE,
                                entry.getKey()));
            } else {
                uniqueRuns.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        addDuplicateIdentifierIssues(
                runIdsByAttemptId, IssueCode.ATTEMPT_ID_DUPLICATE, issues);
        addDuplicateIdentifierIssues(runIdsByRepeatId, IssueCode.REPEAT_ID_DUPLICATE, issues);
        validateRepeatOrdinals(uniqueRuns.values(), issues);
        return Collections.unmodifiableMap(uniqueRuns);
    }

    private static void validateRun(RunObservation run, String subject, Set<Issue> issues) {
        invalidUnless(validOpaqueId(run.runId()), IssueCode.RUN_ID_INVALID, subject, issues);
        invalidUnless(validOpaqueId(run.attemptId()), IssueCode.ATTEMPT_ID_INVALID, subject, issues);
        invalidUnless(validOpaqueId(run.repeatId()), IssueCode.REPEAT_ID_INVALID, subject, issues);
        invalidUnless(run.deviceProfile() != null, IssueCode.DEVICE_PROFILE_MISSING, subject, issues);
        invalidUnless(run.deviceKind() != null, IssueCode.DEVICE_KIND_MISSING, subject, issues);
        invalidUnless(
                validOpaqueId(run.deviceInstanceId()),
                IssueCode.DEVICE_INSTANCE_ID_INVALID,
                subject,
                issues);
        invalidUnless(validOpaqueId(run.firmwareId()), IssueCode.FIRMWARE_ID_INVALID, subject, issues);
        invalidUnless(
                run.androidApi() >= 28 && run.androidApi() <= 100,
                IssueCode.ANDROID_API_INVALID,
                subject,
                issues);
        invalidUnless(run.abi() != null, IssueCode.ABI_MISSING, subject, issues);
        invalidUnless(run.mode() != null, IssueCode.RUN_MODE_MISSING, subject, issues);
        invalidUnless(run.repeatOrdinal() > 0, IssueCode.REPEAT_ORDINAL_INVALID, subject, issues);
        invalidUnless(
                validOpaqueId(run.approvedSliceId()),
                IssueCode.APPROVED_SLICE_ID_INVALID,
                subject,
                issues);
        invalidUnless(run.screenState() != null, IssueCode.SCREEN_STATE_MISSING, subject, issues);
        invalidUnless(
                run.brightnessPercent() >= 0 && run.brightnessPercent() <= 100,
                IssueCode.BRIGHTNESS_INVALID,
                subject,
                issues);
        invalidUnless(run.radioState() != null, IssueCode.RADIO_STATE_MISSING, subject, issues);
        invalidUnless(run.signalState() != null, IssueCode.SIGNAL_STATE_MISSING, subject, issues);
        invalidUnless(
                run.startThermalStatus() != null,
                IssueCode.START_THERMAL_STATUS_MISSING,
                subject,
                issues);
        invalidUnless(
                validDigest(run.fixtureSha256()),
                IssueCode.FIXTURE_DIGEST_INVALID,
                subject,
                issues);
        invalidUnless(run.durationUnit() != null, IssueCode.DURATION_UNIT_MISSING, subject, issues);
        invalidUnless(
                run.plannedDurationSeconds() > 0,
                IssueCode.PLANNED_DURATION_INVALID,
                subject,
                issues);
        invalidUnless(
                run.actualDurationSeconds() > 0,
                IssueCode.ACTUAL_DURATION_INVALID,
                subject,
                issues);
        invalidUnless(
                run.energyMicroWh() == null || run.energyMicroWh() > 0,
                IssueCode.ENERGY_MICRO_WH_INVALID,
                subject,
                issues);
        invalidUnless(run.chargerState() != null, IssueCode.CHARGER_STATE_MISSING, subject, issues);
        invalidUnless(run.powerSource() != null, IssueCode.POWER_SOURCE_MISSING, subject, issues);
        invalidUnless(run.droppedFrames() != null, IssueCode.DROPPED_FRAMES_MISSING, subject, issues);
        invalidUnless(
                run.droppedFrames() == null || run.droppedFrames() >= 0,
                IssueCode.DROPPED_FRAMES_INVALID,
                subject,
                issues);
        invalidUnless(
                run.maxThermalStatus() != null,
                IssueCode.MAX_THERMAL_STATUS_MISSING,
                subject,
                issues);
        invalidUnless(run.disposition() != null, IssueCode.RUN_DISPOSITION_MISSING, subject, issues);
        boolean reasonValid =
                run.disposition() == RunDisposition.VALID
                        ? run.invalidationReasonCode() == null
                        : run.disposition() == RunDisposition.INVALIDATED
                                && validOpaqueId(run.invalidationReasonCode());
        invalidUnless(reasonValid, IssueCode.INVALIDATION_REASON_INVALID, subject, issues);
        invalidUnless(
                validOpaqueId(run.protocolId()),
                IssueCode.PROTOCOL_ID_INVALID,
                subject,
                issues);
        invalidUnless(
                validOpaqueId(run.measurementSourceId()),
                IssueCode.MEASUREMENT_SOURCE_ID_INVALID,
                subject,
                issues);
    }

    private static void validateRepeatOrdinals(
            Iterable<RunObservation> runs, Set<Issue> issues) {
        Map<String, List<String>> idsByRepeatKey = new TreeMap<>();
        for (RunObservation run : runs) {
            if (run.repeatOrdinal() <= 0 || !controlFieldsPresent(run) || run.mode() == null) {
                continue;
            }
            String key = run.mode().name() + '|' + controlKey(run) + '|' + run.repeatOrdinal();
            idsByRepeatKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(run.runId());
        }
        for (List<String> ids : idsByRepeatKey.values()) {
            if (ids.size() > 1) {
                Collections.sort(ids);
                issues.add(
                        issue(
                                IssueKind.INVALID_INPUT,
                                IssueCode.REPEAT_ORDINAL_DUPLICATE,
                                String.join("+", ids)));
            }
        }
    }

    private static void addDuplicateIdentifierIssues(
            Map<String, List<String>> runIdsByIdentifier,
            IssueCode issueCode,
            Set<Issue> issues) {
        for (Map.Entry<String, List<String>> entry : runIdsByIdentifier.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> runIds = new ArrayList<>(entry.getValue());
                Collections.sort(runIds);
                issues.add(
                        issue(
                                IssueKind.INVALID_INPUT,
                                issueCode,
                                entry.getKey() + ':' + String.join("+", runIds)));
            }
        }
    }

    private static void validatePairs(
            List<ComparisonPair> pairs,
            Map<String, RunObservation> runsById,
            Set<Issue> issues) {
        if (pairs == null) {
            return;
        }
        Map<String, Integer> pairIdCounts = new TreeMap<>();
        Map<String, List<String>> pairIdsByRunId = new TreeMap<>();
        for (ComparisonPair pair : pairs) {
            if (pair == null) {
                issues.add(issue(IssueKind.INVALID_INPUT, IssueCode.PAIR_NULL, "<PAIR>"));
                continue;
            }
            String subject = validOpaqueId(pair.pairId()) ? pair.pairId() : "<PAIR>";
            invalidUnless(validOpaqueId(pair.pairId()), IssueCode.PAIR_ID_INVALID, subject, issues);
            invalidUnless(
                    validOpaqueId(pair.baselineRunId()),
                    IssueCode.PAIR_BASELINE_RUN_ID_INVALID,
                    subject,
                    issues);
            invalidUnless(
                    validOpaqueId(pair.captureRunId()),
                    IssueCode.PAIR_CAPTURE_RUN_ID_INVALID,
                    subject,
                    issues);
            if (validOpaqueId(pair.pairId())) {
                pairIdCounts.merge(pair.pairId(), 1, Integer::sum);
            }
            if (validOpaqueId(pair.baselineRunId())) {
                if (!runsById.containsKey(pair.baselineRunId())) {
                    issues.add(
                            issue(
                                    IssueKind.INVALID_INPUT,
                                    IssueCode.PAIR_DANGLING_BASELINE,
                                    subject));
                }
                pairIdsByRunId
                        .computeIfAbsent(pair.baselineRunId(), ignored -> new ArrayList<>())
                        .add(subject);
            }
            if (validOpaqueId(pair.captureRunId())) {
                if (!runsById.containsKey(pair.captureRunId())) {
                    issues.add(
                            issue(
                                    IssueKind.INVALID_INPUT,
                                    IssueCode.PAIR_DANGLING_CAPTURE,
                                    subject));
                }
                pairIdsByRunId
                        .computeIfAbsent(pair.captureRunId(), ignored -> new ArrayList<>())
                        .add(subject);
            }
            if (Objects.equals(pair.baselineRunId(), pair.captureRunId())) {
                issues.add(
                        issue(
                                IssueKind.INVALID_INPUT,
                                IssueCode.PAIR_SELF_REFERENCE,
                                subject));
            }
        }
        for (Map.Entry<String, Integer> entry : pairIdCounts.entrySet()) {
            if (entry.getValue() > 1) {
                issues.add(
                        issue(
                                IssueKind.INVALID_INPUT,
                                IssueCode.PAIR_ID_DUPLICATE,
                                entry.getKey()));
            }
        }
        for (Map.Entry<String, List<String>> entry : pairIdsByRunId.entrySet()) {
            if (entry.getValue().size() > 1) {
                issues.add(
                        issue(
                                IssueKind.INVALID_INPUT,
                                IssueCode.RUN_REUSED_ACROSS_PAIRS,
                                entry.getKey()));
            }
        }
    }

    private static List<RunAssessment> assessRuns(
            Map<String, RunObservation> runsById, Set<Issue> issues) {
        List<RunAssessment> assessments = new ArrayList<>();
        for (RunObservation run : runsById.values()) {
            TreeSet<IssueCode> exclusions = new TreeSet<>(Comparator.comparing(Enum::name));
            if (run.disposition() == RunDisposition.INVALIDATED) {
                exclusions.add(IssueCode.RUN_INVALIDATED);
            }
            if (run.deviceKind() != DeviceKind.PHYSICAL) {
                exclusions.add(IssueCode.RUN_NON_PHYSICAL);
            }
            if (!run.completed()) {
                exclusions.add(IssueCode.RUN_INCOMPLETE);
            }
            if (run.durationUnit() != DurationUnit.SECONDS) {
                exclusions.add(IssueCode.RUN_DURATION_UNIT_NOT_SECONDS);
            }
            if (run.plannedDurationSeconds() != REQUIRED_DURATION_SECONDS
                    || run.actualDurationSeconds() != REQUIRED_DURATION_SECONDS) {
                exclusions.add(IssueCode.RUN_DURATION_NOT_ONE_HOUR);
            }
            if (run.chargerState() != ChargerState.UNPLUGGED) {
                exclusions.add(IssueCode.RUN_CHARGER_NOT_UNPLUGGED);
            }
            if (run.powerSource() != PowerSource.BATTERY) {
                exclusions.add(IssueCode.RUN_POWER_SOURCE_NOT_BATTERY);
            }
            if (run.startThermalStatus() == ThermalStatus.UNKNOWN) {
                exclusions.add(IssueCode.RUN_START_THERMAL_UNKNOWN);
            }
            if (run.maxThermalStatus() == ThermalStatus.UNKNOWN) {
                exclusions.add(IssueCode.RUN_MAX_THERMAL_UNKNOWN);
            }
            for (IssueCode code : exclusions) {
                issues.add(issue(IssueKind.EXCLUSION, code, run.runId()));
            }
            boolean drops = run.droppedFrames() != null && run.droppedFrames() > 0;
            boolean severe = isSevereOrHigher(run.maxThermalStatus());
            if (drops && run.mode() == RunMode.DORA_CAPTURE_ONLY) {
                issues.add(
                        issue(
                                IssueKind.OBSERVATION,
                                IssueCode.CAPTURE_DROPPED_FRAMES_OBSERVED,
                                run.runId()));
            }
            if (severe && run.mode() == RunMode.DORA_CAPTURE_ONLY) {
                issues.add(
                        issue(
                                IssueKind.OBSERVATION,
                                IssueCode.CAPTURE_SEVERE_THERMAL_OBSERVED,
                                run.runId()));
            }
            assessments.add(
                    new RunAssessment(
                            run.runId(), exclusions.isEmpty(), List.copyOf(exclusions), drops, severe));
        }
        assessments.sort(Comparator.comparing(RunAssessment::runId));
        return List.copyOf(assessments);
    }

    private static PairAssessment assessPair(
            ComparisonPair pair,
            RunObservation baseline,
            RunObservation capture,
            Map<String, RunAssessment> assessmentsByRunId,
            Set<Issue> issues) {
        TreeSet<IssueCode> exclusions = new TreeSet<>(Comparator.comparing(Enum::name));
        RunAssessment baselineAssessment = assessmentsByRunId.get(baseline.runId());
        RunAssessment captureAssessment = assessmentsByRunId.get(capture.runId());
        exclusions.addAll(baselineAssessment.exclusions());
        exclusions.addAll(captureAssessment.exclusions());
        if (baseline.mode() != RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE) {
            exclusions.add(IssueCode.PAIR_BASELINE_MODE_INVALID);
        }
        if (capture.mode() != RunMode.DORA_CAPTURE_ONLY) {
            exclusions.add(IssueCode.PAIR_CAPTURE_MODE_INVALID);
        }
        addControlMismatches(baseline, capture, exclusions);
        for (IssueCode code : exclusions) {
            issues.add(issue(IssueKind.EXCLUSION, code, pair.pairId()));
        }

        boolean drops = capture.droppedFrames() != null && capture.droppedFrames() > 0;
        boolean severe = isSevereOrHigher(capture.maxThermalStatus());
        if (!exclusions.isEmpty()) {
            return new PairAssessment(
                    pair.pairId(),
                    baseline.runId(),
                    capture.runId(),
                    PairStatus.EXCLUDED,
                    RatioState.NOT_EVALUATED_EXCLUDED,
                    false,
                    null,
                    drops,
                    severe,
                    List.copyOf(exclusions));
        }

        String sliceKey = controlKey(capture);
        if (baseline.energyMicroWh() == null || capture.energyMicroWh() == null) {
            issues.add(
                    issue(
                            IssueKind.EXCLUSION,
                            IssueCode.PAIR_ENERGY_MISSING,
                            pair.pairId()));
            return new PairAssessment(
                    pair.pairId(),
                    baseline.runId(),
                    capture.runId(),
                    PairStatus.CONTROLLED_NOT_EVALUATED_NO_MWH,
                    RatioState.NOT_EVALUATED_NO_MWH,
                    false,
                    sliceKey,
                    drops,
                    severe,
                    List.of(IssueCode.PAIR_ENERGY_MISSING));
        }

        RatioState ratio = withinRelativeBoundary(baseline.energyMicroWh(), capture.energyMicroWh())
                ? RatioState.WITHIN_5_OVER_4
                : RatioState.ABOVE_5_OVER_4;
        if (ratio == RatioState.ABOVE_5_OVER_4) {
            issues.add(
                    issue(
                            IssueKind.OBSERVATION,
                            IssueCode.PAIR_ABOVE_RELATIVE_BOUNDARY,
                            pair.pairId()));
        }
        return new PairAssessment(
                pair.pairId(),
                baseline.runId(),
                capture.runId(),
                PairStatus.CONTROLLED_COMPARABLE,
                ratio,
                true,
                sliceKey,
                drops,
                severe,
                List.of());
    }

    private static void addControlMismatches(
            RunObservation baseline,
            RunObservation capture,
            Set<IssueCode> exclusions) {
        addMismatch(
                baseline.deviceProfile() == capture.deviceProfile(),
                IssueCode.PAIR_CONTROL_MISMATCH_DEVICE_PROFILE,
                exclusions);
        addMismatch(
                baseline.deviceKind() == capture.deviceKind(),
                IssueCode.PAIR_CONTROL_MISMATCH_DEVICE_KIND,
                exclusions);
        addMismatch(
                Objects.equals(baseline.deviceInstanceId(), capture.deviceInstanceId()),
                IssueCode.PAIR_CONTROL_MISMATCH_DEVICE_INSTANCE,
                exclusions);
        addMismatch(
                Objects.equals(baseline.firmwareId(), capture.firmwareId()),
                IssueCode.PAIR_CONTROL_MISMATCH_FIRMWARE,
                exclusions);
        addMismatch(
                baseline.androidApi() == capture.androidApi(),
                IssueCode.PAIR_CONTROL_MISMATCH_ANDROID_API,
                exclusions);
        addMismatch(
                baseline.abi() == capture.abi(),
                IssueCode.PAIR_CONTROL_MISMATCH_ABI,
                exclusions);
        addMismatch(
                Objects.equals(baseline.approvedSliceId(), capture.approvedSliceId()),
                IssueCode.PAIR_CONTROL_MISMATCH_APPROVED_SLICE,
                exclusions);
        addMismatch(
                Objects.equals(baseline.fixtureSha256(), capture.fixtureSha256()),
                IssueCode.PAIR_CONTROL_MISMATCH_FIXTURE,
                exclusions);
        addMismatch(
                baseline.durationUnit() == capture.durationUnit(),
                IssueCode.PAIR_CONTROL_MISMATCH_DURATION_UNIT,
                exclusions);
        addMismatch(
                baseline.plannedDurationSeconds() == capture.plannedDurationSeconds(),
                IssueCode.PAIR_CONTROL_MISMATCH_PLANNED_DURATION,
                exclusions);
        addMismatch(
                baseline.actualDurationSeconds() == capture.actualDurationSeconds(),
                IssueCode.PAIR_CONTROL_MISMATCH_ACTUAL_DURATION,
                exclusions);
        addMismatch(
                baseline.completed() == capture.completed(),
                IssueCode.PAIR_CONTROL_MISMATCH_COMPLETION,
                exclusions);
        addMismatch(
                baseline.screenState() == capture.screenState(),
                IssueCode.PAIR_CONTROL_MISMATCH_SCREEN,
                exclusions);
        addMismatch(
                baseline.batterySaverEnabled() == capture.batterySaverEnabled(),
                IssueCode.PAIR_CONTROL_MISMATCH_BATTERY_SAVER,
                exclusions);
        addMismatch(
                baseline.brightnessPercent() == capture.brightnessPercent(),
                IssueCode.PAIR_CONTROL_MISMATCH_BRIGHTNESS,
                exclusions);
        addMismatch(
                baseline.radioState() == capture.radioState(),
                IssueCode.PAIR_CONTROL_MISMATCH_RADIO,
                exclusions);
        addMismatch(
                baseline.signalState() == capture.signalState(),
                IssueCode.PAIR_CONTROL_MISMATCH_SIGNAL,
                exclusions);
        addMismatch(
                baseline.startThermalStatus() == capture.startThermalStatus(),
                IssueCode.PAIR_CONTROL_MISMATCH_START_THERMAL,
                exclusions);
        addMismatch(
                baseline.chargerState() == capture.chargerState(),
                IssueCode.PAIR_CONTROL_MISMATCH_CHARGER,
                exclusions);
        addMismatch(
                baseline.powerSource() == capture.powerSource(),
                IssueCode.PAIR_CONTROL_MISMATCH_POWER_SOURCE,
                exclusions);
        addMismatch(
                Objects.equals(baseline.protocolId(), capture.protocolId()),
                IssueCode.PAIR_CONTROL_MISMATCH_PROTOCOL,
                exclusions);
        addMismatch(
                Objects.equals(baseline.measurementSourceId(), capture.measurementSourceId()),
                IssueCode.PAIR_CONTROL_MISMATCH_MEASUREMENT_SOURCE,
                exclusions);
    }

    private static List<SliceAssessment> assessSlices(
            List<PairAssessment> pairs, Set<Issue> issues) {
        Map<String, MutableSlice> slices = new TreeMap<>();
        for (PairAssessment pair : pairs) {
            if (pair.sliceKey() == null || pair.status() == PairStatus.EXCLUDED) {
                continue;
            }
            MutableSlice slice =
                    slices.computeIfAbsent(pair.sliceKey(), ignored -> new MutableSlice());
            if (pair.countsTowardMinimum()) {
                slice.validPairCount++;
            }
            switch (pair.ratioState()) {
                case WITHIN_5_OVER_4 -> slice.withinBoundaryCount++;
                case ABOVE_5_OVER_4 -> slice.aboveBoundaryCount++;
                case NOT_EVALUATED_NO_MWH -> slice.noMwhCount++;
                case NOT_EVALUATED_EXCLUDED -> {
                    // Excluded comparisons never have a slice key.
                }
            }
            slice.droppedFramesObserved |= pair.captureDroppedFramesObserved();
            slice.severeThermalObserved |= pair.captureSevereThermalObserved();
        }
        List<SliceAssessment> result = new ArrayList<>();
        for (Map.Entry<String, MutableSlice> entry : slices.entrySet()) {
            MutableSlice slice = entry.getValue();
            boolean minimumMet = slice.validPairCount >= MINIMUM_CONTROLLED_REPEATS;
            if (!minimumMet) {
                issues.add(
                        issue(
                                IssueKind.COVERAGE,
                                IssueCode.SLICE_FEWER_THAN_THREE_VALID_PAIRS,
                                entry.getKey()));
            }
            LocalSliceState localState;
            if (!minimumMet) {
                localState = LocalSliceState.INCOMPLETE;
            } else if (slice.aboveBoundaryCount > 0
                    || slice.droppedFramesObserved
                    || slice.severeThermalObserved) {
                localState = LocalSliceState.DISQUALIFIER_OBSERVED;
            } else {
                localState = LocalSliceState.ELIGIBLE_OBSERVATIONS_WITHIN_BOUNDARY;
            }
            result.add(
                    new SliceAssessment(
                            entry.getKey(),
                            slice.validPairCount,
                            minimumMet,
                            localState,
                            slice.withinBoundaryCount,
                            slice.aboveBoundaryCount,
                            slice.noMwhCount,
                            slice.droppedFramesObserved,
                            slice.severeThermalObserved));
        }
        return List.copyOf(result);
    }

    private static CampaignEvaluation invalidEvaluation(
            String campaignId, String pocId, String gateSetVersion, List<Issue> issues) {
        TreeSet<Issue> ordered = new TreeSet<>(ISSUE_ORDER);
        ordered.addAll(issues);
        return new CampaignEvaluation(
                campaignId == null ? "<NULL>" : campaignId,
                pocId == null ? "<NULL>" : pocId,
                gateSetVersion == null ? "<NULL>" : gateSetVersion,
                StructuralStatus.INVALID,
                VERDICT_SENTINEL,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.copyOf(ordered));
    }

    private static boolean containsInvalidInput(Set<Issue> issues) {
        return issues.stream().anyMatch(issue -> issue.kind() == IssueKind.INVALID_INPUT);
    }

    private static boolean withinRelativeBoundary(long baselineMicroWh, long captureMicroWh) {
        BigInteger captureTimesFour = BigInteger.valueOf(captureMicroWh).multiply(BigInteger.valueOf(4));
        BigInteger baselineTimesFive = BigInteger.valueOf(baselineMicroWh).multiply(BigInteger.valueOf(5));
        return captureTimesFour.compareTo(baselineTimesFive) <= 0;
    }

    private static boolean isSevereOrHigher(ThermalStatus status) {
        return status == ThermalStatus.SEVERE
                || status == ThermalStatus.CRITICAL
                || status == ThermalStatus.EMERGENCY
                || status == ThermalStatus.SHUTDOWN;
    }

    private static String controlKey(RunObservation run) {
        StringBuilder key = new StringBuilder(256);
        appendToken(key, "profile", run.deviceProfile().name());
        appendToken(key, "kind", run.deviceKind().name());
        appendToken(key, "device", run.deviceInstanceId());
        appendToken(key, "firmware", run.firmwareId());
        appendToken(key, "api", Integer.toString(run.androidApi()));
        appendToken(key, "abi", run.abi().name());
        appendToken(key, "approvedSlice", run.approvedSliceId());
        appendToken(key, "fixture", run.fixtureSha256());
        appendToken(key, "durationUnit", run.durationUnit().name());
        appendToken(key, "planned", Long.toString(run.plannedDurationSeconds()));
        appendToken(key, "actual", Long.toString(run.actualDurationSeconds()));
        appendToken(key, "completed", Boolean.toString(run.completed()));
        appendToken(key, "screen", run.screenState().name());
        appendToken(key, "saver", Boolean.toString(run.batterySaverEnabled()));
        appendToken(key, "brightness", Integer.toString(run.brightnessPercent()));
        appendToken(key, "radio", run.radioState().name());
        appendToken(key, "signal", run.signalState().name());
        appendToken(key, "startThermal", run.startThermalStatus().name());
        appendToken(key, "charger", run.chargerState().name());
        appendToken(key, "powerSource", run.powerSource().name());
        appendToken(key, "protocol", run.protocolId());
        appendToken(key, "measurementSource", run.measurementSourceId());
        return key.toString();
    }

    private static boolean controlFieldsPresent(RunObservation run) {
        return run.deviceProfile() != null
                && run.deviceKind() != null
                && validOpaqueId(run.deviceInstanceId())
                && validOpaqueId(run.firmwareId())
                && run.abi() != null
                && validOpaqueId(run.approvedSliceId())
                && validDigest(run.fixtureSha256())
                && run.durationUnit() != null
                && run.screenState() != null
                && run.radioState() != null
                && run.signalState() != null
                && run.startThermalStatus() != null
                && run.chargerState() != null
                && run.powerSource() != null
                && validOpaqueId(run.protocolId())
                && validOpaqueId(run.measurementSourceId());
    }

    private static boolean validOpaqueId(String value) {
        return value != null && OPAQUE_ID.matcher(value).matches();
    }

    private static boolean validDigest(String value) {
        return value != null && SHA256.matcher(value).matches() && !ZERO_SHA256.equals(value);
    }

    private static void invalidUnless(
            boolean condition, IssueCode code, String subject, Set<Issue> issues) {
        if (!condition) {
            issues.add(issue(IssueKind.INVALID_INPUT, code, subject));
        }
    }

    private static void addMismatch(
            boolean matches, IssueCode code, Set<IssueCode> exclusions) {
        if (!matches) {
            exclusions.add(code);
        }
    }

    private static Issue issue(IssueKind kind, IssueCode code, String subject) {
        return new Issue(kind, code, subject);
    }

    private static <T> List<T> immutableNullableCopy(List<T> values) {
        if (values == null) {
            return null;
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static void appendToken(StringBuilder output, String name, String value) {
        output.append(name).append('=').append(value.length()).append(':').append(value).append(';');
    }

    private static void appendJsonField(
            StringBuilder output, String name, String value, boolean comma) {
        if (comma) {
            output.append(',');
        }
        appendJsonString(output, name);
        output.append(':');
        appendJsonString(output, value);
    }

    private static void appendJsonNullableField(
            StringBuilder output, String name, String value, boolean comma) {
        if (comma) {
            output.append(',');
        }
        appendJsonString(output, name);
        output.append(':');
        if (value == null) {
            output.append("null");
        } else {
            appendJsonString(output, value);
        }
    }

    private static void appendJsonBoolean(
            StringBuilder output, String name, boolean value, boolean comma) {
        if (comma) {
            output.append(',');
        }
        appendJsonString(output, name);
        output.append(':').append(value);
    }

    private static void appendJsonNumber(
            StringBuilder output, String name, int value, boolean comma) {
        if (comma) {
            output.append(',');
        }
        appendJsonString(output, name);
        output.append(':').append(value);
    }

    private static void appendJsonEnums(
            StringBuilder output,
            String name,
            List<? extends Enum<?>> values,
            boolean comma) {
        if (comma) {
            output.append(',');
        }
        appendJsonString(output, name);
        output.append(":[");
        boolean first = true;
        for (Enum<?> value : values) {
            if (!first) {
                output.append(',');
            }
            first = false;
            appendJsonString(output, value.name());
        }
        output.append(']');
    }

    private static void appendJsonString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (current < 0x20) {
                        output.append(String.format("\\u%04x", (int) current));
                    } else {
                        output.append(current);
                    }
                }
            }
        }
        output.append('"');
    }

    private static final class MutableSlice {
        private int validPairCount;
        private int withinBoundaryCount;
        private int aboveBoundaryCount;
        private int noMwhCount;
        private boolean droppedFramesObserved;
        private boolean severeThermalObserved;
    }
}
