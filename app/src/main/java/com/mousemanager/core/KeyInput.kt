package com.mousemanager.core

/**
 * Неизменяемое событие горячего пути (§5.3). Создаётся в `service/KeyEventMapper`
 * из `android.view.KeyEvent`, поэтому сам `core` не импортирует Android и тестируется
 * обычным JUnit.
 *
 * [action] — значение `KeyEvent.ACTION_DOWN` / `ACTION_UP`; в `core` константы не
 * дублируются: сравнение появится вместе с логикой решений в M2.
 */
data class KeyInput(
    val keyCode: Int,
    val action: Int,
    val repeatCount: Int,
    val metaState: Int,
    val deviceDescriptor: String?,
    val deviceId: Int,
    val scanCode: Int,
    val eventTime: Long,
)
