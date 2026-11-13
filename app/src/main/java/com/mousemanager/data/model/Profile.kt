package com.mousemanager.data.model

/** Именованный профиль (§5.1). В M1 создаётся только профиль по умолчанию. */
data class Profile(
    val id: String,
    val name: String,
    val mappings: List<Mapping>,
) {
    companion object {
        const val DEFAULT_ID = "default"

        /** Единственный профиль по умолчанию (D-011: именованные профили — v1). */
        val DEFAULT = Profile(
            id = DEFAULT_ID,
            name = "Основной",
            mappings = emptyList(),
        )
    }
}
