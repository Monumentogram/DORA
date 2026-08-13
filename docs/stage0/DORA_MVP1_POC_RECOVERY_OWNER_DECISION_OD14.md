# Dora MVP 1 — OD-14 recovery governance owner record

Decision ID: `OD-14`\
Status: **Approved owner constraints and prospective policy `REC-JSR305-EXCLUDE-001`; implementation and execution withheld**\
Date: **12 August 2026**\
Owner: **Project owner**\
Scope: governance/readiness and exact Stage 0 evaluation-package preparation for
`POC-RECOVERY-001` only\
Decision input governance HEAD: `eb312feb2a0d5e5b24b45fcd045bacca94e8c9da`\
Active Gate Set: `poc-recovery-stage0-v0.6`\
Active protocol: `poc-recovery-protocol-stage0-v0.6`

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
- Active governance protocol v0.6 incorporates the exact SHA-256-pinned v0.5/v0.4 amendments and, through them, the v0.3 contract; it retains the v0.2 owner selection of public AES-GCM-HKDF Streaming
  parameters (16-byte input key,
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
- The exact non-deprecated Keystore Builder path, 9/13/21 publication sequences,
  `final + ".tmp"` namespace/five-state reconciliation, WAL/FULL SQLite profile, deterministic
  `processingIntentId`, 12 candidate-specific barriers and 33-row fault matrix are normative in
  the inherited v0.3 base. Historical v0.4 adds durable `key-confirmation/run.kc` as the ninth family, an exact
  13-step alias/confirmation/SQLite bootstrap before every candidate publication, a fail-closed
  confirmation taxonomy and 12 mandatory confirmation/bootstrap fault rows, for 45 total. Active
  historical v0.5 replaced only the ambiguous taxonomy/reconciliation/fault-profile portions: it defines the
  exact ordered eight-class KEY taxonomy with plaintext validation only after successful decrypt,
  adds `KCF-07` for 46 total rows, and separates Phase A (184 injections) from the full physical
  D1/D2/D5 campaign (138 injections). A Phase A D2 repetition is reusable only when commit,
  protocol/Gate Set, fixture, injection, device identity/profile, fresh preflight and validity
  criteria all match; otherwise D2 repeats. Valid reuse leaves 92 D1/D5 injections. These
  remediations do not change the two candidates, thresholds, authority or owner product choice.
- Active v0.6 replaces only the effective inherited `KEY-04` oracle and materializes the single
  46-ID active matrix. The immutable v0.3 row remains historical and is not counted a second time.
  Effective `KEY-04` requires all stored confirmation identity and usable-alias prerequisites,
  controller replacement of the underlying alias key with another valid AEAD key while preserving
  ciphertext bytes and recorded identity, no recovery key creation/replacement, and only
  `Aead.decrypt(existingConfirmationCiphertext, exactAad)` authentication/AAD failure. Its sole
  classification is `KEY_UNAVAILABLE_KEY_MISMATCH`. Successful decrypt plus malformed/wrong
  plaintext remains `KCF-07` → `CORRUPT_KEY_CONFIRMATION`; pre-decrypt identity corruption,
  missing/invalidated/unusable alias and later structurally valid envelope auth failure retain their
  distinct classifications.
- GPT-5.6 Sol, OpenAI, acting only as AI documentary advisory reviewer, reviewed commit
  `eca48ba62acd79007884710395cc40ea21a02611` on 2026-08-12 with `formalReviewer=false` and
  disposition `CHANGES_REQUIRED`. `REC-REV-20260812-01` is
  `CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE`; `REC-REV-20260812-02` remains
  `OPEN_BLOCKING`. This review does not close `REC-RDY-02` or assign formal accountability.
- Per-coordinate checksum, OpenPGP and source-correspondence verification closes authenticity for
  all eight publisher-closure coordinates. The `jsr305:3.0.2` signed-POM/exact-source license
  conflict remains uninterpreted and the artifact is handled only by the prospective exclusion
  policy below.
- Exact immutable JetBrains annotations LICENSE and NOTICE bytes at commit
  `f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c` are verified and accepted only for this Stage 0
  governance packet. If that artifact enters a separately approved future graph, its NOTICE must
  be preserved in the future Stage 0 notices packet. This is not Production Legal, redistribution
  or dependency/production admission.
- Each candidate has 120 base hard-kill executions and requires at least 100 confirmed valid hard
  kills. Invalid attempts remain explicit and are never restarted or replaced silently.

## Owner / Stage 0 Product-IP disposition: `REC-JSR305-EXCLUDE-001`

For `POC-RECOVERY-001` Stage 0 evaluation only, the Project owner acting as Stage 0 Product/IP
reviewer approves prospective policy `REC-JSR305-EXCLUDE-001` and the reviewed governance package
for a future exact excluded Stage 0 graph. A future, separately scoped recovery harness may declare
only exact `com.google.crypto.tink:tink-android:1.23.0`, and only when all of these conditions are
met. This policy is bounded to the future `:poc:recovery` module and does not claim Tink or JSR-305
absent from the entire repository; existing other-module buildscript/AGP/UTP/lint/tooling/test
lockfile paths are outside this recovery boundary and are not recovery admission evidence:

1. the Tink dependency edge has the scoped exclusion of exact
   `com.google.code.findbugs:jsr305:3.0.2`;
2. every resolvable compile, runtime, unit-test, `androidTest`, benchmark and release configuration,
   every packaging/runtime-artifact input, and the dependency locks/verification metadata owned by
   future `:poc:recovery` resolve zero JSR-305 components and package zero JSR-305
   `javax.annotation` class definitions;
3. R8 uses only these three narrow rules:

   ```text
   -dontwarn javax.annotation.Nullable
   -dontwarn javax.annotation.concurrent.GuardedBy
   -dontwarn javax.annotation.concurrent.ThreadSafe
   ```

4. broader `dontwarn` rules are forbidden;
5. the exact implementation graph is separately verified and retained as evidence;
6. the release R8 build completes with no unresolved missing classes, and any
   `javax.lang.model.element.Modifier` condition from
   `com.google.errorprone:error_prone_annotations:2.41.0` is separately resolved and verified
   without a broad `dontwarn`; and
7. any reappearance of JSR-305 inside that exact recovery boundary fails closed and blocks both
   implementation verification and execution; unrelated pre-existing tooling occurrences alone do
   not.

This disposition treats the conflicting JSR-305 artifact as **excluded**. It does not decide
whether Apache-2.0 or BSD-3-Clause applies and does not approve use or distribution of JSR-305.
It approves only the prospective evaluation policy and reviewed governance package; it does not
add Tink or JSR-305 to a graph, admit Tink to production, authorize redistribution, satisfy
Production Legal or Production Security, replace a distinct accountable Engineering/Security
review, authorize a harness or implementation, or authorize device execution, a kill campaign,
a benchmark or any measured execution.

The prospective policy and exact governance authenticity/LICENSE/NOTICE evidence are closed. The
future actual graph/package/R8 verification and its scoped Product/IP disposition are separate,
open and blocking; approving use of the excluded JSR-305 artifact is neither required nor granted.

The resulting governance commit still requires a repeat read-only review at its exact 40-character
HEAD. A separate owner scope must authorize implementation. The future exact Gradle-resolved graph,
release R8 result, non-metric implementation verification, scoped Product/IP disposition of the
actual graph and fresh emulator/D2 SQLite, Keystore and filesystem preflight remain mandatory. A
later, separate owner record must authorize execution; D1/D5 remain required for a full physical
`PASS`.

## Normative package

- Proposed decision: `docs/stage0/DEC-044-POC-RECOVERY-EXPERIMENT.md`;
- exact Gate Set: `docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_6.md`;
- machine contracts: `docs/stage0/poc-recovery-gate-set-stage0-v0.6.json` and
  `docs/stage0/poc-recovery-protocol-stage0-v0.6.json`;
- superseded audit artifacts, prohibited for future execution:
  `DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_1.md`,
  `poc-recovery-gate-set-stage0-v0.1.json` and
  `poc-recovery-protocol-stage0-v0.1.json`, plus all corresponding v0.2, v0.3, v0.4 and v0.5 Gate
  Set/protocol files, whose 15 SHA-256 values are pinned by v0.6;
- exact evidence: `docs/evidence/poc-recovery-001/`; and
- fail-closed checker: `tools/check_poc_recovery_run_readiness.py`.

## Not approved

This record does not approve a winner/final format, final `ADR-AUDIO-001`, implementation
correctness, an actual Gradle/runtime dependency graph, a recovery module, production schema,
redistribution, JSR-305 use, Production Legal/Security, physical D1/D5 availability or execution.
The formal Engineering/Security reviewer, Production Legal reviewer, Production Security reviewer,
execution authorization and emulator/D2 runtime facts remain null. Both
`implementationAllowed=false` and `executionAllowed=false`; neither may change implicitly when a
prerequisite is completed.
