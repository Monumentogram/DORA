package com.monumentogram.dora.stage0.vad.p4;

import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.Accepted;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.Frame;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.FrameClass;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.Profile;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.ProfileAccepted;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.ReplayInput;
import com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplay.Snapshot;
import com.monumentogram.dora.stage0.vad.p4.VadPcmRotationOracle.ClosingOwnershipProof;
import com.monumentogram.dora.stage0.vad.p4.VadPcmRotationOracle.Completion;
import com.monumentogram.dora.stage0.vad.p4.VadPcmRotationOracle.OracleException;
import com.monumentogram.dora.stage0.vad.p4.VadPcmRotationOracle.OverlapProof;
import com.monumentogram.dora.stage0.vad.p4.VadPcmRotationOracle.PartProof;
import com.monumentogram.dora.stage0.vad.p4.VadPcmRotationOracle.PartSnapshot;
import com.monumentogram.dora.stage0.vad.p4.VadPcmRotationOracle.RejectCode;
import com.monumentogram.dora.stage0.vad.p4.VadPcmRotationOracle.Session;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public final class VadPcmRotationOracleTest {
    private static final String PASS_MARKER = "LOCAL_PASS vad-p4-synthetic-pcm-rotation";
    private static final long EXACT_TOTAL_FRAMES = 60_025L;
    private static final String EXPECTED_FRAME_ZERO_SHA256 =
            "b35bc378bf0093d20e31cad8a94271fff9424fac97087071eb252eaa92ef08b0";
    private static final String EXPECTED_EXACT_SUMMARY_SHA256 =
            "ef3d8bb065c181009a7126e2eabe8127d1a060657f266d31eeda6fb6dc68a554";
    private static final String EXPECTED_PART_ZERO_SHA256 =
            "3de2c5c8f1b69f79fbdaf4eb9d065e997f3a9f52d301aae5b80753f172c25f54";
    private static final String EXPECTED_PART_ONE_SHA256 =
            "4a3dfdc29b052b009a34c7650a0e3b233da27c1c57d94218b9614343f1bb3296";
    private static final String EXPECTED_PART_TWO_SHA256 =
            "fc9df6d06a821b56c55a99e5cda106682a9161c7ea923478da1967e9a70f7c6b";

    private VadPcmRotationOracleTest() {}

    public static void main(String[] ignored) throws Exception {
        constantsAndSyntheticPcmAreExact();
        Accepted exactPlan = replayPlan(Math.toIntExact(EXACT_TOTAL_FRAMES));
        planAndCreationBoundsFailClosed(exactPlan);
        Completion first = exactRotationAndOverlapProof(exactPlan);
        Completion second = exactRotationAndOverlapProof(exactPlan);
        checkEquals(first, second, "two complete byte-identical proofs");
        String exactSummary = VadPcmRotationOracle.canonicalSummary(first);
        checkEquals(
                EXPECTED_EXACT_SUMMARY_SHA256,
                sha256(exactSummary.getBytes(StandardCharsets.UTF_8)),
                "exact summary known answer");
        snapshotRestoreAndChunkingMatch(exactPlan, first);
        malformedSnapshotsFailClosed();
        corruptedAndTruncatedFilesFailClosed();
        replacedIdentityAndUnexpectedEntryFailClosed();
        markerTamperFailsClosed();
        inputOrderBoundsAndIncompleteFinishFailClosed();
        cleanupRefusesCorruptionThenRemovesAllOwnedArtifacts();
        immutableSnapshotsAndNoMutableStaticState();
        System.out.println(PASS_MARKER);
    }

    private static void constantsAndSyntheticPcmAreExact() {
        checkEquals(16_000, VadPcmRotationOracle.SAMPLE_RATE_HZ, "sample rate");
        checkEquals(1, VadPcmRotationOracle.CHANNEL_COUNT, "mono");
        checkEquals(16, VadPcmRotationOracle.BITS_PER_SAMPLE, "bits per sample");
        checkEquals(2, VadPcmRotationOracle.BYTES_PER_SAMPLE, "bytes per sample");
        checkEquals(320, VadPcmRotationOracle.SAMPLES_PER_FRAME, "samples per frame");
        checkEquals(20, VadPcmRotationOracle.FRAME_MILLIS, "frame duration");
        checkEquals(640, VadPcmRotationOracle.FRAME_BYTES, "frame bytes");
        checkEquals(30_000L, VadPcmRotationOracle.FRESH_CAP_FRAMES, "fresh cap frames");
        checkEquals(19_200_000L, VadPcmRotationOracle.FRESH_CAP_BYTES, "fresh cap bytes");
        checkEquals(75, VadPcmRotationOracle.OVERLAP_FRAMES, "overlap frames");
        checkEquals(48_000L, VadPcmRotationOracle.OVERLAP_BYTES, "overlap bytes");
        checkEquals(100, VadPcmRotationOracle.PRE_ROLL_FRAMES, "pre-roll frames");
        checkEquals(
                600_000L,
                VadPcmRotationOracle.FRESH_CAP_FRAMES
                        * VadPcmRotationOracle.FRAME_MILLIS,
                "fresh cap milliseconds");
        checkEquals(
                1_500L,
                (long) VadPcmRotationOracle.OVERLAP_FRAMES
                        * VadPcmRotationOracle.FRAME_MILLIS,
                "overlap milliseconds");

        byte[] first = VadPcmRotationOracle.syntheticFrame(0L);
        byte[] repeated = VadPcmRotationOracle.syntheticFrame(0L);
        byte[] second = VadPcmRotationOracle.syntheticFrame(1L);
        checkEquals(640, first.length, "generated frame length");
        checkArrayEquals(first, repeated, "same frame bytes");
        checkTrue(!Arrays.equals(first, second), "adjacent synthetic frames differ");
        checkEquals(
                EXPECTED_FRAME_ZERO_SHA256,
                sha256(first),
                "frame zero known answer");

        int littleEndianSample =
                Byte.toUnsignedInt(first[0]) | (Byte.toUnsignedInt(first[1]) << 8);
        short reconstructed = (short) littleEndianSample;
        checkEquals(first[0], (byte) (reconstructed & 0xff), "PCM16LE low byte");
        checkEquals(first[1], (byte) ((reconstructed >>> 8) & 0xff), "PCM16LE high byte");

        expectIllegalArgument(() -> VadPcmRotationOracle.syntheticFrame(-1L), "negative frame");
        expectIllegalArgument(
                () ->
                        VadPcmRotationOracle.syntheticFrame(
                                VadPcmRotationOracle.MAX_PROOF_FRESH_FRAMES),
                "upper frame bound");
    }

    private static void planAndCreationBoundsFailClosed(Accepted exactPlan) throws Exception {
        expectCode(
                () -> VadPcmRotationOracle.create(null, exactPlan),
                RejectCode.NULL_TEMP_PARENT,
                "null temporary parent");

        Path nullPlanParent = newTestParent();
        expectCode(
                () -> VadPcmRotationOracle.create(nullPlanParent, null),
                RejectCode.NULL_ACCEPTED_REPLAY,
                "null accepted replay");
        assertEmpty(nullPlanParent, "null plan did not create artifacts");
        Files.delete(nullPlanParent);

        Accepted missingCaps =
                new Accepted(
                        exactPlan.after(),
                        exactPlan.speechOnsets(),
                        exactPlan.semanticBoundaries(),
                        List.of());
        Path capParent = newTestParent();
        expectCode(
                () -> VadPcmRotationOracle.create(capParent, missingCaps),
                RejectCode.P3_CAP_COUNT_MISMATCH,
                "missing P3 caps");
        assertEmpty(capParent, "cap rejection did not create artifacts");
        Files.delete(capParent);

        Snapshot after = exactPlan.after();
        Snapshot beyondBound =
                new Snapshot(
                        after.schemaVersion(),
                        after.profilePreRollFrames(),
                        after.profileOverlapFrames(),
                        VadPcmRotationOracle.MAX_PROOF_FRESH_FRAMES + 1L,
                        after.phase(),
                        after.consecutiveSilenceFrames(),
                        after.semanticGroupsOpened(),
                        after.semanticBoundariesEmitted(),
                        after.preRollFramesAvailable());
        Accepted oversized =
                new Accepted(
                        beyondBound,
                        exactPlan.speechOnsets(),
                        exactPlan.semanticBoundaries(),
                        exactPlan.physicalCaps());
        Path boundParent = newTestParent();
        expectCode(
                () -> VadPcmRotationOracle.create(boundParent, oversized),
                RejectCode.TOTAL_FRAMES_OUT_OF_RANGE,
                "proof upper bound");
        assertEmpty(boundParent, "bound rejection did not create artifacts");
        Files.delete(boundParent);
    }

    private static Completion exactRotationAndOverlapProof(Accepted plan) throws Exception {
        Path parent = newTestParent();
        Session session = VadPcmRotationOracle.create(parent, plan);
        Path root = session.ownedDirectoryForTesting();
        checkEquals(parent, root.getParent(), "owned root parent");
        session.appendSynthetic(0L, Math.toIntExact(EXACT_TOTAL_FRAMES));
        Completion completion = session.finish();

        checkEquals(3, completion.parts().size(), "exact part count");
        checkEquals(2, completion.overlaps().size(), "exact overlap count");
        checkEquals(2, completion.closingOwnership().size(), "ownership proof count");
        checkEquals(
                EXACT_TOTAL_FRAMES * VadPcmRotationOracle.FRAME_BYTES,
                completion.totalFreshBytes(),
                "total fresh bytes");

        PartProof first = completion.parts().get(0);
        checkEquals(0L, first.partOrdinal(), "first ordinal");
        checkEquals(0L, first.storedStartFrameInclusive(), "first stored start");
        checkEquals(0L, first.freshStartFrameInclusive(), "first fresh start");
        checkEquals(30_000L, first.freshEndFrameExclusive(), "first fresh end");
        checkEquals(19_200_000L, first.storedByteCount(), "first stored bytes");
        checkEquals(19_200_000L, first.freshByteCount(), "first fresh bytes");
        checkEquals(0L, first.overlapPrefixByteCount(), "first prefix bytes");
        checkEquals(-1L, first.overlapOwnerPartOrdinal(), "first has no overlap owner");
        checkEquals(EXPECTED_PART_ZERO_SHA256, first.sha256(), "first part known answer");

        PartProof middle = completion.parts().get(1);
        checkEquals(1L, middle.partOrdinal(), "middle ordinal");
        checkEquals(29_925L, middle.storedStartFrameInclusive(), "middle stored start");
        checkEquals(30_000L, middle.freshStartFrameInclusive(), "middle fresh start");
        checkEquals(60_000L, middle.freshEndFrameExclusive(), "middle fresh end");
        checkEquals(19_248_000L, middle.storedByteCount(), "middle stored bytes");
        checkEquals(19_200_000L, middle.freshByteCount(), "middle exact 600 second cap");
        checkEquals(48_000L, middle.overlapPrefixByteCount(), "middle prefix bytes");
        checkEquals(0L, middle.overlapOwnerPartOrdinal(), "middle overlap owner");
        checkEquals(EXPECTED_PART_ONE_SHA256, middle.sha256(), "middle part known answer");

        PartProof last = completion.parts().get(2);
        checkEquals(59_925L, last.storedStartFrameInclusive(), "last stored start");
        checkEquals(60_000L, last.freshStartFrameInclusive(), "last fresh start");
        checkEquals(60_025L, last.freshEndFrameExclusive(), "last fresh end");
        checkEquals(64_000L, last.storedByteCount(), "last stored bytes");
        checkEquals(16_000L, last.freshByteCount(), "last fresh bytes");
        checkEquals(48_000L, last.overlapPrefixByteCount(), "last prefix bytes");
        checkEquals(1L, last.overlapOwnerPartOrdinal(), "last overlap owner");
        checkEquals(EXPECTED_PART_TWO_SHA256, last.sha256(), "last part known answer");

        for (int ordinal = 0; ordinal < completion.parts().size(); ordinal++) {
            checkEquals(
                    completion.parts().get(ordinal).storedByteCount(),
                    Files.size(session.partPathForTesting(ordinal)),
                    "physical part size " + ordinal);
        }
        for (int ordinal = 1; ordinal < completion.parts().size(); ordinal++) {
            PartProof closing = completion.parts().get(ordinal - 1);
            byte[] suffix =
                    readRange(
                            session.partPathForTesting(ordinal - 1L),
                            closing.storedByteCount() - VadPcmRotationOracle.OVERLAP_BYTES,
                            Math.toIntExact(VadPcmRotationOracle.OVERLAP_BYTES));
            byte[] prefix =
                    readRange(
                            session.partPathForTesting(ordinal),
                            0L,
                            Math.toIntExact(VadPcmRotationOracle.OVERLAP_BYTES));
            checkArrayEquals(suffix, prefix, "physical suffix/prefix equality " + ordinal);
        }
        for (int ordinal = 0; ordinal < completion.overlaps().size(); ordinal++) {
            OverlapProof overlap = completion.overlaps().get(ordinal);
            checkEquals((long) ordinal, overlap.closingPartOrdinal(), "overlap closing ordinal");
            checkEquals(ordinal + 1L, overlap.nextPartOrdinal(), "overlap next ordinal");
            checkEquals(48_000L, overlap.overlapByteCount(), "overlap byte count");
            checkTrue(overlap.suffixPrefixBytesEqual(), "overlap byte equality flag");
            checkEquals((long) ordinal, overlap.ownerPartOrdinal(), "overlap closing owner");
        }
        for (int ordinal = 0; ordinal < completion.closingOwnership().size(); ordinal++) {
            ClosingOwnershipProof ownership = completion.closingOwnership().get(ordinal);
            checkEquals(
                    (long) ordinal, ownership.closingPartOrdinal(), "ownership closing ordinal");
            checkEquals(16_000L, ownership.nonOverlapPrefixByteCount(), "25-frame prefix bytes");
            checkEquals(
                    (long) ordinal, ownership.ownerPartOrdinal(), "non-overlap closing owner");
            checkEquals(
                    25L,
                    ownership.nonOverlapPrefixEndFrameExclusive()
                            - ownership.preRollStartFrameInclusive(),
                    "non-overlap prefix frames");
            checkEquals(
                    ownership.overlapStartFrameInclusive(),
                    ownership.nonOverlapPrefixEndFrameExclusive(),
                    "non-overlap prefix ends at overlap");
        }

        session.cleanup();
        checkTrue(!Files.exists(root, LinkOption.NOFOLLOW_LINKS), "owned root removed");
        assertEmpty(parent, "parent empty after exact cleanup");
        Files.delete(parent);
        return completion;
    }

    private static void snapshotRestoreAndChunkingMatch(
            Accepted plan, Completion uninterrupted) throws Exception {
        Path parent = newTestParent();
        Session original = VadPcmRotationOracle.create(parent, plan);
        original.appendSynthetic(0L, 123);
        original.appendSynthetic(123L, 29_877);
        original.appendSynthetic(30_000L, 1_337);
        VadPcmRotationOracle.Snapshot beforeSuspend = original.snapshot();
        VadPcmRotationOracle.Snapshot suspended = original.suspend();
        checkEquals(beforeSuspend, suspended, "snapshot and suspend match");
        checkEquals(31_337L, suspended.nextFreshFrameIndex(), "snapshot next frame");
        checkEquals(2, suspended.parts().size(), "snapshot part count");
        checkEquals(19_200_000L, suspended.parts().get(0).storedByteCount(), "closed part size");
        checkEquals(903_680L, suspended.parts().get(1).storedByteCount(), "partial part size");
        expectCode(
                () -> original.appendSynthetic(31_337L, 1),
                RejectCode.SESSION_STATE_MISMATCH,
                "suspended session cannot append");

        Path root = original.ownedDirectoryForTesting();
        Session restored = VadPcmRotationOracle.restore(root, plan, suspended);
        long next = suspended.nextFreshFrameIndex();
        int[] chunks = {1, 74, 925, 4_096, 17, 2_003};
        int chunkIndex = 0;
        while (next < EXACT_TOTAL_FRAMES) {
            int count =
                    Math.toIntExact(
                            Math.min(
                                    (long) chunks[chunkIndex % chunks.length],
                                    EXACT_TOTAL_FRAMES - next));
            restored.appendSynthetic(next, count);
            next += count;
            chunkIndex++;
        }
        Completion chunked = restored.finish();
        checkEquals(uninterrupted, chunked, "snapshot/restore chunk completion");
        checkEquals(
                VadPcmRotationOracle.canonicalSummary(uninterrupted),
                VadPcmRotationOracle.canonicalSummary(chunked),
                "snapshot/restore canonical bytes");
        restored.cleanup();
        checkTrue(!Files.exists(root, LinkOption.NOFOLLOW_LINKS), "restored root removed");
        assertEmpty(parent, "parent empty after restored cleanup");
        Files.delete(parent);
    }

    private static void malformedSnapshotsFailClosed() throws Exception {
        Accepted plan = replayPlan(64);
        Path parent = newTestParent();
        Session session = VadPcmRotationOracle.create(parent, plan);
        session.appendSynthetic(0L, 64);
        VadPcmRotationOracle.Snapshot valid = session.suspend();
        Path root = session.ownedDirectoryForTesting();

        expectCode(
                () ->
                        VadPcmRotationOracle.restore(
                                root,
                                plan,
                                copySnapshot(
                                        valid,
                                        valid.schemaVersion() + 1,
                                        valid.syntheticSeed(),
                                        valid.nextFreshFrameIndex(),
                                        valid.parts())),
                RejectCode.SCHEMA_MISMATCH,
                "snapshot schema");
        expectCode(
                () ->
                        VadPcmRotationOracle.restore(
                                root,
                                plan,
                                copySnapshot(
                                        valid,
                                        valid.schemaVersion(),
                                        valid.syntheticSeed() + 1L,
                                        valid.nextFreshFrameIndex(),
                                        valid.parts())),
                RejectCode.SNAPSHOT_CONSTANT_MISMATCH,
                "snapshot seed");
        expectCode(
                () ->
                        VadPcmRotationOracle.restore(
                                root,
                                plan,
                                copySnapshot(
                                        valid,
                                        valid.schemaVersion(),
                                        valid.syntheticSeed(),
                                        valid.totalFreshFrames() + 1L,
                                        valid.parts())),
                RejectCode.SNAPSHOT_BOUNDS_MISMATCH,
                "snapshot next bound");

        PartSnapshot part = valid.parts().get(0);
        PartSnapshot wrongOrdinal =
                new PartSnapshot(
                        1L,
                        part.storedStartFrameInclusive(),
                        part.freshStartFrameInclusive(),
                        part.freshEndFrameExclusive(),
                        part.storedByteCount(),
                        part.freshByteCount(),
                        part.sha256(),
                        part.fileIdentity());
        expectCode(
                () ->
                        VadPcmRotationOracle.restore(
                                root,
                                plan,
                                copySnapshot(
                                        valid,
                                        valid.schemaVersion(),
                                        valid.syntheticSeed(),
                                        valid.nextFreshFrameIndex(),
                                        List.of(wrongOrdinal))),
                RejectCode.SNAPSHOT_PART_MISMATCH,
                "snapshot part order");

        PartSnapshot wrongDigest =
                new PartSnapshot(
                        part.partOrdinal(),
                        part.storedStartFrameInclusive(),
                        part.freshStartFrameInclusive(),
                        part.freshEndFrameExclusive(),
                        part.storedByteCount(),
                        part.freshByteCount(),
                        "0".repeat(64),
                        part.fileIdentity());
        expectCode(
                () ->
                        VadPcmRotationOracle.restore(
                                root,
                                plan,
                                copySnapshot(
                                        valid,
                                        valid.schemaVersion(),
                                        valid.syntheticSeed(),
                                        valid.nextFreshFrameIndex(),
                                        List.of(wrongDigest))),
                RejectCode.FILE_CONTENT_MISMATCH,
                "snapshot digest");

        Session restored = VadPcmRotationOracle.restore(root, plan, valid);
        Completion completion = restored.finish();
        checkEquals(64L, completion.totalFreshFrames(), "valid snapshot remains restorable");
        restored.cleanup();
        assertEmpty(parent, "parent empty after malformed snapshot cases");
        Files.delete(parent);
    }

    private static void corruptedAndTruncatedFilesFailClosed() throws Exception {
        Accepted plan = replayPlan(96);
        Path parent = newTestParent();
        Session session = VadPcmRotationOracle.create(parent, plan);
        session.appendSynthetic(0L, 96);
        VadPcmRotationOracle.Snapshot snapshot = session.suspend();
        Path root = session.ownedDirectoryForTesting();
        Path part = session.partPathForTesting(0L);
        byte[] original = Files.readAllBytes(part);

        try (FileChannel channel = FileChannel.open(part, StandardOpenOption.WRITE)) {
            channel.truncate(original.length - 1L);
        }
        expectCode(
                () -> VadPcmRotationOracle.restore(root, plan, snapshot),
                RejectCode.FILE_TRUNCATED,
                "truncated part");
        Files.write(
                part,
                original,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);

        byte[] corrupted = original.clone();
        corrupted[corrupted.length / 2] ^= 0x5a;
        Files.write(
                part,
                corrupted,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        expectCode(
                () -> VadPcmRotationOracle.restore(root, plan, snapshot),
                RejectCode.FILE_CONTENT_MISMATCH,
                "same-length corruption");
        Files.write(
                part,
                original,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);

        Session restored = VadPcmRotationOracle.restore(root, plan, snapshot);
        restored.finish();
        restored.cleanup();
        assertEmpty(parent, "parent empty after corruption cases");
        Files.delete(parent);
    }

    private static void replacedIdentityAndUnexpectedEntryFailClosed() throws Exception {
        Accepted plan = replayPlan(48);
        Path parent = newTestParent();
        Session session = VadPcmRotationOracle.create(parent, plan);
        session.appendSynthetic(0L, 48);
        VadPcmRotationOracle.Snapshot snapshot = session.suspend();
        Path root = session.ownedDirectoryForTesting();
        Path unexpected = root.resolve("unexpected.bin");
        Files.write(unexpected, new byte[0], StandardOpenOption.CREATE_NEW);
        expectCode(
                () -> VadPcmRotationOracle.restore(root, plan, snapshot),
                RejectCode.DIRECTORY_CONTENT_MISMATCH,
                "unexpected directory entry");
        Files.delete(unexpected);

        Path part = session.partPathForTesting(0L);
        byte[] original = Files.readAllBytes(part);
        Files.delete(part);
        Files.write(
                part,
                original,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        OracleException replacement =
                expectCode(
                        () -> VadPcmRotationOracle.restore(root, plan, snapshot),
                        RejectCode.FILE_IDENTITY_MISMATCH,
                        "replaced file identity");
        checkTrue(
                !replacement.getMessage().contains(root.toString()),
                "rejection message contains no local path");
        checkEquals((long) original.length, Files.size(part), "replacement retained after rejection");

        deleteKnownTestTree(
                parent,
                root,
                Set.of(
                        "part-00000.pcm16le",
                        "part-00000.pcm16le.identity-link",
                        "vad-p4-owned.marker"));
    }

    private static void markerTamperFailsClosed() throws Exception {
        Accepted plan = replayPlan(12);
        Path parent = newTestParent();
        Session session = VadPcmRotationOracle.create(parent, plan);
        session.appendSynthetic(0L, 12);
        VadPcmRotationOracle.Snapshot snapshot = session.suspend();
        Path root = session.ownedDirectoryForTesting();
        Path marker = session.markerPathForTesting();
        byte[] original = Files.readAllBytes(marker);
        byte[] corrupted = original.clone();
        corrupted[0] ^= 0x01;
        Files.write(
                marker,
                corrupted,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        expectCode(
                () -> VadPcmRotationOracle.restore(root, plan, snapshot),
                RejectCode.MARKER_MISMATCH,
                "marker content");
        Files.write(
                marker,
                original,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        Session restored = VadPcmRotationOracle.restore(root, plan, snapshot);
        restored.finish();
        restored.cleanup();
        assertEmpty(parent, "parent empty after marker case");
        Files.delete(parent);
    }

    private static void inputOrderBoundsAndIncompleteFinishFailClosed() throws Exception {
        Accepted plan = replayPlan(10);
        Path parent = newTestParent();
        Session session = VadPcmRotationOracle.create(parent, plan);
        VadPcmRotationOracle.Snapshot initial = session.snapshot();
        expectCode(
                () -> session.appendSynthetic(1L, 1),
                RejectCode.INPUT_ORDER_MISMATCH,
                "out-of-order input");
        expectCode(
                () -> session.appendSynthetic(0L, -1),
                RejectCode.INPUT_BOUNDS_MISMATCH,
                "negative count");
        expectCode(
                () -> session.appendSynthetic(0L, 11),
                RejectCode.INPUT_BOUNDS_MISMATCH,
                "past total");
        checkEquals(initial, session.snapshot(), "rejections leave session unchanged");

        session.appendSynthetic(0L, 5);
        expectCode(
                session::finish,
                RejectCode.INCOMPLETE_SESSION,
                "incomplete finish");
        expectCode(
                () -> session.appendSynthetic(4L, 1),
                RejectCode.INPUT_ORDER_MISMATCH,
                "midstream order");
        checkEquals(5L, session.snapshot().nextFreshFrameIndex(), "midstream state unchanged");
        session.appendSynthetic(5L, 5);
        session.finish();
        session.cleanup();
        assertEmpty(parent, "parent empty after input rejection cases");
        Files.delete(parent);
    }

    private static void cleanupRefusesCorruptionThenRemovesAllOwnedArtifacts()
            throws Exception {
        Accepted plan = replayPlan(20);
        Path parent = newTestParent();
        Session session = VadPcmRotationOracle.create(parent, plan);
        session.appendSynthetic(0L, 20);
        session.finish();
        Path root = session.ownedDirectoryForTesting();
        Path part = session.partPathForTesting(0L);
        byte[] original = Files.readAllBytes(part);
        byte[] corrupted = original.clone();
        corrupted[7] ^= 0x7f;
        Files.write(
                part,
                corrupted,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        expectCode(
                session::cleanup,
                RejectCode.FILE_CONTENT_MISMATCH,
                "cleanup corruption preflight");
        checkTrue(Files.exists(root, LinkOption.NOFOLLOW_LINKS), "tampered root retained");
        checkTrue(Files.exists(part, LinkOption.NOFOLLOW_LINKS), "tampered file retained");

        Files.write(
                part,
                original,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        session.cleanup();
        checkTrue(!Files.exists(root, LinkOption.NOFOLLOW_LINKS), "repaired root removed");
        assertEmpty(parent, "parent empty after cleanup recovery");
        Files.delete(parent);
    }

    private static void immutableSnapshotsAndNoMutableStaticState() throws Exception {
        Accepted plan = replayPlan(4);
        Path parent = newTestParent();
        Session session = VadPcmRotationOracle.create(parent, plan);
        session.appendSynthetic(0L, 4);
        VadPcmRotationOracle.Snapshot snapshot = session.snapshot();
        List<PartSnapshot> caller = new ArrayList<>(snapshot.parts());
        VadPcmRotationOracle.Snapshot copied =
                copySnapshot(
                        snapshot,
                        snapshot.schemaVersion(),
                        snapshot.syntheticSeed(),
                        snapshot.nextFreshFrameIndex(),
                        caller);
        caller.clear();
        checkEquals(1, copied.parts().size(), "snapshot defensively copies parts");
        boolean immutable = false;
        try {
            copied.parts().clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        checkTrue(immutable, "snapshot parts immutable");

        for (Field field : VadPcmRotationOracle.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                checkTrue(Modifier.isFinal(field.getModifiers()), "oracle static field final");
            }
        }
        for (Field field : Session.class.getDeclaredFields()) {
            checkTrue(!Modifier.isStatic(field.getModifiers()), "session has no static state");
        }
        session.finish();
        session.cleanup();
        assertEmpty(parent, "parent empty after immutability case");
        Files.delete(parent);
    }

    private static Accepted replayPlan(int frameCount) {
        Profile profile =
                requireType(
                                VadDeterministicReplay.createProfile(100, 75),
                                ProfileAccepted.class)
                        .profile();
        Frame[] frames = new Frame[frameCount];
        for (int index = 0; index < frames.length; index++) {
            frames[index] = new Frame(index, FrameClass.SILENCE);
        }
        return requireType(
                VadDeterministicReplay.replay(
                        profile,
                        VadDeterministicReplay.initial(profile),
                        new ReplayInput(frames)),
                Accepted.class);
    }

    private static VadPcmRotationOracle.Snapshot copySnapshot(
            VadPcmRotationOracle.Snapshot source,
            int schema,
            long seed,
            long next,
            List<PartSnapshot> parts) {
        return new VadPcmRotationOracle.Snapshot(
                schema,
                source.sampleRateHz(),
                source.channelCount(),
                source.bitsPerSample(),
                source.samplesPerFrame(),
                source.frameBytes(),
                source.freshCapFrames(),
                source.freshCapBytes(),
                source.overlapFrames(),
                source.overlapBytes(),
                seed,
                source.totalFreshFrames(),
                next,
                source.p3PlanSha256(),
                source.rootIdentity(),
                source.markerIdentity(),
                parts);
    }

    private static Path newTestParent() throws IOException {
        return Files.createTempDirectory("dora-vad-p4-test-parent-")
                .toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static void deleteKnownTestTree(
            Path parent, Path root, Set<String> expectedNames) throws IOException {
        checkEquals(parent, root.getParent(), "manual cleanup parent boundary");
        Set<String> actual = new java.util.HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                actual.add(entry.getFileName().toString());
            }
        }
        checkEquals(expectedNames, actual, "manual cleanup exact names");
        for (String name : expectedNames) {
            Path target = root.resolve(name).toAbsolutePath().normalize();
            checkEquals(root, target.getParent(), "manual cleanup exact child");
            Files.delete(target);
        }
        Files.delete(root);
        assertEmpty(parent, "manual cleanup parent empty");
        Files.delete(parent);
    }

    private static void assertEmpty(Path directory, String label) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            checkTrue(!stream.iterator().hasNext(), label);
        }
    }

    private static byte[] readRange(Path path, long offset, int length) throws IOException {
        byte[] result = new byte[length];
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.position(offset);
            ByteBuffer buffer = ByteBuffer.wrap(result);
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer);
                if (read < 0) {
                    throw new AssertionError("test range truncated");
                }
            }
        }
        return result;
    }

    private static OracleException expectCode(
            ThrowingAction action, RejectCode expected, String label) throws Exception {
        try {
            action.run();
        } catch (OracleException exception) {
            checkEquals(expected, exception.code(), label);
            return exception;
        }
        throw new AssertionError(label + ": expected " + expected);
    }

    private static void expectIllegalArgument(ThrowingAction action, String label) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        } catch (Exception unexpected) {
            throw new AssertionError(label, unexpected);
        }
        checkTrue(rejected, label);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
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

    private static void checkArrayEquals(byte[] expected, byte[] actual, String label) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label);
        }
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

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
