package com.monumentogram.dora.stage0.vad.p3;

import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.Accepted;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.Frame;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.FrameClass;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.PhysicalCap;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.Profile;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.ProfileAccepted;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.ProfileRejected;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.RejectCode;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.Rejected;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.ReplayInput;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.RestoreRejected;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.Restored;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.SemanticBoundary;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.SemanticPhase;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.Snapshot;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.SpeechOnset;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class VadDeterministicReplayTest {
    private static final String PASS_MARKER = "LOCAL_PASS vad-p3-deterministic-integrated-replay";

    private VadDeterministicReplayTest() {}

    public static void main(String[] ignored) {
        constantsAndExactHarnessProfileAreFrozen();
        replayInputIsDefensivelyCopied();
        preRollMetadataIsExactAndBounded();
        semanticBoundaryGoldensAreExact();
        resumeAtEightyNinePointNineCancelsBoundary();
        physicalCapsAndOverlapLinksAreExact();
        semanticBoundaryAndPhysicalCapCanCoincide();
        invalidInputsAndSnapshotsFailClosed();
        restoreAndChunkingMatchUninterruptedReplay();
        deterministicKnownAnswerIsStable();
        noMutableStaticStateExists();
        System.out.println(PASS_MARKER);
    }

    private static void constantsAndExactHarnessProfileAreFrozen() {
        checkEquals(16_000, VadDeterministicReplay.SAMPLE_RATE_HZ, "sample rate");
        checkEquals(320, VadDeterministicReplay.SAMPLES_PER_FRAME, "frame samples");
        checkEquals(20_000_000L, VadDeterministicReplay.FRAME_NANOS, "frame nanos");
        checkEquals(4_500L, VadDeterministicReplay.SEMANTIC_BOUNDARY_FRAMES, "90 seconds");
        checkEquals(30_000L, VadDeterministicReplay.FRESH_PHYSICAL_CAP_FRAMES, "600 seconds");
        checkEquals(100, VadDeterministicReplay.HARNESS_PRE_ROLL_FRAMES, "2 second pre-roll");
        checkEquals(75, VadDeterministicReplay.HARNESS_OVERLAP_FRAMES, "1.5 second overlap");
        checkEquals(
                90_000_000_000L,
                Math.multiplyExact(
                        VadDeterministicReplay.SEMANTIC_BOUNDARY_FRAMES,
                        VadDeterministicReplay.FRAME_NANOS),
                "semantic duration");
        checkEquals(
                600_000_000_000L,
                Math.multiplyExact(
                        VadDeterministicReplay.FRESH_PHYSICAL_CAP_FRAMES,
                        VadDeterministicReplay.FRAME_NANOS),
                "physical duration");
        checkEquals(
                2_000_000_000L,
                Math.multiplyExact(
                        VadDeterministicReplay.HARNESS_PRE_ROLL_FRAMES,
                        VadDeterministicReplay.FRAME_NANOS),
                "pre-roll duration");
        checkEquals(
                1_500_000_000L,
                Math.multiplyExact(
                        VadDeterministicReplay.HARNESS_OVERLAP_FRAMES,
                        VadDeterministicReplay.FRAME_NANOS),
                "overlap duration");

        Profile profile = profile();
        checkEquals(100, profile.preRollFrames(), "profile pre-roll");
        checkEquals(75, profile.overlapFrames(), "profile overlap");
        assertProfileRejected(99, 75, RejectCode.PROFILE_PRE_ROLL_MISMATCH);
        assertProfileRejected(101, 75, RejectCode.PROFILE_PRE_ROLL_MISMATCH);
        assertProfileRejected(Integer.MIN_VALUE, 75, RejectCode.PROFILE_PRE_ROLL_MISMATCH);
        assertProfileRejected(100, 74, RejectCode.PROFILE_OVERLAP_MISMATCH);
        assertProfileRejected(100, 76, RejectCode.PROFILE_OVERLAP_MISMATCH);
        assertProfileRejected(100, Integer.MAX_VALUE, RejectCode.PROFILE_OVERLAP_MISMATCH);

        boolean nullInitialRejected = false;
        try {
            VadDeterministicReplay.initial(null);
        } catch (IllegalArgumentException expected) {
            nullInitialRejected = true;
        }
        checkTrue(nullInitialRejected, "null initial profile");
    }

    private static void replayInputIsDefensivelyCopied() {
        Profile profile = profile();
        Frame[] caller = frames(0L, FrameClass.SILENCE, FrameClass.SPEECH);
        ReplayInput input = new ReplayInput(caller);
        caller[0] = null;
        Frame[] accessor = input.frames();
        accessor[1] = null;

        Accepted accepted = accepted(profile, VadDeterministicReplay.initial(profile), input);
        checkEquals(2L, accepted.after().nextFrameIndex(), "owned input applied");
        checkEquals(1, accepted.speechOnsets().size(), "owned onset retained");
        checkEquals(1L, accepted.speechOnsets().get(0).onsetFrameInclusive(), "owned onset index");

        ReplayInput nullArray = new ReplayInput(null);
        assertRejected(
                VadDeterministicReplay.replay(
                        profile, VadDeterministicReplay.initial(profile), nullArray),
                RejectCode.NULL_INPUT,
                VadDeterministicReplay.initial(profile),
                false);
    }

    private static void preRollMetadataIsExactAndBounded() {
        Profile profile = profile();

        Accepted atBeginning = replay(profile, labels(1, FrameClass.SPEECH));
        checkEquals(
                new SpeechOnset(0L, 0L, 0L, 0, true),
                atBeginning.speechOnsets().get(0),
                "beginning onset");

        FrameClass[] shortLabels = labels(26, FrameClass.SILENCE);
        shortLabels[25] = FrameClass.SPEECH;
        SpeechOnset shortOnset = replay(profile, shortLabels).speechOnsets().get(0);
        checkEquals(new SpeechOnset(25L, 0L, 25L, 25, true), shortOnset, "short pre-roll");

        FrameClass[] fullLabels = labels(151, FrameClass.SILENCE);
        fullLabels[150] = FrameClass.SPEECH;
        SpeechOnset fullOnset = replay(profile, fullLabels).speechOnsets().get(0);
        checkEquals(new SpeechOnset(150L, 50L, 150L, 100, true), fullOnset, "full pre-roll");

        FrameClass[] acrossCapLabels = labels(30_051, FrameClass.SILENCE);
        acrossCapLabels[30_050] = FrameClass.SPEECH;
        Accepted acrossCap = replay(profile, acrossCapLabels);
        checkEquals(1, acrossCap.physicalCaps().size(), "pre-roll cap count");
        checkEquals(
                new SpeechOnset(30_050L, 29_950L, 30_050L, 100, true),
                acrossCap.speechOnsets().get(0),
                "pre-roll crosses cap");
        checkTrue(
                acrossCap.speechOnsets().get(0).preRollStartFrameInclusive()
                        >= acrossCap
                                .physicalCaps()
                                .get(0)
                                .nextPartOverlapStartFrameInclusive(),
                "pre-roll is covered by selected next-part overlap");
    }

    private static void semanticBoundaryGoldensAreExact() {
        Profile profile = profile();

        Accepted at895 = speechThenSilence(profile, 4_475);
        checkEquals(0, at895.semanticBoundaries().size(), "89.5 boundary count");
        checkEquals(4_475L, at895.after().consecutiveSilenceFrames(), "89.5 counter");

        Accepted at900 = speechThenSilence(profile, 4_500);
        checkEquals(1, at900.semanticBoundaries().size(), "90.0 boundary count");
        checkEquals(
                new SemanticBoundary(0L, 1L, 4_501L, 90_000_000_000L, 90_020_000_000L),
                at900.semanticBoundaries().get(0),
                "90.0 boundary");
        checkEquals(SemanticPhase.AWAITING_SPEECH, at900.after().phase(), "90.0 phase");

        Accepted at905 = speechThenSilence(profile, 4_525);
        checkEquals(1, at905.semanticBoundaries().size(), "90.5 boundary count");
        checkEquals(SemanticPhase.AWAITING_SPEECH, at905.after().phase(), "90.5 phase");
        checkEquals(0L, at905.after().consecutiveSilenceFrames(), "post-boundary extra silence");

        Accepted initialSilence = replay(profile, labels(4_525, FrameClass.SILENCE));
        checkEquals(0, initialSilence.semanticBoundaries().size(), "initial silence has no group");
        checkEquals(0L, initialSilence.after().semanticGroupsOpened(), "initial silence groups");
    }

    private static void resumeAtEightyNinePointNineCancelsBoundary() {
        Profile profile = profile();
        Snapshot state = VadDeterministicReplay.initial(profile);
        state = accepted(profile, state, input(state, FrameClass.SPEECH)).after();
        state = accepted(profile, state, repeatedInput(state, 4_495, FrameClass.SILENCE)).after();
        Accepted resumed = accepted(profile, state, input(state, FrameClass.SPEECH));

        checkEquals(4_497L, resumed.after().nextFrameIndex(), "89.9 resume timeline");
        checkEquals(0, resumed.semanticBoundaries().size(), "89.9 boundary cancelled");
        checkEquals(SemanticPhase.ACTIVE_SPEECH, resumed.after().phase(), "89.9 phase");
        checkEquals(0L, resumed.after().consecutiveSilenceFrames(), "89.9 counter reset");
        checkEquals(
                new SpeechOnset(4_496L, 4_396L, 4_496L, 100, false),
                resumed.speechOnsets().get(0),
                "89.9 resume pre-roll");

        Accepted finalSilence =
                accepted(
                        profile,
                        resumed.after(),
                        repeatedInput(resumed.after(), 4_500, FrameClass.SILENCE));
        checkEquals(1, finalSilence.semanticBoundaries().size(), "fresh 90 seconds required");
        checkEquals(4_497L, finalSilence.semanticBoundaries().get(0).silenceStartFrameInclusive(), "fresh timer start");
    }

    private static void physicalCapsAndOverlapLinksAreExact() {
        Profile profile = profile();
        Accepted longReplay = replay(profile, labels(60_025, FrameClass.SPEECH));
        checkEquals(2, longReplay.physicalCaps().size(), ">10 minute cap count");
        checkEquals(
                new PhysicalCap(0L, 0L, 30_000L, 1L, 29_925L, 30_000L, 0L),
                longReplay.physicalCaps().get(0),
                "first cap");
        checkEquals(
                new PhysicalCap(1L, 30_000L, 60_000L, 2L, 59_925L, 60_000L, 1L),
                longReplay.physicalCaps().get(1),
                "second cap");
        for (PhysicalCap cap : longReplay.physicalCaps()) {
            checkEquals(
                    30_000L,
                    cap.freshEndFrameExclusive() - cap.freshStartFrameInclusive(),
                    "fresh cap span");
            checkEquals(
                    75L,
                    cap.nextPartOverlapEndFrameExclusive()
                            - cap.nextPartOverlapStartFrameInclusive(),
                    "overlap span");
            checkEquals(
                    cap.closedPartOrdinal(),
                    cap.overlapOwnerPartOrdinal(),
                    "dedup ownership remains with closing fresh part");
        }
    }

    private static void semanticBoundaryAndPhysicalCapCanCoincide() {
        Profile profile = profile();
        FrameClass[] labels = labels(30_000, FrameClass.SPEECH);
        for (int index = 25_500; index < labels.length; index++) {
            labels[index] = FrameClass.SILENCE;
        }
        Accepted accepted = replay(profile, labels);
        checkEquals(1, accepted.semanticBoundaries().size(), "simultaneous semantic count");
        checkEquals(1, accepted.physicalCaps().size(), "simultaneous physical count");
        checkEquals(30_000L, accepted.semanticBoundaries().get(0).boundaryAfterFrameExclusive(), "simultaneous semantic frame");
        checkEquals(30_000L, accepted.physicalCaps().get(0).freshEndFrameExclusive(), "simultaneous cap frame");
    }

    private static void invalidInputsAndSnapshotsFailClosed() {
        Profile profile = profile();
        Snapshot initial = VadDeterministicReplay.initial(profile);

        assertRejected(
                VadDeterministicReplay.replay(null, initial, input(initial, FrameClass.SPEECH)),
                RejectCode.NULL_PROFILE,
                initial,
                true);
        assertRejected(
                VadDeterministicReplay.replay(profile, null, input(initial, FrameClass.SPEECH)),
                RejectCode.NULL_SNAPSHOT,
                null,
                true);
        assertRejected(
                VadDeterministicReplay.replay(profile, initial, null),
                RejectCode.NULL_INPUT,
                initial,
                true);
        assertRejected(
                VadDeterministicReplay.replay(
                        profile, initial, new ReplayInput(new Frame[] {null})),
                RejectCode.NULL_FRAME,
                initial,
                true);
        assertRejected(
                VadDeterministicReplay.replay(
                        profile,
                        initial,
                        new ReplayInput(new Frame[] {new Frame(0L, null)})),
                RejectCode.NULL_FRAME_CLASS,
                initial,
                true);
        assertRejected(
                VadDeterministicReplay.replay(
                        profile,
                        initial,
                        new ReplayInput(new Frame[] {new Frame(1L, FrameClass.SPEECH)})),
                RejectCode.INPUT_ORDER_MISMATCH,
                initial,
                true);
        assertRejected(
                VadDeterministicReplay.replay(
                        profile,
                        initial,
                        new ReplayInput(
                                new Frame[] {
                                    new Frame(0L, FrameClass.SPEECH),
                                    new Frame(2L, FrameClass.SILENCE)
                                })),
                RejectCode.INPUT_ORDER_MISMATCH,
                initial,
                true);

        Snapshot wrongSchema = snapshot(initial, 0, initial.nextFrameIndex(), initial.phase(), 0L, 0L, 0L, 0);
        assertRestoreRejected(profile, wrongSchema, RejectCode.SCHEMA_MISMATCH);
        Snapshot wrongProfile =
                new Snapshot(1, 99, 75, 0L, SemanticPhase.AWAITING_SPEECH, 0L, 0L, 0L, 0);
        assertRestoreRejected(profile, wrongProfile, RejectCode.SNAPSHOT_PROFILE_MISMATCH);
        Snapshot negative = snapshot(initial, 1, -1L, SemanticPhase.AWAITING_SPEECH, 0L, 0L, 0L, 0);
        assertRestoreRejected(profile, negative, RejectCode.NEXT_FRAME_OUT_OF_RANGE);
        Snapshot missingPhase = snapshot(initial, 1, 0L, null, 0L, 0L, 0L, 0);
        assertRestoreRejected(profile, missingPhase, RejectCode.PHASE_MISSING);
        Snapshot badSilence = snapshot(initial, 1, 1L, SemanticPhase.ACTIVE_SILENCE, 0L, 1L, 0L, 1);
        assertRestoreRejected(profile, badSilence, RejectCode.SILENCE_INVARIANT);
        Snapshot badGroups = snapshot(initial, 1, 10L, SemanticPhase.AWAITING_SPEECH, 0L, 1L, 0L, 10);
        assertRestoreRejected(profile, badGroups, RejectCode.GROUP_COUNTER_INVARIANT);
        Snapshot badBuffer = snapshot(initial, 1, 10L, SemanticPhase.AWAITING_SPEECH, 0L, 0L, 0L, 9);
        assertRestoreRejected(profile, badBuffer, RejectCode.PRE_ROLL_BUFFER_INVARIANT);

        Snapshot terminal =
                snapshot(
                        initial,
                        1,
                        VadDeterministicReplay.MAX_SAFE_FRAME_COUNT,
                        SemanticPhase.AWAITING_SPEECH,
                        0L,
                        0L,
                        0L,
                        100);
        Restored restored = requireType(VadDeterministicReplay.restore(profile, terminal), Restored.class);
        assertRejected(
                VadDeterministicReplay.replay(
                        profile,
                        restored.snapshot(),
                        new ReplayInput(
                                new Frame[] {
                                    new Frame(
                                            VadDeterministicReplay.MAX_SAFE_FRAME_COUNT,
                                            FrameClass.SILENCE)
                                })),
                RejectCode.INPUT_RANGE_EXHAUSTED,
                restored.snapshot(),
                true);

        Accepted control = accepted(profile, initial, input(initial, FrameClass.SPEECH));
        Rejected rejected =
                requireType(
                        VadDeterministicReplay.replay(
                                profile,
                                initial,
                                new ReplayInput(new Frame[] {new Frame(4L, FrameClass.SPEECH)})),
                        Rejected.class);
        checkTrue(rejected.unchanged() == initial, "rejection preserves exact reference");
        checkEquals(
                control,
                accepted(profile, initial, input(initial, FrameClass.SPEECH)),
                "rejection leaves future replay unchanged");
    }

    private static void restoreAndChunkingMatchUninterruptedReplay() {
        Profile profile = profile();
        FrameClass[] labels = deterministicLabels();
        Accepted uninterrupted = replay(profile, labels);

        FrameClass[] prefixLabels = new FrameClass[31_337];
        FrameClass[] tailLabels = new FrameClass[labels.length - prefixLabels.length];
        System.arraycopy(labels, 0, prefixLabels, 0, prefixLabels.length);
        System.arraycopy(labels, prefixLabels.length, tailLabels, 0, tailLabels.length);

        Accepted prefix = replay(profile, prefixLabels);
        Restored restored =
                requireType(VadDeterministicReplay.restore(profile, prefix.after()), Restored.class);
        checkTrue(restored.snapshot() != prefix.after(), "restore copies snapshot");
        Accepted tail =
                accepted(profile, restored.snapshot(), input(restored.snapshot(), tailLabels));

        checkEquals(uninterrupted.after(), tail.after(), "chunked terminal state");
        checkEquals(
                uninterrupted.speechOnsets().subList(
                        uninterrupted.speechOnsets().size() - tail.speechOnsets().size(),
                        uninterrupted.speechOnsets().size()),
                tail.speechOnsets(),
                "chunked tail onsets");
        checkEquals(
                uninterrupted.semanticBoundaries().subList(
                        uninterrupted.semanticBoundaries().size()
                                - tail.semanticBoundaries().size(),
                        uninterrupted.semanticBoundaries().size()),
                tail.semanticBoundaries(),
                "chunked tail boundaries");
        checkEquals(
                uninterrupted.physicalCaps().subList(
                        uninterrupted.physicalCaps().size() - tail.physicalCaps().size(),
                        uninterrupted.physicalCaps().size()),
                tail.physicalCaps(),
                "chunked tail caps");
    }

    private static void deterministicKnownAnswerIsStable() {
        Profile profile = profile();
        Accepted first = replay(profile, deterministicLabels());
        String canonical = VadDeterministicReplay.canonicalSummary(first);

        checkEquals(
                new Snapshot(
                        1,
                        100,
                        75,
                        60_025L,
                        SemanticPhase.ACTIVE_SILENCE,
                        3_525L,
                        8L,
                        7L,
                        100),
                first.after(),
                "known terminal state");
        checkEquals(8, first.speechOnsets().size(), "known onset count");
        checkEquals(7, first.semanticBoundaries().size(), "known boundary count");
        checkEquals(2, first.physicalCaps().size(), "known cap count");
        checkEquals(
                new SpeechOnset(0L, 0L, 0L, 0, true),
                first.speechOnsets().get(0),
                "known first onset");
        checkEquals(
                new SpeechOnset(56_000L, 55_900L, 56_000L, 100, true),
                first.speechOnsets().get(7),
                "known last onset");
        checkEquals(
                new SemanticBoundary(0L, 500L, 5_000L, 90_000_000_000L, 100_000_000_000L),
                first.semanticBoundaries().get(0),
                "known first boundary");
        checkEquals(
                new SemanticBoundary(6L, 48_500L, 53_000L, 90_000_000_000L, 1_060_000_000_000L),
                first.semanticBoundaries().get(6),
                "known last boundary");
        checkEquals(
                "e83e102990aa22efc138705bd9ed4ad8457cbe44097c67780fee47a6e91fc6e8",
                sha256(canonical),
                "canonical known-answer digest");

        for (int repetition = 0; repetition < 100; repetition++) {
            Accepted repeated = replay(profile, deterministicLabels());
            checkEquals(first, repeated, "deterministic result " + repetition);
            checkEquals(canonical, VadDeterministicReplay.canonicalSummary(repeated), "canonical replay " + repetition);
        }
    }

    private static void noMutableStaticStateExists() {
        for (Field field : VadDeterministicReplay.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                checkTrue(Modifier.isFinal(field.getModifiers()), "outer static field final");
            }
        }
        for (Field field : ReplayInput.class.getDeclaredFields()) {
            checkTrue(Modifier.isFinal(field.getModifiers()), "input field final");
        }
        checkEquals(0, VadDeterministicReplay.class.getConstructors().length, "no public constructor");
    }

    private static Accepted speechThenSilence(Profile profile, int silenceFrames) {
        FrameClass[] labels = labels(silenceFrames + 1, FrameClass.SILENCE);
        labels[0] = FrameClass.SPEECH;
        return replay(profile, labels);
    }

    private static FrameClass[] deterministicLabels() {
        FrameClass[] labels = new FrameClass[60_025];
        for (int index = 0; index < labels.length; index++) {
            labels[index] = index % 8_000 < 500 ? FrameClass.SPEECH : FrameClass.SILENCE;
        }
        return labels;
    }

    private static Accepted replay(Profile profile, FrameClass[] labels) {
        Snapshot initial = VadDeterministicReplay.initial(profile);
        return accepted(profile, initial, input(initial, labels));
    }

    private static ReplayInput repeatedInput(
            Snapshot start, int count, FrameClass frameClass) {
        return input(start, labels(count, frameClass));
    }

    private static ReplayInput input(Snapshot start, FrameClass... labels) {
        Frame[] frames = new Frame[labels.length];
        for (int offset = 0; offset < labels.length; offset++) {
            frames[offset] = new Frame(start.nextFrameIndex() + offset, labels[offset]);
        }
        return new ReplayInput(frames);
    }

    private static Frame[] frames(long start, FrameClass... labels) {
        Frame[] frames = new Frame[labels.length];
        for (int offset = 0; offset < labels.length; offset++) {
            frames[offset] = new Frame(start + offset, labels[offset]);
        }
        return frames;
    }

    private static FrameClass[] labels(int count, FrameClass frameClass) {
        FrameClass[] labels = new FrameClass[count];
        for (int index = 0; index < count; index++) {
            labels[index] = frameClass;
        }
        return labels;
    }

    private static Accepted accepted(Profile profile, Snapshot state, ReplayInput input) {
        return requireType(VadDeterministicReplay.replay(profile, state, input), Accepted.class);
    }

    private static Profile profile() {
        return requireType(
                        VadDeterministicReplay.createProfile(100, 75), ProfileAccepted.class)
                .profile();
    }

    private static Snapshot snapshot(
            Snapshot template,
            int schema,
            long next,
            SemanticPhase phase,
            long silence,
            long groups,
            long boundaries,
            int buffered) {
        return new Snapshot(
                schema,
                template.profilePreRollFrames(),
                template.profileOverlapFrames(),
                next,
                phase,
                silence,
                groups,
                boundaries,
                buffered);
    }

    private static void assertProfileRejected(
            int preRoll, int overlap, RejectCode expected) {
        ProfileRejected rejected =
                requireType(
                        VadDeterministicReplay.createProfile(preRoll, overlap),
                        ProfileRejected.class);
        checkEquals(expected, rejected.code(), "profile rejection");
    }

    private static void assertRestoreRejected(
            Profile profile, Snapshot snapshot, RejectCode expected) {
        RestoreRejected rejected =
                requireType(
                        VadDeterministicReplay.restore(profile, snapshot), RestoreRejected.class);
        checkEquals(expected, rejected.code(), "restore rejection");
    }

    private static void assertRejected(
            Object result,
            RejectCode expected,
            Snapshot expectedReference,
            boolean requireSameReference) {
        Rejected rejected = requireType(result, Rejected.class);
        checkEquals(expected, rejected.code(), "replay rejection");
        if (requireSameReference) {
            checkTrue(rejected.unchanged() == expectedReference, "rejection state reference");
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
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
}
