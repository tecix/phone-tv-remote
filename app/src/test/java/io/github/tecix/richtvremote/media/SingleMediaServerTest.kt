package io.github.tecix.richtvremote.media

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

class SingleMediaServerTest {
    @Test fun rangesAreBoundedAndValidated() {
        assertEquals(2L..5L, byteRange("bytes=2-5", 10))
        assertEquals(7L..9L, byteRange("bytes=-3", 10))
        assertEquals(8L..9L, byteRange("bytes=8-", 10))
        assertEquals(0L..9L, byteRange("bytes=0-9999", 10))
        for (s in listOf("bytes=10-", "bytes=3-1", "bytes=-0", "bytes=0-1,3-4", "bytes=99999999999999999999-", "junk")) assertNull(byteRange(s, 10))
    }
    @Test fun streamsExactRangesAndRequiresSecretPath() {
        val file = Files.createTempFile("media-test", ".mp4").toFile()
        file.writeBytes(ByteArray(256) { it.toByte() })
        try {
            SingleMediaServer(file, "video/mp4", InetAddress.getLoopbackAddress()).use { server ->
                fun connect(path: String = server.url, method: String = "GET", range: String? = null) =
                    (URL(path).openConnection() as HttpURLConnection).apply {
                        requestMethod = method; connectTimeout = 2000; readTimeout = 2000
                        range?.let { setRequestProperty("Range", it) }
                    }
                val full = connect()
                assertEquals(200, full.responseCode)
                assertEquals("Streaming", full.getHeaderField("transferMode.dlna.org"))
                assertEquals("DLNA.ORG_OP=01;DLNA.ORG_CI=0", full.getHeaderField("contentFeatures.dlna.org"))
                assertArrayEquals(file.readBytes(), full.inputStream.use { it.readBytes() })
                full.disconnect()
                val head = connect(method = "HEAD")
                assertEquals(256, head.contentLength); assertEquals(0, head.inputStream.readBytes().size); head.disconnect()
                val partial = connect(range = "bytes=100-109")
                assertEquals(206, partial.responseCode)
                assertEquals("bytes 100-109/256", partial.getHeaderField("Content-Range"))
                assertArrayEquals(ByteArray(10) { (100 + it).toByte() }, partial.inputStream.use { it.readBytes() }); partial.disconnect()
                val unsat = connect(range = "bytes=999-")
                assertEquals(416, unsat.responseCode); unsat.disconnect()
                val invalid = connect(server.url.substringBeforeLast('/') + "/../etc/passwd")
                assertEquals(404, invalid.responseCode); invalid.disconnect()
            }
        } finally { file.delete() }
    }
}
