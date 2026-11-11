package com.mousemanager.diagnostics.input

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import com.mousemanager.diagnostics.model.InputDeviceInfo
import com.mousemanager.diagnostics.util.EventFormat

/** Lists physical (non-virtual) input devices: mice, touchpads, keyboards. */
object DeviceEnumerator {

    fun list(context: Context): List<InputDeviceInfo> {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager ?: return emptyList()

        return inputManager.inputDeviceIds
            .toList()
            .mapNotNull { inputManager.getInputDevice(it) }
            // System-generated (virtual) devices are noise here, but a physical touchpad of
            // the keyboard cover is also reported on the virtual bus — keep anything pointer-like.
            .filter { device -> !device.isVirtual || EventFormat.isMouseLike(device.sources) }
            .map { device -> device.toInfo() }
            .sortedWith(compareByDescending<InputDeviceInfo> { it.isMouse }.thenBy { it.name.lowercase() })
    }

    private fun InputDevice.toInfo(): InputDeviceInfo = InputDeviceInfo(
        id = id,
        name = name,
        vendorId = vendorId,
        productId = productId,
        sourceLabels = EventFormat.sourceNames(sources),
        isMouse = EventFormat.isMouseLike(sources),
    )
}
