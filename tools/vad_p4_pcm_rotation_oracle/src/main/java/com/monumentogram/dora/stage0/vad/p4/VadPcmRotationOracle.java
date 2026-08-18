package com.monumentogram.dora.stage0.vad.p4;

import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.Accepted;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.PhysicalCap;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.Profile;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.ProfileAccepted;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.Restored;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Dependency-free Stage 0 proof of deterministic synthetic PCM16LE file rotation.
 *
 * <p>The oracle consumes only content-free metadata from the merged P3 replay and generates its
 * own deterministic synthetic bytes. It does not perform acoustic VAD, capture audio, read a
 * microphone, use a codec or container, load a model, call Android, use a device or emulator,
 * access a network, retain a dataset, or make a product-admission claim.
 */
public final class VadPcmRotationOracle {
    public static final int SNAPSHOT_SCHEMA_VERSION = 1;
    public static final int SAMPLE_RATE_HZ = VadDeterministicReplay.SAMPLE_RATE_HZ;
    public static final int CHANNEL_COUNT = 1;
    public static final int BITS_PER_SAMPLE = 16;
    public static final int BYTES_PER_SAMPLE = 2;
    public static final int SAMPLES_PER_FRAME = VadDeterministicReplay.SAMPLES_PER_FRAME;
    public static final int FRAME_MILLIS = 20;
    public static final int FRAME_BYTES = SAMPLES_PER_FRAME * BYTES_PER_SAMPLE;
    public static final long FRESH_CAP_FRAMES =
            VadDeterministicReplay.FRESH_PHYSICAL_CAP_FRAMES;
    public static final long FRESH_CAP_BYTES = FRESH_CAP_FRAMES * FRAME_BYTES;
    public static final int OVERLAP_FRAMES = VadDeterministicReplay.HARNESS_OVERLAP_FRAMES;
    public static final long OVERLAP_BYTES = (long) OVERLAP_FRAMES * FRAME_BYTES;
    public static final int PRE_ROLL_FRAMES = VadDeterministicReplay.HARNESS_PRE_ROLL_FRAMES;
    public static final long SYNTHETIC_SEED = 0x444F524156414434L;
    public static final long MAX_PROOF_FRESH_FRAMES = 60_025L;

    private static final String ROOT_PREFIX = "dora-vad-p4-owned-";
    private static final String MARKER_NAME = "vad-p4-owned.marker";
    private static final String IDENTITY_LINK_SUFFIX = ".identity-link";
    private static final byte[] MARKER_BYTES =
            "DORA_STAGE0_VAD_P4_OWNED_V1\n".getBytes(StandardCharsets.US_ASCII);

    private VadPcmRotationOracle() {}

    /** Stable fail-closed outcomes; messages deliberately contain no local paths or PCM bytes. */
    public enum RejectCode {
        NULL_TEMP_PARENT,
        NULL_ACCEPTED_REPLAY,
        P3_PROFILE_MISMATCH,
        P3_STATE_REJECTED,
        P3_CAP_COUNT_MISMATCH,
        P3_CAP_METADATA_MISMATCH,
        TOTAL_FRAMES_OUT_OF_RANGE,
        UNSAFE_DIRECTORY,
        DIRECTORY_CONTENT_MISMATCH,
        FILE_IDENTITY_UNAVAILABLE,
        FILE_IDENTITY_MISMATCH,
        MARKER_MISMATCH,
        FILE_MISSING,
        FILE_TRUNCATED,
        FILE_LENGTH_MISMATCH,
        FILE_CONTENT_MISMATCH,
        SCHEMA_MISMATCH,
        SNAPSHOT_CONSTANT_MISMATCH,
        SNAPSHOT_PLAN_MISMATCH,
        SNAPSHOT_BOUNDS_MISMATCH,
        SNAPSHOT_PART_MISMATCH,
        INPUT_ORDER_MISMATCH,
        INPUT_BOUNDS_MISMATCH,
        SESSION_STATE_MISMATCH,
        INCOMPLETE_SESSION,
        IO_FAILURE
    }

    /** Checked rejection used by every filesystem mutation boundary. */
    public static final class OracleException extends Exception {
        private static final long serialVersionUID = 1L;

        private final RejectCode code;

        private OracleException(RejectCode code, String message) {
            super(message);
            this.code = Objects.requireNonNull(code, "code");
        }

        private OracleException(RejectCode code, String message, Throwable cause) {
            super(message, cause);
            this.code = Objects.requireNonNull(code, "code");
        }

        public RejectCode code() {
            return code;
        }
    }

    /** Content-free snapshot of one exact owned file. */
    public record PartSnapshot(
            long partOrdinal,
            long storedStartFrameInclusive,
            long freshStartFrameInclusive,
            long freshEndFrameExclusive,
            long storedByteCount,
            long freshByteCount,
            String sha256,
            String fileIdentity) {}

    /**
     * Restart point for the local proof. File identities never enter canonical evidence output.
     */
    public record Snapshot(
            int schemaVersion,
            int sampleRateHz,
            int channelCount,
            int bitsPerSample,
            int samplesPerFrame,
            int frameBytes,
            long freshCapFrames,
            long freshCapBytes,
            int overlapFrames,
            long overlapBytes,
            long syntheticSeed,
            long totalFreshFrames,
            long nextFreshFrameIndex,
            String p3PlanSha256,
            String rootIdentity,
            String markerIdentity,
            List<PartSnapshot> parts) {
        public Snapshot {
            parts = parts == null ? null : List.copyOf(parts);
        }
    }

    public record PartProof(
            long partOrdinal,
            long storedStartFrameInclusive,
            long freshStartFrameInclusive,
            long freshEndFrameExclusive,
            long storedByteCount,
            long freshByteCount,
            long overlapPrefixByteCount,
            long overlapOwnerPartOrdinal,
            String sha256) {}

    public record OverlapProof(
            long closingPartOrdinal,
            long nextPartOrdinal,
            long overlapStartFrameInclusive,
            long overlapEndFrameExclusive,
            long overlapByteCount,
            boolean suffixPrefixBytesEqual,
            long ownerPartOrdinal) {}

    public record ClosingOwnershipProof(
            long closingPartOrdinal,
            long preRollStartFrameInclusive,
            long overlapStartFrameInclusive,
            long nonOverlapPrefixEndFrameExclusive,
            long nonOverlapPrefixByteCount,
            long ownerPartOrdinal) {}

    /** Deterministic content-free completion result. */
    public record Completion(
            int schemaVersion,
            long totalFreshFrames,
            long totalFreshBytes,
            List<PartProof> parts,
            List<OverlapProof> overlaps,
            List<ClosingOwnershipProof> closingOwnership) {
        public Completion {
            parts = parts == null ? null : List.copyOf(parts);
            overlaps = overlaps == null ? null : List.copyOf(overlaps);
            closingOwnership =
                    closingOwnership == null ? null : List.copyOf(closingOwnership);
        }
    }

    /** Create a new bounded, oracle-owned child under an existing temporary parent directory. */
    public static Session create(Path temporaryParent, Accepted accepted) throws OracleException {
        Plan plan = validateP3Plan(accepted);
        Path parent = requireSafeParent(temporaryParent);
        Path root = null;
        try {
            root = Files.createTempDirectory(parent, ROOT_PREFIX).toAbsolutePath().normalize();
            if (!root.getParent().equals(parent)
                    || !root.getFileName().toString().startsWith(ROOT_PREFIX)
                    || Files.isSymbolicLink(root)) {
                throw rejection(RejectCode.UNSAFE_DIRECTORY, "owned directory validation failed");
            }
            String rootIdentity = directoryIdentity(root);
            Path marker = resolveOwned(root, MARKER_NAME);
            Files.write(
                    marker,
                    MARKER_BYTES,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            String markerIdentity = regularFileIdentity(marker);
            Part partZero = createPart(root, 0L);
            return new Session(
                    root,
                    rootIdentity,
                    marker,
                    markerIdentity,
                    plan,
                    new ArrayList<>(List.of(partZero)),
                    partZero,
                    0L);
        } catch (OracleException exception) {
            cleanupIncompleteCreate(root);
            throw exception;
        } catch (IOException exception) {
            cleanupIncompleteCreate(root);
            throw rejection(
                    RejectCode.IO_FAILURE, "owned directory creation failed", exception);
        }
    }

    /** Restore only an exact, untampered snapshot in the same owned directory. */
    public static Session restore(Path ownedDirectory, Accepted accepted, Snapshot candidate)
            throws OracleException {
        Plan plan = validateP3Plan(accepted);
        if (candidate == null) {
            throw rejection(RejectCode.SCHEMA_MISMATCH, "snapshot is required");
        }
        Path root = requireSafeOwnedDirectory(ownedDirectory);
        validateSnapshotAndFiles(root, plan, candidate);

        List<Part> parts = new ArrayList<>();
        for (PartSnapshot partSnapshot : candidate.parts()) {
            Path path = resolveOwned(root, partName(partSnapshot.partOrdinal()));
            Path identityLink =
                    resolveOwned(root, identityLinkName(partSnapshot.partOrdinal()));
            parts.add(
                    new Part(
                            partSnapshot.partOrdinal(),
                            path,
                            identityLink,
                            partSnapshot.fileIdentity(),
                            null));
        }
        Part current = parts.get(parts.size() - 1);
        try {
            current.channel =
                    FileChannel.open(
                            current.path,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS);
            current.channel.position(candidate.parts().get(parts.size() - 1).storedByteCount());
        } catch (IOException exception) {
            closeQuietly(current.channel);
            throw rejection(RejectCode.IO_FAILURE, "snapshot reopen failed", exception);
        }
        return new Session(
                root,
                candidate.rootIdentity(),
                resolveOwned(root, MARKER_NAME),
                candidate.markerIdentity(),
                plan,
                parts,
                current,
                candidate.nextFreshFrameIndex());
    }

    /** Generate one deterministic mono signed PCM16LE 20 ms frame. */
    public static byte[] syntheticFrame(long frameIndex) {
        if (frameIndex < 0L || frameIndex >= MAX_PROOF_FRESH_FRAMES) {
            throw new IllegalArgumentException("frameIndex outside proof bound");
        }
        byte[] frame = new byte[FRAME_BYTES];
        long firstSample = Math.multiplyExact(frameIndex, (long) SAMPLES_PER_FRAME);
        for (int sampleOffset = 0; sampleOffset < SAMPLES_PER_FRAME; sampleOffset++) {
            long value = firstSample + sampleOffset + SYNTHETIC_SEED;
            value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
            value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
            value ^= value >>> 31;
            short pcm = (short) value;
            int byteOffset = sampleOffset * BYTES_PER_SAMPLE;
            frame[byteOffset] = (byte) (pcm & 0xff);
            frame[byteOffset + 1] = (byte) ((pcm >>> 8) & 0xff);
        }
        return frame;
    }

    /** Stable serialization excluding root paths and filesystem identities. */
    public static String canonicalSummary(Completion completion) {
        if (completion == null
                || completion.parts() == null
                || completion.overlaps() == null
                || completion.closingOwnership() == null) {
            throw new IllegalArgumentException("complete result required");
        }
        StringBuilder summary = new StringBuilder(512);
        summary.append("v1|")
                .append(SAMPLE_RATE_HZ)
                .append('|')
                .append(CHANNEL_COUNT)
                .append('|')
                .append(BITS_PER_SAMPLE)
                .append('|')
                .append(SAMPLES_PER_FRAME)
                .append('|')
                .append(FRAME_BYTES)
                .append('|')
                .append(FRESH_CAP_FRAMES)
                .append('|')
                .append(FRESH_CAP_BYTES)
                .append('|')
                .append(OVERLAP_FRAMES)
                .append('|')
                .append(OVERLAP_BYTES)
                .append('|')
                .append(completion.totalFreshFrames())
                .append('|')
                .append(completion.totalFreshBytes());
        for (PartProof part : completion.parts()) {
            summary.append("|P:")
                    .append(part.partOrdinal())
                    .append(',')
                    .append(part.storedStartFrameInclusive())
                    .append(',')
                    .append(part.freshStartFrameInclusive())
                    .append(',')
                    .append(part.freshEndFrameExclusive())
                    .append(',')
                    .append(part.storedByteCount())
                    .append(',')
                    .append(part.freshByteCount())
                    .append(',')
                    .append(part.overlapPrefixByteCount())
                    .append(',')
                    .append(part.overlapOwnerPartOrdinal())
                    .append(',')
                    .append(part.sha256());
        }
        for (OverlapProof overlap : completion.overlaps()) {
            summary.append("|O:")
                    .append(overlap.closingPartOrdinal())
                    .append(',')
                    .append(overlap.nextPartOrdinal())
                    .append(',')
                    .append(overlap.overlapStartFrameInclusive())
                    .append(',')
                    .append(overlap.overlapEndFrameExclusive())
                    .append(',')
                    .append(overlap.overlapByteCount())
                    .append(',')
                    .append(overlap.suffixPrefixBytesEqual())
                    .append(',')
                    .append(overlap.ownerPartOrdinal());
        }
        for (ClosingOwnershipProof ownership : completion.closingOwnership()) {
            summary.append("|C:")
                    .append(ownership.closingPartOrdinal())
                    .append(',')
                    .append(ownership.preRollStartFrameInclusive())
                    .append(',')
                    .append(ownership.overlapStartFrameInclusive())
                    .append(',')
                    .append(ownership.nonOverlapPrefixEndFrameExclusive())
                    .append(',')
                    .append(ownership.nonOverlapPrefixByteCount())
                    .append(',')
                    .append(ownership.ownerPartOrdinal());
        }
        return summary.toString();
    }

    /** A single-owner mutable session. It intentionally has no implicit recursive close. */
    public static final class Session {
        private final Path root;
        private final String rootIdentity;
        private final Path marker;
        private final String markerIdentity;
        private final Plan plan;
        private final List<Part> parts;
        private Part current;
        private long nextFreshFrame;
        private SessionState state;

        private Session(
                Path root,
                String rootIdentity,
                Path marker,
                String markerIdentity,
                Plan plan,
                List<Part> parts,
                Part current,
                long nextFreshFrame) {
            this.root = root;
            this.rootIdentity = rootIdentity;
            this.marker = marker;
            this.markerIdentity = markerIdentity;
            this.plan = plan;
            this.parts = parts;
            this.current = current;
            this.nextFreshFrame = nextFreshFrame;
            state = SessionState.OPEN;
        }

        /** Append only the oracle's deterministic frames in exact global-frame order. */
        public void appendSynthetic(long firstFrameIndex, int frameCount)
                throws OracleException {
            requireState(SessionState.OPEN);
            if (firstFrameIndex != nextFreshFrame) {
                throw rejection(RejectCode.INPUT_ORDER_MISMATCH, "frame order rejected");
            }
            if (frameCount < 0
                    || (long) frameCount > plan.totalFreshFrames - nextFreshFrame) {
                throw rejection(RejectCode.INPUT_BOUNDS_MISMATCH, "frame range rejected");
            }
            verifyCurrentIdentityAndLength();
            try {
                for (int offset = 0; offset < frameCount; offset++) {
                    if (nextFreshFrame > 0L && nextFreshFrame % FRESH_CAP_FRAMES == 0L) {
                        rotate();
                    }
                    writeFully(current.channel, ByteBuffer.wrap(syntheticFrame(nextFreshFrame)));
                    nextFreshFrame = Math.addExact(nextFreshFrame, 1L);
                }
            } catch (IOException | ArithmeticException exception) {
                throw rejection(RejectCode.IO_FAILURE, "synthetic append failed", exception);
            }
        }

        /** Verify every byte and return an immutable restart point without closing the session. */
        public Snapshot snapshot() throws OracleException {
            requireState(SessionState.OPEN);
            forceCurrent();
            return buildVerifiedSnapshot();
        }

        /** Verify, snapshot, and relinquish all open handles for an exact restore. */
        public Snapshot suspend() throws OracleException {
            Snapshot snapshot = snapshot();
            closeCurrent();
            state = SessionState.SUSPENDED;
            return snapshot;
        }

        /** Complete only after the exact P3-declared fresh-frame range has been written. */
        public Completion finish() throws OracleException {
            requireState(SessionState.OPEN);
            if (nextFreshFrame != plan.totalFreshFrames) {
                throw rejection(RejectCode.INCOMPLETE_SESSION, "fresh range is incomplete");
            }
            Snapshot snapshot = snapshot();
            closeCurrent();
            state = SessionState.FINISHED;
            return deriveCompletion(root, plan, snapshot);
        }

        /**
         * Delete only the exact verified identities created by this session. Any replacement,
         * unexpected entry, length change, or byte change causes a rejection before deletion.
         */
        public void cleanup() throws OracleException {
            if (state != SessionState.OPEN && state != SessionState.FINISHED) {
                throw rejection(RejectCode.SESSION_STATE_MISMATCH, "cleanup state rejected");
            }
            if (state == SessionState.OPEN) {
                forceCurrent();
            }
            verifyRuntimeFiles();
            closeCurrent();
            try {
                for (int index = parts.size() - 1; index >= 0; index--) {
                    Part part = parts.get(index);
                    requireHardLink(part.path, part.identityLink);
                    requireIdentity(part.path, part.fileIdentity, false);
                    Files.delete(part.path);
                    requireIdentity(part.identityLink, part.fileIdentity, false);
                    Files.delete(part.identityLink);
                }
                requireIdentity(marker, markerIdentity, false);
                verifyMarker(marker);
                Files.delete(marker);
                requireIdentity(root, rootIdentity, true);
                requireDirectoryNames(root, Set.of());
                Files.delete(root);
                state = SessionState.CLEANED;
            } catch (IOException exception) {
                state = SessionState.POISONED;
                throw rejection(RejectCode.IO_FAILURE, "verified cleanup failed", exception);
            }
        }

        Path ownedDirectoryForTesting() {
            return root;
        }

        Path partPathForTesting(long ordinal) throws OracleException {
            return resolveOwned(root, partName(ordinal));
        }

        Path markerPathForTesting() throws OracleException {
            return resolveOwned(root, MARKER_NAME);
        }

        private void rotate() throws IOException, OracleException {
            long nextOrdinal = Math.addExact(current.ordinal, 1L);
            long overlapStart = Math.subtractExact(nextFreshFrame, OVERLAP_FRAMES);
            long overlapOffset =
                    Math.multiplyExact(
                            Math.subtractExact(overlapStart, storedStart(current.ordinal)),
                            (long) FRAME_BYTES);
            current.channel.force(true);
            byte[] suffix = readRange(current.path, overlapOffset, Math.toIntExact(OVERLAP_BYTES));
            byte[] expected = expectedFrames(overlapStart, OVERLAP_FRAMES);
            if (!Arrays.equals(expected, suffix)) {
                throw rejection(
                        RejectCode.FILE_CONTENT_MISMATCH, "closing suffix verification failed");
            }
            closeCurrent();
            Part next = createPart(root, nextOrdinal);
            try {
                writeFully(next.channel, ByteBuffer.wrap(suffix));
                next.channel.force(true);
            } catch (IOException exception) {
                closeQuietly(next.channel);
                throw exception;
            }
            parts.add(next);
            current = next;
        }

        private Snapshot buildVerifiedSnapshot() throws OracleException {
            requireIdentity(root, rootIdentity, true);
            requireIdentity(marker, markerIdentity, false);
            verifyMarker(marker);
            requireDirectoryNames(root, expectedDirectoryNames(parts.size()));

            List<PartSnapshot> snapshots = new ArrayList<>();
            int expectedCount = expectedPartCount(nextFreshFrame);
            if (parts.size() != expectedCount) {
                throw rejection(
                        RejectCode.SNAPSHOT_PART_MISMATCH, "runtime part count rejected");
            }
            for (int ordinal = 0; ordinal < parts.size(); ordinal++) {
                Part part = parts.get(ordinal);
                if (part.ordinal != ordinal) {
                    throw rejection(
                            RejectCode.SNAPSHOT_PART_MISMATCH, "runtime part order rejected");
                }
                PartShape shape = expectedPartShape(ordinal, nextFreshFrame);
                Inspection inspection =
                        inspectExpectedPart(
                                part.path, part.identityLink, part.fileIdentity, shape);
                snapshots.add(
                        new PartSnapshot(
                                ordinal,
                                shape.storedStart,
                                shape.freshStart,
                                shape.freshEnd,
                                shape.storedBytes,
                                shape.freshBytes,
                                inspection.sha256,
                                inspection.identity));
            }
            return new Snapshot(
                    SNAPSHOT_SCHEMA_VERSION,
                    SAMPLE_RATE_HZ,
                    CHANNEL_COUNT,
                    BITS_PER_SAMPLE,
                    SAMPLES_PER_FRAME,
                    FRAME_BYTES,
                    FRESH_CAP_FRAMES,
                    FRESH_CAP_BYTES,
                    OVERLAP_FRAMES,
                    OVERLAP_BYTES,
                    SYNTHETIC_SEED,
                    plan.totalFreshFrames,
                    nextFreshFrame,
                    plan.p3PlanSha256,
                    rootIdentity,
                    markerIdentity,
                    snapshots);
        }

        private void verifyRuntimeFiles() throws OracleException {
            requireIdentity(root, rootIdentity, true);
            requireIdentity(marker, markerIdentity, false);
            verifyMarker(marker);
            requireDirectoryNames(root, expectedDirectoryNames(parts.size()));
            if (parts.size() != expectedPartCount(nextFreshFrame)) {
                throw rejection(
                        RejectCode.SNAPSHOT_PART_MISMATCH, "runtime part count rejected");
            }
            for (int ordinal = 0; ordinal < parts.size(); ordinal++) {
                Part part = parts.get(ordinal);
                if (part.ordinal != ordinal) {
                    throw rejection(
                            RejectCode.SNAPSHOT_PART_MISMATCH, "runtime part order rejected");
                }
                inspectExpectedPart(
                        part.path,
                        part.identityLink,
                        part.fileIdentity,
                        expectedPartShape(ordinal, nextFreshFrame));
            }
        }

        private void verifyCurrentIdentityAndLength() throws OracleException {
            requireIdentity(current.path, current.fileIdentity, false);
            PartShape shape = expectedPartShape(Math.toIntExact(current.ordinal), nextFreshFrame);
            try {
                long actual = Files.size(current.path);
                if (actual < shape.storedBytes) {
                    throw rejection(RejectCode.FILE_TRUNCATED, "current file is truncated");
                }
                if (actual > shape.storedBytes) {
                    throw rejection(
                            RejectCode.FILE_LENGTH_MISMATCH, "current file length rejected");
                }
                if (current.channel.position() != shape.storedBytes) {
                    throw rejection(
                            RejectCode.FILE_LENGTH_MISMATCH, "current write position rejected");
                }
            } catch (IOException exception) {
                throw rejection(RejectCode.IO_FAILURE, "current file inspection failed", exception);
            }
        }

        private void forceCurrent() throws OracleException {
            try {
                current.channel.force(true);
            } catch (IOException exception) {
                throw rejection(RejectCode.IO_FAILURE, "file force failed", exception);
            }
        }

        private void closeCurrent() throws OracleException {
            try {
                if (current.channel != null && current.channel.isOpen()) {
                    current.channel.close();
                }
            } catch (IOException exception) {
                throw rejection(RejectCode.IO_FAILURE, "file close failed", exception);
            }
        }

        private void requireState(SessionState required) throws OracleException {
            if (state != required) {
                throw rejection(RejectCode.SESSION_STATE_MISMATCH, "session state rejected");
            }
        }
    }

    private enum SessionState {
        OPEN,
        SUSPENDED,
        FINISHED,
        CLEANED,
        POISONED
    }

    private static final class Part {
        private final long ordinal;
        private final Path path;
        private final Path identityLink;
        private final String fileIdentity;
        private FileChannel channel;

        private Part(
                long ordinal,
                Path path,
                Path identityLink,
                String fileIdentity,
                FileChannel channel) {
            this.ordinal = ordinal;
            this.path = path;
            this.identityLink = identityLink;
            this.fileIdentity = fileIdentity;
            this.channel = channel;
        }
    }

    private record Plan(long totalFreshFrames, String p3PlanSha256, List<PhysicalCap> caps) {
        private Plan {
            caps = List.copyOf(caps);
        }
    }

    private record PartShape(
            long storedStart,
            long freshStart,
            long freshEnd,
            long storedBytes,
            long freshBytes) {}

    private record Inspection(String identity, long byteCount, String sha256) {}

    private static Plan validateP3Plan(Accepted accepted) throws OracleException {
        if (accepted == null) {
            throw rejection(RejectCode.NULL_ACCEPTED_REPLAY, "accepted replay is required");
        }
        Object profileResult =
                VadDeterministicReplay.createProfile(PRE_ROLL_FRAMES, OVERLAP_FRAMES);
        if (!(profileResult instanceof ProfileAccepted profileAccepted)) {
            throw rejection(RejectCode.P3_PROFILE_MISMATCH, "P3 profile rejected");
        }
        Profile profile = profileAccepted.profile();
        if (!(VadDeterministicReplay.restore(profile, accepted.after()) instanceof Restored)) {
            throw rejection(RejectCode.P3_STATE_REJECTED, "P3 terminal state rejected");
        }
        long total = accepted.after().nextFrameIndex();
        if (total < 0L || total > MAX_PROOF_FRESH_FRAMES) {
            throw rejection(
                    RejectCode.TOTAL_FRAMES_OUT_OF_RANGE, "P3 fresh-frame bound rejected");
        }
        long expectedCapCount = total / FRESH_CAP_FRAMES;
        if (accepted.physicalCaps().size() != expectedCapCount) {
            throw rejection(
                    RejectCode.P3_CAP_COUNT_MISMATCH, "P3 cap count rejected");
        }
        for (int index = 0; index < accepted.physicalCaps().size(); index++) {
            long ordinal = index;
            long freshStart = Math.multiplyExact(ordinal, FRESH_CAP_FRAMES);
            long freshEnd = Math.addExact(freshStart, FRESH_CAP_FRAMES);
            PhysicalCap expected =
                    new PhysicalCap(
                            ordinal,
                            freshStart,
                            freshEnd,
                            ordinal + 1L,
                            freshEnd - OVERLAP_FRAMES,
                            freshEnd,
                            ordinal);
            if (!expected.equals(accepted.physicalCaps().get(index))) {
                throw rejection(
                        RejectCode.P3_CAP_METADATA_MISMATCH, "P3 cap metadata rejected");
            }
        }
        String fingerprint = sha256(VadDeterministicReplay.canonicalSummary(accepted));
        return new Plan(total, fingerprint, accepted.physicalCaps());
    }

    private static Path requireSafeParent(Path temporaryParent) throws OracleException {
        if (temporaryParent == null) {
            throw rejection(RejectCode.NULL_TEMP_PARENT, "temporary parent is required");
        }
        try {
            Path parent = temporaryParent.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (Files.isSymbolicLink(parent)
                    || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
                throw rejection(RejectCode.UNSAFE_DIRECTORY, "temporary parent rejected");
            }
            directoryIdentity(parent);
            return parent;
        } catch (IOException exception) {
            throw rejection(RejectCode.UNSAFE_DIRECTORY, "temporary parent rejected", exception);
        }
    }

    private static Path requireSafeOwnedDirectory(Path ownedDirectory) throws OracleException {
        if (ownedDirectory == null) {
            throw rejection(RejectCode.UNSAFE_DIRECTORY, "owned directory is required");
        }
        try {
            Path root = ownedDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (Files.isSymbolicLink(root)
                    || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || !root.getFileName().toString().startsWith(ROOT_PREFIX)) {
                throw rejection(RejectCode.UNSAFE_DIRECTORY, "owned directory rejected");
            }
            directoryIdentity(root);
            return root;
        } catch (IOException exception) {
            throw rejection(RejectCode.UNSAFE_DIRECTORY, "owned directory rejected", exception);
        }
    }

    private static void validateSnapshotAndFiles(Path root, Plan plan, Snapshot snapshot)
            throws OracleException {
        if (snapshot.schemaVersion() != SNAPSHOT_SCHEMA_VERSION) {
            throw rejection(RejectCode.SCHEMA_MISMATCH, "snapshot schema rejected");
        }
        if (snapshot.sampleRateHz() != SAMPLE_RATE_HZ
                || snapshot.channelCount() != CHANNEL_COUNT
                || snapshot.bitsPerSample() != BITS_PER_SAMPLE
                || snapshot.samplesPerFrame() != SAMPLES_PER_FRAME
                || snapshot.frameBytes() != FRAME_BYTES
                || snapshot.freshCapFrames() != FRESH_CAP_FRAMES
                || snapshot.freshCapBytes() != FRESH_CAP_BYTES
                || snapshot.overlapFrames() != OVERLAP_FRAMES
                || snapshot.overlapBytes() != OVERLAP_BYTES
                || snapshot.syntheticSeed() != SYNTHETIC_SEED) {
            throw rejection(
                    RejectCode.SNAPSHOT_CONSTANT_MISMATCH, "snapshot constants rejected");
        }
        if (snapshot.totalFreshFrames() != plan.totalFreshFrames
                || !Objects.equals(snapshot.p3PlanSha256(), plan.p3PlanSha256)) {
            throw rejection(RejectCode.SNAPSHOT_PLAN_MISMATCH, "snapshot plan rejected");
        }
        if (snapshot.nextFreshFrameIndex() < 0L
                || snapshot.nextFreshFrameIndex() > plan.totalFreshFrames) {
            throw rejection(RejectCode.SNAPSHOT_BOUNDS_MISMATCH, "snapshot bounds rejected");
        }
        String actualRootIdentity = directoryIdentity(root);
        if (!Objects.equals(snapshot.rootIdentity(), actualRootIdentity)) {
            throw rejection(
                    RejectCode.FILE_IDENTITY_MISMATCH, "root identity rejected");
        }
        Path marker = resolveOwned(root, MARKER_NAME);
        requireIdentity(marker, snapshot.markerIdentity(), false);
        verifyMarker(marker);

        int expectedCount = expectedPartCount(snapshot.nextFreshFrameIndex());
        if (snapshot.parts() == null || snapshot.parts().size() != expectedCount) {
            throw rejection(RejectCode.SNAPSHOT_PART_MISMATCH, "snapshot part count rejected");
        }
        requireDirectoryNames(root, expectedDirectoryNames(expectedCount));
        for (int ordinal = 0; ordinal < expectedCount; ordinal++) {
            PartSnapshot candidate = snapshot.parts().get(ordinal);
            PartShape shape = expectedPartShape(ordinal, snapshot.nextFreshFrameIndex());
            if (candidate == null
                    || candidate.partOrdinal() != ordinal
                    || candidate.storedStartFrameInclusive() != shape.storedStart
                    || candidate.freshStartFrameInclusive() != shape.freshStart
                    || candidate.freshEndFrameExclusive() != shape.freshEnd
                    || candidate.storedByteCount() != shape.storedBytes
                    || candidate.freshByteCount() != shape.freshBytes
                    || !isLowerHexSha256(candidate.sha256())
                    || candidate.fileIdentity() == null
                    || candidate.fileIdentity().isEmpty()) {
                throw rejection(
                        RejectCode.SNAPSHOT_PART_MISMATCH, "snapshot part metadata rejected");
            }
            Path path = resolveOwned(root, partName(ordinal));
            Path identityLink = resolveOwned(root, identityLinkName(ordinal));
            Inspection inspection =
                    inspectExpectedPart(path, identityLink, candidate.fileIdentity(), shape);
            if (!candidate.sha256().equals(inspection.sha256)) {
                throw rejection(
                        RejectCode.FILE_CONTENT_MISMATCH, "snapshot digest rejected");
            }
        }
        requireIdentity(root, snapshot.rootIdentity(), true);
    }

    private static Completion deriveCompletion(Path root, Plan plan, Snapshot snapshot)
            throws OracleException {
        List<PartProof> partProofs = new ArrayList<>();
        for (PartSnapshot part : snapshot.parts()) {
            long prefixBytes = part.partOrdinal() == 0L ? 0L : OVERLAP_BYTES;
            long owner = part.partOrdinal() == 0L ? -1L : part.partOrdinal() - 1L;
            partProofs.add(
                    new PartProof(
                            part.partOrdinal(),
                            part.storedStartFrameInclusive(),
                            part.freshStartFrameInclusive(),
                            part.freshEndFrameExclusive(),
                            part.storedByteCount(),
                            part.freshByteCount(),
                            prefixBytes,
                            owner,
                            part.sha256()));
        }

        List<OverlapProof> overlapProofs = new ArrayList<>();
        for (int ordinal = 1; ordinal < snapshot.parts().size(); ordinal++) {
            PartSnapshot closing = snapshot.parts().get(ordinal - 1);
            PartSnapshot next = snapshot.parts().get(ordinal);
            byte[] suffix =
                    readRange(
                            resolveOwned(root, partName(closing.partOrdinal())),
                            closing.storedByteCount() - OVERLAP_BYTES,
                            Math.toIntExact(OVERLAP_BYTES));
            byte[] prefix =
                    readRange(
                            resolveOwned(root, partName(next.partOrdinal())),
                            0L,
                            Math.toIntExact(OVERLAP_BYTES));
            if (!Arrays.equals(suffix, prefix)) {
                throw rejection(
                        RejectCode.FILE_CONTENT_MISMATCH, "overlap byte equality rejected");
            }
            PhysicalCap cap = plan.caps.get(ordinal - 1);
            overlapProofs.add(
                    new OverlapProof(
                            cap.closedPartOrdinal(),
                            cap.nextPartOrdinal(),
                            cap.nextPartOverlapStartFrameInclusive(),
                            cap.nextPartOverlapEndFrameExclusive(),
                            OVERLAP_BYTES,
                            true,
                            cap.overlapOwnerPartOrdinal()));
        }

        List<ClosingOwnershipProof> ownershipProofs = new ArrayList<>();
        long nonOverlapFrames = PRE_ROLL_FRAMES - OVERLAP_FRAMES;
        for (PhysicalCap cap : plan.caps) {
            long preRollStart = cap.freshEndFrameExclusive() - PRE_ROLL_FRAMES;
            long prefixEnd = cap.nextPartOverlapStartFrameInclusive();
            ownershipProofs.add(
                    new ClosingOwnershipProof(
                            cap.closedPartOrdinal(),
                            preRollStart,
                            cap.nextPartOverlapStartFrameInclusive(),
                            prefixEnd,
                            Math.multiplyExact(nonOverlapFrames, (long) FRAME_BYTES),
                            cap.closedPartOrdinal()));
        }
        return new Completion(
                SNAPSHOT_SCHEMA_VERSION,
                plan.totalFreshFrames,
                Math.multiplyExact(plan.totalFreshFrames, (long) FRAME_BYTES),
                partProofs,
                overlapProofs,
                ownershipProofs);
    }

    private static Part createPart(Path root, long ordinal) throws OracleException {
        Path path = resolveOwned(root, partName(ordinal));
        Path identityLink = resolveOwned(root, identityLinkName(ordinal));
        FileChannel channel = null;
        try {
            channel =
                    FileChannel.open(
                            path,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS);
            Files.createLink(identityLink, path);
            requireHardLink(path, identityLink);
            String identity = regularFileIdentity(path);
            return new Part(ordinal, path, identityLink, identity, channel);
        } catch (IOException exception) {
            closeQuietly(channel);
            deleteIfExistsQuietly(identityLink);
            deleteIfExistsQuietly(path);
            throw rejection(RejectCode.IO_FAILURE, "part creation failed", exception);
        } catch (OracleException exception) {
            closeQuietly(channel);
            deleteIfExistsQuietly(identityLink);
            deleteIfExistsQuietly(path);
            throw exception;
        }
    }

    private static Inspection inspectExpectedPart(
            Path path, Path identityLink, String expectedIdentity, PartShape shape)
            throws OracleException {
        requireHardLink(path, identityLink);
        String identity = requireIdentity(path, expectedIdentity, false);
        try {
            long actualLength = Files.size(path);
            if (actualLength < shape.storedBytes) {
                throw rejection(RejectCode.FILE_TRUNCATED, "part is truncated");
            }
            if (actualLength > shape.storedBytes) {
                throw rejection(RejectCode.FILE_LENGTH_MISMATCH, "part length rejected");
            }
            MessageDigest digest = newSha256();
            try (FileChannel channel =
                    FileChannel.open(
                            path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer actual = ByteBuffer.allocate(FRAME_BYTES);
                for (long frameIndex = shape.storedStart;
                        frameIndex < shape.freshEnd;
                        frameIndex++) {
                    actual.clear();
                    readFully(channel, actual);
                    byte[] actualBytes = actual.array();
                    byte[] expectedBytes = syntheticFrame(frameIndex);
                    if (!Arrays.equals(expectedBytes, actualBytes)) {
                        throw rejection(
                                RejectCode.FILE_CONTENT_MISMATCH, "part bytes rejected");
                    }
                    digest.update(actualBytes);
                }
            }
            requireIdentity(path, identity, false);
            requireHardLink(path, identityLink);
            return new Inspection(
                    identity, actualLength, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException exception) {
            throw rejection(RejectCode.IO_FAILURE, "part inspection failed", exception);
        }
    }

    private static PartShape expectedPartShape(int ordinal, long nextFreshFrame)
            throws OracleException {
        if (ordinal < 0 || ordinal >= expectedPartCount(nextFreshFrame)) {
            throw rejection(RejectCode.SNAPSHOT_PART_MISMATCH, "part ordinal rejected");
        }
        long freshStart = Math.multiplyExact((long) ordinal, FRESH_CAP_FRAMES);
        long storedStart = ordinal == 0 ? 0L : freshStart - OVERLAP_FRAMES;
        long freshEnd = Math.min(nextFreshFrame, freshStart + FRESH_CAP_FRAMES);
        long storedBytes = Math.multiplyExact(freshEnd - storedStart, (long) FRAME_BYTES);
        long freshBytes = Math.multiplyExact(freshEnd - freshStart, (long) FRAME_BYTES);
        return new PartShape(storedStart, freshStart, freshEnd, storedBytes, freshBytes);
    }

    private static int expectedPartCount(long nextFreshFrame) {
        if (nextFreshFrame == 0L) {
            return 1;
        }
        return Math.toIntExact(((nextFreshFrame - 1L) / FRESH_CAP_FRAMES) + 1L);
    }

    private static long storedStart(long ordinal) {
        long freshStart = Math.multiplyExact(ordinal, FRESH_CAP_FRAMES);
        return ordinal == 0L ? 0L : freshStart - OVERLAP_FRAMES;
    }

    private static byte[] expectedFrames(long firstFrame, int count) {
        byte[] result = new byte[Math.multiplyExact(count, FRAME_BYTES)];
        for (int offset = 0; offset < count; offset++) {
            byte[] frame = syntheticFrame(firstFrame + offset);
            System.arraycopy(frame, 0, result, offset * FRAME_BYTES, FRAME_BYTES);
        }
        return result;
    }

    private static byte[] readRange(Path path, long offset, int length) throws OracleException {
        byte[] bytes = new byte[length];
        try (FileChannel channel =
                FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            channel.position(offset);
            readFully(channel, ByteBuffer.wrap(bytes));
            return bytes;
        } catch (IOException exception) {
            throw rejection(RejectCode.IO_FAILURE, "bounded range read failed", exception);
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer target)
            throws IOException, OracleException {
        while (target.hasRemaining()) {
            int read = channel.read(target);
            if (read < 0) {
                throw rejection(RejectCode.FILE_TRUNCATED, "bounded read was truncated");
            }
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer source) throws IOException {
        while (source.hasRemaining()) {
            channel.write(source);
        }
    }

    private static Path resolveOwned(Path root, String fileName) throws OracleException {
        Path candidate = root.resolve(fileName).toAbsolutePath().normalize();
        if (!candidate.getParent().equals(root)
                || !candidate.getFileName().toString().equals(fileName)) {
            throw rejection(RejectCode.UNSAFE_DIRECTORY, "owned file path rejected");
        }
        return candidate;
    }

    private static String partName(long ordinal) throws OracleException {
        if (ordinal < 0L || ordinal > 9_999L) {
            throw rejection(RejectCode.SNAPSHOT_PART_MISMATCH, "part ordinal rejected");
        }
        return String.format(java.util.Locale.ROOT, "part-%05d.pcm16le", ordinal);
    }

    private static Set<String> expectedDirectoryNames(int partCount) throws OracleException {
        Set<String> names = new HashSet<>();
        names.add(MARKER_NAME);
        for (int ordinal = 0; ordinal < partCount; ordinal++) {
            names.add(partName(ordinal));
            names.add(identityLinkName(ordinal));
        }
        return names;
    }

    private static String identityLinkName(long ordinal) throws OracleException {
        return partName(ordinal) + IDENTITY_LINK_SUFFIX;
    }

    private static void requireHardLink(Path part, Path identityLink) throws OracleException {
        try {
            if (Files.isSymbolicLink(part)
                    || Files.isSymbolicLink(identityLink)
                    || !Files.isSameFile(part, identityLink)) {
                throw rejection(
                        RejectCode.FILE_IDENTITY_MISMATCH, "hard-link identity rejected");
            }
        } catch (IOException exception) {
            throw rejection(
                    RejectCode.FILE_IDENTITY_MISMATCH,
                    "hard-link identity rejected",
                    exception);
        }
    }

    private static void requireDirectoryNames(Path root, Set<String> expected)
            throws OracleException {
        Set<String> actual = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                actual.add(entry.getFileName().toString());
            }
        } catch (IOException exception) {
            throw rejection(RejectCode.IO_FAILURE, "directory inspection failed", exception);
        }
        if (!actual.equals(expected)) {
            throw rejection(
                    RejectCode.DIRECTORY_CONTENT_MISMATCH, "directory contents rejected");
        }
    }

    private static String directoryIdentity(Path path) throws OracleException {
        try {
            BasicFileAttributes attributes =
                    Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
                throw rejection(RejectCode.UNSAFE_DIRECTORY, "directory type rejected");
            }
            return stableIdentity(attributes);
        } catch (IOException exception) {
            throw rejection(RejectCode.UNSAFE_DIRECTORY, "directory identity failed", exception);
        }
    }

    private static String regularFileIdentity(Path path) throws OracleException {
        try {
            BasicFileAttributes attributes =
                    Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                throw rejection(RejectCode.FILE_MISSING, "regular file type rejected");
            }
            return stableIdentity(attributes);
        } catch (IOException exception) {
            throw rejection(RejectCode.FILE_MISSING, "regular file identity failed", exception);
        }
    }

    private static String requireIdentity(Path path, String expected, boolean directory)
            throws OracleException {
        String actual = directory ? directoryIdentity(path) : regularFileIdentity(path);
        if (!Objects.equals(expected, actual)) {
            throw rejection(RejectCode.FILE_IDENTITY_MISMATCH, "file identity rejected");
        }
        return actual;
    }

    private static String stableIdentity(BasicFileAttributes attributes) {
        if (attributes.fileKey() != null) {
            return "file-key:" + attributes.fileKey();
        }
        // Some Windows providers deliberately expose no fileKey. Creation time is the strongest
        // provider-neutral replacement detector still available through java.base; exact path,
        // type, byte length, deterministic content, marker, and directory membership are checked
        // independently at every restore and cleanup boundary.
        return "creation-time:" + attributes.creationTime();
    }

    private static void verifyMarker(Path marker) throws OracleException {
        try {
            byte[] actual = Files.readAllBytes(marker);
            if (!Arrays.equals(MARKER_BYTES, actual)) {
                throw rejection(RejectCode.MARKER_MISMATCH, "ownership marker rejected");
            }
        } catch (IOException exception) {
            throw rejection(RejectCode.MARKER_MISMATCH, "ownership marker rejected", exception);
        }
    }

    private static boolean isLowerHexSha256(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static String sha256(String text) {
        MessageDigest digest = newSha256();
        return HexFormat.of()
                .formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void cleanupIncompleteCreate(Path root) {
        if (root == null) {
            return;
        }
        try {
            Path part = root.resolve("part-00000.pcm16le");
            Path identityLink = root.resolve("part-00000.pcm16le" + IDENTITY_LINK_SUFFIX);
            Path marker = root.resolve(MARKER_NAME);
            Files.deleteIfExists(part);
            Files.deleteIfExists(identityLink);
            Files.deleteIfExists(marker);
            Files.deleteIfExists(root);
        } catch (IOException ignored) {
            // A failed create never broadens deletion beyond the four exact paths above.
        }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // The original checked failure remains authoritative.
        }
    }

    private static void deleteIfExistsQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The original checked failure remains authoritative.
        }
    }

    private static OracleException rejection(RejectCode code, String message) {
        return new OracleException(code, message);
    }

    private static OracleException rejection(
            RejectCode code, String message, Throwable cause) {
        return new OracleException(code, message, cause);
    }
}
