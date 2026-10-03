# ADR-VAD-001 — Isolated sherpa VAD-only artifact candidate

Status: PENDING_FINAL_PUBLICATION. This is an admission candidate decision,
not production Stage 8.4 implementation or a claim that external gates passed.

The official generic sherpa-onnx 1.13.8 AAR remains rejected by the immutable
[historical admission](../evidence/vad-8.4-admission/README.md). Its packaged
TTS/eSpeak/piper graph is not authorized for DORA distribution.

Build a separate arm64-v8a candidate from pristine upstream commit
`11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf`. Use upstream TTS and diarization
OFF options. Upstream has no independent ASR/KWS build switches. An external
CMake target compiles the unchanged upstream VAD and version JNI translation
units and links the upstream static core with section garbage collection.
The full generic JNI/C/C++ APIs are not built or packaged. C_API remains ON
only at configuration time to prevent upstream from forcibly enabling its
generic Android JNI target; the recipe builds only `dora-vad-jni`.

Upstream's shared VAD factory retains its TEN adapter and supporting numeric
code. This is inventoried rather than hidden: only the separately pinned
Silero VAD 6.2.1 model is tested/selected. No TEN model or provider substitution
is authorized. Native code is not manually removed or edited after linking.
Ordinary removal of debug symbols does not remove feature/license obligations.

The isolated AAR includes only the exact upstream Java VAD/version/loading
classes and two arm64 native libraries. It does not add a production module,
Gradle dependency, microphone pipeline, segmentation state machine or profile.
The disposable harness has no microphone permission and accepts synthetic
file fixtures only. Its result proves native runtime admission behavior, not
acoustic, capture, timing, durability or Stage 8.4 product acceptance.

The runtime and model remain distinct supply-chain objects. The model is
2,327,524 bytes, SHA-256
`1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3`, MIT.
The runtime's inherited MPL-covered components require their exact source
availability and notices; removal of GPL TTS does not mean every component
uses the same license as sherpa's repository root.

Binary custody is a separate gate. The existing SQLCipher mechanism is a
public repository Maven artifact. This task does not automatically publish
the new binary there. A machine-local AAR is not reproducible shared custody.
The [successor evidence](../evidence/vad-8.4-remediation/README.md) must report
the actual custody disposition and every uncompleted gate explicitly.

Stage 8.4 remains planned. Production VAD integration, 8.4C and 8.5 remain
NOT_STARTED. Stage 8 remains IN_PROGRESS. PERF-REC-001 stays deferred and the
development security restoration blocker stays OPEN. Existing no-PIN
development behavior is unchanged. No Sheet write or parent-history rewrite.
