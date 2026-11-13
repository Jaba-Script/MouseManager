package com.mousemanager.diagnostics

/**
 * Неизменяемая запись журнала (D-017): снимок события и принятого решения.
 *
 * [descriptor] — единственный стабильный идентификатор устройства: `deviceId` меняется
 * при переподключении (SPIKES §2), поэтому в журнал пишется и он, и имя.
 *
 * [action] — значение `KeyEvent.ACTION_DOWN` / `ACTION_UP` (0/1); `diagnostics` не
 * импортирует Android-константы, чтобы запись оставалась обычными данными.
 *
 * [armed] в M1 всегда `false`: мастер-переключателя ещё нет, `RemapEngine` ничего не
 * поглощает (D-006). Значение по умолчанию защищает от случайной установки `true`
 * до M2.
 */
data class KeyRecord(
    val timestamp: Long,
    val deviceId: Int,
    val deviceName: String,
    val descriptor: String?,
    val keyCode: Int,
    val scanCode: Int,
    val metaState: Int,
    val repeatCount: Int,
    val action: Int,
    val decision: Decision,
    val isTarget: Boolean,
    val armed: Boolean = false,
)
