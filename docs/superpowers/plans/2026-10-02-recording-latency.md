# Stage 8.3 Pause/Resume latency implementation plan

> **For agentic workers:** Use superpowers:executing-plans in the existing isolated worktree. Independent adversarial review is required before publication.

**Goal:** Measure and remediate physical POCO Pause/Resume latency without changing recording durability, provenance, or security.

**Architecture:** Separate immediate pending UI from confirmed transitions. Fence PCM admission atomically, retain a single native owner through release, and consume an exact-access, current-foreground authorization for Resume. Preserve durable persistence boundaries; optimize only measured redundant work.

**Tech Stack:** Kotlin/JVM 17, Android API 28–36, Compose, AudioRecord, existing encrypted Recovery/SQLCipher composition.

**Spec:** Owner Stage 8.3 Remediation: Pause/Resume Latency request, 2026-10-02. Exact parent `c596f5256e34bebf308d4c90d89c9f97affc3abb`.

## Global constraints

- Branch `stage/8.3-pause-resume-latency`, draft PR base `stage/8.3-product-recording`; preserve parents and main.
- No PCM/identifiers in diagnostics; bounded monotonic timings only.
- Same-session POCO p95: visual acknowledgement 100 ms; admission stop 250 ms; confirmed Pause 500 ms; native Resume 500 ms; first PCM and confirmed recording 750 ms.
- All owned PCM persists or produces typed failure. No overlapping native reader, revoked grant revival, background grace, or physical-segment collapse.
- Acceptance uses absent development marker and real initial authentication; restore owner's marker afterward.
- Stage 8.4 NOT_STARTED. Source PENDING_FINAL_PUBLICATION until external evidence can be sealed.

## Review focus

- Tap queued behind main-thread stall must retain its original timestamp.
- Pause during append must preserve both queued and borrowed PCM.
- Revocation between grant and native start must reject the old grant after a later unlock.
- Delayed reader release must prevent all later native starts.
- Missing presentation evidence and failed operations remain visible in the benchmark denominator.

## Task 1: Measured baseline and trustworthy diagnostics

Files: app `RecordingLatency.kt`, its unit tests, `MainActivity.kt`, `RecordingScreen.kt`, `RecordingController.kt`, `AudioRecordCapture.kt`; core `RecordingSession.kt` and scoped persistence timing hooks.

- [x] Write failing bounded-clock/operation/frame correlation tests; run `:app:testDebugUnitTest` and observe missing implementation.
- [x] Implement baseline timing and capture initial physical Pause/Resume evidence.
- [x] Correct input association, span pairing and success-only markers identified by independent review. Correlate drawn vsync and tap with `gfxinfo` DisplayPresentTime instead of treating frame submission as presentation. Three unmatched Resume frames remain explicitly incomplete.
- [x] Measure persistence substages before changing its behavior. Keep all failed and intermediate attempts.

## Task 2: Safe responsive controls

Files: app capture/controller/queue/screen/service; core `AndroidProductAudioRuntime.kt`, `AndroidRecordingAccessManager.kt`, recording access/authority tests; a bounded transition gate if needed.

- [ ] Add deterministic failing tests for all 17 owner race cases with clocks/latches and exact frame/segment assertions.
- [x] Implement an atomic admission monitor, safe stop/release ownership, exact-generation command validation, and immediate truthful Pause/Resume pending states.
- [x] Attempt Resume using current AppLockSession.Authorization and exact active RecordingAccess; otherwise route to existing real authentication. Recheck identity/generation/service at consumption.
- [x] Optimize only persistence work proven redundant by measurements; retain fsync/readback/Recovery security and typed failures. Durable Pause still exceeds the limit.
- [x] Run targeted JVM and UI/service regression suites; obtain independent adversarial review and resolve code findings. Physical P1 is not closed by review.

## Task 3: Physical acceptance and publication

Files: remediation ADR, bounded successor validator/evidence contract, status/backlog overlay, external run packet.

- [ ] Run at least 20 Pause and 20 Resume operations on POCO, retain every value and min/median/nearest-rank p95/max; separately prove real reauthentication after revocation.
- [ ] Retain 414 Recovery tests and all Stage 00/8.1/8.2/8.2C/8.3, Cloud, migration/lifecycle/UI, formatting/lint/Detekt/dependency/SBOM/native gates.
- [ ] Preserve historical source receipts while adding explicit successor admission. Commit scoped files, create stacked draft PR, attach it, verify exact-SHA CI and audit artifacts/logs.
- [ ] Publish PASS only when every threshold and gate is proved; otherwise report exact blocking distribution. Preserve Sheet C74 and Stage 8.4, and restore development marker after stopping the test.

## Evidence disposition

20 Pause and 20 Resume attempts completed with the development marker absent. The marker was restored after saving. A separate real background reauthentication check passed. Captured timer cadence on POCO no longer skips seconds during append. Pause acknowledgement p95 109.60 ms and confirmed Pause p95 2102.76 ms fail the unchanged limits; Resume confirmation presentation is incomplete in 3/20 attempts. No latency-ready status is claimed.

Race evidence is split across the real native-reader port, admission gate, request gate, AppLockSession and RecordingSession. Tests cover empty/nonempty queue, a blocked worker holding borrowed PCM, initial-start fence, duplicate controls, immediate Resume/Pause, Pause→Stop, Resume→Stop, revoked authority before/inside consumption, lock/recreation, competing request ownership, stale service generation and delayed native release. Notification routing retains the inherited Android UI test. These component tests and source review do not pretend to be a directly injected complete Android RecordingController race harness; that coverage remains a stated limitation of this draft.
