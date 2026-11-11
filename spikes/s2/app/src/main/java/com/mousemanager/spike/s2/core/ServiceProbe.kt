package com.mousemanager.spike.s2.core

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ServiceStatus(
    val enabled: Boolean = false,
    val flags: Int = 0,
    val filterFlagRequested: Boolean = false,
) {
    /** Declared statically in res/xml/accessibility_service_config.xml. */
    val canRequestFilterKeyEventsDeclared: Boolean = true
}

/**
 * Two independent sources of truth, shown side by side in the UI:
 *  - what the system reports about our service (enabled? which flags did it keep?),
 *  - what the service itself reports once connected.
 *
 * Together they answer "did canRequestFilterKeyEvents actually take effect?" without
 * relying on undocumented APIs.
 */
object ServiceProbe {

    private val _status = MutableStateFlow(ServiceStatus())
    val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _filterFlagApplied = MutableStateFlow(false)
    val filterFlagApplied: StateFlow<Boolean> = _filterFlagApplied.asStateFlow()

    fun refresh(context: Context) {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

        val ours = manager
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.firstOrNull { it.resolveInfo?.serviceInfo?.packageName == context.packageName }

        val flags = ours?.flags ?: 0
        _status.value = ServiceStatus(
            enabled = ours != null,
            flags = flags,
            filterFlagRequested = flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0,
        )
    }

    fun onServiceConnected(appliedFlags: Int) {
        _connected.value = true
        _filterFlagApplied.value =
            appliedFlags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0
    }

    fun onServiceStopped() {
        _connected.value = false
        _filterFlagApplied.value = false
    }
}
