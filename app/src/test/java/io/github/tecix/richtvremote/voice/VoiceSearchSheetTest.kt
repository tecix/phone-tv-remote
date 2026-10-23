package io.github.tecix.richtvremote.voice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import io.github.tecix.richtvremote.ui.screens.VoiceSearchSheet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceSearchSheetTest {
    @get:Rule val compose = createComposeRule()
    private class Registry(val missing: Boolean = false) : ActivityResultRegistry(), ActivityResultRegistryOwner {
        override val activityResultRegistry get() = this
        var requestCode = 0
        var intent: Intent? = null
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            if (missing) throw ActivityNotFoundException()
            this.requestCode = requestCode
            intent = input as Intent
        }
        fun result(code: Int, text: String? = null) {
            dispatchResult(requestCode, code, Intent().apply {
                if (text != null) putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(text))
            })
        }
    }
    private val connected = mutableStateOf(true)
    private var dismissals = 0
    private var accept = true
    private val sent = mutableListOf<Pair<String, Boolean>>()
    private val listeningChanges = mutableListOf<Boolean>()
    private fun show(registry: Registry, language: String = "vi-VN", startWithVoice: Boolean = true) {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                MaterialTheme {
                    VoiceSearchSheet("DIRECT", language, {}, connected.value, { dismissals++ }, { query, play ->
                        sent += query to play
                        accept
                    }, startWithVoice = startWithVoice, onListeningChange = { listeningChanges += it })
                }
            }
        }
    }
    @Test fun vietnameseRecognitionImmediatelySearchesEvenForPlayPhrases() {
        val registry = Registry(); show(registry)
        compose.runOnIdle {
            assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, registry.intent?.action)
            assertEquals("vi-VN", registry.intent?.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
            assertEquals(listOf(true), listeningChanges)
            registry.result(Activity.RESULT_OK, "Mở Tom và Jerry tập 5")
        }
        compose.runOnIdle {
            assertEquals(listOf("Tom và Jerry tập 5" to false), sent); assertEquals(1, dismissals)
            assertEquals(listOf(true, false), listeningChanges)
        }
        compose.onNodeWithText("Recognized: Mở Tom và Jerry tập 5").assertExists()
    }
    @Test fun englishSearchDoesNotAutoplay() {
        val registry = Registry(); show(registry, "en-US")
        compose.runOnIdle {
            assertEquals("en-US", registry.intent?.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
            registry.result(Activity.RESULT_OK, "Search for Baby Shark")
        }
        compose.runOnIdle { assertEquals(listOf("Baby Shark" to false), sent); assertEquals(1, dismissals) }
    }
    @Test fun cancelledSpeechAllowsManualSearch() {
        val registry = Registry(); show(registry)
        compose.runOnIdle { registry.result(Activity.RESULT_CANCELED) }
        compose.onNodeWithText("Voice search cancelled. You can type a query.").assertExists()
        compose.onNodeWithText("Search query").performTextInput("Sơn Tùng & friends")
        compose.onNodeWithText("Search", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(listOf("Sơn Tùng & friends" to false), sent) }
    }
    @Test fun missingServiceKeepsManualFallback() {
        show(Registry(missing = true))
        compose.onNodeWithText("No speech recognition service installed. Type a query below.").assertExists()
        compose.onNodeWithText("Search query").performTextInput("Baby Shark")
        compose.onNodeWithText("Play", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(listOf("Baby Shark" to true), sent) }
    }
    @Test fun emptyAndFailedResultsDoNotSend() {
        val registry = Registry(); show(registry)
        compose.runOnIdle { registry.result(Activity.RESULT_OK, "   ") }
        compose.onNodeWithText("No search query recognized. Try again or type below.").assertExists()
        compose.onNodeWithContentDescription("Speak").performClick()
        compose.runOnIdle { registry.result(42) }
        compose.onNodeWithText("Speech recognition failed. Try again or type a query.").assertExists()
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
    }
    @Test fun reconnectionDoesNotSendSpeechAutomatically() {
        connected.value = false
        val registry = Registry(); show(registry)
        compose.runOnIdle {
            registry.result(Activity.RESULT_OK, "Play Baby Shark")
            assertTrue(sent.isEmpty())
            connected.value = true
        }
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
    }
    @Test fun textEntryDoesNotLaunchRecognition() {
        val registry = Registry(); show(registry, startWithVoice = false)
        compose.runOnIdle { assertNull(registry.intent) }
        compose.onNodeWithText("Search query").performTextInput("Đen Vâu & friends")
        compose.onNodeWithText("Search", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(listOf("Đen Vâu & friends" to false), sent) }
    }
    @Test fun panelMicrophoneAlsoSearchesImmediately() {
        val registry = Registry(); show(registry, startWithVoice = false)
        compose.onNodeWithContentDescription("Speak").performClick()
        compose.runOnIdle { registry.result(Activity.RESULT_OK, "Đen Vâu") }
        compose.runOnIdle { assertEquals(listOf("Đen Vâu" to false), sent); assertEquals(1, dismissals) }
    }
    @Test fun rejectedVoiceSearchRetainsQueryAndManualPlay() {
        accept = false
        val registry = Registry(); show(registry)
        compose.runOnIdle { registry.result(Activity.RESULT_OK, "Play Baby Shark") }
        compose.runOnIdle { assertEquals(0, dismissals); accept = true }
        compose.onNodeWithText("Play", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(listOf("Baby Shark" to false, "Baby Shark" to true), sent); assertEquals(1, dismissals) }
    }
    @Test fun clearingQueryDisablesSearchAndPlay() {
        val registry = Registry(); show(registry, startWithVoice = false)
        compose.onNodeWithText("Search query").performTextInput("Baby Shark")
        compose.onNodeWithContentDescription("Clear query").performClick()
        compose.onNodeWithText("Search").assertIsNotEnabled()
        compose.onNodeWithText("Play").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
    }

    @Test fun rejectedCommandKeepsPanelOpen() {
        accept = false
        show(Registry(), startWithVoice = false)
        compose.onNodeWithText("Search query").performTextInput("Baby Shark")
        compose.onNodeWithText("Search", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(0, dismissals) }
        compose.onNodeWithText("Command could not be queued. Check the connection and query.").assertExists()
    }

}
