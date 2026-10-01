package com.monumentogram.dora

import android.app.Application
import com.monumentogram.dora.audio.persistence.runtime.AndroidProductAudioRuntime

/** Installs the real local persistence coordinator before any Activity resumes. */
class DoraApplication : Application() {
    lateinit var audioRuntime: AndroidProductAudioRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        audioRuntime = AndroidProductAudioRuntime(this)
    }
}
