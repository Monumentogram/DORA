# Dora MVP 1 — Implementation Readiness Review

Статус документа: Stage 00 review\
Дата: 4 августа 2026 года\
Recovery governance amendment: 12 августа 2026 года — owner-remediated protocol v0.3 under Proposed `DEC-044`/`OD-14`; dependency authenticity is verified and conditioned `jsr305:3.0.2` exclusion is technically proven, while Product/IP acceptance, accountable review, exact future zero-component graph, implementation and execution remain BLOCKED; the underlying license conflict is not interpreted\
Проверенный baseline: `1be83e2940a09f7b23e33b4cdf3827de2690f3fd`\
Источники: `DORA_MVP1_TECHNICAL_PLAN.md`, `DORA_MVP1_DESIGN_SPEC.md`, design tokens и screen inventory.

## 1. Итоговый статус

Общий вердикт: **READY WITH CONDITIONS** только для репозиторного и Android bootstrap.

Документы достаточно подробно задают направление, границы и измеримые gates, однако прямо запрещают начинать production-реализацию рискованных частей до PoC, license/legal review и решений владельца продукта. Stage 00 может создать воспроизводимый Android skeleton, CI, правила разработки и контракты тестирования. Он не подтверждает реализуемость capture, ML, backend или релиза.

| Область | Статус | Условие перехода к production-коду |
|---|---|---|
| Android bootstrap | **READY WITH CONDITIONS** | использовать provisional non-release application ID; зафиксировать toolchain; не добавлять native/model/backend код |
| Аудиозапись | **NOT READY** | PoC 1/3/6, FGS/OEM/device evidence, audio format и recovery ADR, product/legal decisions DEC-002–006 и DEC-018 |
| VAD | **NOT READY** | deterministic PoC 2 на 89,5/90/90,5 с, noise corpus и versioned segmentation profile |
| ASR | **NOT READY** | PoC 4, выбранный artifact/digest/quantization, WER/RTF/RAM/thermal и 16-КБ evidence |
| Diarization | **NOT READY** | PoC 5, DER/JER, correction burden и artifact-level license/redistribution approval |
| Локальное хранение | **NOT READY** | Tink truncated-stream PoC или sealed-microfile fallback, SQLCipher/Keystore lifecycle и migration/recovery tests |
| Backend | **NOT READY** | region/provider/legal decision, consent ledger, payload/key-custody ADR, OpenAPI v1 и deletion receipt model |
| UI | **READY WITH CONDITIONS** | допустим только neutral bootstrap shell; feature UI ждёт D-P1–D-P12, state fixtures и relevant technical PoC |
| Тестирование | **READY WITH CONDITIONS** | стратегия и gates определены; нужны device matrix, consented corpus, fixtures и reproducible harnesses |
| Релизный процесс | **NOT READY** | final application ID/signing ownership, target/API recheck, 16-КБ report, store/legal/privacy assets и green release matrix |

## 2. Рамка готовности

### Можно делать сейчас

- GitHub repository visibility/protection, branch/PR workflow и CI;
- Gradle/Kotlin/Compose skeleton под JVM 17, `minSdk 28`, `compileSdk/targetSdk 36`;
- version catalog, convention plugins, статические проверки и JVM smoke tests;
- документацию, ADR, backlog, test schemas и полностью искусственные fixtures;
- интерфейсы без engine implementation, только когда они нужны ближайшему PoC.

### Нельзя считать разрешённым этим review

- production `AudioRecord`/FGS, crypto container, Room production schema;
- загрузку или коммит model weights и native prebuilts;
- реальную отправку audio/transcript в облако;
- высокоточную DoraWave/feature UI реализацию вместо design/engineering PoC;
- регистрацию или публикацию provisional package name;
- маркетинговые обещания о восьмичасовой записи, WER/DER, recovery или legal consent.

## 3. Перекрёстная проверка и вопросы

Приоритет означает срок решения, а не серьёзность формулировки:

- **P0 — блокирует следующий код** соответствующей области;
- **P1 — должен быть решён до соответствующего этапа**;
- **P2 — может быть отложен** без ложного обещания пользователю.

| ID | Класс | Формулировка и источник | Влияние | Рекомендуемое решение | Обратимость | Обязательно до |
|---|---|---|---|---|---|---|
| RDY-001 | P0 | Не утверждены release application ID, владелец signing key и Android developer/package verification. Technical §37 этап 1/14, §40 P6/P16; Design §7. | Нельзя безопасно закреплять distribution identity или выпускать build. | В bootstrap использовать явно non-release `com.monumentogram.dora.bootstrap`; отдельным owner decision утвердить production ID и custody до регистрации. | Средняя до публикации, практически необратимо после неё. | production Stage 1 / store registration |
| RDY-002 | P0 | Manual task рекомендована Design D-P17 и `TK-02` допускает task без conversation, но Technical §26 требует `Task.conversationId` и `sourceEventId` как `UUID!`. | UI-сценарий невозможно сохранить без поддельного source. | Решить scope. Если manual task входит: оба FK nullable с `origin=USER`, отдельная provenance invariant и migration tests. | Средняя; schema migration после данных. | Stage 8 schema/code |
| RDY-003 | P0 | Recording control `Метка` есть в Design §22 и UI inventory, но отдельной marker entity/event и durable semantics в Technical schema нет. | Метка потеряется, смешается с ML timeline или не сможет пережить recovery. | Добавить `RecordingMarker`/`USER_MARK` contract с elapsed sample/time, segment link и audit semantics до capture slice. | Высокая до schema freeze. | Stage 2 |
| RDY-004 | P0 | Pause — отдельное UX/FSM-состояние, но Technical schema не хранит pause intervals/accumulated captured duration явно. | Невозможно однозначно восстановить timer, source wall-time и audit после process death. | Добавить pause/resume events или `RecordingPauseInterval`; duration выводить из sample count, wall duration хранить отдельно. | Средняя. | Stage 2 |
| RDY-005 | P0 | Tink Streaming AEAD не имеет implementation evidence для owner-remediated protocol `stage0-v0.3`: design фиксирует public AES-GCM-HKDF `DURABLE_ONE_SEGMENT_LOOKAHEAD`, exact read/EOF math, Keystore/key precedence, 9/13/21 publication, final+temp state, manifest/AAD/commit/durability/SQLite/33-row fault contracts. Подлинность всех восьми publisher-closure координат подтверждена. Conditioned exclusion `jsr305:3.0.2` технически доказан: binary references annotation-only, Kotlin/JVM/D8 проходят без artifact, bare AGP R8 не проходит, exact three-type rule закрывает JSR-305 condition; full seven-program probe затем независимо останавливается на `javax.lang.model.element.Modifier` из `error_prone_annotations:2.41.0`. Underlying Apache-2.0/BSD-3-Clause conflict не интерпретирован. Product/IP acceptance, accountable review, harness и execution отсутствуют. Technical §14.3, risk R9; Proposed `DEC-044`/`OD-14`. | Ошибка реализации lookahead, key envelope, manifest/checkpoint или commit может сделать committed prefix недоступным либо принять неаутентифицированные bytes; повторное появление conflicted coordinate через compile/runtime/test path, unresolved R8 missing class или расширенный `dontwarn` скроет policy drift. | Владельцу принять/отклонить `REC-JSR305-EXCLUDE-001` либо выбрать Legal/IP/abandon fallback и записать Stage 0 Product/IP disposition; затем назначить distinct accountable recovery Engineering/Security reviewer. Отдельным scope реализовать и неметрически проверить exact v0.3 design, Tink-local exclusion, zero `jsr305` components во всех resolvable recovery/consumer configurations, exact three-line R8 rule, independently resolve the observed `Modifier` condition if present, produce debug/release/package evidence with no unresolved missing classes и fresh emulator/D2 preflight. Только после отдельного owner authorization выполнить 12 strata / 120 base kills per candidate / ≥100 valid; committed loss 0, tail ≤5 s. | Низкая после записи production data. | Stage 3 |
| RDY-006 | P0 | Долгая FGS-запись на OEM, screen-off, user stop, reboot и route changes не измерена. Technical §5–6, R1–R3; Design RC/ER flows. | Главная ценность может терять аудио или сообщать неверный статус. | PoC 1/3/6 на D1–D7, 1/3/8 h, checkpoint/exit evidence; публиковать только tested-device claims. | Низкая для product promise. | Stage 2 |
| RDY-007 | P0 | Все будущие ASR/diarization/LLM/SQLCipher `.so` должны поддерживать 16-КБ pages; artifacts ещё не выбраны. Technical §5, R21. | Install/load/native crash и Play rejection. | NDK r28+, современный AGP, per-artifact ELF/ZIP alignment + runtime CI/device gate; запрещать неизвестные prebuilts. | Средняя при replaceable ports, низкая при tight coupling. | admission каждого native dependency |
| RDY-008 | P0 | Конкретные model weights имеют отдельные licenses/gated terms; code license недостаточна. Technical §15–16, §33, R6. | Нарушение redistribution/commercial terms; невозможна reproducible download. | Artifact manifest: source, exact digest, SPDX/terms, attribution, mirror/redistribution rights и Legal approval. | Низкая после распространения. | model admission / Stage 4–5 |
| RDY-009 | P0 | ASR selection — hypothesis: Whisper base quantization; WER/RTF/RAM/thermal не измерены на Dora corpus/devices. | Нельзя определить supported tier или обещать offline result. | Blind PoC 4 с fixed normalization и faster-whisper reference; выбрать artifact только по gates. | Высокая благодаря port. | Stage 4 |
| RDY-010 | P0 | Local diarization stack и exact weights не доказали DER/overlap/license. | Неверные speaker/task assignments и excessive correction burden. | PoC 5; при miss gate — manual labels/server opt-in/no forced speaker. | Высокая благодаря port. | Stage 5 |
| RDY-011 | P0 | Market, controller/processor, consent wording и RU/global data plane не утверждены. Technical P1/P2/P10/P11; Design D-P7/D-P19. | Нельзя законно включить recording/cloud copy или выбрать provider/region. | Local-only PoC на synthetic/consented data; counsel/product decision до beta; region never inferred from VPN. | Низкая после user data collection. | recording beta / Stage 11 |
| RDY-012 | P0 | Per-artifact/provider/region consent в Design §31.3 не поддержан одной строкой `User.cloudConsentVersion` и одним `consentReceiptId` в API example. | Нельзя доказать scope/revocation и корректно блокировать upload. | Спроектировать append-only `ConsentReceipt` + scopes, provider, region, policy version, granted/revoked timestamps; gate every outbound artifact. | Низкая после backend launch. | Stage 11 contract freeze |
| RDY-013 | P0 | Cloud payload protection/key custody оставлены вариантом: encrypted transport либо TLS payload; worker должен получить DEK. Technical §13.2. | Риск ложного E2EE claim и неуправляемого доступа workers. | Отдельный threat-model ADR: exact envelope, KMS/DEK lifecycle, worker access, audit, retention and deletion. Не использовать термин E2EE без соответствующей модели. | Низкая после API/data rollout. | Stage 11 |
| RDY-014 | P0 | Quality/reliability thresholds §35 помечены proposed и не утверждены владельцем. | PoC не имеет однозначного go/no-go; scope может дрейфовать. | Утвердить либо versioned override с owner/date/rationale до оценки PoC. | Высокая до public claims. | Stage 0 exit review |
| RDY-015 | P1 | Design summary говорит «72 radial bars», geometry допускает landscape 60–72, token JSON фиксирует landscape 60. | Figma/code/screenshot tests получат разные contracts. | Считать token JSON 60 для compact landscape provisional; D-P10 должен явно подтвердить variant-specific bar count. | Высокая. | DoraWave D1 / Stage 2 UI |
| RDY-016 | P1 | Пользовательское слово `фрагмент` одновременно означает physical `AudioSegment`, semantic boundary и иногда processing unit. Technical §2/14; Design §13/18/24. | Ошибочная durability/processing коммуникация и неверные progress denominators. | В domain/analytics использовать разные IDs; в copy catalog определить `сохранённая часть` vs `смысловой фрагмент`; progress считать physical units только при реальном denominator. | Средняя после analytics/API. | capture state contract |
| RDY-017 | P1 | Structured Summary UI редактирует/regenerates отдельные blocks, а Technical `Summary` — одна revision с JSON blobs без block ownership/revision relation. | Нельзя выполнить локальный edit/undo/invalidation без полной перезаписи. | Ввести `SummaryBlockRevision` либо typed block revisions/source joins и field ownership. | Средняя. | Stage 9 |
| RDY-018 | P1 | Remote deletion/processing log имеет UI (`ST-03`, `DL-02`) и API receipt, но local schema не задаёт `DeletionOperation`, consent/cloud audit ledger. | После local delete пользователь не увидит durable pending/failed receipt. | Добавить durable outbox operation + receipt entity, не хранить это только в transient job message. | Средняя. | Stage 11/delete implementation |
| RDY-019 | P1 | Screen inventory не содержит `ST-08`, хотя Design Settings включает language, notifications, accessibility и About; `ST-04` частично дублирует `MD-01`. | Analytics/QA/deep links и ownership экранов неоднозначны. | На design D2 решить: отдельные screen IDs или sections/system intents; обновить inventory, не baseline, отдельным approved handoff change. | Высокая. | design D2 / settings implementation |
| RDY-020 | P1 | Screen inventory помечает почти весь широкий MVP как P0, тогда как Technical §37 требует последовательные Stage 2–14 и PoC gates. | Команда может параллельно начать экраны без data/engine contracts. | `mvp_priority` не трактовать как implementation order; backlog и stage gate имеют приоритет по dependencies. | Высокая. | planning каждого stage |
| RDY-021 | P1 | `SpeakerIdentity` и future connector tables предложены в Technical schema, хотя функции исключены и data minimization критична. | Преждевременная biometric/schema surface и migrations. | Оставить port/type placeholders; не создавать biometric/template tables до отдельного approved feature/threat model. | Средняя. | Stage 3 schema freeze |
| RDY-022 | P1 | UI NT-01 требует notification actions Recording/Paused/Recovery, но notification contract, pending-intent idempotency и stale-session behavior не описаны. | Duplicate Stop, неправильный label или resume не той session. | Зафиксировать notification state table и command idempotency в capture ADR/tests. | Средняя. | Stage 2 |
| RDY-023 | P1 | `FINALIZING` UI допускает leave screen и фон, но Android post-processing/FGS quotas различаются; exact ownership операции не выбран. | UI может обещать завершение после system kill без recoverable journal. | Последнюю durable finalize держит capture service до короткого commit; дальнейшие стадии — unique WorkManager. Test kill на каждом transition. | Средняя. | Stage 2–3 |
| RDY-024 | P1 | App-switcher privacy, screenshots и biometric lock оставлены product trade-off. | Support/export/accessibility могут конфликтовать с blanket `FLAG_SECURE`. | Default privacy cover только в recent-apps через supported lifecycle; screenshot blocking scoped и отдельным decision. | Высокая. | Stage 12/privacy UI |
| RDY-025 | P1 | Font Manrope рекомендован, но exact artifact/glyph/hinting/license packaging не выбран. | Missing glyph, binary size, visual regressions, OFL notice gap. | Font PoC, exact digest, bundled OFL notice; system fallback until approved. | Высокая. | design D1 / UI foundation |
| RDY-026 | P1 | Target 36 выбран как floor; API 37 требует final SDK behavior/memory gate. | Преждевременный target может изменить FGS/memory behavior; слишком низкий нарушит store deadline. | Bootstrap compile/target 36; отдельный time-boxed API 37 migration gate before submission. | Средняя. | Stage 14 |
| RDY-027 | P1 | Accessibility gates высокие, но отсутствуют approved semantic fixtures/content strings and assistive-device test harness. | Custom dock/wave/source controls могут стать release blockers поздно. | Standard Material semantics in bootstrap; D9 fixtures/audit before custom components Ready for Dev. | Средняя. | each UI flow / D9 |
| RDY-028 | P1 | Release distribution names Play/RuStore/APK, но monetization/legal entity/store account не выбраны. | Нельзя finalise flavors, signing, Data Safety or rollout. | Core remains no-GMS/free; store-specific modules only after DEC-016. | Высокая. | Stage 14 |
| RDY-029 | P2 | Dynamic color, advanced fold postures, texture and voice/sound cues are unresolved but explicitly non-core. | Может увеличить design/test scope, не влияет на local core. | Оставить off/standard adaptive baseline; revisit only after gates. | Высокая. | post-D10 / post-MVP |
| RDY-030 | P2 | Semantic search, voice identity, connectors, team/sync/billing have placeholders despite explicit deferral. | Scope creep and accidental dependencies. | Ports only when a near-term consumer/test needs them; no SDK/table/screen implementation now. | Высокая. | MVP 2 decision |

## 4. Сводка противоречий и пробелов

### Прямые противоречия

1. Manual standalone task в design-handoff против обязательных source/conversation FK технической схемы (`RDY-002`).
2. DoraWave «72 bars» как headline decision против landscape token `60` (`RDY-015`).
3. Summary block-level edit/regenerate в UX против document-level `Summary` revision (`RDY-017`).

### Экраны без достаточной технической поддержки

- `RC-02` Mark — нет marker entity (`RDY-003`).
- `RC-03` pause duration/recovery — нет pause interval model (`RDY-004`).
- `ST-03` granular consent и `DL-02` remote receipt — нет полного consent/deletion ledger (`RDY-012`, `RDY-018`).
- manual mode `TK-02` — schema запрещает standalone source (`RDY-002`).
- settings categories без IDs и ownership (`RDY-019`).

### Технические состояния без полностью определённого UI/data contract

- native OOM/tier downgrade и API 37 memory limit: общий warning есть, но downgrade/retry decision не зафиксирован;
- stale worker lease, input hash dedup и partial stage artifact: доступны только Diagnostics, пользовательский merge/retry contract требует fixtures;
- Keystore invalidation/key loss: recovery/error vocabulary не различает «corrupt» и «ключ недоступен»;
- remote deletion backup expiry/receipt: UI предусмотрен, durable local operation — нет.

### UI-состояния без подтверждённой модели данных

- `long-pause`, recording marker, summary block ownership, per-artifact cloud consent;
- privacy/app-lock state and biometric recovery policy;
- model `paused/verifying/incompatible/update` требует artifact/download state machine и compatibility evidence;
- `audio-unavailable` должен различать retention deletion, key loss, corruption и temporary player error.

## 5. Зависимости и лицензии

Ни одна версия из baseline не является разрешением брать `latest`. Перед admission создаётся lock/update PR с release notes, CVE, ABI и license evidence.

| Кандидат | Текущий вывод | Gate |
|---|---|---|
| Android/Jetpack/Compose/Hilt/Room | допустимы для bootstrap при pinned compatible versions | dependency verification, release-note/CVE check |
| SQLCipher Android | code license выглядит совместимой с commercial use при notices | exact artifact/ABI/16-КБ/migration/key test |
| Tink Java | допустимый crypto primitive, не доказанный Dora container | truncated-stream/kill PoC + crypto review |
| whisper.cpp / Whisper weights | MIT baseline, но exact binary/model не выбран | digest, reproducible JNI, WER/thermal/16-КБ |
| sherpa-onnx / Silero | code licenses приемлемы, weights отдельно | exact artifact license + performance |
| pyannote / 3D-Speaker / WeSpeaker | gated/CC-BY/weights provenance differs | redistribution, attribution, DER and dataset terms |
| llama.cpp / Qwen models | code/model family выглядит permissive, exact artifact unapproved | digest, prompt/schema tests, memory/thermal/license |
| Manrope | OFL candidate | exact font file, glyph/hinting and bundled notice |
| Reference images | права не подтверждены | inspiration only; do not commit/ship copies |

## 6. Android lifecycle, battery и native risks

- FGS стартует только из видимого user action; notification/system mic indicator не скрываются.
- Back/navigation/rotation не управляют capture; state восстанавливается из service/repository.
- Pause освобождает mic и не увеличивает captured duration; Resume создаёт новую physical part.
- Task Manager stop/force-stop/reboot не обещают continuation; только authenticated salvage и explicit Resume.
- UI render/waveform прекращается background и не находится в audio callback.
- Heavy ML serializes one model at a time и по умолчанию не конкурирует с capture до battery PoC.
- Worker дробится/checkpoint-ится; long-running jobs и Android 16 quotas не обходятся бесконечным service.
- Любой JNI wrapper проходит cancel/recreate/leak/corrupt-input/trim tests и имеет symbolized crash path.
- Любой `.so` проходит 16-КБ packaging и runtime test до merge dependency.

## 7. Необратимые решения

Следующие действия требуют owner/ADR и не делаются автоматически:

- production application ID/package registration и signing key custody;
- сбор реальных meeting/voice corpora и research consent;
- выбор/зеркалирование model weights с gated terms;
- включение cloud provider/region и схема доступа worker к расшифровываемому payload;
- публикация store privacy/retention/legal claims;
- создание voice biometric templates;
- первая production schema с обязательными/non-null source contracts после реальных данных.

## 8. Traceability matrix

| Требование | Модуль/порт | Экран | Данные | Ключевой тест | Этап |
|---|---|---|---|---|---|
| Explicit manual recording | `recording:service`, capture state holder | `RC-01/02/03`, `NT-01` | `RecordingSession`, pause/marker additions | permission/FGS/Back/route/OEM 1–3–8 h | 0 PoC → 2 |
| 90 s silence + 10 min cap | `ml:vad-sherpa`, segmenter | `RC-02`, `PQ-01` | `SegmentationProfile`, `AudioSegment` | 89.5/90/90.5 s, continuous speech, overlap | 0 PoC → 3 |
| Authenticated crash recovery | `core:crypto/files`, recovery coordinator | `RC-04`, `ER-01/05` | exact relative filename/hash journal, encrypted keysets, manifest/checkpoint, audit | v0.3: 12 strata, 120 base kills/candidate, ≥100 valid and ≥8/stratum; 33 corruption/replay/rollback/key/path/quarantine/event fault rows; Phase A cannot PASS without D1/D5 | 0 PoC → 3 |
| Offline RU/EN transcript | `TranscriptionEngine` | `PQ-01`, `CV-03`, `MD-01` | transcript/model revisions | WER/RTF/PSS/thermal/timestamps/16-КБ | 0 PoC → 4 |
| Correctable speakers | `DiarizationEngine`, participant domain | `CV-04`, `RV-01` | speaker/participant/manual intervals | DER/JER/overlap + rename/merge/reassign persistence | 0 PoC → 5 |
| Source-grounded protocol | analysis/event domain | `CV-02`, `CV-03` | timeline event + source ranges | invalid source/schema rejection; source seek | 6 |
| Evolving final decision | decision reconciler/projection | `CV-05/06`, `RV-01` | decision revision graph | amend/cancel/conflict/manual truth | 0 PoC → 7 |
| Tasks/promises/deadlines | task domain | `TK-01/02`, `CV-01` | task/promise/deadline/ownership | negation, ambiguity, timezone, supersession | 8 |
| Structured summary | `AnalysisEngine` + summary domain | `CV-01` | typed block revisions + sources | factuality/source coverage/block invalidation | 9 |
| Offline history/search | Room/FTS repository | `HI-01/02`, Home | normalized entities + `SearchDocument` | 10k/1M p95, query escaping, rebuild | 0 PoC → 10 |
| Explicit optional cloud | `CloudProcessingProvider`, sync/outbox | `ST-02/03`, `PQ-01`, `ER-04` | consent receipt, sync/job/receipt | zero call local mode, VPN/multipart/dedup/authZ | 0 PoC → 11 |
| Delete/export/privacy | storage/security/export domain | `ST-05/06/07`, `DL-02`, `PR-01` | tombstone, key deletion, remote operation | scope comprehension, artifact/FTS/cache/receipt audit | 12–14 |
| Adaptive accessible UI | design system/navigation | all critical screens | semantic state fixtures | TalkBack/Switch/200%/insets/fold/reduced motion | D1–D10 / 13 |
| Reproducible release | build logic/CI/release | n/a | dependency/model/SBOM provenance | clean build, signing/update, 16-КБ, store reports | 1 / 14 |

## 9. Go/no-go для следующего чата

После Stage 00 следующий безопасный этап — **Stage 0 PoC**, а не production features. Он начинается только с:

1. утверждённого backlog item и отдельной ветки;
2. synthetic или consent-governed test data;
3. измеримого hypothesis/gate/fallback;
4. отсутствия model weights/secrets/private audio в Git;
5. обновления decision/status документов по результату.

Production capture Stage 2 запрещён до доказательств PoC 1/3/6 и решений `RDY-003`–`RDY-006`. Backend Stage 11 запрещён до `RDY-011`–`RDY-013` и `RDY-018`.
