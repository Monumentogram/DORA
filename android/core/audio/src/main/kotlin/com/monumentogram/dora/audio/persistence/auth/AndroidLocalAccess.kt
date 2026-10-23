package com.monumentogram.dora.audio.persistence.auth

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager

/** Current Android user/app sandbox and foreground lifetime, without credential authentication. */
internal class AndroidLocalAccess(application: Application, private val onRevoked: () -> Unit) {
    private val keyguard = application.getSystemService(KeyguardManager::class.java)
    private val power = application.getSystemService(PowerManager::class.java)
    private val session = LocalAccessSession { power.isInteractive && !keyguard.isDeviceLocked }
    private var resumed: Activity? = null

    val isForeground: Boolean
        get() = session.isForeground

    fun capture(activity: Activity): LocalAccessSession.Authorization {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (resumed !== activity || activity.isFinishing || activity.isDestroyed)
            throw LocalAccessDeniedException()
        // A fresh explicit action in this exact resumed Activity may acquire the
        // current generation after revocation. It never revives an issued handle.
        session.resume()
        return session.capture()
    }

    fun revoke() {
        session.revoke()
        // Never call downstream listeners while holding the session monitor.
        onRevoked()
    }

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    if (resumed !== activity) revoke()
                    resumed = activity
                    session.resume()
                }

                override fun onActivityPaused(activity: Activity) {
                    if (resumed === activity) {
                        resumed = null
                        revoke()
                    }
                }

                override fun onActivityDestroyed(activity: Activity) {
                    if (resumed === activity) {
                        resumed = null
                        revoke()
                    }
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            }
        )
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) = revoke()
            }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else application.registerReceiver(receiver, filter)
    }
}
