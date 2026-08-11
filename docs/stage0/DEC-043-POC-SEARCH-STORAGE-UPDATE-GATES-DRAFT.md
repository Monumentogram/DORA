# DEC-043 — POC-SEARCH-001 storage/update gates

Status: **Draft / Proposed — owner has not selected or approved an option**\
Date prepared: 11 August 2026\
Decision owner: Project owner\
Scope: prospective Stage 0 `POC-SEARCH-001` campaign only\
Companion gate set: `DORA_MVP1_POC_SEARCH_GATE_SET_STAGE0_V0_2_DRAFT.md`\
Machine-readable draft: `poc-search-gate-set-stage0-v0.2.draft.json`

## Decision required

Select one storage/update option, modify one prospectively, or reject all options. Until the
Project owner records that decision, Gate Set `stage0-v0.2` is not approved and no targeted or
full measured benchmark may run.

This DEC cannot change any result produced under `stage0-v0.1`. Historical measurements remain
immutable and are not used to select a threshold.

## Problem

The approved `stage0-v0.1` search row declares that a storage or update gate failure is a
mandatory failure condition, but it supplies no numeric predicate and no status exception. A
retrospective interpretation would violate the pre-run rule in `DORA_MVP1_POC_GATES.md`.

The missing contract has three distinct parts:

1. incremental compacted storage attributable to the search index;
2. transactional maintenance cost relative to the same canonical mutation without the index;
3. time from canonical commit until the change is visible in search, without ever returning a
   stale success response.

## Independent basis

The option bands below were constructed from requirements and public platform documentation, not
from any completed Dora search result:

- SQLite documents that FTS4 normally maintains shadow tables and that `content=` can avoid a
  second stored copy of indexed text, with significant space savings. This supports explicit
  half-copy, one-copy, and capacity-ceiling storage policies rather than a threshold fitted to a
  measured database: <https://www.sqlite.org/fts3.html#the_content_option>.
- SQLite defines `VACUUM` as rebuilding a database into a minimal packed representation and
  `wal_checkpoint(TRUNCATE)` as checkpointing and truncating the WAL to zero. Those operations
  define the normalized storage boundary: <https://www.sqlite.org/lang_vacuum.html> and
  <https://www.sqlite.org/pragma.html#pragma_wal_checkpoint>.
- Android recommends physical devices for performance benchmarks because emulator numbers are
  tied to host hardware. Emulator evidence is therefore harness/provenance evidence only:
  <https://developer.android.com/topic/performance/benchmarking/benchmarking-in-ci#use-real-devices>.
- Android uses 16 ms as the smooth-frame target, 700 ms as the frozen-frame boundary, and 5 s as
  the foreground ANR boundary, while requiring long work to stay off the UI thread:
  <https://developer.android.com/topic/performance/vitals/render>.

The Android values do not prescribe a database SLA. They are independent outer anchors for the
three policy bands. Every option still requires all database work off the main thread.

## Common measurement contract

All three options use the exact protocol in the companion Gate Set:

- fixed synthetic scale: 10,000 conversations, 1,000,000 transcript segments, 100 segments per
  conversation;
- three fresh paired builds per required physical profile D1, D2 and D3;
- one canonical-only control database and one indexed candidate database from the same fixture;
- five operation classes: add conversation/100 segments, update one segment, update one
  conversation filter, delete one segment, delete conversation/100 segments;
- 10 unmeasured warm-ups followed by 100 measured operations per class, database and fresh build;
- 300 measured samples per operation class and profile;
- nearest-rank p95/p99 inside each profile and operation class; the gate uses the worst required
  profile and worst applicable operation class, never an average across profiles;
- pinned release-like non-debuggable build, exact Room/SQLite/system-image identity, fixed SQLite
  pragmas, fixed device state, monotonic clock and predeclared invalidation rules;
- the pinned API 36 x86_64 emulator validates the harness but cannot replace D1–D3 physical flash
  evidence. Missing D1 or D3 leaves the formal result `INCONCLUSIVE`.

## Exact metrics

After `wal_checkpoint(TRUNCATE)`, `VACUUM`, a second successful truncate checkpoint and close:

```text
index_incremental_bytes = indexed_main_db_bytes - canonical_only_main_db_bytes
index_overhead_ratio = index_incremental_bytes / canonical_only_main_db_bytes
index_overhead_bytes_per_segment = index_incremental_bytes / 1_000_000
```

For matched deterministic mutation `i`:

```text
maintenance_delta_ms[i] = indexed_commit_ms[i] - canonical_only_commit_ms[i]
visibility_latency_ms[i] = first_expected_search_response_end - indexed_commit_return
```

Negative maintenance deltas are retained. No outlier is deleted. A post-commit response containing
old results is an immediate update-integrity failure; an asynchronous fallback may return an
explicit `INDEXING` state until the correct result is ready.

## Options

All listed predicates are conjunctive: exceeding any limit triggers the storage/update failure
gate. None is approved merely by appearing in this draft.

| Option | Storage limits | Update-maintenance limits | Visibility limits | Independent policy rationale |
|---|---|---|---|---|
| **A — duplication-averse / immediate** | incremental bytes ≤256 MiB; ratio ≤0.50; ≤256 bytes/segment | worst single-row p95 delta ≤16 ms; worst 100-row bulk p95 delta ≤100 ms; worst indexed commit p99 ≤250 ms | p95 ≤100 ms; p99 ≤250 ms | Requires a compact/content-external or per-entity design and treats one UI-frame interval as the strict single-row serial-work budget, while still forbidding main-thread I/O. |
| **B — balanced local baseline** | incremental bytes ≤512 MiB; ratio ≤1.00; ≤512 bytes/segment | worst single-row p95 delta ≤50 ms; worst 100-row bulk p95 delta ≤250 ms; worst indexed commit p99 ≤500 ms | p95 ≤250 ms; p99 ≤1,000 ms | Allows at most one additional canonical-sized storage budget and keeps p99 commit below Android's 700 ms frozen-frame outer boundary with explicit headroom. This is the draft engineering preference, not an owner decision. |
| **C — capacity-first / explicit pending state** | incremental bytes ≤768 MiB; ratio ≤1.50; ≤768 bytes/segment | worst single-row p95 delta ≤100 ms; worst 100-row bulk p95 delta ≤500 ms; worst indexed commit p99 ≤700 ms | p95 ≤1,000 ms; p99 ≤5,000 ms | Uses Android's frozen-frame and foreground-ANR boundaries only as hard outer anchors. It is acceptable solely with off-main execution and a visible non-stale `INDEXING` state. |

## Fallbacks

- **Option A:** use external-content/contentless or per-entity FTS, with transactional ordering and
  the existing mapping/rebuild invariants. Do not relax the threshold after a result.
- **Option B:** first remove duplicated content or split the index by entity; if maintenance is the
  failure, use a durable coalesced off-main queue whose pending state is visible and whose p99
  visibility still stays within one second.
- **Option C:** if either storage or five-second visibility fails, reject a monolithic 1M-row FTS
  candidate and fall back to bounded per-entity/on-demand indexing. No vector database or FTS5 is
  admitted by this decision.
- **Unavailable physical profiles:** retain emulator/D2 observations as `INCONCLUSIVE`; do not
  substitute host numbers or claim D1–D3 support.
- **Any stale result, mapping loss, crash or non-deterministic rebuild:** the approved v0.1
  integrity failure gate still produces `FAIL` regardless of the selected numeric option.

## Draft recommendation

Option B is the non-normative engineering preference because it establishes a simple one-extra-copy
storage ceiling and a sub-frozen-frame commit ceiling while keeping a one-second freshness bound.
This recommendation is independent of prior Dora benchmark values and does not authorize a run.

## Owner decision record — intentionally blank

```text
DEC-043 selected option: <A | B | C | modified contract | reject all>
Approved gate set: stage0-v0.2
Approved by: <Project owner>
Approved on: <UTC date>
Rationale: <independent prospective rationale>
Rerun authorized: <yes/no and exact scope>
```

Until every field is completed and the machine-readable gate set changes from
`DRAFT_UNAPPROVED` to `APPROVED`, `tools/check_poc_search_run_readiness.py` must fail closed.
