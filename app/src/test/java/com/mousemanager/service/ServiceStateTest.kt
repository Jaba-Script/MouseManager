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
