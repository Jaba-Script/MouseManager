package com.mousemanager.ui.screens

import com.mousemanager.core.ProfileRuntime
import com.mousemanager.core.RuntimeConfig
import com.mousemanager.data.ConfigError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Матрица состояний карточки «Настройки» на Home (S7.2): загрузка, ошибка, ноль и
 * ненулевое число целей, наличие и отсутствие активного профиля. Тест не требует
 * Compose и Android-устройства.
 */
class HomeStateTest {

    @Test
    fun `empty runtime is loading with zero targets and no profile`() {
        val state = homeConfigState(RuntimeConfig.EMPTY, error = null)

        assertNull(state.error)
        assertTrue(state.loading)
        assertEquals(0, state.targetsCount)
        assertNull(state.activeProfileName)
    }

    @Test
    fun `published config is not loading and reports targets and profile`() {
        val state = homeConfigState(
            runtime(
                targets = setOf("ce5035b0", "another"),
                profiles = mapOf(PROFILE_ID to profile("Основной")),
            ),
            error = null,
        )

        assertFalse(state.loading)
        assertEquals(2, state.targetsCount)
        assertEquals("Основной", state.activeProfileName)
    }

    @Test
    fun `config error is reported instead of loading state`() {
        val state = homeConfigState(RuntimeConfig.EMPTY, error = ConfigError.UNAVAILABLE)

        assertEquals(ConfigError.UNAVAILABLE, state.error)
        assertFalse(state.loading)
        assertEquals(0, state.targetsCount)
    }

    @Test
    fun `missing active profile gives null name`() {
        val state = homeConfigState(
            runtime(activeProfileId = "missing"),
            error = null,
        )

        assertNull(state.activeProfileName)
    }

    @Test
    fun `blank profile name is treated as absent`() {
        val state = homeConfigState(
            runtime(profiles = mapOf(PROFILE_ID to profile("  "))),
            error = null,
        )

        assertNull(state.activeProfileName)
    }

    private fun runtime(
        generation: Long = 1,
        targets: Set<String> = emptySet(),
        profiles: Map<String, ProfileRuntime> = mapOf(PROFILE_ID to profile("Основной")),
        activeProfileId: String = PROFILE_ID,
    ) = RuntimeConfig(
        generation = generation,
        targets = targets,
        profiles = profiles,
        activeProfileId = activeProfileId,
        defaultProfileId = PROFILE_ID,
        appBindings = emptyMap(),
    )

    private fun profile(name: String) = ProfileRuntime(
        id = PROFILE_ID,
        name = name,
        byDevice = emptyMap(),
    )

    private companion object {
        const val PROFILE_ID = "default"
    }
}
