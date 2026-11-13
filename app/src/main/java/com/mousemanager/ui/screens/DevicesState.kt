package com.mousemanager.ui.screens

import com.mousemanager.devices.MouseDevice
import java.util.Locale

/**
 * Строка экрана Devices (S7.3–S7.4).
 *
 * Одна строка — одна цель выбора, то есть один `descriptor` (D-003): несколько
 * физических устройств с одинаковым `descriptor` неразличимы системой, поэтому
 * склеиваются в одну строку с [duplicateCount] > 1 и общим чекбоксом.
 *
 * [name] = `null`, если устройство не подключено (сохранённая цель) или у него нет
 * имени. [vendorProduct] = `null` для отключённых целей: каталог их не знает.
 */
data class DeviceRow(
    val descriptor: String,
    val name: String?,
    val vendorProduct: String?,
    val connected: Boolean,
    val isTarget: Boolean,
    val duplicateCount: Int,
) {
    val isDuplicate: Boolean
        get() = duplicateCount > 1
}

/**
 * Чистая функция формирования списка (тестируется на JVM).
 *
 * Источники: снимок `DeviceCatalog.devices` (только подключённые устройства) и
 * `RuntimeConfig.targets` — сохранённые цели. Цель, которой нет в каталоге, остаётся
 * в списке как «не подключено»: выбор не удаляется при отключении мыши (§10).
 * Подключённые строки идут первыми и отсортированы по имени, отключённые — по
 * `descriptor`; порядок стабилен между обновлениями каталога.
 */
fun deviceRows(devices: List<MouseDevice>, targets: Set<String>): List<DeviceRow> {
    val byDescriptor = devices.groupBy { it.descriptor }

    val connected = byDescriptor
        .map { (descriptor, group) ->
            val device = group.first()
            DeviceRow(
                descriptor = descriptor,
                name = device.name.takeIf(String::isNotBlank),
                vendorProduct = formatVendorProduct(device.vendorId, device.productId),
                connected = true,
                isTarget = descriptor in targets,
                duplicateCount = group.size,
            )
        }
        .sortedBy { it.name?.lowercase(Locale.ROOT) ?: it.descriptor }

    val disconnected = (targets - byDescriptor.keys)
        .map { descriptor ->
            DeviceRow(
                descriptor = descriptor,
                name = null,
                vendorProduct = null,
                connected = false,
                isTarget = true,
                duplicateCount = 0,
            )
        }
        .sortedBy { it.descriptor }

    return connected + disconnected
}

/** `vendorId:productId` в hex — для идентификации модели рядом с именем. */
fun formatVendorProduct(vendorId: Int, productId: Int): String =
    String.format(Locale.ROOT, "%04x:%04x", vendorId, productId)

/**
 * Сокращённый `descriptor`: полный идентификатор слишком длинный для строки списка.
 * Короткие значения возвращаются без изменений.
 */
fun shortDescriptor(descriptor: String): String =
    if (descriptor.length <= SHORT_DESCRIPTOR_LENGTH) {
        descriptor
    } else {
        descriptor.take(8) + "…" + descriptor.takeLast(4)
    }

private const val SHORT_DESCRIPTOR_LENGTH = 16
