package io.github.tecix.textv.ha

import android.util.Log
import io.github.tecix.textv.RemoteBackend
import io.github.tecix.textv.TvKey
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class HomeAssistantException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class HaConfig(
    val url: String = "",
    val token: String = "",
    val entityId: String = "",
    /** The Android Debug Bridge media_player of the same TV; blank means "derive from [entityId]". */
    val mediaEntityId: String = "",
) {
    val isComplete get() = url.isNotBlank() && token.isNotBlank() && entityId.isNotBlank()

    val mediaPlayer get() = mediaEntityId.trim().ifEmpty { "media_player." + entityId.trim().substringAfter('.') }
}

// Commands waiting for HA beyond this are dropped, so a held D-pad key doesn't
// keep moving the focus long after it was released.
private const val MAX_PENDING = 4
private const val TIMEOUT_MS = 10_000
private const val TAG = "HomeAssistant"

private fun shellQuote(text: String) = "'" + text.replace("'", "'\\''") + "'"

/** Blocking REST calls to Home Assistant. Call from a background thread. */
class HaHttp(config: HaConfig) {
    private val base: String
    private val token = config.token.trim()

    init {
        val url = config.url.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw HomeAssistantException("The Home Assistant URL must start with http:// or https://")
        }
        base = url
    }

    fun json(method: String, path: String, body: JSONObject? = null): JSONObject? {
        val text = request(method, path, body).toString(Charsets.UTF_8)
        return runCatching { JSONObject(text) }.getOrNull()
    }

    fun state(entityId: String): JSONObject =
        json("GET", "/api/states/$entityId") ?: throw HomeAssistantException("Home Assistant sent no state for $entityId")

    /** Fetch a path HA hands out, such as a media_player's `entity_picture`. */
    fun bytes(path: String): ByteArray = request("GET", path, null)

    fun callService(domain: String, service: String, data: JSONObject) {
        request("POST", "/api/services/$domain/$service", data)
    }

    private fun request(method: String, path: String, body: JSONObject?): ByteArray {
        if (token.isEmpty()) throw HomeAssistantException("No Home Assistant access token set")
        val conn = try {
            (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toString().toByteArray()) }
                }
            }
        } catch (e: IOException) {
            throw HomeAssistantException("Can't reach Home Assistant (${e.message})", e)
        }
        val status = try {
            conn.responseCode
        } catch (e: IOException) {
            conn.disconnect()
            throw HomeAssistantException("Can't reach Home Assistant (${e.message})", e)
        }
        // Read bodies to the end (and don't disconnect) so the connection is kept alive for the next key.
        if (status >= 400) {
            runCatching { conn.errorStream?.use { it.readBytes() } }
            when (status) {
                401 -> throw HomeAssistantException("Home Assistant rejected the access token")
                404 -> throw HomeAssistantException("Home Assistant doesn't know ${path.substringAfterLast('/')}")
                else -> throw HomeAssistantException("Home Assistant answered HTTP $status")
            }
        }
        return try {
            conn.inputStream.use { it.readBytes() }
        } catch (e: IOException) {
            throw HomeAssistantException("Can't reach Home Assistant (${e.message})", e)
        }
    }
}

/**
 * Sends key presses to the TV through Home Assistant's Android Debug Bridge integration
 * (`remote.send_command` on the TV's `remote.*` entity). HA injects keys with
 * `input keyevent` over ADB, which works on screens an accessibility-based receiver can't steer.
 *
 * Requests run in order on one worker thread; the send methods never block.
 */
class HomeAssistantRemote(
    config: HaConfig,
    private val onError: (String) -> Unit,
) : RemoteBackend {
    private val http = HaHttp(config)
    val entityId = config.entityId.trim()

    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) {
        Thread(it, "home-assistant").apply { isDaemon = true }
    }

    override fun press(key: TvKey) = command("input keyevent ${key.code}", droppable = true)

    override fun longPress(key: TvKey) = command("input keyevent --longpress ${key.code}")

    // One shell command for the whole macro: a request per key would be slower than the pauses.
    override fun pressSequence(keys: List<TvKey>) =
        command(keys.joinToString("; sleep 0.3; ") { "input keyevent ${it.code}" })

    // `input text` treats %s as a space; quote everything else for the shell.
    override fun sendText(text: String) = command("input text ${shellQuote(text.replace(" ", "%s"))}")

    override fun resetText() = Unit // Text goes in as typed; there is no field mirror to reset.

    // Receiver apps often lack a TV launcher entry, so fall back to the phone-style one.
    override fun launchApp(packageName: String) {
        val pkg = shellQuote(packageName)
        command(
            "monkey -p $pkg -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 || " +
                "monkey -p $pkg -c android.intent.category.LAUNCHER 1"
        )
    }

    /** Verify URL, token and entity. Calls back on the worker thread with the entity's friendly name. */
    fun check(onResult: (Result<String>) -> Unit) {
        worker.execute {
            onResult(runCatching {
                http.state(entityId).optJSONObject("attributes")?.optString("friendly_name")
                    ?.takeIf { it.isNotBlank() } ?: entityId
            })
        }
    }

    fun close() = worker.shutdown()

    private fun command(command: String, droppable: Boolean = false) {
        if (worker.isShutdown) return
        if (droppable && worker.queue.size >= MAX_PENDING) {
            Log.d(TAG, "Home Assistant is lagging; dropped $command")
            return
        }
        worker.execute {
            try {
                http.callService("remote", "send_command", JSONObject().put("entity_id", entityId).put("command", command))
            } catch (e: HomeAssistantException) {
                Log.w(TAG, "Command '$command' failed: ${e.message}")
                onError(e.message ?: "Request failed")
            }
        }
    }
}

/** What the TV is doing, as seen through HA's Android Debug Bridge media_player. */
data class HaTvSnapshot(
    val appName: String?,
    val appId: String?,
    val state: String,
    val picturePath: String?,
)

/** Reads for the Home Assistant tile. Separate from [HomeAssistantRemote] so polling never delays key presses. */
class HomeAssistantStatus(config: HaConfig) {
    private val http = HaHttp(config)
    private val mediaPlayer = config.mediaPlayer

    fun snapshot(): HaTvSnapshot {
        val state = http.state(mediaPlayer)
        val attrs = state.optJSONObject("attributes") ?: JSONObject()
        fun attr(name: String) = attrs.optString(name).takeIf { it.isNotBlank() && it != "null" }
        return HaTvSnapshot(
            appName = attr("app_name") ?: attr("source"),
            appId = attr("app_id"),
            state = state.optString("state"),
            picturePath = attr("entity_picture"),
        )
    }

    /** The TV's screenshot (PNG), if HA's screencap option is on. */
    fun screenshot(path: String): ByteArray = http.bytes(path)

    /** Seconds since the TV booted, via `cat /proc/uptime` and the media_player's `adb_response`. */
    fun uptimeSeconds(): Double? {
        http.callService(
            "androidtv", "adb_command",
            JSONObject().put("entity_id", mediaPlayer).put("command", "cat /proc/uptime"),
        )
        // Any adb_command shares this attribute; only accept something shaped like /proc/uptime.
        val response = http.state(mediaPlayer).optJSONObject("attributes")?.optString("adb_response") ?: return null
        val match = Regex("""^\s*(\d+(?:\.\d+)?)\s+\d+(?:\.\d+)?\s*$""").find(response) ?: return null
        return match.groupValues[1].toDouble()
    }
}
