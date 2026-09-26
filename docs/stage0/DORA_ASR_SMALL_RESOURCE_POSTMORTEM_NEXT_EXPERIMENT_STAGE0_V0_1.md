# ASR SMALL resource postmortem and next experiment — Stage 0 v0.1

26 September 2026. **PASS / SMALL_RESOURCE_FAILURE_ASSESSED_NEXT_EXPERIMENT_FROZEN**.

Select exactly one next experiment: **ASR-SMALL-ARM82-01 — the same multilingual SMALL q5_1
artifact with whisper.cpp v1.9.4 rebuilt for `armv8.2-a+fp16+dotprod`, CPU-only, four threads**.
Keep decoding, full audio context, scoring, limits and lifecycle unchanged. Use a new untouched
24 RU + 24 EN holdout. This is a prospective experiment decision, not a demonstrated speedup
or production admission. No new inference, model load, audio decode or device command occurred.

The [previous measured result](DORA_ALPHA_ASR_SMALL_MEASURED_CAMPAIGN_STAGE0_V0_1.md) remains
**INCOMPLETE / BOUNDED_SMALL_POCO_CAMPAIGN_STOPPED**, with candidate disposition
**VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_RESOURCE_GATE** and decisive failure **RTF_MAXIMUM**.
Its exact model/runtime experiment remains rejected. Quality remains NOT_EVALUABLE;
the partial RU normalized WER 65/369 = 17.615176% is not a language PASS. No EN case ran.

## Authority and retained-evidence method

Fresh fetch, local HEAD and remote HEAD agreed on `e12f66f1507d87b00f91070342128e054b274aac`;
the canonical branch `chat/alpha-asr-runner-scope` was clean, including untracked files.
The owner's present postmortem instruction authorizes numeric per-attempt publication using
sanitized ordinals, superseding the earlier aggregate-only publication restriction for this
narrow projection. It does not authorize sample identities, text, private paths or source metadata.

All 19 primary journal records were read using SQLite read-only immutable mode; integrity_check
passed. Native metrics, raw memory samples, raw thermal records, observations, cleanup records,
stderr sizes and retained process identities were reconciled. All attempts are finalized,
SUCCEEDED, ordinal 1, with distinct processes, nonoverlapping windows and VERIFIED cleanup.
All 308 files in the retained control/evidence tree were fingerprinted before and after analysis;
their bytes remained unchanged. Hashing is an integrity check, not transcript interpretation.
No transcript or reference content was used in diagnosis or selection, and no audio was decoded.
Six retained native artifacts match the original campaign pins; upstream runtime source is clean
at the exact recorded revision. The [decision JSON](../evidence/poc-asr-001/asr-small-resource-postmortem-next-experiment-stage0-v0.1.json)
contains exact ratios, all numeric rows, static audit evidence and the prospective contract.

## Distribution — POSTMORTEM_DIAGNOSTIC_ONLY

Every descriptive statistic, correlation, fit, subset aggregation, case comparison and hardware
performance expectation in this postmortem is **POSTMORTEM_DIAGNOSTIC_ONLY**. None replaces the
frozen evaluator. This is a stopped, RU-only prefix, not an unbiased full-campaign distribution.
The 19-case empirical p95 is rank 19 and therefore the maximum; the frozen 48-case p95 remains
NOT_EVALUABLE. No population-tail confidence or EN timing conclusion follows.

| RTF diagnostic | Value |
|---|---:|
| Minimum | 1.155312 |
| Median / p50 | 2.337809 |
| Arithmetic mean | 2.665495 |
| p75, nearest rank 15 | 3.584925 |
| p90, nearest rank 18 | 4.787871 |
| Empirical p95, nearest rank 19 | 6.063749 |
| Maximum | 6.063749314128944 |
| Count strictly above 2 / 4 / 6 | 10 / 3 / 1 |
| Observed-prefix duration-weighted RTF | 1.994219 |

Quantiles use ascending exact per-case fractions and nearest rank `ceil(p*N)`; median is the
middle observation. Means/correlations are descriptive floating-point values. The prefix weighted
value below 2 does not pass the frozen complete-48 gate and does not erase the maximum failure.

All rows below are **POSTMORTEM_DIAGNOSTIC_ONLY**. Audio/inference/cold-load/decode are seconds;
PSS and native heap are bytes. All 19 have thermal maximum 0/NONE, zero evidenced OOM,
valid timing/resource telemetry, fresh process/context, exit success and VERIFIED cleanup.
Those common fields are also explicit in every machine-readable row.

| RU attempt | Audio s | Inference s | RTF | Cold s | Decode s | PSS bytes | Heap bytes |
|---|---:|---:|---:|---:|---:|---:|---:|
| 01 | 14.256 | 20.297822 | 1.423809 | 0.253669 | 0.018984 | 360298496 | 932875128 |
| 02 | 3.996 | 19.132333 | 4.787871 | 0.250853 | 0.006504 | 359491584 | 931823480 |
| 03 | 5.940 | 19.395802 | 3.265287 | 0.251540 | 0.007323 | 359565312 | 932085592 |
| 04 | 17.388 | 21.309799 | 1.225546 | 0.254067 | 0.023525 | 360551424 | 933922712 |
| 05 | 5.940 | 19.586389 | 3.297372 | 0.250595 | 0.009270 | 359543808 | 932087000 |
| 06 | 4.068 | 18.961837 | 4.661219 | 0.253058 | 0.005442 | 359417856 | 931823448 |
| 07 | 8.676 | 20.282827 | 2.337809 | 0.251536 | 0.011102 | 359712768 | 932611800 |
| 08 | 6.588 | 19.127005 | 2.903310 | 0.254587 | 0.008126 | 359606272 | 932085720 |
| 09 | 18.360 | 21.211528 | 1.155312 | 0.255927 | 0.023080 | 360782848 | 933922936 |
| 10 | 5.076 | 18.197078 | 3.584925 | 0.255522 | 0.006183 | 359249920 | 932077560 |
| 11 | 11.340 | 20.362269 | 1.795615 | 0.255322 | 0.014500 | 360096768 | 932873976 |
| 12 | 10.368 | 19.718111 | 1.901824 | 0.255177 | 0.012867 | 360049664 | 932611288 |
| 13 | 4.896 | 17.979920 | 3.672369 | 0.255040 | 0.006025 | 359504896 | 932077432 |
| 14 | 13.356 | 20.826435 | 1.559332 | 0.254649 | 0.016572 | 360167424 | 932874072 |
| 15 | 13.536 | 20.757617 | 1.533512 | 0.254034 | 0.016427 | 360217600 | 932875608 |
| 16 | 17.820 | 20.799529 | 1.167201 | 0.254406 | 0.022015 | 360648704 | 933922808 |
| 17 | 6.120 | 18.897141 | 3.087768 | 0.324106 | 0.007627 | 359649280 | 932084824 |
| 18 | 18.396 | 22.453828 | 1.220582 | 0.264897 | 0.023187 | 360809472 | 933925816 |
| 19 | 2.916 | 17.681893 | 6.063749 | 0.257571 | 0.003676 | 359204864 | 931811320 |

The five highest RTFs are RU attempts **19, 02, 06, 13, 10**. They have audio durations
2.916, 3.996, 4.068, 4.896 and 5.076 seconds respectively. Their inference times are
17.681893, 19.132333, 18.961837, 17.979920 and 18.197078 seconds. The high-RTF cases are
short clips, not the longest inference executions.

Across 2.916–18.396 seconds of audio, inference occupies a narrow 17.681893–22.453828 second
range (median 19.718111). Pearson correlation: duration vs RTF **-0.902755**; duration vs
inference seconds **+0.912713**. The descriptive least-squares fit is
`inference_seconds = 17.757846 + 0.209379 * audio_seconds`, R-squared **0.833044**.
Its intercept is not a separately measured phase and must not be extrapolated outside this data.
The RTF relationship partly follows directly from dividing by duration; correlation alone is not
causality. The nearly flat absolute inference cost and the source audit independently support it.
Mean RTF by duration band: 1–5 s **4.796302** (4 cases), >5–10 s **3.079412** (6),
>10–15 s **1.642818** (5), >15–20 s **1.192160** (4).

## Decisive attempt — POSTMORTEM_DIAGNOSTIC_ONLY

**RU attempt 19**, late in the executed prefix and the last executed case, produced exactly
`17681893 / 2916000 = 6.063749314128944`. It is the shortest audio and the fastest absolute
inference in the 19 observations. Classification: **representative of systemic performance**,
specifically the short-input tail produced by a substantial per-clip compute cost. It is not
evidence of an isolated latency stall; this does not establish failure of every SMALL configuration.

Its cold load was 257571 us and decode 3676 us, both outside inference RTF. Peak PSS was
359204864 bytes, native heap 931811320 bytes, thermal 0, evidenced OOM 0. Memory had 169 samples,
maximum gap 113533 us; thermal had 19 samples, maximum gap 1094000 us. These satisfy the existing
1000000/3000000 us gap bounds. Native stderr was empty and cleanup VERIFIED.
The preceding RTFs were attempt 17 **3.087768** and attempt 18 **1.220582**; attempt 20 never ran.
All used the same fresh-process/model-load/cleanup lifecycle and zero retries. The gap after
attempt 18's native window was 218.402848 s, within the other gaps of 218.029975–219.601241 s.
Repeated host verification/transfer is outside the inference timer and explains long campaign
wall-clock gaps, not the RTF rejection. There is no distinct relaunch or telemetry anomaly at 19.

## Root-cause assessment and limits

| Confidence | Assessment and evidence |
|---|---|
| HIGH | A systemic short-clip cost in this exact SMALL/runtime configuration explains the RTF shape better than one slow execution. Ten cases exceed RTF 2; three exceed 4; the decisive case has the shortest absolute inference. |
| MEDIUM | Default full audio-context computation is the most likely structural contributor: `audio_ctx=0`, model context 1500, 30-second model window, full-width convolution/encoder/cross-attention graphs and padded input in pinned source. Phase timings were not retained, so the encoder share or number of encoder evaluations is not measured. |
| MEDIUM | The conservative ARM build is a plausible avoidable contributor. Omission of FP16 vector arithmetic and DOTPROD is proven; their performance contribution is not measured. The pinned Q5_1 dot-product path uses a multiply/widen/add fallback, and F16 SIMD falls back to FP32 conversions. |
| LOW | Four threads on a heterogeneous CPU, scheduling, frequency scaling or contention could contribute. No CPU residency/frequency/utilization trace exists. Four is a configured compute-thread count, not proof of four big cores. No thread count is selected from a holdout benchmark. |
| LOW / unsupported | Acoustic or token-generation anomalies cannot be assigned from timing alone. No transcript inspection, language-specific causal claim or hidden case exclusion is used. |
| HIGH confidence against the proposed explanation | Codec and model-load overhead cannot directly explain this inference-only RTF: they are separately timed and excluded. Decode was 3676–23525 us; cold load at most 324106 us. |
| No positive evidence | Memory exhaustion or observed severe thermal. PSS max 360809472, heap max 933925816, evidenced OOM 0, thermal 0 throughout. These are sampled/attributed observations, not proof of all-system OOM absence or zero DVFS throttling. |

Memory and thermal telemetry was valid on 19/19 executed attempts (19/48 intended). Maximum
memory/thermal gaps were 127201/1125000 us. PSS and allocated heap are distinct quantities and
must not be added. Retained battery snapshots were USB-powered at 100%, battery temperature
29–31 C; that is not CPU temperature. Ordinal correlation with RTF is -0.047863 and with inference
seconds +0.025427: no observed campaign-progress trend explains the last-case ratio.

## Actual runtime audit

**ACTUALLY_USED:** `ggml-org/whisper.cpp v1.9.4`, commit
`927cfce34f31707e17f2bff35c349632fb9e2c3a`; test-only `dora-asr-campaign infer ru/en`
calling `whisper_full` directly, not whisper-cli or `whisper_full_parallel`.
CPU backend `libggml-cpu.so`, `use_gpu=false`, `flash_attn=true` (CPU algorithm, not GPU evidence).
Exactly `n_threads=4`; ggml default pool priority 0/inherited, poll 50, strict_cpu=false and
zero/default affinity mask. No taskset, custom affinity, governor or priority override was issued.
Actual core residency remains unmeasured. A separate 100-ms memory sampler also runs.

Greedy argmax, temperature 0, temperature_inc 0, translate=false, explicit row locale,
detect_language=false, no_context=true, no initial prompt or prompt tokens. Resolved best_of=5
is retained, but the temperature-zero greedy path uses one decoder; it is not five campaign retries.
audio_ctx=0, n_max_text_ctx=16384, no_timestamps=false, token_timestamps=false, single_segment=false,
VAD=false, DTW=false. Preserve the entire [resolved profile](../evidence/poc-asr-001/alpha-asr-decoding-profile-stage0-v0.1.json),
including default suppression/fallback fields; no beam/temperature/prompt search occurred.
Miniaudio 0.11.24 converts to 16-kHz mono float32 with its default resampler; no added trimming,
filtering, normalization or VAD. A fresh process initializes and frees one model context per case;
OS page caches are not forcibly cleared, so 'cold load' means fresh context, not proven cold disk cache.
One generated-silence warmup precedes the corpus, with no per-case warmup.

Build: NDK 28.2.13676358 (r28c), clang 19.0.1, CMake 3.22.1, Ninja 1.10.2;
Android arm64-v8a/API28, Release `-O3 -DNDEBUG`, c++_shared, CPU `-march=armv8-a`.
NEON and FMA are enabled; OpenMP, native host tuning, CPU all-variants, backend dynamic loading,
BLAS, KleidiAI, LTO and optional GPU/NPU backends are off. Retained CPU disassembly contains
0 SDOT/UDOT and 0 FP16 vector FMA instructions, versus 553 FP32 vector FMA instructions.
These static counts prove emitted-code properties, not how often instructions ran.
The six verified artifacts are the campaign executable, libwhisper.so, libggml.so,
libggml-base.so, libggml-cpu.so and NDK libc++_shared.so; exact hashes are in the JSON.

Reproducible pinned source anchors: [full-width encoder graph](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/src/whisper.cpp#L2070),
[Q5_1 dot-product kernel](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/ggml/src/ggml-cpu/arch/arm/quants.c#L1032),
[DOTPROD fallback](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/ggml/src/ggml-cpu/ggml-cpu-impl.h#L307),
[FP16 SIMD dispatch](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/ggml/src/ggml-cpu/simd-mappings.h#L263),
[ARM build switch](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/ggml/src/ggml-cpu/CMakeLists.txt#L171)
and [thread-pool defaults](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/ggml/src/ggml.c#L8067).

**AVAILABLE_BUT_NOT_USED:** pinned ARM FP16/DOTPROD source paths and supported
`GGML_CPU_ARM_ARCH` build switch; the upstream Android example also has an FP16 build variant.
Vulkan/OpenCL and other optional source backends exist but were not built or exercised here.
No claim of working POCO acceleration follows from their presence. Audio-context override exists
but is not selected because reducing context changes the computation/quality question.

**HYPOTHETICAL:** a speedup from a different thread count, CPU affinity, Vulkan/OpenCL,
NNAPI/NPU, reduced context or persistent-context serving. This retained adapter has no NNAPI
integration and offers no evidence for such a backend on this device. No i8mm/SVE support is assumed.

## Alternatives and selection

The technical plan separates model quality from runtime and keeps engines behind a replaceable
TranscriptionEngine; this decision remains an isolated PoC. Expectations below are
**POSTMORTEM_DIAGNOSTIC_ONLY**, qualitative engineering judgments, not predicted WER or measured speedups.
All considered paths operate offline once artifacts are obtained; normalizer/Java scoring can be reused.

| Option | RU / EN quality expectation | RTF, memory and cold-load expectation | Compatibility, work and risk | Decision |
|---|---|---|---|---|
| A: SMALL q5_1, ARMv8.2 FP16/DOTPROD, 4 threads | Same capacity/decoding; both gates unknown. Floating-point kernel changes can change outputs, so new quality measurement is mandatory. | Addresses demonstrated omitted CPU paths; speedup unknown. Model bytes unchanged; broadly similar memory/load expected, unproven. | Existing Android ARM64 adapter/evaluator, MIT model/runtime, no new dependency; one isolated rebuild and profile binding. Lowest integration cost and fastest credible test. | **SELECTED**: strongest bounded next-test rationale; joint-pass probability unknown, with short-clip and EN risks. |
| B: multilingual Whisper BASE q5_1, same runtime | Prior distinct BASE holdout failed RU 30.5% and EN 18.4300%; smaller capacity is a poor quality direction. Cross-holdout figures are not a controlled comparison. | Historical BASE was faster/lighter; does not establish acceptance on a new set. | Existing Android/offline/MIT path, very low integration risk and quick test; evaluator fully reusable. | Reject: speed advantage already has material RU-quality counterevidence. Tiny would increase this quality risk without DORA evidence. |
| C: multilingual SMALL q8_0 | May reduce quantization error; RU/EN gain unknown. | More bytes and likely memory/load cost; kernel-dependent speed may improve or worsen. No evidence quantization or bandwidth, rather than full-window compute, dominates. | Same Android/offline/MIT family and evaluator, new artifact/provenance plus device test; low–medium risk. | Reject: less direct evidence for addressing the bottleneck; quantization alone is not a speed guarantee. |
| D: Parakeet-TDT 0.6B v3 INT8 through sherpa-onnx CPU | Upstream RU/EN support; DORA quality unknown. | Different FastConformer/TDT design is promising; POCO RTF, memory and load are unmeasured; larger model is not presumed faster. | Official Android path exists; new runtime/ONNX frontend/tokenizer adapter, ABI/16-KiB/provenance work. Apache-2.0 runtime and CC-BY-4.0 original weights; conversion/redistribution notices require verification. Scoring reusable; runtime implementation risk higher and test slower. | Reject for this next step: plausible reserve, materially larger integration scope before device evidence. |

A is selected because the omitted CPU instructions are directly evidenced and can be changed
without lowering model capacity or shortening the audio context. It does not establish that A
will pass. Shrinking context, changing threads/affinity and changing quantization simultaneously
would obscure the question; this contract freezes one ISA build variant, not a parameter sweep.

Primary external context: [POCO M5 specifications](https://www.po.co/global/product/poco-m5/specs/),
[Helio G99 CPU topology](https://www.mediatek.com/products/smartphones/mediatek-helio-g99),
[Arm instruction capabilities](https://developer.arm.com/-/media/Arm%20Developer%20Community/PDF/Cortex-A%20R%20M%20datasheets/Arm%20Cortex-A%20Comparison%20Table_v4.pdf),
[pinned Q5_1 artifact](https://huggingface.co/ggerganov/whisper.cpp/blob/f281eb45af861ab5e5297d23694b7d46e090c02c/ggml-small-q5_1.bin),
[Parakeet model card](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3),
[sherpa Android/model documentation](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/nemo-transducer-models.html).
These establish identities/capabilities, not a DORA performance result. G99's two A76 and six A55
cores motivate checking ISA support on the actual device before loading the next binary.

## Prospective contract: ASR-SMALL-ARM82-01

The current owner instruction is authority to select and freeze this contract. It does not
execute it. The JSON `prospectiveExperiment` is the complete machine-readable expansion;
its sorted compact UTF-8 canonical SHA-256 is recorded alongside it. Any semantic change requires
a new prospective decision before inference; no automatic fallback candidate or configuration.

**Candidate:** multilingual OpenAI Whisper SMALL, GGML q5_1, `ggml-small-q5_1.bin`,
190085487 bytes, SHA-256 `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`.
Source `ggerganov/whisper.cpp`, exact revision `f281eb45af861ab5e5297d23694b7d46e090c02c`;
MIT. This pins the already admitted converted artifact, not an unverified new conversion.

**Runtime/build:** `ggml-org/whisper.cpp v1.9.4` at
`927cfce34f31707e17f2bff35c349632fb9e2c3a`, MIT, unchanged source. Reuse the exact NDK/CMake/Ninja,
API28/arm64-v8a/Release/c++_shared build recipe and CPU backend configuration, changing only
`GGML_CPU_ARM_ARCH=armv8.2-a+fp16+dotprod`. Keep `GGML_NATIVE=OFF`, CPU=ON, CPU_ALL_VARIANTS=OFF,
CPU_REPACK=ON, CPU_KLEIDIAI=OFF, OPENMP=OFF, BLAS=OFF, BACKEND_DL=OFF, LTO=OFF and all optional
accelerators OFF. No source patch, aggressive fast-math, i8mm/SVE or LTO is added. Native adapter
source remains unchanged. The full explicit relevant option set is in JSON.

**Runtime policy:** four compute threads, CPU-only, use_gpu=false, flash_attn=true; default
inherited scheduling/affinity and no device settings changes. Exact prior decoding profile,
audio_ctx=0/full context, decoder, fresh-process/model-load/cleanup lifecycle and one generated
3-second silence warmup are retained. No thread/quantization/backend/context sweep is allowed.

**Device/preflight:** physical POCO M5 only. Before executing the specialized runtime,
a baseline-compatible capability check must verify `HWCAP_ASIMD`, `HWCAP_FPHP`, `HWCAP_ASIMDHP`
and `HWCAP_ASIMDDP` on the actual device; stop on absent/unknown support, without fallback.
Confirm compile macros, emitted DOTPROD/FP16 paths, ELF/dependency/16-KiB packaging checks,
runtime/model/decoder/source hashes, storage/retention and resolved profile. Record the device's
actual page size without claiming 16-KiB runtime proof from this POCO. CPU flags/topology/default
affinity and power/thermal context may be recorded read-only in the next task; no optimization
decision may use the new holdout. No such device check was run in this task.

**Data:** exactly **24 new RU + 24 new EN** from the same admitted Common Voice Spontaneous
Speech 5.0 release/test splits, under the current task's new-holdout direction and existing lawful
use/storage terms. Keep eligibility 1000–20000 ms, valid decode, non-empty reference, exact source
identity/content binding and unchanged deterministic hash ranking. Before ranking exclude all
original BASE 48 and all consumed SMALL 48, including the 29 unexecuted SMALL selections,
by source identity and audio digest; exclude every other evaluation/tuning use and all members
of duplicate-audio groups. Apply lawful participant-overlap exclusion where metadata permits;
otherwise record the limitation without re-identification. Do not narrow durations to avoid short
clips, inspect references to select easy cases or substitute after results. New pool/manifest
availability is not claimed. Fewer than 24 eligible cases in either language means stop.
Rank `SHA256(UTF8("dora-alpha-asr-v0.1") || NUL || UTF8(locale) || NUL || exact UTF8(path))`,
tie-break exact path bytes, take first 24 per language; execute RU24 then EN24. Fixed order remains
a known time/language confounder. No old holdout is a regression or tuning set.

**Quality/resources:** unchanged BasicTextNormalizer(False, False), CPython 3.12/Unicode 15,
unchanged Java oracle and exact integer aggregate normalized WER gates RU <=20%, EN <=18%,
both on complete 24-case coverage. Raw WER diagnostic; timestamp quality NOT_EVALUABLE.
Across all 48, weighted RTF <=2, nearest-rank p95 (rank 46) <=4, maximum <=6; cold load <=15 s;
PSS <=1610612736 bytes; native allocated heap <=1342177280 bytes; evidenced OOM=0; thermal <3;
complete telemetry. Timing regions and sampling remain unchanged: memory target/max gap
100/1000 ms, thermal polling/max gap 1000/3000 ms, before decode through model free.
End-to-end timing remains diagnostic. No acceptance limit change is proposed.

**Attempts/stops:** exactly one future campaign invocation, one primary attempt per case,
zero automatic/infrastructure/quality retries, no case replacement. Preserve durable start marker,
journal and partial evidence. One codec decode-only check and one silent warmup; no unscored
holdout inference or repeated performance preflight. Stop on identity/input drift, invalid output,
required telemetry failure, cleanup failure, timeout, positive OOM, SEVERE+ thermal or an
evidenced per-case resource breach. Timeout 600 s/native alarm 605 s remain safety bounds.
Do not abort or repeat a case merely for a poor per-case WER; aggregate with the unchanged rules.
Incomplete coverage cannot PASS; a positive resource/quality failure retains VALID_FAIL meaning.

**Before first inference, including warmup:** freeze the selected manifest/exclusion/pool digests,
new operator/source identity, all new binary hashes, unchanged model/decoding/scoring/protocol,
this contract digest and retention/storage authority. Artifact and manifest hashes are outputs
of the next materialization, not fabricated in this decision. Implement a separate successor
execution binding without editing v0.3 or historical evidence. Retention remains **2026-10-25**,
with no extension, publication of private data or production admission. If completion/retention
cannot meet that deadline, stop and follow existing deletion obligations.

## Validation, publication and stage effect

Strict JSON, exact diagnostic arithmetic/raw reconciliation, contract digest, single selection,
unchanged thresholds, privacy/local references and diff hygiene: **PASS**. All **7 Stage00 checks
PASS**. All **609 unchanged baseline files** and **308 retained private files** retain their exact
bytes; both status/backlog additions preserve the full previous bytes. No Android checks or
ASR execution ran. The result commit and fresh remote readback are supplied in the handoff.

Exactly four public files change: this report, its decision JSON, additive Stage Status and
Implementation Backlog entries. Every other tracked file retains its prior raw bytes, including
the SMALL result, operators, thresholds, Recovery and production Android files. Earlier dated
status/backlog contents remain byte-preserved. PR #86 stays OPEN/DRAFT/UNMERGED with unchanged
head/base and update time. One atomic commit and normal push; no PR creation or merge.

5.6C.2B and 5.6C.2 measured completion remain INCOMPLETE with the original VALID_FAIL resource
disposition. Postmortem and prospective decision are complete; next experiment is
**FROZEN / NOT_EXECUTED**. Stage 5/Group B stay NOT_PASS; POC-ASR-001 stays BLOCKED / NOT_READY.
Recovery remains **0D.6 = ALPHA CLOSED / FULL OPEN**. CI has no applicable branch push trigger.

Operation counts in this task: **ASR inference 0; device execution 0; current holdout reruns 0;
tuning on current holdout 0; threshold changes 0; Recovery changes 0; PR #86 changes 0; merges 0**.
Also model loads, new audio decodes, model downloads and new holdout materializations are zero.

**Immediate next task:** implement the separate ASR-SMALL-ARM82-01 execution binding, build and
pin the one specialized CPU variant, materialize the new exclusion-verified RU24+EN24 holdout,
complete capability/storage/static/profile preflight, freeze all actual identities, and perform
one separately authorized physical POCO M5 campaign under this contract. Publish its terminal
result without changing configuration or thresholds in response to measurements.
