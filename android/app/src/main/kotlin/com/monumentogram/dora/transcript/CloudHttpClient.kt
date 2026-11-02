@file:Suppress("MagicNumber", "TooManyFunctions", "TooGenericExceptionCaught")

package com.monumentogram.dora.transcript

import android.util.Base64
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.transcript.CloudConsent
import com.monumentogram.dora.audio.transcript.CloudSegment
import com.monumentogram.dora.audio.transcript.CloudTranscriptResult
import com.monumentogram.dora.audio.transcript.PagedTranscriptLimits
import com.monumentogram.dora.audio.transcript.PagedTranscriptSpec
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/** All remote content terminates here. No redirects, provider URLs or credentials in logs. */
internal class CloudHttpClient(
    override val endpoint: String,
    private val installation: String,
    private val sign: (ByteArray) -> String,
    private val connect: (URL) -> HttpsURLConnection = {
        it.openConnection() as HttpsURLConnection
    },
) : CloudApi {
    init {
        require(CloudEndpoint.validate(endpoint) == endpoint)
    }

    private var token: String? = null
    private var tokenExpiry = 0L
    @Volatile private var active: HttpsURLConnection? = null
    private val compositeJobs = mutableSetOf<String>()

    override fun register(source: OriginalAudioReference) {
        request(
            "POST",
            "/v1/recordings",
            JSONObject()
                .put("recording_id", source.identity.recordingId.value)
                .put("source_digest", source.digest)
                .put("source_version", source.version)
                .put("frames", source.frames),
        )
    }

    override fun create(
        source: OriginalAudioReference,
        id: String,
        language: String,
        consent: CloudConsent,
    ) =
        job(
            request(
                "POST",
                "/v1/jobs",
                JSONObject()
                    .put("action_id", id)
                    .put("recording_id", source.identity.recordingId.value)
                    .put("language", if (language == "ru") "ru-RU" else "en-US")
                    .put("consent_version", consent.version)
                    .put("consent_source", consent.source)
                    .put("consent_granted_at", consent.grantedAt),
            ),
            id,
        )

    override fun status(id: String) = job(request("GET", path(id)), id)

    override fun retry(id: String, cancelled: () -> Boolean) =
        job(request("POST", path(id) + "/retry", JSONObject(), cancelled), id)

    override fun cancel(id: String) = job(request("POST", path(id) + "/cancel", JSONObject()), id)

    override fun quoteRun(spec: PagedTranscriptSpec): CloudRunQuote {
        val json = request("POST", "/v1/runs/quote", runBody(spec))
        val required = json.getLong("required_micro_usd")
        val available = json.getLong("available_micro_usd")
        require(required >= 0 && available >= 0)
        return CloudRunQuote(
            json.getBoolean("enabled"),
            json.getBoolean("affordable"),
            required,
            available,
            json.getInt("part_count"),
            json.getString("window_policy"),
            json.getLong("max_frames"),
            json.getLong("max_part_frames"),
        )
    }

    override fun createRun(spec: PagedTranscriptSpec): CloudRunState {
        val json = request("POST", "/v1/runs", runBody(spec))
        require(json.getString("id") == spec.jobId)
        val items = json.getJSONArray("parts")
        require(items.length() in 1..48)
        return CloudRunState(
            spec.jobId,
            json.getString("status"),
            json.getString("source_digest"),
            json.getInt("source_version"),
            json.getLong("frames"),
            json.getString("window_policy"),
            List(items.length()) { index ->
                val part = items.getJSONObject(index)
                val id = part.getString("id")
                path(id)
                compositeJobs.add(id)
                CloudRunPart(
                    part.getInt("index"),
                    id,
                    part.getLong("first_frame"),
                    part.getLong("frames"),
                    part.getString("status"),
                )
            },
        )
    }

    override fun runPart(id: String, index: Int): CloudJobState {
        require(index in 0..47)
        path(id)
        val json = request("POST", "/v1/runs/$id/parts/$index", JSONObject())
        return job(json, json.getString("id"))
    }

    private fun runBody(spec: PagedTranscriptSpec): JSONObject {
        val consent = requireNotNull(spec.consent)
        return JSONObject()
            .put("action_id", spec.jobId)
            .put("recording_id", spec.source.identity.recordingId.value)
            .put("source_digest", spec.source.digest)
            .put("source_version", spec.source.version)
            .put("frames", spec.source.frames)
            .put("language", if (spec.language == "ru") "ru-RU" else "en-US")
            .put("consent_version", consent.version)
            .put("consent_source", consent.source)
            .put("consent_granted_at", consent.grantedAt)
            .put("window_policy", spec.windowPolicy)
    }

    override fun upload(
        id: String,
        offset: Long,
        bytes: ByteArray,
        final: Boolean,
        cancelled: () -> Boolean,
    ): CloudJobState {
        require(bytes.size in 1..65536)
        return job(
            request(
                "POST",
                path(id) + "/audio",
                JSONObject()
                    .put("offset", offset)
                    .put("data_base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    .put("final", final),
                cancelled,
            ),
            id,
        )
    }

    override fun abortTransfer() {
        active?.disconnect()
    }

    private fun path(id: String): String {
        require(UUID.fromString(id).toString() == id)
        return "/v1/jobs/$id"
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        cancelled: () -> Boolean = { false },
    ): JSONObject {
        if (cancelled()) throw java.util.concurrent.CancellationException()
        if (token == null || System.currentTimeMillis() / 1000 >= tokenExpiry - 10) {
            val session = exchange("POST", "/v1/session", JSONObject(), null, cancelled)
            token = session.getString("access_token")
            tokenExpiry = session.getLong("expires_at")
        }
        return exchange(method, path, body, token, cancelled)
    }

    @Suppress(
        "ThrowsCount"
    ) // Preserve cancellation and safe server categories across transport failures.
    private fun exchange(
        method: String,
        path: String,
        body: JSONObject?,
        access: String?,
        cancelled: () -> Boolean,
    ): JSONObject {
        val bytes = body?.toString()?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
        val time = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString()
        val digest =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
        val proof =
            "DORA-ASR-V1\n$method\n$path\n$time\n$nonce\n$digest".toByteArray(Charsets.UTF_8)
        val connection = connect(URL(endpoint + path))
        active = connection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.useCaches = false
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Dora-Installation", installation)
            connection.setRequestProperty("X-Dora-Time", time)
            connection.setRequestProperty("X-Dora-Nonce", nonce)
            connection.setRequestProperty("X-Dora-Signature", sign(proof))
            if (access != null) connection.setRequestProperty("Authorization", "Bearer $access")
            if (cancelled()) throw java.util.concurrent.CancellationException()
            if (body != null) writeBody(connection, bytes, cancelled)
            val status = connection.responseCode
            if (status !in 200..299) {
                if (status == 401) token = null
                throw CloudFailure(errorCode(status))
            }
            val response = connection.inputStream.use { readCloudResponse(it, path, compositeJobs) }
            return try {
                JSONObject(String(response, Charsets.UTF_8))
            } finally {
                response.fill(0)
            }
        } catch (error: CloudFailure) {
            throw error
        } catch (error: java.util.concurrent.CancellationException) {
            throw error
        } catch (_: Exception) {
            throw CloudFailure("NETWORK")
        } finally {
            bytes.fill(0)
            active = null
            connection.disconnect()
        }
    }

    private fun errorCode(status: Int): String =
        when (status) {
            401 -> "AUTH"
            403 -> "CONSENT"
            404 -> "NOT_FOUND"
            409 -> "CONFLICT"
            429 -> "QUOTA"
            in 500..599 -> "SERVICE"
            else -> "REQUEST"
        }

    private fun writeBody(
        connection: HttpsURLConnection,
        bytes: ByteArray,
        cancelled: () -> Boolean,
    ) {
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { output ->
            var offset = 0
            while (offset < bytes.size) {
                if (cancelled()) throw java.util.concurrent.CancellationException()
                val length = minOf(4096, bytes.size - offset)
                output.write(bytes, offset, length)
                offset += length
            }
        }
    }

    private fun job(json: JSONObject, id: String): CloudJobState {
        require(json.getString("id") == id)
        val status = json.getString("status")
        require(
            status in
                setOf("UPLOADING", "QUEUED", "IN_PROGRESS", "COMPLETED", "FAILED", "CANCELLED")
        )
        val result =
            json.optJSONObject("result")?.let { raw ->
                val items = raw.getJSONArray("segments")
                require(
                    items.length() <=
                        if (json.has("parent_id")) PagedTranscriptLimits.MAX_CLOUD_SEGMENTS
                        else CloudTranscriptResult.MAX_SEGMENTS
                )
                CloudTranscriptResult(
                    raw.getString("text"),
                    raw.getString("provider_id"),
                    raw.getString("model"),
                    raw.getString("provider_job_id"),
                    raw.getString("source_digest"),
                    raw.getInt("source_version"),
                    raw.getLong("completed_at"),
                    List(items.length()) { index ->
                        val item = items.getJSONObject(index)
                        CloudSegment(
                            item.getString("text"),
                            if (item.isNull("start_ms")) null else item.getLong("start_ms"),
                            if (item.isNull("end_ms")) null else item.getLong("end_ms"),
                            if (item.isNull("confidence")) null else item.getDouble("confidence"),
                        )
                    },
                )
            }
        require((status == "COMPLETED") == (result != null))
        val offset = json.getLong("uploaded_bytes")
        require(offset in 0..57_600_044L)
        return CloudJobState(
            id,
            status,
            offset,
            json.getBoolean("retryable"),
            json.getBoolean("cleanup_pending"),
            result,
            if (json.has("parent_id")) json.getString("parent_id") else null,
            if (json.has("part_index")) json.getInt("part_index") else null,
            if (json.has("first_frame")) json.getLong("first_frame") else null,
            if (json.has("frames")) json.getLong("frames") else null,
        )
    }
}
