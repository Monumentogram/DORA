package com.monumentogram.dora.audio.persistence.auth

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalAccessTestHostActivity : Activity()

class AndroidLocalAccessTest {
    @Test
    fun exactResumedActivityRequiredWithoutCredentialPrompt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var local: AndroidLocalAccess
        instrumentation.runOnMainSync {
            local = AndroidLocalAccess(context.applicationContext as Application) {}
        }
        val host =
            instrumentation.startActivitySync(
                Intent(context, LocalAccessTestHostActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        try {
            instrumentation.runOnMainSync {
                local.capture(host).requireActive()
                assertThrows(LocalAccessDeniedException::class.java) { local.capture(Activity()) }
                val old = local.capture(host)
                local.revoke()
                assertThrows(LocalAccessDeniedException::class.java) { old.requireActive() }
                local.capture(host).requireActive()
                assertThrows(LocalAccessDeniedException::class.java) { old.requireActive() }
            }
        } finally {
            instrumentation.runOnMainSync { host.finish() }
        }
    }
}
