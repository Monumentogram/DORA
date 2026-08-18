package com.monumentogram.dora.stage0.vad.p3;

import java.util.ArrayList;
import java.util.List;

/**
 * Dependency-free Stage 0 replay of post-acoustic VAD labels and physical-part metadata.
 *
 * <p>This harness consumes only synthetic {@link FrameClass} labels. It does not read audio,
 * select an acoustic detector, write files, use a wall clock, or establish device, real-time,
 * storage, byte-overlap, or production evidence.
 */
public final class VadDeterministicReplay {
    public static final int SNAPSHOT_SCHEMA_VERSION = 1;
    public static final int SAMPLE_RATE_HZ = 16_000;
    public static final int SAMPLES_PER_FRAME = 320;
    public static final long FRAME_NANOS = 20_000_000L;
    public static final long SEMANTIC_BOUNDARY_FRAMES = 4_500L;
    public static final long FRESH_PHYSICAL_CAP_FRAMES = 30_000L;
    public static final int HARNESS_PRE_ROLL_FRAMES = 100;
    public static final int HARNESS_OVERLAP_FRAMES = 75;
    public static final long MAX_SAFE_FRAME_COUNT = Long.MAX_VALUE / FRAME_NANOS;

    private VadDeterministicReplay() {}

    public enum FrameClass {
        SPEECH,
        SILENCE
    }

    public enum SemanticPhase {
        AWAITING_SPEECH,
        ACTIVE_SPEECH,
        ACTIVE_SILENCE
    }

    public enum RejectCode {
        NULL_PROFILE,
        PROFILE_PRE_ROLL_MISMATCH,
        PROFILE_OVERLAP_MISMATCH,
        NULL_SNAPSHOT,
        NULL_INPUT,
        NULL_FRAME,
        NULL_FRAME_CLASS,
        SCHEMA_MISMATCH,
        SNAPSHOT_PROFILE_MISMATCH,
        NEXT_FRAME_OUT_OF_RANGE,
        PHASE_MISSING,
        SILENCE_INVARIANT,
        GROUP_COUNTER_INVARIANT,
        PRE_ROLL_BUFFER_INVARIANT,
        INPUT_ORDER_MISMATCH,
        INPUT_RANGE_EXHAUSTED,
        ARITHMETIC_OVERFLOW
    }

    /**
     * Exact bounded harness profile. These values are evidence fixtures, not a production profile
     * admission.
     */
    public record Profile(int preRollFrames, int overlapFrames) {}

    public record Frame(long frameIndex, FrameClass frameClass) {}

    /** Immutable input whose constructor and accessor both defensively copy the frame array. */
    public static final class ReplayInput {
        private final Frame[] frames;

        public ReplayInput(Frame[] frames) {
            this.frames = frames == null ? null : frames.clone();
        }

        public Frame[] frames() {
            return frames == null ? null : frames.clone();
        }

        private Frame[] ownedCopy() {
            return frames == null ? null : frames.clone();
        }
    }

    /** Immutable restart point; malformed public candidates are rejected before any replay. */
    public record Snapshot(
            int schemaVersion,
            int profilePreRollFrames,
            int profileOverlapFrames,
            long nextFrameIndex,
            SemanticPhase phase,
            long consecutiveSilenceFrames,
            long semanticGroupsOpened,
            long semanticBoundariesEmitted,
            int preRollFramesAvailable) {}

    public record SpeechOnset(
            long onsetFrameInclusive,
            long preRollStartFrameInclusive,
            long preRollEndFrameExclusive,
            int preRollFramesIncluded,
            boolean openedSemanticGroup) {}

    public record SemanticBoundary(
            long groupOrdinal,
            long silenceStartFrameInclusive,
            long boundaryAfterFrameExclusive,
            long silenceDurationNanos,
            long boundaryTimelineNanos) {}

    /**
     * Metadata for a fresh 600-second span and the exact prior-frame range copied into the next
     * part. It deliberately makes no encoded-file or byte-integrity claim.
     */
    public record PhysicalCap(
            long closedPartOrdinal,
            long freshStartFrameInclusive,
            long freshEndFrameExclusive,
            long nextPartOrdinal,
            long nextPartOverlapStartFrameInclusive,
            long nextPartOverlapEndFrameExclusive,
            long overlapOwnerPartOrdinal) {}

    public sealed interface ProfileResult permits ProfileAccepted, ProfileRejected {}

    public record ProfileAccepted(Profile profile) implements ProfileResult {
        public ProfileAccepted {
            if (profile == null) {
                throw new IllegalArgumentException("profile");
            }
        }
    }

    public record ProfileRejected(RejectCode code) implements ProfileResult {
        public ProfileRejected {
            if (code == null) {
                throw new IllegalArgumentException("code");
            }
        }
    }

    public sealed interface RestoreResult permits Restored, RestoreRejected {}

    public record Restored(Snapshot snapshot) implements RestoreResult {
        public Restored {
            if (snapshot == null) {
                throw new IllegalArgumentException("snapshot");
            }
        }
    }

    public record RestoreRejected(RejectCode code) implements RestoreResult {
        public RestoreRejected {
            if (code == null) {
                throw new IllegalArgumentException("code");
            }
        }
    }

    public sealed interface ReplayResult permits Accepted, Rejected {}

    public record Accepted(
            Snapshot after,
            List<SpeechOnset> speechOnsets,
            List<SemanticBoundary> semanticBoundaries,
            List<PhysicalCap> physicalCaps)
            implements ReplayResult {
        public Accepted {
            if (after == null
                    || speechOnsets == null
                    || semanticBoundaries == null
                    || physicalCaps == null) {
                throw new IllegalArgumentException("accepted result");
            }
            speechOnsets = List.copyOf(speechOnsets);
            semanticBoundaries = List.copyOf(semanticBoundaries);
            physicalCaps = List.copyOf(physicalCaps);
        }
    }

    public record Rejected(RejectCode code, Snapshot unchanged) implements ReplayResult {
        public Rejected {
            if (code == null) {
                throw new IllegalArgumentException("code");
            }
        }
    }

    public static ProfileResult createProfile(int preRollFrames, int overlapFrames) {
        if (preRollFrames != HARNESS_PRE_ROLL_FRAMES) {
            return new ProfileRejected(RejectCode.PROFILE_PRE_ROLL_MISMATCH);
        }
        if (overlapFrames != HARNESS_OVERLAP_FRAMES) {
            return new ProfileRejected(RejectCode.PROFILE_OVERLAP_MISMATCH);
        }
        return new ProfileAccepted(new Profile(preRollFrames, overlapFrames));
    }

    public static Snapshot initial(Profile profile) {
        requireExactProfile(profile);
        return new Snapshot(
                SNAPSHOT_SCHEMA_VERSION,
                profile.preRollFrames(),
                profile.overlapFrames(),
                0L,
                SemanticPhase.AWAITING_SPEECH,
                0L,
                0L,
                0L,
                0);
    }

    public static RestoreResult restore(Profile profile, Snapshot candidate) {
        RejectCode rejection = validateSnapshot(profile, candidate);
        if (rejection != null) {
            return new RestoreRejected(rejection);
        }
        return new Restored(copyOf(candidate));
    }

    public static ReplayResult replay(Profile profile, Snapshot before, ReplayInput input) {
        if (profile == null) {
            return new Rejected(RejectCode.NULL_PROFILE, before);
        }
        if (before == null) {
            return new Rejected(RejectCode.NULL_SNAPSHOT, null);
        }
        if (input == null) {
            return new Rejected(RejectCode.NULL_INPUT, before);
        }

        RejectCode invalidSnapshot = validateSnapshot(profile, before);
        if (invalidSnapshot != null) {
            return new Rejected(invalidSnapshot, before);
        }

        Frame[] frames = input.ownedCopy();
        if (frames == null) {
            return new Rejected(RejectCode.NULL_INPUT, before);
        }
        RejectCode invalidInput = validateInput(before.nextFrameIndex(), frames);
        if (invalidInput != null) {
            return new Rejected(invalidInput, before);
        }

        try {
            long nextFrame = before.nextFrameIndex();
            SemanticPhase phase = before.phase();
            long silence = before.consecutiveSilenceFrames();
            long groups = before.semanticGroupsOpened();
            long boundaries = before.semanticBoundariesEmitted();
            int preRollAvailable = before.preRollFramesAvailable();
            List<SpeechOnset> onsets = new ArrayList<>();
            List<SemanticBoundary> semanticEvents = new ArrayList<>();
            List<PhysicalCap> physicalEvents = new ArrayList<>();

            for (Frame frame : frames) {
                long frameIndex = frame.frameIndex();
                long afterExclusive = Math.addExact(frameIndex, 1L);
                FrameClass frameClass = frame.frameClass();

                if (frameClass == FrameClass.SPEECH && phase != SemanticPhase.ACTIVE_SPEECH) {
                    int included = Math.min(profile.preRollFrames(), preRollAvailable);
                    long preRollStart = Math.subtractExact(frameIndex, included);
                    boolean opensGroup = phase == SemanticPhase.AWAITING_SPEECH;
                    onsets.add(
                            new SpeechOnset(
                                    frameIndex, preRollStart, frameIndex, included, opensGroup));
                }

                switch (phase) {
                    case AWAITING_SPEECH -> {
                        if (frameClass == FrameClass.SPEECH) {
                            phase = SemanticPhase.ACTIVE_SPEECH;
                            groups = Math.addExact(groups, 1L);
                        }
                    }
                    case ACTIVE_SPEECH -> {
                        if (frameClass == FrameClass.SILENCE) {
                            phase = SemanticPhase.ACTIVE_SILENCE;
                            silence = 1L;
                        }
                    }
                    case ACTIVE_SILENCE -> {
                        if (frameClass == FrameClass.SPEECH) {
                            phase = SemanticPhase.ACTIVE_SPEECH;
                            silence = 0L;
                        } else if (silence == SEMANTIC_BOUNDARY_FRAMES - 1L) {
                            long silenceStart =
                                    Math.subtractExact(afterExclusive, SEMANTIC_BOUNDARY_FRAMES);
                            semanticEvents.add(
                                    new SemanticBoundary(
                                            boundaries,
                                            silenceStart,
                                            afterExclusive,
                                            Math.multiplyExact(
                                                    SEMANTIC_BOUNDARY_FRAMES, FRAME_NANOS),
                                            Math.multiplyExact(afterExclusive, FRAME_NANOS)));
                            boundaries = Math.addExact(boundaries, 1L);
                            phase = SemanticPhase.AWAITING_SPEECH;
                            silence = 0L;
                        } else {
                            silence = Math.addExact(silence, 1L);
                        }
                    }
                }

                if (afterExclusive % FRESH_PHYSICAL_CAP_FRAMES == 0L) {
                    long closedPart =
                            Math.subtractExact(
                                    afterExclusive / FRESH_PHYSICAL_CAP_FRAMES, 1L);
                    long freshStart =
                            Math.subtractExact(afterExclusive, FRESH_PHYSICAL_CAP_FRAMES);
                    long overlapStart =
                            Math.subtractExact(afterExclusive, profile.overlapFrames());
                    physicalEvents.add(
                            new PhysicalCap(
                                    closedPart,
                                    freshStart,
                                    afterExclusive,
                                    Math.addExact(closedPart, 1L),
                                    overlapStart,
                                    afterExclusive,
                                    closedPart));
                }

                nextFrame = afterExclusive;
                preRollAvailable = Math.min(profile.preRollFrames(), preRollAvailable + 1);
            }

            Snapshot after =
                    new Snapshot(
                            SNAPSHOT_SCHEMA_VERSION,
                            profile.preRollFrames(),
                            profile.overlapFrames(),
                            nextFrame,
                            phase,
                            silence,
                            groups,
                            boundaries,
                            preRollAvailable);
            return new Accepted(after, onsets, semanticEvents, physicalEvents);
        } catch (ArithmeticException ignored) {
            return new Rejected(RejectCode.ARITHMETIC_OVERFLOW, before);
        }
    }

    /** Stable content-free serialization used only for deterministic known-answer evidence. */
    public static String canonicalSummary(Accepted accepted) {
        if (accepted == null) {
            throw new IllegalArgumentException("accepted");
        }
        StringBuilder summary = new StringBuilder(256);
        Snapshot state = accepted.after();
        summary.append("v1|")
                .append(state.nextFrameIndex())
                .append('|')
                .append(state.phase())
                .append('|')
                .append(state.consecutiveSilenceFrames())
                .append('|')
                .append(state.semanticGroupsOpened())
                .append('|')
                .append(state.semanticBoundariesEmitted())
                .append('|')
                .append(state.preRollFramesAvailable());
        for (SpeechOnset onset : accepted.speechOnsets()) {
            summary.append("|O:")
                    .append(onset.onsetFrameInclusive())
                    .append(',')
                    .append(onset.preRollStartFrameInclusive())
                    .append(',')
                    .append(onset.preRollFramesIncluded())
                    .append(',')
                    .append(onset.openedSemanticGroup());
        }
        for (SemanticBoundary boundary : accepted.semanticBoundaries()) {
            summary.append("|S:")
                    .append(boundary.groupOrdinal())
                    .append(',')
                    .append(boundary.silenceStartFrameInclusive())
                    .append(',')
                    .append(boundary.boundaryAfterFrameExclusive());
        }
        for (PhysicalCap cap : accepted.physicalCaps()) {
            summary.append("|P:")
                    .append(cap.closedPartOrdinal())
                    .append(',')
                    .append(cap.freshStartFrameInclusive())
                    .append(',')
                    .append(cap.freshEndFrameExclusive())
                    .append(',')
                    .append(cap.nextPartOverlapStartFrameInclusive())
                    .append(',')
                    .append(cap.nextPartOverlapEndFrameExclusive());
        }
        return summary.toString();
    }

    private static RejectCode validateInput(long expectedStart, Frame[] frames) {
        if (frames.length > MAX_SAFE_FRAME_COUNT - expectedStart) {
            return RejectCode.INPUT_RANGE_EXHAUSTED;
        }
        long expected = expectedStart;
        for (Frame frame : frames) {
            if (frame == null) {
                return RejectCode.NULL_FRAME;
            }
            if (frame.frameClass() == null) {
                return RejectCode.NULL_FRAME_CLASS;
            }
            if (frame.frameIndex() != expected) {
                return RejectCode.INPUT_ORDER_MISMATCH;
            }
            expected++;
        }
        return null;
    }

    private static RejectCode validateSnapshot(Profile profile, Snapshot snapshot) {
        if (profile == null) {
            return RejectCode.NULL_PROFILE;
        }
        if (profile.preRollFrames() != HARNESS_PRE_ROLL_FRAMES) {
            return RejectCode.PROFILE_PRE_ROLL_MISMATCH;
        }
        if (profile.overlapFrames() != HARNESS_OVERLAP_FRAMES) {
            return RejectCode.PROFILE_OVERLAP_MISMATCH;
        }
        if (snapshot == null) {
            return RejectCode.NULL_SNAPSHOT;
        }
        if (snapshot.schemaVersion() != SNAPSHOT_SCHEMA_VERSION) {
            return RejectCode.SCHEMA_MISMATCH;
        }
        if (snapshot.profilePreRollFrames() != profile.preRollFrames()
                || snapshot.profileOverlapFrames() != profile.overlapFrames()) {
            return RejectCode.SNAPSHOT_PROFILE_MISMATCH;
        }
        if (snapshot.nextFrameIndex() < 0L
                || snapshot.nextFrameIndex() > MAX_SAFE_FRAME_COUNT) {
            return RejectCode.NEXT_FRAME_OUT_OF_RANGE;
        }
        if (snapshot.phase() == null) {
            return RejectCode.PHASE_MISSING;
        }
        long silence = snapshot.consecutiveSilenceFrames();
        if (snapshot.phase() == SemanticPhase.ACTIVE_SILENCE) {
            if (silence < 1L || silence >= SEMANTIC_BOUNDARY_FRAMES) {
                return RejectCode.SILENCE_INVARIANT;
            }
        } else if (silence != 0L) {
            return RejectCode.SILENCE_INVARIANT;
        }
        long groups = snapshot.semanticGroupsOpened();
        long boundaries = snapshot.semanticBoundariesEmitted();
        if (groups < 0L || boundaries < 0L || boundaries > groups) {
            return RejectCode.GROUP_COUNTER_INVARIANT;
        }
        long openGroups = groups - boundaries;
        if ((snapshot.phase() == SemanticPhase.AWAITING_SPEECH && openGroups != 0L)
                || (snapshot.phase() != SemanticPhase.AWAITING_SPEECH && openGroups != 1L)) {
            return RejectCode.GROUP_COUNTER_INVARIANT;
        }
        if (groups > snapshot.nextFrameIndex()) {
            return RejectCode.GROUP_COUNTER_INVARIANT;
        }
        int expectedBuffered =
                (int) Math.min((long) profile.preRollFrames(), snapshot.nextFrameIndex());
        if (snapshot.preRollFramesAvailable() != expectedBuffered) {
            return RejectCode.PRE_ROLL_BUFFER_INVARIANT;
        }
        try {
            long minimum = Math.addExact(groups, Math.multiplyExact(boundaries, SEMANTIC_BOUNDARY_FRAMES));
            minimum = Math.addExact(minimum, silence);
            if (minimum > snapshot.nextFrameIndex()) {
                return RejectCode.GROUP_COUNTER_INVARIANT;
            }
        } catch (ArithmeticException ignored) {
            return RejectCode.GROUP_COUNTER_INVARIANT;
        }
        return null;
    }

    private static Snapshot copyOf(Snapshot snapshot) {
        return new Snapshot(
                snapshot.schemaVersion(),
                snapshot.profilePreRollFrames(),
                snapshot.profileOverlapFrames(),
                snapshot.nextFrameIndex(),
                snapshot.phase(),
                snapshot.consecutiveSilenceFrames(),
                snapshot.semanticGroupsOpened(),
                snapshot.semanticBoundariesEmitted(),
                snapshot.preRollFramesAvailable());
    }

    private static void requireExactProfile(Profile profile) {
        if (profile == null
                || profile.preRollFrames() != HARNESS_PRE_ROLL_FRAMES
                || profile.overlapFrames() != HARNESS_OVERLAP_FRAMES) {
            throw new IllegalArgumentException("exact harness profile required");
        }
    }
}
