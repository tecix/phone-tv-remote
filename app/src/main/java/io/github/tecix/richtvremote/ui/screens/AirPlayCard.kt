package io.github.tecix.richtvremote.ui.screens

import androidx.compose.foundation.layout.*
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

/** Controls for the video this phone sent over AirPlay: timeline, play/pause, ±10 s, stop, subtitles. */
@Composable
internal fun AirPlayCard(nowPlaying: AirPlayNowPlaying, actions: RemoteActions) {
    val status = nowPlaying.status
    val loading = status == null || status.state == AirPlayStatus.State.LOADING
    val playing = status?.state == AirPlayStatus.State.PLAYING
    var subtitles by rememberSaveable { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Airplay, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(nowPlaying.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(nowPlaying.preparation ?: if (loading) "Opening on ${nowPlaying.receiver.name}…" else "AirPlay · ${nowPlaying.receiver.name}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            if (loading) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            else PlaybackTimeline(status!!.toPlayback(nowPlaying.title), true, actions.airPlaySeek)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { actions.airPlaySkip(-10_000) }, enabled = !loading) {
                    Icon(Icons.Rounded.Replay10, "Back 10 seconds")
                }
                FilledIconButton(onClick = { actions.airPlaySetPlaying(!playing) }, enabled = !loading,
                    modifier = Modifier.size(56.dp)) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play")
                }
                FilledTonalIconButton(onClick = { actions.airPlaySkip(10_000) }, enabled = !loading) {
                    Icon(Icons.Rounded.Forward10, "Forward 10 seconds")
                }
                FilledTonalIconButton(onClick = { subtitles = true }) {
                    Icon(Icons.Rounded.ClosedCaption, "Subtitles")
                }
                if (nowPlaying.preparation != null) {
                    TextButton(onClick = actions.airPlayStop) { Text("Cancel") }
                } else FilledTonalIconButton(onClick = actions.airPlayStop) {
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
