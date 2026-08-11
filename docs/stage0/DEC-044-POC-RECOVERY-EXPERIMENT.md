# DEC-044 — POC-RECOVERY-001 pre-PoC experiment decision

Status: **Proposed — owner constraints frozen; package and independent Engineering/Security review pending**\
Recorded for: **Project owner**\
Recorded on: **2026-08-12**\
Gate Set: `poc-recovery-stage0-v0.1`\
Scope: governance and readiness preparation for `POC-RECOVERY-001` only\
Execution authorized: **no**

## Context

`POC-RECOVERY-001` must determine whether Dora can preserve an authenticated, contiguous
committed prefix after abrupt process death without losing any committed byte and while limiting
uncommitted tail loss to five seconds. The Technical Plan names Tink Streaming AEAD and sealed
AEAD microfiles as candidates, but evidence must precede a final audio/container decision.

This record is deliberately a **Proposed experiment decision**, not `ADR-AUDIO-001`, a production
architecture decision, dependency admission, or permission to implement or execute a harness.

## Proposed experiment

One future isolated recovery harness would compare exactly two candidates against the same
synthetic input, kill controller, journal, recovery oracle and evidence schema:

1. `REC-STREAM-TINK`: Tink `StreamingAead` through public API only. The exact evaluation artifact
   is `com.google.crypto.tink:tink-android:1.23.0`. `AES128_GCM_HKDF_4KB` is only a **Proposed
   evaluation template**. Its public-API construction, checkpoint semantics and recovery safety
   require independent recovery-scoped Engineering/Security approval.
2. `REC-MICROFILE-TINK`: individually sealed Tink `Aead` microfiles plus an authenticated,
   generation-numbered manifest. Five seconds is the only cadence eligible under the current
   tail-loss gate. Fifteen- and thirty-second cadences may be recorded only as explicitly labeled
   observations, or evaluated after a five-second failure as fallbacks; neither can receive PASS
   under this Gate Set. The microfile AEAD template is intentionally unselected pending the same
   independent review.

No candidate is preferred in advance. A final `ADR-AUDIO-001` may be proposed only after valid
evidence and cannot infer production admission from a Stage 0 result.

## Frozen safety invariants

- Every committed plaintext byte must recover as part of one authenticated contiguous prefix.
  Loss of committed bytes is always exactly zero.
- Tail loss is computed from writer-accepted synthetic PCM bytes to the end of the recovered
  authenticated prefix and must be no more than `5.000` seconds (`160000` bytes at mono PCM16,
  16 kHz) on every valid hard kill.
- Each candidate has 120 base hard-kill attempts. At least 100 confirmed valid hard kills are
  required, with all invalid attempts retained and explained. Candidate-caused failures are valid
  results and may never be reclassified as invalid.
- Recovery must reject unauthenticated bytes, distinguish key unavailability from corruption,
  quarantine ambiguous/orphaned state without silent deletion, converge idempotently and never
  restart the microphone automatically.
- Android Keystore wraps test key material. Run keys are unique per run; streaming segment keys
  are derived within the approved streaming construction, while microfile segment keys are unique
  per sealed microfile. No key bytes, keysets, plaintext, raw database or recovery ciphertext may
  enter Git, Actions artifacts or logs. Key loss is a mandatory fault case.
- A PoC-local platform `android.database.sqlite` journal is allowed only for DB/file split-brain
  and reconciliation tests. Room, SQLCipher, WorkManager, production schema and production
  migrations are prohibited.

The normative definitions, predicates, strata, invalidation rules and fault matrix are in
`DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_1.md` and the machine-readable
`poc-recovery-protocol-stage0-v0.1.json`.

## Device and verdict contract

Phase A may, in principle, use the pinned API 36 emulator image and the available physical D2.
It remains execution-blocked until package review, independent Engineering/Security approval and
a later, explicit Project-owner authorization. Phase A can produce only `FAIL` or `INCONCLUSIVE`;
`PASS` is structurally forbidden because D1 and D5 are unavailable.

A full physical verdict requires a separately authorized campaign on physical D1, D2 and D5.
Purchasing D1 or D5 is not required now. Emulator evidence never substitutes for a required
physical profile.

## Review and authority boundary

- The Project owner is the Stage 0 Product/IP reviewer and the only person who may later authorize
  execution. Product/IP approval does not approve crypto engineering or security.
- An independent Engineering/Security reviewer, not the package author and not acting as
  Production Security, must approve the crypto template, key hierarchy, associated data,
  checkpoint/commit semantics, manifest protocol, kill mappings and recovery state machine before
  execution. That reviewer is currently unassigned.
- Production Legal is unassigned. Stage 0 evaluation review does not grant redistribution or
  production rights.
- Production Security approval remains a separate future gate and is not replaced by the
  recovery-scoped review.

## Explicitly forbidden by this decision

- adding Tink or another recovery dependency to any Gradle/runtime graph;
- creating `:poc:recovery`, a recovery harness, production schema or production `:app` change;
- running a kill campaign, device test, benchmark or measurement;
- using internal/reflection-based Tink APIs or silently changing candidate/template/cadence;
- treating this record as final `ADR-AUDIO-001` or dependency admission.

## Readiness state

`executionAllowed=false`. The readiness checker must fail closed until the Proposed Gate Set and
protocol are independently approved, exact future harness resolution is reviewed, required
preflights are present and the Project owner records a new execution authorization. Completing a
prerequisite never flips the flag implicitly.
