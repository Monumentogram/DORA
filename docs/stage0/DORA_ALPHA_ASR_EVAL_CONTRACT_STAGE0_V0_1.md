# DORA Alpha ASR evaluation contract — Stage 0 v0.1

Task: **5.3A — VERSIONED ASR NORMALIZATION / SCORING / RESULT CONTRACT**.
Date: **2026-09-24**. Authority: Project Owner's explicit 5.3A execution instruction.
Predecessor: 8487eb847f9e5e6116ea9ae1503068037fdbd580.
Branch: chat/alpha-asr-runner-scope. Publication: one atomic commit/push; **no PR or merge**.

This host input/result contract closes VERSIONED_TOKENIZATION_AND_NORMALIZATION_CONTRACT_NOT_APPROVED
only for the bounded 5.1 RU/EN Alpha pilot. It does not close full POC-ASR-001 readiness.

## 1. Authority, scope and execution plan

The [technical plan](../DORA_MVP1_TECHNICAL_PLAN.md), [approved gates](DORA_MVP1_POC_GATES.md),
[readiness review](../DORA_MVP1_IMPLEMENTATION_READINESS.md),
[5.1 data scope](DORA_ALPHA_ASR_DATA_SCOPE_STAGE0_V0_1.md),
[5.2 model admission](DORA_ALPHA_ASR_MODEL_ADMISSION_STAGE0_V0_1.md) and immutable
[I1 evidence](../evidence/poc-asr-001/i1-synthetic-scoring-oracle-local-evidence-stage0-v0.1.json)
remain authoritative in their scopes. [ADR-0008](../adr/ADR-0008-alpha-asr-evaluation-contract.md)
records this technical choice; no product behavior or production admission changes.

The owner's detailed task is the execution plan: verify exact predecessor/remote and preserve
user files; add synthetic tests before implementation; implement stdlib normalization and closed
profile/result validation; check pinned-source parity and unchanged Java mechanics; document
claim/privacy boundaries; validate scope and publish only on PASS. No native build, real-corpus
access, inference, Android/Gradle, device, POCO or Recovery operation belongs to this plan.

Implementation decisions:

- Preserve upstream regex behavior and edge spaces, without recursive bracket parsing or trim.
- Pin CPython 3.12 / Unicode 15.0.0 to prevent silent Unicode-table drift.
- Bind oracle hash to LF Git blob bytes; Windows CRLF conversion is not a source change.
- Validate supplied S/D/I consistency, never recalculate alignment in Python.
- The CLI prints only the frozen profile and rejects all arguments without echoing them.
  Private text enters the library API in memory; no JSON-input/file-input CLI is provided.
- The closed typed result schema below is enforced by validate_result without dependencies.

## 2. Versions and exact identities

| Field | Value |
|---|---|
| Private result contract | dora-alpha-asr-eval-result-v0.1 |
| Evaluation profile | dora-alpha-asr-eval-profile-v0.1 |
| Normalization | dora-alpha-asr-normalization-v0.1 |
| Python semantic profile | cpython-3.12-unicode-15.0.0 |
| Selected-manifest SHA-256 | 5d9e12fa76e9db54bc69bbd22396dbec733348db97bba353361ac701af64061c |
| Model | ggml-base-q5_1.bin |
| Model SHA-256 | 422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898 |
| Runtime source | ggml-org/whisper.cpp v1.9.4 |
| Runtime commit | 927cfce34f31707e17f2bff35c349632fb9e2c3a |
| Oracle | com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle |
| Oracle schema | 1 |
| Oracle SHA-256 (Git blob, LF) | a2b3e37305f24f91a8ace00f5f824d1d13effda94248c15b31aa31db78c76125 |

profile() in [the implementation](../../tools/alpha_asr_eval_text_contract.py) is the normative
machine-readable expansion, including gates/options. Every private result embeds that entire
closed profile. Unknown, missing, mistyped or changed fields at any depth fail closed; booleans
cannot substitute for integers. Interpreter patch version is recorded in evidence.
A different Python minor, Unicode database, model, corpus, source or contract profile requires
prospective review/versioning. This host interpreter pin does not select a backend toolchain.

## 3. Exact normalization and tokenization

Semantic reference: official [OpenAI Whisper basic.py](https://github.com/openai/whisper/blob/86098128c0b4f24f0e2aa2994de830614b474227/whisper/normalizers/basic.py),
revision 86098128c0b4f24f0e2aa2994de830614b474227.
Original source bytes: 2064; SHA-256:
4742eaa040e0657fa1247a1361e0d856c62317a43326ea59a40c2e9edd8d2c38.

Use BasicTextNormalizer(remove_diacritics=False, split_letters=False) semantics.
EnglishTextNormalizer is excluded. RU and EN share one language-neutral policy.

For each exact raw reference or hypothesis:

1. Python Unicode str.lower().
2. Remove bracket content with exact regex shown below.
3. Remove parenthetical content with exact regex shown below.
4. Unicode NFKC.
5. Replace each character whose Unicode category begins with M, S or P by one space.
6. Unicode lowercase again.
7. Collapse Unicode whitespace using the final regex below.
8. Normalized words are the resulting string's Python whitespace .split().

~~~python
r"[<\[][^>\]]*[>\]]"   # replacement: ""
r"\(([^)]+?)\)"       # replacement: ""
r"\s+"               # replacement: " "
~~~

Bracket removal inserts no separator, so adjacent text may join. Upstream permits mixed bracket
closers. Parentheses use the nonrecursive upstream regex through the first closing parenthesis;
no recursive removal is performed. Empty parentheses survive that regex and then become spaces
as punctuation. Compatibility brackets created by NFKC are not processed again.
Edge whitespace remains one space until tokenization. Category C characters are not removed.

Composed Latin diacritics survive when NFKC leaves letters. Combining marks remaining after NFKC
become spaces. No extra accent stripping, punctuation exceptions, number-to-word conversion,
spelling correction, stemming, transliteration, script conversion or language dictionary is added.

Raw diagnostic tokens are exactly rawText.split(), with no other processing.
**Raw WER is diagnostic; normalized WER is the language quality metric.**

## 4. Text authority and existing oracle handoff

Reference text is the exact controlled transcription bound to the frozen selected manifest.
A later runner must verify membership, locale and reference-text hash before prepare_case.
This host tool does not establish membership/authenticity or transcript-to-audio matching.

Hypothesis is the exact raw text emitted later by the pinned runner. No manual punctuation/spelling
repair, hallucination deletion or best-of-multiple-run selection is permitted.
Text hashes are SHA-256 of exact UTF-8 text, without BOM, trim, newline conversion or normalization.

Private input contains exactly profile, caseId, locale, referenceText, hypothesisText.
Case IDs follow the existing sample-[0-9a-f]{16} opaque convention; locale is exactly ru or en.
Null/non-string text and unpaired Unicode surrogates fail with content-free codes.
A zero-token normalized reference fails that case as INVALID_EMPTY_NORMALIZED_REFERENCE,
including non-empty bracket/punctuation-only source text. Retain the case and failure; do not
exclude it or call it zero WER. An empty hypothesis is valid and naturally scores as deletions.

prepare_case returns four private token arrays, both raw-text hashes, case ID, locale and empty
timestampAnchors. It neither executes Java nor computes WER. The existing
[Java oracle](../../tools/asr_i1_synthetic_scoring_oracle/src/main/java/com/monumentogram/dora/stage0/asr/i1/AsrSyntheticScoringOracle.java)
retains Levenshtein, stable ties, S/D/I, exact rational WER aggregation, rational timestamp median
and nearest-rank p95 unchanged.

A future adapter maps ru to RU and en to EN and passes all four arrays unchanged. Singleton
requests expose per-case counts through score.overall; sum those for each language.
The existing oracle requires an acoustic enum and has no UNKNOWN. For this unstratified pilot,
QUIET may fill that mechanical slot, **without claiming that the clip is quiet**.
Ignore and suppress all byAcoustic output. Protected acoustic claims need separately admitted
labels and scope; a mechanical bucket is never ground truth.

Host safety limits reject rather than truncate: 131,072 raw code points, existing oracle bounds
of 4,096 tokens per sequence / UTF-16 units per token and 25,000,000 alignment cells per request.
The singleton handoff checks both streams' combined cells. Future batches additionally enforce
the whole-request oracle bounds and duplicate-ID checks.

## 5. WER gates and completeness

Gate set stage0-v0.1 is unchanged: **RU <=20%; EN <=18% normalized aggregate WER**.
Use sum(S+D+I) / sum(reference tokens), never a mean of per-case percentages.
Inclusive integer cross multiplication avoids rounding. WER may exceed 100%; never clamp it.

language_gate(locale, errors, reference_tokens) compares already aggregated counts only.
Its PASS names a numerical language predicate, not a campaign verdict. Before quality claims,
a future campaign must prove exact membership and complete coverage of 48 cases (24 per locale),
account for every attempt/failure and bind the predeclared attempt policy. Missing/invalid/failed
cases prevent complete bounded-pilot PASS; partial aggregates are observations.
No choosing a better rerun; subsequent authorized campaigns retain earlier results.

Mixed RU/EN, noisy and speakerphone gates are NOT_CLAIMED in this pilot.
Overall averages cannot hide a failing language predicate. No full POC-ASR PASS follows.

## 6. Timestamp and performance boundary

- TIMESTAMP_REFERENCE_STATE = NOT_AVAILABLE_IN_5_1_CORPUS.
- TIMESTAMP_QUALITY = NOT_EVALUABLE.
- Both timestamp median and p95 gates = NOT_EVALUATED.

The pilot supplies text/duration, not adjudicated word-level timestamps. Pass no anchors;
null statistics are not zero error. Do not synthesize reference truth from model timestamps,
duration, forced alignment or transcript position. Future separately admitted anchors may reuse
the oracle; this contract can never yield timestamp PASS.

RTF_THRESHOLD_STATE and MEMORY_THRESHOLD_STATE remain PROPOSED_NOT_APPROVED.
No numeric RTF/PSS/native-heap threshold is invented. A prospectively approved version is needed
before the campaign judged by it; do not retrospectively approve observed results.

Reserved RTF is inference elapsed microseconds / positive audio microseconds; model load time is
separate. Timing is monotonic. The later runner must freeze device tier, measurement source,
warm-up and repetition policy. Memory records peak PSS/native heap in bytes. Thermal records
Android status observations; severe thermal/OOM remain governed by existing gates.
None of those measurements is simulated or made here.

## 7. Closed private result schema

result_shell(case_id, locale) produces the exact JSON-compatible shape; validate_result checks it.
All fields below are required. No unknown fields or free-text errors are allowed.
Integer means a non-boolean integer in [0, 2^63-1] unless further constrained.

| Field | Type / invariant |
|---|---|
| profile | Exact full profile() record with all identity and gate pins |
| caseId, locale | Opaque sample pattern; ru / en |
| attemptOrdinal | Zero iff NOT_RUN; positive otherwise; append-only within future campaign |
| executionAttemptState | NOT_RUN / SUCCEEDED / FAILED / INVALID_INPUT |
| errorCategory | Null for NOT_RUN/SUCCEEDED; closed code otherwise |
| rawReferenceTextSha256, rawHypothesisTextSha256 | Lowercase 64-hex or null; both required for SUCCEEDED |
| rawTokenCounts, normalizedTokenCounts | Null unless SUCCEEDED; then exactly {reference, hypothesis}; reference 1..4096, hypothesis 0..4096 |
| oracleCounts | Null unless SUCCEEDED; then {raw, normalized}, each exactly {substitutions, deletions, insertions}, integers supplied by pinned oracle |
| normalizedWerContribution | Null unless SUCCEEDED; then {errors, referenceTokens}; equals normalized S+D+I and reference count |
| timing | {state, audioDurationMicros, inferenceElapsedMicros, modelLoadElapsedMicros} |
| timestampReferenceState | Fixed NOT_AVAILABLE_IN_5_1_CORPUS |
| timestampQuality | Fixed NOT_EVALUABLE |
| timestampMedianGate, timestampP95Gate | Fixed NOT_EVALUATED |
| rtf | {state, elapsedMicros, audioMicros, thresholdState} |
| memory | {state, peakPssBytes, peakNativeHeapBytes, thresholdState} |
| thermal | {state, initialStatus, maximumStatus} |

Observation state: NOT_MEASURED / UNAVAILABLE / OBSERVED. Nonobserved values are null.
OBSERVED needs at least one non-null value; numeric values are nonnegative integers.
RTF needs both integers, positive audio duration and exact matching observed timing values.
Thermal needs both known statuses and maximum severity >= initial. Allowed statuses:
NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN.
Performance thresholdState is always PROPOSED_NOT_APPROVED, never PASS.

S/D/I structural checks: S+D <= Nref; S+I <= Nhyp; Nref-D+I = Nhyp.
These are necessary consistency checks, not scoring or proof that the oracle was run.
SUCCEEDED means execution/scoring completed, not quality PASS.

FAILED codes: RUNNER_FAILURE, RUNNER_TIMEOUT, ORACLE_REJECTED.
INVALID_INPUT codes: INVALID_EMPTY_NORMALIZED_REFERENCE, INPUT_BINDING_MISMATCH, INVALID_TEXT.
Failed/invalid rows retain attempts and available hashes; all scoring fields are null.
Raw exception text, paths or model diagnostics cannot enter this record.
NOT_RUN has ordinal zero and untouched null-valued measurement shells.

## 8. Private/public evidence boundary

Actual references, hypotheses, tokens, per-case hashes/IDs/results, selected upstream paths,
actual selected manifest, audio and private locators remain controlled/private. The result
schema has no raw-text or locator fields. Hashes do not make per-case publication safe.

Public evidence is a separate allowlist projection, never a private record with a few fields
deleted. Allowed: versions/profiles, normalizer revision/hash, oracle identity/hash, aggregate
selected-manifest digest, model/runtime pins, counts, later RU/EN aggregate S/D/I/WER, result-state
counts, timestamp NOT_EVALUABLE and limitations. Actual per-case rows, text, paths, audio and
private locators are prohibited. CLI profile output is public-safe; library returns are private.
Future aggregate publication needs separately scoped completeness/privacy review.

## 9. Validation and parity reproduction

Implementation/tests use Python standard library only. The synthetic Java handoff test needs
JDK 17; no Gradle, Android or native build. From the repository root:

~~~text
python -B -m unittest discover -s tools -p test_alpha_asr_eval_text_contract.py -v
python -B tools/alpha_asr_eval_text_contract.py
python -B tools/verify_alpha_asr_normalizer_parity.py <official-pinned-basic.py>
git diff --check
~~~

The verifier reads only the supplied official source, checks SHA-256 before parsing/execution,
and omits only the unused import regex. All normalizer function/class AST nodes remain intact.
Both options are False and the third-party branch is never called. No Whisper/tiktoken/regex
dependency is installed or used. Bounded parity includes 26 literal known answers, 4,096
deterministic scalar cases and 576 combinations: **4,698 fixtures** for strings and tokens.
Any source/semantic mismatch returns BLOCKED_NORMALIZATION_PARITY without fallback.

[Sanitized evidence](../evidence/poc-asr-001/alpha-asr-eval-contract-local-evidence-stage0-v0.1.json)
records tests, deterministic digests, environment, source pins and privacy/scope limitations.
The existing Java source/tests remain unchanged; the synthetic bridge exercises case/punctuation,
insertions, substitutions and empty-hypothesis deletions. No actual transcript/model/manifest access.

## 10. Claim ceiling

On passing host checks: **5.3A = PASS / VERSIONED_ASR_EVALUATION_CONTRACT_READY**.
Only normalization/tokenization, private result schema and future oracle inputs are frozen.

- 5.1 = PASS_BOUNDED_ALPHA_DATA_SCOPE_ONLY.
- 5.2 = PASS / MODEL_ARTIFACT_AND_RUNTIME_SOURCE_PINNED_FOR_STAGE0_EVALUATION.
- 5.3B = NOT_STARTED; 5.3C = NOT_STARTED; overall 5.3 is not complete.
- 5.4 = NOT_AUTHORIZED; ASR_INFERENCE = NOT_RUN; MODEL_QUALITY = NOT_EVALUATED.
- Real WER, timestamp quality, RTF, PSS/native heap, thermal, POCO and device support remain
  unevaluated. Native runtime is not built/loaded.
- Full POC-ASR remains BLOCKED / NOT_READY / NOT_RUN. Native/ABI/16-KiB, runner verification,
  campaign and production admission remain separate.
- No bounded 5.3A P0/P1 blocker remains after recorded checks. Missing timestamp truth and
  performance approval remain future owner-controlled prerequisites.

## Upstream attribution

OpenAI Whisper, MIT, Copyright (c) 2022 OpenAI.
The repo-owned semantic equivalent preserves the permission notice from the
[pinned license](https://github.com/openai/whisper/blob/86098128c0b4f24f0e2aa2994de830614b474227/LICENSE):

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
