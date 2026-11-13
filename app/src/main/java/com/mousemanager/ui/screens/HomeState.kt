package com.mousemanager.ui.screens

import com.mousemanager.core.RuntimeConfig
import com.mousemanager.data.ConfigError

/**
 * Состояние карточки «Настройки» на Home (S7.2).
 *
 * [loading] — DataStore ещё не опубликовал первый снимок (`generation == 0`, §5.2).
 * Ошибка имеет приоритет над загрузкой: при ней показывается сообщение из D-018 §18.5.
 * [activeProfileName] = `null`, если активного профиля нет в снимке или имя пустое.
 */
data class HomeConfigState(
    val error: ConfigError?,
    val loading: Boolean,
    val targetsCount: Int,
    val activeProfileName: String?,
)

/**
 * Чистая функция без Compose и Android: UI только отображает результат, а матрицу
 * состояний (загрузка, ошибка, ноль/ненулевые цели, отсутствие профиля) проверяет
 * JVM-тест `HomeStateTest`.
 */
fun homeConfigState(runtime: RuntimeConfig, error: ConfigError?): HomeConfigState =
    HomeConfigState(
        error = error,
        loading = error == null && runtime.generation == 0L,
        targetsCount = runtime.targets.size,
        activeProfileName = runtime.profiles[runtime.activeProfileId]
            ?.name
            ?.takeIf(String::isNotBlank),
    )
