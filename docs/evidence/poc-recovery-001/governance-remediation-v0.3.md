# POC-RECOVERY-001 governance remediation v0.3

Status: **F-01–F-05 CLOSED; F-06 PARTIALLY CLOSED / AUTHENTICITY PENDING; EXECUTION BLOCKED**\
Date: 12 August 2026\
Reviewed v0.2 commit: `70cf26125dbecbb347311ca0bb9ce1ad5c637e18`\
Decision linkage: Proposed `DEC-044` / owner record `OD-14`\
Active contract: `poc-recovery-stage0-v0.3` / `poc-recovery-protocol-stage0-v0.3`\
Authorization: `executionAllowed=false`

## Scope

This is a governance-only remediation. It does not add Tink or another runtime dependency, create
`:poc:recovery`, implement a harness, modify production `:app`, run a device/recovery test, execute
a kill campaign or benchmark, admit a dependency, select a production design, or finalize
`ADR-AUDIO-001`.

The v0.1 and v0.2 Gate Set/protocol artifacts remain byte-for-byte unchanged and are retained as
superseded, non-executable audit artifacts. v0.3 is the only active prospective contract.

## Finding disposition

| Finding | Disposition | v0.3 evidence |
|---|---|---|
| `F-01` | `CLOSED` | exact `AndroidKeystoreKmsClient.Builder().setKeyUri(alias).build().getAead(alias)` access path; new-run generation remains `generateNewAeadKey(alias)` only; recovery generation/replacement forbidden |
| `F-02` | `CLOSED` | generic order removed; exact 9-step streaming setup, 13-step checkpoint and 21-step microfile sequences; K01–K12 reference those boundaries |
| `F-03` | `CLOSED` | exact `final + ".tmp"` mapping, exclusive create/collision block, no overwrite, five reconciliation states, and 8 final + 8 temp path-validation coverage |
| `F-04` | `CLOSED` | five-level key precedence and unambiguous `KEY-01`–`KEY-07`; mandatory fault count is 33 |
| `F-05` | `CLOSED` | sanitized machine ledgers `review-findings-v0.1.json` and `review-findings-v0.2.json`; every entry records `formalReviewer=false` |
| `F-06` | `PARTIALLY_CLOSED_AUTHENTICITY_PENDING` | eight per-coordinate license/copyright/NOTICE/source records; 16 publisher checksum matches and 16 cryptographically verified detached signatures; six coordinates still lack authoritative upstream signer trust and/or exact source correspondence |

## Supply-chain boundary

All exact JAR and POM bytes continue to match the SHA-256 inventory. Online verification on
2026-08-12 additionally matched 16 publisher-hosted SHA-256/SHA-1 checksums and cryptographically
verified all 16 detached OpenPGP signatures using BouncyCastle 1.84. AndroidX and Error Prone have
coordinate-specific upstream signer trust evidence. Tink, JSR-305, Gson, both Kotlin coordinates
and JetBrains annotations remain `AUTHENTICITY_PENDING`; the exact closure paths are recorded in
`dependency-ip-authenticity-v0.3.json`.

Accordingly the former `EVIDENCE_COMPLETE_PACKAGE_REVIEW_PENDING` wording is withdrawn. Overall
status is `AUTHENTICITY_PENDING_PRODUCT_IP_APPROVAL_BLOCKED`; Product/IP final approval remains
false, `approvedReviewer=null`, and `approvedOn=null`.

## Remaining blockers

- `P0`: close six coordinate-specific signer trust/source-correspondence paths and obtain final
  Stage 0 Product/IP disposition.
- `P0`: assign a distinct accountable recovery Engineering/Security reviewer; this Codex
  remediation is not formal independent review.
- `P0`: separately scope implementation and non-metric verification of the exact v0.3 contract,
  then capture the future resolved Gradle graph and fresh emulator/D2 SQLite preflight.
- `P0`: obtain a separate Project-owner execution authorization only after all prerequisites.
- `P1`: Production Legal and Production Security remain unassigned for any later admission or
  redistribution; D1/D5 remain deferred until a full physical verdict is desired.

No blocker can implicitly flip execution authority. `executionAllowed=false` remains normative.
