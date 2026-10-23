package io.github.tecix.richtvremote.media

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remember a renderer for this app session; Stop playback and Disconnect are separate actions. */
internal class DlnaReceiverSession {
    private val _selected = MutableStateFlow<DlnaRenderer?>(null)
    val selected = _selected.asStateFlow()
    private val _receivers = MutableStateFlow<List<DlnaRenderer>>(emptyList())
    val receivers = _receivers.asStateFlow()
    private var revision = 0L

    fun select(renderer: DlnaRenderer) {
        revision++
        _selected.value = renderer
        if (renderer !in _receivers.value) _receivers.value += renderer
    }

    fun disconnect() { revision++; _selected.value = null; _receivers.value = emptyList() }

    suspend fun find(host: String?, force: Boolean = false, scan: suspend () -> List<DlnaRenderer>): List<DlnaRenderer> {
        if (!force && _selected.value != null) return _receivers.value
        val startedAt = revision
        val found = scan().distinctBy { it.controlUrl }.sortedByDescending { it.host == host }
        currentCoroutineContext().ensureActive()
        if (startedAt != revision) return _receivers.value // A delayed scan cannot undo Disconnect or a manual choice.
        revision++
        _receivers.value = found
        _selected.value = found.firstOrNull { it == _selected.value }
            ?: found.firstOrNull { it.host == host } ?: found.singleOrNull()
        return found
    }
}
