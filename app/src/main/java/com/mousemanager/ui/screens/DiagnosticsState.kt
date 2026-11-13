package com.mousemanager.ui.screens

import androidx.annotation.StringRes
import com.mousemanager.R
import com.mousemanager.diagnostics.Decision
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT)

/** Время события в локальной зоне: миллисекунды нужны, потому что аккорд идёт ~120 мс. */
fun formatRecordTime(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp).atZone(zoneId))

/** `metaState` в hex: так битовые флаги читаются глазами. */
fun formatMetaState(metaState: Int): String = String.format(Locale.ROOT, "0x%x", metaState)

/**
 * Подписи всех восьми [Decision] (D-017). Enum и его значения не меняются: тексты
 * живут только в `strings.xml` (D-019). Функция чистая и покрыта JVM-тестом.
 */
@StringRes
fun decisionLabelRes(decision: Decision): Int = when (decision) {
    Decision.CONSUMED -> R.string.decision_consumed
    Decision.PASSED_DISARMED -> R.string.decision_passed_disarmed
    Decision.PASSED_FOREIGN_DEVICE -> R.string.decision_passed_foreign_device
    Decision.PASSED_MODIFIER -> R.string.decision_passed_modifier
    Decision.PASSED_NO_MAPPING -> R.string.decision_passed_no_mapping
    Decision.PASSED_NON_HARDWARE -> R.string.decision_passed_non_hardware
    Decision.PASSED_ERROR -> R.string.decision_passed_error
    Decision.ACTION_FAILED -> R.string.decision_action_failed
}
