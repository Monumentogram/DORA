package com.monumentogram.dora.poc.capture.runtime

import android.content.Context
import androidx.core.content.edit
import com.monumentogram.dora.poc.capture.model.RecoveryCandidate
import com.monumentogram.dora.poc.capture.model.RunKind
import java.io.File

class CaptureStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("capture_poc_progress", Context.MODE_PRIVATE)
    val captureDirectory = File(context.filesDir, "captures")

    fun completedRuns(): Set<RunKind> = readRuns(KEY_COMPLETED)

    fun criticalRuns(): Set<RunKind> = readRuns(KEY_CRITICAL)

    fun saveRunProgress(completed: Set<RunKind>, critical: Set<RunKind>) {
        preferences.edit {
            putStringSet(KEY_COMPLETED, completed.map { it.id }.toSet())
            putStringSet(KEY_CRITICAL, critical.map { it.id }.toSet())
        }
    }

    fun recoveryCandidate(): RecoveryCandidate? {
        val file =
            captureDirectory
                .listFiles()
                .orEmpty()
                .filter { it.isFile && (it.name.endsWith(".wav.part") || it.name.endsWith(".wav")) }
                .minByOrNull { it.lastModified() } ?: return null
        return RecoveryCandidate(
            fileName = file.name,
            bytes = file.length(),
            finalized = file.name.endsWith(".wav"),
        )
    }

    fun captureFile(fileName: String): File {
        require(SAFE_FILE_NAME.matches(fileName)) { "Unsafe capture file name" }
        return File(captureDirectory, fileName)
    }

    private fun readRuns(key: String): Set<RunKind> =
        preferences.getStringSet(key, emptySet()).orEmpty().mapNotNull(RunKind::fromId).toSet()

    companion object {
        private const val KEY_COMPLETED = "completed_runs"
        private const val KEY_CRITICAL = "critical_runs"
        private val SAFE_FILE_NAME = Regex("^[a-z0-9-]+\\.wav(?:\\.part)?$")
    }
}
