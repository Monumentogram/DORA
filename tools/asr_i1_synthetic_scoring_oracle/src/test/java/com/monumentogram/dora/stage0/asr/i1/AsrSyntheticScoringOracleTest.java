package com.monumentogram.dora.stage0.asr.i1;

import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.AcousticMetrics;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.AcousticSlice;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.AggregateMetrics;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.CampaignScore;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.CaseInput;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.EvaluationAccepted;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.EvaluationRejected;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.EvaluationResult;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.ExactRational;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.LanguageMetrics;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.LanguageSlice;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.RejectCode;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.ScoringRequest;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.TimestampAnchor;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.TimestampMetrics;
import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.WerMetrics;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class AsrSyntheticScoringOracleTest {
    private static final String PASS_MARKER = "POC_ASR_I1_SYNTHETIC_SCORING_ORACLE_TESTS_OK";
    private static final String EXPECTED_CAMPAIGN_SHA256 =
            "d68c443728774c68c7ea5b104121ebcfc9553bdb78677ff4f915a51c4d183d4c";

    private AsrSyntheticScoringOracleTest() {}

    public static void main(String[] ignored) {
        generatedCampaignHasExactMetricsAndGoldenDigest();
        campaignOrderAndRepeatedEvaluationAreInvariant();
        alignmentTieOrderAndRawNormalizedIndependenceAreExact();
        zeroReferenceAndTimestampStatisticsAreExplicit();
        validUnicodeAndExtremeTimestampAreSupported();
        malformedInputsFailClosedWithoutEchoingIdentifiers();
        inputAndOutputCollectionsAreDefensivelyImmutable();
        exactRationalsAndPublicMetricInvariantsAreStrict();
        implementationHasNoMutableStaticState();
        System.out.println(PASS_MARKER);
    }

    private static void generatedCampaignHasExactMetricsAndGoldenDigest() {
        CampaignScore score = accepted(generatedCampaign());
        equal(AsrSyntheticScoringOracle.SCHEMA_VERSION, score.schemaVersion(), "schema version");
        equal(144, score.caseCount(), "generated case count");
        assertAggregate(score.overall(), 144, 576L, 36L, 36L, 36L, 288L, 5_328L);

        equal(3, score.byLanguage().size(), "language aggregate count");
        for (int index = 0; index < LanguageSlice.values().length; index++) {
            LanguageMetrics metrics = score.byLanguage().get(index);
            equal(LanguageSlice.values()[index], metrics.slice(), "language order");
            assertAggregate(metrics.metrics(), 48, 192L, 12L, 12L, 12L, 96L, 1_776L);
        }

        equal(3, score.byAcoustic().size(), "acoustic aggregate count");
        for (int index = 0; index < AcousticSlice.values().length; index++) {
            AcousticMetrics metrics = score.byAcoustic().get(index);
            equal(AcousticSlice.values()[index], metrics.slice(), "acoustic order");
            assertAggregate(metrics.metrics(), 48, 192L, 12L, 12L, 12L, 96L, 1_776L);
        }

        String canonical = score.canonicalJson();
        check(canonical.startsWith("{\"schemaVersion\":1,\"caseCount\":144"), "canonical prefix");
        check(canonical.contains("\"wer\":\"3/16\""), "canonical exact WER");
        check(!canonical.contains("PRIVATE-CASE-"), "case identifiers excluded");
        check(!canonical.contains("SYNTHETIC_CONTENT_SENTINEL"), "token content excluded");
        equal(EXPECTED_CAMPAIGN_SHA256, score.canonicalSha256(), "campaign golden digest");
    }

    private static void campaignOrderAndRepeatedEvaluationAreInvariant() {
        ScoringRequest original = generatedCampaign();
        CampaignScore first = accepted(original);
        CampaignScore second = accepted(original);
        equal(first, second, "repeated typed score");
        equal(first.canonicalSha256(), second.canonicalSha256(), "repeated digest");

        List<CaseInput> reversedCases = new ArrayList<>(original.cases());
        Collections.reverse(reversedCases);
        CampaignScore reversed = accepted(new ScoringRequest(reversedCases));
        equal(first, reversed, "reversed typed score");
        equal(first.canonicalSha256(), reversed.canonicalSha256(), "reversed digest");

        List<CaseInput> rotatedCases = new ArrayList<>(original.cases());
        Collections.rotate(rotatedCases, 37);
        CampaignScore rotated = accepted(new ScoringRequest(rotatedCases));
        equal(first, rotated, "rotated typed score");
        equal(first.canonicalSha256(), rotated.canonicalSha256(), "rotated digest");
    }

    private static void alignmentTieOrderAndRawNormalizedIndependenceAreExact() {
        CaseInput tie =
                new CaseInput(
                        "TIE",
                        LanguageSlice.EN,
                        AcousticSlice.QUIET,
                        List.of("a", "b"),
                        List.of("b", "a"),
                        List.of("same"),
                        List.of("same"),
                        List.of());
        AggregateMetrics tieMetrics = accepted(new ScoringRequest(List.of(tie))).overall();
        assertWer(tieMetrics.raw(), 2L, 2L, 0L, 0L, "1/1");
        assertWer(tieMetrics.normalized(), 1L, 0L, 0L, 0L, "0/1");

        CaseInput independent =
                new CaseInput(
                        "RAW-NORMALIZED",
                        LanguageSlice.EN,
                        AcousticSlice.NOISE,
                        List.of("Token"),
                        List.of("token"),
                        List.of("token"),
                        List.of("token"),
                        List.of());
        AggregateMetrics metrics = accepted(new ScoringRequest(List.of(independent))).overall();
        assertWer(metrics.raw(), 1L, 1L, 0L, 0L, "1/1");
        assertWer(metrics.normalized(), 1L, 0L, 0L, 0L, "0/1");
    }

    private static void zeroReferenceAndTimestampStatisticsAreExplicit() {
        CaseInput zeroReference =
                new CaseInput(
                        "ZERO-REFERENCE",
                        LanguageSlice.RU,
                        AcousticSlice.SPEAKERPHONE,
                        List.of(),
                        List.of("one", "two"),
                        List.of(),
                        List.of(),
                        List.of(new TimestampAnchor(1L, 2L), new TimestampAnchor(5L, 3L)));
        AggregateMetrics metrics = accepted(new ScoringRequest(List.of(zeroReference))).overall();
        assertWerNa(metrics.raw(), 0L, 0L, 0L, 2L);
        assertWerNa(metrics.normalized(), 0L, 0L, 0L, 0L);
        TimestampMetrics timestamps = metrics.timestamps();
        equal(2L, timestamps.anchorCount(), "two anchors");
        equal(BigInteger.valueOf(3L), timestamps.totalAbsoluteErrorMicros(), "timestamp total");
        equal("3/2", timestamps.medianAbsoluteErrorMicros().orElseThrow().canonical(), "median");
        equal(2L, timestamps.p95NearestRankAbsoluteErrorMicros().orElseThrow(), "p95");

        CaseInput noAnchors = validCase("NO-ANCHORS");
        TimestampMetrics empty = accepted(new ScoringRequest(List.of(noAnchors))).overall().timestamps();
        equal(0L, empty.anchorCount(), "zero anchors");
        equal(BigInteger.ZERO, empty.totalAbsoluteErrorMicros(), "zero timestamp total");
        check(empty.medianAbsoluteErrorMicros().isEmpty(), "zero-anchor median NA");
        check(empty.p95NearestRankAbsoluteErrorMicros().isEmpty(), "zero-anchor p95 NA");
    }

    private static void validUnicodeAndExtremeTimestampAreSupported() {
        String cyrillic = "\u0434\u0430";
        String supplementary = "\uD83D\uDE80";
        CaseInput unicode =
                new CaseInput(
                        "UNICODE",
                        LanguageSlice.MIXED_RU_EN,
                        AcousticSlice.QUIET,
                        List.of(cyrillic, supplementary),
                        List.of(cyrillic, supplementary),
                        List.of(cyrillic, supplementary),
                        List.of(cyrillic, supplementary),
                        List.of(new TimestampAnchor(0L, Long.MAX_VALUE)));
        AggregateMetrics metrics = accepted(new ScoringRequest(List.of(unicode))).overall();
        assertWer(metrics.raw(), 2L, 0L, 0L, 0L, "0/1");
        TimestampMetrics timestamps = metrics.timestamps();
        equal(
                BigInteger.valueOf(Long.MAX_VALUE),
                timestamps.totalAbsoluteErrorMicros(),
                "extreme timestamp total");
        equal(
                Long.MAX_VALUE + "/1",
                timestamps.medianAbsoluteErrorMicros().orElseThrow().canonical(),
                "extreme median");
        equal(
                Long.MAX_VALUE,
                timestamps.p95NearestRankAbsoluteErrorMicros().orElseThrow(),
                "extreme p95");
    }

    private static void malformedInputsFailClosedWithoutEchoingIdentifiers() {
        assertRejected(null, RejectCode.NULL_REQUEST, -1);
        assertRejected(new ScoringRequest(null), RejectCode.CASES_MISSING, -1);
        assertRejected(new ScoringRequest(List.of()), RejectCode.CASES_EMPTY, -1);

        List<CaseInput> tooManyCases =
                Collections.nCopies(AsrSyntheticScoringOracle.MAX_CASES + 1, validCase("REUSED"));
        assertRejected(
                new ScoringRequest(tooManyCases), RejectCode.CASE_COUNT_OUT_OF_RANGE, -1);
        assertRejected(
                new ScoringRequest(Collections.singletonList(null)), RejectCode.CASE_MISSING, 0);
        assertRejected(
                new ScoringRequest(List.of(copy(validCase(" "), " "))),
                RejectCode.CASE_ID_INVALID,
                0);
        assertRejected(
                new ScoringRequest(
                        List.of(validCase("DUPLICATE"), validCase("DUPLICATE"))),
                RejectCode.DUPLICATE_CASE_ID,
                1);

        CaseInput valid = validCase("FIELDS");
        assertRejected(
                new ScoringRequest(
                        List.of(
                                new CaseInput(
                                        valid.caseId(),
                                        null,
                                        valid.acousticSlice(),
                                        valid.rawReferenceTokens(),
                                        valid.rawHypothesisTokens(),
                                        valid.normalizedReferenceTokens(),
                                        valid.normalizedHypothesisTokens(),
                                        valid.timestampAnchors()))),
                RejectCode.LANGUAGE_SLICE_MISSING,
                0);
        assertRejected(
                new ScoringRequest(
                        List.of(
                                new CaseInput(
                                        valid.caseId(),
                                        valid.languageSlice(),
                                        null,
                                        valid.rawReferenceTokens(),
                                        valid.rawHypothesisTokens(),
                                        valid.normalizedReferenceTokens(),
                                        valid.normalizedHypothesisTokens(),
                                        valid.timestampAnchors()))),
                RejectCode.ACOUSTIC_SLICE_MISSING,
                0);

        assertMissingTokenList(valid, 0, RejectCode.RAW_REFERENCE_MISSING);
        assertMissingTokenList(valid, 1, RejectCode.RAW_HYPOTHESIS_MISSING);
        assertMissingTokenList(valid, 2, RejectCode.NORMALIZED_REFERENCE_MISSING);
        assertMissingTokenList(valid, 3, RejectCode.NORMALIZED_HYPOTHESIS_MISSING);

        List<String> tooManyTokens =
                Collections.nCopies(AsrSyntheticScoringOracle.MAX_TOKENS_PER_SEQUENCE + 1, "x");
        assertRejected(
                new ScoringRequest(List.of(withRawReference(valid, tooManyTokens))),
                RejectCode.TOKEN_COUNT_OUT_OF_RANGE,
                0);
        assertRejected(
                new ScoringRequest(List.of(withRawReference(valid, List.of("")))),
                RejectCode.TOKEN_INVALID,
                0);
        assertRejected(
                new ScoringRequest(
                        List.of(withRawReference(valid, Arrays.asList("valid", null)))),
                RejectCode.TOKEN_INVALID,
                0);
        assertRejected(
                new ScoringRequest(List.of(withRawReference(valid, List.of("\uD800")))),
                RejectCode.TOKEN_INVALID,
                0);

        CaseInput missingAnchors =
                new CaseInput(
                        valid.caseId(),
                        valid.languageSlice(),
                        valid.acousticSlice(),
                        valid.rawReferenceTokens(),
                        valid.rawHypothesisTokens(),
                        valid.normalizedReferenceTokens(),
                        valid.normalizedHypothesisTokens(),
                        null);
        assertRejected(
                new ScoringRequest(List.of(missingAnchors)),
                RejectCode.TIMESTAMP_ANCHORS_MISSING,
                0);
        List<TimestampAnchor> tooManyAnchors =
                Collections.nCopies(
                        AsrSyntheticScoringOracle.MAX_TIMESTAMP_ANCHORS_PER_CASE + 1,
                        new TimestampAnchor(0L, 0L));
        assertRejected(
                new ScoringRequest(List.of(withAnchors(valid, tooManyAnchors))),
                RejectCode.TIMESTAMP_ANCHOR_COUNT_OUT_OF_RANGE,
                0);
        assertRejected(
                new ScoringRequest(
                        List.of(withAnchors(valid, Arrays.asList(new TimestampAnchor(0L, 0L), null)))),
                RejectCode.TIMESTAMP_ANCHOR_MISSING,
                0);
        assertRejected(
                new ScoringRequest(
                        List.of(withAnchors(valid, List.of(new TimestampAnchor(-1L, 0L))))),
                RejectCode.TIMESTAMP_NEGATIVE,
                0);

        List<String> maximum =
                Collections.nCopies(AsrSyntheticScoringOracle.MAX_TOKENS_PER_SEQUENCE, "x");
        CaseInput overBudget =
                new CaseInput(
                        "OVER-BUDGET",
                        LanguageSlice.EN,
                        AcousticSlice.QUIET,
                        maximum,
                        maximum,
                        maximum,
                        maximum,
                        List.of());
        assertRejected(
                new ScoringRequest(List.of(overBudget)),
                RejectCode.ALIGNMENT_WORK_BUDGET_EXCEEDED,
                0);

        List<TimestampAnchor> maximumAnchors =
                Collections.nCopies(
                        AsrSyntheticScoringOracle.MAX_TIMESTAMP_ANCHORS_PER_CASE,
                        new TimestampAnchor(0L, 0L));
        List<CaseInput> timestampBudgetCases = new ArrayList<>();
        int count =
                Math.toIntExact(
                        AsrSyntheticScoringOracle.MAX_TIMESTAMP_ANCHORS_PER_REQUEST
                                        / AsrSyntheticScoringOracle.MAX_TIMESTAMP_ANCHORS_PER_CASE
                                + 1L);
        for (int index = 0; index < count; index++) {
            timestampBudgetCases.add(withAnchors(validCase("ANCHORS-" + index), maximumAnchors));
        }
        assertRejected(
                new ScoringRequest(timestampBudgetCases),
                RejectCode.TIMESTAMP_WORK_BUDGET_EXCEEDED,
                count - 1);
    }

    private static void inputAndOutputCollectionsAreDefensivelyImmutable() {
        List<String> rawReference = new ArrayList<>(List.of("a", "b"));
        List<String> rawHypothesis = new ArrayList<>(List.of("a", "b"));
        List<String> normalizedReference = new ArrayList<>(List.of("a", "b"));
        List<String> normalizedHypothesis = new ArrayList<>(List.of("a", "b"));
        List<TimestampAnchor> anchors = new ArrayList<>(List.of(new TimestampAnchor(1L, 1L)));
        CaseInput input =
                new CaseInput(
                        "DEFENSIVE",
                        LanguageSlice.EN,
                        AcousticSlice.QUIET,
                        rawReference,
                        rawHypothesis,
                        normalizedReference,
                        normalizedHypothesis,
                        anchors);
        List<CaseInput> cases = new ArrayList<>(List.of(input));
        ScoringRequest request = new ScoringRequest(cases);

        rawReference.clear();
        rawHypothesis.add("mutation");
        normalizedReference.clear();
        normalizedHypothesis.clear();
        anchors.clear();
        cases.clear();

        CampaignScore score = accepted(request);
        assertWer(score.overall().raw(), 2L, 0L, 0L, 0L, "0/1");
        equal(1L, score.overall().timestamps().anchorCount(), "anchor copy");
        assertThrows(UnsupportedOperationException.class, () -> request.cases().clear());
        assertThrows(UnsupportedOperationException.class, () -> input.rawReferenceTokens().clear());
        assertThrows(UnsupportedOperationException.class, () -> input.timestampAnchors().clear());
        assertThrows(UnsupportedOperationException.class, () -> score.byLanguage().clear());
        assertThrows(UnsupportedOperationException.class, () -> score.byAcoustic().clear());
    }

    private static void exactRationalsAndPublicMetricInvariantsAreStrict() {
        equal("3/4", new ExactRational(BigInteger.valueOf(6L), BigInteger.valueOf(8L)).canonical(), "reduce");
        equal("0/1", ExactRational.of(0L, 99L).canonical(), "zero reduce");
        assertThrows(IllegalArgumentException.class, () -> ExactRational.of(-1L, 1L));
        assertThrows(IllegalArgumentException.class, () -> ExactRational.of(1L, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WerMetrics(0L, 0L, 0L, 0L, Optional.of(ExactRational.of(0L, 1L))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WerMetrics(1L, 0L, 0L, 0L, Optional.empty()));
    }

    private static void implementationHasNoMutableStaticState() {
        for (Field field : AsrSyntheticScoringOracle.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                check(Modifier.isFinal(field.getModifiers()), "static field is final: " + field.getName());
            }
        }
    }

    private static ScoringRequest generatedCampaign() {
        List<CaseInput> cases = new ArrayList<>();
        int caseIndex = 0;
        for (LanguageSlice language : LanguageSlice.values()) {
            for (AcousticSlice acoustic : AcousticSlice.values()) {
                for (Outcome outcome : Outcome.values()) {
                    for (int ordinal = 0; ordinal < 4; ordinal++) {
                        List<String> rawReference =
                                List.of(
                                        "SYNTHETIC_CONTENT_SENTINEL_RAW",
                                        language.name(),
                                        acoustic.name(),
                                        "ORDINAL_" + ordinal);
                        List<String> normalizedReference =
                                List.of(
                                        "SYNTHETIC_CONTENT_SENTINEL_NORMALIZED",
                                        language.name(),
                                        acoustic.name(),
                                        "ORDINAL_" + ordinal);
                        long baseError = outcome.ordinal() * 10L + ordinal;
                        long baseTimestamp = caseIndex * 1_000_000L;
                        cases.add(
                                new CaseInput(
                                        "PRIVATE-CASE-" + caseIndex,
                                        language,
                                        acoustic,
                                        rawReference,
                                        mutate(rawReference, outcome),
                                        normalizedReference,
                                        mutate(normalizedReference, outcome),
                                        List.of(
                                                new TimestampAnchor(
                                                        baseTimestamp, baseTimestamp + baseError),
                                                new TimestampAnchor(
                                                        baseTimestamp + 100_000L,
                                                        baseTimestamp + 100_004L + baseError))));
                        caseIndex++;
                    }
                }
            }
        }
        return new ScoringRequest(cases);
    }

    private static List<String> mutate(List<String> reference, Outcome outcome) {
        List<String> result = new ArrayList<>(reference);
        switch (outcome) {
            case EXACT -> {
                return List.copyOf(result);
            }
            case SUBSTITUTION -> result.set(2, "SYNTHETIC_CONTENT_SENTINEL_SUBSTITUTED");
            case DELETION -> result.remove(2);
            case INSERTION -> result.add(2, "SYNTHETIC_CONTENT_SENTINEL_INSERTED");
        }
        return List.copyOf(result);
    }

    private static CaseInput validCase(String id) {
        return new CaseInput(
                id,
                LanguageSlice.EN,
                AcousticSlice.QUIET,
                List.of("a"),
                List.of("a"),
                List.of("a"),
                List.of("a"),
                List.of());
    }

    private static CaseInput copy(CaseInput input, String id) {
        return new CaseInput(
                id,
                input.languageSlice(),
                input.acousticSlice(),
                input.rawReferenceTokens(),
                input.rawHypothesisTokens(),
                input.normalizedReferenceTokens(),
                input.normalizedHypothesisTokens(),
                input.timestampAnchors());
    }

    private static CaseInput withRawReference(CaseInput input, List<String> rawReference) {
        return new CaseInput(
                input.caseId(),
                input.languageSlice(),
                input.acousticSlice(),
                rawReference,
                input.rawHypothesisTokens(),
                input.normalizedReferenceTokens(),
                input.normalizedHypothesisTokens(),
                input.timestampAnchors());
    }

    private static CaseInput withAnchors(CaseInput input, List<TimestampAnchor> anchors) {
        return new CaseInput(
                input.caseId(),
                input.languageSlice(),
                input.acousticSlice(),
                input.rawReferenceTokens(),
                input.rawHypothesisTokens(),
                input.normalizedReferenceTokens(),
                input.normalizedHypothesisTokens(),
                anchors);
    }

    private static void assertMissingTokenList(CaseInput valid, int field, RejectCode expected) {
        List<String> rawReference = field == 0 ? null : valid.rawReferenceTokens();
        List<String> rawHypothesis = field == 1 ? null : valid.rawHypothesisTokens();
        List<String> normalizedReference = field == 2 ? null : valid.normalizedReferenceTokens();
        List<String> normalizedHypothesis = field == 3 ? null : valid.normalizedHypothesisTokens();
        CaseInput malformed =
                new CaseInput(
                        valid.caseId(),
                        valid.languageSlice(),
                        valid.acousticSlice(),
                        rawReference,
                        rawHypothesis,
                        normalizedReference,
                        normalizedHypothesis,
                        valid.timestampAnchors());
        assertRejected(new ScoringRequest(List.of(malformed)), expected, 0);
    }

    private static void assertAggregate(
            AggregateMetrics metrics,
            int cases,
            long references,
            long substitutions,
            long deletions,
            long insertions,
            long anchors,
            long totalTimestampError) {
        equal(cases, metrics.caseCount(), "aggregate cases");
        assertWer(metrics.raw(), references, substitutions, deletions, insertions, "3/16");
        assertWer(metrics.normalized(), references, substitutions, deletions, insertions, "3/16");
        equal(anchors, metrics.timestamps().anchorCount(), "aggregate anchors");
        equal(
                BigInteger.valueOf(totalTimestampError),
                metrics.timestamps().totalAbsoluteErrorMicros(),
                "aggregate timestamp total");
        equal(
                "37/2",
                metrics.timestamps().medianAbsoluteErrorMicros().orElseThrow().canonical(),
                "aggregate median");
        equal(
                36L,
                metrics.timestamps().p95NearestRankAbsoluteErrorMicros().orElseThrow(),
                "aggregate p95");
    }

    private static void assertWer(
            WerMetrics metrics,
            long references,
            long substitutions,
            long deletions,
            long insertions,
            String ratio) {
        equal(references, metrics.referenceTokens(), "reference tokens");
        equal(substitutions, metrics.substitutions(), "substitutions");
        equal(deletions, metrics.deletions(), "deletions");
        equal(insertions, metrics.insertions(), "insertions");
        equal(
                Math.addExact(Math.addExact(substitutions, deletions), insertions),
                metrics.errors(),
                "errors");
        equal(ratio, metrics.wordErrorRate().orElseThrow().canonical(), "WER ratio");
    }

    private static void assertWerNa(
            WerMetrics metrics,
            long references,
            long substitutions,
            long deletions,
            long insertions) {
        equal(references, metrics.referenceTokens(), "NA reference tokens");
        equal(substitutions, metrics.substitutions(), "NA substitutions");
        equal(deletions, metrics.deletions(), "NA deletions");
        equal(insertions, metrics.insertions(), "NA insertions");
        check(metrics.wordErrorRate().isEmpty(), "WER is NA");
    }

    private static CampaignScore accepted(ScoringRequest request) {
        EvaluationResult result = AsrSyntheticScoringOracle.evaluate(request);
        if (result instanceof EvaluationAccepted accepted) {
            return accepted.score();
        }
        throw new AssertionError("expected accepted result, got " + result);
    }

    private static void assertRejected(
            ScoringRequest request, RejectCode expectedCode, int expectedOrdinal) {
        EvaluationResult result = AsrSyntheticScoringOracle.evaluate(request);
        if (!(result instanceof EvaluationRejected rejected)) {
            throw new AssertionError("expected rejected result, got " + result);
        }
        equal(expectedCode, rejected.code(), "rejection code");
        equal(expectedOrdinal, rejected.caseOrdinal(), "rejection ordinal");
        check(!rejected.toString().contains("PRIVATE-CASE-"), "rejection omits private identifier");
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected " + expected.getSimpleName());
        } catch (Throwable throwable) {
            if (!expected.isInstance(throwable)) {
                throw new AssertionError(
                        "expected " + expected.getSimpleName() + ", got " + throwable, throwable);
            }
        }
    }

    private static void equal(Object expected, Object actual, String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private enum Outcome {
        EXACT,
        SUBSTITUTION,
        DELETION,
        INSERTION
    }
}
