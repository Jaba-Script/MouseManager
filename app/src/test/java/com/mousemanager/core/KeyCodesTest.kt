package com.mousemanager.core

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Сверка продублированных констант с `android.view.KeyEvent` и контракт [modsOf].
 *
 * Robolectric не нужен: `static final int` встраиваются компилятором, поэтому значения
 * доступны в обычном JVM-тесте. Тест ловит опечатку в числовом дубле — компилятор её
 * не видит.
 */
class KeyCodesTest {

    @Test
    fun `action constants match KeyEvent`() {
        assertEquals(KeyEvent.ACTION_DOWN, ACTION_DOWN)
        assertEquals(KeyEvent.ACTION_UP, ACTION_UP)
    }

    @Test
    fun `modifier keycodes match KeyEvent`() {
        assertEquals(KeyEvent.KEYCODE_CTRL_LEFT, KEYCODE_CTRL_LEFT)
        assertEquals(KeyEvent.KEYCODE_CTRL_RIGHT, KEYCODE_CTRL_RIGHT)
        assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, KEYCODE_SHIFT_LEFT)
        assertEquals(KeyEvent.KEYCODE_SHIFT_RIGHT, KEYCODE_SHIFT_RIGHT)
        assertEquals(KeyEvent.KEYCODE_ALT_LEFT, KEYCODE_ALT_LEFT)
        assertEquals(KeyEvent.KEYCODE_ALT_RIGHT, KEYCODE_ALT_RIGHT)
        assertEquals(KeyEvent.KEYCODE_META_LEFT, KEYCODE_META_LEFT)
        assertEquals(KeyEvent.KEYCODE_META_RIGHT, KEYCODE_META_RIGHT)
    }

    @Test
    fun `modifier keycodes contain exactly the eight modifiers`() {
        assertEquals(8, MODIFIER_KEYCODES.size)
        assertEquals(
            setOf(
                KEYCODE_CTRL_LEFT,
                KEYCODE_CTRL_RIGHT,
                KEYCODE_SHIFT_LEFT,
                KEYCODE_SHIFT_RIGHT,
                KEYCODE_ALT_LEFT,
                KEYCODE_ALT_RIGHT,
                KEYCODE_META_LEFT,
                KEYCODE_META_RIGHT,
            ),
            MODIFIER_KEYCODES,
        )
    }

    @Test
    fun `modifier keycodes exclude chord letters and hardware keys`() {
        assertFalse(KeyEvent.KEYCODE_C in MODIFIER_KEYCODES)
        assertFalse(KeyEvent.KEYCODE_V in MODIFIER_KEYCODES)
        assertFalse(KeyEvent.KEYCODE_VOLUME_UP in MODIFIER_KEYCODES)
    }

    @Test
    fun `empty meta state gives empty mask`() {
        assertEquals(0, modsOf(0))
    }

    @Test
    fun `each modifier alone gives its own bit`() {
        assertEquals(
            MOD_CTRL,
            modsOf(KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON),
        )
        assertEquals(
            MOD_SHIFT,
            modsOf(KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON),
        )
        assertEquals(
            MOD_ALT,
            modsOf(KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON),
        )
        assertEquals(
            MOD_META,
            modsOf(KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON),
        )
    }

    @Test
    fun `several modifiers are combined into one mask`() {
        val metaState = KeyEvent.META_CTRL_ON or
            KeyEvent.META_CTRL_LEFT_ON or
            KeyEvent.META_SHIFT_ON or
            KeyEvent.META_SHIFT_LEFT_ON

        assertEquals(MOD_CTRL or MOD_SHIFT, modsOf(metaState))
    }

    @Test
    fun `foreign meta flags do not affect mask`() {
        val foreign = KeyEvent.META_CAPS_LOCK_ON or
            KeyEvent.META_NUM_LOCK_ON or
            KeyEvent.META_SYM_ON

        assertEquals(0, modsOf(foreign))
        // Посторонний флаг не мешает распознать настоящий модификатор.
        assertEquals(MOD_CTRL, modsOf(foreign or KeyEvent.META_CTRL_ON))
    }
}
