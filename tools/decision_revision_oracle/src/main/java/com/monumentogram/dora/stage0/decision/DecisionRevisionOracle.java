package com.monumentogram.dora.stage0.decision;

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
 * A Stage 0, host-only structural oracle for a deliberately narrow subset of decision revision
 * projection. This is not a product schema, an extractor, or a semantic source-grounding engine.
 */
public final class DecisionRevisionOracle {
    private static final Pattern OPAQUE_ID = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern LOWERCASE_SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final String ZERO_SHA256 = "0".repeat(64);

    private static final Comparator<Revision> REVISION_ORDER =
            Comparator.comparingLong(Revision::ordinal).thenComparing(Revision::revisionId);
    private static final Comparator<ValidationIssue> ISSUE_ORDER =
            Comparator.comparing((ValidationIssue issue) -> issue.code().name())
                    .thenComparing(ValidationIssue::entityId);

    private DecisionRevisionOracle() {}

    public enum RevisionType {
        PROPOSAL,
        TENTATIVE,
        CONFIRMED,
        FINAL,
        AMENDED,
        CANCELLED,
        CONTRADICTED
    }

    public enum Origin {
        MODEL_ONLY,
        MANUAL_CONFIRMED
    }

    public enum BatchState {
        VALID,
        INVALID
    }

    public enum ProjectionState {
        NO_CURRENT,
        CURRENT,
        CANCELLED,
        CONFLICT_REVIEW,
        REVIEW_REQUIRED,
        MANUAL_CURRENT_REVIEW_REQUIRED
    }

    public enum IssueCode {
        CROSS_DECISION_SUPERSEDES,
        CROSS_SUBJECT_SUPERSEDES,
        DANGLING_SUPERSEDES,
        DECISION_SUBJECT_MISMATCH,
        DUPLICATE_ORDINAL,
        DUPLICATE_REVISION_ID,
        DUPLICATE_SOURCE_ID,
        INVALID_DECISION_ID,
        INVALID_REVISION_ID,
        INVALID_SOURCE_DIGEST,
        INVALID_SOURCE_ID,
        INVALID_SOURCE_RANGE,
        INVALID_SOURCE_REF_ID,
        INVALID_SUBJECT_ID,
        INVALID_SUPERSEDES_ID,
        MISSING_ORIGIN,
        MISSING_REVISION_TYPE,
        MISSING_SOURCE_REFS,
        NON_LATER_SUPERSEDES,
        NONPOSITIVE_ORDINAL,
        NONPOSITIVE_SOURCE_LENGTH,
        NULL_REVISION,
        NULL_REVISION_LIST,
        NULL_SOURCE_FIXTURE,
        NULL_SOURCE_LIST,
        NULL_SOURCE_REF,
        RELATION_REQUIRES_SUPERSEDES,
        SELF_SUPERSEDES,
        SUPERSEDES_CYCLE,
        UNKNOWN_SOURCE_REF,
        ZERO_SOURCE_DIGEST
    }

    /**
     * A content-free synthetic source boundary measured in opaque units. The digest identifies the
     * whole fixture and is checked only for lowercase, nonzero SHA-256 syntax; no fixture bytes or
     * excerpt bytes are accepted by this oracle, so it cannot verify either.
     */
    public record SourceFixture(String sourceId, long lengthUnits, String sha256) {}

    /** A half-open structural range into a declared synthetic source fixture. */
    public record SourceRef(String sourceId, long startInclusive, long endExclusive) {}

    /**
     * An oracle-local revision record. The ordinal is a deterministic per-decision ordering key,
     * not a product timestamp or persisted-schema decision.
     */
    public record Revision(
            String revisionId,
            String decisionId,
            String subjectId,
            long ordinal,
            RevisionType type,
            Origin origin,
            boolean userRejected,
            String supersedesRevisionId,
            List<SourceRef> sourceRefs) {
        public Revision {
            sourceRefs = immutableCopyAllowingNulls(sourceRefs);
        }
    }

    public record ValidationIssue(IssueCode code, String entityId) {}

    private record BranchEvaluation(
            Revision leaf,
            Revision effectiveCandidate,
            Revision protectedManual,
            boolean cancellationLeaf,
            boolean reopenedAfterCancellation,
            boolean reviewRequired) {}

    public record DecisionProjection(
            String decisionId,
            String subjectId,
            ProjectionState state,
            String currentRevisionId,
            List<String> historyRevisionIds,
            List<String> reviewRevisionIds) {
        public DecisionProjection {
            historyRevisionIds = immutableCopyAllowingNulls(historyRevisionIds);
            reviewRevisionIds = immutableCopyAllowingNulls(reviewRevisionIds);
        }
    }

    public record EvaluationResult(
            BatchState state,
            List<DecisionProjection> projections,
            List<ValidationIssue> issues) {
        public EvaluationResult {
            projections = immutableCopyAllowingNulls(projections);
            issues = immutableCopyAllowingNulls(issues);
        }

        /** Returns a deterministic, content-free representation suitable for equality checks. */
        public String canonicalOutput() {
            StringBuilder output = new StringBuilder();
            output.append("batch=").append(state.name()).append('\n');
            for (ValidationIssue issue : issues) {
                output.append("issue=")
                        .append(issue.code().name())
                        .append('|')
                        .append(issue.entityId())
                        .append('\n');
            }
            for (DecisionProjection projection : projections) {
                output.append("projection=")
                        .append(projection.decisionId())
                        .append('|')
                        .append(projection.subjectId())
                        .append('|')
                        .append(projection.state().name())
                        .append("|current=")
                        .append(encodeNullable(projection.currentRevisionId()))
                        .append("|history=")
                        .append(String.join(",", projection.historyRevisionIds()))
                        .append("|review=")
                        .append(String.join(",", projection.reviewRevisionIds()))
                        .append('\n');
            }
            return output.toString();
        }
    }

    private static String encodeNullable(String value) {
        return value == null ? "N" : "S" + value.length() + ":" + value;
    }

    /** Validates the whole input first and projects no decision when any structural issue exists. */
    public static EvaluationResult evaluate(
            List<SourceFixture> sourceFixtures, List<Revision> revisions) {
        List<SourceFixture> sourceSnapshot = immutableCopyAllowingNulls(sourceFixtures);
        List<Revision> revisionSnapshot = immutableCopyAllowingNulls(revisions);
        List<ValidationIssue> issues = new ArrayList<>();

        Map<String, SourceFixture> sourcesById = validateSources(sourceSnapshot, issues);
        validateRevisions(revisionSnapshot, sourcesById, issues);

        List<ValidationIssue> normalizedIssues = normalizeIssues(issues);
        if (!normalizedIssues.isEmpty()) {
            return new EvaluationResult(BatchState.INVALID, List.of(), normalizedIssues);
        }

        Map<String, List<Revision>> byDecision = new TreeMap<>();
        for (Revision revision : revisionSnapshot) {
            byDecision.computeIfAbsent(revision.decisionId(), ignored -> new ArrayList<>())
                    .add(revision);
        }

        List<DecisionProjection> projections = new ArrayList<>();
        for (Map.Entry<String, List<Revision>> entry : byDecision.entrySet()) {
            List<Revision> decisionRevisions = new ArrayList<>(entry.getValue());
            decisionRevisions.sort(REVISION_ORDER);
            projections.add(projectDecision(decisionRevisions));
        }
        return new EvaluationResult(BatchState.VALID, projections, List.of());
    }

    private static Map<String, SourceFixture> validateSources(
            List<SourceFixture> sources, List<ValidationIssue> issues) {
        Map<String, SourceFixture> sourcesById = new TreeMap<>();
        if (sources == null) {
            issues.add(issue(IssueCode.NULL_SOURCE_LIST, "SOURCE_LIST"));
            return sourcesById;
        }

        for (int index = 0; index < sources.size(); index++) {
            SourceFixture source = sources.get(index);
            String label = sourceLabel(index, source);
            if (source == null) {
                issues.add(issue(IssueCode.NULL_SOURCE_FIXTURE, label));
                continue;
            }
            if (!isOpaqueId(source.sourceId())) {
                issues.add(issue(IssueCode.INVALID_SOURCE_ID, label));
            } else if (sourcesById.putIfAbsent(source.sourceId(), source) != null) {
                issues.add(issue(IssueCode.DUPLICATE_SOURCE_ID, label));
            }
            if (source.lengthUnits() <= 0) {
                issues.add(issue(IssueCode.NONPOSITIVE_SOURCE_LENGTH, label));
            }
            if (!isLowercaseSha256(source.sha256())) {
                issues.add(issue(IssueCode.INVALID_SOURCE_DIGEST, label));
            } else if (ZERO_SHA256.equals(source.sha256())) {
                issues.add(issue(IssueCode.ZERO_SOURCE_DIGEST, label));
            }
        }
        return sourcesById;
    }

    private static Map<String, Revision> validateRevisions(
            List<Revision> revisions,
            Map<String, SourceFixture> sourcesById,
            List<ValidationIssue> issues) {
        Map<String, Revision> revisionsById = new TreeMap<>();
        Set<String> ordinalKeys = new HashSet<>();
        Map<String, String> subjectByDecision = new HashMap<>();

        if (revisions == null) {
            issues.add(issue(IssueCode.NULL_REVISION_LIST, "REVISION_LIST"));
            return revisionsById;
        }

        for (int index = 0; index < revisions.size(); index++) {
            Revision revision = revisions.get(index);
            String label = revisionLabel(index, revision);
            if (revision == null) {
                issues.add(issue(IssueCode.NULL_REVISION, label));
                continue;
            }

            boolean revisionIdValid = isOpaqueId(revision.revisionId());
            boolean decisionIdValid = isOpaqueId(revision.decisionId());
            boolean subjectIdValid = isOpaqueId(revision.subjectId());
            if (!revisionIdValid) {
                issues.add(issue(IssueCode.INVALID_REVISION_ID, label));
            } else if (revisionsById.putIfAbsent(revision.revisionId(), revision) != null) {
                issues.add(issue(IssueCode.DUPLICATE_REVISION_ID, label));
            }
            if (!decisionIdValid) {
                issues.add(issue(IssueCode.INVALID_DECISION_ID, label));
            }
            if (!subjectIdValid) {
                issues.add(issue(IssueCode.INVALID_SUBJECT_ID, label));
            }
            if (revision.ordinal() <= 0) {
                issues.add(issue(IssueCode.NONPOSITIVE_ORDINAL, label));
            } else if (decisionIdValid
                    && !ordinalKeys.add(revision.decisionId() + "\u0000" + revision.ordinal())) {
                issues.add(issue(IssueCode.DUPLICATE_ORDINAL, label));
            }
            if (revision.type() == null) {
                issues.add(issue(IssueCode.MISSING_REVISION_TYPE, label));
            }
            if (revision.origin() == null) {
                issues.add(issue(IssueCode.MISSING_ORIGIN, label));
            }

            if (decisionIdValid && subjectIdValid) {
                String previousSubject =
                        subjectByDecision.putIfAbsent(revision.decisionId(), revision.subjectId());
                if (previousSubject != null && !previousSubject.equals(revision.subjectId())) {
                    issues.add(issue(IssueCode.DECISION_SUBJECT_MISMATCH, label));
                }
            }

            validateSourceRefs(revision, label, sourcesById, issues);
            if (requiresExplicitRelation(revision.type())
                    && !isOpaqueId(revision.supersedesRevisionId())) {
                issues.add(issue(IssueCode.RELATION_REQUIRES_SUPERSEDES, label));
            }
        }

        validateSupersedes(revisions, revisionsById, issues);
        detectCycles(revisionsById, issues);
        return revisionsById;
    }

    private static void validateSourceRefs(
            Revision revision,
            String label,
            Map<String, SourceFixture> sourcesById,
            List<ValidationIssue> issues) {
        List<SourceRef> sourceRefs = revision.sourceRefs();
        if (sourceRefs == null || sourceRefs.isEmpty()) {
            issues.add(issue(IssueCode.MISSING_SOURCE_REFS, label));
            return;
        }

        for (SourceRef sourceRef : sourceRefs) {
            if (sourceRef == null) {
                issues.add(issue(IssueCode.NULL_SOURCE_REF, label));
                continue;
            }
            if (!isOpaqueId(sourceRef.sourceId())) {
                issues.add(issue(IssueCode.INVALID_SOURCE_REF_ID, label));
                continue;
            }
            SourceFixture fixture = sourcesById.get(sourceRef.sourceId());
            if (fixture == null) {
                issues.add(issue(IssueCode.UNKNOWN_SOURCE_REF, label));
                continue;
            }
            if (sourceRef.startInclusive() < 0
                    || sourceRef.startInclusive() >= sourceRef.endExclusive()
                    || sourceRef.endExclusive() > fixture.lengthUnits()) {
                issues.add(issue(IssueCode.INVALID_SOURCE_RANGE, label));
            }
        }
    }

    private static void validateSupersedes(
            List<Revision> revisions,
            Map<String, Revision> revisionsById,
            List<ValidationIssue> issues) {
        for (int index = 0; index < revisions.size(); index++) {
            Revision revision = revisions.get(index);
            if (revision == null || revision.supersedesRevisionId() == null) {
                continue;
            }
            String label = revisionLabel(index, revision);
            if (!isOpaqueId(revision.supersedesRevisionId())) {
                issues.add(issue(IssueCode.INVALID_SUPERSEDES_ID, label));
                continue;
            }
            if (revision.supersedesRevisionId().equals(revision.revisionId())) {
                issues.add(issue(IssueCode.SELF_SUPERSEDES, label));
                continue;
            }
            Revision parent = revisionsById.get(revision.supersedesRevisionId());
            if (parent == null) {
                issues.add(issue(IssueCode.DANGLING_SUPERSEDES, label));
                continue;
            }
            if (!Objects.equals(revision.decisionId(), parent.decisionId())) {
                issues.add(issue(IssueCode.CROSS_DECISION_SUPERSEDES, label));
            }
            if (!Objects.equals(revision.subjectId(), parent.subjectId())) {
                issues.add(issue(IssueCode.CROSS_SUBJECT_SUPERSEDES, label));
            }
            if (revision.ordinal() <= parent.ordinal()) {
                issues.add(issue(IssueCode.NON_LATER_SUPERSEDES, label));
            }
        }
    }

    private static void detectCycles(
            Map<String, Revision> revisionsById, List<ValidationIssue> issues) {
        Set<String> completed = new HashSet<>();
        for (String startId : revisionsById.keySet()) {
            if (completed.contains(startId)) {
                continue;
            }
            List<String> path = new ArrayList<>();
            Map<String, Integer> position = new LinkedHashMap<>();
            String currentId = startId;
            while (currentId != null
                    && revisionsById.containsKey(currentId)
                    && !completed.contains(currentId)) {
                Integer cycleStart = position.get(currentId);
                if (cycleStart != null) {
                    String cycleLabel =
                            path.subList(cycleStart, path.size()).stream()
                                    .min(String::compareTo)
                                    .orElse(currentId);
                    issues.add(issue(IssueCode.SUPERSEDES_CYCLE, cycleLabel));
                    break;
                }
                position.put(currentId, path.size());
                path.add(currentId);
                currentId = revisionsById.get(currentId).supersedesRevisionId();
            }
            completed.addAll(path);
        }
    }

    private static DecisionProjection projectDecision(List<Revision> revisions) {
        String decisionId = revisions.get(0).decisionId();
        String subjectId = revisions.get(0).subjectId();
        List<String> history = revisions.stream().map(Revision::revisionId).toList();
        List<Revision> active = revisions.stream().filter(revision -> !revision.userRejected()).toList();
        if (active.isEmpty()) {
            return projection(
                    decisionId,
                    subjectId,
                    ProjectionState.NO_CURRENT,
                    null,
                    history,
                    List.of());
        }

        Map<String, Revision> activeById = new TreeMap<>();
        Set<String> parentsWithActiveChildren = new HashSet<>();
        for (Revision revision : active) {
            activeById.put(revision.revisionId(), revision);
        }
        for (Revision revision : active) {
            if (revision.supersedesRevisionId() != null
                    && activeById.containsKey(revision.supersedesRevisionId())) {
                parentsWithActiveChildren.add(revision.supersedesRevisionId());
            }
        }
        List<Revision> leaves =
                active.stream()
                        .filter(revision -> !parentsWithActiveChildren.contains(revision.revisionId()))
                        .sorted(REVISION_ORDER)
                        .toList();

        List<BranchEvaluation> branches =
                leaves.stream().map(leaf -> evaluateBranch(leaf, activeById)).toList();
        boolean hasCancellationBoundary =
                branches.stream()
                        .anyMatch(
                                branch ->
                                        branch.cancellationLeaf()
                                                || branch.reopenedAfterCancellation());
        if (hasCancellationBoundary) {
            return projectAcrossCancellationBoundary(
                    decisionId, subjectId, history, leaves, branches, activeById);
        }

        TreeMap<String, Revision> distinctCandidates = new TreeMap<>();
        for (BranchEvaluation branch : branches) {
            if (branch.effectiveCandidate() != null) {
                distinctCandidates.put(
                        branch.effectiveCandidate().revisionId(), branch.effectiveCandidate());
            }
        }
        if (distinctCandidates.size() > 1
                && !allCandidatesRelated(
                        List.copyOf(distinctCandidates.values()), activeById)) {
            return projection(
                    decisionId,
                    subjectId,
                    ProjectionState.CONFLICT_REVIEW,
                    null,
                    history,
                    ids(leaves));
        }

        Revision effective =
                selectPolicyCandidate(List.copyOf(distinctCandidates.values()));
        boolean reviewRequired =
                leaves.size() > 1
                        || branches.stream().anyMatch(BranchEvaluation::reviewRequired)
                        || distinctCandidates.size() > 1;
        if (effective == null) {
            return projection(
                    decisionId,
                    subjectId,
                    ProjectionState.REVIEW_REQUIRED,
                    null,
                    history,
                    ids(leaves));
        }
        if (!reviewRequired
                && leaves.size() == 1
                && leaves.get(0).revisionId().equals(effective.revisionId())) {
            return projection(
                    decisionId,
                    subjectId,
                    ProjectionState.CURRENT,
                    effective.revisionId(),
                    history,
                    List.of());
        }
        return projection(
                decisionId,
                subjectId,
                effective.origin() == Origin.MANUAL_CONFIRMED
                        ? ProjectionState.MANUAL_CURRENT_REVIEW_REQUIRED
                        : ProjectionState.REVIEW_REQUIRED,
                effective.revisionId(),
                history,
                reviewIdsExcluding(leaves, effective.revisionId()));
    }

    private static BranchEvaluation evaluateBranch(
            Revision leaf, Map<String, Revision> activeById) {
        // Cancellation is a hard oracle boundary: descendants cannot reactivate an ancestor.
        Revision cancellation = oldestCancellationOnPath(leaf, activeById);
        if (cancellation != null) {
            Revision priorCandidate =
                    selectPolicyCandidate(authoritativeBefore(cancellation, activeById));
            Revision protectedManual =
                    cancellation.origin() == Origin.MODEL_ONLY
                                    && priorCandidate != null
                                    && priorCandidate.origin() == Origin.MANUAL_CONFIRMED
                            ? priorCandidate
                            : null;
            return new BranchEvaluation(
                    leaf,
                    null,
                    protectedManual,
                    leaf.revisionId().equals(cancellation.revisionId()),
                    !leaf.revisionId().equals(cancellation.revisionId()),
                    true);
        }

        Revision effective =
                selectPolicyCandidate(authoritativeFromLeaf(leaf, activeById));
        return new BranchEvaluation(
                leaf,
                effective,
                null,
                false,
                false,
                effective == null || !leaf.revisionId().equals(effective.revisionId()));
    }

    private static DecisionProjection projectAcrossCancellationBoundary(
            String decisionId,
            String subjectId,
            List<String> history,
            List<Revision> leaves,
            List<BranchEvaluation> branches,
            Map<String, Revision> activeById) {
        if (branches.size() == 1) {
            BranchEvaluation branch = branches.get(0);
            if (branch.protectedManual() != null) {
                return projection(
                        decisionId,
                        subjectId,
                        ProjectionState.MANUAL_CURRENT_REVIEW_REQUIRED,
                        branch.protectedManual().revisionId(),
                        history,
                        ids(leaves));
            }
            if (branch.cancellationLeaf()) {
                Revision priorCandidate =
                        selectPolicyCandidate(
                                authoritativeBefore(branch.leaf(), activeById));
                if (priorCandidate != null) {
                    return projection(
                            decisionId,
                            subjectId,
                            ProjectionState.CANCELLED,
                            null,
                            history,
                            List.of());
                }
            }
            return projection(
                    decisionId,
                    subjectId,
                    ProjectionState.REVIEW_REQUIRED,
                    null,
                    history,
                    ids(leaves));
        }

        TreeMap<String, Revision> protectedManuals = new TreeMap<>();
        TreeMap<String, Revision> otherManualCandidates = new TreeMap<>();
        for (BranchEvaluation branch : branches) {
            if (branch.protectedManual() != null) {
                protectedManuals.put(
                        branch.protectedManual().revisionId(), branch.protectedManual());
            }
            if (branch.effectiveCandidate() != null
                    && branch.effectiveCandidate().origin() == Origin.MANUAL_CONFIRMED) {
                otherManualCandidates.put(
                        branch.effectiveCandidate().revisionId(), branch.effectiveCandidate());
            }
        }
        if (protectedManuals.size() == 1) {
            Revision protectedManual = protectedManuals.firstEntry().getValue();
            boolean manualMismatch =
                    otherManualCandidates.values().stream()
                            .anyMatch(
                                    candidate ->
                                            !candidate.revisionId()
                                                    .equals(protectedManual.revisionId()));
            if (!manualMismatch) {
                return projection(
                        decisionId,
                        subjectId,
                        ProjectionState.MANUAL_CURRENT_REVIEW_REQUIRED,
                        protectedManual.revisionId(),
                        history,
                        reviewIdsExcluding(leaves, protectedManual.revisionId()));
            }
        }
        return projection(
                decisionId,
                subjectId,
                ProjectionState.CONFLICT_REVIEW,
                null,
                history,
                ids(leaves));
    }

    private static Revision oldestCancellationOnPath(
            Revision leaf, Map<String, Revision> activeById) {
        Revision current = leaf;
        Revision oldestCancellation = null;
        while (current != null) {
            if (current.type() == RevisionType.CANCELLED) {
                oldestCancellation = current;
            }
            String parentId = current.supersedesRevisionId();
            current = parentId == null ? null : activeById.get(parentId);
        }
        return oldestCancellation;
    }

    private static List<Revision> authoritativeFromLeaf(
            Revision leaf, Map<String, Revision> activeById) {
        List<Revision> candidates = new ArrayList<>();
        Revision current = leaf;
        while (current != null && current.type() != RevisionType.CANCELLED) {
            if (isAuthoritative(current)) {
                candidates.add(current);
            }
            String parentId = current.supersedesRevisionId();
            current = parentId == null ? null : activeById.get(parentId);
        }
        return Collections.unmodifiableList(candidates);
    }

    private static List<Revision> authoritativeBefore(
            Revision cancellation, Map<String, Revision> activeById) {
        String parentId = cancellation.supersedesRevisionId();
        if (parentId == null) {
            return List.of();
        }
        Revision parent = activeById.get(parentId);
        return parent == null ? List.of() : authoritativeFromLeaf(parent, activeById);
    }

    private static Revision selectPolicyCandidate(List<Revision> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        // Conservative oracle-local ordering only; it does not define product demotion semantics.
        List<Revision> manual =
                candidates.stream()
                        .filter(candidate -> candidate.origin() == Origin.MANUAL_CONFIRMED)
                        .toList();
        List<Revision> pool = manual.isEmpty() ? candidates : manual;
        return pool.stream()
                .max(
                        Comparator.comparingInt(DecisionRevisionOracle::authorityRank)
                                .thenComparingLong(Revision::ordinal)
                                .thenComparing(Revision::revisionId))
                .orElseThrow();
    }

    private static boolean allCandidatesRelated(
            List<Revision> candidates, Map<String, Revision> activeById) {
        for (int left = 0; left < candidates.size(); left++) {
            for (int right = left + 1; right < candidates.size(); right++) {
                Revision first = candidates.get(left);
                Revision second = candidates.get(right);
                if (!isAncestor(first.revisionId(), second, activeById)
                        && !isAncestor(second.revisionId(), first, activeById)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isAncestor(
            String ancestorRevisionId,
            Revision possibleDescendant,
            Map<String, Revision> activeById) {
        String parentId = possibleDescendant.supersedesRevisionId();
        while (parentId != null) {
            if (ancestorRevisionId.equals(parentId)) {
                return true;
            }
            Revision parent = activeById.get(parentId);
            if (parent == null) {
                return false;
            }
            parentId = parent.supersedesRevisionId();
        }
        return false;
    }

    private static boolean isAuthoritative(Revision revision) {
        return revision.type() == RevisionType.CONFIRMED || revision.type() == RevisionType.FINAL;
    }

    private static int authorityRank(Revision revision) {
        return revision.type() == RevisionType.FINAL ? 2 : 1;
    }

    private static boolean requiresExplicitRelation(RevisionType type) {
        return type == RevisionType.AMENDED
                || type == RevisionType.CANCELLED
                || type == RevisionType.CONTRADICTED;
    }

    private static DecisionProjection projection(
            String decisionId,
            String subjectId,
            ProjectionState state,
            String currentRevisionId,
            List<String> history,
            List<String> review) {
        return new DecisionProjection(
                decisionId, subjectId, state, currentRevisionId, history, review);
    }

    private static List<String> ids(List<Revision> revisions) {
        return revisions.stream().sorted(REVISION_ORDER).map(Revision::revisionId).toList();
    }

    private static List<String> reviewIdsExcluding(
            List<Revision> revisions, String excludedRevisionId) {
        return revisions.stream()
                .filter(revision -> !revision.revisionId().equals(excludedRevisionId))
                .sorted(REVISION_ORDER)
                .map(Revision::revisionId)
                .toList();
    }

    private static List<ValidationIssue> normalizeIssues(List<ValidationIssue> issues) {
        List<ValidationIssue> sorted = new ArrayList<>(issues);
        sorted.sort(ISSUE_ORDER);
        List<ValidationIssue> normalized = new ArrayList<>();
        ValidationIssue previous = null;
        for (ValidationIssue issue : sorted) {
            if (!issue.equals(previous)) {
                normalized.add(issue);
                previous = issue;
            }
        }
        return Collections.unmodifiableList(normalized);
    }

    private static ValidationIssue issue(IssueCode code, String entityId) {
        return new ValidationIssue(code, entityId);
    }

    private static boolean isOpaqueId(String value) {
        return value != null && OPAQUE_ID.matcher(value).matches();
    }

    private static boolean isLowercaseSha256(String value) {
        return value != null && LOWERCASE_SHA256.matcher(value).matches();
    }

    private static String sourceLabel(int index, SourceFixture source) {
        if (source != null && isOpaqueId(source.sourceId())) {
            return source.sourceId();
        }
        return indexedLabel("SOURCE_AT_", index);
    }

    private static String revisionLabel(int index, Revision revision) {
        if (revision != null && isOpaqueId(revision.revisionId())) {
            return revision.revisionId();
        }
        return indexedLabel("REVISION_AT_", index);
    }

    private static String indexedLabel(String prefix, int index) {
        String digits = Integer.toString(index);
        return prefix + "0".repeat(Math.max(0, 4 - digits.length())) + digits;
    }

    private static <T> List<T> immutableCopyAllowingNulls(List<T> values) {
        if (values == null) {
            return null;
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
