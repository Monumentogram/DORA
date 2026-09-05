"""Host-only REC-I3 external-controller contract core.

This module contains no operating-system, Android, ADB, recovery, crypto, filesystem, network,
campaign, or evidence-serialization adapter. Injected ports are exercised only by synthetic tests
in the current scope.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from threading import Lock
from typing import Protocol


ACTIVE_PROTOCOL_ID = "poc-recovery-protocol-stage0-v0.6"
ACTIVE_GATE_SET_ID = "poc-recovery-stage0-v0.6"
SIGNAL_NAME = "SIGKILL"
BYTES_PER_SECOND = 32_000
TAIL_GATE_BYTES = 160_000
MAX_PLAINTEXT_BYTES_PER_RUN = 115_200_000


class ProtocolInputError(ValueError):
    """The supplied host-side protocol value is malformed."""


class SignalNotEligible(RuntimeError):
    """The supplied facts do not permit one injected signal-port call."""

    def __init__(self, message: str, invalidators: frozenset[Invalidator] = frozenset()):
        super().__init__(message)
        self.invalidators = frozenset(invalidators)


class SignalAlreadyAttempted(RuntimeError):
    """The controller has already crossed the side-effect boundary for this base attempt."""


class SignalOutcomeUnknown(RuntimeError):
    """The signal port was called but did not return an exact confirmed receipt."""


class ReceiptRejected(RuntimeError):
    """A death probe was not bound to this controller's stored exact receipt and identity."""


class Candidate(str, Enum):
    STREAM = "REC-STREAM-TINK"
    MICROFILE = "REC-MICROFILE-TINK"


class Stratum(str, Enum):
    K01 = "K01"
    K02 = "K02"
    K03 = "K03"
    K04 = "K04"
    K05 = "K05"
    K06 = "K06"
    K07 = "K07"
    K08 = "K08"
    K09 = "K09"
    K10 = "K10"
    K11 = "K11"
    K12 = "K12"


class Invalidator(str, Enum):
    WRONG_PID = "WRONG_PID"
    STRATUM_BARRIER_NOT_REACHED = "STRATUM_BARRIER_NOT_REACHED"
    CONTROLLER_DISCONNECTED_BEFORE_SIGNAL_CONFIRMATION = (
        "CONTROLLER_DISCONNECTED_BEFORE_SIGNAL_CONFIRMATION"
    )
    DEVICE_REBOOT_OR_POWER_LOSS_OUTSIDE_ASSIGNED_FAULT = (
        "DEVICE_REBOOT_OR_POWER_LOSS_OUTSIDE_ASSIGNED_FAULT"
    )
    PREFLIGHT_OR_PROTOCOL_IDENTITY_MISMATCH = "PREFLIGHT_OR_PROTOCOL_IDENTITY_MISMATCH"
    FIXTURE_DRIFT = "FIXTURE_DRIFT"
    OPERATOR_INTERVENTION = "OPERATOR_INTERVENTION"
    EXTERNAL_CONTROLLER_OR_PREFLIGHT_EVIDENCE_ENVELOPE_INCOMPLETE_OR_SCHEMA_INVALID = (
        "EXTERNAL_CONTROLLER_OR_PREFLIGHT_EVIDENCE_ENVELOPE_INCOMPLETE_OR_SCHEMA_INVALID"
    )


class CandidateOutcome(str, Enum):
    CANDIDATE_CRASH = "CANDIDATE_CRASH"
    AUTHENTICATION_FAILURE = "AUTHENTICATION_FAILURE"
    COMMITTED_BYTE_LOSS = "COMMITTED_BYTE_LOSS"
    TAIL_LOSS_GATE_FAILURE = "TAIL_LOSS_GATE_FAILURE"
    UNRECOVERABLE_SPLIT_BRAIN = "UNRECOVERABLE_SPLIT_BRAIN"
    DUPLICATE_OR_MISSING_PROCESSING_INTENT = "DUPLICATE_OR_MISSING_PROCESSING_INTENT"
    CANDIDATE_TIMEOUT_OR_DEADLOCK = "CANDIDATE_TIMEOUT_OR_DEADLOCK"
    CANDIDATE_RECOVERY_OUTPUT_MISSING_OR_SCHEMA_INVALID_AFTER_CONFIRMED_KILL = (
        "CANDIDATE_RECOVERY_OUTPUT_MISSING_OR_SCHEMA_INVALID_AFTER_CONFIRMED_KILL"
    )


class QuantityViolation(str, Enum):
    COMMITTED_EXCEEDS_RECOVERED = "C_GT_R"
    RECOVERED_EXCEEDS_ACCEPTED = "R_GT_A"


class AttemptStage(str, Enum):
    READY = "READY"
    SIGNAL_IN_FLIGHT = "SIGNAL_IN_FLIGHT"
    SIGNAL_CONFIRMED = "SIGNAL_CONFIRMED"
    SIGNAL_OUTCOME_UNKNOWN = "SIGNAL_OUTCOME_UNKNOWN"
    DEATH_CONFIRMED = "DEATH_CONFIRMED"


def _require_string(value: object, field: str) -> None:
    if type(value) is not str or not value:
        raise ProtocolInputError(f"{field} must be a non-empty string")


def _require_int(value: object, field: str, *, minimum: int | None = None) -> None:
    if type(value) is not int:
        raise ProtocolInputError(f"{field} must be an integer")
    if minimum is not None and value < minimum:
        raise ProtocolInputError(f"{field} must be at least {minimum}")


def _require_bool(value: object, field: str) -> None:
    if type(value) is not bool:
        raise ProtocolInputError(f"{field} must be a boolean")


@dataclass(frozen=True)
class AttemptIdentity:
    attempt_id: str
    phase: str
    candidate: Candidate
    stratum: Stratum
    environment: str
    slot: int
    protocol_id: str
    gate_set_id: str
    build_identity: str
    artifact_identity: str
    preflight_identity: str
    fixture_identity: str
    target_pid: int

    def __post_init__(self) -> None:
        for field in (
            "attempt_id",
            "phase",
            "environment",
            "protocol_id",
            "gate_set_id",
            "build_identity",
            "artifact_identity",
            "preflight_identity",
            "fixture_identity",
        ):
            _require_string(getattr(self, field), field)
        if type(self.candidate) is not Candidate:
            raise ProtocolInputError("candidate must be a Candidate")
        if type(self.stratum) is not Stratum:
            raise ProtocolInputError("stratum must be a Stratum")
        _require_int(self.slot, "slot", minimum=1)
        if self.slot > 10:
            raise ProtocolInputError("slot must be at most 10")
        _require_int(self.target_pid, "target_pid", minimum=1)
        if self.protocol_id != ACTIVE_PROTOCOL_ID:
            raise ProtocolInputError("expected protocol must be active v0.6")
        if self.gate_set_id != ACTIVE_GATE_SET_ID:
            raise ProtocolInputError("expected Gate Set must be active v0.6")
        expected_attempt_id = (
            f"{self.phase}-{self.candidate.value}-{self.stratum.value}-{self.environment}"
            f"-slot-{self.slot:02d}"
        )
        if self.attempt_id != expected_attempt_id:
            raise ProtocolInputError("attempt_id does not match the exact base-attempt identity")


@dataclass(frozen=True)
class ObservedAttemptIdentity:
    attempt_id: str
    phase: str
    candidate_id: str
    stratum_id: str
    environment: str
    slot: int
    protocol_id: str
    gate_set_id: str
    build_identity: str
    artifact_identity: str
    preflight_identity: str
    fixture_identity: str
    target_pid: int

    def __post_init__(self) -> None:
        for field in (
            "attempt_id",
            "phase",
            "candidate_id",
            "stratum_id",
            "environment",
            "protocol_id",
            "gate_set_id",
            "build_identity",
            "artifact_identity",
            "preflight_identity",
            "fixture_identity",
        ):
            _require_string(getattr(self, field), field)
        _require_int(self.slot, "slot")
        _require_int(self.target_pid, "target_pid", minimum=1)


@dataclass(frozen=True)
class BarrierObservation:
    candidate_id: str
    stratum_id: str
    target_pid: int
    public_api_or_harness_callback: bool

    def __post_init__(self) -> None:
        _require_string(self.candidate_id, "candidate_id")
        _require_string(self.stratum_id, "stratum_id")
        _require_int(self.target_pid, "target_pid", minimum=1)
        _require_bool(self.public_api_or_harness_callback, "public_api_or_harness_callback")


@dataclass(frozen=True)
class CommitObservation:
    sqlite_end_transaction_returned: bool
    controller_commit_event_observed: bool

    def __post_init__(self) -> None:
        _require_bool(self.sqlite_end_transaction_returned, "sqlite_end_transaction_returned")
        _require_bool(self.controller_commit_event_observed, "controller_commit_event_observed")
        if self.controller_commit_event_observed and not self.sqlite_end_transaction_returned:
            raise ProtocolInputError("controller commit event cannot precede transaction return")


@dataclass(frozen=True)
class SignalReceipt:
    attempt_id: str
    target_pid: int
    signal_name: str
    confirmed: bool

    def __post_init__(self) -> None:
        _require_string(self.attempt_id, "attempt_id")
        _require_int(self.target_pid, "target_pid", minimum=1)
        _require_string(self.signal_name, "signal_name")
        _require_bool(self.confirmed, "confirmed")


@dataclass(frozen=True)
class DeathObservation:
    attempt_id: str
    target_pid: int
    independently_confirmed_dead: bool

    def __post_init__(self) -> None:
        _require_string(self.attempt_id, "attempt_id")
        _require_int(self.target_pid, "target_pid", minimum=1)
        _require_bool(self.independently_confirmed_dead, "independently_confirmed_dead")


class SignalPort(Protocol):
    def issue_sigkill(self, attempt_id: str, target_pid: int) -> SignalReceipt: ...


class LivenessPort(Protocol):
    def is_alive(self, attempt_id: str, target_pid: int) -> bool: ...


class AcceptedWatermarkOracle:
    def __init__(self) -> None:
        self._lock = Lock()
        self._accepted_watermark = 0
        self._active: tuple[int, int, int] | None = None
        self._next_token = 1

    @property
    def accepted_watermark(self) -> int:
        with self._lock:
            return self._accepted_watermark

    def begin_bounded_write(self, start: int, end: int) -> int:
        _require_int(start, "start", minimum=0)
        _require_int(end, "end", minimum=0)
        with self._lock:
            if self._active is not None:
                raise ProtocolInputError("only one bounded writer call may be in progress")
            if start != self._accepted_watermark:
                raise ProtocolInputError("bounded writer calls must form one gap-free interval")
            if end <= start:
                raise ProtocolInputError("bounded writer interval must be non-empty")
            if end > MAX_PLAINTEXT_BYTES_PER_RUN:
                raise ProtocolInputError("bounded writer interval exceeds the run limit")
            token = self._next_token
            self._next_token += 1
            self._active = (token, start, end)
            return token

    def complete_bounded_write(self, token: int) -> int:
        _require_int(token, "token", minimum=1)
        with self._lock:
            if self._active is None or self._active[0] != token:
                raise ProtocolInputError("token does not identify the active bounded writer call")
            _, start, end = self._active
            if start != self._accepted_watermark:
                raise ProtocolInputError("active bounded writer call no longer follows A")
            self._accepted_watermark = end
            self._active = None
            return end


@dataclass(frozen=True)
class RecoveryQuantities:
    accepted_end: int
    committed_end: int
    recovered_end: int

    def __post_init__(self) -> None:
        for field in ("accepted_end", "committed_end", "recovered_end"):
            value = getattr(self, field)
            _require_int(value, field, minimum=0)
            if value > MAX_PLAINTEXT_BYTES_PER_RUN:
                raise ProtocolInputError(f"{field} exceeds the run limit")

    @property
    def committed_loss_bytes(self) -> int:
        return self.committed_end - min(self.committed_end, self.recovered_end)

    @property
    def tail_loss_bytes(self) -> int:
        return self.accepted_end - self.recovered_end

    @property
    def tail_loss_seconds(self) -> float:
        return self.tail_loss_bytes / BYTES_PER_SECOND


@dataclass(frozen=True)
class QuantityAssessment:
    invariant_holds: bool
    violations: tuple[QuantityViolation, ...]

    def __post_init__(self) -> None:
        _require_bool(self.invariant_holds, "invariant_holds")
        normalized = tuple(self.violations)
        if any(type(item) is not QuantityViolation for item in normalized):
            raise ProtocolInputError("violations must contain QuantityViolation values")
        object.__setattr__(self, "violations", normalized)


@dataclass(frozen=True)
class AttemptEvidence:
    expected: AttemptIdentity
    observed: ObservedAttemptIdentity
    scheduled_before_execution: bool
    barrier: BarrierObservation | None
    controller_connected_until_signal_confirmation: bool
    reboot_or_power_loss_outside_assigned_fault: bool
    operator_intervention: bool
    signal_receipt: SignalReceipt | None
    death: DeathObservation | None
    graceful_finalize_after_trigger: bool
    envelope_complete_and_schema_valid: bool
    explicit_invalidators: frozenset[Invalidator]
    observed_candidate_outcomes: frozenset[CandidateOutcome]
    commit: CommitObservation | None
    quantities: RecoveryQuantities | None

    def __post_init__(self) -> None:
        if type(self.expected) is not AttemptIdentity:
            raise ProtocolInputError("expected must be an AttemptIdentity")
        if type(self.observed) is not ObservedAttemptIdentity:
            raise ProtocolInputError("observed must be an ObservedAttemptIdentity")
        for field in (
            "scheduled_before_execution",
            "controller_connected_until_signal_confirmation",
            "reboot_or_power_loss_outside_assigned_fault",
            "operator_intervention",
            "graceful_finalize_after_trigger",
            "envelope_complete_and_schema_valid",
        ):
            _require_bool(getattr(self, field), field)
        if self.barrier is not None and type(self.barrier) is not BarrierObservation:
            raise ProtocolInputError("barrier must be a BarrierObservation or None")
        if self.signal_receipt is not None and type(self.signal_receipt) is not SignalReceipt:
            raise ProtocolInputError("signal_receipt must be a SignalReceipt or None")
        if self.death is not None and type(self.death) is not DeathObservation:
            raise ProtocolInputError("death must be a DeathObservation or None")
        if self.commit is not None and type(self.commit) is not CommitObservation:
            raise ProtocolInputError("commit must be a CommitObservation or None")
        if self.quantities is not None and type(self.quantities) is not RecoveryQuantities:
            raise ProtocolInputError("quantities must be RecoveryQuantities or None")
        invalidators = frozenset(self.explicit_invalidators)
        outcomes = frozenset(self.observed_candidate_outcomes)
        if any(type(item) is not Invalidator for item in invalidators):
            raise ProtocolInputError("explicit_invalidators must contain Invalidator values")
        if any(type(item) is not CandidateOutcome for item in outcomes):
            raise ProtocolInputError("observed_candidate_outcomes must contain CandidateOutcome values")
        object.__setattr__(self, "explicit_invalidators", invalidators)
        object.__setattr__(self, "observed_candidate_outcomes", outcomes)


@dataclass(frozen=True)
class EvidenceAssessment:
    externally_valid: bool
    invalidators: frozenset[Invalidator]
    candidate_outcomes: frozenset[CandidateOutcome]
    quantity_assessment: QuantityAssessment | None

    def __post_init__(self) -> None:
        _require_bool(self.externally_valid, "externally_valid")
        invalidators = frozenset(self.invalidators)
        outcomes = frozenset(self.candidate_outcomes)
        if any(type(item) is not Invalidator for item in invalidators):
            raise ProtocolInputError("invalidators must contain Invalidator values")
        if any(type(item) is not CandidateOutcome for item in outcomes):
            raise ProtocolInputError("candidate_outcomes must contain CandidateOutcome values")
        if self.quantity_assessment is not None and type(self.quantity_assessment) is not QuantityAssessment:
            raise ProtocolInputError("quantity_assessment must be QuantityAssessment or None")
        object.__setattr__(self, "invalidators", invalidators)
        object.__setattr__(self, "candidate_outcomes", outcomes)


def _identity_invalidators(
    expected: AttemptIdentity,
    observed: ObservedAttemptIdentity,
) -> set[Invalidator]:
    invalidators: set[Invalidator] = set()
    identity_pairs = (
        (expected.attempt_id, observed.attempt_id),
        (expected.phase, observed.phase),
        (expected.candidate.value, observed.candidate_id),
        (expected.stratum.value, observed.stratum_id),
        (expected.environment, observed.environment),
        (expected.slot, observed.slot),
        (expected.protocol_id, observed.protocol_id),
        (expected.gate_set_id, observed.gate_set_id),
        (expected.build_identity, observed.build_identity),
        (expected.artifact_identity, observed.artifact_identity),
        (expected.preflight_identity, observed.preflight_identity),
    )
    if any(expected_value != observed_value for expected_value, observed_value in identity_pairs):
        invalidators.add(Invalidator.PREFLIGHT_OR_PROTOCOL_IDENTITY_MISMATCH)
    if expected.fixture_identity != observed.fixture_identity:
        invalidators.add(Invalidator.FIXTURE_DRIFT)
    if expected.target_pid != observed.target_pid:
        invalidators.add(Invalidator.WRONG_PID)
    return invalidators


def _barrier_invalidators(
    expected: AttemptIdentity,
    barrier: BarrierObservation | None,
) -> set[Invalidator]:
    if barrier is None:
        return {Invalidator.STRATUM_BARRIER_NOT_REACHED}
    invalidators: set[Invalidator] = set()
    if (
        barrier.candidate_id != expected.candidate.value
        or barrier.stratum_id != expected.stratum.value
        or not barrier.public_api_or_harness_callback
    ):
        invalidators.add(Invalidator.STRATUM_BARRIER_NOT_REACHED)
    if barrier.target_pid != expected.target_pid:
        invalidators.add(Invalidator.WRONG_PID)
        invalidators.add(Invalidator.STRATUM_BARRIER_NOT_REACHED)
    return invalidators


def derive_invalidators(evidence: AttemptEvidence) -> frozenset[Invalidator]:
    invalidators = set(evidence.explicit_invalidators)
    invalidators.update(_identity_invalidators(evidence.expected, evidence.observed))
    invalidators.update(_barrier_invalidators(evidence.expected, evidence.barrier))
    if not evidence.controller_connected_until_signal_confirmation:
        invalidators.add(Invalidator.CONTROLLER_DISCONNECTED_BEFORE_SIGNAL_CONFIRMATION)
    if evidence.reboot_or_power_loss_outside_assigned_fault:
        invalidators.add(Invalidator.DEVICE_REBOOT_OR_POWER_LOSS_OUTSIDE_ASSIGNED_FAULT)
    if evidence.operator_intervention:
        invalidators.add(Invalidator.OPERATOR_INTERVENTION)
    if not evidence.envelope_complete_and_schema_valid:
        invalidators.add(
            Invalidator.EXTERNAL_CONTROLLER_OR_PREFLIGHT_EVIDENCE_ENVELOPE_INCOMPLETE_OR_SCHEMA_INVALID
        )
    if evidence.signal_receipt is None or evidence.death is None:
        invalidators.add(
            Invalidator.EXTERNAL_CONTROLLER_OR_PREFLIGHT_EVIDENCE_ENVELOPE_INCOMPLETE_OR_SCHEMA_INVALID
        )
    else:
        if evidence.signal_receipt.target_pid != evidence.expected.target_pid:
            invalidators.add(Invalidator.WRONG_PID)
        if evidence.signal_receipt.attempt_id != evidence.expected.attempt_id:
            invalidators.add(Invalidator.PREFLIGHT_OR_PROTOCOL_IDENTITY_MISMATCH)
        if evidence.death.target_pid != evidence.expected.target_pid:
            invalidators.add(Invalidator.WRONG_PID)
        if evidence.death.attempt_id != evidence.expected.attempt_id:
            invalidators.add(Invalidator.PREFLIGHT_OR_PROTOCOL_IDENTITY_MISMATCH)
    return frozenset(invalidators)


def assess_quantities(quantities: RecoveryQuantities) -> QuantityAssessment:
    if type(quantities) is not RecoveryQuantities:
        raise ProtocolInputError("quantities must be RecoveryQuantities")
    violations: list[QuantityViolation] = []
    if quantities.committed_end > quantities.recovered_end:
        violations.append(QuantityViolation.COMMITTED_EXCEEDS_RECOVERED)
    if quantities.recovered_end > quantities.accepted_end:
        violations.append(QuantityViolation.RECOVERED_EXCEEDS_ACCEPTED)
    return QuantityAssessment(not violations, tuple(violations))


def assess_evidence(evidence: AttemptEvidence) -> EvidenceAssessment:
    if type(evidence) is not AttemptEvidence:
        raise ProtocolInputError("evidence must be AttemptEvidence")
    invalidators = derive_invalidators(evidence)
    outcomes = set(evidence.observed_candidate_outcomes)
    quantity_assessment = None
    if evidence.quantities is not None:
        quantity_assessment = assess_quantities(evidence.quantities)
        if evidence.quantities.committed_loss_bytes > 0:
            outcomes.add(CandidateOutcome.COMMITTED_BYTE_LOSS)
        if evidence.quantities.tail_loss_bytes > TAIL_GATE_BYTES:
            outcomes.add(CandidateOutcome.TAIL_LOSS_GATE_FAILURE)
    signal = evidence.signal_receipt
    death = evidence.death
    exact_signal = (
        signal is not None
        and signal.attempt_id == evidence.expected.attempt_id
        and signal.target_pid == evidence.expected.target_pid
        and signal.signal_name == SIGNAL_NAME
        and signal.confirmed
    )
    exact_death = (
        death is not None
        and death.attempt_id == evidence.expected.attempt_id
        and death.target_pid == evidence.expected.target_pid
        and death.independently_confirmed_dead
    )
    externally_valid = (
        evidence.scheduled_before_execution
        and not invalidators
        and exact_signal
        and exact_death
        and not evidence.graceful_finalize_after_trigger
    )
    return EvidenceAssessment(
        externally_valid,
        invalidators,
        frozenset(outcomes),
        quantity_assessment,
    )


@dataclass
class _AttemptRecord:
    expected: AttemptIdentity
    stage: AttemptStage
    receipt: SignalReceipt | None = None
    death_probe_in_flight: bool = False


class ExternalController:
    def __init__(self, signal_port: SignalPort, liveness_port: LivenessPort) -> None:
        self._signal_port = signal_port
        self._liveness_port = liveness_port
        self._lock = Lock()
        self._attempts: dict[str, _AttemptRecord] = {}

    def stage_for(self, attempt_id: str) -> AttemptStage:
        _require_string(attempt_id, "attempt_id")
        with self._lock:
            record = self._attempts.get(attempt_id)
            return AttemptStage.READY if record is None else record.stage

    def issue_after_validated_barrier(
        self,
        expected: AttemptIdentity,
        observed: ObservedAttemptIdentity,
        *,
        scheduled_before_execution: bool,
        barrier: BarrierObservation | None,
        controller_connected: bool,
        reboot_or_power_loss_outside_assigned_fault: bool,
        operator_intervention: bool,
    ) -> SignalReceipt:
        if type(expected) is not AttemptIdentity:
            raise ProtocolInputError("expected must be an AttemptIdentity")
        if type(observed) is not ObservedAttemptIdentity:
            raise ProtocolInputError("observed must be an ObservedAttemptIdentity")
        for field, value in (
            ("scheduled_before_execution", scheduled_before_execution),
            ("controller_connected", controller_connected),
            (
                "reboot_or_power_loss_outside_assigned_fault",
                reboot_or_power_loss_outside_assigned_fault,
            ),
            ("operator_intervention", operator_intervention),
        ):
            _require_bool(value, field)
        with self._lock:
            if expected.attempt_id in self._attempts:
                raise SignalAlreadyAttempted(expected.attempt_id)

        invalidators = _identity_invalidators(expected, observed)
        invalidators.update(_barrier_invalidators(expected, barrier))
        if not controller_connected:
            invalidators.add(Invalidator.CONTROLLER_DISCONNECTED_BEFORE_SIGNAL_CONFIRMATION)
        if reboot_or_power_loss_outside_assigned_fault:
            invalidators.add(Invalidator.DEVICE_REBOOT_OR_POWER_LOSS_OUTSIDE_ASSIGNED_FAULT)
        if operator_intervention:
            invalidators.add(Invalidator.OPERATOR_INTERVENTION)
        if not scheduled_before_execution or invalidators:
            raise SignalNotEligible("attempt is not eligible for the signal port", frozenset(invalidators))
        if not self._liveness_port.is_alive(expected.attempt_id, expected.target_pid):
            raise SignalNotEligible("target PID is not alive immediately before signaling")

        with self._lock:
            if expected.attempt_id in self._attempts:
                raise SignalAlreadyAttempted(expected.attempt_id)
            self._attempts[expected.attempt_id] = _AttemptRecord(
                expected=expected,
                stage=AttemptStage.SIGNAL_IN_FLIGHT,
            )

        try:
            receipt = self._signal_port.issue_sigkill(expected.attempt_id, expected.target_pid)
        except Exception as error:
            self._mark_signal_unknown(expected.attempt_id)
            raise SignalOutcomeUnknown(expected.attempt_id) from error

        exact_receipt = (
            type(receipt) is SignalReceipt
            and receipt.attempt_id == expected.attempt_id
            and receipt.target_pid == expected.target_pid
            and receipt.signal_name == SIGNAL_NAME
            and receipt.confirmed
        )
        if not exact_receipt:
            self._mark_signal_unknown(expected.attempt_id)
            raise SignalOutcomeUnknown(expected.attempt_id)
        with self._lock:
            record = self._attempts[expected.attempt_id]
            record.stage = AttemptStage.SIGNAL_CONFIRMED
            record.receipt = receipt
        return receipt

    def _mark_signal_unknown(self, attempt_id: str) -> None:
        with self._lock:
            self._attempts[attempt_id].stage = AttemptStage.SIGNAL_OUTCOME_UNKNOWN

    def confirm_independent_death(
        self,
        expected: AttemptIdentity,
        receipt: SignalReceipt,
    ) -> DeathObservation:
        if type(expected) is not AttemptIdentity or type(receipt) is not SignalReceipt:
            raise ReceiptRejected("death probe requires exact typed identity and receipt")
        with self._lock:
            record = self._attempts.get(expected.attempt_id)
            if (
                record is None
                or record.stage is not AttemptStage.SIGNAL_CONFIRMED
                or record.expected != expected
                or record.receipt is not receipt
                or record.death_probe_in_flight
            ):
                raise ReceiptRejected("receipt is not the stored receipt for the original identity")
            record.death_probe_in_flight = True
        try:
            alive = self._liveness_port.is_alive(expected.attempt_id, expected.target_pid)
        finally:
            with self._lock:
                self._attempts[expected.attempt_id].death_probe_in_flight = False
        death = DeathObservation(expected.attempt_id, expected.target_pid, not alive)
        if death.independently_confirmed_dead:
            with self._lock:
                self._attempts[expected.attempt_id].stage = AttemptStage.DEATH_CONFIRMED
        return death
