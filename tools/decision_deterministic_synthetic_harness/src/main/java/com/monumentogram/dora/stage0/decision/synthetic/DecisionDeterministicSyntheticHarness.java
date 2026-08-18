package com.monumentogram.dora.stage0.decision.synthetic;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * Dependency-free Stage 0 mechanics for generated-text source anchors, metric aggregation, and
 * user-truth protection.
 *
 * <p>This is a synthetic host harness, not a product schema, extractor, benchmark result, or
 * automatic activation path. In particular, it never applies a decision or task candidate.
 */
public final class DecisionDeterministicSyntheticHarness {
    public static final String PROFILE_VERSION = "stage0-v0.1";
    public static final String SOURCE_RANGE_UNIT =
            "UTF8_BYTE_OFFSETS_HALF_OPEN_STAGE0_V0_1";
    public static final String NEEDS_CONFIRMATION = "NEEDS_CONFIRMATION";
    public static final String PLANNED = "PLANNED";

    private static final Pattern OPAQUE_ID = Pattern.compile("[A-Z][A-Z0-9_]{0,95}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Comparator<ValidationIssue> ISSUE_ORDER =
            Comparator.comparing((ValidationIssue issue) -> issue.code().name())
                    .thenComparing(ValidationIssue::entityId)
                    .thenComparingInt(ValidationIssue::sourceRefIndex);

    private DecisionDeterministicSyntheticHarness() {}

    public enum EvaluationState {
        COMPLETE,
        INVALID_INPUT
    }

    public enum Label {
        FINAL_DECISION,
        REVISION_LINK,
        CONFIRMED_TASK
    }

    public enum SourceStatus {
        VALID,
        INVALID
    }

    public enum SupportStatus {
        SUPPORTED_BY_SYNTHETIC_GOLD,
        UNSUPPORTED_BY_SYNTHETIC_GOLD
    }

    public enum CandidateDisposition {
        ADMISSIBLE_FOR_REVIEW,
        REJECT_INVALID_SOURCE,
        REJECT_UNSUPPORTED,
        REJECT_INVALID_SOURCE_AND_UNSUPPORTED
    }

    public enum IssueCode {
        NULL_SOURCE_LIST,
        NULL_GOLD_LIST,
        NULL_PREDICTION_LIST,
        NULL_SOURCE,
        INVALID_SOURCE_ID,
        DUPLICATE_SOURCE_ID,
        NULL_SOURCE_TEXT,
        EMPTY_SOURCE_TEXT,
        INVALID_SOURCE_UNICODE,
        INVALID_SOURCE_DIGEST,
        SOURCE_DIGEST_MISMATCH,
        NULL_GOLD_ITEM,
        NULL_PREDICTION_ITEM,
        INVALID_ITEM_ID,
        DUPLICATE_GOLD_ITEM_ID,
        DUPLICATE_PREDICTION_ITEM_ID,
        MISSING_LABEL,
        MISSING_SOURCE_REFS,
        NULL_SOURCE_REF,
        INVALID_SOURCE_REF_ID,
        UNKNOWN_SOURCE_REF,
        INVALID_SOURCE_RANGE,
        SOURCE_RANGE_NOT_UTF8_BOUNDARY,
        INVALID_EXCERPT_DIGEST,
        EXCERPT_DIGEST_MISMATCH,
        NULL_FIELD_LIST,
        NULL_PROPOSAL_LIST,
        NULL_FIELD_SNAPSHOT,
        NULL_MACHINE_PROPOSAL,
        INVALID_ENTITY_ID,
        MISSING_FIELD_NAME,
        MISSING_FIELD_OWNERSHIP,
        NULL_FIELD_VALUE,
        NULL_PROPOSED_VALUE,
        DUPLICATE_FIELD_SNAPSHOT,
        DUPLICATE_MACHINE_PROPOSAL,
        UNKNOWN_FIELD_TARGET
    }

    public record GeneratedSource(String sourceId, String text, String declaredUtf8Sha256) {}

    /** Offsets are UTF-8 byte offsets, start-inclusive and end-exclusive. */
    public record SourceRange(
            String sourceId,
            long startInclusive,
            long endExclusive,
            String excerptUtf8Sha256) {}

    public record ScoredItem(String itemId, Label label, List<SourceRange> sourceRanges) {
        public ScoredItem {
            sourceRanges =
                    sourceRanges == null
                            ? null
                            : Collections.unmodifiableList(new ArrayList<>(sourceRanges));
        }
    }

    public record ValidationIssue(IssueCode code, String entityId, int sourceRefIndex) {
        public ValidationIssue {
            Objects.requireNonNull(code, "code");
            entityId = entityId == null ? "NULL" : entityId;
        }

        public ValidationIssue(IssueCode code, String entityId) {
            this(code, entityId, -1);
        }
    }

    public record ConfusionCounts(long truePositive, long falsePositive, long falseNegative) {
        public ConfusionCounts {
            if (truePositive < 0 || falsePositive < 0 || falseNegative < 0) {
                throw new IllegalArgumentException("confusion counts must be non-negative");
            }
        }

        public Fraction precision() {
            return Fraction.of(truePositive, Math.addExact(truePositive, falsePositive));
        }

        public Fraction recall() {
            return Fraction.of(truePositive, Math.addExact(truePositive, falseNegative));
        }

        public Fraction f1() {
            long doubledTruePositive = Math.multiplyExact(2L, truePositive);
            return Fraction.of(
                    doubledTruePositive,
                    Math.addExact(
                            doubledTruePositive, Math.addExact(falsePositive, falseNegative)));
        }
    }

    /** Exact rational metric. A zero denominator is deliberately represented as undefined. */
    public record Fraction(BigInteger numerator, BigInteger denominator) {
        public Fraction {
            Objects.requireNonNull(numerator, "numerator");
            Objects.requireNonNull(denominator, "denominator");
            if (numerator.signum() < 0 || denominator.signum() < 0) {
                throw new IllegalArgumentException("fraction values must be non-negative");
            }
            if (denominator.signum() == 0) {
                if (numerator.signum() != 0) {
                    throw new IllegalArgumentException("undefined fraction must be 0/0");
                }
            } else {
                BigInteger divisor = numerator.gcd(denominator);
                numerator = numerator.divide(divisor);
                denominator = denominator.divide(divisor);
            }
        }

        public static Fraction of(long numerator, long denominator) {
            return new Fraction(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
        }

        public static Fraction undefined() {
            return new Fraction(BigInteger.ZERO, BigInteger.ZERO);
        }

        public boolean isDefined() {
            return denominator.signum() != 0;
        }

        public String decimal6() {
            if (!isDefined()) {
                return "NA";
            }
            return new BigDecimal(numerator)
                    .divide(new BigDecimal(denominator), 6, RoundingMode.HALF_UP)
                    .toPlainString();
        }

        public String canonical() {
            if (!isDefined()) {
                return "NA";
            }
            return numerator + "/" + denominator + "@" + decimal6();
        }

        private static Fraction average(List<Fraction> values) {
            Fraction sum = Fraction.of(0, 1);
            int count = 0;
            for (Fraction value : values) {
                if (value != null && value.isDefined()) {
                    sum = add(sum, value);
                    count++;
                }
            }
            if (count == 0) {
                return undefined();
            }
            return new Fraction(sum.numerator, sum.denominator.multiply(BigInteger.valueOf(count)));
        }

        private static Fraction add(Fraction left, Fraction right) {
            if (!left.isDefined() || !right.isDefined()) {
                throw new IllegalArgumentException("cannot add undefined fractions");
            }
            return new Fraction(
                    left.numerator.multiply(right.denominator)
                            .add(right.numerator.multiply(left.denominator)),
                    left.denominator.multiply(right.denominator));
        }
    }

    public record LabelMetrics(
            Label label,
            ConfusionCounts counts,
            Fraction precision,
            Fraction recall,
            Fraction f1) {
        public LabelMetrics {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(counts, "counts");
            Objects.requireNonNull(precision, "precision");
            Objects.requireNonNull(recall, "recall");
            Objects.requireNonNull(f1, "f1");
        }
    }

    public record AggregateMetrics(
            List<LabelMetrics> perLabel,
            ConfusionCounts microCounts,
            Fraction microPrecision,
            Fraction microRecall,
            Fraction microF1,
            Fraction macroPrecision,
            Fraction macroRecall,
            Fraction macroF1,
            Fraction sourceValidity,
            Fraction unsupportedClaimRate) {
        public AggregateMetrics {
            perLabel = List.copyOf(perLabel);
            Objects.requireNonNull(microCounts, "microCounts");
            Objects.requireNonNull(microPrecision, "microPrecision");
            Objects.requireNonNull(microRecall, "microRecall");
            Objects.requireNonNull(microF1, "microF1");
            Objects.requireNonNull(macroPrecision, "macroPrecision");
            Objects.requireNonNull(macroRecall, "macroRecall");
            Objects.requireNonNull(macroF1, "macroF1");
            Objects.requireNonNull(sourceValidity, "sourceValidity");
            Objects.requireNonNull(unsupportedClaimRate, "unsupportedClaimRate");
        }
    }

    public record CandidateResult(
            String itemId,
            Label label,
            SourceStatus sourceStatus,
            SupportStatus supportStatus,
            CandidateDisposition disposition,
            List<ValidationIssue> sourceIssues) {
        public CandidateResult {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(sourceStatus, "sourceStatus");
            Objects.requireNonNull(supportStatus, "supportStatus");
            Objects.requireNonNull(disposition, "disposition");
            sourceIssues = immutableSortedIssues(sourceIssues);
        }
    }

    public record EvaluationResult(
            EvaluationState state,
            AggregateMetrics metrics,
            List<CandidateResult> candidates,
            List<ValidationIssue> issues) {
        public EvaluationResult {
            Objects.requireNonNull(state, "state");
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            issues = immutableSortedIssues(issues == null ? List.of() : issues);
            if (state == EvaluationState.COMPLETE && metrics == null) {
                throw new IllegalArgumentException("complete result requires metrics");
            }
            if (state == EvaluationState.INVALID_INPUT && metrics != null) {
                throw new IllegalArgumentException("invalid result cannot contain metrics");
            }
        }

        public String canonicalOutput() {
            StringBuilder output = new StringBuilder();
            output.append("profile=").append(PROFILE_VERSION).append('\n');
            output.append("rangeUnit=").append(SOURCE_RANGE_UNIT).append('\n');
            output.append("state=").append(state).append('\n');
            output.append("autoApply=false\n");
            if (state == EvaluationState.INVALID_INPUT) {
                for (ValidationIssue issue : issues) {
                    appendIssue(output, "issue", issue);
                }
                return output.toString();
            }
            for (LabelMetrics labelMetric : metrics.perLabel()) {
                output.append("label=")
                        .append(labelMetric.label())
                        .append("|tp=")
                        .append(labelMetric.counts().truePositive())
                        .append("|fp=")
                        .append(labelMetric.counts().falsePositive())
                        .append("|fn=")
                        .append(labelMetric.counts().falseNegative())
                        .append("|p=")
                        .append(labelMetric.precision().canonical())
                        .append("|r=")
                        .append(labelMetric.recall().canonical())
                        .append("|f1=")
                        .append(labelMetric.f1().canonical())
                        .append('\n');
            }
            output.append("micro=")
                    .append(metrics.microCounts().truePositive())
                    .append('/')
                    .append(metrics.microCounts().falsePositive())
                    .append('/')
                    .append(metrics.microCounts().falseNegative())
                    .append("|p=")
                    .append(metrics.microPrecision().canonical())
                    .append("|r=")
                    .append(metrics.microRecall().canonical())
                    .append("|f1=")
                    .append(metrics.microF1().canonical())
                    .append('\n');
            output.append("macro=p=")
                    .append(metrics.macroPrecision().canonical())
                    .append("|r=")
                    .append(metrics.macroRecall().canonical())
                    .append("|f1=")
                    .append(metrics.macroF1().canonical())
                    .append('\n');
            output.append("sourceValidity=")
                    .append(metrics.sourceValidity().canonical())
                    .append('\n');
            output.append("unsupportedClaimRate=")
                    .append(metrics.unsupportedClaimRate().canonical())
                    .append('\n');
            for (CandidateResult candidate : candidates) {
                output.append("candidate=")
                        .append(tagged(candidate.itemId()))
                        .append('|')
                        .append(candidate.label())
                        .append('|')
                        .append(candidate.sourceStatus())
                        .append('|')
                        .append(candidate.supportStatus())
                        .append('|')
                        .append(candidate.disposition())
                        .append('\n');
                for (ValidationIssue issue : candidate.sourceIssues()) {
                    appendIssue(output, "candidateIssue", issue);
                }
            }
            return output.toString();
        }
    }

    public enum FieldName {
        DECISION_VALUE,
        TASK_TITLE,
        TASK_ASSIGNEE,
        TASK_DEADLINE,
        TASK_STATUS
    }

    public enum FieldOwnership {
        USER,
        MACHINE
    }

    public enum ProtectionState {
        COMPLETE,
        INVALID_INPUT
    }

    public enum ProposalDisposition {
        NO_CHANGE,
        PROPOSED_DIFF_REVIEW_REQUIRED,
        USER_TRUTH_PROTECTED,
        ACTIVATION_REVIEW_REQUIRED
    }

    public record FieldSnapshot(
            String entityId, FieldName fieldName, String currentValue, FieldOwnership ownership) {}

    public record MachineProposal(String entityId, FieldName fieldName, String proposedValue) {}

    public record ProtectedProposal(
            String entityId,
            FieldName fieldName,
            FieldOwnership currentOwnership,
            ProposalDisposition disposition) {
        public ProtectedProposal {
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(fieldName, "fieldName");
            Objects.requireNonNull(currentOwnership, "currentOwnership");
            Objects.requireNonNull(disposition, "disposition");
        }
    }

    public record ProtectionResult(
            ProtectionState state,
            List<ProtectedProposal> proposals,
            List<ValidationIssue> issues) {
        public ProtectionResult {
            Objects.requireNonNull(state, "state");
            proposals = proposals == null ? List.of() : List.copyOf(proposals);
            issues = immutableSortedIssues(issues == null ? List.of() : issues);
        }

        public String canonicalOutput() {
            StringBuilder output = new StringBuilder();
            output.append("profile=").append(PROFILE_VERSION).append('\n');
            output.append("state=").append(state).append('\n');
            output.append("stateMutation=false\n");
            for (ValidationIssue issue : issues) {
                appendIssue(output, "issue", issue);
            }
            for (ProtectedProposal proposal : proposals) {
                output.append("proposal=")
                        .append(tagged(proposal.entityId()))
                        .append('|')
                        .append(proposal.fieldName())
                        .append('|')
                        .append(proposal.currentOwnership())
                        .append('|')
                        .append(proposal.disposition())
                        .append('\n');
            }
            return output.toString();
        }
    }

    /**
     * Scores exact opaque item identities and validates every generated-text source anchor.
     * Candidate source failures are measured and rejected; malformed sources or gold make the
     * entire input invalid.
     */
    public static EvaluationResult evaluate(
            List<GeneratedSource> sources,
            List<ScoredItem> goldItems,
            List<ScoredItem> predictedItems) {
        List<ValidationIssue> structuralIssues = new ArrayList<>();
        if (sources == null) {
            structuralIssues.add(new ValidationIssue(IssueCode.NULL_SOURCE_LIST, "BATCH"));
        }
        if (goldItems == null) {
            structuralIssues.add(new ValidationIssue(IssueCode.NULL_GOLD_LIST, "BATCH"));
        }
        if (predictedItems == null) {
            structuralIssues.add(new ValidationIssue(IssueCode.NULL_PREDICTION_LIST, "BATCH"));
        }
        if (!structuralIssues.isEmpty()) {
            return invalidEvaluation(structuralIssues);
        }

        Map<String, SourceBytes> sourceIndex = validateSources(sources, structuralIssues);
        Map<String, ScoredItem> goldIndex =
                indexItems(goldItems, true, sourceIndex, structuralIssues);
        Map<String, ScoredItem> predictionIndex =
                indexItems(predictedItems, false, sourceIndex, structuralIssues);
        if (!structuralIssues.isEmpty()) {
            return invalidEvaluation(structuralIssues);
        }

        for (ScoredItem gold : goldIndex.values()) {
            List<ValidationIssue> goldSourceIssues = validateRanges(gold, sourceIndex);
            structuralIssues.addAll(goldSourceIssues);
        }
        if (!structuralIssues.isEmpty()) {
            return invalidEvaluation(structuralIssues);
        }

        List<CandidateResult> candidates = new ArrayList<>();
        long validSourceCount = 0;
        long unsupportedCount = 0;
        for (ScoredItem prediction : predictionIndex.values()) {
            List<ValidationIssue> sourceIssues = validateRanges(prediction, sourceIndex);
            SourceStatus sourceStatus =
                    sourceIssues.isEmpty() ? SourceStatus.VALID : SourceStatus.INVALID;
            ScoredItem matchingGold = goldIndex.get(prediction.itemId());
            SupportStatus supportStatus =
                    matchingGold != null && matchingGold.label() == prediction.label()
                            ? SupportStatus.SUPPORTED_BY_SYNTHETIC_GOLD
                            : SupportStatus.UNSUPPORTED_BY_SYNTHETIC_GOLD;
            if (sourceStatus == SourceStatus.VALID) {
                validSourceCount++;
            }
            if (supportStatus == SupportStatus.UNSUPPORTED_BY_SYNTHETIC_GOLD) {
                unsupportedCount++;
            }
            candidates.add(
                    new CandidateResult(
                            prediction.itemId(),
                            prediction.label(),
                            sourceStatus,
                            supportStatus,
                            disposition(sourceStatus, supportStatus),
                            sourceIssues));
        }
        candidates.sort(
                Comparator.comparing(CandidateResult::itemId)
                        .thenComparing(candidate -> candidate.label().name()));

        List<LabelMetrics> labelMetrics = new ArrayList<>();
        long microTp = 0;
        long microFp = 0;
        long microFn = 0;
        List<Fraction> precisions = new ArrayList<>();
        List<Fraction> recalls = new ArrayList<>();
        List<Fraction> f1s = new ArrayList<>();
        for (Label label : Label.values()) {
            Set<String> goldIds = idsForLabel(goldIndex, label);
            Set<String> predictedIds = idsForLabel(predictionIndex, label);
            long tp = predictedIds.stream().filter(goldIds::contains).count();
            long fp = Math.subtractExact(predictedIds.size(), tp);
            long fn = Math.subtractExact(goldIds.size(), tp);
            ConfusionCounts counts = new ConfusionCounts(tp, fp, fn);
            LabelMetrics metric =
                    new LabelMetrics(
                            label,
                            counts,
                            counts.precision(),
                            counts.recall(),
                            counts.f1());
            labelMetrics.add(metric);
            microTp = Math.addExact(microTp, tp);
            microFp = Math.addExact(microFp, fp);
            microFn = Math.addExact(microFn, fn);
            precisions.add(metric.precision());
            recalls.add(metric.recall());
            f1s.add(metric.f1());
        }
        ConfusionCounts microCounts = new ConfusionCounts(microTp, microFp, microFn);
        long predictionCount = predictionIndex.size();
        AggregateMetrics aggregate =
                new AggregateMetrics(
                        labelMetrics,
                        microCounts,
                        microCounts.precision(),
                        microCounts.recall(),
                        microCounts.f1(),
                        Fraction.average(precisions),
                        Fraction.average(recalls),
                        Fraction.average(f1s),
                        Fraction.of(validSourceCount, predictionCount),
                        Fraction.of(unsupportedCount, predictionCount));
        return new EvaluationResult(
                EvaluationState.COMPLETE, aggregate, candidates, List.of());
    }

    /**
     * Classifies machine reprocessing as a diff only. The returned value never contains a mutated
     * current snapshot.
     */
    public static ProtectionResult protectUserTruth(
            List<FieldSnapshot> currentFields, List<MachineProposal> machineProposals) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (currentFields == null) {
            issues.add(new ValidationIssue(IssueCode.NULL_FIELD_LIST, "BATCH"));
        }
        if (machineProposals == null) {
            issues.add(new ValidationIssue(IssueCode.NULL_PROPOSAL_LIST, "BATCH"));
        }
        if (!issues.isEmpty()) {
            return invalidProtection(issues);
        }

        Map<FieldKey, FieldSnapshot> fields = new HashMap<>();
        for (int index = 0; index < currentFields.size(); index++) {
            FieldSnapshot field = currentFields.get(index);
            String fallbackId = indexed("FIELD", index);
            if (field == null) {
                issues.add(new ValidationIssue(IssueCode.NULL_FIELD_SNAPSHOT, fallbackId));
                continue;
            }
            String entityId = validId(field.entityId()) ? field.entityId() : fallbackId;
            if (!validId(field.entityId())) {
                issues.add(new ValidationIssue(IssueCode.INVALID_ENTITY_ID, entityId));
            }
            if (field.fieldName() == null) {
                issues.add(new ValidationIssue(IssueCode.MISSING_FIELD_NAME, entityId));
            }
            if (field.ownership() == null) {
                issues.add(new ValidationIssue(IssueCode.MISSING_FIELD_OWNERSHIP, entityId));
            }
            if (field.currentValue() == null) {
                issues.add(new ValidationIssue(IssueCode.NULL_FIELD_VALUE, entityId));
            }
            if (validId(field.entityId()) && field.fieldName() != null) {
                FieldKey key = new FieldKey(field.entityId(), field.fieldName());
                if (fields.putIfAbsent(key, field) != null) {
                    issues.add(
                            new ValidationIssue(
                                    IssueCode.DUPLICATE_FIELD_SNAPSHOT,
                                    field.entityId() + ":" + field.fieldName()));
                }
            }
        }

        Map<FieldKey, MachineProposal> proposals = new HashMap<>();
        for (int index = 0; index < machineProposals.size(); index++) {
            MachineProposal proposal = machineProposals.get(index);
            String fallbackId = indexed("PROPOSAL", index);
            if (proposal == null) {
                issues.add(new ValidationIssue(IssueCode.NULL_MACHINE_PROPOSAL, fallbackId));
                continue;
            }
            String entityId = validId(proposal.entityId()) ? proposal.entityId() : fallbackId;
            if (!validId(proposal.entityId())) {
                issues.add(new ValidationIssue(IssueCode.INVALID_ENTITY_ID, entityId));
            }
            if (proposal.fieldName() == null) {
                issues.add(new ValidationIssue(IssueCode.MISSING_FIELD_NAME, entityId));
            }
            if (proposal.proposedValue() == null) {
                issues.add(new ValidationIssue(IssueCode.NULL_PROPOSED_VALUE, entityId));
            }
            if (validId(proposal.entityId()) && proposal.fieldName() != null) {
                FieldKey key = new FieldKey(proposal.entityId(), proposal.fieldName());
                if (proposals.putIfAbsent(key, proposal) != null) {
                    issues.add(
                            new ValidationIssue(
                                    IssueCode.DUPLICATE_MACHINE_PROPOSAL,
                                    proposal.entityId() + ":" + proposal.fieldName()));
                } else if (!fields.containsKey(key)) {
                    issues.add(
                            new ValidationIssue(
                                    IssueCode.UNKNOWN_FIELD_TARGET,
                                    proposal.entityId() + ":" + proposal.fieldName()));
                }
            }
        }
        if (!issues.isEmpty()) {
            return invalidProtection(issues);
        }

        List<ProtectedProposal> results = new ArrayList<>();
        TreeMap<FieldKey, MachineProposal> sorted = new TreeMap<>(proposals);
        for (Map.Entry<FieldKey, MachineProposal> entry : sorted.entrySet()) {
            FieldSnapshot current = fields.get(entry.getKey());
            MachineProposal proposed = entry.getValue();
            ProposalDisposition disposition;
            if (current.currentValue().equals(proposed.proposedValue())) {
                disposition = ProposalDisposition.NO_CHANGE;
            } else if (current.ownership() == FieldOwnership.USER) {
                disposition = ProposalDisposition.USER_TRUTH_PROTECTED;
            } else if (current.fieldName() == FieldName.TASK_STATUS
                    && NEEDS_CONFIRMATION.equals(current.currentValue())
                    && PLANNED.equals(proposed.proposedValue())) {
                disposition = ProposalDisposition.ACTIVATION_REVIEW_REQUIRED;
            } else {
                disposition = ProposalDisposition.PROPOSED_DIFF_REVIEW_REQUIRED;
            }
            results.add(
                    new ProtectedProposal(
                            current.entityId(),
                            current.fieldName(),
                            current.ownership(),
                            disposition));
        }
        return new ProtectionResult(ProtectionState.COMPLETE, results, List.of());
    }

    public static String sha256Utf8(String value) {
        Objects.requireNonNull(value, "value");
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, SourceBytes> validateSources(
            List<GeneratedSource> sources, List<ValidationIssue> issues) {
        Map<String, SourceBytes> index = new HashMap<>();
        for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
            GeneratedSource source = sources.get(sourceIndex);
            String fallbackId = indexed("SOURCE", sourceIndex);
            if (source == null) {
                issues.add(new ValidationIssue(IssueCode.NULL_SOURCE, fallbackId));
                continue;
            }
            String entityId = validId(source.sourceId()) ? source.sourceId() : fallbackId;
            if (!validId(source.sourceId())) {
                issues.add(new ValidationIssue(IssueCode.INVALID_SOURCE_ID, entityId));
            }
            if (source.text() == null) {
                issues.add(new ValidationIssue(IssueCode.NULL_SOURCE_TEXT, entityId));
            } else if (source.text().isEmpty()) {
                issues.add(new ValidationIssue(IssueCode.EMPTY_SOURCE_TEXT, entityId));
            } else if (!hasWellFormedUtf16(source.text())) {
                issues.add(new ValidationIssue(IssueCode.INVALID_SOURCE_UNICODE, entityId));
            }
            if (!validDigest(source.declaredUtf8Sha256())) {
                issues.add(new ValidationIssue(IssueCode.INVALID_SOURCE_DIGEST, entityId));
            }
            if (validId(source.sourceId()) && index.containsKey(source.sourceId())) {
                issues.add(new ValidationIssue(IssueCode.DUPLICATE_SOURCE_ID, source.sourceId()));
            }
            if (validId(source.sourceId())
                    && source.text() != null
                    && !source.text().isEmpty()
                    && hasWellFormedUtf16(source.text())
                    && validDigest(source.declaredUtf8Sha256())
                    && !index.containsKey(source.sourceId())) {
                byte[] bytes = source.text().getBytes(StandardCharsets.UTF_8);
                if (!sha256(bytes).equals(source.declaredUtf8Sha256())) {
                    issues.add(
                            new ValidationIssue(
                                    IssueCode.SOURCE_DIGEST_MISMATCH, source.sourceId()));
                } else {
                    index.put(source.sourceId(), new SourceBytes(bytes));
                }
            }
        }
        return index;
    }

    private static Map<String, ScoredItem> indexItems(
            List<ScoredItem> items,
            boolean gold,
            Map<String, SourceBytes> sourceIndex,
            List<ValidationIssue> issues) {
        Map<String, ScoredItem> index = new LinkedHashMap<>();
        IssueCode nullCode = gold ? IssueCode.NULL_GOLD_ITEM : IssueCode.NULL_PREDICTION_ITEM;
        IssueCode duplicateCode =
                gold
                        ? IssueCode.DUPLICATE_GOLD_ITEM_ID
                        : IssueCode.DUPLICATE_PREDICTION_ITEM_ID;
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            ScoredItem item = items.get(itemIndex);
            String fallbackId = indexed(gold ? "GOLD" : "PREDICTION", itemIndex);
            if (item == null) {
                issues.add(new ValidationIssue(nullCode, fallbackId));
                continue;
            }
            String entityId = validId(item.itemId()) ? item.itemId() : fallbackId;
            if (!validId(item.itemId())) {
                issues.add(new ValidationIssue(IssueCode.INVALID_ITEM_ID, entityId));
            }
            if (item.label() == null) {
                issues.add(new ValidationIssue(IssueCode.MISSING_LABEL, entityId));
            }
            if (validId(item.itemId()) && index.putIfAbsent(item.itemId(), item) != null) {
                issues.add(new ValidationIssue(duplicateCode, item.itemId()));
            }
            if (gold && validId(item.itemId()) && item.label() != null) {
                // Gold source failures invalidate the oracle input; validation runs after indexing.
                validateRangeContainer(item, sourceIndex, issues);
            }
        }
        return index;
    }

    private static void validateRangeContainer(
            ScoredItem item,
            Map<String, SourceBytes> sourceIndex,
            List<ValidationIssue> issues) {
        if (item.sourceRanges() == null || item.sourceRanges().isEmpty()) {
            issues.add(new ValidationIssue(IssueCode.MISSING_SOURCE_REFS, item.itemId()));
        }
    }

    private static List<ValidationIssue> validateRanges(
            ScoredItem item, Map<String, SourceBytes> sourceIndex) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (item.sourceRanges() == null || item.sourceRanges().isEmpty()) {
            issues.add(new ValidationIssue(IssueCode.MISSING_SOURCE_REFS, item.itemId()));
            return immutableSortedIssues(issues);
        }
        for (int refIndex = 0; refIndex < item.sourceRanges().size(); refIndex++) {
            SourceRange range = item.sourceRanges().get(refIndex);
            if (range == null) {
                issues.add(
                        new ValidationIssue(
                                IssueCode.NULL_SOURCE_REF, item.itemId(), refIndex));
                continue;
            }
            if (!validId(range.sourceId())) {
                issues.add(
                        new ValidationIssue(
                                IssueCode.INVALID_SOURCE_REF_ID, item.itemId(), refIndex));
                continue;
            }
            SourceBytes source = sourceIndex.get(range.sourceId());
            if (source == null) {
                issues.add(
                        new ValidationIssue(
                                IssueCode.UNKNOWN_SOURCE_REF, item.itemId(), refIndex));
                continue;
            }
            if (range.startInclusive() < 0
                    || range.startInclusive() >= range.endExclusive()
                    || range.endExclusive() > source.bytes().length
                    || range.endExclusive() > Integer.MAX_VALUE) {
                issues.add(
                        new ValidationIssue(
                                IssueCode.INVALID_SOURCE_RANGE, item.itemId(), refIndex));
                continue;
            }
            int start = (int) range.startInclusive();
            int end = (int) range.endExclusive();
            if (!isUtf8Boundary(source.bytes(), start) || !isUtf8Boundary(source.bytes(), end)) {
                issues.add(
                        new ValidationIssue(
                                IssueCode.SOURCE_RANGE_NOT_UTF8_BOUNDARY,
                                item.itemId(),
                                refIndex));
                continue;
            }
            if (!validDigest(range.excerptUtf8Sha256())) {
                issues.add(
                        new ValidationIssue(
                                IssueCode.INVALID_EXCERPT_DIGEST, item.itemId(), refIndex));
                continue;
            }
            byte[] excerpt = java.util.Arrays.copyOfRange(source.bytes(), start, end);
            if (!sha256(excerpt).equals(range.excerptUtf8Sha256())) {
                issues.add(
                        new ValidationIssue(
                                IssueCode.EXCERPT_DIGEST_MISMATCH,
                                item.itemId(),
                                refIndex));
            }
        }
        return immutableSortedIssues(issues);
    }

    private static CandidateDisposition disposition(
            SourceStatus sourceStatus, SupportStatus supportStatus) {
        boolean invalidSource = sourceStatus == SourceStatus.INVALID;
        boolean unsupported = supportStatus == SupportStatus.UNSUPPORTED_BY_SYNTHETIC_GOLD;
        if (invalidSource && unsupported) {
            return CandidateDisposition.REJECT_INVALID_SOURCE_AND_UNSUPPORTED;
        }
        if (invalidSource) {
            return CandidateDisposition.REJECT_INVALID_SOURCE;
        }
        if (unsupported) {
            return CandidateDisposition.REJECT_UNSUPPORTED;
        }
        return CandidateDisposition.ADMISSIBLE_FOR_REVIEW;
    }

    private static Set<String> idsForLabel(Map<String, ScoredItem> index, Label label) {
        Set<String> ids = new HashSet<>();
        for (ScoredItem item : index.values()) {
            if (item.label() == label) {
                ids.add(item.itemId());
            }
        }
        return ids;
    }

    private static EvaluationResult invalidEvaluation(List<ValidationIssue> issues) {
        return new EvaluationResult(
                EvaluationState.INVALID_INPUT, null, List.of(), issues);
    }

    private static ProtectionResult invalidProtection(List<ValidationIssue> issues) {
        return new ProtectionResult(ProtectionState.INVALID_INPUT, List.of(), issues);
    }

    private static List<ValidationIssue> immutableSortedIssues(List<ValidationIssue> issues) {
        ArrayList<ValidationIssue> sorted = new ArrayList<>(issues);
        sorted.sort(ISSUE_ORDER);
        ArrayList<ValidationIssue> deduplicated = new ArrayList<>();
        ValidationIssue previous = null;
        for (ValidationIssue issue : sorted) {
            if (!issue.equals(previous)) {
                deduplicated.add(issue);
                previous = issue;
            }
        }
        return List.copyOf(deduplicated);
    }

    private static boolean validId(String value) {
        return value != null && OPAQUE_ID.matcher(value).matches();
    }

    private static boolean validDigest(String value) {
        return value != null && SHA_256.matcher(value).matches();
    }

    private static boolean isUtf8Boundary(byte[] bytes, int offset) {
        return offset == 0 || offset == bytes.length || (bytes[offset] & 0xC0) != 0x80;
    }

    private static boolean hasWellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Java 17 must provide SHA-256", impossible);
        }
    }

    private static void appendIssue(
            StringBuilder output, String prefix, ValidationIssue issue) {
        output.append(prefix)
                .append('=')
                .append(issue.code())
                .append('|')
                .append(tagged(issue.entityId()))
                .append("|ref=")
                .append(issue.sourceRefIndex())
                .append('\n');
    }

    private static String tagged(String value) {
        return "S" + value.length() + ":" + value;
    }

    private static String indexed(String prefix, int index) {
        return prefix + "_AT_" + String.format(java.util.Locale.ROOT, "%04d", index);
    }

    private record SourceBytes(byte[] bytes) {
        private SourceBytes {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    private record FieldKey(String entityId, FieldName fieldName)
            implements Comparable<FieldKey> {
        @Override
        public int compareTo(FieldKey other) {
            int byEntity = entityId.compareTo(other.entityId);
            if (byEntity != 0) {
                return byEntity;
            }
            return fieldName.name().compareTo(other.fieldName.name());
        }
    }
}
