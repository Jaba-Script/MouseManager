package com.mousemanager.service

import com.mousemanager.core.ActionDispatcher
import com.mousemanager.core.KeyInput
import com.mousemanager.core.RemapEngine
import com.mousemanager.core.RuntimeConfig
import com.mousemanager.core.Verdict
import com.mousemanager.diagnostics.Decision
import com.mousemanager.diagnostics.KeyRecord
import com.mousemanager.diagnostics.decisionFor

/**
 * Обработка одного события горячего пути (§6, M2.3.4): решение, диагностика и
 * выполнение действия.
 *
 * Класс не зависит от Android: `android.view.KeyEvent` превращает в [KeyInput]
 * служба, а запись журнала, время и логгер внедряются лямбдами. Поэтому порядок
 * `CONSUMED → dispatch → ACTION_FAILED`, ошибки диагностики и ошибки диспетчера
 * проверяются обычным JUnit без Robolectric.
 *
 * **Порядок решения (D-027).** `armed` и `config` читаются до вызова [decide]:
 * запись журнала показывает состояние, в котором движок принял решение.
 *
 * **Диагностика вторична.** Любая ошибка `record` или `logError` перехватывается и
 * не меняет результат: поглощённое событие остаётся поглощённым, а пропущенное —
 * пропущенным. `CONSUMED` записывается до dispatch, поэтому отказ действия не
 * отменяет факт поглощения; `ACTION_FAILED` добавляется второй записью с теми же
 * полями события (D-027 п. 27.3).
 *
 * **Действие.** [ActionDispatcher.dispatch] вызывается только для
 * [Verdict.ConsumeAndDispatch] и только на главном потоке (§13); сам диспетчер
 * показывает сообщение об отказе, служба второго Toast не делает.
 */
class KeyEventHandler(
    private val engine: RemapEngine,
    private val dispatcher: ActionDispatcher,
    private val record: (KeyRecord) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val decide: (KeyInput) -> Verdict = engine::onKey,
    private val logError: (Throwable) -> Unit = {},
) {

    /**
     * Обрабатывает [input] и возвращает то, что служба должна вернуть из
     * `onKeyEvent`: `true` — событие поглощено, `false` — пропущено.
     *
     * @param deviceName имя устройства для журнала; в горячем пути не участвует.
     */
    fun handle(input: KeyInput, deviceName: String): Boolean {
        val armedAtDecision = engine.armed
        val runtime = engine.config
        val timestamp = timestampSafely()
        val base = recordFor(input, deviceName, runtime, armedAtDecision, timestamp)
        val verdict = try {
            decide(input)
        } catch (t: Throwable) {
            disarmSafely()
            recordSafely(base.copy(decision = Decision.PASSED_ERROR))
            logSafely(t)
            return false
        }

        return when (verdict) {
            is Verdict.Pass -> {
                recordSafely(base.copy(decision = decisionFor(verdict)))
                false
            }

            Verdict.Consume -> {
                recordSafely(base.copy(decision = Decision.CONSUMED))
                true
            }

            is Verdict.ConsumeAndDispatch -> {
                val consumed = base.copy(decision = Decision.CONSUMED)
                recordSafely(consumed)

                val dispatched = try {
                    dispatcher.dispatch(verdict.action)
                } catch (t: Throwable) {
                    logSafely(t)
                    false
                }

                if (!dispatched) {
                    recordSafely(consumed.copy(decision = Decision.ACTION_FAILED))
                }
                true
            }
        }
    }

    private fun recordFor(
        input: KeyInput,
        deviceName: String,
        runtime: RuntimeConfig,
        armedAtDecision: Boolean,
        timestamp: Long,
    ): KeyRecord {
        val descriptor = input.deviceDescriptor
        return KeyRecord(
            timestamp = timestamp,
            deviceId = input.deviceId,
            deviceName = deviceName,
            descriptor = descriptor,
            keyCode = input.keyCode,
            scanCode = input.scanCode,
            metaState = input.metaState,
            repeatCount = input.repeatCount,
            action = input.action,
            decision = Decision.CONSUMED,
            isTarget = runtime.generation > 0 &&
                descriptor != null &&
                descriptor in runtime.targets,
            armed = armedAtDecision,
        )
    }

    /** Время тоже относится к диагностике: его сбой не должен менять результат. */
    private fun timestampSafely(): Long = try {
        now()
    } catch (t: Throwable) {
        logSafely(t)
        0L
    }

    private fun recordSafely(entry: KeyRecord) {
        try {
            record(entry)
        } catch (t: Throwable) {
            logSafely(t)
        }
    }

    private fun disarmSafely() {
        try {
            engine.setArmed(false)
        } catch (t: Throwable) {
            logSafely(t)
        }
    }

    private fun logSafely(error: Throwable) {
        try {
            logError(error)
        } catch (ignored: Throwable) {
            // Диагностика не должна ломать горячий путь.
        }
    }
}
