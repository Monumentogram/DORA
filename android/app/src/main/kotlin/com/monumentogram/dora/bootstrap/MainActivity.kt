package com.monumentogram.dora.bootstrap

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.monumentogram.dora.bootstrap.ui.BootstrapNavigationLayout
import com.monumentogram.dora.bootstrap.ui.DoraBootstrapApp
import com.monumentogram.dora.bootstrap.ui.theme.DoraBootstrapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoraBootstrapTheme {
                DoraBootstrapApp()
            }
        }
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
