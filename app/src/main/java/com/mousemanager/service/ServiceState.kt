package com.mousemanager.service

import android.accessibilityservice.AccessibilityServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Статус службы для UI (§11). Служба и Activity живут в одном процессе, поэтому
 * достаточно синглтона; binder не нужен.
 *
 * [filterApplied] вычисляется из того, что система реально оставила в
 * `AccessibilityServiceInfo.flags`, а не из того, что мы запросили: на некоторых
 * прошивках флаг может не примениться.
 *
 * [armed] — **зеркало** `RemapEngine.armed` для UI (D-031). Значение меняется только
 * вместе с реальным `setArmed(...)`; источник истины для горячего пути — по-прежнему
 * движок. Поле не сохраняется и всегда `false` при подключении службы и старте
 * процесса (D-006).
 */
data class ServiceStatus(
    val connected: Boolean = false,
    val appliedFlags: Int = 0,
    val connectedAt: Long = 0L,
    val armed: Boolean = false,
) {
    val filterApplied: Boolean
        get() = (appliedFlags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0
}

/** Процессный синглтон статуса службы (§11). */
object ServiceState {

    private val _status = MutableStateFlow(ServiceStatus())

    val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    /**
     * Подключение службы никогда не включает перехват: создаётся новый статус, в
     * котором `armed` получает значение по умолчанию `false`, независимо от прошлого
     * состояния (D-006, D-031).
     */
    internal fun onConnected(appliedFlags: Int, now: Long = System.currentTimeMillis()) {
        _status.value = ServiceStatus(
            connected = true,
            appliedFlags = appliedFlags,
            connectedAt = now,
        )
    }

    /**
     * Обновляет зеркало `armed`. Вызывается только `ServiceBridge` в одном вызове с
     * `RemapEngine.setArmed(...)` на главном потоке (D-031).
     */
    internal fun onArmed(armed: Boolean) {
        _status.update { it.copy(armed = armed) }
    }

    /** Остановка службы сбрасывает и подключение, и зеркало `armed`. */
    internal fun onStopped() {
        _status.value = ServiceStatus()
    }
}
