# ARM82 parallel Alpha — future physical acceptance protocol

**PREPARATION ONLY. New Owner authorization required. No POCO action performed.**

Authority: Owner request 2026-10-27 and ADR-0024. This protocol installs a NEW
package; it never updates, launches, stops, uninstalls, migrates or clears Pilot v2.
Old same-package update protocols are not applicable to this campaign.

## Exact candidate and approval

Preview: `com.monumentogram.dora.alpha.preview`, versionCode4,
`0.1.0-owner-preview.4`, ARM64 only, visible `DORA Owner Preview`.
Certificate SHA256:
`a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d`.
Copy the exact source SHA, APK SHA256/size/private path, independent review and
CI links from the completed build report into the approval record. Filename or
version alone is insufficient. Require `PARALLEL_ARM64_ALPHA_READY_FOR_OWNER_APPROVAL`.

Obtain a NEW explicit Owner authorization naming these exact bytes, this protocol,
the privately recorded POCO M5 serial, full Secondary Android user10/serial10,
read-only package preflight, one NEW Preview installation, ordinary UI model import
and the single <=60-second recording/ASR/edit/restart/rerun cycle below. It must
separately name a model staging location and any cleanup permitted there. This
document and software PASS are not authorization. If any requirement fails: STOP.

## Fresh preflight after authorization

1. Select only the exact privately approved ADB serial. Never unqualified ADB,
   first-device selection, guessed serial, bulk runner or historical operator script.
   Confirm POCO M5 identity, firmware, API34/Android14 and ARM64 against the accepted
   private record. ABI alone is not proof of ARM82 instruction support.
2. Require current user10 already available/unlocked and the full Secondary
   user10/serial10 identity. Owner interaction must establish this state beforehand.
   No automated switching, starting, creating, removing users or changing credentials.
   Do not query user0 data or inspect historical stores.
3. Read only installed Pilot package metadata/code identity for user10. Require
   `com.monumentogram.dora.alpha.pilot`, code2, `0.1.0-owner-alpha.2`, approved
   certificate above, expected UID and base-code hash from the private v2 receipt
   (`6999c48467fc978a8e14cb23bfc0f33f6211c5fedb44c24d29501e39df6a2305`).
   Verify signer locally from exact code-path APK bytes if needed; never open its
   data directory or invoke its Activity/provider/service. Record UID, version,
   signer, base hash/path, firstInstallTime and lastUpdateTime before mutation.
4. Inspect Pilot process/service state through package/process metadata only.
   If recording, inference, instrumentation, installation, recovery or any ambiguous
   active work is present, STOP; never force-stop or launch Pilot to obtain readiness.
5. Confirm Preview is not registered/installed for any Android user using global
   package metadata (no user0 data queries). Existing Preview means STOP: do not
   use replace/update, uninstall, downgrade, clear or install-existing as a workaround.
   Preview package, declared permissions and authorities must differ from Pilot.
6. Check free internal storage. Require at least2GiB available before installation
   (conservative preparation threshold, not a measured storage guarantee), covering
   APK/extraction, 264464607-byte import plus staging copy and bounded encrypted
   recording. Use ordinary UI low-space errors honestly; no deletion to make room.
7. Recheck local exact signed APK, nondebuggable Manifest, disabled backup/transfer,
   no shared UID/operator/bypass, native inventory and source/build receipt. Confirm
   model SHA/size and unexpired admission before transfer and again before ASR.
   Stop after 2026-11-02 inclusive Moscow unless admission is separately renewed;
   exclusive cutoff is `2026-11-02T21:00:00Z`.

## New-package installation and model import

After preflight GO and the new approval, install only the exact approved Preview:
`adb -s <approved-serial> install --user 10 <exact-approved-preview-apk>`.
No `-r`, `-d`, `-t`, package replacement, automated retry or fallback. On error STOP.
Android code registration is global: `--user 10` alone is not isolation proof;
the distinct package and absence of shared UID are required.

Read Preview's installed identity/signature/code hash/UID/user membership and verify
it is installed for user10 only. Its UID/data directory must differ from Pilot.
Recheck Pilot metadata immediately; all recorded fields must be unchanged. Do not
attempt cross-package file or Keystore probes on the phone: those are emulator tests.

Owner opens normal Preview MainActivity in the already selected user10. Confirm
`DORA Owner Preview 0.1.0-owner-preview.4 (4)`, fresh empty history, no application
PIN/biometric gate, no automatic microphone/ASR. Missing model is an honest state.
Import only via the normal document picker. The model is Whisper SMALL q8:
264464607 bytes, SHA256
`49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f`.
Use only the separately approved user10 document-provider staging location; never
assume `/sdcard` refers to user10. If proving location ownership requires user0 or
historical access, STOP. No root/run-as/direct vault injection or diagnostic intents.

## One bounded functional cycle

Use Owner-approved synthetic/public speech, no private conversation or bystanders.
Make ONE recording <=60seconds total, with a short RU sentence followed by an EN
sentence (pause/resume between them). Product cap remains300seconds.

1. Start manually, speak RU, Pause, Resume, speak EN, Stop and save. Verify ordinary
   finalized recording/readback UI. Keep recording identifiers private.
2. Close Preview normally and reopen it. The recording remains available, no capture
   starts automatically, and no key recreation, repair or recovery action is needed.
3. Start genuine local ASR through UI. Require queued/running/completed and intelligible
   recognition of both known RU and EN portions. If language selection is required,
   run the same recording in each supported language through normal UI; never fake
   completion or infer phone success from emulator evidence. If either language
   fails, physical bilingual acceptance is NOT_PASS. Do not create a second recording
   without a separate amendment to the one-recording scope.
4. Open the text, make one visible correction, save it as a separate version. Switch
   between original machine text and edit. Confirm active version matches UI selection.
5. Close/reopen Preview normally and verify audio, machine text, saved edit/history
   and selected version persist. Repeat ASR on the same audio. A new machine version
   must appear without deleting old versions or silently overwriting the correction.
   Explicitly select a version and reopen again to check persistence.
6. Native crash, unsupported CPU, corrupt storage, missing key, loss of old versions
   or inaccurate completion status means STOP. Do not retry with bypasses or erase data.

## Preservation and report

Compare Pilot v2 version/signer/UID/code hash/path/install timestamps/user10 membership
with the initial record; require unchanged. Never launch/stop Pilot, read/decrypt its
data, or touch LONG-01/LONG-02, Recovery or user0. Metadata equality is not a complete
content-integrity audit. Keep raw evidence private; publish only hashes/counts/status.

Remove only the newly staged model copy if its exact cleanup was separately approved.
No app uninstall, data clear, security changes or historical repair are part of this
physical protocol. Keep Preview installed pending Owner review.

`ARM82_DEVICE_RUNTIME = NOT_RUN` until this physical cycle actually executes; record
PASS/FAIL with exact source/APK/device identity afterward. Software preparation does
not close Stage8.6, production distribution, endurance or v2-to-v4 upgrade qualification.
