package com.mousemanager.diagnostics

import com.mousemanager.core.PassReason
import com.mousemanager.core.Verdict

/**
 * Единственное место перевода вердикта движка в код журнала (D-027).
 *
 * Соответствие взаимно однозначное: каждая [PassReason] получает собственный
 * [Decision], а [Verdict.Consume] и [Verdict.ConsumeAndDispatch] — [Decision.CONSUMED].
 * Штатный пропуск никогда не попадает в [Decision.PASSED_ERROR]: этот код остаётся
 * только за [PassReason.ERROR] и за аварийной веткой службы.
 *
 * `when` по enum написан без `else`: новое значение [PassReason] сломает компиляцию,
 * а не превратится молча в ошибку, как это было в M1.
 *
 * Функция чистая, без Android и I/O: `core` не знает про журнал, `diagnostics` не
 * знает про службу.
 */
fun decisionFor(verdict: Verdict): Decision = when (verdict) {
    is Verdict.Pass -> when (verdict.reason) {
        PassReason.DISARMED -> Decision.PASSED_DISARMED
        PassReason.NO_DESCRIPTOR -> Decision.PASSED_NO_DESCRIPTOR
        PassReason.EMPTY_TARGETS -> Decision.PASSED_NO_TARGETS
        PassReason.FOREIGN_DEVICE -> Decision.PASSED_FOREIGN_DEVICE
        PassReason.MODIFIER -> Decision.PASSED_MODIFIER
        PassReason.KEY_UP -> Decision.PASSED_KEY_UP
        PassReason.NO_MAPPING -> Decision.PASSED_NO_MAPPING
        PassReason.REPEAT_OF_UNKNOWN -> Decision.PASSED_REPEAT
        PassReason.ERROR -> Decision.PASSED_ERROR
    }

    Verdict.Consume -> Decision.CONSUMED
    is Verdict.ConsumeAndDispatch -> Decision.CONSUMED
}
