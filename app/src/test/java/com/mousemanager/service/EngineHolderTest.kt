package com.mousemanager.service

import com.mousemanager.core.ACTION_DOWN
import com.mousemanager.core.ACTION_UP
import com.mousemanager.core.ActionCode
import com.mousemanager.core.ChordKey
import com.mousemanager.core.KeyInput
import com.mousemanager.core.MOD_CTRL
import com.mousemanager.core.ProfileRuntime
import com.mousemanager.core.RemapEngine
import com.mousemanager.core.RuntimeConfig
import com.mousemanager.core.Verdict
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Контракт [EngineHolder] (§11): process-wide ссылка, last-attach-wins и
 * identity-safe detach. Обычный JUnit 4, без Android и Robolectric.
 */
class EngineHolderTest {

    @Before
    fun resetBefore() {
        detachAll()
    }

    @After
    fun resetAfter() {
        detachAll()
    }

    @Test
    fun `empty holder returns null`() {
        assertNull(EngineHolder.engine)
    }

    @Test
    fun `attach publishes the same instance`() {
        val engine = RemapEngine()

        EngineHolder.attach(engine)

        assertSame(engine, EngineHolder.engine)
    }

    @Test
    fun `attach replaces the previous instance`() {
        val previous = RemapEngine()
        val replacement = RemapEngine()

        EngineHolder.attach(previous)
        EngineHolder.attach(replacement)

        assertSame(replacement, EngineHolder.engine)
    }

    @Test
    fun `repeated attach of the same instance is idempotent`() {
        val engine = RemapEngine()

        EngineHolder.attach(engine)
        EngineHolder.attach(engine)

        assertSame(engine, EngineHolder.engine)
    }

    @Test
    fun `detach of the current instance clears the holder`() {
        val engine = RemapEngine()
        EngineHolder.attach(engine)

        EngineHolder.detach(engine)

        assertNull(EngineHolder.engine)
    }

    @Test
    fun `detach of a foreign instance keeps the current one`() {
        val current = RemapEngine()
        val foreign = RemapEngine()
        EngineHolder.attach(current)

        EngineHolder.detach(foreign)

        assertSame(current, EngineHolder.engine)
    }

    @Test
    fun `old instance cannot detach the new one`() {
        val old = RemapEngine()
        val new = RemapEngine()
        EngineHolder.attach(old)
        EngineHolder.attach(new)

        EngineHolder.detach(old)

        assertSame(new, EngineHolder.engine)
    }

    @Test
    fun `repeated detach is idempotent`() {
        val engine = RemapEngine()
        EngineHolder.attach(engine)

        EngineHolder.detach(engine)
        EngineHolder.detach(engine)

        assertNull(EngineHolder.engine)
    }

    @Test
    fun `reading without an instance is safe`() {
        val engine = RemapEngine()
        EngineHolder.attach(engine)
        EngineHolder.detach(engine)

        assertNull(EngineHolder.engine)
        assertNull(EngineHolder.engine)
    }

    @Test
    fun `holder does not change engine state`() {
        val runtime = runtimeConfig()
        val engine = RemapEngine().apply {
            this.config = runtime
            setArmed(true)
            foregroundPackage = "com.example.app"
        }

        EngineHolder.attach(engine)
        EngineHolder.detach(engine)

        assertSame(runtime, engine.config)
        assertTrue(engine.armed)
        assertEquals("com.example.app", engine.foregroundPackage)

        // resetState() очистил бы pending; attach/detach не должны этого делать.
        assertEquals(
            Verdict.ConsumeAndDispatch(ActionCode.BACK),
            engine.onKey(keyInput(ACTION_DOWN, eventTime = 1_000L)),
        )
        EngineHolder.attach(engine)
        EngineHolder.detach(engine)
        assertEquals(
            Verdict.Consume,
            engine.onKey(keyInput(ACTION_UP, eventTime = 1_100L)),
        )
    }

    @Test
    fun `sequential lifecycle attach detach attach`() {
        val first = RemapEngine()
        val second = RemapEngine()

        EngineHolder.attach(first)
        EngineHolder.detach(first)
        EngineHolder.attach(second)
        EngineHolder.detach(first)

        assertSame(second, EngineHolder.engine)

        EngineHolder.detach(second)

        assertNull(EngineHolder.engine)
    }

    /** Сброс process-wide object между тестами без тестового API в production-коде. */
    private fun detachAll() {
        while (true) {
            val engine = EngineHolder.engine ?: return
            EngineHolder.detach(engine)
        }
    }

    private fun runtimeConfig() = RuntimeConfig(
        generation = 1L,
        targets = setOf(DEVICE),
        profiles = mapOf(
            PROFILE to ProfileRuntime(
                id = PROFILE,
                name = PROFILE,
                byDevice = mapOf(
                    DEVICE to mapOf(ChordKey(MOD_CTRL, KEY_C) to ActionCode.BACK),
                ),
            ),
        ),
        activeProfileId = PROFILE,
        defaultProfileId = PROFILE,
        appBindings = emptyMap(),
    )

    private fun keyInput(action: Int, eventTime: Long) = KeyInput(
        keyCode = KEY_C,
        action = action,
        repeatCount = 0,
        metaState = META_CTRL_ON,
        deviceDescriptor = DEVICE,
        deviceId = 1,
        scanCode = 0,
        eventTime = eventTime,
    )

    private companion object {
        const val DEVICE = "test-device"
        const val PROFILE = "default"
        const val KEY_C = 31
        const val META_CTRL_ON = 0x1000
    }
}
