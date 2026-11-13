package com.mousemanager.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Фильтр каталога (§10, D-015): только мыши и тачпады, не виртуальные, с `descriptor`.
 * Тест не создаёт `InputDevice` — проверяется чистая часть каталога.
 */
class DeviceCatalogTest {

    @Test
    fun `mouse is included with all fields`() {
        val device = device(
            descriptor = DESCRIPTOR,
            name = "VXE R1SE+ Mouse",
            vendorId = 0x3554,
            productId = 0x0001,
            sources = SOURCE_MOUSE,
        )

        val result = toMouseDevices(listOf(device))

        assertEquals(
            listOf(
                MouseDevice(
                    descriptor = DESCRIPTOR,
                    name = "VXE R1SE+ Mouse",
                    vendorId = 0x3554,
                    productId = 0x0001,
                    sources = SOURCE_MOUSE,
                    isVirtual = false,
                ),
            ),
            result,
        )
    }

    @Test
    fun `touchpad is included`() {
        val result = toMouseDevices(listOf(device(name = "Touchpad", sources = SOURCE_TOUCHPAD)))

        assertEquals(listOf("Touchpad"), result.map { it.name })
    }

    @Test
    fun `pure keyboard is excluded`() {
        val result = toMouseDevices(
            listOf(
                device(name = "gpio-keys", sources = SOURCE_KEYBOARD),
                device(name = "USB Keyboard", sources = SOURCE_KEYBOARD or SOURCE_MOUSE),
            ),
        )

        // Клавиатура с мышью в одном устройстве — указательное устройство, оставляем.
        assertEquals(listOf("USB Keyboard"), result.map { it.name })
    }

    @Test
    fun `touchscreen is not a mouse`() {
        // 0x1002 and 0x2002 != 0, но это класс pointer, а не SOURCE_MOUSE.
        val result = toMouseDevices(listOf(device(name = "touch", sources = SOURCE_TOUCHSCREEN)))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `mouse relative is not part of M1 filter`() {
        val result = toMouseDevices(listOf(device(name = "relative", sources = SOURCE_MOUSE_RELATIVE)))

        assertTrue("фильтр §10 перечисляет MOUSE и TOUCHPAD", result.isEmpty())
    }

    @Test
    fun `virtual device is excluded`() {
        val result = toMouseDevices(
            listOf(device(name = "virtual mouse", sources = SOURCE_MOUSE, isVirtual = true)),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `device without descriptor is excluded`() {
        val result = toMouseDevices(
            listOf(
                device(name = "null", descriptor = null),
                device(name = "blank", descriptor = "   "),
                device(name = "ok"),
            ),
        )

        assertEquals(listOf("ok"), result.map { it.name })
    }

    @Test
    fun `duplicate descriptors are kept for the UI warning`() {
        val result = toMouseDevices(
            listOf(
                device(name = "Mouse A", descriptor = DESCRIPTOR),
                device(name = "Mouse B", descriptor = DESCRIPTOR),
            ),
        )

        assertEquals(listOf("Mouse A", "Mouse B"), result.map { it.name })
        assertEquals(setOf(DESCRIPTOR), result.map { it.descriptor }.toSet())
    }

    @Test
    fun `devices are sorted by name then descriptor`() {
        val result = toMouseDevices(
            listOf(
                device(name = "beta", descriptor = "b"),
                device(name = "Alpha", descriptor = "c"),
                device(name = "alpha", descriptor = "a"),
            ),
        )

        assertEquals(listOf("a", "c", "b"), result.map { it.descriptor })
    }

    @Test
    fun `blank name falls back to descriptor`() {
        val result = toMouseDevices(listOf(device(name = "  ", sources = SOURCE_MOUSE)))

        assertEquals(DESCRIPTOR, result.single().name)
        assertFalse(result.single().isVirtual)
    }

    private fun device(
        name: String,
        descriptor: String? = DESCRIPTOR,
        vendorId: Int = 1,
        productId: Int = 1,
        sources: Int = SOURCE_MOUSE,
        isVirtual: Boolean = false,
    ) = RawInputDevice(
        descriptor = descriptor,
        name = name,
        vendorId = vendorId,
        productId = productId,
        sources = sources,
        isVirtual = isVirtual,
    )

    private companion object {
        const val DESCRIPTOR = "ce5035b0dc81cc3d25dbb6f30e5e51d03dcb3992"

        // Значения android.view.InputDevice продублированы числами: JVM-тест не должен
        // зависеть от Android-заглушек.
        const val SOURCE_KEYBOARD = 0x00000101
        const val SOURCE_TOUCHSCREEN = 0x00001002
        const val SOURCE_MOUSE = 0x00002002
        const val SOURCE_MOUSE_RELATIVE = 0x00040004
        const val SOURCE_TOUCHPAD = 0x00100008
    }
}
