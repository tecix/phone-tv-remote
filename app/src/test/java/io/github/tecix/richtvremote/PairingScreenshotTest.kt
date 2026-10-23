package io.github.tecix.richtvremote

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.tecix.richtvremote.data.SavedTv
import io.github.tecix.richtvremote.data.Settings
import io.github.tecix.richtvremote.ui.screens.PairingDialog
import io.github.tecix.richtvremote.ui.screens.RemoteActions
import io.github.tecix.richtvremote.ui.screens.RemoteScreen
import io.github.tecix.richtvremote.ui.theme.RichTvRemoteTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** A finite looper capture avoids Espresso waiting indefinitely on the focused dialog's cursor. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PairingScreenshotTest {
    @Test fun pairing() {
        val tv = SavedTv("Living room", "192.168.1.50")
        val state = PairingState.EnterCode(tv, tv.name)
        val settings = Settings(tvs = listOf(tv), selectedHost = tv.host, dynamicColor = false)
        val noop = RemoteActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            controller.get().setContent {
                RichTvRemoteTheme(dynamicColor = false) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        RemoteScreen(settings, DirectState.NeedsPairing(tv), HaState.Unconfigured,
                            TvStatus(), HaTile(), false, SnackbarHostState(), noop)
                        PairingDialog(state, {}, {}, {})
                    }
                }
            }
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            captureScreenRoboImage("build/outputs/roborazzi/pairing.png")
        } finally { controller.pause().stop().destroy() }
    }
}
