package com.monumentogram.dora.poc.recovery.candidate

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryMicrofileJournal
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryStreamingJournal
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import org.json.JSONObject

/**
 * Real on-disk corruption recipes. Facts describe the injected change, never its recovery verdict.
 */
internal object RecoveryCampaignArtifactFaults {
    val cases =
        setOf(
            "COR-01",
            "COR-02",
            "COR-04",
            "TRU-02",
            "TRU-03",
            "KEY-02",
            "KEY-03",
            "SPL-02",
            "SPL-05",
            "QUA-01",
        )

    // Fixed fault matrix, with actual per-case mutation receipts.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun mutate(
        context: Context,
        value: KeyConfirmationValue,
        case: String,
        variant: String,
    ): JSONObject {
        require(case in cases)
        val root =
            File(
                context.noBackupFilesDir,
                "poc-recovery/v1/runs/${value.runId.toCanonicalString()}",
            )
        val micro = value.candidate == RecoveryCandidate.MICROFILE
        val units =
            if (micro) AndroidRecoveryMicrofileJournal(context).loadSnapshot(value.runId).units
            else emptyList()
        val publication =
            if (micro)
                AndroidRecoveryMicrofileJournal(context)
                    .loadSnapshot(value.runId)
                    .publications
                    .last()
                    .publicationRelativeName
            else
                (AndroidRecoveryStreamingJournal(context).checkpointChain(value.runId)
                        as RecoveryStreamingJournalReadResult.Value)
                    .value
                    .last()
                    .checkpointRelativeName
        val targetName =
            when (case) {
                "COR-02" -> publication
                "KEY-03" ->
                    if (micro) units[1].keyEnvelopeRelativeName else "key-envelopes/stream.ks"
                "KEY-02" ->
                    if (micro)
                        AndroidRecoveryMicrofileJournal(context)
                            .loadSnapshot(value.runId)
                            .publications
                            .last()
                            .keyEnvelopeRelativeName
                    else
                        (AndroidRecoveryStreamingJournal(context).checkpointChain(value.runId)
                                as RecoveryStreamingJournalReadResult.Value)
                            .value
                            .last()
                            .checkpointKeyEnvelopeRelativeName
                "SPL-05" ->
                    if (micro) "units/u-0000000003.ct.tmp"
                    else "checkpoints/g-00000000000000000999.ct.tmp"
                "QUA-01" -> "campaign-orphan.bin"
                else -> if (micro) units[1].ciphertextRelativeName else "stream/stream.ct"
            }
        val target = File(root, targetName)
        require(target.toPath().normalize().startsWith(root.toPath()))
        var parent = root
        check(OsConstants.S_ISDIR(Os.lstat(parent.path).st_mode))
        targetName.split('/').dropLast(1).forEach {
            parent = File(parent, it)
            check(OsConstants.S_ISDIR(Os.lstat(parent.path).st_mode))
        }
        val creation = case == "SPL-05" || case == "QUA-01"
        if (!creation) check(OsConstants.S_ISREG(Os.lstat(target.path).st_mode))
        val beforeBytes = if (creation) 0L else target.length()
        val before = if (creation) Sha256Value.ZERO else hash(target)
        when (case) {
            "COR-01",
            "COR-02" ->
                RandomAccessFile(target, "rw").use { file ->
                    val position =
                        if (micro || case == "COR-02") file.length() / 2 else 4096L + 128L
                    file.seek(position)
                    val original = file.read()
                    check(original >= 0)
                    file.seek(position)
                    file.write(original xor 1)
                    file.fd.sync()
                }
            "COR-04" -> {
                require(micro)
                val other = File(root, units[2].ciphertextRelativeName)
                check(OsConstants.S_ISREG(Os.lstat(other.path).st_mode))
                val first = target.readBytes()
                val second = other.readBytes()
                write(target, second)
                write(other, first)
            }
            "TRU-02" ->
                RandomAccessFile(target, "rw").use {
                    it.setLength(if (micro) it.length() / 2 else 4096L)
                    it.fd.sync()
                }
            "TRU-03" -> {
                val count =
                    if (micro) 1
                    else
                        when (variant) {
                            "APPEND_1" -> 1
                            "APPEND_4096" -> 4096
                            "APPEND_8192" -> 8192
                            else -> error("Unscheduled append variant")
                        }
                FileOutputStream(target, true).use {
                    it.write(ByteArray(count) { 0x5a })
                    it.fd.sync()
                }
            }
            "KEY-02",
            "KEY-03",
            "SPL-02" -> check(target.delete())
            "SPL-05",
            "QUA-01" -> {
                check(target.createNewFile())
                write(target, ByteArray(40000) { 0x5a })
            }
        }
        val parentFd =
            Os.open(
                target.parentFile!!.path,
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                0,
            )
        try {
            check(OsConstants.S_ISDIR(Os.fstat(parentFd).st_mode))
            Os.fsync(parentFd)
        } finally {
            Os.close(parentFd)
        }
        return JSONObject()
            .put("recipe", case)
            .put("relativeName", targetName)
            .put("beforeBytes", beforeBytes)
            .put("beforeSha256", before.toLowercaseHex())
            .put("afterBytes", if (target.exists()) target.length() else 0)
            .put(
                "afterSha256",
                if (target.exists()) hash(target).toLowercaseHex() else JSONObject.NULL,
            )
            .put(
                "affectedPlaintextStart",
                if (micro && !creation) units[1].plaintextStartInclusive.toLong() else 0,
            )
            .put(
                "keyEnvelopeTargetKind",
                when (case) {
                    "KEY-02" -> if (micro) "MANIFEST" else "CHECKPOINT"
                    "KEY-03" -> if (micro) "MICROFILE" else "STREAM"
                    else -> JSONObject.NULL
                },
            )
            .put(
                "missingArtifactRole",
                when (case) {
                    "KEY-02" -> "PUBLICATION_KEY_ENVELOPE"
                    "KEY-03" -> "DATA_KEY_ENVELOPE"
                    else -> JSONObject.NULL
                },
            )
            .put("recoveryVerdictClaimed", false)
    }

    private fun write(file: File, bytes: ByteArray) {
        FileOutputStream(file, false).use {
            it.write(bytes)
            it.fd.sync()
        }
    }

    private fun hash(file: File): Sha256Value {
        require(file.length() <= 116000000)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return Sha256Value.fromBytes(digest.digest())
    }
}
