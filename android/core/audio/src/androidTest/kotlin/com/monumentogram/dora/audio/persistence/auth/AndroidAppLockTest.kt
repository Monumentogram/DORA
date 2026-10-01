package com.monumentogram.dora.audio.persistence.auth

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exists only in the instrumentation APK; never in the product manifest. */
class AppLockTestHostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class AndroidAppLockTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun coldLaunchWithoutCredentialRequiresSystemSetup() {
        assumeTrue(!context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        lateinit var lock: AndroidAppLock
        instrumentation.runOnMainSync {
            lock = AndroidAppLock(context.applicationContext as Application)
        }
        val host =
            instrumentation.startActivitySync(
                Intent(context, AppLockTestHostActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        try {
            assertEquals(AppLockState.LOCKED, lock.state)
            val complete = CountDownLatch(1)
            instrumentation.runOnMainSync {
                lock.requestUnlock(host) {
                    assertEquals(UnlockResult.CREDENTIAL_SETUP_REQUIRED, it)
                    complete.countDown()
                }
            }
            assertTrue(complete.await(5, TimeUnit.SECONDS))
            assertEquals(AppLockState.LOCKED, lock.state)
        } finally {
            instrumentation.runOnMainSync {
                lock.lock()
                host.finish()
            }
        }
    }

    @Test
    fun missingProcessRegistryAndInventedActivityIntentStayLocked() {
        lateinit var lock: AndroidAppLock
        instrumentation.runOnMainSync {
            lock = AndroidAppLock(context.applicationContext as Application)
        }
        val host =
            instrumentation.startActivitySync(
                Intent(context, AppLockTestHostActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        try {
            val forged =
                instrumentation.startActivitySync(
                    Intent(context, AppLockAuthenticationActivity::class.java)
                        .putExtra("attempt", "invented")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            instrumentation.waitForIdleSync()
            assertTrue(forged.isFinishing || forged.isDestroyed)
            assertEquals(AppLockState.LOCKED, lock.state)
        } finally {
            instrumentation.runOnMainSync {
                lock.lock()
                host.finish()
            }
        }
    }

    @Test
    fun actualSystemCredentialUnlocksThenHomeRevokesCapturedCapability() {
        assumeTrue(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val syntheticPin = InstrumentationRegistry.getArguments().getString("syntheticAuthPin")
        assumeTrue(syntheticPin != null)
        val observeRevocation = AtomicBoolean(false)
        val automation = instrumentation.uiAutomation
        val previousServiceInfo = automation.serviceInfo
        val revoked = CountDownLatch(1)
        lateinit var lock: AndroidAppLock
        instrumentation.runOnMainSync {
            lock =
                AndroidAppLock(context.applicationContext as Application) {
                    if (observeRevocation.get()) revoked.countDown()
                }
        }
        val host =
            instrumentation.startActivitySync(
                Intent(context, AppLockTestHostActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        try {
            val completed = CountDownLatch(1)
            var outcome: UnlockResult? = null
            retrieveInteractiveWindows()
            instrumentation.uiAutomation
                .executeAndWaitForEvent(
                    {
                        instrumentation.runOnMainSync {
                            lock.requestUnlock(host) {
                                outcome = it
                                completed.countDown()
                            }
                        }
                    },
                    { passwordField() != null },
                    10_000,
                )
                .recycle()
            enterSystemCredential(checkNotNull(syntheticPin))
            assertTrue("Prompt did not complete", completed.await(15, TimeUnit.SECONDS))
            assertEquals(UnlockResult.UNLOCKED, outcome)
            val authorization = lock.captureAuthorization()
            authorization.requireActive()
            observeRevocation.set(true)
            shell("input keyevent 3")
            assertTrue("Home did not revoke", revoked.await(5, TimeUnit.SECONDS))
            assertEquals(AppLockState.LOCKED, lock.state)
            try {
                authorization.withPlaintextDelivery { fail("Plaintext after Home") }
                fail("Old grant survived")
            } catch (_: AppLockedException) {}
        } finally {
            automation.serviceInfo = previousServiceInfo
            instrumentation.runOnMainSync {
                lock.lock()
                host.finish()
            }
        }
    }

    private fun retrieveInteractiveWindows() {
        instrumentation.uiAutomation.serviceInfo =
            instrumentation.uiAutomation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
    }

    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command)
            )
            .use { it.readBytes() }
    }

    private fun enterSystemCredential(syntheticPin: String) {
        instrumentation.uiAutomation.waitForIdle(500, 10_000)
        require(syntheticPin.matches(Regex("[0-9]{6}")))
        val field = checkNotNull(passwordField())
        assertTrue(
            field.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        syntheticPin,
                    )
                },
            )
        )
        assertTrue(field.refresh())
        assertEquals(6, field.text?.length)
        shell("input keyevent 66")
    }

    private fun passwordField(): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            return if (node.isPassword && node.isEditable && node.isFocused) node
            else (0 until node.childCount).firstNotNullOfOrNull { find(node.getChild(it)) }
        }
        // API32 may report the host as active while SystemUI owns the credential window.
        return instrumentation.uiAutomation.windows.firstNotNullOfOrNull { find(it.root) }
    }
}
