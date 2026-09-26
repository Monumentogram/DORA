# SMALL private manifest/operator schema remediation — Stage 0 v0.1

Task 5.6C.2A.1: **PASS / PRIVATE_MANIFEST_OPERATOR_COMPATIBILITY_REMEDIATED**.
Branch: `chat/alpha-asr-runner-scope`. Starting HEAD and freshly fetched remote:
`bdbdaca73c8e0fd9b297bde560cda4442892bd6d`; required parent `d79c71eedf49bf43ee95735ae67f3acbd39f1404`.
Tracked and untracked tree were clean before work. This is one additive six-file commit.

## Cause and correction

The historical 5.6C.2A operator indexes `manifest['samples']` on the fully
materialized document. Its exact identity is valid, but that schema contains
`bindings`. The separate metadata-only selection manifest contains `samples`.
This mismatch explains the historical 5.6C.2B pre-start block; it does not establish
corpus corruption, a SMALL model failure, or a quality/resource result.

The successor validates the four independent canonical identities before using
their records. Only selection and materialized documents exclude their required
`manifestSha256` self field when hashing. Transfer and freeze hash the full object.

| Document | Canonical SHA-256 | Actual verification |
|---|---|---|
| selection | `695f1e147fe968c8f75e7bea5e7a6edfd20f50e939c0bdbc2064d3a7db7d1947` | PASS |
| materialized | `2cb05132de945fe8a1b281e79efd8efa15d9b9b94c0326f6572a594e0c74e9bb` | PASS |
| transfer | `95c26be0501172196587181b3f46705b65237bde879a0e3ee8eb748cee2e1a87` | PASS |
| freeze | `eb088082b2cde6d2bc2649e6cb6d67a92d3a68ad447a8a7331eec58c2794bcaa` | PASS |

Selection owns the exact 48 rows, RU24 then EN24, with frozen per-locale rank,
selection key and order. The actual strict materialized schema has 48 bindings
and two source-index records. Each binding must equal its selected row's identity,
locale, rank, selection key, source identity, duration, audio size/hash and
reference hash. Relative file locators follow the admitted schema and cannot be
overridden by configuration. The transfer schema has exactly 97 records: 48 audio,
48 references and the materialized JSON. Both content classes require matching
byte counts and hashes; the JSON record binds the actual metadata byte snapshot.
The freeze independently binds selection, materialization, transfer, governance,
model metadata, runtime, decoding, protocol, resource gates and retention.
Missing/extra/duplicate fields or records, invalid paths and mismatches fail closed.

## Frozen successor and unchanged history

Operator: `dora-alpha-asr-small-campaign-operator-v0.2`.
Canonical operator SHA-256: `f96127249cbc7cef3a504caefe4704e2932bd268dd43a7811a2021459bab60b1`.
It hashes the canonical `operatorFiles` map in the [aggregate evidence](../evidence/poc-asr-001/alpha-asr-small-campaign-schema-remediation-stage0-v0.1.json),
including the two successor files and three unchanged evaluator/schema modules.
It adds no replacement evaluator, normalization, oracle math, quality gate,
resource gate or disposition logic. The original evaluation profile is unchanged:
`217b5662bfd22520779a2df2551f553cb17e97e11f0273e8238682a5f4b7f69f`. Its historical campaign-version and selected-manifest
field are preserved; the new operator gives selection/materialization separate identities.

All three d79c71e operator/evaluator/test files and both bdbdaca blocked-result
files are byte/blob unchanged. Original 5.6C.2A PASS remains historical host/synthetic
evidence; original 5.6C.2B BLOCKED remains historical pre-start evidence.
All 71 named protected files and 597 immutable baseline files are preserved.
Status/backlog retain every earlier byte through an additive insertion.

## Actual controlled-package check

After the synthetic suite passed, the successor `verify_metadata` accepted the
four actual existing metadata documents. Result: **PASS**, 48 cross-bound cases,
24 RU + 24 EN, 96 content-file metadata bindings, all 97 transfer records.
The actual document bytes matched the initial inspection snapshots.

This pre-publication call used external checks of the required HEAD/parent/branch,
all 599 original tracked file hashes, the exact two new source files, and the frozen
successor source map before/after verification. It did not invoke the separate
published-checkout file wrapper, which requires the final clean successor commit
and report. Generated tests cover that wrapper's repository/evidence/file guards
and four-document read boundary. There was no dirty-tree bypass in that wrapper.

This proves metadata compatibility. It does not reverify current content bytes or
grant execution readiness. The measured API unconditionally rejects calls,
including the old authorization token. No private package was rewritten.

Metadata reads: **8** (4 schema inspection + 4 actual compatibility check).
All forbidden-operation counters are **0**: actual campaign attempts, campaign
markers, attempt journals, model byte reads/loads, audio/reference content reads,
decode, ASR inference, whisper_full, device connections/executions, ADB, codec/warmup,
threshold changes, tuning, reselection, rematerialization and private package writes.
Private IDs, per-case hashes, reference text, participant/device identifiers and
host-private locators are excluded from public artifacts. Synthetic fixtures are generated.

## Validation and boundaries

- Successor tests: 38 PASS; SMALL host suite: 99 PASS.
- Full ASR host suite: 212 PASS, including all 174 historical tests.
- Stage00: all seven checks PASS. Strict JSON, links, privacy, protected blobs,
  exact six-file scope and `git diff --check`: PASS.
- One repeat host run had `subprocess.TimeoutExpired` in the unchanged
  `test_java_oracle_scoring_parity` Java invocation. Its log remains retained;
  no timeout/code was changed. Fresh required suites passed. Cause is unestablished.
  This was a host verification retry, not a measured campaign attempt.
- Independent public-code review: no actionable findings.
- CI not dispatched. This branch does not match configured push branch/tag triggers;
  no PR was created. No Android, device, runtime or Gradle execution occurred.

PR [#86](https://github.com/Monumentogram/DORA/pull/86) was checked read-only:
OPEN, draft, unmerged, untouched. Recovery remains **0D.6 = ALPHA CLOSED / FULL OPEN**.
Retention remains 2026-10-25. POC-ASR-001 is BLOCKED / NOT_READY. Stage 5 and Group B
are not marked PASS. No schema blocker remains, but measured execution needs a
separate owner decision and future execution scope.

**5.6C.2B remains BLOCKED / NOT_AUTHORIZED after this task.**

## Exact changed files

- `tools/run_alpha_asr_small_campaign_v2.py`
- `tools/test_alpha_asr_small_campaign_v2.py`
- `docs/stage0/DORA_ALPHA_ASR_SMALL_CAMPAIGN_SCHEMA_REMEDIATION_STAGE0_V0_1.md`
- `docs/evidence/poc-asr-001/alpha-asr-small-campaign-schema-remediation-stage0-v0.1.json`
- `docs/DORA_MVP1_STAGE_STATUS.md`
- `docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md`
