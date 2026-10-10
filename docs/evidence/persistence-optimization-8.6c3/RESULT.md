# Stage 8.6C.3 result

Status: **LOCAL_VERIFIED_PENDING_EXACT_SHA_CI**.
Performance gate: **USEFUL** on both API28 and API36. Full task acceptance still
requires four successful exact-SHA CI jobs and their publication privacy audit.

The production validator now reads n source elements instead of a quadratic prefix
scan. Append safely reuses one eligible reservation snapshot: five full catalog loads
become four. All four application commit boundaries, post-commit validation,
encrypted readback, Recovery checks, fsync barriers and the 256000-frame fence remain.
PCM, encryption, source identities, tombstones and recovery rules are unchanged.

## Controlled encrypted comparison

The preregistered protocol and thresholds are unchanged. Both API pairs were repeated
in reverse order (optimized then baseline), on AC power with the existing Performance
profile, fresh vaults and no concurrent Gradle/device test workload. The agent did not
change the host profile. API28 used one fresh same-image AVD for both variants after
an Android framework boot failure on the retained old AVD; API36 used its existing AVD.
All four runs completed, with 40 measured observations each. Every run authenticated
exact PCM for all 2007 blocks / 160560000 frames before close and after reopen.

Medians in milliseconds; each cell shows baseline / optimized. CPU improvement is
positive for a reduction. All seven append and three catalog values, maxima, stage
counts, full-load counts, commit order and artifact hashes are in
[the complete comparison](benchmark-comparison.json).

| API | Blocks | Append wall | Append thread CPU | CPU improvement | Catalog wall |
| --- | ---: | ---: | ---: | ---: | ---: |
| 28 | 10 | 541.1 / 534.6 | 418.1 / 411.4 | +1.6% | 32.6 / 32.0 |
| 28 | 400 | 704.2 / 638.6 | 572.2 / 505.3 | +11.7% | 62.1 / 62.3 |
| 28 | 1000 | 990.9 / 853.1 | 853.8 / 715.8 | +16.2% | 115.6 / 113.1 |
| 28 | 2000 | 1594.0 / 1281.2 | 1432.7 / 1138.9 | +20.5% | 217.8 / 213.0 |
| 36 | 10 | 277.6 / 300.4 | 159.4 / 173.0 | -8.5% | 13.5 / 13.8 |
| 36 | 400 | 626.5 / 525.1 | 475.4 / 383.8 | +19.3% | 83.1 / 81.9 |
| 36 | 1000 | 1193.1 / 955.9 | 991.3 / 774.7 | +21.9% | 188.1 / 187.9 |
| 36 | 2000 | 2225.1 / 1707.7 | 1964.7 / 1484.9 | +24.4% | 367.4 / 370.5 |

Append CPU improves by at least 10% at both 1000 and 2000 on each API. All append
and catalog wall-time gates pass. API36's 10-block append is slower within the fixed
10% allowance; no claim of improvement at every size is made. Catalog load CPU is
reported separately and is approximately unchanged. Every measured baseline append
has five full loads; optimized has four. Commit order is reservation, bootstrap,
publication, catalogCommit; candidate file/parent sync counts remain four each.

This is controlled synthetic emulator evidence, not proof of POCO performance or
LONG02 causality. Sequential order, host/JIT/thermal behavior and retained global
Keystore state remain limitations. No frequency normalization or checkpoint selection
was used. Earlier complete baselines and the FAILED / INCOMPLETE optimized API28
attempt remain in the comparison's priorCampaign and verification's priorBenchmarks.
That adverse attempt took906.5s and was manually stopped after its first wall gate
failed: append3430.758ms versus567.246ms and catalog203.501ms versus30.567ms.
Observed DC/Silent host slowdown does not establish its cause. It is not relabeled PASS.

## Correctness and protection

The same frozen optimized APK passed44/44 targeted tests on each API, including all12
Room reservation controls, full394 successor composition, ownership/key/namespace
fences, restart, fail-closed and release denial, Recovery of fresh sources and typed
read-only acquisition/authenticated readback. Build10 passed core debug253 with one
inherited Windows skip, core release246 and app debug76; lint has zero errors and
Detekt exit0 was observed. Host logical regressions passed190/190. Baseline RED tests
and failed engineering attempts are retained in verification.json. Full runtime
inventories and abrupt process-death regressions are mandatory in exact-SHA CI on
fresh emulators; runners that clear packages are not run over retained local benchmark
fixtures and keys. CI is still pending at this source checkpoint.

The first published CI attempt on9e67bf6 (run38006756610) passed the API36
163-method persistence inventory,19 diagnostic tests and all crash runners, but
failed the legacy UI isolation inventory: selecting the whole diagnostic package
now returned new C3 methods beyond its frozen15. The successor correction selects
exactly the original15 methods; strict output checks and the separate mandatory19
C3 gate remain. A host regression reproduced the identical failure before the fix
and passes afterward, including missing/extra/duplicate/skipped/failed controls.
Production and benchmark harness bytes are unchanged. The failed attempt is retained;
a new full exact-SHA CI is mandatory. Independent delta review found zero unresolved
P0/P1/P2 and independently reproduced RED/GREEN; all11 frozen hashes still match.

The Android borrowed-FD leak found during acquisition development was reproduced
with +320/+576 descriptors, fixed with explicit finally-close, then passed its two
regressions and both44-test suites. This does not establish the historical LONG02 cause.

Successor preparation retains all47 predecessor identities and adds LONG02 as the48th,
with394 exact run bindings and four physical sources. Owner-private policy and pinned
APK are prepared, with signing continuity checked. Neither is installed on POCO.
Synthetic success does not establish physical key availability, authenticated LONG02
PCM, or a portable backup. Original physical protection remains unchanged.

Independent final review found zero unresolved P0/P1/P2 across all46 files, frozen
source, raw benchmark receipts, successor preparation and bounded privacy evidence.
Final host regressions after the selector fix passed191 logical,13 comparator/runner and19 policy tests.
The local bounded privacy audit found zero protected-identity or secret-pattern matches.
Public evidence
contains synthetic measurements and hashes; private identities, policy, audio, keys,
host paths and acquisition copies remain outside Git. This source checkpoint claims
no C3 exact-SHA CI success. Final workflow status must be checked on the published SHA.

`LONG02_ROOT_CAUSE = NOT_PROVEN`

`LONG02_PHYSICAL_PROTECTION = NOT_INSTALLED`

`STAGE_8_6 = NOT_READY`

PR99 remains DRAFT / OPEN / UNMERGED. No POCO access, install, launch, acquisition,
readback or mutation occurred. No60-cycle rerun, battery experiment, Sheet update,
GroupD, Cloud or ASR work was performed. Physical protection and one control long run
require a separate owner decision after actual C3 acceptance.
