package com.monumentogram.dora.stage0.offline.i1;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure, deterministic Stage-0 oracle for the frozen Offline I1 semantic packet.
 *
 * <p>This class deliberately has no Android, filesystem, environment, process, network, thread,
 * wall-clock, random, or user-content surface. It is evidence scaffolding, not product code.
 */
public final class OfflineI1Oracle {
  static final String CONTRACT_ID = "poc-offline-readiness-stage0-v0.1";
  static final String SNAPSHOT_SCHEMA = "poc-offline-i1-snapshot-v0.2";
  static final int INITIAL_AUTOMATIC_ATTEMPT_BUDGET = 3;
  static final int MAX_SNAPSHOT_BYTES = 1_048_576;
  static final int MAX_JSON_DEPTH = 32;
  private static final Pattern OPAQUE_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
  private static final Pattern HASH_64 = Pattern.compile("^[0-9a-f]{64}$");

  private OfflineI1Oracle() {}

  public enum LocalState {
    FRESH_LOCAL_DEFAULT,
    LOCAL_READY,
    LOCAL_OPERATION_RUNNING,
    LOCAL_OPERATION_SUCCEEDED,
    LOCAL_OPERATION_FAILED_SCOPED
  }

  public enum ProcessingState {
    PROCESSING_NOT_REQUESTED,
    PROCESSING_QUEUED,
    PROCESSING_ACTIVE,
    WAITING_MODEL,
    PENDING_CAPABILITY,
    PROCESSING_SUCCEEDED,
    PROCESSING_FAILED_SCOPED
  }

  public enum ConnectivityState {
    NETWORK_DENIED,
    AIRPLANE_MODE,
    AVAILABLE,
    RECONNECTING
  }

  public enum ModelState {
    MODEL_NOT_INSTALLED,
    MODEL_INSTALLED_APPROVED,
    MODEL_UNAVAILABLE_OR_INVALID
  }

  public enum QueueState {
    LOCAL_ONLY,
    PENDING_UPLOAD,
    WAITING_NETWORK,
    UPLOADING,
    REMOTE_PROCESSING,
    RESULT_AVAILABLE,
    APPLIED,
    DELETE_PENDING,
    DELETED_REMOTE,
    CONFLICT,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    CANCELLED
  }

  public enum OperationClass {
    NON_DELETION_CLOUD_INTENT,
    DELETE_CLOUD_COPY
  }

  public enum DeletionSubstatus {
    DELETE_RECEIPT_POLL_ELIGIBLE,
    DELETE_WAITING_NETWORK,
    DELETE_RETRY_SCHEDULED,
    DELETE_REVALIDATION_REQUIRED,
    DELETE_USER_ACTION_REQUIRED,
    DELETE_MANUAL_RETRY_REQUIRED
  }

  public enum DeletionError {
    CANCEL_NOT_APPLICABLE_DELETE_PENDING,
    DELETE_PROFILE_REVALIDATION_REQUIRED,
    DELETE_TLS_TRUST_OR_NAME_REJECTED,
    DELETE_SCHEMA_OR_FORMAT_REJECTED,
    DELETE_RESPONSE_INTEGRITY_REJECTED,
    DELETE_IDEMPOTENCY_PAYLOAD_MISMATCH,
    DELETE_SCOPE_REVALIDATION_REQUIRED,
    DELETE_FINITE_BUDGET_EXHAUSTED
  }

  public enum ReceiptOutcome {
    VERIFIED
  }

  public enum ReplayMarker {
    ORIGINAL,
    SAME_INPUT_REPLAY,
    DIFFERENT_INPUT_REJECTED
  }

  public enum ReducerOutcome {
    TRANSITION_APPLIED,
    NO_STATE_CHANGE,
    REJECTED_NO_STATE_CHANGE,
    INVALID_INPUT
  }

  public enum Diagnostic {
    NONE("NONE"),
    NO_ELIGIBLE_RULE("NO_ELIGIBLE_RULE"),
    IDEMPOTENCY_INPUT_MISMATCH("IDEMPOTENCY_INPUT_MISMATCH"),
    POLICY_REJECTED("POLICY_REJECTED"),
    INVALID_001("OFF-I1-INVALID-001"),
    INVALID_002("OFF-I1-INVALID-002"),
    INVALID_003("OFF-I1-INVALID-003"),
    INVALID_004("OFF-I1-INVALID-004"),
    INVALID_005("OFF-I1-INVALID-005"),
    INVALID_006("OFF-I1-INVALID-006"),
    INVALID_007("OFF-I1-INVALID-007"),
    INVALID_008("OFF-I1-INVALID-008"),
    INVALID_009("OFF-I1-INVALID-009"),
    INVALID_010("OFF-I1-INVALID-010"),
    INVALID_011("OFF-I1-INVALID-011"),
    INVALID_012("OFF-I1-INVALID-012"),
    INVALID_013("OFF-I1-INVALID-013"),
    INVALID_014("OFF-I1-INVALID-014"),
    INVALID_015("OFF-I1-INVALID-015"),
    INVALID_016("OFF-I1-INVALID-016");

    private final String code;

    Diagnostic(String code) {
      this.code = code;
    }

    public String code() {
      return code;
    }

    static Diagnostic fromCode(String code) {
      for (Diagnostic value : values()) {
        if (value.code.equals(code)) {
          return value;
        }
      }
      throw fault(Diagnostic.INVALID_014);
    }
  }

  public enum Consent {
    CURRENT,
    MISSING,
    REVOKED,
    SCOPE_MISMATCH,
    VERSION_MISMATCH,
    NOT_APPLICABLE_DELETE
  }

  public enum Profile {
    CURRENT,
    MISSING,
    CHANGED,
    SCOPE_MISMATCH
  }

  public enum RuntimeStatus {
    NOT_REQUIRED,
    ELIGIBLE,
    MODEL_NOT_INSTALLED,
    MODEL_UNAVAILABLE_OR_INVALID,
    ARTIFACT_NOT_EVALUATION_APPROVED,
    DIGEST_MISMATCH,
    API_ABI_INCOMPATIBLE,
    REQUIRED_16K_EVIDENCE_MISSING
  }

  public enum ProcessingRequirement {
    NONE,
    REQUIRED_MODEL,
    OPTIONAL_CAPABILITY
  }

  public enum ExplicitGrant {
    NONE,
    ELIGIBLE_EXPLICIT_GRANT
  }

  public enum RevalidationOutcome {
    INVALID_INPUT,
    NO_STATE_CHANGE,
    ALLOW,
    CANCEL_NON_DELETION_CONSENT_INVALID,
    BLOCK_PROFILE_REVALIDATION_REQUIRED,
    BLOCK_RUNTIME_REVALIDATION_REQUIRED,
    BLOCK_CONNECTIVITY_DENIED,
    BLOCK_FINITE_BUDGET_EXHAUSTED,
    WAIT_REQUIRED_MODEL,
    WAIT_OPTIONAL_CAPABILITY,
    ALLOW_DELETE_CONSENT_NOT_APPLICABLE
  }

  public enum DeletionPhase {
    NOT_APPLICABLE,
    PRE_ACCEPTANCE,
    POST_ACCEPTANCE,
    VERIFIED
  }

  public enum InputVariant {
    PRIMARY,
    ALTERNATE
  }

  /** Typed, content-free queue facts. Multiple facts may be present for priority arbitration. */
  public enum QueueSignal {
    CONSENT_INVALID_OR_REVOKED,
    SAME_KEY_DIFFERENT_CANONICAL_INPUT,
    PROFILE_OR_RUNTIME_BLOCKED,
    CONNECTIVITY_DENIED,
    CONNECTIVITY_LOST,
    VALID_CANCEL_OR_REVOKE,
    NEW_LOGICAL_KEY,
    SCHEDULER_TICK_WHILE_DENIED,
    ALL_REVALIDATION_ALLOW,
    SEND_AVAILABLE,
    FIRST_DURABLE_ACCEPTANCE,
    VALID_RESULT,
    USER_TRUTH_ALLOW,
    USER_TRUTH_BLOCKS,
    RETRYABLE_ERROR,
    RESUME_OR_MANUAL_RETRY,
    TERMINAL_ERROR,
    SAME_INPUT_REPLAY,
    DUPLICATE_TRIGGER
  }

  public record StateVector(
      LocalState local,
      ProcessingState processingCapability,
      ConnectivityState connectivity,
      ModelState model,
      QueueState queue) {
    public StateVector {
      requirePresent(local);
      requirePresent(processingCapability);
      requirePresent(connectivity);
      requirePresent(model);
      requirePresent(queue);
    }

    StateVector withLocal(LocalState value) {
      return new StateVector(value, processingCapability, connectivity, model, queue);
    }

    StateVector withProcessing(ProcessingState value) {
      return new StateVector(local, value, connectivity, model, queue);
    }

    StateVector withQueue(QueueState value) {
      return new StateVector(local, processingCapability, connectivity, model, value);
    }
  }

  public record AttemptBudget(long attemptCount, long manualRetryGrantCount) {
    public AttemptBudget {
      if (attemptCount < 0 || manualRetryGrantCount < 0) {
        throw fault(Diagnostic.INVALID_014);
      }
      long total = checkedAdd(INITIAL_AUTOMATIC_ATTEMPT_BUDGET, manualRetryGrantCount);
      if (attemptCount > total) {
        throw fault(Diagnostic.INVALID_008);
      }
    }

    public long remaining() {
      return checkedSubtract(checkedAdd(INITIAL_AUTOMATIC_ATTEMPT_BUDGET, manualRetryGrantCount), attemptCount);
    }

    AttemptBudget attempt() {
      if (remaining() <= 0) {
        throw fault(Diagnostic.INVALID_008);
      }
      return new AttemptBudget(checkedAdd(attemptCount, 1), manualRetryGrantCount);
    }

    AttemptBudget grant() {
      if (remaining() != 0) {
        throw fault(Diagnostic.INVALID_015);
      }
      return new AttemptBudget(attemptCount, checkedAdd(manualRetryGrantCount, 1));
    }

    AttemptBudget grantThenAttempt() {
      return grant().attempt();
    }
  }

  record QueueRule(
      String id,
      int priority,
      int secondaryOrder,
      String event,
      String source,
      String guard,
      String target,
      ReducerOutcome outcome,
      boolean queueAffecting,
      String counterDeltas,
      boolean flowLedgerAppend,
      boolean queueLedgerAppend) {
    public QueueRule {
      requireRuleId(id);
      if (priority < 0 || secondaryOrder != 0) {
        throw fault(Diagnostic.INVALID_014);
      }
      requirePresent(event);
      requirePresent(source);
      requirePresent(guard);
      requirePresent(target);
      requirePresent(outcome);
      requirePresent(counterDeltas);
    }
  }

  record ActionSpec(
      String scenarioId,
      String actionId,
      int ordinal,
      String eventId,
      String selectedRuleId,
      ReducerOutcome outcome,
      String projectionOutcome,
      String transition,
      boolean queueAffecting,
      boolean flowLedgerAppend,
      boolean queueLedgerAppend,
      boolean template,
      int priority,
      int secondaryOrder) {
    public ActionSpec {
      requireScenarioId(scenarioId);
      requireActionId(actionId);
      requireEventId(eventId);
      if (selectedRuleId == null) {
        throw fault(Diagnostic.INVALID_003);
      }
      if (ordinal <= 0 || priority < 0 || secondaryOrder < 0) {
        throw fault(Diagnostic.INVALID_014);
      }
      String suffix = scenarioId.substring("OFF-SYN-".length()) + "-" + two(ordinal);
      if (!actionId.equals("OFF-I1-ACT-" + suffix)
          || !eventId.equals("OFF-I1-EVT-" + suffix)) {
        throw fault(Diagnostic.INVALID_003);
      }
      if (template) {
        String expected =
            ordinal == 1
                ? "RESOLVE_SCRIPTED_DELETION_DISPATCH_RULE"
                : "RESOLVE_SCRIPTED_DELETION_RECONCILE_RULE";
        if (!scenarioId.equals("OFF-SYN-026") || !selectedRuleId.equals(expected)) {
          throw fault(Diagnostic.INVALID_003);
        }
      } else {
        requireRuleId(selectedRuleId);
        NonDirect expectedNonDirect = nonDirect(actionId);
        String expectedRule =
            expectedNonDirect == null
                ? "OFF-I1-RULE-A-" + suffix
                : expectedNonDirect.ruleId;
        if (!selectedRuleId.equals(expectedRule)) {
          throw fault(Diagnostic.INVALID_003);
        }
      }
      requirePresent(outcome);
      requirePresent(projectionOutcome);
      String expectedProjectionOutcome =
          template && ordinal == 2 ? "RESOLVE_BY_SUBCASE" : outcome.name();
      if (!projectionOutcome.equals(expectedProjectionOutcome)) {
        throw fault(Diagnostic.INVALID_003);
      }
      requirePresent(transition);
    }
  }

  public record DeletionRow(
      int ordinal,
      String eventOutcomeClass,
      QueueState queueState,
      DeletionSubstatus deletionSubstatus,
      boolean preserveCurrentSubstatus,
      String deletionIdOutcome,
      DeletionError error,
      ReceiptOutcome receiptOutcome,
      int dispatchAttemptDelta,
      int dispatchGrantDelta,
      int effectDelta,
      int remoteDeletionEffectDelta) {
    public DeletionRow {
      if (ordinal < 1 || ordinal > 15) {
        throw fault(Diagnostic.INVALID_014);
      }
      requirePresent(eventOutcomeClass);
      requirePresent(queueState);
      requirePresent(deletionIdOutcome);
      if ((dispatchAttemptDelta != 0 && dispatchAttemptDelta != 1)
          || (dispatchGrantDelta != 0 && dispatchGrantDelta != 1)
          || (effectDelta != 0 && effectDelta != 1)
          || (remoteDeletionEffectDelta != 0 && remoteDeletionEffectDelta != 1)) {
        throw fault(Diagnostic.INVALID_014);
      }
    }
  }

  public record QueueRow(
      OperationClass operationClass,
      String intentIdHash,
      String logicalKeyHash,
      String jobIdHash,
      String resultIdHash,
      String deletionScopeDigest,
      String deletionIdHash,
      String deletionReceiptIdHash,
      QueueState queueState,
      DeletionSubstatus deletionSubstatus,
      DeletionError contentFreeDeletionErrorCode,
      ReceiptOutcome deletionReceiptVerificationOutcome,
      long attemptCount,
      ReplayMarker replayMarker,
      int effectCount,
      int applyCount,
      int remoteDeletionEffectCount,
      String preLocalStateDigest,
      String postLocalStateDigest) {
    public QueueRow {
      requireHashFor(preLocalStateDigest, Diagnostic.INVALID_010);
      requireHashFor(postLocalStateDigest, Diagnostic.INVALID_010);
      requireNullableHashFor(deletionScopeDigest, Diagnostic.INVALID_011);
      requireNullableHashFor(deletionIdHash, Diagnostic.INVALID_011);
      requireNullableHashFor(deletionReceiptIdHash, Diagnostic.INVALID_011);
      if (operationClass == OperationClass.DELETE_CLOUD_COPY) {
        requireBitFor(effectCount, Diagnostic.INVALID_011);
        requireBitFor(applyCount, Diagnostic.INVALID_011);
        requireBitFor(remoteDeletionEffectCount, Diagnostic.INVALID_011);
      }
      validateQueueRow(
          operationClass,
          jobIdHash,
          resultIdHash,
          deletionScopeDigest,
          deletionIdHash,
          deletionReceiptIdHash,
          queueState,
          deletionSubstatus,
          contentFreeDeletionErrorCode,
          deletionReceiptVerificationOutcome,
          effectCount,
          applyCount,
          remoteDeletionEffectCount);
      requirePresent(operationClass);
      requireHash(intentIdHash);
      requireHash(logicalKeyHash);
      requireNullableHash(jobIdHash);
      requireNullableHash(resultIdHash);
      requirePresent(queueState);
      requirePresent(replayMarker);
      if (operationClass != OperationClass.DELETE_CLOUD_COPY) {
        requireBit(effectCount);
        requireBit(applyCount);
        requireBit(remoteDeletionEffectCount);
      }
      if (attemptCount < 0) {
        throw fault(Diagnostic.INVALID_014);
      }
    }

    QueueRow withLocalDigests(String pre, String post) {
      return new QueueRow(
          operationClass,
          intentIdHash,
          logicalKeyHash,
          jobIdHash,
          resultIdHash,
          deletionScopeDigest,
          deletionIdHash,
          deletionReceiptIdHash,
          queueState,
          deletionSubstatus,
          contentFreeDeletionErrorCode,
          deletionReceiptVerificationOutcome,
          attemptCount,
          replayMarker,
          effectCount,
          applyCount,
          remoteDeletionEffectCount,
          pre,
          post);
    }
  }

  public record FlowRow(
      long sequence,
      String scenarioId,
      String actionId,
      StateVector preStateVector,
      StateVector postStateVector,
      ReducerOutcome outcome,
      long monotonicOffsetMs,
      String preStateDigest,
      String postStateDigest,
      String processingRequestIdHash,
      String queueIntentIdHash,
      Diagnostic contentFreeErrorCode) {
    public FlowRow {
      requireScenarioId(scenarioId);
      requireActionId(actionId);
      requireHashFor(preStateDigest, Diagnostic.INVALID_010);
      requireHashFor(postStateDigest, Diagnostic.INVALID_010);
      if (sequence <= 0 || monotonicOffsetMs < 0) {
        throw fault(Diagnostic.INVALID_014);
      }
      requirePresent(preStateVector);
      requirePresent(postStateVector);
      requirePresent(outcome);
      requireNullableHash(processingRequestIdHash);
      requireNullableHash(queueIntentIdHash);
      if (contentFreeErrorCode == Diagnostic.NONE) {
        throw fault(Diagnostic.INVALID_015);
      }
    }
  }

  public record TypedLedgerDeltas(
      FlowRow flowAppend, QueueRow queueAppend, Diagnostic diagnosticCode) {
    public TypedLedgerDeltas {
      requirePresent(diagnosticCode);
    }
  }

  public record Result(
      String selectedRuleId,
      ReducerOutcome outcome,
      StateVector postStateVector,
      TypedLedgerDeltas typedLedgerDeltas,
      int effectCount,
      int applyCount,
      int remoteDeletionEffectCount) {
    public Result {
      if (selectedRuleId != null) {
        requireRuleId(selectedRuleId);
      }
      requirePresent(outcome);
      requirePresent(postStateVector);
      requirePresent(typedLedgerDeltas);
      requireBit(effectCount);
      requireBit(applyCount);
      requireBit(remoteDeletionEffectCount);
      requireOutcomeDiagnostic(outcome, selectedRuleId, typedLedgerDeltas.diagnosticCode);
    }
  }

  public record ReplayRecord(
      String logicalKeyHash,
      String canonicalInputDigest,
      String selectedRuleId,
      ReducerOutcome outcome,
      String postStateDigest,
      String resultIdHash,
      ReplayMarker replayMarker,
      long attemptCount,
      int effectCount,
      int applyCount,
      int remoteDeletionEffectCount) {
    public ReplayRecord {
      requireRuleId(selectedRuleId);
      requireHashFor(canonicalInputDigest, Diagnostic.INVALID_010);
      requireHashFor(postStateDigest, Diagnostic.INVALID_010);
      requireHash(logicalKeyHash);
      requirePresent(outcome);
      requireNullableHash(resultIdHash);
      requirePresent(replayMarker);
      if (attemptCount < 0) {
        throw fault(Diagnostic.INVALID_014);
      }
      requireBit(effectCount);
      requireBit(applyCount);
      requireBit(remoteDeletionEffectCount);
    }
  }

  public record RunState(
      String scenarioId,
      int nextActionOrdinal,
      long monotonicOffsetMs,
      AttemptBudget budget,
      StateVector stateVector,
      List<FlowRow> flowLedger,
      List<QueueRow> queueLedger,
      List<ReplayRecord> replayRecords,
      Result lastResult) {
    public RunState {
      requireScenarioId(scenarioId);
      preflightTypedRunStateCategory003(scenarioId, flowLedger, lastResult);
      preflightTypedRunStateCategory010(
          scenarioId, stateVector, flowLedger, queueLedger, replayRecords, lastResult);
      int actionCount = scenarioActionCount(scenarioId);
      if (nextActionOrdinal < 1 || nextActionOrdinal > actionCount + 1 || monotonicOffsetMs < 0) {
        throw fault(Diagnostic.INVALID_014);
      }
      requirePresent(budget);
      requirePresent(stateVector);
      flowLedger = immutableNoNulls(flowLedger);
      queueLedger = immutableNoNulls(queueLedger);
      replayRecords = immutableNoNulls(replayRecords);
      validateLedgerGraph(
          scenarioId,
          nextActionOrdinal,
          monotonicOffsetMs,
          stateVector,
          flowLedger,
          queueLedger,
          replayRecords,
          budget,
          lastResult);
    }
  }

  public record Step(RunState runState, Result result, byte[] snapshotBytes) {
    public Step {
      requirePresent(runState);
      requirePresent(result);
      snapshotBytes = snapshotBytes == null ? null : snapshotBytes.clone();
    }

    @Override
    public byte[] snapshotBytes() {
      return snapshotBytes == null ? null : snapshotBytes.clone();
    }
  }

  public record RevalidationInput(
      QueueRow row,
      Consent consent,
      Profile profile,
      ProcessingRequirement processingRequirement,
      RuntimeStatus runtime,
      ConnectivityState connectivity,
      AttemptBudget budget,
      ExplicitGrant explicitGrant,
      Integer scriptedDeletionRowOrdinal) {
    public RevalidationInput {
      requirePresent(row);
      requirePresent(consent);
      requirePresent(profile);
      requirePresent(processingRequirement);
      requirePresent(runtime);
      requirePresent(connectivity);
      requirePresent(budget);
      requirePresent(explicitGrant);
    }
  }

  public record RevalidationDecision(
      RevalidationOutcome outcome,
      String selectedRuleId,
      QueueState queueTarget,
      ProcessingState processingTarget,
      long manualGrantDelta,
      Diagnostic diagnostic) {
    public RevalidationDecision {
      requirePresent(outcome);
      requirePresent(diagnostic);
      if (manualGrantDelta < 0 || manualGrantDelta > 1) {
        throw fault(Diagnostic.INVALID_014);
      }
    }
  }

  /** Complete typed input for the twenty-rule non-deletion queue reducer. */
  record QueueReducerInput(
      String scenarioId,
      StateVector preStateVector,
      AttemptBudget budget,
      QueueRow currentRow,
      ReplayRecord cachedReplayRecord,
      StateVector cachedReplayPostStateVector,
      Set<QueueSignal> signals,
      ExplicitGrant explicitGrant) {
    public QueueReducerInput {
      requireScenarioId(scenarioId);
      requirePresent(preStateVector);
      requirePresent(budget);
      requirePresent(signals);
      requirePresent(explicitGrant);
      EnumSet<QueueSignal> copy = EnumSet.noneOf(QueueSignal.class);
      for (QueueSignal signal : signals) {
        copy.add(requirePresent(signal));
      }
      signals = Collections.unmodifiableSet(copy);
    }
  }

  /** Pure result of queue arbitration and mutation before any scenario flow row is appended. */
  record QueueReduction(
      String selectedRuleId,
      ReducerOutcome outcome,
      Diagnostic diagnostic,
      StateVector postStateVector,
      AttemptBudget budget,
      QueueRow queueAppend,
      ReplayRecord replayRecord,
      StateVector replayPostStateVector,
      boolean flowLedgerAppend,
      boolean queueLedgerAppend) {
    public QueueReduction {
      requirePresent(outcome);
      requirePresent(diagnostic);
      requirePresent(postStateVector);
      requirePresent(budget);
      if (queueLedgerAppend != (queueAppend != null)) {
        throw fault(Diagnostic.INVALID_015);
      }
      if (outcome != ReducerOutcome.INVALID_INPUT
          && ((replayRecord == null) != (replayPostStateVector == null)
              || (replayRecord != null
                  && !replayRecord.postStateDigest.equals(
                      stateVectorDigest(replayPostStateVector))))) {
        throw fault(Diagnostic.INVALID_015);
      }
      requireOutcomeDiagnostic(outcome, selectedRuleId, diagnostic);
      if (selectedRuleId != null) {
        requireRuleId(selectedRuleId);
      }
    }
  }

  /** Content-free injectable surface for the twelve inherited contract-validation categories. */
  public record ContractProbe(
      boolean syntheticOnly,
      boolean semanticParentPinsExact,
      boolean catalogsContiguousAndExact,
      boolean coverageExact,
      boolean deterministicOnly,
      boolean forbiddenApisAbsent,
      boolean contentFreeOnly,
      boolean retryBoundedAndDeniedSafe,
      boolean restoreExactAndImmutable,
      boolean stateVectorExact,
      boolean deletionInvariantsExact,
      boolean claimsBounded) {}

  /** Returns the first inherited contract diagnostic, or NONE when all twelve predicates pass. */
  public static Diagnostic validateContract(ContractProbe probe) {
    requirePresent(probe);
    if (!probe.syntheticOnly) return Diagnostic.INVALID_001;
    if (!probe.semanticParentPinsExact) return Diagnostic.INVALID_002;
    if (!probe.catalogsContiguousAndExact) return Diagnostic.INVALID_003;
    if (!probe.coverageExact) return Diagnostic.INVALID_004;
    if (!probe.deterministicOnly) return Diagnostic.INVALID_005;
    if (!probe.forbiddenApisAbsent) return Diagnostic.INVALID_006;
    if (!probe.contentFreeOnly) return Diagnostic.INVALID_007;
    if (!probe.retryBoundedAndDeniedSafe) return Diagnostic.INVALID_008;
    if (!probe.restoreExactAndImmutable) return Diagnostic.INVALID_009;
    if (!probe.stateVectorExact) return Diagnostic.INVALID_010;
    if (!probe.deletionInvariantsExact) return Diagnostic.INVALID_011;
    if (!probe.claimsBounded) return Diagnostic.INVALID_012;
    return Diagnostic.NONE;
  }

  private static final List<String> SCENARIO_COUNTS =
      List.of(
          "2", "3", "2", "3", "3", "1", "2", "3", "2", "1", "1", "1", "2",
          "5", "2", "2", "5", "2", "5", "3", "2", "3", "3", "3", "4", "2");

  private static final Set<String> GENERAL_TRANSITIONS =
      orderedSet(
          "=",
          "L:LOCAL_OPERATION_RUNNING",
          "L:LOCAL_OPERATION_SUCCEEDED",
          "L:LOCAL_OPERATION_SUCCEEDED;P:PRESERVE",
          "L:LOCAL_OPERATION_SUCCEEDED;Q:PRESERVE_WAITING_NETWORK",
          "L:PRESERVE",
          "P:PENDING_CAPABILITY",
          "P:PROCESSING_SUCCEEDED",
          "P:WAITING_MODEL",
          "Q:PENDING_UPLOAD",
          "Q:WAITING_NETWORK",
          "Q:UPLOADING;ATTEMPT:0_TO_1",
          "Q:REMOTE_PROCESSING;EFFECT:0_TO_1",
          "Q:RESULT_AVAILABLE",
          "Q:APPLIED;APPLY:0_TO_1",
          "Q:PRESERVE_REMOTE_PROCESSING;ATTEMPT:1_TO_2",
          "Q:APPLIED;APPLY:0_TO_1;EFFECT:PRESERVE_ONE",
          "Q:CANCELLED",
          "Q:CANCELLED_NON_DELETION_ROW",
          "Q:DELETE_PENDING;SUBSTATUS:DELETE_WAITING_NETWORK",
          "Q:APPEND_DELETE_PENDING_ROW_AND_PROJECT",
          "SNAPSHOT:=",
          "RESTORE:=",
          "APPLY_EXACT_INHERITED_ROW",
          "Q:PRESERVE_DELETE_PENDING;ATTEMPT:RESOLVE_BY_SUBCASE");

  private static final Set<String> PACKET_TEMPLATES =
      orderedSet(
          "APPLY_EXACT_INHERITED_ROW",
          "Q:PRESERVE_DELETE_PENDING;ATTEMPT:RESOLVE_BY_SUBCASE",
          "SELECT_ONE_INHERITED_DELETION_CLASS",
          "RECONCILE_SELECTED_CLASS");

  private static final Set<String> SCRIPTED_RUNTIME_TRANSITIONS = buildScriptedTransitions();
  private static final Set<String> RUNTIME_TRANSITIONS = buildRuntimeTransitions();
  private static final List<QueueRule> QUEUE_RULES = buildQueueRules();
  private static final List<ActionSpec> ACTIONS = buildActions();
  private static final List<DeletionRow> DELETION_ROWS = buildDeletionRows();

  public static Set<String> generalTransitionCatalog() {
    return GENERAL_TRANSITIONS;
  }

  public static Set<String> packetTemplateCatalog() {
    return PACKET_TEMPLATES;
  }

  public static Set<String> runtimeTransitionCatalog() {
    return RUNTIME_TRANSITIONS;
  }

  static List<QueueRule> queueRules() {
    return QUEUE_RULES;
  }

  static List<ActionSpec> actionCatalog() {
    return ACTIONS;
  }

  public static List<DeletionRow> deletionRows() {
    return DELETION_ROWS;
  }

  /** Selects and applies the first eligible Q-001..Q-020 rule by frozen priority. */
  static QueueReduction reduceQueue(QueueReducerInput input) {
    requirePresent(input);
    Diagnostic structuralDiagnostic = queueInputDiagnostic(input);
    if (structuralDiagnostic != Diagnostic.NONE) {
      return invalidQueueReduction(input, structuralDiagnostic);
    }
    Set<QueueSignal> signals = input.signals;
    if ((signals.contains(QueueSignal.USER_TRUTH_ALLOW)
            && signals.contains(QueueSignal.USER_TRUTH_BLOCKS))
        || (signals.contains(QueueSignal.SAME_KEY_DIFFERENT_CANONICAL_INPUT)
            && (signals.contains(QueueSignal.SAME_INPUT_REPLAY)
                || signals.contains(QueueSignal.DUPLICATE_TRIGGER)))) {
      return invalidQueueReduction(input, Diagnostic.INVALID_015);
    }
    if (input.explicitGrant == ExplicitGrant.ELIGIBLE_EXPLICIT_GRANT
        && (input.currentRow == null
            || input.currentRow.queueState != QueueState.FAILED_RETRYABLE
            || input.budget.remaining() != 0
            || !signals.contains(QueueSignal.RESUME_OR_MANUAL_RETRY)
            || signals.size() != 1)) {
      return invalidQueueReduction(input, Diagnostic.INVALID_015);
    }

    QueueRule selected = null;
    for (QueueRule rule : QUEUE_RULES) {
      if (queueRuleEligible(rule.id, input)) {
        selected = rule;
        break;
      }
    }
    if (selected == null) {
      return new QueueReduction(
          null,
          ReducerOutcome.NO_STATE_CHANGE,
          Diagnostic.NO_ELIGIBLE_RULE,
          input.preStateVector,
          input.budget,
          null,
          input.cachedReplayRecord,
          input.cachedReplayPostStateVector,
          false,
          false);
    }
    return applyQueueRule(input, selected);
  }

  private static Diagnostic queueInputDiagnostic(QueueReducerInput input) {
    boolean attemptSignal =
        input.signals.contains(QueueSignal.SEND_AVAILABLE)
            || input.signals.contains(QueueSignal.SAME_INPUT_REPLAY);
    if (attemptSignal && input.preStateVector.connectivity != ConnectivityState.AVAILABLE) {
      return Diagnostic.INVALID_008;
    }
    QueueRow row = input.currentRow;
    ReplayRecord replay = input.cachedReplayRecord;
    if (row == null) {
      return input.preStateVector.queue == QueueState.LOCAL_ONLY
              && replay == null
              && input.cachedReplayPostStateVector == null
          ? Diagnostic.NONE
          : Diagnostic.INVALID_015;
    }
    if (row.operationClass != OperationClass.NON_DELETION_CLOUD_INTENT
        || input.preStateVector.queue != row.queueState
        || row.attemptCount != input.budget.attemptCount
        || replay == null
        || input.cachedReplayPostStateVector == null
        || !replay.logicalKeyHash.equals(row.logicalKeyHash)
        || replay.attemptCount != row.attemptCount
        || replay.effectCount != row.effectCount
        || replay.applyCount != row.applyCount
        || replay.remoteDeletionEffectCount != 0
        || !Objects.equals(replay.resultIdHash, row.resultIdHash)
        || replay.outcome != ReducerOutcome.TRANSITION_APPLIED
        || !replay.postStateDigest.equals(
            stateVectorDigest(input.cachedReplayPostStateVector))
        || input.cachedReplayPostStateVector.queue != row.queueState
        || !replay.canonicalInputDigest.equals(
            replayInputDigest(
                OperationClass.NON_DELETION_CLOUD_INTENT,
                row.logicalKeyHash,
                InputVariant.PRIMARY,
                null))
        || !replayRuleAllowedForState(row.queueState, replay.selectedRuleId)
        || !replayMarkerCompatible(row, replay)
        || ((row.jobIdHash != null) != (row.effectCount == 1))
        || ((row.queueState == QueueState.APPLIED) != (row.applyCount == 1))
        || !row.postLocalStateDigest.equals(
            localStateDigest(input.cachedReplayPostStateVector.local))) {
      return Diagnostic.INVALID_015;
    }
    boolean deniedSignal =
        input.signals.contains(QueueSignal.CONNECTIVITY_DENIED)
            || input.signals.contains(QueueSignal.SCHEDULER_TICK_WHILE_DENIED);
    if (deniedSignal
        && input.preStateVector.connectivity != ConnectivityState.NETWORK_DENIED
        && input.preStateVector.connectivity != ConnectivityState.AIRPLANE_MODE) {
      return Diagnostic.INVALID_015;
    }
    boolean availableSignal = input.signals.contains(QueueSignal.ALL_REVALIDATION_ALLOW);
    if (availableSignal && input.preStateVector.connectivity != ConnectivityState.AVAILABLE) {
      return Diagnostic.INVALID_015;
    }
    return Diagnostic.NONE;
  }

  private static boolean replayMarkerCompatible(QueueRow row, ReplayRecord replay) {
    return replay.replayMarker == row.replayMarker
        || (row.queueState == QueueState.APPLIED
            && row.replayMarker == ReplayMarker.ORIGINAL
            && replay.replayMarker == ReplayMarker.SAME_INPUT_REPLAY
            && Set.of("OFF-I1-RULE-Q-013", "OFF-I1-RULE-C-001")
                .contains(replay.selectedRuleId));
  }

  private static boolean replayRuleAllowedForState(QueueState state, String ruleId) {
    return switch (state) {
      case PENDING_UPLOAD ->
          Set.of(
                  "OFF-I1-RULE-Q-003",
                  "OFF-I1-RULE-Q-007",
                  "OFF-I1-RULE-Q-009",
                  "OFF-I1-RULE-Q-016")
              .contains(ruleId);
      case WAITING_NETWORK ->
          Set.of("OFF-I1-RULE-Q-004", "OFF-I1-RULE-Q-008").contains(ruleId);
      case UPLOADING -> "OFF-I1-RULE-Q-010".equals(ruleId);
      case REMOTE_PROCESSING ->
          Set.of("OFF-I1-RULE-Q-011", "OFF-I1-RULE-Q-017", "OFF-I1-RULE-Q-019")
              .contains(ruleId);
      case RESULT_AVAILABLE -> "OFF-I1-RULE-Q-012".equals(ruleId);
      case APPLIED ->
          Set.of("OFF-I1-RULE-Q-013", "OFF-I1-RULE-C-001").contains(ruleId);
      case CONFLICT -> "OFF-I1-RULE-Q-014".equals(ruleId);
      case FAILED_RETRYABLE ->
          Set.of("OFF-I1-RULE-Q-005", "OFF-I1-RULE-Q-015").contains(ruleId);
      case FAILED_FINAL -> "OFF-I1-RULE-Q-018".equals(ruleId);
      case CANCELLED ->
          Set.of("OFF-I1-RULE-Q-001", "OFF-I1-RULE-Q-006").contains(ruleId);
      case LOCAL_ONLY, DELETE_PENDING, DELETED_REMOTE -> false;
    };
  }

  private static boolean queueRuleEligible(String id, QueueReducerInput input) {
    QueueRow row = input.currentRow;
    QueueState source = input.preStateVector.queue;
    Set<QueueSignal> signals = input.signals;
    return switch (id) {
      case "OFF-I1-RULE-Q-001" ->
          row != null
              && isNonDeletionNonterminal(source)
              && signals.contains(QueueSignal.CONSENT_INVALID_OR_REVOKED);
      case "OFF-I1-RULE-Q-002" ->
          row != null && signals.contains(QueueSignal.SAME_KEY_DIFFERENT_CANONICAL_INPUT);
      case "OFF-I1-RULE-Q-003" ->
          row != null
              && EnumSet.of(
                      QueueState.WAITING_NETWORK,
                      QueueState.PENDING_UPLOAD,
                      QueueState.FAILED_RETRYABLE)
                  .contains(source)
              && signals.contains(QueueSignal.PROFILE_OR_RUNTIME_BLOCKED);
      case "OFF-I1-RULE-Q-004" ->
          row != null
              && (source == QueueState.PENDING_UPLOAD || source == QueueState.FAILED_RETRYABLE)
              && signals.contains(QueueSignal.CONNECTIVITY_DENIED);
      case "OFF-I1-RULE-Q-005" ->
          row != null
              && (source == QueueState.UPLOADING || source == QueueState.REMOTE_PROCESSING)
              && signals.contains(QueueSignal.CONNECTIVITY_LOST);
      case "OFF-I1-RULE-Q-006" ->
          row != null
              && isActivePreApplication(source)
              && signals.contains(QueueSignal.VALID_CANCEL_OR_REVOKE);
      case "OFF-I1-RULE-Q-007" ->
          row == null
              && source == QueueState.LOCAL_ONLY
              && signals.contains(QueueSignal.NEW_LOGICAL_KEY);
      case "OFF-I1-RULE-Q-008" ->
          row != null
              && source == QueueState.PENDING_UPLOAD
              && signals.contains(QueueSignal.SCHEDULER_TICK_WHILE_DENIED);
      case "OFF-I1-RULE-Q-009" ->
          row != null
              && source == QueueState.WAITING_NETWORK
              && signals.contains(QueueSignal.ALL_REVALIDATION_ALLOW);
      case "OFF-I1-RULE-Q-010" ->
          row != null
              && source == QueueState.PENDING_UPLOAD
              && input.preStateVector.connectivity == ConnectivityState.AVAILABLE
              && input.budget.remaining() > 0
              && input.explicitGrant == ExplicitGrant.NONE
              && signals.contains(QueueSignal.SEND_AVAILABLE);
      case "OFF-I1-RULE-Q-011" ->
          row != null
              && source == QueueState.UPLOADING
              && row.jobIdHash == null
              && row.effectCount == 0
              && signals.contains(QueueSignal.FIRST_DURABLE_ACCEPTANCE);
      case "OFF-I1-RULE-Q-012" ->
          row != null
              && source == QueueState.REMOTE_PROCESSING
              && row.jobIdHash != null
              && signals.contains(QueueSignal.VALID_RESULT);
      case "OFF-I1-RULE-Q-013" ->
          row != null
              && source == QueueState.RESULT_AVAILABLE
              && row.resultIdHash != null
              && row.applyCount == 0
              && signals.contains(QueueSignal.USER_TRUTH_ALLOW);
      case "OFF-I1-RULE-Q-014" ->
          row != null
              && source == QueueState.RESULT_AVAILABLE
              && row.resultIdHash != null
              && row.applyCount == 0
              && signals.contains(QueueSignal.USER_TRUTH_BLOCKS);
      case "OFF-I1-RULE-Q-015" ->
          row != null
              && (source == QueueState.UPLOADING || source == QueueState.REMOTE_PROCESSING)
              && signals.contains(QueueSignal.RETRYABLE_ERROR);
      case "OFF-I1-RULE-Q-016" ->
          row != null
              && source == QueueState.FAILED_RETRYABLE
              && row.jobIdHash == null
              && resumeGuard(input);
      case "OFF-I1-RULE-Q-017" ->
          row != null
              && source == QueueState.FAILED_RETRYABLE
              && row.jobIdHash != null
              && resumeGuard(input);
      case "OFF-I1-RULE-Q-018" ->
          row != null
              && isActiveOrRetryable(source)
              && (signals.contains(QueueSignal.TERMINAL_ERROR)
                  || (input.budget.remaining() == 0
                      && input.explicitGrant == ExplicitGrant.NONE));
      case "OFF-I1-RULE-Q-019" ->
          row != null
              && source == QueueState.REMOTE_PROCESSING
              && input.preStateVector.connectivity == ConnectivityState.AVAILABLE
              && row.jobIdHash != null
              && row.effectCount == 1
              && input.budget.remaining() > 0
              && signals.contains(QueueSignal.SAME_INPUT_REPLAY);
      case "OFF-I1-RULE-Q-020" ->
          row != null
              && source == QueueState.APPLIED
              && row.applyCount == 1
              && signals.contains(QueueSignal.DUPLICATE_TRIGGER);
      default -> throw fault(Diagnostic.INVALID_003);
    };
  }

  private static boolean resumeGuard(QueueReducerInput input) {
    if (!input.signals.contains(QueueSignal.RESUME_OR_MANUAL_RETRY)) {
      return false;
    }
    return (input.budget.remaining() > 0 && input.explicitGrant == ExplicitGrant.NONE)
        || (input.budget.remaining() == 0
            && input.explicitGrant == ExplicitGrant.ELIGIBLE_EXPLICIT_GRANT);
  }

  private static boolean isNonDeletionNonterminal(QueueState state) {
    return state != QueueState.LOCAL_ONLY
        && state != QueueState.APPLIED
        && state != QueueState.CONFLICT
        && state != QueueState.FAILED_FINAL
        && state != QueueState.CANCELLED
        && state != QueueState.DELETE_PENDING
        && state != QueueState.DELETED_REMOTE;
  }

  private static boolean isActivePreApplication(QueueState state) {
    return EnumSet.of(
            QueueState.PENDING_UPLOAD,
            QueueState.WAITING_NETWORK,
            QueueState.UPLOADING,
            QueueState.REMOTE_PROCESSING,
            QueueState.RESULT_AVAILABLE,
            QueueState.FAILED_RETRYABLE)
        .contains(state);
  }

  private static boolean isActiveOrRetryable(QueueState state) {
    return isActivePreApplication(state);
  }

  private static QueueReduction applyQueueRule(QueueReducerInput input, QueueRule rule) {
    QueueRow current = input.currentRow;
    StateVector post = input.preStateVector;
    AttemptBudget budget = input.budget;
    QueueRow append = null;
    Diagnostic diagnostic = Diagnostic.NONE;

    switch (rule.id) {
      case "OFF-I1-RULE-Q-001", "OFF-I1-RULE-Q-006" -> {
        post = post.withQueue(QueueState.CANCELLED);
        append =
            copyNonDeletion(
                current,
                QueueState.CANCELLED,
                current.attemptCount,
                current.jobIdHash,
                null,
                current.effectCount,
                current.applyCount,
                current.replayMarker);
      }
      case "OFF-I1-RULE-Q-002" -> diagnostic = Diagnostic.IDEMPOTENCY_INPUT_MISMATCH;
      case "OFF-I1-RULE-Q-003" -> {
        post = post.withQueue(QueueState.PENDING_UPLOAD);
        append =
            copyNonDeletion(
                current,
                QueueState.PENDING_UPLOAD,
                current.attemptCount,
                current.jobIdHash,
                null,
                current.effectCount,
                current.applyCount,
                current.replayMarker);
      }
      case "OFF-I1-RULE-Q-004", "OFF-I1-RULE-Q-008" -> {
        post = post.withQueue(QueueState.WAITING_NETWORK);
        append =
            copyNonDeletion(
                current,
                QueueState.WAITING_NETWORK,
                current.attemptCount,
                current.jobIdHash,
                null,
                current.effectCount,
                current.applyCount,
                current.replayMarker);
      }
      case "OFF-I1-RULE-Q-005", "OFF-I1-RULE-Q-015" -> {
        post = post.withQueue(QueueState.FAILED_RETRYABLE);
        append =
            copyNonDeletion(
                current,
                QueueState.FAILED_RETRYABLE,
                current.attemptCount,
                current.jobIdHash,
                null,
                current.effectCount,
                current.applyCount,
                current.replayMarker);
      }
      case "OFF-I1-RULE-Q-007" -> {
        budget = new AttemptBudget(0, 0);
        post = post.withQueue(QueueState.PENDING_UPLOAD);
        append = seedNonDeletion(input.scenarioId, post, QueueState.PENDING_UPLOAD, 0, null, null, 0, 0);
      }
      case "OFF-I1-RULE-Q-009" -> {
        post = post.withQueue(QueueState.PENDING_UPLOAD);
        append =
            copyNonDeletion(
                current,
                QueueState.PENDING_UPLOAD,
                current.attemptCount,
                current.jobIdHash,
                null,
                current.effectCount,
                current.applyCount,
                current.replayMarker);
      }
      case "OFF-I1-RULE-Q-010" -> {
        budget = budget.attempt();
        post = post.withQueue(QueueState.UPLOADING);
        append =
            copyNonDeletion(
                current,
                QueueState.UPLOADING,
                budget.attemptCount,
                current.jobIdHash,
                current.resultIdHash,
                current.effectCount,
                current.applyCount,
                current.replayMarker);
      }
      case "OFF-I1-RULE-Q-011" -> {
        post = post.withQueue(QueueState.REMOTE_PROCESSING);
        append =
            copyNonDeletion(
                current,
                QueueState.REMOTE_PROCESSING,
                current.attemptCount,
                jobIdHash(current.logicalKeyHash, "job." + scenarioSuffix(input.scenarioId)),
                null,
                1,
                0,
                ReplayMarker.ORIGINAL);
      }
      case "OFF-I1-RULE-Q-012" -> {
        post = post.withQueue(QueueState.RESULT_AVAILABLE);
        append =
            copyNonDeletion(
                current,
                QueueState.RESULT_AVAILABLE,
                current.attemptCount,
                current.jobIdHash,
                resultIdHash(current.jobIdHash, "result." + scenarioSuffix(input.scenarioId)),
                current.effectCount,
                0,
                current.replayMarker);
      }
      case "OFF-I1-RULE-Q-013" -> {
        post = post.withQueue(QueueState.APPLIED);
        append =
            copyNonDeletion(
                current,
                QueueState.APPLIED,
                current.attemptCount,
                current.jobIdHash,
                current.resultIdHash,
                current.effectCount,
                1,
                current.replayMarker);
      }
      case "OFF-I1-RULE-Q-014" -> {
        post = post.withQueue(QueueState.CONFLICT);
        append =
            copyNonDeletion(
                current,
                QueueState.CONFLICT,
                current.attemptCount,
                current.jobIdHash,
                current.resultIdHash,
                current.effectCount,
                0,
                current.replayMarker);
      }
      case "OFF-I1-RULE-Q-016", "OFF-I1-RULE-Q-017" -> {
        if (input.explicitGrant == ExplicitGrant.ELIGIBLE_EXPLICIT_GRANT) {
          budget = budget.grant();
        }
        QueueState target =
            rule.id.endsWith("016") ? QueueState.PENDING_UPLOAD : QueueState.REMOTE_PROCESSING;
        post = post.withQueue(target);
        append =
            copyNonDeletion(
                current,
                target,
                current.attemptCount,
                current.jobIdHash,
                null,
                current.effectCount,
                current.applyCount,
                current.replayMarker);
      }
      case "OFF-I1-RULE-Q-018" -> {
        post = post.withQueue(QueueState.FAILED_FINAL);
        append =
            copyNonDeletion(
                current,
                QueueState.FAILED_FINAL,
                current.attemptCount,
                current.jobIdHash,
                null,
                current.effectCount,
                current.applyCount,
                current.replayMarker);
      }
      case "OFF-I1-RULE-Q-019" -> {
        budget = budget.attempt();
        append =
            copyNonDeletion(
                current,
                QueueState.REMOTE_PROCESSING,
                budget.attemptCount,
                current.jobIdHash,
                null,
                current.effectCount,
                current.applyCount,
                ReplayMarker.SAME_INPUT_REPLAY);
      }
      case "OFF-I1-RULE-Q-020" -> {
        // The reducer result is Q-020; the cached semantic result is preserved below.
      }
      default -> throw fault(Diagnostic.INVALID_003);
    }

    if (append != null) {
      append =
          append.withLocalDigests(
              localStateDigest(input.preStateVector.local), localStateDigest(post.local));
    }
    ReplayRecord replay = queueReplayAfter(input, rule, post, append);
    StateVector replayPostState =
        rule.id.endsWith("002") || rule.id.endsWith("020") || append == null
            ? input.cachedReplayPostStateVector
            : post;
    return new QueueReduction(
        rule.id,
        rule.outcome,
        diagnostic,
        post,
        budget,
        append,
        replay,
        replayPostState,
        rule.flowLedgerAppend,
        rule.queueLedgerAppend);
  }

  private static ReplayRecord queueReplayAfter(
      QueueReducerInput input, QueueRule rule, StateVector post, QueueRow append) {
    ReplayRecord cached = input.cachedReplayRecord;
    if (rule.id.endsWith("002")) {
      return cached;
    }
    if (rule.id.endsWith("020")) {
      return new ReplayRecord(
          cached.logicalKeyHash,
          cached.canonicalInputDigest,
          cached.selectedRuleId,
          cached.outcome,
          cached.postStateDigest,
          cached.resultIdHash,
          ReplayMarker.SAME_INPUT_REPLAY,
          cached.attemptCount,
          cached.effectCount,
          cached.applyCount,
          cached.remoteDeletionEffectCount);
    }
    if (append == null) {
      return cached;
    }
    String canonicalInput =
        cached == null
            ? replayInputDigest(
                OperationClass.NON_DELETION_CLOUD_INTENT,
                append.logicalKeyHash,
                InputVariant.PRIMARY,
                null)
            : cached.canonicalInputDigest;
    return new ReplayRecord(
        append.logicalKeyHash,
        canonicalInput,
        rule.id,
        rule.outcome,
        stateVectorDigest(post),
        append.resultIdHash,
        append.replayMarker,
        append.attemptCount,
        append.effectCount,
        append.applyCount,
        0);
  }

  private static QueueReduction invalidQueueReduction(
      QueueReducerInput input, Diagnostic diagnostic) {
    return new QueueReduction(
        null,
        ReducerOutcome.INVALID_INPUT,
        diagnostic,
        input.preStateVector,
        input.budget,
        null,
        input.cachedReplayRecord,
        input.cachedReplayPostStateVector,
        false,
        false);
  }

  private static Set<String> buildScriptedTransitions() {
    LinkedHashSet<String> values = new LinkedHashSet<>();
    values.add("PRESERVE_DELETE_PENDING_ATTEMPT_0");
    values.add("PRESERVE_DELETE_PENDING_ATTEMPT_1");
    values.add("PRESERVE_DELETE_PENDING_ATTEMPT_1_AFTER_EXACT_GRANT");
    for (int row = 1; row <= 15; row++) {
      values.add("APPLY_INHERITED_DELETION_ROW_" + three(row));
    }
    return Collections.unmodifiableSet(values);
  }

  private static Set<String> buildRuntimeTransitions() {
    LinkedHashSet<String> values = new LinkedHashSet<>(GENERAL_TRANSITIONS);
    values.remove("APPLY_EXACT_INHERITED_ROW");
    values.remove("Q:PRESERVE_DELETE_PENDING;ATTEMPT:RESOLVE_BY_SUBCASE");
    values.addAll(SCRIPTED_RUNTIME_TRANSITIONS);
    if (values.size() != 41) {
      throw fault(Diagnostic.INVALID_004);
    }
    return Collections.unmodifiableSet(values);
  }

  private static List<QueueRule> buildQueueRules() {
    return List.of(
        q(1, 10, "REVALIDATE_CONSENT", "NON_DELETION_NONTERMINAL", "CONSENT_INVALID_OR_REVOKED", "CANCELLED", "ZERO", true, true),
        q(2, 20, "IDEMPOTENCY_LOOKUP", "ANY_QUEUE_ROW", "SAME_KEY_DIFFERENT_CANONICAL_INPUT", "UNCHANGED", "ZERO", false, false),
        q(3, 30, "REVALIDATE_PROFILE_OR_RUNTIME", "WAITING_NETWORK_OR_PENDING_UPLOAD_OR_FAILED_RETRYABLE", "PROFILE_OR_RUNTIME_BLOCKED", "PENDING_UPLOAD", "ZERO", true, true),
        q(4, 40, "REVALIDATE_CONNECTIVITY", "PENDING_UPLOAD_OR_FAILED_RETRYABLE", "CONNECTIVITY_DENIED_AND_PRESERVE_EXISTING_JOB_ID_HASH_NULL_OR_NON_NULL", "WAITING_NETWORK", "ZERO", true, true),
        q(5, 41, "TRANSPORT_CONNECTIVITY_LOST", "UPLOADING_OR_REMOTE_PROCESSING", "CONNECTIVITY_LOST", "FAILED_RETRYABLE", "PRESERVE", true, true),
        q(6, 50, "EXPLICIT_CANCEL", "ACTIVE_PRE_APPLICATION", "VALID_CANCEL_OR_REVOKE", "CANCELLED", "ZERO", true, true),
        q(7, 100, "ENQUEUE_NON_DELETION", "LOCAL_ONLY", "NEW_LOGICAL_KEY", "PENDING_UPLOAD", "ZERO", true, true),
        q(8, 110, "SCHEDULER_TICK", "PENDING_UPLOAD", "CONNECTIVITY_DENIED", "WAITING_NETWORK", "ZERO", true, true),
        q(9, 120, "REVALIDATE", "WAITING_NETWORK", "ALL_REVALIDATION_ALLOW", "PENDING_UPLOAD", "ZERO", true, true),
        q(10, 130, "SEND", "PENDING_UPLOAD", "AVAILABLE_AND_POSITIVE_BUDGET", "UPLOADING", "ATTEMPT_PLUS_ONE", true, true),
        q(11, 140, "DURABLE_ACCEPTANCE", "UPLOADING", "FIRST_ACCEPTANCE", "REMOTE_PROCESSING", "EFFECT_ZERO_TO_ONE", true, true),
        q(12, 150, "VALID_RESULT", "REMOTE_PROCESSING", "RESULT_VALID", "RESULT_AVAILABLE", "ZERO", true, true),
        q(13, 160, "APPLY_RESULT", "RESULT_AVAILABLE", "VALIDATION_AND_USER_TRUTH_ALLOW", "APPLIED", "APPLY_ZERO_TO_ONE", true, true),
        q(14, 161, "APPLY_RESULT", "RESULT_AVAILABLE", "CURRENT_USER_TRUTH_BLOCKS", "CONFLICT", "ZERO", true, true),
        q(15, 170, "TRANSPORT_FAILURE", "UPLOADING_OR_REMOTE_PROCESSING", "RETRYABLE_ERROR", "FAILED_RETRYABLE", "PRESERVE", true, true),
        q(16, 171, "RESUME_OR_MANUAL_RETRY", "FAILED_RETRYABLE_WITHOUT_JOB", "REVALIDATION_ALLOW_AND_EITHER_POSITIVE_REMAINING_WITH_GRANT_NONE_OR_ZERO_REMAINING_WITH_ELIGIBLE_EXPLICIT_GRANT", "PENDING_UPLOAD", "ZERO_FOR_POSITIVE_REMAINING_WITH_GRANT_NONE_OR_MANUAL_RETRY_GRANT_PLUS_ONE_ATTEMPT_ZERO_FOR_ZERO_REMAINING_WITH_ELIGIBLE_EXPLICIT_GRANT", true, true),
        q(17, 172, "RESUME_OR_MANUAL_RETRY", "FAILED_RETRYABLE_WITH_JOB", "REVALIDATION_ALLOW_AND_EITHER_POSITIVE_REMAINING_WITH_GRANT_NONE_OR_ZERO_REMAINING_WITH_ELIGIBLE_EXPLICIT_GRANT", "REMOTE_PROCESSING", "ZERO_FOR_POSITIVE_REMAINING_WITH_GRANT_NONE_OR_MANUAL_RETRY_GRANT_PLUS_ONE_ATTEMPT_ZERO_FOR_ZERO_REMAINING_WITH_ELIGIBLE_EXPLICIT_GRANT", true, true),
        q(18, 180, "FINAL_OR_BUDGET_EXHAUSTED", "ACTIVE_OR_RETRYABLE", "TERMINAL_OR_AUTOMATIC_BUDGET_EXHAUSTED", "FAILED_FINAL", "ZERO", true, true),
        q(19, 190, "TRANSPORT_REPLAY", "REMOTE_PROCESSING", "SAME_INPUT", "UNCHANGED", "ATTEMPT_PLUS_ONE_EFFECT_STAYS_ONE", true, true),
        new QueueRule("OFF-I1-RULE-Q-020", 191, 0, "DUPLICATE_TRIGGER", "APPLIED", "SAME_INPUT", "UNCHANGED", ReducerOutcome.TRANSITION_APPLIED, false, "ZERO", true, false));
  }

  private static QueueRule q(
      int ordinal,
      int priority,
      String event,
      String source,
      String guard,
      String target,
      String counters,
      boolean flow,
      boolean queue) {
    return new QueueRule(
        "OFF-I1-RULE-Q-" + three(ordinal),
        priority,
        0,
        event,
        source,
        guard,
        target,
        ordinal == 2 ? ReducerOutcome.NO_STATE_CHANGE : ReducerOutcome.TRANSITION_APPLIED,
        queue,
        counters,
        flow,
        queue);
  }

  private static List<ActionSpec> buildActions() {
    List<ActionSpec> actions = new ArrayList<>();
    addScenario(actions, 1, "=", "=");
    addScenario(actions, 2, "L:LOCAL_OPERATION_RUNNING", "L:LOCAL_OPERATION_RUNNING", "L:LOCAL_OPERATION_SUCCEEDED");
    addScenario(actions, 3, "RESTORE:=", "L:LOCAL_OPERATION_SUCCEEDED");
    addScenario(actions, 4, "L:LOCAL_OPERATION_RUNNING", "L:LOCAL_OPERATION_RUNNING", "L:LOCAL_OPERATION_SUCCEEDED");
    addScenario(actions, 5, "L:LOCAL_OPERATION_SUCCEEDED", "L:LOCAL_OPERATION_SUCCEEDED", "L:LOCAL_OPERATION_SUCCEEDED");
    addScenario(actions, 6, "L:LOCAL_OPERATION_SUCCEEDED");
    addScenario(actions, 7, "L:LOCAL_OPERATION_RUNNING", "L:LOCAL_OPERATION_SUCCEEDED");
    addScenario(actions, 8, "L:LOCAL_OPERATION_RUNNING", "L:LOCAL_OPERATION_SUCCEEDED", "=");
    addScenario(actions, 9, "P:WAITING_MODEL", "L:LOCAL_OPERATION_SUCCEEDED;P:PRESERVE");
    addScenario(actions, 10, "P:PROCESSING_SUCCEEDED");
    addScenario(actions, 11, "P:PROCESSING_SUCCEEDED");
    addScenario(actions, 12, "P:PROCESSING_SUCCEEDED");
    addScenario(actions, 13, "Q:PENDING_UPLOAD", "Q:WAITING_NETWORK");
    addScenario(actions, 14,
        "L:LOCAL_OPERATION_SUCCEEDED;Q:PRESERVE_WAITING_NETWORK",
        "L:LOCAL_OPERATION_SUCCEEDED;Q:PRESERVE_WAITING_NETWORK",
        "L:LOCAL_OPERATION_SUCCEEDED;Q:PRESERVE_WAITING_NETWORK",
        "L:LOCAL_OPERATION_SUCCEEDED;Q:PRESERVE_WAITING_NETWORK",
        "L:LOCAL_OPERATION_SUCCEEDED;Q:PRESERVE_WAITING_NETWORK");
    addScenario(actions, 15, "SNAPSHOT:=", "RESTORE:=");
    addScenario(actions, 16, "SNAPSHOT:=", "RESTORE:=");
    addScenario(actions, 17,
        "Q:PENDING_UPLOAD",
        "Q:UPLOADING;ATTEMPT:0_TO_1",
        "Q:REMOTE_PROCESSING;EFFECT:0_TO_1",
        "Q:RESULT_AVAILABLE",
        "Q:APPLIED;APPLY:0_TO_1");
    addScenario(actions, 18,
        "Q:PRESERVE_REMOTE_PROCESSING;ATTEMPT:1_TO_2",
        "Q:APPLIED;APPLY:0_TO_1;EFFECT:PRESERVE_ONE");
    addScenario(actions, 19, "=", "=", "L:LOCAL_OPERATION_SUCCEEDED", "=", "=");
    addScenario(actions, 20, "=", "RESTORE:=", "L:LOCAL_OPERATION_SUCCEEDED");
    addScenario(actions, 21, "P:PENDING_CAPABILITY", "L:LOCAL_OPERATION_SUCCEEDED;P:PRESERVE");
    addScenario(actions, 22, "Q:CANCELLED", "=", "L:LOCAL_OPERATION_SUCCEEDED");
    addScenario(actions, 23, "L:LOCAL_OPERATION_RUNNING", "L:LOCAL_OPERATION_SUCCEEDED", "=");
    addScenario(actions, 24, "L:LOCAL_OPERATION_SUCCEEDED", "Q:DELETE_PENDING;SUBSTATUS:DELETE_WAITING_NETWORK", "=");
    addScenario(actions, 25, "Q:CANCELLED_NON_DELETION_ROW", "L:PRESERVE", "Q:APPEND_DELETE_PENDING_ROW_AND_PROJECT", "=");
    addScenario(actions, 26, "SELECT_ONE_INHERITED_DELETION_CLASS", "RECONCILE_SELECTED_CLASS");
    if (actions.size() != 67) {
      throw fault(Diagnostic.INVALID_004);
    }
    return List.copyOf(actions);
  }

  private static void addScenario(List<ActionSpec> target, int scenario, String... transitions) {
    String scenarioId = "OFF-SYN-" + three(scenario);
    for (int index = 0; index < transitions.length; index++) {
      int ordinal = index + 1;
      String suffix = three(scenario) + "-" + two(ordinal);
      String actionId = "OFF-I1-ACT-" + suffix;
      String eventId = "OFF-I1-EVT-" + suffix;
      String transition = transitions[index];
      NonDirect nonDirect = nonDirect(actionId);
      boolean template = scenario == 26;
      String ruleId = nonDirect == null ? "OFF-I1-RULE-A-" + suffix : nonDirect.ruleId;
      ReducerOutcome outcome = nonDirect == null ? ReducerOutcome.TRANSITION_APPLIED : nonDirect.outcome;
      String projectionOutcome =
          scenario == 26 && ordinal == 2 ? "RESOLVE_BY_SUBCASE" : outcome.name();
      boolean queue = nonDirect != null && nonDirect.queue;
      int priority = nonDirect == null ? 1000 : nonDirect.priority;
      int secondary = nonDirect == null ? target.size() + 1 : nonDirect.secondary;
      target.add(
          new ActionSpec(
              scenarioId,
              actionId,
              ordinal,
              eventId,
              ruleId,
              outcome,
              projectionOutcome,
              transition,
              queue,
              true,
              queue,
              template,
              priority,
              secondary));
    }
  }

  private record NonDirect(
      String ruleId, ReducerOutcome outcome, boolean queue, int priority, int secondary) {}

  private static NonDirect nonDirect(String actionId) {
    return switch (actionId) {
      case "OFF-I1-ACT-013-01" -> nd("OFF-I1-RULE-Q-007", 100, 0);
      case "OFF-I1-ACT-013-02" -> nd("OFF-I1-RULE-Q-008", 110, 0);
      case "OFF-I1-ACT-017-01" -> nd("OFF-I1-RULE-Q-009", 120, 0);
      case "OFF-I1-ACT-017-02" -> nd("OFF-I1-RULE-Q-010", 130, 0);
      case "OFF-I1-ACT-017-03" -> nd("OFF-I1-RULE-Q-011", 140, 0);
      case "OFF-I1-ACT-017-04" -> nd("OFF-I1-RULE-Q-012", 150, 0);
      case "OFF-I1-ACT-017-05" -> nd("OFF-I1-RULE-Q-013", 160, 0);
      case "OFF-I1-ACT-018-01" -> nd("OFF-I1-RULE-Q-019", 190, 0);
      case "OFF-I1-ACT-018-02" -> nd("OFF-I1-RULE-C-001", 200, 0);
      case "OFF-I1-ACT-022-01", "OFF-I1-ACT-025-01" -> nd("OFF-I1-RULE-Q-001", 10, 0);
      case "OFF-I1-ACT-024-02", "OFF-I1-ACT-025-03" -> nd("OFF-I1-RULE-D-001", 300, 1);
      case "OFF-I1-ACT-026-01" -> new NonDirect("RESOLVE_SCRIPTED_DELETION_DISPATCH_RULE", ReducerOutcome.TRANSITION_APPLIED, true, 250, 0);
      case "OFF-I1-ACT-026-02" -> new NonDirect("RESOLVE_SCRIPTED_DELETION_RECONCILE_RULE", ReducerOutcome.TRANSITION_APPLIED, true, 300, 0);
      default -> null;
    };
  }

  private static NonDirect nd(String id, int priority, int secondary) {
    return new NonDirect(id, ReducerOutcome.TRANSITION_APPLIED, true, priority, secondary);
  }

  private static List<DeletionRow> buildDeletionRows() {
    return List.of(
        d(1, "DURABLE_ENQUEUE_WHILE_NETWORK_DENIED", QueueState.DELETE_PENDING, DeletionSubstatus.DELETE_WAITING_NETWORK, false, "NULL", null, null, 0, 0, 0, 0),
        d(2, "PRE_ACCEPTANCE_RESUME", QueueState.DELETE_PENDING, DeletionSubstatus.DELETE_RETRY_SCHEDULED, false, "NULL", null, null, 1, 0, 0, 0),
        d(3, "ACCEPTED_RESPONSE_OR_RECEIPT_POLL_PENDING", QueueState.DELETE_PENDING, DeletionSubstatus.DELETE_RECEIPT_POLL_ELIGIBLE, false, "REQUIRED_IMMUTABLE", null, null, 1, 0, 0, 0),
        d(4, "NETWORK_DENIED_AFTER_ACCEPTANCE", QueueState.DELETE_PENDING, DeletionSubstatus.DELETE_WAITING_NETWORK, false, "REQUIRED_IMMUTABLE", null, null, 0, 0, 0, 0),
        d(5, "RETRYABLE_FAILURE_OR_BACKOFF", QueueState.DELETE_PENDING, DeletionSubstatus.DELETE_RETRY_SCHEDULED, false, "PRESERVE_PHASE", null, null, 1, 0, 0, 0),
        d(6, "PROFILE_INVALID", QueueState.DELETE_PENDING, DeletionSubstatus.DELETE_REVALIDATION_REQUIRED, false, "PRESERVE_PHASE", DeletionError.DELETE_PROFILE_REVALIDATION_REQUIRED, null, 0, 0, 0, 0),
        d(7, "SCOPE_INVALID", QueueState.DELETE_PENDING, DeletionSubstatus.DELETE_REVALIDATION_REQUIRED, false, "PRESERVE_PHASE", DeletionError.DELETE_SCOPE_REVALIDATION_REQUIRED, null, 0, 0, 0, 0),
        d(8, "FINAL_TLS_TRUST_OR_NAME_REJECT", QueueState.DELETE_PENDING, DeletionSubstatus.DELETE_USER_ACTION_REQUIRED, false, "PRESERVE_PHASE", DeletionError.DELETE_TLS_TRUST_OR_NAME_REJECTED, null, 1, 0, 0, 0),
        d(9, "FINAL_SCHEMA_OR_FORMAT_REJECT", QueueState.DELETE_PENDING, DeletionSubstatus.DELETE_USER_ACTION_REQUIRED, false, "PRESERVE_PHASE", DeletionError.DELETE_SCHEMA_OR_FORMAT_REJECTED, null, 1, 0, 0, 0),
        d(10, "FINAL_RESPONSE_INTEGRITY_REJECT", QueueState.DELETE_PENDING, DeletionSubstatus.DELETE_USER_ACTION_REQUIRED, false, "PRESERVE_PHASE", DeletionError.DELETE_RESPONSE_INTEGRITY_REJECTED, null, 1, 0, 0, 0),
        d(11, "FINAL_IDEMPOTENCY_PAYLOAD_MISMATCH", QueueState.DELETE_PENDING, DeletionSubstatus.DELETE_USER_ACTION_REQUIRED, false, "PRESERVE_PHASE", DeletionError.DELETE_IDEMPOTENCY_PAYLOAD_MISMATCH, null, 1, 0, 0, 0),
        d(12, "RETRY_BUDGET_EXHAUSTED", QueueState.DELETE_PENDING, DeletionSubstatus.DELETE_MANUAL_RETRY_REQUIRED, false, "PRESERVE_PHASE", DeletionError.DELETE_FINITE_BUDGET_EXHAUSTED, null, 0, 0, 0, 0),
        d(13, "CANCEL_REQUESTED_WHILE_DELETE_PENDING", QueueState.DELETE_PENDING, null, true, "PRESERVE_PHASE", DeletionError.CANCEL_NOT_APPLICABLE_DELETE_PENDING, null, 0, 0, 0, 0),
        d(14, "DELETE_REVALIDATED_OR_USER_ACTION_CONFIRMED_AFTER_ACCEPTANCE", QueueState.DELETE_PENDING, DeletionSubstatus.DELETE_RECEIPT_POLL_ELIGIBLE, false, "REQUIRED_IMMUTABLE", null, null, 1, 1, 0, 0),
        d(15, "VERIFIED_SCOPED_RECEIPT", QueueState.DELETED_REMOTE, null, false, "REQUIRED_IMMUTABLE", null, ReceiptOutcome.VERIFIED, 1, 0, 1, 1));
  }

  private static DeletionRow d(
      int ordinal,
      String event,
      QueueState state,
      DeletionSubstatus substatus,
      boolean preserve,
      String idOutcome,
      DeletionError error,
      ReceiptOutcome receipt,
      int attempt,
      int grant,
      int effect,
      int deletionEffect) {
    return new DeletionRow(
        ordinal,
        event,
        state,
        substatus,
        preserve,
        idOutcome,
        error,
        receipt,
        attempt,
        grant,
        effect,
        deletionEffect);
  }

  public static String logicalKeyHash(OperationClass operationClass, String opaqueLogicalKeyId) {
    requirePresent(operationClass);
    return framedHash(
        "OFF_I1_LOGICAL_KEY_V1",
        field("operationClass", operationClass.name()),
        field("opaqueLogicalKeyId", opaque(opaqueLogicalKeyId)));
  }

  public static String jobIdHash(String logicalKeyHash, String opaqueJobId) {
    requireHash(logicalKeyHash);
    return framedHash(
        "OFF_I1_JOB_ID_V1",
        field("logicalKeyHash", logicalKeyHash),
        field("opaqueJobId", opaque(opaqueJobId)));
  }

  public static String resultIdHash(String jobIdHash, String opaqueResultId) {
    requireHash(jobIdHash);
    return framedHash(
        "OFF_I1_RESULT_ID_V1",
        field("jobIdHash", jobIdHash),
        field("opaqueResultId", opaque(opaqueResultId)));
  }

  public static String deletionScopeDigest(String opaqueScopeId) {
    return framedHash(
        "OFF_I1_DELETION_SCOPE_V1",
        field("scopeClass", "REMOTE_CONVERSATION_COPY"),
        field("opaqueScopeId", opaque(opaqueScopeId)));
  }

  public static String deletionIdHash(
      String logicalKeyHash, String deletionScopeDigest, String opaqueDeletionId) {
    requireHash(logicalKeyHash);
    requireHash(deletionScopeDigest);
    return framedHash(
        "OFF_I1_DELETION_ID_V1",
        field("logicalKeyHash", logicalKeyHash),
        field("deletionScopeDigest", deletionScopeDigest),
        field("opaqueDeletionId", opaque(opaqueDeletionId)));
  }

  public static String deletionReceiptIdHash(
      String deletionIdHash, String deletionScopeDigest, String opaqueReceiptId) {
    requireHash(deletionIdHash);
    requireHash(deletionScopeDigest);
    return framedHash(
        "OFF_I1_DELETION_RECEIPT_ID_V1",
        field("deletionIdHash", deletionIdHash),
        field("deletionScopeDigest", deletionScopeDigest),
        field("opaqueDeletionReceiptId", opaque(opaqueReceiptId)));
  }

  public static String replayInputDigest(
      OperationClass operationClass,
      String logicalKeyHash,
      InputVariant inputVariant,
      String deletionScopeDigest) {
    requirePresent(operationClass);
    requirePresent(inputVariant);
    requireHash(logicalKeyHash);
    if (operationClass == OperationClass.NON_DELETION_CLOUD_INTENT) {
      if (deletionScopeDigest != null) {
        throw fault(Diagnostic.INVALID_015);
      }
    } else {
      requireHash(deletionScopeDigest);
    }
    return framedHash(
        "OFF_I1_REPLAY_INPUT_V1",
        field("operationClass", operationClass.name()),
        field("logicalKeyHash", logicalKeyHash),
        field("inputVariant", inputVariant.name()),
        deletionScopeDigest == null
            ? nullField("deletionScopeDigest")
            : field("deletionScopeDigest", deletionScopeDigest));
  }

  public static String processingRequestIdHash(String opaqueProcessingRequestId) {
    return sha256(CONTRACT_ID + "\nPROCESSING_REQUEST\n" + opaque(opaqueProcessingRequestId));
  }

  public static String queueIntentIdHash(String opaqueQueueIntentId) {
    return sha256(CONTRACT_ID + "\nQUEUE_INTENT\n" + opaque(opaqueQueueIntentId));
  }

  public static String localStateDigest(LocalState localState) {
    return framedHash(
        "OFF_I1_LOCAL_STATE_V1", field("local", requirePresent(localState).name()));
  }

  public static byte[] encodeStateVector(StateVector vector) {
    requirePresent(vector);
    StringBuilder out = new StringBuilder(192);
    out.append('{');
    member(out, "local", vector.local.name());
    out.append(',');
    member(out, "processingCapability", vector.processingCapability.name());
    out.append(',');
    member(out, "connectivity", vector.connectivity.name());
    out.append(',');
    member(out, "model", vector.model.name());
    out.append(',');
    member(out, "queue", vector.queue.name());
    out.append('}');
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  public static String stateVectorDigest(StateVector vector) {
    return sha256(encodeStateVector(vector));
  }

  public static RevalidationDecision revalidate(RevalidationInput input) {
    requirePresent(input);
    QueueRow row = input.row;
    DeletionPhase phase;
    try {
      phase = deriveDeletionPhase(row);
    } catch (ContractFault fault) {
      return invalidRevalidation(fault.diagnostic);
    }

    if (input.scriptedDeletionRowOrdinal != null
        && (input.scriptedDeletionRowOrdinal < 1 || input.scriptedDeletionRowOrdinal > 15)) {
      return invalidRevalidation(Diagnostic.INVALID_014);
    }
    if (input.row.attemptCount != input.budget.attemptCount) {
      return invalidRevalidation(Diagnostic.INVALID_015);
    }

    if (phase == DeletionPhase.VERIFIED) {
      return new RevalidationDecision(
          RevalidationOutcome.NO_STATE_CHANGE,
          null,
          row.queueState,
          null,
          0,
          Diagnostic.NO_ELIGIBLE_RULE);
    }
    if (!validRelationship(input, phase)) {
      return invalidRevalidation(Diagnostic.INVALID_015);
    }

    boolean deletion = row.operationClass == OperationClass.DELETE_CLOUD_COPY;
    if (deletion && input.scriptedDeletionRowOrdinal == 13) {
      return decision(
          RevalidationOutcome.ALLOW_DELETE_CONSENT_NOT_APPLICABLE,
          "OFF-I1-RULE-DP-013",
          QueueState.DELETE_PENDING,
          0);
    }
    if (!deletion && input.consent != Consent.CURRENT) {
      return decision(
          RevalidationOutcome.CANCEL_NON_DELETION_CONSENT_INVALID,
          "OFF-I1-RULE-Q-001",
          QueueState.CANCELLED,
          0);
    }
    if (input.profile != Profile.CURRENT) {
      if (!deletion) {
        return decision(
            RevalidationOutcome.BLOCK_PROFILE_REVALIDATION_REQUIRED,
            "OFF-I1-RULE-Q-003",
            QueueState.PENDING_UPLOAD,
            0);
      }
      String id =
          input.profile == Profile.SCOPE_MISMATCH
              ? "OFF-I1-RULE-D-007"
              : "OFF-I1-RULE-D-006";
      int requiredRow = input.profile == Profile.SCOPE_MISMATCH ? 7 : 6;
      if (!Objects.equals(input.scriptedDeletionRowOrdinal, requiredRow)) {
        return invalidRevalidation(Diagnostic.INVALID_015);
      }
      return decision(
          RevalidationOutcome.BLOCK_PROFILE_REVALIDATION_REQUIRED,
          id,
          QueueState.DELETE_PENDING,
          0);
    }
    if (!deletion
        && input.processingRequirement == ProcessingRequirement.REQUIRED_MODEL
        && input.runtime == RuntimeStatus.MODEL_NOT_INSTALLED) {
      return processingDecision(
          RevalidationOutcome.WAIT_REQUIRED_MODEL,
          "OFF-I1-RULE-A-009-01",
          row.queueState,
          ProcessingState.WAITING_MODEL,
          0);
    }
    if (!deletion
        && input.processingRequirement == ProcessingRequirement.OPTIONAL_CAPABILITY
        && input.runtime == RuntimeStatus.MODEL_NOT_INSTALLED) {
      return processingDecision(
          RevalidationOutcome.WAIT_OPTIONAL_CAPABILITY,
          "OFF-I1-RULE-A-021-01",
          row.queueState,
          ProcessingState.PENDING_CAPABILITY,
          0);
    }
    if (!deletion
        && input.processingRequirement != ProcessingRequirement.NONE
        && input.runtime != RuntimeStatus.ELIGIBLE) {
      return decision(
          RevalidationOutcome.BLOCK_RUNTIME_REVALIDATION_REQUIRED,
          "OFF-I1-RULE-Q-003",
          QueueState.PENDING_UPLOAD,
          0);
    }
    if (input.connectivity != ConnectivityState.AVAILABLE) {
      if (!deletion) {
        if (row.queueState == QueueState.WAITING_NETWORK) {
          return decision(
              RevalidationOutcome.BLOCK_CONNECTIVITY_DENIED,
              null,
              QueueState.WAITING_NETWORK,
              0);
        }
        return decision(
            RevalidationOutcome.BLOCK_CONNECTIVITY_DENIED,
            "OFF-I1-RULE-Q-004",
            QueueState.WAITING_NETWORK,
            0);
      }
      int requiredRow = phase == DeletionPhase.PRE_ACCEPTANCE ? 1 : 4;
      if (!Objects.equals(input.scriptedDeletionRowOrdinal, requiredRow)) {
        return invalidRevalidation(Diagnostic.INVALID_015);
      }
      return decision(
          RevalidationOutcome.BLOCK_CONNECTIVITY_DENIED,
          phase == DeletionPhase.PRE_ACCEPTANCE
              ? "OFF-I1-RULE-D-001"
              : "OFF-I1-RULE-D-004",
          QueueState.DELETE_PENDING,
          0);
    }
    if (input.budget.remaining() == 0 && input.explicitGrant == ExplicitGrant.NONE) {
      if (deletion && !Objects.equals(input.scriptedDeletionRowOrdinal, 12)) {
        return invalidRevalidation(Diagnostic.INVALID_015);
      }
      return decision(
          RevalidationOutcome.BLOCK_FINITE_BUDGET_EXHAUSTED,
          deletion ? "OFF-I1-RULE-D-012" : "OFF-I1-RULE-Q-018",
          deletion ? QueueState.DELETE_PENDING : QueueState.FAILED_FINAL,
          0);
    }
    if (deletion) {
      int rowOrdinal = input.scriptedDeletionRowOrdinal;
      if (!eligibleDeletionRow(rowOrdinal, phase, input.budget.remaining(), input.explicitGrant)) {
        return invalidRevalidation(Diagnostic.INVALID_015);
      }
      return decision(
          RevalidationOutcome.ALLOW_DELETE_CONSENT_NOT_APPLICABLE,
          "OFF-I1-RULE-DP-" + three(rowOrdinal),
          QueueState.DELETE_PENDING,
          0);
    }
    if (row.queueState == QueueState.PENDING_UPLOAD) {
      return decision(RevalidationOutcome.ALLOW, null, QueueState.PENDING_UPLOAD, 0);
    }
    if (row.queueState == QueueState.WAITING_NETWORK) {
      return decision(
          RevalidationOutcome.ALLOW,
          "OFF-I1-RULE-Q-009",
          QueueState.PENDING_UPLOAD,
          0);
    }
    boolean hasJob = row.jobIdHash != null;
    long grantDelta = input.budget.remaining() == 0 ? 1 : 0;
    return decision(
        RevalidationOutcome.ALLOW,
        hasJob ? "OFF-I1-RULE-Q-017" : "OFF-I1-RULE-Q-016",
        hasJob ? QueueState.REMOTE_PROCESSING : QueueState.PENDING_UPLOAD,
        grantDelta);
  }

  private static RevalidationDecision invalidRevalidation(Diagnostic diagnostic) {
    return new RevalidationDecision(
        RevalidationOutcome.INVALID_INPUT, null, null, null, 0, diagnostic);
  }

  private static RevalidationDecision decision(
      RevalidationOutcome outcome, String rule, QueueState target, long grantDelta) {
    return new RevalidationDecision(outcome, rule, target, null, grantDelta, Diagnostic.NONE);
  }

  private static RevalidationDecision processingDecision(
      RevalidationOutcome outcome,
      String rule,
      QueueState queueTarget,
      ProcessingState processingTarget,
      long grantDelta) {
    return new RevalidationDecision(
        outcome, rule, queueTarget, processingTarget, grantDelta, Diagnostic.NONE);
  }

  private static boolean validRelationship(RevalidationInput input, DeletionPhase phase) {
    QueueRow row = input.row;
    boolean deletion = row.operationClass == OperationClass.DELETE_CLOUD_COPY;
    if (deletion) {
      if (input.consent != Consent.NOT_APPLICABLE_DELETE
          || input.processingRequirement != ProcessingRequirement.NONE
          || input.runtime != RuntimeStatus.NOT_REQUIRED
          || (phase != DeletionPhase.PRE_ACCEPTANCE && phase != DeletionPhase.POST_ACCEPTANCE)
          || row.queueState != QueueState.DELETE_PENDING
          || input.scriptedDeletionRowOrdinal == null) {
        return false;
      }
    } else {
      if (input.consent == Consent.NOT_APPLICABLE_DELETE
          || phase != DeletionPhase.NOT_APPLICABLE
          || input.scriptedDeletionRowOrdinal != null
          || !EnumSet.of(
                  QueueState.PENDING_UPLOAD,
                  QueueState.WAITING_NETWORK,
                  QueueState.FAILED_RETRYABLE)
              .contains(row.queueState)) {
        return false;
      }
      if ((input.processingRequirement == ProcessingRequirement.NONE)
          != (input.runtime == RuntimeStatus.NOT_REQUIRED)) {
        return false;
      }
    }
    long remaining = input.budget.remaining();
    if (input.explicitGrant == ExplicitGrant.ELIGIBLE_EXPLICIT_GRANT) {
      if (remaining != 0) {
        return false;
      }
      if (deletion) {
        return phase == DeletionPhase.POST_ACCEPTANCE && input.scriptedDeletionRowOrdinal == 14;
      }
      return row.queueState == QueueState.FAILED_RETRYABLE;
    }
    if (deletion && input.scriptedDeletionRowOrdinal == 14) {
      return false;
    }
    return true;
  }

  private static boolean eligibleDeletionRow(
      int row, DeletionPhase phase, long remaining, ExplicitGrant grant) {
    if (row < 1 || row > 15) {
      return false;
    }
    boolean pre = phase == DeletionPhase.PRE_ACCEPTANCE;
    boolean post = phase == DeletionPhase.POST_ACCEPTANCE;
    boolean phaseAllowed =
        switch (row) {
          case 1, 2, 3 -> pre;
          case 4, 14, 15 -> post;
          default -> pre || post;
        };
    if (!phaseAllowed) {
      return false;
    }
    if (row == 14) {
      return remaining == 0 && grant == ExplicitGrant.ELIGIBLE_EXPLICIT_GRANT;
    }
    if (grant != ExplicitGrant.NONE) {
      return false;
    }
    return Set.of(2, 3, 5, 8, 9, 10, 11, 15).contains(row) && remaining > 0;
  }

  public static DeletionPhase deriveDeletionPhase(QueueRow row) {
    requirePresent(row);
    if (row.operationClass == OperationClass.NON_DELETION_CLOUD_INTENT) {
      if (row.deletionScopeDigest != null
          || row.deletionIdHash != null
          || row.deletionReceiptIdHash != null
          || row.deletionSubstatus != null
          || row.contentFreeDeletionErrorCode != null
          || row.deletionReceiptVerificationOutcome != null) {
        throw fault(Diagnostic.INVALID_011);
      }
      return DeletionPhase.NOT_APPLICABLE;
    }
    if (row.queueState == QueueState.DELETE_PENDING
        && row.deletionIdHash == null
        && row.deletionReceiptIdHash == null
        && row.deletionReceiptVerificationOutcome == null) {
      return DeletionPhase.PRE_ACCEPTANCE;
    }
    if (row.queueState == QueueState.DELETE_PENDING
        && row.deletionIdHash != null
        && row.deletionReceiptIdHash == null
        && row.deletionReceiptVerificationOutcome == null) {
      return DeletionPhase.POST_ACCEPTANCE;
    }
    if (row.queueState == QueueState.DELETED_REMOTE
        && row.deletionIdHash != null
        && row.deletionReceiptIdHash != null
        && row.deletionReceiptVerificationOutcome == ReceiptOutcome.VERIFIED
        && row.deletionSubstatus == null
        && row.contentFreeDeletionErrorCode == null) {
      return DeletionPhase.VERIFIED;
    }
    throw fault(Diagnostic.INVALID_011);
  }

  /** Creates one deterministic, synthetic fixture for the selected inherited scenario. */
  public static RunState startScenario(String scenarioId) {
    requireScenarioId(scenarioId);
    if ("OFF-SYN-026".equals(scenarioId)) {
      throw fault(Diagnostic.INVALID_014);
    }
    return startScenario(scenarioId, null, null);
  }

  /** Creates a scenario-026 fixture at one exact row and one exact source phase. */
  public static RunState startDeletionScenario(int rowOrdinal, DeletionPhase sourcePhase) {
    if (sourcePhase == null) {
      throw fault(Diagnostic.INVALID_011);
    }
    return startScenario("OFF-SYN-026", rowOrdinal, sourcePhase);
  }

  private static RunState startScenario(
      String scenarioId, Integer scriptedDeletionRowOrdinal, DeletionPhase sourcePhase) {
    requireScenarioId(scenarioId);
    if (!"OFF-SYN-026".equals(scenarioId)
        && (scriptedDeletionRowOrdinal != null || sourcePhase != null)) {
      throw fault(Diagnostic.INVALID_015);
    }
    StateVector vector =
        new StateVector(
            "OFF-SYN-001".equals(scenarioId)
                ? LocalState.FRESH_LOCAL_DEFAULT
                : LocalState.LOCAL_READY,
            ProcessingState.PROCESSING_NOT_REQUESTED,
            ConnectivityState.NETWORK_DENIED,
            ModelState.MODEL_NOT_INSTALLED,
            QueueState.LOCAL_ONLY);
    AttemptBudget budget = new AttemptBudget(0, 0);
    List<QueueRow> queue = new ArrayList<>();
    List<ReplayRecord> replay = new ArrayList<>();

    switch (scenarioId) {
      case "OFF-SYN-014" -> {
        vector = vector.withQueue(QueueState.WAITING_NETWORK);
        QueueRow row = seedNonDeletion(scenarioId, vector, QueueState.WAITING_NETWORK, 0, null, null, 0, 0);
        queue.add(row);
        replay.add(seedReplay(row, vector, "OFF-I1-RULE-Q-008"));
      }
      case "OFF-SYN-017" -> {
        vector =
            new StateVector(
                LocalState.LOCAL_READY,
                ProcessingState.PROCESSING_NOT_REQUESTED,
                ConnectivityState.AVAILABLE,
                ModelState.MODEL_NOT_INSTALLED,
                QueueState.WAITING_NETWORK);
        QueueRow row = seedNonDeletion(scenarioId, vector, QueueState.WAITING_NETWORK, 0, null, null, 0, 0);
        queue.add(row);
        replay.add(seedReplay(row, vector, "OFF-I1-RULE-Q-008"));
      }
      case "OFF-SYN-018" -> {
        vector =
            new StateVector(
                LocalState.LOCAL_READY,
                ProcessingState.PROCESSING_QUEUED,
                ConnectivityState.AVAILABLE,
                ModelState.MODEL_NOT_INSTALLED,
                QueueState.REMOTE_PROCESSING);
        budget = new AttemptBudget(1, 0);
        QueueRow row =
            seedNonDeletion(
                scenarioId,
                vector,
                QueueState.REMOTE_PROCESSING,
                1,
                fixtureJobHash(scenarioId),
                null,
                1,
                0);
        queue.add(row);
        replay.add(seedReplay(row, vector, "OFF-I1-RULE-Q-011"));
      }
      case "OFF-SYN-022", "OFF-SYN-025" -> {
        vector = vector.withQueue(QueueState.PENDING_UPLOAD);
        QueueRow row = seedNonDeletion(scenarioId, vector, QueueState.PENDING_UPLOAD, 0, null, null, 0, 0);
        queue.add(row);
        replay.add(seedReplay(row, vector, "OFF-I1-RULE-Q-007"));
      }
      case "OFF-SYN-026" -> {
        if (sourcePhase == null) {
          throw fault(Diagnostic.INVALID_011);
        }
        if (scriptedDeletionRowOrdinal == null) {
          throw fault(Diagnostic.INVALID_014);
        }
        if (scriptedDeletionRowOrdinal < 1 || scriptedDeletionRowOrdinal > 15) {
          throw fault(Diagnostic.INVALID_014);
        }
        boolean phaseAllowed =
            eligibleSourcePhase(scriptedDeletionRowOrdinal, sourcePhase);
        if (!phaseAllowed) {
          throw fault(Diagnostic.INVALID_015);
        }
        long attemptCount = deletionSeedAttempt(scriptedDeletionRowOrdinal);
        budget = new AttemptBudget(attemptCount, 0);
        vector = deletionInitialState(scriptedDeletionRowOrdinal);
        QueueRow row = seedDeletion(scenarioId, vector, sourcePhase, attemptCount);
        queue.add(row);
        replay.add(seedReplay(row, vector, "OFF-I1-RULE-D-001"));
      }
      default -> {
        // The local-only default has no queue or replay row.
      }
    }
    return new RunState(
        scenarioId,
        1,
        0,
        budget,
        vector,
        List.of(),
        queue,
        sortedReplay(replay),
        null);
  }

  private static long deletionSeedAttempt(int rowOrdinal) {
    return rowOrdinal == 12 || rowOrdinal == 14 ? 3 : 0;
  }

  private static StateVector deletionInitialState(int rowOrdinal) {
    if (rowOrdinal < 1 || rowOrdinal > 15) {
      throw fault(Diagnostic.INVALID_014);
    }
    return new StateVector(
        LocalState.LOCAL_OPERATION_SUCCEEDED,
        ProcessingState.PROCESSING_NOT_REQUESTED,
        rowOrdinal == 1 || rowOrdinal == 4
            ? ConnectivityState.NETWORK_DENIED
            : ConnectivityState.AVAILABLE,
        ModelState.MODEL_NOT_INSTALLED,
        QueueState.DELETE_PENDING);
  }

  /** Executes exactly the next inherited action. Restore actions require canonical bytes. */
  public static Step execute(RunState prior) {
    return execute(prior, null, null);
  }

  /** Executes exactly the next inherited action, with explicit restore bytes when required. */
  public static Step execute(RunState prior, byte[] restoreBytes) {
    return execute(prior, restoreBytes, null);
  }

  /** Executes scenario 026 and binds both actions to one immutable row ordinal. */
  public static Step executeDeletion(RunState prior, int scriptedDeletionRowOrdinal) {
    return execute(prior, null, scriptedDeletionRowOrdinal);
  }

  private static Step execute(
      RunState prior, byte[] restoreBytes, Integer scriptedDeletionRowOrdinal) {
    requirePresent(prior);
    if (prior.nextActionOrdinal > scenarioActionCount(prior.scenarioId)) {
      return invalidStep(prior, Diagnostic.INVALID_003);
    }
    ActionSpec template = action(prior.scenarioId, prior.nextActionOrdinal);
    if (("OFF-SYN-026".equals(prior.scenarioId)) != (scriptedDeletionRowOrdinal != null)) {
      return invalidStep(prior, Diagnostic.INVALID_015);
    }

    RunState base = prior;
    if ("RESTORE:=".equals(template.transition)) {
      if (restoreBytes == null) {
        return invalidStep(prior, Diagnostic.INVALID_013);
      }
      try {
        RunState restored = decodeSnapshot(restoreBytes);
        if (!restored.scenarioId.equals(prior.scenarioId)
            || restored.nextActionOrdinal != prior.nextActionOrdinal
            || !Arrays.equals(restoreBytes, encodeSnapshot(prior))) {
          return invalidStep(prior, Diagnostic.INVALID_009);
        }
        base = restored;
      } catch (ContractFault fault) {
        return invalidStep(prior, fault.diagnostic);
      }
    } else if (restoreBytes != null) {
      return invalidStep(prior, Diagnostic.INVALID_015);
    }

    Mutation mutation;
    try {
      mutation =
          mutate(base, template, scriptedDeletionRowOrdinal == null ? 0 : scriptedDeletionRowOrdinal);
    } catch (ContractFault fault) {
      return invalidStep(prior, fault.diagnostic);
    }
    long offset;
    try {
      offset = checkedAdd(base.monotonicOffsetMs, 1);
    } catch (ContractFault fault) {
      return invalidStep(prior, fault.diagnostic);
    }
    QueueRow queueAppend = materializeQueueAppend(mutation, base.stateVector);
    long sequence = base.flowLedger.size() + 1L;
    String processingHash =
        base.stateVector.processingCapability == ProcessingState.PROCESSING_NOT_REQUESTED
                && mutation.post.processingCapability == ProcessingState.PROCESSING_NOT_REQUESTED
            ? null
            : processingRequestIdHash("processing." + scenarioSuffix(base.scenarioId));
    QueueRow projected = queueAppend != null ? queueAppend : currentQueue(base);
    String queueIntentHash = projected == null ? null : projected.intentIdHash;
    FlowRow flowAppend =
        new FlowRow(
            sequence,
            base.scenarioId,
            template.actionId,
            base.stateVector,
            mutation.post,
            mutation.outcome,
            offset,
            stateVectorDigest(base.stateVector),
            stateVectorDigest(mutation.post),
            processingHash,
            queueIntentHash,
            mutation.diagnostic == Diagnostic.NONE ? null : mutation.diagnostic);
    TypedLedgerDeltas deltas =
        new TypedLedgerDeltas(flowAppend, queueAppend, mutation.diagnostic);
    int effect = queueAppend == null ? currentEffect(base) : queueAppend.effectCount;
    int apply = queueAppend == null ? currentApply(base) : queueAppend.applyCount;
    int deletionEffect =
        queueAppend == null ? currentDeletionEffect(base) : queueAppend.remoteDeletionEffectCount;
    Result result =
        new Result(
            mutation.ruleId,
            mutation.outcome,
            mutation.post,
            deltas,
            effect,
            apply,
            deletionEffect);

    List<FlowRow> flow = append(base.flowLedger, flowAppend);
    List<QueueRow> queue =
        queueAppend == null ? base.queueLedger : append(base.queueLedger, queueAppend);
    List<ReplayRecord> replay =
        updateReplay(base.replayRecords, queueAppend, mutation, result.postStateVector);
    RunState run =
        new RunState(
            base.scenarioId,
            base.nextActionOrdinal + 1,
            offset,
            mutation.budget,
            mutation.post,
            flow,
            queue,
            replay,
            result);
    byte[] snapshot = "SNAPSHOT:=".equals(template.transition) ? encodeSnapshot(run) : null;
    return new Step(run, result, snapshot);
  }

  private record Mutation(
      String ruleId,
      ReducerOutcome outcome,
      Diagnostic diagnostic,
      StateVector post,
      AttemptBudget budget,
      QueueRow queueAppend,
      ReplayRecord replayOverride,
      StateVector replayWitnessOverride,
      boolean preserveReplay) {}

  private record MutationInput(
      String scenarioId,
      StateVector stateVector,
      AttemptBudget budget,
      QueueRow currentQueue,
      ReplayRecord cachedReplay,
      StateVector cachedReplayWitness) {}

  private record DeletionMutationInput(
      String scenarioId,
      StateVector stateVector,
      AttemptBudget budget,
      QueueRow currentQueue,
      String previousSelectedRuleId) {}

  private static Mutation mutate(
      RunState base, ActionSpec action, int scriptedDeletionRowOrdinal) {
    if (action.template) {
      return mutateDeletion(base, action, scriptedDeletionRowOrdinal);
    }
    QueueRow current = currentQueue(base);
    ReplayRecord cached = current == null ? null : replayFor(base.replayRecords, current.logicalKeyHash);
    StateVector witness = cached == null ? null : replayWitness(base, cached);
    return mutateFixed(
        new MutationInput(
            base.scenarioId, base.stateVector, base.budget, current, cached, witness),
        action);
  }

  private static Mutation mutateFixed(MutationInput base, ActionSpec action) {
    if (action.template) {
      throw fault(Diagnostic.INVALID_015);
    }
    QueueSignal queueSignal = queueSignalForAction(action.actionId);
    if (queueSignal != null) {
      QueueReduction reduction =
          reduceQueue(
              new QueueReducerInput(
                  base.scenarioId,
                  base.stateVector,
                  base.budget,
                  base.currentQueue,
                  base.cachedReplay,
                  base.cachedReplayWitness,
                  Set.of(queueSignal),
                  ExplicitGrant.NONE));
      if (!Objects.equals(reduction.selectedRuleId, action.selectedRuleId)
          || reduction.outcome != action.outcome
          || reduction.flowLedgerAppend != action.flowLedgerAppend
          || reduction.queueLedgerAppend != action.queueLedgerAppend) {
        throw fault(Diagnostic.INVALID_003);
      }
      return new Mutation(
          reduction.selectedRuleId,
          reduction.outcome,
          reduction.diagnostic,
          reduction.postStateVector,
          reduction.budget,
          reduction.queueAppend,
          reduction.replayRecord,
          reduction.replayPostStateVector,
          false);
    }
    StateVector pre = base.stateVector;
    StateVector post = pre;
    AttemptBudget budget = base.budget;
    QueueRow queueAppend = null;
    Diagnostic diagnostic = Diagnostic.NONE;
    ReducerOutcome outcome = action.outcome;
    String ruleId = action.selectedRuleId;
    boolean preserveReplay = false;

    switch (action.transition) {
      case "=" -> post = pre;
      case "L:LOCAL_OPERATION_RUNNING" -> post = pre.withLocal(LocalState.LOCAL_OPERATION_RUNNING);
      case "L:LOCAL_OPERATION_SUCCEEDED", "L:LOCAL_OPERATION_SUCCEEDED;P:PRESERVE" ->
          post = pre.withLocal(LocalState.LOCAL_OPERATION_SUCCEEDED);
      case "L:LOCAL_OPERATION_SUCCEEDED;Q:PRESERVE_WAITING_NETWORK" -> {
        if (pre.queue != QueueState.WAITING_NETWORK) {
          throw fault(Diagnostic.INVALID_015);
        }
        post = pre.withLocal(LocalState.LOCAL_OPERATION_SUCCEEDED);
      }
      case "L:PRESERVE", "SNAPSHOT:=", "RESTORE:=" -> post = pre;
      case "P:PENDING_CAPABILITY" -> post = pre.withProcessing(ProcessingState.PENDING_CAPABILITY);
      case "P:PROCESSING_SUCCEEDED" -> post = pre.withProcessing(ProcessingState.PROCESSING_SUCCEEDED);
      case "P:WAITING_MODEL" -> post = pre.withProcessing(ProcessingState.WAITING_MODEL);
      case "Q:APPLIED;APPLY:0_TO_1;EFFECT:PRESERVE_ONE" -> {
        QueueRow current = requireCurrentNonDeletion(base);
        if (current.effectCount != 1 || current.applyCount != 0) {
          throw fault(Diagnostic.INVALID_015);
        }
        String resultHash =
            current.resultIdHash != null
                ? current.resultIdHash
                : resultIdHash(current.jobIdHash, "result." + scenarioSuffix(base.scenarioId));
        post = pre.withQueue(QueueState.APPLIED);
        queueAppend = copyNonDeletion(current, QueueState.APPLIED, current.attemptCount, current.jobIdHash, resultHash, 1, 1, current.replayMarker);
      }
      case "Q:DELETE_PENDING;SUBSTATUS:DELETE_WAITING_NETWORK", "Q:APPEND_DELETE_PENDING_ROW_AND_PROJECT" -> {
        post = pre.withQueue(QueueState.DELETE_PENDING);
        queueAppend =
            seedDeletion(
                base.scenarioId,
                post,
                DeletionPhase.PRE_ACCEPTANCE,
                0,
                DeletionSubstatus.DELETE_WAITING_NETWORK);
        budget = new AttemptBudget(0, 0);
      }
      default -> throw fault(Diagnostic.INVALID_014);
    }
    return new Mutation(
        ruleId, outcome, diagnostic, post, budget, queueAppend, null, null, preserveReplay);
  }

  private static Mutation mutateDeletion(RunState base, ActionSpec action, int rowOrdinal) {
    return mutateDeletion(
        new DeletionMutationInput(
            base.scenarioId,
            base.stateVector,
            base.budget,
            currentQueue(base),
            base.lastResult == null ? null : base.lastResult.selectedRuleId),
        action,
        rowOrdinal);
  }

  private static Mutation mutateDeletion(
      DeletionMutationInput base, ActionSpec action, int rowOrdinal) {
    if (rowOrdinal < 1 || rowOrdinal > 15) {
      throw fault(Diagnostic.INVALID_014);
    }
    if (!action.template) {
      throw fault(Diagnostic.INVALID_015);
    }
    QueueRow current = base.currentQueue;
    if (current == null || current.operationClass != OperationClass.DELETE_CLOUD_COPY) {
      throw fault(Diagnostic.INVALID_011);
    }
    DeletionRow row = DELETION_ROWS.get(rowOrdinal - 1);
    DeletionPhase phase = deriveDeletionPhase(current);
    if (!eligibleSourcePhase(rowOrdinal, phase)) {
      throw fault(Diagnostic.INVALID_015);
    }
    if (action.ordinal == 1) {
      AttemptBudget budget = base.budget;
      if (rowOrdinal == 14) {
        if (budget.remaining() != 0) {
          throw fault(Diagnostic.INVALID_015);
        }
        budget = budget.grantThenAttempt();
      } else if (row.dispatchAttemptDelta == 1) {
        budget = budget.attempt();
      }
      QueueRow audit =
          copyDeletion(
              current,
              current.queueState,
              current.deletionSubstatus,
              current.contentFreeDeletionErrorCode,
              current.deletionReceiptVerificationOutcome,
              budget.attemptCount,
              current.deletionIdHash,
              current.deletionReceiptIdHash,
              current.effectCount,
              current.remoteDeletionEffectCount);
      return new Mutation(
          "OFF-I1-RULE-DP-" + three(rowOrdinal),
          ReducerOutcome.TRANSITION_APPLIED,
          Diagnostic.NONE,
          base.stateVector,
          budget,
          audit,
          null,
          null,
          false);
    }
    if (!Objects.equals(
        base.previousSelectedRuleId, "OFF-I1-RULE-DP-" + three(rowOrdinal))) {
      throw fault(Diagnostic.INVALID_015);
    }
    String deletionId = current.deletionIdHash;
    if (rowOrdinal == 3 && deletionId == null) {
      deletionId =
          deletionIdHash(
              current.logicalKeyHash,
              current.deletionScopeDigest,
              "deletion." + scenarioSuffix(base.scenarioId));
    }
    if ((rowOrdinal == 4 || rowOrdinal == 14 || rowOrdinal == 15) && deletionId == null) {
      throw fault(Diagnostic.INVALID_011);
    }
    String receiptId = current.deletionReceiptIdHash;
    if (rowOrdinal == 15) {
      receiptId =
          deletionReceiptIdHash(
              deletionId,
              current.deletionScopeDigest,
              "receipt." + scenarioSuffix(base.scenarioId));
    }
    DeletionSubstatus substatus =
        row.preserveCurrentSubstatus ? current.deletionSubstatus : row.deletionSubstatus;
    QueueRow reconciled =
        copyDeletion(
            current,
            row.queueState,
            substatus,
            row.error,
            row.receiptOutcome,
            current.attemptCount,
            deletionId,
            receiptId,
            rowOrdinal == 15 ? 1 : current.effectCount,
            rowOrdinal == 15 ? 1 : current.remoteDeletionEffectCount);
    ReducerOutcome outcome =
        rowOrdinal == 13
            ? ReducerOutcome.REJECTED_NO_STATE_CHANGE
            : ReducerOutcome.TRANSITION_APPLIED;
    Diagnostic diagnostic =
        rowOrdinal == 13 ? Diagnostic.POLICY_REJECTED : Diagnostic.NONE;
    StateVector post =
        rowOrdinal == 13 ? base.stateVector : base.stateVector.withQueue(row.queueState);
    return new Mutation(
        "OFF-I1-RULE-D-" + three(rowOrdinal),
        outcome,
        diagnostic,
        post,
        base.budget,
        reconciled,
        null,
        null,
        rowOrdinal == 13);
  }

  private static Step invalidStep(RunState prior, Diagnostic diagnostic) {
    QueueRow current = currentQueue(prior);
    Result result =
        new Result(
            null,
            ReducerOutcome.INVALID_INPUT,
            prior.stateVector,
            new TypedLedgerDeltas(null, null, diagnostic),
            current == null ? 0 : current.effectCount,
            current == null ? 0 : current.applyCount,
            current == null ? 0 : current.remoteDeletionEffectCount);
    return new Step(prior, result, null);
  }

  private static QueueSignal queueSignalForAction(String actionId) {
    return switch (actionId) {
      case "OFF-I1-ACT-013-01" -> QueueSignal.NEW_LOGICAL_KEY;
      case "OFF-I1-ACT-013-02" -> QueueSignal.SCHEDULER_TICK_WHILE_DENIED;
      case "OFF-I1-ACT-017-01" -> QueueSignal.ALL_REVALIDATION_ALLOW;
      case "OFF-I1-ACT-017-02" -> QueueSignal.SEND_AVAILABLE;
      case "OFF-I1-ACT-017-03" -> QueueSignal.FIRST_DURABLE_ACCEPTANCE;
      case "OFF-I1-ACT-017-04" -> QueueSignal.VALID_RESULT;
      case "OFF-I1-ACT-017-05" -> QueueSignal.USER_TRUTH_ALLOW;
      case "OFF-I1-ACT-018-01" -> QueueSignal.SAME_INPUT_REPLAY;
      case "OFF-I1-ACT-022-01", "OFF-I1-ACT-025-01" ->
          QueueSignal.CONSENT_INVALID_OR_REVOKED;
      default -> null;
    };
  }

  private static ReplayRecord replayFor(List<ReplayRecord> records, String logicalKeyHash) {
    for (ReplayRecord record : records) {
      if (record.logicalKeyHash.equals(logicalKeyHash)) {
        return record;
      }
    }
    throw fault(Diagnostic.INVALID_015);
  }

  private static ActionSpec action(String scenarioId, int ordinal) {
    for (ActionSpec action : ACTIONS) {
      if (action.scenarioId.equals(scenarioId) && action.ordinal == ordinal) {
        return action;
      }
    }
    throw fault(Diagnostic.INVALID_003);
  }

  private static boolean eligibleSourcePhase(int row, DeletionPhase phase) {
    return switch (row) {
      case 1, 2, 3 -> phase == DeletionPhase.PRE_ACCEPTANCE;
      case 4, 14, 15 -> phase == DeletionPhase.POST_ACCEPTANCE;
      default -> phase == DeletionPhase.PRE_ACCEPTANCE || phase == DeletionPhase.POST_ACCEPTANCE;
    };
  }

  private static QueueRow seedNonDeletion(
      String scenarioId,
      StateVector vector,
      QueueState state,
      long attempt,
      String job,
      String result,
      int effect,
      int apply) {
    String suffix = scenarioSuffix(scenarioId);
    String localDigest = localStateDigest(vector.local);
    return new QueueRow(
        OperationClass.NON_DELETION_CLOUD_INTENT,
        queueIntentIdHash("intent." + suffix),
        logicalKeyHash(OperationClass.NON_DELETION_CLOUD_INTENT, "logical." + suffix),
        job,
        result,
        null,
        null,
        null,
        state,
        null,
        null,
        null,
        attempt,
        ReplayMarker.ORIGINAL,
        effect,
        apply,
        0,
        localDigest,
        localDigest);
  }

  private static QueueRow seedDeletion(
      String scenarioId, StateVector vector, DeletionPhase phase, long attempt) {
    return seedDeletion(
        scenarioId,
        vector,
        phase,
        attempt,
        DeletionSubstatus.DELETE_RECEIPT_POLL_ELIGIBLE);
  }

  private static QueueRow seedDeletion(
      String scenarioId,
      StateVector vector,
      DeletionPhase phase,
      long attempt,
      DeletionSubstatus substatus) {
    String suffix = scenarioSuffix(scenarioId);
    String logical = logicalKeyHash(OperationClass.DELETE_CLOUD_COPY, "delete.logical." + suffix);
    String scope = deletionScopeDigest("scope." + suffix);
    String deletionId =
        phase == DeletionPhase.POST_ACCEPTANCE
            ? deletionIdHash(logical, scope, "deletion." + suffix)
            : null;
    String localDigest = localStateDigest(vector.local);
    return new QueueRow(
        OperationClass.DELETE_CLOUD_COPY,
        queueIntentIdHash("delete.intent." + suffix),
        logical,
        null,
        null,
        scope,
        deletionId,
        null,
        QueueState.DELETE_PENDING,
        requirePresent(substatus),
        null,
        null,
        attempt,
        ReplayMarker.ORIGINAL,
        0,
        0,
        0,
        localDigest,
        localDigest);
  }

  private static QueueRow copyNonDeletion(
      QueueRow current,
      QueueState state,
      long attempt,
      String job,
      String result,
      int effect,
      int apply,
      ReplayMarker marker) {
    return new QueueRow(
        OperationClass.NON_DELETION_CLOUD_INTENT,
        current.intentIdHash,
        current.logicalKeyHash,
        job,
        result,
        null,
        null,
        null,
        state,
        null,
        null,
        null,
        attempt,
        marker,
        effect,
        apply,
        0,
        current.preLocalStateDigest,
        current.postLocalStateDigest);
  }

  private static QueueRow copyDeletion(
      QueueRow current,
      QueueState state,
      DeletionSubstatus substatus,
      DeletionError error,
      ReceiptOutcome receipt,
      long attempt,
      String deletionId,
      String receiptId,
      int effect,
      int deletionEffect) {
    return new QueueRow(
        OperationClass.DELETE_CLOUD_COPY,
        current.intentIdHash,
        current.logicalKeyHash,
        null,
        null,
        current.deletionScopeDigest,
        deletionId,
        receiptId,
        state,
        substatus,
        error,
        receipt,
        attempt,
        current.replayMarker,
        effect,
        0,
        deletionEffect,
        current.preLocalStateDigest,
        current.postLocalStateDigest);
  }

  private static QueueRow requireCurrentNonDeletion(RunState run) {
    QueueRow current = currentQueue(run);
    if (current == null || current.operationClass != OperationClass.NON_DELETION_CLOUD_INTENT) {
      throw fault(Diagnostic.INVALID_015);
    }
    return current;
  }

  private static QueueRow requireCurrentNonDeletion(MutationInput input) {
    QueueRow current = input.currentQueue;
    if (current == null || current.operationClass != OperationClass.NON_DELETION_CLOUD_INTENT) {
      throw fault(Diagnostic.INVALID_015);
    }
    return current;
  }

  private static QueueRow currentQueue(RunState run) {
    return run.queueLedger.isEmpty() ? null : run.queueLedger.get(run.queueLedger.size() - 1);
  }

  private static QueueRow materializeQueueAppend(Mutation mutation, StateVector pre) {
    return mutation.queueAppend == null
        ? null
        : mutation.queueAppend.withLocalDigests(
            localStateDigest(pre.local), localStateDigest(mutation.post.local));
  }

  private static StateVector replayWitness(RunState run, ReplayRecord replay) {
    for (int index = run.flowLedger.size() - 1; index >= 0; index--) {
      FlowRow row = run.flowLedger.get(index);
      if (row.postStateDigest.equals(replay.postStateDigest)) {
        return row.postStateVector;
      }
      if (row.preStateDigest.equals(replay.postStateDigest)) {
        return row.preStateVector;
      }
    }
    if (stateVectorDigest(run.stateVector).equals(replay.postStateDigest)) {
      return run.stateVector;
    }
    throw fault(Diagnostic.INVALID_015);
  }

  private static int currentEffect(RunState run) {
    QueueRow current = currentQueue(run);
    return current == null ? 0 : current.effectCount;
  }

  private static int currentApply(RunState run) {
    QueueRow current = currentQueue(run);
    return current == null ? 0 : current.applyCount;
  }

  private static int currentDeletionEffect(RunState run) {
    QueueRow current = currentQueue(run);
    return current == null ? 0 : current.remoteDeletionEffectCount;
  }

  private static String fixtureJobHash(String scenarioId) {
    return jobIdHash(
        logicalKeyHash(
            OperationClass.NON_DELETION_CLOUD_INTENT,
            "logical." + scenarioSuffix(scenarioId)),
        "job." + scenarioSuffix(scenarioId));
  }

  private static ReplayRecord seedReplay(
      QueueRow queue, StateVector state, String selectedRuleId) {
    return new ReplayRecord(
        queue.logicalKeyHash,
        replayInputDigest(
            queue.operationClass,
            queue.logicalKeyHash,
            InputVariant.PRIMARY,
            queue.deletionScopeDigest),
        selectedRuleId,
        ReducerOutcome.TRANSITION_APPLIED,
        stateVectorDigest(state),
        queue.resultIdHash,
        queue.replayMarker,
        queue.attemptCount,
        queue.effectCount,
        queue.applyCount,
        queue.remoteDeletionEffectCount);
  }

  private static List<ReplayRecord> updateReplay(
      List<ReplayRecord> existing, QueueRow queue, Mutation mutation, StateVector postState) {
    if (mutation.preserveReplay) {
      return existing;
    }
    ReplayRecord replacement =
        mutation.replayOverride != null
            ? mutation.replayOverride
            : queue == null
                ? null
                : new ReplayRecord(
                    queue.logicalKeyHash,
                    replayInputDigest(
                        queue.operationClass,
                        queue.logicalKeyHash,
                        InputVariant.PRIMARY,
                        queue.deletionScopeDigest),
                    mutation.ruleId,
                    mutation.outcome,
                    stateVectorDigest(postState),
                    queue.resultIdHash,
                    queue.replayMarker,
                    queue.attemptCount,
                    queue.effectCount,
                    queue.applyCount,
                    queue.remoteDeletionEffectCount);
    if (replacement == null) {
      return existing;
    }
    List<ReplayRecord> copy = new ArrayList<>(existing);
    boolean replaced = false;
    for (int index = 0; index < copy.size(); index++) {
      if (copy.get(index).logicalKeyHash.equals(replacement.logicalKeyHash)) {
        copy.set(index, replacement);
        replaced = true;
        break;
      }
    }
    if (!replaced) {
      copy.add(replacement);
    }
    return sortedReplay(copy);
  }

  private static List<ReplayRecord> sortedReplay(List<ReplayRecord> source) {
    List<ReplayRecord> result = new ArrayList<>(source);
    result.sort(Comparator.comparing(ReplayRecord::logicalKeyHash));
    return List.copyOf(result);
  }

  private static <T> List<T> append(List<T> source, T value) {
    List<T> result = new ArrayList<>(source);
    result.add(value);
    return List.copyOf(result);
  }

  private static String scenarioSuffix(String scenarioId) {
    return scenarioId.substring(scenarioId.length() - 3);
  }

  /** Encodes one complete graph in the exact canonical v0.2 snapshot schema. */
  public static byte[] encodeSnapshot(RunState snapshot) {
    requirePresent(snapshot);
    StringBuilder out = new StringBuilder(4096);
    out.append('{');
    member(out, "snapshotSchema", SNAPSHOT_SCHEMA);
    out.append(',');
    member(out, "contractId", CONTRACT_ID);
    out.append(',');
    member(out, "scenarioId", snapshot.scenarioId);
    out.append(',');
    name(out, "nextActionOrdinal");
    out.append(snapshot.nextActionOrdinal);
    out.append(',');
    name(out, "monotonicOffsetMs");
    out.append(snapshot.monotonicOffsetMs);
    out.append(',');
    name(out, "remainingAutomaticAttemptBudget");
    out.append(snapshot.budget.remaining());
    out.append(',');
    name(out, "manualRetryGrantCount");
    out.append(snapshot.budget.manualRetryGrantCount);
    out.append(',');
    name(out, "stateVector");
    appendState(out, snapshot.stateVector);
    out.append(',');
    name(out, "flowLedger");
    appendFlowArray(out, snapshot.flowLedger);
    out.append(',');
    name(out, "queueLedger");
    appendQueueArray(out, snapshot.queueLedger);
    out.append(',');
    name(out, "replayRecords");
    appendReplayArray(out, snapshot.replayRecords);
    out.append(',');
    name(out, "lastResult");
    if (snapshot.lastResult == null) {
      out.append("null");
    } else {
      appendResult(out, snapshot.lastResult);
    }
    out.append('}');
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** Strictly decodes, validates, re-encodes, and deep-copies one canonical snapshot. */
  public static RunState decodeSnapshot(byte[] candidate) {
    if (candidate == null || candidate.length == 0) {
      throw fault(Diagnostic.INVALID_013);
    }
    if (candidate.length > MAX_SNAPSHOT_BYTES) {
      throw fault(Diagnostic.INVALID_016);
    }
    String text;
    try {
      CharBuffer decoded =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(candidate.clone()));
      text = decoded.toString();
    } catch (CharacterCodingException exception) {
      throw fault(Diagnostic.INVALID_013);
    }
    if (!text.isEmpty() && text.charAt(0) == '\ufeff') {
      throw fault(Diagnostic.INVALID_013);
    }
    Object parsed = new JsonParser(text).parseDocument();
    if (!text.equals(canonicalParsedJson(parsed))) {
      throw fault(Diagnostic.INVALID_013);
    }
    preflightSnapshotStructure(parsed);
    preflightSnapshotEarlierCategories(parsed);
    preflightSnapshotStateCategories(parsed);
    preflightRawIndependentStateDigestCategory010(parsed);
    preflightSnapshotExactStateDigestRelations(parsed);
    preflightSnapshotDeletionCategories(parsed);
    RunState result;
    try {
      result = snapshotFromJson(object(parsed));
    } catch (ContractFault fault) {
      throw fault(fault.diagnostic);
    } catch (RuntimeException exception) {
      throw fault(Diagnostic.INVALID_014);
    }
    if (!Arrays.equals(candidate, encodeSnapshot(result))) {
      throw fault(Diagnostic.INVALID_013);
    }
    return result;
  }

  private static RunState snapshotFromJson(Map<String, Object> value) {
    requireKeys(
        value,
        "snapshotSchema",
        "contractId",
        "scenarioId",
        "nextActionOrdinal",
        "monotonicOffsetMs",
        "remainingAutomaticAttemptBudget",
        "manualRetryGrantCount",
        "stateVector",
        "flowLedger",
        "queueLedger",
        "replayRecords",
        "lastResult");
    if (!SNAPSHOT_SCHEMA.equals(string(value.get("snapshotSchema")))
        || !CONTRACT_ID.equals(string(value.get("contractId")))) {
      throw fault(Diagnostic.INVALID_002);
    }
    String scenarioId = string(value.get("scenarioId"));
    int next = exactInt(value.get("nextActionOrdinal"));
    long offset = nonNegativeLong(value.get("monotonicOffsetMs"));
    long remaining = nonNegativeLong(value.get("remainingAutomaticAttemptBudget"));
    long grants = nonNegativeLong(value.get("manualRetryGrantCount"));
    StateVector state = stateFromJson(object(value.get("stateVector")));
    List<FlowRow> flow = new ArrayList<>();
    for (Object row : array(value.get("flowLedger"))) {
      flow.add(flowFromJson(object(row)));
    }
    List<QueueRow> queue = new ArrayList<>();
    for (Object row : array(value.get("queueLedger"))) {
      queue.add(queueFromJson(object(row)));
    }
    List<ReplayRecord> replay = new ArrayList<>();
    for (Object row : array(value.get("replayRecords"))) {
      replay.add(replayFromJson(object(row)));
    }
    Result last =
        value.get("lastResult") == null ? null : resultFromJson(object(value.get("lastResult")));
    long attempt = queue.isEmpty() ? 0 : queue.get(queue.size() - 1).attemptCount;
    AttemptBudget budget = new AttemptBudget(attempt, grants);
    if (budget.remaining() != remaining) {
      throw fault(Diagnostic.INVALID_008);
    }
    return new RunState(
        scenarioId,
        next,
        offset,
        budget,
        state,
        flow,
        queue,
        replay,
        last);
  }

  private static void appendState(StringBuilder out, StateVector state) {
    out.append(new String(encodeStateVector(state), StandardCharsets.UTF_8));
  }

  private static StateVector stateFromJson(Map<String, Object> value) {
    requireKeys(value, "local", "processingCapability", "connectivity", "model", "queue");
    return new StateVector(
        enumValueFor(LocalState.class, value.get("local"), Diagnostic.INVALID_010),
        enumValueFor(
            ProcessingState.class,
            value.get("processingCapability"),
            Diagnostic.INVALID_010),
        enumValueFor(
            ConnectivityState.class, value.get("connectivity"), Diagnostic.INVALID_010),
        enumValueFor(ModelState.class, value.get("model"), Diagnostic.INVALID_010),
        enumValueFor(QueueState.class, value.get("queue"), Diagnostic.INVALID_010));
  }

  private static void appendFlowArray(StringBuilder out, List<FlowRow> rows) {
    out.append('[');
    for (int index = 0; index < rows.size(); index++) {
      if (index > 0) {
        out.append(',');
      }
      appendFlow(out, rows.get(index));
    }
    out.append(']');
  }

  private static void appendFlow(StringBuilder out, FlowRow row) {
    out.append('{');
    name(out, "sequence");
    out.append(row.sequence);
    out.append(',');
    member(out, "scenarioId", row.scenarioId);
    out.append(',');
    member(out, "actionId", row.actionId);
    out.append(',');
    name(out, "preStateVector");
    appendState(out, row.preStateVector);
    out.append(',');
    name(out, "postStateVector");
    appendState(out, row.postStateVector);
    out.append(',');
    member(out, "outcome", row.outcome.name());
    out.append(',');
    name(out, "monotonicOffsetMs");
    out.append(row.monotonicOffsetMs);
    out.append(',');
    member(out, "preStateDigest", row.preStateDigest);
    out.append(',');
    member(out, "postStateDigest", row.postStateDigest);
    out.append(',');
    nullableMember(out, "processingRequestIdHash", row.processingRequestIdHash);
    out.append(',');
    nullableMember(out, "queueIntentIdHash", row.queueIntentIdHash);
    out.append(',');
    nullableMember(
        out,
        "contentFreeErrorCode",
        row.contentFreeErrorCode == null ? null : row.contentFreeErrorCode.code());
    out.append('}');
  }

  private static FlowRow flowFromJson(Map<String, Object> value) {
    requireKeys(
        value,
        "sequence",
        "scenarioId",
        "actionId",
        "preStateVector",
        "postStateVector",
        "outcome",
        "monotonicOffsetMs",
        "preStateDigest",
        "postStateDigest",
        "processingRequestIdHash",
        "queueIntentIdHash",
        "contentFreeErrorCode");
    String diagnostic = nullableString(value.get("contentFreeErrorCode"));
    String preStateDigest = string(value.get("preStateDigest"));
    String postStateDigest = string(value.get("postStateDigest"));
    requireHashFor(preStateDigest, Diagnostic.INVALID_010);
    requireHashFor(postStateDigest, Diagnostic.INVALID_010);
    return new FlowRow(
        positiveLong(value.get("sequence")),
        string(value.get("scenarioId")),
        string(value.get("actionId")),
        stateFromJson(object(value.get("preStateVector"))),
        stateFromJson(object(value.get("postStateVector"))),
        enumValue(ReducerOutcome.class, value.get("outcome")),
        nonNegativeLong(value.get("monotonicOffsetMs")),
        preStateDigest,
        postStateDigest,
        nullableString(value.get("processingRequestIdHash")),
        nullableString(value.get("queueIntentIdHash")),
        diagnostic == null ? null : Diagnostic.fromCode(diagnostic));
  }

  private static void appendQueueArray(StringBuilder out, List<QueueRow> rows) {
    out.append('[');
    for (int index = 0; index < rows.size(); index++) {
      if (index > 0) {
        out.append(',');
      }
      appendQueue(out, rows.get(index));
    }
    out.append(']');
  }

  private static void appendQueue(StringBuilder out, QueueRow row) {
    out.append('{');
    member(out, "operationClass", row.operationClass.name());
    out.append(',');
    member(out, "intentIdHash", row.intentIdHash);
    out.append(',');
    member(out, "logicalKeyHash", row.logicalKeyHash);
    out.append(',');
    nullableMember(out, "jobIdHash", row.jobIdHash);
    out.append(',');
    nullableMember(out, "resultIdHash", row.resultIdHash);
    out.append(',');
    nullableMember(out, "deletionScopeDigest", row.deletionScopeDigest);
    out.append(',');
    nullableMember(out, "deletionIdHash", row.deletionIdHash);
    out.append(',');
    nullableMember(out, "deletionReceiptIdHash", row.deletionReceiptIdHash);
    out.append(',');
    member(out, "queueState", row.queueState.name());
    out.append(',');
    nullableMember(
        out,
        "deletionSubstatus",
        row.deletionSubstatus == null ? null : row.deletionSubstatus.name());
    out.append(',');
    nullableMember(
        out,
        "contentFreeDeletionErrorCode",
        row.contentFreeDeletionErrorCode == null
            ? null
            : row.contentFreeDeletionErrorCode.name());
    out.append(',');
    nullableMember(
        out,
        "deletionReceiptVerificationOutcome",
        row.deletionReceiptVerificationOutcome == null
            ? null
            : row.deletionReceiptVerificationOutcome.name());
    out.append(',');
    name(out, "attemptCount");
    out.append(row.attemptCount);
    out.append(',');
    member(out, "replayMarker", row.replayMarker.name());
    out.append(',');
    name(out, "effectCount");
    out.append(row.effectCount);
    out.append(',');
    name(out, "applyCount");
    out.append(row.applyCount);
    out.append(',');
    name(out, "remoteDeletionEffectCount");
    out.append(row.remoteDeletionEffectCount);
    out.append(',');
    member(out, "preLocalStateDigest", row.preLocalStateDigest);
    out.append(',');
    member(out, "postLocalStateDigest", row.postLocalStateDigest);
    out.append('}');
  }

  private static QueueRow queueFromJson(Map<String, Object> value) {
    requireKeys(
        value,
        "operationClass",
        "intentIdHash",
        "logicalKeyHash",
        "jobIdHash",
        "resultIdHash",
        "deletionScopeDigest",
        "deletionIdHash",
        "deletionReceiptIdHash",
        "queueState",
        "deletionSubstatus",
        "contentFreeDeletionErrorCode",
        "deletionReceiptVerificationOutcome",
        "attemptCount",
        "replayMarker",
        "effectCount",
        "applyCount",
        "remoteDeletionEffectCount",
        "preLocalStateDigest",
        "postLocalStateDigest");
    OperationClass operationClass =
        enumValue(OperationClass.class, value.get("operationClass"));
    boolean deletion = operationClass == OperationClass.DELETE_CLOUD_COPY;
    String deletionScope =
        deletion
            ? nullableStringFor(value.get("deletionScopeDigest"), Diagnostic.INVALID_011)
            : nullableString(value.get("deletionScopeDigest"));
    String deletionId =
        deletion
            ? nullableStringFor(value.get("deletionIdHash"), Diagnostic.INVALID_011)
            : nullableString(value.get("deletionIdHash"));
    String deletionReceiptId =
        deletion
            ? nullableStringFor(value.get("deletionReceiptIdHash"), Diagnostic.INVALID_011)
            : nullableString(value.get("deletionReceiptIdHash"));
    String preLocalStateDigest = string(value.get("preLocalStateDigest"));
    String postLocalStateDigest = string(value.get("postLocalStateDigest"));
    requireHashFor(preLocalStateDigest, Diagnostic.INVALID_010);
    requireHashFor(postLocalStateDigest, Diagnostic.INVALID_010);
    if (deletion) {
      requireHashFor(deletionScope, Diagnostic.INVALID_011);
      requireNullableHashFor(deletionId, Diagnostic.INVALID_011);
      requireNullableHashFor(deletionReceiptId, Diagnostic.INVALID_011);
    }
    QueueState queueState =
        deletion
            ? enumValueFor(QueueState.class, value.get("queueState"), Diagnostic.INVALID_011)
            : enumValue(QueueState.class, value.get("queueState"));
    DeletionSubstatus deletionSubstatus =
        deletion
            ? nullableEnumFor(
                DeletionSubstatus.class,
                value.get("deletionSubstatus"),
                Diagnostic.INVALID_011)
            : nullableEnum(DeletionSubstatus.class, value.get("deletionSubstatus"));
    DeletionError deletionError =
        deletion
            ? nullableEnumFor(
                DeletionError.class,
                value.get("contentFreeDeletionErrorCode"),
                Diagnostic.INVALID_011)
            : nullableEnum(DeletionError.class, value.get("contentFreeDeletionErrorCode"));
    ReceiptOutcome receipt =
        deletion
            ? nullableEnumFor(
                ReceiptOutcome.class,
                value.get("deletionReceiptVerificationOutcome"),
                Diagnostic.INVALID_011)
            : nullableEnum(
                ReceiptOutcome.class, value.get("deletionReceiptVerificationOutcome"));
    int effect =
        deletion
            ? exactBitFor(value.get("effectCount"), Diagnostic.INVALID_011)
            : exactBit(value.get("effectCount"));
    int apply =
        deletion
            ? exactBitFor(value.get("applyCount"), Diagnostic.INVALID_011)
            : exactBit(value.get("applyCount"));
    int deletionEffect =
        deletion
            ? exactBitFor(value.get("remoteDeletionEffectCount"), Diagnostic.INVALID_011)
            : exactBit(value.get("remoteDeletionEffectCount"));
    try {
      return new QueueRow(
        operationClass,
        string(value.get("intentIdHash")),
        string(value.get("logicalKeyHash")),
        nullableString(value.get("jobIdHash")),
        nullableString(value.get("resultIdHash")),
        deletionScope,
        deletionId,
        deletionReceiptId,
        queueState,
        deletionSubstatus,
        deletionError,
        receipt,
        nonNegativeLong(value.get("attemptCount")),
        enumValue(ReplayMarker.class, value.get("replayMarker")),
        effect,
        apply,
        deletionEffect,
        preLocalStateDigest,
        postLocalStateDigest);
    } catch (ContractFault fault) {
      if (deletion
          && (fault.diagnostic == Diagnostic.INVALID_011
              || fault.diagnostic == Diagnostic.INVALID_014
              || fault.diagnostic == Diagnostic.INVALID_015)) {
        throw fault(Diagnostic.INVALID_011);
      }
      throw fault;
    }
  }

  private static void appendReplayArray(StringBuilder out, List<ReplayRecord> rows) {
    out.append('[');
    for (int index = 0; index < rows.size(); index++) {
      if (index > 0) {
        out.append(',');
      }
      appendReplay(out, rows.get(index));
    }
    out.append(']');
  }

  private static void appendReplay(StringBuilder out, ReplayRecord row) {
    out.append('{');
    member(out, "logicalKeyHash", row.logicalKeyHash);
    out.append(',');
    member(out, "canonicalInputDigest", row.canonicalInputDigest);
    out.append(',');
    member(out, "selectedRuleId", row.selectedRuleId);
    out.append(',');
    member(out, "outcome", row.outcome.name());
    out.append(',');
    member(out, "postStateDigest", row.postStateDigest);
    out.append(',');
    nullableMember(out, "resultIdHash", row.resultIdHash);
    out.append(',');
    member(out, "replayMarker", row.replayMarker.name());
    out.append(',');
    name(out, "attemptCount");
    out.append(row.attemptCount);
    out.append(',');
    name(out, "effectCount");
    out.append(row.effectCount);
    out.append(',');
    name(out, "applyCount");
    out.append(row.applyCount);
    out.append(',');
    name(out, "remoteDeletionEffectCount");
    out.append(row.remoteDeletionEffectCount);
    out.append('}');
  }

  private static ReplayRecord replayFromJson(Map<String, Object> value) {
    requireKeys(
        value,
        "logicalKeyHash",
        "canonicalInputDigest",
        "selectedRuleId",
        "outcome",
        "postStateDigest",
        "resultIdHash",
        "replayMarker",
        "attemptCount",
        "effectCount",
        "applyCount",
        "remoteDeletionEffectCount");
    String postStateDigest = string(value.get("postStateDigest"));
    requireHashFor(postStateDigest, Diagnostic.INVALID_010);
    return new ReplayRecord(
        string(value.get("logicalKeyHash")),
        string(value.get("canonicalInputDigest")),
        string(value.get("selectedRuleId")),
        enumValue(ReducerOutcome.class, value.get("outcome")),
        postStateDigest,
        nullableString(value.get("resultIdHash")),
        enumValue(ReplayMarker.class, value.get("replayMarker")),
        nonNegativeLong(value.get("attemptCount")),
        exactBit(value.get("effectCount")),
        exactBit(value.get("applyCount")),
        exactBit(value.get("remoteDeletionEffectCount")));
  }

  private static void appendResult(StringBuilder out, Result result) {
    out.append('{');
    nullableMember(out, "selectedRuleId", result.selectedRuleId);
    out.append(',');
    member(out, "outcome", result.outcome.name());
    out.append(',');
    name(out, "postStateVector");
    appendState(out, result.postStateVector);
    out.append(',');
    name(out, "typedLedgerDeltas");
    out.append('{');
    name(out, "flowAppend");
    if (result.typedLedgerDeltas.flowAppend == null) {
      out.append("null");
    } else {
      appendFlow(out, result.typedLedgerDeltas.flowAppend);
    }
    out.append(',');
    name(out, "queueAppend");
    if (result.typedLedgerDeltas.queueAppend == null) {
      out.append("null");
    } else {
      appendQueue(out, result.typedLedgerDeltas.queueAppend);
    }
    out.append(',');
    member(out, "diagnosticCode", result.typedLedgerDeltas.diagnosticCode.code());
    out.append('}');
    out.append(',');
    name(out, "effectCount");
    out.append(result.effectCount);
    out.append(',');
    name(out, "applyCount");
    out.append(result.applyCount);
    out.append(',');
    name(out, "remoteDeletionEffectCount");
    out.append(result.remoteDeletionEffectCount);
    out.append('}');
  }

  private static Result resultFromJson(Map<String, Object> value) {
    requireKeys(
        value,
        "selectedRuleId",
        "outcome",
        "postStateVector",
        "typedLedgerDeltas",
        "effectCount",
        "applyCount",
        "remoteDeletionEffectCount");
    Map<String, Object> deltas = object(value.get("typedLedgerDeltas"));
    requireKeys(deltas, "flowAppend", "queueAppend", "diagnosticCode");
    FlowRow flow =
        deltas.get("flowAppend") == null ? null : flowFromJson(object(deltas.get("flowAppend")));
    QueueRow queue =
        deltas.get("queueAppend") == null
            ? null
            : queueFromJson(object(deltas.get("queueAppend")));
    return new Result(
        nullableString(value.get("selectedRuleId")),
        enumValue(ReducerOutcome.class, value.get("outcome")),
        stateFromJson(object(value.get("postStateVector"))),
        new TypedLedgerDeltas(
            flow, queue, Diagnostic.fromCode(string(deltas.get("diagnosticCode")))),
        exactBit(value.get("effectCount")),
        exactBit(value.get("applyCount")),
        exactBit(value.get("remoteDeletionEffectCount")));
  }

  private static void name(StringBuilder out, String name) {
    quoted(out, name);
    out.append(':');
  }

  private static void nullableMember(StringBuilder out, String name, String value) {
    name(out, name);
    if (value == null) {
      out.append("null");
    } else {
      quoted(out, value);
    }
  }

  private static Map<String, Object> object(Object value) {
    if (!(value instanceof Map<?, ?> raw)) {
      throw fault(Diagnostic.INVALID_014);
    }
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw fault(Diagnostic.INVALID_014);
      }
      result.put(key, entry.getValue());
    }
    return result;
  }

  private static List<Object> array(Object value) {
    if (!(value instanceof List<?> list)) {
      throw fault(Diagnostic.INVALID_014);
    }
    return Collections.unmodifiableList(new ArrayList<>(list));
  }

  private static String string(Object value) {
    if (!(value instanceof String text)) {
      throw fault(Diagnostic.INVALID_014);
    }
    return text;
  }

  private static String nullableString(Object value) {
    return value == null ? null : string(value);
  }

  private static String nullableStringFor(Object value, Diagnostic diagnostic) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof String text)) {
      throw fault(diagnostic);
    }
    return text;
  }

  private static long nonNegativeLong(Object value) {
    if (!(value instanceof Long number) || number < 0) {
      throw fault(Diagnostic.INVALID_014);
    }
    return number;
  }

  private static long positiveLong(Object value) {
    long result = nonNegativeLong(value);
    if (result == 0) {
      throw fault(Diagnostic.INVALID_014);
    }
    return result;
  }

  private static int exactInt(Object value) {
    long number = nonNegativeLong(value);
    if (number > Integer.MAX_VALUE) {
      throw fault(Diagnostic.INVALID_014);
    }
    return (int) number;
  }

  private static int exactBit(Object value) {
    int number = exactInt(value);
    if (number != 0 && number != 1) {
      throw fault(Diagnostic.INVALID_014);
    }
    return number;
  }

  private static int exactBitFor(Object value, Diagnostic diagnostic) {
    if (!(value instanceof Long number) || (number != 0 && number != 1)) {
      throw fault(diagnostic);
    }
    return number.intValue();
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, Object value) {
    try {
      return Enum.valueOf(type, string(value));
    } catch (IllegalArgumentException exception) {
      throw fault(Diagnostic.INVALID_014);
    }
  }

  private static <E extends Enum<E>> E enumValueFor(
      Class<E> type, Object value, Diagnostic diagnostic) {
    if (!(value instanceof String text)) {
      throw fault(diagnostic);
    }
    try {
      return Enum.valueOf(type, text);
    } catch (IllegalArgumentException exception) {
      throw fault(diagnostic);
    }
  }

  private static <E extends Enum<E>> E nullableEnum(Class<E> type, Object value) {
    return value == null ? null : enumValue(type, value);
  }

  private static <E extends Enum<E>> E nullableEnumFor(
      Class<E> type, Object value, Diagnostic diagnostic) {
    return value == null ? null : enumValueFor(type, value, diagnostic);
  }

  private static void requireKeys(Map<String, Object> value, String... keys) {
    if (!new ArrayList<>(value.keySet()).equals(Arrays.asList(keys))) {
      throw fault(Diagnostic.INVALID_013);
    }
  }

  private static String canonicalParsedJson(Object value) {
    StringBuilder out = new StringBuilder();
    appendCanonicalParsedJson(out, value);
    return out.toString();
  }

  private static void appendCanonicalParsedJson(StringBuilder out, Object value) {
    if (value == null) {
      out.append("null");
    } else if (value instanceof String text) {
      quoted(out, text);
    } else if (value instanceof Boolean bool) {
      out.append(bool);
    } else if (value instanceof Long || value instanceof BigInteger) {
      out.append(value);
    } else if (value instanceof List<?> list) {
      out.append('[');
      for (int index = 0; index < list.size(); index++) {
        if (index > 0) {
          out.append(',');
        }
        appendCanonicalParsedJson(out, list.get(index));
      }
      out.append(']');
    } else if (value instanceof Map<?, ?> map) {
      out.append('{');
      int index = 0;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!(entry.getKey() instanceof String key)) {
          throw fault(Diagnostic.INVALID_014);
        }
        if (index++ > 0) {
          out.append(',');
        }
        quoted(out, key);
        out.append(':');
        appendCanonicalParsedJson(out, entry.getValue());
      }
      out.append('}');
    } else {
      throw fault(Diagnostic.INVALID_014);
    }
  }

  private static void preflightSnapshotStructure(Object value) {
    if (!(value instanceof Map<?, ?> root)) {
      return;
    }
    requireRawKeys(
        root,
        "snapshotSchema",
        "contractId",
        "scenarioId",
        "nextActionOrdinal",
        "monotonicOffsetMs",
        "remainingAutomaticAttemptBudget",
        "manualRetryGrantCount",
        "stateVector",
        "flowLedger",
        "queueLedger",
        "replayRecords",
        "lastResult");
    preflightStateStructure(root.get("stateVector"));
    preflightFlowArrayStructure(root.get("flowLedger"));
    preflightQueueArrayStructure(root.get("queueLedger"));
    preflightReplayArrayStructure(root.get("replayRecords"));
    preflightResultStructure(root.get("lastResult"));
  }

  private static void preflightStateStructure(Object value) {
    if (value instanceof Map<?, ?> state) {
      requireRawKeys(state, "local", "processingCapability", "connectivity", "model", "queue");
    }
  }

  private static void preflightFlowArrayStructure(Object value) {
    if (!(value instanceof List<?> rows)) {
      return;
    }
    for (Object candidate : rows) {
      preflightFlowStructure(candidate);
    }
  }

  private static void preflightFlowStructure(Object value) {
    if (!(value instanceof Map<?, ?> row)) {
      return;
    }
    requireRawKeys(
        row,
        "sequence",
        "scenarioId",
        "actionId",
        "preStateVector",
        "postStateVector",
        "outcome",
        "monotonicOffsetMs",
        "preStateDigest",
        "postStateDigest",
        "processingRequestIdHash",
        "queueIntentIdHash",
        "contentFreeErrorCode");
    preflightStateStructure(row.get("preStateVector"));
    preflightStateStructure(row.get("postStateVector"));
  }

  private static void preflightQueueArrayStructure(Object value) {
    if (!(value instanceof List<?> rows)) {
      return;
    }
    for (Object candidate : rows) {
      preflightQueueStructure(candidate);
    }
  }

  private static void preflightQueueStructure(Object value) {
    if (!(value instanceof Map<?, ?> row)) {
      return;
    }
    requireRawKeys(
        row,
        "operationClass",
        "intentIdHash",
        "logicalKeyHash",
        "jobIdHash",
        "resultIdHash",
        "deletionScopeDigest",
        "deletionIdHash",
        "deletionReceiptIdHash",
        "queueState",
        "deletionSubstatus",
        "contentFreeDeletionErrorCode",
        "deletionReceiptVerificationOutcome",
        "attemptCount",
        "replayMarker",
        "effectCount",
        "applyCount",
        "remoteDeletionEffectCount",
        "preLocalStateDigest",
        "postLocalStateDigest");
  }

  private static void preflightReplayArrayStructure(Object value) {
    if (!(value instanceof List<?> rows)) {
      return;
    }
    for (Object candidate : rows) {
      if (candidate instanceof Map<?, ?> row) {
        requireRawKeys(
            row,
            "logicalKeyHash",
            "canonicalInputDigest",
            "selectedRuleId",
            "outcome",
            "postStateDigest",
            "resultIdHash",
            "replayMarker",
            "attemptCount",
            "effectCount",
            "applyCount",
            "remoteDeletionEffectCount");
      }
    }
  }

  private static void preflightResultStructure(Object value) {
    if (!(value instanceof Map<?, ?> result)) {
      return;
    }
    requireRawKeys(
        result,
        "selectedRuleId",
        "outcome",
        "postStateVector",
        "typedLedgerDeltas",
        "effectCount",
        "applyCount",
        "remoteDeletionEffectCount");
    preflightStateStructure(result.get("postStateVector"));
    Object deltasValue = result.get("typedLedgerDeltas");
    if (deltasValue instanceof Map<?, ?> deltas) {
      requireRawKeys(deltas, "flowAppend", "queueAppend", "diagnosticCode");
      preflightFlowStructure(deltas.get("flowAppend"));
      preflightQueueStructure(deltas.get("queueAppend"));
    }
  }

  private static void requireRawKeys(Map<?, ?> value, String... keys) {
    if (!new ArrayList<>(value.keySet()).equals(Arrays.asList(keys))) {
      throw fault(Diagnostic.INVALID_013);
    }
  }

  private static void preflightSnapshotEarlierCategories(Object value) {
    if (!(value instanceof Map<?, ?> root)) {
      return;
    }
    Object schema = root.get("snapshotSchema");
    Object contract = root.get("contractId");
    if (schema == null
        || contract == null
        || (schema instanceof String schemaText && !SNAPSHOT_SCHEMA.equals(schemaText))
        || (contract instanceof String contractText && !CONTRACT_ID.equals(contractText))) {
      throw fault(Diagnostic.INVALID_002);
    }
    preflightScenarioId(root.get("scenarioId"));
    preflightFlowArrayCatalogs(root.get("flowLedger"));
    preflightReplayArrayCatalogs(root.get("replayRecords"));
    preflightResultCatalogs(root.get("lastResult"));
    preflightRawFlowContextCategory003(root);
    preflightBudgetCategory(root);
  }

  private static void preflightScenarioId(Object value) {
    if (value == null
        || (value instanceof String text
            && !text.matches("OFF-SYN-(00[1-9]|01[0-9]|02[0-6])"))) {
      throw fault(Diagnostic.INVALID_003);
    }
  }

  private static void preflightFlowArrayCatalogs(Object value) {
    if (!(value instanceof List<?> rows)) {
      return;
    }
    for (Object candidate : rows) {
      if (candidate instanceof Map<?, ?> row) {
        preflightScenarioId(row.get("scenarioId"));
        preflightActionId(row.get("actionId"));
        preflightDiagnosticCode(row.get("contentFreeErrorCode"), true);
      }
    }
  }

  private static void preflightReplayArrayCatalogs(Object value) {
    if (!(value instanceof List<?> rows)) {
      return;
    }
    for (Object candidate : rows) {
      if (candidate instanceof Map<?, ?> row) {
        preflightRuleId(row.get("selectedRuleId"), false);
      }
    }
  }

  private static void preflightResultCatalogs(Object value) {
    if (!(value instanceof Map<?, ?> result)) {
      return;
    }
    preflightRuleId(result.get("selectedRuleId"), true);
    Object deltasValue = result.get("typedLedgerDeltas");
    if (deltasValue instanceof Map<?, ?> deltas) {
      preflightDiagnosticCode(deltas.get("diagnosticCode"), false);
      Object flow = deltas.get("flowAppend");
      if (flow instanceof Map<?, ?> flowRow) {
        preflightScenarioId(flowRow.get("scenarioId"));
        preflightActionId(flowRow.get("actionId"));
        preflightDiagnosticCode(flowRow.get("contentFreeErrorCode"), true);
      }
    }
  }

  private static void preflightRawFlowContextCategory003(Map<?, ?> root) {
    if (!(root.get("scenarioId") instanceof String scenarioId)
        || !(root.get("flowLedger") instanceof List<?> flow)) {
      return;
    }
    int expectedSequence = 1;
    for (Object candidate : flow) {
      if (candidate instanceof Map<?, ?> row) {
        if (expectedSequence > scenarioActionCount(scenarioId)) {
          throw fault(Diagnostic.INVALID_003);
        }
        preflightRawFlowRowContextCategory003(scenarioId, row, expectedSequence);
        if (row.get("sequence") instanceof Long sequence && sequence != expectedSequence) {
          throw fault(Diagnostic.INVALID_003);
        }
      }
      expectedSequence++;
    }
    Object lastResult = root.get("lastResult");
    if (lastResult instanceof Map<?, ?> result
        && result.get("typedLedgerDeltas") instanceof Map<?, ?> deltas
        && deltas.get("flowAppend") instanceof Map<?, ?> nested) {
      int expectedNestedSequence = flow.size();
      if (expectedNestedSequence <= 0 || expectedNestedSequence > scenarioActionCount(scenarioId)) {
        throw fault(Diagnostic.INVALID_003);
      }
      preflightRawFlowRowContextCategory003(scenarioId, nested, expectedNestedSequence);
      if (nested.get("sequence") instanceof Long sequence && sequence != expectedNestedSequence) {
        throw fault(Diagnostic.INVALID_003);
      }
    }
  }

  private static void preflightRawFlowRowContextCategory003(
      String scenarioId, Map<?, ?> row, int sequence) {
    if (row.get("scenarioId") instanceof String rowScenario && !scenarioId.equals(rowScenario)) {
      throw fault(Diagnostic.INVALID_003);
    }
    if (row.get("actionId") instanceof String actionId
        && !action(scenarioId, sequence).actionId.equals(actionId)) {
      throw fault(Diagnostic.INVALID_003);
    }
  }

  private static void preflightActionId(Object value) {
    if (value == null
        || (value instanceof String text && !exactActionOrEventId(text, "OFF-I1-ACT-"))) {
      throw fault(Diagnostic.INVALID_003);
    }
  }

  private static void preflightRuleId(Object value, boolean nullable) {
    if (value == null && nullable) {
      return;
    }
    if (value == null) {
      throw fault(Diagnostic.INVALID_003);
    }
    if (value instanceof String text && !exactRuleId(text)) {
      throw fault(Diagnostic.INVALID_003);
    }
  }

  private static void preflightDiagnosticCode(Object value, boolean nullable) {
    if (value == null && nullable) {
      return;
    }
    if (value == null) {
      throw fault(Diagnostic.INVALID_003);
    }
    if (value instanceof String text) {
      Diagnostic.fromCode(text);
    }
  }

  private static void preflightBudgetCategory(Map<?, ?> root) {
    Object remainingValue = root.get("remainingAutomaticAttemptBudget");
    Object grantsValue = root.get("manualRetryGrantCount");
    Object queueValue = root.get("queueLedger");
    if (!(remainingValue instanceof Long remaining)
        || !(grantsValue instanceof Long grants)
        || !(queueValue instanceof List<?> queue)) {
      return;
    }
    long attempt = 0;
    if (!queue.isEmpty()) {
      Object tail = queue.get(queue.size() - 1);
      if (!(tail instanceof Map<?, ?> row) || !(row.get("attemptCount") instanceof Long count)) {
        return;
      }
      attempt = count;
    }
    if (remaining < 0 || grants < 0 || attempt < 0) {
      return;
    }
    BigInteger available =
        BigInteger.valueOf(INITIAL_AUTOMATIC_ATTEMPT_BUDGET)
            .add(BigInteger.valueOf(grants));
    if (available.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
      return;
    }
    BigInteger expected = available.subtract(BigInteger.valueOf(attempt));
    if (expected.signum() < 0 || !expected.equals(BigInteger.valueOf(remaining))) {
      throw fault(Diagnostic.INVALID_008);
    }
  }

  private static void preflightSnapshotStateCategories(Object value) {
    if (!(value instanceof Map<?, ?> root)) {
      return;
    }
    preflightStateCategory(root.get("stateVector"));
    preflightFlowArrayCategories(root.get("flowLedger"));
    preflightQueueArrayStateCategories(root.get("queueLedger"));
    preflightReplayArrayCategories(root.get("replayRecords"));
    preflightResultStateCategories(root.get("lastResult"));
  }

  private static void preflightSnapshotDeletionCategories(Object value) {
    if (!(value instanceof Map<?, ?> root)) {
      return;
    }
    preflightQueueArrayDeletionCategories(root.get("queueLedger"));
    preflightResultDeletionCategories(root.get("lastResult"));
  }

  private static void preflightSnapshotExactStateDigestRelations(Object value) {
    if (!(value instanceof Map<?, ?> root) || !(root.get("scenarioId") instanceof String scenarioId)) {
      return;
    }
    try {
      StateVector observedState = stateFromJson(object(root.get("stateVector")));
      List<?> flow = array(root.get("flowLedger"));
      List<?> queue = array(root.get("queueLedger"));
      List<?> replay = array(root.get("replayRecords"));
      int seedCount = seededQueueRowCount(scenarioId);
      Integer scriptedDeletionOrdinal = rawDeletionOrdinal(root.get("lastResult"), flow.size());
      StateVector initial =
          rawInitialStateForDigestRelations(
              scenarioId, observedState, scriptedDeletionOrdinal, queue.isEmpty());
      if (initial == null) {
        return;
      }
      preflightRawResultStateDigestRelations(root.get("lastResult"));
      String initialLocalDigest = localStateDigest(initial.local);
      Map<String, String> expectedReplayDigests = new LinkedHashMap<>();
      if (seedCount > 0) {
        if (queue.size() < seedCount || !(queue.get(seedCount - 1) instanceof Map<?, ?> seed)) {
          return;
        }
        String preLocal = rawRequiredString(seed.get("preLocalStateDigest"));
        String postLocal = rawRequiredString(seed.get("postLocalStateDigest"));
        if (preLocal == null || postLocal == null) {
          return;
        }
        if (!preLocal.equals(initialLocalDigest) || !postLocal.equals(initialLocalDigest)) {
          throw fault(Diagnostic.INVALID_010);
        }
        String logicalKey = rawRequiredString(seed.get("logicalKeyHash"));
        if (logicalKey == null) {
          return;
        }
        expectedReplayDigests.put(logicalKey, stateVectorDigest(initial));
      }
      int queueIndex = seedCount;
      StateVector priorPost = initial;
      for (Object candidate : flow) {
        if (!(candidate instanceof Map<?, ?> row)
            || !(row.get("sequence") instanceof Long sequence)
            || sequence <= 0
            || sequence > Integer.MAX_VALUE) {
          return;
        }
        StateVector pre = stateFromJson(object(row.get("preStateVector")));
        StateVector post = stateFromJson(object(row.get("postStateVector")));
        String preDigest = rawRequiredString(row.get("preStateDigest"));
        String postDigest = rawRequiredString(row.get("postStateDigest"));
        if (preDigest == null || postDigest == null) {
          return;
        }
        if (!preDigest.equals(stateVectorDigest(pre)) || !postDigest.equals(stateVectorDigest(post))) {
          throw fault(Diagnostic.INVALID_010);
        }
        if (!pre.equals(priorPost)) {
          throw fault(Diagnostic.INVALID_010);
        }
        priorPost = post;
        ActionSpec action = action(scenarioId, sequence.intValue());
        if (!action.queueAffecting) {
          continue;
        }
        if (queueIndex >= queue.size() || !(queue.get(queueIndex++) instanceof Map<?, ?> paired)) {
          return;
        }
        String preLocal = rawRequiredString(paired.get("preLocalStateDigest"));
        String postLocal = rawRequiredString(paired.get("postLocalStateDigest"));
        String logicalKey = rawRequiredString(paired.get("logicalKeyHash"));
        if (preLocal == null || postLocal == null || logicalKey == null) {
          return;
        }
        if (!preLocal.equals(localStateDigest(pre.local))
            || !postLocal.equals(localStateDigest(post.local))) {
          throw fault(Diagnostic.INVALID_010);
        }
        if (!ReducerOutcome.REJECTED_NO_STATE_CHANGE.name().equals(row.get("outcome"))) {
          expectedReplayDigests.put(logicalKey, postDigest);
        }
      }
      for (Object candidate : replay) {
        if (!(candidate instanceof Map<?, ?> record)) {
          return;
        }
        String logicalKey = rawRequiredString(record.get("logicalKeyHash"));
        String postDigest = rawRequiredString(record.get("postStateDigest"));
        if (logicalKey == null || postDigest == null) {
          return;
        }
        String expected = expectedReplayDigests.get(logicalKey);
        if (expected != null && !postDigest.equals(expected)) {
          throw fault(Diagnostic.INVALID_010);
        }
      }
      if (!observedState.equals(priorPost)) {
        throw fault(Diagnostic.INVALID_010);
      }
    } catch (ContractFault failure) {
      if (failure.diagnostic == Diagnostic.INVALID_010) {
        throw failure;
      }
    }
  }

  private static void preflightRawIndependentStateDigestCategory010(Object value) {
    if (!(value instanceof Map<?, ?> root)) {
      return;
    }
    if (root.get("flowLedger") instanceof List<?> flow) {
      for (Object candidate : flow) {
        if (candidate instanceof Map<?, ?> row) {
          preflightRawFlowRowStateDigestCategory010(row);
        }
      }
    }
    preflightRawResultStateDigestRelations(root.get("lastResult"));
  }

  private static void preflightRawFlowRowStateDigestCategory010(Map<?, ?> row) {
    try {
      StateVector pre = stateFromJson(object(row.get("preStateVector")));
      StateVector post = stateFromJson(object(row.get("postStateVector")));
      String preDigest = rawRequiredString(row.get("preStateDigest"));
      String postDigest = rawRequiredString(row.get("postStateDigest"));
      if (preDigest == null || postDigest == null) {
        return;
      }
      if (!preDigest.equals(stateVectorDigest(pre)) || !postDigest.equals(stateVectorDigest(post))) {
        throw fault(Diagnostic.INVALID_010);
      }
    } catch (ContractFault failure) {
      if (failure.diagnostic == Diagnostic.INVALID_010) {
        throw failure;
      }
    }
  }

  private static void preflightRawResultStateDigestRelations(Object value) {
    if (!(value instanceof Map<?, ?> result)
        || !(result.get("typedLedgerDeltas") instanceof Map<?, ?> deltas)
        || !(deltas.get("flowAppend") instanceof Map<?, ?> flow)) {
      return;
    }
    try {
      StateVector pre = stateFromJson(object(flow.get("preStateVector")));
      StateVector post = stateFromJson(object(flow.get("postStateVector")));
      String preDigest = rawRequiredString(flow.get("preStateDigest"));
      String postDigest = rawRequiredString(flow.get("postStateDigest"));
      if (preDigest == null || postDigest == null) {
        return;
      }
      if (!preDigest.equals(stateVectorDigest(pre)) || !postDigest.equals(stateVectorDigest(post))) {
        throw fault(Diagnostic.INVALID_010);
      }
      if (deltas.get("queueAppend") instanceof Map<?, ?> queue) {
        String preLocal = rawRequiredString(queue.get("preLocalStateDigest"));
        String postLocal = rawRequiredString(queue.get("postLocalStateDigest"));
        if (preLocal == null || postLocal == null) {
          return;
        }
        if (!preLocal.equals(localStateDigest(pre.local))
            || !postLocal.equals(localStateDigest(post.local))) {
          throw fault(Diagnostic.INVALID_010);
        }
      }
    } catch (ContractFault failure) {
      if (failure.diagnostic == Diagnostic.INVALID_010) {
        throw failure;
      }
    }
  }

  private static Integer rawDeletionOrdinal(Object lastResult, int completedActions) {
    if (!(lastResult instanceof Map<?, ?> result)
        || !(result.get("selectedRuleId") instanceof String ruleId)) {
      return null;
    }
    try {
      return deletionOrdinalFromRule(ruleId, completedActions);
    } catch (ContractFault failure) {
      return null;
    }
  }

  private static StateVector rawInitialStateForDigestRelations(
      String scenarioId,
      StateVector observedState,
      Integer scriptedDeletionOrdinal,
      boolean queueEmpty) {
    if (!"OFF-SYN-026".equals(scenarioId)) {
      return initialStateForGraph(scenarioId, observedState, List.of(), null);
    }
    if (scriptedDeletionOrdinal != null) {
      return deletionInitialState(scriptedDeletionOrdinal);
    }
    if (queueEmpty
        || (observedState.connectivity != ConnectivityState.NETWORK_DENIED
            && observedState.connectivity != ConnectivityState.AVAILABLE)) {
      return null;
    }
    return new StateVector(
        LocalState.LOCAL_OPERATION_SUCCEEDED,
        ProcessingState.PROCESSING_NOT_REQUESTED,
        observedState.connectivity,
        ModelState.MODEL_NOT_INSTALLED,
        QueueState.DELETE_PENDING);
  }

  private static String rawRequiredString(Object value) {
    return value instanceof String text ? text : null;
  }

  private static void preflightStateCategory(Object value) {
    if (!(value instanceof Map<?, ?> state)) {
      return;
    }
    requireRawEnum(state.get("local"), LocalState.class, Diagnostic.INVALID_010);
    requireRawEnum(
        state.get("processingCapability"), ProcessingState.class, Diagnostic.INVALID_010);
    requireRawEnum(
        state.get("connectivity"), ConnectivityState.class, Diagnostic.INVALID_010);
    requireRawEnum(state.get("model"), ModelState.class, Diagnostic.INVALID_010);
    requireRawEnum(state.get("queue"), QueueState.class, Diagnostic.INVALID_010);
  }

  private static void preflightFlowArrayCategories(Object value) {
    if (!(value instanceof List<?> rows)) {
      return;
    }
    for (Object candidate : rows) {
      if (candidate instanceof Map<?, ?> row) {
        preflightStateCategory(row.get("preStateVector"));
        preflightStateCategory(row.get("postStateVector"));
        requireRawHash(row.get("preStateDigest"), Diagnostic.INVALID_010, false);
        requireRawHash(row.get("postStateDigest"), Diagnostic.INVALID_010, false);
      }
    }
  }

  private static void preflightQueueArrayStateCategories(Object value) {
    if (!(value instanceof List<?> rows)) {
      return;
    }
    for (Object candidate : rows) {
      if (candidate instanceof Map<?, ?> row) {
        requireRawHash(row.get("preLocalStateDigest"), Diagnostic.INVALID_010, false);
        requireRawHash(row.get("postLocalStateDigest"), Diagnostic.INVALID_010, false);
      }
    }
  }

  private static void preflightReplayArrayCategories(Object value) {
    if (!(value instanceof List<?> rows)) {
      return;
    }
    for (Object candidate : rows) {
      if (candidate instanceof Map<?, ?> row) {
        requireRawHash(row.get("canonicalInputDigest"), Diagnostic.INVALID_010, false);
        requireRawHash(row.get("postStateDigest"), Diagnostic.INVALID_010, false);
      }
    }
  }

  private static void preflightResultStateCategories(Object value) {
    if (!(value instanceof Map<?, ?> result)) {
      return;
    }
    preflightStateCategory(result.get("postStateVector"));
    Object deltasValue = result.get("typedLedgerDeltas");
    if (deltasValue instanceof Map<?, ?> deltas) {
      Object flow = deltas.get("flowAppend");
      if (flow instanceof Map<?, ?> flowRow) {
        preflightStateCategory(flowRow.get("preStateVector"));
        preflightStateCategory(flowRow.get("postStateVector"));
        requireRawHash(flowRow.get("preStateDigest"), Diagnostic.INVALID_010, false);
        requireRawHash(flowRow.get("postStateDigest"), Diagnostic.INVALID_010, false);
      }
      Object queue = deltas.get("queueAppend");
      if (queue instanceof Map<?, ?> queueRow) {
        requireRawHash(queueRow.get("preLocalStateDigest"), Diagnostic.INVALID_010, false);
        requireRawHash(queueRow.get("postLocalStateDigest"), Diagnostic.INVALID_010, false);
      }
    }
  }

  private static void preflightQueueArrayDeletionCategories(Object value) {
    if (!(value instanceof List<?> rows)) {
      return;
    }
    for (Object candidate : rows) {
      if (candidate instanceof Map<?, ?> row) {
        preflightDeletionCategory(row);
      }
    }
  }

  private static void preflightResultDeletionCategories(Object value) {
    if (!(value instanceof Map<?, ?> result)) {
      return;
    }
    Object deltasValue = result.get("typedLedgerDeltas");
    if (deltasValue instanceof Map<?, ?> deltas
        && deltas.get("queueAppend") instanceof Map<?, ?> queueRow) {
      preflightDeletionCategory(queueRow);
    }
  }

  private static void preflightDeletionCategory(Map<?, ?> row) {
    Object operationValue = row.get("operationClass");
    boolean deletion = OperationClass.DELETE_CLOUD_COPY.name().equals(operationValue);
    boolean deletionProjection =
        deletion
            || row.get("deletionScopeDigest") != null
            || row.get("deletionIdHash") != null
            || row.get("deletionReceiptIdHash") != null
            || row.get("deletionSubstatus") != null
            || row.get("contentFreeDeletionErrorCode") != null
            || row.get("deletionReceiptVerificationOutcome") != null
            || rawNonZeroInteger(row.get("remoteDeletionEffectCount"))
            || QueueState.DELETE_PENDING.name().equals(row.get("queueState"))
            || QueueState.DELETED_REMOTE.name().equals(row.get("queueState"));
    if (!deletionProjection) {
      return;
    }
    if (!deletion) {
      throw fault(Diagnostic.INVALID_011);
    }
    requireRawHash(row.get("deletionScopeDigest"), Diagnostic.INVALID_011, false);
    requireRawHash(row.get("deletionIdHash"), Diagnostic.INVALID_011, true);
    requireRawHash(row.get("deletionReceiptIdHash"), Diagnostic.INVALID_011, true);
    QueueState queueState =
        requireRawEnum(row.get("queueState"), QueueState.class, Diagnostic.INVALID_011);
    DeletionSubstatus substatus =
        requireRawNullableEnum(
            row.get("deletionSubstatus"), DeletionSubstatus.class, Diagnostic.INVALID_011);
    DeletionError error =
        requireRawNullableEnum(
            row.get("contentFreeDeletionErrorCode"), DeletionError.class, Diagnostic.INVALID_011);
    ReceiptOutcome receipt =
        requireRawNullableEnum(
            row.get("deletionReceiptVerificationOutcome"),
            ReceiptOutcome.class,
            Diagnostic.INVALID_011);
    int effect = requireRawBit(row.get("effectCount"), Diagnostic.INVALID_011);
    int apply = requireRawBit(row.get("applyCount"), Diagnostic.INVALID_011);
    int deletionEffect =
        requireRawBit(row.get("remoteDeletionEffectCount"), Diagnostic.INVALID_011);
    if (row.get("jobIdHash") != null
        || row.get("resultIdHash") != null
        || (queueState != QueueState.DELETE_PENDING && queueState != QueueState.DELETED_REMOTE)
        || apply != 0
        || effect != deletionEffect) {
      throw fault(Diagnostic.INVALID_011);
    }
    if (queueState == QueueState.DELETE_PENDING) {
      if (substatus == null || row.get("deletionReceiptIdHash") != null || receipt != null) {
        throw fault(Diagnostic.INVALID_011);
      }
    } else if (substatus != null
        || error != null
        || row.get("deletionIdHash") == null
        || row.get("deletionReceiptIdHash") == null
        || receipt != ReceiptOutcome.VERIFIED
        || deletionEffect != 1) {
      throw fault(Diagnostic.INVALID_011);
    }
  }

  private static void requireRawHash(Object value, Diagnostic diagnostic, boolean nullable) {
    if (value == null && nullable) {
      return;
    }
    if (!(value instanceof String text) || !HASH_64.matcher(text).matches()) {
      throw fault(diagnostic);
    }
  }

  private static boolean rawNonZeroInteger(Object value) {
    return (value instanceof Long number && number != 0)
        || (value instanceof BigInteger bigNumber && bigNumber.signum() != 0);
  }

  private static int requireRawBit(Object value, Diagnostic diagnostic) {
    if (!(value instanceof Long number) || (number != 0 && number != 1)) {
      throw fault(diagnostic);
    }
    return number.intValue();
  }

  private static <E extends Enum<E>> E requireRawEnum(
      Object value, Class<E> type, Diagnostic diagnostic) {
    if (!(value instanceof String text)) {
      throw fault(diagnostic);
    }
    try {
      return Enum.valueOf(type, text);
    } catch (IllegalArgumentException exception) {
      throw fault(diagnostic);
    }
  }

  private static <E extends Enum<E>> E requireRawNullableEnum(
      Object value, Class<E> type, Diagnostic diagnostic) {
    return value == null ? null : requireRawEnum(value, type, diagnostic);
  }

  private static final class JsonParser {
    private final String input;
    private int index;
    private int depth;

    JsonParser(String input) {
      this.input = requirePresent(input);
    }

    Object parseDocument() {
      Object value = parseValue();
      if (index != input.length()) {
        throw fault(Diagnostic.INVALID_013);
      }
      return value;
    }

    private Object parseValue() {
      if (index >= input.length()) {
        throw fault(Diagnostic.INVALID_013);
      }
      char value = input.charAt(index);
      return switch (value) {
        case '{' -> parseObject();
        case '[' -> parseArray();
        case '"' -> parseString();
        case 'n' -> parseNull();
        case 't', 'f' -> parseBoolean();
        default -> {
          if ((value >= '0' && value <= '9') || value == '-') {
            yield parseNumber();
          }
          throw fault(Diagnostic.INVALID_013);
        }
      };
    }

    private Map<String, Object> parseObject() {
      enterContainer();
      try {
        expect('{');
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (peek('}')) {
          expect('}');
          return result;
        }
        while (true) {
          if (!peek('"')) {
            throw fault(Diagnostic.INVALID_013);
          }
          String key = parseString();
          if (result.containsKey(key)) {
            throw fault(Diagnostic.INVALID_013);
          }
          expect(':');
          result.put(key, parseValue());
          if (peek('}')) {
            expect('}');
            return result;
          }
          expect(',');
        }
      } finally {
        exitContainer();
      }
    }

    private List<Object> parseArray() {
      enterContainer();
      try {
        expect('[');
        List<Object> result = new ArrayList<>();
        if (peek(']')) {
          expect(']');
          return result;
        }
        while (true) {
          result.add(parseValue());
          if (peek(']')) {
            expect(']');
            return result;
          }
          expect(',');
        }
      } finally {
        exitContainer();
      }
    }

    private String parseString() {
      expect('"');
      StringBuilder out = new StringBuilder();
      while (index < input.length()) {
        char value = input.charAt(index++);
        if (value == '"') {
          return out.toString();
        }
        if (value < 0x20) {
          throw fault(Diagnostic.INVALID_013);
        }
        if (value != '\\') {
          if (Character.isSurrogate(value)) {
            throw fault(Diagnostic.INVALID_013);
          }
          out.append(value);
          continue;
        }
        if (index >= input.length()) {
          throw fault(Diagnostic.INVALID_013);
        }
        char escape = input.charAt(index++);
        switch (escape) {
          case '"', '\\', '/' -> out.append(escape);
          case 'b' -> out.append('\b');
          case 'f' -> out.append('\f');
          case 'n' -> out.append('\n');
          case 'r' -> out.append('\r');
          case 't' -> out.append('\t');
          case 'u' -> {
            int code = hex4();
            if (Character.isSurrogate((char) code)) {
              throw fault(Diagnostic.INVALID_013);
            }
            out.append((char) code);
          }
          default -> throw fault(Diagnostic.INVALID_013);
        }
      }
      throw fault(Diagnostic.INVALID_013);
    }

    private int hex4() {
      if (index + 4 > input.length()) {
        throw fault(Diagnostic.INVALID_013);
      }
      int value = 0;
      for (int count = 0; count < 4; count++) {
        int digit = Character.digit(input.charAt(index++), 16);
        if (digit < 0) {
          throw fault(Diagnostic.INVALID_013);
        }
        value = value * 16 + digit;
      }
      return value;
    }

    private Object parseNull() {
      if (index + 4 > input.length() || !input.startsWith("null", index)) {
        throw fault(Diagnostic.INVALID_013);
      }
      index += 4;
      return null;
    }

    private Boolean parseBoolean() {
      if (input.startsWith("true", index)) {
        index += 4;
        return Boolean.TRUE;
      }
      if (input.startsWith("false", index)) {
        index += 5;
        return Boolean.FALSE;
      }
      throw fault(Diagnostic.INVALID_013);
    }

    private Number parseNumber() {
      int start = index;
      boolean negative = false;
      if (input.charAt(index) == '-') {
        negative = true;
        index++;
        if (index >= input.length() || !Character.isDigit(input.charAt(index))) {
          throw fault(Diagnostic.INVALID_013);
        }
      }
      if (input.charAt(index) == '0') {
        index++;
        if (negative) {
          throw fault(Diagnostic.INVALID_013);
        }
        if (index < input.length() && Character.isDigit(input.charAt(index))) {
          throw fault(Diagnostic.INVALID_013);
        }
      } else {
        while (index < input.length() && Character.isDigit(input.charAt(index))) {
          index++;
        }
      }
      try {
        return Long.parseLong(input.substring(start, index));
      } catch (NumberFormatException exception) {
        return new BigInteger(input.substring(start, index));
      }
    }

    private void expect(char expected) {
      if (index >= input.length() || input.charAt(index) != expected) {
        throw fault(Diagnostic.INVALID_013);
      }
      index++;
    }

    private boolean peek(char expected) {
      return index < input.length() && input.charAt(index) == expected;
    }

    private void enterContainer() {
      if (depth >= MAX_JSON_DEPTH) {
        throw fault(Diagnostic.INVALID_016);
      }
      depth++;
    }

    private void exitContainer() {
      depth--;
    }
  }

  private static long checkedAdd(long left, long right) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException exception) {
      throw fault(Diagnostic.INVALID_014);
    }
  }

  private static long checkedSubtract(long left, long right) {
    try {
      long value = Math.subtractExact(left, right);
      if (value < 0) {
        throw fault(Diagnostic.INVALID_008);
      }
      return value;
    } catch (ArithmeticException exception) {
      throw fault(Diagnostic.INVALID_014);
    }
  }

  private static String framedHash(String domain, String... framedFields) {
    StringBuilder input = new StringBuilder(CONTRACT_ID.length() + domain.length() + 256);
    input.append(CONTRACT_ID).append('\n').append(domain).append('\n');
    for (String framedField : framedFields) {
      input.append(framedField);
    }
    return sha256(input.toString());
  }

  private static String field(String name, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return name + "=S" + bytes.length + ":" + value + "\n";
  }

  private static String nullField(String name) {
    return name + "=N\n";
  }

  private static String sha256(String value) {
    return sha256(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String sha256(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      char[] result = new char[digest.length * 2];
      char[] hex = "0123456789abcdef".toCharArray();
      for (int index = 0; index < digest.length; index++) {
        int value = digest[index] & 0xff;
        result[index * 2] = hex[value >>> 4];
        result[index * 2 + 1] = hex[value & 0x0f];
      }
      return new String(result);
    } catch (NoSuchAlgorithmException exception) {
      throw new ExceptionInInitializerError("OFF-I1-INVALID-006");
    }
  }

  private static String opaque(String value) {
    if (value == null || !OPAQUE_ID.matcher(value).matches() || "~".equals(value)) {
      throw fault(Diagnostic.INVALID_014);
    }
    return value;
  }

  private static void validateQueueRow(
      OperationClass operationClass,
      String jobIdHash,
      String resultIdHash,
      String deletionScopeDigest,
      String deletionIdHash,
      String deletionReceiptIdHash,
      QueueState queueState,
      DeletionSubstatus deletionSubstatus,
      DeletionError error,
      ReceiptOutcome receipt,
      int effect,
      int apply,
      int deletionEffect) {
    boolean deletion = operationClass == OperationClass.DELETE_CLOUD_COPY;
    if (deletion) {
      if (jobIdHash != null
          || resultIdHash != null
          || deletionScopeDigest == null
          || (queueState != QueueState.DELETE_PENDING && queueState != QueueState.DELETED_REMOTE)
          || apply != 0
          || effect != deletionEffect) {
        throw fault(Diagnostic.INVALID_011);
      }
      if (queueState == QueueState.DELETE_PENDING) {
        if (deletionSubstatus == null || deletionReceiptIdHash != null || receipt != null) {
          throw fault(Diagnostic.INVALID_011);
        }
      } else if (deletionSubstatus != null
          || error != null
          || deletionIdHash == null
          || deletionReceiptIdHash == null
          || receipt != ReceiptOutcome.VERIFIED
          || deletionEffect != 1) {
        throw fault(Diagnostic.INVALID_011);
      }
    } else {
      if (deletionScopeDigest != null
          || deletionIdHash != null
          || deletionReceiptIdHash != null
          || deletionSubstatus != null
          || error != null
          || receipt != null
          || queueState == QueueState.DELETE_PENDING
          || queueState == QueueState.DELETED_REMOTE
          || deletionEffect != 0
          || apply > effect) {
        throw fault(Diagnostic.INVALID_011);
      }
      boolean resultBearing =
          queueState == QueueState.RESULT_AVAILABLE
              || queueState == QueueState.APPLIED
              || queueState == QueueState.CONFLICT;
      if (resultBearing != (resultIdHash != null)) {
        throw fault(Diagnostic.INVALID_011);
      }
      boolean accepted =
          queueState == QueueState.REMOTE_PROCESSING
              || queueState == QueueState.RESULT_AVAILABLE
              || queueState == QueueState.APPLIED
              || queueState == QueueState.CONFLICT;
      if (accepted && jobIdHash == null) {
        throw fault(Diagnostic.INVALID_011);
      }
    }
  }

  private static void validateLedgerGraph(
      String scenarioId,
      int nextActionOrdinal,
      long monotonicOffsetMs,
      StateVector state,
      List<FlowRow> flow,
      List<QueueRow> queue,
      List<ReplayRecord> replay,
      AttemptBudget budget,
      Result lastResult) {
    validateStateVectorGraphCategory(state, flow, queue, replay, lastResult);
    if (flow.size() != nextActionOrdinal - 1
        || (!flow.isEmpty()
            && monotonicOffsetMs != flow.get(flow.size() - 1).monotonicOffsetMs)) {
      throw fault(Diagnostic.INVALID_015);
    }
    int seedCount = seededQueueRowCount(scenarioId);
    if (queue.size() < seedCount) {
      throw fault(Diagnostic.INVALID_015);
    }
    Integer scriptedDeletionOrdinal =
        "OFF-SYN-026".equals(scenarioId) && lastResult != null
            ? deletionOrdinalFromRule(lastResult.selectedRuleId, nextActionOrdinal - 1)
            : null;
    StateVector initial =
        initialStateForGraph(scenarioId, state, queue, scriptedDeletionOrdinal);
    validateSeedStateDigestCategory(initial, queue, seedCount);
    validateSeedGraph(
        scenarioId,
        initial,
        queue,
        replay,
        budget,
        flow.isEmpty(),
        scriptedDeletionOrdinal);
    long expected = 1;
    long priorOffset = -1;
    StateVector priorPost = initial;
    int queueIndex = seedCount;
    QueueRow projected = seedCount == 0 ? null : queue.get(seedCount - 1);
    Map<String, ReplayRecord> expectedReplay = new LinkedHashMap<>();
    if (projected != null) {
      expectedReplay.put(
          projected.logicalKeyHash,
          seedReplay(projected, initial, seedRuleForScenario(scenarioId)));
    }
    for (FlowRow row : flow) {
      ActionSpec expectedAction = action(scenarioId, (int) expected);
      QueueRow paired = null;
      if (expectedAction.queueAffecting) {
        if (queueIndex >= queue.size()) {
          throw fault(Diagnostic.INVALID_015);
        }
        paired = queue.get(queueIndex++);
      }
      ReducerOutcome expectedOutcome =
          "OFF-SYN-026".equals(scenarioId)
                  && expectedAction.ordinal == 2
                  && paired != null
                  && paired.contentFreeDeletionErrorCode
                      == DeletionError.CANCEL_NOT_APPLICABLE_DELETE_PENDING
              ? ReducerOutcome.REJECTED_NO_STATE_CHANGE
              : expectedAction.outcome;
      if (row.sequence != expected
          || !row.scenarioId.equals(scenarioId)
          || !row.actionId.equals(expectedAction.actionId)) {
        throw fault(Diagnostic.INVALID_003);
      }
      if (!row.preStateVector.equals(priorPost)
          || !row.preStateDigest.equals(stateVectorDigest(row.preStateVector))
          || !row.postStateDigest.equals(stateVectorDigest(row.postStateVector))) {
        throw fault(Diagnostic.INVALID_010);
      }
      if (row.monotonicOffsetMs < priorOffset
          || row.outcome != expectedOutcome
          || !flowTransitionMatches(expectedAction, row, paired)) {
        throw fault(Diagnostic.INVALID_015);
      }
      expected++;
      Diagnostic expectedDiagnostic =
          expectedOutcome == ReducerOutcome.REJECTED_NO_STATE_CHANGE
              ? Diagnostic.POLICY_REJECTED
              : null;
      if (row.contentFreeErrorCode != expectedDiagnostic) {
        throw fault(Diagnostic.INVALID_015);
      }
      String expectedProcessingHash =
          row.preStateVector.processingCapability == ProcessingState.PROCESSING_NOT_REQUESTED
                  && row.postStateVector.processingCapability
                      == ProcessingState.PROCESSING_NOT_REQUESTED
              ? null
              : processingRequestIdHash("processing." + scenarioSuffix(scenarioId));
      if (!Objects.equals(row.processingRequestIdHash, expectedProcessingHash)) {
        throw fault(Diagnostic.INVALID_015);
      }
      if (paired != null) {
        projected = paired;
        if (!paired.preLocalStateDigest.equals(localStateDigest(row.preStateVector.local))
            || !paired.postLocalStateDigest.equals(localStateDigest(row.postStateVector.local))) {
          throw fault(Diagnostic.INVALID_010);
        }
        if (paired.queueState != row.postStateVector.queue) {
          throw fault(Diagnostic.INVALID_015);
        }
        if (expectedOutcome != ReducerOutcome.REJECTED_NO_STATE_CHANGE) {
          String replayRule =
              graphRuleId(scenarioId, expectedAction, scriptedDeletionOrdinal);
          expectedReplay.put(
              paired.logicalKeyHash,
              new ReplayRecord(
                  paired.logicalKeyHash,
                  replayInputDigest(
                      paired.operationClass,
                      paired.logicalKeyHash,
                      InputVariant.PRIMARY,
                      paired.deletionScopeDigest),
                  replayRule,
                  expectedOutcome,
                  row.postStateDigest,
                  paired.resultIdHash,
                  paired.replayMarker,
                  paired.attemptCount,
                  paired.effectCount,
                  paired.applyCount,
                  paired.remoteDeletionEffectCount));
        }
      }
      String expectedIntent = projected == null ? null : projected.intentIdHash;
      if (!Objects.equals(row.queueIntentIdHash, expectedIntent)) {
        throw fault(Diagnostic.INVALID_015);
      }
      priorOffset = row.monotonicOffsetMs;
      priorPost = row.postStateVector;
    }
    if (!state.equals(priorPost)) {
      throw fault(Diagnostic.INVALID_010);
    }
    if (queueIndex != queue.size()) {
      throw fault(Diagnostic.INVALID_015);
    }

    Map<String, QueueRow> firstByIntent = new LinkedHashMap<>();
    Map<String, QueueRow> latestByLogical = new LinkedHashMap<>();
    for (QueueRow row : queue) {
      QueueRow first = firstByIntent.putIfAbsent(row.intentIdHash, row);
      if (first != null
          && (first.operationClass != row.operationClass
              || !first.logicalKeyHash.equals(row.logicalKeyHash)
              || !Objects.equals(first.deletionScopeDigest, row.deletionScopeDigest))) {
        throw fault(Diagnostic.INVALID_015);
      }
      QueueRow previous = latestByLogical.put(row.logicalKeyHash, row);
      if (previous != null) {
        if (!previous.intentIdHash.equals(row.intentIdHash)
            || previous.operationClass != row.operationClass
            || !Objects.equals(previous.deletionScopeDigest, row.deletionScopeDigest)
            || row.attemptCount < previous.attemptCount
            || row.attemptCount - previous.attemptCount > 1
            || row.effectCount < previous.effectCount
            || row.effectCount - previous.effectCount > 1
            || row.applyCount < previous.applyCount
            || row.applyCount - previous.applyCount > 1
            || row.remoteDeletionEffectCount < previous.remoteDeletionEffectCount
            || row.remoteDeletionEffectCount - previous.remoteDeletionEffectCount > 1
            || (previous.jobIdHash != null && !previous.jobIdHash.equals(row.jobIdHash))
            || (previous.resultIdHash != null
                && row.resultIdHash != null
                && !previous.resultIdHash.equals(row.resultIdHash))
            || (previous.deletionIdHash != null
                && !previous.deletionIdHash.equals(row.deletionIdHash))
            || (previous.deletionReceiptIdHash != null
                && !previous.deletionReceiptIdHash.equals(row.deletionReceiptIdHash))) {
          throw fault(Diagnostic.INVALID_015);
        }
      }
      if (row.operationClass == OperationClass.NON_DELETION_CLOUD_INTENT
          && ((row.jobIdHash != null) != (row.effectCount == 1))) {
        throw fault(Diagnostic.INVALID_015);
      }
    }
    List<ReplayRecord> exactReplay = sortedReplay(new ArrayList<>(expectedReplay.values()));
    for (ReplayRecord expectedRecord : exactReplay) {
      for (ReplayRecord actualRecord : replay) {
        if (actualRecord.logicalKeyHash.equals(expectedRecord.logicalKeyHash)
            && !actualRecord.postStateDigest.equals(expectedRecord.postStateDigest)) {
          throw fault(Diagnostic.INVALID_010);
        }
      }
    }
    if (!replay.equals(exactReplay) || replay.size() != latestByLogical.size()) {
      throw fault(Diagnostic.INVALID_015);
    }
    if ("OFF-SYN-026".equals(scenarioId)) {
      validateExactDeletionScenarioGraph(
          initial,
          flow,
          queue,
          replay,
          budget,
          state,
          lastResult,
          scriptedDeletionOrdinal);
    } else {
      validateExactFixedScenarioGraph(
          scenarioId, initial, flow, queue, replay, budget, state, lastResult);
    }
    if (!queue.isEmpty()) {
      QueueRow terminalProjection = queue.get(queue.size() - 1);
      if (state.queue != terminalProjection.queueState
          || terminalProjection.attemptCount != budget.attemptCount) {
        throw fault(Diagnostic.INVALID_015);
      }
    } else if (state.queue != QueueState.LOCAL_ONLY) {
      throw fault(Diagnostic.INVALID_015);
    }
    if (lastResult == null) {
      if (!flow.isEmpty()) {
        throw fault(Diagnostic.INVALID_015);
      }
      return;
    }
    if (flow.isEmpty()) {
      throw fault(Diagnostic.INVALID_015);
    }
    FlowRow tail = flow.get(flow.size() - 1);
    ActionSpec tailAction = action(scenarioId, (int) tail.sequence);
    QueueRow expectedQueueAppend =
        tailAction.queueAffecting ? queue.get(queue.size() - 1) : null;
    Diagnostic expectedDiagnostic =
        tail.contentFreeErrorCode == null ? Diagnostic.NONE : tail.contentFreeErrorCode;
    QueueRow current = queue.isEmpty() ? null : queue.get(queue.size() - 1);
    if (!lastResult.postStateVector.equals(state)
        || lastResult.outcome != tail.outcome
        || !lastResult.typedLedgerDeltas.flowAppend.equals(tail)
        || !Objects.equals(lastResult.typedLedgerDeltas.queueAppend, expectedQueueAppend)
        || lastResult.typedLedgerDeltas.diagnosticCode != expectedDiagnostic
        || lastResult.effectCount != (current == null ? 0 : current.effectCount)
        || lastResult.applyCount != (current == null ? 0 : current.applyCount)
        || lastResult.remoteDeletionEffectCount
            != (current == null ? 0 : current.remoteDeletionEffectCount)
        || !terminalRuleMatches(scenarioId, tailAction, lastResult.selectedRuleId)) {
      throw fault(Diagnostic.INVALID_015);
    }
  }

  private static void validateStateVectorGraphCategory(
      StateVector state,
      List<FlowRow> flow,
      List<QueueRow> queue,
      List<ReplayRecord> replay,
      Result lastResult) {
    Set<String> stateDigests = new LinkedHashSet<>();
    Set<String> localDigests = new LinkedHashSet<>();
    stateDigests.add(stateVectorDigest(state));
    localDigests.add(localStateDigest(state.local));
    for (FlowRow row : flow) {
      String expectedPre = stateVectorDigest(row.preStateVector);
      String expectedPost = stateVectorDigest(row.postStateVector);
      if (!row.preStateDigest.equals(expectedPre) || !row.postStateDigest.equals(expectedPost)) {
        throw fault(Diagnostic.INVALID_010);
      }
      stateDigests.add(expectedPre);
      stateDigests.add(expectedPost);
      localDigests.add(localStateDigest(row.preStateVector.local));
      localDigests.add(localStateDigest(row.postStateVector.local));
    }
    for (QueueRow row : queue) {
      if (!localDigests.contains(row.preLocalStateDigest)
          || !localDigests.contains(row.postLocalStateDigest)) {
        throw fault(Diagnostic.INVALID_010);
      }
    }
    for (ReplayRecord record : replay) {
      if (!stateDigests.contains(record.postStateDigest)) {
        throw fault(Diagnostic.INVALID_010);
      }
    }
    if (lastResult != null
        && !stateDigests.contains(stateVectorDigest(lastResult.postStateVector))) {
      throw fault(Diagnostic.INVALID_010);
    }
  }

  private static void preflightTypedRunStateCategory010(
      String scenarioId,
      StateVector observedState,
      List<FlowRow> flow,
      List<QueueRow> queue,
      List<ReplayRecord> replay,
      Result lastResult) {
    preflightTypedIndependentStateDigestCategory010(flow, lastResult);
    if (observedState == null
        || flow == null
        || queue == null
        || replay == null
        || hasNullElement(flow)
        || hasNullElement(queue)
        || hasNullElement(replay)) {
      return;
    }
    try {
      validateStateVectorGraphCategory(observedState, flow, queue, replay, lastResult);
      validateResultStateDigestCategory(lastResult);
      int seedCount = seededQueueRowCount(scenarioId);
      Integer scriptedDeletionOrdinal =
          "OFF-SYN-026".equals(scenarioId) && lastResult != null
              ? deletionOrdinalFromRule(lastResult.selectedRuleId, flow.size())
              : null;
      StateVector initial =
          initialStateForGraph(scenarioId, observedState, queue, scriptedDeletionOrdinal);
      validateSeedStateDigestCategory(initial, queue, seedCount);
      int queueIndex = seedCount;
      Map<String, String> expectedReplayDigests = new LinkedHashMap<>();
      if (seedCount > 0 && queue.size() >= seedCount) {
        QueueRow seed = queue.get(seedCount - 1);
        expectedReplayDigests.put(seed.logicalKeyHash, stateVectorDigest(initial));
      }
      StateVector priorPost = initial;
      for (FlowRow row : flow) {
        if (row.sequence > Integer.MAX_VALUE) {
          return;
        }
        if (!row.preStateVector.equals(priorPost)) {
          throw fault(Diagnostic.INVALID_010);
        }
        priorPost = row.postStateVector;
        ActionSpec action = action(scenarioId, (int) row.sequence);
        if (!action.queueAffecting) {
          continue;
        }
        if (queueIndex >= queue.size()) {
          return;
        }
        QueueRow paired = queue.get(queueIndex++);
        if (!paired.preLocalStateDigest.equals(localStateDigest(row.preStateVector.local))
            || !paired.postLocalStateDigest.equals(localStateDigest(row.postStateVector.local))) {
          throw fault(Diagnostic.INVALID_010);
        }
        if (row.outcome != ReducerOutcome.REJECTED_NO_STATE_CHANGE) {
          expectedReplayDigests.put(paired.logicalKeyHash, row.postStateDigest);
        }
      }
      for (ReplayRecord actual : replay) {
        String expected = expectedReplayDigests.get(actual.logicalKeyHash);
        if (expected != null && !actual.postStateDigest.equals(expected)) {
          throw fault(Diagnostic.INVALID_010);
        }
      }
      if (!observedState.equals(priorPost)) {
        throw fault(Diagnostic.INVALID_010);
      }
    } catch (ContractFault failure) {
      if (failure.diagnostic == Diagnostic.INVALID_010) {
        throw failure;
      }
    }
  }

  private static void preflightTypedRunStateCategory003(
      String scenarioId, List<FlowRow> flow, Result lastResult) {
    if (flow == null) {
      return;
    }
    int expectedSequence = 1;
    for (FlowRow row : flow) {
      if (row != null
          && (expectedSequence > scenarioActionCount(scenarioId)
              || row.sequence != expectedSequence
              || !row.scenarioId.equals(scenarioId)
              || !row.actionId.equals(action(scenarioId, expectedSequence).actionId))) {
        throw fault(Diagnostic.INVALID_003);
      }
      expectedSequence++;
    }
    if (lastResult == null
        || lastResult.typedLedgerDeltas == null
        || lastResult.typedLedgerDeltas.flowAppend == null) {
      return;
    }
    FlowRow nested = lastResult.typedLedgerDeltas.flowAppend;
    int expectedNestedSequence = flow.size();
    if (expectedNestedSequence <= 0
        || expectedNestedSequence > scenarioActionCount(scenarioId)
        || nested.sequence != expectedNestedSequence
        || !nested.scenarioId.equals(scenarioId)
        || !nested.actionId.equals(action(scenarioId, expectedNestedSequence).actionId)) {
      throw fault(Diagnostic.INVALID_003);
    }
  }

  private static void preflightTypedIndependentStateDigestCategory010(
      List<FlowRow> flow, Result lastResult) {
    if (flow != null) {
      for (FlowRow row : flow) {
        if (row != null
            && (!row.preStateDigest.equals(stateVectorDigest(row.preStateVector))
                || !row.postStateDigest.equals(stateVectorDigest(row.postStateVector)))) {
          throw fault(Diagnostic.INVALID_010);
        }
      }
    }
    validateResultStateDigestCategory(lastResult);
  }

  private static void validateResultStateDigestCategory(Result result) {
    if (result == null || result.typedLedgerDeltas == null) {
      return;
    }
    FlowRow flow = result.typedLedgerDeltas.flowAppend;
    if (flow == null) {
      return;
    }
    if (!flow.preStateDigest.equals(stateVectorDigest(flow.preStateVector))
        || !flow.postStateDigest.equals(stateVectorDigest(flow.postStateVector))) {
      throw fault(Diagnostic.INVALID_010);
    }
    QueueRow queue = result.typedLedgerDeltas.queueAppend;
    if (queue != null
        && (!queue.preLocalStateDigest.equals(localStateDigest(flow.preStateVector.local))
            || !queue.postLocalStateDigest.equals(localStateDigest(flow.postStateVector.local)))) {
      throw fault(Diagnostic.INVALID_010);
    }
  }

  private static void validateSeedStateDigestCategory(
      StateVector initial, List<QueueRow> queue, int seedCount) {
    String expectedLocalDigest = localStateDigest(initial.local);
    int boundedSeedCount = Math.min(seedCount, queue.size());
    for (int index = 0; index < boundedSeedCount; index++) {
      QueueRow seed = queue.get(index);
      if (!seed.preLocalStateDigest.equals(expectedLocalDigest)
          || !seed.postLocalStateDigest.equals(expectedLocalDigest)) {
        throw fault(Diagnostic.INVALID_010);
      }
    }
  }

  private record GraphSeed(
      AttemptBudget budget,
      List<QueueRow> queue,
      List<ReplayRecord> replay,
      Map<String, StateVector> replayWitnesses) {}

  private static GraphSeed exactFixedGraphSeed(String scenarioId, StateVector initial) {
    AttemptBudget budget = new AttemptBudget(0, 0);
    List<QueueRow> queue = new ArrayList<>();
    List<ReplayRecord> replay = new ArrayList<>();
    Map<String, StateVector> witnesses = new LinkedHashMap<>();
    QueueRow seed =
        switch (scenarioId) {
          case "OFF-SYN-014", "OFF-SYN-017" ->
              seedNonDeletion(
                  scenarioId, initial, QueueState.WAITING_NETWORK, 0, null, null, 0, 0);
          case "OFF-SYN-018" -> {
            budget = new AttemptBudget(1, 0);
            yield seedNonDeletion(
                scenarioId,
                initial,
                QueueState.REMOTE_PROCESSING,
                1,
                fixtureJobHash(scenarioId),
                null,
                1,
                0);
          }
          case "OFF-SYN-022", "OFF-SYN-025" ->
              seedNonDeletion(
                  scenarioId, initial, QueueState.PENDING_UPLOAD, 0, null, null, 0, 0);
          default -> null;
        };
    if (seed != null) {
      queue.add(seed);
      ReplayRecord record = seedReplay(seed, initial, seedRuleForScenario(scenarioId));
      replay.add(record);
      witnesses.put(record.logicalKeyHash, initial);
    }
    return new GraphSeed(
        budget, List.copyOf(queue), sortedReplay(replay), Map.copyOf(witnesses));
  }

  private static void validateExactFixedScenarioGraph(
      String scenarioId,
      StateVector initial,
      List<FlowRow> observedFlow,
      List<QueueRow> observedQueue,
      List<ReplayRecord> observedReplay,
      AttemptBudget observedBudget,
      StateVector observedState,
      Result observedLastResult) {
    GraphSeed seed = exactFixedGraphSeed(scenarioId, initial);
    StateVector expectedState = initial;
    AttemptBudget expectedBudget = seed.budget;
    List<QueueRow> expectedQueue = new ArrayList<>(seed.queue);
    List<ReplayRecord> expectedReplay = new ArrayList<>(seed.replay);
    Map<String, StateVector> witnesses = new LinkedHashMap<>(seed.replayWitnesses);
    Result expectedLastResult = null;

    for (FlowRow observed : observedFlow) {
      ActionSpec action = action(scenarioId, (int) observed.sequence);
      QueueRow current = expectedQueue.isEmpty() ? null : expectedQueue.get(expectedQueue.size() - 1);
      ReplayRecord cached =
          current == null ? null : replayFor(expectedReplay, current.logicalKeyHash);
      StateVector witness = cached == null ? null : witnesses.get(cached.logicalKeyHash);
      Mutation mutation =
          mutateFixed(
              new MutationInput(
                  scenarioId, expectedState, expectedBudget, current, cached, witness),
              action);
      QueueRow append = materializeQueueAppend(mutation, expectedState);
      if ((append != null) != action.queueLedgerAppend) {
        throw fault(Diagnostic.INVALID_015);
      }
      QueueRow projection = append == null ? current : append;
      String processingHash =
          expectedState.processingCapability == ProcessingState.PROCESSING_NOT_REQUESTED
                  && mutation.post.processingCapability == ProcessingState.PROCESSING_NOT_REQUESTED
              ? null
              : processingRequestIdHash("processing." + scenarioSuffix(scenarioId));
      FlowRow expectedFlow =
          new FlowRow(
              observed.sequence,
              scenarioId,
              action.actionId,
              expectedState,
              mutation.post,
              mutation.outcome,
              observed.monotonicOffsetMs,
              stateVectorDigest(expectedState),
              stateVectorDigest(mutation.post),
              processingHash,
              projection == null ? null : projection.intentIdHash,
              mutation.diagnostic == Diagnostic.NONE ? null : mutation.diagnostic);
      if (!observed.equals(expectedFlow)) {
        throw fault(Diagnostic.INVALID_015);
      }
      TypedLedgerDeltas deltas =
          new TypedLedgerDeltas(expectedFlow, append, mutation.diagnostic);
      expectedLastResult =
          new Result(
              mutation.ruleId,
              mutation.outcome,
              mutation.post,
              deltas,
              projection == null ? 0 : projection.effectCount,
              projection == null ? 0 : projection.applyCount,
              projection == null ? 0 : projection.remoteDeletionEffectCount);
      if (append != null) {
        expectedQueue.add(append);
      }
      expectedReplay =
          new ArrayList<>(updateReplay(expectedReplay, append, mutation, mutation.post));
      if (!mutation.preserveReplay) {
        if (mutation.replayOverride != null) {
          witnesses.put(
              mutation.replayOverride.logicalKeyHash, mutation.replayWitnessOverride);
        } else if (append != null) {
          witnesses.put(append.logicalKeyHash, mutation.post);
        }
      }
      expectedState = mutation.post;
      expectedBudget = mutation.budget;
    }
    if (!expectedQueue.equals(observedQueue)
        || !expectedReplay.equals(observedReplay)
        || !expectedBudget.equals(observedBudget)
        || !expectedState.equals(observedState)
        || !Objects.equals(expectedLastResult, observedLastResult)) {
      throw fault(Diagnostic.INVALID_015);
    }
  }

  private static StateVector initialStateForGraph(
      String scenarioId,
      StateVector observedState,
      List<QueueRow> queue,
      Integer scriptedDeletionOrdinal) {
    StateVector initial =
        new StateVector(
            "OFF-SYN-001".equals(scenarioId)
                ? LocalState.FRESH_LOCAL_DEFAULT
                : LocalState.LOCAL_READY,
            ProcessingState.PROCESSING_NOT_REQUESTED,
            ConnectivityState.NETWORK_DENIED,
            ModelState.MODEL_NOT_INSTALLED,
            QueueState.LOCAL_ONLY);
    return switch (scenarioId) {
      case "OFF-SYN-014" -> initial.withQueue(QueueState.WAITING_NETWORK);
      case "OFF-SYN-017" ->
          new StateVector(
              LocalState.LOCAL_READY,
              ProcessingState.PROCESSING_NOT_REQUESTED,
              ConnectivityState.AVAILABLE,
              ModelState.MODEL_NOT_INSTALLED,
              QueueState.WAITING_NETWORK);
      case "OFF-SYN-018" ->
          new StateVector(
              LocalState.LOCAL_READY,
              ProcessingState.PROCESSING_QUEUED,
              ConnectivityState.AVAILABLE,
              ModelState.MODEL_NOT_INSTALLED,
              QueueState.REMOTE_PROCESSING);
      case "OFF-SYN-022", "OFF-SYN-025" -> initial.withQueue(QueueState.PENDING_UPLOAD);
      case "OFF-SYN-026" -> {
        if (queue.isEmpty()) {
          throw fault(Diagnostic.INVALID_015);
        }
        if (scriptedDeletionOrdinal != null) {
          yield deletionInitialState(scriptedDeletionOrdinal);
        }
        ConnectivityState connectivity = observedState.connectivity;
        if (connectivity != ConnectivityState.NETWORK_DENIED
            && connectivity != ConnectivityState.AVAILABLE) {
          throw fault(Diagnostic.INVALID_015);
        }
        yield new StateVector(
            LocalState.LOCAL_OPERATION_SUCCEEDED,
            ProcessingState.PROCESSING_NOT_REQUESTED,
            connectivity,
            ModelState.MODEL_NOT_INSTALLED,
            QueueState.DELETE_PENDING);
      }
      default -> initial;
    };
  }

  private static void validateSeedGraph(
      String scenarioId,
      StateVector initial,
      List<QueueRow> queue,
      List<ReplayRecord> replay,
      AttemptBudget budget,
      boolean initialOnly,
      Integer scriptedDeletionOrdinal) {
    int seedCount = seededQueueRowCount(scenarioId);
    if (seedCount == 0) {
      if (initialOnly && (!queue.isEmpty() || !replay.isEmpty())) {
        throw fault(Diagnostic.INVALID_015);
      }
      return;
    }
    QueueRow observed = queue.get(0);
    QueueRow expected;
    if ("OFF-SYN-026".equals(scenarioId)) {
      if (scriptedDeletionOrdinal == null) {
        if (!initialOnly) {
          throw fault(Diagnostic.INVALID_015);
        }
        expected = matchingUnselectedDeletionSeed(initial, observed);
      } else {
        DeletionPhase phase = deriveDeletionPhase(observed);
        if (!eligibleSourcePhase(scriptedDeletionOrdinal, phase)
            || !initial.equals(deletionInitialState(scriptedDeletionOrdinal))) {
          throw fault(Diagnostic.INVALID_015);
        }
        expected =
            seedDeletion(
                scenarioId,
                initial,
                phase,
                deletionSeedAttempt(scriptedDeletionOrdinal),
                DeletionSubstatus.DELETE_RECEIPT_POLL_ELIGIBLE);
      }
    } else {
      expected =
          switch (scenarioId) {
            case "OFF-SYN-014", "OFF-SYN-017" ->
                seedNonDeletion(
                    scenarioId, initial, QueueState.WAITING_NETWORK, 0, null, null, 0, 0);
            case "OFF-SYN-018" ->
                seedNonDeletion(
                    scenarioId,
                    initial,
                    QueueState.REMOTE_PROCESSING,
                    1,
                    fixtureJobHash(scenarioId),
                    null,
                    1,
                    0);
            case "OFF-SYN-022", "OFF-SYN-025" ->
                seedNonDeletion(
                    scenarioId, initial, QueueState.PENDING_UPLOAD, 0, null, null, 0, 0);
            default -> throw fault(Diagnostic.INVALID_003);
          };
    }
    if (!observed.equals(expected)) {
      throw fault(Diagnostic.INVALID_015);
    }
    if (initialOnly) {
      if (budget.attemptCount != expected.attemptCount
          || budget.manualRetryGrantCount != 0
          || !replay.equals(
              List.of(seedReplay(expected, initial, seedRuleForScenario(scenarioId))))) {
        throw fault(Diagnostic.INVALID_015);
      }
    }
  }

  private static QueueRow matchingUnselectedDeletionSeed(
      StateVector initial, QueueRow observed) {
    for (int rowOrdinal = 1; rowOrdinal <= 15; rowOrdinal++) {
      if (!initial.equals(deletionInitialState(rowOrdinal))) {
        continue;
      }
      for (DeletionPhase phase :
          List.of(DeletionPhase.PRE_ACCEPTANCE, DeletionPhase.POST_ACCEPTANCE)) {
        if (!eligibleSourcePhase(rowOrdinal, phase)) {
          continue;
        }
        QueueRow candidate =
            seedDeletion(
                "OFF-SYN-026",
                initial,
                phase,
                deletionSeedAttempt(rowOrdinal),
                DeletionSubstatus.DELETE_RECEIPT_POLL_ELIGIBLE);
        if (observed.equals(candidate)) {
          return candidate;
        }
      }
    }
    throw fault(Diagnostic.INVALID_015);
  }

  private static String seedRuleForScenario(String scenarioId) {
    return switch (scenarioId) {
      case "OFF-SYN-014", "OFF-SYN-017" -> "OFF-I1-RULE-Q-008";
      case "OFF-SYN-018" -> "OFF-I1-RULE-Q-011";
      case "OFF-SYN-022", "OFF-SYN-025" -> "OFF-I1-RULE-Q-007";
      case "OFF-SYN-026" -> "OFF-I1-RULE-D-001";
      default -> throw fault(Diagnostic.INVALID_003);
    };
  }

  private static boolean flowTransitionMatches(
      ActionSpec action, FlowRow row, QueueRow paired) {
    StateVector pre = row.preStateVector;
    StateVector expected =
        switch (action.transition) {
          case "=", "L:PRESERVE", "SNAPSHOT:=", "RESTORE:=",
              "Q:PRESERVE_REMOTE_PROCESSING;ATTEMPT:1_TO_2",
              "SELECT_ONE_INHERITED_DELETION_CLASS" -> pre;
          case "L:LOCAL_OPERATION_RUNNING" -> pre.withLocal(LocalState.LOCAL_OPERATION_RUNNING);
          case "L:LOCAL_OPERATION_SUCCEEDED",
              "L:LOCAL_OPERATION_SUCCEEDED;P:PRESERVE" ->
              pre.withLocal(LocalState.LOCAL_OPERATION_SUCCEEDED);
          case "L:LOCAL_OPERATION_SUCCEEDED;Q:PRESERVE_WAITING_NETWORK" ->
              pre.queue == QueueState.WAITING_NETWORK
                  ? pre.withLocal(LocalState.LOCAL_OPERATION_SUCCEEDED)
                  : null;
          case "P:PENDING_CAPABILITY" -> pre.withProcessing(ProcessingState.PENDING_CAPABILITY);
          case "P:PROCESSING_SUCCEEDED" ->
              pre.withProcessing(ProcessingState.PROCESSING_SUCCEEDED);
          case "P:WAITING_MODEL" -> pre.withProcessing(ProcessingState.WAITING_MODEL);
          case "Q:PENDING_UPLOAD" -> pre.withQueue(QueueState.PENDING_UPLOAD);
          case "Q:WAITING_NETWORK" -> pre.withQueue(QueueState.WAITING_NETWORK);
          case "Q:UPLOADING;ATTEMPT:0_TO_1" -> pre.withQueue(QueueState.UPLOADING);
          case "Q:REMOTE_PROCESSING;EFFECT:0_TO_1" ->
              pre.withQueue(QueueState.REMOTE_PROCESSING);
          case "Q:RESULT_AVAILABLE" -> pre.withQueue(QueueState.RESULT_AVAILABLE);
          case "Q:APPLIED;APPLY:0_TO_1",
              "Q:APPLIED;APPLY:0_TO_1;EFFECT:PRESERVE_ONE" ->
              pre.withQueue(QueueState.APPLIED);
          case "Q:CANCELLED", "Q:CANCELLED_NON_DELETION_ROW" ->
              pre.withQueue(QueueState.CANCELLED);
          case "Q:DELETE_PENDING;SUBSTATUS:DELETE_WAITING_NETWORK",
              "Q:APPEND_DELETE_PENDING_ROW_AND_PROJECT" ->
              pre.withQueue(QueueState.DELETE_PENDING);
          case "RECONCILE_SELECTED_CLASS" ->
              paired == null ? null : pre.withQueue(paired.queueState);
          default -> null;
        };
    return expected != null && expected.equals(row.postStateVector);
  }

  private static String graphRuleId(
      String scenarioId, ActionSpec action, Integer scriptedDeletionOrdinal) {
    if (!"OFF-SYN-026".equals(scenarioId)) {
      return action.selectedRuleId;
    }
    if (scriptedDeletionOrdinal == null) {
      throw fault(Diagnostic.INVALID_015);
    }
    return (action.ordinal == 1 ? "OFF-I1-RULE-DP-" : "OFF-I1-RULE-D-")
        + three(scriptedDeletionOrdinal);
  }

  private static int deletionOrdinalFromRule(String ruleId, int completedActions) {
    String prefix = completedActions == 1 ? "OFF-I1-RULE-DP-" : "OFF-I1-RULE-D-";
    if (completedActions < 1
        || completedActions > 2
        || ruleId == null
        || !ruleId.startsWith(prefix)
        || ruleId.length() != prefix.length() + 3) {
      throw fault(Diagnostic.INVALID_015);
    }
    try {
      int ordinal = Integer.parseInt(ruleId.substring(prefix.length()));
      if (ordinal < 1 || ordinal > 15) {
        throw fault(Diagnostic.INVALID_015);
      }
      return ordinal;
    } catch (NumberFormatException exception) {
      throw fault(Diagnostic.INVALID_015);
    }
  }

  private static void validateExactDeletionScenarioGraph(
      StateVector initial,
      List<FlowRow> observedFlow,
      List<QueueRow> observedQueue,
      List<ReplayRecord> observedReplay,
      AttemptBudget observedBudget,
      StateVector observedState,
      Result observedLastResult,
      Integer rowOrdinal) {
    if (rowOrdinal == null) {
      if (!observedFlow.isEmpty() || observedLastResult != null || observedQueue.size() != 1) {
        throw fault(Diagnostic.INVALID_015);
      }
      return;
    }
    if (observedFlow.isEmpty() || observedFlow.size() > 2 || observedQueue.size() != observedFlow.size() + 1) {
      throw fault(Diagnostic.INVALID_015);
    }

    DeletionPhase sourcePhase = deriveDeletionPhase(observedQueue.get(0));
    StateVector expectedState = deletionInitialState(rowOrdinal);
    if (!initial.equals(expectedState) || !eligibleSourcePhase(rowOrdinal, sourcePhase)) {
      throw fault(Diagnostic.INVALID_015);
    }
    long seedAttempt = deletionSeedAttempt(rowOrdinal);
    AttemptBudget expectedBudget = new AttemptBudget(seedAttempt, 0);
    QueueRow seed = seedDeletion("OFF-SYN-026", expectedState, sourcePhase, seedAttempt);
    List<QueueRow> expectedQueue = new ArrayList<>(List.of(seed));
    List<ReplayRecord> expectedReplay =
        new ArrayList<>(List.of(seedReplay(seed, expectedState, "OFF-I1-RULE-D-001")));
    Result expectedLastResult = null;
    String previousSelectedRuleId = null;

    for (int index = 0; index < observedFlow.size(); index++) {
      int actionOrdinal = index + 1;
      FlowRow observed = observedFlow.get(index);
      ActionSpec action = action("OFF-SYN-026", actionOrdinal);
      QueueRow current = expectedQueue.get(expectedQueue.size() - 1);
      Mutation mutation =
          mutateDeletion(
              new DeletionMutationInput(
                  "OFF-SYN-026",
                  expectedState,
                  expectedBudget,
                  current,
                  previousSelectedRuleId),
              action,
              rowOrdinal);
      QueueRow append = materializeQueueAppend(mutation, expectedState);
      if (append == null) {
        throw fault(Diagnostic.INVALID_015);
      }
      FlowRow expectedFlow =
          new FlowRow(
              actionOrdinal,
              "OFF-SYN-026",
              action.actionId,
              expectedState,
              mutation.post,
              mutation.outcome,
              observed.monotonicOffsetMs,
              stateVectorDigest(expectedState),
              stateVectorDigest(mutation.post),
              null,
              append.intentIdHash,
              mutation.diagnostic == Diagnostic.NONE ? null : mutation.diagnostic);
      if (!observed.equals(expectedFlow)) {
        throw fault(Diagnostic.INVALID_015);
      }
      TypedLedgerDeltas deltas =
          new TypedLedgerDeltas(expectedFlow, append, mutation.diagnostic);
      expectedLastResult =
          new Result(
              mutation.ruleId,
              mutation.outcome,
              mutation.post,
              deltas,
              append.effectCount,
              append.applyCount,
              append.remoteDeletionEffectCount);
      expectedQueue.add(append);
      expectedReplay =
          new ArrayList<>(updateReplay(expectedReplay, append, mutation, mutation.post));
      expectedState = mutation.post;
      expectedBudget = mutation.budget;
      previousSelectedRuleId = mutation.ruleId;
    }

    if (!expectedQueue.equals(observedQueue)
        || !expectedReplay.equals(observedReplay)
        || !expectedBudget.equals(observedBudget)
        || !expectedState.equals(observedState)
        || !Objects.equals(expectedLastResult, observedLastResult)) {
      throw fault(Diagnostic.INVALID_015);
    }
  }

  private static int seededQueueRowCount(String scenarioId) {
    return switch (scenarioId) {
      case "OFF-SYN-014", "OFF-SYN-017", "OFF-SYN-018", "OFF-SYN-022",
          "OFF-SYN-025", "OFF-SYN-026" -> 1;
      default -> 0;
    };
  }

  private static boolean terminalRuleMatches(
      String scenarioId, ActionSpec action, String selectedRuleId) {
    if (!"OFF-SYN-026".equals(scenarioId)) {
      return action.selectedRuleId.equals(selectedRuleId);
    }
    String prefix = action.ordinal == 1 ? "OFF-I1-RULE-DP-" : "OFF-I1-RULE-D-";
    return selectedRuleId != null
        && selectedRuleId.startsWith(prefix)
        && selectedRuleId.length() == prefix.length() + 3;
  }

  private static <T> List<T> immutableNoNulls(List<T> source) {
    requirePresent(source);
    for (T value : source) {
      requirePresent(value);
    }
    return List.copyOf(source);
  }

  private static boolean hasNullElement(Iterable<?> values) {
    for (Object value : values) {
      if (value == null) {
        return true;
      }
    }
    return false;
  }

  private static <T> T requirePresent(T value) {
    if (value == null) {
      throw fault(Diagnostic.INVALID_014);
    }
    return value;
  }

  private static void requireHash(String value) {
    if (value == null || !HASH_64.matcher(value).matches()) {
      throw fault(Diagnostic.INVALID_014);
    }
  }

  private static void requireHashFor(String value, Diagnostic diagnostic) {
    if (value == null || !HASH_64.matcher(value).matches()) {
      throw fault(diagnostic);
    }
  }

  private static void requireNullableHash(String value) {
    if (value != null) {
      requireHash(value);
    }
  }

  private static void requireNullableHashFor(String value, Diagnostic diagnostic) {
    if (value != null) {
      requireHashFor(value, diagnostic);
    }
  }

  private static void requireBit(int value) {
    if (value != 0 && value != 1) {
      throw fault(Diagnostic.INVALID_014);
    }
  }

  private static void requireBitFor(int value, Diagnostic diagnostic) {
    if (value != 0 && value != 1) {
      throw fault(diagnostic);
    }
  }

  private static void requireOutcomeDiagnostic(
      ReducerOutcome outcome, String selectedRuleId, Diagnostic diagnostic) {
    boolean valid =
        switch (outcome) {
          case TRANSITION_APPLIED ->
              selectedRuleId != null && diagnostic == Diagnostic.NONE;
          case NO_STATE_CHANGE ->
              (selectedRuleId == null && diagnostic == Diagnostic.NO_ELIGIBLE_RULE)
                  || ("OFF-I1-RULE-Q-002".equals(selectedRuleId)
                      && diagnostic == Diagnostic.IDEMPOTENCY_INPUT_MISMATCH);
          case REJECTED_NO_STATE_CHANGE ->
              "OFF-I1-RULE-D-013".equals(selectedRuleId)
                  && diagnostic == Diagnostic.POLICY_REJECTED;
          case INVALID_INPUT ->
              selectedRuleId == null && diagnostic.name().startsWith("INVALID_");
        };
    if (!valid) {
      throw fault(Diagnostic.INVALID_015);
    }
  }

  private static void requireScenarioId(String value) {
    if (value == null || !value.matches("OFF-SYN-(00[1-9]|01[0-9]|02[0-6])")) {
      throw fault(Diagnostic.INVALID_003);
    }
  }

  private static void requireActionId(String value) {
    if (!exactActionOrEventId(value, "OFF-I1-ACT-")) {
      throw fault(Diagnostic.INVALID_003);
    }
  }

  private static void requireEventId(String value) {
    if (!exactActionOrEventId(value, "OFF-I1-EVT-")) {
      throw fault(Diagnostic.INVALID_003);
    }
  }

  private static boolean exactActionOrEventId(String value, String prefix) {
    if (value == null
        || !value.matches(prefix + "(00[1-9]|01[0-9]|02[0-6])-0[1-5]")) {
      return false;
    }
    String scenarioId = "OFF-SYN-" + value.substring(prefix.length(), prefix.length() + 3);
    int ordinal = Integer.parseInt(value.substring(value.length() - 2));
    return ordinal <= scenarioActionCount(scenarioId);
  }

  private static void requireRuleId(String value) {
    if (value == null || !exactRuleId(value)) {
      throw fault(Diagnostic.INVALID_003);
    }
  }

  private static boolean exactRuleId(String value) {
    if ("OFF-I1-RULE-C-001".equals(value)) {
      return true;
    }
    if (value.matches("OFF-I1-RULE-Q-(00[1-9]|01[0-9]|020)")) {
      return true;
    }
    if (value.matches("OFF-I1-RULE-DP?-(00[1-9]|01[0-5])")) {
      return true;
    }
    if (!value.matches("OFF-I1-RULE-A-(00[1-9]|01[0-9]|02[0-6])-0[1-5]")) {
      return false;
    }
    String suffix = value.substring("OFF-I1-RULE-A-".length());
    String actionId = "OFF-I1-ACT-" + suffix;
    return exactActionOrEventId(actionId, "OFF-I1-ACT-")
        && nonDirect(actionId) == null
        && !suffix.startsWith("026-");
  }

  private static int scenarioActionCount(String scenarioId) {
    requireScenarioId(scenarioId);
    int index = Integer.parseInt(scenarioId.substring(scenarioId.length() - 3)) - 1;
    return Integer.parseInt(SCENARIO_COUNTS.get(index));
  }

  private static Set<String> orderedSet(String... values) {
    LinkedHashSet<String> result = new LinkedHashSet<>(Arrays.asList(values));
    if (result.size() != values.length) {
      throw fault(Diagnostic.INVALID_003);
    }
    return Collections.unmodifiableSet(result);
  }

  private static String two(int value) {
    return value < 10 ? "0" + value : Integer.toString(value);
  }

  private static String three(int value) {
    if (value < 10) {
      return "00" + value;
    }
    return value < 100 ? "0" + value : Integer.toString(value);
  }

  private static void member(StringBuilder out, String name, String value) {
    quoted(out, name);
    out.append(':');
    quoted(out, value);
  }

  private static void quoted(StringBuilder out, String value) {
    out.append('"');
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (character < 0x20) {
            out.append("\\u00");
            String hex = Integer.toHexString(character);
            if (hex.length() == 1) {
              out.append('0');
            }
            out.append(hex);
          } else {
            out.append(character);
          }
        }
      }
    }
    out.append('"');
  }

  static final class ContractFault extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final Diagnostic diagnostic;

    ContractFault(Diagnostic diagnostic) {
      super(diagnostic.code());
      this.diagnostic = diagnostic;
    }

    Diagnostic diagnostic() {
      return diagnostic;
    }
  }

  private static ContractFault fault(Diagnostic diagnostic) {
    return new ContractFault(diagnostic);
  }
}
