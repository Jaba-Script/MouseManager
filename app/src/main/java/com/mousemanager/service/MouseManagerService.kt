package com.mousemanager.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.mousemanager.BuildConfig
import com.mousemanager.MouseManagerApp
import com.mousemanager.core.KeyInput
import com.mousemanager.core.PassReason
import com.mousemanager.core.RemapEngine
import com.mousemanager.core.RuntimeConfig
import com.mousemanager.core.Verdict
import com.mousemanager.diagnostics.Decision
import com.mousemanager.diagnostics.KeyRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Служба доступности M1 (§11): только диагностика.
 *
 * Она запрашивает [AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS], чтобы
 * получать `onKeyEvent`, и **никогда** не возвращает `true`: ничего не поглощается и не
 * переназначается. Решение всегда принимает [RemapEngine], который в M1 возвращает
 * `Pass(DISARMED)`.
 *
 * Конфигурация приходит асинхронно из [com.mousemanager.data.ConfigStore]. Начальный
 * [RuntimeConfig.EMPTY] (generation = 0) — это ещё не пользовательский конфиг, поэтому
 * до первой успешной публикации `isTarget` всегда `false`.
 *
 * `armed` в M1 — константа `false` (D-006): мастер-переключателя ещё нет.
 */
class MouseManagerService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Заглушка M1: всегда `Pass(DISARMED)`; состояние не нужно. */
    private val engine = RemapEngine()

    @Volatile
    private var config: RuntimeConfig = RuntimeConfig.EMPTY

    private var configJob: Job? = null

    private val app: MouseManagerApp
        get() = application as MouseManagerApp

    override fun onServiceConnected() {
        super.onServiceConnected()

        // D-006: каждое подключение начинается с выключенного перехвата. В M1
        // переключать нечего — RemapEngine не имеет armed.
        val appliedFlags = applyKeyFilterFlag()
        ServiceState.onConnected(appliedFlags)
        observeConfig()

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "onServiceConnected flags=0x${Integer.toHexString(appliedFlags)} " +
                    "filterApplied=${ServiceState.status.value.filterApplied}",
            )
        }
    }

    /**
     * M1: журналируем событие и **всегда** возвращаем `false`. Даже если в будущем
     * вердикт станет `Consume`, служба этой вехи не имеет права поглощать ввод.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean = try {
        val input = KeyEventMapper.toKeyInput(event)
        val verdict = engine.onKey(input)
        record(event, input, verdict)
        false
    } catch (t: Throwable) {
        // Fail-safe D-018: ошибка разбора/движка не должна блокировать клавишу.
        Log.e(TAG, "onKeyEvent failed; event passed through", t)
        false
    }

    /** Foreground-пакет понадобится для профилей приложений в M2; в M1 не нужен. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        configJob?.cancel()
        configJob = null
        ServiceState.onStopped()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        configJob?.cancel()
        scope.cancel()
        ServiceState.onStopped()
        super.onDestroy()
    }

    /** Запрашивает флаг и возвращает то, что система реально применила (S2: 0x20). */
    private fun applyKeyFilterFlag(): Int {
        val info = serviceInfo ?: return 0
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
        return serviceInfo?.flags ?: 0
    }

    /**
     * Подписка идемпотентна: повторный `onServiceConnected` не создаёт второй сборщик.
     * `ConfigStore.runtime` — горячий `StateFlow`, поэтому подписка сразу отдаёт текущий
     * снимок, включая начальный `RuntimeConfig.EMPTY`.
     */
    private fun observeConfig() {
        if (configJob?.isActive == true) return
        configJob = scope.launch {
            app.configStore.runtime.collect { runtime ->
                config = runtime
                if (BuildConfig.DEBUG && runtime.generation > 0) {
                    Log.d(
                        TAG,
                        "config generation=${runtime.generation} targets=${runtime.targets.size}",
                    )
                }
            }
        }
    }

    private fun record(event: KeyEvent, input: KeyInput, verdict: Verdict) {
        val runtime = config
        // generation == 0 — начальный EMPTY или ошибка загрузки, а не конфиг
        // пользователя. Такие события не считаем «целевыми».
        val configLoaded = runtime.generation > 0
        val descriptor = input.deviceDescriptor

        app.diagnosticsLog.record(
            KeyRecord(
                timestamp = System.currentTimeMillis(),
                deviceId = input.deviceId,
                deviceName = event.device?.name ?: UNKNOWN_DEVICE,
                descriptor = descriptor,
                keyCode = input.keyCode,
                scanCode = input.scanCode,
                metaState = input.metaState,
                repeatCount = input.repeatCount,
                action = input.action,
                decision = decisionFor(verdict),
                isTarget = configLoaded && descriptor != null && descriptor in runtime.targets,
                armed = false,
            ),
        )
    }

    /**
     * В M1 движок возвращает только `Pass(DISARMED)`. Остальные ветки нужны, чтобы
     * журнал не потерял событие, если заглушку случайно заменят раньше M2.
     */
    private fun decisionFor(verdict: Verdict): Decision = when (verdict) {
        Verdict.Pass(PassReason.DISARMED) -> Decision.PASSED_DISARMED
        is Verdict.Pass -> Decision.PASSED_ERROR
        Verdict.Consume -> Decision.CONSUMED
        is Verdict.ConsumeAndDispatch -> Decision.CONSUMED
    }

    private companion object {
        const val TAG = "MouseManager"
        const val UNKNOWN_DEVICE = "unknown"
    }
}
