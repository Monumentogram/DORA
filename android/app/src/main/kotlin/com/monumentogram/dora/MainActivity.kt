package com.monumentogram.dora

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import com.monumentogram.dora.recording.ProductRecordingHost
import com.monumentogram.dora.recording.ProductRecordingService
import com.monumentogram.dora.ui.BootstrapNavigationLayout
import com.monumentogram.dora.ui.DoraBootstrapApp
import com.monumentogram.dora.ui.theme.DoraBootstrapTheme

class MainActivity : ComponentActivity() {
    internal var presentationWindow = 0L
        private set

    private var presentationForeground = false
    private var presentationFocus = false
    internal val presentationVisibility: String
        get() = "f=${if (presentationForeground) 1 else 0} k=${if (presentationFocus) 1 else 0}"

    private val diagnosticFrame =
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                (application as DoraApplication).recording.latency.drawingFrameNanos =
                    frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    private val recordingAction =
        mutableStateOf<Triple<String?, String?, Int>>(Triple(null, null, 0))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presentationWindow =
            (application as DoraApplication).recording.latency.newPresentationEpoch()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        recordingAction.value =
            Triple(intent?.action, intent?.getStringExtra(ProductRecordingService.TOKEN), 0)
        enableEdgeToEdge()
        setContent {
            DoraBootstrapTheme {
                ProductRecordingHost(this, recordingAction.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        presentationEvent("intent")
        setIntent(intent)
        recordingAction.value =
            Triple(
                intent.action,
                intent.getStringExtra(ProductRecordingService.TOKEN),
                recordingAction.value.third + 1,
            )
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val recording = (application as DoraApplication).recording
        if (!recording.latency.enabled) return super.dispatchTouchEvent(event)
        recording.latency.presentation(
            "input",
            presentationWindow,
            detail = "a=${event.actionMasked}",
        )
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            (application as DoraApplication)
                .recording
                .inputAt(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        event.eventTimeNanos
                    else
                        System.nanoTime() -
                            (SystemClock.uptimeMillis() - event.eventTime) * NANOS_PER_MILLI
                )
        }
        return try {
            super.dispatchTouchEvent(event)
        } finally {
            (application as DoraApplication).recording.inputAt(0)
        }
    }

    override fun onResume() {
        super.onResume()
        presentationForeground = true
        presentationEvent("resume")
        if ((application as DoraApplication).recording.latency.enabled)
            Choreographer.getInstance().postFrameCallback(diagnosticFrame)
    }

    override fun onPause() {
        presentationForeground = false
        presentationEvent("pause")
        Choreographer.getInstance().removeFrameCallback(diagnosticFrame)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        presentationFocus = hasFocus
        presentationEvent("focus")
    }

    override fun onDestroy() {
        presentationEvent("destroy")
        super.onDestroy()
    }

    private fun presentationEvent(event: String) {
        (application as DoraApplication)
            .recording
            .latency
            .presentation(
                event,
                presentationWindow,
                detail = presentationVisibility,
            )
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

@Preview(name = "Compact light", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun CompactLightPreview() = DoraBootstrapTheme(darkTheme = false) { DoraBootstrapApp() }

@Preview(
    name = "Compact dark",
    widthDp = 360,
    heightDp = 800,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun CompactDarkPreview() = DoraBootstrapTheme(darkTheme = true) { DoraBootstrapApp() }

@Preview(name = "Wide light", widthDp = 840, heightDp = 900, showBackground = true)
@Composable
private fun WideLightPreview() =
    DoraBootstrapTheme(darkTheme = false) {
        DoraBootstrapApp(forcedLayout = BootstrapNavigationLayout.WIDE_RAIL)
    }
