package com.mousemanager.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.mousemanager.core.ActionCode
import com.mousemanager.core.Chord
import com.mousemanager.core.Modifier
import com.mousemanager.data.model.Mapping
import com.mousemanager.data.model.Profile
import com.mousemanager.data.model.Settings
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Ключи DataStore (D-007). `armed` здесь нет и не должно появиться (D-006). */
object ConfigKeys {
    val schemaVersion = intPreferencesKey("schema_version")
    val targets = stringSetPreferencesKey("targets")
    val deviceNames = stringPreferencesKey("device_names")
    val profiles = stringPreferencesKey("profiles")
    val activeProfileId = stringPreferencesKey("active_profile_id")
    val defaultProfileId = stringPreferencesKey("default_profile_id")
    val appBindings = stringPreferencesKey("app_bindings")
    val diagnosticsEnabled = booleanPreferencesKey("diagnostics_enabled")
}

/**
 * Кодек настроек: DataStore `Preferences` ⇄ [Settings] (D-007).
 *
 * Разбор намеренно терпимый (D-018): битый JSON поля не роняет загрузку, а даёт значение
 * по умолчанию; неизвестное действие становится [ActionCode.NONE]; неизвестный
 * модификатор пропускается. Исключения наружу не выходят — fail-safe решает вызывающий
 * (см. [ConfigStore]).
 */
object ConfigCodec {

    const val CURRENT_SCHEMA_VERSION = 1

    /** Читает настройки из DataStore. Всегда возвращает валидный [Settings]. */
    fun decode(prefs: Preferences): Settings {
        val schemaVersion = prefs[ConfigKeys.schemaVersion] ?: CURRENT_SCHEMA_VERSION
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            // Схема новее приложения: не интерпретируем её, работаем на безопасных
            // значениях (D-018). Файл не перезаписывается.
            return Settings(schemaVersion = schemaVersion).normalized()
        }
        return Settings(
            schemaVersion = schemaVersion,
            targets = prefs[ConfigKeys.targets] ?: emptySet(),
            deviceNames = decodeDeviceNames(prefs[ConfigKeys.deviceNames]),
            profiles = decodeProfiles(prefs[ConfigKeys.profiles]),
            activeProfileId = prefs[ConfigKeys.activeProfileId] ?: Profile.DEFAULT_ID,
            defaultProfileId = prefs[ConfigKeys.defaultProfileId] ?: Profile.DEFAULT_ID,
            appBindings = decodeAppBindings(prefs[ConfigKeys.appBindings]),
            diagnosticsEnabled = prefs[ConfigKeys.diagnosticsEnabled] ?: false,
        ).normalized()
    }

    // ----- профили -----

    fun decodeProfiles(raw: String?): List<Profile> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val id = if (json.isNull("id")) {
                    null
                } else {
                    json.optString("id").takeIf { it.isNotBlank() }
                } ?: return@mapNotNull null
                Profile(
                    id = id,
                    name = if (json.isNull("name")) id else json.optString("name").ifBlank { id },
                    mappings = decodeMappings(json.optJSONArray("mappings")),
                )
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    fun encodeProfiles(profiles: List<Profile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            val mappings = JSONArray()
            profile.mappings.forEach { mapping ->
                mappings.put(
                    JSONObject().apply {
                        put("device", mapping.deviceDescriptor ?: JSONObject.NULL)
                        put(
                            "chord",
                            JSONObject().apply {
                                put("mods", JSONArray(mapping.chord.mods.map { it.name }))
                                put("key", mapping.chord.keyCode)
                            },
                        )
                        put("action", actionToName(mapping.action))
                        put("enabled", mapping.enabled)
                    },
                )
            }
            array.put(
                JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("mappings", mappings)
                },
            )
        }
        return array.toString()
    }

    private fun decodeMappings(array: JSONArray?): List<Mapping> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val chordJson = json.optJSONObject("chord") ?: return@mapNotNull null
            val keyCode = chordJson.optInt("key", -1)
            if (keyCode < 0) return@mapNotNull null
            Mapping(
                deviceDescriptor = if (json.isNull("device")) {
                    null
                } else {
                    json.optString("device").takeIf { it.isNotBlank() }
                },
                chord = Chord(
                    mods = decodeModifiers(chordJson.optJSONArray("mods")),
                    keyCode = keyCode,
                ),
                action = actionFromName(json.optString("action")),
                enabled = json.optBoolean("enabled", true),
            )
        }
    }

    private fun decodeModifiers(array: JSONArray?): Set<Modifier> {
        if (array == null) return emptySet()
        return (0 until array.length()).mapNotNull { index ->
            val name = array.optString(index)
            Modifier.entries.firstOrNull { it.name == name }
        }.toSet()
    }

    // ----- плоские словари -----

    fun decodeDeviceNames(raw: String?): Map<String, String> = decodeStringMap(raw)

    fun encodeDeviceNames(names: Map<String, String>): String = encodeStringMap(names)

    fun decodeAppBindings(raw: String?): Map<String, String> = decodeStringMap(raw)

    fun encodeAppBindings(bindings: Map<String, String>): String = encodeStringMap(bindings)

    private fun decodeStringMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key ->
                    val value = if (json.isNull(key)) null else json.optString(key)
                    value?.takeIf { it.isNotBlank() }?.let { put(key, it) }
                }
            }
        } catch (_: JSONException) {
            emptyMap()
        }
    }

    private fun encodeStringMap(values: Map<String, String>): String {
        val json = JSONObject()
        values.forEach { (key, value) -> json.put(key, value) }
        return json.toString()
    }

    // ----- действия -----

    fun actionFromName(name: String?): ActionCode =
        ActionCode.entries.firstOrNull { it.name == name } ?: ActionCode.NONE

    fun actionToName(action: ActionCode): String = action.name

    /** Приводит настройки к консистентному виду: профили и активный id существуют. */
    internal fun Settings.normalized(): Settings {
        val profiles = if (profiles.isEmpty()) listOf(Profile.DEFAULT) else profiles
        val ids = profiles.map { it.id }.toSet()
        val defaultId = defaultProfileId.takeIf { it in ids } ?: profiles.first().id
        val activeId = activeProfileId.takeIf { it in ids } ?: defaultId
        return copy(
            profiles = profiles,
            activeProfileId = activeId,
            defaultProfileId = defaultId,
        )
    }
}
