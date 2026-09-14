package com.monumentogram.dora.poc.recovery.candidate

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapPublicationCapability
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryJournalDatabase
import com.monumentogram.dora.poc.recovery.journal.AndroidSqliteRecoveryStreamingJournalDatabase
import com.monumentogram.dora.poc.recovery.journal.RecoveryStreamingCheckpointCommitAdapter
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryCandidateStorage
import com.monumentogram.dora.poc.recovery.storage.RecoveryCandidatePathPolicy
import java.io.File
import java.security.MessageDigest

/** Real platform/Keystore composition for synthetic PoC campaign publication only. */
internal object AndroidRecoveryStreamingPublication {
    fun open(
        context: Context,
        confirmation: KeyConfirmationValue,
        capability: BootstrapPublicationCapability,
        observer: (String) -> Unit,
    ): RecoveryStreamingPublicationWriter {
        val app = context.applicationContext
        return RecoveryStreamingPublicationWriter.open(
            confirmation,
            capability,
            AndroidOsRecoveryCandidateStorage(app),
            RecoveryRunAeadProvider()::openExisting,
            { end -> hashPublishedPrefix(app.noBackupFilesDir, confirmation.runId, end) },
            RecoveryStreamingCheckpointCommitAdapter(
                AndroidSqliteRecoveryStreamingJournalDatabase(
                    AndroidRecoveryJournalDatabase.writable(app)
                )
            ),
            observer,
        )
    }

    // Cleanup is thrown only after success; a primary failure retains close as suppressed.
    @Suppress(
        "LongMethod",
        "MagicNumber",
        "TooGenericExceptionCaught",
        "ThrowingExceptionFromFinally",
    )
    private fun hashPublishedPrefix(root: File, runId: RunId, end: ULong): Sha256Value {
        require(end <= 115_654_656UL)
        val paths = RecoveryCandidatePathPolicy.paths(root, runId, "stream/stream.ct")
        val relative = paths.artifact.relativeTo(root).invariantSeparatorsPath.split('/')
        var component = root.absoluteFile
        check(OsConstants.S_ISDIR(Os.lstat(component.path).st_mode))
        relative.dropLast(1).forEach { name ->
            component = File(component, name)
            check(OsConstants.S_ISDIR(Os.lstat(component.path).st_mode)) {
                "Unsafe publication directory"
            }
        }
        val before = Os.lstat(paths.artifact.path)
        check(OsConstants.S_ISREG(before.st_mode))
        val descriptor =
            Os.open(
                paths.artifact.path,
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                0,
            )
        var failure: Throwable? = null
        try {
            val opened = Os.fstat(descriptor)
            check(
                OsConstants.S_ISREG(opened.st_mode) &&
                    opened.st_dev == before.st_dev &&
                    opened.st_ino == before.st_ino
            )
            check(opened.st_size >= 0 && end <= opened.st_size.toULong())
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8_192)
            var position = 0UL
            while (position < end) {
                val count = minOf(buffer.size.toULong(), end - position).toInt()
                val read = Os.pread(descriptor, buffer, 0, count, position.toLong())
                check(read in 1..count) { "Published stream prefix cannot be read exactly" }
                digest.update(buffer, 0, read)
                position += read.toULong()
            }
            val after = Os.fstat(descriptor)
            val pathname = Os.lstat(paths.artifact.path)
            check(
                after.st_dev == opened.st_dev &&
                    after.st_ino == opened.st_ino &&
                    after.st_size == opened.st_size
            )
            check(
                pathname.st_dev == opened.st_dev &&
                    pathname.st_ino == opened.st_ino &&
                    pathname.st_size == opened.st_size
            )
            return Sha256Value.fromBytes(digest.digest())
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
                Os.close(descriptor)
            } catch (close: Throwable) {
                if (failure == null) throw close else failure.addSuppressed(close)
            }
        }
    }
}
