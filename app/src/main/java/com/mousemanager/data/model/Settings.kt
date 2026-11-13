package com.mousemanager.data.model

/**
 * Персистентная конфигурация (§5.1).
 *
 * `armed` здесь **отсутствует** намеренно: это runtime-флаг процесса, а не настройка
 * (D-006). DataStore не должен содержать ключа `armed` ни в каком виде.
 */
data class Settings(
    val schemaVersion: Int = 1,
    val targets: Set<String> = emptySet(),
    val deviceNames: Map<String, String> = emptyMap(),
    val profiles: List<Profile> = listOf(Profile.DEFAULT),
    val activeProfileId: String = Profile.DEFAULT_ID,
    val defaultProfileId: String = Profile.DEFAULT_ID,
    val appBindings: Map<String, String> = emptyMap(),
    val diagnosticsEnabled: Boolean = false,
)
