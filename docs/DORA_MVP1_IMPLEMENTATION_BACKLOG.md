# Dora MVP 1 — Executable Backlog

Версия: Stage 00\
Дата: 4 августа 2026 года\
Источник порядка: Technical Plan §37/§39, Design Spec §36/§39 и readiness gates.

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
| S00-DOC-002 | DONE | Создать Product Decisions registry | S00-DOC-001 | DEC-001–DEC-042; no silent `Approved` decisions |
| S00-DOC-003 | DONE | Зафиксировать backlog, status, ADR и Codex rules | S00-DOC-001 | root `AGENTS.md`, contributing/status/backlog/ADR linked |
| S00-ANDROID-001 | DONE | Создать минимальный Android skeleton | DEC-005/006/015; ADR-0001 | wrapper/JVM 17/min 28/compile-target 36; placeholder only; four unit-test modules green |
| S00-CI-001 | DONE | Добавить GitHub Actions CI | S00-ANDROID-001 | pinned least-privilege workflow validates wrapper/docs, locks, test/lint/assemble and native alignment |
| S00-VERIFY-001 | DONE | Локально проверить clean checkout commands | S00-CI-001 | 186-task test/lint/assemble graph green; Stage 00 validator and 16-KiB ELF/APK gates green; generated artifacts ignored |
| S00-PR-001 | DONE | Commit/push/open PR without merge | S00-VERIFY-001 | checked Stage 00 commit/branch published; ready-for-review PR #1 targets `main`; no merge |
| S00-CI-002 | DONE | Проверить/исправить GitHub Actions | S00-PR-001 | PR-triggered `android-bootstrap` completed successfully, including test/lint/assemble and native gates |
| S00-SEC-001 | DONE | Провести pre-public secret/privacy audit | S00-CI-002 | checksum-verified Gitleaks 8.30.1 scanned all refs/full history and both branch trees; all three commit trees, filenames, identities, Actions configuration/logs and GitHub secret metadata were independently checked; no real secret, PII, private path or accidental artifact found |
| S00-GIT-004 | DONE | Защитить `main` после появления stable check name | S00-SEC-001 | owner explicitly approved temporary public visibility; existing repository changed in place and API-verified public; `main` requires up-to-date GitHub Actions app `15368` check `android-bootstrap`, PR, linear history and conversation resolution; admin enforcement on, force-push/delete off; secret scanning and push protection enabled; ADR-0002 |

## 3. Stage 0 — обязательные governance и PoC

Ни одна задача этого раздела не разрешает production feature implementation. Каждый PoC получает отдельную ветку, synthetic/consent-governed data и machine-readable report.

| ID | State | Size | Задача | Depends on | Deliverable | Acceptance / fallback |
|---|---|---:|---|---|---|---|
| GOV-001 | BLOCKED | M | Markets/legal/consent decision pack | DEC-001/002/027 | counsel/product memo, versioned copy scopes | recording beta and cloud stay off until approved |
| GOV-PRIVACY-001 | READY | M | Privacy/data-flow/threat assumptions v1 | DEC-009/014/015 | data inventory, forbidden telemetry, retention owners | no raw content in logs/analytics; unresolved flows explicitly blocked |
| GOV-TRADEMARK-001 | TODO | S | Name/package/trademark availability | DEC-025 | evidence + approved production identifier candidate | no registration/store asset before approval |
| GOV-IP-001 | READY | S | Reference/font/model asset IP rules | DEC-024/042 | controlled-reference policy and notices checklist | no unlicensed reference bitmap in repo/product |
| GOV-REPO-001 | TODO | S | Long-term repository visibility, account plan and licensing/contribution terms | ADR-0002, owner | explicit decision and, only if approved, matching license/contribution updates | before merging an external contribution or returning the repository to private visibility |
| POC-GATES-001 | BLOCKED | M | Approve versioned gates and result schema | DEC-020 | `benchmark-result.schema.json`, owner-approved thresholds | no PoC pass/fail without named gate/fallback |
| POC-DEVICE-001 | READY | M | Device/firmware matrix D1–D7 | DEC-005/006/018 | `device-matrix.yaml`, procurement and OTA policy | API/OEM/RAM/route/16-KБ coverage recorded |
| POC-DATA-001 | BLOCKED | L | RU/EN/mixed corpus governance and manifest | DEC-001/014 | consent/access/retention/annotation process; immutable split | no private audio in Git; no participant leakage across split |
| POC-CAPTURE-001 | BLOCKED | XL | 1 h/1–3–8 h AudioRecord microphone FGS | GOV-001, POC-GATES-001, POC-DEVICE-001 | isolated capture app/harness + traces | start/finalize/data-loss/energy gates; fallback narrows supported matrix |
| POC-RECOVERY-001 | BLOCKED | XL | Encrypted writer kill/recovery | POC-GATES-001, POC-DEVICE-001, ADR-AUDIO-001 | ≥100 injected kill points, authenticated prefix report | Tink only if all committed prefix recoverable; otherwise sealed microfiles |
| POC-VAD-001 | BLOCKED | L | 90 s silence/max-cap deterministic replay | POC-GATES-001, POC-DATA-001 | fake monotonic replay and acoustic matrix | 89.5/90/90.5, resume 89.9, noise, >10 min; documented profile |
| POC-ASR-001 | BLOCKED | XL | Local RU/EN/mixed ASR benchmark | POC-GATES-001, POC-DATA-001, POC-DEVICE-001 | pinned artifacts/digests + WER/RTF/PSS/thermal report | select tier or Vosk/hybrid fallback; 16-КБ runtime evidence |
| POC-DIAR-001 | BLOCKED | XL | Local/server diarization and correction load | POC-GATES-001, POC-DATA-001 | DER/JER/MAE/correction report + exact weight license | no forced speaker if gate/license fail |
| POC-BATTERY-001 | BLOCKED | L | Capture/VAD/ML energy and thermal matrix | POC-CAPTURE-001 | controlled baseline/repeats/batterystats/thermal policy | capture ≤ approved overhead; heavy ML deferred if unsafe |
| POC-DECISION-001 | BLOCKED | XL | Decision revision graph benchmark | POC-GATES-001, POC-DATA-001 | ≥100 adjudicated cases, source validator, scoring | no auto-final if precision/source gate fails |
| POC-SEARCH-001 | READY | M | Room FTS4 10k/1M benchmark | POC-GATES-001 | generated data, migration/rebuild/query harness | p95/gate, no operator injection; FTS4 or documented fallback |
| POC-OFFLINE-001 | TODO | L | Airplane/no-GMS core dependency audit | selected local harnesses | OS-blocked network run, call ledger | zero core network/login/GMS dependency |
| POC-VPN-001 | TODO | L | VPN/route/idempotent multipart harness | BE-API-001 synthetic server contract | failure-injection report | one job/result, no region switch or duplicate billing |

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
| DES-STORAGE-001 | TODO | Storage/retention/delete comprehension | DEC-013, BE-DELETE-001 | exact scope and loss-of-source test |
| DES-EXPORT-001 | TODO | Export scope/privacy flow | DEC-017 | accessible selection, plain-share warning, cleanup state |
| DES-A11Y-001 | READY | Component accessibility contract | DEC-040 | semantics/focus/touch/contrast/200% template |
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
- no merge to `main` from the Stage 00 task itself.
