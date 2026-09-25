# DORA Alpha ASR bounded runner preflight — Stage 0 v0.1

Task **5.3C**, 25 September 2026.
**PASS / BOUNDED_ASR_RUNNER_INTEGRATION_PREFLIGHT_READY**.

The owner authorized continuation of the six existing local implementation files, exact r28c
installation, an isolated physical-device model initialization, lifecycle checks and one atomic
commit/push on `chat/alpha-asr-runner-scope`. Baseline is
`5566ac510cdd7a8e6d974a5d8ad06b12b8f35bad`. This is bounded preparation before 5.4, not an ASR
quality result or production admission. The [sanitized evidence](../evidence/poc-asr-001/alpha-asr-runner-preflight-stage0-v0.1.json)
contains executable pins, complete build commands, actual ELF inventories and preservation hashes.

## Authority and unchanged contracts

The [technical plan](../DORA_MVP1_TECHNICAL_PLAN.md), [test strategy](../DORA_MVP1_TEST_STRATEGY.md),
[PoC gates](DORA_MVP1_POC_GATES.md), [5.2 pin](DORA_ALPHA_ASR_MODEL_ADMISSION_STAGE0_V0_1.md),
[5.3A contract](DORA_ALPHA_ASR_EVAL_CONTRACT_STAGE0_V0_1.md),
[ADR-0008](../adr/ADR-0008-alpha-asr-evaluation-contract.md) and
[5.3B native record](DORA_ALPHA_ASR_NATIVE_BUILD_STAGE0_V0_1.md) remain unchanged.
No normalization, tokenization, Java oracle, production native allowlist or historical native
evidence was edited. Full POC-ASR remains BLOCKED / NOT_READY; 5.4 is NOT_AUTHORIZED.

## Existing implementation and runner contract

The six starting files were hash-checked and preserved in a verified private archive outside Git.
Only the Android adapter and runner tests needed corrections during this continuation. Runner,
journal, native C++ and CMake files retain their original hashes. No files were recreated.

[`alpha_asr_runner.py`](../../tools/alpha_asr_runner.py) defines
`dora-alpha-asr-runner-request-v0.1`. A closed request binds the entire frozen evaluation profile,
result and normalization versions, model bytes/hash, runtime commit, native manifest digest,
case ID, locale, deterministic generated probe identity, ordinal and timeout/cancellation policy.
Unknown/missing/mistyped fields, booleans masquerading as integers and mismatched identities are
rejected before engine invocation. Caller requests and adapter manifests are snapshotted.

The two operations are MODEL_INIT_ONLY and LIFECYCLE_HOLD_ONLY. The probe identifies this
no-audio protocol; it is not a corpus sample or speech input. Native SUCCESS means the exact model
context initialized and was freed. The embedded frozen 5.3A evaluation remains NOT_RUN/ordinal 0,
because its SUCCEEDED state requires scoring. Failed, cancelled and timed-out attempts remain
unscored with frozen content-free failure categories. No WER is invented for model initialization.

[`alpha_asr_runner_store.py`](../../tools/alpha_asr_runner_store.py) uses a private SQLite journal
outside Git, a single-process OS ownership lock, FULL synchronous writes and an exact versioned
schema. Begin is transactional; finalization conditionally changes RUNNING to FINISHED once.
Exact replay returns the saved record without invoking an engine. Altered replay and nonsequential
ordinals are rejected. Reopening retains interrupted work as ENGINE_FAILURE without retry.
The journal allows at most 32 retained attempts. Existing private-directory permissions are a
caller prerequisite; this test journal is not production encrypted storage.

The public projection contains only version/profile, aggregate outcome counts and explicit
NOT_RUN/false claim ceilings. Requests, case identities and individual attempt records stay private.

## Exact toolchain and isolated native build

The official Google r28c archive was resumed from retained ranges, verified at 748118221 bytes
and SHA-1 `086bba43ff2f5eb0e387b15c8278bb4e0d89ba1d`, then independently verified by all required
file SHA-256 values. It is installed in `SDK_ROOT/ndk/28.2.13676358`. Compiler identity is Android
clang 19.0.1, build 13624864, r530567e. Existing CMake 3.22.1 and Ninja 1.10.2 matched 5.3B and
were retained. No TLS bypass, mirror, source patch or substitute toolchain was used.

SDK Manager timed out waiting for manifests; no usable Android Studio installation was found.
A task-owned BITS job was removed after a client job-ID lookup error. The successful recovery
used 31 official HTTP range requests: 24 completed and seven interrupted/retried, retaining
verified response prefixes. Earlier DNS/TLS/timeout diagnostics remain private.

Source is clean `ggml-org/whisper.cpp` v1.9.4 at
`927cfce34f31707e17f2bff35c349632fb9e2c3a`. Two fresh builds reuse the complete 5.3B vectors:
Android API28, arm64-v8a, armv8-a, Release, c++_shared, CPU only, target whisper, parallelism 2.
An initial compiler execution denied by the host sandbox was retained separately; both actual
candidate builds subsequently configured and completed successfully with the exact compiler.

The [test-only harness](../../tools/alpha_asr_native_preflight/main.cpp) and
[CMake target](../../tools/alpha_asr_native_preflight/CMakeLists.txt) link the isolated candidate.
The executable checks required whisper symbols, disables GPU use, initializes/frees the context,
and emits a content-free receipt. `hold` waits for termination without model initialization.
An independent 125-second alarm is a safety ceiling. There is no `whisper_full` call.

All six files used on the device have fresh bytes/SHA-256, ELF64/AArch64, SONAME (absent for the
executable), DT_NEEDED and every PT_LOAD recorded in JSON. Dependency closure contains the five
candidate libraries plus Android libc/libm/libdl. Every applicable PT_LOAD meets 16-KiB alignment.
Both uncompressed lib-only ZIP fixtures pass zipalign and the unchanged DORA native verifier;
they are not installable APKs. The original binaries, not stripped comparison copies, ran on device.

Two-build reproducibility is NON_BIT_IDENTICAL_EXPLAINED. Differences are confined to DWARF,
derived build ID and local mapping-symbol values referencing reordered `.debug_str` strings.
Every changed mapping symbol retains name/type/section/size and points to the same string after
normalizing the build directory. All other bytes match. Copies stripped only of debug/build-id
are byte-identical. JSON separately records comparison with historical 5.3B digests; no old raw
hash is presented as the identity of a newly built binary.

## Physical Android proof and cleanup

The authorized device was reverified as Android 14/API34, arm64-v8a, build type user, through
ADB 1.0.41/platform-tools 37.0.1-15733141. No serial or personal device label is published.
Static 16-KiB compatibility does not establish operation on a 16-KiB-page device.

[`alpha_asr_android_preflight.py`](../../tools/alpha_asr_android_preflight.py) verifies host artifacts,
creates a fresh task-owned directory, copies only the executable, five libraries and exact model,
then checks remote hashes. It rechecks host/device bindings and control immediately before launch.
The model is 59707625 bytes, SHA-256
`422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898`.

The initial three native process starts produced the intended outcomes:

| Technical scenario | Typed result | Reopen/replay | Cleanup |
|---|---|---|---|
| One exact model context initialization | SUCCESS | Saved result; no native restart | VERIFIED |
| Cancel a confirmed live hold process | CANCELLED | Saved result; no native restart | VERIFIED |
| Three-second host deadline for hold | TIMEOUT | Saved result; no native restart | VERIFIED |

After the final review's process-exit race correction, cancellation and timeout holds were each
repeated once against the same binaries. Both passed with verified cleanup and saved-result replay.
Total native starts: 5 (one initialization, two cancellation holds, two timeout holds). These two
corrective lifecycle reruns performed no additional model initialization or inference.

Model initialization was attempted once and succeeded. Native dependency resolution and all four
required whisper symbols succeeded. Successful smoke inferences: 0; smoke inference NOT_RUN.
Initialization already proves runner/adapter/native result plumbing. No audio was generated or read.
The three-second deadline is a control test, not a latency/performance measurement.

The adapter disables PTY and accepts only exact complete LF or CRLF protocol receipts. Windows
ADB still emitted CRLF with PTY disabled; mixed delimiters, extra content and wrong exit codes
remain rejected. Two new host regressions establish this behavior.

Independent review identified an unreadable-process false-GONE case and an empty PID publication
race. Before fixes, real Android shell fixtures reproduced both failures. The adapter now checks
process existence and read status separately, fails closed on read errors, and treats an empty
startup PID as bounded PENDING. It rejects zero/malformed PIDs and foreign commands. Final review
also reproduced a process disappearing between existence check and read; a second existence check
now accepts genuine disappearance while preserving rejection of unreadable existing processes.
A failed termination signal is also accepted only if a fresh check confirms process absence.
Six device fixture checks passed: failed read of a live shell, empty PID, absent PID, foreign
process, exit during read and exit during signal delivery. All four lifecycle defects were observed
failing before their corrections; the signal-delivery case exercises the real Android kill script
with the surrounding poll results controlled by the fixture.
For reproduction, wrap the state script in a task-owned directory with `pid` set to the same
shell's `$$`, then inject `tr() { return 1; }`; it must reject the read instead of reporting GONE.
An empty `pid` must yield PENDING. Fixtures contain no model or native process and are cleaned.

Cleanup checks the directory's ownership marker, targets only its exact executable/process,
removes that directory and verifies absence. All native and regression-fixture cleanups passed.
No app installation, root, bootloader, SELinux, security-setting or Recovery action occurred.

## Validation and publication ceiling

With CPython 3.12.14/Unicode 15.0 and JDK 17, from DORA_ROOT:

~~~text
python -B -m unittest discover -s tools -p test_alpha_asr_runner.py -v
python -B -m unittest discover -s tools -p test_alpha_asr_eval_text_contract.py -v
~~~

Results: **44 runner + 22 unchanged 5.3A = 66 passed, 0 failed, 0 skipped**. The latter includes
the unchanged Java oracle suite and four-stream bridge. Coverage retains identity/type rejection,
cancellation, timeout, model/native/input errors, persistence failure, reopen/replay, interrupted
attempt retention, ordinal sequencing, no double finalization, deterministic JSON and privacy.
Native execution, device lifecycle regressions, ELF/package checks and both builds are additional.
No production Android source or dependency changed; production Gradle/Recovery work is out of scope.

Final JSON, Markdown/local links, privacy/scope, protected-file hashes and diff checks accompany
independent read-only review before publication. This advisory review is not production admission.
CI is NOT_RUN; no applicable task-branch push workflow or PR is requested.

Corpus clips accessed = 0; private transcripts accessed = 0; public hypotheses = 0;
published private attempt rows = 0. Model, binaries, archives, raw logs, journal, private backup
and personal paths remain outside Git. NDK notices are retained locally; existing license and
production admission gates remain separate.

5.3A/5.3B retain their bounded PASS. 5.3C is the bounded PASS above;
overall 5.3 = PRE_5_4_PREPARATION_COMPLETE. ASR_INFERENCE and WER = NOT_RUN;
RU/EN, RTF, PSS, native heap and thermal = NOT_EVALUATED. 5.4 = NOT_AUTHORIZED.
Production integration/admission = false. PR #86 and Recovery are untouched. One atomic commit
and push are authorized; no PR or merge is authorized by this task.
