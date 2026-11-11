package com.mousemanager.diagnostics.input

import android.view.KeyEvent
import com.mousemanager.diagnostics.model.DiagEvent
import com.mousemanager.diagnostics.util.EventFormat

/** Copies a [KeyEvent] into an immutable [DiagEvent.Key]. Never consumes the original. */
object KeyEventMapper {

    fun map(event: KeyEvent, id: Long, timestampMs: Long): DiagEvent.Key = DiagEvent.Key(
        id = id,
        timestampMs = timestampMs,
        timestampLabel = EventFormat.timeLabel(timestampMs),
        deviceId = event.deviceId,
        deviceName = event.device?.name ?: "unknown",
        keyCode = event.keyCode,
        keyLabel = KeyEvent.keyCodeToString(event.keyCode),
        scanCode = event.scanCode,
        metaState = event.metaState,
        metaStateLabels = EventFormat.metaNames(event.metaState),
        ctrlPressed = event.isCtrlPressed,
        shiftPressed = event.isShiftPressed,
        altPressed = event.isAltPressed,
        metaPressed = event.isMetaPressed,
        repeatCount = event.repeatCount,
    )
}
