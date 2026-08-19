package com.monumentogram.dora.stage0.asr.i1;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Pure Stage 0 mechanics oracle for deterministic ASR score aggregation.
 *
 * <p>The caller owns tokenization, normalization, transcript provenance, timestamp-anchor matching,
 * and every quality threshold. This class accepts both raw and already-normalized token sequences
 * plus already-matched timestamp anchors; it does not inspect audio, run a model, or select any
 * product policy. Its canonical output contains aggregate counts only and deliberately excludes
 * case identifiers and token content.
 */
public final class AsrSyntheticScoringOracle {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CASES = 10_000;
    public static final int MAX_TOKENS_PER_SEQUENCE = 4_096;
    public static final int MAX_TOKEN_UTF16_UNITS = 4_096;
    public static final int MAX_CASE_ID_UTF16_UNITS = 256;
    public static final int MAX_TIMESTAMP_ANCHORS_PER_CASE = 4_096;
    public static final long MAX_ALIGNMENT_CELLS_PER_REQUEST = 25_000_000L;
    public static final long MAX_TIMESTAMP_ANCHORS_PER_REQUEST = 1_000_000L;

    private AsrSyntheticScoringOracle() {}

    public enum LanguageSlice {
        RU,
        EN,
        MIXED_RU_EN
    }

    public enum AcousticSlice {
        QUIET,
        NOISE,
        SPEAKERPHONE
    }

    public enum RejectCode {
        NULL_REQUEST,
        CASES_MISSING,
        CASES_EMPTY,
        CASE_COUNT_OUT_OF_RANGE,
        CASE_MISSING,
        CASE_ID_INVALID,
        DUPLICATE_CASE_ID,
        LANGUAGE_SLICE_MISSING,
        ACOUSTIC_SLICE_MISSING,
        RAW_REFERENCE_MISSING,
        RAW_HYPOTHESIS_MISSING,
        NORMALIZED_REFERENCE_MISSING,
        NORMALIZED_HYPOTHESIS_MISSING,
        TOKEN_COUNT_OUT_OF_RANGE,
        TOKEN_INVALID,
        TIMESTAMP_ANCHORS_MISSING,
        TIMESTAMP_ANCHOR_COUNT_OUT_OF_RANGE,
        TIMESTAMP_ANCHOR_MISSING,
        TIMESTAMP_NEGATIVE,
        ALIGNMENT_WORK_BUDGET_EXCEEDED,
        TIMESTAMP_WORK_BUDGET_EXCEEDED,
        ARITHMETIC_OVERFLOW
    }

    /** A caller-matched reference/hypothesis anchor in integer microseconds. */
    public record TimestampAnchor(long referenceMicros, long hypothesisMicros) {}

    /**
     * One pre-tokenized, pre-matched scoring case. A null field is retained so {@link #evaluate}
     * can reject malformed input with a typed result; non-null collections are defensively copied.
     */
    public record CaseInput(
            String caseId,
            LanguageSlice languageSlice,
            AcousticSlice acousticSlice,
            List<String> rawReferenceTokens,
            List<String> rawHypothesisTokens,
            List<String> normalizedReferenceTokens,
            List<String> normalizedHypothesisTokens,
            List<TimestampAnchor> timestampAnchors) {
        public CaseInput {
            rawReferenceTokens = immutableNullableCopy(rawReferenceTokens);
            rawHypothesisTokens = immutableNullableCopy(rawHypothesisTokens);
            normalizedReferenceTokens = immutableNullableCopy(normalizedReferenceTokens);
            normalizedHypothesisTokens = immutableNullableCopy(normalizedHypothesisTokens);
            timestampAnchors = immutableNullableCopy(timestampAnchors);
        }
    }

    /** An evaluation request whose case order is not semantically significant. */
    public record ScoringRequest(List<CaseInput> cases) {
        public ScoringRequest {
            cases = immutableNullableCopy(cases);
        }
    }

    public sealed interface EvaluationResult permits EvaluationAccepted, EvaluationRejected {}

    public record EvaluationAccepted(CampaignScore score) implements EvaluationResult {
        public EvaluationAccepted {
            Objects.requireNonNull(score, "score");
        }
    }

    /** caseOrdinal is -1 for a request-level rejection. No caller identifier is echoed. */
    public record EvaluationRejected(RejectCode code, int caseOrdinal) implements EvaluationResult {
        public EvaluationRejected {
            Objects.requireNonNull(code, "code");
            if (caseOrdinal < -1) {
                throw new IllegalArgumentException("caseOrdinal");
            }
        }
    }

    /** Exact, reduced, non-negative rational value. */
    public record ExactRational(BigInteger numerator, BigInteger denominator) {
        public ExactRational {
            Objects.requireNonNull(numerator, "numerator");
            Objects.requireNonNull(denominator, "denominator");
            if (numerator.signum() < 0 || denominator.signum() <= 0) {
                throw new IllegalArgumentException("non-negative numerator and positive denominator required");
            }
            BigInteger divisor = numerator.gcd(denominator);
            numerator = numerator.divide(divisor);
            denominator = denominator.divide(divisor);
        }

        public static ExactRational of(long numerator, long denominator) {
            return new ExactRational(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
        }

        public String canonical() {
            return numerator + "/" + denominator;
        }
    }

    public record WerMetrics(
            long referenceTokens,
            long substitutions,
            long deletions,
            long insertions,
            Optional<ExactRational> wordErrorRate) {
        public WerMetrics {
            if (referenceTokens < 0L || substitutions < 0L || deletions < 0L || insertions < 0L) {
                throw new IllegalArgumentException("negative WER count");
            }
            Objects.requireNonNull(wordErrorRate, "wordErrorRate");
            if ((referenceTokens == 0L) == wordErrorRate.isPresent()) {
                throw new IllegalArgumentException("WER denominator presence mismatch");
            }
        }

        public long errors() {
            return Math.addExact(Math.addExact(substitutions, deletions), insertions);
        }
    }

    public record TimestampMetrics(
            long anchorCount,
            BigInteger totalAbsoluteErrorMicros,
            Optional<ExactRational> medianAbsoluteErrorMicros,
            OptionalLong p95NearestRankAbsoluteErrorMicros) {
        public TimestampMetrics {
            if (anchorCount < 0L) {
                throw new IllegalArgumentException("anchorCount");
            }
            Objects.requireNonNull(totalAbsoluteErrorMicros, "totalAbsoluteErrorMicros");
            Objects.requireNonNull(medianAbsoluteErrorMicros, "medianAbsoluteErrorMicros");
            Objects.requireNonNull(
                    p95NearestRankAbsoluteErrorMicros, "p95NearestRankAbsoluteErrorMicros");
            if (totalAbsoluteErrorMicros.signum() < 0
                    || ((anchorCount == 0L) == medianAbsoluteErrorMicros.isPresent())
                    || ((anchorCount == 0L) == p95NearestRankAbsoluteErrorMicros.isPresent())) {
                throw new IllegalArgumentException("timestamp metric invariant");
            }
        }
    }

    public record AggregateMetrics(
            int caseCount,
            WerMetrics raw,
            WerMetrics normalized,
            TimestampMetrics timestamps) {
        public AggregateMetrics {
            if (caseCount < 0) {
                throw new IllegalArgumentException("caseCount");
            }
            Objects.requireNonNull(raw, "raw");
            Objects.requireNonNull(normalized, "normalized");
            Objects.requireNonNull(timestamps, "timestamps");
        }
    }

    public record LanguageMetrics(LanguageSlice slice, AggregateMetrics metrics) {
        public LanguageMetrics {
            Objects.requireNonNull(slice, "slice");
            Objects.requireNonNull(metrics, "metrics");
        }
    }

    public record AcousticMetrics(AcousticSlice slice, AggregateMetrics metrics) {
        public AcousticMetrics {
            Objects.requireNonNull(slice, "slice");
            Objects.requireNonNull(metrics, "metrics");
        }
    }

    /** Aggregate-only result. Its canonical form is stable and contains no input strings. */
    public record CampaignScore(
            int schemaVersion,
            int caseCount,
            AggregateMetrics overall,
            List<LanguageMetrics> byLanguage,
            List<AcousticMetrics> byAcoustic) {
        public CampaignScore {
            if (schemaVersion != SCHEMA_VERSION || caseCount <= 0) {
                throw new IllegalArgumentException("campaign identity");
            }
            Objects.requireNonNull(overall, "overall");
            byLanguage = List.copyOf(byLanguage);
            byAcoustic = List.copyOf(byAcoustic);
            if (overall.caseCount() != caseCount
                    || byLanguage.size() != LanguageSlice.values().length
                    || byAcoustic.size() != AcousticSlice.values().length) {
                throw new IllegalArgumentException("campaign aggregate shape");
            }
        }

        public String canonicalJson() {
            StringBuilder output = new StringBuilder(2_048);
            output.append("{\"schemaVersion\":")
                    .append(schemaVersion)
                    .append(",\"caseCount\":")
                    .append(caseCount)
                    .append(",\"overall\":");
            appendAggregate(output, overall);
            output.append(",\"byLanguage\":[");
            for (int index = 0; index < byLanguage.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                LanguageMetrics item = byLanguage.get(index);
                output.append("{\"slice\":\"").append(item.slice().name()).append("\",\"metrics\":");
                appendAggregate(output, item.metrics());
                output.append('}');
            }
            output.append("],\"byAcoustic\":[");
            for (int index = 0; index < byAcoustic.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                AcousticMetrics item = byAcoustic.get(index);
                output.append("{\"slice\":\"").append(item.slice().name()).append("\",\"metrics\":");
                appendAggregate(output, item.metrics());
                output.append('}');
            }
            return output.append("]}").toString();
        }

        public String canonicalSha256() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of()
                        .formatHex(digest.digest(canonicalJson().getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 unavailable", exception);
            }
        }
    }

    public static EvaluationResult evaluate(ScoringRequest request) {
        if (request == null) {
            return rejected(RejectCode.NULL_REQUEST, -1);
        }
        List<CaseInput> cases = request.cases();
        if (cases == null) {
            return rejected(RejectCode.CASES_MISSING, -1);
        }
        if (cases.isEmpty()) {
            return rejected(RejectCode.CASES_EMPTY, -1);
        }
        if (cases.size() > MAX_CASES) {
            return rejected(RejectCode.CASE_COUNT_OUT_OF_RANGE, -1);
        }

        Set<String> identifiers = new HashSet<>();
        long alignmentCells = 0L;
        long timestampAnchors = 0L;
        for (int ordinal = 0; ordinal < cases.size(); ordinal++) {
            CaseInput input = cases.get(ordinal);
            RejectCode invalid = validateCase(input);
            if (invalid != null) {
                return rejected(invalid, ordinal);
            }
            if (!identifiers.add(input.caseId())) {
                return rejected(RejectCode.DUPLICATE_CASE_ID, ordinal);
            }
            try {
                alignmentCells =
                        Math.addExact(
                                alignmentCells,
                                alignmentCells(
                                        input.rawReferenceTokens(), input.rawHypothesisTokens()));
                alignmentCells =
                        Math.addExact(
                                alignmentCells,
                                alignmentCells(
                                        input.normalizedReferenceTokens(),
                                        input.normalizedHypothesisTokens()));
                timestampAnchors = Math.addExact(timestampAnchors, input.timestampAnchors().size());
            } catch (ArithmeticException ignored) {
                return rejected(RejectCode.ARITHMETIC_OVERFLOW, ordinal);
            }
            if (alignmentCells > MAX_ALIGNMENT_CELLS_PER_REQUEST) {
                return rejected(RejectCode.ALIGNMENT_WORK_BUDGET_EXCEEDED, ordinal);
            }
            if (timestampAnchors > MAX_TIMESTAMP_ANCHORS_PER_REQUEST) {
                return rejected(RejectCode.TIMESTAMP_WORK_BUDGET_EXCEEDED, ordinal);
            }
        }

        AggregateAccumulator overall = new AggregateAccumulator();
        EnumMap<LanguageSlice, AggregateAccumulator> languages =
                new EnumMap<>(LanguageSlice.class);
        for (LanguageSlice slice : LanguageSlice.values()) {
            languages.put(slice, new AggregateAccumulator());
        }
        EnumMap<AcousticSlice, AggregateAccumulator> acoustics =
                new EnumMap<>(AcousticSlice.class);
        for (AcousticSlice slice : AcousticSlice.values()) {
            acoustics.put(slice, new AggregateAccumulator());
        }

        try {
            for (CaseInput input : cases) {
                CaseScore score = scoreCase(input);
                overall.add(score);
                languages.get(input.languageSlice()).add(score);
                acoustics.get(input.acousticSlice()).add(score);
            }
            List<LanguageMetrics> languageResults = new ArrayList<>();
            for (LanguageSlice slice : LanguageSlice.values()) {
                languageResults.add(new LanguageMetrics(slice, languages.get(slice).finish()));
            }
            List<AcousticMetrics> acousticResults = new ArrayList<>();
            for (AcousticSlice slice : AcousticSlice.values()) {
                acousticResults.add(new AcousticMetrics(slice, acoustics.get(slice).finish()));
            }
            return new EvaluationAccepted(
                    new CampaignScore(
                            SCHEMA_VERSION,
                            cases.size(),
                            overall.finish(),
                            languageResults,
                            acousticResults));
        } catch (ArithmeticException ignored) {
            return rejected(RejectCode.ARITHMETIC_OVERFLOW, -1);
        }
    }

    private static RejectCode validateCase(CaseInput input) {
        if (input == null) {
            return RejectCode.CASE_MISSING;
        }
        if (input.caseId() == null
                || input.caseId().isBlank()
                || input.caseId().length() > MAX_CASE_ID_UTF16_UNITS
                || !isWellFormedUtf16(input.caseId())) {
            return RejectCode.CASE_ID_INVALID;
        }
        if (input.languageSlice() == null) {
            return RejectCode.LANGUAGE_SLICE_MISSING;
        }
        if (input.acousticSlice() == null) {
            return RejectCode.ACOUSTIC_SLICE_MISSING;
        }
        RejectCode tokenResult =
                validateTokens(input.rawReferenceTokens(), RejectCode.RAW_REFERENCE_MISSING);
        if (tokenResult != null) {
            return tokenResult;
        }
        tokenResult = validateTokens(input.rawHypothesisTokens(), RejectCode.RAW_HYPOTHESIS_MISSING);
        if (tokenResult != null) {
            return tokenResult;
        }
        tokenResult =
                validateTokens(
                        input.normalizedReferenceTokens(), RejectCode.NORMALIZED_REFERENCE_MISSING);
        if (tokenResult != null) {
            return tokenResult;
        }
        tokenResult =
                validateTokens(
                        input.normalizedHypothesisTokens(), RejectCode.NORMALIZED_HYPOTHESIS_MISSING);
        if (tokenResult != null) {
            return tokenResult;
        }
        if (input.timestampAnchors() == null) {
            return RejectCode.TIMESTAMP_ANCHORS_MISSING;
        }
        if (input.timestampAnchors().size() > MAX_TIMESTAMP_ANCHORS_PER_CASE) {
            return RejectCode.TIMESTAMP_ANCHOR_COUNT_OUT_OF_RANGE;
        }
        for (TimestampAnchor anchor : input.timestampAnchors()) {
            if (anchor == null) {
                return RejectCode.TIMESTAMP_ANCHOR_MISSING;
            }
            if (anchor.referenceMicros() < 0L || anchor.hypothesisMicros() < 0L) {
                return RejectCode.TIMESTAMP_NEGATIVE;
            }
        }
        return null;
    }

    private static RejectCode validateTokens(List<String> tokens, RejectCode missingCode) {
        if (tokens == null) {
            return missingCode;
        }
        if (tokens.size() > MAX_TOKENS_PER_SEQUENCE) {
            return RejectCode.TOKEN_COUNT_OUT_OF_RANGE;
        }
        for (String token : tokens) {
            if (token == null
                    || token.isEmpty()
                    || token.length() > MAX_TOKEN_UTF16_UNITS
                    || !isWellFormedUtf16(token)) {
                return RejectCode.TOKEN_INVALID;
            }
        }
        return null;
    }

    private static boolean isWellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char unit = value.charAt(index);
            if (Character.isHighSurrogate(unit)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(unit)) {
                return false;
            }
        }
        return true;
    }

    private static long alignmentCells(List<String> reference, List<String> hypothesis) {
        return Math.multiplyExact((long) reference.size() + 1L, (long) hypothesis.size() + 1L);
    }

    private static CaseScore scoreCase(CaseInput input) {
        AlignmentCounts raw = align(input.rawReferenceTokens(), input.rawHypothesisTokens());
        AlignmentCounts normalized =
                align(input.normalizedReferenceTokens(), input.normalizedHypothesisTokens());
        List<Long> timestampErrors = new ArrayList<>(input.timestampAnchors().size());
        for (TimestampAnchor anchor : input.timestampAnchors()) {
            long larger = Math.max(anchor.referenceMicros(), anchor.hypothesisMicros());
            long smaller = Math.min(anchor.referenceMicros(), anchor.hypothesisMicros());
            timestampErrors.add(Math.subtractExact(larger, smaller));
        }
        return new CaseScore(raw, normalized, List.copyOf(timestampErrors));
    }

    /**
     * Levenshtein alignment with stable cell-local precedence: diagonal match, diagonal
     * substitution, deletion, then insertion. Equal-cost candidates never replace an earlier one.
     */
    private static AlignmentCounts align(List<String> reference, List<String> hypothesis) {
        AlignmentState[] previous = new AlignmentState[hypothesis.size() + 1];
        previous[0] = AlignmentState.ZERO;
        for (int column = 1; column <= hypothesis.size(); column++) {
            previous[column] = increment(previous[column - 1], EditOperation.INSERTION);
        }

        for (int row = 1; row <= reference.size(); row++) {
            AlignmentState[] current = new AlignmentState[hypothesis.size() + 1];
            current[0] = increment(previous[0], EditOperation.DELETION);
            for (int column = 1; column <= hypothesis.size(); column++) {
                EditOperation diagonalOperation =
                        reference.get(row - 1).equals(hypothesis.get(column - 1))
                                ? EditOperation.MATCH
                                : EditOperation.SUBSTITUTION;
                AlignmentState best = increment(previous[column - 1], diagonalOperation);
                AlignmentState deletion = increment(previous[column], EditOperation.DELETION);
                if (deletion.distance() < best.distance()) {
                    best = deletion;
                }
                AlignmentState insertion = increment(current[column - 1], EditOperation.INSERTION);
                if (insertion.distance() < best.distance()) {
                    best = insertion;
                }
                current[column] = best;
            }
            previous = current;
        }

        AlignmentState result = previous[hypothesis.size()];
        return new AlignmentCounts(
                reference.size(), result.substitutions(), result.deletions(), result.insertions());
    }

    private static AlignmentState increment(AlignmentState state, EditOperation operation) {
        return switch (operation) {
            case MATCH -> state;
            case SUBSTITUTION ->
                    new AlignmentState(
                            Math.addExact(state.distance(), 1),
                            Math.addExact(state.substitutions(), 1L),
                            state.deletions(),
                            state.insertions());
            case DELETION ->
                    new AlignmentState(
                            Math.addExact(state.distance(), 1),
                            state.substitutions(),
                            Math.addExact(state.deletions(), 1L),
                            state.insertions());
            case INSERTION ->
                    new AlignmentState(
                            Math.addExact(state.distance(), 1),
                            state.substitutions(),
                            state.deletions(),
                            Math.addExact(state.insertions(), 1L));
        };
    }

    private static void appendAggregate(StringBuilder output, AggregateMetrics metrics) {
        output.append("{\"caseCount\":").append(metrics.caseCount()).append(",\"raw\":");
        appendWer(output, metrics.raw());
        output.append(",\"normalized\":");
        appendWer(output, metrics.normalized());
        output.append(",\"timestamps\":");
        appendTimestamps(output, metrics.timestamps());
        output.append('}');
    }

    private static void appendWer(StringBuilder output, WerMetrics metrics) {
        output.append("{\"referenceTokens\":")
                .append(metrics.referenceTokens())
                .append(",\"substitutions\":")
                .append(metrics.substitutions())
                .append(",\"deletions\":")
                .append(metrics.deletions())
                .append(",\"insertions\":")
                .append(metrics.insertions())
                .append(",\"wer\":");
        appendOptionalRational(output, metrics.wordErrorRate());
        output.append('}');
    }

    private static void appendTimestamps(StringBuilder output, TimestampMetrics metrics) {
        output.append("{\"anchorCount\":")
                .append(metrics.anchorCount())
                .append(",\"totalAbsoluteErrorMicros\":\"")
                .append(metrics.totalAbsoluteErrorMicros())
                .append("\",\"medianAbsoluteErrorMicros\":");
        appendOptionalRational(output, metrics.medianAbsoluteErrorMicros());
        output.append(",\"p95NearestRankAbsoluteErrorMicros\":");
        if (metrics.p95NearestRankAbsoluteErrorMicros().isPresent()) {
            output.append(metrics.p95NearestRankAbsoluteErrorMicros().getAsLong());
        } else {
            output.append("null");
        }
        output.append('}');
    }

    private static void appendOptionalRational(
            StringBuilder output, Optional<ExactRational> rational) {
        if (rational.isPresent()) {
            output.append('\"').append(rational.orElseThrow().canonical()).append('\"');
        } else {
            output.append("null");
        }
    }

    private static EvaluationRejected rejected(RejectCode code, int ordinal) {
        return new EvaluationRejected(code, ordinal);
    }

    private static <T> List<T> immutableNullableCopy(List<T> values) {
        if (values == null) {
            return null;
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private enum EditOperation {
        MATCH,
        SUBSTITUTION,
        DELETION,
        INSERTION
    }

    private record AlignmentState(int distance, long substitutions, long deletions, long insertions) {
        private static final AlignmentState ZERO = new AlignmentState(0, 0L, 0L, 0L);
    }

    private record AlignmentCounts(
            long referenceTokens, long substitutions, long deletions, long insertions) {}

    private record CaseScore(
            AlignmentCounts raw, AlignmentCounts normalized, List<Long> timestampErrors) {}

    private static final class AggregateAccumulator {
        private int caseCount;
        private long rawReferenceTokens;
        private long rawSubstitutions;
        private long rawDeletions;
        private long rawInsertions;
        private long normalizedReferenceTokens;
        private long normalizedSubstitutions;
        private long normalizedDeletions;
        private long normalizedInsertions;
        private final List<Long> timestampErrors = new ArrayList<>();

        private void add(CaseScore score) {
            caseCount = Math.addExact(caseCount, 1);
            rawReferenceTokens = Math.addExact(rawReferenceTokens, score.raw().referenceTokens());
            rawSubstitutions = Math.addExact(rawSubstitutions, score.raw().substitutions());
            rawDeletions = Math.addExact(rawDeletions, score.raw().deletions());
            rawInsertions = Math.addExact(rawInsertions, score.raw().insertions());
            normalizedReferenceTokens =
                    Math.addExact(normalizedReferenceTokens, score.normalized().referenceTokens());
            normalizedSubstitutions =
                    Math.addExact(normalizedSubstitutions, score.normalized().substitutions());
            normalizedDeletions =
                    Math.addExact(normalizedDeletions, score.normalized().deletions());
            normalizedInsertions =
                    Math.addExact(normalizedInsertions, score.normalized().insertions());
            timestampErrors.addAll(score.timestampErrors());
        }

        private AggregateMetrics finish() {
            return new AggregateMetrics(
                    caseCount,
                    wer(rawReferenceTokens, rawSubstitutions, rawDeletions, rawInsertions),
                    wer(
                            normalizedReferenceTokens,
                            normalizedSubstitutions,
                            normalizedDeletions,
                            normalizedInsertions),
                    timestamps(timestampErrors));
        }
    }

    private static WerMetrics wer(long references, long substitutions, long deletions, long insertions) {
        long errors = Math.addExact(Math.addExact(substitutions, deletions), insertions);
        Optional<ExactRational> ratio =
                references == 0L
                        ? Optional.empty()
                        : Optional.of(ExactRational.of(errors, references));
        return new WerMetrics(references, substitutions, deletions, insertions, ratio);
    }

    private static TimestampMetrics timestamps(List<Long> unsortedErrors) {
        if (unsortedErrors.isEmpty()) {
            return new TimestampMetrics(
                    0L, BigInteger.ZERO, Optional.empty(), OptionalLong.empty());
        }
        List<Long> sorted = new ArrayList<>(unsortedErrors);
        Collections.sort(sorted);
        BigInteger total = BigInteger.ZERO;
        for (long error : sorted) {
            total = total.add(BigInteger.valueOf(error));
        }
        int size = sorted.size();
        ExactRational median;
        if ((size & 1) == 1) {
            median = ExactRational.of(sorted.get(size / 2), 1L);
        } else {
            BigInteger middleSum =
                    BigInteger.valueOf(sorted.get(size / 2 - 1))
                            .add(BigInteger.valueOf(sorted.get(size / 2)));
            median = new ExactRational(middleSum, BigInteger.TWO);
        }
        long rank = (95L * size + 99L) / 100L;
        long p95 = sorted.get(Math.toIntExact(rank - 1L));
        return new TimestampMetrics(
                size, total, Optional.of(median), OptionalLong.of(p95));
    }
}
