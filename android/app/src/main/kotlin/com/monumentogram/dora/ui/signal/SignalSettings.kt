@file:Suppress("LongMethod")

package com.monumentogram.dora.ui.signal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.R
import com.monumentogram.dora.transcript.CloudIdentity
import com.monumentogram.dora.transcript.CloudSetup
import com.monumentogram.dora.transcript.LocalAsrModel
import com.monumentogram.dora.transcript.WhisperNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SignalSettings(activity: MainActivity, modifier: Modifier = Modifier) {
    var modelReady by remember { mutableStateOf<Boolean?>(null) }
    var setup by remember { mutableStateOf(false) }
    val cloud = remember(activity) { CloudIdentity(activity.applicationContext) }
    var endpoint by remember { mutableStateOf(cloud.endpoint()) }
    LaunchedEffect(activity.modelImportStatus) {
        modelReady =
            withContext(Dispatchers.IO) {
                LocalAsrModel(activity).installed() != null && WhisperNative.available()
            }
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsGroup(stringResource(R.string.signal_recognition)) {
            Text("Whisper", style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.signal_local_asr_description))
            Text(
                stringResource(R.string.signal_languages),
                style = MaterialTheme.typography.labelLarge,
            )
            Text("Русский · English")
        }
        SettingsGroup(stringResource(R.string.signal_local_model)) {
            Text("Whisper Small · q8", style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    when (modelReady) {
                        true -> R.string.signal_model_ready
                        false -> R.string.signal_model_missing
                        null -> R.string.signal_checking
                    }
                )
            )
            if (activity.modelImportStatus.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (activity.modelImportMessage.isNotBlank()) Text(uiCopy(activity.modelImportMessage))
            OutlinedButton(
                enabled = !activity.modelImportStatus.busy,
                onClick = activity::launchModelImport,
            ) {
                Text(stringResource(R.string.signal_import_model))
            }
        }
        SettingsGroup(stringResource(R.string.signal_cloud)) {
            Text(
                stringResource(
                    if (endpoint == null) R.string.signal_cloud_not_configured
                    else R.string.signal_cloud_configured
                )
            )
            Text(stringResource(R.string.signal_cloud_description))
            OutlinedButton(onClick = { setup = true }) {
                Text(stringResource(R.string.signal_cloud_connection))
            }
        }
        SettingsGroup(stringResource(R.string.signal_security)) {
            Text(stringResource(R.string.signal_security_description))
        }
        SettingsGroup(stringResource(R.string.signal_appearance)) {
            Text(stringResource(R.string.signal_system_theme))
        }
        SettingsGroup(stringResource(R.string.signal_about)) {
            Text(
                stringResource(R.string.alpha_version_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.signal_about_description))
        }
    }
    if (setup)
        CloudSetup(
            cloud,
            {
                endpoint = cloud.endpoint()
                setup = false
            },
            { setup = false },
        )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
