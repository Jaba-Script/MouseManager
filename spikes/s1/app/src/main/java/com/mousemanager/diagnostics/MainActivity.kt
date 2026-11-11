package com.mousemanager.diagnostics

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mousemanager.diagnostics.input.InputEventLogger
import com.mousemanager.diagnostics.ui.DiagnosticsScreen
import com.mousemanager.diagnostics.ui.theme.MouseManagerTheme
import com.mousemanager.diagnostics.util.EventFormat

class MainActivity : ComponentActivity() {

    private var lastSeenDeviceId = Int.MIN_VALUE

    private companion object {
        const val TAG = "MouseManager"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MouseManagerTheme {
                DiagnosticsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        InputEventLogger.refreshDevices(this)
    }

    /**
     * Observes every generic motion event of this window: cursor movement, hover,
     * button presses and wheel scrolling. The event is only copied into the journal,
     * then handed back to the platform unchanged.
     */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        InputEventLogger.recordMotion(ev)
        if (BuildConfig.DEBUG) {
            val masked = ev.actionMasked
            if (masked == MotionEvent.ACTION_BUTTON_PRESS || masked == MotionEvent.ACTION_BUTTON_RELEASE) {
                Log.d(
                    TAG,
                    "button ${if (masked == MotionEvent.ACTION_BUTTON_PRESS) "PRESS" else "RELEASE"} " +
                        "actionButton=${EventFormat.buttonLabel(ev.actionButton)} " +
                        "state=0x${ev.buttonState.toString(16)} device=${ev.deviceId}('${ev.device?.name}')",
                )
            }
        }
        refreshDevicesIfNeeded(ev.deviceId)
        return super.dispatchGenericMotionEvent(ev)
    }

    /**
     * Observes key events before the rest of the app sees them. Some mice report their
     * side buttons as KEYCODE_BACK / KEYCODE_FORWARD, so this is worth logging too.
     * Nothing is consumed.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        InputEventLogger.recordKey(event)
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "key ${KeyEvent.keyCodeToString(event.keyCode)} scan=${event.scanCode} " +
                    "device=${event.deviceId}('${event.device?.name}') meta=0x${event.metaState.toString(16)} " +
                    "action=${if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"}",
            )
        }
        refreshDevicesIfNeeded(event.deviceId)
        return super.dispatchKeyEvent(event)
    }

    private fun refreshDevicesIfNeeded(deviceId: Int) {
        if (deviceId == lastSeenDeviceId) return
        lastSeenDeviceId = deviceId
        InputEventLogger.refreshDevices(this)
    }
}
