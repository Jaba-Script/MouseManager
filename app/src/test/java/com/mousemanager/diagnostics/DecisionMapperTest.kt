package com.mousemanager.diagnostics

import com.mousemanager.core.ActionCode
import com.mousemanager.core.PassReason
import com.mousemanager.core.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Контракт маппинга вердиктов в журнал (D-027): каждая [PassReason] получает
 * собственный [Decision], штатный пропуск не попадает в `PASSED_ERROR`, а
 * `PASSED_NON_HARDWARE` в v1 не производится.
 */
class DecisionMapperTest {

    @Test
    fun `every pass reason maps to its own decision`() {
        val expected = mapOf(
            PassReason.DISARMED to Decision.PASSED_DISARMED,
            PassReason.NO_DESCRIPTOR to Decision.PASSED_NO_DESCRIPTOR,
            PassReason.EMPTY_TARGETS to Decision.PASSED_NO_TARGETS,
            PassReason.FOREIGN_DEVICE to Decision.PASSED_FOREIGN_DEVICE,
            PassReason.MODIFIER to Decision.PASSED_MODIFIER,
            PassReason.KEY_UP to Decision.PASSED_KEY_UP,
            PassReason.NO_MAPPING to Decision.PASSED_NO_MAPPING,
            PassReason.REPEAT_OF_UNKNOWN to Decision.PASSED_REPEAT,
            PassReason.ERROR to Decision.PASSED_ERROR,
        )

        assertEquals(PassReason.entries.toSet(), expected.keys)
        expected.forEach { (reason, decision) ->
            assertEquals("причина $reason", decision, decisionFor(Verdict.Pass(reason)))
        }
        assertEquals(expected.size, expected.values.toSet().size)
    }

    @Test
    fun `only error reason maps to passed error`() {
        assertEquals(Decision.PASSED_ERROR, decisionFor(Verdict.Pass(PassReason.ERROR)))

        PassReason.entries
            .filter { it != PassReason.ERROR }
            .forEach { reason ->
                assertNotEquals("причина $reason", Decision.PASSED_ERROR, decisionFor(Verdict.Pass(reason)))
            }
    }

    @Test
    fun `consume maps to consumed`() {
        assertEquals(Decision.CONSUMED, decisionFor(Verdict.Consume))
    }

    @Test
    fun `consume and dispatch maps to consumed for any action`() {
        ActionCode.entries.forEach { action ->
            assertEquals("действие $action", Decision.CONSUMED, decisionFor(Verdict.ConsumeAndDispatch(action)))
        }
    }

    @Test
    fun `non hardware code is reserved and not produced`() {
        val produced = PassReason.entries.map { decisionFor(Verdict.Pass(it)) } +
            decisionFor(Verdict.Consume) +
            decisionFor(Verdict.ConsumeAndDispatch(ActionCode.NONE))

        assertFalse(Decision.PASSED_NON_HARDWARE in produced)
    }

    @Test
    fun `mapper produces every decision except reserved and dispatcher error`() {
        val produced = PassReason.entries.map { decisionFor(Verdict.Pass(it)) } +
            decisionFor(Verdict.Consume) +
            decisionFor(Verdict.ConsumeAndDispatch(ActionCode.BACK))

        assertEquals(
            Decision.entries.toSet() - setOf(Decision.PASSED_NON_HARDWARE, Decision.ACTION_FAILED),
            produced.toSet(),
        )
    }
}
