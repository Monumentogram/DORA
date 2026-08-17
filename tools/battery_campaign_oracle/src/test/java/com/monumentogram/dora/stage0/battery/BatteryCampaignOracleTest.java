package com.monumentogram.dora.stage0.battery;

import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.Abi;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.CampaignEvaluation;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.CampaignInput;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.ChargerState;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.ComparisonPair;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.DeviceKind;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.DeviceProfile;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.DurationUnit;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.Issue;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.IssueCode;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.IssueKind;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.LocalSliceState;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.PairAssessment;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.PairStatus;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.PowerSource;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.RadioState;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.RatioState;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.RunDisposition;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.RunMode;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.RunObservation;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.RunAssessment;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.ScreenState;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.SignalState;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.SliceAssessment;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.StructuralStatus;
import com.monumentogram.dora.stage0.battery.BatteryCampaignOracle.ThermalStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Self-contained Java 17 adversarial tests for {@link BatteryCampaignOracle}. */
public final class BatteryCampaignOracleTest {
    private static final BatteryCampaignOracle ORACLE = new BatteryCampaignOracle();
    private static final String DIGEST_ONE = "sha256:" + "1".repeat(64);
    private static final String DIGEST_TWO = "sha256:" + "2".repeat(64);
    private static int assertions;

    private BatteryCampaignOracleTest() {}

    public static void main(String[] args) {
        boundaryAndThreeRepeatSlice();
        boundaryPlusOneAndOverflow();
        missingAndInvalidEnergy();
        everyControlMismatch();
        excludedRunStates();
        identifierAndPairFailures();
        droppedFrameAndThermalDistinctions();
        incompleteAndInvalidatedAttempts();
        profileCoverageAndSliceIsolation();
        permutationRepeatAndBranchDeterminism();
        immutableInputsAndOutputs();
        captureLikeNonEvidenceCases();
        canonicalJsonGolden();
        require(assertions >= 180, "assertion floor");
        System.out.print("BATTERY_CAMPAIGN_ORACLE_TEST_OK");
    }

    private static void boundaryAndThreeRepeatSlice() {
        Fixture fixture = fixture("BOUND", DeviceProfile.D1, 3, 4L, 5L);
        CampaignEvaluation result = evaluate(fixture.runs(), fixture.pairs());

        equal(StructuralStatus.VALID, result.structuralStatus(), "boundary structural status");
        equal(BatteryCampaignOracle.VERDICT_SENTINEL, result.verdict(), "verdict sentinel");
        equal(6, result.runs().size(), "all attempts retained");
        equal(3, result.pairs().size(), "all explicit pairs retained");
        equal(1, result.slices().size(), "one exact slice");
        SliceAssessment slice = result.slices().get(0);
        equal(3, slice.validPairCount(), "three comparable repeats");
        require(slice.minimumThreeControlledRepeatsObserved(), "minimum repeats observed");
        equal(
                LocalSliceState.ELIGIBLE_OBSERVATIONS_WITHIN_BOUNDARY,
                slice.localSliceState(),
                "bounded local slice state");
        equal(3, slice.withinBoundaryCount(), "boundary equality accepted");
        equal(0, slice.aboveBoundaryCount(), "no ratio above boundary");
        equal(0, slice.noMwhCount(), "no missing metric");
        require(!slice.droppedFramesObserved(), "zero drops");
        require(!slice.severeThermalObserved(), "no severe thermal");
        equal(List.of(DeviceProfile.D1), result.observedPhysicalProfiles(), "observed D1");
        equal(
                List.of(DeviceProfile.D2, DeviceProfile.D3, DeviceProfile.D4, DeviceProfile.D5),
                result.missingPhysicalProfiles(),
                "missing profiles explicit");
        for (PairAssessment pair : result.pairs()) {
            equal(PairStatus.CONTROLLED_COMPARABLE, pair.status(), "controlled pair");
            equal(RatioState.WITHIN_5_OVER_4, pair.ratioState(), "exact 5/4 boundary");
            require(pair.countsTowardMinimum(), "pair counts toward repeat minimum");
            equal(List.of(), pair.exclusions(), "no pair exclusion");
        }
        hasIssue(result, IssueCode.PHYSICAL_PROFILE_MISSING, "D2");
        lacksIssue(result, IssueCode.SLICE_FEWER_THAN_THREE_VALID_PAIRS);
        equal("POC-BATTERY-001", result.pocId(), "exact PoC ID retained");
        equal("stage0-v0.1", result.gateSetVersion(), "exact gate set retained");
    }

    private static void boundaryPlusOneAndOverflow() {
        CampaignEvaluation plusOne = singlePair(4L, 6L);
        equal(RatioState.ABOVE_5_OVER_4, plusOne.pairs().get(0).ratioState(), "+1 above");
        hasIssue(plusOne, IssueCode.PAIR_ABOVE_RELATIVE_BOUNDARY, "PAIR-1");

        long overflowBaseline = 7_378_697_629_483_820_644L;
        long exactCapture = 9_223_372_036_854_775_805L;
        CampaignEvaluation overflowEqual = singlePair(overflowBaseline, exactCapture);
        equal(
                RatioState.WITHIN_5_OVER_4,
                overflowEqual.pairs().get(0).ratioState(),
                "overflow-safe exact equality");
        CampaignEvaluation overflowPlusOne = singlePair(overflowBaseline, exactCapture + 1L);
        equal(
                RatioState.ABOVE_5_OVER_4,
                overflowPlusOne.pairs().get(0).ratioState(),
                "overflow-safe plus one");

        CampaignEvaluation maxEqual = singlePair(Long.MAX_VALUE, Long.MAX_VALUE);
        equal(
                RatioState.WITHIN_5_OVER_4,
                maxEqual.pairs().get(0).ratioState(),
                "Long.MAX direct comparison");
    }

    private static void missingAndInvalidEnergy() {
        CampaignEvaluation missingCapture = singlePair(100L, null);
        PairAssessment missingPair = missingCapture.pairs().get(0);
        equal(
                PairStatus.CONTROLLED_NOT_EVALUATED_NO_MWH,
                missingPair.status(),
                "null mWh is controlled but not evaluated");
        equal(RatioState.NOT_EVALUATED_NO_MWH, missingPair.ratioState(), "null reason state");
        equal(List.of(IssueCode.PAIR_ENERGY_MISSING), missingPair.exclusions(), "exact null reason");
        hasIssue(missingCapture, IssueCode.PAIR_ENERGY_MISSING, "PAIR-1");
        require(!missingPair.countsTowardMinimum(), "null mWh cannot count");

        CampaignEvaluation missingBaseline = singlePair(null, 100L);
        equal(
                RatioState.NOT_EVALUATED_NO_MWH,
                missingBaseline.pairs().get(0).ratioState(),
                "baseline null not inferred");

        assertEnergyInvalid(0L, RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE);
        assertEnergyInvalid(-1L, RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE);
        assertEnergyInvalid(0L, RunMode.DORA_CAPTURE_ONLY);
        assertEnergyInvalid(Long.MIN_VALUE, RunMode.DORA_CAPTURE_ONLY);
    }

    private static void everyControlMismatch() {
        assertMismatch(
                spec -> spec.deviceProfile = DeviceProfile.D2,
                IssueCode.PAIR_CONTROL_MISMATCH_DEVICE_PROFILE);
        assertMismatch(
                spec -> spec.deviceKind = DeviceKind.EMULATOR,
                IssueCode.PAIR_CONTROL_MISMATCH_DEVICE_KIND,
                IssueCode.RUN_NON_PHYSICAL);
        assertMismatch(
                spec -> spec.deviceInstanceId = "DEVICE-B",
                IssueCode.PAIR_CONTROL_MISMATCH_DEVICE_INSTANCE);
        assertMismatch(
                spec -> spec.firmwareId = "FIRMWARE-B",
                IssueCode.PAIR_CONTROL_MISMATCH_FIRMWARE);
        assertMismatch(spec -> spec.androidApi = 35, IssueCode.PAIR_CONTROL_MISMATCH_ANDROID_API);
        assertMismatch(spec -> spec.abi = Abi.ARMEABI_V7A, IssueCode.PAIR_CONTROL_MISMATCH_ABI);
        assertMismatch(
                spec -> spec.approvedSliceId = "SLICE-B",
                IssueCode.PAIR_CONTROL_MISMATCH_APPROVED_SLICE);
        assertMismatch(spec -> spec.fixtureSha256 = DIGEST_TWO, IssueCode.PAIR_CONTROL_MISMATCH_FIXTURE);
        assertMismatch(
                spec -> spec.durationUnit = DurationUnit.MILLISECONDS,
                IssueCode.PAIR_CONTROL_MISMATCH_DURATION_UNIT,
                IssueCode.RUN_DURATION_UNIT_NOT_SECONDS);
        assertMismatch(
                spec -> spec.plannedDurationSeconds = 3_599L,
                IssueCode.PAIR_CONTROL_MISMATCH_PLANNED_DURATION,
                IssueCode.RUN_DURATION_NOT_ONE_HOUR);
        assertMismatch(
                spec -> spec.actualDurationSeconds = 3_599L,
                IssueCode.PAIR_CONTROL_MISMATCH_ACTUAL_DURATION,
                IssueCode.RUN_DURATION_NOT_ONE_HOUR);
        assertMismatch(
                spec -> spec.completed = false,
                IssueCode.PAIR_CONTROL_MISMATCH_COMPLETION,
                IssueCode.RUN_INCOMPLETE);
        assertMismatch(spec -> spec.screenState = ScreenState.ON, IssueCode.PAIR_CONTROL_MISMATCH_SCREEN);
        assertMismatch(
                spec -> spec.batterySaverEnabled = true,
                IssueCode.PAIR_CONTROL_MISMATCH_BATTERY_SAVER);
        assertMismatch(spec -> spec.brightnessPercent = 41, IssueCode.PAIR_CONTROL_MISMATCH_BRIGHTNESS);
        assertMismatch(spec -> spec.radioState = RadioState.WIFI_ON, IssueCode.PAIR_CONTROL_MISMATCH_RADIO);
        assertMismatch(
                spec -> spec.signalState = SignalState.STABLE_HIGH,
                IssueCode.PAIR_CONTROL_MISMATCH_SIGNAL);
        assertMismatch(
                spec -> spec.startThermalStatus = ThermalStatus.LIGHT,
                IssueCode.PAIR_CONTROL_MISMATCH_START_THERMAL);
        assertMismatch(
                spec -> spec.chargerState = ChargerState.CHARGING,
                IssueCode.PAIR_CONTROL_MISMATCH_CHARGER,
                IssueCode.RUN_CHARGER_NOT_UNPLUGGED);
        assertMismatch(
                spec -> spec.powerSource = PowerSource.USB,
                IssueCode.PAIR_CONTROL_MISMATCH_POWER_SOURCE,
                IssueCode.RUN_POWER_SOURCE_NOT_BATTERY);
        assertMismatch(spec -> spec.protocolId = "PROTOCOL-B", IssueCode.PAIR_CONTROL_MISMATCH_PROTOCOL);
        assertMismatch(
                spec -> spec.measurementSourceId = "SOURCE-B",
                IssueCode.PAIR_CONTROL_MISMATCH_MEASUREMENT_SOURCE);
    }

    private static void excludedRunStates() {
        assertRunExcluded(spec -> spec.deviceKind = DeviceKind.EMULATOR, IssueCode.RUN_NON_PHYSICAL);
        assertRunExcluded(
                spec -> spec.deviceKind = DeviceKind.REMOTE_PHYSICAL,
                IssueCode.RUN_NON_PHYSICAL);
        assertRunExcluded(spec -> spec.completed = false, IssueCode.RUN_INCOMPLETE);
        assertRunExcluded(
                spec -> spec.durationUnit = DurationUnit.MILLISECONDS,
                IssueCode.RUN_DURATION_UNIT_NOT_SECONDS);
        assertRunExcluded(
                spec -> {
                    spec.plannedDurationSeconds = 3_599L;
                    spec.actualDurationSeconds = 3_599L;
                },
                IssueCode.RUN_DURATION_NOT_ONE_HOUR);
        assertRunExcluded(
                spec -> spec.chargerState = ChargerState.CHARGING,
                IssueCode.RUN_CHARGER_NOT_UNPLUGGED);
        assertRunExcluded(
                spec -> spec.chargerState = ChargerState.UNKNOWN,
                IssueCode.RUN_CHARGER_NOT_UNPLUGGED);
        assertRunExcluded(
                spec -> spec.chargerState = ChargerState.FULL,
                IssueCode.RUN_CHARGER_NOT_UNPLUGGED);
        assertRunExcluded(
                spec -> spec.powerSource = PowerSource.UNKNOWN,
                IssueCode.RUN_POWER_SOURCE_NOT_BATTERY);
        assertRunExcluded(
                spec -> spec.startThermalStatus = ThermalStatus.UNKNOWN,
                IssueCode.RUN_START_THERMAL_UNKNOWN);
        assertRunExcluded(
                spec -> spec.maxThermalStatus = ThermalStatus.UNKNOWN,
                IssueCode.RUN_MAX_THERMAL_UNKNOWN);
        assertRunExcluded(
                spec -> {
                    spec.disposition = RunDisposition.INVALIDATED;
                    spec.invalidationReasonCode = "CONTROL-DEVIATION";
                },
                IssueCode.RUN_INVALIDATED);
    }

    private static void identifierAndPairFailures() {
        RunObservation baseline = run("B", RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE, 1, 100L);
        RunObservation capture = run("C", RunMode.DORA_CAPTURE_ONLY, 1, 100L);

        assertInvalid(
                new CampaignInput(
                        null,
                        BatteryCampaignOracle.REQUIRED_POC_ID,
                        BatteryCampaignOracle.REQUIRED_GATE_SET_VERSION,
                        List.of(baseline, capture),
                        List.of(pair("P", "B", "C"))),
                IssueCode.CAMPAIGN_ID_INVALID);
        assertInvalid(
                new CampaignInput(
                        "CAMPAIGN-A",
                        "POC-BATTERY-999",
                        BatteryCampaignOracle.REQUIRED_GATE_SET_VERSION,
                        List.of(),
                        List.of()),
                IssueCode.POC_ID_INVALID);
        assertInvalid(
                new CampaignInput(
                        "CAMPAIGN-A",
                        BatteryCampaignOracle.REQUIRED_POC_ID,
                        "stage0-v9.9",
                        List.of(),
                        List.of()),
                IssueCode.GATE_SET_VERSION_INVALID);
        assertInvalid(campaign(null, List.of()), IssueCode.RUN_LIST_NULL);
        assertInvalid(campaign(List.of(), null), IssueCode.PAIR_LIST_NULL);

        RunSpec badId = spec("B", RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE, 1, 100L);
        badId.runId = "lowercase";
        assertInvalid(campaign(List.of(badId.build()), List.of()), IssueCode.RUN_ID_INVALID);

        RunSpec duplicateRun = spec("B", RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE, 2, 100L);
        duplicateRun.attemptId = "ATTEMPT-B2";
        duplicateRun.repeatId = "REPEAT-B2";
        assertInvalid(
                campaign(List.of(baseline, duplicateRun.build()), List.of()),
                IssueCode.RUN_ID_DUPLICATE);

        RunSpec duplicateAttempt = spec("B2", RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE, 2, 100L);
        duplicateAttempt.attemptId = "ATTEMPT-B";
        assertInvalid(
                campaign(List.of(baseline, duplicateAttempt.build()), List.of()),
                IssueCode.ATTEMPT_ID_DUPLICATE);

        RunSpec duplicateRepeat = spec("B2", RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE, 2, 100L);
        duplicateRepeat.repeatId = "REPEAT-B";
        assertInvalid(
                campaign(List.of(baseline, duplicateRepeat.build()), List.of()),
                IssueCode.REPEAT_ID_DUPLICATE);

        RunSpec duplicateOrdinal = spec("B2", RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE, 1, 100L);
        assertInvalid(
                campaign(List.of(baseline, duplicateOrdinal.build()), List.of()),
                IssueCode.REPEAT_ORDINAL_DUPLICATE);

        assertInvalid(
                campaign(
                        List.of(baseline, capture),
                        List.of(pair("P", "B", "C"), pair("P", "B", "C"))),
                IssueCode.PAIR_ID_DUPLICATE);
        assertInvalid(
                campaign(List.of(baseline), List.of(pair("P", "B", "B"))),
                IssueCode.PAIR_SELF_REFERENCE);
        assertInvalid(
                campaign(List.of(capture), List.of(pair("P", "MISSING", "C"))),
                IssueCode.PAIR_DANGLING_BASELINE);
        assertInvalid(
                campaign(List.of(baseline), List.of(pair("P", "B", "MISSING"))),
                IssueCode.PAIR_DANGLING_CAPTURE);

        RunObservation captureTwo = run("C2", RunMode.DORA_CAPTURE_ONLY, 2, 100L);
        assertInvalid(
                campaign(
                        List.of(baseline, capture, captureTwo),
                        List.of(pair("P1", "B", "C"), pair("P2", "B", "C2"))),
                IssueCode.RUN_REUSED_ACROSS_PAIRS);

        CampaignEvaluation swapped =
                evaluate(
                        List.of(baseline, capture),
                        List.of(pair("P", "C", "B")));
        equal(PairStatus.EXCLUDED, swapped.pairs().get(0).status(), "swapped modes excluded");
        hasPairExclusion(swapped, "P", IssueCode.PAIR_BASELINE_MODE_INVALID);
        hasPairExclusion(swapped, "P", IssueCode.PAIR_CAPTURE_MODE_INVALID);
    }

    private static void droppedFrameAndThermalDistinctions() {
        RunSpec nullDrops = spec("C", RunMode.DORA_CAPTURE_ONLY, 1, 100L);
        nullDrops.droppedFrames = null;
        assertInvalid(
                campaign(
                        List.of(run("B", RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE, 1, 100L), nullDrops.build()),
                        List.of(pair("P", "B", "C"))),
                IssueCode.DROPPED_FRAMES_MISSING);

        RunSpec nullThermal = spec("C", RunMode.DORA_CAPTURE_ONLY, 1, 100L);
        nullThermal.maxThermalStatus = null;
        assertInvalid(
                campaign(
                        List.of(run("B", RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE, 1, 100L), nullThermal.build()),
                        List.of(pair("P", "B", "C"))),
                IssueCode.MAX_THERMAL_STATUS_MISSING);

        CampaignEvaluation zeroNone = singlePairWithCapture(spec -> {
            spec.droppedFrames = 0L;
            spec.maxThermalStatus = ThermalStatus.NONE;
        });
        require(!zeroNone.pairs().get(0).captureDroppedFramesObserved(), "zero drops distinct");
        require(!zeroNone.pairs().get(0).captureSevereThermalObserved(), "NONE thermal distinct");

        CampaignEvaluation oneDrop = threePairVariant(1L, ThermalStatus.MODERATE);
        require(oneDrop.slices().get(0).droppedFramesObserved(), "one drop observed");
        require(!oneDrop.slices().get(0).severeThermalObserved(), "MODERATE not severe");
        equal(
                LocalSliceState.DISQUALIFIER_OBSERVED,
                oneDrop.slices().get(0).localSliceState(),
                "drop blocks local slice candidate");
        hasIssue(oneDrop, IssueCode.CAPTURE_DROPPED_FRAMES_OBSERVED, "THERM-C1");

        CampaignEvaluation severe = threePairVariant(0L, ThermalStatus.SEVERE);
        require(severe.slices().get(0).severeThermalObserved(), "transient SEVERE observed");
        equal(
                LocalSliceState.DISQUALIFIER_OBSERVED,
                severe.slices().get(0).localSliceState(),
                "SEVERE blocks local slice candidate");
        equal(BatteryCampaignOracle.VERDICT_SENTINEL, severe.verdict(), "no sustained policy verdict");

        CampaignEvaluation critical = singlePairWithCapture(spec -> spec.maxThermalStatus = ThermalStatus.CRITICAL);
        require(critical.pairs().get(0).captureSevereThermalObserved(), "CRITICAL is severe-or-higher");
    }

    private static void incompleteAndInvalidatedAttempts() {
        Fixture two = fixture("TWO", DeviceProfile.D1, 2, 100L, 120L);
        CampaignEvaluation incomplete = evaluate(two.runs(), two.pairs());
        equal(2, incomplete.slices().get(0).validPairCount(), "two repeats retained");
        equal(LocalSliceState.INCOMPLETE, incomplete.slices().get(0).localSliceState(), "two incomplete");
        hasIssue(
                incomplete,
                IssueCode.SLICE_FEWER_THAN_THREE_VALID_PAIRS,
                incomplete.slices().get(0).sliceKey());

        Fixture three = fixture("INVALID", DeviceProfile.D1, 3, 100L, 120L);
        List<RunObservation> changed = new ArrayList<>(three.runs());
        int index = indexOfRun(changed, "INVALID-C3");
        RunSpec invalidated = RunSpec.from(changed.get(index));
        invalidated.disposition = RunDisposition.INVALIDATED;
        invalidated.invalidationReasonCode = "PREDECLARED-CONTROL-DEVIATION";
        changed.set(index, invalidated.build());
        CampaignEvaluation result = evaluate(changed, three.pairs());
        equal(6, result.runs().size(), "invalid attempt retained in run history");
        equal(3, result.pairs().size(), "invalid pair retained");
        equal(PairStatus.EXCLUDED, pairById(result, "INVALID-P3").status(), "invalid pair excluded");
        hasPairExclusion(result, "INVALID-P3", IssueCode.RUN_INVALIDATED);
        equal(2, result.slices().get(0).validPairCount(), "invalid repeat does not count");
        equal(LocalSliceState.INCOMPLETE, result.slices().get(0).localSliceState(), "invalid repeat leaves incomplete");
    }

    private static void profileCoverageAndSliceIsolation() {
        Fixture d1 = fixture("ISO1", DeviceProfile.D1, 3, 100L, 120L);
        Fixture d2 = fixture("ISO2", DeviceProfile.D2, 3, 200L, 251L);
        List<RunObservation> runs = new ArrayList<>(d1.runs());
        runs.addAll(d2.runs());
        List<ComparisonPair> pairs = new ArrayList<>(d1.pairs());
        pairs.addAll(d2.pairs());
        CampaignEvaluation result = evaluate(runs, pairs);
        equal(2, result.slices().size(), "two exact slices isolated");
        equal(List.of(DeviceProfile.D1, DeviceProfile.D2), result.observedPhysicalProfiles(), "D1 D2 observed");
        equal(
                List.of(DeviceProfile.D3, DeviceProfile.D4, DeviceProfile.D5),
                result.missingPhysicalProfiles(),
                "D3-D5 still missing");
        equal(
                LocalSliceState.ELIGIBLE_OBSERVATIONS_WITHIN_BOUNDARY,
                result.slices().get(0).localSliceState(),
                "D1 unaffected by D2");
        equal(
                LocalSliceState.DISQUALIFIER_OBSERVED,
                result.slices().get(1).localSliceState(),
                "D2 above boundary stays isolated");
    }

    private static void permutationRepeatAndBranchDeterminism() {
        Fixture base = fixture("PERM", DeviceProfile.D3, 4, 100L, 125L);
        List<RunObservation> runs = new ArrayList<>(base.runs());
        RunSpec missingEnergy = RunSpec.from(runs.get(indexOfRun(runs, "PERM-C4")));
        missingEnergy.energyMicroWh = null;
        runs.set(indexOfRun(runs, "PERM-C4"), missingEnergy.build());
        List<ComparisonPair> pairs = new ArrayList<>(base.pairs());
        CampaignEvaluation forward = evaluate(runs, pairs);

        List<RunObservation> reversedRuns = new ArrayList<>(runs);
        Collections.reverse(reversedRuns);
        List<ComparisonPair> reversedPairs = new ArrayList<>(pairs);
        Collections.reverse(reversedPairs);
        CampaignEvaluation reversed = evaluate(reversedRuns, reversedPairs);
        equal(forward, reversed, "input permutation equality");
        equal(forward.canonicalJson(), reversed.canonicalJson(), "canonical JSON permutation equality");
        equal(forward, evaluate(runs, pairs), "repeat evaluation equality");
        equal(forward.canonicalJson(), forward.canonicalJson(), "repeat serialization equality");

        List<RunObservation> rotatedRuns = new ArrayList<>(runs);
        Collections.rotate(rotatedRuns, 3);
        List<ComparisonPair> rotatedPairs = new ArrayList<>(pairs);
        Collections.rotate(rotatedPairs, 1);
        equal(forward, evaluate(rotatedRuns, rotatedPairs), "branch-heavy rotation equality");
        equal(3, forward.slices().get(0).validPairCount(), "null fourth attempt excluded from count");
        equal(1, forward.slices().get(0).noMwhCount(), "null fourth attempt retained");
    }

    private static void immutableInputsAndOutputs() {
        List<RunObservation> mutableRuns = new ArrayList<>();
        mutableRuns.add(run("B", RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE, 1, 100L));
        mutableRuns.add(run("C", RunMode.DORA_CAPTURE_ONLY, 1, 100L));
        List<ComparisonPair> mutablePairs = new ArrayList<>();
        mutablePairs.add(pair("P", "B", "C"));
        CampaignInput input = campaign(mutableRuns, mutablePairs);
        mutableRuns.clear();
        mutablePairs.clear();
        equal(2, input.runs().size(), "input run list copied");
        equal(1, input.explicitPairs().size(), "input pair list copied");
        expectUnsupported(() -> input.runs().add(run("X", RunMode.DORA_CAPTURE_ONLY, 2, 1L)));
        CampaignEvaluation result = ORACLE.evaluate(input);
        expectUnsupported(() -> result.runs().clear());
        expectUnsupported(() -> result.pairs().clear());
        expectUnsupported(() -> result.slices().clear());
        expectUnsupported(() -> result.issues().clear());
    }

    private static void captureLikeNonEvidenceCases() {
        CampaignEvaluation captureLikeNull = singlePair(null, null);
        equal(
                RatioState.NOT_EVALUATED_NO_MWH,
                captureLikeNull.pairs().get(0).ratioState(),
                "Capture-like null energy stays not evaluated");
        equal(BatteryCampaignOracle.VERDICT_SENTINEL, captureLikeNull.verdict(), "no Capture reinterpretation");

        CampaignEvaluation captureLikeCharging = singlePairWithCapture(spec -> {
            spec.energyMicroWh = null;
            spec.chargerState = ChargerState.CHARGING;
            spec.powerSource = PowerSource.USB;
        });
        equal(PairStatus.EXCLUDED, captureLikeCharging.pairs().get(0).status(), "charging excluded first");
        equal(
                RatioState.NOT_EVALUATED_EXCLUDED,
                captureLikeCharging.pairs().get(0).ratioState(),
                "charging is not converted into missing-mWh comparison");
        hasPairExclusion(captureLikeCharging, "PAIR-1", IssueCode.RUN_CHARGER_NOT_UNPLUGGED);
        hasPairExclusion(captureLikeCharging, "PAIR-1", IssueCode.RUN_POWER_SOURCE_NOT_BATTERY);
    }

    private static void canonicalJsonGolden() {
        String expected =
                "{\"campaignId\":\"<NULL>\",\"pocId\":\"<NULL>\",\"gateSetVersion\":\"<NULL>\","
                        + "\"structuralStatus\":\"INVALID\",\"verdict\":\"NO_FULL_POC_VERDICT\","
                        + "\"runs\":[],\"pairs\":[],\"slices\":[],\"observedPhysicalProfiles\":[],"
                        + "\"missingPhysicalProfiles\":[],\"issues\":[{\"kind\":\"INVALID_INPUT\","
                        + "\"code\":\"CAMPAIGN_NULL\",\"subjectId\":\"CAMPAIGN\"}]}";
        equal(expected, ORACLE.evaluate(null).canonicalJson(), "literal canonical JSON golden");
        require(expected.startsWith("{"), "JSON object start");
        require(expected.endsWith("}"), "JSON object end");
        require(!expected.contains("NaN"), "JSON has no non-finite token");

        CampaignEvaluation nonEmpty =
                new CampaignEvaluation(
                        "C\"\\\n",
                        "P",
                        "G",
                        StructuralStatus.VALID,
                        "V",
                        List.of(new RunAssessment("R", true, List.of(), false, false)),
                        List.of(
                                new PairAssessment(
                                        "PAIR",
                                        "B",
                                        "C",
                                        PairStatus.EXCLUDED,
                                        RatioState.NOT_EVALUATED_EXCLUDED,
                                        false,
                                        null,
                                        true,
                                        false,
                                        List.of(IssueCode.RUN_INCOMPLETE))),
                        List.of(
                                new SliceAssessment(
                                        "S",
                                        1,
                                        false,
                                        LocalSliceState.INCOMPLETE,
                                        0,
                                        0,
                                        1,
                                        true,
                                        false)),
                        List.of(DeviceProfile.D1),
                        List.of(DeviceProfile.D2),
                        List.of(
                                new Issue(
                                        IssueKind.EXCLUSION,
                                        IssueCode.RUN_INCOMPLETE,
                                        "I\n")));

        String expectedNonEmpty =
                "{\"campaignId\":\"C\\\"\\\\\\n\",\"pocId\":\"P\",\"gateSetVersion\":\"G\","
                        + "\"structuralStatus\":\"VALID\",\"verdict\":\"V\",\"runs\":[{\"runId\":\"R\","
                        + "\"eligibleForControlledComparison\":true,\"exclusions\":[],"
                        + "\"droppedFramesObserved\":false,\"severeThermalObserved\":false}],"
                        + "\"pairs\":[{\"pairId\":\"PAIR\",\"baselineRunId\":\"B\","
                        + "\"captureRunId\":\"C\",\"status\":\"EXCLUDED\","
                        + "\"ratioState\":\"NOT_EVALUATED_EXCLUDED\","
                        + "\"countsTowardMinimum\":false,\"sliceKey\":null,"
                        + "\"captureDroppedFramesObserved\":true,"
                        + "\"captureSevereThermalObserved\":false,"
                        + "\"exclusions\":[\"RUN_INCOMPLETE\"]}],"
                        + "\"slices\":[{\"sliceKey\":\"S\",\"validPairCount\":1,"
                        + "\"minimumThreeControlledRepeatsObserved\":false,"
                        + "\"localSliceState\":\"INCOMPLETE\",\"withinBoundaryCount\":0,"
                        + "\"aboveBoundaryCount\":0,\"noMwhCount\":1,"
                        + "\"droppedFramesObserved\":true,"
                        + "\"severeThermalObserved\":false}],"
                        + "\"observedPhysicalProfiles\":[\"D1\"],"
                        + "\"missingPhysicalProfiles\":[\"D2\"],"
                        + "\"issues\":[{\"kind\":\"EXCLUSION\","
                        + "\"code\":\"RUN_INCOMPLETE\",\"subjectId\":\"I\\n\"}]}";

        equal(
                expectedNonEmpty,
                nonEmpty.canonicalJson(),
                "literal non-empty escaping/null canonical JSON golden");
    }

    private static CampaignEvaluation threePairVariant(Long drops, ThermalStatus thermal) {
        Fixture fixture = fixture("THERM", DeviceProfile.D1, 3, 100L, 120L);
        List<RunObservation> runs = new ArrayList<>(fixture.runs());
        int index = indexOfRun(runs, "THERM-C1");
        RunSpec changed = RunSpec.from(runs.get(index));
        changed.droppedFrames = drops;
        changed.maxThermalStatus = thermal;
        runs.set(index, changed.build());
        return evaluate(runs, fixture.pairs());
    }

    private static CampaignEvaluation singlePairWithCapture(Consumer<RunSpec> change) {
        RunObservation baseline = run("B", RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE, 1, 100L);
        RunSpec capture = spec("C", RunMode.DORA_CAPTURE_ONLY, 1, 100L);
        change.accept(capture);
        return evaluate(List.of(baseline, capture.build()), List.of(pair("PAIR-1", "B", "C")));
    }

    private static void assertEnergyInvalid(Long value, RunMode mode) {
        RunSpec changed = spec("ENERGY", mode, 1, value);
        CampaignEvaluation result = ORACLE.evaluate(campaign(List.of(changed.build()), List.of()));
        equal(StructuralStatus.INVALID, result.structuralStatus(), "invalid energy structural status");
        hasIssue(result, IssueCode.ENERGY_MICRO_WH_INVALID, "ENERGY");
    }

    private static void assertMismatch(
            Consumer<RunSpec> change, IssueCode... expectedExclusions) {
        require(expectedExclusions.length > 0, "expected mismatch exclusions");
        IssueCode expectedMismatch = expectedExclusions[0];
        RunObservation baseline = run("B", RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE, 1, 100L);
        RunSpec capture = spec("C", RunMode.DORA_CAPTURE_ONLY, 1, 100L);
        change.accept(capture);
        CampaignEvaluation result =
                evaluate(List.of(baseline, capture.build()), List.of(pair("PAIR-1", "B", "C")));
        equal(
                StructuralStatus.VALID,
                result.structuralStatus(),
                expectedMismatch + " structural validity");
        equal(
                PairStatus.EXCLUDED,
                result.pairs().get(0).status(),
                expectedMismatch + " pair excluded");
        equal(
                List.of(expectedExclusions),
                result.pairs().get(0).exclusions(),
                expectedMismatch + " exact pair exclusions");
        hasIssue(result, expectedMismatch, "PAIR-1");
    }

    private static void assertRunExcluded(Consumer<RunSpec> change, IssueCode expected) {
        RunObservation baseline = run("B", RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE, 1, 100L);
        RunSpec capture = spec("C", RunMode.DORA_CAPTURE_ONLY, 1, 100L);
        change.accept(capture);
        CampaignEvaluation result =
                evaluate(List.of(baseline, capture.build()), List.of(pair("PAIR-1", "B", "C")));
        equal(StructuralStatus.VALID, result.structuralStatus(), expected + " valid typed input");
        require(
                result.runs().stream()
                        .filter(run -> run.runId().equals("C"))
                        .findFirst()
                        .orElseThrow()
                        .exclusions()
                        .contains(expected),
                expected + " run exclusion");
        equal(PairStatus.EXCLUDED, result.pairs().get(0).status(), expected + " pair exclusion");
    }

    private static void assertInvalid(CampaignInput input, IssueCode expected) {
        CampaignEvaluation result = ORACLE.evaluate(input);
        equal(StructuralStatus.INVALID, result.structuralStatus(), expected + " invalid");
        require(result.issues().stream().anyMatch(issue -> issue.code() == expected), expected + " issue");
        equal(List.of(), result.runs(), expected + " no partial run projection");
        equal(List.of(), result.pairs(), expected + " no partial pair projection");
    }

    private static CampaignEvaluation singlePair(Long baselineEnergy, Long captureEnergy) {
        return evaluate(
                List.of(
                        run("B", RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE, 1, baselineEnergy),
                        run("C", RunMode.DORA_CAPTURE_ONLY, 1, captureEnergy)),
                List.of(pair("PAIR-1", "B", "C")));
    }

    private static CampaignEvaluation evaluate(
            List<RunObservation> runs, List<ComparisonPair> pairs) {
        return ORACLE.evaluate(campaign(runs, pairs));
    }

    private static CampaignInput campaign(
            List<RunObservation> runs, List<ComparisonPair> pairs) {
        return new CampaignInput(
                "CAMPAIGN-SYNTHETIC-A",
                BatteryCampaignOracle.REQUIRED_POC_ID,
                BatteryCampaignOracle.REQUIRED_GATE_SET_VERSION,
                runs,
                pairs);
    }

    private static Fixture fixture(
            String prefix,
            DeviceProfile profile,
            int count,
            long baselineEnergy,
            long captureEnergy) {
        List<RunObservation> runs = new ArrayList<>();
        List<ComparisonPair> pairs = new ArrayList<>();
        for (int ordinal = 1; ordinal <= count; ordinal++) {
            String baselineId = prefix + "-B" + ordinal;
            String captureId = prefix + "-C" + ordinal;
            RunSpec baseline = spec(baselineId, RunMode.MINIMAL_AUDIO_RECORD_FGS_BASELINE, ordinal, baselineEnergy);
            RunSpec capture = spec(captureId, RunMode.DORA_CAPTURE_ONLY, ordinal, captureEnergy);
            baseline.deviceProfile = profile;
            capture.deviceProfile = profile;
            baseline.deviceInstanceId = "DEVICE-" + profile.name();
            capture.deviceInstanceId = "DEVICE-" + profile.name();
            baseline.firmwareId = "FIRMWARE-" + profile.name();
            capture.firmwareId = "FIRMWARE-" + profile.name();
            baseline.approvedSliceId = "SLICE-" + profile.name();
            capture.approvedSliceId = "SLICE-" + profile.name();
            runs.add(baseline.build());
            runs.add(capture.build());
            pairs.add(pair(prefix + "-P" + ordinal, baselineId, captureId));
        }
        return new Fixture(List.copyOf(runs), List.copyOf(pairs));
    }

    private static RunObservation run(
            String id, RunMode mode, int ordinal, Long energyMicroWh) {
        return spec(id, mode, ordinal, energyMicroWh).build();
    }

    private static RunSpec spec(String id, RunMode mode, int ordinal, Long energyMicroWh) {
        return new RunSpec(id, mode, ordinal, energyMicroWh);
    }

    private static ComparisonPair pair(String id, String baselineId, String captureId) {
        return new ComparisonPair(id, baselineId, captureId);
    }

    private static int indexOfRun(List<RunObservation> runs, String runId) {
        for (int index = 0; index < runs.size(); index++) {
            if (runs.get(index).runId().equals(runId)) {
                return index;
            }
        }
        throw new AssertionError("missing run " + runId);
    }

    private static PairAssessment pairById(CampaignEvaluation result, String pairId) {
        return result.pairs().stream()
                .filter(pair -> pair.pairId().equals(pairId))
                .findFirst()
                .orElseThrow();
    }

    private static void hasPairExclusion(
            CampaignEvaluation result, String pairId, IssueCode issueCode) {
        require(
                pairById(result, pairId).exclusions().contains(issueCode),
                pairId + " has pair exclusion " + issueCode);
    }

    private static void hasIssue(
            CampaignEvaluation result, IssueCode code, String subjectId) {
        require(
                result.issues().contains(new Issue(issueKind(code), code, subjectId)),
                "issue " + code + " for " + subjectId);
    }

    private static void lacksIssue(CampaignEvaluation result, IssueCode code) {
        require(result.issues().stream().noneMatch(issue -> issue.code() == code), "lacks " + code);
    }

    private static BatteryCampaignOracle.IssueKind issueKind(IssueCode code) {
        return switch (code) {
            case PAIR_ABOVE_RELATIVE_BOUNDARY,
                    CAPTURE_DROPPED_FRAMES_OBSERVED,
                    CAPTURE_SEVERE_THERMAL_OBSERVED -> BatteryCampaignOracle.IssueKind.OBSERVATION;
            case SLICE_FEWER_THAN_THREE_VALID_PAIRS,
                    NO_CONTROLLED_COMPARISONS,
                    PHYSICAL_PROFILE_MISSING -> BatteryCampaignOracle.IssueKind.COVERAGE;
            case RUN_INVALIDATED,
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
                    PAIR_ENERGY_MISSING -> BatteryCampaignOracle.IssueKind.EXCLUSION;
            default -> BatteryCampaignOracle.IssueKind.INVALID_INPUT;
        };
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertions++;
        }
    }

    private static void equal(Object expected, Object actual, String label) {
        assertions++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void require(boolean condition, String label) {
        assertions++;
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private record Fixture(List<RunObservation> runs, List<ComparisonPair> pairs) {}

    private static final class RunSpec {
        private String runId;
        private String attemptId;
        private String repeatId;
        private DeviceProfile deviceProfile;
        private DeviceKind deviceKind;
        private String deviceInstanceId;
        private String firmwareId;
        private int androidApi;
        private Abi abi;
        private RunMode mode;
        private int repeatOrdinal;
        private String approvedSliceId;
        private ScreenState screenState;
        private boolean batterySaverEnabled;
        private int brightnessPercent;
        private RadioState radioState;
        private SignalState signalState;
        private ThermalStatus startThermalStatus;
        private String fixtureSha256;
        private DurationUnit durationUnit;
        private long plannedDurationSeconds;
        private long actualDurationSeconds;
        private boolean completed;
        private Long energyMicroWh;
        private ChargerState chargerState;
        private PowerSource powerSource;
        private Long droppedFrames;
        private ThermalStatus maxThermalStatus;
        private RunDisposition disposition;
        private String invalidationReasonCode;
        private String protocolId;
        private String measurementSourceId;

        private RunSpec(String id, RunMode mode, int ordinal, Long energyMicroWh) {
            this.runId = id;
            this.attemptId = "ATTEMPT-" + id;
            this.repeatId = "REPEAT-" + id;
            this.deviceProfile = DeviceProfile.D1;
            this.deviceKind = DeviceKind.PHYSICAL;
            this.deviceInstanceId = "DEVICE-D1";
            this.firmwareId = "FIRMWARE-D1";
            this.androidApi = 36;
            this.abi = Abi.ARM64_V8A;
            this.mode = mode;
            this.repeatOrdinal = ordinal;
            this.approvedSliceId = "SLICE-D1";
            this.screenState = ScreenState.OFF;
            this.batterySaverEnabled = false;
            this.brightnessPercent = 40;
            this.radioState = RadioState.AIRPLANE_MODE;
            this.signalState = SignalState.NO_RADIO_SIGNAL;
            this.startThermalStatus = ThermalStatus.NONE;
            this.fixtureSha256 = DIGEST_ONE;
            this.durationUnit = DurationUnit.SECONDS;
            this.plannedDurationSeconds = 3_600L;
            this.actualDurationSeconds = 3_600L;
            this.completed = true;
            this.energyMicroWh = energyMicroWh;
            this.chargerState = ChargerState.UNPLUGGED;
            this.powerSource = PowerSource.BATTERY;
            this.droppedFrames = 0L;
            this.maxThermalStatus = ThermalStatus.NONE;
            this.disposition = RunDisposition.VALID;
            this.invalidationReasonCode = null;
            this.protocolId = "PROTOCOL-A";
            this.measurementSourceId = "BATTERYSTATS-A";
        }

        private static RunSpec from(RunObservation run) {
            RunSpec result = new RunSpec(run.runId(), run.mode(), run.repeatOrdinal(), run.energyMicroWh());
            result.attemptId = run.attemptId();
            result.repeatId = run.repeatId();
            result.deviceProfile = run.deviceProfile();
            result.deviceKind = run.deviceKind();
            result.deviceInstanceId = run.deviceInstanceId();
            result.firmwareId = run.firmwareId();
            result.androidApi = run.androidApi();
            result.abi = run.abi();
            result.approvedSliceId = run.approvedSliceId();
            result.screenState = run.screenState();
            result.batterySaverEnabled = run.batterySaverEnabled();
            result.brightnessPercent = run.brightnessPercent();
            result.radioState = run.radioState();
            result.signalState = run.signalState();
            result.startThermalStatus = run.startThermalStatus();
            result.fixtureSha256 = run.fixtureSha256();
            result.durationUnit = run.durationUnit();
            result.plannedDurationSeconds = run.plannedDurationSeconds();
            result.actualDurationSeconds = run.actualDurationSeconds();
            result.completed = run.completed();
            result.chargerState = run.chargerState();
            result.powerSource = run.powerSource();
            result.droppedFrames = run.droppedFrames();
            result.maxThermalStatus = run.maxThermalStatus();
            result.disposition = run.disposition();
            result.invalidationReasonCode = run.invalidationReasonCode();
            result.protocolId = run.protocolId();
            result.measurementSourceId = run.measurementSourceId();
            return result;
        }

        private RunObservation build() {
            return new RunObservation(
                    runId,
                    attemptId,
                    repeatId,
                    deviceProfile,
                    deviceKind,
                    deviceInstanceId,
                    firmwareId,
                    androidApi,
                    abi,
                    mode,
                    repeatOrdinal,
                    approvedSliceId,
                    screenState,
                    batterySaverEnabled,
                    brightnessPercent,
                    radioState,
                    signalState,
                    startThermalStatus,
                    fixtureSha256,
                    durationUnit,
                    plannedDurationSeconds,
                    actualDurationSeconds,
                    completed,
                    energyMicroWh,
                    chargerState,
                    powerSource,
                    droppedFrames,
                    maxThermalStatus,
                    disposition,
                    invalidationReasonCode,
                    protocolId,
                    measurementSourceId);
        }
    }
}
