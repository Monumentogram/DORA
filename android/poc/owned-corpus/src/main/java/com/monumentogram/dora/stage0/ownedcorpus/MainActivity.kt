package com.monumentogram.dora.stage0.ownedcorpus

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONObject

// Native lifecycle and view actions stay together; capture and persistence remain separate.
@Suppress("TooManyFunctions", "MagicNumber")
class MainActivity : Activity() {
    private lateinit var store: CorpusStore
    private lateinit var capture: AudioCapture
    private lateinit var page: GuidedPage
    private var reference: EditText? = null
    private var humanCheck: CheckBox? = null
    private var playbackButton: Button? = null
    private var level: ProgressBar? = null
    private var elapsed: TextView? = null
    private var currentId: String? = null
    private var editing = false
    private var completed = false
    private var foreground = false
    private var rendering = false
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var draftSave: Runnable? = null
    private var lastStatus = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val session = OwnedSession.obtain(this, File(filesDir, "owned"))
        store = session.store
        capture = session.capture
        currentId = savedInstanceState?.getString("case_id")
        editing = savedInstanceState?.getBoolean("editing") ?: false
        completed = savedInstanceState?.getBoolean("completed") ?: false
        restorePrivateInbox()
        render()
    }

    private fun restorePrivateInbox() = safely {
        val archive = File(filesDir, "inbox/seed.zip")
        if (!store.installed() && archive.isFile) store.importSeed(archive)
        val activation = File(filesDir, "inbox/activation.json")
        if (store.installed() && !store.recordingEnabled() && activation.isFile) {
            store.activate(activation)
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (capture.running) waitForInterruptedWorker()
    }

    private fun waitForInterruptedWorker() {
        if (!foreground || isDestroyed) return
        if (capture.running) {
            page.primary.isEnabled = false
            message("Сохраняем прерванную попытку…")
            handler.postDelayed({ waitForInterruptedWorker() }, 100)
        } else {
            render()
            prepareTransfer()
        }
    }

    override fun onPause() {
        foreground = false
        saveDraftNow()
        capture.stop("INTERRUPTED_BACKGROUND")
        stopPlayback()
        super.onPause()
    }

    override fun onDestroy() {
        capture.stop("INTERRUPTED_ACTIVITY_DESTROYED")
        handler.removeCallbacksAndMessages(null)
        io.shutdown()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("case_id", currentId)
        outState.putBoolean("editing", editing)
        outState.putBoolean("completed", completed)
        super.onSaveInstanceState(outState)
    }

    // UI and private-storage boundaries fail closed while retaining their previous saved data.
    @Suppress("TooGenericExceptionCaught")
    private fun safely(action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            val code =
                error.message.orEmpty().takeIf { Regex("[A-Z_]{1,80}").matches(it) }
                    ?: "LOCAL_OPERATION_FAILED"
            message("Не удалось выполнить действие: $code. Данные сохранены.", true)
        }
    }

    private fun message(value: String, error: Boolean = false) {
        lastStatus = value
        if (::page.isInitialized) {
            page.status.text = value
            page.status.visibility = if (value.isBlank()) View.GONE else View.VISIBLE
            page.status.setTextColor(
                getColor(if (error) R.color.owned_error else R.color.owned_primary)
            )
        }
    }

    private fun ids(): List<String> {
        val items = store.seed().getJSONArray("items")
        return (0 until items.length()).map { items.getJSONObject(it).getString("id") }
    }

    private fun verifiedIds(): Set<String> {
        val records = store.state().getJSONObject("records")
        return ids()
            .filter { records.optJSONObject(it)?.optBoolean("human_verified_reference") == true }
            .toSet()
    }

    private fun render() {
        rendering = true
        reference = null
        humanCheck = null
        playbackButton = null
        elapsed = null
        level = null
        page = GuidedPage(this)
        setContentView(page.root)
        page.root.requestApplyInsets()
        page.primary.setOnClickListener { safely { primaryAction() } }
        addNavigation()
        message(lastStatus)
        if (!store.installed()) {
            page.content.addView(
                page.text("Ожидаем задания с компьютера. После передачи откройте приложение снова.")
            )
        } else {
            renderInstalled()
        }
        rendering = false
        refreshPrimary()
    }

    private fun renderInstalled() {
        val ids = ids()
        val verified = verifiedIds()
        if (currentId !in ids) currentId = ids.firstOrNull { it !in verified } ?: ids.first()
        val id = requireNotNull(currentId)
        val records = store.state().getJSONObject("records")
        page.counts.text = "Записано ${records.length()}/8 · Проверено ${verified.size}/8"
        page.counts.tag = "progress"
        if (completed && verified.size == ids.size) {
            page.title.text = "Все 8 заданий готовы"
            page.content.addView(
                page.text("Записи и проверенные тексты сохранены на телефоне.", 23f)
            )
            page.content.addView(
                page.text("Подключите телефон к компьютеру для приватной передачи по USB.")
            )
        } else {
            completed = false
            page.title.text = "${ids.indexOf(id) + 1}/8 · ${caseTitle(id)}"
            val record = records.optJSONObject(id)
            if (record == null) renderPrompt(store.item(id)) else renderSaved(id, record)
        }
    }

    private fun addNavigation() {
        val tasks = page.button("Задания", "cases") { safely { chooseTask() } }
        val more = page.button("Ещё", "more") { safely { showMore() } }
        tasks.isEnabled = store.installed() && !capture.running
        more.isEnabled = !capture.running
        page.navigation.addView(tasks, LinearLayout.LayoutParams(0, -2, 1f))
        page.navigation.addView(more, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun renderPrompt(item: JSONObject) {
        val read = item.getString("speech_class") == "READ"
        if (hasTechnicalAttempt())
            page.content.addView(
                page.text(
                    "Прерванная попытка сохранена. Можно явно повторить это задание до первой принятой записи.",
                    15f,
                )
            )
        page.content.addView(
            page.text(
                if (read) "Прочитайте вслух · 20–44 секунды"
                else "Ответьте своими словами · 20–59 секунд"
            )
        )
        if (!read)
            page.content.addView(
                page.text("Не готовьте письменный ответ и не называйте личные данные.", 15f)
            )
        page.content.addView(
            page.text(item.getString("material"), 23f).apply {
                tag = "material"
                setBackgroundColor(getColor(R.color.owned_surface))
                setPadding(page.dp(12), page.dp(12), page.dp(12), page.dp(12))
                setTextIsSelectable(true)
            }
        )
        if (capture.running) {
            elapsed = page.text("0.0 с", 22f).also { page.content.addView(it) }
            level =
                ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
                    .apply {
                        max = 100
                        contentDescription = "Уровень сигнала"
                    }
                    .also { page.content.addView(it, LinearLayout.LayoutParams(-1, page.dp(20))) }
        }
    }

    private fun renderSaved(id: String, record: JSONObject) {
        page.content.addView(
            page.text(
                "Запись сохранена · ${seconds(record.getLong("duration_us") / 1_000_000.0)} с",
                18f,
            )
        )
        playbackButton =
            page
                .button("▶ Прослушать запись", "play") { safely { togglePlayback(id) } }
                .also { page.content.addView(it) }
        if (record.optBoolean("human_verified_reference") && !editing) {
            page.content.addView(page.text("Текст проверен вами", 20f))
            page.content.addView(
                page.text(record.getString("reference_text"), 20f).apply {
                    setTextIsSelectable(true)
                }
            )
            page.content.addView(
                page.button("Исправить текст", "edit") {
                    safely {
                        stopPlayback()
                        editing = true
                        render()
                    }
                }
            )
        } else {
            addEditor(id, record)
        }
    }

    private fun addEditor(id: String, record: JSONObject) {
        page.content.addView(page.text("Проверьте сказанные слова", 21f))
        page.content.addView(
            page.text(
                "Прослушайте всю запись и исправьте текст: учтите оговорки, пропуски и добавления.",
                16f,
            )
        )
        reference =
            EditText(this)
                .apply {
                    tag = "reference"
                    textSize = 19f
                    minLines = 5
                    gravity = android.view.Gravity.TOP
                    inputType =
                        android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    val item = store.item(id)
                    val fallback =
                        if (item.getString("speech_class") == "READ") item.getString("material")
                        else ""
                    setText(record.optString("draft_text", fallback))
                    addTextChangedListener(draftWatcher())
                }
                .also { page.content.addView(it) }
        humanCheck =
            CheckBox(this)
                .apply {
                    tag = "human_confirmation"
                    text = "Я прослушал(а) всю запись и подтверждаю каждое сказанное слово."
                    textSize = 17f
                    minHeight = page.dp(56)
                    isChecked = false
                    setOnCheckedChangeListener { _, _ -> refreshPrimary() }
                }
                .also { page.content.addView(it) }
    }

    private fun draftWatcher(): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun afterTextChanged(s: Editable?) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!rendering) {
                    humanCheck?.isChecked = false
                    draftSave?.let(handler::removeCallbacks)
                    draftSave =
                        Runnable {
                                saveDraftNow()
                                refreshPrimary()
                            }
                            .also { handler.postDelayed(it, 600) }
                    refreshPrimary()
                }
            }
        }

    private fun action(): GuidedFlow.Action {
        if (capture.running) return GuidedFlow.Action.STOP
        val record = currentId?.let { store.state().getJSONObject("records").optJSONObject(it) }
        return GuidedFlow.action(
            capture.running,
            record != null,
            record?.optBoolean("human_verified_reference") == true && !editing,
            store.recordingEnabled(),
        )
    }

    private fun hasTechnicalAttempt(): Boolean {
        val rejected = store.state().getJSONArray("rejected")
        return (0 until rejected.length()).any {
            rejected.getJSONObject(it).getString("id") == currentId
        }
    }

    private fun refreshPrimary() {
        if (!::page.isInitialized || !store.installed()) return
        when {
            capture.running -> refreshRecordingPrimary()
            completed -> {
                page.primary.text = "Закрыть"
                page.primary.isEnabled = true
            }
            else -> refreshTaskPrimary()
        }
    }

    private fun refreshRecordingPrimary() {
        page.primary.text = "Остановить и сохранить"
        page.primary.isEnabled = CapturePolicy.canStop(capture.frames)
        page.hint.text =
            "${seconds(capture.frames.toDouble() / Wav.RATE)} с · " +
                if (CapturePolicy.canStop(capture.frames)) "можно остановить"
                else "минимум 20 секунд"
        page.hint.visibility = View.VISIBLE
    }

    private fun primaryLabel(action: GuidedFlow.Action): String =
        when (action) {
            GuidedFlow.Action.WAIT -> "Запись пока недоступна"
            GuidedFlow.Action.START ->
                if (hasTechnicalAttempt()) "Повторить попытку записи" else "Начать запись"
            GuidedFlow.Action.STOP -> "Остановить и сохранить"
            GuidedFlow.Action.CONFIRM ->
                if (verifiedIds().size == ids().size - 1) "Подтвердить и завершить"
                else "Подтвердить и дальше"
            GuidedFlow.Action.NEXT ->
                if (verifiedIds().size == ids().size) "Завершить 8 заданий" else "Следующее задание"
        }

    private fun primaryHint(action: GuidedFlow.Action): String =
        when (action) {
            GuidedFlow.Action.WAIT -> "Ожидаем разрешение записи с компьютера."
            GuidedFlow.Action.START -> "Микрофон включится только по нажатию."
            GuidedFlow.Action.STOP -> "Говорите не менее 20 секунд."
            GuidedFlow.Action.CONFIRM -> "После проверки текста поставьте галочку."
            GuidedFlow.Action.NEXT -> "Запись и проверенный текст сохранены."
        }

    private fun refreshTaskPrimary() {
        val action = action()
        page.primary.text = primaryLabel(action)
        page.primary.isEnabled =
            when (action) {
                GuidedFlow.Action.WAIT -> false
                GuidedFlow.Action.STOP -> CapturePolicy.canStop(capture.frames)
                GuidedFlow.Action.CONFIRM ->
                    humanCheck?.isChecked == true && !reference?.text.isNullOrBlank()
                else -> true
            }
        page.hint.text = primaryHint(action)
        page.hint.visibility = View.VISIBLE
    }

    private fun primaryAction() {
        if (!store.installed()) return
        if (completed) {
            finish()
            return
        }
        when (action()) {
            GuidedFlow.Action.START -> requestStart()
            GuidedFlow.Action.STOP ->
                if (CapturePolicy.canStop(capture.frames)) {
                    page.primary.isEnabled = false
                    capture.stop()
                    message("Сохраняем запись…")
                }
            GuidedFlow.Action.CONFIRM -> {
                store.verifyReference(
                    requireNotNull(currentId),
                    reference!!.text.toString(),
                    humanCheck!!.isChecked,
                )
                prepareTransfer()
                nextTask()
            }
            GuidedFlow.Action.NEXT -> nextTask()
            GuidedFlow.Action.WAIT -> Unit
        }
    }

    private fun nextTask() {
        draftSave?.let(handler::removeCallbacks)
        draftSave = null
        hideKeyboard()
        stopPlayback()
        editing = false
        val next = GuidedFlow.next(ids(), verifiedIds(), requireNotNull(currentId))
        completed = next == null
        if (next != null) currentId = next
        message("")
        render()
        if (completed) prepareTransfer()
    }

    private fun chooseTask() {
        saveDraftNow()
        val ids = ids()
        val verified = verifiedIds()
        AlertDialog.Builder(this)
            .setTitle("Выберите задание")
            .setItems(
                ids.map { "${caseTitle(it)}${if (it in verified) " · ✓" else ""}" }.toTypedArray()
            ) { _, index ->
                safely {
                    hideKeyboard()
                    stopPlayback()
                    currentId = ids[index]
                    editing = false
                    completed = false
                    message("")
                    render()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showMore() {
        saveDraftNow()
        AlertDialog.Builder(this)
            .setTitle("DORA · 8 записей")
            .setItems(
                arrayOf("О наборе и приватности", "Передача по USB", "Обновить доступ с компьютера")
            ) { _, index ->
                safely {
                    when (index) {
                        0 -> showAbout()
                        1 -> showTransfer()
                        else -> {
                            restorePrivateInbox()
                            render()
                        }
                    }
                }
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("О наборе и приватности")
            .setMessage(
                profileDescription() +
                    "Все восемь записей " +
                    "одного говорящего входят в оценку, резервов нет. " +
                    "Набор не доказывает качество для других людей.\n\n" +
                    "Шум и временные отметки: НЕ ОЦЕНЕНЫ. Разметка времени не нужна.\n\n" +
                    "Аудио и тексты хранятся в закрытой области приложения. Интернета и облачной отправки нет. " +
                    "Android может применять обработку производителя. " +
                    "Полученный звук сохраняется точно в WAV mono PCM16 16 кГц.\n\n" +
                    "Уход из приложения прерывает запись. Прерванный исходник сохраняется; начать заново " +
                    "можно явно до первой принятой записи. Сохранённую запись заменить нельзя."
            )
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun profileDescription(): String =
        when {
            !store.installed() -> "Состав заданий будет указан после передачи набора. "
            store.seed().getString("protocol_version") == CorpusStore.EASY_ENGLISH_PROTOCOL ->
                "На русском: два чтения и два свободных ответа. На английском: четыре простых текста " +
                    "для чтения. Свободная речь на английском: НЕ ОЦЕНЕНА.\n\n"
            else -> "По два чтения и два свободных ответа на русском и английском. "
        }

    private fun showTransfer() {
        AlertDialog.Builder(this)
            .setTitle("Передача по USB")
            .setMessage(
                "Архив готовится автоматически после записи и проверки текста. Подключите телефон к " +
                    "компьютеру для приватной синхронизации. Интернет не используется."
            )
            .setPositiveButton("Обновить архив") { _, _ ->
                safely {
                    saveDraftNow()
                    prepareTransfer(notify = true)
                }
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun requestStart() {
        check(foreground && !capture.running && store.recordingEnabled()) { "RECORDING_DEFERRED" }
        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
            return
        }
        hideKeyboard()
        stopPlayback()
        capture.start(
            requireNotNull(currentId),
            { frames, peak ->
                runOnUiThread {
                    if (!isDestroyed) {
                        elapsed?.text = "${seconds(frames.toDouble() / Wav.RATE)} с"
                        level?.progress = peak
                        refreshPrimary()
                    }
                }
            },
            { accepted, error ->
                runOnUiThread {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (!isDestroyed) {
                        message(
                            if (accepted) "" else "Попытка прервана: $error. Исходник сохранён.",
                            !accepted,
                        )
                        render()
                        prepareTransfer()
                    }
                }
            },
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        message("")
        render()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) {
            message(
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                    "Микрофон разрешён. Нажмите «Начать запись»."
                else "Микрофон не разрешён; запись не началась."
            )
            if (!isDestroyed) render()
        }
    }

    private fun saveDraftNow() {
        draftSave?.let(handler::removeCallbacks)
        draftSave = null
        val id = currentId
        val value = reference?.text?.toString()
        val unavailable = rendering || !store.installed() || capture.running
        if (unavailable || id == null || value == null) return
        if (store.state().getJSONObject("records").has(id))
            safely {
                store.saveDraft(id, value)
                page.counts.text =
                    "Записано ${store.state().getJSONObject("records").length()}/8 · " +
                        "Проверено ${verifiedIds().size}/8"
            }
    }

    // Export failures stay local and visible; no exception may erase or replace captured data.
    @Suppress("TooGenericExceptionCaught")
    private fun prepareTransfer(notify: Boolean = false) {
        if (!store.installed() || capture.running) return
        io.execute {
            try {
                store.exportArchive(File(filesDir, "export/mobile-export.zip"))
                if (notify)
                    runOnUiThread { if (!isDestroyed) message("Архив готов к передаче по USB.") }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isDestroyed)
                        message(
                            "Архив пока не готов. Данные сохранены; обновите его через «Ещё».",
                            true,
                        )
                }
            }
        }
    }

    private fun togglePlayback(id: String) {
        if (player != null) {
            stopPlayback()
            return
        }
        player =
            MediaPlayer().apply {
                setDataSource(store.audio(id).absolutePath)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
        playbackButton?.text = "■ Остановить прослушивание"
    }

    private fun stopPlayback() {
        player?.release()
        player = null
        playbackButton?.text = "▶ Прослушать запись"
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
            page.root.windowToken,
            0,
        )
        reference?.clearFocus()
    }

    private fun seconds(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    private fun caseTitle(id: String): String {
        val parts = id.split('-')
        val language = if (parts[0] == "ru") "Русский" else "Английский"
        val kind = if (parts[1] == "read") "Чтение" else "Свободная речь"
        return "$language · $kind ${parts[2].toInt()}"
    }
}
