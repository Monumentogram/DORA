# Instant recording controls implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline. The owner supplied the architecture ruling and requested execution; independent adversarial review is required.

**Goal:** Pause and Resume finish independently of encrypted persistence without losing accepted frames or weakening final Saved.

**Architecture:** Keep native ownership and the existing control executor. RecordingSession seals immutable units on that executor and sends them to one persistence executor; completion returns to the control executor. Admission tags each block with physical identity and exact first frame. A shared outstanding-frame budget covers every admitted frame until confirmed durable, including queue, assembler and in-flight append.

**Tech Stack:** Kotlin/JVM17, Android API28–36, AudioRecord, existing SQLCipher/Recovery writer.

**Spec:** Owner's Stage8.3 Remediation v2, 2026-10-02; ADR-AUDIO-005 records its architecture ruling. Exact parent 3e1603dbad9083977b1e08b6a5cce9a52de2148d.

## Global constraints

- No persistence policy, key, Recovery, native ownership or AppLock weakening.
- Existing 512000-byte admission bound spans all outstanding PCM.
- Capture PAUSED requires admission fence and native release; durability may remain PENDING.
- Final SAVED requires every preceding append and verified finalize success.
- Stage8.4 NOT_STARTED; prior PRs and evidence immutable; new PR DRAFT/UNMERGED.
- Physical 30+30, fixed prospective p95 limits: Pause ACK100/fence50/release200/visible250ms; Resume ACK100/native150/PCM300/visible300ms. Every presentation sample required.

## Review focus

- Persistence fails while a new native start is being authorized: fence admission and stop immediately.
- Completion arrives after interruption: it must never revive capture or falsely publish SAVED.
- Rapid empty segments: no unbounded queued commands or empty writes.
- Interrupted writer still owns bytes/access: do not close access until its serialized barrier completes.
- Dropped first draw: retain bounded draw candidates, require exact presented-frame association rather than infer it.

## Task1: asynchronous immutable persistence

Files: RecordingSession.kt; new AsyncRecordingSessionTest.kt.

- [x] RED tests with controlled executors: old A append blocked, Pause visible, Resume B accepted, A then B exact provenance; finalization waits; failure interrupts; duplicate/rapid/empty controls.
- [x] Seal bytes and identity before enqueue; advance reserved offsets independently of durable offsets. Persistence tasks never mutate capture state; return results through control completion executor.
- [x] Fence failure atomically, clear queued plaintext after typed failure, expose settlement barrier for safe access retirement.
- [x] Run core audio suite including all414Recovery regressions.

## Task2: segment-aware admission and controls

Files: BoundedPcmQueue.kt, CaptureAdmission.kt, AudioRecordCapture.kt, CapturedTimeline.kt, RecordingController.kt; corresponding tests.

- [x] RED stale generation/provenance, backlog budget after drain, immediate Resume and backpressure tests.
- [x] Tag admitted PCM with immutable physical ID and first frame; validate exact continuity at session accept. Budget is released only by durable completion, reset only after terminal settlement.
- [x] Drain memory only before sealing Pause. Keep reader release protocol. Resume bypasses writer queue. Final Stop stays FINALIZING until settlement; failures stop native and retain Recovery semantics.
- [x] Deterministic race coverage for UI/notification duplicates, lock, stop and rapid cycles; run app/core suites.

## Task3: truthful UI and physical evidence

Files: RecordingScreen.kt, ProductRecordingService.kt, RecordingLatency.kt and tests; private measurement scripts.

- [x] Pending durability text independent of capture; freeze waveform at admission fence.
- [x] Retain bounded draw candidates for exact presentation matching and independent durability catch-up timestamps.
- [ ] Install bounded test build; measure30Pause+30Resume and rapid sequence on POCO with real authorization, no missing samples or exclusions. Stop microphone and restore owner development marker.
- [ ] Secure-window-respecting visual verification; sanitized evidence only.

## Task4: successor governance and publication

Files: new ADR/decision, successor contract/validators/tests/evidence, additive status/backlog and CI normalization.

- [x] Preserve all historical evidence and sealed gates; add exact successor admission and fixed-threshold negative controls.
- [x] Full local622+ baseline JVM,373+Python,API28/API36 persistence/crash/UI, lint/detekt/format/dependencies/SBOM/alignment.
- [x] Independent adversarial review, fix reproducible findings with RED/GREEN tests.
- [ ] Commit exact reviewed content, draft stacked PR, exact-SHA CI and leak/artifact audit.
- [ ] Only PASS permits Sheet I74/J74 update after live read. Final22-point report includes all raw timing rows and unresolved limitations honestly.
