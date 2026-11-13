package com.mousemanager.service

import android.accessibilityservice.AccessibilityServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Статус службы для UI (§11). Служба и Activity живут в одном процессе, поэтому
 * достаточно синглтона; binder не нужен.
 *
 * [filterApplied] вычисляется из того, что система реально оставила в
 * `AccessibilityServiceInfo.flags`, а не из того, что мы запросили: на некоторых
 * прошивках флаг может не примениться.
 */
data class ServiceStatus(
    val connected: Boolean = false,
    val appliedFlags: Int = 0,
    val connectedAt: Long = 0L,
) {
    val filterApplied: Boolean
        get() = (appliedFlags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0
}

/** Процессный синглтон статуса службы (§11). */
object ServiceState {

    private val _status = MutableStateFlow(ServiceStatus())

    val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    internal fun onConnected(appliedFlags: Int, now: Long = System.currentTimeMillis()) {
        _status.value = ServiceStatus(
            connected = true,
            appliedFlags = appliedFlags,
            connectedAt = now,
        )
    }

    internal fun onStopped() {
        _status.value = ServiceStatus()
    }
}
