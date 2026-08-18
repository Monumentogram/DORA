# Dora MVP 1 — Product Decisions

Статус документа: единый реестр решений владельца продукта\
Дата: 15 августа 2026 года\
Последнее изменение: `DEC-045` утверждает только `DORA-ALPHA-001` Increment 1 manual local workspace; Stage 0 verdicts, production storage/capture/ML/backend, real data, Legal/Security и release не изменены\
Основание: Technical Plan §40 P1–P20, Design Spec §40.2 D-P1–D-P22 и owner approval record Stage 0A.

`Provisional` означает, что рекомендуемый baseline можно использовать для обратимого PoC/bootstrap, но это не заменяет явное решение владельца. `Proposed` запрещает необратимые или пользовательские действия до утверждения. `Approved` означает прямое решение владельца в указанной области; оно не расширяет scope на production, Legal или release без явной формулировки. Статусы, повышенные owner-решением, ссылаются на соответствующий `OD-*`, владельца и дату.

## DEC-001. Рынки, юрлицо и B2C/B2B

Статус: Proposed\
Приоритет: P0\
Источник: Technical P1\
Срок принятия: до записи реальных встреч, beta и выбора data plane\
Варианты: РФ; ЕЭЗ; США; ограниченная комбинация; B2C; B2B\
Рекомендуемый вариант: ограниченная РФ + один global beta только после counsel review\
Обоснование: рынки меняют consent, controller/processor roles, localization, transfers, stores и telemetry.\
Влияние на архитектуру: deployment profiles, tenant/region isolation, retention и auth.\
Влияние на UX: legal copy, provider/region, privacy and deletion screens.\
Обратимость: низкая после сбора пользовательских данных.\
Связанные задачи: `GOV-001`, `POC-DATA-001`, `BE-LEGAL-001`.

## DEC-002. Подтверждение согласия участников

Статус: Approved\
Приоритет: P0\
Источник: Technical P2; Design D-P7\
Дата решения: 4 августа 2026 года\
Владелец решения: Project owner\
Область утверждения: Stage 0 capture PoC; production legal wording остаётся отдельным gate\
Срок принятия: до production recording flow\
Варианты: checkbox каждую встречу; one-tap acknowledgement; enterprise-configured text\
Рекомендуемый вариант: утверждён владельцем — отдельный checkbox перед каждым тестовым запуском; это напоминание, а не юридическое разрешение (`OD-02`)\
Обоснование: напоминает обязанность пользователя, не выдавая действие за юридическое разрешение.\
Влияние на архитектуру: versioned acknowledgement/consent audit отдельно от Android permission.\
Влияние на UX: friction preflight, market-specific copy, accessibility.\
Обратимость: средняя; изменение wording требует versioning.\
Связанные задачи: `GOV-001`, `DES-START-001`, `POC-CAPTURE-001`.

## DEC-003. Mic-only scope

Статус: Approved\
Приоритет: P0\
Источник: Technical P3\
Дата решения: 4 августа 2026 года\
Владелец решения: Project owner\
Область утверждения: Stage 0 capture PoC; production scope требует отдельного admission/Legal gate\
Срок принятия: до capture PoC и публичного scope\
Варианты: физический microphone only; system/playback capture; call recording\
Рекомендуемый вариант: утверждён владельцем — только физический microphone input; system/call recording исключены (`OD-01`)\
Обоснование: обычное Android-приложение не может надёжно обещать downlink/uplink capture.\
Влияние на архитектуру: `AudioRecord`/route diagnostics без privileged APIs.\
Влияние на UX: честные source labels и ограничения speakerphone.\
Обратимость: низкая — другой scope является отдельным продуктом/legal track.\
Связанные задачи: `POC-CAPTURE-001`, `ADR-AUDIO-001`.

## DEC-004. Только ручной Start

Статус: Approved\
Приоритет: P0\
Источник: Technical P4\
Дата решения: 4 августа 2026 года\
Владелец решения: Project owner\
Область утверждения: Stage 0 capture PoC; production flow требует отдельного admission/Legal gate\
Срок принятия: до capture implementation\
Варианты: explicit Start; speech-triggered; passive always-on\
Рекомендуемый вариант: утверждён владельцем — user-visible explicit Start/Stop, без hidden/passive auto-record (`OD-01`)\
Обоснование: соответствует Android FGS, Play policy и privacy expectation.\
Влияние на архитектуру: mic FGS создаётся из visible Activity/user action; reboot только recovery notification.\
Влияние на UX: preflight, persistent notification/banner, explicit Resume.\
Обратимость: низкая для policy/product promise.\
Связанные задачи: `POC-CAPTURE-001`, `DES-START-001`.

## DEC-005. Минимальная версия Android

Статус: Provisional\
Приоритет: P0\
Источник: Technical P5\
Срок принятия: Stage 00 bootstrap\
Варианты: API 28; более низкий legacy floor; более высокий support floor\
Рекомендуемый вариант: `minSdk 28`\
Обоснование: приемлемый охват при современном Keystore/foreground/background baseline.\
Влияние на архитектуру: API guards, test matrix D1 и dependency admission.\
Влияние на UX: поддерживаемые устройства и fallback capabilities.\
Обратимость: повысить легко, понизить дорого.\
Связанные задачи: `S00-ANDROID-001`, `POC-DEVICE-001`.

## DEC-006. Release target SDK

Статус: Provisional\
Приоритет: P0\
Источник: Technical P6\
Срок принятия: Stage 00 для baseline; повторно перед submission\
Варианты: target 36; target 37 после final SDK/toolchain gate\
Рекомендуемый вариант: compile/target 36 сейчас; API 37 — отдельный migration gate\
Обоснование: API 36 — подтверждённый release floor, API 37 меняет memory/behavior assumptions.\
Влияние на архитектуру: edge-to-edge, FGS/job quotas, native memory tests.\
Влияние на UX: insets, system permission/notification behavior.\
Обратимость: средняя; target нельзя безопасно понижать после store policy change.\
Связанные задачи: `S00-ANDROID-001`, `REL-API-001`.

## DEC-007. Offline summary на D1

Статус: Proposed\
Приоритет: P1\
Источник: Technical P7\
Срок принятия: до Stage 9\
Варианты: обязательная local LLM summary; rules/protocol fallback; opt-in cloud\
Рекомендуемый вариант: transcript/protocol/tasks offline обязательны, LLM summary может ждать capability\
Обоснование: слабое устройство не должно перегреваться или блокировать core.\
Влияние на архитектуру: capability tiers и `PENDING_CAPABILITY`.\
Влияние на UX: честный capability state вместо error.\
Обратимость: высокая.\
Связанные задачи: `POC-ASR-001`, `NLP-SUMMARY-001`.

## DEC-008. Лимит model download и storage

Статус: Proposed\
Приоритет: P1\
Источник: Technical P8\
Срок принятия: до Model Manager/Stage 4\
Варианты: Whisper base default; smaller model; enhanced assets >500 MB\
Рекомендуемый вариант: base как явная отдельная загрузка; >500 MB только enhanced option\
Обоснование: APK остаётся малым, пользователь видит bytes и free-space impact.\
Влияние на архитектуру: signed catalog, resumable download, side-load/mirror.\
Влияние на UX: size, compatibility, Wi-Fi/metered choice before download.\
Обратимость: высокая при versioned catalog.\
Связанные задачи: `POC-ASR-001`, `ML-CATALOG-001`.

## DEC-009. Cloud по умолчанию выключено

Статус: Approved\
Приоритет: P0\
Источник: Technical P9; Design OB-02/05\
Дата решения: 4 августа 2026 года\
Владелец решения: Project owner\
Область утверждения: local-first baseline; cloud требует отдельного будущего consent\
Срок принятия: до любого outbound network path\
Варианты: Local default; Hybrid default; Cloud default\
Рекомендуемый вариант: утверждён владельцем — Local; cloud выключено до отдельного явного scope consent (`OD-10`)\
Обоснование: local-first core и отсутствие silent upload.\
Влияние на архитектуру: safe local defaults, zero-call test, consent gate/outbox.\
Влияние на UX: Local не визуально хуже cloud; clear location labels.\
Обратимость: низкая для trust; не менять remote config.\
Связанные задачи: `POC-OFFLINE-001`, `BE-CONSENT-001`.

## DEC-010. Российский backend/provider

Статус: Proposed\
Приоритет: P1\
Источник: Technical P10\
Срок принятия: до Stage 11 pilot\
Варианты: Yandex Cloud; Selectel; self-hosted; local-only launch\
Рекомендуемый вариант: procurement benchmark при сохранении S3/PostgreSQL/OIDC ports\
Обоснование: contract, localization, GPU availability и payment меняются.\
Влияние на архитектуру: RU isolated tenant/object/key plane и provider adapter.\
Влияние на UX: provider, region, retention and health labels.\
Обратимость: средняя при portable interfaces.\
Связанные задачи: `BE-LEGAL-001`, `BE-PROVIDER-001`.

## DEC-011. Global backend region/provider

Статус: Proposed\
Приоритет: P1\
Источник: Technical P11\
Срок принятия: до global cloud beta\
Варианты: approved EU region; other region; self-host; no global cloud\
Рекомендуемый вариант: EU region как рабочая гипотеза после DPA/transfer review\
Обоснование: residency, latency, procurement and transfers must be explicit.\
Влияние на архитектуру: separate global data plane and keys.\
Влияние на UX: provider/region and no VPN-driven switching.\
Обратимость: низкая после stored data.\
Связанные задачи: `BE-LEGAL-001`, `BE-PROVIDER-002`.

## DEC-012. Cloud audio retention

Статус: Proposed\
Приоритет: P1\
Источник: Technical P12\
Срок принятия: до first cloud upload\
Варианты: delete on receipt; fixed TTL; user-configured longer retention\
Рекомендуемый вариант: delete after confirmed result receipt or 24 h, whichever earlier\
Обоснование: минимизация sensitive audio при ограниченном recovery window.\
Влияние на архитектуру: TTL, deletion operation/receipt, backup disclosure.\
Влияние на UX: exact retention before consent and pending receipt after delete.\
Обратимость: низкая для already-retained data.\
Связанные задачи: `BE-CONSENT-001`, `BE-DELETE-001`.

## DEC-013. Local audio retention

Статус: Approved — только versioned reversible design/governance contract\
Приоритет: P1\
Источник: Technical P13\
Дата решения: 15 августа 2026 года\
Владелец решения: Project owner, через standing delegation для ordinary reversible project/product decisions\
Исполнитель/рецензент решения: OpenAI Codex project coordinator; `formalReviewer=false`\
Область утверждения: reusable contract `des-storage-retention-delete-v0.1`; code, Figma, schema, storage/provider wiring, deletion execution, conformance и research остаются separately scoped\
Источник делегирования (дословно): `Продолжи работу. Все где нужно принимать решения типа РАЗРЕШАЮ делай сама или поручи отдельному агенту. Важно, чтобы процесс не останавливался из-за этого`\
Интерпретация делегирования: standing delegation координатору для ordinary reversible project/product decisions, чтобы workflow не останавливался\
Граница полномочий: делегирование не даёт Legal/Security approval, Recovery/device/measured execution, dependency admission, production admission, real-data collection или merge authority; Codex не выдаёт себя за квалифицированного Legal/Security reviewer и не создаёт подпись человека\
Срок принятия: contract принят до Stage 3 storage policy; implementation/schema/deletion execution остаются отдельными gates\
Варианты: until explicit delete; opt-in auto-retention; transcript-only cleanup\
Рекомендуемый вариант: утверждённый local-first/privacy-first baseline ниже\
Default и automatic retention: audio хранится до явного удаления; automatic retention OFF, никогда не preselected/silent и может относиться только к audio, не к structured content/whole conversation. До отдельного утверждения versioned allowed-period catalog automatic retention visibly unavailable; этот DEC не придумывает numeric periods. Будущий opt-in/shortening требует impact review и explicit confirmation; overdue audio становится eligible только после confirmation и всех guards\
Automatic-cleanup guards: durable internally validated local result обязателен; внешний export сам по себе не считается result; age anchor — durable finalization; cleanup запрещена во время active recording и до reconciliation source jobs. Technical §14.3(7) относится только к automatic retention, не к explicit erasure\
Explicit audio-only deletion: доступно без result/export после enhanced warning; удаляет local audio и только verifiably per-audio key/material/audio caches, reconciles jobs/uploads; сохраняет transcript, protocol, decisions, tasks, summary, FTS и source links с точной source-unavailable reason\
Whole-conversation deletion: доступно без result/export после scoped confirmation; tombstones/reconciles jobs, revokes queued uploads, deletes local canonical/structured/derivatives/FTS и сохраняет минимальное content-free operation evidence. Local и remote scope независимы\
Active capture: отдельный accessible `Stop and delete/discard` flow; cancel до confirmation ничего не меняет; после начала irreversible work UI close не является cancellation, undo не обещается\
Erasure claim: physical overwrite не обещается. Crypto erase допускается только при verified per-artifact key granularity; иначе scoped best-effort cleanup и visible partial failure/retry\
Source reasons: `USER_DELETED`, `RETENTION_DELETED`, `MISSING`, `CORRUPT`, `KEY_UNAVAILABLE` различаются\
Export boundary: `DEC-017` temp не canonical и никогда не блокирует conversation delete. Opaque local binding, если нужен, не логируется; unexpired temp следует `DEC-017` safe-release/warned Delete-now/one-hour/startup lifecycle. External copies не удаляются\
Remote deletion: отдельно gated; только `ABSENT`, `NOT_REQUESTED`, `PENDING`, `FAILED`, `RECEIPT`; local success не равен remote success и backend promise не создаётся\
Storage/model boundary: model cleanup — отдельная in-use-guarded операция; reserved safety space не cleanup target; low/full storage не инициирует и не выдумывает deletion\
A11y/logging: focus, ≥48 dp targets, 200%, TalkBack/Switch/keyboard/D-pad, focus restore, meaningful announcements and no color-only state; logs/evidence content-free\
Обоснование: audio остаётся проверяемым source по умолчанию, а явное scoped erasure не блокируется отсутствием производного результата или export.\
Влияние на архитектуру: future retention/deletion operation contracts, durable retry/restart, source-unavailable taxonomy and orthogonal local/remote state; этот DEC не создаёт schema/storage implementation.\
Влияние на UX: exact scope/warnings, explicit opt-in, independent local/remote results, visible partial failure/retry and accessible destructive flow.\
Обратимость: низкая после удаления.\
Decision/evidence record: `docs/evidence/des-storage-001/decision-record-v0.1.json`; interaction contract: `docs/design/DORA_MVP1_STORAGE_RETENTION_DELETE_CONTRACT.md`\
Связанные задачи: `STORAGE-RETENTION-001`, `DES-STORAGE-001`.

## DEC-014. Использование встреч для улучшения моделей

Статус: Approved\
Приоритет: P0\
Источник: Technical P14\
Дата решения: 4 августа 2026 года\
Владелец решения: Project owner\
Область утверждения: все Stage 0 данные\
Срок принятия: до corpus collection/telemetry\
Варианты: never; separate research opt-in; bundled processing consent\
Рекомендуемый вариант: утверждён владельцем — обучение и улучшение моделей запрещены без отдельного будущего research consent (`OD-04`)\
Обоснование: training/human review не является необходимой обработкой.\
Влияние на архитектуру: isolated research store, access/retention/license manifest.\
Влияние на UX: отдельное добровольное согласие без dark patterns.\
Обратимость: низкая после dataset inclusion.\
Связанные задачи: `POC-DATA-001`, `GOV-PRIVACY-001`.

## DEC-015. Account requirement

Статус: Approved\
Приоритет: P0\
Источник: Technical P15\
Дата решения: 4 августа 2026 года\
Владелец решения: Project owner\
Область утверждения: local mode\
Срок принятия: Stage 00/1 architecture\
Варианты: account required; local anonymous + optional cloud account\
Рекомендуемый вариант: утверждён владельцем — local mode работает без account, network и GMS; OIDC возможен только для отдельно согласованного cloud (`OD-10`)\
Обоснование: сеть/auth не должны блокировать capture/history.\
Влияние на архитектуру: local canonical IDs and optional OIDC subject.\
Влияние на UX: no login wall; account shell deferred.\
Обратимость: средняя; later account can be additive.\
Связанные задачи: `S00-ANDROID-001`, `BE-AUTH-001`.

## DEC-016. Distribution и monetization

Статус: Proposed\
Приоритет: P1\
Источник: Technical P16\
Срок принятия: до release flavors/signing/store setup\
Варианты: free Play; RuStore; signed APK/enterprise; billing later\
Рекомендуемый вариант: free Play + RuStore + optional signed APK, billing вне MVP\
Обоснование: core не зависит от GMS/payment availability.\
Влияние на архитектуру: no-GMS core, isolated store adapters/flavors.\
Влияние на UX: no subscription urgency or entitlement gate in MVP.\
Обратимость: средняя до publication.\
Связанные задачи: `REL-STORE-001`, `REL-SIGN-001`.

## DEC-017. Export formats

Статус: Approved — только versioned reversible design/governance contract\
Приоритет: P1\
Источник: Technical P17; Design ST-07\
Дата решения: 15 августа 2026 года\
Владелец решения: Project owner, через standing delegation для ordinary reversible project/product decisions\
Исполнитель/рецензент решения: OpenAI Codex project coordinator; `formalReviewer=false`\
Область утверждения: reusable contract `des-export-interaction-v0.1`; code, Figma, schema, provider, FileProvider, SAF/Sharesheet wiring и export execution остаются separately scoped\
Источник делегирования (дословно): `Продолжи работу. Все где нужно принимать решения типа РАЗРЕШАЮ делай сама или поручи отдельному агенту. Важно, чтобы процесс не останавливался из-за этого`\
Интерпретация делегирования: standing delegation координатору для ordinary reversible project/product decisions, чтобы workflow не останавливался\
Граница полномочий: делегирование не даёт Legal/Security approval, Recovery/device/measured execution, dependency admission, production admission, real-data collection или merge authority; Codex не выдаёт себя за квалифицированного Legal/Security reviewer и не создаёт подпись человека\
Срок принятия: contract принят до Stage 12/export implementation; implementation остаётся отдельным gate\
Варианты: copy/select; Markdown; JSON; CSV tasks; audio; encrypted bundle\
Рекомендуемый вариант: утверждённый local-first/privacy-first baseline ниже\
Утверждённая матрица содержимого и форматов: Copy/plain text — Summary, Protocol, Transcript, Tasks; Markdown — Summary, Protocol, Transcript, Tasks; versioned JSON v1 — эти четыре structured content class, raw audio bytes исключены; CSV — только Tasks; Audio — отдельный one-shot WAV/PCM, default OFF и никогда не наследуется и не выбирается через Select all; encrypted bundle — deferred/not MVP и недоступен до отдельного key/recovery decision\
Defaults и совместимость: content, format и destination не preselected и не remembered; incompatible pairs disabled с доступной причиной; Audio требует отдельное acknowledgement при каждом export\
Destinations: Copy использует только clipboard; files используют Android Sharesheet или Storage Access Framework; Dora cloud upload отсутствует, account/GMS/network не требуются; до передачи раскрывается, что выбранное external app/provider управляет своей копией и может независимо использовать network\
Обязательное предупреждение перед каждым clipboard/plain/unencrypted transfer, без skip или remembered acknowledgement — RU: `Dora передаст выбранные данные разговора как незашифрованный текст или файл. После копирования, сохранения или отправки Dora не управляет копией в буфере обмена, другом приложении или выбранном хранилище.` EN: `Dora will transfer the selected conversation data as unencrypted text or files. After copying, saving, or sharing, Dora does not control copies held by the clipboard, another app, or the selected storage provider.`\
Temp lifecycle: Dora-managed temp создаётся только после Review и обязательного warning; Clipboard temp не создаёт; SAF staging можно clean только после successful destination close либо после прекращённой/failed write; Sharesheet handoff не равен delivery, поэтому Dora temp сохраняется до отдельного observable safe-release event, а при отсутствии такого event — до `Delete now` / `Удалить сейчас`, hard maximum один час или startup orphan cleanup. Failed cleanup остаётся видимым и retryable. SAF/external destination copies находятся вне Dora expiry. Export cleanup никогда не удаляет source conversation/audio/content и не обещает physical overwrite\
Recent exports: только currently live Dora-managed temp artifacts и cleanup failures с content-free metadata; entry удаляется после cleanup; отдельной persistent export history в MVP нет\
Clipboard: только explicit action; best-effort clear через 60 секунд только если clipboard всё ещё содержит неизменённый Dora clip; clearing не гарантируется\
Managed policy: может явно disable/restrict formats, destinations и Audio и сокращать expiry; не может молча enable, select или initiate export\
Retry: начинается с REVIEW после source/selection revalidation; background resume и reuse partial output запрещены\
Success semantics: clipboard API accepted; SAF final write/close completed; Sharesheet handed to Android. Нельзя утверждать, что external app/provider прочитал, сохранил, синхронизировал или доставил copy\
Progress и diagnostics: determinate только при real numerator/denominator, иначе stage-only; logs/diagnostics content-free и не содержат path, URI, key, payload или user content\
Обоснование: portable local-first results without connector lock-in, silent outbound flow, retained acknowledgement или ложного контроля внешней копии.\
Влияние на архитектуру: versioned future export schema/ports, deny-by-default compatibility and policy gates, bounded Dora-managed temp lifecycle; этот DEC не создаёт schema/port/provider implementation.\
Влияние на UX: explicit selection, separate audio acknowledgement, exact per-transfer unencrypted warning, truthful handoff semantics, accessible cleanup/retry states.\
Обратимость: высокая additively; removing a published format is costly.\
Decision/evidence record: `docs/evidence/des-export-001/decision-record-v0.1.json`; interaction contract: `docs/design/DORA_MVP1_EXPORT_INTERACTION_CONTRACT.md`\
Связанные задачи: `EXPORT-001`, `DES-EXPORT-001`.

## DEC-018. Восьмичасовая запись

Статус: Approved\
Приоритет: P0\
Источник: Technical P18\
Дата решения: 4 августа 2026 года\
Владелец решения: Project owner\
Область утверждения: Stage 0 evidence и будущие claims только на tested matrix\
Срок принятия: до claims и capture acceptance\
Варианты: guaranteed all devices; best effort tested matrix; out of scope\
Рекомендуемый вариант: утверждён владельцем — восемь часов являются best effort только для проверенных устройств, firmware, питания, температуры и свободного места (`OD-07`)\
Обоснование: OEM, battery, thermal and storage prevent universal guarantee.\
Влияние на архитектуру: rotation/checkpoint/guard/endurance suite.\
Влияние на UX: preflight budget and honest supported-device guidance.\
Обратимость: низкая for marketing/support promise.\
Связанные задачи: `POC-CAPTURE-001`, `POC-BATTERY-001`.

## DEC-019. Activation of uncertain tasks

Статус: Provisional\
Приоритет: P0\
Источник: Technical P19\
Срок принятия: before Stage 7–8 code\
Варианты: auto-PLANNED; high-confidence only; always review high-risk\
Рекомендуемый вариант: uncertain tasks remain `NEEDS_CONFIRMATION`; no silent `PLANNED`\
Обоснование: wrong action/deadline is high-cost.\
Влияние на архитектуру: review state and user-owned field protection.\
Влияние на UX: confirm/edit/reject and source required.\
Обратимость: low for trust/data mutations.\
Связанные задачи: `POC-DECISION-001`, `TASK-001`.

## DEC-020. Quality and reliability gates

Статус: Approved\
Приоритет: P0\
Источник: Technical P20/§35\
Дата решения: 4 августа 2026 года\
Владелец решения: Project owner\
Область утверждения: Gate Set `stage0-v0.1` только для Stage 0; undefined thresholds не утверждены\
Срок принятия: до Stage 0 go/no-go review\
Варианты: approve proposed gates; approve versioned changes with rationale\
Рекомендуемый вариант: утверждён владельцем — явно определённые gates `stage0-v0.1` утверждены; critical data-loss/source/consent gates нельзя снижать после просмотра результата; §7 `Unresolved thresholds` остаётся `Proposed` (`OD-05`)\
Обоснование: PoC needs an objective exit criterion.\
Влияние на архитектуру: tier/fallback/support matrix.\
Влияние на UX: low-confidence and unsupported-device behavior.\
Обратимость: medium before public claims.\
Связанные задачи: `POC-GATES-001`, all `POC-*`.

## DEC-021. Visual direction

Статус: Provisional\
Приоритет: P1\
Источник: Design D-P1\
Срок принятия: до high-fidelity D1/D3\
Варианты: Deep Ocean / Quiet Intelligence; alternative brand directions\
Рекомендуемый вариант: Deep Ocean / Quiet Intelligence\
Обоснование: calm, private, source-grounded character.\
Влияние на архитектуру: semantic design tokens only.\
Влияние на UX: brand atmosphere, motion and surfaces.\
Обратимость: medium after shipped assets.\
Связанные задачи: `DES-FOUND-001`.

## DEC-022. Brand colors

Статус: Provisional\
Приоритет: P1\
Источник: Design D-P2\
Срок принятия: design D1\
Варианты: `#061A35` deep + `#0C5193` primary; revised contrast-tested ramp\
Рекомендуемый вариант: baseline pair with semantic tokens and real-device contrast audit\
Обоснование: reference-derived deep identity with accessible interaction color.\
Влияние на архитектуру: token adapter, light/dark themes.\
Влияние на UX: all components/status contrast.\
Обратимость: medium.\
Связанные задачи: `DES-FOUND-001`, `DES-A11Y-001`.

## DEC-023. Theme strategy

Статус: Provisional\
Приоритет: P1\
Источник: Design D-P3\
Срок принятия: design D1 / first feature UI\
Варианты: light-first; dark-first; system-only\
Рекомендуемый вариант: light-first content, deep recording, complete dark theme\
Обоснование: long transcript readability without losing brand.\
Влияние на архитектуру: semantic color modes and screenshot matrix.\
Влияние на UX: reading comfort and recording distinction.\
Обратимость: medium.\
Связанные задачи: `DES-FOUND-001`, `DES-A11Y-001`.

## DEC-024. Custom font

Статус: Proposed\
Приоритет: P1\
Источник: Design D-P4\
Срок принятия: design D1\
Варианты: bundled Manrope Variable; system Roboto only\
Рекомендуемый вариант: Manrope after glyph/hinting/size/OFL artifact gate; system fallback meanwhile\
Обоснование: RU/EN brand character must not compromise rendering or license.\
Влияние на архитектуру: bundled resource/digest/license notice.\
Влияние на UX: layout, timer numerals, 200% text.\
Обратимость: high before visual baseline.\
Связанные задачи: `DES-FONT-001`.

## DEC-025. Wordmark and dot motif

Статус: Proposed\
Приоритет: P1\
Источник: Design D-P5\
Срок принятия: after trademark/name check, before store assets\
Варианты: `dora.`; `Dora`; alternative legally clear mark\
Рекомендуемый вариант: prototype `dora.` only, no production adoption before clearance\
Обоснование: brand motif is useful but name/trademark availability is unknown.\
Влияние на архитектуру: launch/icon asset pipeline only.\
Влияние на UX: wordmark/icon recognition.\
Обратимость: low after publication.\
Связанные задачи: `DES-BRAND-001`, `GOV-TRADEMARK-001`.

## DEC-026. Compact navigation

Статус: Provisional\
Приоритет: P1\
Источник: Design D-P6\
Срок принятия: design D2 before shell implementation\
Варианты: 4 destinations + central record action; standard nav + FAB\
Рекомендуемый вариант: proposed Dora Dock subject to first-click/accessibility tests\
Обоснование: record is an action, not a fifth destination.\
Влияние на архитектуру: app shell, state restoration, adaptive rail.\
Влияние на UX: reachability, semantics and active recording return.\
Обратимость: medium after feature navigation ships.\
Связанные задачи: `DES-IA-001`, `DES-A11Y-001`.

## DEC-027. Per-session acknowledgement pattern

Статус: Approved\
Приоритет: P0\
Источник: Design D-P7; Technical P2\
Дата решения: 4 августа 2026 года\
Владелец решения: Project owner\
Область утверждения: Stage 0 capture PoC; production Legal/usability evidence остаётся обязательным\
Срок принятия: before production preflight\
Варианты: checkbox; one-tap; enterprise policy\
Рекомендуемый вариант: утверждён владельцем — required compact checkbox перед каждым тестовым запуском (`OD-02`)\
Обоснование: clarity must be balanced with habituation.\
Влияние на архитектуру: versioned acknowledgement record.\
Влияние на UX: time-to-start and comprehension.\
Обратимость: medium.\
Связанные задачи: `DES-START-001`, `GOV-001`.

## DEC-028. Stop interaction

Статус: Provisional\
Приоритет: P0\
Источник: Design D-P8\
Срок принятия: prototype D3 / capture Stage 2\
Варианты: immediate Stop; tap + confirmation while capture continues; hold\
Рекомендуемый вариант: tap → explicit confirmation, capture continues until confirmed\
Обоснование: prevents accidental loss without ambiguous pause.\
Влияние на архитектуру: idempotent Stop command and dialog state independent of capture.\
Влияние на UX: clear `recording continues` copy and ≤5 s intentional Stop target.\
Обратимость: medium.\
Связанные задачи: `DES-STOP-001`, `POC-CAPTURE-001`.

## DEC-029. Pause timer semantics

Статус: Provisional\
Приоритет: P0\
Источник: Design D-P9\
Срок принятия: capture state contract\
Варианты: wall session time; captured audio time; both\
Рекомендуемый вариант: primary timer = captured audio; pause duration secondary\
Обоснование: source timestamps must map to actual samples.\
Влияние на архитектуру: sample-derived duration + pause interval model.\
Влияние на UX: frozen main timer and explicit pause duration.\
Обратимость: low after persisted/exported timestamps.\
Связанные задачи: `ADR-AUDIO-001`, `POC-CAPTURE-001`.

## DEC-030. DoraWave contract

Статус: Provisional\
Приоритет: P1\
Источник: Design D-P10; token JSON\
Срок принятия: prototype D1 before production component\
Варианты: 72 bars all sizes; variant count (60 landscape/72 default); simpler ring\
Рекомендуемый вариант: amplitude history ≤20 fps; landscape count follows token 60 until D1 resolves mismatch\
Обоснование: visual identity cannot affect capture reliability.\
Влияние на архитектуру: aggregated 20 ms levels, no raw PCM in UI, background 0 fps.\
Влияние на UX: clear speech/quiet/paused/error semantics and reduced-motion fallback.\
Обратимость: high before component baseline.\
Связанные задачи: `DES-WAVE-001`, `POC-BATTERY-001`.

## DEC-031. Silence countdown visibility

Статус: Provisional\
Приоритет: P1\
Источник: Design D-P11\
Срок принятия: VAD/DoraWave prototype\
Варианты: subtle 90 s arc; text only; hidden\
Рекомендуемый вариант: subtle arc after speech, explicitly not auto-stop wording\
Обоснование: explains semantic boundary without implying meeting end.\
Влияние на архитектуру: monotonic VAD state exposed, not UI timer authority.\
Влияние на UX: quiet/session-continuing comprehension.\
Обратимость: high.\
Связанные задачи: `POC-VAD-001`, `DES-WAVE-001`.

## DEC-032. Global active-recording banner

Статус: Provisional\
Приоритет: P0\
Источник: Design D-P12\
Срок принятия: app shell/capture Stage 2\
Варианты: persistent banner; dock indicator only; forced recording screen\
Рекомендуемый вариант: banner across destinations + explicit return action\
Обоснование: navigation never hides active capture state.\
Влияние на архитектуру: process-wide observable capture state.\
Влияние на UX: no dismiss while active; TalkBack announcement once.\
Обратимость: medium.\
Связанные задачи: `DES-IA-001`, `POC-CAPTURE-001`.

## DEC-033. Dynamic Material color

Статус: Provisional\
Приоритет: P2\
Источник: Design D-P13\
Срок принятия: after design D1, not a launch blocker\
Варианты: off; optional adapter; default on\
Рекомендуемый вариант: off by default, architecture adapter only\
Обоснование: status/wave contrast and deep-blue identity need predictability.\
Влияние на архитектуру: no direct raw brand bindings in components.\
Влияние на UX: stable brand/accessibility.\
Обратимость: high.\
Связанные задачи: `DES-FOUND-001`.

## DEC-034. Sound cues and haptics

Статус: Provisional\
Приоритет: P1\
Источник: Design D-P14\
Срок принятия: recording UX prototype\
Варианты: app tones; haptics only; silent\
Рекомендуемый вариант: no app-generated tones; restrained system-respecting haptics\
Обоснование: tones pollute recording and do not prove legal notice.\
Влияние на архитектуру: no audio playback coupling in capture.\
Влияние на UX: visible/text status remains authoritative.\
Обратимость: high.\
Связанные задачи: `DES-STOP-001`.

## DEC-035. Home personalization

Статус: Provisional\
Приоритет: P2\
Источник: Design D-P15\
Срок принятия: Home implementation\
Варианты: neutral greeting; account name; no greeting\
Рекомендуемый вариант: neutral greeting, no account required\
Обоснование: avoids auth/profile dependency.\
Влияние на архитектуру: none beyond local optional profile.\
Влияние на UX: simple local-first first launch.\
Обратимость: high.\
Связанные задачи: `UI-HOME-001`.

## DEC-036. Summary top structure

Статус: Provisional\
Приоритет: P1\
Источник: Design D-P16\
Срок принятия: before Stage 9 schema/UI\
Варианты: single prose; typed blocks; timeline only\
Рекомендуемый вариант: short summary + decisions + tasks + open questions/risks as typed source-linked blocks\
Обоснование: readable, editable and independently invalidatable.\
Влияние на архитектуру: block revisions/source joins/field ownership.\
Влияние на UX: copy/edit/regenerate per block.\
Обратимость: medium after schema.\
Связанные задачи: `NLP-SUMMARY-001`, `DATA-SUMMARY-001`.

## DEC-037. Manual task creation

Статус: Proposed\
Приоритет: P0\
Источник: Design D-P17; readiness `RDY-002`\
Срок принятия: before Task schema freeze\
Варианты: extracted-only; manual standalone; manual only within conversation\
Рекомендуемый вариант: include manual tasks, with nullable conversation/source and explicit `origin=USER` invariant\
Обоснование: design recommends Dora as usable task system, but current technical schema forbids it.\
Влияние на архитектуру: Task FK/nullability/provenance, FTS and export.\
Влияние на UX: manual create empty state and source-absent explanation.\
Обратимость: low after persisted schema/data.\
Связанные задачи: `DATA-TASK-001`, `TASK-001`.

## DEC-038. App-switcher privacy

Статус: Proposed\
Приоритет: P1\
Источник: Design D-P18\
Срок принятия: before privacy/security screens\
Варианты: always shield; user setting; sensitive screens only; off\
Рекомендуемый вариант: user setting, default on for sensitive previews; scoped screenshot blocking separately\
Обоснование: privacy without breaking legitimate support/share/accessibility.\
Влияние на архитектуру: lifecycle privacy cover and optional app lock state.\
Влияние на UX: recent-app cover, biometric recovery/support.\
Обратимость: high.\
Связанные задачи: `SEC-PRIVACY-001`, `DES-A11Y-001`.

## DEC-039. Cloud consent granularity

Статус: Provisional\
Приоритет: P0\
Источник: Design D-P19; Technical cloud contract\
Срок принятия: before OpenAPI/backend Stage 11\
Варианты: one global consent; per artifact; per conversation; enterprise policy\
Рекомендуемый вариант: per artifact class + provider + region, with explicit receipt/revocation\
Обоснование: audio, transcript and structured candidates have different sensitivity.\
Влияние на архитектуру: append-only consent ledger and outbound gate.\
Влияние на UX: clear what/why/where/how long before send.\
Обратимость: low after uploads.\
Связанные задачи: `BE-CONSENT-001`, `BE-API-001`.

## DEC-040. Accessibility release bar

Статус: Provisional\
Приоритет: P0\
Источник: Design D-P20\
Срок принятия: applies from first component; final at release\
Варианты: WCAG 2.2 AA-inspired Android critical flow; lower ad-hoc baseline\
Рекомендуемый вариант: AA-inspired gates + TalkBack/Switch/200%/keyboard/reduced-motion audit\
Обоснование: custom waveform/dock and critical recording actions must be usable by construction.\
Влияние на архитектуру: semantics, standard components, screenshot/test matrix.\
Влияние на UX: release blocker for critical flow.\
Обратимость: low if postponed.\
Связанные задачи: `DES-A11Y-001`, `QA-A11Y-001`.

## DEC-041. Tablet/foldable commitment

Статус: Provisional\
Приоритет: P1\
Источник: Design D-P21\
Срок принятия: adaptive D10 / stabilization\
Варианты: phone-only; adaptive baseline; advanced posture support\
Рекомендуемый вариант: adaptive baseline required; advanced postures only after D10 evidence\
Обоснование: current window can resize even on phones; no stretched phone UI.\
Влияние на архитектуру: window size/state restoration/list-detail boundaries.\
Влияние на UX: rail/panes/insets/hinge.\
Обратимость: medium.\
Связанные задачи: `DES-ADAPT-001`, `QA-A11Y-001`.

## DEC-042. Reference texture use

Статус: Provisional\
Приоритет: P2\
Источник: Design D-P22\
Срок принятия: design D1; may be omitted\
Варианты: no texture; new 2–3% asset on selected screens; reference bitmap\
Рекомендуемый вариант: only newly created low-opacity asset on splash/onboarding/recording after IP/performance check\
Обоснование: reference rights are unconfirmed and text readability is primary.\
Влияние на архитектуру: optional static asset/no-texture fallback.\
Влияние на UX: subtle atmosphere only.\
Обратимость: high.\
Связанные задачи: `DES-FOUND-001`, `GOV-IP-001`.

## DEC-043. POC-SEARCH-001 storage/update gates

Статус: Approved — Option B; measured execution separately withheld\
Приоритет: P1\
Источник: Technical Plan §39 `POC-SEARCH-001`; final review PR #10; Gate Set v0.1 pre-run rule\
Дата решения: 11 августа 2026 года\
Срок принятия: принято 11 августа 2026 года до любой новой измерительной кампании\
Владелец решения: Project owner\
Область решения: prospective Stage 0 search campaign only; historical v0.1 results immutable\
Варианты: A — strict; B — balanced local baseline; C — bounded one-second async fallback; полные prospective predicates сохранены в superseded draft artifacts.\
Рекомендуемый вариант: B, теперь утверждён владельцем\
Утверждённый вариант: Option B — balanced local baseline; ≤512 MiB, ratio ≤1.00, ≤512 bytes/segment; single-row/bulk-100 maintenance delta p95 ≤50/250 ms; indexed commit p99 ≤500 ms; visibility p95/p99 ≤250/1000 ms.\
Обоснование: владелец зафиксировал, что одна дополнительная копия объёма исходных данных, умеренная стоимость обновления и появление изменений в поиске не позднее одной секунды дают приемлемый баланс размера, скорости и сложности для локального MVP. Выбор не основан на прежних результатах Dora.\
Влияние на архитектуру: paired canonical-only/indexed harness, compacted incremental footprint, matched mutation overhead, visible non-stale indexing state and physical D1–D3 evidence.\
Влияние на UX: stale success запрещён; asynchronous fallback показывает явное `INDEXING` и обязан уложиться в one-second p99 visibility.\
Execution authorization: withheld; exact component/license/NOTICE package и новый harness теперь завершены для Stage 0, но confirmed physical D1–D3, fresh exact-commit preflight и отдельная recorded owner authorization обязательны до запуска.\
Обратимость: thresholds frozen для следующей кампании и не меняют historical v0.1; дальнейшее изменение требует новой prospective версии.\
Fallback: external/contentless or per-entity FTS, durable visible pending queue, or `INCONCLUSIVE` when required physical profiles are unavailable.\
Decision record: `docs/stage0/DEC-043-POC-SEARCH-STORAGE-UPDATE-GATES.md`; Gate Set: `docs/stage0/DORA_MVP1_POC_SEARCH_GATE_SET_STAGE0_V0_2.md`; machine companion: `docs/stage0/poc-search-gate-set-stage0-v0.2.json`; superseded proposal artifacts retain `-DRAFT`/`.draft`.\
Связанные задачи: `POC-GATES-001`, `POC-SEARCH-001`.

## DEC-044. POC-RECOVERY-001 pre-PoC experiment contract

Статус: Proposed experiment — protocol v0.6 exact KEY-04 governance package merged; repeat AI documentary re-review completed without actionable findings; Novikova Katerina completed the distinct accountable formal human review with `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW`; `REC-JSR305-EXCLUDE-001` and exact governance authenticity/LICENSE/NOTICE evidence closed; separate implementation review/authorization, actual graph, implementation verification and execution withheld\
Приоритет: P0\
Источник: direct Project-owner decision of 12 August 2026 / `OD-14`; owner-relayed verbatim formal review response of Novikova Katerina dated 13 August 2026; Technical Plan recovery gate; `RECOVERY-ADR-ORDER-001`\
Срок принятия: separately scope implementation review and obtain separate owner implementation authorization, exact excluded Gradle graph/release-R8/non-metric verification and scoped Product/IP disposition of the actual graph, fresh emulator/D2 preflight and later Project-owner authorization before execution\
Варианты: public Tink `StreamingAead` AES-GCM-HKDF with `DURABLE_ONE_SEGMENT_LOOKAHEAD`; public Tink `Aead` five-second `AES256_GCM_TINK_IV12_TAG16` microfiles with exact authenticated manifest; 15/30-second microfiles only as non-PASS observations or post-failure fallbacks.\
Рекомендуемый вариант: no recovery winner before evidence; protocol v0.6 inherits all unchanged SHA-256-pinned v0.5/v0.4/v0.3 candidate semantics and durable run-key confirmation, while replacing only the effective inherited `KEY-04` oracle and retaining one canonical ordered eight-class KEY taxonomy and separate campaign profiles. Prospective `REC-JSR305-EXCLUDE-001` covers only future `:poc:recovery`: Tink-local exclusion of exact `jsr305:3.0.2`, zero resolved components/packaged definitions across all resolvable compile/runtime/unit-test/androidTest/benchmark/release and packaging/runtime inputs plus recovery locks/verification metadata, and exactly three type-specific R8 `-dontwarn`; broader rules are rejected. Existing other-module tooling/lint/UTP/test paths are inventoried and not claimed absent.\
Обоснование: exact inherited v0.3 contract plus v0.4 ninth-family `key-confirmation/run.kc`, separate bounded plaintext/AAD schemas and 13-step durable bootstrap remain immutable. v0.5 made ciphertext identity checks pre-decrypt and plaintext validation post-decrypt only and added KCF-07. v0.6 materializes exactly 46 unique active rows with one `KEY-04`: only an exact-AAD decrypt authentication/AAD failure after all stored-identity/usable-alias/controller-key-replacement preconditions may return only `KEY_UNAVAILABLE_KEY_MISMATCH`; successful decrypt plus malformed/wrong plaintext remains `KCF-07` → `CORRUPT_KEY_CONFIRMATION`. Phase A remains 184 injections and full physical 138; 120 base hard kills/candidate remains separate. Phase A remains `FAIL`/`INCONCLUSIVE`; complete physical D1/D2/D5 is required for PASS.\
Влияние на архитектуру: design-selected public Tink parameters with implementation verification required; exact non-deprecated Android Keystore Builder/encrypted-keyset/AAD plus durable key-confirmation bootstrap; authenticated big-endian manifest/checkpoint; nine final+temp families with collision/no-overwrite and confirmation reconciliation; PoC-local WAL/FULL platform SQLite and deterministic UNIQUE processing intents. Prospective policy and exact governance packet evidence are closed, while future actual graph/package/R8 Product/IP disposition is open/blocking. No Gradle dependency, `:poc:recovery`, production schema or `:app` change is authorized.\
Влияние на UX: any future recovery UI must distinguish authenticated recovery, key unavailability, corruption and quarantined state; microphone must never restart automatically.\
Обратимость: high before execution and final ADR; any template/protocol change requires a prospective version and review, while measured evidence remains bound to its exact contract.\
Reviewer boundary: Project owner is the Stage 0 Product/IP reviewer and later execution authorizer. All eight coordinate authenticity classifications and immutable JetBrains LICENSE/NOTICE evidence are verified for the exact governance packet. Prospective policy is closed/approved; excluded JSR-305 terms remain uninterpreted and use/distribution unapproved. Future actual graph Product/IP disposition remains open. Novikova Katerina completed the distinct accountable recovery Engineering/Security review in `individual professional capacity; Rambus listed only as affiliation`; no Rambus corporate approval is claimed. Codex remains package author/advisory preparer and does not claim formal independence. Production Legal/Security remain null/separate.\
Execution authorization: withheld; the accountable exact-package Engineering/Security review is complete, but `implementationAllowed=false`, `implementationAllowedByThisPackage=false`, `executionAllowed=false` and `measuredExecutionAllowed=false`. Implementation still requires separate owner scope/authorization and non-metric verification; the exact scoped exclusion/R8 rule and zero-component future graph must be proven, the independent `Modifier` condition resolved without broad `dontwarn`, fresh emulator/D2 preflight completed and a later explicit owner execution authorization recorded.\
Decision record: `docs/stage0/DEC-044-POC-RECOVERY-EXPERIMENT.md`; Gate Set: `docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_6.md`; machine contract: `docs/stage0/poc-recovery-gate-set-stage0-v0.6.json` and `docs/stage0/poc-recovery-protocol-stage0-v0.6.json`; all 15 v0.1/v0.2/v0.3/v0.4/v0.5 Markdown/Gate JSON/protocol JSON files remain unchanged SHA-256-pinned superseded audit artifacts and are non-executable. Historical GPT-5.6 Sol/OpenAI and OpenAI Codex (GPT-5) advisory records remain unchanged with `formalReviewer=false` and did not close `REC-RDY-02`. The separate AI dossier is advisory only, not Novikova Katerina's decision. The additive formal record `docs/evidence/poc-recovery-001/formal-engineering-security-review-2026-08-13.json` records all 12 answers as `ACCEPT` and disposition `APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW` for exact commit `b5371f523e4471aca48a63a82b9ee4e1f9a7e0fd` / tree `1fd03fd489836c65f7ee043298f8f6d32df00c55`; it was not published as a formal GitHub review and grants no implementation, Phase A, execution, dependency or production authority.\
Finding status: `REC-REV-20260812-01=CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE`; `REC-REV-20260812-02=CLOSED_BY_DISTINCT_ACCOUNTABLE_FORMAL_HUMAN_REVIEW`; `REC-RDY-02=CLOSED_DISTINCT_ACCOUNTABLE_FORMAL_HUMAN_REVIEW`.\
Active identities: Gate Set `poc-recovery-stage0-v0.6`; protocol `poc-recovery-protocol-stage0-v0.6`.\
Связанные задачи: `POC-GATES-001`, `POC-DEVICE-001`, `POC-RECOVERY-001`, future final `ADR-AUDIO-001`.

## DEC-045. DORA Alpha Increment 1 manual local workspace

Статус: Approved for `DORA-ALPHA-001` Increment 1 only\
Приоритет: P0\
Источник: direct Project-owner instruction of 17 August 2026 to launch DORA Alpha in parallel with Stage 0, create the necessary DEC/ADR/backlog/status records and immediately implement the narrowest useful honest slice\
Срок принятия: принято 17 August 2026 до начала product-code implementation\
Варианты: (A) ждать полного Stage 0 closure без продуктового Alpha; (B) показывать недоказанные audio/AI placeholders; (C) выпустить ограниченный ручной local-only workspace с честно недоступными стадиями\
Рекомендуемый вариант: C — полезный user-authored vertical slice без зависимости от blocked capture/storage-crypto/ML/backend gates\
Дата решения: 17 August 2026\
Владелец решения: Project owner\
Область утверждения: internal non-release Alpha Increment 1 under `ALPHA-GATE-001`; no production or Stage 0 reclassification\
Утверждённое поведение: user-authored conversation title, transcript/notes, manual summary and conversation-scoped manual tasks can be created, viewed, edited, searched, completed and deleted locally without account, network or GMS. Every stored task has `origin=USER`; no transcript/source event is fabricated.\
Честный fallback: recording/import/audio, ASR, diarization, automatic protocol/summary/task extraction, models, cloud and export are visibly unavailable and produce no placeholder result.\
Data boundary: only synthetic or non-sensitive test text until a later privacy/data decision; real meetings, voice and unapproved personal data are not authorized.\
Техническая граница: Alpha-only versioned bounded app-private AtomicFile snapshot, no new runtime dependency and no Room/SQLCipher/encryption/security claim; preserve the non-release bootstrap ID.\
Readiness reconciliation: `RDY-002` is resolved only for this disposable Alpha model by requiring conversation ownership plus `origin=USER` and allowing no machine-source claim. The future production task schema remains unresolved and unchanged.\
Обоснование: this is the largest useful end-to-end local slice that does not depend on blocked capture, storage-crypto, data-corpus, ML, backend or Legal gates and does not misrepresent synthetic output.\
Влияние на архитектуру: reuse `:app`/Compose/semantic tokens; isolate a replaceable bounded store behind a repository; no new module until a real boundary requires one.\
Влияние на UX: manual authorship and limitations are explicit; the microphone action remains unavailable; whole-conversation local deletion requires confirmation.\
Обратимость: high before production schema or real data; Alpha snapshot migration/discard requires a later ADR.\
Не разрешает: production identity/signing/release, store publication, real meeting data, audio, models, cloud/backend, dependency admission, Stage 0 PASS or security/privacy compliance claims.\
Связанные задачи: `ALPHA-001`–`ALPHA-007`, Alpha-only reconciliation of `RDY-002`.\
Связанные артефакты: `DORA_ALPHA_CHARTER.md`, `ADR-0003`, `ALPHA-001`–`ALPHA-007`.

## Stage 0A owner approval record

Дата решений: 4 августа 2026 года\
Владелец решений: Project owner\
Статус: `OD-01`–`OD-10` — Approved только в указанной Stage 0A scope. `OD-11`–`OD-13` записаны в Stage 0 owner registry; `OD-14` вынесен в отдельный recovery owner record, чтобы не изменять хэшированное historical search evidence. `DEC-043` Approved как prospective search contract, а `DEC-044` остаётся Proposed recovery experiment contract с утверждённым только prospective `REC-JSR305-EXCLUDE-001`. Ни одно из этих решений не разрешает recovery implementation, measured execution или production implementation.

Crosswalk: `OD-01` → `DEC-003`/`DEC-004`; `OD-02` → `DEC-002`/`DEC-027`; `OD-03`/`OD-04` → `DEC-014` и Dataset Governance; `OD-05` → `DEC-020`; `OD-06`/`OD-07` → `DEC-018` и device matrix; `OD-08` → `DEC-009`/`DEC-014` и Privacy policy; `OD-09` → Stage 0 research retention, без изменения production `DEC-013`; `OD-10` → `DEC-009`/`DEC-015`; `OD-11` → `DEC-020` и IP policy Stage 0 SQLite/reviewer boundary; `OD-12` → Approved `DEC-043`/Gate Set `stage0-v0.2` with execution withheld; `OD-13` → exact Stage 0 search IP evaluation package и formal `INCONCLUSIVE` closure; отдельный `docs/stage0/DORA_MVP1_POC_RECOVERY_OWNER_DECISION_OD14.md` → Proposed `DEC-044`, prospective recovery Gate Set/protocol `stage0-v0.6`, closed prospective `REC-JSR305-EXCLUDE-001`, closed exact governance evidence, retained unchanged SHA-256-pinned v0.1–v0.5 audit artifacts и `implementationAllowed=false` / `executionAllowed=false`.

### OD-01. Первый технический PoC Stage 0

Статус: Approved\
Приоритет: P0\
Источник: прямое решение Owner `OD-01`; Technical §39\
Дата решения: 4 августа 2026 года\
Владелец решения: Project owner\
Область утверждения: только изолированный Stage 0 PoC\
Утверждённое решение: первым выполняется `POC-CAPTURE-001`; только физический микрофон и явный Start/Stop, без звонков, system audio и passive recording\
Обоснование: capture reliability — первый технический риск; PoC должен оставаться disposable evidence harness, а не production implementation.\
Влияние на архитектуру: отдельная PoC-ветка/модуль, no admission without ADR; никаких production identity/signing/backend/storage/ML dependencies.\
Влияние на UX: явный Start, видимое recording state и отдельный acknowledgement.\
Обратимость: высокая до production admission.\
Связанные задачи: `POC-CAPTURE-001`, `POC-BATTERY-001`.

### OD-03 и OD-04. Разрешённые данные Stage 0

Статус: Approved\
Приоритет: P0\
Источник: Owner `OD-03`/`OD-04`; Dataset Governance\
Дата решения: 4 августа 2026 года\
Владелец решения: Project owner\
Область утверждения: Stage 0 evaluation data\
Утверждённое решение: synthetic data first; специально записанные тестовые фразы взрослых добровольцев — только с отдельным consent; реальные совещания запрещены; training/model improvement требует нового research consent\
Обоснование: evaluation purpose не даёт прав на реальные meetings, public data release или training.\
Влияние на архитектуру: immutable manifest/splits, `trainingAllowed=false`, local/cloud boundary and deletion metadata.\
Влияние на UX: recording/evaluation consent раздельны; отказ не влияет на account/product.\
Обратимость: низкая после collection, поэтому scope фиксируется заранее.\
Связанные задачи: `POC-DATA-001`, `POC-VAD-001`, `POC-ASR-001`, `POC-DIAR-001`.

### OD-06. Охват устройств первого exploratory run

Статус: Approved\
Приоритет: P0\
Источник: Owner `OD-06`; Technical §34.2/§39\
Дата решения: 4 августа 2026 года\
Владелец решения: Project owner\
Область утверждения: первый exploratory run `POC-CAPTURE-001`\
Утверждённое решение: использовать один физический Android-смартфон владельца; до первого измеряемого запуска автоматически определить model, Android API, firmware/build, ABI и RAM после подключения; до discovery availability=`unknown`; остальные устройства не закупать\
Обоснование: один телефон позволяет настроить и измерить harness, но не доказывает D1–D7 coverage.\
Влияние на архитектуру: pre-run device inventory gate; serial/account/private paths не входят в public evidence.\
Влияние на UX: никаких support claims; первый результат ограничен конкретным discovered device.\
Обратимость: высокая; matrix расширяется только новым evidence/owner decision.\
Связанные задачи: `POC-DEVICE-001`, `POC-CAPTURE-001`.

### OD-08. Хранение evidence Stage 0

Статус: Approved\
Приоритет: P0\
Источник: Owner `OD-08`; Privacy Data Flow and Threat Model\
Дата решения: 4 августа 2026 года\
Владелец решения: Project owner\
Область утверждения: Stage 0 PoC evidence\
Утверждённое решение: GitHub содержит только sanitized reports и aggregate metrics; raw traces/audio — только controlled non-public storage; до его настройки разрешены только synthetic data\
Обоснование: Git, PR и Actions публичны и не являются хранилищем чувствительного evidence.\
Влияние на архитектуру: evidence classification, opaque locator/digest, redaction and cleanup gate before publication.\
Влияние на UX: отсутствует.\
Обратимость: низкая после public disclosure.\
Связанные задачи: `GOV-PRIVACY-001`, `POC-DATA-001`, all `POC-*`.

### OD-09. Retention тестовых данных Stage 0

Статус: Approved\
Приоритет: P0\
Источник: Owner `OD-09`; Dataset Governance\
Дата решения: 4 августа 2026 года\
Владелец решения: Project owner\
Область утверждения: purpose-recorded Stage 0 data; shorter mandatory term wins\
Утверждённое решение: raw audio ≤90 дней после закрытия PoC; annotations ≤180 дней; withdrawal deletion ≤30 дней; более короткий обязательный срок имеет приоритет\
Обоснование: indefinite retention запрещён; срок известен до collection.\
Влияние на архитектуру: `expiresAt`, deletion state/receipt and custodian checklist.\
Влияние на UX: срок и withdrawal path раскрываются до consent.\
Обратимость: низкая после collection.\
Связанные задачи: `POC-DATA-001`, `GOV-PRIVACY-001`.

## Decision update rule

Изменение статуса выполняется отдельным PR с owner, датой, evidence/ADR и влиянием на backlog. `Approved` решение не переписывается задним числом: замена получает `Superseded` и ссылку на новый DEC/ADR. Изменение runtime/product behavior без обновления этого реестра запрещено.
