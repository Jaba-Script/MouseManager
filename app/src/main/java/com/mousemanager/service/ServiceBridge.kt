package com.mousemanager.service

import androidx.annotation.MainThread
import com.mousemanager.core.RemapEngine

/**
 * Единственный публичный путь управления `armed` для UI (D-031).
 *
 * Мост не создаёт и не хранит собственный [RemapEngine]: он читает опубликованный
 * экземпляр через [EngineHolder] и меняет только его флаг и зеркало [ServiceState].
 * Поэтому после переподключения службы или fail-safe disarm в горячем пути UI видит
 * фактическое состояние, а не устаревшее «включено».
 *
 * **Гейт включения.** Включение разрешено только при одновременно выполненных
 * условиях (D-030 п. 30.12, D-031 п. 31.4):
 * - служба подключена;
 * - `filterApplied == true`;
 * - конфиг применён текущей сессией — наблюдаемое зеркало `configGeneration > 0`;
 * - движок доступен.
 *
 * Наблюдаемая часть гейта ([canArm]) нужна только для доступности тумблера: она не
 * читает движок, иначе UI не перерисовался бы после асинхронной записи конфига.
 * Окончательное решение [setArmed] принимает по свежему состоянию и дополнительно
 * проверяет `engine.config.generation` — источник истины, а не зеркало.
 * Выключение разрешено всегда и выполняется немедленно.
 *
 * **Ошибки.** Любой сбой при включении приводит к `armed=false`; исключение наружу
 * не выходит (D-018), причина передаётся в [logError]. Синхронное обновление движка и
 * зеркала в одном вызове исключает рассинхронизацию.
 *
 * **Потоки.** Все методы вызываются только на главном потоке: UI-клик,
 * `onServiceConnected`, `stopSession` и срабатывание `disarmIfEmpty` выполняются там.
 * Собственной синхронизации у моста нет — как и у [com.mousemanager.core.ChordTracker].
 */
class ServiceBridge(
    private val engineProvider: () -> RemapEngine? = { EngineHolder.engine },
    private val statusProvider: () -> ServiceStatus = { ServiceState.status.value },
    private val armedMirror: (Boolean) -> Unit = ServiceState::onArmed,
    private val logError: (Throwable) -> Unit = {},
) {

    /**
     * Можно ли включить перехват прямо сейчас. Используется UI для доступности
     * тумблера; решение всё равно принимает [setArmed] по свежему состоянию.
     *
     * Читает только наблюдаемые зеркала [ServiceStatus]: генерация конфига приходит
     * через `StateFlow`, поэтому Compose перерисовывает тумблер сразу после того, как
     * служба применила конфиг. Движок здесь не читается намеренно.
     */
    @MainThread
    fun canArm(): Boolean = try {
        if (engineProvider() == null) {
            false
        } else {
            isArmable(statusProvider())
        }
    } catch (t: Throwable) {
        logSafely(t)
        false
    }

    /**
     * Включает или выключает перехват. Возвращает фактическое состояние `armed` после
     * вызова: `true` — перехват включён, `false` — выключен (гейт не выполнен, ошибка
     * или явное выключение). Исключения наружу не выходят.
     */
    @MainThread
    fun setArmed(armed: Boolean): Boolean {
        if (!armed) {
            disarm()
            return false
        }

        return try {
            val engine = engineProvider()
            if (engine == null || !isArmable(statusProvider()) || !hasPublishedConfig(engine)) {
                disarm()
                false
            } else {
                engine.setArmed(true)
                armedMirror(true)
                true
            }
        } catch (t: Throwable) {
            // Любая ошибка включения — безопасное состояние (D-018, D-031 п. 31.9).
            logSafely(t)
            disarm()
            false
        }
    }

    /**
     * Свежая проверка наблюдаемой части гейта. `configGeneration` — зеркало движка,
     * обновляемое службой в одном вызове с записью `engine.config`; оно же служит
     * сигналом перерисовки для UI.
     */
    private fun isArmable(status: ServiceStatus): Boolean =
        status.connected &&
            status.filterApplied &&
            status.configGeneration > 0L

    /**
     * Окончательная проверка по источнику истины (D-030 п. 30.12): включение разрешает
     * только конфиг, реально записанный в движок. Зеркало могло обновиться раньше по
     * времени, поэтому одного `configGeneration` недостаточно.
     */
    private fun hasPublishedConfig(engine: RemapEngine): Boolean =
        engine.config.generation > 0L

    /**
     * Выключение разрешено всегда. Движок и зеркало переводятся в `false`; если один
     * из них недоступен или бросает исключение, второй всё равно обновляется.
     */
    private fun disarm() {
        try {
            engineProvider()?.setArmed(false)
        } catch (ignored: Throwable) {
            // Движок недоступен — зеркало всё равно должно стать false.
        }
        try {
            armedMirror(false)
        } catch (ignored: Throwable) {
            // Сбой зеркала не делает выключение неуспешным.
        }
    }

    /** Логгер не должен влиять на безопасное состояние при сбое включения. */
    private fun logSafely(t: Throwable) {
        try {
            logError(t)
        } catch (ignored: Throwable) {
            // Ошибка логгера не выходит наружу (D-018).
        }
    }
}
