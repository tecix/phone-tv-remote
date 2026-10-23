package io.github.tecix.richtvremote.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

enum class MediaDestination { DLNA, VLC }

data class DlnaRenderer(val name: String, val controlUrl: String, val serviceType: String) {
    val host: String get() = URI(controlUrl).host
}

/** Standard UPnP renderer control. AirReceiver fetches the original media from the phone over HTTP. */
object DlnaController {
    private const val rendererType = "urn:schemas-upnp-org:device:MediaRenderer:1"

    suspend fun discover(): List<DlnaRenderer> = withContext(Dispatchers.IO) {
        val locations = linkedSetOf<String>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 300
            val message = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $rendererType\r\n\r\n").toByteArray(Charsets.US_ASCII)
            repeat(2) { socket.send(DatagramPacket(message, message.size, InetAddress.getByName("239.255.255.250"), 1900)) }
            val deadline = System.nanoTime() + 3_000_000_000L
            while (System.nanoTime() < deadline && locations.size < 16) {
                currentCoroutineContext().ensureActive()
                val packet = DatagramPacket(ByteArray(8192), 8192)
                try {
                    socket.receive(packet)
                    val response = String(packet.data, 0, packet.length, Charsets.US_ASCII)
                    ssdpLocation(response)?.let { location ->
                        // Only fetch descriptions from the device that answered discovery.
                        if (runCatching { InetAddress.getByName(URI(location).host) == packet.address }.getOrDefault(false)) {
                            locations += location
                        }
                    }
                } catch (_: SocketTimeoutException) { /* Continue until the discovery window closes. */ }
            }
        }
        locations.mapNotNull { location ->
            currentCoroutineContext().ensureActive()
            runCatching { parseRenderer(location, request(location)) }.getOrNull()
        }.distinctBy { it.controlUrl }
    }

    suspend fun play(renderer: DlnaRenderer, url: String, mime: String, title: String) = withContext(Dispatchers.IO) {
        requireWebUrl(url)
        action(renderer, "SetAVTransportURI", linkedMapOf(
            "InstanceID" to "0", "CurrentURI" to url,
            "CurrentURIMetaData" to metadata(url, mime, title),
        ))
        currentCoroutineContext().ensureActive()
        action(renderer, "Play", linkedMapOf("InstanceID" to "0", "Speed" to "1"))
    }

    suspend fun stop(renderer: DlnaRenderer) = withContext(Dispatchers.IO) {
        action(renderer, "Stop", linkedMapOf("InstanceID" to "0"))
    }

    private fun action(renderer: DlnaRenderer, name: String, arguments: Map<String, String>) {
        request(renderer.controlUrl, soap(renderer.serviceType, name, arguments), "\"${renderer.serviceType}#$name\"")
    }

    private fun request(url: String, body: String? = null, soapAction: String? = null): String {
        val connection = requireWebUrl(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 2500
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = false
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                connection.setRequestProperty("SOAPAction", soapAction)
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val data = (if (status in 200..299) connection.inputStream else connection.errorStream)?.use {
                it.readBytesLimited(256 * 1024).toString(Charsets.UTF_8)
            }.orEmpty()
            if (status == 404 || status == 410) throw java.io.IOException("DLNA receiver endpoint changed; reconnect to the receiver")
            check(status in 200..299) { "DLNA receiver rejected the command (HTTP $status). Check its DLNA settings and video format." }
            check(!data.contains(Regex("<(?:[\\w-]+:)?Fault[\\s/>]"))) { "DLNA receiver returned a playback error" }
            return data
        } finally { connection.disconnect() }
    }

    internal fun ssdpLocation(response: String): String? {
        if (!response.lineSequence().firstOrNull().orEmpty().startsWith("HTTP/1.1 200")) return null
        return response.lineSequence().firstOrNull { it.substringBefore(':').equals("location", true) }
            ?.substringAfter(':')?.trim()?.takeIf { runCatching { requireWebUrl(it) }.isSuccess }
    }

    internal fun parseRenderer(location: String, xml: String): DlnaRenderer? {
        val origin = requireWebUrl(location)
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml)) { "DLNA description contains a document type" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
        }
        val document = builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val root = document.documentElement
        val base = root.childText("URLBase").ifBlank { location }.let { requireWebUrl(it) }
        val devices = root.getElementsByTagNameNS("*", "device")
        for (i in 0 until devices.length) {
            val device = devices.item(i) as Element
            if (!device.childText("deviceType").startsWith("urn:schemas-upnp-org:device:MediaRenderer:")) continue
            val services = device.childElement("serviceList")?.getElementsByTagNameNS("*", "service") ?: continue
            for (j in 0 until services.length) {
                val service = services.item(j) as Element
                val type = service.childText("serviceType")
                if (!Regex("urn:schemas-upnp-org:service:AVTransport:[1-9][0-9]*").matches(type)) continue
                val path = service.childText("controlURL").takeIf { it.isNotBlank() } ?: continue
                val control = requireWebUrl(base.resolve(path).toString())
                require(control.host.equals(origin.host, true)) { "DLNA control endpoint changed host" }
                return DlnaRenderer(device.childText("friendlyName").ifBlank { origin.host }, control.toString(), type)
            }
        }
        return null
    }

    internal fun metadata(url: String, mime: String, title: String): String {
        val candidate = if (mime.endsWith("/*") || mime == "application/octet-stream")
            java.net.URLConnection.guessContentTypeFromName(URI(url).path) ?: mime else mime
        val mediaType = candidate.takeIf { Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+*-]+").matches(it) } ?: "application/octet-stream"
        val itemClass = when {
            mediaType.startsWith("audio/") -> "object.item.audioItem.musicTrack"
            mediaType.startsWith("image/") -> "object.item.imageItem.photo"
            else -> "object.item.videoItem"
        }
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>${escape(title)}</dc:title>" +
            "<upnp:class>$itemClass</upnp:class><res protocolInfo=\"http-get:*:${escape(mediaType)}:*\">${escape(url)}</res></item></DIDL-Lite>"
    }

    internal fun soap(type: String, action: String, arguments: Map<String, String>): String =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
            "<u:$action xmlns:u=\"$type\">" + arguments.entries.joinToString("") { (key, value) -> "<$key>${escape(value)}</$key>" } +
            "</u:$action></s:Body></s:Envelope>"

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun requireWebUrl(value: String): URI = URI(value).also {
        require(it.scheme in listOf("http", "https") && !it.host.isNullOrBlank() && it.rawUserInfo == null && it.rawFragment == null)
    }

    private fun Element.childElement(name: String): Element? = (0 until childNodes.length)
        .map { childNodes.item(it) }.filterIsInstance<Element>().firstOrNull { it.localName == name }
    private fun Element.childText(name: String) = childElement(name)?.textContent?.trim().orEmpty()
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val n = read(buffer)
        if (n < 0) return output.toByteArray()
        check(output.size() + n <= limit) { "DLNA response too large" }
        output.write(buffer, 0, n)
    }
}
