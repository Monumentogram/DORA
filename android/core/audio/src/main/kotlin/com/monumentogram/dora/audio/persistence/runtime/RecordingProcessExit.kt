package com.monumentogram.dora.audio.persistence.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** Package-level diagnostic only. It cannot establish which recording was interrupted. */
data class RecordingProcessExit(val reason: Int, val status: Int, val timestampMillis: Long) {
    companion object {
        internal fun read(context: Context): RecordingProcessExit? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            return try {
                context
                    .getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessExitReasons(context.packageName, 0, 1)
                    .firstOrNull()
                    ?.let { RecordingProcessExit(it.reason, it.status, it.timestamp) }
            } catch (_: Exception) {
                null // Explanatory data never gates authenticated recovery or implies data loss.
            }
        }
    }
}
