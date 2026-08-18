package com.monumentogram.dora.stage0.decision.i3;

import com.monumentogram.dora.stage0.decision.i3.DecisionSyntheticCampaign.CampaignCase;
import com.monumentogram.dora.stage0.decision.i3.DecisionSyntheticCampaign.CampaignResult;
import com.monumentogram.dora.stage0.decision.i3.DecisionSyntheticCampaign.LanguageSlice;
import com.monumentogram.dora.stage0.decision.i3.DecisionSyntheticCampaign.OutcomeVariant;
import com.monumentogram.dora.stage0.decision.i3.DecisionSyntheticCampaign.RelationSlice;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.CandidateDisposition;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.CandidateResult;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.ConfusionCounts;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.EvaluationResult;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.EvaluationState;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.FieldSnapshot;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.Fraction;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.GeneratedSource;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.Label;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.MachineProposal;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.ProposalDisposition;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.ProtectionResult;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.ProtectionState;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.ScoredItem;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.SourceRange;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.SourceStatus;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.SupportStatus;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Independent executable checks for the generated Decision I3 mechanics campaign. */
public final class DecisionSyntheticCampaignTest {
    private static final String EXPECTED_EVALUATION_DIGEST =
            "2c0e95f069dfa64ea1846b6ab554418975255bcbbabaa375fc13abe5c5453d7a";
    private static final String EXPECTED_PROTECTION_DIGEST =
            "807da67fb46b1c980b7d62893104575231441b6577de540c1abf2d7d361749f1";
    private static final String EXPECTED_CAMPAIGN_DIGEST =
            "00915b5ed6072eecec379342d7abebc2d59a3ff0a2f296294e2c46202abe8db9";

    private DecisionSyntheticCampaignTest() {}

    public static void main(String[] args) {
        profileAndCaseMatrixAreExact();
        generatedTextAndAnchorsAreDeterministic();
        individualOutcomeRelationsMatchI2();
        aggregateRationalMechanicsAreExact();
        userTruthIsNeverOverwritten();
        orderingMetamorphicsAndRepeatDigestsAreStable();
        publicSnapshotsAreImmutable();
        canonicalOutputIsContentFreeAndClaimBounded();
        System.out.print("POC_DECISION_I3_SYNTHETIC_CAMPAIGN_TESTS_OK\n");
    }

    private static void profileAndCaseMatrixAreExact() {
        check(
                DecisionSyntheticCampaign.PROFILE_VERSION.equals("decision-i3-stage0-v0.1"),
                "profile drifted");
        check(
                DecisionSyntheticCampaign.AUTHORIZATION_ID.equals(
                        "POC-DECISION-001-I3-SYNTHETIC-METAMORPHIC-CAMPAIGN-AUTH-20260819-01"),
                "authority drifted");
        check(DecisionSyntheticCampaign.CASE_COUNT == 144, "case constant drifted");
        check(
                DecisionSyntheticCampaign.GOVERNED_CORPUS_CASE_COUNT == 0,
                "generated cases were misclassified as governed corpus");

        List<CampaignCase> cases = DecisionSyntheticCampaign.generatedCases();
        check(cases.size() == 144, "generated case count drifted");
        Set<String> caseIds = new HashSet<>();
        Set<String> sourceIds = new HashSet<>();
        Set<String> goldIds = new HashSet<>();
        EnumMap<LanguageSlice, Integer> languageCounts = zeroCounts(LanguageSlice.class);
        EnumMap<RelationSlice, Integer> relationCounts = zeroCounts(RelationSlice.class);
        EnumMap<OutcomeVariant, Integer> outcomeCounts = zeroCounts(OutcomeVariant.class);
        for (CampaignCase campaignCase : cases) {
            check(caseIds.add(campaignCase.caseId()), "duplicate case ID");
            check(sourceIds.add(campaignCase.source().sourceId()), "duplicate source ID");
            check(goldIds.add(campaignCase.gold().itemId()), "duplicate gold ID");
            increment(languageCounts, campaignCase.language());
            increment(relationCounts, campaignCase.relation());
            increment(outcomeCounts, campaignCase.outcome());
        }
        for (LanguageSlice language : LanguageSlice.values()) {
            check(languageCounts.get(language) == 48, "language count drifted: " + language);
        }
        for (RelationSlice relation : RelationSlice.values()) {
            check(relationCounts.get(relation) == 36, "relation count drifted: " + relation);
        }
        check(outcomeCounts.get(OutcomeVariant.MATCH_VALID_SOURCE) == 48,
                "valid match count drifted");
        check(outcomeCounts.get(OutcomeVariant.WRONG_ID_VALID_SOURCE) == 24,
                "valid wrong-ID count drifted");
        check(outcomeCounts.get(OutcomeVariant.WRONG_LABEL_VALID_SOURCE) == 24,
                "valid wrong-label count drifted");
        check(outcomeCounts.get(OutcomeVariant.MATCH_INVALID_UTF8_BOUNDARY) == 24,
                "invalid-boundary match count drifted");
        check(outcomeCounts.get(OutcomeVariant.WRONG_ID_INVALID_UTF8_BOUNDARY) == 24,
                "invalid-boundary wrong-ID count drifted");
    }

    private static void generatedTextAndAnchorsAreDeterministic() {
        for (CampaignCase campaignCase : DecisionSyntheticCampaign.generatedCases()) {
            GeneratedSource source = campaignCase.source();
            check(
                    DecisionDeterministicSyntheticHarness.sha256Utf8(source.text())
                            .equals(source.declaredUtf8Sha256()),
                    "whole-source digest drifted: " + campaignCase.caseId());
            check(source.text().contains(campaignCase.caseId()),
                    "source lost opaque case marker");
            switch (campaignCase.language()) {
                case RU -> check(source.text().startsWith(
                                "\u0421\u0418\u041d\u0422\u0415\u0422\u0418\u0427\u0415\u0421\u041a\u0418\u0419_"
                                        + "\u0422\u0415\u041a\u0421\u0422 "),
                        "RU generated prefix drifted");
                case EN -> check(source.text().startsWith("SYNTHETIC_TEXT "),
                        "EN generated prefix drifted");
                case MIXED_RU_EN -> check(source.text().startsWith(
                                "SYNTHETIC_\u0422\u0415\u041a\u0421\u0422 "),
                        "mixed generated prefix drifted");
            }

            SourceRange goldRange = onlyRange(campaignCase.gold());
            byte[] sourceBytes = source.text().getBytes(StandardCharsets.UTF_8);
            int start = Math.toIntExact(goldRange.startInclusive());
            int end = Math.toIntExact(goldRange.endExclusive());
            check(start >= 0 && start < end && end <= sourceBytes.length,
                    "gold range out of bounds");
            check(isUtf8Boundary(sourceBytes, start), "gold start is not UTF-8 boundary");
            check(isUtf8Boundary(sourceBytes, end), "gold end is not UTF-8 boundary");
            String excerpt = new String(sourceBytes, start, end - start, StandardCharsets.UTF_8);
            check(
                    DecisionDeterministicSyntheticHarness.sha256Utf8(excerpt)
                            .equals(goldRange.excerptUtf8Sha256()),
                    "gold excerpt digest drifted");

            SourceRange predictionRange = onlyRange(campaignCase.prediction());
            if (hasInvalidBoundary(campaignCase.outcome())) {
                int predictionStart = Math.toIntExact(predictionRange.startInclusive());
                check(!isUtf8Boundary(sourceBytes, predictionStart),
                        "negative case accidentally used a valid UTF-8 boundary");
                check(predictionStart == start + 1,
                        "negative case no longer targets the first continuation byte");
            } else {
                check(predictionRange.equals(goldRange), "valid prediction range drifted");
            }
        }
    }

    private static void individualOutcomeRelationsMatchI2() {
        EnumMap<CandidateDisposition, Integer> dispositions =
                zeroCounts(CandidateDisposition.class);
        for (CampaignCase campaignCase : DecisionSyntheticCampaign.generatedCases()) {
            EvaluationResult result =
                    DecisionDeterministicSyntheticHarness.evaluate(
                            List.of(campaignCase.source()),
                            List.of(campaignCase.gold()),
                            List.of(campaignCase.prediction()));
            check(result.state() == EvaluationState.COMPLETE, result.canonicalOutput());
            check(result.candidates().size() == 1, "individual candidate count drifted");
            CandidateResult candidate = result.candidates().get(0);
            SourceStatus expectedSource =
                    hasInvalidBoundary(campaignCase.outcome())
                            ? SourceStatus.INVALID
                            : SourceStatus.VALID;
            SupportStatus expectedSupport =
                    hasUnsupportedIdentityOrLabel(campaignCase.outcome())
                            ? SupportStatus.UNSUPPORTED_BY_SYNTHETIC_GOLD
                            : SupportStatus.SUPPORTED_BY_SYNTHETIC_GOLD;
            CandidateDisposition expectedDisposition =
                    expectedDisposition(expectedSource, expectedSupport);
            check(candidate.sourceStatus() == expectedSource,
                    "individual source status drifted: " + campaignCase.caseId());
            check(candidate.supportStatus() == expectedSupport,
                    "individual support status drifted: " + campaignCase.caseId());
            check(candidate.disposition() == expectedDisposition,
                    "individual disposition drifted: " + campaignCase.caseId());
            increment(dispositions, candidate.disposition());

            Label expectedGoldLabel =
                    switch (campaignCase.relation()) {
                        case FINAL -> Label.FINAL_DECISION;
                        case AMEND, CANCEL -> Label.REVISION_LINK;
                        case SOURCE_GROUNDING -> Label.CONFIRMED_TASK;
                    };
            check(campaignCase.gold().label() == expectedGoldLabel,
                    "relation-to-I2-label mapping drifted");
        }
        check(dispositions.get(CandidateDisposition.ADMISSIBLE_FOR_REVIEW) == 48,
                "review-only count drifted");
        check(dispositions.get(CandidateDisposition.REJECT_UNSUPPORTED) == 48,
                "unsupported count drifted");
        check(dispositions.get(CandidateDisposition.REJECT_INVALID_SOURCE) == 24,
                "invalid-source count drifted");
        check(
                dispositions.get(CandidateDisposition.REJECT_INVALID_SOURCE_AND_UNSUPPORTED)
                        == 24,
                "combined-rejection count drifted");
    }

    private static void aggregateRationalMechanicsAreExact() {
        CampaignResult result = DecisionSyntheticCampaign.run();
        check(
                result.evaluation().metrics().microCounts()
                        .equals(new ConfusionCounts(72, 72, 72)),
                "micro counts drifted");
        check(result.evaluation().metrics().microPrecision().equals(Fraction.of(1, 2)),
                "micro precision drifted");
        check(result.evaluation().metrics().microRecall().equals(Fraction.of(1, 2)),
                "micro recall drifted");
        check(result.evaluation().metrics().microF1().equals(Fraction.of(1, 2)),
                "micro F1 drifted");
        check(result.evaluation().metrics().macroPrecision().equals(Fraction.of(1, 2)),
                "macro precision drifted");
        check(result.evaluation().metrics().macroRecall().equals(Fraction.of(1, 2)),
                "macro recall drifted");
        check(result.evaluation().metrics().macroF1().equals(Fraction.of(1, 2)),
                "macro F1 drifted");
        check(result.evaluation().metrics().sourceValidity().equals(Fraction.of(2, 3)),
                "source validity drifted");
        check(
                result.evaluation().metrics().unsupportedClaimRate().equals(Fraction.of(1, 2)),
                "unsupported rate drifted");
        check(
                result.evaluation().metrics().perLabel().stream()
                        .allMatch(
                                metric ->
                                        metric.precision().equals(Fraction.of(1, 2))
                                                && metric.recall().equals(Fraction.of(1, 2))
                                                && metric.f1().equals(Fraction.of(1, 2))),
                "per-label exact rational mechanics drifted");
    }

    private static void userTruthIsNeverOverwritten() {
        List<CampaignCase> cases = DecisionSyntheticCampaign.generatedCases();
        List<FieldSnapshot> fields = new ArrayList<>();
        List<MachineProposal> proposals = new ArrayList<>();
        for (CampaignCase campaignCase : cases) {
            fields.addAll(campaignCase.currentFields());
            proposals.addAll(campaignCase.machineProposals());
        }
        List<FieldSnapshot> before = List.copyOf(fields);
        ProtectionResult result =
                DecisionDeterministicSyntheticHarness.protectUserTruth(fields, proposals);
        check(result.state() == ProtectionState.COMPLETE, result.canonicalOutput());
        check(fields.equals(before), "I2 protection mutated current fields");
        check(result.proposals().size() == 576, "protected proposal count drifted");
        EnumMap<ProposalDisposition, Integer> counts = zeroCounts(ProposalDisposition.class);
        result.proposals().forEach(proposal -> increment(counts, proposal.disposition()));
        for (ProposalDisposition disposition : ProposalDisposition.values()) {
            check(counts.get(disposition) == 144,
                    "proposal disposition count drifted: " + disposition);
        }
    }

    private static void orderingMetamorphicsAndRepeatDigestsAreStable() {
        CampaignResult first = DecisionSyntheticCampaign.run();
        CampaignResult second = DecisionSyntheticCampaign.run();
        check(first.equals(second), "repeat changed typed campaign result");
        check(first.canonicalOutput().equals(second.canonicalOutput()),
                "repeat changed canonical campaign result");
        check(first.evaluationDigest().equals(EXPECTED_EVALUATION_DIGEST),
                "evaluation digest golden drifted");
        check(first.protectionDigest().equals(EXPECTED_PROTECTION_DIGEST),
                "protection digest golden drifted");
        check(first.campaignDigest().equals(EXPECTED_CAMPAIGN_DIGEST),
                "campaign digest golden drifted");
        check(
                DecisionDeterministicSyntheticHarness.sha256Utf8(first.canonicalPayload())
                        .equals(first.campaignDigest()),
                "campaign digest no longer binds canonical payload");

        List<CampaignCase> cases = DecisionSyntheticCampaign.generatedCases();
        EvaluationResult ordered = evaluate(cases);
        List<CampaignCase> reversed = new ArrayList<>(cases);
        Collections.reverse(reversed);
        EvaluationResult permuted = evaluate(reversed);
        check(ordered.equals(permuted), "case ordering changed I2 typed evaluation");
        check(ordered.canonicalOutput().equals(permuted.canonicalOutput()),
                "case ordering changed I2 canonical evaluation");
    }

    private static void publicSnapshotsAreImmutable() {
        List<CampaignCase> cases = DecisionSyntheticCampaign.generatedCases();
        expectUnsupported(cases::clear);
        expectUnsupported(() -> cases.get(0).currentFields().clear());
        expectUnsupported(() -> cases.get(0).machineProposals().clear());
        CampaignResult result = DecisionSyntheticCampaign.run();
        expectUnsupported(() -> result.languageCounts().clear());
        expectUnsupported(() -> result.relationCounts().clear());
        expectUnsupported(() -> result.outcomeCounts().clear());
        expectUnsupported(() -> result.sourceStatusCounts().clear());
        expectUnsupported(() -> result.supportStatusCounts().clear());
        expectUnsupported(() -> result.candidateDispositionCounts().clear());
        expectUnsupported(() -> result.proposalDispositionCounts().clear());
    }

    private static void canonicalOutputIsContentFreeAndClaimBounded() {
        CampaignResult result = DecisionSyntheticCampaign.run();
        String output = result.canonicalOutput();
        for (CampaignCase campaignCase : DecisionSyntheticCampaign.generatedCases()) {
            check(!output.contains(campaignCase.source().text()),
                    "canonical output contains generated source text");
            for (FieldSnapshot field : campaignCase.currentFields()) {
                check(!output.contains(field.currentValue()),
                        "canonical output contains current field value");
            }
            for (MachineProposal proposal : campaignCase.machineProposals()) {
                check(!output.contains(proposal.proposedValue()),
                        "canonical output contains proposed field value");
            }
        }
        check(output.contains("governedCorpusCaseCount=0\n"),
                "governed-corpus ceiling missing");
        check(output.contains("autoApply=false\n"), "auto-apply ceiling missing");
        check(output.contains("stateMutation=false\n"), "mutation ceiling missing");
        check(output.contains("pocVerdict=NOT_RUN\n"), "PoC verdict ceiling missing");
        check(output.contains("pocReadiness=BLOCKED_UNCHANGED\n"),
                "readiness ceiling missing");
        check(output.contains("qualityClaim=false\n"), "quality ceiling missing");
        check(output.contains("productAdmission=false\n"),
                "admission ceiling missing");
    }

    private static EvaluationResult evaluate(List<CampaignCase> cases) {
        List<GeneratedSource> sources = cases.stream().map(CampaignCase::source).toList();
        List<ScoredItem> gold = cases.stream().map(CampaignCase::gold).toList();
        List<ScoredItem> predictions = cases.stream().map(CampaignCase::prediction).toList();
        return DecisionDeterministicSyntheticHarness.evaluate(sources, gold, predictions);
    }

    private static SourceRange onlyRange(ScoredItem item) {
        check(item.sourceRanges().size() == 1, "case must have exactly one range");
        return item.sourceRanges().get(0);
    }

    private static boolean isUtf8Boundary(byte[] bytes, int offset) {
        return offset == 0 || offset == bytes.length || (bytes[offset] & 0xC0) != 0x80;
    }

    private static boolean hasInvalidBoundary(OutcomeVariant outcome) {
        return outcome == OutcomeVariant.MATCH_INVALID_UTF8_BOUNDARY
                || outcome == OutcomeVariant.WRONG_ID_INVALID_UTF8_BOUNDARY;
    }

    private static boolean hasUnsupportedIdentityOrLabel(OutcomeVariant outcome) {
        return outcome == OutcomeVariant.WRONG_ID_VALID_SOURCE
                || outcome == OutcomeVariant.WRONG_LABEL_VALID_SOURCE
                || outcome == OutcomeVariant.WRONG_ID_INVALID_UTF8_BOUNDARY;
    }

    private static CandidateDisposition expectedDisposition(
            SourceStatus sourceStatus, SupportStatus supportStatus) {
        if (sourceStatus == SourceStatus.INVALID
                && supportStatus == SupportStatus.UNSUPPORTED_BY_SYNTHETIC_GOLD) {
            return CandidateDisposition.REJECT_INVALID_SOURCE_AND_UNSUPPORTED;
        }
        if (sourceStatus == SourceStatus.INVALID) {
            return CandidateDisposition.REJECT_INVALID_SOURCE;
        }
        if (supportStatus == SupportStatus.UNSUPPORTED_BY_SYNTHETIC_GOLD) {
            return CandidateDisposition.REJECT_UNSUPPORTED;
        }
        return CandidateDisposition.ADMISSIBLE_FOR_REVIEW;
    }

    private static <E extends Enum<E>> EnumMap<E, Integer> zeroCounts(Class<E> enumClass) {
        EnumMap<E, Integer> counts = new EnumMap<>(enumClass);
        for (E value : enumClass.getEnumConstants()) {
            counts.put(value, 0);
        }
        return counts;
    }

    private static <E extends Enum<E>> void increment(Map<E, Integer> counts, E value) {
        counts.compute(value, (ignored, count) -> Math.addExact(count, 1));
    }

    private static void expectUnsupported(Runnable mutation) {
        try {
            mutation.run();
            throw new AssertionError("mutation unexpectedly succeeded");
        } catch (UnsupportedOperationException expected) {
            // Expected: every public collection is an immutable snapshot.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
