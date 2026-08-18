package com.monumentogram.dora.stage0.vad.p2;

import java.util.Optional;

/**
 * Pure Stage 0 oracle for the frame-level timing rules that follow a VAD decision.
 *
 * <p>The input is a post-acoustic decision already classified as speech or silence by an upstream
 * detector after that detector's separately selected onset, short-pause hysteresis, and pre-roll
 * policy. A {@link FrameClass#SILENCE} input starts or continues this oracle's fixed continuous-
 * silence timer immediately; it does not select zero hysteresis. This class does not inspect audio,
 * choose or validate the acoustic profile, or claim acoustic-VAD, real-time, device, storage, or
 * production evidence.
 */
public final class VadBoundaryOracle {
    public static final int SNAPSHOT_SCHEMA_VERSION = 1;
    public static final int SAMPLE_RATE_HZ = 16_000;
    public static final int SAMPLES_PER_FRAME = 320;
    public static final long FRAME_NANOS = 20_000_000L;
    public static final long SILENCE_BOUNDARY_FRAMES = 4_500L;
    public static final long PHYSICAL_CAP_FRAMES = 30_000L;
    public static final int MIN_OVERLAP_FRAMES = 75;
    public static final int MAX_OVERLAP_FRAMES = 100;
    public static final long MAX_SAFE_FRAME_COUNT = Long.MAX_VALUE / FRAME_NANOS;

    private VadBoundaryOracle() {}

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
        OVERLAP_OUT_OF_RANGE,
        NULL_STATE,
        NULL_FRAME_CLASS,
        SCHEMA_MISMATCH,
        PROFILE_MISMATCH,
        NEXT_FRAME_OUT_OF_RANGE,
        PHASE_MISSING,
        SILENCE_INVARIANT,
        GROUP_COUNTER_INVARIANT,
        FRAME_INDEX_NEGATIVE,
        FRAME_INDEX_MISMATCH,
        TIME_RANGE_EXHAUSTED,
        ARITHMETIC_OVERFLOW
    }

    public static final class Profile {
        private final int overlapFrames;

        private Profile(int overlapFrames) {
            this.overlapFrames = overlapFrames;
        }

        public int overlapFrames() {
            return overlapFrames;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Profile profile && overlapFrames == profile.overlapFrames;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(overlapFrames);
        }

        @Override
        public String toString() {
            return "Profile[overlapFrames=" + overlapFrames + "]";
        }
    }

    /**
     * Immutable snapshot. Its public constructor deliberately permits malformed candidates so that
     * {@link #restore(Profile, State)} and {@link #step(Profile, State, long, FrameClass)} can reject
     * them without partial mutation.
     */
    public record State(
            int schemaVersion,
            int profileOverlapFrames,
            long nextFrameIndex,
            SemanticPhase phase,
            long consecutiveSilenceFrames,
            long semanticGroupsOpened,
            long semanticBoundariesEmitted) {}

    public record SemanticBoundary(
            long groupOrdinal,
            long silenceStartFrameInclusive,
            long boundaryAfterFrameExclusive,
            long silenceDurationNanos,
            long boundaryTimelineNanos) {}

    /**
     * A fresh, non-overlapped 600-second span plus the source range to copy into the next part.
     * This is not a claim about encoded file duration or byte-level overlap integrity.
     */
    public record PhysicalCap(
            long partOrdinal,
            long freshStartFrameInclusive,
            long freshEndFrameExclusive,
            int overlapFramesForNextPart,
            long overlapSourceStartFrameInclusive,
            long overlapSourceEndFrameExclusive) {}

    public sealed interface CreateResult permits Created, CreateRejected {}

    public record Created(Profile profile) implements CreateResult {
        public Created {
            if (profile == null) {
                throw new IllegalArgumentException("profile");
            }
        }
    }

    public record CreateRejected(RejectCode code) implements CreateResult {
        public CreateRejected {
            if (code == null) {
                throw new IllegalArgumentException("code");
            }
        }
    }

    public sealed interface RestoreResult permits Restored, RestoreRejected {}

    public record Restored(State state) implements RestoreResult {
        public Restored {
            if (state == null) {
                throw new IllegalArgumentException("state");
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

    public sealed interface StepResult permits Applied, Rejected {}

    public record Applied(
            State after,
            Optional<SemanticBoundary> semanticBoundary,
            Optional<PhysicalCap> physicalCap)
            implements StepResult {
        public Applied {
            if (after == null || semanticBoundary == null || physicalCap == null) {
                throw new IllegalArgumentException("applied result");
            }
        }
    }

    public record Rejected(RejectCode code, State unchanged) implements StepResult {
        public Rejected {
            if (code == null) {
                throw new IllegalArgumentException("code");
            }
        }
    }

    public static CreateResult createProfile(int overlapFrames) {
        if (overlapFrames < MIN_OVERLAP_FRAMES || overlapFrames > MAX_OVERLAP_FRAMES) {
            return new CreateRejected(RejectCode.OVERLAP_OUT_OF_RANGE);
        }
        return new Created(new Profile(overlapFrames));
    }

    /**
     * Creates an initial snapshot for a non-null profile returned by {@link #createProfile(int)}.
     * A null profile is a caller programming error, not a runtime state-machine input.
     */
    public static State initial(Profile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile");
        }
        return new State(
                SNAPSHOT_SCHEMA_VERSION,
                profile.overlapFrames(),
                0L,
                SemanticPhase.AWAITING_SPEECH,
                0L,
                0L,
                0L);
    }

    public static RestoreResult restore(Profile expectedProfile, State candidate) {
        RejectCode rejection = validateState(expectedProfile, candidate);
        if (rejection != null) {
            return new RestoreRejected(rejection);
        }
        return new Restored(copyOf(candidate));
    }

    public static StepResult step(
            Profile profile, State before, long frameIndex, FrameClass frameClass) {
        if (profile == null) {
            return new Rejected(RejectCode.NULL_PROFILE, before);
        }
        if (before == null) {
            return new Rejected(RejectCode.NULL_STATE, null);
        }
        if (frameClass == null) {
            return new Rejected(RejectCode.NULL_FRAME_CLASS, before);
        }

        RejectCode invalidState = validateState(profile, before);
        if (invalidState != null) {
            return new Rejected(invalidState, before);
        }
        if (frameIndex < 0L) {
            return new Rejected(RejectCode.FRAME_INDEX_NEGATIVE, before);
        }
        if (frameIndex != before.nextFrameIndex()) {
            return new Rejected(RejectCode.FRAME_INDEX_MISMATCH, before);
        }
        if (before.nextFrameIndex() >= MAX_SAFE_FRAME_COUNT) {
            return new Rejected(RejectCode.TIME_RANGE_EXHAUSTED, before);
        }

        try {
            long afterExclusive = Math.addExact(before.nextFrameIndex(), 1L);
            long groups = before.semanticGroupsOpened();
            long boundaries = before.semanticBoundariesEmitted();
            long silence = before.consecutiveSilenceFrames();
            SemanticPhase phase = before.phase();
            SemanticBoundary semanticEvent = null;

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
                    } else if (silence == SILENCE_BOUNDARY_FRAMES - 1L) {
                        long timelineNanos = Math.multiplyExact(afterExclusive, FRAME_NANOS);
                        long silenceNanos =
                                Math.multiplyExact(SILENCE_BOUNDARY_FRAMES, FRAME_NANOS);
                        long silenceStart = Math.subtractExact(afterExclusive, SILENCE_BOUNDARY_FRAMES);
                        semanticEvent =
                                new SemanticBoundary(
                                        boundaries,
                                        silenceStart,
                                        afterExclusive,
                                        silenceNanos,
                                        timelineNanos);
                        boundaries = Math.addExact(boundaries, 1L);
                        phase = SemanticPhase.AWAITING_SPEECH;
                        silence = 0L;
                    } else {
                        silence = Math.addExact(silence, 1L);
                    }
                }
            }

            State after =
                    new State(
                            SNAPSHOT_SCHEMA_VERSION,
                            profile.overlapFrames(),
                            afterExclusive,
                            phase,
                            silence,
                            groups,
                            boundaries);

            PhysicalCap physicalEvent = null;
            if (afterExclusive % PHYSICAL_CAP_FRAMES == 0L) {
                long partOrdinal = Math.subtractExact(afterExclusive / PHYSICAL_CAP_FRAMES, 1L);
                long freshStart = Math.subtractExact(afterExclusive, PHYSICAL_CAP_FRAMES);
                long overlapStart = Math.subtractExact(afterExclusive, profile.overlapFrames());
                physicalEvent =
                        new PhysicalCap(
                                partOrdinal,
                                freshStart,
                                afterExclusive,
                                profile.overlapFrames(),
                                overlapStart,
                                afterExclusive);
            }

            return new Applied(
                    after, Optional.ofNullable(semanticEvent), Optional.ofNullable(physicalEvent));
        } catch (ArithmeticException ignored) {
            return new Rejected(RejectCode.ARITHMETIC_OVERFLOW, before);
        }
    }

    private static RejectCode validateState(Profile expectedProfile, State state) {
        if (expectedProfile == null) {
            return RejectCode.NULL_PROFILE;
        }
        if (state == null) {
            return RejectCode.NULL_STATE;
        }
        if (state.schemaVersion() != SNAPSHOT_SCHEMA_VERSION) {
            return RejectCode.SCHEMA_MISMATCH;
        }
        if (state.profileOverlapFrames() != expectedProfile.overlapFrames()) {
            return RejectCode.PROFILE_MISMATCH;
        }
        if (state.nextFrameIndex() < 0L || state.nextFrameIndex() > MAX_SAFE_FRAME_COUNT) {
            return RejectCode.NEXT_FRAME_OUT_OF_RANGE;
        }
        if (state.phase() == null) {
            return RejectCode.PHASE_MISSING;
        }

        long silence = state.consecutiveSilenceFrames();
        if (state.phase() == SemanticPhase.ACTIVE_SILENCE) {
            if (silence < 1L || silence >= SILENCE_BOUNDARY_FRAMES) {
                return RejectCode.SILENCE_INVARIANT;
            }
        } else if (silence != 0L) {
            return RejectCode.SILENCE_INVARIANT;
        }

        long groups = state.semanticGroupsOpened();
        long boundaries = state.semanticBoundariesEmitted();
        if (groups < 0L || boundaries < 0L || boundaries > groups) {
            return RejectCode.GROUP_COUNTER_INVARIANT;
        }
        long openGroups = groups - boundaries;
        if (state.phase() == SemanticPhase.AWAITING_SPEECH) {
            if (openGroups != 0L) {
                return RejectCode.GROUP_COUNTER_INVARIANT;
            }
        } else if (openGroups != 1L) {
            return RejectCode.GROUP_COUNTER_INVARIANT;
        }
        if (groups > state.nextFrameIndex()) {
            return RejectCode.GROUP_COUNTER_INVARIANT;
        }

        try {
            long completedSilenceFrames = Math.multiplyExact(boundaries, SILENCE_BOUNDARY_FRAMES);
            long minimumFrames = Math.addExact(groups, completedSilenceFrames);
            minimumFrames = Math.addExact(minimumFrames, silence);
            if (minimumFrames > state.nextFrameIndex()) {
                return RejectCode.GROUP_COUNTER_INVARIANT;
            }
        } catch (ArithmeticException ignored) {
            return RejectCode.GROUP_COUNTER_INVARIANT;
        }
        return null;
    }

    private static State copyOf(State state) {
        return new State(
                state.schemaVersion(),
                state.profileOverlapFrames(),
                state.nextFrameIndex(),
                state.phase(),
                state.consecutiveSilenceFrames(),
                state.semanticGroupsOpened(),
                state.semanticBoundariesEmitted());
    }
}
