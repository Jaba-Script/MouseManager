package com.mousemanager.data.model

import com.mousemanager.core.ActionCode
import com.mousemanager.core.Chord

/**
 * Один маппинг профиля (§5.1, D-007).
 *
 * [deviceDescriptor] = `null` — любое целевое устройство (`device: null` в JSON).
 */
data class Mapping(
    val deviceDescriptor: String?,
    val chord: Chord,
    val action: ActionCode,
    val enabled: Boolean = true,
)
