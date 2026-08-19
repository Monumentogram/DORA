package com.monumentogram.dora.stage0.decision.i3;

import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.AggregateMetrics;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.CandidateDisposition;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.CandidateResult;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.ConfusionCounts;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.EvaluationResult;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.EvaluationState;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.FieldName;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.FieldOwnership;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.FieldSnapshot;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.Fraction;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.GeneratedSource;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.Label;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.LabelMetrics;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.MachineProposal;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.ProposalDisposition;
import com.monumentogram.dora.stage0.decision.synthetic.DecisionDeterministicSyntheticHarness.ProtectedProposal;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic generated-text mechanics campaign over the merged Decision I2 public API.
 *
 * <p>The campaign is a Stage 0 host preflight only. Its 144 generated cases are not governed or
 * adjudicated corpus cases, do not measure an extractor or model, and cannot produce a quality,
 * readiness, PASS, or product-admission claim. Candidate application and state mutation remain
 * impossible through this API.
 */
public final class DecisionSyntheticCampaign {
    public static final String PROFILE_VERSION = "decision-i3-stage0-v0.1";
    public static final String AUTHORIZATION_ID =
            "POC-DECISION-001-I3-SYNTHETIC-METAMORPHIC-CAMPAIGN-AUTH-20260819-01";
    public static final int CASES_PER_LANGUAGE_RELATION = 12;
    public static final int CASE_COUNT =
            LanguageSlice.values().length
                    * RelationSlice.values().length
                    * CASES_PER_LANGUAGE_RELATION;
    public static final int GOVERNED_CORPUS_CASE_COUNT = 0;

    private static final int EXPECTED_VALID_SOURCE_CASES = 96;
    private static final int EXPECTED_INVALID_SOURCE_CASES = 48;
    private static final int EXPECTED_SUPPORTED_CASES = 72;
    private static final int EXPECTED_UNSUPPORTED_CASES = 72;

    private DecisionSyntheticCampaign() {}

    public enum LanguageSlice {
        RU,
        EN,
        MIXED_RU_EN
    }

    public enum RelationSlice {
        FINAL,
        AMEND,
        CANCEL,
        SOURCE_GROUNDING
    }

    public enum OutcomeVariant {
        MATCH_VALID_SOURCE,
        WRONG_ID_VALID_SOURCE,
        WRONG_LABEL_VALID_SOURCE,
        MATCH_INVALID_UTF8_BOUNDARY,
        WRONG_ID_INVALID_UTF8_BOUNDARY
    }

    public record CampaignCase(
            String caseId,
            LanguageSlice language,
            RelationSlice relation,
            OutcomeVariant outcome,
            GeneratedSource source,
            ScoredItem gold,
            ScoredItem prediction,
            List<FieldSnapshot> currentFields,
            List<MachineProposal> machineProposals) {
        public CampaignCase {
            Objects.requireNonNull(caseId, "caseId");
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(relation, "relation");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(gold, "gold");
            Objects.requireNonNull(prediction, "prediction");
            currentFields = List.copyOf(currentFields);
            machineProposals = List.copyOf(machineProposals);
        }
    }

    public record CampaignResult(
            int caseCount,
            Map<LanguageSlice, Integer> languageCounts,
            Map<RelationSlice, Integer> relationCounts,
            Map<OutcomeVariant, Integer> outcomeCounts,
            Map<SourceStatus, Integer> sourceStatusCounts,
            Map<SupportStatus, Integer> supportStatusCounts,
            Map<CandidateDisposition, Integer> candidateDispositionCounts,
            Map<ProposalDisposition, Integer> proposalDispositionCounts,
            EvaluationResult evaluation,
            ProtectionResult protection,
            String evaluationDigest,
            String protectionDigest,
            String canonicalPayload,
            String campaignDigest) {
        public CampaignResult {
            if (caseCount != CASE_COUNT) {
                throw new IllegalArgumentException("campaign case count drifted");
            }
            languageCounts = immutableEnumMap(languageCounts);
            relationCounts = immutableEnumMap(relationCounts);
            outcomeCounts = immutableEnumMap(outcomeCounts);
            sourceStatusCounts = immutableEnumMap(sourceStatusCounts);
            supportStatusCounts = immutableEnumMap(supportStatusCounts);
            candidateDispositionCounts = immutableEnumMap(candidateDispositionCounts);
            proposalDispositionCounts = immutableEnumMap(proposalDispositionCounts);
            Objects.requireNonNull(evaluation, "evaluation");
            Objects.requireNonNull(protection, "protection");
            requireDigest(evaluationDigest, "evaluationDigest");
            requireDigest(protectionDigest, "protectionDigest");
            Objects.requireNonNull(canonicalPayload, "canonicalPayload");
            requireDigest(campaignDigest, "campaignDigest");
            if (!DecisionDeterministicSyntheticHarness.sha256Utf8(canonicalPayload)
                    .equals(campaignDigest)) {
                throw new IllegalArgumentException("campaign digest does not bind payload");
            }
        }

        public String canonicalOutput() {
            return canonicalPayload + "campaignDigest=" + campaignDigest + '\n';
        }
    }

    /** Builds the complete immutable generated case matrix without external input or state. */
    public static List<CampaignCase> generatedCases() {
        List<CampaignCase> cases = new ArrayList<>(CASE_COUNT);
        for (LanguageSlice language : LanguageSlice.values()) {
            for (RelationSlice relation : RelationSlice.values()) {
                for (int ordinal = 0; ordinal < CASES_PER_LANGUAGE_RELATION; ordinal++) {
                    cases.add(buildCase(language, relation, ordinal));
                }
            }
        }
        if (cases.size() != CASE_COUNT) {
            throw new IllegalStateException("generated case count drifted");
        }
        return List.copyOf(cases);
    }

    /**
     * Executes only deterministic java.base mechanics and fails closed on any I2 semantic drift.
     */
    public static CampaignResult run() {
        List<CampaignCase> cases = generatedCases();
        List<GeneratedSource> sources = mapSources(cases);
        List<ScoredItem> gold = mapGold(cases);
        List<ScoredItem> predictions = mapPredictions(cases);

        EvaluationResult evaluation =
                DecisionDeterministicSyntheticHarness.evaluate(sources, gold, predictions);
        require(
                evaluation.state() == EvaluationState.COMPLETE,
                "campaign evaluation failed: " + evaluation.canonicalOutput());
        require(evaluation.issues().isEmpty(), "complete campaign exposed structural issues");

        EvaluationResult repeatedEvaluation =
                DecisionDeterministicSyntheticHarness.evaluate(sources, gold, predictions);
        EvaluationResult reversedEvaluation =
                DecisionDeterministicSyntheticHarness.evaluate(
                        reversed(sources), reversed(gold), reversed(predictions));
        EvaluationResult rotatedEvaluation =
                DecisionDeterministicSyntheticHarness.evaluate(
                        rotated(sources, 17), rotated(gold, 31), rotated(predictions, 47));
        require(evaluation.equals(repeatedEvaluation), "repeat changed typed evaluation");
        require(evaluation.equals(reversedEvaluation), "reverse changed typed evaluation");
        require(evaluation.equals(rotatedEvaluation), "rotation changed typed evaluation");
        require(
                evaluation.canonicalOutput().equals(repeatedEvaluation.canonicalOutput())
                        && evaluation.canonicalOutput()
                                .equals(reversedEvaluation.canonicalOutput())
                        && evaluation.canonicalOutput()
                                .equals(rotatedEvaluation.canonicalOutput()),
                "metamorphic ordering changed canonical evaluation");

        List<FieldSnapshot> currentFields = mapCurrentFields(cases);
        List<MachineProposal> machineProposals = mapMachineProposals(cases);
        ProtectionResult protection =
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        currentFields, machineProposals);
        require(
                protection.state() == ProtectionState.COMPLETE,
                "campaign protection failed: " + protection.canonicalOutput());
        require(protection.issues().isEmpty(), "complete protection exposed issues");

        ProtectionResult repeatedProtection =
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        currentFields, machineProposals);
        ProtectionResult reversedProtection =
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        reversed(currentFields), reversed(machineProposals));
        ProtectionResult rotatedProtection =
                DecisionDeterministicSyntheticHarness.protectUserTruth(
                        rotated(currentFields, 53), rotated(machineProposals, 71));
        require(protection.equals(repeatedProtection), "repeat changed typed protection");
        require(protection.equals(reversedProtection), "reverse changed typed protection");
        require(protection.equals(rotatedProtection), "rotation changed typed protection");
        require(
                protection.canonicalOutput().equals(repeatedProtection.canonicalOutput())
                        && protection.canonicalOutput()
                                .equals(reversedProtection.canonicalOutput())
                        && protection.canonicalOutput()
                                .equals(rotatedProtection.canonicalOutput()),
                "metamorphic ordering changed canonical protection");

        Map<LanguageSlice, Integer> languageCounts = countLanguages(cases);
        Map<RelationSlice, Integer> relationCounts = countRelations(cases);
        Map<OutcomeVariant, Integer> outcomeCounts = countOutcomes(cases);
        Map<SourceStatus, Integer> sourceStatusCounts = countSourceStatuses(evaluation);
        Map<SupportStatus, Integer> supportStatusCounts = countSupportStatuses(evaluation);
        Map<CandidateDisposition, Integer> candidateDispositionCounts =
                countCandidateDispositions(evaluation);
        Map<ProposalDisposition, Integer> proposalDispositionCounts =
                countProposalDispositions(protection);

        verifyMatrixCounts(languageCounts, relationCounts, outcomeCounts);
        verifyEvaluation(evaluation, sourceStatusCounts, supportStatusCounts,
                candidateDispositionCounts);
        verifyProtection(protection, proposalDispositionCounts);
        verifyIndividualCaseRelations(cases);

        String evaluationDigest =
                DecisionDeterministicSyntheticHarness.sha256Utf8(evaluation.canonicalOutput());
        String repeatedEvaluationDigest =
                DecisionDeterministicSyntheticHarness.sha256Utf8(
                        repeatedEvaluation.canonicalOutput());
        require(evaluationDigest.equals(repeatedEvaluationDigest), "evaluation digest drifted");

        String protectionDigest =
                DecisionDeterministicSyntheticHarness.sha256Utf8(protection.canonicalOutput());
        String repeatedProtectionDigest =
                DecisionDeterministicSyntheticHarness.sha256Utf8(
                        repeatedProtection.canonicalOutput());
        require(protectionDigest.equals(repeatedProtectionDigest), "protection digest drifted");

        String canonicalPayload =
                canonicalPayload(
                        languageCounts,
                        relationCounts,
                        outcomeCounts,
                        sourceStatusCounts,
                        supportStatusCounts,
                        candidateDispositionCounts,
                        proposalDispositionCounts,
                        evaluation.metrics(),
                        evaluationDigest,
                        protectionDigest);
        String campaignDigest =
                DecisionDeterministicSyntheticHarness.sha256Utf8(canonicalPayload);
        require(
                campaignDigest.equals(
                        DecisionDeterministicSyntheticHarness.sha256Utf8(canonicalPayload)),
                "campaign digest repeat drifted");

        return new CampaignResult(
                cases.size(),
                languageCounts,
                relationCounts,
                outcomeCounts,
                sourceStatusCounts,
                supportStatusCounts,
                candidateDispositionCounts,
                proposalDispositionCounts,
                evaluation,
                protection,
                evaluationDigest,
                protectionDigest,
                canonicalPayload,
                campaignDigest);
    }

    private static CampaignCase buildCase(
            LanguageSlice language, RelationSlice relation, int ordinal) {
        OutcomeVariant outcome = outcomeFor(ordinal);
        String caseId =
                String.format(
                        Locale.ROOT, "CASE_%s_%s_%02d", language, relation, ordinal);
        String sourceId = "SOURCE_" + caseId;
        String itemId = "ITEM_" + caseId;
        String anchor = anchor(language, relation, ordinal);
        String prefix = prefix(language, caseId);
        String sourceText = prefix + anchor + suffix(language);
        long start = utf8Length(prefix);
        long end = Math.addExact(start, utf8Length(anchor));
        SourceRange validRange =
                new SourceRange(
                        sourceId,
                        start,
                        end,
                        DecisionDeterministicSyntheticHarness.sha256Utf8(anchor));
        SourceRange predictionRange =
                hasInvalidBoundary(outcome)
                        ? new SourceRange(
                                sourceId,
                                Math.addExact(start, 1L),
                                end,
                                validRange.excerptUtf8Sha256())
                        : validRange;

        Label goldLabel = labelFor(relation);
        Label predictedLabel =
                outcome == OutcomeVariant.WRONG_LABEL_VALID_SOURCE
                        ? wrongLabelFor(goldLabel, ordinal)
                        : goldLabel;
        String predictedItemId = hasWrongId(outcome) ? "PRED_" + caseId : itemId;

        GeneratedSource source =
                new GeneratedSource(
                        sourceId,
                        sourceText,
                        DecisionDeterministicSyntheticHarness.sha256Utf8(sourceText));
        ScoredItem gold = new ScoredItem(itemId, goldLabel, List.of(validRange));
        ScoredItem prediction =
                new ScoredItem(predictedItemId, predictedLabel, List.of(predictionRange));

        String decisionEntity = "DECISION_" + caseId;
        String taskEntity = "TASK_" + caseId;
        List<FieldSnapshot> fields =
                List.of(
                        new FieldSnapshot(
                                decisionEntity,
                                FieldName.DECISION_VALUE,
                                "USER_VALUE_" + caseId,
                                FieldOwnership.USER),
                        new FieldSnapshot(
                                taskEntity,
                                FieldName.TASK_STATUS,
                                DecisionDeterministicSyntheticHarness.NEEDS_CONFIRMATION,
                                FieldOwnership.MACHINE),
                        new FieldSnapshot(
                                taskEntity,
                                FieldName.TASK_TITLE,
                                "TITLE_A_" + caseId,
                                FieldOwnership.MACHINE),
                        new FieldSnapshot(
                                taskEntity,
                                FieldName.TASK_ASSIGNEE,
                                "ASSIGNEE_" + caseId,
                                FieldOwnership.USER));
        List<MachineProposal> proposals =
                List.of(
                        new MachineProposal(
                                decisionEntity,
                                FieldName.DECISION_VALUE,
                                "MACHINE_VALUE_" + caseId),
                        new MachineProposal(
                                taskEntity,
                                FieldName.TASK_STATUS,
                                DecisionDeterministicSyntheticHarness.PLANNED),
                        new MachineProposal(
                                taskEntity,
                                FieldName.TASK_TITLE,
                                "TITLE_B_" + caseId),
                        new MachineProposal(
                                taskEntity,
                                FieldName.TASK_ASSIGNEE,
                                "ASSIGNEE_" + caseId));
        return new CampaignCase(
                caseId,
                language,
                relation,
                outcome,
                source,
                gold,
                prediction,
                fields,
                proposals);
    }

    private static OutcomeVariant outcomeFor(int ordinal) {
        return switch (ordinal) {
            case 0, 1, 6, 7 -> OutcomeVariant.MATCH_VALID_SOURCE;
            case 2, 8 -> OutcomeVariant.WRONG_ID_VALID_SOURCE;
            case 3, 9 -> OutcomeVariant.WRONG_LABEL_VALID_SOURCE;
            case 4, 10 -> OutcomeVariant.MATCH_INVALID_UTF8_BOUNDARY;
            case 5, 11 -> OutcomeVariant.WRONG_ID_INVALID_UTF8_BOUNDARY;
            default -> throw new IllegalArgumentException("unsupported ordinal: " + ordinal);
        };
    }

    private static Label labelFor(RelationSlice relation) {
        return switch (relation) {
            case FINAL -> Label.FINAL_DECISION;
            case AMEND, CANCEL -> Label.REVISION_LINK;
            case SOURCE_GROUNDING -> Label.CONFIRMED_TASK;
        };
    }

    private static Label wrongLabelFor(Label goldLabel, int ordinal) {
        return switch (goldLabel) {
            case FINAL_DECISION, CONFIRMED_TASK -> Label.REVISION_LINK;
            case REVISION_LINK ->
                    ordinal == 3 ? Label.FINAL_DECISION : Label.CONFIRMED_TASK;
        };
    }

    private static String prefix(LanguageSlice language, String caseId) {
        return switch (language) {
            case RU -> "\u0421\u0418\u041d\u0422\u0415\u0422\u0418\u0427\u0415\u0421\u041a\u0418\u0419_"
                    + "\u0422\u0415\u041a\u0421\u0422 " + caseId + " ";
            case EN -> "SYNTHETIC_TEXT " + caseId + " ";
            case MIXED_RU_EN -> "SYNTHETIC_\u0422\u0415\u041a\u0421\u0422 " + caseId + " ";
        };
    }

    private static String anchor(
            LanguageSlice language, RelationSlice relation, int ordinal) {
        String relationToken =
                switch (language) {
                    case RU ->
                            switch (relation) {
                                case FINAL -> "\u0444\u0438\u043d\u0430\u043b";
                                case AMEND -> "\u0438\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0435";
                                case CANCEL -> "\u043e\u0442\u043c\u0435\u043d\u0430";
                                case SOURCE_GROUNDING -> "\u0438\u0441\u0442\u043e\u0447\u043d\u0438\u043a";
                            };
                    case EN ->
                            switch (relation) {
                                case FINAL -> "final";
                                case AMEND -> "amend";
                                case CANCEL -> "cancel";
                                case SOURCE_GROUNDING -> "source";
                            };
                    case MIXED_RU_EN ->
                            switch (relation) {
                                case FINAL -> "final_\u0440\u0435\u0448\u0435\u043d\u043e";
                                case AMEND -> "amend_\u0438\u0437\u043c\u0435\u043d\u0435\u043d\u043e";
                                case CANCEL -> "cancel_\u043e\u0442\u043c\u0435\u043d\u0435\u043d\u043e";
                                case SOURCE_GROUNDING -> "source_\u0438\u0441\u0442\u043e\u0447\u043d\u0438\u043a";
                            };
                };
        return "\u00a7" + relationToken + "_" + String.format(Locale.ROOT, "%02d", ordinal);
    }

    private static String suffix(LanguageSlice language) {
        return switch (language) {
            case RU -> " \u0442\u043e\u043b\u044c\u043a\u043e_\u0441\u0438\u043d\u0442\u0435\u0442\u0438\u043a\u0430.";
            case EN -> " generated_only.";
            case MIXED_RU_EN -> " generated_\u0442\u043e\u043b\u044c\u043a\u043e.";
        };
    }

    private static boolean hasWrongId(OutcomeVariant outcome) {
        return outcome == OutcomeVariant.WRONG_ID_VALID_SOURCE
                || outcome == OutcomeVariant.WRONG_ID_INVALID_UTF8_BOUNDARY;
    }

    private static boolean hasInvalidBoundary(OutcomeVariant outcome) {
        return outcome == OutcomeVariant.MATCH_INVALID_UTF8_BOUNDARY
                || outcome == OutcomeVariant.WRONG_ID_INVALID_UTF8_BOUNDARY;
    }

    private static long utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static List<GeneratedSource> mapSources(List<CampaignCase> cases) {
        return cases.stream().map(CampaignCase::source).toList();
    }

    private static List<ScoredItem> mapGold(List<CampaignCase> cases) {
        return cases.stream().map(CampaignCase::gold).toList();
    }

    private static List<ScoredItem> mapPredictions(List<CampaignCase> cases) {
        return cases.stream().map(CampaignCase::prediction).toList();
    }

    private static List<FieldSnapshot> mapCurrentFields(List<CampaignCase> cases) {
        List<FieldSnapshot> fields = new ArrayList<>(cases.size() * 4);
        for (CampaignCase campaignCase : cases) {
            fields.addAll(campaignCase.currentFields());
        }
        return List.copyOf(fields);
    }

    private static List<MachineProposal> mapMachineProposals(List<CampaignCase> cases) {
        List<MachineProposal> proposals = new ArrayList<>(cases.size() * 4);
        for (CampaignCase campaignCase : cases) {
            proposals.addAll(campaignCase.machineProposals());
        }
        return List.copyOf(proposals);
    }

    private static <T> List<T> reversed(List<T> input) {
        List<T> result = new ArrayList<>(input);
        Collections.reverse(result);
        return List.copyOf(result);
    }

    private static <T> List<T> rotated(List<T> input, int distance) {
        List<T> result = new ArrayList<>(input);
        Collections.rotate(result, distance);
        return List.copyOf(result);
    }

    private static Map<LanguageSlice, Integer> countLanguages(List<CampaignCase> cases) {
        EnumMap<LanguageSlice, Integer> counts = zeroCounts(LanguageSlice.class);
        for (CampaignCase campaignCase : cases) {
            increment(counts, campaignCase.language());
        }
        return counts;
    }

    private static Map<RelationSlice, Integer> countRelations(List<CampaignCase> cases) {
        EnumMap<RelationSlice, Integer> counts = zeroCounts(RelationSlice.class);
        for (CampaignCase campaignCase : cases) {
            increment(counts, campaignCase.relation());
        }
        return counts;
    }

    private static Map<OutcomeVariant, Integer> countOutcomes(List<CampaignCase> cases) {
        EnumMap<OutcomeVariant, Integer> counts = zeroCounts(OutcomeVariant.class);
        for (CampaignCase campaignCase : cases) {
            increment(counts, campaignCase.outcome());
        }
        return counts;
    }

    private static Map<SourceStatus, Integer> countSourceStatuses(EvaluationResult evaluation) {
        EnumMap<SourceStatus, Integer> counts = zeroCounts(SourceStatus.class);
        for (CandidateResult candidate : evaluation.candidates()) {
            increment(counts, candidate.sourceStatus());
        }
        return counts;
    }

    private static Map<SupportStatus, Integer> countSupportStatuses(
            EvaluationResult evaluation) {
        EnumMap<SupportStatus, Integer> counts = zeroCounts(SupportStatus.class);
        for (CandidateResult candidate : evaluation.candidates()) {
            increment(counts, candidate.supportStatus());
        }
        return counts;
    }

    private static Map<CandidateDisposition, Integer> countCandidateDispositions(
            EvaluationResult evaluation) {
        EnumMap<CandidateDisposition, Integer> counts = zeroCounts(CandidateDisposition.class);
        for (CandidateResult candidate : evaluation.candidates()) {
            increment(counts, candidate.disposition());
        }
        return counts;
    }

    private static Map<ProposalDisposition, Integer> countProposalDispositions(
            ProtectionResult protection) {
        EnumMap<ProposalDisposition, Integer> counts = zeroCounts(ProposalDisposition.class);
        for (ProtectedProposal proposal : protection.proposals()) {
            increment(counts, proposal.disposition());
        }
        return counts;
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

    private static void verifyMatrixCounts(
            Map<LanguageSlice, Integer> languageCounts,
            Map<RelationSlice, Integer> relationCounts,
            Map<OutcomeVariant, Integer> outcomeCounts) {
        for (LanguageSlice language : LanguageSlice.values()) {
            require(languageCounts.get(language) == 48, "language slice count drifted: " + language);
        }
        for (RelationSlice relation : RelationSlice.values()) {
            require(relationCounts.get(relation) == 36, "relation slice count drifted: " + relation);
        }
        require(
                outcomeCounts.get(OutcomeVariant.MATCH_VALID_SOURCE) == 48,
                "valid match count drifted");
        require(
                outcomeCounts.get(OutcomeVariant.WRONG_ID_VALID_SOURCE) == 24,
                "valid wrong-id count drifted");
        require(
                outcomeCounts.get(OutcomeVariant.WRONG_LABEL_VALID_SOURCE) == 24,
                "valid wrong-label count drifted");
        require(
                outcomeCounts.get(OutcomeVariant.MATCH_INVALID_UTF8_BOUNDARY) == 24,
                "invalid-boundary match count drifted");
        require(
                outcomeCounts.get(OutcomeVariant.WRONG_ID_INVALID_UTF8_BOUNDARY) == 24,
                "invalid-boundary wrong-id count drifted");
    }

    private static void verifyEvaluation(
            EvaluationResult evaluation,
            Map<SourceStatus, Integer> sourceStatusCounts,
            Map<SupportStatus, Integer> supportStatusCounts,
            Map<CandidateDisposition, Integer> dispositionCounts) {
        require(evaluation.candidates().size() == CASE_COUNT, "candidate count drifted");
        require(
                sourceStatusCounts.get(SourceStatus.VALID) == EXPECTED_VALID_SOURCE_CASES,
                "valid source count drifted");
        require(
                sourceStatusCounts.get(SourceStatus.INVALID) == EXPECTED_INVALID_SOURCE_CASES,
                "invalid source count drifted");
        require(
                supportStatusCounts.get(SupportStatus.SUPPORTED_BY_SYNTHETIC_GOLD)
                        == EXPECTED_SUPPORTED_CASES,
                "supported count drifted");
        require(
                supportStatusCounts.get(SupportStatus.UNSUPPORTED_BY_SYNTHETIC_GOLD)
                        == EXPECTED_UNSUPPORTED_CASES,
                "unsupported count drifted");
        require(
                dispositionCounts.get(CandidateDisposition.ADMISSIBLE_FOR_REVIEW) == 48,
                "review-only disposition count drifted");
        require(
                dispositionCounts.get(CandidateDisposition.REJECT_UNSUPPORTED) == 48,
                "unsupported disposition count drifted");
        require(
                dispositionCounts.get(CandidateDisposition.REJECT_INVALID_SOURCE) == 24,
                "invalid-source disposition count drifted");
        require(
                dispositionCounts.get(
                                CandidateDisposition.REJECT_INVALID_SOURCE_AND_UNSUPPORTED)
                        == 24,
                "combined rejection count drifted");

        AggregateMetrics metrics = evaluation.metrics();
        require(metrics.microCounts().equals(new ConfusionCounts(72, 72, 72)),
                "micro counts drifted");
        requireHalf(metrics.microPrecision(), "micro precision");
        requireHalf(metrics.microRecall(), "micro recall");
        requireHalf(metrics.microF1(), "micro F1");
        requireHalf(metrics.macroPrecision(), "macro precision");
        requireHalf(metrics.macroRecall(), "macro recall");
        requireHalf(metrics.macroF1(), "macro F1");
        require(metrics.sourceValidity().equals(Fraction.of(2, 3)), "source validity drifted");
        require(
                metrics.unsupportedClaimRate().equals(Fraction.of(1, 2)),
                "unsupported claim rate drifted");

        verifyLabelMetric(metrics, Label.FINAL_DECISION, 18);
        verifyLabelMetric(metrics, Label.REVISION_LINK, 36);
        verifyLabelMetric(metrics, Label.CONFIRMED_TASK, 18);
    }

    private static void verifyLabelMetric(
            AggregateMetrics metrics, Label label, long expectedEachCount) {
        LabelMetrics metric =
                metrics.perLabel().stream()
                        .filter(candidate -> candidate.label() == label)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("missing label metric: " + label));
        require(
                metric.counts()
                        .equals(
                                new ConfusionCounts(
                                        expectedEachCount, expectedEachCount, expectedEachCount)),
                "label counts drifted: " + label);
        requireHalf(metric.precision(), label + " precision");
        requireHalf(metric.recall(), label + " recall");
        requireHalf(metric.f1(), label + " F1");
    }

    private static void verifyProtection(
            ProtectionResult protection,
            Map<ProposalDisposition, Integer> proposalDispositionCounts) {
        require(protection.proposals().size() == CASE_COUNT * 4, "proposal count drifted");
        for (ProposalDisposition disposition : ProposalDisposition.values()) {
            require(
                    proposalDispositionCounts.get(disposition) == CASE_COUNT,
                    "proposal disposition count drifted: " + disposition);
        }
    }

    private static void verifyIndividualCaseRelations(List<CampaignCase> cases) {
        for (CampaignCase campaignCase : cases) {
            EvaluationResult result =
                    DecisionDeterministicSyntheticHarness.evaluate(
                            List.of(campaignCase.source()),
                            List.of(campaignCase.gold()),
                            List.of(campaignCase.prediction()));
            require(result.state() == EvaluationState.COMPLETE,
                    "individual case invalid: " + campaignCase.caseId());
            require(result.candidates().size() == 1,
                    "individual candidate count drifted: " + campaignCase.caseId());
            CandidateResult candidate = result.candidates().get(0);
            SourceStatus expectedSource =
                    hasInvalidBoundary(campaignCase.outcome())
                            ? SourceStatus.INVALID
                            : SourceStatus.VALID;
            SupportStatus expectedSupport =
                    hasWrongId(campaignCase.outcome())
                                    || campaignCase.outcome()
                                            == OutcomeVariant.WRONG_LABEL_VALID_SOURCE
                            ? SupportStatus.UNSUPPORTED_BY_SYNTHETIC_GOLD
                            : SupportStatus.SUPPORTED_BY_SYNTHETIC_GOLD;
            require(candidate.sourceStatus() == expectedSource,
                    "individual source status drifted: " + campaignCase.caseId());
            require(candidate.supportStatus() == expectedSupport,
                    "individual support status drifted: " + campaignCase.caseId());
            if (expectedSource == SourceStatus.INVALID) {
                require(
                        candidate.sourceIssues().stream()
                                .allMatch(
                                        issue ->
                                                issue.code()
                                                        == DecisionDeterministicSyntheticHarness
                                                                .IssueCode
                                                                .SOURCE_RANGE_NOT_UTF8_BOUNDARY),
                        "unexpected invalid-boundary issue: " + campaignCase.caseId());
            } else {
                require(candidate.sourceIssues().isEmpty(),
                        "valid case exposed source issues: " + campaignCase.caseId());
            }
        }
    }

    private static void requireHalf(Fraction fraction, String name) {
        require(fraction.equals(Fraction.of(1, 2)), name + " drifted");
    }

    private static String canonicalPayload(
            Map<LanguageSlice, Integer> languageCounts,
            Map<RelationSlice, Integer> relationCounts,
            Map<OutcomeVariant, Integer> outcomeCounts,
            Map<SourceStatus, Integer> sourceStatusCounts,
            Map<SupportStatus, Integer> supportStatusCounts,
            Map<CandidateDisposition, Integer> candidateDispositionCounts,
            Map<ProposalDisposition, Integer> proposalDispositionCounts,
            AggregateMetrics metrics,
            String evaluationDigest,
            String protectionDigest) {
        StringBuilder output = new StringBuilder();
        output.append("profile=").append(PROFILE_VERSION).append('\n');
        output.append("authority=").append(AUTHORIZATION_ID).append('\n');
        output.append("dependencyBoundary=java.base\n");
        output.append("caseCount=").append(CASE_COUNT).append('\n');
        output.append("governedCorpusCaseCount=").append(GOVERNED_CORPUS_CASE_COUNT).append('\n');
        appendEnumCounts(output, "language", LanguageSlice.values(), languageCounts);
        appendEnumCounts(output, "relation", RelationSlice.values(), relationCounts);
        appendEnumCounts(output, "outcome", OutcomeVariant.values(), outcomeCounts);
        appendEnumCounts(output, "sourceStatus", SourceStatus.values(), sourceStatusCounts);
        appendEnumCounts(output, "supportStatus", SupportStatus.values(), supportStatusCounts);
        appendEnumCounts(
                output,
                "candidateDisposition",
                CandidateDisposition.values(),
                candidateDispositionCounts);
        appendEnumCounts(
                output,
                "proposalDisposition",
                ProposalDisposition.values(),
                proposalDispositionCounts);
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
        output.append("sourceValidity=").append(metrics.sourceValidity().canonical()).append('\n');
        output.append("unsupportedClaimRate=")
                .append(metrics.unsupportedClaimRate().canonical())
                .append('\n');
        output.append("evaluationDigest=").append(evaluationDigest).append('\n');
        output.append("protectionDigest=").append(protectionDigest).append('\n');
        output.append("orderingMetamorphicInvariant=true\n");
        output.append("repeatDigestStable=true\n");
        output.append("autoApply=false\n");
        output.append("stateMutation=false\n");
        output.append("pocVerdict=NOT_RUN\n");
        output.append("pocReadiness=BLOCKED_UNCHANGED\n");
        output.append("qualityClaim=false\n");
        output.append("productAdmission=false\n");
        return output.toString();
    }

    private static <E extends Enum<E>> void appendEnumCounts(
            StringBuilder output, String prefix, E[] values, Map<E, Integer> counts) {
        for (E value : values) {
            output.append(prefix)
                    .append('=')
                    .append(value)
                    .append('|')
                    .append(counts.get(value))
                    .append('\n');
        }
    }

    private static <E extends Enum<E>> Map<E, Integer> immutableEnumMap(
            Map<E, Integer> values) {
        Objects.requireNonNull(values, "values");
        return Collections.unmodifiableMap(new EnumMap<>(values));
    }

    private static void requireDigest(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
