package com.mousemanager.ui.screens

import com.mousemanager.devices.MouseDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Формирование списка Devices (S7.3–S7.4): склейка одинаковых `descriptor`,
 * сохранённые цели без подключённого устройства, сортировка и сокращение ID.
 * Тест чистый: ни Compose, ни Android-устройства не нужны.
 */
class DevicesStateTest {

    @Test
    fun `empty catalog and targets give empty list`() {
        assertTrue(deviceRows(devices = emptyList(), targets = emptySet()).isEmpty())
    }

    @Test
    fun `connected device is a single unselected row with model id`() {
        val rows = deviceRows(devices = listOf(device()), targets = emptySet())

        val row = rows.single()
        assertEquals(DESCRIPTOR, row.descriptor)
        assertEquals("VXE R1SE+ Mouse", row.name)
        assertEquals("3554:0001", row.vendorProduct)
        assertTrue(row.connected)
        assertFalse(row.isTarget)
        assertEquals(1, row.duplicateCount)
        assertFalse(row.isDuplicate)
    }

    @Test
    fun `targets are marked and several devices sorted by name`() {
        val rows = deviceRows(
            devices = listOf(
                device(descriptor = "d2", name = "Zebra Mouse", vendorId = 2, productId = 2),
                device(descriptor = "d1", name = "Alpha Mouse", vendorId = 1, productId = 1),
            ),
            targets = setOf("d2"),
        )

        assertEquals(listOf("Alpha Mouse", "Zebra Mouse"), rows.map { it.name })
        assertEquals(listOf(false, true), rows.map { it.isTarget })
    }

    @Test
    fun `disconnected target stays in list after connected devices`() {
        val rows = deviceRows(
            devices = listOf(device()),
            targets = setOf(GONE_DESCRIPTOR),
        )

        val gone = rows.last()
        assertEquals(2, rows.size)
        assertFalse(gone.connected)
        assertTrue(gone.isTarget)
        assertNull(gone.name)
        assertNull(gone.vendorProduct)
        assertEquals(0, gone.duplicateCount)
        assertEquals(GONE_DESCRIPTOR, gone.descriptor)
    }

    @Test
    fun `same descriptor is one shared target`() {
        val rows = deviceRows(
            devices = listOf(device(), device()),
            targets = setOf(DESCRIPTOR),
        )

        val row = rows.single()
        assertEquals(2, row.duplicateCount)
        assertTrue(row.isDuplicate)
        assertTrue(row.isTarget)
    }

    @Test
    fun `blank device name is treated as unknown`() {
        val row = deviceRows(devices = listOf(device(name = "   ")), targets = emptySet()).single()

        assertNull(row.name)
    }

    @Test
    fun `short descriptor keeps short values and trims long ones`() {
        assertEquals("abc", shortDescriptor("abc"))
        assertEquals("ce5035b0…3992", shortDescriptor(DESCRIPTOR))
    }

    private fun device(
        descriptor: String = DESCRIPTOR,
        name: String = "VXE R1SE+ Mouse",
        vendorId: Int = 0x3554,
        productId: Int = 0x0001,
    ) = MouseDevice(
        descriptor = descriptor,
        name = name,
        vendorId = vendorId,
        productId = productId,
        sources = SOURCE_MOUSE,
        isVirtual = false,
    )

    private companion object {
        const val DESCRIPTOR = "ce5035b0dc81cc3d25dbb6f30e5e51d03dcb3992"
        const val GONE_DESCRIPTOR = "ffffffffffffffffffffffffffffffffffffffff"

        // android.view.InputDevice.SOURCE_MOUSE; JVM-тест не зависит от Android-заглушек.
        const val SOURCE_MOUSE = 0x00002002
    }
}
