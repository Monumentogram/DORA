package com.monumentogram.dora.stage0.decision.i4;

import com.monumentogram.dora.stage0.decision.i4.DecisionProvenanceSourceValidator.CandidateResult;
import com.monumentogram.dora.stage0.decision.i4.DecisionProvenanceSourceValidator.Diagnostic;
import com.monumentogram.dora.stage0.decision.i4.DecisionProvenanceSourceValidator.GeneratedSource;
import com.monumentogram.dora.stage0.decision.i4.DecisionProvenanceSourceValidator.LanguageSlice;
import com.monumentogram.dora.stage0.decision.i4.DecisionProvenanceSourceValidator.ProvenanceEnvelope;
import com.monumentogram.dora.stage0.decision.i4.DecisionProvenanceSourceValidator.ValidationBatch;
import com.monumentogram.dora.stage0.decision.i4.DecisionProvenanceSourceValidator.ValidationStatus;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Exact generated-fixture tests for the Decision I4 provenance/source mechanics validator. */
public final class DecisionProvenanceSourceValidatorTest {
    private static final String EXPECTED_CANONICAL_SHA256 =
            "eae9b3bd500e99660dfd07092f339a2f49ddd2fef5e2befe5bd24e489b6350ba";

    private static final Map<String, Diagnostic> EXPECTED_REJECTIONS =
            Map.ofEntries(
                    Map.entry("CASE_REJECT_FORGED_SOURCE_ID", Diagnostic.UNKNOWN_SOURCE_ID),
                    Map.entry(
                            "CASE_REJECT_WHOLE_SHA",
                            Diagnostic.WHOLE_SOURCE_SHA256_MISMATCH),
                    Map.entry(
                            "CASE_REJECT_EXCERPT_SHA", Diagnostic.EXCERPT_SHA256_MISMATCH),
                    Map.entry("CASE_REJECT_NEGATIVE_RANGE", Diagnostic.RANGE_NEGATIVE),
                    Map.entry("CASE_REJECT_OUT_OF_RANGE", Diagnostic.RANGE_OUT_OF_BOUNDS),
                    Map.entry("CASE_REJECT_REVERSED_RANGE", Diagnostic.RANGE_REVERSED),
                    Map.entry("CASE_REJECT_EMPTY_RANGE", Diagnostic.RANGE_EMPTY),
                    Map.entry(
                            "CASE_REJECT_MID_UTF8_START",
                            Diagnostic.RANGE_NOT_UTF8_BOUNDARY),
                    Map.entry(
                            "CASE_REJECT_MID_UTF8_END",
                            Diagnostic.RANGE_NOT_UTF8_BOUNDARY),
                    Map.entry(
                            "CASE_REJECT_SCHEMA_VERSION",
                            Diagnostic.SCHEMA_VERSION_MISMATCH),
                    Map.entry(
                            "CASE_REJECT_SOURCE_VERSION",
                            Diagnostic.SOURCE_VERSION_MISMATCH));

    private DecisionProvenanceSourceValidatorTest() {}

    public static void main(String[] args) {
        profileAndGeneratedSourcesAreExact();
        exactGeneratedCasesAcceptOrRejectAsFrozen();
        invalidRangesNeverSliceAndUtf8FaultsTargetContinuationBytes();
        permutationAndRepeatAreDeterministic();
        canonicalOutputIsContentFreeAndClaimBounded();
        publicSnapshotsAndInputsFailClosed();
        ValidationBatch result = DecisionProvenanceSourceValidator.runGeneratedCaseMatrix();
        String canonicalSha256 =
                DecisionProvenanceSourceValidator.sha256Utf8(result.canonicalOutput());
        check(
                canonicalSha256.equals(EXPECTED_CANONICAL_SHA256),
                "canonical digest drifted: " + canonicalSha256);
        System.out.print(
                "POC_DECISION_I4_PROVENANCE_SOURCE_VALIDATOR_TESTS_OK"
                        + " cases="
                        + result.results().size()
                        + " accepted="
                        + result.acceptedCount()
                        + " rejected="
                        + result.rejectedCount()
                        + " canonicalSha256="
                        + canonicalSha256
                        + " claim="
                        + DecisionProvenanceSourceValidator.CLAIM_CEILING
                        + "\n");
    }

    private static void profileAndGeneratedSourcesAreExact() {
        check(
                DecisionProvenanceSourceValidator.PROFILE_VERSION.equals(
                        "decision-i4-provenance-source-validator-stage0-v0.1"),
                "profile drifted");
        check(
                DecisionProvenanceSourceValidator.ENVELOPE_SCHEMA_VERSION.equals(
                        "DORA_DECISION_I4_PROVENANCE_ENVELOPE_STAGE0_V0_1"),
                "schema version drifted");
        check(
                DecisionProvenanceSourceValidator.SOURCE_SET_VERSION.equals(
                        "DORA_DECISION_I4_GENERATED_SOURCE_SET_STAGE0_V0_1"),
                "source version drifted");
        check(
                DecisionProvenanceSourceValidator.RANGE_UNIT.equals(
                        "UTF8_BYTE_OFFSETS_HALF_OPEN_STAGE0_V0_1"),
                "range unit drifted");
        check(
                DecisionProvenanceSourceValidator.CLAIM_CEILING.equals(
                        "DECISION_I4_SYNTHETIC_PROVENANCE_SOURCE_MECHANICS_EXERCISED"),
                "claim ceiling drifted");

        List<GeneratedSource> sources = DecisionProvenanceSourceValidator.generatedSources();
        check(sources.size() == 3, "source count drifted");
        check(
                sources.stream().map(GeneratedSource::language).collect(Collectors.toSet())
                        .equals(Set.of(LanguageSlice.RU, LanguageSlice.EN, LanguageSlice.MIXED_RU_EN)),
                "language matrix drifted");
        check(
                sources.stream().map(GeneratedSource::sourceId).collect(Collectors.toSet())
                        .equals(Set.of("SOURCE_RU", "SOURCE_EN", "SOURCE_MIXED")),
                "source IDs drifted");
        for (GeneratedSource source : sources) {
            byte[] bytes = source.text().getBytes(StandardCharsets.UTF_8);
            check(
                    DecisionProvenanceSourceValidator.sha256Utf8(source.text())
                            .equals(source.wholeSourceSha256()),
                    "whole-source digest drifted: " + source.sourceId());
            int start = Math.toIntExact(source.excerptStartInclusive());
            int end = Math.toIntExact(source.excerptEndExclusive());
            check(start >= 0 && start < end && end <= bytes.length, "source range drifted");
            check(isUtf8Boundary(bytes, start), "source start boundary drifted");
            check(isUtf8Boundary(bytes, end), "source end boundary drifted");
            String excerpt = new String(bytes, start, end - start, StandardCharsets.UTF_8);
            check(
                    DecisionProvenanceSourceValidator.sha256Utf8(excerpt)
                            .equals(source.excerptSha256()),
                    "excerpt digest drifted: " + source.sourceId());
        }
    }

    private static void exactGeneratedCasesAcceptOrRejectAsFrozen() {
        List<ProvenanceEnvelope> cases = DecisionProvenanceSourceValidator.generatedCases();
        check(cases.size() == 14, "case count drifted");
        check(
                cases.stream().map(ProvenanceEnvelope::candidateId).distinct().count() == 14,
                "case IDs are not unique");

        ValidationBatch result = DecisionProvenanceSourceValidator.validate(cases);
        check(result.acceptedCount() == 3, "accepted count drifted");
        check(result.rejectedCount() == 11, "rejected count drifted");
        Map<String, CandidateResult> byId =
                result.results().stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        CandidateResult::candidateId, Function.identity()));
        for (String acceptedId :
                List.of("CASE_ACCEPT_RU", "CASE_ACCEPT_EN", "CASE_ACCEPT_MIXED")) {
            CandidateResult accepted = byId.get(acceptedId);
            check(accepted != null, "missing accepted case: " + acceptedId);
            check(accepted.status() == ValidationStatus.ACCEPTED, acceptedId + " was rejected");
            check(accepted.diagnostics().isEmpty(), acceptedId + " has diagnostics");
        }
        for (Map.Entry<String, Diagnostic> expected : EXPECTED_REJECTIONS.entrySet()) {
            CandidateResult rejected = byId.get(expected.getKey());
            check(rejected != null, "missing rejected case: " + expected.getKey());
            check(
                    rejected.status() == ValidationStatus.REJECTED,
                    expected.getKey() + " was accepted");
            check(
                    rejected.diagnostics().equals(List.of(expected.getValue())),
                    expected.getKey() + " diagnostics drifted: " + rejected.diagnostics());
        }
    }

    private static void invalidRangesNeverSliceAndUtf8FaultsTargetContinuationBytes() {
        Map<String, ProvenanceEnvelope> cases =
                DecisionProvenanceSourceValidator.generatedCases().stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        ProvenanceEnvelope::candidateId, Function.identity()));
        GeneratedSource ru =
                DecisionProvenanceSourceValidator.generatedSources().stream()
                        .filter(source -> source.sourceId().equals("SOURCE_RU"))
                        .findFirst()
                        .orElseThrow();
        byte[] ruBytes = ru.text().getBytes(StandardCharsets.UTF_8);
        ProvenanceEnvelope midStart = cases.get("CASE_REJECT_MID_UTF8_START");
        ProvenanceEnvelope midEnd = cases.get("CASE_REJECT_MID_UTF8_END");
        check(
                !isUtf8Boundary(ruBytes, Math.toIntExact(midStart.startInclusive())),
                "mid-UTF8 start became a boundary");
        check(
                !isUtf8Boundary(ruBytes, Math.toIntExact(midEnd.endExclusive())),
                "mid-UTF8 end became a boundary");
        check(
                cases.get("CASE_REJECT_NEGATIVE_RANGE").startInclusive() < 0,
                "negative fixture drifted");
        check(
                cases.get("CASE_REJECT_REVERSED_RANGE").startInclusive()
                        > cases.get("CASE_REJECT_REVERSED_RANGE").endExclusive(),
                "reversed fixture drifted");
        check(
                cases.get("CASE_REJECT_EMPTY_RANGE").startInclusive()
                        == cases.get("CASE_REJECT_EMPTY_RANGE").endExclusive(),
                "empty fixture drifted");
    }

    private static void permutationAndRepeatAreDeterministic() {
        List<ProvenanceEnvelope> cases = DecisionProvenanceSourceValidator.generatedCases();
        ValidationBatch ordered = DecisionProvenanceSourceValidator.validate(cases);
        ValidationBatch repeated = DecisionProvenanceSourceValidator.validate(cases);
        List<ProvenanceEnvelope> reversedCases = new ArrayList<>(cases);
        Collections.reverse(reversedCases);
        ValidationBatch reversed = DecisionProvenanceSourceValidator.validate(reversedCases);
        List<ProvenanceEnvelope> rotatedCases = new ArrayList<>(cases);
        Collections.rotate(rotatedCases, 5);
        ValidationBatch rotated = DecisionProvenanceSourceValidator.validate(rotatedCases);
        check(ordered.equals(repeated), "repeat changed typed result");
        check(ordered.equals(reversed), "reverse changed typed result");
        check(ordered.equals(rotated), "rotation changed typed result");
        check(
                ordered.canonicalOutput().equals(repeated.canonicalOutput()),
                "repeat changed canonical output");
        check(
                ordered.canonicalOutput().equals(reversed.canonicalOutput()),
                "reverse changed canonical output");
        check(
                ordered.canonicalOutput().equals(rotated.canonicalOutput()),
                "rotation changed canonical output");
    }

    private static void canonicalOutputIsContentFreeAndClaimBounded() {
        ValidationBatch result = DecisionProvenanceSourceValidator.runGeneratedCaseMatrix();
        String output = result.canonicalOutput();
        for (GeneratedSource source : DecisionProvenanceSourceValidator.generatedSources()) {
            check(!output.contains(source.text()), "canonical output contains generated source");
            byte[] bytes = source.text().getBytes(StandardCharsets.UTF_8);
            String excerpt =
                    new String(
                            bytes,
                            Math.toIntExact(source.excerptStartInclusive()),
                            Math.toIntExact(
                                    source.excerptEndExclusive()
                                            - source.excerptStartInclusive()),
                            StandardCharsets.UTF_8);
            check(!output.contains(excerpt), "canonical output contains generated excerpt");
        }
        check(output.contains("autoApply=false\n"), "auto-apply ceiling missing");
        check(output.contains("stateMutation=false\n"), "mutation ceiling missing");
        check(output.contains("pocVerdict=NOT_RUN\n"), "PoC verdict ceiling missing");
        check(
                output.contains("pocReadiness=BLOCKED_UNCHANGED\n"),
                "readiness ceiling missing");
        check(
                output.contains(
                        "claim="
                                + DecisionProvenanceSourceValidator.CLAIM_CEILING
                                + "\n"),
                "claim ceiling missing");
    }

    private static void publicSnapshotsAndInputsFailClosed() {
        expectUnsupported(() -> DecisionProvenanceSourceValidator.generatedSources().clear());
        expectUnsupported(() -> DecisionProvenanceSourceValidator.generatedCases().clear());
        ValidationBatch result = DecisionProvenanceSourceValidator.runGeneratedCaseMatrix();
        expectUnsupported(() -> result.results().clear());
        expectUnsupported(() -> result.results().get(0).diagnostics().clear());
        expectFailure(() -> DecisionProvenanceSourceValidator.validate(null));
        List<ProvenanceEnvelope> withNull = new ArrayList<>();
        withNull.add(null);
        expectFailure(() -> DecisionProvenanceSourceValidator.validate(withNull));
        ProvenanceEnvelope duplicate = DecisionProvenanceSourceValidator.generatedCases().get(0);
        expectFailure(
                () -> DecisionProvenanceSourceValidator.validate(List.of(duplicate, duplicate)));
        expectFailure(
                () ->
                        new ProvenanceEnvelope(
                                "bad-id",
                                DecisionProvenanceSourceValidator.ENVELOPE_SCHEMA_VERSION,
                                DecisionProvenanceSourceValidator.SOURCE_SET_VERSION,
                                "SOURCE_RU",
                                "0".repeat(64),
                                0,
                                1,
                                "0".repeat(64)));
    }

    private static boolean isUtf8Boundary(byte[] bytes, int offset) {
        return offset == 0 || offset == bytes.length || (bytes[offset] & 0xC0) != 0x80;
    }

    private static void expectUnsupported(Runnable mutation) {
        try {
            mutation.run();
            throw new AssertionError("mutation unexpectedly succeeded");
        } catch (UnsupportedOperationException expected) {
            // Expected: every public collection is an immutable snapshot.
        }
    }

    private static void expectFailure(Runnable call) {
        try {
            call.run();
            throw new AssertionError("invalid input unexpectedly succeeded");
        } catch (IllegalArgumentException | NullPointerException expected) {
            // Expected: invalid programmatic fixture inputs fail closed.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
