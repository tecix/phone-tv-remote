package io.github.tecix.richtvremote.media

import android.content.Intent
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/** Incoming shares remain inert until the user chooses Play on TV. */
/** [headers]: Referer/User-Agent the sending app needed for this link (only for web links). */
data class SharedMedia(val uri: Uri, val mime: String, val title: String, val headers: Map<String, String> = emptyMap()) {
    val isFile get() = uri.scheme in listOf("content", "file")
    companion object {
        @Suppress("DEPRECATION")
        fun from(intent: Intent): SharedMedia? = allFrom(intent).singleOrNull()

        @Suppress("DEPRECATION")
        fun allFrom(intent: Intent, resolver: ContentResolver? = null): List<SharedMedia> {
            if (intent.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW)) return emptyList()
            val streams = when (intent.action) {
                Intent.ACTION_VIEW -> listOfNotNull(intent.data)
                Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                else -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            }.ifEmpty {
                intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
            }.distinct()
            require(streams.size <= 100) { "Share up to 100 media files at a time" }
            if (streams.isNotEmpty()) return streams.map { stream ->
                if (stream.scheme in listOf("http", "https")) return@map fromUrl(stream.toString(), intent)
                require(stream.scheme in listOf("content", "file")) { "Share a media file using its app’s Share action" }
                val title = runCatching {
                    resolver?.query(stream, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                }.getOrNull() ?: intent.getStringExtra(Intent.EXTRA_TITLE) ?: stream.lastPathSegment ?: "Shared media"
                val providerType = runCatching { resolver?.getType(stream) }.getOrNull()
                val mime = resolveMediaMime(providerType, title) ?: resolveMediaMime(intent.type, title)
                    ?: if (intent.type.isNullOrBlank() || intent.type in listOf("*/*", "application/octet-stream", "application/binary", "application/unknown"))
                        "application/octet-stream" else error("Choose a video, audio or photo file. Documents and archives cannot play on the TV.")
                SharedMedia(stream, mime, title)
            }
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            val urls = Regex("https?://[^\\s<>\"]+").findAll(text).map { it.value }.toList()
            require(urls.size == 1) { "Share one video or audio link at a time" }
            return listOf(fromUrl(urls.single(), intent))
        }

        private fun fromUrl(value: String, intent: Intent): SharedMedia {
            val uri = Uri.parse(value)
            requireHttpMedia(uri)
            require(uri.host !in listOf("drive.google.com", "docs.google.com")) {
                "In Google Drive, use Send a copy or Open with to share the actual video/audio file. A private Drive page cannot play on the TV."
            }
            // Streaming CDNs disguise playlists and segments as images (playlist.jpg); only the sharing
            // app's own image/* type makes a link a photo.
            val guessed = resolveMediaMime(intent.type, uri.lastPathSegment.orEmpty())
            val mime = if (guessed?.startsWith("image/") == true && intent.type?.startsWith("image/") != true) "video/*" else guessed
            val title = intent.getStringExtra(Intent.EXTRA_TITLE) ?: intent.getStringExtra("title")?.takeIf { it.isNotBlank() }
            return SharedMedia(uri, mime ?: "video/*", title ?: uri.host.orEmpty(), linkHeaders(intent))
        }
    }
}

/**
 * Request headers video apps attach to "Open with" links: MX Player's "headers" string array
 * (name, value, …) or a Bundle under Browser.EXTRA_HEADERS / the media HTTP_HEADERS extra.
 * Only Referer and User-Agent are kept; AirPlay passes them on to the TV.
 */
internal fun linkHeaders(intent: Intent): Map<String, String> {
    val raw = linkedMapOf<String, String>()
    runCatching { intent.getStringArrayExtra("headers") }.getOrNull()?.toList()?.chunked(2)?.forEach {
        if (it.size == 2) raw[it[0]] = it[1]
    }
    for (key in listOf("com.android.browser.headers", "android.media.intent.extra.HTTP_HEADERS")) {
        @Suppress("DEPRECATION")
        val bundle = runCatching { intent.getBundleExtra(key) }.getOrNull() ?: continue
        bundle.keySet().forEach { k -> bundle.getString(k)?.let { raw[k] = it } }
    }
    val out = linkedMapOf<String, String>()
    raw.forEach { (k, v) ->
        val name = when { k.equals("Referer", true) -> "Referer"; k.equals("User-Agent", true) -> "User-Agent"; else -> null }
        if (name != null && v.isNotBlank() && v.length < 1024 && v.none { it.isISOControl() } &&
            (name != "Referer" || Regex("(?i)https?://\\S+").matches(v))) out[name] = v.trim()
    }
    return out
}

/** Provider MIME types vary widely for MKV, TS and other containers; use the filename when needed. */
internal fun resolveMediaMime(type: String?, name: String): String? {
    val normalized = type?.substringBefore(';')?.trim()?.lowercase()
    if (normalized != null && Regex("(?:audio|video|image)/[a-z0-9.+*-]+").matches(normalized) && !normalized.endsWith("/*")) return normalized
    val extension = name.substringBefore('?').substringAfterLast('.', "").lowercase()
    val known = mapOf(
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mkv" to "video/x-matroska", "avi" to "video/x-msvideo",
        "mov" to "video/quicktime", "webm" to "video/webm", "3gp" to "video/3gpp", "3g2" to "video/3gpp2",
        "ts" to "video/mp2t", "mts" to "video/mp2t", "m2ts" to "video/mp2t", "vob" to "video/mpeg",
        "mpeg" to "video/mpeg", "mpg" to "video/mpeg", "wmv" to "video/x-ms-wmv", "flv" to "video/x-flv", "ogv" to "video/ogg",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "flac" to "audio/flac",
        "wav" to "audio/wav", "ogg" to "audio/ogg", "opus" to "audio/ogg", "amr" to "audio/amr",
        "wma" to "audio/x-ms-wma", "aiff" to "audio/aiff", "aif" to "audio/aiff", "ac3" to "audio/ac3", "dts" to "audio/vnd.dts",
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp",
        "gif" to "image/gif", "bmp" to "image/bmp", "heic" to "image/heic", "heif" to "image/heif", "avif" to "image/avif",
    )
    return known[extension] ?: normalized?.takeIf { it in listOf("video/*", "audio/*", "image/*") }
}

fun requireHttpMedia(uri: Uri) {
    require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null &&
        uri.toString().none { it.isISOControl() }) { "Use an HTTP or HTTPS media URL without embedded credentials" }
}
