# Permanent Whisper on POCO — final device result, 2026-10-29

**PASS / OWNER_DEVICE_PERMANENT_WHISPER_READY**

**PERMANENT_WHISPER_DEVICE_PASS + EXISTING_FUNCTIONS_PRESERVED + AMAZON_UNCHANGED + SIGNAL_UI_READY_FOR_IMPLEMENTATION**

This verdict covers the actual Owner Preview update and bounded Alpha functional
acceptance below. It does not certify general ASR accuracy, every repository CI job,
or an implemented Signal UI. Historical reports remain unchanged.

## A–G outcome

| Stage | Status | Evidence and scope |
|---|---|---|
| A | PASS | PERMANENT_LOCAL_MODEL_CODE_READY: calendar gate removed; size/SHA-256, atomic import, cancellation and retention boundaries preserved; controlled-clock regression tests pass. |
| B | PASS | POCO connected, correct user/package/source/signer identified; unlocked preflight and phone-model admission verified. |
| C | PASS | Original-key-signed minimal backport, same applicationId/versionCode/storage/native runtime; exact installed APK digest verified. |
| D | PASS | OWNER_DEVICE_PERMANENT_WHISPER_READY: real update, old audio/text/edits/history preserved, actual RU/EN local ASR, cancellation and reopen pass. |
| E | PASS | Independent review package and evidence prepared; PR119 remains draft and unmerged. Generic CI failures below remain explicit, not waived by this status. |
| F | PASS | SIGNAL_UI_IMPLEMENTATION_READY: isolated presentation migration plan, seven-screen mapping and real-PCM waveform specification; no redesigned APK. |
| G | PASS | CLOUD_ASR_UNCHANGED: backend/contracts pass, AWS code/infrastructure/auth unchanged; no audio upload or live AWS operation. |

Prior source/build details: [full A–G report](https://github.com/Monumentogram/DORA/blob/e967fa4d10f66a318b066a3fe185a1c50f879708/docs/PERMANENT_WHISPER_SIGNAL_RESULT_2026-10-28.md),
[preflight](PERMANENT_WHISPER_DEVICE_PREFLIGHT_2026-10-29.md),
[upgrade/restoration runbook](PERMANENT_WHISPER_DEVICE_UPGRADE.md).

## Source, artifact and installation

- Working Cloud base: `codex/stage18-1e-live-deployment`,
  `59ca965e35a305ece8b6e17e0d679bf199a2b264` (not assumed to be main).
- [PR119](https://github.com/Monumentogram/DORA/pull/119),
  `stage/9-permanent-whisper-model`: accepted input
  `4edf687352968c40017242608e4a85f64d43a467`; current final head
  `e967fa4d10f66a318b066a3fe185a1c50f879708`.
- Installed baseline source: `98a975f3aff268db605573e1cd666dbc237f3123`.
  Minimal device branch: `stage/9-permanent-whisper-device`; final APK application
  source **`acfd8a7743d8a7f414eb8f1f5bdce77fcd492289`**. Subsequent commits add
  documentation only. Android source diff from that application commit is empty.
- Package `com.monumentogram.dora.alpha.preview`, code **4**, name
  **0.1.0-owner-preview.4**, minSdk28, targetSdk36, arm64-v8a. Version text remains
  the same; the APK digest and update timestamp identify this update.
- Candidate and installed SHA-256:
  `284db30f699c53500ea15d8cb2db7a15f45dd581ad20fece20114001d89e4095`.
  Original APK SHA-256:
  `0370f1999fcdcdf04d116c58a07ddea212701fc1afc463b0e601daee3ea850a1`.
- Both APK signatures verify with the same certificate SHA-256:
  `a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d`.
  Original signing key remains private and unchanged. Ten native libraries,
  manifest, runtime assets and 16-KiB alignment passed prior binary audit; actual
  recognition now verifies native execution on POCO too. Weights are absent from APK/Git.
- Owner explicitly approved this exact APK. Two attempts with foreground user10
  returned `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` and left the
  original APK unchanged. Historical installation evidence identified Xiaomi's
  confirmation in foreground user0. Owner separately authorized temporary
  foreground switching; the third `install --user 10 -r` returned **Success**.
- Returned to user10; owner unlocked it. Exact on-device APK hash matches the
  candidate; package is installed=true for user10 and installed=false for user0.
  Last update: 2026-10-29 08:49:22 Moscow. User0 apps/data/security settings were
  not changed. No uninstall, clear-data, reset, downgrade, signer/key change or
  model import/deletion occurred.

The installed baseline predates Cloud/transcript-codec additions in the PR base.
Backporting only the model fix avoided introducing those unrelated changes into
the owner's working installation.

## Actual preservation and ASR checks

| Check | Status | Observed result |
|---|---|---|
| Launch in user10 | PASS | Updated app opens; opens again after idle process stop/relaunch. |
| Existing recordings/originals | PASS | Same two identities and lengths; full authenticated readback of 215,200 frames/3 blocks and 280,800 frames/5 blocks before update, after update and after relaunch. |
| Existing transcripts/history | PASS | Two documents, four versions; all four opened after update. Original timestamps, machine/rendered text hashes and active-version labels match preflight. |
| Saved manual edits | PASS | Existing edited active text preserved exactly; reopened again after relaunch. No old transcript version was added or edited during post-update tests. |
| Phone model | PASS | App's pinned size/SHA-256 admission and real native inference succeed using the existing model. No model was replaced. |
| RU local ASR | PASS | Fresh microphone recording of Windows Irina synthetic speech; real recognized text contains the entire known Russian phrase. |
| EN local ASR | PASS | Fresh microphone recording of Windows Zira synthetic speech with English selected; trimmed recognized text equals the known English phrase. |
| Offline application operation | PASS | Actual RU/EN inference in the installed APK without `android.permission.INTERNET`, confirmed in APK permissions and installed package permissions. No network/radio settings changed. This is application-level no-network evidence, not an airplane-mode test. |
| Cancellation | PASS | Cancel during visible Recognition yields cancelled state and no additional version; persisted document still has two versions after reopen/relaunch. |
| AWS contracts | PASS | Existing backend/software CI passes; no AWS infrastructure, IAM/S3/KMS/HTTPS, auth or Cloud code changed. No live AWS call. |

The owner explicitly placed POCO beside the computer speaker and approved two
test recordings. Both were recorded through the built-in microphone and encrypted
normally, not injected into app-private storage. RU: 489,600 frames (30.6s);
EN: 660,800 frames (41.3s), including setup/silence around the short phrases.

Known phrases:

- RU: «Это проверка локального распознавания. Сегодня мы сохраняем важную запись.»
- EN: “This is a local speech recognition test. Today we save an important recording.”

Quality is distinct from execution. **EN known-phrase quality PASS** for this one
clip; no general benchmark claim. **RU known phrase PASS**, but **full-clip quality
BLOCKED / incomplete ground truth**: additional recognized speech was present and
the owner said they may have spoken it. It is not established as hallucination or
an ASR defect; the initial suspicion was corrected. No extra speech is published.

The first post-update cancellation request arrived after the commit boundary;
the app correctly finished saving a second version on the new EN test recording.
A subsequent attempt was cancelled during Recognition, leaving those two versions
unchanged. Native compute subphase/latency is not asserted. Old records were not
reprocessed after updating. Four total recordings remain: two originals plus two
authorized test fixtures; seven total versions: four originals plus RU1/EN2.
No test data was deleted. Original and new transcripts reopen after app relaunch.

Private XML snapshots, comparison hashes, actual transcripts, APK/signature outputs
and assertion scripts remain under
`%LOCALAPPDATA%/DORA/permanent-whisper-20261028/device-preflight-20261029/`.
`verify_preservation.py` and `verify_acceptance.py` pass. Public receipt:
[device acceptance](evidence/permanent-whisper-20261029/device-acceptance.json).

## Calendar, code checks and remaining CI failures

No phone/system clock was changed. The existing managed-clock tests cover the old
boundary, 2026-11-04 and 2100-01-01, including import across the boundary. They fail
on the old installed source and pass on the permanent-model source. Production
model/runtime code no longer consumes a calendar deadline; this is not a claim
of physical future-date testing. No replacement expiration date was added.

Previously completed local gates remain applicable: PR app117/audio264/model5 and
backend55 tests; device OwnerPreview108/audio255 tests; formatting, Detekt, Lint,
and APK builds pass. No code was changed or redundant test campaign run today.

On exact PR head `e967fa4d10f66a318b066a3fe185a1c50f879708`, checked 2026-10-29:

- [Permanent Whisper Android CI](https://github.com/Monumentogram/DORA/actions/runs/40752916044): **PASS**.
- [Software/backend and API28/API36 emulator CI](https://github.com/Monumentogram/DORA/actions/runs/40752924708): **PASS**, all four jobs completed successfully.
- [Generic Android CI](https://github.com/Monumentogram/DORA/actions/runs/40752916145): **FAIL**. Bootstrap has the previously recorded `Product identity differs from 7.3`.
  Both encrypted-persistence jobs also fail with
  `ProtectedReadOnlyVaultTest#fullSuccessor394UsesPinnedEntryAndRejectsCatalogOwnershipDrift`
  reported failed or unexpectedly skipped; the subsequent protected-diagnostic
  receipt artifact is absent. This additional failure is now recorded explicitly.
  Its root cause is not proven by the bounded job logs. Do not characterize all
  CI failures as merely the identity check. No unrelated fixture/identity repair,
  Recovery rerun or merge was performed. Independent investigation is still needed
  before claiming generic CI green.

## Changed files, Signal and next step

Device application diff from the installed baseline contains only:

- `android/app/src/main/kotlin/com/monumentogram/dora/transcript/LocalAsrModel.kt`
- `android/app/src/main/kotlin/com/monumentogram/dora/transcript/ModelImportController.kt`
- `android/app/src/main/kotlin/com/monumentogram/dora/transcript/TranscriptScreen.kt`
- `android/app/src/main/kotlin/com/monumentogram/dora/transcript/WhisperNative.kt`
- `android/app/src/test/kotlin/com/monumentogram/dora/transcript/PermanentModelCalendarTest.kt`
- `android/app/src/test/kotlin/com/monumentogram/dora/transcript/PinnedModelStoreTest.kt`

This installation turn adds this report and its sanitized evidence JSON. The
earlier preflight commit is `f8f410310fc26bc040bc02cd6a9ced14445397ff`. No Android,
backend or Signal implementation file changed during device acceptance.

Signal branch `stage/10-signal-ui-preparation`,
`42423cf0070d607f7ad7911996bf64a2ef1e7301`, starts from the correct working Cloud
base. Its [implementation plan](https://github.com/Monumentogram/DORA/blob/42423cf0070d607f7ad7911996bf64a2ef1e7301/docs/superpowers/plans/2026-10-28-signal-ui.md)
maps seven screens and specifies a bounded real-PCM peak stream without blocking
or modifying capture. Palette and 164-peak reference density are documented;
responsive density, pause, load throttling and up-to-60FPS presentation are covered.
Unimplemented Stage10 features remain labeled as such. Figma exact visual inspection
is still quota-blocked; no unconfirmed layout or assets are represented as approved.

Next practical step: independent PR119 integration decision and separate triage of
the generic CI failures, then implement Signal in its isolated branch. The POCO
already has the permanent local model fix; it does not depend on that merge or on
the separately tracked direct-HTTPS timeout to AWS. No automatic/background work
is scheduled. The old expiring APK must not be restored/launched at or after
2026-11-03 00:00 Moscow; use a same-key forward repair if needed.
