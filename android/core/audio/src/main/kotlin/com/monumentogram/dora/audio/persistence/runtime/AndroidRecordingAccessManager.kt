@file:Suppress(
    "ThrowsCount"
) // Acquisition failures preserve typed outcomes and retire partial ownership.

package com.monumentogram.dora.audio.persistence.runtime

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.os.Handler
import android.os.Looper
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.persistence.EncryptedAudioVault
import com.monumentogram.dora.audio.persistence.auth.AndroidAppLock
import com.monumentogram.dora.audio.persistence.auth.AppLockedException
import com.monumentogram.dora.audio.persistence.auth.UnlockResult
import com.monumentogram.dora.audio.recording.RecordingAccess
import com.monumentogram.dora.audio.recording.RecordingAuthority
import com.monumentogram.dora.audio.recording.RecordingRecovery
import java.util.concurrent.Executors

/** Same vault after exclusive retirement; no parallel audio store or retained reader authority. */
internal class AndroidRecordingAccessManager(
    private val application: Application,
    private val appLock: AndroidAppLock,
    private val coordinator: AudioRuntimeCoordinator,
) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Dora recording vault")
    }
    private val retiring = ResourceRetirement()
    @Volatile
    var inUse = false
        private set

    @Volatile private var active: RecordingAccess? = null
    @Volatile private var epoch = 0L
    @Volatile private var currentAuthority: RecordingAuthority? = null
    @Volatile private var opening = false

    fun request(
        activity: Activity,
        mode: AudioOpenMode,
        identity: AudioIdentity,
        resumeExisting: Boolean = false,
        completion: (AudioResult<RecordingAccess>) -> Unit,
    ) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (inUse) {
            if (active == null && !opening)
                worker.execute {
                    retiring.retry()
                    main.post { if (active == null && !opening && retiring.isEmpty) inUse = false }
                }
            completion(AudioResult.Failed(AudioFailure.BUSY))
            return
        }
        inUse = true
        opening = true
        val requestEpoch = ++epoch
        appLock.requestUnlock(activity) { result ->
            if (result != UnlockResult.UNLOCKED) {
                opening = false
                inUse = false
                completion(
                    AudioResult.Failed(
                        when (result) {
                            UnlockResult.CANCELLED -> AudioFailure.CANCELLED
                            UnlockResult.CREDENTIAL_SETUP_REQUIRED ->
                                AudioFailure.CREDENTIAL_SETUP_REQUIRED
                            else -> AudioFailure.UNAVAILABLE
                        }
                    )
                )
            } else afterAuthentication(mode, identity, resumeExisting, requestEpoch, completion)
        }
    }

    private fun afterAuthentication(
        mode: AudioOpenMode,
        identity: AudioIdentity,
        resumeExisting: Boolean,
        requestEpoch: Long,
        completion: (AudioResult<RecordingAccess>) -> Unit,
    ) {
        val foreground =
            try {
                appLock.captureAuthorization()
            } catch (_: AppLockedException) {
                opening = false
                inUse = false
                completion(AudioResult.Failed(AudioFailure.LOCKED))
                return
            }
        val keyguard = application.getSystemService(KeyguardManager::class.java)
        val authority =
            RecordingAuthority(
                foreground::requireActive,
                { keyguard.isDeviceSecure && epoch == requestEpoch },
                foreground::withPlaintextDelivery,
            )
        currentAuthority = authority
        coordinator.retireForRecording { clean ->
            worker.execute {
                val opened =
                    if (clean) open(mode, identity, authority, resumeExisting)
                    else AudioResult.Failed(AudioFailure.BUSY)
                main.post {
                    opening = false
                    val checked =
                        try {
                            authority.requireForeground()
                            opened
                        } catch (_: AppLockedException) {
                            AudioResult.Failed(AudioFailure.LOCKED)
                        }
                    if (checked is AudioResult.Value) active = checked.value
                    else {
                        authority.revoke()
                        if (opened is AudioResult.Value) opened.value.close()
                        else inUse = !retiring.isEmpty
                    }
                    completion(checked)
                }
            }
        }
    }

    private fun open(
        mode: AudioOpenMode,
        identity: AudioIdentity,
        authority: RecordingAuthority,
        resumeExisting: Boolean,
    ): AudioResult<RecordingAccess> {
        var candidate: EncryptedAudioVault? = null
        var transferred = false
        return try {
            retiring.retry()
            authority.requireForeground()
            if (!retiring.isEmpty) throw RecordingOpenException(AudioFailure.BUSY)
            val result =
                EncryptedAudioVault.open(
                    application,
                    mode == AudioOpenMode.CREATE_NEW,
                    authority::requireWriter,
                    { throw AppLockedException() },
                )
            if (result is AudioResult.Failed) throw RecordingOpenException(result.reason)
            val vault = (result as AudioResult.Value).value
            candidate = vault
            val continuation = if (resumeExisting) continuation(vault, identity) else null
            authority.requireForeground()
            val access =
                RecordingAccess(
                    identity,
                    authority,
                    vault,
                    { Looper.myLooper() == Looper.getMainLooper() },
                    continuation,
                ) { owned ->
                    worker.execute {
                        retiring.retire(owned)
                        active = null
                        inUse = !retiring.isEmpty
                    }
                }
            transferred = true
            AudioResult.Value(access)
        } catch (error: RecordingOpenException) {
            AudioResult.Failed(error.reason)
        } catch (_: AppLockedException) {
            AudioResult.Failed(AudioFailure.LOCKED)
        } catch (_: Exception) {
            AudioResult.Failed(AudioFailure.UNAVAILABLE)
        } finally {
            if (!transferred) candidate?.let(retiring::retire)
        }
    }

    private fun continuation(
        vault: EncryptedAudioVault,
        identity: AudioIdentity,
    ): RecordingRecovery {
        return when (val result = vault.recordingRecovery(identity)) {
            is AudioResult.Failed -> throw RecordingOpenException(result.reason)
            is AudioResult.Value ->
                result.value.also {
                    if (!it.canResume || it.summary == null)
                        throw RecordingOpenException(it.failure ?: AudioFailure.INCOMPLETE)
                }
        }
    }

    fun resumeGrant(
        expected: RecordingAccess,
        boundary: (() -> Unit) -> Unit,
    ): com.monumentogram.dora.audio.recording.RecordingResumeGrant {
        val issuedEpoch = epoch
        if (active !== expected) throw AppLockedException()
        return expected.resumeGrant { start ->
            boundary {
                if (active !== expected || epoch != issuedEpoch) throw AppLockedException()
                start()
            }
        }
    }

    fun revoke() {
        currentAuthority?.revoke()
        epoch++
        active?.revoke()
    }

    private class RecordingOpenException(val reason: AudioFailure) :
        IllegalStateException(reason.name)
}
