package com.monumentogram.dora.vad.sherpa

import android.content.Context
import com.monumentogram.dora.vad.SegmentationProfile
import com.monumentogram.dora.vad.VadEngine
import com.monumentogram.dora.vad.VadEngineFactory
import com.monumentogram.dora.vad.VadException
import com.monumentogram.dora.vad.VadFailure
import java.io.File
import java.security.MessageDigest

/** Production factory. Missing private runtime is explicit; there is no fake fallback. */
class SherpaEngineFactory(context: Context) : VadEngineFactory {
    private val application = context.applicationContext

    override fun create(profile: SegmentationProfile): VadEngine {
        try {
            Class.forName("com.k2fsa.sherpa.onnx.Vad")
        } catch (_: ClassNotFoundException) {
            throw VadException(VadFailure.RUNTIME_UNAVAILABLE)
        }
        val model = verifiedModel(profile)
        return try {
            SherpaVadEngine(ReflectiveSherpaBinding(model, profile), profile)
        } catch (failure: VadException) {
            throw failure
        } catch (_: Exception) {
            throw VadException(VadFailure.INITIALIZATION_FAILED)
        } catch (_: LinkageError) {
            throw VadException(VadFailure.RUNTIME_UNAVAILABLE)
        }
    }

    @Suppress(
        "ThrowsCount",
        "NestedBlockDepth",
    ) // Preserve typed integrity/unavailable failures across nested owned streams.
    private fun verifiedModel(profile: SegmentationProfile): File {
        val target = File(application.noBackupFilesDir, "silero-${profile.modelSha256}.onnx")
        try {
            if (!target.exists()) {
                application.assets.open("dora-vad/silero.onnx").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (target.length() != MODEL_BYTES || digest(target) != profile.modelSha256) {
                target.delete()
                throw VadException(VadFailure.MODEL_INVALID)
            }
            return target
        } catch (failure: VadException) {
            throw failure
        } catch (_: Exception) {
            throw VadException(VadFailure.MODEL_UNAVAILABLE)
        }
    }

    @Suppress("NestedBlockDepth") // Bounded read loop with guaranteed buffer clearing.
    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            try {
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
            } finally {
                buffer.fill(0)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MODEL_BYTES = 2_327_524L
    }
}
