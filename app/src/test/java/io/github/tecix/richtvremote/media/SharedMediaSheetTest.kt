package io.github.tecix.richtvremote.media

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.tecix.richtvremote.data.Route
import io.github.tecix.richtvremote.ui.screens.SharedMediaSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SharedMediaSheetTest {
    @get:Rule val compose = createComposeRule()
    private val device = DlnaRenderer("AirReceiver", "http://192.168.2.192:3500/control", "urn:schemas-upnp-org:service:AVTransport:1")

    @Test fun dlnaRequiresSelectionAndWorksWithoutRemoteConnection() {
        val selected = mutableStateOf<DlnaRenderer?>(null)
        var played = 0
        compose.setContent {
            MaterialTheme {
                SharedMediaSheet(SharedMedia(Uri.parse("content://media/video/1"), "video/mp4", "Movie"),
                    Route.DIRECT, connected = false, busy = false, onDismiss = {}, onPlay = { played++ },
                    destination = MediaDestination.DLNA, renderers = listOf(device), renderer = selected.value,
                    onRendererChange = { selected.value = it })
            }
        }
        compose.onNodeWithText("Play on receiver").assertIsNotEnabled()
        compose.onNodeWithText("AirReceiver · 192.168.2.192").performClick()
        compose.onNodeWithText("Play on AirReceiver").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, played) }
    }

    @Test fun vlcStillRequiresRemoteConnection() {
        compose.setContent {
            MaterialTheme {
                SharedMediaSheet(SharedMedia(Uri.parse("https://example.com/movie.mp4"), "video/mp4", "Movie"),
                    Route.HOME_ASSISTANT, connected = false, busy = false, onDismiss = {}, onPlay = {},
                    destination = MediaDestination.VLC)
            }
        }
        compose.onNodeWithText("Play in VLC").assertIsNotEnabled()
    }
}
