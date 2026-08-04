# Dora MVP 1 — Stage Status

Updated: 5 August 2026
Baseline: `1be83e2940a09f7b23e33b4cdf3827de2690f3fd`
Stage 00 merge commit: `a4aae302f9033e5471f6759f513e7e351c375a72`
Repository: public `Monumentogram/DORA` by temporary owner-approved decision (ADR-0002)
Default branch: `main`
Active stage: `Stage 0A — Governance and PoC Preparation`
Active branch: `stage/0a-poc-governance`
Stage state: **OWNER DECISIONS APPROVED — FIRST DEVICE DISCOVERY PENDING**

## Stage 00 closure

- Stage 00 is complete.
- Pull Request #1 was merged into `main`.
- The merge commit is `a4aae302f9033e5471f6759f513e7e351c375a72`.
- The Stage 00 Android bootstrap, CI, governance baseline and validation tooling are present on `main`.
- Production functionality was not started in Stage 00.

## Active Stage 0A scope

Stage 0A prepares governance and reproducible evidence contracts for isolated Stage 0 PoC work. On 4 August 2026, the Project owner explicitly approved `OD-01`–`OD-10`; the approvals are recorded with scope in Product Decisions. The working branch contains documentation only:

- the approved owner-decision pack for the nearest PoC blockers;
- privacy/data-flow/threat assumptions for `GOV-PRIVACY-001`;
- an IP and asset provenance policy for `GOV-IP-001`;
- the preparatory D1–D7 device matrix for `POC-DEVICE-001`;
- the Stage 0-approved gate set plus explicitly Proposed unresolved thresholds and a machine-readable benchmark-result schema for `POC-GATES-001`;
- dataset governance foundations for `POC-DATA-001`;
- a dependency-aware execution order that selects one next technical PoC.

This stage does not implement or admit recording, microphone permission, foreground services, VAD, ASR, diarization, database, backend, accounts, production identity/signing or model weights.

No technical PoC has been launched in Stage 0A. Production functionality has not started.

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
- The owner's physical Android phone has not been connected or identified. Availability for every D1–D7 profile remains `unknown`.
- Before the first measured run, that phone must be connected and its sanitized model, Android API, firmware/build, ABI and RAM automatically discovered. The phone is then mapped to the matching profile; one-device evidence cannot `PASS` the matrix and is `INCONCLUSIVE` unless an approved failure gate produces `FAIL`.
- No controlled non-public evidence store or custodian has been configured. Until then, only synthetic data and sanitized aggregate/public evidence are allowed; raw traces/audio and purpose-recorded volunteer phrases remain blocked.
- Production markets/lawful basis/copy under `DEC-001` and Legal review remain unresolved. The Stage 0 reminder checkbox does not resolve production consent legality.
- No PoC result admits a production dependency. Native code or model admission later requires an ADR plus license, provenance, ABI, 16-KiB and runtime evidence.
- `main` remains the protected integration branch. This Stage 0A work is reviewed in Pull Request #7 and is not merged by this task.

## Next safe action

After Pull Request #7 review and after the owner's phone is connected and automatically identified, the recommended first isolated technical experiment is `POC-CAPTURE-001`. Its measured input must be synthetic until controlled storage is configured. It must run in a dedicated PoC branch/harness and must not be treated as production capture code.

## Update protocol

Every later task updates this file only when stage truth changes. Live PR/build status remains authoritative in GitHub and should not be copied as a stale badge or hard-coded run ID here.
