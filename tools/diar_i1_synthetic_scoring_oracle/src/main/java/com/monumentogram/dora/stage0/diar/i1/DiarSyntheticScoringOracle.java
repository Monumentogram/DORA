package com.monumentogram.dora.stage0.diar.i1;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure Stage 0 mechanics oracle for deterministic diarization score aggregation.
 *
 * <p>The caller owns collar policy, overlap policy, reference/hypothesis alignment, annotation
 * provenance, and every quality threshold. This class accepts already aligned, disjoint half-open
 * microsecond atoms. It does not inspect audio, run a model, infer identities, persist content, or
 * choose product policy. Canonical output contains aggregate counts only and deliberately excludes
 * case and speaker identifiers.
 */
public final class DiarSyntheticScoringOracle {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CASES = 1_024;
    public static final int MAX_ATOMS_PER_CASE = 4_096;
    public static final int MAX_TOTAL_ATOMS = 100_000;
    public static final int MAX_REFERENCE_SPEAKERS = 6;
    public static final int MAX_HYPOTHESIS_SPEAKERS = 12;
    public static final int MAX_CASE_ID_UTF8_BYTES = 256;
    public static final int MAX_SPEAKER_LABEL_UTF8_BYTES = 256;
    public static final long MAX_MAPPING_WORK_UNITS = 5_000_000L;

    private DiarSyntheticScoringOracle() {}

    public enum LanguageSlice {
        RU,
        EN,
        MIXED_RU_EN
    }

    /** Frozen AUTH-01 condition order. Labels are fixture metadata, not acoustic evidence. */
    public enum Condition {
        CLEAN,
        NOISY,
        REMOTE,
        OVERLAP,
        FAST_TURN,
        RETURNING_SPEAKER,
        SPEAKERPHONE,
        TV_NEGATIVE
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
        CONDITION_MISSING,
        ATOMS_MISSING,
        ATOMS_EMPTY,
        ATOM_COUNT_OUT_OF_RANGE,
        ATOM_MISSING,
        ATOM_RANGE_INVALID,
        ATOM_ORDER_OR_OVERLAP_INVALID,
        REFERENCE_SPEAKERS_MISSING,
        HYPOTHESIS_SPEAKERS_MISSING,
        SPEAKER_LABEL_INVALID,
        DUPLICATE_REFERENCE_SPEAKER_IN_ATOM,
        DUPLICATE_HYPOTHESIS_SPEAKER_IN_ATOM,
        REFERENCE_SPEAKER_COUNT_OUT_OF_RANGE,
        HYPOTHESIS_SPEAKER_COUNT_OUT_OF_RANGE,
        TOTAL_ATOM_BUDGET_EXCEEDED,
        MAPPING_WORK_BUDGET_EXCEEDED,
        ARITHMETIC_OVERFLOW
    }

    /** One caller-aligned half-open interval [startMicros, endMicros). */
    public record ScoringAtom(
            long startMicros,
            long endMicros,
            List<String> referenceSpeakers,
            List<String> hypothesisSpeakers) {
        public ScoringAtom {
            referenceSpeakers = immutableNullableCopy(referenceSpeakers);
            hypothesisSpeakers = immutableNullableCopy(hypothesisSpeakers);
        }
    }

    /**
     * One aligned case. The two review booleans are caller-owned labels used only for aggregate
     * flag-recall mechanics.
     */
    public record CaseInput(
            String caseId,
            LanguageSlice languageSlice,
            Condition condition,
            boolean reviewRequired,
            boolean reviewFlagged,
            List<ScoringAtom> atoms) {
        public CaseInput {
            atoms = immutableNullableCopy(atoms);
        }
    }

    /** Case order is not semantically significant. */
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

    /** caseOrdinal is -1 for a request-level rejection. Caller identifiers are never echoed. */
    public record EvaluationRejected(RejectCode code, int caseOrdinal) implements EvaluationResult {
        public EvaluationRejected {
            Objects.requireNonNull(code, "code");
            if (caseOrdinal < -1) {
                throw new IllegalArgumentException("caseOrdinal");
            }
        }
    }

    /** Exact, reduced, non-negative rational. */
    public record ExactRational(BigInteger numerator, BigInteger denominator)
            implements Comparable<ExactRational> {
        public ExactRational {
            Objects.requireNonNull(numerator, "numerator");
            Objects.requireNonNull(denominator, "denominator");
            if (numerator.signum() < 0 || denominator.signum() <= 0) {
                throw new IllegalArgumentException(
                        "non-negative numerator and positive denominator required");
            }
            BigInteger divisor = numerator.gcd(denominator);
            numerator = numerator.divide(divisor);
            denominator = denominator.divide(divisor);
        }

        public static ExactRational zero() {
            return new ExactRational(BigInteger.ZERO, BigInteger.ONE);
        }

        public static ExactRational one() {
            return new ExactRational(BigInteger.ONE, BigInteger.ONE);
        }

        public static ExactRational of(long numerator, long denominator) {
            return new ExactRational(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
        }

        public static ExactRational of(BigInteger numerator, BigInteger denominator) {
            return new ExactRational(numerator, denominator);
        }

        public ExactRational add(ExactRational other) {
            Objects.requireNonNull(other, "other");
            return new ExactRational(
                    numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }

        public ExactRational divide(long positiveDivisor) {
            if (positiveDivisor <= 0L) {
                throw new IllegalArgumentException("positiveDivisor");
            }
            return new ExactRational(
                    numerator, denominator.multiply(BigInteger.valueOf(positiveDivisor)));
        }

        @Override
        public int compareTo(ExactRational other) {
            Objects.requireNonNull(other, "other");
            return numerator
                    .multiply(other.denominator)
                    .compareTo(other.numerator.multiply(denominator));
        }

        public String canonical() {
            return numerator + "/" + denominator;
        }
    }

    public record DerMetrics(
            BigInteger missedSpeakerMicros,
            BigInteger falseAlarmSpeakerMicros,
            BigInteger confusionSpeakerMicros,
            BigInteger referenceSpeakerMicros,
            Optional<ExactRational> microDer,
            Optional<ExactRational> caseMacroDer) {
        public DerMetrics {
            requireNonNegative(missedSpeakerMicros, "missedSpeakerMicros");
            requireNonNegative(falseAlarmSpeakerMicros, "falseAlarmSpeakerMicros");
            requireNonNegative(confusionSpeakerMicros, "confusionSpeakerMicros");
            requireNonNegative(referenceSpeakerMicros, "referenceSpeakerMicros");
            Objects.requireNonNull(microDer, "microDer");
            Objects.requireNonNull(caseMacroDer, "caseMacroDer");
            if ((referenceSpeakerMicros.signum() == 0) == microDer.isPresent()) {
                throw new IllegalArgumentException("micro DER denominator presence mismatch");
            }
        }

        public BigInteger errorSpeakerMicros() {
            return missedSpeakerMicros.add(falseAlarmSpeakerMicros).add(confusionSpeakerMicros);
        }
    }

    public record JerMetrics(
            long referenceSpeakerCases,
            Optional<ExactRational> speakerMacroJer,
            Optional<ExactRational> caseMacroJer) {
        public JerMetrics {
            if (referenceSpeakerCases < 0L) {
                throw new IllegalArgumentException("referenceSpeakerCases");
            }
            Objects.requireNonNull(speakerMacroJer, "speakerMacroJer");
            Objects.requireNonNull(caseMacroJer, "caseMacroJer");
            if ((referenceSpeakerCases == 0L) == speakerMacroJer.isPresent()) {
                throw new IllegalArgumentException("speaker JER denominator presence mismatch");
            }
        }
    }

    public record SpeakerCountMetrics(
            long absoluteErrorTotal, Optional<ExactRational> meanAbsoluteError) {
        public SpeakerCountMetrics {
            if (absoluteErrorTotal < 0L) {
                throw new IllegalArgumentException("absoluteErrorTotal");
            }
            Objects.requireNonNull(meanAbsoluteError, "meanAbsoluteError");
        }
    }

    public record ReviewFlagMetrics(
            int requiredCases,
            int flaggedRequiredCases,
            int flaggedCases,
            int falsePositiveCases,
            BigInteger requiredDurationMicros,
            BigInteger flaggedRequiredDurationMicros,
            Optional<ExactRational> countRecall,
            Optional<ExactRational> durationRecall) {
        public ReviewFlagMetrics {
            if (requiredCases < 0
                    || flaggedRequiredCases < 0
                    || flaggedRequiredCases > requiredCases
                    || flaggedCases < 0
                    || falsePositiveCases < 0
                    || flaggedRequiredCases + falsePositiveCases != flaggedCases) {
                throw new IllegalArgumentException("review flag count invariant");
            }
            requireNonNegative(requiredDurationMicros, "requiredDurationMicros");
            requireNonNegative(flaggedRequiredDurationMicros, "flaggedRequiredDurationMicros");
            if (flaggedRequiredDurationMicros.compareTo(requiredDurationMicros) > 0) {
                throw new IllegalArgumentException("review duration invariant");
            }
            Objects.requireNonNull(countRecall, "countRecall");
            Objects.requireNonNull(durationRecall, "durationRecall");
            if ((requiredCases == 0) == countRecall.isPresent()
                    || (requiredDurationMicros.signum() == 0) == durationRecall.isPresent()) {
                throw new IllegalArgumentException("review recall denominator presence mismatch");
            }
        }
    }

    public record AggregateMetrics(
            int caseCount,
            BigInteger totalDurationMicros,
            DerMetrics der,
            JerMetrics jer,
            SpeakerCountMetrics speakerCount,
            ReviewFlagMetrics reviewFlags) {
        public AggregateMetrics {
            if (caseCount < 0) {
                throw new IllegalArgumentException("caseCount");
            }
            requireNonNegative(totalDurationMicros, "totalDurationMicros");
            Objects.requireNonNull(der, "der");
            Objects.requireNonNull(jer, "jer");
            Objects.requireNonNull(speakerCount, "speakerCount");
            Objects.requireNonNull(reviewFlags, "reviewFlags");
            if ((caseCount == 0) == speakerCount.meanAbsoluteError().isPresent()
                    || (caseCount == 0) == der.caseMacroDer().isPresent()
                    || (caseCount == 0) == jer.caseMacroJer().isPresent()) {
                throw new IllegalArgumentException("case aggregate denominator presence mismatch");
            }
        }
    }

    public record LanguageMetrics(LanguageSlice slice, AggregateMetrics metrics) {
        public LanguageMetrics {
            Objects.requireNonNull(slice, "slice");
            Objects.requireNonNull(metrics, "metrics");
        }
    }

    public record ConditionMetrics(Condition condition, AggregateMetrics metrics) {
        public ConditionMetrics {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(metrics, "metrics");
        }
    }

    /** Aggregate-only immutable result; canonical JSON contains ASCII and no caller strings. */
    public record CampaignScore(
            int schemaVersion,
            int caseCount,
            AggregateMetrics overall,
            List<LanguageMetrics> byLanguage,
            List<ConditionMetrics> byCondition) {
        public CampaignScore {
            if (schemaVersion != SCHEMA_VERSION || caseCount <= 0) {
                throw new IllegalArgumentException("campaign identity");
            }
            Objects.requireNonNull(overall, "overall");
            byLanguage = List.copyOf(byLanguage);
            byCondition = List.copyOf(byCondition);
            if (overall.caseCount() != caseCount
                    || byLanguage.size() != LanguageSlice.values().length
                    || byCondition.size() != Condition.values().length) {
                throw new IllegalArgumentException("campaign aggregate shape");
            }
        }

        public String canonicalJson() {
            StringBuilder output = new StringBuilder(8_192);
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
                output.append("{\"slice\":\"")
                        .append(item.slice().name())
                        .append("\",\"metrics\":");
                appendAggregate(output, item.metrics());
                output.append('}');
            }
            output.append("],\"byCondition\":[");
            for (int index = 0; index < byCondition.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                ConditionMetrics item = byCondition.get(index);
                output.append("{\"condition\":\"")
                        .append(item.condition().name())
                        .append("\",\"metrics\":");
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
        long totalAtoms = 0L;
        long totalMappingWork = 0L;
        long totalDuration = 0L;
        long totalReferenceSpeakerMicros = 0L;
        long totalHypothesisSpeakerMicros = 0L;
        for (int ordinal = 0; ordinal < cases.size(); ordinal++) {
            CaseInput input = cases.get(ordinal);
            ValidationCheck check = validateCase(input);
            if (check.code() != null) {
                return rejected(check.code(), ordinal);
            }
            if (!identifiers.add(input.caseId())) {
                return rejected(RejectCode.DUPLICATE_CASE_ID, ordinal);
            }
            CaseShape shape = check.shape();
            try {
                totalAtoms = Math.addExact(totalAtoms, shape.atomCount());
                totalMappingWork = Math.addExact(totalMappingWork, shape.mappingWorkUnits());
                totalDuration = Math.addExact(totalDuration, shape.durationMicros());
                totalReferenceSpeakerMicros =
                        Math.addExact(
                                totalReferenceSpeakerMicros, shape.referenceSpeakerMicros());
                totalHypothesisSpeakerMicros =
                        Math.addExact(
                                totalHypothesisSpeakerMicros, shape.hypothesisSpeakerMicros());
            } catch (ArithmeticException ignored) {
                return rejected(RejectCode.ARITHMETIC_OVERFLOW, ordinal);
            }
            if (totalAtoms > MAX_TOTAL_ATOMS) {
                return rejected(RejectCode.TOTAL_ATOM_BUDGET_EXCEEDED, ordinal);
            }
            if (totalMappingWork > MAX_MAPPING_WORK_UNITS) {
                return rejected(RejectCode.MAPPING_WORK_BUDGET_EXCEEDED, ordinal);
            }
        }

        AggregateAccumulator overall = new AggregateAccumulator();
        EnumMap<LanguageSlice, AggregateAccumulator> languages =
                new EnumMap<>(LanguageSlice.class);
        for (LanguageSlice slice : LanguageSlice.values()) {
            languages.put(slice, new AggregateAccumulator());
        }
        EnumMap<Condition, AggregateAccumulator> conditions = new EnumMap<>(Condition.class);
        for (Condition condition : Condition.values()) {
            conditions.put(condition, new AggregateAccumulator());
        }

        try {
            for (CaseInput input : cases) {
                CaseScore score = scoreCase(input);
                overall.add(score);
                languages.get(input.languageSlice()).add(score);
                conditions.get(input.condition()).add(score);
            }
            List<LanguageMetrics> languageResults = new ArrayList<>();
            for (LanguageSlice slice : LanguageSlice.values()) {
                languageResults.add(new LanguageMetrics(slice, languages.get(slice).finish()));
            }
            List<ConditionMetrics> conditionResults = new ArrayList<>();
            for (Condition condition : Condition.values()) {
                conditionResults.add(
                        new ConditionMetrics(condition, conditions.get(condition).finish()));
            }
            return new EvaluationAccepted(
                    new CampaignScore(
                            SCHEMA_VERSION,
                            cases.size(),
                            overall.finish(),
                            languageResults,
                            conditionResults));
        } catch (ArithmeticException ignored) {
            return rejected(RejectCode.ARITHMETIC_OVERFLOW, -1);
        }
    }

    private static ValidationCheck validateCase(CaseInput input) {
        if (input == null) {
            return invalid(RejectCode.CASE_MISSING);
        }
        if (!validBoundedText(input.caseId(), MAX_CASE_ID_UTF8_BYTES)) {
            return invalid(RejectCode.CASE_ID_INVALID);
        }
        if (input.languageSlice() == null) {
            return invalid(RejectCode.LANGUAGE_SLICE_MISSING);
        }
        if (input.condition() == null) {
            return invalid(RejectCode.CONDITION_MISSING);
        }
        if (input.atoms() == null) {
            return invalid(RejectCode.ATOMS_MISSING);
        }
        if (input.atoms().isEmpty()) {
            return invalid(RejectCode.ATOMS_EMPTY);
        }
        if (input.atoms().size() > MAX_ATOMS_PER_CASE) {
            return invalid(RejectCode.ATOM_COUNT_OUT_OF_RANGE);
        }

        Set<String> allReferences = new TreeSet<>();
        Set<String> allHypotheses = new TreeSet<>();
        long previousEnd = -1L;
        long durationMicros = 0L;
        long referenceSpeakerMicros = 0L;
        long hypothesisSpeakerMicros = 0L;
        for (int index = 0; index < input.atoms().size(); index++) {
            ScoringAtom atom = input.atoms().get(index);
            if (atom == null) {
                return invalid(RejectCode.ATOM_MISSING);
            }
            if (atom.startMicros() < 0L || atom.endMicros() <= atom.startMicros()) {
                return invalid(RejectCode.ATOM_RANGE_INVALID);
            }
            if (index > 0 && atom.startMicros() < previousEnd) {
                return invalid(RejectCode.ATOM_ORDER_OR_OVERLAP_INVALID);
            }
            previousEnd = atom.endMicros();
            if (atom.referenceSpeakers() == null) {
                return invalid(RejectCode.REFERENCE_SPEAKERS_MISSING);
            }
            if (atom.hypothesisSpeakers() == null) {
                return invalid(RejectCode.HYPOTHESIS_SPEAKERS_MISSING);
            }
            Set<String> atomReferences = new HashSet<>();
            for (String label : atom.referenceSpeakers()) {
                if (!validBoundedText(label, MAX_SPEAKER_LABEL_UTF8_BYTES)) {
                    return invalid(RejectCode.SPEAKER_LABEL_INVALID);
                }
                if (!atomReferences.add(label)) {
                    return invalid(RejectCode.DUPLICATE_REFERENCE_SPEAKER_IN_ATOM);
                }
                allReferences.add(label);
            }
            Set<String> atomHypotheses = new HashSet<>();
            for (String label : atom.hypothesisSpeakers()) {
                if (!validBoundedText(label, MAX_SPEAKER_LABEL_UTF8_BYTES)) {
                    return invalid(RejectCode.SPEAKER_LABEL_INVALID);
                }
                if (!atomHypotheses.add(label)) {
                    return invalid(RejectCode.DUPLICATE_HYPOTHESIS_SPEAKER_IN_ATOM);
                }
                allHypotheses.add(label);
            }
            try {
                long duration = Math.subtractExact(atom.endMicros(), atom.startMicros());
                durationMicros = Math.addExact(durationMicros, duration);
                referenceSpeakerMicros =
                        Math.addExact(
                                referenceSpeakerMicros,
                                Math.multiplyExact(duration, atom.referenceSpeakers().size()));
                hypothesisSpeakerMicros =
                        Math.addExact(
                                hypothesisSpeakerMicros,
                                Math.multiplyExact(duration, atom.hypothesisSpeakers().size()));
            } catch (ArithmeticException ignored) {
                return invalid(RejectCode.ARITHMETIC_OVERFLOW);
            }
        }
        if (allReferences.isEmpty() || allReferences.size() > MAX_REFERENCE_SPEAKERS) {
            return invalid(RejectCode.REFERENCE_SPEAKER_COUNT_OUT_OF_RANGE);
        }
        if (allHypotheses.size() > MAX_HYPOTHESIS_SPEAKERS) {
            return invalid(RejectCode.HYPOTHESIS_SPEAKER_COUNT_OUT_OF_RANGE);
        }
        try {
            long mappingWork = mappingWorkUnits(allReferences.size(), allHypotheses.size());
            return new ValidationCheck(
                    null,
                    new CaseShape(
                            input.atoms().size(),
                            mappingWork,
                            durationMicros,
                            referenceSpeakerMicros,
                            hypothesisSpeakerMicros));
        } catch (ArithmeticException ignored) {
            return invalid(RejectCode.ARITHMETIC_OVERFLOW);
        }
    }

    private static long mappingWorkUnits(int referenceCount, int hypothesisCount) {
        long derStates = 1L << referenceCount;
        long jerStates = 1L << hypothesisCount;
        long derWork =
                Math.multiplyExact(
                        Math.multiplyExact((long) hypothesisCount + 1L, derStates),
                        (long) referenceCount + 1L);
        long jerWork =
                Math.multiplyExact(
                        Math.multiplyExact((long) referenceCount + 1L, jerStates),
                        (long) hypothesisCount + 1L);
        return Math.addExact(derWork, jerWork);
    }

    private static CaseScore scoreCase(CaseInput input) {
        List<String> references = sortedSpeakers(input.atoms(), true);
        List<String> hypotheses = sortedSpeakers(input.atoms(), false);
        Map<String, Integer> referenceIndexes = indexes(references);
        Map<String, Integer> hypothesisIndexes = indexes(hypotheses);
        long[] referenceDurations = new long[references.size()];
        long[] hypothesisDurations = new long[hypotheses.size()];
        long[][] cooccurrence = new long[hypotheses.size()][references.size()];
        long durationMicros = 0L;
        long missedSpeakerMicros = 0L;
        long falseAlarmSpeakerMicros = 0L;
        long minimumActiveSpeakerMicros = 0L;
        long referenceSpeakerMicros = 0L;

        for (ScoringAtom atom : input.atoms()) {
            long duration = Math.subtractExact(atom.endMicros(), atom.startMicros());
            durationMicros = Math.addExact(durationMicros, duration);
            int referenceActive = atom.referenceSpeakers().size();
            int hypothesisActive = atom.hypothesisSpeakers().size();
            missedSpeakerMicros =
                    Math.addExact(
                            missedSpeakerMicros,
                            Math.multiplyExact(
                                    duration, Math.max(0, referenceActive - hypothesisActive)));
            falseAlarmSpeakerMicros =
                    Math.addExact(
                            falseAlarmSpeakerMicros,
                            Math.multiplyExact(
                                    duration, Math.max(0, hypothesisActive - referenceActive)));
            minimumActiveSpeakerMicros =
                    Math.addExact(
                            minimumActiveSpeakerMicros,
                            Math.multiplyExact(
                                    duration, Math.min(referenceActive, hypothesisActive)));
            referenceSpeakerMicros =
                    Math.addExact(
                            referenceSpeakerMicros,
                            Math.multiplyExact(duration, referenceActive));
            for (String reference : atom.referenceSpeakers()) {
                int referenceIndex = referenceIndexes.get(reference);
                referenceDurations[referenceIndex] =
                        Math.addExact(referenceDurations[referenceIndex], duration);
            }
            for (String hypothesis : atom.hypothesisSpeakers()) {
                int hypothesisIndex = hypothesisIndexes.get(hypothesis);
                hypothesisDurations[hypothesisIndex] =
                        Math.addExact(hypothesisDurations[hypothesisIndex], duration);
                for (String reference : atom.referenceSpeakers()) {
                    int referenceIndex = referenceIndexes.get(reference);
                    cooccurrence[hypothesisIndex][referenceIndex] =
                            Math.addExact(
                                    cooccurrence[hypothesisIndex][referenceIndex], duration);
                }
            }
        }

        DerSolution derSolution = optimalHypothesisToReference(cooccurrence, references, hypotheses);
        long confusionSpeakerMicros =
                Math.subtractExact(minimumActiveSpeakerMicros, derSolution.correctSpeakerMicros());
        long derErrorSpeakerMicros =
                Math.addExact(
                        Math.addExact(missedSpeakerMicros, falseAlarmSpeakerMicros),
                        confusionSpeakerMicros);
        ExactRational caseDer =
                ExactRational.of(derErrorSpeakerMicros, referenceSpeakerMicros);

        JerSolution jerSolution =
                optimalReferenceToHypothesis(
                        cooccurrence,
                        referenceDurations,
                        hypothesisDurations,
                        references,
                        hypotheses);
        List<ExactRational> jerErrors = new ArrayList<>(references.size());
        for (int referenceIndex = 0; referenceIndex < references.size(); referenceIndex++) {
            int hypothesisIndex = jerSolution.hypothesisByReference().get(referenceIndex);
            if (hypothesisIndex < 0) {
                jerErrors.add(ExactRational.one());
            } else {
                long intersection = cooccurrence[hypothesisIndex][referenceIndex];
                long union =
                        Math.subtractExact(
                                Math.addExact(
                                        referenceDurations[referenceIndex],
                                        hypothesisDurations[hypothesisIndex]),
                                intersection);
                jerErrors.add(ExactRational.of(Math.subtractExact(union, intersection), union));
            }
        }
        ExactRational jerSum = ExactRational.zero();
        for (ExactRational error : jerErrors) {
            jerSum = jerSum.add(error);
        }
        ExactRational caseJer = jerSum.divide(references.size());
        long countError = Math.abs((long) references.size() - hypotheses.size());
        return new CaseScore(
                durationMicros,
                missedSpeakerMicros,
                falseAlarmSpeakerMicros,
                confusionSpeakerMicros,
                referenceSpeakerMicros,
                caseDer,
                List.copyOf(jerErrors),
                caseJer,
                countError,
                input.reviewRequired(),
                input.reviewFlagged());
    }

    /**
     * Maximizes H-to-R cooccurrence under a partial injective assignment. Hypothesis and reference
     * labels are sorted with {@link String#compareTo(String)}; equal-score candidates retain the
     * first lexicographic mapped target, with unmatched last.
     */
    private static DerSolution optimalHypothesisToReference(
            long[][] cooccurrence, List<String> references, List<String> hypotheses) {
        DerSolution[][] memo = new DerSolution[hypotheses.size() + 1][1 << references.size()];
        return solveDer(0, 0, cooccurrence, references.size(), hypotheses.size(), memo);
    }

    private static DerSolution solveDer(
            int hypothesisIndex,
            int usedReferenceMask,
            long[][] cooccurrence,
            int referenceCount,
            int hypothesisCount,
            DerSolution[][] memo) {
        if (hypothesisIndex == hypothesisCount) {
            return new DerSolution(0L, List.of());
        }
        DerSolution cached = memo[hypothesisIndex][usedReferenceMask];
        if (cached != null) {
            return cached;
        }
        DerSolution best = null;
        for (int referenceIndex = 0; referenceIndex < referenceCount; referenceIndex++) {
            int bit = 1 << referenceIndex;
            if ((usedReferenceMask & bit) != 0) {
                continue;
            }
            DerSolution suffix =
                    solveDer(
                            hypothesisIndex + 1,
                            usedReferenceMask | bit,
                            cooccurrence,
                            referenceCount,
                            hypothesisCount,
                            memo);
            DerSolution candidate =
                    new DerSolution(
                            Math.addExact(
                                    cooccurrence[hypothesisIndex][referenceIndex],
                                    suffix.correctSpeakerMicros()),
                            prepend(referenceIndex, suffix.referenceByHypothesis()));
            if (best == null
                    || candidate.correctSpeakerMicros() > best.correctSpeakerMicros()) {
                best = candidate;
            }
        }
        DerSolution unmatchedSuffix =
                solveDer(
                        hypothesisIndex + 1,
                        usedReferenceMask,
                        cooccurrence,
                        referenceCount,
                        hypothesisCount,
                        memo);
        DerSolution unmatched =
                new DerSolution(
                        unmatchedSuffix.correctSpeakerMicros(),
                        prepend(-1, unmatchedSuffix.referenceByHypothesis()));
        if (best == null || unmatched.correctSpeakerMicros() > best.correctSpeakerMicros()) {
            best = unmatched;
        }
        memo[hypothesisIndex][usedReferenceMask] = best;
        return best;
    }

    /**
     * Maximizes exact Jaccard similarity under a partial injective R-to-H assignment. Reference and
     * hypothesis labels use the same String.compareTo lexicographic tie rule as DER.
     */
    private static JerSolution optimalReferenceToHypothesis(
            long[][] cooccurrence,
            long[] referenceDurations,
            long[] hypothesisDurations,
            List<String> references,
            List<String> hypotheses) {
        JerSolution[][] memo = new JerSolution[references.size() + 1][1 << hypotheses.size()];
        return solveJer(
                0,
                0,
                cooccurrence,
                referenceDurations,
                hypothesisDurations,
                references.size(),
                hypotheses.size(),
                memo);
    }

    private static JerSolution solveJer(
            int referenceIndex,
            int usedHypothesisMask,
            long[][] cooccurrence,
            long[] referenceDurations,
            long[] hypothesisDurations,
            int referenceCount,
            int hypothesisCount,
            JerSolution[][] memo) {
        if (referenceIndex == referenceCount) {
            return new JerSolution(ExactRational.zero(), List.of());
        }
        JerSolution cached = memo[referenceIndex][usedHypothesisMask];
        if (cached != null) {
            return cached;
        }
        JerSolution best = null;
        for (int hypothesisIndex = 0; hypothesisIndex < hypothesisCount; hypothesisIndex++) {
            int bit = 1 << hypothesisIndex;
            if ((usedHypothesisMask & bit) != 0) {
                continue;
            }
            long intersection = cooccurrence[hypothesisIndex][referenceIndex];
            long union =
                    Math.subtractExact(
                            Math.addExact(
                                    referenceDurations[referenceIndex],
                                    hypothesisDurations[hypothesisIndex]),
                            intersection);
            ExactRational similarity = ExactRational.of(intersection, union);
            JerSolution suffix =
                    solveJer(
                            referenceIndex + 1,
                            usedHypothesisMask | bit,
                            cooccurrence,
                            referenceDurations,
                            hypothesisDurations,
                            referenceCount,
                            hypothesisCount,
                            memo);
            JerSolution candidate =
                    new JerSolution(
                            similarity.add(suffix.similaritySum()),
                            prepend(hypothesisIndex, suffix.hypothesisByReference()));
            if (best == null || candidate.similaritySum().compareTo(best.similaritySum()) > 0) {
                best = candidate;
            }
        }
        JerSolution unmatchedSuffix =
                solveJer(
                        referenceIndex + 1,
                        usedHypothesisMask,
                        cooccurrence,
                        referenceDurations,
                        hypothesisDurations,
                        referenceCount,
                        hypothesisCount,
                        memo);
        JerSolution unmatched =
                new JerSolution(
                        unmatchedSuffix.similaritySum(),
                        prepend(-1, unmatchedSuffix.hypothesisByReference()));
        if (best == null || unmatched.similaritySum().compareTo(best.similaritySum()) > 0) {
            best = unmatched;
        }
        memo[referenceIndex][usedHypothesisMask] = best;
        return best;
    }

    private static List<Integer> prepend(int value, List<Integer> suffix) {
        List<Integer> result = new ArrayList<>(suffix.size() + 1);
        result.add(value);
        result.addAll(suffix);
        return List.copyOf(result);
    }

    private static List<String> sortedSpeakers(List<ScoringAtom> atoms, boolean reference) {
        Set<String> sorted = new TreeSet<>();
        for (ScoringAtom atom : atoms) {
            sorted.addAll(reference ? atom.referenceSpeakers() : atom.hypothesisSpeakers());
        }
        return List.copyOf(sorted);
    }

    private static Map<String, Integer> indexes(List<String> labels) {
        Map<String, Integer> result = new HashMap<>();
        for (int index = 0; index < labels.size(); index++) {
            result.put(labels.get(index), index);
        }
        return Map.copyOf(result);
    }

    private static boolean validBoundedText(String value, int maxUtf8Bytes) {
        return value != null
                && !value.isBlank()
                && isWellFormedUtf16(value)
                && value.getBytes(StandardCharsets.UTF_8).length <= maxUtf8Bytes;
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

    private static void appendAggregate(StringBuilder output, AggregateMetrics metrics) {
        output.append("{\"caseCount\":")
                .append(metrics.caseCount())
                .append(",\"totalDurationMicros\":\"")
                .append(metrics.totalDurationMicros())
                .append("\",\"der\":");
        appendDer(output, metrics.der());
        output.append(",\"jer\":");
        appendJer(output, metrics.jer());
        output.append(",\"speakerCount\":");
        appendSpeakerCount(output, metrics.speakerCount());
        output.append(",\"reviewFlags\":");
        appendReviewFlags(output, metrics.reviewFlags());
        output.append('}');
    }

    private static void appendDer(StringBuilder output, DerMetrics metrics) {
        output.append("{\"missedSpeakerMicros\":\"")
                .append(metrics.missedSpeakerMicros())
                .append("\",\"falseAlarmSpeakerMicros\":\"")
                .append(metrics.falseAlarmSpeakerMicros())
                .append("\",\"confusionSpeakerMicros\":\"")
                .append(metrics.confusionSpeakerMicros())
                .append("\",\"errorSpeakerMicros\":\"")
                .append(metrics.errorSpeakerMicros())
                .append("\",\"referenceSpeakerMicros\":\"")
                .append(metrics.referenceSpeakerMicros())
                .append("\",\"microDer\":");
        appendOptionalRational(output, metrics.microDer());
        output.append(",\"caseMacroDer\":");
        appendOptionalRational(output, metrics.caseMacroDer());
        output.append('}');
    }

    private static void appendJer(StringBuilder output, JerMetrics metrics) {
        output.append("{\"referenceSpeakerCases\":")
                .append(metrics.referenceSpeakerCases())
                .append(",\"speakerMacroJer\":");
        appendOptionalRational(output, metrics.speakerMacroJer());
        output.append(",\"caseMacroJer\":");
        appendOptionalRational(output, metrics.caseMacroJer());
        output.append('}');
    }

    private static void appendSpeakerCount(StringBuilder output, SpeakerCountMetrics metrics) {
        output.append("{\"absoluteErrorTotal\":")
                .append(metrics.absoluteErrorTotal())
                .append(",\"meanAbsoluteError\":");
        appendOptionalRational(output, metrics.meanAbsoluteError());
        output.append('}');
    }

    private static void appendReviewFlags(StringBuilder output, ReviewFlagMetrics metrics) {
        output.append("{\"requiredCases\":")
                .append(metrics.requiredCases())
                .append(",\"flaggedRequiredCases\":")
                .append(metrics.flaggedRequiredCases())
                .append(",\"flaggedCases\":")
                .append(metrics.flaggedCases())
                .append(",\"falsePositiveCases\":")
                .append(metrics.falsePositiveCases())
                .append(",\"requiredDurationMicros\":\"")
                .append(metrics.requiredDurationMicros())
                .append("\",\"flaggedRequiredDurationMicros\":\"")
                .append(metrics.flaggedRequiredDurationMicros())
                .append("\",\"countRecall\":");
        appendOptionalRational(output, metrics.countRecall());
        output.append(",\"durationRecall\":");
        appendOptionalRational(output, metrics.durationRecall());
        output.append('}');
    }

    private static void appendOptionalRational(
            StringBuilder output, Optional<ExactRational> value) {
        if (value.isPresent()) {
            output.append('"').append(value.orElseThrow().canonical()).append('"');
        } else {
            output.append("null");
        }
    }

    private static EvaluationRejected rejected(RejectCode code, int ordinal) {
        return new EvaluationRejected(code, ordinal);
    }

    private static ValidationCheck invalid(RejectCode code) {
        return new ValidationCheck(code, null);
    }

    private static void requireNonNegative(BigInteger value, String label) {
        Objects.requireNonNull(value, label);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(label);
        }
    }

    private static <T> List<T> immutableNullableCopy(List<T> values) {
        if (values == null) {
            return null;
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private record ValidationCheck(RejectCode code, CaseShape shape) {}

    private record CaseShape(
            int atomCount,
            long mappingWorkUnits,
            long durationMicros,
            long referenceSpeakerMicros,
            long hypothesisSpeakerMicros) {}

    private record DerSolution(long correctSpeakerMicros, List<Integer> referenceByHypothesis) {
        private DerSolution {
            referenceByHypothesis = List.copyOf(referenceByHypothesis);
        }
    }

    private record JerSolution(
            ExactRational similaritySum, List<Integer> hypothesisByReference) {
        private JerSolution {
            Objects.requireNonNull(similaritySum, "similaritySum");
            hypothesisByReference = List.copyOf(hypothesisByReference);
        }
    }

    private record CaseScore(
            long durationMicros,
            long missedSpeakerMicros,
            long falseAlarmSpeakerMicros,
            long confusionSpeakerMicros,
            long referenceSpeakerMicros,
            ExactRational caseDer,
            List<ExactRational> jerErrors,
            ExactRational caseJer,
            long speakerCountAbsoluteError,
            boolean reviewRequired,
            boolean reviewFlagged) {
        private CaseScore {
            jerErrors = List.copyOf(jerErrors);
        }
    }

    private static final class AggregateAccumulator {
        private int caseCount;
        private BigInteger totalDurationMicros = BigInteger.ZERO;
        private BigInteger missedSpeakerMicros = BigInteger.ZERO;
        private BigInteger falseAlarmSpeakerMicros = BigInteger.ZERO;
        private BigInteger confusionSpeakerMicros = BigInteger.ZERO;
        private BigInteger referenceSpeakerMicros = BigInteger.ZERO;
        private ExactRational caseDerSum = ExactRational.zero();
        private long referenceSpeakerCases;
        private ExactRational speakerJerSum = ExactRational.zero();
        private ExactRational caseJerSum = ExactRational.zero();
        private long speakerCountAbsoluteError;
        private int requiredCases;
        private int flaggedRequiredCases;
        private int flaggedCases;
        private int falsePositiveCases;
        private BigInteger requiredDurationMicros = BigInteger.ZERO;
        private BigInteger flaggedRequiredDurationMicros = BigInteger.ZERO;

        private void add(CaseScore score) {
            caseCount = Math.addExact(caseCount, 1);
            totalDurationMicros =
                    totalDurationMicros.add(BigInteger.valueOf(score.durationMicros()));
            missedSpeakerMicros =
                    missedSpeakerMicros.add(BigInteger.valueOf(score.missedSpeakerMicros()));
            falseAlarmSpeakerMicros =
                    falseAlarmSpeakerMicros.add(
                            BigInteger.valueOf(score.falseAlarmSpeakerMicros()));
            confusionSpeakerMicros =
                    confusionSpeakerMicros.add(BigInteger.valueOf(score.confusionSpeakerMicros()));
            referenceSpeakerMicros =
                    referenceSpeakerMicros.add(
                            BigInteger.valueOf(score.referenceSpeakerMicros()));
            caseDerSum = caseDerSum.add(score.caseDer());
            referenceSpeakerCases =
                    Math.addExact(referenceSpeakerCases, score.jerErrors().size());
            for (ExactRational jerError : score.jerErrors()) {
                speakerJerSum = speakerJerSum.add(jerError);
            }
            caseJerSum = caseJerSum.add(score.caseJer());
            speakerCountAbsoluteError =
                    Math.addExact(
                            speakerCountAbsoluteError, score.speakerCountAbsoluteError());
            if (score.reviewFlagged()) {
                flaggedCases = Math.addExact(flaggedCases, 1);
            }
            if (score.reviewRequired()) {
                requiredCases = Math.addExact(requiredCases, 1);
                requiredDurationMicros =
                        requiredDurationMicros.add(BigInteger.valueOf(score.durationMicros()));
                if (score.reviewFlagged()) {
                    flaggedRequiredCases = Math.addExact(flaggedRequiredCases, 1);
                    flaggedRequiredDurationMicros =
                            flaggedRequiredDurationMicros.add(
                                    BigInteger.valueOf(score.durationMicros()));
                }
            } else if (score.reviewFlagged()) {
                falsePositiveCases = Math.addExact(falsePositiveCases, 1);
            }
        }

        private AggregateMetrics finish() {
            Optional<ExactRational> microDer =
                    referenceSpeakerMicros.signum() == 0
                            ? Optional.empty()
                            : Optional.of(
                                    ExactRational.of(
                                            missedSpeakerMicros
                                                    .add(falseAlarmSpeakerMicros)
                                                    .add(confusionSpeakerMicros),
                                            referenceSpeakerMicros));
            Optional<ExactRational> caseMacroDer =
                    caseCount == 0
                            ? Optional.empty()
                            : Optional.of(caseDerSum.divide(caseCount));
            Optional<ExactRational> speakerMacroJer =
                    referenceSpeakerCases == 0L
                            ? Optional.empty()
                            : Optional.of(speakerJerSum.divide(referenceSpeakerCases));
            Optional<ExactRational> caseMacroJer =
                    caseCount == 0
                            ? Optional.empty()
                            : Optional.of(caseJerSum.divide(caseCount));
            Optional<ExactRational> speakerCountMae =
                    caseCount == 0
                            ? Optional.empty()
                            : Optional.of(
                                    ExactRational.of(speakerCountAbsoluteError, caseCount));
            Optional<ExactRational> countRecall =
                    requiredCases == 0
                            ? Optional.empty()
                            : Optional.of(
                                    ExactRational.of(flaggedRequiredCases, requiredCases));
            Optional<ExactRational> durationRecall =
                    requiredDurationMicros.signum() == 0
                            ? Optional.empty()
                            : Optional.of(
                                    ExactRational.of(
                                            flaggedRequiredDurationMicros,
                                            requiredDurationMicros));
            return new AggregateMetrics(
                    caseCount,
                    totalDurationMicros,
                    new DerMetrics(
                            missedSpeakerMicros,
                            falseAlarmSpeakerMicros,
                            confusionSpeakerMicros,
                            referenceSpeakerMicros,
                            microDer,
                            caseMacroDer),
                    new JerMetrics(referenceSpeakerCases, speakerMacroJer, caseMacroJer),
                    new SpeakerCountMetrics(speakerCountAbsoluteError, speakerCountMae),
                    new ReviewFlagMetrics(
                            requiredCases,
                            flaggedRequiredCases,
                            flaggedCases,
                            falsePositiveCases,
                            requiredDurationMicros,
                            flaggedRequiredDurationMicros,
                            countRecall,
                            durationRecall));
        }
    }
}
