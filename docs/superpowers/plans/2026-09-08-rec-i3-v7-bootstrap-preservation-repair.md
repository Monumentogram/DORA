# REC-I3 V7 Bootstrap and Preservation Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce one reviewed, CI-green accepted revision and a versioned V7 ASUS handoff that repairs the missing bootstrap fixture prerequisite, exposes safe first-insert diagnostics, and preserves evidence and cleanup observations even when long-path copying fails.

**Architecture:** The Android preflight will create its synthetic parent through `AndroidRecoveryKeyBootstrap` before calling the existing streaming journal; no storage API or schema behavior changes. Real SQLite schema behavior is checked on the host and actual Android adapter behavior is covered by instrumentation tests explicitly pending device execution. A standalone PowerShell preservation tool stages to a short root through `robocopy`, records original-path/hash mappings, and performs package/emulator cleanup from `finally`.

**Tech Stack:** Kotlin, Android instrumentation, Android SQLite/Keystore adapters, Python 3 host verification, PowerShell 7/Windows PowerShell 5.1 compatibility, `robocopy`, Gradle/JVM 17, GitHub Actions.

**Spec:** `docs/adr/ADR-0003-unified-poc-recovery-journal-and-run-lease.md`, `docs/adr/ADR-0005-poc-recovery-streaming-persistence-and-range-quarantine.md`, `docs/DORA_MVP1_TEST_STRATEGY.md`, and the owner-scoped V6 diagnosis supplied on 2026-09-08.

## Global Constraints

- Stage 0D / `POC-RECOVERY-001` / REC-I3 only; deterministic synthetic data only.
- Preserve foreign keys, WAL/FULL configuration, schema v4, exact candidate identity `REC-STREAM-TINK`, and the accepted controller success and negative assertions.
- Do not add automatic bootstrap-parent creation to the streaming journal or any production application edge.
- Android instrumentation execution remains pending until the new immutable accepted revision and local ASUS prompt are ready; no SSH, Tailscale, remote ASUS setup, or remote ASUS test is allowed.
- Package cleanup remains `UNVERIFIED` unless direct post-uninstall package observations prove absence; process absence cannot substitute.
- Fault injection, hard-kill/Phase A, measured work, physical-device work, production admission, and v6 mutation remain out of scope.

---

### Task 1: Freeze and verify the V6 diagnosis

**Files:**
- Read: Drive `DIAGNOSIS.md`, `REPORT.md`, and diagnosis evidence ZIP
- Read: immutable Git commit `c473a6f3877f60a1c1686e676606affd4fc66334`

**Interfaces:**
- Consumes: V6 attempt evidence and accepted source/tree identities.
- Produces: verified causal boundary and immutable repair baseline.

- [x] **Step 1: Fetch the two reports through the connected Google Drive account.**
- [x] **Step 2: Download the ZIP and verify SHA-256 `4834442220211379eb3ea551f8a1e0e90aa9d3fe90867308c9121cb3f8c0cb31`.**
- [x] **Step 3: Verify all 325 `EVIDENCE_MANIFEST.sha256` entries and compare the recorded source identity with immutable `c473a6f`.**
- [x] **Step 4: Confirm the claim boundary: missing bootstrap parent is source/schema-causal; exact caught SQLite exception and concrete runtime result remain unobserved.**

### Task 2: Add real schema and Android-adapter regression coverage

**Files:**
- Modify: `tools/verify_rec_i3_streaming_sqlite.py`
- Modify: `tools/test_poc_recovery_i3_governance.py`
- Create: `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCheckpointForeignKeyInstrumentedTest.kt`
- Create: `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCheckpointAndroidTestFixture.kt`

**Interfaces:**
- Consumes: exact schema-v4 DDL, `AndroidRecoveryStreamingJournal`, and production bootstrap controller.
- Produces: host SQLite orphan/parent evidence plus Android tests for `Retry(JOURNAL_OPERATIONAL)` on an orphan and `CheckpointReceipt` after production bootstrap.

- [x] **Step 1: Write a governance test requiring explicit host SQLite evidence for orphan rejection and bootstrap-parent acceptance.**
- [x] **Step 2: Run the focused Python test and verify it fails because the evidence is absent.**
- [x] **Step 3: Extend the existing schema verifier minimally to attempt the orphan insert, assert the foreign-key rejection, insert the exact synthetic bootstrap parent, and accept the same checkpoint.**
- [x] **Step 4: Run the focused Python test and schema verifier to verify the new evidence passes.**
- [x] **Step 5: Write the Android instrumentation tests first against a wished-for test fixture; compile and capture the expected missing-helper failure.**
- [x] **Step 6: Implement the test-only fixture through `AndroidRecoveryKeyBootstrap`, with deterministic same-run/candidate confirmation and child-before-parent cleanup including Keystore alias and files.**
- [ ] **Step 7: Compile the Android instrumentation sources. Record actual Android execution as pending rather than claiming host coverage proves adapter/FK behavior.**

### Task 3: Repair the V7 preflight scenario and diagnostics

**Files:**
- Modify: `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryE36GapiPreflightInstrumentedTest.kt`
- Reuse: `RecoveryCheckpointAndroidTestFixture.kt`

**Interfaces:**
- Consumes: production bootstrap fixture and the existing single-test preflight contract.
- Produces: bootstrap-before-checkpoint composition, safe pre-assert classification, and exact cleanup counts including the parent.

- [x] **Step 1: Add source/governance assertions that require bootstrap before checkpoint, safe diagnostic emission before the receipt assertion, and parent cleanup after all children.**
- [x] **Step 2: Run the focused test and verify it fails on immutable `c473a6f`.**
- [x] **Step 3: Update the one preflight scenario to bootstrap first, record committed bootstrap evidence, classify the first journal result before assertion, preserve all existing success/replay/identity-denial assertions, and delete range → outcome → checkpoint → bootstrap before alias/file cleanup.**
- [ ] **Step 4: Run focused tests and compile the instrumentation source. Verify status expectations change only by the justified bootstrap delete/evidence fields.**

### Task 4: Build and regression-test long-path preservation with independent cleanup

**Files:**
- Create: `tools/rec_i3_preserve_and_cleanup.ps1`
- Create: `tools/test_rec_i3_preserve_and_cleanup.py`

**Interfaces:**
- Consumes: two Gradle artifact roots, a deliberately short staging root, ADB path/serial/packages, and optional injected copy failure.
- Produces: byte-verified staged artifacts, original-path mapping, cleanup observation JSON, and truthful nonzero result on copy or cleanup failure.

- [x] **Step 1: Write tests that construct source and destination paths of at least 310 characters and assert successful byte/hash preservation.**
- [x] **Step 2: Write an injected-copy-failure test that still requires the fake ADB cleanup and post-uninstall observations to run from `finally`.**
- [x] **Step 3: Run both tests and verify they fail because the preservation tool is absent.**
- [x] **Step 4: Implement short-root validation, `robocopy` exit-code handling (0–7 success), complete file enumeration, SHA-256/original-path mapping, and cleanup observation in `finally`.**
- [x] **Step 5: Run both tests and verify the ≥310-character success case and injected failure case pass with truthful outcomes.**

### Task 5: Validate, review, integrate, and prepare V7

**Files:**
- Modify only if evidence changes truth: `docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md`, `docs/DORA_MVP1_STAGE_STATUS.md`
- Create outside Git: versioned V7 folder with `00_START_HERE`, `01_RUN_FILES`, `02_REPORTS_AND_REVIEWS`, `03_EVIDENCE`, `04_CHECKSUMS`

**Interfaces:**
- Consumes: one immutable combined candidate, CI results, independent review, and exact protected-main squash identity.
- Produces: accepted V7 packet, complete ZIP/manifest, English `ASUS_LOCAL_PROMPT.md`, verified Drive folder, and no remote execution.

- [ ] **Step 1: Run focused Python, Kotlin, schema, formatting, static-analysis, lint, assembly, native/alignment, and repository governance checks required by the test strategy.**
- [ ] **Step 2: Commit the complete candidate and request an independent Sol HIGH review of the same immutable commit; resolve all P0/P1/P2 findings through a new immutable candidate and rereview.**
- [ ] **Step 3: Push the dedicated branch, open a PR, wait for exact-head CI, and merge only after review and required checks under the standing technical delegation.**
- [ ] **Step 4: Verify protected-main tree equality and post-main CI before pinning V7.**
- [ ] **Step 5: Generate V7 with exact accepted commit/tree/runtime/test/tool hashes, review and CI reports, complete manifest, ZIP, and an English prompt authorizing only the newly scoped non-measured local ASUS preflight.**
- [ ] **Step 6: Upload the full new versioned folder under Drive `DORA / Android Testing / REC-I3`, preserve V6 unchanged, and verify Drive readback, sizes, checksums, and pins.**
- [ ] **Step 7: Send the Governor the folder URL, prompt URL/text, ZIP SHA-256, accepted commit, CI/review outcomes, and exact local ASUS execution boundary.**
