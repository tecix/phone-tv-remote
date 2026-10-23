package io.github.tecix.richtvremote

/** Android keycodes the remote sends, shared by the Direct and Home Assistant routes. */
enum class TvKey(val code: Int) {
    DPAD_UP(19), DPAD_DOWN(20), DPAD_LEFT(21), DPAD_RIGHT(22), DPAD_CENTER(23),
    BACK(4), HOME(3), MENU(82), NOTIFICATION(83),
    VOLUME_UP(24), VOLUME_DOWN(25), VOLUME_MUTE(164),
    POWER(26), SLEEP(223), WAKEUP(224),
    MEDIA_PLAY_PAUSE(85), MEDIA_STOP(86), MEDIA_NEXT(87), MEDIA_PREVIOUS(88),
    MEDIA_REWIND(89), MEDIA_FAST_FORWARD(90),
    ALL_APPS(284), TV(170), TV_INPUT(178), SETTINGS(176), INFO(165), GUIDE(172),
    CHANNEL_UP(166), CHANNEL_DOWN(167),
    ASSIST(219), SEARCH(84), CAPTIONS(175),
    ENTER(66), DEL(67), FORWARD_DEL(112),
    S(47), // SmartTube: toggle playback speed (with "Remap S to speed toggle" on)
    ;

    companion object {
        private val aliases = mapOf(
            "UP" to DPAD_UP, "DOWN" to DPAD_DOWN, "LEFT" to DPAD_LEFT, "RIGHT" to DPAD_RIGHT,
            "OK" to DPAD_CENTER, "CENTER" to DPAD_CENTER,
        )

        /**
         * Parse a key macro such as `DOWN RIGHT*3 OK`: key names (or the D-pad
         * aliases above), each optionally repeated with `*n`. Returns null if any token is unknown.
         */
        fun parseMacro(text: String): List<TvKey>? = buildList {
            for (token in text.trim().uppercase().split(Regex("""[\s,]+""")).filter { it.isNotEmpty() }) {
                val name = token.substringBefore('*')
                val times = token.substringAfter('*', "1").toIntOrNull()?.takeIf { it in 1..20 } ?: return null
                val key = aliases[name] ?: entries.firstOrNull { it.name == name } ?: return null
                repeat(times) { add(key) }
            }
        }.takeIf { it.isNotEmpty() }
    }
}

/** Something the phone can drive the TV through: the Remote v2 protocol or Home Assistant. */
interface RemoteBackend {
    fun press(key: TvKey)
    fun longPress(key: TvKey)

    /** Press keys one after another, with a pause between them so on-screen menus can follow. */
    fun pressSequence(keys: List<TvKey>)
    fun sendText(text: String)
    fun resetText()
    val supportsMedia: Boolean get() = false
    fun setupMediaAccess() { throw UnsupportedOperationException("Update TinyTV Receiver to enable playback controls") }
    fun seekTo(positionMs: Long, sessionId: String) { throw UnsupportedOperationException("This connection does not support seeking") }
    fun openMedia(uri: String, mime: String) { throw UnsupportedOperationException("Use Home Assistant or an updated TinyTV receiver for VLC sharing") }
    fun openLink(uri: String)
    fun launchApp(packageName: String)
}
