package com.monumentogram.dora.stage0.ownedcorpus

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONObject

// Native lifecycle and bounded view factories stay together; business limits live in CapturePolicy.
@Suppress("TooManyFunctions", "MagicNumber")
class MainActivity : Activity() {
    private lateinit var store: CorpusStore
    private lateinit var capture: AudioCapture
    private lateinit var content: LinearLayout
    private lateinit var statusView: TextView
    private var reference: EditText? = null
    private var humanCheck: CheckBox? = null
    private var startButton: Button? = null
    private var stopButton: Button? = null
    private var level: ProgressBar? = null
    private var elapsed: TextView? = null
    private var currentId: String? = null
    private var foreground = false
    private var rendering = false
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var draftSave: Runnable? = null
    private val controls = mutableListOf<View>()
    private var lastStatus = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = OwnedSession.obtain(this, File(filesDir, "owned"))
        store = session.store
        capture = session.capture
        currentId = savedInstanceState?.getString("case_id")
        if (!store.installed() && File(filesDir, "inbox/seed.zip").isFile) {
            try {
                store.importSeed(File(filesDir, "inbox/seed.zip"))
            } catch (_: Exception) {
                lastStatus =
                    "Не удалось проверить пакет заданий. Компьютер должен передать исходный пакет ещё раз."
            }
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (capture.running) waitForInterruptedWorker()
    }

    private fun waitForInterruptedWorker() {
        if (!foreground || isDestroyed) return
        if (capture.running) {
            controls.forEach { it.isEnabled = false }
            stopButton?.isEnabled = false
            message("Завершаем предыдущую попытку и сохраняем исходный звук…")
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
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float = 16f): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(getColor(R.color.owned_on_surface))
            setPadding(0, dp(8), 0, dp(8))
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    // User actions cross platform and private-storage boundaries; failures remain visible and
    // closed.
    @Suppress("TooGenericExceptionCaught")
    private fun button(label: String, tag: String? = null, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            minHeight = dp(52)
            setTextColor(getColor(R.color.owned_on_primary))
            backgroundTintList = ColorStateList.valueOf(getColor(R.color.owned_primary))
            this.tag = tag
            setOnClickListener {
                try {
                    action()
                } catch (error: Exception) {
                    showError(error)
                }
            }
            layoutParams =
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                }
        }

    private fun addControl(view: View) {
        content.addView(view)
        controls.add(view)
    }

    private fun message(value: String, error: Boolean = false) {
        lastStatus = value
        if (::statusView.isInitialized) {
            statusView.text = value
            statusView.visibility = if (value.isBlank()) View.GONE else View.VISIBLE
            statusView.setTextColor(
                getColor(if (error) R.color.owned_error else R.color.owned_primary)
            )
        }
    }

    private fun showError(error: Exception) {
        val code =
            error.message.orEmpty().takeIf { Regex("[A-Z_]{1,80}").matches(it) }
                ?: "LOCAL_OPERATION_FAILED"
        message("Действие не выполнено: $code. Данные не заменены.", true)
    }

    private fun render() {
        rendering = true
        reference = null
        humanCheck = null
        controls.clear()
        addPageHeader()
        if (!store.installed()) {
            content.addView(
                text(
                    "Задания ещё не переданы с компьютера. Подключение выполняется приватным " +
                        "мостом; выбирать файлы вручную не нужно."
                )
            )
            content.addView(
                button("Проверить передачу заданий", "import") {
                    store.importSeed(File(filesDir, "inbox/seed.zip"))
                    render()
                }
            )
            startButton = button("Начать запись", "record") {}.apply { isEnabled = false }
            content.addView(startButton)
            rendering = false
            return
        }
        val seed = store.seed()
        val state = store.state()
        val records = state.getJSONObject("records")
        val items = seed.getJSONArray("items")
        val ids = (0 until items.length()).map { items.getJSONObject(it).getString("id") }
        if (currentId == null || !ids.contains(currentId.orEmpty()))
            currentId =
                ids.firstOrNull {
                    records.optJSONObject(it)?.optBoolean("human_verified_reference") != true
                } ?: ids.first()
        val id = requireNotNull(currentId)
        val item = store.item(id)
        val record = records.optJSONObject(id)
        val rejected = state.getJSONArray("rejected")
        val excluded =
            (0 until rejected.length()).any { rejected.getJSONObject(it).getString("id") == id }
        val enabled = store.recordingEnabled()
        addAuthority(ids, records, enabled)
        addCaseSelector(ids, records, id)
        addRecordingPrompt(item)
        addCaptureControls(record, excluded, enabled)
        addPlayback(record, excluded, id)
        addReferenceEditor(item, record)
        addHumanConfirmation(id, record)
        addTransferControls(ids, id)
        if (capture.running) {
            controls.forEach { it.isEnabled = false }
            stopButton?.isEnabled = false
        }
        rendering = false
    }

    private fun addCaseSelector(ids: List<String>, records: JSONObject, id: String) {
        val spinner =
            Spinner(this).apply {
                minimumHeight = dp(52)
                tag = "cases"
                adapter =
                    ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        ids.map { case ->
                            val verified =
                                records
                                    .optJSONObject(case)
                                    ?.optBoolean("human_verified_reference") == true
                            "${caseTitle(case)}${if (verified) " · ✓" else ""}"
                        },
                    )
                setSelection(ids.indexOf(id))
                onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit

                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            rowId: Long,
                        ) {
                            if (!rendering && !capture.running && ids[position] != currentId) {
                                saveDraftNow()
                                stopPlayback()
                                currentId = ids[position]
                                render()
                            }
                        }
                    }
            }
        addControl(spinner)
    }

    private fun addRecordingPrompt(item: JSONObject) {
        content.addView(
            text(
                if (item.getString("speech_class") == "READ") "Прочитайте как написано · 20–44 с"
                else
                    "Ответьте своими словами · 20–59 с. Не готовьте письменный ответ и не сообщайте личные данные.",
                17f,
            )
        )
        content.addView(
            text(item.getString("material"), 23f).apply {
                tag = "material"
                setBackgroundColor(getColor(R.color.owned_surface))
                setPadding(dp(14), dp(16), dp(14), dp(16))
                setTextIsSelectable(true)
            }
        )
        elapsed = text("0.0 с", 20f).also { content.addView(it) }
        level =
            ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
                .apply {
                    max = 100
                    progress = 0
                    contentDescription = "Уровень сигнала"
                }
                .also { content.addView(it, LinearLayout.LayoutParams(-1, dp(20))) }
    }

    private fun caseTitle(id: String): String {
        val parts = id.split('-')
        val language = if (parts[0] == "ru") "Русский" else "Английский"
        val kind = if (parts[1] == "read") "Чтение" else "Свободная речь"
        return "$language · $kind ${parts[2].toInt()}"
    }

    private fun addCaptureControls(record: JSONObject?, excluded: Boolean, enabled: Boolean) {
        startButton =
            button(
                    if (excluded && record == null) "Повторить техническую попытку"
                    else "Начать запись",
                    "record",
                ) {
                    requestStart()
                }
                .apply { isEnabled = enabled && record == null }
        addControl(startButton!!)
        stopButton =
            button("Остановить и сохранить", "stop") {
                    if (CapturePolicy.canStop(capture.frames)) capture.stop()
                }
                .apply { isEnabled = false }
        content.addView(stopButton)
        content.addView(
            text(
                "Остановка доступна после 20.25 с фактически полученного звука. Уход из " +
                    "приложения завершает попытку как прерванную; автоматически микрофон не " +
                    "возобновляется. Сохранённую запись нельзя перезаписать.",
                14f,
            )
        )
    }

    private fun addPlayback(record: JSONObject?, excluded: Boolean, id: String) {
        if (excluded)
            content.addView(
                text(
                    "Предыдущая техническая попытка сохранена с причиной. До первой принятой " +
                        "записи можно явно повторить это же задание; принятый исходник заменить нельзя.",
                    16f,
                )
            )
        if (record != null)
            content.addView(
                text(
                    recordingDescription(record),
                    16f,
                )
            )
        addControl(
            button("Прослушать / остановить", "play") {
                    if (player != null) stopPlayback() else play(id)
                }
                .apply { isEnabled = record != null }
        )
    }

    private fun recordingDescription(record: JSONObject): String {
        val seconds =
            String.format(Locale.ROOT, "%.2f", record.getLong("duration_us") / 1_000_000.0)
        return "Запись сохранена · $seconds с · WAV mono PCM16 16 кГц"
    }

    private fun addReferenceEditor(item: JSONObject, record: JSONObject?) {
        content.addView(text("Фактическая речь", 22f))
        content.addView(
            text(
                "Прослушайте всю запись. Исправьте оговорки, добавления и пропуски. Для " +
                    "свободного ответа запишите каждое сказанное слово. Исходный текст чтения сам " +
                    "по себе не подтверждает речь.",
                16f,
            )
        )
        reference =
            EditText(this).apply {
                tag = "reference"
                textSize = 18f
                minLines = 6
                gravity = android.view.Gravity.TOP
                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                isEnabled = record != null
                setText(
                    record?.optString("draft_text")
                        ?: if (item.getString("speech_class") == "READ") item.getString("material")
                        else ""
                )
                addTextChangedListener(draftWatcher())
            }
        addControl(reference!!)
    }

    private fun draftWatcher(): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {
                if (!rendering) {
                    humanCheck?.isChecked = false
                    draftSave?.let(handler::removeCallbacks)
                    draftSave = Runnable { saveDraftNow() }.also { handler.postDelayed(it, 600) }
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        }

    private fun addHumanConfirmation(id: String, record: JSONObject?) {
        humanCheck =
            CheckBox(this).apply {
                tag = "human_confirmation"
                text =
                    "Я прослушал(а) всю запись, проверил(а) каждое слово и подтверждаю фактическую речь."
                textSize = 17f
                minHeight = dp(56)
                isChecked = false
                isEnabled = record != null
            }
        addControl(humanCheck!!)
        addControl(
            button("Подтвердить фактический текст", "verify") {
                    store.verifyReference(id, reference!!.text.toString(), humanCheck!!.isChecked)
                    message(
                        "Человеческий эталон сохранён. Передача компьютеру подготовлена приватно."
                    )
                    prepareTransfer()
                    render()
                }
                .apply { isEnabled = record != null }
        )
    }

    private fun addTransferControls(ids: List<String>, id: String) {
        addControl(
            button("Подготовить передачу компьютеру", "export") {
                saveDraftNow()
                prepareTransfer()
            }
        )
        addControl(
            button("Следующее непроверенное", "next") {
                saveDraftNow()
                val latest = store.state().getJSONObject("records")
                currentId =
                    ids.firstOrNull {
                        latest.optJSONObject(it)?.optBoolean("human_verified_reference") != true
                    } ?: id
                stopPlayback()
                render()
            }
        )
        content.addView(
            text(
                "Аудио, тексты и история хранятся только в закрытой области приложения. " +
                    "Интернет и облачная отправка в приложении отсутствуют. Компьютер забирает " +
                    "частный архив через уже настроенное USB-соединение.",
                14f,
            )
        )
    }

    private fun addPageHeader() {
        val scroll =
            ScrollView(this).apply {
                setBackgroundColor(getColor(R.color.owned_background))
                isFillViewport = true
            }
        content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(32))
            }
        scroll.addView(content)
        setContentView(scroll)
        content.addView(text("DORA · 8 записей", 26f))
        content.addView(text("4 задания на русском · 4 на английском", 16f))
        addControl(button("О наборе", "about") { showAbout() })
        statusView =
            text(lastStatus, 16f).apply {
                tag = "status"
                visibility = if (lastStatus.isBlank()) View.GONE else View.VISIBLE
            }
        content.addView(statusView)
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("О частном наборе")
            .setMessage(
                "Восемь записей одного говорящего: по два чтения и два свободных ответа " +
                    "на русском и английском. Все записи входят в оценку; резервов нет.\n\n" +
                    "Шум и временные отметки: НЕ ОЦЕНЕНЫ. Ручная разметка времени не нужна. " +
                    "Набор не доказывает качество для других говорящих.\n\n" +
                    "Микрофон включается только отдельной кнопкой после вашего явного разрешения. " +
                    "Android может применять обработку производителя; полученный звук сохраняется " +
                    "точно в WAV mono PCM16 16 кГц. Интернет и облачная отправка в приложении отсутствуют."
            )
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun addAuthority(ids: List<String>, records: JSONObject, enabled: Boolean) {
        val complete = ids.count {
            records.optJSONObject(it)?.optBoolean("human_verified_reference") == true
        }
        content.addView(
            text("Записано: ${records.length()} / 8 · Проверено: $complete / 8", 20f).apply {
                tag = "progress"
            }
        )
        content.addView(
            text(
                    if (enabled) "Запись разрешена. Микрофон включается только по кнопке."
                    else "Запись отложена. Ожидаем «Готов записывать»."
                )
                .apply { tag = "authority" }
        )
        addControl(
            button("Обновить доступ с компьютера", "activate") {
                store.activate(File(filesDir, "inbox/activation.json"))
                message(
                    "Локальное разрешение проверено. Запись запускается только отдельным нажатием."
                )
                render()
            }
        )
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
        saveDraftNow()
        stopPlayback()
        capture.start(
            requireNotNull(currentId),
            { frames, peak ->
                runOnUiThread {
                    if (!isDestroyed) {
                        elapsed?.text =
                            "${String.format(Locale.ROOT, "%.1f", frames.toDouble() / Wav.RATE)} с"
                        level?.progress = peak
                        stopButton?.isEnabled = capture.running && CapturePolicy.canStop(frames)
                    }
                }
            },
            { accepted, error ->
                runOnUiThread {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (!isDestroyed) {
                        message(
                            if (accepted)
                                "Запись сохранена. Прослушайте её и подтвердите фактическую речь."
                            else
                                "Попытка сохранена как отклонённая: $error. Автоматической замены нет.",
                            !accepted,
                        )
                        render()
                        prepareTransfer()
                    }
                }
            },
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controls.forEach { it.isEnabled = false }
        stopButton?.isEnabled = false
        message("Идёт запись. Оставайтесь в приложении и говорите естественно.")
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
                    "Микрофон разрешён. Для начала отдельно нажмите «Начать запись»."
                else "Микрофон не разрешён; запись не началась."
            )
            if (!isDestroyed) render()
        }
    }

    // A draft save failure must not crash the Activity or erase its previous verified state.
    @Suppress("TooGenericExceptionCaught")
    private fun saveDraftNow() {
        draftSave?.let(handler::removeCallbacks)
        draftSave = null
        val id = currentId
        val value = reference?.text?.toString()
        val unavailable = rendering || !store.installed() || capture.running
        if (unavailable || id == null || value == null) return
        if (!store.state().getJSONObject("records").has(id)) return
        try {
            store.saveDraft(id, value)
        } catch (error: Exception) {
            showError(error)
        }
    }

    private fun prepareTransfer() {
        if (!store.installed() || capture.running) return
        io.execute {
            try {
                store.exportArchive(File(filesDir, "export/mobile-export.zip"))
                runOnUiThread {
                    if (!isDestroyed)
                        message(
                            "Частный архив готов. Компьютер может синхронизировать записи и подтверждённые тексты."
                        )
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isDestroyed)
                        message(
                            "Архив пока не подготовлен. Исходные данные сохранены; повторите передачу позже.",
                            true,
                        )
                }
            }
        }
    }

    private fun play(id: String) {
        player =
            MediaPlayer().apply {
                setDataSource(store.audio(id).absolutePath)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
    }

    private fun stopPlayback() {
        player?.release()
        player = null
    }
}
