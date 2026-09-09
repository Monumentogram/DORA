# REC-I3 V8 Host Run Contract Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the contradictory V7 host-run contract with a reviewed V8 contract that can validate an immutable detached revision without weakening REC-I3 governance, consumes at most one instrumentation attempt durably, binds all device operations to one serial, preserves evidence without overwriting earlier staging, and reports cleanup truthfully.

**Architecture:** Add one repository-owned PowerShell runner whose accepted commit and tree are explicit mandatory inputs and which validates, but never fetches or switches, the checkout it builds. Add a V8 governance profile rooted at accepted V7 main `55940df0c95e919a00708ae57e1b8aa23d89b6de`; the profile accepts the exact V8 delta on its dedicated branch, integrated `main`, or a local detached checkout, while retaining exact-path, clean-tree, production-source, and CI identity checks. Strengthen the existing preservation helper so every run has a unique staging directory and hashes the complete source→stage→evidence mapping; keep reporting and cleanup independent through fail-closed observations.

**Tech Stack:** PowerShell 5.1/7, Python 3 `unittest`, Git, Android SDK/ADB, Gradle/JVM 17.

**Spec:** `ASUS_DEVELOPMENT_HANDOFF.md` in Drive folder `1908rDUhDOiKjtmuyqMx_DsL0eBZ0w53c`, `AGENTS.md`, `docs/DORA_MVP1_TEST_STRATEGY.md`, and preserved V7 packet/report evidence under `<workspace>\REC-I3-V7-PACKET` and `DORA-REC-I3-V7-PREPARATION-BLOCKED-20260908T192555Z-REPORT.md`.

## Global Constraints

- Stage 0D / `POC-RECOVERY-001` / REC-I3 only; deterministic synthetic data only.
- V6 remains immutable historical FAIL with ZIP SHA-256 `4834442220211379eb3ea551f8a1e0e90aa9d3fe90867308c9121cb3f8c0cb31`.
- V7 remains immutable `PREPARATION_BLOCKED`, `instrumentationAttemptCount=0`, and `PACKAGE_CLEANUP_UNVERIFIED`; never overwrite or relabel it.
- Never weaken Android assertions, governance exact-path/clean-tree checks, production-source protection, or the one-test selector to obtain PASS.
- The tested checkout is immutable after creation: the runner must not fetch, checkout, reset, clean, edit, or otherwise switch it.
- Only the root coordinator may own emulator and instrumentation execution; the implementation agent must run host regressions only.
- Every ADB operation and the Gradle connected-test route must be bound to the explicit serial.
- Retain the complete V7 preparation/test/evidence contract: exact E36-GAPI fingerprint, API 36, x86_64 ABI and AVD identity; governance and online dependency verification; online then offline target/test APK assembly; exact single-method selector and both runner arguments; offline/no-daemon/no-configuration-cache connected execution; APK digests, first checkpoint diagnostic, logcat and complete recovery build-tree preservation.
- Every external command has a finite timeout. A timeout consumes the one-attempt ledger conservatively and cleanup still runs; an owned emulator process is terminated on boot/preflight failure.
- A run lock is created atomically immediately before Gradle connected-test process launch; once created, a later invocation must fail closed without launching instrumentation.
- Evidence and observations are written before optional reporting copies; cleanup executes from `finally` and remains attempted when preparation, execution, preservation, or reporting fails.
- Prior staging is never recursively deleted or overwritten. Each attempt has a caller-supplied safe identifier and a unique staging directory retained for audit.
- `copySucceeded=true` requires identical complete relative-path sets, byte sizes, and SHA-256 digests at source, staging, and evidence.
- Package absence requires a healthy serial-bound ADB transport plus direct package-manager observations; an empty failing `pm path` alone is never sufficient.
- No SSH, Tailscale, remote orchestration, Windows virtualization changes, credentials, global Git/config changes, physical-device work, measured campaign, Phase A, hard-kill campaign, production admission, or merge.

---

### Task 1: Implement and test the V8 host-run contract

**Files:**
- Create: `tools/run_rec_i3_v8.ps1`
- Create: `tools/test_run_rec_i3_v8.py`
- Modify: `tools/rec_i3_preserve_and_cleanup.ps1`
- Modify: `tools/test_rec_i3_preserve_and_cleanup.py`
- Modify: `tools/validate_poc_recovery_governance.py`
- Modify: `tools/test_poc_recovery_i3_governance.py`
- Modify: `tools/verify_poc_recovery_dependency_inventory.py`
- Modify completion record and exact-path clarification: `docs/superpowers/plans/2026-09-09-rec-i3-v8-host-run-contract-repair.md`

**Interfaces:**
- `tools/run_rec_i3_v8.ps1` consumes mandatory `Repository`, `EvidenceBase`, `StagingRoot`, `Serial`, `AcceptedCommit`, and `AcceptedTree`; optional `AvdName` defaults to `dora_api36_recovery`.
- `tools/rec_i3_preserve_and_cleanup.ps1` additionally consumes mandatory safe `AttemptId` and `Serial`; it emits preservation manifest schema `DORA_REC_I3_PRESERVATION_V2` and cleanup observation schema `DORA_REC_I3_CLEANUP_OBSERVATION_V2`.
- Governance exposes `REC_I3_V8_BRANCH`, `REC_I3_V8_BASE`, `REC_I3_V8_PATHS`, `rec_i3_v8_source_candidate`, `rec_i3_v8_candidate`, `validate_rec_i3_v8`, and a V8 self-test fast path before V7/legacy dispatch.
- The runner emits timestamped raw and preserved directories, a cleanup observation outside the source tree, and one commit-specific attempt ledger under `EvidenceBase`.

- [x] **Step 1: Write failing preservation and cleanup regressions.**

  Add executable tests that require: a serial prefix on every fake ADB call; a unique `AttemptId` staging directory; refusal on a reused staging identifier without deleting its sentinel; source, staged, and evidence hashes and byte sizes in every manifest record; exact relative-path-set equality; cleanup after injected source/stage/evidence/report failures; and classification that distinguishes healthy-transport package absence from transport or permission failure. Name the production change each test protects in a comment before the assertion.

- [x] **Step 2: Run the preservation regressions and capture RED.**

  Run:

  ```powershell
  & '<python-executable>' tools\test_rec_i3_preserve_and_cleanup.py -v
  ```

  Expected: new tests fail because V1 has no `AttemptId`/`Serial`, deletes the constant staging directory, omits source hashes, and treats ADB results without an independent transport/package listing proof.

- [x] **Step 3: Implement the minimal V2 preservation/cleanup behavior and make the focused tests green.**

  Do not call `Remove-Item` on an existing attempt directory. Validate `AttemptId` with a conservative allowlist such as `^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$`; create `<StagingRoot>\rec-i3-preservation-<AttemptId>` only when absent. Prefix every ADB call with `-s,$Serial`. Record transport probe, `pm path`, and exact `pm list packages` observations; mark absence only when transport and package-list commands succeed, the list lacks the exact package, and `pm path` is empty with only the platform-observed absent exit forms accepted. Preserve all raw exit codes/output and keep ambiguous cases `PACKAGE_CLEANUP_UNVERIFIED`.

- [x] **Step 4: Write failing runner regressions.**

  Add host tests using a temporary fake repository/toolchain and fake executables. They must prove the runner: never contains or invokes `git fetch`, `git checkout`, `git reset`, or `git clean`; rejects wrong commit/tree/dirty state before instrumentation; captures every preflight command exit code; binds the serial through `ANDROID_SERIAL`, the AGP connected-test `--serial` option, logcat, package checks, preservation, uninstall, and emulator kill; writes command output incrementally; atomically creates the commit-specific attempt ledger before fake connected Gradle starts; blocks a second invocation when that ledger exists; leaves a conservative consumed/unknown state if the fake Gradle is interrupted or times out; and still invokes preservation/cleanup when metadata/reporting fails. Tests must never start a real emulator or Gradle.

- [x] **Step 5: Run the runner regressions and capture RED.**

  Run:

  ```powershell
  & '<python-executable>' tools\test_run_rec_i3_v8.py -v
  ```

  Expected: FAIL because `tools/run_rec_i3_v8.ps1` does not exist.

- [x] **Step 6: Implement the minimal repository-owned runner and make its tests green.**

  Validate exact HEAD/tree/cleanliness without switching the checkout. Start the named AVD hidden only when the exact serial is absent, record emulator stdout/stderr, wait on boot/device predicates with a bounded condition loop, and reject any extra online device. Create raw evidence before preparation. Use a streaming logged-command helper that appends each line before returning. Atomically create the ledger with `FileMode.CreateNew` immediately before connected Gradle launch and update it after completion without erasing the conservative launch record. Run preservation/cleanup first in `finally`; write any reporting-copy failure separately without suppressing cleanup outcomes.

- [x] **Step 7: Write failing V8 governance regressions, including detached identity.**

  Build temporary Git fixtures that assert the exact V8 delta is recognized on `codex/rec-i3-v8-host-runner-fix`, integrated `main`, and a local detached checkout, while unrelated branches, dirty trees, extra/missing paths, changed production Recovery source, GitHub detached context, and unpinned ancestry fail closed. Add source checks requiring V2 manifest/cleanup schemas, no staging deletion, serial binding, durable attempt ledger creation before connected Gradle invocation, and runner absence of checkout mutation commands.

- [x] **Step 8: Run the focused governance regressions and capture RED.**

  Run:

  ```powershell
  & '<python-executable>' -m unittest tools.test_poc_recovery_i3_governance.RecoveryI3GovernanceTests.test_v8_detached_host_run_contract tools.test_poc_recovery_i3_governance.RecoveryI3GovernanceTests.test_v8_host_tools_execute_regressions -v
  ```

  Expected: FAIL because no V8 candidate/profile exists.

- [x] **Step 9: Implement the exact V8 governance profile and make focused tests green.**

  Root the profile at `55940df0c95e919a00708ae57e1b8aa23d89b6de`. Local detached acceptance is allowed only when there is no GitHub event/PR context and `rec_i3_v8_source_candidate(lifecycle.head)` proves the exact V8 committed path set; integrated/main and feature-branch paths retain their corresponding identity checks. Preserve the V7 fast path and historical profiles unchanged.

- [ ] **Step 10: Run the complete host regression set.**

  Run:

  ```powershell
  & '<python-executable>' tools\test_rec_i3_preserve_and_cleanup.py -v
  & '<python-executable>' tools\test_run_rec_i3_v8.py -v
  & '<python-executable>' tools\validate_poc_recovery_governance.py --self-test
  & '<python-executable>' -m py_compile tools\validate_poc_recovery_governance.py tools\test_poc_recovery_i3_governance.py tools\test_rec_i3_preserve_and_cleanup.py tools\test_run_rec_i3_v8.py
  git diff --check
  ```

  Expected: all tests and validation pass with no warnings; governance reports the V8 profile and leaves V7 history unchanged.

- [ ] **Step 11: Commit and report.**

  The cumulative V8 delta from `55940df0c95e919a00708ae57e1b8aa23d89b6de` must contain exactly the eight listed regular `100644` files, including this plan. This corrects the stale seven-file wording according to the recorded exact-path ruling. Commit only the paths assigned to each implementer on `codex/rec-i3-v8-host-runner-fix`. Write the implementation report to the task report path supplied by the coordinator, including RED and GREEN commands/outcomes, exact commit(s), files changed, self-review, and concerns. Do not run emulator/instrumentation, push, merge, package, upload, or modify V6/V7 evidence.

**Verified implementation checkpoints:** Task 1a's runner/helper and host regressions were reviewed through `a724776d9124282bf37b2233d80625f1aa977ca5`. Task 1c's preflight permission-stderr predicate and covering regression were reviewed at `086bcf5c58fa905f0feb97d708f6aab0ef38b933`; both stderr-aware cleanup and per-attempt fallback records are retained. Task 1b recorded eight expected RED failures for absent V8 candidate/validation APIs before implementation. These host-tool checkpoints do not complete independent whole-candidate review or Android acceptance; V6 remains FAIL, V7 remains PREPARATION_BLOCKED with zero instrumentation attempts and cleanup UNVERIFIED, `0D.6` remains OPEN, and `POC-RECOVERY-001` remains BLOCKED / NOT_READY.

### Task 2: Independently review and freeze the immutable candidate

**Files:**
- Read: Task 1 brief, implementation report, and base-to-head review package.
- Create outside Git: independent review and risk assessment in the local V8 evidence workspace.

**Interfaces:**
- Consumes the complete immutable Task 1 commit range.
- Produces an ACCEPTED/REVISE verdict with P0/P1/P2/P3 findings and exact reviewed patch SHA-256.

- [ ] **Step 1: Verify Task 1 commit range and run fresh focused and full host checks.**
- [ ] **Step 2: Review exact governance strength, runner lifecycle, serial ownership, attempt durability, preservation equivalence, cleanup truth, and V6/V7 immutability.**
- [ ] **Step 3: Resolve all P0/P1/P2 findings through the implementation agent and scoped rereview; freeze one reviewed commit.**

### Task 3: Execute one bounded local acceptance and preserve the result

**Files:**
- Create outside Git: a detached acceptance worktree, timestamped V8 raw/preserved evidence, attempt ledger, cleanup observation, final report, manifest, packet, and checksums.
- Preserve unchanged: every V6 and V7 local/Drive artifact.

**Interfaces:**
- Consumes the immutable reviewed V8 commit/tree and repository-owned runner.
- Produces exactly one final V8 acceptance outcome with truthful instrumentation and cleanup classifications.

- [ ] **Step 1: Create a new detached acceptance worktree at the reviewed commit; verify exact HEAD/tree and clean state, then make no source changes there.**
- [ ] **Step 2: Verify SDK/JVM/AVD/tool versions, V8 governance, dependencies, and package absence without launching instrumentation.**
- [ ] **Step 3: Execute the repository-owned V8 runner once. Do not retry after the durable attempt ledger is created.**
- [ ] **Step 4: Preserve Gradle/UTP/logcat/APK hashes, first checkpoint diagnostic, attempt ledger, package/emulator cleanup observations, and every command/exit code.**
- [ ] **Step 5: Run final manifest/checksum/readback verification and independent Astra HIGH final review of the immutable revision plus local evidence.**
- [ ] **Step 6: Upload the accepted V8 handoff and final evidence under the existing Drive REC-I3 hierarchy; verify Drive names, sizes, and checksums. Leave `0D.6` open unless the evidence and governance explicitly authorize a different state.**
