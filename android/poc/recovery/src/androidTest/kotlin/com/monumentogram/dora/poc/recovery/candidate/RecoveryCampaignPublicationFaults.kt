@file:Suppress(
    "MagicNumber",
    "LongMethod",
    "TooManyFunctions",
    "LongParameterList",
    "CyclomaticComplexMethod",
)

package com.monumentogram.dora.poc.recovery.candidate

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryCheckpointCodec
import com.monumentogram.dora.poc.recovery.contract.RecoveryContract
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestCodec
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.crypto.RecoveryTinkRuntime
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryJournalDatabase
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryStreamingJournal
import com.monumentogram.dora.poc.recovery.journal.RecoveryJournalSchema
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.json.JSONObject

/** Synthetic faults only. Every recipe authenticates the existing real publication first. */
internal object RecoveryCampaignPublicationFaults {
    val cases = setOf("COR-03", "COR-05", "COR-06", "KEY-05", "KEY-06", "PAR-01")

    private data class Publication(
        val generation: Long,
        val committedEnd: Long,
        val previous: Sha256Value,
        val name: String,
        val envelopeName: String,
        val plaintext: ByteArray,
        val ciphertext: ByteArray,
        val envelope: ByteArray,
        val envelopeAad: KeyEnvelopeAad,
        val publicationAad: PublicationAad,
    )

    fun mutate(
        context: Context,
        value: KeyConfirmationValue,
        case: String,
        variant: String,
    ): JSONObject {
        require(case in cases)
        require(case != "COR-05" || value.candidate == RecoveryCandidate.MICROFILE)
        val root =
            File(
                context.noBackupFilesDir,
                "poc-recovery/v1/runs/${value.runId.toCanonicalString()}",
            )
        require(root.isDirectory)
        val database = AndroidRecoveryJournalDatabase.writable(context)
        val stream = value.candidate == RecoveryCandidate.STREAM
        val table =
            if (stream) RecoveryJournalSchema.STREAM_CHECKPOINT_TABLE
            else RecoveryJournalSchema.PUBLICATION_TABLE
        val cipherColumn = if (stream) "checkpoint" else "publication"
        val envelopeColumn = if (stream) "checkpoint_key_envelope" else "key_envelope"
        val publication =
            readPublication(database, table, root, value, cipherColumn, envelopeColumn)
        val facts =
            JSONObject()
                .put("caseId", case)
                .put("mutationVariant", variant)
                .put("targetName", publication.name)
                .put("targetEnvelopeName", publication.envelopeName)
                .put("affectedPlaintextStart", 0)
                .put("generation", publication.generation)
                .put("originalPublicationAuthenticated", true)
                .put("originalPublicationParsed", true)
                .put("beforeCiphertextBytes", publication.ciphertext.size)
                .put("beforeCiphertextSha256", hex(publication.ciphertext))
                .put("beforeEnvelopeBytes", publication.envelope.size)
                .put("beforeEnvelopeSha256", hex(publication.envelope))
        val updates = ContentValues()
        var cipher = publication.ciphertext
        var envelope = publication.envelope
        when (case) {
            "COR-03" -> {
                when (variant) {
                    "FILENAME" ->
                        updates.put(
                            "${cipherColumn}_relative_name",
                            publication.name.replace(".ct", ".missing.ct"),
                        )
                    "LENGTH" -> updates.put("${cipherColumn}_bytes", cipher.size.toLong() + 1)
                    "DIGEST" -> updates.put("${cipherColumn}_sha256", ByteArray(32))
                    "RANGE" -> updates.put("committed_end", publication.committedEnd + 2)
                    else -> error("Unscheduled COR-03 variant")
                }
                // Logical database corruption is this row's declared injection.
                // The temporary per-connection switch is restored before return.
                database.execSQL("PRAGMA ignore_check_constraints=ON")
                try {
                    update(database, table, value.runId, publication.generation, updates)
                } finally {
                    database.execSQL("PRAGMA ignore_check_constraints=OFF")
                }
                facts.put("syntheticSqliteCheckConstraintBypass", true)
            }
            "KEY-05" -> {
                when (variant) {
                    "WRONG_LENGTH" ->
                        updates.put("${envelopeColumn}_bytes", envelope.size.toLong() + 1)
                    "WRONG_DIGEST" -> updates.put("${envelopeColumn}_sha256", ByteArray(32))
                    "INVALID_BINARY" -> envelope = byteArrayOf(0xff.toByte())
                    "PARSER_INVALID" -> envelope = byteArrayOf(0x12, 0)
                    else -> error("Unscheduled KEY-05 variant")
                }
                if (!envelope.contentEquals(publication.envelope)) {
                    replace(root, publication.envelopeName, envelope)
                    updates.put("${envelopeColumn}_bytes", envelope.size.toLong())
                    updates.put("${envelopeColumn}_sha256", sha(envelope).toByteArray())
                }
                updateIdentityIfNeeded(
                    context,
                    value,
                    publication,
                    cipher,
                    envelope,
                    updates,
                    stream,
                )
                update(database, table, value.runId, publication.generation, updates)
            }
            "KEY-06",
            "COR-06" -> {
                require(variant == "CROSS_GENERATION")
                val older =
                    readPublication(
                        database,
                        table,
                        root,
                        value,
                        cipherColumn,
                        envelopeColumn,
                        publication.generation,
                    )
                envelope = older.envelope
                replace(root, publication.envelopeName, envelope)
                updates.put("${envelopeColumn}_bytes", envelope.size.toLong())
                updates.put("${envelopeColumn}_sha256", sha(envelope).toByteArray())
                if (case == "COR-06") {
                    cipher = older.ciphertext
                    replace(root, publication.name, cipher)
                    updates.put("${cipherColumn}_bytes", cipher.size.toLong())
                    updates.put("${cipherColumn}_sha256", sha(cipher).toByteArray())
                }
                updateIdentityIfNeeded(
                    context,
                    value,
                    publication,
                    cipher,
                    envelope,
                    updates,
                    stream,
                )
                update(database, table, value.runId, publication.generation, updates)
                facts
                    .put("sourceGeneration", older.generation)
                    .put("sourcePublicationAuthenticated", true)
                    .put("expectedTargetAadRetained", true)
            }
            "COR-05",
            "PAR-01" -> {
                if (case == "PAR-01" && variant == "SYMLINK") {
                    val targetName =
                        if (stream) RecoveryRelativeNames.streamCiphertext()
                        else
                            RecoveryManifestCodec.decode(publication.plaintext)
                                .entries
                                .first()
                                .ciphertextRelativeName
                    val target = regular(root, targetName)
                    val backup = File(root, "campaign-symlink-target.bin")
                    require(!backup.exists())
                    FileOutputStream(backup).use {
                        it.write(target.readBytes())
                        it.fd.sync()
                    }
                    Os.remove(target.absolutePath)
                    Os.symlink(backup.absolutePath, target.absolutePath)
                    syncDirectory(target.parentFile!!)
                    check(OsConstants.S_ISLNK(Os.lstat(target.absolutePath).st_mode))
                    facts
                        .put("symlinkRelativeName", targetName)
                        .put("symlinkTargetWithinOwnedRun", true)
                } else {
                    val malformed =
                        if (case == "COR-05") malformedManifest(publication.plaintext, variant)
                        else malformedPublication(publication.plaintext, variant, stream)
                    val runAead = RecoveryRunAeadProvider().openExisting(value.runId)
                    val keyset = RecoveryTinkRuntime.newAeadKeyset(publication.envelopeAad)
                    envelope = keyset.serializeEncrypted(runAead)
                    cipher = keyset.encryptPublication(malformed, publication.publicationAad)
                    // Re-open the new exact envelope and confirm actual successful
                    // decrypt of the deliberately invalid bounded plaintext.
                    val verify =
                        RecoveryTinkRuntime.parseEncryptedAeadKeyset(
                            envelope,
                            runAead,
                            publication.envelopeAad,
                        )
                    check(
                        verify
                            .decryptPublication(cipher, publication.publicationAad)
                            .contentEquals(malformed)
                    )
                    replace(root, publication.envelopeName, envelope)
                    replace(root, publication.name, cipher)
                    updates.put("${cipherColumn}_bytes", cipher.size.toLong())
                    updates.put("${cipherColumn}_sha256", sha(cipher).toByteArray())
                    updates.put("${envelopeColumn}_bytes", envelope.size.toLong())
                    updates.put("${envelopeColumn}_sha256", sha(envelope).toByteArray())
                    updateIdentityIfNeeded(
                        context,
                        value,
                        publication,
                        cipher,
                        envelope,
                        updates,
                        stream,
                    )
                    update(database, table, value.runId, publication.generation, updates)
                    facts
                        .put("faultPublicationDecryptSucceeded", true)
                        .put("faultPlaintextBytes", malformed.size)
                }
            }
        }
        return facts
            .put("afterCiphertextBytes", cipher.size)
            .put("afterCiphertextSha256", hex(cipher))
            .put("afterEnvelopeBytes", envelope.size)
            .put("afterEnvelopeSha256", hex(envelope))
            .put("outerMetadataUpdated", updates.size() > 0)
    }

    private fun readPublication(
        database: SQLiteDatabase,
        table: String,
        root: File,
        value: KeyConfirmationValue,
        cipherColumn: String,
        envelopeColumn: String,
        beforeGeneration: Long? = null,
    ): Publication {
        val selection = "run_id=?" + if (beforeGeneration == null) "" else " AND generation<?"
        val args =
            if (beforeGeneration == null) arrayOf(value.runId.toCanonicalString())
            else arrayOf(value.runId.toCanonicalString(), beforeGeneration.toString())
        return database
            .query(table, null, selection, args, null, null, "generation DESC", "1")
            .use { cursor ->
                check(cursor.moveToFirst()) {
                    "Required authenticated publication generation absent"
                }
                fun text(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name))
                fun number(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                fun digest(name: String) =
                    Sha256Value.fromBytes(cursor.getBlob(cursor.getColumnIndexOrThrow(name)))
                val stream = value.candidate == RecoveryCandidate.STREAM
                val generation = number("generation")
                val end = number("committed_end")
                val previous =
                    digest(
                        if (stream) "previous_checkpoint_sha256" else "previous_publication_sha256"
                    )
                val name = text("${cipherColumn}_relative_name")
                val envelopeName = text("${envelopeColumn}_relative_name")
                val cipher = regular(root, name).readBytes()
                val envelope = regular(root, envelopeName).readBytes()
                check(
                    cipher.size.toLong() == number("${cipherColumn}_bytes") &&
                        sha(cipher) == digest("${cipherColumn}_sha256")
                )
                check(
                    envelope.size.toLong() == number("${envelopeColumn}_bytes") &&
                        sha(envelope) == digest("${envelopeColumn}_sha256")
                )
                val envelopeAad =
                    KeyEnvelopeAad(
                        value.candidate,
                        value.runId,
                        if (stream) KeyEnvelopeTargetKind.CHECKPOINT
                        else KeyEnvelopeTargetKind.MANIFEST,
                        generation.toULong(),
                        KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                        0UL,
                        end.toULong(),
                        0UL,
                        previous,
                    )
                val terminal =
                    if (stream) {
                        if (end == 0L) PublicationAad.EMPTY_TERMINAL_UNIT_INDEX
                        else number("durable_non_final_segment_count").toULong() - 1UL
                    } else generation.toULong() - 1UL
                val aad =
                    PublicationAad(
                        value.candidate,
                        value.runId,
                        if (stream) PublicationKind.CHECKPOINT else PublicationKind.MANIFEST,
                        generation.toULong(),
                        terminal,
                        end.toULong(),
                        previous,
                    )
                val keyset =
                    RecoveryTinkRuntime.parseEncryptedAeadKeyset(
                        envelope,
                        RecoveryRunAeadProvider().openExisting(value.runId),
                        envelopeAad,
                    )
                val plaintext = keyset.decryptPublication(cipher, aad)
                if (stream) RecoveryCheckpointCodec.decode(plaintext)
                else RecoveryManifestCodec.decode(plaintext)
                Publication(
                    generation,
                    end,
                    previous,
                    name,
                    envelopeName,
                    plaintext,
                    cipher,
                    envelope,
                    envelopeAad,
                    aad,
                )
            }
    }

    private fun updateIdentityIfNeeded(
        context: Context,
        value: KeyConfirmationValue,
        publication: Publication,
        cipher: ByteArray,
        envelope: ByteArray,
        updates: ContentValues,
        stream: Boolean,
    ) {
        if (!stream) return
        val chain =
            (AndroidRecoveryStreamingJournal(context).checkpointChain(value.runId)
                    as RecoveryStreamingJournalReadResult.Value)
                .value
        val row = chain.single { it.generation == publication.generation.toULong() }
        val identity =
            row.identityInput()
                .copy(
                    checkpointBytes =
                        (updates.getAsLong("checkpoint_bytes") ?: cipher.size.toLong()).toULong(),
                    checkpointSha256 =
                        updates.getAsByteArray("checkpoint_sha256")?.let(Sha256Value::fromBytes)
                            ?: sha(cipher),
                    checkpointEnvelopeBytes =
                        (updates.getAsLong("checkpoint_key_envelope_bytes")
                                ?: envelope.size.toLong())
                            .toULong(),
                    checkpointEnvelopeSha256 =
                        updates
                            .getAsByteArray("checkpoint_key_envelope_sha256")
                            ?.let(Sha256Value::fromBytes) ?: sha(envelope),
                )
        updates.put(
            "checkpoint_identity",
            RecoveryStreamingIdentity.checkpoint(identity).toByteArray(),
        )
    }

    private fun update(
        database: SQLiteDatabase,
        table: String,
        run: RunId,
        generation: Long,
        values: ContentValues,
    ) {
        database.beginTransactionNonExclusive()
        try {
            check(
                database.update(
                    table,
                    values,
                    "run_id=? AND generation=?",
                    arrayOf(run.toCanonicalString(), generation.toString()),
                ) == 1
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    internal fun malformedManifest(plaintext: ByteArray, variant: String): ByteArray {
        RecoveryManifestCodec.decode(plaintext)
        val buffer = ByteBuffer.wrap(plaintext).order(ByteOrder.BIG_ENDIAN)
        buffer.position(identityEnd(plaintext) + 8 + 32 + 8)
        val countOffset = buffer.position()
        val count = buffer.int
        require(count >= 3) { "COR-05 requires at least three authenticated baseline units" }
        val headerEnd = buffer.position()
        val entries =
            List(count) {
                    val start = buffer.position()
                    buffer.position(start + 104)
                    repeat(2) {
                        val size = buffer.short.toInt() and 0xffff
                        buffer.position(buffer.position() + size)
                    }
                    plaintext.copyOfRange(start, buffer.position())
                }
                .toMutableList()
        when (variant) {
            "DUPLICATE_ENTRY" -> entries[1] = entries[0].copyOf()
            "GAP_ENTRY" -> entries.removeAt(1)
            "REORDER_ENTRIES" -> {
                val first = entries[0]
                entries[0] = entries[1]
                entries[1] = first
            }
            "REMOVE_ENTRY" -> entries.removeAt(entries.lastIndex)
            else -> error("Unscheduled COR-05 variant")
        }
        val header = plaintext.copyOfRange(0, headerEnd)
        ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).putInt(countOffset, entries.size)
        return header + entries.fold(byteArrayOf()) { result, entry -> result + entry }
    }

    internal fun malformedPublication(
        plaintext: ByteArray,
        variant: String,
        stream: Boolean,
    ): ByteArray =
        when (variant) {
            "MALFORMED" -> plaintext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
            "OVERSIZED" -> ByteArray(RecoveryContract.MAX_MANIFEST_PLAINTEXT_BYTES + 1)
            "TRAILING_BYTE" -> plaintext + byteArrayOf(0)
            "UNSAFE_PATH",
            "TRAVERSAL_PATH" -> {
                val copy = plaintext.copyOf()
                val offset =
                    if (stream) identityEnd(copy) + 8 + 32 + 4 + 8 + 8 + 8 + 32
                    else identityEnd(copy) + 8 + 32 + 8 + 4 + 104
                val size =
                    ByteBuffer.wrap(copy).order(ByteOrder.BIG_ENDIAN).getShort(offset).toInt() and
                        0xffff
                require(size >= 3)
                val replacement = if (variant == "TRAVERSAL_PATH") "../" else "/x/"
                replacement.toByteArray(Charsets.US_ASCII).copyInto(copy, offset + 2)
                copy
            }
            else -> error("Unscheduled PAR-01 variant")
        }

    private fun identityEnd(bytes: ByteArray): Int {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.position(10)
        repeat(2) {
            val count = buffer.short.toInt() and 0xffff
            buffer.position(buffer.position() + count)
        }
        return buffer.position() + 16
    }

    private fun regular(root: File, name: String): File {
        require(
            name.matches(Regex("[A-Za-z0-9_./-]+")) &&
                name.split('/').none { it.isEmpty() || it == "." || it == ".." }
        )
        val target = File(root, name)
        require(target.canonicalPath.startsWith(root.canonicalPath + File.separator))
        var item: File? = target
        while (item != null && item != root) {
            val mode = Os.lstat(item.absolutePath).st_mode
            check(!OsConstants.S_ISLNK(mode))
            item = item.parentFile
        }
        check(OsConstants.S_ISREG(Os.lstat(target.absolutePath).st_mode))
        require(target.length() in 0..2_000_000)
        return target
    }

    private fun replace(root: File, name: String, bytes: ByteArray) {
        val file = regular(root, name)
        FileOutputStream(file, false).use {
            it.write(bytes)
            it.fd.sync()
        }
        syncDirectory(file.parentFile!!)
        check(file.readBytes().contentEquals(bytes))
    }

    private fun syncDirectory(file: File) {
        val descriptor =
            Os.open(
                file.absolutePath,
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                0,
            )
        try {
            check(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode))
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private fun sha(bytes: ByteArray) =
        Sha256Value.fromBytes(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray) = sha(bytes).toString()
}
