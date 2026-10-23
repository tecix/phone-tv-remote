package io.github.tecix.richtvremote

import android.net.Uri
import io.github.tecix.richtvremote.data.Route
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceRoutingTest {
    private class Backend(var failure: Boolean = false) : RemoteBackend {
        val links = mutableListOf<String>()
        override fun openLink(uri: String) { if (failure) error("Transport failed"); links += uri }
        override fun press(key: TvKey) = Unit
        override fun longPress(key: TvKey) = Unit
        override fun pressSequence(keys: List<TvKey>) = Unit
        override fun sendText(text: String) = Unit
        override fun resetText() = Unit
        override fun launchApp(packageName: String) = Unit
    }
    @Test fun usesOnlySelectedBackend() {
        val direct = Backend(); val ha = Backend()
        assertTrue(dispatchSmartTube(Route.DIRECT, "Tom và Jerry", true, direct, ha).isSuccess)
        assertEquals(1, direct.links.size); assertTrue(ha.links.isEmpty())
        assertEquals("play", Uri.parse(direct.links.single()).getQueryParameter("method"))
        assertTrue(dispatchSmartTube(Route.HOME_ASSISTANT, "Tom & Jerry", false, direct, ha).isSuccess)
        assertEquals(1, direct.links.size); assertEquals(1, ha.links.size)
        assertNull(Uri.parse(ha.links.single()).getQueryParameter("method"))
    }
    @Test fun unavailableRouteNeverFallsBack() {
        val other = Backend()
        assertTrue(dispatchSmartTube(Route.DIRECT, "hello", true, null, other).isFailure)
        assertTrue(dispatchSmartTube(Route.HOME_ASSISTANT, "hello", true, other, null).isFailure)
        assertTrue(other.links.isEmpty())
    }
    @Test fun transportAndInvalidQueryErrorsAreReturned() {
        val backend = Backend(true)
        assertEquals("Transport failed", dispatchSmartTube(Route.DIRECT, "hello", true, backend, null).exceptionOrNull()?.message)
        backend.failure = false
        for (query in listOf(" ", "bad\u0000query")) assertTrue(dispatchSmartTube(Route.DIRECT, query, true, backend, null).isFailure)
        assertTrue(backend.links.isEmpty())
    }
}
