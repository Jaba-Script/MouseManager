package com.mousemanager.diagnostics.model

/**
 * One diagnostic entry captured from a physical input device.
 *
 * The event is a plain immutable copy of the platform event: nothing from the original
 * [android.view.MotionEvent] / [android.view.KeyEvent] is retained, so the platform
 * object can be recycled by the framework right after dispatch.
 */
sealed interface DiagEvent {
    val id: Long
    val timestampMs: Long
    val timestampLabel: String
    val deviceId: Int
    val deviceName: String

    data class Motion(
        override val id: Long,
        override val timestampMs: Long,
        override val timestampLabel: String,
        override val deviceId: Int,
        override val deviceName: String,
        val action: Int,
        val actionLabel: String,
        val source: Int,
        val sourceLabel: String,
        val toolType: Int,
        val toolTypeLabel: String,
        val buttonState: Int,
        val buttonStateLabels: List<String>,
        val actionButton: Int,
        val actionButtonLabel: String,
        val verticalScroll: Float,
        val horizontalScroll: Float,
        val x: Float,
        val y: Float,
        val pointerCount: Int,
        val isMouseLike: Boolean,
    ) : DiagEvent

    data class Key(
        override val id: Long,
        override val timestampMs: Long,
        override val timestampLabel: String,
        override val deviceId: Int,
        override val deviceName: String,
        val keyCode: Int,
        val keyLabel: String,
        val scanCode: Int,
        val metaState: Int,
        val metaStateLabels: List<String>,
        val ctrlPressed: Boolean,
        val shiftPressed: Boolean,
        val altPressed: Boolean,
        val metaPressed: Boolean,
        val repeatCount: Int,
    ) : DiagEvent
}
