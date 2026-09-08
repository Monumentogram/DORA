Product/Legal/IP read-only review — [PR #47](https://github.com/Monumentogram/DORA/pull/47)

1. Reviewer identity and actual capacity

Новиков Юрий Product/Legal/IP evidence reviewer.

2. Reviewed identity and scope

* Commit: `a0580189333ab1ab8551c8c30cbb15a1e072faff`
* Tree: `df023d4cb8ca815c0ee499d35e8e1ad00c32e732`
* Review completed: `2026-08-19T06:57:25Z`
* PR state: `OPEN / DRAFT / UNMERGED`
* Scope: шесть изменённых Phase B governance/evidence files, PR metadata и содержащиеся в них sanitized findings.
* Все шесть blobs остались byte-for-byte неизменными после ancestry merge с commit `173b089681e1679f7e4bd434732527eca2da9c6c`.

Raw upstream source, полный текст root license, оставшиеся 11 blobs, AUTH-05 semantics для 18 tracker items и комментарии 11–18 PR 7322 в рамках этого review не запрашивались и не проверялись.

3. Overall disposition

`BLOCKED_RIGHTS`

Дополнительные выводы:

* license compatibility: `INSUFFICIENT_EVIDENCE`;
* дальнейшее действие: `DEFER`;
* `evaluationApproved: false`;
* `admitted: false`;
* Ready/merge: не одобрены.

`REJECT` не выносится: имеющихся данных недостаточно для вывода о несовместимости или запрете, однако их также недостаточно для подтверждения прав.

4. Статус шести файлов

Да. Шесть файлов могут рассматриваться только как sanitized governance evidence.

Они содержат ограниченные метаданные: пути, OID, размеры, SHA-256, счётчики, классификационные сигналы и non-admission dispositions. В них отсутствуют verbatim upstream source, тексты лицензий, issue/PR bodies и комментарии.

Такой статус:

* не утверждает наличие прав на inspection, copy, modify, evaluate, port, distribute или dependency admission;
* не превращает публичную доступность репозитория в разрешение на использование;
* не является license-compatibility или ownership conclusion;
* не распространяет root license автоматически на каждый материал репозитория.

GitHub отдельно указывает, что публичному репозиторию нужна лицензия, чтобы предоставить права на использование, изменение и распространение программного обеспечения. [GitHub — Licensing a repository](https://docs.github.com/articles/licensing-a-repository)

5. Допустимые границы будущего отдельного owner scope

Любое продолжение допустимо только по отдельному письменному owner scope со следующими ограничениями:

* точная фиксация upstream repository, immutable commit и tree;
* отдельные allowlists для source, license/notice, manifests, blobs и tracker items;
* фиксированные OID, размеры, hashes, request caps и byte caps;
* отсутствие clone, archive, recursive retrieval, patch или repository-wide diff, если это прямо не разрешено;
* получение точного root `LICENSE`, всех применимых nested `LICENSE`/`NOTICE`, manifest/lock files и сведений о vendored/third-party components;
* отдельные inventories и условия для моделей, весов, datasets, training/input/output data, изображений, аудио, шрифтов, брендов и других assets;
* memory-only обработка raw content и немедленная fail-closed остановка при secret, PII, private endpoint или неизвестных условиях доступа;
* tracker retrieval только по утверждённым IDs и полям; pagination и комментарии 11–18 PR 7322 требуют отдельного явного разрешения;
* отдельная rights matrix по каждому компоненту и предполагаемому действию;
* formal human Legal/IP review до copy, modification, evaluation, port, distribution или dependency admission.

GitHub предупреждает, что автоматическое определение root license не учитывает лицензии зависимостей и иные способы документирования лицензий. [GitHub — REST API endpoints for licenses](https://docs.github.com/en/rest/licenses/licenses)

Внутренний owner scope разрешает только проведение работы внутри проекта. Он сам по себе не создаёт прав, которые должны быть предоставлены upstream-правообладателями или третьими лицами.

6. Root license и дополнительные категории прав

Sanitized evidence фиксирует для root `LICENSE` только `MIT_FAMILY_SIGNAL`. Одновременно указано:

* `scopeComplete: false`;
* `legalCompatibilityConclusion: null`;
* recommendation: `DEFER`.

Стандартный текст MIT License перечисляет права в отношении охватываемого лицензией “Software”, включая использование, копирование, изменение и распространение при соблюдении условия о сохранении уведомления. Однако стандартный текст не содержит отдельного явно выраженного trademark grant или patent grant. [SPDX — MIT License](https://spdx.org/licenses/MIT)

Поэтому root-license signal не предоставляет автоматически и не подтверждает:

* права на название Omi, логотипы и trademarks;
* полный объём patent rights — возможные implied patent rights зависят от применимого права и требуют отдельной юридической оценки;
* права на модели или model weights;
* права на datasets, training/input/output data или database rights;
* права на изображения, аудио, шрифты, документацию и иные third-party assets;
* права на компоненты с собственными nested licenses или notices.

Evidence уже показывает MIT-, Apache- и BSD-family signals, а также `EMPTY_OR_UNKNOWN_LICENSE_SIGNAL`. Следовательно, распространение root license на весь репозиторий недопустимо без пофайловой и покомпонентной проверки. OSI также отмечает, что многие open-source licenses не содержат явно выраженных patent provisions, поэтому такие права нельзя механически выводить только из названия лицензии. [Open Source Initiative — FAQ](https://opensource.org/faq)

7. Reuse, port и dependency admission

Не одобрены:

* source reuse;
* code copy;
* port;
* dependency addition;
* model или dataset admission;
* использование upstream tests, schemas, binaries, services или product behavior;
* evaluation или production use.

Запись `PORT_CANDIDATE` для offline-sync pattern является только non-authorizing learning signal. Она не означает право на перенос или одобрение реализации.

Итог остаётся:

* `overallRights: BLOCKED_RIGHTS`;
* `componentReuse: INSUFFICIENT_EVIDENCE`;
* `evaluationApproved: false`;
* `admitted: false`;
* `readyOrMerge: false`.

8. Attestation

Я подтверждаю, что review был read-only и ограничен указанными commit/tree и шестью sanitized evidence files. Никакие файлы, права, reviewer placeholders или состояния PR не изменялись. GitHub review не публиковался; Ready и merge не выполнялись.

`2026-08-19T06:57:25Z`
