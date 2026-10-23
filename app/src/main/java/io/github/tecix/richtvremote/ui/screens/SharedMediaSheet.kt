package io.github.tecix.richtvremote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tecix.richtvremote.data.Route
import io.github.tecix.richtvremote.media.SharedMedia
import io.github.tecix.richtvremote.media.DlnaRenderer
import io.github.tecix.richtvremote.media.MediaDestination

@Composable
fun SharedMediaSheet(
    media: SharedMedia, route: Route, connected: Boolean, busy: Boolean,
    onDismiss: () -> Unit, onPlay: () -> Unit, message: String = "",
    destination: MediaDestination = MediaDestination.VLC,
    onDestinationChange: (MediaDestination) -> Unit = {},
    renderers: List<DlnaRenderer> = emptyList(), renderer: DlnaRenderer? = null,
    discovering: Boolean = false, onDiscover: () -> Unit = {}, onRendererChange: (DlnaRenderer) -> Unit = {},
    choices: List<SharedMedia> = emptyList(), onMediaChange: (SharedMedia) -> Unit = {},
) {
    val dlna = destination == MediaDestination.DLNA
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
                    FilterChip(selected = dlna, enabled = !busy, onClick = { onDestinationChange(MediaDestination.DLNA) },
                        label = { Text("AirReceiver / DLNA") })
                    FilterChip(selected = !dlna, enabled = !busy && !media.mime.startsWith("image/"), onClick = { onDestinationChange(MediaDestination.VLC) },
                        label = { Text("VLC") })
                }
                if (dlna) {
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
            Button(onClick = onPlay, enabled = !busy && (if (dlna) renderer != null && !discovering else connected),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (dlna) "Play on ${renderer?.name ?: "receiver"}" else "Play in VLC")
            }
            TextButton(onClick = onDismiss) { Text(if (busy) "Cancel" else "Close") }
        }
    }
}
