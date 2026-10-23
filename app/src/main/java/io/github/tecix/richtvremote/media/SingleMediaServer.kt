package io.github.tecix.richtvremote.media

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Serves exactly one file at an unguessable path; supports VLC/DLNA HEAD and byte-range requests. */
class SingleMediaServer(private val file: File, private val mime: String, address: InetAddress) : Closeable {
    private val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val path = "/$token/media"
    private val checkedMime = mime.also { require(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+*-]+").matches(it)) }
    private val server = ServerSocket(0, 4, address)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(4)) {
        Thread(it, "vlc-stream").apply { isDaemon = true }
    }
    val url = "http://${address.hostAddress}:${server.localPort}$path"
    init {
        Thread({
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                clients.add(socket)
                try { workers.execute { socket.use { runCatching { serve(it) } }; clients.remove(socket) } }
                catch (_: Exception) { clients.remove(socket); socket.close() }
            }
        }, "vlc-listener").apply { isDaemon = true; start() }
    }
    private fun serve(socket: Socket) {
        socket.soTimeout = 10_000
        val input = socket.getInputStream()
        val request = line(input).split(' ')
        if (request.size != 3 || request[1] != path || request[0] !in listOf("GET", "HEAD")) {
            socket.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            return
        }
        var range: String? = null
        var ended = false
        repeat(32) {
            if (!ended) {
                val header = line(input)
                if (header.isEmpty()) ended = true
                else if (header.substringBefore(':').equals("Range", true)) {
                    require(range == null); range = header.substringAfter(':').trim()
                }
            }
        }
        require(ended)
        val size = file.length()
        val bytes = if (range != null) byteRange(range!!, size) else 0L until size
        val output = socket.getOutputStream()
        if (bytes == null) {
            output.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$size\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            return
        }
        val count = if (size == 0L) 0L else bytes.last - bytes.first + 1
        val header = buildString {
            append("HTTP/1.1 ${if (range == null) "200 OK" else "206 Partial Content"}\r\n")
            append("Content-Type: $mime\r\nContent-Length: $count\r\nAccept-Ranges: bytes\r\n")
            append("transferMode.dlna.org: Streaming\r\ncontentFeatures.dlna.org: DLNA.ORG_OP=01;DLNA.ORG_CI=0\r\n")
            if (range != null) append("Content-Range: bytes ${bytes.first}-${bytes.last}/$size\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(Charsets.US_ASCII))
        if (request[0] == "HEAD") return
        file.inputStream().use { media ->
            media.channel.position(bytes.first)
            val buffer = ByteArray(64 * 1024)
            var remaining = count
            while (remaining > 0 && !socket.isClosed) {
                val n = media.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (n < 0) break
                output.write(buffer, 0, n)
                remaining -= n
            }
        }
    }
    override fun close() {
        server.close()
        clients.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }
    private fun line(input: InputStream): String {
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() < 8192) {
            val b = input.read()
            require(b >= 0)
            if (b == 10) return bytes.toString("US-ASCII").removeSuffix("\r")
            bytes.write(b)
        }
        error("HTTP header too long")
    }
}

internal fun byteRange(value: String, size: Long): LongRange? {
    if (size <= 0 || !value.startsWith("bytes=") || ',' in value) return null
    val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(value) ?: return null
    val (a, b) = match.destructured
    if (a.isEmpty()) {
        val suffix = b.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return (size - minOf(size, suffix)) until size
    }
    val start = a.toLongOrNull()?.takeIf { it in 0 until size } ?: return null
    val end = if (b.isEmpty()) size - 1 else b.toLongOrNull()?.coerceAtMost(size - 1) ?: return null
    return if (end >= start) start..end else null
}
