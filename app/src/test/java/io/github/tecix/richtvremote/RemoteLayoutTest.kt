package io.github.tecix.richtvremote

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import io.github.tecix.richtvremote.media.MediaPlayback
import io.github.tecix.richtvremote.data.Route
import io.github.tecix.richtvremote.data.SavedTv
import io.github.tecix.richtvremote.data.Settings
import io.github.tecix.richtvremote.direct.DeviceInfo
import io.github.tecix.richtvremote.ui.screens.RemoteActions
import io.github.tecix.richtvremote.ui.screens.RemoteScreen
import io.github.tecix.richtvremote.ui.theme.RichTvRemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class RemoteLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun topBarRoutesAndSearchActionsRemainIndependent() {
        val route = mutableStateOf(Route.DIRECT)
        val keys = mutableListOf<TvKey>()
        var voice = 0
        var text = 0
        var files = 0
        var stops = 0
        var disconnects = 0
        val stopping = mutableStateOf(false)
        val receiver = mutableStateOf<io.github.tecix.richtvremote.media.DlnaRenderer?>(
            io.github.tecix.richtvremote.media.DlnaRenderer("Living room DLNA", "http://192.168.1.50/control", "urn:schemas-upnp-org:service:AVTransport:1"))
        var seek = -1L
        val tv = SavedTv("Living room", "192.168.1.50")
        val actions = RemoteActions(
            press = { keys += it }, longPress = {}, launchApp = {}, setRoute = { route.value = it },
            openDevices = {}, openSettings = {}, openKeyboard = {}, pair = {}, retry = {},
            runMacro = {}, saveMacro = {}, deleteMacro = {}, refreshTile = {}, irPower = {},
            openVoice = { voice++ }, openVoiceText = { text++ }, seekTo = { seek = it },
            pickMedia = { files++ },
            disconnectDlna = { disconnects++; receiver.value = null },
        )
        compose.setContent {
            RichTvRemoteTheme {
                RemoteScreen(Settings(route = route.value, tvs = listOf(tv), selectedHost = tv.host),
                    DirectState.Connected(tv, DeviceInfo("TV", "Android TV")), HaState.Ready("TV"),
                    TvStatus(playback = MediaPlayback("vlc", "Movie", 30_000, 120_000, false, true)), HaTile(), true, SnackbarHostState(), actions,
                    sharingFile = true, onStopSharing = { stops++; stopping.value = true }, stoppingStreaming = stopping.value,
                    dlnaReceiver = receiver.value)
            }
        }
        compose.onNodeWithContentDescription("Playback position").performSemanticsAction(SemanticsActions.SetProgress) { it(60_000f) }
        compose.onNodeWithContentDescription("Play or pause").performClick()
        compose.onNodeWithText("Search SmartTube").performClick()
        compose.onNodeWithContentDescription("SmartTube voice search").performClick()
        compose.onNodeWithContentDescription("Browser").performClick()
        compose.onNodeWithContentDescription("Choose file for TV").performClick()
        compose.onNodeWithText("DLNA · Living room DLNA").performClick()
        compose.onNodeWithText("Disconnect DLNA").performClick()
        compose.onNodeWithText("DLNA · Living room DLNA").assertDoesNotExist()
        compose.onNodeWithText("Stop TV streaming").performClick()
        compose.onNodeWithText("Stopping TV streaming…").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(listOf(TvKey.MEDIA_PLAY_PAUSE, TvKey.ASSIST), keys)
            assertEquals(1, text)
            assertEquals(1, voice)
            assertEquals(1, files)
            assertEquals(1, stops)
            assertEquals(1, disconnects)
            assertEquals(60_000L, seek)
        }
        compose.onNodeWithContentDescription("Direct mode. Switch to Home Assistant").performClick()
        compose.onNodeWithContentDescription("Home Assistant mode. Switch to Direct").assertExists().performClick()
        compose.runOnIdle { assertEquals(Route.DIRECT, route.value) }
    }
}
