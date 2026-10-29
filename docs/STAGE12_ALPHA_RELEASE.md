# Stage 12 security / integrated Alpha and Samsung release-track

Owner scope: 2026-10-30. Base `5957acb6e54f733ae5224c27b73a4b55eea4360e`.
This document records the release contract; executed results are in `evidence/stage12-verification.json`.

## Executed software qualification

Implementation `425e4192ae1db341dfb7b8856f5fc3f614719394`: 402 JVM tests, 183 host checks and 64 backend tests passed. Android16/API36 emulator: eight regression methods and two upgrade methods passed without skips, with repeat verification after reboot and in airplane mode. The upgrade fixture retained encrypted audio, Local/Cloud text and edits, selected version, endpoint/installation ID, the original Keystore proof key and imported model. It used baseline v6 from accepted Stage11 and v7 with the same disposable instrumentation signer. This is real APK replacement evidence, **not** owner-signed ARM64 installation or POCO user10 validation.

Two clean, strict-dependency, no-build-cache owner builds produced the same signed 84,096,581-byte APK. SHA-256: `f3be7bfce2c491436d54238b6424b664d99c95babe5f64959dd2b3a864aa3001`. Package/signature match Preview6; all 32 native/assets entries are identical. ELF and ZIP16KB checks passed. No main/core source, schema or storage-format change occurred. Test provider, test fixtures and Whisper weights are absent from this APK. [Artifact audit](evidence/stage12-apk.json) records its own conservative distribution block: it cannot grant software readiness. The independent review, final CI and published release receipt grant the separate distribution gate.

Fresh Cloud fault tests use the real encrypted repository with a synthetic remote transport; API28/API36 mandatory CI also exercises the signed HTTPS fixture. Actual direct live RU/EN Cloud and cleanup evidence is inherited from Stage9.2R; native offline RU/EN execution is inherited from Stage11 with identical payloads. Neither was re-run on the final ARM64 v7. No new ASR quality claim is made.

Final CI and publication state are linked from [Draft PR126](https://github.com/Monumentogram/DORA/pull/126); publication is gated on all required checks and independent review. Download and phone-only installation instructions are in the [owner Alpha release](https://github.com/Monumentogram/DORA/releases/tag/personal-alpha-s22-ultra-20261030-v7). Before-public-release AWS verification is tracked in [Issue125](https://github.com/Monumentogram/DORA/issues/125). This release does not close full Stage12 or physical acceptance.

## Scope reconciliation

Technical Plan В§Stage12 is **security**, with key-loss/backup/tenant/deletion/redaction/supply-chain evidence and runbooks. ADR-0010 and the accepted first-Alpha scope also send integrated acceptance and all 14 Cloud exits to Stage12. вЂњAlpha Daily-Use ReadinessвЂќ is therefore a separate Samsung release-track, not a silent rename or a waiver of these requirements.

The overlap implemented here is bounded failure recovery, encrypted data continuity, export failures and release identity qualification. Stage12 remains **PARTIAL** until its complete security/integrated evidence is accepted. Samsung can independently reach software artifact readiness with **SAMSUNG_DEVICE_VALIDATION_PENDING**. This is an owner Alpha, not public-service admission.

GitHub readback on 2026-10-30: Draft #124 is OPEN at the exact base above and depends on Draft #123 (`bbff0211b29f0bda43d4319c14943ae501ad4263`, base `stage/9.3-permanent-cloud-access`). Neither is merged. This successor targets #124's branch. Open Issue #110 concerns the already implemented ADR-0021; it is not an instruction to restore PIN prompts.

## Data and privacy contract

| Data/path | Protection and release policy |
|---|---|
| Audio | Existing encrypted files and per-run keys; explicit deletion only; 300-second capture limit retained |
| Transcript, edits, history, selection | Existing SQLCipher schema and append-only version rules; no schema or migration delta |
| Search | In-memory projection; no plaintext index/query history |
| Keys | Existing UID-scoped Android Keystore and aliases; no reset, rotation or replacement in this release |
| Model | Existing pinned app-private imported file, preserved across updates; Whisper weights absent from APK/Git/release assets; accepted small VAD model remains embedded |
| Cloud identity | Existing installation ID, endpoint and non-exportable proof key; no AWS credentials in APK |
| Export | Explicit selected saved version, UTF-8, user-chosen SAF destination; external copy is outside app deletion |
| Backup/transfer | Disabled/excluded by existing manifest and backup XML; reinstall/device transfer is not a restoration method |
| Diagnostics | Fixed failure categories; no private recordings, transcripts, tokens or keys in public receipts |

Relevant threats are accidental overwrite on retry/update, lost acknowledgement causing duplicate result, export falsely reporting success, deletion resurrecting content and secret leakage through release packaging. Existing reducer/storage/authZ boundaries remain; new tests exercise the first three and existing deletion/ownership regressions cover the others. This bounded review is not a signed repository-wide threat-model or penetration-test completion claim.

## Compatibility

Application ID `com.monumentogram.dora.alpha.preview`; versionCode **7**; versionName `0.1.0-owner-preview.7`. Owner certificate SHA-256 must remain `a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d`. Production and Pilot identities are unchanged.

API floor28, compile/target36, ARM64 production payload. APK audit must verify each native library, ELF/ZIP16KB alignment, exact native/assets equality to Preview6, release flags and permissions. No new native dependency is admitted. Actual Samsung Galaxy S22 Ultra / Android16 hardware acceptance is pending; emulator API36 and historical POCO evidence do not replace it.

Recording starts from a visible user action with microphone permission and a microphone foreground service. Android16 applies job quotas even when jobs originate in a foreground service; this release retains the existing manual foreground recognition flow. No boot-time microphone restart or new background scheduler is added. References: [Android foreground-service changes](https://developer.android.com/develop/background-work/services/fgs/changes), [16KB guidance](https://developer.android.com/guide/practices/page-sizes).

## AWS deletion and incident runbook

Local whole-recording deletion persists cancellation intent and removes local content; it is **not** an AWS-erasure receipt. ADR-0011/0012 retain the policy: controlled audio immediate cleanup after successful ingestion, hard24h maximum; transient result immediate after ingestion/failed24h; ordinary metadata30d, audit90d; non-audio backups at most30d and guarded tombstones120d. These are policy, not newly measured retention guarantees. Provider-internal physical erasure is not promised.

Existing backend service/adapter/watchdog mechanisms and their tests are retained. The Stage9.2R historical report records two completed RU/EN jobs, absent Transcribe jobs and empty input/output buckets at its measured time. This task does not represent that snapshot as current live proof or mutate existing AWS resources. End-to-end current deletion/revocation, credential-loss and deadline evidence must be tracked before public use.

On a suspected Cloud leak or cancellation failure: stop new Cloud requests, preserve content-free action identifiers in private custody, use the existing service operator's scoped cancel/cleanup procedure, and verify the named job/object and cleanup receipt before declaring erasure. Local capture/export remains available. Do not rotate server or installation keys blindly: preserve the old custody material and restoration plan, reconcile active actions and test a separately authorized replacement before revocation. A lost Android vault key is not repaired by generating a new key over the old encrypted files; preserve files and stop affected access.

## Safe Samsung installation

1. Download the published v7 APK in the phone browser. Verify the checksum from the same release when practical.
2. Close any active recording. Open the APK and choose **Update** for an existing Preview installation. Allow installation from that browser/file manager if Android asks.
3. If Android reports an incompatible signature or proposes removing the old app, stop. Do not uninstall, clear storage or reset the profile. Pilot and Preview are different packages; installing Preview does not migrate Pilot data.
4. Open existing recordings and check audio, edits, history, selected version, Cloud settings and the installed Whisper model. Then restart the app and phone and repeat offline.
5. On a fresh installation, grant microphone permission when requested; import the already approved local model file through the app picker. New-device Cloud access requires its own authorized invitation/configuration. Whisper model weights and credentials are absent from the APK; the accepted VAD model remains embedded.

Keep enough free storage for APK staging and existing data. Android backup is disabled; ordinary APK rollback is not a data-recovery plan. If an upgrade fails, retain the installed app and all data and report the error. No physical installation is performed by this release task without the owner's specific permission.
