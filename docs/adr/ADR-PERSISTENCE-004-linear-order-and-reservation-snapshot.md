# ADR-PERSISTENCE-004 — Linear order validation and one-use reservation snapshot

Status: implementation decision; acceptance requires the Stage 8.6C.3 evidence gates.

Owner scope: Stage 8.6C.3, 9 October 2026. Parent: `9c6b7563eca5b6c003ad2e192a887b5df2630e2d`.

## Problem and decision

The C2 investigation measured quadratic catalog-order traversal and five complete
catalog loads per ordinary append. These are demonstrated inefficiencies; they
are not a proven explanation of the historical LONG-02 latency spikes.

Replace prefix scans in `RecoveryAudioBridge.validateOrder` with a fresh map from
physical segment identity to its first-frame origin. All existing validation
predicates and their order remain. The map lives only for this validation call.

Within one `RoomAudioJournal` lease, retain a private, defensively copied snapshot
from a successful complete catalog load. Only the next append reservation may
consume it, once, after current authorization, identity and mutation fences.
Do not cache pending, finalized, uncertain or in-transaction observations.
Clear the snapshot before a new owned load, every transaction entry, uncertainty,
successful lease acquisition/release and close. A failed reservation consumes it.

The append entry load and reservation precondition can therefore share one
observation under the existing sole-handle/lease ownership contract. Reservation
post-commit validation, CAS precondition and CAS post-commit validation still
load the real catalog. Public `load` always queries storage. Finalization never
uses this optimization. The detailed ownership and invalidation proof is in
[catalog-reuse-proof](../evidence/persistence-optimization-8.6c3/catalog-reuse-proof.md).

## Required evidence

The [preregistered protocol](../evidence/persistence-optimization-8.6c3/benchmark-protocol.md)
requires identical synthetic encrypted harnesses on API28 and API36, measured
at 10, 400, 1,000 and 2,000 blocks. Acceptance requires exactly five-to-four full
loads, linear operation counts, at least 10% median append-thread CPU improvement
at both large sizes on each API, and no wall-time regression beyond the stated
tolerance. Preserve every attempt and disclose ordering/host/Keystore confounders.

Negative controls cover malformed identity/order/physical origins, duplicate and
overflow cases, mutable caller lists, lease changes, failed loads, revoked access,
rollback, uncertain transaction completion and retained full post-commit readback.
Run the inherited interrupted-transaction and abrupt-process-death suites, real
authenticated reopen/readback, exact-SHA CI and independent review before PASS.

## Unchanged contracts and limits

There is no schema, PCM, encryption, key, tombstone, Recovery-rule or durability
barrier change. The outstanding-frame limit remains 256,000. Source files and
historical evidence are admitted through a separate exact successor contract;
old evidence is not resealed or replaced. A passing engineering result does not
close physical Stage 8.6 acceptance.

`LONG02_ROOT_CAUSE = NOT_PROVEN`

`LONG02_PHYSICAL_PROTECTION = NOT_INSTALLED`

`STAGE_8_6 = NOT_READY`

POCO launch, installation, acquisition, readback, mutation and another recording
campaign remain outside this task. A later owner decision is required.
