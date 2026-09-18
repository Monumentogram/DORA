@file:Suppress(
    "LongMethod",
    "MagicNumber",
    "MaxLineLength",
    "TooGenericExceptionCaught",
    "TooManyFunctions",
)

package com.monumentogram.dora.poc.recovery.candidate

import android.content.Context
import android.database.Cursor
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyRecoveryClassification
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.controller.ConfirmationResult
import com.monumentogram.dora.poc.recovery.controller.RecoveryKeyConfirmationController
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryJournalDatabase
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryReconciliationSource
import com.monumentogram.dora.poc.recovery.journal.RecoveryJournalSchema
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Explicit synthetic cleanup with an immutable retained plan; never an implicit recovery action.
 */
internal object RecoveryCampaignCleanupFaults {
    private val tables =
        listOf(
            RecoveryJournalSchema.STREAM_RANGE_TABLE,
            RecoveryJournalSchema.STREAM_OUTCOME_TABLE,
            RecoveryJournalSchema.STREAM_CHECKPOINT_TABLE,
            RecoveryJournalSchema.QUARANTINE_TABLE,
            RecoveryJournalSchema.UNIT_TABLE,
            RecoveryJournalSchema.PUBLICATION_TABLE,
            RecoveryJournalSchema.RUN_TABLE,
        )

    fun prepare(context: Context, value: KeyConfirmationValue, attemptId: String): JSONObject {
        val control = controlDirectory(context, attemptId)
        val planFile = File(control, "cleanup-plan.json")
        if (!planFile.exists()) {
            val plan =
                JSONObject()
                    .put("schema", "DORA_SYNTHETIC_CLEANUP_PLAN_V1")
                    .put("runId", value.runId.toCanonicalString())
                    .put("candidateId", value.candidate.contractId)
                    .put("artifacts", artifacts(context, value))
                    .put("journal", journal(context, value))
            saveExclusive(planFile, plan.toString().toByteArray(Charsets.UTF_8))
        }
        val plan = readPlan(planFile, value)
        return JSONObject()
            .put("cleanupPlanSha256", digest(planFile))
            .put("cleanupPlanRelativePath", "files/campaign/$attemptId/cleanup-plan.json")
            .put("retainedArtifactReferences", plan.getJSONObject("artifacts"))
            .put("retainedJournalReferences", plan.getJSONObject("journal"))
            .put("deletionStarted", false)
    }

    // Keep retention admission, injected platform failure, and cleanup ordering visible in one
    // flow.
    @Suppress("LongParameterList", "CyclomaticComplexMethod", "NestedBlockDepth", "ReturnCount")
    fun apply(
        context: Context,
        value: KeyConfirmationValue,
        attemptId: String,
        caseId: String,
        retainedCleanupPlanSha256: String,
        retentionReceiptSha256: String,
        resume: Boolean,
        observer: (String) -> Unit = {},
    ): JSONObject {
        require(caseId in setOf("CLN-01", "CLN-02", "CLN-03"))
        require(Regex("[0-9a-f]{64}").matches(retentionReceiptSha256))
        require(Regex("[0-9a-f]{64}").matches(retainedCleanupPlanSha256))
        val control = controlDirectory(context, attemptId)
        val planFile = File(control, "cleanup-plan.json")
        check(digest(planFile) == retainedCleanupPlanSha256) {
            "Host did not retain this exact cleanup plan"
        }
        val plan = readPlan(planFile, value)
        val admission = File(control, "cleanup-retention.json")
        val receipt =
            JSONObject()
                .put("retainedCleanupPlanSha256", retainedCleanupPlanSha256)
                .put("retentionReceiptSha256", retentionReceiptSha256)
                .put("caseId", caseId)
        if (admission.exists()) check(readJson(admission).toString() == receipt.toString())
        else {
            check(!resume) { "Cleanup resume requires a previously admitted retention receipt" }
            saveExclusive(admission, receipt.toString().toByteArray(Charsets.UTF_8))
        }
        verifyRemaining(context, value, plan, allowRemoved = resume)
        val facts =
            JSONObject()
                .put("recipe", caseId)
                .put("retentionReceiptSha256", retentionReceiptSha256)
                .put("retainedCleanupPlanSha256", retainedCleanupPlanSha256)
                .put("receiptContainsReferencesOnly", true)
        if (caseId == "CLN-03") {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = value.canonicalAlias.removePrefix("android-keystore://")
            if (!resume) check(store.containsAlias(alias))
            store.deleteEntry(alias)
            check(!store.containsAlias(alias))
            val snapshot =
                AndroidRecoveryReconciliationSource(context)
                    .loadConfirmation(value.runId)
                    .copy(expected = value)
            val result = RecoveryKeyConfirmationController().evaluate(snapshot)
            check(
                result is ConfirmationResult.Rejected &&
                    result.classification == KeyRecoveryClassification.KEY_UNAVAILABLE
            )
            verifyRemaining(context, value, plan, allowRemoved = false)
            return facts
                .put("classification", result.classification.name)
                .put("keyAliasAbsent", true)
                .put("dataAndJournalRetained", true)
                .put("cleanupState", "KEYS_DELETED_AFTER_RETENTION")
        }
        val expected = plan.getJSONObject("artifacts")
        if (caseId == "CLN-02" && !resume) {
            val first = expected.keys().asSequence().sorted().first()
            val target = resolve(context, value, first)
            val parent = requireNotNull(target.parentFile)
            val mode = Os.lstat(parent.path)
            check(OsConstants.S_ISDIR(mode.st_mode) && mode.st_uid == Process.myUid())
            var denied = false
            try {
                Os.chmod(parent.path, 320) // 0500: this app can observe, but cannot delete a child.
                facts.put("injectedParentMode", Os.lstat(parent.path).st_mode and 511)
                try {
                    Os.remove(target.path)
                } catch (error: ErrnoException) {
                    facts.put("observedDeletionErrno", error.errno)
                    denied = error.errno == OsConstants.EACCES || error.errno == OsConstants.EPERM
                    if (!denied) throw error
                }
            } finally {
                Os.chmod(parent.path, mode.st_mode and 511)
                syncDirectory(parent)
                facts.put(
                    "parentModeRestored",
                    (Os.lstat(parent.path).st_mode and 511) == (mode.st_mode and 511),
                )
            }
            check(denied) { "Filesystem did not enforce the requested deletion denial" }
            verifyRemaining(context, value, plan, allowRemoved = false)
            return facts
                .put("cleanupState", "RETRYABLE")
                .put("verdictAndEvidenceRetained", true)
                .put("deletionDeniedByPlatform", true)
        }
        var removed = 0
        for (name in expected.keys().asSequence().sorted()) {
            val file = resolve(context, value, name)
            if (file.exists()) {
                val wanted = expected.getJSONObject(name)
                check(
                    digest(file) == wanted.getString("sha256") &&
                        file.length() == wanted.getLong("bytes")
                )
                Os.remove(file.path)
                syncDirectory(requireNotNull(file.parentFile))
                removed++
                if (caseId == "CLN-01" && !resume && removed == 1) {
                    saveExclusive(
                        File(control, "cleanup-barrier-issued.json"),
                        facts.toString().toByteArray(Charsets.UTF_8),
                    )
                    observer("CLN-01")
                    error("Cleanup kill barrier returned without process death")
                }
            }
        }
        val database = AndroidRecoveryJournalDatabase.writable(context)
        database.beginTransactionNonExclusive()
        try {
            for (table in tables) database.delete(
                table,
                "run_id=?",
                arrayOf(value.runId.toCanonicalString()),
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        val after = journal(context, value)
        check(tables.all { after.getJSONObject(it).getInt("rows") == 0 })
        for (root in roots(context, value).values) removeEmptyDirectories(root)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = value.canonicalAlias.removePrefix("android-keystore://")
        store.deleteEntry(alias)
        check(!store.containsAlias(alias))
        check(roots(context, value).values.none(File::exists))
        return facts
            .put("cleanupState", "COMPLETE")
            .put("removedFilesThisInvocation", removed)
            .put("keyAliasAbsent", true)
            .put("removedRunRowsVerified", true)
            .put("cleanupComplete", true)
    }

    private fun verifyRemaining(
        context: Context,
        value: KeyConfirmationValue,
        plan: JSONObject,
        allowRemoved: Boolean,
    ) {
        val expected = plan.getJSONObject("artifacts")
        val actual = artifacts(context, value)
        val actualNames = actual.keys().asSequence().toSet()
        val expectedNames = expected.keys().asSequence().toSet()
        check(
            if (allowRemoved) expectedNames.containsAll(actualNames)
            else actualNames == expectedNames
        )
        for (name in actualNames) check(
            actual.getJSONObject(name).toString() == expected.getJSONObject(name).toString()
        )
        val currentRows = journal(context, value)
        for (table in tables) {
            val current = currentRows.getJSONObject(table)
            check(
                current.toString() ==
                    plan.getJSONObject("journal").getJSONObject(table).toString() ||
                    allowRemoved && current.getInt("rows") == 0
            )
        }
    }

    @Suppress("NestedBlockDepth") // Typed table/row/column traversal stays inside one transaction.
    private fun journal(context: Context, value: KeyConfirmationValue): JSONObject {
        val database = AndroidRecoveryJournalDatabase.writable(context)
        val result = JSONObject()
        database.beginTransactionNonExclusive()
        try {
            for (table in tables) {
                val rows = JSONArray()
                database
                    .rawQuery(
                        "SELECT * FROM $table WHERE run_id=? ORDER BY rowid",
                        arrayOf(value.runId.toCanonicalString()),
                    )
                    .use { cursor ->
                        while (cursor.moveToNext()) {
                            val row = JSONArray()
                            for (index in 0 until cursor.columnCount) {
                                val item =
                                    when (cursor.getType(index)) {
                                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                        Cursor.FIELD_TYPE_BLOB ->
                                            Base64.encodeToString(
                                                cursor.getBlob(index),
                                                Base64.NO_WRAP,
                                            )
                                        else -> cursor.getString(index)
                                    }
                                row.put(JSONArray().put(cursor.getType(index)).put(item))
                            }
                            rows.put(row)
                        }
                    }
                result.put(
                    table,
                    JSONObject()
                        .put("rows", rows.length())
                        .put(
                            "typedRowsSha256",
                            Sha256Value.calculate(rows.toString().toByteArray(Charsets.UTF_8))
                                .toLowercaseHex(),
                        ),
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        return result
    }

    private fun artifacts(context: Context, value: KeyConfirmationValue): JSONObject {
        val result = JSONObject()
        var count = 0
        for ((prefix, root) in roots(context, value)) {
            fun visit(file: File) {
                val stat = Os.lstat(file.path)
                check(++count <= 4096)
                if (OsConstants.S_ISDIR(stat.st_mode))
                    requireNotNull(file.listFiles()).sortedBy(File::getName).forEach(::visit)
                else {
                    check(OsConstants.S_ISREG(stat.st_mode)) {
                        "Cleanup refuses unexpected file type"
                    }
                    val relative = "$prefix/${file.relativeTo(root).invariantSeparatorsPath}"
                    result.put(
                        relative,
                        JSONObject().put("bytes", stat.st_size).put("sha256", digest(file)),
                    )
                }
            }
            verifyAncestors(context.noBackupFilesDir, root)
            if (existsNoFollow(root)) visit(root)
        }
        return result
    }

    private fun roots(context: Context, value: KeyConfirmationValue) =
        linkedMapOf(
            "run" to
                File(
                    context.noBackupFilesDir,
                    "poc-recovery/v1/runs/${value.runId.toCanonicalString()}",
                ),
            "quarantine" to
                File(
                    context.noBackupFilesDir,
                    "poc-recovery/v1/quarantine/${value.runId.toCanonicalString()}",
                ),
        )

    private fun resolve(context: Context, value: KeyConfirmationValue, name: String): File {
        val parts = name.split('/')
        require(
            parts.size >= 2 && parts.none { it.isEmpty() || it == "." || it == ".." || '\\' in it }
        )
        val root = roots(context, value).getValue(parts.first())
        return File(root, parts.drop(1).joinToString("/")).also {
            verifyAncestors(context.noBackupFilesDir, it)
        }
    }

    private fun controlDirectory(context: Context, attemptId: String): File {
        require(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,159}").matches(attemptId))
        val file = File(context.filesDir, "campaign/$attemptId")
        for (directory in listOf(context.filesDir, file.parentFile!!, file)) check(
            OsConstants.S_ISDIR(Os.lstat(directory.path).st_mode)
        )
        return file
    }

    private fun readPlan(file: File, value: KeyConfirmationValue): JSONObject =
        readJson(file).also {
            require(it.getString("schema") == "DORA_SYNTHETIC_CLEANUP_PLAN_V1")
            require(
                it.getString("runId") == value.runId.toCanonicalString() &&
                    it.getString("candidateId") == value.candidate.contractId
            )
        }

    private fun existsNoFollow(file: File): Boolean =
        try {
            Os.lstat(file.path)
            true
        } catch (error: ErrnoException) {
            if (error.errno != OsConstants.ENOENT) throw error
            false
        }

    private fun verifyAncestors(base: File, leaf: File) {
        val parents =
            generateSequence(leaf.parentFile) { if (it == base) null else it.parentFile }.toList()
        require(parents.lastOrNull() == base) { "Cleanup path is outside the owned base" }
        var missing = false
        for (parent in parents.asReversed()) {
            if (!existsNoFollow(parent)) missing = true
            else {
                check(!missing && OsConstants.S_ISDIR(Os.lstat(parent.path).st_mode)) {
                    "Cleanup refuses a non-directory ancestor"
                }
            }
        }
    }

    private fun readJson(file: File): JSONObject {
        check(OsConstants.S_ISREG(Os.lstat(file.path).st_mode) && file.length() in 1..16_777_216)
        return JSONObject(file.readText(Charsets.UTF_8))
    }

    private fun digest(file: File): String {
        val before = Os.lstat(file.path)
        check(OsConstants.S_ISREG(before.st_mode) && before.st_size in 0..134_217_728)
        val descriptor =
            Os.open(
                file.path,
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                0,
            )
        try {
            val opened = Os.fstat(descriptor)
            check(
                opened.st_dev == before.st_dev &&
                    opened.st_ino == before.st_ino &&
                    opened.st_size == before.st_size
            )
            val hash = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var total = 0L
            while (total < opened.st_size) {
                val count =
                    Os.read(
                        descriptor,
                        buffer,
                        0,
                        minOf(buffer.size.toLong(), opened.st_size - total).toInt(),
                    )
                check(count > 0)
                hash.update(buffer, 0, count)
                total += count
            }
            check(Os.read(descriptor, buffer, 0, 1) == 0)
            return Sha256Value.fromBytes(hash.digest()).toLowercaseHex()
        } finally {
            Os.close(descriptor)
        }
    }

    private fun saveExclusive(file: File, bytes: ByteArray) {
        val descriptor =
            Os.open(
                file.path,
                OsConstants.O_CREAT or
                    OsConstants.O_EXCL or
                    OsConstants.O_WRONLY or
                    OsConstants.O_CLOEXEC or
                    OsConstants.O_NOFOLLOW,
                384,
            )
        try {
            var offset = 0
            while (offset < bytes.size) {
                val count = Os.write(descriptor, bytes, offset, bytes.size - offset)
                check(count in 1..(bytes.size - offset))
                offset += count
            }
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
        syncDirectory(requireNotNull(file.parentFile))
    }

    private fun syncDirectory(file: File) {
        check(OsConstants.S_ISDIR(Os.lstat(file.path).st_mode))
        val descriptor =
            Os.open(
                file.path,
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                0,
            )
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private fun removeEmptyDirectories(root: File) {
        if (!root.exists()) return
        check(OsConstants.S_ISDIR(Os.lstat(root.path).st_mode))
        requireNotNull(root.listFiles()).forEach(::removeEmptyDirectories)
        Os.remove(root.path)
        syncDirectory(requireNotNull(root.parentFile))
    }
}
