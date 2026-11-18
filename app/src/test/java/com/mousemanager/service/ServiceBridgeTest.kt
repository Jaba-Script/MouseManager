package com.mousemanager.service

import com.mousemanager.core.RemapEngine
import com.mousemanager.core.RuntimeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контракт [ServiceBridge] (D-031): матрица гейта, повторная проверка в момент
 * включения, безусловное выключение, синхронное обновление движка и зеркала,
 * изоляция ошибок. JUnit 4, без Android и Robolectric: зависимости моста внедряются
 * лямбдами.
 */
class ServiceBridgeTest {

    @Test
    fun `disconnected service cannot arm`() {
        val h = Harness(connected = false)

        assertFalse(h.bridge.canArm())
        assertFalse(h.bridge.setArmed(true))
        assertFalse(h.engine!!.armed)
        assertEquals(listOf(false), h.mirrored)
    }

    @Test
    fun `connected service without filter cannot arm`() {
        val h = Harness(filterApplied = false)

        assertFalse(h.bridge.canArm())
        assertFalse(h.bridge.setArmed(true))
        assertFalse(h.engine!!.armed)
    }

    @Test
    fun `unpublished config mirror cannot arm`() {
        val h = Harness(configGeneration = 0L)

        assertFalse(h.bridge.canArm())
        assertFalse(h.bridge.setArmed(true))
        assertFalse(h.engine!!.armed)
    }

    @Test
    fun `engine without config rejects arming even when the mirror is ready`() {
        // Зеркало обновилось, а движок ещё (или уже) без конфига: окончательное
        // решение принимает setArmed по engine.config.generation.
        val h = Harness(configGeneration = 1L, engine = engineWithGeneration(0L))

        assertTrue(h.bridge.canArm())
        assertFalse(h.bridge.setArmed(true))
        assertFalse(h.engine!!.armed)
        assertEquals(listOf(false), h.mirrored)
    }

    @Test
    fun `missing engine cannot arm and does not throw`() {
        val h = Harness(engine = null)

        assertFalse(h.bridge.canArm())
        assertFalse(h.bridge.setArmed(true))
        assertEquals(listOf(false), h.mirrored)
    }

    @Test
    fun `all conditions met arms the engine and the mirror`() {
        val h = Harness()

        assertTrue(h.bridge.canArm())
        assertTrue(h.bridge.setArmed(true))
        assertTrue(h.engine!!.armed)
        assertEquals(listOf(true), h.mirrored)
    }

    @Test
    fun `conditions are checked again at the moment of arming`() {
        val h = Harness()
        assertTrue(h.bridge.canArm())

        // Состояние изменилось между отрисовкой тумблера и нажатием.
        h.connected = false

        assertFalse(h.bridge.setArmed(true))
        assertFalse(h.engine!!.armed)
        assertEquals(listOf(false), h.mirrored)
    }

    @Test
    fun `disabling is always allowed even when disconnected`() {
        val h = Harness(connected = false)
        h.engine!!.setArmed(true)

        // Возврат — фактическое состояние armed, а не признак успеха операции.
        assertFalse(h.bridge.setArmed(false))
        assertFalse(h.engine.armed)
        assertEquals(listOf(false), h.mirrored)
    }

    @Test
    fun `disabling without engine still updates the mirror`() {
        val h = Harness(engine = null)

        assertFalse(h.bridge.setArmed(false))
        assertEquals(listOf(false), h.mirrored)
    }

    @Test
    fun `enabling twice is idempotent`() {
        val h = Harness()

        assertTrue(h.bridge.setArmed(true))
        assertTrue(h.bridge.setArmed(true))

        assertTrue(h.engine!!.armed)
        assertEquals(listOf(true, true), h.mirrored)
    }

    @Test
    fun `mirror failure on enable leaves the engine disarmed`() {
        val h = Harness()
        h.mirrorFailure = IllegalStateException("state flow failed")

        assertFalse(h.bridge.setArmed(true))
        assertFalse(h.engine!!.armed)
        assertTrue(h.mirrored.isEmpty())
    }

    @Test
    fun `engine provider failure is contained and disarms the mirror`() {
        val h = Harness()
        h.engineFailure = IllegalStateException("holder failed")

        assertFalse(h.bridge.setArmed(true))
        assertFalse(h.engine!!.armed)
        assertEquals(listOf(false), h.mirrored)
    }

    @Test
    fun `status provider failure is contained`() {
        val h = Harness()
        h.statusFailure = IllegalStateException("state failed")

        assertFalse(h.bridge.canArm())
        assertFalse(h.bridge.setArmed(true))
        assertFalse(h.engine!!.armed)
        assertEquals(listOf(false), h.mirrored)
    }

    @Test
    fun `enabling failure is reported to the error logger`() {
        val h = Harness()
        h.statusFailure = IllegalStateException("state failed")

        assertFalse(h.bridge.setArmed(true))

        assertEquals(1, h.logged.size)
        assertEquals("state failed", h.logged.single().message)
    }

    @Test
    fun `mirror failure does not prevent disarming`() {
        val h = Harness(connected = false)
        h.engine!!.setArmed(true)
        h.mirrorFailure = IllegalStateException("state flow failed")

        assertFalse(h.bridge.setArmed(false))
        assertFalse(h.engine.armed)
    }

    private class Harness(
        var connected: Boolean = true,
        var filterApplied: Boolean = true,
        var configGeneration: Long = 1L,
        val engine: RemapEngine? = engineWithGeneration(1L),
    ) {
        val mirrored = mutableListOf<Boolean>()
        val logged = mutableListOf<Throwable>()
        var mirrorFailure: Throwable? = null
        var statusFailure: Throwable? = null
        var engineFailure: Throwable? = null

        val bridge = ServiceBridge(
            engineProvider = {
                engineFailure?.let { throw it }
                engine
            },
            statusProvider = {
                statusFailure?.let { throw it }
                ServiceStatus(
                    connected = connected,
                    appliedFlags = if (filterApplied) FILTER_FLAG else 0,
                    connectedAt = 1L,
                    configGeneration = configGeneration,
                )
            },
            armedMirror = {
                mirrorFailure?.let { throw it }
                mirrored += it
            },
            logError = { logged += it },
        )

        private companion object {
            const val FILTER_FLAG = 0x20
        }
    }

    private companion object {
        /** Движок с опубликованным конфигом указанной generation. */
        fun engineWithGeneration(generation: Long): RemapEngine = RemapEngine().apply {
            config = if (generation == 0L) {
                RuntimeConfig.EMPTY
            } else {
                RuntimeConfig(
                    generation = generation,
                    targets = emptySet(),
                    profiles = emptyMap(),
                    activeProfileId = "default",
                    defaultProfileId = "default",
                    appBindings = emptyMap(),
                )
            }
        }
    }
}
