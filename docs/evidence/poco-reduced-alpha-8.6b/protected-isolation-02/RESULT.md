# Protected POCO successor campaign — 2026-10-09

**NOT_READY / POCO_SCREEN_OFF_CAPTURE_FAILED**

The exact 47 isolation, safe update, bounded physical smoke and fresh 60-cycle
series passed. The owner-authorized successor hour, DORA-LONG-02, failed after
about 33 minutes. Its new terminal proves `PERSISTENCE_BACKPRESSURE`; the underlying
persistence latency cause is still unknown. No second retry or post-main physical
functional campaign was started. This is not Stage 8.6 PASS.

## Repository and binary

- Task baseline: `20057696c556a354d08e007dfcba736ff217fe6d`.
- Runtime source: `c0f1587ed969f089b6eae03cb9851ae6ba388960`.
- Verified pre-publication HEAD: `cefeb0f814d12d64c277ef0dea09cedd234c1848`.
- Branch: `stage/8.6-poco-recording-acceptance`.
- [PR #99](https://github.com/Monumentogram/DORA/pull/99): DRAFT / OPEN / UNMERGED;
  base remains `stage/8.5-logical-recording-recovery`. No merge/rebase/force-push.
- Installed physical APK:
  `f7803281d20655c773906c753e10d2299640e647c1b21b544b20f06361f690b0`.
- Prior physical APK: `c2adf61e7834b6202f62da80b56bd0b4447f71756f4b38f0786715e1280895c1`.
- Signing continuity:
  `a526f525ec8594bc40fc7f0c9ddc6b8ad034d4c3ecd47f650ef81c7b56f74e72`.
- Two independent clean builds match byte-for-byte: 170 ZIP entries, 19 DEX files,
  102 application pre-DEX classes. Final publication SHA/CI and the final-head
  rebuild are reported in the external terminal result to avoid a self-referencing
  commit. No Android/runtime changes follow c0f1587 in this evidence publication.
- POCO M5, Android 14/API34, arm64-v8a, firmware V816.0.6.0.ULURUXM,4096-byte runtime
  pages. Wireless ADB controls the USB-powered experiment; no battery measurement.
- Private AAR `64969d0fbf5d3936a127879684bc2f14014b99817ca3c9b2e16298c1372208db`,
  model `1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3`,
  segmentation profile `1c5ceb70f08593dfa96e522f43c51b7b28e36ab8b65a423fae417cc4e1cd2292`
  remain pinned. No private binaries, identities or audio are published.

## Isolation and preservation

The private snapshot-bound policy identifies exactly47 historical sources and
their component/run namespaces. It is owner-only debug diagnostics; release
cannot activate it, missing/corrupt/mismatched policy fails closed. There is no
global Recovery switch. The lower lease/catalog/key/file boundaries reject
historical mutation before reconciliation, not after UI filtering. Startup,
authenticated open, discovery/refresh/pagination, continuation, finalization and
deletion all cross these guards. New positively owned sources retain full Recovery.
The [entrypoint table](README.md) and additive 15-method API28/API36 isolation suite
cover the runtime boundary and metadata-repair rejection. Actual compiled release
activation denial is verified by the separate release unit test.

Post-smoke, post60 and post-failure authenticated copied-database read-only
inspection each preserved all 47 historical sources. The final proof establishes:

- 11,395 original catalog rows and 1,710 retained key challenges unchanged;
- 8,547/8,547 immutable files byte-identical;
- the only historical file differences are the exact permitted shared DB and
  absent WAL/SHM sidecars; original8550 remains the baseline, not a claim that all
 8550 files still exist;
- 61 owned sources were read back and deleted: one bounded smoke plus 60 cycles;
- LONG02 remains unfinalized, with no deletion tombstone:1,970 new encrypted files
  and 394 new keys mapped to its exact authenticated run namespaces;
- final vault inventory10,518 files; inspection itself changed zero current files;
- LONG01 and the 46 originals are unchanged. Its prior388 authenticated units /
 31,040,000 frames remain historical proof; no repeat PCM inspection was claimed.

The final LONG02 catalog has 394 committed units /31,520,000 **claimed** frames,
one applied APPEND receipt and zero pending intents. Its full authenticated PCM
readback is **NOT_RUN**; whole-recording loss remains **UNKNOWN**. Normal DORA
launch must remain suspended pending safe LONG02 disposition: the new source is
not one of the 47 protected historical identities. No policy removal or ordinary
Recovery was authorized after this failure.

## Physical results

| Gate | Result |
| --- | --- |
| In-place signed update, installed exact APK | PASS; no uninstall or data clear |
| Preflight Start/Cancel/storage budget | PASS; acknowledgement not auto-granted; microphone remains off |
| Controlled low-storage / terminal injection | PASS; unsafe Start rejected without PCM or partial asset; fresh async receipt |
| One isolation smoke | PASS;30,183ms screen-off;483,200 admitted=durable=authenticated frames;49 deletion targets absent |
| Fresh short series | PASS60/60;4,882,400 frames;120 authenticated blocks;840 deletion targets |
| Short-series gaps/duplicates/corruption/read errors/short reads |0; no replaced or retried attempts |
| Short-series VAD |9,374 pre-Stop /9,506 post-Stop inferences;0 deadline misses;60 degraded observations retained |
| DORA-LONG-02 target3600s | FAIL; last healthy sample1980.082s, failure receipt1982.475s |
| One-hour CAP/overlap/final integrity/storage gate | NOT_EVALUATED; incomplete capture and no finalized full readback |
| Long partial thermal | NONE in 67 samples; no observed severe state; not full-hour evidence |
| Post-main notification/permission/Recovery functional smokes | NOT_RUN after main-campaign failure |
| Battery Saver / Doze | DEFERRED / USB_POWER_CONSTRAINT; DEFERRED / AUTONOMOUS_DEVICE_CONSTRAINT |
| Cleanup |61 verified owned sources deleted; failed LONG02 deliberately retained |
| Safe shutdown | Microphone OFF; recording FGS and product process absent |

The reduced60 samples do not prove the historical >=99.5%200-cycle reliability
gate. Existing frozen90-second semantic timing,600-second CAP/32000-frame overlap,
125,000,000-byte/hour storage budget plus 16,777,216-byte start headroom and all
cryptographic/durability safeguards were not weakened.

## Failure attribution

The native capture admission returned FULL at 320 outstanding blocks /256,000
undurable frames. AudioRecordCapture emitted `PERSISTENCE_BACKPRESSURE`, and
RecordingController captured the terminal before interruption/cleanup.

| Observation | Frames |
| --- | ---: |
| Native/controller admitted at terminal |31,696,000 |
| Durable completion at terminal |31,440,000 |
| Outstanding at terminal |256,000 |
| Later authenticated catalog claims |31,520,000 |
| LONG02 authenticated readable PCM |NOT_RUN |

These are different authorities and times. The later catalog claims one 80,000-frame
unit more than the terminal durability snapshot; asynchronous commit/publication
timing is consistent with that difference, but the exact last callback sequence
and PCM readability are not reconstructed by metadata alone.

The last completed append took 9.905677308s. Across the observed bounded span
history,393 unique completions were reconstructed and four exceeded5s. The latest
stage timings are nested, not additive or disk-only costs; an in-flight append's
final duration is unavailable. Canonical queue high-water 4 does not rule out
durability backlog. VAD 41 missed deadlines do not prove that VAD caused the failure.
The underlying latency source is UNKNOWN. Historical LONG01 cause stays UNPROVEN.

Partial resource diagnostics: PSS start 153670/p50149606/p95183219/peak 185838/end 109796KiB;
RSS peak 315224KiB; native heap peak 63,380,624B; Java heap peak 24,862,672B;
threads peak 46, FDs peak 171/end 147. CPU time delta 1,245,569ms over the observed
partial interval. These introduce no new acceptance thresholds. Full values and
67 samples are retained in [partial resources](long02-partial-resources.json) and
[failed receipt](long02-failed.json).

## Regression, review and remaining work

Fresh accepted local evidence includes Recovery 414/414, audio debug 240 with the
one inherited Windows skip, release 232, additive Android isolation 15/15, host
POCO 88 and logical-Recovery-host 153. These are named executions/checkpoints, not
relabelled reruns. Spotless/Detekt and dependency/native/SBOM gates pass in the
verified CI checkpoint. [Exact cefeb0f CI](ci-cefeb0f.json) run 37836070505 is4/4
SUCCESS: each API has exact 138 distinct persistence methods (only accepted
credential-state skips),22 Recovery phases,21 UI and 15 isolation methods.
Final publication-head CI is still independently required.

Independent implementation/receipt review found and closed scoped P2 verifier
issues before publication; the corrections and rejected private oracle receipts
are retained. No unresolved P0/P1/P2 remains in those reviewed changes; the actual
LONG02 physical failure is an **open acceptance blocker**, not a clean overall
acceptance result. CI artifacts/logs and repository evidence receive positive
canary privacy audits; only content-free receipts are published.

Next task: diagnose increasing async persistence latency and bound the retained
LONG02 source safely, with deterministic reproduction and isolated remediation.
Do not increase256,000 frames, drop canonical PCM, claim durability before commit,
or run another hour to seek a favorable outcome. No further retry is launched here.

Sheet C78 remains unchanged/ЗАПЛАНИРОВАНО. Stage 8.6 remains not accepted. Battery
efficiency stays DEFERRED / NON_BLOCKING_FOR_ALPHA; hardware microWh and comparative
ratio remain NOT_EVALUATED. Security restoration is OPEN; PERF-REC-001 deferred /
non-blocking. Group D, Stage 9, Cloud and ASR remain NOT_STARTED. POCO evidence is not
Android device-matrix, physical16KiB, eight-hour endurance or all-device energy proof.
