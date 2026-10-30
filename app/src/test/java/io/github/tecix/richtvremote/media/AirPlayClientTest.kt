package io.github.tecix.richtvremote.media

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AirPlayClientTest {
    /** Minimal HTTP receiver: records "METHOD path\nbody" and answers from a table. */
    private class FakeReceiver(val replies: Map<String, Pair<Int, String>>) : AutoCloseable {
        val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val sessions: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        init {
            Thread {
                while (!server.isClosed) {
                    val s = runCatching { server.accept() }.getOrNull() ?: break
                    s.use {
                        val input = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        val first = input.readLine() ?: return@use
                        var length = 0
                        while (true) {
                            val h = input.readLine() ?: break
                            if (h.isEmpty()) break
                            if (h.startsWith("Content-Length:", true)) length = h.substringAfter(':').trim().toInt()
                            if (h.startsWith("X-Apple-Session-ID:", true)) sessions += h.substringAfter(':').trim()
                        }
                        val body = CharArray(length).also { b -> var o = 0; while (o < length) o += input.read(b, o, length - o) }
                        val (method, path) = first.split(' ')
                        requests += "$method $path\n${String(body)}"
                        val (code, text) = replies[path.substringBefore('?')] ?: (200 to "")
                        val bytes = text.toByteArray()
                        it.getOutputStream().write(("HTTP/1.1 $code X\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray() + bytes)
                    }
                }
            }.apply { isDaemon = true; start() }
        }
        val receiver get() = AirPlayReceiver("Test TV", "127.0.0.1", server.localPort)
        override fun close() = server.close()
    }

    @Test fun playSendsAirPlayV1ParametersAndControls() = runBlocking {
        FakeReceiver(emptyMap()).use { tv ->
            val c = AirPlayClient(tv.receiver)
            c.play("https://cdn.example/hls/playlist.jpg", 95.0, listOf("http://192.168.2.3:4000/abc/movie.vi.srt"),
                referer = "https://site.example/watch")
            c.setPlaying(false)
            c.seek(1_812_500)
            c.loadCaptions(listOf("https://cdn.example/en.vtt"))
            c.selectCaptions(-1)
            c.addCaptionsDelay(-500)
            c.stop()
            assertEquals(listOf(
                "POST /play\nContent-Location: https://cdn.example/hls/playlist.jpg\nStart-Position: 95.000\n" +
                    "Caption-Location: http://192.168.2.3:4000/abc/movie.vi.srt\nReferer: https://site.example/watch\n",
                "POST /rate?value=0.000000\n", "POST /scrub?position=1812.500\n",
                "POST /load-captions\nCaption-Location: https://cdn.example/en.vtt\n",
                "POST /select-captions?index=-1\n", "POST /add-captions-offset?value=-500000\n", "POST /stop\n"), tv.requests)
            assertEquals("one session id for the whole video", 1, tv.sessions.size)
        }
    }

    @Test fun rejectsNonWebAndInjectedUrls() = runBlocking {
        FakeReceiver(emptyMap()).use { tv ->
            val c = AirPlayClient(tv.receiver)
            for (bad in listOf("file:///sdcard/x.mp4", "https://a.example/x\nCaption-Location: http://evil/", "https://user:pw@a.example/x"))
                assertTrue(bad, runCatching { c.play(bad) }.isFailure)
            assertTrue(tv.requests.isEmpty())
        }
    }

    @Test fun statusPrefersTinyTvJsonAndFallsBackToPlaybackInfo() = runBlocking {
        val json = """{"state":"PLAYING","position":22.105,"duration":3410.374,"url":"http://127.0.0.1:7000/hls/x/p1.m3u8",""" +
            """"error":null,"caption":1,"captionOffsetMs":-500,"captions":["a.srt","b \"en\".vtt"]}"""
        FakeReceiver(mapOf("/tinytv/status" to (200 to json))).use { tv ->
            val s = AirPlayClient(tv.receiver).status()
            assertEquals(AirPlayStatus(AirPlayStatus.State.PLAYING, 22_105, 3_410_374, listOf("a.srt", "b \"en\".vtt"), 1, -500, null, true,
                "http://127.0.0.1:7000/hls/x/p1.m3u8"), s)
            assertTrue(s.active)
        }
        val plist = "<plist><dict><key>duration</key>\n<real>100.5</real><key>position</key>\n<real>12.0</real>" +
            "<key>rate</key>\n<real>0.0</real></dict></plist>"
        FakeReceiver(mapOf("/tinytv/status" to (404 to ""), "/playback-info" to (200 to plist))).use { tv ->
            val s = AirPlayClient(tv.receiver).status()
            assertEquals(AirPlayStatus(AirPlayStatus.State.PAUSED, 12_000, 100_500), s)
            assertFalse(s.extended)
        }
        FakeReceiver(mapOf("/tinytv/status" to (404 to ""), "/playback-info" to (200 to ""))).use { tv ->
            assertEquals(AirPlayStatus.State.IDLE, AirPlayClient(tv.receiver).status().state)
        }
    }

    @Test fun failedVideoReportsTheReceiversReason() {
        val s = AirPlayClient.parseTinyTvStatus("""{"state":"ENDED","position":0,"duration":0,"url":null,""" +
            """"error":"The TV player can't open this video (1/-5003)","caption":-1,"captionOffsetMs":0,"captions":[]}""")
        assertFalse(s.active)
        assertEquals("The TV player can't open this video (1/-5003)", s.error)
        assertEquals(-1, AirPlayClient.parseTinyTvStatus("""{"state":"PLAYING","caption":5,"captions":["a"]}""").caption)
    }
}
