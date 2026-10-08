# POCO reduced autonomous acceptance: owner override result

**NOT_READY / POCO_SCREEN_OFF_CAPTURE_FAILED.** The reduced physical campaign is
blocked by DORA-LONG-01 interruption. No repeat or replacement hour was run.
This does not close Stage 8.6 and does not start another product stage.

| Gate | Recorded result |
| --- | --- |
| Short real recording cycles | 60/60 Start, Stop, finalize and full authenticated readback |
| Short canonical integrity | 4,886,400 frames total; admitted = durable = readback; zero gaps, duplicates, corruption or read errors |
| Short cleanup | All 60 exact owned recordings deleted after verified readback |
| One-hour screen-off | FAILED at about 32.5 minutes; only 1,950,153 ms observed through last sample |
| Long capture failure | LONG_CAPTURE_INTERRUPTED; actual terminal product error enum unavailable |
| Last long counters | 31,203,200 admitted; 30,960,000 durable; zero read errors in last sample |
| Long thermal | NONE in all 66 observed samples; this is not a full-hour thermal PASS |
| Long VAD | Real pinned inference observed; 60,943 inference count and 27 deadline misses at last sample; no accepted full-run result |
| Long finalize/readback/storage/rotations | NOT_EVALUATED or unsuccessful; no fabricated integrity/storage PASS |
| Protected data after failure | Authenticated catalog check: original46 unchanged; one failed test source retained |
| Retained failed source | 388 committed catalog claims / 31,040,000 catalog frames; not finalized; audio readback NOT_RUN |
| Shutdown | Mic OFF, recording FGS absent, pending Start excluded; force-idle false |
| Storage budget | Minimal UI and exact 141,777,216-byte admission implemented; controlled100,000,000-byte injection blocks unsafe Start without partial asset on tested APK |
| Remaining functional smokes | Prepared, NOT_RUN after main campaign failure |
| Battery efficiency / Saver / Doze | Explicitly deferred; no battery experiments |

Catalog metadata is **not** authenticated PCM readback. The failed source was
not deleted, resumed or projected through Recovery. Total remaining catalog:
46 original records plus this one diagnostic source. No owner action was needed.

## Evidence and provenance

- [All60 reduced receipts](cycles-60.json), [summary](cycles-summary.json),
  [terminal reconciliation](cycles-terminal-reconciliation.json).
- [Host failures](main-host-attempts.json): first interruption after completed
  attempt3; fixed denominator continued at4. A second host receipt/exit race
  after60 was recovered from the device COMPLETE receipt, without another Start.
- [Failed long receipt](long-01-failed.json), [resource and failure summary](long-failure-summary.json),
  [USB context](long-power-context.json), [post-failure preservation](post-failure-preservation.json),
  [final safe state](final-safe-state.json).
- [Current scoped result](owner-override-result.json), [operation ledger](post-failure-operation-ledger.json),
  [local checks](local-verification-followup.json), [review checkpoints](review-checkpoints.json).
- Main protocol and continuation protocol were sealed before their respective
  runs. Functional v1/v2 were prepared but never executed. The separate inspection
  protocol binds the exact failed receipt and permits catalog reads only.
- Product APK: `c2adf61e7834b6202f62da80b56bd0b4447f71756f4b38f0786715e1280895c1`.
  Runtime checkpoint: `71f94fd3b8b73b3659d07cdb5639547479759e4f`.
  Final publication SHA, rebuilt APK relationship and exact-SHA CI are reported
  in the external terminal RESULT; green CI cannot override this physical failure.

## Limits and next investigation

Persistence backpressure is a **candidate**, because the final sampled
admitted-minus-durable difference was243,200 frames near the existing256,000-frame
admission cap. The terminal failure enum was not captured; causality is unproven.
Investigate the preserved source and persistence stall in a separately scoped
remediation before proposing another accepted long run. Do not weaken the fence.

The earlier `result.json`, preflight failures and Samsung/energy history remain
unchanged. Battery efficiency is DEFERRED / NON_BLOCKING_FOR_ALPHA; Saver is
DEFERRED / USB_POWER_CONSTRAINT; Doze is DEFERRED / AUTONOMOUS_DEVICE_CONSTRAINT.
Hardware microWh is NOT_EVALUATED / HARDWARE_ENERGY_COUNTER_UNAVAILABLE.
No200-cycle reliability, unplugged endurance, full Android matrix or physical
16-KiB runtime claim. Sheet C78 unchanged; GroupD/Stage9/Cloud/ASR NOT_STARTED.
Security restoration remains OPEN; PERF-REC-001 stays deferred/non-blocking.
PR99 remains DRAFT / OPEN / UNMERGED.
