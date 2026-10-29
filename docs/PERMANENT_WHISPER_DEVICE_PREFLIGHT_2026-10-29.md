# Owner Preview device preflight — 2026-10-29

This is new evidence after the owner unlocked the POCO. It supplements, without
rewriting, the [2026-10-28 handoff](PERMANENT_WHISPER_FINAL_CI_HANDOFF.md) and
[upgrade runbook](PERMANENT_WHISPER_DEVICE_UPGRADE.md).

**B PASS / DEVICE_PREFLIGHT_READY. D BLOCKED / OWNER_INSTALL_APPROVAL_REQUIRED.**
No APK has been installed in this session. No updated-device success is claimed.

## Identity and artifact checks

- Explicitly selected the previously verified POCO serial; a separate emulator was
  also connected. Current Android user is 10, unlocked, with Owner Preview in the
  foreground. No user switch was needed in this session.
- Package `com.monumentogram.dora.alpha.preview`, versionCode `4`, versionName
  `0.1.0-owner-preview.4`; last update remains 2026-10-27. User 10 installed=true,
  user 0 installed=false. Only these two Android users exist.
- Pulled the currently installed APK again. SHA-256 is unchanged:
  `0370f1999fcdcdf04d116c58a07ddea212701fc1afc463b0e601daee3ea850a1`.
- Rehashed candidate `dora-preview-permanent-whisper-vc4.apk`:
  `284db30f699c53500ea15d8cb2db7a15f45dd581ad20fece20114001d89e4095`.
- Reverified both APK signatures with apksigner. Both use certificate SHA-256
  `a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d`.
  An initial command had an invalid JAVA_HOME; corrected to the installed JDK17
  and both actual verification commands succeeded.
- Candidate badging confirms the same package/code/name, minSdk28, targetSdk36,
  arm64-v8a. Existing signing key and native-runtime evidence remain in the runbook.
- APK application source is still `acfd8a7743d8a7f414eb8f1f5bdce77fcd492289`,
  on `stage/9-permanent-whisper-device`, based on the installed source
  `98a975f3aff268db605573e1cd666dbc237f3123`. This session changes documentation only.

## Existing data baseline: PASS

Used existing app controls, without run-as, data export, instrumentation replacement
or encryption changes. The History navigation tab is a placeholder in this build;
the working saved-recording and transcript controls are on the recording screen.

- Two finalized recordings were listed; no further page was present.
- Both passed the app's full authenticated original-audio readback:
  215,200 frames / 3 blocks and 280,800 frames / 5 blocks.
- Transcript library contains two documents. The first has one saved version;
  the second has three. All four versions open. Version 1 is active in each document.
- The second document's active version has saved manual edits. Machine and edited
  text hashes differ, as expected. Other versions were previewed without changing
  the active selection or saving edits.
- Private UI evidence and a comparison baseline contain version timestamps,
  machine/rendered text hashes and active-version labels. They are stored only in
  `%LOCALAPPDATA%/DORA/permanent-whisper-20261028/device-preflight-20261029/`.
  No transcript content, recording identifiers or device serial are committed.

## Installed model and cancellation: PASS, scoped pre-update check

The installed UI has no independent model-status action. Started one local
recognition attempt on the first saved recording, observed `Распознавание`, then
used `Отменить обработку` before any result publication. The existing code reaches
this state only after model size/SHA-256 admission and native availability succeed.
This proves admission of the phone's pinned model, rather than merely the host copy.
The model was not imported, replaced or deleted.

The app reported `Распознавание отменено. Сохранённые версии не изменены.` Reopened
the persisted transcript and compared it with the private baseline: same one
version, timestamp, active label, machine text and rendered text. No new transcript
version or edit was created. The cancelled attempt can update internal attempt
metadata; this check does not claim the entire encrypted store is byte-identical.

This is not a completed RU/EN decode or a post-update cancellation test. Those,
offline operation and updated-APK data preservation remain unperformed. No network
settings or system clock were changed, and no audio was sent to AWS.

## Installation decision still required

The app was left idle, with no recording, ASR or import running. The same-code,
same-signer original APK is retained. It is not a backup of encrypted user data.
The runbook's separately approved non-destructive APK restoration applies only
before 2026-11-03 00:00 Moscow; at/after that cutoff use forward repair retaining
the permanent-model fix, because the original APK can remove the model by date.

Next: obtain explicit owner approval for this exact candidate, then recheck current
user/package/idle state and use the runbook's `install --user 10 -r` command. Stop on
any installation rejection; never uninstall, clear data, change signer or use `-d`.
After installation, compare both originals and all baseline transcript versions,
then perform actual RU/EN, offline and cancellation acceptance checks.

A/C/E/F/G retain their prior scoped results. D remains BLOCKED pending installation
approval and actual post-update acceptance. No merge, AWS change or UI implementation
was performed. This document is the only repository file added in this session.
