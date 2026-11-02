package com.monumentogram.dora

import android.os.Debug
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.recording.RecordingController
import com.monumentogram.dora.ui.signal.uiCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real microphone and visible controls; no Espresso idle dependency after screen wake. */
class LongRecordingDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation
        get() = instrumentation.uiAutomation

    private val context
        get() = instrumentation.targetContext

    @Test
    @Suppress(
        "LongMethod"
    ) // One actual capture session, wake interaction and independent successor.
    fun screenOffCapturePauseResumeSaveAndNextRecording() {
        val duration =
            InstrumentationRegistry.getArguments().getString("longRecordingSeconds")?.toLong()
                ?: 10L
        // Full-day physical runs require a separate, owner-authorized endurance protocol.
        require(duration in 10L..28_800L)
        shell("pm grant ${context.packageName} android.permission.RECORD_AUDIO")
        shell("input keyevent 224")
        shell("wm dismiss-keyguard")
        ActivityScenario.launch(MainActivity::class.java).use {
            val controller = (context.applicationContext as DoraApplication).recording
            start()
            await(controller, 30_000) {
                it.recording.phase == RecordingPhase.RECORDING && it.recording.frames >= 16_000
            }
            shell("input keyevent 223")
            val deadline = SystemClock.elapsedRealtime() + (duration + 90) * 1000
            var nextSample = 0L
            var peakSampledPssKiB = Debug.getPss()
            while (controller.state.value.recording.frames < duration * 16_000) {
                peakSampledPssKiB = maxOf(peakSampledPssKiB, Debug.getPss())
                assertEquals(
                    controller.diagnosticSummary(),
                    RecordingPhase.RECORDING,
                    controller.state.value.recording.phase,
                )
                assertTrue(
                    "Capture stalled: ${controller.diagnosticSummary()}",
                    SystemClock.elapsedRealtime() < deadline,
                )
                if (SystemClock.elapsedRealtime() >= nextSample) {
                    println("DORA_LONG_SAMPLE ${controller.diagnosticSummary()}")
                    nextSample = SystemClock.elapsedRealtime() + 60_000
                }
                Thread.sleep(1000)
            }
            assertTrue(controller.state.value.recording.durableFrames > 0)
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            click(uiCopy("Пауза"))
            await(controller, 15_000) { it.recording.phase == RecordingPhase.PAUSED }
            val pausedFrames = controller.state.value.recording.frames
            Thread.sleep(1200)
            assertEquals(pausedFrames, controller.state.value.recording.frames)
            click(uiCopy("Продолжить"))
            await(controller, 15_000) {
                it.recording.phase == RecordingPhase.RECORDING && it.recording.frames > pausedFrames
            }
            val finalizationStarted = SystemClock.elapsedRealtime()
            save(controller)
            val finalizationMillis = SystemClock.elapsedRealtime() - finalizationStarted
            val saved = controller.state.value.recording
            assertEquals(saved.frames, saved.durableFrames)
            assertTrue(saved.durableFrames >= duration * 16_000)
            println(
                "DORA_LONG_SAVED seconds=$duration frames=${saved.frames} bytes=${saved.durableFrames * 2L} " +
                    "peakSampledPssKiB=$peakSampledPssKiB finalizationMillis=$finalizationMillis"
            )
            // A terminal session must release capture, service and writer ownership.
            start(fromLibrary = false)
            await(controller, 30_000) {
                it.recording.phase == RecordingPhase.RECORDING && it.recording.frames >= 16_000
            }
            save(controller)
            click(context.getString(R.string.signal_copy_2))
            assertTrue("Library did not return", waitForText("DORA", 15_000) != null)
        }
    }

    private fun start(fromLibrary: Boolean = true) {
        if (fromLibrary) click(context.getString(R.string.signal_new_recording))
        click(uiCopy("Я предупредил(а) участников о записи"))
        click(context.getString(R.string.signal_copy_4))
    }

    private fun save(controller: RecordingController) {
        click(context.getString(R.string.signal_copy_13))
        click(context.getString(R.string.signal_copy_17))
        await(controller, 60_000) { it.recording.phase == RecordingPhase.SAVED }
    }

    private fun await(
        controller: RecordingController,
        timeout: Long,
        predicate: (com.monumentogram.dora.recording.RecordingViewState) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!predicate(controller.state.value)) {
            assertTrue(controller.diagnosticSummary(), SystemClock.elapsedRealtime() < deadline)
            Thread.sleep(100)
        }
    }

    @Suppress(
        "NestedBlockDepth"
    ) // Resolve and scroll a visible actionable node without Espresso idling.
    private fun click(label: String) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        var forward = false
        var scans = 0
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = automation.rootInActiveWindow
            val node = root?.let { find(it, label, clickableOnly = true) }
            if (node != null) {
                var target: AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable) target = target.parent
                if (
                    target?.isEnabled == true &&
                        target.isVisibleToUser &&
                        target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                )
                    return
                node.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id
                )
            } else
                root?.let {
                    // A stale pre-navigation tree can leave the next screen at either scroll edge.
                    // Search both directions; captions and checkbox controls may share the same
                    // label.
                    if (!scroll(it, forward) || ++scans % 3 == 0) forward = !forward
                }
            Thread.sleep(200)
        }
        dumpControls()
        throw AssertionError("Visible control unavailable: $label")
    }

    private fun waitForText(label: String, timeout: Long): AccessibilityNodeInfo? {
        val deadline = SystemClock.elapsedRealtime() + timeout
        do {
            automation.rootInActiveWindow
                ?.let { find(it, label) }
                ?.let {
                    return it
                }
            Thread.sleep(200)
        } while (SystemClock.elapsedRealtime() < deadline)
        return null
    }

    @Suppress("ReturnCount") // A tree walk exits at the first actionable matching control.
    private fun find(
        node: AccessibilityNodeInfo,
        label: String,
        clickableOnly: Boolean = false,
    ): AccessibilityNodeInfo? {
        if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
            if (!clickableOnly) return node
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) target = target.parent
            if (target != null) return target
        }
        for (index in 0 until node.childCount) {
            node
                .getChild(index)
                ?.let { find(it, label, clickableOnly) }
                ?.let {
                    return it
                }
        }
        return null
    }

    /** Kept in private test storage only; never print screen text to public test output. */
    private fun dumpControls() {
        val text = StringBuilder()
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 25 || text.length > 65_536) return
            val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
            text.appendLine(
                "$depth ${node.className} text=${node.text} desc=${node.contentDescription} " +
                    "visible=${node.isVisibleToUser} click=${node.isClickable} bounds=$bounds"
            )
            for (index in 0 until node.childCount) node.getChild(index)?.let {
                visit(it, depth + 1)
            }
        }
        automation.rootInActiveWindow?.let { visit(it, 0) }
        java.io.File(context.cacheDir, "v9-controls-private.txt").writeText(text.toString())
    }

    @Suppress("ReturnCount") // Stop after the first scroll container accepting the action.
    private fun scroll(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val action =
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (node.isScrollable && node.performAction(action)) return true
        for (index in 0 until node.childCount) {
            if (node.getChild(index)?.let { scroll(it, forward) } == true) return true
        }
        return false
    }

    private fun shell(command: String) {
        automation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream ->
                val bytes = ByteArray(4096)
                while (stream.read(bytes) >= 0) Unit
            }
        }
    }
}
