package io.github.tecix.textv

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tecix.textv.data.Route
import io.github.tecix.textv.data.SavedTv
import io.github.tecix.textv.data.Settings
import io.github.tecix.textv.data.SettingsRepository
import io.github.tecix.textv.direct.AndroidTvRemote
import io.github.tecix.textv.direct.ClientIdentity
import io.github.tecix.textv.direct.DeviceInfo
import io.github.tecix.textv.direct.NotPairedException
import io.github.tecix.textv.direct.PairingException
import io.github.tecix.textv.direct.PairingSession
import io.github.tecix.textv.direct.VolumeInfo
import io.github.tecix.textv.discovery.DiscoveredTv
import io.github.tecix.textv.discovery.discoverTvs
import io.github.tecix.textv.ha.HaConfig
import io.github.tecix.textv.ha.HomeAssistantException
import io.github.tecix.textv.ha.HomeAssistantRemote
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.tecix.textv.ha.HomeAssistantStatus
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
)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)
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

    private val _haTile = MutableStateFlow(HaTile())
    val haTile = _haTile.asStateFlow()

    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    private var remote: AndroidTvRemote? = null
    private var haRemote: HomeAssistantRemote? = null
    private var pairingSession: PairingSession? = null
    private var connectJob: Job? = null
    private var tileJob: Job? = null
    private var foreground = false
    private var lastHaError = 0L

    // Set when the user picks a TV, so an unpaired one goes straight to pairing.
    private var pairWhenNeeded = false

    init {
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

    override fun onCleared() {
        dropRemote()
        haRemote?.close()
        pairingSession?.close()
    }

    // ── Route ───────────────────────────────────────────────────────────────

    fun setRoute(route: Route) {
        remote?.resetText()
        viewModelScope.launch { repo.setRoute(route) }
        if (route == Route.HOME_ASSISTANT && !(settings.value?.ha?.isComplete ?: false)) {
            messages.trySend("Set up Home Assistant in Settings first")
        }
    }

    private fun backend(): RemoteBackend? = when (settings.value?.route) {
        Route.HOME_ASSISTANT -> haRemote
        else -> remote?.takeIf { it.connected }
    }

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

    /** SmartTube: switch between 1x and the last picked speed (needs "Remap S to speed toggle"). */
    fun smartTubeSpeed() = withBackend { it.press(TvKey.S) }

    fun smartTubeCaptions() {
        val keys = TvKey.parseMacro(settings.value?.captionMacro ?: "")
        if (keys == null) {
            messages.trySend("The captions macro in Settings isn't valid")
            return
        }
        withBackend { it.pressSequence(keys) }
    }

    fun setCaptionMacro(macro: String) = viewModelScope.launch { repo.setCaptionMacro(macro) }

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

        override fun onVolume(volume: VolumeInfo) = _status.update { it.copy(volume = volume) }

        override fun onPower(on: Boolean) = _status.update { it.copy(powerOn = on) }

        override fun onCurrentApp(packageName: String) = _status.update { it.copy(currentApp = packageName) }
    }

    private fun dropRemote() {
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
