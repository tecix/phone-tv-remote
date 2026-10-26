package io.github.tecix.richtvremote

import io.github.tecix.richtvremote.voice.VoiceAction
import io.github.tecix.richtvremote.voice.VoiceCommand
import io.github.tecix.richtvremote.voice.SmartTubeLink
import android.app.Application
import android.content.Intent
import android.content.ClipData
import androidx.core.content.ContextCompat
import io.github.tecix.richtvremote.media.SharedMedia
import io.github.tecix.richtvremote.media.MediaShareService
import io.github.tecix.richtvremote.media.ShareState
import io.github.tecix.richtvremote.media.DlnaController
import io.github.tecix.richtvremote.media.DlnaRenderer
import io.github.tecix.richtvremote.media.MediaDestination
import io.github.tecix.richtvremote.media.replaceMediaTransfer
import io.github.tecix.richtvremote.media.MediaStreaming
import io.github.tecix.richtvremote.media.SharedPlayback
import io.github.tecix.richtvremote.media.AirPlayReceiver
import io.github.tecix.richtvremote.media.AirPlaySession
import kotlinx.coroutines.flow.first
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tecix.richtvremote.data.Route
import io.github.tecix.richtvremote.data.MacroAction
import io.github.tecix.richtvremote.data.SavedTv
import io.github.tecix.richtvremote.data.Settings
import io.github.tecix.richtvremote.data.SettingsRepository
import io.github.tecix.richtvremote.direct.AndroidTvRemote
import io.github.tecix.richtvremote.direct.ClientIdentity
import io.github.tecix.richtvremote.direct.DeviceInfo
import io.github.tecix.richtvremote.direct.NotPairedException
import io.github.tecix.richtvremote.direct.PairingException
import io.github.tecix.richtvremote.direct.PairingSession
import io.github.tecix.richtvremote.direct.VolumeInfo
import io.github.tecix.richtvremote.discovery.DiscoveredTv
import io.github.tecix.richtvremote.discovery.discoverTvs
import io.github.tecix.richtvremote.ha.HaConfig
import io.github.tecix.richtvremote.ha.HomeAssistantException
import io.github.tecix.richtvremote.ha.HomeAssistantRemote
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.tecix.richtvremote.ha.HomeAssistantStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DirectState {
    data object NoTv : DirectState
    data class Connecting(val tv: SavedTv) : DirectState
    data class Connected(val tv: SavedTv, val info: DeviceInfo) : DirectState
    data class NeedsPairing(val tv: SavedTv) : DirectState
    data class Offline(val tv: SavedTv, val message: String) : DirectState
}

sealed interface HaState {
    data object Unconfigured : HaState
    data object Checking : HaState
    data class Ready(val name: String) : HaState
    data class Failed(val message: String) : HaState
}

sealed interface PairingState {
    data object Hidden : PairingState
    data class Starting(val tv: SavedTv) : PairingState
    data class EnterCode(val tv: SavedTv, val tvName: String, val error: String? = null) : PairingState
    data class Verifying(val tv: SavedTv, val tvName: String) : PairingState
    data class Failed(val tv: SavedTv, val message: String) : PairingState
}

/** The Home Assistant tile: current app, a screenshot of the TV and when it booted. */
data class HaTile(
    val appName: String? = null,
    val screenshot: ImageBitmap? = null,
    val bootedAtMillis: Long? = null,
    val standby: Boolean = false,
)

data class TvStatus(
    val volume: VolumeInfo? = null,
    val powerOn: Boolean? = null,
    val currentApp: String? = null,
    val playback: io.github.tecix.richtvremote.media.MediaPlayback? = null,
)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)
    private val streaming = MediaStreaming.controller(app)
    private val dlnaSession = MediaStreaming.dlnaSession
    internal val streamingPlayback = streaming.playback
    val stoppingStreaming = streaming.stopping
    private val clientName = "${app.getString(R.string.app_name)} (${Build.MODEL})"

    val settings: StateFlow<Settings?> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _direct = MutableStateFlow<DirectState>(DirectState.NoTv)
    val direct = _direct.asStateFlow()

    private val _ha = MutableStateFlow<HaState>(HaState.Unconfigured)
    val ha = _ha.asStateFlow()

    private val _pairing = MutableStateFlow<PairingState>(PairingState.Hidden)
    val pairing = _pairing.asStateFlow()

    private val _status = MutableStateFlow(TvStatus())
    val status = _status.asStateFlow()
    private val _mediaAccessRequired = MutableStateFlow(false)
    val mediaAccessRequired = _mediaAccessRequired.asStateFlow()

    private val _haTile = MutableStateFlow(HaTile())
    val haTile = _haTile.asStateFlow()

    private val _incomingShare = MutableStateFlow<SharedMedia?>(null)
    val incomingShare = _incomingShare.asStateFlow()
    private val _sharedChoices = MutableStateFlow<List<SharedMedia>>(emptyList())
    val sharedChoices = _sharedChoices.asStateFlow()
    fun selectSharedMedia(media: SharedMedia) {
        if (!_shareBusy.value && media in _sharedChoices.value) {
            _incomingShare.value = media
            if (media.mime.startsWith("image/")) setMediaDestination(MediaDestination.DLNA)
        }
    }
    private val _shareMessage = MutableStateFlow("")
    val shareMessage = _shareMessage.asStateFlow()
    private val _shareBusy = MutableStateFlow(false)
    val shareBusy = _shareBusy.asStateFlow()
    private var shareJob: Job? = null
    private var shareGeneration = 0L
    private var autoPlayReplacement = false
    private var sharedPlayback: SharedPlayback?
        get() = streaming.playback.value
        set(value) { streaming.setPlayback(value) }
    private var discoveryJob: Job? = null
    private val _mediaDestination = MutableStateFlow(sharedPlayback?.destination ?: MediaDestination.AIRPLAY)
    val mediaDestination = _mediaDestination.asStateFlow()
    val renderers = dlnaSession.receivers
    val renderer = dlnaSession.selected
    private var receiverRecoveryAttempts = 0
    private val _discoveringRenderers = MutableStateFlow(false)
    val discoveringRenderers = _discoveringRenderers.asStateFlow()
    val airPlayReceivers = AirPlaySession.receivers
    val airPlayReceiver = AirPlaySession.selected
    val airPlay = AirPlaySession.nowPlaying
    val pendingCaptions = AirPlaySession.pendingCaptions

    private fun destinationReady(destination: MediaDestination) = when (destination) {
        MediaDestination.DLNA -> renderer.value != null
        MediaDestination.AIRPLAY -> airPlayReceiver.value != null
        MediaDestination.VLC -> true
    }

    fun selectAirPlayReceiver(receiver: AirPlayReceiver) {
        if (_shareBusy.value) return
        AirPlaySession.select(receiver)
        if (autoPlayReplacement) playSharedMedia()
    }

    /** Finds AirPlay receivers (TinyTV's own on the selected TV first). Shares the sheet's progress state with DLNA. */
    fun discoverAirPlay(force: Boolean = false) {
        if (!force && airPlayReceiver.value != null) {
            if (autoPlayReplacement) playSharedMedia()
            return
        }
        if (_discoveringRenderers.value || _shareBusy.value) return
        _discoveringRenderers.value = true
        _shareMessage.value = ""
        discoveryJob = viewModelScope.launch {
            try {
                val tv = settings.value?.selectedTv
                val found = AirPlaySession.discover(getApplication(), tv?.host, tv?.name)
                if (found.isEmpty()) _shareMessage.value = "No AirPlay receiver found. Update TinyTV Receiver on the TV " +
                    "(its AirPlay switch must be on) and check that both devices are on the same Wi-Fi."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _shareMessage.value = e.message ?: "AirPlay discovery failed" }
            finally {
                if (discoveryJob == kotlinx.coroutines.currentCoroutineContext()[Job]) {
                    _discoveringRenderers.value = false
                    if (autoPlayReplacement && airPlayReceiver.value != null) playSharedMedia()
                }
            }
        }
    }

    // ── AirPlay playback controls ───────────────────────────────────────────

    private fun airPlayCommand(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { messages.trySend("AirPlay: ${e.message ?: "command failed"}") }
        }
    }

    fun airPlaySetPlaying(playing: Boolean) = airPlayCommand { AirPlaySession.setPlaying(playing) }
    fun airPlaySeek(positionMs: Long) = airPlayCommand { AirPlaySession.seek(positionMs) }
    fun airPlaySkip(deltaMs: Long) = airPlayCommand { AirPlaySession.skip(deltaMs) }
    fun airPlaySelectCaptions(index: Int) = airPlayCommand { AirPlaySession.selectCaptions(index) }
    fun airPlayShowCaptions(show: Boolean) = airPlayCommand { AirPlaySession.showCaptions(show) }
    fun airPlayCaptionsDelay(deltaMs: Long) = airPlayCommand { AirPlaySession.addCaptionsDelay(deltaMs) }
    fun addSubtitleUrl(url: String) = airPlayCommand {
        AirPlaySession.addSubtitleUrl(url.trim())
        messages.trySend(if (AirPlaySession.nowPlaying.value != null) "Subtitle sent to the TV" else "Subtitle will be sent with the video")
    }
    fun addSubtitleFile(uri: android.net.Uri) = airPlayCommand {
        AirPlaySession.addSubtitleFile(getApplication(), uri)
        messages.trySend(if (AirPlaySession.nowPlaying.value != null) "Subtitle sent to the TV" else "Subtitle will be sent with the video")
    }
    fun removePendingCaption(subtitle: io.github.tecix.richtvremote.media.ServedSubtitle) = AirPlaySession.removePendingCaption(subtitle)

    /** Stop from the AirPlay card: the shared-file stop path also closes the phone's file server. */
    fun airPlayStop() {
        if (sharedPlayback?.destination == MediaDestination.AIRPLAY) stopSharing()
        else airPlayCommand { AirPlaySession.stop() }
    }

    fun setMediaDestination(destination: MediaDestination) {
        if (_shareBusy.value) return
        _mediaDestination.value = destination
        _shareMessage.value = ""
        if (destination == MediaDestination.DLNA) discoverRenderers()
        else if (destination == MediaDestination.AIRPLAY) discoverAirPlay()
        else if (autoPlayReplacement) playSharedMedia()
    }

    fun selectRenderer(renderer: DlnaRenderer) {
        if (!_shareBusy.value) {
            dlnaSession.select(renderer)
            if (autoPlayReplacement) playSharedMedia()
        }
    }

    fun disconnectDlnaReceiver() {
        dlnaSession.disconnect()
        discoveryJob?.cancel()
        _discoveringRenderers.value = false
        if (sharedPlayback?.destination == MediaDestination.DLNA ||
            (mediaDestination.value == MediaDestination.DLNA && (_shareBusy.value || autoPlayReplacement))) stopSharing()
        autoPlayReplacement = false
    }

    fun discoverRenderers(force: Boolean = false) {
        if (!force && renderer.value != null) {
            if (autoPlayReplacement) playSharedMedia()
            return
        }
        if (_discoveringRenderers.value || _shareBusy.value) return
        _discoveringRenderers.value = true
        _shareMessage.value = ""
        discoveryJob = viewModelScope.launch {
            try {
                val found = dlnaSession.find(settings.value?.selectedHost, force = force) {
                    // Existing paired AppLink handling in TinyTV launches the TV app, whose saved DLNA setting starts its receiver.
                    kotlinx.coroutines.withTimeoutOrNull(3000) { canSend.first { it } }
                    val route = backend()
                    val started = route != null && runCatching { route.launchApp("com.softmedia.receiver") }.isSuccess
                    if (started) delay(1000)
                    var found = DlnaController.discover()
                    if (started && (if (settings.value?.selectedHost == null) found.isEmpty()
                        else found.none { it.host == settings.value?.selectedHost })) {
                        delay(1000)
                        found = (found + DlnaController.discover()).distinctBy { it.controlUrl }
                    }
                    found
                }
                if (found.isEmpty()) _shareMessage.value = "No DLNA receiver found. Open AirReceiver on the TV, enable DLNA, and check that both devices are on the same Wi-Fi."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _shareMessage.value = e.message ?: "DLNA discovery failed" }
            finally {
                if (discoveryJob == kotlinx.coroutines.currentCoroutineContext()[Job]) {
                    _discoveringRenderers.value = false
                    if (autoPlayReplacement && renderer.value != null) playSharedMedia()
                }
            }
        }
    }

    fun receiveShare(intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW)) return
        val files = runCatching { SharedMedia.allFrom(intent, getApplication<Application>().contentResolver) }
            .getOrElse { messages.trySend(it.message ?: "Could not read shared media"); return }
        if (files.isEmpty()) return
        receiverRecoveryAttempts = 0
        val replacing = sharedPlayback != null || MediaShareService.running.value || _shareBusy.value || streaming.stopping.value
        dismissShare()
        _shareMessage.value = ""
        _sharedChoices.value = files
        _incomingShare.value = files.first()
        if (files.first().mime.startsWith("image/")) _mediaDestination.value = MediaDestination.DLNA
        autoPlayReplacement = replacing
        streaming.bindPending(shareJob, ::dismissShare)
        if (replacing) {
            stopFileSharing()
            if (destinationReady(mediaDestination.value)) playSharedMedia()
            else if (mediaDestination.value == MediaDestination.AIRPLAY) discoverAirPlay()
            else discoverRenderers()
        } else if (mediaDestination.value == MediaDestination.DLNA) discoverRenderers()
        else if (mediaDestination.value == MediaDestination.AIRPLAY) discoverAirPlay()
    }

    fun dismissShare() {
        ++shareGeneration
        autoPlayReplacement = false
        shareJob?.cancel()
        discoveryJob?.cancel()
        _discoveringRenderers.value = false
        if (_shareBusy.value) {
            stopFileSharing()
        }
        _incomingShare.value = null
        _sharedChoices.value = emptyList()
        _shareBusy.value = false
        AirPlaySession.clearPendingCaptions()
    }

    private fun stopFileSharing() {
        getApplication<Application>().stopService(Intent(getApplication(), MediaShareService::class.java))
        MediaShareService.cancelPreparing()
    }

    fun stopSharing() { streaming.requestStop() }

    private suspend fun stopPreviousShare() = streaming.stopForReplacement()

    fun playSharedMedia() {
        val media = _incomingShare.value ?: return
        if (_shareBusy.value) return
        _shareMessage.value = ""
        val destination = mediaDestination.value
        val renderer = renderer.value
        val airplay = airPlayReceiver.value
        if (destination == MediaDestination.DLNA && renderer == null) {
            _shareMessage.value = "Choose a DLNA receiver first"
            return
        }
        if (destination == MediaDestination.AIRPLAY && airplay == null) {
            _shareMessage.value = "Choose an AirPlay receiver first"
            return
        }
        val original = settings.value ?: return
        val previousJob = shareJob
        val generation = ++shareGeneration
        autoPlayReplacement = false
        _shareBusy.value = true
        shareJob = viewModelScope.launch {
            var startedFileShare = false
            var recoverReceiver = false
            try {
                replaceMediaTransfer(previousJob, ::stopPreviousShare) {
                    if (destination == MediaDestination.VLC) {
                        check(kotlinx.coroutines.withTimeoutOrNull(5000) { canSend.first { it } } == true) { "Connect to the TV to play in VLC" }
                        if (media.isFile) check(backend()?.supportsMedia == true) { "Update TinyTV Receiver for VLC sharing, or select Home Assistant" }
                    }
                    suspend fun play(url: String, mime: String): Boolean {
                        // Record before sending, so a newer request can stop even a command cancelled in flight.
                        sharedPlayback = SharedPlayback(destination, renderer, original, airplay)
                        if (destination == MediaDestination.VLC) return openMedia(url, mime)
                        if (destination == MediaDestination.AIRPLAY) {
                            AirPlaySession.play(airplay!!, url, media.title, if (media.isFile) emptyMap() else media.headers)
                            messages.trySend("Playing over AirPlay · ${airplay.name}")
                            return true
                        }
                        DlnaController.play(renderer!!, url, mime, media.title)
                        messages.trySend("DLNA playback requested · ${renderer.name}")
                        return true
                    }
                    if (!media.isFile) {
                        if (play(media.uri.toString(), media.mime)) _incomingShare.value = null
                        return@replaceMediaTransfer
                    }
                    val app = getApplication<Application>()
                    startedFileShare = true
                    if (destination == MediaDestination.AIRPLAY) {
                        sharedPlayback = SharedPlayback(destination, renderer, original, airplay)
                        AirPlaySession.preparing(airplay!!, media.title)
                    }
                    MediaShareService.markPreparing()
                    ContextCompat.startForegroundService(app, Intent(app, MediaShareService::class.java)
                        .setDataAndType(media.uri, media.mime)
                        .apply {
                            if (destination == MediaDestination.AIRPLAY) {
                                putExtra(MediaShareService.AIRPLAY_HOST, airplay!!.host)
                                putExtra(MediaShareService.AIRPLAY_PORT, airplay.port)
                            }
                            clipData = ClipData.newUri(app.contentResolver, "Shared media", media.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    val result = MediaShareService.state.first { it !is ShareState.Preparing }
                    when (result) {
                        is ShareState.Failed -> { _shareMessage.value = result.message; messages.trySend(result.message) }
                        is ShareState.Serving -> {
                            val current = settings.value
                            check(destination != MediaDestination.VLC ||
                                (current?.route == original.route && current.selectedHost == original.selectedHost && current.ha == original.ha)) {
                                "TV or connection changed. Share the media again."
                            }
                            if (play(result.url, result.mime)) _incomingShare.value = null else stopFileSharing()
                        }
                        else -> { _shareMessage.value = "Sharing stopped" }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == shareGeneration) {
                    if (startedFileShare) stopFileSharing()
                    _shareMessage.value = e.message ?: "Could not share this media"
                    messages.trySend(_shareMessage.value)
                    if (destination == MediaDestination.DLNA && e is java.io.IOException && receiverRecoveryAttempts == 0) {
                        receiverRecoveryAttempts++
                        dlnaSession.disconnect()
                        autoPlayReplacement = true
                        recoverReceiver = true
                        _shareMessage.value = "DLNA receiver disconnected. Finding it again…"
                    }
                }
            }
            finally {
                if (generation == shareGeneration) {
                    _shareBusy.value = false
                    if (recoverReceiver) discoverRenderers(force = true)
                }
            }
        }
        streaming.bindPending(shareJob, ::dismissShare)
    }

    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    private var remote: AndroidTvRemote? = null
    private var haRemote: HomeAssistantRemote? = null
    private var pairingSession: PairingSession? = null
    private var connectJob: Job? = null
    private var tileJob: Job? = null
    private var mediaJob: Job? = null
    private var foreground = false
    private var recognizingSpeech = false
    private var lastHaError = 0L

    // Set when the user picks a TV, so an unpaired one goes straight to pairing.
    private var pairWhenNeeded = false

    init {
        viewModelScope.launch {
            AirPlaySession.ended.collect { message ->
                if (message != null) {
                    messages.trySend(message)
                    AirPlaySession.consumeEnded()
                }
            }
        }
        viewModelScope.launch {
            streaming.result.collect { result ->
                if (result != null) messages.trySend(result.error?.let { "Could not fully stop streaming: $it" }
                    ?: "Streaming stop completed.")
            }
        }
        viewModelScope.launch {
            repo.settings.map { it.selectedTv }.distinctUntilChanged().collect { tv ->
                if (foreground) connectDirect(tv) else _direct.value = tv?.let { DirectState.Offline(it, "") } ?: DirectState.NoTv
            }
        }
        viewModelScope.launch {
            repo.settings.map { it.ha }.distinctUntilChanged().collect(::rebuildHa)
        }
        viewModelScope.launch {
            // A new HA config needs a new poller, so restart rather than keep the running one.
            repo.settings.distinctUntilChanged { a, b -> a.route == b.route && a.ha == b.ha }.collect { s ->
                tileJob?.cancel()
                tileJob = null
                updateTilePolling(s)
            }
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    fun onForeground() {
        foreground = true
        updateTilePolling()
        val state = _direct.value
        if (state !is DirectState.Connected && state !is DirectState.Connecting) {
            connectDirect(settings.value?.selectedTv)
        }
    }

    fun onBackground() {
        foreground = false
        updateTilePolling()
        // The recognizer is a temporary phone activity; keep the paired route ready for its result.
        if (recognizingSpeech) return
        connectJob?.cancel()
        dropRemote()
        _direct.update { s ->
            when (s) {
                is DirectState.Connected -> DirectState.Offline(s.tv, "")
                is DirectState.Connecting -> DirectState.Offline(s.tv, "")
                else -> s
            }
        }
    }

    fun setRecognizingSpeech(active: Boolean) { recognizingSpeech = active }

    override fun onCleared() {
        dropRemote()
        haRemote?.close()
        pairingSession?.close()
    }

    // ── Route ───────────────────────────────────────────────────────────────

    fun setRoute(route: Route) {
        remote?.resetText()
        _status.update { it.copy(playback = null) }
        _mediaAccessRequired.value = false
        viewModelScope.launch { repo.setRoute(route) }
        if (route == Route.HOME_ASSISTANT && !(settings.value?.ha?.isComplete ?: false)) {
            messages.trySend("Set up Home Assistant in Settings first")
        }
    }

    private fun backend(): RemoteBackend? = selectBackend(
        settings.value?.route ?: Route.DIRECT, remote?.takeIf { it.connected }, haRemote,
    )

    val canSend: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(settings, _direct, _ha) { s, d, h ->
        when (s?.route) {
            Route.HOME_ASSISTANT -> h is HaState.Ready || h is HaState.Checking
            else -> d is DirectState.Connected
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ── Commands ────────────────────────────────────────────────────────────

    fun press(key: TvKey) = withBackend { it.press(key) }

    fun irPower() {
        if (settings.value?.route != Route.HOME_ASSISTANT) return
        withBackend { (it as HomeAssistantRemote).irPower() }
    }

    fun longPress(key: TvKey) = withBackend { it.longPress(key) }

    fun sendText(text: String) = withBackend { it.sendText(text) }

    fun resetText() = withBackend { it.resetText() }

    fun launchApp(packageName: String) = withBackend { it.launchApp(packageName) }

    fun seekTo(positionMs: Long) {
        val p = _status.value.playback
        if (p == null || !p.canSeek || positionMs !in 0..p.durationMs) {
            messages.trySend("Playback seeking is unavailable")
            return
        }
        withBackend { it.seekTo(positionMs, p.sessionId) }
    }

    fun setupMediaAccess() = withBackend { it.setupMediaAccess() }

    fun openMedia(uri: String, mime: String): Boolean {
        return try {
            val b = backend() ?: error("Connect to the selected TV first")
            b.openMedia(uri, mime)
            messages.trySend("VLC command queued · check the TV")
            true
        } catch (e: Exception) { _shareMessage.value = e.message ?: "Could not open media"; messages.trySend(_shareMessage.value); false }
    }

    fun searchSmartTube(query: String, play: Boolean): Boolean {
        val result = dispatchSmartTube(
            settings.value?.route ?: Route.DIRECT, query, play,
            remote?.takeIf { it.connected }, haRemote,
        )
        messages.trySend(result.exceptionOrNull()?.message ?: "Command queued · playback is not confirmed")
        return result.isSuccess
    }

    fun runMacro(id: String) {
        val action = settings.value?.macroActions?.firstOrNull { it.id == id } ?: return
        val keys = TvKey.parseMacro(action.sequence)
        if (keys == null) {
            messages.trySend("The key sequence for ${action.name} isn't valid")
            return
        }
        withBackend { it.pressSequence(keys) }
    }

    fun saveMacro(action: MacroAction) = viewModelScope.launch { repo.saveMacro(action) }

    fun deleteMacro(id: String) = viewModelScope.launch { repo.deleteMacro(id) }

    private inline fun withBackend(action: (RemoteBackend) -> Unit) {
        val backend = backend()
        if (backend == null) {
            messages.trySend(
                if (settings.value?.route == Route.HOME_ASSISTANT) "Home Assistant isn't set up" else "Not connected to a TV"
            )
            return
        }
        try {
            action(backend)
        } catch (e: Exception) {
            messages.trySend(e.message ?: "Command failed")
        }
    }

    // ── Direct connection ───────────────────────────────────────────────────

    fun retryDirect() = connectDirect(settings.value?.selectedTv)

    private fun connectDirect(tv: SavedTv?) {
        connectJob?.cancel()
        dropRemote()
        _status.value = TvStatus()
        if (tv == null) {
            _direct.value = DirectState.NoTv
            return
        }
        connectJob = viewModelScope.launch {
            var attempt = 0
            while (true) {
                _direct.value = DirectState.Connecting(tv)
                val r = AndroidTvRemote(ClientIdentity.get(getApplication()), tv.host, clientName)
                val result = withContext(Dispatchers.IO) { runCatching { r.connect() } }
                result.onSuccess { info ->
                    pairWhenNeeded = false
                    remote = r
                    r.listener = listenerFor(r, tv)
                    _direct.value = DirectState.Connected(tv, info)
                    r.probeMedia()
                    mediaJob = viewModelScope.launch {
                        while (remote === r && foreground) {
                            if (settings.value?.route == Route.DIRECT) r.requestMediaStatus()
                            delay(1_000)
                        }
                    }
                    return@launch
                }
                when (val e = result.exceptionOrNull()) {
                    is NotPairedException -> {
                        _direct.value = DirectState.NeedsPairing(tv)
                        if (pairWhenNeeded) {
                            pairWhenNeeded = false
                            startPairing(tv)
                        }
                        return@launch
                    }
                    else -> _direct.value = DirectState.Offline(tv, e?.message ?: "Connection failed")
                }
                // The TV may be asleep or rebooting: keep trying, more slowly over time.
                delay(listOf(2_000L, 4_000L, 8_000L, 15_000L)[attempt.coerceAtMost(3)])
                attempt++
            }
        }
    }

    private fun listenerFor(r: AndroidTvRemote, tv: SavedTv) = object : AndroidTvRemote.Listener {
        override fun onDisconnected(error: Throwable?) {
            viewModelScope.launch {
                if (remote !== r) return@launch
                remote = null
                _direct.value = DirectState.Offline(tv, error?.message ?: "Disconnected")
                if (foreground) {
                    delay(1_000)
                    if (remote == null && settings.value?.selectedTv == tv) connectDirect(tv)
                }
            }
        }

        override fun onPlayback(playback: io.github.tecix.richtvremote.media.MediaPlayback?) {
            if (remote === r && settings.value?.route == Route.DIRECT) _status.update { it.copy(playback = playback) }
        }

        override fun onMediaAccessRequired(required: Boolean) {
            if (remote === r && settings.value?.route == Route.DIRECT) _mediaAccessRequired.value = required
        }

        override fun onCommandError(message: String) { messages.trySend("Direct: $message") }

        override fun onVolume(volume: VolumeInfo) = _status.update { it.copy(volume = volume) }

        override fun onPower(on: Boolean) = _status.update { it.copy(powerOn = on) }

        override fun onCurrentApp(packageName: String) = _status.update { it.copy(currentApp = packageName) }
    }

    private fun dropRemote() {
        mediaJob?.cancel()
        mediaJob = null
        val r = remote ?: return
        remote = null
        r.listener = null
        viewModelScope.launch(Dispatchers.IO) { r.disconnect() }
    }

    // ── TVs ─────────────────────────────────────────────────────────────────

    fun discover(): kotlinx.coroutines.flow.Flow<List<DiscoveredTv>> = discoverTvs(getApplication())

    fun selectTv(tv: SavedTv) {
        val alreadySelected = settings.value?.selectedHost == tv.host
        pairWhenNeeded = true
        viewModelScope.launch {
            repo.saveTv(tv, select = true)
            if (alreadySelected && _direct.value !is DirectState.Connected) connectDirect(tv)
        }
    }

    fun removeTv(host: String) {
        viewModelScope.launch { repo.removeTv(host) }
    }

    fun renameTv(tv: SavedTv, name: String) {
        viewModelScope.launch { repo.saveTv(tv.copy(name = name.trim().ifEmpty { tv.host }), select = false) }
    }

    // ── Pairing ─────────────────────────────────────────────────────────────

    fun startPairing(tv: SavedTv) {
        pairingSession?.close()
        connectJob?.cancel()
        dropRemote()
        _pairing.value = PairingState.Starting(tv)
        viewModelScope.launch {
            val session = PairingSession(ClientIdentity.get(getApplication()), tv.host, clientName)
            pairingSession = session
            withContext(Dispatchers.IO) { runCatching { session.start() } }
                .onSuccess { name -> _pairing.value = PairingState.EnterCode(tv, name) }
                .onFailure { e -> _pairing.value = PairingState.Failed(tv, e.message ?: "Pairing failed") }
        }
    }

    fun submitPairingCode(code: String) {
        val state = _pairing.value as? PairingState.EnterCode ?: return
        val session = pairingSession ?: return
        _pairing.value = PairingState.Verifying(state.tv, state.tvName)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { session.finish(code) } }
                .onSuccess {
                    pairingSession = null
                    _pairing.value = PairingState.Hidden
                    messages.trySend("Paired with ${state.tvName}")
                    repo.saveTv(state.tv, select = true)
                    connectDirect(state.tv)
                }
                .onFailure { e ->
                    val message = e.message ?: "Pairing failed"
                    _pairing.value = if (e is PairingException && session.isOpen) {
                        PairingState.EnterCode(state.tv, state.tvName, message)
                    } else {
                        PairingState.Failed(state.tv, message)
                    }
                }
        }
    }

    fun cancelPairing() {
        pairingSession?.close()
        pairingSession = null
        _pairing.value = PairingState.Hidden
        if (foreground && _direct.value !is DirectState.NeedsPairing) retryDirect()
    }

    fun forgetIdentity() {
        dropRemote()
        ClientIdentity.reset(getApplication())
        messages.trySend("Pairing identity reset. Pair your TVs again.")
        retryDirect()
    }

    // ── Home Assistant ──────────────────────────────────────────────────────

    private fun rebuildHa(config: HaConfig) {
        haRemote?.close()
        haRemote = null
        if (!config.isComplete) {
            _ha.value = HaState.Unconfigured
            return
        }
        val client = try {
            HomeAssistantRemote(config, onError = ::onHaError)
        } catch (e: HomeAssistantException) {
            _ha.value = HaState.Failed(e.message ?: "Invalid settings")
            return
        }
        haRemote = client
        _ha.value = HaState.Checking
        client.check { result ->
            if (haRemote !== client) return@check
            _ha.value = result.fold({ HaState.Ready(it) }, { HaState.Failed(it.message ?: "Check failed") })
        }
    }

    private fun onHaError(message: String) {
        // One message per burst: a held key would otherwise stack many.
        val now = System.currentTimeMillis()
        if (now - lastHaError > 5_000) {
            lastHaError = now
            messages.trySend("Home Assistant: $message")
        }
    }

    fun saveHa(config: HaConfig) {
        viewModelScope.launch {
            repo.setHa(config)
            // Saving unchanged settings doesn't emit; check again anyway.
            if (settings.value?.ha == config.trimmed()) rebuildHa(config.trimmed())
        }
    }

    /** Check a config without saving it. */
    suspend fun testHa(config: HaConfig): Result<String> {
        val client = try {
            HomeAssistantRemote(config) {}
        } catch (e: HomeAssistantException) {
            return Result.failure(e)
        }
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            client.check { result ->
                client.close()
                cont.resumeWith(Result.success(result))
            }
        }
    }

    // ── Home Assistant tile ─────────────────────────────────────────────────

    private fun updateTilePolling(s: Settings? = settings.value) {
        val wanted = foreground && s?.route == Route.HOME_ASSISTANT && s.ha.isComplete
        if (!wanted) {
            tileJob?.cancel()
            tileJob = null
            return
        }
        if (tileJob?.isActive == true) return
        val config = s!!.ha
        tileJob = viewModelScope.launch(Dispatchers.IO) {
            val status = runCatching { HomeAssistantStatus(config) }.getOrNull() ?: return@launch
            var lastUptimeCheck = 0L
            while (isActive) {
                runCatching { status.snapshot() }.onSuccess { snap ->
                    val shot = snap.picturePath?.let { path ->
                        runCatching { decodeScreenshot(status.screenshot(path)) }.getOrNull()
                    }
                    if (!isActive || settings.value?.route != Route.HOME_ASSISTANT || settings.value?.ha != config) return@onSuccess
                    _status.update { it.copy(playback = snap.playback) }
                    _mediaAccessRequired.value = snap.mediaAccessRequired
                    _haTile.update {
                        it.copy(
                            appName = snap.appName,
                            screenshot = shot ?: it.screenshot,
                            standby = snap.state == "off" || snap.state == "standby",
                        )
                    }
                }
                // Uptime only drifts with the clock, so ask the TV rarely.
                val now = System.currentTimeMillis()
                if (now - lastUptimeCheck > 10 * 60_000L || _haTile.value.bootedAtMillis == null && now - lastUptimeCheck > 60_000L) {
                    lastUptimeCheck = now
                    runCatching { status.uptimeSeconds() }.getOrNull()?.let { up ->
                        _haTile.update { it.copy(bootedAtMillis = now - (up * 1000).toLong()) }
                    }
                }
                delay(TILE_REFRESH_MS)
            }
        }
    }

    fun refreshTile() {
        tileJob?.cancel()
        tileJob = null
        _haTile.update { it.copy(bootedAtMillis = null) }
        updateTilePolling()
    }

    // ── Preferences ─────────────────────────────────────────────────────────

    fun setSpeechLanguage(language: String) = viewModelScope.launch { repo.setSpeechLanguage(language) }

    fun setVolumeKeys(enabled: Boolean) = viewModelScope.launch { repo.setVolumeKeys(enabled) }

    fun setHaptics(enabled: Boolean) = viewModelScope.launch { repo.setHaptics(enabled) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repo.setDynamicColor(enabled) }

    fun message(text: String) {
        messages.trySend(text)
    }
}

private fun HaConfig.trimmed() = copy(
    url = url.trim(), token = token.trim(), entityId = entityId.trim(),
    mediaEntityId = mediaEntityId.trim(), irPowerEntityId = irPowerEntityId.trim(),
)

private const val TILE_REFRESH_MS = 5_000L

/** Screenshots are full HD PNGs; half size is plenty for a phone-width tile. */
private fun decodeScreenshot(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 2 })
        ?.asImageBitmap()

/** Never substitute a different connection when the selected route is unavailable. */
internal fun selectBackend(route: Route, direct: RemoteBackend?, ha: RemoteBackend?): RemoteBackend? =
    if (route == Route.HOME_ASSISTANT) ha else direct

internal fun dispatchSmartTube(
    route: Route, query: String, play: Boolean, direct: RemoteBackend?, ha: RemoteBackend?,
): Result<Unit> = runCatching {
    val command = VoiceCommand(
        if (play) VoiceAction.PLAY else VoiceAction.SEARCH,
        query.trim(),
    )
    require(command.query.isNotBlank() && command.query.none { it.isISOControl() }) { "Enter a valid search query" }
    val backend = selectBackend(route, direct, ha)
        ?: error(if (route == Route.HOME_ASSISTANT) "Home Assistant isn't set up" else "Not connected to a TV")
    backend.openLink(SmartTubeLink.build(command))
}
