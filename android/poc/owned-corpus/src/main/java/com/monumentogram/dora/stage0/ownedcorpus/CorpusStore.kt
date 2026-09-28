package com.monumentogram.dora.stage0.ownedcorpus

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.time.Instant
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/** Private-only store. No component exposes these operations through intents or a network. */
// One synchronized aggregate owns its bounded lifecycle and private serialization helpers.
@Suppress("TooManyFunctions")
class CorpusStore(private val root: File) {
    companion object {
        private const val COPY_BUFFER = 8192
        private const val MAX_SEED_BYTES = 17_000_000
        private const val MAX_SEED_ENTRIES = 9
        private const val MAX_TEXT_LENGTH = 10000
        private const val CORPUS_SIZE = 8
        const val APP = "DORA_OWNED8_ANDROID_V1"
        const val REDUCED_PROTOCOL = "dora-owned-reduced8-v2"
        const val EASY_ENGLISH_PROTOCOL = "dora-owned-easy-en8-v3"
        const val ATTESTATION =
            "I am the speaker of these recordings and authorize DORA to process this corpus with " +
                "Amazon Transcribe in eu-central-1 solely for the bounded 6.2D evaluation under the " +
                "current DORA Alpha privacy/retention policy."
        val IDS: Set<String> =
            listOf("ru", "en")
                .flatMap { language ->
                    listOf("read", "spontaneous").flatMap { kind ->
                        (1..2).map { "$language-$kind-0$it" }
                    }
                }
                .toSet()
        private val EASY_ENGLISH_IDS =
            IDS.filter { it.startsWith("ru-") }.toSet() +
                setOf("en-read-01", "en-read-02", "en-read-03", "en-read-04")
        private val SEED_PATHS =
            setOf("seed.json") + (IDS + EASY_ENGLISH_IDS).map { "audio/$it.wav" }

        private fun idsForProtocol(protocol: String): Set<String> =
            when (protocol) {
                REDUCED_PROTOCOL -> IDS
                EASY_ENGLISH_PROTOCOL -> EASY_ENGLISH_IDS
                else -> throw IllegalArgumentException("INVALID_SEED_SCHEMA")
            }
    }

    data class Attempt(
        val id: String,
        val attemptId: String,
        val file: File,
        val speechClass: String,
    )

    private val stateFile = AtomicFile(File(root, "state.json"))

    init {
        root.mkdirs()
        recoverInterruptedAttempt()
    }

    @Synchronized
    fun installed(): Boolean =
        File(root, "seed.json").exists() &&
            (stateFile.baseFile.exists() || File(root, "state.json.bak").exists())

    @Synchronized
    fun importSeed(archive: File) {
        val entries = readSeedArchive(archive)
        val raw = requireNotNull(entries["seed.json"]) { "SEED_JSON_MISSING" }
        val seed = JSONObject(raw.toString(Charsets.UTF_8))
        validateSeed(seed)
        val seedHash = Wav.sha256(raw)
        if (installed()) {
            require(state().getString("seed_sha256") == seedHash) {
                "SEED_ALREADY_INSTALLED_DIFFERENT_CONTENT"
            }
            return
        }
        val records = seedRecords(seed, entries)
        // Validation is complete before the first private corpus write.
        entries
            .filterKeys { it != "seed.json" }
            .forEach { (path, bytes) -> immutableWrite(File(root, path), bytes) }
        immutableWrite(File(root, "seed.json"), raw)
        save(
            JSONObject()
                .put("seed_sha256", seedHash)
                .put("recording_enabled", seed.getBoolean("recording_enabled"))
                .put("records", records)
                .put("rejected", JSONArray())
                .put("events", JSONArray())
                .put("pending_attempt", JSONObject.NULL)
        )
    }

    private fun seedRecords(seed: JSONObject, entries: Map<String, ByteArray>): JSONObject {
        val records = JSONObject()
        val declared = mutableSetOf("seed.json")
        val items = seed.getJSONArray("items")
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val id = item.getString("id")
            if (!item.isNull("recording")) {
                val recording = item.getJSONObject("recording")
                val relative = "audio/$id.wav"
                require(recording.getString("audio_path") == relative) { "INVALID_AUDIO_PATH" }
                val audio = requireNotNull(entries[relative]) { "SEED_AUDIO_MISSING" }
                require(Wav.sha256(audio) == recording.getString("audio_sha256")) {
                    "SEED_AUDIO_HASH_MISMATCH"
                }
                val duration = Wav.inspect(audio)
                require(duration == recording.getLong("duration_us")) {
                    "SEED_AUDIO_DURATION_MISMATCH"
                }
                require(
                    CapturePolicy.eligible(
                        (audio.size - Wav.HEADER_BYTES) / 2,
                        item.getString("speech_class"),
                    )
                ) {
                    "SEED_AUDIO_DURATION_INVALID"
                }
                declared.add(relative)
                val reference = item.optJSONObject("reference")
                val verified = reference?.optBoolean("HUMAN_VERIFIED_REFERENCE") == true
                val text = if (verified) reference!!.getString("text") else null
                records.put(
                    id,
                    JSONObject()
                        .put("audio_path", relative)
                        .put("audio_sha256", Wav.sha256(audio))
                        .put("duration_us", duration)
                        .put("reference_text", text ?: JSONObject.NULL)
                        .put("human_verified_reference", verified)
                        .put(
                            "draft_text",
                            text
                                ?: if (item.getString("speech_class") == "READ")
                                    item.getString("material")
                                else "",
                        )
                        .put("reference_history", JSONArray()),
                )
            }
        }
        require(entries.keys == declared) { "UNDECLARED_SEED_FILE" }
        return records
    }

    private fun readSeedArchive(archive: File): Map<String, ByteArray> {
        require(archive.isFile) { "SEED_NOT_AVAILABLE" }
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in SEED_PATHS) {
                    "INVALID_SEED_PATH"
                }
                require(!entries.containsKey(entry.name) && entries.size < MAX_SEED_ENTRIES) {
                    "INVALID_SEED_ENTRIES"
                }
                val bytes = readSeedEntry(zip)
                total += bytes.size
                require(total <= MAX_SEED_BYTES) { "SEED_TOO_LARGE" }
                entries[entry.name] = bytes
            }
        }
        return entries
    }

    private fun readSeedEntry(zip: ZipInputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(COPY_BUFFER)
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            require(output.size() + count <= Wav.MAX_BYTES) { "SEED_TOO_LARGE" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun validateSeed(seed: JSONObject) {
        require(seed.getString("schema_version") == "1.0" && seed.getString("app") == APP) {
            "INVALID_SEED_SCHEMA"
        }
        val expectedIds = idsForProtocol(seed.getString("protocol_version"))
        require(Regex("[0-9a-f]{32}").matches(seed.getString("seed_id"))) { "INVALID_SEED_ID" }
        listOf("inventory_sha256", "protocol_overlay_sha256").forEach {
            require(Regex("[0-9a-f]{64}").matches(seed.getString(it))) { "INVALID_SEED_BINDING" }
        }
        require(seed.get("recording_enabled") == false) { "INITIAL_SEED_MUST_BE_DEFERRED" }
        val attestation = seed.getJSONObject("attestation")
        require(attestation.get("confirmed") is Boolean && attestation.getBoolean("confirmed")) {
            "HOST_ATTESTATION_REQUIRED"
        }
        require(
            attestation.getString("version") == "dora-owned-corpus-attestation-v1" &&
                attestation.getString("text") == ATTESTATION
        ) {
            "INVALID_ATTESTATION"
        }
        require(
            Wav.sha256(attestation.getString("text").toByteArray(Charsets.UTF_8)) ==
                attestation.getString("sha256")
        ) {
            "ATTESTATION_HASH_MISMATCH"
        }
        validateItems(seed.getJSONArray("items"), expectedIds)
    }

    private fun validateItems(items: JSONArray, expectedIds: Set<String>) {
        require(items.length() == CORPUS_SIZE) { "EIGHT_ITEMS_REQUIRED" }
        val ids = mutableSetOf<String>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val id = item.getString("id")
            require(
                ids.add(id) &&
                    id in expectedIds &&
                    item.getString("language") == id.substringBefore('-') &&
                    item.getString("speech_class") == id.split('-')[1].uppercase()
            ) {
                "INVALID_ITEM_ID"
            }
            require(
                item.getString("condition") == "CLEAN" &&
                    item.getString("material").length in 1..MAX_TEXT_LENGTH
            ) {
                "INVALID_ITEM_MATERIAL"
            }
        }
        require(ids == expectedIds) { "INVALID_INVENTORY" }
    }

    @Synchronized
    fun seed(): JSONObject = JSONObject(File(root, "seed.json").readText(Charsets.UTF_8))

    @Synchronized
    fun activeIds(): Set<String> = idsForProtocol(seed().getString("protocol_version"))

    @Synchronized
    fun state(): JSONObject {
        val value = JSONObject(stateFile.readFully().toString(Charsets.UTF_8))
        require(Wav.sha256(File(root, "seed.json").readBytes()) == value.getString("seed_sha256")) {
            "SEED_HASH_MISMATCH"
        }
        return value
    }

    @Synchronized
    fun item(id: String): JSONObject {
        require(id in activeIds()) { "INVALID_ITEM_ID" }
        val items = seed().getJSONArray("items")
        return (0 until items.length())
            .map { items.getJSONObject(it) }
            .first { it.getString("id") == id }
    }

    @Synchronized
    fun recordingEnabled(): Boolean = installed() && state().getBoolean("recording_enabled")

    @Synchronized
    fun activate(file: File) {
        require(file.isFile && file.length() <= COPY_BUFFER) { "ACTIVATION_NOT_AVAILABLE" }
        val activation = JSONObject(file.readText(Charsets.UTF_8))
        val seed = seed()
        val state = state()
        require(
            activation.getString("schema_version") == "1.0" &&
                activation.getString("app") == APP &&
                activation.get("recording_enabled") == true
        ) {
            "INVALID_ACTIVATION"
        }
        listOf("seed_id", "inventory_sha256", "protocol_overlay_sha256").forEach {
            require(activation.getString(it) == seed.getString(it)) {
                "ACTIVATION_BINDING_MISMATCH"
            }
        }
        require(activation.getString("seed_sha256") == state.getString("seed_sha256")) {
            "ACTIVATION_BINDING_MISMATCH"
        }
        state.put("recording_enabled", true)
        event(state, "OPERATOR_BOUND_ACTIVATION")
        save(state)
    }

    @Synchronized
    fun beginAttempt(id: String): Attempt {
        require(recordingEnabled()) { "RECORDING_DEFERRED" }
        val state = state()
        require(state.isNull("pending_attempt") && !state.getJSONObject("records").has(id)) {
            "CAPTURE_ALREADY_EXISTS"
        }
        val rejected = state.getJSONArray("rejected")
        val technical =
            setOf(
                "INTERRUPTED_BACKGROUND",
                "INTERRUPTED_ACTIVITY_DESTROYED",
                "INTERRUPTED_PROCESS",
                "AUDIO_CAPTURE_FAILED",
                "INVALID_FORMAT_OR_DURATION",
            )
        require(
            (0 until rejected.length())
                .filter { rejected.getJSONObject(it).getString("id") == id }
                .all { rejected.getJSONObject(it).getString("reason") in technical }
        ) {
            "REJECTED_CASE_REQUIRES_OPERATOR_REVIEW"
        }
        val item = item(id)
        val attemptId = UUID.randomUUID().toString().replace("-", "")
        val file = File(root, "rejected/$attemptId.wav")
        immutableWrite(file, Wav.header(0))
        state.put(
            "pending_attempt",
            JSONObject()
                .put("id", id)
                .put("attempt_id", attemptId)
                .put("speech_class", item.getString("speech_class")),
        )
        event(state, "CAPTURE_STARTED", id)
        save(state)
        return Attempt(id, attemptId, file, item.getString("speech_class"))
    }

    @Synchronized
    fun completeAttempt(attempt: Attempt, error: String?): Boolean {
        val state = state()
        val pending = state.getJSONObject("pending_attempt")
        require(pending.getString("attempt_id") == attempt.attemptId) { "ATTEMPT_MISMATCH" }
        val frames =
            ((attempt.file.length().coerceAtLeast(Wav.HEADER_BYTES.toLong()) - Wav.HEADER_BYTES) /
                    2)
                .toInt()
                .coerceAtMost(Wav.MAX_FRAMES)
        RandomAccessFile(attempt.file, "rw").use {
            it.setLength(Wav.HEADER_BYTES.toLong() + frames * 2)
            it.seek(0)
            it.write(Wav.header(frames))
            it.fd.sync()
        }
        val audio = attempt.file.readBytes()
        val accepted = error == null && CapturePolicy.eligible(frames, attempt.speechClass)
        if (accepted) {
            val target = File(root, "audio/${attempt.id}.wav")
            require(!target.exists()) { "IMMUTABLE_AUDIO_EXISTS" }
            target.parentFile!!.mkdirs()
            require(attempt.file.renameTo(target)) { "AUDIO_FINALIZATION_FAILED" }
            state
                .getJSONObject("records")
                .put(
                    attempt.id,
                    JSONObject()
                        .put("audio_path", "audio/${attempt.id}.wav")
                        .put("audio_sha256", Wav.sha256(audio))
                        .put("duration_us", Wav.durationUs(frames))
                        .put("reference_text", JSONObject.NULL)
                        .put("human_verified_reference", false)
                        .put(
                            "draft_text",
                            if (attempt.speechClass == "READ")
                                item(attempt.id).getString("material")
                            else "",
                        )
                        .put("reference_history", JSONArray())
                        .put("capture_source", "ANDROID_AUDIORECORD_MIC_OEM_PROCESSING_UNSPECIFIED")
                        .put("source_equals_evaluation", true),
                )
        } else {
            state
                .getJSONArray("rejected")
                .put(
                    JSONObject()
                        .put("attempt_id", attempt.attemptId)
                        .put("id", attempt.id)
                        .put("audio_path", "rejected/${attempt.attemptId}.wav")
                        .put("audio_sha256", Wav.sha256(audio))
                        .put("reason", error ?: "INVALID_FORMAT_OR_DURATION")
                )
        }
        state.put("pending_attempt", JSONObject.NULL)
        event(state, if (accepted) "CAPTURE_SAVED" else "CAPTURE_REJECTED", attempt.id)
        save(state)
        return accepted
    }

    @Synchronized
    private fun recoverInterruptedAttempt() {
        if (!installed()) return
        val pending = state().optJSONObject("pending_attempt") ?: return
        val id = pending.getString("id")
        val attemptId = pending.getString("attempt_id")
        require(id in activeIds() && Regex("[0-9a-f]{32}").matches(attemptId)) {
            "INVALID_PENDING_ATTEMPT"
        }
        val file = File(root, "rejected/$attemptId.wav")
        if (!file.exists()) {
            // Capture was durably moved but the state write was interrupted. Preserve it as
            // rejected evidence.
            val moved = File(root, "audio/$id.wav")
            if (moved.exists())
                require(moved.renameTo(file)) { "INTERRUPTED_AUDIO_RECOVERY_FAILED" }
            else immutableWrite(file, Wav.header(0))
        }
        completeAttempt(
            Attempt(id, attemptId, file, pending.getString("speech_class")),
            "INTERRUPTED_PROCESS",
        )
    }

    @Synchronized
    fun saveDraft(id: String, text: String) {
        require(text.length <= MAX_TEXT_LENGTH) { "REFERENCE_TOO_LONG" }
        val state = state()
        val record = state.getJSONObject("records").getJSONObject(id)
        if (
            record.optBoolean("human_verified_reference") &&
                record.getString("reference_text") != text
        ) {
            record
                .getJSONArray("reference_history")
                .put(
                    JSONObject()
                        .put("text", record.getString("reference_text"))
                        .put("changed_at", Instant.now().toString())
                )
            record.put("reference_text", JSONObject.NULL).put("human_verified_reference", false)
        }
        record.put("draft_text", text)
        save(state)
    }

    @Synchronized
    fun verifyReference(id: String, text: String, confirmed: Boolean) {
        require(confirmed && text.isNotBlank() && text.length <= MAX_TEXT_LENGTH) {
            "HUMAN_VERIFICATION_REQUIRED"
        }
        saveDraft(id, text)
        val state = state()
        state
            .getJSONObject("records")
            .getJSONObject(id)
            .put("reference_text", text)
            .put("human_verified_reference", true)
        event(state, "HUMAN_VERIFIED_REFERENCE", id)
        save(state)
    }

    @Synchronized
    fun audio(id: String): File {
        require(id in activeIds()) { "INVALID_ITEM_ID" }
        val record = state().getJSONObject("records").getJSONObject(id)
        require(record.getString("audio_path") == "audio/$id.wav") { "INVALID_AUDIO_PATH" }
        return File(root, "audio/$id.wav").also {
            require(Wav.sha256(it.readBytes()) == record.getString("audio_sha256")) {
                "AUDIO_HASH_MISMATCH"
            }
        }
    }

    @Synchronized
    fun exportArchive(destination: File): File {
        val state = state()
        require(state.isNull("pending_attempt")) { "CAPTURE_IN_PROGRESS" }
        val revision = Math.addExact(state.optLong("export_revision", 0), 1)
        state.put("export_revision", revision)
        save(state)
        val seed = seed()
        val files = linkedMapOf<String, File>()
        val records = exportRecords(state, files)
        val rejected = state.getJSONArray("rejected")
        for (i in 0 until rejected.length()) {
            val entry = rejected.getJSONObject(i)
            val path = entry.getString("audio_path")
            require(Regex("rejected/[0-9a-f]{32}\\.wav").matches(path)) { "INVALID_REJECTED_PATH" }
            val file = File(root, path)
            require(Wav.sha256(file.readBytes()) == entry.getString("audio_sha256")) {
                "REJECTED_HASH_MISMATCH"
            }
            files[path] = file
        }
        val manifest =
            JSONObject()
                .put("schema_version", "1.0")
                .put("app", APP)
                .put("seed_sha256", state.getString("seed_sha256"))
                .put("export_revision", revision)
                .put("records", records)
                .put("rejected", rejected)
        val audit = state.toString().toByteArray(Charsets.UTF_8)
        manifest.put("audit_path", "audit.json").put("audit_sha256", Wav.sha256(audit))
        listOf("seed_id", "inventory_sha256", "protocol_overlay_sha256").forEach {
            manifest.put(it, seed.getString(it))
        }
        destination.parentFile!!.mkdirs()
        val temporary = File(destination.parentFile, "mobile-export.pending.zip")
        ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
            fun stored(name: String, bytes: ByteArray) {
                val checksum = CRC32().apply { update(bytes) }.value
                val entry =
                    ZipEntry(name).apply {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        compressedSize = size
                        crc = checksum
                    }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
            stored("export.json", manifest.toString().toByteArray(Charsets.UTF_8))
            stored("audit.json", audit)
            files.forEach { (name, file) ->
                stored(name, file.readBytes())
            }
        }
        FileOutputStream(temporary, true).use { it.fd.sync() }
        require(temporary.renameTo(destination)) { "EXPORT_RENAME_FAILED" }
        return destination
    }

    private fun exportRecords(state: JSONObject, files: MutableMap<String, File>): JSONArray {
        val records = JSONArray()
        for (id in activeIds().sorted()) {
            val record = state.getJSONObject("records").optJSONObject(id) ?: continue
            files["audio/$id.wav"] = audio(id)
            val revisions = JSONArray()
            val history = record.getJSONArray("reference_history")
            for (i in 0 until history.length()) revisions.put(
                JSONObject()
                    .put("text", history.getJSONObject(i).getString("text"))
                    .put("human_verified_reference", true)
            )
            records.put(
                JSONObject()
                    .put("id", id)
                    .put("audio_path", "audio/$id.wav")
                    .put("audio_sha256", record.getString("audio_sha256"))
                    .put(
                        "reference_text",
                        if (record.getBoolean("human_verified_reference"))
                            record.get("reference_text")
                        else record.getString("draft_text"),
                    )
                    .put("human_verified_reference", record.getBoolean("human_verified_reference"))
                    .put("reference_revisions", revisions)
            )
        }
        return records
    }

    private fun event(state: JSONObject, code: String, id: String? = null) {
        state
            .getJSONArray("events")
            .put(
                JSONObject()
                    .put("event", code)
                    .put("at", Instant.now().toString())
                    .put("id", id ?: JSONObject.NULL)
            )
    }

    // Roll back AtomicFile on any serialization/write failure, then propagate the original failure.
    @Suppress("TooGenericExceptionCaught")
    private fun save(value: JSONObject) {
        val output = stateFile.startWrite()
        try {
            output.write(value.toString().toByteArray(Charsets.UTF_8))
            stateFile.finishWrite(output)
        } catch (error: Exception) {
            stateFile.failWrite(output)
            throw error
        }
    }

    private fun immutableWrite(file: File, bytes: ByteArray) {
        file.parentFile!!.mkdirs()
        if (file.exists()) {
            require(file.readBytes().contentEquals(bytes)) { "IMMUTABLE_FILE_MISMATCH" }
            return
        }
        val temporary = File.createTempFile("owned-", ".pending", file.parentFile)
        try {
            FileOutputStream(temporary).use {
                it.write(bytes)
                it.fd.sync()
            }
            require(!file.exists() && temporary.renameTo(file)) { "IMMUTABLE_FILE_WRITE_FAILED" }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}
