package com.mousemanager.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import com.mousemanager.core.ActionCode
import com.mousemanager.core.ActionDispatcher

/**
 * Реализация [ActionDispatcher] через `AccessibilityService.performGlobalAction` (D-010).
 *
 * Маппинг [ActionCode] → `GLOBAL_ACTION_*` — единственное место перевода действия в
 * системный вызов. [ActionCode.NONE] возвращает `true` без вызова API и без сообщения:
 * это валидный маппинг, а не ошибка (D-007, D-027).
 *
 * **Отказ.** `performGlobalAction` может вернуть `false` или бросить исключение на
 * OEM-прошивке. В обоих случаях [dispatch] возвращает `false`, не повторяет вызов и не
 * выпускает исключение наружу. Запись `ACTION_FAILED` и текст сообщения — обязанность
 * службы (M2.3.4): у диспетчера нет полей события, а исходный `CONSUMED` должен
 * сохраниться (D-027 п. 27.3).
 *
 * **Rate limit.** Сообщение об отказе показывается не чаще одного раза в
 * [FAILURE_NOTIFY_INTERVAL_MS]. Первый отказ уведомляет всегда, даже если
 * [now] вернул `0`: для этого используется sentinel `null`, а не `0L`. Время
 * фиксируется до вызова [notifyFailure], поэтому исключение в самом сообщении не
 * приводит к повторному показу на следующем событии.
 *
 * **Тестируемость.** Android-обвязка внедряется лямбдами, а не передачей
 * `AccessibilityService`: `perform`, `notifyFailure`, `now` и `logFailure` можно
 * заменить в обычном JVM-тесте без Robolectric. В продукте служба передаёт
 * `{ performGlobalAction(it) }`, `Toast` и `Log.e`.
 *
 * Вызывается только с главного потока (`performGlobalAction` требует main thread);
 * экземпляр не потокобезопасен. `now` должен быть монотонным — по умолчанию
 * [SystemClock.elapsedRealtime].
 */
class GlobalActionDispatcher(
    private val perform: (Int) -> Boolean,
    private val notifyFailure: () -> Unit,
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
    private val logFailure: (Throwable) -> Unit = {},
) : ActionDispatcher {

    /**
     * Время последнего показа сообщения об отказе. `null` — сообщений ещё не было:
     * именно поэтому первый отказ уведомляет даже при `now() == 0`.
     */
    private var lastFailureNotifiedAt: Long? = null

    override fun dispatch(action: ActionCode): Boolean {
        // NONE — успех без системного вызова, Toast и записи об ошибке.
        val globalAction = globalActionFor(action) ?: return true

        val performed = try {
            perform(globalAction)
        } catch (e: Exception) {
            // D-018: сбой OEM-прошивки не должен ронять приложение или выпускать
            // исключение в горячий путь. Критические ошибки JVM не перехватываем.
            logSafely(e)
            false
        }

        if (!performed) notifyFailureOnce()
        return performed
    }

    /**
     * Показывает сообщение об отказе не чаще раза в [FAILURE_NOTIFY_INTERVAL_MS].
     * Отметка ставится до вызова [notifyFailure]: если Toast бросит исключение,
     * следующее событие не попытается показать его повторно в том же окне.
     */
    private fun notifyFailureOnce() {
        val current = now()
        val last = lastFailureNotifiedAt
        if (last != null && current - last < FAILURE_NOTIFY_INTERVAL_MS) return

        lastFailureNotifiedAt = current
        try {
            notifyFailure()
        } catch (e: Exception) {
            logSafely(e)
        }
    }

    /** Логирование не должно ломать горячий путь; критические ошибки JVM не ловим. */
    private fun logSafely(error: Throwable) {
        try {
            logFailure(error)
        } catch (ignored: Exception) {
            // Намеренно пусто: диагностика вторична по отношению к вводу.
        }
    }

    companion object {
        /** Минимальный интервал между сообщениями об отказе (D-010, D-018). */
        internal const val FAILURE_NOTIFY_INTERVAL_MS = 2_000L
    }
}

/**
 * `ActionCode` → константа `AccessibilityService`. `when` без `else`: новое действие
 * сломает компиляцию, а не превратится молча в no-op.
 *
 * `NONE` — единственное значение без системного действия; вызывающий код трактует
 * `null` как успешный no-op.
 */
internal fun globalActionFor(action: ActionCode): Int? = when (action) {
    ActionCode.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
    ActionCode.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
    ActionCode.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
    ActionCode.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
    ActionCode.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
    ActionCode.POWER_DIALOG -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
    ActionCode.LOCK_SCREEN -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
    ActionCode.SCREENSHOT -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
    ActionCode.NONE -> null
}
