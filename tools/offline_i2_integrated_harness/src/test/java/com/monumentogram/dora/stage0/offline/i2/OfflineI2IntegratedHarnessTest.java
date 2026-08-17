package com.monumentogram.dora.stage0.offline.i2;

import com.monumentogram.dora.stage0.offline.i1.OfflineI1Oracle;
import java.util.ArrayList;
import java.util.List;

/** Executable dependency-free checks for the fixed Offline I2 synthetic integration contour. */
public final class OfflineI2IntegratedHarnessTest {
  private static final String ZERO_HASH = "0".repeat(64);
  private static int assertions;

  private OfflineI2IntegratedHarnessTest() {}

  public static void main(String[] args) {
    testCatalogAndExactOrder();
    testKnownAnswersAndDeterminism();
    testIntegratedLineageAndModelBoundary();
    testQueueSnapshotConflictAndReplay();
    testI1Witnesses();
    testRunResultEnvelopeBindingsFailClosed();
    testFailClosedBeforeMutation();
    testQueueAndTraceBindingsFailClosed();
    testImmutabilityAndContentFreeShape();
    check(assertions >= 180, "T-ASSERTION-FLOOR");
    System.out.print("PASS offline-i2-integrated-synthetic-harness");
  }

  private static void testCatalogAndExactOrder() {
    List<OfflineI2IntegratedHarness.Action> expected =
        List.of(
            OfflineI2IntegratedHarness.Action.CAPTURE_SYNTHETIC_SOURCE,
            OfflineI2IntegratedHarness.Action.SAVE_LOCAL_SOURCE,
            OfflineI2IntegratedHarness.Action.PROCESS_RULES_ONLY,
            OfflineI2IntegratedHarness.Action.OPEN_HISTORY,
            OfflineI2IntegratedHarness.Action.SEARCH_LOCAL_INDEX,
            OfflineI2IntegratedHarness.Action.CREATE_LOCAL_TASK,
            OfflineI2IntegratedHarness.Action.EDIT_LOCAL_PROTOCOL,
            OfflineI2IntegratedHarness.Action.COPY_LOCAL_REPRESENTATION,
            OfflineI2IntegratedHarness.Action.EXPORT_LOCAL_REPRESENTATION,
            OfflineI2IntegratedHarness.Action.REQUEST_REQUIRED_MODEL,
            OfflineI2IntegratedHarness.Action.ENQUEUE_CLOUD_INTENT,
            OfflineI2IntegratedHarness.Action.DENIED_SCHEDULER_TICK,
            OfflineI2IntegratedHarness.Action.REPEAT_LOCAL_WHILE_WAITING,
            OfflineI2IntegratedHarness.Action.SNAPSHOT_AND_RESTORE,
            OfflineI2IntegratedHarness.Action.PROJECT_RECONNECT_CONFLICT,
            OfflineI2IntegratedHarness.Action.PROJECT_SAME_INPUT_REPLAY);
    equal(expected, OfflineI2IntegratedHarness.actionOrder(), "T-CATALOG-ORDER");
    check(expected.size() == 16, "T-CATALOG-COUNT");
    check(
        OfflineI2IntegratedHarness.ModelProfile.values().length == 2,
        "T-CATALOG-MODEL-PROFILES");
    check(
        OfflineI2IntegratedHarness.Action.values().length == expected.size(),
        "T-CATALOG-ACTIONS");
    check(
        OfflineI2IntegratedHarness.PROOF_CLASS.equals(
            "PURE_HOST_SYNTHETIC_IN_MEMORY_SEMANTICS_NOT_DEVICE_NETWORK_OR_PRODUCT"),
        "T-CATALOG-PROOF-CLASS");
  }

  private static void testKnownAnswersAndDeterminism() {
    OfflineI2IntegratedHarness.RunResult absent1 =
        OfflineI2IntegratedHarness.run(
            OfflineI2IntegratedHarness.ModelProfile.MODEL_ABSENT);
    OfflineI2IntegratedHarness.RunResult absent2 =
        OfflineI2IntegratedHarness.run(
            OfflineI2IntegratedHarness.ModelProfile.MODEL_ABSENT);
    OfflineI2IntegratedHarness.RunResult present1 =
        OfflineI2IntegratedHarness.run(
            OfflineI2IntegratedHarness.ModelProfile.MODEL_PRESENT_UNAPPROVED);
    OfflineI2IntegratedHarness.RunResult present2 =
        OfflineI2IntegratedHarness.run(
            OfflineI2IntegratedHarness.ModelProfile.MODEL_PRESENT_UNAPPROVED);

    equal(absent1, absent2, "T-DETERMINISM-ABSENT");
    equal(present1, present2, "T-DETERMINISM-PRESENT");
    equal(
        "fc222dc7240b0137014b9c5928eb5ed4e3dfd23cd74c94b2bf93a6f0d4d80071",
        absent1.summaryDigest(),
        "T-KAT-ABSENT-SUMMARY");
    equal(
        "c192a29008965a55e7720daec676921586af7eb2df071bc7f03dd071114ef685",
        present1.summaryDigest(),
        "T-KAT-PRESENT-SUMMARY");
    equal(
        "2a1fb678219c16711b0deff2e429dd454dbdb508ad514c94fbd1168563910fb5",
        absent1.traceDigest(),
        "T-KAT-ABSENT-TRACE");
    equal(
        "07092ac03841f5e9b69734e6b48cf9eabf62aecf61b61ae55f1936ba4b18db3c",
        present1.traceDigest(),
        "T-KAT-PRESENT-TRACE");
    check(!absent1.summaryDigest().equals(present1.summaryDigest()), "T-PROFILE-DISTINCT");
    check(!absent1.traceDigest().equals(present1.traceDigest()), "T-TRACE-DISTINCT");
  }

  private static void testIntegratedLineageAndModelBoundary() {
    OfflineI2IntegratedHarness.RunResult absent =
        OfflineI2IntegratedHarness.run(
            OfflineI2IntegratedHarness.ModelProfile.MODEL_ABSENT);
    OfflineI2IntegratedHarness.RunResult present =
        OfflineI2IntegratedHarness.run(
            OfflineI2IntegratedHarness.ModelProfile.MODEL_PRESENT_UNAPPROVED);
    OfflineI2IntegratedHarness.LocalAggregate local = absent.finalState().local();

    equal(
        "cfc1c032ec573d792fcbf2a828dab13781d0f04dddfb44e4859afdbd164ef7fb",
        local.conversationIdHash(),
        "T-LINEAGE-CONVERSATION");
    equal(
        "62afd6ae53abf9c531354c36f47d5363351da2c68ba35a853c8d14819f863117",
        local.sourceDigest(),
        "T-LINEAGE-SOURCE");
    equal(
        "d2d5e28795cd741aa5d4317d2fe418e9e5869607723cba9845fb7bf7dc0c98a2",
        local.durableDigest(),
        "T-LINEAGE-DURABLE");
    equal(
        "ae319be3080dfaf4b3cfad536e393931d3b3d76c79e1968aa0b392a788262e84",
        local.transcriptDigest(),
        "T-LINEAGE-TRANSCRIPT");
    equal(
        "4ad1e273c9c31e859fb30bb512db7d6d67450f78851cffeb7e3704e76a3d1812",
        local.protocolDigest(),
        "T-LINEAGE-PROTOCOL");
    equal(
        "377852ed029525fcdf09b4d5486f046801d13d6ef08a89421093816b93ef65e4",
        local.taskDigest(),
        "T-LINEAGE-TASK");
    equal(
        "454dc56497b73c3ca66dea31b99621680c1898534bb6a043e6fa6ed7ffd5b309",
        local.taskSourceProtocolDigest(),
        "T-LINEAGE-TASK-SOURCE");
    equal(
        "a69d12d5d8808a99f5b22979650c5b664cd58daeaee1785590b479b433ce490f",
        local.copyDigest(),
        "T-LINEAGE-COPY");
    equal(
        "1a1d4e5e284e537f79679ced27b024eb7cd7d1ee241413ce7a77edccd035a560",
        local.exportDigest(),
        "T-LINEAGE-EXPORT");
    check(local.revision() == 1, "T-LINEAGE-REVISION");
    check(
        local.protocolOwner() == OfflineI2IntegratedHarness.FieldOwner.USER,
        "T-LINEAGE-OWNER");
    check(
        !local.protocolDigest().equals(local.taskSourceProtocolDigest()),
        "T-LINEAGE-USER-EDIT-DISTINCT");
    equal(local, present.finalState().local(), "T-PROFILES-SAME-LOCAL-CORE");
    equal(
        absent.finalState().queueProjection(),
        present.finalState().queueProjection(),
        "T-PROFILES-SAME-QUEUE");

    check(
        absent.finalState().processing() == OfflineI1Oracle.ProcessingState.WAITING_MODEL,
        "T-MODEL-ABSENT-WAIT");
    check(
        absent.finalState().model() == OfflineI1Oracle.ModelState.MODEL_NOT_INSTALLED,
        "T-MODEL-ABSENT-STATE");
    check(
        present.finalState().processing()
            == OfflineI1Oracle.ProcessingState.PROCESSING_FAILED_SCOPED,
        "T-MODEL-PRESENT-BLOCKED");
    check(
        present.finalState().model()
            == OfflineI1Oracle.ModelState.MODEL_UNAVAILABLE_OR_INVALID,
        "T-MODEL-PRESENT-UNAPPROVED");
    check(
        present.finalState().model() != OfflineI1Oracle.ModelState.MODEL_INSTALLED_APPROVED,
        "T-MODEL-NO-FALSE-ADMISSION");
    check(
        absent.finalState().localPhase()
            == OfflineI2IntegratedHarness.LocalPhase.EXPORTED_LOCAL,
        "T-LOCAL-FINAL-ABSENT");
    check(
        present.finalState().localPhase()
            == OfflineI2IntegratedHarness.LocalPhase.EXPORTED_LOCAL,
        "T-LOCAL-FINAL-PRESENT");

    assertTrace(absent);
    assertTrace(present);
  }

  private static void assertTrace(OfflineI2IntegratedHarness.RunResult result) {
    List<OfflineI2IntegratedHarness.Action> order = OfflineI2IntegratedHarness.actionOrder();
    check(result.trace().size() == order.size(), "T-TRACE-SIZE-" + result.profile());
    String previous = null;
    for (int index = 0; index < result.trace().size(); index++) {
      OfflineI2IntegratedHarness.TraceRow row = result.trace().get(index);
      check(row.sequence() == index + 1L, "T-TRACE-SEQUENCE-" + result.profile() + "-" + index);
      check(row.action() == order.get(index), "T-TRACE-ACTION-" + result.profile() + "-" + index);
      check(row.preStateDigest().length() == 64, "T-TRACE-PRE-" + result.profile() + "-" + index);
      check(row.postStateDigest().length() == 64, "T-TRACE-POST-" + result.profile() + "-" + index);
      check(row.localAggregateDigest().length() == 64, "T-TRACE-LOCAL-" + result.profile() + "-" + index);
      if (previous != null) {
        equal(previous, row.preStateDigest(), "T-TRACE-CHAIN-" + result.profile() + "-" + index);
      }
      if (index < 10) {
        check(row.queueIntentIdHash() == null, "T-TRACE-NO-QUEUE-" + result.profile() + "-" + index);
      } else {
        check(row.queueIntentIdHash() != null, "T-TRACE-QUEUE-" + result.profile() + "-" + index);
      }
      previous = row.postStateDigest();
    }
    check(
        result.trace().get(9).outcome()
            == (result.profile() == OfflineI2IntegratedHarness.ModelProfile.MODEL_ABSENT
                ? OfflineI2IntegratedHarness.Outcome.WAITING_MODEL
                : OfflineI2IntegratedHarness.Outcome.UNAPPROVED_MODEL_BLOCKED),
        "T-TRACE-MODEL-OUTCOME-" + result.profile());
    check(
        result.trace().get(11).outcome()
            == OfflineI2IntegratedHarness.Outcome.WAITING_NETWORK_ZERO_ATTEMPTS,
        "T-TRACE-WAITING-NETWORK-" + result.profile());
    check(
        result.trace().get(12).outcome()
            == OfflineI2IntegratedHarness.Outcome.LOCAL_STATE_PRESERVED,
        "T-TRACE-LOCAL-PRESERVED-" + result.profile());
    check(
        result.trace().get(13).outcome()
            == OfflineI2IntegratedHarness.Outcome.RESTORED_IMMUTABLE_SEMANTIC_EQUALITY,
        "T-TRACE-RESTORE-" + result.profile());
    check(
        result.trace().get(14).outcome()
            == OfflineI2IntegratedHarness.Outcome.CONFLICT_USER_EDIT_PRESERVED,
        "T-TRACE-CONFLICT-" + result.profile());
    check(
        result.trace().get(15).outcome()
            == OfflineI2IntegratedHarness.Outcome.SAME_INPUT_REPLAY_ZERO_DUPLICATE_EFFECT,
        "T-TRACE-REPLAY-" + result.profile());
  }

  private static void testQueueSnapshotConflictAndReplay() {
    List<OfflineI2IntegratedHarness.TraceRow> trace = new ArrayList<>();
    OfflineI2IntegratedHarness.IntegratedState state =
        OfflineI2IntegratedHarness.initialState(
            OfflineI2IntegratedHarness.ModelProfile.MODEL_ABSENT);
    String userProtocolDigest = null;
    OfflineI2IntegratedHarness.LocalAggregate reconnectLocal = null;
    OfflineI2IntegratedHarness.QueueProjection waitingQueue = null;

    for (OfflineI2IntegratedHarness.Action action : OfflineI2IntegratedHarness.actionOrder()) {
      if (action == OfflineI2IntegratedHarness.Action.SNAPSHOT_AND_RESTORE) {
        OfflineI2IntegratedHarness.Snapshot snapshot =
            OfflineI2IntegratedHarness.snapshot(state, trace);
        OfflineI2IntegratedHarness.IntegratedState restored =
            OfflineI2IntegratedHarness.restore(snapshot, trace);
        equal(state, restored, "T-SNAPSHOT-EQUALITY");
        check(state != restored, "T-SNAPSHOT-NEW-STATE");
        check(state.local() != restored.local(), "T-SNAPSHOT-NEW-LOCAL");
        check(state.queueProjection() != restored.queueProjection(), "T-SNAPSHOT-NEW-QUEUE");
      }
      String pre = OfflineI2IntegratedHarness.digestState(state);
      OfflineI2IntegratedHarness.Transition transition =
          OfflineI2IntegratedHarness.transition(state, action, trace);
      state = transition.state();
      trace.add(
          new OfflineI2IntegratedHarness.TraceRow(
              trace.size() + 1L,
              action,
              transition.outcome(),
              pre,
              OfflineI2IntegratedHarness.digestState(state),
              OfflineI2IntegratedHarness.digestLocal(state.local()),
              state.queueProjection() == null
                  ? null
                  : state.queueProjection().intentIdHash()));

      if (action == OfflineI2IntegratedHarness.Action.EDIT_LOCAL_PROTOCOL) {
        userProtocolDigest = state.local().protocolDigest();
      }
      if (action == OfflineI2IntegratedHarness.Action.DENIED_SCHEDULER_TICK) {
        waitingQueue = state.queueProjection();
        check(waitingQueue.attemptCount() == 0, "T-QUEUE-DENIED-ATTEMPT-ZERO");
        check(waitingQueue.effectCount() == 0, "T-QUEUE-DENIED-EFFECT-ZERO");
        check(waitingQueue.applyCount() == 0, "T-QUEUE-DENIED-APPLY-ZERO");
        check(
            waitingQueue.state() == OfflineI1Oracle.QueueState.WAITING_NETWORK,
            "T-QUEUE-DENIED-STATE");
      }
      if (action == OfflineI2IntegratedHarness.Action.REPEAT_LOCAL_WHILE_WAITING) {
        equal(waitingQueue, state.queueProjection(), "T-QUEUE-LOCAL-WORK-PRESERVES-ROW");
        check(state.local().protocolOwner() == OfflineI2IntegratedHarness.FieldOwner.USER, "T-QUEUE-LOCAL-WORK-PRESERVES-OWNER");
        reconnectLocal = state.local();
      }
      if (action == OfflineI2IntegratedHarness.Action.PROJECT_RECONNECT_CONFLICT) {
        equal(reconnectLocal, state.local(), "T-CONFLICT-NO-USER-OVERWRITE");
        equal(userProtocolDigest, state.local().protocolDigest(), "T-CONFLICT-PROTOCOL-PRESERVED");
        check(state.queueProjection().attemptCount() == 1, "T-CONFLICT-ATTEMPT-ONE");
        check(state.queueProjection().effectCount() == 1, "T-CONFLICT-EFFECT-ONE");
        check(state.queueProjection().applyCount() == 0, "T-CONFLICT-APPLY-ZERO");
        check(state.queue() == OfflineI1Oracle.QueueState.CONFLICT, "T-CONFLICT-STATE");
      }
      if (action == OfflineI2IntegratedHarness.Action.PROJECT_SAME_INPUT_REPLAY) {
        equal(reconnectLocal, state.local(), "T-REPLAY-NO-USER-OVERWRITE");
        equal(userProtocolDigest, state.local().protocolDigest(), "T-REPLAY-PROTOCOL-PRESERVED");
        check(state.queueProjection().attemptCount() == 2, "T-REPLAY-ATTEMPT-TWO");
        check(state.queueProjection().effectCount() == 1, "T-REPLAY-EFFECT-STILL-ONE");
        check(state.queueProjection().applyCount() == 0, "T-REPLAY-APPLY-STILL-ZERO");
        check(
            state.queueProjection().replayMarker()
                == OfflineI2IntegratedHarness.ReplayMarker.SAME_INPUT_REPLAY,
            "T-REPLAY-MARKER");
      }
    }

    equal(
        "2c77d3e0dbe6fef1b438ba2bffbcf930481116d56af31124ee334a7e2c56b05a",
        state.queueProjection().intentIdHash(),
        "T-QUEUE-ID-KAT");
    equal(
        "c57e5514a1ce49bba8234faa30af08824361aed75dbbea67483aa2abf94eb1cf",
        state.queueProjection().canonicalInputDigest(),
        "T-QUEUE-INPUT-KAT");
    equal(
        waitingQueue.intentIdHash(),
        state.queueProjection().intentIdHash(),
        "T-QUEUE-ID-STABLE");
    equal(
        waitingQueue.canonicalInputDigest(),
        state.queueProjection().canonicalInputDigest(),
        "T-QUEUE-INPUT-STABLE");
  }

  private static void testI1Witnesses() {
    OfflineI2IntegratedHarness.I1Witnesses witnesses =
        OfflineI2IntegratedHarness.run(
                OfflineI2IntegratedHarness.ModelProfile.MODEL_ABSENT)
            .i1Witnesses();
    equal(
        "af8905cbafcb61ba64cec680e001a2399bbd442d71d92c42049768043217bd13",
        witnesses.modelAbsentDigest(),
        "T-I1-MODEL-ABSENT");
    equal(
        "bff7b94a2842026018dd6910768953f5e07954a775f37abdc3d2a8b97d5b199b",
        witnesses.deniedQueueDigest(),
        "T-I1-DENIED-QUEUE");
    equal(
        "e56ffb5df8bcfaae842d0d5638e4a71e78be45db3967edde18b8b17fd780e422",
        witnesses.localWhileQueuedDigest(),
        "T-I1-LOCAL-WHILE-QUEUED");
    equal(
        "d2f343f8b92080603cc9fde7c0c0e7298d1e4bc7907a34273971b203fb1996b6",
        witnesses.snapshotContinuationDigest(),
        "T-I1-SNAPSHOT");
    equal(
        "737413ba09679254982f939426cd603ab347f7635b4e8ec30334cfcb758416b8",
        witnesses.reconnectExactlyOnceDigest(),
        "T-I1-RECONNECT");
    equal(
        "4cf8509cc8356d7b1ffe1fe00ad9cea75c9a178a121117269dc015e9dad81ec3",
        witnesses.replayExactlyOnceDigest(),
        "T-I1-REPLAY");
  }

  private static void testRunResultEnvelopeBindingsFailClosed() {
    OfflineI2IntegratedHarness.RunResult result =
        OfflineI2IntegratedHarness.run(
            OfflineI2IntegratedHarness.ModelProfile.MODEL_ABSENT);
    OfflineI2IntegratedHarness.IntegratedState originalState = result.finalState();
    List<OfflineI2IntegratedHarness.TraceRow> originalTrace = List.copyOf(result.trace());
    OfflineI2IntegratedHarness.I1Witnesses originalWitnesses = result.i1Witnesses();
    String originalSummary = result.summaryDigest();

    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_STATE,
        () ->
            new OfflineI2IntegratedHarness.RunResult(
                OfflineI2IntegratedHarness.ModelProfile.MODEL_PRESENT_UNAPPROVED,
                result.finalState(),
                result.trace(),
                result.i1Witnesses(),
                result.traceDigest(),
                result.summaryDigest(),
                result.proofClass()),
        "T-FAIL-RUN-PROFILE-STATE-BINDING");

    String[] witnessFieldIds = {
      "MODEL-ABSENT",
      "DENIED-QUEUE",
      "LOCAL-WHILE-QUEUED",
      "SNAPSHOT-CONTINUATION",
      "RECONNECT-EXACTLY-ONCE",
      "REPLAY-EXACTLY-ONCE"
    };
    for (int index = 0; index < witnessFieldIds.length; index++) {
      OfflineI2IntegratedHarness.I1Witnesses forgedWitnesses =
          witnessWithTamper(result.i1Witnesses(), index);
      expectFault(
          OfflineI2IntegratedHarness.Diagnostic.I1_WITNESS_MISMATCH,
          () ->
              new OfflineI2IntegratedHarness.RunResult(
                  result.profile(),
                  result.finalState(),
                  result.trace(),
                  forgedWitnesses,
                  result.traceDigest(),
                  result.summaryDigest(),
                  result.proofClass()),
          "T-FAIL-RUN-WITNESS-" + witnessFieldIds[index]);
    }

    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_STATE,
        () ->
            new OfflineI2IntegratedHarness.RunResult(
                result.profile(),
                result.finalState(),
                result.trace(),
                result.i1Witnesses(),
                result.traceDigest(),
                ZERO_HASH,
                result.proofClass()),
        "T-FAIL-RUN-SUMMARY-BINDING");

    List<OfflineI2IntegratedHarness.TraceRow> mutableTrace =
        new ArrayList<>(result.trace());
    OfflineI2IntegratedHarness.RunResult rebound =
        new OfflineI2IntegratedHarness.RunResult(
            result.profile(),
            result.finalState(),
            mutableTrace,
            result.i1Witnesses(),
            result.traceDigest(),
            result.summaryDigest(),
            result.proofClass());
    mutableTrace.clear();
    equal(originalTrace, rebound.trace(), "T-RUN-ENVELOPE-COPIES-TRACE");
    equal(originalState, result.finalState(), "T-RUN-ENVELOPE-NO-STATE-MUTATION");
    equal(originalTrace, result.trace(), "T-RUN-ENVELOPE-NO-TRACE-MUTATION");
    equal(
        originalWitnesses,
        result.i1Witnesses(),
        "T-RUN-ENVELOPE-NO-WITNESS-MUTATION");
    equal(originalSummary, result.summaryDigest(), "T-RUN-ENVELOPE-NO-SUMMARY-MUTATION");
  }

  private static OfflineI2IntegratedHarness.I1Witnesses witnessWithTamper(
      OfflineI2IntegratedHarness.I1Witnesses prior, int fieldIndex) {
    return new OfflineI2IntegratedHarness.I1Witnesses(
        fieldIndex == 0 ? ZERO_HASH : prior.modelAbsentDigest(),
        fieldIndex == 1 ? ZERO_HASH : prior.deniedQueueDigest(),
        fieldIndex == 2 ? ZERO_HASH : prior.localWhileQueuedDigest(),
        fieldIndex == 3 ? ZERO_HASH : prior.snapshotContinuationDigest(),
        fieldIndex == 4 ? ZERO_HASH : prior.reconnectExactlyOnceDigest(),
        fieldIndex == 5 ? ZERO_HASH : prior.replayExactlyOnceDigest());
  }

  private static void testFailClosedBeforeMutation() {
    OfflineI2IntegratedHarness.IntegratedState initial =
        OfflineI2IntegratedHarness.initialState(
            OfflineI2IntegratedHarness.ModelProfile.MODEL_ABSENT);
    OfflineI2IntegratedHarness.IntegratedState immutableWitness = initial;
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_ORDER,
        () ->
            OfflineI2IntegratedHarness.transition(
                initial,
                OfflineI2IntegratedHarness.Action.SAVE_LOCAL_SOURCE,
                List.of()),
        "T-FAIL-ORDER");
    equal(immutableWitness, initial, "T-FAIL-ORDER-NO-MUTATION");

    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_NULL,
        () -> OfflineI2IntegratedHarness.run(null),
        "T-FAIL-NULL-PROFILE");

    List<OfflineI2IntegratedHarness.TraceRow> trace = new ArrayList<>();
    OfflineI2IntegratedHarness.IntegratedState waiting = initial;
    for (int index = 0; index <= 12; index++) {
      OfflineI2IntegratedHarness.Action action = OfflineI2IntegratedHarness.actionOrder().get(index);
      String pre = OfflineI2IntegratedHarness.digestState(waiting);
      OfflineI2IntegratedHarness.Transition transition =
          OfflineI2IntegratedHarness.transition(waiting, action, trace);
      waiting = transition.state();
      trace.add(
          new OfflineI2IntegratedHarness.TraceRow(
              trace.size() + 1L,
              action,
              transition.outcome(),
              pre,
              OfflineI2IntegratedHarness.digestState(waiting),
              OfflineI2IntegratedHarness.digestLocal(waiting.local()),
              waiting.queueProjection() == null
                  ? null
                  : waiting.queueProjection().intentIdHash()));
    }
    OfflineI2IntegratedHarness.IntegratedState waitingWitness = waiting;
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.ATTEMPT_WHILE_DENIED,
        () ->
            OfflineI2IntegratedHarness.projectAttempt(
                waitingWitness, OfflineI1Oracle.ConnectivityState.NETWORK_DENIED),
        "T-FAIL-DENIED-ATTEMPT");
    equal(waitingWitness, waiting, "T-FAIL-DENIED-NO-MUTATION");

    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_SNAPSHOT,
        () -> new OfflineI2IntegratedHarness.Snapshot(waitingWitness, ZERO_HASH, OfflineI2IntegratedHarness.digestTrace(trace)),
        "T-FAIL-SNAPSHOT-TAMPER");
    OfflineI2IntegratedHarness.Snapshot validStateWrongTrace =
        new OfflineI2IntegratedHarness.Snapshot(
            waitingWitness,
            OfflineI2IntegratedHarness.digestState(waitingWitness),
            ZERO_HASH);
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_SNAPSHOT,
        () -> OfflineI2IntegratedHarness.restore(validStateWrongTrace, trace),
        "T-FAIL-SNAPSHOT-TRACE-TAMPER");

    OfflineI2IntegratedHarness.QueueProjection queue = waiting.queueProjection();
    OfflineI2IntegratedHarness.QueueProjection wrongQueue =
        new OfflineI2IntegratedHarness.QueueProjection(
            ZERO_HASH,
            queue.canonicalInputDigest(),
            queue.state(),
            queue.attemptCount(),
            queue.effectCount(),
            queue.applyCount(),
            queue.replayMarker());
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.QUEUE_IDENTITY_CHANGED,
        () ->
            new OfflineI2IntegratedHarness.IntegratedState(
                waitingWitness.profile(),
                waitingWitness.nextActionIndex(),
                waitingWitness.localPhase(),
                waitingWitness.processing(),
                waitingWitness.connectivity(),
                waitingWitness.model(),
                waitingWitness.queue(),
                waitingWitness.local(),
                wrongQueue),
        "T-FAIL-QUEUE-ID");

    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.MODEL_ADMISSION_VIOLATION,
        () ->
            new OfflineI2IntegratedHarness.IntegratedState(
                OfflineI2IntegratedHarness.ModelProfile.MODEL_PRESENT_UNAPPROVED,
                0,
                OfflineI2IntegratedHarness.LocalPhase.FRESH_LOCAL_DEFAULT,
                OfflineI1Oracle.ProcessingState.PROCESSING_NOT_REQUESTED,
                OfflineI1Oracle.ConnectivityState.NETWORK_DENIED,
                OfflineI1Oracle.ModelState.MODEL_INSTALLED_APPROVED,
                OfflineI1Oracle.QueueState.LOCAL_ONLY,
                initial.local(),
                null),
        "T-FAIL-MODEL-ADMISSION");

    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_STATE,
        () ->
            new OfflineI2IntegratedHarness.QueueProjection(
                queue.intentIdHash(),
                queue.canonicalInputDigest(),
                queue.state(),
                0,
                2,
                0,
                queue.replayMarker()),
        "T-FAIL-EFFECT-BOUND");
  }

  private static void testQueueAndTraceBindingsFailClosed() {
    Prefix waiting = prefix(OfflineI2IntegratedHarness.ModelProfile.MODEL_ABSENT, 13);
    OfflineI2IntegratedHarness.IntegratedState waitingState = waiting.state();
    OfflineI2IntegratedHarness.QueueProjection waitingQueue = waitingState.queueProjection();

    OfflineI2IntegratedHarness.QueueProjection wrongInput =
        queueWith(
            waitingQueue,
            ZERO_HASH,
            waitingQueue.state(),
            waitingQueue.attemptCount(),
            waitingQueue.effectCount(),
            waitingQueue.applyCount(),
            waitingQueue.replayMarker());
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.QUEUE_IDENTITY_CHANGED,
        () -> stateWith(waitingState, waitingState.localPhase(), waitingState.queue(), wrongInput),
        "T-FAIL-QUEUE-CANONICAL-INPUT-BINDING");

    OfflineI2IntegratedHarness.QueueProjection foreignQueueState =
        queueWith(
            waitingQueue,
            waitingQueue.canonicalInputDigest(),
            OfflineI1Oracle.QueueState.FAILED_FINAL,
            0,
            0,
            0,
            OfflineI2IntegratedHarness.ReplayMarker.ORIGINAL);
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_STATE,
        () ->
            stateWith(
                waitingState,
                waitingState.localPhase(),
                OfflineI1Oracle.QueueState.FAILED_FINAL,
                foreignQueueState),
        "T-FAIL-QUEUE-STATE-LIFECYCLE");

    OfflineI2IntegratedHarness.QueueProjection waitingReplayMarker =
        queueWith(
            waitingQueue,
            waitingQueue.canonicalInputDigest(),
            waitingQueue.state(),
            0,
            0,
            0,
            OfflineI2IntegratedHarness.ReplayMarker.SAME_INPUT_REPLAY);
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_STATE,
        () ->
            stateWith(
                waitingState,
                waitingState.localPhase(),
                waitingState.queue(),
                waitingReplayMarker),
        "T-FAIL-QUEUE-MARKER-LIFECYCLE");

    Prefix history = prefix(OfflineI2IntegratedHarness.ModelProfile.MODEL_ABSENT, 4);
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_STATE,
        () ->
            stateWith(
                history.state(),
                OfflineI2IntegratedHarness.LocalPhase.SEARCH_MATCHED,
                history.state().queue(),
                history.state().queueProjection()),
        "T-FAIL-PHASE-INDEX-BINDING");

    Prefix conflict = prefix(OfflineI2IntegratedHarness.ModelProfile.MODEL_ABSENT, 15);
    OfflineI2IntegratedHarness.QueueProjection conflictQueue = conflict.state().queueProjection();
    OfflineI2IntegratedHarness.QueueProjection earlyReplayCounter =
        queueWith(
            conflictQueue,
            conflictQueue.canonicalInputDigest(),
            conflictQueue.state(),
            2,
            1,
            0,
            OfflineI2IntegratedHarness.ReplayMarker.ORIGINAL);
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_STATE,
        () ->
            stateWith(
                conflict.state(),
                conflict.state().localPhase(),
                conflict.state().queue(),
                earlyReplayCounter),
        "T-FAIL-CONFLICT-COUNTER-LIFECYCLE");

    Prefix replay = prefix(OfflineI2IntegratedHarness.ModelProfile.MODEL_ABSENT, 16);
    OfflineI2IntegratedHarness.QueueProjection replayQueue = replay.state().queueProjection();
    OfflineI2IntegratedHarness.QueueProjection replayWrongMarker =
        queueWith(
            replayQueue,
            replayQueue.canonicalInputDigest(),
            replayQueue.state(),
            replayQueue.attemptCount(),
            replayQueue.effectCount(),
            replayQueue.applyCount(),
            OfflineI2IntegratedHarness.ReplayMarker.ORIGINAL);
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_STATE,
        () ->
            stateWith(
                replay.state(),
                replay.state().localPhase(),
                replay.state().queue(),
                replayWrongMarker),
        "T-FAIL-REPLAY-MARKER-LIFECYCLE");

    List<OfflineI2IntegratedHarness.TraceRow> wrongAction =
        replaceTraceRow(
            waiting.trace(),
            0,
            traceRowWith(
                waiting.trace().get(0),
                OfflineI2IntegratedHarness.Action.SAVE_LOCAL_SOURCE,
                waiting.trace().get(0).outcome(),
                waiting.trace().get(0).preStateDigest(),
                waiting.trace().get(0).postStateDigest(),
                waiting.trace().get(0).localAggregateDigest(),
                waiting.trace().get(0).queueIntentIdHash()));
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_SNAPSHOT,
        () -> OfflineI2IntegratedHarness.snapshot(waitingState, wrongAction),
        "T-FAIL-TRACE-ACTION-PREFIX");

    List<OfflineI2IntegratedHarness.TraceRow> wrongOutcome =
        replaceTraceRow(
            waiting.trace(),
            0,
            traceRowWith(
                waiting.trace().get(0),
                waiting.trace().get(0).action(),
                OfflineI2IntegratedHarness.Outcome.LOCAL_STATE_PRESERVED,
                waiting.trace().get(0).preStateDigest(),
                waiting.trace().get(0).postStateDigest(),
                waiting.trace().get(0).localAggregateDigest(),
                waiting.trace().get(0).queueIntentIdHash()));
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_SNAPSHOT,
        () -> OfflineI2IntegratedHarness.snapshot(waitingState, wrongOutcome),
        "T-FAIL-TRACE-OUTCOME-PREFIX");

    OfflineI2IntegratedHarness.TraceRow second = waiting.trace().get(1);
    OfflineI2IntegratedHarness.TraceRow third = waiting.trace().get(2);
    List<OfflineI2IntegratedHarness.TraceRow> withRewrittenSecond =
        replaceTraceRow(
            waiting.trace(),
            1,
            traceRowWith(
                second,
                second.action(),
                second.outcome(),
                second.preStateDigest(),
                ZERO_HASH,
                second.localAggregateDigest(),
                second.queueIntentIdHash()));
    List<OfflineI2IntegratedHarness.TraceRow> selfConsistentRechained =
        replaceTraceRow(
            withRewrittenSecond,
            2,
            traceRowWith(
                third,
                third.action(),
                third.outcome(),
                ZERO_HASH,
                third.postStateDigest(),
                third.localAggregateDigest(),
                third.queueIntentIdHash()));
    OfflineI2IntegratedHarness.Snapshot selfConsistentForgedTraceSnapshot =
        new OfflineI2IntegratedHarness.Snapshot(
            waitingState,
            OfflineI2IntegratedHarness.digestState(waitingState),
            OfflineI2IntegratedHarness.digestTrace(selfConsistentRechained));
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_SNAPSHOT,
        () ->
            OfflineI2IntegratedHarness.restore(
                selfConsistentForgedTraceSnapshot, selfConsistentRechained),
        "T-FAIL-TRACE-SELF-CONSISTENT-RECHAIN");

    int tailIndex = waiting.trace().size() - 1;
    OfflineI2IntegratedHarness.TraceRow tail = waiting.trace().get(tailIndex);
    List<OfflineI2IntegratedHarness.TraceRow> wrongTail =
        replaceTraceRow(
            waiting.trace(),
            tailIndex,
            traceRowWith(
                tail,
                tail.action(),
                tail.outcome(),
                tail.preStateDigest(),
                ZERO_HASH,
                tail.localAggregateDigest(),
                tail.queueIntentIdHash()));
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_SNAPSHOT,
        () -> OfflineI2IntegratedHarness.snapshot(waitingState, wrongTail),
        "T-FAIL-TRACE-TAIL-STATE");

    List<OfflineI2IntegratedHarness.TraceRow> wrongTailLocal =
        replaceTraceRow(
            waiting.trace(),
            tailIndex,
            traceRowWith(
                tail,
                tail.action(),
                tail.outcome(),
                tail.preStateDigest(),
                tail.postStateDigest(),
                ZERO_HASH,
                tail.queueIntentIdHash()));
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_SNAPSHOT,
        () -> OfflineI2IntegratedHarness.snapshot(waitingState, wrongTailLocal),
        "T-FAIL-TRACE-TAIL-LOCAL");

    int interiorIndex = 4;
    OfflineI2IntegratedHarness.TraceRow interior = waiting.trace().get(interiorIndex);
    List<OfflineI2IntegratedHarness.TraceRow> wrongInteriorLocal =
        replaceTraceRow(
            waiting.trace(),
            interiorIndex,
            traceRowWith(
                interior,
                interior.action(),
                interior.outcome(),
                interior.preStateDigest(),
                interior.postStateDigest(),
                ZERO_HASH,
                interior.queueIntentIdHash()));
    OfflineI2IntegratedHarness.Snapshot selfConsistentWrongInteriorLocalSnapshot =
        new OfflineI2IntegratedHarness.Snapshot(
            waitingState,
            OfflineI2IntegratedHarness.digestState(waitingState),
            OfflineI2IntegratedHarness.digestTrace(wrongInteriorLocal));
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_SNAPSHOT,
        () ->
            OfflineI2IntegratedHarness.restore(
                selfConsistentWrongInteriorLocalSnapshot, wrongInteriorLocal),
        "T-FAIL-TRACE-INTERIOR-LOCAL");

    List<OfflineI2IntegratedHarness.TraceRow> wrongTraceIntent =
        replaceTraceRow(
            waiting.trace(),
            tailIndex,
            traceRowWith(
                tail,
                tail.action(),
                tail.outcome(),
                tail.preStateDigest(),
                tail.postStateDigest(),
                tail.localAggregateDigest(),
                ZERO_HASH));
    expectFault(
        OfflineI2IntegratedHarness.Diagnostic.INVALID_SNAPSHOT,
        () -> OfflineI2IntegratedHarness.snapshot(waitingState, wrongTraceIntent),
        "T-FAIL-TRACE-QUEUE-IDENTITY");
  }

  private static Prefix prefix(
      OfflineI2IntegratedHarness.ModelProfile profile, int actionCount) {
    OfflineI2IntegratedHarness.IntegratedState state =
        OfflineI2IntegratedHarness.initialState(profile);
    List<OfflineI2IntegratedHarness.TraceRow> trace = new ArrayList<>();
    for (int index = 0; index < actionCount; index++) {
      OfflineI2IntegratedHarness.Action action = OfflineI2IntegratedHarness.actionOrder().get(index);
      String pre = OfflineI2IntegratedHarness.digestState(state);
      OfflineI2IntegratedHarness.Transition transition =
          OfflineI2IntegratedHarness.transition(state, action, trace);
      state = transition.state();
      trace.add(
          new OfflineI2IntegratedHarness.TraceRow(
              trace.size() + 1L,
              action,
              transition.outcome(),
              pre,
              OfflineI2IntegratedHarness.digestState(state),
              OfflineI2IntegratedHarness.digestLocal(state.local()),
              state.queueProjection() == null
                  ? null
                  : state.queueProjection().intentIdHash()));
    }
    return new Prefix(state, List.copyOf(trace));
  }

  private static OfflineI2IntegratedHarness.IntegratedState stateWith(
      OfflineI2IntegratedHarness.IntegratedState prior,
      OfflineI2IntegratedHarness.LocalPhase phase,
      OfflineI1Oracle.QueueState queue,
      OfflineI2IntegratedHarness.QueueProjection projection) {
    return new OfflineI2IntegratedHarness.IntegratedState(
        prior.profile(),
        prior.nextActionIndex(),
        phase,
        prior.processing(),
        prior.connectivity(),
        prior.model(),
        queue,
        prior.local(),
        projection);
  }

  private static OfflineI2IntegratedHarness.QueueProjection queueWith(
      OfflineI2IntegratedHarness.QueueProjection prior,
      String canonicalInputDigest,
      OfflineI1Oracle.QueueState state,
      long attemptCount,
      int effectCount,
      int applyCount,
      OfflineI2IntegratedHarness.ReplayMarker marker) {
    return new OfflineI2IntegratedHarness.QueueProjection(
        prior.intentIdHash(),
        canonicalInputDigest,
        state,
        attemptCount,
        effectCount,
        applyCount,
        marker);
  }

  private static OfflineI2IntegratedHarness.TraceRow traceRowWith(
      OfflineI2IntegratedHarness.TraceRow prior,
      OfflineI2IntegratedHarness.Action action,
      OfflineI2IntegratedHarness.Outcome outcome,
      String preStateDigest,
      String postStateDigest,
      String localAggregateDigest,
      String queueIntentIdHash) {
    return new OfflineI2IntegratedHarness.TraceRow(
        prior.sequence(),
        action,
        outcome,
        preStateDigest,
        postStateDigest,
        localAggregateDigest,
        queueIntentIdHash);
  }

  private static List<OfflineI2IntegratedHarness.TraceRow> replaceTraceRow(
      List<OfflineI2IntegratedHarness.TraceRow> trace,
      int index,
      OfflineI2IntegratedHarness.TraceRow replacement) {
    List<OfflineI2IntegratedHarness.TraceRow> changed = new ArrayList<>(trace);
    changed.set(index, replacement);
    return List.copyOf(changed);
  }

  private static void testImmutabilityAndContentFreeShape() {
    OfflineI2IntegratedHarness.RunResult result =
        OfflineI2IntegratedHarness.run(
            OfflineI2IntegratedHarness.ModelProfile.MODEL_ABSENT);
    expectUnsupported(
        () -> result.trace().add(result.trace().get(0)), "T-IMMUTABLE-RESULT-TRACE");
    expectUnsupported(
        () ->
            OfflineI2IntegratedHarness.actionOrder()
                .add(OfflineI2IntegratedHarness.Action.CAPTURE_SYNTHETIC_SOURCE),
        "T-IMMUTABLE-ACTION-ORDER");
    for (OfflineI2IntegratedHarness.TraceRow row : result.trace()) {
      check(row.preStateDigest().matches("[0-9a-f]{64}"), "T-CONTENT-FREE-PRE-" + row.sequence());
      check(row.postStateDigest().matches("[0-9a-f]{64}"), "T-CONTENT-FREE-POST-" + row.sequence());
      check(row.localAggregateDigest().matches("[0-9a-f]{64}"), "T-CONTENT-FREE-LOCAL-" + row.sequence());
      check(
          row.queueIntentIdHash() == null || row.queueIntentIdHash().matches("[0-9a-f]{64}"),
          "T-CONTENT-FREE-QUEUE-" + row.sequence());
    }
    check(result.summaryDigest().matches("[0-9a-f]{64}"), "T-CONTENT-FREE-SUMMARY");
    check(result.traceDigest().matches("[0-9a-f]{64}"), "T-CONTENT-FREE-TRACE");
    equal(
        OfflineI2IntegratedHarness.PROOF_CLASS,
        result.proofClass(),
        "T-CONTENT-FREE-PROOF-BOUNDARY");
  }

  private static void expectFault(
      OfflineI2IntegratedHarness.Diagnostic expected, Checked action, String id) {
    try {
      action.run();
      throw new AssertionError(id);
    } catch (OfflineI2IntegratedHarness.HarnessFault fault) {
      check(fault.diagnostic() == expected, id + "-DIAGNOSTIC");
    }
  }

  private static void expectUnsupported(Checked action, String id) {
    try {
      action.run();
      throw new AssertionError(id);
    } catch (UnsupportedOperationException expected) {
      assertions++;
    }
  }

  private static void equal(Object expected, Object actual, String id) {
    check(expected == null ? actual == null : expected.equals(actual), id);
  }

  private static void check(boolean condition, String id) {
    assertions++;
    if (!condition) {
      throw new AssertionError(id);
    }
  }

  @FunctionalInterface
  private interface Checked {
    void run();
  }

  private record Prefix(
      OfflineI2IntegratedHarness.IntegratedState state,
      List<OfflineI2IntegratedHarness.TraceRow> trace) {}
}
