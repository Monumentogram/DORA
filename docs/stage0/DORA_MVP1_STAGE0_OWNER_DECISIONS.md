# Dora MVP 1 — решения владельца для ближайших PoC

Статус: **Approved owner records; Option B approved, benchmark execution withheld**\
Даты решений: **4 и 11 августа 2026 года**\
Владелец решения: **Project owner**\
Область: только Stage 0 PoC; эти решения не разрешают production-функциональность, реальные совещания или публичные support claims\
Связанные записи: `DEC-002`–`DEC-004`, `DEC-009`, `DEC-014`, `DEC-015`, `DEC-018`, `DEC-020`, `DEC-027`, Approved `DEC-043` и Stage 0A owner approval record в Product Decisions

Этот сокращённый реестр содержит тринадцать решений для ближайших PoC. Все ответы ниже прямо утверждены владельцем в указанной области. `OD-12` утверждает только prospective Option B contract, а `OD-13` — точный Stage 0 IP evaluation package и честное `INCONCLUSIVE`-закрытие; измерительное исполнение остаётся отдельно запрещено.

## OD-01. Какой технический PoC запускать первым и что он вправе записывать?

**Статус решения:** `Approved` — 4 августа 2026 года.

**Простой вопрос:** подтверждаем ли первым `POC-CAPTURE-001`: изолированный тест физического микрофона, который запускается и останавливается только явно и не записывает звонки, системный звук или фон без участия пользователя?

**Зачем это нужно:** capture — первый риск в порядке Technical Plan. Точная граница не даёт превратить PoC в преждевременную product-функцию или тест недостижимого Android-сценария.

**Варианты:**

- A — первым запустить `POC-CAPTURE-001`; только физический микрофон и явный Start/Stop.
- B — сначала выполнить синтетический `POC-SEARCH-001`, а capture отложить.
- C — включить system/call/passive recording; это отдельный product/legal track и текущим этапом запрещено.

**Рекомендуемый вариант:** A.

**Последствия рекомендуемого варианта:** следующий отдельный этап может создать disposable PoC-harness с microphone permission и microphone FGS только в PoC-контуре. Evidence не принимается в production автоматически и не подтверждает поддержку телефонов.

**Что было заблокировано до утверждения:** начало `POC-CAPTURE-001`, затем `POC-BATTERY-001` и подготовка production capture Stage 2.

**Зафиксированный ответ владельца:**

`OD-01: выбираю A — первым запускаем POC-CAPTURE-001; только физический микрофон, явный Start/Stop, без звонков, системного звука и passive recording.`

## OD-02. Как подтверждать уведомление участников в capture PoC?

**Статус решения:** `Approved` — 4 августа 2026 года.

**Простой вопрос:** нужен ли перед каждым тестовым запуском отдельный checkbox о том, что участники предупреждены?

**Зачем это нужно:** Android permission подтверждает доступ приложения к микрофону, но не согласие людей на запись. Checkbox должен напоминать об обязанности пользователя, не изображая юридическое разрешение.

**Варианты:**

- A — обязательный короткий checkbox перед каждым запуском PoC.
- B — одно нажатие с кратким напоминанием без checkbox.
- C — одно подтверждение только при первом запуске.

**Рекомендуемый вариант:** A как временный PoC-контракт до Legal/usability review.

**Последствия рекомендуемого варианта:** тестовый старт получает отдельное подтверждение; текст версионируется и прямо говорит, что Dora не определяет законность записи. Production wording всё ещё требует Legal/usability evidence.

**Что было заблокировано до утверждения:** capture-flow `POC-CAPTURE-001` и проектирование production preflight.

**Зафиксированный ответ владельца:**

`OD-02: выбираю A — перед каждым тестовым запуском нужен отдельный checkbox; это напоминание, а не юридическое разрешение.`

## OD-03. Какие аудиоданные разрешены для Stage 0?

**Статус решения:** `Approved` — 4 августа 2026 года.

**Простой вопрос:** разрешаем ли синтетические данные и специально записанные тестовые фразы взрослых добровольцев с отдельным согласием, но не реальные совещания?

**Зачем это нужно:** capture/VAD/ASR/diarization требуют воспроизводимого звука, но публичный репозиторий и текущий этап не допускают private meeting audio.

**Варианты:**

- A — синтетика сначала; специально записанные фразы только по отдельному consent-процессу; реальные совещания запрещены.
- B — только полностью синтетические данные; качество на живой речи пока не оценивается.
- C — разрешить реальные совещания; это требует отдельного legal/research процесса и Stage 0 не допускается.

**Рекомендуемый вариант:** A.

**Последствия рекомендуемого варианта:** deterministic checks можно готовить на синтетике; purpose-recorded фразы становятся допустимы только после согласия, manifest, private storage, контроля доступа и удаления.

**Что было заблокировано до утверждения:** политика входных данных для acoustic-части `POC-VAD-001`, `POC-ASR-001`, `POC-DIAR-001`, `POC-DECISION-001` и capture fixtures.

**Зафиксированный ответ владельца:**

`OD-03: выбираю A — сначала используются синтетические данные; специально записанные тестовые фразы взрослых добровольцев разрешены только с отдельным согласием; реальные совещания запрещены.`

## OD-04. Можно ли использовать PoC-данные для обучения моделей?

**Статус решения:** `Approved` — 4 августа 2026 года.

**Простой вопрос:** запрещаем ли обучение и улучшение моделей по Stage 0 данным без нового отдельного research consent?

**Зачем это нужно:** согласие на проверку функции не означает согласия на training, human review или повторное использование корпуса.

**Варианты:**

- A — обучение запрещено; нужен отдельный будущий research consent и новый purpose record.
- B — training разрешён тем же согласием, что и PoC evaluation.
- C — всегда использовать только synthetic data и никогда не собирать purpose-recorded voice data.

**Рекомендуемый вариант:** A.

**Последствия рекомендуемого варианта:** evaluation и research остаются разделены; dataset нельзя позднее незаметно переиспользовать для обучения.

**Что было заблокировано до утверждения:** финальная формулировка purpose limitation для consent form и access manifest.

**Зафиксированный ответ владельца:**

`OD-04: выбираю A — Stage 0 данные нельзя использовать для обучения или улучшения моделей без отдельного будущего research consent.`

## OD-05. По каким правилам выносить pass/fail?

**Статус решения:** `Approved` — 4 августа 2026 года; неопределённые числовые пороги остаются `Proposed`.

**Простой вопрос:** принимаем ли Gate Set v0.1 как критерии только Stage 0, но не как обещания продукта?

**Зачем это нужно:** заранее зафиксированное правило не позволяет переопределить успех после просмотра результата.

**Варианты:**

- A — утвердить определённые gates v0.1 для Stage 0; critical data-loss/source/consent gates не ослаблять после результата.
- B — разрешить только baseline measurements без verdict; затем утвердить v0.2 и повторить тест.
- C — изменить конкретные пороги до первого запуска с письменным обоснованием.

**Рекомендуемый вариант:** A для уже определённых gates; любой критерий, зависящий от неопределённого порога, остаётся `INCONCLUSIVE`, пока порог не утверждён заранее.

**Последствия рекомендуемого варианта:** определённые результаты сравнимы; прохождение означает только PoC evidence, а не production admission или marketing claim. Порог ASR RTF по tier, пределы PSS/native heap, diarization corrections/min, абсолютная battery drain без mWh, capture sample-gap tolerance и minimum raw-trace retention не утверждены.

**Что было заблокировано до утверждения:** authoritative verdict по уже определённым gates любого `POC-*`, прежде всего `POC-CAPTURE-001`.

**Зафиксированный ответ владельца:**

`OD-05: выбираю A — утверждаю Gate Set v0.1 только для Stage 0; это не обещание продукта. Critical data-loss, source и consent gates нельзя снижать после просмотра результатов. Неопределённые пороги из раздела Unresolved thresholds остаются Proposed.`

## OD-06. Какой охват устройств допустим для первого exploratory run?

**Статус решения:** `Approved` — 4 августа 2026 года.

**Простой вопрос:** допускаем ли первый exploratory run на одном физическом Android-смартфоне владельца без закупки полной матрицы?

**Зачем это нужно:** emulator не воспроизводит микрофон, батарею, OEM killing, flash и thermal behavior, но один телефон также не доказывает D1–D7 coverage.

**Варианты:**

- A — обеспечить физические D1–D6 и проверенный D7 emulator до кампании.
- B — начать на одном физическом Android-смартфоне владельца; не выносить общий matrix PASS и не заявлять поддержку устройств.
- C — использовать только emulator; capture/endurance evidence останется недействительным.

**Рекомендуемый вариант:** B для первого exploratory run; полная матрица понадобится только перед соответствующими support/admission claims.

**Последствия рекомендуемого варианта:** до подключения availability всех D1–D7 остаётся `unknown`. Перед первым измеряемым запуском телефон обязательно подключается, а model, Android API, firmware/build, ABI и RAM определяются автоматически. После discovery телефон сопоставляется с подходящим профилем; результат ограничивается этим устройством. Approved failure gate может дать `FAIL`; без такого отказа неполное покрытие даёт только `INCONCLUSIVE`, но не matrix `PASS`.

**Что было заблокировано до утверждения:** выбор минимальной кампании; полный `POC-CAPTURE-001`, `POC-RECOVERY-001`, `POC-BATTERY-001` и support claims по-прежнему требуют соответствующего фактического покрытия.

**Зафиксированный ответ владельца:**

`OD-06: выбираю B — первый exploratory run выполняем только на одном физическом Android-смартфоне владельца. Точную модель, Android API, firmware, ABI и RAM нужно определить автоматически после подключения телефона. До подключения availability остаётся unknown. Общий PASS для всей матрицы D1–D7 и заявления о поддержке устройств запрещены. Остальные устройства пока не закупаем.`

## OD-07. Что обещать про восьмичасовую запись?

**Статус решения:** `Approved` — 4 августа 2026 года.

**Простой вопрос:** считаем ли восемь часов best effort только на проверенных устройствах и условиях?

**Зачем это нужно:** OEM, заряд, температура и свободное место не позволяют обещать одинаковый результат на каждом Android-телефоне.

**Варианты:**

- A — считать 8 часов best effort только на versioned tested matrix и при выполненном preflight.
- B — гарантировать 8 часов на всех поддерживаемых устройствах.
- C — исключить 8-часовой сценарий из MVP.

**Рекомендуемый вариант:** A.

**Последствия рекомендуемого варианта:** endurance evidence идёт ступенями 1→3→8 часов; неуспех сужает measured scope или требует fallback, а не скрытого обхода.

**Что было заблокировано до утверждения:** interpretation endurance results, `POC-BATTERY-001` и будущая product claim.

**Зафиксированный ответ владельца:**

`OD-07: выбираю A — восемь часов являются best effort только для проверенных устройств, firmware, питания, температуры и свободного места.`

## OD-08. Где хранить evidence PoC?

**Статус решения:** `Approved` — 4 августа 2026 года.

**Простой вопрос:** разрешаем ли в публичном GitHub только sanitized reports и агрегированные метрики, а raw traces и аудио — только в контролируемом непубличном хранилище?

**Зачем это нужно:** repository, PR и Actions logs публичны; traces могут раскрывать пути, идентификаторы или content.

**Варианты:**

- A — GitHub только для sanitized evidence; raw evidence хранится в контролируемом private storage с access log и opaque reference.
- B — загружать raw traces в GitHub Actions artifacts.
- C — хранить всё локально без воспроизводимого manifest.

**Рекомендуемый вариант:** A.

**Последствия рекомендуемого варианта:** публичный отчёт проверяем по digest/manifest; raw evidence остаётся вне Git/LFS/Actions. Пока controlled storage не настроено, разрешены только синтетические данные.

**Что было заблокировано до утверждения:** выбор public/private boundary; сохранение raw evidence и purpose-recorded audio всё ещё заблокировано до настройки controlled storage.

**Зафиксированный ответ владельца:**

`OD-08: выбираю A — в GitHub разрешены только sanitized reports и агрегированные метрики. Raw traces и аудио хранятся только в контролируемом непубличном хранилище. До настройки такого хранилища разрешены только синтетические данные.`

## OD-09. Как долго хранить purpose-recorded test data?

**Статус решения:** `Approved` — 4 августа 2026 года.

**Простой вопрос:** принимаем ли сроки: raw audio не более 90 дней после закрытия PoC, annotations не более 180 дней, удаление после отзыва не позднее 30 дней?

**Зачем это нужно:** до записи человек должен знать предельный срок; бессрочное хранение запрещено принципом минимизации.

**Варианты:**

- A — принять 90/180/30 дней как Stage 0 maximum; более короткий обязательный срок имеет приоритет.
- B — указать другие точные сроки до сбора данных.
- C — использовать только synthetic data и не хранить записи добровольцев.

**Рекомендуемый вариант:** A как максимальные operational limits, не заменяющие Legal review.

**Последствия рекомендуемого варианта:** manifest получает `expiresAt`; до автоматизации data custodian удаляет данные и фиксирует deletion receipt вручную.

**Что было заблокировано до утверждения:** срок хранения purpose-recorded data; сам сбор всё ещё требует consent, custodian и controlled storage.

**Зафиксированный ответ владельца:**

`OD-09: выбираю A — raw audio хранится не более 90 дней после закрытия PoC, annotations — не более 180 дней, удаление после отзыва согласия выполняется не позднее 30 дней. Более короткий обязательный срок имеет приоритет.`

## OD-10. Что обязательно должно работать без account, сети и GMS?

**Статус решения:** `Approved` — 4 августа 2026 года.

**Простой вопрос:** подтверждаем ли local-first baseline: cloud выключено, account не нужен, а local mode не зависит от сети и Google Mobile Services?

**Зачем это нужно:** иначе network/auth SDK может незаметно стать обязательной частью локального ядра и исказить `POC-OFFLINE-001`.

**Варианты:**

- A — local mode без account/network/GMS; cloud только после отдельного будущего consent.
- B — account требуется при первом запуске.
- C — hybrid/cloud включены по умолчанию.

**Рекомендуемый вариант:** A.

**Последствия рекомендуемого варианта:** PoC harness использует local safe defaults и не добавляет remote config, analytics или provider SDK; cloud/VPN остаются отдельными поздними PoC.

**Что было заблокировано до утверждения:** policy gate `POC-OFFLINE-001` и admission network/account dependencies.

**Зафиксированный ответ владельца:**

`OD-10: выбираю A — local mode должен работать без account, сети и Google Mobile Services; cloud выключен до отдельного явного согласия.`

## OD-11. Кто выполняет Stage 0 artifact review и какая SQLite provenance достаточна?

**Статус решения:** `Approved` — 11 августа 2026 года.

**Область:** только evaluation `POC-SEARCH-001` в Stage 0. Решение не является production
admission, production Legal approval или независимым production security review.

**Зафиксированный ответ владельца:**

- Product reviewer — `Project owner`.
- Project owner принимает роль IP policy reviewer для Stage 0 evaluation.
- Project owner принимает объединённую роль Engineering/Security reviewer только для Stage 0
  evaluation. Независимый production security review этим не заменяется.
- Production Legal reviewer/approval не назначен и остаётся `BLOCKED`.
- Для встроенного Android platform SQLite в Stage 0 достаточно digest точного system-image archive
  вместе с package/image ID, revision, runtime build fingerprint, API, ABI и `sqlite_version()`.
  Отдельный digest SQLite binary не требуется, если PoC не загружает и не распространяет отдельную
  SQLite library.
- Перед production admission эта nested-platform provenance boundary обязательно пересматривается;
  отдельный SQLCipher/custom SQLite/prebuilt всегда требует собственного artifact digest.

**Что это решение не утверждает:**

- ни один вариант численных predicates draft `stage0-v0.2`;
- evaluation rights всех 66 locked components без завершённого component/license/NOTICE review;
- production use, redistribution, SBOM/notices или shipping dependency admission.

**Следствие:** reviewer assignment и достаточность SQLite provenance method больше не являются
неопределёнными. `OD-12` позднее выбрал Gate Set Option B, а `OD-13` утвердил точный Stage 0 IP
evaluation package. Measured execution остаётся заблокирован до physical D1–D3 и отдельной
execution authorization.

## OD-12. Какой storage/update contract утверждён и разрешён ли benchmark?

**Статус решения:** `Approved` — 11 августа 2026 года.

**Область:** prospective Gate Set `stage0-v0.2` для `POC-SEARCH-001`; historical v0.1 evidence
остаётся неизменным.

**Зафиксированный ответ владельца:** выбран Option B.

- Index incremental bytes ≤512 MiB, overhead ratio ≤1.00 и ≤512 bytes/segment.
- Worst single-row/bulk-100 maintenance delta p95 ≤50/250 ms; indexed commit p99 ≤500 ms.
- Search visibility p95/p99 ≤250/1000 ms; stale successful responses запрещены.
- Обоснование: одна дополнительная копия исходного объёма, умеренная стоимость обновления и
  one-second visibility — приемлемый локальный MVP balance. Выбор не основан на прежних
  результатах Dora.

**Execution decision:** benchmark **не разрешён**. До любого measured workflow обязательны:

1. explicit `EVALUATION_APPROVED` exact component/license/NOTICE review;
2. implementation и verification нового paired storage/update harness;
3. подтверждённая availability/preflight физических D1, D2 и D3;
4. последующая recorded owner authorization, меняющая `benchmarkExecutionAllowed` на `true`.

**Что это решение не утверждает:** historical reclassification, artifact rights, physical device
availability, production schema/dependency admission, production Legal или independent production
Security approval.

## OD-13. Утверждён ли точный Stage 0 IP package и как закрывается Stage 0C?

**Статус решения:** `Approved` — 11 августа 2026 года.

**Область:** только internal synthetic research/evaluation `POC-SEARCH-001` в Stage 0.

**Зафиксированный ответ владельца:**

- exact Stage 0 IP evaluation package имеет статус `EVALUATION_APPROVED`;
- утверждены dependency inventory из 66 компонентов с digest
  `sha256:63a2a3dadfbfe072770d914a74cbd40d6adbd517548bda4ba0331dd314ca6a98`,
  license/NOTICE inventory с digest
  `sha256:8b80fa573a2674cb32fe08446683f5b3d05ce4721b6bcb018edec51cf9fbeb50`
  и Android system-image archive с digest
  `sha256:b1bb0769d0bed7698e61f203d7dc9bf6e7c37cd01a39d0d8788a11186bc78160`;
- Stage 0 IP precondition выполнен только для этого ограниченного evaluation scope;
- D1/D3 и measured benchmark отложены. Benchmark не разрешён, `benchmarkExecutionAllowed`
  остаётся `false`, а будущий запуск потребует физические D1–D3, fresh exact-commit preflight и
  отдельную последующую authorization владельца;
- Stage 0C завершается с формальным результатом `INCONCLUSIVE` и recommendation `BLOCKED` без
  нового измерительного запуска; Draft PR #10 остаётся без merge до final review.

**Что это решение не утверждает:** production Legal approval, независимый production Security
review, redistribution, production schema/dependency admission или автоматический допуск FTS4 в
production. Принятие `OD-13` также не доказывает задним числом выполнение IP precondition для
исторической v0.1 кампании и не меняет её метрики, gates или verdict.

## Итог утверждения

- `OD-01`–`OD-10` имеют статус `Approved` с датой 4 августа 2026 года; `OD-11`–`OD-13` имеют статус
  `Approved` с датой 11 августа 2026 года и только указанную Stage 0 scope.
- Первым остаётся `POC-CAPTURE-001`, но этот PoC в Stage 0A не запускался.
- До первого измеряемого запуска один физический телефон владельца должен быть подключён и автоматически идентифицирован.
- До настройки controlled non-public storage используются только synthetic data.
- Option B в `stage0-v0.2` Approved prospectively, Stage 0 IP package Approved только для research,
  но benchmark execution withheld; один телефон не даёт общий PASS D1–D7 и не подтверждает D1–D3
  availability.
