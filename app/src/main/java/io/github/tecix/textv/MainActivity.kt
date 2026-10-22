package io.github.tecix.textv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tecix.textv.data.Route
import io.github.tecix.textv.discovery.DiscoveredTv
import io.github.tecix.textv.ui.components.LocalHaptics
import io.github.tecix.textv.ui.screens.DevicesScreen
import io.github.tecix.textv.ui.screens.KeyboardSheet
import io.github.tecix.textv.ui.screens.PairingDialog
import io.github.tecix.textv.ui.screens.RemoteActions
import io.github.tecix.textv.ui.screens.RemoteScreen
import io.github.tecix.textv.ui.screens.SettingsScreen
import io.github.tecix.textv.ui.theme.TexTvTheme

private enum class Screen { Remote, Devices, Settings }

class MainActivity : ComponentActivity() {
    private val vm: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            TexTvTheme(dynamicColor = s.dynamicColor) {
                CompositionLocalProvider(LocalHaptics provides s.haptics) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        App(vm)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> TvKey.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> TvKey.VOLUME_DOWN
            else -> null
        }
        if (key != null && vm.settings.value?.volumeKeys == true && vm.canSend.value) {
            // Act on key down (including auto-repeat while held); swallow key up as well.
            if (event.action == KeyEvent.ACTION_DOWN) vm.press(key)
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

@androidx.compose.runtime.Composable
private fun App(vm: RemoteViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val direct by vm.direct.collectAsStateWithLifecycle()
    val ha by vm.ha.collectAsStateWithLifecycle()
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val haTile by vm.haTile.collectAsStateWithLifecycle()
    val canSend by vm.canSend.collectAsStateWithLifecycle()
    val s = settings ?: return

    var screen by rememberSaveable { mutableStateOf(Screen.Remote) }
    var keyboard by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it) }
    }
    BackHandler(enabled = screen != Screen.Remote) { screen = Screen.Remote }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = targetState != Screen.Remote
            (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
        },
        label = "screen",
    ) { target ->
        when (target) {
            Screen.Remote -> RemoteScreen(
                settings = s,
                direct = direct,
                ha = ha,
                status = status,
                haTile = haTile,
                canSend = canSend,
                snackbar = snackbar,
                actions = RemoteActions(
                    press = vm::press,
                    longPress = vm::longPress,
                    launchApp = vm::launchApp,
                    setRoute = vm::setRoute,
                    openDevices = { screen = Screen.Devices },
                    openSettings = { screen = Screen.Settings },
                    openKeyboard = { keyboard = true },
                    pair = { s.selectedTv?.let(vm::startPairing) },
                    retry = vm::retryDirect,
                    smartTubeCaptions = vm::smartTubeCaptions,
                    smartTubeSpeed = vm::smartTubeSpeed,
                    refreshTile = vm::refreshTile,
                ),
            )
            Screen.Devices -> {
                val discovered by remember { vm.discover() }.collectAsStateWithLifecycle(emptyList<DiscoveredTv>())
                DevicesScreen(
                    saved = s.tvs,
                    selectedHost = s.selectedHost,
                    discovered = discovered,
                    snackbar = snackbar,
                    onBack = { screen = Screen.Remote },
                    onSelect = { tv ->
                        vm.selectTv(tv)
                        if (s.route != Route.DIRECT) vm.setRoute(Route.DIRECT)
                        screen = Screen.Remote
                    },
                    onPair = { tv ->
                        vm.startPairing(tv)
                        screen = Screen.Remote
                    },
                    onRename = vm::renameTv,
                    onRemove = { vm.removeTv(it.host) },
                )
            }
            Screen.Settings -> SettingsScreen(
                settings = s,
                ha = ha,
                snackbar = snackbar,
                onBack = { screen = Screen.Remote },
                onSaveHa = vm::saveHa,
                onTestHa = vm::testHa,
                onVolumeKeys = { vm.setVolumeKeys(it) },
                onHaptics = { vm.setHaptics(it) },
                onDynamicColor = { vm.setDynamicColor(it) },
                onCaptionMacro = { vm.setCaptionMacro(it) },
                onForgetIdentity = vm::forgetIdentity,
            )
        }
    }

    if (keyboard) {
        KeyboardSheet(
            onDismiss = { keyboard = false },
            onText = vm::sendText,
            onKey = vm::press,
            onOpen = vm::resetText,
        )
    }

    PairingDialog(
        state = pairing,
        onSubmit = vm::submitPairingCode,
        onRetry = {
            (pairing as? PairingState.Failed)?.tv?.let(vm::startPairing)
        },
        onDismiss = vm::cancelPairing,
    )
}
