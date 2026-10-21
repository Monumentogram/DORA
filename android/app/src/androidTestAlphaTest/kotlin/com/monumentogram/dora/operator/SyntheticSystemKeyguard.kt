@file:Suppress("DEPRECATION")

package com.monumentogram.dora.operator

import android.app.Instrumentation
import android.app.KeyguardManager
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Only the synthetic emulator's OS PIN surface; this grants no DORA App Lock authority. */
@Suppress(
    "MagicNumber",
    "LongMethod",
) // Literal synthetic PIN length, keypad identities and bounded waits.
internal class SyntheticSystemKeyguard(
    private val instrumentation: Instrumentation,
    private val pinProvider: () -> String,
    private val node: ((AccessibilityNodeInfo) -> Boolean) -> AccessibilityNodeInfo?,
) {
    fun unlockIfNeeded() {
        val keyguard = instrumentation.targetContext.getSystemService(KeyguardManager::class.java)
        assertTrue(
            "Synthetic secondary credential must already be configured",
            keyguard.isDeviceSecure,
        )
        if (!keyguard.isDeviceLocked) return
        // Separate OS-user unlock from App Lock. Neither a shell dismissal nor a device PIN
        // typed into an unrelated lockscreen is evidence that DORA accepted its own prompt.
        if (!systemKeyguardPinSurface()) {
            try {
                instrumentation.uiAutomation
                    .executeAndWaitForEvent(
                        {
                            ParcelFileDescriptor.AutoCloseInputStream(
                                    instrumentation.uiAutomation.executeShellCommand(
                                        "input keyevent ${KeyEvent.KEYCODE_WAKEUP} ${KeyEvent.KEYCODE_MENU}"
                                    )
                                )
                                .use { it.readBytes() }
                        },
                        { systemKeyguardPinSurface() },
                        15_000,
                    )
                    .recycle()
            } catch (_: TimeoutException) {
                throw AssertionError(
                    "SystemUI locked PIN surface not recognized: locked=${keyguard.isDeviceLocked}"
                )
            }
        }
        assertTrue("System keyguard must remain locked at PIN entry", keyguard.isDeviceLocked)
        assertTrue(
            "PIN entry requires the actual SystemUI keyguard keypad",
            systemKeyguardPinSurface(),
        )
        // API28 keyguard uses custom PIN widgets. Its known SystemUI keypad is a separate
        // surface from App Lock's focused editable credential field; preserve those gates there.
        val pin = pinProvider()
        require(pin.matches(Regex("[0-9]{6}")))
        val events = pin.map { KeyEvent.KEYCODE_0 + it.digitToInt() }.joinToString(" ")
        ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(
                    "input keyevent $events ${KeyEvent.KEYCODE_ENTER}"
                )
            )
            .use { it.readBytes() }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (keyguard.isDeviceLocked && System.nanoTime() < deadline) Thread.sleep(100)
        assertFalse(
            "Real system credential did not unlock the secondary keyguard",
            keyguard.isDeviceLocked,
        )
    }

    private fun systemKeyguardPinSurface(): Boolean {
        val system = "com.android.systemui"
        val digits =
            (0..9).all { digit ->
                node {
                    it.packageName?.toString() == system &&
                        (it.viewIdResourceName == "$system:id/key$digit" ||
                            (!it.isPassword && it.text?.toString() == digit.toString()))
                } != null
            }
        val pinContainer =
            node {
                it.packageName?.toString() == system &&
                    it.viewIdResourceName in
                        setOf("$system:id/keyguard_pin_view", "$system:id/pinEntry")
            } != null
        val submit =
            node {
                it.packageName?.toString() == system &&
                    (it.viewIdResourceName == "$system:id/key_enter" ||
                        it.text?.toString() == "Enter" ||
                        it.contentDescription?.toString() in setOf("Enter", "Unlock"))
            } != null
        return digits && (pinContainer || submit)
    }
}
