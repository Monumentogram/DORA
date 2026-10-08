package com.monumentogram.dora.recording

/** Admission estimate; the finalization headroom is not filesystem preallocation. */
object RecordingStorageBudget {
    const val FINALIZATION_RESERVE_BYTES = 16_777_216L
    const val HOUR_ALLOWANCE_BYTES = 125_000_000L

    data class Snapshot(val availableBytes: Long?) {
        val requiredBytes = HOUR_ALLOWANCE_BYTES + FINALIZATION_RESERVE_BYTES
        val recordingBudgetBytes =
            ((availableBytes ?: 0L) - FINALIZATION_RESERVE_BYTES).coerceAtLeast(0L)
        val canStart = availableBytes != null && availableBytes >= requiredBytes
    }

    fun assess(availableBytes: Long?): Snapshot = Snapshot(availableBytes?.takeIf { it >= 0L })
}
