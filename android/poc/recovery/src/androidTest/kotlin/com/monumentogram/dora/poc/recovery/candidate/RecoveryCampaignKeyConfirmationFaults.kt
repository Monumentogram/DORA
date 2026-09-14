@file:Suppress("LongMethod", "MagicNumber", "MaxLineLength", "TooGenericExceptionCaught")

package com.monumentogram.dora.poc.recovery.candidate

import android.content.ContentValues
import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationAadCodec
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationPlaintextCodec
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryJournalDatabase
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryBootstrapStorage
import com.monumentogram.dora.poc.recovery.storage.RecoveryBootstrapPathPolicy
import java.io.File
import java.io.FileDescriptor
import java.security.GeneralSecurityException
import java.security.KeyStore
import org.json.JSONObject

/**
 * Fault construction only: public Tink/Keystore, real ciphertext and exact SQLite outer identity.
 * No returned recipe fact is a Recovery classification or a campaign PASS.
 */
internal object RecoveryCampaignKeyConfirmationFaults {
    val kcf07Variants =
        listOf(
            "MALFORMED_PLAINTEXT",
            "WRONG_MAGIC",
            "WRONG_SCHEMA",
            "WRONG_PROTOCOL_ID",
            "WRONG_CANDIDATE_ID",
            "WRONG_RUN_ID",
            "WRONG_CANONICAL_ALIAS_SHA256",
        )

    // All seven prescribed malformed plaintext variants retain real AEAD checks.
    @Suppress("CyclomaticComplexMethod")
    fun mutate(context: Context, value: KeyConfirmationValue, variant: String): JSONObject {
        require(variant == "KCF-04" || variant in kcf07Variants)
        val paths =
            RecoveryBootstrapPathPolicy.paths(
                context.noBackupFilesDir,
                value.runId.toCanonicalString(),
            )
        validateDirectories(context.noBackupFilesDir, paths.confirmationDirectory)
        val beforeStat = Os.lstat(paths.finalFile.path)
        check(OsConstants.S_ISREG(beforeStat.st_mode))
        val before = paths.finalFile.readBytes()
        require(before.isNotEmpty() && before.size <= 4096)
        val beforeHash = Sha256Value.calculate(before)
        val database = AndroidRecoveryJournalDatabase.writable(context)
        val where = "run_id=? AND candidate_id=?"
        val args = arrayOf(value.runId.toCanonicalString(), value.candidate.contractId)
        fun requireRow(bytes: ByteArray) {
            database
                .query(
                    "recovery_run_bootstrap_v1",
                    arrayOf(
                        "key_confirmation_relative_name",
                        "key_confirmation_bytes",
                        "key_confirmation_sha256",
                        "canonical_alias_sha256",
                        "key_confirmation_state",
                    ),
                    where,
                    args,
                    null,
                    null,
                    null,
                )
                .use { cursor ->
                    check(cursor.count == 1 && cursor.moveToFirst())
                    check(
                        cursor.getString(0) == "key-confirmation/run.kc" &&
                            cursor.getLong(1) == bytes.size.toLong()
                    )
                    check(
                        cursor.getBlob(2).contentEquals(Sha256Value.calculate(bytes).toByteArray())
                    )
                    check(
                        cursor.getBlob(3).contentEquals(value.canonicalAliasSha256.toByteArray()) &&
                            cursor.getString(4) == "VALID"
                    )
                }
        }
        requireRow(before)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(keyStore.containsAlias(value.canonicalAlias.removePrefix("android-keystore://")))
        // Test-only fault injector opens the already-owned alias; it never creates/replaces a key.
        val aead =
            AndroidKeystoreKmsClient.Builder()
                .setKeyUri(value.canonicalAlias)
                .build()
                .getAead(value.canonicalAlias)
        val aad = KeyConfirmationAadCodec.encode(value)
        val originalPlaintext = aead.decrypt(before, aad)
        check(KeyConfirmationPlaintextCodec.decode(originalPlaintext) == value)
        val replacement: ByteArray
        val facts =
            JSONObject()
                .put("recipe", if (variant == "KCF-04") variant else "KCF-07")
                .put("variant", variant)
                .put("originalConfirmationSha256", beforeHash.toLowercaseHex())
                .put("originalConfirmationBytes", before.size)
                .put("originalValidConfirmationAuthenticated", true)
                .put("aliasReplaced", false)
                .put("recoveryClassificationClaimed", false)
        if (variant == "KCF-04") {
            val other = value.copy(candidate = otherCandidate(value.candidate))
            val otherAad = KeyConfirmationAadCodec.encode(other)
            val otherPlaintext = KeyConfirmationPlaintextCodec.encode(other)
            replacement = aead.encrypt(otherPlaintext, otherAad)
            check(aead.decrypt(replacement, otherAad).contentEquals(otherPlaintext))
            val failed =
                try {
                    aead.decrypt(replacement, aad)
                    false
                } catch (_: GeneralSecurityException) {
                    true
                }
            check(failed) {
                "Cross-candidate confirmation did not cause real AAD authentication failure"
            }
            facts
                .put("sourceCandidate", other.candidate.contractId)
                .put("sourceCandidateAuthenticated", true)
                .put("targetAadAuthenticationFailed", true)
        } else {
            val malformed = malformedPlaintext(value, variant)
            replacement = aead.encrypt(malformed, aad)
            check(aead.decrypt(replacement, aad).contentEquals(malformed))
            val parsed = runCatching { KeyConfirmationPlaintextCodec.decode(malformed) }
            check(parsed.isFailure || parsed.getOrNull() != value)
            facts
                .put("correctKeyAndExactAadDecryptSucceeded", true)
                .put("decryptedPlaintextInvalidForExpectedRun", true)
        }
        val descriptor =
            Os.open(
                paths.finalFile.path,
                OsConstants.O_WRONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                0,
            )
        try {
            val opened = Os.fstat(descriptor)
            check(
                opened.st_dev == beforeStat.st_dev &&
                    opened.st_ino == beforeStat.st_ino &&
                    opened.st_size == before.size.toLong()
            )
            Os.ftruncate(descriptor, 0)
            writeFully(descriptor, replacement)
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
        val afterHash = Sha256Value.calculate(replacement)
        database.beginTransactionNonExclusive()
        try {
            val columns =
                ContentValues().apply {
                    put("key_confirmation_bytes", replacement.size)
                    put("key_confirmation_sha256", afterHash.toByteArray())
                }
            check(database.update("recovery_run_bootstrap_v1", columns, where, args) == 1)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        requireRow(replacement)
        check(paths.finalFile.readBytes().contentEquals(replacement))
        return facts
            .put("storedCiphertextIdentityUpdatedAndReadBack", true)
            .put("replacementConfirmationBytes", replacement.size)
            .put("replacementConfirmationSha256", afterHash.toLowercaseHex())
    }

    fun prepareCollision(
        context: Context,
        value: KeyConfirmationValue,
        variant: String,
    ): JSONObject {
        require(variant in setOf("TEMP_ONLY", "FINAL_ONLY", "TEMP_AND_FINAL"))
        val paths =
            RecoveryBootstrapPathPolicy.paths(
                context.noBackupFilesDir,
                value.runId.toCanonicalString(),
            )
        check(!paths.runRoot.exists())
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(!keyStore.containsAlias(value.canonicalAlias.removePrefix("android-keystore://")))
        val database = AndroidRecoveryJournalDatabase.writable(context)
        database
            .rawQuery(
                "SELECT count(*) FROM recovery_run_bootstrap_v1 WHERE run_id=?",
                arrayOf(value.runId.toCanonicalString()),
            )
            .use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0)
            }
        val storage = AndroidOsRecoveryBootstrapStorage(context)
        val bytes = "DORA synthetic preexisting KCF-06 namespace".toByteArray(Charsets.UTF_8)
        val handle = storage.openExclusiveConfirmationTemp(value.runId)
        try {
            var offset = 0
            while (offset < bytes.size) {
                val count = storage.write(handle, bytes, offset, bytes.size - offset)
                check(count in 1..(bytes.size - offset))
                offset += count
            }
            storage.fsyncTemp(handle)
        } finally {
            storage.closeTemp(handle)
        }
        if (variant != "TEMP_ONLY") storage.renameTempToFinal(value.runId)
        if (variant == "TEMP_AND_FINAL") {
            validateDirectories(context.noBackupFilesDir, paths.confirmationDirectory)
            val descriptor =
                Os.open(
                    paths.temporaryFile.path,
                    OsConstants.O_CREAT or
                        OsConstants.O_EXCL or
                        OsConstants.O_WRONLY or
                        OsConstants.O_CLOEXEC or
                        OsConstants.O_NOFOLLOW,
                    384,
                )
            try {
                writeFully(descriptor, bytes)
                Os.fsync(descriptor)
            } finally {
                Os.close(descriptor)
            }
        }
        storage.fsyncConfirmationParent(value.runId)
        val facts =
            JSONObject()
                .put("recipe", "KCF-06")
                .put("variant", variant)
                .put("initialAliasAbsent", true)
                .put("initialRunRowAbsent", true)
                .put("injectionCreatedAlias", false)
                .put("recoveryClassificationClaimed", false)
        for ((role, file) in listOf("temp" to paths.temporaryFile, "final" to paths.finalFile)) {
            val present = file.exists()
            check(
                present ==
                    (role == "temp" && variant != "FINAL_ONLY" ||
                        role == "final" && variant != "TEMP_ONLY")
            )
            facts.put("${role}Present", present)
            if (present) {
                check(
                    OsConstants.S_ISREG(Os.lstat(file.path).st_mode) &&
                        file.readBytes().contentEquals(bytes)
                )
                facts.put("${role}Sha256", Sha256Value.calculate(bytes).toLowercaseHex())
            }
        }
        return facts
    }

    private fun malformedPlaintext(value: KeyConfirmationValue, variant: String): ByteArray {
        val valid = KeyConfirmationPlaintextCodec.encode(value)
        return when (variant) {
            "MALFORMED_PLAINTEXT" -> valid.copyOf(valid.size - 1)
            "WRONG_MAGIC" -> valid.also { it[0] = 'X'.code.toByte() }
            "WRONG_SCHEMA" ->
                valid.also {
                    it[8] = 0
                    it[9] = 2
                }
            "WRONG_PROTOCOL_ID" -> valid.also { it[12] = 'x'.code.toByte() }
            "WRONG_CANDIDATE_ID" ->
                KeyConfirmationPlaintextCodec.encode(
                    value.copy(candidate = otherCandidate(value.candidate))
                )
            "WRONG_RUN_ID" -> {
                val bytes =
                    value.runId.toByteArray().also { it[15] = (it[15].toInt() xor 1).toByte() }
                KeyConfirmationPlaintextCodec.encode(value.copy(runId = RunId.fromBytes(bytes)))
            }
            "WRONG_CANONICAL_ALIAS_SHA256" ->
                valid.also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            else -> error("Unsupported KCF-07 variant")
        }
    }

    private fun otherCandidate(candidate: RecoveryCandidate) =
        if (candidate == RecoveryCandidate.STREAM) RecoveryCandidate.MICROFILE
        else RecoveryCandidate.STREAM

    private fun validateDirectories(root: File, directory: File) {
        check(OsConstants.S_ISDIR(Os.lstat(root.path).st_mode))
        var component = root.absoluteFile
        directory.relativeTo(root).invariantSeparatorsPath.split('/').forEach { part ->
            require(part != "." && part != "..")
            component = File(component, part)
            check(OsConstants.S_ISDIR(Os.lstat(component.path).st_mode))
        }
    }

    private fun writeFully(descriptor: FileDescriptor, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = Os.write(descriptor, bytes, offset, bytes.size - offset)
            check(count in 1..(bytes.size - offset))
            offset += count
        }
    }
}
