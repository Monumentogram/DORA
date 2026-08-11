# POC-SEARCH-001 evidence

Status: `FORMAL INCONCLUSIVE — RECOMMENDATION BLOCKED`

This directory contains public-safe evidence for the isolated Stage 0C Room + SQLite FTS4 experiment. It does not admit a production schema, dependency, migration, or product feature. Only synthetic generated text, deterministic identifiers, aggregate measurements, public runner metadata, and reviewed provenance records may be retained in this public repository.

## Current authoritative assessment

`evidence-index.json` is the entry point. It identifies the versioned `review-2026-08-11-v4` assessment derived from immutable Actions run `31487775567`. The v4 governance assessment records approved prospective Option B, the separate execution hold, exact review evidence, harness readiness and physical-device availability. It did not edit a completed result in place and did not perform a new measurement run.

The current assessment is `INCONCLUSIVE` with recommendation `BLOCKED`, for three independent reasons:

1. The historical campaign ran under Stage 0 v0.1, whose mandatory storage/update failure prose had no numeric predicate. The owner has now approved Option B only prospectively in `stage0-v0.2`; it was not measured and cannot reclassify the historical campaign. `GATE-SEARCH-STORAGE-UPDATE-OVERHEAD` remains `not_evaluated` for that campaign.
2. The exact 66-component license/NOTICE evidence is complete, but the assigned Project owner has not recorded `EVALUATION_APPROVED` for the dependency graph and pinned Android platform image. Evidence completeness is not evaluation-rights approval.
3. Only physical D2 is recorded. Physical D1 and D3 are not recorded, and the Project owner has explicitly withheld measured execution.

The evaluated emulator latency, correctness, safety, update-integrity, mapping, rebuild, and cleanup observations remain useful measurements. They do not produce a gate-complete host `PASS` or a `GO` recommendation.

## Preconditions before another measured campaign

The paired control/indexed harness prerequisite is complete at commit `b5bcf0951f3cb16d3fec65174395e7715c49a7d7`: debug and benchmark source sets compile, synthetic combiner tests pass, and both push and pull-request API 36 emulator runtime-smoke jobs pass. Those smoke timings are not gate evidence, and no measured workflow ran.

Option B is approved as the prospective contract, but no new targeted or full benchmark may run until all three remaining conditions are recorded:

- the assigned Project owner explicitly approves or rejects the exact Stage 0 component/license/NOTICE packet in `ip-stage0-evaluation-review.md`;
- matching physical D1, D2 and D3 availability plus a fresh exact-commit preflight are recorded;
- after those prerequisites, the Project owner separately authorizes measured execution and `benchmarkExecutionAllowed` changes from `false` to `true` in a reviewed commit.

`OD-11` records Project owner as Product and Stage 0 IP policy reviewer and as Engineering/Security reviewer only for Stage 0 evaluation. It explicitly does not replace independent production Security; production Legal is unassigned and blocked.

`OD-12` records the prospective Option B selection and its independent local-MVP rationale. It also records that contract approval is not benchmark authorization and does not use prior Dora measurements to set the threshold.

`tools/check_poc_search_run_readiness.py` enforces these preconditions in the measured workflow. It currently fails closed by design.

## Frozen before the first full run

- Dataset: `poc-search-synthetic-v1`, generator `search-generator-1.0.0`, seed `2026081001`.
- Scale: exactly 10,000 conversations and 1,000,000 transcript segments (100 per conversation).
- Logical dataset digest: `sha256:a3ad26892fc9f3abfcce26c3338a44a27dbe55e5048148f49edfc36c8fb8310a`.
- Query campaign: 61 fixed cases, 34 latency-eligible cases, five warmups per eligible case, 30 measured repetitions, and 1,020 measured operations.
- Percentiles: nearest-rank over the fixed combined campaign; timing uses `SystemClock.elapsedRealtimeNanos`.
- Mutation campaign: five fixed add/update/filter/delete operations. Correctness, stale results, mapping loss, and crashes are evaluated. The unresolved numeric overhead predicate is not silently discarded.
- Logical rebuild determinism: equal canonical counts, generated dataset digest, ordered logical FTS row/text digest, and frozen query results. Byte-for-byte SQLite file equality is not required.

The original manifest file SHA-256 values are:

- `dataset-manifest.json`: `e7ffaa57a731ba347d791fd3bbe33a031a5c7538e923c2b55602ece5822a3db6`.
- `query-manifest.json`: `69f9448d5b6585061d5aae83686da423fd58d2a2639bc7ab7779d592ade576b4`.
- `mutation-manifest.json`: `7c75a0962ab7a60a83ea27aefa4630f54e198c1362c72edaf1ac1360e86c3b83`.

None of these contracts or historical thresholds was changed by the review.

## Durable evidence and versioning

Actions artifacts are only a transfer buffer. The workflow retention buffer is now 90 days, but a run is not durable evidence until its sanitized files are imported and added to `evidence-ledger.json`.

The repository now retains:

- `runs/31484440781/`: the complete valid FAIL result, raw combined observations, detail files, and targeted observation;
- `runs/31486943815/`: the standalone targeted plan evidence;
- `runs/31487775567/`: the original finalizer output, raw combined observations, detail files, and targeted observation;
- `assessments/review-2026-08-11-v2/`: the superseded first P1 non-measurement assessment;
- `assessments/review-2026-08-11-v3/`: the superseded pre-selection governance assessment;
- `assessments/review-2026-08-11-v4/`: the current non-measurement governance assessment;
- `evidence-ledger.json`: immutable locators, file sizes, SHA-256 values, Actions artifact IDs, classifications, and environment corrections.

The root v1 result files are retained as superseded historical output. They are not the current assessment. Validators follow `evidence-index.json`, verify every repository locator and digest, and require both the valid FAIL and targeted evidence to remain present.

Generated database files and the one-million-row corpus are never committed. The validator rejects retained `.db`, `.sqlite`, or `.sqlite3` files.

## Environment correction

The raw Android observations from all retained runs recorded `kind: physical`, while the same records identify `sdk_gphone64_x86_64`, device `emu64xa`, a `google/sdk_gphone64_x86_64/...` fingerprint, and `Android virtual processor`. The original one-token classifier only looked for `generic` in the fingerprint.

Raw files remain immutable. `evidence-ledger.json` records the correction, and the v2/v3/v4 environment assessments report `emulator` without changing any metric. The harness now classifies multiple independent build fields and records brand and hardware. Kotlin tests cover Google/AOSP emulators and physical Google/Samsung devices. Python finalization and validation independently derive the kind and reject a mismatch.

## Dependency lock and IP inventory

`android/poc/search/gradle.lockfile` pins the complete PoC dependency graph. `dependency-inventory.json` covers exactly 66 components from both debug and formal benchmark runtime/AndroidTest/Room-KSP configurations, with 52 binary AAR/JAR digests and 66 Maven POM digests/source URLs. CI regenerates and compares this inventory after resolving the build.

`license-notice-inventory.json` resolves effective license evidence for all 66 coordinates (62 Apache-2.0, one BSD-2-Clause, two BSD-3-Clause, one EPL-1.0 and one MIT; one component carries two identifiers), classifies and hashes all 19 discovered embedded license entries, and records three NOTICE entries. No license coordinate, embedded license file, or discovered NOTICE file remains unresolved. `ip-evaluation.json` is intentionally `EXACT_EVIDENCE_COMPLETE_OWNER_APPROVAL_PENDING`, not an approval; `ip-stage0-evaluation-review.md` leaves the owner decision block incomplete.

The API 36 Google APIs x86_64 r07 archive is pinned by byte length, official SHA-1 and independently computed SHA-256. Under `OD-11`, embedded platform SQLite 3.44.3 uses that containing-system-image digest together with exact image ID/revision, fingerprint, API and ABI; no extracted SQLite-binary digest is required for Stage 0. This provenance method must be reconsidered before production admission and grants no evaluation or redistribution rights by itself.

## Invalid runs

- Run `31393093186` at `98c4de021e18dc8823be4d1028622b38e2ecc1f5` is `INVALID`: the original 120-minute harness ceiling elapsed before observations were written.
- Run `31406399307` at `2838481cb1b0e2eb1c348f2cd6b25c29cf75c893` is `INVALID`: GitHub's 360-minute ceiling elapsed after both rebuilds but before complete observations. No partial metric was admitted.
- Run `31438814885` at `1db7a97f0d36b4d2dfacdc395f9ac7cef1390c32` is `INVALID`: it was cancelled during the second rebuild correctness pass. Diagnostics isolated the FTS/source count plan defect; no partial metric was admitted.

The three invalid runs did not change manifests, repetitions, percentile definitions, or gates.

## Retained measured runs

- Run `31484440781` at `b74aa4f0cc42f65c15502d1018aa68ee3b7b293f` is a valid implementation `FAIL`. Both generated 10k/1M builds passed all 61 correctness cases, plan safety, mutations, deterministic rebuild, and cleanup, but emulator p95 was `247.435677 ms`, above the approved `200 ms` latency gate; p99 was `270.842256 ms`.
- Targeted run `31486943815` at `9571e5f2e7cceeab95b52f1e1167770518a3e475` returned the expected 175,000 source-filter count. The bound count query took `190.605659 ms`; the corrected page query took `3.432928 ms`, was FTS4-driven, and avoided the temporary order-by B-tree.
- Full run `31487775567` at the same commit completed all checkpoints. Its frozen 1,020-operation emulator campaign measured p50 `0.820430 ms`, p95 `97.537984 ms`, p99 `144.481302 ms`, and max `153.800700 ms`; both independent 10k/1M builds passed 61/61 correctness cases and the discrete mutation/rebuild/cleanup checks.

The original finalizer labeled the last run host `PASS`/`GO`. That conclusion is superseded because one mandatory gate was not evaluated and external-artifact approval was unsupported. The measurements are retained; the current verdict is not weakened or promoted retroactively.
