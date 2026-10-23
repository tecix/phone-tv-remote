package io.github.tecix.richtvremote.media

import android.content.Intent
import android.net.Uri
import io.github.tecix.richtvremote.ha.playbackFromHa
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaTest {
    @Test fun receivesFileAndLinkSharesWithoutStartingPlayback() {
        val file = SharedMedia.from(Intent(Intent.ACTION_SEND).setType("audio/mpeg")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://com.google.android.apps.docs.storage/document/123")))!!
        assertTrue(file.isFile); assertEquals("audio/mpeg", file.mime)
        val link = SharedMedia.from(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Listen: https://example.com/audio.mp3?x=1&y=2"))!!
        assertFalse(link.isFile); assertEquals("https://example.com/audio.mp3?x=1&y=2", link.uri.toString())
    }
    @Test fun rejectsPrivateDrivePagesAndUnsupportedContent() {
        for (text in listOf("https://drive.google.com/file/d/secret/view", "hello", "file:///etc/passwd", "https://user:pass@example.com/a.mp4")) {
            assertTrue(runCatching { SharedMedia.from(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)) }.isFailure)
        }
        assertTrue(runCatching { SharedMedia.from(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, Uri.parse("content://docs/document"))) }.isFailure)
        assertNull(SharedMedia.from(Intent(Intent.ACTION_MAIN)))
    }
    @Test fun acceptsGenericVendorContainersPhotosAndOpenWith() {
        for ((name, type) in listOf("movie.mkv" to "video/x-matroska", "movie.m2ts" to "video/mp2t",
            "music.flac" to "audio/flac", "photo.heic" to "image/heic")) {
            val shared = SharedMedia.from(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://files/$name"), "*/*"))!!
            assertEquals(type, shared.mime)
            assertEquals(name, shared.title)
        }
        assertEquals("video/x-matroska", SharedMedia.from(Intent(Intent.ACTION_SEND).setType("application/x-matroska")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://files/movie.mkv")))!!.mime)
    }
    @Test fun retainsAllFilesFromMultipleAndClipDataShares() {
        val a = Uri.parse("content://files/a.mp4")
        val b = Uri.parse("content://files/b.mp3")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(a, b))
        assertEquals(listOf(a, b), SharedMedia.allFrom(intent).map { it.uri })
        assertEquals(listOf("video/mp4", "audio/mpeg"), SharedMedia.allFrom(intent).map { it.mime })
        val clip = android.content.ClipData.newRawUri("files", a).apply { addItem(android.content.ClipData.Item(b)) }
        assertEquals(2, SharedMedia.allFrom(Intent(Intent.ACTION_SEND).setType("*/*").apply { clipData = clip }).size)
    }
    @Test fun playbackUsesRealDurationAndSeekCapability() {
        assertNull(playbackFromHa(JSONObject(), "playing"))
        val attrs = JSONObject().put("media_duration", 120).put("media_position", 30).put("supported_features", 2)
        val p = playbackFromHa(attrs, "paused")!!
        assertEquals(120_000, p.durationMs); assertEquals(30_000, p.positionMs); assertTrue(p.canSeek)
        assertEquals(30_000, p.positionAt(p.sampledAt + 10_000))
        assertFalse(playbackFromHa(attrs.put("supported_features", 0), "playing")!!.canSeek)
    }
    @Test fun timeFormattingAndExtrapolation() {
        assertEquals("1:05", formatMediaTime(65_000)); assertEquals("1:01:01", formatMediaTime(3_661_000))
        assertEquals("0:00", formatMediaTime(-1))
        val p = MediaPlayback("id", "title", 30_000, 120_000, true, true, sampledAt = 1_000)
        assertEquals(35_000, p.positionAt(6_000)); assertEquals(120_000, p.positionAt(200_000))
    }
}
