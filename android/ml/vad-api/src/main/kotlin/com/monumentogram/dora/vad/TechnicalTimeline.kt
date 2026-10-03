package com.monumentogram.dora.vad

data class TechnicalSlice(
    val range: FrameRange,
    val technicalId: String,
    val technicalFirstFrame: Long,
    val captureEpoch: String,
    val overlapBefore: FrameRange?,
)

/** Independent of inference. Exact source splitting, including a cap inside a native read. */
class TechnicalTimeline(
    private val profile: SegmentationProfile,
    firstFrame: Long,
    private var captureEpoch: String,
    private val nextId: (Long) -> String,
) {
    private var expected = firstFrame
    private var epochStart = firstFrame
    private var technicalStart = firstFrame
    private var technicalId = captureEpoch
    private var ordinal = 0L

    fun accept(range: FrameRange, epoch: String): List<TechnicalSlice> {
        require(range.first == expected && epoch == captureEpoch)
        val result = mutableListOf<TechnicalSlice>()
        var first = range.first
        while (first < range.end) {
            if (first - technicalStart == profile.technicalCapFrames) {
                technicalStart = first
                technicalId = nextId(++ordinal)
            }
            val count =
                minOf(range.end - first, profile.technicalCapFrames - (first - technicalStart))
            val overlap =
                if (technicalStart == epochStart) null
                else
                    FrameRange(
                        maxOf(epochStart, technicalStart - profile.overlapFrames),
                        technicalStart,
                    )
            result.add(
                TechnicalSlice(
                    FrameRange(first, first + count),
                    technicalId,
                    technicalStart,
                    captureEpoch,
                    overlap,
                )
            )
            first += count
        }
        expected = range.end
        return result
    }

    fun resume(atFrame: Long, epoch: String) {
        require(atFrame == expected && epoch != captureEpoch)
        captureEpoch = epoch
        epochStart = atFrame
        technicalStart = atFrame
        technicalId = epoch
    }
}
