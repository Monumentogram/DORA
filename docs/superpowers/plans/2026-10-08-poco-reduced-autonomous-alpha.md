# POCO reduced autonomous Alpha implementation plan

> For agentic workers: use superpowers:executing-plans inline under the owner's explicit autonomy instruction.

**Goal:** execute the approved 60-cycle plus one-hour campaign with independent safe-state supervision.
**Architecture:** isolated host orchestrator and watchdog supervise the existing real product-control driver.
No new production recorder, authentication bypass or battery instrumentation.
**Tech stack:** Python/ADB, Java instrumentation, existing Kotlin recording/storage/VAD.
**Spec:** owner Stage 8.6B-LITE attachment; ADR-RECORDING-006 records the authoritative scope.

## Global constraints

60/60 real five-second cycles, fixed denominator; one >=3,600-second screen-off run.
Preserve owner46, historical failures, all runtime/security gates and 4/4 exact-SHA CI.
No 200/three-hour PASS, battery tests, Sheet closure, merge/rebase or future stages.
Only standard nonsecure keyguard dismissal; owner-required action stops the autonomous run.

## Review focus

- Lost host/ADB heartbeat must stop new work without claiming unobserved microphone release.
- A watchdog cannot stop or delete a pre-existing owner recording.
- Screen wake/readback authentication must complete autonomously before admitting an hour.
- Failure receipts and unverified durable prefixes survive retries and process death.
- Reduced samples must never be reported as historical full acceptance.

### 1. Scope and preflight

- [x] Verify exact local/remote starting SHA; record inherited dirty work instead of discarding it.
- [x] Preserve failed screen-smoke, authenticate/read/delete its exact test identity; owner46 unchanged.
- [ ] Add narrowly sealed repository admission for storage budget and additive LITE tooling/governance.

### 2. Orchestrator and supervision

Files: `tools/poco_alpha_acceptance.py`, `tools/poco_non_battery/alpha_protocol.py`,
`tools/poco_non_battery/test_alpha_protocol.py`, existing isolated driver.

- [ ] Write failing tests for 60/60 denominator, sealed protocol, secure-keyguard refusal,
  expired heartbeat, missing ADB, resource/thermal failure and ownership fences.
- [ ] Implement one reproducible sequential orchestrator with private target configuration,
  exact APK/source pins, append-only attempt ledger, bounded waits and content-free receipts.
- [ ] Add independent host watchdog/awake lease and device-owned recording deadline/abort watcher.
- [ ] Verify negative tests and diagnostic screen-off/readback before accepted measurements.

### 3. Functional checks and sealed source

- [ ] Add bounded notification Pause/Resume/Stop, Doze, Saver, permission-denial recovery and
  process-kill/explicit-Resume modes using real controls; no fake PCM or state assignment.
- [ ] Preserve full readback before exact test-only deletion; verify original46 again.
- [ ] Freeze protocol/source/APK identities, review and commit/push scoped changes; no merge.

### 4. Campaign and terminal verification

- [ ] Execute once: 60 five-second cycles, DORA-LONG-01 and remaining bounded functionals.
- [ ] Validate all counters/chunks/storage/screen/thermal/watchdog receipts; preserve all failures.
- [ ] Run accepted local regressions, exact-SHA CI4/4, independent review and positive-canary audit.
- [ ] Publish reduced-scope result with original gates explicitly unfulfilled; leave Sheet untouched.
