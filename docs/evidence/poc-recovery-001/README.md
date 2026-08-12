# POC-RECOVERY-001 governance/readiness evidence

Status: **OWNER-REMEDIATED PROTOCOL v0.3 — JSR305 EXCLUSION PATH PROVEN; PRODUCT/IP DECISION AND EXECUTION BLOCKED**\
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
| `governance-remediation-v0.3.md` | F-01–F-06 disposition and remaining blockers at the reviewed remediation point | F-01–F-06 closed; retained evidence of the underlying jsr305 license conflict |
| `review-findings-v0.1.json` / `review-findings-v0.2.json` | sanitized stable finding ledgers | `formalReviewer=false`; accountable reviewer still unassigned |
| `dependency-inventory.json` | exact published JAR/POM/metadata closure for `tink-android:1.23.0`, hashes, edges and composition | verified publisher closure; all eight authenticity classifications verified; not runtime admission |
| `jsr305-exclusion-analysis-2026-08-12.json` / `.md` | exact POM, source, bytecode, compiler, loader, D8/R8 and A–E decision analysis | conditioned complete exclusion technically proven; bare exclude rejected; Product/IP decision pending |
| `jsr305-reference-classes-2026-08-12.txt` | complete sorted Tink class list containing JSR-305 descriptors | 182 classes; SHA-256 pinned by the analysis JSON |
| `dependency-ip-authenticity-v0.3.json` | per-coordinate license/copyright/NOTICE, checksums, PGP, signer trust and source correspondence | 16 JAR/POM checksums + signatures verified; six exact multisource and two publisher-bound classifications |
| `dependency-ip-authenticity-verification-2026-08-12.md` | commands, full fingerprints, exact source comparisons, conclusions and limitations | authenticity verified; jsr305 Apache/BSD conflict recorded |
| `license-notice-inventory.json` | exact POM declarations, upstream LICENSE/NOTICE evidence, shaded protobuf terms and patent notes | jsr305 signed-POM/exact-source license conflict; Product/IP approval blocked |
| `security-advisory-inventory.json` | exact-version and historical advisory snapshot, upstream maintenance notes and crypto-review risks | snapshot complete; independent review pending |
| `sqlite-platform-provenance.json` | recovery-only emulator/D2 platform SQLite provenance boundary | emulator pinned; D2 runtime preflight pending |
| `review-roles.json` | reviewer assignment, null approval fields and authority boundaries | Product/IP assigned but not finally approved; accountable Engineering/Security unassigned |
| `readiness.json` | fail-closed readiness record | `executionAllowed=false` |
| `ip-stage0-evaluation-review.md` | human-readable supply-chain and Product/IP package assessment | protocol v0.3 license/Product-IP review blocked |
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
signatures verified cryptographically over artifact bytes with full primary/signing fingerprints.
AndroidX and Error Prone use publisher-bound signatures. The other six use signed source JARs plus
exact upstream source correspondence; every generated transformation is enumerated and no source
entry is unexplained. All eight coordinate authenticity classifications are verified.

F-06 is closed because the exact per-coordinate evidence is complete. Its conclusion nevertheless
records that the signed published `jsr305:3.0.2` POM declares Apache-2.0, while the exact
release-source POM/LICENSE declares BSD-3-Clause. The new exclusion analysis does not choose which
terms govern. It proves that a future binary-consuming harness can keep the artifact out of every
resolved configuration without a replacement, provided it uses both a Tink-local Gradle exclusion
and three exact R8 `-dontwarn` rules. A bare exclusion fails the repository-pinned AGP R8 probe.
The narrow rule removes every JSR-305 diagnostic in a seven-program-artifact observation, but that
observation then fails independently on `javax.lang.model.element.Modifier` from
`error_prone_annotations:2.41.0`; the future exact release build must resolve that condition without
broadening the JSR-305 `dontwarn` policy.

The recommended owner choice is conditioned Option A: accept that technical avoidance for the
exact Stage 0 graph. The underlying artifact conflict remains unresolved and the owner has not yet
accepted the avoidance treatment, so Product/IP approval fields remain null. If accepted, a future
graph must contain zero `com.google.code.findbugs:jsr305:3.0.2` components across every resolvable
recovery/consumer configuration; `compileOnly` and alternate paths are forbidden. The readiness
checker fails closed on a present nonconforming report and cannot pass while exact future graph,
build and package evidence is absent or while any R8 missing class remains unresolved.

The root artifact is a Java JAR with 1,878 class entries, no `.so`/JNI/native entry and 540 shaded
protobuf entries. `RuntimeVersion` identifies the embedded protobuf runtime as 4.33.6. The
publisher-declared external closure has eight coordinates including the root. This is not a future
Gradle-resolved harness graph: the repository currently uses Kotlin 2.2.10 while AndroidX metadata
declares Kotlin stdlib 1.7.10. An exact future configuration lock and delta review are therefore a
pre-execution blocker and cannot be manufactured without the Gradle wiring prohibited in this
task.

## Review state

- The Project owner is assigned as Stage 0 Product/IP reviewer, but final approval fields remain
  null. The pending decision is whether to accept `REC-JSR305-EXCLUDE-001`, retain the coordinate
  while seeking Legal/IP clarification, or abandon Tink. Technical evidence recommends conditioned
  exclusion and does not resolve the Apache-2.0/BSD-3-Clause conflict itself.
- A distinct accountable recovery Engineering/Security reviewer is unassigned. The current Codex
  remediation does not claim formal independence. That reviewer must verify or revise the selected
  streaming/microfile construction, lookahead proof, exact parsers/AAD/key path, commit/durability,
  SQLite profile, public barriers and fault/recovery state machine.
- Production Legal and Production Security remain null and separate future approvals.
- A later owner authorization is required even after all package/reviewer conditions are met.

No state in this directory admits Tink, SQLite schema, Room, SQLCipher, WorkManager or any recovery
protocol to production.
