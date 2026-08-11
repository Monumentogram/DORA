# DEC-043 — POC-SEARCH-001 storage/update gates

Status: **Approved for prospective Stage 0 evaluation — execution not authorized**\
Approved by: **Project owner**\
Approved on: **2026-08-11**\
Selected option: **B — balanced local baseline**\
Approved Gate Set: `stage0-v0.2`\
Scope: prospective `POC-SEARCH-001` campaign only

## Decision

Option B is the normative storage/update contract for the next valid Stage 0 search campaign.
The contract is frozen before that campaign and cannot reclassify any result produced under
`stage0-v0.1`.

The Project owner approved this rationale:

> Вариант B ограничивает индекс одной дополнительной копией объёма исходных данных, сохраняет
> умеренную стоимость обновления и требует появления изменений в поиске не позднее одной секунды.
> Это приемлемый баланс между размером, скоростью и сложностью для локального MVP. Выбор не основан
> на прежних результатах Dora.

## Approved Option B predicates

All predicates are conjunctive. Exceeding any threshold triggers the approved
`GATE-SEARCH-STORAGE-UPDATE-FAILURE` gate.

| Metric | Approved threshold |
|---|---:|
| `search.storage.index_incremental_bytes` | ≤536,870,912 bytes (512 MiB) |
| `search.storage.index_overhead_ratio` | ≤1.00 |
| `search.storage.index_overhead_bytes_per_segment` | ≤512 bytes |
| worst single-row `search.update.maintenance_delta_ms` p95 | ≤50 ms |
| worst bulk-100 `search.update.maintenance_delta_ms` p95 | ≤250 ms |
| worst indexed commit p99 | ≤500 ms |
| `search.update.visibility_latency_ms` p95 | ≤250 ms |
| `search.update.visibility_latency_ms` p99 | ≤1,000 ms |
| stale successful responses | 0 |
| mapping errors, lost mutations, or candidate crashes | 0 |

Metrics, scale, repetitions, aggregation, environment, invalidation rules, and fallback are
normatively defined in `DORA_MVP1_POC_SEARCH_GATE_SET_STAGE0_V0_2.md` and the machine-readable
`poc-search-gate-set-stage0-v0.2.json`.

## Approval is not execution authorization

The numeric contract is approved, but the Project owner explicitly did **not** authorize a
benchmark. `benchmarkExecutionAllowed` remains `false` until all of the following have durable
evidence:

1. the exact component/license/NOTICE Stage 0 review is complete and explicitly approved;
2. the new paired storage/update harness is implemented and verified against this contract;
3. physical D1, D2, and D3 devices are confirmed available with exact preflight evidence.

Completing a prerequisite does not silently change the execution flag. A later recorded owner
authorization must change `benchmarkExecutionAllowed` to `true` before any targeted or full
measured workflow, tag, or manual instrumentation command is dispatched.

## Historical boundary

- The retained `stage0-v0.1` observations remain immutable and `INCONCLUSIVE`.
- Option B was not derived from those observations and cannot be applied to them retrospectively.
- No historical PASS/FAIL status changes because of this decision.
- A new result must identify `stage0-v0.2`, the exact approved contract digest, and the new
  campaign commit.

## Fallback

If the selected storage limit fails, remove duplicated indexed content or split the index by
entity. If maintenance fails, use a durable coalesced off-main queue with an explicit visible
`INDEXING` state. If one-second p99 visibility fails, use bounded per-entity/on-demand indexing.
Never return stale results as current. FTS5, vector search, a production schema, or dependency
admission remains outside this decision.

## Owner decision record

```text
DEC-043 selected option: B
Approved gate set: stage0-v0.2
Approved by: Project owner
Approved on: 2026-08-11
Rationale: one-extra-copy storage ceiling, moderate update cost, and <=1 second search visibility;
           not based on prior Dora results
Benchmark authorized: no
Execution prerequisites: exact component/license/NOTICE approval; verified new harness;
                         confirmed physical D1-D3
Historical results affected: no
```
