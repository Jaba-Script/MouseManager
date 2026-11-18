package com.mousemanager.ui.screens

import com.mousemanager.data.ConfigError
import com.mousemanager.service.ServiceStatus

/**
 * Почему мастер-переключатель нельзя включить прямо сейчас (D-031).
 *
 * Причины упорядочены по действию пользователя: сначала подключение службы, затем
 * применённый системой фильтр, затем готовность конфигурации и, наконец, готовность
 * моста/движка. UI показывает одну причину — первую по этому порядку.
 */
enum class HomeMasterReason {
    SERVICE_DISCONNECTED,
    FILTER_MISSING,
    CONFIG_ERROR,
    CONFIG_LOADING,
    ENGINE_UNAVAILABLE,
}

/**
 * Состояние мастер-переключателя на Home (M2.4, D-031).
 *
 * [checked] — зеркало `armed` из [ServiceStatus], [enabled] — можно ли включить
 * перехват. [requiresConfirmation] истинно, только когда переключатель выключен и
 * включение разрешено: включать можно лишь после диалога, выключение выполняется
 * сразу. [reason] — первая причина недоступности либо `null`, если включение доступно.
 */
data class HomeMasterState(
    val checked: Boolean,
    val enabled: Boolean,
    val reason: HomeMasterReason?,
    val requiresConfirmation: Boolean,
)

/**
 * Чистая функция без Compose и Android: UI только отображает результат, а матрицу
 * состояний (служба, фильтр, конфигурация, готовность моста) проверяет JVM-тест
 * `HomeMasterStateTest`.
 *
 * Готовность конфигурации берётся из наблюдаемого [ServiceStatus.configGeneration],
 * а не из `RuntimeConfig` и не из движка: служба применяет конфиг асинхронно, и
 * только зеркало в `StateFlow` гарантирует перерисовку в тот момент, когда генерация
 * действительно записана. Поэтому `CONFIG_LOADING` не «залипает» и не превращается в
 * ложный `ENGINE_UNAVAILABLE`.
 *
 * [canArm] — вердикт `ServiceBridge.canArm()`: он включает проверку доступности
 * движка, которую UI не должен повторять самостоятельно. Поэтому при выполненных
 * видимых условиях, но закрытом мосте, причина — [HomeMasterReason.ENGINE_UNAVAILABLE].
 * Окончательное решение всё равно принимает `ServiceBridge.setArmed`.
 */
fun homeMasterState(
    status: ServiceStatus,
    error: ConfigError?,
    canArm: Boolean,
): HomeMasterState {
    val reason = when {
        !status.connected -> HomeMasterReason.SERVICE_DISCONNECTED
        !status.filterApplied -> HomeMasterReason.FILTER_MISSING
        error != null -> HomeMasterReason.CONFIG_ERROR
        status.configGeneration == 0L -> HomeMasterReason.CONFIG_LOADING
        !canArm -> HomeMasterReason.ENGINE_UNAVAILABLE
        else -> null
    }

    return HomeMasterState(
        checked = status.armed,
        enabled = reason == null,
        reason = reason,
        requiresConfirmation = reason == null && !status.armed,
    )
}
