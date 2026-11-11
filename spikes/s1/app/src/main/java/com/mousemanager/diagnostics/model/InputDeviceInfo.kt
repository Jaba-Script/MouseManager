package com.mousemanager.diagnostics.model

/** Immutable snapshot of a connected (non-virtual) input device. */
data class InputDeviceInfo(
    val id: Int,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val sourceLabels: List<String>,
    val isMouse: Boolean,
) {
    val vendorProduct: String
        get() = "%04x:%04x".format(vendorId, productId)

    val sourcesSummary: String
        get() = sourceLabels.joinToString(" · ").ifEmpty { "—" }
}
