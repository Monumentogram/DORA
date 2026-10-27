package com.monumentogram.dora.transcript

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import java.util.UUID
import java.util.concurrent.Executors

/** UI ownership is Activity-bound; neither content nor draft is put in saved-instance state. */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
internal class TranscriptActions(private val activity: MainActivity) : AutoCloseable {
    private val runtime = (activity.application as DoraApplication).audioRuntime
    private val worker = Executors.newSingleThreadExecutor()
    private val model = LocalAsrModel(activity.applicationContext)
    private var generation = 0L
    private var closed = false
    private var attempt: RecognitionAttempt? = null
    @Volatile private var cloudApi: CloudApi? = null
    private var cloudIdentity: AudioIdentity? = null
    private val cancellations = EncryptedCloudCancellations(activity.applicationContext)
    private var cloudAction: Pair<String, String>? = null
    var busy by mutableStateOf(false)
        private set

    var document by mutableStateOf<TranscriptDocument?>(null)
        private set

    var history by mutableStateOf<List<AudioIdentity>>(emptyList())
        private set

    var message by mutableStateOf("Откройте сохранённую запись")
        private set

    fun clearVisible() {
        generation++
        cancelAttempt()
        document = null
        history = emptyList()
        message = "Обработка прервана. Откройте расшифровку снова."
    }

    fun load(identity: AudioIdentity) = open { session ->
        documentResult(requireValue(session.transcripts.recover(identity)))
    }

    fun library() = open { session ->
        val entries = requireValue(session.transcripts.history())
        val publish: () -> Unit = {
            history = entries
            message = if (entries.isEmpty()) "Расшифровок пока нет" else "Сохранённые расшифровки"
        }
        publish
    }

    fun recognize(identity: AudioIdentity, language: String) {
        if (busy || closed) return
        val owned = RecognitionAttempt()
        attempt = owned
        val request = generation + 1
        open { session ->
            val file = model.installed() ?: throw RecognitionFailure("MODEL_MISSING")
            if (!WhisperNative.available()) throw RecognitionFailure("RUNTIME_UNAVAILABLE")
            val result =
                LocalRecognitionRun({ pcm, lang, cancel ->
                        WhisperNative.recognize(file, pcm, lang, cancel)
                    })
                    .run(
                        session,
                        identity,
                        UUID.randomUUID().toString(),
                        language,
                        "whisper.cpp:927cfce34f31707e17f2bff35c349632fb9e2c3a/" +
                            "small-q8_0:49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f",
                        owned,
                    ) { state ->
                        activity.runOnUiThread { if (current(request)) message = state }
                    }
            documentResult(requireValue(result))
        }
    }

    fun cancel() {
        message = cancelAttempt()
    }

    private fun cancelAttempt(): String =
        try {
            if (
                attempt?.cancel {
                    cloudAction?.let { (id, destination) -> cancellations.record(id, destination) }
                } == true
            )
                "Отменяем распознавание…"
            else "Завершаем сохранение результата…"
        } catch (_: Exception) {
            cloudFailureMessage("CANCEL_STORAGE")
        } finally {
            cloudApi?.abortTransfer()
        }

    fun recognizeCloud(identity: AudioIdentity, language: String, retryId: String? = null) {
        if (busy || closed) return
        val installation = CloudIdentity(activity.applicationContext)
        val endpoint =
            if (retryId == null) installation.endpoint()
            else
                document
                    ?.takeIf { it.source.identity == identity }
                    ?.jobs
                    ?.singleOrNull { it.id == retryId }
                    ?.consent
                    ?.destination
        if (endpoint == null) {
            message = cloudFailureMessage("SETUP")
            return
        }
        val jobId = retryId ?: UUID.randomUUID().toString()
        val owned = RecognitionAttempt()
        attempt = owned
        cloudIdentity = identity
        // Capture before requestOpen: cancellation may happen while the vault is opening.
        cloudAction = jobId to endpoint
        val request = generation + 1
        open { session ->
            val api = CloudHttpClient(endpoint, installation.installationId, installation::sign)
            cloudApi = api
            if (owned.cancelled() || cancellations.blocked(jobId, endpoint))
                throw CloudFailure("CANCELLED")
            if (retryId == null) {
                val source =
                    (requireValue(session.originals.acquire(identity))
                            as? OriginalAudioStatus.Available)
                        ?.reference ?: throw CloudFailure("SOURCE_OR_STORAGE")
                val time = System.currentTimeMillis()
                requireValue(
                    session.transcripts.beginCloud(
                        source,
                        jobId,
                        language,
                        CloudConsent(grantedAt = time, destination = endpoint),
                        time,
                    )
                )
            }
            val result =
                CloudRecognitionRun(api, cancellations).run(session, identity, jobId, owned) { state
                    ->
                    activity.runOnUiThread { if (current(request)) message = state }
                }
            documentResult(requireValue(result))
        }
    }

    fun cancelCloud(identity: AudioIdentity, jobId: String) {
        if (busy || closed) return
        try {
            val visible = requireNotNull(document)
            require(visible.source.identity == identity)
            val grant = requireNotNull(visible.jobs.single { it.id == jobId }.consent)
            // Persist on the calling UI thread, before requestOpen can lose its foreground lease.
            cancellations.record(jobId, grant.destination)
        } catch (_: Exception) {
            message = cloudFailureMessage("CANCEL_STORAGE")
            return
        }
        open { session -> reconcileCancellation(session, identity, jobId) }
    }

    private fun reconcileCancellation(
        session: ProductAudioSession,
        identity: AudioIdentity,
        jobId: String,
    ): () -> Unit {
        val doc = requireValue(session.transcripts.revokeCloud(identity, jobId))
        val grant = requireNotNull(doc.jobs.single { it.id == jobId }.consent)
        val installation = CloudIdentity(activity.applicationContext)
        var remoteConfirmed = false
        try {
            if (installation.endpoint() == grant.destination) {
                val api =
                    CloudHttpClient(
                        grant.destination,
                        installation.installationId,
                        installation::sign,
                    )
                val result = api.cancel(jobId)
                remoteConfirmed = result.status == "CANCELLED" && !result.cleanupPending
            }
        } catch (_: Exception) {
            /* Local revocation remains durable; server watchdog owns cleanup. */
        }
        val publish: () -> Unit = {
            document = doc
            message =
                if (remoteConfirmed) "Отмена подтверждена. Исходное аудио сохранено."
                else "Отправка запрещена. Удаление временных данных в облаке ещё не подтверждено."
        }
        return publish
    }

    fun edit(identity: AudioIdentity, version: String, revision: Long, text: String) =
        open { session ->
            documentResult(
                requireValue(
                    session.transcripts.edit(
                        identity,
                        version,
                        revision,
                        text,
                        System.currentTimeMillis(),
                    )
                )
            )
        }

    fun select(identity: AudioIdentity, version: String, revision: Long, resolved: Set<String>) =
        open { session ->
            documentResult(
                requireValue(
                    session.transcripts.select(
                        identity,
                        version,
                        revision,
                        resolved,
                        System.currentTimeMillis(),
                    )
                )
            )
        }

    private fun documentResult(value: TranscriptDocument?): () -> Unit = {
        document = value
        message = if (value?.versions?.isNotEmpty() == true) "Готово" else "Расшифровки пока нет"
    }

    private fun open(action: (ProductAudioSession) -> (() -> Unit)) {
        if (busy || closed) return
        busy = true
        message = "Ожидает обработки"
        val request = ++generation
        runtime.requestOpen(activity, AudioOpenMode.OPEN_EXISTING) { available ->
            if (!current(request)) {
                busy = false
                return@requestOpen
            }
            if (available !is AudioAvailability.Available) {
                busy = false
                message = "Хранилище недоступно. Повторите в открытом приложении."
                return@requestOpen
            }
            worker.execute {
                val publish: () -> Unit =
                    try {
                        action(available.session)
                    } catch (error: Exception) {
                        val category = (error as? RecognitionFailure)?.category
                        val latest =
                            if (cloudApi != null)
                                cloudIdentity?.let { sessionIdentity ->
                                    (available.session.transcripts.load(sessionIdentity)
                                            as? com.monumentogram.dora.audio.AudioResult.Value)
                                        ?.value
                                }
                            else null
                        val failure: () -> Unit = {
                            if (latest != null) document = latest
                            message =
                                (error as? CloudFailure)?.let { cloudFailureMessage(it.code) }
                                    ?: failureMessage(category)
                        }
                        failure
                    }
                activity.runOnUiThread {
                    busy = false
                    attempt = null
                    cloudApi = null
                    cloudIdentity = null
                    cloudAction = null
                    if (
                        current(request) &&
                            (runtime.availability as? AudioAvailability.Available)?.session ===
                                available.session
                    )
                        publish()
                }
            }
        }
    }

    private fun current(request: Long) =
        !closed &&
            generation == request &&
            !activity.isDestroyed &&
            !activity.isFinishing &&
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    override fun close() {
        clearVisible()
        closed = true
        worker.shutdown()
    }
}

private fun failureMessage(category: String?): String =
    when (category) {
        "MODEL_MISSING" ->
            "Установите пакет распознавания без интернета. Запись аудио доступна без него."
        "RUNTIME_UNAVAILABLE" -> "Распознавание на этом устройстве пока недоступно в этой сборке."
        "CANCELLED" -> "Распознавание отменено. Сохранённые версии не изменены."
        "SOURCE_UNAVAILABLE",
        "CORRUPT",
        "AUTHENTICATION_FAILED" ->
            "Исходное аудио недоступно или повреждено. Сохранённый текст доступен в истории."
        else -> "Действие не завершено. Откройте актуальную версию и повторите."
    }
