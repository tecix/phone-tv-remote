package io.github.tecix.richtvremote.media

import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

internal data class VideoSource(val codec: String, val width: Int, val height: Int, val rotation: Int, val fps: Double)
internal data class CodecLimits(val width: Int, val height: Int, val fps: Double)
internal data class AirPlayCapabilities(
    val avc: CodecLimits = CodecLimits(1920, 1088, 60.0),
    val hevc: CodecLimits = CodecLimits(3840, 2160, 60.0),
    val hevcRotate: Boolean = false,
) {
    fun needsTranscode(src: VideoSource): Boolean {
        val limits = when (src.codec) { "video/avc" -> avc; "video/hevc" -> hevc; else -> return true }
        return maxOf(src.width, src.height) > limits.width || minOf(src.width, src.height) > limits.height ||
            src.fps > limits.fps || (src.codec == "video/hevc" && src.rotation % 360 != 0 && !hevcRotate)
    }
    companion object {
        fun parse(json: String): AirPlayCapabilities = runCatching {
            val o = JSONObject(json)
            require(o.getInt("version") == 1)
            fun limits(key: String): CodecLimits {
                val c = o.getJSONObject(key)
                return CodecLimits(c.getInt("maxWidth"), c.getInt("maxHeight"), c.getDouble("maxFps")).also {
                    require(it.width > 0 && it.height > 0 && it.fps.isFinite() && it.fps > 0)
                }
            }
            AirPlayCapabilities(limits("avc"), limits("hevc"), o.getJSONObject("hevc").getBoolean("rotate"))
        }.getOrDefault(AirPlayCapabilities())

        suspend fun fetch(receiver: AirPlayReceiver): AirPlayCapabilities = withContext(Dispatchers.IO) {
            runCatching {
                val host = if (':' in receiver.host) "[${receiver.host.removeSurrounding("[", "]")}]" else receiver.host
                val c = URI("http://$host:${receiver.port}/tinytv/caps").toURL().openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 1500; c.readTimeout = 1500
                    if (c.responseCode != 200) AirPlayCapabilities() else {
                        val bytes = c.inputStream.use { input ->
                            val buffer = ByteArray(16 * 1024 + 1)
                            var size = 0
                            while (size < buffer.size) {
                                val n = input.read(buffer, size, buffer.size - size)
                                if (n < 0) break
                                size += n
                            }
                            buffer.copyOf(size)
                        }
                        if (bytes.size > 16 * 1024) AirPlayCapabilities() else parse(bytes.toString(Charsets.UTF_8))
                    }
                } finally { c.disconnect() }
            }.getOrDefault(AirPlayCapabilities())
        }
    }
}
