package io.github.tecix.richtvremote.media

import android.util.Base64
import io.github.tecix.richtvremote.direct.Proto
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReceiverMediaTest {
    @Test fun optionalExtensionReusesFramingAndCorrelatesAdbResponses() {
        val request = mediaRequest("status")
        val frame = Proto.readFrame(Proto.frame(Proto.msg(9000 to request.toString())).inputStream())
        assertEquals(request.getString("id"), JSONObject(Proto.parse(frame).text(9000)).getString("id"))
        val reply = JSONObject().put("version", 1).put("id", request.getString("id")).put("ok", true)
        val encoded = Base64.encodeToString(reply.toString().toByteArray(), Base64.NO_WRAP)
        assertNotNull(broadcastResponse("Broadcast completed: data=\"TEXTV_MEDIA=$encoded\"", request.getString("id")))
        assertNull(broadcastResponse("TEXTV_MEDIA=$encoded", "stale-request"))
        assertNull(broadcastResponse("unrelated uptime response", request.getString("id")))
    }
    @Test fun broadcastUsesOnlyQuotedBase64ForUntrustedData() {
        val request = mediaRequest("open").put("uri", "https://example.com/'\$(touch fake)&x=2").put("mime", "video/*")
        val command = mediaBroadcast(request)
        val argument = command.substringAfter("--es request '").removeSuffix("'")
        assertTrue(argument.matches(Regex("[A-Za-z0-9+/=]+")))
        assertEquals(request.getString("uri"), JSONObject(String(Base64.decode(argument, Base64.DEFAULT))).getString("uri"))
    }
    @Test fun playbackRequiresValidMetadataAndKeepsSeekCapability() {
        assertNull(receiverPlayback(null)); assertNull(receiverPlayback(JSONObject()))
        val p = receiverPlayback(JSONObject().put("session", "vlc:track").put("title", "Sơn Tùng")
            .put("position", 10_000).put("duration", 120_000).put("playing", false).put("seekable", true))!!
        assertEquals("Sơn Tùng", p.title); assertEquals(10_000L, p.positionMs); assertTrue(p.canSeek)
    }
}
