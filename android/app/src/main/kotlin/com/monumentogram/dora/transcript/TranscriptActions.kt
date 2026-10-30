package com.monumentogram.dora.transcript

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.R
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.audio.transcript.PagedTranscriptProgress
import com.monumentogram.dora.audio.transcript.PagedTranscriptSpec
import com.monumentogram.dora.audio.transcript.TranscriptDocument
import com.monumentogram.dora.audio.transcript.TranscriptPageCursor
import com.monumentogram.dora.audio.transcript.TranscriptTextPage
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
    @Volatile private var compositeCloud = false
    var busy by mutableStateOf(false)
        private set

    var document by mutableStateOf<TranscriptDocument?>(null)
        private set

    var localProgress by mutableStateOf<PagedTranscriptProgress?>(null)
        private set

    var cloudProgress by mutableStateOf<PagedTranscriptProgress?>(null)
        private set

    var textPage by mutableStateOf<TranscriptTextPage?>(null)
        private set

    var pageVersion by mutableStateOf<String?>(null)
        private set

    var partialPage by mutableStateOf<TranscriptTextPage?>(null)
        private set

    var partialPageVersion by mutableStateOf<String?>(null)
        private set

    private var localIdentity: AudioIdentity? = null

    var history by mutableStateOf<List<AudioIdentity>>(emptyList())
        private set

    var message by mutableStateOf("Откройте сохранённую запись")
        private set

    fun clearVisible() {
        generation++
        cancelAttempt(revokeCloud = !compositeCloud)
        document = null
        localProgress = null
        cloudProgress = null
        textPage = null
        pageVersion = null
        partialPage = null
        partialPageVersion = null
        history = emptyList()
        message = "Обработка прервана. Откройте расшифровку снова."
    }

    fun load(identity: AudioIdentity) = open { session ->
        val doc = requireValue(session.transcripts.recover(identity))
        val job = doc?.jobs?.lastOrNull()?.takeIf { it.status != "SUCCEEDED" }
        val progress = job?.let {
            requireValue(session.transcripts.paged.progress(identity, it.id))
        }
        val publish = documentResult(doc)
        val result: () -> Unit = {
            publish()
            localProgress = progress?.takeIf { it.spec.engine == "LOCAL" }
            cloudProgress = progress?.takeIf { it.spec.engine == "CLOUD" }
        }
        result
    }

    fun deleteRecording(identity: AudioIdentity, onDeleted: () -> Unit) = open { session ->
        // Revoke every retained Cloud action before erasing its transcript/job metadata.
        // This is local revocation; the UI never claims a remote deletion receipt.
        requireValue(session.transcripts.load(identity))?.jobs?.forEach { job ->
            job.consent?.let { cancellations.record(job.id, it.destination) }
        }
        val result = session.deleteRecordingConfirmed(identity)
        val publish: () -> Unit = {
            document = null
            if (result is com.monumentogram.dora.audio.AudioResult.Value) onDeleted()
            else message = activity.getString(R.string.stage11_delete_incomplete)
        }
        publish
    }

    fun library() = open { session ->
        val entries = requireValue(session.transcripts.history())
        val publish: () -> Unit = {
            history = entries
            message = if (entries.isEmpty()) "Расшифровок пока нет" else "Сохранённые расшифровки"
        }
        publish
    }

    fun recognize(identity: AudioIdentity, language: String, resumeJobId: String? = null) {
        if (busy || closed) return
        val owned = RecognitionAttempt()
        attempt = owned
        localIdentity = identity
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
                        resumeJobId ?: UUID.randomUUID().toString(),
                        language,
                        "whisper.cpp:927cfce34f31707e17f2bff35c349632fb9e2c3a/" +
                            "small-q8_0:49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f",
                        owned,
                    ) { state ->
                        activity.runOnUiThread { if (current(request)) message = state }
                    }
            val publish = documentResult(requireValue(result))
            val complete: () -> Unit = {
                publish()
                localProgress = null
            }
            complete
        }
    }

    fun cancel() {
        message = cancelAttempt()
    }

    private fun cancelAttempt(revokeCloud: Boolean = true): String =
        try {
            if (
                attempt?.cancel {
                    if (revokeCloud)
                        cloudAction?.let { (id, destination) ->
                            cancellations.record(id, destination)
                        }
                } == true
            )
                "Отменяем распознавание…"
            else "Завершаем сохранение результата…"
        } catch (_: Exception) {
            cloudFailureMessage("CANCEL_STORAGE")
        } finally {
            cloudApi?.abortTransfer()
        }

    // Keep one authenticated source lifetime across consent, legacy/composite work and publication.
    @Suppress(
        "ReturnCount",
        "ThrowsCount",
        "LongMethod",
        "CyclomaticComplexMethod",
    ) // Explicit preconditions before identity or job creation.
    fun recognizeCloud(identity: AudioIdentity, language: String, retryId: String? = null) {
        if (busy || closed) return
        if (!cloudConnectionAvailable(activity)) {
            message = activity.getString(R.string.stage11_cloud_offline)
            return
        }
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
            val authenticated =
                if (retryId == null) requireValue(session.originals.openAuthenticated(identity))
                else null
            try {
                if (retryId == null) {
                    val source = requireNotNull(authenticated).reference
                    if (source.frames > CloudAudio.MAX_COMPOSITE_FRAMES)
                        throw CloudFailure("RECORDING_LIMIT")
                    val time = System.currentTimeMillis()
                    compositeCloud = source.frames > CloudAudio.MAX_FRAMES
                    if (compositeCloud) {
                        requireValue(
                            session.transcripts.paged.begin(
                                PagedTranscriptSpec(
                                    source,
                                    jobId,
                                    "CLOUD",
                                    language,
                                    "standard-batch:eu-central-1:" +
                                        if (language == "ru") "ru-RU" else "en-US",
                                    "cloud-balanced-1800-v1",
                                    CloudAudio.MAX_PART_FRAMES,
                                    time,
                                    CloudConsent(grantedAt = time, destination = endpoint),
                                )
                            )
                        )
                    } else
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
                compositeCloud =
                    requireNotNull(requireValue(session.transcripts.load(identity))).source.frames >
                        CloudAudio.MAX_FRAMES
                val result =
                    CloudRecognitionRun(api, cancellations, authenticated = authenticated).run(
                        session,
                        identity,
                        jobId,
                        owned,
                    ) { state ->
                        activity.runOnUiThread { if (current(request)) message = state }
                    }
                documentResult(requireValue(result))
            } finally {
                authenticated?.close()
            }
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

    fun loadPage(
        identity: AudioIdentity,
        version: String,
        after: TranscriptPageCursor?,
        allowPartial: Boolean = false,
    ) = open { session ->
        val page =
            requireValue(
                session.transcripts.paged.page(
                    identity,
                    version,
                    after,
                    allowPartial = allowPartial,
                )
            )
        val publish: () -> Unit = {
            if (allowPartial) {
                if (page != null || partialPageVersion != version) {
                    partialPage = page
                    partialPageVersion = version
                }
            } else if (page != null) {
                textPage = page
                pageVersion = version
            }
            message = if (page == null) "Конец расшифровки" else "Текст загружен"
        }
        publish
    }

    fun previousPage(identity: AudioIdentity, version: String, allowPartial: Boolean = false) {
        val cursor = (if (allowPartial) partialPage else textPage)?.cursor ?: return
        open { session ->
            val after =
                if (cursor.pageIndex > 0) cursor.copy(pageIndex = cursor.pageIndex - 2)
                else if (cursor.partIndex > 0) {
                    val part =
                        requireValue(
                            session.transcripts.paged.part(identity, version, cursor.partIndex - 1)
                        )!!
                    TranscriptPageCursor(cursor.partIndex - 1, part.pageCount - 2)
                } else null
            val page =
                requireValue(
                    session.transcripts.paged.page(
                        identity,
                        version,
                        after,
                        allowPartial = allowPartial,
                    )
                )
            val publish: () -> Unit = {
                if (allowPartial) {
                    partialPage = page
                    partialPageVersion = version
                } else {
                    textPage = page
                    pageVersion = version
                }
            }
            publish
        }
    }

    fun editPage(
        identity: AudioIdentity,
        version: String,
        revision: Long,
        page: TranscriptTextPage,
        text: String,
    ) = open { session ->
        val doc =
            requireValue(
                session.transcripts.paged.editPage(
                    identity,
                    version,
                    page.cursor,
                    revision,
                    page.digest,
                    text,
                    System.currentTimeMillis(),
                )
            )
        val cursor = page.cursor
        val after =
            if (cursor.pageIndex > 0) cursor.copy(pageIndex = cursor.pageIndex - 1)
            else TranscriptPageCursor(cursor.partIndex - 1, Int.MAX_VALUE)
        val updated = requireValue(session.transcripts.paged.page(identity, version, after))
        val publish: () -> Unit = {
            document = doc
            textPage = updated
            pageVersion = version
            message = "Правка сохранена"
        }
        publish
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
        if (value?.jobs?.lastOrNull()?.status == "SUCCEEDED") {
            localProgress = null
            cloudProgress = null
        }
        message = if (value?.versions?.isNotEmpty() == true) "Готово" else "Расшифровки пока нет"
    }

    // Authorization, worker completion and generation-bound UI delivery share one lifecycle.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
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
                            (cloudIdentity ?: localIdentity)?.let { sessionIdentity ->
                                (available.session.transcripts.load(sessionIdentity)
                                        as? com.monumentogram.dora.audio.AudioResult.Value)
                                    ?.value
                            }
                        val pending =
                            latest?.jobs?.lastOrNull()?.takeIf { it.status != "SUCCEEDED" }
                        val processingIdentity = cloudIdentity ?: localIdentity
                        val checkpoint =
                            if (pending != null && processingIdentity != null)
                                (available.session.transcripts.paged.progress(
                                        processingIdentity,
                                        pending.id,
                                    ) as? com.monumentogram.dora.audio.AudioResult.Value)
                                    ?.value
                            else null
                        val failure: () -> Unit = {
                            if (latest != null) document = latest
                            localProgress = checkpoint?.takeIf { it.spec.engine == "LOCAL" }
                            cloudProgress = checkpoint?.takeIf { it.spec.engine == "CLOUD" }
                            message =
                                (error as? CloudFailure)?.let { cloudFailureMessage(it) }
                                    ?: failureMessage(category)
                        }
                        failure
                    }
                activity.runOnUiThread {
                    busy = false
                    attempt = null
                    cloudApi = null
                    cloudIdentity = null
                    localIdentity = null
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
