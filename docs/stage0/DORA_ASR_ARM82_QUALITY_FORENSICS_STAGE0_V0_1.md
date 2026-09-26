# ASR ARM82 RU quality forensics and untouched-holdout audit

**BLOCKED / INSUFFICIENT_UNTOUCHED_HOLDOUT**. The currently admitted SPS5.0 dev authority
leaves **RU0 / EN137**, below the required RU24+EN24. No new holdout was selected and no
successor experiment was frozen. No ASR inference, device execution or historical rerun occurred.
This report starts from accepted commit `9a17523062a7ffe11ba40c8e267f18513d8dcb65`; the
[accepted measured result](DORA_ASR_SMALL_ARM82_01_MEASURED_STAGE0_V0_1.md) remains immutable.
Machine-readable findings, exact preserved runtime/build/gate identities and model catalog pins:
[forensic evidence](../evidence/poc-asr-001/asr-arm82-quality-forensics-stage0-v0.1.json).

## Accepted result reconciliation

Read-only journal/manifest/prepared-stream checks confirm RU24/24, **73/349 = 20.916905%**.
The unchanged gate is errors/reference tokens <=20/100. Therefore floor(349*20/100)=**69**
is the maximum passing integer error count: 69/349=19.770774%, whereas 70/349=20.057307%.
The result exceeds that count by **4 aggregate errors**. Those four are an arithmetic margin,
not four specially identified errors or permission to remove cases.
EN3/24, with retained partial 1/13 errors/tokens, remains **NOT_EVALUABLE**; aggregate resources
remain **NOT_EVALUABLE**. Completed RU failure makes overall quality FAIL and retains
**VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE**, regardless of unfinished EN.
The campaign remains **INCOMPLETE / BOUNDED_SMALL_POCO_CAMPAIGN_STOPPED**.
All 28 journal rows are reconciled by manifest identity; journal ordinal1 is the per-case
primary-attempt ordinal, not campaign order. RU01–24 below follow the frozen manifest order.

## RU24 diagnostics — POSTMORTEM_DIAGNOSTIC_ONLY

The identifying private table contains the requested sample IDs and pseudonymous participant
hashes; its whole-file digest is retained in JSON. Public P1–P4 are report-local labels assigned
at first occurrence, not source participant hashes. No reference or hypothesis text is published.
Duration is retained decoded-frame duration. Errors and reference counts are original journal
values; prepared reference/hypothesis token counts were verified using the unchanged normalizer.
No WER alignment or oracle was rerun and no historical case was repaired/rescored.

**S/D/I limitation:** the journal retained total edit errors, not separate edit operations.
The raw reference/hypothesis token streams alone would require alignment to reconstruct them.
The owner forbids rescoring; clarification permitting frozen-oracle reconstruction has not
been received. Hence S/D/I ranges below are only integer bounds from E=S+D+I and N−H=D−I,
with nonnegative counts and matched-token bounds. A single value is uniquely implied by
those counts (12 cases); a range is **not an observed alignment count**. Exact aggregate
composition is unavailable. Bounds: S10–52, D19–40, I2–23, jointly constrained to E73 and D−I17.
These cannot establish deletion/substitution dominance. Unavailable exact fields are null in JSON.

| Case | Person | Seconds | Ref N | Hyp H | S bound | D bound | I bound | Errors E | Case WER | Error contribution | Token contribution |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| RU01 | P1 | 16.776 | 37 | 35 | 1–7 | 2–5 | 0–3 | 9 | 24.32% | 9/73 (12.33%) | 37/349 (10.60%) |
| RU02 | P1 | 8.496 | 10 | 10 | 0–2 | 0–1 | 0–1 | 2 | 20.00% | 2/73 (2.74%) | 10/349 (2.87%) |
| RU03 | P1 | 19.620 | 41 | 39 | 1–5 | 2–4 | 0–2 | 7 | 17.07% | 7/73 (9.59%) | 41/349 (11.75%) |
| RU04 | P1 | 7.236 | 14 | 12 | 1–5 | 2–4 | 0–2 | 7 | 50.00% | 7/73 (9.59%) | 14/349 (4.01%) |
| RU05 | P1 | 18.756 | 37 | 37 | 0–6 | 0–3 | 0–3 | 6 | 16.22% | 6/73 (8.22%) | 37/349 (10.60%) |
| RU06 | P2 | 5.040 | 4 | 4 | 0 | 0 | 0 | 0 | 0.00% | 0/73 (0.00%) | 4/349 (1.15%) |
| RU07 | P1 | 5.040 | 4 | 4 | 1 | 0 | 0 | 1 | 25.00% | 1/73 (1.37%) | 4/349 (1.15%) |
| RU08 | P1 | 11.988 | 16 | 18 | 1–3 | 0–1 | 2–3 | 5 | 31.25% | 5/73 (6.85%) | 16/349 (4.58%) |
| RU09 | P1 | 8.388 | 7 | 6 | 1–3 | 1–2 | 0–1 | 4 | 57.14% | 4/73 (5.48%) | 7/349 (2.01%) |
| RU10 | P3 | 10.944 | 11 | 11 | 0–2 | 0–1 | 0–1 | 2 | 18.18% | 2/73 (2.74%) | 11/349 (3.15%) |
| RU11 | P2 | 3.096 | 4 | 4 | 0 | 0 | 0 | 0 | 0.00% | 0/73 (0.00%) | 4/349 (1.15%) |
| RU12 | P1 | 13.680 | 15 | 14 | 0 | 1 | 0 | 1 | 6.67% | 1/73 (1.37%) | 15/349 (4.30%) |
| RU13 | P3 | 10.548 | 15 | 15 | 0–2 | 0–1 | 0–1 | 2 | 13.33% | 2/73 (2.74%) | 15/349 (4.30%) |
| RU14 | P2 | 8.496 | 14 | 14 | 0 | 0 | 0 | 0 | 0.00% | 0/73 (0.00%) | 14/349 (4.01%) |
| RU15 | P1 | 15.480 | 22 | 17 | 0–6 | 5–8 | 0–3 | 11 | 50.00% | 11/73 (15.07%) | 22/349 (6.30%) |
| RU16 | P2 | 15.228 | 17 | 17 | 1 | 0 | 0 | 1 | 5.88% | 1/73 (1.37%) | 17/349 (4.87%) |
| RU17 | P1 | 11.196 | 17 | 16 | 1 | 1 | 0 | 2 | 11.76% | 2/73 (2.74%) | 17/349 (4.87%) |
| RU18 | P2 | 5.796 | 8 | 8 | 0 | 0 | 0 | 0 | 0.00% | 0/73 (0.00%) | 8/349 (2.29%) |
| RU19 | P2 | 6.120 | 9 | 9 | 0 | 0 | 0 | 0 | 0.00% | 0/73 (0.00%) | 9/349 (2.58%) |
| RU20 | P1 | 10.440 | 12 | 11 | 1 | 1 | 0 | 2 | 16.67% | 2/73 (2.74%) | 12/349 (3.44%) |
| RU21 | P3 | 7.524 | 7 | 5 | 0 | 2 | 0 | 2 | 28.57% | 2/73 (2.74%) | 7/349 (2.01%) |
| RU22 | P3 | 5.472 | 3 | 3 | 1 | 0 | 0 | 1 | 33.33% | 1/73 (1.37%) | 3/349 (0.86%) |
| RU23 | P4 | 6.804 | 10 | 10 | 0–2 | 0–1 | 0–1 | 2 | 20.00% | 2/73 (2.74%) | 10/349 (2.87%) |
| RU24 | P1 | 9.360 | 15 | 13 | 0–4 | 2–4 | 0–2 | 6 | 40.00% | 6/73 (8.22%) | 15/349 (4.30%) |

| Person | Cases | Errors | Reference tokens | Aggregate WER | Share of errors | Share of tokens |
|---|---:|---:|---:|---:|---:|---:|
| P1 | 13 | 63 | 247 | 25.506% | 86.301% | 70.774% |
| P2 | 6 | 1 | 56 | 1.786% | 1.370% | 16.046% |
| P3 | 4 | 7 | 36 | 19.444% | 9.589% | 10.315% |
| P4 | 1 | 2 | 10 | 20.000% | 2.740% | 2.865% |

Observed error histogram (errors: number of cases): 0:5, 1:4, 2:7, 4:1, 5:1, 6:2, 7:2, 9:1, 11:1.
There are **five zero-error cases** and errors in **19/24 cases**. Highest errors: RU15=11,
RU01=9, RU03=7, RU04=7, RU05=6 (RU24 also has6; ties use earlier ordinal).
Top1/3/5 contribute **11/73=15.068%**, **27/73=36.986%**, **40/73=54.795%**.
All five belong to P1. P1 contributes **63/73=86.301%** of errors and **247/349=70.774%**
of tokens across13 cases. Thus errors occur broadly across cases but are **materially concentrated
in one participant and several cases**, not one isolated bad sample. The four-error miss cannot
be assigned a separate causal location. No participant was excluded or rescored.

For duration diagnostics only, split at the observed median8.928s: the12 shorter/equal cases
have19/94=20.213% aggregate WER, the12 longer cases54/255=21.176%. Pearson duration/error-count
correlation is0.6400; duration/case-WER correlation is0.1034. Longer utterances contribute more
tokens/errors, with little observed difference in aggregate WER. This small participant-clustered
sample does not prove an independent duration effect. It cannot establish broad-population RU quality.

**Observation versus hypothesis:** concentration and the integer margin are retained evidence.
Participant/acoustic/linguistic factors and quantization sensitivity are hypotheses, not established
causes. No transcript meaning was used for eligibility, and no causal error-type claim is made.

## EN04 stop and prospective correction

The raw reference is nonempty and has2 whitespace tokens, entirely inside square brackets.
It passes the historical `bool(raw.strip())` eligibility rule. Tracing the exact frozen
`normalize` function shows19 characters before its first bracket-removal expression and0
immediately after: `[<\[][^>\]]*[>\]]` removes the entire bracketed span. Later parenthesis,
Unicode and whitespace steps still leave0 tokens. Native inference returned code0; this was
a reference eligibility/evaluation mismatch, not an ASR crash. The evaluator correctly emitted
`INVALID_EMPTY_NORMALIZED_REFERENCE` at overall attempt28 / EN04. The reference remains unchanged.

Prospective invariant: **NORMALIZED_REFERENCE_TOKEN_COUNT >= 1**. Bind exact UTF-8 reference
bytes to their frozen SHA-256, call the existing
`alpha_asr_eval_text_contract.normalized_tokens`, and reject empty token output **before
deterministic ranking/selection and freeze**. No second normalizer, text repair, threshold
change or retroactive selection filter is introduced. The
[prospective wrapper](../../tools/alpha_asr_prospective_reference_eligibility.py) delegates
all subsequent eligibility, exclusions, duplicate-group rejection and ranking to the historical
dev selector. Future operators must use its `select` entry point; old operators remain unchanged.
Missing/mismatched reference bytes fail closed. The
[generated tests](../../tools/test_alpha_asr_prospective_reference_eligibility.py) cover raw-nonempty
normalized-empty rejection, valid Unicode, exact normalizer calls before rank, deterministic
selection, shortage, anti-reuse, input immutability and historical public bytes remaining unchanged.

## Untouched pool — current authority only

Same Common Voice Spontaneous Speech5.0 / `sps-corpus-5.0-2026-09-11`, **dev** for both languages,
CC0-1.0, exact archives and source-index hashes from the accepted data authority. No split,
release, source, duration bound or participant policy was expanded. Exact archive/index/reference
digests were rechecked read-only; decode/duration validity reuses the retained hash-bound619-record
inventory. There was no audio decode or device execution this task.
Exclusions cover BASE48+SMALL48+ARM82dev48 = **144 source records /144 audio digests /55 participants**,
including29 unexecuted historical SMALL selections and20 unexecuted ARM82 EN selections.
ARM82 EN04 executed but was invalid:21 EN selections lack valid scores, while20 were unexecuted.
Thus49 of144 consumed selections were never executed;50 lack valid scores. This distinction
reconciles the owner wording to the retained28-attempt journal without weakening any exclusion.
Four retained journals and the48-row prior-use ledger reconcile to these exclusions or generated
silence; no separately identified development/tuning corpus exists in the retained authority.

Counts are sequential; overlapping reasons are charged at their first listed step. Duplicate
groups are checked after other eligibility, as the frozen selector requires. Both the complete
inventory and final pool have no repeated-audio digest groups, so this ordering does not conceal
any duplicate removals.

| Exclusion/check completed | RU remaining | EN remaining |
|---|---:|---:|
| complete_current_dev_inventory | 71 | 548 |
| BASE48_SMALL48_source_audio | 71 | 548 |
| all_ARM82_dev48_source_audio_including_unexecuted | 47 | 524 |
| all_historical_participants | 0 | 171 |
| normalized_reference_token_count_at_least_one | 0 | 154 |
| existing_duration_source_decode_raw_validity | 0 | 137 |
| all_members_of_duplicate_audio_groups | 0 | 137 |

All71 RU dev records belong to the same four participants now consumed by ARM82dev48.
Consequently participant exclusion alone makes RU0, independently of the new reference rule.
For detailed exclusion attribution: BASE/SMALL source and audio removals are each RU0/EN0;
dev48 source removes24/24, then audio removes0/0 additionally. Historical BASE/SMALL participants
remove0/26 (remaining47/498), and dev48 participants remove47/327 (remaining0/171).
EN171 remain after participant exclusions;17 normalize empty, leaving154; one is below1s
(remaining153), sixteen exceed20s (remaining137); other source/raw/decode checks remove0.
Duplicate exclusions remove0. Final EN137 span49 participants. Across the whole inventory,60 EN and0 RU
references normalize empty; these are prospective pool diagnostics only, not historical rescoring.
No eligible record is ranked into a new holdout and no new manifest/materialization is produced.

Combined participant-set digest: `102942dbfdd99f8b3b052c4ed90f6560a074065cf26497b4966a28faa9df7ec4`.
Eligible-pool digest (canonical complete retained candidate rows, sorted by canonical JSON bytes):
`e04697ca0f42dcb991fe7743c75533c4ae6309725d6bfc716fe4069867651adb`.
Full exclusion/count identities and private audit whole-file pins are in JSON.

## Narrow candidate comparison — not an experiment freeze

Conditional preference is **multilingual Small q8_0**, preserving Small capacity and changing
only weight quantization as the principal quality/performance variable. No model is downloaded,
bound to a new profile or admitted for execution because RU24 cannot be formed.
Exact catalog at revision`5359861c739e955e79d9a303bcbc70fb988958b1`, MIT:

| Artifact | Exact bytes | Increase over Small q5_1 | Assessment |
|---|---:|---:|---|
| Small q8_0 |264464607|74379120 (+39.13%)|Narrowest plausible precision test; moderate model-storage increment; conditional preference.|
| Small F16 |487601967|297516480 (+156.52%)|Same capacity, much greater memory/storage burden; no demonstrated need for this jump.|
| Medium q5_0 |539212467|349126980 (+183.67%)|Changes capacity and quantization, increases computation and working-set risk; weak fit for this isolated next test.|

Small q8_0 SHA-256: `49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f`.
Exact hashes for all three are in JSON, read from immutable Git LFS pointers, not model inference.
[Upstream model catalog/license](https://huggingface.co/ggerganov/whisper.cpp/blob/5359861c739e955e79d9a303bcbc70fb988958b1/README.md).
The pinned runtime implements Q5_1 and Q8_0 CPU dot-product paths; its reference quantizers use
5-bit affine versus8-bit symmetric storage. Higher precision plausibly reduces weight rounding
loss, but **does not guarantee recovering four errors or improving either language's WER**.
[Pinned quantizers](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/ggml/src/ggml-quants.c),
[CPU type implementations](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/ggml/src/ggml-cpu/ggml-cpu.c).

All three are offline ggml artifacts compatible in format with the existing Android ARM64 CPU
integration and RU/EN evaluator; this is source-level compatibility, not a new device admission.
Q8_0 entails identity/materialization work with low integration change, but unknown quality and
latency effects. More weight bytes increase storage and likely load/memory traffic; kernel behavior
prevents a confident RTF direction or ratio. F16 and Medium have greater resource risk.
Historical ARM82 peak heap934189144 leaves407988136 bytes below the limit, but artifact growth
is **not** a measured peak-memory increment; do not treat that subtraction as Q8_0 admission.
Historical observed maxRTF4.613362 was below6, but weighted/p95/complete resource outcome remained
NOT_EVALUABLE. Cold load277978us gives observed context only, not a successor guarantee.
Upstream generic memory figures (Small~852MB, Medium~2.1GB) are not quantized POCO predictions.
[Runtime resource documentation](https://github.com/ggml-org/whisper.cpp/blob/927cfce34f31707e17f2bff35c349632fb9e2c3a/README.md).

## Validation

**191 generated host tests PASS**, including9 new prospective-admission tests. Required host-only
Stage00 checks pass: Spotless, detekt, four unit-test tasks, Android-test compilation, four lint tasks
and debug assembly (197 Gradle tasks); Stage00 artifact validator and APK native alignment also pass.
No Android device test or ASR inference was run. Strict JSON, local Markdown links, privacy and
`git diff --check` pass. Exact snapshots confirm619 historical tracked files and1003 retained private
files byte-identical; earlier status/backlog bytes are preserved with an additive entry only.
Validation command details and log hashes are retained in the external audit; sanitized outcomes
are in JSON. The unchanged evaluation profiles/gates and native identities are compared exactly.

## Changed and unchanged authority

Changed prospectively: normalized-reference eligibility; consumed-set bookkeeping expands96→144
records/audio digests and32→55 participants; new analysis/helper/tests and additive status/backlog.
No successor experiment ID, selected manifest, new execution profile or campaign authority exists.

Explicitly unchanged: accepted historical model q5_1/hash; whisper.cppv1.9.4 commit
`927cfce34f31707e17f2bff35c349632fb9e2c3a`; all accepted native hashes and ARM82 build options;
NDK28.2.13676358/Clang19.0.1/CMake3.22.1/Ninja1.10.2, arm64-v8a/API28 Release;
CPU-only4 threads, GPU off, flash attention on, inherited scheduling/no affinity changes;
greedy temperature0, explicit RU/EN, no_context=true, audio_ctx=0/full context; fresh process/context;
normalizer/oracle; RU<=20%, EN<=18%, sum edits/sum reference tokens and24 valid cases each;
weightedRTF<=2, p95<=4, max<=6, cold<=15s, PSS<=1610612736, heap<=1342177280,
OOM0, thermal<3 and complete telemetry48; measurement/cleanup/stop precedence; one primary per
case/no retries/no replacements, one future invocation only if separately authorized; private owner-only
retention through2026-10-25. All exact preserved fields are copied into JSON for comparison.

ASR-SMALL-ARM82-01 remains TERMINAL / MEASURED with accepted VALID_FAIL. Stage5 and GroupB
remain NOT_PASS; POC-ASR-001 remains BLOCKED / NOT_READY. Recovery stays0D.6 ALPHA CLOSED / FULL OPEN.
PR86 remains OPEN/DRAFT/UNMERGED and untouched. No Android production/runtime changes.

Next action: **owner authorization for a narrow data-authority expansion**, preserving participant
isolation and the corrected eligibility rule. Only after sufficient untouched RU24+EN24 is proven
can Small q8_0 be prospectively frozen. This task ends at the factual holdout blocker.
