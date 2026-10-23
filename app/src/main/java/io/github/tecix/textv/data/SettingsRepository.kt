package io.github.tecix.textv.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.tecix.textv.ha.HaConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** How buttons reach the TV. */
enum class Route { DIRECT, HOME_ASSISTANT }

data class SavedTv(val name: String, val host: String)

data class Settings(
    val route: Route = Route.DIRECT,
    val tvs: List<SavedTv> = emptyList(),
    val selectedHost: String? = null,
    val ha: HaConfig = HaConfig(),
    val volumeKeys: Boolean = true,
    val haptics: Boolean = true,
    val dynamicColor: Boolean = true,
    val macroActions: List<MacroAction> = defaultMacroActions(),
) {
    val selectedTv get() = tvs.firstOrNull { it.host == selectedHost }
}

private val Context.dataStore by preferencesDataStore("settings")

/** Settings live in app-private storage, which also keeps the HA token out of reach of other apps. */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val route = stringPreferencesKey("route")
        val tvs = stringPreferencesKey("tvs")
        val selected = stringPreferencesKey("selected_host")
        val haUrl = stringPreferencesKey("ha_url")
        val haToken = stringPreferencesKey("ha_token")
        val haEntity = stringPreferencesKey("ha_entity")
        val haMediaEntity = stringPreferencesKey("ha_media_entity")
        val haIrPowerEntity = stringPreferencesKey("ha_ir_power_entity")
        val captionMacro = stringPreferencesKey("caption_macro")
        val macroActions = stringPreferencesKey("macro_actions")
        val volumeKeys = booleanPreferencesKey("volume_keys")
        val haptics = booleanPreferencesKey("haptics")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            route = p[Keys.route]?.let { runCatching { Route.valueOf(it) }.getOrNull() } ?: Route.DIRECT,
            tvs = decodeTvs(p[Keys.tvs]),
            selectedHost = p[Keys.selected],
            ha = HaConfig(p[Keys.haUrl] ?: "", p[Keys.haToken] ?: "", p[Keys.haEntity] ?: "", p[Keys.haMediaEntity] ?: "", p[Keys.haIrPowerEntity] ?: ""),
            volumeKeys = p[Keys.volumeKeys] ?: true,
            haptics = p[Keys.haptics] ?: true,
            dynamicColor = p[Keys.dynamicColor] ?: true,
            macroActions = decodeMacroActions(p[Keys.macroActions], p[Keys.captionMacro]),
        )
    }

    suspend fun setRoute(route: Route) = edit { it[Keys.route] = route.name }

    suspend fun saveTv(tv: SavedTv, select: Boolean = true) = edit { p ->
        val tvs = decodeTvs(p[Keys.tvs]).filter { it.host != tv.host } + tv
        p[Keys.tvs] = encodeTvs(tvs)
        if (select) p[Keys.selected] = tv.host
    }

    suspend fun removeTv(host: String) = edit { p ->
        p[Keys.tvs] = encodeTvs(decodeTvs(p[Keys.tvs]).filter { it.host != host })
        if (p[Keys.selected] == host) p.remove(Keys.selected)
    }

    suspend fun selectTv(host: String) = edit { it[Keys.selected] = host }

    suspend fun setHa(config: HaConfig) = edit {
        it[Keys.haUrl] = config.url.trim()
        it[Keys.haToken] = config.token.trim()
        it[Keys.haEntity] = config.entityId.trim()
        it[Keys.haMediaEntity] = config.mediaEntityId.trim()
        it[Keys.haIrPowerEntity] = config.irPowerEntityId.trim()
    }

    suspend fun saveMacro(action: MacroAction) {
        val trimmed = action.copy(name = action.name.trim(), sequence = action.sequence.trim())
        require(trimmed.isValid) { "Enter a name and a valid key sequence" }
        edit { p ->
            val actions = decodeMacroActions(p[Keys.macroActions], p[Keys.captionMacro]).toMutableList()
            val index = actions.indexOfFirst { it.id == trimmed.id }
            if (index < 0) actions.add(trimmed) else actions[index] = trimmed
            p[Keys.macroActions] = encodeMacroActions(actions)
        }
    }

    suspend fun deleteMacro(id: String) = edit { p ->
        val actions = decodeMacroActions(p[Keys.macroActions], p[Keys.captionMacro])
        p[Keys.macroActions] = encodeMacroActions(actions.filterNot { it.id == id })
    }

    suspend fun setVolumeKeys(enabled: Boolean) = edit { it[Keys.volumeKeys] = enabled }

    suspend fun setHaptics(enabled: Boolean) = edit { it[Keys.haptics] = enabled }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.dynamicColor] = enabled }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun decodeTvs(json: String?): List<SavedTv> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        List(array.length()) { i ->
            array.getJSONObject(i).let { SavedTv(it.getString("name"), it.getString("host")) }
        }
    }.getOrDefault(emptyList())

    private fun encodeTvs(tvs: List<SavedTv>) =
        JSONArray(tvs.map { JSONObject().put("name", it.name).put("host", it.host) }).toString()
}
