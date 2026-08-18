package com.monumentogram.dora.stage0.offline.i3;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure in-memory validator for one frozen, content-free Offline static call-ledger fixture.
 *
 * <p>This class validates schema and readiness invariants only. It never observes a process, a
 * device, Android, GMS, a network stack, a provider, or a model, and it cannot prove that a runtime
 * call count is zero.</p>
 */
public final class OfflineI3StaticCallLedger {
    public static final String AUTHORIZATION_ID =
            "POC-OFFLINE-001-I3-STATIC-CALL-LEDGER-AUTH-20260818-01";
    public static final String FIXTURE_SCHEMA =
            "poc-offline-i3-static-call-ledger-fixture-v0.1";
    public static final String CONTRACT_ID = "poc-offline-readiness-stage0-v0.1";
    public static final String BASE_COMMIT =
            "881c5bfbb84cedfc2d267ca8f8255c456b3908b8";
    public static final String BASE_TREE =
            "0366351b73fd68adc713b22b587926330aed4bab";
    public static final String PROOF_CLASS =
            "STATIC_SYNTHETIC_CALL_LEDGER_STRUCTURE_ONLY_NOT_RUNTIME_EVIDENCE";
    public static final String POC_TRUTH = "TODO_NOT_READY_NOT_RUN_NOT_AUTHORIZED";

    public static final String FROZEN_FIXTURE_SHA256 =
            "1aa27452ebed89a2b12a3d9a4c59644abe9d73bd9042d21cc325b37b0df92fa8";
    public static final String FROZEN_VALID_RESULT_SHA256 =
            "d58d2a2fbf5ac5f8180b18326ab1139d042f28d59c64daa7ee8b0bf88cd3bbc9";

    public static final List<String> NETWORK_CALL_LEDGER_FIELDS = List.of(
            "sequence",
            "windowClass",
            "calibrationEventId",
            "attributedToMeasuredScenario",
            "appUidClass",
            "processClass",
            "componentClass",
            "scenarioId",
            "actionId",
            "callKind",
            "destinationClass",
            "endpointId",
            "consentProfileDigest",
            "decision",
            "outcome",
            "retryState",
            "monotonicOffsetMs");

    public static final List<String> MONITOR_CALIBRATION_FIELDS = List.of(
            "method",
            "attributionBoundary",
            "coverageStart",
            "coverageEnd",
            "coverageGaps",
            "perCallKindCoverage",
            "preRunCanaryObserved",
            "postRunCanaryObserved",
            "rawMonitorEventCount",
            "excludedCalibrationEventCount",
            "measuredDoraForbiddenAttemptCount");

    public static final List<String> CALL_KIND_NAMES = List.of(
            "DNS",
            "SOCKET",
            "HTTP",
            "GMS_BIND",
            "ACCOUNT_AUTH",
            "REMOTE_CONFIG",
            "ANALYTICS",
            "PROVIDER");

    private static final String CONSENT_PROFILE_DIGEST = sha256(
            CONTRACT_ID
                    + "\nOFF_I3_SYNTHETIC_CONSENT_PROFILE_V1\nSTATIC_CONTENT_FREE_PROFILE");

    private static final List<ArtifactPin> EXPECTED_ARTIFACT_PINS = List.of(
            new ArtifactPin(
                    "docs/stage0/DORA_MVP1_POC_OFFLINE_READINESS_CONTRACT_STAGE0_V0_1.md",
                    45_377,
                    "9a905eabbcd75601fc598cccc001dcff56a5b6b65eeb936f2a9c4602d658682a"),
            new ArtifactPin(
                    "docs/evidence/poc-offline-001/readiness-contract-stage0-v0.1.json",
                    58_124,
                    "a564cf9031f610006327c374baff82983de2ea9ba4b6c9d13d6185e7967a6dae"),
            new ArtifactPin(
                    "docs/evidence/poc-offline-001/i1-host-oracle-implementation-evidence-stage0-v0.1.json",
                    15_672,
                    "27168571abe76fc76e958ca984217b5ff58f6433d7790e36a72d065815de876d"),
            new ArtifactPin(
                    "tools/offline_i1_oracle/src/main/java/com/monumentogram/dora/stage0/offline/i1/OfflineI1Oracle.java",
                    213_295,
                    "2eb3a80716fdc25abe7300552c8ba95f4deabe68e7c8d5d42dcf9d5cb13235bf"),
            new ArtifactPin(
                    "tools/offline_i1_oracle/src/test/java/com/monumentogram/dora/stage0/offline/i1/OfflineI1OracleTest.java",
                    244_685,
                    "235be2b6d3c646ce9c0910369de99847a149d226159ab1f8a1785e074572b89c"),
            new ArtifactPin(
                    "docs/evidence/poc-offline-001/i2-integrated-synthetic-harness-evidence-stage0-v0.1.json",
                    20_853,
                    "bd4b8cfbe96f05192eb0e37d4a1dfa4758009abb91a2cc95d8a991ac49c75e5c"),
            new ArtifactPin(
                    "tools/offline_i2_integrated_harness/src/main/java/com/monumentogram/dora/stage0/offline/i2/OfflineI2IntegratedHarness.java",
                    47_515,
                    "74a775220f58fedcb51fc3eeac3d6d0e652377e19e1597269105c5a432e25ac2"),
            new ArtifactPin(
                    "tools/offline_i2_integrated_harness/src/test/java/com/monumentogram/dora/stage0/offline/i2/OfflineI2IntegratedHarnessTest.java",
                    41_310,
                    "fa307e3fdc1c03a2ebbe9b7a916f9523374384342c04748bf406bfb7ead07383"),
            new ArtifactPin(
                    "docs/evidence/poc-offline-001/reviews/off-i2-integrated-synthetic-harness-independent-advisory-review-2026-08-18.json",
                    8_566,
                    "75e1b8c91ed9e5a4b04b569a74b19574eeac60b81d34ddf50c30e9697f95a922"),
            new ArtifactPin(
                    "docs/evidence/stage0-host-oracle-publication-closure-2026-08-18.json",
                    9_746,
                    "d11aba83be1fe2664eda3ae847386967239b4ad09280bb2a5bd7002fed70d1d8"));

    private static final List<ReadinessBlocker> EXPECTED_BLOCKERS = List.of(
            new ReadinessBlocker("OFF-RDY-01", "BLOCKED"),
            new ReadinessBlocker("OFF-RDY-02", "BLOCKED"),
            new ReadinessBlocker("OFF-RDY-03", "BLOCKED"),
            new ReadinessBlocker("OFF-RDY-04", "BLOCKED"),
            new ReadinessBlocker("OFF-RDY-05", "BLOCKED"),
            new ReadinessBlocker("OFF-RDY-06", "BLOCKED"),
            new ReadinessBlocker("OFF-RDY-07", "BLOCKED"),
            new ReadinessBlocker("OFF-RDY-08", "BLOCKED"),
            new ReadinessBlocker("OFF-RDY-09", "BLOCKED"),
            new ReadinessBlocker("OFF-RDY-10", "BLOCKED"));

    private OfflineI3StaticCallLedger() {}

    public enum WindowClass {
        CALIBRATION_PRE,
        MEASURED_SCENARIO,
        CALIBRATION_POST
    }

    public enum CallKind {
        DNS,
        SOCKET,
        HTTP,
        GMS_BIND,
        ACCOUNT_AUTH,
        REMOTE_CONFIG,
        ANALYTICS,
        PROVIDER
    }

    public enum Diagnostic {
        NONE,
        FIXTURE_MISSING,
        TOP_LEVEL_SCHEMA_INVALID,
        SCHEMA_VERSION_STALE,
        CONTRACT_ID_STALE,
        BASE_PIN_STALE,
        LEDGER_FIELD_DUPLICATE,
        LEDGER_FIELD_MISSING,
        LEDGER_FIELD_EXTRA,
        LEDGER_FIELD_ORDER_STALE,
        CALIBRATION_FIELD_DUPLICATE,
        CALIBRATION_FIELD_MISSING,
        CALIBRATION_FIELD_EXTRA,
        CALIBRATION_FIELD_ORDER_STALE,
        CALL_KIND_DUPLICATE,
        CALL_KIND_MISSING,
        CALL_KIND_EXTRA,
        CALL_KIND_ORDER_STALE,
        ARTIFACT_PIN_DUPLICATE,
        ARTIFACT_PIN_MISSING,
        ARTIFACT_PIN_EXTRA,
        ARTIFACT_PIN_ORDER_STALE,
        ARTIFACT_PIN_STALE,
        READINESS_TRUTH_STALE,
        BLOCKER_DUPLICATE,
        BLOCKER_MISSING,
        BLOCKER_EXTRA,
        BLOCKER_ORDER_STALE,
        BLOCKER_STATE_STALE,
        ENTRY_DUPLICATE,
        ENTRY_MISSING,
        ENTRY_EXTRA,
        ENTRY_ORDER_STALE,
        ENTRY_SCHEMA_INVALID,
        ENTRY_STALE,
        FIXTURE_HASH_STALE,
        RESULT_HASH_STALE
    }

    public record ArtifactPin(String path, long bytes, String sha256) {}

    public record ReadinessBlocker(String id, String state) {}

    public record BacklogTruth(
            String id,
            String status,
            String readiness,
            String integratedExecution,
            String authorization) {}

    public record LedgerEntry(
            long sequence,
            WindowClass windowClass,
            String calibrationEventId,
            boolean attributedToMeasuredScenario,
            String appUidClass,
            String processClass,
            String componentClass,
            String scenarioId,
            String actionId,
            CallKind callKind,
            String destinationClass,
            String endpointId,
            String consentProfileDigest,
            String decision,
            String outcome,
            String retryState,
            long monotonicOffsetMs) {}

    public record Fixture(
            String schemaVersion,
            String authorizationId,
            String contractId,
            String baseCommit,
            String baseTree,
            List<String> networkCallLedgerFields,
            List<String> monitorCalibrationFields,
            List<String> callKindNames,
            List<ArtifactPin> artifactPins,
            BacklogTruth backlogTruth,
            List<ReadinessBlocker> readinessBlockers,
            List<LedgerEntry> entries,
            String declaredFixtureSha256) {
        public Fixture {
            networkCallLedgerFields = immutableCopyOrNull(networkCallLedgerFields);
            monitorCalibrationFields = immutableCopyOrNull(monitorCalibrationFields);
            callKindNames = immutableCopyOrNull(callKindNames);
            artifactPins = immutableCopyOrNull(artifactPins);
            readinessBlockers = immutableCopyOrNull(readinessBlockers);
            entries = immutableCopyOrNull(entries);
        }
    }

    public record Coverage(int preStaticRows, int postStaticRows, int measuredRows) {}

    public record ValidationResult(
            boolean structureValid,
            Diagnostic diagnostic,
            String proofClass,
            String pocTruth,
            String fixtureSha256,
            int ledgerFieldCount,
            int calibrationFieldCount,
            int callKindCount,
            int artifactPinCount,
            int blockerCount,
            int staticRowCount,
            int measuredRowCount,
            Map<CallKind, Coverage> coverage,
            boolean monitorCalibrated,
            boolean runtimeZeroCallProven,
            boolean d4NoGmsDeviceProven,
            boolean offlineJourneyProven,
            boolean modelProviderOrProductReady) {
        public ValidationResult {
            coverage = immutableCoverageOrNull(coverage);
        }
    }

    public static Fixture frozenFixture() {
        return new Fixture(
                FIXTURE_SCHEMA,
                AUTHORIZATION_ID,
                CONTRACT_ID,
                BASE_COMMIT,
                BASE_TREE,
                NETWORK_CALL_LEDGER_FIELDS,
                MONITOR_CALIBRATION_FIELDS,
                CALL_KIND_NAMES,
                EXPECTED_ARTIFACT_PINS,
                new BacklogTruth(
                        "POC-OFFLINE-001",
                        "TODO",
                        "NOT_READY",
                        "NOT_RUN",
                        "NOT_AUTHORIZED"),
                EXPECTED_BLOCKERS,
                expectedEntries(),
                FROZEN_FIXTURE_SHA256);
    }

    public static ValidationResult validate(Fixture fixture) {
        Diagnostic diagnostic = firstFailure(fixture);
        if (diagnostic != Diagnostic.NONE) {
            return invalidResult(diagnostic);
        }

        EnumMap<CallKind, Coverage> coverage = new EnumMap<>(CallKind.class);
        for (CallKind kind : CallKind.values()) {
            coverage.put(kind, new Coverage(1, 1, 0));
        }
        ValidationResult result = new ValidationResult(
                true,
                Diagnostic.NONE,
                PROOF_CLASS,
                POC_TRUTH,
                fixture.declaredFixtureSha256(),
                NETWORK_CALL_LEDGER_FIELDS.size(),
                MONITOR_CALIBRATION_FIELDS.size(),
                CALL_KIND_NAMES.size(),
                EXPECTED_ARTIFACT_PINS.size(),
                EXPECTED_BLOCKERS.size(),
                fixture.entries().size(),
                0,
                coverage,
                false,
                false,
                false,
                false,
                false);
        if (!FROZEN_VALID_RESULT_SHA256.equals(canonicalResultSha256(result))) {
            return invalidResult(Diagnostic.RESULT_HASH_STALE);
        }
        return result;
    }

    public static String canonicalFixtureSha256(Fixture fixture) {
        if (fixture == null) {
            return sha256("FIXTURE_NULL\n");
        }
        StringBuilder out = new StringBuilder(16_384);
        append(out, "schemaVersion", fixture.schemaVersion());
        append(out, "authorizationId", fixture.authorizationId());
        append(out, "contractId", fixture.contractId());
        append(out, "baseCommit", fixture.baseCommit());
        append(out, "baseTree", fixture.baseTree());
        appendStrings(out, "networkCallLedgerFields", fixture.networkCallLedgerFields());
        appendStrings(out, "monitorCalibrationFields", fixture.monitorCalibrationFields());
        appendStrings(out, "callKindNames", fixture.callKindNames());
        appendArtifactPins(out, fixture.artifactPins());
        appendBacklogTruth(out, fixture.backlogTruth());
        appendBlockers(out, fixture.readinessBlockers());
        appendEntries(out, fixture.entries());
        return sha256(out.toString());
    }

    public static String canonicalResultSha256(ValidationResult result) {
        if (result == null) {
            return sha256("RESULT_NULL\n");
        }
        StringBuilder out = new StringBuilder(2_048);
        append(out, "structureValid", Boolean.toString(result.structureValid()));
        append(out, "diagnostic", nameOrNull(result.diagnostic()));
        append(out, "proofClass", result.proofClass());
        append(out, "pocTruth", result.pocTruth());
        append(out, "fixtureSha256", result.fixtureSha256());
        append(out, "ledgerFieldCount", Integer.toString(result.ledgerFieldCount()));
        append(out, "calibrationFieldCount", Integer.toString(result.calibrationFieldCount()));
        append(out, "callKindCount", Integer.toString(result.callKindCount()));
        append(out, "artifactPinCount", Integer.toString(result.artifactPinCount()));
        append(out, "blockerCount", Integer.toString(result.blockerCount()));
        append(out, "staticRowCount", Integer.toString(result.staticRowCount()));
        append(out, "measuredRowCount", Integer.toString(result.measuredRowCount()));
        if (result.coverage() == null) {
            append(out, "coverage", null);
        } else {
            for (CallKind kind : CallKind.values()) {
                Coverage value = result.coverage().get(kind);
                append(out, "coverageKind", kind.name());
                append(out, "coveragePre", value == null ? null : Integer.toString(value.preStaticRows()));
                append(out, "coveragePost", value == null ? null : Integer.toString(value.postStaticRows()));
                append(out, "coverageMeasured", value == null ? null : Integer.toString(value.measuredRows()));
            }
        }
        append(out, "monitorCalibrated", Boolean.toString(result.monitorCalibrated()));
        append(out, "runtimeZeroCallProven", Boolean.toString(result.runtimeZeroCallProven()));
        append(out, "d4NoGmsDeviceProven", Boolean.toString(result.d4NoGmsDeviceProven()));
        append(out, "offlineJourneyProven", Boolean.toString(result.offlineJourneyProven()));
        append(
                out,
                "modelProviderOrProductReady",
                Boolean.toString(result.modelProviderOrProductReady()));
        return sha256(out.toString());
    }

    private static Diagnostic firstFailure(Fixture fixture) {
        if (fixture == null) {
            return Diagnostic.FIXTURE_MISSING;
        }
        if (fixture.schemaVersion() == null
                || fixture.authorizationId() == null
                || fixture.contractId() == null
                || fixture.baseCommit() == null
                || fixture.baseTree() == null
                || fixture.declaredFixtureSha256() == null) {
            return Diagnostic.TOP_LEVEL_SCHEMA_INVALID;
        }
        if (!FIXTURE_SCHEMA.equals(fixture.schemaVersion())) {
            return Diagnostic.SCHEMA_VERSION_STALE;
        }
        if (!AUTHORIZATION_ID.equals(fixture.authorizationId())) {
            return Diagnostic.TOP_LEVEL_SCHEMA_INVALID;
        }
        if (!CONTRACT_ID.equals(fixture.contractId())) {
            return Diagnostic.CONTRACT_ID_STALE;
        }
        if (!BASE_COMMIT.equals(fixture.baseCommit()) || !BASE_TREE.equals(fixture.baseTree())) {
            return Diagnostic.BASE_PIN_STALE;
        }

        Diagnostic diagnostic = compareStringCatalog(
                fixture.networkCallLedgerFields(),
                NETWORK_CALL_LEDGER_FIELDS,
                Diagnostic.LEDGER_FIELD_DUPLICATE,
                Diagnostic.LEDGER_FIELD_MISSING,
                Diagnostic.LEDGER_FIELD_EXTRA,
                Diagnostic.LEDGER_FIELD_ORDER_STALE);
        if (diagnostic != Diagnostic.NONE) {
            return diagnostic;
        }
        diagnostic = compareStringCatalog(
                fixture.monitorCalibrationFields(),
                MONITOR_CALIBRATION_FIELDS,
                Diagnostic.CALIBRATION_FIELD_DUPLICATE,
                Diagnostic.CALIBRATION_FIELD_MISSING,
                Diagnostic.CALIBRATION_FIELD_EXTRA,
                Diagnostic.CALIBRATION_FIELD_ORDER_STALE);
        if (diagnostic != Diagnostic.NONE) {
            return diagnostic;
        }
        diagnostic = compareStringCatalog(
                fixture.callKindNames(),
                CALL_KIND_NAMES,
                Diagnostic.CALL_KIND_DUPLICATE,
                Diagnostic.CALL_KIND_MISSING,
                Diagnostic.CALL_KIND_EXTRA,
                Diagnostic.CALL_KIND_ORDER_STALE);
        if (diagnostic != Diagnostic.NONE) {
            return diagnostic;
        }

        diagnostic = validateArtifactPins(fixture.artifactPins());
        if (diagnostic != Diagnostic.NONE) {
            return diagnostic;
        }
        if (!expectedBacklogTruth().equals(fixture.backlogTruth())) {
            return Diagnostic.READINESS_TRUTH_STALE;
        }
        diagnostic = validateBlockers(fixture.readinessBlockers());
        if (diagnostic != Diagnostic.NONE) {
            return diagnostic;
        }
        diagnostic = validateEntries(fixture.entries());
        if (diagnostic != Diagnostic.NONE) {
            return diagnostic;
        }
        if (!isHex64(fixture.declaredFixtureSha256())
                || !FROZEN_FIXTURE_SHA256.equals(fixture.declaredFixtureSha256())
                || !FROZEN_FIXTURE_SHA256.equals(canonicalFixtureSha256(fixture))) {
            return Diagnostic.FIXTURE_HASH_STALE;
        }
        return Diagnostic.NONE;
    }

    private static Diagnostic validateArtifactPins(List<ArtifactPin> actual) {
        if (actual == null) {
            return Diagnostic.ARTIFACT_PIN_MISSING;
        }
        Set<String> paths = new HashSet<>();
        for (ArtifactPin pin : actual) {
            if (pin == null || pin.path() == null || !paths.add(pin.path())) {
                return Diagnostic.ARTIFACT_PIN_DUPLICATE;
            }
        }
        Set<String> expectedPaths = new LinkedHashSet<>();
        for (ArtifactPin pin : EXPECTED_ARTIFACT_PINS) {
            expectedPaths.add(pin.path());
        }
        Set<String> actualPaths = new LinkedHashSet<>(paths);
        if (!actualPaths.containsAll(expectedPaths)) {
            return Diagnostic.ARTIFACT_PIN_MISSING;
        }
        if (!expectedPaths.containsAll(actualPaths)) {
            return Diagnostic.ARTIFACT_PIN_EXTRA;
        }
        for (int index = 0; index < EXPECTED_ARTIFACT_PINS.size(); index++) {
            ArtifactPin expected = EXPECTED_ARTIFACT_PINS.get(index);
            ArtifactPin observed = actual.get(index);
            if (!expected.path().equals(observed.path())) {
                return Diagnostic.ARTIFACT_PIN_ORDER_STALE;
            }
            if (!expected.equals(observed)
                    || observed.bytes() < 0
                    || !isHex64(observed.sha256())) {
                return Diagnostic.ARTIFACT_PIN_STALE;
            }
        }
        return Diagnostic.NONE;
    }

    private static Diagnostic validateBlockers(List<ReadinessBlocker> actual) {
        if (actual == null) {
            return Diagnostic.BLOCKER_MISSING;
        }
        Set<String> ids = new HashSet<>();
        for (ReadinessBlocker blocker : actual) {
            if (blocker == null || blocker.id() == null || !ids.add(blocker.id())) {
                return Diagnostic.BLOCKER_DUPLICATE;
            }
        }
        Set<String> expectedIds = new LinkedHashSet<>();
        for (ReadinessBlocker blocker : EXPECTED_BLOCKERS) {
            expectedIds.add(blocker.id());
        }
        if (!ids.containsAll(expectedIds)) {
            return Diagnostic.BLOCKER_MISSING;
        }
        if (!expectedIds.containsAll(ids)) {
            return Diagnostic.BLOCKER_EXTRA;
        }
        for (int index = 0; index < EXPECTED_BLOCKERS.size(); index++) {
            ReadinessBlocker expected = EXPECTED_BLOCKERS.get(index);
            ReadinessBlocker observed = actual.get(index);
            if (!expected.id().equals(observed.id())) {
                return Diagnostic.BLOCKER_ORDER_STALE;
            }
            if (!expected.equals(observed)) {
                return Diagnostic.BLOCKER_STATE_STALE;
            }
        }
        return Diagnostic.NONE;
    }

    private static Diagnostic validateEntries(List<LedgerEntry> actual) {
        if (actual == null) {
            return Diagnostic.ENTRY_MISSING;
        }
        Set<Long> sequences = new HashSet<>();
        Set<String> eventIds = new HashSet<>();
        for (LedgerEntry entry : actual) {
            if (entry == null
                    || !sequences.add(entry.sequence())
                    || entry.calibrationEventId() == null
                    || !eventIds.add(entry.calibrationEventId())) {
                return Diagnostic.ENTRY_DUPLICATE;
            }
        }
        List<LedgerEntry> expected = expectedEntries();
        Set<String> expectedKeys = entryKeys(expected);
        Set<String> actualKeys = entryKeys(actual);
        if (!actualKeys.containsAll(expectedKeys)) {
            return Diagnostic.ENTRY_MISSING;
        }
        if (!expectedKeys.containsAll(actualKeys)) {
            return Diagnostic.ENTRY_EXTRA;
        }
        if (actual.size() != expected.size()) {
            return actual.size() < expected.size()
                    ? Diagnostic.ENTRY_MISSING
                    : Diagnostic.ENTRY_EXTRA;
        }
        for (int index = 0; index < expected.size(); index++) {
            LedgerEntry observed = actual.get(index);
            LedgerEntry pinned = expected.get(index);
            if (!entryKey(pinned).equals(entryKey(observed))) {
                return Diagnostic.ENTRY_ORDER_STALE;
            }
            if (!isStructurallyValidEntry(observed)) {
                return Diagnostic.ENTRY_SCHEMA_INVALID;
            }
            if (!pinned.equals(observed)) {
                return Diagnostic.ENTRY_STALE;
            }
        }
        return Diagnostic.NONE;
    }

    private static boolean isStructurallyValidEntry(LedgerEntry entry) {
        return entry.sequence() > 0
                && entry.windowClass() != null
                && entry.windowClass() != WindowClass.MEASURED_SCENARIO
                && !entry.attributedToMeasuredScenario()
                && isCatalogToken(entry.calibrationEventId())
                && isCatalogToken(entry.appUidClass())
                && isCatalogToken(entry.processClass())
                && isCatalogToken(entry.componentClass())
                && isCatalogToken(entry.scenarioId())
                && isCatalogToken(entry.actionId())
                && entry.callKind() != null
                && isCatalogToken(entry.destinationClass())
                && isCatalogToken(entry.endpointId())
                && isHex64(entry.consentProfileDigest())
                && isCatalogToken(entry.decision())
                && isCatalogToken(entry.outcome())
                && isCatalogToken(entry.retryState())
                && entry.monotonicOffsetMs() >= 0;
    }

    private static Diagnostic compareStringCatalog(
            List<String> actual,
            List<String> expected,
            Diagnostic duplicate,
            Diagnostic missing,
            Diagnostic extra,
            Diagnostic staleOrder) {
        if (actual == null) {
            return missing;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : actual) {
            if (value == null || !unique.add(value)) {
                return duplicate;
            }
        }
        Set<String> expectedSet = new LinkedHashSet<>(expected);
        if (!unique.containsAll(expectedSet)) {
            return missing;
        }
        if (!expectedSet.containsAll(unique)) {
            return extra;
        }
        return actual.equals(expected) ? Diagnostic.NONE : staleOrder;
    }

    private static ValidationResult invalidResult(Diagnostic diagnostic) {
        return new ValidationResult(
                false,
                diagnostic,
                PROOF_CLASS,
                POC_TRUTH,
                null,
                0,
                0,
                0,
                0,
                10,
                0,
                0,
                Map.of(),
                false,
                false,
                false,
                false,
                false);
    }

    private static BacklogTruth expectedBacklogTruth() {
        return new BacklogTruth(
                "POC-OFFLINE-001", "TODO", "NOT_READY", "NOT_RUN", "NOT_AUTHORIZED");
    }

    private static List<LedgerEntry> expectedEntries() {
        List<LedgerEntry> entries = new ArrayList<>(CallKind.values().length * 2);
        long sequence = 1;
        for (int pass = 0; pass < 2; pass++) {
            boolean pre = pass == 0;
            WindowClass window = pre ? WindowClass.CALIBRATION_PRE : WindowClass.CALIBRATION_POST;
            String phase = pre ? "PRE" : "POST";
            String actionId = pre ? "OFF-I1-ACT-019-01" : "OFF-I1-ACT-019-05";
            long offsetBase = pre ? 0 : 100;
            for (CallKind kind : CallKind.values()) {
                int ordinal = kind.ordinal() + 1;
                entries.add(new LedgerEntry(
                        sequence++,
                        window,
                        "OFF-I3-CAL-" + phase + "-" + threeDigits(ordinal),
                        false,
                        "DORA_APP_UID_CLASS_SYNTHETIC",
                        "DORA_PROCESS_CLASS_SYNTHETIC",
                        "STATIC_SCHEMA_CANARY",
                        "OFF-SYN-019",
                        actionId,
                        kind,
                        "FORBIDDEN_CALL_KIND_SCHEMA_CANARY",
                        "OFF-I3-OPAQUE-ENDPOINT-" + threeDigits(ordinal),
                        CONSENT_PROFILE_DIGEST,
                        "STATIC_FIXTURE_CANARY",
                        "STRUCTURE_ONLY_NOT_OBSERVED",
                        "NOT_APPLICABLE",
                        offsetBase + kind.ordinal()));
            }
        }
        return List.copyOf(entries);
    }

    private static Set<String> entryKeys(List<LedgerEntry> entries) {
        Set<String> keys = new LinkedHashSet<>();
        for (LedgerEntry entry : entries) {
            if (entry != null) {
                keys.add(entryKey(entry));
            }
        }
        return keys;
    }

    private static String entryKey(LedgerEntry entry) {
        return entry.sequence()
                + "|"
                + nameOrNull(entry.windowClass())
                + "|"
                + nameOrNull(entry.callKind())
                + "|"
                + entry.calibrationEventId();
    }

    private static void appendArtifactPins(StringBuilder out, List<ArtifactPin> pins) {
        if (pins == null) {
            append(out, "artifactPins", null);
            return;
        }
        append(out, "artifactPinCount", Integer.toString(pins.size()));
        for (ArtifactPin pin : pins) {
            if (pin == null) {
                append(out, "artifactPin", null);
            } else {
                append(out, "artifactPath", pin.path());
                append(out, "artifactBytes", Long.toString(pin.bytes()));
                append(out, "artifactSha256", pin.sha256());
            }
        }
    }

    private static void appendBacklogTruth(StringBuilder out, BacklogTruth truth) {
        if (truth == null) {
            append(out, "backlogTruth", null);
            return;
        }
        append(out, "backlogId", truth.id());
        append(out, "backlogStatus", truth.status());
        append(out, "backlogReadiness", truth.readiness());
        append(out, "backlogIntegratedExecution", truth.integratedExecution());
        append(out, "backlogAuthorization", truth.authorization());
    }

    private static void appendBlockers(StringBuilder out, List<ReadinessBlocker> blockers) {
        if (blockers == null) {
            append(out, "readinessBlockers", null);
            return;
        }
        append(out, "readinessBlockerCount", Integer.toString(blockers.size()));
        for (ReadinessBlocker blocker : blockers) {
            if (blocker == null) {
                append(out, "readinessBlocker", null);
            } else {
                append(out, "readinessBlockerId", blocker.id());
                append(out, "readinessBlockerState", blocker.state());
            }
        }
    }

    private static void appendEntries(StringBuilder out, List<LedgerEntry> entries) {
        if (entries == null) {
            append(out, "entries", null);
            return;
        }
        append(out, "entryCount", Integer.toString(entries.size()));
        for (LedgerEntry entry : entries) {
            if (entry == null) {
                append(out, "entry", null);
                continue;
            }
            append(out, "sequence", Long.toString(entry.sequence()));
            append(out, "windowClass", nameOrNull(entry.windowClass()));
            append(out, "calibrationEventId", entry.calibrationEventId());
            append(
                    out,
                    "attributedToMeasuredScenario",
                    Boolean.toString(entry.attributedToMeasuredScenario()));
            append(out, "appUidClass", entry.appUidClass());
            append(out, "processClass", entry.processClass());
            append(out, "componentClass", entry.componentClass());
            append(out, "scenarioId", entry.scenarioId());
            append(out, "actionId", entry.actionId());
            append(out, "callKind", nameOrNull(entry.callKind()));
            append(out, "destinationClass", entry.destinationClass());
            append(out, "endpointId", entry.endpointId());
            append(out, "consentProfileDigest", entry.consentProfileDigest());
            append(out, "decision", entry.decision());
            append(out, "outcome", entry.outcome());
            append(out, "retryState", entry.retryState());
            append(out, "monotonicOffsetMs", Long.toString(entry.monotonicOffsetMs()));
        }
    }

    private static void appendStrings(StringBuilder out, String label, List<String> values) {
        if (values == null) {
            append(out, label, null);
            return;
        }
        append(out, label + "Count", Integer.toString(values.size()));
        for (String value : values) {
            append(out, label + "Item", value);
        }
    }

    private static void append(StringBuilder out, String name, String value) {
        out.append(name.length()).append(':').append(name).append('=');
        if (value == null) {
            out.append('N').append('\n');
        } else {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            out.append('S').append(bytes.length).append(':').append(value).append('\n');
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return lowercaseHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA_256_UNAVAILABLE", error);
        }
    }

    private static String lowercaseHex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = alphabet[value >>> 4];
            result[index * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(result);
    }

    private static boolean isHex64(String value) {
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

    private static boolean isCatalogToken(String value) {
        if (value == null || value.isEmpty() || value.length() > 128) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '_'
                    || character == '-')) {
                return false;
            }
        }
        return true;
    }

    private static String threeDigits(int value) {
        if (value < 1 || value > 999) {
            throw new IllegalArgumentException("CATALOG_ORDINAL_OUT_OF_RANGE");
        }
        if (value < 10) {
            return "00" + value;
        }
        if (value < 100) {
            return "0" + value;
        }
        return Integer.toString(value);
    }

    private static String nameOrNull(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static <T> List<T> immutableCopyOrNull(List<T> value) {
        return value == null ? null : List.copyOf(value);
    }

    private static Map<CallKind, Coverage> immutableCoverageOrNull(
            Map<CallKind, Coverage> value) {
        if (value == null) {
            return null;
        }
        EnumMap<CallKind, Coverage> copy = new EnumMap<>(CallKind.class);
        copy.putAll(value);
        return Collections.unmodifiableMap(copy);
    }
}
