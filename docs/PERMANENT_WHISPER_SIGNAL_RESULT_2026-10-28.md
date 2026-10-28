# DORA — Permanent Whisper, device upgrade and Signal preparation

Проверка: 28 октября 2026. **Обновление телефона НЕ выполнено.** Владелец подтвердил
целевой пакет DORA Owner Preview и сообщил, что сейчас не может разблокировать
профиль. Подготовка на компьютере завершена; установка остаётся отдельным шагом.

## Результаты A–G

| Этап | Статус | Доказательство и границы |
|---|---|---|
| A — бессрочная модель | **PASS / PERMANENT_LOCAL_MODEL_CODE_READY** | Проверен diff PR119, два теста управляемых часов воспроизвели ошибку на старом коде и прошли на исправленном; сохранены размер/SHA-256/атомарность/отмена. |
| B — среда и телефон | **BLOCKED / DEVICE_PREFLIGHT_LOCKED** | Компьютер, SDK, Gradle, NDK, ADB и POCO доступны. Пакет, версия, user и сертификат проверены. Закрытый user 10 не позволяет проверить пользовательские данные и app-private модель. Это не DEVICE_UNAVAILABLE. |
| C — сборка обновления | **PASS / SIGNED_UPDATE_ARTIFACT_READY** | Узкий backport на точный исходник установленной Preview собран, подписан исходным ключом и проверен. Runtime упакован; реальный запуск новой APK относится к D. |
| D — обновление/данные/ASR | **BLOCKED / OWNER_UNLOCK_AND_INSTALL_APPROVAL_REQUIRED** | Установка не выполнялась, отдельное согласие на неё не запрашивалось до проверки данных. OWNER_DEVICE_PERMANENT_WHISPER_READY не присваивается. |
| E — review/integration | **PASS / INDEPENDENT_CODE_REVIEW_READY** | Независимое read-only review: нет Critical/Important замечаний в коде. PR совместим со своей актуальной базой; новые тесты закоммичены. CI конкретного нового HEAD учитывается отдельно. Merge не выполнен. |
| F — DORA Signal | **PASS / SIGNAL_UI_IMPLEMENTATION_READY** | Изолированная ветка, архитектура, карта экранов, алгоритм PCM и поэтапный план. Figma MCP исчерпал Starter-квоту; точная визуальная сверка заблокирована, UI не реализован. |
| G — Amazon | **PASS / CLOUD_ASR_UNCHANGED** | Backend/Cloud-контракты не изменены исправлением; 55 backend-тестов и Android Cloud unit-тесты прошли. Запросы к живому AWS и отправка аудио не выполнялись. |

Общая цель `PERMANENT_WHISPER_DEVICE_PASS + EXISTING_FUNCTIONS_PRESERVED +
AMAZON_UNCHANGED + SIGNAL_UI_READY_FOR_IMPLEMENTATION` **пока BLOCKED** на D.
Отсутствие изменений в исходниках хранилища не заменяет проверку данных на телефоне.

## Ветки, SHA и ссылки

| Назначение | Исходная база | Конечный исходник / handoff |
|---|---|---|
| PR119 `stage/9-permanent-whisper-model` | `codex/stage18-1e-live-deployment`, `59ca965e35a305ece8b6e17e0d679bf199a2b264` | Проверенный вход `4edf687352968c40017242608e4a85f64d43a467`; исходник с новыми тестами `d691dc0` (полный SHA в Git). Последующий commit этого отчёта меняет только документы/evidence. |
| Установленная Preview | `codex/parallel-alpha-preview`, `98a975f3aff268db605573e1cd666dbc237f3123` | APK с телефона побайтно совпал с сохранённой сборкой этой ревизии. |
| `stage/9-permanent-whisper-device` | `98a975f3aff268db605573e1cd666dbc237f3123` | Код APK `acfd8a7743d8a7f414eb8f1f5bdce77fcd492289`; handoff с runbook `0c1c55133fcab74454ca691c1b6daffb6bd85e3d`. |
| `stage/10-signal-ui-preparation` | `59ca965e35a305ece8b6e17e0d679bf199a2b264` | `42423cf0070d607f7ad7911996bf64a2ef1e7301`, только два документа. |

- [PR119](https://github.com/Monumentogram/DORA/pull/119), без merge и без нового PR.
- [Принятый Android CI на 4edf687](https://github.com/Monumentogram/DORA/actions/runs/40744427843): PASS.
- [Смежные software/backend/API28/API36 проверки того же HEAD](https://github.com/Monumentogram/DORA/actions/runs/40744435972): PASS.
- [Общий исторический Android workflow](https://github.com/Monumentogram/DORA/actions/runs/40744427758): FAIL; android-bootstrap и encrypted-persistence jobs не проходят общий gate. Известное `Product identity differs from 7.3` не исправлялось изменением реальной идентичности. Эти результаты не названы зелёными.
- [Runbook устройства](https://github.com/Monumentogram/DORA/blob/stage/9-permanent-whisper-device/docs/PERMANENT_WHISPER_DEVICE_UPGRADE.md).
- [План Signal](https://github.com/Monumentogram/DORA/blob/stage/10-signal-ui-preparation/docs/superpowers/plans/2026-10-28-signal-ui.md).

Новые локальные тесты не выдаются за результат старого CI. Публикационный HEAD и
его CI сверяются после push и фиксируются в финальном handoff/PR. Исторические
результаты не переписаны; `main` не использовался как Alpha-база.

## Почему APK не собрана напрямую из всей рабочей Cloud-ветки

Установленная Preview предшествует Stage18. Полный PR119 основан на ветке, где уже
добавлены Amazon и новая версия TranscriptCodec. Для срочного обновления перенос
всех этих изменений нарушил бы требование сохранить текущий формат данных.
Поэтому в device-ветку перенесены ровно четыре production-файла из diff PR119:

- `android/app/src/main/kotlin/com/monumentogram/dora/transcript/LocalAsrModel.kt`
- `android/app/src/main/kotlin/com/monumentogram/dora/transcript/WhisperNative.kt`
- `android/app/src/main/kotlin/com/monumentogram/dora/transcript/ModelImportController.kt`
- `android/app/src/main/kotlin/com/monumentogram/dora/transcript/TranscriptScreen.kt`

Также изменён `PinnedModelStoreTest.kt`, добавлен `PermanentModelCalendarTest.kt`
в том же пакете под `android/app/src/test/kotlin/`, и добавлен device runbook.
Нет изменений в core/audio, recording, Android Keystore, schema/codec, JNI,
manifest, Gradle version/signing, сроках хранения аудио/расшифровок.

В PR119 дополнительно к прежним 11 файлам добавлены новый calendar test, этот
отчёт и evidence; восстановлен executable bit `tools/validate_stage00.py`, потерянный
в исходном diff (единственное minor-замечание review). Прежние 11 файлов:
четыре production-файла выше, PinnedModelStoreTest, workflow
`verify-permanent-whisper.yml`, PRODUCT_DECISIONS, ADR-0027,
`docs/superpowers/plans/2026-10-28-permanent-local-whisper.md`,
`tools/test_poc_recovery_i3_governance.py`, `tools/validate_stage00.py`.
Полный перечень воспроизводим через `git diff --name-status 59ca965..HEAD`.

## A — календарь и целостность

Убраны EXPIRES, withinRetention и removeExpiredFiles из Android model/runtime.
Native available не зависит от даты; CancellationProbe проверяет отмену владельца.
Не введена новая дата или временное исключение. JNI abort/encoder callbacks и
проверки до/после inference не менялись. Existing RecognitionAttempt/LocalRecognitionRun
tests входят в прошедшие suites.

Сохранены 264,464,607 bytes и SHA-256
`49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f`.
Проверка выполняется перед использованием. Импорт ограничен размером, проверяет
digest, синхронизирует временный файл и атомарно перемещает его в пределах каталога;
неверный размер/hash/ошибка чтения не заменяют хорошую модель. Symlink rejection
сохранён. Удаление собственного `.pending` при неудаче не является удалением модели.

`PermanentModelCalendarTest` использует Clock.fixed: до старой границы,
`2026-11-02T21:00:00Z` (3 ноября 00:00 МСК), 4 ноября и 1 января 2100.
Проверены сохранность существующего файла, повторное открытие, импорт после
границы и переход через неё во время чтения импорта. Один и тот же test fixture
поддерживает старый constructor с инъекцией времени как negative control; на старом
коде оба теста упали на требуемых assertions. Исправленный store вообще не принимает
часы, поэтому тест доказывает устранение прежней зависимости, а не эмулирует
системные часы Android/JNI. Системная дата POCO не изменялась.

Исторические host custody scripts/контракты всё ещё могут содержать прежнюю дату;
они не являются Android runtime и не удаляют модель в установленном исправленном
приложении. Исторические evidence не редактировались. Для будущей сборки использовать
описанный controlled-local Gradle путь, не старый date-gated helper.

## B/C — среда, подпись и APK

Windows; JDK 17.0.20, Gradle wrapper 9.5.0; SDK/build-tools 36.0.0,
NDK 28.2.13676358. ADB работает по абсолютному пути SDK. `adb devices -l` обнаружил
POCO и эмулятор; все обращения к POCO использовали явный serial (хранится только
локально). Android 14, arm64-v8a. Первоначально foreground user=0, профиль 10 running;
для разрешённых проверок foreground переключён на 10. Приложения/данные user 0
не устанавливались и не изменялись. Профиль 10 требует системный PIN.

В user 10 обнаружены alpha.test (code4), alpha.pilot (code2), alpha.preview (code4).
Для всех трёх `installed=false` в user 0. Владелец отдельно подтвердил Preview.
Её package state, APK, подпись и версия прочитаны. `run-as --user 10` отказал:
package not debuggable. Ни обход защиты, ни смена debuggable не применялись.

| Параметр | Проверенное значение |
|---|---|
| applicationId | `com.monumentogram.dora.alpha.preview` |
| versionCode / versionName | `4` / `0.1.0-owner-preview.4`, сохранены |
| ABI | arm64-v8a; 10 native libraries |
| min/target SDK | 28 / 36 |
| Сертификат установленной и новой APK | `a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d` |
| SHA-256 установленной APK | `0370f1999fcdcdf04d116c58a07ddea212701fc1afc463b0e601daee3ea850a1` |
| SHA-256 новой APK | `284db30f699c53500ea15d8cb2db7a15f45dd581ad20fece20114001d89e4095` |
| Размер новой APK | 83,735,909 bytes |
| Исходный private key | Доступен вне Git; успешно подписал candidate |
| Signature/ZIP 16K/ELF 16K/dependency/JNI exports | PASS |
| Whisper model в APK/Git | Отсутствует |

VersionCode повышать для `adb install -r` не требуется. Сохранение кода обеспечивает
подготовленный same-version rollback до старого deadline без `-d` и удаления данных.
Это не публикация в магазине. Манифест, ресурсы/native payload неизменны; разница
архивов ограничена `classes3.dex`, `assets/dexopt/baseline.prof`,
`assets/dexopt/baseline.profm` и signature metadata. ART-профили закономерно связаны
с DEX checksum. Pin каждого native-файла совпадает с установленной версией и
admitted inventory; успешный запуск именно новой APK пока не проверен.

Локальная host-копия SMALL q8 проверена по размеру/hash. Состояние model в
`noBackupFilesDir` телефона неизвестно до разблокировки и проверки приложения.
APK и исходный APK для восстановления находятся вне Git в
`%LOCALAPPDATA%/DORA/permanent-whisper-20261028/` вместе с закрытыми receipts/logs.

## Проверки этой сессии

| Проверка | Результат |
|---|---|
| PR app Debug unit | 117 tests, 0 failures/errors |
| PR audio Debug / model Debug | 264 / 5 tests, 0 failures/errors |
| Device OwnerPreview unit / audio Release | 108 / 255 tests, 0 failures/errors |
| Calendar negative control на старом установленном исходнике | 2 tests, 2 ожидаемых assertion failures; доказательство ошибки, не текущая регрессия |
| Calendar на исправленном исходнике обеих веток | 2/2 PASS |
| PinnedModelStore suite | 7/7 PASS |
| PR Spotless / Detekt / Debug Lint | PASS |
| Device Spotless / Detekt / OwnerPreview Lint / assembly | PASS |
| Финальная сборка закоммиченного device source | PASS |
| Backend через существующую virtualenv | 55/55 PASS; без live AWS |
| Независимое review model/device patch | Нет Critical/Important; minor executable bit исправлен |
| Signal builder check | Уточнены atomic packed ring, LIMIT_REACHED и throwing observer isolation |

Первые локальные команды выявили ошибки запуска, а не дефекты продукта:
Spotless задача находится в корне, не в app; backend tests лежат в backend/tests,
а bundled Python не содержит fastapi — использована существующая backend/.venv.
Первая APK-проверка выявила изменение ART-профилей и была уточнена после разбора
точного ZIP diff; проверки runtime assets/native не ослаблены. Попытка общего ELF
скрипта с неверным форматом allowlist не прошла; затем его load_segments проверил
все 10 библиотек точного pinned inventory. Все эти логи сохранены локально.
Принятые Recovery/endurance этапы не запускались заново.

## D — что ещё НЕ проверено

| Проверка после обновления | Статус |
|---|---|
| Реальная установка и запуск user 10 | BLOCKED |
| Старые записи / исходные аудиофайлы | BLOCKED |
| Расшифровки / ручные правки / история версий | BLOCKED |
| Модель доступна внутри обновлённого приложения | BLOCKED |
| Реальная локальная RU / EN расшифровка | BLOCKED / BLOCKED |
| Качество RU / EN | BLOCKED, не измерено |
| Работа без сети / физическая отмена inference | BLOCKED / BLOCKED |
| Отсутствие calendar gate | PASS автоматизированно и по коду; дата телефона не менялась |
| AWS-контракты | PASS локально; live deployment не трогали |

Копия APK не является backup пользовательского vault/Keystore. До установки нужно
зафиксировать доступность данных и модели в приложении, затем показать владельцу
точный кандидат и получить отдельное согласие. Только после этого — `-s <serial>
install --user 10 -r`, без удаления/очистки. Подробный порядок и восстановление в
device runbook. Старую APK нельзя запускать/возвращать после 3 ноября 00:00 МСК:
она может удалить модель. Тогда допустим только сохраняющий данные forward repair.

## F/G и следующий шаг

Signal основан на реальном Compose UI и сохранённых владельцах действий. План
указывает все семь экранов и отличает действующие ASR/editing/recording от placeholders.
Будущая осциллограмма получает все полностью принятые PCM reads, строит 5-ms peaks,
сохраняет maxima при агрегации, использует отдельный bounded visual stream и Canvas,
останавливается на паузе и снижает FPS при нагрузке. 164 — плотность референса,
не число PCM samples. Никакой случайной рабочей анимации. Новые экраны, product
search/indexing, adaptive waveform и новые settings preferences ещё не реализованы.

Figma: [утверждённый файл](https://www.figma.com/design/dkhVOt8b4PiKAv4cUBefa3).
MCP quota блокирует точные размеры/шрифты/ассеты; подтверждённые владельцем цвета и
концепция использованы без выдумывания остальных деталей. Ветка содержит ADR и план,
никаких UI-коммитов в экстренном исправлении.

AWS/IAM/S3/KMS/HTTPS/auth/backend оставлены без изменений. Известная прямая POCO
HTTPS timeout остаётся независимой задачей `HTTPS_NETWORK_TIMEOUT`; прежний USB
тест не объявляется прямой связностью. Новый туннель не создавался, реальные записи
не отправлялись, платные тесты не запускались.

**Следующий практический шаг:** владелец разблокирует DORA-Alpha-Test, затем проверяем
сохранённые данные и модель. После отдельного согласия обновляем подготовленной APK
и выполняем короткую RU/EN/offline/cancel проверку. До этого старая установленная
версия всё ещё содержит прежний срок. Фоновая работа после этой сессии не обещается.
