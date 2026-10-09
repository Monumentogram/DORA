# Stage 8.6C.2 result

**ROOT_CAUSE_NOT_PROVEN. Stage 8.6 remains NOT_READY.** This investigation establishes
growth mechanisms in the persistence implementation and measures a real encrypted
synthetic writer. It does not establish the cause of LONG-02's terminal stall.
No product runtime fix, physical device access or LONG-02 mutation was performed.

## 1. Causal decision

| Required proof | Result |
|---|---|
| Confirmed mechanism | Yes: five full catalog reconstructions per append; quadratic order validation; shared persistence queue and deferred completion acknowledgement |
| Reproduced growth | Yes: executed production order-validator counts and increasing real catalog-load CPU/latency on an emulator |
| Link to LONG-02 | Qualitative reserve/catalog growth agrees; the multi-stage tail bursts and final censored operation are not reproduced or attributed |
| Verified remedy | Absent: no controlled intervention establishes that a contract-preserving change prevents this failure |

The gate for PROVEN is not met. Confirming an algorithm's complexity does not prove it
caused this particular backpressure failure. No SQL/WAL, Keystore, filesystem, scheduler,
callback-delay or GC cause has been independently ruled in or out for that final event.

## 2. Causal model and historical observations

Each accepted 80,000-frame unit waits on the shared persistence executor, then performs
catalog validation/reservation, key bootstrap, encrypted publication, authenticated
Recovery readback and catalog commit. The control executor subsequently applies the
completion and releases admission budget. Metadata also uses that persistence executor.
Consequently growing per-unit CPU work, a writer/metadata delay or a late control callback
can all consume the finite 256,000-frame headroom. Queue length and callback delay were
not individually measured in LONG-02.

All **393** reconstructed completed appends match the raw diagnostic span union and
the durable-frame series. First/last sequential-window p50 rises **2.102587 → 2.887910 s**
(37.35%); overall p50/p95/p99/max is **2.505340 / 3.408167 / 5.101319 / 9.905659 s**.
The four >5-second events are ordinals **219, 220, 221 and 393**. Only 220 and 393 have
matching latest-stage snapshots; those are non-atomic and inclusive. The last in-flight
append is censored. The saved 394-block catalog cannot supply its missing duration.

Sampled reserve/catalog-commit medians rise about **3.03× / 3.18×** while ordinary
bootstrap/publication/Recovery medians remain comparatively stable. Ordinal/append
Spearman rho is **0.881985**, an observational, time-confounded relationship. All 66
sampled policy/commit/key-open counts remain **2,680 / 2 / 5**. Neither database/WAL size
nor an async-operation-count series exists in those saved diagnostics. Full windows,
outlier details and correlation limits are in [the statistics](long02-statistics.md).

The [architecture trace](architecture.md) identifies five complete catalog loads, linear
publication/digest reconstruction and `validateOrder` prefix scans. The latter executes
**55 / 5,050 / 80,200 / 500,500 / 2,001,000 original-list reads** at the five requested
sizes. Its O(n²) work per append is confirmed in the actual method, independently of
physical grouping. New-run file scanning is bounded to the current five-file run, not
all historical audio files. Four application durability phases have distinct crash
semantics; there is no evidence that removing a transaction would be safe.

## 3. Synthetic benchmark

API36 x86_64 emulator; real encrypted/durable writer; **COMPLETE**, 2,003 blocks,
**160,240,000 authenticated PCM frames**, catalog verified after reopen. All values
below are milliseconds unless marked otherwise; medians use three observations.

| Blocks n | Append p50 / max | Catalog load p50 | Metadata insert / replay p50 | Catalog commit p50 | SQL commit p50 |
|---:|---:|---:|---:|---:|---:|
| 10 | 484.76 / 1392.52 | 13.85 | 31.99 / 19.45 | 145.88 | 11.14 |
| 100 | 401.24 / 462.03 | 29.51 | 43.53 / 36.68 | 71.88 | 11.25 |
| 400 | 670.29 / 702.59 | 80.00 | 92.60 / 91.43 | 183.74 | 11.85 |
| 1,000 | 1118.61 / 1132.90 | 177.19 | 194.61 / 189.36 | 373.59 | 9.22 |
| 2,000 | 2254.25 / 2368.41 | 382.54 | 406.87 / 381.01 | 760.81 | 12.24 |

| Blocks n | Append thread / process CPU p50, ms | Java / native heap after p50, MB | Files after p50 | WAL after p50, MB |
|---:|---:|---:|---:|---:|
| 10 | 328.36 / 490 | 5.32 / 6.83 | 65 | 1.59 |
| 100 | 288.13 / 423 | 5.02 / 6.64 | 515 | 11.68 |
| 400 | 528.83 / 829 | 5.48 / 7.36 | 2015 | 48.93 |
| 1,000 | 963.98 / 1491 | 5.77 / 8.42 | 5015 | 125.82 |
| 2,000 | 2037.55 / 3036 | 10.54 / 8.50 | 10015 | 256.22 |

MB means decimal 1,000,000 bytes. The main `.db` remains 4,096 bytes while WAL grows;
total journal persistence is not represented by `.db` size alone. File counts include
supporting vault/database files and reflect the median post-append state n+2. Process CPU
sums work across threads and can exceed wall time. Heap values are snapshots, not peaks.

Each operation has three observations. Append starts at n/n+1/n+2; other operations use
the same n. `sql_commit` covers two instrumented journal boundaries, not all four
application phases. Catalog commit includes nested work. CPU and heap snapshots,
all boundary counts/timings, database/WAL/file sizes and raw-log hash are retained in
[the machine-readable benchmark](benchmark-api36.json).

The first measured n=10 append is an early outlier (1,392.52 ms); the next two are
306.08/484.76 ms. A cold/JIT cause for this outlier is not established.
Do not fit a monotonic model through that checkpoint or infer p95/p99 from three values.
The continuously open database, WAL, file/key counts and elapsed time grow together.
Same-state repeated catalog/metadata reads expose growth without adding new files during
those operations, but do not independently control all global state across sizes.

Across measured appends, intercepted query/policy/end-transaction/candidate-file-sync/
candidate-parent-sync counts are **100 / 1,784 / 8 / 4 / 4**. These are API boundaries,
not total SQL statements or sync syscalls. Catalog loads use nine intercepted queries
and 126 policy events. Candidate decorators omit other real bootstrap/directory/DB
sync paths. Synchronous submitted-but-unreturned frames peak at **80,000**, then zero;
capture outstanding frames are **unavailable**, not zero.

One 20-second CPU profile attributes **53.06% inclusive samples** to `Catalog.load`,
24.84% to digest parsing and 3.71% to `validateOrder`. The percentages overlap. Its exact
ordinal range is unavailable, and CPU samples cannot measure descheduled or blocked wall
time. See [the profile](cpu-profile.json) and [experiment controls/limitations](README.md).

## 4. Relationship to LONG-02

Reserve/catalog growth in the synthetic run agrees qualitatively with LONG-02 and the
source mechanism. The emulator uses x86_64 software/platform behavior, fresh isolated
storage and ordinary policy; LONG-02 had retained historical state and an exact protected
overlay. Policy event counts differ (1,784 versus 2,680). Direct writer calls also omit
the recording session's physical OPEN/CLOSE scheduling. Host builds, profiling and
another synthetic emulator added noise. This is not a quantitative POCO reproduction.

No measured >5-second append burst or terminal capture backpressure was reproduced by
the five-checkpoint synchronous experiment. It cannot establish a fix for the original
screen-off failure. Stage 8.6 acceptance remains open; the battery gate stays
DEFERRED / NON_BLOCKING_FOR_ALPHA under the existing decision.

## 5. Change scope

Added diagnostic Kotlin tests/decorators, offline parsers and a guarded emulator runner,
their host regression tests, an additive sealed publication route, and this evidence
package. Diagnostic instrumentation lives outside the frozen persistence CI package.
No production PCM, queue, encryption, database schema, durability, admission limit or
startup/recovery behavior changed. There is no speculative performance fix.

## 6. Verification

- Kotlin host tests: core audio debug **245 total, 244 passed, one inherited Windows skip**;
  core audio release **237/237 PASS**; app debug **76/76 PASS**, including the seven existing
  capture-backpressure tests. The five new actual-validator work-count tests pass in both variants.
- Python analysis/parser/runner **35/35 PASS**; additive admission **14/14 PASS**. Privacy-field,
  malformed/incomplete log, hardlink/junction preservation, emulator-selection and failed-cleanup
  negative controls are included. The broader logical-recovery integration suite passes
  **167/167**, including those 14 admission tests; inherited compiled contract validation passes.
- Final diagnostic APK: API28 **2/2** and API36 **2/2** real async/durability tests PASS; each API
  also passes the bounded n=10 benchmark with **13 blocks / 1,040,000 authenticated frames** and
  reopen verification. This is not a full five-size API28 campaign.
- Android test APK assembly, Spotless and Detekt PASS. Windows Detekt uses the exact existing
  Gradle task arguments via Java `@argfile`; no rule/configuration is weakened.
- Independent reviews closed the identified guard, privacy/output-preservation and failure-cleanup
  issues. Offline custody recheck and private-identifier scan passed. New admission keeps all
  historical seals and exact CI identity checks. No unresolved P0/P1 review finding was reported.

See [verification and identity receipt](verification.json) for exact APK, raw-log and diagnostic
source hashes, counts, inherited skip, failed setup attempts and interpretation limits. The
skip is `DevelopmentDeviceSecurityTest#symbolicMarkerCannotGrantTheException` on Windows.
Exact publication-SHA CI outcomes are recorded after commit in the final task receipt.

The full performance APK and final diagnostic APK have distinct hashes. The full run
started before final formatting, guard tightening and diagnostic-package relocation;
its measurement logic was preserved. Final-source API smoke results are separate from
the pinned full-performance APK. This distinction is retained in the receipt.

## 7. LONG-02 protection and backup

All **19** offline evidence files were rechecked by SHA-256. Saved mappings cover all
**394** contiguous claims, **1,970** distinct artifacts, four physical sources and
394 matching key aliases. There are **zero saved LONG-02 key challenges**. These facts
establish saved metadata consistency, not authenticated PCM integrity or current device
state. The original 8,547 historical-file comparisons remain untouched.

The [separate fail-closed plan](long02-protection-plan.md) specifies exact ownership/key
mapping, pre-startup mutation fences, denial of reconciliation/delete/finalize/resume,
quiescent DB/WAL acquisition, before/copy/after byte hashes, independently stored copies
and complete authenticated readback. The current exact47 pinned policy cannot be expanded
by simply appending a 48th source. Android Keystore keys are non-exportable; copying
ciphertext and aliases alone is device-dependent preservation, not portable recovery.

**Protection was not extended; audio backup was not created; authenticated LONG-02
readback was not run.** DORA's physical launch hold remains in effect.

## 8. Publication

Branch: `stage/8.6-poco-recording-acceptance`; source baseline
`944b41532e3007ca14d1851d83c2232de1132874`. Publication commit and exact-SHA CI are reported
in the task's final receipt after committing these sealed artifacts, avoiding a
self-referential commit hash. [PR #99](https://github.com/Monumentogram/DORA/pull/99)
must remain **DRAFT / OPEN / UNMERGED**. No main change or merge is authorized.

## 9. Remaining blockers

- The terminal multi-stage latency burst and callback/admission timeline are not causally attributed.
- No controlled intervention has established a remedy under all durability/security contracts.
- Full-size performance was measured once on API36; API28 has bounded compatibility checks.
- Physical protection, verified audio preservation and full authenticated LONG-02 readback remain pending.
- Stage 8.6 screen-off acceptance remains blocked. Battery stays DEFERRED / NON_BLOCKING_FOR_ALPHA;
  Group D/Cloud/ASR/Stage 9 did not start.

## 10. Next concrete task

Run a controlled **synthetic-only** catalog experiment in one continuously open real
encrypted vault containing two assets with 1,000 and 10 units. After symmetric warmup,
perform ten balanced ABBA rounds of actual catalog loads with **no writes**, holding
global N=1,010, WAL, keys, files, connection and metadata fixed. Capture CPU/elapsed time,
GC and row/policy counts outside census noise. This isolates per-asset reconstruction
from global retained state; it does not exercise the separate quadratic validator.
Then quantify full-writer impact with balanced real appends and explicit state increments,
and test a proposed remedy against crash/readback/ownership contracts before product changes.

In parallel as a separately scoped design task, prepare the exact successor protection
contract and synthetic tests for LONG-02. Any architectural change and any physical
acquisition/readback session require the Owner decisions specified in the plan.
No physical testing follows automatically from this investigation.
