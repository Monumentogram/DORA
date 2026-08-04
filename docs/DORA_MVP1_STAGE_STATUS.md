# Dora MVP 1 — Stage Status

Updated: 4 August 2026
Baseline: `1be83e2940a09f7b23e33b4cdf3827de2690f3fd`
Stage 00 merge commit: `a4aae302f9033e5471f6759f513e7e351c375a72`
Repository: public `Monumentogram/DORA` by temporary owner-approved decision (ADR-0002)
Default branch: `main`
Active stage: `Stage 0A — Governance and PoC Preparation`
Active branch: `stage/0a-poc-governance`
Stage state: **GOVERNANCE PACKAGE IN OWNER REVIEW**

## Stage 00 closure

- Stage 00 is complete.
- Pull Request #1 was merged into `main`.
- The merge commit is `a4aae302f9033e5471f6759f513e7e351c375a72`.
- The Stage 00 Android bootstrap, CI, governance baseline and validation tooling are present on `main`.
- Production functionality was not started in Stage 00.

## Active Stage 0A scope

Stage 0A prepares governance and reproducible evidence contracts for isolated Stage 0 PoC work. The working branch contains documentation only:

- a short owner-decision pack for the nearest PoC blockers;
- privacy/data-flow/threat assumptions for `GOV-PRIVACY-001`;
- an IP and asset provenance policy for `GOV-IP-001`;
- the preparatory D1–D7 device matrix for `POC-DEVICE-001`;
- proposed PoC gates and a machine-readable benchmark-result schema for `POC-GATES-001`;
- dataset governance foundations for `POC-DATA-001`;
- a dependency-aware execution order that selects one next technical PoC.

This stage does not implement or admit recording, microphone permission, foreground services, VAD, ASR, diarization, database, backend, accounts, production identity/signing or model weights.

## Current gates

- All numeric quality and reliability thresholds remain **Proposed** until the owner explicitly approves them or approves a versioned override.
- Physical device availability is not yet confirmed. A matrix requirement is not evidence that a device exists or was tested.
- Synthetic data is the default. Purpose-recorded test audio requires the documented consent, access, retention and deletion process. Real meetings remain prohibited.
- No PoC result admits a production dependency. Native code or model admission later requires an ADR plus license, provenance, ABI, 16-KiB and runtime evidence.
- `main` remains the protected integration branch. This Stage 0A work is reviewed through a separate Pull Request and is not merged by this task.

## Next safe action

After the owner resolves the blocking questions and the required device is confirmed, the recommended first isolated technical experiment is `POC-CAPTURE-001`. It must run in a dedicated PoC branch/harness and must not be treated as production capture code.

## Update protocol

Every later task updates this file only when stage truth changes. Live PR/build status remains authoritative in GitHub and should not be copied as a stale badge or hard-coded run ID here.
