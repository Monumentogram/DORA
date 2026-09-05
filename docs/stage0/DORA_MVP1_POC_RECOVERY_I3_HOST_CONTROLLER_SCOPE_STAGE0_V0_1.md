# Dora MVP 1 — REC-I3 host external-controller core

Scope ID: `rec-i3-host-controller-core-stage0-v0.1`\
Backlog: `POC-RECOVERY-001` / partial `REC-I3`\
Date: 5 September 2026\
Authority: `OWNER-AUTH-BATCH-20260819-01` / [OD-15](DORA_MVP1_STAGE0_OWNER_DECISION_OD15.md) §1\
Implementation base: `b90305f1aa387c6668320b03e8aa33b754a7162a` / tree
`6bddf5cb44030b6975a3cc9e8807672d44953c79`

## 1. Selected boundary

This additive scope permits exactly two new Python standard-library files:

- `tools/rec_i3_external_controller.py`; and
- `tools/test_rec_i3_external_controller.py`.

They implement and test only the host-side, single-base-attempt controller semantics inherited by
active `poc-recovery-protocol-stage0-v0.6` from the SHA-256-pinned v0.5/v0.3 contract. The covered
normative inputs are v0.3 `/fixture`, `/definitions`, `/hardKillCampaign` and `/evidencePolicy`.
The effective v0.6 `KEY-04` override is outside this scope.

The module may define immutable typed values, a deterministic completed-write watermark oracle,
raw recovery-quantity assessment, exact invalidator/candidate-outcome routing and injected abstract
signal/liveness ports. Tests use only deterministic in-memory synthetic identities and fake ports.
There is no concrete operating-system, ADB, Android, filesystem, network, crypto, recovery or
campaign adapter.

The active protocol requires a complete schema-valid external evidence envelope but defines no
standalone serialized controller-result schema. This scope therefore permits no JSON serializer or
new public evidence format. Later runtime adapters and evidence publication require separate exact
scope and review.

## 2. Additive technical choices

These choices implement frozen semantics without changing the Gate Set or protocol:

1. A strict `AttemptIdentity` represents only the expected authorized active-protocol base attempt.
   A distinct `ObservedAttemptIdentity` preserves raw non-active protocol/Gate/candidate/stratum,
   PID and fixture observations so drift can route fail-closed instead of failing construction.
2. `RecoveryQuantities` retains raw non-negative bounded `A`, `C` and `R`. The
   `0 <= C <= R <= A` invariant is assessed separately. Values are never reordered, clamped or
   normalized. `C > R` computes positive committed loss and remains the frozen candidate outcome
   `COMMITTED_BYTE_LOSS`, never an external invalidator.
3. One controller instance owns a lock-protected stage per exact base attempt. It records
   `SIGNAL_IN_FLIGHT` before invoking the injected signal port. Sequential, concurrent, reentrant,
   exception and unknown-receipt paths cannot issue a second signal for that attempt.
4. A signal-port exception or malformed, unconfirmed, wrong-signal, wrong-PID or wrong-attempt
   receipt leaves terminal `SIGNAL_OUTCOME_UNKNOWN`. The uncertainty is not retried silently.
5. Independent death probing accepts only the exact confirmed receipt object retained by the same
   controller for the complete original expected identity. A copied, cross-attempt,
   cross-controller or retargeted receipt is rejected before liveness probing.
6. Only base attempt IDs matching the frozen slot-01..10 pattern are accepted. The inherited `-R1`
   replacement mechanism is not implemented and no replacement API is exposed.

All DTOs own immutable scalar values or immutable collections and reject booleans where the
contract requires integers. Controller state preserves the complete original expected identity;
reusing an attempt ID with changed PID, build, artifact, preflight, fixture, candidate, stratum or
environment cannot retarget signaling or death confirmation.

## 3. Acceptance evidence

The standard-library `unittest` suite must first fail because the production module is absent, then
pass after implementation. It covers both candidates, K01–K12, slot bounds, expected/observed drift,
all eight external invalidators, all eight never-invalid candidate outcomes, completed-call-only
`A`, raw/invariant-separated C/R/A quantities, positive committed-loss preservation, commit-event
ordering, exact public-barrier eligibility, at-most-once sequential/concurrent/reentrant signal
issuance, terminal port uncertainty, and exact-receipt independent death probing.

Passing host tests prove only deterministic controller logic over supplied facts. They do not prove
that a runtime callback is an actual barrier, that cryptography or durability occurred, that a PID
belongs to an Android target, that `SIGKILL` reached a process, that death/recovery evidence is
genuine, or that a deterministic fixture ran in the candidate runtime. Fixture evidence cannot mint
runtime, dependency or production admission.

No Android/Gradle/R8/package test is required by this isolated host scope. The common writer alone
later owns the successor governance validator, evidence record and backlog/status integration after
the reconciliation and host slices have clean reviews.

## 4. Claim ceiling

This slice is not full REC-I3 and closes no Recovery readiness blocker. It provides no real signal,
ADB command, campaign runner, attempt aggregation, replacement attempt, metric, verdict, public
evidence schema, runtime adapter, package proof, preflight result or dependency/production
admission. `phaseAAllowed=false`, `executionAllowed=false`, `measuredExecutionAllowed=false` and
`productionAdmissionAllowed=false` remain exact. No device/emulator execution, Recovery hard-kill
or fault campaign, PASS/READY claim, PR merge or shared-governance mutation is performed.
