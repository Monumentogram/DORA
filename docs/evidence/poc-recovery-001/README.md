# POC-RECOVERY-001 governance/readiness evidence

Status: **OWNER-REMEDIATED PROTOCOL v0.3 READY FOR RE-REVIEW — AUTHENTICITY AND EXECUTION BLOCKED**\
Date: 12 August 2026\
Branch: `stage/0d-poc-recovery-governance`\
Measured execution: **none**

This directory contains only public governance, provenance and review evidence for the proposed
Stage 0 recovery experiment. The first package received the owner-supplied disposition
`CHANGES_REQUIRED`; `governance-remediation-v0.2.md` preserves the first response and
`governance-remediation-v0.3.md` records the F-01–F-06 response.
It contains no harness, recovery module, dependency lock from a runtime graph, device result,
benchmark, key, keyset, ciphertext, plaintext, audio or database.

## Package index

| Artifact | Purpose | Current state |
|---|---|---|
| `evidence-index.json` | machine-readable active/superseded artifact index and immutable v0.1/v0.2 SHA-256 pins | v0.3 active; execution blocked |
| `governance-remediation-v0.2.md` | retained first remediation record | superseded audit evidence; unchanged |
| `governance-remediation-v0.3.md` | F-01–F-06 disposition and remaining blockers | F-01–F-05 closed; F-06 partial/authenticity pending |
| `review-findings-v0.1.json` / `review-findings-v0.2.json` | sanitized stable finding ledgers | `formalReviewer=false`; accountable reviewer still unassigned |
| `dependency-inventory.json` | exact published JAR/POM/metadata closure for `tink-android:1.23.0`, hashes, edges and composition | verified publisher closure; authenticity pending; not runtime admission |
| `dependency-ip-authenticity-v0.3.json` | per-coordinate license/copyright/NOTICE, checksums, PGP, signer trust and source correspondence | 16 checksums + signatures verified; six coordinates authenticity pending |
| `license-notice-inventory.json` | exact POM declarations, upstream LICENSE/NOTICE evidence, shaded protobuf terms and patent notes | authenticity pending; Product/IP approval blocked |
| `security-advisory-inventory.json` | exact-version and historical advisory snapshot, upstream maintenance notes and crypto-review risks | snapshot complete; independent review pending |
| `sqlite-platform-provenance.json` | recovery-only emulator/D2 platform SQLite provenance boundary | emulator pinned; D2 runtime preflight pending |
| `review-roles.json` | reviewer assignment, null approval fields and authority boundaries | Product/IP assigned but not finally approved; accountable Engineering/Security unassigned |
| `readiness.json` | fail-closed readiness record | `executionAllowed=false` |
| `ip-stage0-evaluation-review.md` | human-readable supply-chain and Product/IP package assessment | protocol v0.3/authenticity review blocked |
| `independent-engineering-security-review-task.md` | ready-to-send read-only v0.3 review assignment | distinct accountable reviewer unassigned; no implementation/execution |

The normative experiment decision, Gate Set and protocol are:

- `docs/stage0/DORA_MVP1_POC_RECOVERY_OWNER_DECISION_OD14.md`;
- `docs/stage0/DEC-044-POC-RECOVERY-EXPERIMENT.md`;
- `docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_3.md`;
- `docs/stage0/poc-recovery-gate-set-stage0-v0.3.json`; and
- `docs/stage0/poc-recovery-protocol-stage0-v0.3.json`.

The v0.1 and v0.2 Markdown/Gate Set/protocol files remain unchanged as superseded audit artifacts.
They are not valid inputs for future implementation or execution.

## Exact external candidate boundary

The only prepared evaluation candidate is
`com.google.crypto.tink:tink-android:1.23.0`. Its published root JAR SHA-256 is
`c656918451b01c45ce5b20c7b6d4c388f956f61b3a3528e769048c8944c42f9e` and its published POM
SHA-256 is `a2d27e7207e6a25764859b62924fc7b972f41884ce272cead9b946c15a1f410f`.
The v1.23.0 source tag resolves to commit
`1bedd75ae7161017c5f45b020395a72bbd40645d`.

All eight exact JAR and POM pairs matched publisher-hosted checksums and their detached OpenPGP
signatures verified cryptographically. Only AndroidX and Error Prone currently have an authoritative
coordinate-specific upstream signer trust source. The other six records are
`AUTHENTICITY_PENDING`; successful cryptography alone is not treated as publisher identity proof.

The root artifact is a Java JAR with 1,878 class entries, no `.so`/JNI/native entry and 540 shaded
protobuf entries. `RuntimeVersion` identifies the embedded protobuf runtime as 4.33.6. The
publisher-declared external closure has eight coordinates including the root. This is not a future
Gradle-resolved harness graph: the repository currently uses Kotlin 2.2.10 while AndroidX metadata
declares Kotlin stdlib 1.7.10. An exact future configuration lock and delta review are therefore a
pre-execution blocker and cannot be manufactured without the Gradle wiring prohibited in this
task.

## Review state

- The Project owner is assigned as Stage 0 Product/IP reviewer, but final approval fields remain
  null and approval is blocked by the six authenticity/source-correspondence gaps.
- A distinct accountable recovery Engineering/Security reviewer is unassigned. The current Codex
  remediation does not claim formal independence. That reviewer must verify or revise the selected
  streaming/microfile construction, lookahead proof, exact parsers/AAD/key path, commit/durability,
  SQLite profile, public barriers and fault/recovery state machine.
- Production Legal and Production Security remain null and separate future approvals.
- A later owner authorization is required even after all package/reviewer conditions are met.

No state in this directory admits Tink, SQLite schema, Room, SQLCipher, WorkManager or any recovery
protocol to production.
