package com.monumentogram.dora.recording

/** Admission estimate; the finalization headroom is not filesystem preallocation. */
object RecordingStorageBudget {
    const val FINALIZATION_RESERVE_BYTES = 16_777_216L
    // API36 encrypted filesystem measured 236 KiB allocated per five-second 160,000-byte
    // unit, including directories and key/manifest artifacts. Allow 200 MB/hour for those
    // allocations plus catalog overhead; PCM byte length alone substantially underestimates it.
    const val HOUR_ALLOWANCE_BYTES = 200_000_000L
    private const val HOURS_PER_DAY = 24L
    private const val SECONDS_PER_HOUR = 3600L

    data class Snapshot(val availableBytes: Long?) {
        val requiredBytes = HOUR_ALLOWANCE_BYTES + FINALIZATION_RESERVE_BYTES
        val recordingBudgetBytes =
            ((availableBytes ?: 0L) - FINALIZATION_RESERVE_BYTES).coerceAtLeast(0L)
        val canStart = availableBytes != null && availableBytes >= requiredBytes
        // Conservative disk allowance, including encrypted unit/catalog overhead; not a
        // reservation.
        val fullDayRequiredBytes = HOUR_ALLOWANCE_BYTES * HOURS_PER_DAY + FINALIZATION_RESERVE_BYTES
        val canFitFullDay = availableBytes != null && availableBytes >= fullDayRequiredBytes
        val estimatedSeconds =
            minOf(recordingBudgetBytes, HOUR_ALLOWANCE_BYTES * HOURS_PER_DAY) * SECONDS_PER_HOUR /
                HOUR_ALLOWANCE_BYTES
    }

    fun assess(availableBytes: Long?): Snapshot = Snapshot(availableBytes?.takeIf { it >= 0L })
}
