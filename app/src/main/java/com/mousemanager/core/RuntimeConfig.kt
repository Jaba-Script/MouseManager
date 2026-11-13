package com.mousemanager.core

/**
 * Профиль в предвычисленном виде (§5.2). Ключ `byDevice` — `descriptor` или
 * [ANY_DEVICE]; значение — готовый маппинг «аккорд → действие».
 */
data class ProfileRuntime(
    val id: String,
    val name: String,
    val byDevice: Map<String, Map<ChordKey, ActionCode>>,
)

/**
 * Неизменяемый снимок конфигурации для горячего пути (§5.2, D-008).
 * Живёт в `@Volatile`-ссылке и публикуется целиком, без частичных обновлений.
 *
 * [generation] увеличивается при каждой публикации: по нему в журнале видно, какая
 * версия конфига приняла решение.
 */
data class RuntimeConfig(
    val generation: Long,
    val targets: Set<String>,
    val profiles: Map<String, ProfileRuntime>,
    val activeProfileId: String,
    val defaultProfileId: String,
    val appBindings: Map<String, String>,
) {
    companion object {
        /**
         * Fail-safe снимок (D-018, §9): нет целей, нет профилей, перехват невозможен.
         * Используется при ошибке декодирования настроек.
         */
        val EMPTY = RuntimeConfig(
            generation = 0,
            targets = emptySet(),
            profiles = emptyMap(),
            activeProfileId = "default",
            defaultProfileId = "default",
            appBindings = emptyMap(),
        )
    }
}
