package io.github.tecix.richtvremote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.tecix.richtvremote.data.Route
import io.github.tecix.richtvremote.data.SavedTv
import io.github.tecix.richtvremote.data.Settings
import io.github.tecix.richtvremote.direct.DeviceInfo
import io.github.tecix.richtvremote.direct.VolumeInfo
import io.github.tecix.richtvremote.discovery.DiscoveredTv
import io.github.tecix.richtvremote.ha.HaConfig
import io.github.tecix.richtvremote.media.MediaPlayback
import io.github.tecix.richtvremote.ui.screens.DevicesScreen
import io.github.tecix.richtvremote.ui.screens.RemoteActions
import io.github.tecix.richtvremote.ui.screens.RemoteScreen
import io.github.tecix.richtvremote.ui.screens.VoiceSearchSheet
import io.github.tecix.richtvremote.ui.screens.SettingsScreen
import io.github.tecix.richtvremote.ui.screens.KeyboardSheet
import io.github.tecix.richtvremote.ui.theme.RichTvRemoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
/** Renders each screen to app/build/outputs/roborazzi/ so the design can be reviewed without a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val tv = SavedTv("Living room", "192.168.1.50")
    private val settings = Settings(
        route = Route.DIRECT,
        tvs = listOf(tv, SavedTv("Bedroom", "192.168.1.61")),
        selectedHost = tv.host,
        ha = HaConfig("http://homeassistant.local:8123", "token", "remote.living_room_tv"),
        dynamicColor = false,
    )
    private val noop = RemoteActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private val dlnaRenderer = io.github.tecix.richtvremote.media.DlnaRenderer("TV638_DVB-192[DLNA]",
        "http://192.168.2.192:3500/AVTransport/control.xml", "urn:schemas-upnp-org:service:AVTransport:1")

    @Test fun remoteDlnaSession() = shot("remote_dlna_session") {
        RemoteScreen(settings, DirectState.Connected(tv, DeviceInfo("Philips", "PUS8808")), HaState.Unconfigured,
            TvStatus(playback = MediaPlayback("dlna", "Shared video", 65_000, 300_000, false, true)),
            HaTile(), true, SnackbarHostState(), noop, sharingFile = true, dlnaReceiver = dlnaRenderer)
    }

    @Composable
    private fun sharePanel() {
        directPreview(dlnaRenderer)
        io.github.tecix.richtvremote.ui.screens.SharedMediaSheet(
            io.github.tecix.richtvremote.media.SharedMedia(android.net.Uri.parse("content://media/movie"), "video/mp4", "Shared video"),
            Route.DIRECT, false, false, {}, {}, destination = io.github.tecix.richtvremote.media.MediaDestination.DLNA,
            renderers = listOf(dlnaRenderer), renderer = dlnaRenderer)
    }

    @Test fun shareDlna() = shot("share_dlna") { sharePanel() }

    @Test
    @Config(qualifiers = "+night")
    fun shareDlnaDark() = shot("share_dlna_dark") { sharePanel() }

    private fun shot(name: String, beforeCapture: () -> Unit = {}, content: @Composable () -> Unit) {
        // A focused text field's blinking cursor never lets the UI go idle.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RichTvRemoteTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        // Scroll semantics need advancing frames; freeze again before the screenshot's idle check.
        compose.mainClock.autoAdvance = true
        beforeCapture()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        captureScreenRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Composable
    private fun directPreview(receiver: io.github.tecix.richtvremote.media.DlnaRenderer? = null) {
        RemoteScreen(settings, DirectState.Connected(tv, DeviceInfo("Philips", "PUS8808")), HaState.Unconfigured,
            TvStatus(VolumeInfo(18, 60, false), true, "org.videolan.vlc",
                MediaPlayback("vlc", "Shared video", 65_000, 300_000, false, true)), HaTile(), true, SnackbarHostState(), noop,
            dlnaReceiver = receiver)
    }

    @Composable
    private fun devicesPreview() {
        DevicesScreen(settings.tvs, tv.host, listOf(DiscoveredTv("Kitchen Chromecast", "192.168.1.77")),
            SnackbarHostState(), {}, {}, {}, { _, _ -> }, {})
    }

    @Composable
    private fun settingsPreview() {
        SettingsScreen(settings, HaState.Ready("Living Room Philips TV"), SnackbarHostState(),
            {}, {}, { Result.success("") }, {}, {}, {}, {}, {}, {})
    }

    @Test fun remoteMediaAndShortcuts() = shot("remote_media_shortcuts", beforeCapture = {
        compose.onNodeWithText("Quick launch").performScrollTo()
    }) { directPreview() }

    @Test fun dlnaConnectionMenu() = shot("dlna_connection_menu", beforeCapture = {
        compose.onNodeWithText("DLNA · ${dlnaRenderer.name}").performClick()
        compose.onNodeWithText("Disconnect DLNA").assertIsDisplayed()
    }) {
        RemoteScreen(settings, DirectState.Connected(tv, DeviceInfo("Philips", "PUS8808")), HaState.Unconfigured,
            TvStatus(), HaTile(), true, SnackbarHostState(), noop, dlnaReceiver = dlnaRenderer)
    }

    @Test fun keyboard() = shot("keyboard", beforeCapture = {
        compose.onNodeWithText("Text").performTextInput("Hello, TV!")
    }) { directPreview(); KeyboardSheet({}, {}, {}, {}) }

    @Test fun addTv() = shot("add_tv", beforeCapture = {
        compose.onNodeWithText("Add by IP", useUnmergedTree = true).performClick()
    }) { devicesPreview() }

    @Test fun macroEditor() = shot("macro_editor", beforeCapture = {
        compose.onNodeWithText("Captions").performScrollTo().performClick()
    }) { settingsPreview() }

    @Test fun settingsLight() = shot("settings") { settingsPreview() }

    @Test fun settingsPhoneOptions() = shot("settings_phone", beforeCapture = {
        compose.onNodeWithText("Reset pairing identity").performScrollTo()
    }) { settingsPreview() }

    @Test fun shareVlc() = shot("share_vlc") {
        directPreview()
        io.github.tecix.richtvremote.ui.screens.SharedMediaSheet(
            io.github.tecix.richtvremote.media.SharedMedia(android.net.Uri.parse("content://media/movie"), "video/mp4", "Shared video.mp4"),
            Route.DIRECT, true, false, {}, {}, destination = io.github.tecix.richtvremote.media.MediaDestination.VLC)
    }

    /** Stands in for a TV screenshot: a dusk-colored gradient with a "video" block. */
    private fun fakeScreenshot(): ImageBitmap {
        val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 960f, 540f, 0xFF283C86.toInt(), 0xFFB24592.toInt(), android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 960f, 540f, paint)
        canvas.drawRect(80f, 90f, 520f, 340f, android.graphics.Paint().apply { color = 0x55FFFFFF })
        return bitmap.asImageBitmap()
    }

    @Test
    fun remoteDirect() = shot("remote_direct") {
        RemoteScreen(
            settings, DirectState.Connected(tv, DeviceInfo("Philips", "PUS8808")), HaState.Unconfigured,
            TvStatus(VolumeInfo(18, 60, false), true, "org.videolan.vlc",
                MediaPlayback("vlc", "Shared video", 65_000, 300_000, false, true)), HaTile(), true, SnackbarHostState(), noop,
        )
    }

    @Test
    @Config(qualifiers = "+night")
    fun remoteDirectDark() = shot("remote_direct_dark") {
        RemoteScreen(
            settings, DirectState.Connected(tv, DeviceInfo("Philips", "PUS8808")), HaState.Unconfigured,
            TvStatus(VolumeInfo(18, 60, false), true, "org.videolan.vlc",
                MediaPlayback("vlc", "Shared video", 65_000, 300_000, false, true)), HaTile(), true, SnackbarHostState(), noop,
        )
    }

    @Test
    fun remoteHomeAssistant() = shot("remote_ha") {
        RemoteScreen(
            settings.copy(route = Route.HOME_ASSISTANT), DirectState.NoTv, HaState.Ready("Philips TV"),
            TvStatus(), HaTile("SmartTube", fakeScreenshot(), System.currentTimeMillis() - 13_984_000L), true,
            SnackbarHostState(), noop,
        )
    }

    @Test
    fun remoteNeedsPairing() = shot("remote_needs_pairing") {
        RemoteScreen(settings, DirectState.NeedsPairing(tv), HaState.Unconfigured, TvStatus(), HaTile(), false, SnackbarHostState(), noop)
    }

    @Test
    fun devices() = shot("devices") {
        DevicesScreen(
            saved = settings.tvs,
            selectedHost = tv.host,
            discovered = listOf(DiscoveredTv("Kitchen Chromecast", "192.168.1.77")),
            snackbar = SnackbarHostState(),
            onBack = {}, onSelect = {}, onPair = {}, onRename = { _, _ -> }, onRemove = {},
        )
    }

    @Test
    @Config(qualifiers = "+night")
    fun settingsDark() = shot("settings_dark") {
        SettingsScreen(
            settings, HaState.Ready("Living Room Philips TV"), SnackbarHostState(),
            onBack = {}, onSaveHa = {}, onTestHa = { Result.success("") },
            onVolumeKeys = {}, onHaptics = {}, onDynamicColor = {}, onSaveMacro = {}, onDeleteMacro = {}, onForgetIdentity = {},
        )
    }
    @Test
    fun voiceSearch() = shot("voice_search") {
        directPreview()
        VoiceSearchSheet("DIRECT", "vi-VN", {}, true, {}, { _, _ -> true }, startWithVoice = false)
    }

    @Test
    @Config(qualifiers = "+night")
    fun voiceSearchDark() = shot("voice_search_dark") {
        directPreview()
        VoiceSearchSheet("DIRECT", "vi-VN", {}, true, {}, { _, _ -> true }, startWithVoice = false)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun voiceSearchCompact() = shot("voice_search_compact") {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
            directPreview()
            VoiceSearchSheet("DIRECT", "vi-VN", {}, false, {}, { _, _ -> false }, startWithVoice = false)
        }
    }

}
