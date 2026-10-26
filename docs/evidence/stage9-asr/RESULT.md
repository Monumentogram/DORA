# Stage 9 — observed software evidence, 2026-10-26

The implementation and bounded runtime verification are complete. The final
`PASS / STAGE9_LOCAL_ASR_TRANSCRIPT_ALPHA_SOFTWARE_READY` verdict requires the
required checks on the final PR head to pass; the authoritative exact-head CI
and final verdict are recorded in [Draft PR112](https://github.com/Monumentogram/DORA/pull/112).
This source checkpoint does not predict a future CI result. Physical acceptance
and production distribution remain separate.

## Runtime and user-flow evidence

[Sanitized receipt](native-functional-20261026.json): actual pinned SMALL q8 with
new JNI on disposable x86_64 API28 and API36 emulators. English public speech and
Russian offline synthetic speech each passed one zero-skip integration test on
both APIs. Each test completed two real recognitions, kept immutable versions,
independent corrections and explicit selection, cancelled a third native invocation
without publishing a version, and verified encrypted history after vault reopen.
Cancellation is callback-triggered; its precise compute phase and latency are not
asserted. Model initialization still has no abort API in the pinned runtime.

API36 used the installed Google APIs page-size-16KB image revision7. The test
asserted `Os.sysconf(_SC_PAGESIZE) == 16384` in the app. This is the supported x86
16KB compatibility environment, not ARM64 hardware evidence; see
[Android's emulator explanation](https://android-developers.googleblog.com/2024/08/adding-16-kb-page-size-to-android.html).
The native inputs remain the separate explicitly unadmitted x86 test build.

Manual product UI verification on that API36 emulator opened a real Russian
transcript, ran recognition again from the UI, observed a third version without
changing the active second version, saved a manual correction, force-stopped the
app and reopened the same correction and all three versions. No PIN/biometric
challenge occurred. Public/synthetic fixture bytes and UI dumps remain outside Git.

## Migration and regression evidence

CI [40291645198](https://github.com/Monumentogram/DORA/actions/runs/40291645198)
passed software and 16 synthetic checks on each API28/API36 at
`8d19ec3538e104e1be06b8ceaadd8e959cfb6e6b`.
Review then identified ordinary v1/v2 migration fixtures that still opened current
schema4 as schema3. Two failures were reproduced on API36 before repairing the
fixtures. All nine persistence/migration tests then passed, including exact v1,
v2 and v3 migration, encrypted row/source preservation and tampered-DDL rejection.
Production migration/downgrade guards were not weakened. The final workflow now
requires 20 tests per API:9 persistence/migration,3 cap/authority,1+1 process phases,
and6 editor/picker/recording UI tests. Final SHA outcomes belong to PR checks.

Local verification also passed app JVM105, Spotless, Detekt, controlled native
APK/instrumentation assembly and `zipalign -c -P 16 4`. Earlier core JVM254/255,
lint, contract guards and full software CI evidence below remain applicable;
final CI repeats the required checks. Independent review accepted the migration
fix and found no further evidenced important finding.

## Custody and remaining gates

Owner freed disk space and explicitly renewed the identical model's custody and
local test use through November2 inclusive (Europe/Moscow), ending exclusively at
`2026-11-02T21:00:00Z`. SHA256 was reverified locally and after provisioning inside
each emulator. The app and visible expiry message enforce the renewed deadline.
No model/audio/transcript is in Git, CI artifacts or the APK. Historical receipts
and the original expiry are preserved.

Separate open gates: ARM82/POCO qualification with explicit owner permission;
historical protected diagnostic-v3 qualification; existing security restoration,
production upgrade, signing and distribution. Default APK still has no ASR native
payload or weights; a controlled build plus separately provided model is required.
Stage8.6 remains NOT_READY. No physical device operation or merge occurred.

## Historical checkpoint (preserved)

The following records describe earlier unverified/blocked states and are not the
current runtime outcome.

# Stage 9 local ASR and transcript — software evidence

Current classification: **PARTIAL / STAGE9_IMPLEMENTATION_INCOMPLETE**.

Resume checkpoint, 2026-10-26: CI run 39970852704 on
`8903c085189348c62f85fbce1867f1d69b247bb0` passed the full software job
and booted both API28/API36 emulators. Each passed the three encrypted transcript
persistence tests, then failed both migration tests during fixture creation:
the exported schema omits `indices` for two tables. The fixture now accepts that
optional array, following the existing v1 migration fixture. This does not weaken
the production schema verifier or the migration assertions. Successor CI must
confirm migration, restart and editor execution; the failed run is not a PASS.

The existing model-use deadline `2026-10-26T00:00:00Z` has now elapsed.
No real inference or deadline extension was performed on resume. Further real
ASR testing requires renewed explicit model custody/use authorization as well as
disk space. Source-checkpoint statements below retain the earlier evidence.

Later on 2026-10-26, the owner freed local disk space (28.7 GB observed) and
explicitly renewed custody/local test use of the identical model through November
2 inclusive, without publication or POCO operations. The new exclusive cutoff is
`2026-11-02T21:00:00Z` (Europe/Moscow). Real integration execution remains pending
until an actual native test supplies evidence; this authorization is not a PASS.
Branch: `codex/stage9-local-asr`, stacked on PR111 commit
`4ee27ef2473b0b047ee73be0595e981d08848e94`. No merge or physical device operation.

Implemented: bounded protected PCM extraction; pinned optional Whisper JNI;
separate asynchronous worker with cancel/error handling; schema4 encrypted
immutable machine versions, independent edits and explicit atomic selections;
foreground editor, history after audio-only deletion, and process-restart recovery.
No fake product inference, plaintext audio spool, Cloud or guessed timestamps.

Observed static evidence: ARM64 and test-only x86 JNI/runtime builds, ELF16KiB,
exact pin/receipt/license checks, controlled debug APK assembly/zip alignment,
negative packaging admission checks. Model admission and pure transcript/worker
boundaries have JVM tests. These are not native speech-recognition evidence.

Local checks observed: core/audio debug254 and release255 JVM tests; app debug105
JVM tests, all zero failures/skips; Detekt; app/core-audio lintDebug; both
instrumentation APK builds. Python guards:3 PIN-free,44 transcript-contract,
3 native-admission and2 emulator-runner checks passed. Later exact-SHA CI results
are authoritative in the PR checks and sanitized workflow receipts. Instrumentation
execution must not be inferred from APK compilation.

The workflow covers synthetic API28/API36 encrypted migration/restart/editor
execution and rejects skipped/empty test runs. At this source checkpoint those
remote results are not yet observed. No software-ready claim is made.

Actual new-adapter inference is NOT_RUN. Local disposable emulator startup failed
its free-disk-space check. Owner authorized cleanup of regenerable development
intermediates, but automatic tool policy rejected both the bounded batch and an
exact single-directory removal (`blocked by policy`); no deletion was performed.
No physical POCO fallback was authorized or attempted. Retention stays unchanged.

Open gates: real model-to-protected-audio-to-encrypted-text runtime execution;
API28/API36 results until observed; physical POCO acceptance; actual16KiB runtime;
historical protected diagnostic v3 qualification; existing security restoration,
production upgrade/signing/distribution gates. Stage8.6 remains NOT_READY.

The historical protected-vault positive fixtures hard-code schema3. They are not
silently migrated, skipped, weakened or reported as Stage9 PASS. This successor's
ordinary synthetic migration and retained-text tests are separate evidence.

Independent review found a P1 model-picker lifecycle issue: the dialog disappeared
on ON_PAUSE before the result returned. It is fixed by unconditional Activity
registration and a retained model-only import owner; re-review found the issue
resolved and no other evidenced P0/P1/P2. Queue unit and registry lifecycle tests
cover the boundary; device execution still needs the emulator receipt.
