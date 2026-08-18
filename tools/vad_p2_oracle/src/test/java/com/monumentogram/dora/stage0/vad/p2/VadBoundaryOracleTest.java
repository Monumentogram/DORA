package com.monumentogram.dora.stage0.vad.p2;

import com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracle.Applied;
import com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracle.CreateRejected;
import com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracle.Created;
import com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracle.FrameClass;
import com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracle.PhysicalCap;
import com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracle.Profile;
import com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracle.RejectCode;
import com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracle.Rejected;
import com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracle.RestoreRejected;
import com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracle.Restored;
import com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracle.SemanticBoundary;
import com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracle.SemanticPhase;
import com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracle.State;
import com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracle.StepResult;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class VadBoundaryOracleTest {
    private static final String PASS_MARKER = "LOCAL_PASS vad-p2-frame-timing-host-oracle";

    private VadBoundaryOracleTest() {}

    public static void main(String[] ignored) {
        constantsAndProfilesAreExact();
        initialSilenceNeverCreatesSemanticGroup();
        semanticBoundaryCasesAreExact();
        everyPreBoundaryResumeCancels();
        physicalCapMetadataIsExact();
        simultaneousSemanticAndPhysicalEventsAreIndependent();
        invalidInputsAreFailClosed();
        restoreValidationIsExact();
        terminalArithmeticIsBounded();
        replayAndRestoreAreDeterministic();
        independentProfilesDoNotContaminateEachOther();
        oracleHasNoMutableStaticState();
        System.out.println(PASS_MARKER);
    }

    private static void constantsAndProfilesAreExact() {
        checkEquals(16_000, VadBoundaryOracle.SAMPLE_RATE_HZ, "sample rate");
        checkEquals(320, VadBoundaryOracle.SAMPLES_PER_FRAME, "samples per frame");
        checkEquals(20_000_000L, VadBoundaryOracle.FRAME_NANOS, "frame nanos");
        checkEquals(4_500L, VadBoundaryOracle.SILENCE_BOUNDARY_FRAMES, "silence frames literal");
        checkEquals(30_000L, VadBoundaryOracle.PHYSICAL_CAP_FRAMES, "cap frames literal");
        checkEquals(75, VadBoundaryOracle.MIN_OVERLAP_FRAMES, "minimum overlap literal");
        checkEquals(100, VadBoundaryOracle.MAX_OVERLAP_FRAMES, "maximum overlap literal");
        checkEquals(
                90_000_000_000L,
                Math.multiplyExact(
                        VadBoundaryOracle.SILENCE_BOUNDARY_FRAMES,
                        VadBoundaryOracle.FRAME_NANOS),
                "silence boundary");
        checkEquals(
                600_000_000_000L,
                Math.multiplyExact(
                        VadBoundaryOracle.PHYSICAL_CAP_FRAMES,
                        VadBoundaryOracle.FRAME_NANOS),
                "physical cap");
        checkEquals(
                1_500_000_000L,
                Math.multiplyExact(
                        VadBoundaryOracle.MIN_OVERLAP_FRAMES,
                        VadBoundaryOracle.FRAME_NANOS),
                "minimum overlap");
        checkEquals(
                2_000_000_000L,
                Math.multiplyExact(
                        VadBoundaryOracle.MAX_OVERLAP_FRAMES,
                        VadBoundaryOracle.FRAME_NANOS),
                "maximum overlap");
        checkEquals(
                Long.MAX_VALUE / VadBoundaryOracle.FRAME_NANOS,
                VadBoundaryOracle.MAX_SAFE_FRAME_COUNT,
                "maximum safe count");

        for (int overlap = 75; overlap <= 100; overlap++) {
            Profile profile = profile(overlap);
            checkEquals(overlap, profile.overlapFrames(), "accepted overlap");
            checkEquals(profile, profile(overlap), "profile value equality");
            checkEquals(profile.hashCode(), profile(overlap).hashCode(), "profile hash equality");
        }
        assertCreateRejected(74, RejectCode.OVERLAP_OUT_OF_RANGE);
        assertCreateRejected(101, RejectCode.OVERLAP_OUT_OF_RANGE);
        assertCreateRejected(Integer.MIN_VALUE, RejectCode.OVERLAP_OUT_OF_RANGE);
        assertCreateRejected(Integer.MAX_VALUE, RejectCode.OVERLAP_OUT_OF_RANGE);

        boolean nullInitialRejected = false;
        try {
            VadBoundaryOracle.initial(null);
        } catch (IllegalArgumentException expected) {
            nullInitialRejected = true;
        }
        checkTrue(nullInitialRejected, "null initial profile is a documented caller error");
    }

    private static void initialSilenceNeverCreatesSemanticGroup() {
        Profile profile = profile(75);
        Run run = run(profile, VadBoundaryOracle.initial(profile), 30_000, FrameClass.SILENCE);
        checkEquals(0, run.semanticBoundaries().size(), "initial silence boundaries");
        checkEquals(1, run.physicalCaps().size(), "initial silence cap");
        checkEquals(SemanticPhase.AWAITING_SPEECH, run.state().phase(), "initial silence phase");
        checkEquals(0L, run.state().semanticGroupsOpened(), "initial silence groups");
        checkEquals(0L, run.state().semanticBoundariesEmitted(), "initial silence emitted");
        checkEquals(0L, run.state().consecutiveSilenceFrames(), "initial silence counter");
    }

    private static void semanticBoundaryCasesAreExact() {
        Profile profile = profile(75);

        Run at895 = speechThenSilence(profile, 4_475);
        checkEquals(0, at895.semanticBoundaries().size(), "89.5 second boundary");
        checkEquals(4_475L, at895.state().consecutiveSilenceFrames(), "89.5 counter");

        Run at8998 = speechThenSilence(profile, 4_499);
        checkEquals(0, at8998.semanticBoundaries().size(), "89.98 second boundary");
        checkEquals(4_499L, at8998.state().consecutiveSilenceFrames(), "89.98 counter");

        Run at900 = speechThenSilence(profile, 4_500);
        checkEquals(1, at900.semanticBoundaries().size(), "90 second boundary");
        SemanticBoundary boundary = at900.semanticBoundaries().get(0);
        checkEquals(0L, boundary.groupOrdinal(), "first group ordinal");
        checkEquals(1L, boundary.silenceStartFrameInclusive(), "silence start");
        checkEquals(4_501L, boundary.boundaryAfterFrameExclusive(), "boundary frame");
        checkEquals(90_000_000_000L, boundary.silenceDurationNanos(), "silence duration");
        checkEquals(90_020_000_000L, boundary.boundaryTimelineNanos(), "timeline duration");
        checkEquals(SemanticPhase.AWAITING_SPEECH, at900.state().phase(), "post-boundary phase");
        checkEquals(0L, at900.state().consecutiveSilenceFrames(), "post-boundary counter");
        checkEquals(1L, at900.state().semanticGroupsOpened(), "groups after boundary");
        checkEquals(1L, at900.state().semanticBoundariesEmitted(), "boundaries after boundary");

        Run at905 = speechThenSilence(profile, 4_525);
        checkEquals(1, at905.semanticBoundaries().size(), "90.5 second boundary count");
        checkEquals(SemanticPhase.AWAITING_SPEECH, at905.state().phase(), "90.5 phase");

        State state = VadBoundaryOracle.initial(profile);
        state = applied(profile, state, FrameClass.SPEECH).after();
        state = run(profile, state, 4_500, FrameClass.SILENCE).state();
        state = run(profile, state, 2_000, FrameClass.SILENCE).state();
        Applied newSpeech = applied(profile, state, FrameClass.SPEECH);
        checkEquals(SemanticPhase.ACTIVE_SPEECH, newSpeech.after().phase(), "second group phase");
        checkEquals(2L, newSpeech.after().semanticGroupsOpened(), "second group opened");
        Run secondBoundary = run(profile, newSpeech.after(), 4_500, FrameClass.SILENCE);
        checkEquals(1, secondBoundary.semanticBoundaries().size(), "second boundary count");
        checkEquals(1L, secondBoundary.semanticBoundaries().get(0).groupOrdinal(), "second ordinal");

        for (int silenceLength = 0; silenceLength <= 4_525; silenceLength++) {
            Run candidate = speechThenSilence(profile, silenceLength);
            int expected = silenceLength >= 4_500 ? 1 : 0;
            checkEquals(expected, candidate.semanticBoundaries().size(), "exhaustive boundary length");
        }
    }

    private static void everyPreBoundaryResumeCancels() {
        Profile profile = profile(100);
        for (int silenceLength = 0; silenceLength < 4_500; silenceLength++) {
            State state = VadBoundaryOracle.initial(profile);
            state = applied(profile, state, FrameClass.SPEECH).after();
            Run silence = run(profile, state, silenceLength, FrameClass.SILENCE);
            Applied resumed = applied(profile, silence.state(), FrameClass.SPEECH);
            checkTrue(resumed.semanticBoundary().isEmpty(), "resume boundary absent");
            checkEquals(SemanticPhase.ACTIVE_SPEECH, resumed.after().phase(), "resume phase");
            checkEquals(0L, resumed.after().consecutiveSilenceFrames(), "resume counter");
            checkEquals(0L, resumed.after().semanticBoundariesEmitted(), "resume emitted count");
        }

        State resume899 = VadBoundaryOracle.initial(profile);
        resume899 = applied(profile, resume899, FrameClass.SPEECH).after();
        resume899 = run(profile, resume899, 4_495, FrameClass.SILENCE).state();
        Applied resumed = applied(profile, resume899, FrameClass.SPEECH);
        checkEquals(4_497L, resumed.after().nextFrameIndex(), "89.9 resume timeline");
        checkTrue(resumed.semanticBoundary().isEmpty(), "89.9 resume cancellation");
    }

    private static void physicalCapMetadataIsExact() {
        Profile profile = profile(75);
        State beforeFirst =
                restored(
                        profile,
                        new State(
                                1,
                                75,
                                29_999L,
                                SemanticPhase.AWAITING_SPEECH,
                                0L,
                                0L,
                                0L));
        Applied first = applied(profile, beforeFirst, FrameClass.SILENCE);
        PhysicalCap firstCap = requiredCap(first);
        checkEquals(0L, firstCap.partOrdinal(), "first part ordinal");
        checkEquals(0L, firstCap.freshStartFrameInclusive(), "first fresh start");
        checkEquals(30_000L, firstCap.freshEndFrameExclusive(), "first fresh end");
        checkEquals(29_925L, firstCap.overlapSourceStartFrameInclusive(), "first overlap start");
        checkEquals(30_000L, firstCap.overlapSourceEndFrameExclusive(), "first overlap end");
        checkTrue(
                applied(profile, first.after(), FrameClass.SILENCE).physicalCap().isEmpty(),
                "no duplicate cap");

        State beforeSecond =
                restored(
                        profile,
                        new State(
                                1,
                                75,
                                59_999L,
                                SemanticPhase.AWAITING_SPEECH,
                                0L,
                                0L,
                                0L));
        PhysicalCap secondCap = requiredCap(applied(profile, beforeSecond, FrameClass.SILENCE));
        checkEquals(1L, secondCap.partOrdinal(), "second part ordinal");
        checkEquals(30_000L, secondCap.freshStartFrameInclusive(), "second fresh start");
        checkEquals(60_000L, secondCap.freshEndFrameExclusive(), "second fresh end");
        checkEquals(75, secondCap.overlapFramesForNextPart(), "second overlap frames");
        checkEquals(59_925L, secondCap.overlapSourceStartFrameInclusive(), "second overlap start");
        checkEquals(60_000L, secondCap.overlapSourceEndFrameExclusive(), "second overlap end");

        for (int overlap = 75; overlap <= 100; overlap++) {
            Profile candidate = profile(overlap);
            State before =
                    restored(
                            candidate,
                            new State(
                                    1,
                                    overlap,
                                    29_999L,
                                    SemanticPhase.AWAITING_SPEECH,
                                    0L,
                                    0L,
                                    0L));
            PhysicalCap cap = requiredCap(applied(candidate, before, FrameClass.SILENCE));
            checkEquals(overlap, cap.overlapFramesForNextPart(), "overlap metadata");
            checkEquals(30_000L - overlap, cap.overlapSourceStartFrameInclusive(), "overlap start");
            checkEquals(30_000L, cap.overlapSourceEndFrameExclusive(), "overlap end");
        }

        Run continuousSpeech =
                run(profile, VadBoundaryOracle.initial(profile), 30_000, FrameClass.SPEECH);
        checkEquals(1, continuousSpeech.physicalCaps().size(), "continuous speech cap");
        checkEquals(SemanticPhase.ACTIVE_SPEECH, continuousSpeech.state().phase(), "speech phase");
        checkEquals(1L, continuousSpeech.state().semanticGroupsOpened(), "speech group");

        Run twoCaps = run(profile, VadBoundaryOracle.initial(profile), 60_001, FrameClass.SILENCE);
        checkEquals(2, twoCaps.physicalCaps().size(), "two cap count");
        checkEquals(0L, twoCaps.physicalCaps().get(0).partOrdinal(), "two cap first ordinal");
        checkEquals(1L, twoCaps.physicalCaps().get(1).partOrdinal(), "two cap second ordinal");
    }

    private static void simultaneousSemanticAndPhysicalEventsAreIndependent() {
        Profile profile = profile(100);
        State before =
                restored(
                        profile,
                        new State(
                                1,
                                100,
                                29_999L,
                                SemanticPhase.ACTIVE_SILENCE,
                                4_499L,
                                1L,
                                0L));
        Applied applied = applied(profile, before, FrameClass.SILENCE);
        checkTrue(applied.semanticBoundary().isPresent(), "simultaneous semantic event");
        checkTrue(applied.physicalCap().isPresent(), "simultaneous physical event");
        checkEquals(SemanticPhase.AWAITING_SPEECH, applied.after().phase(), "simultaneous phase");
        checkEquals(1L, applied.after().semanticBoundariesEmitted(), "simultaneous counter");
        SemanticBoundary boundary = applied.semanticBoundary().orElseThrow();
        checkEquals(0L, boundary.groupOrdinal(), "simultaneous group ordinal");
        checkEquals(25_500L, boundary.silenceStartFrameInclusive(), "simultaneous silence start");
        checkEquals(30_000L, boundary.boundaryAfterFrameExclusive(), "simultaneous boundary frame");
        checkEquals(90_000_000_000L, boundary.silenceDurationNanos(), "simultaneous silence duration");
        checkEquals(600_000_000_000L, boundary.boundaryTimelineNanos(), "simultaneous timeline");
        checkEquals(30_000L, requiredCap(applied).freshEndFrameExclusive(), "simultaneous cap end");
    }

    private static void invalidInputsAreFailClosed() {
        Profile profile = profile(75);
        State initial = VadBoundaryOracle.initial(profile);

        assertRejected(
                VadBoundaryOracle.step(null, initial, 0L, FrameClass.SPEECH),
                RejectCode.NULL_PROFILE,
                initial);
        assertRejected(
                VadBoundaryOracle.step(profile, null, 0L, FrameClass.SPEECH),
                RejectCode.NULL_STATE,
                null);
        assertRejected(
                VadBoundaryOracle.step(profile, initial, 0L, null),
                RejectCode.NULL_FRAME_CLASS,
                initial);
        assertRejected(
                VadBoundaryOracle.step(profile, initial, -1L, FrameClass.SPEECH),
                RejectCode.FRAME_INDEX_NEGATIVE,
                initial);
        assertRejected(
                VadBoundaryOracle.step(profile, initial, 1L, FrameClass.SPEECH),
                RejectCode.FRAME_INDEX_MISMATCH,
                initial);
        State nonzero = applied(profile, initial, FrameClass.SPEECH).after();
        assertRejected(
                VadBoundaryOracle.step(profile, nonzero, 0L, FrameClass.SILENCE),
                RejectCode.FRAME_INDEX_MISMATCH,
                nonzero);

        State invalidSchema =
                new State(2, 75, 0L, SemanticPhase.AWAITING_SPEECH, 0L, 0L, 0L);
        assertRejected(
                VadBoundaryOracle.step(profile, invalidSchema, 0L, FrameClass.SPEECH),
                RejectCode.SCHEMA_MISMATCH,
                invalidSchema);

        State invalidProfile =
                new State(1, 76, 0L, SemanticPhase.AWAITING_SPEECH, 0L, 0L, 0L);
        assertRejected(
                VadBoundaryOracle.step(profile, invalidProfile, 0L, FrameClass.SPEECH),
                RejectCode.PROFILE_MISMATCH,
                invalidProfile);

        Applied control = applied(profile, initial, FrameClass.SPEECH);
        StepResult rejected = VadBoundaryOracle.step(profile, initial, 10L, FrameClass.SPEECH);
        assertRejected(rejected, RejectCode.FRAME_INDEX_MISMATCH, initial);
        Applied afterRejected = applied(profile, initial, FrameClass.SPEECH);
        checkEquals(control, afterRejected, "rejected input leaves future result unchanged");
    }

    private static void restoreValidationIsExact() {
        Profile profile = profile(75);
        State initial = VadBoundaryOracle.initial(profile);
        Restored restored = requireType(VadBoundaryOracle.restore(profile, initial), Restored.class);
        checkEquals(initial, restored.state(), "initial restore equality");
        checkTrue(initial != restored.state(), "restore defensive copy");
        State activeSpeech =
                restored(
                        profile,
                        new State(1, 75, 1L, SemanticPhase.ACTIVE_SPEECH, 0L, 1L, 0L));
        checkEquals(SemanticPhase.ACTIVE_SPEECH, activeSpeech.phase(), "active speech restore");
        State activeSilence =
                restored(
                        profile,
                        new State(1, 75, 2L, SemanticPhase.ACTIVE_SILENCE, 1L, 1L, 0L));
        checkEquals(SemanticPhase.ACTIVE_SILENCE, activeSilence.phase(), "active silence restore");

        assertRestoreRejected(null, initial, RejectCode.NULL_PROFILE);
        assertRestoreRejected(profile, null, RejectCode.NULL_STATE);
        assertRestoreRejected(
                profile,
                new State(0, 75, 0L, SemanticPhase.AWAITING_SPEECH, 0L, 0L, 0L),
                RejectCode.SCHEMA_MISMATCH);
        assertRestoreRejected(
                profile,
                new State(1, 76, 0L, SemanticPhase.AWAITING_SPEECH, 0L, 0L, 0L),
                RejectCode.PROFILE_MISMATCH);
        assertRestoreRejected(
                profile,
                new State(1, 75, -1L, SemanticPhase.AWAITING_SPEECH, 0L, 0L, 0L),
                RejectCode.NEXT_FRAME_OUT_OF_RANGE);
        assertRestoreRejected(
                profile,
                new State(
                        1,
                        75,
                        VadBoundaryOracle.MAX_SAFE_FRAME_COUNT + 1L,
                        SemanticPhase.AWAITING_SPEECH,
                        0L,
                        0L,
                        0L),
                RejectCode.NEXT_FRAME_OUT_OF_RANGE);
        assertRestoreRejected(
                profile,
                new State(1, 75, 0L, null, 0L, 0L, 0L),
                RejectCode.PHASE_MISSING);
        assertRestoreRejected(
                profile,
                new State(1, 75, 1L, SemanticPhase.AWAITING_SPEECH, 1L, 0L, 0L),
                RejectCode.SILENCE_INVARIANT);
        assertRestoreRejected(
                profile,
                new State(1, 75, 2L, SemanticPhase.ACTIVE_SPEECH, 1L, 1L, 0L),
                RejectCode.SILENCE_INVARIANT);
        assertRestoreRejected(
                profile,
                new State(1, 75, 1L, SemanticPhase.ACTIVE_SILENCE, 0L, 1L, 0L),
                RejectCode.SILENCE_INVARIANT);
        assertRestoreRejected(
                profile,
                new State(1, 75, 1L, SemanticPhase.ACTIVE_SILENCE, -1L, 1L, 0L),
                RejectCode.SILENCE_INVARIANT);
        assertRestoreRejected(
                profile,
                new State(1, 75, 4_501L, SemanticPhase.ACTIVE_SILENCE, 4_500L, 1L, 0L),
                RejectCode.SILENCE_INVARIANT);
        assertRestoreRejected(
                profile,
                new State(1, 75, 0L, SemanticPhase.AWAITING_SPEECH, 0L, -1L, 0L),
                RejectCode.GROUP_COUNTER_INVARIANT);
        assertRestoreRejected(
                profile,
                new State(1, 75, 0L, SemanticPhase.AWAITING_SPEECH, 0L, 0L, -1L),
                RejectCode.GROUP_COUNTER_INVARIANT);
        assertRestoreRejected(
                profile,
                new State(1, 75, 10L, SemanticPhase.AWAITING_SPEECH, 0L, 1L, 2L),
                RejectCode.GROUP_COUNTER_INVARIANT);
        assertRestoreRejected(
                profile,
                new State(1, 75, 10L, SemanticPhase.AWAITING_SPEECH, 0L, 1L, 0L),
                RejectCode.GROUP_COUNTER_INVARIANT);
        assertRestoreRejected(
                profile,
                new State(1, 75, 10L, SemanticPhase.ACTIVE_SPEECH, 0L, 0L, 0L),
                RejectCode.GROUP_COUNTER_INVARIANT);
        assertRestoreRejected(
                profile,
                new State(1, 75, 10L, SemanticPhase.ACTIVE_SPEECH, 0L, 2L, 0L),
                RejectCode.GROUP_COUNTER_INVARIANT);
        assertRestoreRejected(
                profile,
                new State(1, 75, 1L, SemanticPhase.ACTIVE_SPEECH, 0L, 2L, 1L),
                RejectCode.GROUP_COUNTER_INVARIANT);
        assertRestoreRejected(
                profile,
                new State(1, 75, 4_500L, SemanticPhase.AWAITING_SPEECH, 0L, 1L, 1L),
                RejectCode.GROUP_COUNTER_INVARIANT);
        assertRestoreRejected(
                profile,
                new State(1, 75, 1L, SemanticPhase.ACTIVE_SILENCE, 1L, 1L, 0L),
                RejectCode.GROUP_COUNTER_INVARIANT);

        State minimalCompleted =
                restored(
                        profile,
                        new State(
                                1,
                                75,
                                4_501L,
                                SemanticPhase.AWAITING_SPEECH,
                                0L,
                                1L,
                                1L));
        checkEquals(4_501L, minimalCompleted.nextFrameIndex(), "minimal completed restore");

        assertRestoreRejected(
                profile,
                new State(
                        1,
                        75,
                        VadBoundaryOracle.MAX_SAFE_FRAME_COUNT,
                        SemanticPhase.AWAITING_SPEECH,
                        0L,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE),
                RejectCode.GROUP_COUNTER_INVARIANT);
    }

    private static void terminalArithmeticIsBounded() {
        Profile profile = profile(100);
        long maximum = VadBoundaryOracle.MAX_SAFE_FRAME_COUNT;
        long exactNanos = Math.multiplyExact(maximum, VadBoundaryOracle.FRAME_NANOS);
        checkTrue(exactNanos >= 0L, "maximum duration is nonnegative");

        State beforeTerminal =
                restored(
                        profile,
                        new State(
                                1,
                                100,
                                maximum - 1L,
                                SemanticPhase.AWAITING_SPEECH,
                                0L,
                                0L,
                                0L));
        Applied terminal = applied(profile, beforeTerminal, FrameClass.SILENCE);
        checkEquals(maximum, terminal.after().nextFrameIndex(), "terminal frame applied");
        assertRejected(
                VadBoundaryOracle.step(
                        profile,
                        terminal.after(),
                        terminal.after().nextFrameIndex(),
                        FrameClass.SILENCE),
                RejectCode.TIME_RANGE_EXHAUSTED,
                terminal.after());

        State nearBoundary =
                restored(
                        profile,
                        new State(
                                1,
                                100,
                                maximum - 1L,
                                SemanticPhase.ACTIVE_SILENCE,
                                4_499L,
                                1L,
                                0L));
        SemanticBoundary boundary =
                applied(profile, nearBoundary, FrameClass.SILENCE).semanticBoundary().orElseThrow();
        checkEquals(exactNanos, boundary.boundaryTimelineNanos(), "maximum boundary timeline");
        checkEquals(90_000_000_000L, boundary.silenceDurationNanos(), "maximum boundary silence");

        long capEnd = (maximum / VadBoundaryOracle.PHYSICAL_CAP_FRAMES) * VadBoundaryOracle.PHYSICAL_CAP_FRAMES;
        State beforeCap =
                restored(
                        profile,
                        new State(
                                1,
                                100,
                                capEnd - 1L,
                                SemanticPhase.AWAITING_SPEECH,
                                0L,
                                0L,
                                0L));
        PhysicalCap cap = requiredCap(applied(profile, beforeCap, FrameClass.SILENCE));
        checkEquals(capEnd, cap.freshEndFrameExclusive(), "large cap end");
        checkTrue(cap.freshStartFrameInclusive() >= 0L, "large cap start");
        checkTrue(cap.overlapSourceStartFrameInclusive() >= 0L, "large overlap start");
    }

    private static void replayAndRestoreAreDeterministic() {
        Profile profile = profile(88);
        Run first = deterministicScenario(profile);
        Run second = deterministicScenario(profile);
        checkEquals(first, second, "identical runs");
        checkEquals(
                new State(1, 88, 60_001L, SemanticPhase.ACTIVE_SILENCE, 1_001L, 12L, 11L),
                first.state(),
                "literal deterministic terminal state");
        checkEquals(11, first.semanticBoundaries().size(), "literal deterministic boundary count");
        checkEquals(
                new SemanticBoundary(0L, 700L, 5_200L, 90_000_000_000L, 104_000_000_000L),
                first.semanticBoundaries().get(0),
                "literal deterministic first boundary");
        checkEquals(
                new SemanticBoundary(
                        10L, 53_700L, 58_200L, 90_000_000_000L, 1_164_000_000_000L),
                first.semanticBoundaries().get(10),
                "literal deterministic last boundary");
        checkEquals(
                List.of(
                        new PhysicalCap(0L, 0L, 30_000L, 88, 29_912L, 30_000L),
                        new PhysicalCap(1L, 30_000L, 60_000L, 88, 59_912L, 60_000L)),
                first.physicalCaps(),
                "literal deterministic cap metadata");

        for (int repetition = 0; repetition < 100; repetition++) {
            checkEquals(first, deterministicScenario(profile), "100 deterministic repeats");
        }

        State prefix = VadBoundaryOracle.initial(profile);
        prefix = applied(profile, prefix, FrameClass.SPEECH).after();
        prefix = run(profile, prefix, 2_000, FrameClass.SILENCE).state();
        State resumed = restored(profile, prefix);
        Run resumedTail = run(profile, resumed, 2_500, FrameClass.SILENCE);
        Run uninterrupted = speechThenSilence(profile, 4_500);
        checkEquals(uninterrupted.state(), resumedTail.state(), "snapshot resumed state");
        checkEquals(
                uninterrupted.semanticBoundaries(),
                resumedTail.semanticBoundaries(),
                "snapshot resumed event");

        State control = VadBoundaryOracle.initial(profile);
        State withRejected = VadBoundaryOracle.initial(profile);
        for (int frame = 0; frame < 5_000; frame++) {
            FrameClass frameClass = frame % 257 == 0 ? FrameClass.SPEECH : FrameClass.SILENCE;
            control = applied(profile, control, frameClass).after();
            State sameReference = withRejected;
            assertRejected(
                    VadBoundaryOracle.step(
                            profile,
                            withRejected,
                            withRejected.nextFrameIndex() + 1L,
                            frameClass),
                    RejectCode.FRAME_INDEX_MISMATCH,
                    sameReference);
            withRejected = applied(profile, withRejected, frameClass).after();
        }
        checkEquals(control, withRejected, "rejections do not alter replay");
    }

    private static void independentProfilesDoNotContaminateEachOther() {
        Profile shortOverlap = profile(75);
        Profile longOverlap = profile(100);
        State shortState = VadBoundaryOracle.initial(shortOverlap);
        State longState = VadBoundaryOracle.initial(longOverlap);
        Run shortControl = runPattern(shortOverlap, 30_000);
        Run longControl = runPattern(longOverlap, 30_000);

        List<SemanticBoundary> shortSemantic = new ArrayList<>();
        List<SemanticBoundary> longSemantic = new ArrayList<>();
        List<PhysicalCap> shortCaps = new ArrayList<>();
        List<PhysicalCap> longCaps = new ArrayList<>();
        for (int frame = 0; frame < 30_000; frame++) {
            FrameClass frameClass = pattern(frame);
            Applied shortApplied = applied(shortOverlap, shortState, frameClass);
            Applied longApplied = applied(longOverlap, longState, frameClass);
            shortState = shortApplied.after();
            longState = longApplied.after();
            shortApplied.semanticBoundary().ifPresent(shortSemantic::add);
            longApplied.semanticBoundary().ifPresent(longSemantic::add);
            shortApplied.physicalCap().ifPresent(shortCaps::add);
            longApplied.physicalCap().ifPresent(longCaps::add);
        }
        checkEquals(shortControl.state(), shortState, "short interleaved state");
        checkEquals(longControl.state(), longState, "long interleaved state");
        checkEquals(shortControl.semanticBoundaries(), shortSemantic, "short interleaved semantic");
        checkEquals(longControl.semanticBoundaries(), longSemantic, "long interleaved semantic");
        checkEquals(shortControl.physicalCaps(), shortCaps, "short interleaved caps");
        checkEquals(longControl.physicalCaps(), longCaps, "long interleaved caps");
        checkEquals(shortState.phase(), longState.phase(), "profile-independent semantic phase");
        checkEquals(
                shortState.semanticBoundariesEmitted(),
                longState.semanticBoundariesEmitted(),
                "profile-independent boundary count");
        checkEquals(75, shortCaps.get(0).overlapFramesForNextPart(), "short overlap isolated");
        checkEquals(100, longCaps.get(0).overlapFramesForNextPart(), "long overlap isolated");
    }

    private static void oracleHasNoMutableStaticState() {
        for (Field field : VadBoundaryOracle.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                checkTrue(Modifier.isFinal(field.getModifiers()), "outer static field is final");
            }
        }
        for (Field field : Profile.class.getDeclaredFields()) {
            checkTrue(Modifier.isFinal(field.getModifiers()), "profile field is final");
        }
        checkEquals(0, VadBoundaryOracle.class.getConstructors().length, "no public constructor");
    }

    private static Run deterministicScenario(Profile profile) {
        return runPattern(profile, 60_001);
    }

    private static Run runPattern(Profile profile, int frameCount) {
        State state = VadBoundaryOracle.initial(profile);
        List<SemanticBoundary> semantic = new ArrayList<>();
        List<PhysicalCap> caps = new ArrayList<>();
        for (int frame = 0; frame < frameCount; frame++) {
            Applied applied = applied(profile, state, pattern(frame));
            state = applied.after();
            applied.semanticBoundary().ifPresent(semantic::add);
            applied.physicalCap().ifPresent(caps::add);
        }
        return new Run(state, List.copyOf(semantic), List.copyOf(caps));
    }

    private static FrameClass pattern(int frame) {
        int cycle = frame % 5_300;
        if (cycle < 700) {
            return FrameClass.SPEECH;
        }
        return FrameClass.SILENCE;
    }

    private static Run speechThenSilence(Profile profile, int silenceFrames) {
        State state = VadBoundaryOracle.initial(profile);
        Applied speech = applied(profile, state, FrameClass.SPEECH);
        return run(profile, speech.after(), silenceFrames, FrameClass.SILENCE);
    }

    private static Run run(Profile profile, State initial, int frameCount, FrameClass frameClass) {
        State state = initial;
        List<SemanticBoundary> semantic = new ArrayList<>();
        List<PhysicalCap> caps = new ArrayList<>();
        for (int index = 0; index < frameCount; index++) {
            Applied applied = applied(profile, state, frameClass);
            state = applied.after();
            applied.semanticBoundary().ifPresent(semantic::add);
            applied.physicalCap().ifPresent(caps::add);
        }
        return new Run(state, List.copyOf(semantic), List.copyOf(caps));
    }

    private static Applied applied(Profile profile, State state, FrameClass frameClass) {
        StepResult result =
                VadBoundaryOracle.step(profile, state, state.nextFrameIndex(), frameClass);
        return requireType(result, Applied.class);
    }

    private static Profile profile(int overlapFrames) {
        return requireType(VadBoundaryOracle.createProfile(overlapFrames), Created.class).profile();
    }

    private static State restored(Profile profile, State candidate) {
        return requireType(VadBoundaryOracle.restore(profile, candidate), Restored.class).state();
    }

    private static PhysicalCap requiredCap(Applied applied) {
        return applied.physicalCap().orElseThrow(() -> new AssertionError("physical cap absent"));
    }

    private static void assertCreateRejected(int overlapFrames, RejectCode expected) {
        CreateRejected rejected =
                requireType(VadBoundaryOracle.createProfile(overlapFrames), CreateRejected.class);
        checkEquals(expected, rejected.code(), "profile rejection");
    }

    private static void assertRestoreRejected(
            Profile profile, State state, RejectCode expected) {
        RestoreRejected rejected =
                requireType(VadBoundaryOracle.restore(profile, state), RestoreRejected.class);
        checkEquals(expected, rejected.code(), "restore rejection");
    }

    private static void assertRejected(
            StepResult result, RejectCode expected, State expectedReference) {
        Rejected rejected = requireType(result, Rejected.class);
        checkEquals(expected, rejected.code(), "step rejection");
        checkTrue(rejected.unchanged() == expectedReference, "rejection returns same state reference");
    }

    private static <T> T requireType(Object value, Class<T> type) {
        if (!type.isInstance(value)) {
            throw new AssertionError(
                    "Expected "
                            + type.getSimpleName()
                            + " but was "
                            + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        return type.cast(value);
    }

    private static void checkTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void checkEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private record Run(
            State state,
            List<SemanticBoundary> semanticBoundaries,
            List<PhysicalCap> physicalCaps) {}
}
