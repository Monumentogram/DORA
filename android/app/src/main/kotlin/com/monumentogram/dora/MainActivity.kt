package com.monumentogram.dora

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
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
    private val recordingAction =
        mutableStateOf<Triple<String?, String?, Int>>(Triple(null, null, 0))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        setIntent(intent)
        recordingAction.value =
            Triple(
                intent.action,
                intent.getStringExtra(ProductRecordingService.TOKEN),
                recordingAction.value.third + 1,
            )
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
