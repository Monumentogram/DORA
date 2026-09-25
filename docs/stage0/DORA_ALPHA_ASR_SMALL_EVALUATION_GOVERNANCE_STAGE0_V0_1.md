# DORA Alpha ASR SMALL evaluation governance — Stage 0 v0.1

Task **5.6B**, decision date **2026-09-25**.

**5.6B = PASS / SMALL_Q5_1_EVALUATION_DATA_AND_RESOURCE_GATES_FROZEN**.

The owner's explicit 5.6B instruction prospectively approves the data, retention, selection and
resource decisions below, before any SMALL inference result exists. This task records governance
only. **5.6C = NOT_STARTED / NOT_AUTHORIZED**. No private data or model was opened, and no
selection, materialization, decode, model load, inference or device execution occurred.

## Authority and exact scope

Repository `Monumentogram/DORA`, branch `chat/alpha-asr-runner-scope`. Required starting HEAD,
observed local HEAD and freshly fetched remote HEAD all equal
`9e3551501209fcde100bbd09cfdea4eeaec01857`. Initial index and working tree were clean.
Authority: the Project Owner's task instruction, titled
`5.6B — SMALL Q5_1 NEXT-CANDIDATE DATA / RETENTION / RESOURCE-GATE FREEZE`.
The [machine-readable record](../evidence/poc-asr-001/alpha-asr-small-evaluation-governance-stage0-v0.1.json)
binds the instruction digest, approved contracts, protected-file hashes and validation.

| Identity | Frozen value |
|---|---|
| Candidate | `ggml-small-q5_1.bin`, multilingual Whisper SMALL q5_1 |
| Bytes | `190085487` |
| SHA-256 | `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` |
| HF repository / revision | `ggerganov/whisper.cpp` / `f281eb45af861ab5e5297d23694b7d46e090c02c` |
| Runtime | `ggml-org/whisper.cpp v1.9.4`, commit `927cfce34f31707e17f2bff35c349632fb9e2c3a` |
| Target | **POCO M5 only**, one bounded SMALL Stage-0 acceptance campaign |

[5.6A.1 admission](DORA_ALPHA_ASR_NEXT_CANDIDATE_STORAGE_REMEDIATION_STAGE0_V0_1.md) remains
the byte-verification authority; no model reread/download is needed here. Its PASS and overall
5.6A admission remain unchanged. The original BASE remains
**VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE**.
These new limits are not universal D1/D2/D3, OEM-matrix, all-device or production requirements.
They are owner-selected prospective limits, never thresholds derived from 5.4 observations.

## Approved reuse, access and retention

**APPROVED_FOR_ONE_FUTURE_BOUNDED_SMALL_CAMPAIGN**: reuse the already controlled Common Voice
Spontaneous Speech 5.0 RU/EN source archives and inventories to select one untouched acceptance
set, execute that bounded evaluation when separately authorized, and retain the minimum private
references/results/evidence needed to audit it. Existing dataset identity, provenance, lawful-use
and storage constraints remain applicable. This governance approval does not authorize actual
access in 5.6B or execute/authorize 5.6C.

Approved identities remain RU `cmu5mg3pr00simh07epeylc55`, EN `cmu5nqn1h00vwmi07b4dbk085`,
version `5.0`, release `sps-corpus-5.0-2026-09-11`, provider `test` split. Exact previously admitted
archive/terms identities must remain bound privately in a later authorized execution; they were
not reopened or invented here. Access: Project Owner/Data Custodian and the explicitly authorized
bounded local test process only. No other people, services or cloud systems receive access.

Training, fine-tuning, decoder/prompt/beam/best_of/temperature/suppression/VAD/language-policy
search, candidate selection using acceptance outcomes, public sharing, Git/LFS/Actions upload,
cloud upload and production use are prohibited. This approval creates no tuning/development set.

The historical policy remains `ACTIVE_THROUGH_5_5_THEN_DELETE_WITHIN_30_CALENDAR_DAYS` with
assessment completion `2026-09-25`. This dated owner decision adds only minimum necessary reuse
through the bounded SMALL campaign and its evidence assessment, with the same absolute private-data
deletion deadline **2026-10-25**. **No extension is approved.** All required private-data work must
finish before that deadline. If it cannot, stop, execute the existing deletion obligation and
record verified deletion receipts; do not silently retain data while seeking an extension.
Stricter applicable terms and earlier no-longer-needed deletion still apply. No deletion occurs here.

Receipts must cover applicable source/archive copies, inventories, extracted/selected host files,
temporary device copies, references, hypotheses/results, journals, temp/cache copies, derived
private evidence and backups. Record opaque copy class/ID, custodian, deadline/trigger, deletion
time/method, verified absence, failures and remediation. Public receipts exclude absolute private
paths and sample identities. Do not reopen inventories merely to enumerate copies in this task.

## Frozen successor selection contract

Contract ID: **dora-alpha-asr-small-acceptance-selection-v0.1**. Its canonical SHA-256 is recorded
in the JSON evidence: hash the `selectionContract` object as sorted-key UTF-8 JSON with compact
separators, no NaN and no terminal newline. This is a semantic governance contract; implementation
and private manifests are not created in 5.6B. The old first-24 validator is unchanged and does
not by itself validate successor exclusion logic.

Exactly **24 RU + 24 EN**, all fresh and untouched by prior model evaluation. Inherit the
[5.1B eligibility/path/provenance contract](DORA_ALPHA_ASR_DATA_MANIFEST_CONTRACT_STAGE0_V0_1.md):
exact admitted identity/test split and locale, valid hashes/positive byte length, inclusive duration
**1000–20000 ms**, `decodeResult=VALIDATED`, valid non-empty reference and all existing applicable
governance constraints. Neither eligible-pool availability nor completeness is claimed here.

Before ranking, exclude all original 48 cases by source identity, all original 48 audio digests,
every record previously used for model evaluation or tuning, duplicate audio, and any later
authorized development/tuning data. For deterministic duplicate exclusion, remove every member
of repeated audio-digest groups in the eligible candidate pool; no arbitrary representative is
kept. Where existing lawful pseudonymous metadata permits, exclude participant overlap with
development/tuning data. Otherwise record the participant-disjointness limitation; never invent
disjointness or re-identify anyone. Participant metadata remains private and outside the unchanged
public manifest schema. Exclusion and eligibility evidence must be complete before future ranking.

~~~text
SHA-256(UTF8("dora-alpha-asr-v0.1") || NUL || UTF8(locale)
        || NUL || UTF8(upstreamRelativePath))
~~~

NUL means one zero byte. Preserve exact UTF-8 path bytes without case folding, normalization or
rewriting. After exclusions, sort by raw 32-byte key ascending, tie-break by exact UTF-8 path bytes,
and take first 24 eligible RU then first 24 eligible EN. Campaign order is **RU24 then EN24**,
retaining the historical time/order confounder for comparability. Fewer than 24 in either language
means **BLOCKED**; do not weaken rules or manually substitute cases based on quality.

Before first inference, freeze privately exclusion-set digest, eligible-pool digest,
selection-contract version/digest, selected-manifest digest, exact candidate identity, exact
decoding profile, this resource-gate record and data/retention authority. Use separately authorized
successor manifest validation; historical profiles, runner and source remain unchanged here.

Acceptance is never decoder/prompt/temperature/suppression/VAD search, model-selection or threshold-
selection data. If tuning is necessary, stop and obtain separate authorization for a disjoint
development set. Once outcomes inform a decision, the set is no longer a fresh blind holdout for
another candidate-selection cycle. Original 48 reuse is excluded from fresh acceptance and requires
separate explicit authority for any bounded regression comparison, reported separately. No access
to original cases or private exclusion metadata occurred in 5.6B.

## Frozen quality and resource predicates

Preserve [5.3A scoring](DORA_ALPHA_ASR_EVAL_CONTRACT_STAGE0_V0_1.md), the normalizer and Java
oracle. For each language sum normalized S+D+I and reference tokens; use exact integer comparisons:
RU `100 * errors <= 20 * referenceTokens`, EN `100 * errors <= 18 * referenceTokens`.
Both must pass on complete valid attempts. No rounding a failure to PASS, per-case WER averaging,
quality retry or omission of poor cases. Timestamp quality remains **NOT_EVALUABLE** unless
separate timestamp-reference scope is approved.

The immutable [measurement protocol](../evidence/poc-asr-001/alpha-asr-measurement-protocol-stage0-v0.1.json)
continues to define measurement regions/sampling. This new prospective governance overlay approves
the following gates for SMALL on POCO M5 only; the old protocol's dated proposed threshold fields
are preserved historically. Future execution must explicitly bind this overlay, never silently
reinterpret old results as passing new limits.

| Metric | Approved predicate / disposition |
|---|---|
| Duration-weighted inference RTF | `sum(inferenceElapsedMicros) / sum(decodedAudioDurationMicros) <= 2.0` |
| Nearest-rank p95 inference RTF | `<= 4.0`; ascending exact per-case ratios, rank `ceil(0.95*N)`, 1-based (46 of 48) |
| Maximum per-case inference RTF | `<= 6.0` |
| Median inference RTF | Reported diagnostic only |
| Maximum cold model load | `<= 15000000` microseconds |
| End-to-end latency | **DIAGNOSTIC_ONLY / NOT_AN_ACCEPTANCE_GATE**; no new start/end boundary invented |
| Maximum observed PSS | `<= 1610612736` bytes (1.5 GiB) |
| Maximum sampled native allocated heap | `<= 1342177280` bytes (1.25 GiB) |
| Evidenced OOM | Exactly `0`; any positively evidenced event is FAIL |

All three RTF gates apply across the complete valid 48-case campaign and must pass. Use exact
ratios/cross-products without result-dependent rounding or aggregation selection. Inference RTF
is `inferenceElapsedMicros / decodedAudioDurationMicros`, measuring only the frozen monotonic
region around `whisper_full`, excluding decode, model load and persistence. Cold load is the
separate region around `whisper_init_from_file_with_params`, fresh process/context for each case;
any valid case above 15 seconds fails that predicate. These function names define future timing,
not calls made by this task. Existing naturally available end-to-end observations may be reported
separately and cannot create PASS/FAIL; a product latency gate needs another prospective contract.

PSS is process `smaps_rollup` Pss KiB multiplied by 1024; native heap is `mallinfo().uordblks`
bytes. Both independent ceilings must pass; never add the metrics. They are maximum observed/
sampled values, not continuous high-water marks. Preserve the window before decode through after
model free and synchronous samples around load, after inference and before free.

Missing results, generic abnormal exits or incomplete attribution do not establish OOM and cannot
be converted to PASS. Ambiguous termination makes the affected attempt/campaign incomplete and
requires explicit disposition without a silent retry. Preserve original evidence and all attempts.

Android thermal status must stay below **SEVERE (3)** throughout an accepted campaign. SEVERE or
worse requires a safe stop of further execution, preservation of evidence, no silent same-campaign
retry and no resource PASS. A future rerun needs a separately justified execution decision. No
temperature-in-degrees threshold is introduced.

Memory sampling target **100 ms**, maximum valid gap **1000 ms**; thermal polling delay
**1000 ms**, maximum valid gap **3000 ms**. Complete applicable telemetry is required. Missing
required samples/streams or excessive gaps prevent resource PASS; never synthesize missing samples.
The **600-second timeout** and **605-second native alarm** remain safety controls, not latency or
RTF budgets. No gate may be weakened after outcomes are observed.

## Evidence, integrity and publication boundary

All eight operation counts are **0**: private corpus content access, retained private reference
access, retained hypothesis/result access, new audio decode, model load, ASR inference, device
execution and new evaluation subset materialization. Model downloads and model-byte reads are
also zero in 5.6B. Only public repository evidence and the owner's instruction were read.

Validation covers strict JSON, Markdown/references, private-path/privacy scan, applicable Stage 00
checks, `git diff --check`, exact four-file delta and 56 protected-file hashes/Git blob identities.
This includes 5.4/5.5, blocked 5.6A and 5.6A.1 report/evidence pairs, historical ASR/data evidence,
normalizer, oracle, runner/native code and production native allowlist. All pre-existing status/
backlog bytes are retained through additive insertion; no other tracked file changes. Recovery,
production code and PR #86 are untouched. No Android/Gradle/device checks or CI dispatch are needed.

One atomic commit and push to this task branch; no PR or merge. Publication is pending at evidence
freeze, with actual commit/fetched-remote identities supplied in the handoff. PASS means only that
rules are prospectively frozen. SMALL quality/performance/device fitness, production/all-device
support and POC-ASR completion are not claimed. **5.6C remains NOT_STARTED / NOT_AUTHORIZED**.
Stop after push without opening data or materializing the holdout.
