package io.github.tecix.richtvremote.media

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MediaTransferReplacementTest {
    @Test fun replacementWaitsForOldPreparationAndServerShutdownBeforePlaying() = runBlocking {
        val events = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val shutdownStarted = CompletableDeferred<Unit>()
        val previous = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() }
            finally { events += "old preparation cancelled" }
        }
        val replacement = launch(start = CoroutineStart.UNDISPATCHED) {
            replaceMediaTransfer(previous, stop = {
                events += "stop old server"
                shutdownStarted.complete(Unit)
                release.await()
                events += "old file removed"
            }, play = { events += "play new file" })
        }
        shutdownStarted.await()
        assertEquals(listOf("old preparation cancelled", "stop old server"), events)
        release.complete(Unit)
        replacement.join()
        assertEquals(listOf("old preparation cancelled", "stop old server", "old file removed", "play new file"), events)
    }

    @Test fun rapidlyChoosingThirdFileCannotLaunchTheSecondOrOvertakeOldCleanup() = runBlocking {
        val events = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() }
            finally {
                withContext(NonCancellable) {
                    release.await()
                    events += "first command finished"
                }
            }
        }
        val second = launch(start = CoroutineStart.UNDISPATCHED) {
            replaceMediaTransfer(first, { events += "stop for second" }, { events += "play second" })
        }
        val third = launch(start = CoroutineStart.UNDISPATCHED) {
            replaceMediaTransfer(second, { events += "stop for third" }, { events += "play third" })
        }
        assertTrue(events.isEmpty())
        release.complete(Unit)
        third.join()
        assertEquals(listOf("first command finished", "stop for third", "play third"), events)
        assertTrue(second.isCancelled)
    }
}
