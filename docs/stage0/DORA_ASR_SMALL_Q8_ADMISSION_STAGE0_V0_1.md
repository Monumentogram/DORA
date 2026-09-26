# DORA SMALL q8 artifact, init and operator source — Stage 0 v0.1

Task **5.6D.2**, 27 September 2026. **PASS / SMALL_Q8_ARTIFACT_INIT_OPERATOR_READY**.
Published data commit `15563e9fbcee7821bc9ff3f10520d4168b19b229`, branch `chat/alpha-asr-runner-scope`.
This phase admits exact artifact/static/init evidence and prepares the additive operator source.
The immutable execution envelope will be sealed **after the operator commit** and pinned in5.6D.3;
no measured campaign has been invoked. This ordering avoids a commit/self-hash cycle.

## Artifact and provenance

One download of `ggml-small-q8_0.bin` from `ggerganov/whisper.cpp` revision `5359861c739e955e79d9a303bcbc70fb988958b1`:
264464607bytes, SHA256`49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f`. Catalog LFS identity and independently hashed bytes match;
one controlled host copy remains, no staging copy. The bounded static walk confirms multilingual SMALL,
ftype2007,479tensors:284F32+2F16+193Q8_0, exact names/shapes/types and EOF.
Static parsing performs no native loading or ASR. Exact historical conversion build and bitwise reproduction are not claimed.

Primary sources: [artifact](https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-small-q8_0.bin), [pinned model card](https://huggingface.co/ggerganov/whisper.cpp/raw/5359861c739e955e79d9a303bcbc70fb988958b1/README.md),
[catalog](https://huggingface.co/api/models/ggerganov/whisper.cpp/tree/5359861c739e955e79d9a303bcbc70fb988958b1), [OpenAI license](https://raw.githubusercontent.com/openai/whisper/86098128c0b4f24f0e2aa2994de830614b474227/LICENSE),
[runtime license](https://raw.githubusercontent.com/ggml-org/whisper.cpp/927cfce34f31707e17f2bff35c349632fb9e2c3a/LICENSE), [weights license applicability](https://raw.githubusercontent.com/openai/whisper/86098128c0b4f24f0e2aa2994de830614b474227/README.md).
Original weights, converted-model card declaration and runtime are MIT. Complete applicable notices are retained
with the controlled copy. This project authorizes bounded internal evaluation, not production admission or redistribution.
Owner-only storage, no Git/LFS/sync copies, deletion deadline2026-10-25; no retention extension.

## Native and init-only evidence

Exact accepted ARM82 binaries were reused: whisper.cpp v1.9.4 `927cfce34f31707e17f2bff35c349632fb9e2c3a`, no new native build.
ELF segment alignment, needed libraries and APK16KiB zip alignment pass. The physical POCO M5 runs Android14/API34,
arm64-v8a with actual4096-byte pages. **An actual16KiB-device runtime test was not performed.**
One init attempt returned SUCCESS; one separate no-model hold/cancellation probe proves the owned process gone.
Cleanup VERIFIED. Init diagnostic wall time875.0ms is not the campaign cold-load gate.
Corpus/reference transfers0, whisper_full calls0, ASR inference0. Full native stdout/stderr and private locators remain outside Git.

## Operator source and execution boundary

The [additive binding](../../tools/asr_small_q8_v01/alpha_asr_small_q8_binding.py) and
[contract README](../../tools/asr_small_q8_v01/README.md) require independently supplied operator-commit and envelope digests.
Only candidate/profile/data identity changes; historical normalization, Java oracle, quality/resource gates,
CPU4/flash-attention decoding, generated warmup, per-case lifecycle and stop rules remain inherited.
Closed source/private-document sets, model/native/profile/storage checks and immutable consumed RU24+EN24
are verified before the claim, warmup, each primary case and after the sequence.
One exclusive durable campaign claim, zero retries/replacements/resume; a changed work locator cannot bypass the claim.
Historical evaluator modules are loaded in isolation and remain unmodified.

98 targeted host tests PASS, including15 new binding tests;
Stage00 checks7/7 and Gradle197tasks PASS. These host checks and init evidence do not establish quality or performance.
Source/test pins and admission evidence are in [sanitized JSON](../evidence/poc-asr-001/asr-small-q8-admission-stage0-v0.1.json).
Public evidence contains no participant identifiers, transcripts, sample paths, model locator or device serial.

## Remaining execution and claim limits

The operator source is READY; an actual execution envelope is **not yet sealed**.
After this seven-file operator commit, bind its exact clean HEAD, the published data parent, the source files,
consumed data, q8 model, native build, profile, codec sample and private work boundary into one immutable envelope.
Publish its whole-file/canonical pins with phase5.6D.3 after the one already-authorized campaign.
Stage5 remains NOT_PASS and POC-ASR-001 remains BLOCKED / NOT_READY pending measured gates and terminal closeout.
Historical BASE/SMALL/ARM82 results, Recovery0D.6 and PR#86 remain unchanged.
