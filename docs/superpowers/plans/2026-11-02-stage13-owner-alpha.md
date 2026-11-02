# Stage 13 owner Alpha stabilization implementation plan

> **For agentic workers:** Use superpowers:executing-plans and independent domain workers; track evidence before completion claims.

**Goal:** Improve the existing daily recording/transcript workflow and prepare honest Samsung and Amazon qualification after Alpha v9.

**Architecture:** Keep the existing encrypted recording, Local/Cloud versioning, native libraries and Signal UI. Diagnose retained evidence before changing behavior; apply only bounded, regression-tested corrections. Independent Samsung, Whisper, Cloud and UX tracks converge at release qualification.

**Tech stack:** Kotlin/Compose, JVM 17, Android API28/36, Python host tooling, existing Whisper/ONNX and Amazon adapter.

**Spec:** Owner Stage13 request dated 2026-11-02; canonical `docs/DORA_MVP1_TECHNICAL_PLAN.md` Stage13 and test strategy. Base: `49ca2070f15a618d145ded307fc87249f382b22c`, stacked on PR128 then PR127.

## Global constraints

- No main merge, personal-device installation/microphone/settings, production AWS mutation or unapproved billable request.
- Preserve package, signing certificate, Keystore, encrypted data, schema compatibility, models, settings, edits and versions.
- Long Cloud remains disabled. Existing $20/month and operation/day limits remain.
- Samsung physical remains `SAMSUNG_DEVICE_VALIDATION_PENDING` without actual owner acceptance.
- Stage12 remains PARTIAL and issue125 open. Full Stage13 requires canonical device/endurance gates.
- Retain strict historical tests and failed evidence. No unchanged 24-hour synthetic rerun.

## Review focus

- Busy operations must not silently lose edits or appear successfully saved before persistence.
- Cancellation/retry after network failure must preserve completed work and avoid a new paid submission.
- Missing RU interior phrases must not be mislabeled window-boundary defects.
- Upgrade validation must inspect the retained v9 fixture, never clear or replace it.
- Public results contain scalar synthetic evidence only; no private content or credentials.

## Task A: Samsung readiness and qualification control

Files: `docs/STAGE13_SAMSUNG_ACCEPTANCE.md`, `docs/STAGE13_RESULT.md`, stage status/backlog appendices.

- [ ] Record identity/API/ABI/firmware and native/OEM acceptance prerequisites without touching personal devices.
- [ ] Write exact owner procedure for install/update, model, short RU recording/edit, screen-off 30 minutes, playback/ASR, restart/history/search/export.
- [ ] Bind inherited evidence and remaining physical gates explicitly.

## Task B: Localized Whisper diagnosis

Files: `docs/STAGE13_WHISPER_QUALITY.md`, focused host diagnostics/tests if needed.

- [ ] Inspect retained RU/EN PCM/native-return evidence and immutable fixture markers.
- [ ] Compare full-window and isolated controls with unchanged model/settings; record limits where unavailable.
- [ ] Add only useful reproducible diagnostic tooling with failing guard tests first; product changes require proven cause.
- [ ] Report functional/quality separately, exact/bounded marker counts, speed and RAM comparability.

## Task C: Cloud software/live readiness

Files: existing Cloud source/tests and `docs/STAGE13_CLOUD_READINESS.md`.

- [ ] Trace existing authorization/HTTPS/upload/versions/retry/cancel and composed-job accounting.
- [ ] Reproduce any concrete defect before minimum fix; execute relevant host/unit regressions.
- [ ] Prepare 30-minute then 60-minute live procedure with explicit authorization/cost prerequisites and disabled default preserved.

## Task D: Daily UX stabilization

Files: existing recording/transcript/library UI/controller tests and `docs/STAGE13_UX_STABILIZATION.md`.

- [ ] Trace status, editing, selected-version search and export behavior.
- [ ] Reproduce a concrete issue, implement minimal correction, verify regression and existing suite.
- [ ] Preserve Signal UI and existing storage/capture architecture.

## Task E: Candidate and final gates

- [ ] Run scoped host/JVM/lint checks and API28/36 coverage, preserving historical inventories.
- [ ] Obtain independent review; fix important findings with regression evidence.
- [ ] If product behavior changed, increment versionCode above9, produce reproducible owner-signed APK, verify identity and retained-v9 update/restart data.
- [ ] Run seven mandatory workflows at exact candidate source; retain failures and results.
- [ ] Publish a stacked draft PR; publish APK/hash only after release gates pass. No merge.
- [ ] Record full requested matrix with inherited/fresh/PENDING distinctions and exact remaining owner actions.
