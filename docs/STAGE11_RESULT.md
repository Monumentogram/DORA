# Stage 11 — Privacy, offline and export Alpha

Status: **PASS / STAGE11_PRIVACY_OFFLINE_EXPORT_ALPHA_READY**.

| Part | Software verdict |
|---|---|
| 11.1 Privacy / deletion | PASS within accepted Alpha scope |
| 11.2 Offline | PASS, including functional RU/EN and isolated network boundary |
| 11.3 Export | PASS, current saved displayed text and explicit SAF save/cancel |

No open P0/P1 defect was identified in this scope. **DEVICE_VALIDATION_PENDING**
for physical acceptance of the final signed product APK; this software status is
not overall Alpha admission.

Base: `bbff0211b29f0bda43d4319c14943ae501ad4263`, dependent on open Draft PR #123.
No merge, Preview/Pilot installation or user 10 data access is authorized by this work.

## Privacy evidence and scope

Existing accepted Stage 10.3C evidence covers encrypted transcript/search storage, current edits,
selected versions and reopen. Search computes projections from SQLCipher in memory; it has no
plaintext index or persisted query history. The accepted per-audio deletion tests cover inventory,
per-run key removal and restart; Stage 11 adds whole-recording erasure, not a replacement cipher.

Source inspection: `allowBackup=false`; both backup and device-transfer XML exclude root, files,
databases, shared preferences, external and device-protected domains. Vault and Cloud cancellation
records use `noBackupFilesDir`; Keystore keys remain in their existing namespaces. Production
Activity and transcript dialog use FLAG_SECURE. Main app/core sources contain no transcript or
audio logging calls. Transport failures map to fixed categories rather than exception contents.
The sole application HTTP adapter is CloudHttpClient, entered by explicit Cloud actions. No
analytics or GMS dependency was found in these source entrypoints; runtime isolation evidence
must be reported separately from this static inspection.

Whole deletion keeps only the content-free Recovery ownership/deletion inventory needed to
prevent namespace reuse and retry cleanup. It must not remove shared wrapping/database keys.
No flash overwrite or AWS deletion receipt is implied. Existing Cloud retention remains in force;
external exported copies are outside local deletion scope.

## Execution notes

Initial build command hit PowerShell splitting of an unquoted Gradle property; retry quoted it.
The next attempt hit disk exhaustion while writing build state. Source/build moved to a dedicated
task checkout on D: rather than deleting historical artifacts or user data. These are build
environment failures, not test PASS or product failures.

## Implemented behavior

- Whole-recording deletion is separate from the existing audio-only contract. The
  existing durable deletion transaction erases transcript/version/edit payload and
  segmentation, then replays per-run audio/key cleanup. Interrupted cleanup stays
  discoverable and explicitly retryable. No database schema migration.
- Signal exposes RU/EN delete confirmation/cancel, pending-deletion status and errors.
- Explicit Cloud recognition checks OS connectivity before reading Cloud identity or
  creating a job. Offline refusal preserves audio and edits; no fake queued/sent state.
- Export uses Android CreateDocument (`text/plain`, local-only chooser), an in-memory
  UTF-8 snapshot of the displayed saved version and a background ContentResolver write.
  Unsaved edits must first be saved; a newer machine proposal never changes that rule.
  There is no plaintext staging file, public cache, broad storage permission or upload.
  Cancellation, picker failure and output failure have distinct messages. Only a
  successful write/flush/close reports success. Rotation retains the explicit request
  in a ViewModel; process death does not serialize transcript content.

## Privacy / deletion verification

POCO user 0, isolated `com.monumentogram.dora.audio.test`: **7 tests PASS** (96.311s).
New whole-deletion, interrupted-delete/reopen/retry and audio-only contract tests,
three accepted deletion-failure regressions, and the existing selected-text search
reopen test. A second recording, its audio and edited transcript survive deletion;
the target disappears from transcript history and search after reopening. Synthetic
transcript/edit canaries are absent from raw persisted files. UI cancel/confirm also
passed in the separate Stage 11 app. After a later full force-stop/relaunch, a manual
search for the deleted synthetic title returned no results; another recording with
its saved correction remained searchable. Shared keys and content-free Recovery ownership
remain intact; existing encrypted-audio evidence is reused.

Local Cloud cancellation markers are persisted before deleting job metadata. This
does not establish AWS erasure. Cloud retention/retries/consent remain existing contracts.

## Offline verification

The diagnostic build uses the same app sources/native pins with only a separate
debug application ID/label and removal of INTERNET. Package:
`com.monumentogram.dora.stage11`, POCO M5 Android 14 API 34, **user 0 only**.
Preview/Pilot were not updated, cleared or uninstalled; no user 10 source was accessed.

| Scenario | Evidence / result |
|---|---|
| Launch/library without network/account dependency | Isolated app launch and force-stop/relaunch PASS |
| Capture/Pause/Resume/Stop/audio save | Existing Signal recording flow PASS |
| Whisper Small q8_0 RU and EN | Two synthetic PCM inputs, one recognition each, both nonblank and editable; PASS (104.210s combined test) |
| Network boundary | INTERNET absent from actual diagnostic APK; valid UID TX counter unchanged across both local recognitions; source audit finds explicit Cloud entry only |
| View/edit/history/search | Existing encrypted contracts and Signal search/edit/recreate test PASS |
| Delete | Encrypted and UI cancellation/confirmation tests PASS |
| Export | Real DocumentsUI save/cancel and lifecycle test PASS; full version matrix PASS |
| Restart | Corrected native transcript visible after explicit force-stop/relaunch; search/reopen and deletion persistence PASS |
| Cloud with no network | Immediate understandable refusal, no job created, source preserved: PASS |
| Missing local model | Honest install message, no download, unchanged source: PASS before model provisioning |

The known RU accuracy limitation and historical EN deviation remain unchanged.
Functional recognition is not accuracy acceptance. There was no benchmark or ASR
root-cause investigation. No Google sign-in or registration was performed in the
isolated app. Isolation evidence is not a packet capture of the final Preview APK.

## Export verification

Real POCO DocumentsUI saved `DORA-stage11-synthetic.txt` in Downloads. Its 31 UTF-8
bytes exactly equal `Synthetic offline correction en`, the saved correction shown
in the editor, rather than the original machine paragraph. SHA-256:
`aa71601ba06b40687c3df1e54e401e1671dbd2b50162079ac7823b65751e849d`.
Success feedback was observed. A second chooser was cancelled; cancellation feedback
was observed and the existing exported file hash was unchanged. A recording without
a transcript has no export action. No external copy is removed by local deletion.

Five unit tests cover exact UTF-8/paragraphs/emoji, snapshot buffer clearing,
cancellation, orphan result, closed lifecycle, picker launch failure/retry and
open/write/close failures. Device lifecycle test covers recreation and retained
feedback. The separate real-editor version matrix uses a synthetic picker result;
its final rerun passed on POCO (30.218s), including Local, Cloud, saved edits, inactive preview, active selection, reprocessing and cancellation.

## Verification ledger and limitations

- Unit: **402 PASS** (app 123, core/audio 274, core/model 5), no skipped tests.
- Host: **181 PASS**: inventory 7, Preview artifact guards 17, version labels 4,
  PIN-free local access 3, versioning 44, Cloud provider 24, Cloud identity/auth 82.
- Gradle: ownerPreview assembly, lint and detekt PASS; strict-format/instrumentation
  rebuild PASS. Lint has zero errors (18 existing advisory warnings and four hints). Only applicable suites were selected.
- Physical runs and earlier harness failures are retained in local logs. Search's
  empty AnnotatedString comparison was narrowed to exact `.text`; the three-second
  replay test now controls Compose frames after actual AudioTrack replay was proven.
  Both affected tests passed on rerun. No player/search product behavior changed.
- The new export matrix initially used a stale generation-bound fixture session after
  editor load. Follow-up exposed the separate Opening and document-publication phases. The fixture now waits for both current session and displayed document; the complete final rerun passed.
- Device: **17 distinct applicable tests PASS** across the targeted runs: 7 encrypted-storage and 10 app/UI/native tests. Earlier failed harness runs are retained separately and are not counted as passes.
- Independent final static review: **APPROVE**, including a real re-review after the
  picker-error/rotation-feedback fixes and later test repairs. See
  [review record](evidence/stage11-independent-review.md). It does not assert CI/device PASS.
- Actual provider disk-full/fault injection and live network loss/reconnect during
  Cloud are not run. Output failures and existing Cloud retry/consent contracts are
  covered at unit/host level. No new persistent Cloud queue or backend change.
- Signed APK and audit PASS. Remote CI on implementation SHA `5b9be24487b2ee8ee951ebc3d52c376d796d48c5`:
  [Stage 11 software/contracts](https://github.com/Monumentogram/DORA/actions/runs/40949506510),
  [existing software/backend/emulators API 28 and 36](https://github.com/Monumentogram/DORA/actions/runs/40949550068),
  and PIN-free checks: **all PASS**. The broad existing PR workflow ran automatically;
  no additional ASR benchmark or manual project-wide campaign was requested.
  The final evidence-only commit leaves the verified Android/tooling sources unchanged.
- Draft PR: https://github.com/Monumentogram/DORA/pull/124, base PR #123; no merge.

Final product APK physical validation is **DEVICE_VALIDATION_PENDING**. Passing
isolated diagnostics does not authorize or replace updating/accepting user Preview.
Stage 11 software readiness does not grant overall Alpha acceptance or all 14 Cloud
exit criteria. Stage 12 should follow software closure, with physical acceptance
tracked separately; Stage 8.6, 10.5C and model-manager Stage 20 stay outside this work.

## Signed artifact

Implementation commit: `5b9be24487b2ee8ee951ebc3d52c376d796d48c5`.
Android source tree: `39cb3dee9bdb3a207bd629c96b59a837bc722b35`.
The report-only commit does not change these sources.

Owner-local file: `dora-stage11-owner-preview-vc6.apk` (84,096,581 bytes).
SHA-256: `1920d88f9d1b99f45e5167f8957020af00d8a459359b37b0c80b9bc1e9155da9`.
Package `com.monumentogram.dora.alpha.preview`; versionCode **6**; versionName
`0.1.0-owner-preview.6`. Existing signer certificate SHA-256:
`a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d`.

Signature match and ascending version PASS. Native libraries and runtime assets are
byte-identical to the accepted Preview baseline; Whisper weights remain external.
Actual APK audit checks backup exclusions, component/permission allowlists, absence
of operator/test/unlock code, compiled version label, 16-KB alignment and native
dependencies. The manifest changes are version fields plus ACCESS_NETWORK_STATE.
The audit needed UTF-8 mode on Windows to decode resource output; the initial host
decoding failure did not alter or re-sign the already verified artifact.
See [artifact receipt](evidence/stage11-apk.json) and
[verification ledger](evidence/stage11-verification.json).

This is source/signature/version compatibility evidence, not an executed upgrade
or proof of preservation of private Preview records. The APK was **not installed**
over Preview/Pilot. Physical approval of this final APK remains separately pending.
