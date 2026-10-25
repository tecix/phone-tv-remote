package io.github.tecix.richtvremote.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import java.util.UUID

/** An AirPlay video receiver found over mDNS (_airplay._tcp) or typed in; TinyTV listens on 7000. */
data class AirPlayReceiver(val name: String, val host: String, val port: Int = 7000) {
    val label: String get() = if (port == 7000) "$name · $host" else "$name · $host:$port"
}

/** What the TV is playing. Subtitle details come from TinyTV's /tinytv/status; other receivers have none. */
data class AirPlayStatus(
    val state: State,
    val positionMs: Long,
    val durationMs: Long,
    val captions: List<String> = emptyList(),
    val caption: Int = -1,
    val captionOffsetMs: Long = 0,
    val error: String? = null,
    val extended: Boolean = false,
) {
    enum class State { IDLE, LOADING, PLAYING, PAUSED, ENDED }
    val active: Boolean get() = state == State.LOADING || state == State.PLAYING || state == State.PAUSED

    fun toPlayback(title: String): MediaPlayback =
        MediaPlayback("airplay", title, positionMs, durationMs, state == State.PLAYING, durationMs > 0)
}

/**
 * AirPlay v1 video sender over plain HTTP, as Web Video Caster and ExoAirPlayer use it: /play with
 * text/parameters (Content-Location, Start-Position, Caption-Location, User-Agent, Referer), /rate,
 * /scrub, /stop, /playback-info, plus the ExoAirPlayer subtitle extensions and TinyTV's
 * /tinytv/status and /select-captions.
 */
class AirPlayClient(val receiver: AirPlayReceiver) {
    private val session = UUID.randomUUID().toString().uppercase(Locale.ROOT)
    private val base = "http://${hostLiteral(receiver.host)}:${receiver.port}"

    suspend fun play(url: String, startSeconds: Double = 0.0, captions: List<String> = emptyList(),
                     userAgent: String? = null, referer: String? = null) {
        requireWebUrl(url)
        val body = buildString {
            append("Content-Location: ").append(url).append('\n')
            append("Start-Position: ").append(String.format(Locale.ROOT, "%.3f", startSeconds.coerceAtLeast(0.0))).append('\n')
            captions.forEach { requireWebUrl(it); append("Caption-Location: ").append(it).append('\n') }
            userAgent?.let { requireHeaderValue(it); append("User-Agent: ").append(it).append('\n') }
            referer?.let { requireWebUrl(it); append("Referer: ").append(it).append('\n') }
        }
        // The receiver may check the stream (and remux it) before answering.
        request("POST", "/play", body, readTimeout = 25_000)
    }

    suspend fun setPlaying(playing: Boolean) { request("POST", "/rate?value=${if (playing) "1.000000" else "0.000000"}") }

    suspend fun seek(positionMs: Long) {
        request("POST", "/scrub?position=" + String.format(Locale.ROOT, "%.3f", positionMs.coerceAtLeast(0) / 1000.0))
    }

    suspend fun stop() { request("POST", "/stop") }

    suspend fun loadCaptions(urls: List<String>) {
        require(urls.isNotEmpty())
        request("POST", "/load-captions", urls.joinToString("") { requireWebUrl(it); "Caption-Location: $it\n" })
    }

    /** -1 hides subtitles (TinyTV only). */
    suspend fun selectCaptions(index: Int) { request("POST", "/select-captions?index=$index") }

    suspend fun showCaptions(show: Boolean) { request("POST", "/show-captions?toggle=${if (show) 1 else 0}") }

    suspend fun addCaptionsDelay(deltaMs: Long) { request("POST", "/add-captions-offset?value=${deltaMs * 1000}") }

    suspend fun status(): AirPlayStatus {
        val extended = runCatching { request("GET", "/tinytv/status", readTimeout = 4000) }.getOrNull()
        if (extended != null) return parseTinyTvStatus(extended)
        return parsePlaybackInfo(request("GET", "/playback-info", readTimeout = 4000))
    }

    private suspend fun request(method: String, path: String, body: String? = null, readTimeout: Int = 6000): String =
        withContext(Dispatchers.IO) {
            val connection = URI(base + path).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 3000
                connection.readTimeout = readTimeout
                connection.instanceFollowRedirects = false
                connection.requestMethod = method
                connection.setRequestProperty("User-Agent", "RichTVRemote/2.1 (AirPlay sender)")
                connection.setRequestProperty("X-Apple-Session-ID", session)
                if (body != null || method == "POST") {
                    val bytes = (body ?: "").toByteArray(Charsets.UTF_8)
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "text/parameters")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val code = connection.responseCode
                val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                if (code == 404) throw IOException("AirPlay receiver doesn't support $path")
                if (code !in 200..299) throw IOException("AirPlay receiver answered HTTP $code for ${path.substringBefore('?')}")
                text
            } finally { connection.disconnect() }
        }

    companion object {
        internal fun parseTinyTvStatus(json: String): AirPlayStatus {
            val o = JSONObject(json)
            val captions = o.optJSONArray("captions")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            return AirPlayStatus(
                state = runCatching { AirPlayStatus.State.valueOf(o.getString("state")) }.getOrDefault(AirPlayStatus.State.IDLE),
                positionMs = (o.optDouble("position", 0.0) * 1000).toLong().coerceAtLeast(0),
                durationMs = (o.optDouble("duration", 0.0).takeIf { it.isFinite() } ?: 0.0).times(1000).toLong().coerceAtLeast(0),
                captions = captions,
                caption = o.optInt("caption", -1).takeIf { it in captions.indices } ?: -1,
                captionOffsetMs = o.optLong("captionOffsetMs", 0),
                error = o.optString("error").takeIf { !o.isNull("error") && it.isNotBlank() },
                extended = true,
            )
        }

        /** Standard XML plist reply; empty means nothing is playing. */
        internal fun parsePlaybackInfo(xml: String): AirPlayStatus {
            fun real(key: String) = Regex("<key>$key</key>\\s*<(?:real|integer)>([-0-9.eE]+)</").find(xml)?.groupValues?.get(1)?.toDoubleOrNull()
            val duration = real("duration") ?: return AirPlayStatus(AirPlayStatus.State.IDLE, 0, 0)
            val rate = real("rate") ?: 0.0
            return AirPlayStatus(if (rate > 0) AirPlayStatus.State.PLAYING else AirPlayStatus.State.PAUSED,
                ((real("position") ?: 0.0) * 1000).toLong().coerceAtLeast(0),
                (duration.takeIf { it.isFinite() } ?: 0.0).times(1000).toLong().coerceAtLeast(0))
        }

        private fun hostLiteral(host: String) = if (':' in host && !host.startsWith("[")) "[$host]" else host

        private fun requireWebUrl(url: String) {
            val uri = URI(url)
            require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
                url.none { it.isISOControl() }) { "AirPlay needs an HTTP or HTTPS address" }
        }

        private fun requireHeaderValue(value: String) = require(value.none { it.isISOControl() } && value.length < 512)
    }
}
