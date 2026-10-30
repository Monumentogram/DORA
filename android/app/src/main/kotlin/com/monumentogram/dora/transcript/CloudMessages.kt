package com.monumentogram.dora.transcript

private const val MICRO_USD_PER_USD = 1_000_000.0

internal fun cloudFailureMessage(error: CloudFailure): String =
    if (error is CloudBudgetFailure) {
        val required =
            String.format(java.util.Locale.US, "%.2f", error.requiredMicroUsd / MICRO_USD_PER_USD)
        val available =
            String.format(java.util.Locale.US, "%.2f", error.availableMicroUsd / MICRO_USD_PER_USD)
        "Нужен резерв до $$required; доступно $$available. Платный запуск заблокирован. " +
            "Исходное аудио и готовые части сохранены."
    } else cloudFailureMessage(error.code)

// One explicit mapping keeps every protocol failure's user-facing recovery instruction together.
@Suppress("CyclomaticComplexMethod")
internal fun cloudFailureMessage(code: String): String =
    when (code) {
        "DURATION_LIMIT" ->
            "Облако пока поддерживает записи до 5 минут. Используйте локальное распознавание; исходное аудио сохранено."
        "LONG_NOT_READY" ->
            "Сервер не подтвердил обработку длинной записи. Доступен прежний путь до 5 минут; исходное аудио сохранено."
        "RECORDING_LIMIT" -> "Поддерживается одна запись до 24 часов. Исходное аудио сохранено."
        "INTERRUPTED" ->
            "Обработка прервана. Готовые части сохранены; продолжите эту операцию вручную."
        "AUTH" -> "Доступ к облаку не подтверждён или истёк. Проверьте приглашение установки."
        "SETUP" -> "Сначала подключите облако. Локальная запись доступна без подключения."
        "CONSENT" ->
            "Разрешение на обработку недействительно. Отмените операцию и подтвердите новую."
        "QUOTA" -> "Достигнут лимит облачной обработки. Повторите позже."
        "CANCEL_STORAGE" -> "Не удалось сохранить отмену. Облачная отправка заблокирована."
        "CANCEL_PENDING" -> "Ожидается подтверждение прежней отмены. Новая отправка запрещена."
        "CANCELLED" ->
            "Отправка отменена. Исходное аудио и версии сохранены. Удаление в облаке может ещё выполняться."
        "WAIT_TIMEOUT" ->
            "Облако ещё обрабатывает запись. Нажмите «Повторить / проверить результат»."
        "SOURCE_OR_STORAGE" ->
            "Исходное аудио или хранилище недоступно. Сохранённые версии не изменены."
        "NETWORK",
        "SERVICE" ->
            "Нет ответа от облака. Повтор проверит прежнюю операцию; ваши данные сохранены."
        else ->
            "Облачная обработка не завершена. Обновите запись и повторите или отмените операцию."
    }
