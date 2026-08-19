package com.monumentogram.dora.stage0.diar.i1;

import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.AggregateMetrics;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.CampaignScore;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.CaseInput;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.Condition;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.DerMetrics;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.EvaluationAccepted;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.EvaluationRejected;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.EvaluationResult;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.ExactRational;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.JerMetrics;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.LanguageMetrics;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.LanguageSlice;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.RejectCode;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.ReviewFlagMetrics;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.ScoringAtom;
import com.monumentogram.dora.stage0.diar.i1.DiarSyntheticScoringOracle.ScoringRequest;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class DiarSyntheticScoringOracleTest {
    private static final String PASS_MARKER =
            "POC_DIAR_I1_SYNTHETIC_SCORING_ORACLE_TESTS_OK";
    private static final String EXPECTED_CAMPAIGN_SHA256 =
            "4e6cbbaf65215e52f965295600c4919e7496e0b05fb137ea5b342fa1e62abb6e";

    /** Frozen AUTH-01 fixture identifiers. They are not production error taxonomy. */
    private enum ErrorShape {
        PERFECT_PERMUTED,
        MISS_ONLY,
        FALSE_ALARM_ONLY,
        MIXED
    }

    private DiarSyntheticScoringOracleTest() {}

    public static void main(String[] ignored) {
        frozenIdentifiersAndGeneratedMatrixAreExact();
        generatedCampaignHasKnownAggregateAnswersAndGoldenDigest();
        repeatedReverseRotateAndSpeakerListOrderAreInvariant();
        derAndJerUseIndependentOptimalMappings();
        missedFalseAlarmAndReviewMetricsAreExact();
        zeroDenominatorsAreExplicitlyUndefined();
        unicodeAndUtf8BoundsAreExact();
        malformedInputsAndBudgetsFailClosed();
        inputsAndResultsAreDefensivelyImmutable();
        exactRationalAndPublicResultInvariantsAreStrict();
        canonicalOutputIsAggregateOnlyAsciiAndNoncircular();
        System.out.println(PASS_MARKER);
    }

    private static void frozenIdentifiersAndGeneratedMatrixAreExact() {
        equal(
                List.of("RU", "EN", "MIXED_RU_EN"),
                enumNames(LanguageSlice.values()),
                "language order");
        equal(
                List.of(
                        "CLEAN",
                        "NOISY",
                        "REMOTE",
                        "OVERLAP",
                        "FAST_TURN",
                        "RETURNING_SPEAKER",
                        "SPEAKERPHONE",
                        "TV_NEGATIVE"),
                enumNames(Condition.values()),
                "condition order");
        equal(
                List.of(
                        "PERFECT_PERMUTED",
                        "MISS_ONLY",
                        "FALSE_ALARM_ONLY",
                        "MIXED"),
                enumNames(ErrorShape.values()),
                "error-shape order");

        ScoringRequest campaign = generatedCampaign();
        equal(564, campaign.cases().size(), "generated matrix case count");
        for (LanguageSlice language : LanguageSlice.values()) {
            equal(188L, countLanguage(campaign, language), "language matrix count " + language);
        }
        for (Condition condition : Condition.values()) {
            long expected = condition == Condition.OVERLAP ? 60L : 72L;
            equal(expected, countCondition(campaign, condition), "condition matrix count " + condition);
        }
        check(
                campaign.cases().stream()
                        .filter(input -> input.condition() == Condition.OVERLAP)
                        .allMatch(input -> uniqueReferences(input) >= 2),
                "overlap matrix excludes single-reference cases");
    }

    private static void generatedCampaignHasKnownAggregateAnswersAndGoldenDigest() {
        CampaignScore score = accepted(generatedCampaign());
        equal(DiarSyntheticScoringOracle.SCHEMA_VERSION, score.schemaVersion(), "schema version");
        equal(564, score.caseCount(), "campaign case count");
        assertAggregate(
                score.overall(),
                564,
                "2565000",
                "162000",
                "162000",
                "480000",
                "2745000",
                "268/915",
                "743/2256",
                2004L,
                "227/1002",
                "1133/4512",
                288L,
                "24/47",
                188,
                141,
                235,
                94,
                "855000",
                "683000",
                "3/4",
                "683/855");

        equal(3, score.byLanguage().size(), "language aggregate count");
        for (int index = 0; index < LanguageSlice.values().length; index++) {
            LanguageMetrics item = score.byLanguage().get(index);
            equal(LanguageSlice.values()[index], item.slice(), "language aggregate order");
            equal(188, item.metrics().caseCount(), "language case count");
        }
        equal(8, score.byCondition().size(), "condition aggregate count");
        for (int index = 0; index < Condition.values().length; index++) {
            equal(
                    Condition.values()[index],
                    score.byCondition().get(index).condition(),
                    "condition aggregate order");
            int expected = Condition.values()[index] == Condition.OVERLAP ? 60 : 72;
            equal(expected, score.byCondition().get(index).metrics().caseCount(), "condition cases");
        }

        equal(EXPECTED_CAMPAIGN_SHA256, score.canonicalSha256(), "campaign golden digest");
    }

    private static void repeatedReverseRotateAndSpeakerListOrderAreInvariant() {
        ScoringRequest original = generatedCampaign();
        CampaignScore first = accepted(original);
        CampaignScore repeated = accepted(original);
        equal(first, repeated, "repeat typed result");
        equal(first.canonicalSha256(), repeated.canonicalSha256(), "repeat digest");

        List<CaseInput> reversed = new ArrayList<>(original.cases());
        Collections.reverse(reversed);
        CampaignScore reversedScore = accepted(new ScoringRequest(reversed));
        equal(first, reversedScore, "reverse case-order result");

        List<CaseInput> rotated = new ArrayList<>(original.cases());
        Collections.rotate(rotated, 137);
        CampaignScore rotatedScore = accepted(new ScoringRequest(rotated));
        equal(first, rotatedScore, "rotated case-order result");

        List<CaseInput> speakerOrderReversed = new ArrayList<>();
        for (CaseInput input : original.cases()) {
            List<ScoringAtom> atoms = new ArrayList<>();
            for (ScoringAtom atom : input.atoms()) {
                List<String> references = new ArrayList<>(atom.referenceSpeakers());
                List<String> hypotheses = new ArrayList<>(atom.hypothesisSpeakers());
                Collections.reverse(references);
                Collections.reverse(hypotheses);
                atoms.add(
                        new ScoringAtom(
                                atom.startMicros(), atom.endMicros(), references, hypotheses));
            }
            speakerOrderReversed.add(copy(input, input.caseId(), atoms));
        }
        CampaignScore speakerOrderScore = accepted(new ScoringRequest(speakerOrderReversed));
        equal(first, speakerOrderScore, "per-atom speaker-list order result");
        equal(
                first.canonicalSha256(),
                speakerOrderScore.canonicalSha256(),
                "speaker-list order digest");
    }

    private static void derAndJerUseIndependentOptimalMappings() {
        CaseInput tie =
                new CaseInput(
                        "DER-JER-TIE",
                        LanguageSlice.EN,
                        Condition.CLEAN,
                        false,
                        false,
                        List.of(
                                atom(0L, 100L, List.of("r-a"), List.of("h-only")),
                                atom(100L, 200L, List.of("r-b"), List.of("h-only"))));
        AggregateMetrics metrics = accepted(new ScoringRequest(List.of(tie))).overall();
        assertDer(metrics.der(), "0", "0", "100", "200", "1/2", "1/2");
        assertJer(metrics.jer(), 2L, "3/4", "3/4");
        equal(1L, metrics.speakerCount().absoluteErrorTotal(), "tie speaker count error");
        equal(
                "1/1",
                metrics.speakerCount().meanAbsoluteError().orElseThrow().canonical(),
                "tie speaker count MAE");

        CaseInput separateOptima =
                new CaseInput(
                        "SEPARATE-OPTIMA",
                        LanguageSlice.RU,
                        Condition.RETURNING_SPEAKER,
                        false,
                        false,
                        List.of(
                                atom(0L, 90L, List.of("r-a"), List.of("h-a")),
                                atom(90L, 150L, List.of("r-b"), List.of("h-a")),
                                atom(150L, 200L, List.of("r-b"), List.of("h-b")),
                                atom(200L, 240L, List.of("r-a"), List.of("h-b"))));
        AggregateMetrics separate =
                accepted(new ScoringRequest(List.of(separateOptima))).overall();
        assertDer(separate.der(), "0", "0", "100", "240", "5/12", "5/12");
        assertJer(separate.jer(), 2L, "34/57", "34/57");
    }

    private static void missedFalseAlarmAndReviewMetricsAreExact() {
        CaseInput missedAndFalseAlarm =
                new CaseInput(
                        "M-F",
                        LanguageSlice.MIXED_RU_EN,
                        Condition.SPEAKERPHONE,
                        true,
                        true,
                        List.of(
                                atom(
                                        0L,
                                        100L,
                                        List.of("r-a", "r-b"),
                                        List.of("h-a")),
                                atom(
                                        100L,
                                        200L,
                                        List.of("r-a"),
                                        List.of("h-a", "h-b"))));
        AggregateMetrics metrics =
                accepted(new ScoringRequest(List.of(missedAndFalseAlarm))).overall();
        assertDer(metrics.der(), "100", "100", "0", "300", "2/3", "2/3");

        ScoringRequest reviewRequest =
                new ScoringRequest(
                        List.of(
                                reviewCase("REVIEW-TP", 100L, true, true),
                                reviewCase("REVIEW-FN", 300L, true, false),
                                reviewCase("REVIEW-FP", 200L, false, true)));
        ReviewFlagMetrics flags = accepted(reviewRequest).overall().reviewFlags();
        equal(2, flags.requiredCases(), "required case count");
        equal(1, flags.flaggedRequiredCases(), "review true positive count");
        equal(2, flags.flaggedCases(), "flagged case count");
        equal(1, flags.falsePositiveCases(), "review false positive count");
        equal(BigInteger.valueOf(400L), flags.requiredDurationMicros(), "required duration");
        equal(
                BigInteger.valueOf(100L),
                flags.flaggedRequiredDurationMicros(),
                "flagged required duration");
        equal("1/2", flags.countRecall().orElseThrow().canonical(), "count recall");
        equal("1/4", flags.durationRecall().orElseThrow().canonical(), "duration recall");
    }

    private static void zeroDenominatorsAreExplicitlyUndefined() {
        CaseInput noReview = reviewCase("NO-REVIEW", 100L, false, false);
        CampaignScore score = accepted(new ScoringRequest(List.of(noReview)));
        ReviewFlagMetrics flags = score.overall().reviewFlags();
        equal(0, flags.requiredCases(), "zero review-required cases");
        check(flags.countRecall().isEmpty(), "zero count denominator is undefined");
        check(flags.durationRecall().isEmpty(), "zero duration denominator is undefined");

        AggregateMetrics emptyLanguage = score.byLanguage().get(1).metrics();
        equal(0, emptyLanguage.caseCount(), "empty EN slice");
        check(emptyLanguage.der().microDer().isEmpty(), "empty DER micro undefined");
        check(emptyLanguage.der().caseMacroDer().isEmpty(), "empty DER macro undefined");
        check(emptyLanguage.jer().speakerMacroJer().isEmpty(), "empty speaker JER undefined");
        check(emptyLanguage.jer().caseMacroJer().isEmpty(), "empty case JER undefined");
        check(
                emptyLanguage.speakerCount().meanAbsoluteError().isEmpty(),
                "empty count MAE undefined");
    }

    private static void unicodeAndUtf8BoundsAreExact() {
        String exactly256Ascii = "c".repeat(256);
        String exactly256Utf8 = "\uD83D\uDE80".repeat(64);
        CaseInput valid =
                new CaseInput(
                        exactly256Ascii,
                        LanguageSlice.MIXED_RU_EN,
                        Condition.REMOTE,
                        false,
                        false,
                        List.of(atom(0L, 1L, List.of(exactly256Utf8), List.of("\u0433\u0438\u043f"))));
        accepted(new ScoringRequest(List.of(valid)));

        assertRejected(
                new ScoringRequest(List.of(copy(valid, "x".repeat(257), valid.atoms()))),
                RejectCode.CASE_ID_INVALID,
                0);
        CaseInput tooManySpeakerBytes =
                withAtom(
                        valid,
                        atom(0L, 1L, List.of("\uD83D\uDE80".repeat(65)), List.of("h")));
        assertRejected(
                new ScoringRequest(List.of(tooManySpeakerBytes)),
                RejectCode.SPEAKER_LABEL_INVALID,
                0);
        assertRejected(
                new ScoringRequest(List.of(copy(valid, "bad-\uD800", valid.atoms()))),
                RejectCode.CASE_ID_INVALID,
                0);
        assertRejected(
                new ScoringRequest(
                        List.of(withAtom(valid, atom(0L, 1L, List.of("bad-\uDC00"), List.of("h"))))),
                RejectCode.SPEAKER_LABEL_INVALID,
                0);
    }

    private static void malformedInputsAndBudgetsFailClosed() {
        assertRejected(null, RejectCode.NULL_REQUEST, -1);
        assertRejected(new ScoringRequest(null), RejectCode.CASES_MISSING, -1);
        assertRejected(new ScoringRequest(List.of()), RejectCode.CASES_EMPTY, -1);
        assertRejected(
                new ScoringRequest(
                        Collections.nCopies(
                                DiarSyntheticScoringOracle.MAX_CASES + 1, validCase("SAME"))),
                RejectCode.CASE_COUNT_OUT_OF_RANGE,
                -1);
        List<CaseInput> nullCase = new ArrayList<>();
        nullCase.add(null);
        assertRejected(new ScoringRequest(nullCase), RejectCode.CASE_MISSING, 0);

        CaseInput valid = validCase("VALID");
        assertRejected(
                new ScoringRequest(List.of(copy(valid, null, valid.atoms()))),
                RejectCode.CASE_ID_INVALID,
                0);
        assertRejected(
                new ScoringRequest(List.of(copy(valid, "   ", valid.atoms()))),
                RejectCode.CASE_ID_INVALID,
                0);
        assertRejected(
                new ScoringRequest(List.of(valid, copy(valid, valid.caseId(), valid.atoms()))),
                RejectCode.DUPLICATE_CASE_ID,
                1);
        assertRejected(
                new ScoringRequest(
                        List.of(
                                new CaseInput(
                                        "NO-LANGUAGE",
                                        null,
                                        Condition.CLEAN,
                                        false,
                                        false,
                                        valid.atoms()))),
                RejectCode.LANGUAGE_SLICE_MISSING,
                0);
        assertRejected(
                new ScoringRequest(
                        List.of(
                                new CaseInput(
                                        "NO-CONDITION",
                                        LanguageSlice.RU,
                                        null,
                                        false,
                                        false,
                                        valid.atoms()))),
                RejectCode.CONDITION_MISSING,
                0);
        assertRejected(
                new ScoringRequest(
                        List.of(
                                new CaseInput(
                                        "NO-ATOMS",
                                        LanguageSlice.RU,
                                        Condition.CLEAN,
                                        false,
                                        false,
                                        null))),
                RejectCode.ATOMS_MISSING,
                0);
        assertRejected(
                new ScoringRequest(List.of(copy(valid, "EMPTY-ATOMS", List.of()))),
                RejectCode.ATOMS_EMPTY,
                0);
        assertRejected(
                new ScoringRequest(
                        List.of(
                                copy(
                                        valid,
                                        "MANY-ATOMS",
                                        Collections.nCopies(
                                                DiarSyntheticScoringOracle.MAX_ATOMS_PER_CASE + 1,
                                                valid.atoms().get(0))))),
                RejectCode.ATOM_COUNT_OUT_OF_RANGE,
                0);

        List<ScoringAtom> missingAtom = new ArrayList<>();
        missingAtom.add(null);
        assertRejected(
                new ScoringRequest(List.of(copy(valid, "MISSING-ATOM", missingAtom))),
                RejectCode.ATOM_MISSING,
                0);
        assertAtomRejected(valid, atom(-1L, 1L, List.of("r"), List.of("h")), RejectCode.ATOM_RANGE_INVALID);
        assertAtomRejected(valid, atom(1L, 1L, List.of("r"), List.of("h")), RejectCode.ATOM_RANGE_INVALID);
        assertRejected(
                new ScoringRequest(
                        List.of(
                                copy(
                                        valid,
                                        "OVERLAPPING-ATOMS",
                                        List.of(
                                                atom(0L, 10L, List.of("r"), List.of("h")),
                                                atom(9L, 20L, List.of("r"), List.of("h")))))),
                RejectCode.ATOM_ORDER_OR_OVERLAP_INVALID,
                0);
        assertAtomRejected(valid, new ScoringAtom(0L, 1L, null, List.of("h")), RejectCode.REFERENCE_SPEAKERS_MISSING);
        assertAtomRejected(valid, new ScoringAtom(0L, 1L, List.of("r"), null), RejectCode.HYPOTHESIS_SPEAKERS_MISSING);
        assertAtomRejected(valid, atom(0L, 1L, List.of(" "), List.of("h")), RejectCode.SPEAKER_LABEL_INVALID);
        assertAtomRejected(
                valid,
                atom(0L, 1L, List.of("r", "r"), List.of("h")),
                RejectCode.DUPLICATE_REFERENCE_SPEAKER_IN_ATOM);
        assertAtomRejected(
                valid,
                atom(0L, 1L, List.of("r"), List.of("h", "h")),
                RejectCode.DUPLICATE_HYPOTHESIS_SPEAKER_IN_ATOM);
        assertAtomRejected(valid, atom(0L, 1L, List.of(), List.of("h")), RejectCode.REFERENCE_SPEAKER_COUNT_OUT_OF_RANGE);
        assertAtomRejected(
                valid,
                atom(0L, 1L, labels("r", 7), List.of("h")),
                RejectCode.REFERENCE_SPEAKER_COUNT_OUT_OF_RANGE);
        assertAtomRejected(
                valid,
                atom(0L, 1L, List.of("r"), labels("h", 13)),
                RejectCode.HYPOTHESIS_SPEAKER_COUNT_OUT_OF_RANGE);

        ScoringAtom budgetAtom = atom(0L, 1L, List.of("r"), List.of("h"));
        List<ScoringAtom> maximumAtoms =
                Collections.nCopies(DiarSyntheticScoringOracle.MAX_ATOMS_PER_CASE, budgetAtom);
        List<CaseInput> atomBudgetCases = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            atomBudgetCases.add(copy(valid, "ATOM-BUDGET-" + index, maximumAtoms));
        }
        assertRejected(
                new ScoringRequest(atomBudgetCases),
                RejectCode.ATOM_ORDER_OR_OVERLAP_INVALID,
                0);

        List<ScoringAtom> nonOverlappingMaximumAtoms = new ArrayList<>();
        for (int index = 0; index < DiarSyntheticScoringOracle.MAX_ATOMS_PER_CASE; index++) {
            nonOverlappingMaximumAtoms.add(
                    atom(index, index + 1L, List.of("r"), List.of("h")));
        }
        atomBudgetCases.clear();
        for (int index = 0; index < 25; index++) {
            atomBudgetCases.add(
                    copy(valid, "TOTAL-ATOM-BUDGET-" + index, nonOverlappingMaximumAtoms));
        }
        assertRejected(
                new ScoringRequest(atomBudgetCases),
                RejectCode.TOTAL_ATOM_BUDGET_EXCEEDED,
                24);

        ScoringAtom expensive = atom(0L, 1L, labels("r", 6), labels("h", 12));
        List<CaseInput> expensiveCases = new ArrayList<>();
        for (int index = 0; index < 14; index++) {
            expensiveCases.add(withAtom(copy(valid, "MAPPING-BUDGET-" + index, valid.atoms()), expensive));
        }
        assertRejected(
                new ScoringRequest(expensiveCases),
                RejectCode.MAPPING_WORK_BUDGET_EXCEEDED,
                13);

        assertAtomRejected(
                valid,
                atom(0L, Long.MAX_VALUE, List.of("r-a", "r-b"), List.of("h")),
                RejectCode.ARITHMETIC_OVERFLOW);

        ScoringRequest validThenInvalid =
                new ScoringRequest(
                        List.of(
                                validCase("FIRST-VALID"),
                                withAtom(
                                        validCase("SECOND-INVALID"),
                                        atom(2L, 1L, List.of("r"), List.of("h")))));
        assertRejected(validThenInvalid, RejectCode.ATOM_RANGE_INVALID, 1);
    }

    private static void inputsAndResultsAreDefensivelyImmutable() {
        List<String> references = new ArrayList<>(List.of("r"));
        List<String> hypotheses = new ArrayList<>(List.of("h"));
        ScoringAtom atom = new ScoringAtom(0L, 1L, references, hypotheses);
        references.add("r-mutated");
        hypotheses.add("h-mutated");
        equal(List.of("r"), atom.referenceSpeakers(), "reference defensive copy");
        equal(List.of("h"), atom.hypothesisSpeakers(), "hypothesis defensive copy");
        assertThrows(UnsupportedOperationException.class, () -> atom.referenceSpeakers().add("x"));

        List<ScoringAtom> atoms = new ArrayList<>(List.of(atom));
        CaseInput input =
                new CaseInput(
                        "IMMUTABLE",
                        LanguageSlice.RU,
                        Condition.CLEAN,
                        false,
                        false,
                        atoms);
        atoms.clear();
        equal(1, input.atoms().size(), "atom defensive copy");
        assertThrows(UnsupportedOperationException.class, () -> input.atoms().clear());

        List<CaseInput> cases = new ArrayList<>(List.of(input));
        ScoringRequest request = new ScoringRequest(cases);
        cases.clear();
        equal(1, request.cases().size(), "case defensive copy");
        assertThrows(UnsupportedOperationException.class, () -> request.cases().clear());

        CampaignScore score = accepted(request);
        assertThrows(UnsupportedOperationException.class, () -> score.byLanguage().clear());
        assertThrows(UnsupportedOperationException.class, () -> score.byCondition().clear());
    }

    private static void exactRationalAndPublicResultInvariantsAreStrict() {
        equal("1/2", ExactRational.of(2L, 4L).canonical(), "rational reduction");
        equal("5/6", ExactRational.of(1L, 2L).add(ExactRational.of(1L, 3L)).canonical(), "rational addition");
        equal("1/6", ExactRational.of(1L, 2L).divide(3L).canonical(), "rational division");
        check(ExactRational.of(2L, 3L).compareTo(ExactRational.of(3L, 4L)) < 0, "exact compare");
        assertThrows(IllegalArgumentException.class, () -> ExactRational.of(-1L, 1L));
        assertThrows(IllegalArgumentException.class, () -> ExactRational.of(1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> ExactRational.one().divide(0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationRejected(RejectCode.CASES_EMPTY, -2));
        assertThrows(
                NullPointerException.class,
                () -> new DiarSyntheticScoringOracle.EvaluationAccepted(null));

        CampaignScore valid = accepted(new ScoringRequest(List.of(validCase("PUBLIC-INVARIANT"))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CampaignScore(
                                2,
                                valid.caseCount(),
                                valid.overall(),
                                valid.byLanguage(),
                                valid.byCondition()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CampaignScore(
                                1,
                                valid.caseCount() + 1,
                                valid.overall(),
                                valid.byLanguage(),
                                valid.byCondition()));
    }

    private static void canonicalOutputIsAggregateOnlyAsciiAndNoncircular() {
        CampaignScore score = accepted(generatedCampaign());
        String canonical = score.canonicalJson();
        check(canonical.startsWith("{\"schemaVersion\":1,\"caseCount\":564"), "canonical prefix");
        check(canonical.contains("\"condition\":\"TV_NEGATIVE\""), "frozen condition emitted");
        check(!canonical.contains("DIAR-PRIVATE-CASE"), "case identifiers excluded");
        check(!canonical.contains("reference-private-speaker"), "reference labels excluded");
        check(!canonical.contains("hypothesis-private-speaker"), "hypothesis labels excluded");
        check(!canonical.contains("sha256"), "canonical output has no self-digest field");
        check(
                canonical.chars().allMatch(codePoint -> codePoint >= 0x20 && codePoint <= 0x7e),
                "canonical output is printable ASCII");
        equal(64, score.canonicalSha256().length(), "SHA-256 hex length");
        check(
                score.canonicalSha256().chars().allMatch(DiarSyntheticScoringOracleTest::isHex),
                "SHA-256 lowercase hex");
    }

    private static ScoringRequest generatedCampaign() {
        List<CaseInput> cases = new ArrayList<>();
        int ordinal = 0;
        for (LanguageSlice language : LanguageSlice.values()) {
            for (Condition condition : Condition.values()) {
                int minimumSpeakers = condition == Condition.OVERLAP ? 2 : 1;
                for (int speakers = minimumSpeakers; speakers <= 6; speakers++) {
                    for (ErrorShape shape : ErrorShape.values()) {
                        boolean reviewRequired = ordinal % 3 == 0;
                        boolean reviewFlagged = reviewRequired ? ordinal % 4 != 0 : ordinal % 4 == 1;
                        String id =
                                "DIAR-PRIVATE-CASE-"
                                        + language.name()
                                        + '-'
                                        + condition.name()
                                        + '-'
                                        + speakers
                                        + '-'
                                        + shape.name();
                        cases.add(
                                new CaseInput(
                                        id,
                                        language,
                                        condition,
                                        reviewRequired,
                                        reviewFlagged,
                                        generatedAtoms(condition, speakers, shape)));
                        ordinal++;
                    }
                }
            }
        }
        return new ScoringRequest(cases);
    }

    private static List<ScoringAtom> generatedAtoms(
            Condition condition, int speakerCount, ErrorShape shape) {
        List<String> references = labels("reference-private-speaker-", speakerCount);
        List<String> hypotheses = labels("hypothesis-private-speaker-", speakerCount);
        List<ScoringAtom> atoms = new ArrayList<>();
        long cursor = 0L;

        if (shape == ErrorShape.MIXED && speakerCount == 1) {
            atoms.add(atom(cursor, cursor += 1_000L, List.of(references.get(0)), List.of()));
            atoms.add(
                    atom(
                            cursor,
                            cursor += 1_000L,
                            List.of(references.get(0)),
                            List.of(hypotheses.get(0), "hypothesis-private-speaker-extra")));
        } else {
            for (int index = 0; index < speakerCount; index++) {
                String reference = references.get(index);
                if (shape == ErrorShape.MIXED) {
                    atoms.add(
                            atom(
                                    cursor,
                                    cursor += 1_000L,
                                    List.of(reference),
                                    List.of(hypotheses.get(index))));
                    atoms.add(
                            atom(
                                    cursor,
                                    cursor += 1_000L,
                                    List.of(reference),
                                    List.of(hypotheses.get((index + 1) % speakerCount))));
                    continue;
                }
                List<String> activeHypotheses;
                if (shape == ErrorShape.MISS_ONLY && index == speakerCount - 1) {
                    activeHypotheses = List.of();
                } else {
                    int hypothesisIndex =
                            shape == ErrorShape.PERFECT_PERMUTED
                                    ? (index + 1) % speakerCount
                                    : index;
                    List<String> mutable = new ArrayList<>(List.of(hypotheses.get(hypothesisIndex)));
                    if (shape == ErrorShape.FALSE_ALARM_ONLY && index == 0) {
                        mutable.add("hypothesis-private-speaker-extra");
                    }
                    activeHypotheses = List.copyOf(mutable);
                }
                atoms.add(
                        atom(
                                cursor,
                                cursor += 1_000L,
                                List.of(reference),
                                activeHypotheses));
            }
        }

        if (condition == Condition.OVERLAP) {
            List<String> overlapHypotheses = new ArrayList<>(hypotheses);
            if (shape == ErrorShape.PERFECT_PERMUTED) {
                Collections.rotate(overlapHypotheses, 1);
            }
            atoms.add(atom(cursor, cursor + 1_000L, references, overlapHypotheses));
        }
        return List.copyOf(atoms);
    }

    private static CaseInput validCase(String id) {
        return new CaseInput(
                id,
                LanguageSlice.RU,
                Condition.CLEAN,
                false,
                false,
                List.of(atom(0L, 1L, List.of("r"), List.of("h"))));
    }

    private static CaseInput reviewCase(
            String id, long durationMicros, boolean required, boolean flagged) {
        return new CaseInput(
                id,
                LanguageSlice.RU,
                Condition.CLEAN,
                required,
                flagged,
                List.of(atom(0L, durationMicros, List.of("r"), List.of("h"))));
    }

    private static CaseInput copy(CaseInput input, String id, List<ScoringAtom> atoms) {
        return new CaseInput(
                id,
                input.languageSlice(),
                input.condition(),
                input.reviewRequired(),
                input.reviewFlagged(),
                atoms);
    }

    private static CaseInput withAtom(CaseInput input, ScoringAtom atom) {
        return copy(input, input.caseId(), List.of(atom));
    }

    private static ScoringAtom atom(
            long startMicros,
            long endMicros,
            List<String> references,
            List<String> hypotheses) {
        return new ScoringAtom(startMicros, endMicros, references, hypotheses);
    }

    private static List<String> labels(String prefix, int count) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(prefix + String.format("%02d", index));
        }
        return List.copyOf(result);
    }

    private static long countLanguage(ScoringRequest request, LanguageSlice language) {
        return request.cases().stream().filter(input -> input.languageSlice() == language).count();
    }

    private static long countCondition(ScoringRequest request, Condition condition) {
        return request.cases().stream().filter(input -> input.condition() == condition).count();
    }

    private static int uniqueReferences(CaseInput input) {
        List<String> result = new ArrayList<>();
        for (ScoringAtom atom : input.atoms()) {
            for (String reference : atom.referenceSpeakers()) {
                if (!result.contains(reference)) {
                    result.add(reference);
                }
            }
        }
        return result.size();
    }

    private static <E extends Enum<E>> List<String> enumNames(E[] values) {
        List<String> names = new ArrayList<>();
        for (E value : values) {
            names.add(value.name());
        }
        return List.copyOf(names);
    }

    private static void assertAggregate(
            AggregateMetrics metrics,
            int cases,
            String duration,
            String missed,
            String falseAlarm,
            String confusion,
            String reference,
            String microDer,
            String macroDer,
            long referenceSpeakerCases,
            String speakerMacroJer,
            String caseMacroJer,
            long speakerCountAbsoluteError,
            String speakerCountMae,
            int required,
            int flaggedRequired,
            int flagged,
            int falsePositive,
            String requiredDuration,
            String flaggedRequiredDuration,
            String countRecall,
            String durationRecall) {
        equal(cases, metrics.caseCount(), "aggregate cases");
        equal(new BigInteger(duration), metrics.totalDurationMicros(), "aggregate duration");
        assertDer(metrics.der(), missed, falseAlarm, confusion, reference, microDer, macroDer);
        assertJer(metrics.jer(), referenceSpeakerCases, speakerMacroJer, caseMacroJer);
        equal(
                speakerCountAbsoluteError,
                metrics.speakerCount().absoluteErrorTotal(),
                "aggregate speaker-count error");
        equal(
                speakerCountMae,
                metrics.speakerCount().meanAbsoluteError().orElseThrow().canonical(),
                "aggregate speaker-count MAE");
        ReviewFlagMetrics flags = metrics.reviewFlags();
        equal(required, flags.requiredCases(), "aggregate required cases");
        equal(flaggedRequired, flags.flaggedRequiredCases(), "aggregate flagged required");
        equal(flagged, flags.flaggedCases(), "aggregate flagged cases");
        equal(falsePositive, flags.falsePositiveCases(), "aggregate false positives");
        equal(new BigInteger(requiredDuration), flags.requiredDurationMicros(), "aggregate required duration");
        equal(
                new BigInteger(flaggedRequiredDuration),
                flags.flaggedRequiredDurationMicros(),
                "aggregate flagged required duration");
        equal(countRecall, flags.countRecall().orElseThrow().canonical(), "aggregate count recall");
        equal(durationRecall, flags.durationRecall().orElseThrow().canonical(), "aggregate duration recall");
    }

    private static void assertDer(
            DerMetrics metrics,
            String missed,
            String falseAlarm,
            String confusion,
            String reference,
            String micro,
            String macro) {
        equal(new BigInteger(missed), metrics.missedSpeakerMicros(), "DER missed");
        equal(new BigInteger(falseAlarm), metrics.falseAlarmSpeakerMicros(), "DER false alarm");
        equal(new BigInteger(confusion), metrics.confusionSpeakerMicros(), "DER confusion");
        equal(new BigInteger(reference), metrics.referenceSpeakerMicros(), "DER reference");
        equal(micro, metrics.microDer().orElseThrow().canonical(), "DER micro");
        equal(macro, metrics.caseMacroDer().orElseThrow().canonical(), "DER macro");
    }

    private static void assertJer(
            JerMetrics metrics, long referenceSpeakerCases, String speakerMacro, String caseMacro) {
        equal(referenceSpeakerCases, metrics.referenceSpeakerCases(), "JER speaker-case count");
        equal(speakerMacro, metrics.speakerMacroJer().orElseThrow().canonical(), "speaker JER");
        equal(caseMacro, metrics.caseMacroJer().orElseThrow().canonical(), "case JER");
    }

    private static CampaignScore accepted(ScoringRequest request) {
        EvaluationResult result = DiarSyntheticScoringOracle.evaluate(request);
        if (result instanceof EvaluationAccepted accepted) {
            return accepted.score();
        }
        throw new AssertionError("expected accepted, got " + result);
    }

    private static void assertRejected(
            ScoringRequest request, RejectCode expectedCode, int expectedOrdinal) {
        EvaluationResult result = DiarSyntheticScoringOracle.evaluate(request);
        if (!(result instanceof EvaluationRejected rejected)) {
            throw new AssertionError("expected rejection " + expectedCode + ", got " + result);
        }
        equal(expectedCode, rejected.code(), "reject code");
        equal(expectedOrdinal, rejected.caseOrdinal(), "reject ordinal");
        check(!rejected.toString().contains("DIAR-PRIVATE-CASE"), "rejection excludes private case IDs");
        check(!rejected.toString().contains("private-speaker"), "rejection excludes speaker labels");
    }

    private static void assertAtomRejected(
            CaseInput template, ScoringAtom atom, RejectCode expectedCode) {
        assertRejected(
                new ScoringRequest(List.of(withAtom(template, atom))), expectedCode, 0);
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(
                    "expected " + expected.getName() + ", got " + thrown.getClass().getName(), thrown);
        }
        throw new AssertionError("expected " + expected.getName());
    }

    private static boolean isHex(int unit) {
        return (unit >= '0' && unit <= '9') || (unit >= 'a' && unit <= 'f');
    }

    private static void equal(Object expected, Object actual, String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
