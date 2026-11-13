package com.mousemanager.core

/** Решение горячего пути (§5.4). */
sealed interface Verdict {
    data class Pass(val reason: PassReason) : Verdict
    data object Consume : Verdict
    data class ConsumeAndDispatch(val action: ActionCode) : Verdict
}

/** Причина пропуска события. Значения соответствуют D-017/D-018. */
enum class PassReason {
    DISARMED,
    EMPTY_TARGETS,
    NO_DESCRIPTOR,
    FOREIGN_DEVICE,
    MODIFIER,
    KEY_UP,
    NO_MAPPING,
    REPEAT_OF_UNKNOWN,
    ERROR,
}
