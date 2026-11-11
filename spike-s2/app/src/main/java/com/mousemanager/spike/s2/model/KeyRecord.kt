package com.mousemanager.spike.s2.model

/** What the service decided to do with a hardware key event. */
enum class Decision(val label: String) {
    CONSUMED("ПОГЛОЩЕНО"),
    PASSED_DISARMED("пропущено · выкл"),
    PASSED_OTHER_DEVICE("пропущено · не та мышь"),
    PASSED_OTHER_KEY("пропущено · другая клавиша"),
}

/** Immutable copy of one [android.view.KeyEvent] plus the interception verdict. */
data class KeyRecord(
    val id: Long,
    val timestampLabel: String,
    val deviceName: String,
    val deviceId: Int,
    val descriptor: String?,
    val keyCode: Int,
    val keyLabel: String,
    val scanCode: Int,
    val metaState: Int,
    val metaLabels: List<String>,
    val actionLabel: String,
    val repeatCount: Int,
    val decision: Decision,
    val isTarget: Boolean,
    val armed: Boolean,
)
