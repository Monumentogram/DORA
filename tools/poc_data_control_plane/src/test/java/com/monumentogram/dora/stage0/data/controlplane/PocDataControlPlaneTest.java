package com.monumentogram.dora.stage0.data.controlplane;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/** Dependency-free adversarial tests for the bounded Stage 0 synthetic control plane. */
public final class PocDataControlPlaneTest {
    private static final Path MANIFEST =
            Path.of(
                    "docs/evidence/poc-data-001/"
                            + "control-plane-synthetic-manifest-stage0-v0.1.json");
    private static final Path SCHEMA =
            Path.of("docs/stage0/poc-data-control-plane-stage0-v0.1.schema.json");
    private static int assertions;

    private PocDataControlPlaneTest() {}

    public static void main(String[] args) throws Exception {
        byte[] manifest = Files.readAllBytes(MANIFEST);
        byte[] schema = Files.readAllBytes(SCHEMA);

        scenario001ExactManifestAndSchema(manifest, schema);
        scenario002CanonicalAndEncoding(manifest);
        scenario003DuplicateKeysAndSyntax();
        scenario004BoundsAndDepth();
        scenario005AuthorityCannotElevate(manifest);
        scenario006CustodianFailsClosed(manifest);
        scenario007SensitiveFieldsRemainAbsent(manifest);
        scenario008LineageAndDeletionLedger(manifest);
        scenario009RbacPositiveMatrix();
        scenario010RbacNegativeMatrix();
        scenario011SyntheticDeletionLifecycle(manifest);
        scenario012DeletionIsIdempotent(manifest);
        scenario013RepeatedRunIsByteIdentical(manifest);
        scenario014SourceIsImmutableAndTempConfined(manifest);
        scenario015CliIsContentFree(manifest, schema);

        System.out.println(
                "LOCAL_PASS POC_DATA_CONTROL_PLANE_TESTS scenarios=15 assertions="
                        + assertions
                        + " readiness=NOT_READY overall=NOT_RUN collection=NOT_AUTHORIZED");
    }

    private static void scenario001ExactManifestAndSchema(byte[] manifest, byte[] schema) {
        PocDataControlPlane.ValidationReport report =
                PocDataControlPlane.validateManifest(manifest);
        equal(PocDataControlPlane.MANIFEST_SHA256, report.manifestSha256(), "manifest digest");
        equal("NOT_READY", report.readiness(), "readiness");
        equal("NOT_RUN", report.overallResult(), "overall result");
        equal("NOT_AUTHORIZED", report.collectionAuthorization(), "collection authorization");
        equal(
                PocDataControlPlane.SCHEMA_SHA256,
                PocDataControlPlane.validateSchemaProfile(schema),
                "schema digest");

        byte[] schemaDrift = schema.clone();
        schemaDrift[schemaDrift.length - 2] = (byte) ' ';
        expectFault(
                "E_SCHEMA_PROFILE_DRIFT",
                () -> PocDataControlPlane.validateSchemaProfile(schemaDrift));
    }

    private static void scenario002CanonicalAndEncoding(byte[] manifest) {
        check(manifest.length > 1 && manifest[manifest.length - 1] == '\n', "one final LF");
        check(manifest[manifest.length - 2] == '}', "no blank final line");
        expectFault(
                "E_JSON_NON_CANONICAL",
                () ->
                        PocDataControlPlane.validateManifest(
                                (" " + new String(manifest, StandardCharsets.UTF_8))
                                        .getBytes(StandardCharsets.UTF_8)));
        byte[] bom = new byte[manifest.length + 3];
        bom[0] = (byte) 0xef;
        bom[1] = (byte) 0xbb;
        bom[2] = (byte) 0xbf;
        System.arraycopy(manifest, 0, bom, 3, manifest.length);
        expectFault("E_JSON_ENCODING", () -> PocDataControlPlane.validateManifest(bom));
        expectFault(
                "E_JSON_ENCODING",
                () -> PocDataControlPlane.validateManifest(new byte[] {(byte) 0xc3, (byte) 0x28}));
    }

    private static void scenario003DuplicateKeysAndSyntax() {
        expectFault(
                "E_JSON_DUPLICATE_KEY",
                () ->
                        PocDataControlPlane.validateManifest(
                                "{\"a\":1,\"a\":2}\n".getBytes(StandardCharsets.UTF_8)));
        expectFault(
                "E_JSON_NUMBER",
                () ->
                        PocDataControlPlane.validateManifest(
                                "{\"a\":1.0}\n".getBytes(StandardCharsets.UTF_8)));
        expectFault(
                "E_JSON_SYNTAX",
                () ->
                        PocDataControlPlane.validateManifest(
                                "{\"a\":true,}\n".getBytes(StandardCharsets.UTF_8)));
    }

    private static void scenario004BoundsAndDepth() {
        byte[] oversized = new byte[PocDataControlPlane.MAX_INPUT_BYTES + 1];
        Arrays.fill(oversized, (byte) ' ');
        expectFault("E_INPUT_TOO_LARGE", () -> PocDataControlPlane.validateManifest(oversized));

        String deep = "[".repeat(PocDataControlPlane.MAX_JSON_DEPTH + 2)
                + "0"
                + "]".repeat(PocDataControlPlane.MAX_JSON_DEPTH + 2)
                + "\n";
        expectFault(
                "E_JSON_DEPTH",
                () -> PocDataControlPlane.validateManifest(deep.getBytes(StandardCharsets.UTF_8)));
    }

    private static void scenario005AuthorityCannotElevate(byte[] manifest) {
        expectFault(
                "E_VALUE",
                () ->
                        PocDataControlPlane.validateManifest(
                                replaceOnce(
                                        manifest,
                                        "\"realPeopleAllowed\":false",
                                        "\"realPeopleAllowed\":true")));
        expectFault(
                "E_VALUE",
                () ->
                        PocDataControlPlane.validateManifest(
                                replaceOnce(
                                        manifest,
                                        "\"trainingAllowed\":false,\"transientSyntheticSentinelAllowed\"",
                                        "\"trainingAllowed\":true,\"transientSyntheticSentinelAllowed\"")));
        expectFault(
                "E_VALUE",
                () ->
                        PocDataControlPlane.validateManifest(
                                replaceOnce(
                                        manifest,
                                        "\"pocDataReadyAllowed\":false",
                                        "\"pocDataReadyAllowed\":true")));
    }

    private static void scenario006CustodianFailsClosed(byte[] manifest) {
        expectFault(
                "E_VALUE",
                () ->
                        PocDataControlPlane.validateManifest(
                                replaceOnce(
                                        manifest,
                                        "\"assignment\":\"CUSTODIAN_UNASSIGNED\"",
                                        "\"assignment\":\"UNAPPROVED_PERSON\"")));
        expectFault(
                "E_VALUE",
                () ->
                        PocDataControlPlane.validateManifest(
                                replaceOnce(
                                        manifest,
                                        "\"realCollectionEnabled\":false",
                                        "\"realCollectionEnabled\":true")));
        check(
                !PocDataControlPlane.authorize("CUSTODIAN", "APPROVE_REAL_COLLECTION").allowed(),
                "unassigned custodian cannot approve collection");
    }

    private static void scenario007SensitiveFieldsRemainAbsent(byte[] manifest) {
        expectFault(
                "E_CONTENT_DIGEST_PRESENT",
                () ->
                        PocDataControlPlane.validateManifest(
                                replaceOnce(
                                        manifest,
                                        "\"contentSha256\":null",
                                        "\"contentSha256\":\"sha256:forbidden\"")));
        expectFault(
                "E_PRIVATE_LOCATOR_PRESENT",
                () ->
                        PocDataControlPlane.validateManifest(
                                replaceOnce(
                                        manifest,
                                        "\"evidenceLocator\":null",
                                        "\"evidenceLocator\":\"private://forbidden\"")));
        expectFault(
                "E_VALUE",
                () ->
                        PocDataControlPlane.validateManifest(
                                replaceOnce(
                                        manifest,
                                        "\"containsPersonalData\":false",
                                        "\"containsPersonalData\":true")));
    }

    private static void scenario008LineageAndDeletionLedger(byte[] manifest) {
        expectFault(
                "E_LINEAGE",
                () ->
                        PocDataControlPlane.validateManifest(
                                replaceOnce(
                                        manifest,
                                        "\"parentSampleId\":\"sample-1111111111111111\"",
                                        "\"parentSampleId\":\"sample-2222222222222222\"")));
        expectFault(
                "E_VALUE",
                () ->
                        PocDataControlPlane.validateManifest(
                                replaceOnce(
                                        manifest,
                                        "\"outcome\":\"DELETED\"",
                                        "\"outcome\":\"ACTIVE\"")));
        expectFault(
                "E_VALUE",
                () ->
                        PocDataControlPlane.validateManifest(
                                replaceOnce(
                                        manifest,
                                        "\"controlledStorageDryRunStatus\":\"NOT_RUN\"",
                                        "\"controlledStorageDryRunStatus\":\"PASS\"")));
    }

    private static void scenario009RbacPositiveMatrix() {
        allow("SYNTHETIC_DRY_RUN_OPERATOR", "READ_PUBLIC_MANIFEST");
        allow("SECURITY_AUDITOR", "READ_PUBLIC_MANIFEST");
        allow("EVALUATOR", "READ_PUBLIC_MANIFEST");
        allow("SYNTHETIC_DRY_RUN_OPERATOR", "VALIDATE_MANIFEST");
        allow("SYNTHETIC_DRY_RUN_OPERATOR", "CREATE_SYNTHETIC_TEMP");
        allow("SYNTHETIC_DRY_RUN_OPERATOR", "DELETE_SYNTHETIC_TEMP");
        allow("SYNTHETIC_DRY_RUN_OPERATOR", "READ_CONTENT_FREE_EVIDENCE");
        allow("SECURITY_AUDITOR", "READ_CONTENT_FREE_EVIDENCE");
    }

    private static void scenario010RbacNegativeMatrix() {
        for (String action : PocDataControlPlane.ACTIONS.subList(5, PocDataControlPlane.ACTIONS.size())) {
            for (String role : PocDataControlPlane.ROLES) {
                deny(role, action);
            }
        }
        deny("EVALUATOR", "VALIDATE_MANIFEST");
        deny("COLLECTOR", "READ_PUBLIC_MANIFEST");
        deny("ANNOTATOR", "CREATE_SYNTHETIC_TEMP");
        equal(
                "E_UNKNOWN_ROLE",
                PocDataControlPlane.authorize("UNKNOWN", "READ_PUBLIC_MANIFEST").code(),
                "unknown role code");
        equal(
                "E_UNKNOWN_ACTION",
                PocDataControlPlane.authorize("SECURITY_AUDITOR", "UNKNOWN").code(),
                "unknown action code");
    }

    private static void scenario011SyntheticDeletionLifecycle(byte[] manifest) throws IOException {
        Path root = Files.createTempDirectory("dora-data-cp-test-lifecycle-");
        try {
            PocDataControlPlane.DryRunReport report = PocDataControlPlane.dryRun(manifest, root);
            check(report.sentinelDeleted(), "sentinel deleted");
            check(report.deletionIdempotent(), "deletion idempotent");
            try (var entries = Files.list(root)) {
                check(entries.findAny().isEmpty(), "temporary root empty after dry-run");
            }
        } finally {
            Files.deleteIfExists(root);
        }
    }

    private static void scenario012DeletionIsIdempotent(byte[] manifest) throws IOException {
        Path root = Files.createTempDirectory("dora-data-cp-test-repeat-delete-");
        try {
            PocDataControlPlane.DryRunReport first = PocDataControlPlane.dryRun(manifest, root);
            PocDataControlPlane.DryRunReport second = PocDataControlPlane.dryRun(manifest, root);
            check(first.deletionIdempotent() && second.deletionIdempotent(), "repeat deletion idempotent");
            check(first.sentinelDeleted() && second.sentinelDeleted(), "repeat sentinel absence");
        } finally {
            Files.deleteIfExists(root);
        }
    }

    private static void scenario013RepeatedRunIsByteIdentical(byte[] manifest) throws IOException {
        Path firstRoot = Files.createTempDirectory("dora-data-cp-test-determinism-a-");
        Path secondRoot = Files.createTempDirectory("dora-data-cp-test-determinism-b-");
        try {
            byte[] first = PocDataControlPlane.dryRun(manifest, firstRoot).canonicalBytes();
            byte[] second = PocDataControlPlane.dryRun(manifest, secondRoot).canonicalBytes();
            check(Arrays.equals(first, second), "dry-run report bytes identical");
            check(first[first.length - 1] == '\n', "dry-run report canonical LF");
        } finally {
            Files.deleteIfExists(firstRoot);
            Files.deleteIfExists(secondRoot);
        }
    }

    private static void scenario014SourceIsImmutableAndTempConfined(byte[] manifest) throws IOException {
        byte[] before = manifest.clone();
        Path root = Files.createTempDirectory("dora-data-cp-test-confined-");
        try {
            PocDataControlPlane.DryRunReport report = PocDataControlPlane.dryRun(manifest, root);
            check(report.sourceUnchanged(), "report says source unchanged");
            check(Arrays.equals(before, manifest), "caller bytes unchanged");
            equal(sha256(before), report.manifestSha256(), "source digest unchanged");

            Path occupied = root.resolve("unrelated-user-sentinel.txt");
            Files.writeString(occupied, "KEEP", StandardCharsets.US_ASCII);
            expectFault("E_TEMP_ROOT_NOT_EMPTY", () -> PocDataControlPlane.dryRun(manifest, root));
            equal("KEEP", Files.readString(occupied, StandardCharsets.US_ASCII), "unrelated file preserved");
            Files.delete(occupied);
        } finally {
            Files.deleteIfExists(root);
        }
    }

    private static void scenario015CliIsContentFree(byte[] manifest, byte[] schema) throws IOException {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
                PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            int validateExit =
                    PocDataControlPlane.runCli(
                            new String[] {"validate", MANIFEST.toString(), SCHEMA.toString()}, out, err);
            equal(0, validateExit, "validate CLI exit");
            String validateOutput = outBytes.toString(StandardCharsets.UTF_8);
            check(validateOutput.startsWith("LOCAL_PASS POC_DATA_CONTROL_PLANE_SYNTHETIC_ONLY"), "validate marker");
            check(!validateOutput.contains(MANIFEST.toString()), "no manifest path in output");
            check(errBytes.size() == 0, "no validate stderr");

            outBytes.reset();
            int dryRunExit =
                    PocDataControlPlane.runCli(
                            new String[] {"dry-run", MANIFEST.toString(), SCHEMA.toString()}, out, err);
            equal(0, dryRunExit, "dry-run CLI exit");
            String dryRunOutput = outBytes.toString(StandardCharsets.UTF_8);
            check(dryRunOutput.contains("scenarios=15"), "dry-run scenario count");
            check(dryRunOutput.contains("collection=NOT_AUTHORIZED"), "dry-run collection status");
            check(!dryRunOutput.contains("Temp"), "no temp path in output");

            Path invalid = Files.createTempFile("dora-data-cp-invalid-", ".json");
            try {
                Files.write(invalid, "{\"secret-marker\":true,\"secret-marker\":false}\n".getBytes(StandardCharsets.UTF_8));
                outBytes.reset();
                errBytes.reset();
                int invalidExit =
                        PocDataControlPlane.runCli(
                                new String[] {"validate", invalid.toString(), SCHEMA.toString()}, out, err);
                equal(1, invalidExit, "invalid CLI exit");
                String diagnostic = errBytes.toString(StandardCharsets.UTF_8);
                check(diagnostic.startsWith("LOCAL_FAIL E_JSON_DUPLICATE_KEY /"), "content-free fault code");
                check(!diagnostic.contains("secret-marker"), "input content not echoed");
                check(!diagnostic.contains(invalid.toString()), "invalid path not echoed");
            } finally {
                Files.deleteIfExists(invalid);
            }
        }

        check(Arrays.equals(manifest, Files.readAllBytes(MANIFEST)), "CLI source file unchanged");
        check(Arrays.equals(schema, Files.readAllBytes(SCHEMA)), "CLI schema file unchanged");
    }

    private static byte[] replaceOnce(byte[] input, String expected, String replacement) {
        String text = new String(input, StandardCharsets.UTF_8);
        int start = text.indexOf(expected);
        check(start >= 0, "mutation source exists: " + expected);
        String changed = text.substring(0, start) + replacement + text.substring(start + expected.length());
        return changed.getBytes(StandardCharsets.UTF_8);
    }

    private static void allow(String role, String action) {
        PocDataControlPlane.AccessDecision decision = PocDataControlPlane.authorize(role, action);
        check(decision.allowed(), "expected allow " + role + " " + action);
        equal("ALLOW_SYNTHETIC_ONLY", decision.code(), "allow code");
    }

    private static void deny(String role, String action) {
        PocDataControlPlane.AccessDecision decision = PocDataControlPlane.authorize(role, action);
        check(!decision.allowed(), "expected deny " + role + " " + action);
    }

    private static void expectFault(String code, ThrowingRunnable action) {
        try {
            action.run();
        } catch (PocDataControlPlane.ControlPlaneFault fault) {
            equal(code, fault.code(), "fault code");
            check(fault.pointer().startsWith("/"), "content-free JSON pointer");
            return;
        } catch (Exception error) {
            throw new AssertionError("unexpected checked exception", error);
        }
        throw new AssertionError("expected fault " + code);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
