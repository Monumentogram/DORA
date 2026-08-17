package com.monumentogram.dora.stage0.data.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Strict validator for a disposable, non-normative Stage 0 synthetic-public metadata profile.
 *
 * <p>This class is not a general JSON Schema implementation. It does not authorize or validate
 * human data collection, private storage, model training, redistribution, device execution, or a
 * PoC verdict.
 */
public final class SyntheticManifestValidator {
    public static final int MAX_INPUT_BYTES = 524_288;
    public static final int MAX_JSON_DEPTH = 32;
    public static final String PASS_MARKER = "LOCAL_PASS poc-data-synthetic-manifest-validator";
    public static final String PROFILE_ID =
            "poc-data-synthetic-public-projection-stage0-v0.1";
    public static final String SCHEMA_URN =
            "urn:dora:stage0:poc-data:synthetic-public-projection:v0.1";

    public static final List<String> POC_IDS =
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
    public static final List<String> ALLOWED_USES =
            List.of(
                    "CAPTURE_SAMPLE_INTEGRITY_AND_RECOVERY_TESTING",
                    "VAD_SILENCE_AND_MAX_CAP_EVALUATION",
                    "ASR_RU_EN_MIXED_EVALUATION",
                    "DIARIZATION_AND_CORRECTION_LOAD_EVALUATION",
                    "DECISION_TASK_SOURCE_GROUNDING_EVALUATION",
                    "OFFLINE_VPN_TRANSPORT_TESTING",
                    "BATTERY_THERMAL_CALIBRATION",
                    "SEARCH_SCALE_TESTING");
    public static final List<String> EXCLUDED_USES =
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
    public static final List<String> LIMITATIONS =
            List.of(
                    "NON_NORMATIVE_MINIMUM_PROFILE",
                    "NO_DURABLE_OR_GOVERNED_DATASET_OR_CORPUS_MANIFEST_CREATED",
                    "SYNTHETIC_METADATA_ONLY",
                    "NO_SAMPLE_BYTES",
                    "NO_COLLECTION_STORAGE_ACCESS_OR_CONSENT_EVIDENCE",
                    "NO_POC_PASS_OR_PRODUCTION_ADMISSION");

    private static final List<String> DATA_CLASSES =
            List.of("SYNTHETIC_SIGNAL", "GENERATED_TEXT");
    private static final List<String> LANGUAGE_SLICES =
            List.of("ru", "en", "mixed-ru-en", "non-speech");
    private static final List<String> CONDITIONS =
            List.of(
                    "SYNTHETIC_SILENCE",
                    "SYNTHETIC_TONE",
                    "SYNTHETIC_BOUNDED_NOISE",
                    "NOT_APPLICABLE_TEXT");
    private static final List<String> DISTANCES =
            List.of("SYNTHETIC_NEAR_FIELD", "SYNTHETIC_FAR_FIELD", "NOT_APPLICABLE");
    private static final List<String> ROUTES =
            List.of("SYNTHETIC_BUILT_IN_MIC", "SYNTHETIC_HEADSET", "NOT_APPLICABLE");
    private static final List<String> OVERLAPS =
            List.of("NONE", "SYNTHETIC_OVERLAP", "NOT_APPLICABLE");
    private static final List<String> SPEAKER_BUCKETS =
            List.of("0", "1", "2", "3", "4", "5-6", "not-applicable");
    private static final List<String> SPLITS = List.of("development", "test", "evaluation");
    private static final List<String> ACCESS_ROLES =
            List.of("generator", "qa-evaluator", "security-auditor");
    private static final List<String> DELETION_STATES =
            List.of("active", "withdrawal-pending", "expired", "deleted", "quarantined");
    private static final List<String> NOTE_CODES =
            List.of(
                    "ROOT_SYNTHETIC_ENTRY",
                    "DERIVED_SYNTHETIC_VARIANT",
                    "DELETION_DRY_RUN_TARGET");

    private static final Set<String> ROOT_FIELDS =
            Set.of(
                    "schemaVersion",
                    "profileId",
                    "manifestId",
                    "datasetId",
                    "version",
                    "purpose",
                    "dataClass",
                    "origin",
                    "licenseId",
                    "termsDigest",
                    "consentReference",
                    "trainingAllowed",
                    "publicRedistributionAllowed",
                    "samples",
                    "deletionLedger",
                    "publicProjection",
                    "limitations");
    private static final Set<String> PURPOSE_FIELDS =
            Set.of("pocIds", "allowedUses", "excludedUses");
    private static final Set<String> ORIGIN_FIELDS =
            Set.of("type", "generatorId", "generatorVersion", "seed", "sourceReference");
    private static final Set<String> SAMPLE_FIELDS =
            Set.of(
                    "sampleId",
                    "contentSha256",
                    "languageSlice",
                    "acousticSlice",
                    "speakerCountBucket",
                    "split",
                    "parentSampleId",
                    "storageClass",
                    "evidenceLocator",
                    "accessRoles",
                    "createdAt",
                    "expiresAt",
                    "deletionState",
                    "notes");
    private static final Set<String> ACOUSTIC_FIELDS =
            Set.of("condition", "distance", "route", "overlap");
    private static final Set<String> DELETION_FIELDS =
            Set.of(
                    "eventId",
                    "sampleId",
                    "trigger",
                    "requestedAt",
                    "completedAt",
                    "backupExpiresAt",
                    "affectedScopes",
                    "unresolvedFailures",
                    "outcome");
    private static final Set<String> PUBLIC_FIELDS =
            Set.of(
                    "containsRawContent",
                    "containsTranscriptOrSourceExcerpt",
                    "containsPersonalData",
                    "containsParticipantMapping",
                    "containsConsentForm",
                    "containsPrivateLocator",
                    "containsSignedUrl",
                    "containsDeviceOrAccountIdentifier",
                    "containsPublicLinkableContentDigest");

    private static final Map<String, String> POC_TO_USE = createPocToUse();
    private static final Pattern MANIFEST_ID = Pattern.compile("manifest-[0-9a-f]{16}");
    private static final Pattern DATASET_ID = Pattern.compile("dataset-[0-9a-f]{16}");
    private static final Pattern SAMPLE_ID = Pattern.compile("sample-[0-9a-f]{16}");
    private static final Pattern EVENT_ID = Pattern.compile("delete-[0-9a-f]{16}");
    private static final Pattern SEMVER =
            Pattern.compile("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)");
    private static final Pattern SHA256 = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Pattern TIMESTAMP =
            Pattern.compile(
                    "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z");
    private SyntheticManifestValidator() {}

    /** Validate canonical manifest bytes without retaining caller-owned mutable state. */
    public static void validate(byte[] input) {
        Objects.requireNonNull(input, "input");
        byte[] owned = input.clone();
        Object parsed = parse(owned, true);
        validateManifest(parsed);
    }

    /** Verify selected fixed schema and catalog bindings without acting as a schema engine. */
    public static void validateProfileSchema(byte[] input) {
        Objects.requireNonNull(input, "input");
        Map<String, Object> schema = object(parse(input.clone(), false), "/");
        if (!SCHEMA_URN.equals(string(schema.get("$id"), "/$id"))) {
            reject("E_SCHEMA_PROFILE_DRIFT", "/$id");
        }
        if (!"PINNED_JAVA_VALIDATOR_REQUIRED_FOR_CANONICAL_ORDER_AND_CROSS_FIELD_RULES"
                .equals(
                        string(
                                schema.get("x-dora-semantic-layer"),
                                "/x-dora-semantic-layer"))) {
            reject("E_SCHEMA_PROFILE_DRIFT", "/x-dora-semantic-layer");
        }
        if (!ROOT_FIELDS.equals(new HashSet<>(stringList(schema.get("required"), "/required")))) {
            reject("E_SCHEMA_PROFILE_DRIFT", "/required");
        }
        Map<String, Object> properties = object(schema.get("properties"), "/properties");
        requireSchemaPattern(
                properties,
                "version",
                "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$",
                "/properties/version");
        if (!PROFILE_ID.equals(
                string(
                        object(properties.get("profileId"), "/properties/profileId").get("const"),
                        "/properties/profileId/const"))) {
            reject("E_SCHEMA_PROFILE_DRIFT", "/properties/profileId");
        }
        requireSchemaList(
                object(properties.get("dataClass"), "/properties/dataClass").get("enum"),
                DATA_CLASSES,
                "/properties/dataClass/enum");
        Map<String, Object> defs = object(schema.get("$defs"), "/$defs");
        requireSchemaPattern(
                defs,
                "sha256",
                "^sha256:(?!0{64}$)[0-9a-f]{64}$",
                "/$defs/sha256");
        Map<String, Object> purpose =
                object(
                        object(defs.get("purpose"), "/$defs/purpose").get("properties"),
                        "/$defs/purpose/properties");
        requireSchemaNestedList(purpose, "pocIds", POC_IDS, "/$defs/purpose/properties/pocIds");
        requireSchemaNestedList(
                purpose, "allowedUses", ALLOWED_USES, "/$defs/purpose/properties/allowedUses");
        requireSchemaNestedList(
                purpose, "excludedUses", EXCLUDED_USES, "/$defs/purpose/properties/excludedUses");
        Map<String, Object> deletion =
                object(
                        object(defs.get("deletionEntry"), "/$defs/deletionEntry").get("properties"),
                        "/$defs/deletionEntry/properties");
        requireSchemaNestedList(
                deletion,
                "affectedScopes",
                DELETION_SCOPES,
                "/$defs/deletionEntry/properties/affectedScopes");
        requireSchemaList(
                object(properties.get("limitations"), "/properties/limitations")
                        .get("items")
                        instanceof Map<?, ?> items
                        ? object(items, "/properties/limitations/items").get("enum")
                        : null,
                LIMITATIONS,
                "/properties/limitations/items/enum");
        Map<String, Object> origin =
                object(
                        object(defs.get("origin"), "/$defs/origin").get("properties"),
                        "/$defs/origin/properties");
        requireSchemaPattern(
                origin,
                "generatorVersion",
                "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$",
                "/$defs/origin/generatorVersion");
        if (!"poc-data-java17-in-memory-fixture-generator"
                        .equals(
                                string(
                                        object(origin.get("generatorId"), "/$defs/origin/generatorId")
                                                .get("const"),
                                        "/$defs/origin/generatorId/const"))
                || !"tools/poc_data_manifest_validator/src/test/java/com/monumentogram/dora/stage0/data/manifest/SyntheticManifestValidatorTest.java"
                        .equals(
                                string(
                                        object(
                                                        origin.get("sourceReference"),
                                                        "/$defs/origin/sourceReference")
                                                .get("const"),
                                        "/$defs/origin/sourceReference/const"))) {
            reject("E_SCHEMA_PROFILE_DRIFT", "/$defs/origin");
        }
    }

    /** Content-free CLI. The input file is read only and no output file is created. */
    public static void main(String[] args) {
        int exit = runCli(args, System.out, System.err);
        if (exit != 0) {
            System.exit(exit);
        }
    }

    static int runCli(String[] args, PrintStream out, PrintStream err) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        if (args.length != 1) {
            err.println("LOCAL_FAIL E_USAGE /");
            return 2;
        }
        try {
            validate(readBounded(Path.of(args[0])));
            out.println(PASS_MARKER);
            return 0;
        } catch (ManifestFault fault) {
            err.println("LOCAL_FAIL " + fault.code() + " " + fault.pointer());
            return 1;
        } catch (IOException | RuntimeException error) {
            err.println("LOCAL_FAIL E_READ_OR_INTERNAL /");
            return 3;
        }
    }

    private static byte[] readBounded(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return input.readNBytes(MAX_INPUT_BYTES + 1);
        }
    }

    /** Return canonical UTF-8 JSON bytes for test and tooling callers. */
    public static byte[] canonicalBytes(Object value) {
        StringBuilder out = new StringBuilder();
        appendCanonical(value, out);
        out.append('\n');
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, String> createPocToUse() {
        Map<String, String> result = new HashMap<>();
        result.put("POC-CAPTURE-001", ALLOWED_USES.get(0));
        result.put("POC-RECOVERY-001", ALLOWED_USES.get(0));
        result.put("POC-VAD-001", ALLOWED_USES.get(1));
        result.put("POC-ASR-001", ALLOWED_USES.get(2));
        result.put("POC-DIAR-001", ALLOWED_USES.get(3));
        result.put("POC-DECISION-001", ALLOWED_USES.get(4));
        result.put("POC-OFFLINE-001", ALLOWED_USES.get(5));
        result.put("POC-VPN-001", ALLOWED_USES.get(5));
        result.put("POC-BATTERY-001", ALLOWED_USES.get(6));
        result.put("POC-SEARCH-001", ALLOWED_USES.get(7));
        return Collections.unmodifiableMap(result);
    }

    private static void requireSchemaNestedList(
            Map<String, Object> parent, String key, List<String> expected, String pointer) {
        Map<String, Object> property = object(parent.get(key), pointer);
        Map<String, Object> items = object(property.get("items"), pointer + "/items");
        requireSchemaList(items.get("enum"), expected, pointer + "/items/enum");
    }

    private static void requireSchemaPattern(
            Map<String, Object> parent, String key, String expected, String pointer) {
        Map<String, Object> property = object(parent.get(key), pointer);
        if (!expected.equals(string(property.get("pattern"), pointer + "/pattern"))) {
            reject("E_SCHEMA_PROFILE_DRIFT", pointer + "/pattern");
        }
    }

    private static void requireSchemaList(Object value, List<String> expected, String pointer) {
        if (!expected.equals(stringList(value, pointer))) {
            reject("E_SCHEMA_PROFILE_DRIFT", pointer);
        }
    }

    private static Object parse(byte[] raw, boolean requireCanonical) {
        if (raw.length == 0) {
            reject("E_JSON_SYNTAX", "/");
        }
        if (raw.length > MAX_INPUT_BYTES) {
            reject("E_INPUT_TOO_LARGE", "/");
        }
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

    private static boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (source[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static void validateManifest(Object value) {
        Map<String, Object> root = exactObject(value, ROOT_FIELDS, "/");
        if (integer(root.get("schemaVersion"), "/schemaVersion") != 1L
                || !PROFILE_ID.equals(string(root.get("profileId"), "/profileId"))) {
            reject("E_SCHEMA_VERSION", "/");
        }
        opaque(root.get("manifestId"), MANIFEST_ID, "/manifestId");
        opaque(root.get("datasetId"), DATASET_ID, "/datasetId");
        if (!SEMVER.matcher(string(root.get("version"), "/version")).matches()) {
            reject("E_VERSION_FORMAT", "/version");
        }
        validatePurpose(root.get("purpose"));
        String dataClass = catalogue(root.get("dataClass"), DATA_CLASSES, "/dataClass");
        validateOrigin(root.get("origin"));
        if (!"DORA_ORIGINAL_SYNTHETIC_METADATA_ONLY"
                .equals(string(root.get("licenseId"), "/licenseId"))) {
            reject("E_RIGHTS_METADATA", "/licenseId");
        }
        String digest = string(root.get("termsDigest"), "/termsDigest");
        if (!SHA256.matcher(digest).matches() || digest.equals("sha256:" + "0".repeat(64))) {
            reject("E_DIGEST_FORMAT", "/termsDigest");
        }
        if (!"not-applicable"
                .equals(string(root.get("consentReference"), "/consentReference"))) {
            reject("E_CONSENT_REFERENCE", "/consentReference");
        }
        if (bool(root.get("trainingAllowed"), "/trainingAllowed")) {
            reject("E_TRAINING_FORBIDDEN", "/trainingAllowed");
        }
        if (bool(root.get("publicRedistributionAllowed"), "/publicRedistributionAllowed")) {
            reject("E_REDISTRIBUTION_FORBIDDEN", "/publicRedistributionAllowed");
        }
        Map<String, SampleRow> graph = validateSamples(root.get("samples"), dataClass);
        validateDeletionLedger(root.get("deletionLedger"), graph);
        validatePublicProjection(root.get("publicProjection"));
        orderedStrings(root.get("limitations"), LIMITATIONS, false, "/limitations");
    }

    private static void validatePurpose(Object value) {
        Map<String, Object> purpose = exactObject(value, PURPOSE_FIELDS, "/purpose");
        List<String> pocs = orderedStrings(purpose.get("pocIds"), POC_IDS, true, "/purpose/pocIds");
        List<String> uses =
                orderedStrings(
                        purpose.get("allowedUses"),
                        ALLOWED_USES,
                        true,
                        "/purpose/allowedUses");
        orderedStrings(
                purpose.get("excludedUses"),
                EXCLUDED_USES,
                false,
                "/purpose/excludedUses");
        Set<String> mappedSet = new HashSet<>();
        for (String poc : pocs) {
            mappedSet.add(POC_TO_USE.get(poc));
        }
        List<String> mapped = new ArrayList<>();
        for (String allowed : ALLOWED_USES) {
            if (mappedSet.contains(allowed)) {
                mapped.add(allowed);
            }
        }
        if (!uses.equals(mapped)) {
            reject("E_PURPOSE_MAPPING", "/purpose");
        }
    }

    private static void validateOrigin(Object value) {
        Map<String, Object> origin = exactObject(value, ORIGIN_FIELDS, "/origin");
        if (!"DETERMINISTIC_GENERATOR".equals(string(origin.get("type"), "/origin/type"))) {
            reject("E_DATA_CLASS_OUT_OF_SCOPE", "/origin/type");
        }
        if (!"poc-data-java17-in-memory-fixture-generator"
                .equals(string(origin.get("generatorId"), "/origin/generatorId"))) {
            reject("E_ID_FORMAT", "/origin/generatorId");
        }
        if (!SEMVER.matcher(string(origin.get("generatorVersion"), "/origin/generatorVersion"))
                .matches()) {
            reject("E_VERSION_FORMAT", "/origin/generatorVersion");
        }
        long seed = integer(origin.get("seed"), "/origin/seed");
        if (seed < 0L) {
            reject("E_INTEGER_RANGE", "/origin/seed");
        }
        if (!"tools/poc_data_manifest_validator/src/test/java/com/monumentogram/dora/stage0/data/manifest/SyntheticManifestValidatorTest.java"
                .equals(string(origin.get("sourceReference"), "/origin/sourceReference"))) {
            reject("E_REPO_PATH", "/origin/sourceReference");
        }
    }

    private static Map<String, SampleRow> validateSamples(Object value, String dataClass) {
        List<Object> samples = array(value, "/samples");
        if (samples.size() < 2 || samples.size() > 256) {
            reject("E_ARRAY_SIZE", "/samples");
        }
        List<String> orderedIds = new ArrayList<>();
        Map<String, SampleRow> graph = new LinkedHashMap<>();
        for (int index = 0; index < samples.size(); index++) {
            SampleRow row = validateSample(samples.get(index), index, dataClass);
            orderedIds.add(row.sampleId());
            if (graph.put(row.sampleId(), row) != null) {
                reject("E_SAMPLE_ID_DUPLICATE", "/samples");
            }
        }
        List<String> sortedIds = new ArrayList<>(orderedIds);
        sortedIds.sort(Comparator.naturalOrder());
        if (!orderedIds.equals(sortedIds)) {
            reject("E_ARRAY_ORDER", "/samples");
        }
        for (SampleRow row : graph.values()) {
            if (row.parentSampleId() == null) {
                continue;
            }
            SampleRow parent = graph.get(row.parentSampleId());
            if (parent == null) {
                reject("E_PARENT_UNKNOWN", "/samples");
            }
            if (parent.sampleId().equals(row.sampleId())) {
                reject("E_PARENT_CYCLE", "/samples");
            }
            if (!parent.split().equals(row.split())) {
                reject("E_PARENT_SPLIT_MISMATCH", "/samples");
            }
            if (row.createdAt().isBefore(parent.createdAt())
                    || row.expiresAt().isAfter(parent.expiresAt())
                    || !parent.accessRoles().containsAll(row.accessRoles())) {
                reject("E_PARENT_RESTRICTIONS_WIDENED", "/samples");
            }
            if (parent.deletionState().equals("deleted")
                    && !row.deletionState().equals("deleted")) {
                reject("E_DELETION_DESCENDANT_ACTIVE", "/samples");
            }
            if (Set.of("withdrawal-pending", "expired", "quarantined")
                            .contains(parent.deletionState())
                    && row.deletionState().equals("active")) {
                reject("E_DELETION_DESCENDANT_ACTIVE", "/samples");
            }
            Set<String> seen = new HashSet<>();
            String cursor = row.sampleId();
            while (cursor != null) {
                if (!seen.add(cursor)) {
                    reject("E_PARENT_CYCLE", "/samples");
                }
                SampleRow current = graph.get(cursor);
                cursor = current == null ? null : current.parentSampleId();
            }
        }
        return Collections.unmodifiableMap(graph);
    }

    private static SampleRow validateSample(Object value, int index, String dataClass) {
        String pointer = "/samples/" + index;
        Map<String, Object> sample = exactObject(value, SAMPLE_FIELDS, pointer);
        String sampleId = opaque(sample.get("sampleId"), SAMPLE_ID, pointer + "/sampleId");
        if (sample.get("contentSha256") != null) {
            reject("E_PUBLIC_DIGEST", pointer + "/contentSha256");
        }
        String language =
                catalogue(sample.get("languageSlice"), LANGUAGE_SLICES, pointer + "/languageSlice");
        Map<String, Object> acoustic =
                exactObject(sample.get("acousticSlice"), ACOUSTIC_FIELDS, pointer + "/acousticSlice");
        String condition =
                catalogue(acoustic.get("condition"), CONDITIONS, pointer + "/acousticSlice/condition");
        String distance =
                catalogue(acoustic.get("distance"), DISTANCES, pointer + "/acousticSlice/distance");
        String route =
                catalogue(acoustic.get("route"), ROUTES, pointer + "/acousticSlice/route");
        String overlap =
                catalogue(acoustic.get("overlap"), OVERLAPS, pointer + "/acousticSlice/overlap");
        String bucket =
                catalogue(
                        sample.get("speakerCountBucket"),
                        SPEAKER_BUCKETS,
                        pointer + "/speakerCountBucket");
        String split = catalogue(sample.get("split"), SPLITS, pointer + "/split");
        String parent =
                sample.get("parentSampleId") == null
                        ? null
                        : opaque(sample.get("parentSampleId"), SAMPLE_ID, pointer + "/parentSampleId");
        if (!"MANIFEST_ONLY_NO_SAMPLE_BYTES"
                .equals(string(sample.get("storageClass"), pointer + "/storageClass"))) {
            reject("E_STORAGE_CLASS", pointer + "/storageClass");
        }
        if (sample.get("evidenceLocator") != null) {
            reject("E_PRIVATE_LOCATOR", pointer + "/evidenceLocator");
        }
        List<String> roles =
                orderedStrings(
                        sample.get("accessRoles"),
                        ACCESS_ROLES,
                        true,
                        pointer + "/accessRoles");
        Instant created = timestamp(sample.get("createdAt"), pointer + "/createdAt");
        Instant expires = timestamp(sample.get("expiresAt"), pointer + "/expiresAt");
        if (!created.isBefore(expires)) {
            reject("E_TIMESTAMP_ORDER", pointer);
        }
        String state =
                catalogue(
                        sample.get("deletionState"),
                        DELETION_STATES,
                        pointer + "/deletionState");
        catalogue(sample.get("notes"), NOTE_CODES, pointer + "/notes");
        if (dataClass.equals("SYNTHETIC_SIGNAL")) {
            if (!language.equals("non-speech")
                    || condition.equals("NOT_APPLICABLE_TEXT")
                    || !bucket.equals("0")) {
                reject("E_CLASS_SLICE_MISMATCH", pointer);
            }
        } else if (dataClass.equals("GENERATED_TEXT")) {
            if (language.equals("non-speech")
                    || !condition.equals("NOT_APPLICABLE_TEXT")
                    || !distance.equals("NOT_APPLICABLE")
                    || !route.equals("NOT_APPLICABLE")
                    || !overlap.equals("NOT_APPLICABLE")
                    || !bucket.equals("not-applicable")) {
                reject("E_CLASS_SLICE_MISMATCH", pointer);
            }
        }
        return new SampleRow(
                sampleId,
                parent,
                split,
                state,
                created,
                expires,
                Set.copyOf(roles));
    }

    private static void validateDeletionLedger(Object value, Map<String, SampleRow> graph) {
        List<Object> entries = array(value, "/deletionLedger");
        if (entries.isEmpty() || entries.size() > 256) {
            reject("E_ARRAY_SIZE", "/deletionLedger");
        }
        List<String> eventIds = new ArrayList<>();
        Set<String> deletedSamples = new HashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            String pointer = "/deletionLedger/" + index;
            Map<String, Object> entry = exactObject(entries.get(index), DELETION_FIELDS, pointer);
            eventIds.add(opaque(entry.get("eventId"), EVENT_ID, pointer + "/eventId"));
            String sampleId = opaque(entry.get("sampleId"), SAMPLE_ID, pointer + "/sampleId");
            SampleRow sample = graph.get(sampleId);
            if (sample == null) {
                reject("E_DELETION_SAMPLE_UNKNOWN", pointer + "/sampleId");
            }
            if (!sample.deletionState().equals("deleted")) {
                reject("E_DELETION_STATE_MISMATCH", pointer);
            }
            if (!deletedSamples.add(sampleId)) {
                reject("E_DELETION_EVENT_DUPLICATE", "/deletionLedger");
            }
            if (!"SYNTHETIC_DRY_RUN".equals(string(entry.get("trigger"), pointer + "/trigger"))
                    || !"DELETED".equals(string(entry.get("outcome"), pointer + "/outcome"))) {
                reject("E_DELETION_VALUE", pointer);
            }
            Instant requested = timestamp(entry.get("requestedAt"), pointer + "/requestedAt");
            Instant completed = timestamp(entry.get("completedAt"), pointer + "/completedAt");
            Instant backup = timestamp(entry.get("backupExpiresAt"), pointer + "/backupExpiresAt");
            if (requested.isBefore(sample.createdAt())
                    || requested.isAfter(completed)
                    || completed.isAfter(backup)) {
                reject("E_TIMESTAMP_ORDER", pointer);
            }
            if (requested.plus(30L, ChronoUnit.DAYS).isBefore(completed)) {
                reject("E_WITHDRAWAL_SLA", pointer);
            }
            orderedStrings(
                    entry.get("affectedScopes"),
                    DELETION_SCOPES,
                    false,
                    pointer + "/affectedScopes");
            if (!array(entry.get("unresolvedFailures"), pointer + "/unresolvedFailures").isEmpty()) {
                reject("E_DELETION_UNRESOLVED", pointer + "/unresolvedFailures");
            }
        }
        if (eventIds.size() != new HashSet<>(eventIds).size()) {
            reject("E_EVENT_ID_DUPLICATE", "/deletionLedger");
        }
        List<String> sortedEventIds = new ArrayList<>(eventIds);
        sortedEventIds.sort(Comparator.naturalOrder());
        if (!eventIds.equals(sortedEventIds)) {
            reject("E_ARRAY_ORDER", "/deletionLedger");
        }
        Set<String> expectedDeleted = new HashSet<>();
        for (SampleRow row : graph.values()) {
            if (row.deletionState().equals("deleted")) {
                expectedDeleted.add(row.sampleId());
            }
        }
        if (!deletedSamples.equals(expectedDeleted)) {
            reject("E_DELETION_LEDGER_MISSING", "/deletionLedger");
        }
    }

    private static void validatePublicProjection(Object value) {
        Map<String, Object> projection = exactObject(value, PUBLIC_FIELDS, "/publicProjection");
        List<String> orderedFields = new ArrayList<>(PUBLIC_FIELDS);
        orderedFields.sort(Comparator.naturalOrder());
        for (String field : orderedFields) {
            if (bool(projection.get(field), "/publicProjection/" + field)) {
                reject("E_PUBLIC_PROJECTION", "/publicProjection/" + field);
            }
        }
    }

    private static List<String> orderedStrings(
            Object value, List<String> catalogue, boolean subsetAllowed, String pointer) {
        List<String> items = stringList(value, pointer);
        if (items.isEmpty()) {
            reject("E_ARRAY_EMPTY", pointer);
        }
        if (items.size() != new HashSet<>(items).size()) {
            reject("E_ARRAY_DUPLICATE", pointer);
        }
        if (!catalogue.containsAll(items)) {
            reject("E_CATALOG_VALUE", pointer);
        }
        List<String> expected = new ArrayList<>();
        for (String item : catalogue) {
            if (items.contains(item)) {
                expected.add(item);
            }
        }
        if (!items.equals(expected)) {
            reject("E_ARRAY_ORDER", pointer);
        }
        if (!subsetAllowed && !items.equals(catalogue)) {
            reject("E_CATALOG_INCOMPLETE", pointer);
        }
        return List.copyOf(items);
    }

    private static String catalogue(Object value, List<String> allowed, String pointer) {
        String text = string(value, pointer);
        if (!allowed.contains(text)) {
            reject("E_CATALOG_VALUE", pointer);
        }
        return text;
    }

    private static String opaque(Object value, Pattern pattern, String pointer) {
        String text = string(value, pointer);
        if (!pattern.matcher(text).matches()) {
            reject("E_ID_FORMAT", pointer);
        }
        return text;
    }

    private static Instant timestamp(Object value, String pointer) {
        String text = string(value, pointer);
        if (!TIMESTAMP.matcher(text).matches()) {
            reject("E_TIMESTAMP_FORMAT", pointer);
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeException error) {
            reject("E_TIMESTAMP_FORMAT", pointer);
            return Instant.EPOCH;
        }
    }

    private static Map<String, Object> exactObject(
            Object value, Set<String> fields, String pointer) {
        Map<String, Object> result = object(value, pointer);
        if (!result.keySet().containsAll(fields)) {
            reject("E_FIELD_MISSING", pointer);
        }
        if (!fields.containsAll(result.keySet())) {
            reject("E_FIELD_UNKNOWN", pointer);
        }
        return result;
    }

    private static Map<String, Object> object(Object value, String pointer) {
        if (!(value instanceof Map<?, ?>)) {
            reject("E_FIELD_TYPE", pointer);
        }
        Map<?, ?> rawMap = (Map<?, ?>) value;
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                reject("E_FIELD_TYPE", pointer);
            }
            String key = (String) entry.getKey();
            result.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<Object> array(Object value, String pointer) {
        if (!(value instanceof List<?>)) {
            reject("E_FIELD_TYPE", pointer);
        }
        List<?> rawList = (List<?>) value;
        return Collections.unmodifiableList(new ArrayList<>(rawList));
    }

    private static List<String> stringList(Object value, String pointer) {
        List<Object> raw = array(value, pointer);
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            result.add(string(item, pointer));
        }
        return List.copyOf(result);
    }

    private static String string(Object value, String pointer) {
        if (!(value instanceof String)) {
            reject("E_FIELD_TYPE", pointer);
        }
        return (String) value;
    }

    private static long integer(Object value, String pointer) {
        if (!(value instanceof Long)) {
            reject("E_FIELD_TYPE", pointer);
        }
        return ((Long) value).longValue();
    }

    private static boolean bool(Object value, String pointer) {
        if (!(value instanceof Boolean)) {
            reject("E_FIELD_TYPE", pointer);
        }
        return ((Boolean) value).booleanValue();
    }

    private static void validateUnicode(Object value) {
        if (value instanceof String text) {
            for (int index = 0; index < text.length(); index++) {
                char current = text.charAt(index);
                if (Character.isHighSurrogate(current)) {
                    if (index + 1 >= text.length()
                            || !Character.isLowSurrogate(text.charAt(index + 1))) {
                        reject("E_JSON_UNICODE", "/");
                    }
                    index++;
                } else if (Character.isLowSurrogate(current)
                        || current < 0x20
                        || current == '\u200b'
                        || current == '\u200c'
                        || current == '\u200d'
                        || current == '\ufeff') {
                    reject("E_JSON_UNICODE", "/");
                }
            }
        } else if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                validateUnicode(entry.getKey());
                validateUnicode(entry.getValue());
            }
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                validateUnicode(item);
            }
        }
    }

    private static void appendCanonical(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            appendString(text, out);
        } else if (value instanceof Boolean flag) {
            out.append(flag.booleanValue());
        } else if (value instanceof Long number) {
            out.append(number.longValue());
        } else if (value instanceof Integer number) {
            out.append(number.intValue());
        } else if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("non-string key");
                }
                sorted.put(key, entry.getValue());
            }
            out.append('{');
            if (!sorted.isEmpty()) {
                out.append('\n');
                int index = 0;
                for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                    out.append("  ");
                    appendString(entry.getKey(), out);
                    out.append(": ");
                    StringBuilder child = new StringBuilder();
                    appendCanonical(entry.getValue(), child);
                    out.append(child.toString().replace("\n", "\n  "));
                    if (++index < sorted.size()) {
                        out.append(',');
                    }
                    out.append('\n');
                }
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            if (!list.isEmpty()) {
                out.append('\n');
                for (int index = 0; index < list.size(); index++) {
                    out.append("  ");
                    StringBuilder child = new StringBuilder();
                    appendCanonical(list.get(index), child);
                    out.append(child.toString().replace("\n", "\n  "));
                    if (index + 1 < list.size()) {
                        out.append(',');
                    }
                    out.append('\n');
                }
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("unsupported value");
        }
    }

    private static void appendString(String text, StringBuilder out) {
        out.append('"');
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
    }

    private static void reject(String code, String pointer) {
        throw new ManifestFault(code, pointer);
    }

    private record SampleRow(
            String sampleId,
            String parentSampleId,
            String split,
            String deletionState,
            Instant createdAt,
            Instant expiresAt,
            Set<String> accessRoles) {}

    /** Content-free failure with stable code and JSON pointer only. */
    public static final class ManifestFault extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final String code;
        private final String pointer;

        private ManifestFault(String code, String pointer) {
            super(code, null, false, false);
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
        private final String source;
        private int offset;

        private JsonParser(String source) {
            this.source = source;
        }

        private Object parseDocument() {
            skipWhitespace();
            Object result = parseValue(0);
            skipWhitespace();
            if (offset != source.length()) {
                reject("E_JSON_SYNTAX", "/");
            }
            return result;
        }

        private Object parseValue(int depth) {
            if (depth > MAX_JSON_DEPTH) {
                reject("E_JSON_TOO_DEEP", "/");
            }
            if (offset >= source.length()) {
                reject("E_JSON_SYNTAX", "/");
            }
            char current = source.charAt(offset);
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
            consume('{');
            skipWhitespace();
            Map<String, Object> result = new LinkedHashMap<>();
            if (peek('}')) {
                consume('}');
                return Collections.unmodifiableMap(result);
            }
            while (true) {
                if (!peek('"')) {
                    reject("E_JSON_SYNTAX", "/");
                }
                String key = parseString();
                skipWhitespace();
                consume(':');
                skipWhitespace();
                Object value = parseValue(depth);
                if (result.containsKey(key)) {
                    reject("E_JSON_DUPLICATE_KEY", "/");
                }
                result.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    consume('}');
                    return Collections.unmodifiableMap(result);
                }
                consume(',');
                skipWhitespace();
            }
        }

        private List<Object> parseArray(int depth) {
            consume('[');
            skipWhitespace();
            List<Object> result = new ArrayList<>();
            if (peek(']')) {
                consume(']');
                return Collections.unmodifiableList(result);
            }
            while (true) {
                result.add(parseValue(depth));
                skipWhitespace();
                if (peek(']')) {
                    consume(']');
                    return Collections.unmodifiableList(result);
                }
                consume(',');
                skipWhitespace();
            }
        }

        private String parseString() {
            consume('"');
            StringBuilder result = new StringBuilder();
            while (offset < source.length()) {
                char current = source.charAt(offset++);
                if (current == '"') {
                    return result.toString();
                }
                if (current == '\\') {
                    if (offset >= source.length()) {
                        reject("E_JSON_SYNTAX", "/");
                    }
                    char escape = source.charAt(offset++);
                    switch (escape) {
                        case '"', '\\', '/' -> result.append(escape);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> result.append(parseUnicodeEscape());
                        default -> reject("E_JSON_SYNTAX", "/");
                    }
                } else {
                    if (current < 0x20) {
                        reject("E_JSON_SYNTAX", "/");
                    }
                    result.append(current);
                }
            }
            reject("E_JSON_SYNTAX", "/");
            return "";
        }

        private char parseUnicodeEscape() {
            if (offset + 4 > source.length()) {
                reject("E_JSON_SYNTAX", "/");
            }
            int value = 0;
            for (int index = 0; index < 4; index++) {
                int digit = Character.digit(source.charAt(offset++), 16);
                if (digit < 0) {
                    reject("E_JSON_SYNTAX", "/");
                }
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private Long parseInteger() {
            int start = offset;
            if (peek('-')) {
                offset++;
            }
            if (offset >= source.length()) {
                reject("E_JSON_SYNTAX", "/");
            }
            if (peek('0')) {
                offset++;
                if (offset < source.length() && Character.isDigit(source.charAt(offset))) {
                    reject("E_JSON_SYNTAX", "/");
                }
            } else {
                if (!Character.isDigit(source.charAt(offset))) {
                    reject("E_JSON_SYNTAX", "/");
                }
                while (offset < source.length() && Character.isDigit(source.charAt(offset))) {
                    offset++;
                }
            }
            if (offset < source.length()
                    && (source.charAt(offset) == '.'
                            || source.charAt(offset) == 'e'
                            || source.charAt(offset) == 'E')) {
                reject("E_JSON_SYNTAX", "/");
            }
            try {
                return Long.valueOf(source.substring(start, offset));
            } catch (NumberFormatException error) {
                reject("E_JSON_SYNTAX", "/");
                return 0L;
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!source.startsWith(literal, offset)) {
                reject("E_JSON_SYNTAX", "/");
            }
            offset += literal.length();
            return value;
        }

        private void consume(char expected) {
            if (offset >= source.length() || source.charAt(offset) != expected) {
                reject("E_JSON_SYNTAX", "/");
            }
            offset++;
        }

        private boolean peek(char expected) {
            return offset < source.length() && source.charAt(offset) == expected;
        }

        private void skipWhitespace() {
            while (offset < source.length()) {
                char current = source.charAt(offset);
                if (current == ' ' || current == '\n' || current == '\r' || current == '\t') {
                    offset++;
                } else {
                    return;
                }
            }
        }
    }
}
