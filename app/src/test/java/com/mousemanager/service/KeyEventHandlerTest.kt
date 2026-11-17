package com.mousemanager.service

import com.mousemanager.core.ACTION_DOWN
import com.mousemanager.core.ACTION_UP
import com.mousemanager.core.ActionCode
import com.mousemanager.core.ActionDispatcher
import com.mousemanager.core.ChordKey
import com.mousemanager.core.KeyInput
import com.mousemanager.core.MOD_CTRL
import com.mousemanager.core.ProfileRuntime
import com.mousemanager.core.RemapEngine
import com.mousemanager.core.RuntimeConfig
import com.mousemanager.core.Verdict
import com.mousemanager.diagnostics.Decision
import com.mousemanager.diagnostics.KeyRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контракт [KeyEventHandler] (M2.3.4): порядок `CONSUMED → dispatch → ACTION_FAILED`,
 * изоляция диагностики и гарантированное поглощение. JUnit 4, без Android и Robolectric.
 */
class KeyEventHandlerTest {

    private val recorded = mutableListOf<KeyRecord>()
    private val logged = mutableListOf<Throwable>()

    @Test
    fun `pass returns false and records a pass decision`() {
        val engine = RemapEngine()

        val result = handlerFor(engine).handle(keyInput(ACTION_DOWN), DEVICE_NAME)

        assertFalse(result)
        assertEquals(listOf(Decision.PASSED_DISARMED), decisions())
    }

    @Test
    fun `pass reason is mapped by DecisionMapper`() {
        val engine = armedEngine(runtimeConfig(targets = emptySet()))

        val result = handlerFor(engine).handle(keyInput(ACTION_DOWN), DEVICE_NAME)

        assertFalse(result)
        assertEquals(listOf(Decision.PASSED_NO_TARGETS), decisions())
    }

    @Test
    fun `consume returns true and records consumed`() {
        val engine = armedEngine()
        // Первый DOWN поглощает движок; для проверки Consume нужен живой pending.
        assertTrue(engine.onKey(keyInput(ACTION_DOWN, eventTime = 1_000L)) is Verdict.ConsumeAndDispatch)

        val result = handlerFor(engine).handle(keyInput(ACTION_UP, eventTime = 1_100L), DEVICE_NAME)

        assertTrue(result)
        assertEquals(listOf(Decision.CONSUMED), decisions())
    }

    @Test
    fun `consume and dispatch records consumed before dispatch`() {
        val engine = armedEngine()
        val events = mutableListOf<String>()
        val dispatcher = FakeDispatcher(onDispatch = { events += "dispatch" })
        val handler = handlerFor(
            engine = engine,
            dispatcher = dispatcher,
            record = { events += "record:${it.decision}" },
        )

        val result = handler.handle(keyInput(ACTION_DOWN), DEVICE_NAME)

        assertTrue(result)
        assertEquals(listOf("record:CONSUMED", "dispatch"), events)
        assertEquals(listOf(ActionCode.BACK), dispatcher.actions)
    }

    @Test
    fun `dispatch failure records action failed after consumed`() {
        val engine = armedEngine()
        val events = mutableListOf<String>()
        val dispatcher = FakeDispatcher(result = false, onDispatch = { events += "dispatch" })
        val handler = handlerFor(
            engine = engine,
            dispatcher = dispatcher,
            record = { events += "record:${it.decision}" },
        )

        val result = handler.handle(keyInput(ACTION_DOWN), DEVICE_NAME)

        assertTrue(result)
        assertEquals(
            listOf("record:CONSUMED", "dispatch", "record:ACTION_FAILED"),
            events,
        )
    }

    @Test
    fun `dispatch exception is contained and still consumed`() {
        val engine = armedEngine()
        val events = mutableListOf<String>()
        val dispatcher = FakeDispatcher(
            onDispatch = {
                events += "dispatch"
                throw IllegalStateException("performGlobalAction failed")
            },
        )
        val handler = handlerFor(
            engine = engine,
            dispatcher = dispatcher,
            record = { events += "record:${it.decision}" },
        )

        val result = handler.handle(keyInput(ACTION_DOWN), DEVICE_NAME)

        assertTrue(result)
        assertEquals(
            listOf("record:CONSUMED", "dispatch", "record:ACTION_FAILED"),
            events,
        )
        assertEquals(1, logged.size)
    }

    @Test
    fun `record failure does not prevent dispatch`() {
        val engine = armedEngine()
        val dispatcher = FakeDispatcher()
        var recordCalls = 0
        val handler = handlerFor(
            engine = engine,
            dispatcher = dispatcher,
            record = {
                recordCalls++
                throw IllegalStateException("journal is broken")
            },
        )

        val result = handler.handle(keyInput(ACTION_DOWN), DEVICE_NAME)

        assertTrue(result)
        assertEquals(1, recordCalls)
        assertEquals(listOf(ActionCode.BACK), dispatcher.actions)
    }

    @Test
    fun `record failure on consume is still consumed`() {
        val engine = armedEngine()
        assertTrue(engine.onKey(keyInput(ACTION_DOWN, eventTime = 1_000L)) is Verdict.ConsumeAndDispatch)
        val handler = handlerFor(
            engine = engine,
            record = { throw IllegalStateException("journal is broken") },
        )

        val result = handler.handle(keyInput(ACTION_UP, eventTime = 1_100L), DEVICE_NAME)

        assertTrue(result)
    }

    @Test
    fun `record failure on pass still passes`() {
        val engine = RemapEngine()
        val handler = handlerFor(
            engine = engine,
            record = { throw IllegalStateException("journal is broken") },
        )

        val result = handler.handle(keyInput(ACTION_DOWN), DEVICE_NAME)

        assertFalse(result)
    }

    @Test
    fun `record failure on action failed is contained`() {
        val engine = armedEngine()
        var recordCalls = 0
        val handler = handlerFor(
            engine = engine,
            dispatcher = FakeDispatcher(result = false),
            record = {
                recordCalls++
                if (recordCalls == 2) throw IllegalStateException("journal is broken")
                recorded += it
            },
        )

        val result = handler.handle(keyInput(ACTION_DOWN), DEVICE_NAME)

        assertTrue(result)
        assertEquals(2, recordCalls)
        assertEquals(listOf(Decision.CONSUMED), decisions())
    }

    @Test
    fun `engine exception disarms and records passed error`() {
        val engine = armedEngine()
        val handler = handlerFor(
            engine = engine,
            decide = { throw IllegalStateException("engine failed") },
            logError = { logged += it },
        )

        val result = handler.handle(keyInput(ACTION_DOWN), DEVICE_NAME)

        assertFalse(result)
        assertFalse(engine.armed)
        assertEquals(listOf(Decision.PASSED_ERROR), decisions())
        assertEquals(1, logged.size)
    }

    @Test
    fun `engine exception invokes the injected disarm callback`() {
        val engine = armedEngine()
        var disarmCalls = 0
        val handler = handlerFor(
            engine = engine,
            decide = { throw IllegalStateException("engine failed") },
            disarm = { disarmCalls++ },
        )

        val result = handler.handle(keyInput(ACTION_DOWN), DEVICE_NAME)

        assertFalse(result)
        assertEquals(1, disarmCalls)
        // Callback заменяет прямой setArmed: сам обработчик движок не выключает.
        assertTrue(engine.armed)
    }

    @Test
    fun `disarm callback failure is contained`() {
        val engine = armedEngine()
        val handler = handlerFor(
            engine = engine,
            decide = { throw IllegalStateException("engine failed") },
            disarm = { throw IllegalStateException("bridge failed") },
            logError = { logged += it },
        )

        val result = handler.handle(keyInput(ACTION_DOWN), DEVICE_NAME)

        assertFalse(result)
        assertEquals(listOf(Decision.PASSED_ERROR), decisions())
        // Первый лог — сбой callback, второй — исходная ошибка движка.
        assertEquals(2, logged.size)
    }

    @Test
    fun `armed is captured before the decision`() {
        val engine = armedEngine()
        val handler = handlerFor(
            engine = engine,
            decide = {
                engine.setArmed(false)
                engine.onKey(it)
            },
        )

        val result = handler.handle(keyInput(ACTION_DOWN), DEVICE_NAME)

        assertFalse(result)
        assertEquals(listOf(Decision.PASSED_DISARMED), decisions())
        assertTrue(recorded.single().armed)
        assertFalse(engine.armed)
    }

    @Test
    fun `action failed repeats the event fields of consumed`() {
        val engine = armedEngine()
        val handler = handlerFor(engine, dispatcher = FakeDispatcher(result = false))

        assertTrue(handler.handle(keyInput(ACTION_DOWN), DEVICE_NAME))

        assertEquals(2, recorded.size)
        assertEquals(Decision.CONSUMED, recorded[0].decision)
        assertEquals(Decision.ACTION_FAILED, recorded[1].decision)
        assertEquals(recorded[0].copy(decision = Decision.ACTION_FAILED), recorded[1])
    }

    @Test
    fun `selected target with loaded config is marked as target`() {
        val engine = armedEngine()

        assertTrue(handlerFor(engine).handle(keyInput(ACTION_DOWN), DEVICE_NAME))

        assertTrue(recorded.single().isTarget)
    }

    @Test
    fun `empty config is not marked as target`() {
        val engine = armedEngine(RuntimeConfig.EMPTY)

        assertFalse(handlerFor(engine).handle(keyInput(ACTION_DOWN), DEVICE_NAME))

        assertFalse(recorded.single().isTarget)
    }

    private fun handlerFor(
        engine: RemapEngine,
        dispatcher: ActionDispatcher = FakeDispatcher(),
        record: (KeyRecord) -> Unit = { recorded += it },
        decide: (KeyInput) -> Verdict = engine::onKey,
        disarm: () -> Unit = { engine.setArmed(false) },
        logError: (Throwable) -> Unit = { logged += it },
    ): KeyEventHandler = KeyEventHandler(
        engine = engine,
        dispatcher = dispatcher,
        record = record,
        now = { RECORD_TIME },
        decide = decide,
        disarm = disarm,
        logError = logError,
    )

    private fun decisions(): List<Decision> = recorded.map { it.decision }

    private fun armedEngine(config: RuntimeConfig = runtimeConfig()): RemapEngine =
        RemapEngine().apply {
            this.config = config
            setArmed(true)
        }

    private fun runtimeConfig(
        generation: Long = 1L,
        targets: Set<String> = setOf(DEVICE),
    ): RuntimeConfig = RuntimeConfig(
        generation = generation,
        targets = targets,
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

    private fun keyInput(
        action: Int,
        eventTime: Long = 1_000L,
    ): KeyInput = KeyInput(
        keyCode = KEY_C,
        action = action,
        repeatCount = 0,
        metaState = META_CTRL_ON,
        deviceDescriptor = DEVICE,
        deviceId = 1,
        scanCode = 0,
        eventTime = eventTime,
    )

    private class FakeDispatcher(
        private val result: Boolean = true,
        private val onDispatch: (ActionCode) -> Unit = {},
    ) : ActionDispatcher {

        val actions = mutableListOf<ActionCode>()

        override fun dispatch(action: ActionCode): Boolean {
            actions += action
            onDispatch(action)
            return result
        }
    }

    private companion object {
        const val DEVICE = "test-device"
        const val DEVICE_NAME = "Test Mouse"
        const val PROFILE = "default"
        const val KEY_C = 31
        const val META_CTRL_ON = 0x1000
        const val RECORD_TIME = 10_000L
    }
}
