package com.mousemanager.service

import android.accessibilityservice.AccessibilityService
import com.mousemanager.core.ActionCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контракт диспетчера (D-010, D-027, D-028): полный маппинг девяти [ActionCode],
 * успешный no-op для `NONE`, отказ без повторов и rate limit сообщения 2000 мс.
 *
 * Robolectric не нужен: `GLOBAL_ACTION_*` — `static final int`, компилятор встраивает
 * их значения; Android-обвязка заменена лямбдами.
 */
class GlobalActionDispatcherTest {

    @Test
    fun `every action maps to its global action`() {
        val expected = mapOf(
            ActionCode.BACK to AccessibilityService.GLOBAL_ACTION_BACK,
            ActionCode.HOME to AccessibilityService.GLOBAL_ACTION_HOME,
            ActionCode.RECENTS to AccessibilityService.GLOBAL_ACTION_RECENTS,
            ActionCode.NOTIFICATIONS to AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
            ActionCode.QUICK_SETTINGS to AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
            ActionCode.POWER_DIALOG to AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
            ActionCode.LOCK_SCREEN to AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
            ActionCode.SCREENSHOT to AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,
        )

        assertEquals(8, expected.size)
        expected.forEach { (action, globalAction) ->
            assertEquals("действие $action", globalAction, globalActionFor(action))
        }
    }

    @Test
    fun `screenshot maps to take screenshot`() {
        assertEquals(
            AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,
            globalActionFor(ActionCode.SCREENSHOT),
        )
    }

    @Test
    fun `all global action ids are distinct`() {
        val ids = ActionCode.entries.mapNotNull { globalActionFor(it) }

        assertEquals(8, ids.size)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `none has no global action`() {
        assertNull(globalActionFor(ActionCode.NONE))
        assertEquals(
            listOf(ActionCode.NONE),
            ActionCode.entries.filter { globalActionFor(it) == null },
        )
    }

    @Test
    fun `successful dispatch performs mapped action once`() {
        val fixture = Fixture()

        assertTrue(fixture.dispatcher.dispatch(ActionCode.BACK))
        assertEquals(1, fixture.performCalls)
        assertEquals(AccessibilityService.GLOBAL_ACTION_BACK, fixture.lastAction)
        assertEquals(0, fixture.notifyCalls)
    }

    @Test
    fun `none succeeds without performing or notifying`() {
        val fixture = Fixture()

        assertTrue(fixture.dispatcher.dispatch(ActionCode.NONE))
        assertEquals(0, fixture.performCalls)
        assertEquals(0, fixture.notifyCalls)
        assertNull(fixture.lastAction)
    }

    @Test
    fun `failed action returns false without retry`() {
        val fixture = Fixture().apply { performResult = false }

        assertFalse(fixture.dispatcher.dispatch(ActionCode.HOME))
        assertEquals(1, fixture.performCalls)
        assertEquals(1, fixture.notifyCalls)
    }

    @Test
    fun `exception from perform returns false without retry`() {
        val failure = IllegalStateException("perform failed")
        val fixture = Fixture().apply { performThrows = failure }

        assertFalse(fixture.dispatcher.dispatch(ActionCode.SCREENSHOT))
        assertEquals(1, fixture.performCalls)
        assertEquals(1, fixture.notifyCalls)
        assertEquals(listOf(failure), fixture.logged)
    }

    @Test
    fun `first failure notifies even at zero time`() {
        val fixture = Fixture().apply {
            performResult = false
            now = 0L
        }

        assertFalse(fixture.dispatcher.dispatch(ActionCode.BACK))
        assertEquals(1, fixture.notifyCalls)
    }

    @Test
    fun `failure within two seconds notifies once`() {
        val fixture = Fixture().apply { performResult = false }

        assertFalse(fixture.dispatcher.dispatch(ActionCode.BACK))
        fixture.now = 1_999L
        assertFalse(fixture.dispatcher.dispatch(ActionCode.HOME))

        assertEquals(1, fixture.notifyCalls)
    }

    @Test
    fun `failure at two seconds notifies again`() {
        val fixture = Fixture().apply { performResult = false }

        assertFalse(fixture.dispatcher.dispatch(ActionCode.BACK))
        fixture.now = 2_000L
        assertFalse(fixture.dispatcher.dispatch(ActionCode.HOME))

        assertEquals(2, fixture.notifyCalls)
    }

    @Test
    fun `success does not reset failure rate limit`() {
        val fixture = Fixture().apply { performResult = false }

        assertFalse(fixture.dispatcher.dispatch(ActionCode.BACK))

        fixture.performResult = true
        fixture.now = 1_000L
        assertTrue(fixture.dispatcher.dispatch(ActionCode.RECENTS))

        fixture.performResult = false
        fixture.now = 1_500L
        assertFalse(fixture.dispatcher.dispatch(ActionCode.HOME))

        assertEquals(1, fixture.notifyCalls)
    }

    @Test
    fun `notification exception does not escape and is logged`() {
        val notifyFailure = IllegalStateException("toast failed")
        val fixture = Fixture().apply {
            performResult = false
            notifyThrows = notifyFailure
        }

        assertFalse(fixture.dispatcher.dispatch(ActionCode.BACK))
        assertEquals(1, fixture.notifyCalls)
        assertEquals(listOf(notifyFailure), fixture.logged)
    }

    @Test
    fun `notification timestamp is set before the call`() {
        val fixture = Fixture().apply {
            performResult = false
            notifyThrows = IllegalStateException("toast failed")
        }

        assertFalse(fixture.dispatcher.dispatch(ActionCode.BACK))

        fixture.now = 100L
        assertFalse(fixture.dispatcher.dispatch(ActionCode.HOME))

        assertEquals(1, fixture.notifyCalls)
    }

    @Test
    fun `log failure exception does not escape`() {
        val fixture = Fixture().apply {
            performThrows = IllegalStateException("perform failed")
            logThrows = IllegalStateException("log failed")
        }

        assertFalse(fixture.dispatcher.dispatch(ActionCode.BACK))
        assertEquals(1, fixture.performCalls)
        assertEquals(1, fixture.notifyCalls)
    }

    @Test
    fun `log exception during notification does not escape`() {
        val fixture = Fixture().apply {
            performResult = false
            notifyThrows = IllegalStateException("toast failed")
            logThrows = IllegalStateException("log failed")
        }

        assertFalse(fixture.dispatcher.dispatch(ActionCode.BACK))
        assertEquals(1, fixture.notifyCalls)
    }
}

/**
 * Заменяет Android-обвязку: `perform` считает вызовы, `now` управляется тестом,
 * `notifyFailure` и `logFailure` можно заставить бросить исключение.
 */
private class Fixture {

    var now: Long = 0L
    var performCalls: Int = 0
    var lastAction: Int? = null
    var notifyCalls: Int = 0
    val logged: MutableList<Throwable> = mutableListOf()

    var performResult: Boolean = true
    var performThrows: Exception? = null
    var notifyThrows: Exception? = null
    var logThrows: Exception? = null

    val dispatcher = GlobalActionDispatcher(
        perform = { action ->
            performCalls++
            lastAction = action
            performThrows?.let { throw it }
            performResult
        },
        notifyFailure = {
            notifyCalls++
            notifyThrows?.let { throw it }
        },
        now = { now },
        logFailure = { error ->
            logged += error
            logThrows?.let { throw it }
        },
    )
}
