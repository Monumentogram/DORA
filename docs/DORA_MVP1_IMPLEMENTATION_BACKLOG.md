# Dora MVP 1 — Executable Backlog

Версия: Stage 0D POC-RECOVERY-001 reviewed/merged I1 contract foundation / execution hold / GOV-OMI-001 Phase A metadata complete\
Дата: 18 августа 2026 года\
Owner approvals effective: 4 августа 2026 года (`OD-01`–`OD-10`), 11 августа 2026 года (`OD-11`–`OD-13`), recovery constraints / prospective `REC-JSR305-EXCLUDE-001` 12 августа 2026 года (`OD-14`; governance package only), and exact task-scoped `REC-I1-AUTH-20260813-01` on 13 августа 2026 года (pure contract foundation only; no runtime crypto/execution/merge)\
Owner GOV-OMI scope: 18 августа 2026 года — task definition plus
`GOV-OMI-PHASE-A-PUBLIC-METADATA-AUDIT-AUTH-20260818-01`; public GitHub metadata collection is
complete, while source/blob/archive and issue/PR-content retrieval, copying, execution and
admission remain unauthorized.\
Источник порядка: Technical Plan §37/§39, Design Spec §36/§39 и readiness gates.

Current `POC-RECOVERY-001` delta: the isolated `:poc:recovery` module now supplies only the
owner-authorized pure REC-I1 identities, bounded codecs, algebra/taxonomy/catalog rules and host-JVM
tests. It adds no Tink/JSR-305/runtime dependency, Android runtime API, harness or product edge.
PR #15 exact HEAD `ee7bb00b09a282df7a8fb3b4d3481a5abd4d0177` received an independent OpenAI
Codex / GPT-5 AI implementation advisory review (`formalReviewer=false`,
`NO_FURTHER_CHANGES_REQUIRED`, P0/P1/P2=0/0/0, `REC-I1-IMPL-001..004=CLOSED`, 62/62 Recovery JVM
tests) and protected-squash-merged as `f2bc8c95bbe8af0d010968fff2ca175851728bf2`. PR #16 then
changed only the governance validator, closed its post-merge branch-lifecycle defect under a
separate AI advisory re-review (`formalReviewer=false`, `NO_FURTHER_CHANGES_REQUIRED`,
P0/P1/P2=0/0/0), and protected-squash-merged as
`685e759290f8987444280b05e69b9d4d0070424e`; exact-main post-merge CI passed. These completed
audit facts do not supply Recovery runtime or execution evidence; GitHub is authoritative for live
PR/branch/CI state.
`implementationAllowed=false`, `implementationAllowedByThisPackage=false`, `executionAllowed=false`
and all ten still-open `REC-RDY` blockers remain authoritative. The backlog row below retains the
full runtime/campaign Definition of Ready; references there to module or task authorization absence
are superseded only for this narrow I1 foundation.

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
| POC-DATA-001 | BLOCKED | L | RU/EN/mixed corpus governance and manifest | `OD-03`/`OD-04`/`OD-08`/`OD-09`; controlled storage/custodian/consent process | foundation in `docs/stage0/DORA_MVP1_DATASET_GOVERNANCE.md`; owner purpose/retention rules approved, actual manifest absent | synthetic-first; purpose-recorded data blocked until consent/private store/access/deletion controls; real meetings and training prohibited |
| POC-CAPTURE-001 | DONE | XL | Exploratory physical-microphone capture: 3 min, 15 min screen-off, then attempted 60 min screen-off | DEC-002/003/004/018/020; `OD-01`/`OD-06`/`OD-08`; POC-DEVICE-001 | isolated capture app/harness in PR #8 + sanitized Run A/B/C reports; no raw trace/audio in Git | Run A and Run B completed; Run C recorded 63:49 but is an `invalidated exploratory attempt` because only 25:58 was screen-off, a TrueConf call occurred and the phone was charging. All three completed recordings produced valid WAVs, zero AudioRecord errors and verified deletion/absence; no approved critical capture failure was observed on the tested Samsung device. Owner accepts exploratory closure with formal verdict `INCONCLUSIVE`; this is not production approval, D1–D7 PASS, eight-hour evidence, clean one-hour screen-off stability or all-device support. The clean 60-minute screen-off baseline is deferred for a separately scoped campaign on a dedicated test device. |
| POC-RECOVERY-001 | BLOCKED | XL | Encrypted writer kill/recovery | POC-GATES-001, POC-DEVICE-001, completed distinct accountable formal human governance review evidence, completed pure REC-I1 advisory implementation review and exact task-scoped owner authorization/non-metric verification, separately scoped future runtime implementation review and owner authorization, complete runtime non-metric verification, future exact recovery-only zero-JSR305 graph/package/release-R8 evidence and scoped Product/IP disposition of that actual graph, fresh emulator/D2 SQLite/Keystore/filesystem preflight, separate owner execution authorization | Prospective Gate Set `poc-recovery-stage0-v0.6` / protocol `poc-recovery-protocol-stage0-v0.6`: exact v0.3 Streaming/microfile, v0.4 ninth-family durable `key-confirmation/run.kc` and unchanged v0.5 contracts inherited by SHA-256. v0.6 materializes exactly 46 unique active rows with one effective `KEY-04`; only decrypt authentication/AAD failure after all exact stored-identity/usable-alias/controller-key-replacement preconditions returns only `KEY_UNAVAILABLE_KEY_MISMATCH`. Successful decrypt with malformed/wrong plaintext remains KCF-07 → `CORRUPT_KEY_CONFIRMATION`. Phase A 184 and full physical 138 injections remain separate from 12 strata/120 base hard kills per candidate; all 15 v0.1–v0.5 artifacts are SHA-256-pinned superseded audit artifacts. PR #12 merged the v0.6 package to `main`; the separate OpenAI Codex (GPT-5) exact-HEAD advisory re-review completed without actionable documentary findings and with `formalReviewer=false`. `REC-REV-20260812-01` is closed; both non-formal AI records remain unchanged and do not close `REC-RDY-02`. Novikova Katerina, acting in individual professional capacity with Rambus listed only as affiliation, completed the formal read-only review with `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW`, closing `REC-REV-20260812-02` and `REC-RDY-02` without implementation, Phase A or execution authority. Prospective `REC-JSR305-EXCLUDE-001` and exact governance authenticity/LICENSE/NOTICE evidence are closed; excluded JSR-305 terms remain uninterpreted and use/distribution unapproved. PR #15 exact HEAD `ee7bb00b09a282df7a8fb3b4d3481a5abd4d0177` then received the independent OpenAI Codex / GPT-5 AI implementation advisory disposition `NO_FURTHER_CHANGES_REQUIRED` with `formalReviewer=false`, no P0/P1/P2 findings, `REC-I1-IMPL-001..004=CLOSED` and 62/62 Recovery JVM tests, and protected-squash-merged as `f2bc8c95bbe8af0d010968fff2ca175851728bf2`. Its first post-merge CI exposed only the main-branch validator lifecycle defect. PR #16 changed only that validator, received a clean separate AI advisory re-review with `formalReviewer=false`, merged as `685e759290f8987444280b05e69b9d4d0070424e`, and passed exact-main post-merge CI. | **Not READY:** `implementationAllowed=false`; `executionAllowed=false`; the pure contract module exists, but no runtime dependency, harness or measured execution exists. Future runtime `:poc:recovery` scope must have zero JSR-305 components and zero packaged JSR-305 definitions across all resolvable compile/runtime/unit-test/androidTest/benchmark/release and packaging/runtime inputs plus its locks/verification metadata. Existing other-module buildscript/AGP/UTP/lint/tooling and app/capture/search lockfiles are outside this boundary and alone do not block recovery. Exact three-line R8 rules must pass release R8 with no unresolved missing classes; broad `dontwarn` is forbidden. The pure I1 review/authorization/verification lifecycle is complete only for that foundation; future actual graph Product/IP disposition, independent `Modifier` resolution if present, separately authorized runtime implementation review, complete runtime implementation verification and preflight remain open. Phase A permits only FAIL/INCONCLUSIVE; full PASS requires the complete D1/D2/D5 profile. D2 reuse requires exact commit/protocol/Gate Set/fixture/injection/device/preflight/validity match; otherwise repeat it. D1/D5 remain deferred. Final `ADR-AUDIO-001` remains post-evidence. |
| POC-VAD-001 | BLOCKED | L | 90 s silence/max-cap deterministic replay | POC-GATES-001, POC-DATA-001 | fake monotonic replay and acoustic matrix | 89.5/90/90.5, resume 89.9, noise, >10 min; documented profile |
| POC-ASR-001 | BLOCKED | XL | Local RU/EN/mixed ASR benchmark | POC-GATES-001, POC-DATA-001, POC-DEVICE-001 | pinned artifacts/digests + WER/RTF/PSS/thermal report | select tier or Vosk/hybrid fallback; 16-КБ runtime evidence |
| POC-DIAR-001 | BLOCKED | XL | Local/server diarization and correction load | POC-GATES-001, POC-DATA-001 | DER/JER/MAE/correction report + exact weight license | no forced speaker if gate/license fail |
| POC-BATTERY-001 | BLOCKED | L | Capture/VAD/ML energy and thermal matrix | POC-CAPTURE-001 | controlled baseline/repeats/batterystats/thermal policy | capture ≤ approved overhead; heavy ML deferred if unsafe |
| POC-DECISION-001 | BLOCKED | XL | Decision revision graph benchmark | POC-GATES-001, POC-DATA-001 | ≥100 adjudicated cases, source validator, scoring | no auto-final if precision/source gate fails |
| POC-SEARCH-001 | DONE | M | Room FTS4 10k/1M Stage 0 evaluation | POC-GATES-001; POC-SEARCH-GATES-002; `OD-11`–`OD-13` | immutable valid FAIL/targeted/final observations, benchmark+debug dependency lock/IP inventory, v5 non-measurement closure assessment, paired harness/combiner and fail-closed readiness evidence under `docs/evidence/poc-search-001/` | Stage 0C is closed honestly as formal `INCONCLUSIVE` with recommendation `BLOCKED`. Historical emulator measurements remain unchanged; approved prospective Option B is unmeasured. The exact 66-component/package review is `EVALUATION_APPROVED` only for Stage 0 research, and the paired harness is verified. Physical D1/D3, fresh exact-commit preflight and the measured v0.2 campaign are deferred to separate future scope; `benchmarkExecutionAllowed=false`. This does not admit FTS4, the PoC schema, dependencies, production Legal or production Security. |
| POC-OFFLINE-001 | TODO | L | Airplane/no-GMS core dependency audit | selected local harnesses; prospective [`poc-offline-readiness-stage0-v0.1`](stage0/DORA_MVP1_POC_OFFLINE_READINESS_CONTRACT_STAGE0_V0_1.md) and [machine record](evidence/poc-offline-001/readiness-contract-stage0-v0.1.json) | static readiness/call-surface contract is complete; integrated harness, calibrated call ledger, exact device matrix, approved local model and OS-blocked physical execution remain absent / `NOT_READY` / `NOT_RUN` / `NOT_AUTHORIZED` | zero app-attributed forbidden calls and usable local core require later integrated synthetic/device evidence; static absence and isolated PoCs are not a PASS |
| POC-VPN-001 | TODO | L | VPN/route/idempotent multipart harness | BE-API-001 synthetic server contract; prospective [`poc-vpn-synthetic-api-stage0-v0.1`](stage0/DORA_MVP1_POC_VPN_SYNTHETIC_CONTRACT_STAGE0_V0_1.md), [machine record](evidence/poc-vpn-001/contract-record-stage0-v0.1.json), [pure-host oracle implementation record](evidence/poc-vpn-001/contract-kernel-implementation-stage0-v0.1.json), task-scoped [I2 hermetic-loopback implementation/evidence](evidence/poc-vpn-001/loopback-transport-implementation-evidence-stage0-v0.1.json) and [sanitized I2 advisory review record](evidence/poc-vpn-001/i2-implementation-advisory-review-2026-08-15.json) | I2 synthetic host-loopback subset is implemented, independently advisory-reviewed with `formalReviewer=false` and protected-squash-merged with exact-main CI green; physical VPN/route execution and the overall PoC verdict remain `NOT_RUN` / `NOT_AUTHORIZED` | one synthetic job/result in I2, no region switch or duplicate economic/deletion effect; neither kernel nor I2 is a physical POC-VPN PASS and this row remains TODO |

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
