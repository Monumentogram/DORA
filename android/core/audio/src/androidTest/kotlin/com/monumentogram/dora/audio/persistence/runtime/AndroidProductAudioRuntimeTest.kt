package com.monumentogram.dora.audio.persistence.runtime

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioCompletion
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.persistence.auth.AppLockTestHostActivity
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

@Suppress("DEPRECATION")
class AndroidProductAudioRuntimeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    @Suppress(
        "LongMethod"
    ) // Keep the real credential and persistence lifecycle visible as one scenario.
    fun credentialVaultRoundTripRelockReopenConfirmedDeletion() {
        assumeTrue(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val pin = checkNotNull(InstrumentationRegistry.getArguments().getString("syntheticAuthPin"))
        val automation = instrumentation.uiAutomation
        val previousServiceInfo = automation.serviceInfo
        lateinit var runtime: AndroidProductAudioRuntime
        instrumentation.runOnMainSync {
            runtime = AndroidProductAudioRuntime(context.applicationContext as Application)
        }
        val host =
            instrumentation.startActivitySync(
                Intent(context, AppLockTestHostActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        try {
            automation.serviceInfo =
                automation.serviceInfo.apply {
                    flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                }
            val first = unlock(runtime, host, AudioOpenMode.CREATE_NEW, pin)
            val actualKey =
                com.monumentogram.dora.audio.persistence.keys
                    .VaultSecretStore(
                        com.monumentogram.dora.audio.persistence.keys.AndroidVaultBundleStorage(
                            context
                        ),
                        com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeyBackend(
                            context
                        ),
                    )
                    .openExisting()
                    as com.monumentogram.dora.audio.persistence.keys.KeyAccess.Available
            assertEquals(actualKey.protection.name, first.protection.name)
            assertTrue(first.protectionDisclosure.startsWith("Vault wrapping key protection: "))
            val audio = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())
            val pcm = ByteArray(320) { (it * 7).toByte() }
            assertEquals(AudioResult.Value(Unit), first.writer.create(audio))
            assertEquals(
                AudioResult.Value(Unit),
                first.writer.append(
                    AudioStorageUnitIdentity(audio, id(), 0, 0),
                    AudioFormat.PCM,
                    pcm,
                ),
            )
            assertEquals(AudioResult.Value(Unit), first.writer.finalize(audio))
            var borrowed: ByteArray? = null
            val read =
                first.reader.extract(audio) { _, bytes ->
                    assertArrayEquals(pcm, bytes)
                    borrowed = bytes
                    assertEquals(AudioResult.Failed(AudioFailure.BUSY), first.sourceState(audio))
                }
            assertEquals(AudioCompletion.FINALIZED, (read as AudioResult.Value).value.completion)
            assertTrue(borrowed!!.all { it == 0.toByte() })
            instrumentation.runOnMainSync { runtime.lock() }
            assertEquals(AudioAvailability.Locked, runtime.availability)
            assertEquals(AudioResult.Failed(AudioFailure.LOCKED), first.writer.create(audio))
            val second = unlock(runtime, host, AudioOpenMode.OPEN_EXISTING, pin)
            assertEquals(first.protection, second.protection)
            assertEquals(AudioResult.Failed(AudioFailure.LOCKED), first.sourceState(audio))
            assertEquals(
                AudioResult.Failed(AudioFailure.INVALID_INPUT),
                second.retryRemainingDeletion(audio),
            )
            delete(runtime, host, second, audio, "Cancel")
            assertTrue(
                (second.sourceState(audio) as AudioResult.Value).value is AudioSourceState.Readable
            )
            delete(runtime, host, second, audio, "Delete audio")
            assertEquals(AudioResult.Value(AudioSourceState.UserDeleted), second.sourceState(audio))
            instrumentation.runOnMainSync { runtime.lock() }
            val third = unlock(runtime, host, AudioOpenMode.OPEN_EXISTING, pin)
            assertEquals(AudioResult.Value(AudioSourceState.UserDeleted), third.sourceState(audio))
            val paused = CountDownLatch(1)
            val lifecycle =
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityPaused(activity: android.app.Activity) {
                        if (activity === host) paused.countDown()
                    }

                    override fun onActivityCreated(activity: android.app.Activity, state: Bundle?) =
                        Unit

                    override fun onActivityStarted(activity: android.app.Activity) = Unit

                    override fun onActivityResumed(activity: android.app.Activity) = Unit

                    override fun onActivityStopped(activity: android.app.Activity) = Unit

                    override fun onActivitySaveInstanceState(
                        activity: android.app.Activity,
                        state: Bundle,
                    ) = Unit

                    override fun onActivityDestroyed(activity: android.app.Activity) = Unit
                }
            (context.applicationContext as Application).registerActivityLifecycleCallbacks(
                lifecycle
            )
            shell("input keyevent 3")
            assertTrue("Home did not pause the host", paused.await(5, TimeUnit.SECONDS))
            (context.applicationContext as Application).unregisterActivityLifecycleCallbacks(
                lifecycle
            )
            assertEquals(AudioAvailability.Locked, runtime.availability)
            assertEquals(AudioResult.Failed(AudioFailure.LOCKED), third.sourceState(audio))
        } finally {
            automation.serviceInfo = previousServiceInfo
            instrumentation.runOnMainSync {
                runtime.lock()
                host.finish()
            }
        }
    }

    @Test
    fun noCredentialCannotCreateStorage() {
        assumeTrue(!context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        lateinit var runtime: AndroidProductAudioRuntime
        instrumentation.runOnMainSync {
            runtime = AndroidProductAudioRuntime(context.applicationContext as Application)
        }
        val existed = java.io.File(context.noBackupFilesDir, "dora-vault-v1").exists()
        val host =
            instrumentation.startActivitySync(
                Intent(context, AppLockTestHostActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        try {
            instrumentation.runOnMainSync {
                runtime.requestOpen(host, AudioOpenMode.CREATE_NEW) {
                    assertEquals(
                        AudioAvailability.Failed(AudioFailure.CREDENTIAL_SETUP_REQUIRED),
                        it,
                    )
                }
            }
            assertEquals(existed, java.io.File(context.noBackupFilesDir, "dora-vault-v1").exists())
        } finally {
            instrumentation.runOnMainSync {
                runtime.lock()
                host.finish()
            }
        }
    }

    private fun unlock(
        runtime: AndroidProductAudioRuntime,
        host: android.app.Activity,
        mode: AudioOpenMode,
        pin: String,
    ): ProductAudioSession {
        val done = CountDownLatch(1)
        var outcome: AudioAvailability? = null
        instrumentation.uiAutomation
            .executeAndWaitForEvent(
                {
                    instrumentation.runOnMainSync {
                        runtime.requestOpen(host, mode) {
                            outcome = it
                            done.countDown()
                        }
                    }
                },
                { passwordField() != null },
                10_000,
            )
            .recycle()
        instrumentation.uiAutomation.waitForIdle(500, 10_000)
        require(pin.matches(Regex("[0-9]{6}")))
        assertTrue(
            checkNotNull(passwordField())
                .performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            pin,
                        )
                    },
                )
        )
        shell("input keyevent 66")
        assertTrue("Runtime open did not complete", done.await(30, TimeUnit.SECONDS))
        assertTrue("Runtime open failed: $outcome", outcome is AudioAvailability.Available)
        return (outcome as AudioAvailability.Available).session
    }

    private fun delete(
        runtime: AndroidProductAudioRuntime,
        host: android.app.Activity,
        session: ProductAudioSession,
        audio: AudioIdentity,
        button: String,
    ) {
        val done = CountDownLatch(1)
        var result: AudioResult<Unit>? = null
        instrumentation.uiAutomation
            .executeAndWaitForEvent(
                {
                    instrumentation.runOnMainSync {
                        runtime.requestAudioDeletion(host, session, audio) {
                            result = it
                            done.countDown()
                        }
                    }
                },
                {
                    node {
                        it.text?.toString()?.equals(button, ignoreCase = true) == true &&
                            it.isClickable
                    } != null
                },
                10_000,
            )
            .recycle()
        assertTrue(
            checkNotNull(
                    node {
                        it.text?.toString()?.equals(button, ignoreCase = true) == true &&
                            it.isClickable
                    }
                )
                .performAction(AccessibilityNodeInfo.ACTION_CLICK)
        )
        assertTrue(done.await(30, TimeUnit.SECONDS))
        assertEquals(
            if (button == "Cancel") AudioResult.Failed(AudioFailure.CANCELLED)
            else AudioResult.Value(Unit),
            result,
        )
    }

    private fun passwordField() = node { it.isPassword && it.isEditable && it.isFocused }

    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun find(current: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
            when {
                current == null -> null
                predicate(current) -> current
                else ->
                    (0 until current.childCount).firstNotNullOfOrNull { find(current.getChild(it)) }
            }
        // API32 may report the host as active while SystemUI owns the credential window.
        return instrumentation.uiAutomation.windows.firstNotNullOfOrNull { find(it.root) }
    }

    private fun shell(command: String) =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command)
            )
            .use {
                it.readBytes()
                Unit
            }

    private fun id() = UUID.randomUUID().toString()
}
