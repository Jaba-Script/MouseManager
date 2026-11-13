package com.mousemanager.devices

/**
 * Мышь или тачпад, видимый системе (§10).
 *
 * Ключ устройства — [descriptor] (D-003): `deviceId` меняется при каждом переподключении,
 * поэтому в модели его нет. Две одинаковые мыши одной модели дают одинаковый
 * `descriptor` и неразличимы — UI обязан об этом предупредить.
 *
 * [sources] хранится целиком: UI показывает источник, а фильтрация уже выполнена
 * каталогом. [isVirtual] оставлен в модели по §10; в текущей политике виртуальные
 * устройства отбрасываются, поэтому у записей каталога он всегда `false`.
 */
data class MouseDevice(
    val descriptor: String,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val sources: Int,
    val isVirtual: Boolean,
)
