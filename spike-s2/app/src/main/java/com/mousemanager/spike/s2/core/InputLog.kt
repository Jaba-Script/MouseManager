package com.mousemanager.spike.s2.core

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.mousemanager.spike.s2.model.Decision
import com.mousemanager.spike.s2.model.DeviceSighting
import com.mousemanager.spike.s2.model.KeyRecord
import com.mousemanager.spike.s2.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Stats(val consumed: Long = 0L, val passed: Long = 0L) {
    val total: Long get() = consumed + passed
}

/**
 * In-memory journal of the current app run.
 *
 * Key events are relatively rare, but the same batching as in Spike S1 is kept: the deque is
 * the source of truth and Compose is notified at most once per main-loop frame.
 *
 * Everything here is called from the main thread (AccessibilityService callbacks and UI).
 */
object InputLog {

    private const val MAX_EVENTS = 300
    private const val MAX_IDS_PER_DEVICE = 12

    private val mainHandler = Handler(Looper.getMainLooper())
    private val buffer = ArrayDeque<KeyRecord>(MAX_EVENTS)
    private val sightings = LinkedHashMap<String, Sighting>()

    private var nextId = 1L
    private var publishScheduled = false
    private var consumed = 0L
    private var passed = 0L

    private val _events = MutableStateFlow<List<KeyRecord>>(emptyList())
    val events: StateFlow<List<KeyRecord>> = _events.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceSighting>>(emptyList())
    val devices: StateFlow<List<DeviceSighting>> = _devices.asStateFlow()

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    fun record(event: KeyEvent, decision: Decision, isTarget: Boolean, armed: Boolean) {
        val now = System.currentTimeMillis()
        val device = event.device
        val descriptor = device?.descriptor

        if (decision == Decision.CONSUMED) consumed++ else passed++

        if (descriptor != null) {
            val sighting = sightings.getOrPut(descriptor) { Sighting(descriptor, firstSeen = now) }
            sighting.name = device.name
            if (sighting.ids.lastOrNull() != event.deviceId) {
                sighting.ids.addLast(event.deviceId)
                while (sighting.ids.size > MAX_IDS_PER_DEVICE) sighting.ids.removeFirst()
            }
            sighting.events++
            sighting.lastSeen = now
        }

        buffer.addFirst(
            KeyRecord(
                id = nextId++,
                timestampLabel = Format.timeLabel(now),
                deviceName = device?.name ?: "unknown",
                deviceId = event.deviceId,
                descriptor = descriptor,
                keyCode = event.keyCode,
                keyLabel = Format.keyLabel(event.keyCode),
                scanCode = event.scanCode,
                metaState = event.metaState,
                metaLabels = Format.metaNames(event.metaState),
                actionLabel = if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP",
                repeatCount = event.repeatCount,
                decision = decision,
                isTarget = isTarget,
                armed = armed,
            ),
        )
        while (buffer.size > MAX_EVENTS) buffer.removeLast()
        schedulePublish()
    }

    /** Clears the journal and the counters; device sightings are kept on purpose. */
    fun clear() {
        buffer.clear()
        consumed = 0
        passed = 0
        schedulePublish()
    }

    private fun schedulePublish() {
        if (publishScheduled) return
        publishScheduled = true
        mainHandler.post {
            publishScheduled = false
            _events.value = buffer.toList()
            _stats.value = Stats(consumed, passed)
            _devices.value = sightings.values.map { it.toSighting() }
        }
    }

    private class Sighting(val descriptor: String, val firstSeen: Long) {
        var name: String = "—"
        var lastSeen: Long = firstSeen
        var events: Long = 0
        val ids = ArrayDeque<Int>()

        fun toSighting(): DeviceSighting = DeviceSighting(
            descriptor = descriptor,
            name = name,
            ids = ids.toList(),
            events = events,
            firstSeenLabel = Format.timeLabel(firstSeen),
            lastSeenLabel = Format.timeLabel(lastSeen),
        )
    }
}
