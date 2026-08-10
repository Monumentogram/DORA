# Dora MVP 1 — Stage Status

Updated: 5 August 2026
Baseline: `1be83e2940a09f7b23e33b4cdf3827de2690f3fd`
Stage 00 merge commit: `a4aae302f9033e5471f6759f513e7e351c375a72`
Stage 0A merge commit: `91b9916b01ff70f63d82412bafbed0d72307dbe1`
Repository: public `Monumentogram/DORA` by temporary owner-approved decision (ADR-0002)
Default branch: `main`
Active stage: `Stage 0B — POC-CAPTURE-001`
Active branch: `stage/0b-poc-capture-001`
Active PoC: `POC-CAPTURE-001`
Stage state: **RUN A AND RUN B REVIEWED — RUN C READY**

## Stage 00 closure

- Stage 00 is complete.
- Pull Request #1 was merged into `main`.
- The merge commit is `a4aae302f9033e5471f6759f513e7e351c375a72`.
- The Stage 00 Android bootstrap, CI, governance baseline and validation tooling are present on `main`.
- Production functionality was not started in Stage 00.

## Stage 0A closure

- Stage 0A is complete.
- Pull Request #7 was merged into `main`.
- The merge commit is `91b9916b01ff70f63d82412bafbed0d72307dbe1`.
- Owner decisions `OD-01`–`OD-10`, privacy/IP/data governance, the device matrix, Gate Set `stage0-v0.1`, benchmark schema and PoC execution order are present on `main`.
- No technical PoC or production functionality was started in Stage 0A.

## Active Stage 0B scope

Stage 0B implements only the disposable `POC-CAPTURE-001` evidence harness selected by `OD-01`. It may add a separate Android application module with physical-microphone permission, a user-initiated microphone foreground service, synthetic signal playback, local WAV analysis/deletion and sanitized report export. It does not admit this implementation into production Dora.

The owner phone cannot be attached to the remote development workstation. Stage 0B therefore uses a remote manual-device workflow without ADB:

1. GitHub Actions builds and publishes a debug-signed PoC APK.
2. The owner installs it manually and starts each test explicitly.
3. The PoC discovers sanitized device characteristics and records technical metrics locally.
4. Raw audio is analyzed and deleted in app-private storage before export is enabled.
5. The owner returns only the sanitized exported profile/report to Codex.

Two bounded phone runs have completed. The first Run A attempt on test build `f351695…` failed at the capture-start boundary with a legacy unscoped `IllegalArgumentException`. The second attempt on `56fe23a` failed before app-private WAV creation with the stable stage code `CAPTURE_START_PRIVATE_FILE (IllegalArgumentException)`. Neither failed attempt began recording or produced a sanitized Run ZIP/deletion receipt. Run A attempt 003 and Run B attempt 004 on `5d9a8ac` recorded, stopped and finalized successfully; their sanitized evidence and deletion receipts were reviewed without an observed approved critical failure, so only Run C is now opened. Run B's duration overshoot, manual intervention and Bluetooth route transition remain an explicit measurement limitation. Production capture, storage, ML, backend, account, production identity/signing and model weights remain outside this stage. The bootstrap `:app` must not receive microphone permission.

## Owner decisions effective 4 August 2026

- `OD-01`: first experiment is `POC-CAPTURE-001`, limited to a physical microphone and explicit Start/Stop; call, system-audio and passive recording are prohibited.
- `OD-02`: every test run requires a separate reminder checkbox; it is not legal permission.
- `OD-03`/`OD-04`: synthetic-first; separately consented adult volunteer phrases may be used only after governance controls; real meetings and training/model improvement are prohibited.
- `OD-05`: fully specified `stage0-v0.1` gates are Approved only for Stage 0; critical data-loss/source/consent gates cannot be weakened after results. The six section 7 thresholds remain `Proposed`.
- `OD-06`: the first exploratory run is limited to one owner-provided physical phone; no other device procurement, global D1–D7 PASS or support claim is allowed.
- `OD-07`: eight hours is best effort only for the exact tested device, firmware, power, temperature and free-space conditions.
- `OD-08`/`OD-09`: GitHub receives only sanitized reports and aggregate metrics; raw evidence requires controlled private storage and the approved 90/180/30-day maximum deletion rules.
- `OD-10`: local mode works without account, network or GMS; cloud remains off until separate explicit consent.

## Current gates and blockers

- The fully specified predicates in Gate Set `stage0-v0.1` are **Approved for Stage 0**. Exact ASR RTF by tier, maximum PSS/native heap, diarization corrections/minute, absolute battery drain without mWh, numeric capture sample-gap tolerance and minimum raw-trace retention remain **Proposed**.
- The owner's physical phone is sanitized as Samsung `SM-S908B`, Android 16 / API 36, build `BP2A.250605.031.A3`, primary ABI `arm64-v8a` and 10515 MiB RAM. It is assigned to D2 as the closest hardware profile; D1 and D3-D7 availability remains `unknown`.
- The refreshed pre-Run-A profile reports 36432 MiB free app storage, above the D2 start threshold of 8192 MiB, with 72% battery, unplugged power and thermal status `NONE`; the Run B export refreshed this to 33707 MiB and 80% with the same unplugged/`NONE` state. The D2 hardware inventory remains valid.
- The first Run A attempt on build `f351695…` is invalid: recording did not start, no sanitized Run ZIP or deletion receipt was produced, and the screenshot-era error can only be classified as a legacy unscoped `IllegalArgumentException` at `beginServiceCapture`.
- Replacement build `56fe23a` is published as prerelease `poc-capture-001-build-56fe23a` after successful push and PR CI. It makes optional battery/AudioRecord telemetry best-effort, adds sanitized capture-start stage codes, hardens recorder/player cleanup and propagates semantic dark-theme content colors.
- The replacement CI debug certificate differs from build `f351695`. The owner-provided post-install preflight showed the replacement-only corrected dark-theme UI, Run A target `00:03:00`, 36252 MiB free storage, 80% battery, unplugged power, thermal status `NONE` and synthetic signal disabled.
- The preflight screenshot does not display a commit identifier; association with `56fe23a` relies on the prescribed clean-install workflow and replacement-only corrected UI behavior. The source screenshot is not committed.
- The subsequent Run A attempt on `56fe23a` is invalid: recording did not start, no raw audio/Run ZIP/deletion receipt was created, and the visible stable code localizes the failure to `CAPTURE_START_PRIVATE_FILE`.
- Root cause is deterministic: generated Run IDs contain safe uppercase UTC delimiters `T`/`Z`, while the private WAV basename validator accidentally allowed only lowercase ASCII. Fix `5d9a8ac` accepts ASCII case consistently while retaining traversal, nested-path and unsupported-suffix rejection.
- Prerelease `poc-capture-001-build-5d9a8ac` targets exact commit `5d9a8aceebaa7175a7a5cbaa139e8295df87d632`; push run `31006645902` and PR run `31006649411` are successful. APK SHA-256 is `dc7c01b8fd0f6f66c2674a8542595aab8817d60c4435d2f1c26ef8b1c4d2ddb9`.
- The new CI debug certificate `4052ae88…` differs from build `56fe23a` (`dd28877d…`). The owner-provided post-install preflight showed readable build `5d9a8ac` Run A UI with target `00:03:00`, 35945 MiB free storage, 72% battery, unplugged power, thermal status `NONE` and synthetic signal disabled; this was the accepted preflight for Run A attempt 003.
- The new preflight screenshot does not display a commit identifier; association with `5d9a8ac` relies on the prescribed clean-install workflow for the differently signed replacement APK. The source screenshot is not committed.
- Run A attempt 003 (`run-a-20260805T133208Z-349c5e0c`) targets exact commit `5d9a8aceebaa7175a7a5cbaa139e8295df87d632`. The returned 5294-byte source ZIP has SHA-256 `f7d00a3675de539908641b513254e7fb8161f82b20685b64f27a4f080fdd05b1`; it passed CRC, allowlist, JSON parse, benchmark schema and sanitization review and is not committed.
- Run A completed 180.258 s with 2881600 actual versus 2884128 expected samples, 5763200 PCM bytes, one short read, zero AudioRecord errors, a valid 5763244-byte WAV, 20 ms start latency, 44 ms finalization latency, zero route changes/interruptions, 67%→67% battery, thermal `NONE`→`NONE`, 132.299 MiB peak PSS and 21.863 MiB peak native heap.
- The deletion receipt reports `deletionSucceeded=true`, `absenceVerified=true` and `containsAudio=false`; no raw audio is retained or committed. Public evidence is under `docs/evidence/poc-capture-001/`.
- Run A notification visibility is `unknown`, not a positive visibility proof and not evidence of a hidden recording. Run B positively records `manual.notification_visible=yes`, so the approved hidden-state failure predicate was not triggered.
- Run B attempt 004 (`run-b-20260810T054638Z-0b71124a`) targets exact commit `5d9a8aceebaa7175a7a5cbaa139e8295df87d632`. The returned 5423-byte source ZIP has SHA-256 `8d8161a57478f13036e3b23ec3c7abc7642b85594f9cd4e38f48a0872a841647`; it passed CRC, flat allowlist, JSON parse, benchmark schema and sanitization review and is not committed.
- Run B completed 1316.644 s against a 900 s plan and accumulated 1020.154 s screen-off. It recorded 21032314 actual versus 21066304 expected samples, 42064628 PCM bytes, one short read, zero AudioRecord errors, a valid 42064672-byte WAV, 15 ms start latency, 31 ms finalization latency, 80%→78% battery, thermal `NONE`→`NONE`, 136.281 MiB peak PSS and 18.496 MiB peak native heap.
- Run B's deletion receipt reports `deletionSucceeded=true`, `absenceVerified=true` and `containsAudio=false`; deleted-WAV SHA-256 is `9f24052322b2ead6f3d528b8d69f8f06a25c4e6eb97a975b77a2394be13b6486`. No raw audio or source ZIP is retained or committed.
- Run B is not treated as a clean uninterrupted protocol execution: it overshot the planned duration by 416.644 s, the owner marked a call or other intervention, and the event log records a 6.476 s route transition to Bluetooth SCO and back after the nominal 15-minute target. The aggregate report has no frame-level timing to prove continuity through that transition, and the -33990 sample observation has no retrospectively approved tolerance.
- No approved critical failure gate was observed, so Run C may proceed. The formal result remains `INCONCLUSIVE`: one phone/two runs cannot prove D1-D7 or 99.5%, and the numeric sample-gap threshold remains Proposed.
- Both sanitized event logs are non-monotonic at raw deletion because the deletion event is labeled with `outcome.actualDurationMs`; the independent receipts/export guard prove deletion happened only after finalized-file analysis. This is a known non-fatal measurement-telemetry defect, and event timing is not used as pass evidence.
- One-device evidence cannot `PASS` the matrix and remains `INCONCLUSIVE` unless an approved failure gate produces `FAIL`.
- No controlled non-public evidence store or custodian has been configured. Until then, only synthetic data and sanitized aggregate/public evidence are allowed; raw traces/audio and purpose-recorded volunteer phrases remain blocked.
- Production markets/lawful basis/copy under `DEC-001` and Legal review remain unresolved. The Stage 0 reminder checkbox does not resolve production consent legality.
- No PoC result admits a production dependency. Native code or model admission later requires an ADR plus license, provenance, ABI, 16-KiB and runtime evidence.
- `main` remains the protected integration branch. Stage 0B work stays on `stage/0b-poc-capture-001`; its Pull Request must remain Draft until Run A, Run B and Run C evidence is complete.

## Next safe action

Execute only `Run C — 60 минут, экран выключен` on the same installed build and phone. Charge the phone to a reasonable level, use a quiet room with no nearby conversations, enable airplane mode, fully disable Bluetooth before Start, keep the synthetic signal disabled, acknowledge/start once and positively verify that the persistent recording notification is visible. Turn the screen off for about 60 minutes, then turn it on, Stop, analyze/delete raw audio, complete all five observations and return only the sanitized ZIP. Stop and report a screenshot without retry if the notification is absent, recording ends unexpectedly, deletion fails or another critical error appears. Do not run a three-hour or eight-hour test in this PR; real meetings remain prohibited.

## Update protocol

Every later task updates this file only when stage truth changes. Live PR/build status remains authoritative in GitHub and should not be copied as a stale badge or hard-coded run ID here.
