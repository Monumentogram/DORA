# POCO Stage 8.6B Non-Battery Physical Acceptance Plan

> For agentic workers: use superpowers:executing-plans inline. Preserve this task's evidence and ledger across interruptions.

**Goal:** prove the owner's Stage 8.6B physical gates without reopening battery efficiency.

**Architecture:** retain the production microphone/FGS/persistence/VAD/segmentation/Recovery path. Add only the missing storage-budget preflight and isolated content-free acceptance instrumentation; no alternate recorder or production architecture.

**Tech stack:** existing Kotlin/Compose, Android instrumentation, Python/ADB, pinned private sherpa/Silero.

**Spec:** owner attachment “CODEX TASK — DORA Stage 8.6B”, 2026-10-08; authority retained locally with campaign receipts. Technical-plan storage sections 14.4 and resource gates; ADR-PERF-002.

## Global constraints

- Starting SHA 2cb4b470da88a52f2ce89ce0ae6c640c3222cfe1, existing PR99 draft/open/unmerged; no parent/main changes.
- Preserve all 46 owner recordings and historical receipts. No package clear/uninstall of DORA or bulk deletion.
- No battery comparison, AccuBattery/Batterystats experiments or energy integration. USB power may remain connected; state recorded, not energy evidence.
- Exactly 200 valid Start attempts; every attempted cycle retained; >=199 starts, finalizations >=99.5% of successful starts, zero whole-recording loss.
- Three separate >=3600-second continuous screen-off DORA runs; real pinned VAD, no fake capture.
- Zero unexplained gaps/duplicates/corruption; 9,600,000-frame CAP and 32,000-frame overlap; sample-delta diagnostic only.
- <=125,000,000 attributed bytes per 3600 seconds on each eligible long run; no averaging failures away.
- No SEVERE-or-worse thermal status, OOM or unexplained process death; no invented CPU/PSS thresholds.
- Exact final CI4/4, 138 methods, Recovery22, existing UI15; limits1800s/API28=60min/API36=45min unchanged.
- No Sheet closure, Group D, Stage9, Cloud/ASR. Battery deferred; security restoration OPEN.

## Review focus

1. Updating DORA must retain its signing identity, private runtime and owner inventory; reject mismatch before install.
2. Start during stale/free-space change or failed StatFs must not acquire the mic without the minimum budget.
3. A host gap must not be mistaken for continuous screen-off/thermal coverage; device callback evidence and monotonic samples are required.
4. Counters can be process-lifetime cumulative; per-run baselines and final authenticated source checks must agree.
5. Test deletion must require an exact campaign-owned identity and never act on the preserved owner set.

### Task 1: preservation and prerequisites

- [x] Verify local/remote SHA, PR/base/draft, prior CI4/4.
- [x] Read-only profile: same POCO model/firmware/API/ABI/page size; no active recording service.
- [x] Reuse pinned read-only inventory helper on installed APK; authenticated 46-entry inventory equals historical identities/frames/finalized state.
- [ ] Verify exact private artifacts and build/signing procedure; preserve content-free baseline inventory fingerprint.

### Task 2: minimum storage preflight

Files: `RecordingStorageBudget.kt`, `RecordingStorageBudgetTest.kt`, existing `RecordingController.kt`, `RecordingScreen.kt`; additive ADR and scoped admission validator.

Interface: `RecordingStorageBudget.assess(availableBytes: Long?): Snapshot` returns available, required, usable recording budget, canStart, and user-facing interpretation. One-hour allowance 125,000,000 plus unchanged16MiB floor =141,777,216 bytes. Reservation is admission headroom, not filesystem preallocation or a guarantee against other apps consuming storage.

- [ ] Add boundary/unknown/stale-state tests first; preserve existing floor semantics during active recording.
- [ ] Implement budget/free-versus-required UX, actionable insufficiency, fresh Start/native-boundary recheck. Use the same real controller boundary for injected low-storage tests; injection only in test driver, never filling owner storage.
- [ ] Run app host/control/UI regressions and compile; review no new recording architecture or latency weakening.
- [ ] Record scoped contract/ADR and minimal validator admission preserving historical hashes.

### Task 3: isolated physical driver and sealed protocol

Files: `tools/poco_non_battery/` for content-free host/parser and test-only Android instrumentation; protocol/evidence under `docs/evidence/poco-non-battery-8.6b/` after privacy reduction. Raw/private working artifacts outside Git at `POCO-8.6B-20261008`.

- [ ] Reuse actual controller Start/Pause/Resume/Stop and authenticated reader/deletion interfaces; never forge recording state, replace VAD, or bypass runtime authorization.
- [ ] Record IDs privately and fingerprints publicly. Protect exact preserved owner set before every campaign deletion.
- [ ] Capture monotonic screen/thermal callbacks, periodic process/FGS/microphone/resources and content-free VAD counters; fail closed for unknown coverage.
- [ ] Test parser rejection for missing/duplicate attempts, false denominator, screen wake, telemetry gaps, wrong identity, storage omission and canonical mismatch.
- [ ] Freeze JSON protocol, run IDs/order, 3-second short captures, 2-second minimum post-cleanup pause, bounded transition deadlines and long-run cadence before accepted measurements. Warm-up/diagnostic attempts remain separate and retained.
- [ ] Seal product source/APK/signing/private-runtime and helper identity before campaign; require APK-equivalent payloads if later source commits are evidence-only.

### Task 4: physical execution

- [ ] Predeclare valid state and environmental conditions; owner receives one precise physical instruction only when needed.
- [ ] Run200 real cycles with independent attempt receipts, canonical PCM, authenticated readback, mic/FGS release and exact campaign-only deletion.
- [ ] RunDORA-LONG-01/02/03, each full continuous screen-off>=3600s; record setup/finalize outside screen-off interval, actual totalcapture duration and storage normalization without energy claims.
- [ ] Verify every canonical frame, provenance/chunk mapping and metadata; full-source authentication; runtime/model identities and VAD statistics.
- [ ] Run bounded error actions, rapid/double controls, low-storage injection, background notification, Doze, Battery Saver and physical process-kill/explicit-Resume smoke.
- [ ] Verify owner46 unchanged and every campaign recording deleted; leave mic/FGS off. Retain all failures/invalidations.

### Task 5: final regression and publication

- [ ] Apply all required accepted regression suites, privacy audit with positive canaries and independent adversarial review.
- [ ] Commit/push on existing branch; obtain final exactSHA4/4 and inspect full receipts, artifacts and logs.
- [ ] Report each owner's45 requested fields with unknown/partial/failure honest; PASS only when all21 conditions hold.
- [ ] Leave PRdraft/open/unmerged and Sheet unchanged. Recommend a separate Stage8.6 closure assessment; do not execute it.
