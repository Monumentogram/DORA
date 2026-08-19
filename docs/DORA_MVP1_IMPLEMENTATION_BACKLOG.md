# Dora MVP 1 — Executable Backlog

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
