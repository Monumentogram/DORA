# LONG-02 offline latency statistics — Stage 8.6C.2

Status: **ROOT_CAUSE_NOT_PROVEN** by observational statistics alone. Source implementation:
`944b41532e3007ca14d1851d83c2232de1132874`, branch
`stage/8.6-poco-recording-acceptance`. This analysis accessed saved host files only.
It does not change LONG-02 protection, create an audio backup, or establish Stage 8.6 readiness.

## Coverage and method

`long02-analysis.json` contains all 393 durations, sequential distributions, 66 sampled
stage observations, metric correlations, missing measurements and SHA-256 bindings to
the four private input files. No recording identifiers, audio, private snapshots, device
identifiers or absolute device timestamps are included. Input paths are supplied only at runtime.

The analyzer independently parses the original diagnostic span lines and compares their
union and first-observation sample against the derived span file. All spans are unique,
positive and non-overlapping. At **all 67 samples**, the cumulative recovered span count
times 80,000 equals `durableFrames`, beginning at zero. At the terminal receipt, 393 ×
80,000 = 31,440,000 durable frames. The rolling 256-span buffer therefore did not lose
completed spans from this reconstruction. Under the implementation's serialized,
80,000-frame successful append semantics, these are completed append ordinals **1–393**,
not merely unverified observation ranks. The last in-flight append has no completed
duration; 394 stored blocks do not imply 394 completed durable appends.

The tool retains observation rank separately and refuses to assign append ordinal when
the coverage check fails. The fixed-block basis is `RecordingSession.TRANSPORT_BYTES`
(160,000 mono PCM16 bytes); timing wraps `writer.append`, and successful completion
advances `durableFrames`. This is telemetry consistency evidence, not authenticated PCM
readback or independent validation of storage claims.

Windows contain 50 consecutive completed appends, except the final 43. p50 is the
ordinary median; p95/p99 use nearest rank. Small-window p99 equals the maximum here.
No sample is removed. A separate sensitivity correlation excludes the four >5 s values.

Stage timings represent the latest completed append at each sampled diagnostic read,
yielding 66 distinct append observations. The latest-span/stage pairing uses the same
diagnostic snapshot, but the underlying reads are separate, not atomic. Stage values are
**inclusive and nested**, include scheduling, and must not be summed as independent work.

## Sequential distributions

All durations are seconds. Reserve and catalog columns are sampled-stage medians;
they do not describe every append in the window.

| Completed append ordinal | n | p50 | p95 | p99 | max | Sampled n | Reserve p50 | Catalog commit p50 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1–50 | 50 | 2.102587 | 2.479472 | 2.595806 | 2.595806 | 8 | 0.153410 | 0.154015 |
| 51–100 | 50 | 2.256380 | 2.319861 | 2.322795 | 2.322795 | 8 | 0.210949 | 0.233782 |
| 101–150 | 50 | 2.337476 | 2.439304 | 3.967355 | 3.967355 | 9 | 0.250041 | 0.268191 |
| 151–200 | 50 | 2.432707 | 2.541672 | 2.903018 | 2.903018 | 8 | 0.310969 | 0.322494 |
| 201–250 | 50 | 2.848736 | 5.101319 | 8.068519 | 8.068519 | 8 | 0.404081 | 0.375182 |
| 251–300 | 50 | 2.701456 | 3.623677 | 4.552277 | 4.552277 | 9 | 0.376479 | 0.406073 |
| 301–350 | 50 | 2.787882 | 2.914135 | 3.988055 | 3.988055 | 8 | 0.414438 | 0.436174 |
| 351–393 | 43 | 2.887910 | 3.273003 | 9.905659 | 9.905659 | 8 | 0.464203 | 0.490134 |

Overall n=393: p50 **2.505340**, p95 **3.408167**, p99 **5.101319**, max **9.905659**.
The first-to-last window median rises approximately **37.35%**. Sampled reserve median
rises about **3.03×**, sampled catalog commit about **3.18×**. These windows differ in
time, catalog proxy and operating conditions; the ratios are descriptive, not controlled effects.

Other first/last sampled medians remain comparatively stable: bootstrap 0.439404/0.446413,
publication 0.656668/0.648264, recovery 0.604398/0.595197, SQL policy
0.550188/0.577148, SQL commit 0.023339/0.023721, key open 0.520057/0.517725 s.
This distinguishes gradual reserve/catalog growth from broader transient tail slowdowns.

## Four appends above five seconds

| Ordinal | Seconds | First sample observed | Stage attribution |
|---:|---:|---:|---|
| 219 | 5.101319077 | 37 | Unavailable; sample 37 already reports latest append 220 |
| 220 | 8.068519078 | 37 | Latest-stage snapshot available, with separate-read caveat |
| 221 | 5.719884616 | 38 | Unavailable; sample 38 reports a later append |
| 393 | 9.905659308 | 66 | Latest-stage snapshot available, with separate-read caveat |

Appends 219–221 form a consecutive burst. The gaps before 220 and 221 are only
0.000467692 and 0.000508384 s: the persistence worker proceeds immediately to queued
work. Three 5-second audio units take **18.889722771 s** of append time, excluding these
small gaps, exceeding their 15-second production interval. This can accumulate backlog;
it does not reveal which shared resource caused the stall.

For 220, reserve=1.201733, bootstrap=1.286762, publication=1.385502,
recovery=2.682440, catalog_commit=1.003521, sql_policy=2.292432,
sql_commit=0.103768, key_open=2.289531 s. Several paths slow together.

For 393, reserve=3.225550, bootstrap=1.255698, publication=1.584232,
recovery=1.077282, catalog_commit=2.432808, sql_policy=2.726979,
sql_commit=0.121393, key_open=0.838350 s. Reserve/catalog growth alone does not
quantitatively explain this burst; multiple inclusive stages are affected. Do not add these
numbers or describe SQL policy as a separate amount to subtract from append time.

At sample 66, backlog is 241,600 frames; the later terminal receipt reaches the 256,000
frame fence. The terminal time is 6.167332001 s after the last observed completed span.
The next in-flight append is censored, so assigning it a completed duration is invalid.

## Correlations and limits

Pearson uses raw values; Spearman uses average ranks for ties. Constant variables have
undefined correlation (`null`), never an invented zero. No p-values are reported: these
time-ordered observations are autocorrelated and not independent experimental trials.

| Pair | n | Pearson r | Spearman rho |
|---|---:|---:|---:|
| Ordinal vs append seconds | 393 | 0.478717 | 0.881985 |
| Same, excluding >5 s | 389 | 0.676037 | 0.887830 |
| Ordinal vs sampled reserve | 66 | 0.439795 | 0.923891 |
| Ordinal vs sampled catalog commit | 66 | 0.531181 | 0.953283 |
| Durable-block proxy vs latest sampled append | 66 | 0.390171 | 0.910615 |
| Process CPU interval rate vs latest sampled append | 66 | 0.431722 | 0.956539 |
| Outstanding-frame snapshot vs latest sampled append | 66 | 0.942086 | -0.131906 |
| Interval VAD deadline misses vs latest sampled append | 66 | 0.953845 | 0.309973 |
| PSS vs latest sampled append | 66 | -0.390191 | -0.880305 |
| RSS vs latest sampled append | 66 | -0.395409 | -0.875879 |
| Java heap vs latest sampled append | 66 | 0.050864 | 0.049098 |
| Native heap vs latest sampled append | 66 | 0.043688 | 0.218537 |

Outstanding frames and deadline-miss Pearson coefficients are tail-sensitive; their much
smaller/different Spearman coefficients do not support a uniformly monotonic backlog
growth claim. CPU is process-wide CPU milliseconds per wall second between samples,
not persistence-only CPU. Snapshot RAM and negative PSS/RSS correlations do not identify
memory pressure or garbage collection as the cause. Thread and FD values are constant.

Ordinal / already durable blocks are proxies for this recording's catalog additions,
confounded with elapsed time and workload. They are not the global catalog size, which
also includes pre-existing recordings. There are no per-append measurements of journal
entries, encrypted file counts, SQLCipher database/WAL size, metadata lookup count,
fsync count/duration, or outstanding async-operation count. The final 394 blocks / 1,970
files are an endpoint inventory, not a measured size series. No missing series is invented
by multiplying ordinal by an assumed file or journal-entry factor.

All 66 sampled instrumentation counts are constant: reserve/bootstrap/publication/
recovery/catalog_commit/key_generation=1, sql_policy=2680, sql_commit=2, key_open=5.
These are instrumentation event counts with the source-defined timer scope; they are
not measurements of disk sync calls or standalone SQL statements. Their variance is zero,
so they cannot identify a count-dependent correlation in these observations.

The supported causal hypothesis is a growth-sensitive reserve/catalog path plus transient
shared latency that can consume backlog headroom. Proving its mechanism, exact scaling,
and contribution to LONG-02 requires source inspection and controlled synthetic
experiments. These statistics alone leave the complete LONG-02 root cause unproven.

## Reproduction and verification

```powershell
python tools/persistence_latency_analysis.py --input-dir <private-offline-directory> --output <new-public-output.json>
python -m unittest discover -s tools -p test_persistence_latency_analysis.py -v
```

The runtime used was the Codex bundled Python interpreter. All 14 analyzer tests passed:
tail-preserving percentiles, invalid durations, tied/constant correlations, latest-only
stage attribution, incomplete coverage, duplicate/overlapping/inconsistent spans, raw
reconstruction mismatch, private-field exclusion, strict validation of exported scalars
and stage counts, exact lowercase source-commit validation, and new-output-only source
preservation. The preservation tests cover existing files, hardlinks, Windows directory
junction aliases, input-directory descendants, and fresh external output creation without
overwriting. A fresh temporary rerun produced byte-identical statistics; the original
statistics artifact and all four input SHA-256 values remained unchanged. Raw source data
remains external to the repository and unchanged. A broader Python discovery attempt was interrupted
before producing test results, at the parent task's request to remove extra host load
during the emulator benchmark. No broad-suite pass is claimed. These are offline analysis
tests, not Android performance admission.
