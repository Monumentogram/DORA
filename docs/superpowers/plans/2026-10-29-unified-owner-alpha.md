# Stage 9.1 Unified Owner Alpha Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the owner's existing Preview to one permanent Local + manually authorized Amazon Cloud APK without losing encrypted data.

**Architecture:** Start at PR119 e967fa4, which contains PR116/117/118 by ancestry. Keep the existing controllers, DTR1-compatible/DTR2 transcript codec, SQLCipher schema4 and Android Keystore. Change only necessary packaging/integration defects; diagnose the existing direct HTTPS route independently.

**Tech Stack:** Kotlin/Compose, Gradle9.5/JDK17, SQLCipher/Room, pinned Whisper ARM64, existing Python AWS backend.

**Spec:** [Owner request](../../specs/STAGE9_1_UNIFIED_OWNER_ALPHA.md).

## Global Constraints

- Package com.monumentogram.dora.alpha.preview, POCO Android user10; same original certificate and storage keys.
- No uninstall, clear-data, reset, old data edits, model deletion, AWS redesign, permanent paid service, merge or Signal implementation.
- Explicit separate confirmation of the exact APK before phone installation; temporary foreground user0 only with separate consent.
- Cloud MANUAL_ONLY, explicit per-recording consent, only short approved test audio, existing $20/month budget.
- No full Recovery benchmark or rewriting historical evidence. No generic CI PASS while failures remain.

## Review Focus

- Legacy transcript edits, selected older versions and cancelled jobs survive DTR1→DTR2: richer golden fixture and encrypted old/new process test.
- A Cloud result must not select itself or replace Local edits: extend encrypted persistence test through Cloud edit and explicit selection.
- New package permission INTERNET must not admit any unrelated permission: artifact validator positive and negative cases.
- Same signer/code increase must preserve vault and model: emulator update first, exact APK audit and physical baseline comparison.
- Direct TLS and installation authorization must remain real: trust validation, new Preview public invitation enrollment, no copied private key or tunnel-based acceptance.

## Task 1 — legacy data and unified persistence

Files: `android/core/audio/src/test/kotlin/com/monumentogram/dora/audio/transcript/TranscriptStateTest.kt`, synthetic legacy fixture under `src/test/resources/`, `android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence/CloudTranscriptPersistenceTest.kt`.
Consumes existing TranscriptCodec/TranscriptReducer/TranscriptPort. Produces regression evidence, no new storage interface.

- [x] Add an independently serialized DTR1 fixture with multiple versions, an edit, explicit older selection and cancelled job; assert exact decode/re-encode and Local/Cloud append preservation.
- [x] Extend encrypted Cloud test: edit Cloud, explicitly select it resolving foreign edits, reopen, assert both edited texts and immutable originals.
- [x] Run targeted JVM tests; build old installed-source and new core test APKs. Seed via old `TranscriptProcessRestartTest#seed`, replace test APK with new same-signer APK, run `readAfterProcessRestart` on the same isolated synthetic run.
- [x] Run targeted encrypted Cloud/persistence checks on the existing disposable Cloud emulator, never POCO instrumentation or user data copies.

## Task 2 — one owner APK

Files: `android/app/build.gradle.kts`, `tools/owner_preview_artifact.py`, `tools/test_owner_preview_artifact.py`, only other proven current Preview identity references.
Consumes Task1 evidence and existing private native/VAD inputs. Produces OwnerPreview code5/name0.1.0-owner-preview.5.

- [x] First update identity/permission tests to require code5, reject old4, admit INTERNET and still reject unrelated permissions; observe failures.
- [x] Bump only OwnerPreview, retain OwnerPilot/shared Alpha identity, bind owner-local audit to this branch and ancestor, update its SBOM version through existing constants.
- [x] Run format/Detekt/unit/Lint, OwnerPreview build and targeted backend contracts; sign with the existing private key. Verify package, signer, native payload, no weights and no diagnostic release hooks.

## Task 3 — scoped CI diagnosis

Files: `docs/STAGE9_1_GENERIC_CI_FOLLOWUP.md` and proven narrow validator fix only if it preserves historical source-bound contracts.

- [x] Record reproduced stale identity/SBOM coupling and source-proven v3 diagnostic versus v4 fixture conflict.
- [x] Complete scoped source/log diagnosis; exact historical diagnostic-test reproduction remains in the documented follow-up, with no broad Recovery campaign.
- [x] Preserve ADR0022 historical v3 requirements. If successor qualification exceeds the unified production path, document exact follow-up and keep Generic CI FAIL.

## Task 4 — existing direct HTTPS and owner enrollment

Files: existing `backend/deployment/alpha/` source only if a proven configuration fix is required; private operator receipts outside Git.

- [x] Refresh existing AWS SSO if needed, inspect current SG/device egress/routing, then DNS/TLS/IPv4/IPv6 and Android behavior using bounded probes.
- [x] Apply only demonstrated necessary existing-network rule change; retain owner authorization, TLS, API limits, cleanup and budget controls. No public SSH.
- [x] Configure Preview HTTPS through its UI and enroll its own public installation invitation. Do not reuse another package/user's private key.

## Task 5 — owner acceptance and publication

Files: `STAGE9_1_UNIFIED_ALPHA_RESULT.md`, sanitized evidence, one Draft PR stacked on PR119.

- [x] Publish reviewed source/targeted checks and exact signed APK; obtain separate install confirmation after migration evidence.
- [x] Capture current four records/seven versions baseline, update only user10, verify signer/package/model/data.
- [ ] Use short authorized test records for actual Local RU/EN, local offline/cancel, manual Cloud RU/EN over direct HTTPS, independent edits/selection, reopen after restart. Do not upload historical owner audio.
- [ ] Verify temporary AWS cleanup; test without USB transport and available network channels, mark unavailable channels/network BLOCKED precisely.
- [x] Record actual statuses, CI limitations and SHA; attach Draft PR; no merge.

## Execution rulings

- User's explicit autonomous execution scope supersedes redundant plan approval; installation confirmation remains mandatory.
- The native worktree tool previously rejected this repository-subfolder layout; reuse the established external `.worktrees/` Git fallback.
- Disk has approximately5.6GB free; reuse the existing disposable Cloud emulator and caches rather than create another AVD.

## Verification update (2026-10-29)

- Old installed-source core test APK seeded an encrypted synthetic vault; new APK updated it in place and passed existing restart/read test on API36. No owner data copied.
- Cloud plus transcript persistence: 5 Android tests passed. Public Preview manifest/identity/permission check: 1 Android test passed.
- OwnerPreview JVM 117, audio Debug 266/Release 267, model 5: all passed. Backend 55 and owner host guards 68 passed.
- Review caught stale shared instrumentation identity/INTERNET expectations; corrected for Preview5 while retaining Pilot4 and cross-user denials. Narrow test passed; reviewer confirmed resolution.
- Owner native build, formatting, Detekt and Preview/core Lint passed. Signing, fresh CI and physical unified acceptance still pending.
- Generic CI follow-up is docs/STAGE9_1_GENERIC_CI_FOLLOWUP.md; do not run broad Recovery acceptance for this release.

## Physical verification update (2026-10-29, 07:50 UTC)

- Exact signed APK5 installed with separate owner approval; user0 remains uninstalled. Original four documents/seven versions and four authenticated originals preserved.
- Two newly authorized synthetic clips passed actual offline Local RU/EN. Cancellation added no version; RU Local edit and both results survived process restart.
- Reviewed opt-in public HTTPS applied to existing stack, retaining all other rules/resources; direct mobile TLS passes after repeated carrier IP changes. Preview own invitation enrolled.
- Both targeted workflows at2e6d38e passed, including API28/API36.
- Actual Cloud acceptance is still blocked: signed session/registration can succeed, but sequential HTTPS calls stall before a job exists. A no-auth/no-audio Android two-request probe reproduces first-success/second-timeout. Diagnose connection reuse before further paid-operation retries.

## Final session boundary (2026-10-29)

- Final code/configuration source985a160: targeted Stage18 40838811236 and PIN-free40838811241 passed. Live nginx validation/reload and backend56 also passed.
- Server single-request connections improved the bounded control sequence, but actual RU remains UPLOADING with0 accepted audio bytes/chunks/provider attempts/reservations. Synthetic body and fresh-process probes reproduced sustained transport failures. Current default Wi-Fi also failed before a small request body; large probe skipped.
- Local upgrade, old data, original audio, RU/EN offline, cancellation and Local edit/restart passed. Actual Cloud RU/EN, independent device Cloud edits/selection/restart, provider cleanup and physical USB-disconnected acceptance remain BLOCKED.
- All phone probes stopped; temporary diagnostic files removed. No additional TLS/domain/infrastructure workaround or Signal implementation. Continue on a network that passes complete trusted HTTPS delivery, then retry the same authorized pending action.
- See STAGE9_1_UNIFIED_ALPHA_RESULT.md and sanitized docs/evidence/stage9_1/mobile-transport.json. No unified-ready PASS or background continuation is promised.
