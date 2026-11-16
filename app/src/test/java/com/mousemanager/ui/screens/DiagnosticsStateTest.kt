package com.mousemanager.ui.screens

import com.mousemanager.R
import com.mousemanager.diagnostics.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.ZoneId

/**
 * Форматирование журнала Diagnostics (S7.5): время, `metaState` и подписи всех
 * двенадцати [Decision] (D-017, D-027). Тест чистый, Android-устройство не нужно.
 */
class DiagnosticsStateTest {

    @Test
    fun `time is formatted with milliseconds in the given zone`() {
        assertEquals("22:13:20.000", formatRecordTime(1_700_000_000_000L, ZoneId.of("UTC")))
        assertEquals("22:13:20.123", formatRecordTime(1_700_000_000_123L, ZoneId.of("UTC")))
    }

    @Test
    fun `meta state is printed as hex`() {
        assertEquals("0x0", formatMetaState(0))
        assertEquals("0x1000", formatMetaState(0x1000))
        assertEquals("0x11", formatMetaState(0x11))
    }

    @Test
    fun `every decision has its own label`() {
        val labels = Decision.entries.map { decisionLabelRes(it) }

        assertEquals(12, Decision.entries.size)
        assertEquals(Decision.entries.size, labels.distinct().size)
        labels.forEach { assertNotEquals(0, it) }
    }

    @Test
    fun `expected resources are used for sample decisions`() {
        assertEquals(R.string.decision_passed_disarmed, decisionLabelRes(Decision.PASSED_DISARMED))
        assertEquals(R.string.decision_passed_no_descriptor, decisionLabelRes(Decision.PASSED_NO_DESCRIPTOR))
        assertEquals(R.string.decision_passed_no_targets, decisionLabelRes(Decision.PASSED_NO_TARGETS))
        assertEquals(R.string.decision_passed_key_up, decisionLabelRes(Decision.PASSED_KEY_UP))
        assertEquals(R.string.decision_passed_repeat, decisionLabelRes(Decision.PASSED_REPEAT))
        assertEquals(R.string.decision_action_failed, decisionLabelRes(Decision.ACTION_FAILED))
    }
}
