package com.monumentogram.dora.audio.persistence.runtime

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.ProductAudioRuntime
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.persistence.EncryptedAudioVault
import com.monumentogram.dora.audio.persistence.auth.AndroidAppLock
import com.monumentogram.dora.audio.persistence.auth.AppLockState
import com.monumentogram.dora.audio.persistence.auth.AppLockedException
import com.monumentogram.dora.audio.persistence.auth.UnlockResult

/**
 * Install once in Application.onCreate, before any Activity resumes. No test delegate is exposed.
 */
class AndroidProductAudioRuntime(application: Application) : ProductAudioRuntime {
    private val main = Handler(Looper.getMainLooper())
    private val coordinator =
        AudioRuntimeCoordinator({ Looper.myLooper() == Looper.getMainLooper() }) {
            mode,
            authorization ->
            EncryptedAudioVault.open(
                application,
                mode == AudioOpenMode.CREATE_NEW,
                authorization::requireActive,
                authorization::withPlaintextDelivery,
            )
        }
    @Volatile private var denied: AudioFailure? = null
    @Volatile private var pending: PendingDeletion? = null
    private val appLock =
        AndroidAppLock(application) {
            denied = null
            coordinator.revoke()
            val old = pending
            old?.confirmation?.cancel()
            main.post { if (pending === old) cancelPending(AudioFailure.LOCKED) }
        }

    override val availability: AudioAvailability
        get() {
            if (
                appLock.state == AppLockState.LOCKED &&
                    coordinator.availability is AudioAvailability.Available
            )
                coordinator.revoke()
            return denied?.let(AudioAvailability::Failed) ?: coordinator.availability
        }

    /** Launches the system prompt on main; vault creation/opening runs only after real success. */
    fun requestOpen(
        activity: Activity,
        mode: AudioOpenMode,
        completion: (AudioAvailability) -> Unit,
    ) {
        requireMain()
        secure(activity)
        appLock.requestUnlock(activity) { result ->
            if (result != UnlockResult.UNLOCKED) {
                val failure =
                    when (result) {
                        UnlockResult.CANCELLED -> AudioFailure.CANCELLED
                        UnlockResult.CREDENTIAL_SETUP_REQUIRED ->
                            AudioFailure.CREDENTIAL_SETUP_REQUIRED
                        else -> AudioFailure.UNAVAILABLE
                    }
                denied = failure
                completion(AudioAvailability.Failed(failure))
            } else {
                coordinator.openAuthenticated(mode, appLock::captureAuthorization) { outcome ->
                    main.post {
                        val checked =
                            if (outcome is AudioAvailability.Available) {
                                try {
                                    coordinator.requireCurrent(outcome.session)
                                    outcome
                                } catch (_: AppLockedException) {
                                    AudioAvailability.Locked
                                }
                            } else outcome
                        completion(checked)
                    }
                }
            }
        }
    }

    fun lock() = appLock.lock()

    fun openCredentialSetup(activity: Activity) {
        requireMain()
        appLock.openCredentialSetup(activity)
    }

    /**
     * Only this private one-use, generation/asset-bound confirmation can start an audio deletion.
     */
    fun requestAudioDeletion(
        activity: Activity,
        session: ProductAudioSession,
        identity: AudioIdentity,
        completion: (AudioResult<Unit>) -> Unit,
    ) {
        requireMain()
        try {
            coordinator.requireCurrent(session)
        } catch (_: AppLockedException) {
            completion(AudioResult.Failed(AudioFailure.LOCKED))
            return
        }
        cancelPending(AudioFailure.CANCELLED)
        secure(activity)
        val confirmation =
            AudioDeletionConfirmation(identity) { coordinator.requireCurrent(session) }
        val dialog =
            AlertDialog.Builder(activity)
                .setTitle("Delete audio")
                .setMessage(
                    AUDIO_DELETE_COPY +
                        "\n\nExternal exported copies will not be deleted. Cloud deletion is separate. " +
                        "Closing this dialog after confirmation does not cancel the accepted deletion."
                )
                .setNegativeButton("Cancel") { _, _ -> cancelPending(AudioFailure.CANCELLED) }
                .setPositiveButton("Delete audio") { _, _ ->
                    val selected = pending
                    if (
                        selected?.confirmation !== confirmation || !confirmation.consume(identity)
                    ) {
                        cancelPending(AudioFailure.LOCKED)
                    } else {
                        pending = null // UI dismissal cannot cancel an accepted operation.
                        coordinator.confirmedDelete(session, identity) { outcome ->
                            main.post {
                                val checked =
                                    try {
                                        coordinator.requireCurrent(session)
                                        outcome
                                    } catch (_: AppLockedException) {
                                        AudioResult.Failed(AudioFailure.LOCKED)
                                    }
                                completion(checked)
                            }
                        }
                    }
                }
                .create()
        pending = PendingDeletion(confirmation, dialog, completion)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setOnCancelListener {
            if (pending?.confirmation === confirmation) cancelPending(AudioFailure.CANCELLED)
        }
        dialog.setOnDismissListener {
            if (pending?.confirmation === confirmation) cancelPending(AudioFailure.CANCELLED)
        }
        dialog.show()
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.apply {
            isFocusableInTouchMode = true
            requestFocus()
        }
    }

    private fun cancelPending(reason: AudioFailure) {
        val old = pending ?: return
        pending = null
        old.confirmation.cancel()
        old.dialog.dismiss()
        old.completion(AudioResult.Failed(reason))
    }

    @Suppress("DEPRECATION")
    private fun secure(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        activity.setTaskDescription(ActivityManager.TaskDescription("DORA"))
    }

    private fun requireMain() = check(Looper.myLooper() == Looper.getMainLooper())

    private class PendingDeletion(
        val confirmation: AudioDeletionConfirmation,
        val dialog: AlertDialog,
        val completion: (AudioResult<Unit>) -> Unit,
    )

    private companion object {
        const val AUDIO_DELETE_COPY =
            "Delete only this conversation’s audio? The transcript, protocol, decisions, tasks, summary, " +
                "and search entries will remain, but Dora will no longer be able to open or re-check them " +
                "against the recording. Deletion may complete only partially; " +
                "physical storage overwrite is not guaranteed."
    }
}
