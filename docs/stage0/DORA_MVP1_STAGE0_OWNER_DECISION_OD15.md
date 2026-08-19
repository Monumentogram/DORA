# Dora MVP 1 — OD-15 REC-I3 and emulator-first owner record

Decision ID: `OD-15`\
Status: **Approved for the exact bounded REC-I3 implementation/non-metric-review scope and bounded
non-measured emulator-matrix checks; Recovery Phase A/measured execution and production admission
withheld**\
Date: **19 August 2026**\
Owner: **Project owner**\
Authorization baseline `main`: `671f594074b37bb2b5c8e4a4c1026de909acf339` / tree
`4c969005307bbe5ef2c9a69b0b2ae84476217dc3`\
Public machine record: `docs/evidence/owner-auth-batch-20260819-01.json`

This is an additive decision. It does not rewrite `OD-14`, `DEC-044`, the immutable Recovery
Gate Set/protocol or any historical evidence record whose contemporaneous REC-I3 flags were false.
The owner attachment is retained outside the repository. Its 9,075 bytes are not present in Git;
the locally recomputed SHA-256 is
`9966f95884d5210f7624133b30d7646bc0c49fefb260143f68532e429a648e89`.
The private source path and attachment body are intentionally not published.

## 1. Exact REC-I3 authority

The Project owner records the following current flags exactly:

| Flag | Value |
|---|---:|
| `recI3ImplementationAllowed` | `true` |
| `recI3NonMetricVerificationAllowed` | `true` |
| `recI3ConditionalMergeAllowed` | `true` |
| `phaseAAllowed` | `false` |
| `executionAllowed` | `false` |
| `measuredExecutionAllowed` | `false` |
| `productionAdmissionAllowed` | `false` |

Here `executionAllowed=false` is the exact attachment flag for the Recovery Phase A/kill/measured
campaign. It does not negate the supplemental direct owner approval for the bounded non-measured
emulator-matrix checks in section 3.

REC-I3 may implement only the preimplementation contract and isolated recovery harness/controller
slice required by active protocol v0.6, using deterministic repository-owned synthetic fixtures.
It may run host-JVM and other non-metric checks and may produce the exact graph, locks, verification
metadata, zero-JSR305, package inventory and release-R8 evidence required by the active Recovery
contract. Publication, independent advisory review, accountable review, exact-head CI and a
conditional protected squash merge are allowed. A merge remains conditional on those exact-head
gates and does not itself authorize execution.

REC-I3 may not run a hard-kill/fault campaign, claim Recovery PASS or READY, select a final
production crypto/container, admit production storage/schema, or change a threshold, existing
Gate Set/protocol semantic, Production Legal/Security state or dependency-production status.

## 2. Later bounded preflight

Only after successful REC-I3 implementation and review may a separately executed preflight query
the approved pinned emulator environments and existing D2 for the exact SQLite version/source/
compile-options, WAL/FULL/zero-autocheckpoint/foreign-key profile, Android Keystore availability
and alias lifecycle, and filesystem/durability prerequisites. It must bind a sanitized result to
the exact commit, APK digest and environment profile.

That Recovery preflight is not Phase A, a benchmark, a kill, a metric campaign or a verdict. It is
available only after successful REC-I3; the supplemental matrix authority does not bypass that
ordering. This
documentation/evidence reconciliation executes no emulator or physical device and records no
preflight result.

## 3. Emulator-first evidence matrix

The owner directly approves the following orthogonal emulator slots. The E namespace avoids
silently resolving `DEVICE-ID-MAPPING-001`, where Technical Plan and Test Strategy disagree about
D6/D7. An E slot is an environment target, not evidence that its artifact is pinned or executable.

| Slot | Purpose | Current pin state |
|---|---|---|
| `E28` | Android API 28 compatibility | `OPEN` |
| `E30` | Android API 30 compatibility | `OPEN` |
| `E36-GAPI` | Android API 36 Google APIs x86_64 reference | exact r07 pin recorded in `device-matrix.yaml` |
| `E-NOGMS` | AOSP/no-GMS behavior | `OPEN` |
| `E16K` | 16-KiB runtime/package behavior | `OPEN` |
| `E-NEXT` | next-API behavior | `OPEN` |

Evidence is collected emulator-first. Existing D2 is used only where the property intrinsically
requires a physical device. Procurement of D1 and D3–D6 is postponed until emulator-slot plus D2
evidence exists and a concrete final gate identifies the missing physical profile. This does not
waive any final physical matrix requirement, change D7, or create a support claim.

An emulator never substitutes for physical microphone input, physical flash/durability, battery,
thermal behavior, OEM lifecycle behavior, radio/VPN route behavior or arm64 physical performance.
Conversely, a physical device does not substitute for an exact required emulator image, 16-KiB
runtime or next-API environment.

The owner authorizes fixing and verifying this E-slot matrix and executing all available
**non-measured functional, fault, compatibility and preflight checks** once the exact environment
pin is available and the prerequisites of the individually scoped task are met. `OPEN` pins are
not executable pins; the exact E36-GAPI pin still requires runtime availability and task-specific
prerequisites. This authority is bounded to the E slots and does not authorize an unbounded device
or emulator campaign. It does not authorize a Recovery hard-kill/fault campaign, Recovery Phase A
or a measured Recovery campaign. Recovery preflight remains gated on successful REC-I3.

D2 may be used only when the evidence intrinsically requires a physical device and the separately
scoped task prerequisites are satisfied; this is not blanket physical execution, procurement or
support authority. No emulator or physical execution was performed by this owner record or by the
documentation reconciliation PR.

## 4. Documentation and evidence reconciliation

The owner authorizes one additive docs/evidence PR to reconcile the active Stage Status, Backlog,
Implementation Readiness, execution order and machine-readable closure records to facts proven on
`main` after PR #43. Historical source, tests, Recovery/KSP evidence and validator pins remain
byte-preserved. The reconciliation may record only proven commit/tree/PR/CI/review facts and may
not elevate any PoC to PASS/READY, perform emulator/device execution, authorize measured execution,
admit a dependency/model/product surface, or rewrite a historical pending/failure field. The
supplemental bounded execution authority in section 3 remains current even though this PR performs
no such execution.

## 5. Remaining owner-only gates

- Recovery `executionAllowed=true` for Phase A/kill/measured campaigning requires a later explicit
  owner record after every applicable precondition is complete.
- A final Recovery PASS remains impossible without the required valid physical profile and full
  active campaign evidence.
- Production Legal and Production Security remain separate and unassigned for recovery admission.
- Production crypto/container/storage admission still requires post-evidence ADR and admission
  review.
