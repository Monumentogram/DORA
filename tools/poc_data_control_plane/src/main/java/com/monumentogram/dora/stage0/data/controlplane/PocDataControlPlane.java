package com.monumentogram.dora.stage0.data.controlplane;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Local, dependency-free validator and synthetic lifecycle dry-run for the Stage 0 POC-DATA
 * control plane.
 *
 * <p>This class has no Android, network, cloud, model, audio, database, or production-storage
 * edge. It accepts only the exact repository-owned synthetic manifest profile. Diagnostics are
 * content-free codes and JSON pointers; they never echo input strings or filesystem paths.
 */
public final class PocDataControlPlane {
    public static final int MAX_INPUT_BYTES = 524_288;
    public static final int MAX_JSON_DEPTH = 32;
    public static final String CUSTODIAN_ASSIGNMENT = "CUSTODIAN_UNASSIGNED";
    public static final String MANIFEST_SHA256 =
            "580589ff04d84ccfbb48d344cfddb144c82eb5f5e3f333794590105572094d46";
    public static final String SCHEMA_SHA256 =
            "4489dd2f7bb9dba8488d9b8b3a298b790d5ce586eabd99dcdf1ed7ae29fa800b";

    private static final byte[] SENTINEL_BYTES =
            "DORA_STAGE0_SYNTHETIC_CONTROL_PLANE_SENTINEL_V1\n"
                    .getBytes(StandardCharsets.US_ASCII);
    private static final String SENTINEL_NAME = "dora-poc-data-control-plane.synthetic";
    private static final Pattern ID_16 =
            Pattern.compile("^(dataset|manifest|sample|delete)-[0-9a-f]{16}$");
    private static final Pattern TIMESTAMP =
            Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$");

    public static final List<String> ROLES =
            List.of(
                    "SYNTHETIC_DRY_RUN_OPERATOR",
                    "SECURITY_AUDITOR",
                    "EVALUATOR",
                    "COLLECTOR",
                    "ANNOTATOR",
                    "CUSTODIAN");

    public static final List<String> ACTIONS =
            List.of(
                    "READ_PUBLIC_MANIFEST",
                    "VALIDATE_MANIFEST",
                    "CREATE_SYNTHETIC_TEMP",
                    "DELETE_SYNTHETIC_TEMP",
                    "READ_CONTENT_FREE_EVIDENCE",
                    "APPROVE_REAL_COLLECTION",
                    "ASSIGN_CONTROLLED_ACCESS",
                    "READ_RAW_OR_DERIVED_DATA",
                    "ANNOTATE_PURPOSE_RECORDED_DATA",
                    "DELETE_CONTROLLED_DATA",
                    "EXPORT_PRIVATE_DATA",
                    "CONFIGURE_CLOUD_TRANSFER",
                    "RUN_MODEL_INFERENCE",
                    "TRAIN_OR_IMPROVE_MODEL");

    public static final List<String> BLOCKERS =
            List.of(
                    "DATA_CUSTODIAN_UNASSIGNED",
                    "CONTROLLED_NON_PUBLIC_STORAGE_NOT_CONFIGURED",
                    "CONSENT_PROCESS_PREPARED_NOT_OPERATIONAL",
                    "REAL_COLLECTION_NOT_AUTHORIZED",
                    "ACTUAL_CORPUS_COUNTS_SLICES_AND_SCRIPTS_NOT_FROZEN",
                    "ACTUAL_IMMUTABLE_CORPUS_MANIFEST_AND_SPLITS_ABSENT",
                    "ANNOTATION_AND_ADJUDICATION_GUIDES_ABSENT",
                    "CONTROLLED_STORAGE_ACCESS_AND_DELETION_DRY_RUN_NOT_RUN",
                    "EXTERNAL_DATA_RIGHTS_NOT_APPROVED",
                    "PRODUCTION_LEGAL_AND_SECURITY_APPROVAL_ABSENT");

    public static final List<String> LIMITATIONS =
            List.of(
                    "SYNTHETIC_CONTROL_PLANE_ONLY",
                    "NO_CORPUS_OR_REAL_DATA_CREATED",
                    "CUSTODIAN_UNASSIGNED",
                    "CONSENT_PREPARED_NOT_OPERATIONAL",
                    "NO_CONTROLLED_STORAGE",
                    "NO_NETWORK_CLOUD_OR_DEVICE_EXECUTION",
                    "NO_MODEL_INFERENCE_TRAINING_OR_IMPROVEMENT",
                    "NO_PRODUCTION_SCHEMA_STORAGE_OR_ADMISSION",
                    "NO_POC_DATA_READY_PASS_OR_BLOCKER_CLOSURE",
                    "NO_LEGAL_SECURITY_OR_FORMAL_HUMAN_APPROVAL_CLAIM");

    public static final List<String> DELETION_SCOPES =
            List.of(
                    "RAW_AND_TRANSFORMED_COPIES",
                    "TRANSCRIPTS_ANNOTATIONS_FEATURES_NOTES",
                    "CACHES_EXPORTS_DOWNLOADS",
                    "SPLIT_INDEX_AND_PRIVATE_MANIFESTS",
                    "CONTROLLED_BACKUPS",
                    "PROVIDER_COPIES",
                    "PARTICIPANT_MAPPING_AND_ACCESS_GRANTS",
                    "DERIVED_VARIANTS");

    public static final List<String> CONSENT_ELEMENTS =
            List.of(
                    "STUDY_OPERATOR_AND_CONTACT",
                    "NAMED_POC_AND_PURPOSE",
                    "INTENTIONAL_RECORDING_AND_SCRIPT",
                    "DERIVATIVE_CLASSES",
                    "RAW_AND_DERIVED_ACCESS_ROLES",
                    "HUMAN_LISTENING_OR_ANNOTATION",
                    "STORAGE_ENCRYPTION_RETENTION_DELETION",
                    "PROVIDER_ARTIFACT_AND_REGION_OR_NONE",
                    "WITHDRAWAL_AND_AGGREGATE_LIMIT",
                    "TRAINING_AND_MODEL_IMPROVEMENT_PROHIBITED",
                    "VOLUNTARY_NO_ACCOUNT_OR_PRODUCT_PENALTY",
                    "DELETION_REQUEST_EVIDENCE_AND_BACKUP_EXPIRY");

    public static final List<String> SCENARIO_IDS = createScenarioIds();
    private static final Map<String, List<String>> ALLOWED_ROLES = createAllowedRoles();
    private static final Set<String> CUSTODIAN_REQUIRED_ACTIONS =
            Set.of(
                    "APPROVE_REAL_COLLECTION",
                    "ASSIGN_CONTROLLED_ACCESS",
                    "READ_RAW_OR_DERIVED_DATA",
                    "ANNOTATE_PURPOSE_RECORDED_DATA",
                    "DELETE_CONTROLLED_DATA",
                    "EXPORT_PRIVATE_DATA",
                    "CONFIGURE_CLOUD_TRANSFER");

    private PocDataControlPlane() {}

    /** A successful exact-profile validation result. */
    public record ValidationReport(
            String manifestSha256,
            String readiness,
            String overallResult,
            String collectionAuthorization) {}

    /** A deterministic, content-free synthetic lifecycle result. */
    public record DryRunReport(
            String manifestSha256,
            int scenarioCount,
            boolean sentinelDeleted,
            boolean deletionIdempotent,
            boolean sourceUnchanged,
            String readiness,
            String overallResult,
            String collectionAuthorization) {
        /** Canonical JSON used by tests to prove repeated-run byte identity. */
        public byte[] canonicalBytes() {
            Map<String, Object> value = new HashMap<>();
            value.put("collectionAuthorization", collectionAuthorization);
            value.put("deletionIdempotent", deletionIdempotent);
            value.put("manifestSha256", manifestSha256);
            value.put("overallResult", overallResult);
            value.put("readiness", readiness);
            value.put("scenarioCount", Long.valueOf(scenarioCount));
            value.put("sentinelDeleted", sentinelDeleted);
            value.put("sourceUnchanged", sourceUnchanged);
            return PocDataControlPlane.canonicalBytes(value);
        }
    }

    /** A fail-closed authorization decision that never grants unknown role/action pairs. */
    public record AccessDecision(boolean allowed, String code) {}

    /** Validate the exact canonical repository-owned synthetic manifest. */
    public static ValidationReport validateManifest(byte[] raw) {
        byte[] owned = Objects.requireNonNull(raw, "raw").clone();
        Object parsed = parse(owned, true);
        validateManifestObject(parsed);
        String digest = sha256(owned);
        require(MANIFEST_SHA256.equals(digest), "E_MANIFEST_PROFILE_DRIFT", "/");
        return new ValidationReport(digest, "NOT_READY", "NOT_RUN", "NOT_AUTHORIZED");
    }

    /** Validate the pinned schema bytes and the high-value closed-profile catalogues. */
    public static String validateSchemaProfile(byte[] raw) {
        byte[] owned = Objects.requireNonNull(raw, "raw").clone();
        Object parsed = parse(owned, false);
        Map<String, Object> root = object(parsed, "/");
        requireExactKeys(
                root,
                "/",
                "$schema",
                "$id",
                "title",
                "description",
                "type",
                "additionalProperties",
                "required",
                "properties",
                "$defs");
        requireString(root, "$schema", "https://json-schema.org/draft/2020-12/schema", "/$schema");
        requireString(root, "$id", "urn:dora:stage0:poc-data:control-plane:v0.1", "/$id");
        requireString(root, "type", "object", "/type");
        requireBoolean(root, "additionalProperties", false, "/additionalProperties");
        requireStrings(root.get("required"), ROOT_KEYS, "/required");

        Map<String, Object> defs = object(root.get("$defs"), "/$defs");
        requireSchemaEnum(defs, "accessRole", ROLES);
        requireSchemaEnum(defs, "action", ACTIONS);
        requireSchemaEnum(defs, "blocker", BLOCKERS);
        requireSchemaEnum(defs, "limitation", LIMITATIONS);
        requireSchemaEnum(defs, "deletionScope", DELETION_SCOPES);

        String digest = sha256(owned);
        require(SCHEMA_SHA256.equals(digest), "E_SCHEMA_PROFILE_DRIFT", "/");
        return digest;
    }

    /** Evaluate one role/action pair under the frozen deny-by-default policy. */
    public static AccessDecision authorize(String role, String action) {
        if (role == null || !ROLES.contains(role)) {
            return new AccessDecision(false, "E_UNKNOWN_ROLE");
        }
        if (action == null || !ACTIONS.contains(action)) {
            return new AccessDecision(false, "E_UNKNOWN_ACTION");
        }
        if (CUSTODIAN_REQUIRED_ACTIONS.contains(action)
                && CUSTODIAN_ASSIGNMENT.equals("CUSTODIAN_UNASSIGNED")) {
            return new AccessDecision(false, "E_CUSTODIAN_UNASSIGNED");
        }
        if (!ALLOWED_ROLES.get(action).contains(role)) {
            return new AccessDecision(false, "E_POLICY_DENY");
        }
        return new AccessDecision(true, "ALLOW_SYNTHETIC_ONLY");
    }

    /**
     * Execute the bounded synthetic sentinel lifecycle in an already-existing empty directory.
     * The directory path is never included in the result or diagnostics.
     */
    public static DryRunReport dryRun(byte[] raw, Path temporaryRoot) throws IOException {
        ValidationReport validation = validateManifest(raw);
        Path root = Objects.requireNonNull(temporaryRoot, "temporaryRoot").toAbsolutePath().normalize();
        require(
                Files.exists(root, LinkOption.NOFOLLOW_LINKS)
                        && Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(root),
                "E_TEMP_ROOT_UNSAFE",
                "/dryRun/tempRoot");
        try (Stream<Path> entries = Files.list(root)) {
            require(entries.findAny().isEmpty(), "E_TEMP_ROOT_NOT_EMPTY", "/dryRun/tempRoot");
        }

        requireAllowed("SYNTHETIC_DRY_RUN_OPERATOR", "VALIDATE_MANIFEST");
        requireAllowed("SYNTHETIC_DRY_RUN_OPERATOR", "CREATE_SYNTHETIC_TEMP");
        requireAllowed("SYNTHETIC_DRY_RUN_OPERATOR", "DELETE_SYNTHETIC_TEMP");
        requireDenied("COLLECTOR", "APPROVE_REAL_COLLECTION");
        requireDenied("CUSTODIAN", "READ_RAW_OR_DERIVED_DATA");
        requireDenied("SYNTHETIC_DRY_RUN_OPERATOR", "CONFIGURE_CLOUD_TRANSFER");
        requireDenied("SYNTHETIC_DRY_RUN_OPERATOR", "RUN_MODEL_INFERENCE");
        requireDenied("SYNTHETIC_DRY_RUN_OPERATOR", "TRAIN_OR_IMPROVE_MODEL");

        String before = sha256(raw);
        Path sentinel = root.resolve(SENTINEL_NAME).normalize();
        require(
                sentinel.startsWith(root) && root.equals(sentinel.getParent()),
                "E_TEMP_TARGET_ESCAPE",
                "/dryRun/tempTarget");

        boolean created = false;
        try {
            Files.write(
                    sentinel,
                    SENTINEL_BYTES,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            created = true;
            byte[] observed = Files.readAllBytes(sentinel);
            require(
                    java.util.Arrays.equals(SENTINEL_BYTES, observed),
                    "E_SENTINEL_VERIFY",
                    "/dryRun/sentinel");
            Files.delete(sentinel);
            created = false;
            require(
                    !Files.exists(sentinel, LinkOption.NOFOLLOW_LINKS),
                    "E_DELETE_VERIFY",
                    "/dryRun/sentinel");
            boolean secondDelete = Files.deleteIfExists(sentinel);
            require(!secondDelete, "E_DELETE_NOT_IDEMPOTENT", "/dryRun/sentinel");
        } finally {
            if (created && Files.exists(sentinel, LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(sentinel);
            }
        }

        String after = sha256(raw);
        require(before.equals(after), "E_SOURCE_MUTATED", "/");
        return new DryRunReport(
                validation.manifestSha256(),
                SCENARIO_IDS.size(),
                true,
                true,
                true,
                validation.readiness(),
                validation.overallResult(),
                validation.collectionAuthorization());
    }

    /** Command line entry point used by the evidence commands. */
    public static void main(String[] args) {
        System.exit(runCli(args, System.out, System.err));
    }

    /** Testable CLI entry point. */
    public static int runCli(String[] args, PrintStream out, PrintStream err) {
        if (args == null
                || args.length != 3
                || !("validate".equals(args[0]) || "dry-run".equals(args[0]))) {
            err.println("LOCAL_FAIL E_USAGE /");
            return 2;
        }
        try {
            byte[] manifest = readBounded(Path.of(args[1]));
            byte[] schema = readBounded(Path.of(args[2]));
            String schemaDigest = validateSchemaProfile(schema);
            if ("validate".equals(args[0])) {
                ValidationReport report = validateManifest(manifest);
                out.println(
                        "LOCAL_PASS POC_DATA_CONTROL_PLANE_SYNTHETIC_ONLY manifest_sha256="
                                + report.manifestSha256()
                                + " schema_sha256="
                                + schemaDigest
                                + " readiness=NOT_READY overall=NOT_RUN collection=NOT_AUTHORIZED");
                return 0;
            }

            Path temporaryRoot = Files.createTempDirectory("dora-poc-data-control-plane-");
            try {
                DryRunReport report = dryRun(manifest, temporaryRoot);
                out.println(
                        "LOCAL_PASS POC_DATA_CONTROL_PLANE_SYNTHETIC_DRY_RUN manifest_sha256="
                                + report.manifestSha256()
                                + " schema_sha256="
                                + schemaDigest
                                + " scenarios="
                                + report.scenarioCount()
                                + " sentinel_deleted=true deletion_idempotent=true source_unchanged=true"
                                + " readiness=NOT_READY overall=NOT_RUN collection=NOT_AUTHORIZED");
                return 0;
            } finally {
                Files.deleteIfExists(temporaryRoot);
            }
        } catch (ControlPlaneFault fault) {
            err.println("LOCAL_FAIL " + fault.code() + " " + fault.pointer());
            return 1;
        } catch (IOException | RuntimeException error) {
            err.println("LOCAL_FAIL E_READ_OR_INTERNAL /");
            return 3;
        }
    }

    /** Return deterministic canonical UTF-8 JSON with sorted object keys and one final LF. */
    public static byte[] canonicalBytes(Object value) {
        StringBuilder output = new StringBuilder();
        appendCanonical(value, output);
        output.append('\n');
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static final List<String> ROOT_KEYS =
            List.of(
                    "authorityFlags",
                    "authorizations",
                    "blockers",
                    "collectionPlan",
                    "consentProcess",
                    "contractId",
                    "custodian",
                    "datasetManifest",
                    "dryRun",
                    "limitations",
                    "rbac",
                    "retentionDeletion",
                    "schemaVersion",
                    "status");

    private static void validateManifestObject(Object parsed) {
        Map<String, Object> root = object(parsed, "/");
        requireExactKeys(root, "/", ROOT_KEYS.toArray(String[]::new));
        requireLong(root, "schemaVersion", 1L, "/schemaVersion");
        requireString(
                root,
                "status",
                "SYNTHETIC_CONTROL_PLANE_DRY_RUN_ONLY",
                "/status");
        requireString(root, "contractId", "poc-data-control-plane-stage0-v0.1", "/contractId");
        requireStrings(
                root.get("authorizations"),
                List.of(
                        "POC-DATA-CONTROL-PLANE-SETUP-AUTH-20260818-01",
                        "POC-DATA-CONTROL-PLANE-DISJOINT-FIRST-AUTH-20260818-01"),
                "/authorizations");
        requireStrings(root.get("blockers"), BLOCKERS, "/blockers");
        requireStrings(root.get("limitations"), LIMITATIONS, "/limitations");
        validateAuthorityFlags(root.get("authorityFlags"));
        validateCollectionPlan(root.get("collectionPlan"));
        validateConsent(root.get("consentProcess"));
        validateCustodian(root.get("custodian"));
        validateDatasetManifest(root.get("datasetManifest"));
        validateDryRun(root.get("dryRun"));
        validateRbac(root.get("rbac"));
        validateRetention(root.get("retentionDeletion"));
    }

    private static void validateAuthorityFlags(Object value) {
        Map<String, Object> flags = object(value, "/authorityFlags");
        Map<String, Boolean> expected = new TreeMap<>();
        expected.put("cloudTransferAllowed", false);
        expected.put("consentProcessOperational", false);
        expected.put("contentFreeEvidenceAllowed", true);
        expected.put("controlPlanePreparationAllowed", true);
        expected.put("controlledStorageConfigured", false);
        expected.put("custodianAssigned", false);
        expected.put("dependencyAdmissionAllowed", false);
        expected.put("derivedSensitiveDataAllowed", false);
        expected.put("deviceExecutionAllowed", false);
        expected.put("humanReviewAllowed", false);
        expected.put("localHostValidationAllowed", true);
        expected.put("mergeAllowed", false);
        expected.put("modelImprovementAllowed", false);
        expected.put("modelInferenceAllowed", false);
        expected.put("networkExecutionAllowed", false);
        expected.put("pocDataPassAllowed", false);
        expected.put("pocDataReadyAllowed", false);
        expected.put("productionAdmissionAllowed", false);
        expected.put("productionSchemaAllowed", false);
        expected.put("productionStorageAllowed", false);
        expected.put("purposeRecordedCollectionAllowed", false);
        expected.put("rawAudioAllowed", false);
        expected.put("realMeetingDataAllowed", false);
        expected.put("realPeopleAllowed", false);
        expected.put("syntheticDryRunAllowed", true);
        expected.put("syntheticManifestArtifactAllowed", true);
        expected.put("trainingAllowed", false);
        expected.put("transientSyntheticSentinelAllowed", true);
        requireExactKeys(flags, "/authorityFlags", expected.keySet().toArray(String[]::new));
        for (Map.Entry<String, Boolean> entry : expected.entrySet()) {
            requireBoolean(
                    flags,
                    entry.getKey(),
                    entry.getValue().booleanValue(),
                    "/authorityFlags/" + entry.getKey());
        }
    }

    private static void validateCollectionPlan(Object value) {
        Map<String, Object> plan = object(value, "/collectionPlan");
        requireExactKeys(
                plan,
                "/collectionPlan",
                "cloudAllowed",
                "dataClasses",
                "deviceCaptureAllowed",
                "fixedInstant",
                "inputBytesPresent",
                "meetingCount",
                "modelTrainingAllowed",
                "networkAllowed",
                "participantCount",
                "planId",
                "sampleRowCount",
                "seed",
                "state",
                "storageClass",
                "voiceCount");
        requireBoolean(plan, "cloudAllowed", false, "/collectionPlan/cloudAllowed");
        requireStrings(
                plan.get("dataClasses"),
                List.of("GENERATED_TEXT_METADATA", "SYNTHETIC_SIGNAL_METADATA"),
                "/collectionPlan/dataClasses");
        requireBoolean(plan, "deviceCaptureAllowed", false, "/collectionPlan/deviceCaptureAllowed");
        requireTimestamp(plan.get("fixedInstant"), "/collectionPlan/fixedInstant");
        requireString(plan, "fixedInstant", "2026-08-18T12:00:00Z", "/collectionPlan/fixedInstant");
        requireBoolean(plan, "inputBytesPresent", false, "/collectionPlan/inputBytesPresent");
        requireLong(plan, "meetingCount", 0L, "/collectionPlan/meetingCount");
        requireBoolean(plan, "modelTrainingAllowed", false, "/collectionPlan/modelTrainingAllowed");
        requireBoolean(plan, "networkAllowed", false, "/collectionPlan/networkAllowed");
        requireLong(plan, "participantCount", 0L, "/collectionPlan/participantCount");
        requireString(
                plan,
                "planId",
                "poc-data-synthetic-control-plane-plan-stage0-v0.1",
                "/collectionPlan/planId");
        requireLong(plan, "sampleRowCount", 4L, "/collectionPlan/sampleRowCount");
        requireLong(plan, "seed", 2026081801L, "/collectionPlan/seed");
        requireString(
                plan,
                "state",
                "AUTHORIZED_SYNTHETIC_DRY_RUN_ONLY",
                "/collectionPlan/state");
        requireString(
                plan,
                "storageClass",
                "REPOSITORY_MANIFEST_AND_TRANSIENT_OS_TEMP_SENTINEL_ONLY",
                "/collectionPlan/storageClass");
        requireLong(plan, "voiceCount", 0L, "/collectionPlan/voiceCount");
    }

    private static void validateConsent(Object value) {
        Map<String, Object> consent = object(value, "/consentProcess");
        requireExactKeys(
                consent,
                "/consentProcess",
                "appliesTo",
                "currentConsentReference",
                "processId",
                "realConsentRecords",
                "requiredElements",
                "state");
        requireString(
                consent,
                "appliesTo",
                "FUTURE_PURPOSE_RECORDED_ADULT_VOLUNTEER_DATA_ONLY",
                "/consentProcess/appliesTo");
        requireString(
                consent,
                "currentConsentReference",
                "not-applicable",
                "/consentProcess/currentConsentReference");
        requireString(
                consent,
                "processId",
                "poc-data-consent-process-stage0-v0.1",
                "/consentProcess/processId");
        requireLong(consent, "realConsentRecords", 0L, "/consentProcess/realConsentRecords");
        requireStrings(consent.get("requiredElements"), CONSENT_ELEMENTS, "/consentProcess/requiredElements");
        requireString(
                consent,
                "state",
                "PREPARED_NOT_OPERATIONAL",
                "/consentProcess/state");
    }

    private static void validateCustodian(Object value) {
        Map<String, Object> custodian = object(value, "/custodian");
        requireExactKeys(
                custodian,
                "/custodian",
                "assignment",
                "controlledStorageEnabled",
                "realCollectionEnabled");
        requireString(custodian, "assignment", CUSTODIAN_ASSIGNMENT, "/custodian/assignment");
        requireBoolean(
                custodian,
                "controlledStorageEnabled",
                false,
                "/custodian/controlledStorageEnabled");
        requireBoolean(
                custodian,
                "realCollectionEnabled",
                false,
                "/custodian/realCollectionEnabled");
    }

    private static void validateDatasetManifest(Object value) {
        Map<String, Object> manifest = object(value, "/datasetManifest");
        requireExactKeys(
                manifest,
                "/datasetManifest",
                "consentReference",
                "dataClass",
                "datasetId",
                "deletionLedger",
                "manifestId",
                "publicProjection",
                "publicRedistributionAllowed",
                "samples",
                "trainingAllowed",
                "version");
        requireString(manifest, "consentReference", "not-applicable", "/datasetManifest/consentReference");
        requireString(
                manifest,
                "dataClass",
                "SYNTHETIC_CONTROL_PLANE_METADATA",
                "/datasetManifest/dataClass");
        requireId(manifest.get("datasetId"), "dataset", "/datasetManifest/datasetId");
        requireString(
                manifest,
                "datasetId",
                "dataset-1111222233334444",
                "/datasetManifest/datasetId");
        requireId(manifest.get("manifestId"), "manifest", "/datasetManifest/manifestId");
        requireString(
                manifest,
                "manifestId",
                "manifest-aaaabbbbccccdddd",
                "/datasetManifest/manifestId");
        requireBoolean(
                manifest,
                "publicRedistributionAllowed",
                false,
                "/datasetManifest/publicRedistributionAllowed");
        requireBoolean(manifest, "trainingAllowed", false, "/datasetManifest/trainingAllowed");
        requireString(manifest, "version", "1.0.0", "/datasetManifest/version");
        validatePublicProjection(manifest.get("publicProjection"));
        validateSamples(manifest.get("samples"));
        validateDeletionLedger(manifest.get("deletionLedger"));
    }

    private static void validatePublicProjection(Object value) {
        Map<String, Object> projection = object(value, "/datasetManifest/publicProjection");
        List<String> keys =
                List.of(
                        "containsConsentForm",
                        "containsContentDigest",
                        "containsDeviceOrAccountIdentifier",
                        "containsParticipantMapping",
                        "containsPersonalData",
                        "containsPrivateLocator",
                        "containsRawAudioOrTranscript",
                        "containsSignedUrl");
        requireExactKeys(
                projection, "/datasetManifest/publicProjection", keys.toArray(String[]::new));
        for (String key : keys) {
            requireBoolean(
                    projection,
                    key,
                    false,
                    "/datasetManifest/publicProjection/" + key);
        }
    }

    private static void validateSamples(Object value) {
        List<Object> samples = array(value, "/datasetManifest/samples");
        require(samples.size() == 4, "E_SAMPLE_COUNT", "/datasetManifest/samples");
        List<String> ids =
                List.of(
                        "sample-1111111111111111",
                        "sample-2222222222222222",
                        "sample-3333333333333333",
                        "sample-4444444444444444");
        List<String> classes =
                List.of(
                        "GENERATED_TEXT_METADATA",
                        "GENERATED_TEXT_METADATA",
                        "SYNTHETIC_SIGNAL_METADATA",
                        "GENERATED_TEXT_METADATA");
        List<String> states = List.of("ACTIVE", "ACTIVE", "DELETED", "ACTIVE");
        List<String> splits = List.of("development", "development", "test", "evaluation");
        List<String> notes =
                List.of(
                        "ROOT_SYNTHETIC_ENTRY",
                        "DERIVED_SYNTHETIC_VARIANT",
                        "DELETION_DRY_RUN_TARGET",
                        "ROOT_SYNTHETIC_ENTRY");
        Set<String> observedIds = new HashSet<>();
        for (int index = 0; index < samples.size(); index++) {
            String pointer = "/datasetManifest/samples/" + index;
            Map<String, Object> sample = object(samples.get(index), pointer);
            requireExactKeys(
                    sample,
                    pointer,
                    "accessRoles",
                    "contentSha256",
                    "createdAt",
                    "dataClass",
                    "deletionState",
                    "evidenceLocator",
                    "expiresAt",
                    "notes",
                    "parentSampleId",
                    "sampleBytesPresent",
                    "sampleId",
                    "split");
            String id = string(sample.get("sampleId"), pointer + "/sampleId");
            requireId(id, "sample", pointer + "/sampleId");
            require(ids.get(index).equals(id), "E_SAMPLE_ORDER", pointer + "/sampleId");
            require(observedIds.add(id), "E_SAMPLE_DUPLICATE", pointer + "/sampleId");
            requireString(sample, "dataClass", classes.get(index), pointer + "/dataClass");
            requireString(sample, "deletionState", states.get(index), pointer + "/deletionState");
            requireString(sample, "split", splits.get(index), pointer + "/split");
            requireString(sample, "notes", notes.get(index), pointer + "/notes");
            requireTimestamp(sample.get("createdAt"), pointer + "/createdAt");
            requireTimestamp(sample.get("expiresAt"), pointer + "/expiresAt");
            requireString(sample, "createdAt", "2026-08-18T12:00:00Z", pointer + "/createdAt");
            requireString(sample, "expiresAt", "2026-08-18T12:00:00Z", pointer + "/expiresAt");
            require(sample.get("contentSha256") == null, "E_CONTENT_DIGEST_PRESENT", pointer + "/contentSha256");
            require(sample.get("evidenceLocator") == null, "E_PRIVATE_LOCATOR_PRESENT", pointer + "/evidenceLocator");
            requireBoolean(sample, "sampleBytesPresent", false, pointer + "/sampleBytesPresent");
            List<String> expectedRoles =
                    index == 0 || index == 3
                            ? List.of("SYNTHETIC_DRY_RUN_OPERATOR", "SECURITY_AUDITOR", "EVALUATOR")
                            : List.of("SYNTHETIC_DRY_RUN_OPERATOR", "SECURITY_AUDITOR");
            requireStrings(sample.get("accessRoles"), expectedRoles, pointer + "/accessRoles");
            Object parent = sample.get("parentSampleId");
            if (index == 1) {
                require(
                        "sample-1111111111111111".equals(parent),
                        "E_LINEAGE",
                        pointer + "/parentSampleId");
            } else {
                require(parent == null, "E_LINEAGE", pointer + "/parentSampleId");
            }
        }
    }

    private static void validateDeletionLedger(Object value) {
        List<Object> ledger = array(value, "/datasetManifest/deletionLedger");
        require(ledger.size() == 1, "E_DELETION_LEDGER", "/datasetManifest/deletionLedger");
        Map<String, Object> entry = object(ledger.get(0), "/datasetManifest/deletionLedger/0");
        requireExactKeys(
                entry,
                "/datasetManifest/deletionLedger/0",
                "affectedScopes",
                "completedAt",
                "eventId",
                "outcome",
                "requestedAt",
                "sampleId",
                "trigger",
                "unresolvedFailures");
        requireStrings(
                entry.get("affectedScopes"),
                DELETION_SCOPES,
                "/datasetManifest/deletionLedger/0/affectedScopes");
        requireTimestamp(entry.get("requestedAt"), "/datasetManifest/deletionLedger/0/requestedAt");
        requireTimestamp(entry.get("completedAt"), "/datasetManifest/deletionLedger/0/completedAt");
        requireString(
                entry,
                "requestedAt",
                "2026-08-18T12:00:00Z",
                "/datasetManifest/deletionLedger/0/requestedAt");
        requireString(
                entry,
                "completedAt",
                "2026-08-18T12:00:00Z",
                "/datasetManifest/deletionLedger/0/completedAt");
        requireString(
                entry,
                "eventId",
                "delete-3333333333333333",
                "/datasetManifest/deletionLedger/0/eventId");
        requireId(entry.get("eventId"), "delete", "/datasetManifest/deletionLedger/0/eventId");
        requireString(
                entry,
                "sampleId",
                "sample-3333333333333333",
                "/datasetManifest/deletionLedger/0/sampleId");
        requireString(entry, "outcome", "DELETED", "/datasetManifest/deletionLedger/0/outcome");
        requireString(
                entry,
                "trigger",
                "SYNTHETIC_DRY_RUN",
                "/datasetManifest/deletionLedger/0/trigger");
        require(
                array(entry.get("unresolvedFailures"),
                                "/datasetManifest/deletionLedger/0/unresolvedFailures")
                        .isEmpty(),
                "E_DELETION_FAILURES",
                "/datasetManifest/deletionLedger/0/unresolvedFailures");
    }

    private static void validateDryRun(Object value) {
        Map<String, Object> dryRun = object(value, "/dryRun");
        requireExactKeys(
                dryRun,
                "/dryRun",
                "expectedCollectionAuthorization",
                "expectedPocDataOverallResult",
                "expectedPocDataReadiness",
                "fixedInstant",
                "inputClass",
                "runId",
                "scenarioIds");
        requireString(
                dryRun,
                "expectedCollectionAuthorization",
                "NOT_AUTHORIZED",
                "/dryRun/expectedCollectionAuthorization");
        requireString(
                dryRun,
                "expectedPocDataOverallResult",
                "NOT_RUN",
                "/dryRun/expectedPocDataOverallResult");
        requireString(
                dryRun,
                "expectedPocDataReadiness",
                "NOT_READY",
                "/dryRun/expectedPocDataReadiness");
        requireString(dryRun, "fixedInstant", "2026-08-18T12:00:00Z", "/dryRun/fixedInstant");
        requireString(
                dryRun,
                "inputClass",
                "REPOSITORY_OWNED_SYNTHETIC_NON_SENSITIVE",
                "/dryRun/inputClass");
        requireString(
                dryRun,
                "runId",
                "poc-data-control-plane-dry-run-stage0-v0.1",
                "/dryRun/runId");
        requireStrings(dryRun.get("scenarioIds"), SCENARIO_IDS, "/dryRun/scenarioIds");
    }

    private static void validateRbac(Object value) {
        Map<String, Object> rbac = object(value, "/rbac");
        requireExactKeys(rbac, "/rbac", "assignments", "mode", "policies", "roles");
        requireString(rbac, "mode", "DENY_BY_DEFAULT", "/rbac/mode");
        requireStrings(rbac.get("roles"), ROLES, "/rbac/roles");
        List<Object> assignments = array(rbac.get("assignments"), "/rbac/assignments");
        require(assignments.size() == 1, "E_ASSIGNMENT_COUNT", "/rbac/assignments");
        Map<String, Object> assignment = object(assignments.get(0), "/rbac/assignments/0");
        requireExactKeys(assignment, "/rbac/assignments/0", "principal", "role");
        requireString(
                assignment,
                "principal",
                "BUILT_IN_TEST_HARNESS_ONLY",
                "/rbac/assignments/0/principal");
        requireString(
                assignment,
                "role",
                "SYNTHETIC_DRY_RUN_OPERATOR",
                "/rbac/assignments/0/role");

        List<Object> policies = array(rbac.get("policies"), "/rbac/policies");
        require(policies.size() == ACTIONS.size(), "E_POLICY_COUNT", "/rbac/policies");
        for (int index = 0; index < policies.size(); index++) {
            String pointer = "/rbac/policies/" + index;
            Map<String, Object> policy = object(policies.get(index), pointer);
            requireExactKeys(
                    policy,
                    pointer,
                    "action",
                    "allowedRoles",
                    "custodianRequired",
                    "syntheticOnly");
            String action = ACTIONS.get(index);
            requireString(policy, "action", action, pointer + "/action");
            requireStrings(policy.get("allowedRoles"), ALLOWED_ROLES.get(action), pointer + "/allowedRoles");
            requireBoolean(
                    policy,
                    "custodianRequired",
                    CUSTODIAN_REQUIRED_ACTIONS.contains(action),
                    pointer + "/custodianRequired");
            requireBoolean(
                    policy,
                    "syntheticOnly",
                    index < 5,
                    pointer + "/syntheticOnly");
        }
    }

    private static void validateRetention(Object value) {
        Map<String, Object> retention = object(value, "/retentionDeletion");
        requireExactKeys(
                retention,
                "/retentionDeletion",
                "approvedMaxima",
                "controlledStorageDryRunStatus",
                "deletionScopes",
                "shorterMandatoryPeriodWins",
                "syntheticTempPolicy");
        Map<String, Object> maxima =
                object(retention.get("approvedMaxima"), "/retentionDeletion/approvedMaxima");
        requireExactKeys(
                maxima,
                "/retentionDeletion/approvedMaxima",
                "derivedDaysAfterPocClose",
                "rawAudioDaysAfterPocClose",
                "withdrawalCompletionDays");
        requireLong(
                maxima,
                "derivedDaysAfterPocClose",
                180L,
                "/retentionDeletion/approvedMaxima/derivedDaysAfterPocClose");
        requireLong(
                maxima,
                "rawAudioDaysAfterPocClose",
                90L,
                "/retentionDeletion/approvedMaxima/rawAudioDaysAfterPocClose");
        requireLong(
                maxima,
                "withdrawalCompletionDays",
                30L,
                "/retentionDeletion/approvedMaxima/withdrawalCompletionDays");
        requireString(
                retention,
                "controlledStorageDryRunStatus",
                "NOT_RUN",
                "/retentionDeletion/controlledStorageDryRunStatus");
        requireStrings(
                retention.get("deletionScopes"),
                DELETION_SCOPES,
                "/retentionDeletion/deletionScopes");
        requireBoolean(
                retention,
                "shorterMandatoryPeriodWins",
                true,
                "/retentionDeletion/shorterMandatoryPeriodWins");
        requireString(
                retention,
                "syntheticTempPolicy",
                "DELETE_AND_VERIFY_ABSENCE_BEFORE_RETURN",
                "/retentionDeletion/syntheticTempPolicy");
    }

    private static void requireAllowed(String role, String action) {
        require(authorize(role, action).allowed(), "E_RBAC_EXPECTED_ALLOW", "/rbac");
    }

    private static void requireDenied(String role, String action) {
        require(!authorize(role, action).allowed(), "E_RBAC_EXPECTED_DENY", "/rbac");
    }

    private static void requireSchemaEnum(
            Map<String, Object> definitions, String name, List<String> expected) {
        Map<String, Object> definition = object(definitions.get(name), "/$defs/" + name);
        requireStrings(definition.get("enum"), expected, "/$defs/" + name + "/enum");
    }

    private static void requireId(Object value, String prefix, String pointer) {
        String text = string(value, pointer);
        require(ID_16.matcher(text).matches(), "E_ID_FORMAT", pointer);
        require(text.startsWith(prefix + "-"), "E_ID_PREFIX", pointer);
    }

    private static void requireTimestamp(Object value, String pointer) {
        String text = string(value, pointer);
        require(TIMESTAMP.matcher(text).matches(), "E_TIMESTAMP", pointer);
        try {
            Instant.parse(text);
        } catch (DateTimeException error) {
            reject("E_TIMESTAMP", pointer);
        }
    }

    private static void requireExactKeys(
            Map<String, Object> value, String pointer, String... expectedKeys) {
        Set<String> expected = Set.of(expectedKeys);
        require(value.size() == expected.size() && value.keySet().equals(expected), "E_OBJECT_KEYS", pointer);
    }

    private static void requireString(
            Map<String, Object> value, String key, String expected, String pointer) {
        require(expected.equals(string(value.get(key), pointer)), "E_VALUE", pointer);
    }

    private static void requireBoolean(
            Map<String, Object> value, String key, boolean expected, String pointer) {
        Object actual = value.get(key);
        require(actual instanceof Boolean, "E_TYPE", pointer);
        require(((Boolean) actual).booleanValue() == expected, "E_VALUE", pointer);
    }

    private static void requireLong(
            Map<String, Object> value, String key, long expected, String pointer) {
        Object actual = value.get(key);
        require(actual instanceof Long, "E_TYPE", pointer);
        require(((Long) actual).longValue() == expected, "E_VALUE", pointer);
    }

    private static void requireStrings(Object value, List<String> expected, String pointer) {
        List<Object> actual = array(value, pointer);
        require(actual.size() == expected.size(), "E_ARRAY_SIZE", pointer);
        for (int index = 0; index < expected.size(); index++) {
            require(
                    expected.get(index).equals(string(actual.get(index), pointer + "/" + index)),
                    "E_VALUE",
                    pointer + "/" + index);
        }
        require(new HashSet<>(expected).size() == expected.size(), "E_INTERNAL_CATALOGUE", pointer);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String pointer) {
        require(value instanceof Map<?, ?>, "E_TYPE", pointer);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value, String pointer) {
        require(value instanceof List<?>, "E_TYPE", pointer);
        return (List<Object>) value;
    }

    private static String string(Object value, String pointer) {
        require(value instanceof String, "E_TYPE", pointer);
        return (String) value;
    }

    private static List<String> createScenarioIds() {
        List<String> result = new ArrayList<>();
        for (int index = 1; index <= 15; index++) {
            result.add(String.format(java.util.Locale.ROOT, "DATA-CP-%03d", Integer.valueOf(index)));
        }
        return Collections.unmodifiableList(result);
    }

    private static Map<String, List<String>> createAllowedRoles() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put(
                "READ_PUBLIC_MANIFEST",
                List.of("SYNTHETIC_DRY_RUN_OPERATOR", "SECURITY_AUDITOR", "EVALUATOR"));
        result.put("VALIDATE_MANIFEST", List.of("SYNTHETIC_DRY_RUN_OPERATOR"));
        result.put("CREATE_SYNTHETIC_TEMP", List.of("SYNTHETIC_DRY_RUN_OPERATOR"));
        result.put("DELETE_SYNTHETIC_TEMP", List.of("SYNTHETIC_DRY_RUN_OPERATOR"));
        result.put(
                "READ_CONTENT_FREE_EVIDENCE",
                List.of("SYNTHETIC_DRY_RUN_OPERATOR", "SECURITY_AUDITOR"));
        for (String action : ACTIONS.subList(5, ACTIONS.size())) {
            result.put(action, List.of());
        }
        return Collections.unmodifiableMap(result);
    }

    private static byte[] readBounded(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return input.readNBytes(MAX_INPUT_BYTES + 1);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static Object parse(byte[] raw, boolean requireCanonical) {
        require(raw.length > 0, "E_JSON_SYNTAX", "/");
        require(raw.length <= MAX_INPUT_BYTES, "E_INPUT_TOO_LARGE", "/");
        if (startsWith(raw, new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf})
                || startsWith(raw, new byte[] {(byte) 0xff, (byte) 0xfe})
                || startsWith(raw, new byte[] {(byte) 0xfe, (byte) 0xff})) {
            reject("E_JSON_ENCODING", "/");
        }
        String text;
        try {
            text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(raw))
                            .toString();
        } catch (CharacterCodingException error) {
            reject("E_JSON_ENCODING", "/");
            return null;
        }
        Object value = new JsonParser(text).parseDocument();
        validateUnicode(value);
        if (requireCanonical && !java.util.Arrays.equals(raw, canonicalBytes(value))) {
            reject("E_JSON_NON_CANONICAL", "/");
        }
        return value;
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static void validateUnicode(Object value) {
        if (value instanceof String text) {
            for (int index = 0; index < text.length(); index++) {
                char current = text.charAt(index);
                if (Character.isHighSurrogate(current)) {
                    require(
                            index + 1 < text.length()
                                    && Character.isLowSurrogate(text.charAt(index + 1)),
                            "E_JSON_UNICODE",
                            "/");
                    index++;
                } else if (Character.isLowSurrogate(current)) {
                    reject("E_JSON_UNICODE", "/");
                }
            }
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                validateUnicode(item);
            }
        } else if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                validateUnicode(entry.getKey());
                validateUnicode(entry.getValue());
            }
        }
    }

    private static void appendCanonical(Object value, StringBuilder output) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String text) {
            appendString(text, output);
        } else if (value instanceof Boolean bool) {
            output.append(bool.booleanValue() ? "true" : "false");
        } else if (value instanceof Long number) {
            output.append(number.longValue());
        } else if (value instanceof List<?> list) {
            output.append('[');
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                appendCanonical(list.get(index), output);
            }
            output.append(']');
        } else if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                require(entry.getKey() instanceof String, "E_JSON_OBJECT_KEY", "/");
                sorted.put((String) entry.getKey(), entry.getValue());
            }
            output.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                appendString(entry.getKey(), output);
                output.append(':');
                appendCanonical(entry.getValue(), output);
            }
            output.append('}');
        } else {
            reject("E_JSON_VALUE", "/");
        }
    }

    private static void appendString(String value, StringBuilder output) {
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
                        output.append(String.format(java.util.Locale.ROOT, "\\u%04x", Integer.valueOf(current)));
                    } else {
                        output.append(current);
                    }
                }
            }
        }
        output.append('"');
    }

    private static void require(boolean condition, String code, String pointer) {
        if (!condition) {
            reject(code, pointer);
        }
    }

    private static void reject(String code, String pointer) {
        throw new ControlPlaneFault(code, pointer);
    }

    /** Content-free validation failure. */
    public static final class ControlPlaneFault extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String code;
        private final String pointer;

        private ControlPlaneFault(String code, String pointer) {
            super(code + " " + pointer);
            this.code = code;
            this.pointer = pointer;
        }

        public String code() {
            return code;
        }

        public String pointer() {
            return pointer;
        }
    }

    private static final class JsonParser {
        private final String input;
        private int position;

        private JsonParser(String input) {
            this.input = input;
        }

        private Object parseDocument() {
            skipWhitespace();
            Object result = parseValue(0);
            skipWhitespace();
            require(position == input.length(), "E_JSON_SYNTAX", "/");
            return result;
        }

        private Object parseValue(int depth) {
            require(depth <= MAX_JSON_DEPTH, "E_JSON_DEPTH", "/");
            require(position < input.length(), "E_JSON_SYNTAX", "/");
            char current = input.charAt(position);
            return switch (current) {
                case '{' -> parseObject(depth + 1);
                case '[' -> parseArray(depth + 1);
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> {
                    if (current == '-' || Character.isDigit(current)) {
                        yield parseInteger();
                    }
                    reject("E_JSON_SYNTAX", "/");
                    yield null;
                }
            };
        }

        private Map<String, Object> parseObject(int depth) {
            expect('{');
            skipWhitespace();
            Map<String, Object> result = new LinkedHashMap<>();
            if (consume('}')) {
                return result;
            }
            while (true) {
                require(peek('"'), "E_JSON_SYNTAX", "/");
                String key = parseString();
                require(!result.containsKey(key), "E_JSON_DUPLICATE_KEY", "/");
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object value = parseValue(depth);
                result.put(key, value);
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                expect(',');
                skipWhitespace();
            }
        }

        private List<Object> parseArray(int depth) {
            expect('[');
            skipWhitespace();
            List<Object> result = new ArrayList<>();
            if (consume(']')) {
                return result;
            }
            while (true) {
                result.add(parseValue(depth));
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                expect(',');
                skipWhitespace();
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (position < input.length()) {
                char current = input.charAt(position++);
                if (current == '"') {
                    return result.toString();
                }
                require(current >= 0x20, "E_JSON_SYNTAX", "/");
                if (current != '\\') {
                    result.append(current);
                    continue;
                }
                require(position < input.length(), "E_JSON_SYNTAX", "/");
                char escaped = input.charAt(position++);
                switch (escaped) {
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case '/' -> result.append('/');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append(parseUnicodeEscape());
                    default -> reject("E_JSON_SYNTAX", "/");
                }
            }
            reject("E_JSON_SYNTAX", "/");
            return null;
        }

        private char parseUnicodeEscape() {
            require(position + 4 <= input.length(), "E_JSON_SYNTAX", "/");
            int value = 0;
            for (int index = 0; index < 4; index++) {
                int digit = Character.digit(input.charAt(position++), 16);
                require(digit >= 0, "E_JSON_SYNTAX", "/");
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private Long parseInteger() {
            int start = position;
            consume('-');
            require(position < input.length(), "E_JSON_SYNTAX", "/");
            if (consume('0')) {
                require(position == input.length() || !Character.isDigit(input.charAt(position)), "E_JSON_NUMBER", "/");
            } else {
                require(position < input.length() && input.charAt(position) >= '1' && input.charAt(position) <= '9', "E_JSON_NUMBER", "/");
                while (position < input.length() && Character.isDigit(input.charAt(position))) {
                    position++;
                }
            }
            if (position < input.length()) {
                char next = input.charAt(position);
                require(next != '.' && next != 'e' && next != 'E', "E_JSON_NUMBER", "/");
            }
            try {
                return Long.valueOf(input.substring(start, position));
            } catch (NumberFormatException error) {
                reject("E_JSON_NUMBER", "/");
                return null;
            }
        }

        private Object parseLiteral(String literal, Object value) {
            require(input.startsWith(literal, position), "E_JSON_SYNTAX", "/");
            position += literal.length();
            return value;
        }

        private void skipWhitespace() {
            while (position < input.length()) {
                char current = input.charAt(position);
                if (current == ' ' || current == '\n' || current == '\r' || current == '\t') {
                    position++;
                } else {
                    return;
                }
            }
        }

        private void expect(char expected) {
            require(position < input.length() && input.charAt(position) == expected, "E_JSON_SYNTAX", "/");
            position++;
        }

        private boolean consume(char expected) {
            if (position < input.length() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private boolean peek(char expected) {
            return position < input.length() && input.charAt(position) == expected;
        }
    }
}
