# Dora MVP 1 — Executable Backlog

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



Версия: Stage 0D post-PR43 main integration / REC-I2A-I2B merged / REC-I3 implementation authority / bounded E-slot checks / Recovery campaign hold\
Дата: 19 августа 2026 года\
Owner approvals effective: 4 августа 2026 года (`OD-01`–`OD-10`), 11 августа 2026 года (`OD-11`–`OD-13`), historical recovery constraints / prospective `REC-JSR305-EXCLUDE-001` 12 августа 2026 года (`OD-14`), exact pure-foundation `REC-I1-AUTH-20260813-01`, and current `OWNER-AUTH-BATCH-20260819-01` / `OD-15` on 19 августа 2026 года (REC-I3 implementation/non-metric verification/conditional merge; bounded non-measured E-slot functional/fault/compatibility/preflight checks after exact-pin availability and task prerequisites; no Recovery Phase A/measured campaign or production admission)\
Owner GOV-OMI scope: 18 августа 2026 года — task definition plus
`GOV-OMI-PHASE-A-PUBLIC-METADATA-AUDIT-AUTH-20260818-01`; public GitHub metadata collection is
complete, while source/blob/archive and issue/PR-content retrieval, copying, execution and
admission remain unauthorized.\
Источник порядка: Technical Plan §37/§39, Design Spec §36/§39 и readiness gates.

Current `POC-RECOVERY-001` delta: PR #38 protected-squash-merged exact REC-I2A resolved-graph/
package/release-R8 and scoped Stage 0 Product/IP evidence plus the reviewed REC-I2B Tink runtime as
`a7e23c9a2758a3ee2cc8aba26be397b07ffc8f5b`. Its exact-head CI succeeded; the first exact-main run
failed only because governance dispatch still selected the historical REC-I1 successor profile.
PR #50 changed only that validator dispatch, preserved all REC-I2A/I2B runtime/evidence bytes,
merged as `0136a6904aac2909582ba228a7e24aafa7fdc4f7`, and restored green exact-main CI. The additive
[post-PR43 main closure](evidence/stage0-post-pr43-main-integration-closure-2026-08-19.json) records
that lineage without rewriting immutable Recovery evidence.

Current owner record `OWNER-AUTH-BATCH-20260819-01` / `OD-15` sets
`recI3ImplementationAllowed=true`, `recI3NonMetricVerificationAllowed=true` and
`recI3ConditionalMergeAllowed=true`, while `phaseAAllowed=false`, `executionAllowed=false`,
`measuredExecutionAllowed=false` and `productionAdmissionAllowed=false`. Historical
`implementationAllowed=false` and `implementationAllowedByThisPackage=false` fields remain
unchanged in their older package/readiness records and do not negate the new named REC-I3 overlay.
Active identities remain Gate Set `poc-recovery-stage0-v0.6` and protocol
`poc-recovery-protocol-stage0-v0.6`; historical `REC-REV-20260812-01` closure and the accountable
`APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW` disposition remain preserved in their exact scopes.
The same current record separately authorizes all available bounded non-measured functional,
fault, compatibility and preflight checks on exact pinned E slots after task-specific prerequisites;
Recovery preflight additionally follows successful REC-I3, and D2 remains limited to intrinsically
physical evidence. The harness/controller, fresh REC-I3 graph, preflight and campaign remain absent; all ten active
`REC-RDY` blockers remain open. Aggregate backlog truth remains exactly `DONE=27`, `BLOCKED=18`,
`TODO=9`, `READY=0`. This reconciliation performs no emulator/device execution and elevates no PoC
state, PASS, readiness or admission.

REC-I3 first-slice amendment, 5 September 2026: the
[read-only key-confirmation controller scope](stage0/DORA_MVP1_POC_RECOVERY_I3_KEY_CONFIRMATION_CONTROLLER_SCOPE_STAGE0_V0_1.md)
and [local evidence](evidence/poc-recovery-001/rec-i3-key-confirmation-controller-local-evidence-stage0-v0.1.json)
supersede only the pre-slice blanket absence of a controller in the historical summary/table.
The full harness, durable bootstrap/publication/journal/quarantine, exact package verification and
required implementation review remain unfinished. This partial REC-I3 result closes no readiness
blocker, unlocks no Recovery preflight and leaves `POC-RECOVERY-001` `BLOCKED / NOT_READY`, aggregate
counts and every campaign/admission flag unchanged. Subsequent implementation slices remain under
the same current OD-15 authority; the current task performs no PR merge.

REC-I3 run-key bootstrap amendment, 5 September 2026: the additive
[bootstrap scope](stage0/DORA_MVP1_POC_RECOVERY_I3_RUN_KEY_BOOTSTRAP_SCOPE_STAGE0_V0_2.md)
and [local evidence](evidence/poc-recovery-001/rec-i3-run-key-bootstrap-local-evidence-stage0-v0.2.json)
add the exact KC01–KC13 new-run controller, typed crypto boundary, minimal PoC Android `Os`
publication adapter and versioned platform SQLite run-row journal. Host tests establish ordering,
short-write/error/closure behavior and the post-KC12 capability boundary; they do not prove Android
filesystem, Keystore or SQLite runtime behavior. Reconciliation, quarantine, candidate writers,
external kill controller, full fresh graph/package/R8 review and accountable implementation review
remain unfinished. All ten readiness blockers, aggregate counts, Recovery preflight lock and every
campaign/admission flag remain unchanged. This task performs no device/emulator execution,
preflight, hard kill, fault campaign, measurement, PR merge or future merged-main admission.

REC-I3 sequential microfile amendment, 5 September 2026: the additive
[scope](stage0/DORA_MVP1_POC_RECOVERY_I3_MICROFILE_PUBLICATION_SCOPE_STAGE0_V0_1.md),
[clarification](stage0/DORA_MVP1_POC_RECOVERY_I3_MICROFILE_PUBLICATION_CLARIFICATION_STAGE0_V0_1.md)
and [local evidence](evidence/poc-recovery-001/rec-i3-sequential-microfile-publication-local-evidence-stage0-v0.1.json)
add a repeatable `REC-MICROFILE-TINK` unit plus cumulative-manifest writer through
exact `MICRO-P01`–`P21`. The slice shares the bootstrap run lease and advances only the PoC journal
to a non-destructive schema v2 in the existing database. Host tests exercise actual Tink
round trips, sequential state, conservative failure remainders and exact processing intents; host
SQLite executes the production DDL and migration statements but is not Android runtime proof.
Reconciliation, quarantine, streaming, external control and campaign execution remain unfinished.
`fullRecI3Completed=false`, all ten blockers and the Recovery preflight lock remain unchanged.

REC-I3 reconciliation/quarantine amendment, 5 September 2026: the additive
[scope](stage0/DORA_MVP1_POC_RECOVERY_I3_MICROFILE_RECONCILIATION_QUARANTINE_SCOPE_STAGE0_V0_1.md),
[ADR-0004](adr/ADR-0004-poc-recovery-reconciliation-and-quarantine.md) and local evidence add the
bounded authenticated microfile-prefix and durable quarantine controller. Host tests cover actual
Tink authentication, replay ordering and exact SQLite v1-to-v2-to-v3 migration; compiled Android
adapters and host fakes are not device durability proof. Streaming, external control and campaign
execution remain unfinished. `fullRecI3Completed=false`, all ten blockers and every preflight,
execution, measurement and production-admission lock remain unchanged.

The additive
[review-correction scope](stage0/DORA_MVP1_POC_RECOVERY_I3_RECONCILIATION_REVIEW_CORRECTION_STAGE0_V0_1.md)
supersedes the first local coverage claim after independent review found missing behavior inside
that bounded slice. The corrected host boundary owns platform observations, binds publication
authority to the candidate, manifest and ordered rows, preserves authenticated fallback prefixes,
and exercises pending quarantine replay, typed failures, descriptor bounds and schema-v3
validation. Independent review of corrected head `5de34577295b5e5477970785f9472d51e21bfc43`
returned `REVISE` (P0/P1/P2 = 0/7/1). Author-local successor `a020944f0444edfcbabc4690d4a367b1cf9e83d7`
then passed its reported host checks, but exact independent review also returned `REVISE`
(P0/P1/P2 = 0/4/2). The historical a020 six findings were pending then; ca2 closed optional-quarantine/no-prefix retention, confirmed-Q05 remainder, row framing and production unique-readback. Exact ca2 round-four truth preserves the immutable original `REVISE 0/3/0` review and its addendum-effective `REVISE 0/4/0` disposition. Independent review of `bc4f4423b535982956575f5a3480c9361eab6378` returned `REVISE 0/2/0`; the later reviewed successor `de735735ace6da3572c45dfdc58a8bbff98145b0` closed the four ca2 findings, the bc4 findings, and the duplicate-check acceptance gap, but found two distinct current P1s: zero-byte inventory regression and missing-final failure retention. The current author successor separates zero-permitting inventory reads from strict non-empty role reads and retains a durable-row missing final as primary with at most one temporary-observation secondary. Its 224 Android host tests and current governance checks are author evidence only; both new P1s remain open pending exact-candidate independent review. No readiness or execution authority changes, and all immutable ca2/addendum history and nonclaims remain recorded.
The prior author-local closure and acceptance mapping are superseded claims rather than independent
closure. Immediate lstat plus rename is not kernel-atomic no-replace proof; Android filesystem
durability and every wider REC-I3 gate remain unproven.

REC-I3 streaming Option A decision amendment, 5 September 2026: approved
[DEC-045](stage0/DEC-045-POC-RECOVERY-STREAMING-AUTHENTICATED-TAIL.md) records the owner-selected
authenticated-tail semantic from final proof `19807f6ea8166fff972e1537beeb131def8bfa1d` / tree
`8920855c9b8642162e5df16204d796d51db4fa86`. The bounded synthetic/non-metric streaming slice is
started by its additive scope; this resolves the owner A/B semantic choice, rejects Option B only
for this scope, and does not complete implementation. `C` remains durable, `R` may include only
independently authenticated contiguous oracle-equal bytes, and an unauthenticated/corrupt remainder
is rejected for bounded quarantine. All ten `REC-RDY` blockers, aggregate counts, the Recovery
preflight lock, active Gate Set/protocol, 8,160-byte/0.255-second bound and every campaign/admission
flag remain unchanged. No device/process-death/durability campaign, production admission, schema or
quarantine-mechanics implementation is authorized here.
## 1. Правила выполнения

- `DONE` означает: артефакт существует, acceptance выполнен и evidence доступно в commit/CI/report.
- `READY` означает: scope, dependency, fixture, gate и fallback определены; работа может начаться в отдельной ветке.
- `BLOCKED` означает: указан конкретный DEC/legal/license/device dependency; код, зависящий от решения, не начинается.
- `TODO` означает: задача известна, но ещё не прошла Definition of Ready.
- Один PR решает одну измеримую задачу или тесно связанный вертикальный slice.
- PoC не превращается в production dependency автоматически. Admission требует отдельный ADR/lock PR.
- Raw private audio, credentials, signing keys и unapproved model weights не входят в Git/LFS/Actions artifacts.

## 2. Stage 00 — GitHub, readiness и bootstrap

| ID | Состояние | Задача | Зависимости | Результат / acceptance |
|---|---|---|---|---|
| S00-GIT-001 | DONE | Проверить baseline/local Git | — | root/branch/status/history/remote проверены; SHA `1be83e…` подтверждён |
| S00-GIT-002 | DONE | Создать initial private `Monumentogram/DORA` и опубликовать baseline `main` | S00-GIT-001 | repository created without an extra initial commit; remote `main` указывает на exact baseline; later visibility change governed by ADR-0002 |
| S00-GIT-003 | DONE | Создать `stage/00-readiness-bootstrap` | S00-GIT-002 | branch fork point = baseline; `main` unchanged |
| S00-DOC-001 | DONE | Полностью прочитать и cross-check четыре baseline artifacts | S00-GIT-003 | readiness review with P0/P1/P2 and traceability |
| S00-DOC-002 | DONE | Создать Product Decisions registry | S00-DOC-001 | стабильный namespace DEC-001–DEC-042 и scoped Stage 0A owner approval record; каждый `Approved` status имеет прямой owner/date/scope record |
| S00-DOC-003 | DONE | Зафиксировать backlog, status, ADR и Codex rules | S00-DOC-001 | root `AGENTS.md`, contributing/status/backlog/ADR linked |
| S00-TEST-001 | DONE | Создать сквозную Test Strategy | S00-DOC-001 | уровни unit→release, environment/pass gates, CI tiers и physical matrix закреплены в `DORA_MVP1_TEST_STRATEGY.md` |
| S00-ANDROID-001 | DONE | Создать минимальный Android skeleton | DEC-005/006/015; ADR-0001 | wrapper/JVM 17/min 28/compile-target 36; adaptive four-destination placeholder shell; separate non-recording action; light/dark semantic token mapping; no microphone permission/product behavior |
| S00-TEST-002 | DONE | Добавить meaningful bootstrap tests и instrumentation infrastructure | S00-TEST-001, S00-ANDROID-001 | destination order/selection, unavailable recording action, theme/tokens and compact/wide threshold covered; Compose UI suite compiles and has a documented device command |
| S00-QUALITY-001 | DONE | Закрепить formatting и Kotlin static analysis | S00-ANDROID-001 | Spotless 8.9.0 + ktfmt 0.63 and Detekt 1.23.8 are version-pinned; checks pass without a baseline or disabled rule set |
| S00-DEPS-001 | DONE | Устранить `androidx.core` catalog/lock drift | S00-ANDROID-001 | catalog intentionally pins `1.18.0`, matching the Activity 1.13.0 graph and regenerated/reviewed dependency locks |
| S00-CI-001 | DONE | Добавить GitHub Actions CI | S00-ANDROID-001, S00-TEST-002, S00-QUALITY-001 | pinned least-privilege workflow validates wrapper/docs, formatting, static analysis, locks, unit/androidTest compilation, lint/assemble and native alignment; debug bootstrap APK is retained for seven days |
| S00-VERIFY-001 | DONE | Локально проверить clean checkout commands | S00-CI-001 | 197-task formatting/detekt/test/androidTest-compile/lint/assemble graph green; Stage 00 validator, dependency insight and 16-KiB ELF/APK gates green; generated artifacts ignored |
| S00-PR-001 | DONE | Commit/push/open PR without merge | S00-VERIFY-001 | checked Stage 00 commit/branch published; ready-for-review PR #1 targets `main`; no merge |
| S00-CI-002 | DONE | Проверить/исправить GitHub Actions | S00-PR-001 | PR-triggered `android-bootstrap` completed successfully, including test/lint/assemble and native gates |
| S00-SEC-001 | DONE | Провести pre-public secret/privacy audit | S00-CI-002 | checksum-verified Gitleaks 8.30.1 scanned all refs/full history and both branch trees; all three commit trees, filenames, identities, Actions configuration/logs and GitHub secret metadata were independently checked; no real secret, PII, private path or accidental artifact found |
| S00-GIT-004 | DONE | Защитить `main` после появления stable check name | S00-SEC-001 | owner explicitly approved temporary public visibility; existing repository changed in place and API-verified public; `main` requires up-to-date GitHub Actions app `15368` check `android-bootstrap`, PR, linear history and conversation resolution; admin enforcement on, force-push/delete off; secret scanning and push protection enabled; ADR-0002 |

## 3. Stage 0 — обязательные governance и PoC

Ни одна задача этого раздела не разрешает production feature implementation. Каждый PoC получает отдельную ветку, synthetic/consent-governed data и machine-readable report.

| ID | State | Size | Задача | Depends on | Deliverable | Acceptance / fallback |
|---|---|---:|---|---|---|---|
| GOV-001 | BLOCKED | M | Markets/legal/consent decision pack | DEC-001; production Legal review | counsel/product memo, market-specific lawful-basis and versioned copy scopes | `OD-02` approves only Stage 0 reminder checkbox; recording beta and production consent claims stay off |
| GOV-PRIVACY-001 | DONE | M | Privacy/data-flow/threat assumptions v1 | DEC-009/014/015 | `docs/stage0/DORA_MVP1_PRIVACY_DATA_FLOW_THREAT_MODEL.md` | data inventory, forbidden telemetry, trust boundaries, deletion and local/corporate modes documented; unresolved cloud/legal flows explicitly blocked |
| GOV-TRADEMARK-001 | TODO | S | Name/package/trademark availability | DEC-025 | evidence + approved production identifier candidate | no registration/store asset before approval |
| GOV-IP-001 | DONE | S | Reference/font/model asset IP rules | DEC-024/042 | `docs/stage0/DORA_MVP1_IP_ASSET_POLICY.md` | artifact-level provenance, license, attribution and admission rules documented; no model/binary/reference asset admitted |
| GOV-OMI-001 | BLOCKED | XL | Exact-snapshot Omi reuse, test and hazard audit without changing Dora invariants | GOV-IP-001; exact future source/content retrieval authority and IP disposition; named reviewers | [`gov-omi-reuse-stage0-v0.1`](stage0/DORA_MVP1_GOV_OMI_REUSE_AUDIT_TASK.md), [machine-readable task record](stage0/gov-omi-reuse-task-stage0-v0.1.json), and five sanitized [Phase A evidence artifacts](evidence/gov-omi-001/audit-report.md) | public-metadata Phase A is complete at Omi commit `7d99abcc4efb9e46a5853b21fc01289e4b891837` / tree `85db621ffd5dc5386bcbd7c87713cc69638be7e3`: untruncated 13,341-entry tree, complete 998-release and 2,953-issue indexes, capped 1,000/1,470 tags and 5,000/8,794 PRs. Rights are `BLOCKED_RIGHTS`; component/hazard conclusions are `INSUFFICIENT_EVIDENCE`. No source/blob/body/comment/patch/diff/archive retrieval, copying, execution, admission, active-stage or product change |
| GOV-REPO-001 | TODO | S | Long-term repository visibility, account plan and licensing/contribution terms | ADR-0002, owner | explicit decision and, only if approved, matching license/contribution updates | before merging an external contribution or returning the repository to private visibility |
| POC-GATES-001 | DONE | M | Approve versioned gates and result schema | DEC-020 / `OD-05` | `docs/stage0/DORA_MVP1_POC_GATES.md`, `docs/stage0/benchmark-result.schema.json`; defined `stage0-v0.1` gates Approved for Stage 0 | six undefined section 7 thresholds remain `Proposed`; affected verdict stays `INCONCLUSIVE` until pre-run approval |
| POC-SEARCH-GATES-002 | DONE | S | Select prospective storage/update predicates for `POC-SEARCH-001` | `DEC-043`; Project owner / `OD-12` | approved prospective `stage0-v0.2` Markdown + machine-readable Option B with paired control, physical D1–D3, exact repetitions/aggregation/environment/fallback | Option B approved on 2026-08-11 independently of prior Dora results; `benchmarkExecutionAllowed=false`; historical v0.1 evidence is not reclassified |
| POC-DEVICE-001 | DONE | M | Device/firmware matrix D1–D7 and first-run inventory | DEC-005/006/018; `OD-06` | `docs/stage0/device-matrix.yaml`; sanitized owner-phone-001 inventory assigned to D2 hardware profile | API/firmware/ABI/RAM inventory is recorded without a unique hardware ID; refreshed profile reports 36432 MiB free storage and satisfies D2 preflight; verdict remains `INCONCLUSIVE` |
| POC-DATA-001 | BLOCKED | L | RU/EN/mixed corpus governance and manifest | `OD-03`/`OD-04`/`OD-08`/`OD-09`; controlled storage/custodian/consent process | foundation in `docs/stage0/DORA_MVP1_DATASET_GOVERNANCE.md`; merged repository-owned [synthetic-public validator](evidence/poc-data-001/synthetic-public-manifest-validator-local-evidence-stage0-v0.1.json); PR #44 bounded [control-plane dry-run](evidence/poc-data-001/control-plane-dry-run-local-evidence-stage0-v0.1.json), synthetic manifest and non-formal review; [post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json) | these host-only artifacts prove deterministic synthetic metadata/control and ownership-safe sentinel mechanics only. `CUSTODIAN_UNASSIGNED`, collection `NOT_AUTHORIZED` and overall `NOT_RUN` remain; no governed manifest/dataset/consented record or controlled store exists. Purpose-recorded data remains blocked; real meetings and training are prohibited |
| POC-CAPTURE-001 | DONE | XL | Exploratory physical-microphone capture: 3 min, 15 min screen-off, then attempted 60 min screen-off | DEC-002/003/004/018/020; `OD-01`/`OD-06`/`OD-08`; POC-DEVICE-001 | isolated capture app/harness in PR #8 + sanitized Run A/B/C reports; no raw trace/audio in Git | Run A and Run B completed; Run C recorded 63:49 but is an `invalidated exploratory attempt` because only 25:58 was screen-off, a TrueConf call occurred and the phone was charging. All three completed recordings produced valid WAVs, zero AudioRecord errors and verified deletion/absence; no approved critical capture failure was observed on the tested Samsung device. Owner accepts exploratory closure with formal verdict `INCONCLUSIVE`; this is not production approval, D1–D7 PASS, eight-hour evidence, clean one-hour screen-off stability or all-device support. The clean 60-minute screen-off baseline is deferred for a separately scoped campaign on a dedicated test device. |
| POC-RECOVERY-001 | BLOCKED | XL | Encrypted writer kill/recovery | POC-GATES-001, POC-DEVICE-001, active v0.6 Gate Set/protocol, completed accountable governance and REC-I2B reviews, merged REC-I2A/REC-I2B, current `OWNER-AUTH-BATCH-20260819-01` / `OD-15`, future successful REC-I3, bounded exact-pin E-slot/D2 Recovery preflight, separate owner Recovery Phase A/measured execution authorization | All 15 v0.1–v0.5 artifacts remain immutable superseded audit records; active v0.6 still has 46 unique rows, one effective KEY-04, 184 Phase A injections and 138 full-physical injections. PR #38 source-equal squash-merged [REC-I2A graph/Product-IP evidence](evidence/poc-recovery-001/rec-i2a-actual-graph-product-ip-disposition-2026-08-17.json) and [REC-I2B runtime/review evidence](evidence/poc-recovery-001/rec-i2b-runtime-crypto-implementation-evidence-2026-08-17.json); PR #50 fixed only squash-main validator dispatch. [OD-15](stage0/DORA_MVP1_STAGE0_OWNER_DECISION_OD15.md) now permits REC-I3 implementation/non-metric verification/conditional merge, authorizes available bounded non-measured E-slot functional/fault/compatibility/preflight checks after exact-pin availability and task prerequisites, and defines E28/E30/E36-GAPI/E-NOGMS/E16K/E-NEXT ordering, with only E36-GAPI exactly pinned. | **Not READY / BLOCKED:** older package snapshots retain `implementationAllowed=false` and `implementationAllowedByThisPackage=false`; current named overlay is `recI3ImplementationAllowed=true`, `recI3NonMetricVerificationAllowed=true`, `recI3ConditionalMergeAllowed=true`. The REC-I3 harness/controller and refreshed exact graph are not yet implemented. `phaseAAllowed=false`, `executionAllowed=false`, `measuredExecutionAllowed=false`, `productionAdmissionAllowed=false` govern the Recovery campaign; ten blockers remain (`REC-RDY-01`, `REC-RDY-03`–`REC-RDY-11`). No preflight, fault campaign or measurement ran in this reconciliation. Recovery preflight remains after successful REC-I3; no Recovery hard-kill/fault or measured campaign is authorized. Full PASS still requires valid D1/D2/D5; D1/D5 remain deferred, and emulators cannot substitute physical mic/flash/battery/thermal/OEM/radio/VPN/arm64 evidence. Final `ADR-AUDIO-001` remains post-evidence. |
| POC-VAD-001 | BLOCKED | L | 90 s silence/max-cap deterministic replay | POC-GATES-001, POC-DATA-001 | merged [P2 frame-timing](evidence/poc-vad-001/p2-host-oracle-local-evidence-stage0-v0.1.json), PR #46 [P3 deterministic replay](evidence/poc-vad-001/p3-deterministic-integrated-replay-local-evidence-stage0-v0.1.json) and PR #49 [P4 PCM rotation](evidence/poc-vad-001/p4-synthetic-pcm-rotation-local-evidence-stage0-v0.1.json), indexed by the [post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json); acoustic matrix remains absent | pure-host evidence covers frozen timing/replay/file-rotation mechanics only; it is not acoustic, realtime, device, governed-corpus, storage-product or support evidence. Physical/acoustic execution and the overall PoC remain blocked/not run |
| POC-ASR-001 | BLOCKED | XL | Local RU/EN/mixed ASR benchmark | POC-GATES-001, POC-DATA-001, POC-DEVICE-001 | PR #53 merged [I1 synthetic WER/timestamp aggregation mechanics](evidence/poc-asr-001/i1-synthetic-scoring-oracle-local-evidence-stage0-v0.1.json) with source-equal tree/review/CI indexed by the [post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json); artifact/corpus/device report absent | the I1 oracle does not define normalization/alignment, choose or run a model, evaluate WER/RTF/PSS/thermal/16-KiB/device support or close a gate; PoC remains BLOCKED / NOT_READY / NOT_RUN |
| POC-DIAR-001 | BLOCKED | XL | Local/server diarization and correction load | POC-GATES-001, POC-DATA-001 | PR #55 merged [I1 synthetic DER/JER/count/review-flag mechanics](evidence/poc-diar-001/i1-synthetic-scoring-oracle-local-evidence-stage0-v0.1.json) with source-equal tree/review/CI indexed by the [post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json); governed corpus/model/license/device/correction report absent | no collar/overlap/alignment/threshold/model/license/device or correction-burden claim; no forced speaker if later gate/license fails; PoC remains BLOCKED / NOT_READY / NOT_RUN / NOT_AUTHORIZED |
| POC-BATTERY-001 | BLOCKED | L | Capture/VAD/ML energy and thermal matrix | POC-CAPTURE-001 | merged [capture-only controlled-comparator host-oracle evidence](evidence/poc-battery-001/capture-only-controlled-comparator-host-oracle-local-evidence-stage0-v0.1.json) and combined [publication closure](evidence/stage0-host-oracle-publication-closure-2026-08-18.json); controlled physical baseline/repeats/batterystats/thermal policy remain absent | the pure-host comparator validates arithmetic and attribution semantics only; it contains no energy, thermal, device, screen-off, VAD or ML measurement. The PoC remains blocked and no threshold or PASS is claimed |
| POC-DECISION-001 | BLOCKED | XL | Decision revision graph benchmark | POC-GATES-001, POC-DATA-001 | merged projection oracle plus PR #45 [I2 deterministic synthetic harness](evidence/poc-decision-001/decision-deterministic-synthetic-harness-local-evidence-stage0-v0.1.json) and PR #54 [I3 synthetic metamorphic campaign](evidence/poc-decision-001/decision-i3-synthetic-campaign-local-evidence-stage0-v0.1.json), indexed by the [post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json); governed source/corpus/model scoring remains absent | host mechanics validate source-range/revision/user-ownership and metamorphic aggregation only. The synthetic 144-case campaign counts as zero governed cases, cannot make a decision final automatically and is not benchmark/model/quality evidence; overall PoC remains BLOCKED / NOT_RUN |
| POC-SEARCH-001 | DONE | M | Room FTS4 10k/1M Stage 0 evaluation | POC-GATES-001; POC-SEARCH-GATES-002; `OD-11`–`OD-13` | immutable valid FAIL/targeted/final observations, exact 66-component evaluation packet, paired harness and fail-closed readiness under `docs/evidence/poc-search-001/`; PR #52 exact [KSP 2.3.11 build-tool lock overlay](evidence/poc-search-001/build-tool-lock-overlay-ksp-2.3.11.json) indexed in the [post-PR43 main closure](evidence/stage0-post-pr43-main-integration-closure-2026-08-19.json) | Stage 0C remains formal `INCONCLUSIVE` with recommendation `BLOCKED`; PR #52 is build-tool maintenance only and does not reclassify historical measurements or admit KSP/Room/FTS/schema/runtime. Physical D1/D3, fresh preflight and measured v0.2 remain deferred; `benchmarkExecutionAllowed=false`; no production Legal/Security admission |
| POC-OFFLINE-001 | TODO | L | Airplane/no-GMS core dependency audit | prospective readiness contract/machine record; merged I1/I2 host evidence and I2 review; PR #48 [I3 static call-ledger validator](evidence/poc-offline-001/i3-static-call-ledger-local-evidence-stage0-v0.1.json); [post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json) | I1/I2/I3 prove bounded synthetic semantics and static call-ledger structure only. All 10 readiness blockers remain open (`0` closed); calibrated monitor, E-NOGMS/D4 and physical matrix, approved local model, durable product integration, reconnect and OS-blocked execution remain absent / `NOT_READY` / `NOT_RUN` / `NOT_AUTHORIZED` | no runtime zero-call claim follows from static structure. Usable local core still requires calibrated/device evidence; host semantics, static absence and repository CI are not an Offline PASS |
| POC-VPN-001 | TODO | L | VPN/route/idempotent multipart harness | BE-API-001 synthetic server contract; prospective [`poc-vpn-synthetic-api-stage0-v0.1`](stage0/DORA_MVP1_POC_VPN_SYNTHETIC_CONTRACT_STAGE0_V0_1.md), [machine record](evidence/poc-vpn-001/contract-record-stage0-v0.1.json), [pure-host oracle implementation record](evidence/poc-vpn-001/contract-kernel-implementation-stage0-v0.1.json), task-scoped [I2 hermetic-loopback implementation/evidence](evidence/poc-vpn-001/loopback-transport-implementation-evidence-stage0-v0.1.json) and [sanitized I2 advisory review record](evidence/poc-vpn-001/i2-implementation-advisory-review-2026-08-15.json) | I2 synthetic host-loopback subset is implemented, independently advisory-reviewed with `formalReviewer=false` and protected-squash-merged with exact-main CI green; physical VPN/route execution and the overall PoC verdict remain `NOT_RUN` / `NOT_AUTHORIZED` | one synthetic job/result in I2, no region switch or duplicate economic/deletion effect; neither kernel nor I2 is a physical POC-VPN PASS and this row remains TODO |

Post-PR43 POC-VPN additive publication fact: PR #51 merged the independently advisory-reviewed
[I3 host-hermetic fault-completion slice](evidence/poc-vpn-001/i3-host-fault-completion-local-evidence-stage0-v0.1.json)
with source/merge-tree and exact-head/main CI reconciliation in the
[post-PR43 host closure](evidence/stage0-post-pr43-host-review-closure-2026-08-19.json). The canonical
`POC-VPN-001` table row above remains byte-preserved for its immutable I2 integration locator. I3
adds no real DNS/TLS/external network/VPN/radio/route/provider/device execution or PASS; the PoC
remains `TODO`, `NOT_READY`, `NOT_RUN` and `NOT_AUTHORIZED`.

## 4. Design evidence backlog

| ID | State | Задача | Dependency | Exit evidence |
|---|---|---|---|---|
| DES-FOUND-001 | BLOCKED | Foundations/tokens/light-dark/device contrast | DEC-021–024 | D1 token report, no raw color drift |
| DES-FONT-001 | BLOCKED | Exact Manrope artifact test | DEC-024, GOV-IP-001 | RU/EN glyph, hinting, 200%, bytes, OFL/digest |
| DES-BRAND-001 | BLOCKED | Wordmark/icon directions | GOV-TRADEMARK-001 | three original directions; mdpi/themed/store checks |
| DES-IA-001 | BLOCKED | Low-fi shell/navigation/adaptive flow | DEC-026/032/041 | tree/first-click results and navigation ADR |
| DES-START-001 | BLOCKED | Permission/consent/preflight D2 | GOV-001, DEC-027 | comprehension/time/abandonment report |
| DES-WAVE-001 | BLOCKED | DoraWave D1 and state fixtures | POC-CAPTURE-001/VAD fixtures | state comprehension, no TalkBack spam/jank/audio impact |
| DES-STOP-001 | BLOCKED | Pause/Back/Stop/finalize D3 | capture state contract | zero accidental stop; persistent-state comprehension |
| DES-STORAGE-001 | DONE | Storage/retention/delete comprehension | DEC-013 | [`des-storage-retention-delete-v0.1`](design/DORA_MVP1_STORAGE_RETENTION_DELETE_CONTRACT.md) and [machine-readable decision evidence](evidence/des-storage-001/decision-record-v0.1.json) define exact scope, loss-of-source warnings, deterministic synthetic fixtures and accessible partial/retry states; contract complete; no implementation/conformance/user-research/deletion-execution claim |
| DES-EXPORT-001 | DONE | Export scope/privacy flow | DEC-017 | [`des-export-interaction-v0.1`](design/DORA_MVP1_EXPORT_INTERACTION_CONTRACT.md) and [machine-readable decision evidence](evidence/des-export-001/decision-record-v0.1.json) cover accessible selection, plain-share warning and cleanup state; contract complete; no implementation/conformance/user-research claim |
| DES-A11Y-001 | DONE | Component accessibility contract | DEC-040 | [versioned semantics/focus/touch/contrast/200% contract and evidence template](design/DORA_MVP1_COMPONENT_ACCESSIBILITY_CONTRACT.md); no component audit/conformance claim |
| DES-ADAPT-001 | TODO | D10 compact→expanded/posture | DES-IA-001 | resize/state/inset/hinge evidence |

## 5. Stage 1 — production project foundation (после Stage 0 go/no-go)

| ID | State | Задача | Gate | Acceptance |
|---|---|---|---|---|
| S01-ID-001 | BLOCKED | Approve production application ID and signing custody | GOV-TRADEMARK-001, owner | documented owner, package registration and key backup/runbook |
| S01-BUILD-001 | TODO | Admit pinned Android dependencies | Stage 00 green; update audit | lock/verification metadata, SBOM/notices, reproducible build |
| S01-ARCH-001 | TODO | Core IDs/clocks/result/error contracts | Stage 0 ADR outcomes | deterministic unit/property tests; no provider DTO leakage |
| S01-TEST-001 | TODO | Room migration harness and test fixtures | data ADRs | non-destructive migration tests; generated data only |
| S01-PORTS-001 | TODO | Add only near-term engine ports | selected PoC admission ADR | one fake + contract tests; no unused future SDK/modules |
| S01-RELEASE-001 | BLOCKED | Internal signing/release pipeline | S01-ID-001 | secrets outside Git, auditable ownership and update test |

## 6. Downstream task IDs reserved by decisions

These are not Ready until their stage dependencies pass.

| Area | IDs |
|---|---|
| Audio/data | `ADR-AUDIO-001`, `STORAGE-RETENTION-001`, `DATA-TASK-001`, `DATA-SUMMARY-001` |
| ML/NLP | `ML-CATALOG-001`, `NLP-SUMMARY-001`, `TASK-001` |
| Backend | `BE-LEGAL-001`, `BE-PROVIDER-001`, `BE-PROVIDER-002`, `BE-CONSENT-001`, `BE-AUTH-001`, `BE-API-001`, `BE-DELETE-001` |
| UI | `UI-HOME-001`, `EXPORT-001`, `SEC-PRIVACY-001`, `QA-A11Y-001` |
| Release | `REL-API-001`, `REL-STORE-001`, `REL-SIGN-001` |

## 7. Definition of Ready

Задача переводится в `READY`, когда в PR/issue description указаны:

1. hypothesis/user value и явный non-goal;
2. source requirements/DEC/ADR;
3. dependencies и разрешённые artifacts/data;
4. measurable acceptance и failure fallback;
5. test matrix, privacy/logging limits и cleanup;
6. expected files/modules и branch name;
7. отсутствие необходимости принять P0 решение молча.

## 8. Definition of Done

- change scoped and reviewed through PR;
- relevant local/CI checks green;
- evidence/report is reproducible and versioned;
- no secrets/private datasets/unapproved binaries;
- status/backlog/DEC/ADR updated when outcome changes truth;
- user/manual truth is never overwritten by a model result;
- no merge to `main` from the current task unless the owner explicitly scopes that merge.


## REC-I3 streaming persistence governance amendment — 6 September 2026

Owner-confirmed `DEC-046`, accepted ADR-0005, and prospective Gate Set/protocol v0.7 pin the exact streaming persistence contract to combined baseline `3c63ab09874f4d089e4363985aa8b5c99900c122` / tree `718eae8d8d619d17c25ac9d025e0e24db3d52f9e`. The governance candidate adds schema-v4 migration, checkpoint/source-witness, sealed outcome/rejected-observation, exact/conservative ACTIVE retained-range, hash-only replay, K12-PERSISTENCE, and TRU-03 stream semantics only as a contract. It preserves the 46/184/138/120 campaign counts, keeps K12-CONSUMER deferred, and changes no v0.1-v0.6 artifact.

Persistence source remains blocked until this exact eight-file governance commit receives an independent CLEAN review. `POC-RECOVERY-001` remains `BLOCKED / NOT_READY`; all ten active blockers remain open and `REC-RDY-02` remains historically closed. No device/emulator execution, Recovery preflight, process-death/fault/measured campaign, durability claim, PASS/READY, dependency/production admission, consumer intent, cross-process guarantee, retirement, or merge follows.

## REC-I3 streaming result-boundary governance amendment — 6 September 2026

'DEC-047', ADR-0006, and Gate Set/protocol v0.8 supersede v0.7 only for the controller result mapping, class-specific strict references, receipt lifecycle/evidence delivery, and legacy ambiguous-commit spelling. v0.7 remains byte-identical and immutable. The corrected boundary has twenty mappings, preserves canonical 'JOURNAL_OPERATIONAL', separates exact-readback receipt core from the final post-close/post-sink receipt, and promises one bounded caller-retryable attempt without autonomous delivery.

This exact ten-path patch creates no v0.8 result-boundary/controller implementation, execution, or evidence. Later persistence source edits remain blocked pending independent CLEAN review. 'POC-RECOVERY-001' stays 'BLOCKED / NOT_READY'; ten blockers remain open, REC-RDY-02 remains historically closed, OD-15 flags and 46/184/138/120 counts remain unchanged, and no execution/admission/consumer/cross-process/retirement/merge claim follows.

## REC-I3 proven-VALID-rollback correction — 6 September 2026

'DEC-048' and ADR-0007 resolve only the outward representation of a semantic VALID attempt after both framework-proven rollback and reconciled absence. The existing 'Retry(JOURNAL, JOURNAL_OPERATIONAL, SQLITE)' mapping applies with no receipt, attempted IDs, references, admitted endpoint, success or ACTIVE claim. Absence without proven rollback stays 'JOURNAL_COMMIT_STATE_UNRESOLVED'; the 4/6/20/4 boundary and pinned v0.7/v0.8 artifacts remain unchanged.

Independent focused review of the immutable amendment returned CLEAN before the affected mapping was enabled. No readiness, execution, campaign, production, consumer, retirement or merge status changes.

## REC-I3 observable streaming controller local candidate — 6 September 2026

The isolated `:poc:recovery` local candidate now composes the accepted v0.8 controller boundary with the existing one-descriptor streaming gateway, journal, Tink prerequisite crypto and bounded evidence port. Host tests cover the exact 4/6/20/4 mapping, constructor/reference invariants, read and precedence boundaries, replay/conflict/rollback outcomes, receipt/evidence ordering, sanitization, resource lifetime and zero-effect guards. Independent final review of immutable head `2f3d48cf813759e391a0281195b689cad1721b91` returned `REVISE 0/3/0`: persisted public evidence omitted mandatory safe positive facts, sealed replay touched prerequisite artifacts/Tink before outcome discovery, and replay did not bind oracle-derived row facts and identities to the supplied oracle. The first author-local repair projected the full sanitized positive evidence shape, discovered exact replay before the fresh-only prerequisite path, and reconstructed the oracle-bound outcome/range before source replay. Independent rereview of repaired head `7232c3f489908c6f7abdaeb2e2a1597996dc540f` then found that replay still trusted the stored retained-range hash. The current successor recomputes that exact range hash through the same frozen descriptor, compares it before receipt/evidence, and rejects a self-consistent altered hash and range identity. The focused Recovery tests, full Recovery JVM suite, Spotless, Detekt, Android lint and host SQLite schema-v4 verifier pass; exact source digests and commands are recorded in the local evidence JSON.

The published `7d7b81bf593cd1fa348f04703e9e22278db6be22` candidate's CI exposed a governance-only omission: Stage 00 still required the registry to end at DEC-044 although the authoritative ordered registry ends at DEC-048. The current successor pins that exact terminal decision, invokes Stage 00 from the Recovery validator, and admits `tools/validate_stage00.py` only through the resulting exact 16-path observable-controller profile. Kotlin bytes and the reviewed Android evidence remain unchanged.

Independent review of CI-correction head `5f897f585b7a4f2131324057823fb8ba5a42dde5` returned `REVISE 0/0/1/0`: its decision-label check used substring membership and could accept a required token in prose, a prefixed label, or an unknown metadata label. The current successor parses anchored field lines, preserves the ordered historical DEC-001–044 required schema, and enforces the exact ordered closed metadata blocks for DEC-045–048. Direct in-memory mutation tests cover relocation, prefix/misspelling, omission, extra metadata, the authoritative registry, and DEC-049 rejection.

Exact PR merge-ref CI run `34034200851` then exposed a test-fixture isolation defect: v0.8 regression tests changed only the simulated branch and retained the current observable-controller pull-request identity. The production validator correctly rejected that mismatched head ref before the intended assertions. The test-only successor constructs simulated v0.8 local lifecycles without inherited pull-request context and separately pins rejection of the real mismatched context.

Exact-head CI run `34035616019` showed that the first test-fixture repair cleared the verified pull-request context but retained GitHub's synthetic two-parent merge commit as the simulated-local HEAD. The production v0.8 linear-history guard correctly rejected that merge. The current test-only successor restores the verified pull-request source head before clearing context; an exact two-parent merge-ref regression reaches the real history check, proves the restored range contains no merge commit, and keeps the identity-drift negative control.

Exact-head CI run `34036673882` is terminal `SUCCESS`; both `android-bootstrap` and `search-smoke` passed with the source-head fixture repair. Under the owner's recurrence-prevention amendment, the mocked source-head check is now replaced by a retained regression that creates an isolated real two-parent merge checkout and invokes the actual Recovery `--self-test` entry point. Its verified-PR leaf proves source-head restoration and retains actual-entrypoint rejection for a wrong event head plus rejection of a genuine merge treated as local history.

Immutable review of retained-regression head `9111913753d40d9640ed07b43e0c6320290cf61e` returned `REVISE 0/0/1/0` because its two nested entrypoint subprocesses had no test-owned timeout, recursion guard, or descendant cleanup. The current test-only repair fails closed before local orchestration when PR event state lacks verified context, runs both children in bounded process groups, terminates their process trees on timeout or failure, and retains the successful leaf marker and both identity/history negatives.

Immutable rereview of bounded-child head `2de6d8238d99e71ae573ffa29481a59e052c0efd` returned `REVISE 0/0/1/0` because Windows cleanup targeted the already-dead nonzero parent PID and could leave its grandchild alive. The current successor creates each Windows child suspended, assigns it to a kill-on-close Job Object before resuming it, and retains the Job handle through completion. Live controls prove both timeout and immediate nonzero-parent descendants are terminated, their delayed markers remain absent, and the temporary directories are removable; POSIX process-group cleanup is unchanged.

This Job-bound retained-regression successor remains pending immutable independent review. `POC-RECOVERY-001` remains `BLOCKED / NOT_READY`; ten active blockers remain open, `REC-RDY-02` remains historically closed, and `fullRecI3Completed=false`. No device/emulator or preflight run, process-death/fault/measured campaign, Android durability claim, production/dependency admission, consumer intent, cross-process guarantee, range retirement, push, Pull Request edit, merge or next slice is claimed.
