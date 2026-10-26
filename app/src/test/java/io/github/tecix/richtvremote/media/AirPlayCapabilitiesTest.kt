package io.github.tecix.richtvremote.media

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AirPlayCapabilitiesTest {
    private val defaults = AirPlayCapabilities()
    private fun video(codec: String = "video/avc", w: Int = 1920, h: Int = 1080, rotation: Int = 0, fps: Double = 30.0) =
        VideoSource(codec, w, h, rotation, fps)
    private val json = """{"version":1,"avc":{"maxWidth":1920,"maxHeight":1088,"maxFps":60},"hevc":{"maxWidth":3840,"maxHeight":2160,"maxFps":60,"rotate":false},"avcRotate":true,"hls":{"segments":"mpegts","requireM3u8Name":true}}"""

    @Test fun avcCodedSizeBoundariesAndBothOrientations() {
        assertFalse(defaults.needsTranscode(video(w = 1920, h = 1088, fps = 60.0)))
        assertFalse(defaults.needsTranscode(video(w = 1088, h = 1920, rotation = 90)))
        assertTrue(defaults.needsTranscode(video(w = 1921)))
        assertTrue(defaults.needsTranscode(video(h = 1089)))
        assertTrue(defaults.needsTranscode(video(w = 1089, h = 1920)))
        assertTrue(defaults.needsTranscode(video(w = 2880, h = 2160, fps = 60.0)))
    }
    @Test fun hevc4kIsPassedThroughExceptRotationOrAboveLimits() {
        assertFalse(defaults.needsTranscode(video("video/hevc", 3840, 2160, fps = 60.0)))
        assertFalse(defaults.needsTranscode(video("video/hevc", 2160, 3840)))
        for (rotation in listOf(90, 180, 270, -90))
            assertTrue(defaults.needsTranscode(video("video/hevc", 3840, 2160, rotation)))
        assertTrue(defaults.needsTranscode(video("video/hevc", 3841, 2160)))
        assertTrue(defaults.needsTranscode(video("video/hevc", 3840, 2161)))
        assertTrue(defaults.needsTranscode(video("video/hevc", fps = 60.1)))
    }
    @Test fun codecAndFrameRateRules() {
        assertTrue(defaults.needsTranscode(video("video/av01")))
        assertTrue(defaults.needsTranscode(video("video/x-vnd.on2.vp9")))
        assertTrue(defaults.needsTranscode(video(fps = 120.0)))
        assertFalse(defaults.needsTranscode(video(fps = 0.0)))
        assertFalse(defaults.needsTranscode(video(rotation = 90, fps = 24.0)))
    }
    @Test fun parseContractAndReceiverOverrides() {
        assertEquals(defaults, AirPlayCapabilities.parse(json))
        val custom = AirPlayCapabilities.parse(json.replace("\"rotate\":false", "\"rotate\":true").replace("\"maxFps\":60", "\"maxFps\":30"))
        assertFalse(custom.needsTranscode(video("video/hevc", rotation = 90)))
        assertTrue(custom.needsTranscode(video(fps = 60.0)))
    }
    @Test fun malformedOrUnrecognizedCapsUseDefaults() {
        for (invalid in listOf("", "not json", "{}", json.replace("\"version\":1", "\"version\":2"),
            json.replace("1920", "0"), json.replace("2160", "-1"), json.replace("\"maxFps\":60", "\"maxFps\":0")))
            assertEquals(defaults, AirPlayCapabilities.parse(invalid))
    }
    @Test fun capsHttpAndOldReceiverFallback() = runBlocking {
        for ((code, body) in listOf(200 to json.replace("\"rotate\":false", "\"rotate\":true"), 404 to "", 200 to "<html>AirReceiver</html>", 200 to "x".repeat(20_000))) {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                val worker = Thread {
                    server.accept().use { socket ->
                        val input = socket.getInputStream().bufferedReader()
                        assertEquals("GET /tinytv/caps HTTP/1.1", input.readLine())
                        while (!input.readLine().isNullOrEmpty()) { }
                        val bytes = body.toByteArray()
                        socket.getOutputStream().write("HTTP/1.1 $code Test\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
                    }
                }.apply { isDaemon = true; start() }
                val caps = AirPlayCapabilities.fetch(AirPlayReceiver("TV", "127.0.0.1", server.localPort))
                assertEquals(code == 200 && body.startsWith("{"), caps.hevcRotate)
                worker.join(2000)
            }
        }
    }
}
