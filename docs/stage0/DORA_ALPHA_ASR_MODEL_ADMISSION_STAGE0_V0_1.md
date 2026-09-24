# DORA bounded Alpha ASR model admission — Stage 0 v0.1

Task: **5.2**. Decision date: **2026-09-24**. Scope: `BOUNDED_INTERNAL_ALPHA_ASR_EVALUATION_ONLY`.

Result: **PASS / MODEL_ARTIFACT_AND_RUNTIME_SOURCE_PINNED_FOR_STAGE0_EVALUATION**.
This is an exact artifact and runtime-source admission record, not a benchmark result or production ADR.

## Authority and continuity

The Project Owner's task 5.2 instruction authorizes this exact candidate under the established bounded Stage-0 Product, Legal/IP and Engineering/Security roles. The license state is `EVALUATION_APPROVED` for the scope above. It does not assign or replace production reviewers.

Branch: `chat/alpha-asr-model-scope`, created from exact predecessor `6d2b2bfdecb7cd79afd3ab61ff0e32b9fec0c811` after remote fetch. Initial HEAD equalled that predecessor. Initial tracked/staged changes were empty; only existing user-owned `.codex-remote-attachments/` files were untracked and remain untouched.

Governance: [IP asset policy](DORA_MVP1_IP_ASSET_POLICY.md), [technical plan §15](../DORA_MVP1_TECHNICAL_PLAN.md), [readiness](../DORA_MVP1_IMPLEMENTATION_READINESS.md), [backlog](../DORA_MVP1_IMPLEMENTATION_BACKLOG.md), [PoC gates](DORA_MVP1_POC_GATES.md), [completed Alpha data scope](DORA_ALPHA_ASR_DATA_SCOPE_STAGE0_V0_1.md), and [synthetic scoring evidence](../evidence/poc-asr-001/i1-synthetic-scoring-oracle-local-evidence-stage0-v0.1.json). `POC-ASR-001` quality/runtime readiness and `ML-CATALOG-001` production activation are not advanced by this record. Prior completed data evidence remains immutable history; this document records the new 5.2 state.

## Exact model

| Field | Pinned value |
|---|---|
| Family | OpenAI Whisper multilingual BASE |
| Bounded languages | RU / EN; multilingual = true |
| Quantization | `q5_1` |
| Artifact | `ggml-base-q5_1.bin` |
| Bytes | `59707625` |
| Local and upstream LFS SHA-256 | `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898` |
| Upstream README and local artifact SHA-1 | `a3733eda680ef76256db5fc5dd9de8629e62c5e7` |
| Git blob SHA-1 of LFS pointer, not model bytes | `4947e22c7365d941a9864b2d0f9f474a505cf39b` |
| Converted-model repository revision | `5359861c739e955e79d9a303bcbc70fb988958b1` |
| License | MIT, original and converted weights reviewed separately |

Source: [immutable official artifact](https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base-q5_1.bin) and [pinned converted-model card](https://huggingface.co/ggerganov/whisper.cpp/blob/5359861c739e955e79d9a303bcbc70fb988958b1/README.md). The [pinned runtime models documentation](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/models/README.md) explicitly refers to this model repository. No fallback artifact was downloaded.

Metadata, LFS pointer and licenses were resolved before download. License review completed at `2026-09-24T11:44:27.822921+00:00`. Acquisition completed at `2026-09-24T11:45:09.513561+00:00`. The acquired file and its controlled-store copy both match the size and SHA-256 above; local SHA-1 also matches the upstream card.

## Provenance and rights

Original weights are OpenAI Whisper `base.pt`, identified by upstream SHA-256 `ed3a0b6b1c0edf879ad9b11b1af5a0e6ab5db9205f891f668f8b0e6c6326e34e` in [pinned OpenAI source](https://github.com/openai/whisper/blob/86098128c0b4f24f0e2aa2994de830614b474227/whisper/__init__.py). This input digest is an upstream reference, not a claim that DORA downloaded or locally verified the original checkpoint.

The published conversion path is OpenAI checkpoint plus tokenizer/mel assets → `models/convert-pt-to-ggml.py` → whisper.cpp quantizer with `q5_1`. Reference converter and quantizer source are pinned at runtime commit `927cfce34f31707e17f2bff35c349632fb9e2c3a`. The downloaded artifact is preconverted, accepted into the official model repository through [upstream change #5](https://huggingface.co/ggerganov/whisper.cpp/discussions/5), merge commit `d148abaa5548d0deea3cf8075f0fd3376e483c8f`.

**Provenance limit:** the historical producer's exact tool build/commit is not published in the reviewed card/history. The pinned reference tools are not asserted to have produced the 2023 artifact. No local conversion or bit-for-bit reproduction was performed. This bounded admission relies on the maintainer-accepted canonical preconverted artifact and verified upstream hashes; it does not grant reproducible production-build status. The file contains hyperparameters, mel filters, vocabulary and tensors; no separate tokenizer/config or executable wrapper was acquired.

| Asset | License evidence | Notice |
|---|---|---|
| Original Whisper weights | [README explicitly covers weights](https://github.com/openai/whisper/blob/86098128c0b4f24f0e2aa2994de830614b474227/README.md#license), [MIT text](https://github.com/openai/whisper/blob/86098128c0b4f24f0e2aa2994de830614b474227/LICENSE) | Copyright (c) 2022 OpenAI |
| Converted artifact | Pinned model-card `license: mit`; no standalone LICENSE file in that repository | Retain original OpenAI attribution/license and upstream conversion provenance |
| Runtime/conversion source | [Pinned MIT text](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/LICENSE) | Copyright (c) 2023-2026 The ggml authors |

The reviewed MIT terms permit internal/commercial use, modification, quantization, copying, redistribution, mirroring and sublicensing, subject to preserving applicable copyright and permission notices. Full license texts, including warranty disclaimers, are retained with the local copies; exact text digests are in the JSON evidence. The reviewed texts impose no source-offer/share-alike duty or separate NOTICE-file requirement. No model-specific gated access, login, click-through, geographic restriction or conflicting evaluation term was found. Public metadata reports `private=false`, `gated=false`, and acquisition succeeded without credentials. DORA redistribution, APK bundling and mirroring are not authorized by this task despite the upstream permissions.

The [OpenAI model card](https://github.com/openai/whisper/blob/86098128c0b4f24f0e2aa2994de830614b474227/model-card.md) describes Internet-derived training data and warns of uneven language/accent performance, hallucination and repetition. This is publisher-reported provenance, not an independent audit of the training corpus. Consent, avoidance of speaker-attribute inference and contextual evaluation remain necessary; MIT does not guarantee rights in future output. No output or model-quality measurement exists in 5.2.

## Runtime source and Android boundary

| Field | Value |
|---|---|
| Project | `ggml-org/whisper.cpp` |
| Version | `v1.9.4` |
| Annotated tag object | `7d75b14994ae7f59623e2471445e2355fe506ed2` |
| Dereferenced commit | `927cfce34f31707e17f2bff35c349632fb9e2c3a` |
| Official release publication | `2026-09-11T05:31:55Z` |
| Code license | MIT |
| Source imported into DORA | No |
| Native prebuilt downloaded / binary built | No / `NOT_BUILT` |
| 16-KiB ELF verification | `DEFERRED_TO_5.3` |

[Official release](https://github.com/ggml-org/whisper.cpp/releases/tag/v1.9.4) and tag dereference agree on the full commit. The annotated tag reports unsigned; no signed-tag claim is made.

The pinned [Android example](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/examples/whisper.android/README.md) exists. Its [library Gradle file](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/examples/whisper.android/lib/build.gradle) declares `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`; minSdk 26, compile/target SDK 34 and NDK `25.2.9519653`. Gradle `externalNativeBuild` invokes [CMake](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/examples/whisper.android/lib/src/main/jni/whisper/CMakeLists.txt), minimum 3.10, C++17, using `src/whisper.cpp`, JNI C source and bundled ggml. Arm64 has a default library and an `armv8.2-a+fp16` variant.

These are prospective source-support facts. Upstream example settings do not replace DORA's JVM 17 / SDK 36 / minSdk 28 baseline. The older example NDK is not 16-KiB evidence. A separately scoped 5.3 must pin the toolchain and validate actual CPU dispatch, native dependencies, ELF/ZIP alignment and runtime behavior. No build, JNI import, device operation or runtime load was performed here.

## Storage, sanity and supply chain

Storage class: `LOCAL_PRIVATE_CONTROLLED_STORAGE`; opaque evidence locator: `controlled:alpha-asr-model-5.2-v0.1`. The task-owned local directory is outside the repository/worktrees, sync/public locations and raw-audio store, with a protected current-user-only ACL. No public share was created. One distinct model was acquired, with two byte-identical local copies for acquisition/post-copy verification. Model bytes are absent from Git, LFS, CI artifacts and attachments.

A bounded host-side binary-data walk verified a regular file, exact bytes/hash, GGML magic `0x67676d6c`, BASE dimensions (six encoder/decoder layers, state 512), multilingual vocabulary capacity 51,865, 80 × 201 mel filters, 50,257 stored vocabulary entries, and all 245 expected tensor names. Tensor types: 146 F32, 2 F16, 97 Q5_1. Tensor payload totals 59,123,072 bytes; records end exactly at EOF. The header `ftype=1009` decodes to quantization version 1 and Q5_1 type 9. Pinned source defines the matching Q5_1 32-element/24-byte layout; its current quantizer emits version 2. This check establishes format sanity, not successful loading by a built runtime. The local checker and its digest are retained in controlled evidence.

Model bytes were treated as untrusted data. No downloaded source was executed, no native prebuilt was introduced, and no executable model wrapper was added. Hash identity does not prove parser safety, absence of vulnerabilities or a signed build chain. Changing any exact digest/variant/quantization/runtime-source pin requires a new admission. If the candidate fails, stop and return evidence; automatic fallback downloads are prohibited.

The Project Owner/custodian must revoke access and remove both model copies when rejected, revoked or evaluation retention ends. Further evaluation needs its own task authority. Production still requires its separate ADR, Legal/Security, dependency/SBOM, ABI, native-runtime and quality gates.

## Verification and status

Exact immutable metadata/tag, license evidence, download size/SHA-256, post-copy hash, upstream SHA-1 and non-inference structure checks passed. The [machine-readable record](../evidence/poc-asr-001/alpha-asr-model-admission-stage0-v0.1.json) contains source references, text digests, timestamps, results and limitations. All 34 host/static checks passed, including JSON/Markdown structure, local links, scope and privacy review. Independent read-only review found no actionable P0/P1/P2 issues. No unresolved P0/P1 blocker remains for this bounded 5.2 admission. CI was not run for this task; no PR is opened or merged.

| Status | Result |
|---|---|
| `5.1` | `PASS_BOUNDED_ALPHA_DATA_SCOPE_ONLY` |
| `5.2` | `PASS / MODEL_ARTIFACT_AND_RUNTIME_SOURCE_PINNED_FOR_STAGE0_EVALUATION` |
| `5.3` | `NOT_STARTED` |
| `5.4` | `NOT_AUTHORIZED` |
| `MODEL_DOWNLOAD` | `PASS_EXACT_ARTIFACT_VERIFIED` |
| `MODEL_QUALITY` | `NOT_EVALUATED` |
| `WER` | `NOT_RUN` |
| `RTF` | `NOT_RUN` |
| `PSS` | `NOT_RUN` |
| `THERMAL` | `NOT_RUN` |
| `ASR_INFERENCE` | `NOT_RUN` |
| `NATIVE_RUNTIME_BINARY` | `NOT_BUILT` |
| `ANDROID_16K_ELF_VERIFICATION` | `DEFERRED_TO_5.3` |
| `DEVICE_RUNTIME` | `NOT_RUN` |
| `PRODUCTION_ADMISSION` | `UNCHANGED` |

No ASR inference, corpus access, WER/RTF/PSS/thermal measurement, Android/Gradle/device test or Recovery work was performed. The 48-clip corpus, data manifests, scoring semantics, Android runtime, CI and Recovery/PR #86 remain unchanged. Task 5.3 has not begun; production admission is not granted.
