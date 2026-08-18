# DORA Alpha 1 — install, limitations and smoke guide

Status: internal candidate; fresh exact-head CI, independent re-review and physical smoke are open\
Date: 18 August 2026\
Governance commit: `81e9678a4eb490b49a5cb0b75c99693cbbf71b7c`\
Implementation commit: `a1f00f55b309fe0ff196d7d300f270d5717d26c6`\
Verified base: `223f31d87355596c8cb36576d2d94366eae9d620`

## Candidate artifact

| Field | Value |
|---|---|
| Application ID | `com.monumentogram.dora.bootstrap` (non-release) |
| Version | `0.1.0-alpha01-internal` (`versionCode=2`) |
| Local artifact | `android/app/build/outputs/apk/debug/app-debug.apk` |
| Size | `30,150,851` bytes |
| SHA-256 | `5b6f323206ecc434659a94d3e53ee01926831daacef33a9667f279f69d43163e` |
| Signing/distribution | local debug signing only; not a store or production build |

The digest was reproduced after checking out the exact implementation commit above and running
`:app:assembleDebug`. Build outputs are intentionally not committed.

## Build and install

Prerequisites are JDK 17 and Android SDK/Build Tools 36.0.0. Set task-local `ANDROID_HOME` and
`ANDROID_SDK_ROOT`; do not create or commit `local.properties`.

From `android/` on Windows:

```powershell
.\gradlew.bat spotlessCheck detekt :app:testDebugUnitTest :core:model:testDebugUnitTest `
  :app:compileDebugAndroidTestKotlin :app:lintDebug :app:assembleDebug
adb devices -l
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Use only an authorized physical test device. Confirm the APK digest before installation. This
candidate accepts only synthetic or non-sensitive test text; do not enter a real meeting, voice,
credential or unapproved personal data.

## Physical smoke record

Record device model, Android API, ABI, available storage, implementation commit and APK digest.
Then execute this flow without account, GMS configuration or network dependency:

1. Launch Dora Alpha and verify the internal-Alpha/non-sensitive-data warning is visible.
2. Tap the central recording action. It must show that recording/import is unavailable and must
   not request microphone access.
3. Create a conversation with a synthetic title, notes, manual summary and two manual tasks; save.
4. Force-stop the app from Android settings (or with `adb shell am force-stop
   com.monumentogram.dora.bootstrap`), relaunch it and verify the saved content is unchanged.
5. Edit the title and summary, save, force-stop/relaunch again and verify the edit persists.
6. Find the conversation from History using text from its notes, then mark a task complete; reopen
   the app and verify the completion state persists.
7. Start deletion, cancel once and verify no mutation. Confirm deletion on the second attempt;
   force-stop/relaunch and verify the conversation and its tasks remain absent.
8. Check Settings: audio, ASR, diarization, automatic results, models, cloud/account/sync and export
   must all remain visibly unavailable.

Any crash, permission prompt, fabricated automatic result, lost successful save, reappearing deleted
record or network/account requirement is a failure. Attach only sanitized logs; never attach entered
content. Until one physical profile passes every step, `ALPHA-007` and the Alpha DoD remain blocked.

## Local verification evidence

Environment: JDK 17, Android SDK 36 and Build Tools 36.0.0. No user-specific SDK path is part of the
evidence.

| Check | Outcome |
|---|---|
| Alpha/app JVM tests and `:core:model:testDebugUnitTest` | PASS |
| App Android test source compilation | PASS, including inaccessible primary/backup and task-state semantics cases; device execution NOT RUN |
| Repository `spotlessCheck` and `detekt` | PASS; zero findings |
| Exact-implementation documented app/core unit/androidTest-compile/lint/assemble graph | PASS; 197 tasks |
| App lint and debug assembly | PASS |
| `zipalign -c -P 16 -v 4` on candidate APK | PASS |
| APK native inventory versus `android/native-libs-allowlist.txt` | exact match: only `libandroidx.graphics.path.so` for four packaged ABIs |
| Dependency and source-manifest diff from verified base | no dependency change; no manifest change |
| Source manifest permission declarations | zero |
| Packaged microphone/network permission | absent |
| Changed-file credential signature scan | zero matches |
| Alpha source network/audio/permission API scan | zero matches |
| `tools/validate_stage00.py` and `tools/verify_apk_native_alignment.py` | NOT RUN locally: no functional Python interpreter; CI required |
| Android device/emulator execution and physical smoke | NOT RUN: `adb devices -l` returned no device; no emulator/system image was installed |
| GitHub CI and independent P0/P1 review | Pre-remediation HEAD `e4ea3e2` CI run `32107371424` PASS; provisional review found P0/P1/P2=`0/2/0`; both P1 fixes are in the implementation commit above, while fresh exact-head CI and re-review remain open |

The merged debug manifest contains the Android Gradle-generated app-scoped
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` plus baseline AndroidX Startup/Profile Installer
components. The source manifest and dependency graph did not change, and no microphone or network
permission exists. This is not a claim that the packaged manifest has zero nodes.

## Known limitations and non-claims

- The Alpha snapshot is bounded, disposable and stored app-private with `AtomicFile`; Dora does not
  encrypt it and makes no Room, SQLCipher, secure-vault, forensic-erasure or production-storage
  claim.
- Recording, import/playback, VAD, ASR, diarization, speaker identity, automatic summary/decision/
  task generation, models, account, cloud, sync, analytics, export and sharing are absent.
- Search is a bounded in-memory scan intended only for this internal Alpha dataset.
- The APK uses the non-release bootstrap identity and debug signing. It is not approved for public,
  store, customer or real-data distribution.
- Local green checks do not imply Stage 0 PASS, full Alpha DoD, production readiness or merge
  authorization.
