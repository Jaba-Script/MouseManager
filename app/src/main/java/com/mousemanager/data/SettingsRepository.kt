package com.mousemanager.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.mousemanager.data.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Единственная точка записи настроек (D-007, §9). UI и служба не вызывают `edit`
 * напрямую, поэтому формат JSON и набор ключей меняются в одном месте.
 *
 * Все методы — `suspend`: запись идёт вне главного потока.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    /** Поток настроек; разбор — по правилам [ConfigCodec] (fail-safe, D-018). */
    val settings: Flow<Settings> = dataStore.data
        .map { prefs -> ConfigCodec.decode(prefs) }
        .distinctUntilChanged()

    suspend fun setTargets(descriptors: Set<String>) {
        dataStore.edit { prefs ->
            prefs[ConfigKeys.targets] = descriptors
        }
    }

    /** Имя устройства запоминается для UI: после отключения мыши `descriptor` остаётся. */
    suspend fun setDeviceName(descriptor: String, name: String) {
        dataStore.edit { prefs ->
            val names = ConfigCodec.decodeDeviceNames(prefs[ConfigKeys.deviceNames]) +
                (descriptor to name)
            prefs[ConfigKeys.deviceNames] = ConfigCodec.encodeDeviceNames(names)
        }
    }

    suspend fun removeDeviceName(descriptor: String) {
        dataStore.edit { prefs ->
            val names = ConfigCodec.decodeDeviceNames(prefs[ConfigKeys.deviceNames]) - descriptor
            prefs[ConfigKeys.deviceNames] = ConfigCodec.encodeDeviceNames(names)
        }
    }

    suspend fun setActiveProfileId(profileId: String) {
        dataStore.edit { prefs ->
            prefs[ConfigKeys.activeProfileId] = profileId
        }
    }

    suspend fun setDiagnosticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[ConfigKeys.diagnosticsEnabled] = enabled
        }
    }

    /**
     * Явный сброс после ошибки (D-018): удаляет все ключи, настройки возвращаются
     * к значениям по умолчанию. Вызывается только действием пользователя.
     */
    suspend fun resetToDefaults() {
        dataStore.edit { prefs -> prefs.clear() }
    }
}
