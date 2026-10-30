package com.monumentogram.dora.ui.signal

import java.util.Locale

/** Presentation-only translation of legacy status copy; never pass user text here. */
internal fun uiCopy(text: String): String =
    if (Locale.getDefault().language == "ru") text else legacyCopy[text] ?: text

private val legacyCopy =
    mapOf(
        "Выберите пакет распознавания." to "Choose a recognition model.",
        "Проверяем и устанавливаем пакет распознавания…" to "Verifying and installing the model…",
        "Пакет распознавания установлен." to "Recognition model installed.",
        "Выбор пакета отменён." to "Model selection cancelled.",
        "Не удалось установить пакет. Проверьте выбранный файл и его целостность." to
            "Could not install the model. Check the file and its integrity.",
        "Осталось в Alpha" to "Alpha time remaining",
        "Сохранено на устройстве" to "Saved on device",
        "Микрофон" to "Microphone",
        "Доступно на устройстве" to "Available storage",
        "МБ" to "MB",
        "Открыть расшифровку" to "Open transcript",
        "Версия" to "Version",
        "Правка" to "Edit",
        "Сервер DORA" to "DORA server",
        "ещё не подключён" to "not configured",
        " речи." to " speech.",
        "Перед началом записи" to "Ready to record",
        "Запись" to "Recording",
        "Пауза" to "Paused",
        "К записи" to "Return to recording",
        "Продолжить" to "Resume",
        "Подготовка записи" to "Preparing recording",
        "Подключаем микрофон…" to "Connecting microphone…",
        "Приостанавливаем…" to "Pausing…",
        "Возобновляем запись…" to "Resuming…",
        "Запись продолжается" to "Recording in progress",
        "Запись приостановлена" to "Recording paused",
        "Проверяем сохранение" to "Verifying saved audio",
        "Запись сохранена" to "Recording saved",
        "Запись завершена без аудио" to "No audio recorded",
        "Запись прервана" to "Recording interrupted",
        "Достигнут лимит записи Alpha — 30 минут. Запись сохранена" to
            "Alpha limit reached — 30 minutes. Recording saved",
        "Сохраняем последние секунды…" to "Saving the last few seconds…",
        "Сохраняем…" to "Saving…",
        "Ожидаем подтверждения сохранения" to "Waiting for save confirmation",
        "Сохранённое аудио остаётся на устройстве. Проверьте его состояние ниже." to
            "Saved audio stays on your device. Check its status below.",
        "Я предупредил(а) участников о записи" to
            "I have informed the participants about recording",
        "Не удалось проверить свободное место" to "Could not check available storage",
        "Alpha: до 30 минут аудио. Для начала записи требуется не менее 142 МБ свободного места." to
            "Alpha: up to 30 minutes of audio. At least 142 MB of free storage is required.",
        "Освободите место и обновите проверку. Сохранённые записи останутся доступны." to
            "Free some storage and check again. Saved recordings remain available.",
        "DORA использует микрофон, чтобы записывать звук, пока вы не нажмёте «Пауза» " to
            "DORA uses the microphone until you pause ",
        "или не завершите запись. Запись продолжится при выключенном экране." to
            "or finish recording. Recording continues with the screen off.",
        "Микрофон не разрешён. Запись не началась." to
            "Microphone permission denied. Recording did not start.",
        "Уведомления отключены. Android может показывать запись только в списке активных приложений." to
            "Notifications are disabled. Android may only show recording in the active apps list.",
        "нужно разрешение" to "permission required",
        "доступ разрешён" to "allowed",
        "Действие прервано. Повторите в открытом приложении" to
            "Action interrupted. Try again with the app open",
        "Ключ хранилища временно недоступен" to "Storage key temporarily unavailable",
        "Ключ хранилища недоступен. Аудио сохранено в зашифрованном виде" to
            "Storage key unavailable. Audio remains encrypted",
        "Проверка сохранённого аудио не пройдена" to "Saved audio verification failed",
        "Хранилище занято. Повторите после завершения операции" to
            "Storage is busy. Try again after the operation finishes",
        "Сохранение требует проверки восстановления" to "Saved audio needs recovery verification",
        "Не удалось подтвердить сохранение аудио" to "Could not confirm saved audio",
        "Доступ к микрофону отозван" to "Microphone permission revoked",
        "Микрофон не поддерживает необходимый формат 16 кГц, моно, PCM16" to
            "Microphone does not support 16 kHz mono PCM16",
        "Не удалось подготовить микрофон" to "Could not prepare microphone",
        "Микрофон недоступен" to "Microphone unavailable",
        "Соединение с микрофоном потеряно" to "Microphone connection lost",
        "Ошибка чтения микрофона" to "Microphone read error",
        "Android не разрешил запуск записи. Откройте DORA и повторите" to
            "Android blocked recording. Open DORA and try again",
        "Недостаточно свободного места" to "Not enough free storage",
        "Хранилище не успевает сохранять аудио" to "Storage cannot keep up with recording",
        "Не удалось подтвердить остановку микрофона" to "Could not confirm microphone stopped",
        "Запись остановлена. Необходимо проверить сохранённое аудио" to
            "Recording stopped. Verify the saved audio",
        "История расшифровок" to "Transcript history",
        "Расшифровать / открыть текст" to "Transcribe / open text",
        "Распознать заново" to "Transcribe again",
        "Расшифровать" to "Transcribe",
        "Модель импортируется вручную и не удаляется из-за календарной даты. " to
            "The model is imported manually and has no calendar expiry. ",
        "Точность временной привязки не проверена." to "Timestamp accuracy has not been verified.",
        " · активная" to " · active",
        "Облако" to "Cloud",
        "На устройстве" to "On device",
        "Облачная расшифровка" to "Cloud transcript",
        "Машинный текст на устройстве" to "Local machine transcript",
        "Ваши сохранённые правки" to "Your saved edits",
        "Редактирование текста" to "Edit text",
        "Выбор версии сохраняет всю историю. Решите, какие правки оставить только в прежних версиях." to
            "Version selection preserves history. Choose which edits to keep only in earlier versions.",
        "Проверить отмену в облаке" to "Check cloud cancellation",
        "Отменить облачную операцию" to "Cancel cloud operation",
        "Подключить облако" to "Connect cloud",
        "Разрешить и распознать" to "Allow and transcribe",
        "Только эта сохранённая запись будет отправлена серверу DORA и " to
            "Only this saved recording will be sent to the DORA server and ",
        "Amazon Transcribe во Франкфурте (Германия) для распознавания " to
            "Amazon Transcribe in Frankfurt, Germany, to recognize ",
        "русской" to "Russian",
        "английской" to "English",
        "Временные аудио и файлы результата удаляются после обработки; " to
            "Temporary audio and result files are deleted after processing; ",
        "предельный срок — 24 часа. Принятая сервером расшифровка хранится " to
            "the maximum is 24 hours. Accepted server transcripts are retained ",
        "до 30 дней. Версии на устройстве сохраняются до вашего удаления." to
            "for up to 30 days. On-device versions remain until you delete them.",
        "Обработка разрешена только при проверенном запрете использования " to
            "Processing requires verified restrictions on the use of ",
        "контента для улучшения сервиса. Срок удаления внутренних копий " to
            "content for service improvement. Deletion timing for internal copies ",
        "провайдера не гарантируется. Уже отправленные байты нельзя " to
            "held by the provider is not guaranteed. Data already sent cannot be ",
        "отозвать мгновенно." to "recalled immediately.",
        "Разрешение относится к этой записи и ручным повторам этой операции. " to
            "Consent applies to this recording and manual retries of this operation. ",
        "Автоматической отправки нет. Ваши правки сохранятся отдельной " to
            "There is no automatic upload. Your edits remain in a separate ",
        "версией. Английское распознавание имеет известное ограничение " to
            "version. English recognition has a known accuracy ",
        "точности; доступ — только владельцу Alpha." to
            "limitation; access is for the Alpha owner only.",
        "Укажите HTTPS-адрес сервера DORA. Администратор должен допустить " to
            "Enter the DORA HTTPS server address. An administrator must allow ",
        "открытый ключ этой установки. Копируется только открытый ключ; " to
            "this installation public key. Only the public key is copied; ",
        "секретный ключ остаётся на устройстве." to "the private key stays on the device.",
        "Открытый ключ скопирован" to "Public key copied",
        "Не удалось создать ключ установки" to "Could not create installation key",
        "Нужен HTTPS-адрес без пути, пароля и параметров" to
            "Use an HTTPS address without a path, password or parameters",
        "Ожидает обработки" to "Waiting to process",
        "Cloud: до 5 минут. Для длинных записей используйте Local." to
            "Cloud: up to 5 minutes. Use Local for longer recordings.",
        "Распознавание" to "Transcribing",
        "Сохраняем расшифровку" to "Saving transcript",
        "Готово" to "Ready",
        "Расшифровки пока нет" to "No transcript yet",
        "Расшифровок пока нет" to "No transcripts yet",
        "Сохранённые расшифровки" to "Saved transcripts",
        "Откройте сохранённую запись" to "Open a saved recording",
        "Обработка прервана. Откройте расшифровку снова." to
            "Processing interrupted. Reopen the transcript.",
        "Хранилище недоступно. Повторите в открытом приложении." to
            "Storage unavailable. Try again with the app open.",
        "Установите пакет распознавания без интернета. Запись аудио доступна без него." to
            "Install the offline recognition model. Recording works without it.",
        "Распознавание на этом устройстве пока недоступно в этой сборке." to
            "Local recognition is unavailable in this build.",
        "Распознавание отменено. Сохранённые версии не изменены." to
            "Recognition cancelled. Saved versions are unchanged.",
        "Исходное аудио недоступно или повреждено. Сохранённый текст доступен в истории." to
            "Original audio is unavailable or damaged. Saved text remains in history.",
        "Действие не завершено. Откройте актуальную версию и повторите." to
            "Action not completed. Open the current version and try again.",
    )
