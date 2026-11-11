package com.mousemanager.diagnostics.util

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure formatting helpers that turn platform bit masks into readable labels. */
object EventFormat {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.US)

    fun timeLabel(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(timeFormatter)

    fun axis(value: Float): String = String.format(Locale.US, "%+.3f", value)

    fun point(x: Float, y: Float): String = String.format(Locale.US, "%.1f / %.1f", x, y)

    fun hex(value: Int): String = String.format(Locale.US, "0x%08X", value)

    fun buttonNames(buttonState: Int): List<String> = buildList {
        if (buttonState and MotionEvent.BUTTON_PRIMARY != 0) add("PRIMARY")
        if (buttonState and MotionEvent.BUTTON_SECONDARY != 0) add("SECONDARY")
        if (buttonState and MotionEvent.BUTTON_TERTIARY != 0) add("TERTIARY")
        if (buttonState and MotionEvent.BUTTON_BACK != 0) add("BACK")
        if (buttonState and MotionEvent.BUTTON_FORWARD != 0) add("FORWARD")
        if (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) add("STYLUS_PRIMARY")
        if (buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0) add("STYLUS_SECONDARY")
    }

    fun buttonLabel(button: Int): String = when (button) {
        0 -> "—"
        MotionEvent.BUTTON_PRIMARY -> "PRIMARY (left)"
        MotionEvent.BUTTON_SECONDARY -> "SECONDARY (right)"
        MotionEvent.BUTTON_TERTIARY -> "TERTIARY (middle)"
        MotionEvent.BUTTON_BACK -> "BACK (side)"
        MotionEvent.BUTTON_FORWARD -> "FORWARD (side)"
        MotionEvent.BUTTON_STYLUS_PRIMARY -> "STYLUS_PRIMARY"
        MotionEvent.BUTTON_STYLUS_SECONDARY -> "STYLUS_SECONDARY"
        else -> "0x%02X".format(Locale.US, button)
    }

    fun toolTypeLabel(toolType: Int): String = when (toolType) {
        MotionEvent.TOOL_TYPE_UNKNOWN -> "UNKNOWN"
        MotionEvent.TOOL_TYPE_FINGER -> "FINGER"
        MotionEvent.TOOL_TYPE_STYLUS -> "STYLUS"
        MotionEvent.TOOL_TYPE_MOUSE -> "MOUSE"
        MotionEvent.TOOL_TYPE_ERASER -> "ERASER"
        else -> "0x%02X".format(Locale.US, toolType)
    }

    fun sourceNames(source: Int): List<String> = buildList {
        if (hasSource(source, InputDevice.SOURCE_MOUSE)) add("MOUSE")
        if (hasSource(source, InputDevice.SOURCE_MOUSE_RELATIVE)) add("MOUSE_RELATIVE")
        if (hasSource(source, InputDevice.SOURCE_TOUCHPAD)) add("TOUCHPAD")
        if (hasSource(source, InputDevice.SOURCE_TOUCHSCREEN)) add("TOUCHSCREEN")
        if (hasSource(source, InputDevice.SOURCE_KEYBOARD)) add("KEYBOARD")
        if (hasSource(source, InputDevice.SOURCE_TRACKBALL)) add("TRACKBALL")
        if (hasSource(source, InputDevice.SOURCE_JOYSTICK)) add("JOYSTICK")
        if (hasSource(source, InputDevice.SOURCE_STYLUS)) add("STYLUS")
        if (hasSource(source, InputDevice.SOURCE_ROTARY_ENCODER)) add("ROTARY_ENCODER")
    }

    fun sourceLabel(source: Int): String =
        sourceNames(source).joinToString(" | ").ifEmpty { hex(source) }

    /**
     * Source constants are not pure bit flags: they also carry a source *class* nibble,
     * so `source and SOURCE_X != 0` produces false positives (e.g. SOURCE_MOUSE also
     * "contains" SOURCE_TOUCHSCREEN). Exact matching on the constant is required.
     */
    private fun hasSource(source: Int, constant: Int): Boolean = source and constant == constant

    fun isMouseLike(source: Int): Boolean =
        hasSource(source, InputDevice.SOURCE_MOUSE) ||
            hasSource(source, InputDevice.SOURCE_MOUSE_RELATIVE) ||
            hasSource(source, InputDevice.SOURCE_TOUCHPAD) ||
            hasSource(source, InputDevice.SOURCE_TRACKBALL)

    fun metaNames(metaState: Int): List<String> = buildList {
        if (metaState and KeyEvent.META_CTRL_ON != 0) add("CTRL")
        if (metaState and KeyEvent.META_SHIFT_ON != 0) add("SHIFT")
        if (metaState and KeyEvent.META_ALT_ON != 0) add("ALT")
        if (metaState and KeyEvent.META_META_ON != 0) add("META")
        if (metaState and KeyEvent.META_SYM_ON != 0) add("SYM")
        if (metaState and KeyEvent.META_CAPS_LOCK_ON != 0) add("CAPS_LOCK")
        if (metaState and KeyEvent.META_NUM_LOCK_ON != 0) add("NUM_LOCK")
    }
}
