# Stage 8.4 — VAD-only artifact remediation

Repository disposition: **PENDING_FINAL_PUBLICATION**.

The custom candidate fixes the generic AAR's TTS/eSpeak/piper inclusion. This
record does not declare terminal admission PASS. Physical Android execution
and accepted shared binary custody remain required. The first POCO installation
was rejected by Android with `INSTALL_FAILED_USER_RESTRICTED`; no native smoke
result is fabricated. See [runtime-smoke.json](runtime-smoke.json).

The [historical generic-AAR rejection](../vad-8.4-admission/README.md) remains
byte-identical. Its BLOCKED result is not rewritten by this successor.

## Immutable identities

| Object | Identity |
|---|---|
| Accepted parent | `23adb618a39a014e5090ee2f32e27015de485f07` |
| Initial admission HEAD | `fc1d362d4d3b692a55b5b954841276b247f4b456` |
| Branch / PR | `stage/8.4-vad-segmentation-rotation` / [PR95](https://github.com/Monumentogram/DORA/pull/95), draft and unmerged |
| sherpa source | `1.13.8`, `11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf`, pristine checkout, no submodules |
| Build profile | `dora-sherpa-vad-arm64-v1` |
| Custom AAR | `sherpa-onnx-vad-1.13.8-dora.1-arm64.aar`, 23,396,212 bytes |
| AAR SHA-256 | `64969d0fbf5d3936a127879684bc2f14014b99817ca3c9b2e16298c1372208db` |
| JNI | `libsherpa-onnx-jni.so`, 611,896 bytes; SHA-256 `2e7412da436e02b705cea92d6fe6e3d2f2bf97a483b12d9ae2cc65ad6b723cc7` |
| ORT | `libonnxruntime.so`, 22,249,560 bytes; SHA-256 `33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1` |
| ORT source | `1.28.2`, `33ca9628233dc8f002435e868d4c2e9f82766ca1` |
| Model, separate from AAR | Silero VAD6.2.1; MIT; 2,327,524 bytes; SHA-256 `1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3` |

The unchanged upstream version API embeds the older short Git label `8c8e275d`.
That label is not substituted for the verified checkout identity. The recipe
requires the exact clean full Git SHA and records source/API input hashes.

## Build and feature boundary

[ADR-VAD-001](../../adr/ADR-VAD-001-isolated-vad-only-runtime-admission.md)
explains the external JNI target. [build-inputs.json](build-inputs.json) contains
every effective Boolean CMake switch, toolchain/profile, archive hashes, linked
objects, native LOAD segments, dynamic dependencies and all20 JNI exports.
All sherpa options are OFF except `SHERPA_ONNX_ENABLE_C_API=ON`, which prevents
upstream from forcibly enabling its generic Android JNI target. C_API is only
configured; the build command names only `dora-vad-jni`.

The two unchanged JNI translation units are `voice-activity-detector.cc` and
`version.cc`. Unneeded ASR/KWS/punctuation/speaker/language/denoising objects are
not pulled from the static archive. The actual link map includes15 sherpa
objects, kaldi-native-fbank, kissfft and NDK runtime support. No kaldi-decoder,
kaldifst, OpenFST or sentencepiece archive member enters the linked candidate.
TTS/eSpeak/piper and diarization are excluded by upstream switches. There is
no removal of GPL files after linking, no altered upstream source and no hidden
packaged TTS resource. Binary string/export/dependency checks supplement the
positive source and linker-closure evidence.

The shared VAD factory retains the sherpa TEN adapter, which requires Eigen,
fbank and kissfft. TEN model weights and their separate restrictive terms are
not admitted. Only the preserved Silero model is selected. ORT's pinned binary
retains NNAPI and KleidiAI capabilities; the isolated test selects CPU.

Both packaged `.so` files are AArch64, have compatible LOAD alignment and
congruence, and are reported **STATIC_16K_ALIGNMENT_COMPATIBLE**. There is no
physical16KiB runtime claim. The recipe strips JNI debug/symbol data; ORT is
supplier-stripped. No other ABI is packaged or claimed as native-tested.

## Reproduce without developer-machine state

Required Windows x86_64 toolchain: Temurin17.0.20.1+1, Python3.12.14,
Android SDK platform36/build-tools36.0.0, NDK28.2.13676358, CMake3.22.1
(`3.22.1-g37088a8-dirty` Android SDK build), Ninja1.10.2. Target API28,
arm64-v8a, Release, static libc++. The upstream ORT binary separately uses
NDK27.3.13750724/API27; its producer/source evidence is inventoried.

Create a pristine upstream checkout and an output directory outside every Git
worktree. Set `$VadSource`, `$VadOutput`, `$AndroidSdk`, `$Jdk17` and `$Python`
to those explicit local paths. Do not put credentials in command arguments.

```powershell
git clone --no-checkout https://github.com/k2-fsa/sherpa-onnx.git $VadSource
git -C $VadSource checkout --detach 11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf
& $Python tools/vad_admission/build.py build --source $VadSource --output $VadOutput --sdk $AndroidSdk --jdk $Jdk17 --notices docs/evidence/vad-8.4-remediation/NOTICE.txt
```

The script verifies clean source/submodule state, clears compiler/ORT override
environment variables, fixes `TZ=UTC` and `SOURCE_DATE_EPOCH=0`, invokes CMake
with explicit toolchain paths and target flags, builds only the selected target,
compiles8 exact upstream Java source files with Java8 bytecode/no debug data,
inspects both ELF files, and packs6 ZIP entries with deterministic ordering,
timestamps, modes and no compression. AAR classes.jar contains14 entries.
No Kotlin runtime, upstream ORT Java JAR, ORT Java JNI library or model is in AAR.
Upstream FetchContent downloads are checked by upstream pinned archive hashes.

Expected output: the exact AAR/hash above and `candidate-inventory.json`.
Three builds, including two fresh rebuild directories on this host/toolchain,
produced the same AAR bytes and SHA. The third uses the review-fixed recipe,
which rejects nonempty output/cache and checks the frozen AAR identity before
harness creation. This is not an independent-host or CI native rebuild.

The synthetic PCM fixture is generated locally using Windows SpeechSynthesizer
at16kHz, S16LE mono; no owner recording is required. The harness recipe accepts
the existing exact model and its pinned MIT text; it never downloads a model:

```powershell
& $Python tools/vad_admission/build.py harness --output $HarnessOutput --sdk $AndroidSdk --jdk $Jdk17 --aar $Aar --model $Model --model-license $ModelLicense --speech $SyntheticSpeechWav
```

The disposable APK has a separate package, no microphone/network permission,
no backup, and a disposable signing key. It checks model identity, loads the
two exact libraries, processes silence/synthetic speech, exercises reset,
flush/front/pop, releases VAD, and writes only content-free counters/identities.
This is API/runtime admission, not Stage8.4 acoustic or segmentation acceptance.

## License, NOTICE and SBOM

[license-inventory.json](license-inventory.json) is the complete scoped
engineering license inventory for the distributed graph, including copied
source families and header-only components. It is not legal counsel.
[NOTICE.txt](NOTICE.txt) retains50 source notice/attribution sections plus their index and exact
source-access instructions. It is packaged byte-identically inside the AAR.
MPL2 Eigen-derived code and LLVM legacy terms remain explicit; no claim is made
that removal of GPL TTS removed all copyleft obligations.

[sbom.cdx.json](sbom.cdx.json) binds36 components to the actual AAR, native
files and Java classes hash. The model is a separate non-AAR supply-chain object.
It contains no eSpeak/piper phantom component. Broad LLVM attribution bundles
do not mean every host compiler component is distributed in the runtime.

## Comparison with rejected generic AAR

| Measure | Generic rejected | Custom candidate |
|---|---:|---:|
| AAR bytes | 50,129,134 | 23,396,212 |
| Internal entries | 26 | 6 |
| Native files | 16 | 2 |
| ABIs | 4 | arm64-v8a only |
| arm64 native payload bytes | 31,927,176 | 22,861,456 |
| classes.jar entries | 128 | 14 |
| TTS/eSpeak/piper | present | absent |
| Bundled NOTICE/license package | absent | present |
| Complete scoped engineering inventory/SBOM | blocked/incomplete | supplied for custom candidate |

ZIP compression differs; byte comparison is actual artifact size, not a claim
of an equal-compression benchmark. No custom binary or model is committed or
uploaded publicly. Existing SQLCipher public Git/Maven custody does not silently
authorize publication of this23MB artifact. **VAD_BINARY_CUSTODY_REQUIRED**
remains until a shared immutable retrieval location is accepted.

## Governance and external gates

The successor validator allows only the exact named admission evidence/tool/ADR
paths. It preserves all three historical rejected-candidate files, source
history linearity, parent seals, product sources, workflow, thresholds and the
OPEN security-restoration blocker. Unexpected docs, workflow, runtime, schema
and historical evidence deltas continue to fail. Existing mandatory CI invokes
the new metadata checks and negative controls; it does not rebuild native code
or claim a fake-engine test proves native execution.

Independent review, bounded leak audit and exact final-SHA CI are external
publication gates and must be reported with actual outcomes after execution.
No Google Sheet write is required or authorized by admission alone: C75 stays
ЗАПЛАНИРОВАНО, C76 stays НЕ НАЧАТО. Production VAD integration,8.4C and8.5 remain
NOT_STARTED. Stage8 remains IN_PROGRESS. Development no-PIN behavior and
DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE=OPEN are unchanged.
