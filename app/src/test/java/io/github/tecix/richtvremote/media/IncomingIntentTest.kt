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
}
