package io.github.tecix.richtvremote.media

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StreamingControllerTest {
    @Test fun stopClosesLocalTransferAndStopsBufferedTvPlayback() = runBlocking {
        val events = mutableListOf<String>()
        val controller = StreamingController<String>(this, { events += "close phone stream" }, { events += "stop $it" })
        controller.setPlayback("DLNA")
        controller.requestStop().join()
        assertEquals(setOf("close phone stream", "stop DLNA"), events.toSet())
        assertNull(controller.playback.value)
        assertFalse(controller.stopping.value)
        assertNull(controller.result.value?.error)
    }

    @Test fun notificationStopCancelsPendingWorkAndWaitsForInFlightPlaybackBeforeStoppingTv() = runBlocking {
        val events = mutableListOf<String>()
        val inFlightFinished = CompletableDeferred<Unit>()
        val localClosed = CompletableDeferred<Unit>()
        val controller = StreamingController<String>(this, {
            events += "close phone stream"; localClosed.complete(Unit)
        }, { events += "stop $it" })
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() }
            finally {
                withContext(NonCancellable) {
                    inFlightFinished.await()
                    controller.setPlayback("VLC")
                    events += "in-flight play finished"
                }
            }
        }
        controller.bindPending(pending) { events += "cancel preparation and autoplay" }
        val stop = controller.requestStop()
        localClosed.await()
        assertTrue(pending.isCancelled)
        assertFalse(events.contains("stop VLC"))
        inFlightFinished.complete(Unit)
        stop.join()
        assertTrue(events.indexOf("stop VLC") > events.indexOf("in-flight play finished"))
        assertNull(controller.playback.value)
    }

    @Test fun failedTvStopRetainsDestinationForRetryWhilePhoneStreamStillCloses() = runBlocking {
        var fail = true
        var localStops = 0
        val controller = StreamingController<String>(this, { localStops++ }, { if (fail) error("TV offline") })
        controller.setPlayback("DLNA")
        controller.requestStop().join()
        assertEquals(1, localStops)
        assertEquals("DLNA", controller.playback.value)
        assertTrue(controller.result.value!!.error!!.contains("TV offline"))
        fail = false
        controller.requestStop().join()
        assertEquals(2, localStops)
        assertNull(controller.playback.value)
        assertNull(controller.result.value!!.error)
    }

    @Test fun newFileWaitsForNotificationStopSoLateStopCannotKillIt() = runBlocking {
        val stopStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val controller = StreamingController<String>(this, {}, {
            stopStarted.complete(Unit); release.await(); events += "stopped $it"
        })
        controller.setPlayback("old")
        val stop = controller.requestStop()
        stopStarted.await()
        val replacement = launch {
            controller.stopForReplacement()
            controller.setPlayback("new")
            events += "play new"
        }
        yield()
        assertTrue(events.isEmpty())
        release.complete(Unit)
        joinAll(stop, replacement)
        assertEquals(listOf("stopped old", "play new"), events)
        assertEquals("new", controller.playback.value)
    }
}
