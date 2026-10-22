package io.github.tecix.textv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.tecix.textv.data.Route
import io.github.tecix.textv.data.SavedTv
import io.github.tecix.textv.data.Settings
import io.github.tecix.textv.direct.DeviceInfo
import io.github.tecix.textv.direct.VolumeInfo
import io.github.tecix.textv.discovery.DiscoveredTv
import io.github.tecix.textv.ha.HaConfig
import io.github.tecix.textv.ui.screens.DevicesScreen
import io.github.tecix.textv.ui.screens.RemoteActions
import io.github.tecix.textv.ui.screens.RemoteScreen
import io.github.tecix.textv.ui.screens.SettingsScreen
import io.github.tecix.textv.ui.theme.TexTvTheme
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
    private val noop = RemoteActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private fun shot(name: String, content: @Composable () -> Unit) {
        // A focused text field's blinking cursor never lets the UI go idle.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TexTvTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        captureScreenRoboImage("build/outputs/roborazzi/$name.png")
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
            TvStatus(VolumeInfo(18, 60, false), true, "com.netflix.ninja"), HaTile(), true, SnackbarHostState(), noop,
        )
    }

    @Test
    @Config(qualifiers = "+night")
    fun remoteDirectDark() = shot("remote_direct_dark") {
        RemoteScreen(
            settings, DirectState.Connected(tv, DeviceInfo("Philips", "PUS8808")), HaState.Unconfigured,
            TvStatus(VolumeInfo(18, 60, false), true, "com.netflix.ninja"), HaTile(), true, SnackbarHostState(), noop,
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
            onVolumeKeys = {}, onHaptics = {}, onDynamicColor = {}, onCaptionMacro = {}, onForgetIdentity = {},
        )
    }
}
