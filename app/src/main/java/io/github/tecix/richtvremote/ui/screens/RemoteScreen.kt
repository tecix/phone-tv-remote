package io.github.tecix.richtvremote.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Airplay
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tecix.richtvremote.DirectState
import io.github.tecix.richtvremote.HaState
import io.github.tecix.richtvremote.HaTile
import io.github.tecix.richtvremote.R
import io.github.tecix.richtvremote.TvKey
import io.github.tecix.richtvremote.TvStatus
import io.github.tecix.richtvremote.data.Route
import io.github.tecix.richtvremote.data.MacroAction
import io.github.tecix.richtvremote.data.Settings
import io.github.tecix.richtvremote.media.DlnaRenderer
import io.github.tecix.richtvremote.ui.components.HoldBehavior
import io.github.tecix.richtvremote.ui.components.RemoteButton
import io.github.tecix.richtvremote.ui.components.RemoteIconButton
import io.github.tecix.richtvremote.ui.components.rememberHaptic

/** Apps offered as one-tap shortcuts, by their Android TV package names. */
val QuickApps = listOf(
    QuickApp("YouTube", "com.google.android.youtube.tv", Icons.Rounded.SmartDisplay),
    QuickApp("Web Video Caster", "com.instantbits.cast.receiver", Icons.Rounded.Cast),
    QuickApp("AirReceiver", "com.softmedia.receiver", Icons.Rounded.Airplay),
    QuickApp("VLC", "org.videolan.vlc", Icons.Rounded.Movie),
)

class QuickApp(val name: String, val packageName: String, val icon: ImageVector)

class RemoteActions(
    val press: (TvKey) -> Unit,
    val longPress: (TvKey) -> Unit,
    val launchApp: (String) -> Unit,
    val setRoute: (Route) -> Unit,
    val openDevices: () -> Unit,
    val openSettings: () -> Unit,
    val openKeyboard: () -> Unit,
    val pair: () -> Unit,
    val retry: () -> Unit,
    val runMacro: (String) -> Unit,
    val saveMacro: (MacroAction) -> Unit,
    val deleteMacro: (String) -> Unit,
    val refreshTile: () -> Unit,
    val irPower: () -> Unit,
    val openVoice: () -> Unit = {},
    val openVoiceText: () -> Unit = openVoice,
    val seekTo: (Long) -> Unit = {},
    val setupMediaAccess: () -> Unit = {},
    val pickMedia: () -> Unit = {},
    val disconnectDlna: () -> Unit = {},
    val airPlaySetPlaying: (Boolean) -> Unit = {},
    val airPlaySeek: (Long) -> Unit = {},
    val airPlaySkip: (Long) -> Unit = {},
    val airPlayStop: () -> Unit = {},
    val airPlaySelectCaptions: (Int) -> Unit = {},
    val airPlayCaptionsDelay: (Long) -> Unit = {},
    val addSubtitleFile: () -> Unit = {},
    val addSubtitleUrl: (String) -> Unit = {},
)

@Composable
fun RemoteScreen(
    settings: Settings,
    direct: DirectState,
    ha: HaState,
    status: TvStatus,
    haTile: HaTile,
    canSend: Boolean,
    snackbar: SnackbarHostState,
    actions: RemoteActions,
    mediaAccessRequired: Boolean = false,
    sharingFile: Boolean = false,
    onStopSharing: () -> Unit = {},
    stoppingStreaming: Boolean = false,
    dlnaReceiver: DlnaRenderer? = null,
    airPlay: io.github.tecix.richtvremote.media.AirPlayNowPlaying? = null,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                settings.selectedTv?.name ?: stringResource(R.string.app_name),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            StatusLine(settings.route, direct, ha)
                        }
                    },
                    actions = {
                        RouteToggle(settings.route, actions.setRoute)
                        IconButton(onClick = actions.openDevices) { Icon(Icons.Rounded.Tv, "TVs") }
                        IconButton(onClick = actions.openSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                dlnaReceiver?.let { receiver ->
                    DlnaConnectionMenu(receiver, actions.disconnectDlna, stoppingStreaming)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ConnectionCard(settings, direct, ha, status, haTile, canSend, actions)
                airPlay?.let { AirPlayCard(it, actions) }
                if (mediaAccessRequired) TextButton(onClick = actions.setupMediaAccess) { Text("Enable playback access on TV") }
                SmartTubeSearchEntry(actions)
                if (sharingFile) TextButton(onClick = onStopSharing, enabled = !stoppingStreaming) {
                    Text(if (stoppingStreaming) "Stopping TV streaming…" else "Stop TV streaming")
                }
                TopKeys(actions, canSend, settings)
                DPad(actions, canSend)
                NavKeys(actions, canSend)
                VolumeRow(status, settings.route, actions, canSend)
                MacroActionsSection(settings.macroActions, canSend, actions.runMacro, actions.saveMacro, actions.deleteMacro)
                QuickAppsRow(actions, canSend)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DlnaConnectionMenu(receiver: DlnaRenderer, onDisconnect: () -> Unit, stopping: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        AssistChip(onClick = { expanded = true }, enabled = !stopping,
            leadingIcon = { Icon(Icons.Rounded.Cast, null, Modifier.size(18.dp)) },
            label = { Text("DLNA · ${receiver.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("${receiver.name} · ${receiver.host}") }, onClick = {}, enabled = false)
            DropdownMenuItem(text = { Text("Disconnect DLNA") }, onClick = {
                expanded = false
                onDisconnect()
            })
        }
    }
}

@Composable
private fun StatusLine(route: Route, direct: DirectState, ha: HaState) {
    val (text, color) = when (route) {
        Route.HOME_ASSISTANT -> when (ha) {
            HaState.Unconfigured -> "Home Assistant · not set up" to MaterialTheme.colorScheme.error
            HaState.Checking -> "Home Assistant · checking…" to MaterialTheme.colorScheme.onSurfaceVariant
            is HaState.Ready -> "Home Assistant · ${ha.name}" to MaterialTheme.colorScheme.tertiary
            is HaState.Failed -> "Home Assistant · error" to MaterialTheme.colorScheme.error
        }
        Route.DIRECT -> when (direct) {
            DirectState.NoTv -> "No TV selected" to MaterialTheme.colorScheme.onSurfaceVariant
            is DirectState.Connecting -> "Connecting…" to MaterialTheme.colorScheme.onSurfaceVariant
            is DirectState.Connected -> "Direct · ${direct.info.model}" to MaterialTheme.colorScheme.tertiary
            is DirectState.NeedsPairing -> "Needs pairing" to MaterialTheme.colorScheme.error
            is DirectState.Offline -> "Offline" to MaterialTheme.colorScheme.error
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun RouteToggle(route: Route, onChange: (Route) -> Unit) {
    val haptic = rememberHaptic()
    val direct = route == Route.DIRECT
    val destination = if (direct) "Home Assistant" else "Direct"
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text("Switch to $destination") } },
        state = rememberTooltipState(),
    ) {
        FilledTonalIconButton(onClick = {
            haptic(false)
            onChange(if (direct) Route.HOME_ASSISTANT else Route.DIRECT)
        }, shapes = IconButtonDefaults.shapes()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (direct) Icons.Rounded.Cast else Icons.Rounded.Home,
                    "${if (direct) "Direct" else "Home Assistant"} mode. Switch to $destination", Modifier.size(20.dp))
                Text(if (direct) "Direct" else "HA", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SmartTubeSearchEntry(actions: RemoteActions) {
    val haptic = rememberHaptic()
    val fileShape = remember { androidx.compose.foundation.shape.GenericShape { size, _ ->
        moveTo(0f, 0f); lineTo(size.width * .72f, 0f); lineTo(size.width, size.height / 2)
        lineTo(size.width * .72f, size.height); lineTo(0f, size.height); lineTo(size.width * .12f, size.height / 2); close()
    } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { haptic(false); actions.pickMedia() },
            shapes = ButtonDefaults.shapes(fileShape), contentPadding = PaddingValues(start = 12.dp, end = 20.dp),
            modifier = Modifier.width(76.dp).height(64.dp).semantics { contentDescription = "Choose file for TV" }) {
            Text("File", maxLines = 1)
        }
        Surface(modifier = Modifier.weight(1f), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { haptic(false); actions.openVoiceText() },
                    modifier = Modifier.weight(1f).height(56.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Search SmartTube", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RemoteIconButton(Icons.Rounded.Mic, "SmartTube voice search", actions.openVoice,
                    size = 56.dp, colors = IconButtonDefaults.filledIconButtonColors())
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    settings: Settings,
    direct: DirectState,
    ha: HaState,
    status: TvStatus,
    haTile: HaTile,
    canSend: Boolean,
    actions: RemoteActions,
) {
    AnimatedContent(
        targetState = Triple(settings.route, direct, ha),
        contentKey = { (route, d, h) -> route to (if (route == Route.HOME_ASSISTANT) h::class else d::class) },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "connection-card",
    ) { state ->
        val (route, direct, ha) = state
        when (route) {
            Route.DIRECT -> when (direct) {
                DirectState.NoTv -> Notice(
                    "Pick your TV",
                    "Find a Google TV or Android TV on your network and pair it.",
                    "Find TVs", actions.openDevices,
                )
                is DirectState.NeedsPairing -> Notice(
                    "Pair with ${direct.tv.name}",
                    "The TV will show a code. It only takes a moment.",
                    "Pair", actions.pair,
                )
                is DirectState.Offline -> Notice(
                    "Can't reach ${direct.tv.name}",
                    direct.message.ifEmpty { "Make sure the TV is on and on the same Wi-Fi." },
                    "Retry", actions.retry, busy = true,
                )
                is DirectState.Connecting -> Notice("Connecting to ${direct.tv.name}…", null, null, null, busy = true)
                is DirectState.Connected -> NowPlaying(status, actions, canSend)
            }
            Route.HOME_ASSISTANT -> when (ha) {
                HaState.Unconfigured -> Notice(
                    "Connect Home Assistant",
                    "Send buttons through your Android Debug Bridge entity in Home Assistant.",
                    "Set up", actions.openSettings,
                )
                is HaState.Failed -> Notice("Home Assistant problem", ha.message, "Settings", actions.openSettings)
                HaState.Checking -> Notice("Checking Home Assistant…", null, null, null, busy = true)
                is HaState.Ready -> Surface(shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column {
                        HaTileCard(haTile, ha.name, actions.refreshTile,
                            shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)))
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            status.volume?.let { Text("Volume ${it.level}", style = MaterialTheme.typography.labelLarge) }
                            PlaybackTimeline(status.playback, canSend, actions.seekTo)
                            MediaRow(actions, canSend)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(title: String, body: String?, action: String?, onAction: (() -> Unit)?, busy: Boolean = false) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                ContainedLoadingIndicator(Modifier.size(48.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (body != null) {
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            if (action != null && onAction != null) {
                Spacer(Modifier.width(12.dp))
                Button(onClick = onAction, shapes = ButtonDefaults.shapes()) { Text(action) }
            }
        }
    }
}

@Composable
private fun NowPlaying(status: TvStatus, actions: RemoteActions, enabled: Boolean) {
    val app = status.currentApp?.let { pkg -> QuickApps.firstOrNull { it.packageName == pkg }?.name ?: pkg.substringAfterLast('.') }
    val volume = status.volume
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SettingsRemote, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    when {
                        status.powerOn == false -> "TV is in standby"
                        app != null -> "Now on $app"
                        else -> "Ready"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (volume != null) {
                    Text(
                        if (volume.muted) "Muted" else "Volume ${volume.level}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            PlaybackTimeline(status.playback, enabled, actions.seekTo)
            MediaRow(actions, enabled)
        }
    }
}

@Composable
private fun TopKeys(actions: RemoteActions, enabled: Boolean, settings: Settings) {
    val useIrPower = settings.route == Route.HOME_ASSISTANT
    val irPowerConfigured = settings.ha.irPowerEntityId.isNotBlank()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val power = MaterialShapes.Cookie4Sided.toShape()
        RemoteIconButton(
            Icons.Rounded.PowerSettingsNew,
            if (useIrPower) "IR Power" else "Power",
            onPress = if (useIrPower) {
                if (irPowerConfigured) actions.irPower else actions.openSettings
            } else {
                { actions.press(TvKey.POWER) }
            },
            enabled = enabled || (useIrPower && !irPowerConfigured),
            shapes = IconButtonDefaults.shapes(power, power),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        )
        RemoteIconButton(Icons.AutoMirrored.Rounded.Input, "Input", { actions.press(TvKey.TV_INPUT) }, enabled = enabled)
        RemoteIconButton(Icons.Rounded.Language, "Browser", { actions.press(TvKey.ASSIST) }, enabled = enabled)
        RemoteIconButton(
            Icons.Rounded.Keyboard, "Keyboard", actions.openKeyboard, enabled = enabled,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        )
    }
}

@Composable
private fun DPad(actions: RemoteActions, enabled: Boolean) {
    val clear = IconButtonDefaults.filledIconButtonColors(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(280.dp),
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            val arrow = 76.dp
            RemoteIconButton(
                Icons.Rounded.KeyboardArrowUp, "Up", { actions.press(TvKey.DPAD_UP) },
                Modifier.align(Alignment.TopCenter), size = arrow, iconSize = 40.dp,
                hold = HoldBehavior.Repeat, colors = clear, enabled = enabled,
            )
            RemoteIconButton(
                Icons.Rounded.KeyboardArrowDown, "Down", { actions.press(TvKey.DPAD_DOWN) },
                Modifier.align(Alignment.BottomCenter), size = arrow, iconSize = 40.dp,
                hold = HoldBehavior.Repeat, colors = clear, enabled = enabled,
            )
            RemoteIconButton(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Left", { actions.press(TvKey.DPAD_LEFT) },
                Modifier.align(Alignment.CenterStart), size = arrow, iconSize = 40.dp,
                hold = HoldBehavior.Repeat, colors = clear, enabled = enabled,
            )
            RemoteIconButton(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Right", { actions.press(TvKey.DPAD_RIGHT) },
                Modifier.align(Alignment.CenterEnd), size = arrow, iconSize = 40.dp,
                hold = HoldBehavior.Repeat, colors = clear, enabled = enabled,
            )
            OkButton(actions, enabled, Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun OkButton(actions: RemoteActions, enabled: Boolean, modifier: Modifier) {
    val cookie = MaterialShapes.Cookie9Sided.toShape()
    val sunny = MaterialShapes.Sunny.toShape()
    RemoteButton(
        onPress = { actions.press(TvKey.DPAD_CENTER) },
        onLongPress = { actions.longPress(TvKey.DPAD_CENTER) },
        hold = HoldBehavior.LongPress,
        modifier = modifier,
        size = 112.dp,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(cookie, sunny),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text("OK", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun NavKeys(actions: RemoteActions, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WideKey(Icons.AutoMirrored.Rounded.ArrowBack, "Back", TvKey.BACK, actions, enabled, Modifier.weight(1f))
        WideKey(Icons.Rounded.Home, "Home", TvKey.HOME, actions, enabled, Modifier.weight(1f))
        WideKey(Icons.Rounded.Apps, "Apps", TvKey.ALL_APPS, actions, enabled, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WideKey(Icons.Rounded.Menu, "Menu", TvKey.MENU, actions, enabled, Modifier.weight(1f), small = true)
        WideKey(Icons.Rounded.ClosedCaption, "Captions", TvKey.CAPTIONS, actions, enabled, Modifier.weight(1f), small = true)
        WideKey(Icons.Rounded.Tune, "TV settings", TvKey.SETTINGS, actions, enabled, Modifier.weight(1f), small = true)
    }
}

@Composable
private fun WideKey(
    icon: ImageVector,
    label: String,
    key: TvKey,
    actions: RemoteActions,
    enabled: Boolean,
    modifier: Modifier,
    small: Boolean = false,
) {
    val height = if (small) 52.dp else 64.dp
    Box(modifier.height(height)) {
        RemoteIconButton(
            icon, label, { actions.press(key) },
            onLongPress = { actions.longPress(key) },
            hold = HoldBehavior.LongPress,
            modifier = Modifier.fillMaxWidth(),
            size = height,
            iconSize = if (small) 22.dp else 26.dp,
            enabled = enabled,
            shapes = IconButtonDefaults.shapes(RoundedCornerShape(height / 2), RoundedCornerShape(14.dp)),
            colors = if (small) {
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                IconButtonDefaults.filledTonalIconButtonColors()
            },
        )
    }
}

@Composable
private fun VolumeRow(status: TvStatus, route: Route, actions: RemoteActions, enabled: Boolean) {
    val muted = route == Route.DIRECT && status.volume?.muted == true
    val clear = IconButtonDefaults.filledIconButtonColors(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Rocker("Volume", Modifier.weight(1.5f)) {
            RemoteIconButton(
                Icons.AutoMirrored.Rounded.VolumeDown, "Volume down", { actions.press(TvKey.VOLUME_DOWN) },
                hold = HoldBehavior.Repeat, enabled = enabled, size = 56.dp,
            )
            RemoteIconButton(
                if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeMute,
                if (muted) "Unmute" else "Mute",
                { actions.press(TvKey.VOLUME_MUTE) },
                enabled = enabled, size = 48.dp, iconSize = 22.dp,
                colors = if (muted) {
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else clear,
            )
            RemoteIconButton(
                Icons.AutoMirrored.Rounded.VolumeUp, "Volume up", { actions.press(TvKey.VOLUME_UP) },
                hold = HoldBehavior.Repeat, enabled = enabled, size = 56.dp,
            )
        }
        Rocker("Channel", Modifier.weight(1f)) {
            RemoteIconButton(
                Icons.Rounded.KeyboardArrowDown, "Channel down", { actions.press(TvKey.CHANNEL_DOWN) },
                enabled = enabled, size = 56.dp, colors = clear,
            )
            RemoteIconButton(
                Icons.Rounded.KeyboardArrowUp, "Channel up", { actions.press(TvKey.CHANNEL_UP) },
                enabled = enabled, size = 56.dp, colors = clear,
            )
        }
    }
}

@Composable
private fun Rocker(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(34.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) { content() }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun MediaRow(actions: RemoteActions, enabled: Boolean) {
    val clear = IconButtonDefaults.filledIconButtonColors(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteIconButton(Icons.Rounded.SkipPrevious, "Previous", { actions.press(TvKey.MEDIA_PREVIOUS) }, size = 48.dp, colors = clear, enabled = enabled)
        RemoteIconButton(Icons.Rounded.FastRewind, "Rewind", { actions.press(TvKey.MEDIA_REWIND) }, size = 48.dp, hold = HoldBehavior.Repeat, enabled = enabled)
        RemoteIconButton(
            Icons.Rounded.PlayArrow, "Play or pause", { actions.press(TvKey.MEDIA_PLAY_PAUSE) },
            size = 64.dp, iconSize = 32.dp, enabled = enabled,
            shapes = IconButtonDefaults.shapes(CircleShape, RoundedCornerShape(20.dp)),
            colors = IconButtonDefaults.filledIconButtonColors(),
        )
        RemoteIconButton(Icons.Rounded.FastForward, "Fast forward", { actions.press(TvKey.MEDIA_FAST_FORWARD) }, size = 48.dp, hold = HoldBehavior.Repeat, enabled = enabled)
        RemoteIconButton(Icons.Rounded.SkipNext, "Next", { actions.press(TvKey.MEDIA_NEXT) }, size = 48.dp, colors = clear, enabled = enabled)
    }
}

@Composable
private fun QuickAppsRow(actions: RemoteActions, enabled: Boolean) {
    Section("Quick launch") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickApps.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { app ->
                        WideButton(app.name, app.icon, { actions.launchApp(app.packageName) }, enabled, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun WideButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    shapes: ButtonShapes = ButtonDefaults.shapes(),
) {
    val haptic = rememberHaptic()
    FilledTonalButton(
        onClick = {
            haptic(false)
            onClick()
        },
        enabled = enabled,
        shapes = shapes,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.height(56.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
