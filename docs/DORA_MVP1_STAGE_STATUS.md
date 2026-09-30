# Dora MVP 1 — Stage Status

## 2026-09-30 — 7.2E Cloud identity, authorization and ownership contract closed

**7.2E = PASS / CLOUD_IDENTITY_AUTHORIZATION_OWNERSHIP_CONTRACT_READY.** [Versioned logical contract](stage1/DORA_ALPHA_CLOUD_IDENTITY_AUTH_OWNERSHIP_CONTRACT_V0_1.md), ADR-0019 and the closed JSON catalogue separate installation principal, verified credential evidence, recording/source/job ownership, current consent and operation-specific authorization. Exact-source upload authority requires validated issuance and current decisions, immutable generation-bound evidence and cumulative byte limits. Retries resolve the prior exact context and reauthorize; ASK/batch transitions preserve prompt history and deferred records. ADR-0009/0011 and existing retention, provider, transcript and Local/offline semantics remain intact. No auth technology is selected and no runtime is implemented.

Implementation `29c9f7da7f9a41e001c61c8f6ebd93bda9eaba85`, parent `88d203ea364981db7917b3d7285078f9de16266f`, passes [exact-SHA CI 36708561117](https://github.com/Monumentogram/DORA/actions/runs/36708561117), both required jobs and every mandatory step. [Local checks/review](evidence/alpha-7.2e-local-v0.1.json), [CI](evidence/alpha-7.2e-ci-v0.1.json), [Sheet readback](evidence/alpha-7.2e-sheet-v0.1.json) and [closure](evidence/alpha-7.2e-closure-v0.1.json) retain evidence. 82 new tests and 68 existing related tests pass; independent review's one P1 and two P2 findings were reproduced and fixed. No unresolved P0/P1 or deferred minor findings. The later evidence-only HEAD requires its own exact-SHA CI and final Sheet HEAD readback, recorded in the task publication receipt.

**Stage 7 and Group C IN PROGRESS. Next separate task: 7.3 — Сборка, CI и проверка внутреннего APK, NOT_STARTED.** 7.3C prerequisites 7.2C–E are satisfied; 7.3C and Stage 8 NOT_STARTED. Auth/backend/token issuance/login/ownership/consent/authorization/upload/object storage/AWS adapter, transcript persistence, merge and recording runtime NOT_IMPLEMENTED. Recovery integration/audio upload NOT_RUN; real audio NOT_USED; AWS NOT_CALLED, spend 0 by this task; FIRST_REAL_PRODUCT_AUDIO NOT_READY. Recovery clean replacement remains separate before Stage 8 acceptance. Main unchanged; PR #86 untouched; PR #87 open/draft/unmerged. No automatic next-stage execution. Earlier dated entries remain historical.

## 2026-09-30 — 7.2D transcript versioning and edit contract closed

**7.2D = PASS / VERSIONED_TRANSCRIPT_AND_EDIT_CONTRACT_READY.** [Versioned logical contract](stage1/DORA_ALPHA_TRANSCRIPT_VERSIONING_EDIT_CONTRACT_V0_1.md), ADR-0018 and the closed JSON catalogue define immutable LOCAL/CLOUD/MERGED versions, separate edit history and recording-wide revisions, source/segment/time/context anchors, explicit alignment/proposals, per-edit conflict resolutions and guarded active selection. Parent/edit-base ordering prevents causal cycles; processing history survives cancellation; historical and new decisions preserve user authority. No physical database, persistence or merge algorithm is implemented.

Implementation `3ad8f297a72b64eb0997e986ed8d817bede23cf7`, parent `55ae8aef51527d87b719369b9e55feaf189674ff`, passes [exact-SHA CI 36701576520](https://github.com/Monumentogram/DORA/actions/runs/36701576520), both required jobs and every mandatory step. [Local checks/review](evidence/alpha-7.2d-local-v0.1.json), [CI](evidence/alpha-7.2d-ci-v0.1.json) and [Sheet readback](evidence/alpha-7.2d-sheet-v0.1.json) retain evidence. 44 new tests pass; all three Important and one Minor independent review findings were reproduced and fixed. 7.2C mojibake hygiene = FIXED / NO_SEMANTIC_CHANGE. The later evidence-only HEAD requires its own exact-SHA CI and final Sheet HEAD readback, recorded in the task publication receipt.

**Stage 7 and Group C IN PROGRESS. Next separate task: 7.2E — Cloud identity, auth and recording/job ownership, NOT_STARTED.** 7.3/7.3C and Stage 8 NOT_STARTED. Physical DB/Room/SQLite/server DB, DAO/repository/migrations, merge algorithm, transcript persistence, auth/ownership, recording and Cloud/AWS adapter runtime NOT_IMPLEMENTED. Recovery integration/audio upload NOT_RUN; real audio NOT_USED; AWS NOT_CALLED, spend 0 by this task; FIRST_REAL_PRODUCT_AUDIO NOT_READY. Recovery clean replacement remains separate before Stage 8 acceptance. Main unchanged; PR #86 untouched; PR #87 open/draft/unmerged. Earlier dated entries remain historical.

## 2026-09-30 — 7.2C replaceable Cloud ASR provider contract closed

**7.2C = PASS / REPLACEABLE_CLOUD_ASR_PROVIDER_CONTRACT_READY.** [Versioned contract](stage1/DORA_ALPHA_CLOUD_ASR_PROVIDER_CONTRACT_V0_1.md), ADR-0017, closed JSON types and a separate evidence-bound Amazon Transcribe profile freeze the server-side provider interface. Original audio, DORA identities, provider namespace/provenance, normalized results/timestamps, errors and reconciliation-before-retry are explicit. Android RecognitionPort and runtime code are unchanged. Unknown model/cancellation/timestamp properties remain unknown; final 6.2D owner-only admission is the sole current provider authority.

Implementation `5c3ce1f0044f29582e2dc99283293b32468e2a9c`, parent `b380fb352d20fa62ed2de3a4fae3b05e51b86f5a`, passes [exact-SHA CI 36695084857](https://github.com/Monumentogram/DORA/actions/runs/36695084857), both required jobs and all mandatory steps. [Local checks/review](evidence/alpha-7.2c-local-v0.1.json), [CI](evidence/alpha-7.2c-ci-v0.1.json) and [Sheet readback](evidence/alpha-7.2c-sheet-v0.1.json) retain evidence. Independent review's two Important findings were reproduced and fixed before publication; 24 new tests pass. The subsequent evidence-only HEAD requires a separate exact-SHA CI, recorded in the task publication receipt to avoid a self-referential commit.

**Stage 7 and Group C IN PROGRESS. Next separate task: 7.2D — Versioned transcript storage and edit anchors, NOT_STARTED.** 7.2E, 7.3/7.3C and Stage 8 NOT_STARTED. Backend/worker/adapter/Android Cloud API, persistence/auth/ownership runtime NOT_IMPLEMENTED. AWS NOT_CALLED, spend 0 by this task; no audio; FIRST_REAL_PRODUCT_AUDIO NOT_READY. Recovery integration NOT_RUN; clean replacement remains separate before Stage 8 acceptance. Main unchanged; PR #86 untouched; PR #87 open/draft/unmerged. Earlier dated entries remain historical.

## 2026-09-30 — 7.2 minimal application contracts closed

**7.2 = PASS / ALPHA_MINIMAL_MODULES_AND_DATA_CONTRACTS_READY.** [Versioned contracts](stage1/DORA_ALPHA_MINIMAL_MODULES_DATA_CONTRACTS_V0_1.md) and ADR-0016 define typed opaque IDs, independent capture/storage/recognition state axes, generic asynchronous ports, typed failures and a JVM-testable app coordinator. Existing UI is wired to explicit UNAVAILABLE production ports. Only `:app` and `:core:model` changed; no new modules or dependencies. Exact implementation `9ee5d718348fc0682ae94e16a5a20ecd75e706c4`, parent `0492ff34a44a6b5becdc232b16c2f3f99de87456`, passes [Android CI 36683669682](https://github.com/Monumentogram/DORA/actions/runs/36683669682), both required jobs and every mandatory step. [Local checks](evidence/alpha-7.2-local-v0.1.json), [CI](evidence/alpha-7.2-ci-v0.1.json) and [Sheet readback](evidence/alpha-7.2-sheet-v0.1.json) retain evidence.

**Stage 7 and Group C IN PROGRESS; next separate task: 7.2C — Replaceable Cloud ASR interface and adapter contract, NOT_STARTED.** 7.2D/E and 7.3 NOT_STARTED; Stage 8 NOT_STARTED. Recording/storage/Cloud ASR runtime NOT_IMPLEMENTED; FIRST_REAL_PRODUCT_AUDIO NOT_READY. 7.1 identity/version/signing remain unchanged. No AWS or Recovery integration; PR #86 untouched, main unchanged, PR #87 open/draft/unmerged. Recovery clean replacement remains separately required before Stage 8 recording/storage acceptance. Earlier dated entries remain historical.

## 2026-09-30 — 7.1 Alpha identity/signing/install closed

**7.1 = PASS / ALPHA_IDENTITY_SIGNING_INSTALL_READY.** [Decision and evidence](stage1/DORA_ALPHA_IDENTITY_SIGNING_INSTALL_V0_1.md): applicationId/namespace `com.monumentogram.dora`, version 2 / `0.1.0-alpha.1`, dedicated PROJECT_OWNER internal signing key outside Git. Signed APK and clean repeat are byte-identical. POCO M5 Android14/API34 fresh/reinstall/launch and same-key 2→3 upgrade PASS; wrong signer rejected. Exact implementation `4a2350a02eba8adc76c04bf250e7769513ad9aa7`, parent admitted `4e7742d88377d3d915be904618fc220d479ab25d`; [CI 36677470351](https://github.com/Monumentogram/DORA/actions/runs/36677470351) passes both required jobs and all mandatory checks. Final phone installation is baseline version 2.

**Stage 7 = IN PROGRESS; Group C = IN PROGRESS; next = 7.2 — Минимальные модули и контракты данных (NOT_STARTED).** No recording/storage/ASR product flow, Cloud runtime or real-product-audio readiness is claimed. PR #86 remains OPEN / DRAFT / UNMERGED / untouched. Owner-authorized reconciliation is PASS / CLEAN_REPLACEMENT_PR; actual Recovery clean replacement is a separate prerequisite before Stage 8 recording/storage acceptance, not before 7.1. No AWS or Recovery execution/integration, no main merge. Earlier dated statuses remain historical.

## 2026-09-29 — 6.3 development admission; exact repair baseline

The [versioned 6.3 decision](stage0/DORA_ALPHA_DEVELOPMENT_ADMISSION_6_3_V0_1.md) records **6.3 = PASS / DEVELOPMENT_ADMISSION_AND_EXACT_BASELINE_FROZEN** and **STAGE_0 = PASS / ALPHA_IMPLEMENTATION_ADMITTED**, strictly for `OWNER_ONLY_CLOSED_INTERNAL_ALPHA`. Stage 6 is closed for implementation entry. The exact implementation baseline is `92f00f7dd4a18a3d4b2fdd159fdaef86fa3f8699` on `chat/alpha-asr-runner-scope`; the subsequent admission-document commit is not substituted for that CI-tested source.

All ten predecessors remain satisfied in their accepted scopes. Complete exact-source Android CI passed before this decision; the versioned package binds its run, jobs and checks. Only `CLD-ADM-ADMISSION-001` changes from BLOCKED to `SATISFIED_FOR_OWNER_ONLY_CLOSED_INTERNAL_ALPHA`. Effective39: 6 SATISFIED + 2 SATISFIED_FOR_CLOSED_INTERNAL_ALPHA + 3 SATISFIED_FOR_OWNER_ONLY_CLOSED_INTERNAL_ALPHA + 0 PARTIALLY_SATISFIED + 3 OPEN + 0 BLOCKED + 25 NOT_RUN = 39. All 28 downstream gates and separate External Legal triggers remain intact.

Group C is READY / NEXT; **7.1 — Идентификатор, подпись и установка alpha** is the first next task. Stage 7 is NOT_STARTED. Cloud runtime is NOT_IMPLEMENTED and FIRST_REAL_PRODUCT_AUDIO is NOT_READY until the applicable runtime checklist passes. PR #86 is outside this baseline; future `PR86-INTEGRATION-RECONCILIATION` grants no integration authority now.

6.2D remains owner-only PASS: RU 17/179 = 9.4972% measured PASS; EN READ 37/181 = 20.4420% historical 18% benchmark FAIL with OD-62D-EN-01 owner acceptance only. No AWS, audio, quality or Recovery campaign was rerun. No second speaker, external/customer/public use, production readiness or Alpha-exit completion is admitted. Earlier dated entries retain their historical scope.

## 2026-09-29 — 6.2D technical closure: owner-only provider admitted

The [technical closure result](stage0/DORA_CLOUD_62D_DUPLICATE_NAME_CLOSURE_RESULT_V0_1.md) closes the current product cases under the published prospective protocol. Canonical byte-level media preflight rejects contradictory format, truncated structure and subminimum duration before upload/Start. Retained missing-input/access adjudications map to typed non-retryable errors. Exactly two new synthetic Starts used the same valid input and request: the first completed with the exact Output CMK, the second returned `ConflictException`. Independent raw-evidence review, cleanup, stack retirement and bounded cost passed. Historical raw verdicts remain unchanged.

**6.2D = PASS / OWNER_ONLY_CLOSED_INTERNAL_ALPHA_PROVIDER_ADMITTED.** EVALUATION and PROVIDER are `SATISFIED_FOR_OWNER_ONLY_CLOSED_INTERNAL_ALPHA`; ADMISSION remains `BLOCKED`. The 39-gate effective map is 6 SATISFIED + 2 SATISFIED_FOR_CLOSED_INTERNAL_ALPHA + 2 SATISFIED_FOR_OWNER_ONLY_CLOSED_INTERNAL_ALPHA + 0 PARTIALLY_SATISFIED + 3 OPEN + 1 BLOCKED + 25 NOT_RUN. External Legal remains separate. **6.3 = ELIGIBLE_TO_START / NEXT; execution NOT_RUN.** This does not admit production implementation.

No owner recording or WER was rerun. RU 17/179 = 9.4972% remains measured PASS; EN READ 37/181 = 20.4420% remains a historical 18% benchmark FAIL accepted only under OD-62D-EN-01 for the Project Owner. EN spontaneous, noise and timestamp accuracy remain NOT_EVALUATED; revalidation is mandatory before a second real speaker. No external/customer/public admission. The historical USD9.9685 reserve remains recorded; proven unused retired capacity yields a revised prior upper bound USD9.755509 plus USD0.240875 incremental reserve = USD9.996384/10 total, USD0.541500/2 ASR. Actual charges are NOT_OBSERVED. Diagnostics are 11/12; no further Start is authorized in this task. Both task CMKs are PendingDeletion, not physically deleted.

## 2026-09-29 — 6.2D owner technical successor: required cases not satisfied

The [terminal technical result](stage0/DORA_CLOUD_62D_OWNER_TECHNICAL_RESULT_V0_1.md) records one synthetic smoke PASS and 13 required technical Starts across 12 cases, with no new owner quality Starts. Raw technical oracles were not all PASS (tech-empty, tech-near_empty, tech-truncated, tech-wrong_format, tech-missing_s3, tech-duplicate_name, tech-permission_denial); append-only adjudications are shown separately from those raw outcomes. The technical suite is **NOT_PASS**. Exact task content cleanup and stack retirement were verified; both task CMKs are pending deletion, not physically deleted. The owner-only English acceptance decision does not waive these technical results.

**6.2D = BLOCKED / REQUIRED_TECHNICAL_CASES_NOT_SATISFIED.** EVALUATION and PROVIDER remain OPEN; broad ADMISSION remains BLOCKED; 6.3 is NOT_RUN / NOT_ELIGIBLE. The frozen 39 gates remain 6 SATISFIED + 2 scoped SATISFIED + 0 PARTIALLY_SATISFIED + 5 OPEN + 1 BLOCKED + 25 NOT_RUN. Historical RU 17/179 (9.4972%) PASS and EN 37/181 (20.4420%) FAIL against 18% remain unchanged. OD-62D-EN-01 accepts the EN limitation for the Project Owner only; it does not establish provider admission. Reserved total USD9.9685 stays within USD10; ASR reservation USD0.5385 stays within USD2. Actual invoiced charges are not observed. The shared diagnostic ledger moved from 8 to 9/12; 13 technical Starts are counted separately.
Exact CloudShell task-content copies were purged after preserving private evidence.

## 2026-09-29 — 6.2D owner eight-clip measurement: English quality gate failed

The [versioned result](stage0/DORA_CLOUD_62D_OWNER_BOUNDED8_MEASURED_RESULT_V0_1.md) and [aggregate machine record](evidence/cloud-6.2d-owner-bounded8-result-v0.1.json) retain eight actual owner primary Starts and independently checked scores. Normalized RU total is **17/179 = 9.4972%** (≤20% PASS); normalized EN READ is **37/181 = 20.4420%** (>18% FAIL). Both four-clip bounded latency diagnostics and all eight timestamp-structure audits passed; timestamp accuracy was not evaluated. The eight measured rows and raw/normalized S/D/I/N counts remain in the result. Four historical failed owner Starts and eight earlier engineering diagnostics remain separate; no automatic retry occurred.

The frozen Lambda rejected the first composite recipe hash before any composite Put. All four composite keys were absent, so the 13 planned technical Starts were **not run**. This harness defect is a separate incomplete technical evaluation, not the cause assigned to the measured English WER failure. Exact owner inputs, task output markers and jobs were removed; both buckets were independently empty, the task stack reached `DELETE_COMPLETE`, all 13 stack resources were checked, and both task CMKs are pending deletion. Nine CloudShell WAV transport copies and eleven CloudShell output/evidence-transfer copies were purged after preserving the private evidence locally; physical CMK deletion and actual invoiced charges are not claimed.

**6.2D = BLOCKED / MEASURED_PROVIDER_QUALITY_FAIL** for this owner-only bounded result. `CLD-ADM-EVALUATION-001` and `CLD-ADM-PROVIDER-001` remain OPEN; owner-only and broad provider admission are NOT_ESTABLISHED; 6.3 remains NOT_RUN / NOT_ELIGIBLE. The frozen 39-gate statuses stay **6 SATISFIED + 2 scoped SATISFIED + 0 PARTIALLY_SATISFIED + 5 OPEN + 1 BLOCKED + 25 NOT_RUN**. The next decision must address the measured EN quality miss and the separate composite harness defect under a new authorized protocol; no repeat Start or admission follows automatically.

## 2026-09-29 — 6.2D bounded owner Phase A v0.3; live evaluation pending

The prospective [Phase A v0.3](stage0/DORA_CLOUD_EVALUATION_PHASE_A_BOUNDED8_V0_3.md) and [machine record](contracts/DORA_CLOUD_EVALUATION_PHASE_A_BOUNDED8_V0_3.json) bind the same eight existing owner recordings, private authority/notice and corpus integrity, a USD9.4370 total reservation within the Owner's USD10 ceiling, and the fresh task-scoped AWS target. Mode A's synthetic S0/S1r/S2/S3 and fresh no-DataRole Mode B B1/B2 completed with exact output CMK and cleanup proofs. The original role-based B1 rejected before job creation; its specific cause remains **UNPROVEN**. Eight of the shared 12 engineering diagnostic Start slots have been consumed; that ledger is not reset.

For this exact owner-only evaluation, the Owner prospectively authorized omitting `JobExecutionSettings` from Start while the task-scoped Lambda operator performs bounded S3/KMS work. The unused DataRole resource remains in the task stack. This is an explicit exception to ADR-0011's general Alpha role design, not broad provider admission or a rewrite of historical evidence. Publication of this exact v0.3 JSON and Markdown, push and independent remote refetch are still required **before any resumed owner WAV upload or Start**. Current opt-out, target configuration and exact WAV bytes must be rechecked at dispatch. The four historical v0.2 owner Start failures remain recorded; no resumed owner audio was used for Mode B.

**6.2D = BLOCKED / LIVE_PROVIDER_EVALUATION_NOT_EXECUTED** until the bounded owner measurements and cleanup are complete. EVALUATION/PROVIDER remain OPEN; broad ADMISSION and 6.3 remain BLOCKED/NOT_RUN. RU ≤20% and EN READ ≤18%, the 39-gate accounting, separate external-use legal gate, Android/Recovery/PR86/main boundaries and earlier dated entries remain unchanged.

## 2026-09-28 — 6.2D reduced eight-recording preparation; acquisition deferred

Explicit Owner scope change prospectively replaces acquisition volume/selection and manual timing requirements only for the nearest bounded evaluation. The [eight-recording amendment](stage0/DORA_CLOUD_62D_EIGHT_CLIP_PROTOCOL_V0_1.md) and [machine record](contracts/DORA_CLOUD_62D_EIGHT_CLIP_PROTOCOL_V0_1.json) list every replacement. Exactly **RU 2 READ + 2 SPONTANEOUS; EN 2 READ + 2 SPONTANEOUS**, all eight evaluated, using the first two existing tasks of each class/language. No reserves/noisy tasks and no manual word timing. Original material, existing consent, recordings, references and history are preserved through a versioned private overlay.

**Recording DEFERRED until the Owner explicitly says “Готов записывать”.** No microphone use, AWS login request, evaluation or readiness polling now. Readiness does not confirm actual words or satisfy AWS prerequisites. Every actual reference still requires personal human verification; technical conversion, hashing, manifest and optional long composites are automated.

WER remains **RU ≤20%, EN ≤18%**, with actual S/D/I/reference-token denominators per record, language and required speech class. Noise robustness and timestamp accuracy are **NOT_EVALUATED / НЕ ОЦЕНЕНЫ**. Eight clips from one owner do not establish other-speaker/condition quality; original volume/noise/timing requirements are not claimed satisfied. EVALUATION/PROVIDER remain OPEN and ADMISSION remains BLOCKED where untested properties are mandatory. The 39-gate accounting is unchanged.

**Phase A v0.1 is immutable; full successor NOT_CREATED; Phase B NOT_RUN; 6.3 NOT_RUN/BLOCKED.** Actual complete private corpus, verified AWS configuration and all other live prerequisites must be bound in a full successor, committed/pushed/refetched before the first AWS upload/Transcribe. USD10 ceiling, privacy, retention/cleanup and no region fallback remain mandatory. Android production, Recovery, Stage5, PR86 and main are unchanged; no merge. Earlier entries below retain their historical scope.

## 2026-09-28 — 6.2D Phase A prospective protocol; live blocked

Owner-authorized 6.2D thresholds are prospectively frozen in [Phase A v0.1](stage0/DORA_CLOUD_EVALUATION_PHASE_A_V0_1.md)
and its [machine record](contracts/DORA_CLOUD_EVALUATION_PHASE_A_V0_1.json). Source baseline:75e45a152800c9c1589db201fc71fec9a6200fef.
**6.2D = BLOCKED / LIVE_PROVIDER_EVALUATION_NOT_EXECUTED**. **Phase A = PARTIAL / PROTOCOL_FROZEN_DATA_AUTHORITY_AND_LIVE_OPERATOR_UNBOUND**.
Real-speech admission: **BLOCKED / CLOUD_DATA_AUTHORITY_NOT_PROVEN**, RU0/EN0. Owner confirms AWS access is not prepared.

RU normalized WER<=20%; EN<=18%, independently by required slice; raw WER also reported.
Noisy/speakerphone<=35%, with no invented LOW_CONFIDENCE waiver. Exact-reference timestamp start/end median<=500ms,p95<=1500ms.
Batch service turnaround=(completion-observed minus submit)/audio duration, p50<=1x,p95<=2x; <=60s files absolutep95<=120s.
Nearest-rank quantiles and complete attempt accounting are frozen. Mixed-language and diarization remain excluded/deferred by6.1.
Primary input only mono PCM16LE16000Hz WAV, <=600s. Amazon Transcribe standard batch/eu-central-1/ru-RU/en-US is a candidate, not admitted.
Current official Frankfurt standard-batch priceUSD0.0001/s (USD0.006/min); budget ceilingUSD10. No AWS spend incurred by this task.

Seven generated non-speech fixtures and26synthetic host tests cover scoring and the shared terminal interface for AWS-shaped/fake adapters.
No live transport is installed/bound; no live quality, timing, latency, errors/limits or cleanup result exists. Mocks cannot close PROVIDER.
All34scope capabilities are dispositioned; no extra AI provider/function admitted. Existing Common Voice Local permission is not Cloud authority.
Exact real-corpus/timing manifest, live client/journal/cleanup binding and effective account controls must be frozen in a successor PhaseA,
pushed/refetched before any live test. Preserve v0.1 and every later benchmark; never amend thresholds from observed results.

EVALUATION OPEN; PROVIDER OPEN; ADMISSION BLOCKED. Unchanged39 counts:
6 SATISFIED +2 SATISFIED_FOR_CLOSED_INTERNAL_ALPHA +0 PARTIALLY_SATISFIED +5 OPEN +1 BLOCKED +25 NOT_RUN =39.
11.1C scoped PASS, PRIVACY/CONTROL scoped satisfaction and RETENTION satisfaction remain unchanged.
PRIVACY-LEGAL-EXTERNAL-001 remains separate mandatory future governance outside39, never waived by internal approval.
6.3 NOT_RUN/BLOCKED; runtime NOT_IMPLEMENTED; first real user audio NOT_READY. Android, Recovery, Stage5 and PR86 untouched; no merge/main change.
Next: Cloud-authorized RU/EN/timing data and test AWS access, then an execution-ready prospective PhaseA successor. Earlier entries below are historical.


## 2026-09-28 — 11.1C closed internal Alpha governance amendment

Effective only for `CLOSED_INTERNAL_ALPHA`: **11.1C = PASS / PRIVACY_RETENTION_CONTROL_PREREQUISITES_SATISFIED_FOR_CLOSED_INTERNAL_ALPHA**. Authority: Owner-approved OD-11C-23..26 in [v0.4](design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_4.md), [machine record](contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_4.json), [ADR-0013](adr/ADR-0013-closed-internal-alpha-expert-review-governance.md) and prospective [expert-review governance v0.1](design/DORA_INTERNAL_ALPHA_EXPERT_REVIEW_GOVERNANCE_V0_1.md). Baseline `7e05a74984a2cfbac3790b1f963b392ef7870fd8`. Earlier entries below are historical and retain their original status/evidence.

Independent AI-assisted Privacy/Legal review = `APPROVED_FOR_CLOSED_INTERNAL_ALPHA_WITH_CONDITIONS`; explicit Owner risk/scope acceptance = `APPROVED_BY_PROJECT_OWNER`. This supplies project governance only, not attorney/DPO opinion, statutory certification, regulator/external-audit/public-release approval. Frozen criterion5 historically required qualified scope approval; it is expressly amended for effective internal governance, never silently rewritten or declared satisfied by an external reviewer.

| Item | Current disposition |
|---|---|
| PC-01 | BLOCKED_EXTERNAL_APPROVAL → RESOLVED_BY_INTERNAL_ALPHA_GOVERNANCE |
| PC-02 / RT-01 / RT-02 / SC-01 | RESOLVED, unchanged |
| SC-02 | BLOCKED_BY_PRIVACY_ONLY → RESOLVED |
| CLD-ADM-PRIVACY-001 | SATISFIED_FOR_CLOSED_INTERNAL_ALPHA |
| CLD-ADM-RETENTION-001 | SATISFIED; v0.3 56-cell matrix and ADR-0012 unchanged |
| CLD-ADM-CONTROL-001 | SATISFIED_FOR_CLOSED_INTERNAL_ALPHA; ADR-0011 architecture unchanged |
| CLD-ADM-EVALUATION-001 / CLD-ADM-PROVIDER-001 | OPEN |
| CLD-ADM-ADMISSION-001 / 6.3 | BLOCKED by EVALUATION/PROVIDER; execution NOT_RUN |
| 6.2D | ELIGIBLE_TO_START / NEXT; execution NOT_RUN, not PASS |
| 6.1 / 6.2 | PASS, unchanged |

Hard population boundary: Owner and invited informed internal Alpha participants with freely given explicit recording/Cloud-test opt-in only. Customer/production calls, non-Alpha third-party or non-agreeing voices, public/anonymous users, hidden/passive recording and employee surveillance are outside scope. Existing mic-only explicit Start and synthetic-data authority remain. Live disclosure is disabled; all12 OD-11C-26 checks are NOT_RUN. Actual operator/contact, jurisdiction, lawful basis/recording duties, AWS contract applicability and usable rights/DSR handling (also after credential loss) are checked before real audio. `GDPR_REFERENCE_FRAMEWORK_WHERE_APPLICABLE`, not a universal applicability claim.

**Mandatory future gate: `PRIVACY-LEGAL-EXTERNAL-001` = `DEFERRED_MANDATORY_PRE_EXTERNAL_USE`** (canonical qualification status `DEFERRED_TO_PRE_EXTERNAL_USE`; residual `DEFERRED_MANDATORY_BEFORE_EXTERNAL_USE`). Owner commissions a qualified external reviewer BEFORE the earliest of: first external/non-internal Alpha user; first real customer production-like recording; conversations involving non-Alpha third parties; public beta; public release; material provider change; processing-region change; material processing-purpose expansion; significant new personal-data category; any legal/contractual circumstance explicitly requiring professional qualification. Verify actual operator/controller, participant jurisdictions, lawful bases, roles, AWS contract, transfers, final notice, participant rights, DSR process and market recording/communications law. NOT_RUN, not COMPLETED. This gate cannot be waived by internal governance, and qualification requirements may apply before internal use; unknown applicability blocks the affected operation pending facts. Full MVP/public governance is unchanged.

39 frozen gates: **6 SATISFIED + 2 SATISFIED_FOR_CLOSED_INTERNAL_ALPHA + 0 PARTIALLY_SATISFIED + 5 OPEN + 1 BLOCKED + 25 NOT_RUN = 39**. Internal normalized count:8/0/5/1/25, with the two scope-qualified promotions retained explicitly. The future external gate is separate governance, not a fortieth frozen gate. Frozen v0.1, privacy v0.1/v0.2/v0.3 and historical 6.1/6.2 evidence are preserved. No Android/runtime/AWS API/audio/inference/device/Recovery/PR#86 changes; no 6.2D/6.3 execution, main update or merge. Next task only: **6.2D — Cloud provider evaluation and technical admission**; no automatic execution.


## Current 11.1C retention closure — 2026-09-28 — v0.3

OD-11C-13..22 are APPROVED_BY_PROJECT_OWNER. [Current retention package](design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_3.md), [machine contract](contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_3.json) and [ADR-0012](adr/ADR-0012-alpha-retention-periods-and-provider-copy-limits.md) supersede only the enumerated retention interpretations of OD-11C-04/08/10. All prior text below is historical where it differs; v0.1/v0.2 and ADR-0011 remain preserved. Baseline `99976ce21a5ee2fde408e1bee78b12902a675b81`.

56/56 lifecycle cells classified: DORA-controlled audio hard 24h with explicit immediate safe success cleanup; transient output immediate after ingestion / failed 24h; ordinary metadata 30d, audit 90d, active consent/identity plus90d; non-audio restorable backup maximum 30d; guarded tombstone 120d after logical deletion. Hidden AWS copies use actual documented purpose/deletion controls and accepted limitations, never an invented physical 24h guarantee. No audio backup/cross-region replica or resurrection. Local defaults and DEC-017 stay unchanged.

`RT-01 = RESOLVED`; `RT-02 = RESOLVED`; `CLD-ADM-RETENTION-001 = SATISFIED`. `PC-02 = RESOLVED` and `SC-01 = RESOLVED` remain accepted. `PC-01 = BLOCKED_EXTERNAL_APPROVAL` is unchanged; `SC-02 = BLOCKED_BY_PRIVACY_ONLY`, depending only on PC-01 / PRIVACY. `CLD-ADM-PRIVACY-001 = BLOCKED`; `CLD-ADM-CONTROL-001 = BLOCKED`; EVALUATION/PROVIDER OPEN; ADMISSION BLOCKED.

Effective 39 gates: 6 SATISFIED, 0 PARTIALLY_SATISFIED, 5 OPEN, 3 BLOCKED, 25 NOT_RUN. 6.1/6.2 PASS; 11.1C PARTIAL; 6.2D/6.3 NOT_RUN and blocked. Remaining 6.3 predecessors: PRIVACY, CONTROL, EVALUATION, PROVIDER. No runtime/Android/Recovery/PR86 work, AWS runtime calls, audio upload or inference. Next 11.1C prerequisite only: qualified Privacy/Legal actual-scope approval PC-01, then SC-02 dependency readback; not automatically started.

---

## Current 11.1C owner/AWS reassessment — 28 September 2026

Source baseline: `cccf85f982436af0bf9675d738dfe2dd61308842`. [Current policy v0.2](design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_2.md),
[contract v0.2](contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_2.json) and
[ADR-0011](adr/ADR-0011-alpha-transcribe-privacy-retention-key-custody.md) apply **OD-11C-01..12 = APPROVED_BY_PROJECT_OWNER**.
This is the sole current successor to immutable v0.1; older sections below are historical snapshots.

**11.1C = PARTIAL / OWNER_DECISIONS_APPLIED_PC02_SC01_RESOLVED_RESIDUAL_PRIVACY_AND_RETENTION_PREREQUISITES**.
PC-02 (current candidate AWS fact package) and SC-01 (accepted reviewed bounded architecture) are RESOLVED.
Remaining: PC-01 BLOCKED_EXTERNAL_APPROVAL; RT-01/RT-02 PARTIALLY_RESOLVED;
SC-02 BLOCKED_BY_FROZEN_CRITERION (PRIVACY/RETENTION dependencies).

Closed internal invitation-only Alpha; one region eu-central-1; Amazon Transcribe candidate only.
Local original until explicit deletion, automatic retention OFF. Cloud audio immediate post-success cleanup
and <=24 hours maximum; durable DORA transcript separate. Explicit customer S3 input/output cleanup,
no long-term/archival/cross-region audio copies or deleted-audio versions; mandatory effective Transcribe opt-out.
Opt-out effective-policy/account/runtime verification NOT_RUN. No E2EE or 24-hour provider-internal erasure guarantee.
90-day job-record automatic expiry is not a mandatory minimum: terminal jobs must be deleted earlier.

Qualified privacy/legal actual-scope approval remains PC-01. RT-01 now concerns only undocumented internal-provider
audio deadline compatibility and enumerated DORA log/record/failed-output periods; RT-02 concerns non-audio copy/
restore horizons and tombstone/receipt retirement. No broad absent-owner-approval or quality benchmark blocker retained.
Accepted RDY-012/013 architecture is documented; applicable RDY-011/013/018 cross-gate residuals remain explicit.

PRIVACY BLOCKED; RETENTION PARTIALLY_SATISFIED; CONTROL BLOCKED. EVALUATION OPEN; PROVIDER OPEN; ADMISSION BLOCKED.
39-gate totals unchanged: 5 SATISFIED / 1 PARTIALLY_SATISFIED / 5 OPEN / 3 BLOCKED / 25 NOT_RUN.
6.1/6.2 PASS; SCOPE/GAPS SATISFIED; AWS SELECTED_ALPHA_PLATFORM; AWS_TECHNICAL_ADMISSION NOT_RUN;
6.2D BLOCKED / NOT_RUN; 6.3 BLOCKED / NOT_RUN; FIRST_REAL_AUDIO_ADMISSION NOT_READY;
CLOUD_RUNTIME NOT_IMPLEMENTED; CLOUD_ALPHA_ACCEPTANCE NOT_RUN. Frozen 6.2C and historical acceptance unchanged.

Next: only PC-01 qualified scope approval, RT-01 exact remaining period/provider evidence and RT-02 non-audio
restore/ledger horizon; then SC-02 dependency reconciliation. No next task, runtime, device campaign or merge started.


## Current 11.1C policy/design evidence — 27 September 2026

[Policy/design package](design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_1.md) and
[machine-readable evidence](contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_1.json)
bind the accepted repaired baseline `5015e1cd7db7c37315dba41503f6962e3db82408`.

**11.1C = PARTIAL / DESIGN_PACKAGE_COMPLETE_WITH_BLOCKING_APPROVAL_AND_POLICY_DECISIONS.**
Data-flow inventory, RU/EN unavailable-state disclosure, 56 artifact/holder lifecycle cells,
installation/credential/key-custody and consent/deletion-ledger proposals, and four RDY risk
dispositions exist. They supply no qualified Privacy/Legal/Security approval or runtime proof.

| Gate | Before | Current | Exact remaining evidence |
|---|---|---|---|
| CLD-ADM-PRIVACY-001 | BLOCKED | BLOCKED | PC-01 qualified actual-scope approval; PC-02 provider/service/location/terms/subprocessor/data-use fact pack |
| CLD-ADM-RETENTION-001 | PARTIALLY_SATISFIED | PARTIALLY_SATISFIED | RT-01 approved periods/triggers; RT-02 backups/replicas/receipt coverage and suppression horizon |
| CLD-ADM-CONTROL-001 | OPEN | BLOCKED | SC-01 accepted bounded design/credential parameters/key-custody ADR; SC-02 PRIVACY and RETENTION dependencies |

Existing local retention remains until explicit deletion; automatic retention OFF, numeric catalog
unavailable. No Cloud TTL, legal entity, region, qualified signature or identity provider invented.
The user-visible 0.1 notice has no enabled Cloud grant action; actual profile/approval gaps block it.
New design choices remain proposals pending SC-01; no owner DEC/ADR is silently superseded.

Effective 39-gate counts: **5 SATISFIED / 1 PARTIALLY_SATISFIED / 5 OPEN / 3 BLOCKED /
25 NOT_RUN**. Only CONTROL changes status relative to repaired 6.2. EVALUATION and PROVIDER
remain OPEN; ADMISSION remains BLOCKED and is the output of 6.3. All five 6.3 predecessors remain.
6.1/6.2 remain PASS; SCOPE/GAPS remain SATISFIED. Frozen 6.2C and 6.2 matrix/evidence are unchanged.
AWS SELECTED_ALPHA_PLATFORM; AWS_TECHNICAL_ADMISSION NOT_RUN; FIRST_REAL_AUDIO_ADMISSION NOT_READY;
CLOUD_RUNTIME NOT_IMPLEMENTED; CLOUD_ALPHA_ACCEPTANCE NOT_RUN; 6.2D and 6.3 BLOCKED.

**Next task: 11.1C owner/qualified Privacy-Legal-Security review of PC-01/02, RT-01/02,
SC-01/02 with scope-specific documentary evidence.** This package starts no further task.
Runtime/device tests NOT_RUN / NOT_REQUIRED; Recovery/Stage5/PR86 unchanged; no implementation,
campaign, AWS API, real audio or merge. Publication requires one docs-only commit, push, exact
remote re-fetch and existing Sheet changed-cell readback; final task report supplies the receipt.

Earlier sections below remain historical snapshots, including their former next-step wording.

## Current Alpha readiness 6.2 — 27 September 2026

[Versioned disposition](stage0/DORA_ALPHA_READINESS_GAP_DISPOSITION_V0_1.md),
[machine matrix](contracts/DORA_ALPHA_READINESS_GAP_DISPOSITION_V0_1.json) and
[current additive gate evidence](evidence/alpha-readiness-6.2-closeout-v0.1.json)
supersede only the prospective current state in earlier snapshots below.

`6.1 = PASS / ALPHA_SCOPE_AND_SUPPORTED_ENVIRONMENT_FROZEN` remains accepted.
`6.2 = PASS / ALPHA_READINESS_GAPS_DISPOSITIONED` and `CLD-ADM-GAPS-001 = SATISFIED`
close disposition only, conditional on this atomic docs commit being pushed, exact remote HEAD
confirmed and all changed Sheet cells read back. Final task report and Sheet carry that receipt.

Capture: reuse bounded Samsung observations; clean 60-minute screen-off/POCO evidence required
at 8.6, with format/lifecycle/measurement decisions before affected code. VAD: reuse host mechanics;
artifact/profile/governed-corpus admission before 8.4, physical acoustic/realtime proof at 8.4/8.6.
Search: reuse 10k/1M mechanics/observations; product dependency/schema/protocol admission before
10.2, actual-build correctness/latency/storage/update and version/deletion acceptance at 10.2/10.3C.
Battery: comparator only; capture-first scheduling/protocol before affected code, physical
capture/VAD energy at 8.6 and integrated energy/thermal before 12.1/12.4. Offline is split into
recording, preservation, deferred state, persistent queue, reconnect, unauthorized upload,
optional package, installed Local ASR, no-account/no-GMS and OS/background evidence at 8/9/11/12.
No unresolved item is waived or assigned an unspecified future date; see exact matrix gates.

Recovery remains `0D.6 = ALPHA CLOSED / FULL OPEN`, reused without a rerun; product integration
is separately gated. Stage 5 remains `PASS / BOUNDED_ALPHA_LOCAL_ASR_CANDIDATE_ADMITTED`,
optional exact POCO/Small q8_0 scope; timestamp quality NOT_EVALUABLE, actual 16KiB runtime NOT_RUN.
All historical PoC DONE/INCONCLUSIVE/FAIL/BLOCKED/TODO and frozen 6.2C bytes remain unchanged.

`AWS = SELECTED_ALPHA_PLATFORM`; `AWS_TECHNICAL_ADMISSION = NOT_RUN`;
`FIRST_REAL_AUDIO_ADMISSION = NOT_READY`; `CLOUD_RUNTIME = NOT_IMPLEMENTED`;
`CLOUD_ALPHA_ACCEPTANCE = NOT_RUN`; `CLOUD_IMPLEMENTATION_ADMISSION = NOT_READY`.
`6.2D = BLOCKED`; `6.3 = BLOCKED`. Remaining 6.3 predecessor gates are PRIVACY, RETENTION,
CONTROL, EVALUATION and PROVIDER (all `CLD-ADM-...-001`); ADMISSION is the output of 6.3.
Only GAPS changes relative to 6.1: **5 SATISFIED / 1 PARTIALLY_SATISFIED / 6 OPEN /
2 BLOCKED / 25 NOT_RUN**. All first-audio gates and all 14 Cloud exits remain mandatory.

**Next task: 11.1C policy/design — resolve CLD-ADM-PRIVACY-001, CLD-ADM-RETENTION-001
and CLD-ADM-CONTROL-001 prerequisites.** Then separately 6.2D and 6.3. Nothing is started here.
Stage 6 / Group B remain in progress. No runtime/device campaign, Android/runtime source edit,
main change, PR #86 operation or merge. Runtime/device tests = `NOT_RUN / NOT_REQUIRED`.

Earlier dated sections below are preserved historical snapshots, including their old next-step
wording. The matrix is the explicit prospective first-Alpha applicability/sequencing overlay;
it does not pass the broader full-MVP PoCs or authorize implementation before 6.3.

## First Alpha scope 6.1 and AWS owner selection — 27 September 2026

[Scope MD](product/DORA_ALPHA_SCOPE_V0_1.md), [scope JSON](contracts/DORA_ALPHA_SCOPE_V0_1.json),
[owner decision](stage0/DORA_AWS_SELECTED_FOR_ALPHA_OWNER_DECISION_V0_1.md), [ADR-0010](adr/ADR-0010-first-alpha-scope-and-aws-platform.md) and
[current gate evidence](evidence/alpha-scope-6.1-closeout-v0.1.json) are the current prospective authority.

`6.1 = PASS / ALPHA_SCOPE_AND_SUPPORTED_ENVIRONMENT_FROZEN` and
`CLD-ADM-SCOPE-001 = SATISFIED` (specification evidence only). Publication closure requires
this atomic docs commit to be pushed, exact remote HEAD confirmed and the existing Alpha Sheet
updated/read back; the final task report and Sheet are the exact-commit closure receipt.

`AWS = SELECTED_ALPHA_PLATFORM`: owner-approved one-platform Alpha selection after
`18.1A = PASS / MARKET_AND_PRICING_DATASET_READY` and
`18.1B = PASS / TCO_AND_ALPHA_PROVIDER_ECONOMICS_READY` (owner-confirmed independent review;
live Sheet evidence is referenced in the owner record). Some multi-provider choices have lower
raw cash TCO; AWS is not claimed cheapest overall. Provider-neutral ASR/diarization/LLM/embeddings
and practical storage/queue/job boundaries remain required; AWS-specific types stop at adapters.

IN: user-started foreground microphone recording with Pause/Resume/Stop, durable original audio,
accepted bounded Recovery and VAD/chunk contracts, RU/EN Cloud ASR, offline recording, consent,
transcript/version/provenance, timestamps only in proven scope, edits, original-audio reprocessing,
local history/lexical search, explicit active-version export, deletion/privacy and Cloud safety controls.
OPTIONAL: exact bounded Stage 5 Local candidate; no forced Local install for Cloud-only.
DEFERRED: speaker/diarization flows, protocol, decisions, tasks, summaries, LLM, embeddings/semantic
search, voice biometrics, connectors, team sync, billing and public release. Stages 13–19 remain;
Stage 18 Cloud services/operations are necessary before Stage 12 Alpha acceptance.
Support: POCO M5 / Android 14 API 34 / arm64-v8a / RU and EN only. Mixed RU/EN is not guaranteed;
minSdk 28 is compatibility only; Stage 5 timestamps NOT_EVALUABLE and actual 16KiB runtime NOT_RUN.

`AWS_TECHNICAL_ADMISSION = NOT_RUN`; `FIRST_REAL_AUDIO_ADMISSION = NOT_READY`;
`CLOUD_RUNTIME = NOT_IMPLEMENTED`; `CLOUD_ALPHA_ACCEPTANCE = NOT_RUN`.
6.2D remains BLOCKED / technical admission NOT_RUN; exact AWS service/configuration/model/region
remain unadmitted. 11.1C remains the privacy/retention/control policy-design prerequisite.
6.3 remains BLOCKED by GAPS, PRIVACY, RETENTION, CONTROL, EVALUATION and PROVIDER (all
`CLD-ADM-…-001`); ADMISSION is its own output. Effective gate counts: 4 SATISFIED, 1 PARTIAL,
7 OPEN, 2 BLOCKED, 25 NOT_RUN. Only SCOPE changes; frozen 6.2C and historical evidence remain intact.

**Next task: 6.2 — close remaining Alpha readiness/gap disposition.** Not executed here.
Stage6/Group B stay in progress; no implementation, real Cloud audio, new campaign, Recovery,
main change, PR #86 change or merge. Earlier dated entries below are historical snapshots and
retain their original next-step/provider wording; the current overlay supersedes only prospective status.


## Alpha Cloud admission gates 6.2C — 27 September 2026

[Human contract](contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.md) and
[machine matrix](contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.json) freeze the complete gate set.
`6.2C = PASS / CLOUD_ALPHA_ADMISSION_GATES_FROZEN` is the closure result only after this atomic
docs commit is pushed, remote HEAD confirmed, and the existing Sheet updated/read back for it.
Until that external receipt exists, stage closure is pending; contract approval is not runtime admission.

Cloud implementation admission = **NOT READY / BLOCKED BY OPEN ADMISSION GATES**.
First real Cloud audio admission = **NOT READY**. Cloud Alpha acceptance = **NOT_RUN / NOT READY**.
Provider/model = **NOT SELECTED**. Cloud runtime = **NOT IMPLEMENTED**; runtime tests = **NOT_RUN**.

6.3 remains BLOCKED. Minimum predecessor blockers (all prefixed `CLD-ADM-`, suffixed `-001`):
SCOPE (6.1), GAPS (6.2), PRIVACY and RETENTION (11.1C policy), CONTROL (11.1C/BE-AUTH design),
EVALUATION and PROVIDER (live 6.2D per-function admission). ADMISSION is 6.3's own output.
Full Alpha runtime tests are subsequent acceptance gates, not prerequisites to writing code.
Policy/design work in later-numbered ownership lanes precedes 6.3; implementation follows it.

Immediate next task: **6.1 — formal Alpha scope/support decision** (`CLD-ADM-SCOPE-001`), required
to determine applicable gaps and per-function 6.2D admission. Do not execute it in 6.2C.
The live Sheet's newly present 6.2D is retained: ASR plus any other AI actually included by 6.1
needs its own admitted provider/model or explicit local/deferred disposition; no single vendor default.
CLOUD-01 stays closed for boundary selection; CLOUD-02 stays blocked; CLOUD-03 stays NOT_RUN.

Stage 5 remains PASS / BOUNDED_ALPHA_LOCAL_ASR_CANDIDATE_ADMITTED, exact POCO scope only.
Recovery, PR #86 and historical evidence are unchanged. Earlier dated sections below are historical
snapshots; this section supersedes their prospective “6.2C next” planning, not their measurements.

## Alpha Cloud execution boundary 6.1C — 27 September 2026

Owner-approved architecture: `6.1C = DORA_CONTROLLED_BACKEND_SELECTED`, recorded in
[ADR-0009](adr/ADR-0009-alpha-cloud-execution-boundary.md). DORA backend is the mandatory
Alpha Cloud/file-ASR control plane; original audio passes through authorized DORA-controlled
storage and a replaceable `CloudAsrProvider`. Provider/model remain NOT SELECTED;
Cloud runtime/backend remain NOT IMPLEMENTED and runtime acceptance NOT_RUN.

Closure result `PASS / DORA_CONTROLLED_BACKEND_SELECTED` requires this commit to be pushed
and the existing Alpha Sheet updated/read back; independent verification remains required.
The immediate next architecture gate is `6.2C — Cloud readiness / admission gates` (PLANNED).
7.2C, 7.2E, 9.2C and 18.1C remain NOT STARTED; 11.1C/CLOUD-02 and runtime gates remain open.
The three approved ASR contracts, Recovery/PR #86 and all historical evidence are unchanged.

## ASR Stage5 terminal closeout 5.6D.3 — 27 September2026

Stage5 `PASS / BOUNDED_ALPHA_LOCAL_ASR_CANDIDATE_ADMITTED`; POC-ASR-001 `PASS / BOUNDED_ALPHA_ONLY`.
Small q8_0 `PASS / ACCEPTED_FOR_BOUNDED_ALPHA_ON_POCO_M5`. One campaign invocation, 48 primary attempts, zero retries/replacements;
RU 24/24 (36/191 = 18.848168%, PASS), EN 24/24 (31/333 = 9.309309%, PASS).
Cleanup VERIFIED; no historical rescoring, tuning, threshold/normalizer/runtime change or next model campaign.
5.6D.1 data PASS and5.6D.2 admission PASS remain immutable. All48 new cases are consumed.
Recovery0D.6 and PR#86 unchanged. Previous entries below remain byte-identical.
Next: Stage6 may consume this bounded POCO admission while preserving all limitations; do not generalize it to production.
See [terminal report](stage0/DORA_ASR_SMALL_Q8_STAGE5_CLOSEOUT_STAGE0_V0_1.md)
and [sanitized evidence](evidence/poc-asr-001/asr-small-q8-stage5-closeout-stage0-v0.1.json).

## ASR SMALL q8 admission and operator source 5.6D.2 — 27 September 2026

`PASS / SMALL_Q8_ARTIFACT_INIT_OPERATOR_READY`. Exact small q8_0 artifact264464607bytes, static479tensors,
one model download, one init SUCCESS, one no-model lifecycle hold/cancellation probe; cleanup VERIFIED.
ASR/whisper_full0, corpus/reference transfers during init0, measured campaign invocations0.
Exact accepted ARM82 reuse and16KiB packaging checks PASS; actual device pages4096, actual16KiB runtime NOT_RUN.
98 targeted host tests (15 new), Stage00 checks7/7 and Gradle197tasks PASS.
Operator SOURCE ready. Immutable execution envelope is sealed only AFTER this operator commit and pinned in5.6D.3;
it is not claimed frozen here. Stage5 remains NOT_PASS; POC-ASR-001 BLOCKED / NOT_READY pending measured gates.
Historical results, Recovery0D.6 and PR#86 unchanged. Previous entries below remain byte-identical.
See [5.6D.2 report](stage0/DORA_ASR_SMALL_Q8_ADMISSION_STAGE0_V0_1.md)
and [sanitized evidence](evidence/poc-asr-001/asr-small-q8-admission-stage0-v0.1.json).

## ASR expanded holdout 5.6D.1 — 27 September 2026

`PASS / UNTOUCHED_EXPANDED_HOLDOUT_AVAILABLE`. Owner-authorized new RU CV27 test + retained EN SPS5 train:
eligible RU10326/EN1112, provider participant keys RU2233/EN224. Frozen exactly RU24+EN24;
all48 immediately consumed, known historical source/audio/provider-key overlap0.
Cross-corpus real-world participant equality UNKNOWN; no identification or inferred mapping.
Exact references bound before unchanged normalization; all selected normalized counts>=1.
No model download/freeze, device operation or inference in this data phase.
Stage5 remains NOT_PASS pending the already-authorized q8 artifact/init/operator/campaign closeout.
Historical results, Recovery0D.6 and PR#86 unchanged. Previous entries below remain byte-identical.
See [5.6D.1 report](stage0/DORA_ASR_EXPANDED_HOLDOUT_STAGE0_V0_1.md)
and [sanitized evidence](evidence/poc-asr-001/asr-expanded-holdout-stage0-v0.1.json).

## ASR train holdout availability audit — 26 September 2026

`BLOCKED / INSUFFICIENT_UNTOUCHED_HOLDOUT_AFTER_TRAIN_AUDIT`.
Same retained SPS5.0 release/archives, owner-authorized provider train only: complete RU0/EN1569;
after all exclusions RU0/EN1112, eligible participants RU0/EN224. Empty provider split is not train.
All144 historical sources/audio digests and55 participants remain excluded, including unexecuted selections.
No ranking, selection, holdout manifest, q8_0 experiment freeze, inference, device execution or download.
Prospective reference rule remains NORMALIZED_REFERENCE_TOKEN_COUNT >=1 with the unchanged normalizer.
Historical ARM82 RU73/349=20.916905% VALID_FAIL; EN3/24 NOT_EVALUABLE; BASE/SMALL unchanged.
Stage5 remains NOT_PASS; POC-ASR-001 BLOCKED / NOT_READY. Recovery0D.6 and PR#86 untouched.
Next: STOP; Project Owner must separately authorize any further data-authority work preserving every isolation rule.
No q8_0 freeze or execution follows from this audit. Historical entries below remain byte-for-byte intact.
See [train audit report](stage0/DORA_ASR_TRAIN_HOLDOUT_AVAILABILITY_STAGE0_V0_1.md)
and [sanitized evidence](evidence/poc-asr-001/asr-train-holdout-availability-stage0-v0.1.json).

## ASR ARM82 quality forensics — 26 September 2026

`BLOCKED / INSUFFICIENT_UNTOUCHED_HOLDOUT`. Read-only reconciliation preserves RU24/24,
73/349=20.916905% FAIL (integer gate maximum69, four errors over); EN3/24 and aggregate resources NOT_EVALUABLE.
Accepted ASR-SMALL-ARM82-01 VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE remains unchanged.
Errors concentrate in P1 (63/73); EN04's bracket-only raw reference normalizes empty.
Prospective eligibility now requires NORMALIZED_REFERENCE_TOKEN_COUNT >=1 using the exact frozen normalizer before ranking.
All144 selected records/audio digests and55 participants are excluded, including all unexecuted selections.
Same admitted dev authority leaves RU0/EN137: no new holdout selected, no successor experiment frozen.
Small q8_0 is the conditional next artifact preference only. No inference, rerun, tuning, threshold/model/decoding change.
Stage5/GroupB remain NOT_PASS; POC-ASR-001 BLOCKED / NOT_READY. Historical sections below remain intact.
Next: owner-authorized narrow data-source/partition expansion while preserving participant isolation and corrected eligibility.
See [forensic report](stage0/DORA_ASR_ARM82_QUALITY_FORENSICS_STAGE0_V0_1.md)
and [sanitized evidence](evidence/poc-asr-001/asr-arm82-quality-forensics-stage0-v0.1.json).


## ASR-SMALL-ARM82-01 measured terminal — 26 September 2026

`INCOMPLETE / BOUNDED_SMALL_POCO_CAMPAIGN_STOPPED`.
Candidate: `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE`. One API invocation, 28 primary attempts,
zero retries; RU 24/24, EN 3/24; cleanup VERIFIED.
Quality FAIL; resources NOT_EVALUABLE.
Owner-authorized data revision uses the same exact SPS5.0 dev partition for both languages:
eligible RU51/EN408 after all exclusions, selected RU24/EN24, zero prior participant/source/audio overlap.
Source/materialization/operator/binary identities were frozen before inference.
Exact ARM82 build and preflight PASS; unchanged SMALL q5_1, CPU4, decoding and thresholds.
The old test-only blocker and historical SMALL VALID_FAIL remain unchanged.
ASR-SMALL-ARM82-01 is now TERMINAL / MEASURED; Stage5/GroupB NOT_PASS;
POC-ASR-001 BLOCKED / NOT_READY. Historical 5.6C.2B remains INCOMPLETE.
Recovery stays 0D.6 = ALPHA CLOSED / FULL OPEN; PR #86 OPEN, DRAFT, UNMERGED, untouched.
Next: Assess the RU quality rejection and normalized-empty reference stop, then prospectively freeze one next candidate experiment and reference-eligibility correction on a new untouched holdout; do not rerun this campaign.
See [measured result and revised data authority](stage0/DORA_ASR_SMALL_ARM82_01_MEASURED_STAGE0_V0_1.md)
and [aggregate evidence](evidence/poc-asr-001/asr-small-arm82-01-measured-stage0-v0.1.json).
All earlier dated sections remain historical evidence.


## ASR-SMALL-ARM82-01 execution — 26 September 2026

`BLOCKED / INSUFFICIENT_FRESH_HOLDOUT_AFTER_PARTICIPANT_EXCLUSION`.
Physical POCO M5 ISA admission PASS: ASIMD, FPHP, ASIMDHP and ASIMDDP present.
Exact admitted source/test metadata leaves at most RU 8 and EN 69 after verified exclusions;
RU has at most 9 records before other eligibility checks after lawful prior-participant exclusion.
The frozen minimum is 24 per language. No selection/materialization, ARM82 build or campaign
was performed; API invocations 0/1, attempts/retries/inference 0, evaluator not invoked.
Candidate disposition NOT_FORMED; quality/resources NOT_EVALUABLE. Probe cleanup VERIFIED.
This is a proven frozen data-contract blocker, requiring a prospective owner data-authority
revision before execution can resume; do not waive exclusions or substitute cases here.
Historical SMALL remains VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_RESOURCE_GATE.
ASR-SMALL-ARM82-01 BLOCKED / NOT_EXECUTED; Stage 5/Group B NOT_PASS;
POC-ASR-001 BLOCKED / NOT_READY; 5.6C.2B/5.6C.2 measured completion remains INCOMPLETE.
Recovery stays 0D.6 = ALPHA CLOSED / FULL OPEN; PR #86 OPEN, DRAFT, UNMERGED, untouched.
See [execution blocker report](stage0/DORA_ASR_SMALL_ARM82_01_EXECUTION_STAGE0_V0_1.md)
and [aggregate evidence](evidence/poc-asr-001/asr-small-arm82-01-execution-stage0-v0.1.json).
All earlier dated sections remain historical evidence.


## ASR SMALL resource postmortem and next experiment — 26 September 2026

`PASS / SMALL_RESOURCE_FAILURE_ASSESSED_NEXT_EXPERIMENT_FROZEN`.
All 19 retained attempts analyzed read-only. The decisive RU attempt 19 is the shortest
audio and fastest absolute inference: evidence supports systemic short-input RTF pressure,
not an isolated latency stall. Historical SMALL remains VALID_FAIL for its frozen resource gate.
Selected exactly one next experiment: `ASR-SMALL-ARM82-01`, same multilingual SMALL q5_1,
whisper.cpp v1.9.4 rebuilt for `armv8.2-a+fp16+dotprod`, CPU-only, 4 threads;
unchanged decoding/full context, normalizer/oracle and quality/resource thresholds.
Require a new untouched RU24+EN24 holdout excluding all BASE48 and consumed SMALL48.
Next: implement the isolated successor binding/build, materialize/preflight/freeze,
then one separately authorized physical POCO M5 campaign. No configuration search.
This task performed zero ASR inference, device execution, holdout reruns or tuning.
5.6C.2B/5.6C.2 measured completion remain INCOMPLETE; next experiment FROZEN / NOT_EXECUTED.
Stage 5/Group B remain NOT_PASS; POC-ASR-001 BLOCKED / NOT_READY.
Recovery stays 0D.6 = ALPHA CLOSED / FULL OPEN; PR #86 OPEN, DRAFT, UNMERGED, untouched.
See [postmortem and prospective contract](stage0/DORA_ASR_SMALL_RESOURCE_POSTMORTEM_NEXT_EXPERIMENT_STAGE0_V0_1.md)
and [decision evidence](evidence/poc-asr-001/asr-small-resource-postmortem-next-experiment-stage0-v0.1.json).
All earlier dated sections remain historical evidence.


## ASR 5.6C.2B measured SMALL terminal result — 26 September 2026

`5.6C.2B = INCOMPLETE / BOUNDED_SMALL_POCO_CAMPAIGN_STOPPED`.
Frozen candidate disposition: `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_RESOURCE_GATE`.
One real v0.3 API invocation; 19 primary attempts;
RU 19/24 and EN 0/24 completed;
zero retries. Quality NOT_EVALUABLE; resources FAIL.
Decisive resource failures: RTF_MAXIMUM.
The unchanged frozen evaluator produced the disposition; host-only preparation
corrections changed no model/data/source semantics, gates or attempt policy.
Frozen cleanup: VERIFIED; marker/journal/terminal and private audit evidence retained.
5.6C.2 measured completion is INCOMPLETE; 5.6C has a terminal outcome.
Stage 5 and Group B remain NOT_PASS; POC-ASR-001 remains BLOCKED / NOT_READY.
Recovery stays 0D.6 = ALPHA CLOSED / FULL OPEN; PR #86 stays OPEN, DRAFT, UNMERGED, untouched.
Next: Assess the measured resource rejection and decide a separately scoped, prospectively frozen candidate/runtime experiment. Do not rerun this campaign or tune on this holdout.
See the [measured campaign report](stage0/DORA_ALPHA_ASR_SMALL_MEASURED_CAMPAIGN_STAGE0_V0_1.md)
and [aggregate evidence](evidence/poc-asr-001/alpha-asr-small-measured-campaign-stage0-v0.1.json).
All earlier dated sections below remain historical evidence.


## ASR 5.6C.2A.3 private storage ACL diagnosis — 26 September 2026

`5.6C.2A.3 = PASS / PRIVATE_STORAGE_BOUNDARY_READY`.
The preceding v0.3 call stopped before its marker with PRIVATE_STORAGE_ACL_INVALID:
one API call, zero primary attempts, no journal/terminal aggregate, evaluator not
reached, quality/resources NOT_EVALUABLE, candidate disposition NOT FORMED.
Diagnosis identifies ACL_QUERY_FAILURE at the acceptance-root query: a Windows
PowerShell module-loading failure through the inherited Python environment.
The admitted protected owner-only acceptance root and all 111 descendants pass.
Process-local module-path isolation restores the unchanged v0.3 storage check.
Existing ACL changes: 0; one new empty future work boundary has its own protected
owner-only DACL. v0.3 is retained byte-identical; no v0.4 is created.
233 applicable host tests and Stage00 pass; 16 campaign-API tests were excluded.
All actual campaign/content/model/device counters in this task are zero.
**5.6C.2B remains BLOCKED / NOT_AUTHORIZED.** POC-ASR-001 stays BLOCKED / NOT_READY;
Stage 5 and Group B are not PASS. Recovery stays 0D.6 = ALPHA CLOSED / FULL OPEN;
PR #86 remains OPEN, draft, unmerged and untouched. See the
[ACL remediation report](stage0/DORA_ALPHA_ASR_SMALL_PRIVATE_STORAGE_ACL_REMEDIATION_STAGE0_V0_1.md)
and [aggregate evidence](evidence/poc-asr-001/alpha-asr-small-private-storage-acl-remediation-stage0-v0.1.json).



## ASR 5.6C.2A.2 execution-capable successor freeze — 26 September 2026

`5.6C.2A.2 = PASS / EXECUTION_CAPABLE_SMALL_SUCCESSOR_OPERATOR_FROZEN`.
The additive v0.3 operator composes unchanged v0.2 metadata authority with unchanged
v0.1 one-shot measurement/evaluation semantics. Its seven-field locator config
forbids caller bindings and SHA overrides; cases are derived from pinned metadata.
Future execution requires `OWNER_AUTHORIZED_5.6C.2B_V03` and a separately reviewed
commit/owner decision. The token in source is not authorization; CLI is information-only.

37 generated v0.3 tests, 136 SMALL host tests, 249 ASR host tests and Stage00 pass.
Actual private metadata reads: 0; all real content/model/device/execution/write
counters: 0. Historical v0.1/v0.2 files and BLOCKED 5.6C.2B evidence are unchanged.
**5.6C.2B = BLOCKED / NOT_AUTHORIZED**; no measured quality/resource result is added.
POC-ASR-001 remains BLOCKED / NOT_READY; Stage 5 and Group B are not marked PASS.
Recovery stays 0D.6 = ALPHA CLOSED / FULL OPEN; PR #86 remains draft and unmerged.
See the [execution operator report](stage0/DORA_ALPHA_ASR_SMALL_CAMPAIGN_EXECUTION_OPERATOR_STAGE0_V0_1.md)
and [aggregate evidence](evidence/poc-asr-001/alpha-asr-small-campaign-execution-operator-stage0-v0.1.json).


## ASR 5.6C.2A.1 metadata compatibility remediation — 26 September 2026

`5.6C.2A.1 = PASS / PRIVATE_MANIFEST_OPERATOR_COMPATIBILITY_REMEDIATED`.
The additive v0.2 operator distinguishes selected `samples`, materialized
`bindings`, transfer records and freeze. Actual controlled metadata passed all
four pinned identities and exact 48-case cross-binding (24 RU + 24 EN; 97 transfer
records). Metadata reads: 8; all content/model/device/execution/write counters: 0.
38 successor tests, 99 SMALL host tests, 212 ASR host tests and Stage00 pass.
The historical three-file operator and blocked result remain unchanged; prior
5.6C.2A PASS and 5.6C.2B BLOCKED retain their historical meanings.

**5.6C.2B remains BLOCKED / NOT_AUTHORIZED after this task.** No new execution
authorization is issued; the successor's measured API rejects every call.
POC-ASR-001 remains BLOCKED / NOT_READY; Stage 5 and Group B are not marked PASS.
Recovery remains 0D.6 = ALPHA CLOSED / FULL OPEN; PR #86 is draft and unmerged.
See the [remediation report](stage0/DORA_ALPHA_ASR_SMALL_CAMPAIGN_SCHEMA_REMEDIATION_STAGE0_V0_1.md)
and [aggregate evidence](evidence/poc-asr-001/alpha-asr-small-campaign-schema-remediation-stage0-v0.1.json).


## ASR 5.6C.2B pre-campaign schema blocker — 26 September 2026

`5.6C.2B = BLOCKED / FROZEN_OPERATOR_PRIVATE_MANIFEST_SCHEMA_MISMATCH`.
All three private metadata canonical identities match, but the exact admitted
materialized manifest lacks the top-level `samples` field required by the frozen
operator. Verification stopped before the campaign marker, model/audio/reference
reads or device access. Primary attempts: 0; completed RU/EN: 0/0; inference: 0.
Quality and resources are NOT_EVALUABLE; SMALL candidate disposition is
`INCONCLUSIVE / CAMPAIGN_EVIDENCE_INCOMPLETE`.

No retry, schema remapping, operator change or corpus modification occurred.
Separate compatibility-remediation scope and review are required before another
execution decision. Prior 5.6C.2A host/synthetic PASS remains historical evidence,
not proof of compatibility with the actual private package. POC-ASR-001 remains
BLOCKED / NOT_READY; Stage 5 and Group B are not marked PASS. Recovery remains
0D.6 = ALPHA CLOSED / FULL OPEN; PR #86 stays draft and unmerged. See the
[result report](stage0/DORA_ALPHA_ASR_SMALL_CAMPAIGN_RESULT_STAGE0_V0_1.md) and
[aggregate evidence](evidence/poc-asr-001/alpha-asr-small-campaign-result-stage0-v0.1.json).


## ASR 5.6C.2A SMALL operator/profile freeze — 25 September 2026

`5.6C.2A = PASS / SMALL_CAMPAIGN_OPERATOR_AND_EVALUATION_PROFILE_FROZEN`.
The additive SMALL evaluator binds the exact fresh acceptance/model/freeze and
unchanged decoding/measurement identities. Integer/exact-ratio quality and resource
predicates, one-primary-attempt policy, safe stop and pre-result dispositions are
frozen and validated with generated host fixtures. Historical BASE operators,
normalization, Java oracle, native runtime, gates and all earlier evidence remain
unchanged. Execution completion and candidate acceptance are separate results.

No private data, model bytes or device were accessed; model load, audio decode,
ASR inference, whisper_full and measured attempts are all zero. This preparation
does not establish SMALL quality, resource fitness or production readiness.
`5.6C.2B = NOT_STARTED / NOT_AUTHORIZED`; `POC-ASR-001 = BLOCKED / NOT_READY`.
Retention remains 2026-10-25. See the
[operator report](stage0/DORA_ALPHA_ASR_SMALL_CAMPAIGN_OPERATOR_STAGE0_V0_1.md) and
[aggregate evidence](evidence/poc-asr-001/alpha-asr-small-campaign-operator-stage0-v0.1.json).


## ASR 5.6C.1.1 exact recovery and init readiness — 25 September 2026

`5.6C.1.1 = PASS / EXACT_CORPUS_RECOVERED_FRESH_HOLDOUT_MATERIALIZED_SMALL_PREFLIGHT_READY`.
Both exact historical RU/EN archive bytes were recovered through owner-authenticated
official MDC downloads. The unchanged metadata CLI reproduces all prior digests;
exactly the previous 24 RU + 24 EN are materialized with matching audio/reference
hashes and a complete private pre-inference freeze. Exact SMALL and pinned native
artifacts pass hash checks; model-init-only execution on POCO M5 succeeds and
task-owned process/file cleanup verifies. ASR inference and whisper_full remain zero.

Resulting `5.6C.1 = PASS / FRESH_48_HOLDOUT_MATERIALIZED_SMALL_MODEL_INIT_PREFLIGHT_READY`.
`5.6C.2 = NOT_STARTED / NOT_AUTHORIZED`; `POC-ASR-001 = BLOCKED / NOT_READY`.
Historical BASE remains `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE`.
The 422 ms diagnostic initialization observation does not satisfy the campaign
cold-load gate. Retention deadline stays 2026-10-25. No selector, runtime, gate,
Recovery or production-admission change follows. Historical BLOCKED evidence remains
unchanged. See the [source recovery report](stage0/DORA_ALPHA_ASR_SMALL_HOLDOUT_SOURCE_RECOVERY_STAGE0_V0_1.md)
and [aggregate evidence](evidence/poc-asr-001/alpha-asr-small-holdout-source-recovery-stage0-v0.1.json).


## ASR 5.6C.1 holdout preparation — 25 September 2026

`5.6C.1 = BLOCKED / FRESH_HOLDOUT_SOURCE_UNAVAILABLE_ON_THIS_DEVICE`. The new exclusion-aware
metadata validator passes; pinned inventory/original48 authority matches. After
332 unique exclusions, 190 RU and 220 EN candidates remain; the deterministic
metadata selection is 24 RU + 24 EN. Materialized counts are 0/0: the owner confirms
the complete controlled corpus is on another device, while this host has only the
original48 transfer subset. Private metadata is retained under owner-only storage,
with deletion deadline 2026-10-25. Full pre-inference freeze and POCO SMALL init
preflight are not run; ASR inference remains zero.

`5.6C.2 = NOT_STARTED / NOT_AUTHORIZED`; `POC-ASR-001 = BLOCKED / NOT_READY`.
Historical BASE remains `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE`.
Source availability must be resolved within the existing retention authority before
materialization/freeze and device preflight can resume. No runtime/profile/threshold,
Recovery or production-admission change follows. See the
[5.6C.1 report](stage0/DORA_ALPHA_ASR_SMALL_HOLDOUT_PREFLIGHT_STAGE0_V0_1.md) and
[aggregate evidence](evidence/poc-asr-001/alpha-asr-small-holdout-preflight-stage0-v0.1.json).


## Alpha ASR 5.6B SMALL governance freeze — 25 September 2026

5.6B = PASS / SMALL_Q5_1_EVALUATION_DATA_AND_RESOURCE_GATES_FROZEN.
See the [approved governance contract](stage0/DORA_ALPHA_ASR_SMALL_EVALUATION_GOVERNANCE_STAGE0_V0_1.md)
and [machine-readable freeze](evidence/poc-asr-001/alpha-asr-small-evaluation-governance-stage0-v0.1.json).
The owner prospectively approves minimum necessary reuse of controlled Common Voice 5.0 RU/EN
archives/inventories for one future bounded SMALL acceptance campaign and its audit, with the
unchanged absolute deletion deadline 2026-10-25. If work cannot finish before it, stop and execute
the existing deletion obligation with receipts; no retention extension or current private access.

Fresh acceptance is exactly 24 RU + 24 EN after mandatory original-48 source/audio-digest,
prior evaluation/tuning, duplicate-audio and development-set exclusions. Freeze the unchanged
SHA-256 key family, bytewise tie-break, first 24 per locale and RU24-then-EN24 order. No tuning,
quality-based substitution or original-48 regression reuse is approved. Pool availability is unverified.
For SMALL on POCO M5 only: normalized aggregate WER RU <=20%, EN <=18%; all inference RTF gates
must pass (duration-weighted <=2.0, nearest-rank p95 <=4.0, maximum <=6.0); maximum cold load
<=15000000 us; observed PSS <=1610612736 bytes; sampled native heap <=1342177280 bytes.
End-to-end latency is diagnostic only. Require zero evidenced OOM, no SEVERE-or-worse thermal
status, complete telemetry with existing valid-gap rules, complete attempts and no quality retries.
600/605-second timeout/alarm remain safety controls, not performance budgets.

All private corpus/reference/result access, decode, model load, inference, device and subset
materialization counts are zero. No model bytes read. These are approved rules, not measured PASS.
5.6A/5.6A.1 admission and historical BASE rejection remain unchanged. Earlier pending-decision and
blocked-attempt sections remain historical; this amendment updates only current governance truth.
POC-ASR-001 stays BLOCKED / NOT_READY; no production/all-device support claim. 5.6C is
NOT_STARTED / NOT_AUTHORIZED. No PR/merge; PR #86, Recovery and production code remain untouched.


## Alpha ASR 5.6A.1 controlled model storage remediation — 25 September 2026

5.6A.1 = PASS / CONTROLLED_MODEL_STORAGE_ESTABLISHED_SMALL_Q5_1_BYTES_VERIFIED.
Current 5.6A = PASS / SMALL_Q5_1_ARTIFACT_ADMITTED_PRE_RUN_PACKAGE_READY.
See the [remediation report](stage0/DORA_ALPHA_ASR_NEXT_CANDIDATE_STORAGE_REMEDIATION_STAGE0_V0_1.md)
and [byte/storage evidence](evidence/poc-asr-001/alpha-asr-next-candidate-storage-remediation-stage0-v0.1.json).
The explicitly authorized replacement owner-only LOCAL_PRIVATE_CONTROLLED_STORAGE holds exactly
one ggml-small-q5_1.bin, 190085487 bytes, from HF revision f281eb45af861ab5e5297d23694b7d46e090c02c.
Complete acquisition and post-promotion SHA-256 both equal
ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb. Static GGML parsing verifies
multilingual SMALL/q5_1 and all 479 tensor names/shapes/types with exact EOF. Pinned whisper.cpp
927cfce34f31707e17f2bff35c349632fb9e2c3a is unchanged; no static incompatibility was demonstrated.

The preceding blocked 5.6A attempt remains immutable history; this additive result resolves only
its model-storage/actual-byte blocker. Artifact admission is not quality/device/production PASS.
Private corpus/reference/hypothesis/result access, audio decode, inference, native model load,
device execution and new subset materialization are all zero. Original BASE remains VALID_FAIL /
REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE. Historical 5.2–5.5, normalizer/oracle/runner/native code,
production allowlist, Recovery and PR #86 remain unchanged; no PR or merge.
Data reuse/retention, fresh 24 RU + 24 EN holdout and numeric RTF/PSS/native-heap decisions remain
pending owner approval. Private-data deletion deadline remains 2026-10-25, without extension.
POC-ASR-001 remains BLOCKED / NOT_READY. 5.6B and 5.6C are NOT_STARTED / NOT_AUTHORIZED.
The following dated amendments are preserved historical states, including the blocked first attempt.


## Alpha ASR 5.6A next-candidate admission / pre-run package — 25 September 2026

5.6A = BLOCKED / CONTROLLED_MODEL_STORAGE_UNAVAILABLE_ARTIFACT_BYTES_UNVERIFIED.
See the [admission and owner-decision package](stage0/DORA_ALPHA_ASR_NEXT_CANDIDATE_ADMISSION_STAGE0_V0_1.md)
and [sanitized evidence](evidence/poc-asr-001/alpha-asr-next-candidate-admission-stage0-v0.1.json).
The selected target is multilingual ggml-small-q5_1.bin at immutable HF revision
f281eb45af861ab5e5297d23694b7d46e090c02c, upstream size 190085487 bytes and expected SHA-256
ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb.
Public metadata, license and pinned-runtime source were reviewed. The existing controlled model
store is not established on this host; no model download occurred. Actual bytes/hash/header are
unverified, so artifact admission is blocked. Establish the existing model store (or separately
authorize a storage boundary), then complete byte/hash/non-inference checks in a later task.

Fresh untouched 24 RU + 24 EN acceptance data, exclusion of the original 48, and separate tuning
data if needed remain PROPOSED_NOT_APPROVED; no subset was materialized. Data reuse/retention and
numeric RTF/PSS/native-heap gates require explicit owner decisions before the next comparison.
Current retention remains ACTIVE_THROUGH_5_5_THEN_DELETE_WITHIN_30_CALENDAR_DAYS, completion
2026-09-25 and default deletion deadline 2026-10-25. No extension or follow-on reuse is approved.
Private-data access/audio decode/inference/device execution are all zero. The original BASE
candidate remains VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE; 5.4/5.5 are unchanged.
POC-ASR-001 stays BLOCKED / NOT_READY; quality/device/production admission is not granted.
5.6B and any new campaign are NOT_STARTED / NOT_AUTHORIZED. No PR/merge; PR #86 and Recovery untouched.
The following dated amendments remain historical; this one updates only next-candidate status.


## Alpha ASR 5.5 evidence assessment amendment — 25 September 2026

5.5 = PASS / CURRENT_MODEL_REJECTED_NEXT_CANDIDATE_REQUIRED. See the
[assessment report](stage0/DORA_ALPHA_ASR_ASSESSMENT_STAGE0_V0_1.md) and
[sanitized aggregate assessment](evidence/poc-asr-001/alpha-asr-assessment-stage0-v0.1.json).
All 48 retained primary attempts (24 RU / 24 EN) independently reconcile through the unchanged
5.3A normalizer and Java oracle. No common evaluation defect was demonstrated. RU 30.5000%
(87/19/16; 400 reference tokens) and EN 18.4300% (44/8/2; 293 reference tokens) remain FAIL.
CURRENT_MODEL_QUALITY_RESULT = VALID_FAIL; the exact base-q5_1 model/runtime/decoding profile
is REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE. This is not a conclusion about other candidates.

Recommended next experiment: same whisper.cpp runtime plus one separately admitted higher-capacity
multilingual Whisper artifact, with a prospectively frozen protocol and valid held-out design.
No exact artifact selected, admission started, model downloaded, inference run or decoder tuned.
RTF/PSS/native-heap numeric thresholds remain PROPOSED_NOT_APPROVED; the owner proposal is not approval.
RETENTION_ACTION_REQUIRED: authorize any follow-on reuse/extension explicitly, or delete retained
corpus and derived private evidence by 25 October 2026 under the existing 30-calendar-day obligation.
No deletion during assessment and no silent retention extension.

5.1/5.2 retain bounded PASS; 5.3 retains PRE_5_4_PREPARATION_COMPLETE; original 5.4 evidence and
its campaign PASS/quality FAIL split remain unchanged. POC-ASR-001 remains BLOCKED / NOT_READY
because the current exact candidate failed both approved quality gates and alternate evidence is absent.
Fresh host/scoring/parity and required offline Android checks passed; CI NOT_RUN. Production admission
is false. PR #86, Recovery and production allowlist are untouched; no PR or merge.
The following sections are preserved historical states; this amendment supersedes only the earlier
5.5 NOT_RUN/model-disposition-deferred state and evaluates the existing retention obligation.


## Alpha ASR 5.4 bounded POCO campaign amendment — 25 September 2026

The owner authorized the exact original 5.1C transfer, frozen 48-clip physical POCO M5 campaign
and one atomic task-branch commit/push. See the [campaign report](stage0/DORA_ALPHA_ASR_CAMPAIGN_STAGE0_V0_1.md)
and [sanitized aggregate evidence](evidence/poc-asr-001/alpha-asr-campaign-stage0-v0.1.json).

5.1C_TRANSFER_IMPORT = PASS / VERIFIED_ON_LAPTOP (48/48 audio, references and original duration
bindings; no replacement, rematerialization or reselection). The missing-private-corpus blocker
is resolved. Synthetic full inference passed before code/config freeze and selected corpus access.
5.4 = PASS / BOUNDED_POCO_48_CLIP_CAMPAIGN_COMPLETE: 24 RU + 24 EN, 48 successful primary attempts,
zero retries/failures/invalid/timeouts/cancellations; device cleanup VERIFIED.

Quality remains separate from execution: RU normalized WER 30.5000%, gate FAIL;
EN normalized WER 18.4300%, gate FAIL. Complete-language S/D/I sums and
reference counts are in aggregate evidence. Raw WER is diagnostic only. RTF/PSS/native-heap
numeric gates are NOT_EVALUATED / PROPOSED_NOT_APPROVED. Thermal maximum is NONE,
with no SEVERE-or-worse observation. Timestamp quality is NOT_EVALUABLE; timestamp gates NOT_EVALUATED.

5.1 and 5.2 retain bounded PASS; 5.3 retains PRE_5_4_PREPARATION_COMPLETE and its unchanged
historical model-init/lifecycle semantics. 5.5 = NOT_RUN; full POC-ASR-001 remains BLOCKED / NOT_READY;
model disposition is deferred to 5.5. No production integration/admission or 16-KiB runtime claim.
99 ASR tests, scoped offline Android baseline checks and native/synthetic checks passed.
Only sanitized aggregates are published. Private corpus/evidence remain retained through 5.5.
PR #86, Recovery, production allowlist and all historical text/counts are unchanged. No PR or merge.
The following entries are historical states; this amendment supersedes only the earlier absence
of authority and evidence for bounded task 5.4.



## Alpha ASR 5.3C bounded runner preflight amendment — 25 September 2026

The owner authorized continuing the six preserved local files, exact r28c installation,
isolated physical-device model initialization and one atomic task-branch commit/push.
See the [runner/preflight technical record](stage0/DORA_ALPHA_ASR_RUNNER_PREFLIGHT_STAGE0_V0_1.md)
and [sanitized evidence](evidence/poc-asr-001/alpha-asr-runner-preflight-stage0-v0.1.json).

5.3C = PASS / BOUNDED_ASR_RUNNER_INTEGRATION_PREFLIGHT_READY.
The exact 5.2 model/source and 5.3B toolchain passed identity checks. Two isolated native builds,
actual ELF/dependency/16-KiB package checks, 44 runner tests and 22 unchanged 5.3A regressions
(including Java oracle/bridge) passed. On authorized Android 14/API34 arm64, one model context
initialization returned typed SUCCESS; live cancellation and timeout returned their typed outcomes.
All three outcomes survived reopen/replay without another native start. Device cleanup = VERIFIED.
Independent read-only review and final privacy/scope checks precede publication.

5.3A and 5.3B retain their existing bounded PASS. Overall 5.3 = PRE_5_4_PREPARATION_COMPLETE,
not full ASR PoC completion. 5.4 = NOT_AUTHORIZED; ASR_INFERENCE and WER = NOT_RUN;
RU/EN, RTF/PSS/native-heap/thermal = NOT_EVALUATED. Corpus/transcript access and published private
attempt rows/hypotheses = 0. Production integration/admission = false. Static 16-KiB compatibility
does not establish 16-KiB-device runtime support. Full POC-ASR remains BLOCKED / NOT_READY.
The following sections remain immutable historical states; this amendment supersedes only their
absence of the bounded runner/model-init proof. Historical counts and Recovery are unchanged.
No PR or merge; PR #86 is untouched. The production allowlist, native verifier, 5.3A contracts,
Java oracle and 5.3B evidence remain unchanged.


## Alpha ASR 5.3B isolated native candidate amendment — 24 September 2026

The owner's explicit resumed 5.3B scope authorized reproducible SDK/cache cleanup, installation
of NDK 28.2.13676358, and an isolated Android arm64-v8a/API28 candidate from the exact 5.2 pin.
See the [native build record](stage0/DORA_ALPHA_ASR_NATIVE_BUILD_STAGE0_V0_1.md) and
[sanitized evidence](evidence/poc-asr-001/alpha-asr-native-build-16k-stage0-v0.1.json).

5.3B = PASS / ISOLATED_WHISPER_CPP_ANDROID_16K_CANDIDATE_VERIFIED.
Two clean source builds and both package fixtures passed full five-library dependency closure,
ELF64/AArch64, all sixteen PT_LOAD 16-KiB checks, zipalign and the unchanged Dora verifier.
Reproducibility is NON_BIT_IDENTICAL_EXPLAINED: only DWARF/build-id differ; other original bytes
and comparison copies without debug/build-id match. NDK libc++ is separately pinned.
NATIVE_RUNTIME_BINARY = BUILT_ISOLATED_NOT_PRODUCT_ADMITTED. The fixture is a lib-only ZIP,
not an installable app or Android runtime/load proof. Production native allowlist is unchanged.

5.1/5.2/5.3A remain PASS within their existing bounded scopes. 5.3C = NOT_STARTED;
5.4 = NOT_AUTHORIZED; overall 5.3 is not complete. ASR_INFERENCE, MODEL_LOAD, DEVICE_RUNTIME,
real WER, RTF/PSS/thermal, POCO and Recovery = NOT_RUN; MODEL_QUALITY = NOT_EVALUATED.
Full POC-ASR-001 remains BLOCKED / NOT_READY / NOT_RUN; no production admission is granted.
The following sections are immutable historical states. This amendment supersedes only the
older absence of a bounded isolated native build; historical counts and Recovery are unchanged.
Publication is one atomic task-branch commit/push, no PR or merge; PR #86 is out of scope.


## Alpha ASR 5.3A host contract amendment — 24 September 2026

The owner's explicit 5.3A instruction freezes the
[bounded evaluation contract](stage0/DORA_ALPHA_ASR_EVAL_CONTRACT_STAGE0_V0_1.md) under
[ADR-0008](adr/ADR-0008-alpha-asr-evaluation-contract.md), with
[synthetic host evidence](evidence/poc-asr-001/alpha-asr-eval-contract-local-evidence-stage0-v0.1.json).
This additive entry supersedes only historical blanket absence of bounded data/model/text
contracts: 5.1 is PASS_BOUNDED_ALPHA_DATA_SCOPE_ONLY and
[5.2](stage0/DORA_ALPHA_ASR_MODEL_ADMISSION_STAGE0_V0_1.md) is
PASS / MODEL_ARTIFACT_AND_RUNTIME_SOURCE_PINNED_FOR_STAGE0_EVALUATION.

5.3A = PASS / VERSIONED_ASR_EVALUATION_CONTRACT_READY.
VERSIONED_TOKENIZATION_AND_NORMALIZATION_CONTRACT_NOT_APPROVED is closed only for the bounded
48-clip RU/EN pilot. Original I1 evidence and Java source remain unchanged. RU/EN normalized WER
gates stay 20%/18%; no mixed/noisy/speakerphone claim. Timestamp quality is NOT_EVALUABLE,
both timestamp gates NOT_EVALUATED, RTF/PSS/native-heap thresholds PROPOSED_NOT_APPROVED.

5.3B = NOT_STARTED; 5.3C = NOT_STARTED; overall 5.3 is not complete; 5.4 = NOT_AUTHORIZED;
ASR_INFERENCE = NOT_RUN; MODEL_QUALITY = NOT_EVALUATED. Full POC-ASR-001 stays
BLOCKED / NOT_READY / NOT_RUN. No model/audio/private-manifest access, native build, real WER,
Android/Gradle/device/POCO/Recovery execution, production admission or merge occurs.
Historical sections and backlog counts remain unchanged. Publication is one task-branch
commit/push without PR or merge, as explicitly instructed.



Updated: 19 August 2026
Baseline: `1be83e2940a09f7b23e33b4cdf3827de2690f3fd`
Stage 00 merge commit: `a4aae302f9033e5471f6759f513e7e351c375a72`
Stage 0A merge commit: `91b9916b01ff70f63d82412bafbed0d72307dbe1`
Repository: public `Monumentogram/DORA` by temporary owner-approved decision (ADR-0002)
Default branch: `main`
Stage 0B merge commit: `5e748469b22c6e7303fe6eb5f95394ea40088d84`
Stage 0C merge commit: `849d9d0406a619b334c9b707a4b6b42b34885b4b`
Active stage: `Stage 0D — REC-I2A/I2B integrated; REC-I3 implementation authority active`
Work branch: task-scoped; live Git/GitHub metadata is authoritative and no branch name is a stage invariant
Active PoC: `POC-RECOVERY-001`
Stage state: **REC-I2A GRAPH AND REC-I2B RUNTIME REVIEWED/SQUASH-MERGED; REC-I3 IMPLEMENTATION, NON-METRIC VERIFICATION AND CONDITIONAL MERGE PLUS BOUNDED NON-MEASURED E-SLOT CHECKS AUTHORIZED; RECOVERY PHASE A/MEASURED CAMPAIGN, PASS AND PRODUCTION ADMISSION BLOCKED**

Omi upstream-governance reconciliation: `GOV-OMI-001` /
`gov-omi-reuse-stage0-v0.1` is now `BLOCKED` after completing the owner-authorized
public-metadata-only Phase A. The immutable upstream identity is commit
`7d99abcc4efb9e46a5853b21fc01289e4b891837` / tree
`85db621ffd5dc5386bcbd7c87713cc69638be7e3`; the recursive 13,341-entry tree was untruncated,
release metadata was complete at 998/998, issue metadata complete/unique at 2,953/2,953, while tags
were capped at 1,000/1,470 and Pull Requests at 5,000/8,794. The five sanitized artifacts under
`docs/evidence/gov-omi-001/` retain `BLOCKED_RIGHTS` for exact rights and
`INSUFFICIENT_EVIDENCE` for behavior, reuse and hazard/fix conclusions. Source/blob/archive,
issue/PR body/comment, patch/diff, copying, execution, dependency admission and product
implementation remain forbidden. No Omi artifact entered Dora. Active Stage 0D, all Recovery
authority flags and every existing PoC verdict remain unchanged.

Stage 0 host-oracle publication reconciliation: the immutable
[closure index](evidence/stage0-host-oracle-publication-closure-2026-08-18.json) binds the exact
source heads, source-equal protected-squash merge trees, successful exact-head checks, successful
exact-main checks and original evidence blobs for merged Pull Requests #32–#37. These artifacts
cover only deterministic repository-owned pure-host semantics for Battery comparison, VAD frame
timing, Decision revision projection, synthetic-public manifest validation and Offline I1/I2. The
separate [Offline I2 independent advisory record](evidence/poc-offline-001/reviews/off-i2-integrated-synthetic-harness-independent-advisory-review-2026-08-18.json)
is non-formal and cannot substitute device, model, network, Legal, Security or production review.
The publication reconciliation leaves aggregate backlog truth `DONE=27`, `BLOCKED=18`, `TODO=9`,
`READY=0`; `POC-OFFLINE-001` remains `TODO / NOT_READY / NOT_RUN / NOT_AUTHORIZED` with all ten
readiness blockers open, while `POC-BATTERY-001`, `POC-VAD-001`, `POC-DECISION-001` and
`POC-DATA-001` remain `BLOCKED`. No PoC PASS, active-stage change or authority elevation follows.

Post-PR43 integration reconciliation: the additive
[main integration closure](evidence/stage0-post-pr43-main-integration-closure-2026-08-19.json) binds
the exact source/merge commits and source-equal squash-merge trees for PR #44, #45, #46, #48, #49,
#38, #50, #51, #52, #53, #54 and #55 through the historical integration cutoff
`671f594074b37bb2b5c8e4a4c1026de909acf339`. All twelve exact-head Android CI runs succeeded.
The first exact-main run after PR #38 remains recorded as a Recovery successor-dispatch failure;
PR #50 changed only the validator dispatch, preserved the REC-I2A/I2B runtime/evidence bytes and
returned exact-main CI to success. The companion
[host review closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json) reconciles the
current non-formal review/publication state for the bounded Data, Decision, VAD, Offline, VPN, ASR
and Diar synthetic host slices without rewriting their immutable pre-review fields.
This reconciliation is published from later `main` baseline
`9256db3d95fa20bc0d98aa35b48734ffeeb2623c` (PR #56). PR #56 is indexed only as a subsequent
non-product host-regression baseline (runner tool, workflow, scope and inventory) that revalidates
current-main VAD/Offline/Data synthetic host mechanics; it is not a thirteenth member of the
historical PR #44–#55 integration set and adds no PoC readiness/status elevation.

`OWNER-AUTH-BATCH-20260819-01` / `OD-15` now records the named current REC-I3 overlay:
`recI3ImplementationAllowed=true`, `recI3NonMetricVerificationAllowed=true` and
`recI3ConditionalMergeAllowed=true`, while `phaseAAllowed=false`, `executionAllowed=false`,
`measuredExecutionAllowed=false` and `productionAdmissionAllowed=false` retain their exact Recovery
campaign meaning. Its supplemental direct approval authorizes all available non-measured
functional, fault, compatibility and preflight checks on exactly pinned, available E slots after
task-specific prerequisites; Recovery preflight remains gated on successful REC-I3 and no Recovery
hard-kill/fault or Phase A campaign is authorized. Historical OD-14, DEC-044, Recovery records and
their contemporaneous false REC-I3 flags remain unchanged. Aggregate backlog truth remains
`DONE=27`, `BLOCKED=18`, `TODO=9`, `READY=0`; this reconciliation performs no device/emulator
execution and adds no PoC PASS/READY, dependency/model/product admission or production support
claim.

Design-governance reconciliation: `DES-EXPORT-001` / `des-export-interaction-v0.1` is contract
complete under `DEC-017`; no implementation/conformance/user-research, code/Figma/schema/provider
or export-execution claim follows. Active Stage 0D, `POC-DATA-001` blocking and every Recovery
authority flag remain unchanged.

Storage design-governance reconciliation: `DES-STORAGE-001` /
`des-storage-retention-delete-v0.1` is contract complete under `DEC-013`. It distinguishes
automatic audio retention, explicit audio-only deletion and whole-conversation deletion, with
orthogonal local/remote/cleanup states and synthetic acceptance fixtures. No Android/storage/schema,
deletion execution, conformance, user research, backend, Legal/Security or production-admission
claim follows. Active Stage 0D, `POC-DATA-001` blocking and every Recovery authority flag remain
unchanged.

POC-VPN governance reconciliation: prospective contract `poc-vpn-synthetic-api-stage0-v0.1` and its
machine record are contract complete. A [pure-host oracle implementation record](evidence/poc-vpn-001/contract-kernel-implementation-stage0-v0.1.json)
locates the dependency-free, non-network kernel, while the task-scoped [I2 implementation/evidence](evidence/poc-vpn-001/loopback-transport-implementation-evidence-stage0-v0.1.json)
locates only its hermetic numeric-loopback HTTP/fault subset. The exact I2 tree received a clean,
non-formal independent AI advisory review recorded in the [sanitized review artifact](evidence/poc-vpn-001/i2-implementation-advisory-review-2026-08-15.json), then preserved through protected squash merge and successful exact-main CI. There is no device, VPN, route, DNS,
TLS, provider, billing, Security, Legal or production PASS. `POC-VPN-001` remains `TODO`,
`NOT_READY`, `NOT_RUN` and globally `NOT_AUTHORIZED`; active Stage 0D and every Recovery authority
flag remain unchanged.

Post-PR43 POC-VPN additive publication fact: PR #51 merged the independently advisory-reviewed
[I3 host-hermetic fault-completion slice](evidence/poc-vpn-001/i3-host-fault-completion-local-evidence-stage0-v0.1.json).
Its immutable local record remains unchanged; source/merge-tree and exact-head/main CI truth is
indexed by the [post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json).
This host-only slice adds no device, VPN, route, DNS, TLS, provider, billing, Security, Legal or
production PASS and leaves `POC-VPN-001` `TODO`, `NOT_READY`, `NOT_RUN` and `NOT_AUTHORIZED`.

POC-OFFLINE governance reconciliation: prospective
[`poc-offline-readiness-stage0-v0.1`](stage0/DORA_MVP1_POC_OFFLINE_READINESS_CONTRACT_STAGE0_V0_1.md)
and its [machine record](evidence/poc-offline-001/readiness-contract-stage0-v0.1.json) complete only
the static readiness/call-surface contract. Merged I1/I2 host semantics and PR #48's independently
reviewed [I3 static call-ledger validator](evidence/poc-offline-001/i3-static-call-ledger-local-evidence-stage0-v0.1.json)
still classify only synthetic/static structure and do not prove an integrated local flow or runtime
absence of calls. The required device matrix, D4 no-GMS, approved local model, calibrated call monitor,
Offline-owned reconnect and OS-blocked execution remain open. `POC-OFFLINE-001` remains `TODO`,
`NOT_READY`, `NOT_RUN` and `NOT_AUTHORIZED`; active Stage 0D and every Recovery authority flag
remain unchanged.

## REC-I3 key-confirmation first-slice amendment — 5 September 2026

The additive [scope](stage0/DORA_MVP1_POC_RECOVERY_I3_KEY_CONFIRMATION_CONTROLLER_SCOPE_STAGE0_V0_1.md)
and [local evidence](evidence/poc-recovery-001/rec-i3-key-confirmation-controller-local-evidence-stage0-v0.1.json)
record a read-only key-confirmation controller in isolated `:poc:recovery`. It checks supplied
stored identity before opening the existing alias, preserves the effective KEY-04/KCF-07 boundary,
and performs no key creation, durable publication or device operation. The historical statements
below that no controller existed describe the pre-slice snapshot; the full REC-I3 harness,
bootstrap/writer, journal, quarantine, package verification and implementation review remain
unfinished. Confirmation-only host evidence does not complete REC-I3 or unlock Recovery preflight.
All ten active Recovery readiness blockers and the existing campaign/admission flags remain
unchanged. Further REC-I3 implementation continues under current OD-15 authority; no new activation
decision is required for its already authorized scope, and no PR merge is performed by this task.

## REC-I3 run-key bootstrap amendment — 5 September 2026

The additive [scope](stage0/DORA_MVP1_POC_RECOVERY_I3_RUN_KEY_BOOTSTRAP_SCOPE_STAGE0_V0_2.md)
and [local evidence](evidence/poc-recovery-001/rec-i3-run-key-bootstrap-local-evidence-stage0-v0.2.json)
record the next partial REC-I3 slice in isolated `:poc:recovery`. The new-run controller preserves
the exact KC01–KC13 order, creates its typed publication capability only after successful KC12
`endTransaction`, and treats KC13 evidence failure as a committed event gap rather than rollback.
The minimal Android `Os` adapter performs exclusive temp creation, complete short writes, file and
directory fsync, immediate final collision check and rename under an enforceable process-wide
same-run writer lease. The platform SQLite journal is PoC-only, schema-versioned, WAL/FULL,
zero-autocheckpoint and foreign-key configured, with no destructive migration fallback.

Host fault/order tests and Android compilation do not establish runtime filesystem, Keystore,
SQLite or kill-safety evidence. The full REC-I3 candidate writers, reconciliation, quarantine,
external controller, fresh exact graph/package/R8 review and accountable implementation review
remain unfinished. `fullRecI3Completed=false`, Recovery preflight remains locked, all ten active
readiness blockers remain open, and `POC-RECOVERY-001` remains `BLOCKED / NOT_READY`. No
device/emulator execution, preflight, hard-kill/fault campaign, measurement, PASS, production
admission, PR merge or future merged-main admission is performed or claimed.

## REC-I3 sequential microfile publication amendment — 5 September 2026

The additive
[scope](stage0/DORA_MVP1_POC_RECOVERY_I3_MICROFILE_PUBLICATION_SCOPE_STAGE0_V0_1.md),
[clarification](stage0/DORA_MVP1_POC_RECOVERY_I3_MICROFILE_PUBLICATION_CLARIFICATION_STAGE0_V0_1.md)
and [ADR-0003](adr/ADR-0003-unified-poc-recovery-journal-and-run-lease.md) record another partial
REC-I3 slice. The host-verified implementation publishes sequential encrypted microfiles and
cumulative authenticated manifests through exact `MICRO-P01`–`P21`, derives continuation only
from validated durable rows, and creates its capability only after confirmed transaction end.
Android storage/SQLite adapters compile as source evidence; host SQLite migration tests are not
device preflight. Independent/accountable implementation review remains pending.

The remaining REC-I3 harness, reconciliation, quarantine, stream candidate, external controller
and campaign work are unfinished. `fullRecI3Completed=false`; all ten active blockers,
`POC-RECOVERY-001` `BLOCKED / NOT_READY`, the Recovery preflight lock and every execution,
measurement and production-admission nonclaim remain unchanged.

## REC-I3 microfile reconciliation/quarantine amendment — 5 September 2026

The additive
[scope](stage0/DORA_MVP1_POC_RECOVERY_I3_MICROFILE_RECONCILIATION_QUARANTINE_SCOPE_STAGE0_V0_1.md)
and [ADR-0004](adr/ADR-0004-poc-recovery-reconciliation-and-quarantine.md) record a bounded partial
REC-I3 slice. It validates the confirmation root before unrelated candidate observations,
authenticates the longest gap-free microfile prefix with actual typed Tink operations, and records
stable quarantine intents before no-overwrite rename and both directory fsyncs. Schema v3 remains
PoC-only at the existing database path and rejects malformed predecessor schemas.

Host SQLite/fakes and compiled Android adapters do not prove Android runtime durability. The
remaining REC-I3 harness, stream candidate, external controller and campaign work are unfinished.
`fullRecI3Completed=false`; all ten active blockers, `POC-RECOVERY-001` `BLOCKED / NOT_READY`, and
every preflight, execution, measurement and production-admission nonclaim remain unchanged.

Independent review of the first reconciliation candidate required correction within this same
bounded slice. The additive
[correction scope](stage0/DORA_MVP1_POC_RECOVERY_I3_RECONCILIATION_REVIEW_CORRECTION_STAGE0_V0_1.md)
records production-owned observation, exact capability binding, authenticated fallback, pending
quarantine replay, immutable typed diagnostics, descriptor bounds and exact schema-v3 checks. The
second corrected head `5de34577295b5e5477970785f9472d51e21bfc43` received independent `REVISE`
review (P0/P1/P2 = 0/7/1). Author-local successor `a020944f0444edfcbabc4690d4a367b1cf9e83d7`
passed its reported host checks, but its exact independent review returned `REVISE` (P0/P1/P2 =
0/4/2). The later `ca2db88e0c6e53f346908bdc73b628beaf6c4ec4` review recorded `REVISE`
(P0/P1/P2 = 0/3/0), and its cursor addendum makes the effective disposition `REVISE`
(P0/P1/P2 = 0/4/0). Optional namespace/result retention, confirmed-Q05 truth, row framing and
production unique readback are closed prior findings. Independent review of
`bc4f4423b535982956575f5a3480c9361eab6378` returned `REVISE` (P0/P1/P2 = 0/2/0). The reviewed
successor `de735735ace6da3572c45dfdc58a8bbff98145b0` closed the four ca2 findings, both bc4
findings and the duplicate-check acceptance gap, then identified zero-byte inventory regression
and missing-final failure retention as two distinct current P1 findings. The current author
successor passed 224 Android host tests with the zero-byte inventory and primary/secondary
diagnostic composition matrix. Current governance checks are author evidence; both P1s remain
open until exact-candidate independent review, and accountable review remains pending. Historical
round-three and bc4 contextual acceptance assertions are superseded author claims. Host assertions do not establish
Android runtime durability; immediate lstat plus rename is not kernel-atomic no-replace evidence,
and no Recovery gate is unlocked.

## Stage 00 closure

- Stage 00 is complete.
- Pull Request #1 was merged into `main`.
- The merge commit is `a4aae302f9033e5471f6759f513e7e351c375a72`.
- The Stage 00 Android bootstrap, CI, governance baseline and validation tooling are present on `main`.
- Production functionality was not started in Stage 00.
- Build-only Stage 00 maintenance updates the Spotless Gradle plugin from 8.8.0 to 8.9.0. ktfmt
  0.63, Detekt 1.23.8 and the formatting/static-analysis policy remain unchanged. This does not
  change product/runtime behavior, the active stage, Recovery readiness or any authority flag.

## Stage 0A closure

- Stage 0A is complete.
- Pull Request #7 was merged into `main`.
- The merge commit is `91b9916b01ff70f63d82412bafbed0d72307dbe1`.
- Owner decisions `OD-01`–`OD-10`, privacy/IP/data governance, the device matrix, Gate Set `stage0-v0.1`, benchmark schema and PoC execution order are present on `main`.
- No technical PoC or production functionality was started in Stage 0A.

## Stage 0B closure

- Stage 0B is complete and merged.
- Pull Request #8 was merged into `main`.
- The merge commit is `5e748469b22c6e7303fe6eb5f95394ea40088d84`.
- The formal `POC-CAPTURE-001` result remains `INCONCLUSIVE` and is not reinterpreted by Stage 0C.
- No production capture, storage, ML, backend, account or cloud functionality was admitted.

## Stage 0C closure scope

Stage 0C is complete and Pull Request #10 was merged into `main` at
`849d9d0406a619b334c9b707a4b6b42b34885b4b`. The historical search evidence and verdict below are
closure context only; Stage 0D does not modify or rerun that PoC.

Stage 0C evaluated only `POC-SEARCH-001` in the isolated `:poc:search` contour. It uses a
versioned deterministic generator and fully synthetic text to evaluate Room 2.8.4 with
SQLite FTS4 at 10,000 conversations and exactly 1,000,000 transcript segments. The
pre-run dataset, query, mutation, warm-up, repetition, metric and Gate Set contracts are
frozen before the first full run. The generated database is temporary and must not enter
Git or Actions artifacts.

Host/emulator evidence records generated-scale correctness, schema, mapping, mutation,
logical rebuild and exploratory latency observations. It does not establish a gate-complete
host `PASS`: prospective `stage0-v0.2` now contains owner-approved Option B, but it was not measured
and cannot reclassify the historical campaign. Under `OD-13`, the exact external-artifact evidence
packet is `EVALUATION_APPROVED` only for internal synthetic Stage 0 research. A formal
`PASS` and device latency/support claim also remain unavailable until the required physical
D1-D3 search slices exist. FTS4 and the PoC schema are not production-admitted by this stage.

The final checkpointed host/emulator campaign completed the frozen 10k/1M measurements without a
repeat of the multi-hour query-plan failure. Exploratory p95 is `97.537984 ms` and p99 is
`144.481302 ms`; both independent builds passed all `61/61` correctness cases, the FTS4-driven
count and page plans were accepted without temporary page sorting, and mutation, deterministic
rebuild and cleanup checks passed. The original `PASS`/`GO` host conclusion is superseded by the
versioned 11 August review assessment. The current v5 closure result is `INCONCLUSIVE` with
recommendation `BLOCKED`; no measurement was changed or rerun. D1/D3 and the measured v0.2
campaign are deferred to separately authorized future scope.

## Stage 0D governance/readiness, REC-I2B integration and REC-I3 authority

Stage 0D first prepared the governance/readiness package for `POC-RECOVERY-001`. Owner record
`REC-I1-AUTH-20260813-01` now additionally permits only the isolated pure, non-metric common
contract foundation on exact base `9c4a798aa3c95877ff3f9aa66f18f94849b25cce`.
The v0.3 package at reviewed commit `c61603d30c01c72347aa205c247729ad534c2882` received four final
advisory findings, closed by historical v0.4. The v0.4 package at reviewed commit
`c3eae5c3fbe5cba6a96ad827441cfe4e3f1bfc55` received `REC-ADV-V04-001..004`, closed by historical
v0.5. GPT-5.6 Sol/OpenAI then reviewed commit `eca48ba62acd79007884710395cc40ea21a02611`
as a non-formal AI documentary advisory reviewer and returned `CHANGES_REQUIRED`. Proposed `DEC-044`
and owner-linked Gate Set/protocol `stage0-v0.6` now fix the prospective design semantics: exact
SHA-256-pinned v0.3/v0.4 public AES-GCM-HKDF Streaming AEAD and durable key confirmation with
`DURABLE_ONE_SEGMENT_LOOKAHEAD` versus sealed five-second `AES256_GCM_TINK_IV12_TAG16` microfiles
and exact authenticated binary manifest plus durable run-key confirmation. Gate Set/protocol v0.1, v0.2, v0.3, v0.4 and v0.5 remain 15 unchanged SHA-256-pinned superseded audit artifacts
and cannot govern future execution.

The inherited v0.3 contract fixes one derived AES key per streaming ciphertext stream, exact lookahead/read
math and 8160-byte/0.255-second streaming design bound; deterministic big-endian AAD and manifest;
the exact non-deprecated Keystore Builder/key-confirmation/classification path; 9/13/21 candidate
publication sequences; `final + ".tmp"`, collision/no-overwrite and five reconciliation states;
successful SQLite `endTransaction()` as semantic commit; WAL/FULL rows with exact identities and
UNIQUE deterministic processing intents and candidate-specific K01–K12 barriers. Historical v0.4 adds ninth-family
`key-confirmation/run.kc`, separate bounded plaintext/AAD schemas, exact 13-step durable bootstrap,
confirmation taxonomy/reconciliation and 12 mandatory bootstrap/confirmation rows, for 45 total.
Historical v0.5 defines one strict eight-class KEY taxonomy, moves plaintext parser/magic/schema/
no-trailing/identity checks to post-decrypt only and adds KCF-07. Active v0.6 materializes exactly
46 unique effective rows and replaces only the single `KEY-04`: all confirmation identity and
usable-alias prerequisites plus controller replacement of the underlying alias key while preserving
ciphertext bytes/recorded identity must precede an authentication/AAD-only decrypt failure; the sole
classification is `KEY_UNAVAILABLE_KEY_MISMATCH`. Successful decrypt with malformed/wrong plaintext
remains KCF-07 → `CORRUPT_KEY_CONFIRMATION`. v0.6 separates Phase A
184 injections from the full physical 138-injection campaign, and freezes exact D2 reuse and 11
canonical readiness blocker IDs. The
original safety gates remain zero committed-byte loss, no more than five seconds tail per valid
kill, 12 strata, 120 base kills/candidate, at least 100 valid and at least eight valid/stratum.
Fifteen/30-second microfiles remain non-PASS observations or post-failure fallbacks.

The exact `tink-android:1.23.0` published JAR/POM/transitive closure, SHA-256 values,
per-coordinate license/copyright/NOTICE evidence, 16 publisher checksum matches and 16 verified
OpenPGP signatures, relevant advisory history, non-native composition and shaded protobuf 4.33.6
are recorded under `docs/evidence/poc-recovery-001/`. All eight coordinate authenticity
classifications are verified through publisher-bound signatures or exact multisource source
correspondence. The signed `jsr305:3.0.2` Maven POM declares Apache-2.0 while the exact release
source POM/LICENSE declares BSD-3-Clause. F-06 closes because the exact evidence is complete; the
underlying conflict remains uninterpreted. Governance-only source/bytecode/Kotlin/JVM/D8/R8
analysis proves conditioned exclusion: a Tink-local Gradle edge exclusion, zero resolved JSR-305
components and three exact R8 warning rules. A bare exclusion fails the exact AGP 9.3.1 R8 probe.
With all seven remaining closure JARs as program inputs, that exact rule removes the JSR-305
diagnostics but R8 then fails independently on `javax.lang.model.element.Modifier` from
`error_prone_annotations:2.41.0`; the future real graph/release build must resolve any such issue
without broadening the rule.
The Project owner / Stage 0 Product-IP reviewer approved only prospective
`REC-JSR305-EXCLUDE-001` and the reviewed governance package for a future exact excluded Stage 0
graph. The disposition treats JSR-305 as excluded without selecting Apache-2.0 or BSD-3-Clause and
does not approve its use/distribution. This remains package preparation, not dependency admission.
At that pre-REC-I2A governance snapshot, no Tink coordinate or rule had yet been added to Gradle.

Phase A remains prospective only; actual execution is withheld. Without D1/D5 it can produce only
`FAIL` or `INCONCLUSIVE`, and a full physical verdict still requires D1/D2/D5. The later `OD-15`
emulator-first order postpones D1 and D3–D6 procurement until E-slot plus D2 evidence and a concrete
final gate identify the missing physical profile; it does not weaken the physical verdict. Its
bounded non-measured E-slot check authority is separate and does not authorize Recovery Phase A,
hard-kill/fault or measured campaigning.

PR #38 protected-squash-merged exact source head
`f178490e314251bcb1c7fb334e92b479fece1155` as
`a7e23c9a2758a3ee2cc8aba26be397b07ffc8f5b`, preserving tree
`4b07b00b247decfed3b1bd6155ca9bc98701a196`. It added the exact REC-I2A resolved
graph/package/release-R8 proof and scoped Stage 0 Product/IP disposition for the REC-I2B input, plus
the REC-I2B Tink runtime crypto boundary and its accountable/independent review records. This is
evaluation-scope evidence, not dependency or production admission. The first exact-main run
`32190003292` failed only because the Recovery governance validator selected the REC-I1 successor
profile. PR #50 changed only that dispatch, preserved all Recovery runtime/evidence bytes, merged as
`0136a6904aac2909582ba228a7e24aafa7fdc4f7`, and exact-main run `32195264657` succeeded.

The isolated `:poc:recovery` module therefore contains the merged REC-I1 contract and REC-I2B
runtime crypto boundary, but still no REC-I3 harness/controller, fault campaign, device run,
benchmark, product schema or production-module edge. Immutable readiness record
`docs/evidence/poc-recovery-001/readiness.json` remains a historical pre-REC-I2A/I2B snapshot with
`implementationAllowed=false`, `implementationAllowedByThisPackage=false` and
`executionAllowed=false`; it is not rewritten. Current additive authority is recorded by
`OWNER-AUTH-BATCH-20260819-01` / `OD-15`: REC-I3 implementation, non-metric verification and
conditional merge are allowed, while Recovery Phase A/hard-kill/measured campaigning and production
admission remain false. OD-15 separately authorizes bounded non-measured
E-slot functional, fault, compatibility and preflight checks after exact-pin availability and
task-specific prerequisites; none was executed by this reconciliation. Novikova Katerina's
distinct accountable reviews remain bounded to their
exact targets; no Rambus corporate approval is claimed. Codex does not claim formal independence.
Production Legal and Production Security remain null and separate.

The exact REC-I1 implementation lifecycle is now closed only for that pure contract foundation.
OpenAI Codex / GPT-5 independently reviewed PR #15 HEAD
`ee7bb00b09a282df7a8fb3b4d3481a5abd4d0177` / tree
`ac3dcf273fd447623fa8dbc5c71087acd6315830` as an AI Recovery I1 implementation advisory
reviewer. The review recorded `formalReviewer=false`, `NO_FURTHER_CHANGES_REQUIRED`, no P0/P1/P2
findings, `REC-I1-IMPL-001..004=CLOSED` and 62/62 passing Recovery JVM tests. Protected squash
merge created `main` commit `f2bc8c95bbe8af0d010968fff2ca175851728bf2` with parent
`9c4a798aa3c95877ff3f9aa66f18f94849b25cce` and the same reviewed tree. This review and merge do
not authorize runtime crypto, dependencies, a harness, device execution or production admission.

The first post-merge push run `31743157457` failed only because the governance validator still
treated `main` as an unauthorized pre-merge branch. That was a validator lifecycle defect, not a
new finding in the Recovery Kotlin implementation. PR #16 remediated only
`tools/validate_poc_recovery_governance.py`: its final reviewed HEAD
`8038153db2557e439c684686ea739d8c14620da3` / tree
`a75ea1bf1de96827b26c98cd99e461aedfa06ab7` received a separate OpenAI Codex / GPT-5 AI governance
remediation advisory re-review with `formalReviewer=false`, `NO_FURTHER_CHANGES_REQUIRED` and no
P0/P1/P2 findings. Required PR CI run `31784363002` attempt 3 passed. Protected squash merge then
created `main` `685e759290f8987444280b05e69b9d4d0070424e`, parent `f2bc8c95…`, with the reviewed tree; exact
post-merge push run `31790356849` passed. Its `search-smoke` job remains unrelated Search emulator
evidence and is not Recovery execution evidence. These are completed audit facts; GitHub remains
authoritative for current branch, PR, protection and CI state.

## Closed Stage 0B evidence record

Stage 0B implemented only the disposable `POC-CAPTURE-001` evidence harness selected by `OD-01`. It did not admit that implementation into production Dora.

The owner phone could not be attached to the remote development workstation. Stage 0B therefore used a remote manual-device workflow without ADB:

1. GitHub Actions builds and publishes a debug-signed PoC APK.
2. The owner installs it manually and starts each test explicitly.
3. The PoC discovers sanitized device characteristics and records technical metrics locally.
4. Raw audio is analyzed and deleted in app-private storage before export is enabled.
5. The owner returns only the sanitized exported profile/report to Codex.

Three bounded phone runs were returned after two invalid pre-recording starts. Run A attempt 003 and Run B attempt 004 on `5d9a8ac` recorded, stopped and finalized successfully. Run C attempt 005 recorded for 63:49 but is classified by the owner as an `invalidated exploratory attempt`: only 25:58 was screen-off, a TrueConf call occurred and the phone was charging. Bluetooth was fully disabled before Run C and no route change was reported. All three completed recordings produced valid WAVs, zero AudioRecord errors and verified raw-audio deletion/absence; no approved critical capture failure was observed on the tested Samsung device. The formal result remains `INCONCLUSIVE`, and the owner accepts exploratory closure without repeating Run C on the primary work phone. Production capture, storage, ML, backend, account, production identity/signing and model weights remain outside this stage. The bootstrap `:app` must not receive microphone permission.

## Owner decisions effective 4, 11, 12 and 19 August 2026

- `OD-01`: first experiment is `POC-CAPTURE-001`, limited to a physical microphone and explicit Start/Stop; call, system-audio and passive recording are prohibited.
- `OD-02`: every test run requires a separate reminder checkbox; it is not legal permission.
- `OD-03`/`OD-04`: synthetic-first; separately consented adult volunteer phrases may be used only after governance controls; real meetings and training/model improvement are prohibited.
- `OD-05`: fully specified `stage0-v0.1` gates are Approved only for Stage 0; critical data-loss/source/consent gates cannot be weakened after results. The six section 7 thresholds remain `Proposed`.
- `OD-06`: the first exploratory run is limited to one owner-provided physical phone; no other device procurement, global D1–D7 PASS or support claim is allowed.
- `OD-07`: eight hours is best effort only for the exact tested device, firmware, power, temperature and free-space conditions.
- `OD-08`/`OD-09`: GitHub receives only sanitized reports and aggregate metrics; raw evidence requires controlled private storage and the approved 90/180/30-day maximum deletion rules.
- `OD-10`: local mode works without account, network or GMS; cloud remains off until separate explicit consent.
- `OD-11`: Project owner is Product and IP policy reviewer and acts as Engineering/Security reviewer only for Stage 0 evaluation. This does not replace production Legal or independent production Security. Embedded platform SQLite may use the containing system-image digest plus exact image/runtime identity for Stage 0; that boundary must be reconsidered before production admission.
- `OD-12`: Project owner prospectively approves Option B for `stage0-v0.2`, based on the local-MVP storage/update/one-second visibility balance and not on prior Dora results. Benchmark execution remains separately withheld.
- `OD-13`: Project owner approves the exact 66-component/license/NOTICE/platform package only for internal synthetic Stage 0 evaluation and accepts formal `INCONCLUSIVE` closure without a new benchmark. This is not production Legal/Security approval, does not admit FTS4 automatically, is not retroactive, and leaves D1/D3 plus measured execution deferred.
- `OD-14`: Project owner constraints link active prospective governance protocol v0.6, which inherits all unchanged SHA-256-pinned v0.5/v0.4/v0.3 semantics and replaces only effective KEY-04 while preserving the 46-row campaign profiles and canonical blocker IDs. Historical AI advisory records remain unchanged with `formalReviewer=false`. Novikova Katerina completed the distinct accountable read-only formal human review in individual professional capacity, Rambus affiliation only, with `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW`; `REC-REV-20260812-02=CLOSED_BY_DISTINCT_ACCOUNTABLE_FORMAL_HUMAN_REVIEW` and `REC-RDY-02=CLOSED_DISTINCT_ACCOUNTABLE_FORMAL_HUMAN_REVIEW` without a Rambus corporate-approval claim or implementation/Phase A/execution authority. Prospective `REC-JSR305-EXCLUDE-001` and exact governance authenticity/LICENSE/NOTICE evidence are closed; the future actual recovery graph/package/R8 evidence and its Product/IP disposition remain open/blocking. The selected construction remains `DESIGN_SELECTED_IMPLEMENTATION_VERIFICATION_REQUIRED`; excluded JSR-305 terms are not interpreted and use/distribution is not approved. The task-scoped REC-I1 foundation has its own completed advisory implementation review/authorization/verification lifecycle; every runtime implementation scope, complete verification and later execution authorization remain separate and mandatory. `implementationAllowed=false`; `implementationAllowedByThisPackage=false`; `executionAllowed=false`; `measuredExecutionAllowed=false`.
- `OD-15`: Project owner activates current REC-I3 implementation, non-metric verification and conditional protected merge after exact-head CI and required clean reviews. Exact Recovery flags are `recI3ImplementationAllowed=true`, `recI3NonMetricVerificationAllowed=true`, `recI3ConditionalMergeAllowed=true`, `phaseAAllowed=false`, `executionAllowed=false`, `measuredExecutionAllowed=false`, `productionAdmissionAllowed=false`. The same decision authorizes all available bounded non-measured functional, fault, compatibility and preflight checks on orthogonal E28/E30/E36-GAPI/E-NOGMS/E16K/E-NEXT slots after exact-pin availability and task prerequisites, uses D2 only for intrinsically physical evidence and postpones D1/D3–D6 procurement until E-slot plus D2 evidence and a concrete final gate. Recovery preflight still follows successful REC-I3; Recovery hard-kill/fault, Phase A and measured campaigning remain unauthorized. It does not rewrite OD-14/DEC-044/history, resolve D6/D7, waive physical microphone/flash/battery/thermal/OEM/radio/VPN/arm64 evidence, or admit a production dependency/container. This reconciliation performs no emulator/device run.

## Current gates and blockers

- `POC-RECOVERY-001` remains `BLOCKED`, not READY. Proposed `DEC-044`, current Approved `OD-15`,
  Gate Set `poc-recovery-stage0-v0.6`, protocol `poc-recovery-protocol-stage0-v0.6` and all 15
  unchanged SHA-256-pinned v0.1–v0.5 superseded audit artifacts remain authoritative in their exact
  scopes. Novikova Katerina's accountable governance review closed
  `REC-RDY-02=CLOSED_DISTINCT_ACCOUNTABLE_FORMAL_HUMAN_REVIEW`; the later exact REC-I2B accountable
  review also completed for its own target. `REC-I1-AUTH-20260813-01` remains the historical pure-I1
  authorization. None of those historical records is rewritten by current `OD-15`.
- PR #38 merged the exact REC-I2A graph/package/R8 proof and REC-I2B Tink runtime, including the
  dependency-local JSR305 exclusion, zero policy-covered resolved/packaged JSR305 evidence, exact
  three-line R8 boundary and scoped Product/IP disposition for that REC-I2B input. Existing
  other-module tooling/lint/UTP/test occurrences remain outside the Recovery admission boundary.
  PR #50 closed only the squash-main validator dispatch defect. No final harness graph, recovery
  campaign, dependency-production admission or Production Legal/Security approval follows.
- Current `OWNER-AUTH-BATCH-20260819-01` allows the separate REC-I3 implementation and non-metric
  verification needed to build the isolated harness/controller and refresh its exact graph/locks/
  verification/package/R8 evidence; a conditional protected merge is allowed only after the
  required exact-head CI and clean independent/accountable reviews. It also allows all available
  bounded non-measured E-slot functional/fault/compatibility/preflight checks once exact pins are
  available and task prerequisites are met; Recovery hard-kill/fault, Phase A and measured campaigns
  remain excluded, and D2 remains limited to intrinsic physical evidence. Ten active blockers remain:
  `REC-RDY-01`, `REC-RDY-03`–`REC-RDY-11`. Recovery preflight follows successful REC-I3 and must bind exact E-slot/D2
  SQLite/Keystore/filesystem facts, including effective WAL/FULL, `wal_autocheckpoint=0`,
  `foreign_keys=ON`, `sqlite_version()`, `sqlite_source_id()` and canonical compile-options digest.
  `phaseAAllowed=false`, `executionAllowed=false`, `measuredExecutionAllowed=false`; Phase A's 184
  injections and the full physical 138-injection D1/D2/D5 campaign were not run. PASS remains
  structurally forbidden without the required physical evidence, and the 120-attempt hard-kill
  campaign/candidate remains a separate denominator.
- `tools/check_poc_recovery_run_readiness.py` must fail closed while
  `executionAllowed=false`; completion of any prerequisite cannot silently authorize execution.
- `POC-SEARCH-001` retains its frozen generated-scale observations. The earlier valid full result
  failed the latency gate; the corrected streaming rowid page plan passed the targeted scale guard
  before the final full measurement campaign met the evaluated latency/correctness predicates.
  Frozen manifests, parameter binding, repetitions, percentile definition and historical gates
  were not weakened.
- The Stage 0 v0.1 search row says that a storage/update gate failure is mandatory and lists no
  status exception, but no numeric overhead threshold was frozen. `DEC-043` / Gate Set
  `stage0-v0.2` now prospectively approves Option B with exact paired metrics, scale, repetitions,
  aggregation, physical environment and fallbacks. The historical gate remains `not_evaluated`;
  `benchmarkExecutionAllowed=false`, so no rerun is authorized.
- The PoC module now has a dependency lock and exact 66-component artifact/POM inventory covering
  both debug and formal benchmark configurations. All 66 effective licenses and discovered
  license/NOTICE entries are inventoried; `OD-13` makes the exact packet
  `EVALUATION_APPROVED` only for Stage 0 evaluation. `OD-11` records the Stage 0 Product/IP and
  Engineering/Security roles. The Android API 36 Google APIs x86_64 r07 archive is
  pinned by official SHA-1 and independently computed SHA-256. Embedded SQLite 3.44.3 uses that
  containing-image digest with exact image ID/revision, fingerprint, API and ABI for Stage 0; no
  extracted binary digest is required. This boundary must be reconsidered before production;
  production Legal is unassigned and independent production Security remains mandatory.
- The new paired control/indexed harness and nearest-rank combiner are implemented and bound to
  commit `b5bcf0951f3cb16d3fec65174395e7715c49a7d7`; debug and benchmark compilation, synthetic
  combiner tests and API 36 emulator runtime smoke pass. Smoke timings are not gate evidence. No
  formal 10k/1M v0.2 benchmark was executed.
- Physical availability is now explicit: D2 (`owner-phone-001`) exists, while D1 and D3 remain
  `unknown`. Missing D1/D3 blocks execution and any formal search `PASS` or support claim.
- The fully specified predicates in Gate Set `stage0-v0.1` are **Approved for Stage 0**. Exact ASR RTF by tier, maximum PSS/native heap, diarization corrections/minute, absolute battery drain without mWh, numeric capture sample-gap tolerance and minimum raw-trace retention remain **Proposed**.
- The owner's physical phone is sanitized as Samsung `SM-S908B`, Android 16 / API 36, build `BP2A.250605.031.A3`, primary ABI `arm64-v8a` and 10515 MiB RAM. It is assigned to D2 as the closest hardware profile; D1 and D3-D7 availability remains `unknown`.
- The refreshed pre-Run-A profile reports 36432 MiB free app storage, above the D2 start threshold of 8192 MiB, with 72% battery, unplugged power and thermal status `NONE`; the Run B export refreshed this to 33707 MiB and 80% with the same unplugged/`NONE` state. The Run C profile reports 33645 MiB, 70%, charging and `NONE`. The D2 hardware inventory remains valid; Run C battery data is not comparative evidence.
- The first Run A attempt on build `f351695…` is invalid: recording did not start, no sanitized Run ZIP or deletion receipt was produced, and the screenshot-era error can only be classified as a legacy unscoped `IllegalArgumentException` at `beginServiceCapture`.
- Replacement build `56fe23a` is published as prerelease `poc-capture-001-build-56fe23a` after successful push and PR CI. It makes optional battery/AudioRecord telemetry best-effort, adds sanitized capture-start stage codes, hardens recorder/player cleanup and propagates semantic dark-theme content colors.
- The replacement CI debug certificate differs from build `f351695`. The owner-provided post-install preflight showed the replacement-only corrected dark-theme UI, Run A target `00:03:00`, 36252 MiB free storage, 80% battery, unplugged power, thermal status `NONE` and synthetic signal disabled.
- The preflight screenshot does not display a commit identifier; association with `56fe23a` relies on the prescribed clean-install workflow and replacement-only corrected UI behavior. The source screenshot is not committed.
- The subsequent Run A attempt on `56fe23a` is invalid: recording did not start, no raw audio/Run ZIP/deletion receipt was created, and the visible stable code localizes the failure to `CAPTURE_START_PRIVATE_FILE`.
- Root cause is deterministic: generated Run IDs contain safe uppercase UTC delimiters `T`/`Z`, while the private WAV basename validator accidentally allowed only lowercase ASCII. Fix `5d9a8ac` accepts ASCII case consistently while retaining traversal, nested-path and unsupported-suffix rejection.
- Prerelease `poc-capture-001-build-5d9a8ac` targets exact commit `5d9a8aceebaa7175a7a5cbaa139e8295df87d632`; push run `31006645902` and PR run `31006649411` are successful. APK SHA-256 is `dc7c01b8fd0f6f66c2674a8542595aab8817d60c4435d2f1c26ef8b1c4d2ddb9`.
- The new CI debug certificate `4052ae88…` differs from build `56fe23a` (`dd28877d…`). The owner-provided post-install preflight showed readable build `5d9a8ac` Run A UI with target `00:03:00`, 35945 MiB free storage, 72% battery, unplugged power, thermal status `NONE` and synthetic signal disabled; this was the accepted preflight for Run A attempt 003.
- The new preflight screenshot does not display a commit identifier; association with `5d9a8ac` relies on the prescribed clean-install workflow for the differently signed replacement APK. The source screenshot is not committed.
- Run A attempt 003 (`run-a-20260805T133208Z-349c5e0c`) targets exact commit `5d9a8aceebaa7175a7a5cbaa139e8295df87d632`. The returned 5294-byte source ZIP has SHA-256 `f7d00a3675de539908641b513254e7fb8161f82b20685b64f27a4f080fdd05b1`; it passed CRC, allowlist, JSON parse, benchmark schema and sanitization review and is not committed.
- Run A completed 180.258 s with 2881600 actual versus 2884128 expected samples, 5763200 PCM bytes, one short read, zero AudioRecord errors, a valid 5763244-byte WAV, 20 ms start latency, 44 ms finalization latency, zero route changes/interruptions, 67%→67% battery, thermal `NONE`→`NONE`, 132.299 MiB peak PSS and 21.863 MiB peak native heap.
- The deletion receipt reports `deletionSucceeded=true`, `absenceVerified=true` and `containsAudio=false`; no raw audio is retained or committed. Public evidence is under `docs/evidence/poc-capture-001/`.
- Run A notification visibility is `unknown`, not a positive visibility proof and not evidence of a hidden recording. Run B positively records `manual.notification_visible=yes`, so the approved hidden-state failure predicate was not triggered.
- Run B attempt 004 (`run-b-20260810T054638Z-0b71124a`) targets exact commit `5d9a8aceebaa7175a7a5cbaa139e8295df87d632`. The returned 5423-byte source ZIP has SHA-256 `8d8161a57478f13036e3b23ec3c7abc7642b85594f9cd4e38f48a0872a841647`; it passed CRC, flat allowlist, JSON parse, benchmark schema and sanitization review and is not committed.
- Run B completed 1316.644 s against a 900 s plan and accumulated 1020.154 s screen-off. It recorded 21032314 actual versus 21066304 expected samples, 42064628 PCM bytes, one short read, zero AudioRecord errors, a valid 42064672-byte WAV, 15 ms start latency, 31 ms finalization latency, 80%→78% battery, thermal `NONE`→`NONE`, 136.281 MiB peak PSS and 18.496 MiB peak native heap.
- Run B's deletion receipt reports `deletionSucceeded=true`, `absenceVerified=true` and `containsAudio=false`; deleted-WAV SHA-256 is `9f24052322b2ead6f3d528b8d69f8f06a25c4e6eb97a975b77a2394be13b6486`. No raw audio or source ZIP is retained or committed.
- Run B is not treated as a clean uninterrupted protocol execution: it overshot the planned duration by 416.644 s, the owner marked a call or other intervention, and the event log records a 6.476 s route transition to Bluetooth SCO and back after the nominal 15-minute target. The aggregate report has no frame-level timing to prove continuity through that transition, and the -33990 sample observation has no retrospectively approved tolerance.
- Run C attempt 005 (`run-c-20260810T082734Z-d6abea38`) targets exact commit `5d9a8aceebaa7175a7a5cbaa139e8295df87d632`. The returned 5319-byte source ZIP has SHA-256 `7f8b71182b1fa1ea015f8251a2d872005e8a3f47897f1c95e9062a5353bfdc69`; it passed CRC, flat allowlist, JSON parse, benchmark schema and sanitization review and is not committed.
- Run C recorded 3829.050 s (63:49.050) against a 3600 s plan but only 1558.004 s (25:58.004) was screen-off. It recorded 61087644 actual versus 61264800 expected samples, 122175288 PCM bytes, one short read, zero AudioRecord errors, a valid 122175332-byte WAV, 24 ms start latency, 68 ms finalization latency, zero route changes/automated interruptions, thermal maximum `NONE`, 135.712 MiB peak PSS and 17.602 MiB peak native heap.
- Owner review confirms Bluetooth was fully disabled before Start, the 25:58 screen-off telemetry is correct, a TrueConf call occurred and the phone was charging. These corrections override the source questionnaire for campaign interpretation without rewriting the immutable machine report. The 70%→86% battery result is excluded from battery evidence.
- Run C raw-audio deletion succeeded, absence was verified and deleted-WAV SHA-256 is `50502a92a48825cf666057191b847d32d0b80aaeed010ec7e99abd604717d6ce`; no source ZIP, raw audio or raw event log is committed.
- Run C is an `invalidated exploratory attempt`, neither PASS nor FAIL. It did not complete the required clean 60-minute screen-off slice, but no approved critical failure gate was observed. Its -177156 sample delta is retained only as an observation because the numeric threshold remains Proposed.
- All three sanitized event logs are non-monotonic at raw deletion because the deletion event is labeled with `outcome.actualDurationMs`; the independent receipts/export guard prove deletion happened only after finalized-file analysis. This is a known non-fatal measurement-telemetry defect, and event timing is not used as pass evidence.
- The owner accepts closure of the current exploratory campaign without repeating Run C on the primary work phone. The clean 60-minute screen-off baseline remains deferred evidence for a separately scoped campaign on a dedicated test device.
- Exploratory conclusion: `No approved critical capture failure was observed on the tested Samsung device during Run A, Run B and the interrupted Run C campaign.`
- The formal result remains `INCONCLUSIVE`: one phone/three completed recordings cannot prove D1-D7, 99.5%, an eight-hour run, clean one-hour screen-off stability, an approved sample-gap threshold or support for all Android devices.
- One-device evidence cannot `PASS` the matrix and remains `INCONCLUSIVE` unless an approved failure gate produces `FAIL`.
- No controlled non-public evidence store or custodian has been configured. Until then, only synthetic data and sanitized aggregate/public evidence are allowed; raw traces/audio and purpose-recorded volunteer phrases remain blocked.
- Production markets/lawful basis/copy under `DEC-001` and Legal review remain unresolved. The Stage 0 reminder checkbox does not resolve production consent legality.
- No PoC result admits a production dependency. Native code or model admission later requires an ADR plus license, provenance, ABI, 16-KiB and runtime evidence.
- `main` remains the protected integration branch. Stage 0C/PR #10 and PR #11 are already merged and
  untouched. Protected GitHub squash merge PR #12 closed at 06:03:14 Europe/Moscow on 13 August
  2026 as `main` commit `f14c6f37d7acb37590be875f176653c100f0ae20`; that commit has parent
  `eca48ba62acd79007884710395cc40ea21a02611` and the same tree as PR HEAD
  `b5371f523e4471aca48a63a82b9ee4e1f9a7e0fd`. Post-merge `android-bootstrap` and
  `search-smoke` succeeded. Governance-only reconciliation PR #13 later merged as
  `5c97f09f3165a90afa5300b30499e0dcb36168f2`. Live GitHub metadata is authoritative for current
  branch, PR and `main` lifecycle state.
- PR #15 later protected-squash-merged the independently reviewed pure REC-I1 foundation as
  `f2bc8c95bbe8af0d010968fff2ca175851728bf2`. Its first post-merge CI exposed only the branch-lifecycle
  validator defect. PR #16 changed only the validator and protected-squash-merged the reviewed fix as
  `685e759290f8987444280b05e69b9d4d0070424e`; exact-main post-merge CI then passed. Neither merge
  changed any Recovery authority flag or produced Recovery execution evidence.

## REC-I3 streaming Option A decision, 5 September 2026

`DEC-045` records the owner-selected authenticated-tail semantic against final proof
`19807f6ea8166fff972e1537beeb131def8bfa1d` / tree `8920855c9b8642162e5df16204d796d51db4fa86`.
Its additive bounded synthetic/non-metric streaming scope is started; implementation is not
complete. The decision resolves the pending A/B semantic authority: independently authenticated
pre-existing contiguous oracle-equal bytes may extend `R` beyond `C`; completed authenticated reads
remain in `R` after a later authentication failure; `C` does not advance; and unauthenticated or
corrupt remainder is rejected for bounded quarantine. Option B is rejected only for this scope.

This status change preserves the active Gate Set/protocol, 8,160-byte/0.255-second bound, all ten
`REC-RDY` blockers, `POC-RECOVERY-001` `BLOCKED / NOT_READY`, Recovery preflight ordering and every
campaign/admission flag. No device/process-death/durability campaign, production admission, schema
or quarantine-mechanics implementation is authorized.

## Next safe action

The exact REC-I2A/REC-I2B integration and its accountable/independent review chain are complete on
`main`; the historical PR #38 main-run failure is closed only by validator successor PR #50. The
next safe Recovery implementation is the owner-authorized REC-I3 isolated harness/controller slice
in a separate branch under `OWNER-AUTH-BATCH-20260819-01` / `OD-15`. That task may implement and
non-metrically verify only the frozen v0.6 contract with deterministic synthetic fixtures, refresh
its exact graph/locks/verification/package/R8 proof, obtain the required independent/accountable
reviews and conditionally merge after exact-head CI.

REC-I3 authorization does not make the PoC READY and does not authorize a Recovery Phase A,
hard-kill/fault or measured campaign. OD-15 separately authorizes all available bounded non-measured
functional, fault, compatibility and preflight checks on exact pinned E slots after task-specific
prerequisites; it does not authorize a support claim or substitute for intrinsic physical evidence.
Only after successful REC-I3 may the Recovery preflight record exact pinned E-slot and D2 SQLite,
Keystore and filesystem facts; it is still not Phase A or a verdict. Owner-only blockers remain:
explicit Phase A/measured execution authorization, any later concrete physical procurement gate,
the complete D1/D2/D5 verdict, Production Legal/Security and final post-evidence container/admission
decisions. Only a later explicit owner record may change `executionAllowed` after every applicable
prerequisite is satisfied.
Current Pull Request state is never a static document invariant; use live GitHub metadata.

## Update protocol

Every later task updates this file only when stage truth changes. Live PR/build status remains authoritative in GitHub and should not be copied as a stale badge or hard-coded run ID here.


## REC-I3 streaming persistence governance amendment — 6 September 2026

The Project owner confirmed the exact v5 streaming-persistence package, recorded as `DEC-046`. Accepted ADR-0005 and prospective Gate Set/protocol v0.7 are pinned to combined baseline `3c63ab09874f4d089e4363985aa8b5c99900c122` / tree `718eae8d8d619d17c25ac9d025e0e24db3d52f9e` and define schema v4, same-descriptor `P<=S<=E` proof, sealed outcomes, rejected observations, exact/conservative ACTIVE retained ranges, hash-only replay, K12-PERSISTENCE, and the TRU-03 stream override. K12-CONSUMER remains deferred. All v0.1-v0.6 artifacts and the exact 46/184/138/120 counts remain unchanged.

This is governance truth only until the exact eight-file commit receives an independent CLEAN review. The current OD-15 implementation/non-metric/conditional-merge overlay remains unchanged; all campaign/execution/admission flags remain false. `POC-RECOVERY-001` remains `BLOCKED / NOT_READY`, all ten active blockers remain open, and `REC-RDY-02` remains historically closed. No source implementation, device/emulator/preflight, real process death, hard-kill/fault/Phase A/measured campaign, physical durability evidence, PASS/READY, dependency/production admission, processing intent, cross-process denial, retirement, or merge is claimed.

## REC-I3 streaming result-boundary governance amendment — 6 September 2026

The owner-approved DEC-047/ADR-0006 and prospective Gate Set/protocol v0.8 change only the controller result/evidence boundary. They define twenty exact mappings, retain distinct known-operational and ambiguous journal retries, fix class-specific strict references, and split immutable exact-readback receipt core from the final post-close/post-sink receipt. One bounded sanitized sink attempt is required per invocation; PENDING is caller-retryable on exact replay with no autonomous guarantee.

No v0.8 result-boundary/controller implementation, execution, or evidence was produced. The exact ten-path governance commit requires independent CLEAN review before later source edits. v0.7 remains immutable; schema v4 and durable semantics are unchanged. 'POC-RECOVERY-001' remains 'BLOCKED / NOT_READY'; all ten blockers, historical REC-RDY-02 closure, OD-15 flags, 46/184/138/120 counts, K12 deferral, and all execution/production/consumer/cross-process/retirement/merge prohibitions remain unchanged.

## REC-I3 proven-VALID-rollback correction — 6 September 2026

The Development Governor approved DEC-048/ADR-0007 under the owner's recorded delegation. A semantic VALID attempt with both framework-proven rollback and reconciled absence maps to the existing 'Retry(JOURNAL, JOURNAL_OPERATIONAL, SQLITE)' result with no persistence or ACTIVE claim. Absence without rollback proof remains ambiguous. The exact v0.8 4/6/20/4 boundary and pinned predecessor artifacts do not change.

Independent focused review returned CLEAN before the affected controller mapping was enabled. 'POC-RECOVERY-001' remains 'BLOCKED / NOT_READY'; no execution, campaign, admission, consumer, retirement or merge authority follows.

## REC-I3 observable streaming controller local candidate — 6 September 2026

The isolated `:poc:recovery` candidate implements the accepted v0.8 observable result, replay, receipt and bounded sanitized evidence boundary over the existing schema-v4 journal and one-descriptor streaming gateway. It preserves exact 4/6/20/4 public counts, strict decoded ordered/deduplicated references, exact-readback receipt core, caller-driven exact replay, same-run lease lifetime and guard no-effect behavior. Independent final review of immutable head `2f3d48cf813759e391a0281195b689cad1721b91` returned `REVISE 0/3/0` for incomplete positive evidence shape, prerequisite/Tink work before sealed replay discovery, and missing oracle-bound replay row validation. The first author-local repair projected every permitted positive evidence fact, routed sealed replay around the fresh-only prerequisites, and reconstructed the oracle-derived outcome/observation/range identities before source replay. Independent rereview of repaired head `7232c3f489908c6f7abdaeb2e2a1597996dc540f` then found that retained-range replay trusted the stored range hash. The current successor recomputes the exact retained range through the same frozen descriptor and rejects a self-consistent altered range hash/identity before receipt or evidence. Focused and full Recovery JVM tests, Spotless, Detekt, Android lint and the host SQLite verifier pass with synthetic fixtures; the versioned local evidence records exact source digests and commands.

CI for published head `7d7b81bf593cd1fa348f04703e9e22278db6be22` exposed a governance-only omission: Stage 00 still pinned the terminal Product Decision at DEC-044 although the authoritative ordered registry ends at DEC-048. The current successor pins the exact DEC-001 through DEC-048 range, makes the Recovery validator invoke Stage 00, and expands only its exact observable-controller profile to 16 paths. Kotlin bytes and the reviewed Android evidence are unchanged.

Independent review of CI-correction head `5f897f585b7a4f2131324057823fb8ba5a42dde5` returned `REVISE 0/0/1/0` because mandatory decision labels were tested as unconstrained substrings. The current successor recognizes anchored metadata field lines, preserves the ordered historical DEC-001–044 requirements, and accepts only the exact decision-specific ordered metadata blocks for DEC-045–048. Direct mutation controls reject prose relocation, prefixed/misspelled/missing labels, extra metadata, and DEC-049.

Exact PR merge-ref CI run `34034200851` exposed a test-only context leak: simulated v0.8 lifecycle fixtures retained the current observable-controller pull-request identity when replacing the branch. The production validator correctly failed closed on that mismatched head ref. The current test fixture clears pull-request context only for simulated local v0.8 checks and retains a separate negative control proving that the real mismatch is rejected.

Exact-head CI run `34035616019` then showed that clearing the context alone left GitHub's synthetic two-parent merge commit in the simulated-local lifecycle HEAD. The production linear-history guard correctly rejected it. The current fixture first restores the verified pull-request source head, then clears context; the exact merge-ref regression proves that no merge commit remains in the simulated v0.8 history while the real context mismatch is still rejected.

Exact-head CI run `34036673882` is terminal `SUCCESS`; both `android-bootstrap` and `search-smoke` passed with that fixture repair. The owner's recurrence-prevention amendment now requires the behavior to remain covered through the actual Recovery self-test entry point. The retained test creates an isolated real two-parent merge checkout, supplies the matching PR event and environment, proves restoration to the verified linear source head, and keeps actual-entrypoint rejection for a wrong event head and local-history rejection for a genuine merge.

Immutable review of retained-regression head `9111913753d40d9640ed07b43e0c6320290cf61e` returned `REVISE 0/0/1/0`: the nested validator subprocesses were not bounded and a PR-context regression could recursively re-enter local orchestration. The current repair uses PR event state as a fail-closed nonrecursion guard, applies a 180-second bound to both validator children, terminates their process groups on timeout or failure, and verifies descendant cleanup before temporary topology removal.

Immutable rereview of head `2de6d8238d99e71ae573ffa29481a59e052c0efd` returned `REVISE 0/0/1/0`: on Windows an immediate nonzero parent could exit before `taskkill /T` ran, preventing discovery of its surviving grandchild. The current successor uses a kill-on-close Windows Job Object, starts the direct child suspended, assigns it before resume, and retains authority over descendants after parent exit. The real nonzero-parent and timeout controls both suppress delayed descendant markers and allow temporary-directory cleanup; POSIX isolated process groups remain unchanged.

This Job-bound retained-regression successor is author-local host evidence pending immutable independent review. It is not Android runtime durability, device/emulator, preflight, process-death/fault or measured campaign evidence. `fullRecI3Completed=false`; `POC-RECOVERY-001` remains `BLOCKED / NOT_READY`, all ten active blockers remain open, `REC-RDY-02` remains historically closed, and every execution, production-admission, consumer, cross-process, retirement and merge nonclaim remains unchanged.
