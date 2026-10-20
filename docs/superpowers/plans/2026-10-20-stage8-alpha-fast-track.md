# Stage8 limited Alpha implementation plan

> Execute inline with superpowers:executing-plans. Use one independent whole-branch
> review after verification. The owner explicitly authorized autonomous execution,
> commit/push and a separate stacked Draft PR.

**Goal:** Safe five-minute recording software and honest Stage9 dependency admission.
**Architecture:** Reuse the control/persistence split; fence capture admission,
session input and authenticated writer independently. Preserve all storage contracts.
**Spec:** ../../adr/ADR-RECORDING-011-limited-alpha-recording.md
**Baseline:** 6f5e87317b163e4567ffa76da3cd3191bcfac826.

## Global constraints

MAX_ALPHA_FRAMES=4_800_000; PCM16/16kHz/mono; outstanding frames=256_000.
No physical POCO, no benchmark campaigns, no Cloud/ASR implementation, no merge.
Physical smoke and security restoration remain separate gates.

## Review focus

- Final block enters during Pause/Stop: no extra PCM and one finalization.
- Writer completes while native shutdown fails: never SAVED.
- Recovery prefix at/above cap: no new microphone or mutations of old audio.
- Direct scoped writer bypass: contiguous cumulative bound, including uncertainty.
- New recording after prior terminal state: fresh allowance, preserved old source.

## Tasks

- [ ] Add failing boundary/session/admission/writer tests; observe RED.
- [ ] Implement fixed frame budget, boundary truncation, safe automatic stop and UI.
- [ ] Verify appropriate JVM regressions; record exact long-test deferrals.
- [ ] Add API28/API36 real encrypted cap/reopen/recovery tests and bounded runner.
- [ ] Run appropriate existing crypto/Recovery/process-death regressions.
- [ ] Add versioned acceptance contract, matrix, Stage9 disposition and release gates.
- [ ] Audit scope/privacy, independent review, fix findings, commit/push Draft PR.
- [ ] Verify final remote SHA/CI and publish truthful software/physical disposition.

## Execution ledger

2026-10-20: local and remote baseline match, tree clean, PR99 Draft/Open/Unmerged.
Existing linked worktree reused on new `codex/stage8-alpha-fast-track` branch.
Owner's autonomous instruction supersedes additional process approval checkpoints.

2026-10-20 CI attempt 39444133205: software 1,067/1,067 passed on Linux,
but the inherited mixed segmentation fallback exceeded the new cap. Corrected
only the limited inventory: preserve all old source and move its four short
phases to explicit host-kill verification; three long phases remain deferred.
