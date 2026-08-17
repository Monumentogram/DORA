package com.monumentogram.dora.stage0.decision;

import com.monumentogram.dora.stage0.decision.DecisionRevisionOracle.BatchState;
import com.monumentogram.dora.stage0.decision.DecisionRevisionOracle.DecisionProjection;
import com.monumentogram.dora.stage0.decision.DecisionRevisionOracle.EvaluationResult;
import com.monumentogram.dora.stage0.decision.DecisionRevisionOracle.IssueCode;
import com.monumentogram.dora.stage0.decision.DecisionRevisionOracle.Origin;
import com.monumentogram.dora.stage0.decision.DecisionRevisionOracle.ProjectionState;
import com.monumentogram.dora.stage0.decision.DecisionRevisionOracle.Revision;
import com.monumentogram.dora.stage0.decision.DecisionRevisionOracle.RevisionType;
import com.monumentogram.dora.stage0.decision.DecisionRevisionOracle.SourceFixture;
import com.monumentogram.dora.stage0.decision.DecisionRevisionOracle.SourceRef;
import com.monumentogram.dora.stage0.decision.DecisionRevisionOracle.ValidationIssue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** In-code, opaque, deterministic tests for the Stage 0 decision revision oracle. */
public final class DecisionRevisionOracleTest {
    private static final String HASH_A = "1".repeat(64);
    private static final String HASH_B = "a".repeat(64);
    private static final SourceFixture SOURCE_A = new SourceFixture("SOURCE_A", 1_000, HASH_A);
    private static final SourceFixture SOURCE_B = new SourceFixture("SOURCE_B", 2_000, HASH_B);
    private static final List<SourceFixture> SOURCES = List.of(SOURCE_A, SOURCE_B);
    private static final SourceRef REF_A = new SourceRef("SOURCE_A", 10, 20);
    private static final SourceRef REF_B = new SourceRef("SOURCE_B", 30, 40);

    private DecisionRevisionOracleTest() {}

    public static void main(String[] args) {
        canonicalChainSelectsFinalAndRetainsHistory();
        onlyConfirmedAndFinalBecomeCurrent();
        explicitCancellationIsPolicySafeOnly();
        reopeningAfterCancellationFailsClosed();
        oldestCancellationBarrierCannotBeBypassed();
        cancellationCompetingWithAnotherLeafConflicts();
        modelCancellationCannotReopenPastManualCurrent();
        manualCurrentSurvivesModelReplacement();
        unlinkedEligibleLeavesRequireConflictReview();
        unclearAndDownTierRelationsFailClosed();
        confirmedCannotReplaceLinkedFinalWithoutReview();
        manualConfirmedCannotReplaceManualFinalWithoutReview();
        fullAncestryPreservesStrongestCandidate();
        downTierBranchConflictsWithUnrelatedCandidate();
        manualPromotionBecomesCurrent();
        directCancellationWithoutAuthoritativeParentNeedsReview();
        unrelatedManualHeadsConflict();
        protectedManualBranchesReconcileFailClosed();
        multiplePreservedBranchCandidatesConflict();
        rejectedRevisionsAreExcludedButRetainedInHistory();
        decisionsRemainIsolatedAndSorted();
        inputPermutationAndRepeatAreDeterministic();
        branchHeavyValidPermutationsAreDeterministic();
        canonicalOutputHasLiteralGolden();
        canonicalNullDoesNotCollideWithNoneId();
        invalidSourceFixturesReturnTypedIssues();
        invalidRevisionIdentityAndOrderingReturnTypedIssues();
        invalidSourceReferencesReturnTypedIssues();
        invalidSupersedesGraphsReturnTypedIssues();
        scalarNullAndLongBoundariesReturnExactIssues();
        threeNodeCycleIsPermutationDeterministic();
        nullInputsAndElementsReturnTypedIssues();
        recordsAndResultsAreImmutable();
        System.out.print("POC_DECISION_REVISION_ORACLE_TESTS_OK\n");
    }

    private static void canonicalChainSelectsFinalAndRetainsHistory() {
        Revision proposal =
                revision(
                        "REV_001",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.PROPOSAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision amended =
                revision(
                        "REV_002",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.AMENDED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_001",
                        REF_A);
        Revision finalRevision =
                revision(
                        "REV_003",
                        "DECISION_A",
                        "SUBJECT_A",
                        3,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_002",
                        REF_B);

        EvaluationResult result = evaluate(List.of(finalRevision, proposal, amended));
        assertProjection(
                result,
                "DECISION_A",
                ProjectionState.CURRENT,
                "REV_003",
                List.of("REV_001", "REV_002", "REV_003"),
                List.of());
    }

    private static void onlyConfirmedAndFinalBecomeCurrent() {
        List<Revision> revisions =
                List.of(
                        revision(
                                "REV_A1",
                                "DECISION_A",
                                "SUBJECT_A",
                                1,
                                RevisionType.PROPOSAL,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                REF_A),
                        revision(
                                "REV_B1",
                                "DECISION_B",
                                "SUBJECT_B",
                                1,
                                RevisionType.TENTATIVE,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                REF_A),
                        revision(
                                "REV_C1",
                                "DECISION_C",
                                "SUBJECT_C",
                                1,
                                RevisionType.PROPOSAL,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                REF_A),
                        revision(
                                "REV_C2",
                                "DECISION_C",
                                "SUBJECT_C",
                                2,
                                RevisionType.AMENDED,
                                Origin.MODEL_ONLY,
                                false,
                                "REV_C1",
                                REF_A),
                        revision(
                                "REV_D1",
                                "DECISION_D",
                                "SUBJECT_D",
                                1,
                                RevisionType.CONFIRMED,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                REF_B),
                        revision(
                                "REV_E1",
                                "DECISION_E",
                                "SUBJECT_E",
                                1,
                                RevisionType.FINAL,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                REF_B));

        EvaluationResult result = evaluate(revisions);
        assertProjectionInBatch(
                result,
                5,
                "DECISION_A",
                ProjectionState.REVIEW_REQUIRED,
                null,
                List.of("REV_A1"),
                List.of("REV_A1"));
        assertProjectionInBatch(
                result,
                5,
                "DECISION_B",
                ProjectionState.REVIEW_REQUIRED,
                null,
                List.of("REV_B1"),
                List.of("REV_B1"));
        assertProjectionInBatch(
                result,
                5,
                "DECISION_C",
                ProjectionState.REVIEW_REQUIRED,
                null,
                List.of("REV_C1", "REV_C2"),
                List.of("REV_C2"));
        assertProjectionInBatch(
                result,
                5,
                "DECISION_D",
                ProjectionState.CURRENT,
                "REV_D1",
                List.of("REV_D1"),
                List.of());
        assertProjectionInBatch(
                result,
                5,
                "DECISION_E",
                ProjectionState.CURRENT,
                "REV_E1",
                List.of("REV_E1"),
                List.of());
    }

    private static void explicitCancellationIsPolicySafeOnly() {
        Revision modelCurrent =
                revision(
                        "REV_101",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision modelCancel =
                revision(
                        "REV_102",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.CANCELLED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_101",
                        REF_B);
        assertProjection(
                evaluate(List.of(modelCurrent, modelCancel)),
                "DECISION_A",
                ProjectionState.CANCELLED,
                null,
                List.of("REV_101", "REV_102"),
                List.of());

        Revision manualCurrent =
                revision(
                        "REV_201",
                        "DECISION_B",
                        "SUBJECT_B",
                        1,
                        RevisionType.FINAL,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        null,
                        REF_A);
        Revision unsafeModelCancel =
                revision(
                        "REV_202",
                        "DECISION_B",
                        "SUBJECT_B",
                        2,
                        RevisionType.CANCELLED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_201",
                        REF_B);
        assertProjection(
                evaluate(List.of(manualCurrent, unsafeModelCancel)),
                "DECISION_B",
                ProjectionState.MANUAL_CURRENT_REVIEW_REQUIRED,
                "REV_201",
                List.of("REV_201", "REV_202"),
                List.of("REV_202"));

        Revision safeManualCancel =
                revision(
                        "REV_203",
                        "DECISION_B",
                        "SUBJECT_B",
                        3,
                        RevisionType.CANCELLED,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        "REV_201",
                        REF_B);
        assertProjection(
                evaluate(List.of(manualCurrent, safeManualCancel)),
                "DECISION_B",
                ProjectionState.CANCELLED,
                null,
                List.of("REV_201", "REV_203"),
                List.of());
    }

    private static void reopeningAfterCancellationFailsClosed() {
        Revision current =
                revision(
                        "REV_211",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision cancelled =
                revision(
                        "REV_212",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.CANCELLED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_211",
                        REF_B);
        Revision tentativeReopen =
                revision(
                        "REV_213",
                        "DECISION_A",
                        "SUBJECT_A",
                        3,
                        RevisionType.TENTATIVE,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_212",
                        REF_A);
        assertProjection(
                evaluate(List.of(current, cancelled, tentativeReopen)),
                "DECISION_A",
                ProjectionState.REVIEW_REQUIRED,
                null,
                List.of("REV_211", "REV_212", "REV_213"),
                List.of("REV_213"));

        Revision finalReopen =
                revision(
                        "REV_214",
                        "DECISION_A",
                        "SUBJECT_A",
                        4,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_212",
                        REF_A);
        assertProjection(
                evaluate(List.of(current, cancelled, finalReopen)),
                "DECISION_A",
                ProjectionState.REVIEW_REQUIRED,
                null,
                List.of("REV_211", "REV_212", "REV_214"),
                List.of("REV_214"));
    }

    private static void oldestCancellationBarrierCannotBeBypassed() {
        Revision manualFinal =
                revision(
                        "REV_241A",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.FINAL,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        null,
                        REF_A);
        Revision firstManualChainCancellation =
                revision(
                        "REV_242A",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.CANCELLED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_241A",
                        REF_B);
        Revision reopenedManualChainFinal =
                revision(
                        "REV_243A",
                        "DECISION_A",
                        "SUBJECT_A",
                        3,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_242A",
                        REF_A);
        Revision laterManualChainCancellation =
                revision(
                        "REV_244A",
                        "DECISION_A",
                        "SUBJECT_A",
                        4,
                        RevisionType.CANCELLED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_243A",
                        REF_B);
        List<Revision> manualOrdered =
                List.of(
                        manualFinal,
                        firstManualChainCancellation,
                        reopenedManualChainFinal,
                        laterManualChainCancellation);
        EvaluationResult manualResult = evaluate(manualOrdered);
        EvaluationResult manualPermuted =
                DecisionRevisionOracle.evaluate(
                        List.of(SOURCE_B, SOURCE_A),
                        List.of(
                                laterManualChainCancellation,
                                reopenedManualChainFinal,
                                manualFinal,
                                firstManualChainCancellation));
        EvaluationResult manualRepeated = evaluate(manualOrdered);
        assertProjection(
                manualResult,
                "DECISION_A",
                ProjectionState.MANUAL_CURRENT_REVIEW_REQUIRED,
                "REV_241A",
                List.of("REV_241A", "REV_242A", "REV_243A", "REV_244A"),
                List.of("REV_244A"));
        check(manualResult.equals(manualPermuted), "manual double-cancel permutation changed output");
        check(manualResult.equals(manualRepeated), "manual double-cancel repeat changed output");
        check(
                manualResult.canonicalOutput().equals(manualPermuted.canonicalOutput()),
                "manual double-cancel permutation changed canonical output");

        Revision modelFinal =
                revision(
                        "REV_241B",
                        "DECISION_B",
                        "SUBJECT_B",
                        1,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision firstModelChainCancellation =
                revision(
                        "REV_242B",
                        "DECISION_B",
                        "SUBJECT_B",
                        2,
                        RevisionType.CANCELLED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_241B",
                        REF_B);
        Revision reopenedModelChainFinal =
                revision(
                        "REV_243B",
                        "DECISION_B",
                        "SUBJECT_B",
                        3,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_242B",
                        REF_A);
        Revision laterModelChainCancellation =
                revision(
                        "REV_244B",
                        "DECISION_B",
                        "SUBJECT_B",
                        4,
                        RevisionType.CANCELLED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_243B",
                        REF_B);
        List<Revision> modelOrdered =
                List.of(
                        modelFinal,
                        firstModelChainCancellation,
                        reopenedModelChainFinal,
                        laterModelChainCancellation);
        EvaluationResult modelResult = evaluate(modelOrdered);
        EvaluationResult modelPermuted =
                DecisionRevisionOracle.evaluate(
                        List.of(SOURCE_B, SOURCE_A),
                        List.of(
                                firstModelChainCancellation,
                                laterModelChainCancellation,
                                modelFinal,
                                reopenedModelChainFinal));
        EvaluationResult modelRepeated = evaluate(modelOrdered);
        assertProjection(
                modelResult,
                "DECISION_B",
                ProjectionState.REVIEW_REQUIRED,
                null,
                List.of("REV_241B", "REV_242B", "REV_243B", "REV_244B"),
                List.of("REV_244B"));
        check(modelResult.equals(modelPermuted), "model double-cancel permutation changed output");
        check(modelResult.equals(modelRepeated), "model double-cancel repeat changed output");
        check(
                modelResult.canonicalOutput().equals(modelPermuted.canonicalOutput()),
                "model double-cancel permutation changed canonical output");
    }

    private static void cancellationCompetingWithAnotherLeafConflicts() {
        Revision current =
                revision(
                        "REV_221",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision cancelled =
                revision(
                        "REV_222",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.CANCELLED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_221",
                        REF_B);
        Revision unlinkedFinal =
                revision(
                        "REV_223",
                        "DECISION_A",
                        "SUBJECT_A",
                        3,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        assertProjection(
                evaluate(List.of(current, cancelled, unlinkedFinal)),
                "DECISION_A",
                ProjectionState.CONFLICT_REVIEW,
                null,
                List.of("REV_221", "REV_222", "REV_223"),
                List.of("REV_222", "REV_223"));
    }

    private static void modelCancellationCannotReopenPastManualCurrent() {
        Revision manualCurrent =
                revision(
                        "REV_231",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.FINAL,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        null,
                        REF_A);
        Revision modelCancel =
                revision(
                        "REV_232",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.CANCELLED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_231",
                        REF_B);
        Revision linkedModelFinal =
                revision(
                        "REV_233",
                        "DECISION_A",
                        "SUBJECT_A",
                        3,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_232",
                        REF_A);
        assertProjection(
                evaluate(List.of(manualCurrent, modelCancel, linkedModelFinal)),
                "DECISION_A",
                ProjectionState.MANUAL_CURRENT_REVIEW_REQUIRED,
                "REV_231",
                List.of("REV_231", "REV_232", "REV_233"),
                List.of("REV_233"));

        Revision unlinkedModelFinal =
                revision(
                        "REV_234",
                        "DECISION_A",
                        "SUBJECT_A",
                        4,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_B);
        assertProjection(
                evaluate(List.of(manualCurrent, modelCancel, unlinkedModelFinal)),
                "DECISION_A",
                ProjectionState.MANUAL_CURRENT_REVIEW_REQUIRED,
                "REV_231",
                List.of("REV_231", "REV_232", "REV_234"),
                List.of("REV_232", "REV_234"));
    }

    private static void manualCurrentSurvivesModelReplacement() {
        Revision manualCurrent =
                revision(
                        "REV_301",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        null,
                        REF_A);
        Revision linkedModelFinal =
                revision(
                        "REV_302",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_301",
                        REF_B);
        assertProjection(
                evaluate(List.of(linkedModelFinal, manualCurrent)),
                "DECISION_A",
                ProjectionState.MANUAL_CURRENT_REVIEW_REQUIRED,
                "REV_301",
                List.of("REV_301", "REV_302"),
                List.of("REV_302"));

        Revision unlinkedModelFinal =
                revision(
                        "REV_303",
                        "DECISION_A",
                        "SUBJECT_A",
                        3,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_B);
        assertProjection(
                evaluate(List.of(manualCurrent, unlinkedModelFinal)),
                "DECISION_A",
                ProjectionState.CONFLICT_REVIEW,
                null,
                List.of("REV_301", "REV_303"),
                List.of("REV_301", "REV_303"));
    }

    private static void unlinkedEligibleLeavesRequireConflictReview() {
        Revision first =
                revision(
                        "REV_401",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision second =
                revision(
                        "REV_402",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_B);
        assertProjection(
                evaluate(List.of(second, first)),
                "DECISION_A",
                ProjectionState.CONFLICT_REVIEW,
                null,
                List.of("REV_401", "REV_402"),
                List.of("REV_401", "REV_402"));
    }

    private static void unclearAndDownTierRelationsFailClosed() {
        Revision current =
                revision(
                        "REV_501",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision downTier =
                revision(
                        "REV_502",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.TENTATIVE,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_501",
                        REF_B);
        assertProjection(
                evaluate(List.of(current, downTier)),
                "DECISION_A",
                ProjectionState.REVIEW_REQUIRED,
                "REV_501",
                List.of("REV_501", "REV_502"),
                List.of("REV_502"));

        Revision contradicted =
                revision(
                        "REV_503",
                        "DECISION_A",
                        "SUBJECT_A",
                        3,
                        RevisionType.CONTRADICTED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_501",
                        REF_B);
        assertProjection(
                evaluate(List.of(current, contradicted)),
                "DECISION_A",
                ProjectionState.REVIEW_REQUIRED,
                "REV_501",
                List.of("REV_501", "REV_503"),
                List.of("REV_503"));
    }

    private static void confirmedCannotReplaceLinkedFinalWithoutReview() {
        Revision finalCurrent =
                revision(
                        "REV_551",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision laterConfirmed =
                revision(
                        "REV_552",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_551",
                        REF_B);
        assertProjection(
                evaluate(List.of(finalCurrent, laterConfirmed)),
                "DECISION_A",
                ProjectionState.REVIEW_REQUIRED,
                "REV_551",
                List.of("REV_551", "REV_552"),
                List.of("REV_552"));
    }

    private static void manualConfirmedCannotReplaceManualFinalWithoutReview() {
        Revision manualFinal =
                revision(
                        "REV_561",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.FINAL,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        null,
                        REF_A);
        Revision laterManualConfirmed =
                revision(
                        "REV_562",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.CONFIRMED,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        "REV_561",
                        REF_B);
        assertProjection(
                evaluate(List.of(manualFinal, laterManualConfirmed)),
                "DECISION_A",
                ProjectionState.MANUAL_CURRENT_REVIEW_REQUIRED,
                "REV_561",
                List.of("REV_561", "REV_562"),
                List.of("REV_562"));
    }

    private static void fullAncestryPreservesStrongestCandidate() {
        Revision modelFinal =
                revision(
                        "REV_571",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision modelConfirmed =
                revision(
                        "REV_572",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_571",
                        REF_B);
        Revision secondModelConfirmed =
                revision(
                        "REV_573",
                        "DECISION_A",
                        "SUBJECT_A",
                        3,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_572",
                        REF_A);
        assertProjection(
                evaluate(List.of(secondModelConfirmed, modelFinal, modelConfirmed)),
                "DECISION_A",
                ProjectionState.REVIEW_REQUIRED,
                "REV_571",
                List.of("REV_571", "REV_572", "REV_573"),
                List.of("REV_573"));

        Revision manualFinal =
                revision(
                        "REV_581",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.FINAL,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        null,
                        REF_A);
        Revision manualConfirmed =
                revision(
                        "REV_582",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.CONFIRMED,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        "REV_581",
                        REF_B);
        Revision secondManualConfirmed =
                revision(
                        "REV_583",
                        "DECISION_A",
                        "SUBJECT_A",
                        3,
                        RevisionType.CONFIRMED,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        "REV_582",
                        REF_A);
        assertProjection(
                evaluate(List.of(manualConfirmed, secondManualConfirmed, manualFinal)),
                "DECISION_A",
                ProjectionState.MANUAL_CURRENT_REVIEW_REQUIRED,
                "REV_581",
                List.of("REV_581", "REV_582", "REV_583"),
                List.of("REV_583"));
    }

    private static void downTierBranchConflictsWithUnrelatedCandidate() {
        Revision confirmed =
                revision(
                        "REV_591",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision tentative =
                revision(
                        "REV_592",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.TENTATIVE,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_591",
                        REF_B);
        Revision unrelatedConfirmed =
                revision(
                        "REV_593",
                        "DECISION_A",
                        "SUBJECT_A",
                        3,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        assertProjection(
                evaluate(List.of(unrelatedConfirmed, tentative, confirmed)),
                "DECISION_A",
                ProjectionState.CONFLICT_REVIEW,
                null,
                List.of("REV_591", "REV_592", "REV_593"),
                List.of("REV_592", "REV_593"));
    }

    private static void manualPromotionBecomesCurrent() {
        Revision confirmed =
                revision(
                        "REV_601A",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        null,
                        REF_A);
        Revision finalRevision =
                revision(
                        "REV_602A",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.FINAL,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        "REV_601A",
                        REF_B);
        assertProjection(
                evaluate(List.of(finalRevision, confirmed)),
                "DECISION_A",
                ProjectionState.CURRENT,
                "REV_602A",
                List.of("REV_601A", "REV_602A"),
                List.of());
    }

    private static void directCancellationWithoutAuthoritativeParentNeedsReview() {
        Revision proposal =
                revision(
                        "REV_611",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.PROPOSAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision cancellation =
                revision(
                        "REV_612",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.CANCELLED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_611",
                        REF_B);
        assertProjection(
                evaluate(List.of(cancellation, proposal)),
                "DECISION_A",
                ProjectionState.REVIEW_REQUIRED,
                null,
                List.of("REV_611", "REV_612"),
                List.of("REV_612"));
    }

    private static void unrelatedManualHeadsConflict() {
        Revision first =
                revision(
                        "REV_621",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        null,
                        REF_A);
        Revision second =
                revision(
                        "REV_622",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.FINAL,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        null,
                        REF_B);
        assertProjection(
                evaluate(List.of(second, first)),
                "DECISION_A",
                ProjectionState.CONFLICT_REVIEW,
                null,
                List.of("REV_621", "REV_622"),
                List.of("REV_621", "REV_622"));
    }

    private static void protectedManualBranchesReconcileFailClosed() {
        Revision protectedManual =
                revision(
                        "REV_631",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.FINAL,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        null,
                        REF_A);
        Revision modelFinal =
                revision(
                        "REV_632",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_631",
                        REF_B);
        Revision modelCancellation =
                revision(
                        "REV_633",
                        "DECISION_A",
                        "SUBJECT_A",
                        3,
                        RevisionType.CANCELLED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_632",
                        REF_A);
        assertProjection(
                evaluate(List.of(modelCancellation, modelFinal, protectedManual)),
                "DECISION_A",
                ProjectionState.MANUAL_CURRENT_REVIEW_REQUIRED,
                "REV_631",
                List.of("REV_631", "REV_632", "REV_633"),
                List.of("REV_633"));

        Revision secondManual =
                revision(
                        "REV_634",
                        "DECISION_A",
                        "SUBJECT_A",
                        4,
                        RevisionType.CONFIRMED,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        null,
                        REF_B);
        assertProjection(
                evaluate(List.of(protectedManual, modelFinal, modelCancellation, secondManual)),
                "DECISION_A",
                ProjectionState.CONFLICT_REVIEW,
                null,
                List.of("REV_631", "REV_632", "REV_633", "REV_634"),
                List.of("REV_633", "REV_634"));

        Revision firstManual =
                revision(
                        "REV_641",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        null,
                        REF_A);
        Revision firstCancellation =
                revision(
                        "REV_642",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.CANCELLED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_641",
                        REF_B);
        Revision otherManual =
                revision(
                        "REV_643",
                        "DECISION_A",
                        "SUBJECT_A",
                        3,
                        RevisionType.FINAL,
                        Origin.MANUAL_CONFIRMED,
                        false,
                        null,
                        REF_A);
        Revision secondCancellation =
                revision(
                        "REV_644",
                        "DECISION_A",
                        "SUBJECT_A",
                        4,
                        RevisionType.CANCELLED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_643",
                        REF_B);
        assertProjection(
                evaluate(List.of(secondCancellation, firstManual, firstCancellation, otherManual)),
                "DECISION_A",
                ProjectionState.CONFLICT_REVIEW,
                null,
                List.of("REV_641", "REV_642", "REV_643", "REV_644"),
                List.of("REV_642", "REV_644"));
    }

    private static void multiplePreservedBranchCandidatesConflict() {
        Revision firstFinal =
                revision(
                        "REV_651",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision firstTentative =
                revision(
                        "REV_652",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.TENTATIVE,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_651",
                        REF_B);
        Revision secondFinal =
                revision(
                        "REV_653",
                        "DECISION_A",
                        "SUBJECT_A",
                        3,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision secondTentative =
                revision(
                        "REV_654",
                        "DECISION_A",
                        "SUBJECT_A",
                        4,
                        RevisionType.TENTATIVE,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_653",
                        REF_B);
        assertProjection(
                evaluate(List.of(secondTentative, firstFinal, secondFinal, firstTentative)),
                "DECISION_A",
                ProjectionState.CONFLICT_REVIEW,
                null,
                List.of("REV_651", "REV_652", "REV_653", "REV_654"),
                List.of("REV_652", "REV_654"));
    }

    private static void rejectedRevisionsAreExcludedButRetainedInHistory() {
        Revision current =
                revision(
                        "REV_601",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision rejectedFinal =
                revision(
                        "REV_602",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        true,
                        "REV_601",
                        REF_B);
        assertProjection(
                evaluate(List.of(rejectedFinal, current)),
                "DECISION_A",
                ProjectionState.CURRENT,
                "REV_601",
                List.of("REV_601", "REV_602"),
                List.of());

        Revision rejectedOnly =
                revision(
                        "REV_603",
                        "DECISION_B",
                        "SUBJECT_B",
                        1,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        true,
                        null,
                        REF_A);
        assertProjection(
                evaluate(List.of(rejectedOnly)),
                "DECISION_B",
                ProjectionState.NO_CURRENT,
                null,
                List.of("REV_603"),
                List.of());
    }

    private static void decisionsRemainIsolatedAndSorted() {
        Revision decisionB =
                revision(
                        "REV_B01",
                        "DECISION_B",
                        "SUBJECT_B",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_B);
        Revision decisionA =
                revision(
                        "REV_A01",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        EvaluationResult result = evaluate(List.of(decisionB, decisionA));
        assertProjectionInBatch(
                result,
                2,
                "DECISION_A",
                ProjectionState.CURRENT,
                "REV_A01",
                List.of("REV_A01"),
                List.of());
        assertProjectionInBatch(
                result,
                2,
                "DECISION_B",
                ProjectionState.CURRENT,
                "REV_B01",
                List.of("REV_B01"),
                List.of());
        check(
                result.projections().get(0).decisionId().equals("DECISION_A"),
                "decision output must be sorted by exact ID");
        check(
                result.projections().get(1).decisionId().equals("DECISION_B"),
                "decision output must preserve isolation");
    }

    private static void inputPermutationAndRepeatAreDeterministic() {
        Revision first =
                revision(
                        "REV_701",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.PROPOSAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision second =
                revision(
                        "REV_702",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_701",
                        REF_B);
        Revision third =
                revision(
                        "REV_703",
                        "DECISION_B",
                        "SUBJECT_B",
                        1,
                        RevisionType.TENTATIVE,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);

        EvaluationResult ordered =
                DecisionRevisionOracle.evaluate(SOURCES, List.of(first, second, third));
        EvaluationResult permuted =
                DecisionRevisionOracle.evaluate(
                        List.of(SOURCE_B, SOURCE_A), List.of(third, second, first));
        EvaluationResult repeated =
                DecisionRevisionOracle.evaluate(SOURCES, List.of(first, second, third));
        check(ordered.equals(permuted), "input permutation changed typed output");
        check(ordered.equals(repeated), "repeat changed typed output");
        check(
                ordered.canonicalOutput().equals(permuted.canonicalOutput()),
                "input permutation changed canonical output");
        check(
                ordered.canonicalOutput().equals(repeated.canonicalOutput()),
                "repeat changed canonical output");
    }

    private static void branchHeavyValidPermutationsAreDeterministic() {
        Revision root =
                revision(
                        "REV_711",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision tentative =
                revision(
                        "REV_712",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.TENTATIVE,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_711",
                        REF_B);
        Revision amended =
                revision(
                        "REV_713",
                        "DECISION_A",
                        "SUBJECT_A",
                        3,
                        RevisionType.AMENDED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_711",
                        REF_A);
        Revision contradicted =
                revision(
                        "REV_714",
                        "DECISION_A",
                        "SUBJECT_A",
                        4,
                        RevisionType.CONTRADICTED,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_711",
                        REF_B);
        List<Revision> orderedInput = List.of(root, tentative, amended, contradicted);
        List<Revision> reversedInput = List.of(contradicted, amended, tentative, root);
        List<Revision> rotatedInput = List.of(amended, root, contradicted, tentative);
        EvaluationResult ordered = DecisionRevisionOracle.evaluate(SOURCES, orderedInput);
        EvaluationResult reversed =
                DecisionRevisionOracle.evaluate(List.of(SOURCE_B, SOURCE_A), reversedInput);
        EvaluationResult rotated = DecisionRevisionOracle.evaluate(SOURCES, rotatedInput);
        EvaluationResult repeated = DecisionRevisionOracle.evaluate(SOURCES, orderedInput);
        assertProjection(
                ordered,
                "DECISION_A",
                ProjectionState.REVIEW_REQUIRED,
                "REV_711",
                List.of("REV_711", "REV_712", "REV_713", "REV_714"),
                List.of("REV_712", "REV_713", "REV_714"));
        check(ordered.equals(reversed), "branch-heavy reverse changed typed output");
        check(ordered.equals(rotated), "branch-heavy rotation changed typed output");
        check(ordered.equals(repeated), "branch-heavy repeat changed typed output");
        check(
                ordered.canonicalOutput().equals(reversed.canonicalOutput()),
                "branch-heavy reverse changed canonical output");
        check(
                ordered.canonicalOutput().equals(rotated.canonicalOutput()),
                "branch-heavy rotation changed canonical output");
    }

    private static void canonicalOutputHasLiteralGolden() {
        EvaluationResult result =
                new EvaluationResult(
                        BatchState.VALID,
                        List.of(
                                new DecisionProjection(
                                        "DECISION_A",
                                        "SUBJECT_A",
                                        ProjectionState.CURRENT,
                                        "REV_001",
                                        List.of("REV_001"),
                                        List.of())),
                        List.of());
        String expected =
                "batch=VALID\n"
                        + "projection=DECISION_A|SUBJECT_A|CURRENT|current=S7:REV_001"
                        + "|history=REV_001|review=\n";
        check(result.canonicalOutput().equals(expected), "canonical literal golden mismatch");
    }

    private static void canonicalNullDoesNotCollideWithNoneId() {
        DecisionProjection absent =
                new DecisionProjection(
                        "DECISION_A",
                        "SUBJECT_A",
                        ProjectionState.NO_CURRENT,
                        null,
                        List.of("NONE"),
                        List.of());
        DecisionProjection literalNone =
                new DecisionProjection(
                        "DECISION_A",
                        "SUBJECT_A",
                        ProjectionState.CURRENT,
                        "NONE",
                        List.of("NONE"),
                        List.of());
        String absentOutput =
                new EvaluationResult(BatchState.VALID, List.of(absent), List.of())
                        .canonicalOutput();
        String literalOutput =
                new EvaluationResult(BatchState.VALID, List.of(literalNone), List.of())
                        .canonicalOutput();
        check(absentOutput.contains("|current=N|"), "null current lacks tagged encoding");
        check(literalOutput.contains("|current=S4:NONE|"), "opaque NONE ID lacks length tag");
        check(!absentOutput.equals(literalOutput), "null current collides with opaque NONE ID");
    }

    private static void invalidSourceFixturesReturnTypedIssues() {
        expectInvalid(
                IssueCode.DUPLICATE_SOURCE_ID,
                List.of(SOURCE_A, new SourceFixture("SOURCE_A", 5, HASH_B)),
                List.of());
        expectInvalid(
                IssueCode.INVALID_SOURCE_ID,
                List.of(new SourceFixture("bad-source", 5, HASH_A)),
                List.of());
        expectInvalid(
                IssueCode.NONPOSITIVE_SOURCE_LENGTH,
                List.of(new SourceFixture("SOURCE_C", 0, HASH_A)),
                List.of());
        expectInvalid(
                IssueCode.INVALID_SOURCE_DIGEST,
                List.of(new SourceFixture("SOURCE_C", 5, "ABC")),
                List.of());
        expectInvalid(
                IssueCode.ZERO_SOURCE_DIGEST,
                List.of(new SourceFixture("SOURCE_C", 5, "0".repeat(64))),
                List.of());
    }

    private static void invalidRevisionIdentityAndOrderingReturnTypedIssues() {
        Revision valid =
                revision(
                        "REV_801",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        expectInvalid(
                IssueCode.DUPLICATE_REVISION_ID,
                SOURCES,
                List.of(
                        valid,
                        revision(
                                "REV_801",
                                "DECISION_A",
                                "SUBJECT_A",
                                2,
                                RevisionType.FINAL,
                                Origin.MODEL_ONLY,
                                false,
                                "REV_801",
                                REF_A)));
        expectInvalid(
                IssueCode.DUPLICATE_ORDINAL,
                SOURCES,
                List.of(
                        valid,
                        revision(
                                "REV_802",
                                "DECISION_A",
                                "SUBJECT_A",
                                1,
                                RevisionType.FINAL,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                REF_A)));
        expectInvalid(
                IssueCode.INVALID_REVISION_ID,
                SOURCES,
                List.of(
                        revision(
                                "bad-id",
                                "DECISION_A",
                                "SUBJECT_A",
                                1,
                                RevisionType.CONFIRMED,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                REF_A)));
        expectInvalid(
                IssueCode.INVALID_DECISION_ID,
                SOURCES,
                List.of(
                        revision(
                                "REV_803",
                                "",
                                "SUBJECT_A",
                                1,
                                RevisionType.CONFIRMED,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                REF_A)));
        expectInvalid(
                IssueCode.INVALID_SUBJECT_ID,
                SOURCES,
                List.of(
                        revision(
                                "REV_804",
                                "DECISION_A",
                                "bad-subject",
                                1,
                                RevisionType.CONFIRMED,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                REF_A)));
        expectInvalid(
                IssueCode.NONPOSITIVE_ORDINAL,
                SOURCES,
                List.of(
                        revision(
                                "REV_805",
                                "DECISION_A",
                                "SUBJECT_A",
                                0,
                                RevisionType.CONFIRMED,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                REF_A)));
        expectInvalid(
                IssueCode.MISSING_REVISION_TYPE,
                SOURCES,
                List.of(
                        revision(
                                "REV_806",
                                "DECISION_A",
                                "SUBJECT_A",
                                1,
                                null,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                REF_A)));
        expectInvalid(
                IssueCode.MISSING_ORIGIN,
                SOURCES,
                List.of(
                        revision(
                                "REV_807",
                                "DECISION_A",
                                "SUBJECT_A",
                                1,
                                RevisionType.CONFIRMED,
                                null,
                                false,
                                null,
                                REF_A)));
    }

    private static void invalidSourceReferencesReturnTypedIssues() {
        expectInvalid(
                IssueCode.MISSING_SOURCE_REFS,
                SOURCES,
                List.of(
                        revisionWithRefs(
                                "REV_901",
                                "DECISION_A",
                                "SUBJECT_A",
                                1,
                                RevisionType.CONFIRMED,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                List.of())));
        expectInvalid(
                IssueCode.MISSING_SOURCE_REFS,
                SOURCES,
                List.of(
                        revisionWithRefs(
                                "REV_902",
                                "DECISION_A",
                                "SUBJECT_A",
                                1,
                                RevisionType.CONFIRMED,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                null)));
        List<SourceRef> refsWithNull = new ArrayList<>();
        refsWithNull.add(null);
        expectInvalid(
                IssueCode.NULL_SOURCE_REF,
                SOURCES,
                List.of(
                        revisionWithRefs(
                                "REV_903",
                                "DECISION_A",
                                "SUBJECT_A",
                                1,
                                RevisionType.CONFIRMED,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                refsWithNull)));
        expectInvalid(
                IssueCode.INVALID_SOURCE_REF_ID,
                SOURCES,
                List.of(
                        revision(
                                "REV_904",
                                "DECISION_A",
                                "SUBJECT_A",
                                1,
                                RevisionType.CONFIRMED,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                new SourceRef("bad-source", 0, 1))));
        expectInvalid(
                IssueCode.UNKNOWN_SOURCE_REF,
                SOURCES,
                List.of(
                        revision(
                                "REV_905",
                                "DECISION_A",
                                "SUBJECT_A",
                                1,
                                RevisionType.CONFIRMED,
                                Origin.MODEL_ONLY,
                                false,
                                null,
                                new SourceRef("SOURCE_UNKNOWN", 0, 1))));
        for (SourceRef invalidRange :
                List.of(
                        new SourceRef("SOURCE_A", -1, 1),
                        new SourceRef("SOURCE_A", 2, 2),
                        new SourceRef("SOURCE_A", 3, 2),
                        new SourceRef("SOURCE_A", 999, 1_001))) {
            expectInvalid(
                    IssueCode.INVALID_SOURCE_RANGE,
                    SOURCES,
                    List.of(
                            revision(
                                    "REV_906",
                                    "DECISION_A",
                                    "SUBJECT_A",
                                    1,
                                    RevisionType.CONFIRMED,
                                    Origin.MODEL_ONLY,
                                    false,
                                    null,
                                    invalidRange)));
        }
    }

    private static void invalidSupersedesGraphsReturnTypedIssues() {
        Revision parent =
                revision(
                        "REV_A01",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        expectInvalid(
                IssueCode.DANGLING_SUPERSEDES,
                SOURCES,
                List.of(
                        revision(
                                "REV_A02",
                                "DECISION_A",
                                "SUBJECT_A",
                                2,
                                RevisionType.FINAL,
                                Origin.MODEL_ONLY,
                                false,
                                "REV_MISSING",
                                REF_A)));
        expectInvalid(
                IssueCode.SELF_SUPERSEDES,
                SOURCES,
                List.of(
                        revision(
                                "REV_A03",
                                "DECISION_A",
                                "SUBJECT_A",
                                2,
                                RevisionType.FINAL,
                                Origin.MODEL_ONLY,
                                false,
                                "REV_A03",
                                REF_A)));
        expectInvalid(
                IssueCode.INVALID_SUPERSEDES_ID,
                SOURCES,
                List.of(
                        revision(
                                "REV_A04",
                                "DECISION_A",
                                "SUBJECT_A",
                                2,
                                RevisionType.FINAL,
                                Origin.MODEL_ONLY,
                                false,
                                "bad-parent",
                                REF_A)));
        Revision crossDecision =
                revision(
                        "REV_B01",
                        "DECISION_B",
                        "SUBJECT_A",
                        2,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_A01",
                        REF_A);
        expectInvalid(
                IssueCode.CROSS_DECISION_SUPERSEDES,
                SOURCES,
                List.of(parent, crossDecision));
        Revision crossSubject =
                revision(
                        "REV_A05",
                        "DECISION_A",
                        "SUBJECT_B",
                        2,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_A01",
                        REF_A);
        expectInvalid(
                IssueCode.CROSS_SUBJECT_SUPERSEDES,
                SOURCES,
                List.of(parent, crossSubject));
        expectInvalid(
                IssueCode.DECISION_SUBJECT_MISMATCH,
                SOURCES,
                List.of(parent, crossSubject));
        Revision laterOrdinal =
                revision(
                        "REV_A06",
                        "DECISION_A",
                        "SUBJECT_A",
                        2,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        Revision earlierChild =
                revision(
                        "REV_A07",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_A06",
                        REF_A);
        expectInvalid(
                IssueCode.NON_LATER_SUPERSEDES,
                SOURCES,
                List.of(laterOrdinal, earlierChild));

        Revision cycleA =
                revision(
                        "REV_C01",
                        "DECISION_C",
                        "SUBJECT_C",
                        1,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_C02",
                        REF_A);
        Revision cycleB =
                revision(
                        "REV_C02",
                        "DECISION_C",
                        "SUBJECT_C",
                        2,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_C01",
                        REF_A);
        expectInvalid(IssueCode.SUPERSEDES_CYCLE, SOURCES, List.of(cycleA, cycleB));

        for (RevisionType relationType :
                List.of(
                        RevisionType.AMENDED,
                        RevisionType.CANCELLED,
                        RevisionType.CONTRADICTED)) {
            expectInvalid(
                    IssueCode.RELATION_REQUIRES_SUPERSEDES,
                    SOURCES,
                    List.of(
                            revision(
                                    "REV_D01",
                                    "DECISION_D",
                                    "SUBJECT_D",
                                    1,
                                    relationType,
                                    Origin.MODEL_ONLY,
                                    false,
                                    null,
                                    REF_A)));
        }
    }

    private static void scalarNullAndLongBoundariesReturnExactIssues() {
        expectInvalidExactly(
                List.of(new SourceFixture(null, 1, HASH_A)),
                List.of(),
                List.of(new ValidationIssue(IssueCode.INVALID_SOURCE_ID, "SOURCE_AT_0000")));

        Revision nullScalars =
                new Revision(
                        null,
                        null,
                        null,
                        1,
                        null,
                        null,
                        false,
                        null,
                        List.of(REF_A));
        expectInvalidExactly(
                SOURCES,
                List.of(nullScalars),
                List.of(
                        new ValidationIssue(
                                IssueCode.INVALID_DECISION_ID, "REVISION_AT_0000"),
                        new ValidationIssue(
                                IssueCode.INVALID_REVISION_ID, "REVISION_AT_0000"),
                        new ValidationIssue(
                                IssueCode.INVALID_SUBJECT_ID, "REVISION_AT_0000"),
                        new ValidationIssue(IssueCode.MISSING_ORIGIN, "REVISION_AT_0000"),
                        new ValidationIssue(
                                IssueCode.MISSING_REVISION_TYPE, "REVISION_AT_0000")));

        Revision nullSourceRefId =
                revision(
                        "REV_NULL_REF",
                        "DECISION_A",
                        "SUBJECT_A",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        new SourceRef(null, 0, 1));
        expectInvalidExactly(
                SOURCES,
                List.of(nullSourceRefId),
                List.of(
                        new ValidationIssue(
                                IssueCode.INVALID_SOURCE_REF_ID, "REV_NULL_REF")));

        SourceFixture maximumSource =
                new SourceFixture("SOURCE_MAX", Long.MAX_VALUE, HASH_B);
        Revision maximumRevision =
                revision(
                        "REV_MAX",
                        "DECISION_MAX",
                        "SUBJECT_MAX",
                        Long.MAX_VALUE,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        new SourceRef("SOURCE_MAX", 0, Long.MAX_VALUE));
        assertProjection(
                DecisionRevisionOracle.evaluate(List.of(maximumSource), List.of(maximumRevision)),
                "DECISION_MAX",
                ProjectionState.CURRENT,
                "REV_MAX",
                List.of("REV_MAX"),
                List.of());

        Revision minimumOrdinal =
                revision(
                        "REV_MIN",
                        "DECISION_A",
                        "SUBJECT_A",
                        Long.MIN_VALUE,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        REF_A);
        expectInvalidExactly(
                SOURCES,
                List.of(minimumOrdinal),
                List.of(new ValidationIssue(IssueCode.NONPOSITIVE_ORDINAL, "REV_MIN")));

        Revision minimumRange =
                revision(
                        "REV_MIN_RANGE",
                        "DECISION_MAX",
                        "SUBJECT_MAX",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        new SourceRef("SOURCE_MAX", Long.MIN_VALUE, Long.MAX_VALUE));
        expectInvalidExactly(
                List.of(maximumSource),
                List.of(minimumRange),
                List.of(
                        new ValidationIssue(
                                IssueCode.INVALID_SOURCE_RANGE, "REV_MIN_RANGE")));
    }

    private static void threeNodeCycleIsPermutationDeterministic() {
        Revision cycleA =
                revision(
                        "REV_C01",
                        "DECISION_C",
                        "SUBJECT_C",
                        1,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_C03",
                        REF_A);
        Revision cycleB =
                revision(
                        "REV_C02",
                        "DECISION_C",
                        "SUBJECT_C",
                        2,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_C01",
                        REF_A);
        Revision cycleC =
                revision(
                        "REV_C03",
                        "DECISION_C",
                        "SUBJECT_C",
                        3,
                        RevisionType.FINAL,
                        Origin.MODEL_ONLY,
                        false,
                        "REV_C02",
                        REF_A);
        List<ValidationIssue> expected =
                List.of(
                        new ValidationIssue(IssueCode.NON_LATER_SUPERSEDES, "REV_C01"),
                        new ValidationIssue(IssueCode.SUPERSEDES_CYCLE, "REV_C01"));
        List<List<Revision>> permutations =
                List.of(
                        List.of(cycleA, cycleB, cycleC),
                        List.of(cycleA, cycleC, cycleB),
                        List.of(cycleB, cycleA, cycleC),
                        List.of(cycleB, cycleC, cycleA),
                        List.of(cycleC, cycleA, cycleB),
                        List.of(cycleC, cycleB, cycleA));
        String canonical = null;
        for (List<Revision> permutation : permutations) {
            EvaluationResult result = expectInvalidExactly(SOURCES, permutation, expected);
            if (canonical == null) {
                canonical = result.canonicalOutput();
            } else {
                check(
                        canonical.equals(result.canonicalOutput()),
                        "three-node cycle permutation changed canonical output");
            }
        }
    }

    private static void nullInputsAndElementsReturnTypedIssues() {
        expectInvalid(IssueCode.NULL_SOURCE_LIST, null, List.of());
        expectInvalid(IssueCode.NULL_REVISION_LIST, SOURCES, null);

        List<SourceFixture> sourcesWithNull = new ArrayList<>();
        sourcesWithNull.add(null);
        expectInvalid(IssueCode.NULL_SOURCE_FIXTURE, sourcesWithNull, List.of());

        List<Revision> revisionsWithNull = new ArrayList<>();
        revisionsWithNull.add(null);
        expectInvalid(IssueCode.NULL_REVISION, SOURCES, revisionsWithNull);
    }

    private static void recordsAndResultsAreImmutable() {
        List<SourceRef> mutableRefs = new ArrayList<>();
        mutableRefs.add(REF_A);
        Revision revision =
                revisionWithRefs(
                        "REV_F01",
                        "DECISION_F",
                        "SUBJECT_F",
                        1,
                        RevisionType.CONFIRMED,
                        Origin.MODEL_ONLY,
                        false,
                        null,
                        mutableRefs);
        mutableRefs.clear();
        check(revision.sourceRefs().size() == 1, "revision did not defensively copy source refs");
        expectUnsupported(() -> revision.sourceRefs().add(REF_B));

        List<Revision> mutableRevisions = new ArrayList<>();
        mutableRevisions.add(revision);
        EvaluationResult result = DecisionRevisionOracle.evaluate(SOURCES, mutableRevisions);
        mutableRevisions.clear();
        assertProjection(
                result,
                "DECISION_F",
                ProjectionState.CURRENT,
                "REV_F01",
                List.of("REV_F01"),
                List.of());
        expectUnsupported(() -> result.projections().clear());
        expectUnsupported(() -> result.issues().add(null));
        expectUnsupported(() -> result.projections().get(0).historyRevisionIds().add("REV_F02"));
        expectUnsupported(() -> result.projections().get(0).reviewRevisionIds().add("REV_F02"));
    }

    private static EvaluationResult evaluate(List<Revision> revisions) {
        return DecisionRevisionOracle.evaluate(SOURCES, revisions);
    }

    private static Revision revision(
            String revisionId,
            String decisionId,
            String subjectId,
            long ordinal,
            RevisionType type,
            Origin origin,
            boolean userRejected,
            String supersedesRevisionId,
            SourceRef sourceRef) {
        return revisionWithRefs(
                revisionId,
                decisionId,
                subjectId,
                ordinal,
                type,
                origin,
                userRejected,
                supersedesRevisionId,
                List.of(sourceRef));
    }

    private static Revision revisionWithRefs(
            String revisionId,
            String decisionId,
            String subjectId,
            long ordinal,
            RevisionType type,
            Origin origin,
            boolean userRejected,
            String supersedesRevisionId,
            List<SourceRef> sourceRefs) {
        return new Revision(
                revisionId,
                decisionId,
                subjectId,
                ordinal,
                type,
                origin,
                userRejected,
                supersedesRevisionId,
                sourceRefs);
    }

    private static void assertProjection(
            EvaluationResult result,
            String decisionId,
            ProjectionState expectedState,
            String expectedCurrentRevisionId,
            List<String> expectedHistory,
            List<String> expectedReview) {
        assertProjectionInBatch(
                result,
                1,
                decisionId,
                expectedState,
                expectedCurrentRevisionId,
                expectedHistory,
                expectedReview);
    }

    private static void assertProjectionInBatch(
            EvaluationResult result,
            int expectedProjectionCount,
            String decisionId,
            ProjectionState expectedState,
            String expectedCurrentRevisionId,
            List<String> expectedHistory,
            List<String> expectedReview) {
        check(result.state() == BatchState.VALID, "expected valid result: " + result.canonicalOutput());
        check(result.issues().equals(List.of()), "valid result must have no issues");
        check(
                result.projections().size() == expectedProjectionCount,
                "projection count mismatch: " + result.canonicalOutput());
        check(decisionId.startsWith("DECISION_"), "test decision ID lacks expected prefix");
        String expectedSubjectId = "SUBJECT_" + decisionId.substring("DECISION_".length());
        DecisionProjection projection =
                result.projections().stream()
                        .filter(candidate -> candidate.decisionId().equals(decisionId))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("missing projection for " + decisionId));
        DecisionProjection expected =
                new DecisionProjection(
                        decisionId,
                        expectedSubjectId,
                        expectedState,
                        expectedCurrentRevisionId,
                        expectedHistory,
                        expectedReview);
        check(
                projection.equals(expected),
                "exact projection mismatch for "
                        + decisionId
                        + ": expected="
                        + expected
                        + ", actual="
                        + projection);
    }

    private static void expectInvalid(
            IssueCode expectedIssue,
            List<SourceFixture> sources,
            List<Revision> revisions) {
        EvaluationResult result = DecisionRevisionOracle.evaluate(sources, revisions);
        assertInvalidShape(result);
        check(
                result.issues().stream().anyMatch(issue -> issue.code() == expectedIssue),
                "missing issue " + expectedIssue + ": " + result.canonicalOutput());
    }

    private static EvaluationResult expectInvalidExactly(
            List<SourceFixture> sources,
            List<Revision> revisions,
            List<ValidationIssue> expectedIssues) {
        EvaluationResult result = DecisionRevisionOracle.evaluate(sources, revisions);
        assertInvalidShape(result);
        check(
                result.issues().equals(expectedIssues),
                "exact issue tuple mismatch: expected="
                        + expectedIssues
                        + ", actual="
                        + result.issues());
        return result;
    }

    private static void assertInvalidShape(EvaluationResult result) {
        check(result.state() == BatchState.INVALID, "expected typed invalid result");
        check(result.projections().isEmpty(), "invalid input must not produce projections");
        List<ValidationIssue> sorted = new ArrayList<>(result.issues());
        sorted.sort(
                java.util.Comparator.comparing(
                                (ValidationIssue issue) -> issue.code().name())
                        .thenComparing(ValidationIssue::entityId));
        check(result.issues().equals(sorted), "invalid issues are not deterministically ordered");
        check(
                result.issues().stream().distinct().count() == result.issues().size(),
                "invalid issues are not deduplicated");
    }

    private static void expectUnsupported(Runnable mutation) {
        try {
            mutation.run();
            throw new AssertionError("mutation unexpectedly succeeded");
        } catch (UnsupportedOperationException expected) {
            // Expected: all list boundaries are immutable snapshots.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
