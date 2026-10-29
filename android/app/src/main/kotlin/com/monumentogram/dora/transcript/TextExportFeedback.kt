package com.monumentogram.dora.transcript

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.monumentogram.dora.R

@Composable
internal fun TextExportFeedback(status: TextExportStatus) {
    val resource =
        when (status) {
            TextExportStatus.IDLE -> return
            TextExportStatus.SELECTING -> R.string.stage11_export_selecting
            TextExportStatus.WRITING -> R.string.stage11_export_writing
            TextExportStatus.SAVED -> R.string.stage11_export_saved
            TextExportStatus.CANCELLED -> R.string.stage11_export_cancelled
            TextExportStatus.FAILED -> R.string.stage11_export_failed
        }
    Text(stringResource(resource))
}
