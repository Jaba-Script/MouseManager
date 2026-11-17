package com.mousemanager.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.mousemanager.BuildConfig
import com.mousemanager.MouseManagerApp
import com.mousemanager.R
import com.mousemanager.core.RemapEngine
import com.mousemanager.core.RuntimeConfig
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
 * Служба доступности M2.3.4 (§11): перехват клавиш, решение [RemapEngine],
 * диагностика и выполнение действия.
 *
 * Служба остаётся тонкой: разбор `KeyEvent` и lifecycle — здесь, обработка одного
 * события — в [KeyEventHandler]. Сам движок ничего не знает про Android.
 *
 * **`armed`.** Служба никогда не включает перехват сама: каждое подключение и
 * завершение сессии начинается с `armed = false` (D-006). Включение возможно только
 * через [ServiceBridge], который проверяет гейт и синхронно обновляет зеркало
 * [ServiceState] (D-031). Пока мастер-переключатель не подключён к UI, `armed` всегда
 * `false`, и поведение продукта совпадает с M1.
 *
 * **Конфигурация.** Единственный источник — [RemapEngine.config]. Начальный
 * [RuntimeConfig.EMPTY] (`generation == 0`) — ещё не конфиг пользователя, поэтому при
 * нём планируется fail-safe disarm (D-030).
 *
 * **Сессия.** Конфиг применяется только текущей сессией наблюдения: `configSession`
 * увеличивается при старте и завершении сессии, а все записи `engine.config` и
 * планирование disarm выполняются на главном потоке с проверкой токена. Отменённый,
 * но ещё не завершившийся сборщик не может записать устаревший снимок; отложенный
 * `disarmIfEmpty` помнит токен своей сессии и не выключает перехват новой
 * ([DisarmGuard], D-031 п. 31.11).
 *
 * **Жизненный цикл.** `onServiceConnected` всегда начинает с `armed = false` через
 * мост, сбрасывает трекер, `config = EMPTY` и `foregroundPackage = null`, затем
 * публикует движок через [EngineHolder]. `onUnbind` снимает сессию (включая сброс
 * config, foreground и зеркала `armed`), но не отменяет общий scope; `onDestroy`
 * снимает сессию и отменяет scope.
 */
class MouseManagerService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Владелец движка — служба; [EngineHolder] хранит только ссылку (D-029). */
    private val engine = RemapEngine()

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Единственный путь изменения `armed` (D-031): синхронно переводит движок и
     * зеркало [ServiceState] в согласованное состояние. Собственного движка не
     * хранит и берёт опубликованный экземпляр через [EngineHolder].
     */
    private val bridge = ServiceBridge(
        logError = { Log.e(TAG, "service bridge failed", it) },
    )

    private var configJob: Job? = null

    /**
     * Токен текущей сессии наблюдения за конфигом. Меняется только на главном потоке.
     * Коллектор применяет снимок, лишь пока его токен совпадает с этим значением.
     */
    private var configSession = 0L

    /** Последняя generation, увиденная коллектором: нужна для немедленного disarm. */
    @Volatile
    private var observedGeneration: Long? = null

    private val app: MouseManagerApp
        get() = application as MouseManagerApp

    /**
     * Диспетчер создаётся лениво: `performGlobalAction` доступен только после
     * подключения службы. Единственный Toast об отказе показывает он (D-028).
     */
    private val dispatcher by lazy {
        GlobalActionDispatcher(
            perform = { globalAction -> performGlobalAction(globalAction) },
            notifyFailure = {
                Toast.makeText(this, R.string.action_failed_toast, Toast.LENGTH_SHORT).show()
            },
            logFailure = { Log.e(TAG, "global action failed", it) },
        )
    }

    private val eventHandler by lazy {
        KeyEventHandler(
            engine = engine,
            dispatcher = dispatcher,
            record = { entry -> app.diagnosticsLog.record(entry) },
            disarm = { bridge.setArmed(false) },
            logError = { Log.e(TAG, "key event handling failed", it) },
        )
    }

    /**
     * Отложенный fail-safe disarm для `generation == 0` (D-030). Callback помнит
     * токен сессии, в которой был запланирован: устаревший callback прошлой сессии
     * не должен выключать перехват новой (D-031 п. 31.11).
     */
    private var disarmSession = 0L

    private val disarmIfEmpty = Runnable {
        if (DisarmGuard.shouldDisarm(disarmSession, configSession, engine.config.generation)) {
            bridge.setArmed(false)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // D-006: каждое подключение начинается с выключенного перехвата.
        // Движок публикуется до disarm, чтобы мост выключил именно этот экземпляр
        // и синхронно обновил зеркало ServiceState (D-031).
        EngineHolder.attach(engine)
        bridge.setArmed(false)
        engine.resetState()
        // Новая сессия не наследует config/foreground предыдущей (D-030).
        engine.config = RuntimeConfig.EMPTY
        engine.foregroundPackage = null
        observedGeneration = null

        val appliedFlags = applyKeyFilterFlag()
        val filterApplied =
            (appliedFlags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0
        if (!filterApplied) {
            // Без фильтра перехват невозможен: движок остаётся выключенным (D-018).
            bridge.setArmed(false)
            Log.w(TAG, "key filter was not applied; remapping stays disarmed")
        }

        // Публикуем подключение только после attach и проверки фильтра.
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
     * M2.3.4: результат определяет [KeyEventHandler]. `KeyEvent` без descriptor
     * безопасно проходит, ошибка разбора не блокирует ввод (D-018).
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val input = try {
            KeyEventMapper.toKeyInput(event)
        } catch (t: Throwable) {
            Log.e(TAG, "key event mapping failed; event passed through", t)
            recordMappingFailure(event)
            return false
        }

        return try {
            eventHandler.handle(input, event.device?.name ?: UNKNOWN_DEVICE)
        } catch (t: Throwable) {
            // Fail-safe D-018: неожиданная ошибка не должна блокировать клавишу.
            Log.e(TAG, "onKeyEvent failed; event passed through", t)
            bridge.setArmed(false)
            false
        }
    }

    /**
     * Foreground-пакет нужен для привязок профилей (§8). `null`/пустой пакет не
     * затирает последнее известное значение.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        engine.foregroundPackage = packageName
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        stopSession()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopSession()
        scope.cancel()
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
     * Перезапускает подписку с новым токеном сессии: повторный `onServiceConnected`
     * не оставляет второй активный сборщик, а старый теряет право на запись.
     * `ConfigStore.runtime` — горячий `StateFlow`, поэтому подписка сразу отдаёт текущий
     * снимок, включая начальный `RuntimeConfig.EMPTY`.
     */
    private fun observeConfig() {
        configJob?.cancel()
        val session = ++configSession
        configJob = scope.launch {
            app.configStore.runtime.collect { runtime ->
                applyConfig(session, runtime)
            }
        }
    }

    /**
     * Запись `engine.config` и планирование disarm сериализованы на главном потоке:
     * `onServiceConnected`/`stopSession` тоже выполняются там, поэтому проверка токена
     * и изменение состояния атомарны относительно смены сессии.
     */
    private fun applyConfig(session: Long, runtime: RuntimeConfig) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyConfigNow(session, runtime)
        } else {
            mainHandler.post { applyConfigNow(session, runtime) }
        }
    }

    /** Применяет снимок, только если сессия всё ещё текущая. */
    private fun applyConfigNow(session: Long, runtime: RuntimeConfig) {
        if (session != configSession) return

        engine.config = runtime
        syncDisarm(runtime.generation)
        if (BuildConfig.DEBUG && runtime.generation > 0) {
            Log.d(
                TAG,
                "config generation=${runtime.generation} targets=${runtime.targets.size}",
            )
        }
    }

    /**
     * `generation == 0` — fail-safe состояние (D-018, D-030):
     * - начальный EMPTY получает отсрочку 1000 мс;
     * - переход `> 0 → 0` выключает перехват немедленно;
     * - в обоих случаях callback захватывает токен текущей сессии и перед disarm
     *   проверяет и его, и generation ([DisarmGuard], D-031 п. 31.11).
     *
     * Вызывается только из [applyConfigNow] на главном потоке, поэтому запись
     * `disarmSession`, `observedGeneration` и `armed` не конкурирует со сменой сессии.
     */
    private fun syncDisarm(generation: Long) {
        val previous = observedGeneration
        observedGeneration = generation

        mainHandler.removeCallbacks(disarmIfEmpty)
        when {
            generation > 0L -> Unit
            previous != null && previous > 0L -> {
                disarmSession = configSession
                mainHandler.post(disarmIfEmpty)
            }
            else -> {
                disarmSession = configSession
                mainHandler.postDelayed(disarmIfEmpty, EMPTY_CONFIG_DISARM_DELAY_MS)
            }
        }
    }

    /**
     * Идемпотентное завершение сессии. `onUnbind` и `onDestroy` могут вызвать его
     * оба: повторная отмена job, снятие callback и `detach` безопасны.
     *
     * Токен увеличивается **до** любых записей: коллектор прошлой сессии, который
     * пережил `cancel()`, уже не пройдёт проверку в [applyConfigNow] и не изменит ни
     * `config`, ни запланированный disarm.
     */
    private fun stopSession() {
        configJob?.cancel()
        configJob = null
        configSession++

        mainHandler.removeCallbacks(disarmIfEmpty)

        bridge.setArmed(false)
        engine.resetState()
        engine.config = RuntimeConfig.EMPTY
        engine.foregroundPackage = null

        // Старый экземпляр не должен помечать «остановленной» новую службу.
        if (EngineHolder.engine === engine) ServiceState.onStopped()
        EngineHolder.detach(engine)

        observedGeneration = null
    }

    /**
     * D-027: исключение в `onKeyEvent` фиксируется как `PASSED_ERROR`, насколько это
     * возможно без разобранного [com.mousemanager.core.KeyInput]. Сбой журнала не
     * выходит наружу.
     */
    private fun recordMappingFailure(event: KeyEvent) {
        try {
            app.diagnosticsLog.record(
                KeyRecord(
                    timestamp = System.currentTimeMillis(),
                    deviceId = event.deviceId,
                    deviceName = event.device?.name ?: UNKNOWN_DEVICE,
                    descriptor = event.device?.descriptor,
                    keyCode = event.keyCode,
                    scanCode = event.scanCode,
                    metaState = event.metaState,
                    repeatCount = event.repeatCount,
                    action = event.action,
                    decision = Decision.PASSED_ERROR,
                    isTarget = false,
                    armed = engine.armed,
                ),
            )
        } catch (t: Throwable) {
            Log.e(TAG, "failed to record key mapping error", t)
        }
    }

    private companion object {
        const val TAG = "MouseManager"
        const val UNKNOWN_DEVICE = "unknown"

        /** Отсрочка fail-safe disarm для начального `RuntimeConfig.EMPTY` (D-030). */
        const val EMPTY_CONFIG_DISARM_DELAY_MS = 1_000L
    }
}
