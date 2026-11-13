package com.mousemanager.data

import com.mousemanager.core.ANY_DEVICE
import com.mousemanager.core.ChordKey
import com.mousemanager.core.ProfileRuntime
import com.mousemanager.core.RuntimeConfig
import com.mousemanager.core.modsMask
import com.mousemanager.data.model.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Ошибка подготовки конфигурации (D-018, §9). */
enum class ConfigError {
    /** DataStore недоступен или поток завершился исключением. */
    UNAVAILABLE,

    /** Зарезервировано: в M1 разбор JSON терпимый и ошибок наружу не отдаёт. */
    DECODE_FAILED,
}

/**
 * Строит снимок для горячего пути (§5.2, D-008). Чистая функция — это позволяет
 * тестировать её на JVM без корутин и DataStore.
 *
 * Отключённые маппинги в снимок не попадают; `device = null` превращается в
 * [ANY_DEVICE]; при дубликате аккорда в одном устройстве побеждает последний.
 */
internal fun buildRuntimeConfig(settings: Settings, generation: Long): RuntimeConfig =
    RuntimeConfig(
        generation = generation,
        targets = settings.targets,
        profiles = settings.profiles.associate { profile ->
            profile.id to ProfileRuntime(
                id = profile.id,
                name = profile.name,
                byDevice = profile.mappings
                    .filter { it.enabled }
                    .groupBy { it.deviceDescriptor ?: ANY_DEVICE }
                    .mapValues { (_, mappings) ->
                        mappings.associate { mapping ->
                            ChordKey(
                                modsMask = modsMask(mapping.chord.mods),
                                keyCode = mapping.chord.keyCode,
                            ) to mapping.action
                        }
                    },
            )
        },
        activeProfileId = settings.activeProfileId,
        defaultProfileId = settings.defaultProfileId,
        appBindings = settings.appBindings,
    )

/**
 * Публикует неизменяемый [RuntimeConfig] для службы (§9).
 *
 * - разбор настроек — на [dispatcher], не на главном потоке;
 * - ошибка потока → [RuntimeConfig.EMPTY] и [ConfigError.UNAVAILABLE] (D-018);
 * - `generation` растёт при каждой публикации — по нему видно, какая версия конфига
 *   приняла решение.
 */
class ConfigStore(
    repository: SettingsRepository,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val _runtime = MutableStateFlow(RuntimeConfig.EMPTY)
    val runtime: StateFlow<RuntimeConfig> = _runtime.asStateFlow()

    private val _error = MutableStateFlow<ConfigError?>(null)
    val error: StateFlow<ConfigError?> = _error.asStateFlow()

    private var generation = 0L

    init {
        scope.launch(dispatcher) {
            repository.settings
                .distinctUntilChanged()
                .map { settings -> buildRuntimeConfig(settings, generation + 1) }
                .catch {
                    _error.value = ConfigError.UNAVAILABLE
                    _runtime.value = RuntimeConfig.EMPTY
                }
                .collect { config ->
                    generation = config.generation
                    _error.value = null
                    _runtime.value = config
                }
        }
    }
}
