# Stage 12 security completion and long recording Alpha

> Execute inline using superpowers:executing-plans, test-driven-development and final independent review. Owner requests autonomous execution.

**Goal:** Preserve existing data and deliver a signed successor to v7 with bounded 30-minute capture and local processing.
**Architecture:** Retain SQLCipher, Keystore, 5-second encrypted transport units and existing recovery. Read authenticated audio in bounded windows outside decoder/playback callbacks. Preserve native Whisper. Prepare Cloud changes only in the test environment; production deployment requires separate authorization.
**Tech stack:** Kotlin, Android API28/36, JVM17, Python backend.
**Spec:** Owner Stage12 + long recording request; Technical Plan §§24,34.5,Stage12; ADR0011/0012; inherited release contract STAGE12_ALPHA_RELEASE.md.

## Constraints and decisions
- Base b310283, successor branch, no merge. Package and owner signer unchanged; Preview versionCode8.
- 30-minute intermediate scope, not a 120-minute claim. Existing Stage8.6 limitations remain disclosed.
- No private phone install, production AWS mutation, user-data deletion, key replacement or schema migration.
- Whole-file PCM allocation is disallowed for long recordings. Windows retain complete-source authentication before consumer access; repeated reads trade throughput for minimal architecture change.
- Cloud deployed API currently accepts 300 seconds. Keep explicit client guard until a separately authorized compatible backend deployment; do not claim long Cloud qualification from synthetic tests.

## Review focus
- Failure after partial capture retains committed prefix, never falsely reports saved.
- Failed/corrupt tail never reaches playback or ASR; cancellation wipes owned windows.
- Automatic limit stop is visible even in background.
- Upgrade retains encrypted audio, edits, settings and keys.
- Cloud deletion receipt never overclaims production or provider-internal erasure.

## Task 1: Capture and bounded local processing
- [ ] RED: extend AlphaRecordingLimitTest to 28,800,000 frames; add window selection/corrupt-tail and ordered Local ASR tests.
- [ ] Implement 30-minute AlphaRecordingLimit; ProtectedPcm.readWindow bounded to 4,800,000 samples; local recognition loops windows and publishes only complete result; playback loops authenticated windows.
- [ ] Update limit copy, background completion notification and boundary/recovery tests.
- [ ] GREEN: :core:audio:testReleaseUnitTest :app:testOwnerPreviewUnitTest; formatting and detekt.

## Task 2: Security and Cloud lifecycle
- [ ] Map canonical requirements with evidence and explicit blockers.
- [ ] Exercise existing authZ, KMS/S3 adapter, deletion/watchdog/credential loss using synthetic test custody; fix reproduced gaps only.
- [ ] Document metadata/audit/backup/provider limitations and incident/key-loss runbooks; no production changes.

## Task 3: Device qualification and release
- [ ] API28/API36 encrypted 30-minute accelerated capture, recovery, bounded processing, real >5-minute foreground/background capture.
- [ ] v7→v8 emulator APK replacement and continuity checks; owner signature/package/static native comparison.
- [ ] Update release tools/version and CI workflow. Run mandatory checks and independent review.
- [ ] Build signed APK, publish checksum and draft PR after gates; record distinct Stage12/long-recording/APK/Samsung statuses.
