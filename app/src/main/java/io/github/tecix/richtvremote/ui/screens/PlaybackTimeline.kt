package io.github.tecix.richtvremote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.tecix.richtvremote.media.MediaPlayback
import io.github.tecix.richtvremote.media.formatMediaTime
import kotlinx.coroutines.delay

@Composable
internal fun PlaybackTimeline(playback: MediaPlayback?, enabled: Boolean, onSeek: (Long) -> Unit) {
    var position by remember { mutableLongStateOf(0) }
    var dragging by remember(playback?.sessionId, playback?.title, playback?.durationMs) { mutableStateOf<Float?>(null) }
    LaunchedEffect(playback) {
        while (playback != null) { position = playback.positionAt(); delay(500) }
    }
    val duration = playback?.durationMs?.coerceAtLeast(0) ?: 0
    val seekable = enabled && duration > 0 && playback?.canSeek == true
    Column {
        Slider(
            value = dragging ?: position.coerceIn(0, duration).toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                val seek = dragging
                dragging = null
                if (seek != null && seekable) onSeek(seek.toLong().coerceIn(0, duration))
            },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            enabled = seekable,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Playback position" },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (duration > 0) formatMediaTime(dragging?.toLong() ?: position) else "--:--",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (duration > 0) formatMediaTime(duration) else "--:--",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (duration == 0L) Text("Playback time unavailable from this connection", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (!playback!!.canSeek) Text("This media session does not support seeking", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
