package io.github.tecix.richtvremote.media

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IncomingIntentTest {
    @Test fun mediaAndGenericSharesResolveForSingleMultipleAndOpenWith() {
        val pm = RuntimeEnvironment.getApplication().packageManager
        for (type in listOf("video/mp4", "audio/flac", "image/jpeg", "application/x-matroska", "application/octet-stream", "*/*")) {
            for (action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW)) {
                val intent = Intent(action).setType(type)
                if (action == Intent.ACTION_VIEW) intent.setDataAndType(Uri.parse("content://files/video"), type)
                assertTrue("$action $type must resolve", pm.queryIntentActivities(intent, 0)
                    .any { it.activityInfo.name == "io.github.tecix.richtvremote.MainActivity" })
            }
        }
    }

    @Test fun renameRetainsInstalledIdentityAndLegacyActivityAlias() {
        val app = RuntimeEnvironment.getApplication()
        assertEquals("io.github.tecix.textv", app.packageName)
        assertEquals("Rich TV Remote", app.packageManager.getApplicationLabel(app.applicationInfo).toString())
        val alias = app.packageManager.getActivityInfo(ComponentName(app.packageName, "io.github.tecix.textv.MainActivity"), 0)
        assertEquals("io.github.tecix.richtvremote.MainActivity", alias.targetActivity)
    }

    @Test fun linksDisguisedAsImagesStayVideoUnlessTheAppSaysImage() {
        val url = "https://nebula.example/hls/1080p/playlist.jpg"
        val text = SharedMedia.from(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url))!!
        assertEquals("video/*", text.mime)
        val photo = SharedMedia.from(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_TEXT, url))!!
        assertEquals("image/jpeg", photo.mime)
    }

    @Test fun webVideoLinksFromOpenWithResolveAndKeepHeaders() {
        val pm = RuntimeEnvironment.getApplication().packageManager
        val url = "https://nebula.example/hls/1080p/playlist.jpg"
        for (type in listOf("video/mp4", "application/x-mpegURL", "application/vnd.apple.mpegurl", "video/*")) {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), type)
            assertTrue("VIEW $type link must resolve", pm.queryIntentActivities(intent, 0)
                .any { it.activityInfo.name == "io.github.tecix.richtvremote.MainActivity" })
        }
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "application/x-mpegURL")
            .putExtra("title", "Episode 1")
            .putExtra("headers", arrayOf("Referer", "https://site.example/watch", "Cookie", "secret", "User-Agent", "WVC"))
        val media = SharedMedia.from(view)!!
        assertEquals("video/*", media.mime)
        assertEquals("Episode 1", media.title)
        assertEquals(mapOf("Referer" to "https://site.example/watch", "User-Agent" to "WVC"), media.headers)
    }
}
