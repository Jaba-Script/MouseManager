package com.mousemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контракт [ChordTracker] по D-026 и `ARCHITECTURE.md` §6.1.
 *
 * Тесты фиксируют три вещи, на которых держится поглощение: pending не переживает
 * парный UP, скользящее окно не даёт утечки повторов и порядок «descriptor → keyCode»
 * изолирует устройства друг от друга.
 */
class ChordTrackerTest {

    private val tracker = ChordTracker()

    private fun input(
        keyCode: Int = KEYCODE_C,
        action: Int = ACTION_DOWN,
        repeatCount: Int = 0,
        metaState: Int = 0,
        deviceDescriptor: String? = MOUSE,
        eventTime: Long = 1_000L,
    ) = KeyInput(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        metaState = metaState,
        deviceDescriptor = deviceDescriptor,
        deviceId = 29,
        scanCode = 46,
        eventTime = eventTime,
    )

    @Test
    fun `setPending makes the pair visible`() {
        tracker.setPending(MOUSE, KEYCODE_C, 1_000L)

        assertTrue(tracker.hasPending(MOUSE, KEYCODE_C))
        assertFalse(tracker.hasPending(MOUSE, KEYCODE_V))
        assertFalse(tracker.hasPending(OTHER_MOUSE, KEYCODE_C))
    }

    @Test
    fun `consumePending removes the entry`() {
        tracker.setPending(MOUSE, KEYCODE_C, 1_000L)

        assertTrue(tracker.consumePending(MOUSE, KEYCODE_C))
        assertFalse(tracker.hasPending(MOUSE, KEYCODE_C))
        assertFalse("повторный UP не должен поглощаться", tracker.consumePending(MOUSE, KEYCODE_C))
    }

    @Test
    fun `consumePending for unknown pair returns false`() {
        assertFalse(tracker.consumePending(MOUSE, KEYCODE_C))
        assertFalse(tracker.consumePending(OTHER_MOUSE, KEYCODE_C))
    }

    @Test
    fun `setPending refuses to overwrite an existing entry`() {
        tracker.setPending(MOUSE, KEYCODE_C, 1_000L)

        assertThrows(IllegalStateException::class.java) {
            tracker.setPending(MOUSE, KEYCODE_C, 2_000L)
        }
    }

    @Test
    fun `refreshPending updates time`() {
        tracker.setPending(MOUSE, KEYCODE_C, 1_000L)

        assertTrue(tracker.refreshPending(MOUSE, KEYCODE_C, 2_000L))
        // 3 500 - 2 000 = 1 500 — ровно на границе, запись жива.
        tracker.expire(3_500L)
        assertTrue(tracker.hasPending(MOUSE, KEYCODE_C))
    }

    @Test
    fun `refreshPending without pending does not create it`() {
        assertFalse(tracker.refreshPending(MOUSE, KEYCODE_C, 1_000L))
        assertFalse(tracker.hasPending(MOUSE, KEYCODE_C))
    }

    @Test
    fun `refreshPending keeps the later time`() {
        tracker.setPending(MOUSE, KEYCODE_C, 2_000L)

        assertTrue(tracker.refreshPending(MOUSE, KEYCODE_C, 1_000L))
        // Если бы время откатилось к 1 000, запись истекла бы: 2 900 - 1 000 > 1 500.
        tracker.expire(2_900L)
        assertTrue(tracker.hasPending(MOUSE, KEYCODE_C))
    }

    @Test
    fun `expire removes pending older than timeout`() {
        tracker.setPending(MOUSE, KEYCODE_C, 1_000L)

        tracker.expire(2_501L)

        assertFalse(tracker.hasPending(MOUSE, KEYCODE_C))
    }

    @Test
    fun `expire keeps pending younger than timeout`() {
        tracker.setPending(MOUSE, KEYCODE_C, 1_000L)

        tracker.expire(2_400L)

        assertTrue(tracker.hasPending(MOUSE, KEYCODE_C))
    }

    @Test
    fun `exactly timeout is still alive`() {
        tracker.setPending(MOUSE, KEYCODE_C, 1_000L)

        tracker.expire(PENDING_TIMEOUT_MS + 1_000L)
        assertTrue("ровно 1500 мс — ещё действителен", tracker.hasPending(MOUSE, KEYCODE_C))

        tracker.expire(PENDING_TIMEOUT_MS + 1_001L)
        assertFalse(tracker.hasPending(MOUSE, KEYCODE_C))
    }

    @Test
    fun `sliding window keeps pending alive while events keep coming`() {
        tracker.setPending(MOUSE, KEYCODE_C, 1_000L)

        var now = 1_000L
        repeat(5) {
            now += 1_000L
            assertTrue(tracker.refreshPending(MOUSE, KEYCODE_C, now))
            tracker.expire(now)
        }

        assertEquals("серия repeat дольше окна", 6_000L, now)
        assertTrue(tracker.hasPending(MOUSE, KEYCODE_C))
    }

    @Test
    fun `expire time never goes backwards`() {
        tracker.setPending(MOUSE, KEYCODE_C, 10_000L)
        tracker.expire(9_000L)
        assertTrue("указатель остался на 10 000", tracker.hasPending(MOUSE, KEYCODE_C))

        tracker.expire(20_000L)
        assertFalse(tracker.hasPending(MOUSE, KEYCODE_C))

        // Запись с «прошлым» временем сразу просрочена: указатель уже 20 000.
        tracker.setPending(MOUSE, KEYCODE_C, 10_500L)
        tracker.expire(10_600L)
        assertFalse(tracker.hasPending(MOUSE, KEYCODE_C))
    }

    @Test
    fun `pending for two key codes are independent`() {
        tracker.setPending(MOUSE, KEYCODE_C, 1_000L)
        tracker.setPending(MOUSE, KEYCODE_V, 1_000L)

        assertTrue(tracker.consumePending(MOUSE, KEYCODE_C))
        assertTrue(tracker.hasPending(MOUSE, KEYCODE_V))
    }

    @Test
    fun `pending for two devices are independent`() {
        tracker.setPending(MOUSE, KEYCODE_C, 1_000L)
        tracker.setPending(OTHER_MOUSE, KEYCODE_C, 1_000L)

        assertTrue(tracker.consumePending(MOUSE, KEYCODE_C))
        assertTrue(tracker.hasPending(OTHER_MOUSE, KEYCODE_C))
    }

    @Test
    fun `modifier down and up toggles the bit`() {
        val down = input(keyCode = KEYCODE_CTRL_LEFT, action = ACTION_DOWN)
        tracker.onModifier(down)
        assertEquals(MOD_CTRL, tracker.heldMods(MOUSE))

        // Повторный DOWN идемпотентен.
        tracker.onModifier(down)
        assertEquals(MOD_CTRL, tracker.heldMods(MOUSE))

        // Игнорируемое: null descriptor, не-модификатор, неизвестный action.
        tracker.onModifier(down.copy(deviceDescriptor = null))
        tracker.onModifier(down.copy(keyCode = KEYCODE_C))
        tracker.onModifier(down.copy(action = ACTION_MULTIPLE))
        assertEquals("Ctrl всё ещё нажат", MOD_CTRL, tracker.heldMods(MOUSE))

        tracker.onModifier(down.copy(action = ACTION_UP))
        assertEquals(0, tracker.heldMods(MOUSE))
    }

    @Test
    fun `held modifiers are per device`() {
        tracker.onModifier(input(keyCode = KEYCODE_CTRL_LEFT, action = ACTION_DOWN))
        tracker.onModifier(
            input(
                keyCode = KEYCODE_SHIFT_LEFT,
                action = ACTION_DOWN,
                deviceDescriptor = OTHER_MOUSE,
            ),
        )

        assertEquals(MOD_CTRL, tracker.heldMods(MOUSE))
        assertEquals(MOD_SHIFT, tracker.heldMods(OTHER_MOUSE))
        assertEquals(0, tracker.heldMods("unknown"))
    }

    @Test
    fun `resetModifiers keeps pending`() {
        tracker.onModifier(input(keyCode = KEYCODE_CTRL_LEFT, action = ACTION_DOWN))
        tracker.setPending(MOUSE, KEYCODE_C, 1_000L)

        tracker.resetModifiers()

        assertEquals(0, tracker.heldMods(MOUSE))
        assertTrue("поглощённый DOWN обязан получить парный UP", tracker.hasPending(MOUSE, KEYCODE_C))
    }

    @Test
    fun `resetAll clears modifiers and pending`() {
        tracker.onModifier(input(keyCode = KEYCODE_CTRL_LEFT, action = ACTION_DOWN))
        tracker.setPending(MOUSE, KEYCODE_C, 1_000L)

        tracker.resetAll()

        assertEquals(0, tracker.heldMods(MOUSE))
        assertFalse(tracker.hasPending(MOUSE, KEYCODE_C))
    }

    @Test
    fun `resetAll does not reset the monotonic expire pointer`() {
        tracker.setPending(MOUSE, KEYCODE_C, 10_000L)
        tracker.expire(20_000L) // запись удалена, lastExpireTime = 20 000

        tracker.resetAll()

        tracker.setPending(MOUSE, KEYCODE_C, 10_500L)
        tracker.expire(10_600L)

        assertFalse(
            "lastExpireTime = 20 000 сохранился: запись с временем 10 500 уже просрочена",
            tracker.hasPending(MOUSE, KEYCODE_C),
        )
    }

    private companion object {
        const val KEYCODE_C = 31
        const val KEYCODE_V = 50
        const val ACTION_MULTIPLE = 2

        // Синтетические descriptor того же формата, что у реального устройства.
        const val MOUSE = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
        const val OTHER_MOUSE = "ffeeddccbbaa00998877665544332211ffeeddcc"
    }
}
