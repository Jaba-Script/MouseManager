package com.mousemanager.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Контракт [RemapEngine] по §6: строгий порядок проверок, pending по D-026 и fail-safe
 * поведение D-018. Все сценарии — JVM, без Android и без службы.
 */
class RemapEngineTest {

    // --- armed и descriptor ---

    @Test
    fun `disarmed engine passes a known chord`() {
        val engine = engine(armed = false)

        assertEquals(Verdict.Pass(PassReason.DISARMED), engine.onKey(input()))
    }

    @Test
    fun `null descriptor is reported even when disarmed`() {
        val engine = engine(armed = false)

        assertEquals(
            Verdict.Pass(PassReason.NO_DESCRIPTOR),
            engine.onKey(input(deviceDescriptor = null)),
        )
    }

    @Test
    fun `null descriptor is reported when armed`() {
        val engine = engine()

        assertEquals(
            Verdict.Pass(PassReason.NO_DESCRIPTOR),
            engine.onKey(input(deviceDescriptor = null)),
        )
    }

    @Test
    fun `paired up is consumed while disarmed`() {
        val engine = engine()
        engine.onKey(input(eventTime = 1_000L))
        engine.setArmed(false)

        assertEquals(Verdict.Consume, engine.onKey(input(action = ACTION_UP, eventTime = 1_100L)))
    }

    @Test
    fun `repeat is consumed while disarmed`() {
        val engine = engine()
        engine.onKey(input(eventTime = 1_000L))
        engine.setArmed(false)

        assertEquals(
            Verdict.Consume,
            engine.onKey(input(repeatCount = 2, eventTime = 1_100L)),
        )
    }

    @Test
    fun `second down is consumed while disarmed`() {
        val engine = engine()
        engine.onKey(input(eventTime = 1_000L))
        engine.setArmed(false)

        assertEquals(Verdict.Consume, engine.onKey(input(eventTime = 1_100L)))
    }

    // --- targets ---

    @Test
    fun `empty targets pass as empty targets`() {
        val engine = engine(config = singleConfig(CTRL_C to ActionCode.BACK).copy(targets = emptySet()))

        assertEquals(Verdict.Pass(PassReason.EMPTY_TARGETS), engine.onKey(input()))
    }

    @Test
    fun `foreign device passes as foreign device`() {
        val engine = engine()

        assertEquals(
            Verdict.Pass(PassReason.FOREIGN_DEVICE),
            engine.onKey(input(deviceDescriptor = OTHER_MOUSE)),
        )
    }

    @Test
    fun `modifier of a foreign device is a foreign device not a modifier`() {
        val engine = engine()

        assertEquals(
            Verdict.Pass(PassReason.FOREIGN_DEVICE),
            engine.onKey(
                input(
                    keyCode = KEYCODE_CTRL_LEFT,
                    metaState = 0,
                    deviceDescriptor = OTHER_MOUSE,
                ),
            ),
        )
    }

    // --- модификаторы ---

    @Test
    fun `modifier down is passed as modifier`() {
        val engine = engine()

        assertEquals(
            Verdict.Pass(PassReason.MODIFIER),
            engine.onKey(input(keyCode = KEYCODE_CTRL_LEFT)),
        )
    }

    @Test
    fun `modifier up is passed as modifier`() {
        val engine = engine()
        engine.onKey(input(keyCode = KEYCODE_CTRL_LEFT))

        assertEquals(
            Verdict.Pass(PassReason.MODIFIER),
            engine.onKey(input(keyCode = KEYCODE_CTRL_LEFT, action = ACTION_UP)),
        )
    }

    @Test
    fun `repeated modifier down is idempotent`() {
        val engine = engine()
        engine.onKey(input(keyCode = KEYCODE_CTRL_LEFT, metaState = 0, eventTime = 1_000L))
        engine.onKey(input(keyCode = KEYCODE_CTRL_LEFT, metaState = 0, eventTime = 1_050L))

        assertEquals(
            Verdict.ConsumeAndDispatch(ActionCode.BACK),
            engine.onKey(input(metaState = 0, eventTime = 1_100L)),
        )
    }

    @Test
    fun `tracker supplies modifiers missing from meta state`() {
        val engine = engine()
        engine.onKey(input(keyCode = KEYCODE_CTRL_LEFT, metaState = 0, eventTime = 1_000L))

        assertEquals(
            Verdict.ConsumeAndDispatch(ActionCode.BACK),
            engine.onKey(input(metaState = 0, eventTime = 1_100L)),
        )
    }

    // --- основной путь ---

    @Test
    fun `mapped chord down is consumed and dispatched`() {
        val engine = engine()

        assertEquals(
            Verdict.ConsumeAndDispatch(ActionCode.BACK),
            engine.onKey(input()),
        )
    }

    @Test
    fun `paired up after consumed down is consumed`() {
        val engine = engine()
        engine.onKey(input(eventTime = 1_000L))

        assertEquals(Verdict.Consume, engine.onKey(input(action = ACTION_UP, eventTime = 1_100L)))
    }

    @Test
    fun `up without pending down is passed as key up`() {
        val engine = engine()

        assertEquals(Verdict.Pass(PassReason.KEY_UP), engine.onKey(input(action = ACTION_UP)))
    }

    @Test
    fun `down without ctrl is not mapped`() {
        val engine = engine()

        assertEquals(
            Verdict.Pass(PassReason.NO_MAPPING),
            engine.onKey(input(metaState = 0)),
        )
    }

    @Test
    fun `extra modifier breaks the chord`() {
        val engine = engine()

        assertEquals(
            Verdict.Pass(PassReason.NO_MAPPING),
            engine.onKey(input(metaState = META_CTRL_LEFT_ON or META_SHIFT_ON)),
        )
    }

    @Test
    fun `action multiple is passed as key up`() {
        val engine = engine()

        assertEquals(
            Verdict.Pass(PassReason.KEY_UP),
            engine.onKey(input(action = ACTION_MULTIPLE)),
        )
    }

    @Test
    fun `none mapping is consumed and dispatched without an action`() {
        val engine = engine(config = singleConfig(CTRL_C to ActionCode.NONE))

        assertEquals(
            Verdict.ConsumeAndDispatch(ActionCode.NONE),
            engine.onKey(input()),
        )
    }

    @Test
    fun `any device mapping is used for a target device`() {
        val engine = engine(
            config = RuntimeConfig(
                generation = 1L,
                targets = setOf(MOUSE),
                profiles = mapOf(ACTIVE to profile(ACTIVE, CTRL_C to ActionCode.HOME, device = ANY_DEVICE)),
                activeProfileId = ACTIVE,
                defaultProfileId = ACTIVE,
                appBindings = emptyMap(),
            ),
        )

        assertEquals(
            Verdict.ConsumeAndDispatch(ActionCode.HOME),
            engine.onKey(input()),
        )
    }

    // --- pending, repeat, timeout ---

    @Test
    fun `repeat with pending is consumed once`() {
        val engine = engine()
        assertEquals(Verdict.ConsumeAndDispatch(ActionCode.BACK), engine.onKey(input(eventTime = 1_000L)))
        assertEquals(Verdict.Consume, engine.onKey(input(repeatCount = 1, eventTime = 1_100L)))
        assertEquals(Verdict.Consume, engine.onKey(input(action = ACTION_UP, eventTime = 1_200L)))
    }

    @Test
    fun `second down with pending is consumed without a second dispatch`() {
        val engine = engine()
        assertEquals(Verdict.ConsumeAndDispatch(ActionCode.BACK), engine.onKey(input(eventTime = 1_000L)))

        assertEquals(Verdict.Consume, engine.onKey(input(eventTime = 1_050L)))
    }

    @Test
    fun `repeat without pending but with mapping is repeat of unknown`() {
        val engine = engine()

        assertEquals(
            Verdict.Pass(PassReason.REPEAT_OF_UNKNOWN),
            engine.onKey(input(repeatCount = 1)),
        )
    }

    @Test
    fun `repeat without pending and without mapping is no mapping`() {
        val engine = engine(config = singleConfig(CTRL_V to ActionCode.HOME))

        assertEquals(
            Verdict.Pass(PassReason.NO_MAPPING),
            engine.onKey(input(repeatCount = 1)),
        )
    }

    @Test
    fun `repeats slide the pending window`() {
        val engine = engine()
        assertEquals(Verdict.ConsumeAndDispatch(ActionCode.BACK), engine.onKey(input(eventTime = 1_000L)))
        assertEquals(Verdict.Consume, engine.onKey(input(repeatCount = 1, eventTime = 2_400L)))
        assertEquals(Verdict.Consume, engine.onKey(input(repeatCount = 2, eventTime = 3_700L)))

        assertEquals(Verdict.Consume, engine.onKey(input(action = ACTION_UP, eventTime = 3_800L)))
    }

    @Test
    fun `pending survives exactly the timeout`() {
        val engine = engine()
        engine.onKey(input(eventTime = 1_000L))

        assertEquals(
            Verdict.Consume,
            engine.onKey(input(action = ACTION_UP, eventTime = 1_000L + PENDING_TIMEOUT_MS)),
        )
    }

    @Test
    fun `pending expires one millisecond after the timeout`() {
        val engine = engine()
        engine.onKey(input(eventTime = 1_000L))

        assertEquals(
            Verdict.Pass(PassReason.KEY_UP),
            engine.onKey(input(action = ACTION_UP, eventTime = 1_000L + PENDING_TIMEOUT_MS + 1)),
        )
        assertEquals(
            Verdict.Pass(PassReason.KEY_UP),
            engine.onKey(input(action = ACTION_UP, eventTime = 2_600L)),
        )
    }

    @Test
    fun `two key codes on one device keep independent pendings`() {
        val engine = engine(config = singleConfig(CTRL_C to ActionCode.BACK, CTRL_V to ActionCode.HOME))

        assertEquals(Verdict.ConsumeAndDispatch(ActionCode.BACK), engine.onKey(input(eventTime = 1_000L)))
        assertEquals(
            Verdict.ConsumeAndDispatch(ActionCode.HOME),
            engine.onKey(input(keyCode = KEYCODE_V, eventTime = 1_100L)),
        )
        assertEquals(Verdict.Consume, engine.onKey(input(action = ACTION_UP, eventTime = 1_200L)))
        assertEquals(
            Verdict.Consume,
            engine.onKey(input(keyCode = KEYCODE_V, action = ACTION_UP, eventTime = 1_300L)),
        )
    }

    @Test
    fun `one key code on two devices keeps independent pendings`() {
        val engine = engine(config = singleConfig(CTRL_C to ActionCode.BACK).copy(targets = setOf(MOUSE, OTHER_MOUSE)))
        engine.onKey(input(eventTime = 1_000L))

        assertEquals(
            Verdict.Pass(PassReason.KEY_UP),
            engine.onKey(input(action = ACTION_UP, deviceDescriptor = OTHER_MOUSE, eventTime = 1_100L)),
        )
        assertEquals(Verdict.Consume, engine.onKey(input(action = ACTION_UP, eventTime = 1_200L)))
    }

    // --- смена armed, targets и конфига между DOWN и UP ---

    @Test
    fun `up is consumed after disarm between down and up`() {
        val engine = engine()
        engine.onKey(input(eventTime = 1_000L))
        engine.setArmed(false)

        assertEquals(Verdict.Consume, engine.onKey(input(action = ACTION_UP, eventTime = 1_100L)))
    }

    @Test
    fun `up is consumed after removing the device from targets`() {
        val engine = engine()
        engine.onKey(input(eventTime = 1_000L))
        engine.config = singleConfig(CTRL_C to ActionCode.BACK).copy(targets = emptySet())

        assertEquals(Verdict.Consume, engine.onKey(input(action = ACTION_UP, eventTime = 1_100L)))
    }

    @Test
    fun `up is consumed after the mapping disappears`() {
        val engine = engine()
        engine.onKey(input(eventTime = 1_000L))
        engine.config = singleConfig(CTRL_V to ActionCode.HOME)

        assertEquals(Verdict.Consume, engine.onKey(input(action = ACTION_UP, eventTime = 1_100L)))
        assertEquals(Verdict.Pass(PassReason.NO_MAPPING), engine.onKey(input(eventTime = 1_200L)))
    }

    @Test
    fun `new chord works after the old pending expires`() {
        val engine = engine()
        assertEquals(Verdict.ConsumeAndDispatch(ActionCode.BACK), engine.onKey(input(eventTime = 1_000L)))

        assertEquals(
            Verdict.ConsumeAndDispatch(ActionCode.BACK),
            engine.onKey(input(eventTime = 1_000L + PENDING_TIMEOUT_MS + 1)),
        )
        assertEquals(Verdict.Consume, engine.onKey(input(action = ACTION_UP, eventTime = 2_600L)))
    }

    @Test
    fun `reset state drops pending`() {
        val engine = engine()
        engine.onKey(input(eventTime = 1_000L))

        engine.resetState()

        assertEquals(Verdict.Pass(PassReason.KEY_UP), engine.onKey(input(action = ACTION_UP, eventTime = 1_100L)))
    }

    @Test
    fun `reset state drops held modifiers`() {
        val engine = engine()
        engine.onKey(input(keyCode = KEYCODE_CTRL_LEFT, metaState = 0, eventTime = 1_000L))

        engine.resetState()

        assertEquals(
            Verdict.Pass(PassReason.NO_MAPPING),
            engine.onKey(input(metaState = 0, eventTime = 1_100L)),
        )
    }

    // --- профили и конфиг ---

    @Test
    fun `foreground package selects the bound profile`() {
        val engine = engine(
            config = RuntimeConfig(
                generation = 1L,
                targets = setOf(MOUSE),
                profiles = mapOf(
                    ACTIVE to profile(ACTIVE, CTRL_C to ActionCode.BACK),
                    WORK to profile(WORK, CTRL_C to ActionCode.RECENTS),
                ),
                activeProfileId = ACTIVE,
                defaultProfileId = ACTIVE,
                appBindings = mapOf(CHROME to WORK),
            ),
            foregroundPackage = CHROME,
        )

        assertEquals(
            Verdict.ConsumeAndDispatch(ActionCode.RECENTS),
            engine.onKey(input(eventTime = 1_000L)),
        )
        engine.onKey(input(action = ACTION_UP, eventTime = 1_100L))

        engine.foregroundPackage = null
        assertEquals(
            Verdict.ConsumeAndDispatch(ActionCode.BACK),
            engine.onKey(input(eventTime = 1_200L)),
        )
    }

    @Test
    fun `config can be replaced after construction`() {
        val engine = engine(config = RuntimeConfig.EMPTY)

        assertEquals(Verdict.Pass(PassReason.EMPTY_TARGETS), engine.onKey(input(eventTime = 1_000L)))

        engine.config = singleConfig(CTRL_C to ActionCode.BACK)
        assertEquals(
            Verdict.ConsumeAndDispatch(ActionCode.BACK),
            engine.onKey(input(eventTime = 1_100L)),
        )
    }

    // --- helpers ---

    private fun input(
        keyCode: Int = KEYCODE_C,
        action: Int = ACTION_DOWN,
        repeatCount: Int = 0,
        metaState: Int = META_CTRL_LEFT_ON,
        deviceDescriptor: String? = MOUSE,
        deviceId: Int = 29,
        scanCode: Int = 46,
        eventTime: Long = 1_000L,
    ) = KeyInput(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        metaState = metaState,
        deviceDescriptor = deviceDescriptor,
        deviceId = deviceId,
        scanCode = scanCode,
        eventTime = eventTime,
    )

    private fun engine(
        config: RuntimeConfig = singleConfig(CTRL_C to ActionCode.BACK),
        armed: Boolean = true,
        foregroundPackage: String? = null,
    ) = RemapEngine().apply {
        this.config = config
        setArmed(armed)
        this.foregroundPackage = foregroundPackage
    }

    private fun singleConfig(vararg mappings: Pair<ChordKey, ActionCode>): RuntimeConfig =
        RuntimeConfig(
            generation = 1L,
            targets = setOf(MOUSE),
            profiles = mapOf(ACTIVE to profile(ACTIVE, *mappings)),
            activeProfileId = ACTIVE,
            defaultProfileId = ACTIVE,
            appBindings = emptyMap(),
        )

    private fun profile(
        id: String,
        vararg mappings: Pair<ChordKey, ActionCode>,
        device: String = MOUSE,
    ) = ProfileRuntime(
        id = id,
        name = id,
        byDevice = mapOf(device to mappings.toMap()),
    )

    private companion object {
        // Значения android.view.KeyEvent продублированы числами: core не зависит от Android.
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MULTIPLE = 2

        const val KEYCODE_C = 31
        const val KEYCODE_V = 50
        const val KEYCODE_CTRL_LEFT = 113

        // META_CTRL_ON | META_CTRL_LEFT_ON (измерено в S2) и META_SHIFT_ON.
        const val META_CTRL_LEFT_ON = 0x3000
        const val META_SHIFT_ON = 0x1

        const val ACTIVE = "active"
        const val WORK = "work"
        const val CHROME = "com.android.chrome"
        const val MOUSE = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
        const val OTHER_MOUSE = "ce5035b0e1a2b3c4d5e6f708192a3b4c5d6e7f80"

        val CTRL_C = ChordKey(MOD_CTRL, KEYCODE_C)
        val CTRL_V = ChordKey(MOD_CTRL, KEYCODE_V)
    }
}
