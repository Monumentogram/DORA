# Dora MVP 1 — POC-SEARCH-001 Gate Set `stage0-v0.2`

Status: **APPROVED CONTRACT — EXECUTION NOT AUTHORIZED**\
Decision: `DEC-043`\
Selected option: **B**\
Approved by: **Project owner**\
Approved on: **2026-08-11**\
Measured execution allowed: **no**

## 1. Scope and precedence

This prospective contract supplies the numeric storage/update predicates missing from
`stage0-v0.1`. It inherits the v0.1 latency, correctness, adversarial-safety, canonical-mapping,
update-integrity, and deterministic-rebuild gates without weakening them. Historical results stay
under v0.1 and are not reclassified.

Contract approval and execution authorization are separate states. Option B is normative, but no
benchmark may run while `benchmarkExecutionAllowed` is `false`.

## 2. Fixed scale and pairing

| Field | Required value |
|---|---:|
| Conversations | 10,000 |
| Transcript segments | 1,000,000 |
| Segments per conversation | 100 |
| Input | deterministic synthetic text only |
| Database pair | canonical-only control and indexed candidate from the same fixture |
| Fresh paired builds | 3 per required physical profile |
| Required physical profiles | D1, D2, D3 |

The control and candidate use the same canonical schema, data, SQLite settings, and operation
schedule. Only the candidate contains and maintains the FTS index. The fixture manifest,
generator version, seed, logical digest, schemas, gate-set digest, and commit are frozen before an
authorized campaign.

## 3. Environment

Each formal execution records and fixes:

- physical `arm64-v8a` D1, D2, and D3 model/build/security patch/API/RAM/storage class;
- release-like nondebuggable, profileable benchmark build and recorded full/AOT compilation state;
- Room version, dependency lock, SQLite `sqlite_version()`, page size, and compile options;
- `journal_mode=WAL`, `synchronous=FULL`, `wal_autocheckpoint=1000`, `foreign_keys=ON`, and
  `temp_store=DEFAULT` for both databases;
- airplane mode, no account/GMS/network dependency, screen on at 20% brightness, animations off,
  unplugged battery 40–80%, thermal `NONE` at start and never above `LIGHT`;
- at least ten minutes cooldown before each fresh paired build;
- free space at least the D-profile minimum and at least four times
  `(canonical_main_db_bytes + 536870912)` after the control build.

The pinned API 36 x86_64 emulator validates only harness behavior and provenance. Emulator timing
cannot satisfy D1–D3 storage/update gates.

## 4. Storage normalization and metrics

For each control/candidate database:

1. build and verify the exact canonical fixture;
2. build the index only for the candidate;
3. verify counts, canonical logical digest, `PRAGMA integrity_check`, and mapping invariants;
4. require successful `PRAGMA wal_checkpoint(TRUNCATE)` with no busy result;
5. run `VACUUM`;
6. require a second successful truncate checkpoint and close all connections;
7. require WAL/SHM absent or zero and main-file bytes equal `page_count * page_size`.

```text
index_incremental_bytes = indexed_main_db_bytes - canonical_only_main_db_bytes
index_overhead_ratio = index_incremental_bytes / canonical_only_main_db_bytes
index_overhead_bytes_per_segment = index_incremental_bytes / 1_000_000
```

Storage aggregation is the maximum across three fresh builds and then the maximum across D1, D2,
and D3. Negative incremental values are retained and investigated, never clamped to zero.

## 5. Update protocol

| Class | Cardinality | Group |
|---|---:|---|
| `ADD_CONVERSATION_100` | one conversation plus 100 segments | bulk-100 |
| `UPDATE_SEGMENT_TEXT_1` | one segment | single-row |
| `UPDATE_CONVERSATION_FILTER_1` | one conversation filter field | single-row |
| `DELETE_SEGMENT_1` | one segment | single-row |
| `DELETE_CONVERSATION_100` | one conversation plus 100 cascading segments | bulk-100 |

Each fresh build executes 10 warm-ups and 100 measured operations per class and database using
deterministic disjoint IDs. Pair order is control-first, candidate-first, control-first for builds
1–3. Every profile contributes 300 measured samples per class.

Timing uses `SystemClock.elapsedRealtimeNanos` and includes transaction begin, canonical
statements, index maintenance, and commit return. Fixture setup and correctness queries are
excluded.

```text
maintenance_delta_ms[i] = indexed_commit_ms[i] - canonical_only_commit_ms[i]
visibility_latency_ms[i] = first_expected_search_response_end - indexed_commit_return
```

The first visibility query starts immediately. An asynchronous implementation may poll every
10 ms and return explicit `INDEXING` before readiness; it may never return an old result as a
successful current result.

## 6. Aggregation

For each physical profile and operation class, sort all 300 samples. Nearest-rank percentile is
`value[ceil(p * N)]`, using one-based rank. Report p95 and p99. The gate takes the maximum
percentile across required profiles and applicable operation classes; profiles/classes are never
averaged and no timing outlier is deleted.

## 7. Approved Option B predicates

All predicates are conjunctive.

| Gate | Approved threshold |
|---|---:|
| incremental compacted index bytes | ≤536,870,912 |
| compacted index/canonical ratio | ≤1.00 |
| incremental bytes per segment | ≤512 |
| worst single-row maintenance delta p95 | ≤50 ms |
| worst bulk-100 maintenance delta p95 | ≤250 ms |
| worst indexed commit p99 | ≤500 ms |
| visibility p95 | ≤250 ms |
| visibility p99 | ≤1,000 ms |
| stale successful responses | 0 |
| mapping errors, lost mutations, candidate crashes | 0 |

Gate IDs:

- `GATE-SEARCH-STORAGE-INCREMENTAL`;
- `GATE-SEARCH-UPDATE-MAINTENANCE`;
- `GATE-SEARCH-UPDATE-VISIBILITY`;
- `GATE-SEARCH-STORAGE-UPDATE-FAILURE`.

## 8. Invalidation and result rules

Predeclared invalidation is limited to fixture/commit/settings drift, missing required metadata,
checkpoint busy, insufficient free-space preflight, external OS interruption, or thermal state
above `LIGHT` before a measured block. Preserve every attempt.

A candidate crash, stale successful result, lost mutation, canonical/index mapping loss, failed
integrity check, or non-deterministic rebuild is `FAIL`, not an invalid run. Missing D1/D2/D3,
fewer than three valid paired builds, or fewer than 300 samples per class/profile yields
`INCONCLUSIVE`, never `PASS`.

## 9. Fallback

- Storage failure: reviewed external/contentless or per-entity FTS.
- Maintenance failure: durable coalesced off-main queue with an explicit `INDEXING` state.
- Visibility failure: bounded per-entity/on-demand indexing; stale success is forbidden.
- Missing physical profile: retain available observations as `INCONCLUSIVE` without a support
  claim.
- FTS5/vector search/production schema: separate scope and ADR.

## 10. Execution hold

- [x] Project owner selected Option B.
- [x] Independent rationale and approval date are recorded in `DEC-043`.
- [x] Machine-readable status is `APPROVED` with `selectedOptionId=B`.
- [ ] Exact component/license/NOTICE review is explicitly `EVALUATION_APPROVED`.
- [ ] New paired harness is implemented and verified against this complete contract.
- [ ] Physical D1, D2, and D3 availability and preflight are recorded.
- [ ] Project owner records a later execution authorization.

Until every unchecked item is complete, `benchmarkExecutionAllowed` remains `false` and every
measured workflow must fail closed before provisioning a device or running instrumentation.
