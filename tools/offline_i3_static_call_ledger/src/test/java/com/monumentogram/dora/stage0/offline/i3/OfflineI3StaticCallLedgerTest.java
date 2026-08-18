package com.monumentogram.dora.stage0.offline.i3;

import com.monumentogram.dora.stage0.offline.i3.OfflineI3StaticCallLedger.ArtifactPin;
import com.monumentogram.dora.stage0.offline.i3.OfflineI3StaticCallLedger.BacklogTruth;
import com.monumentogram.dora.stage0.offline.i3.OfflineI3StaticCallLedger.CallKind;
import com.monumentogram.dora.stage0.offline.i3.OfflineI3StaticCallLedger.Diagnostic;
import com.monumentogram.dora.stage0.offline.i3.OfflineI3StaticCallLedger.Fixture;
import com.monumentogram.dora.stage0.offline.i3.OfflineI3StaticCallLedger.LedgerEntry;
import com.monumentogram.dora.stage0.offline.i3.OfflineI3StaticCallLedger.ReadinessBlocker;
import com.monumentogram.dora.stage0.offline.i3.OfflineI3StaticCallLedger.ValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OfflineI3StaticCallLedgerTest {
    private static final String EXPECTED_FIXTURE_SHA256 =
            "1aa27452ebed89a2b12a3d9a4c59644abe9d73bd9042d21cc325b37b0df92fa8";
    private static final String EXPECTED_RESULT_SHA256 =
            "d58d2a2fbf5ac5f8180b18326ab1139d042f28d59c64daa7ee8b0bf88cd3bbc9";

    private OfflineI3StaticCallLedgerTest() {}

    public static void main(String[] arguments) {
        require(arguments.length == 0, "arguments are forbidden");
        exactSchemasAndCatalogsAreIndependentlyPinned();
        exactPinsTruthAndBlockersAreFrozen();
        canonicalKnownAnswersAndValidResultAreDeterministic();
        fixtureAndResultAreDeeplyImmutable();
        schemaMissingExtraDuplicateAndOrderDriftFailClosed();
        artifactPinDriftFailsClosed();
        readinessAndBlockerDriftFailClosed();
        entryMissingExtraDuplicateOrderMalformedAndStaleDriftFailClosed();
        staleTopLevelAndHashPinsFailClosed();
        invalidCandidateCannotMutateAcceptedFixtureOrResult();
        System.out.println("PASS offline-i3-static-call-ledger");
    }

    private static void exactSchemasAndCatalogsAreIndependentlyPinned() {
        List<String> expectedLedgerFields = List.of(
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
        List<String> expectedCalibrationFields = List.of(
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
        List<String> expectedKinds = List.of(
                "DNS",
                "SOCKET",
                "HTTP",
                "GMS_BIND",
                "ACCOUNT_AUTH",
                "REMOTE_CONFIG",
                "ANALYTICS",
                "PROVIDER");

        requireEquals(expectedLedgerFields, OfflineI3StaticCallLedger.NETWORK_CALL_LEDGER_FIELDS);
        requireEquals(expectedCalibrationFields, OfflineI3StaticCallLedger.MONITOR_CALIBRATION_FIELDS);
        requireEquals(expectedKinds, OfflineI3StaticCallLedger.CALL_KIND_NAMES);
        requireEquals(expectedKinds, enumNames(CallKind.values()));
        requireEquals(17, expectedLedgerFields.size());
        requireEquals(11, expectedCalibrationFields.size());
        requireEquals(8, expectedKinds.size());
    }

    private static void exactPinsTruthAndBlockersAreFrozen() {
        Fixture fixture = OfflineI3StaticCallLedger.frozenFixture();
        requireEquals("poc-offline-i3-static-call-ledger-fixture-v0.1", fixture.schemaVersion());
        requireEquals(
                "POC-OFFLINE-001-I3-STATIC-CALL-LEDGER-AUTH-20260818-01",
                fixture.authorizationId());
        requireEquals("poc-offline-readiness-stage0-v0.1", fixture.contractId());
        requireEquals("881c5bfbb84cedfc2d267ca8f8255c456b3908b8", fixture.baseCommit());
        requireEquals("0366351b73fd68adc713b22b587926330aed4bab", fixture.baseTree());

        requireEquals(10, fixture.artifactPins().size());
        requireEquals(
                new ArtifactPin(
                        "docs/stage0/DORA_MVP1_POC_OFFLINE_READINESS_CONTRACT_STAGE0_V0_1.md",
                        45_377,
                        "9a905eabbcd75601fc598cccc001dcff56a5b6b65eeb936f2a9c4602d658682a"),
                fixture.artifactPins().get(0));
        requireEquals(
                new ArtifactPin(
                        "docs/evidence/stage0-host-oracle-publication-closure-2026-08-18.json",
                        9_746,
                        "d11aba83be1fe2664eda3ae847386967239b4ad09280bb2a5bd7002fed70d1d8"),
                fixture.artifactPins().get(9));

        requireEquals(
                new BacklogTruth(
                        "POC-OFFLINE-001",
                        "TODO",
                        "NOT_READY",
                        "NOT_RUN",
                        "NOT_AUTHORIZED"),
                fixture.backlogTruth());
        requireEquals(10, fixture.readinessBlockers().size());
        for (int index = 0; index < 10; index++) {
            requireEquals(
                    new ReadinessBlocker("OFF-RDY-" + twoDigits(index + 1), "BLOCKED"),
                    fixture.readinessBlockers().get(index));
        }
        for (LedgerEntry entry : fixture.entries()) {
            requireEquals(
                    "913f222095eb3894f928956d2e0cd14d19d8c4dbe9c5332028b59ce3330b19dd",
                    entry.consentProfileDigest());
        }
    }

    private static void canonicalKnownAnswersAndValidResultAreDeterministic() {
        Fixture firstFixture = OfflineI3StaticCallLedger.frozenFixture();
        Fixture secondFixture = OfflineI3StaticCallLedger.frozenFixture();
        String firstFixtureDigest = OfflineI3StaticCallLedger.canonicalFixtureSha256(firstFixture);
        String secondFixtureDigest = OfflineI3StaticCallLedger.canonicalFixtureSha256(secondFixture);
        requireEquals(EXPECTED_FIXTURE_SHA256, firstFixtureDigest);
        requireEquals(firstFixtureDigest, secondFixtureDigest);
        requireEquals(EXPECTED_FIXTURE_SHA256, firstFixture.declaredFixtureSha256());

        ValidationResult first = OfflineI3StaticCallLedger.validate(firstFixture);
        ValidationResult second = OfflineI3StaticCallLedger.validate(secondFixture);
        require(first.structureValid(), "frozen fixture must validate");
        requireEquals(Diagnostic.NONE, first.diagnostic());
        requireEquals(first, second);
        requireEquals(EXPECTED_RESULT_SHA256, OfflineI3StaticCallLedger.canonicalResultSha256(first));
        requireEquals(EXPECTED_RESULT_SHA256, OfflineI3StaticCallLedger.canonicalResultSha256(second));

        requireEquals(
                "STATIC_SYNTHETIC_CALL_LEDGER_STRUCTURE_ONLY_NOT_RUNTIME_EVIDENCE",
                first.proofClass());
        requireEquals("TODO_NOT_READY_NOT_RUN_NOT_AUTHORIZED", first.pocTruth());
        requireEquals(17, first.ledgerFieldCount());
        requireEquals(11, first.calibrationFieldCount());
        requireEquals(8, first.callKindCount());
        requireEquals(10, first.artifactPinCount());
        requireEquals(10, first.blockerCount());
        requireEquals(16, first.staticRowCount());
        requireEquals(0, first.measuredRowCount());
        require(!first.monitorCalibrated(), "static fixture cannot calibrate a monitor");
        require(!first.runtimeZeroCallProven(), "static fixture cannot prove runtime zero calls");
        require(!first.d4NoGmsDeviceProven(), "static fixture cannot prove D4/no-GMS");
        require(!first.offlineJourneyProven(), "static fixture cannot prove an offline journey");
        require(
                !first.modelProviderOrProductReady(),
                "static fixture cannot make model/provider/product ready");
        for (CallKind kind : CallKind.values()) {
            requireEquals(1, first.coverage().get(kind).preStaticRows());
            requireEquals(1, first.coverage().get(kind).postStaticRows());
            requireEquals(0, first.coverage().get(kind).measuredRows());
        }
    }

    private static void fixtureAndResultAreDeeplyImmutable() {
        Fixture frozen = OfflineI3StaticCallLedger.frozenFixture();
        assertThrows(UnsupportedOperationException.class, () -> frozen.entries().add(frozen.entries().get(0)));
        assertThrows(UnsupportedOperationException.class, () -> frozen.artifactPins().clear());
        assertThrows(UnsupportedOperationException.class, () -> frozen.readinessBlockers().clear());
        assertThrows(UnsupportedOperationException.class, () -> frozen.callKindNames().clear());

        List<LedgerEntry> callerOwned = new ArrayList<>(frozen.entries());
        Fixture copied = withEntries(frozen, callerOwned);
        callerOwned.clear();
        requireEquals(16, copied.entries().size());

        ValidationResult result = OfflineI3StaticCallLedger.validate(frozen);
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.coverage().put(CallKind.DNS, result.coverage().get(CallKind.DNS)));
    }

    private static void schemaMissingExtraDuplicateAndOrderDriftFailClosed() {
        Fixture fixture = OfflineI3StaticCallLedger.frozenFixture();

        requireDiagnostic(
                withLedgerFields(fixture, duplicateFirst(fixture.networkCallLedgerFields())),
                Diagnostic.LEDGER_FIELD_DUPLICATE);
        requireDiagnostic(
                withLedgerFields(fixture, withoutLast(fixture.networkCallLedgerFields())),
                Diagnostic.LEDGER_FIELD_MISSING);
        requireDiagnostic(
                withLedgerFields(fixture, withExtra(fixture.networkCallLedgerFields(), "unexpected")),
                Diagnostic.LEDGER_FIELD_EXTRA);
        requireDiagnostic(
                withLedgerFields(fixture, swapFirstTwo(fixture.networkCallLedgerFields())),
                Diagnostic.LEDGER_FIELD_ORDER_STALE);

        requireDiagnostic(
                withCalibrationFields(fixture, duplicateFirst(fixture.monitorCalibrationFields())),
                Diagnostic.CALIBRATION_FIELD_DUPLICATE);
        requireDiagnostic(
                withCalibrationFields(fixture, withoutLast(fixture.monitorCalibrationFields())),
                Diagnostic.CALIBRATION_FIELD_MISSING);
        requireDiagnostic(
                withCalibrationFields(
                        fixture, withExtra(fixture.monitorCalibrationFields(), "unexpected")),
                Diagnostic.CALIBRATION_FIELD_EXTRA);
        requireDiagnostic(
                withCalibrationFields(fixture, swapFirstTwo(fixture.monitorCalibrationFields())),
                Diagnostic.CALIBRATION_FIELD_ORDER_STALE);

        requireDiagnostic(
                withCallKinds(fixture, duplicateFirst(fixture.callKindNames())),
                Diagnostic.CALL_KIND_DUPLICATE);
        requireDiagnostic(
                withCallKinds(fixture, withoutLast(fixture.callKindNames())),
                Diagnostic.CALL_KIND_MISSING);
        requireDiagnostic(
                withCallKinds(fixture, withExtra(fixture.callKindNames(), "UNKNOWN_KIND")),
                Diagnostic.CALL_KIND_EXTRA);
        requireDiagnostic(
                withCallKinds(fixture, swapFirstTwo(fixture.callKindNames())),
                Diagnostic.CALL_KIND_ORDER_STALE);
    }

    private static void artifactPinDriftFailsClosed() {
        Fixture fixture = OfflineI3StaticCallLedger.frozenFixture();
        List<ArtifactPin> duplicate = new ArrayList<>(fixture.artifactPins());
        duplicate.add(fixture.artifactPins().get(0));
        requireDiagnostic(withArtifactPins(fixture, duplicate), Diagnostic.ARTIFACT_PIN_DUPLICATE);
        requireDiagnostic(
                withArtifactPins(fixture, withoutLast(fixture.artifactPins())),
                Diagnostic.ARTIFACT_PIN_MISSING);

        List<ArtifactPin> extra = new ArrayList<>(fixture.artifactPins());
        extra.add(new ArtifactPin(
                "docs/evidence/poc-offline-001/extra.json",
                1,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        requireDiagnostic(withArtifactPins(fixture, extra), Diagnostic.ARTIFACT_PIN_EXTRA);
        requireDiagnostic(
                withArtifactPins(fixture, swapFirstTwo(fixture.artifactPins())),
                Diagnostic.ARTIFACT_PIN_ORDER_STALE);

        List<ArtifactPin> stale = new ArrayList<>(fixture.artifactPins());
        ArtifactPin first = stale.get(0);
        stale.set(0, new ArtifactPin(first.path(), first.bytes() + 1, first.sha256()));
        requireDiagnostic(withArtifactPins(fixture, stale), Diagnostic.ARTIFACT_PIN_STALE);
    }

    private static void readinessAndBlockerDriftFailClosed() {
        Fixture fixture = OfflineI3StaticCallLedger.frozenFixture();
        requireDiagnostic(
                withBacklogTruth(
                        fixture,
                        new BacklogTruth(
                                "POC-OFFLINE-001",
                                "DONE",
                                "READY",
                                "PASS",
                                "AUTHORIZED")),
                Diagnostic.READINESS_TRUTH_STALE);

        List<ReadinessBlocker> duplicate = new ArrayList<>(fixture.readinessBlockers());
        duplicate.add(fixture.readinessBlockers().get(0));
        requireDiagnostic(withBlockers(fixture, duplicate), Diagnostic.BLOCKER_DUPLICATE);
        requireDiagnostic(
                withBlockers(fixture, withoutLast(fixture.readinessBlockers())),
                Diagnostic.BLOCKER_MISSING);
        List<ReadinessBlocker> extra = new ArrayList<>(fixture.readinessBlockers());
        extra.add(new ReadinessBlocker("OFF-RDY-11", "BLOCKED"));
        requireDiagnostic(withBlockers(fixture, extra), Diagnostic.BLOCKER_EXTRA);
        requireDiagnostic(
                withBlockers(fixture, swapFirstTwo(fixture.readinessBlockers())),
                Diagnostic.BLOCKER_ORDER_STALE);
        List<ReadinessBlocker> stale = new ArrayList<>(fixture.readinessBlockers());
        stale.set(4, new ReadinessBlocker("OFF-RDY-05", "CLOSED"));
        requireDiagnostic(withBlockers(fixture, stale), Diagnostic.BLOCKER_STATE_STALE);
    }

    private static void entryMissingExtraDuplicateOrderMalformedAndStaleDriftFailClosed() {
        Fixture fixture = OfflineI3StaticCallLedger.frozenFixture();
        List<LedgerEntry> duplicate = new ArrayList<>(fixture.entries());
        duplicate.add(fixture.entries().get(0));
        requireDiagnostic(withEntries(fixture, duplicate), Diagnostic.ENTRY_DUPLICATE);
        requireDiagnostic(
                withEntries(fixture, withoutLast(fixture.entries())), Diagnostic.ENTRY_MISSING);

        List<LedgerEntry> extra = new ArrayList<>(fixture.entries());
        LedgerEntry last = fixture.entries().get(fixture.entries().size() - 1);
        extra.add(copyEntry(last, 17, "OFF-I3-CAL-POST-009", last.actionId(), last.outcome()));
        requireDiagnostic(withEntries(fixture, extra), Diagnostic.ENTRY_EXTRA);
        requireDiagnostic(
                withEntries(fixture, swapFirstTwo(fixture.entries())),
                Diagnostic.ENTRY_ORDER_STALE);

        List<LedgerEntry> malformed = new ArrayList<>(fixture.entries());
        LedgerEntry first = malformed.get(0);
        malformed.set(0, copyEntry(first, first.sequence(), first.calibrationEventId(), "bad value", first.outcome()));
        requireDiagnostic(withEntries(fixture, malformed), Diagnostic.ENTRY_SCHEMA_INVALID);

        List<LedgerEntry> stale = new ArrayList<>(fixture.entries());
        stale.set(
                0,
                copyEntry(
                        first,
                        first.sequence(),
                        first.calibrationEventId(),
                        first.actionId(),
                        "STRUCTURE_ONLY_NOT_OBSERVED_STALE"));
        requireDiagnostic(withEntries(fixture, stale), Diagnostic.ENTRY_STALE);
    }

    private static void staleTopLevelAndHashPinsFailClosed() {
        Fixture fixture = OfflineI3StaticCallLedger.frozenFixture();
        requireDiagnostic(null, Diagnostic.FIXTURE_MISSING);
        requireDiagnostic(withSchema(fixture, null), Diagnostic.TOP_LEVEL_SCHEMA_INVALID);
        requireDiagnostic(
                withSchema(fixture, "poc-offline-i3-static-call-ledger-fixture-v0.2"),
                Diagnostic.SCHEMA_VERSION_STALE);
        requireDiagnostic(withContract(fixture, "stale-contract"), Diagnostic.CONTRACT_ID_STALE);
        requireDiagnostic(
                withBase(fixture, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", fixture.baseTree()),
                Diagnostic.BASE_PIN_STALE);
        requireDiagnostic(
                withDeclaredHash(
                        fixture,
                        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
                Diagnostic.FIXTURE_HASH_STALE);
    }

    private static void invalidCandidateCannotMutateAcceptedFixtureOrResult() {
        Fixture accepted = OfflineI3StaticCallLedger.frozenFixture();
        ValidationResult before = OfflineI3StaticCallLedger.validate(accepted);
        String beforeFixtureDigest = OfflineI3StaticCallLedger.canonicalFixtureSha256(accepted);
        String beforeResultDigest = OfflineI3StaticCallLedger.canonicalResultSha256(before);

        List<LedgerEntry> alteredEntries = new ArrayList<>(accepted.entries());
        LedgerEntry first = alteredEntries.get(0);
        alteredEntries.set(
                0,
                copyEntry(
                        first,
                        first.sequence(),
                        first.calibrationEventId(),
                        first.actionId(),
                        "STRUCTURE_ONLY_NOT_OBSERVED_STALE"));
        ValidationResult rejected = OfflineI3StaticCallLedger.validate(
                withEntries(accepted, alteredEntries));
        require(!rejected.structureValid(), "altered candidate must fail closed");
        requireEquals(Diagnostic.ENTRY_STALE, rejected.diagnostic());
        requireEquals(0, rejected.staticRowCount());
        requireEquals(0, rejected.measuredRowCount());
        require(!rejected.runtimeZeroCallProven(), "rejection must not create runtime evidence");

        ValidationResult after = OfflineI3StaticCallLedger.validate(accepted);
        requireEquals(beforeFixtureDigest, OfflineI3StaticCallLedger.canonicalFixtureSha256(accepted));
        requireEquals(before, after);
        requireEquals(beforeResultDigest, OfflineI3StaticCallLedger.canonicalResultSha256(after));
    }

    private static void requireDiagnostic(Fixture fixture, Diagnostic expected) {
        ValidationResult result = OfflineI3StaticCallLedger.validate(fixture);
        require(!result.structureValid(), "invalid fixture must fail closed: " + expected);
        requireEquals(expected, result.diagnostic());
        requireEquals("TODO_NOT_READY_NOT_RUN_NOT_AUTHORIZED", result.pocTruth());
        requireEquals(10, result.blockerCount());
        requireEquals(0, result.staticRowCount());
        requireEquals(0, result.measuredRowCount());
        require(!result.monitorCalibrated(), "invalid result cannot calibrate monitor");
        require(!result.runtimeZeroCallProven(), "invalid result cannot prove zero calls");
        require(!result.d4NoGmsDeviceProven(), "invalid result cannot prove D4");
        require(!result.offlineJourneyProven(), "invalid result cannot prove journey");
        require(!result.modelProviderOrProductReady(), "invalid result cannot make product ready");
    }

    private static Fixture withSchema(Fixture value, String schema) {
        return copyFixture(
                value,
                schema,
                value.contractId(),
                value.baseCommit(),
                value.baseTree(),
                value.networkCallLedgerFields(),
                value.monitorCalibrationFields(),
                value.callKindNames(),
                value.artifactPins(),
                value.backlogTruth(),
                value.readinessBlockers(),
                value.entries(),
                value.declaredFixtureSha256());
    }

    private static Fixture withContract(Fixture value, String contract) {
        return copyFixture(
                value,
                value.schemaVersion(),
                contract,
                value.baseCommit(),
                value.baseTree(),
                value.networkCallLedgerFields(),
                value.monitorCalibrationFields(),
                value.callKindNames(),
                value.artifactPins(),
                value.backlogTruth(),
                value.readinessBlockers(),
                value.entries(),
                value.declaredFixtureSha256());
    }

    private static Fixture withBase(Fixture value, String commit, String tree) {
        return copyFixture(
                value,
                value.schemaVersion(),
                value.contractId(),
                commit,
                tree,
                value.networkCallLedgerFields(),
                value.monitorCalibrationFields(),
                value.callKindNames(),
                value.artifactPins(),
                value.backlogTruth(),
                value.readinessBlockers(),
                value.entries(),
                value.declaredFixtureSha256());
    }

    private static Fixture withLedgerFields(Fixture value, List<String> fields) {
        return copyFixture(
                value,
                value.schemaVersion(),
                value.contractId(),
                value.baseCommit(),
                value.baseTree(),
                fields,
                value.monitorCalibrationFields(),
                value.callKindNames(),
                value.artifactPins(),
                value.backlogTruth(),
                value.readinessBlockers(),
                value.entries(),
                value.declaredFixtureSha256());
    }

    private static Fixture withCalibrationFields(Fixture value, List<String> fields) {
        return copyFixture(
                value,
                value.schemaVersion(),
                value.contractId(),
                value.baseCommit(),
                value.baseTree(),
                value.networkCallLedgerFields(),
                fields,
                value.callKindNames(),
                value.artifactPins(),
                value.backlogTruth(),
                value.readinessBlockers(),
                value.entries(),
                value.declaredFixtureSha256());
    }

    private static Fixture withCallKinds(Fixture value, List<String> kinds) {
        return copyFixture(
                value,
                value.schemaVersion(),
                value.contractId(),
                value.baseCommit(),
                value.baseTree(),
                value.networkCallLedgerFields(),
                value.monitorCalibrationFields(),
                kinds,
                value.artifactPins(),
                value.backlogTruth(),
                value.readinessBlockers(),
                value.entries(),
                value.declaredFixtureSha256());
    }

    private static Fixture withArtifactPins(Fixture value, List<ArtifactPin> pins) {
        return copyFixture(
                value,
                value.schemaVersion(),
                value.contractId(),
                value.baseCommit(),
                value.baseTree(),
                value.networkCallLedgerFields(),
                value.monitorCalibrationFields(),
                value.callKindNames(),
                pins,
                value.backlogTruth(),
                value.readinessBlockers(),
                value.entries(),
                value.declaredFixtureSha256());
    }

    private static Fixture withBacklogTruth(Fixture value, BacklogTruth truth) {
        return copyFixture(
                value,
                value.schemaVersion(),
                value.contractId(),
                value.baseCommit(),
                value.baseTree(),
                value.networkCallLedgerFields(),
                value.monitorCalibrationFields(),
                value.callKindNames(),
                value.artifactPins(),
                truth,
                value.readinessBlockers(),
                value.entries(),
                value.declaredFixtureSha256());
    }

    private static Fixture withBlockers(Fixture value, List<ReadinessBlocker> blockers) {
        return copyFixture(
                value,
                value.schemaVersion(),
                value.contractId(),
                value.baseCommit(),
                value.baseTree(),
                value.networkCallLedgerFields(),
                value.monitorCalibrationFields(),
                value.callKindNames(),
                value.artifactPins(),
                value.backlogTruth(),
                blockers,
                value.entries(),
                value.declaredFixtureSha256());
    }

    private static Fixture withEntries(Fixture value, List<LedgerEntry> entries) {
        return copyFixture(
                value,
                value.schemaVersion(),
                value.contractId(),
                value.baseCommit(),
                value.baseTree(),
                value.networkCallLedgerFields(),
                value.monitorCalibrationFields(),
                value.callKindNames(),
                value.artifactPins(),
                value.backlogTruth(),
                value.readinessBlockers(),
                entries,
                value.declaredFixtureSha256());
    }

    private static Fixture withDeclaredHash(Fixture value, String hash) {
        return copyFixture(
                value,
                value.schemaVersion(),
                value.contractId(),
                value.baseCommit(),
                value.baseTree(),
                value.networkCallLedgerFields(),
                value.monitorCalibrationFields(),
                value.callKindNames(),
                value.artifactPins(),
                value.backlogTruth(),
                value.readinessBlockers(),
                value.entries(),
                hash);
    }

    private static Fixture copyFixture(
            Fixture value,
            String schema,
            String contract,
            String commit,
            String tree,
            List<String> ledgerFields,
            List<String> calibrationFields,
            List<String> callKinds,
            List<ArtifactPin> pins,
            BacklogTruth truth,
            List<ReadinessBlocker> blockers,
            List<LedgerEntry> entries,
            String declaredHash) {
        return new Fixture(
                schema,
                value.authorizationId(),
                contract,
                commit,
                tree,
                ledgerFields,
                calibrationFields,
                callKinds,
                pins,
                truth,
                blockers,
                entries,
                declaredHash);
    }

    private static LedgerEntry copyEntry(
            LedgerEntry value,
            long sequence,
            String eventId,
            String actionId,
            String outcome) {
        return new LedgerEntry(
                sequence,
                value.windowClass(),
                eventId,
                value.attributedToMeasuredScenario(),
                value.appUidClass(),
                value.processClass(),
                value.componentClass(),
                value.scenarioId(),
                actionId,
                value.callKind(),
                value.destinationClass(),
                value.endpointId(),
                value.consentProfileDigest(),
                value.decision(),
                outcome,
                value.retryState(),
                value.monotonicOffsetMs());
    }

    private static List<String> enumNames(CallKind[] values) {
        List<String> names = new ArrayList<>(values.length);
        for (CallKind value : values) {
            names.add(value.name());
        }
        return List.copyOf(names);
    }

    private static <T> List<T> duplicateFirst(List<T> values) {
        List<T> copy = new ArrayList<>(values);
        copy.add(values.get(0));
        return copy;
    }

    private static <T> List<T> withoutLast(List<T> values) {
        return new ArrayList<>(values.subList(0, values.size() - 1));
    }

    private static <T> List<T> withExtra(List<T> values, T extra) {
        List<T> copy = new ArrayList<>(values);
        copy.add(extra);
        return copy;
    }

    private static <T> List<T> swapFirstTwo(List<T> values) {
        List<T> copy = new ArrayList<>(values);
        Collections.swap(copy, 0, 1);
        return copy;
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    private static void assertThrows(
            Class<? extends Throwable> expected,
            ThrowingRunnable operation) {
        try {
            operation.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return;
            }
            throw new AssertionError("unexpected exception type: " + actual.getClass().getName(), actual);
        }
        throw new AssertionError("expected exception: " + expected.getName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
