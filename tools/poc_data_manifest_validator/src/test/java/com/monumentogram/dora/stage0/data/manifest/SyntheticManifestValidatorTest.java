package com.monumentogram.dora.stage0.data.manifest;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Adversarial dependency-free tests. All manifest fixtures exist only in memory or OS temp. */
public final class SyntheticManifestValidatorTest {
    private static final String EXPECTED_PROFILE_ID =
            "poc-data-synthetic-public-projection-stage0-v0.1";
    private static final String EXPECTED_PASS_MARKER =
            "LOCAL_PASS poc-data-synthetic-manifest-validator";
    private static final String EXPECTED_SCHEMA_SHA256 =
            "d487e8062d10535ec64cb44e8ebc363a4d1dca337db9bf2a8dca23ae0518fffb";
    private static final List<String> EXPECTED_POC_IDS =
            List.of(
                    "POC-CAPTURE-001",
                    "POC-RECOVERY-001",
                    "POC-VAD-001",
                    "POC-ASR-001",
                    "POC-DIAR-001",
                    "POC-DECISION-001",
                    "POC-OFFLINE-001",
                    "POC-VPN-001",
                    "POC-BATTERY-001",
                    "POC-SEARCH-001");
    private static final List<String> EXPECTED_USES =
            List.of(
                    "CAPTURE_SAMPLE_INTEGRITY_AND_RECOVERY_TESTING",
                    "VAD_SILENCE_AND_MAX_CAP_EVALUATION",
                    "ASR_RU_EN_MIXED_EVALUATION",
                    "DIARIZATION_AND_CORRECTION_LOAD_EVALUATION",
                    "DECISION_TASK_SOURCE_GROUNDING_EVALUATION",
                    "OFFLINE_VPN_TRANSPORT_TESTING",
                    "BATTERY_THERMAL_CALIBRATION",
                    "SEARCH_SCALE_TESTING");
    private static final List<String> EXPECTED_EXCLUDED =
            List.of(
                    "MODEL_TRAINING",
                    "FINE_TUNING",
                    "DISTILLATION",
                    "EMBEDDING_ENROLLMENT",
                    "UNDECLARED_HUMAN_REVIEW",
                    "PROVIDER_QUALITY_IMPROVEMENT",
                    "PUBLIC_RELEASE_REDISTRIBUTION_OR_PUBLICATION",
                    "MARKETING_OR_DEMO",
                    "BIOMETRIC_IDENTITY",
                    "CROSS_CONVERSATION_SPEAKER_RECOGNITION",
                    "TRAINING_EXAMPLE_SELECTION_OUTSIDE_NAMED_POC",
                    "HARD_EXAMPLE_RETENTION_BEYOND_APPROVED_PERIOD",
                    "CROSS_PROJECT_OR_COMPANY_REUSE");
    private static final List<String> EXPECTED_SCOPES =
            List.of(
                    "RAW_AND_TRANSFORMED_COPIES",
                    "TRANSCRIPTS_ANNOTATIONS_FEATURES_NOTES",
                    "CACHES_EXPORTS_DOWNLOADS",
                    "SPLIT_INDEX_AND_PRIVATE_MANIFESTS",
                    "CONTROLLED_BACKUPS",
                    "PROVIDER_COPIES",
                    "PARTICIPANT_MAPPING_AND_ACCESS_GRANTS",
                    "DERIVED_VARIANTS");
    private static final List<String> EXPECTED_LIMITATIONS =
            List.of(
                    "NON_NORMATIVE_MINIMUM_PROFILE",
                    "NO_DURABLE_OR_GOVERNED_DATASET_OR_CORPUS_MANIFEST_CREATED",
                    "SYNTHETIC_METADATA_ONLY",
                    "NO_SAMPLE_BYTES",
                    "NO_COLLECTION_STORAGE_ACCESS_OR_CONSENT_EVIDENCE",
                    "NO_POC_PASS_OR_PRODUCTION_ADMISSION");
    private static final String TEST_MARKER =
            "LOCAL_PASS poc-data-synthetic-manifest-validator-tests";

    private SyntheticManifestValidatorTest() {}

    public static void main(String[] args) {
        List<TestCase> tests =
                List.of(
                        new TestCase("profile-constants", SyntheticManifestValidatorTest::profileConstants),
                        new TestCase("positive-profiles", SyntheticManifestValidatorTest::positiveProfiles),
                        new TestCase("json-boundary", SyntheticManifestValidatorTest::jsonBoundary),
                        new TestCase("shape-scalars", SyntheticManifestValidatorTest::shapeAndScalars),
                        new TestCase("purpose-rights", SyntheticManifestValidatorTest::purposeAndRights),
                        new TestCase("lineage-deletion", SyntheticManifestValidatorTest::lineageAndDeletion),
                        new TestCase("cli-read-only", SyntheticManifestValidatorTest::cliReadOnly));
        for (TestCase test : tests) {
            try {
                test.body().run();
            } catch (RuntimeException | AssertionError error) {
                System.err.println("LOCAL_FAIL " + test.name());
                System.exit(1);
            }
        }
        System.out.println(TEST_MARKER);
    }

    private static void profileConstants() {
        check(SyntheticManifestValidator.MAX_INPUT_BYTES == 524_288, "byte-cap");
        check(SyntheticManifestValidator.MAX_JSON_DEPTH == 32, "depth-cap");
        check(SyntheticManifestValidator.PROFILE_ID.equals(EXPECTED_PROFILE_ID), "profile-id");
        check(SyntheticManifestValidator.PASS_MARKER.equals(EXPECTED_PASS_MARKER), "pass-marker");
        check(SyntheticManifestValidator.POC_IDS.equals(EXPECTED_POC_IDS), "poc-catalogue");
        check(SyntheticManifestValidator.ALLOWED_USES.equals(EXPECTED_USES), "use-catalogue");
        check(
                SyntheticManifestValidator.EXCLUDED_USES.equals(EXPECTED_EXCLUDED),
                "excluded-catalogue");
        check(
                SyntheticManifestValidator.DELETION_SCOPES.equals(EXPECTED_SCOPES),
                "deletion-catalogue");
        check(
                SyntheticManifestValidator.LIMITATIONS.equals(EXPECTED_LIMITATIONS),
                "limitation-catalogue");
        try {
            byte[] schema =
                    Files.readAllBytes(
                            Path.of(
                                    "docs",
                                    "stage0",
                                    "poc-data-synthetic-public-projection-stage0-v0.1.schema.json"));
            check(sha256(schema).equals(EXPECTED_SCHEMA_SHA256), "schema-byte-pin");
            SyntheticManifestValidator.validateProfileSchema(schema);
        } catch (java.io.IOException error) {
            throw new AssertionError("schema-read");
        }
    }

    private static void positiveProfiles() {
        expectValid(fixture());
        expectValid(generatedTextFixture());
        for (int index = 0; index < EXPECTED_POC_IDS.size(); index++) {
            Map<String, Object> value = fixture();
            Map<String, Object> purpose = object(value.get("purpose"));
            purpose.put("pocIds", mutableList(EXPECTED_POC_IDS.get(index)));
            purpose.put("allowedUses", mutableList(mappedUse(EXPECTED_POC_IDS.get(index))));
            expectValid(value);
        }
        Map<String, Object> combined = fixture();
        Map<String, Object> purpose = object(combined.get("purpose"));
        purpose.put("pocIds", mutableList("POC-OFFLINE-001", "POC-VPN-001"));
        purpose.put("allowedUses", mutableList("OFFLINE_VPN_TRANSPORT_TESTING"));
        expectValid(combined);
        for (long seed : new long[] {0L, Long.MAX_VALUE}) {
            Map<String, Object> value = fixture();
            object(value.get("origin")).put("seed", seed);
            expectValid(value);
        }
    }

    private static void jsonBoundary() {
        byte[] valid = SyntheticManifestValidator.canonicalBytes(fixture());
        expectRawFault(concat(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf}, valid), "E_JSON_ENCODING");
        expectRawFault(new byte[] {(byte) 0xff, (byte) 0xfe, '{', 0, '}', 0}, "E_JSON_ENCODING");
        expectRawFault(new byte[] {'{', '"', 'x', '"', ':', '"', (byte) 0xff, '"', '}', '\n'}, "E_JSON_ENCODING");
        expectRawFault("{\"a\": 1, \"a\": 2}\n".getBytes(StandardCharsets.UTF_8), "E_JSON_DUPLICATE_KEY");
        expectRawFault(
                "{\"sampleId\": 1, \"\\u0073ampleId\": 2}\n"
                        .getBytes(StandardCharsets.UTF_8),
                "E_JSON_DUPLICATE_KEY");
        expectRawFault("{} trailing\n".getBytes(StandardCharsets.UTF_8), "E_JSON_SYNTAX");
        expectRawFault(new byte[0], "E_JSON_SYNTAX");
        expectRawFault(
                new String(valid, StandardCharsets.UTF_8)
                        .replace("\n", "\r\n")
                        .getBytes(StandardCharsets.UTF_8),
                "E_JSON_NON_CANONICAL");
        expectRawFault(Arrays.copyOf(valid, valid.length - 1), "E_JSON_NON_CANONICAL");
        expectRawFault(concat(valid, new byte[] {'\n'}), "E_JSON_NON_CANONICAL");
        expectRawFault(new byte[SyntheticManifestValidator.MAX_INPUT_BYTES + 1], "E_INPUT_TOO_LARGE");
        Object deep = null;
        for (int index = 0; index < SyntheticManifestValidator.MAX_JSON_DEPTH + 2; index++) {
            deep = mutableList(deep);
        }
        expectRawFault(SyntheticManifestValidator.canonicalBytes(deep), "E_JSON_TOO_DEEP");
        expectRawFault("{\"x\": \"\\ud800\"}\n".getBytes(StandardCharsets.UTF_8), "E_JSON_UNICODE");
        expectRawFaultAt("{\"x\":1.0}\n".getBytes(StandardCharsets.UTF_8), "E_JSON_SYNTAX", "/");
        expectRawFaultAt("{\"x\":1e2}\n".getBytes(StandardCharsets.UTF_8), "E_JSON_SYNTAX", "/");
        expectRawFaultAt("{\"x\":01}\n".getBytes(StandardCharsets.UTF_8), "E_JSON_SYNTAX", "/");
        expectRawFaultAt(
                "{\"x\":9223372036854775808}\n".getBytes(StandardCharsets.UTF_8),
                "E_JSON_SYNTAX",
                "/");
        expectRawFaultAt(
                "{\n  \"schemaVersion\": -0\n}\n".getBytes(StandardCharsets.UTF_8),
                "E_JSON_NON_CANONICAL",
                "/");
    }

    private static void shapeAndScalars() {
        for (String field : new ArrayList<>(fixture().keySet())) {
            expectFault(changed(value -> value.remove(field)), "E_FIELD_MISSING");
        }
        expectFault(changed(value -> value.put("unexpected", Boolean.FALSE)), "E_FIELD_UNKNOWN");
        expectFault(
                changed(value -> object(value.get("purpose")).put("unexpected", Boolean.FALSE)),
                "E_FIELD_UNKNOWN");
        expectFault(
                changed(value -> object(value.get("origin")).put("unexpected", Boolean.FALSE)),
                "E_FIELD_UNKNOWN");
        expectFault(
                changed(value -> sampleAt(value, 0).put("audio", "x")), "E_FIELD_UNKNOWN");
        expectFault(changed(value -> value.put("schemaVersion", Boolean.TRUE)), "E_FIELD_TYPE");
        expectFaultAt(changed(value -> value.put("schemaVersion", map())), "E_FIELD_TYPE", "/schemaVersion");
        expectFaultAt(changed(value -> value.put("trainingAllowed", mutableList())), "E_FIELD_TYPE", "/trainingAllowed");
        expectFaultAt(
                changed(value -> object(value.get("purpose")).put("pocIds", null)),
                "E_FIELD_TYPE",
                "/purpose/pocIds");
        expectFaultAt(
                changed(
                        value ->
                                object(value.get("purpose"))
                                        .put("pocIds", mutableList(Boolean.TRUE))),
                "E_FIELD_TYPE",
                "/purpose/pocIds");
        expectFault(changed(value -> value.put("trainingAllowed", 0L)), "E_FIELD_TYPE");
        expectFault(
                changed(value -> object(value.get("origin")).put("seed", Boolean.FALSE)),
                "E_FIELD_TYPE");
        expectFault(changed(value -> value.put("manifestId", "manifest-person-name")), "E_ID_FORMAT");
        expectFault(changed(value -> value.put("datasetId", "dataset-ABCDEF0011223344")), "E_ID_FORMAT");
        expectFault(changed(value -> value.put("version", "01.0.0")), "E_VERSION_FORMAT");
        expectFault(changed(value -> value.put("termsDigest", "sha256:" + "0".repeat(64))), "E_DIGEST_FORMAT");
        expectFault(
                changed(
                        value ->
                                object(value.get("origin"))
                                        .put("sourceReference", "docs/../private")),
                "E_REPO_PATH");
    }

    private static void purposeAndRights() {
        expectFault(
                changed(value -> object(value.get("purpose")).put("pocIds", new ArrayList<>())),
                "E_ARRAY_EMPTY");
        expectFault(
                changed(
                        value ->
                                object(value.get("purpose"))
                                        .put(
                                                "pocIds",
                                                mutableList("POC-VAD-001", "POC-VAD-001"))),
                "E_ARRAY_DUPLICATE");
        expectFaultAt(
                changed(
                        value ->
                                object(value.get("purpose"))
                                        .put("pocIds", mutableList("POC-UNKNOWN-001"))),
                "E_CATALOG_VALUE",
                "/purpose/pocIds");
        expectFaultAt(
                changed(
                        value ->
                                object(value.get("purpose"))
                                        .put(
                                                "pocIds",
                                                mutableList(
                                                        "POC-RECOVERY-001",
                                                        "POC-CAPTURE-001"))),
                "E_ARRAY_ORDER",
                "/purpose/pocIds");
        expectFaultAt(
                changed(
                        value ->
                                java.util.Collections.reverse(
                                        array(
                                                object(value.get("purpose"))
                                                        .get("excludedUses")))),
                "E_ARRAY_ORDER",
                "/purpose/excludedUses");
        expectFault(
                changed(
                        value ->
                                object(value.get("purpose"))
                                        .put(
                                                "allowedUses",
                                                mutableList("ASR_RU_EN_MIXED_EVALUATION"))),
                "E_PURPOSE_MAPPING");
        expectFault(
                changed(
                        value ->
                                array(object(value.get("purpose")).get("excludedUses"))
                                        .remove(EXPECTED_EXCLUDED.size() - 1)),
                "E_CATALOG_INCOMPLETE");
        expectFault(changed(value -> value.put("dataClass", "PURPOSE_RECORDED")), "E_CATALOG_VALUE");
        expectFault(changed(value -> value.put("dataClass", "REAL_MEETING")), "E_CATALOG_VALUE");
        expectFault(changed(value -> value.put("trainingAllowed", Boolean.TRUE)), "E_TRAINING_FORBIDDEN");
        expectFault(
                changed(value -> value.put("publicRedistributionAllowed", Boolean.TRUE)),
                "E_REDISTRIBUTION_FORBIDDEN");
        expectFault(
                changed(value -> value.put("consentReference", "opaque-private")),
                "E_CONSENT_REFERENCE");
        expectFault(
                changed(value -> sampleAt(value, 0).put("languageSlice", "ru")),
                "E_CLASS_SLICE_MISMATCH");
        expectFault(
                changed(value -> sampleAt(value, 0).put("contentSha256", "sha256:" + "2a".repeat(32))),
                "E_PUBLIC_DIGEST");
        expectFault(
                changed(value -> sampleAt(value, 0).put("evidenceLocator", "private")),
                "E_PRIVATE_LOCATOR");
        expectFault(
                changed(
                        value ->
                                object(value.get("publicProjection"))
                                        .put("containsPersonalData", Boolean.TRUE)),
                "E_PUBLIC_PROJECTION");
    }

    private static void lineageAndDeletion() {
        expectFault(
                changed(
                        value ->
                                sampleAt(value, 1)
                                        .put("parentSampleId", "sample-9999999999999999")),
                "E_PARENT_UNKNOWN");
        expectFault(
                changed(
                        value ->
                                sampleAt(value, 1)
                                        .put("parentSampleId", "sample-2222222222222222")),
                "E_PARENT_CYCLE");
        expectFaultAt(
                changed(
                        value -> {
                            sampleAt(value, 0)
                                    .put("parentSampleId", "sample-2222222222222222");
                            sampleAt(value, 0)
                                    .put("expiresAt", "2026-12-30T00:00:00Z");
                            sampleAt(value, 1)
                                    .put("parentSampleId", "sample-1111111111111111");
                        }),
                "E_PARENT_CYCLE",
                "/samples");
        expectFault(
                changed(value -> sampleAt(value, 1).put("split", "test")),
                "E_PARENT_SPLIT_MISMATCH");
        expectFaultAt(
                changed(
                        value ->
                                sampleAt(value, 1)
                                        .put("createdAt", "2026-07-31T23:59:59Z")),
                "E_PARENT_RESTRICTIONS_WIDENED",
                "/samples");
        expectFaultAt(
                changed(
                        value ->
                                sampleAt(value, 1)
                                        .put(
                                                "accessRoles",
                                                mutableList(
                                                        "generator",
                                                        "qa-evaluator",
                                                        "security-auditor"))),
                "E_PARENT_RESTRICTIONS_WIDENED",
                "/samples");
        expectFault(
                changed(
                        value ->
                                sampleAt(value, 1)
                                        .put("expiresAt", "2027-01-01T00:00:00Z")),
                "E_PARENT_RESTRICTIONS_WIDENED");
        expectFault(
                changed(value -> java.util.Collections.reverse(array(value.get("samples")))),
                "E_ARRAY_ORDER");
        expectFault(
                changed(value -> array(value.get("samples")).add(deepCopy(sampleAt(value, 0)))),
                "E_SAMPLE_ID_DUPLICATE");
        expectFault(
                changed(
                        value ->
                                deletionAt(value, 0)
                                        .put("sampleId", "sample-9999999999999999")),
                "E_DELETION_SAMPLE_UNKNOWN");
        expectFault(
                changed(value -> array(value.get("deletionLedger")).remove(1)),
                "E_DELETION_LEDGER_MISSING");
        expectFaultAt(
                changed(
                        value ->
                                deletionAt(value, 1)
                                        .put("eventId", "delete-3333333333333333")),
                "E_EVENT_ID_DUPLICATE",
                "/deletionLedger");
        expectFaultAt(
                changed(
                        value ->
                                deletionAt(value, 1)
                                        .put("sampleId", "sample-3333333333333333")),
                "E_DELETION_EVENT_DUPLICATE",
                "/deletionLedger");
        expectFaultAt(
                changed(value -> java.util.Collections.reverse(array(value.get("deletionLedger")))),
                "E_ARRAY_ORDER",
                "/deletionLedger");
        expectFaultAt(
                changed(value -> deletionAt(value, 0).put("trigger", "ACTUAL_DELETE")),
                "E_DELETION_VALUE",
                "/deletionLedger/0");
        expectFaultAt(
                changed(value -> deletionAt(value, 0).put("outcome", "PENDING")),
                "E_DELETION_VALUE",
                "/deletionLedger/0");
        expectFault(
                changed(
                        value ->
                                array(deletionAt(value, 0).get("affectedScopes")).remove(7)),
                "E_CATALOG_INCOMPLETE");
        expectFault(
                changed(
                        value ->
                                array(deletionAt(value, 0).get("unresolvedFailures"))
                                        .add("failure")),
                "E_DELETION_UNRESOLVED");
        expectFault(
                changed(
                        value ->
                                deletionAt(value, 0)
                                        .put("completedAt", "2026-07-31T00:00:00Z")),
                "E_TIMESTAMP_ORDER");
        expectFault(
                changed(value -> sampleAt(value, 2).put("deletionState", "active")),
                "E_DELETION_STATE_MISMATCH");
        expectFault(
                changed(value -> sampleAt(value, 3).put("deletionState", "active")),
                "E_DELETION_DESCENDANT_ACTIVE");
        expectFault(
                changed(value -> sampleAt(value, 3).put("deletionState", "expired")),
                "E_DELETION_DESCENDANT_ACTIVE");
        expectValid(
                changed(
                        value -> {
                            deletionAt(value, 0).put("completedAt", "2026-09-01T00:00:00Z");
                            deletionAt(value, 0).put("backupExpiresAt", "2026-09-01T00:00:00Z");
                        }));
        expectFaultAt(
                changed(
                        value -> {
                            deletionAt(value, 0).put("completedAt", "2026-09-01T00:00:01Z");
                            deletionAt(value, 0).put("backupExpiresAt", "2026-09-01T00:00:01Z");
                        }),
                "E_WITHDRAWAL_SLA",
                "/deletionLedger/0");
    }

    private static void cliReadOnly() {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("dora-poc-data-cli-");
            Path manifest = directory.resolve("manifest.json");
            byte[] valid = SyntheticManifestValidator.canonicalBytes(fixture());
            Files.write(manifest, valid);
            CliResult success = runCli(manifest.toString());
            check(success.exit() == 0, "cli-success-exit");
            check(
                    success.stdout().equals(EXPECTED_PASS_MARKER + System.lineSeparator()),
                    "cli-success-stdout");
            check(success.stderr().isEmpty(), "cli-success-stderr");
            check(Arrays.equals(Files.readAllBytes(manifest), valid), "cli-source-unchanged");

            Map<String, Object> invalidValue = changed(value -> value.put("trainingAllowed", true));
            byte[] invalid = SyntheticManifestValidator.canonicalBytes(invalidValue);
            Files.write(manifest, invalid);
            CliResult rejected = runCli(manifest.toString());
            check(rejected.exit() == 1, "cli-rejected-exit");
            check(rejected.stdout().isEmpty(), "cli-rejected-stdout");
            check(
                    rejected.stderr()
                            .equals(
                                    "LOCAL_FAIL E_TRAINING_FORBIDDEN /trainingAllowed"
                                            + System.lineSeparator()),
                    "cli-rejected-stderr");
            check(Arrays.equals(Files.readAllBytes(manifest), invalid), "cli-rejected-source");

            CliResult usage = runCli();
            check(usage.exit() == 2, "cli-usage-exit");
            check(usage.stdout().isEmpty(), "cli-usage-stdout");
            check(
                    usage.stderr().equals("LOCAL_FAIL E_USAGE /" + System.lineSeparator()),
                    "cli-usage-stderr");

            CliResult missing = runCli(directory.resolve("missing.json").toString());
            check(missing.exit() == 3, "cli-missing-exit");
            check(missing.stdout().isEmpty(), "cli-missing-stdout");
            check(
                    missing.stderr()
                            .equals("LOCAL_FAIL E_READ_OR_INTERNAL /" + System.lineSeparator()),
                    "cli-missing-stderr");

            Path oversized = directory.resolve("oversized.json");
            Files.write(oversized, new byte[524_289]);
            CliResult bounded = runCli(oversized.toString());
            check(bounded.exit() == 1, "cli-oversized-exit");
            check(bounded.stdout().isEmpty(), "cli-oversized-stdout");
            check(
                    bounded.stderr()
                            .equals("LOCAL_FAIL E_INPUT_TOO_LARGE /" + System.lineSeparator()),
                    "cli-oversized-stderr");
            check(Files.size(oversized) == 524_289L, "cli-oversized-source");

            try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                check(files.count() == 2L, "cli-no-output-files");
            }
            Files.delete(oversized);
            Files.delete(manifest);
            Files.delete(directory);
            directory = null;
        } catch (java.io.IOException error) {
            throw new AssertionError("cli-io");
        } finally {
            if (directory != null) {
                try {
                    Path manifest = directory.resolve("manifest.json");
                    Path oversized = directory.resolve("oversized.json");
                    Files.deleteIfExists(oversized);
                    Files.deleteIfExists(manifest);
                    Files.deleteIfExists(directory);
                } catch (java.io.IOException error) {
                    throw new AssertionError("cli-cleanup");
                }
            }
        }
    }

    private static Map<String, Object> fixture() {
        Map<String, Object> result = map();
        result.put("schemaVersion", 1L);
        result.put("profileId", EXPECTED_PROFILE_ID);
        result.put("manifestId", "manifest-0011223344556677");
        result.put("datasetId", "dataset-8899aabbccddeeff");
        result.put("version", "1.0.0");
        result.put(
                "purpose",
                map(
                        "pocIds",
                        mutableList("POC-VAD-001"),
                        "allowedUses",
                        mutableList("VAD_SILENCE_AND_MAX_CAP_EVALUATION"),
                        "excludedUses",
                        new ArrayList<>(EXPECTED_EXCLUDED)));
        result.put("dataClass", "SYNTHETIC_SIGNAL");
        result.put(
                "origin",
                map(
                        "type",
                        "DETERMINISTIC_GENERATOR",
                        "generatorId",
                        "poc-data-java17-in-memory-fixture-generator",
                        "generatorVersion",
                        "1.0.0",
                        "seed",
                        2_026_081_701L,
                        "sourceReference",
                        "tools/poc_data_manifest_validator/src/test/java/com/monumentogram/dora/stage0/data/manifest/SyntheticManifestValidatorTest.java"));
        result.put("licenseId", "DORA_ORIGINAL_SYNTHETIC_METADATA_ONLY");
        result.put("termsDigest", "sha256:" + "1a".repeat(32));
        result.put("consentReference", "not-applicable");
        result.put("trainingAllowed", Boolean.FALSE);
        result.put("publicRedistributionAllowed", Boolean.FALSE);
        result.put(
                "samples",
                mutableList(
                        sample(
                                "sample-1111111111111111",
                                null,
                                "development",
                                "active",
                                "2026-12-31T00:00:00Z",
                                "ROOT_SYNTHETIC_ENTRY"),
                        sample(
                                "sample-2222222222222222",
                                "sample-1111111111111111",
                                "development",
                                "active",
                                "2026-12-30T00:00:00Z",
                                "DERIVED_SYNTHETIC_VARIANT"),
                        sample(
                                "sample-3333333333333333",
                                null,
                                "test",
                                "deleted",
                                "2026-12-31T00:00:00Z",
                                "DELETION_DRY_RUN_TARGET"),
                        sample(
                                "sample-4444444444444444",
                                "sample-3333333333333333",
                                "test",
                                "deleted",
                                "2026-12-30T00:00:00Z",
                                "DELETION_DRY_RUN_TARGET")));
        result.put(
                "deletionLedger",
                mutableList(
                        deletion("delete-3333333333333333", "sample-3333333333333333"),
                        deletion("delete-4444444444444444", "sample-4444444444444444")));
        result.put(
                "publicProjection",
                map(
                        "containsRawContent",
                        Boolean.FALSE,
                        "containsTranscriptOrSourceExcerpt",
                        Boolean.FALSE,
                        "containsPersonalData",
                        Boolean.FALSE,
                        "containsParticipantMapping",
                        Boolean.FALSE,
                        "containsConsentForm",
                        Boolean.FALSE,
                        "containsPrivateLocator",
                        Boolean.FALSE,
                        "containsSignedUrl",
                        Boolean.FALSE,
                        "containsDeviceOrAccountIdentifier",
                        Boolean.FALSE,
                        "containsPublicLinkableContentDigest",
                        Boolean.FALSE));
        result.put("limitations", new ArrayList<>(EXPECTED_LIMITATIONS));
        return result;
    }

    private static Map<String, Object> generatedTextFixture() {
        Map<String, Object> value = fixture();
        value.put("dataClass", "GENERATED_TEXT");
        List<Object> samples = array(value.get("samples"));
        List<String> languages = List.of("ru", "en", "mixed-ru-en", "ru");
        for (int index = 0; index < samples.size(); index++) {
            Map<String, Object> row = object(samples.get(index));
            row.put("languageSlice", languages.get(index));
            row.put("acousticSlice", textAcoustic());
            row.put("speakerCountBucket", "not-applicable");
        }
        return value;
    }

    private static Map<String, Object> sample(
            String sampleId,
            String parent,
            String split,
            String state,
            String expires,
            String note) {
        return map(
                "sampleId",
                sampleId,
                "contentSha256",
                null,
                "languageSlice",
                "non-speech",
                "acousticSlice",
                signalAcoustic(),
                "speakerCountBucket",
                "0",
                "split",
                split,
                "parentSampleId",
                parent,
                "storageClass",
                "MANIFEST_ONLY_NO_SAMPLE_BYTES",
                "evidenceLocator",
                null,
                "accessRoles",
                mutableList("generator", "qa-evaluator"),
                "createdAt",
                "2026-08-01T00:00:00Z",
                "expiresAt",
                expires,
                "deletionState",
                state,
                "notes",
                note);
    }

    private static Map<String, Object> deletion(String eventId, String sampleId) {
        return map(
                "eventId",
                eventId,
                "sampleId",
                sampleId,
                "trigger",
                "SYNTHETIC_DRY_RUN",
                "requestedAt",
                "2026-08-02T00:00:00Z",
                "completedAt",
                "2026-08-03T00:00:00Z",
                "backupExpiresAt",
                "2026-08-03T00:00:00Z",
                "affectedScopes",
                new ArrayList<>(EXPECTED_SCOPES),
                "unresolvedFailures",
                new ArrayList<>(),
                "outcome",
                "DELETED");
    }

    private static Map<String, Object> signalAcoustic() {
        return map(
                "condition",
                "SYNTHETIC_SILENCE",
                "distance",
                "NOT_APPLICABLE",
                "route",
                "NOT_APPLICABLE",
                "overlap",
                "NONE");
    }

    private static Map<String, Object> textAcoustic() {
        return map(
                "condition",
                "NOT_APPLICABLE_TEXT",
                "distance",
                "NOT_APPLICABLE",
                "route",
                "NOT_APPLICABLE",
                "overlap",
                "NOT_APPLICABLE");
    }

    private static String mappedUse(String pocId) {
        return switch (pocId) {
            case "POC-CAPTURE-001", "POC-RECOVERY-001" -> EXPECTED_USES.get(0);
            case "POC-VAD-001" -> EXPECTED_USES.get(1);
            case "POC-ASR-001" -> EXPECTED_USES.get(2);
            case "POC-DIAR-001" -> EXPECTED_USES.get(3);
            case "POC-DECISION-001" -> EXPECTED_USES.get(4);
            case "POC-OFFLINE-001", "POC-VPN-001" -> EXPECTED_USES.get(5);
            case "POC-BATTERY-001" -> EXPECTED_USES.get(6);
            case "POC-SEARCH-001" -> EXPECTED_USES.get(7);
            default -> throw new AssertionError("poc-mapping");
        };
    }

    private static Map<String, Object> changed(Consumer<Map<String, Object>> mutation) {
        Map<String, Object> value = object(deepCopy(fixture()));
        mutation.accept(value);
        return value;
    }

    private static void expectValid(Map<String, Object> value) {
        SyntheticManifestValidator.validate(SyntheticManifestValidator.canonicalBytes(value));
    }

    private static void expectFault(Map<String, Object> value, String code) {
        expectRawFault(SyntheticManifestValidator.canonicalBytes(value), code);
    }

    private static void expectFaultAt(Map<String, Object> value, String code, String pointer) {
        expectRawFaultAt(SyntheticManifestValidator.canonicalBytes(value), code, pointer);
    }

    private static void expectRawFault(byte[] value, String code) {
        try {
            SyntheticManifestValidator.validate(value);
            throw new AssertionError("missing-fault");
        } catch (SyntheticManifestValidator.ManifestFault fault) {
            check(fault.code().equals(code), "wrong-fault");
            check(fault.pointer().startsWith("/"), "fault-pointer");
            check(fault.getMessage().equals(code), "fault-message");
        }
    }

    private static void expectRawFaultAt(byte[] value, String code, String pointer) {
        try {
            SyntheticManifestValidator.validate(value);
            throw new AssertionError("missing-fault");
        } catch (SyntheticManifestValidator.ManifestFault fault) {
            check(fault.code().equals(code), "wrong-fault-code");
            check(fault.pointer().equals(pointer), "wrong-fault-pointer");
            check(fault.getMessage().equals(code), "fault-message");
        }
    }

    private static CliResult runCli(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit;
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            exit = SyntheticManifestValidator.runCli(args, out, err);
        }
        return new CliResult(
                exit,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError("sha256-unavailable");
        }
    }

    private static Map<String, Object> sampleAt(Map<String, Object> value, int index) {
        return object(array(value.get("samples")).get(index));
    }

    private static Map<String, Object> deletionAt(Map<String, Object> value, int index) {
        return object(array(value.get("deletionLedger")).get(index));
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put((String) entry.getKey(), deepCopy(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(deepCopy(item));
            }
            return result;
        }
        return value;
    }

    private static Map<String, Object> map(Object... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("pairs");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    private record CliResult(int exit, String stdout, String stderr) {}

    private static List<Object> mutableList(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new AssertionError("object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put((String) entry.getKey(), entry.getValue());
        }
        if (value instanceof LinkedHashMap<?, ?>) {
            return castMutableMap(value);
        }
        return result;
    }

    private static Map<String, Object> castMutableMap(Object value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    private static List<Object> array(Object value) {
        if (!(value instanceof List<?>)) {
            throw new AssertionError("array");
        }
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) value;
        return result;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private record TestCase(String name, Runnable body) {}
}
