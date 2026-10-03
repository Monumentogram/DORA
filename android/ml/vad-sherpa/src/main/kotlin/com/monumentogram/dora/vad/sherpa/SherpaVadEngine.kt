package com.monumentogram.dora.vad.sherpa

import com.monumentogram.dora.vad.SegmentationProfile
import com.monumentogram.dora.vad.VadEngine
import com.monumentogram.dora.vad.VadException
import com.monumentogram.dora.vad.VadFailure

internal interface SherpaBinding : AutoCloseable {
    fun compute(input: FloatArray): Float

    fun reset()

    override fun close()
}

/** Worker-confined. Silero v6.2.1 raw compute tensor: 64 context + 512 new samples. */
internal class SherpaVadEngine(
    private val binding: SherpaBinding,
    private val profile: SegmentationProfile,
) : VadEngine {
    private val tensor = FloatArray(profile.contextFrames + profile.windowFrames)
    private var closed = false

    @Suppress("ComplexCondition") // Validate every sample before JNI, including NaN and infinities.
    override fun probability(samples: FloatArray): Float {
        if (
            closed ||
                samples.size != profile.windowFrames ||
                samples.any { !it.isFinite() || it < -1f || it > 1f }
        )
            throw VadException(VadFailure.INVALID_FRAME)
        samples.copyInto(tensor, profile.contextFrames)
        return try {
            binding.compute(tensor).also {
                tensor.copyInto(tensor, 0, profile.windowFrames, tensor.size)
            }
        } catch (_: Exception) {
            tensor.fill(0f)
            throw VadException(VadFailure.INFERENCE_FAILED)
        } finally {
            tensor.fill(0f, profile.contextFrames, tensor.size)
        }
    }

    override fun reset() {
        tensor.fill(0f)
        if (closed) throw VadException(VadFailure.RESET_FAILED)
        try {
            binding.reset()
        } catch (_: Exception) {
            throw VadException(VadFailure.RESET_FAILED)
        }
    }

    override fun close() {
        tensor.fill(0f)
        if (closed) return
        closed = true
        binding.close()
    }
}
