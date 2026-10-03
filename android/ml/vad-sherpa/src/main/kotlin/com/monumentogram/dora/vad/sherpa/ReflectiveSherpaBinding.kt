package com.monumentogram.dora.vad.sherpa

import com.monumentogram.dora.vad.SegmentationProfile
import com.monumentogram.dora.vad.VadException
import com.monumentogram.dora.vad.VadFailure
import java.io.File

/** Exact admitted Java API. Reflection keeps credential-free CI independent of private binaries. */
internal class ReflectiveSherpaBinding(model: File, profile: SegmentationProfile) : SherpaBinding {
    private val vadClass = type("Vad")
    private val compute = vadClass.getMethod("compute", FloatArray::class.java)
    private val reset = vadClass.getMethod("reset")
    private val release = vadClass.getMethod("release")
    private val instance: Any

    init {
        type("LibraryLoader")
            .getMethod("setAutoLoadEnabled", Boolean::class.javaPrimitiveType)
            .invoke(null, false)
        System.loadLibrary("onnxruntime")
        System.loadLibrary("sherpa-onnx-jni")
        val version = type("VersionInfo").getMethod("getVersion").invoke(null)
        if (version != profile.engineVersion) throw VadException(VadFailure.INITIALIZATION_FAILED)
        val silero = checkNotNull(type("SileroVadModelConfig").getMethod("builder").invoke(null))
        set(silero, "setModel", String::class.java, model.absolutePath)
        set(silero, "setWindowSize", Integer.TYPE, profile.windowFrames)
        set(silero, "setThreshold", java.lang.Float.TYPE, profile.probabilityThreshold)
        set(silero, "setMinSpeechDuration", java.lang.Float.TYPE, profile.builderMinSpeechSeconds)
        set(silero, "setMinSilenceDuration", java.lang.Float.TYPE, profile.builderMinSilenceSeconds)
        set(silero, "setMaxSpeechDuration", java.lang.Float.TYPE, profile.builderMaxSpeechSeconds)
        val config = checkNotNull(type("VadModelConfig").getMethod("builder").invoke(null))
        set(config, "setSileroVadModelConfig", type("SileroVadModelConfig"), build(silero))
        set(config, "setSampleRate", Integer.TYPE, profile.sampleRate)
        set(config, "setNumThreads", Integer.TYPE, profile.numThreads)
        set(config, "setProvider", String::class.java, profile.provider)
        set(config, "setDebug", java.lang.Boolean.TYPE, profile.debug)
        instance = vadClass.getConstructor(type("VadModelConfig")).newInstance(build(config))
    }

    override fun compute(input: FloatArray): Float = compute.invoke(instance, input) as Float

    override fun reset() {
        reset.invoke(instance)
    }

    override fun close() {
        release.invoke(instance)
    }

    private fun set(builder: Any, method: String, argumentType: Class<*>, value: Any) {
        builder.javaClass.getMethod(method, argumentType).invoke(builder, value)
    }

    private fun build(builder: Any): Any =
        checkNotNull(builder.javaClass.getMethod("build").invoke(builder))

    private fun type(name: String): Class<*> = Class.forName("com.k2fsa.sherpa.onnx.$name")
}
