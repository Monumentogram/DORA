# DORA Alpha ASR next candidate admission and pre-run package — Stage 0 v0.1

Task **5.6A**, 25 September 2026.

**5.6A = BLOCKED / CONTROLLED_MODEL_STORAGE_UNAVAILABLE_ARTIFACT_BYTES_UNVERIFIED**.

The public provenance/license investigation and prospective decision package are complete.
Artifact admission is incomplete: the existing controlled model-storage boundary could not be
established on this host; the owner reports it is probably on another device. The instruction
permits downloading only into that existing boundary. No replacement store was created and no
model was downloaded. Actual byte size, independently computed SHA-256 and binary identity remain
unverified. Upstream metadata is not a substitute for those checks. No 5.6A PASS is claimed.

## Authority, continuity and exact target

Repository: `Monumentogram/DORA`. Branch: `chat/alpha-asr-runner-scope`.
Required starting HEAD, observed local HEAD and freshly fetched remote HEAD all equal
`3485a5a7d81bbafbca11422a4ad9f29208bc4ba3`. The initial working tree and index were clean.
This is an additive record under the owner's supplied 5.6A instruction; historical 5.2,
5.3A/B/C, 5.4 and 5.5 records remain unchanged. The
[machine-readable evidence](../evidence/poc-asr-001/alpha-asr-next-candidate-admission-stage0-v0.1.json)
binds sources, protected file hashes, checks and pending decisions.

| Field | Exact value / state |
|---|---|
| Candidate | OpenAI Whisper multilingual SMALL, q5_1 |
| Publisher repository | `ggerganov/whisper.cpp` on Hugging Face |
| Immutable revision | `f281eb45af861ab5e5297d23694b7d46e090c02c` |
| Filename | `ggml-small-q5_1.bin` |
| Upstream exact size | `190085487` bytes |
| Expected SHA-256, matching upstream LFS metadata | `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` |
| Observed local size / computed SHA-256 | `null` / `null`; `NOT_DOWNLOADED` |
| Expected versus observed digest | `NOT_EVALUATED`, not MATCH |
| LFS pointer Git blob, not model digest | `87631c52c89d37ed2337a46ec4a009976db1f3f4` |
| Runtime | `ggml-org/whisper.cpp v1.9.4` |
| Runtime commit | `927cfce34f31707e17f2bff35c349632fb9e2c3a` |

Source: [immutable artifact URL](https://huggingface.co/ggerganov/whisper.cpp/resolve/f281eb45af861ab5e5297d23694b7d46e090c02c/ggml-small-q5_1.bin),
[file metadata](https://huggingface.co/api/models/ggerganov/whisper.cpp/revision/f281eb45af861ab5e5297d23694b7d46e090c02c?blobs=true)
and [LFS pointer](https://huggingface.co/ggerganov/whisper.cpp/raw/f281eb45af861ab5e5297d23694b7d46e090c02c/ggml-small-q5_1.bin).
Both independent metadata representations agree on exact size and expected SHA-256.
The API returned the required revision, public and ungated. No alternate weights were acquired.

The selected change keeps Whisper family and q5_1 quantization while increasing BASE to SMALL
capacity. It does not isolate every causal difference and predicts neither quality nor device
fitness. The original `ggml-base-q5_1.bin`, SHA-256
`422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898`, remains
**VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE**. No historical result is reclassified.

## Public provenance and license review

The [pinned converted-model card](https://huggingface.co/ggerganov/whisper.cpp/blob/f281eb45af861ab5e5297d23694b7d46e090c02c/README.md)
attributes the models to OpenAI and declares MIT. Pinned runtime
[models documentation](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/models/README.md)
identifies this canonical repository and explains the OpenAI checkpoint to GGML conversion.
OpenAI's [pinned checkpoint map](https://github.com/openai/whisper/blob/86098128c0b4f24f0e2aa2994de830614b474227/whisper/__init__.py)
identifies multilingual `small.pt` with upstream digest
`9ecf779972d90ba49c06d968637d720dd632c55bbf19d441fb42bf17a411e794`.
That is a publisher reference; the original checkpoint was not downloaded or locally hashed.

Published lineage: OpenAI small checkpoint plus mel/tokenizer assets, conversion through
`models/convert-pt-to-ggml.py`, then whisper.cpp quantization to q5_1, delivered as a canonical
preconverted artifact. [Upstream change #5](https://huggingface.co/ggerganov/whisper.cpp/discussions/5)
records maintainer acceptance of the quantized model collection at merge
`d148abaa5548d0deea3cf8075f0fd3376e483c8f`. The historical producer's exact converter/quantizer
build is not published in the reviewed material. The pinned current reference tools are not
asserted to have produced the historical artifact. No local conversion, reproducible-build,
signature, malware clearance or training-corpus rights audit is claimed.

| Asset | Rights evidence | Required notice |
|---|---|---|
| Original code and weights | [OpenAI README license applicability](https://github.com/openai/whisper/blob/86098128c0b4f24f0e2aa2994de830614b474227/README.md#license) and [MIT license](https://github.com/openai/whisper/blob/86098128c0b4f24f0e2aa2994de830614b474227/LICENSE) | Copyright (c) 2022 OpenAI |
| Converted weights | MIT declaration in the pinned model card; no standalone LICENSE in the metadata inventory | Preserve original MIT notice and conversion attribution |
| Runtime and reference conversion source | [Pinned MIT license](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/LICENSE) | Copyright (c) 2023-2026 The ggml authors |

Reviewed MIT terms permit internal evaluation, commercial use, modification/quantization,
copying, redistribution, mirroring and sublicensing subject to the copyright and permission
notice obligations. Preserve the full applicable license texts including warranty disclaimers
with any authorized model copies. No source-offer/share-alike or separate NOTICE obligation,
model-specific geographic/use restriction or gated click-through was found in these sources.
This is the bounded 5.6A artifact review under the owner instruction, not production Legal/Security
approval, authorization to redistribute or permission for private-data reuse. Public source and
license text snapshots and their hashes are retained locally; no model copies yet exist for 5.6A.
Publisher-reported Internet training provenance and known uneven language accuracy/hallucination
limitations do not establish data consent or third-party rights in future outputs.

## Runtime compatibility and remaining artifact checks

The pinned loader recognizes SMALL from 12 audio layers, multilingual vocabulary from
`n_vocab >= 51865`, and derives quantization type from `ftype % 1000`. Pinned ggml definitions
support Q5_1 (tensor type 7; file quantization type 9), using 32-element/24-byte blocks.
Source inspection found no incompatibility requiring a runtime change. This is a source-level
conclusion only: no native loader, runtime build, audio decoder or inference was invoked.

After the existing store becomes available (or a separate explicit storage authorization exists),
the admission must independently download and hash the complete exact file, then check its
GGML magic/header, multilingual SMALL dimensions, quantization version/type, mel/vocabulary
structure, expected tensor names/shapes/types, bounded payload sizes and exact EOF. The
[5.2 methodology](DORA_ALPHA_ASR_MODEL_ADMISSION_STAGE0_V0_1.md) is the reference, not evidence
that these checks ran for SMALL. Do not infer the actual header from the filename.
Any mismatch fails closed. If compatibility needs a runtime change, return
`BLOCKED / RUNTIME_CHANGE_REQUIRES_SEPARATE_SCOPE`; no upgrade, patch or fallback is authorized.
Device fitness, actual native loading, Android ABI/16-KiB behavior and quality remain untested.

## Prospective held-out package — PROPOSED_NOT_APPROVED

No retained archive, inventory, old clip, hypothesis, reference or private per-case result was
opened. No new subset or selection manifest was materialized. These are prospective instructions
for a separately authorized campaign, not an execution approval.

1. Obtain explicit reuse authority for the exact Common Voice Spontaneous Speech 5.0 RU/EN
   datasets and eligible provider test-split records, with applicable terms still valid. The
   proposed acceptance subset is exactly **24 new RU + 24 new EN**. Availability is unverified.
2. Freeze a successor selection contract before inspecting any candidate output. Preserve the
   existing identity, duration 1000–20000 ms, valid decode and nonempty-reference eligibility.
   Exclude the original 48 by source identity and audio digest, and exclude all known previously
   evaluated/tuning records. Prevent duplicate audio and, where legally available, participant
   overlap with development data using approved pseudonymous metadata, never re-identification.
   Report any participant-disjointness limitation rather than inventing it.
3. Proposed deterministic order: retain the 5.1B key
   `SHA-256(UTF8("dora-alpha-asr-v0.1") || NUL || UTF8(locale) || NUL || UTF8(upstreamRelativePath))`,
   rank only the remaining eligible records by key then exact UTF-8 path bytes, take the first
   24 per locale and preserve RU-then-EN execution order. Freeze exclusion-set, eligible-pool,
   selection-contract and manifest digests privately before inference. No quality-based manual
   substitutions. Insufficient eligible data blocks the campaign. This changed population needs
   a separately scoped successor validator; the existing first-24 validator is not silently reused
   as proof of the new exclusion logic. Fixed language order retains the time/order confounder.
4. Keep the acceptance set untouched by decoder, beam/best_of/temperature, prompt, suppression,
   VAD, language-policy search or candidate selection. The model is already prospectively chosen.
   If tuning becomes necessary, first authorize and freeze a separate development set disjoint
   from acceptance; its size/purpose require a new owner decision. Once acceptance outcomes inform
   a decision, that set is no longer a fresh blind holdout for later candidate selection.
5. The original 48 may only be a later explicitly authorized bounded regression comparison,
   separately labeled and never pooled with fresh acceptance or used to tune/select this candidate.

Preserve the frozen normalizer, Java oracle, RU <=20% and EN <=18% normalized aggregate WER
predicates with exact integer comparisons, complete attempt accounting and no quality retries.
No per-case mean, exclusion of poor results or rounded PASS. A future candidate-specific profile
must bind the SMALL hash without changing historical profiles. Retain four CPU threads, explicit
RU/EN, translate=false, detect_language=false, no_context=true, no prompt, greedy best_of=5,
temperature=0, temperature_inc=0, current suppression/VAD settings and full resolved profile.
Preserve codec/resampling, output concatenation, scoring and measurement definitions. Any future
change must be separately scoped and frozen as a confounder before results are seen.
Timestamp quality remains NOT_EVALUABLE without separately admitted timestamp references.

## Owner decision record — no approval recorded

Decision ID: `ASR-5.6A-DATA-REUSE-RETENTION-PROPOSAL-01`.
State: **PENDING_OWNER_DECISION**. Approver, approval date, new expiry and approved scope: **null**.

| Decision field | Minimum proposal requiring explicit owner approval |
|---|---|
| Purpose | One bounded internal SMALL acceptance campaign; no training, fine-tuning, production use or public redistribution |
| Data scope | Eligible unused records needed to deterministically select 24 RU + 24 EN; selected audio/references; restricted exclusion identities; campaign hypotheses/results and telemetry needed to audit the result |
| Existing copies | Owner must specify whether source archives, inventories, extracted files, original selected copies and derived private 5.3C/5.4/5.5 evidence may be reused/retained; no such access occurred here |
| New copies | One controlled host acceptance subset; only temporary authorized device transfers during the later campaign; private outputs/journal and audit evidence; enumerate actual copies and any backups before execution |
| Access | Project Owner/Data Custodian plus an explicitly authorized bounded test process; no additional people/services, cloud upload, Git/LFS/Actions or public share |
| Expiry | Owner must set absolute expiry and assessment endpoint for each necessary copy class; prefer completing within current deadline; any extension must be explicit before expiry and obey stricter applicable terms |
| Deletion receipts | Opaque copy ID/class, owner, deletion trigger/deadline, deletion time, method, verified absence including device/temp/cache/backup copies, failures and remediation; public receipts contain no private paths or per-case data |
| Regression/development | Separate opt-in scope and split; neither is approved by acceptance approval alone |

Current policy remains **ACTIVE_THROUGH_5_5_THEN_DELETE_WITHIN_30_CALENDAR_DAYS**.
Assessment completed **2026-09-25**; default deletion deadline **2026-10-25**.
No retention extension or follow-on reuse is granted. Without a later owner authorization, the
existing deletion obligation controls; prepare and verify deletion receipts by the deadline.
This task performs no private-data deletion, inventory reopening or retention modification.

## Resource owner decisions — PROPOSED_NOT_APPROVED

Decision ID: `ASR-5.6A-RESOURCE-GATES-PROPOSAL-01`. Approver/date: **null**.
No authoritative approved numeric RTF/PSS/native-heap limit was found in the scoped records.
All acceptance numeric values below remain **null**, to be chosen from device/product requirements
before the next measured comparison. Historical 5.4 measurements are observations only and are
not used to derive limits in this package.

| Metric / rule | Frozen definition or boundary | Exact owner decision still needed |
|---|---|---|
| Inference-only RTF | `inferenceElapsedMicros / decodedAudioDurationMicros`; only `whisper_full`, excluding decode/load/persistence | Target device tier; gated statistic(s): median, p95, maximum and/or duration-weighted total; numeric limit for each and per-language/combined application |
| Cold model load | Monotonic microseconds around `whisper_init_from_file_with_params`, fresh process/context per case | Whether gated; statistic(s) and maximum permitted microseconds |
| End-to-end latency | Separate from inference and model load; no frozen standalone acceptance boundary in current protocol | Explicit start/end events, included transfer/decode/scoring/persistence work, statistic(s), microsecond limit or explicit diagnostic-only decision; instrument only in later scope |
| PSS | Maximum observed `/proc/self/smaps_rollup` Pss KiB ×1024 over frozen process window | Ceiling in exact bytes; per-case/campaign application; not an inferred continuous peak |
| Native allocated heap | Maximum sampled `mallinfo().uordblks` bytes; distinct from PSS/resident memory | Separate exact byte ceiling and application; not a substitute for PSS |
| OOM | Preserve native exit/result/error/incomplete evidence; generic exit or missing output alone does not establish OOM | Approve zero evidenced OOM for acceptance, attribution evidence and treatment of ambiguous exits; ambiguity must not become PASS |
| Thermal invalidation | Existing status >=3/SEVERE stops campaign, retains operational failure; no temperature override/threshold | Ratify stop/invalidation and controlled restart policy for next campaign; no acceptance from thermally compromised attempts |
| Telemetry completeness | Memory every 100 ms, max gap 1000 ms; thermal delay 1000 ms, max gap 3000 ms; frozen window before decode through after free | Ratify required streams and complete coverage; missing required telemetry blocks comparison; explicitly scope any added end-to-end/OOM evidence |

The frozen 600-second timeout and 605-second native alarm are operational safety controls, not
approved performance budgets. No result-dependent gate choice, silent relaxation, or unrecorded
retry is allowed. Missing numeric approvals prevent device-fitness acceptance even if quality
later passes. All proposals require an explicit dated owner record before measured execution.

## Verification, publication and stop boundary

Repository validation is restricted to documentation/evidence, strict JSON, Markdown links/text,
privacy/scope review, `git diff --check` and protected-file byte equality. The evidence records
the actual results. The 45 protected public files include historical ASR reports/evidence,
normalizer, Java oracle, runners/native source/tests and production native allowlist.
Git additionally verifies no changes outside the four authorized files. Status/backlog additions
preserve their prior bytes. No ASR tests, Gradle/device tests or Recovery checks are necessary
for this docs-only blocked admission record; no CI success is claimed.

Private-data file access **0**; new audio decode **0**; ASR inference **0**; device execution **0**.
Storage discovery inspected directory/file names only, including an initial overly broad workspace
filename listing; no retained private file contents or inventories were opened or used.
Public/model-source metadata is kept separate from private evaluation data.

Publication authority: exactly one atomic commit and push to the existing task branch, no PR,
no merge, no PR #86 action. At evidence freeze, commit/push are pending; final commit and fetched
remote identities are reported in the handoff because a commit cannot contain its own hash.
The blocker is unavailable authorized model storage and consequently absent byte/hash/header
verification. POC-ASR-001 remains BLOCKED / NOT_READY; production admission is false.
**5.6B and any new campaign remain NOT_STARTED / NOT_AUTHORIZED. Stop after commit/push.**
