package io.github.tecix.richtvremote.media

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import javax.xml.parsers.DocumentBuilderFactory

class DlnaControllerTest {
    private val type = "urn:schemas-upnp-org:service:AVTransport:1"
    private fun description(base: String = "", control: String = "/transport") = """
        <root xmlns="urn:schemas-upnp-org:device-1-0">$base<device>
          <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
          <friendlyName>AirReceiver &amp; TV</friendlyName><serviceList><service>
            <serviceType>$type</serviceType><controlURL>$control</controlURL>
          </service></serviceList></device></root>
    """.trimIndent()

    @Test fun parsesRendererAndResolvesAdvertisedControlUrl() {
        val device = DlnaController.parseRenderer("http://192.168.2.192:49152/device.xml", description())!!
        assertEquals("AirReceiver & TV", device.name)
        assertEquals("http://192.168.2.192:49152/transport", device.controlUrl)
        val base = "<URLBase>http://192.168.2.192:1234/dlna/</URLBase>"
        assertEquals("http://192.168.2.192:1234/dlna/play",
            DlnaController.parseRenderer("http://192.168.2.192/device.xml", description(base, "play"))?.controlUrl)
    }

    @Test fun rejectsUntrustedDescriptionsAndInvalidSsdp() {
        for (xml in listOf(description(control = "http://other-host/transport"),
            "<!DOCTYPE root [<!ENTITY external SYSTEM 'file:///etc/passwd'>]>" + description())) {
            assertTrue(runCatching { DlnaController.parseRenderer("http://tv/device.xml", xml) }.isFailure)
        }
        assertNull(DlnaController.parseRenderer("http://tv/device.xml", description().replace("MediaRenderer", "MediaServer")))
        assertNull(DlnaController.ssdpLocation("NOTIFY * HTTP/1.1\r\nLocation: http://tv/device.xml\r\n"))
        assertNull(DlnaController.ssdpLocation("HTTP/1.1 200 OK\r\nLocation: file:///etc/passwd\r\n"))
        assertEquals("http://tv/device.xml", DlnaController.ssdpLocation("HTTP/1.1 200 OK\r\nlOcAtIoN: http://tv/device.xml\r\n"))
    }

    @Test fun sendsEscapedMetadataBeforePlayAndReportsRendererFailures() = runBlocking {
        val requests = mutableListOf<Pair<String, String>>()
        var fail = false
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/transport") { exchange ->
            val action = exchange.requestHeaders.getFirst("SOAPAction")
            val body = exchange.requestBody.use { it.readBytes().toString(Charsets.UTF_8) }
            requests += action to body
            val response = if (fail) "<s:Fault xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"/>" else "<ok/>"
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(if (fail) 500 else 200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val renderer = DlnaRenderer("TV", "http://127.0.0.1:${server.address.port}/transport", type)
            val url = "http://phone/secret/video?a=1&b=2"
            DlnaController.stop(renderer)
            assertEquals("\"$type#Stop\"", requests.single().first)
            assertTrue(requests.single().second.contains("<InstanceID>0</InstanceID>"))
            requests.clear()
            DlnaController.play(renderer, url, "video/mp4", "Nhạc & <video>")
            assertEquals(listOf("\"$type#SetAVTransportURI\"", "\"$type#Play\""), requests.map { it.first })
            val factory = DocumentBuilderFactory.newInstance()
            val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(requests[0].second.toByteArray()))
            assertEquals(url, document.getElementsByTagName("CurrentURI").item(0).textContent)
            val metadata = document.getElementsByTagName("CurrentURIMetaData").item(0).textContent
            val didl = factory.newDocumentBuilder().parse(ByteArrayInputStream(metadata.toByteArray()))
            assertEquals("Nhạc & <video>", didl.getElementsByTagName("dc:title").item(0).textContent)
            assertEquals(url, didl.getElementsByTagName("res").item(0).textContent)
            requests.clear()
            fail = true
            assertTrue(runCatching { DlnaController.play(renderer, url, "video/mp4", "Video") }.isFailure)
            assertEquals(1, requests.size) // Never Play after SetAVTransportURI was rejected.
        } finally { server.stop(0) }
    }
}
