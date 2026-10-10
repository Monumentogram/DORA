# Stage 8.6C.2 — persistence latency investigation

This additive evidence package investigates LONG-02 from saved diagnostics and synthetic
Android storage. It does not authorize physical device access. Stage 8.6 remains
**NOT_READY**; see [the result](RESULT.md) for the causal decision and remaining work.

- [Historical statistics](long02-statistics.md) and [machine-readable analysis](long02-analysis.json)
  cover all 393 reconstructed completed appends and explicitly censor the last in-flight append.
- [Architecture](architecture.md) traces PCM admission through encrypted publication,
  catalog commit and the later callback that releases the frame budget.
- [Executed order-validation work counts](catalog-order-work.json) isolate algorithmic traversal
  from encryption, SQL and platform timing. The published stdout excerpt normalizes its
  trailing blank line; the original extracted stdout is retained privately.
- [CPU profile summary](cpu-profile.json) records one synthetic writer sample; inclusive
  percentages overlap and cannot be added.
- [Protection and backup plan](long02-protection-plan.md) keeps the physical launch hold and
  distinguishes metadata consistency, ciphertext preservation and authenticated PCM readback.

## Experiment and controls

`PersistenceScalingBenchmarkTest` is opt-in and requires emulator hardware, the test-only
package and an existing device credential. A fresh random fixture namespace isolates its
vault, root envelope, database and run artifacts from other recordings. All writes use the
real production SQLCipher, Android Keystore and Recovery adapters, including WAL/FULL,
publication syncs, authentication and exact post-commit readback. Decorators delegate
operations unchanged and count fixed API boundaries without recording their arguments.
The benchmark uses deterministic 160,000-byte PCM blocks, no microphone, and one writer.

The vault stays continuously open while growing through 10, 100, 400, 1,000 and 2,000
committed blocks. At each checkpoint it repeats three catalog loads on the same state,
then three new metadata inserts and immediate same-row replays, then three real appends.
The latter start at n, n+1 and n+2. Metadata inserts themselves grow the metadata table.
Physical source identities change every 120 blocks. The final 2,003 blocks must pass
complete authenticated PCM comparison, followed by close/reopen and catalog verification.
Failure before all checkpoints, full readback, reopen and JUnit success is not COMPLETE.

Elapsed time and CPU/heap/GC snapshots end before the fixture file census. Census includes
the fixture's database/WAL and supporting files; it is not just five audio files per block.
Snapshots are not peak memory. Candidate sync boundaries cover only part of the real sync
path. SQL wrapper calls include Room bookkeeping; `db.endTransaction` counts cannot be
equated with four application durability phases or physical filesystem syncs. Stage and
boundary timers are nested/inclusive. Never sum them to derive total I/O time.

The experiment holds PCM, writer composition, vault lifetime and operation shapes constant.
It does **not** independently hold global journal rows, run keys, directories, WAL size,
elapsed runtime and n constant across sizes: they grow together. Same-state repetition
reduces within-size variance but cannot resolve that confounding. Ordinary diagnostic
policy is used; LONG-02's protected historical-source overlay is not reproduced. Direct
writer calls do not reproduce RecordingSession physical OPEN/CLOSE metadata scheduling.
The process may have global retained test keys outside the fresh fixture. Synthetic WAL
growth must not be mistaken for measured LONG-02 WAL growth, which was not collected.

Three observations support a median and observed maximum, not stable p95/p99 estimates.
Cold/warm behavior, JIT/GC, a 20-second seed CPU profile and concurrent host builds/API28
emulator activity affect wall time. This is one x86_64 emulator run on a Windows host,
not POCO M5 hardware performance or a randomized causal intervention.

Synchronous submitted-but-unreturned frames peak at 80,000 and return to zero per append;
`captureOutstandingFrames` is explicitly null. The separate `PersistenceAsyncDurabilityTest`
uses the real encrypted writer and controlled execution/completion queues to verify that
durable storage precedes callback acknowledgement. It is not a microphone/admission test.
The existing seven `CapturePersistenceBackpressureTest` tests cover the admission boundary.

## Reproduction

Use an explicitly selected, disposable emulator with an existing synthetic credential.
Do not copy these commands to a physical target. No device discovery is necessary.
Set `ANDROID_HOME` and JDK17 for the local SDK before building from `android/`:

```powershell
./gradlew.bat :core:audio:assembleDebugAndroidTest
```

From the repository root, pass paths and the exact emulator serial explicitly. The output
directory must be new. Supply the verified `audio-debug-androidTest.apk`: the runner checks
emulator status and API before installation, but does not inspect the supplied APK's manifest.
Its instrumentation and failure cleanup name only the audio test package. It never sets a
credential, and the diagnostic tests do not start a microphone.

```powershell
python tools/run_persistence_scaling.py --adb <sdk-platform-tools-adb> --serial emulator-5580 --expected-api 36 --apk <audio-debug-androidTest.apk> --output-dir <new-private-run-directory>
python tools/persistence_scaling_results.py --input <private-raw-log> --output <new-public-receipt-outside-input-tree> --expected-api 36
python -m unittest discover -s tools -p test_persistence_latency_analysis.py -v
python -m unittest discover -s tools -p test_persistence_scaling_results.py -v
python -m unittest discover -s tools -p test_run_persistence_scaling.py -v
```

The runner requires exclusive use of the selected test package during the run. Do not
install another APK or launch other instrumentation against it. Retain raw logs and the
tested APK privately. Parsers accept only the fixed numeric public schema and reject
existing output paths and aliases into the input tree. A short `maxBlocks=10` instrumentation
smoke may verify API compatibility; it is intentionally rejected as a full scaling campaign
by the five-checkpoint parser. Fixtures and test keys are retained, not silently deleted.

This task's full performance APK and final-source smoke APK are separately hashed in the
verification receipt. Formatting, a stricter emulator guard and additional tests were applied
after the full run started. The measured APK must not be described as the exact final-source
APK; final-source API28/API36 smoke outcomes are reported separately. Raw evidence remains
outside Git. Historical diagnostics and evidence are read-only inputs.
