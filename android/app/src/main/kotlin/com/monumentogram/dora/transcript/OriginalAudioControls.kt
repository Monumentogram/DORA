@file:Suppress("LongMethod") // One compact player surface.

package com.monumentogram.dora.transcript

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.R
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.recording.capturedTime

@Composable
internal fun OriginalAudioControls(
    activity: MainActivity,
    identity: AudioIdentity,
    knownFrames: Long = 0,
    canPlay: () -> Boolean = { true },
) {
    val player = remember(activity, identity) { OriginalAudioPlayer(activity) }
    val enabled = canPlay()
    LaunchedEffect(enabled) { if (!enabled) player.pause() }
    LaunchedEffect(knownFrames) { player.describe(knownFrames) }
    DisposableEffect(player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.clear()
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            activity.lifecycle.removeObserver(observer)
            player.close()
        }
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.signal_original_audio),
                style = MaterialTheme.typography.titleMedium,
            )
            if (player.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val description = stringResource(R.string.signal_seek)
            Slider(
                value = player.position.toFloat(),
                onValueChange = { player.seek(it.toLong()) },
                valueRange = 0f..maxOf(1L, player.frames).toFloat(),
                enabled = player.frames > 0,
                modifier = Modifier.semantics { contentDescription = description },
            )
            Text(
                capturedTime(player.position) + " / " + capturedTime(player.frames),
                style = MaterialTheme.typography.labelLarge,
            )
            Button(
                enabled = enabled && !player.loading,
                onClick = {
                    if (player.playing) player.pause() else if (canPlay()) player.play(identity)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(
                    stringResource(
                        if (player.playing) R.string.signal_pause else R.string.signal_play
                    )
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    enabled = player.frames > 0,
                    onClick = { player.seek(player.position - SEEK_FRAMES) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.signal_back_ten))
                }
                TextButton(
                    enabled = player.frames > 0,
                    onClick = { player.seek(player.position + SEEK_FRAMES) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.signal_forward_ten))
                }
            }
            if (player.failed)
                Text(
                    stringResource(R.string.signal_playback_error),
                    color = MaterialTheme.colorScheme.error,
                )
        }
    }
}

private const val SEEK_FRAMES = 160_000L
