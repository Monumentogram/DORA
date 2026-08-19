# Dora Stage 0 — Decision I4 synthetic provenance/source validator scope v0.1

Task: `POC-DECISION-001` / Decision I4 under `OWNER-AUTH-BATCH-20260819-01`
Version: 0.1
Baseline: `origin/main@9256db3d95fa20bc0d98aa35b48734ffeeb2623c`
Baseline tree: `6e17701efbdc80e14bd0778535f187c1424ed047`
Branch: `codex/poc-decision-i4-provenance-source-validator`
Status: implementation boundary frozen before validator, test, runner, evidence, or workflow code

## 1. Hypothesis

A dependency-free Java 17 host fixture can deterministically reject a bounded set of forged or
internally inconsistent source/provenance envelopes before any candidate could be reviewed. The
fixture uses only programmatically generated RU, EN, and mixed RU/EN text and can prove these
mechanics: exact source identity, exact whole-source and excerpt SHA-256 binding, valid half-open
UTF-8 byte ranges, and exact fixture schema/source-version binding.

This is an isolated Stage 0 mechanics hypothesis. It is not the complete source validator named by
the `POC-DECISION-001` backlog row, a product schema, a parser for external model output, or a
benchmark/quality result. Parent `POC-DECISION-001` remains `BLOCKED` and `NOT_RUN`.

## 2. Authoritative inputs and relationship to I2/I3

The implementation follows the repository hierarchy in `AGENTS.md`, especially Technical Plan
§§18, 29, 34, 35, and 39; the Design Spec source-grounding and reversibility rules; Product
Decisions `DEC-019` and `DEC-020`; the Test Strategy synthetic-fixture and deterministic host-test
rules; and the unchanged Backlog/Stage Status gate truth.

Decision I2 already exercises generated-text source ranges, metric arithmetic, and user-truth
protection. Decision I3 already exercises a 144-case generated metamorphic campaign over the I2
API. I4 does not rerun that campaign, score candidates, classify semantic support, or redefine I2.
It adds only the missing versioned provenance-envelope mechanics listed in this scope. The I2/I3
source and evidence bytes are pinned as unchanged context in the I4 evidence record.

No conflict was found among the authoritative sources for this bounded fixture. The backlog phrase
“source validator ... absent” continues to mean the full governed/product source validator; this
host-only fixture does not close that gap and therefore does not require a Backlog or Stage Status
edit.

## 3. Frozen fixture contract

The fixture constants are exact:

- profile: `decision-i4-provenance-source-validator-stage0-v0.1`;
- envelope schema: `DORA_DECISION_I4_PROVENANCE_ENVELOPE_STAGE0_V0_1`;
- generated source-set version: `DORA_DECISION_I4_GENERATED_SOURCE_SET_STAGE0_V0_1`;
- range unit: `UTF8_BYTE_OFFSETS_HALF_OPEN_STAGE0_V0_1`;
- digest encoding: lowercase hexadecimal SHA-256 over exact UTF-8 bytes;
- canonical ordering: candidate ID, then diagnostic declaration order;
- candidate application/state mutation: always false.

The in-memory registry contains exactly three generated sources, one each for `RU`, `EN`, and
`MIXED_RU_EN`. Each generated source has an opaque source ID, exact text bytes, whole-source
SHA-256, and one exact non-empty excerpt on UTF-8 code-point boundaries. Source and excerpt text
must never appear in canonical stdout or public evidence.

The provenance envelope contains only this Stage 0 fixture shape:

1. candidate ID;
2. envelope schema version;
3. generated source-set version;
4. source ID;
5. declared whole-source SHA-256;
6. UTF-8 byte start inclusive;
7. UTF-8 byte end exclusive;
8. declared excerpt SHA-256.

This record is deliberately not a production event/transcript/audio schema. Null record fields are
rejected at the Java record boundary; no JSON parser or external serialization format is added.

## 4. Deterministic case matrix

Exactly 14 generated cases are executed. These three cases are accepted:

| Case ID | Language | Expected result |
|---|---|---|
| `CASE_ACCEPT_RU` | RU | `ACCEPTED` with no diagnostic |
| `CASE_ACCEPT_EN` | EN | `ACCEPTED` with no diagnostic |
| `CASE_ACCEPT_MIXED` | mixed RU/EN | `ACCEPTED` with no diagnostic |

These 11 single-fault cases are rejected with exactly the named diagnostic:

| Case ID | Injected fault | Exact diagnostic |
|---|---|---|
| `CASE_REJECT_FORGED_SOURCE_ID` | source ID absent from the generated registry | `UNKNOWN_SOURCE_ID` |
| `CASE_REJECT_WHOLE_SHA` | wrong whole-source digest | `WHOLE_SOURCE_SHA256_MISMATCH` |
| `CASE_REJECT_EXCERPT_SHA` | wrong excerpt digest | `EXCERPT_SHA256_MISMATCH` |
| `CASE_REJECT_NEGATIVE_RANGE` | negative start byte offset | `RANGE_NEGATIVE` |
| `CASE_REJECT_OUT_OF_RANGE` | end byte offset exceeds source length | `RANGE_OUT_OF_BOUNDS` |
| `CASE_REJECT_REVERSED_RANGE` | start byte offset is greater than end | `RANGE_REVERSED` |
| `CASE_REJECT_EMPTY_RANGE` | start byte offset equals end | `RANGE_EMPTY` |
| `CASE_REJECT_MID_UTF8_START` | start points at a UTF-8 continuation byte | `RANGE_NOT_UTF8_BOUNDARY` |
| `CASE_REJECT_MID_UTF8_END` | end points at a UTF-8 continuation byte | `RANGE_NOT_UTF8_BOUNDARY` |
| `CASE_REJECT_SCHEMA_VERSION` | envelope schema differs from the frozen schema | `SCHEMA_VERSION_MISMATCH` |
| `CASE_REJECT_SOURCE_VERSION` | source-set version differs from the frozen version | `SOURCE_VERSION_MISMATCH` |

Validation is fail-closed: a case is `ACCEPTED` only when it has zero diagnostics. Invalid range
shape/bounds never trigger excerpt slicing. Input permutation and repeated execution must produce
typed-equal results and byte-identical canonical output.

## 5. Non-goals and authority ceiling

- No network, device, emulator, Android runtime, Gradle, filesystem persistence, clock, random,
  environment, process, thread, reflection, model, provider, cloud, or external dependency work.
- No real meeting, voice/audio, transcript, person, organization, private data, consented corpus,
  governed dataset, model output, or training data.
- No I3 campaign repeat, semantic decision/revision scoring, threshold comparison, benchmark,
  quality estimate, user study, or real source-grounding rate.
- No product event/transcript/audio schema, parser, database, API, UI, extractor, state mutation,
  current/final decision, confirmed/planned task, or automatic application path.
- No changes to I1/I2/I3 sources or evidence, product code, dependencies, ADRs, Product Decisions,
  Backlog, Stage Status, readiness, parent PoC state, thresholds, execution authority, or admission.
- No `PASS`, `READY`, production, support, Security, Legal, human-review, or MVP-completion claim.
- No Ready-for-review transition or merge before a separate exact-head independent review and
  later conditional owner authorization.

## 6. Acceptance criteria

Success requires all of the following:

1. The implementation matches the exact constants, three-source registry, and 14-case matrix above.
2. Every accepted case is exact; every negative case is rejected with exactly one frozen diagnostic.
3. Canonical output is deterministic under repeat and input permutation and contains no generated
   source/excerpt text or external/private content.
4. `javac` major version 17 compiles main and test sources with
   `--release 17 -encoding UTF-8 -Xlint:all -Werror`; compiler stdout/stderr are empty.
5. The exact test command uses assertions and bytecode verification, emits the one frozen
   content-free stdout line, emits no stderr, and is byte-identical across three runner repeats.
6. Recursive `jdeps` output for the compiled main/test closure is exactly `java.base`; every class
   file has major version 61.
7. Source and compiled-main scans reject forbidden runtime surfaces. Public-artifact scans reject
   secrets, credentials/tokens, emails, endpoints, private locators, absolute local paths, source
   text, and claim-ceiling violations.
8. The evidence record pins the exact baseline, I2/I3 context hashes, I4 source/test hashes,
   expected stdout hash, canonical result digest, scope limits, and unchanged parent state.
9. A standard-library-only runner validates a closed file allowlist, exact hashes, evidence shape,
   compiler/runtime/dependency/class boundaries, deterministic repeats, privacy/forbidden surfaces,
   and repository cleanliness. Its fail-closed self-test exercises every declared mutation class.
10. Runner-owned temporary files live outside the repository and are deleted on success and
    failure; no `.class` or other generated artifact appears in the worktree.
11. The full validator is executed locally twice from clean state with byte-identical exact stdout.
12. A dedicated least-privilege workflow checks out the exact event SHA and runs self-test plus full
    validation on relevant pull requests to `main`, pushes to `main`, and manual dispatch. It
    uploads no artifact.
13. The exact implementation commit is pushed to a Draft PR, and exact-head dedicated plus required
    Android CI are observed. Any red P0/P1 or scoped CI result stops this track.
14. The exact head is handed to a separate independent reviewer; this task does not merge it.

## 7. Exact expected tracked files

Only these six files may be added by this slice:

1. `.github/workflows/stage0-decision-i4-provenance-source-validator.yml`
2. `docs/evidence/poc-decision-001/decision-i4-provenance-source-validator-local-evidence-stage0-v0.1.json`
3. `docs/stage0/DORA_MVP1_DECISION_I4_PROVENANCE_SOURCE_VALIDATOR_SCOPE_STAGE0_V0_1.md`
4. `tools/decision_i4_provenance_source_validator/src/main/java/com/monumentogram/dora/stage0/decision/i4/DecisionProvenanceSourceValidator.java`
5. `tools/decision_i4_provenance_source_validator/src/test/java/com/monumentogram/dora/stage0/decision/i4/DecisionProvenanceSourceValidatorTest.java`
6. `tools/run_stage0_decision_i4_provenance_source_validator.py`

All pre-existing paths remain byte-for-byte unchanged.

## 8. Claim ceiling

The maximum allowed claim is:

`DECISION_I4_SYNTHETIC_PROVENANCE_SOURCE_MECHANICS_EXERCISED`

It means only that the exact generated 14-case Stage 0 fixture passed its closed deterministic host
mechanics checks on the tested commit. It is not a PoC `PASS`, readiness, completion, semantic or
model quality, governed-corpus, real-data, device, product-schema, production, support, admission,
Security, Legal, or human-review claim. `POC-DECISION-001` remains `BLOCKED` and `NOT_RUN`.
