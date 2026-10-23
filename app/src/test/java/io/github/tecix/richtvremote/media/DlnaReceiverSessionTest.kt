package io.github.tecix.richtvremote.media

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DlnaReceiverSessionTest {
    private val tv = DlnaRenderer("Living room", "http://192.168.2.192:3500/control", "urn:schemas-upnp-org:service:AVTransport:1")

    @Test fun consecutiveFileSharesReuseSelectedReceiverWithoutScanning() = runBlocking {
        val session = DlnaReceiverSession()
        var scans = 0
        repeat(3) { session.find(tv.host) { scans++; listOf(tv) } }
        assertEquals(1, scans)
        assertEquals(tv, session.selected.value)
    }

    @Test fun disconnectClearsSessionAndNextShareScansAgain() = runBlocking {
        val session = DlnaReceiverSession()
        var scans = 0
        session.find(tv.host) { scans++; listOf(tv) }
        session.disconnect()
        assertNull(session.selected.value)
        assertTrue(session.receivers.value.isEmpty())
        session.find(tv.host) { scans++; listOf(tv) }
        assertEquals(2, scans)
    }

    @Test fun explicitChangeReceiverRefreshesButKeepsExistingSelectionWhenAvailable() = runBlocking {
        val session = DlnaReceiverSession()
        val other = tv.copy(name = "Bedroom", controlUrl = "http://192.168.2.22/control")
        session.select(other)
        session.find(tv.host, force = true) { listOf(tv, other) }
        assertEquals(other, session.selected.value)
    }

    @Test fun disconnectDuringDiscoveryCannotReconnectFromStaleResults() = runBlocking {
        val session = DlnaReceiverSession()
        val results = CompletableDeferred<List<DlnaRenderer>>()
        val scan = launch(start = CoroutineStart.UNDISPATCHED) { session.find(tv.host) { results.await() } }
        session.disconnect()
        results.complete(listOf(tv))
        scan.join()
        assertNull(session.selected.value)
    }
}
