package com.mousemanager.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контракт отложенного fail-safe disarm (D-031 п. 31.11): callback срабатывает только
 * для своей сессии и только при пустом конфиге. Чистая логика, без Android.
 */
class DisarmGuardTest {

    @Test
    fun `current session with empty config disarms`() {
        assertTrue(DisarmGuard.shouldDisarm(7L, 7L, 0L))
    }

    @Test
    fun `stale session does not disarm the new one`() {
        assertFalse(DisarmGuard.shouldDisarm(6L, 7L, 0L))
    }

    @Test
    fun `published config blocks disarm even in the same session`() {
        assertFalse(DisarmGuard.shouldDisarm(7L, 7L, 3L))
    }

    @Test
    fun `stale session with published config does not disarm`() {
        assertFalse(DisarmGuard.shouldDisarm(6L, 7L, 3L))
    }

    @Test
    fun `initial session token zero is a valid session`() {
        assertTrue(DisarmGuard.shouldDisarm(0L, 0L, 0L))
    }
}
