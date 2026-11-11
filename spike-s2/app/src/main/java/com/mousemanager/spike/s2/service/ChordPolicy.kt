package com.mousemanager.spike.s2.service

import android.view.KeyEvent

/**
 * The VXE R1SE+ side buttons arrive as a complete keyboard chord (measured in Spike S1):
 *
 *   Ctrl down -> C/V down -> Ctrl up -> C/V up
 *
 * Consuming only part of it would leave the system with a half-pressed modifier, so the
 * gate accepts all four key codes of the chord (and nothing else).
 */
object ChordPolicy {

    private val chordKeys = setOf(
        KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_C,
        KeyEvent.KEYCODE_V,
    )

    fun isChordKey(event: KeyEvent): Boolean = event.keyCode in chordKeys
}
