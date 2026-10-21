# POCO isolated Alpha physical smoke protocol

Status: **future procedure; not executed**. This document does not authorize any physical mutation. Software readiness, physical acceptance, historical-data safety and signed release are separate gates. Stage 8.6 remains NOT_READY; security restoration remains OPEN.

## Preconditions and owner decisions

Obtain a separate owner decision covering creation of a full Secondary User, installing the exact isolated APK in it, recording a consented test, and deleting only the new test recording/user afterward. The owner sets a separate system credential directly on the phone. Never ask for, record or automate the owner's PIN. Do not substitute Second Space or Work Profile.

Use a finalized software receipt with exact source commit, two clean builds, APK SHA-256, a single isolated signing certificate, pinned AAR/model and all mandatory CI/review gates. The only applicationId is `com.monumentogram.dora.alpha.test`. Do not install `com.monumentogram.dora` or `com.monumentogram.dora.debug`. Different Android users have separate app data but share package code; a `--user` argument does not make updating an existing package harmless.

Set explicit variables locally: `S` is the authorized POCO serial; `T` is the newly created user ID; `N` is its serial number from `dumpsys user`; `APK` is the accepted absolute APK path. Never infer `T` from the foreground user, hardcode 10/999, or accept 0. If `T == 0`, stop with `STOP / OWNER_USER_SELECTED` before any other operation. No shell wildcard, mass uninstall or global data cleanup is permitted.

## Historical-data safety gate

Before creation, capture only read-only user/package metadata: `adb -s S shell pm list users`, `adb -s S shell dumpsys user`, and `adb -s S shell dumpsys package` for each of the two historical packages. Keep raw output private. Check no DORA process/service is active using read-only process/service queries. If an old process is active, stop; do not force-stop, unlock, open or reconcile it as an automatic fix.

Do not read, hash, copy, recover, finalize, resume or delete LONG-01/LONG-02 or any historical vault contents. No `run-as`, content-provider extraction, backup/restore, key enumeration or permission change targeting historical packages. Package metadata stability and the Android sandbox provide a bounded non-interference argument; they are **not a fresh bytewise historical-vault attestation**. If bytewise confirmation is required, the owner must separately approve a protected acquisition procedure before physical acceptance can close that stronger gate.

## Creation (only after approval)

1. Check the previously confirmed firmware remains Android 14/API34, POCO M5 V816.0.6.0.ULURUXM, SELinux enforcing and verified boot locked. Stop on drift and reassess.
2. Through the system Users UI, create one ordinary Secondary User, with no account import/restore or app/data migration. An authorized operator may instead run exactly `adb -s S shell pm create-user DORA-Alpha-Test`; this command is explicitly outside the current software task. Record the returned ID and independently verify it in `dumpsys user`.
3. Owner completes the new-user setup and sets its own PIN/password on the phone. The test profile must be `android.os.usertype.full.SECONDARY`, initialized, non-admin, non-guest, non-demo, unrestricted, not being removed and not ephemeral. Record `T > 0` and serial `N > 0`; reject duplicates or unknown flags.
4. Owner switches to that user via system UI. Verify `adb -s S shell am get-current-user` equals the already recorded `T`. Do not substitute that output for explicit selection.

## Installation (only after approval)

1. Run `python tools/isolated_alpha_preflight.py --serial S --user-id T --user-serial N --receipt PREINSTALL.json`. It must return `READY_FOR_SEPARATELY_APPROVED_INSTALL`. It only reads metadata and cannot install or switch users. The test package must be entirely absent, including retained package state. Any prior test installation requires a separate investigation; do not use `-r`, `-d`, `install-existing`, uninstall or clear as a workaround.
2. Independently verify the local APK hash equals the accepted receipt, the APK signature verifies against its exact isolated certificate, and badging reports the exact test ID/version. Recheck source SHA against the final PR receipt. Verify merged manifest has no sharedUserId, cross-user privileges, singleUser components, production provider authorities, backup/export routes or debug bypass. Treat a mismatched APK as a new candidate requiring validation.
3. With the approved `S`, `T` and `APK`, run exactly `adb -s S install --user T APK` once. Do not omit `--user`, add `-r`, or fall back to user 0. Check the command reports success.
4. Read package state for all users. The test package must be installed only for `T`; it must not be installed for user 0. Its appId/codePath must differ from both historical packages. Recheck both historical packages' codePath, appId, version, signing identity and per-user installed/enabled state against the pre-install snapshot. Stop on unexpected changes; do not automatically undo via global uninstall.
5. Only now launch the isolated test app through the selected user's launcher. A new package/UID creates its own app-private data and Keystore namespace; do not import aliases, databases, recovery policies or credentials.

## Functional smoke gate

1. Confirm App Lock requires the selected user's real system credential. Cancel/wrong credential must not expose recording metadata or audio. Background/restart must require fresh authorization. Never enable development markers or disable screen lock.
2. Enter the explicitly verified `T` and `N` in the isolated operator and verify its displayed package/user identity. Host topology confirmation is an operator attestation, not a platform-issued cryptographic proof. It supplements the runtime's actual-user/profile checks.
3. Start exactly one consented recording via normal DORA UI. Confirm microphone indicator/FGS and actual non-silent input; record only non-content timing metadata. After completion use the alpha operator's authenticated local RecordingId listing to identify the new source, then explicitly enter that exact ID. Listing does not grant a deletion proof. Do not extract an ID by reading the vault through ADB.
4. Turn the screen off during capture. Resume the same user, reauthorize when requested, Pause and Resume once. Verify elapsed audio duration is cumulative and the RecordingId remains unchanged. Paused wall time does not count as captured frames.
5. Let the accepted automatic five-minute limit occur. Do not replace it with manual Stop. Capture must stop before Finalize; pending durability must show saving, never SAVED early. Canonical frame count must be positive and at most 4,800,000. Backpressure remains 256,000 frames; an interruption fails this smoke rather than raising the limit.
6. Use the isolated operator's exact RecordingId readback action after real authorization. It must verify durable completion, every encrypted unit and canonical ranges, with no unexplained gap, duplicate or corruption. One logical recording and its technical chunks must agree. PCM remains transient in the app and is not exported. Retain only content-free counts/result metadata.
7. Check microphone indicator OFF, AudioRecord released and recording FGS absent. Recreate/restart the isolated app; no automatic microphone restart is permitted. Reauthorize and verify the same durable recording with the operator. Controlled process death before finalization, if separately included in the approved smoke, must use only the isolated package and retain the same RecordingId without automatic resume.
8. Exercise stale authorization, canceled authorization and background/foreground controls. Failure must never grant audio access or allow a stale deletion ticket.

## Exact-recording cleanup (only after approved smoke)

1. Fully authenticate and read back the exact new RecordingId. Do not use historical IDs, directory paths or bulk selection. Keep the fresh readback result in the same authorization/session.
2. Choose the isolated operator's explicit delete action, verify the exact displayed identity and confirm the existing DORA deletion dialog. Cancellation must leave the source untouched. A stale proof requires fresh readback, never a bypass.
3. Require confirmed source deletion, absent encrypted audio artifacts and audio key references, and the expected tombstone. Partial deletion or unavailable evidence is NOT clean; retain the isolated environment for diagnosis. Do not relabel best-effort cleanup as complete.
4. Reopen/re-authorize the isolated app and verify the deleted source cannot be read or recovered. Other synthetic/test sources, if separately approved, must remain unaffected.
5. If owner approved removing the test user, first verify `T > 0`, unchanged serial `N`, ordinary Secondary User type, recording cleanup receipt and absence of any needed test evidence. Owner switches back via system UI; require foreground is no longer `T`. Run only `adb -s S shell pm remove-user T`. Confirm that exact user/serial is gone. Do not issue a global APK uninstall, `pm clear` or remove-user fallback.
6. Compare historical package/user metadata again. Never launch historical DORA to check it. The historical preservation report must explicitly distinguish observed metadata stability, no issued historical mutation commands and the absence of a new bytewise vault check.

## Acceptance and failure handling

Physical PASS requires completed real microphone/screen-off/Pause/Resume/cap/readback/restart/FGS/cleanup evidence from the exact installed APK. Historical-safety PASS requires the approved non-interference evidence and no unexpected historical-package changes; stronger bytewise preservation remains unclaimed without separately authorized evidence. Any ambiguity leaves the relevant gate open. A functioning isolated build does not establish production APK physical acceptance or Signed Alpha readiness.

No new 60-cycle campaign, long recording, battery experiment, Group D, Cloud, ASR or Stage9 work is authorized by this protocol. Google Sheet remains unchanged during software preparation.

Platform references: [AOSP multi-user data separation](https://source.android.com/docs/devices/admin/multi-user), [multi-user-aware components](https://source.android.com/docs/devices/admin/multiuser-apps), and [UserManager public API limits](https://developer.android.com/reference/android/os/UserManager).
