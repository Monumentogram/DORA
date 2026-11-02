package com.monumentogram.dora.audio.persistence.journal

/** Scalar SQL results only: no historic unit list or PCM is materialized for library browsing. */
internal data class LibraryClaimStats(
    val unitCount: Long,
    val committedCount: Long,
    val committedFrames: Long,
    val reservedFrames: Long,
    val firstOrdinal: Long?,
    val lastOrdinal: Long?,
)

internal data class LibraryFinalizationStats(
    val unitCount: Long,
    val frames: Long,
    val invalid: Long,
)

internal class CorruptRecordingLibraryMetadata : IllegalStateException("Invalid library metadata")
