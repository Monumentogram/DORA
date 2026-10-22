@file:Suppress("DEPRECATION")

package com.monumentogram.dora.ownerpilot

import android.app.Instrumentation
import android.app.KeyguardManager
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.TimeUnit
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
            ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand(
                        "input keyevent ${KeyEvent.KEYCODE_WAKEUP} ${KeyEvent.KEYCODE_MENU}"
                    )
                )
                .use { it.readBytes() }
            // Accessibility events may precede population of their window trees. Poll the
            // same exact observed surface instead of requiring another event to recheck it.
            val surfaceDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!systemKeyguardPinSurface() && System.nanoTime() < surfaceDeadline) Thread.sleep(
                100
            )
        }
        assertTrue("System keyguard must remain locked at PIN entry", keyguard.isDeviceLocked)
        assertTrue(
            "PIN entry requires the actual SystemUI keyguard keypad: " + windowMetadata(),
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

    private fun windowMetadata(): String {
        val automation = instrumentation.uiAutomation
        fun describe(root: AccessibilityNodeInfo?): String {
            if (root == null) return "NONE"
            val ids = mutableSetOf<String>()
            var remaining = 512
            val known =
                Regex("com\\.android\\.systemui:id/(key[0-9]|keyguard_pin_view|pinEntry|key_enter)")
            fun visit(current: AccessibilityNodeInfo?) {
                if (current == null || remaining-- <= 0) return
                current.viewIdResourceName?.takeIf(known::matches)?.let(ids::add)
                for (index in 0 until current.childCount) visit(current.getChild(index))
            }
            visit(root)
            return "package=${root.packageName} class=${root.className} " +
                "visible=${root.isVisibleToUser} focused=${root.isFocused} knownIds=${ids.sorted()}"
        }
        val windows = automation.windows
        return "windows=${windows.size} roots=" +
            windows.take(8).joinToString(";") { describe(it.root) } +
            " active=" +
            describe(automation.rootInActiveWindow)
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
