package com.mousemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `RuntimeConfig.resolve` (§8): выбор профиля и поиск маппинга на предвычисленном
 * снимке, без DataStore и корутин.
 */
class RuntimeConfigTest {

    @Test
    fun `app binding selects bound profile`() {
        val config = config(
            profiles = mapOf(
                ACTIVE to profile(ACTIVE, deviceTable(deviceChord to ActionCode.BACK)),
                DEFAULT to profile(DEFAULT, deviceTable(deviceChord to ActionCode.HOME)),
                WORK to profile(WORK, deviceTable(deviceChord to ActionCode.RECENTS)),
            ),
            appBindings = mapOf(CHROME to WORK),
        )

        assertEquals(ActionCode.RECENTS, config.resolve(MOUSE, deviceChord, CHROME))
    }

    @Test
    fun `binding to missing profile falls back to active`() {
        val config = config(
            profiles = mapOf(
                ACTIVE to profile(ACTIVE, deviceTable(deviceChord to ActionCode.BACK)),
                DEFAULT to profile(DEFAULT, deviceTable(deviceChord to ActionCode.HOME)),
            ),
            appBindings = mapOf(CHROME to "deleted"),
        )

        assertEquals(ActionCode.BACK, config.resolve(MOUSE, deviceChord, CHROME))
    }

    @Test
    fun `missing active profile falls back to default`() {
        val config = config(
            profiles = mapOf(
                DEFAULT to profile(DEFAULT, deviceTable(deviceChord to ActionCode.HOME)),
            ),
            activeProfileId = "deleted",
            defaultProfileId = DEFAULT,
        )

        assertEquals(ActionCode.HOME, config.resolve(MOUSE, deviceChord, null))
    }

    @Test
    fun `missing active and default profiles resolve to null`() {
        val config = config(
            profiles = mapOf(
                WORK to profile(WORK, deviceTable(deviceChord to ActionCode.RECENTS)),
            ),
            activeProfileId = "deleted-active",
            defaultProfileId = "deleted-default",
        )

        assertNull(config.resolve(MOUSE, deviceChord, CHROME))
    }

    @Test
    fun `null foreground package uses active profile`() {
        val config = config(
            profiles = mapOf(
                ACTIVE to profile(ACTIVE, deviceTable(deviceChord to ActionCode.BACK)),
                DEFAULT to profile(DEFAULT, deviceTable(deviceChord to ActionCode.HOME)),
            ),
            appBindings = mapOf(CHROME to DEFAULT),
        )

        assertEquals(ActionCode.BACK, config.resolve(MOUSE, deviceChord, null))
    }

    @Test
    fun `unknown package uses active profile`() {
        val config = config(
            profiles = mapOf(
                ACTIVE to profile(ACTIVE, deviceTable(deviceChord to ActionCode.BACK)),
                WORK to profile(WORK, deviceTable(deviceChord to ActionCode.RECENTS)),
            ),
            appBindings = mapOf(CHROME to WORK),
        )

        assertEquals(ActionCode.BACK, config.resolve(MOUSE, deviceChord, "com.other.app"))
    }

    @Test
    fun `device specific mapping is found only for its device`() {
        val config = config(
            profiles = mapOf(
                ACTIVE to profile(ACTIVE, deviceTable(deviceChord to ActionCode.BACK)),
            ),
        )

        assertEquals(ActionCode.BACK, config.resolve(MOUSE, deviceChord, null))
        assertNull(config.resolve(OTHER_MOUSE, deviceChord, null))
    }

    @Test
    fun `device specific mapping wins over any device`() {
        val config = config(
            profiles = mapOf(
                ACTIVE to profile(
                    ACTIVE,
                    mapOf(
                        MOUSE to mapOf(deviceChord to ActionCode.BACK),
                        ANY_DEVICE to mapOf(deviceChord to ActionCode.HOME),
                    ),
                ),
            ),
        )

        assertEquals(ActionCode.BACK, config.resolve(MOUSE, deviceChord, null))
    }

    @Test
    fun `any device mapping is used when device has no entry`() {
        val config = config(
            profiles = mapOf(
                ACTIVE to profile(
                    ACTIVE,
                    mapOf(ANY_DEVICE to mapOf(deviceChord to ActionCode.HOME)),
                ),
            ),
        )

        assertEquals(ActionCode.HOME, config.resolve(MOUSE, deviceChord, null))
    }

    @Test
    fun `missing chord resolves to null`() {
        val config = config(
            profiles = mapOf(
                ACTIVE to profile(ACTIVE, deviceTable(deviceChord to ActionCode.BACK)),
            ),
        )

        assertNull(config.resolve(MOUSE, ChordKey(MOD_ALT, KEYCODE_C), null))
        assertNull(config.resolve(MOUSE, ChordKey(MOD_CTRL, KEYCODE_V), null))
    }

    @Test
    fun `empty snapshot resolves to null`() {
        assertNull(RuntimeConfig.EMPTY.resolve(MOUSE, deviceChord, CHROME))
    }

    @Test
    fun `profile without mappings resolves to null`() {
        val config = config(
            profiles = mapOf(
                ACTIVE to profile(ACTIVE, emptyMap()),
            ),
        )

        assertNull(config.resolve(MOUSE, deviceChord, null))
    }

    @Test
    fun `none is a real mapping and is returned as is`() {
        val config = config(
            profiles = mapOf(
                ACTIVE to profile(ACTIVE, deviceTable(deviceChord to ActionCode.NONE)),
            ),
        )

        assertEquals(ActionCode.NONE, config.resolve(MOUSE, deviceChord, null))
    }

    private fun profile(
        id: String,
        byDevice: Map<String, Map<ChordKey, ActionCode>>,
    ) = ProfileRuntime(id = id, name = id, byDevice = byDevice)

    private fun deviceTable(vararg entries: Pair<ChordKey, ActionCode>) =
        mapOf(MOUSE to entries.toMap())

    private fun config(
        profiles: Map<String, ProfileRuntime>,
        activeProfileId: String = ACTIVE,
        defaultProfileId: String = DEFAULT,
        appBindings: Map<String, String> = emptyMap(),
    ) = RuntimeConfig(
        generation = 1L,
        targets = setOf(MOUSE),
        profiles = profiles,
        activeProfileId = activeProfileId,
        defaultProfileId = defaultProfileId,
        appBindings = appBindings,
    )

    private companion object {
        const val ACTIVE = "active"
        const val DEFAULT = "default"
        const val WORK = "work"
        const val CHROME = "com.android.chrome"
        const val MOUSE = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
        const val OTHER_MOUSE = "ce5035b0e1a2b3c4d5e6f708192a3b4c5d6e7f80"
        const val KEYCODE_C = 31
        const val KEYCODE_V = 50

        val deviceChord = ChordKey(MOD_CTRL, KEYCODE_C)
    }
}
