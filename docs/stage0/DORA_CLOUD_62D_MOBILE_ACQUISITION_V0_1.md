# DORA 6.2D mobile acquisition: design and implementation plan

Owner authority: explicit request to build the described separate test application
for POCO, following the [eight-recording amendment](DORA_CLOUD_62D_EIGHT_CLIP_PROTOCOL_V0_1.md).
Source parent: `e0d1479333122166192b5f6a5f27813d9eb86f09` on
`chat/alpha-asr-runner-scope`. This is isolated acquisition tooling; Android product
runtime, Recovery, Stage5, PR86 and6.3 are outside scope. No release/admission claim.

## Goal and scope

Perform the complete eight-task capture/listen/actual-word verification flow on the
Owner's POCO. Reuse frozen first-two READ/SPONTANEOUS materials per RU/EN, existing
attestation and saved audio/reference/history. Exactly8 quality samples, no reserves,
no noisy tasks and no human timing requirement. RUWER<=20%, EN<=18%; noise/timestamp
accuracy NOT_EVALUATED and one-owner limitation unchanged. Long fixtures remain host
generated. This mobile package neither runs ASR nor uploads to AWS.

Recording is DEFERRED until the explicit chat message “Готов записывать”. Preparing,
installing or opening the app does not authorize microphone use or confirm words.
Initial input has `recording_enabled=false`. No readiness polling or reminders.
After the future explicit message, the host can create a seed-bound activation file;
the app has no self-service unlock, exported unlock intent or cloud authorization.
Android microphone permission is requested only following an explicit Record action
when acquisition has been enabled. Permission grant alone never starts capture.

## Architecture and privacy

- New application module `android/poc/owned-corpus`, package
  `com.monumentogram.dora.stage0.ownedcorpus`, separate from every existing DORA app.
- Kotlin/platform Activity and controls; semantic resource colors, readable Russian
  instructions and48dp targets. Pinned existing AGP/SDK/JVM/test dependencies only.
- Platform AudioRecord on a worker thread, PCM16 mono16000Hz, duration measured by
  samples. READ20–45s/SPONTANEOUS20–60s, automatic caps44/59s. Current mobile source
  and evaluation WAV are identical lossless PCM bytes; record a distinct native
  conversion recipe rather than falsely claiming the historical browser converter.
- Foreground-only explicit capture. Stop/background/interruption releases microphone;
  partial or failed acquisition is retained and identified, never silently replaces
  an accepted recording or resumes the microphone. No new Recovery admission.
- Internal app files only; backup/device transfer disabled, no INTERNET permission,
  analytics, account, network library or public exported data provider. Source files
  are private plaintext in the app sandbox, not a new encryption/vault guarantee.
- Private seed archive is delivered separately from the APK through local USB/ADB.
  APK/source/Git/test logs contain no actual authored inventory, recordings, words,
  participant identifiers or per-record hashes. Existing host original remains intact.
- Immutable seed, private state/reference revisions and audio; drafts are not gold.
  Explicit listened-and-verified confirmation is required for actual words. Editing
  a confirmed reference invalidates that draft's confirmation until reconfirmed.
- A private export archive is collected over USB/ADB by the host bridge. Owner does
  not rename/convert/hash files or edit manifests. Host preserves incoming archives,
  verifies seed/inventory/overlay/audio/reference bindings and current baseline,
  rejects traversal/duplicates/oversize/stale conflicts, imports idempotently, and
  retains the canonical CorpusStore history. No silent host/phone overwrite.

## Implementation plan

### Prospective replacements specific to mobile acquisition

| Historical requirement or mechanism | Mobile revision | Limit retained |
| --- | --- | --- |
| Desktop browser recorder and browser WAV conversion recipe | Separate native test APK; lossless PCM16 mono 16000Hz source/evaluation bytes with a distinct native recipe | Original browser captures and provenance remain unchanged |
| Invalid acquisition permanently blocks the fixed case without another attempt | Explicit technical retry is permitted only before any accepted capture, following interruption, invalid duration or format; every failed original and reason remains in private audit history | Exactly eight accepted cases; no accepted, quality-based or post-provider retakes; attempts are not extra scored samples |
| Desktop confirmation and local recorder files | Phone playback, editable actual words, explicit human confirmation, private USB import with immutable revisions and stale-host conflict rejection | No automatic truth confirmation; frozen READ scripts are not gold references |
| Browser recording readiness control | Initially disabled seed, then a separately bound activation after explicit Owner readiness | Opening/installing the app or granting permission never authorizes recording or AWS |

This changes acquisition logistics only. The eight-recording amendment's precise
replacements of 72 candidates, prior selection counts, reserves, noise tasks and
manual timing remain effective. Their historical requirements are not claimed met.

- [x] Add isolated module/build configuration and reproducible dependency locks.
- [x] Native private store/UI/capture/export with unit and synthetic instrumentation
  tests for deferred mic, exact8, explicit words, immutable files and interruptions.
- [x] Host seed/activation/export bridge, native conversion provenance and adversarial
  archive/conflict/idempotence tests; no actual activation in this preparation task.
- [x] Cross-check an Android-generated synthetic archive with the Python importer,
  verify all8 can complete without noise/timing and original files remain unchanged.
- [x] Run formatting/static analysis, unit tests, lint, debug assembly, Stage00,
  relevant Cloud tests, synthetic Android tests and emulator deferred-screen QA.
- [ ] Install only the separate utility on the connected POCO and import the private
  seed without microphone access or reference confirmation. Verify saved progress.
- [x] Independent review, privacy/allowlist/hash/parity checks.

### Verification outcome and remaining device action

Local Android verification passed: formatting, Detekt, lint, two native unit tests,
debug APK/test APK and the required existing application/core checks (276 tasks).
API36 emulator: all seven synthetic instrumentation tests passed, including an
actual native export of eight distinct synthetic WAVs. The Windows USB bridge
transferred exact bytes, the Python importer admitted all eight, exact replay was
idempotent and canonical finalization required no noise/timing artifacts. Windows
ADB raw payload transfer is separate from shell exit-status checks because shell
stdout can convert LF to CRLF.

Offline host corpus/server/mobile/USB tests: 41 passed; preparation/report guards:
10 passed; frozen Cloud harness: 26 passed; AWS offline tests: 13 passed; Stage00:
7/7 passed. These are local results; CI is not represented as verified here.
No real microphone or acoustic quality test was performed. Emulator behavior does
not establish POCO microphone behavior, other Android versions or any ASR quality.

POCO installation is **pending Android system confirmation**: installation returned
`INSTALL_FAILED_USER_RESTRICTED`. No alternative installation route was attempted.
The disabled private input is prepared outside Git, with existing consent and one
recording retained; all ten preexisting private files remained byte-identical.
Nothing was transferred into the phone's app sandbox while installation was blocked.
The APK is prepared for delivery; the private input is deliberately separate.
The Owner's installation confirmation is unrelated to future recording readiness.

The [machine evidence](DORA_CLOUD_62D_MOBILE_EVIDENCE_V0_1.md) binds the final sources,
APK digest and bounded test outcomes. Publication and Sheet verification are reported
with the exact commit in the task handoff; they do not promote Phase A or admission.

## Review focus

1. Stale or concurrent desktop/phone edits must be rejected or retained as explicit
   revisions; a duplicate import must not duplicate capture or confirmation events.
2. Process death, storage failure or navigation during capture preserves a recoverable
   partial attempt and cannot promote a malformed/truncated WAV or start mic again.
3. Crafted archive paths, hashes, IDs or ZIP expansion cannot read/write outside the
   private root, admit extra clips, infer speaker consent or bypass deferral.
4. Existing browser source/evaluation/audio hashes remain unchanged; native capture
   provenance must be truthful and bound before future prospective Phase A.
5. Device QA uses synthetic fixtures only and must not clear real corpus app data,
   invoke real mic, accept human truth, or touch existing DORA/Recovery applications.

## Operator handoff

The APK contains code/resources only. Transfer its private input separately; never
embed a seed in assets, publish a corpus ZIP or place private audio in Downloads.
`tools.cloud62d_owned.mobile_bridge seed` creates a disabled package and
`tools.cloud62d_owned.mobile_device seed` delivers it through the package's private
USB channel. Installation does not request microphone permission. A phone-side
Android installation restriction requires the Owner's system confirmation; it is
not recording readiness and must not be bypassed.

Only after the explicit future readiness message: record readiness in the canonical
store, create the bound activation with the bridge, deliver it with the device
helper's explicit readiness switch, and use the app's refresh action. The Owner
starts each missing recording, listens, edits the actual words and confirms them.
Existing accepted audio is reused. The UI separately counts saved recordings and
verified references; completion requires eight of each. USB is only needed for
the initial transfer, activation and collection, not while completing tasks.

Export has a durable monotonic revision. Collection retains interrupted transfer
attempts and atomically publishes complete bytes. Import rejects older previously
unimported revisions, allows exact-package replay, archives prior gold and revokes
active gold when the phone exports an unconfirmed correction. Reconfirmation is a
new human revision. Rejected attempts, drafts and audit history stay in the private
archive and cannot become evaluated samples or public evidence.

On completion, collect with the device helper and import using the bridge. The
canonical host tool generates names, hashes, manifests and optional long fixtures.
No microphone, recording, reference confirmation, AWS action or scheduled reminder
is triggered by any transfer or installation operation.

## Frozen downstream boundaries

Published Phase A v0.1 and the eight-recording amendment remain historical immutable
protocol records. This mobile acquisition revision adds a capture/transfer mechanism,
not new samples, changed thresholds or an execution-ready Phase A. A full successor
still requires the completed real corpus, verified AWS configuration/preflight and
exact live operator/cost binding; publish/push/refetch before first AWS call. USD10,
privacy/retention/cleanup and no6.3 remain mandatory. AWS evaluation NOT_RUN.

Platform implementation follows the official [AudioRecord API](https://developer.android.com/reference/android/media/AudioRecord)
and [app-specific storage guidance](https://developer.android.com/training/data-storage/app-specific).
No new production storage/capture dependency is admitted by this test utility.
