package io.github.tecix.richtvremote.voice

import android.net.Uri

enum class VoiceAction { PLAY, SEARCH }
data class VoiceCommand(val action: VoiceAction, val query: String)

/** Stateless boundary for a future optional interpreter; preserves the original query. */
fun interface CommandInterpreter {
    fun parse(text: String): VoiceCommand?
}

object VoiceCommandParser : CommandInterpreter {
    private val prefixes = listOf(
        "tìm kiếm" to VoiceAction.SEARCH, "search for" to VoiceAction.SEARCH,
        "look for" to VoiceAction.SEARCH, "tìm" to VoiceAction.SEARCH,
        "search" to VoiceAction.SEARCH, "find" to VoiceAction.SEARCH,
        "mở" to VoiceAction.PLAY, "phát" to VoiceAction.PLAY,
        "bật" to VoiceAction.PLAY, "play" to VoiceAction.PLAY,
        "open" to VoiceAction.PLAY, "watch" to VoiceAction.PLAY,
    )
    override fun parse(text: String): VoiceCommand? {
        val original = text.trim()
        if (original.isEmpty() || original.any { it.isISOControl() }) return null
        for ((prefix, action) in prefixes) {
            if (original.equals(prefix, ignoreCase = true)) return null
            if (original.startsWith(prefix, ignoreCase = true) &&
                original.getOrNull(prefix.length)?.isWhitespace() == true) {
                return original.substring(prefix.length).trim().takeIf { it.isNotEmpty() }
                    ?.let { VoiceCommand(action, it) }
            }
        }
        return VoiceCommand(VoiceAction.PLAY, original)
    }
}

object SmartTubeLink {
    fun build(command: VoiceCommand): String {
        require(command.query.isNotBlank() && command.query.none { it.isISOControl() })
        return Uri.Builder().scheme("youtube").authority("search")
            .appendQueryParameter("query", command.query)
            .apply { if (command.action == VoiceAction.PLAY) appendQueryParameter("method", "play") }
            .build().toString()
    }
}
