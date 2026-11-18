package com.mousemanager.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Контракт статуса службы (§11): флаг фильтра берётся из применённых системой flags. */
class ServiceStateTest {

    @Before
    fun reset() {
        ServiceState.onStopped()
    }

    @After
    fun tearDown() {
        ServiceState.onStopped()
    }

    @Test
    fun `stopped service is not connected`() {
        val status = ServiceState.status.value

        assertFalse(status.connected)
        assertFalse(status.filterApplied)
        assertFalse(status.armed)
        assertEquals(0L, status.configGeneration)
    }

    @Test
    fun `config generation starts unpublished and is published by the service`() {
        ServiceState.onConnected(appliedFlags = FILTER_FLAG, now = 1L)
        assertEquals(0L, ServiceState.status.value.configGeneration)

        ServiceState.onConfigApplied(7L)

        assertEquals(7L, ServiceState.status.value.configGeneration)
        assertTrue(ServiceState.status.value.connected)
    }

    @Test
    fun `reconnecting resets the config generation`() {
        ServiceState.onConnected(appliedFlags = FILTER_FLAG, now = 1L)
        ServiceState.onConfigApplied(3L)

        ServiceState.onConnected(appliedFlags = FILTER_FLAG, now = 2L)

        assertEquals(0L, ServiceState.status.value.configGeneration)
    }

    @Test
    fun `stopping the service resets the config generation`() {
        ServiceState.onConnected(appliedFlags = FILTER_FLAG, now = 1L)
        ServiceState.onConfigApplied(4L)

        ServiceState.onStopped()

        assertEquals(0L, ServiceState.status.value.configGeneration)
    }

    @Test
    fun `armed mirror starts disabled and can be updated`() {
        assertFalse(ServiceState.status.value.armed)

        ServiceState.onArmed(true)

        assertTrue(ServiceState.status.value.armed)
    }

    @Test
    fun `stopping the service resets the armed mirror`() {
        ServiceState.onConnected(appliedFlags = FILTER_FLAG, now = 1L)
        ServiceState.onArmed(true)

        ServiceState.onStopped()

        assertFalse(ServiceState.status.value.connected)
        assertFalse(ServiceState.status.value.armed)
    }

    @Test
    fun `reconnecting never restores armed`() {
        ServiceState.onConnected(appliedFlags = FILTER_FLAG, now = 1L)
        ServiceState.onArmed(true)

        ServiceState.onConnected(appliedFlags = FILTER_FLAG, now = 2L)

        val status = ServiceState.status.value
        assertTrue(status.connected)
        assertFalse(status.armed)
        assertEquals(2L, status.connectedAt)
    }

    @Test
    fun `applied filter flag is reported`() {
        ServiceState.onConnected(appliedFlags = FILTER_FLAG, now = 123L)

        val status = ServiceState.status.value
        assertTrue(status.connected)
        assertTrue(status.filterApplied)
        assertTrue(status.appliedFlags and FILTER_FLAG != 0)
        assertEquals(123L, status.connectedAt)
    }

    @Test
    fun `missing filter flag is reported`() {
        ServiceState.onConnected(appliedFlags = 0, now = 456L)

        val status = ServiceState.status.value
        assertTrue(status.connected)
        assertFalse(status.filterApplied)
    }

    private companion object {
        // AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS (S2: flags=0x20).
        const val FILTER_FLAG = 0x20
    }
}
