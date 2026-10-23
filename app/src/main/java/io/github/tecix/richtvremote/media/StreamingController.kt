package io.github.tecix.richtvremote.media

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class StreamingStopResult(val request: Long, val error: String?)

/** App-scoped stop operation shared by the screen and notification, independent of the file service. */
internal class StreamingController<T : Any>(
    private val scope: CoroutineScope,
    private val stopLocal: suspend () -> Unit,
    private val stopTv: suspend (T) -> Unit,
) {
    private val _playback = MutableStateFlow<T?>(null)
    val playback = _playback.asStateFlow()
    private val _stopping = MutableStateFlow(false)
    val stopping = _stopping.asStateFlow()
    private val _result = MutableStateFlow<StreamingStopResult?>(null)
    val result = _result.asStateFlow()
    private var pending: Job? = null
    private var cancelPending: (() -> Unit)? = null
    private var stopJob: Job? = null
    private var request = 0L

    fun setPlayback(target: T?) { _playback.value = target }
    fun bindPending(job: Job?, cancel: () -> Unit) { pending = job; cancelPending = cancel }

    fun requestStop(): Job {
        val previous = pending
        cancelPending?.invoke()
        previous?.cancel()
        stopJob?.takeIf { it.isActive }?.let { return it }
        _stopping.value = true
        val id = ++request
        return scope.launch {
            try {
                coroutineScope {
                    // Close the HTTP stream immediately, even if an in-flight TV command needs time to finish.
                    val local = async { attempt { stopLocal() } }
                    withContext(NonCancellable) { previous?.cancelAndJoin() }
                    val tvError = stopTarget()
                    val localError = local.await()
                    _result.value = StreamingStopResult(id, listOfNotNull(tvError?.let { "TV: $it" }, localError?.let { "Phone: $it" })
                        .joinToString("; ").ifEmpty { null })
                }
            } finally { _stopping.value = false }
        }.also { stopJob = it }
    }

    suspend fun stopForReplacement() {
        stopJob?.join() // A notification Stop must finish before a new file can start.
        try { stopTarget() } finally { stopLocal() }
    }

    private suspend fun stopTarget(): String? {
        val target = _playback.value ?: return null
        val error = attempt { stopTv(target) }
        if (error == null && _playback.value === target) _playback.value = null
        return error // Retain an unreachable target so Stop can be retried.
    }

    private suspend fun attempt(action: suspend () -> Unit): String? = try {
        action(); null
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { e.message ?: "Could not stop streaming" }
}
