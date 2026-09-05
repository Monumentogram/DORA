"""Behavior tests for the bounded REC-I3 host external-controller core."""

from __future__ import annotations

from dataclasses import replace
from threading import Event, Thread
import unittest

import tools.rec_i3_external_controller as controller_module
from tools.rec_i3_external_controller import (
    ACTIVE_GATE_SET_ID,
    ACTIVE_PROTOCOL_ID,
    SIGNAL_NAME,
    AcceptedWatermarkOracle,
    AttemptEvidence,
    AttemptIdentity,
    AttemptStage,
    BarrierObservation,
    Candidate,
    CandidateOutcome,
    CommitObservation,
    DeathObservation,
    ExternalController,
    Invalidator,
    ObservedAttemptIdentity,
    ProtocolInputError,
    QuantityViolation,
    ReceiptRejected,
    RecoveryQuantities,
    SignalAlreadyAttempted,
    SignalNotEligible,
    SignalOutcomeUnknown,
    SignalReceipt,
    Stratum,
    assess_evidence,
    assess_quantities,
    derive_invalidators,
)


class FakeSignalPort:
    def __init__(self, receipt: SignalReceipt | None = None, error: Exception | None = None):
        self.receipt = receipt
        self.error = error
        self.calls: list[tuple[str, int]] = []

    def issue_sigkill(self, attempt_id: str, target_pid: int) -> SignalReceipt:
        self.calls.append((attempt_id, target_pid))
        if self.error is not None:
            raise self.error
        if self.receipt is None:
            return SignalReceipt(attempt_id, target_pid, SIGNAL_NAME, True)
        return self.receipt


class FakeLivenessPort:
    def __init__(self, *responses: object):
        self.responses = list(responses)
        self.calls: list[tuple[str, int]] = []

    def is_alive(self, attempt_id: str, target_pid: int) -> object:
        self.calls.append((attempt_id, target_pid))
        if not self.responses:
            raise AssertionError("unexpected liveness probe")
        return self.responses.pop(0)


class BlockingSignalPort(FakeSignalPort):
    def __init__(self):
        super().__init__()
        self.entered = Event()
        self.release = Event()

    def issue_sigkill(self, attempt_id: str, target_pid: int) -> SignalReceipt:
        self.calls.append((attempt_id, target_pid))
        self.entered.set()
        if not self.release.wait(timeout=5):
            raise AssertionError("test did not release the signal port")
        return SignalReceipt(attempt_id, target_pid, SIGNAL_NAME, True)


def expected_identity(
    *,
    candidate: Candidate = Candidate.MICROFILE,
    stratum: Stratum = Stratum.K04,
    slot: int = 1,
    target_pid: int = 4242,
    build_identity: str = "build-main",
) -> AttemptIdentity:
    phase = "phase-a"
    environment = "E36-GAPI"
    attempt_id = f"{phase}-{candidate.value}-{stratum.value}-{environment}-slot-{slot:02d}"
    return AttemptIdentity(
        attempt_id=attempt_id,
        phase=phase,
        candidate=candidate,
        stratum=stratum,
        environment=environment,
        slot=slot,
        protocol_id=ACTIVE_PROTOCOL_ID,
        gate_set_id=ACTIVE_GATE_SET_ID,
        build_identity=build_identity,
        artifact_identity="apk-sha",
        preflight_identity="preflight-sha",
        fixture_identity="fixture-sha",
        target_pid=target_pid,
    )


def observed_identity(expected: AttemptIdentity, **changes: object) -> ObservedAttemptIdentity:
    values: dict[str, object] = {
        "attempt_id": expected.attempt_id,
        "phase": expected.phase,
        "candidate_id": expected.candidate.value,
        "stratum_id": expected.stratum.value,
        "environment": expected.environment,
        "slot": expected.slot,
        "protocol_id": expected.protocol_id,
        "gate_set_id": expected.gate_set_id,
        "build_identity": expected.build_identity,
        "artifact_identity": expected.artifact_identity,
        "preflight_identity": expected.preflight_identity,
        "fixture_identity": expected.fixture_identity,
        "target_pid": expected.target_pid,
    }
    values.update(changes)
    return ObservedAttemptIdentity(**values)  # type: ignore[arg-type]


def barrier_for(expected: AttemptIdentity, **changes: object) -> BarrierObservation:
    values: dict[str, object] = {
        "candidate_id": expected.candidate.value,
        "stratum_id": expected.stratum.value,
        "target_pid": expected.target_pid,
        "public_api_or_harness_callback": True,
    }
    values.update(changes)
    return BarrierObservation(**values)  # type: ignore[arg-type]


def complete_evidence(
    expected: AttemptIdentity,
    *,
    observed: ObservedAttemptIdentity | None = None,
    quantities: RecoveryQuantities | None = None,
    outcomes: set[CandidateOutcome] | frozenset[CandidateOutcome] = frozenset(),
    invalidators: set[Invalidator] | frozenset[Invalidator] = frozenset(),
    envelope_complete: bool = True,
    graceful_finalize: bool = False,
) -> AttemptEvidence:
    return AttemptEvidence(
        expected=expected,
        observed=observed or observed_identity(expected),
        scheduled_before_execution=True,
        barrier=barrier_for(expected),
        controller_connected_until_signal_confirmation=True,
        reboot_or_power_loss_outside_assigned_fault=False,
        operator_intervention=False,
        signal_receipt=SignalReceipt(expected.attempt_id, expected.target_pid, SIGNAL_NAME, True),
        death=DeathObservation(expected.attempt_id, expected.target_pid, True),
        graceful_finalize_after_trigger=graceful_finalize,
        envelope_complete_and_schema_valid=envelope_complete,
        explicit_invalidators=invalidators,
        observed_candidate_outcomes=outcomes,
        commit=None,
        quantities=quantities,
    )


def issue_valid(controller: ExternalController, expected: AttemptIdentity) -> SignalReceipt:
    return controller.issue_after_validated_barrier(
        expected,
        observed_identity(expected),
        scheduled_before_execution=True,
        barrier=barrier_for(expected),
        controller_connected=True,
        reboot_or_power_loss_outside_assigned_fault=False,
        operator_intervention=False,
    )


class IdentityTest(unittest.TestCase):
    def test_authorized_identity_accepts_exact_active_base_attempts(self) -> None:
        for candidate in Candidate:
            for stratum in Stratum:
                for slot in (1, 10):
                    identity = expected_identity(candidate=candidate, stratum=stratum, slot=slot)
                    self.assertEqual(slot, identity.slot)
                    self.assertTrue(identity.attempt_id.endswith(f"slot-{slot:02d}"))

    def test_authorized_identity_rejects_nonactive_protocol_gate_and_r1(self) -> None:
        valid = expected_identity()
        cases = (
            {"protocol_id": "poc-recovery-protocol-stage0-v0.5"},
            {"gate_set_id": "poc-recovery-stage0-v0.5"},
            {"attempt_id": valid.attempt_id + "-R1"},
            {"slot": True},
            {"target_pid": True},
        )
        for changes in cases:
            with self.subTest(changes=changes), self.assertRaises(ProtocolInputError):
                replace(valid, **changes)

    def test_observed_identity_preserves_protocol_gate_candidate_and_stratum_drift(self) -> None:
        expected = expected_identity()
        observed = observed_identity(
            expected,
            protocol_id="observed-protocol",
            gate_set_id="observed-gate",
            candidate_id="observed-candidate",
            stratum_id="K99",
        )
        self.assertEqual("observed-protocol", observed.protocol_id)
        self.assertEqual("observed-gate", observed.gate_set_id)
        self.assertEqual("observed-candidate", observed.candidate_id)
        self.assertEqual("K99", observed.stratum_id)

    def test_observed_protocol_gate_and_build_drift_derives_identity_invalidator(self) -> None:
        expected = expected_identity()
        for change in (
            {"protocol_id": "v0.5"},
            {"gate_set_id": "old-gate"},
            {"build_identity": "other-build"},
            {"candidate_id": Candidate.STREAM.value},
            {"stratum_id": Stratum.K05.value},
        ):
            evidence = complete_evidence(expected, observed=observed_identity(expected, **change))
            self.assertIn(Invalidator.PREFLIGHT_OR_PROTOCOL_IDENTITY_MISMATCH, derive_invalidators(evidence))

    def test_observed_fixture_drift_derives_fixture_only(self) -> None:
        expected = expected_identity()
        evidence = complete_evidence(
            expected, observed=observed_identity(expected, fixture_identity="different-fixture")
        )
        self.assertEqual(frozenset({Invalidator.FIXTURE_DRIFT}), derive_invalidators(evidence))

    def test_observed_pid_drift_derives_wrong_pid(self) -> None:
        expected = expected_identity()
        evidence = complete_evidence(expected, observed=observed_identity(expected, target_pid=9999))
        self.assertIn(Invalidator.WRONG_PID, derive_invalidators(evidence))

    def test_observed_and_evidence_dtos_reject_boolean_integer_fields(self) -> None:
        expected = expected_identity()
        with self.assertRaises(ProtocolInputError):
            observed_identity(expected, slot=True)
        with self.assertRaises(ProtocolInputError):
            observed_identity(expected, target_pid=True)
        with self.assertRaises(ProtocolInputError):
            BarrierObservation(expected.candidate.value, expected.stratum.value, True, True)
        with self.assertRaises(ProtocolInputError):
            SignalReceipt(expected.attempt_id, True, SIGNAL_NAME, True)


class WatermarkAndQuantityTest(unittest.TestCase):
    def test_watermark_advances_only_after_completed_call(self) -> None:
        oracle = AcceptedWatermarkOracle()
        token = oracle.begin_bounded_write(0, 160000)
        self.assertEqual(0, oracle.accepted_watermark)
        self.assertEqual(160000, oracle.complete_bounded_write(token))
        self.assertEqual(160000, oracle.accepted_watermark)

    def test_in_progress_call_is_excluded_from_a(self) -> None:
        oracle = AcceptedWatermarkOracle()
        first = oracle.begin_bounded_write(0, 40000)
        oracle.complete_bounded_write(first)
        oracle.begin_bounded_write(40000, 80000)
        self.assertEqual(40000, oracle.accepted_watermark)

    def test_watermark_rejects_gap_overlap_duplicate_and_bound_overrun(self) -> None:
        oracle = AcceptedWatermarkOracle()
        token = oracle.begin_bounded_write(0, 10)
        oracle.complete_bounded_write(token)
        invalid_operations = (
            lambda: oracle.begin_bounded_write(11, 20),
            lambda: oracle.begin_bounded_write(9, 20),
            lambda: oracle.complete_bounded_write(token),
            lambda: oracle.begin_bounded_write(10, 115200001),
            lambda: oracle.begin_bounded_write(10, 10),
            lambda: oracle.begin_bounded_write(True, 20),
        )
        for operation in invalid_operations:
            with self.subTest(operation=operation), self.assertRaises(ProtocolInputError):
                operation()
            self.assertEqual(10, oracle.accepted_watermark)

    def test_k12_quantities_keep_c_r_a_distinct(self) -> None:
        stream = RecoveryQuantities(12216, 8136, 8136)
        self.assertEqual((0, 4080, 0.1275), (
            stream.committed_loss_bytes,
            stream.tail_loss_bytes,
            stream.tail_loss_seconds,
        ))
        microfile = RecoveryQuantities(360000, 320000, 320000)
        self.assertEqual((0, 40000, 1.25), (
            microfile.committed_loss_bytes,
            microfile.tail_loss_bytes,
            microfile.tail_loss_seconds,
        ))

    def test_negative_boolean_or_individually_unbounded_quantities_are_rejected(self) -> None:
        for values in ((-1, 0, 0), (0, -1, 0), (0, 0, -1), (True, 0, 0), (115200001, 0, 0)):
            with self.subTest(values=values), self.assertRaises(ProtocolInputError):
                RecoveryQuantities(*values)

    def test_c_greater_than_r_is_preserved_and_computes_committed_loss(self) -> None:
        quantities = RecoveryQuantities(100, 80, 60)
        self.assertEqual((100, 80, 60), (
            quantities.accepted_end,
            quantities.committed_end,
            quantities.recovered_end,
        ))
        self.assertEqual(20, quantities.committed_loss_bytes)
        self.assertEqual((QuantityViolation.COMMITTED_EXCEEDS_RECOVERED,), assess_quantities(quantities).violations)

    def test_r_greater_than_a_is_preserved_as_quantity_violation(self) -> None:
        quantities = RecoveryQuantities(100, 80, 120)
        self.assertEqual((100, 80, 120), (
            quantities.accepted_end,
            quantities.committed_end,
            quantities.recovered_end,
        ))
        self.assertEqual((QuantityViolation.RECOVERED_EXCEEDS_ACCEPTED,), assess_quantities(quantities).violations)

    def test_committed_loss_is_candidate_outcome_not_external_invalidator(self) -> None:
        assessment = assess_evidence(
            complete_evidence(expected_identity(), quantities=RecoveryQuantities(100, 80, 60))
        )
        self.assertTrue(assessment.externally_valid)
        self.assertEqual(frozenset(), assessment.invalidators)
        self.assertIn(CandidateOutcome.COMMITTED_BYTE_LOSS, assessment.candidate_outcomes)

    def test_commit_event_requires_transaction_return(self) -> None:
        with self.assertRaises(ProtocolInputError):
            CommitObservation(False, True)

    def test_k10_transaction_without_event_does_not_create_c_or_r(self) -> None:
        observation = CommitObservation(True, False)
        evidence = replace(complete_evidence(expected_identity()), commit=observation)
        self.assertIsNone(evidence.quantities)
        self.assertTrue(assess_evidence(evidence).externally_valid)


class ControllerTest(unittest.TestCase):
    def test_exact_public_barrier_signals_live_pid_once(self) -> None:
        expected = expected_identity()
        signals = FakeSignalPort()
        controller = ExternalController(signals, FakeLivenessPort(True))
        receipt = issue_valid(controller, expected)
        self.assertEqual(SignalReceipt(expected.attempt_id, 4242, SIGNAL_NAME, True), receipt)
        self.assertEqual([(expected.attempt_id, 4242)], signals.calls)
        self.assertEqual(AttemptStage.SIGNAL_CONFIRMED, controller.stage_for(expected.attempt_id))

    def test_second_sequential_call_never_reissues_signal(self) -> None:
        expected = expected_identity()
        signals = FakeSignalPort()
        controller = ExternalController(signals, FakeLivenessPort(True))
        issue_valid(controller, expected)
        with self.assertRaises(SignalAlreadyAttempted):
            issue_valid(controller, expected)
        self.assertEqual(1, len(signals.calls))

    def test_same_attempt_id_with_changed_pid_or_build_cannot_retarget_signal(self) -> None:
        expected = expected_identity()
        signals = FakeSignalPort()
        controller = ExternalController(signals, FakeLivenessPort(True))
        issue_valid(controller, expected)
        for changed in (
            expected_identity(target_pid=7777),
            expected_identity(build_identity="retargeted-build"),
        ):
            with self.subTest(changed=changed), self.assertRaises(SignalAlreadyAttempted):
                issue_valid(controller, changed)
        self.assertEqual([(expected.attempt_id, expected.target_pid)], signals.calls)

    def test_concurrent_calls_issue_at_most_one_signal(self) -> None:
        expected = expected_identity()
        signals = BlockingSignalPort()
        controller = ExternalController(signals, FakeLivenessPort(True, True))
        results: list[object] = []

        def first_call() -> None:
            try:
                results.append(issue_valid(controller, expected))
            except Exception as error:  # pragma: no cover - asserted through results
                results.append(error)

        worker = Thread(target=first_call)
        worker.start()
        self.assertTrue(signals.entered.wait(timeout=5))
        with self.assertRaises(SignalAlreadyAttempted):
            issue_valid(controller, expected)
        signals.release.set()
        worker.join(timeout=5)
        self.assertFalse(worker.is_alive())
        self.assertEqual(1, len(signals.calls))
        self.assertIsInstance(results[0], SignalReceipt)

    def test_reentrant_signal_port_cannot_reissue(self) -> None:
        expected = expected_identity()
        nested_errors: list[Exception] = []

        class ReentrantPort(FakeSignalPort):
            controller: ExternalController

            def issue_sigkill(self, attempt_id: str, target_pid: int) -> SignalReceipt:
                self.calls.append((attempt_id, target_pid))
                try:
                    issue_valid(self.controller, expected)
                except Exception as error:
                    nested_errors.append(error)
                return SignalReceipt(attempt_id, target_pid, SIGNAL_NAME, True)

        signals = ReentrantPort()
        controller = ExternalController(signals, FakeLivenessPort(True, True))
        signals.controller = controller
        issue_valid(controller, expected)
        self.assertEqual(1, len(signals.calls))
        self.assertEqual(1, len(nested_errors))
        self.assertIsInstance(nested_errors[0], SignalAlreadyAttempted)

    def test_port_exception_is_terminal_unknown_and_replay_does_not_reissue(self) -> None:
        expected = expected_identity()
        signals = FakeSignalPort(error=RuntimeError("unknown delivery"))
        controller = ExternalController(signals, FakeLivenessPort(True))
        with self.assertRaises(SignalOutcomeUnknown):
            issue_valid(controller, expected)
        self.assertEqual(AttemptStage.SIGNAL_OUTCOME_UNKNOWN, controller.stage_for(expected.attempt_id))
        with self.assertRaises(SignalAlreadyAttempted):
            issue_valid(controller, expected)
        self.assertEqual(1, len(signals.calls))

    def test_unconfirmed_or_malformed_receipt_is_terminal_unknown(self) -> None:
        expected = expected_identity()
        receipts = (
            SignalReceipt(expected.attempt_id, expected.target_pid, SIGNAL_NAME, False),
            SignalReceipt(expected.attempt_id, expected.target_pid, "SIGTERM", True),
            SignalReceipt(expected.attempt_id, 9999, SIGNAL_NAME, True),
            SignalReceipt("other-attempt", expected.target_pid, SIGNAL_NAME, True),
        )
        for receipt in receipts:
            signals = FakeSignalPort(receipt)
            controller = ExternalController(signals, FakeLivenessPort(True))
            with self.subTest(receipt=receipt), self.assertRaises(SignalOutcomeUnknown):
                issue_valid(controller, expected)
            with self.assertRaises(SignalAlreadyAttempted):
                issue_valid(controller, expected)
            self.assertEqual(1, len(signals.calls))

    def test_wrong_missing_or_private_barrier_never_signals(self) -> None:
        expected = expected_identity()
        cases = (
            (None, {Invalidator.STRATUM_BARRIER_NOT_REACHED}),
            (barrier_for(expected, stratum_id="K99"), {Invalidator.STRATUM_BARRIER_NOT_REACHED}),
            (barrier_for(expected, target_pid=9999), {Invalidator.WRONG_PID, Invalidator.STRATUM_BARRIER_NOT_REACHED}),
            (barrier_for(expected, public_api_or_harness_callback=False), {Invalidator.STRATUM_BARRIER_NOT_REACHED}),
        )
        for barrier, invalidators in cases:
            signals = FakeSignalPort()
            controller = ExternalController(signals, FakeLivenessPort(True))
            with self.subTest(barrier=barrier), self.assertRaises(SignalNotEligible) as raised:
                controller.issue_after_validated_barrier(
                    expected,
                    observed_identity(expected),
                    scheduled_before_execution=True,
                    barrier=barrier,
                    controller_connected=True,
                    reboot_or_power_loss_outside_assigned_fault=False,
                    operator_intervention=False,
                )
            self.assertEqual(frozenset(invalidators), raised.exception.invalidators)
            self.assertEqual([], signals.calls)

    def test_unscheduled_attempt_never_calls_ports(self) -> None:
        expected = expected_identity()
        signals = FakeSignalPort()
        liveness = FakeLivenessPort(True)
        controller = ExternalController(signals, liveness)
        with self.assertRaises(SignalNotEligible) as raised:
            controller.issue_after_validated_barrier(
                expected,
                observed_identity(expected),
                scheduled_before_execution=False,
                barrier=barrier_for(expected),
                controller_connected=True,
                reboot_or_power_loss_outside_assigned_fault=False,
                operator_intervention=False,
            )
        self.assertEqual(frozenset(), raised.exception.invalidators)
        self.assertEqual([], signals.calls)
        self.assertEqual([], liveness.calls)

    def test_disconnect_reboot_and_operator_intervention_block_signal(self) -> None:
        expected = expected_identity()
        cases = (
            ({"controller_connected": False}, Invalidator.CONTROLLER_DISCONNECTED_BEFORE_SIGNAL_CONFIRMATION),
            ({"reboot_or_power_loss_outside_assigned_fault": True}, Invalidator.DEVICE_REBOOT_OR_POWER_LOSS_OUTSIDE_ASSIGNED_FAULT),
            ({"operator_intervention": True}, Invalidator.OPERATOR_INTERVENTION),
        )
        for changes, invalidator in cases:
            signals = FakeSignalPort()
            controller = ExternalController(signals, FakeLivenessPort(True))
            arguments = {
                "scheduled_before_execution": True,
                "barrier": barrier_for(expected),
                "controller_connected": True,
                "reboot_or_power_loss_outside_assigned_fault": False,
                "operator_intervention": False,
            }
            arguments.update(changes)
            with self.subTest(changes=changes), self.assertRaises(SignalNotEligible) as raised:
                controller.issue_after_validated_barrier(expected, observed_identity(expected), **arguments)
            self.assertEqual(frozenset({invalidator}), raised.exception.invalidators)
            self.assertEqual([], signals.calls)

    def test_dead_pid_before_signal_is_not_signaled(self) -> None:
        expected = expected_identity()
        signals = FakeSignalPort()
        controller = ExternalController(signals, FakeLivenessPort(False))
        with self.assertRaises(SignalNotEligible) as raised:
            issue_valid(controller, expected)
        self.assertEqual(frozenset(), raised.exception.invalidators)
        self.assertEqual([], signals.calls)

    def test_pre_signal_liveness_requires_exact_boolean(self) -> None:
        expected = expected_identity()
        for malformed in (None, 1):
            signals = FakeSignalPort()
            controller = ExternalController(signals, FakeLivenessPort(malformed))
            with self.subTest(malformed=malformed), self.assertRaises(ProtocolInputError):
                issue_valid(controller, expected)
            self.assertEqual([], signals.calls)
            self.assertEqual(AttemptStage.READY, controller.stage_for(expected.attempt_id))

    def test_signal_confirmation_does_not_confirm_death(self) -> None:
        expected = expected_identity()
        controller = ExternalController(FakeSignalPort(), FakeLivenessPort(True))
        issue_valid(controller, expected)
        self.assertEqual(AttemptStage.SIGNAL_CONFIRMED, controller.stage_for(expected.attempt_id))

    def test_death_probe_rejects_copied_cross_attempt_cross_controller_and_retargeted_receipts(self) -> None:
        expected = expected_identity()
        liveness = FakeLivenessPort(True)
        controller = ExternalController(FakeSignalPort(), liveness)
        receipt = issue_valid(controller, expected)
        liveness_calls_before = list(liveness.calls)
        other = expected_identity(stratum=Stratum.K05)
        other_controller = ExternalController(FakeSignalPort(), FakeLivenessPort(True))
        cases = (
            (expected, replace(receipt)),
            (other, receipt),
            (expected_identity(target_pid=7777), receipt),
            (expected_identity(build_identity="retargeted-build"), receipt),
        )
        for identity, supplied_receipt in cases:
            with self.subTest(identity=identity), self.assertRaises(ReceiptRejected):
                controller.confirm_independent_death(identity, supplied_receipt)
        with self.assertRaises(ReceiptRejected):
            other_controller.confirm_independent_death(expected, receipt)
        self.assertEqual(liveness_calls_before, liveness.calls)

    def test_death_requires_separate_false_liveness_result(self) -> None:
        expected = expected_identity()
        liveness = FakeLivenessPort(True, True, False)
        controller = ExternalController(FakeSignalPort(), liveness)
        receipt = issue_valid(controller, expected)
        alive = controller.confirm_independent_death(expected, receipt)
        self.assertFalse(alive.independently_confirmed_dead)
        self.assertEqual(AttemptStage.SIGNAL_CONFIRMED, controller.stage_for(expected.attempt_id))
        dead = controller.confirm_independent_death(expected, receipt)
        self.assertTrue(dead.independently_confirmed_dead)
        self.assertEqual(AttemptStage.DEATH_CONFIRMED, controller.stage_for(expected.attempt_id))

    def test_death_liveness_requires_exact_boolean_and_releases_probe(self) -> None:
        expected = expected_identity()
        for malformed in (None, 1):
            liveness = FakeLivenessPort(True, malformed, False)
            controller = ExternalController(FakeSignalPort(), liveness)
            receipt = issue_valid(controller, expected)
            with self.subTest(malformed=malformed), self.assertRaises(ProtocolInputError):
                controller.confirm_independent_death(expected, receipt)
            self.assertEqual(AttemptStage.SIGNAL_CONFIRMED, controller.stage_for(expected.attempt_id))
            death = controller.confirm_independent_death(expected, receipt)
            self.assertTrue(death.independently_confirmed_dead)

    def test_death_probe_remains_in_flight_through_atomic_stage_transition(self) -> None:
        expected = expected_identity()
        liveness = FakeLivenessPort(True, False, True)
        controller = ExternalController(FakeSignalPort(), liveness)
        receipt = issue_valid(controller, expected)
        original_death_observation = controller_module.DeathObservation
        construction_entered = Event()
        release_construction = Event()
        construction_calls: list[int] = []
        first_results: list[object] = []
        second_results: list[object] = []

        def gated_death_observation(
            attempt_id: str,
            target_pid: int,
            independently_confirmed_dead: bool,
        ) -> DeathObservation:
            construction_calls.append(1)
            if len(construction_calls) == 1:
                construction_entered.set()
                if not release_construction.wait(timeout=5):
                    raise AssertionError("test did not release death observation construction")
            return original_death_observation(
                attempt_id,
                target_pid,
                independently_confirmed_dead,
            )

        def probe(results: list[object]) -> None:
            try:
                results.append(controller.confirm_independent_death(expected, receipt))
            except Exception as error:
                results.append(error)

        controller_module.DeathObservation = gated_death_observation
        first = Thread(target=probe, args=(first_results,))
        second = Thread(target=probe, args=(second_results,))
        try:
            first.start()
            self.assertTrue(construction_entered.wait(timeout=5))
            second.start()
            second.join(timeout=5)
            self.assertFalse(second.is_alive())
        finally:
            release_construction.set()
            first.join(timeout=5)
            second.join(timeout=5)
            controller_module.DeathObservation = original_death_observation

        self.assertFalse(first.is_alive())
        self.assertIsInstance(first_results[0], DeathObservation)
        self.assertTrue(first_results[0].independently_confirmed_dead)
        self.assertEqual(1, len(second_results))
        self.assertIsInstance(second_results[0], ReceiptRejected)
        self.assertEqual(
            [(expected.attempt_id, expected.target_pid)] * 2,
            liveness.calls,
        )
        self.assertEqual(AttemptStage.DEATH_CONFIRMED, controller.stage_for(expected.attempt_id))


class EvidenceAssessmentTest(unittest.TestCase):
    def test_exact_eight_invalidators_and_never_invalid_outcomes_are_stable(self) -> None:
        self.assertEqual(
            {
                "WRONG_PID",
                "STRATUM_BARRIER_NOT_REACHED",
                "CONTROLLER_DISCONNECTED_BEFORE_SIGNAL_CONFIRMATION",
                "DEVICE_REBOOT_OR_POWER_LOSS_OUTSIDE_ASSIGNED_FAULT",
                "PREFLIGHT_OR_PROTOCOL_IDENTITY_MISMATCH",
                "FIXTURE_DRIFT",
                "OPERATOR_INTERVENTION",
                "EXTERNAL_CONTROLLER_OR_PREFLIGHT_EVIDENCE_ENVELOPE_INCOMPLETE_OR_SCHEMA_INVALID",
            },
            {item.value for item in Invalidator},
        )
        self.assertEqual(
            {
                "CANDIDATE_CRASH",
                "AUTHENTICATION_FAILURE",
                "COMMITTED_BYTE_LOSS",
                "TAIL_LOSS_GATE_FAILURE",
                "UNRECOVERABLE_SPLIT_BRAIN",
                "DUPLICATE_OR_MISSING_PROCESSING_INTENT",
                "CANDIDATE_TIMEOUT_OR_DEADLOCK",
                "CANDIDATE_RECOVERY_OUTPUT_MISSING_OR_SCHEMA_INVALID_AFTER_CONFIRMED_KILL",
            },
            {item.value for item in CandidateOutcome},
        )

    def test_candidate_failures_do_not_invalidate_external_kill(self) -> None:
        expected = expected_identity()
        for outcome in CandidateOutcome:
            assessment = assess_evidence(complete_evidence(expected, outcomes={outcome}))
            self.assertTrue(assessment.externally_valid)
            self.assertEqual(frozenset(), assessment.invalidators)
            self.assertIn(outcome, assessment.candidate_outcomes)

    def test_incomplete_envelope_and_graceful_finalize_fail_external_validity(self) -> None:
        expected = expected_identity()
        incomplete = assess_evidence(complete_evidence(expected, envelope_complete=False))
        self.assertFalse(incomplete.externally_valid)
        self.assertEqual(
            frozenset({Invalidator.EXTERNAL_CONTROLLER_OR_PREFLIGHT_EVIDENCE_ENVELOPE_INCOMPLETE_OR_SCHEMA_INVALID}),
            incomplete.invalidators,
        )
        graceful = assess_evidence(complete_evidence(expected, graceful_finalize=True))
        self.assertFalse(graceful.externally_valid)
        self.assertEqual(frozenset(), graceful.invalidators)

    def test_dto_collections_are_defensively_frozen(self) -> None:
        expected = expected_identity()
        outcomes = {CandidateOutcome.CANDIDATE_CRASH}
        invalidators = {Invalidator.OPERATOR_INTERVENTION}
        evidence = complete_evidence(expected, outcomes=outcomes, invalidators=invalidators)
        outcomes.clear()
        invalidators.clear()
        self.assertEqual(frozenset({CandidateOutcome.CANDIDATE_CRASH}), evidence.observed_candidate_outcomes)
        self.assertEqual(frozenset({Invalidator.OPERATOR_INTERVENTION}), evidence.explicit_invalidators)

    def test_repeated_pure_assessment_is_deterministic(self) -> None:
        evidence = complete_evidence(
            expected_identity(),
            quantities=RecoveryQuantities(100, 80, 60),
            outcomes={CandidateOutcome.AUTHENTICATION_FAILURE},
        )
        self.assertEqual(assess_evidence(evidence), assess_evidence(evidence))


if __name__ == "__main__":
    unittest.main()
