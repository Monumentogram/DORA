# POC-SEARCH-001 evidence

Status: `HOST/EMULATOR GATES PASS — FORMAL INCONCLUSIVE`

This directory contains the public-safe evidence contract for the isolated Stage 0C Room + SQLite FTS4 experiment. The generated database and the one-million-row transcript corpus are never committed or retained as workflow artifacts. Only generator source, deterministic manifests, canonical identifiers, aggregate measurements, and sanitized result files are eligible for Git.

## Frozen before the first full run

- Dataset: `poc-search-synthetic-v1`, generator `search-generator-1.0.0`, seed `2026081001`.
- Scale: exactly 10,000 conversations and 1,000,000 transcript segments (100 per conversation).
- Logical dataset digest: `sha256:a3ad26892fc9f3abfcce26c3338a44a27dbe55e5048148f49edfc36c8fb8310a`.
- Query campaign: 61 fixed cases, 34 latency-eligible cases, five warmups per eligible case, 30 measured repetitions, and 1,020 measured operations in the combined gate campaign.
- Percentiles: nearest-rank over the fixed combined campaign; timing uses `SystemClock.elapsedRealtimeNanos`.
- Mutation campaign: five fixed add/update/filter/delete operations; mutation latency is observation-only because the approved Gate Set has no numeric mutation-latency threshold.
- Logical rebuild determinism: equal canonical counts, generated dataset digest, ordered logical FTS row/text digest, and frozen query results. Byte-for-byte SQLite file equality is explicitly not required.

The manifests were generated before any full SQLite benchmark result was observed:

- `dataset-manifest.json`: file SHA-256 `e7ffaa57a731ba347d791fd3bbe33a031a5c7538e923c2b55602ece5822a3db6`.
- `query-manifest.json`: file SHA-256 `69f9448d5b6585061d5aae83686da423fd58d2a2639bc7ab7779d592ade576b4`.
- `mutation-manifest.json`: file SHA-256 `7c75a0962ab7a60a83ea27aefa4630f54e198c1362c72edaf1ac1360e86c3b83`.

## Measurement boundary

Mandatory CI runs a small generated-data Room/FTS4 smoke suite. The full reference campaign runs only through the separate commit-bound workflow and records the runner, Android API, ABI, emulator build, CPU/RAM information, build type, database sizes, preparation phases, sampled memory, correctness, latency, mutation behavior, and rebuild behavior.

Before the full campaign can start, a targeted 10k/1M preflight executes the exact bound
`Q-SEARCH-SOURCE` plan and requires FTS4 to drive canonical rowid lookups. Its 30-second
per-operation guard and 10-minute CI-step timeout exist only to stop a pathological plan quickly;
they are not approved product gates and do not contribute a verdict. A passing full campaign runs
query, rebuild/mutation, and independent-secondary phases as separate jobs, retains each completed
sanitized checkpoint, and combines them only after commit, manifest, environment, count, digest,
correctness, and query-plan consistency checks pass.

Host/emulator measurements can establish generated-scale correctness and exploratory feasibility. They cannot establish real-phone latency or a supported-device claim. Unless an approved failure gate is triggered, the formal result therefore remains `INCONCLUSIVE` until a future physical D1–D3 campaign exists.

The Room/FTS4 schema in `:poc:search` is PoC-only. It is not a production schema, architecture admission, migration commitment, or permission to start production functionality.

## Result files

Full workflow run `31487775567` produced and schema-validated these sanitized files before they were admitted here:

- `benchmark-result.json` against `docs/stage0/benchmark-result.schema.json`;
- `environment.json`;
- `query-result.json`;
- `mutation-result.json`;
- `rebuild-result.json`.

The five generated result files match the final workflow artifact byte-for-byte. The frozen
dataset, query and mutation manifest file SHA-256 values above remain the original pre-run
Windows-worktree provenance values. Machine evidence locators verify the canonical LF Git blobs
required by `.gitattributes`; the validator normalizes CRLF only for that cross-platform byte
comparison. Manifest JSON content, the 61-case campaign, repetitions, percentile definition and
approved gates were not changed after any observed result.

Any harness defect invalidates that run. Its fact must be preserved in this README before a corrected, unchanged-contract campaign is used for a verdict.

## Invalid runs

- Full workflow run `31393093186` at commit `98c4de021e18dc8823be4d1028622b38e2ecc1f5`
  is `INVALID`: the job reached the harness's original 120-minute ceiling while the single
  instrumentation test was still running, before any observation or result artifact was written.
  No gate was evaluated from this run. The frozen dataset, query/mutation manifests, repetition
  plan, percentile definition, and approved gates were not changed. The harness correction keeps
  both required full-scale 1M-row rebuilds and the independent second 1M-row build, moves the
  scale-independent corruption/recovery boundary to a separate 10-conversation/1,000-row fully
  generated index, emits phase progress, and gives the reproducible workflow sufficient runtime.
- Full workflow run `31406399307` at commit `2838481cb1b0e2eb1c348f2cd6b25c29cf75c893`
  is `INVALID`: the job reached GitHub's 360-minute hosted-job ceiling after both required
  full-scale rebuilds, before the harness could write observations. The phase log proved that
  database generation, initial indexing, both rebuilds and the 1,020-operation latency campaign
  completed, but each correctness pass spent about 115 minutes executing count queries that
  redundantly joined common FTS matches through both canonical tables. No partial timing or gate
  outcome is admitted. The correction retains the frozen manifests, query mix, repetitions,
  percentile definition and gates, while using the FTS doclist directly for semantically
  equivalent unfiltered MATCH counts and adding per-query phase diagnostics.
- Full workflow run `31438814885` at commit `1db7a97f0d36b4d2dfacdc395f9ac7cef1390c32`
  is `INVALID`: the job was cancelled after approximately 356 minutes while the second rebuild
  correctness pass was still executing `Q-SEARCH-SOURCE`, before observations or result evidence
  could be written. Phase diagnostics isolate the defect to the `FTS MATCH + source_type + COUNT`
  query: the baseline execution took about 140 minutes, the first-rebuild execution took about
  141 minutes, and the second-rebuild execution was still running after about 67 minutes when the
  job ended. Database preparation completed in about 88 seconds, the frozen 1,020-operation
  latency campaign completed in about 132 seconds, and each full-scale rebuild itself completed
  in 24–31 seconds. No partial metric or gate outcome is admitted. The correction preserves
  parameter binding and all frozen manifests, repetitions, percentile definitions and approved
  gates; it makes FTS4 the driving table, verifies the full-scale query plan before another full
  campaign, and splits the campaign into independently retained phase checkpoints.

## Valid runs and outcome

- Full workflow run `31484440781` at commit `b74aa4f0cc42f65c15502d1018aa68ee3b7b293f`
  is a valid implementation `FAIL`, not an invalid run. Both generated 10k/1M builds passed all
  61 correctness cases, query-plan safety, mutation, deterministic rebuild and cleanup checks,
  but exploratory emulator p95 was `247.435677 ms`, above the approved `200 ms` failure gate;
  p99 was `270.842256 ms`. Evidence isolated the latency to a temporary B-tree sort performed
  before the limited result page was returned. This result remains part of the evidence history.
- Targeted full-scale run `31486943815` at commit
  `9571e5f2e7cceeab95b52f1e1167770518a3e475` validated the corrected page plan before another
  full campaign. `Q-SEARCH-SOURCE` returned the expected count of `175000`; its bound count query
  took `190.605659 ms`, while the result page fell from about `190 ms` to `3.432928 ms`. Both
  EXPLAIN plans are FTS4-driven and the page plan contains no temporary order-by B-tree.
- Final full workflow run `31487775567` at the same commit completed every checkpointed job and
  finalizer. The host/emulator exploratory outcome is `PASS` with recommendation `GO`: p50
  `0.820430 ms`, p95 `97.537984 ms`, p99 `144.481302 ms`, and maximum `153.800700 ms` over the
  frozen 1,020-operation campaign. Both independent 10k/1M builds passed all `61/61` correctness
  cases; parameter binding, accepted count/page plans, mutation correctness, deterministic
  rebuild, checkpoint consistency and temporary-database cleanup all passed. Summed benchmark
  phase time was `525.82291096 s`; the complete workflow finished in about 20 minutes rather than
  hours.

The formal result remains `INCONCLUSIVE`, not `PASS`, solely because no physical D1–D3 latency
campaign was run. This is sufficient evidence to close the isolated PoC as generated-scale
host/emulator feasibility; it does not admit FTS4, the PoC schema, or any production dependency.
