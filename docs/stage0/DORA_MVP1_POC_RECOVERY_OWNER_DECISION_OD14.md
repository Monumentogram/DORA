# Dora MVP 1 — OD-14 recovery governance owner record

Decision ID: `OD-14`\
Status: **Approved owner constraints and governance protocol v0.2 / Proposed experiment decision**\
Date: **12 August 2026**\
Owner: **Project owner**\
Scope: governance/readiness and exact Stage 0 evaluation-package preparation for
`POC-RECOVERY-001` only

This record is intentionally separate from the immutable `OD-01`–`OD-13` registry referenced by
the closed `POC-SEARCH-001` evidence ledger. It does not modify or re-hash that historical evidence.

## Recorded owner decision

- A narrow exploratory Phase A may in principle use the pinned emulator and available physical D2,
  but execution is prohibited until package review, independent recovery Engineering/Security
  review and a later separate owner authorization.
- Without D1 and D5, Phase A can return only `INCONCLUSIVE` or `FAIL`; `PASS` is prohibited. D1/D5
  procurement is not required now. A full physical verdict requires D1/D2/D5 and is deferred.
- The approved safety gate is not weakened: tail loss is at most 5.000 seconds on every valid hard
  kill, and committed-byte loss is always exactly zero.
- One future common harness compares exactly two candidates: Tink Streaming AEAD through public API
  and sealed Tink AEAD microfiles with an authenticated manifest.
- Five seconds is the only gate-compatible microfile cadence. Fifteen/30 seconds are observations
  or post-FAIL fallbacks and cannot receive PASS under the current gate.
- The exact package candidate is `com.google.crypto.tink:tink-android:1.23.0`. Permission covers
  Stage 0 evaluation-package preparation only, not dependency admission or runtime-graph wiring.
- Governance protocol v0.2 selects public AES-GCM-HKDF Streaming parameters (16-byte input key,
  one 16-byte HKDF-SHA256-derived AES-GCM key per stream and 4096-byte ciphertext segments) with
  `DURABLE_ONE_SEGMENT_LOOKAHEAD`. The design is
  `DESIGN_SELECTED_IMPLEMENTATION_VERIFICATION_REQUIRED`; deprecated
  `StreamingAeadKeyTemplates` is forbidden.
- The microfile candidate selects `AES256_GCM_TINK_IV12_TAG16`, one fresh keyset per microfile and
  exact binary big-endian authenticated manifest `DORA_RECOVERY_MANIFEST_V1_BINARY_BE`.
- The Project owner is Stage 0 Product/IP reviewer. Production Legal is unassigned. An independent
  recovery Engineering/Security reviewer must separately verify the selected construction and
  recovery protocol; that review does not replace Production Security. No formal independence is
  claimed for the current Codex remediation; a distinct accountable reviewer is mandatory before
  execution.
- The protocol requires exact stream/microfile/manifest/checkpoint/key-envelope AAD, Android
  Keystore alias and encrypted-keyset rules, no key replacement, no keys in Git/logs and the full
  key-loss/mismatch/envelope fault matrix. It does not claim a different derived AES key per
  streaming segment.
- A PoC-local platform SQLite journal is allowed only for DB/file split-brain tests. Room,
  SQLCipher, WorkManager and production schema are forbidden. The Stage 0 provenance policy is
  extended only for recovery scope and creates no production admission.
- Semantic commit is the successful return of SQLite `endTransaction()` after durable file and
  authenticated publication. The controller event follows commit and is evidence only. The
  external Phase A ledger is the rollback anchor; without it only crash/split-brain rollback
  detection may be claimed.
- The exact file/directory durability, WAL/FULL SQLite profile, deterministic
  `processingIntentId`, 12 candidate-specific barriers and expanded fault matrix are normative in
  v0.2.
- Each candidate has 120 base hard-kill executions and requires at least 100 confirmed valid hard
  kills. Invalid attempts remain explicit and are never restarted or replaced silently.

## Normative package

- Proposed decision: `docs/stage0/DEC-044-POC-RECOVERY-EXPERIMENT.md`;
- exact Gate Set: `docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_2.md`;
- machine contracts: `docs/stage0/poc-recovery-gate-set-stage0-v0.2.json` and
  `docs/stage0/poc-recovery-protocol-stage0-v0.2.json`;
- superseded audit artifacts, prohibited for future execution:
  `DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_1.md`,
  `poc-recovery-gate-set-stage0-v0.1.json` and
  `poc-recovery-protocol-stage0-v0.1.json`;
- exact evidence: `docs/evidence/poc-recovery-001/`; and
- fail-closed checker: `tools/check_poc_recovery_run_readiness.py`.

## Not approved

This record does not approve a winner/final format, final `ADR-AUDIO-001`, implementation
correctness, Gradle/runtime dependency, recovery module, production schema, redistribution,
Product/IP final disposition, Production Legal/Security, physical D1/D5 availability or execution.
`approvedReviewer`, `approvedOn`, execution authorization and emulator/D2 runtime facts remain
null. `executionAllowed=false` and cannot change implicitly when a prerequisite is completed.
