package com.mousemanager.devices

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Каталог мышей и тачпадов (§10, D-015).
 *
 * Начальная энумерация — `InputManager.inputDeviceIds`; дальше подписка на
 * [InputManager.InputDeviceListener] с колбэками на главном потоке. Любое
 * добавление/удаление/изменение устройства перечитывает список целиком: устройств
 * мало, зато нет частичных состояний.
 *
 * [devices] — неизменяемый снимок для UI; обновляется атомарно через [MutableStateFlow].
 * Две одинаковые мыши дают две записи с одинаковым `descriptor` — каталог их не
 * склеивает, иначе UI не смог бы предупредить о неразличимости (D-003).
 */
class DeviceCatalog(context: Context) {

    private val inputManager =
        context.applicationContext.getSystemService(Context.INPUT_SERVICE) as? InputManager

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<List<MouseDevice>>(emptyList())

    /** Подключённые мыши и тачпады; порядок стабилен — по имени, затем по `descriptor`. */
    val devices: StateFlow<List<MouseDevice>> = _devices.asStateFlow()

    private var listening = false

    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refresh()

        override fun onInputDeviceRemoved(deviceId: Int) = refresh()

        override fun onInputDeviceChanged(deviceId: Int) = refresh()
    }

    /** Начальная энумерация и подписка на изменения. Идемпотентно. */
    fun start() {
        refresh()
        if (listening) return
        inputManager?.registerInputDeviceListener(listener, mainHandler)
        listening = true
    }

    /** Отписка от системных событий. Последний снимок остаётся доступен UI. */
    fun stop() {
        if (!listening) return
        inputManager?.unregisterInputDeviceListener(listener)
        listening = false
    }

    private fun refresh() {
        val manager = inputManager ?: return
        val raw = manager.inputDeviceIds.toList().mapNotNull { id ->
            manager.getInputDevice(id)?.toRawDevice()
        }
        _devices.value = toMouseDevices(raw)
    }
}

/** Снимок [InputDevice], пригодный для разбора без Android (тестируется на JVM). */
internal data class RawInputDevice(
    val descriptor: String?,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val sources: Int,
    val isVirtual: Boolean,
)

/**
 * Фильтр §10/D-015: только указательные устройства, не виртуальные, с пригодным
 * `descriptor`. Устройства без `descriptor` отбрасываются: по D-003 их нельзя
 * идентифицировать и нельзя добавить в цели.
 */
internal fun toMouseDevices(raw: List<RawInputDevice>): List<MouseDevice> =
    raw.asSequence()
        .filterNot { it.isVirtual }
        .filter { isMouseOrTouchpad(it.sources) }
        .mapNotNull { device ->
            val descriptor = device.descriptor?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            MouseDevice(
                descriptor = descriptor,
                name = device.name.ifBlank { descriptor },
                vendorId = device.vendorId,
                productId = device.productId,
                sources = device.sources,
                isVirtual = false,
            )
        }
        .sortedWith(compareBy({ it.name.lowercase() }, { it.descriptor }))
        .toList()

/**
 * Источники — не чистые биты, а класс плюс флаги (см. комментарий в S1), поэтому
 * сравнение точное: `sources and SOURCE_MOUSE == SOURCE_MOUSE`, а не `!= 0`.
 */
internal fun isMouseOrTouchpad(sources: Int): Boolean =
    (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
        (sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD

private fun InputDevice.toRawDevice(): RawInputDevice = RawInputDevice(
    descriptor = descriptor,
    name = name.orEmpty(),
    vendorId = vendorId,
    productId = productId,
    sources = sources,
    isVirtual = isVirtual,
)
