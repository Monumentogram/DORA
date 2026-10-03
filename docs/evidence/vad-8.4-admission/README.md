# Stage 8.4 — blocked runtime/model admission

Date: 2026-10-03. Source-publication status: `PENDING_FINAL_PUBLICATION`.
Verdict: **BLOCKED / VAD_RUNTIME_MODEL_ADMISSION_REQUIRED**.

This is an additive admission investigation, not a segmentation implementation
or Stage 8.4 acceptance. No product code, Gradle dependency, model weights,
native binaries, workflow, historical receipt or security setting is changed.
The exact parent is `23adb618a39a014e5090ee2f32e27015de485f07`.
Branch: `stage/8.4-vad-segmentation-rotation`; required PR base:
`stage/8.3-instant-recording-controls`. PR publication must remain draft/unmerged.
Final commit/PR/CI state belongs in the external publication report.

## Candidate identity

| Item | Exact candidate, not admitted |
|---|---|
| Runtime | sherpa-onnx v1.13.8, revision `11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf` |
| Runtime artifact | `sherpa-onnx-1.13.8.aar`, 50,129,134 bytes; official release asset 555224925 |
| AAR SHA-256 | `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96` |
| Top-level code license | Apache-2.0; this does **not** describe every linked component |
| Model | Silero VAD v6.2.1, revision `7e30209a3e901f9842f81b225f3e93d8199902b1`, `src/silero_vad/data/silero_vad.onnx` |
| Model bytes / SHA-256 | 2,327,524 / `1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3` |
| Model license | MIT, commercial use/redistribution permitted with required notice retention |
| Rate/window | Silero upstream documents 8/16 kHz; selected sherpa adapter requires 16 kHz and 512-sample windows, with 64 samples of model context for its three-input interface |
| ABI inventory | arm64-v8a, armeabi-v7a, x86, x86_64; four libraries per ABI |
| `.so` names per ABI | `libonnxruntime.so`, `libsherpa-onnx-c-api.so`, `libsherpa-onnx-cxx-api.so`, `libsherpa-onnx-jni.so` |
| API | AAR manifest minSdk21; API28/API36 runtime compatibility not executed |

[Official runtime release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8),
[exact Silero source](https://github.com/snakers4/silero-vad/tree/7e30209a3e901f9842f81b225f3e93d8199902b1),
[Silero license](https://github.com/snakers4/silero-vad/blob/7e30209a3e901f9842f81b225f3e93d8199902b1/LICENSE).
The model came directly from the tagged Silero repository, not the mutable
sherpa `asr-models` asset. Exact-pair inference has not been executed.

## Admission blockers

**VAD-ADM-001 — OPEN / P1: generic runtime contains eSpeak/TTS.**
The tagged Android build defaults TTS ON. Its dependency recipe statically
includes eSpeak revision `ed530aa113046142eb5115cf2fc9157854d0ffe1` and
piper-phonemize revision `f3ff95afc03640bc1399e113e83361192a2fafb4`.
The actual arm64 JNI artifact contains eSpeak implementation strings, including
`Wrong version of espeak-ng-data`. The exact eSpeak source declares
GPL-3.0-or-later. Restricting application calls to VAD does not remove this
statically linked code. Commercial use is not categorically prohibited by GPL;
the unresolved issue is this application's complete redistribution disposition.
No repository relicensing or GPL acceptance is inferred from public visibility.

Sources: [pinned eSpeak recipe](https://github.com/k2-fsa/sherpa-onnx/blob/11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf/cmake/espeak-ng-for-piper.cmake),
[exact eSpeak source notice](https://raw.githubusercontent.com/csukuangfj/espeak-ng/ed530aa113046142eb5115cf2fc9157854d0ffe1/src/libespeak-ng/synthesize.c),
[exact GPL text](https://raw.githubusercontent.com/csukuangfj/espeak-ng/ed530aa113046142eb5115cf2fc9157854d0ffe1/COPYING).

**VAD-ADM-002 — OPEN / P1: transitive notices/SBOM are incomplete.**
The AAR and its nested classes.jar contain zero LICENSE/NOTICE/COPYING entries.
The source-indicated component list in [candidate.json](candidate.json) includes
ORT and its transitives, Kaldi dependencies, sentencepiece, JSON, hclust,
eSpeak/piper and C++ support. This list is explicitly not a complete binary SBOM.
Missing embedded notices alone does not establish that redistribution is
impossible; the complete set and its disposition have not been supplied here.

Official release origin and digest were verified. They do not prove a
reproducible transitive build: the tagged workflow selects
`ANDROID_NDK_LATEST_HOME`, and the Android script's ORT download does not verify
its hash. A separate CMake download path has a hash but does not prove the
script-supplied input. The inspected arm64 JNI ELF note identifies Android API21,
NDK r29, build 14206865. Reproducibility is an evidence limit, not an assertion
that the official artifact is tampered with.

## Checks performed and limits

- Recomputed byte count and SHA-256 for all ten downloaded evidence objects;
  all matched their recorded values. AAR also matched the independent digest
  and size in the official GitHub release metadata.
- Inspected all 16 ELF files. Every PT_LOAD alignment is at least 16,384 bytes
  and virtual-address/file-offset congruence holds modulo 16,384.
  [native-inventory.json](native-inventory.json) records each file's size, digest,
  ELF class/machine and every load segment. This is static alignment evidence,
  not final APK zip alignment, API compatibility or physical 16-KiB execution.
- NDK llvm-readelf independently read the arm64 JNI dynamic section/notes:
  dependencies are libandroid, liblog, libonnxruntime, libm, libdl and libc.
  This dynamic list does not enumerate statically linked components.
- An independent AI adversarial admission reviewer rechecked the downloads,
  source recipes, binary markers and missing notices. Two unresolved P1
  admission findings remain; **0 unresolved P0/P1/P2 is not achieved**.
  This review is not formal legal approval or a review of implemented VAD logic.
- ADB inventory found the POCO-class physical device and two emulators.
  No candidate was installed; no microphone, playback, permission, PIN,
  vault, app data or development opt-in was changed.

Reproduction uses the source URLs and digests in candidate.json: hash each
download, inspect the AAR and nested classes.jar with ZIP tooling, inspect each
`.so` with `llvm-readelf --program-headers --dynamic --notes`, and read the exact
source revisions linked above. Do not execute the candidate to reproduce these
static findings. No model or runtime binary is redistributed in this PR.

## Required report / unexecuted gates

| Requested result | Actual disposition |
|---|---|
| Verdict | BLOCKED / VAD_RUNTIME_MODEL_ADMISSION_REQUIRED |
| Parent SHA | `23adb618a39a014e5090ee2f32e27015de485f07` |
| Final HEAD / PR | External publication report; no self-certifying SHA in this commit |
| Module/dependency changes | None; `:ml:vad-sherpa` not created before admission |
| SegmentationProfile | Not frozen; no calibration fixtures evaluated |
| Speech onset / hysteresis | Not selected; required ranges remain 0.3–0.5 s / 0.8–1.2 s |
| Pre-roll | Required 2,000 ms; not implemented |
| 90-second semantics | Required continuous known VAD-negative frames from initial silence onset after established speech; hysteresis must not add time; pause/scheduling/wall-clock gaps must add no silence. Not implemented or tested |
| Physical cap / overlap | Required 600 s + at most one audio frame; overlap selection remains unfrozen within 1.5–2.0 s |
| 89.5 / 90.0 / 90.5 s; 89.9 s cancellation | NOT_EXECUTED_ADMISSION_BLOCKED |
| Virtual 1 / 3 / 8 h; multiple rotations/provenance | NOT_EXECUTED_ADMISSION_BLOCKED |
| Failure / backpressure / stale callbacks / resets | Not implemented; no fail-safe behavior claimed |
| Canonical PCM equivalence | No VAD path exists; on/off equivalence campaign not executed. Product source is unchanged; that is not runtime equivalence proof |
| POCO acoustics, >10 min rotation, 90 s silence / cancellation | NOT_EXECUTED_ADMISSION_BLOCKED |
| CPU / PSS / cadence / p50 / p95 / max / queue / deadlines / read errors | Not measured; no zero values inferred |
| Screen-off | NOT_EXECUTED_ADMISSION_BLOCKED |
| Process-death / Recovery / low space | Stage 8.4 campaign not executed; historical evidence untouched |
| Automated Stage 8.4 unit/integration tests | 0; static admission checks are not segmentation tests |
| API28 / API36 | Stage 8.4 runtime tests not executed; historical parent matrix is not reused as proof |
| Native / 16 KiB / SBOM | 16 static ELF checks pass; packaged/runtime tests absent; SBOM unresolved |
| Independent review | Admission-only; 2 unresolved P1 findings; full runtime review not executed |
| Leak audit | Publication delta receives a bounded content-free audit; no audio/log campaign or device forensic-safety claim |
| Exact-SHA CI | No Stage 8.4 product acceptance claimed; external publication report records observed CI state |
| Sheet | Read-only A74:F77, no write. C74=ПРОЙДЕНО, C75=ЗАПЛАНИРОВАНО, C76=НЕ НАЧАТО, C77=ЗАПЛАНИРОВАНО |
| 8.4C / 8.5 | NOT_STARTED / NOT_STARTED. Existing 8.5 Sheet label means planned, not started |
| Development no-PIN / security restoration | Unchanged; DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE remains OPEN |

Stage 8.3 remains functional PASS under the Owner's Alpha acceptance.
PERF-REC-001 remains deferred/non-blocking Alpha; historical strict latency FAIL
is unchanged. Stage 8 remains IN_PROGRESS. No Stage 8.4C or 8.5 work is started.
The source gate's sealed baseline documents and validators are not loosened
merely to publish this blocked investigation.

## Path to resume admission

Create a new exact pinned sherpa candidate with TTS/eSpeak/piper disabled and
unnecessary APIs excluded; retain exact toolchain, source, patches and link map;
prove the unwanted components are absent. Close all remaining transitive
licenses/notices and produce a complete SBOM. Verify model/runtime pairing,
required ABIs, minSdk, 16-KiB ELF/packaging and bounded native failure handling.
Only after admission may production integration/calibration proceed.

This remains Silero through sherpa; no WebRTC or substitute engine is selected.
Neither a custom build nor a GPL redistribution disposition is silently
declared completed. The owner's 53-section acceptance scope and frozen ranges
remain mandatory, including actual acoustic evidence and a >10-minute POCO run.
