package com.mousemanager.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Контракт заглушки M1: `RemapEngine` **никогда** ничего не поглощает — для любого
 * события возвращается `Pass(DISARMED)`. Тест защищает от случайного появления логики
 * перехвата до M2.
 */
class RemapEngineTest {

    private val engine = RemapEngine()

    private fun input(
        keyCode: Int = KEYCODE_C,
        action: Int = ACTION_DOWN,
        repeatCount: Int = 0,
        metaState: Int = META_CTRL_LEFT_ON,
        deviceDescriptor: String? = MOUSE_DESCRIPTOR,
        deviceId: Int = 29,
        scanCode: Int = 46,
        eventTime: Long = 1_000L,
    ) = KeyInput(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        metaState = metaState,
        deviceDescriptor = deviceDescriptor,
        deviceId = deviceId,
        scanCode = scanCode,
        eventTime = eventTime,
    )

    @Test
    fun `mouse chord down is passed as disarmed`() {
        assertEquals(Verdict.Pass(PassReason.DISARMED), engine.onKey(input(action = ACTION_DOWN)))
    }

    @Test
    fun `mouse chord up is passed as disarmed`() {
        assertEquals(Verdict.Pass(PassReason.DISARMED), engine.onKey(input(action = ACTION_UP)))
    }

    @Test
    fun `repeat is passed as disarmed`() {
        assertEquals(
            Verdict.Pass(PassReason.DISARMED),
            engine.onKey(input(action = ACTION_DOWN, repeatCount = 3)),
        )
    }

    @Test
    fun `foreign device is passed as disarmed`() {
        val input = input(
            keyCode = KEYCODE_VOLUME_UP,
            deviceDescriptor = "485d69228e24",
            scanCode = 115,
            metaState = 0,
        )
        assertEquals(Verdict.Pass(PassReason.DISARMED), engine.onKey(input))
    }

    @Test
    fun `null descriptor is passed as disarmed`() {
        assertEquals(
            Verdict.Pass(PassReason.DISARMED),
            engine.onKey(input(deviceDescriptor = null)),
        )
    }

    @Test
    fun `modifier is passed as disarmed`() {
        val input = input(keyCode = KEYCODE_CTRL_LEFT, scanCode = 29)
        assertEquals(Verdict.Pass(PassReason.DISARMED), engine.onKey(input))
    }

    @Test
    fun `no event is ever consumed`() {
        val events = listOf(
            input(),
            input(action = ACTION_UP),
            input(repeatCount = 1),
            input(keyCode = KEYCODE_V, scanCode = 47),
            input(deviceDescriptor = null),
            input(deviceDescriptor = "653e1ca6257f"),
            input(metaState = 0),
            input(eventTime = Long.MAX_VALUE),
        )
        events.forEach { input ->
            val verdict = engine.onKey(input)
            assertEquals("неожиданный вердикт для $input", Verdict.Pass(PassReason.DISARMED), verdict)
        }
    }

    private companion object {
        // Значения ниже — из android.view.KeyEvent, продублированы числами намеренно:
        // core не должен зависеть от Android.
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1

        const val KEYCODE_C = 31
        const val KEYCODE_V = 50
        const val KEYCODE_CTRL_LEFT = 113
        const val KEYCODE_VOLUME_UP = 24

        // META_CTRL_ON | META_CTRL_LEFT_ON, как в измерениях S2 (SPIKES §4).
        const val META_CTRL_LEFT_ON = 0x3000

        // descriptor мыши VXE R1SE+ (SPIKES §4).
        const val MOUSE_DESCRIPTOR = "ce5035b0dc81cc3d25dbb6f30e5e51d03dcb3992"
    }
}
