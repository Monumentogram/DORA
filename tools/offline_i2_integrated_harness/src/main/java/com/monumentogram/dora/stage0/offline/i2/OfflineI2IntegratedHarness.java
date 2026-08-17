package com.monumentogram.dora.stage0.offline.i2;

import com.monumentogram.dora.stage0.offline.i1.OfflineI1Oracle;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Disposable pure-host Stage 0 integration harness for deterministic Offline semantics.
 *
 * <p>The harness carries only repository-owned categorical values, counters, and digests. It has
 * no Android, persistence, external communication, environment, process, thread, clock, random,
 * model, provider, or user-content surface. Reconnect is a semantic projection, never transport
 * evidence.
 */
public final class OfflineI2IntegratedHarness {
  public static final String HARNESS_ID =
      "poc-offline-i2-integrated-synthetic-harness-stage0-v0.1";
  public static final String PROOF_CLASS =
      "PURE_HOST_SYNTHETIC_IN_MEMORY_SEMANTICS_NOT_DEVICE_NETWORK_OR_PRODUCT";

  private static final String FIXTURE_ID = "offline.i2.fixture.001";
  private static final String QUEUE_ID = "offline.i2.queue.001";
  private static final int MAX_ATTEMPTS = 3;
  private static final Pattern HASH = Pattern.compile("^[0-9a-f]{64}$");
  private static final List<Action> ACTION_ORDER =
      List.of(
          Action.CAPTURE_SYNTHETIC_SOURCE,
          Action.SAVE_LOCAL_SOURCE,
          Action.PROCESS_RULES_ONLY,
          Action.OPEN_HISTORY,
          Action.SEARCH_LOCAL_INDEX,
          Action.CREATE_LOCAL_TASK,
          Action.EDIT_LOCAL_PROTOCOL,
          Action.COPY_LOCAL_REPRESENTATION,
          Action.EXPORT_LOCAL_REPRESENTATION,
          Action.REQUEST_REQUIRED_MODEL,
          Action.ENQUEUE_CLOUD_INTENT,
          Action.DENIED_SCHEDULER_TICK,
          Action.REPEAT_LOCAL_WHILE_WAITING,
          Action.SNAPSHOT_AND_RESTORE,
          Action.PROJECT_RECONNECT_CONFLICT,
          Action.PROJECT_SAME_INPUT_REPLAY);

  private OfflineI2IntegratedHarness() {}

  public enum ModelProfile {
    MODEL_ABSENT,
    MODEL_PRESENT_UNAPPROVED
  }

  public enum LocalPhase {
    FRESH_LOCAL_DEFAULT,
    CAPTURED_SYNTHETIC,
    SAVED_LOCAL,
    RULES_PROCESSED,
    HISTORY_OPENED,
    SEARCH_MATCHED,
    TASK_CREATED,
    USER_EDITED,
    COPIED_LOCAL,
    EXPORTED_LOCAL
  }

  public enum FieldOwner {
    MACHINE,
    USER
  }

  public enum Action {
    CAPTURE_SYNTHETIC_SOURCE,
    SAVE_LOCAL_SOURCE,
    PROCESS_RULES_ONLY,
    OPEN_HISTORY,
    SEARCH_LOCAL_INDEX,
    CREATE_LOCAL_TASK,
    EDIT_LOCAL_PROTOCOL,
    COPY_LOCAL_REPRESENTATION,
    EXPORT_LOCAL_REPRESENTATION,
    REQUEST_REQUIRED_MODEL,
    ENQUEUE_CLOUD_INTENT,
    DENIED_SCHEDULER_TICK,
    REPEAT_LOCAL_WHILE_WAITING,
    SNAPSHOT_AND_RESTORE,
    PROJECT_RECONNECT_CONFLICT,
    PROJECT_SAME_INPUT_REPLAY
  }

  public enum Outcome {
    TRANSITION_APPLIED,
    LOCAL_STATE_PRESERVED,
    WAITING_MODEL,
    UNAPPROVED_MODEL_BLOCKED,
    WAITING_NETWORK_ZERO_ATTEMPTS,
    RESTORED_IMMUTABLE_SEMANTIC_EQUALITY,
    CONFLICT_USER_EDIT_PRESERVED,
    SAME_INPUT_REPLAY_ZERO_DUPLICATE_EFFECT
  }

  public enum Diagnostic {
    INVALID_NULL,
    INVALID_ORDER,
    INVALID_STATE,
    INVALID_DIGEST,
    INVALID_SNAPSHOT,
    ATTEMPT_WHILE_DENIED,
    ATTEMPT_BUDGET_EXCEEDED,
    QUEUE_IDENTITY_CHANGED,
    USER_OWNERSHIP_VIOLATION,
    MODEL_ADMISSION_VIOLATION,
    I1_WITNESS_MISMATCH
  }

  public enum ReplayMarker {
    ORIGINAL,
    SAME_INPUT_REPLAY
  }

  public record LocalAggregate(
      String conversationIdHash,
      String sourceDigest,
      String durableDigest,
      String transcriptDigest,
      String protocolDigest,
      String taskDigest,
      String taskSourceProtocolDigest,
      String copyDigest,
      String exportDigest,
      long revision,
      FieldOwner protocolOwner) {
    public LocalAggregate {
      requireHash(conversationIdHash);
      requireNullableHash(sourceDigest);
      requireNullableHash(durableDigest);
      requireNullableHash(transcriptDigest);
      requireNullableHash(protocolDigest);
      requireNullableHash(taskDigest);
      requireNullableHash(taskSourceProtocolDigest);
      requireNullableHash(copyDigest);
      requireNullableHash(exportDigest);
      if (revision < 0 || protocolOwner == null) {
        throw fault(Diagnostic.INVALID_STATE);
      }
    }
  }

  public record QueueProjection(
      String intentIdHash,
      String canonicalInputDigest,
      OfflineI1Oracle.QueueState state,
      long attemptCount,
      int effectCount,
      int applyCount,
      ReplayMarker replayMarker) {
    public QueueProjection {
      requireHash(intentIdHash);
      requireHash(canonicalInputDigest);
      requirePresent(state);
      requirePresent(replayMarker);
      if (attemptCount < 0 || attemptCount > MAX_ATTEMPTS) {
        throw fault(Diagnostic.ATTEMPT_BUDGET_EXCEEDED);
      }
      requireBit(effectCount);
      requireBit(applyCount);
      if (applyCount > effectCount) {
        throw fault(Diagnostic.INVALID_STATE);
      }
    }
  }

  public record IntegratedState(
      ModelProfile profile,
      int nextActionIndex,
      LocalPhase localPhase,
      OfflineI1Oracle.ProcessingState processing,
      OfflineI1Oracle.ConnectivityState connectivity,
      OfflineI1Oracle.ModelState model,
      OfflineI1Oracle.QueueState queue,
      LocalAggregate local,
      QueueProjection queueProjection) {
    public IntegratedState {
      requirePresent(profile);
      requirePresent(localPhase);
      requirePresent(processing);
      requirePresent(connectivity);
      requirePresent(model);
      requirePresent(queue);
      requirePresent(local);
      if (nextActionIndex < 0 || nextActionIndex > ACTION_ORDER.size()) {
        throw fault(Diagnostic.INVALID_ORDER);
      }
      validateState(
          profile,
          nextActionIndex,
          localPhase,
          processing,
          connectivity,
          model,
          queue,
          local,
          queueProjection);
    }
  }

  public record TraceRow(
      long sequence,
      Action action,
      Outcome outcome,
      String preStateDigest,
      String postStateDigest,
      String localAggregateDigest,
      String queueIntentIdHash) {
    public TraceRow {
      if (sequence <= 0) {
        throw fault(Diagnostic.INVALID_STATE);
      }
      requirePresent(action);
      requirePresent(outcome);
      requireHash(preStateDigest);
      requireHash(postStateDigest);
      requireHash(localAggregateDigest);
      requireNullableHash(queueIntentIdHash);
    }
  }

  public record Snapshot(IntegratedState state, String stateDigest, String traceDigest) {
    public Snapshot {
      requirePresent(state);
      requireHash(stateDigest);
      requireHash(traceDigest);
      if (!stateDigest.equals(digestState(state))) {
        throw fault(Diagnostic.INVALID_SNAPSHOT);
      }
    }
  }

  public record I1Witnesses(
      String modelAbsentDigest,
      String deniedQueueDigest,
      String localWhileQueuedDigest,
      String snapshotContinuationDigest,
      String reconnectExactlyOnceDigest,
      String replayExactlyOnceDigest) {
    public I1Witnesses {
      requireHash(modelAbsentDigest);
      requireHash(deniedQueueDigest);
      requireHash(localWhileQueuedDigest);
      requireHash(snapshotContinuationDigest);
      requireHash(reconnectExactlyOnceDigest);
      requireHash(replayExactlyOnceDigest);
    }
  }

  public record RunResult(
      ModelProfile profile,
      IntegratedState finalState,
      List<TraceRow> trace,
      I1Witnesses i1Witnesses,
      String traceDigest,
      String summaryDigest,
      String proofClass) {
    public RunResult {
      requirePresent(profile);
      requirePresent(finalState);
      requirePresent(trace);
      requirePresent(i1Witnesses);
      requireHash(traceDigest);
      requireHash(summaryDigest);
      if (!PROOF_CLASS.equals(proofClass)) {
        throw fault(Diagnostic.INVALID_STATE);
      }
      trace = List.copyOf(trace);
      if (profile != finalState.profile()) {
        throw fault(Diagnostic.INVALID_STATE);
      }
      String expectedTraceDigest = digestTrace(trace);
      if (trace.size() != ACTION_ORDER.size()
          || finalState.nextActionIndex() != ACTION_ORDER.size()
          || !traceDigest.equals(expectedTraceDigest)) {
        throw fault(Diagnostic.INVALID_STATE);
      }
      validateTracePrefix(finalState, trace);
      I1Witnesses expectedI1Witnesses = verifyI1Witnesses();
      if (!i1Witnesses.equals(expectedI1Witnesses)) {
        throw fault(Diagnostic.I1_WITNESS_MISMATCH);
      }
      String expectedSummaryDigest =
          digestRunSummary(
              profile,
              finalState,
              trace,
              expectedTraceDigest,
              expectedI1Witnesses);
      if (!summaryDigest.equals(expectedSummaryDigest)) {
        throw fault(Diagnostic.INVALID_STATE);
      }
    }
  }

  /** Runs the complete fixed synthetic trace for one exact model-boundary profile. */
  public static RunResult run(ModelProfile profile) {
    IntegratedState state = initialState(profile);
    List<TraceRow> trace = new ArrayList<>();
    for (Action action : ACTION_ORDER) {
      String before = digestState(state);
      Transition transition = transition(state, action, trace);
      state = transition.state();
      trace.add(
          new TraceRow(
              trace.size() + 1L,
              action,
              transition.outcome(),
              before,
              digestState(state),
              digestLocal(state.local()),
              state.queueProjection() == null
                  ? null
                  : state.queueProjection().intentIdHash()));
    }
    List<TraceRow> immutableTrace = List.copyOf(trace);
    I1Witnesses witnesses = verifyI1Witnesses();
    String traceDigest = digestTrace(immutableTrace);
    String summaryDigest =
        digestRunSummary(profile, state, immutableTrace, traceDigest, witnesses);
    return new RunResult(
        profile,
        state,
        immutableTrace,
        witnesses,
        traceDigest,
        summaryDigest,
        PROOF_CLASS);
  }

  static List<Action> actionOrder() {
    return ACTION_ORDER;
  }

  static IntegratedState initialState(ModelProfile profile) {
    requirePresent(profile);
    LocalAggregate local =
        new LocalAggregate(
            digest("CONVERSATION_ID_V1", FIXTURE_ID),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            FieldOwner.MACHINE);
    return new IntegratedState(
        profile,
        0,
        LocalPhase.FRESH_LOCAL_DEFAULT,
        OfflineI1Oracle.ProcessingState.PROCESSING_NOT_REQUESTED,
        OfflineI1Oracle.ConnectivityState.NETWORK_DENIED,
        profile == ModelProfile.MODEL_ABSENT
            ? OfflineI1Oracle.ModelState.MODEL_NOT_INSTALLED
            : OfflineI1Oracle.ModelState.MODEL_UNAVAILABLE_OR_INVALID,
        OfflineI1Oracle.QueueState.LOCAL_ONLY,
        local,
        null);
  }

  static Transition transition(IntegratedState prior, Action action, List<TraceRow> priorTrace) {
    requirePresent(prior);
    requirePresent(action);
    requirePresent(priorTrace);
    validateTracePrefix(prior, priorTrace);
    if (prior.nextActionIndex() >= ACTION_ORDER.size()
        || ACTION_ORDER.get(prior.nextActionIndex()) != action
        || priorTrace.size() != prior.nextActionIndex()) {
      throw fault(Diagnostic.INVALID_ORDER);
    }
    return applyTransition(prior, action, priorTrace, true);
  }

  private static Transition applyTransition(
      IntegratedState prior,
      Action action,
      List<TraceRow> priorTrace,
      boolean verifySnapshotRoundTrip) {
    IntegratedState next;
    Outcome outcome = Outcome.TRANSITION_APPLIED;
    LocalAggregate local = prior.local();
    QueueProjection projection = prior.queueProjection();

    switch (action) {
      case CAPTURE_SYNTHETIC_SOURCE -> {
        local =
            replaceLocal(
                local,
                digest(
                    "SYNTHETIC_SOURCE_V1",
                    local.conversationIdHash(),
                    "frame-count=64",
                    "sample-rate-shape=16000"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                FieldOwner.MACHINE);
        next = update(prior, LocalPhase.CAPTURED_SYNTHETIC, prior.processing(), prior.connectivity(), prior.model(), prior.queue(), local, null);
      }
      case SAVE_LOCAL_SOURCE -> {
        local =
            replaceLocal(
                local,
                local.sourceDigest(),
                digest("DURABLE_LOCAL_V1", local.conversationIdHash(), local.sourceDigest()),
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                FieldOwner.MACHINE);
        next = update(prior, LocalPhase.SAVED_LOCAL, prior.processing(), prior.connectivity(), prior.model(), prior.queue(), local, null);
      }
      case PROCESS_RULES_ONLY -> {
        String transcript =
            digest("RULES_TRANSCRIPT_SHAPE_V1", local.conversationIdHash(), local.durableDigest());
        String protocol =
            digest("RULES_PROTOCOL_SHAPE_V1", local.conversationIdHash(), transcript);
        local =
            replaceLocal(
                local,
                local.sourceDigest(),
                local.durableDigest(),
                transcript,
                protocol,
                null,
                null,
                null,
                null,
                0,
                FieldOwner.MACHINE);
        next =
            update(
                prior,
                LocalPhase.RULES_PROCESSED,
                OfflineI1Oracle.ProcessingState.PROCESSING_SUCCEEDED,
                prior.connectivity(),
                prior.model(),
                prior.queue(),
                local,
                null);
      }
      case OPEN_HISTORY ->
          next = update(prior, LocalPhase.HISTORY_OPENED, prior.processing(), prior.connectivity(), prior.model(), prior.queue(), local, null);
      case SEARCH_LOCAL_INDEX ->
          next = update(prior, LocalPhase.SEARCH_MATCHED, prior.processing(), prior.connectivity(), prior.model(), prior.queue(), local, null);
      case CREATE_LOCAL_TASK -> {
        String task =
            digest("LOCAL_TASK_V1", local.conversationIdHash(), local.protocolDigest(), "task-slot=1");
        local =
            replaceLocal(
                local,
                local.sourceDigest(),
                local.durableDigest(),
                local.transcriptDigest(),
                local.protocolDigest(),
                task,
                local.protocolDigest(),
                null,
                null,
                0,
                FieldOwner.MACHINE);
        next = update(prior, LocalPhase.TASK_CREATED, prior.processing(), prior.connectivity(), prior.model(), prior.queue(), local, null);
      }
      case EDIT_LOCAL_PROTOCOL -> {
        String edited =
            digest("USER_EDIT_V1", local.conversationIdHash(), local.protocolDigest(), "revision=1");
        local =
            replaceLocal(
                local,
                local.sourceDigest(),
                local.durableDigest(),
                local.transcriptDigest(),
                edited,
                local.taskDigest(),
                local.taskSourceProtocolDigest(),
                null,
                null,
                1,
                FieldOwner.USER);
        next = update(prior, LocalPhase.USER_EDITED, prior.processing(), prior.connectivity(), prior.model(), prior.queue(), local, null);
      }
      case COPY_LOCAL_REPRESENTATION -> {
        String copy =
            digest(
                "LOCAL_COPY_V1",
                local.conversationIdHash(),
                local.transcriptDigest(),
                local.protocolDigest(),
                local.taskDigest(),
                "revision=1");
        local =
            replaceLocal(
                local,
                local.sourceDigest(),
                local.durableDigest(),
                local.transcriptDigest(),
                local.protocolDigest(),
                local.taskDigest(),
                local.taskSourceProtocolDigest(),
                copy,
                null,
                local.revision(),
                local.protocolOwner());
        next = update(prior, LocalPhase.COPIED_LOCAL, prior.processing(), prior.connectivity(), prior.model(), prior.queue(), local, null);
      }
      case EXPORT_LOCAL_REPRESENTATION -> {
        String exported =
            digest("LOCAL_EXPORT_V1", local.conversationIdHash(), local.copyDigest());
        local =
            replaceLocal(
                local,
                local.sourceDigest(),
                local.durableDigest(),
                local.transcriptDigest(),
                local.protocolDigest(),
                local.taskDigest(),
                local.taskSourceProtocolDigest(),
                local.copyDigest(),
                exported,
                local.revision(),
                local.protocolOwner());
        next = update(prior, LocalPhase.EXPORTED_LOCAL, prior.processing(), prior.connectivity(), prior.model(), prior.queue(), local, null);
      }
      case REQUEST_REQUIRED_MODEL -> {
        if (prior.profile() == ModelProfile.MODEL_ABSENT) {
          next =
              update(
                  prior,
                  prior.localPhase(),
                  OfflineI1Oracle.ProcessingState.WAITING_MODEL,
                  prior.connectivity(),
                  OfflineI1Oracle.ModelState.MODEL_NOT_INSTALLED,
                  prior.queue(),
                  local,
                  null);
          outcome = Outcome.WAITING_MODEL;
        } else {
          next =
              update(
                  prior,
                  prior.localPhase(),
                  OfflineI1Oracle.ProcessingState.PROCESSING_FAILED_SCOPED,
                  prior.connectivity(),
                  OfflineI1Oracle.ModelState.MODEL_UNAVAILABLE_OR_INVALID,
                  prior.queue(),
                  local,
                  null);
          outcome = Outcome.UNAPPROVED_MODEL_BLOCKED;
        }
      }
      case ENQUEUE_CLOUD_INTENT -> {
        projection =
            new QueueProjection(
                expectedQueueIntent(local),
                expectedCanonicalQueueInput(local),
                OfflineI1Oracle.QueueState.PENDING_UPLOAD,
                0,
                0,
                0,
                ReplayMarker.ORIGINAL);
        next =
            update(
                prior,
                prior.localPhase(),
                prior.processing(),
                prior.connectivity(),
                prior.model(),
                OfflineI1Oracle.QueueState.PENDING_UPLOAD,
                local,
                projection);
      }
      case DENIED_SCHEDULER_TICK -> {
        if (prior.connectivity() != OfflineI1Oracle.ConnectivityState.NETWORK_DENIED
            || projection == null
            || projection.attemptCount() != 0) {
          throw fault(Diagnostic.INVALID_STATE);
        }
        projection =
            replaceQueue(
                projection,
                OfflineI1Oracle.QueueState.WAITING_NETWORK,
                0,
                0,
                0,
                ReplayMarker.ORIGINAL);
        next =
            update(
                prior,
                prior.localPhase(),
                prior.processing(),
                prior.connectivity(),
                prior.model(),
                OfflineI1Oracle.QueueState.WAITING_NETWORK,
                local,
                projection);
        outcome = Outcome.WAITING_NETWORK_ZERO_ATTEMPTS;
      }
      case REPEAT_LOCAL_WHILE_WAITING -> {
        if (projection == null
            || projection.state() != OfflineI1Oracle.QueueState.WAITING_NETWORK
            || projection.attemptCount() != 0
            || prior.connectivity() != OfflineI1Oracle.ConnectivityState.NETWORK_DENIED) {
          throw fault(Diagnostic.ATTEMPT_WHILE_DENIED);
        }
        requireLocalLineage(local);
        next = update(prior, prior.localPhase(), prior.processing(), prior.connectivity(), prior.model(), prior.queue(), local, projection);
        outcome = Outcome.LOCAL_STATE_PRESERVED;
      }
      case SNAPSHOT_AND_RESTORE -> {
        IntegratedState restored = prior;
        if (verifySnapshotRoundTrip) {
          Snapshot snapshot = snapshot(prior, priorTrace);
          restored = restore(snapshot, priorTrace);
          if (!prior.equals(restored)) {
            throw fault(Diagnostic.INVALID_SNAPSHOT);
          }
        }
        next = advance(restored);
        outcome = Outcome.RESTORED_IMMUTABLE_SEMANTIC_EQUALITY;
      }
      case PROJECT_RECONNECT_CONFLICT -> {
        QueueProjection attempted = projectAttempt(prior, OfflineI1Oracle.ConnectivityState.AVAILABLE);
        if (local.protocolOwner() != FieldOwner.USER || local.revision() != 1) {
          throw fault(Diagnostic.USER_OWNERSHIP_VIOLATION);
        }
        projection =
            replaceQueue(
                attempted,
                OfflineI1Oracle.QueueState.CONFLICT,
                attempted.attemptCount(),
                1,
                0,
                ReplayMarker.ORIGINAL);
        next =
            update(
                prior,
                prior.localPhase(),
                prior.processing(),
                OfflineI1Oracle.ConnectivityState.AVAILABLE,
                prior.model(),
                OfflineI1Oracle.QueueState.CONFLICT,
                local,
                projection);
        outcome = Outcome.CONFLICT_USER_EDIT_PRESERVED;
      }
      case PROJECT_SAME_INPUT_REPLAY -> {
        if (projection == null
            || prior.queue() != OfflineI1Oracle.QueueState.CONFLICT
            || projection.effectCount() != 1
            || projection.applyCount() != 0
            || local.protocolOwner() != FieldOwner.USER) {
          throw fault(Diagnostic.INVALID_STATE);
        }
        projection =
            replaceQueue(
                projection,
                OfflineI1Oracle.QueueState.CONFLICT,
                checkedIncrement(projection.attemptCount()),
                1,
                0,
                ReplayMarker.SAME_INPUT_REPLAY);
        next = update(prior, prior.localPhase(), prior.processing(), prior.connectivity(), prior.model(), prior.queue(), local, projection);
        outcome = Outcome.SAME_INPUT_REPLAY_ZERO_DUPLICATE_EFFECT;
      }
      default -> throw fault(Diagnostic.INVALID_ORDER);
    }
    return new Transition(next, outcome);
  }

  static Snapshot snapshot(IntegratedState state, List<TraceRow> trace) {
    requirePresent(state);
    requirePresent(trace);
    validateTracePrefix(state, trace);
    IntegratedState copy = copyState(state);
    return new Snapshot(copy, digestState(copy), digestTrace(trace));
  }

  static IntegratedState restore(Snapshot snapshot, List<TraceRow> trace) {
    requirePresent(snapshot);
    requirePresent(trace);
    validateTracePrefix(snapshot.state(), trace);
    if (!snapshot.stateDigest().equals(digestState(snapshot.state()))
        || !snapshot.traceDigest().equals(digestTrace(trace))
        || trace.size() != snapshot.state().nextActionIndex()) {
      throw fault(Diagnostic.INVALID_SNAPSHOT);
    }
    IntegratedState copy = copyState(snapshot.state());
    if (!snapshot.stateDigest().equals(digestState(copy))) {
      throw fault(Diagnostic.INVALID_SNAPSHOT);
    }
    return copy;
  }

  static QueueProjection projectAttempt(
      IntegratedState state, OfflineI1Oracle.ConnectivityState observedConnectivity) {
    requirePresent(state);
    requirePresent(observedConnectivity);
    if (state.queueProjection() == null
        || state.queueProjection().state() != OfflineI1Oracle.QueueState.WAITING_NETWORK) {
      throw fault(Diagnostic.INVALID_STATE);
    }
    if (observedConnectivity != OfflineI1Oracle.ConnectivityState.AVAILABLE) {
      throw fault(Diagnostic.ATTEMPT_WHILE_DENIED);
    }
    QueueProjection prior = state.queueProjection();
    return replaceQueue(
        prior,
        OfflineI1Oracle.QueueState.RESULT_AVAILABLE,
        checkedIncrement(prior.attemptCount()),
        1,
        0,
        ReplayMarker.ORIGINAL);
  }

  static String digestState(IntegratedState state) {
    requirePresent(state);
    QueueProjection queue = state.queueProjection();
    return digest(
        "INTEGRATED_STATE_V1",
        state.profile().name(),
        Integer.toString(state.nextActionIndex()),
        state.localPhase().name(),
        state.processing().name(),
        state.connectivity().name(),
        state.model().name(),
        state.queue().name(),
        digestLocal(state.local()),
        queue == null ? "NULL" : digestQueue(queue));
  }

  static String digestLocal(LocalAggregate local) {
    requirePresent(local);
    return digest(
        "LOCAL_AGGREGATE_V1",
        local.conversationIdHash(),
        nullable(local.sourceDigest()),
        nullable(local.durableDigest()),
        nullable(local.transcriptDigest()),
        nullable(local.protocolDigest()),
        nullable(local.taskDigest()),
        nullable(local.taskSourceProtocolDigest()),
        nullable(local.copyDigest()),
        nullable(local.exportDigest()),
        Long.toString(local.revision()),
        local.protocolOwner().name());
  }

  static String digestTrace(List<TraceRow> trace) {
    requirePresent(trace);
    List<String> fields = new ArrayList<>();
    fields.add(Integer.toString(trace.size()));
    long expected = 1;
    for (TraceRow row : trace) {
      requirePresent(row);
      if (row.sequence() != expected) {
        throw fault(Diagnostic.INVALID_STATE);
      }
      fields.add(Long.toString(row.sequence()));
      fields.add(row.action().name());
      fields.add(row.outcome().name());
      fields.add(row.preStateDigest());
      fields.add(row.postStateDigest());
      fields.add(row.localAggregateDigest());
      fields.add(nullable(row.queueIntentIdHash()));
      expected++;
    }
    return digest("TRACE_V1", fields.toArray(String[]::new));
  }

  private static String digestRunSummary(
      ModelProfile profile,
      IntegratedState finalState,
      List<TraceRow> trace,
      String traceDigest,
      I1Witnesses i1Witnesses) {
    return digest(
        "RUN_SUMMARY_V1",
        profile.name(),
        Integer.toString(trace.size()),
        digestState(finalState),
        digestLocal(finalState.local()),
        traceDigest,
        digestWitnesses(i1Witnesses),
        PROOF_CLASS);
  }

  private static void validateTracePrefix(IntegratedState state, List<TraceRow> trace) {
    requirePresent(state);
    requirePresent(trace);
    if (trace.size() != state.nextActionIndex()) {
      throw fault(Diagnostic.INVALID_SNAPSHOT);
    }
    IntegratedState expectedState = initialState(state.profile());
    List<TraceRow> expectedTrace = new ArrayList<>();
    for (int index = 0; index < trace.size(); index++) {
      TraceRow row = requirePresent(trace.get(index));
      Action expectedAction = ACTION_ORDER.get(index);
      Transition expectedTransition =
          applyTransition(expectedState, expectedAction, expectedTrace, false);
      IntegratedState expectedNext = expectedTransition.state();
      TraceRow expectedRow =
          new TraceRow(
              index + 1L,
              expectedAction,
              expectedTransition.outcome(),
              digestState(expectedState),
              digestState(expectedNext),
              digestLocal(expectedNext.local()),
              expectedNext.queueProjection() == null
                  ? null
                  : expectedNext.queueProjection().intentIdHash());
      if (!row.equals(expectedRow)) {
        throw fault(Diagnostic.INVALID_SNAPSHOT);
      }
      expectedTrace.add(expectedRow);
      expectedState = expectedNext;
    }
    if (!expectedState.equals(state)) {
      throw fault(Diagnostic.INVALID_SNAPSHOT);
    }
  }

  private static I1Witnesses verifyI1Witnesses() {
    OfflineI1Oracle.RunState modelAbsent = runI1Fixed("OFF-SYN-009", 2);
    if (modelAbsent.stateVector().processingCapability()
            != OfflineI1Oracle.ProcessingState.WAITING_MODEL
        || modelAbsent.stateVector().local()
            != OfflineI1Oracle.LocalState.LOCAL_OPERATION_SUCCEEDED) {
      throw fault(Diagnostic.I1_WITNESS_MISMATCH);
    }

    OfflineI1Oracle.RunState deniedQueue = runI1Fixed("OFF-SYN-013", 2);
    OfflineI1Oracle.QueueRow deniedRow = lastQueue(deniedQueue);
    if (deniedQueue.stateVector().queue() != OfflineI1Oracle.QueueState.WAITING_NETWORK
        || deniedRow.attemptCount() != 0
        || deniedRow.effectCount() != 0
        || deniedRow.applyCount() != 0) {
      throw fault(Diagnostic.I1_WITNESS_MISMATCH);
    }

    OfflineI1Oracle.RunState localWhileQueued = runI1Fixed("OFF-SYN-014", 5);
    OfflineI1Oracle.QueueRow localQueueRow = lastQueue(localWhileQueued);
    if (localWhileQueued.stateVector().local()
            != OfflineI1Oracle.LocalState.LOCAL_OPERATION_SUCCEEDED
        || localWhileQueued.stateVector().queue()
            != OfflineI1Oracle.QueueState.WAITING_NETWORK
        || localQueueRow.attemptCount() != 0) {
      throw fault(Diagnostic.I1_WITNESS_MISMATCH);
    }

    OfflineI1Oracle.RunState snapshotSeed = OfflineI1Oracle.startScenario("OFF-SYN-015");
    OfflineI1Oracle.Step snapshotStep = OfflineI1Oracle.execute(snapshotSeed);
    byte[] snapshotBytes = snapshotStep.snapshotBytes();
    if (snapshotBytes == null) {
      throw fault(Diagnostic.I1_WITNESS_MISMATCH);
    }
    OfflineI1Oracle.Step restoredStep =
        OfflineI1Oracle.execute(snapshotStep.runState(), snapshotBytes);
    if (restoredStep.result().outcome() != OfflineI1Oracle.ReducerOutcome.TRANSITION_APPLIED) {
      throw fault(Diagnostic.I1_WITNESS_MISMATCH);
    }

    OfflineI1Oracle.RunState reconnect = runI1Fixed("OFF-SYN-017", 5);
    OfflineI1Oracle.QueueRow reconnectRow = lastQueue(reconnect);
    if (reconnect.stateVector().queue() != OfflineI1Oracle.QueueState.APPLIED
        || reconnectRow.attemptCount() != 1
        || reconnectRow.effectCount() != 1
        || reconnectRow.applyCount() != 1) {
      throw fault(Diagnostic.I1_WITNESS_MISMATCH);
    }

    OfflineI1Oracle.RunState replay = runI1Fixed("OFF-SYN-018", 2);
    OfflineI1Oracle.QueueRow replayRow = lastQueue(replay);
    if (replay.stateVector().queue() != OfflineI1Oracle.QueueState.APPLIED
        || replayRow.attemptCount() != 2
        || replayRow.effectCount() != 1
        || replayRow.applyCount() != 1) {
      throw fault(Diagnostic.I1_WITNESS_MISMATCH);
    }

    return new I1Witnesses(
        witnessDigest(modelAbsent),
        witnessDigest(deniedQueue),
        witnessDigest(localWhileQueued),
        witnessDigest(restoredStep.runState()),
        witnessDigest(reconnect),
        witnessDigest(replay));
  }

  private static OfflineI1Oracle.RunState runI1Fixed(String scenarioId, int actionCount) {
    OfflineI1Oracle.RunState state = OfflineI1Oracle.startScenario(scenarioId);
    for (int index = 0; index < actionCount; index++) {
      OfflineI1Oracle.Step step = OfflineI1Oracle.execute(state);
      if (step.result().outcome() == OfflineI1Oracle.ReducerOutcome.INVALID_INPUT) {
        throw fault(Diagnostic.I1_WITNESS_MISMATCH);
      }
      state = step.runState();
    }
    return state;
  }

  private static OfflineI1Oracle.QueueRow lastQueue(OfflineI1Oracle.RunState state) {
    if (state.queueLedger().isEmpty()) {
      throw fault(Diagnostic.I1_WITNESS_MISMATCH);
    }
    return state.queueLedger().get(state.queueLedger().size() - 1);
  }

  private static String witnessDigest(OfflineI1Oracle.RunState state) {
    OfflineI1Oracle.QueueRow queue =
        state.queueLedger().isEmpty()
            ? null
            : state.queueLedger().get(state.queueLedger().size() - 1);
    return digest(
        "I1_WITNESS_V1",
        state.scenarioId(),
        Integer.toString(state.nextActionOrdinal()),
        OfflineI1Oracle.stateVectorDigest(state.stateVector()),
        Integer.toString(state.flowLedger().size()),
        Integer.toString(state.queueLedger().size()),
        queue == null ? "NULL" : Long.toString(queue.attemptCount()),
        queue == null ? "NULL" : Integer.toString(queue.effectCount()),
        queue == null ? "NULL" : Integer.toString(queue.applyCount()));
  }

  private static String digestWitnesses(I1Witnesses witnesses) {
    return digest(
        "I1_WITNESS_SET_V1",
        witnesses.modelAbsentDigest(),
        witnesses.deniedQueueDigest(),
        witnesses.localWhileQueuedDigest(),
        witnesses.snapshotContinuationDigest(),
        witnesses.reconnectExactlyOnceDigest(),
        witnesses.replayExactlyOnceDigest());
  }

  private static IntegratedState update(
      IntegratedState prior,
      LocalPhase phase,
      OfflineI1Oracle.ProcessingState processing,
      OfflineI1Oracle.ConnectivityState connectivity,
      OfflineI1Oracle.ModelState model,
      OfflineI1Oracle.QueueState queue,
      LocalAggregate local,
      QueueProjection projection) {
    return new IntegratedState(
        prior.profile(),
        prior.nextActionIndex() + 1,
        phase,
        processing,
        connectivity,
        model,
        queue,
        local,
        projection);
  }

  private static IntegratedState advance(IntegratedState state) {
    return new IntegratedState(
        state.profile(),
        state.nextActionIndex() + 1,
        state.localPhase(),
        state.processing(),
        state.connectivity(),
        state.model(),
        state.queue(),
        state.local(),
        state.queueProjection());
  }

  private static IntegratedState copyState(IntegratedState state) {
    LocalAggregate local = state.local();
    LocalAggregate localCopy =
        new LocalAggregate(
            local.conversationIdHash(),
            local.sourceDigest(),
            local.durableDigest(),
            local.transcriptDigest(),
            local.protocolDigest(),
            local.taskDigest(),
            local.taskSourceProtocolDigest(),
            local.copyDigest(),
            local.exportDigest(),
            local.revision(),
            local.protocolOwner());
    QueueProjection queue = state.queueProjection();
    QueueProjection queueCopy =
        queue == null
            ? null
            : new QueueProjection(
                queue.intentIdHash(),
                queue.canonicalInputDigest(),
                queue.state(),
                queue.attemptCount(),
                queue.effectCount(),
                queue.applyCount(),
                queue.replayMarker());
    return new IntegratedState(
        state.profile(),
        state.nextActionIndex(),
        state.localPhase(),
        state.processing(),
        state.connectivity(),
        state.model(),
        state.queue(),
        localCopy,
        queueCopy);
  }

  private static LocalAggregate replaceLocal(
      LocalAggregate prior,
      String sourceDigest,
      String durableDigest,
      String transcriptDigest,
      String protocolDigest,
      String taskDigest,
      String taskSourceProtocolDigest,
      String copyDigest,
      String exportDigest,
      long revision,
      FieldOwner owner) {
    return new LocalAggregate(
        prior.conversationIdHash(),
        sourceDigest,
        durableDigest,
        transcriptDigest,
        protocolDigest,
        taskDigest,
        taskSourceProtocolDigest,
        copyDigest,
        exportDigest,
        revision,
        owner);
  }

  private static QueueProjection replaceQueue(
      QueueProjection prior,
      OfflineI1Oracle.QueueState state,
      long attemptCount,
      int effectCount,
      int applyCount,
      ReplayMarker marker) {
    return new QueueProjection(
        prior.intentIdHash(),
        prior.canonicalInputDigest(),
        state,
        attemptCount,
        effectCount,
        applyCount,
        marker);
  }

  private static void validateState(
      ModelProfile profile,
      int nextActionIndex,
      LocalPhase phase,
      OfflineI1Oracle.ProcessingState processing,
      OfflineI1Oracle.ConnectivityState connectivity,
      OfflineI1Oracle.ModelState model,
      OfflineI1Oracle.QueueState queue,
      LocalAggregate local,
      QueueProjection projection) {
    OfflineI1Oracle.ModelState expectedModel =
        profile == ModelProfile.MODEL_ABSENT
            ? OfflineI1Oracle.ModelState.MODEL_NOT_INSTALLED
            : OfflineI1Oracle.ModelState.MODEL_UNAVAILABLE_OR_INVALID;
    if (model != expectedModel) {
      throw fault(Diagnostic.MODEL_ADMISSION_VIOLATION);
    }
    if (profile == ModelProfile.MODEL_PRESENT_UNAPPROVED
        && model == OfflineI1Oracle.ModelState.MODEL_INSTALLED_APPROVED) {
      throw fault(Diagnostic.MODEL_ADMISSION_VIOLATION);
    }
    if (phase != expectedLocalPhase(nextActionIndex)) {
      throw fault(Diagnostic.INVALID_STATE);
    }
    validateLocalPhase(phase, local);
    if (nextActionIndex >= 10) {
      OfflineI1Oracle.ProcessingState expectedProcessing =
          profile == ModelProfile.MODEL_ABSENT
              ? OfflineI1Oracle.ProcessingState.WAITING_MODEL
              : OfflineI1Oracle.ProcessingState.PROCESSING_FAILED_SCOPED;
      if (processing != expectedProcessing) {
        throw fault(Diagnostic.MODEL_ADMISSION_VIOLATION);
      }
    } else if (nextActionIndex >= 3) {
      if (processing != OfflineI1Oracle.ProcessingState.PROCESSING_SUCCEEDED) {
        throw fault(Diagnostic.INVALID_STATE);
      }
    } else if (processing != OfflineI1Oracle.ProcessingState.PROCESSING_NOT_REQUESTED) {
      throw fault(Diagnostic.INVALID_STATE);
    }

    if (nextActionIndex <= 10) {
      if (queue != OfflineI1Oracle.QueueState.LOCAL_ONLY || projection != null) {
        throw fault(Diagnostic.INVALID_STATE);
      }
    } else {
      if (projection == null || projection.state() != queue) {
        throw fault(Diagnostic.INVALID_STATE);
      }
      if (!expectedQueueIntent(local).equals(projection.intentIdHash())
          || !expectedCanonicalQueueInput(local).equals(projection.canonicalInputDigest())) {
        throw fault(Diagnostic.QUEUE_IDENTITY_CHANGED);
      }
      switch (nextActionIndex) {
        case 11 ->
            requireQueueLifecycle(
                queue,
                projection,
                OfflineI1Oracle.QueueState.PENDING_UPLOAD,
                0,
                0,
                0,
                ReplayMarker.ORIGINAL);
        case 12, 13, 14 ->
            requireQueueLifecycle(
                queue,
                projection,
                OfflineI1Oracle.QueueState.WAITING_NETWORK,
                0,
                0,
                0,
                ReplayMarker.ORIGINAL);
        case 15 ->
            requireQueueLifecycle(
                queue,
                projection,
                OfflineI1Oracle.QueueState.CONFLICT,
                1,
                1,
                0,
                ReplayMarker.ORIGINAL);
        case 16 ->
            requireQueueLifecycle(
                queue,
                projection,
                OfflineI1Oracle.QueueState.CONFLICT,
                2,
                1,
                0,
                ReplayMarker.SAME_INPUT_REPLAY);
        default -> throw fault(Diagnostic.INVALID_STATE);
      }
    }
    if (nextActionIndex < 15
        && connectivity != OfflineI1Oracle.ConnectivityState.NETWORK_DENIED) {
      throw fault(Diagnostic.INVALID_STATE);
    }
    if (nextActionIndex >= 15
        && connectivity != OfflineI1Oracle.ConnectivityState.AVAILABLE) {
      throw fault(Diagnostic.INVALID_STATE);
    }
  }

  private static LocalPhase expectedLocalPhase(int nextActionIndex) {
    return switch (nextActionIndex) {
      case 0 -> LocalPhase.FRESH_LOCAL_DEFAULT;
      case 1 -> LocalPhase.CAPTURED_SYNTHETIC;
      case 2 -> LocalPhase.SAVED_LOCAL;
      case 3 -> LocalPhase.RULES_PROCESSED;
      case 4 -> LocalPhase.HISTORY_OPENED;
      case 5 -> LocalPhase.SEARCH_MATCHED;
      case 6 -> LocalPhase.TASK_CREATED;
      case 7 -> LocalPhase.USER_EDITED;
      case 8 -> LocalPhase.COPIED_LOCAL;
      case 9, 10, 11, 12, 13, 14, 15, 16 -> LocalPhase.EXPORTED_LOCAL;
      default -> throw fault(Diagnostic.INVALID_ORDER);
    };
  }

  private static void requireQueueLifecycle(
      OfflineI1Oracle.QueueState queue,
      QueueProjection projection,
      OfflineI1Oracle.QueueState expectedState,
      long expectedAttempts,
      int expectedEffects,
      int expectedApplies,
      ReplayMarker expectedMarker) {
    if (queue != expectedState
        || projection.state() != expectedState
        || projection.attemptCount() != expectedAttempts
        || projection.effectCount() != expectedEffects
        || projection.applyCount() != expectedApplies
        || projection.replayMarker() != expectedMarker) {
      throw fault(Diagnostic.INVALID_STATE);
    }
  }

  private static void validateLocalPhase(LocalPhase phase, LocalAggregate local) {
    int ordinal = phase.ordinal();
    requirePresence(local.sourceDigest(), ordinal >= LocalPhase.CAPTURED_SYNTHETIC.ordinal());
    requirePresence(local.durableDigest(), ordinal >= LocalPhase.SAVED_LOCAL.ordinal());
    requirePresence(local.transcriptDigest(), ordinal >= LocalPhase.RULES_PROCESSED.ordinal());
    requirePresence(local.protocolDigest(), ordinal >= LocalPhase.RULES_PROCESSED.ordinal());
    requirePresence(local.taskDigest(), ordinal >= LocalPhase.TASK_CREATED.ordinal());
    requirePresence(
        local.taskSourceProtocolDigest(), ordinal >= LocalPhase.TASK_CREATED.ordinal());
    requirePresence(local.copyDigest(), ordinal >= LocalPhase.COPIED_LOCAL.ordinal());
    requirePresence(local.exportDigest(), ordinal >= LocalPhase.EXPORTED_LOCAL.ordinal());
    if (ordinal >= LocalPhase.USER_EDITED.ordinal()) {
      if (local.protocolOwner() != FieldOwner.USER || local.revision() != 1) {
        throw fault(Diagnostic.USER_OWNERSHIP_VIOLATION);
      }
    } else if (local.protocolOwner() != FieldOwner.MACHINE || local.revision() != 0) {
      throw fault(Diagnostic.INVALID_STATE);
    }
    if (ordinal >= LocalPhase.TASK_CREATED.ordinal()
        && Objects.equals(local.protocolDigest(), local.taskSourceProtocolDigest())
            != (ordinal < LocalPhase.USER_EDITED.ordinal())) {
      throw fault(Diagnostic.INVALID_STATE);
    }
    if (ordinal >= LocalPhase.EXPORTED_LOCAL.ordinal()) {
      requireLocalLineage(local);
    }
  }

  private static void requireLocalLineage(LocalAggregate local) {
    String expectedCopy =
        digest(
            "LOCAL_COPY_V1",
            local.conversationIdHash(),
            local.transcriptDigest(),
            local.protocolDigest(),
            local.taskDigest(),
            "revision=1");
    String expectedExport =
        digest("LOCAL_EXPORT_V1", local.conversationIdHash(), expectedCopy);
    if (!expectedCopy.equals(local.copyDigest())
        || !expectedExport.equals(local.exportDigest())
        || local.protocolOwner() != FieldOwner.USER
        || local.revision() != 1
        || Objects.equals(local.protocolDigest(), local.taskSourceProtocolDigest())) {
      throw fault(Diagnostic.USER_OWNERSHIP_VIOLATION);
    }
  }

  private static String expectedQueueIntent(LocalAggregate local) {
    return digest("QUEUE_INTENT_ID_V1", QUEUE_ID, local.conversationIdHash());
  }

  private static String expectedCanonicalQueueInput(LocalAggregate local) {
    return digest("QUEUE_INPUT_V1", local.conversationIdHash(), local.exportDigest());
  }

  private static String digestQueue(QueueProjection queue) {
    return digest(
        "QUEUE_PROJECTION_V1",
        queue.intentIdHash(),
        queue.canonicalInputDigest(),
        queue.state().name(),
        Long.toString(queue.attemptCount()),
        Integer.toString(queue.effectCount()),
        Integer.toString(queue.applyCount()),
        queue.replayMarker().name());
  }

  private static long checkedIncrement(long value) {
    if (value < 0 || value >= MAX_ATTEMPTS) {
      throw fault(Diagnostic.ATTEMPT_BUDGET_EXCEEDED);
    }
    return value + 1;
  }

  private static String digest(String domain, String... fields) {
    requirePresent(domain);
    requirePresent(fields);
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      updateFramed(messageDigest, HARNESS_ID);
      updateFramed(messageDigest, domain);
      for (String field : fields) {
        updateFramed(messageDigest, requirePresent(field));
      }
      return toHex(messageDigest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }

  private static void updateFramed(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
    digest.update((byte) ':');
    digest.update(bytes);
    digest.update((byte) '\n');
  }

  private static String toHex(byte[] bytes) {
    char[] alphabet = "0123456789abcdef".toCharArray();
    char[] encoded = new char[bytes.length * 2];
    for (int index = 0; index < bytes.length; index++) {
      int value = bytes[index] & 0xff;
      encoded[index * 2] = alphabet[value >>> 4];
      encoded[index * 2 + 1] = alphabet[value & 0x0f];
    }
    return new String(encoded);
  }

  private static String nullable(String value) {
    return value == null ? "NULL" : value;
  }

  private static void requirePresence(String value, boolean required) {
    if (required != (value != null)) {
      throw fault(Diagnostic.INVALID_STATE);
    }
  }

  private static void requireNullableHash(String value) {
    if (value != null) {
      requireHash(value);
    }
  }

  private static void requireHash(String value) {
    if (value == null || !HASH.matcher(value).matches()) {
      throw fault(Diagnostic.INVALID_DIGEST);
    }
  }

  private static void requireBit(int value) {
    if (value != 0 && value != 1) {
      throw fault(Diagnostic.INVALID_STATE);
    }
  }

  private static <T> T requirePresent(T value) {
    if (value == null) {
      throw fault(Diagnostic.INVALID_NULL);
    }
    return value;
  }

  private static HarnessFault fault(Diagnostic diagnostic) {
    return new HarnessFault(diagnostic);
  }

  record Transition(IntegratedState state, Outcome outcome) {
    Transition {
      requirePresent(state);
      requirePresent(outcome);
    }
  }

  static final class HarnessFault extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final Diagnostic diagnostic;

    HarnessFault(Diagnostic diagnostic) {
      super(requirePresent(diagnostic).name());
      this.diagnostic = diagnostic;
    }

    Diagnostic diagnostic() {
      return diagnostic;
    }
  }
}
