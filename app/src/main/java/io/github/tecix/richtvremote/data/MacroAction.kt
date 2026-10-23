package io.github.tecix.richtvremote.data

import io.github.tecix.richtvremote.TvKey
import org.json.JSONArray
import org.json.JSONObject

data class MacroAction(val id: String, val name: String, val sequence: String) {
    val isValid get() = id.isNotBlank() && name.isNotBlank() && TvKey.parseMacro(sequence) != null
}

const val DEFAULT_CAPTION_MACRO = "DOWN RIGHT*4 OK BACK"
private const val OLD_CAPTION_MACRO = "DOWN RIGHT*2 OK BACK"

fun defaultMacroActions(captionSequence: String = DEFAULT_CAPTION_MACRO) = listOf(
    MacroAction("captions", "Captions", captionSequence),
    MacroAction("speed", "1.5× speed", "S"),
)

/** Migrate the old captions setting, keeping custom sequences and updating the old default. */
fun decodeMacroActions(json: String?, legacyCaptions: String? = null): List<MacroAction> {
    val defaults = defaultMacroActions(
        legacyCaptions?.trim()?.takeUnless { it == OLD_CAPTION_MACRO || TvKey.parseMacro(it) == null }
            ?: DEFAULT_CAPTION_MACRO,
    )
    if (json == null) return defaults
    return runCatching {
        val array = JSONArray(json)
        List(array.length()) { index ->
            array.getJSONObject(index).let {
                MacroAction(it.getString("id"), it.getString("name"), it.getString("sequence"))
            }
        }.also { actions ->
            require(actions.all { it.isValid } && actions.map { it.id }.distinct().size == actions.size)
        }
    }.getOrDefault(defaults)
}

fun encodeMacroActions(actions: List<MacroAction>): String = JSONArray(actions.map {
    JSONObject().put("id", it.id).put("name", it.name).put("sequence", it.sequence)
}).toString()
