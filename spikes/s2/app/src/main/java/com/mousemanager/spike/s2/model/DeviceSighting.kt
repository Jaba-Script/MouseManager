package com.mousemanager.spike.s2.model

import com.mousemanager.spike.s2.util.Format

/**
 * A device that produced at least one key event during this app run.
 *
 * [ids] holds the *distinct* deviceIds seen for one descriptor, in order of appearance.
 * That list is the actual answer to "does InputDevice.getDescriptor() survive a
 * reconnect?": if the mouse is reconnected and only ids grow, the descriptor is stable.
 */
data class DeviceSighting(
    val descriptor: String,
    val name: String,
    val ids: List<Int>,
    val events: Long,
    val firstSeenLabel: String,
    val lastSeenLabel: String,
) {
    val currentId: Int? get() = ids.lastOrNull()
    val reconnectCount: Int get() = (ids.size - 1).coerceAtLeast(0)
    val idHistory: String get() = ids.joinToString(" → ").ifEmpty { "—" }
    val shortDescriptor: String get() = Format.shortDescriptor(descriptor)
}
