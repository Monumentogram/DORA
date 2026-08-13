# DORA POC-RECOVERY-001 — Engineering/Security review-досье

**Версия:** Advisory dossier v1.0
**Статус всего документа:** **ADVISORY DRAFT FOR HUMAN REVIEWER**
**Формальный disposition:** не выбран; поле оставлено Novikova Katerina.

## 1. Identity and scope

| Поле | Значение |
|---|---|
| Dossier preparer/model | OpenAI Codex (GPT-5) |
| Organization | OpenAI |
| Role | AI technical analysis preparer |
| Dossier date | 2026-08-13, Europe/Moscow |
| Primary reviewed package | [`[b5371f523e4471aca48a63a82b9ee4e1f9a7e0fd](https://github.com/Monumentogram/DORA/commit/b5371f523e4471aca48a63a82b9ee4e1f9a7e0fd)`](https://github.com/Monumentogram/DORA/commit/b5371f523e4471aca48a63a82b9ee4e1f9a7e0fd) |
| Primary reviewed tree | `1fd03fd489836c65f7ee043298f8f6d32df00c55` |
| Contextual current main | [`[5c97f09f3165a90afa5300b30499e0dcb36168f2](https://github.com/Monumentogram/DORA/commit/5c97f09f3165a90afa5300b30499e0dcb36168f2)`](https://github.com/Monumentogram/DORA/commit/5c97f09f3165a90afa5300b30499e0dcb36168f2) |
| Current main tree | `1765ccba62936b8600e8e34174d0ab0bb2f35493` |
| Assigned human reviewer | Novikova Katerina |
| Affiliation | Rambus |
| Project role | Distinct accountable Stage 0 Recovery Engineering/Security reviewer |
| `formalReviewer` for this AI dossier | `false` |
| Review mode | Read-only documentary/static/cryptographic verification |
| Production Security approval | Нет |
| Production Legal approval | Нет |
| Dependency admission | Нет |
| Implementation/execution performed | Нет |

Это досье не является formal disposition Novikova Katerina, не закрывает `REC-RDY-02` и не приписывает Rambus корпоративное одобрение. Rambus рассматривается только как affiliation, пока Katerina явно не выберет capacity «authorized representative of Rambus».

### Commit/tree/blob identity

Live GitHub verification установила:

- [[PR #12](https://github.com/Monumentogram/DORA/pull/12)](https://github.com/Monumentogram/DORA/pull/12): merged, HEAD `b537…`, squash commit `f14c…`; оба имеют tree `1fd03f…`.
- [[PR #13](https://github.com/Monumentogram/DORA/pull/13)](https://github.com/Monumentogram/DORA/pull/13): merged 2026-08-13 08:42:06 Europe/Moscow, HEAD `252508…`, merge/current-main commit `5c97…`; оба имеют tree `1765cc…`.
- Live `refs/heads/main` указывает на `5c97…`.

Ключевые v0.6 blobs побайтно одинаковы в primary target и current main:

| Artifact | Blob в `b537…` и current main |
|---|---|
| Gate Set Markdown | `708d4e229776ed6d20a89b4c7d00b718650e5750` |
| Gate Set JSON | `69086959baf5a9988ba7367c9a738ec0bd3efaea` |
| Protocol JSON | `e02b92b0817385cecec646e07b631e85d7174bc9` |
| Governance remediation v0.6 | `958c743dbc276c5538638bbc0d0d93b5a3daa664` |
| Findings ledgers v0.1–v0.5 | Все пять идентичны |
| Dependency/authenticity/license/security/SQLite evidence | Все проверенные blobs идентичны |

Из 43 indexed evidence/contract paths 39 идентичны. Четыре изменились только как post-merge governance metadata: review task, `readiness.json`, `review-roles.json`, OD-14. Остальные изменения PR #13 — status/backlog/index/post-merge evidence и validators, без изменения криптографических, storage, SQLite, fault или campaign contracts.

Все 15 исторических v0.1–v0.5 Gate Markdown/Gate JSON/protocol JSON прошли SHA-256 pin verification и остаются superseded/non-executable.

---

## 2. Executive summary

**ADVISORY DRAFT FOR HUMAN REVIEWER**

v0.6 пока не выбирает победителя между двумя recovery-кандидатами. Он замораживает точный экспериментальный контракт для:

1. одного Tink `StreamingAead` stream с durable one-segment lookahead;
2. отдельных пятиисекундных Tink AEAD microfiles с authenticated manifest.

Главное изменение v0.6 — устранение неоднозначности `KEY-04`. Теперь эта строка означает только один сценарий: контроллер заменил underlying Keystore key на другой действующий AEAD key, старый confirmation ciphertext и его записанные length/hash сохранены, alias доступен, exact AAD вычислен, а decrypt завершается authentication/AAD failure. Единственный oracle — `KEY_UNAVAILABLE_KEY_MISMATCH`.

`KCF-07` отделён потому, что это принципиально другая стадия отказа: правильный alias и AAD успешно расшифровывают специально созданный ciphertext, но plaintext после decrypt оказывается malformed или содержит неправильную identity. Это `CORRUPT_KEY_CONFIRMATION`, а не key mismatch.

Проверенные гарантии являются **design claims**. Нет реализации, harness, recovery Gradle graph, device preflight или execution evidence, поэтому досье не утверждает, что Android/Tink/SQLite runtime фактически выполняет контракт.

Возможный human disposition `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW` означал бы только следующее:

- Katerina принимает v0.6 как достаточно точную спецификацию для следующего, отдельно авторизованного этапа;
- Project owner после этого может отдельно решить, разрешать ли implementation;
- будущая реализация и non-metric verification должны снова пройти accountable review;
- это не разрешает dependency wiring, device execution, kill campaign, benchmark или measurement;
- `implementationAllowed=false`, `implementationAllowedByThisPackage=false`, `executionAllowed=false` и `measuredExecutionAllowed=false` остаются неизменными.

**AI advisory recommendation:** `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW`, при условии что documentary cleanup будет включён в recording PR и Katerina лично примет либо исправит выводы ниже. Это не formal disposition.

---

## 3. Verification table — immutable questions 1–12

Все conclusions ниже: **ADVISORY DRAFT FOR HUMAN REVIEWER**.

| ID | Requirement | Exact evidence locators | Independent reasoning | Validator/check corroboration | Advisory conclusion | Uncertainty / limitation | Required human decision | Level |
|---|---|---|---|---|---|---|---|---|
| 1 | `DURABLE_ONE_SEGMENT_LOOKAHEAD`, q/R/read/EOF/bound | v0.3 protocol `/candidates/0/checkpointModel`, `/recoveryReads`; DEC-044 Frozen invariants | `q=0/1 → R=0`; `q=2 → 4056`; `q=3 → 8136`; `q=4 → 12216`. Первый authenticated payload — 4056, последующие — 4080. Последний durable non-final segment остаётся sacrificial и не входит в `C`. Консервативный предел двух 4080-byte payloads: `8160/32000=0.255 s`. Только успешно вернувшийся `read()` увеличивает R; исключение отбрасывает весь caller buffer; только `-1` является normal authenticated EOF. | Governance validator и negative self-tests passed; формулы пересчитаны отдельно. | **VERIFIED** | Не проверено на реальном Tink stream; implementation evidence отсутствует. | ACCEPT / MODIFY / REJECT | P0 implementation blocker: REC-RDY-03 |
| 2 | Public non-deprecated Streaming construction | v0.3 `/candidates/0/construction`, `/keyModel` | Зафиксированы `AesGcmHkdfStreamingParameters`, input/derived 16/16, SHA-256, 4096-byte ciphertext segment, fresh run keyset, одна derived AES key на stream, nonce-prefix/index/last uniqueness, AAD once per stream, `RegistryConfiguration.get()`. Deprecated templates запрещены. | Static governance and dependency validators passed; online source/signature correspondence passed. | **VERIFIED** | API contract не заменяет compile/runtime implementation proof. | ACCEPT / MODIFY / REJECT | P0 REC-RDY-03 |
| 3 | Exact microfile AEAD | v0.3 `/candidates/1/aeadTemplate`, `/cadences` | 32-byte key + 12-byte IV + 16-byte tag + 5-byte TINK prefix дают `160000 → 160033`, overhead 33. Fresh keyset на microfile. Только 5 s удовлетворяет 5-second tail gate; 15/30 s не PASS-eligible. | Governance validator passed; arithmetic checked separately. | **VERIFIED** | Нет Android AEAD execution or packaging evidence. | ACCEPT / MODIFY / REJECT | P0 REC-RDY-04 |
| 4 | Manifest and four AAD schemas | v0.3 `/candidates/1/manifest`, `/associatedDataSchemas`, `/rollbackModel` | Manifest имеет exact header/entry order, generation and previous-digest chain, contiguous strictly ordered entries, 721-entry/524288-byte bounds, no trailing bytes. Четыре AAD schemas связывают protocol/candidate/run и применимые generation/unit/range/previous digest. Без внешнего controller ledger локальная chain не даёт global anti-rollback guarantee. | Governance hash/self-tests passed. | **VERIFIED** | Parser и corruption handling пока design-only. | ACCEPT / MODIFY / REJECT | P0 REC-RDY-04 |
| 5 | Keystore, key confirmation, taxonomy, KEY-04/KCF-07 | v0.4 `/keyConfirmation`; v0.5 `/canonicalKeyTaxonomyV05`; v0.6 `/canonicalKeyTaxonomyV06`, `/faultCampaign/.../KEY-04` | Alias canonical lowercase UUID; generation только new run; Builder/`getAead` обязателен для access. Новая collision проверяет alias/reference/temp/final. Recovery идёт в точном девятишаговом порядке. `KEY-04` содержит все 8 preconditions и только decrypt auth/AAD failure. `KCF-07` требует successful decrypt и post-decrypt malformed/wrong identity. `DORAKC01` и `DORAKA01` раздельны; 13-step bootstrap заканчивается SQLite commit до publication. | Все KEY-04/KCF-07 negative mutations passed. | **VERIFIED** | Реальные Android Keystore invalidation/replacement semantics не исполнялись. | ACCEPT / MODIFY / REJECT | P0 REC-RDY-02/04 |
| 6 | Semantic commit and authenticated prefix | v0.3 `/definitions`, `/sqliteJournal`, `/storageProtocol/publicationSequences` | Commit — успешный `endTransaction()` после durable files/publication. Event после commit не может его понизить. `C` берётся из SQLite/authenticated publication intersection; `R` — только oracle-equal authenticated reads; `A` — завершённые writer calls. Поэтому нормативный порядок `0≤C≤R≤A`, committed loss `C-min(C,R)=0`, auth threshold 100%. | Static validator passed; EVT-01 present. | **VERIFIED** | Реальная filesystem/SQLite durability не доказана до preflight и fault execution. | ACCEPT / MODIFY / REJECT | P0 REC-RDY-03/04/06 |
| 7 | Publication sequences and namespace | v0.3 `/storageProtocol`; v0.4 `/storageAmendment`, `/cleanupAndQuarantine` | Посчитаны 9 streaming setup, 13 checkpoint, 21 microfile steps. Microfile transaction включает оба envelopes и оба ciphertext finals до SQLite commit/event. Temp mapping exact `final+".tmp"`, exclusive create/no overwrite, пять reconciliation states. Восемь исходных families плюс ninth key-confirmation family проходят containment/lstat/regular/no-symlink checks; confirmation quarantine transaction отдельна. | Sequence counts computed independently: 9/13/21; bootstrap 13. | **VERIFIED** | `fsync`/rename behavior на emulator/D2 не проверялось. | ACCEPT / MODIFY / REJECT | P0 REC-RDY-04/06 |
| 8 | SQLite and preflight | v0.3 `/sqliteJournal`; SQLite provenance evidence | WAL, FULL, autocheckpoint 0, foreign keys ON, single writer, non-exclusive transaction; exact relative names/lengths/SHA; deterministic SHA-256 `processingIntentId` with UNIQUE. Preflight требует version, source ID и canonical sorted-LF compile-options digest. | Static evidence passed; provenance intentionally says preflight incomplete. | **VERIFIED** | Fresh emulator/D2 facts отсутствуют; нельзя делать runtime verdict. | ACCEPT / MODIFY / REJECT | P0 REC-RDY-06 |
| 9 | Candidate-specific K01–K12 | v0.3 `/hardKillCampaign` | Streaming K02 — downstream OutputStream callback before delegate write; microfile K02 — after AEAD return before temp write. K04–K11 соответствуют точным fsync/rename/SQLite/event boundaries. K12 seeds фиксируют `A/C/R` и canonical outcome. 120/100/8, allocations, invalidators и max 20 replacements сохранены. | Governance validator and contract inspection passed. | **VERIFIED** | Barrier observability в реальном harness не доказана. | ACCEPT / MODIFY / REJECT | P0 REC-RDY-03/04/07 |
| 10 | 46 active rows, one KEY-04, campaign separation | v0.6 `/faultCampaign/activeEffectiveFaultMatrixV06`, Gate Set §§4–6 | Независимо посчитано 46 rows, 46 unique IDs, `KEY-04` один раз: 33 inherited effective IDs + 6 KCB + 6 KCF inherited + KCF-07. Historical v0.3 KEY-04 не активен дополнительно. KCF-07 outer length/hash обновляется, decrypt succeeds, затем plaintext validation fails. Matrix включает path/symlink, rollback, alias replacement, envelope swap и commit/event gap. | Count/uniqueness/KEY-04 and mutation negative tests passed. | **VERIFIED** | Faults не инъецировались; подтверждена спецификация, не runtime classification. | ACCEPT / MODIFY / REJECT | P0 REC-RDY-04/07 |
| 11 | Dependency authenticity/IP/R8 | dependency inventory; authenticity v0.3; license/NOTICE inventory; JSR analysis; JetBrains immutable verification | Все 8 JAR/POM pairs имеют exact SHA, publisher checksums и verified detached signatures. Два publisher-bound, шесть multisource/source-correspondence. Full fingerprints проверены; keyserver UID не принят как самостоятельный trust binding. Root Tink JAR: 1878 classes, 0 native, 540 shaded protobuf 4.33.6. JSR-305 Maven POM Apache-2.0 конфликтует с exact source BSD-3-Clause; terms не выбраны. Recovery-only exclusion требует zero component/package definitions, ровно три R8 rules, no broad `dontwarn`; `Modifier` остаётся отдельным future issue. | Static validator passed; online revalidation of 8 coordinates and immutable JetBrains LICENSE/NOTICE passed. | **VERIFIED** для governance packet | Нет actual Gradle graph/release R8/package; это не Legal/IP approval и не admission. | ACCEPT / MODIFY / REJECT | P0 REC-RDY-01/05/11 |
| 12 | Phase A/full/D2 reuse/120 kills/11 blockers | v0.6 Gate/Protocol profiles; `readiness.json` | `46×4=184`, `46×3=138`, valid D2 reuse оставляет `46×2=92`. Reuse требует exact commit, protocol/Gate, fixture, injection, device profile, fresh preflight и validity criteria. 120 hard kills/candidate — отдельный denominator. 11 blocker IDs в Gate и readiness имеют exact ordered equality. | Independent calculations and readiness checker passed/expected BLOCKED. | **VERIFIED** | Никакая injection или hard kill не выполнялась; D1/D5 deferred. | ACCEPT / MODIFY / REJECT | P0/P1 REC-RDY-06/08/09 |

---

## 4. Owner-selected design fields

Все verdicts: **ADVISORY DRAFT FOR HUMAN REVIEWER**.

| Field | Selected value | Evidence | Advisory verdict | Implementation evidence | Что должна подтвердить Katerina |
|---|---|---|---|---|---|
| Candidate set | Streaming Tink и 5-s microfile Tink; winner не выбран | DEC-044, Gate v0.6 | VERIFIED | ABSENT | Достаточно ли двух кандидатов и нейтральности до evidence |
| Streaming parameters | 16/16-byte AES-GCM-HKDF, SHA-256, 4096 ciphertext segment | v0.3 candidate construction | VERIFIED | ABSENT | Принимает ли exact parameter set |
| Streaming durability model | `DURABLE_ONE_SEGMENT_LOOKAHEAD` | v0.3 checkpoint model | VERIFIED | ABSENT | Принимает ли sacrificial segment и bound |
| Read/EOF rules | 4056/4080; only successful return; discard on exception; only `-1` EOF | v0.3 recoveryReads | VERIFIED | ABSENT | Достаточно ли fail-closed semantics |
| Microfile template | AES256, IV12, TAG16, TINK | v0.3 microfile template | VERIFIED | ABSENT | Принимает ли template и fresh-keyset scope |
| PASS cadence | 5 s only | v0.3 cadences | VERIFIED | ABSENT | Согласна ли, что 15/30 s не PASS |
| Manifest | Binary BE, 721 entries, 512 KiB, strict chain | v0.3 manifest | VERIFIED | ABSENT | Достаточны ли bounds/parser rules |
| Four AAD schemas | Stream, microfile, publication, key envelope | v0.3 associatedDataSchemas | VERIFIED | ABSENT | Достаточно ли cross-run/generation binding |
| Keystore path | Exact alias; generate only new run; Builder/getAead access | v0.3/v0.4 key protocol | VERIFIED | ABSENT | Принимает ли no-replacement recovery rule |
| Key confirmation | Ninth family, `DORAKC01`/`DORAKA01`, 13 steps | v0.4 keyConfirmation | VERIFIED | ABSENT | Достаточна ли bootstrap durability boundary |
| KEY taxonomy | Ordered eight-class algorithm | v0.6 canonical taxonomy | VERIFIED | ABSENT | Принимает ли exact precedence |
| Effective KEY-04 | Only replaced valid key + decrypt auth/AAD failure | v0.6 effective row | VERIFIED | ABSENT | Принимает ли все 8 preconditions и single oracle |
| KCF-07 | Successful decrypt, invalid plaintext → corruption | v0.6 KCF-07 | VERIFIED | ABSENT | Подтверждает ли separation from KEY-04 |
| Semantic commit | Successful SQLite `endTransaction()` | v0.3 definitions | VERIFIED | ABSENT | Принимает ли controller event как evidence only |
| Publication | 9/13/21 steps, exclusive no-overwrite | v0.3 storage protocol | VERIFIED | ABSENT | Достаточны ли durability/path boundaries |
| SQLite profile | WAL/FULL/0/foreign keys/single writer | v0.3 sqliteJournal | VERIFIED | ABSENT | Принимает ли platform SQLite только для Stage 0 |
| Hard-kill barriers | Candidate-specific K01–K12 | v0.3 hardKillCampaign | VERIFIED | ABSENT | Достаточна ли public observability |
| Fault profiles | 46 rows; Phase A 184; full 138; kills 120/candidate | v0.6 faultCampaign | VERIFIED | ABSENT | Принимает ли separation of denominators |
| Rollback claim | Global protection only with external controller ledger | v0.3 rollbackModel | VERIFIED | ABSENT | Принимает ли ограниченную claim |
| JSR-305 policy | Recovery-only exact exclusion; 3 narrow R8 rules | OD-14, JSR analysis | VERIFIED | ACTUAL GRAPH ABSENT | Принимает ли engineering boundary без Legal interpretation |
| Authority | All four implementation/execution flags false | Gate/readiness | VERIFIED | N/A | Подтверждает ли, что disposition не меняет flags |

---

## 5. Findings

### 5.1 Actionable technical findings

**ADVISORY DRAFT:** новых P0/P1 defects в design contract v0.6 не обнаружено.

Это не означает, что реализация безопасна: её нет. Все соответствующие implementation/runtime blockers остаются открыты.

### 5.2 Documentary findings

| Finding | Severity | Locator | Impact | Required change |
|---|---:|---|---|---|
| `DOC-POSTMERGE-001` — stale lifecycle state | P2, non-blocking for technical target | `docs/DORA_MVP1_STAGE_STATUS.md:12,277–278`; evidence README line 5; independent review task line 152 | Current main ошибочно называет reconciliation branch активной и требует, чтобы уже merged PR оставался Draft/unmerged. Может запутать будущего читателя, но не меняет v0.6 bytes или gate logic. | В recording PR обновить branch/lifecycle truth; удалить self-referential требования о Draft/unmerged и не фиксировать изменчивый live PR state как постоянный gate. |

Оценка:

- это documentary status cleanup;
- техническая корректность v0.6 package не затронута;
- Katerina может принять техническое решение до cleanup;
- cleanup допустимо и желательно включить в тот же governance recording PR, который запишет её formal disposition;
- перед merge recording PR следует проверить, что изменения не затронули три active v0.6 contract blobs и 15 historical pins.

### 5.3 Non-blocking observations

- Назначение Katerina дано вне Git и ещё не записано в repository truth. `REC-RDY-02` поэтому правильно остаётся `P0 / OPEN_UNASSIGNED / BLOCKING` до её личного письменного disposition и governance record.
- PR #13 validators добавляют post-merge advisory evidence, но не изменяют crypto/storage/fault contracts.
- Current checkout остаётся на уже merged reconciliation branch; это не current-main ref и не использовалось как primary target.
- Empty advisory-database exact-version query уменьшает известную неопределённость, но не доказывает отсутствие security defects.

### 5.4 Known missing implementation/runtime evidence

Отсутствуют:

- `:poc:recovery` и common harness;
- Tink/Keystore/SQLite implementation;
- actual resolved Gradle graph and locks;
- debug/D8 and release/R8 actual build evidence;
- zero-packaged-JSR305 scan;
- emulator/D2 Keystore/filesystem/SQLite preflight;
- streaming/microfile non-metric verification;
- fault injections and recovery classifications;
- 120 hard kills per candidate;
- Phase A 184 injections;
- D1/D2/D5 full 138 profile;
- benchmark or measurement;
- Product/IP actual-graph disposition;
- Production Legal and Production Security approval.

---

## 6. Remaining blockers

`REC-RDY-02` не закрыт.

| ID | Priority / status | Owner | Closure condition |
|---|---|---|---|
| REC-RDY-01-PRODUCT-IP-FINAL-APPROVAL | P0 / actual graph disposition pending | Project owner | Получить actual graph/package/R8 evidence и scoped Product/IP disposition; prospective policy alone insufficient. |
| REC-RDY-02-ACCOUNTABLE-ENGINEERING-SECURITY-REVIEW | **P0 / OPEN_UNASSIGNED / BLOCKING** | Project owner | Записать личный formal exact-package disposition Novikova Katerina или другого distinct accountable reviewer. Это AI dossier не закрывает blocker. |
| REC-RDY-03-STREAMING-IMPLEMENTATION-VERIFICATION | P0 / OPEN_BY_DESIGN | Future implementation + accountable review | Реализовать и non-metrically проверить public construction, lookahead, q/R, reads/EOF и barriers. |
| REC-RDY-04-MICROFILE-IMPLEMENTATION-VERIFICATION | P0 / OPEN_BY_DESIGN | Future implementation + accountable review | Проверить microfiles, parsers/AAD, key confirmation, taxonomy, KEY-04/KCF-07, publication, path and fault matrix. |
| REC-RDY-05-FUTURE-RESOLVED-GRAPH | P0 / OPEN_BY_DESIGN | Future implementation task | Exact recovery configurations/locks/package; zero JSR-305; exact R8 rules; no unresolved classes; separately resolve `Modifier`. |
| REC-RDY-06-DEVICE-SQLITE-PREFLIGHT | P0 / OPEN | Future authorized preflight task | Fresh exact-commit emulator/D2 SQLite, Keystore and filesystem evidence. |
| REC-RDY-07-HARNESS-ABSENT | P0 / OPEN_BY_OWNER_INSTRUCTION | Future separately scoped implementation task | Separate implementation authorization, implementation and safe non-metric verification. |
| REC-RDY-08-OWNER-EXECUTION-AUTHORIZATION | P0 / WITHHELD | Project owner | После остальных Phase A prerequisites отдельно установить named-scope `executionAllowed=true`. |
| REC-RDY-09-D1-D5-FULL-VERDICT | P1 / DEFERRED | Project owner | Выполнить полный D1/D2/D5 profile, если требуется PASS; D1/D5 deferred. |
| REC-RDY-10-PRODUCTION-LEGAL-SECURITY | P1 / OUTSIDE_STAGE0_UNASSIGNED | Project owner | Назначить Production Legal/Security до production admission/redistribution. |
| REC-RDY-11-SUPPLY-CHAIN-AUTHENTICITY | P0 / future actual graph OPEN_BLOCKED | Project owner / Stage 0 Product-IP | Governance packet verified; требуется actual graph/package/R8 evidence и disposition. |

---

## 7. Checks performed

| Проверка / команда | Результат | Доказывает | Не доказывает |
|---|---|---|---|
| GitHub connector + `gh api` для PR #12/#13, main ref, commit trees | PASS | Live PR/merge/ref/tree identities | Содержательную корректность contract |
| `git show -s --format=%H/%T` и blob comparisons | PASS | Target/merge tree equality; active contract blob identity | Runtime behavior |
| Comparison of 43 indexed paths | 39 identical; 4 post-merge governance metadata changes | Technical blobs не подменены current main | Что status prose свободен от stale lines |
| `validate_poc_recovery_governance.py --self-test` на detached temp clone exact `b537…` | PASS | 15 historical pins, 46 unique rows, one KEY-04, KCF-07 routing, counts, flags; negative mutations rejected | Android implementation |
| `verify_poc_recovery_dependency_inventory.py` | PASS | Static inventory/license/authenticity/policy consistency | Fresh remote availability |
| `verify_poc_recovery_dependency_inventory.py --online` | PASS | 8 exact JAR/POM pairs, checksums, full-fingerprint signatures, source correspondence, no native payload, immutable JetBrains LICENSE/NOTICE | Legal interpretation/admission/reproducible binary build |
| `validate_stage00.py` | PASS | Stage 00 artifact/governance integrity | Recovery runtime |
| `check_poc_recovery_run_readiness.py` | Expected exit 1, `BLOCKED` with exact 11 IDs | Fail-closed readiness | Closure of blockers |
| Parse all `docs/**/*.json` in target archive | PASS, 87 JSON files | JSON syntax | Semantic correctness beyond validators/review |
| `python -m py_compile` for four applicable validators | PASS | Python syntax/import compilation | Full test coverage |
| `git diff --check eca48… b537…` | PASS | Target patch whitespace integrity | Design correctness |
| `git diff --check b537… 252508…` | PASS | Reconciliation patch whitespace integrity | Lifecycle prose truth |
| Android diff checks for target and reconciliation | No Android files | No product/Android implementation change | Future implementation readiness |
| Search for recovery module paths | None | `:poc:recovery` absent | Dependency graph semantics after future scaffolding |
| Search Gradle for Tink 1.23 wiring | None | Candidate not wired | Other unrelated tooling dependencies absent |
| Live Actions run [`[31671226826](https://github.com/Monumentogram/DORA/actions/runs/31671226826)`](https://github.com/Monumentogram/DORA/actions/runs/31671226826) | `android-bootstrap=success`, `search-smoke=success` | Post-merge ordinary CI green | Recovery/device/kill execution |

Первый archive-only governance запуск не мог выполнить встроенный Git-history diff, потому что ZIP не содержал `.git`; он был повторён в полном временном clone и прошёл. Это была инфраструктурная ошибка запуска, не contract failure.

Все временные ZIP/directories/clones удалены. Никакие recovery/device tests, kill campaigns, benchmarks или measured executions не запускались.

---

## 8. Human reviewer decision worksheet

**НЕЗАПОЛНЕННАЯ ФОРМА — ТОЛЬКО ДЛЯ NOVIKOVA KATERINA**

Reviewer: **Novikova Katerina**
Affiliation: **Rambus**
Project role: **Distinct accountable Stage 0 Recovery Engineering/Security reviewer**
Review target: `b5371f523e4471aca48a63a82b9ee4e1f9a7e0fd`
Contextual main: `5c97f09f3165a90afa5300b30499e0dcb36168f2`
Review date: __________________

### Capacity — выбрать ровно одно

- [ ] individual professional capacity; Rambus listed only as affiliation
- [ ] authorized representative of Rambus

### Public-record consent

- [ ] Я согласна на публичное размещение моего имени, affiliation, project role, review date и formal disposition в публичном репозитории DORA.
- [ ] Если выбрана individual capacity, я понимаю, что affiliation Rambus не означает одобрение Rambus.
- [ ] Если выбрана authorized representative capacity, у меня есть соответствующие полномочия.

### Решение по вопросам 1–12

| ID | ACCEPT | MODIFY | REJECT | Комментарий / обязательное изменение |
|---|:---:|:---:|:---:|---|
| 1. Lookahead/q/R/read/EOF | [ ] | [ ] | [ ] | |
| 2. Streaming construction | [ ] | [ ] | [ ] | |
| 3. Microfile template/cadence | [ ] | [ ] | [ ] | |
| 4. Manifest/four AAD/rollback | [ ] | [ ] | [ ] | |
| 5. Keystore/KC/taxonomy/KEY-04 | [ ] | [ ] | [ ] | |
| 6. Semantic commit/C/R/A | [ ] | [ ] | [ ] | |
| 7. Publication/path/quarantine | [ ] | [ ] | [ ] | |
| 8. SQLite/preflight | [ ] | [ ] | [ ] | |
| 9. K01–K12 | [ ] | [ ] | [ ] | |
| 10. 46 rows/KCF-07/faults | [ ] | [ ] | [ ] | |
| 11. Dependency/IP/R8 boundary | [ ] | [ ] | [ ] | |
| 12. Campaign profiles/blockers | [ ] | [ ] | [ ] | |

Обязательные исправления:

____________________________________________________________________

Non-blocking observations:

____________________________________________________________________

### Formal disposition — выбрать ровно одно

- [ ] `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW`
- [ ] `CHANGES_REQUIRED`
- [ ] `REJECT`

### Подтверждения reviewer

- [ ] Я лично прочитала досье и доступные exact evidence.
- [ ] Я осознанно принимаю выводы либо явно исправила их выше.
- [ ] Я принимаю ответственность за выбранный formal disposition.
- [ ] Review был read-only.
- [ ] Implementation, execution и measurement не выполнялись.
- [ ] Это не Production Security approval.
- [ ] Это не Production Legal approval.
- [ ] Это не dependency admission.
- [ ] Это не разрешение execution.
- [ ] Даже `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW` не меняет `implementationAllowed=false`, `implementationAllowedByThisPackage=false`, `executionAllowed=false` или `measuredExecutionAllowed=false`.

Reviewer name: __________________________________
Date: __________________________________________
Written confirmation method/link: __________________

---

# Компактный worksheet для передачи Katerina

**Target:** `b5371f523e4471aca48a63a82b9ee4e1f9a7e0fd`
**Context main:** `5c97f09f3165a90afa5300b30499e0dcb36168f2`

Capacity:

- [ ] Individual professional capacity; Rambus is affiliation only
- [ ] Authorized representative of Rambus

- [ ] Public-record consent confirmed

| # | ACCEPT | MODIFY | REJECT |
|---|:---:|:---:|:---:|
| 1 | [ ] | [ ] | [ ] |
| 2 | [ ] | [ ] | [ ] |
| 3 | [ ] | [ ] | [ ] |
| 4 | [ ] | [ ] | [ ] |
| 5 | [ ] | [ ] | [ ] |
| 6 | [ ] | [ ] | [ ] |
| 7 | [ ] | [ ] | [ ] |
| 8 | [ ] | [ ] | [ ] |
| 9 | [ ] | [ ] | [ ] |
| 10 | [ ] | [ ] | [ ] |
| 11 | [ ] | [ ] | [ ] |
| 12 | [ ] | [ ] | [ ] |

Required changes: ________________________________________________

Non-blocking observations: ________________________________________

Disposition — exactly one:

- [ ] `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW`
- [ ] `CHANGES_REQUIRED`
- [ ] `REJECT`

- [ ] Personally reviewed
- [ ] Conclusions consciously accepted or corrected
- [ ] Responsibility for disposition accepted
- [ ] Read-only; no implementation/execution/measurement
- [ ] Not Production Security/Legal approval
- [ ] Not dependency admission or execution authorization
- [ ] Approval does not change any false authority flag

Name: __________________ Date: __________________
Written confirmation method: _____________________

## Вопросы, на которые Katerina обязательно должна ответить

1. В какой capacity она действует: individual или authorized Rambus representative?
2. Подтверждает ли она public-record consent?
3. ACCEPT/MODIFY/REJECT по каждому вопросу 1–12?
4. Какие изменения обязательны до дальнейшего движения?
5. Какие observations являются non-blocking?
6. Какой ровно один formal disposition она выбирает?
7. Подтверждает ли она все authority limitations?
8. Каковы дата и проверяемый способ письменного подтверждения?

## Финальное подтверждение AI

Я не выполнял formal review от имени Novikova Katerina и не выбрал за неё formal disposition. `REC-RDY-02` остаётся `P0 / OPEN_UNASSIGNED / BLOCKING`.

Repository files, tracked worktree, Git refs/index, GitHub PR/reviews/comments и `.codex-remote-attachments/` не изменялись. Ветка осталась `agent/stage-0d-post-merge-reconciliation`, HEAD `252508…`; staged и unstaged diffs пусты. Единственный существовавший до review untracked entry `.codex-remote-attachments/` остался нетронутым. Никакой PR этим review не создавался и не merge-ился.
