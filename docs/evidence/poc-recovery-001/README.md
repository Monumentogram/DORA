# POC-RECOVERY-001 governance/readiness evidence

Status: **REC-I1 REVIEWED AND SQUASH-MERGED; POST-MERGE VALIDATOR LIFECYCLE REMEDIATED; RUNTIME IMPLEMENTATION, ACTUAL GRAPH AND EXECUTION BLOCKED**\
Date: 14 August 2026\
Repository lifecycle: live Git/GitHub state is authoritative; no mutable branch or Pull Request state is a package invariant\
Measured execution: **none**

This directory contains only public governance, provenance and review evidence for the proposed
Stage 0 recovery experiment. The first package received the owner-supplied disposition
`CHANGES_REQUIRED`; `governance-remediation-v0.2.md` preserves the first response and
`governance-remediation-v0.3.md` records the F-01–F-06 response and
`governance-remediation-v0.4.md` records the retained v0.4 advisory remediations, and
`governance-remediation-v0.5.md` records `REC-ADV-V04-001..004`, and
`governance-remediation-v0.6.md` records `REC-REV-20260812-01..02` from the non-formal GPT-5.6 Sol
documentary advisory review of commit `eca48ba62acd79007884710395cc40ea21a02611`.
PR #12 subsequently squash-merged that v0.6 package to `main` as
`f14c6f37d7acb37590be875f176653c100f0ae20`; its tree is identical to PR HEAD
`b5371f523e4471aca48a63a82b9ee4e1f9a7e0fd`. A separate 13 August OpenAI Codex (GPT-5)
documentary advisory re-review of that PR HEAD recorded
`NO_FURTHER_DOCUMENTARY_CHANGES_REQUIRED`, no actionable findings and `formalReviewer=false`.
The separate AI-prepared Engineering/Security dossier is also advisory (`formalReviewer=false`) and
is not Novikova Katerina's decision. Novikova Katerina, acting in individual professional capacity
with Rambus listed only as affiliation, then returned the formal read-only disposition
`APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW` for the exact PR #12 HEAD/tree. This closes
`REC-REV-20260812-02` and `REC-RDY-02` without authorizing implementation, Phase A, execution,
measurement, dependency admission, Production Security or Production Legal. No Rambus corporate
approval is claimed.
Owner authorization `REC-I1-AUTH-20260813-01` permits only the isolated pure, non-metric
`:poc:recovery` contract foundation. The module contains no runtime crypto, Tink/JSR-305 wiring,
Keystore bootstrap, SQLite/filesystem implementation, harness or production edge. This package
contains no runtime-graph dependency lock, device result, benchmark, key, keyset, ciphertext,
plaintext, audio or database. Global `implementationAllowed`, execution and measurement authority
remain false.

The pure foundation's later implementation lifecycle is recorded as completed audit context, not
as new authority. OpenAI Codex / GPT-5 independently reviewed PR #15 exact HEAD
`ee7bb00b09a282df7a8fb3b4d3481a5abd4d0177` / tree
`ac3dcf273fd447623fa8dbc5c71087acd6315830` in the AI Recovery I1 implementation advisory role.
The disposition was `NO_FURTHER_CHANGES_REQUIRED`, `formalReviewer=false`, P0/P1/P2=0/0/0,
`REC-I1-IMPL-001..004=CLOSED`, with 62/62 Recovery JVM tests passing. Protected squash merge
created `main` `f2bc8c95bbe8af0d010968fff2ca175851728bf2` from parent
`9c4a798aa3c95877ff3f9aa66f18f94849b25cce`, preserving the reviewed tree.

Initial post-merge push run `31743157457` failed because the validator still rejected branch
`main`; this was a governance-validator lifecycle defect, not a Recovery Kotlin finding. PR #16
changed only `tools/validate_poc_recovery_governance.py`. Its final HEAD
`8038153db2557e439c684686ea739d8c14620da3` / tree
`a75ea1bf1de96827b26c98cd99e461aedfa06ab7` received a separate OpenAI Codex / GPT-5 AI Recovery
governance remediation advisory re-review with `formalReviewer=false`,
`NO_FURTHER_CHANGES_REQUIRED` and P0/P1/P2=0/0/0. Required PR CI run `31784363002` attempt 3
passed; protected squash merge produced `main` `685e759290f8987444280b05e69b9d4d0070424e`, and exact-main
post-merge push run `31790356849` passed. `search-smoke` is unrelated Search emulator evidence, not
Recovery execution evidence. GitHub is authoritative for live branch, PR, protection and CI state.

## Package index

| Artifact | Purpose | Current state |
|---|---|---|
| `implementation-authorization-rec-i1-20260813-01.json` | exact owner authorization for Recovery I1 | task-scoped pure contract implementation only; no runtime crypto, execution, Ready state or merge authority |
| `evidence-index.json` | machine-readable active/superseded artifact index, immutable v0.1–v0.5 SHA-256 pins and completed PR #15/#16 lifecycle audit summary | v0.6 active; pure REC-I1 reviewed/merged; runtime implementation/execution blocked |
| `governance-remediation-v0.2.md` | retained first remediation record | superseded audit evidence; unchanged |
| `governance-remediation-v0.3.md` | F-01–F-06 disposition and remaining blockers at the reviewed remediation point | F-01–F-06 closed; retained evidence of the underlying jsr305 license conflict |
| `governance-remediation-v0.4.md` | final four-finding remediation and three-state readiness model | governance findings closed; future actual graph remains blocked |
| `governance-remediation-v0.5.md` | canonical KEY taxonomy, separate campaign profiles, blocker IDs and active metadata remediation | `REC-ADV-V04-001..004` closed prospectively; implementation/execution blocked |
| `governance-remediation-v0.6.md` | exact effective KEY-04 decrypt-failure-only override and 46-row active matrix | historical remediation state: `REC-REV-20260812-01` closed; `REC-REV-20260812-02` was open/blocking |
| `review-findings-v0.1.json` through `review-findings-v0.5.json` | sanitized stable historical advisory finding ledgers | unchanged; v0.5 records GPT-5.6 Sol/OpenAI `CHANGES_REQUIRED` with `formalReviewer=false` |
| `post-merge-advisory-rereview-2026-08-13.json` | SHA-256-pinned PR #12 merge facts, post-merge Actions and separate advisory re-review | unchanged historical advisory evidence; `formalReviewer=false`; did not close either finding |
| `advisory-engineering-security-dossier-2026-08-13.md` | separate AI-prepared technical review dossier | advisory only; `formalReviewer=false`; not Novikova Katerina's decision; does not close `REC-RDY-02` |
| `formal-engineering-security-review-2026-08-13.json` | distinct accountable human review record and authority boundary | Novikova Katerina; `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW`; no implementation/execution authority |
| `review-findings-v0.6.json` | additive formal closure ledger preserving v0.1–v0.5 | `REC-REV-20260812-02` and `REC-RDY-02` closed by the formal human record |
| `dependency-inventory.json` | exact published JAR/POM/metadata closure plus current I1 boundary | pure module present with existing JUnit alias only; no Tink/runtime wiring; all eight packet authenticity classifications verified; no runtime admission |
| `jsr305-exclusion-analysis-2026-08-12.json` / `.md` | exact POM, source, bytecode, compiler, loader, D8/R8 and A–E decision analysis | conditioned complete exclusion technically proven; bare exclude rejected; Option A accepted prospectively by `OD-14` |
| `jsr305-reference-classes-2026-08-12.txt` | complete sorted Tink class list containing JSR-305 descriptors | 182 classes; SHA-256 pinned by the analysis JSON |
| `dependency-ip-authenticity-v0.3.json` | per-coordinate license/copyright/NOTICE, checksums, PGP, signer trust and source correspondence | 16 JAR/POM checksums + signatures verified; six exact multisource and two publisher-bound classifications |
| `dependency-ip-authenticity-verification-2026-08-12.md` | commands, full fingerprints, exact source comparisons, conclusions and limitations | authenticity verified; jsr305 Apache/BSD conflict recorded |
| `jetbrains-annotations-license-notice-verification-2026-08-12.md` | immutable exact-commit JetBrains LICENSE/NOTICE bytes and independent SHA-256 verification | exact governance packet evidence verified; future NOTICE preservation required |
| `base-lockfile-tooling-inventory-2026-08-12.json` | honest base lockfile Tink/JSR-305 tooling/test/lint/UTP inventory and exact recovery boundary | no repository-wide absence claim; unrelated paths are not recovery admission |
| `license-notice-inventory.json` | exact POM declarations, upstream LICENSE/NOTICE evidence, shaded protobuf terms and patent notes | jsr305 signed-POM/exact-source conflict remains; artifact evaluation approval blocked and owner policy excludes it |
| `security-advisory-inventory.json` | exact-version and historical advisory snapshot, upstream maintenance notes and crypto-review risks | retained historical snapshot; later accountable review recorded separately |
| `sqlite-platform-provenance.json` | recovery-only emulator/D2 platform SQLite provenance boundary | emulator pinned; D2 runtime preflight pending |
| `review-roles.json` | reviewer assignment, scoped owner disposition and authority boundaries | accountable formal review complete; implementation and execution authority withheld |
| `readiness.json` | fail-closed current readiness, narrow I1 verification and completed PR #15/#16 lifecycle audit summary | ten active blockers; all authority flags remain false |
| `ip-stage0-evaluation-review.md` | human-readable supply-chain and Product/IP package assessment | prospective exclusion policy approved; artifact use, implementation and execution blocked |
| `independent-engineering-security-review-task.md` | retained read-only review assignment and question set | assignment completed 2026-08-13; no implementation/execution |

The normative experiment decision, Gate Set and protocol are:

- `docs/stage0/DORA_MVP1_POC_RECOVERY_OWNER_DECISION_OD14.md`;
- `docs/stage0/DEC-044-POC-RECOVERY-EXPERIMENT.md`;
- `docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_6.md`;
- `docs/stage0/poc-recovery-gate-set-stage0-v0.6.json`; and
- `docs/stage0/poc-recovery-protocol-stage0-v0.6.json`.

The v0.1, v0.2, v0.3, v0.4 and v0.5 Markdown/Gate Set/protocol files remain unchanged and SHA-256-pinned as 15 superseded audit artifacts.
They are not valid inputs for future implementation or execution.

Active v0.6 contains exactly one effective `KEY-04`: all eight preconditions culminate in
`Aead.decrypt(existingConfirmationCiphertext, exactAad)` authentication/AAD failure, and the only
classification is `KEY_UNAVAILABLE_KEY_MISMATCH`. Successful decrypt is forbidden for `KEY-04`;
successful decrypt with malformed or wrong plaintext remains `KCF-07` and returns
`CORRUPT_KEY_CONFIRMATION`. The active matrix remains exactly 46 unique IDs. Phase A remains 184
injections, full physical remains 138 and the separate hard-kill denominator remains 120 attempts
per candidate.

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

F-06 is closed only after immutable JetBrains LICENSE/NOTICE verification completed at
`2026-08-12T14:38:33Z`. Its conclusion nevertheless
records that the signed published `jsr305:3.0.2` POM declares Apache-2.0, while the exact
release-source POM/LICENSE declares BSD-3-Clause. The new exclusion analysis does not choose which
terms govern. It proves that a future binary-consuming harness can keep the artifact out of every
resolved configuration without a replacement, provided it uses both a Tink-local Gradle exclusion
and three exact R8 `-dontwarn` rules. A bare exclusion fails the repository-pinned AGP R8 probe.
The narrow rule removes every JSR-305 diagnostic in a seven-program-artifact observation, but that
observation then fails independently on `javax.lang.model.element.Modifier` from
`error_prone_annotations:2.41.0`; the future exact release build must resolve that condition without
broadening the JSR-305 `dontwarn` policy.

The Project owner / Stage 0 Product-IP reviewer accepted conditioned Option A only as prospective
policy `REC-JSR305-EXCLUDE-001` and approved the reviewed governance package for a future exact
excluded Stage 0 graph. The underlying artifact conflict remains unresolved; neither license is
selected and JSR-305 use/distribution is not approved. A future graph must contain zero
`com.google.code.findbugs:jsr305:3.0.2` components across every covered compile, runtime, benchmark,
test and packaging configuration; `compileOnly`, alternate paths and broader `dontwarn` are
forbidden. The readiness checker fails closed on any recurrence and cannot pass while exact future
graph, release R8, non-metric implementation and package evidence is absent or while any R8 missing
class remains unresolved.

The authorized I1 `:poc:recovery` module is pure and adds no Tink/runtime-crypto dependency. The
prospective exclusion policy applies to a future separately authorized runtime graph and every one
of its compile/runtime/unit-test/`androidTest`/benchmark/release configurations,
packaging/runtime inputs, locks and verification metadata. Existing buildscript/AGP/UTP/lint/tooling
paths of other modules and app/capture/search lockfiles remain outside that boundary. They are
inventoried rather than hidden, so no repository-wide Tink/JSR-305 absence is claimed.

The root artifact is a Java JAR with 1,878 class entries, no `.so`/JNI/native entry and 540 shaded
protobuf entries. `RuntimeVersion` identifies the embedded protobuf runtime as 4.33.6. The
publisher-declared external closure has eight coordinates including the root. The pure I1 module is
not that candidate runtime graph and does not resolve Tink. The repository currently uses Kotlin
2.2.10 while AndroidX metadata declares Kotlin stdlib 1.7.10. An exact future runtime configuration
lock and delta review therefore remain pre-execution blockers and require separate authorization.

## Review state

- GPT-5.6 Sol, OpenAI, reviewed commit `eca48ba62acd79007884710395cc40ea21a02611` on
  12 August 2026 as an AI documentary advisory reviewer. Its disposition is `CHANGES_REQUIRED` and
  `formalReviewer=false`. v0.6 closes documentary finding `REC-REV-20260812-01`; governance finding
  `REC-REV-20260812-02` was `OPEN_BLOCKING` at the time of that historical record. This advisory
  evidence did not close `REC-RDY-02` and remains unchanged.
- PR #12 was closed by protected GitHub squash merge at 06:03:14 Europe/Moscow on 13 August 2026.
  New `main` `f14c6f37d7acb37590be875f176653c100f0ae20` has one parent (`eca48ba…`) and the
  same tree as PR HEAD `b5371f523e4471aca48a63a82b9ee4e1f9a7e0fd`; post-merge
  `android-bootstrap` and `search-smoke` succeeded and the source head branch remains preserved.
- OpenAI Codex (GPT-5), OpenAI, separately re-reviewed PR HEAD `b5371f…` on 13 August 2026 as an
  AI documentary advisory reviewer. The disposition is `NO_FURTHER_DOCUMENTARY_CHANGES_REQUIRED`,
  actionable findings are absent, the review was not published as a formal GitHub review and
  `formalReviewer=false`. It did not close `REC-REV-20260812-02` or `REC-RDY-02` and remains unchanged.
- The AI-prepared `advisory-engineering-security-dossier-2026-08-13.md` is separate advisory
  evidence with `formalReviewer=false`. It is not Novikova Katerina's decision and cannot close
  `REC-RDY-02`.
- Novikova Katerina, affiliation Rambus, acting in capacity exactly `individual professional
  capacity; Rambus listed only as affiliation`, personally accepted the 12 review questions and
  recorded `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW` on 13 August 2026 for exact commit
  `b5371f523e4471aca48a63a82b9ee4e1f9a7e0fd` and tree
  `1fd03fd489836c65f7ee043298f8f6d32df00c55`. The formal record closes
  `REC-REV-20260812-02` and `REC-RDY-02`. It is read-only, was not published as a formal GitHub
  review, claims no Rambus corporate approval, and does not authorize implementation, Phase A,
  execution, measurement, dependency admission, Production Security or Production Legal.
- The Project owner, acting as Stage 0 Product/IP reviewer, approved prospective policy
  `REC-JSR305-EXCLUDE-001` and the reviewed governance package at input HEAD
  `eb312feb2a0d5e5b24b45fcd045bacca94e8c9da`; exact governance authenticity/LICENSE/NOTICE evidence
  is verified. `REC-I1-AUTH-20260813-01` now authorizes only the pure contract foundation on its
  exact base. An actual candidate runtime graph, dependency admission, JSR-305 use/distribution,
  runtime implementation, harness and execution remain unapproved.
- OpenAI Codex / GPT-5 independently reviewed the exact PR #15 implementation HEAD/tree in the AI
  Recovery I1 implementation advisory role and returned `NO_FURTHER_CHANGES_REQUIRED` with
  `formalReviewer=false`, no P0/P1/P2 findings and all four implementation findings closed. PR #15
  then protected-squash-merged the pure foundation. This is a completed task-scoped advisory and
  merge lifecycle, not formal human review or runtime/Phase A authority.
- The PR #15 post-merge failure was isolated to validator branch-lifecycle handling. PR #16 changed
  only that validator, received a separate OpenAI Codex / GPT-5 AI advisory re-review with
  `formalReviewer=false` and `NO_FURTHER_CHANGES_REQUIRED`, then protected-squash-merged. Exact-main
  post-merge CI passed; no Recovery execution evidence was produced.
- Production Legal and Production Security remain null and separate future approvals.
- A later owner authorization is required even after all package/reviewer conditions are met.

No state in this directory admits Tink, SQLite schema, Room, SQLCipher, WorkManager or any recovery
protocol to production.
