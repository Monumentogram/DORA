package com.monumentogram.dora

import android.app.Application
import com.monumentogram.dora.audio.persistence.runtime.AndroidProductAudioRuntime
import com.monumentogram.dora.recording.RecordingController
import com.monumentogram.dora.vad.sherpa.SherpaEngineFactory

/** Installs the real local persistence coordinator before any Activity resumes. */
class DoraApplication : Application() {
    lateinit var audioRuntime: AndroidProductAudioRuntime
        private set

    lateinit var recording: RecordingController
        private set

    override fun onCreate() {
        super.onCreate()
        audioRuntime = AndroidProductAudioRuntime(this)
        recording = RecordingController(this, audioRuntime, SherpaEngineFactory(this))
    }
}
