# DEC-045 — POC recovery streaming authenticated-tail semantics

Status: **Approved**
Approved by: **Project owner**
Approved on: **2026-09-05**
Scope: **Stage 0 `REC-I3` streaming-recovery semantic only**

## Decision source and reviewed basis

The Project owner selected Option A in parent task
`01a07028-54d4-79b1-8549-126c2ec0b98a`. This record is made through the delegated recovery task
and is the governing decision for the bounded semantic gap identified by the streaming proof.

The decision is based on the immutable final proof commit
`19807f6ea8166fff972e1537beeb131def8bfa1d`, tree
`8920855c9b8642162e5df16204d796d51db4fa86`, and proof-scope commit
`477b5e354c8649fb0202fb039758526c4d41de03`. The final static-correction independent review is
CLEAN with P0/P1/P2 = 0/0/0: Markdown SHA-256
`93f763a93e0fdb79a1658f84194abf5162d6e2a7f1038478a97b4d7f37080f11`; JSON SHA-256
`4c7cc94087262ae46c13dbba823d04c5103671963f58d6f86937a798d8c4bd04`.

The owner decision packet is the external prepared artifact
`rec-i3-streaming-after-proof-decision-packet.md`, raw SHA-256
`becb78d81e8bea5fdccb2e109b6b8eeada6f47353399222900bd0af5756e0db3`.
The packet and proof remain evidence; this DEC supplies the prospective semantic authority that
they intentionally did not choose.

## Approved Option A semantic

Independently authenticated, pre-existing, contiguous oracle-equal bytes may extend recovered `R`
beyond durable checkpoint `C`. A successful completed authenticated read remains in `R` even when a
later public read terminates in authentication failure. `C` never advances as a consequence of this
recovery result.

No metadata, semantic commit, processing intent, or other durable processing meaning is adopted
from unauthenticated bytes. The unauthenticated or corrupt remainder is rejected and routed to
bounded quarantine. The invariant remains `0 <= C <= R <= A`; every returned byte is authenticated.
The 8,160-byte / 0.255-second design bound remains unchanged.

Physical-source extent means only the bounded observed candidate bytes defined by the applicable
synthetic harness policy. This decision does not authorize treating arbitrary post-crash append data
as trusted. For `TRU-03`, prefix integrity means recovered byte values and their authentication are
unchanged; it does not require the recovered endpoint to be identical when independently
authenticated pre-existing bytes become readable. A same-file suffix cannot silently promote a
whole object.

## Scope and boundaries

Option B is rejected for this scope only; its historical proof discussion remains unchanged.
Streaming source work is now eligible only within the separately recorded exact synthetic,
non-metric Stage 0 scope under `OD-15`. This DEC changes no active Gate Set/protocol text or JSON,
threshold, protocol version, ADR status, dependency, runtime implementation, schema, evidence
record, workflow, campaign status, or production-admission state.

Persistence schema, source-extent enforcement, quarantine mechanics, typed diagnostics and any
durability behavior require a later accepted ADR and implementation scope. No device,
process-death, durability campaign, Recovery Phase A, measured campaign, PASS/READY claim, or
production admission is authorized. The ten active readiness blockers remain unchanged.

## Reversibility and effects

This decision is reversible before a later accepted ADR and implementation admission. Any semantic
change requires a new prospective DEC and any required successor contract; it cannot rewrite the
immutable proof, its 27 observations, `DEC-044`, or the active v0.6 Gate Set/protocol.

The next scope may build only a deterministic synthetic host/JVM seam and proof-test extension.
It must demonstrate authenticated-tail accounting and failure classification without advancing `C`
or adopting unauthenticated metadata. It is not authority to implement persistence, a production
controller, or quarantine storage.
