# Dora MVP 1 — REC-I3 bootstrap provider-witness correction

Contract ID: `rec-i3-bootstrap-provider-witness-correction-stage0-v0.1`  
Backlog: `POC-RECOVERY-001` / partial `REC-I3` implementation correction  
Date: 5 September 2026  
Authority: `OWNER-AUTH-BATCH-20260819-01` / OD-15  
Corrected predecessor: `d6b419e38658725155cf2d4fa469b6d2053ee77c` / tree `12137d39ac7046a91e5a39e3efd77a42ee900e36`

## Additive correction boundary

The frozen run-key bootstrap scope remains immutable. This additive correction resolves the
observability gap inside its required `RecoveryRunAeadProvider.createNew` route without changing
REC-I2B source, typed crypto semantics, dependencies, publication authority or the KC01–KC13
protocol order.

`RecoveryRunAeadProvider.createNew` performs two runtime operations in order: its backend
`generateNew` must return, then its backend `getAead` must return before the provider returns the
typed `RecoveryRunAead`. A bootstrap-local witnessing backend may delegate those same two calls and
record only the observed successful return of `generateNew`. The provider remains the route that
constructs the typed run AEAD. The witness is post-hoc boundary evidence about a completed backend
call; it does not reorder, skip, retry or replace either runtime operation.

The witness produces these exact controller observations:

1. If `generateNew` throws, KC02 fails. Alias creation is `CREATION_OUTCOME_UNKNOWN`, because a
   throwing platform call cannot prove either absence or durable creation.
2. If `generateNew` returns and `getAead` throws, KC02 is complete, alias creation is
   `CONFIRMED_CREATED`, and KC03 fails with the original `getAead` cause.
3. If both calls return, KC02 is complete, alias creation is `CONFIRMED_CREATED`, and KC03 consumes
   the exact typed `RecoveryRunAead` returned by `RecoveryRunAeadProvider.createNew`.

No caller-provided boolean or status may establish the witness. Only control flow after the
delegated `generateNew` return changes the private witness state. The private successful-KC12 proof
remains the only constructor authority for `BootstrapPublicationCapability`; no creation outcome,
witness or failure can mint publication authority.

## Exact implementation and evidence boundary

The correction may change only the already admitted bootstrap crypto/controller source and focused
test, the additive bootstrap evidence record, this contract, and the narrow REC-I3 successor
validator plus its tests. It adds no dependency, schema, production feature, device execution,
preflight, fault campaign, hard kill or measurement. Meaningful host tests must execute the actual
provider with a recording backend for generate-success/get-failure and generate-failure outcomes,
and must verify the controller KC step and durable alias observation.

All first-slice and REC-I2B source hashes, the frozen bootstrap scope, build files, locks, R8 policy,
historical evidence, ten readiness blockers, `fullRecI3Completed=false` and the Recovery preflight
lock remain unchanged. This correction grants no execution, readiness, production admission, push,
merge or future merged-main claim.
