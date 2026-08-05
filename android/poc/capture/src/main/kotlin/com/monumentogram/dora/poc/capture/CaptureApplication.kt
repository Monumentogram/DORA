package com.monumentogram.dora.poc.capture

import android.app.Application
import com.monumentogram.dora.poc.capture.runtime.CaptureController

class CaptureApplication : Application() {
    val controller: CaptureController by lazy { CaptureController(this) }

    override fun onCreate() {
        super.onCreate()
        controller.initialize()
    }
}
