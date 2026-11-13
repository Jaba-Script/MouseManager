package com.mousemanager.service

import android.view.KeyEvent
import com.mousemanager.core.KeyInput

/**
 * Единственное место, где `android.view.KeyEvent` превращается в [KeyInput] (§4, §6).
 * Вся остальная логика работает с неизменяемым `core`-событием и не знает про Android.
 *
 * `descriptor` берётся у устройства события; `deviceId` — только для диагностики
 * (D-003: он меняется при переподключении и не является ключом).
 */
object KeyEventMapper {

    fun toKeyInput(event: KeyEvent): KeyInput = KeyInput(
        keyCode = event.keyCode,
        action = event.action,
        repeatCount = event.repeatCount,
        metaState = event.metaState,
        deviceDescriptor = event.device?.descriptor,
        deviceId = event.deviceId,
        scanCode = event.scanCode,
        eventTime = event.eventTime,
    )
}
