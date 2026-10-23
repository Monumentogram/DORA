# Stage 9 local ASR and transcript — software evidence

Current classification: **PARTIAL / STAGE9_IMPLEMENTATION_INCOMPLETE**.
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
