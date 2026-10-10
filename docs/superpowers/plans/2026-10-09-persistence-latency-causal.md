# Stage 8.6C.2 persistence latency investigation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for benchmark implementation; independent statistics, architecture and protection analysis use superpowers:dispatching-parallel-agents.

**Goal:** Establish what causes persistence scaling and whether it explains LONG-02, without contacting POCO or weakening durability.

**Architecture:** Analyze immutable observations independently from a real encrypted synthetic vault on isolated Android emulators. Test-only decorators measure production boundaries without replacing their behavior. Admit a product fix only if the causal gate is met.

**Tech Stack:** Kotlin instrumentation/JUnit, real Room/SQLCipher/Android Keystore/Recovery adapters, Python host analysis, API28/API36 emulators.

**Spec:** Owner attachment `Вставленный текст.txt`, task Stage 8.6C.2, received 2026-10-09 (private attachment retained outside Git).

## Global constraints

- No physical POCO access, startup, mutation, audio readback or new physical campaign.
- Preserve historical evidence and 256000-frame admission; no PCM loss or weaker encryption/commit semantics.
- Existing isolated worktree and branch `stage/8.6-poco-recording-acceptance`; no main changes or PR merge.
- Synthetic sizes 10, 100, 400, 1000, 2000; host/emulator timing is not POCO performance.
- Root cause remains NOT_PROVEN if strict causal criteria are not met. Architectural changes require Owner Decision.

## Review focus

- Failed/partial benchmark must remain visible, never relabelled as a complete run.
- Nested timers and boundary counts are not total I/O or syscall counts.
- Catalog metadata alone is not PCM integrity or independent backup proof.
- Test execution must explicitly target a verified emulator, never default ADB selection.
- No private identities, policy pins, keys, audio or source inventories in Git/CI.

### Task 1: Historical analysis and architecture

- [x] Add tested ordinal/window/correlation analysis and content-free results for all 393 observed spans.
- [x] Trace complete production persistence path, complexity, transactions, file scans and concurrency; record missing evidence.
- [x] Prepare exact-bound private custody verification and public fail-closed protection/backup plan.

### Task 2: Real synthetic benchmark

**Files:** new `android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/diagnostics/PersistenceScalingBenchmarkTest.kt` and test-only helpers as needed; diagnostics stay outside the frozen persistence runtime-test package.
**Interfaces:** existing `EncryptedAudioVault.Dependencies`, `PersistenceLatency.collect`, writer append/segmentation and real platform wrappers; JSON rows with counts and timings, no source IDs.

- [x] Compile opt-in benchmark with an explicit emulator-only guard and synthetic isolated vault.
- [x] Populate real encrypted/durable blocks; measure append and metadata at all five sizes, SQL/catalog boundary counts, CPU/memory, DB/WAL/file sizes and synchronous outstanding-frame semantics.
- [x] Record full raw output, exact APK/source identity and fail-closed partial-run outcomes. Full API36 run completed 2,003 blocks and 160,240,000 authenticated frames; full-run and final-source APK identities are explicitly distinct.
- [x] Use controlled same-state catalog load repetition and cold/warm observations; retain contracts. These controls do not fully separate size from time/global co-growth, which remains an explicit limitation and next experimental task.

### Task 3: Causal gate and verification

- [x] Compare benchmark mechanism with LONG-02; distinguish proven scaling from unproven terminal stall.
- [x] If product root cause PROVEN and fix contract-preserving: failing regression first, minimal fix, normal/slow writer tests and API28/API36 checks. Otherwise no speculative runtime fix. Decision: NOT_PROVEN; runtime fix branch not taken.
- [x] Run applicable host tests, instrumentation compilation, formatting/static checks and privacy audit; independent review of all new artifacts. Final API28/API36 async tests and bounded smoke pass; exact-source publication checks follow sealing.
- [ ] Commit scoped changes, publish to existing draft PR, inspect exact-SHA CI and preserve real status.
- [x] Report benchmark table, root-cause decision, protection plan, remaining blockers and next concrete task in the result artifact.

## Execution ledger

- Baseline: `944b41532e3007ca14d1851d83c2232de1132874`, clean linked worktree; previous backpressure tests 7/7.
- Owner authorizes autonomous engineering and conditional fix/CI. No extra approval gate for the diagnostic implementation.
- Separate agents own new statistics, architecture and protection files; benchmark owner edits only new test files. No shared runtime edits.
- Independent reviews found and closed scalar-output/privacy and output-alias preservation defects, tightened the emulator hardware guard, and added failure-receipt cleanup coverage. Runner APK identity remains a caller-supplied verified input; the documented runner does not inspect its manifest.
- The inherited publication validator freezes exact path sets. A separately tested additive manifest admits only diagnostic files, preserves the complete parent tree and exact reversible validator hook, and keeps all previous seals/CI/runtime checks.
- Original root task checklist items concerning commit and CI complete only in the post-commit task receipt. This sealed source plan intentionally cannot contain its own final publication SHA.
