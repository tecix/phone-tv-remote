package io.github.tecix.richtvremote.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Airplay
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tecix.richtvremote.media.AirPlayNowPlaying
import io.github.tecix.richtvremote.media.AirPlayStatus

/**
 * The video on the TV over AirPlay: title, timeline, play/pause, ±10 s, subtitles, stop. Shown alone, or
 * inside the TV's now-playing card when [tvState] (e.g. "Now on SmartTube") and [volume] are given.
 */
@Composable
internal fun AirPlayCard(nowPlaying: AirPlayNowPlaying, actions: RemoteActions, tvState: String? = null, volume: String? = null) {
    val status = nowPlaying.status
    val loading = status == null || status.state == AirPlayStatus.State.LOADING
    val playing = status?.state == AirPlayStatus.State.PLAYING
    // Until the TV's own player is decoding, it still pulls the video from the sender.
    val stage = status?.stage
    val dependent = !loading && nowPlaying.preparation == null && (stage == "buffering" || stage == "opening" || stage == "checking")
    var subtitles by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(scheme.primary),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Airplay, null, tint = scheme.onPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(nowPlaying.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        nowPlaying.preparation ?: if (loading) when (stage) {
                            "opening" -> "TV player is opening the stream…"
                            "checking" -> "TV is checking the video…"
                            else -> "Opening on ${nowPlaying.receiver.name}…"
                        } else if (dependent) "Buffering · the TV still needs this phone"
                        else listOfNotNull("AirPlay · ${nowPlaying.receiver.name}", tvState).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = scheme.onPrimaryContainer.copy(alpha = .78f),
                    )
                }
                if (volume != null) Surface(shape = CircleShape, color = scheme.primary.copy(alpha = .14f)) {
                    Text(volume, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
            if (loading) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
            else PlaybackTimeline(status!!.toPlayback(nowPlaying.title), true, actions.airPlaySeek)
            // Mid-play stalls: the cue disappears once the TV player is decoding on its own again.
            AnimatedVisibility(dependent) { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { subtitles = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.ClosedCaption, "Subtitles")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { actions.airPlaySkip(-10_000) }, enabled = !loading,
                        shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.Replay10, "Back 10 seconds")
                    }
                    FilledIconButton(onClick = { actions.airPlaySetPlaying(!playing) }, enabled = !loading,
                        shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(72.dp, 64.dp)) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play",
                            Modifier.size(32.dp))
                    }
                    FilledTonalIconButton(onClick = { actions.airPlaySkip(10_000) }, enabled = !loading,
                        shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.Forward10, "Forward 10 seconds")
                    }
                }
                if (nowPlaying.preparation != null) TextButton(onClick = actions.airPlayStop) { Text("Cancel") }
                else FilledTonalIconButton(onClick = actions.airPlayStop, shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = scheme.errorContainer,
                        contentColor = scheme.onErrorContainer)) {
                    Icon(Icons.Rounded.Stop, "Stop AirPlay")
                }
            }
        }
    }
    if (subtitles) AirPlaySubtitlesSheet(status, actions) { subtitles = false }
}

@Composable
private fun AirPlaySubtitlesSheet(status: AirPlayStatus?, actions: RemoteActions, onDismiss: () -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Subtitles", style = MaterialTheme.typography.headlineSmall)
            val tracks = status?.captions.orEmpty()
            if (status?.extended == true) {
                Column {
                    SubtitleChoice("Off", status.caption < 0) { actions.airPlaySelectCaptions(-1) }
                    tracks.forEachIndexed { i, name -> SubtitleChoice(name, status.caption == i) { actions.airPlaySelectCaptions(i) } }
                }
                if (tracks.isEmpty()) Text("No subtitles on the TV yet. Add a file or a link below.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { actions.airPlayCaptionsDelay(-500) }, enabled = tracks.isNotEmpty()) { Text("−0.5 s") }
                    Text("Delay %+.1f s".format(java.util.Locale.ROOT, status.captionOffsetMs / 1000.0),
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { actions.airPlayCaptionsDelay(500) }, enabled = tracks.isNotEmpty()) { Text("+0.5 s") }
                }
            } else {
                Text("This receiver doesn't list its subtitles; added ones are shown when it supports them.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Button(onClick = actions.addSubtitleFile, modifier = Modifier.fillMaxWidth()) { Text("Add subtitle file from phone (.srt, .vtt)") }
            OutlinedTextField(
                value = link, onValueChange = { link = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Subtitle link (http…/.srt or .vtt)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (link.isNotBlank()) { actions.addSubtitleUrl(link); link = "" } }),
            )
            TextButton(onClick = { actions.addSubtitleUrl(link); link = "" }, enabled = link.isNotBlank(),
                modifier = Modifier.align(Alignment.End)) { Text("Add link") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SubtitleChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
