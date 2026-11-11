package com.mousemanager.spike.s2.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.mousemanager.spike.s2.BuildConfig
import com.mousemanager.spike.s2.core.FilterState
import com.mousemanager.spike.s2.core.InputLog
import com.mousemanager.spike.s2.core.ServiceProbe
import com.mousemanager.spike.s2.model.Decision

/**
 * Spike S2: observes every hardware key event and, when armed and the event comes from the
 * selected device, swallows the Ctrl+C / Ctrl+V chord that the mouse reports for its side
 * buttons.
 *
 * Nothing is remapped: the only two outcomes are "pass through" and "drop".
 */
class KeyFilterService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        FilterState.init(this)

        val info = serviceInfo ?: return
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info

        // Read back what the system accepted - this is the runtime half of the
        // canRequestFilterKeyEvents check shown in the UI.
        val applied = serviceInfo?.flags ?: 0
        ServiceProbe.onServiceConnected(applied)

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "onServiceConnected flags=${Integer.toHexString(applied)} " +
                    "filterRequested=" +
                    ((applied and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0),
            )
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val device = event.device
        val descriptor = device?.descriptor
        val armed = FilterState.isArmed()
        val target = FilterState.targetDescriptor()
        val isTarget = target != null && descriptor == target

        val decision = when {
            !armed -> Decision.PASSED_DISARMED
            !isTarget -> Decision.PASSED_OTHER_DEVICE
            !ChordPolicy.isChordKey(event) -> Decision.PASSED_OTHER_KEY
            else -> Decision.CONSUMED
        }

        InputLog.record(event, decision, isTarget, armed)

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "${decision.name} ${KeyEvent.keyCodeToString(event.keyCode)} " +
                    "device=${event.deviceId}('${device?.name}') target=$isTarget " +
                    "descriptor=${descriptor?.take(12)}",
            )
        }

        return decision == Decision.CONSUMED
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        ServiceProbe.onServiceStopped()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ServiceProbe.onServiceStopped()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MouseManagerS2"
    }
}
