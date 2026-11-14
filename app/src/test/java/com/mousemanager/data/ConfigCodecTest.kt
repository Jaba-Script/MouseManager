package com.mousemanager.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.mousemanager.core.ActionCode
import com.mousemanager.core.Chord
import com.mousemanager.core.Modifier
import com.mousemanager.data.model.Mapping
import com.mousemanager.data.model.Profile
import com.mousemanager.data.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контракт кодека (D-007) и fail-safe разбора (D-018): битые данные дают значения по
 * умолчанию, неизвестное действие — `NONE`, схема новее приложения не интерпретируется.
 */
class ConfigCodecTest {

    @Test
    fun `empty preferences give safe defaults`() {
        val settings = ConfigCodec.decode(mutablePreferencesOf())

        assertEquals(ConfigCodec.CURRENT_SCHEMA_VERSION, settings.schemaVersion)
        assertTrue(settings.targets.isEmpty())
        assertTrue(settings.deviceNames.isEmpty())
        assertTrue(settings.appBindings.isEmpty())
        assertFalse(settings.diagnosticsEnabled)
        assertEquals(Profile.DEFAULT_ID, settings.activeProfileId)
        assertEquals(Profile.DEFAULT_ID, settings.defaultProfileId)
        assertEquals(listOf(Profile.DEFAULT_ID), settings.profiles.map { it.id })
    }

    @Test
    fun `profiles round trip preserves mappings`() {
        val profiles = listOf(
            Profile(
                id = "default",
                name = "Основной",
                mappings = listOf(
                    Mapping(
                        deviceDescriptor = MOUSE_DESCRIPTOR,
                        chord = Chord(setOf(Modifier.CTRL), KEYCODE_C),
                        action = ActionCode.BACK,
                        enabled = true,
                    ),
                    Mapping(
                        deviceDescriptor = null,
                        chord = Chord(emptySet(), KEYCODE_V),
                        action = ActionCode.SCREENSHOT,
                        enabled = false,
                    ),
                ),
            ),
        )
        val prefs = mutablePreferencesOf(
            ConfigKeys.profiles to ConfigCodec.encodeProfiles(profiles),
        )

        assertEquals(profiles, ConfigCodec.decode(prefs).profiles)
    }

    @Test
    fun `unknown action becomes NONE`() {
        val raw = """
            [{"id":"default","name":"Основной","mappings":[
              {"device":null,"chord":{"mods":["CTRL"],"key":31},"action":"TELEPORT","enabled":true}
            ]}]
        """.trimIndent()

        val mappings = ConfigCodec.decodeProfiles(raw).single().mappings

        assertEquals(ActionCode.NONE, mappings.single().action)
        assertEquals(setOf(Modifier.CTRL), mappings.single().chord.mods)
    }

    @Test
    fun `broken profiles json falls back to default profile`() {
        val prefs = mutablePreferencesOf(
            ConfigKeys.profiles to "{not a json",
            ConfigKeys.targets to setOf(MOUSE_DESCRIPTOR),
        )

        val settings = ConfigCodec.decode(prefs)

        assertEquals(listOf(Profile.DEFAULT_ID), settings.profiles.map { it.id })
        // Цели не зависят от разбора профилей и сохраняются.
        assertEquals(setOf(MOUSE_DESCRIPTOR), settings.targets)
    }

    @Test
    fun `newer schema is not interpreted`() {
        val prefs = mutablePreferencesOf(
            ConfigKeys.schemaVersion to 99,
            ConfigKeys.targets to setOf(MOUSE_DESCRIPTOR),
            ConfigKeys.activeProfileId to "work",
        )

        val settings = ConfigCodec.decode(prefs)

        assertEquals(99, settings.schemaVersion)
        assertTrue("цели из чужой схемы не применяются", settings.targets.isEmpty())
        assertEquals(Profile.DEFAULT_ID, settings.activeProfileId)
    }

    @Test
    fun `missing active profile falls back to existing one`() {
        val prefs = mutablePreferencesOf(
            ConfigKeys.profiles to ConfigCodec.encodeProfiles(
                listOf(Profile("work", "Рабочий", emptyList())),
            ),
            ConfigKeys.activeProfileId to "missing",
            ConfigKeys.defaultProfileId to "missing",
        )

        val settings = ConfigCodec.decode(prefs)

        assertEquals("work", settings.activeProfileId)
        assertEquals("work", settings.defaultProfileId)
    }

    @Test
    fun `device names and app bindings round trip`() {
        val names = mapOf(MOUSE_DESCRIPTOR to "VXE R1SE+ Mouse")
        val bindings = mapOf("com.android.chrome" to "work")
        val prefs = mutablePreferencesOf(
            ConfigKeys.deviceNames to ConfigCodec.encodeDeviceNames(names),
            ConfigKeys.appBindings to ConfigCodec.encodeAppBindings(bindings),
        )

        val settings = ConfigCodec.decode(prefs)

        assertEquals(names, settings.deviceNames)
        assertEquals(bindings, settings.appBindings)
    }

    @Test
    fun `broken dictionaries become empty maps`() {
        val prefs = mutablePreferencesOf(
            ConfigKeys.deviceNames to "[1,2,3]",
            ConfigKeys.appBindings to "oops",
        )

        val settings = ConfigCodec.decode(prefs)

        assertTrue(settings.deviceNames.isEmpty())
        assertTrue(settings.appBindings.isEmpty())
    }

    @Test
    fun `null ids and null dictionary values are ignored`() {
        val raw = """
            [{"id":null,"name":null,"mappings":[]},
             {"id":"work","name":null,"mappings":[]}]
        """.trimIndent()

        val profiles = ConfigCodec.decodeProfiles(raw)

        assertEquals(listOf("work"), profiles.map { it.id })
        assertEquals("work", profiles.single().name)

        val prefs = mutablePreferencesOf(
            ConfigKeys.deviceNames to """{"broken":null,"ok":"Mouse"}""",
        )
        assertEquals(mapOf("ok" to "Mouse"), ConfigCodec.decode(prefs).deviceNames)
    }

    @Test
    fun `armed is not part of settings`() {
        // Страховка от регрессии D-006: в модели нет поля armed, поэтому и в DataStore
        // не может появиться такой ключ. Java-рефлексия не требует kotlin-reflect.
        val fields = Settings::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name.startsWith("$") }
            .map { it.name }
            .toSet()

        assertEquals(
            setOf(
                "schemaVersion",
                "targets",
                "deviceNames",
                "profiles",
                "activeProfileId",
                "defaultProfileId",
                "appBindings",
                "diagnosticsEnabled",
            ),
            fields,
        )
    }

    private companion object {
        const val MOUSE_DESCRIPTOR = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
        const val KEYCODE_C = 31
        const val KEYCODE_V = 50
    }
}
