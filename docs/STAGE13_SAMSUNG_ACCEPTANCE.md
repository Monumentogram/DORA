# Stage13 Samsung owner acceptance

Status: `SAMSUNG_DEVICE_VALIDATION_PENDING` until every required result below has actual evidence. Partial device observations are not full acceptance. Canonical Stage13 additionally requires the supported device matrix, 1/3/8h soak, 16-KiB runtime and remaining security gates.

## Actual device and authorization, 2026-11-02

- Owner connected Samsung and initially permitted read-only information only.
- Owner subsequently explicitly authorized acceptance and installation, then confirmed participants had been notified and recording could start.
- Read-only identity: Samsung SM-S908B (Galaxy S22 Ultra), Android16/API36, ARM64 (`arm64-v8a,armeabi-v7a,armeabi`), 4,096-byte pages, firmware S908BXXSOGZH3, security patch2026-08-05, physical device (`ro.kernel.qemu=0`). No serial is published.
- Owner Preview was absent before installation. Existing unrelated DORA PoC packages were preserved. This is a new Preview installation, not evidence of migration from an earlier Samsung Preview.
- Installed published Alpha v9 after verifying APK SHA256 `fe8f13035f089515ea19510d8e1a0de279c46796c3c3e2756a53ffd11a779ad7` and certificate SHA256 `a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d`.
- Package `com.monumentogram.dora.alpha.preview`; cold launch completed (reported755ms). Initial library was empty. App opened the microphone after the owner's participant confirmation. Timer and committed audio duration advanced.
- Initial sampled process PSS142,998KiB; later screen-off sample113,972KiB. These are snapshots, not peak-memory qualification.
- Screen-off observation showed foreground microphone type0x80 and held `DORA:recording` wake lock. No device timeout/battery policy was changed.
- Original pinned small-q8 model copied to a newly named Download file for explicit in-app import; digest checked on both ends. Import and inference are separate pending checks.

The emulator ONNX topology crash remains historical evidence. Successful physical startup above only covers this device/build/start, not all firmware, endurance or 16-KiB devices. Do not transfer an emulator PASS to Samsung.

## Owner procedure and result sheet

Use a quiet consented test, with several complete Russian sentences and pauses. Keep personal audio/text on the device. Record only categorical outcomes, durations, counts and sanitized error codes in public evidence.

| Step | Action | Required result / evidence |
|---|---|---|
|1|Confirm device model, Android/API, ABI, firmware and candidate version/hash/signing certificate.|Exact installed candidate identified; supported claim limited to observed device.|
|2|If upgrading, note existing recording count, a saved edit, selected Local/Cloud version, settings and imported-model status.|No uninstall, clear-data, profile reset or downgrade. Existing items still open after `install -r`.|
|3|Open DORA and import the pinned model through Settings/model file picker if absent.|Import success; digest is enforced by the app. Restart and verify it remains available.|
|4|Create a2–3minute Russian recording, including speech, a pause/resume and distinct opening/closing phrases.|Preparation finishes; recording indicator/timer advance only while recording; pause and resume work.|
|5|Stop; wait for the saved result; open the record.|One saved item, plausible duration; beginning/middle/end audio play. An interruption must name retained audio, never imply a successful complete recording.|
|6|Run Local/Russian recognition.|Local version persists, intelligible content checked by owner; missing phrases recorded honestly. Functional completion is separate from WER/quality.|
|7|Edit, explicitly save, close/reopen.|Exact saved edit retained; model rerun never silently overwrites it. Unsaved draft clearing on background follows existing privacy contract.|
|8|Start a30minute recording; turn screen off and leave app backgrounded.|Visible foreground microphone notification; no unexplained stop. Sample bounded process memory without extracting recording content.|
|9|Unlock, pause/resume once if appropriate, stop and wait for finalization.|Saved duration at least30minutes of actual recording (excluding pause), complete playable audio; finalization status remains visible.|
|10|Recognize the long record locally.|Visible progress, bounded windows, completed text retained; quality reviewed independently. Cancel/retry should preserve completed work.|
|11|Restart app; when independently authorized, restart device.|History, audio, model, saved edits, selected version and settings still available; microphone must not restart itself.|
|12|Search a known phrase from selected saved text; open result; export displayed saved text.|Correct record, current chosen/edited version, explicit file picker and success status. No accidental export of another version.|
|13|Delete only a newly created disposable test record after explicit owner instruction.|Clear scope; durable removal and truthful progress/retry. Retained user/test evidence is otherwise preserved.|

If screen locks, the owner unlocks it. Never bypass the lock, change personal-device battery/security settings, or install an instrumentation package on the personal device as an unstated substitute for product acceptance.

## Pending/stop rules

- Native crash, lost committed audio, unexplained interruption, missing history/edit/model, or signing/package mismatch fails the affected gate. Preserve data and bounded local diagnostics.
- A completed30minute trial qualifies only that trial. It does not close canonical endurance, other OEMs, security Stage12 or AWS issue125.
- Amazon live work requires its own approved budget/configuration; Samsung installation authorization does not authorize AWS changes or paid jobs.
- Physical acceptance remains pending until the completed sheet is bound to the installed source/APK. Owner-visible success alone does not substitute for unperformed steps.
