package io.github.tecix.textv.ui.screens

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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
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
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tecix.textv.DirectState
import io.github.tecix.textv.HaState
import io.github.tecix.textv.HaTile
import io.github.tecix.textv.TvKey
import io.github.tecix.textv.TvStatus
import io.github.tecix.textv.data.Route
import io.github.tecix.textv.data.Settings
import io.github.tecix.textv.ui.components.HoldBehavior
import io.github.tecix.textv.ui.components.RemoteButton
import io.github.tecix.textv.ui.components.RemoteIconButton
import io.github.tecix.textv.ui.components.rememberHaptic

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
    val smartTubeCaptions: () -> Unit,
    val smartTubeSpeed: () -> Unit,
    val refreshTile: () -> Unit,
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
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            settings.selectedTv?.name ?: "tex-TV Remote",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        StatusLine(settings.route, direct, ha)
                    }
                },
                actions = {
                    IconButton(onClick = actions.openDevices) { Icon(Icons.Rounded.Tv, "TVs") }
                    IconButton(onClick = actions.openSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
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
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                RouteSwitcher(settings.route, actions.setRoute)
                ConnectionCard(settings, direct, ha, status, haTile, actions)
                TopKeys(actions, canSend)
                DPad(actions, canSend)
                NavKeys(actions, canSend)
                VolumeRow(status, settings.route, actions, canSend)
                MediaRow(actions, canSend)
                SmartTubeRow(actions, canSend)
                QuickAppsRow(actions, canSend)
                Spacer(Modifier.height(8.dp))
            }
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
            is DirectState.Connected -> "Connected · ${direct.info.model}" to MaterialTheme.colorScheme.tertiary
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
private fun RouteSwitcher(route: Route, onChange: (Route) -> Unit) {
    val haptic = rememberHaptic()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        val options = listOf(
            Triple(Route.DIRECT, "Direct", Icons.Rounded.Cast),
            Triple(Route.HOME_ASSISTANT, "Home Assistant", Icons.Rounded.Home),
        )
        options.forEachIndexed { index, (value, label, icon) ->
            ToggleButton(
                checked = route == value,
                onCheckedChange = {
                    haptic(false)
                    onChange(value)
                },
                shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Icon(icon, null, Modifier.size(ToggleButtonDefaults.IconSize))
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(label, maxLines = 1)
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
    actions: RemoteActions,
) {
    val key: Any = if (settings.route == Route.HOME_ASSISTANT) ha::class else direct::class
    AnimatedContent(
        targetState = key,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "connection-card",
    ) { _ ->
        when (settings.route) {
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
                is DirectState.Connected -> NowPlaying(status, showVolume = true)
            }
            Route.HOME_ASSISTANT -> when (ha) {
                HaState.Unconfigured -> Notice(
                    "Connect Home Assistant",
                    "Send buttons through your Android Debug Bridge entity in Home Assistant.",
                    "Set up", actions.openSettings,
                )
                is HaState.Failed -> Notice("Home Assistant problem", ha.message, "Settings", actions.openSettings)
                HaState.Checking -> Notice("Checking Home Assistant…", null, null, null, busy = true)
                is HaState.Ready -> HaTileCard(haTile, ha.name, actions.refreshTile)
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
private fun NowPlaying(status: TvStatus, showVolume: Boolean) {
    val app = status.currentApp?.let { pkg -> QuickApps.firstOrNull { it.packageName == pkg }?.name ?: pkg.substringAfterLast('.') }
    val volume = status.volume
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                if (showVolume && volume != null) {
                    Text(
                        if (volume.muted) "Muted" else "${volume.level}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (showVolume && volume != null) {
                val progress by animateFloatAsState(
                    if (volume.muted) 0f else volume.level.toFloat() / volume.maximum.coerceAtLeast(1),
                    MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "volume",
                )
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TopKeys(actions: RemoteActions, enabled: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val power = MaterialShapes.Cookie4Sided.toShape()
        RemoteIconButton(
            Icons.Rounded.PowerSettingsNew, "Power", { actions.press(TvKey.POWER) },
            enabled = enabled,
            shapes = IconButtonDefaults.shapes(power, power),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        )
        RemoteIconButton(Icons.AutoMirrored.Rounded.Input, "Input", { actions.press(TvKey.TV_INPUT) }, enabled = enabled)
        RemoteIconButton(Icons.Rounded.Mic, "Assistant", { actions.press(TvKey.ASSIST) }, enabled = enabled)
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
        RemoteIconButton(Icons.Rounded.SkipPrevious, "Previous", { actions.press(TvKey.MEDIA_PREVIOUS) }, size = 52.dp, colors = clear, enabled = enabled)
        RemoteIconButton(Icons.Rounded.FastRewind, "Rewind", { actions.press(TvKey.MEDIA_REWIND) }, size = 56.dp, hold = HoldBehavior.Repeat, enabled = enabled)
        RemoteIconButton(
            Icons.Rounded.PlayArrow, "Play or pause", { actions.press(TvKey.MEDIA_PLAY_PAUSE) },
            size = 80.dp, iconSize = 40.dp, enabled = enabled,
            shapes = IconButtonDefaults.shapes(CircleShape, RoundedCornerShape(20.dp)),
            colors = IconButtonDefaults.filledIconButtonColors(),
        )
        RemoteIconButton(Icons.Rounded.FastForward, "Fast forward", { actions.press(TvKey.MEDIA_FAST_FORWARD) }, size = 56.dp, hold = HoldBehavior.Repeat, enabled = enabled)
        RemoteIconButton(Icons.Rounded.SkipNext, "Next", { actions.press(TvKey.MEDIA_NEXT) }, size = 52.dp, colors = clear, enabled = enabled)
    }
}

@Composable
private fun SmartTubeRow(actions: RemoteActions, enabled: Boolean) {
    Section("SmartTube") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            WideButton("Captions", Icons.Rounded.ClosedCaption, actions.smartTubeCaptions, enabled, Modifier.weight(1f),
                ButtonGroupDefaults.connectedLeadingButtonShapes().let { ButtonDefaults.shapes(it.shape, it.pressedShape) })
            WideButton("1.5× speed", Icons.Rounded.Speed, actions.smartTubeSpeed, enabled, Modifier.weight(1f),
                ButtonGroupDefaults.connectedTrailingButtonShapes().let { ButtonDefaults.shapes(it.shape, it.pressedShape) })
        }
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
