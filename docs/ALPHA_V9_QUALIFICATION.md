# Alpha v9 qualification — working record

Status: `IN_PROGRESS / QUALIFICATION_PENDING`, 2026-10-30. This is a preparation
record, not final release acceptance. Authority and scope:
[DEC-053](DORA_MVP1_PRODUCT_DECISIONS.md#dec-053--alpha-v9-twenty-four-hour-recording-and-resumable-transcription-2026-10-30),
[ADR-0031](adr/ADR-0031-twenty-four-hour-recording.md),
[24-hour design](ALPHA_V9_24H_DESIGN.md) and
[source-admission boundary](ALPHA_V9_SOURCE_ADMISSION.md).
Evidence filenames below identify owner-local retained outputs; private fixtures,
keys, model files and device paths are not publication artifacts.

## A. Architecture, storage and transcript persistence

| Area | Observed evidence | Current qualification |
|---|---|---|
| Implemented maximum | One logical source: 86,400 seconds / 1,382,400,000 frames / 2,764,800,000 plaintext PCM bytes; 64-bit ranges, indexed authenticated handles and paged transcripts. | Implemented limit; full maximum **not qualified**. |
| Maximum-size encrypted synthetic trial, API36 | Full target failed with `UNCERTAIN` append after 2,728.213 seconds, with `/data` at 100% on a 5.8 GB partition. `storage-24h-api36.log`. | **FAILED / REQUALIFICATION_PENDING**; reviewed remediation is not a 24-hour storage PASS. |
| Authenticated partial prefix | The trial reopened and verified the exact hash of 8,640 five-second units: 12 hours / 1,382,400,000 PCM bytes. `storage-24h-api36-metrics.log`, `reopened_prefix units=8640`. | PASS for that synthetic recovered prefix; not physical capture or a complete 24-hour source. |
| Library projection, API28 | Four real encrypted tests passed in 412.389 seconds. A 361-unit / 57.76 MB source listed in 55 ms with zero run-key opens and zero historic-row-list queries. `library-projection-green-api28.log`; retained runtime metrics. | PASS for those cases; listing is display-only metadata, not authenticated audio availability. Deletion batching is implemented; fresh latency qualification remains pending. |
| Indexed ranges, source fences and paged persistence, API36 | Six scoped instrumented tests passed in 53.481 seconds. `core-range-pages-api36-v6.log`. | Scoped PASS; additive schema/version checks and complete ordinary inventories remain separate from maximum-size endurance. |
| Software/source checks before storage remediation | Static checks, lint, JVM tests and owner variant build passed in 2m55s; related host batch 249/249 and working source gate passed. `v9-final-static-and-owner-build.log`, `v9-release-host-tests-library-final.log`, `v9-pinfree-source-gate-library-final.log`. | Prior-revision evidence. New checkpoint/deletion/budget changes require fresh review, pins and regression checks. |
| Reviewed WAL/deletion successor source | Independent review found no P0/P1/P2; final formatting passed. Eighteen reviewed source pins, 250 host tests without skips and the working source gate passed. `v9-spotless-playback.log`, `v9-release-host-tests-wal-final.log`, `v9-pinfree-source-gate-wal-final.log`. | Source admission PASS; no maximum-size verdict follows. |
| Supplemental WAL/deletion runtime, API28 | Four WAL methods and three deletion-receipt methods passed in 330.492 seconds. `wal-batch-final-seven-api28.log`, `OK (7 tests)`. | Scoped runtime PASS; complete ordinary-inventory execution and the full-size trial remain separate. |
| Subsequent API28 full-target trial | Operator intentionally interrupted after the 480-unit checkpoint: 40 minutes of synthetic audio. Sampled WAL reached 7,935,152 bytes and returned to zero at a checkpoint. `storage-24h-api28-bounded-wal-checkpoints-before-transfer.log`. | **INTERRUPTED**, not a full-size/hash qualification; execution transferred to a faster isolated API36 emulator. |
| Fresh isolated API36 full-target trial | Started 11:18:48 UTC, emulator5556/PID5064. Actual filesystem is about 6 GB; preflight free space was 4,854,710,272 bytes. Early 240-unit checkpoint took 39.222 seconds with bounded WAL; 1,200 units were subsequently appended in 205.886 seconds. `storage-24h-api36-isolated-metrics.log`. | **RUNNING / MAXIMUM_QUALIFICATION_PENDING**. No complete 24-hour hash/reopen/delete result yet. The attempted larger-AVD setup failed; its preparation logs remain outside Git. |
| Latest build checks | Owner lint/unit/APK build passed in 2m8s; debug cache-unit, detekt, formatting and APK checks passed in 1m23s. `final-owner-lint-core-package.log`, `fastseek-native-debug-green-build.log`. | Scoped build PASS; final exact-head CI and complete ordinary device inventory remain pending. |

The failed fixture was preserved: 12,920 run directories were observed, but the last
pending unit was not confirmed; directory count is not a committed-duration verdict.
Measured allocation was 3,681,296 KiB total, including 3,049,884 KiB Recovery data;
database/WAL/SHM sizes were 18,259,968 / 626,965,152 / 1,245,184 bytes.
`failed-24h-preservation-receipt.json` records a verified 2,941,899,264-byte encrypted
archive with 129,210 members. Only that exact synthetic fixture directory was then
removed to release emulator space; package data outside it and Keystore aliases
were retained. Preserve the archive, failure and prefix evidence through remediation.

The original 125 MB/hour storage estimate was rejected. The successor now budgets
200 MB/hour plus a 16 MiB finalization reserve: 4,816,777,216 bytes for a day,
shown as about 5 GB. Three expected RED failures preceded eight passing budgeting
tests (`storage-budget-measured-red.log`, `storage-budget-measured-green.log`).
This estimate does not reserve disk space or prove the maximum fits. Bounded
manual checkpointing and deletion batching have been implemented and reviewed;
supplemental device checks and a new adequately provisioned full-size trial remain
qualification gates. The refreshed source admission does not replace those runs.

## B. Actual capture and physical-device endurance

| Area | Observed evidence | Current qualification |
|---|---|---|
| API28 real-time capture/control regressions | Requested 10-second and 310-second screen-off scenarios passed, including pause/resume, save and a subsequent recording; test elapsed times 100.463 and 385.206 seconds. `api28-visible10-harness2.log`, `api28-visible310-harness3.log`. | Scoped emulator capture PASS; no physical-device or 24-hour claim. |
| Earlier UI attempts | API36 could not find “New recording”; earlier API28 runs could not reach the participant acknowledgement. `api36-visible10.log`, `api28-visible10.log`, `api28-visible310.log`. | Retained failed harness attempts; later API28 passes do not erase them or qualify API36 capture. |
| API28 1,800-second screen-off capture | Actual timed scenario passed in 1,867.145 seconds and saved 28,824,000 frames / 57,648,000 bytes: 1,801.5 seconds including its resumed tail. Queue high-water 2; maximum append 2.6514792 seconds; zero read errors. `api28-visible1800.log`, `api28-1800-runtime-metrics.log`. | Scoped emulator capture PASS; no 24-hour or physical-phone claim. |
| Captured source after process restart | A new PID (4968 versus capture PID3973) reopened that recording and advanced actual AudioTrack playback at start, middle and end. One test passed in 224.062 seconds. `api28-1800-reopen-playback.log`. | Scoped reopen/playback PASS on the previous implementation. |
| Reused authenticated handle/cache, API36 | Actual playback of the known public 30-minute source passed in 47.343 seconds; readiness times were start 18.989s, middle 2.547s and end 0.206s. `fastseek-public30m-api36.log`. | Scoped new-implementation playback PASS. This and the earlier microphone-source result use different APIs/sources and do not form a controlled speedup benchmark. |
| Longer capture and physical phones | No 2/4/8/24-hour physical completion or Samsung/POCO successor acceptance is established by the evidence above. | **PENDING**, subject to available device, power, storage and explicit owner permission. |

The earlier 310-second scenario saved 4,984,000 frames / 9,968,000 bytes (311.5
seconds including its resumed tail), in `api28-capture-through310-metrics.log`.
The observed capture-process lifetime RSS high-water was 205,692 KiB; late sampled
PSS peaked at 76,038 KiB. The latter is not a full-run PSS maximum. These capture
durations are distinct from the larger synthetic prefix in section A.

## C. Local native ASR and Cloud

| Area | Observed evidence | Current qualification |
|---|---|---|
| Local 24-hour orchestration | The virtual 288-window app test passed in 7.86 seconds using the test recognizer. | Software orchestration only; not 24-hour Whisper inference. |
| Actual short EN native ASR, API36 | Same public 30-second fixture, model and audio: functional persistence/edit/version/selection test passed in 66.883 seconds. `native-short-en-functional-api36.log`. | Functional PASS; accuracy remains PARTIAL. The earlier exact assertion failed on “amber landern” versus expected “amber lantern”; retain `native-short-en-api36.log` and diagnostic evidence. The bounded one-edit matcher is test-only, not a changed model or expectation. |
| Actual EN six-window native run | Six real native windows completed in 792.003 seconds; six nonempty results, retained history and vault-reopen assertions were logged before the final quality assertion. The original test **FAILED**, with 16/18 exact and 17/18 bounded-one-edit markers. `native-long-en-api36.log`; zero-inference diagnostic passed in 40.89s, `native-long-en-diagnostic-api36.log`. | **BOUNDARY_QUALITY_PARTIAL**. Entire window2 end marker “crimson sunrise” is missing; “amber lantern” became “amber landern”. The original failed test is not relabelled PASS. |
| Targeted native tail check | The same audio/model's 870–900s PCM interval passed two native recognition calls plus history/cancellation checks in 57.029 seconds and recognized exact “crimson sunrise”. `native-window2-tail30-en-api36.log`. | Scoped tail PASS supports a decoder-context explanation; root cause remains unproven. No PCM, native, model or marker-matcher replacement occurred. |
| Separate functional/readback and strict quality qualification | Reviewed test-only separation retains six real calls, six nonempty parts, durable history/reopen assertions and a separate strict 18-marker/order gate. EN readback in a different process passed in 26.343s using archived original PID8160; `native-en-functional-process-restart.log`. The separate strict quality test failed in 3.425s on the same 17/18 coverage; `native-en-quality-separate.log`. RU processing started at approximately 11:24 UTC. | **EN FUNCTIONAL_READBACK_PASS / QUALITY_PARTIAL; RU RUNNING**. Readback performed zero new inference calls and does not relabel the original failed test. No complete EN/RU boundary-quality PASS is claimed. |
| Cloud backend/contracts | Retained backend suite 89/89 PASS, bounded Android unit coverage and independent cancellation/response-limit review. `backend-cloud-v9-tests.log`. | Scoped software PASS; not live AWS qualification. |
| Actual Android HTTPS integration, API28 | A 1,801-second synthetic encrypted source completed as two children with signatures, pinned TLS, real HTTP and SQLCipher pages; prior Local edit/version/selection survived. One test passed in 558.198 seconds. `cloud-api28-composite1801-attempt2.log`. | PASS with a local fake provider and finite USD5 fixture reserve. First attempt failed before upload at the second synthetic unit append: the harness defaulted `sourceFrameOffset` to zero; setting it to `first % 9_600_000L` corrected the fixture. Retain `cloud-api28-composite1801.log`. |
| Cloud 24-hour budget/live maximum | The HTTPS test rejected a USD43.68 aggregate reserve. Production long mode stays disabled; USD0.25 daily reserve, USD5 hard ceiling and USD20 monthly AWS Budget are unchanged. | Expected fail-closed budget result. No paid long AWS run, live 30-minute/24-hour recognition or full Cloud maximum is claimed. |

The owned TLS fixture server was stopped and its generated synthetic state removed
with the scoped cleanup helper: `cloud-composite-fixture-cleanup.log`,
`OWNED_SYNTHETIC_FIXTURE_STOPPED_AND_CONTENT_REMOVED`. This is fixture cleanup,
not production AWS retention or provider-internal-copy deletion evidence.

Final exact-head CI, signed Preview9 reproducibility and v8 data/model/history
preservation, independent review after remediation and applicable device acceptance
remain release gates. Stage12 stays PARTIAL; Issue125 and privacy/control approvals,
historical unqualified migrations and Stage8.6 are not closed by this record.
No personal-phone installation, production AWS change, release or merge follows.
