# Frozen SMALL campaign result — Stage 0 v0.1

Recorded: 2026-09-26. Pre-campaign stop observed: 2026-09-25T18:54:39.079846+00:00.
Task: **5.6C.2B**.

## Result

**Execution: BLOCKED / FROZEN_OPERATOR_PRIVATE_MANIFEST_SCHEMA_MISMATCH.**
**Quality: NOT_EVALUABLE. Resources: NOT_EVALUABLE.**
**SMALL candidate: INCONCLUSIVE / CAMPAIGN_EVIDENCE_INCOMPLETE.**

The measured campaign did not start. There are **0 primary attempts, 0 completed
RU cases and 0 completed EN cases**. No campaign-started.json, measured attempt
journal or private measured result was created. Codec check, warmup, model load,
audio decode, ASR inference and device access are all zero.

## Exact failed invariant

The existing package passed all three exact canonical identity checks: materialized
manifest, transfer index and private pre-inference freeze. The frozen consumer then
requires `rows = manifest['samples']`. The exact admitted materialized manifest
does not contain the required top-level `samples` key. The read raises `KeyError`
before selected-row validation, SMALL rehash, native/runtime verification,
audio/reference binding reads, device identity checks or any campaign marker.

Failed invariant: **FROZEN_OPERATOR_REQUIRES_TOP_LEVEL_SAMPLES**.
This is a producer/consumer schema incompatibility, not evidence of changed source
bytes, model failure or poor model quality. No field was remapped, schema
reinterpreted, operator patched, corpus regenerated, reference edited or sample
replaced. One additional metadata-only forensic read identified the missing key;
it was not a verification retry. No second precheck sequence or campaign call occurred.

The owner interrupted reporting and resubmitted the task. The retained failure
was resumed without reopening campaign execution. The marker-based authorization
was not consumed, but the explicit pre-campaign stop rule terminates this task:
the absence of a marker does not authorize a repair or another verification attempt.

Separate owner-scoped compatibility remediation and review are needed before a
new execution decision. They are outside this result task.

## Frozen identities

| Binding | Value |
| --- | --- |
| evaluationProfileId | `dora-alpha-asr-small-eval-profile-v0.1` |
| evaluationProfileCanonicalSha256 | `217b5662bfd22520779a2df2551f553cb17e97e11f0273e8238682a5f4b7f69f` |
| operatorId | `dora-alpha-asr-small-campaign-v0.1` |
| operatorCanonicalSha256 | `8d5574d0365c3796f8d73d00cd47fbec557d54ce7f28893c883bb45635141b3c` |
| modelArtifact | `ggml-small-q5_1.bin` |
| modelBytes | `190085487` |
| modelSha256 | `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` |
| modelRehashedThisTask | `False` |
| materializedManifestSha256 | `2cb05132de945fe8a1b281e79efd8efa15d9b9b94c0326f6572a594e0c74e9bb` |
| transferIndexSha256 | `95c26be0501172196587181b3f46705b65237bde879a0e3ee8eb748cee2e1a87` |
| privateFreezeSha256 | `eb088082b2cde6d2bc2649e6cb6d67a92d3a68ad447a8a7331eec58c2794bcaa` |
| runtimeVersion | `whisper.cpp v1.9.4` |
| runtimeCommit | `927cfce34f31707e17f2bff35c349632fb9e2c3a` |
| decodingProfileSha256 | `cd53fda162402675f54875de470b01c303f0ede9a2e0cefd6f1056e8fa381d39` |
| measurementProtocolSha256 | `0f1e456fb4b4b0da28a209abe94e42fe7a0ca599e5c5c794db16a6abe478b116` |
| resourceGateId | `dora-alpha-asr-small-poco-m5-resource-gates-v0.1` |
| resourceGateSha256 | `fdfb56b56429c07a99538c92d87f9ea8570c723cc67f5e5eb02915d97557ccd1` |

These are frozen expected identities. The SMALL model, native artifact bytes and
runtime checkout were not read/reverified in this task because the earlier schema
guard stopped progress. All three private metadata canonical identities did match.
The operator/profile identities, historical public hashes, branch/parent and clean
repository checks passed before the private metadata check.

## Measured results and completeness

| Result | Observation | Gate |
| --- | --- | --- |
| RU normalized aggregate errors / reference tokens / WER | Not measured | NOT_EVALUABLE |
| EN normalized aggregate errors / reference tokens / WER | Not measured | NOT_EVALUABLE |
| Duration-weighted RTF | Not measured | NOT_EVALUABLE |
| Nearest-rank p95 RTF, required rank 46 of 48 | Not measured | NOT_EVALUABLE |
| Maximum RTF | Not measured | NOT_EVALUABLE |
| Maximum cold model load | Not measured | NOT_EVALUABLE |
| Maximum observed PSS | Not measured | NOT_EVALUABLE |
| Maximum sampled native heap | Not measured | NOT_EVALUABLE |
| Evidenced OOM | 0 events; no execution | NOT_EVALUABLE |
| Maximum thermal status | No device observation | NOT_EVALUABLE |
| Memory / thermal telemetry | 0 / 0 samples; not collected | Incomplete |
| Cleanup | NOT_NEEDED_NO_DEVICE_ACCESS | No cleanup verification claim |

Missing measurements are null in the JSON, never invented zero-valued quality or
performance observations. Measured evidence is incomplete; no decisive quality or
resource failure was observed. The final candidate disposition is the frozen
`disposition(False, 'NOT_EVALUABLE', 'NOT_EVALUABLE')` result. It is a host-only
application of the frozen table, not output from a measured campaign invocation.
No campaign execution PASS or candidate rejection/acceptance is claimed.

## Operations, privacy and retention

One pre-campaign verification sequence read three private metadata documents.
One subsequent schema-presence read clarified the exact failure: **4 metadata
read operations total**. Audio content, reference content, old hypotheses/results,
model bytes, model load, decode, inference, whisper_full, ADB connection/execution,
codec/warmup and measured attempts all remain **0**.

Automatic, quality, result-dependent and manual retries are **0**. Threshold changes,
candidate tuning, reselection, rematerialization, downloads and private package writes
are **0**. Original package and failure receipts are preserved. Ephemeral precheck
and sanitized receipts reside outside Git. No measured private output exists.
No private paths, sample IDs, reference/hypothesis text or serials are published.
Private-data retention deadline remains **2026-10-25**, without extension.

## Host validation and historical preservation

Frozen SMALL host tests: **61 PASS**. All ASR host tests: **174 PASS**, including
113 unchanged historical tests. Stage 00: **all seven checks PASS**. These are
synthetic/host checks and do not repair the schema or establish campaign readiness.
JSON, links/privacy, diff, four-file scope and final preservation checks are recorded
in [aggregate evidence](../evidence/poc-asr-001/alpha-asr-small-campaign-result-stage0-v0.1.json).

The three frozen SMALL source/test files remain unchanged. 69 named
protected files carry byte/hash/Git-blob verification; all 595 original
tracked files outside the two additively amended status/backlog documents are
preserved, including historical BASE evidence, prior Stage-0 evidence, production
allowlist and Recovery. No CI was dispatched; no Android/Gradle/device tests ran.

## Repository and scope

Branch: `chat/alpha-asr-runner-scope`. Starting local and freshly fetched remote
HEAD: `d79c71eedf49bf43ee95735ae67f3acbd39f1404`. Parent: `aa4e65cf4510f055260dca6a7e6e8f26916f732e`.
The branch was attached and tracked/untracked state completely clean before the
precheck; no repository file was written until after the terminal pre-campaign stop.

Exactly four result files: this report, its JSON evidence, and additive
Stage Status / Implementation Backlog updates. One neutral atomic result commit
and push only to the existing branch; actual commit/remote readback returned afterward.

PR #86 was read-only verified through the GitHub connector as **OPEN / DRAFT /
UNMERGED**, remains out of scope, and was not modified. No new PR or merge.
Recovery remains **0D.6 = ALPHA CLOSED / FULL OPEN**. POC-ASR-001 stays
**BLOCKED / NOT_READY**. Stage 5 and Group B are not marked PASS; Stage 1 and
production readiness remain outside scope. Stop after publication; no rerun or
next candidate is authorized by this result.
