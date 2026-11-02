# Alpha v9 native startup investigation

Evidence date: 2026-11-02. This supplements the
[qualification record](ALPHA_V9_QUALIFICATION.md) and preserves
[ADR-0024's runtime boundary](adr/ADR-0024-parallel-owner-preview.md).
It changes no runtime dependency, release gate or physical-device qualification.

## Artifact and failure

The candidate built from `f31292c2ddf6f648e90b4cedd5329dce111d146c` has APK
SHA-256 `48a24b2ebdd25aa483c5852944bfe0e4625a2deef16689228c8e10bf1dfb8b47`.
Published v8 and this v9 contain identical bytes for all 11 native/VAD payloads.
Both failed ordinary recording startup on the same four-core API36 x86_64
emulator translating ARM64: SIGSEGV, null address `0x8`, ORT-relative PCs
`0x14bb1c8 / 0x1230b40 / 0xb2a2fc`. The original and paired failures remain
retained; baseline equivalence alone did not clear the critical failure.

The exact ARM64 ORT library is 22,249,560 bytes, SHA-256
`33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1`.
Its admitted ONNX Runtime 1.28.2 source is
`33ca9628233dc8f002435e868d4c2e9f82766ca1`, whose
[dependency pin](https://github.com/microsoft/onnxruntime/blob/33ca9628233dc8f002435e868d4c2e9f82766ca1/cmake/deps.txt)
selects CPUInfo `4628dc060ce4e82345dc166bbac875609db4ff69`.

## Causal investigation

A separate permission-free ARM64 diagnostic app saved bounded topology data
inside the translated native context, fsynced its private evidence, then loaded
only the unchanged ORT library. It contained no Dora, Sherpa, model or microphone
path. The same constructor crash reproduced before the load returned.

Exact-library disassembly maps `0x1230b40` to `pthread_once` with callback
`cpuinfo_arm_linux_init`, reached by the ORT static constructor at `0xb2a2fc`.
The crashing block fills an L2 cache descriptor. The library is stripped, so
this mapping uses pinned source and instructions rather than invented symbols.

The [pinned CPUInfo implementation](https://github.com/pytorch/cpuinfo/blob/4628dc060ce4e82345dc166bbac875609db4ff69/src/arm/linux/init.c)
counts cache entries from decoded ARM metadata, but its later fill pass can add
an entry based on sysfs even when the count was zero. Its null L2-array branch
and subsequent field store at null plus eight match the saved registers/fault.
The observed inputs explain how zero metadata reaches that path: present CPUs
missing from proc remain eligible for sorting, while only the reported CPU
count bounds the cache passes. An unreported CPU can therefore be selected with
zero-initialized architecture/cache data.

| Same installed diagnostic APK and AVD image/data | Four-core failure | Two-core control |
|---|---|---|
| Native `/proc/cpuinfo` CPU IDs | 0, 1 | 0, 1; identical text |
| possible/present/online CPU IDs | 0–3 | 0–1 |
| Reported ARM architecture | 8 for both reported CPUs | unchanged |
| CPU0/1 L2 level/size/sharing | 2 / 1280K / singleton | unchanged |
| Kernel, native HWCAP and page size | captured baseline | unchanged |
| ORT-only load | same constructor SIGSEGV | returned in **291 ms** |

The control changed the emulator launch CPU count to two. It removed the
proc/sysfs mismatch and produced the result predicted before execution. Source,
binary, native-visible inputs and this same-binary intervention strongly support
the CPUInfo topology/count-fill defect as the explanation of this observed
startup failure. Independent review found no blocking evidence/causality defect.
Internal count/base values and a complete frequency/sort replay were not logged;
this is not a claim of instruction-level live tracing or an exclusively
translation-specific defect.

## Qualification limits and retained evidence

Two cores is an environment control/workaround, not a corrected library. A later
normal-UI run of the unchanged signed candidate reached saved state after a
roughly 90-second scenario, with at least 2,728 VAD inferences observed before
finalization and no native crash. The earlier 1,985 count was an intermediate
recording snapshot, not the terminal total.
An initial VAD `BACKPRESSURE` condition was observed; this is not a clean VAD
quality/performance or endurance verdict. Installed-APK readback confirmed the
candidate hash above and diagnostic APK
`9f167924bff8af57627c83023a4037e34f549f84f817cfc4f5b75bd1bacef0e7`.

Owner-local evidence filenames: `ortprobe-topology-01.txt`,
`ortprobe-phase-01.txt`, `ortprobe-crash-01.log`,
`ortprobe-topology-2core.txt`, `ortprobe-phase-2core.txt`,
`installed-apk-digests-2core.json`, `standalone-result-01.md` and
`v9-2core-recording-01.txt`, `v9-2core-finalizing-01.txt` and
`v9-ui-2core-05.xml`. Raw outputs, private device paths, audio and binaries
are not included in this document. The original failure evidence is preserved.

No physical ARM device was tested, no native library/model was replaced, and no
24-hour native-ASR qualification follows. Real ARM behavior, other topologies,
remaining product defects and current-source release checks remain separate.
This investigation does not itself change the release guard or authorize release.
