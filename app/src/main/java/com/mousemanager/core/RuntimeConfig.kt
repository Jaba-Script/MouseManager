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

    /**
     * Ищет действие для аккорда (§8, D-008). Чистая функция: только чтение предвычисленных
     * карт, без аллокаций и без обращения к DataStore — вызывается на горячем пути.
     *
     * Профиль выбирается по цепочке: привязка `foregroundPackage` → активный → по
     * умолчанию. Профиль, которого нет в [profiles], цепочку не прерывает, а пропускается:
     * ссылка на удалённый профиль не должна отключать ремап (D-018).
     *
     * Внутри профиля побеждает маппинг конкретного устройства, затем [ANY_DEVICE].
     *
     * @return `null`, если подходящего профиля или маппинга нет — вызывающий отдаёт
     * `Pass(NO_MAPPING)`.
     */
    fun resolve(
        deviceDescriptor: String,
        chord: ChordKey,
        foregroundPackage: String?,
    ): ActionCode? {
        val profile = profileFor(foregroundPackage) ?: return null
        return profile.byDevice[deviceDescriptor]?.get(chord)
            ?: profile.byDevice[ANY_DEVICE]?.get(chord)
    }

    /**
     * Первый существующий профиль цепочки §8: привязка приложения → активный → default.
     * `null` для `foregroundPackage` означает «пакет неизвестен» и сразу ведёт к активному.
     */
    private fun profileFor(foregroundPackage: String?): ProfileRuntime? {
        if (foregroundPackage != null) {
            val boundId = appBindings[foregroundPackage]
            if (boundId != null) profiles[boundId]?.let { return it }
        }
        profiles[activeProfileId]?.let { return it }
        return profiles[defaultProfileId]
    }

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
