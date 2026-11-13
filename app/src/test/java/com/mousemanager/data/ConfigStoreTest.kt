package com.mousemanager.data

import com.mousemanager.core.ANY_DEVICE
import com.mousemanager.core.ActionCode
import com.mousemanager.core.Chord
import com.mousemanager.core.ChordKey
import com.mousemanager.core.MOD_ALT
import com.mousemanager.core.MOD_CTRL
import com.mousemanager.core.Modifier
import com.mousemanager.core.RuntimeConfig
import com.mousemanager.data.model.Mapping
import com.mousemanager.data.model.Profile
import com.mousemanager.data.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `buildRuntimeConfig` — чистая часть [ConfigStore] (§5.2): проверяем сборку снимка без
 * DataStore и корутин.
 */
class ConfigStoreTest {

    @Test
    fun `empty snapshot has zero generation until first publication`() {
        // На это опирается служба: generation == 0 — «конфиг ещё не загружен»,
        // а не пустые пользовательские настройки.
        assertEquals(0L, RuntimeConfig.EMPTY.generation)
        assertEquals(1L, buildRuntimeConfig(Settings(), generation = 1L).generation)
    }

    @Test
    fun `default settings produce empty snapshot`() {
        val runtime = buildRuntimeConfig(Settings(), generation = 7L)

        assertEquals(7L, runtime.generation)
        assertTrue(runtime.targets.isEmpty())
        assertTrue(runtime.appBindings.isEmpty())
        assertEquals(Profile.DEFAULT_ID, runtime.activeProfileId)
        assertEquals(Profile.DEFAULT_ID, runtime.defaultProfileId)
        assertTrue(runtime.profiles.getValue(Profile.DEFAULT_ID).byDevice.isEmpty())
    }

    @Test
    fun `targets and bindings are copied`() {
        val settings = Settings(
            targets = setOf(MOUSE_DESCRIPTOR),
            appBindings = mapOf("com.android.chrome" to "default"),
        )

        val runtime = buildRuntimeConfig(settings, generation = 1L)

        assertEquals(setOf(MOUSE_DESCRIPTOR), runtime.targets)
        assertEquals(mapOf("com.android.chrome" to "default"), runtime.appBindings)
    }

    @Test
    fun `mapping is indexed by descriptor and modifier mask`() {
        val settings = Settings(
            targets = setOf(MOUSE_DESCRIPTOR),
            profiles = listOf(
                Profile(
                    id = Profile.DEFAULT_ID,
                    name = "Основной",
                    mappings = listOf(
                        Mapping(
                            deviceDescriptor = MOUSE_DESCRIPTOR,
                            chord = Chord(setOf(Modifier.CTRL), KEYCODE_C),
                            action = ActionCode.BACK,
                        ),
                    ),
                ),
            ),
        )

        val table = buildRuntimeConfig(settings, generation = 1L)
            .profiles.getValue(Profile.DEFAULT_ID)
            .byDevice.getValue(MOUSE_DESCRIPTOR)

        assertEquals(ActionCode.BACK, table[ChordKey(MOD_CTRL, KEYCODE_C)])
        assertNull(table[ChordKey(MOD_ALT, KEYCODE_C)])
        assertNull(table[ChordKey(MOD_CTRL, KEYCODE_V)])
    }

    @Test
    fun `null device becomes any device key`() {
        val settings = Settings(
            profiles = listOf(
                Profile(
                    id = Profile.DEFAULT_ID,
                    name = "Основной",
                    mappings = listOf(
                        Mapping(
                            deviceDescriptor = null,
                            chord = Chord(emptySet(), KEYCODE_V),
                            action = ActionCode.HOME,
                        ),
                    ),
                ),
            ),
        )

        val profile = buildRuntimeConfig(settings, generation = 1L)
            .profiles.getValue(Profile.DEFAULT_ID)

        assertEquals(ActionCode.HOME, profile.byDevice.getValue(ANY_DEVICE)[ChordKey(0, KEYCODE_V)])
        assertTrue(profile.byDevice.keys == setOf(ANY_DEVICE))
    }

    @Test
    fun `disabled mapping is skipped`() {
        val settings = Settings(
            profiles = listOf(
                Profile(
                    id = Profile.DEFAULT_ID,
                    name = "Основной",
                    mappings = listOf(
                        Mapping(
                            deviceDescriptor = MOUSE_DESCRIPTOR,
                            chord = Chord(setOf(Modifier.CTRL), KEYCODE_C),
                            action = ActionCode.BACK,
                            enabled = false,
                        ),
                    ),
                ),
            ),
        )

        val profile = buildRuntimeConfig(settings, generation = 1L)
            .profiles.getValue(Profile.DEFAULT_ID)

        assertTrue(profile.byDevice.isEmpty())
    }

    @Test
    fun `profiles are keyed by id`() {
        val settings = Settings(
            profiles = listOf(
                Profile(Profile.DEFAULT_ID, "Основной", emptyList()),
                Profile("work", "Рабочий", emptyList()),
            ),
            activeProfileId = "work",
        )

        val runtime = buildRuntimeConfig(settings, generation = 2L)

        assertEquals(setOf(Profile.DEFAULT_ID, "work"), runtime.profiles.keys)
        assertEquals("Рабочий", runtime.profiles.getValue("work").name)
        assertEquals("work", runtime.activeProfileId)
        assertEquals(2L, runtime.generation)
    }

    private companion object {
        const val MOUSE_DESCRIPTOR = "ce5035b0dc81cc3d25dbb6f30e5e51d03dcb3992"
        const val KEYCODE_C = 31
        const val KEYCODE_V = 50
    }
}
