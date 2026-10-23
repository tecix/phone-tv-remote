package io.github.tecix.richtvremote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.os.Bundle
import android.content.Intent
import io.github.tecix.richtvremote.media.MediaShareService
import io.github.tecix.richtvremote.media.ShareState
import io.github.tecix.richtvremote.ui.screens.SharedMediaSheet
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
import io.github.tecix.richtvremote.data.Route
import io.github.tecix.richtvremote.discovery.DiscoveredTv
import io.github.tecix.richtvremote.ui.components.LocalHaptics
import io.github.tecix.richtvremote.ui.screens.DevicesScreen
import io.github.tecix.richtvremote.ui.screens.KeyboardSheet
import io.github.tecix.richtvremote.ui.screens.PairingDialog
import io.github.tecix.richtvremote.ui.screens.RemoteActions
import io.github.tecix.richtvremote.ui.screens.RemoteScreen
import io.github.tecix.richtvremote.ui.screens.SettingsScreen
import io.github.tecix.richtvremote.ui.theme.RichTvRemoteTheme

private enum class Screen { Remote, Devices, Settings }

class MainActivity : ComponentActivity() {
    private val vm: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) vm.receiveShare(intent)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            RichTvRemoteTheme(dynamicColor = s.dynamicColor) {
                CompositionLocalProvider(LocalHaptics provides s.haptics) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        App(vm)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.receiveShare(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }

    private fun volumeKey(keyCode: Int): TvKey? = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> TvKey.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> TvKey.VOLUME_DOWN
            else -> null
        }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = volumeKey(keyCode)
        if (key != null && vm.settings.value?.volumeKeys == true && vm.canSend.value) {
            vm.press(key) // Includes auto-repeat while held.
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (volumeKey(keyCode) != null && vm.settings.value?.volumeKeys == true && vm.canSend.value) return true
        return super.onKeyUp(keyCode, event)
    }
}

@androidx.compose.runtime.Composable
private fun App(vm: RemoteViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val direct by vm.direct.collectAsStateWithLifecycle()
    val ha by vm.ha.collectAsStateWithLifecycle()
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val mediaAccessRequired by vm.mediaAccessRequired.collectAsStateWithLifecycle()
    val haTile by vm.haTile.collectAsStateWithLifecycle()
    val canSend by vm.canSend.collectAsStateWithLifecycle()
    val incomingShare by vm.incomingShare.collectAsStateWithLifecycle()
    val sharedChoices by vm.sharedChoices.collectAsStateWithLifecycle()
    val shareBusy by vm.shareBusy.collectAsStateWithLifecycle()
    val shareMessage by vm.shareMessage.collectAsStateWithLifecycle()
    val mediaDestination by vm.mediaDestination.collectAsStateWithLifecycle()
    val renderers by vm.renderers.collectAsStateWithLifecycle()
    val renderer by vm.renderer.collectAsStateWithLifecycle()
    val discoveringRenderers by vm.discoveringRenderers.collectAsStateWithLifecycle()
    val sharing by MediaShareService.state.collectAsStateWithLifecycle()
    val streamingPlayback by vm.streamingPlayback.collectAsStateWithLifecycle()
    val stoppingStreaming by vm.stoppingStreaming.collectAsStateWithLifecycle()
    val s = settings ?: return

    var screen by rememberSaveable { mutableStateOf(Screen.Remote) }
    var voiceAutoStart by rememberSaveable { mutableStateOf(true) }
    var voice by rememberSaveable { mutableStateOf(false) }
    var keyboard by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.receiveShare(Intent(Intent.ACTION_VIEW).setDataAndType(it, "*/*")) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.playSharedMedia() // Sharing still works when notification permission is declined; Stop remains in the app.
    }

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
                mediaAccessRequired = mediaAccessRequired,
                sharingFile = sharing is ShareState.Serving || sharing is ShareState.Preparing || streamingPlayback != null || shareBusy || stoppingStreaming,
                stoppingStreaming = stoppingStreaming,
                dlnaReceiver = renderer,
                onStopSharing = vm::stopSharing,
                settings = s,
                direct = direct,
                ha = ha,
                status = status,
                haTile = haTile,
                canSend = canSend,
                snackbar = snackbar,
                actions = RemoteActions(
                    openVoice = { voiceAutoStart = true; voice = true },
                    pickMedia = { mediaPicker.launch(arrayOf("*/*")) },
                    disconnectDlna = vm::disconnectDlnaReceiver,
                    openVoiceText = { voiceAutoStart = false; voice = true },
                    seekTo = vm::seekTo,
                    setupMediaAccess = vm::setupMediaAccess,
                    press = vm::press,
                    longPress = vm::longPress,
                    launchApp = vm::launchApp,
                    setRoute = vm::setRoute,
                    openDevices = { screen = Screen.Devices },
                    openSettings = { screen = Screen.Settings },
                    openKeyboard = { keyboard = true },
                    pair = { s.selectedTv?.let(vm::startPairing) },
                    retry = vm::retryDirect,
                    runMacro = vm::runMacro,
                    saveMacro = { vm.saveMacro(it) },
                    deleteMacro = { vm.deleteMacro(it) },
                    refreshTile = vm::refreshTile,
                    irPower = vm::irPower,
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
                onSaveMacro = { vm.saveMacro(it) },
                onDeleteMacro = { vm.deleteMacro(it) },
                onForgetIdentity = vm::forgetIdentity,
            )
        }
    }

    incomingShare?.let { media ->
        SharedMediaSheet(media, s.route, canSend, shareBusy,
            onDismiss = vm::dismissShare, onPlay = {
                if (media.isFile && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else vm.playSharedMedia()
            }, message = shareMessage, destination = mediaDestination,
            onDestinationChange = vm::setMediaDestination,
            renderers = renderers, renderer = renderer, discovering = discoveringRenderers,
            onDiscover = { vm.discoverRenderers(force = true) }, onRendererChange = vm::selectRenderer,
            choices = sharedChoices, onMediaChange = vm::selectSharedMedia)
    }

    if (voice) {
        io.github.tecix.richtvremote.ui.screens.VoiceSearchSheet(
            route = s.route.name,
            startWithVoice = voiceAutoStart,
            onListeningChange = vm::setRecognizingSpeech,
            preferredLanguage = s.speechLanguage,
            onLanguageChange = { vm.setSpeechLanguage(it) },
            canSend = canSend,
            onDismiss = { voice = false },
            onSend = vm::searchSmartTube,
        )
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
