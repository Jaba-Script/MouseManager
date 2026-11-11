package com.mousemanager.diagnostics.input

import android.view.MotionEvent
import com.mousemanager.diagnostics.model.DiagEvent
import com.mousemanager.diagnostics.util.EventFormat

/**
 * Copies everything interesting out of a [MotionEvent] into an immutable [DiagEvent.Motion].
 *
 * The original event is never mutated or recycled here: it is still owned by the
 * dispatcher and must continue down the normal Android dispatch chain.
 */
object MotionEventMapper {

    fun map(event: MotionEvent, id: Long, timestampMs: Long): DiagEvent.Motion {
        val source = event.source
        val toolType = event.getToolType(0)

        return DiagEvent.Motion(
            id = id,
            timestampMs = timestampMs,
            timestampLabel = EventFormat.timeLabel(timestampMs),
            deviceId = event.deviceId,
            deviceName = event.device?.name ?: "unknown",
            action = event.actionMasked,
            actionLabel = MotionEvent.actionToString(event.action),
            source = source,
            sourceLabel = EventFormat.sourceLabel(source),
            toolType = toolType,
            toolTypeLabel = EventFormat.toolTypeLabel(toolType),
            buttonState = event.buttonState,
            buttonStateLabels = EventFormat.buttonNames(event.buttonState),
            actionButton = event.actionButton,
            actionButtonLabel = EventFormat.buttonLabel(event.actionButton),
            verticalScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL),
            horizontalScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL),
            x = event.x,
            y = event.y,
            pointerCount = event.pointerCount,
            isMouseLike = EventFormat.isMouseLike(source),
        )
    }
}
