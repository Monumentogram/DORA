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
Stage state: **REMOTE MANUAL-DEVICE HARNESS IN DEVELOPMENT — PHONE RUNS NOT STARTED**

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

No phone run has started. Production capture, storage, ML, backend, account, production identity/signing and model weights remain outside this stage. The bootstrap `:app` must not receive microphone permission.

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
- The owner's physical Android phone has not been identified. Availability for every D1–D7 profile remains `unknown`.
- Before the first measured run, the installed PoC must export its sanitized model, Android API, firmware/build, ABI and RAM. The phone is then mapped to the matching profile; one-device evidence cannot `PASS` the matrix and is `INCONCLUSIVE` unless an approved failure gate produces `FAIL`.
- No controlled non-public evidence store or custodian has been configured. Until then, only synthetic data and sanitized aggregate/public evidence are allowed; raw traces/audio and purpose-recorded volunteer phrases remain blocked.
- Production markets/lawful basis/copy under `DEC-001` and Legal review remain unresolved. The Stage 0 reminder checkbox does not resolve production consent legality.
- No PoC result admits a production dependency. Native code or model admission later requires an ADR plus license, provenance, ABI, 16-KiB and runtime evidence.
- `main` remains the protected integration branch. Stage 0B work stays on `stage/0b-poc-capture-001`; its Pull Request must remain Draft until Run A, Run B and Run C evidence is complete.

## Next safe action

Build, validate and publish the isolated `POC-CAPTURE-001` APK through a Draft Pull Request, then stop before any recording and wait for the owner to confirm installation. The first device profile and all measured inputs follow the remote manual-device workflow above. Real meetings remain prohibited.

## Update protocol

Every later task updates this file only when stage truth changes. Live PR/build status remains authoritative in GitHub and should not be copied as a stale badge or hard-coded run ID here.
