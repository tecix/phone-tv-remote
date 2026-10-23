package io.github.tecix.richtvremote.media

import android.content.Intent
import android.net.Uri
import io.github.tecix.richtvremote.data.Settings
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaShareNotificationTest {
    @Test fun notificationActionDispatchesUnifiedStopAndRemovesForegroundNotification() = runBlocking {
        val serviceController = Robolectric.buildService(MediaShareService::class.java).create()
        val service = serviceController.get()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val events = mutableListOf<String>()
        val stopped = CompletableDeferred<Unit>()
        var destroyed = false
        val controller = StreamingController<SharedPlayback>(scope, {
            events += "phone stopped"
            serviceController.destroy()
            destroyed = true
        }, { events += "DLNA stopped"; stopped.complete(Unit) })
        val field = MediaStreaming.javaClass.getDeclaredField("instance").apply { isAccessible = true }
        val previous = field.get(null)
        field.set(null, controller)
        try {
            // A normal file-share notification must carry the same command as the main-screen Stop.
            service.onStartCommand(Intent(service, MediaShareService::class.java)
                .setDataAndType(Uri.parse("content://files/test.mp4"), "video/mp4"), 0, 1)
            controller.setPlayback(SharedPlayback(MediaDestination.DLNA,
                DlnaRenderer("TV", "http://tv/transport", "urn:schemas-upnp-org:service:AVTransport:1"), Settings()))
            val notification = Shadows.shadowOf(service).lastForegroundNotification
            val action = notification.actions.single { it.title.toString() == "Stop streaming" }
            assertTrue(Shadows.shadowOf(action.actionIntent).isImmutable)
            action.actionIntent.send()
            val dispatched = Shadows.shadowOf(service).nextStartedService
            assertEquals(MediaShareService.STOP, dispatched.action)
            service.onStartCommand(dispatched, 0, 2)
            withTimeout(2000) { stopped.await() }
            assertEquals(setOf("phone stopped", "DLNA stopped"), events.toSet())
            assertTrue(Shadows.shadowOf(service).isForegroundStopped)
            assertTrue(Shadows.shadowOf(service).notificationShouldRemoved)
            assertNull(controller.playback.value)
        } finally {
            if (!destroyed) serviceController.destroy()
            scope.cancel()
            field.set(null, previous)
        }
    }
}
