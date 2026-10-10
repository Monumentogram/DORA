# Stage 8.6C.3 preregistered controlled encrypted benchmark

Registered before C3 timings. Historical C2 evidence is immutable. This protocol measures
an emulator synthetic writer, not microphone capture, admission frames or POCO performance.
No timing result establishes LONG-02 causality or physical-device readiness.

## Method

Build the identical `PersistenceOptimizationBenchmarkTest` and probe against baseline
and optimized production sources. Retain both APKs, source revisions/patch hashes and
SHA-256 digests privately. Runner binds an explicit variant and expected source digest
to each output. Hash the harness files to establish identical benchmark code. Baseline
must compile before production changes. Execute API36 and API28 independently, with
exclusive use of the selected disposable emulator and test package and no concurrent
Gradle/device workload. Baseline precedes optimized; this is not randomized and host/JIT,
thermal, filesystem and retained global Keystore state remain possible confounders.

`sourceSha256` is SHA-256 of a retained UTF-8 source manifest containing production
base commit plus any production patch digest and the two Kotlin harness file digests;
it is not a 40-character Git object ID. Baseline production is commit `9c6b756` without
production edits. APK SHA-256 is verified by the runner before installation and retained
outside the instrumentation log. Short `maxBlocks=10` engineering compatibility smoke
runs may test observers before full capture; they are never acceptance measurements.

Each variant/API creates one fresh isolated vault. Never copy a seeded vault without its
live Keystore state; never substitute metadata-only seeds. Grow continuously through
10, 400, 1000 and 2000 real encrypted blocks. The initial ten seed appends provide
symmetric warmup. At each target, perform one unmeasured catalog warmup, three measured
same-state catalog loads, then seven measured appends beginning at n through n+6.
Continue seeding to the next target without closing/checkpointing the vault. Every block
is 160000 deterministic PCM bytes (80000 frames), with one physical ID per 120 blocks,
one writer and the same logical-recording origin metadata; no additional metadata work.
PCM is generated, never captured. Retain the fixture and keys after the run.

Measure wall and calling-thread CPU nanoseconds, process CPU milliseconds, full catalog
load count, ordered successful application transaction completions, stage timings/counts
and delegated candidate sync counts. A complete snapshot is counted through its exact
DAO ordered unit-claim query, including internal reserve/CAS readbacks. SQL and binding
values are never logged. Transaction labels come from executed writes, not all Room
endTransaction calls. Trace reserve/bootstrap/publication/recovery/catalog_commit stage
completion order separately. These observations do not count physical filesystem syscalls;
the existing durability/fault tests remain the authority for interruption semantics.

Require complete authenticated exact PCM comparison for all 2007 blocks before close
and again after reopen, with exact frames, catalog segment order/count, no pending intent
and no finalization. Every append already verifies its new ciphertext through Recovery.
Only final COMPLETE followed by JUnit `OK (1 test)` makes a run complete. Short smoke
runs are incomplete campaign evidence. Progress/timeout/interruption never become success.
The runner may force-stop only its verified emulator's synthetic test package on failure;
it does not resume a partial run, mutate historical evidence, delete fixtures or credentials.
Recovery of an interrupted fixture belongs to the separate preservation/fault campaign.

## Useful-improvement criterion

Correctness/durability is mandatory at every target on both APIs. Every measured append
must retain exactly reservation → bootstrap → publication → catalogCommit transaction
completions and reserve → bootstrap → publication → recovery → catalog_commit stages.
The baseline must show five full catalog loads per measured append and optimized four;
steady catalog loads must show one. Stage counts remain one each and sql_commit two.
Candidate publication fsync/fsyncParent counts must remain equal between variants.

Call the optimization useful only if, on **each API**, median append thread CPU improves
by at least 10% at **both 1000 and 2000 blocks**, with no median append or catalog-load
wall-time regression exceeding max(10% of baseline, 10 ms) at any checkpoint. Catalog CPU is reported separately. A wall
regression, missing API/size, incomplete run or a noisy result is inconclusive/does not
meet this gate; never change thresholds after seeing timings. Report seven append values
and three catalog values with median and maximum; no stable tail-percentile claim.
Report all failures and exact tested artifact provenance. An unsupported target remains
explicitly unmeasured, never silently omitted from the gate.

Linear validator work is independently verified by production regression/operation-count
tests; this real encrypted harness does not invent a visit counter from elapsed time.
If host noise motivates repetition, repeat the entire paired baseline/optimized campaign
for that API with reversed variant order, identical controls and fresh fixtures; retain
and report every attempt. Do not select only favorable checkpoints or silently replace
the first run. Global keys and elapsed time are not isolated by this method.

## Private output and invocation

Use new child directories of the owner-private `POCO-8.6C3-20261009` output root, outside Git.
Do not reuse a directory. `run_persistence_optimization_c3.py` requires explicit ADB,
emulator serial, expected API, APK and aapt paths, variant, expected source SHA-256,
and expected APK SHA-256. APK package is checked before install; hardware and credential
guards also run in instrumentation. It never configures a credential or discovers devices.
The source digest is a provenance assertion supplied from the saved build manifest;
the runner cannot infer APK source contents. Compare receipts with the pure C3 parser.
