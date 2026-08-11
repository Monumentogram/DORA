# POC-RECOVERY-001 governance/readiness evidence

Status: **PACKAGE READY FOR REVIEW — EXECUTION BLOCKED**\
Date: 12 August 2026\
Branch: `stage/0d-poc-recovery-governance`\
Measured execution: **none**

This directory contains only public governance, provenance and review evidence for the proposed
Stage 0 recovery experiment. It contains no harness, recovery module, dependency lock from a
runtime graph, device result, benchmark, key, keyset, ciphertext, plaintext, audio or database.

## Package index

| Artifact | Purpose | Current state |
|---|---|---|
| `dependency-inventory.json` | exact published JAR/POM/metadata closure for `tink-android:1.23.0`, hashes, edges and composition | verified publisher closure; not runtime admission |
| `license-notice-inventory.json` | exact POM declarations, upstream LICENSE/NOTICE evidence, shaded protobuf terms and patent notes | evidence complete; Project-owner Product/IP review pending |
| `security-advisory-inventory.json` | exact-version and historical advisory snapshot, upstream maintenance notes and crypto-review risks | snapshot complete; independent review pending |
| `sqlite-platform-provenance.json` | recovery-only emulator/D2 platform SQLite provenance boundary | emulator pinned; D2 runtime preflight pending |
| `review-roles.json` | reviewer assignment and authority boundaries | Product/IP assigned; independent Engineering/Security unassigned |
| `readiness.json` | fail-closed readiness record | `executionAllowed=false` |
| `ip-stage0-evaluation-review.md` | human-readable Product/IP/security package assessment | package ready; not approved for execution |
| `independent-engineering-security-review-task.md` | ready-to-send independent review assignment | unassigned; review only, no implementation/execution |

The normative experiment decision, Gate Set and protocol are:

- `docs/stage0/DORA_MVP1_POC_RECOVERY_OWNER_DECISION_OD14.md`;
- `docs/stage0/DEC-044-POC-RECOVERY-EXPERIMENT.md`;
- `docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_1.md`;
- `docs/stage0/poc-recovery-gate-set-stage0-v0.1.json`; and
- `docs/stage0/poc-recovery-protocol-stage0-v0.1.json`.

## Exact external candidate boundary

The only prepared evaluation candidate is
`com.google.crypto.tink:tink-android:1.23.0`. Its published root JAR SHA-256 is
`c656918451b01c45ce5b20c7b6d4c388f956f61b3a3528e769048c8944c42f9e` and its published POM
SHA-256 is `a2d27e7207e6a25764859b62924fc7b972f41884ce272cead9b946c15a1f410f`.
The v1.23.0 source tag resolves to commit
`1bedd75ae7161017c5f45b020395a72bbd40645d`.

The root artifact is a Java JAR with 1,878 class entries, no `.so`/JNI/native entry and 540 shaded
protobuf entries. `RuntimeVersion` identifies the embedded protobuf runtime as 4.33.6. The
publisher-declared external closure has eight coordinates including the root. This is not a future
Gradle-resolved harness graph: the repository currently uses Kotlin 2.2.10 while AndroidX metadata
declares Kotlin stdlib 1.7.10. An exact future configuration lock and delta review are therefore a
pre-execution blocker and cannot be manufactured without the Gradle wiring prohibited in this
task.

## Review state

- The Project owner is assigned as Stage 0 Product/IP reviewer but has not yet reviewed this newly
  produced exact package.
- The independent recovery Engineering/Security reviewer is unassigned. That review must approve
  the Proposed streaming template, public-API checkpoint proof, microfile template and manifest,
  key hierarchy/AAD, SQLite durability profile, kill mappings and failure state machine.
- Production Legal is unassigned. Production Security remains a separate future approval.
- A later owner authorization is required even after all package/reviewer conditions are met.

No state in this directory admits Tink, SQLite schema, Room, SQLCipher, WorkManager or any recovery
protocol to production.
