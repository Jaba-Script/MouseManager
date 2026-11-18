package com.mousemanager.ui.screens

import com.mousemanager.data.ConfigError
import com.mousemanager.service.ServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контракт мастер-переключателя Home (M2.4, D-031): доступность включения, текущее
 * состояние, причина недоступности и необходимость подтверждения. Чистая логика без
 * Compose и Android.
 */
class HomeMasterStateTest {

    @Test
    fun `disconnected service disables the switch`() {
        val state = state(status(connected = false, filterApplied = false), canArm = false)

        assertFalse(state.checked)
        assertFalse(state.enabled)
        assertEquals(HomeMasterReason.SERVICE_DISCONNECTED, state.reason)
        assertFalse(state.requiresConfirmation)
    }

    @Test
    fun `connected service without filter is unavailable`() {
        val state = state(status(filterApplied = false), canArm = false)

        assertFalse(state.enabled)
        assertEquals(HomeMasterReason.FILTER_MISSING, state.reason)
    }

    @Test
    fun `config error is reported before loading`() {
        val state = state(
            status = status(configGeneration = 0L),
            error = ConfigError.UNAVAILABLE,
            canArm = false,
        )

        assertFalse(state.enabled)
        assertEquals(HomeMasterReason.CONFIG_ERROR, state.reason)
    }

    @Test
    fun `unpublished config is still loading`() {
        val state = state(
            status = status(configGeneration = 0L),
            canArm = false,
        )

        assertFalse(state.enabled)
        assertEquals(HomeMasterReason.CONFIG_LOADING, state.reason)
    }

    @Test
    fun `bridge refusal after visible checks reports unavailable engine`() {
        val state = state(status(), canArm = false)

        assertFalse(state.enabled)
        assertEquals(HomeMasterReason.ENGINE_UNAVAILABLE, state.reason)
    }

    @Test
    fun `published config generation clears the pending reason`() {
        // Служба подключена и фильтр применён, но конфиг ещё не записан в движок.
        val loading = state(status(configGeneration = 0L), canArm = false)
        assertEquals(HomeMasterReason.CONFIG_LOADING, loading.reason)

        // onConfigApplied(1): зеркало обновилось — Home перерисовывается сам.
        val ready = state(status(configGeneration = 1L), canArm = true)

        assertNull(ready.reason)
        assertTrue(ready.enabled)
        assertTrue(ready.requiresConfirmation)
    }

    @Test
    fun `engine unavailable is not shown before the config is applied`() {
        val state = state(status(configGeneration = 0L), canArm = false)

        assertEquals(HomeMasterReason.CONFIG_LOADING, state.reason)
        assertNotEquals(HomeMasterReason.ENGINE_UNAVAILABLE, state.reason)
    }

    @Test
    fun `open gate allows enabling and requires confirmation`() {
        val state = state(status(armed = false), canArm = true)

        assertFalse(state.checked)
        assertTrue(state.enabled)
        assertNull(state.reason)
        assertTrue(state.requiresConfirmation)
    }

    @Test
    fun `armed switch does not require confirmation to disable`() {
        val state = state(status(armed = true), canArm = true)

        assertTrue(state.checked)
        assertTrue(state.enabled)
        assertNull(state.reason)
        assertFalse(state.requiresConfirmation)
    }

    @Test
    fun `armed switch stays switchable when the gate closes`() {
        val state = state(status(connected = false, armed = true), canArm = false)

        assertTrue(state.checked)
        assertFalse(state.enabled)
        assertEquals(HomeMasterReason.SERVICE_DISCONNECTED, state.reason)
        assertFalse(state.requiresConfirmation)
    }

    @Test
    fun `disconnection outranks config error`() {
        val state = state(
            status = status(connected = false),
            error = ConfigError.UNAVAILABLE,
            canArm = false,
        )

        assertEquals(HomeMasterReason.SERVICE_DISCONNECTED, state.reason)
    }

    @Test
    fun `missing filter outranks config error`() {
        val state = state(
            status = status(filterApplied = false),
            error = ConfigError.UNAVAILABLE,
            canArm = false,
        )

        assertEquals(HomeMasterReason.FILTER_MISSING, state.reason)
    }

    private fun state(
        status: ServiceStatus = status(),
        error: ConfigError? = null,
        canArm: Boolean = false,
    ): HomeMasterState = homeMasterState(status, error, canArm)

    private fun status(
        connected: Boolean = true,
        filterApplied: Boolean = true,
        armed: Boolean = false,
        configGeneration: Long = 1L,
    ): ServiceStatus = ServiceStatus(
        connected = connected,
        appliedFlags = if (filterApplied) FILTER_FLAG else 0,
        connectedAt = if (connected) 1L else 0L,
        armed = armed,
        configGeneration = configGeneration,
    )

    private companion object {
        // AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS (S2: flags=0x20).
        const val FILTER_FLAG = 0x20
    }
}
