package io.github.tecix.richtvremote.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.tecix.richtvremote.discovery.discoverServices
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** A subtitle for the TV: a link, or a file this phone serves over Wi-Fi. */
class ServedSubtitle internal constructor(val name: String, val url: String,
    internal val server: SingleMediaServer?, internal val file: File?)

/** The video currently sent over AirPlay, and what the TV last reported about it. */
data class AirPlayNowPlaying(val receiver: AirPlayReceiver, val title: String, val status: AirPlayStatus?, val preparation: String? = null)

/**
 * App-scoped AirPlay sender: the chosen receiver, the video on it, playback polling and the subtitle
 * files this phone serves to the TV. Survives the activity like [MediaStreaming].
 */
internal object AirPlaySession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _selected = MutableStateFlow<AirPlayReceiver?>(null)
    val selected = _selected.asStateFlow()
    private val _receivers = MutableStateFlow<List<AirPlayReceiver>>(emptyList())
    val receivers = _receivers.asStateFlow()
    private val _nowPlaying = MutableStateFlow<AirPlayNowPlaying?>(null)
    val nowPlaying = _nowPlaying.asStateFlow()
    /** Subtitles chosen in the share sheet before Play; sent with /play. */
    private val _pendingCaptions = MutableStateFlow<List<ServedSubtitle>>(emptyList())
    val pendingCaptions = _pendingCaptions.asStateFlow()

    internal fun preparing(receiver: AirPlayReceiver, title: String, message: String = "Preparing video on phone…") {
        _nowPlaying.value = AirPlayNowPlaying(receiver, title, null, message)
    }

    internal fun preparationProgress(message: String) {
        _nowPlaying.value?.takeIf { it.preparation != null }?.let { _nowPlaying.value = it.copy(preparation = message) }
    }

    internal fun clearPreparation() {
        if (_nowPlaying.value?.preparation != null) _nowPlaying.value = null
    }

    private var client: AirPlayClient? = null
    private var poller: Job? = null
    private val servers = mutableListOf<ServedSubtitle>()

    fun select(receiver: AirPlayReceiver) {
        _selected.value = receiver
        if (receiver !in _receivers.value) _receivers.value += receiver
    }

    /** mDNS for a few seconds; the selected TV's own port 7000 is checked when mDNS finds nothing. */
    suspend fun discover(context: Context, tvHost: String?, tvName: String?): List<AirPlayReceiver> {
        // Keyed by host and port: one TV can run several receivers (TinyTV on 7000, AirReceiver elsewhere).
        val found = linkedMapOf<String, AirPlayReceiver>()
        fun tinyTv() = tvHost != null && "$tvHost:7000" in found
        withTimeoutOrNull(3500) {
            discoverServices(context.applicationContext, "_airplay._tcp.").first { list ->
                list.forEach { found["${it.host}:${it.port}"] = AirPlayReceiver(it.name, it.host, if (it.port > 0) it.port else 7000) }
                tinyTv() // the selected TV's TinyTV answered: no need to wait longer
            }
        }
        if (tvHost != null && !tinyTv() && probe(tvHost)) found["$tvHost:7000"] = AirPlayReceiver(tvName ?: tvHost, tvHost)
        // TinyTV (port 7000) on the selected TV first, then any TinyTV, then other receivers on that TV.
        val list = found.values.sortedWith(compareByDescending<AirPlayReceiver> { it.host == tvHost && it.port == 7000 }
            .thenByDescending { it.port == 7000 }.thenByDescending { it.host == tvHost })
        _receivers.value = list
        _selected.value = list.firstOrNull { it == _selected.value } ?: list.firstOrNull()
        return list
    }

    private suspend fun probe(host: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = URI("http://$host:7000/server-info").toURL().openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 1500; c.readTimeout = 1500
                c.responseCode == 200
            } finally { c.disconnect() }
        }.getOrDefault(false)
    }

    suspend fun play(receiver: AirPlayReceiver, url: String, title: String, headers: Map<String, String> = emptyMap()) {
        val c = AirPlayClient(receiver)
        val captions = _pendingCaptions.value
        poller?.cancel()
        _nowPlaying.value = AirPlayNowPlaying(receiver, title, AirPlayStatus(AirPlayStatus.State.LOADING, 0, 0))
        try {
            c.play(url, captions = captions.map { it.url }, userAgent = headers["User-Agent"], referer = headers["Referer"])
        } catch (e: Exception) {
            _nowPlaying.value = null
            throw e
        }
        client = c
        synchronized(servers) { servers += captions }
        _pendingCaptions.value = emptyList()
        poll(c)
    }

    private fun poll(c: AirPlayClient) {
        poller = scope.launch {
            var failures = 0
            var seenActive = false
            while (isActive && client === c) {
                val status = runCatching { c.status() }.getOrNull()
                if (client !== c) break
                if (status == null) {
                    if (++failures >= 10) { finish(c, "Lost contact with the AirPlay receiver"); break }
                } else {
                    failures = 0
                    seenActive = seenActive || status.active
                    _nowPlaying.value = _nowPlaying.value?.copy(status = status)
                    if (seenActive && !status.active) {
                        finish(c, status.error)
                        break
                    }
                    // A receiver that never starts (bad URL) reports IDLE with an error.
                    if (!seenActive && status.error != null) { finish(c, status.error); break }
                }
                delay(1000)
            }
        }
    }

    private val _ended = MutableStateFlow<String?>(null)
    /** Set when the TV finished or failed the video: null message means it simply ended. */
    val ended = _ended.asStateFlow()

    private fun finish(c: AirPlayClient, error: String?) {
        if (client !== c) return
        client = null
        _nowPlaying.value = null
        _ended.value = error ?: "Playback on the TV ended"
        releaseServers()
    }

    fun consumeEnded() { _ended.value = null }

    suspend fun stop(receiver: AirPlayReceiver? = _nowPlaying.value?.receiver) {
        val c = client
        client = null
        poller?.cancel()
        _nowPlaying.value = null
        releaseServers()
        (c ?: receiver?.let(::AirPlayClient))?.stop()
    }

    suspend fun setPlaying(playing: Boolean) = control { it.setPlaying(playing) }
    suspend fun seek(positionMs: Long) = control { it.seek(positionMs) }
    suspend fun selectCaptions(index: Int) = control { it.selectCaptions(index) }
    suspend fun addCaptionsDelay(deltaMs: Long) = control { it.addCaptionsDelay(deltaMs) }
    suspend fun showCaptions(show: Boolean) = control { it.showCaptions(show) }

    suspend fun skip(deltaMs: Long) {
        val status = _nowPlaying.value?.status ?: return
        val target = (status.positionMs + deltaMs).coerceIn(0, if (status.durationMs > 0) status.durationMs else Long.MAX_VALUE)
        seek(target)
    }

    private suspend fun control(action: suspend (AirPlayClient) -> Unit) {
        val c = client ?: throw IOException("Nothing is playing over AirPlay")
        action(c)
        runCatching { c.status() }.getOrNull()?.let { s -> if (client === c) _nowPlaying.value = _nowPlaying.value?.copy(status = s) }
    }

    /** A subtitle link: added to the playing video, or kept for the next Play. */
    suspend fun addSubtitleUrl(url: String) {
        requireHttp(url)
        val subtitle = ServedSubtitle(Uri.parse(url).lastPathSegment ?: url, url, null, null)
        addSubtitle(subtitle)
    }

    /** A subtitle file from this phone, served over Wi-Fi to the TV. */
    suspend fun addSubtitleFile(context: Context, uri: Uri) {
        val app = context.applicationContext
        val served = withContext(Dispatchers.IO) {
            val name = runCatching {
                app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment ?: "subtitle.srt"
            val extension = name.substringAfterLast('.', "").lowercase()
            require(extension in SUBTITLE_EXTENSIONS) { "Choose an .srt or .vtt subtitle file" }
            val file = File.createTempFile("tv-subtitle-", ".$extension", app.cacheDir)
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { out ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= 8L * 1024 * 1024) { "Subtitle files must be under 8 MB" }
                            out.write(buffer, 0, n)
                        }
                    }
                } ?: error("Could not read the subtitle file")
                val mime = if (extension == "vtt") "text/vtt" else "application/x-subrip"
                val server = SingleMediaServer(file, mime, MediaShareService.localNetwork(app).first, name)
                ServedSubtitle(name, server.url, server, file)
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        }
        addSubtitle(served)
    }

    private suspend fun addSubtitle(subtitle: ServedSubtitle) {
        val c = client
        if (c == null) {
            _pendingCaptions.value += subtitle
            return
        }
        try {
            c.loadCaptions(listOf(subtitle.url))
        } catch (e: Exception) {
            subtitle.close()
            throw e
        }
        synchronized(servers) { servers += subtitle }
    }

    fun removePendingCaption(subtitle: ServedSubtitle) {
        _pendingCaptions.value -= subtitle
        subtitle.close()
    }

    fun clearPendingCaptions() {
        _pendingCaptions.value.forEach { it.close() }
        _pendingCaptions.value = emptyList()
    }

    private fun releaseServers() {
        val old = synchronized(servers) { servers.toList().also { servers.clear() } }
        old.forEach { it.close() }
    }

    private fun ServedSubtitle.close() {
        runCatching { server?.close() }
        runCatching { file?.delete() }
    }

    private fun requireHttp(url: String) {
        val uri = runCatching { URI(url) }.getOrNull()
        require(uri != null && uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null) {
            "Enter an http(s) link to an .srt or .vtt file"
        }
    }

    private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "webvtt", "txt")
}
