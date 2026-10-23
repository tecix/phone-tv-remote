package io.github.tecix.richtvremote.media

import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Keep replacement requests ordered even when the user chooses a third file while the second waits. */
internal suspend fun replaceMediaTransfer(previous: Job?, stop: suspend () -> Unit, play: suspend () -> Unit) {
    withContext(NonCancellable) { previous?.cancelAndJoin() }
    currentCoroutineContext().ensureActive()
    stop()
    currentCoroutineContext().ensureActive()
    play()
}
