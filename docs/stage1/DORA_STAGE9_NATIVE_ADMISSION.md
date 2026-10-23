# Stage 9 bounded local Whisper integration

Current owner scope authorizes integration of the Stage5 SMALL q8 bounded Alpha
candidate. It does not broaden the historical POCO M5 result to production or all
devices. The existing model receipt's `productionReuse=false` and redistribution
restrictions remain historical admission limits. This thin JNI adapter is new and
is unqualified until its product integration receives actual runtime testing.

## Exact inputs and optional packaging

- whisper.cpp v1.9.4 commit `927cfce34f31707e17f2bff35c349632fb9e2c3a`.
- `ggml-small-q8_0.bin`, 264464607 bytes, SHA256
  `49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f`.
- The five exact accepted ARM82 libraries are pinned in
  [build_stage9_whisper_adapter.py](../../tools/build_stage9_whisper_adapter.py)
  and match [Stage5 artifact admission](../evidence/poc-asr-001/asr-small-q8-admission-stage0-v0.1.json).
- NDK r28c `28.2.13676358`, CMake `3.22.1`, Android API28, arm64-v8a.

Default builds contain neither model nor ASR libraries. An explicitly selected
controlled local build may package the private native directory. CI may not use
this path. The model is separately imported by its owner, never downloaded or
bundled in the APK. It stays in application `noBackupFilesDir`, requires exact
length and SHA256 on import and each use, and uses a same-directory atomic
replacement only after successful verification. A failed import preserves a
previous valid installation. There is no fallback to another model.

`-PdoraPrivateAsrDir=EXTERNAL_OUTPUT/jniLibs` opts into the admitted ARM82 native
inputs. `VerifyPrivateAsrTask` validates the sibling build receipt against the
current adapter/CMake sources, exact runtime pins, all six native file hashes,
ABI/ELF layout and complete license notices before staging. Its outputs are not
cacheable; no model is an input. `-PdoraPrivateAsrTestDir=TEST_OUTPUT/jniLibs` uses
the separate x86 test receipt and exact new test-runtime pins. Those properties
are mutually exclusive and prohibited on GitHub Actions. Test mode disables all
non-debug variants and cannot combine with the arm64-only private VAD path.

Model use/import stops at `2026-10-26T00:00:00Z` (the end of the already admitted
2026-10-25 UTC retention day). Inspection removes an expired installed model.
The cancellation predicate also stops ongoing recognition after that deadline.
This deadline is not a retention extension. Device wall time is the available
local time authority; an offline app cannot establish an independent trusted
clock or perform cleanup while it is not running.

## JNI and audio boundary

The new adapter is built for baseline ARMv8 and depends only on platform libraries
and the accepted C++ runtime. It checks `AT_HWCAP` for ASIMD, FPHP, ASIMDHP and
ASIMDDP before `dlopen` loads the specialized ARM82 backend. It does not rebuild
Whisper or ggml. It resolves the required API from the pinned headers and runtime.

`WhisperNative.recognize` accepts an in-memory mono 16-kHz FloatArray with at most
4800000 samples (300 seconds), RU or EN, and a cancellation predicate. The caller
must obtain PCM through the protected `OriginalAudioReference` extraction port,
complete extraction successfully, and perform inference outside its bounded
authorization callback. The native PCM copy is overwritten on all exits. The
caller owns and clears its JVM PCM. No plaintext audio file, subprocess, native
hypothesis file or transcript log is created. Result acceptance must recheck the
exact source through `withAvailable`; acquiring a reference is not access consent.

The runtime uses CPU4, flash attention, greedy decoding, the specified language,
no translation, context or prompts, and zero temperature/fallback increment.
Whisper/ggml logging and print flags are disabled. Native cancellation safely
attaches callback threads to the JVM, stops on callback failure, and checks before
model loading, after loading, during encoder/compute and before result return.
Model initialization itself has no abort API at this runtime pin. Only one native
recognition can run concurrently; additional calls fail with a fixed category.
Standard UTF-8 output is strictly converted to UTF-16, including supplementary
characters; JNI modified-UTF-8 is not used for transcript results.

## Build and validation

Run the helper explicitly with `--source PINNED_SOURCE --native ACCEPTED_LIB_DIR
--sdk ANDROID_SDK --output NEW_EXTERNAL_DIRECTORY`. All four paths remain local.
The helper requires a clean pinned source, verifies every runtime digest, builds
only the adapter, checks its JNI exports and dependency closure, verifies ELF
16-KiB LOAD alignment, and creates `jniLibs/arm64-v8a`, notices and a sanitized
`build-receipt.json` outside Git. It does not copy model weights or operate a device.

Synthetic host tests cover artifact identity and alignment rejection, atomic model
admission, failed replacement, corruption on next use and expiry. Static build
success is not actual 16-KiB runtime, quality, latency, memory or cancellation
evidence. The historical Stage5 evidence is unchanged. No POCO operation is
authorized by this document alone.

An explicitly requested disposable-emulator functional test may use
[build_stage9_whisper_test_runtime.py](../../tools/build_stage9_whisper_test_runtime.py)
with `--explicit-unadmitted-emulator-test`. This separate helper builds the same
clean pinned source for x86_64, with CPU extensions disabled and a compile-time
test-only JNI capability mode. Its outputs and `test-build-receipt.json` stay
outside Git. The ordinary ARM82 helper rejects those different binary identities.
This test runtime may only enter an explicit debug/emulator packaging path; it is
not a substitute for ARM82/POCO qualification. Real recognition on it can establish
functional JNI/audio/model flow, not inherited Stage5 quality or resource gates.

The retained [ARM64](../evidence/stage9-asr/arm64-static-build.json) and
[x86 test](../evidence/stage9-asr/x86-emulator-static-build.json) receipts report
actual successful builds, JNI dependency checks and ELF16KiB alignment only.
Local controlled x86 `assembleDebug` and `assembleDebugAndroidTest` passed. The
resulting APK passed `zipalign -c -P 16 4`; all six ASR native entries remained
byte-identical to their receipt and all five notice/attribution assets were
present. No Whisper weights were packaged. Negative Gradle checks rejected an
altered synthetic native artifact, GitHub Actions packaging, simultaneous product
and test inputs, and release/ownerPilot requests under the test property.
No emulator or physical-device inference was performed by this build task.

## Notices

The runtime and original weights use MIT notices retained with the controlled
artifact and [source attribution](../evidence/stage9-asr/model-attribution.md).
The helper retains the exact runtime MIT and NDK distributed notices.
The local packaging path must also retain the complete OpenAI MIT notice and
conversion attribution when distributing model copies under separately authorized
custody. License notices do not substitute for project admission or data authority.
