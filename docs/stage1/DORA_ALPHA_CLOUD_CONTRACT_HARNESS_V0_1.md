# 7.3C — Deterministic Cloud contract harness v0.1

Status: PASS / DETERMINISTIC_CLOUD_CONTRACT_HARNESS_READY.

## Scope and decision

Compose frozen 7.2E authorization, 7.2C provider observations and 7.2D transcript
transitions in a hermetic Python host model. All fixtures are synthetic and
non-production. No socket, provider, storage service, audio, credential, database
or Android runtime is used. GitHub/CI and Sheet publication are task coordination,
outside the harness. The current 7.3 release alpha.2 (4) remains unchanged.

The explicit autonomous task supplies the design and authorizes inline execution,
one implementation commit followed by one docs/evidence commit. This overrides
additional design approval pauses and separate planning commits in Superpowers.
The existing isolated alpha-foundation worktree is reused.

## Architecture

| Fixture | Responsibility | Real resource |
|---|---|---|
| FakeClock | Explicit UTC epoch, integer microsecond advances, expiry/backoff | None |
| SyntheticNetwork | Ordered/delayed/duplicated/reordered inert observations | None |
| SyntheticByteStore | Count accepted/rejected parts, cumulative authority ceiling | None |
| SyntheticProvider | Deterministic submit outcomes and authoritative reconciliation | None |
| SyntheticControlPlaneLedger | Compose current authorization, job/attempt/result and selection guards | None |

```mermaid
sequenceDiagram
  participant S as Synthetic scenario
  participant L as Synthetic ledger
  participant A as Frozen 7.2E validator
  participant P as Synthetic provider / 7.2C
  participant T as Frozen 7.2D validator
  S->>L: create / issue / bounded part
  L->>A: current request and resolved snapshot
  A-->>L: ALLOW or DENY
  L->>L: count bytes only after validation
  L->>P: authorized dispatch
  P-->>L: observation (possibly UNKNOWN)
  L->>P: reconcile before any uncertain retry
  P-->>L: correlated validated SUCCEEDED
  L->>T: immutable version / guarded activation
  T-->>L: retain version; protect edits and manual selection
```

Single-threaded event ordering models the linearization point between revalidation
and byte acceptance. It does not prove production atomicity, cryptographic proof,
durability, provider semantics, erasure, merge text quality or runtime readiness.
Request-bound verification flags are synthetic server facts, never real credentials.
The JSON scenario catalogue binds initial state, event ordering, expected decisions,
counters and final state. Test expectations are literals independent of the model.
Traces contain closed event/reason codes, fake time, synthetic identifiers and counts;
never text, payload, audio or secrets. Byte fixtures are counts only.

## Safety invariants and counters

Every bounded part and retry revalidates current authentication, ownership, consent,
source, route, action/job and generation vector. Issuance evidence is independently
validated against its preserved snapshot. Accepted bytes accumulate across retries
and authority reissuance for the same job/source. Denied operations accept zero new
bytes; historical accepted bytes remain counted. No callback creates job state.
An uncertain submission permits only reconciliation, never immediate resubmission.
Provider conflict alone cannot publish. Each intended action has one logical job,
fresh attempts and at most one accepted logical result. Result validation precedes
publication, which precedes a separate 7.2D activation decision. Manual selection,
edits, cancellation intent and tombstones fence automatic activation.

Counters: logical actions, logical jobs, attempts, provider submits, upload
authorities, accepted bytes, rejected bytes, results published and selection moves.
Selection moves count changes made by the harness after its initial Local snapshot.

## Scenario matrix and adversarial regression coverage

The machine-readable catalogue contains 62 literal expected event/counter scenarios;
35 host tests additionally exercise rejected evidence and structural mutations.

| Group | Scenarios / assertion |
|---|---|
| AUTH | 12 missing/denied/mismatched current-authority cases: zero accepted bytes |
| UPLOAD | 10 generation/expiry fences; 400+400+300 ceiling; denied creation; missing authority |
| PROVIDER / RESULT | Happy path; output-only; six correlation/mapping failures; duplicate winner |
| RETRY / RECONCILIATION | Uncertain wait/found/proven nonacceptance/conflict; permanent/transient/backoff; permanent classification survives reconciliation |
| TRANSCRIPT / EDIT / LATE | Retained Local/Cloud/edit; manual selection; actual stale acceptance rejection; positive resolved proposal acceptance |
| CANCELLATION / DELETION | Requested/unknown/confirmed; reordered cancellation; protective cancel after revocation; tombstone |
| NETWORK | Correlated delayed/duplicate/reordered responses; duplicate request delivery; definite rejection; response loss; failure before dispatch/after possible acceptance |

Queued envelopes copy their request, operation and result at enqueue time. An old
attempt cannot acquire the current attempt's identity at delivery. A separate
dispatch-time namespace guards mutable operation/result evidence. Request events
revalidate current authorization when delivered; reconnect grants nothing.

The independent review identified five important composition/coverage findings
and one minor vacuous test. Each was reproduced before its correction: permanent
rejections now survive reconciliation, confirmed cancellation is monotonic, queued
callbacks preserve correlation, dispatch namespace remains independent, and stale
proposal acceptance invokes the frozen decision/transition guard. Binding forgery
tests now start with an unbound ledger and assert the intended rejection reason.
The proposal positive control uses explicitly synthetic user resolutions and an
inert merged version; it implements no merge algorithm or real user decision.

Prior 7.2C–E important findings are covered where composed here: uncertain/conflict
classification, output-only/terminal ordering, namespace mismatch, independently
validated issuance, retry-attempt evidence, late/manual selection, cyclic merge
provenance rejection, system-forged user rejection, and generation reset through
null. Batch selection is outside this one-recording ledger; its all-presented-record
negative controls remain mandatory in the unchanged 7.2E suite.

## Implementation plan / completion ledger

1. Verify exact start, clean isolation, frozen contracts and 150 existing tests.
   Completed before edits: HEAD/origin `1e34b9d7fe4a7f4aa43095244d75bbe3e1b49018`,
   parent `351874fff41774f10298e8a186bdc78bf6bb720f`, grandparent
   `d48e41a65c377dc17d9ac3a0384d687061bc33c6`; main and both draft PRs unchanged.
2. Write failing composed scenarios; implement fixtures/ledger under tools only.
   Validate frozen domains through imports; run complete harness twice and compare
   normalized outputs/digests. Expected RED before implementation, then GREEN.
3. Add structural/hermetic guard and mandatory CI step. Preserve every existing
   gate. Regression-test historical release provenance against its frozen closure
   and verify current product inputs are identical. No release rebuild or signing.
4. Fresh independent adversarial review; reproduce/fix important findings using
   RED→GREEN regression tests. Verify all local relevant checks.
5. Commit/push implementation; require both jobs and mandatory harness step at the
   exact implementation SHA. No PASS status before this gate.
6. Evidence/status/Sheet closure, docs-only commit, exact final-SHA CI, PR metadata
   update/refetch and final Sheet readback. No Stage 8 execution.

Pre-flight interfaces: authorization decisions feed the byte store and provider
request references; validated provider output feeds transcript publication; frozen
activation transitions guard the selection projection. No contract schema changes.
Ruling: historical release CI inventory and docs-only closure must be checked at
the immutable 7.3 closure, then current release inputs separately; comparing old
receipts to newly added CI steps is temporally invalid. Cost if wrong: missed
release drift; negative controls and frozen-input comparison must catch it.

Review focus: forged issuance, unauthorized bytes, retry ceiling reset, stale
authority, retry before reconciliation, duplicate submit/winner, namespace drift,
conflict-as-success, cancel intent-as-confirmation, stale edit/selection proposals,
tombstone resurrection, reconnect consent, clock/network leakage, false runtime claims.

## Non-execution

Stage 8 NOT_STARTED. Recording/VAD/product audio storage, transcript persistence,
merge/auth/backend/object-storage/AWS-adapter runtime NOT_IMPLEMENTED. Recovery
integration/audio upload NOT_RUN. Real network/provider/audio NOT_USED by harness;
AWS NOT_CALLED, spend 0 BY THIS TASK; FIRST_REAL_PRODUCT_AUDIO NOT_READY.
Next separate roadmap task: Stage 8 preparation; Recovery clean replacement is
required before Stage 8 recording/storage acceptance. Group C remains IN PROGRESS.

## Technical closure — 2026-09-30

Implementation `7899d3eb134b549258316ae9369459afa77857a3` passes [exact-SHA CI 36731600770](https://github.com/Monumentogram/DORA/actions/runs/36731600770), both mandatory jobs and every required step. 62 scenarios and 35 harness tests pass; two separate processes (hash seeds 1/937), each running twice, produce identical report bytes, counters and trace digest `e3fbb85ac3c1dafe2ac8ad59d05bae2b86cae0f45133e725b80b2ad37566288c`. 150 frozen-contract tests, 34 signing/release/boundary tests and 401 JVM tests pass locally. All five important and one minor review findings are corrected and regression-tested; no unresolved P0/P1. See the five `alpha-7.3c-*-v0.1.json` evidence files.

Stage 7 = PASS / ALPHA_FOUNDATION_READY; Group C remains IN_PROGRESS; Stage 8 NOT_STARTED. The subsequent docs-only HEAD still requires exact-SHA CI, final PR metadata and Sheet readback, recorded separately to avoid a self-referential commit.
