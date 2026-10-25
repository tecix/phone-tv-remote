package io.github.tecix.richtvremote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tecix.richtvremote.data.Route
import io.github.tecix.richtvremote.media.SharedMedia
import io.github.tecix.richtvremote.media.DlnaRenderer
import io.github.tecix.richtvremote.media.MediaDestination
import io.github.tecix.richtvremote.media.AirPlayReceiver
import io.github.tecix.richtvremote.media.ServedSubtitle

@Composable
fun SharedMediaSheet(
    media: SharedMedia, route: Route, connected: Boolean, busy: Boolean,
    onDismiss: () -> Unit, onPlay: () -> Unit, message: String = "",
    destination: MediaDestination = MediaDestination.VLC,
    onDestinationChange: (MediaDestination) -> Unit = {},
    renderers: List<DlnaRenderer> = emptyList(), renderer: DlnaRenderer? = null,
    discovering: Boolean = false, onDiscover: () -> Unit = {}, onRendererChange: (DlnaRenderer) -> Unit = {},
    choices: List<SharedMedia> = emptyList(), onMediaChange: (SharedMedia) -> Unit = {},
    airPlayReceivers: List<AirPlayReceiver> = emptyList(), airPlayReceiver: AirPlayReceiver? = null,
    onAirPlayChange: (AirPlayReceiver) -> Unit = {}, onDiscoverAirPlay: () -> Unit = {},
    subtitles: List<ServedSubtitle> = emptyList(), onAddSubtitle: () -> Unit = {},
    onRemoveSubtitle: (ServedSubtitle) -> Unit = {},
) {
    val dlna = destination == MediaDestination.DLNA
    val airplay = destination == MediaDestination.AIRPLAY
    val image = media.mime.startsWith("image/")
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Rounded.Cast, null, tint = MaterialTheme.colorScheme.primary)
                Text("Play on TV", style = MaterialTheme.typography.headlineSmall)
                Text(media.title, style = MaterialTheme.typography.titleMedium)
                if (choices.size > 1) {
                    Text("Choose a file to play (${choices.size} shared)")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        choices.forEach { file ->
                            FilterChip(selected = file == media, enabled = !busy, onClick = { onMediaChange(file) }, label = { Text(file.title) })
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = airplay, enabled = !busy && !image, onClick = { onDestinationChange(MediaDestination.AIRPLAY) },
                        label = { Text("AirPlay") })
                    FilterChip(selected = dlna, enabled = !busy, onClick = { onDestinationChange(MediaDestination.DLNA) },
                        label = { Text("AirReceiver / DLNA") })
                    FilterChip(selected = destination == MediaDestination.VLC, enabled = !busy && !image, onClick = { onDestinationChange(MediaDestination.VLC) },
                        label = { Text("VLC") })
                }
                if (airplay) {
                    Text(if (airPlayReceiver != null) "TinyTV plays it in its own player. Control it from the AirPlay card on the remote screen."
                        else "Looking for AirPlay receivers. TinyTV Receiver on the TV has one built in.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        airPlayReceivers.forEach { device ->
                            FilterChip(selected = airPlayReceiver == device, enabled = !busy, onClick = { onAirPlayChange(device) },
                                label = { Text(device.label) })
                        }
                    }
                    if (discovering) {
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                        Text("Finding AirPlay receivers…")
                    }
                    TextButton(onClick = onDiscoverAirPlay, enabled = !discovering && !busy) { Text(if (airPlayReceiver != null) "Find again" else "Find receivers") }
                    Text("Subtitles", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        subtitles.forEach { sub ->
                            InputChip(selected = true, enabled = !busy, onClick = { onRemoveSubtitle(sub) }, label = { Text(sub.name, maxLines = 1) },
                                trailingIcon = { Icon(Icons.Rounded.Close, "Remove ${sub.name}", Modifier.size(18.dp)) })
                        }
                        AssistChip(onClick = onAddSubtitle, enabled = !busy, label = { Text("Add subtitle") },
                            leadingIcon = { Icon(Icons.Rounded.ClosedCaption, null, Modifier.size(18.dp)) })
                    }
                } else if (dlna) {
                    Text(if (renderer != null) "Using the current DLNA receiver. Manage or disconnect it from the top menu."
                        else "AirReceiver starts through the TV remote connection. Choose its receiver below. DLNA must be enabled in AirReceiver.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        renderers.forEach { device ->
                            FilterChip(selected = renderer == device, enabled = !busy, onClick = { onRendererChange(device) },
                                label = { Text("${device.name} · ${device.host}") })
                        }
                    }
                    if (discovering) {
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                        Text("Starting AirReceiver and finding DLNA receivers…")
                    }
                    TextButton(onClick = onDiscover, enabled = !discovering && !busy) { Text(if (renderer != null) "Change receiver" else "Find receivers") }
                } else {
                    Text("VLC · ${if (route == Route.DIRECT) "Direct" else "Home Assistant"}")
                    if (!connected) Text("Connect to the TV before playing.", color = MaterialTheme.colorScheme.error)
                }
                Text(if (media.isFile) "The file is prepared on this phone, then streamed over local Wi-Fi. Keep the phone connected. Stop sharing when finished."
                    else "The TV plays this media URL directly. It must be accessible without your phone’s login.")
                if (busy) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (media.isFile) "Preparing and opening media… Cloud files may need to download first." else "Opening media…")
                }
                if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onPlay, enabled = !busy && when (destination) {
                    MediaDestination.DLNA -> renderer != null && !discovering
                    MediaDestination.AIRPLAY -> airPlayReceiver != null && !discovering
                    MediaDestination.VLC -> connected
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(when (destination) {
                    MediaDestination.DLNA -> "Play on ${renderer?.name ?: "receiver"}"
                    MediaDestination.AIRPLAY -> "AirPlay to ${airPlayReceiver?.name ?: "TV"}"
                    MediaDestination.VLC -> "Play in VLC"
                })
            }
            TextButton(onClick = onDismiss) { Text(if (busy) "Cancel" else "Close") }
        }
    }
}
