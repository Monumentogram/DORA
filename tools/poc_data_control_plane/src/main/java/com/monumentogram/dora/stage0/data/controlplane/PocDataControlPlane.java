package com.monumentogram.dora.stage0.data.controlplane;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
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
            "5ce259432d77c8876f6b7c5e8ab6981d4312c1fbc18cdf6c84a97023d450ec72";
    public static final String SCHEMA_SHA256 =
            "b38ae362a5a804401878f56f139839dbd47009ec2fee59f1f05fdf08984b537e";

    private static final byte[] SENTINEL_BYTES =
            "DORA_STAGE0_SYNTHETIC_CONTROL_PLANE_SENTINEL_V1\n"
                    .getBytes(StandardCharsets.US_ASCII);
    private static final String SENTINEL_NAME = "dora-poc-data-control-plane.synthetic";
    private static final Pattern ID_16 =
            Pattern.compile("^(dataset|manifest|sample|delete|control)-[0-9a-f]{16}$");
    private static final Pattern SHA256 = Pattern.compile("^sha256:[0-9a-f]{64}$");
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
                    "CREATE_TRANSIENT_SYNTHETIC_SENTINEL",
                    "DELETE_TRANSIENT_SYNTHETIC_SENTINEL",
                    "EMIT_CONTENT_FREE_SUMMARY",
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
                    "CONTROL_PLANE_PREPARATION_ONLY",
                    "CUSTODIAN_UNASSIGNED",
                    "CONSENT_NOT_OPERATIONAL",
                    "CONTROLLED_STORAGE_NOT_CONFIGURED",
                    "SYNTHETIC_METADATA_ONLY",
                    "NO_SAMPLE_BYTES",
                    "NO_CORPUS_OR_DATASET_CREATED",
                    "NO_POC_DATA_READINESS_PASS_OR_EXECUTION",
                    "NO_REAL_PUBLIC_PRIVATE_OR_DERIVED_DATA",
                    "NO_NETWORK_CLOUD_MODEL_OR_TRAINING",
                    "NO_PRODUCTION_ADMISSION");

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
                    "OPERATOR_AND_CONTACT",
                    "NAMED_POC_AND_PURPOSE",
                    "SCRIPT_AND_INTENTIONAL_RECORDING",
                    "DERIVATIVES_AND_METRICS",
                    "RAW_AND_DERIVED_ACCESS_ROLES",
                    "HUMAN_REVIEW_DISCLOSURE",
                    "STORAGE_ENCRYPTION_RETENTION",
                    "PROVIDER_ARTIFACT_REGION_OR_NONE",
                    "WITHDRAWAL_AND_AGGREGATE_LIMIT",
                    "NO_TRAINING_OR_MODEL_IMPROVEMENT",
                    "VOLUNTARY_NO_PRODUCT_PENALTY",
                    "DELETION_EVIDENCE_AND_BACKUP_EXPIRY");

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
    private static final DryRunInterlock NOOP_DRY_RUN_INTERLOCK = new DryRunInterlock() {};

    private PocDataControlPlane() {}

    /** Package-private deterministic interlocks used only by adversarial local tests. */
    interface DryRunInterlock {
        default void afterManifestValidation() throws IOException {}

        default void beforeOwnedSentinelCreate(Path sentinel) throws IOException {}

        default void afterOwnedSentinelClose(Path sentinel) throws IOException {}
    }

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
        return validateOwnedManifest(owned);
    }

    private static ValidationReport validateOwnedManifest(byte[] owned) {
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
                "$defs",
                "$id",
                "$schema",
                "additionalProperties",
                "properties",
                "required",
                "title",
                "type",
                "x-dora-semantic-layer");
        requireString(root, "$schema", "https://json-schema.org/draft/2020-12/schema", "/$schema");
        requireString(root, "$id", "urn:dora:stage0:poc-data:control-plane:v0.1", "/$id");
        requireString(root, "type", "object", "/type");
        requireBoolean(root, "additionalProperties", false, "/additionalProperties");
        requireStrings(root.get("required"), ROOT_KEYS, "/required");
        requireString(
                root,
                "x-dora-semantic-layer",
                "PINNED_JAVA17_VALIDATOR_REQUIRED_FOR_CANONICAL_AND_CROSS_FIELD_RULES",
                "/x-dora-semantic-layer");
        Map<String, Object> properties = object(root.get("properties"), "/properties");
        requireExactKeys(properties, "/properties", ROOT_KEYS.toArray(String[]::new));
        Map<String, Object> definitions = object(root.get("$defs"), "/$defs");
        requireExactKeys(
                definitions,
                "/$defs",
                "controlRecord",
                "controlRecordId",
                "deletionEntry",
                "sha256",
                "timestamp");

        String digest = sha256(owned);
        require(SCHEMA_SHA256.equals(digest), "E_SCHEMA_PROFILE_DRIFT", "/");
        return digest;
    }

    /** Strictly parse and canonical-check an unpinned content-free JSON evidence document. */
    public static String validateCanonicalJson(byte[] raw) {
        byte[] owned = Objects.requireNonNull(raw, "raw").clone();
        parse(owned, true);
        return sha256(owned);
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
        byte[] owned = Objects.requireNonNull(raw, "raw").clone();
        return dryRunOwned(owned, temporaryRoot, NOOP_DRY_RUN_INTERLOCK);
    }

    static DryRunReport dryRunForTest(
            byte[] raw, Path temporaryRoot, DryRunInterlock interlock) throws IOException {
        byte[] owned = Objects.requireNonNull(raw, "raw").clone();
        return dryRunOwned(owned, temporaryRoot, Objects.requireNonNull(interlock, "interlock"));
    }

    private static DryRunReport dryRunOwned(
            byte[] owned, Path temporaryRoot, DryRunInterlock interlock) throws IOException {
        ValidationReport validation = validateOwnedManifest(owned);
        interlock.afterManifestValidation();
        Path root = Objects.requireNonNull(temporaryRoot, "temporaryRoot").toAbsolutePath().normalize();
        Path systemTemporaryRoot =
                Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        require(
                root.startsWith(systemTemporaryRoot)
                        && !root.equals(systemTemporaryRoot)
                        && Files.exists(root, LinkOption.NOFOLLOW_LINKS)
                        && Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(root),
                "E_TEMP_ROOT_UNSAFE",
                "/dryRun/tempRoot");
        try (Stream<Path> entries = Files.list(root)) {
            require(entries.findAny().isEmpty(), "E_TEMP_ROOT_NOT_EMPTY", "/dryRun/tempRoot");
        }

        requireAllowed("SYNTHETIC_DRY_RUN_OPERATOR", "READ_PUBLIC_MANIFEST");
        requireAllowed(
                "SYNTHETIC_DRY_RUN_OPERATOR", "CREATE_TRANSIENT_SYNTHETIC_SENTINEL");
        requireAllowed(
                "SYNTHETIC_DRY_RUN_OPERATOR", "DELETE_TRANSIENT_SYNTHETIC_SENTINEL");
        requireAllowed("SYNTHETIC_DRY_RUN_OPERATOR", "EMIT_CONTENT_FREE_SUMMARY");
        requireDenied("COLLECTOR", "APPROVE_REAL_COLLECTION");
        requireDenied("CUSTODIAN", "READ_RAW_OR_DERIVED_DATA");
        requireDenied("SYNTHETIC_DRY_RUN_OPERATOR", "CONFIGURE_CLOUD_TRANSFER");
        requireDenied("SYNTHETIC_DRY_RUN_OPERATOR", "RUN_MODEL_INFERENCE");
        requireDenied("SYNTHETIC_DRY_RUN_OPERATOR", "TRAIN_OR_IMPROVE_MODEL");

        String before = sha256(owned);
        Path sentinel = root.resolve(SENTINEL_NAME).normalize();
        require(
                sentinel.startsWith(root) && root.equals(sentinel.getParent()),
                "E_TEMP_TARGET_ESCAPE",
                "/dryRun/tempTarget");
        interlock.beforeOwnedSentinelCreate(sentinel);

        boolean ownedHandleOpened = false;
        try (FileChannel ownedSentinel =
                FileChannel.open(
                        sentinel,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.DELETE_ON_CLOSE,
                        LinkOption.NOFOLLOW_LINKS)) {
            ownedHandleOpened = true;
            writeAndVerifyOwnedSentinel(ownedSentinel);
        } catch (FileAlreadyExistsException conflict) {
            reject("E_SENTINEL_CREATE_CONFLICT", "/dryRun/sentinel");
        }
        require(ownedHandleOpened, "E_SENTINEL_CREATE", "/dryRun/sentinel");

        // DELETE_ON_CLOSE binds cleanup to the successfully created handle. Never delete by path:
        // a path present now can only be unverified or replacement state and must be preserved.
        interlock.afterOwnedSentinelClose(sentinel);
        requireSentinelAbsent(sentinel);
        requireSentinelAbsent(sentinel);

        String after = sha256(owned);
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

    private static void writeAndVerifyOwnedSentinel(FileChannel ownedSentinel) throws IOException {
        ByteBuffer writeBuffer = ByteBuffer.wrap(SENTINEL_BYTES);
        while (writeBuffer.hasRemaining()) {
            require(
                    ownedSentinel.write(writeBuffer) > 0,
                    "E_SENTINEL_WRITE",
                    "/dryRun/sentinel");
        }
        ownedSentinel.force(true);
        require(
                ownedSentinel.size() == SENTINEL_BYTES.length,
                "E_SENTINEL_VERIFY",
                "/dryRun/sentinel");

        ownedSentinel.position(0L);
        byte[] observed = new byte[SENTINEL_BYTES.length];
        ByteBuffer readBuffer = ByteBuffer.wrap(observed);
        while (readBuffer.hasRemaining()) {
            require(
                    ownedSentinel.read(readBuffer) > 0,
                    "E_SENTINEL_VERIFY",
                    "/dryRun/sentinel");
        }
        require(
                ownedSentinel.read(ByteBuffer.allocate(1)) == -1
                        && java.util.Arrays.equals(SENTINEL_BYTES, observed),
                "E_SENTINEL_VERIFY",
                "/dryRun/sentinel");
    }

    private static void requireSentinelAbsent(Path sentinel) throws IOException {
        try {
            Files.readAttributes(
                    sentinel,
                    java.nio.file.attribute.BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            reject("E_SENTINEL_UNOWNED_PATH", "/dryRun/sentinel");
        } catch (NoSuchFileException expected) {
            // Expected: the owned handle deleted only its own file on close.
        }
    }

    /** Command line entry point used by the evidence commands. */
    public static void main(String[] args) {
        System.exit(runCli(args, System.out, System.err));
    }

    /** Testable CLI entry point. */
    public static int runCli(String[] args, PrintStream out, PrintStream err) {
        if (args != null && args.length == 2 && "validate-json".equals(args[0])) {
            try {
                String digest = validateCanonicalJson(readBounded(Path.of(args[1])));
                out.println("LOCAL_PASS CANONICAL_JSON sha256=" + digest);
                return 0;
            } catch (ControlPlaneFault fault) {
                err.println("LOCAL_FAIL " + fault.code() + " " + fault.pointer());
                return 1;
            } catch (IOException | RuntimeException error) {
                err.println("LOCAL_FAIL E_READ_OR_INTERNAL /");
                return 3;
            }
        }
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
        appendPrettyCanonical(value, output, 0);
        output.append('\n');
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static final List<String> ROOT_KEYS =
            List.of(
                    "authorityFlags",
                    "blockers",
                    "collectionPlan",
                    "consentProcess",
                    "contractId",
                    "controlRecords",
                    "dataPolicy",
                    "deletionLedger",
                    "dryRunMatrix",
                    "limitations",
                    "manifestId",
                    "privacyBoundary",
                    "rbac",
                    "retention",
                    "schemaVersion");

    private static void validateManifestObject(Object parsed) {
        Map<String, Object> root = object(parsed, "/");
        requireExactKeys(root, "/", ROOT_KEYS.toArray(String[]::new));
        requireLong(root, "schemaVersion", 1L, "/schemaVersion");
        requireString(root, "contractId", "poc-data-control-plane-stage0-v0.1", "/contractId");
        requireStrings(root.get("blockers"), BLOCKERS, "/blockers");
        requireStrings(root.get("limitations"), LIMITATIONS, "/limitations");
        validateAuthorityFlags(root.get("authorityFlags"));
        validateCollectionPlan(root.get("collectionPlan"));
        validateConsent(root.get("consentProcess"));
        validateControlRecords(root.get("controlRecords"));
        validateDataPolicy(root.get("dataPolicy"));
        validateCurrentDeletionLedger(root.get("deletionLedger"));
        validateDryRunMatrix(root.get("dryRunMatrix"));
        requireString(
                root,
                "manifestId",
                "control-manifest-0123456789abcdef",
                "/manifestId");
        validatePrivacyBoundary(root.get("privacyBoundary"));
        validateRbac(root.get("rbac"));
        validateRetention(root.get("retention"));
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
        expected.put("mergeAllowedByThisPackage", false);
        expected.put("modelImprovementAllowed", false);
        expected.put("modelInferenceAllowed", false);
        expected.put("networkExecutionAllowed", false);
        expected.put("pocDataPassAllowed", false);
        expected.put("pocDataReadyAllowed", false);
        expected.put("productionAdmissionAllowed", false);
        expected.put("productionSchemaAllowed", false);
        expected.put("productionStorageAllowed", false);
        expected.put("publicationAllowedByThisPackage", false);
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
                "clockSource",
                "dataClasses",
                "frozenAt",
                "hypothesis",
                "manifestRowCount",
                "meetingCount",
                "modelExecutionEnabled",
                "networkEnabled",
                "nonGoals",
                "participantCount",
                "planId",
                "seed",
                "state",
                "storageClasses",
                "voiceCount");
        requireString(
                plan,
                "clockSource",
                "FROZEN_LITERAL_NO_WALL_CLOCK_READ",
                "/collectionPlan/clockSource");
        requireStrings(
                plan.get("dataClasses"),
                List.of("GENERATED_TEXT_METADATA", "SYNTHETIC_SIGNAL_METADATA"),
                "/collectionPlan/dataClasses");
        requireTimestamp(plan.get("frozenAt"), "/collectionPlan/frozenAt");
        requireString(plan, "frozenAt", "2026-08-18T12:00:00Z", "/collectionPlan/frozenAt");
        requireString(
                plan,
                "hypothesis",
                "FAIL_CLOSED_SYNTHETIC_CONTROL_PLANE_IS_DETERMINISTIC",
                "/collectionPlan/hypothesis");
        requireLong(plan, "manifestRowCount", 4L, "/collectionPlan/manifestRowCount");
        requireLong(plan, "meetingCount", 0L, "/collectionPlan/meetingCount");
        requireBoolean(
                plan, "modelExecutionEnabled", false, "/collectionPlan/modelExecutionEnabled");
        requireBoolean(plan, "networkEnabled", false, "/collectionPlan/networkEnabled");
        requireStrings(
                plan.get("nonGoals"),
                List.of(
                        "CORPUS_QUALITY",
                        "CONSENT_USABILITY",
                        "ACOUSTIC_COVERAGE",
                        "MODEL_QUALITY",
                        "DEVICE_BEHAVIOR",
                        "CONTROLLED_STORE_OPERATIONS",
                        "PRODUCTION_BEHAVIOR"),
                "/collectionPlan/nonGoals");
        requireLong(plan, "participantCount", 0L, "/collectionPlan/participantCount");
        requireString(
                plan,
                "planId",
                "poc-data-synthetic-control-plane-plan-stage0-v0.1",
                "/collectionPlan/planId");
        requireLong(plan, "seed", 2026081801L, "/collectionPlan/seed");
        requireString(
                plan,
                "state",
                "AUTHORIZED_SYNTHETIC_DRY_RUN_ONLY",
                "/collectionPlan/state");
        requireStrings(
                plan.get("storageClasses"),
                List.of(
                        "PUBLIC_REPOSITORY_MANIFEST_ONLY",
                        "VALIDATED_OS_TEMP_SENTINEL_ONLY"),
                "/collectionPlan/storageClasses");
        requireLong(plan, "voiceCount", 0L, "/collectionPlan/voiceCount");
    }

    private static void validateConsent(Object value) {
        Map<String, Object> consent = object(value, "/consentProcess");
        requireExactKeys(
                consent,
                "/consentProcess",
                "consentReference",
                "finalLegalCopyApproved",
                "processId",
                "purposeRecordedConsentUsable",
                "realConsentRecordCount",
                "requirements",
                "state",
                "stateMachine");
        requireString(consent, "consentReference", "not-applicable", "/consentProcess/consentReference");
        requireBoolean(
                consent,
                "finalLegalCopyApproved",
                false,
                "/consentProcess/finalLegalCopyApproved");
        requireString(
                consent,
                "processId",
                "poc-data-consent-process-stage0-v0.1",
                "/consentProcess/processId");
        requireBoolean(
                consent,
                "purposeRecordedConsentUsable",
                false,
                "/consentProcess/purposeRecordedConsentUsable");
        requireLong(
                consent,
                "realConsentRecordCount",
                0L,
                "/consentProcess/realConsentRecordCount");
        requireStrings(consent.get("requirements"), CONSENT_ELEMENTS, "/consentProcess/requirements");
        requireString(
                consent,
                "state",
                "PREPARED_NOT_OPERATIONAL",
                "/consentProcess/state");
        requireStrings(
                consent.get("stateMachine"),
                List.of(
                        "DRAFT",
                        "REVIEW_REQUIRED",
                        "APPROVED_FOR_NAMED_PLAN",
                        "GRANTED",
                        "REVOKED",
                        "EXPIRED"),
                "/consentProcess/stateMachine");
    }

    private static void validateControlRecords(Object value) {
        List<Object> records = array(value, "/controlRecords");
        require(records.size() == 4, "E_RECORD_COUNT", "/controlRecords");
        List<String> ids =
                List.of(
                        "control-1111111111111111",
                        "control-2222222222222222",
                        "control-3333333333333333",
                        "control-4444444444444444");
        List<String> classes =
                List.of(
                        "GENERATED_TEXT_METADATA",
                        "GENERATED_TEXT_METADATA",
                        "SYNTHETIC_SIGNAL_METADATA",
                        "GENERATED_TEXT_METADATA");
        List<String> states = List.of("ACTIVE", "ACTIVE", "DELETED", "ACTIVE");
        List<String> created =
                List.of(
                        "2026-08-18T12:00:00Z",
                        "2026-08-18T12:01:00Z",
                        "2026-08-18T12:00:00Z",
                        "2026-08-18T12:00:00Z");
        List<String> expires =
                List.of(
                        "2026-08-18T12:30:00Z",
                        "2026-08-18T12:20:00Z",
                        "2026-08-18T12:10:00Z",
                        "2026-08-18T12:15:00Z");
        List<String> notes =
                List.of(
                        "ROOT_SYNTHETIC_CONTROL_ENTRY",
                        "DERIVED_SYNTHETIC_CONTROL_ENTRY",
                        "SYNTHETIC_DELETION_TARGET",
                        "PROTECTED_EVALUATION_CONTROL_ENTRY");
        List<String> splits = List.of("development", "development", "test", "evaluation");
        List<List<String>> roles =
                List.of(
                        List.of("SYNTHETIC_DRY_RUN_OPERATOR", "SECURITY_AUDITOR", "EVALUATOR"),
                        List.of("SYNTHETIC_DRY_RUN_OPERATOR", "SECURITY_AUDITOR"),
                        List.of("SYNTHETIC_DRY_RUN_OPERATOR"),
                        List.of("SECURITY_AUDITOR", "EVALUATOR"));
        Set<String> observed = new HashSet<>();
        for (int index = 0; index < records.size(); index++) {
            String pointer = "/controlRecords/" + index;
            Map<String, Object> record = object(records.get(index), pointer);
            requireExactKeys(
                    record,
                    pointer,
                    "accessRoles",
                    "containsPersonalData",
                    "containsSampleBytes",
                    "containsTranscriptOrSourceExcerpt",
                    "contentSha256",
                    "createdAt",
                    "dataClass",
                    "deletionState",
                    "evidenceLocator",
                    "expiresAt",
                    "note",
                    "parentRecordId",
                    "recordId",
                    "split",
                    "storageClass");
            String id = string(record.get("recordId"), pointer + "/recordId");
            requireId(id, "control", pointer + "/recordId");
            require(ids.get(index).equals(id), "E_RECORD_ORDER", pointer + "/recordId");
            require(observed.add(id), "E_RECORD_DUPLICATE", pointer + "/recordId");
            requireStrings(record.get("accessRoles"), roles.get(index), pointer + "/accessRoles");
            requireBoolean(record, "containsPersonalData", false, pointer + "/containsPersonalData");
            requireBoolean(record, "containsSampleBytes", false, pointer + "/containsSampleBytes");
            requireBoolean(
                    record,
                    "containsTranscriptOrSourceExcerpt",
                    false,
                    pointer + "/containsTranscriptOrSourceExcerpt");
            require(record.get("contentSha256") == null, "E_CONTENT_DIGEST_PRESENT", pointer + "/contentSha256");
            require(record.get("evidenceLocator") == null, "E_PRIVATE_LOCATOR_PRESENT", pointer + "/evidenceLocator");
            requireTimestamp(record.get("createdAt"), pointer + "/createdAt");
            requireTimestamp(record.get("expiresAt"), pointer + "/expiresAt");
            requireString(record, "createdAt", created.get(index), pointer + "/createdAt");
            requireString(record, "expiresAt", expires.get(index), pointer + "/expiresAt");
            requireString(record, "dataClass", classes.get(index), pointer + "/dataClass");
            requireString(record, "deletionState", states.get(index), pointer + "/deletionState");
            requireString(record, "note", notes.get(index), pointer + "/note");
            requireString(record, "split", splits.get(index), pointer + "/split");
            requireString(
                    record,
                    "storageClass",
                    "MANIFEST_ONLY_NO_SAMPLE_BYTES",
                    pointer + "/storageClass");
            Object parent = record.get("parentRecordId");
            if (index == 1) {
                require(
                        "control-1111111111111111".equals(parent),
                        "E_LINEAGE",
                        pointer + "/parentRecordId");
                require(
                        new HashSet<>(roles.get(0)).containsAll(roles.get(1)),
                        "E_ACCESS_WIDENING",
                        pointer + "/accessRoles");
                require(
                        splits.get(0).equals(splits.get(1)),
                        "E_SPLIT_LINEAGE",
                        pointer + "/split");
            } else {
                require(parent == null, "E_LINEAGE", pointer + "/parentRecordId");
            }
        }
    }

    private static void validateDataPolicy(Object value) {
        Map<String, Object> policy = object(value, "/dataPolicy");
        requireExactKeys(
                policy,
                "/dataPolicy",
                "allowedClasses",
                "allowedUses",
                "excludedUses",
                "forbiddenClasses",
                "licenseId",
                "publicRedistributionAllowed",
                "termsDigest",
                "trainingAllowed");
        requireStrings(
                policy.get("allowedClasses"),
                List.of("GENERATED_TEXT_METADATA", "SYNTHETIC_SIGNAL_METADATA"),
                "/dataPolicy/allowedClasses");
        requireStrings(
                policy.get("allowedUses"),
                List.of("CONTROL_PLANE_VALIDATION", "TRANSIENT_SENTINEL_DELETION_DRY_RUN"),
                "/dataPolicy/allowedUses");
        requireStrings(
                policy.get("excludedUses"),
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
                        "CROSS_PROJECT_OR_COMPANY_REUSE"),
                "/dataPolicy/excludedUses");
        requireStrings(
                policy.get("forbiddenClasses"),
                List.of("PURPOSE_RECORDED", "PUBLIC_LICENSED", "REAL_MEETING", "DERIVED_SENSITIVE"),
                "/dataPolicy/forbiddenClasses");
        requireString(
                policy,
                "licenseId",
                "DORA_ORIGINAL_SYNTHETIC_CONTROL_METADATA_ONLY",
                "/dataPolicy/licenseId");
        requireBoolean(
                policy,
                "publicRedistributionAllowed",
                false,
                "/dataPolicy/publicRedistributionAllowed");
        String termsDigest = string(policy.get("termsDigest"), "/dataPolicy/termsDigest");
        require(SHA256.matcher(termsDigest).matches(), "E_SHA256", "/dataPolicy/termsDigest");
        require(
                "sha256:26726af9ea7a196b5aa940d358ccdc45a8566b01e9588ecf2af71f9e3f8ade0d"
                        .equals(termsDigest),
                "E_VALUE",
                "/dataPolicy/termsDigest");
        requireBoolean(policy, "trainingAllowed", false, "/dataPolicy/trainingAllowed");
    }

    private static void validateCurrentDeletionLedger(Object value) {
        List<Object> ledger = array(value, "/deletionLedger");
        require(ledger.size() == 1, "E_DELETION_LEDGER", "/deletionLedger");
        Map<String, Object> entry = object(ledger.get(0), "/deletionLedger/0");
        requireExactKeys(
                entry,
                "/deletionLedger/0",
                "affectedScopes",
                "backupExpiresAt",
                "completedAt",
                "eventId",
                "outcome",
                "recordId",
                "requestedAt",
                "trigger",
                "unresolvedFailures");
        requireStrings(entry.get("affectedScopes"), DELETION_SCOPES, "/deletionLedger/0/affectedScopes");
        requireString(entry, "backupExpiresAt", "2026-08-18T12:02:00Z", "/deletionLedger/0/backupExpiresAt");
        requireString(entry, "completedAt", "2026-08-18T12:02:00Z", "/deletionLedger/0/completedAt");
        requireString(entry, "requestedAt", "2026-08-18T12:01:00Z", "/deletionLedger/0/requestedAt");
        requireTimestamp(entry.get("backupExpiresAt"), "/deletionLedger/0/backupExpiresAt");
        requireTimestamp(entry.get("completedAt"), "/deletionLedger/0/completedAt");
        requireTimestamp(entry.get("requestedAt"), "/deletionLedger/0/requestedAt");
        requireString(entry, "eventId", "delete-3333333333333333", "/deletionLedger/0/eventId");
        requireId(entry.get("eventId"), "delete", "/deletionLedger/0/eventId");
        requireString(entry, "recordId", "control-3333333333333333", "/deletionLedger/0/recordId");
        requireString(entry, "outcome", "DELETED_METADATA_ONLY", "/deletionLedger/0/outcome");
        requireString(entry, "trigger", "SYNTHETIC_DRY_RUN", "/deletionLedger/0/trigger");
        require(
                array(entry.get("unresolvedFailures"), "/deletionLedger/0/unresolvedFailures").isEmpty(),
                "E_DELETION_FAILURES",
                "/deletionLedger/0/unresolvedFailures");
    }

    private static void validateDryRunMatrix(Object value) {
        List<Object> matrix = array(value, "/dryRunMatrix");
        require(matrix.size() == 15, "E_DRY_RUN_MATRIX", "/dryRunMatrix");
        for (int index = 0; index < matrix.size(); index++) {
            String pointer = "/dryRunMatrix/" + index;
            Map<String, Object> row = object(matrix.get(index), pointer);
            requireExactKeys(row, pointer, "expected", "id");
            requireString(row, "id", SCENARIO_IDS.get(index), pointer + "/id");
            String expected = index < 5 || (index >= 9 && index < 12)
                    ? "ALLOW"
                    : index < 9 ? "DENY" : "REJECT";
            requireString(row, "expected", expected, pointer + "/expected");
        }
    }

    private static void validatePrivacyBoundary(Object value) {
        Map<String, Object> boundary = object(value, "/privacyBoundary");
        List<String> keys =
                List.of(
                        "containsConsentForm",
                        "containsDeviceOrAccountIdentifier",
                        "containsNetworkEndpoint",
                        "containsParticipantMapping",
                        "containsPersonalData",
                        "containsPrivateLocator",
                        "containsPublicLinkableContentDigest",
                        "containsRawContent",
                        "containsSignedUrl",
                        "containsTranscriptOrSourceExcerpt",
                        "sampleBytesPresent");
        requireExactKeys(boundary, "/privacyBoundary", keys.toArray(String[]::new));
        for (String key : keys) {
            requireBoolean(boundary, key, false, "/privacyBoundary/" + key);
        }
    }

    private static void validateRbac(Object value) {
        Map<String, Object> rbac = object(value, "/rbac");
        requireExactKeys(
                rbac,
                "/rbac",
                "assignments",
                "custodianAssignment",
                "mode",
                "roleRules");
        requireString(rbac, "mode", "DENY_BY_DEFAULT", "/rbac/mode");
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

        Map<String, Object> custodian =
                object(rbac.get("custodianAssignment"), "/rbac/custodianAssignment");
        requireExactKeys(custodian, "/rbac/custodianAssignment", "principal", "state");
        require(
                custodian.get("principal") == null,
                "E_CUSTODIAN_ASSIGNED",
                "/rbac/custodianAssignment/principal");
        requireString(
                custodian,
                "state",
                CUSTODIAN_ASSIGNMENT,
                "/rbac/custodianAssignment/state");

        List<Object> rules = array(rbac.get("roleRules"), "/rbac/roleRules");
        require(rules.size() == ROLES.size(), "E_ROLE_RULE_COUNT", "/rbac/roleRules");
        List<List<String>> expectedActions =
                List.of(
                        List.of(
                                "READ_PUBLIC_MANIFEST",
                                "CREATE_TRANSIENT_SYNTHETIC_SENTINEL",
                                "DELETE_TRANSIENT_SYNTHETIC_SENTINEL",
                                "EMIT_CONTENT_FREE_SUMMARY"),
                        List.of("READ_PUBLIC_MANIFEST", "READ_CONTENT_FREE_EVIDENCE"),
                        List.of("READ_PUBLIC_MANIFEST"),
                        List.of(),
                        List.of(),
                        List.of());
        for (int index = 0; index < rules.size(); index++) {
            String pointer = "/rbac/roleRules/" + index;
            Map<String, Object> rule = object(rules.get(index), pointer);
            requireExactKeys(rule, pointer, "actions", "role");
            requireString(rule, "role", ROLES.get(index), pointer + "/role");
            requireStrings(rule.get("actions"), expectedActions.get(index), pointer + "/actions");
        }
    }

    private static void validateRetention(Object value) {
        Map<String, Object> retention = object(value, "/retention");
        requireExactKeys(
                retention,
                "/retention",
                "controlledStoreDeletionDryRunCompleted",
                "physicalOverwritePromised",
                "purposeRecordedDerivedMaxDays",
                "purposeRecordedRawMaxDays",
                "shorterMandatoryTermWins",
                "syntheticTempDeletionDryRunRequired",
                "transientSentinelRetention",
                "withdrawalCompletionMaxDays");
        requireBoolean(
                retention,
                "controlledStoreDeletionDryRunCompleted",
                false,
                "/retention/controlledStoreDeletionDryRunCompleted");
        requireBoolean(
                retention,
                "physicalOverwritePromised",
                false,
                "/retention/physicalOverwritePromised");
        requireLong(
                retention,
                "purposeRecordedDerivedMaxDays",
                180L,
                "/retention/purposeRecordedDerivedMaxDays");
        requireLong(
                retention,
                "purposeRecordedRawMaxDays",
                90L,
                "/retention/purposeRecordedRawMaxDays");
        requireBoolean(
                retention,
                "shorterMandatoryTermWins",
                true,
                "/retention/shorterMandatoryTermWins");
        requireBoolean(
                retention,
                "syntheticTempDeletionDryRunRequired",
                true,
                "/retention/syntheticTempDeletionDryRunRequired");
        requireString(
                retention,
                "transientSentinelRetention",
                "DELETE_BEFORE_OPERATION_RETURNS",
                "/retention/transientSentinelRetention");
        requireLong(
                retention,
                "withdrawalCompletionMaxDays",
                30L,
                "/retention/withdrawalCompletionMaxDays");
    }

    private static void requireAllowed(String role, String action) {
        require(authorize(role, action).allowed(), "E_RBAC_EXPECTED_ALLOW", "/rbac");
    }

    private static void requireDenied(String role, String action) {
        require(!authorize(role, action).allowed(), "E_RBAC_EXPECTED_DENY", "/rbac");
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
        result.put(
                "CREATE_TRANSIENT_SYNTHETIC_SENTINEL",
                List.of("SYNTHETIC_DRY_RUN_OPERATOR"));
        result.put(
                "DELETE_TRANSIENT_SYNTHETIC_SENTINEL",
                List.of("SYNTHETIC_DRY_RUN_OPERATOR"));
        result.put("EMIT_CONTENT_FREE_SUMMARY", List.of("SYNTHETIC_DRY_RUN_OPERATOR"));
        result.put(
                "READ_CONTENT_FREE_EVIDENCE",
                List.of("SECURITY_AUDITOR"));
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
                    int codePoint = Character.toCodePoint(current, text.charAt(index + 1));
                    require(
                            Character.getType(codePoint) != Character.FORMAT,
                            "E_JSON_UNICODE",
                            "/");
                    index++;
                } else if (Character.isLowSurrogate(current)) {
                    reject("E_JSON_UNICODE", "/");
                } else {
                    require(
                            Character.getType(current) != Character.FORMAT,
                            "E_JSON_UNICODE",
                            "/");
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

    private static void appendPrettyCanonical(Object value, StringBuilder output, int depth) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String text) {
            appendString(text, output);
        } else if (value instanceof Boolean bool) {
            output.append(bool.booleanValue() ? "true" : "false");
        } else if (value instanceof Long number) {
            output.append(number.longValue());
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                output.append("[]");
                return;
            }
            output.append("[\n");
            for (int index = 0; index < list.size(); index++) {
                appendIndent(output, depth + 1);
                appendPrettyCanonical(list.get(index), output, depth + 1);
                if (index + 1 < list.size()) {
                    output.append(',');
                }
                output.append('\n');
            }
            appendIndent(output, depth);
            output.append(']');
        } else if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                require(entry.getKey() instanceof String, "E_JSON_OBJECT_KEY", "/");
                sorted.put((String) entry.getKey(), entry.getValue());
            }
            if (sorted.isEmpty()) {
                output.append("{}");
                return;
            }
            output.append("{\n");
            int index = 0;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                appendIndent(output, depth + 1);
                appendString(entry.getKey(), output);
                output.append(": ");
                appendPrettyCanonical(entry.getValue(), output, depth + 1);
                if (++index < sorted.size()) {
                    output.append(',');
                }
                output.append('\n');
            }
            appendIndent(output, depth);
            output.append('}');
        } else {
            reject("E_JSON_VALUE", "/");
        }
    }

    private static void appendIndent(StringBuilder output, int depth) {
        output.append("  ".repeat(depth));
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
