package com.monumentogram.dora.stage0.offline.i1;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Standalone, content-free host verification for {@link OfflineI1Oracle}. */
public final class OfflineI1OracleTest {
  private static int assertions;
  private static final int[] SCENARIO_COUNTS = {
    2, 3, 2, 3, 3, 1, 2, 3, 2, 1, 1, 1, 2, 5, 2, 2, 5, 2, 5, 3, 2, 3, 3, 3,
    4, 2
  };
  private static final String[][] SCENARIO_TRANSITIONS = {
    {"=", "="},
    {"L:LOCAL_OPERATION_RUNNING", "L:LOCAL_OPERATION_RUNNING", "L:LOCAL_OPERATION_SUCCEEDED"},
    {"RESTORE:=", "L:LOCAL_OPERATION_SUCCEEDED"},
    {"L:LOCAL_OPERATION_RUNNING", "L:LOCAL_OPERATION_RUNNING", "L:LOCAL_OPERATION_SUCCEEDED"},
    {"L:LOCAL_OPERATION_SUCCEEDED", "L:LOCAL_OPERATION_SUCCEEDED", "L:LOCAL_OPERATION_SUCCEEDED"},
    {"L:LOCAL_OPERATION_SUCCEEDED"},
    {"L:LOCAL_OPERATION_RUNNING", "L:LOCAL_OPERATION_SUCCEEDED"},
    {"L:LOCAL_OPERATION_RUNNING", "L:LOCAL_OPERATION_SUCCEEDED", "="},
    {"P:WAITING_MODEL", "L:LOCAL_OPERATION_SUCCEEDED;P:PRESERVE"},
    {"P:PROCESSING_SUCCEEDED"},
    {"P:PROCESSING_SUCCEEDED"},
    {"P:PROCESSING_SUCCEEDED"},
    {"Q:PENDING_UPLOAD", "Q:WAITING_NETWORK"},
    {"L:LOCAL_OPERATION_SUCCEEDED;Q:PRESERVE_WAITING_NETWORK", "L:LOCAL_OPERATION_SUCCEEDED;Q:PRESERVE_WAITING_NETWORK", "L:LOCAL_OPERATION_SUCCEEDED;Q:PRESERVE_WAITING_NETWORK", "L:LOCAL_OPERATION_SUCCEEDED;Q:PRESERVE_WAITING_NETWORK", "L:LOCAL_OPERATION_SUCCEEDED;Q:PRESERVE_WAITING_NETWORK"},
    {"SNAPSHOT:=", "RESTORE:="},
    {"SNAPSHOT:=", "RESTORE:="},
    {"Q:PENDING_UPLOAD", "Q:UPLOADING;ATTEMPT:0_TO_1", "Q:REMOTE_PROCESSING;EFFECT:0_TO_1", "Q:RESULT_AVAILABLE", "Q:APPLIED;APPLY:0_TO_1"},
    {"Q:PRESERVE_REMOTE_PROCESSING;ATTEMPT:1_TO_2", "Q:APPLIED;APPLY:0_TO_1;EFFECT:PRESERVE_ONE"},
    {"=", "=", "L:LOCAL_OPERATION_SUCCEEDED", "=", "="},
    {"=", "RESTORE:=", "L:LOCAL_OPERATION_SUCCEEDED"},
    {"P:PENDING_CAPABILITY", "L:LOCAL_OPERATION_SUCCEEDED;P:PRESERVE"},
    {"Q:CANCELLED", "=", "L:LOCAL_OPERATION_SUCCEEDED"},
    {"L:LOCAL_OPERATION_RUNNING", "L:LOCAL_OPERATION_SUCCEEDED", "="},
    {"L:LOCAL_OPERATION_SUCCEEDED", "Q:DELETE_PENDING;SUBSTATUS:DELETE_WAITING_NETWORK", "="},
    {"Q:CANCELLED_NON_DELETION_ROW", "L:PRESERVE", "Q:APPEND_DELETE_PENDING_ROW_AND_PROJECT", "="},
    {"SELECT_ONE_INHERITED_DELETION_CLASS", "RECONCILE_SELECTED_CLASS"}
  };

  private static final String[] Q_EXPECTED = {
    "OFF-I1-RULE-Q-001|10|REVALIDATE_CONSENT|NON_DELETION_NONTERMINAL|CONSENT_INVALID_OR_REVOKED|CANCELLED|TRANSITION_APPLIED|true|ZERO|true|true",
    "OFF-I1-RULE-Q-002|20|IDEMPOTENCY_LOOKUP|ANY_QUEUE_ROW|SAME_KEY_DIFFERENT_CANONICAL_INPUT|UNCHANGED|NO_STATE_CHANGE|false|ZERO|false|false",
    "OFF-I1-RULE-Q-003|30|REVALIDATE_PROFILE_OR_RUNTIME|WAITING_NETWORK_OR_PENDING_UPLOAD_OR_FAILED_RETRYABLE|PROFILE_OR_RUNTIME_BLOCKED|PENDING_UPLOAD|TRANSITION_APPLIED|true|ZERO|true|true",
    "OFF-I1-RULE-Q-004|40|REVALIDATE_CONNECTIVITY|PENDING_UPLOAD_OR_FAILED_RETRYABLE|CONNECTIVITY_DENIED_AND_PRESERVE_EXISTING_JOB_ID_HASH_NULL_OR_NON_NULL|WAITING_NETWORK|TRANSITION_APPLIED|true|ZERO|true|true",
    "OFF-I1-RULE-Q-005|41|TRANSPORT_CONNECTIVITY_LOST|UPLOADING_OR_REMOTE_PROCESSING|CONNECTIVITY_LOST|FAILED_RETRYABLE|TRANSITION_APPLIED|true|PRESERVE|true|true",
    "OFF-I1-RULE-Q-006|50|EXPLICIT_CANCEL|ACTIVE_PRE_APPLICATION|VALID_CANCEL_OR_REVOKE|CANCELLED|TRANSITION_APPLIED|true|ZERO|true|true",
    "OFF-I1-RULE-Q-007|100|ENQUEUE_NON_DELETION|LOCAL_ONLY|NEW_LOGICAL_KEY|PENDING_UPLOAD|TRANSITION_APPLIED|true|ZERO|true|true",
    "OFF-I1-RULE-Q-008|110|SCHEDULER_TICK|PENDING_UPLOAD|CONNECTIVITY_DENIED|WAITING_NETWORK|TRANSITION_APPLIED|true|ZERO|true|true",
    "OFF-I1-RULE-Q-009|120|REVALIDATE|WAITING_NETWORK|ALL_REVALIDATION_ALLOW|PENDING_UPLOAD|TRANSITION_APPLIED|true|ZERO|true|true",
    "OFF-I1-RULE-Q-010|130|SEND|PENDING_UPLOAD|AVAILABLE_AND_POSITIVE_BUDGET|UPLOADING|TRANSITION_APPLIED|true|ATTEMPT_PLUS_ONE|true|true",
    "OFF-I1-RULE-Q-011|140|DURABLE_ACCEPTANCE|UPLOADING|FIRST_ACCEPTANCE|REMOTE_PROCESSING|TRANSITION_APPLIED|true|EFFECT_ZERO_TO_ONE|true|true",
    "OFF-I1-RULE-Q-012|150|VALID_RESULT|REMOTE_PROCESSING|RESULT_VALID|RESULT_AVAILABLE|TRANSITION_APPLIED|true|ZERO|true|true",
    "OFF-I1-RULE-Q-013|160|APPLY_RESULT|RESULT_AVAILABLE|VALIDATION_AND_USER_TRUTH_ALLOW|APPLIED|TRANSITION_APPLIED|true|APPLY_ZERO_TO_ONE|true|true",
    "OFF-I1-RULE-Q-014|161|APPLY_RESULT|RESULT_AVAILABLE|CURRENT_USER_TRUTH_BLOCKS|CONFLICT|TRANSITION_APPLIED|true|ZERO|true|true",
    "OFF-I1-RULE-Q-015|170|TRANSPORT_FAILURE|UPLOADING_OR_REMOTE_PROCESSING|RETRYABLE_ERROR|FAILED_RETRYABLE|TRANSITION_APPLIED|true|PRESERVE|true|true",
    "OFF-I1-RULE-Q-016|171|RESUME_OR_MANUAL_RETRY|FAILED_RETRYABLE_WITHOUT_JOB|REVALIDATION_ALLOW_AND_EITHER_POSITIVE_REMAINING_WITH_GRANT_NONE_OR_ZERO_REMAINING_WITH_ELIGIBLE_EXPLICIT_GRANT|PENDING_UPLOAD|TRANSITION_APPLIED|true|ZERO_FOR_POSITIVE_REMAINING_WITH_GRANT_NONE_OR_MANUAL_RETRY_GRANT_PLUS_ONE_ATTEMPT_ZERO_FOR_ZERO_REMAINING_WITH_ELIGIBLE_EXPLICIT_GRANT|true|true",
    "OFF-I1-RULE-Q-017|172|RESUME_OR_MANUAL_RETRY|FAILED_RETRYABLE_WITH_JOB|REVALIDATION_ALLOW_AND_EITHER_POSITIVE_REMAINING_WITH_GRANT_NONE_OR_ZERO_REMAINING_WITH_ELIGIBLE_EXPLICIT_GRANT|REMOTE_PROCESSING|TRANSITION_APPLIED|true|ZERO_FOR_POSITIVE_REMAINING_WITH_GRANT_NONE_OR_MANUAL_RETRY_GRANT_PLUS_ONE_ATTEMPT_ZERO_FOR_ZERO_REMAINING_WITH_ELIGIBLE_EXPLICIT_GRANT|true|true",
    "OFF-I1-RULE-Q-018|180|FINAL_OR_BUDGET_EXHAUSTED|ACTIVE_OR_RETRYABLE|TERMINAL_OR_AUTOMATIC_BUDGET_EXHAUSTED|FAILED_FINAL|TRANSITION_APPLIED|true|ZERO|true|true",
    "OFF-I1-RULE-Q-019|190|TRANSPORT_REPLAY|REMOTE_PROCESSING|SAME_INPUT|UNCHANGED|TRANSITION_APPLIED|true|ATTEMPT_PLUS_ONE_EFFECT_STAYS_ONE|true|true",
    "OFF-I1-RULE-Q-020|191|DUPLICATE_TRIGGER|APPLIED|SAME_INPUT|UNCHANGED|TRANSITION_APPLIED|false|ZERO|true|false"
  };

  private enum FixtureSource {
    LOCAL_ONLY,
    PENDING,
    WAITING,
    UPLOADING,
    REMOTE,
    RESULT,
    APPLIED,
    FAILED_NO_JOB_POSITIVE,
    FAILED_JOB_POSITIVE,
    FAILED_NO_JOB_ZERO,
    FAILED_JOB_ZERO
  }

  private record QueueCase(
      int ordinal,
      FixtureSource positive,
      FixtureSource mismatch,
      OfflineI1Oracle.QueueSignal signal,
      OfflineI1Oracle.QueueState target,
      int attemptDelta,
      int grantDelta,
      int effect,
      int apply,
      boolean flowAppend,
      boolean queueAppend,
      OfflineI1Oracle.ReducerOutcome outcome,
      OfflineI1Oracle.Diagnostic diagnostic) {}

  private record QueueFixture(
      OfflineI1Oracle.StateVector state,
      OfflineI1Oracle.AttemptBudget budget,
      OfflineI1Oracle.QueueRow row,
      OfflineI1Oracle.ReplayRecord replay,
      OfflineI1Oracle.StateVector replayWitness) {}

  private enum ExpectedPhases {
    PRE,
    POST,
    BOTH
  }

  private enum ExpectedBudget {
    ANY,
    POSITIVE,
    ZERO
  }

  private record DeletionExpected(
      int ordinal,
      String event,
      OfflineI1Oracle.QueueState queueState,
      OfflineI1Oracle.DeletionSubstatus substatus,
      boolean preserveSubstatus,
      String deletionIdOutcome,
      OfflineI1Oracle.DeletionError error,
      OfflineI1Oracle.ReceiptOutcome receipt,
      int dispatchAttemptDelta,
      int dispatchGrantDelta,
      int effectDelta,
      int remoteDeletionEffectDelta,
      OfflineI1Oracle.ReducerOutcome reconcileOutcome,
      ExpectedPhases phases,
      ExpectedBudget budget,
      OfflineI1Oracle.ExplicitGrant grant,
      OfflineI1Oracle.RevalidationOutcome revalidationOutcome,
      String gateRuleId) {}

  private static final List<DeletionExpected> D_EXPECTED =
      List.of(
          de(1, "DURABLE_ENQUEUE_WHILE_NETWORK_DENIED", OfflineI1Oracle.QueueState.DELETE_PENDING, OfflineI1Oracle.DeletionSubstatus.DELETE_WAITING_NETWORK, false, "NULL", null, null, 0, 0, 0, 0, OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED, ExpectedPhases.PRE, ExpectedBudget.ANY, OfflineI1Oracle.ExplicitGrant.NONE, OfflineI1Oracle.RevalidationOutcome.BLOCK_CONNECTIVITY_DENIED, "OFF-I1-RULE-D-001"),
          de(2, "PRE_ACCEPTANCE_RESUME", OfflineI1Oracle.QueueState.DELETE_PENDING, OfflineI1Oracle.DeletionSubstatus.DELETE_RETRY_SCHEDULED, false, "NULL", null, null, 1, 0, 0, 0, OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED, ExpectedPhases.PRE, ExpectedBudget.POSITIVE, OfflineI1Oracle.ExplicitGrant.NONE, OfflineI1Oracle.RevalidationOutcome.ALLOW_DELETE_CONSENT_NOT_APPLICABLE, null),
          de(3, "ACCEPTED_RESPONSE_OR_RECEIPT_POLL_PENDING", OfflineI1Oracle.QueueState.DELETE_PENDING, OfflineI1Oracle.DeletionSubstatus.DELETE_RECEIPT_POLL_ELIGIBLE, false, "REQUIRED_IMMUTABLE", null, null, 1, 0, 0, 0, OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED, ExpectedPhases.PRE, ExpectedBudget.POSITIVE, OfflineI1Oracle.ExplicitGrant.NONE, OfflineI1Oracle.RevalidationOutcome.ALLOW_DELETE_CONSENT_NOT_APPLICABLE, null),
          de(4, "NETWORK_DENIED_AFTER_ACCEPTANCE", OfflineI1Oracle.QueueState.DELETE_PENDING, OfflineI1Oracle.DeletionSubstatus.DELETE_WAITING_NETWORK, false, "REQUIRED_IMMUTABLE", null, null, 0, 0, 0, 0, OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED, ExpectedPhases.POST, ExpectedBudget.ANY, OfflineI1Oracle.ExplicitGrant.NONE, OfflineI1Oracle.RevalidationOutcome.BLOCK_CONNECTIVITY_DENIED, "OFF-I1-RULE-D-004"),
          de(5, "RETRYABLE_FAILURE_OR_BACKOFF", OfflineI1Oracle.QueueState.DELETE_PENDING, OfflineI1Oracle.DeletionSubstatus.DELETE_RETRY_SCHEDULED, false, "PRESERVE_PHASE", null, null, 1, 0, 0, 0, OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED, ExpectedPhases.BOTH, ExpectedBudget.POSITIVE, OfflineI1Oracle.ExplicitGrant.NONE, OfflineI1Oracle.RevalidationOutcome.ALLOW_DELETE_CONSENT_NOT_APPLICABLE, null),
          de(6, "PROFILE_INVALID", OfflineI1Oracle.QueueState.DELETE_PENDING, OfflineI1Oracle.DeletionSubstatus.DELETE_REVALIDATION_REQUIRED, false, "PRESERVE_PHASE", OfflineI1Oracle.DeletionError.DELETE_PROFILE_REVALIDATION_REQUIRED, null, 0, 0, 0, 0, OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED, ExpectedPhases.BOTH, ExpectedBudget.ANY, OfflineI1Oracle.ExplicitGrant.NONE, OfflineI1Oracle.RevalidationOutcome.BLOCK_PROFILE_REVALIDATION_REQUIRED, "OFF-I1-RULE-D-006"),
          de(7, "SCOPE_INVALID", OfflineI1Oracle.QueueState.DELETE_PENDING, OfflineI1Oracle.DeletionSubstatus.DELETE_REVALIDATION_REQUIRED, false, "PRESERVE_PHASE", OfflineI1Oracle.DeletionError.DELETE_SCOPE_REVALIDATION_REQUIRED, null, 0, 0, 0, 0, OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED, ExpectedPhases.BOTH, ExpectedBudget.ANY, OfflineI1Oracle.ExplicitGrant.NONE, OfflineI1Oracle.RevalidationOutcome.BLOCK_PROFILE_REVALIDATION_REQUIRED, "OFF-I1-RULE-D-007"),
          de(8, "FINAL_TLS_TRUST_OR_NAME_REJECT", OfflineI1Oracle.QueueState.DELETE_PENDING, OfflineI1Oracle.DeletionSubstatus.DELETE_USER_ACTION_REQUIRED, false, "PRESERVE_PHASE", OfflineI1Oracle.DeletionError.DELETE_TLS_TRUST_OR_NAME_REJECTED, null, 1, 0, 0, 0, OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED, ExpectedPhases.BOTH, ExpectedBudget.POSITIVE, OfflineI1Oracle.ExplicitGrant.NONE, OfflineI1Oracle.RevalidationOutcome.ALLOW_DELETE_CONSENT_NOT_APPLICABLE, null),
          de(9, "FINAL_SCHEMA_OR_FORMAT_REJECT", OfflineI1Oracle.QueueState.DELETE_PENDING, OfflineI1Oracle.DeletionSubstatus.DELETE_USER_ACTION_REQUIRED, false, "PRESERVE_PHASE", OfflineI1Oracle.DeletionError.DELETE_SCHEMA_OR_FORMAT_REJECTED, null, 1, 0, 0, 0, OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED, ExpectedPhases.BOTH, ExpectedBudget.POSITIVE, OfflineI1Oracle.ExplicitGrant.NONE, OfflineI1Oracle.RevalidationOutcome.ALLOW_DELETE_CONSENT_NOT_APPLICABLE, null),
          de(10, "FINAL_RESPONSE_INTEGRITY_REJECT", OfflineI1Oracle.QueueState.DELETE_PENDING, OfflineI1Oracle.DeletionSubstatus.DELETE_USER_ACTION_REQUIRED, false, "PRESERVE_PHASE", OfflineI1Oracle.DeletionError.DELETE_RESPONSE_INTEGRITY_REJECTED, null, 1, 0, 0, 0, OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED, ExpectedPhases.BOTH, ExpectedBudget.POSITIVE, OfflineI1Oracle.ExplicitGrant.NONE, OfflineI1Oracle.RevalidationOutcome.ALLOW_DELETE_CONSENT_NOT_APPLICABLE, null),
          de(11, "FINAL_IDEMPOTENCY_PAYLOAD_MISMATCH", OfflineI1Oracle.QueueState.DELETE_PENDING, OfflineI1Oracle.DeletionSubstatus.DELETE_USER_ACTION_REQUIRED, false, "PRESERVE_PHASE", OfflineI1Oracle.DeletionError.DELETE_IDEMPOTENCY_PAYLOAD_MISMATCH, null, 1, 0, 0, 0, OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED, ExpectedPhases.BOTH, ExpectedBudget.POSITIVE, OfflineI1Oracle.ExplicitGrant.NONE, OfflineI1Oracle.RevalidationOutcome.ALLOW_DELETE_CONSENT_NOT_APPLICABLE, null),
          de(12, "RETRY_BUDGET_EXHAUSTED", OfflineI1Oracle.QueueState.DELETE_PENDING, OfflineI1Oracle.DeletionSubstatus.DELETE_MANUAL_RETRY_REQUIRED, false, "PRESERVE_PHASE", OfflineI1Oracle.DeletionError.DELETE_FINITE_BUDGET_EXHAUSTED, null, 0, 0, 0, 0, OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED, ExpectedPhases.BOTH, ExpectedBudget.ZERO, OfflineI1Oracle.ExplicitGrant.NONE, OfflineI1Oracle.RevalidationOutcome.BLOCK_FINITE_BUDGET_EXHAUSTED, "OFF-I1-RULE-D-012"),
          de(13, "CANCEL_REQUESTED_WHILE_DELETE_PENDING", OfflineI1Oracle.QueueState.DELETE_PENDING, null, true, "PRESERVE_PHASE", OfflineI1Oracle.DeletionError.CANCEL_NOT_APPLICABLE_DELETE_PENDING, null, 0, 0, 0, 0, OfflineI1Oracle.ReducerOutcome.REJECTED_NO_STATE_CHANGE, ExpectedPhases.BOTH, ExpectedBudget.ANY, OfflineI1Oracle.ExplicitGrant.NONE, OfflineI1Oracle.RevalidationOutcome.ALLOW_DELETE_CONSENT_NOT_APPLICABLE, null),
          de(14, "DELETE_REVALIDATED_OR_USER_ACTION_CONFIRMED_AFTER_ACCEPTANCE", OfflineI1Oracle.QueueState.DELETE_PENDING, OfflineI1Oracle.DeletionSubstatus.DELETE_RECEIPT_POLL_ELIGIBLE, false, "REQUIRED_IMMUTABLE", null, null, 1, 1, 0, 0, OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED, ExpectedPhases.POST, ExpectedBudget.ZERO, OfflineI1Oracle.ExplicitGrant.ELIGIBLE_EXPLICIT_GRANT, OfflineI1Oracle.RevalidationOutcome.ALLOW_DELETE_CONSENT_NOT_APPLICABLE, null),
          de(15, "VERIFIED_SCOPED_RECEIPT", OfflineI1Oracle.QueueState.DELETED_REMOTE, null, false, "REQUIRED_IMMUTABLE", null, OfflineI1Oracle.ReceiptOutcome.VERIFIED, 1, 0, 1, 1, OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED, ExpectedPhases.POST, ExpectedBudget.POSITIVE, OfflineI1Oracle.ExplicitGrant.NONE, OfflineI1Oracle.RevalidationOutcome.ALLOW_DELETE_CONSENT_NOT_APPLICABLE, null));

  private enum RevalidationSource {
    PENDING,
    WAITING,
    FAILED,
    REQUIRED_MODEL,
    OPTIONAL_CAPABILITY
  }

  private enum ExpectedJob {
    NOT_APPLICABLE,
    ABSENT,
    PRESENT,
    BOTH
  }

  private enum RevalidationCause {
    CONSENT,
    PROFILE,
    RUNTIME,
    CONNECTIVITY,
    BUDGET,
    ALLOW,
    POSITIVE,
    GRANT,
    MODEL_MISSING
  }

  private record RevalidationExpected(
      RevalidationSource source,
      ExpectedJob job,
      RevalidationCause cause,
      OfflineI1Oracle.RevalidationOutcome outcome,
      String rule,
      OfflineI1Oracle.QueueState queueTarget,
      OfflineI1Oracle.ProcessingState processingTarget,
      int grantDelta) {}

  private static final List<RevalidationExpected> R_EXPECTED =
      List.of(
          rx(RevalidationSource.PENDING, ExpectedJob.NOT_APPLICABLE, RevalidationCause.CONSENT, OfflineI1Oracle.RevalidationOutcome.CANCEL_NON_DELETION_CONSENT_INVALID, "OFF-I1-RULE-Q-001", OfflineI1Oracle.QueueState.CANCELLED, null, 0),
          rx(RevalidationSource.PENDING, ExpectedJob.NOT_APPLICABLE, RevalidationCause.PROFILE, OfflineI1Oracle.RevalidationOutcome.BLOCK_PROFILE_REVALIDATION_REQUIRED, "OFF-I1-RULE-Q-003", OfflineI1Oracle.QueueState.PENDING_UPLOAD, null, 0),
          rx(RevalidationSource.PENDING, ExpectedJob.NOT_APPLICABLE, RevalidationCause.RUNTIME, OfflineI1Oracle.RevalidationOutcome.BLOCK_RUNTIME_REVALIDATION_REQUIRED, "OFF-I1-RULE-Q-003", OfflineI1Oracle.QueueState.PENDING_UPLOAD, null, 0),
          rx(RevalidationSource.PENDING, ExpectedJob.NOT_APPLICABLE, RevalidationCause.CONNECTIVITY, OfflineI1Oracle.RevalidationOutcome.BLOCK_CONNECTIVITY_DENIED, "OFF-I1-RULE-Q-004", OfflineI1Oracle.QueueState.WAITING_NETWORK, null, 0),
          rx(RevalidationSource.PENDING, ExpectedJob.NOT_APPLICABLE, RevalidationCause.BUDGET, OfflineI1Oracle.RevalidationOutcome.BLOCK_FINITE_BUDGET_EXHAUSTED, "OFF-I1-RULE-Q-018", OfflineI1Oracle.QueueState.FAILED_FINAL, null, 0),
          rx(RevalidationSource.PENDING, ExpectedJob.NOT_APPLICABLE, RevalidationCause.ALLOW, OfflineI1Oracle.RevalidationOutcome.ALLOW, null, OfflineI1Oracle.QueueState.PENDING_UPLOAD, null, 0),
          rx(RevalidationSource.WAITING, ExpectedJob.NOT_APPLICABLE, RevalidationCause.CONSENT, OfflineI1Oracle.RevalidationOutcome.CANCEL_NON_DELETION_CONSENT_INVALID, "OFF-I1-RULE-Q-001", OfflineI1Oracle.QueueState.CANCELLED, null, 0),
          rx(RevalidationSource.WAITING, ExpectedJob.NOT_APPLICABLE, RevalidationCause.PROFILE, OfflineI1Oracle.RevalidationOutcome.BLOCK_PROFILE_REVALIDATION_REQUIRED, "OFF-I1-RULE-Q-003", OfflineI1Oracle.QueueState.PENDING_UPLOAD, null, 0),
          rx(RevalidationSource.WAITING, ExpectedJob.NOT_APPLICABLE, RevalidationCause.RUNTIME, OfflineI1Oracle.RevalidationOutcome.BLOCK_RUNTIME_REVALIDATION_REQUIRED, "OFF-I1-RULE-Q-003", OfflineI1Oracle.QueueState.PENDING_UPLOAD, null, 0),
          rx(RevalidationSource.WAITING, ExpectedJob.NOT_APPLICABLE, RevalidationCause.CONNECTIVITY, OfflineI1Oracle.RevalidationOutcome.BLOCK_CONNECTIVITY_DENIED, null, OfflineI1Oracle.QueueState.WAITING_NETWORK, null, 0),
          rx(RevalidationSource.WAITING, ExpectedJob.NOT_APPLICABLE, RevalidationCause.BUDGET, OfflineI1Oracle.RevalidationOutcome.BLOCK_FINITE_BUDGET_EXHAUSTED, "OFF-I1-RULE-Q-018", OfflineI1Oracle.QueueState.FAILED_FINAL, null, 0),
          rx(RevalidationSource.WAITING, ExpectedJob.NOT_APPLICABLE, RevalidationCause.ALLOW, OfflineI1Oracle.RevalidationOutcome.ALLOW, "OFF-I1-RULE-Q-009", OfflineI1Oracle.QueueState.PENDING_UPLOAD, null, 0),
          rx(RevalidationSource.FAILED, ExpectedJob.BOTH, RevalidationCause.CONSENT, OfflineI1Oracle.RevalidationOutcome.CANCEL_NON_DELETION_CONSENT_INVALID, "OFF-I1-RULE-Q-001", OfflineI1Oracle.QueueState.CANCELLED, null, 0),
          rx(RevalidationSource.FAILED, ExpectedJob.BOTH, RevalidationCause.PROFILE, OfflineI1Oracle.RevalidationOutcome.BLOCK_PROFILE_REVALIDATION_REQUIRED, "OFF-I1-RULE-Q-003", OfflineI1Oracle.QueueState.PENDING_UPLOAD, null, 0),
          rx(RevalidationSource.FAILED, ExpectedJob.BOTH, RevalidationCause.RUNTIME, OfflineI1Oracle.RevalidationOutcome.BLOCK_RUNTIME_REVALIDATION_REQUIRED, "OFF-I1-RULE-Q-003", OfflineI1Oracle.QueueState.PENDING_UPLOAD, null, 0),
          rx(RevalidationSource.FAILED, ExpectedJob.BOTH, RevalidationCause.CONNECTIVITY, OfflineI1Oracle.RevalidationOutcome.BLOCK_CONNECTIVITY_DENIED, "OFF-I1-RULE-Q-004", OfflineI1Oracle.QueueState.WAITING_NETWORK, null, 0),
          rx(RevalidationSource.FAILED, ExpectedJob.BOTH, RevalidationCause.BUDGET, OfflineI1Oracle.RevalidationOutcome.BLOCK_FINITE_BUDGET_EXHAUSTED, "OFF-I1-RULE-Q-018", OfflineI1Oracle.QueueState.FAILED_FINAL, null, 0),
          rx(RevalidationSource.FAILED, ExpectedJob.ABSENT, RevalidationCause.POSITIVE, OfflineI1Oracle.RevalidationOutcome.ALLOW, "OFF-I1-RULE-Q-016", OfflineI1Oracle.QueueState.PENDING_UPLOAD, null, 0),
          rx(RevalidationSource.FAILED, ExpectedJob.PRESENT, RevalidationCause.POSITIVE, OfflineI1Oracle.RevalidationOutcome.ALLOW, "OFF-I1-RULE-Q-017", OfflineI1Oracle.QueueState.REMOTE_PROCESSING, null, 0),
          rx(RevalidationSource.FAILED, ExpectedJob.ABSENT, RevalidationCause.GRANT, OfflineI1Oracle.RevalidationOutcome.ALLOW, "OFF-I1-RULE-Q-016", OfflineI1Oracle.QueueState.PENDING_UPLOAD, null, 1),
          rx(RevalidationSource.FAILED, ExpectedJob.PRESENT, RevalidationCause.GRANT, OfflineI1Oracle.RevalidationOutcome.ALLOW, "OFF-I1-RULE-Q-017", OfflineI1Oracle.QueueState.REMOTE_PROCESSING, null, 1),
          rx(RevalidationSource.REQUIRED_MODEL, ExpectedJob.NOT_APPLICABLE, RevalidationCause.MODEL_MISSING, OfflineI1Oracle.RevalidationOutcome.WAIT_REQUIRED_MODEL, "OFF-I1-RULE-A-009-01", OfflineI1Oracle.QueueState.PENDING_UPLOAD, OfflineI1Oracle.ProcessingState.WAITING_MODEL, 0),
          rx(RevalidationSource.OPTIONAL_CAPABILITY, ExpectedJob.NOT_APPLICABLE, RevalidationCause.MODEL_MISSING, OfflineI1Oracle.RevalidationOutcome.WAIT_OPTIONAL_CAPABILITY, "OFF-I1-RULE-A-021-01", OfflineI1Oracle.QueueState.PENDING_UPLOAD, OfflineI1Oracle.ProcessingState.PENDING_CAPABILITY, 0));

  private static final List<QueueCase> QUEUE_CASES =
      List.of(
          qc(1, FixtureSource.PENDING, FixtureSource.APPLIED, OfflineI1Oracle.QueueSignal.CONSENT_INVALID_OR_REVOKED, OfflineI1Oracle.QueueState.CANCELLED, 0, 0, 0, 0, true, true),
          new QueueCase(2, FixtureSource.PENDING, FixtureSource.LOCAL_ONLY, OfflineI1Oracle.QueueSignal.SAME_KEY_DIFFERENT_CANONICAL_INPUT, OfflineI1Oracle.QueueState.PENDING_UPLOAD, 0, 0, 0, 0, false, false, OfflineI1Oracle.ReducerOutcome.NO_STATE_CHANGE, OfflineI1Oracle.Diagnostic.IDEMPOTENCY_INPUT_MISMATCH),
          qc(3, FixtureSource.WAITING, FixtureSource.APPLIED, OfflineI1Oracle.QueueSignal.PROFILE_OR_RUNTIME_BLOCKED, OfflineI1Oracle.QueueState.PENDING_UPLOAD, 0, 0, 0, 0, true, true),
          qc(4, FixtureSource.FAILED_JOB_POSITIVE, FixtureSource.WAITING, OfflineI1Oracle.QueueSignal.CONNECTIVITY_DENIED, OfflineI1Oracle.QueueState.WAITING_NETWORK, 0, 0, 1, 0, true, true),
          qc(5, FixtureSource.UPLOADING, FixtureSource.PENDING, OfflineI1Oracle.QueueSignal.CONNECTIVITY_LOST, OfflineI1Oracle.QueueState.FAILED_RETRYABLE, 0, 0, 0, 0, true, true),
          qc(6, FixtureSource.PENDING, FixtureSource.APPLIED, OfflineI1Oracle.QueueSignal.VALID_CANCEL_OR_REVOKE, OfflineI1Oracle.QueueState.CANCELLED, 0, 0, 0, 0, true, true),
          qc(7, FixtureSource.LOCAL_ONLY, FixtureSource.PENDING, OfflineI1Oracle.QueueSignal.NEW_LOGICAL_KEY, OfflineI1Oracle.QueueState.PENDING_UPLOAD, 0, 0, 0, 0, true, true),
          qc(8, FixtureSource.PENDING, FixtureSource.WAITING, OfflineI1Oracle.QueueSignal.SCHEDULER_TICK_WHILE_DENIED, OfflineI1Oracle.QueueState.WAITING_NETWORK, 0, 0, 0, 0, true, true),
          qc(9, FixtureSource.WAITING, FixtureSource.PENDING, OfflineI1Oracle.QueueSignal.ALL_REVALIDATION_ALLOW, OfflineI1Oracle.QueueState.PENDING_UPLOAD, 0, 0, 0, 0, true, true),
          qc(10, FixtureSource.PENDING, FixtureSource.WAITING, OfflineI1Oracle.QueueSignal.SEND_AVAILABLE, OfflineI1Oracle.QueueState.UPLOADING, 1, 0, 0, 0, true, true),
          qc(11, FixtureSource.UPLOADING, FixtureSource.REMOTE, OfflineI1Oracle.QueueSignal.FIRST_DURABLE_ACCEPTANCE, OfflineI1Oracle.QueueState.REMOTE_PROCESSING, 0, 0, 1, 0, true, true),
          qc(12, FixtureSource.REMOTE, FixtureSource.UPLOADING, OfflineI1Oracle.QueueSignal.VALID_RESULT, OfflineI1Oracle.QueueState.RESULT_AVAILABLE, 0, 0, 1, 0, true, true),
          qc(13, FixtureSource.RESULT, FixtureSource.REMOTE, OfflineI1Oracle.QueueSignal.USER_TRUTH_ALLOW, OfflineI1Oracle.QueueState.APPLIED, 0, 0, 1, 1, true, true),
          qc(14, FixtureSource.RESULT, FixtureSource.REMOTE, OfflineI1Oracle.QueueSignal.USER_TRUTH_BLOCKS, OfflineI1Oracle.QueueState.CONFLICT, 0, 0, 1, 0, true, true),
          qc(15, FixtureSource.REMOTE, FixtureSource.PENDING, OfflineI1Oracle.QueueSignal.RETRYABLE_ERROR, OfflineI1Oracle.QueueState.FAILED_RETRYABLE, 0, 0, 1, 0, true, true),
          qc(16, FixtureSource.FAILED_NO_JOB_POSITIVE, FixtureSource.APPLIED, OfflineI1Oracle.QueueSignal.RESUME_OR_MANUAL_RETRY, OfflineI1Oracle.QueueState.PENDING_UPLOAD, 0, 0, 0, 0, true, true),
          qc(17, FixtureSource.FAILED_JOB_POSITIVE, FixtureSource.APPLIED, OfflineI1Oracle.QueueSignal.RESUME_OR_MANUAL_RETRY, OfflineI1Oracle.QueueState.REMOTE_PROCESSING, 0, 0, 1, 0, true, true),
          qc(18, FixtureSource.FAILED_JOB_ZERO, FixtureSource.APPLIED, OfflineI1Oracle.QueueSignal.TERMINAL_ERROR, OfflineI1Oracle.QueueState.FAILED_FINAL, 0, 0, 1, 0, true, true),
          qc(19, FixtureSource.REMOTE, FixtureSource.UPLOADING, OfflineI1Oracle.QueueSignal.SAME_INPUT_REPLAY, OfflineI1Oracle.QueueState.REMOTE_PROCESSING, 1, 0, 1, 0, true, true),
          qc(20, FixtureSource.APPLIED, FixtureSource.RESULT, OfflineI1Oracle.QueueSignal.DUPLICATE_TRIGGER, OfflineI1Oracle.QueueState.APPLIED, 0, 0, 1, 1, true, false));

  private OfflineI1OracleTest() {}

  public static void main(String[] args) {
    check(args.length == 0, "T-ARGS");
    testCatalogs();
    testHashKnownAnswers();
    testContractDiagnostics();
    testRequiredNullDiagnostics();
    testExactCatalogRejection();
    testQueueReducer();
    testQueueReplayWitness();
    testReplayLifecycle();
    testAllFixedScenarios();
    testScenario026();
    testRevalidation();
    testSnapshotStrictness();
    testDecoderBoundaries();
    testGraphClassifierPrecedence();
    testOffsetOverflow();
    testImmutability();
    System.out.print("PASS offline-i1-host-oracle");
  }

  private static void testRequiredNullDiagnostics() {
    OfflineI1Oracle.StateVector vector =
        new OfflineI1Oracle.StateVector(
            OfflineI1Oracle.LocalState.LOCAL_READY,
            OfflineI1Oracle.ProcessingState.PROCESSING_NOT_REQUESTED,
            OfflineI1Oracle.ConnectivityState.AVAILABLE,
            OfflineI1Oracle.ModelState.MODEL_NOT_INSTALLED,
            OfflineI1Oracle.QueueState.LOCAL_ONLY);
    for (int field = 0; field < 5; field++) {
      int selected = field;
      expectFault(
          OfflineI1Oracle.Diagnostic.INVALID_014,
          () ->
              new OfflineI1Oracle.StateVector(
                  selected == 0 ? null : vector.local(),
                  selected == 1 ? null : vector.processingCapability(),
                  selected == 2 ? null : vector.connectivity(),
                  selected == 3 ? null : vector.model(),
                  selected == 4 ? null : vector.queue()),
          "T-NULL-STATE-" + field);
    }

    OfflineI1Oracle.QueueRule queueRule = OfflineI1Oracle.queueRules().get(0);
    for (int field = 0; field < 7; field++) {
      int selected = field;
      expectFault(
          selected == 0
              ? OfflineI1Oracle.Diagnostic.INVALID_003
              : OfflineI1Oracle.Diagnostic.INVALID_014,
          () ->
              new OfflineI1Oracle.QueueRule(
                  selected == 0 ? null : queueRule.id(),
                  queueRule.priority(),
                  queueRule.secondaryOrder(),
                  selected == 1 ? null : queueRule.event(),
                  selected == 2 ? null : queueRule.source(),
                  selected == 3 ? null : queueRule.guard(),
                  selected == 4 ? null : queueRule.target(),
                  selected == 5 ? null : queueRule.outcome(),
                  queueRule.queueAffecting(),
                  selected == 6 ? null : queueRule.counterDeltas(),
                  queueRule.flowLedgerAppend(),
                  queueRule.queueLedgerAppend()),
          "T-NULL-QUEUE-RULE-" + field);
    }

    OfflineI1Oracle.ActionSpec action = OfflineI1Oracle.actionCatalog().get(0);
    for (int field = 0; field < 7; field++) {
      int selected = field;
      expectFault(
          selected <= 3
              ? OfflineI1Oracle.Diagnostic.INVALID_003
              : OfflineI1Oracle.Diagnostic.INVALID_014,
          () ->
              new OfflineI1Oracle.ActionSpec(
                  selected == 0 ? null : action.scenarioId(),
                  selected == 1 ? null : action.actionId(),
                  action.ordinal(),
                  selected == 2 ? null : action.eventId(),
                   selected == 3 ? null : action.selectedRuleId(),
                   selected == 4 ? null : action.outcome(),
                   selected == 5 ? null : action.projectionOutcome(),
                   selected == 6 ? null : action.transition(),
                  action.queueAffecting(),
                  action.flowLedgerAppend(),
                  action.queueLedgerAppend(),
                  action.template(),
                  action.priority(),
                  action.secondaryOrder()),
          "T-NULL-ACTION-" + field);
    }

    OfflineI1Oracle.DeletionRow deletion = OfflineI1Oracle.deletionRows().get(0);
    for (int field = 0; field < 3; field++) {
      int selected = field;
      expectFault(
          OfflineI1Oracle.Diagnostic.INVALID_014,
          () ->
              new OfflineI1Oracle.DeletionRow(
                  deletion.ordinal(),
                  selected == 0 ? null : deletion.eventOutcomeClass(),
                  selected == 1 ? null : deletion.queueState(),
                  deletion.deletionSubstatus(),
                  deletion.preserveCurrentSubstatus(),
                  selected == 2 ? null : deletion.deletionIdOutcome(),
                  deletion.error(),
                  deletion.receiptOutcome(),
                  deletion.dispatchAttemptDelta(),
                  deletion.dispatchGrantDelta(),
                  deletion.effectDelta(),
                  deletion.remoteDeletionEffectDelta()),
          "T-NULL-DELETION-ROW-" + field);
    }

    OfflineI1Oracle.QueueRow queue =
        nonDeleteRow(OfflineI1Oracle.QueueState.PENDING_UPLOAD, false, 0);
    for (int field = 0; field < 7; field++) {
      int selected = field;
      expectFault(
          selected >= 5
              ? OfflineI1Oracle.Diagnostic.INVALID_010
              : OfflineI1Oracle.Diagnostic.INVALID_014,
          () ->
              new OfflineI1Oracle.QueueRow(
                  selected == 0 ? null : queue.operationClass(),
                  selected == 1 ? null : queue.intentIdHash(),
                  selected == 2 ? null : queue.logicalKeyHash(),
                  queue.jobIdHash(),
                  queue.resultIdHash(),
                  queue.deletionScopeDigest(),
                  queue.deletionIdHash(),
                  queue.deletionReceiptIdHash(),
                  selected == 3 ? null : queue.queueState(),
                  queue.deletionSubstatus(),
                  queue.contentFreeDeletionErrorCode(),
                  queue.deletionReceiptVerificationOutcome(),
                  queue.attemptCount(),
                  selected == 4 ? null : queue.replayMarker(),
                  queue.effectCount(),
                  queue.applyCount(),
                  queue.remoteDeletionEffectCount(),
                  selected == 5 ? null : queue.preLocalStateDigest(),
                  selected == 6 ? null : queue.postLocalStateDigest()),
          "T-NULL-QUEUE-ROW-" + field);
    }
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_010,
        () ->
            new OfflineI1Oracle.QueueRow(
                queue.operationClass(),
                queue.intentIdHash(),
                queue.logicalKeyHash(),
                queue.jobIdHash(),
                queue.resultIdHash(),
                queue.deletionScopeDigest(),
                queue.deletionIdHash(),
                queue.deletionReceiptIdHash(),
                queue.queueState(),
                queue.deletionSubstatus(),
                queue.contentFreeDeletionErrorCode(),
                queue.deletionReceiptVerificationOutcome(),
                queue.attemptCount(),
                queue.replayMarker(),
                queue.effectCount(),
                queue.applyCount(),
                queue.remoteDeletionEffectCount(),
                "bad",
                queue.postLocalStateDigest()),
        "T-QUEUE-ROW-LOCAL-DIGEST-CATEGORY");
    OfflineI1Oracle.QueueRow deletionQueue =
        OfflineI1Oracle.startDeletionScenario(1, OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE)
            .queueLedger()
            .get(0);
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_011,
        () ->
            new OfflineI1Oracle.QueueRow(
                deletionQueue.operationClass(),
                deletionQueue.intentIdHash(),
                deletionQueue.logicalKeyHash(),
                deletionQueue.jobIdHash(),
                deletionQueue.resultIdHash(),
                "bad",
                deletionQueue.deletionIdHash(),
                deletionQueue.deletionReceiptIdHash(),
                deletionQueue.queueState(),
                deletionQueue.deletionSubstatus(),
                deletionQueue.contentFreeDeletionErrorCode(),
                deletionQueue.deletionReceiptVerificationOutcome(),
                deletionQueue.attemptCount(),
                deletionQueue.replayMarker(),
                deletionQueue.effectCount(),
                deletionQueue.applyCount(),
                deletionQueue.remoteDeletionEffectCount(),
                deletionQueue.preLocalStateDigest(),
                deletionQueue.postLocalStateDigest()),
        "T-QUEUE-ROW-DELETION-HASH-CATEGORY");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_010,
        () ->
            new OfflineI1Oracle.QueueRow(
                deletionQueue.operationClass(),
                deletionQueue.intentIdHash(),
                deletionQueue.logicalKeyHash(),
                deletionQueue.jobIdHash(),
                deletionQueue.resultIdHash(),
                "bad",
                deletionQueue.deletionIdHash(),
                deletionQueue.deletionReceiptIdHash(),
                deletionQueue.queueState(),
                deletionQueue.deletionSubstatus(),
                deletionQueue.contentFreeDeletionErrorCode(),
                deletionQueue.deletionReceiptVerificationOutcome(),
                deletionQueue.attemptCount(),
                deletionQueue.replayMarker(),
                deletionQueue.effectCount(),
                deletionQueue.applyCount(),
                deletionQueue.remoteDeletionEffectCount(),
                "bad",
                deletionQueue.postLocalStateDigest()),
        "T-QUEUE-ROW-STATE-BEFORE-DELETION");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_011,
        () ->
            new OfflineI1Oracle.QueueRow(
                deletionQueue.operationClass(),
                "bad",
                deletionQueue.logicalKeyHash(),
                deletionQueue.jobIdHash(),
                deletionQueue.resultIdHash(),
                deletionQueue.deletionScopeDigest(),
                deletionQueue.deletionIdHash(),
                deletionQueue.deletionReceiptIdHash(),
                null,
                deletionQueue.deletionSubstatus(),
                deletionQueue.contentFreeDeletionErrorCode(),
                deletionQueue.deletionReceiptVerificationOutcome(),
                deletionQueue.attemptCount(),
                deletionQueue.replayMarker(),
                deletionQueue.effectCount(),
                deletionQueue.applyCount(),
                deletionQueue.remoteDeletionEffectCount(),
                deletionQueue.preLocalStateDigest(),
                deletionQueue.postLocalStateDigest()),
        "T-QUEUE-ROW-DELETION-BEFORE-GENERIC");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_011,
        () ->
            new OfflineI1Oracle.QueueRow(
                deletionQueue.operationClass(),
                deletionQueue.intentIdHash(),
                deletionQueue.logicalKeyHash(),
                deletionQueue.jobIdHash(),
                deletionQueue.resultIdHash(),
                deletionQueue.deletionScopeDigest(),
                deletionQueue.deletionIdHash(),
                deletionQueue.deletionReceiptIdHash(),
                deletionQueue.queueState(),
                deletionQueue.deletionSubstatus(),
                deletionQueue.contentFreeDeletionErrorCode(),
                deletionQueue.deletionReceiptVerificationOutcome(),
                deletionQueue.attemptCount(),
                deletionQueue.replayMarker(),
                2,
                deletionQueue.applyCount(),
                2,
                deletionQueue.preLocalStateDigest(),
                deletionQueue.postLocalStateDigest()),
        "T-QUEUE-ROW-DELETION-COUNTER-CATEGORY");

    OfflineI1Oracle.Step step =
        OfflineI1Oracle.execute(OfflineI1Oracle.startScenario("OFF-SYN-001"));
    OfflineI1Oracle.FlowRow flow = step.result().typedLedgerDeltas().flowAppend();
    for (int field = 0; field < 5; field++) {
      int selected = field;
      expectFault(
          selected < 2
              ? OfflineI1Oracle.Diagnostic.INVALID_003
              : OfflineI1Oracle.Diagnostic.INVALID_014,
          () ->
              new OfflineI1Oracle.FlowRow(
                  flow.sequence(),
                  selected == 0 ? null : flow.scenarioId(),
                  selected == 1 ? null : flow.actionId(),
                  selected == 2 ? null : flow.preStateVector(),
                  selected == 3 ? null : flow.postStateVector(),
                  selected == 4 ? null : flow.outcome(),
                  flow.monotonicOffsetMs(),
                  flow.preStateDigest(),
                  flow.postStateDigest(),
                  flow.processingRequestIdHash(),
                  flow.queueIntentIdHash(),
                  flow.contentFreeErrorCode()),
          "T-NULL-FLOW-ROW-" + field);
    }
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_010,
        () ->
            new OfflineI1Oracle.FlowRow(
                flow.sequence(),
                flow.scenarioId(),
                flow.actionId(),
                flow.preStateVector(),
                flow.postStateVector(),
                flow.outcome(),
                flow.monotonicOffsetMs(),
                "bad",
                flow.postStateDigest(),
                flow.processingRequestIdHash(),
                flow.queueIntentIdHash(),
                flow.contentFreeErrorCode()),
        "T-FLOW-ROW-STATE-DIGEST-CATEGORY");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_010,
        () ->
            new OfflineI1Oracle.FlowRow(
                0,
                flow.scenarioId(),
                flow.actionId(),
                flow.preStateVector(),
                flow.postStateVector(),
                flow.outcome(),
                flow.monotonicOffsetMs(),
                "bad",
                flow.postStateDigest(),
                flow.processingRequestIdHash(),
                flow.queueIntentIdHash(),
                flow.contentFreeErrorCode()),
        "T-FLOW-ROW-STATE-BEFORE-GENERIC");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_014,
        () -> new OfflineI1Oracle.TypedLedgerDeltas(flow, null, null),
        "T-NULL-TYPED-DELTAS");

    OfflineI1Oracle.Result result = step.result();
    for (int field = 0; field < 3; field++) {
      int selected = field;
      expectFault(
          OfflineI1Oracle.Diagnostic.INVALID_014,
          () ->
              new OfflineI1Oracle.Result(
                  result.selectedRuleId(),
                  selected == 0 ? null : result.outcome(),
                  selected == 1 ? null : result.postStateVector(),
                  selected == 2 ? null : result.typedLedgerDeltas(),
                  result.effectCount(),
                  result.applyCount(),
                  result.remoteDeletionEffectCount()),
          "T-NULL-RESULT-" + field);
    }
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_003,
        () ->
            new OfflineI1Oracle.Result(
                "OFF-I1-RULE-Q-999",
                null,
                null,
                null,
                2,
                2,
                2),
        "T-RESULT-RULE-BEFORE-GENERIC");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_003,
        () ->
            new OfflineI1Oracle.Result(
                "OFF-I1-RULE-Q-999",
                result.outcome(),
                result.postStateVector(),
                result.typedLedgerDeltas(),
                result.effectCount(),
                result.applyCount(),
                result.remoteDeletionEffectCount()),
        "T-RESULT-RULE-BEFORE-RELATIONSHIP");

    OfflineI1Oracle.ReplayRecord replay = queueFixture(FixtureSource.PENDING).replay();
    for (int field = 0; field < 6; field++) {
      int selected = field;
      expectFault(
          selected == 2
              ? OfflineI1Oracle.Diagnostic.INVALID_003
              : selected == 1 || selected == 4
                  ? OfflineI1Oracle.Diagnostic.INVALID_010
                  : OfflineI1Oracle.Diagnostic.INVALID_014,
          () ->
              new OfflineI1Oracle.ReplayRecord(
                  selected == 0 ? null : replay.logicalKeyHash(),
                  selected == 1 ? null : replay.canonicalInputDigest(),
                  selected == 2 ? null : replay.selectedRuleId(),
                  selected == 3 ? null : replay.outcome(),
                  selected == 4 ? null : replay.postStateDigest(),
                  replay.resultIdHash(),
                  selected == 5 ? null : replay.replayMarker(),
                  replay.attemptCount(),
                  replay.effectCount(),
                  replay.applyCount(),
                  replay.remoteDeletionEffectCount()),
          "T-NULL-REPLAY-" + field);
    }
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_010,
        () ->
            new OfflineI1Oracle.ReplayRecord(
                replay.logicalKeyHash(),
                replay.canonicalInputDigest(),
                replay.selectedRuleId(),
                replay.outcome(),
                "bad",
                replay.resultIdHash(),
                replay.replayMarker(),
                replay.attemptCount(),
                replay.effectCount(),
                replay.applyCount(),
                replay.remoteDeletionEffectCount()),
        "T-REPLAY-STATE-DIGEST-CATEGORY");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_010,
        () ->
            new OfflineI1Oracle.ReplayRecord(
                "bad",
                "bad",
                replay.selectedRuleId(),
                replay.outcome(),
                replay.postStateDigest(),
                replay.resultIdHash(),
                replay.replayMarker(),
                replay.attemptCount(),
                replay.effectCount(),
                replay.applyCount(),
                replay.remoteDeletionEffectCount()),
        "T-REPLAY-CANONICAL-DIGEST-BEFORE-GENERIC");

    OfflineI1Oracle.RunState run = OfflineI1Oracle.startScenario("OFF-SYN-001");
    for (int field = 0; field < 6; field++) {
      int selected = field;
      expectFault(
          selected == 0
              ? OfflineI1Oracle.Diagnostic.INVALID_003
              : OfflineI1Oracle.Diagnostic.INVALID_014,
          () ->
              new OfflineI1Oracle.RunState(
                  selected == 0 ? null : run.scenarioId(),
                  run.nextActionOrdinal(),
                  run.monotonicOffsetMs(),
                  selected == 1 ? null : run.budget(),
                  selected == 2 ? null : run.stateVector(),
                  selected == 3 ? null : run.flowLedger(),
                  selected == 4 ? null : run.queueLedger(),
                  selected == 5 ? null : run.replayRecords(),
                  run.lastResult()),
          "T-NULL-RUN-" + field);
    }
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_014,
        () -> new OfflineI1Oracle.Step(null, result, null),
        "T-NULL-STEP-RUN");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_014,
        () -> new OfflineI1Oracle.Step(run, null, null),
        "T-NULL-STEP-RESULT");

    OfflineI1Oracle.RevalidationInput revalidation =
        new OfflineI1Oracle.RevalidationInput(
            queue,
            OfflineI1Oracle.Consent.CURRENT,
            OfflineI1Oracle.Profile.CURRENT,
            OfflineI1Oracle.ProcessingRequirement.NONE,
            OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED,
            OfflineI1Oracle.ConnectivityState.AVAILABLE,
            new OfflineI1Oracle.AttemptBudget(0, 0),
            OfflineI1Oracle.ExplicitGrant.NONE,
            null);
    for (int field = 0; field < 8; field++) {
      int selected = field;
      expectFault(
          OfflineI1Oracle.Diagnostic.INVALID_014,
          () ->
              new OfflineI1Oracle.RevalidationInput(
                  selected == 0 ? null : revalidation.row(),
                  selected == 1 ? null : revalidation.consent(),
                  selected == 2 ? null : revalidation.profile(),
                  selected == 3 ? null : revalidation.processingRequirement(),
                  selected == 4 ? null : revalidation.runtime(),
                  selected == 5 ? null : revalidation.connectivity(),
                  selected == 6 ? null : revalidation.budget(),
                  selected == 7 ? null : revalidation.explicitGrant(),
                  null),
          "T-NULL-REVALIDATION-INPUT-" + field);
    }
    OfflineI1Oracle.RevalidationDecision decision = OfflineI1Oracle.revalidate(revalidation);
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_014,
        () ->
            new OfflineI1Oracle.RevalidationDecision(
                null,
                decision.selectedRuleId(),
                decision.queueTarget(),
                decision.processingTarget(),
                decision.manualGrantDelta(),
                decision.diagnostic()),
        "T-NULL-REVALIDATION-OUTCOME");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_014,
        () ->
            new OfflineI1Oracle.RevalidationDecision(
                decision.outcome(),
                decision.selectedRuleId(),
                decision.queueTarget(),
                decision.processingTarget(),
                decision.manualGrantDelta(),
                null),
        "T-NULL-REVALIDATION-DIAGNOSTIC");

    QueueFixture fixture = queueFixture(FixtureSource.PENDING);
    OfflineI1Oracle.QueueReducerInput reducerInput =
        queueInput(
            fixture,
            Set.of(OfflineI1Oracle.QueueSignal.CONNECTIVITY_DENIED),
            OfflineI1Oracle.ExplicitGrant.NONE);
    for (int field = 0; field < 5; field++) {
      int selected = field;
      expectFault(
          selected == 0
              ? OfflineI1Oracle.Diagnostic.INVALID_003
              : OfflineI1Oracle.Diagnostic.INVALID_014,
          () ->
              new OfflineI1Oracle.QueueReducerInput(
                  selected == 0 ? null : reducerInput.scenarioId(),
                  selected == 1 ? null : reducerInput.preStateVector(),
                  selected == 2 ? null : reducerInput.budget(),
                  reducerInput.currentRow(),
                  reducerInput.cachedReplayRecord(),
                  reducerInput.cachedReplayPostStateVector(),
                  selected == 3 ? null : reducerInput.signals(),
                  selected == 4 ? null : reducerInput.explicitGrant()),
          "T-NULL-REDUCER-INPUT-" + field);
    }
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_014,
        () -> {
          Set<OfflineI1Oracle.QueueSignal> withNull = new HashSet<>();
          withNull.add(null);
          new OfflineI1Oracle.QueueReducerInput(
              reducerInput.scenarioId(),
              reducerInput.preStateVector(),
              reducerInput.budget(),
              reducerInput.currentRow(),
              reducerInput.cachedReplayRecord(),
              reducerInput.cachedReplayPostStateVector(),
              withNull,
              reducerInput.explicitGrant());
        },
        "T-NULL-REDUCER-SIGNAL");
    OfflineI1Oracle.QueueReduction reduction = OfflineI1Oracle.reduceQueue(reducerInput);
    for (int field = 0; field < 4; field++) {
      int selected = field;
      expectFault(
          OfflineI1Oracle.Diagnostic.INVALID_014,
          () ->
              new OfflineI1Oracle.QueueReduction(
                  reduction.selectedRuleId(),
                  selected == 0 ? null : reduction.outcome(),
                  selected == 1 ? null : reduction.diagnostic(),
                  selected == 2 ? null : reduction.postStateVector(),
                  selected == 3 ? null : reduction.budget(),
                  reduction.queueAppend(),
                  reduction.replayRecord(),
                  reduction.replayPostStateVector(),
                  reduction.flowLedgerAppend(),
                  reduction.queueLedgerAppend()),
          "T-NULL-REDUCTION-" + field);
    }

    List<Checked> nullMethods =
        List.of(
            () -> OfflineI1Oracle.validateContract(null),
            () -> OfflineI1Oracle.logicalKeyHash(null, "opaque"),
            () ->
                OfflineI1Oracle.replayInputDigest(
                    null,
                    replay.logicalKeyHash(),
                    OfflineI1Oracle.InputVariant.PRIMARY,
                    null),
            () ->
                OfflineI1Oracle.replayInputDigest(
                    OfflineI1Oracle.OperationClass.NON_DELETION_CLOUD_INTENT,
                    replay.logicalKeyHash(),
                    null,
                    null),
            () -> OfflineI1Oracle.localStateDigest(null),
            () -> OfflineI1Oracle.encodeStateVector(null),
            () -> OfflineI1Oracle.revalidate(null),
            () -> OfflineI1Oracle.deriveDeletionPhase(null),
            () -> OfflineI1Oracle.execute((OfflineI1Oracle.RunState) null),
            () -> OfflineI1Oracle.encodeSnapshot(null));
    for (int index = 0; index < nullMethods.size(); index++) {
      expectFault(
          OfflineI1Oracle.Diagnostic.INVALID_014,
          nullMethods.get(index),
          "T-NULL-PUBLIC-METHOD-" + index);
    }
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_011,
        () -> OfflineI1Oracle.startDeletionScenario(1, null),
        "T-NULL-DELETION-PHASE");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_011,
        () -> OfflineI1Oracle.startDeletionScenario(16, null),
        "T-NULL-DELETION-PHASE-BEFORE-ORDINAL");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_003,
        () -> OfflineI1Oracle.startScenario(null),
        "T-NULL-SCENARIO-ID");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_014,
        () -> OfflineI1Oracle.startScenario("OFF-SYN-026"),
        "T-026-DIRECT-START-REQUIRES-DIRECTIVE");
  }

  private static void testCatalogs() {
    check(OfflineI1Oracle.MAX_SNAPSHOT_BYTES == 1_048_576, "T-CAT-ENVELOPE-BYTES");
    check(OfflineI1Oracle.MAX_JSON_DEPTH == 32, "T-CAT-ENVELOPE-DEPTH");
    check(OfflineI1Oracle.Diagnostic.values().length == 20, "T-CAT-DIAGNOSTIC-COUNT");
    List<String> exactDiagnostics =
        List.of(
            "NONE",
            "NO_ELIGIBLE_RULE",
            "IDEMPOTENCY_INPUT_MISMATCH",
            "POLICY_REJECTED",
            "OFF-I1-INVALID-001",
            "OFF-I1-INVALID-002",
            "OFF-I1-INVALID-003",
            "OFF-I1-INVALID-004",
            "OFF-I1-INVALID-005",
            "OFF-I1-INVALID-006",
            "OFF-I1-INVALID-007",
            "OFF-I1-INVALID-008",
            "OFF-I1-INVALID-009",
            "OFF-I1-INVALID-010",
            "OFF-I1-INVALID-011",
            "OFF-I1-INVALID-012",
            "OFF-I1-INVALID-013",
            "OFF-I1-INVALID-014",
            "OFF-I1-INVALID-015",
            "OFF-I1-INVALID-016");
    for (int index = 0; index < exactDiagnostics.size(); index++) {
      equal(
          OfflineI1Oracle.Diagnostic.values()[index].code(),
          exactDiagnostics.get(index),
          "T-CAT-DIAGNOSTIC-ORDER-" + index);
    }
    List<OfflineI1Oracle.Diagnostic> semanticClassifier =
        List.of(
            OfflineI1Oracle.Diagnostic.INVALID_013,
            OfflineI1Oracle.Diagnostic.INVALID_001,
            OfflineI1Oracle.Diagnostic.INVALID_002,
            OfflineI1Oracle.Diagnostic.INVALID_003,
            OfflineI1Oracle.Diagnostic.INVALID_004,
            OfflineI1Oracle.Diagnostic.INVALID_005,
            OfflineI1Oracle.Diagnostic.INVALID_006,
            OfflineI1Oracle.Diagnostic.INVALID_007,
            OfflineI1Oracle.Diagnostic.INVALID_008,
            OfflineI1Oracle.Diagnostic.INVALID_009,
            OfflineI1Oracle.Diagnostic.INVALID_010,
            OfflineI1Oracle.Diagnostic.INVALID_011,
            OfflineI1Oracle.Diagnostic.INVALID_012,
            OfflineI1Oracle.Diagnostic.INVALID_014,
            OfflineI1Oracle.Diagnostic.INVALID_015);
    check(semanticClassifier.size() == 15, "T-CAT-SEMANTIC-CLASSIFIER-COUNT");
    check(
        new HashSet<>(semanticClassifier).size() == 15,
        "T-CAT-SEMANTIC-CLASSIFIER-UNIQUE");
    check(
        !semanticClassifier.contains(OfflineI1Oracle.Diagnostic.INVALID_016),
        "T-CAT-SEMANTIC-CLASSIFIER-EXCLUDES-016");
    Set<OfflineI1Oracle.Diagnostic> resourceEnvelope =
        Set.of(OfflineI1Oracle.Diagnostic.INVALID_016);
    check(resourceEnvelope.size() == 1, "T-CAT-RESOURCE-ENVELOPE-COUNT");
    List<OfflineI1Oracle.QueueRule> queueRules = OfflineI1Oracle.queueRules();
    check(queueRules.size() == 20, "T-CAT-Q-COUNT");
    Set<String> queueIds = new HashSet<>();
    for (int index = 0; index < queueRules.size(); index++) {
      OfflineI1Oracle.QueueRule rule = queueRules.get(index);
      String[] expected = Q_EXPECTED[index].split("\\|", -1);
      equal(rule.id(), expected[0], "T-CAT-Q-ID-" + index);
      check(rule.priority() == Integer.parseInt(expected[1]), "T-CAT-Q-PRIORITY-" + index);
      check(rule.secondaryOrder() == 0, "T-CAT-Q-SECONDARY-" + index);
      equal(rule.event(), expected[2], "T-CAT-Q-EVENT-" + index);
      equal(rule.source(), expected[3], "T-CAT-Q-SOURCE-" + index);
      equal(rule.guard(), expected[4], "T-CAT-Q-GUARD-" + index);
      equal(rule.target(), expected[5], "T-CAT-Q-TARGET-" + index);
      equal(rule.outcome().name(), expected[6], "T-CAT-Q-OUTCOME-" + index);
      check(rule.queueAffecting() == Boolean.parseBoolean(expected[7]), "T-CAT-Q-AFFECTING-" + index);
      equal(rule.counterDeltas(), expected[8], "T-CAT-Q-COUNTERS-" + index);
      check(rule.flowLedgerAppend() == Boolean.parseBoolean(expected[9]), "T-CAT-Q-FLOW-" + index);
      check(rule.queueLedgerAppend() == Boolean.parseBoolean(expected[10]), "T-CAT-Q-QUEUE-" + index);
      check(queueIds.add(rule.id()), "T-CAT-Q-UNIQUE-" + index);
    }

    Set<String> general = OfflineI1Oracle.generalTransitionCatalog();
    Set<String> templates = OfflineI1Oracle.packetTemplateCatalog();
    Set<String> runtime = OfflineI1Oracle.runtimeTransitionCatalog();
    check(general.size() == 25, "T-CAT-GENERAL");
    check(templates.size() == 4, "T-CAT-TEMPLATE");
    check(runtime.size() == 41, "T-CAT-RUNTIME");
    check(general.contains("APPLY_EXACT_INHERITED_ROW"), "T-CAT-TEMPLATE-GENERAL-1");
    check(general.contains("Q:PRESERVE_DELETE_PENDING;ATTEMPT:RESOLVE_BY_SUBCASE"), "T-CAT-TEMPLATE-GENERAL-2");
    for (String template : templates) {
      check(!runtime.contains(template), "T-CAT-NO-RUNTIME-TEMPLATE-" + safeOrdinal(template));
    }
    check(runtime.contains("PRESERVE_DELETE_PENDING_ATTEMPT_0"), "T-CAT-DISPATCH-0");
    check(runtime.contains("PRESERVE_DELETE_PENDING_ATTEMPT_1"), "T-CAT-DISPATCH-1");
    check(runtime.contains("PRESERVE_DELETE_PENDING_ATTEMPT_1_AFTER_EXACT_GRANT"), "T-CAT-DISPATCH-G");
    for (int row = 1; row <= 15; row++) {
      check(runtime.contains("APPLY_INHERITED_DELETION_ROW_" + three(row)), "T-CAT-APPLY-" + row);
    }

    List<OfflineI1Oracle.ActionSpec> actions = OfflineI1Oracle.actionCatalog();
    check(actions.size() == 67, "T-CAT-ACTION-COUNT");
    int global = 0;
    int direct = 0;
    int nonDirect = 0;
    Set<String> actionIds = new HashSet<>();
    Set<String> eventIds = new HashSet<>();
    for (int scenario = 1; scenario <= 26; scenario++) {
      String scenarioId = "OFF-SYN-" + three(scenario);
      List<OfflineI1Oracle.ActionSpec> selected =
          actions.stream()
              .filter(a -> a.scenarioId().equals(scenarioId))
              .toList();
      check(selected.size() == SCENARIO_COUNTS[scenario - 1], "T-CAT-SCENARIO-COUNT-" + scenario);
      for (int ordinal = 1; ordinal <= selected.size(); ordinal++) {
        OfflineI1Oracle.ActionSpec action = selected.get(ordinal - 1);
        global++;
        String suffix = three(scenario) + "-" + two(ordinal);
        check(action.actionId().equals("OFF-I1-ACT-" + suffix), "T-CAT-ACTION-ID-" + global);
        check(action.eventId().equals("OFF-I1-EVT-" + suffix), "T-CAT-EVENT-ID-" + global);
        check(action.ordinal() == ordinal, "T-CAT-ORDINAL-" + global);
        check(actionIds.add(action.actionId()), "T-CAT-ACTION-UNIQUE-" + global);
        check(eventIds.add(action.eventId()), "T-CAT-EVENT-UNIQUE-" + global);
        equal(action.transition(), SCENARIO_TRANSITIONS[scenario - 1][ordinal - 1], "T-CAT-TRANSITION-" + global);
        equal(action.selectedRuleId(), expectedActionRule(scenario, ordinal), "T-CAT-RULE-" + global);
        check(action.outcome() == expectedActionOutcome(scenario, ordinal), "T-CAT-OUTCOME-" + global);
        equal(
            action.projectionOutcome(),
            scenario == 26 && ordinal == 2
                ? "RESOLVE_BY_SUBCASE"
                : expectedActionOutcome(scenario, ordinal).name(),
            "T-CAT-PROJECTION-OUTCOME-" + global);
        check(action.queueAffecting() == expectedActionQueue(scenario, ordinal), "T-CAT-QUEUE-AFFECTING-" + global);
        check(action.queueLedgerAppend() == expectedActionQueue(scenario, ordinal), "T-CAT-QUEUE-APPEND-" + global);
        check(action.flowLedgerAppend(), "T-CAT-FLOW-APPEND-" + global);
        if (action.selectedRuleId().startsWith("OFF-I1-RULE-A-")) {
          direct++;
          check(action.priority() == 1000, "T-CAT-A-PRIORITY-" + global);
          check(action.secondaryOrder() == global, "T-CAT-A-SECONDARY-" + global);
          check(!action.queueAffecting(), "T-CAT-A-QUEUE-" + global);
          check(!action.queueLedgerAppend(), "T-CAT-A-QAPPEND-" + global);
        } else {
          nonDirect++;
          check(action.priority() == expectedActionPriority(scenario, ordinal), "T-CAT-NONDIRECT-PRIORITY-" + global);
          check(action.secondaryOrder() == expectedActionSecondary(scenario, ordinal), "T-CAT-NONDIRECT-SECONDARY-" + global);
        }
        check(action.flowLedgerAppend(), "T-CAT-FLOW-" + global);
      }
    }
    check(global == 67, "T-CAT-GLOBAL");
    check(direct == 52, "T-CAT-DIRECT");
    check(nonDirect == 15, "T-CAT-NONDIRECT");
    check(actions.get(65).template() && actions.get(66).template(), "T-CAT-026-TEMPLATES");

    List<OfflineI1Oracle.DeletionRow> deletionRows = OfflineI1Oracle.deletionRows();
    check(deletionRows.size() == 15, "T-CAT-D-COUNT");
    for (int row = 1; row <= deletionRows.size(); row++) {
      OfflineI1Oracle.DeletionRow value = deletionRows.get(row - 1);
      DeletionExpected expected = D_EXPECTED.get(row - 1);
      check(value.ordinal() == expected.ordinal, "T-CAT-D-ORDINAL-" + row);
      equal(value.eventOutcomeClass(), expected.event, "T-CAT-D-EVENT-" + row);
      check(value.queueState() == expected.queueState, "T-CAT-D-STATE-" + row);
      check(value.deletionSubstatus() == expected.substatus, "T-CAT-D-SUBSTATUS-" + row);
      check(value.preserveCurrentSubstatus() == expected.preserveSubstatus, "T-CAT-D-PRESERVE-" + row);
      equal(value.deletionIdOutcome(), expected.deletionIdOutcome, "T-CAT-D-ID-OUTCOME-" + row);
      check(value.error() == expected.error, "T-CAT-D-ERROR-" + row);
      check(value.receiptOutcome() == expected.receipt, "T-CAT-D-RECEIPT-" + row);
      check(value.dispatchAttemptDelta() == expected.dispatchAttemptDelta, "T-CAT-D-ATTEMPT-" + row);
      check(value.dispatchGrantDelta() == expected.dispatchGrantDelta, "T-CAT-D-GRANT-" + row);
      check(value.effectDelta() == expected.effectDelta, "T-CAT-D-EFFECT-" + row);
      check(value.remoteDeletionEffectDelta() == expected.remoteDeletionEffectDelta, "T-CAT-D-REMOTE-EFFECT-" + row);
    }
  }

  private static void testHashKnownAnswers() {
    String logical =
        OfflineI1Oracle.logicalKeyHash(
            OfflineI1Oracle.OperationClass.NON_DELETION_CLOUD_INTENT, "logical.001");
    equal(
        logical,
        "9f7a4d73702581383d650ff013b83ee84ee8db3f2f15dfc4901cb88944a5a0ba",
        "T-HASH-LOGICAL");
    String job = OfflineI1Oracle.jobIdHash(logical, "job.001");
    equal(job, "de1c1647384a80f62d2b3c35a242a00fc91b07c18c7f5ab9a9d351a85578c135", "T-HASH-JOB");
    String result = OfflineI1Oracle.resultIdHash(job, "result.001");
    equal(result, "81b67b028ce071c84703f3d3ffb99d4aaea99471144258a0a4f78f3314c99280", "T-HASH-RESULT");
    String scope = OfflineI1Oracle.deletionScopeDigest("scope.001");
    equal(scope, "636898965db7b0c38c4a0954be3ddc52c70dbdedde84e4634d6d2a6b10af681e", "T-HASH-SCOPE");
    String deleteLogical =
        OfflineI1Oracle.logicalKeyHash(
            OfflineI1Oracle.OperationClass.DELETE_CLOUD_COPY, "delete.logical.001");
    equal(deleteLogical, "aecce292a718eabbb27f92185f371c30d75444a7ccbe538fcaa821d93c70bd18", "T-HASH-DELETE-LOGICAL");
    String deletion = OfflineI1Oracle.deletionIdHash(deleteLogical, scope, "deletion.001");
    equal(deletion, "8fe9caa08eabdaf80dafb0bc4219ad2015f72dc6e10b1bf42852f36c93b53f15", "T-HASH-DELETION");
    equal(
        OfflineI1Oracle.deletionReceiptIdHash(deletion, scope, "receipt.001"),
        "81fc792b61cfe6a409ed9a4a26f54041d1ed3faf445df17d6063690260e44479",
        "T-HASH-RECEIPT");
    equal(
        OfflineI1Oracle.replayInputDigest(
            OfflineI1Oracle.OperationClass.NON_DELETION_CLOUD_INTENT,
            logical,
            OfflineI1Oracle.InputVariant.PRIMARY,
            null),
        "9635e4b7a36f333e8197f691603c0648a9157c2bf6f81ac76a8edebfdbc7fe33",
        "T-HASH-REPLAY-NONDELETE");
    equal(
        OfflineI1Oracle.replayInputDigest(
            OfflineI1Oracle.OperationClass.DELETE_CLOUD_COPY,
            deleteLogical,
            OfflineI1Oracle.InputVariant.PRIMARY,
            scope),
        "a527f0c6cc3dd6b3b4563c1e7e4ddce834ad269535341517a78dad931f4b12f5",
        "T-HASH-REPLAY-DELETE");
    equal(
        OfflineI1Oracle.localStateDigest(OfflineI1Oracle.LocalState.FRESH_LOCAL_DEFAULT),
        "cde8b74c30cd0dc58e72305f936b8bbaf12e5b53f4245608338712c65d99ca85",
        "T-HASH-LOCAL");
    OfflineI1Oracle.StateVector fresh =
        new OfflineI1Oracle.StateVector(
            OfflineI1Oracle.LocalState.FRESH_LOCAL_DEFAULT,
            OfflineI1Oracle.ProcessingState.PROCESSING_NOT_REQUESTED,
            OfflineI1Oracle.ConnectivityState.NETWORK_DENIED,
            OfflineI1Oracle.ModelState.MODEL_NOT_INSTALLED,
            OfflineI1Oracle.QueueState.LOCAL_ONLY);
    byte[] vectorBytes = OfflineI1Oracle.encodeStateVector(fresh);
    check(vectorBytes.length == 164, "T-HASH-VECTOR-BYTES");
    equal(
        OfflineI1Oracle.stateVectorDigest(fresh),
        "affa8cd4027ffe64431b38a45747b838a9461c7e6470cc890dd48d71828ba4e6",
        "T-HASH-VECTOR");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_014,
        () -> OfflineI1Oracle.logicalKeyHash(OfflineI1Oracle.OperationClass.DELETE_CLOUD_COPY, "~"),
        "T-HASH-RESERVED");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_015,
        () ->
            OfflineI1Oracle.replayInputDigest(
                OfflineI1Oracle.OperationClass.NON_DELETION_CLOUD_INTENT,
                logical,
                OfflineI1Oracle.InputVariant.PRIMARY,
                scope),
        "T-HASH-NULL-LIFECYCLE");
  }

  private static void testContractDiagnostics() {
    check(
        OfflineI1Oracle.validateContract(probe(0)) == OfflineI1Oracle.Diagnostic.NONE,
        "T-DIAG-NONE");
    OfflineI1Oracle.Diagnostic[] expected = {
      OfflineI1Oracle.Diagnostic.INVALID_001,
      OfflineI1Oracle.Diagnostic.INVALID_002,
      OfflineI1Oracle.Diagnostic.INVALID_003,
      OfflineI1Oracle.Diagnostic.INVALID_004,
      OfflineI1Oracle.Diagnostic.INVALID_005,
      OfflineI1Oracle.Diagnostic.INVALID_006,
      OfflineI1Oracle.Diagnostic.INVALID_007,
      OfflineI1Oracle.Diagnostic.INVALID_008,
      OfflineI1Oracle.Diagnostic.INVALID_009,
      OfflineI1Oracle.Diagnostic.INVALID_010,
      OfflineI1Oracle.Diagnostic.INVALID_011,
      OfflineI1Oracle.Diagnostic.INVALID_012
    };
    for (int index = 1; index <= expected.length; index++) {
      check(
          OfflineI1Oracle.validateContract(probe(index)) == expected[index - 1],
          "T-DIAG-" + index);
    }
    check(
        OfflineI1Oracle.validateContract(
                new OfflineI1Oracle.ContractProbe(
                    false, false, false, false, false, false, false, false, false, false, false, false))
            == OfflineI1Oracle.Diagnostic.INVALID_001,
        "T-DIAG-FIRST");
  }

  private static OfflineI1Oracle.ContractProbe probe(int failedOrdinal) {
    boolean[] values = new boolean[12];
    Arrays.fill(values, true);
    if (failedOrdinal > 0) values[failedOrdinal - 1] = false;
    return new OfflineI1Oracle.ContractProbe(
        values[0], values[1], values[2], values[3], values[4], values[5], values[6],
        values[7], values[8], values[9], values[10], values[11]);
  }

  private static void testExactCatalogRejection() {
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_003,
        () -> OfflineI1Oracle.startScenario("OFF-SYN-027"),
        "T-CATALOG-SCENARIO-027");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_003,
        () ->
            new OfflineI1Oracle.ActionSpec(
                "OFF-SYN-001",
                "OFF-I1-ACT-001-03",
                3,
                "OFF-I1-EVT-001-03",
                "OFF-I1-RULE-A-001-03",
                OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED,
                OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED.name(),
                "=",
                false,
                true,
                false,
                false,
                1000,
                1),
        "T-CATALOG-ACTION-OUTSIDE-SCENARIO");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_003,
        () ->
            new OfflineI1Oracle.QueueRule(
                "OFF-I1-RULE-Q-021",
                1,
                0,
                "event",
                "source",
                "guard",
                "target",
                OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED,
                true,
                "0,0,0,0",
                true,
                true),
        "T-CATALOG-Q021");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_003,
        () ->
            new OfflineI1Oracle.QueueRule(
                "OFF-I1-RULE-A-013-01",
                1,
                0,
                "event",
                "source",
                "guard",
                "target",
                OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED,
                true,
                "0,0,0,0",
                true,
                true),
        "T-CATALOG-NONEXISTENT-A-RULE");
    QueueFixture applied = queueFixture(FixtureSource.APPLIED);
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_003,
        () ->
            new OfflineI1Oracle.ReplayRecord(
                applied.replay.logicalKeyHash(),
                applied.replay.canonicalInputDigest(),
                "OFF-I1-RULE-D-016",
                applied.replay.outcome(),
                applied.replay.postStateDigest(),
                applied.replay.resultIdHash(),
                applied.replay.replayMarker(),
                applied.replay.attemptCount(),
                applied.replay.effectCount(),
                applied.replay.applyCount(),
                applied.replay.remoteDeletionEffectCount()),
        "T-CATALOG-D016");
  }

  private static void testQueueReducer() {
    check(QUEUE_CASES.size() == 20, "T-Q-CASE-COUNT");
    int positiveCases = 0;
    int sourceNegativeCases = 0;
    int guardNegativeCases = 0;
    for (QueueCase expected : QUEUE_CASES) {
      String id = "T-Q-" + three(expected.ordinal);
      QueueFixture positive = queueFixture(expected.positive);
      OfflineI1Oracle.QueueReduction actual =
          OfflineI1Oracle.reduceQueue(
              queueInput(positive, Set.of(expected.signal), OfflineI1Oracle.ExplicitGrant.NONE));
      equal(actual.selectedRuleId(), "OFF-I1-RULE-Q-" + three(expected.ordinal), id + "-RULE");
      check(actual.outcome() == expected.outcome, id + "-OUTCOME");
      check(actual.diagnostic() == expected.diagnostic, id + "-DIAGNOSTIC");
      check(actual.postStateVector().queue() == expected.target, id + "-TARGET");
      check(
          actual.budget().attemptCount()
              == positive.budget.attemptCount() + expected.attemptDelta,
          id + "-ATTEMPT");
      check(
          actual.budget().manualRetryGrantCount()
              == positive.budget.manualRetryGrantCount() + expected.grantDelta,
          id + "-GRANT");
      check(actual.flowLedgerAppend() == expected.flowAppend, id + "-FLOW-FLAG");
      check(actual.queueLedgerAppend() == expected.queueAppend, id + "-QUEUE-FLAG");
      check((actual.queueAppend() != null) == expected.queueAppend, id + "-QUEUE-PRESENCE");
      if (actual.queueAppend() != null) {
        check(actual.queueAppend().queueState() == expected.target, id + "-QUEUE-TARGET");
        check(actual.queueAppend().effectCount() == expected.effect, id + "-EFFECT");
        check(actual.queueAppend().applyCount() == expected.apply, id + "-APPLY");
        check(
            actual.queueAppend().attemptCount() == actual.budget().attemptCount(),
            id + "-QUEUE-ATTEMPT");
      }
      if (expected.ordinal == 2) {
        check(actual.replayRecord().equals(positive.replay), id + "-REPLAY-BYTE-PRESERVE");
      } else if (expected.ordinal == 20) {
        check(
            actual.replayRecord().selectedRuleId().equals(positive.replay.selectedRuleId()),
            id + "-CACHED-RULE");
        check(actual.replayRecord().outcome() == positive.replay.outcome(), id + "-CACHED-OUTCOME");
        check(
            actual.replayRecord().postStateDigest().equals(positive.replay.postStateDigest()),
            id + "-CACHED-DIGEST");
        check(
            actual.replayRecord().replayMarker()
                == OfflineI1Oracle.ReplayMarker.SAME_INPUT_REPLAY,
            id + "-MARKER");
      } else {
        equal(
            actual.replayRecord().selectedRuleId(),
            "OFF-I1-RULE-Q-" + three(expected.ordinal),
            id + "-REPLAY-RULE");
      }
      positiveCases++;

      QueueFixture mismatch = queueFixture(expected.mismatch);
      assertNoRule(
          OfflineI1Oracle.reduceQueue(
              queueInput(mismatch, Set.of(expected.signal), OfflineI1Oracle.ExplicitGrant.NONE)),
          id + "-SOURCE");
      sourceNegativeCases++;

      QueueFixture guardFalse =
          expected.ordinal == 18 ? queueFixture(FixtureSource.FAILED_JOB_POSITIVE) : positive;
      assertNoRule(
          OfflineI1Oracle.reduceQueue(
              queueInput(guardFalse, Set.of(), OfflineI1Oracle.ExplicitGrant.NONE)),
          id + "-GUARD");
      guardNegativeCases++;
    }
    check(positiveCases == 20, "T-Q-POSITIVE-COUNT");
    check(sourceNegativeCases == 20, "T-Q-SOURCE-NEGATIVE-COUNT");
    check(guardNegativeCases == 20, "T-Q-GUARD-NEGATIVE-COUNT");

    QueueFixture failedNoJob = queueFixture(FixtureSource.FAILED_NO_JOB_ZERO);
    OfflineI1Oracle.QueueReduction q16Grant =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                failedNoJob,
                Set.of(OfflineI1Oracle.QueueSignal.RESUME_OR_MANUAL_RETRY),
                OfflineI1Oracle.ExplicitGrant.ELIGIBLE_EXPLICIT_GRANT));
    equal(q16Grant.selectedRuleId(), "OFF-I1-RULE-Q-016", "T-Q016-GRANT-RULE");
    check(q16Grant.budget().attemptCount() == 3, "T-Q016-GRANT-ATTEMPT");
    check(q16Grant.budget().manualRetryGrantCount() == 1, "T-Q016-GRANT-COUNT");
    QueueFixture failedJob = queueFixture(FixtureSource.FAILED_JOB_ZERO);
    OfflineI1Oracle.QueueReduction q17Grant =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                failedJob,
                Set.of(OfflineI1Oracle.QueueSignal.RESUME_OR_MANUAL_RETRY),
                OfflineI1Oracle.ExplicitGrant.ELIGIBLE_EXPLICIT_GRANT));
    equal(q17Grant.selectedRuleId(), "OFF-I1-RULE-Q-017", "T-Q017-GRANT-RULE");
    check(q17Grant.budget().attemptCount() == 3, "T-Q017-GRANT-ATTEMPT");
    check(q17Grant.budget().manualRetryGrantCount() == 1, "T-Q017-GRANT-COUNT");

    QueueFixture pending = queueFixture(FixtureSource.PENDING);
    equal(
        OfflineI1Oracle.reduceQueue(
                queueInput(
                    pending,
                    Set.of(
                        OfflineI1Oracle.QueueSignal.CONSENT_INVALID_OR_REVOKED,
                        OfflineI1Oracle.QueueSignal.SAME_KEY_DIFFERENT_CANONICAL_INPUT,
                        OfflineI1Oracle.QueueSignal.PROFILE_OR_RUNTIME_BLOCKED,
                        OfflineI1Oracle.QueueSignal.CONNECTIVITY_DENIED),
                    OfflineI1Oracle.ExplicitGrant.NONE))
            .selectedRuleId(),
        "OFF-I1-RULE-Q-001",
        "T-Q-ARBITRATION-Q001");
    QueueFixture waiting = queueFixture(FixtureSource.WAITING);
    equal(
        OfflineI1Oracle.reduceQueue(
                queueInput(
                    waiting,
                    Set.of(
                        OfflineI1Oracle.QueueSignal.SAME_KEY_DIFFERENT_CANONICAL_INPUT,
                        OfflineI1Oracle.QueueSignal.PROFILE_OR_RUNTIME_BLOCKED),
                    OfflineI1Oracle.ExplicitGrant.NONE))
            .selectedRuleId(),
        "OFF-I1-RULE-Q-002",
        "T-Q-ARBITRATION-Q002");
    QueueFixture failedZero = queueFixture(FixtureSource.FAILED_JOB_ZERO);
    equal(
        OfflineI1Oracle.reduceQueue(
                queueInput(
                    failedZero,
                    Set.of(OfflineI1Oracle.QueueSignal.PROFILE_OR_RUNTIME_BLOCKED),
                    OfflineI1Oracle.ExplicitGrant.NONE))
            .selectedRuleId(),
        "OFF-I1-RULE-Q-003",
        "T-Q-ARBITRATION-Q003");
    equal(
        OfflineI1Oracle.reduceQueue(
                queueInput(
                    failedZero,
                    Set.of(OfflineI1Oracle.QueueSignal.CONNECTIVITY_DENIED),
                    OfflineI1Oracle.ExplicitGrant.NONE))
            .selectedRuleId(),
        "OFF-I1-RULE-Q-004",
        "T-Q-ARBITRATION-Q004");
    OfflineI1Oracle.QueueReduction invalidTruth =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                queueFixture(FixtureSource.RESULT),
                Set.of(
                    OfflineI1Oracle.QueueSignal.USER_TRUTH_ALLOW,
                    OfflineI1Oracle.QueueSignal.USER_TRUTH_BLOCKS),
                OfflineI1Oracle.ExplicitGrant.NONE));
    check(invalidTruth.outcome() == OfflineI1Oracle.ReducerOutcome.INVALID_INPUT, "T-Q-TRUTH-CONFLICT-OUTCOME");
    check(invalidTruth.diagnostic() == OfflineI1Oracle.Diagnostic.INVALID_015, "T-Q-TRUTH-CONFLICT-DIAGNOSTIC");
    assertNoRule(
        OfflineI1Oracle.reduceQueue(
            queueInput(pending, Set.of(), OfflineI1Oracle.ExplicitGrant.NONE)),
        "T-Q-NO-RULE");

    QueueFixture local = queueFixture(FixtureSource.LOCAL_ONLY);
    OfflineI1Oracle.QueueReduction q7Reset =
        OfflineI1Oracle.reduceQueue(
            new OfflineI1Oracle.QueueReducerInput(
                "OFF-SYN-013",
                local.state,
                new OfflineI1Oracle.AttemptBudget(2, 0),
                null,
                null,
                null,
                Set.of(OfflineI1Oracle.QueueSignal.NEW_LOGICAL_KEY),
                OfflineI1Oracle.ExplicitGrant.NONE));
    equal(q7Reset.selectedRuleId(), "OFF-I1-RULE-Q-007", "T-Q007-BUDGET-RULE");
    check(q7Reset.budget().attemptCount() == 0, "T-Q007-BUDGET-ATTEMPT");
    check(q7Reset.budget().manualRetryGrantCount() == 0, "T-Q007-BUDGET-GRANT");

    QueueFixture failedAccepted = queueFixture(FixtureSource.FAILED_JOB_POSITIVE);
    OfflineI1Oracle.QueueReduction q3Accepted =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                failedAccepted,
                Set.of(OfflineI1Oracle.QueueSignal.PROFILE_OR_RUNTIME_BLOCKED),
                OfflineI1Oracle.ExplicitGrant.NONE));
    QueueFixture pendingAccepted =
        new QueueFixture(
            q3Accepted.postStateVector(),
            q3Accepted.budget(),
            q3Accepted.queueAppend(),
            q3Accepted.replayRecord(),
            q3Accepted.replayPostStateVector());
    OfflineI1Oracle.QueueReduction q10Accepted =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                pendingAccepted,
                Set.of(OfflineI1Oracle.QueueSignal.SEND_AVAILABLE),
                OfflineI1Oracle.ExplicitGrant.NONE));
    equal(q10Accepted.selectedRuleId(), "OFF-I1-RULE-Q-010", "T-Q010-ACCEPTED-RULE");
    equal(
        q10Accepted.queueAppend().jobIdHash(),
        pendingAccepted.row.jobIdHash(),
        "T-Q010-ACCEPTED-JOB");
    check(q10Accepted.queueAppend().effectCount() == 1, "T-Q010-ACCEPTED-EFFECT");
    check(q10Accepted.queueAppend().applyCount() == 0, "T-Q010-ACCEPTED-APPLY");

    QueueFixture uploading = queueFixture(FixtureSource.UPLOADING);
    OfflineI1Oracle.QueueReduction q11 =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                uploading,
                Set.of(OfflineI1Oracle.QueueSignal.FIRST_DURABLE_ACCEPTANCE),
                OfflineI1Oracle.ExplicitGrant.NONE));
    equal(
        q11.queueAppend().jobIdHash(),
        OfflineI1Oracle.jobIdHash(uploading.row.logicalKeyHash(), "job.017"),
        "T-Q011-CURRENT-LOGICAL-PREIMAGE");

    OfflineI1Oracle.QueueReduction deniedContradiction =
        OfflineI1Oracle.reduceQueue(
            new OfflineI1Oracle.QueueReducerInput(
                "OFF-SYN-017",
                pending.state,
                pending.budget,
                pending.row,
                pending.replay,
                pending.replayWitness,
                Set.of(OfflineI1Oracle.QueueSignal.CONNECTIVITY_DENIED),
                OfflineI1Oracle.ExplicitGrant.NONE));
    assertInvalidQueue(
        deniedContradiction,
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-Q-CONNECTIVITY-DENIED-CONTRADICTION");
    QueueFixture deniedPending = withConnectivity(pending, OfflineI1Oracle.ConnectivityState.NETWORK_DENIED);
    OfflineI1Oracle.QueueReduction sendContradiction =
        OfflineI1Oracle.reduceQueue(
            new OfflineI1Oracle.QueueReducerInput(
                "OFF-SYN-017",
                deniedPending.state,
                deniedPending.budget,
                deniedPending.row,
                deniedPending.replay,
                deniedPending.replayWitness,
                Set.of(OfflineI1Oracle.QueueSignal.SEND_AVAILABLE),
                OfflineI1Oracle.ExplicitGrant.NONE));
    assertInvalidQueue(
        sendContradiction,
        OfflineI1Oracle.Diagnostic.INVALID_008,
        "T-Q-SEND-CONNECTIVITY-CONTRADICTION");

    OfflineI1Oracle.QueueReduction q19 =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                queueFixture(FixtureSource.REMOTE),
                Set.of(OfflineI1Oracle.QueueSignal.SAME_INPUT_REPLAY),
                OfflineI1Oracle.ExplicitGrant.NONE));
    QueueFixture remote = queueFixture(FixtureSource.REMOTE);
    check(
        q19.replayRecord().canonicalInputDigest().equals(remote.replay.canonicalInputDigest()),
        "T-Q019-CANONICAL-INPUT");
    check(q19.replayRecord().resultIdHash() == null, "T-Q019-RESULT");
    check(q19.replayRecord().effectCount() == 1, "T-Q019-EFFECT");
    check(q19.replayRecord().applyCount() == 0, "T-Q019-APPLY");
    check(
        q19.replayRecord().replayMarker() == OfflineI1Oracle.ReplayMarker.SAME_INPUT_REPLAY,
        "T-Q019-MARKER");
    check(
        q19.replayRecord().attemptCount() == remote.replay.attemptCount() + 1,
        "T-Q019-ATTEMPT-ONLY");

    QueueFixture applied = queueFixture(FixtureSource.APPLIED);
    OfflineI1Oracle.QueueReduction q20 =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                applied,
                Set.of(OfflineI1Oracle.QueueSignal.DUPLICATE_TRIGGER),
                OfflineI1Oracle.ExplicitGrant.NONE));
    assertReplayEqualExceptMarker(
        q20.replayRecord(), applied.replay, "T-Q020-CACHED-SEMANTIC");

    OfflineI1Oracle.RunState replayRun = OfflineI1Oracle.startScenario("OFF-SYN-018");
    OfflineI1Oracle.QueueReduction directReplay =
        OfflineI1Oracle.reduceQueue(
            new OfflineI1Oracle.QueueReducerInput(
                replayRun.scenarioId(),
                replayRun.stateVector(),
                replayRun.budget(),
                lastQueue(replayRun),
                replayRun.replayRecords().get(0),
                replayRun.stateVector(),
                Set.of(OfflineI1Oracle.QueueSignal.SAME_INPUT_REPLAY),
                OfflineI1Oracle.ExplicitGrant.NONE));
    OfflineI1Oracle.Step adaptedReplay = OfflineI1Oracle.execute(replayRun);
    check(
        adaptedReplay.runState().replayRecords().get(0).equals(directReplay.replayRecord()),
        "T-Q019-ADAPTER-REPLAY-AUTHORITY");
  }

  private static void testQueueReplayWitness() {
    QueueFixture pending = queueFixture(FixtureSource.PENDING);
    OfflineI1Oracle.StateVector localDrift =
        new OfflineI1Oracle.StateVector(
            OfflineI1Oracle.LocalState.LOCAL_OPERATION_SUCCEEDED,
            pending.state.processingCapability(),
            pending.state.connectivity(),
            pending.state.model(),
            pending.state.queue());
    QueueFixture localFixture =
        new QueueFixture(
            localDrift,
            pending.budget,
            pending.row,
            pending.replay,
            pending.replayWitness);
    equal(
        OfflineI1Oracle.reduceQueue(
                queueInput(
                    localFixture,
                    Set.of(OfflineI1Oracle.QueueSignal.PROFILE_OR_RUNTIME_BLOCKED),
                    OfflineI1Oracle.ExplicitGrant.NONE))
            .selectedRuleId(),
        "OFF-I1-RULE-Q-003",
        "T-REPLAY-WITNESS-LOCAL-DRIFT");

    QueueFixture connectivityFixture =
        withConnectivity(pending, OfflineI1Oracle.ConnectivityState.NETWORK_DENIED);
    equal(
        OfflineI1Oracle.reduceQueue(
                queueInput(
                    connectivityFixture,
                    Set.of(OfflineI1Oracle.QueueSignal.CONNECTIVITY_DENIED),
                    OfflineI1Oracle.ExplicitGrant.NONE))
            .selectedRuleId(),
        "OFF-I1-RULE-Q-004",
        "T-REPLAY-WITNESS-CONNECTIVITY-DRIFT");

    OfflineI1Oracle.StateVector modelDrift =
        new OfflineI1Oracle.StateVector(
            pending.state.local(),
            pending.state.processingCapability(),
            pending.state.connectivity(),
            OfflineI1Oracle.ModelState.MODEL_INSTALLED_APPROVED,
            pending.state.queue());
    QueueFixture modelFixture =
        new QueueFixture(
            modelDrift,
            pending.budget,
            pending.row,
            pending.replay,
            pending.replayWitness);
    equal(
        OfflineI1Oracle.reduceQueue(
                queueInput(
                    modelFixture,
                    Set.of(OfflineI1Oracle.QueueSignal.PROFILE_OR_RUNTIME_BLOCKED),
                    OfflineI1Oracle.ExplicitGrant.NONE))
            .selectedRuleId(),
        "OFF-I1-RULE-Q-003",
        "T-REPLAY-WITNESS-MODEL-DRIFT");

    OfflineI1Oracle.StateVector processingDrift =
        new OfflineI1Oracle.StateVector(
            pending.state.local(),
            OfflineI1Oracle.ProcessingState.PENDING_CAPABILITY,
            pending.state.connectivity(),
            pending.state.model(),
            pending.state.queue());
    QueueFixture processingFixture =
        new QueueFixture(
            processingDrift,
            pending.budget,
            pending.row,
            pending.replay,
            pending.replayWitness);
    equal(
        OfflineI1Oracle.reduceQueue(
                queueInput(
                    processingFixture,
                    Set.of(OfflineI1Oracle.QueueSignal.PROFILE_OR_RUNTIME_BLOCKED),
                    OfflineI1Oracle.ExplicitGrant.NONE))
            .selectedRuleId(),
        "OFF-I1-RULE-Q-003",
        "T-REPLAY-WITNESS-PROCESSING-DRIFT");

    OfflineI1Oracle.ReplayRecord forgedDigest =
        new OfflineI1Oracle.ReplayRecord(
            pending.replay.logicalKeyHash(),
            pending.replay.canonicalInputDigest(),
            pending.replay.selectedRuleId(),
            pending.replay.outcome(),
            OfflineI1Oracle.stateVectorDigest(localDrift),
            pending.replay.resultIdHash(),
            pending.replay.replayMarker(),
            pending.replay.attemptCount(),
            pending.replay.effectCount(),
            pending.replay.applyCount(),
            pending.replay.remoteDeletionEffectCount());
    assertInvalidQueue(
        OfflineI1Oracle.reduceQueue(
            new OfflineI1Oracle.QueueReducerInput(
                "OFF-SYN-017",
                localDrift,
                pending.budget,
                pending.row,
                forgedDigest,
                pending.replayWitness,
                Set.of(OfflineI1Oracle.QueueSignal.PROFILE_OR_RUNTIME_BLOCKED),
                OfflineI1Oracle.ExplicitGrant.NONE)),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-REPLAY-WITNESS-FORGED-DIGEST");
    assertInvalidQueue(
        OfflineI1Oracle.reduceQueue(
            new OfflineI1Oracle.QueueReducerInput(
                "OFF-SYN-017",
                localDrift,
                pending.budget,
                pending.row,
                pending.replay,
                localDrift,
                Set.of(OfflineI1Oracle.QueueSignal.PROFILE_OR_RUNTIME_BLOCKED),
                OfflineI1Oracle.ExplicitGrant.NONE)),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-REPLAY-WITNESS-FORGED-WITNESS");

    OfflineI1Oracle.QueueReduction missingWitness =
        OfflineI1Oracle.reduceQueue(
            new OfflineI1Oracle.QueueReducerInput(
                "OFF-SYN-017",
                pending.state,
                pending.budget,
                pending.row,
                pending.replay,
                null,
                Set.of(),
                OfflineI1Oracle.ExplicitGrant.NONE));
    assertInvalidQueue(
        missingWitness,
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-REPLAY-WITNESS-NULL-MISMATCH");
    check(missingWitness.replayRecord().equals(pending.replay), "T-REPLAY-WITNESS-NULL-RECORD");
    check(missingWitness.replayPostStateVector() == null, "T-REPLAY-WITNESS-NULL-WITNESS");
    check(missingWitness.postStateVector().equals(pending.state), "T-REPLAY-WITNESS-NULL-STATE");
    check(missingWitness.budget().equals(pending.budget), "T-REPLAY-WITNESS-NULL-BUDGET");

    OfflineI1Oracle.QueueReduction missingRecord =
        OfflineI1Oracle.reduceQueue(
            new OfflineI1Oracle.QueueReducerInput(
                "OFF-SYN-017",
                pending.state,
                pending.budget,
                pending.row,
                null,
                pending.replayWitness,
                Set.of(),
                OfflineI1Oracle.ExplicitGrant.NONE));
    assertInvalidQueue(
        missingRecord,
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-REPLAY-RECORD-NULL-MISMATCH");
    check(missingRecord.replayRecord() == null, "T-REPLAY-RECORD-NULL-RECORD");
    check(
        missingRecord.replayPostStateVector().equals(pending.replayWitness),
        "T-REPLAY-RECORD-NULL-WITNESS");
    check(missingRecord.postStateVector().equals(pending.state), "T-REPLAY-RECORD-NULL-STATE");
    check(missingRecord.budget().equals(pending.budget), "T-REPLAY-RECORD-NULL-BUDGET");

    OfflineI1Oracle.QueueReduction combinedFirstFailure =
        OfflineI1Oracle.reduceQueue(
            new OfflineI1Oracle.QueueReducerInput(
                "OFF-SYN-017",
                connectivityFixture.state,
                pending.budget,
                pending.row,
                forgedDigest,
                pending.replayWitness,
                Set.of(OfflineI1Oracle.QueueSignal.SAME_INPUT_REPLAY),
                OfflineI1Oracle.ExplicitGrant.NONE));
    assertInvalidQueue(
        combinedFirstFailure,
        OfflineI1Oracle.Diagnostic.INVALID_008,
        "T-REPLAY-WITNESS-DENIED-FIRST-FAILURE");

    QueueFixture applied = queueFixture(FixtureSource.APPLIED);
    OfflineI1Oracle.QueueReduction firstDuplicate =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                applied,
                Set.of(OfflineI1Oracle.QueueSignal.DUPLICATE_TRIGGER),
                OfflineI1Oracle.ExplicitGrant.NONE));
    QueueFixture replayedApplied =
        new QueueFixture(
            applied.state,
            applied.budget,
            applied.row,
            firstDuplicate.replayRecord(),
            firstDuplicate.replayPostStateVector());
    OfflineI1Oracle.QueueReduction secondDuplicate =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                replayedApplied,
                Set.of(OfflineI1Oracle.QueueSignal.DUPLICATE_TRIGGER),
                OfflineI1Oracle.ExplicitGrant.NONE));
    equal(secondDuplicate.selectedRuleId(), "OFF-I1-RULE-Q-020", "T-Q020-REPEATED-RULE");
    check(
        secondDuplicate.replayRecord().equals(firstDuplicate.replayRecord()),
        "T-Q020-REPEATED-BYTE-STABLE");
    check(
        secondDuplicate.replayPostStateVector().equals(firstDuplicate.replayPostStateVector()),
        "T-Q020-REPEATED-WITNESS-STABLE");

    QueueFixture remote = queueFixture(FixtureSource.REMOTE);
    for (OfflineI1Oracle.ConnectivityState blocked :
        List.of(
            OfflineI1Oracle.ConnectivityState.NETWORK_DENIED,
            OfflineI1Oracle.ConnectivityState.AIRPLANE_MODE,
            OfflineI1Oracle.ConnectivityState.RECONNECTING)) {
      QueueFixture blockedRemote = withConnectivity(remote, blocked);
      OfflineI1Oracle.QueueReduction rejected =
          OfflineI1Oracle.reduceQueue(
              queueInput(
                  blockedRemote,
                  Set.of(OfflineI1Oracle.QueueSignal.SAME_INPUT_REPLAY),
                  OfflineI1Oracle.ExplicitGrant.NONE));
      assertInvalidQueue(rejected, OfflineI1Oracle.Diagnostic.INVALID_008, "T-Q019-BLOCKED-" + blocked);
      check(rejected.postStateVector().equals(blockedRemote.state), "T-Q019-BLOCKED-STATE-" + blocked);
      check(rejected.budget().equals(blockedRemote.budget), "T-Q019-BLOCKED-BUDGET-" + blocked);
      check(rejected.replayRecord().equals(blockedRemote.replay), "T-Q019-BLOCKED-REPLAY-" + blocked);
      check(
          rejected.replayPostStateVector().equals(blockedRemote.replayWitness),
          "T-Q019-BLOCKED-WITNESS-" + blocked);
    }
  }

  private static void testReplayLifecycle() {
    int rows = 0;
    int concrete = 0;
    QueueFixture local = queueFixture(FixtureSource.LOCAL_ONLY);
    OfflineI1Oracle.QueueReduction absent =
        OfflineI1Oracle.reduceQueue(
            queueInput(local, Set.of(), OfflineI1Oracle.ExplicitGrant.NONE));
    check(absent.replayRecord() == null, "T-REPLAY-01-ABSENT");
    rows++;
    concrete++;

    OfflineI1Oracle.QueueReduction original =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                local,
                Set.of(OfflineI1Oracle.QueueSignal.NEW_LOGICAL_KEY),
                OfflineI1Oracle.ExplicitGrant.NONE));
    check(
        original.replayRecord().replayMarker() == OfflineI1Oracle.ReplayMarker.ORIGINAL,
        "T-REPLAY-02-MARKER");
    equal(
        original.replayRecord().selectedRuleId(),
        "OFF-I1-RULE-Q-007",
        "T-REPLAY-02-RULE");
    rows++;
    concrete++;

    QueueFixture originalFixture =
        new QueueFixture(
            original.postStateVector(),
            original.budget(),
            original.queueAppend(),
            original.replayRecord(),
            original.replayPostStateVector());
    OfflineI1Oracle.QueueReduction ordinary =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                originalFixture,
                Set.of(OfflineI1Oracle.QueueSignal.SCHEDULER_TICK_WHILE_DENIED),
                OfflineI1Oracle.ExplicitGrant.NONE));
    equal(
        ordinary.replayRecord().logicalKeyHash(),
        original.replayRecord().logicalKeyHash(),
        "T-REPLAY-03-LOGICAL");
    equal(
        ordinary.replayRecord().canonicalInputDigest(),
        original.replayRecord().canonicalInputDigest(),
        "T-REPLAY-03-INPUT");
    equal(
        ordinary.replayRecord().selectedRuleId(),
        "OFF-I1-RULE-Q-008",
        "T-REPLAY-03-RULE");
    rows++;
    concrete++;

    QueueFixture remote = queueFixture(FixtureSource.REMOTE);
    OfflineI1Oracle.QueueReduction replayAttempt =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                remote,
                Set.of(OfflineI1Oracle.QueueSignal.SAME_INPUT_REPLAY),
                OfflineI1Oracle.ExplicitGrant.NONE));
    check(
        replayAttempt.replayRecord().attemptCount() == remote.replay.attemptCount() + 1,
        "T-REPLAY-04-ATTEMPT");
    check(replayAttempt.replayRecord().effectCount() == 1, "T-REPLAY-04-EFFECT");
    rows++;
    concrete++;

    QueueFixture applied = queueFixture(FixtureSource.APPLIED);
    OfflineI1Oracle.QueueReduction duplicate =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                applied,
                Set.of(OfflineI1Oracle.QueueSignal.DUPLICATE_TRIGGER),
                OfflineI1Oracle.ExplicitGrant.NONE));
    assertReplayEqualExceptMarker(
        duplicate.replayRecord(), applied.replay, "T-REPLAY-05-CACHED");
    rows++;
    concrete++;

    QueueFixture pending = queueFixture(FixtureSource.PENDING);
    OfflineI1Oracle.QueueReduction mismatch =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                pending,
                Set.of(OfflineI1Oracle.QueueSignal.SAME_KEY_DIFFERENT_CANONICAL_INPUT),
                OfflineI1Oracle.ExplicitGrant.NONE));
    check(mismatch.replayRecord().equals(pending.replay), "T-REPLAY-06-BYTE-IDENTICAL");
    check(
        mismatch.diagnostic() == OfflineI1Oracle.Diagnostic.IDEMPOTENCY_INPUT_MISMATCH,
        "T-REPLAY-06-DIAGNOSTIC");
    rows++;
    concrete++;

    OfflineI1Oracle.QueueReduction invalid =
        OfflineI1Oracle.reduceQueue(
            queueInput(
                queueFixture(FixtureSource.RESULT),
                Set.of(
                    OfflineI1Oracle.QueueSignal.USER_TRUTH_ALLOW,
                    OfflineI1Oracle.QueueSignal.USER_TRUTH_BLOCKS),
                OfflineI1Oracle.ExplicitGrant.NONE));
    check(
        invalid.replayRecord().equals(queueFixture(FixtureSource.RESULT).replay),
        "T-REPLAY-07-INVALID");
    concrete++;
    OfflineI1Oracle.QueueReduction noRule =
        OfflineI1Oracle.reduceQueue(
            queueInput(pending, Set.of(), OfflineI1Oracle.ExplicitGrant.NONE));
    check(noRule.replayRecord().equals(pending.replay), "T-REPLAY-07-NO-RULE");
    concrete++;
    OfflineI1Oracle.RunState delete13 =
        OfflineI1Oracle.startDeletionScenario(
            13, OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE);
    OfflineI1Oracle.Step delete13Dispatch = OfflineI1Oracle.executeDeletion(delete13, 13);
    List<OfflineI1Oracle.ReplayRecord> beforeRejection =
        delete13Dispatch.runState().replayRecords();
    OfflineI1Oracle.Step delete13Reject =
        OfflineI1Oracle.executeDeletion(delete13Dispatch.runState(), 13);
    check(
        delete13Reject.runState().replayRecords().equals(beforeRejection),
        "T-REPLAY-07-POLICY");
    concrete++;
    rows++;

    OfflineI1Oracle.RunState restoredSource = runFixedScenario("OFF-SYN-017");
    byte[] restoredBytes = OfflineI1Oracle.encodeSnapshot(restoredSource);
    OfflineI1Oracle.RunState restored = OfflineI1Oracle.decodeSnapshot(restoredBytes);
    check(restored.replayRecords().equals(restoredSource.replayRecords()), "T-REPLAY-08-EQUAL");
    check(restored.replayRecords() != restoredSource.replayRecords(), "T-REPLAY-08-FRESH-LIST");
    rows++;
    concrete++;

    check(rows == 8, "T-REPLAY-ROWS-8");
    check(concrete == 10, "T-REPLAY-CONCRETE-10");
  }

  private static void testAllFixedScenarios() {
    for (int scenario = 1; scenario <= 25; scenario++) {
      String scenarioId = "OFF-SYN-" + three(scenario);
      OfflineI1Oracle.RunState run = OfflineI1Oracle.startScenario(scenarioId);
      byte[] emittedSnapshot = null;
      for (int ordinal = 1; ordinal <= SCENARIO_COUNTS[scenario - 1]; ordinal++) {
        String transition = SCENARIO_TRANSITIONS[scenario - 1][ordinal - 1];
        check(run.nextActionOrdinal() == ordinal, "T-RUN-ORDINAL-" + scenario + "-" + ordinal);
        OfflineI1Oracle.Step step;
        if (transition.equals("RESTORE:=")) {
          byte[] source = emittedSnapshot == null ? OfflineI1Oracle.encodeSnapshot(run) : emittedSnapshot;
          step = OfflineI1Oracle.execute(run, source);
        } else {
          step = OfflineI1Oracle.execute(run);
        }
        check(step.result().outcome() == expectedActionOutcome(scenario, ordinal), "T-RUN-OUTCOME-" + scenario + "-" + ordinal);
        equal(step.result().selectedRuleId(), expectedActionRule(scenario, ordinal), "T-RUN-RULE-" + scenario + "-" + ordinal);
        check(step.result().typedLedgerDeltas().flowAppend() != null, "T-RUN-FLOW-" + scenario + "-" + ordinal);
        check(
            (step.result().typedLedgerDeltas().queueAppend() != null) == expectedActionQueue(scenario, ordinal),
            "T-RUN-QUEUE-" + scenario + "-" + ordinal);
        check(step.runState().flowLedger().size() == ordinal, "T-RUN-FLOW-SIZE-" + scenario + "-" + ordinal);
        if (step.snapshotBytes() != null) emittedSnapshot = step.snapshotBytes();
        run = step.runState();
      }
      check(run.nextActionOrdinal() == SCENARIO_COUNTS[scenario - 1] + 1, "T-RUN-COMPLETE-" + scenario);
      byte[] canonical = OfflineI1Oracle.encodeSnapshot(run);
      equalBytes(canonical, OfflineI1Oracle.encodeSnapshot(OfflineI1Oracle.decodeSnapshot(canonical)), "T-RUN-ROUNDTRIP-" + scenario);
    }

    OfflineI1Oracle.RunState seventeen = runFixedScenario("OFF-SYN-017");
    check(seventeen.stateVector().queue() == OfflineI1Oracle.QueueState.APPLIED, "T-RUN-017-STATE");
    check(seventeen.budget().attemptCount() == 1, "T-RUN-017-ATTEMPT");
    check(lastQueue(seventeen).effectCount() == 1, "T-RUN-017-EFFECT");
    check(lastQueue(seventeen).applyCount() == 1, "T-RUN-017-APPLY");

    OfflineI1Oracle.RunState eighteen = runFixedScenario("OFF-SYN-018");
    check(eighteen.stateVector().queue() == OfflineI1Oracle.QueueState.APPLIED, "T-RUN-018-STATE");
    check(eighteen.budget().attemptCount() == 2, "T-RUN-018-ATTEMPT");
    check(lastQueue(eighteen).effectCount() == 1, "T-RUN-018-EFFECT");
    check(lastQueue(eighteen).applyCount() == 1, "T-RUN-018-APPLY");

    OfflineI1Oracle.RunState twentyFive = runFixedScenario("OFF-SYN-025");
    check(twentyFive.stateVector().queue() == OfflineI1Oracle.QueueState.DELETE_PENDING, "T-RUN-025-PROJECTION");
    check(
        twentyFive.queueLedger().stream()
            .anyMatch(q -> q.operationClass() == OfflineI1Oracle.OperationClass.NON_DELETION_CLOUD_INTENT && q.queueState() == OfflineI1Oracle.QueueState.CANCELLED),
        "T-RUN-025-CANCELLED-ROW");
    check(
        twentyFive.queueLedger().stream()
            .anyMatch(q -> q.operationClass() == OfflineI1Oracle.OperationClass.DELETE_CLOUD_COPY && q.queueState() == OfflineI1Oracle.QueueState.DELETE_PENDING),
        "T-RUN-025-DELETE-ROW");
    check(twentyFive.replayRecords().size() == 2, "T-RUN-025-REPLAY-ROWS");
    List<OfflineI1Oracle.QueueRow> twentyFourDeletes =
        runFixedScenario("OFF-SYN-024").queueLedger().stream()
            .filter(q -> q.operationClass() == OfflineI1Oracle.OperationClass.DELETE_CLOUD_COPY)
            .toList();
    check(twentyFourDeletes.size() == 1, "T-RUN-024-DELETE-COUNT");
    assertWaitingDeleteSeed(twentyFourDeletes.get(0), "T-RUN-024-DELETE");
    OfflineI1Oracle.QueueRow twentyFiveDelete =
        twentyFive.queueLedger().stream()
            .filter(q -> q.operationClass() == OfflineI1Oracle.OperationClass.DELETE_CLOUD_COPY)
            .findFirst()
            .orElseThrow(() -> new AssertionError("T-RUN-025-DELETE-MISSING"));
    assertWaitingDeleteSeed(twentyFiveDelete, "T-RUN-025-DELETE");

    for (String scenarioId : List.of("OFF-SYN-015", "OFF-SYN-016", "OFF-SYN-020")) {
      equalBytes(
          OfflineI1Oracle.encodeSnapshot(runFixedScenario(scenarioId)),
          OfflineI1Oracle.encodeSnapshot(runFixedScenario(scenarioId)),
          "T-RUN-CONTINUATION-" + scenarioId);
    }
  }

  private static OfflineI1Oracle.RunState runFixedScenario(String scenarioId) {
    OfflineI1Oracle.RunState run = OfflineI1Oracle.startScenario(scenarioId);
    byte[] snapshot = null;
    int scenario = Integer.parseInt(scenarioId.substring(scenarioId.length() - 3));
    for (int ordinal = 1; ordinal <= SCENARIO_COUNTS[scenario - 1]; ordinal++) {
      String transition = SCENARIO_TRANSITIONS[scenario - 1][ordinal - 1];
      OfflineI1Oracle.Step step;
      if (transition.equals("RESTORE:=")) {
        step =
            OfflineI1Oracle.execute(
                run, snapshot == null ? OfflineI1Oracle.encodeSnapshot(run) : snapshot);
      } else {
        step = OfflineI1Oracle.execute(run);
      }
      if (step.snapshotBytes() != null) snapshot = step.snapshotBytes();
      run = step.runState();
    }
    return run;
  }

  private static void testScenario026() {
    int phasePairs = 0;
    for (int row = 1; row <= 15; row++) {
      DeletionExpected expected = D_EXPECTED.get(row - 1);
      for (OfflineI1Oracle.DeletionPhase phase :
          List.of(OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE, OfflineI1Oracle.DeletionPhase.POST_ACCEPTANCE)) {
        if (!phaseAllowed(row, phase)) continue;
        phasePairs++;
        OfflineI1Oracle.RunState start = OfflineI1Oracle.startDeletionScenario(row, phase);
        long attemptsBefore = start.budget().attemptCount();
        long grantsBefore = start.budget().manualRetryGrantCount();
        OfflineI1Oracle.QueueRow before = lastQueue(start);
        OfflineI1Oracle.Step dispatch = OfflineI1Oracle.executeDeletion(start, row);
        check(dispatch.result().selectedRuleId().equals("OFF-I1-RULE-DP-" + three(row)), "T-026-DP-RULE-" + row + "-" + phase);
        check(dispatch.runState().budget().attemptCount() == attemptsBefore + expected.dispatchAttemptDelta, "T-026-DP-ATTEMPT-" + row + "-" + phase);
        check(dispatch.runState().budget().manualRetryGrantCount() == grantsBefore + expected.dispatchGrantDelta, "T-026-DP-GRANT-" + row + "-" + phase);
        List<OfflineI1Oracle.ReplayRecord> replayBeforeReconcile = dispatch.runState().replayRecords();
        OfflineI1Oracle.Step reconcile = OfflineI1Oracle.executeDeletion(dispatch.runState(), row);
        check(reconcile.result().selectedRuleId().equals("OFF-I1-RULE-D-" + three(row)), "T-026-D-RULE-" + row + "-" + phase);
        check(reconcile.result().outcome() == expected.reconcileOutcome, "T-026-D-OUTCOME-" + row + "-" + phase);
        OfflineI1Oracle.QueueRow current = lastQueue(reconcile.runState());
        check(current.queueState() == expected.queueState, "T-026-D-STATE-" + row + "-" + phase);
        check(
            current.deletionSubstatus()
                == (expected.preserveSubstatus ? before.deletionSubstatus() : expected.substatus),
            "T-026-D-SUBSTATUS-" + row + "-" + phase);
        check(current.contentFreeDeletionErrorCode() == expected.error, "T-026-D-ERROR-" + row + "-" + phase);
        check(current.deletionReceiptVerificationOutcome() == expected.receipt, "T-026-D-RECEIPT-" + row + "-" + phase);
        check(current.remoteDeletionEffectCount() == expected.remoteDeletionEffectDelta, "T-026-D-REMOTE-EFFECT-" + row + "-" + phase);
        check(current.effectCount() == expected.effectDelta, "T-026-D-EFFECT-" + row + "-" + phase);
        check(current.applyCount() == 0, "T-026-D-APPLY-" + row + "-" + phase);
        check((current.deletionIdHash() != null) == deletionIdExpected(row, phase), "T-026-D-ID-" + row + "-" + phase);
        if (row == 13) {
          check(reconcile.runState().replayRecords().equals(replayBeforeReconcile), "T-026-D13-REPLAY-" + phase);
          check(reconcile.result().typedLedgerDeltas().diagnosticCode() == OfflineI1Oracle.Diagnostic.POLICY_REJECTED, "T-026-D13-DIAGNOSTIC-" + phase);
        }
        if (row == 15) {
          check(current.deletionReceiptIdHash() != null, "T-026-D15-RECEIPT");
          check(current.deletionReceiptVerificationOutcome() == OfflineI1Oracle.ReceiptOutcome.VERIFIED, "T-026-D15-VERIFIED");
          check(current.deletionSubstatus() == null, "T-026-D15-SUBSTATUS");
        }
        equalBytes(
            OfflineI1Oracle.encodeSnapshot(reconcile.runState()),
            OfflineI1Oracle.encodeSnapshot(OfflineI1Oracle.decodeSnapshot(OfflineI1Oracle.encodeSnapshot(reconcile.runState()))),
            "T-026-ROUNDTRIP-" + row + "-" + phase);
      }
    }
    check(phasePairs == 24, "T-026-PHASE-PAIR-COUNT");

    int invalidPhases = 0;
    for (int row : new int[] {1, 2, 3}) {
      int selected = row;
      expectFault(
          OfflineI1Oracle.Diagnostic.INVALID_015,
          () -> OfflineI1Oracle.startDeletionScenario(selected, OfflineI1Oracle.DeletionPhase.POST_ACCEPTANCE),
          "T-026-PHASE-NEG-POST-" + row);
      invalidPhases++;
    }
    for (int row : new int[] {4, 14, 15}) {
      int selected = row;
      expectFault(
          OfflineI1Oracle.Diagnostic.INVALID_015,
          () -> OfflineI1Oracle.startDeletionScenario(selected, OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE),
          "T-026-PHASE-NEG-PRE-" + row);
      invalidPhases++;
    }
    check(invalidPhases == 6, "T-026-PHASE-NEGATIVE-COUNT");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_014,
        () -> OfflineI1Oracle.startDeletionScenario(16, OfflineI1Oracle.DeletionPhase.POST_ACCEPTANCE),
        "T-026-RANGE");
    OfflineI1Oracle.RunState row3 =
        OfflineI1Oracle.startDeletionScenario(3, OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE);
    OfflineI1Oracle.Step dispatched = OfflineI1Oracle.executeDeletion(row3, 3);
    OfflineI1Oracle.Step mismatch = OfflineI1Oracle.executeDeletion(dispatched.runState(), 4);
    check(mismatch.result().outcome() == OfflineI1Oracle.ReducerOutcome.INVALID_INPUT, "T-026-IMMUTABLE-ORDINAL");
    check(mismatch.result().typedLedgerDeltas().diagnosticCode() == OfflineI1Oracle.Diagnostic.INVALID_015, "T-026-IMMUTABLE-DIAG");
    testDeletionSnapshotGraphAdversaries();
  }

  private static void testDeletionSnapshotGraphAdversaries() {
    OfflineI1Oracle.RunState rowOne =
        runDeletionScenario(1, OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE);
    List<OfflineI1Oracle.QueueRow> attemptQueue = new ArrayList<>();
    for (OfflineI1Oracle.QueueRow row : rowOne.queueLedger()) {
      attemptQueue.add(withAttempt(row, 1));
    }
    OfflineI1Oracle.ReplayRecord attemptReplay =
        copyReplay(rowOne.replayRecords().get(0), null, null, null, 1L, null, null, null);
    OfflineI1Oracle.Result attemptResult =
        resultWith(
            rowOne.lastResult(),
            rowOne.flowLedger().get(1),
            attemptQueue.get(2));
    expectGraphInvalid(
        rowOne,
        new OfflineI1Oracle.AttemptBudget(1, 0),
        rowOne.stateVector(),
        rowOne.flowLedger(),
        List.copyOf(attemptQueue),
        List.of(attemptReplay),
        attemptResult,
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-026-SEED-ATTEMPT-COHERENT-DRIFT");

    OfflineI1Oracle.RunState rowThree =
        runDeletionScenario(3, OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE);
    OfflineI1Oracle.QueueRow rowThreeDispatch = rowThree.queueLedger().get(1);
    OfflineI1Oracle.QueueRow rowThreeReconcile = rowThree.queueLedger().get(2);
    String forgedIntent = OfflineI1Oracle.queueIntentIdHash("intent.026.forged");
    String forgedLogical =
        OfflineI1Oracle.logicalKeyHash(
            OfflineI1Oracle.OperationClass.DELETE_CLOUD_COPY,
            "delete.logical.026.forged");
    String forgedScope = OfflineI1Oracle.deletionScopeDigest("scope.026.forged");
    String forgedDeletionId =
        OfflineI1Oracle.deletionIdHash(
            forgedLogical, forgedScope, "deletion.026.forged");
    OfflineI1Oracle.QueueRow forgedReconcile =
        copyDeletionQueue(
            rowThreeReconcile,
            forgedIntent,
            forgedLogical,
            forgedScope,
            forgedDeletionId,
            rowThreeReconcile.deletionReceiptIdHash(),
            rowThreeReconcile.attemptCount(),
            rowThreeReconcile.replayMarker(),
            rowThreeReconcile.effectCount(),
            rowThreeReconcile.remoteDeletionEffectCount());
    OfflineI1Oracle.FlowRow forgedTailFlow =
        copyFlow(
            rowThree.flowLedger().get(1),
            rowThree.flowLedger().get(1).preStateVector(),
            rowThree.flowLedger().get(1).postStateVector(),
            rowThree.flowLedger().get(1).processingRequestIdHash(),
            forgedIntent);
    List<OfflineI1Oracle.ReplayRecord> forgedIdentityReplay = new ArrayList<>();
    forgedIdentityReplay.add(
        new OfflineI1Oracle.ReplayRecord(
            rowThreeDispatch.logicalKeyHash(),
            OfflineI1Oracle.replayInputDigest(
                rowThreeDispatch.operationClass(),
                rowThreeDispatch.logicalKeyHash(),
                OfflineI1Oracle.InputVariant.PRIMARY,
                rowThreeDispatch.deletionScopeDigest()),
            "OFF-I1-RULE-DP-003",
            OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED,
            rowThree.flowLedger().get(0).postStateDigest(),
            null,
            rowThreeDispatch.replayMarker(),
            rowThreeDispatch.attemptCount(),
            rowThreeDispatch.effectCount(),
            rowThreeDispatch.applyCount(),
            rowThreeDispatch.remoteDeletionEffectCount()));
    forgedIdentityReplay.add(
        new OfflineI1Oracle.ReplayRecord(
            forgedLogical,
            OfflineI1Oracle.replayInputDigest(
                OfflineI1Oracle.OperationClass.DELETE_CLOUD_COPY,
                forgedLogical,
                OfflineI1Oracle.InputVariant.PRIMARY,
                forgedScope),
            "OFF-I1-RULE-D-003",
            OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED,
            forgedTailFlow.postStateDigest(),
            null,
            forgedReconcile.replayMarker(),
            forgedReconcile.attemptCount(),
            forgedReconcile.effectCount(),
            forgedReconcile.applyCount(),
            forgedReconcile.remoteDeletionEffectCount()));
    forgedIdentityReplay.sort(
        (left, right) -> left.logicalKeyHash().compareTo(right.logicalKeyHash()));
    expectGraphInvalid(
        rowThree,
        rowThree.budget(),
        rowThree.stateVector(),
        List.of(rowThree.flowLedger().get(0), forgedTailFlow),
        List.of(rowThree.queueLedger().get(0), rowThreeDispatch, forgedReconcile),
        List.copyOf(forgedIdentityReplay),
        resultWith(rowThree.lastResult(), forgedTailFlow, forgedReconcile),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-026-RECONCILE-IDENTITY-COHERENT-DRIFT");

    String alternateDeletionId =
        OfflineI1Oracle.deletionIdHash(
            rowThreeReconcile.logicalKeyHash(),
            rowThreeReconcile.deletionScopeDigest(),
            "deletion.026.alternate");
    OfflineI1Oracle.QueueRow alternateD03 =
        copyDeletionQueue(
            rowThreeReconcile,
            rowThreeReconcile.intentIdHash(),
            rowThreeReconcile.logicalKeyHash(),
            rowThreeReconcile.deletionScopeDigest(),
            alternateDeletionId,
            null,
            rowThreeReconcile.attemptCount(),
            rowThreeReconcile.replayMarker(),
            0,
            0);
    expectGraphInvalid(
        rowThree,
        rowThree.budget(),
        rowThree.stateVector(),
        rowThree.flowLedger(),
        List.of(rowThree.queueLedger().get(0), rowThreeDispatch, alternateD03),
        rowThree.replayRecords(),
        resultWith(rowThree.lastResult(), rowThree.flowLedger().get(1), alternateD03),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-026-D03-DELETION-ID-COHERENT-DRIFT");

    OfflineI1Oracle.RunState rowFifteen =
        runDeletionScenario(15, OfflineI1Oracle.DeletionPhase.POST_ACCEPTANCE);
    OfflineI1Oracle.QueueRow rowFifteenDispatch = rowFifteen.queueLedger().get(1);
    OfflineI1Oracle.QueueRow rowFifteenReconcile = rowFifteen.queueLedger().get(2);
    String alternateReceiptId =
        OfflineI1Oracle.deletionReceiptIdHash(
            rowFifteenReconcile.deletionIdHash(),
            rowFifteenReconcile.deletionScopeDigest(),
            "receipt.026.alternate");
    OfflineI1Oracle.QueueRow alternateD15Receipt =
        copyDeletionQueue(
            rowFifteenReconcile,
            rowFifteenReconcile.intentIdHash(),
            rowFifteenReconcile.logicalKeyHash(),
            rowFifteenReconcile.deletionScopeDigest(),
            rowFifteenReconcile.deletionIdHash(),
            alternateReceiptId,
            rowFifteenReconcile.attemptCount(),
            rowFifteenReconcile.replayMarker(),
            1,
            1);
    expectGraphInvalid(
        rowFifteen,
        rowFifteen.budget(),
        rowFifteen.stateVector(),
        rowFifteen.flowLedger(),
        List.of(
            rowFifteen.queueLedger().get(0),
            rowFifteenDispatch,
            alternateD15Receipt),
        rowFifteen.replayRecords(),
        resultWith(
            rowFifteen.lastResult(),
            rowFifteen.flowLedger().get(1),
            alternateD15Receipt),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-026-D15-RECEIPT-ID-COHERENT-DRIFT");

    OfflineI1Oracle.QueueRow prematureD15Effect =
        copyDeletionQueue(
            rowFifteenDispatch,
            rowFifteenDispatch.intentIdHash(),
            rowFifteenDispatch.logicalKeyHash(),
            rowFifteenDispatch.deletionScopeDigest(),
            rowFifteenDispatch.deletionIdHash(),
            null,
            rowFifteenDispatch.attemptCount(),
            rowFifteenDispatch.replayMarker(),
            1,
            1);
    expectGraphInvalid(
        rowFifteen,
        rowFifteen.budget(),
        rowFifteen.stateVector(),
        rowFifteen.flowLedger(),
        List.of(
            rowFifteen.queueLedger().get(0),
            prematureD15Effect,
            rowFifteenReconcile),
        rowFifteen.replayRecords(),
        rowFifteen.lastResult(),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-026-D15-DISPATCH-EFFECT-COHERENT-DRIFT");

    OfflineI1Oracle.RunState rowFive =
        runDeletionScenario(5, OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE);
    OfflineI1Oracle.QueueRow markerDispatch =
        copyDeletionQueue(
            rowFive.queueLedger().get(1),
            rowFive.queueLedger().get(1).intentIdHash(),
            rowFive.queueLedger().get(1).logicalKeyHash(),
            rowFive.queueLedger().get(1).deletionScopeDigest(),
            rowFive.queueLedger().get(1).deletionIdHash(),
            null,
            rowFive.queueLedger().get(1).attemptCount(),
            OfflineI1Oracle.ReplayMarker.SAME_INPUT_REPLAY,
            0,
            0);
    OfflineI1Oracle.QueueRow markerReconcile =
        copyDeletionQueue(
            rowFive.queueLedger().get(2),
            rowFive.queueLedger().get(2).intentIdHash(),
            rowFive.queueLedger().get(2).logicalKeyHash(),
            rowFive.queueLedger().get(2).deletionScopeDigest(),
            rowFive.queueLedger().get(2).deletionIdHash(),
            null,
            rowFive.queueLedger().get(2).attemptCount(),
            OfflineI1Oracle.ReplayMarker.SAME_INPUT_REPLAY,
            0,
            0);
    OfflineI1Oracle.ReplayRecord markerReplay =
        copyReplay(
            rowFive.replayRecords().get(0),
            null,
            null,
            null,
            null,
            OfflineI1Oracle.ReplayMarker.SAME_INPUT_REPLAY,
            null,
            null);
    expectGraphInvalid(
        rowFive,
        rowFive.budget(),
        rowFive.stateVector(),
        rowFive.flowLedger(),
        List.of(rowFive.queueLedger().get(0), markerDispatch, markerReconcile),
        List.of(markerReplay),
        resultWith(rowFive.lastResult(), rowFive.flowLedger().get(1), markerReconcile),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-026-MARKER-COHERENT-DRIFT");
  }

  private static OfflineI1Oracle.RunState runDeletionScenario(
      int row, OfflineI1Oracle.DeletionPhase phase) {
    OfflineI1Oracle.RunState start = OfflineI1Oracle.startDeletionScenario(row, phase);
    OfflineI1Oracle.Step dispatch = OfflineI1Oracle.executeDeletion(start, row);
    return OfflineI1Oracle.executeDeletion(dispatch.runState(), row).runState();
  }

  private static boolean phaseAllowed(int row, OfflineI1Oracle.DeletionPhase phase) {
    ExpectedPhases expected = D_EXPECTED.get(row - 1).phases;
    return expected == ExpectedPhases.BOTH
        || (expected == ExpectedPhases.PRE
            && phase == OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE)
        || (expected == ExpectedPhases.POST
            && phase == OfflineI1Oracle.DeletionPhase.POST_ACCEPTANCE);
  }

  private static boolean deletionIdExpected(int row, OfflineI1Oracle.DeletionPhase phase) {
    String outcome = D_EXPECTED.get(row - 1).deletionIdOutcome;
    return switch (outcome) {
      case "NULL" -> false;
      case "REQUIRED_IMMUTABLE" -> true;
      case "PRESERVE_PHASE" -> phase == OfflineI1Oracle.DeletionPhase.POST_ACCEPTANCE;
      default -> throw new AssertionError("T-026-D-ID-OUTCOME");
    };
  }

  private static void testRevalidationMatrix() {
    check(R_EXPECTED.size() == 23, "T-REV-MATRIX-23");
    int concrete = 0;
    for (int index = 0; index < R_EXPECTED.size(); index++) {
      RevalidationExpected expected = R_EXPECTED.get(index);
      boolean[] jobCases =
          expected.job == ExpectedJob.BOTH
              ? new boolean[] {false, true}
              : new boolean[] {expected.job == ExpectedJob.PRESENT};
      for (boolean withJob : jobCases) {
        OfflineI1Oracle.RevalidationInput input = revalidationInput(expected, withJob);
        long attemptBefore = input.budget().attemptCount();
        OfflineI1Oracle.RevalidationDecision actual = OfflineI1Oracle.revalidate(input);
        decisionExact(
            actual,
            expected.outcome,
            expected.rule,
            expected.queueTarget,
            expected.processingTarget,
            expected.grantDelta,
            OfflineI1Oracle.Diagnostic.NONE,
            "T-REV-MATRIX-" + two(index + 1) + "-" + (withJob ? "JOB" : "NOJOB"));
        check(input.budget().attemptCount() == attemptBefore, "T-REV-MATRIX-ATTEMPT-STABLE-" + index);
        concrete++;
      }
    }
    check(concrete == 28, "T-REV-MATRIX-28");

    OfflineI1Oracle.QueueRow pending = nonDeleteRow(OfflineI1Oracle.QueueState.PENDING_UPLOAD, false, 0);
    OfflineI1Oracle.RevalidationDecision mismatch =
        revalidation(
            pending,
            OfflineI1Oracle.Consent.CURRENT,
            OfflineI1Oracle.Profile.CURRENT,
            OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED,
            OfflineI1Oracle.ConnectivityState.AVAILABLE,
            new OfflineI1Oracle.AttemptBudget(1, 0),
            OfflineI1Oracle.ExplicitGrant.NONE,
            null);
    decisionExact(
        mismatch,
        OfflineI1Oracle.RevalidationOutcome.INVALID_INPUT,
        null,
        null,
        null,
        0,
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-REV-BUDGET-ROW-MISMATCH");
  }

  private static OfflineI1Oracle.RevalidationInput revalidationInput(
      RevalidationExpected expected, boolean withJob) {
    OfflineI1Oracle.QueueState queueState =
        switch (expected.source) {
          case PENDING, REQUIRED_MODEL, OPTIONAL_CAPABILITY ->
              OfflineI1Oracle.QueueState.PENDING_UPLOAD;
          case WAITING -> OfflineI1Oracle.QueueState.WAITING_NETWORK;
          case FAILED -> OfflineI1Oracle.QueueState.FAILED_RETRYABLE;
        };
    long attempt =
        expected.cause == RevalidationCause.BUDGET || expected.cause == RevalidationCause.GRANT
            ? 3
            : expected.source == RevalidationSource.FAILED ? 2 : 0;
    OfflineI1Oracle.QueueRow row = nonDeleteRow(queueState, withJob, attempt);
    OfflineI1Oracle.Consent consent =
        expected.cause == RevalidationCause.CONSENT
            ? OfflineI1Oracle.Consent.REVOKED
            : OfflineI1Oracle.Consent.CURRENT;
    OfflineI1Oracle.Profile profile =
        expected.cause == RevalidationCause.PROFILE
            ? OfflineI1Oracle.Profile.MISSING
            : OfflineI1Oracle.Profile.CURRENT;
    OfflineI1Oracle.ProcessingRequirement requirement =
        switch (expected.source) {
          case REQUIRED_MODEL -> OfflineI1Oracle.ProcessingRequirement.REQUIRED_MODEL;
          case OPTIONAL_CAPABILITY -> OfflineI1Oracle.ProcessingRequirement.OPTIONAL_CAPABILITY;
          default ->
              expected.cause == RevalidationCause.RUNTIME
                  ? OfflineI1Oracle.ProcessingRequirement.REQUIRED_MODEL
                  : OfflineI1Oracle.ProcessingRequirement.NONE;
        };
    OfflineI1Oracle.RuntimeStatus runtime =
        switch (expected.cause) {
          case RUNTIME -> OfflineI1Oracle.RuntimeStatus.DIGEST_MISMATCH;
          case MODEL_MISSING -> OfflineI1Oracle.RuntimeStatus.MODEL_NOT_INSTALLED;
          default ->
              requirement == OfflineI1Oracle.ProcessingRequirement.NONE
                  ? OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED
                  : OfflineI1Oracle.RuntimeStatus.ELIGIBLE;
        };
    OfflineI1Oracle.ConnectivityState connectivity =
        expected.cause == RevalidationCause.CONNECTIVITY
            ? OfflineI1Oracle.ConnectivityState.NETWORK_DENIED
            : OfflineI1Oracle.ConnectivityState.AVAILABLE;
    OfflineI1Oracle.ExplicitGrant grant =
        expected.cause == RevalidationCause.GRANT
            ? OfflineI1Oracle.ExplicitGrant.ELIGIBLE_EXPLICIT_GRANT
            : OfflineI1Oracle.ExplicitGrant.NONE;
    return new OfflineI1Oracle.RevalidationInput(
        row,
        consent,
        profile,
        requirement,
        runtime,
        connectivity,
        new OfflineI1Oracle.AttemptBudget(attempt, 0),
        grant,
        null);
  }

  private static void testDeletionRevalidationMatrix() {
    int concrete = 0;
    for (DeletionExpected expected : D_EXPECTED) {
      for (OfflineI1Oracle.DeletionPhase phase :
          List.of(
              OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE,
              OfflineI1Oracle.DeletionPhase.POST_ACCEPTANCE)) {
        if (!phaseAllowed(expected.ordinal, phase)) continue;
        OfflineI1Oracle.RunState run =
            OfflineI1Oracle.startDeletionScenario(expected.ordinal, phase);
        check(
            expected.budget == ExpectedBudget.ANY
                || (expected.budget == ExpectedBudget.POSITIVE
                    && run.budget().remaining() > 0)
                || (expected.budget == ExpectedBudget.ZERO
                    && run.budget().remaining() == 0),
            "T-REV-D-BUDGET-" + expected.ordinal + "-" + phase);
        OfflineI1Oracle.Profile profile =
            expected.ordinal == 6
                ? OfflineI1Oracle.Profile.MISSING
                : expected.ordinal == 7
                    ? OfflineI1Oracle.Profile.SCOPE_MISMATCH
                    : OfflineI1Oracle.Profile.CURRENT;
        OfflineI1Oracle.ConnectivityState connectivity =
            expected.ordinal == 1 || expected.ordinal == 4
                ? OfflineI1Oracle.ConnectivityState.NETWORK_DENIED
                : OfflineI1Oracle.ConnectivityState.AVAILABLE;
        OfflineI1Oracle.RevalidationDecision actual =
            deleteRevalidation(
                run,
                profile,
                connectivity,
                expected.grant,
                expected.ordinal);
        String expectedRule =
            expected.gateRuleId == null
                ? "OFF-I1-RULE-DP-" + three(expected.ordinal)
                : expected.gateRuleId;
        decisionExact(
            actual,
            expected.revalidationOutcome,
            expectedRule,
            OfflineI1Oracle.QueueState.DELETE_PENDING,
            null,
            0,
            OfflineI1Oracle.Diagnostic.NONE,
            "T-REV-D-" + three(expected.ordinal) + "-" + phase);
        concrete++;
      }
    }
    check(concrete == 24, "T-REV-D-CONCRETE-24");

    OfflineI1Oracle.RunState invalidBase =
        OfflineI1Oracle.startDeletionScenario(
            1, OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE);
    OfflineI1Oracle.RevalidationDecision invalidRelationship =
        OfflineI1Oracle.revalidate(
            new OfflineI1Oracle.RevalidationInput(
                lastQueue(invalidBase),
                OfflineI1Oracle.Consent.NOT_APPLICABLE_DELETE,
                OfflineI1Oracle.Profile.CURRENT,
                OfflineI1Oracle.ProcessingRequirement.REQUIRED_MODEL,
                OfflineI1Oracle.RuntimeStatus.MODEL_NOT_INSTALLED,
                OfflineI1Oracle.ConnectivityState.NETWORK_DENIED,
                invalidBase.budget(),
                OfflineI1Oracle.ExplicitGrant.NONE,
                1));
    decisionExact(
        invalidRelationship,
        OfflineI1Oracle.RevalidationOutcome.INVALID_INPUT,
        null,
        null,
        null,
        0,
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-REV-D-RELATIONSHIP");

    OfflineI1Oracle.RunState row15 =
        OfflineI1Oracle.startDeletionScenario(
            15, OfflineI1Oracle.DeletionPhase.POST_ACCEPTANCE);
    OfflineI1Oracle.Step row15Dispatch = OfflineI1Oracle.executeDeletion(row15, 15);
    OfflineI1Oracle.RunState verified =
        OfflineI1Oracle.executeDeletion(row15Dispatch.runState(), 15).runState();
    OfflineI1Oracle.RevalidationDecision terminal =
        OfflineI1Oracle.revalidate(
            new OfflineI1Oracle.RevalidationInput(
                lastQueue(verified),
                OfflineI1Oracle.Consent.NOT_APPLICABLE_DELETE,
                OfflineI1Oracle.Profile.CURRENT,
                OfflineI1Oracle.ProcessingRequirement.NONE,
                OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED,
                OfflineI1Oracle.ConnectivityState.AVAILABLE,
                verified.budget(),
                OfflineI1Oracle.ExplicitGrant.NONE,
                null));
    decisionExact(
        terminal,
        OfflineI1Oracle.RevalidationOutcome.NO_STATE_CHANGE,
        null,
        OfflineI1Oracle.QueueState.DELETED_REMOTE,
        null,
        0,
        OfflineI1Oracle.Diagnostic.NO_ELIGIBLE_RULE,
        "T-REV-D-VERIFIED");
  }

  private static void testRevalidationCatalogVariants() {
    OfflineI1Oracle.QueueRow pending =
        nonDeleteRow(OfflineI1Oracle.QueueState.PENDING_UPLOAD, false, 0);
    OfflineI1Oracle.AttemptBudget budget = new OfflineI1Oracle.AttemptBudget(0, 0);
    for (OfflineI1Oracle.Consent consent :
        List.of(
            OfflineI1Oracle.Consent.MISSING,
            OfflineI1Oracle.Consent.REVOKED,
            OfflineI1Oracle.Consent.SCOPE_MISMATCH,
            OfflineI1Oracle.Consent.VERSION_MISMATCH)) {
      decisionExact(
          revalidation(
              pending,
              consent,
              OfflineI1Oracle.Profile.CURRENT,
              OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED,
              OfflineI1Oracle.ConnectivityState.AVAILABLE,
              budget,
              OfflineI1Oracle.ExplicitGrant.NONE,
              null),
          OfflineI1Oracle.RevalidationOutcome.CANCEL_NON_DELETION_CONSENT_INVALID,
          "OFF-I1-RULE-Q-001",
          OfflineI1Oracle.QueueState.CANCELLED,
          null,
          0,
          OfflineI1Oracle.Diagnostic.NONE,
          "T-REV-CONSENT-CATALOG-" + consent);
    }
    for (OfflineI1Oracle.Profile profile :
        List.of(
            OfflineI1Oracle.Profile.MISSING,
            OfflineI1Oracle.Profile.CHANGED,
            OfflineI1Oracle.Profile.SCOPE_MISMATCH)) {
      decisionExact(
          revalidation(
              pending,
              OfflineI1Oracle.Consent.CURRENT,
              profile,
              OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED,
              OfflineI1Oracle.ConnectivityState.AVAILABLE,
              budget,
              OfflineI1Oracle.ExplicitGrant.NONE,
              null),
          OfflineI1Oracle.RevalidationOutcome.BLOCK_PROFILE_REVALIDATION_REQUIRED,
          "OFF-I1-RULE-Q-003",
          OfflineI1Oracle.QueueState.PENDING_UPLOAD,
          null,
          0,
          OfflineI1Oracle.Diagnostic.NONE,
          "T-REV-PROFILE-CATALOG-" + profile);
    }
    for (OfflineI1Oracle.ConnectivityState connectivity :
        List.of(
            OfflineI1Oracle.ConnectivityState.NETWORK_DENIED,
            OfflineI1Oracle.ConnectivityState.AIRPLANE_MODE,
            OfflineI1Oracle.ConnectivityState.RECONNECTING)) {
      decisionExact(
          revalidation(
              pending,
              OfflineI1Oracle.Consent.CURRENT,
              OfflineI1Oracle.Profile.CURRENT,
              OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED,
              connectivity,
              budget,
              OfflineI1Oracle.ExplicitGrant.NONE,
              null),
          OfflineI1Oracle.RevalidationOutcome.BLOCK_CONNECTIVITY_DENIED,
          "OFF-I1-RULE-Q-004",
          OfflineI1Oracle.QueueState.WAITING_NETWORK,
          null,
          0,
          OfflineI1Oracle.Diagnostic.NONE,
          "T-REV-CONNECTIVITY-CATALOG-" + connectivity);
    }
    for (OfflineI1Oracle.RuntimeStatus runtime :
        List.of(
            OfflineI1Oracle.RuntimeStatus.MODEL_UNAVAILABLE_OR_INVALID,
            OfflineI1Oracle.RuntimeStatus.ARTIFACT_NOT_EVALUATION_APPROVED,
            OfflineI1Oracle.RuntimeStatus.DIGEST_MISMATCH,
            OfflineI1Oracle.RuntimeStatus.API_ABI_INCOMPATIBLE,
            OfflineI1Oracle.RuntimeStatus.REQUIRED_16K_EVIDENCE_MISSING)) {
      OfflineI1Oracle.RevalidationDecision actual =
          OfflineI1Oracle.revalidate(
              new OfflineI1Oracle.RevalidationInput(
                  pending,
                  OfflineI1Oracle.Consent.CURRENT,
                  OfflineI1Oracle.Profile.CURRENT,
                  OfflineI1Oracle.ProcessingRequirement.REQUIRED_MODEL,
                  runtime,
                  OfflineI1Oracle.ConnectivityState.AVAILABLE,
                  budget,
                  OfflineI1Oracle.ExplicitGrant.NONE,
                  null));
      decisionExact(
          actual,
          OfflineI1Oracle.RevalidationOutcome.BLOCK_RUNTIME_REVALIDATION_REQUIRED,
          "OFF-I1-RULE-Q-003",
          OfflineI1Oracle.QueueState.PENDING_UPLOAD,
          null,
          0,
          OfflineI1Oracle.Diagnostic.NONE,
          "T-REV-RUNTIME-CATALOG-" + runtime);
    }
  }

  private static void testRevalidation() {
    testRevalidationMatrix();
    testDeletionRevalidationMatrix();
    testRevalidationCatalogVariants();
    OfflineI1Oracle.QueueRow pending = nonDeleteRow(OfflineI1Oracle.QueueState.PENDING_UPLOAD, false, 0);
    OfflineI1Oracle.QueueRow waiting = nonDeleteRow(OfflineI1Oracle.QueueState.WAITING_NETWORK, false, 0);
    OfflineI1Oracle.QueueRow failedPre = nonDeleteRow(OfflineI1Oracle.QueueState.FAILED_RETRYABLE, false, 0);
    OfflineI1Oracle.QueueRow failedPost = nonDeleteRow(OfflineI1Oracle.QueueState.FAILED_RETRYABLE, true, 0);
    OfflineI1Oracle.AttemptBudget positive = new OfflineI1Oracle.AttemptBudget(0, 0);
    OfflineI1Oracle.AttemptBudget zero = new OfflineI1Oracle.AttemptBudget(3, 0);

    decision(
        revalidation(pending, OfflineI1Oracle.Consent.REVOKED, OfflineI1Oracle.Profile.MISSING, OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED, OfflineI1Oracle.ConnectivityState.NETWORK_DENIED, positive, OfflineI1Oracle.ExplicitGrant.NONE, null),
        OfflineI1Oracle.RevalidationOutcome.CANCEL_NON_DELETION_CONSENT_INVALID,
        "OFF-I1-RULE-Q-001",
        0,
        "T-REV-CONSENT-PRIORITY");
    decision(
        revalidation(withAttempt(pending, 3), OfflineI1Oracle.Consent.CURRENT, OfflineI1Oracle.Profile.MISSING, OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED, OfflineI1Oracle.ConnectivityState.NETWORK_DENIED, zero, OfflineI1Oracle.ExplicitGrant.NONE, null),
        OfflineI1Oracle.RevalidationOutcome.BLOCK_PROFILE_REVALIDATION_REQUIRED,
        "OFF-I1-RULE-Q-003",
        0,
        "T-REV-PROFILE-PRIORITY");
    decision(
        revalidation(withAttempt(pending, 3), OfflineI1Oracle.Consent.CURRENT, OfflineI1Oracle.Profile.CURRENT, OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED, OfflineI1Oracle.ConnectivityState.NETWORK_DENIED, zero, OfflineI1Oracle.ExplicitGrant.NONE, null),
        OfflineI1Oracle.RevalidationOutcome.BLOCK_CONNECTIVITY_DENIED,
        "OFF-I1-RULE-Q-004",
        0,
        "T-REV-CONNECTIVITY-PRIORITY");
    decision(
        revalidation(withAttempt(pending, 3), OfflineI1Oracle.Consent.CURRENT, OfflineI1Oracle.Profile.CURRENT, OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED, OfflineI1Oracle.ConnectivityState.AVAILABLE, zero, OfflineI1Oracle.ExplicitGrant.NONE, null),
        OfflineI1Oracle.RevalidationOutcome.BLOCK_FINITE_BUDGET_EXHAUSTED,
        "OFF-I1-RULE-Q-018",
        0,
        "T-REV-BUDGET");
    decision(
        revalidation(pending, OfflineI1Oracle.Consent.CURRENT, OfflineI1Oracle.Profile.CURRENT, OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED, OfflineI1Oracle.ConnectivityState.AVAILABLE, positive, OfflineI1Oracle.ExplicitGrant.NONE, null),
        OfflineI1Oracle.RevalidationOutcome.ALLOW,
        null,
        0,
        "T-REV-PENDING-ALLOW");
    decision(
        revalidation(waiting, OfflineI1Oracle.Consent.CURRENT, OfflineI1Oracle.Profile.CURRENT, OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED, OfflineI1Oracle.ConnectivityState.AVAILABLE, positive, OfflineI1Oracle.ExplicitGrant.NONE, null),
        OfflineI1Oracle.RevalidationOutcome.ALLOW,
        "OFF-I1-RULE-Q-009",
        0,
        "T-REV-WAIT-ALLOW");
    decision(
        revalidation(failedPre, OfflineI1Oracle.Consent.CURRENT, OfflineI1Oracle.Profile.CURRENT, OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED, OfflineI1Oracle.ConnectivityState.AVAILABLE, positive, OfflineI1Oracle.ExplicitGrant.NONE, null),
        OfflineI1Oracle.RevalidationOutcome.ALLOW,
        "OFF-I1-RULE-Q-016",
        0,
        "T-REV-Q016-POSITIVE");
    decision(
        revalidation(failedPost, OfflineI1Oracle.Consent.CURRENT, OfflineI1Oracle.Profile.CURRENT, OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED, OfflineI1Oracle.ConnectivityState.AVAILABLE, positive, OfflineI1Oracle.ExplicitGrant.NONE, null),
        OfflineI1Oracle.RevalidationOutcome.ALLOW,
        "OFF-I1-RULE-Q-017",
        0,
        "T-REV-Q017-POSITIVE");
    decision(
        revalidation(withAttempt(failedPre, 3), OfflineI1Oracle.Consent.CURRENT, OfflineI1Oracle.Profile.CURRENT, OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED, OfflineI1Oracle.ConnectivityState.AVAILABLE, zero, OfflineI1Oracle.ExplicitGrant.ELIGIBLE_EXPLICIT_GRANT, null),
        OfflineI1Oracle.RevalidationOutcome.ALLOW,
        "OFF-I1-RULE-Q-016",
        1,
        "T-REV-Q016-GRANT");
    decision(
        revalidation(withAttempt(failedPost, 3), OfflineI1Oracle.Consent.CURRENT, OfflineI1Oracle.Profile.CURRENT, OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED, OfflineI1Oracle.ConnectivityState.AVAILABLE, zero, OfflineI1Oracle.ExplicitGrant.ELIGIBLE_EXPLICIT_GRANT, null),
        OfflineI1Oracle.RevalidationOutcome.ALLOW,
        "OFF-I1-RULE-Q-017",
        1,
        "T-REV-Q017-GRANT");
    decision(
        revalidation(failedPost, OfflineI1Oracle.Consent.CURRENT, OfflineI1Oracle.Profile.CURRENT, OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED, OfflineI1Oracle.ConnectivityState.NETWORK_DENIED, positive, OfflineI1Oracle.ExplicitGrant.NONE, null),
        OfflineI1Oracle.RevalidationOutcome.BLOCK_CONNECTIVITY_DENIED,
        "OFF-I1-RULE-Q-004",
        0,
        "T-REV-Q004-POST");

    OfflineI1Oracle.QueueRow requiredRow = pending;
    OfflineI1Oracle.RevalidationDecision requiredWait =
        OfflineI1Oracle.revalidate(
            new OfflineI1Oracle.RevalidationInput(
                requiredRow,
                OfflineI1Oracle.Consent.CURRENT,
                OfflineI1Oracle.Profile.CURRENT,
                OfflineI1Oracle.ProcessingRequirement.REQUIRED_MODEL,
                OfflineI1Oracle.RuntimeStatus.MODEL_NOT_INSTALLED,
                OfflineI1Oracle.ConnectivityState.NETWORK_DENIED,
                positive,
                OfflineI1Oracle.ExplicitGrant.NONE,
                null));
    decision(requiredWait, OfflineI1Oracle.RevalidationOutcome.WAIT_REQUIRED_MODEL, "OFF-I1-RULE-A-009-01", 0, "T-REV-WAIT-MODEL");
    OfflineI1Oracle.RevalidationDecision optionalWait =
        OfflineI1Oracle.revalidate(
            new OfflineI1Oracle.RevalidationInput(
                requiredRow,
                OfflineI1Oracle.Consent.CURRENT,
                OfflineI1Oracle.Profile.CURRENT,
                OfflineI1Oracle.ProcessingRequirement.OPTIONAL_CAPABILITY,
                OfflineI1Oracle.RuntimeStatus.MODEL_NOT_INSTALLED,
                OfflineI1Oracle.ConnectivityState.NETWORK_DENIED,
                positive,
                OfflineI1Oracle.ExplicitGrant.NONE,
                null));
    decision(optionalWait, OfflineI1Oracle.RevalidationOutcome.WAIT_OPTIONAL_CAPABILITY, "OFF-I1-RULE-A-021-01", 0, "T-REV-WAIT-OPTIONAL");

    OfflineI1Oracle.RunState delete1 =
        OfflineI1Oracle.startDeletionScenario(1, OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE);
    decision(
        deleteRevalidation(delete1, OfflineI1Oracle.Profile.CURRENT, OfflineI1Oracle.ConnectivityState.NETWORK_DENIED, OfflineI1Oracle.ExplicitGrant.NONE, 1),
        OfflineI1Oracle.RevalidationOutcome.BLOCK_CONNECTIVITY_DENIED,
        "OFF-I1-RULE-D-001",
        0,
        "T-REV-D1");
    OfflineI1Oracle.RunState delete14 =
        OfflineI1Oracle.startDeletionScenario(14, OfflineI1Oracle.DeletionPhase.POST_ACCEPTANCE);
    decision(
        deleteRevalidation(delete14, OfflineI1Oracle.Profile.CURRENT, OfflineI1Oracle.ConnectivityState.AVAILABLE, OfflineI1Oracle.ExplicitGrant.ELIGIBLE_EXPLICIT_GRANT, 14),
        OfflineI1Oracle.RevalidationOutcome.ALLOW_DELETE_CONSENT_NOT_APPLICABLE,
        "OFF-I1-RULE-DP-014",
        0,
        "T-REV-D14");
    OfflineI1Oracle.RunState delete13 =
        OfflineI1Oracle.startDeletionScenario(13, OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE);
    decision(
        deleteRevalidation(delete13, OfflineI1Oracle.Profile.MISSING, OfflineI1Oracle.ConnectivityState.NETWORK_DENIED, OfflineI1Oracle.ExplicitGrant.NONE, 13),
        OfflineI1Oracle.RevalidationOutcome.ALLOW_DELETE_CONSENT_NOT_APPLICABLE,
        "OFF-I1-RULE-DP-013",
        0,
        "T-REV-D13-BYPASS");

    OfflineI1Oracle.RevalidationDecision invalidDeleteConsent =
        OfflineI1Oracle.revalidate(
            new OfflineI1Oracle.RevalidationInput(
                lastQueue(delete1),
                OfflineI1Oracle.Consent.CURRENT,
                OfflineI1Oracle.Profile.CURRENT,
                OfflineI1Oracle.ProcessingRequirement.NONE,
                OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED,
                OfflineI1Oracle.ConnectivityState.NETWORK_DENIED,
                delete1.budget(),
                OfflineI1Oracle.ExplicitGrant.NONE,
                1));
    decisionExact(
        invalidDeleteConsent,
        OfflineI1Oracle.RevalidationOutcome.INVALID_INPUT,
        null,
        null,
        null,
        0,
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-REV-DELETE-CONSENT-INVALID");
  }

  private static OfflineI1Oracle.RevalidationDecision revalidation(
      OfflineI1Oracle.QueueRow row,
      OfflineI1Oracle.Consent consent,
      OfflineI1Oracle.Profile profile,
      OfflineI1Oracle.RuntimeStatus runtime,
      OfflineI1Oracle.ConnectivityState connectivity,
      OfflineI1Oracle.AttemptBudget budget,
      OfflineI1Oracle.ExplicitGrant grant,
      Integer rowOrdinal) {
    return OfflineI1Oracle.revalidate(
        new OfflineI1Oracle.RevalidationInput(
            row,
            consent,
            profile,
            OfflineI1Oracle.ProcessingRequirement.NONE,
            runtime,
            connectivity,
            budget,
            grant,
            rowOrdinal));
  }

  private static OfflineI1Oracle.RevalidationDecision deleteRevalidation(
      OfflineI1Oracle.RunState run,
      OfflineI1Oracle.Profile profile,
      OfflineI1Oracle.ConnectivityState connectivity,
      OfflineI1Oracle.ExplicitGrant grant,
      int row) {
    return OfflineI1Oracle.revalidate(
        new OfflineI1Oracle.RevalidationInput(
            lastQueue(run),
            OfflineI1Oracle.Consent.NOT_APPLICABLE_DELETE,
            profile,
            OfflineI1Oracle.ProcessingRequirement.NONE,
            OfflineI1Oracle.RuntimeStatus.NOT_REQUIRED,
            connectivity,
            run.budget(),
            grant,
            row));
  }

  private static void decision(
      OfflineI1Oracle.RevalidationDecision actual,
      OfflineI1Oracle.RevalidationOutcome outcome,
      String rule,
      long grantDelta,
      String id) {
    check(actual.outcome() == outcome, id + "-OUTCOME");
    check((rule == null && actual.selectedRuleId() == null) || (rule != null && rule.equals(actual.selectedRuleId())), id + "-RULE");
    check(actual.manualGrantDelta() == grantDelta, id + "-GRANT");
    check(actual.diagnostic() == OfflineI1Oracle.Diagnostic.NONE, id + "-DIAGNOSTIC");
  }

  private static void decisionExact(
      OfflineI1Oracle.RevalidationDecision actual,
      OfflineI1Oracle.RevalidationOutcome outcome,
      String rule,
      OfflineI1Oracle.QueueState queueTarget,
      OfflineI1Oracle.ProcessingState processingTarget,
      long grantDelta,
      OfflineI1Oracle.Diagnostic diagnostic,
      String id) {
    check(actual.outcome() == outcome, id + "-OUTCOME");
    check(Objects.equals(actual.selectedRuleId(), rule), id + "-RULE");
    check(actual.queueTarget() == queueTarget, id + "-QUEUE-TARGET");
    check(actual.processingTarget() == processingTarget, id + "-PROCESSING-TARGET");
    check(actual.manualGrantDelta() == grantDelta, id + "-GRANT");
    check(actual.diagnostic() == diagnostic, id + "-DIAGNOSTIC");
  }

  private static void testSnapshotStrictness() {
    OfflineI1Oracle.RunState fresh = OfflineI1Oracle.startScenario("OFF-SYN-001");
    byte[] canonical = OfflineI1Oracle.encodeSnapshot(fresh);
    String exactFresh =
        "{\"snapshotSchema\":\"poc-offline-i1-snapshot-v0.2\",\"contractId\":\"poc-offline-readiness-stage0-v0.1\",\"scenarioId\":\"OFF-SYN-001\",\"nextActionOrdinal\":1,\"monotonicOffsetMs\":0,\"remainingAutomaticAttemptBudget\":3,\"manualRetryGrantCount\":0,\"stateVector\":{\"local\":\"FRESH_LOCAL_DEFAULT\",\"processingCapability\":\"PROCESSING_NOT_REQUESTED\",\"connectivity\":\"NETWORK_DENIED\",\"model\":\"MODEL_NOT_INSTALLED\",\"queue\":\"LOCAL_ONLY\"},\"flowLedger\":[],\"queueLedger\":[],\"replayRecords\":[],\"lastResult\":null}";
    equalBytes(
        canonical,
        exactFresh.getBytes(StandardCharsets.UTF_8),
        "T-SNAP-FRESH-EXACT-LITERAL");
    equalBytes(canonical, OfflineI1Oracle.encodeSnapshot(OfflineI1Oracle.decodeSnapshot(canonical)), "T-SNAP-FRESH");
    check(new String(canonical, StandardCharsets.UTF_8).contains("\"flowLedger\":[]"), "T-SNAP-EMPTY-FLOW");
    check(new String(canonical, StandardCharsets.UTF_8).endsWith("\"lastResult\":null}"), "T-SNAP-NULL-RESULT");

    expectSnapshotInvalid(concat(canonical, new byte[] {'\n'}), OfflineI1Oracle.Diagnostic.INVALID_013, "T-SNAP-FINAL-LF");
    expectSnapshotInvalid(concat(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf}, canonical), OfflineI1Oracle.Diagnostic.INVALID_013, "T-SNAP-BOM");
    expectSnapshotInvalid(concat(new byte[] {' '}, canonical), OfflineI1Oracle.Diagnostic.INVALID_013, "T-SNAP-LEADING-WS");
    expectSnapshotInvalid(new byte[] {(byte) 0xc0, (byte) 0xaf}, OfflineI1Oracle.Diagnostic.INVALID_013, "T-SNAP-UTF8-OVERLONG");
    expectSnapshotInvalid(new byte[] {(byte) 0x80}, OfflineI1Oracle.Diagnostic.INVALID_013, "T-SNAP-UTF8-LONE");
    expectSnapshotInvalid(new byte[] {(byte) 0xe2, (byte) 0x82}, OfflineI1Oracle.Diagnostic.INVALID_013, "T-SNAP-UTF8-TRUNCATED");

    String text = new String(canonical, StandardCharsets.UTF_8);
    expectSnapshotInvalid(
        text.replace(
                "\"snapshotSchema\":\"poc-offline-i1-snapshot-v0.2\"",
                "\"snapshotSchema\":null")
            .getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_002,
        "T-SNAP-NULL-SCHEMA-PIN");
    String nullContract =
        text.replace(
            "\"contractId\":\"poc-offline-readiness-stage0-v0.1\"",
            "\"contractId\":null");
    expectSnapshotInvalid(
        nullContract.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_002,
        "T-SNAP-NULL-CONTRACT-PIN");
    expectSnapshotInvalid(
        nullContract.replace("FRESH_LOCAL_DEFAULT", "UNKNOWN_LOCAL_STATE")
            .getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_002,
        "T-SNAP-NULL-PIN-BEFORE-STATE");
    expectSnapshotInvalid(
        text.replace("\"scenarioId\":\"OFF-SYN-001\"", "\"scenarioId\":null")
            .getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_003,
        "T-SNAP-NULL-SCENARIO-ID");
    String duplicate = text.replaceFirst("\\{", "{\"snapshotSchema\":\"poc-offline-i1-snapshot-v0.2\",");
    expectSnapshotInvalid(duplicate.getBytes(StandardCharsets.UTF_8), OfflineI1Oracle.Diagnostic.INVALID_013, "T-SNAP-DUPLICATE");
    String unknown = text.replaceFirst("\\{", "{\"unknown\":null,");
    expectSnapshotInvalid(unknown.getBytes(StandardCharsets.UTF_8), OfflineI1Oracle.Diagnostic.INVALID_013, "T-SNAP-UNKNOWN");
    String swapped =
        text.replace(
            "{\"snapshotSchema\":\"poc-offline-i1-snapshot-v0.2\",\"contractId\":\"poc-offline-readiness-stage0-v0.1\"",
            "{\"contractId\":\"poc-offline-readiness-stage0-v0.1\",\"snapshotSchema\":\"poc-offline-i1-snapshot-v0.2\"");
    expectSnapshotInvalid(swapped.getBytes(StandardCharsets.UTF_8), OfflineI1Oracle.Diagnostic.INVALID_013, "T-SNAP-ORDER");
    String leadingZero = text.replace("\"nextActionOrdinal\":1", "\"nextActionOrdinal\":01");
    expectSnapshotInvalid(leadingZero.getBytes(StandardCharsets.UTF_8), OfflineI1Oracle.Diagnostic.INVALID_013, "T-SNAP-LEADING-ZERO");
    String wrongType = text.replace("\"nextActionOrdinal\":1", "\"nextActionOrdinal\":\"1\"");
    expectSnapshotInvalid(wrongType.getBytes(StandardCharsets.UTF_8), OfflineI1Oracle.Diagnostic.INVALID_014, "T-SNAP-TYPE");
    String whitespace = text.replace("\"contractId\":", "\"contractId\": ");
    expectSnapshotInvalid(whitespace.getBytes(StandardCharsets.UTF_8), OfflineI1Oracle.Diagnostic.INVALID_013, "T-SNAP-WHITESPACE");
    String escaped = text.replace("poc-offline-i1-snapshot-v0.2", "poc-offline-i1-snapshot-v0\\u002e2");
    expectSnapshotInvalid(escaped.getBytes(StandardCharsets.UTF_8), OfflineI1Oracle.Diagnostic.INVALID_013, "T-SNAP-ESCAPE");

    OfflineI1Oracle.RunState completed = runFixedScenario("OFF-SYN-017");
    byte[] full = OfflineI1Oracle.encodeSnapshot(completed);
    equalBytes(full, OfflineI1Oracle.encodeSnapshot(OfflineI1Oracle.decodeSnapshot(full)), "T-SNAP-FULL");
    String fullText = new String(full, StandardCharsets.UTF_8);
    String nullFlowAction =
        fullText.replaceFirst("\"actionId\":\"OFF-I1-ACT-[^\"]+\"", "\"actionId\":null");
    expectSnapshotInvalid(
        nullFlowAction.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_003,
        "T-SNAP-NULL-FLOW-ACTION-ID");
    String nullReplayRule =
        fullText.replaceFirst(
            "(\"replayRecords\":\\[\\{[^}]*\"selectedRuleId\":)\"[^\"]+\"",
            "$1null");
    expectSnapshotInvalid(
        nullReplayRule.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_003,
        "T-SNAP-NULL-REPLAY-RULE-ID");
    String badDigest =
        fullText.replaceFirst(
            "[0-9a-f]{64}",
            "0000000000000000000000000000000000000000000000000000000000000000");
    expectSnapshotInvalid(badDigest.getBytes(StandardCharsets.UTF_8), OfflineI1Oracle.Diagnostic.INVALID_010, "T-SNAP-DIGEST");
    String unknownState = text.replace("FRESH_LOCAL_DEFAULT", "UNKNOWN_LOCAL_STATE");
    expectSnapshotInvalid(
        unknownState.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-STATE-CATALOG-DIAGNOSTIC");
    String stateBeforeGenericType =
        unknownState.replace("\"nextActionOrdinal\":1", "\"nextActionOrdinal\":\"1\"");
    expectSnapshotInvalid(
        stateBeforeGenericType.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-STATE-BEFORE-GENERIC-TYPE");
    String lexicalBeforeState =
        unknownState.replace(
            "poc-offline-i1-snapshot-v0.2", "poc-offline-i1-snapshot-v0\\u002e2");
    expectSnapshotInvalid(
        lexicalBeforeState.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-LEXICAL-BEFORE-STATE");
    String pinBeforeState =
        unknownState.replace(
            "poc-offline-readiness-stage0-v0.1", "poc-offline-readiness-stage0-v9.9");
    expectSnapshotInvalid(
        pinBeforeState.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_002,
        "T-SNAP-PIN-BEFORE-STATE");
    String catalogBeforeState = unknownState.replace("OFF-SYN-001", "OFF-SYN-999");
    expectSnapshotInvalid(
        catalogBeforeState.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_003,
        "T-SNAP-CATALOG-BEFORE-STATE");
    String budgetBeforeState =
        unknownState.replace(
            "\"remainingAutomaticAttemptBudget\":3",
            "\"remainingAutomaticAttemptBudget\":2");
    expectSnapshotInvalid(
        budgetBeforeState.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_008,
        "T-SNAP-BUDGET-BEFORE-STATE");
    String overflowAfterState =
        unknownState.replace(
            "\"nextActionOrdinal\":1",
            "\"nextActionOrdinal\":999999999999999999999999999999999999");
    expectSnapshotInvalid(
        overflowAfterState.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-STATE-BEFORE-OVERFLOW");
    String overflowOnly =
        text.replace(
            "\"nextActionOrdinal\":1",
            "\"nextActionOrdinal\":999999999999999999999999999999999999");
    expectSnapshotInvalid(
        overflowOnly.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_014,
        "T-SNAP-OVERFLOW-GENERIC");
    String negativeInteger = text.replace("\"nextActionOrdinal\":1", "\"nextActionOrdinal\":-1");
    expectSnapshotInvalid(
        negativeInteger.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_014,
        "T-SNAP-NEGATIVE-INTEGER-RANGE");
    String negativeRemaining =
        text.replace(
            "\"remainingAutomaticAttemptBudget\":3",
            "\"remainingAutomaticAttemptBudget\":-1");
    expectSnapshotInvalid(
        negativeRemaining.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_014,
        "T-SNAP-NEGATIVE-BUDGET-RANGE");
    String grantOverflow =
        text.replace(
            "\"manualRetryGrantCount\":0",
            "\"manualRetryGrantCount\":9223372036854775807");
    expectSnapshotInvalid(
        grantOverflow.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_014,
        "T-SNAP-GRANT-ARITHMETIC-OVERFLOW");
    String negativeZero = text.replace("\"nextActionOrdinal\":1", "\"nextActionOrdinal\":-0");
    expectSnapshotInvalid(
        negativeZero.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-NEGATIVE-ZERO-LEXICAL");
    String malformedFlowDigest =
        fullText.replaceFirst(
            "(\"preStateDigest\":)\"[0-9a-f]{64}\"",
            "$1\"bad\"");
    expectSnapshotInvalid(
        malformedFlowDigest.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-FLOW-DIGEST-SHAPE-DIAGNOSTIC");
    String malformedReplayDigest =
        fullText.replaceFirst(
            "(\"replayRecords\":\\[\\{[^}]*\"postStateDigest\":)\"[0-9a-f]{64}\"",
            "$1\"bad\"");
    expectSnapshotInvalid(
        malformedReplayDigest.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-REPLAY-DIGEST-SHAPE-DIAGNOSTIC");
    String malformedCanonicalInputDigest =
        fullText.replaceFirst(
            "(\"replayRecords\":\\[\\{[^}]*\"canonicalInputDigest\":)\"[0-9a-f]{64}\"",
            "$1\"bad\"");
    expectSnapshotInvalid(
        malformedCanonicalInputDigest.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-CANONICAL-INPUT-DIGEST-DIAGNOSTIC");
    String canonicalDigestBeforeOverflow =
        malformedCanonicalInputDigest.replaceFirst(
            "\"nextActionOrdinal\":\\d+",
            "\"nextActionOrdinal\":999999999999999999999999999999999999");
    expectSnapshotInvalid(
        canonicalDigestBeforeOverflow.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-CANONICAL-DIGEST-BEFORE-OVERFLOW");
    String malformedLocalDigest =
        fullText.replaceFirst(
            "(\"preLocalStateDigest\":)\"[0-9a-f]{64}\"",
            "$1\"bad\"");
    expectSnapshotInvalid(
        malformedLocalDigest.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-LOCAL-DIGEST-SHAPE-DIAGNOSTIC");
    OfflineI1Oracle.FlowRow validFirst = completed.flowLedger().get(0);
    OfflineI1Oracle.FlowRow badFirstDigest =
        new OfflineI1Oracle.FlowRow(
            validFirst.sequence(),
            validFirst.scenarioId(),
            validFirst.actionId(),
            validFirst.preStateVector(),
            validFirst.postStateVector(),
            validFirst.outcome(),
            validFirst.monotonicOffsetMs(),
            "0000000000000000000000000000000000000000000000000000000000000000",
            validFirst.postStateDigest(),
            validFirst.processingRequestIdHash(),
            validFirst.queueIntentIdHash(),
            validFirst.contentFreeErrorCode());
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_010,
        () ->
            new OfflineI1Oracle.RunState(
                completed.scenarioId(),
                completed.nextActionOrdinal() - 1,
                completed.monotonicOffsetMs(),
                completed.budget(),
                completed.stateVector(),
                replaceAt(completed.flowLedger(), 0, badFirstDigest),
                completed.queueLedger(),
                completed.replayRecords(),
                completed.lastResult()),
        "T-SNAP-DIGEST-BEFORE-RELATIONSHIP-PRIORITY");
    OfflineI1Oracle.RunState deletionDiagnostic =
        runDeletionScenario(3, OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE);
    String deletionDiagnosticText =
        new String(
            OfflineI1Oracle.encodeSnapshot(deletionDiagnostic), StandardCharsets.UTF_8)
            .replace("\"nextActionOrdinal\":3", "\"nextActionOrdinal\":2")
            .replaceFirst(
                "(\"deletionScopeDigest\":)\"[0-9a-f]{64}\"",
                "$1\"bad\"");
    expectSnapshotInvalid(
        deletionDiagnosticText.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_011,
        "T-SNAP-DELETION-BEFORE-RELATIONSHIP-PRIORITY");
    String deletionBeforeGenericType =
        deletionDiagnosticText.replace("\"nextActionOrdinal\":2", "\"nextActionOrdinal\":\"2\"");
    expectSnapshotInvalid(
        deletionBeforeGenericType.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_011,
        "T-SNAP-DELETION-BEFORE-GENERIC-TYPE");
    String unrelatedLocalDigest =
        OfflineI1Oracle.localStateDigest(OfflineI1Oracle.LocalState.FRESH_LOCAL_DEFAULT);
    check(
        !unrelatedLocalDigest.equals(deletionDiagnostic.queueLedger().get(0).preLocalStateDigest()),
        "T-SNAP-STATE-RELATION-BEFORE-DELETION-FIXTURE");
    String stateRelationBeforeDeletion =
        deletionDiagnosticText.replaceFirst(
            "(\\\"preLocalStateDigest\\\":)\\\"[0-9a-f]{64}\\\"",
            "$1\\\"" + unrelatedLocalDigest + "\\\"");
    expectSnapshotInvalid(
        stateRelationBeforeDeletion.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-STATE-RELATION-BEFORE-DELETION");
    String unrelatedStateDigest =
        OfflineI1Oracle.stateVectorDigest(OfflineI1Oracle.startScenario("OFF-SYN-001").stateVector());
    check(
        !unrelatedStateDigest.equals(deletionDiagnostic.replayRecords().get(0).postStateDigest()),
        "T-SNAP-REPLAY-RELATION-BEFORE-DELETION-FIXTURE");
    String replayRelationBeforeDeletion =
        deletionDiagnosticText.replaceFirst(
            "(\\\"replayRecords\\\":\\[\\{[^}]*\\\"postStateDigest\\\":)\\\"[0-9a-f]{64}\\\"",
            "$1\\\"" + unrelatedStateDigest + "\\\"");
    expectSnapshotInvalid(
        replayRelationBeforeDeletion.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-REPLAY-RELATION-BEFORE-DELETION");
    String nonDeletionEffectBeforeGeneric =
        fullText
            .replaceFirst("\\\"remoteDeletionEffectCount\\\":0", "\\\"remoteDeletionEffectCount\\\":1")
            .replaceFirst("\\\"nextActionOrdinal\\\":\\d+", "\\\"nextActionOrdinal\\\":\\\"bad\\\"");
    expectSnapshotInvalid(
        nonDeletionEffectBeforeGeneric.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_011,
        "T-SNAP-NONDELETE-REMOTE-EFFECT-BEFORE-GENERIC");
    String stateBeforeDeletion =
        deletionDiagnosticText.replace(
            "LOCAL_OPERATION_SUCCEEDED", "UNKNOWN_LOCAL_STATE");
    expectSnapshotInvalid(
        stateBeforeDeletion.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-STATE-BEFORE-DELETION");
    String catalogBeforeDeletion =
        deletionDiagnosticText.replace("OFF-SYN-026", "OFF-SYN-999");
    expectSnapshotInvalid(
        catalogBeforeDeletion.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_003,
        "T-SNAP-CATALOG-BEFORE-DELETION");
    String budgetBeforeDeletion =
        deletionDiagnosticText.replaceFirst(
            "\"remainingAutomaticAttemptBudget\":\\d+",
            "\"remainingAutomaticAttemptBudget\":999");
    expectSnapshotInvalid(
        budgetBeforeDeletion.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_008,
        "T-SNAP-BUDGET-BEFORE-DELETION");
    String verifiedDeletionText =
        new String(
            OfflineI1Oracle.encodeSnapshot(
                runDeletionScenario(15, OfflineI1Oracle.DeletionPhase.POST_ACCEPTANCE)),
            StandardCharsets.UTF_8);
    String deletionCounterOverflow =
        verifiedDeletionText.replaceFirst(
            "\"effectCount\":1",
            "\"effectCount\":999999999999999999999999999999999999");
    expectSnapshotInvalid(
        deletionCounterOverflow.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_011,
        "T-SNAP-DELETION-COUNTER-OVERFLOW");
    String nestedStateOrder =
        text.replace(
            "{\"local\":\"FRESH_LOCAL_DEFAULT\",\"processingCapability\":\"PROCESSING_NOT_REQUESTED\"",
            "{\"processingCapability\":\"PROCESSING_NOT_REQUESTED\",\"local\":\"FRESH_LOCAL_DEFAULT\"");
    expectSnapshotInvalid(
        nestedStateOrder.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-NESTED-STATE-ORDER");
    String nestedFlowUnknown =
        fullText.replaceFirst("\"flowLedger\":\\[\\{", "\"flowLedger\":[{\"unknown\":null,");
    expectSnapshotInvalid(
        nestedFlowUnknown.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-NESTED-FLOW-UNKNOWN");
    String structureBeforeState =
        nestedFlowUnknown.replace("LOCAL_OPERATION_SUCCEEDED", "UNKNOWN_LOCAL_STATE");
    expectSnapshotInvalid(
        structureBeforeState.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-STRUCTURE-BEFORE-STATE");
    String structureBeforePin =
        nestedFlowUnknown.replace(
            "poc-offline-readiness-stage0-v0.1", "poc-offline-readiness-stage0-v9.9");
    expectSnapshotInvalid(
        structureBeforePin.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-STRUCTURE-BEFORE-PIN");
    String nestedQueueType =
        fullText.replaceFirst("\"attemptCount\":0", "\"attemptCount\":\"0\"");
    expectSnapshotInvalid(
        nestedQueueType.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_014,
        "T-SNAP-NESTED-QUEUE-TYPE");
    String nestedReplayNull =
        fullText.replaceFirst(
            "(\"replayRecords\":\\[\\{\"logicalKeyHash\":)\"[0-9a-f]{64}\"",
            "$1null");
    expectSnapshotInvalid(
        nestedReplayNull.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_014,
        "T-SNAP-NESTED-REPLAY-NULL");
    String nestedDeltasUnknown =
        fullText.replaceFirst(
            "\"typedLedgerDeltas\":\\{",
            "\"typedLedgerDeltas\":{\"unknown\":null,");
    expectSnapshotInvalid(
        nestedDeltasUnknown.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-NESTED-DELTAS-UNKNOWN");

    OfflineI1Oracle.RunState nonUnit =
        withFlowOffsets(runFixedScenario("OFF-SYN-001"), new long[] {7, 11});
    byte[] nonUnitBytes = OfflineI1Oracle.encodeSnapshot(nonUnit);
    equalBytes(
        nonUnitBytes,
        OfflineI1Oracle.encodeSnapshot(OfflineI1Oracle.decodeSnapshot(nonUnitBytes)),
        "T-SNAP-MONOTONIC-NONUNIT");

    OfflineI1Oracle.Step snapshotStep =
        OfflineI1Oracle.execute(OfflineI1Oracle.startScenario("OFF-SYN-015"));
    OfflineI1Oracle.RunState restorePrior = snapshotStep.runState();
    OfflineI1Oracle.RunState validPeer = withFlowOffsets(restorePrior, new long[] {7});
    byte[] validPeerBytes = OfflineI1Oracle.encodeSnapshot(validPeer);
    equalBytes(
        validPeerBytes,
        OfflineI1Oracle.encodeSnapshot(OfflineI1Oracle.decodeSnapshot(validPeerBytes)),
        "T-RESTORE-PEER-VALID");
    OfflineI1Oracle.Step rejectedPeer = OfflineI1Oracle.execute(restorePrior, validPeerBytes);
    check(
        rejectedPeer.result().outcome() == OfflineI1Oracle.ReducerOutcome.INVALID_INPUT,
        "T-RESTORE-PEER-OUTCOME");
    check(
        rejectedPeer.result().typedLedgerDeltas().diagnosticCode()
            == OfflineI1Oracle.Diagnostic.INVALID_009,
        "T-RESTORE-PEER-DIAGNOSTIC");
    check(
        rejectedPeer.result().typedLedgerDeltas().flowAppend() == null
            && rejectedPeer.result().typedLedgerDeltas().queueAppend() == null,
        "T-RESTORE-PEER-ZERO-DELTAS");
    equalBytes(
        OfflineI1Oracle.encodeSnapshot(rejectedPeer.runState()),
        OfflineI1Oracle.encodeSnapshot(restorePrior),
        "T-RESTORE-PEER-UNCHANGED");

    testSnapshotGraphAdversaries();
  }

  private static void testDecoderBoundaries() {
    OfflineI1Oracle.RunState fresh = OfflineI1Oracle.startScenario("OFF-SYN-001");
    byte[] canonical = OfflineI1Oracle.encodeSnapshot(fresh);
    String text = new String(canonical, StandardCharsets.UTF_8);

    byte[] exactByteLimit = new byte[OfflineI1Oracle.MAX_SNAPSHOT_BYTES];
    Arrays.fill(exactByteLimit, (byte) 0x80);
    expectSnapshotInvalid(
        exactByteLimit,
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-SIZE-EXACT-ADMITTED-UTF8");
    byte[] overByteLimit = new byte[OfflineI1Oracle.MAX_SNAPSHOT_BYTES + 1];
    Arrays.fill(overByteLimit, (byte) 0x80);
    byte[] overByteLimitBefore = overByteLimit.clone();
    expectSnapshotInvalid(
        overByteLimit,
        OfflineI1Oracle.Diagnostic.INVALID_016,
        "T-SNAP-SIZE-OVER-PRECEDES-UTF8");
    equalBytes(overByteLimit, overByteLimitBefore, "T-SNAP-SIZE-OVER-INPUT-UNCHANGED");

    String depth32 =
        "[".repeat(OfflineI1Oracle.MAX_JSON_DEPTH)
            + "null"
            + "]".repeat(OfflineI1Oracle.MAX_JSON_DEPTH);
    String depth33 =
        "[".repeat(OfflineI1Oracle.MAX_JSON_DEPTH + 1)
            + "null"
            + "]".repeat(OfflineI1Oracle.MAX_JSON_DEPTH + 1);
    byte[] depth33Bytes = depth33.getBytes(StandardCharsets.UTF_8);
    byte[] depth33BytesBefore = depth33Bytes.clone();
    expectSnapshotInvalid(
        depth32.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_014,
        "T-SNAP-DEPTH-EXACT-ADMITTED-ROOT-TYPE");
    expectSnapshotInvalid(
        depth33Bytes,
        OfflineI1Oracle.Diagnostic.INVALID_016,
        "T-SNAP-DEPTH-OVER");
    equalBytes(depth33Bytes, depth33BytesBefore, "T-SNAP-DEPTH-OVER-INPUT-UNCHANGED");
    expectSnapshotInvalid(
        concat(depth33Bytes, new byte[] {(byte) 0x80}),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-DEPTH-WITH-MALFORMED-UTF8");
    expectSnapshotInvalid(
        concat(
            new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf},
            depth33.getBytes(StandardCharsets.UTF_8)),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-DEPTH-WITH-BOM");
    expectSnapshotInvalid(
        ("!" + depth33).getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-SYNTAX-BEFORE-DEPTH");
    expectSnapshotInvalid(
        (depth33 + "!").getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_016,
        "T-SNAP-DEPTH-BEFORE-LATER-SYNTAX");

    int maximumValidSnapshotBytes = 0;
    int maximumValidSnapshotDepth = 0;
    int validSnapshotCount = 0;
    for (int scenario = 1; scenario <= 25; scenario++) {
      OfflineI1Oracle.RunState run =
          OfflineI1Oracle.startScenario("OFF-SYN-" + three(scenario));
      byte[] initial = OfflineI1Oracle.encodeSnapshot(run);
      assertValidEnvelopeSnapshot(run, initial, "T-SNAP-VALID-INITIAL-" + scenario);
      validSnapshotCount++;
      maximumValidSnapshotBytes = Math.max(maximumValidSnapshotBytes, initial.length);
      maximumValidSnapshotDepth =
          Math.max(maximumValidSnapshotDepth, maximumJsonContainerDepth(initial));
      for (int ordinal = 1; ordinal <= SCENARIO_COUNTS[scenario - 1]; ordinal++) {
        OfflineI1Oracle.Step step =
            "RESTORE:=".equals(SCENARIO_TRANSITIONS[scenario - 1][ordinal - 1])
                ? OfflineI1Oracle.execute(run, OfflineI1Oracle.encodeSnapshot(run))
                : OfflineI1Oracle.execute(run);
        check(
            step.result().outcome() != OfflineI1Oracle.ReducerOutcome.INVALID_INPUT,
            "T-SNAP-VALID-ENVELOPE-FIXED-" + scenario + "-" + ordinal);
        run = step.runState();
        byte[] encoded = OfflineI1Oracle.encodeSnapshot(run);
        assertValidEnvelopeSnapshot(
            run, encoded, "T-SNAP-VALID-FIXED-" + scenario + "-" + ordinal);
        validSnapshotCount++;
        maximumValidSnapshotBytes = Math.max(maximumValidSnapshotBytes, encoded.length);
        maximumValidSnapshotDepth =
            Math.max(maximumValidSnapshotDepth, maximumJsonContainerDepth(encoded));
      }
    }
    for (int row = 1; row <= 15; row++) {
      DeletionExpected expected = D_EXPECTED.get(row - 1);
      List<OfflineI1Oracle.DeletionPhase> phases = new ArrayList<>();
      if (expected.phases() != ExpectedPhases.POST) {
        phases.add(OfflineI1Oracle.DeletionPhase.PRE_ACCEPTANCE);
      }
      if (expected.phases() != ExpectedPhases.PRE) {
        phases.add(OfflineI1Oracle.DeletionPhase.POST_ACCEPTANCE);
      }
      for (OfflineI1Oracle.DeletionPhase phase : phases) {
        OfflineI1Oracle.RunState run = OfflineI1Oracle.startDeletionScenario(row, phase);
        for (int ordinal = 0; ordinal <= 2; ordinal++) {
          byte[] encoded = OfflineI1Oracle.encodeSnapshot(run);
          assertValidEnvelopeSnapshot(
              run,
              encoded,
              "T-SNAP-VALID-DELETION-" + row + "-" + phase + "-" + ordinal);
          validSnapshotCount++;
          maximumValidSnapshotBytes = Math.max(maximumValidSnapshotBytes, encoded.length);
          maximumValidSnapshotDepth =
              Math.max(maximumValidSnapshotDepth, maximumJsonContainerDepth(encoded));
          if (ordinal < 2) {
            OfflineI1Oracle.Step step = OfflineI1Oracle.executeDeletion(run, row);
            check(
                step.result().outcome() != OfflineI1Oracle.ReducerOutcome.INVALID_INPUT,
                "T-SNAP-VALID-ENVELOPE-DELETION-" + row + "-" + phase + "-" + ordinal);
            run = step.runState();
          }
        }
      }
    }
    check(validSnapshotCount == 162, "T-SNAP-VALID-DOMAIN-EXACT-COUNT");
    check(
        maximumValidSnapshotBytes < 524_288,
        "T-SNAP-VALID-DOMAIN-PROVED-BYTE-BOUND");
    check(
        maximumValidSnapshotBytes < OfflineI1Oracle.MAX_SNAPSHOT_BYTES,
        "T-SNAP-VALID-DOMAIN-BELOW-SIZE-LIMIT");
    check(maximumValidSnapshotDepth == 5, "T-SNAP-VALID-DOMAIN-EXACT-DEPTH");
    check(
        maximumValidSnapshotDepth < OfflineI1Oracle.MAX_JSON_DEPTH,
        "T-SNAP-VALID-DOMAIN-BELOW-DEPTH-LIMIT");

    expectSnapshotInvalid(
        text.replace("\"flowLedger\":[]", "\"flowLedger\":[null]")
            .getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_014,
        "T-SNAP-NULL-FLOW-ELEMENT");
    expectSnapshotInvalid(
        text.replace("\"queueLedger\":[]", "\"queueLedger\":[null]")
            .getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_014,
        "T-SNAP-NULL-QUEUE-ELEMENT");
    expectSnapshotInvalid(
        text.replace("\"replayRecords\":[]", "\"replayRecords\":[null]")
            .getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_014,
        "T-SNAP-NULL-REPLAY-ELEMENT");
    expectSnapshotInvalid(
        text.replace("\"nextActionOrdinal\":1", "\"nextActionOrdinal\":true")
            .getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_014,
        "T-SNAP-BOOLEAN-TOP-LEVEL-TYPE");

    OfflineI1Oracle.RunState completed = runFixedScenario("OFF-SYN-017");
    String fullText =
        new String(OfflineI1Oracle.encodeSnapshot(completed), StandardCharsets.UTF_8);
    expectSnapshotInvalid(
        fullText.replaceFirst("\"attemptCount\":0", "\"attemptCount\":false")
            .getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_014,
        "T-SNAP-BOOLEAN-NESTED-TYPE");
    expectSnapshotInvalid(
        text.replace("\"manualRetryGrantCount\":0,", "")
            .getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-MISSING-TOP-LEVEL-KEY");
    expectSnapshotInvalid(
        text.replace(",\"queue\":\"LOCAL_ONLY\"", "")
            .getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-MISSING-NESTED-STATE-KEY");
    expectSnapshotInvalid(
        fullText.replaceFirst(",\"contentFreeErrorCode\":null", "")
            .getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-MISSING-NESTED-FLOW-KEY");
    expectSnapshotInvalid(
        Arrays.copyOf(canonical, canonical.length - 1),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-TRUNCATED-CONTAINER");
    expectSnapshotInvalid(
        (text.substring(0, text.length() - 1) + ",}").getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-TRAILING-COMMA");
    expectSnapshotInvalid(
        text.replace("poc-offline-readiness-stage0-v0.1", "\\u" + "D800")
            .getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-SNAP-ESCAPED-SURROGATE");

    OfflineI1Oracle.RunState restorePrior = OfflineI1Oracle.startScenario("OFF-SYN-003");
    OfflineI1Oracle.Step malformedRestore =
        OfflineI1Oracle.execute(restorePrior, Arrays.copyOf(canonical, canonical.length - 1));
    assertInvalidStepUnchanged(
        malformedRestore,
        restorePrior,
        OfflineI1Oracle.Diagnostic.INVALID_013,
        "T-RESTORE-MALFORMED");
    OfflineI1Oracle.Step oversizeRestore =
        OfflineI1Oracle.execute(restorePrior, overByteLimit);
    assertInvalidStepUnchanged(
        oversizeRestore,
        restorePrior,
        OfflineI1Oracle.Diagnostic.INVALID_016,
        "T-RESTORE-SIZE-OVER");
    OfflineI1Oracle.Step depthRestore =
        OfflineI1Oracle.execute(restorePrior, depth33Bytes);
    assertInvalidStepUnchanged(
        depthRestore,
        restorePrior,
        OfflineI1Oracle.Diagnostic.INVALID_016,
        "T-RESTORE-DEPTH-OVER");
    equalBytes(overByteLimit, overByteLimitBefore, "T-RESTORE-SIZE-INPUT-UNCHANGED");
    equalBytes(depth33Bytes, depth33BytesBefore, "T-RESTORE-DEPTH-INPUT-UNCHANGED");
    check(
        !new String(OfflineI1Oracle.encodeSnapshot(oversizeRestore.runState()), StandardCharsets.UTF_8)
            .contains(OfflineI1Oracle.Diagnostic.INVALID_016.code()),
        "T-RESTORE-016-NOT-SERIALIZED-SIZE");
    check(
        !new String(OfflineI1Oracle.encodeSnapshot(depthRestore.runState()), StandardCharsets.UTF_8)
            .contains(OfflineI1Oracle.Diagnostic.INVALID_016.code()),
        "T-RESTORE-016-NOT-SERIALIZED-DEPTH");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_015,
        () ->
            new OfflineI1Oracle.RunState(
                restorePrior.scenarioId(),
                restorePrior.nextActionOrdinal(),
                restorePrior.monotonicOffsetMs(),
                restorePrior.budget(),
                restorePrior.stateVector(),
                restorePrior.flowLedger(),
                restorePrior.queueLedger(),
                restorePrior.replayRecords(),
                depthRestore.result()),
        "T-RESTORE-016-RESULT-CANNOT-BECOME-RUNSTATE");
  }

  private static void testGraphClassifierPrecedence() {
    OfflineI1Oracle.RunState source = runFixedScenario("OFF-SYN-017");
    String sourceText =
        new String(OfflineI1Oracle.encodeSnapshot(source), StandardCharsets.UTF_8);
    OfflineI1Oracle.FlowRow first = source.flowLedger().get(0);
    OfflineI1Oracle.FlowRow tail = source.flowLedger().get(source.flowLedger().size() - 1);
    String unrelatedDigest =
        OfflineI1Oracle.stateVectorDigest(OfflineI1Oracle.startScenario("OFF-SYN-001").stateVector());

    String rawWrongAction =
        replaceFirstLiteral(
            sourceText,
            "\"actionId\":\"" + first.actionId() + "\"",
            "\"actionId\":\"OFF-I1-ACT-001-01\"",
            "T-SNAP-RAW-CONTEXT-ACTION-FIXTURE");
    rawWrongAction =
        replaceFirstLiteral(
            rawWrongAction,
            "\"preStateDigest\":\"" + first.preStateDigest() + "\"",
            "\"preStateDigest\":\"" + unrelatedDigest + "\"",
            "T-SNAP-RAW-CONTEXT-ACTION-DIGEST-FIXTURE");
    expectSnapshotInvalid(
        rawWrongAction.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_003,
        "T-SNAP-RAW-CONTEXT-ACTION-BEFORE-STATE");

    OfflineI1Oracle.FlowRow typedWrongAction =
        copyFlowWithCatalog(first, first.sequence(), "OFF-I1-ACT-001-01", first.outcome(), first.monotonicOffsetMs());
    expectGraphInvalid(
        source,
        replaceAt(source.flowLedger(), 0, typedWrongAction),
        source.queueLedger(),
        source.replayRecords(),
        OfflineI1Oracle.Diagnostic.INVALID_003,
        "T-SNAP-TYPED-CONTEXT-ACTION");

    String rawWrongSequence =
        replaceFirstLiteral(
            sourceText,
            "\"sequence\":1",
            "\"sequence\":2",
            "T-SNAP-RAW-SEQUENCE-FIXTURE");
    rawWrongSequence =
        replaceFirstLiteral(
            rawWrongSequence,
            "\"preStateDigest\":\"" + first.preStateDigest() + "\"",
            "\"preStateDigest\":\"" + unrelatedDigest + "\"",
            "T-SNAP-RAW-SEQUENCE-DIGEST-FIXTURE");
    expectSnapshotInvalid(
        rawWrongSequence.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_003,
        "T-SNAP-RAW-SEQUENCE-BEFORE-STATE");
    OfflineI1Oracle.FlowRow typedWrongSequence =
        copyFlowWithCatalog(first, 2, first.actionId(), first.outcome(), first.monotonicOffsetMs());
    expectGraphInvalid(
        source,
        replaceAt(source.flowLedger(), 0, typedWrongSequence),
        source.queueLedger(),
        source.replayRecords(),
        OfflineI1Oracle.Diagnostic.INVALID_003,
        "T-SNAP-TYPED-SEQUENCE");

    String rawNestedWrongAction =
        replaceLastLiteral(
            sourceText,
            "\"actionId\":\"" + tail.actionId() + "\"",
            "\"actionId\":\"OFF-I1-ACT-001-01\"",
            "T-SNAP-RAW-NESTED-CONTEXT-ACTION-FIXTURE");
    rawNestedWrongAction =
        replaceLastLiteral(
            rawNestedWrongAction,
            "\"preStateDigest\":\"" + tail.preStateDigest() + "\"",
            "\"preStateDigest\":\"" + unrelatedDigest + "\"",
            "T-SNAP-RAW-NESTED-CONTEXT-DIGEST-FIXTURE");
    expectSnapshotInvalid(
        rawNestedWrongAction.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_003,
        "T-SNAP-RAW-NESTED-CONTEXT-BEFORE-STATE");
    OfflineI1Oracle.FlowRow typedNestedWrongAction =
        copyFlowWithCatalog(
            tail, tail.sequence(), "OFF-I1-ACT-001-01", tail.outcome(), tail.monotonicOffsetMs());
    OfflineI1Oracle.Result typedNestedWrongResult =
        resultWith(
            source.lastResult(),
            typedNestedWrongAction,
            source.lastResult().typedLedgerDeltas().queueAppend());
    expectGraphInvalid(
        source,
        source.budget(),
        source.stateVector(),
        source.flowLedger(),
        source.queueLedger(),
        source.replayRecords(),
        typedNestedWrongResult,
        OfflineI1Oracle.Diagnostic.INVALID_003,
        "T-SNAP-TYPED-NESTED-CONTEXT-ACTION");

    OfflineI1Oracle.FlowRow second = source.flowLedger().get(1);
    OfflineI1Oracle.FlowRow decreasingOffset =
        copyFlowWithCatalog(second, second.sequence(), second.actionId(), second.outcome(), 0);
    expectGraphInvalid(
        source,
        replaceAt(source.flowLedger(), 1, decreasingOffset),
        source.queueLedger(),
        source.replayRecords(),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-TYPED-OFFSET-RELATION");
    OfflineI1Oracle.FlowRow wrongOutcome =
        copyFlowWithCatalog(
            first,
            first.sequence(),
            first.actionId(),
            OfflineI1Oracle.ReducerOutcome.NO_STATE_CHANGE,
            first.monotonicOffsetMs());
    expectGraphInvalid(
        source,
        replaceAt(source.flowLedger(), 0, wrongOutcome),
        source.queueLedger(),
        source.replayRecords(),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-TYPED-OUTCOME-RELATION");

    OfflineI1Oracle.StateVector wrongCurrent =
        new OfflineI1Oracle.StateVector(
            source.stateVector().local(),
            source.stateVector().processingCapability(),
            OfflineI1Oracle.ConnectivityState.NETWORK_DENIED,
            source.stateVector().model(),
            source.stateVector().queue());
    String rawWrongCurrent =
        replaceFirstLiteral(
            sourceText,
            stateJson(source.stateVector()),
            stateJson(wrongCurrent),
            "T-SNAP-RAW-CURRENT-STATE-FIXTURE");
    rawWrongCurrent =
        replaceFirstLiteral(
            rawWrongCurrent,
            "\"nextActionOrdinal\":" + source.nextActionOrdinal(),
            "\"nextActionOrdinal\":\"bad\"",
            "T-SNAP-RAW-CURRENT-STATE-ORDINAL-FIXTURE");
    expectSnapshotInvalid(
        rawWrongCurrent.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-RAW-CURRENT-STATE-BEFORE-GENERIC");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_010,
        () ->
            new OfflineI1Oracle.RunState(
                source.scenarioId(),
                0,
                source.monotonicOffsetMs(),
                source.budget(),
                wrongCurrent,
                source.flowLedger(),
                source.queueLedger(),
                source.replayRecords(),
                source.lastResult()),
        "T-SNAP-TYPED-CURRENT-STATE-BEFORE-ORDINAL");

    String nullFirstFlow = replaceFirstFlowWithNull(sourceText, "T-SNAP-NULL-FIRST-FLOW-FIXTURE");
    String rawNullThenDigest =
        replaceLiteralAfter(
            nullFirstFlow,
            "\"flowLedger\":[null,{\"sequence\":2",
            "\"preStateDigest\":\"" + second.preStateDigest() + "\"",
            "\"preStateDigest\":\"" + unrelatedDigest + "\"",
            "T-SNAP-RAW-NULL-THEN-DIGEST-FIXTURE");
    expectSnapshotInvalid(
        rawNullThenDigest.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-RAW-NULL-THEN-DIGEST");
    String rawNullThenAction =
        replaceLiteralAfter(
            nullFirstFlow,
            "\"flowLedger\":[null,{\"sequence\":2",
            "\"actionId\":\"" + second.actionId() + "\"",
            "\"actionId\":\"OFF-I1-ACT-001-01\"",
            "T-SNAP-RAW-NULL-THEN-ACTION-FIXTURE");
    expectSnapshotInvalid(
        rawNullThenAction.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_003,
        "T-SNAP-RAW-NULL-THEN-ACTION");

    List<OfflineI1Oracle.FlowRow> typedNullThenDigest = new ArrayList<>(source.flowLedger());
    typedNullThenDigest.set(0, null);
    typedNullThenDigest.set(
        1,
        new OfflineI1Oracle.FlowRow(
            second.sequence(),
            second.scenarioId(),
            second.actionId(),
            second.preStateVector(),
            second.postStateVector(),
            second.outcome(),
            second.monotonicOffsetMs(),
            unrelatedDigest,
            second.postStateDigest(),
            second.processingRequestIdHash(),
            second.queueIntentIdHash(),
            second.contentFreeErrorCode()));
    expectGraphInvalid(
        source,
        typedNullThenDigest,
        source.queueLedger(),
        source.replayRecords(),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-TYPED-NULL-THEN-DIGEST");
    List<OfflineI1Oracle.FlowRow> typedNullThenAction = new ArrayList<>(source.flowLedger());
    typedNullThenAction.set(0, null);
    typedNullThenAction.set(
        1,
        copyFlowWithCatalog(
            second,
            second.sequence(),
            "OFF-I1-ACT-001-01",
            second.outcome(),
            second.monotonicOffsetMs()));
    expectGraphInvalid(
        source,
        typedNullThenAction,
        source.queueLedger(),
        source.replayRecords(),
        OfflineI1Oracle.Diagnostic.INVALID_003,
        "T-SNAP-TYPED-NULL-THEN-ACTION");

    String rawSequenceTypeAndAction =
        replaceFirstLiteral(
            sourceText,
            "\"sequence\":1",
            "\"sequence\":\"bad\"",
            "T-SNAP-RAW-SEQUENCE-TYPE-FIXTURE");
    rawSequenceTypeAndAction =
        replaceFirstLiteral(
            rawSequenceTypeAndAction,
            "\"actionId\":\"" + first.actionId() + "\"",
            "\"actionId\":\"OFF-I1-ACT-001-01\"",
            "T-SNAP-RAW-SEQUENCE-TYPE-ACTION-FIXTURE");
    expectSnapshotInvalid(
        rawSequenceTypeAndAction.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_003,
        "T-SNAP-RAW-SEQUENCE-TYPE-AND-ACTION");
    String rawBigSequenceAndDigest =
        replaceFirstLiteral(
            sourceText,
            "\"sequence\":1",
            "\"sequence\":999999999999999999999999999999999999",
            "T-SNAP-RAW-BIG-SEQUENCE-FIXTURE");
    rawBigSequenceAndDigest =
        replaceFirstLiteral(
            rawBigSequenceAndDigest,
            "\"preStateDigest\":\"" + first.preStateDigest() + "\"",
            "\"preStateDigest\":\"" + unrelatedDigest + "\"",
            "T-SNAP-RAW-BIG-SEQUENCE-DIGEST-FIXTURE");
    expectSnapshotInvalid(
        rawBigSequenceAndDigest.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-RAW-BIG-SEQUENCE-AND-DIGEST");
  }

  private static void testOffsetOverflow() {
    OfflineI1Oracle.RunState fresh = OfflineI1Oracle.startScenario("OFF-SYN-001");
    OfflineI1Oracle.RunState maximum =
        new OfflineI1Oracle.RunState(
            fresh.scenarioId(),
            fresh.nextActionOrdinal(),
            Long.MAX_VALUE,
            fresh.budget(),
            fresh.stateVector(),
            fresh.flowLedger(),
            fresh.queueLedger(),
            fresh.replayRecords(),
            fresh.lastResult());
    assertInvalidStepUnchanged(
        OfflineI1Oracle.execute(maximum),
        maximum,
        OfflineI1Oracle.Diagnostic.INVALID_014,
        "T-RUN-OFFSET-MAX-TYPED");
    OfflineI1Oracle.RunState decoded =
        OfflineI1Oracle.decodeSnapshot(OfflineI1Oracle.encodeSnapshot(maximum));
    assertInvalidStepUnchanged(
        OfflineI1Oracle.execute(decoded),
        decoded,
        OfflineI1Oracle.Diagnostic.INVALID_014,
        "T-RUN-OFFSET-MAX-DECODED");
  }

  private static OfflineI1Oracle.RunState withFlowOffsets(
      OfflineI1Oracle.RunState source, long[] offsets) {
    check(source.flowLedger().size() == offsets.length, "T-SNAP-OFFSET-COUNT");
    List<OfflineI1Oracle.FlowRow> flow = new ArrayList<>();
    for (int index = 0; index < offsets.length; index++) {
      OfflineI1Oracle.FlowRow row = source.flowLedger().get(index);
      flow.add(
          new OfflineI1Oracle.FlowRow(
              row.sequence(),
              row.scenarioId(),
              row.actionId(),
              row.preStateVector(),
              row.postStateVector(),
              row.outcome(),
              offsets[index],
              row.preStateDigest(),
              row.postStateDigest(),
              row.processingRequestIdHash(),
              row.queueIntentIdHash(),
              row.contentFreeErrorCode()));
    }
    OfflineI1Oracle.Result last = source.lastResult();
    OfflineI1Oracle.Result adjusted =
        last == null
            ? null
            : new OfflineI1Oracle.Result(
                last.selectedRuleId(),
                last.outcome(),
                last.postStateVector(),
                new OfflineI1Oracle.TypedLedgerDeltas(
                    flow.get(flow.size() - 1),
                    last.typedLedgerDeltas().queueAppend(),
                    last.typedLedgerDeltas().diagnosticCode()),
                last.effectCount(),
                last.applyCount(),
                last.remoteDeletionEffectCount());
    long top = offsets.length == 0 ? source.monotonicOffsetMs() : offsets[offsets.length - 1];
    return new OfflineI1Oracle.RunState(
        source.scenarioId(),
        source.nextActionOrdinal(),
        top,
        source.budget(),
        source.stateVector(),
        flow,
        source.queueLedger(),
        source.replayRecords(),
        adjusted);
  }

  private static void testSnapshotGraphAdversaries() {
    OfflineI1Oracle.RunState scenarioOne = runFixedScenario("OFF-SYN-001");
    OfflineI1Oracle.FlowRow first = scenarioOne.flowLedger().get(0);
    OfflineI1Oracle.StateVector wrongInitial =
        new OfflineI1Oracle.StateVector(
            OfflineI1Oracle.LocalState.LOCAL_READY,
            first.preStateVector().processingCapability(),
            first.preStateVector().connectivity(),
            first.preStateVector().model(),
            first.preStateVector().queue());
    OfflineI1Oracle.FlowRow wrongFirst =
        copyFlow(
            first,
            wrongInitial,
            first.postStateVector(),
            first.processingRequestIdHash(),
            first.queueIntentIdHash());
    expectGraphInvalid(
        scenarioOne,
        replaceAt(scenarioOne.flowLedger(), 0, wrongFirst),
        scenarioOne.queueLedger(),
        scenarioOne.replayRecords(),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-FIRST-PRESTATE-MISMATCH");

    OfflineI1Oracle.RunState scenarioNine = runFixedScenario("OFF-SYN-009");
    OfflineI1Oracle.FlowRow processing = scenarioNine.flowLedger().get(0);
    expectGraphInvalid(
        scenarioNine,
        replaceAt(
            scenarioNine.flowLedger(),
            0,
            copyFlow(
                processing,
                processing.preStateVector(),
                processing.postStateVector(),
                null,
                processing.queueIntentIdHash())),
        scenarioNine.queueLedger(),
        scenarioNine.replayRecords(),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-PROCESSING-HASH-NULLABILITY");

    OfflineI1Oracle.RunState scenarioFourteen = runFixedScenario("OFF-SYN-014");
    OfflineI1Oracle.FlowRow projected = scenarioFourteen.flowLedger().get(0);
    expectGraphInvalid(
        scenarioFourteen,
        replaceAt(
            scenarioFourteen.flowLedger(),
            0,
            copyFlow(
                projected,
                projected.preStateVector(),
                projected.postStateVector(),
                projected.processingRequestIdHash(),
                null)),
        scenarioFourteen.queueLedger(),
        scenarioFourteen.replayRecords(),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-PROJECTED-QUEUE-HASH");

    OfflineI1Oracle.QueueRow historicalQueue = scenarioFourteen.queueLedger().get(0);
    String otherValidLocalDigest =
        OfflineI1Oracle.localStateDigest(projected.postStateVector().local());
    check(
        !otherValidLocalDigest.equals(historicalQueue.preLocalStateDigest()),
        "T-SNAP-SWAPPED-LOCAL-DIGEST-FIXTURE");
    OfflineI1Oracle.QueueRow swappedLocalDigest =
        new OfflineI1Oracle.QueueRow(
            historicalQueue.operationClass(),
            historicalQueue.intentIdHash(),
            historicalQueue.logicalKeyHash(),
            historicalQueue.jobIdHash(),
            historicalQueue.resultIdHash(),
            historicalQueue.deletionScopeDigest(),
            historicalQueue.deletionIdHash(),
            historicalQueue.deletionReceiptIdHash(),
            historicalQueue.queueState(),
            historicalQueue.deletionSubstatus(),
            historicalQueue.contentFreeDeletionErrorCode(),
            historicalQueue.deletionReceiptVerificationOutcome(),
            historicalQueue.attemptCount(),
            historicalQueue.replayMarker(),
            historicalQueue.effectCount(),
            historicalQueue.applyCount(),
            historicalQueue.remoteDeletionEffectCount(),
            otherValidLocalDigest,
            otherValidLocalDigest);
    expectGraphInvalid(
        scenarioFourteen,
        scenarioFourteen.flowLedger(),
        List.of(swappedLocalDigest),
        scenarioFourteen.replayRecords(),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-IN-GRAPH-SWAPPED-LOCAL-DIGEST");
    String scenarioFourteenText =
        new String(OfflineI1Oracle.encodeSnapshot(scenarioFourteen), StandardCharsets.UTF_8);
    String swappedLocalDigestText =
        scenarioFourteenText.replaceFirst(
            "\"preLocalStateDigest\":\"" + historicalQueue.preLocalStateDigest() + "\"",
            "\"preLocalStateDigest\":\"" + otherValidLocalDigest + "\"");
    check(
        !swappedLocalDigestText.equals(scenarioFourteenText),
        "T-SNAP-SWAPPED-LOCAL-RAW-FIXTURE");
    String swappedLocalBeforeGeneric =
        swappedLocalDigestText.replace(
            "\"nextActionOrdinal\":" + scenarioFourteen.nextActionOrdinal(),
            "\"nextActionOrdinal\":\"" + scenarioFourteen.nextActionOrdinal() + "\"");
    expectSnapshotInvalid(
        swappedLocalBeforeGeneric.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-SWAPPED-LOCAL-BEFORE-GENERIC");
    String swappedLocalBeforeSameRowOverflow =
        swappedLocalDigestText.replaceFirst(
            "\\\"attemptCount\\\":\\d+",
            "\\\"attemptCount\\\":999999999999999999999999999999999999");
    expectSnapshotInvalid(
        swappedLocalBeforeSameRowOverflow.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-SWAPPED-LOCAL-BEFORE-SAME-ROW-OVERFLOW");
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_010,
        () ->
            new OfflineI1Oracle.RunState(
                scenarioFourteen.scenarioId(),
                0,
                scenarioFourteen.monotonicOffsetMs(),
                scenarioFourteen.budget(),
                scenarioFourteen.stateVector(),
                scenarioFourteen.flowLedger(),
                List.of(swappedLocalDigest),
                scenarioFourteen.replayRecords(),
                scenarioFourteen.lastResult()),
        "T-SNAP-TYPED-STATE-RELATION-BEFORE-ORDINAL");

    OfflineI1Oracle.ReplayRecord historicalReplay = scenarioFourteen.replayRecords().get(0);
    String otherValidStateDigest =
        OfflineI1Oracle.stateVectorDigest(projected.postStateVector());
    check(
        !otherValidStateDigest.equals(historicalReplay.postStateDigest()),
        "T-SNAP-SWAPPED-REPLAY-DIGEST-FIXTURE");
    OfflineI1Oracle.ReplayRecord swappedReplayDigest =
        new OfflineI1Oracle.ReplayRecord(
            historicalReplay.logicalKeyHash(),
            historicalReplay.canonicalInputDigest(),
            historicalReplay.selectedRuleId(),
            historicalReplay.outcome(),
            otherValidStateDigest,
            historicalReplay.resultIdHash(),
            historicalReplay.replayMarker(),
            historicalReplay.attemptCount(),
            historicalReplay.effectCount(),
            historicalReplay.applyCount(),
            historicalReplay.remoteDeletionEffectCount());
    expectGraphInvalid(
        scenarioFourteen,
        scenarioFourteen.flowLedger(),
        scenarioFourteen.queueLedger(),
        List.of(swappedReplayDigest),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-IN-GRAPH-SWAPPED-REPLAY-DIGEST");
    String swappedReplayDigestText =
        scenarioFourteenText.replaceFirst(
            "(\"replayRecords\":\\[\\{[^}]*\"postStateDigest\":)\""
                + historicalReplay.postStateDigest()
                + "\"",
            "$1\"" + otherValidStateDigest + "\"");
    check(
        !swappedReplayDigestText.equals(scenarioFourteenText),
        "T-SNAP-SWAPPED-REPLAY-RAW-FIXTURE");
    String swappedReplayBeforeRelationship =
        swappedReplayDigestText.replace(
            "\"nextActionOrdinal\":" + scenarioFourteen.nextActionOrdinal(),
            "\"nextActionOrdinal\":" + (scenarioFourteen.nextActionOrdinal() - 1));
    expectSnapshotInvalid(
        swappedReplayBeforeRelationship.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-SWAPPED-REPLAY-BEFORE-RELATIONSHIP");
    String swappedReplayBeforeSameRowOverflow =
        swappedReplayDigestText.replaceFirst(
            "(\\\"replayRecords\\\":\\[\\{[^}]*\\\"attemptCount\\\":)\\d+",
            "$1" + "999999999999999999999999999999999999");
    expectSnapshotInvalid(
        swappedReplayBeforeSameRowOverflow.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-SWAPPED-REPLAY-BEFORE-SAME-ROW-OVERFLOW");

    OfflineI1Oracle.RunState scenarioSeventeen = runFixedScenario("OFF-SYN-017");
    OfflineI1Oracle.QueueRow firstAppend = scenarioSeventeen.queueLedger().get(1);
    expectGraphInvalid(
        scenarioSeventeen,
        scenarioSeventeen.flowLedger(),
        replaceAt(
            scenarioSeventeen.queueLedger(),
            1,
            copyQueue(
                firstAppend,
                firstAppend.intentIdHash(),
                firstAppend.jobIdHash(),
                firstAppend.effectCount(),
                OfflineI1Oracle.QueueState.WAITING_NETWORK)),
        scenarioSeventeen.replayRecords(),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-QUEUE-FLOW-STATE-MISMATCH");

    String driftIntent = OfflineI1Oracle.queueIntentIdHash("intent.graph.drift");
    OfflineI1Oracle.QueueRow secondAppend = scenarioSeventeen.queueLedger().get(2);
    OfflineI1Oracle.FlowRow secondFlow = scenarioSeventeen.flowLedger().get(1);
    expectGraphInvalid(
        scenarioSeventeen,
        replaceAt(
            scenarioSeventeen.flowLedger(),
            1,
            copyFlow(
                secondFlow,
                secondFlow.preStateVector(),
                secondFlow.postStateVector(),
                secondFlow.processingRequestIdHash(),
                driftIntent)),
        replaceAt(
            scenarioSeventeen.queueLedger(),
            2,
            copyQueue(
                secondAppend,
                driftIntent,
                secondAppend.jobIdHash(),
                secondAppend.effectCount(),
                secondAppend.queueState())),
        scenarioSeventeen.replayRecords(),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-LOGICAL-IDENTITY-DRIFT");

    String prematureJob =
        OfflineI1Oracle.jobIdHash(firstAppend.logicalKeyHash(), "job.premature");
    expectGraphInvalid(
        scenarioSeventeen,
        scenarioSeventeen.flowLedger(),
        replaceAt(
            scenarioSeventeen.queueLedger(),
            1,
            copyQueue(
                firstAppend,
                firstAppend.intentIdHash(),
                prematureJob,
                1,
                firstAppend.queueState())),
        scenarioSeventeen.replayRecords(),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-PREACCEPTANCE-JOB");

    OfflineI1Oracle.ReplayRecord replay = scenarioSeventeen.replayRecords().get(0);
    String otherDigest =
        OfflineI1Oracle.stateVectorDigest(
            OfflineI1Oracle.startScenario("OFF-SYN-001").stateVector());
    for (int variant = 0; variant < 3; variant++) {
      OfflineI1Oracle.ReplayRecord corrupt =
          new OfflineI1Oracle.ReplayRecord(
              replay.logicalKeyHash(),
              replay.canonicalInputDigest(),
              variant == 1 ? "OFF-I1-RULE-Q-012" : replay.selectedRuleId(),
              replay.outcome(),
              variant == 0 ? otherDigest : replay.postStateDigest(),
              replay.resultIdHash(),
              variant == 2
                  ? OfflineI1Oracle.ReplayMarker.SAME_INPUT_REPLAY
                  : replay.replayMarker(),
              replay.attemptCount(),
              replay.effectCount(),
              replay.applyCount(),
              replay.remoteDeletionEffectCount());
      expectGraphInvalid(
          scenarioSeventeen,
          scenarioSeventeen.flowLedger(),
          scenarioSeventeen.queueLedger(),
          List.of(corrupt),
          variant == 0
              ? OfflineI1Oracle.Diagnostic.INVALID_010
              : OfflineI1Oracle.Diagnostic.INVALID_015,
          "T-SNAP-REPLAY-DRIFT-" + variant);
    }

    OfflineI1Oracle.RunState scenarioEighteen = runFixedScenario("OFF-SYN-018");
    String q19Text = new String(OfflineI1Oracle.encodeSnapshot(scenarioEighteen), StandardCharsets.UTF_8);
    String q19AttemptDrift =
        q19Text
            .replace(
                "\"remainingAutomaticAttemptBudget\":1,\"manualRetryGrantCount\":0",
                "\"remainingAutomaticAttemptBudget\":2,\"manualRetryGrantCount\":0")
            .replace("\"attemptCount\":2", "\"attemptCount\":1");
    check(!q19AttemptDrift.equals(q19Text), "T-SNAP-Q019-ATTEMPT-DRIFT-FIXTURE");
    expectSnapshotInvalid(
        q19AttemptDrift.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-Q019-ATTEMPT-COHERENT-DRIFT");

    String q19MarkerDrift =
        q19Text.replace("\"replayMarker\":\"SAME_INPUT_REPLAY\"", "\"replayMarker\":\"ORIGINAL\"");
    check(!q19MarkerDrift.equals(q19Text), "T-SNAP-Q019-MARKER-DRIFT-FIXTURE");
    expectSnapshotInvalid(
        q19MarkerDrift.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-Q019-MARKER-COHERENT-DRIFT");

    String q17Text = new String(OfflineI1Oracle.encodeSnapshot(scenarioSeventeen), StandardCharsets.UTF_8);
    OfflineI1Oracle.FlowRow chainHead = scenarioSeventeen.flowLedger().get(0);
    OfflineI1Oracle.StateVector wrongChainPre =
        new OfflineI1Oracle.StateVector(
            chainHead.preStateVector().local(),
            chainHead.preStateVector().processingCapability(),
            OfflineI1Oracle.ConnectivityState.NETWORK_DENIED,
            chainHead.preStateVector().model(),
            chainHead.preStateVector().queue());
    check(!wrongChainPre.equals(chainHead.preStateVector()), "T-SNAP-FLOW-CHAIN-FIXTURE");
    String rawChainMismatch =
        replaceFirstLiteral(
            q17Text,
            stateJson(chainHead.preStateVector()),
            stateJson(wrongChainPre),
            "T-SNAP-RAW-FLOW-CHAIN-STATE-FIXTURE");
    rawChainMismatch =
        replaceFirstLiteral(
            rawChainMismatch,
            "\"preStateDigest\":\"" + chainHead.preStateDigest() + "\"",
            "\"preStateDigest\":\"" + OfflineI1Oracle.stateVectorDigest(wrongChainPre) + "\"",
            "T-SNAP-RAW-FLOW-CHAIN-DIGEST-FIXTURE");
    rawChainMismatch =
        replaceFirstLiteral(
            rawChainMismatch,
            "\"nextActionOrdinal\":" + scenarioSeventeen.nextActionOrdinal(),
            "\"nextActionOrdinal\":\"bad\"",
            "T-SNAP-RAW-FLOW-CHAIN-ORDINAL-FIXTURE");
    expectSnapshotInvalid(
        rawChainMismatch.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-RAW-FLOW-CHAIN-BEFORE-GENERIC");
    OfflineI1Oracle.FlowRow typedChainMismatch =
        copyFlow(
            chainHead,
            wrongChainPre,
            chainHead.postStateVector(),
            chainHead.processingRequestIdHash(),
            chainHead.queueIntentIdHash());
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_010,
        () ->
            new OfflineI1Oracle.RunState(
                scenarioSeventeen.scenarioId(),
                0,
                scenarioSeventeen.monotonicOffsetMs(),
                scenarioSeventeen.budget(),
                scenarioSeventeen.stateVector(),
                replaceAt(scenarioSeventeen.flowLedger(), 0, typedChainMismatch),
                scenarioSeventeen.queueLedger(),
                scenarioSeventeen.replayRecords(),
                scenarioSeventeen.lastResult()),
        "T-SNAP-TYPED-FLOW-CHAIN-BEFORE-ORDINAL");

    OfflineI1Oracle.Result nestedSource = scenarioSeventeen.lastResult();
    OfflineI1Oracle.FlowRow nestedFlow = nestedSource.typedLedgerDeltas().flowAppend();
    check(
        !otherValidStateDigest.equals(nestedFlow.preStateDigest()),
        "T-SNAP-NESTED-FLOW-DIGEST-FIXTURE");
    String rawNestedFlowDigest =
        replaceLastLiteral(
            q17Text,
            "\"preStateDigest\":\"" + nestedFlow.preStateDigest() + "\"",
            "\"preStateDigest\":\"" + otherValidStateDigest + "\"",
            "T-SNAP-RAW-NESTED-FLOW-DIGEST-FIXTURE");
    rawNestedFlowDigest =
        replaceFirstLiteral(
            rawNestedFlowDigest,
            "\"nextActionOrdinal\":" + scenarioSeventeen.nextActionOrdinal(),
            "\"nextActionOrdinal\":\"bad\"",
            "T-SNAP-RAW-NESTED-FLOW-ORDINAL-FIXTURE");
    expectSnapshotInvalid(
        rawNestedFlowDigest.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-RAW-NESTED-FLOW-BEFORE-GENERIC");
    OfflineI1Oracle.FlowRow typedNestedFlow =
        new OfflineI1Oracle.FlowRow(
            nestedFlow.sequence(),
            nestedFlow.scenarioId(),
            nestedFlow.actionId(),
            nestedFlow.preStateVector(),
            nestedFlow.postStateVector(),
            nestedFlow.outcome(),
            nestedFlow.monotonicOffsetMs(),
            otherValidStateDigest,
            nestedFlow.postStateDigest(),
            nestedFlow.processingRequestIdHash(),
            nestedFlow.queueIntentIdHash(),
            nestedFlow.contentFreeErrorCode());
    OfflineI1Oracle.Result typedNestedFlowResult =
        new OfflineI1Oracle.Result(
            nestedSource.selectedRuleId(),
            nestedSource.outcome(),
            nestedSource.postStateVector(),
            new OfflineI1Oracle.TypedLedgerDeltas(
                typedNestedFlow,
                nestedSource.typedLedgerDeltas().queueAppend(),
                nestedSource.typedLedgerDeltas().diagnosticCode()),
            nestedSource.effectCount(),
            nestedSource.applyCount(),
            nestedSource.remoteDeletionEffectCount());
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_010,
        () ->
            new OfflineI1Oracle.RunState(
                scenarioSeventeen.scenarioId(),
                0,
                scenarioSeventeen.monotonicOffsetMs(),
                scenarioSeventeen.budget(),
                scenarioSeventeen.stateVector(),
                scenarioSeventeen.flowLedger(),
                scenarioSeventeen.queueLedger(),
                scenarioSeventeen.replayRecords(),
                typedNestedFlowResult),
        "T-SNAP-TYPED-NESTED-FLOW-BEFORE-ORDINAL");

    OfflineI1Oracle.QueueRow nestedQueue = nestedSource.typedLedgerDeltas().queueAppend();
    String unrelatedNestedLocal =
        OfflineI1Oracle.localStateDigest(OfflineI1Oracle.LocalState.FRESH_LOCAL_DEFAULT);
    check(
        !unrelatedNestedLocal.equals(nestedQueue.preLocalStateDigest()),
        "T-SNAP-NESTED-QUEUE-LOCAL-FIXTURE");
    String rawNestedQueueLocal =
        replaceLastLiteral(
            q17Text,
            "\"preLocalStateDigest\":\"" + nestedQueue.preLocalStateDigest() + "\"",
            "\"preLocalStateDigest\":\"" + unrelatedNestedLocal + "\"",
            "T-SNAP-RAW-NESTED-QUEUE-LOCAL-FIXTURE");
    rawNestedQueueLocal =
        replaceFirstLiteral(
            rawNestedQueueLocal,
            "\"nextActionOrdinal\":" + scenarioSeventeen.nextActionOrdinal(),
            "\"nextActionOrdinal\":\"bad\"",
            "T-SNAP-RAW-NESTED-QUEUE-ORDINAL-FIXTURE");
    expectSnapshotInvalid(
        rawNestedQueueLocal.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_010,
        "T-SNAP-RAW-NESTED-QUEUE-BEFORE-GENERIC");
    OfflineI1Oracle.QueueRow typedNestedQueue =
        new OfflineI1Oracle.QueueRow(
            nestedQueue.operationClass(),
            nestedQueue.intentIdHash(),
            nestedQueue.logicalKeyHash(),
            nestedQueue.jobIdHash(),
            nestedQueue.resultIdHash(),
            nestedQueue.deletionScopeDigest(),
            nestedQueue.deletionIdHash(),
            nestedQueue.deletionReceiptIdHash(),
            nestedQueue.queueState(),
            nestedQueue.deletionSubstatus(),
            nestedQueue.contentFreeDeletionErrorCode(),
            nestedQueue.deletionReceiptVerificationOutcome(),
            nestedQueue.attemptCount(),
            nestedQueue.replayMarker(),
            nestedQueue.effectCount(),
            nestedQueue.applyCount(),
            nestedQueue.remoteDeletionEffectCount(),
            unrelatedNestedLocal,
            nestedQueue.postLocalStateDigest());
    OfflineI1Oracle.Result typedNestedQueueResult =
        new OfflineI1Oracle.Result(
            nestedSource.selectedRuleId(),
            nestedSource.outcome(),
            nestedSource.postStateVector(),
            new OfflineI1Oracle.TypedLedgerDeltas(
                nestedFlow,
                typedNestedQueue,
                nestedSource.typedLedgerDeltas().diagnosticCode()),
            nestedSource.effectCount(),
            nestedSource.applyCount(),
            nestedSource.remoteDeletionEffectCount());
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_010,
        () ->
            new OfflineI1Oracle.RunState(
                scenarioSeventeen.scenarioId(),
                0,
                scenarioSeventeen.monotonicOffsetMs(),
                scenarioSeventeen.budget(),
                scenarioSeventeen.stateVector(),
                scenarioSeventeen.flowLedger(),
                scenarioSeventeen.queueLedger(),
                scenarioSeventeen.replayRecords(),
                typedNestedQueueResult),
        "T-SNAP-TYPED-NESTED-QUEUE-BEFORE-ORDINAL");

    String q17GrantDrift =
        q17Text.replace(
            "\"remainingAutomaticAttemptBudget\":2,\"manualRetryGrantCount\":0",
            "\"remainingAutomaticAttemptBudget\":3,\"manualRetryGrantCount\":1");
    check(!q17GrantDrift.equals(q17Text), "T-SNAP-GRANT-DRIFT-FIXTURE");
    expectSnapshotInvalid(
        q17GrantDrift.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-NONDELETE-GRANT-COHERENT-DRIFT");

    String oldJob = scenarioSeventeen.queueLedger().get(3).jobIdHash();
    String oldResult = scenarioSeventeen.queueLedger().get(4).resultIdHash();
    String newJob =
        OfflineI1Oracle.jobIdHash(
            scenarioSeventeen.queueLedger().get(0).logicalKeyHash(), "job.graph.drift");
    String newResult = OfflineI1Oracle.resultIdHash(newJob, "result.graph.drift");
    String q17IdentityDrift = q17Text.replace(oldJob, newJob).replace(oldResult, newResult);
    check(!q17IdentityDrift.equals(q17Text), "T-SNAP-Q011-ID-DRIFT-FIXTURE");
    expectSnapshotInvalid(
        q17IdentityDrift.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-Q011-JOB-RESULT-COHERENT-DRIFT");

    OfflineI1Oracle.RunState scenarioThirteen = runFixedScenario("OFF-SYN-013");
    String q7Text = new String(OfflineI1Oracle.encodeSnapshot(scenarioThirteen), StandardCharsets.UTF_8);
    String coherentJob =
        OfflineI1Oracle.jobIdHash(
            scenarioThirteen.queueLedger().get(0).logicalKeyHash(), "job.coherent.drift");
    String q7EffectDrift =
        q7Text
            .replace("\"jobIdHash\":null", "\"jobIdHash\":\"" + coherentJob + "\"")
            .replace("\"effectCount\":0", "\"effectCount\":1");
    check(!q7EffectDrift.equals(q7Text), "T-SNAP-Q007-EFFECT-DRIFT-FIXTURE");
    expectSnapshotInvalid(
        q7EffectDrift.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-Q007-EFFECT-JOB-COHERENT-DRIFT");

    OfflineI1Oracle.QueueRow q7First = scenarioThirteen.queueLedger().get(0);
    String alternateLogical =
        OfflineI1Oracle.logicalKeyHash(
            OfflineI1Oracle.OperationClass.NON_DELETION_CLOUD_INTENT,
            "logical.coherent.drift");
    String alternateIntent = OfflineI1Oracle.queueIntentIdHash("intent.coherent.drift");
    String alternateInput =
        OfflineI1Oracle.replayInputDigest(
            OfflineI1Oracle.OperationClass.NON_DELETION_CLOUD_INTENT,
            alternateLogical,
            OfflineI1Oracle.InputVariant.PRIMARY,
            null);
    String q7IdentityDrift =
        q7Text
            .replace(q7First.logicalKeyHash(), alternateLogical)
            .replace(q7First.intentIdHash(), alternateIntent)
            .replace(scenarioThirteen.replayRecords().get(0).canonicalInputDigest(), alternateInput);
    check(!q7IdentityDrift.equals(q7Text), "T-SNAP-Q007-ID-DRIFT-FIXTURE");
    expectSnapshotInvalid(
        q7IdentityDrift.getBytes(StandardCharsets.UTF_8),
        OfflineI1Oracle.Diagnostic.INVALID_015,
        "T-SNAP-Q007-IDENTITY-COHERENT-DRIFT");

    OfflineI1Oracle.Result tail = scenarioSeventeen.lastResult();
    expectFault(
        OfflineI1Oracle.Diagnostic.INVALID_015,
        () ->
            new OfflineI1Oracle.Result(
                tail.selectedRuleId(),
                OfflineI1Oracle.ReducerOutcome.REJECTED_NO_STATE_CHANGE,
                tail.postStateVector(),
                tail.typedLedgerDeltas(),
                tail.effectCount(),
                tail.applyCount(),
                tail.remoteDeletionEffectCount()),
        "T-RESULT-OUTCOME-DIAGNOSTIC-TABLE");
    List<OfflineI1Oracle.Result> badResults =
        List.of(
            new OfflineI1Oracle.Result(
                "OFF-I1-RULE-Q-012",
                tail.outcome(),
                tail.postStateVector(),
                tail.typedLedgerDeltas(),
                tail.effectCount(),
                tail.applyCount(),
                tail.remoteDeletionEffectCount()),
            new OfflineI1Oracle.Result(
                tail.selectedRuleId(),
                tail.outcome(),
                tail.postStateVector(),
                new OfflineI1Oracle.TypedLedgerDeltas(
                    scenarioSeventeen.flowLedger().get(0),
                    tail.typedLedgerDeltas().queueAppend(),
                    tail.typedLedgerDeltas().diagnosticCode()),
                tail.effectCount(),
                tail.applyCount(),
                tail.remoteDeletionEffectCount()),
            new OfflineI1Oracle.Result(
                tail.selectedRuleId(),
                tail.outcome(),
                tail.postStateVector(),
                new OfflineI1Oracle.TypedLedgerDeltas(
                    tail.typedLedgerDeltas().flowAppend(),
                    null,
                    tail.typedLedgerDeltas().diagnosticCode()),
                tail.effectCount(),
                tail.applyCount(),
                tail.remoteDeletionEffectCount()),
            new OfflineI1Oracle.Result(
                tail.selectedRuleId(),
                tail.outcome(),
                tail.postStateVector(),
                tail.typedLedgerDeltas(),
                0,
                tail.applyCount(),
                tail.remoteDeletionEffectCount()));
    for (int variant = 0; variant < badResults.size(); variant++) {
      expectGraphInvalid(
          scenarioSeventeen,
          scenarioSeventeen.budget(),
          scenarioSeventeen.stateVector(),
          scenarioSeventeen.flowLedger(),
            scenarioSeventeen.queueLedger(),
            scenarioSeventeen.replayRecords(),
            badResults.get(variant),
            variant == 1
                ? OfflineI1Oracle.Diagnostic.INVALID_003
                : OfflineI1Oracle.Diagnostic.INVALID_015,
            "T-SNAP-LAST-RESULT-DRIFT-" + variant);
    }
  }

  private static OfflineI1Oracle.FlowRow copyFlowWithCatalog(
      OfflineI1Oracle.FlowRow source,
      long sequence,
      String actionId,
      OfflineI1Oracle.ReducerOutcome outcome,
      long monotonicOffsetMs) {
    return new OfflineI1Oracle.FlowRow(
        sequence,
        source.scenarioId(),
        actionId,
        source.preStateVector(),
        source.postStateVector(),
        outcome,
        monotonicOffsetMs,
        source.preStateDigest(),
        source.postStateDigest(),
        source.processingRequestIdHash(),
        source.queueIntentIdHash(),
        source.contentFreeErrorCode());
  }

  private static OfflineI1Oracle.FlowRow copyFlow(
      OfflineI1Oracle.FlowRow source,
      OfflineI1Oracle.StateVector pre,
      OfflineI1Oracle.StateVector post,
      String processingHash,
      String queueHash) {
    return new OfflineI1Oracle.FlowRow(
        source.sequence(),
        source.scenarioId(),
        source.actionId(),
        pre,
        post,
        source.outcome(),
        source.monotonicOffsetMs(),
        OfflineI1Oracle.stateVectorDigest(pre),
        OfflineI1Oracle.stateVectorDigest(post),
        processingHash,
        queueHash,
        source.contentFreeErrorCode());
  }

  private static OfflineI1Oracle.QueueRow copyQueue(
      OfflineI1Oracle.QueueRow source,
      String intentIdHash,
      String jobIdHash,
      int effectCount,
      OfflineI1Oracle.QueueState queueState) {
    return new OfflineI1Oracle.QueueRow(
        source.operationClass(),
        intentIdHash,
        source.logicalKeyHash(),
        jobIdHash,
        source.resultIdHash(),
        source.deletionScopeDigest(),
        source.deletionIdHash(),
        source.deletionReceiptIdHash(),
        queueState,
        source.deletionSubstatus(),
        source.contentFreeDeletionErrorCode(),
        source.deletionReceiptVerificationOutcome(),
        source.attemptCount(),
        source.replayMarker(),
        effectCount,
        source.applyCount(),
        source.remoteDeletionEffectCount(),
        source.preLocalStateDigest(),
        source.postLocalStateDigest());
  }

  private static OfflineI1Oracle.QueueRow copyDeletionQueue(
      OfflineI1Oracle.QueueRow source,
      String intentIdHash,
      String logicalKeyHash,
      String deletionScopeDigest,
      String deletionIdHash,
      String deletionReceiptIdHash,
      long attemptCount,
      OfflineI1Oracle.ReplayMarker replayMarker,
      int effectCount,
      int remoteDeletionEffectCount) {
    return new OfflineI1Oracle.QueueRow(
        OfflineI1Oracle.OperationClass.DELETE_CLOUD_COPY,
        intentIdHash,
        logicalKeyHash,
        null,
        null,
        deletionScopeDigest,
        deletionIdHash,
        deletionReceiptIdHash,
        source.queueState(),
        source.deletionSubstatus(),
        source.contentFreeDeletionErrorCode(),
        source.deletionReceiptVerificationOutcome(),
        attemptCount,
        replayMarker,
        effectCount,
        0,
        remoteDeletionEffectCount,
        source.preLocalStateDigest(),
        source.postLocalStateDigest());
  }

  private static OfflineI1Oracle.ReplayRecord copyReplay(
      OfflineI1Oracle.ReplayRecord source,
      String logicalKeyHash,
      String canonicalInputDigest,
      String selectedRuleId,
      Long attemptCount,
      OfflineI1Oracle.ReplayMarker replayMarker,
      Integer effectCount,
      Integer remoteDeletionEffectCount) {
    return new OfflineI1Oracle.ReplayRecord(
        logicalKeyHash == null ? source.logicalKeyHash() : logicalKeyHash,
        canonicalInputDigest == null
            ? source.canonicalInputDigest()
            : canonicalInputDigest,
        selectedRuleId == null ? source.selectedRuleId() : selectedRuleId,
        source.outcome(),
        source.postStateDigest(),
        source.resultIdHash(),
        replayMarker == null ? source.replayMarker() : replayMarker,
        attemptCount == null ? source.attemptCount() : attemptCount,
        effectCount == null ? source.effectCount() : effectCount,
        source.applyCount(),
        remoteDeletionEffectCount == null
            ? source.remoteDeletionEffectCount()
            : remoteDeletionEffectCount);
  }

  private static OfflineI1Oracle.Result resultWith(
      OfflineI1Oracle.Result source,
      OfflineI1Oracle.FlowRow flow,
      OfflineI1Oracle.QueueRow queue) {
    return new OfflineI1Oracle.Result(
        source.selectedRuleId(),
        source.outcome(),
        source.postStateVector(),
        new OfflineI1Oracle.TypedLedgerDeltas(
            flow, queue, source.typedLedgerDeltas().diagnosticCode()),
        queue.effectCount(),
        queue.applyCount(),
        queue.remoteDeletionEffectCount());
  }

  private static <T> List<T> replaceAt(List<T> source, int index, T value) {
    List<T> copy = new ArrayList<>(source);
    copy.set(index, value);
    return List.copyOf(copy);
  }

  private static void expectGraphInvalid(
      OfflineI1Oracle.RunState source,
      List<OfflineI1Oracle.FlowRow> flow,
      List<OfflineI1Oracle.QueueRow> queue,
      List<OfflineI1Oracle.ReplayRecord> replay,
      OfflineI1Oracle.Diagnostic diagnostic,
      String id) {
    expectGraphInvalid(
        source,
        source.budget(),
        source.stateVector(),
        flow,
        queue,
        replay,
        source.lastResult(),
        diagnostic,
        id);
  }

  private static void expectGraphInvalid(
      OfflineI1Oracle.RunState source,
      OfflineI1Oracle.AttemptBudget budget,
      OfflineI1Oracle.StateVector state,
      List<OfflineI1Oracle.FlowRow> flow,
      List<OfflineI1Oracle.QueueRow> queue,
      List<OfflineI1Oracle.ReplayRecord> replay,
      OfflineI1Oracle.Result lastResult,
      OfflineI1Oracle.Diagnostic diagnostic,
      String id) {
    expectFault(
        diagnostic,
        () ->
            new OfflineI1Oracle.RunState(
                source.scenarioId(),
                source.nextActionOrdinal(),
                source.monotonicOffsetMs(),
                budget,
                state,
                flow,
                queue,
                replay,
                lastResult),
        id);
  }

  private static void testImmutability() {
    OfflineI1Oracle.RunState run = runFixedScenario("OFF-SYN-017");
    expectUnsupported(() -> run.flowLedger().add(null), "T-IMMUTABLE-FLOW");
    expectUnsupported(() -> run.queueLedger().add(null), "T-IMMUTABLE-QUEUE");
    expectUnsupported(() -> run.replayRecords().add(null), "T-IMMUTABLE-REPLAY");
    OfflineI1Oracle.Step snapshotStep =
        OfflineI1Oracle.execute(OfflineI1Oracle.startScenario("OFF-SYN-015"));
    byte[] first = snapshotStep.snapshotBytes();
    byte[] second = snapshotStep.snapshotBytes();
    check(first != second, "T-IMMUTABLE-BYTE-REFERENCE");
    byte original = second[0];
    first[0] = (byte) (first[0] ^ 1);
    check(snapshotStep.snapshotBytes()[0] == original, "T-IMMUTABLE-BYTE-COPY");
  }

  private static String expectedActionRule(int scenario, int ordinal) {
    int key = scenario * 100 + ordinal;
    return switch (key) {
      case 1301 -> "OFF-I1-RULE-Q-007";
      case 1302 -> "OFF-I1-RULE-Q-008";
      case 1701 -> "OFF-I1-RULE-Q-009";
      case 1702 -> "OFF-I1-RULE-Q-010";
      case 1703 -> "OFF-I1-RULE-Q-011";
      case 1704 -> "OFF-I1-RULE-Q-012";
      case 1705 -> "OFF-I1-RULE-Q-013";
      case 1801 -> "OFF-I1-RULE-Q-019";
      case 1802 -> "OFF-I1-RULE-C-001";
      case 2201, 2501 -> "OFF-I1-RULE-Q-001";
      case 2402, 2503 -> "OFF-I1-RULE-D-001";
      case 2601 -> "RESOLVE_SCRIPTED_DELETION_DISPATCH_RULE";
      case 2602 -> "RESOLVE_SCRIPTED_DELETION_RECONCILE_RULE";
      default -> "OFF-I1-RULE-A-" + three(scenario) + "-" + two(ordinal);
    };
  }

  private static OfflineI1Oracle.ReducerOutcome expectedActionOutcome(
      int scenario, int ordinal) {
    check(
        scenario >= 1
            && scenario <= SCENARIO_COUNTS.length
            && ordinal >= 1
            && ordinal <= SCENARIO_COUNTS[scenario - 1],
        "T-EXPECTED-ACTION-OUTCOME-RANGE");
    return OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED;
  }

  private static boolean expectedActionQueue(int scenario, int ordinal) {
    return switch (scenario * 100 + ordinal) {
      case 1301, 1302, 1701, 1702, 1703, 1704, 1705, 1801, 1802, 2201, 2402, 2501,
          2503, 2601, 2602 -> true;
      default -> false;
    };
  }

  private static int expectedActionPriority(int scenario, int ordinal) {
    return switch (scenario * 100 + ordinal) {
      case 1301 -> 100;
      case 1302 -> 110;
      case 1701 -> 120;
      case 1702 -> 130;
      case 1703 -> 140;
      case 1704 -> 150;
      case 1705 -> 160;
      case 1801 -> 190;
      case 1802 -> 200;
      case 2201, 2501 -> 10;
      case 2601 -> 250;
      case 2402, 2503, 2602 -> 300;
      default -> 1000;
    };
  }

  private static int expectedActionSecondary(int scenario, int ordinal) {
    return switch (scenario * 100 + ordinal) {
      case 2402, 2503 -> 1;
      case 1301, 1302, 1701, 1702, 1703, 1704, 1705, 1801, 1802, 2201, 2501,
          2601, 2602 -> 0;
      default -> globalActionOrdinal(scenario, ordinal);
    };
  }

  private static int globalActionOrdinal(int scenario, int ordinal) {
    int result = ordinal;
    for (int index = 0; index < scenario - 1; index++) {
      result += SCENARIO_COUNTS[index];
    }
    return result;
  }

  private static DeletionExpected de(
      int ordinal,
      String event,
      OfflineI1Oracle.QueueState queueState,
      OfflineI1Oracle.DeletionSubstatus substatus,
      boolean preserveSubstatus,
      String deletionIdOutcome,
      OfflineI1Oracle.DeletionError error,
      OfflineI1Oracle.ReceiptOutcome receipt,
      int dispatchAttemptDelta,
      int dispatchGrantDelta,
      int effectDelta,
      int remoteDeletionEffectDelta,
      OfflineI1Oracle.ReducerOutcome reconcileOutcome,
      ExpectedPhases phases,
      ExpectedBudget budget,
      OfflineI1Oracle.ExplicitGrant grant,
      OfflineI1Oracle.RevalidationOutcome revalidationOutcome,
      String gateRuleId) {
    return new DeletionExpected(
        ordinal,
        event,
        queueState,
        substatus,
        preserveSubstatus,
        deletionIdOutcome,
        error,
        receipt,
        dispatchAttemptDelta,
        dispatchGrantDelta,
        effectDelta,
        remoteDeletionEffectDelta,
        reconcileOutcome,
        phases,
        budget,
        grant,
        revalidationOutcome,
        gateRuleId);
  }

  private static RevalidationExpected rx(
      RevalidationSource source,
      ExpectedJob job,
      RevalidationCause cause,
      OfflineI1Oracle.RevalidationOutcome outcome,
      String rule,
      OfflineI1Oracle.QueueState queueTarget,
      OfflineI1Oracle.ProcessingState processingTarget,
      int grantDelta) {
    return new RevalidationExpected(
        source,
        job,
        cause,
        outcome,
        rule,
        queueTarget,
        processingTarget,
        grantDelta);
  }

  private static QueueCase qc(
      int ordinal,
      FixtureSource positive,
      FixtureSource mismatch,
      OfflineI1Oracle.QueueSignal signal,
      OfflineI1Oracle.QueueState target,
      int attemptDelta,
      int grantDelta,
      int effect,
      int apply,
      boolean flowAppend,
      boolean queueAppend) {
    return new QueueCase(
        ordinal,
        positive,
        mismatch,
        signal,
        target,
        attemptDelta,
        grantDelta,
        effect,
        apply,
        flowAppend,
        queueAppend,
        OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED,
        OfflineI1Oracle.Diagnostic.NONE);
  }

  private static QueueFixture queueFixture(FixtureSource source) {
    OfflineI1Oracle.QueueState queueState =
        switch (source) {
          case LOCAL_ONLY -> OfflineI1Oracle.QueueState.LOCAL_ONLY;
          case PENDING -> OfflineI1Oracle.QueueState.PENDING_UPLOAD;
          case WAITING -> OfflineI1Oracle.QueueState.WAITING_NETWORK;
          case UPLOADING -> OfflineI1Oracle.QueueState.UPLOADING;
          case REMOTE -> OfflineI1Oracle.QueueState.REMOTE_PROCESSING;
          case RESULT -> OfflineI1Oracle.QueueState.RESULT_AVAILABLE;
          case APPLIED -> OfflineI1Oracle.QueueState.APPLIED;
          case FAILED_NO_JOB_POSITIVE, FAILED_JOB_POSITIVE, FAILED_NO_JOB_ZERO,
              FAILED_JOB_ZERO -> OfflineI1Oracle.QueueState.FAILED_RETRYABLE;
        };
    OfflineI1Oracle.StateVector state =
        new OfflineI1Oracle.StateVector(
            OfflineI1Oracle.LocalState.LOCAL_READY,
            OfflineI1Oracle.ProcessingState.PROCESSING_QUEUED,
            OfflineI1Oracle.ConnectivityState.AVAILABLE,
            OfflineI1Oracle.ModelState.MODEL_NOT_INSTALLED,
            queueState);
    if (source == FixtureSource.LOCAL_ONLY) {
      return new QueueFixture(
          state, new OfflineI1Oracle.AttemptBudget(0, 0), null, null, null);
    }
    boolean withJob =
        source == FixtureSource.REMOTE
            || source == FixtureSource.RESULT
            || source == FixtureSource.APPLIED
            || source == FixtureSource.FAILED_JOB_POSITIVE
            || source == FixtureSource.FAILED_JOB_ZERO;
    boolean withResult = source == FixtureSource.RESULT || source == FixtureSource.APPLIED;
    long attempt =
        switch (source) {
          case PENDING, WAITING -> 0;
          case FAILED_NO_JOB_POSITIVE, FAILED_JOB_POSITIVE -> 2;
          case FAILED_NO_JOB_ZERO, FAILED_JOB_ZERO -> 3;
          default -> 1;
        };
    int effect = withJob ? 1 : 0;
    int apply = source == FixtureSource.APPLIED ? 1 : 0;
    String logical =
        OfflineI1Oracle.logicalKeyHash(
            OfflineI1Oracle.OperationClass.NON_DELETION_CLOUD_INTENT, "logical.queue.case");
    String job = withJob ? OfflineI1Oracle.jobIdHash(logical, "job.queue.case") : null;
    String result = withResult ? OfflineI1Oracle.resultIdHash(job, "result.queue.case") : null;
    String local = OfflineI1Oracle.localStateDigest(OfflineI1Oracle.LocalState.LOCAL_READY);
    OfflineI1Oracle.QueueRow row =
        new OfflineI1Oracle.QueueRow(
            OfflineI1Oracle.OperationClass.NON_DELETION_CLOUD_INTENT,
            OfflineI1Oracle.queueIntentIdHash("intent.queue.case"),
            logical,
            job,
            result,
            null,
            null,
            null,
            queueState,
            null,
            null,
            null,
            attempt,
            OfflineI1Oracle.ReplayMarker.ORIGINAL,
            effect,
            apply,
            0,
            local,
            local);
    String priorRule =
        switch (source) {
          case PENDING -> "OFF-I1-RULE-Q-007";
          case WAITING -> "OFF-I1-RULE-Q-008";
          case UPLOADING -> "OFF-I1-RULE-Q-010";
          case REMOTE -> "OFF-I1-RULE-Q-011";
          case RESULT -> "OFF-I1-RULE-Q-012";
          case APPLIED -> "OFF-I1-RULE-Q-013";
          case FAILED_NO_JOB_POSITIVE, FAILED_NO_JOB_ZERO -> "OFF-I1-RULE-Q-005";
          case FAILED_JOB_POSITIVE, FAILED_JOB_ZERO -> "OFF-I1-RULE-Q-015";
          case LOCAL_ONLY -> throw new AssertionError("T-Q-FIXTURE-LOCAL-REPLAY");
        };
    OfflineI1Oracle.ReplayRecord replay =
        new OfflineI1Oracle.ReplayRecord(
            logical,
            OfflineI1Oracle.replayInputDigest(
                OfflineI1Oracle.OperationClass.NON_DELETION_CLOUD_INTENT,
                logical,
                OfflineI1Oracle.InputVariant.PRIMARY,
                null),
            priorRule,
            OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED,
            OfflineI1Oracle.stateVectorDigest(state),
            result,
            OfflineI1Oracle.ReplayMarker.ORIGINAL,
            attempt,
            effect,
            apply,
            0);
    return new QueueFixture(
        state, new OfflineI1Oracle.AttemptBudget(attempt, 0), row, replay, state);
  }

  private static OfflineI1Oracle.QueueReducerInput queueInput(
      QueueFixture fixture,
      Set<OfflineI1Oracle.QueueSignal> signals,
      OfflineI1Oracle.ExplicitGrant grant) {
    OfflineI1Oracle.ConnectivityState connectivity =
        signals.contains(OfflineI1Oracle.QueueSignal.CONNECTIVITY_DENIED)
                || signals.contains(OfflineI1Oracle.QueueSignal.SCHEDULER_TICK_WHILE_DENIED)
            ? OfflineI1Oracle.ConnectivityState.NETWORK_DENIED
            : fixture.state.connectivity();
    OfflineI1Oracle.StateVector state =
        new OfflineI1Oracle.StateVector(
            fixture.state.local(),
            fixture.state.processingCapability(),
            connectivity,
            fixture.state.model(),
            fixture.state.queue());
    return new OfflineI1Oracle.QueueReducerInput(
        "OFF-SYN-017",
        state,
        fixture.budget,
        fixture.row,
        fixture.replay,
        fixture.replayWitness,
        signals,
        grant);
  }

  private static QueueFixture withConnectivity(
      QueueFixture fixture, OfflineI1Oracle.ConnectivityState connectivity) {
    OfflineI1Oracle.StateVector state =
        new OfflineI1Oracle.StateVector(
            fixture.state.local(),
            fixture.state.processingCapability(),
            connectivity,
            fixture.state.model(),
            fixture.state.queue());
    return new QueueFixture(
        state, fixture.budget, fixture.row, fixture.replay, fixture.replayWitness);
  }

  private static void assertInvalidQueue(
      OfflineI1Oracle.QueueReduction actual,
      OfflineI1Oracle.Diagnostic diagnostic,
      String id) {
    check(actual.selectedRuleId() == null, id + "-RULE");
    check(actual.outcome() == OfflineI1Oracle.ReducerOutcome.INVALID_INPUT, id + "-OUTCOME");
    check(actual.diagnostic() == diagnostic, id + "-DIAGNOSTIC");
    check(actual.queueAppend() == null && !actual.queueLedgerAppend(), id + "-QUEUE");
    check(!actual.flowLedgerAppend(), id + "-FLOW");
  }

  private static void assertReplayEqualExceptMarker(
      OfflineI1Oracle.ReplayRecord actual,
      OfflineI1Oracle.ReplayRecord expected,
      String id) {
    equal(actual.logicalKeyHash(), expected.logicalKeyHash(), id + "-LOGICAL");
    equal(actual.canonicalInputDigest(), expected.canonicalInputDigest(), id + "-INPUT");
    equal(actual.selectedRuleId(), expected.selectedRuleId(), id + "-RULE");
    check(actual.outcome() == expected.outcome(), id + "-OUTCOME");
    equal(actual.postStateDigest(), expected.postStateDigest(), id + "-POST");
    check(Objects.equals(actual.resultIdHash(), expected.resultIdHash()), id + "-RESULT");
    check(
        actual.replayMarker() == OfflineI1Oracle.ReplayMarker.SAME_INPUT_REPLAY,
        id + "-MARKER");
    check(actual.attemptCount() == expected.attemptCount(), id + "-ATTEMPT");
    check(actual.effectCount() == expected.effectCount(), id + "-EFFECT");
    check(actual.applyCount() == expected.applyCount(), id + "-APPLY");
    check(
        actual.remoteDeletionEffectCount() == expected.remoteDeletionEffectCount(),
        id + "-DELETE-EFFECT");
  }

  private static void assertNoRule(OfflineI1Oracle.QueueReduction actual, String id) {
    check(actual.selectedRuleId() == null, id + "-RULE");
    check(actual.outcome() == OfflineI1Oracle.ReducerOutcome.NO_STATE_CHANGE, id + "-OUTCOME");
    check(actual.diagnostic() == OfflineI1Oracle.Diagnostic.NO_ELIGIBLE_RULE, id + "-DIAGNOSTIC");
    check(actual.queueAppend() == null && !actual.queueLedgerAppend(), id + "-QUEUE");
    check(!actual.flowLedgerAppend(), id + "-FLOW");
  }

  private static void assertWaitingDeleteSeed(OfflineI1Oracle.QueueRow row, String id) {
    check(row.queueState() == OfflineI1Oracle.QueueState.DELETE_PENDING, id + "-STATE");
    check(
        row.deletionSubstatus() == OfflineI1Oracle.DeletionSubstatus.DELETE_WAITING_NETWORK,
        id + "-SUBSTATUS");
    check(row.deletionIdHash() == null, id + "-DELETION-ID");
    check(row.deletionReceiptIdHash() == null, id + "-RECEIPT-ID");
    check(row.contentFreeDeletionErrorCode() == null, id + "-ERROR");
    check(row.remoteDeletionEffectCount() == 0, id + "-EFFECT");
  }

  private static OfflineI1Oracle.QueueRow nonDeleteRow(
      OfflineI1Oracle.QueueState state, boolean withJob, long attempt) {
    String logical =
        OfflineI1Oracle.logicalKeyHash(
            OfflineI1Oracle.OperationClass.NON_DELETION_CLOUD_INTENT, "logical.test");
    String job = withJob ? OfflineI1Oracle.jobIdHash(logical, "job.test") : null;
    String local = OfflineI1Oracle.localStateDigest(OfflineI1Oracle.LocalState.LOCAL_READY);
    return new OfflineI1Oracle.QueueRow(
        OfflineI1Oracle.OperationClass.NON_DELETION_CLOUD_INTENT,
        OfflineI1Oracle.queueIntentIdHash("intent.test"),
        logical,
        job,
        null,
        null,
        null,
        null,
        state,
        null,
        null,
        null,
        attempt,
        OfflineI1Oracle.ReplayMarker.ORIGINAL,
        withJob ? 1 : 0,
        0,
        0,
        local,
        local);
  }

  private static OfflineI1Oracle.QueueRow withAttempt(
      OfflineI1Oracle.QueueRow row, long attempt) {
    return new OfflineI1Oracle.QueueRow(
        row.operationClass(),
        row.intentIdHash(),
        row.logicalKeyHash(),
        row.jobIdHash(),
        row.resultIdHash(),
        row.deletionScopeDigest(),
        row.deletionIdHash(),
        row.deletionReceiptIdHash(),
        row.queueState(),
        row.deletionSubstatus(),
        row.contentFreeDeletionErrorCode(),
        row.deletionReceiptVerificationOutcome(),
        attempt,
        row.replayMarker(),
        row.effectCount(),
        row.applyCount(),
        row.remoteDeletionEffectCount(),
        row.preLocalStateDigest(),
        row.postLocalStateDigest());
  }

  private static OfflineI1Oracle.QueueRow lastQueue(OfflineI1Oracle.RunState run) {
    return run.queueLedger().get(run.queueLedger().size() - 1);
  }

  private static void expectSnapshotInvalid(
      byte[] bytes, OfflineI1Oracle.Diagnostic diagnostic, String id) {
    expectFault(diagnostic, () -> OfflineI1Oracle.decodeSnapshot(bytes), id);
  }

  private static void assertInvalidStepUnchanged(
      OfflineI1Oracle.Step actual,
      OfflineI1Oracle.RunState prior,
      OfflineI1Oracle.Diagnostic diagnostic,
      String id) {
    check(actual.result().selectedRuleId() == null, id + "-RULE");
    check(actual.result().outcome() == OfflineI1Oracle.ReducerOutcome.INVALID_INPUT, id + "-OUTCOME");
    check(actual.result().typedLedgerDeltas().diagnosticCode() == diagnostic, id + "-DIAGNOSTIC");
    check(
        actual.result().typedLedgerDeltas().flowAppend() == null
            && actual.result().typedLedgerDeltas().queueAppend() == null,
        id + "-ZERO-DELTAS");
    check(
        actual.result().effectCount() == 0
            && actual.result().applyCount() == 0
            && actual.result().remoteDeletionEffectCount() == 0,
        id + "-ZERO-COUNTERS");
    check(actual.snapshotBytes() == null, id + "-NO-SNAPSHOT");
    equalBytes(
        OfflineI1Oracle.encodeSnapshot(actual.runState()),
        OfflineI1Oracle.encodeSnapshot(prior),
        id + "-STATE");
  }

  private static void expectFault(
      OfflineI1Oracle.Diagnostic diagnostic, Checked operation, String id) {
    try {
      operation.run();
      throw new AssertionError(id + "-NO-FAULT");
    } catch (OfflineI1Oracle.ContractFault fault) {
      check(fault.diagnostic() == diagnostic, id + "-DIAGNOSTIC");
      check(fault.getMessage().equals(diagnostic.code()), id + "-CONTENT-FREE");
    }
  }

  private static void expectUnsupported(Checked operation, String id) {
    try {
      operation.run();
      throw new AssertionError(id + "-NO-FAULT");
    } catch (UnsupportedOperationException expected) {
      assertions++;
    }
  }

  private static byte[] concat(byte[] left, byte[] right) {
    byte[] result = Arrays.copyOf(left, left.length + right.length);
    System.arraycopy(right, 0, result, left.length, right.length);
    return result;
  }

  private static int safeOrdinal(String value) {
    return Math.abs(value.hashCode() % 10000);
  }

  private static String stateJson(OfflineI1Oracle.StateVector state) {
    return "{\"local\":\""
        + state.local().name()
        + "\",\"processingCapability\":\""
        + state.processingCapability().name()
        + "\",\"connectivity\":\""
        + state.connectivity().name()
        + "\",\"model\":\""
        + state.model().name()
        + "\",\"queue\":\""
        + state.queue().name()
        + "\"}";
  }

  private static String replaceFirstLiteral(
      String source, String target, String replacement, String id) {
    int index = source.indexOf(target);
    check(index >= 0, id);
    return source.substring(0, index) + replacement + source.substring(index + target.length());
  }

  private static String replaceLastLiteral(
      String source, String target, String replacement, String id) {
    int index = source.lastIndexOf(target);
    check(index >= 0, id);
    return source.substring(0, index) + replacement + source.substring(index + target.length());
  }

  private static String replaceLiteralAfter(
      String source, String anchor, String target, String replacement, String id) {
    int anchorIndex = source.indexOf(anchor);
    check(anchorIndex >= 0, id + "-ANCHOR");
    int index = source.indexOf(target, anchorIndex + anchor.length());
    check(index >= 0, id + "-TARGET");
    return source.substring(0, index) + replacement + source.substring(index + target.length());
  }

  private static String replaceFirstFlowWithNull(String source, String id) {
    String anchor = "\"flowLedger\":[";
    int start = source.indexOf(anchor);
    check(start >= 0, id + "-START");
    start += anchor.length();
    int end = source.indexOf(",{\"sequence\":2", start);
    check(end > start, id + "-END");
    return source.substring(0, start) + "null" + source.substring(end);
  }

  private static void assertValidEnvelopeSnapshot(
      OfflineI1Oracle.RunState expected, byte[] canonical, String id) {
    check(canonical.length < 524_288, id + "-BYTE-BOUND");
    check(maximumJsonContainerDepth(canonical) <= 5, id + "-DEPTH-BOUND");
    OfflineI1Oracle.RunState decoded = OfflineI1Oracle.decodeSnapshot(canonical);
    check(decoded.equals(expected), id + "-DEEP-EQUALITY");
    equalBytes(OfflineI1Oracle.encodeSnapshot(decoded), canonical, id + "-REENCODE");
    expectUnsupported(() -> decoded.flowLedger().add(null), id + "-FLOW-IMMUTABLE");
    expectUnsupported(() -> decoded.queueLedger().add(null), id + "-QUEUE-IMMUTABLE");
    expectUnsupported(() -> decoded.replayRecords().add(null), id + "-REPLAY-IMMUTABLE");
  }

  private static int maximumJsonContainerDepth(byte[] canonical) {
    String text = new String(canonical, StandardCharsets.UTF_8);
    int depth = 0;
    int maximum = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int index = 0; index < text.length(); index++) {
      char value = text.charAt(index);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (value == '\\') {
          escaped = true;
        } else if (value == '"') {
          inString = false;
        }
      } else if (value == '"') {
        inString = true;
      } else if (value == '{' || value == '[') {
        depth++;
        maximum = Math.max(maximum, depth);
      } else if (value == '}' || value == ']') {
        depth--;
      }
    }
    check(!inString && !escaped && depth == 0, "T-SNAP-DEPTH-SCANNER");
    return maximum;
  }

  private static void equal(String actual, String expected, String id) {
    check(expected.equals(actual), id);
  }

  private static void equalBytes(byte[] actual, byte[] expected, String id) {
    check(Arrays.equals(actual, expected), id);
  }

  private static void check(boolean condition, String id) {
    assertions++;
    if (!condition) throw new AssertionError(id);
  }

  private static String two(int value) {
    return value < 10 ? "0" + value : Integer.toString(value);
  }

  private static String three(int value) {
    if (value < 10) return "00" + value;
    return value < 100 ? "0" + value : Integer.toString(value);
  }

  @FunctionalInterface
  private interface Checked {
    void run();
  }
}
