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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.lang.ref.WeakReference
import java.util.UUID

internal enum class AppLockState {
    LOCKED,
    UNLOCKED,
}

internal enum class UnlockResult {
    UNLOCKED,
    CANCELLED,
    UNAVAILABLE,
    CREDENTIAL_SETUP_REQUIRED,
}

/** Application-owned coordinator. Construct before the first Activity resumes. */
internal class AndroidAppLock(
    private val application: Application,
    private val onLocked: () -> Unit = {},
) {
    private val keyguard = application.getSystemService(KeyguardManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val session =
        AppLockSession(android.os.SystemClock::elapsedRealtime) {
            keyguard.isDeviceSecure && !keyguard.isDeviceLocked
        }
    private var resumed: Activity? = null
    private var pending: Pending? = null

    val state: AppLockState
        get() = if (session.isUnlocked) AppLockState.UNLOCKED else AppLockState.LOCKED

    fun requireAuthorized() = session.capture().requireActive()

    fun captureAuthorization(): AppLockSession.Authorization = session.capture()

    fun lock() {
        session.lock() // Revoke synchronously, including when called from a worker.
        if (Looper.myLooper() == Looper.getMainLooper()) finishPending(UnlockResult.CANCELLED)
        else main.post { finishPending(UnlockResult.CANCELLED) }
        onLocked()
    }

    fun requestUnlock(activity: Activity, completion: (UnlockResult) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        lock()
        val denied = denialFor(activity)
        if (denied != null) {
            completion(denied)
            return
        }
        val proof =
            try {
                AndroidAuthProof.create()
            } catch (_: Exception) {
                completion(UnlockResult.UNAVAILABLE)
                return
            }
        val request =
            Pending(
                UUID.randomUUID().toString(),
                session.begin(),
                WeakReference(activity),
                proof,
                completion,
            )
        pending = request
        AppLockPromptRegistry.put(
            request.id,
            AppLockPromptRequest(proof) { result ->
                // Called only by the private, process-local route claimed by the system-prompt
                // Activity.
                if (pending !== request || request.returned != null) return@AppLockPromptRequest
                request.returned =
                    if (result == UnlockResult.UNLOCKED && !session.recordResult(request.attempt)) {
                        UnlockResult.CANCELLED
                    } else result
                if (resumed === request.caller.get()) finishReturned(request)
            },
        )
        try {
            activity.startActivity(
                Intent(activity, AppLockAuthenticationActivity::class.java)
                    .putExtra("attempt", request.id)
            )
        } catch (_: Exception) {
            finishPending(UnlockResult.UNAVAILABLE)
        }
    }

    /** Setup never grants authority; returning requires a separate requestUnlock prompt. */
    fun openCredentialSetup(activity: Activity) {
        lock()
        activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }

    private fun denialFor(activity: Activity): UnlockResult? =
        when {
            resumed !== activity || activity.isFinishing || activity.isDestroyed ->
                UnlockResult.CANCELLED
            !keyguard.isDeviceSecure -> UnlockResult.CREDENTIAL_SETUP_REQUIRED
            keyguard.isDeviceLocked -> UnlockResult.UNAVAILABLE
            else -> null
        }

    private fun finishReturned(request: Pending) {
        val result = request.returned ?: return
        if (pending !== request) return
        val authorized =
            result == UnlockResult.UNLOCKED &&
                session.complete(request.attempt) {
                    request.proof.verifyRecentAuthentication()
                }
        finishPending(
            if (authorized) UnlockResult.UNLOCKED
            else if (result == UnlockResult.UNLOCKED) UnlockResult.UNAVAILABLE else result
        )
    }

    private fun finishPending(result: UnlockResult) {
        val request = pending ?: return
        pending = null
        AppLockPromptRegistry.remove(request.id)
        session.cancel(request.attempt)
        request.proof.close()
        request.completion(result)
    }

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    resumed = activity
                    session.resume()
                    if (!keyguard.isDeviceSecure || keyguard.isDeviceLocked) {
                        lock()
                        return
                    }
                    pending?.let { if (it.caller.get() === activity) finishReturned(it) }
                }

                override fun onActivityPaused(activity: Activity) {
                    if (resumed === activity) resumed = null
                    session.pause()
                    onLocked()
                }

                override fun onActivityStopped(activity: Activity) {
                    // onPause revokes before any stop/cleanup; no ProcessLifecycleOwner delay.
                    if (resumed == null) {
                        session.pause()
                        onLocked()
                    }
                }

                override fun onActivityDestroyed(activity: Activity) {
                    if (pending?.caller?.get() === activity) lock()
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            }
        )
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    lock()
                }
            }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else application.registerReceiver(receiver, filter)
    }

    private class Pending(
        val id: String,
        val attempt: AppLockSession.Attempt,
        val caller: WeakReference<Activity>,
        val proof: AndroidAuthProof,
        val completion: (UnlockResult) -> Unit,
        var returned: UnlockResult? = null,
    )
}

/** No route survives process recreation. Identifiers alone are never authorizing. */
internal object AppLockPromptRegistry {
    private val requests = mutableMapOf<String, AppLockPromptRequest>()

    @Synchronized
    fun put(id: String, request: AppLockPromptRequest) {
        requests[id] = request
    }

    @Synchronized fun take(id: String): AppLockPromptRequest? = requests.remove(id)

    @Synchronized
    fun remove(id: String) {
        requests.remove(id)
    }
}

internal class AppLockPromptRequest(
    val proof: AndroidAuthProof,
    private val completion: (UnlockResult) -> Unit,
) {
    private var consumed = false

    fun returned(result: UnlockResult) {
        if (consumed) return
        consumed = true
        completion(result)
    }
}
