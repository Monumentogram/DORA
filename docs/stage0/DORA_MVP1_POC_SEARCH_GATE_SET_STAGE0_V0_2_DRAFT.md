# Dora MVP 1 — POC-SEARCH-001 Gate Set `stage0-v0.2` draft

Status: **DRAFT_UNAPPROVED**\
Decision: `DEC-043`\
Owner: Project owner\
Prepared: 11 August 2026\
Measured execution allowed: **no**

## 1. Scope and precedence

This is a prospective draft for `POC-SEARCH-001`. It does not supersede approved Gate Set
`stage0-v0.1`, does not change a historical result and does not authorize a benchmark. All v0.1
latency, correctness, adversarial-safety, canonical-mapping and deterministic-rebuild gates remain
unchanged. This draft supplies only the missing storage/update predicates.

The selected option becomes normative only after the Project owner updates `DEC-043`, records an
approval date and changes the machine-readable companion to `APPROVED` with exactly one selected
option.

## 2. Fixed reference scale

| Field | Required value |
|---|---:|
| Conversations | 10,000 |
| Transcript segments | 1,000,000 |
| Segments per conversation | 100 |
| Input | deterministic synthetic text only |
| Canonical/control pair | same fixture, schema and SQLite settings; FTS table/triggers omitted only from control |
| Fresh builds | 3 paired builds per required physical profile |

The fixture manifest, generator version, seed, logical digest, commit and database schemas are
frozen before any approved run. A schema or harness correction produces a new commit and preserves
all failed/invalid evidence.

## 3. Environment

Formal performance evaluation requires physical D1, D2 and D3 devices from
`device-matrix.yaml`. The following are fixed per campaign:

- `arm64-v8a`, exact model/build/security patch/API/RAM/storage class;
- release-like nondebuggable, profileable benchmark APK; full/AOT compilation state recorded;
- Room version, dependency lock, SQLite `sqlite_version()`, page size and compile options recorded;
- `journal_mode=WAL`, `synchronous=FULL`, `wal_autocheckpoint=1000`, `foreign_keys=ON` and
  `temp_store=DEFAULT` for control and candidate;
- airplane mode, account/GMS/network not required, screen on at fixed 20% brightness, animations
  disabled, no charging, battery 40–80%, thermal state `NONE` at start and never above `LIGHT`;
- at least ten minutes of cooldown before each fresh paired build;
- after the control build, free bytes must be at least the profile minimum and at least four times
  `(canonical_main_db_bytes + selected_option_max_incremental_bytes)` before the indexed build.

The pinned `system-images;android-36;google_apis;x86_64` revision 7 emulator is mandatory for
harness/provenance validation only. Its timings cannot satisfy D1–D3 physical gates.

## 4. Normalized storage protocol

For each control/candidate database:

1. build the exact canonical fixture;
2. build the candidate index only in the indexed database;
3. verify canonical logical digest, counts, `PRAGMA integrity_check` and mapping invariants;
4. require successful `PRAGMA wal_checkpoint(TRUNCATE)` with no busy result;
5. run `VACUUM`;
6. require a second successful truncate checkpoint, close every connection, and require WAL/SHM
   absent or zero bytes;
7. record main-file bytes and `page_count * page_size` and require equality.

Metrics:

```text
search.storage.index_incremental_bytes
  = indexed_main_db_bytes - canonical_only_main_db_bytes

search.storage.index_overhead_ratio
  = search.storage.index_incremental_bytes / canonical_only_main_db_bytes

search.storage.index_overhead_bytes_per_segment
  = search.storage.index_incremental_bytes / 1_000_000
```

Aggregation is `max` across the three fresh builds and then `max` across D1, D2 and D3. A negative
incremental value is retained and investigated; it is never replaced by zero.

## 5. Update protocol

Operation classes, each using deterministic disjoint IDs:

| Class | Cardinality | Group |
|---|---:|---|
| `ADD_CONVERSATION_100` | one conversation plus 100 segments | bulk-100 |
| `UPDATE_SEGMENT_TEXT_1` | one segment | single-row |
| `UPDATE_CONVERSATION_FILTER_1` | one conversation filter field | single-row |
| `DELETE_SEGMENT_1` | one segment | single-row |
| `DELETE_CONVERSATION_100` | one conversation plus 100 cascading segments | bulk-100 |

Each fresh build executes 10 warm-ups and 100 measured operations per class in both control and
candidate databases. Warm-ups are excluded. The paired database order alternates control-first,
candidate-first, control-first across the three builds. Every profile therefore contributes 300
measured samples per class.

Timing uses `SystemClock.elapsedRealtimeNanos` and includes transaction begin, statements, FTS
maintenance and commit return. It excludes fixture setup and correctness queries.

```text
search.update.maintenance_delta_ms[i]
  = indexed_commit_ms[i] - canonical_only_commit_ms[i]

search.update.visibility_latency_ms[i]
  = first_expected_search_response_end - indexed_commit_return
```

The first verification query starts immediately; an asynchronous candidate may poll every 10 ms
until its selected visibility deadline. Before readiness it may return explicit `INDEXING`, never
old results as successful current results. Any stale success, mapping error, crash or lost mutation
is an immediate approved integrity failure.

For each profile and operation class, sort all 300 samples and use nearest-rank
`value[ceil(p × N)]` for p95/p99. Gate aggregation is the maximum percentile across required
profiles and applicable classes. Profiles and operation classes are never averaged together.

## 6. Candidate predicates

Every value remains `Proposed` until one complete row is selected. All predicates in a selected row
are conjunctive.

| Option | Incremental bytes | Ratio | Bytes/segment | Single-row delta p95 | Bulk-100 delta p95 | Indexed commit p99 | Visibility p95 | Visibility p99 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| A | ≤268,435,456 | ≤0.50 | ≤256 | ≤16 ms | ≤100 ms | ≤250 ms | ≤100 ms | ≤250 ms |
| B | ≤536,870,912 | ≤1.00 | ≤512 | ≤50 ms | ≤250 ms | ≤500 ms | ≤250 ms | ≤1,000 ms |
| C | ≤805,306,368 | ≤1.50 | ≤768 | ≤100 ms | ≤500 ms | ≤700 ms | ≤1,000 ms | ≤5,000 ms |

Gate IDs after approval:

- `GATE-SEARCH-STORAGE-INCREMENTAL` — success only when all three storage limits pass;
- `GATE-SEARCH-UPDATE-MAINTENANCE` — success only when all three commit/overhead limits pass;
- `GATE-SEARCH-UPDATE-VISIBILITY` — success only when both visibility limits pass and no stale
  success response occurs;
- `GATE-SEARCH-STORAGE-UPDATE-FAILURE` — triggered when any selected numeric predicate fails.

Missing D1/D2/D3, fewer than three valid paired builds, fewer than 300 samples per class/profile,
or an unapproved option yields `INCONCLUSIVE`, not `PASS`.

## 7. Invalidation and failure classification

Predeclared invalidation reasons are limited to fixture/commit/settings drift, unavailable required
metadata, checkpoint busy, insufficient free-space preflight, external OS interruption, or thermal
state above `LIGHT` before the measured block. The complete attempt is retained.

A candidate crash, stale successful result, canonical/index mapping loss, failed integrity check,
lost mutation or non-deterministic rebuild is a candidate `FAIL`, not an invalid benchmark. No
timing outlier may be deleted.

## 8. Fallback

- Storage failure: remove stored-content duplication with reviewed external/contentless FTS or
  split by entity; preserve transactional ordering and rebuild/mapping verification.
- Maintenance failure: move indexing off main/UI work, coalesce a durable queue, and expose an
  explicit `INDEXING` state until correct results are ready.
- Visibility failure: use bounded per-entity/on-demand indexing; never return a stale result as
  current.
- Missing physical profiles: keep emulator or available-device data as observation and retain
  `INCONCLUSIVE`.
- FTS5, a vector database, production schema or dependency admission requires separate scope and
  an ADR; it is not authorized here.

## 9. Activation checklist

- [ ] Project owner selects exactly one option or records a modified full row.
- [ ] Independent rationale and date are recorded in `DEC-043`.
- [ ] Machine-readable status becomes `APPROVED` and `selectedOptionId` is non-null.
- [ ] Exact dependency/platform Stage 0 evaluation reaches `EVALUATION_APPROVED`.
- [ ] D1, D2 and D3 physical availability and preflight are recorded.
- [ ] Harness implements the paired control, repetitions, aggregation and result gate IDs.
- [ ] Validators pass before any measured workflow dispatch or tag.

Until every applicable item is complete, `benchmarkExecutionAllowed` remains `false`.
