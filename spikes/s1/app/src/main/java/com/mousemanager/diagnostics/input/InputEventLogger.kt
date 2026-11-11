package com.mousemanager.diagnostics.input

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import com.mousemanager.diagnostics.model.DiagEvent
import com.mousemanager.diagnostics.model.InputDeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory journal of the current session.
 *
 * Mouse input can arrive at ~1000 Hz, so events are buffered in a plain deque and published
 * to Compose at most once per main-loop frame. This keeps recomposition smooth without
 * dropping data from the journal.
 *
 * The journal is observational only: nothing here consumes, recycles or re-dispatches events.
 */
object InputEventLogger {

    private const val MAX_EVENTS = 500

    private val mainHandler = Handler(Looper.getMainLooper())
    private val buffer = ArrayDeque<DiagEvent>(MAX_EVENTS)
    private var nextId = 1L
    private var publishScheduled = false

    private val _events = MutableStateFlow<List<DiagEvent>>(emptyList())
    val events: StateFlow<List<DiagEvent>> = _events.asStateFlow()

    private val _devices = MutableStateFlow<List<InputDeviceInfo>>(emptyList())
    val devices: StateFlow<List<InputDeviceInfo>> = _devices.asStateFlow()

    fun recordMotion(event: MotionEvent) {
        push(MotionEventMapper.map(event, id = nextId++, timestampMs = System.currentTimeMillis()))
    }

    fun recordKey(event: KeyEvent) {
        push(KeyEventMapper.map(event, id = nextId++, timestampMs = System.currentTimeMillis()))
    }

    fun refreshDevices(context: Context) {
        _devices.value = DeviceEnumerator.list(context.applicationContext)
    }

    fun clear() {
        buffer.clear()
        publishScheduled = false
        mainHandler.removeCallbacksAndMessages(null)
        _events.value = emptyList()
    }

    private fun push(event: DiagEvent) {
        buffer.addFirst(event)
        while (buffer.size > MAX_EVENTS) {
            buffer.removeLast()
        }
        schedulePublish()
    }

    private fun schedulePublish() {
        if (publishScheduled) return
        publishScheduled = true
        mainHandler.post {
            publishScheduled = false
            _events.value = buffer.toList()
        }
    }
}
