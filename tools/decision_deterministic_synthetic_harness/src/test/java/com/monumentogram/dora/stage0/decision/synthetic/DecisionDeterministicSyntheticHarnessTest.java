package com.monumentogram.dora.stage0.decision.synthetic;

import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.AggregateMetrics;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.CandidateDisposition;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.CandidateResult;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.EvaluationResult;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.EvaluationState;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.FieldName;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.FieldOwnership;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.FieldSnapshot;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.Fraction;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.GeneratedSource;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.IssueCode;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.Label;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.MachineProposal;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.ProposalDisposition;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.ProtectedProposal;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.ProtectionResult;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.ProtectionState;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.ScoredItem;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.SourceRange;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.SourceStatus;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.SupportStatus;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.ValidationIssue;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Adversarial, generated-text-only tests for the Stage 0 deterministic synthetic harness. */
public final class DecisionDeterministicSyntheticHarnessTest {
    private static final String RU_TEXT =
            "\u0413\u0440\u0443\u043f\u043f\u0430 \u0410\u043b\u044c\u0444\u0430 "
                    + "\u0440\u0435\u0448\u0438\u043b\u0430 "
                    + "\u0432\u044b\u043f\u0443\u0441\u0442\u0438\u0442\u044c "
                    + "\u043c\u0430\u043a\u0435\u0442 \u0432 "
                    + "\u043f\u044f\u0442\u043d\u0438\u0446\u0443.";
    private static final String EN_TEXT =
            "Team Beta confirmed the reversible option.";
    private static final String MIXED_TEXT =
            "Gamma \u0440\u0435\u0448\u0430\u0435\u0442 ship draft only after review.";

    private static final GeneratedSource SOURCE_RU =
            new GeneratedSource(
                    "SOURCE_RU",
                    RU_TEXT,
                    "f236164d907a56edb548c1eed4a378b9ca0783d008103bb132efd6f4df74c73a");
    private static final GeneratedSource SOURCE_EN =
            new GeneratedSource(
                    "SOURCE_EN",
                    EN_TEXT,
                    "77d2b4fa180ac37d1c3f1cc9bb12ef45756f31355b0e99d9accb079647be8d62");
    private static final GeneratedSource SOURCE_MIXED =
            new GeneratedSource(
                    "SOURCE_MIXED",
                    MIXED_TEXT,
                    "6b3551de5c874d49a73cdc11a2d5c02b9d8ea6f1b9479695bf295f5d0e7f603a");
    private static final List<GeneratedSource> SOURCES =
            List.of(SOURCE_RU, SOURCE_EN, SOURCE_MIXED);

    private static final SourceRange RANGE_RU =
            new SourceRange(
                    "SOURCE_RU",
                    24,
                    66,
                    "b769214e1587142e14a74da7448e0d737e888c6126448fd497e4631e3fa6bd48");
    private static final SourceRange RANGE_EN =
            new SourceRange(
                    "SOURCE_EN",
                    10,
                    41,
                    "a41eab3209606fc74bc446166d49cd7228c570f19b826792fd780ea556f89ad5");
    private static final SourceRange RANGE_MIXED =
            new SourceRange(
                    "SOURCE_MIXED",
                    19,
                    47,
                    "dba17b9120213515be4fbb0ce95da760e6276e83f79f2d10e2f876c34fba37c7");

    private DecisionDeterministicSyntheticHarnessTest() {}

    public static void main(String[] args) {
        profileAndIndependentHashGoldensAreExact();
        metricAggregationHasExactCanonicalGolden();
        inputPermutationAndRepeatAreDeterministic();
        emptyAndZeroDenominatorMetricsAreExplicit();
        malformedSourceFixturesFailTheWholeBatch();
        malformedGoldAnchorsFailTheWholeBatch();
        malformedPredictionIdentityFailsTheWholeBatch();
        candidateSourceFailuresAreMeasuredAndRejected();
        sourceAndSupportFailuresCombineFailClosed();
        userTruthAndUncertainTaskActivationStayUnchanged();
        malformedProtectionInputsFailClosed();
        recordsAndResultsAreImmutable();
        canonicalOutputsContainNoGeneratedTextOrFieldValues();
        System.out.print("POC_DECISION_DETERMINISTIC_SYNTHETIC_HARNESS_TESTS_OK\n");
    }

    private static void profileAndIndependentHashGoldensAreExact() {
        check(
                DecisionDeterministicSyntheticHarness.PROFILE_VERSION.equals("stage0-v0.1"),
                "profile version drifted");
        check(
                DecisionDeterministicSyntheticHarness.SOURCE_RANGE_UNIT.equals(
                        "UTF8_BYTE_OFFSETS_HALF_OPEN_STAGE0_V0_1"),
                "source range unit drifted");
        check(
                DecisionDeterministicSyntheticHarness.sha256Utf8(RU_TEXT)
                        .equals(SOURCE_RU.declaredUtf8Sha256()),
                "RU SHA-256 golden mismatch");
        check(
                DecisionDeterministicSyntheticHarness.sha256Utf8(EN_TEXT)
                        .equals(SOURCE_EN.declaredUtf8Sha256()),
                "EN SHA-256 golden mismatch");
        check(
                DecisionDeterministicSyntheticHarness.sha256Utf8(MIXED_TEXT)
                        .equals(SOURCE_MIXED.declaredUtf8Sha256()),
                "mixed SHA-256 golden mismatch");
    }

    private static void metricAggregationHasExactCanonicalGolden() {
        List<ScoredItem> gold = canonicalGold();
        SourceRange badLinkDigest =
                new SourceRange(
                        RANGE_EN.sourceId(),
                        RANGE_EN.startInclusive(),
                        RANGE_EN.endExclusive(),
                        "0".repeat(64));
        List<ScoredItem> predictions =
                List.of(
                        item("ITEM_DEC_1", Label.FINAL_DECISION, RANGE_RU),
                        item("ITEM_DEC_EXTRA", Label.FINAL_DECISION, RANGE_RU),
                        item("ITEM_LINK_1", Label.REVISION_LINK, badLinkDigest),
                        item("ITEM_TASK_1", Label.CONFIRMED_TASK, RANGE_MIXED));

        EvaluationResult result =
                DecisionDeterministicSyntheticHarness.evaluate(SOURCES, gold, predictions);
        check(result.state() == EvaluationState.COMPLETE, result.canonicalOutput());
        check(result.issues().isEmpty(), "complete evaluation has structural issues");
        AggregateMetrics metrics = result.metrics();
        check(metrics.microCounts().truePositive() == 3, "micro TP mismatch");
        check(metrics.microCounts().falsePositive() == 1, "micro FP mismatch");
        check(metrics.microCounts().falseNegative() == 1, "micro FN mismatch");
        check(metrics.microPrecision().equals(fraction(3, 4)), "micro precision mismatch");
        check(metrics.microRecall().equals(fraction(3, 4)), "micro recall mismatch");
        check(metrics.microF1().equals(fraction(3, 4)), "micro F1 mismatch");
        check(metrics.macroPrecision().equals(fraction(5, 6)), "macro precision mismatch");
        check(metrics.macroRecall().equals(fraction(5, 6)), "macro recall mismatch");
        check(metrics.macroF1().equals(fraction(5, 6)), "macro F1 mismatch");
        check(metrics.sourceValidity().equals(fraction(3, 4)), "source validity mismatch");
        check(
                metrics.unsupportedClaimRate().equals(fraction(1, 4)),
                "unsupported claim rate mismatch");

        String expected =
                "profile=stage0-v0.1\n"
                        + "rangeUnit=UTF8_BYTE_OFFSETS_HALF_OPEN_STAGE0_V0_1\n"
                        + "state=COMPLETE\n"
                        + "autoApply=false\n"
                        + "label=FINAL_DECISION|tp=1|fp=1|fn=1|p=1/2@0.500000|r=1/2@0.500000|f1=1/2@0.500000\n"
                        + "label=REVISION_LINK|tp=1|fp=0|fn=0|p=1/1@1.000000|r=1/1@1.000000|f1=1/1@1.000000\n"
                        + "label=CONFIRMED_TASK|tp=1|fp=0|fn=0|p=1/1@1.000000|r=1/1@1.000000|f1=1/1@1.000000\n"
                        + "micro=3/1/1|p=3/4@0.750000|r=3/4@0.750000|f1=3/4@0.750000\n"
                        + "macro=p=5/6@0.833333|r=5/6@0.833333|f1=5/6@0.833333\n"
                        + "sourceValidity=3/4@0.750000\n"
                        + "unsupportedClaimRate=1/4@0.250000\n"
                        + "candidate=S10:ITEM_DEC_1|FINAL_DECISION|VALID|SUPPORTED_BY_SYNTHETIC_GOLD|ADMISSIBLE_FOR_REVIEW\n"
                        + "candidate=S14:ITEM_DEC_EXTRA|FINAL_DECISION|VALID|UNSUPPORTED_BY_SYNTHETIC_GOLD|REJECT_UNSUPPORTED\n"
                        + "candidate=S11:ITEM_LINK_1|REVISION_LINK|INVALID|SUPPORTED_BY_SYNTHETIC_GOLD|REJECT_INVALID_SOURCE\n"
                        + "candidateIssue=EXCERPT_DIGEST_MISMATCH|S11:ITEM_LINK_1|ref=0\n"
                        + "candidate=S11:ITEM_TASK_1|CONFIRMED_TASK|VALID|SUPPORTED_BY_SYNTHETIC_GOLD|ADMISSIBLE_FOR_REVIEW\n";
        check(result.canonicalOutput().equals(expected), "canonical metric golden mismatch");
        check(
                result.candidates().stream()
                        .filter(candidate -> candidate.itemId().equals("ITEM_LINK_1"))
                        .findFirst()
                        .orElseThrow()
                        .disposition()
                        == CandidateDisposition.REJECT_INVALID_SOURCE,
                "invalid source candidate was not rejected");
        check(
                result.candidates().stream()
                        .filter(candidate -> candidate.itemId().equals("ITEM_DEC_1"))
                        .findFirst()
                        .orElseThrow()
                        .disposition()
                        == CandidateDisposition.ADMISSIBLE_FOR_REVIEW,
                "valid supported candidate must remain review-only");
    }

    private static void inputPermutationAndRepeatAreDeterministic() {
        List<ScoredItem> gold = canonicalGold();
        List<ScoredItem> predictions =
                List.of(
                        item("ITEM_TASK_1", Label.CONFIRMED_TASK, RANGE_MIXED),
                        item("ITEM_LINK_1", Label.REVISION_LINK, RANGE_EN),
                        item("ITEM_DEC_1", Label.FINAL_DECISION, RANGE_RU));
        EvaluationResult ordered =
                DecisionDeterministicSyntheticHarness.evaluate(SOURCES, gold, predictions);

        List<ScoredItem> reversedGold = new ArrayList<>(gold);
        Collections.reverse(reversedGold);
        List<ScoredItem> reversedPredictions = new ArrayList<>(predictions);
        Collections.reverse(reversedPredictions);
        EvaluationResult permuted =
                DecisionDeterministicSyntheticHarness.evaluate(
                        List.of(SOURCE_MIXED, SOURCE_EN, SOURCE_RU),
                        reversedGold,
                        reversedPredictions);
        EvaluationResult repeated =
                DecisionDeterministicSyntheticHarness.evaluate(SOURCES, gold, predictions);
        check(ordered.equals(permuted), "input permutation changed typed evaluation");
        check(ordered.equals(repeated), "repeat changed typed evaluation");
        check(
                ordered.canonicalOutput().equals(permuted.canonicalOutput()),
                "input permutation changed canonical evaluation");
        check(
                ordered.canonicalOutput().equals(repeated.canonicalOutput()),
                "repeat changed canonical evaluation");
    }

    private static void emptyAndZeroDenominatorMetricsAreExplicit() {
        EvaluationResult empty =
                DecisionDeterministicSyntheticHarness.evaluate(SOURCES, List.of(), List.of());
        check(empty.state() == EvaluationState.COMPLETE, empty.canonicalOutput());
        check(!empty.metrics().microPrecision().isDefined(), "empty precision must be NA");
        check(!empty.metrics().microRecall().isDefined(), "empty recall must be NA");
        check(!empty.metrics().microF1().isDefined(), "empty F1 must be NA");
        check(!empty.metrics().macroPrecision().isDefined(), "empty macro precision must be NA");
        check(!empty.metrics().macroRecall().isDefined(), "empty macro recall must be NA");
        check(!empty.metrics().macroF1().isDefined(), "empty macro F1 must be NA");
        check(!empty.metrics().sourceValidity().isDefined(), "empty source metric must be NA");
        check(
                !empty.metrics().unsupportedClaimRate().isDefined(),
                "empty unsupported metric must be NA");
        check(empty.canonicalOutput().contains("micro=0/0/0|p=NA|r=NA|f1=NA\n"),
                "empty canonical metric is ambiguous");

        ScoredItem missing = item("ITEM_ONLY", Label.FINAL_DECISION, RANGE_RU);
        EvaluationResult noPredictions =
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES, List.of(missing), List.of());
        check(
                !noPredictions.metrics().microPrecision().isDefined(),
                "precision with no predictions must remain NA");
        check(
                noPredictions.metrics().microRecall().equals(fraction(0, 1)),
                "recall with a missed gold must be zero");
        check(
                noPredictions.metrics().microF1().equals(fraction(0, 1)),
                "F1 with a missed gold must be zero");

        expectIllegalArgument(() -> new Fraction(BigInteger.ONE, BigInteger.ZERO));
        expectIllegalArgument(() -> fraction(-1, 1));
    }

    private static void malformedSourceFixturesFailTheWholeBatch() {
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(null, List.of(), List.of()),
                IssueCode.NULL_SOURCE_LIST);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(SOURCES, null, List.of()),
                IssueCode.NULL_GOLD_LIST);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(SOURCES, List.of(), null),
                IssueCode.NULL_PREDICTION_LIST);

        List<GeneratedSource> withNull = new ArrayList<>();
        withNull.add(null);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(withNull, List.of(), List.of()),
                IssueCode.NULL_SOURCE);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        List.of(new GeneratedSource("bad-source", RU_TEXT, "bad")),
                        List.of(),
                        List.of()),
                IssueCode.INVALID_SOURCE_ID);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        List.of(
                                new GeneratedSource(
                                        "SOURCE_X", null, SOURCE_RU.declaredUtf8Sha256())),
                        List.of(),
                        List.of()),
                IssueCode.NULL_SOURCE_TEXT);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        List.of(
                                new GeneratedSource(
                                        "SOURCE_X", "", SOURCE_RU.declaredUtf8Sha256())),
                        List.of(),
                        List.of()),
                IssueCode.EMPTY_SOURCE_TEXT);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        List.of(
                                new GeneratedSource(
                                        "SOURCE_X",
                                        "BROKEN_\uD800",
                                        DecisionDeterministicSyntheticHarness.sha256Utf8(
                                                "BROKEN_\uD800"))),
                        List.of(),
                        List.of()),
                IssueCode.INVALID_SOURCE_UNICODE);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        List.of(new GeneratedSource("SOURCE_X", RU_TEXT, "ABC")),
                        List.of(),
                        List.of()),
                IssueCode.INVALID_SOURCE_DIGEST);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        List.of(new GeneratedSource("SOURCE_X", RU_TEXT, "0".repeat(64))),
                        List.of(),
                        List.of()),
                IssueCode.SOURCE_DIGEST_MISMATCH);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        List.of(SOURCE_RU, SOURCE_RU), List.of(), List.of()),
                IssueCode.DUPLICATE_SOURCE_ID);

        EvaluationResult sortedIssues =
                DecisionDeterministicSyntheticHarness.evaluate(
                        List.of(new GeneratedSource(null, null, "ABC")), List.of(), List.of());
        check(
                sortedIssues.issues().equals(
                        List.of(
                                new ValidationIssue(
                                        IssueCode.INVALID_SOURCE_DIGEST, "SOURCE_AT_0000"),
                                new ValidationIssue(
                                        IssueCode.INVALID_SOURCE_ID, "SOURCE_AT_0000"),
                                new ValidationIssue(
                                        IssueCode.NULL_SOURCE_TEXT, "SOURCE_AT_0000"))),
                "source issues are not exact and deterministically sorted");
    }

    private static void malformedGoldAnchorsFailTheWholeBatch() {
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES,
                        List.of(new ScoredItem("ITEM_GOLD", Label.FINAL_DECISION, List.of())),
                        List.of()),
                IssueCode.MISSING_SOURCE_REFS);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES,
                        List.of(new ScoredItem("ITEM_GOLD", Label.FINAL_DECISION, null)),
                        List.of()),
                IssueCode.MISSING_SOURCE_REFS);

        List<SourceRange> nullRange = new ArrayList<>();
        nullRange.add(null);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES,
                        List.of(new ScoredItem("ITEM_GOLD", Label.FINAL_DECISION, nullRange)),
                        List.of()),
                IssueCode.NULL_SOURCE_REF);
        assertInvalid(
                evaluationWithGoldRange(
                        new SourceRange(
                                "bad-source", 0, 1, RANGE_RU.excerptUtf8Sha256())),
                IssueCode.INVALID_SOURCE_REF_ID);
        assertInvalid(
                evaluationWithGoldRange(
                        new SourceRange(
                                "SOURCE_UNKNOWN", 0, 1, RANGE_RU.excerptUtf8Sha256())),
                IssueCode.UNKNOWN_SOURCE_REF);
        assertInvalid(
                evaluationWithGoldRange(
                        new SourceRange(
                                "SOURCE_RU", 4, 4, RANGE_RU.excerptUtf8Sha256())),
                IssueCode.INVALID_SOURCE_RANGE);
        assertInvalid(
                evaluationWithGoldRange(
                        new SourceRange(
                                "SOURCE_RU", 1, 2, RANGE_RU.excerptUtf8Sha256())),
                IssueCode.SOURCE_RANGE_NOT_UTF8_BOUNDARY);
        assertInvalid(
                evaluationWithGoldRange(new SourceRange("SOURCE_RU", 24, 66, "ABC")),
                IssueCode.INVALID_EXCERPT_DIGEST);
        assertInvalid(
                evaluationWithGoldRange(
                        new SourceRange("SOURCE_RU", 24, 66, "0".repeat(64))),
                IssueCode.EXCERPT_DIGEST_MISMATCH);

        List<ScoredItem> nullGold = new ArrayList<>();
        nullGold.add(null);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(SOURCES, nullGold, List.of()),
                IssueCode.NULL_GOLD_ITEM);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES,
                        List.of(
                                item("ITEM_DUP", Label.FINAL_DECISION, RANGE_RU),
                                item("ITEM_DUP", Label.REVISION_LINK, RANGE_EN)),
                        List.of()),
                IssueCode.DUPLICATE_GOLD_ITEM_ID);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES,
                        List.of(new ScoredItem("bad-id", Label.FINAL_DECISION, List.of(RANGE_RU))),
                        List.of()),
                IssueCode.INVALID_ITEM_ID);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES,
                        List.of(new ScoredItem("ITEM_GOLD", null, List.of(RANGE_RU))),
                        List.of()),
                IssueCode.MISSING_LABEL);
    }

    private static void malformedPredictionIdentityFailsTheWholeBatch() {
        List<ScoredItem> nullPrediction = new ArrayList<>();
        nullPrediction.add(null);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES, canonicalGold(), nullPrediction),
                IssueCode.NULL_PREDICTION_ITEM);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES,
                        canonicalGold(),
                        List.of(
                                item("ITEM_DUP", Label.FINAL_DECISION, RANGE_RU),
                                item("ITEM_DUP", Label.FINAL_DECISION, RANGE_RU))),
                IssueCode.DUPLICATE_PREDICTION_ITEM_ID);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES,
                        canonicalGold(),
                        List.of(new ScoredItem("bad-id", Label.FINAL_DECISION, List.of(RANGE_RU)))),
                IssueCode.INVALID_ITEM_ID);
        assertInvalid(
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES,
                        canonicalGold(),
                        List.of(new ScoredItem("ITEM_PRED", null, List.of(RANGE_RU)))),
                IssueCode.MISSING_LABEL);
    }

    private static void candidateSourceFailuresAreMeasuredAndRejected() {
        assertCandidateIssue(null, IssueCode.MISSING_SOURCE_REFS);
        assertCandidateIssue(List.of(), IssueCode.MISSING_SOURCE_REFS);

        List<SourceRange> nullRange = new ArrayList<>();
        nullRange.add(null);
        assertCandidateIssue(nullRange, IssueCode.NULL_SOURCE_REF);
        assertCandidateIssue(
                List.of(
                        new SourceRange(
                                "bad-source", 0, 1, RANGE_RU.excerptUtf8Sha256())),
                IssueCode.INVALID_SOURCE_REF_ID);
        assertCandidateIssue(
                List.of(
                        new SourceRange(
                                "SOURCE_UNKNOWN", 0, 1, RANGE_RU.excerptUtf8Sha256())),
                IssueCode.UNKNOWN_SOURCE_REF);
        assertCandidateIssue(
                List.of(
                        new SourceRange(
                                "SOURCE_RU", -1, 2, RANGE_RU.excerptUtf8Sha256())),
                IssueCode.INVALID_SOURCE_RANGE);
        assertCandidateIssue(
                List.of(
                        new SourceRange(
                                "SOURCE_RU", 1, 2, RANGE_RU.excerptUtf8Sha256())),
                IssueCode.SOURCE_RANGE_NOT_UTF8_BOUNDARY);
        assertCandidateIssue(
                List.of(new SourceRange("SOURCE_RU", 24, 66, "ABC")),
                IssueCode.INVALID_EXCERPT_DIGEST);
        assertCandidateIssue(
                List.of(new SourceRange("SOURCE_RU", 24, 66, "0".repeat(64))),
                IssueCode.EXCERPT_DIGEST_MISMATCH);

        EvaluationResult multiple =
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES,
                        List.of(item("ITEM_TARGET", Label.FINAL_DECISION, RANGE_RU)),
                        List.of(
                                new ScoredItem(
                                        "ITEM_TARGET",
                                        Label.FINAL_DECISION,
                                        List.of(
                                                new SourceRange(
                                                        "SOURCE_UNKNOWN",
                                                        0,
                                                        1,
                                                        RANGE_RU.excerptUtf8Sha256()),
                                                new SourceRange(
                                                        "SOURCE_RU", 24, 66, "0".repeat(64))))));
        CandidateResult candidate = onlyCandidate(multiple);
        check(
                candidate.sourceIssues().equals(
                        List.of(
                                new ValidationIssue(
                                        IssueCode.EXCERPT_DIGEST_MISMATCH, "ITEM_TARGET", 1),
                                new ValidationIssue(
                                        IssueCode.UNKNOWN_SOURCE_REF, "ITEM_TARGET", 0))),
                "candidate issues are not sorted exactly");
    }

    private static void sourceAndSupportFailuresCombineFailClosed() {
        EvaluationResult unsupportedValid =
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES,
                        List.of(item("ITEM_GOLD", Label.FINAL_DECISION, RANGE_RU)),
                        List.of(item("ITEM_OTHER", Label.FINAL_DECISION, RANGE_RU)));
        CandidateResult validCandidate = onlyCandidate(unsupportedValid);
        check(validCandidate.sourceStatus() == SourceStatus.VALID, "source should be valid");
        check(
                validCandidate.supportStatus()
                        == SupportStatus.UNSUPPORTED_BY_SYNTHETIC_GOLD,
                "candidate should be unsupported");
        check(
                validCandidate.disposition() == CandidateDisposition.REJECT_UNSUPPORTED,
                "unsupported candidate was not rejected");

        EvaluationResult unsupportedInvalid =
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES,
                        List.of(item("ITEM_GOLD", Label.FINAL_DECISION, RANGE_RU)),
                        List.of(
                                item(
                                        "ITEM_OTHER",
                                        Label.FINAL_DECISION,
                                        new SourceRange(
                                                "SOURCE_RU", 24, 66, "0".repeat(64)))));
        CandidateResult invalidCandidate = onlyCandidate(unsupportedInvalid);
        check(
                invalidCandidate.disposition()
                        == CandidateDisposition.REJECT_INVALID_SOURCE_AND_UNSUPPORTED,
                "combined source/support failure was not rejected exactly");

        EvaluationResult wrongLabel =
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES,
                        List.of(item("ITEM_SHARED", Label.FINAL_DECISION, RANGE_RU)),
                        List.of(item("ITEM_SHARED", Label.REVISION_LINK, RANGE_RU)));
        check(
                onlyCandidate(wrongLabel).supportStatus()
                        == SupportStatus.UNSUPPORTED_BY_SYNTHETIC_GOLD,
                "wrong label silently matched gold identity");
        check(wrongLabel.metrics().microCounts().truePositive() == 0, "wrong label counted TP");
        check(wrongLabel.metrics().microCounts().falsePositive() == 1, "wrong label FP mismatch");
        check(wrongLabel.metrics().microCounts().falseNegative() == 1, "wrong label FN mismatch");
    }

    private static void userTruthAndUncertainTaskActivationStayUnchanged() {
        List<FieldSnapshot> fields =
                List.of(
                        new FieldSnapshot(
                                "DECISION_ALPHA",
                                FieldName.DECISION_VALUE,
                                "USER_VALUE",
                                FieldOwnership.USER),
                        new FieldSnapshot(
                                "TASK_ALPHA",
                                FieldName.TASK_STATUS,
                                DecisionDeterministicSyntheticHarness.NEEDS_CONFIRMATION,
                                FieldOwnership.MACHINE),
                        new FieldSnapshot(
                                "TASK_ALPHA",
                                FieldName.TASK_TITLE,
                                "DRAFT_A",
                                FieldOwnership.MACHINE),
                        new FieldSnapshot(
                                "TASK_ALPHA",
                                FieldName.TASK_ASSIGNEE,
                                "ALPHA",
                                FieldOwnership.USER));
        List<MachineProposal> proposals =
                List.of(
                        new MachineProposal(
                                "TASK_ALPHA", FieldName.TASK_TITLE, "DRAFT_B"),
                        new MachineProposal(
                                "TASK_ALPHA",
                                FieldName.TASK_STATUS,
                                DecisionDeterministicSyntheticHarness.PLANNED),
                        new MachineProposal(
                                "DECISION_ALPHA", FieldName.DECISION_VALUE, "MACHINE_VALUE"),
                        new MachineProposal(
                                "TASK_ALPHA", FieldName.TASK_ASSIGNEE, "ALPHA"));
        ProtectionResult result =
                DecisionDeterministicSyntheticHarness.protectUserTruth(fields, proposals);
        check(result.state() == ProtectionState.COMPLETE, result.canonicalOutput());
        check(result.issues().isEmpty(), "valid protection input has issues");
        check(result.proposals().size() == 4, "proposal result count mismatch");
        check(
                disposition(result, "DECISION_ALPHA", FieldName.DECISION_VALUE)
                        == ProposalDisposition.USER_TRUTH_PROTECTED,
                "user decision value was not protected");
        check(
                disposition(result, "TASK_ALPHA", FieldName.TASK_STATUS)
                        == ProposalDisposition.ACTIVATION_REVIEW_REQUIRED,
                "uncertain task silently became planned");
        check(
                disposition(result, "TASK_ALPHA", FieldName.TASK_TITLE)
                        == ProposalDisposition.PROPOSED_DIFF_REVIEW_REQUIRED,
                "machine field update did not remain a diff");
        check(
                disposition(result, "TASK_ALPHA", FieldName.TASK_ASSIGNEE)
                        == ProposalDisposition.NO_CHANGE,
                "exact user value should be no-change");
        check(
                fields.get(0).currentValue().equals("USER_VALUE"),
                "current user value was mutated");
        check(
                fields.get(1).currentValue().equals(
                        DecisionDeterministicSyntheticHarness.NEEDS_CONFIRMATION),
                "current task state was mutated");

        String expected =
                "profile=stage0-v0.1\n"
                        + "state=COMPLETE\n"
                        + "stateMutation=false\n"
                        + "proposal=S14:DECISION_ALPHA|DECISION_VALUE|USER|USER_TRUTH_PROTECTED\n"
                        + "proposal=S10:TASK_ALPHA|TASK_ASSIGNEE|USER|NO_CHANGE\n"
                        + "proposal=S10:TASK_ALPHA|TASK_STATUS|MACHINE|ACTIVATION_REVIEW_REQUIRED\n"
                        + "proposal=S10:TASK_ALPHA|TASK_TITLE|MACHINE|PROPOSED_DIFF_REVIEW_REQUIRED\n";
        check(result.canonicalOutput().equals(expected), "protection canonical golden mismatch");

        List<FieldSnapshot> reversedFields = new ArrayList<>(fields);
        Collections.reverse(reversedFields);
        List<MachineProposal> reversedProposals = new ArrayList<>(proposals);
        Collections.reverse(reversedProposals);
        ProtectionResult permuted =
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        reversedFields, reversedProposals);
        ProtectionResult repeated =
                DecisionDeterministicSyntheticHarness.protectUserTruth(fields, proposals);
        check(result.equals(permuted), "protection input permutation changed typed output");
        check(result.equals(repeated), "protection repeat changed typed output");
        check(
                result.canonicalOutput().equals(permuted.canonicalOutput()),
                "protection permutation changed canonical output");
    }

    private static void malformedProtectionInputsFailClosed() {
        assertProtectionInvalid(
                DecisionDeterministicSyntheticHarness.protectUserTruth(null, List.of()),
                IssueCode.NULL_FIELD_LIST);
        assertProtectionInvalid(
                DecisionDeterministicSyntheticHarness.protectUserTruth(List.of(), null),
                IssueCode.NULL_PROPOSAL_LIST);

        List<FieldSnapshot> nullField = new ArrayList<>();
        nullField.add(null);
        assertProtectionInvalid(
                DecisionDeterministicSyntheticHarness.protectUserTruth(nullField, List.of()),
                IssueCode.NULL_FIELD_SNAPSHOT);
        List<MachineProposal> nullProposal = new ArrayList<>();
        nullProposal.add(null);
        assertProtectionInvalid(
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        List.of(
                                new FieldSnapshot(
                                        "TASK_ALPHA",
                                        FieldName.TASK_TITLE,
                                        "A",
                                        FieldOwnership.MACHINE)),
                        nullProposal),
                IssueCode.NULL_MACHINE_PROPOSAL);

        assertProtectionInvalid(
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        List.of(
                                new FieldSnapshot(
                                        "bad-id",
                                        FieldName.TASK_TITLE,
                                        "A",
                                        FieldOwnership.MACHINE)),
                        List.of()),
                IssueCode.INVALID_ENTITY_ID);
        assertProtectionInvalid(
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        List.of(
                                new FieldSnapshot(
                                        "TASK_ALPHA", null, "A", FieldOwnership.MACHINE)),
                        List.of()),
                IssueCode.MISSING_FIELD_NAME);
        assertProtectionInvalid(
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        List.of(
                                new FieldSnapshot(
                                        "TASK_ALPHA", FieldName.TASK_TITLE, "A", null)),
                        List.of()),
                IssueCode.MISSING_FIELD_OWNERSHIP);
        assertProtectionInvalid(
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        List.of(
                                new FieldSnapshot(
                                        "TASK_ALPHA",
                                        FieldName.TASK_TITLE,
                                        null,
                                        FieldOwnership.MACHINE)),
                        List.of()),
                IssueCode.NULL_FIELD_VALUE);
        assertProtectionInvalid(
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        List.of(
                                new FieldSnapshot(
                                        "TASK_ALPHA",
                                        FieldName.TASK_TITLE,
                                        "A",
                                        FieldOwnership.MACHINE)),
                        List.of(
                                new MachineProposal(
                                        "TASK_ALPHA", FieldName.TASK_TITLE, null))),
                IssueCode.NULL_PROPOSED_VALUE);
        assertProtectionInvalid(
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        List.of(
                                new FieldSnapshot(
                                        "TASK_ALPHA",
                                        FieldName.TASK_TITLE,
                                        "A",
                                        FieldOwnership.MACHINE),
                                new FieldSnapshot(
                                        "TASK_ALPHA",
                                        FieldName.TASK_TITLE,
                                        "B",
                                        FieldOwnership.USER)),
                        List.of()),
                IssueCode.DUPLICATE_FIELD_SNAPSHOT);
        assertProtectionInvalid(
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        List.of(
                                new FieldSnapshot(
                                        "TASK_ALPHA",
                                        FieldName.TASK_TITLE,
                                        "A",
                                        FieldOwnership.MACHINE)),
                        List.of(
                                new MachineProposal(
                                        "TASK_ALPHA", FieldName.TASK_TITLE, "B"),
                                new MachineProposal(
                                        "TASK_ALPHA", FieldName.TASK_TITLE, "C"))),
                IssueCode.DUPLICATE_MACHINE_PROPOSAL);
        assertProtectionInvalid(
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        List.of(
                                new FieldSnapshot(
                                        "TASK_ALPHA",
                                        FieldName.TASK_TITLE,
                                        "A",
                                        FieldOwnership.MACHINE)),
                        List.of(
                                new MachineProposal(
                                        "TASK_ALPHA", FieldName.TASK_DEADLINE, "OPAQUE"))),
                IssueCode.UNKNOWN_FIELD_TARGET);
    }

    private static void recordsAndResultsAreImmutable() {
        List<SourceRange> mutableRanges = new ArrayList<>();
        mutableRanges.add(RANGE_RU);
        ScoredItem item =
                new ScoredItem("ITEM_IMMUTABLE", Label.FINAL_DECISION, mutableRanges);
        mutableRanges.clear();
        check(item.sourceRanges().equals(List.of(RANGE_RU)), "item did not copy ranges");
        expectUnsupported(() -> item.sourceRanges().clear());

        EvaluationResult evaluation =
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES, List.of(item), List.of(item));
        expectUnsupported(() -> evaluation.candidates().clear());
        expectUnsupported(() -> evaluation.issues().add(null));
        expectUnsupported(() -> evaluation.metrics().perLabel().clear());
        expectUnsupported(() -> evaluation.candidates().get(0).sourceIssues().add(null));

        ProtectionResult protection =
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        List.of(
                                new FieldSnapshot(
                                        "TASK_ALPHA",
                                        FieldName.TASK_TITLE,
                                        "A",
                                        FieldOwnership.MACHINE)),
                        List.of(
                                new MachineProposal(
                                        "TASK_ALPHA", FieldName.TASK_TITLE, "B")));
        expectUnsupported(() -> protection.proposals().clear());
        expectUnsupported(() -> protection.issues().add(null));
    }

    private static void canonicalOutputsContainNoGeneratedTextOrFieldValues() {
        EvaluationResult evaluation =
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES, canonicalGold(), canonicalGold());
        String evaluationOutput = evaluation.canonicalOutput();
        check(!evaluationOutput.contains(RU_TEXT), "canonical output contains RU source text");
        check(!evaluationOutput.contains(EN_TEXT), "canonical output contains EN source text");
        check(!evaluationOutput.contains(MIXED_TEXT), "canonical output contains mixed source text");

        ProtectionResult protection =
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        List.of(
                                new FieldSnapshot(
                                        "DECISION_ALPHA",
                                        FieldName.DECISION_VALUE,
                                        "PRIVATE_CURRENT_SENTINEL",
                                        FieldOwnership.USER)),
                        List.of(
                                new MachineProposal(
                                        "DECISION_ALPHA",
                                        FieldName.DECISION_VALUE,
                                        "PRIVATE_PROPOSED_SENTINEL")));
        String protectionOutput = protection.canonicalOutput();
        check(
                !protectionOutput.contains("PRIVATE_CURRENT_SENTINEL"),
                "canonical output contains current field value");
        check(
                !protectionOutput.contains("PRIVATE_PROPOSED_SENTINEL"),
                "canonical output contains proposed field value");
    }

    private static List<ScoredItem> canonicalGold() {
        return List.of(
                item("ITEM_DEC_1", Label.FINAL_DECISION, RANGE_RU),
                item("ITEM_DEC_2", Label.FINAL_DECISION, RANGE_EN),
                item("ITEM_LINK_1", Label.REVISION_LINK, RANGE_EN),
                item("ITEM_TASK_1", Label.CONFIRMED_TASK, RANGE_MIXED));
    }

    private static EvaluationResult evaluationWithGoldRange(SourceRange range) {
        return DecisionDeterministicSyntheticHarness.evaluate(
                SOURCES,
                List.of(item("ITEM_GOLD", Label.FINAL_DECISION, range)),
                List.of());
    }

    private static void assertCandidateIssue(
            List<SourceRange> predictionRanges, IssueCode expectedIssue) {
        ScoredItem prediction =
                new ScoredItem("ITEM_TARGET", Label.FINAL_DECISION, predictionRanges);
        EvaluationResult result =
                DecisionDeterministicSyntheticHarness.evaluate(
                        SOURCES,
                        List.of(item("ITEM_TARGET", Label.FINAL_DECISION, RANGE_RU)),
                        List.of(prediction));
        check(result.state() == EvaluationState.COMPLETE, result.canonicalOutput());
        CandidateResult candidate = onlyCandidate(result);
        check(candidate.sourceStatus() == SourceStatus.INVALID, "candidate source was not invalid");
        check(
                candidate.supportStatus() == SupportStatus.SUPPORTED_BY_SYNTHETIC_GOLD,
                "candidate support unexpectedly changed");
        check(
                candidate.disposition() == CandidateDisposition.REJECT_INVALID_SOURCE,
                "invalid source candidate was not rejected");
        check(
                candidate.sourceIssues().stream()
                        .anyMatch(issue -> issue.code() == expectedIssue),
                "missing candidate issue " + expectedIssue + ": " + result.canonicalOutput());
        check(result.metrics().sourceValidity().equals(fraction(0, 1)),
                "invalid source metric mismatch");
        check(result.metrics().unsupportedClaimRate().equals(fraction(0, 1)),
                "supported candidate became unsupported");
    }

    private static CandidateResult onlyCandidate(EvaluationResult result) {
        check(result.state() == EvaluationState.COMPLETE, result.canonicalOutput());
        check(result.candidates().size() == 1, "expected exactly one candidate");
        return result.candidates().get(0);
    }

    private static ProposalDisposition disposition(
            ProtectionResult result, String entityId, FieldName fieldName) {
        return result.proposals().stream()
                .filter(
                        proposal ->
                                proposal.entityId().equals(entityId)
                                        && proposal.fieldName() == fieldName)
                .map(ProtectedProposal::disposition)
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "missing protected proposal " + entityId + ":" + fieldName));
    }

    private static ScoredItem item(String itemId, Label label, SourceRange sourceRange) {
        return new ScoredItem(itemId, label, List.of(sourceRange));
    }

    private static Fraction fraction(long numerator, long denominator) {
        return Fraction.of(numerator, denominator);
    }

    private static void assertInvalid(EvaluationResult result, IssueCode expectedIssue) {
        check(result.state() == EvaluationState.INVALID_INPUT, "expected invalid evaluation");
        check(result.metrics() == null, "invalid evaluation exposed metrics");
        check(result.candidates().isEmpty(), "invalid evaluation exposed candidates");
        check(
                result.issues().stream().anyMatch(issue -> issue.code() == expectedIssue),
                "missing issue " + expectedIssue + ": " + result.canonicalOutput());
        assertSortedDistinct(result.issues());
    }

    private static void assertProtectionInvalid(
            ProtectionResult result, IssueCode expectedIssue) {
        check(result.state() == ProtectionState.INVALID_INPUT, "expected invalid protection input");
        check(result.proposals().isEmpty(), "invalid protection input exposed proposals");
        check(
                result.issues().stream().anyMatch(issue -> issue.code() == expectedIssue),
                "missing protection issue " + expectedIssue + ": " + result.canonicalOutput());
        assertSortedDistinct(result.issues());
    }

    private static void assertSortedDistinct(List<ValidationIssue> issues) {
        List<ValidationIssue> sorted = new ArrayList<>(issues);
        sorted.sort(
                java.util.Comparator.comparing(
                                (ValidationIssue issue) -> issue.code().name())
                        .thenComparing(ValidationIssue::entityId)
                        .thenComparingInt(ValidationIssue::sourceRefIndex));
        check(issues.equals(sorted), "issues are not deterministically sorted");
        check(issues.stream().distinct().count() == issues.size(), "issues are not deduplicated");
    }

    private static void expectUnsupported(Runnable mutation) {
        try {
            mutation.run();
            throw new AssertionError("mutation unexpectedly succeeded");
        } catch (UnsupportedOperationException expected) {
            // Expected: every list boundary is an immutable snapshot.
        }
    }

    private static void expectIllegalArgument(Runnable call) {
        try {
            call.run();
            throw new AssertionError("invalid value unexpectedly succeeded");
        } catch (IllegalArgumentException expected) {
            // Expected: invalid public record construction is rejected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
