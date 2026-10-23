package io.github.tecix.richtvremote.media

import android.util.Base64
import org.json.JSONObject
import java.util.UUID

/** Optional TinyTV media extension; standard Remote v2 packets and pairing remain unchanged. */
internal fun mediaRequest(op: String): JSONObject = JSONObject().put("version", 1).put("id", UUID.randomUUID().toString()).put("op", op)

internal fun receiverPlayback(json: JSONObject?): MediaPlayback? = runCatching {
    json ?: return null
    val duration = json.getLong("duration").coerceAtLeast(0)
    MediaPlayback(json.getString("session"), json.getString("title"), json.getLong("position").coerceIn(0, duration),
        duration, json.getBoolean("playing"), json.getBoolean("seekable") && duration > 0)
}.getOrNull()

internal fun mediaBroadcast(request: JSONObject): String {
    val data = Base64.encodeToString(request.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    // Base64 alphabet contains no shell syntax. Component and action are fixed constants.
    return "am broadcast -n com.tinytv.receiver/.MediaControlReceiver -a com.tinytv.receiver.MEDIA_CONTROL --es request '$data'"
}

internal fun broadcastResponse(text: String, id: String): JSONObject? = runCatching {
    val encoded = Regex("TEXTV_MEDIA=([A-Za-z0-9+/=]+)").find(text)?.groupValues?.get(1) ?: return null
    JSONObject(String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)).takeIf { it.optString("id") == id && it.optInt("version") == 1 }
}.getOrNull()
