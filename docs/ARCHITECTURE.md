# ARCHITECTURE — Mouse Manager

Версия: 1.0 (действует). Дата: **2026-11-11**.
Связанные документы: `docs/DECISIONS.md` (почему), `docs/PROJECT_STATUS.md` (состояние),
`docs/SPIKES.md` (доказательства), `docs/ROADMAP.md` (порядок).

---

## 1. Цели и не-цели

### Цели v1

1. Глобально переназначать **боковые кнопки физической мыши** на системные действия.
2. Работать **без root** и без обязательного Shizuku.
3. Не мешать обычной клавиатуре, кнопкам громкости и обычным кнопкам мыши.
4. Давать явный, всегда доступный способ выключить перехват.
5. Хранить настройки и профили между запусками.
6. Быть предсказуемым при ошибках: лучше не перехватить, чем заблокировать ввод.

### Не-цели v1

- Ремап левой/правой/средней кнопок (см. D-009, D-012).
- Инъекция произвольных клавиш, макросы, автозапуск приложений.
- Действие «вперёд» в v1 (глобального action не существует; варианты и S3 — D-021).
- Поддержка Android 8–9 (`minSdk 29`, D-001).
- Облачная синхронизация, экспорт/импорт профилей.
- Shizuku, root, `INJECT_EVENTS` (D-013).

---

## 2. Ограничения платформы, которые определяют архитектуру

| Ограничение | Следствие в дизайне |
|---|---|
| Нет root и `INJECT_EVENTS` | Только `performGlobalAction`; произвольные клавиши не синтезируем |
| `AccessibilityService` включается только вручную | UI ведёт пользователя в настройки; служба не может включить себя |
| `onKeyEvent` — только аппаратные события | `adb shell input` не тестирует перехват; автотесты только логические |
| `FLAG_REQUEST_FILTER_KEY_EVENTS` нужно и объявить, и применить | Флаг выставляется в XML и повторно в `onServiceConnected` |
| `InputDevice.descriptor` — API 29+ | `minSdk 29`; `deviceId` не используем как ключ |
| `onMotionEvent` забирает события у системы | Обычные кнопки не трогаем (D-012) |
| Две одинаковые мыши = один `descriptor` | UI предупреждает; различить нельзя |
| `performGlobalAction` может вернуть `false` | Результат проверяется и логируется |
| Фоновый `startActivity` ограничен | Действий «открыть приложение» в v1 нет |
| Служба видит все клавиши | Фильтрация по `descriptor` — первая же проверка |

---

## 3. Высокоуровневая схема

```
┌────────────────────┐
│ Физическая мышь    │  боковая кнопка = Ctrl↓ C↓ Ctrl↑ C↑
└─────────┬──────────┘
          │ hardware key events
          ▼
┌──────────────────────────────────────────────┐
│ Android InputDispatcher                      │
└─────────┬────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────────────┐
│ MouseManagerService : AccessibilityService   │
│  onKeyEvent(KeyEvent)                        │
│    → KeyEventMapper.toKeyInput()             │
│    → RemapEngine.onKey(KeyInput)             │
│         ├─ Pass      → return false          │
│         └─ Consume   → return true           │
│              + ActionDispatcher.dispatch()   │
└─────────┬────────────────────────────────────┘
          │ @Volatile RuntimeConfig
          │
┌─────────┴──────────────┐   ┌──────────────────────────┐
│ ConfigStore            │◄──│ SettingsRepository       │
│ (immutable snapshot)   │   │ (DataStore + org.json)   │
└─────────▲──────────────┘   └────────────▲─────────────┘
          │                               │ writes
          │                               │
┌─────────┴──────────────┐   ┌────────────┴─────────────┐
│ DeviceCatalog          │   │ Compose UI (MainActivity)│
│ (InputManager listener)│   │ Home/Devices/Mappings/…  │
└────────────────────────┘   └──────────────────────────┘
```

Два независимых потока данных:

- **Горячий путь** (событие → решение) не делает I/O и не ждёт UI.
- **Конфигурация** (DataStore → снимок) обновляется асинхронно и публикует новую ссылку.

---

## 4. Слои и пакеты

```
com.mousemanager
├── MouseManagerApp.kt              Application: DataStore, репозиторий, каталог устройств
├── MainActivity.kt                 единственная Activity, Compose
│
├── core/                           чистая логика, без Android-зависимостей
│   ├── KeyInput.kt                 immutable-событие (keyCode, action, metaState, descriptor…)
│   ├── Chord.kt                    Chord, ChordKey, маска модификаторов
│   ├── ActionCode.kt               доступные действия
│   ├── ActionDispatcher.kt         контракт выполнения действия (D-028)
│   ├── Verdict.kt                  Pass / Consume / ConsumeAndDispatch
│   ├── ChordTracker.kt             состояние аккорда: модификаторы, ожидающий UP (M2)
│   ├── RemapEngine.kt              единственное место принятия решения (в M1 — заглушка)
│   └── RuntimeConfig.kt            неизменяемый снимок конфигурации
│
├── service/                        Android-обвязка
│   ├── MouseManagerService.kt      AccessibilityService, тонкий
│   ├── EngineHolder.kt             процессная ссылка на RemapEngine (M2.3.3)
│   ├── KeyEventHandler.kt          решение, журнал и dispatch одного события (M2.3.4)
│   ├── KeyEventMapper.kt           KeyEvent → KeyInput
│   ├── GlobalActionDispatcher.kt   ActionCode → performGlobalAction, rate limit (D-028)
│   ├── CaptureController.kt        «нажмите кнопку» для добавления маппинга (M2)
│   ├── ServiceBridge.kt            единственный путь изменения armed (M2.4, D-031)
│   ├── DisarmGuard.kt              токен сессии для отложенного disarm (M2.4)
│   └── ServiceState.kt             статус службы для UI и зеркало armed (M2.4)
│
├── data/
│   ├── SettingsRepository.kt       единственная точка записи
│   ├── ConfigCodec.kt              Settings ⇄ JSON (org.json)
│   ├── ConfigStore.kt              Settings → RuntimeConfig, @Volatile публикация
│   └── model/
│       ├── Settings.kt
│       ├── Profile.kt
│       └── Mapping.kt
│
├── devices/
│   ├── DeviceCatalog.kt            InputManager + InputDeviceListener
│   └── MouseDevice.kt              descriptor, name, vendor, product, sources
│
├── diagnostics/
│   ├── DiagnosticsLog.kt           кольцевой буфер 500 записей
│   ├── KeyRecord.kt
│   └── Decision.kt
│
└── ui/
    ├── App.kt                      Screen, каркас, навигация, передача зависимостей
    ├── screens/
    │   ├── HomeScreen.kt           статус службы и мастер-переключатель (M2.4)
    │   ├── HomeMasterState.kt      чистое состояние мастер-переключателя (M2.4)
    │   ├── HomeState.kt            чистое состояние карточки «Настройки»
    │   ├── DevicesScreen.kt        список устройств и выбор целей
    │   ├── DevicesState.kt         чистая сборка строк устройств
    │   ├── DiagnosticsScreen.kt    журнал KeyRecord и очистка
    │   ├── DiagnosticsState.kt     форматирование времени/metaState, подписи Decision
    │   └── AboutScreen.kt          версия и ограничения
    ├── components/
    │   └── SectionCard.kt          общая карточка раздела
    └── theme/                      Material3, тёмная/светлая тема
```

**Правило зависимостей:** `core` не импортирует `android.view.KeyEvent`,
`android.accessibilityservice.*` и `androidx.datastore.*`. Это позволяет тестировать
решения на JVM. `service` знает про Android и `core`. `data` знает про DataStore и
`core`-модели. `ui` знает про `data`, `devices`, `diagnostics` и `service`
(`ServiceState`); обратных зависимостей нет.

---

## 5. Модель данных

### 5.1 Конфигурация (персистентная)

```kotlin
enum class Modifier { CTRL, SHIFT, ALT, META }

data class Chord(val mods: Set<Modifier>, val keyCode: Int)

data class Mapping(
    val deviceDescriptor: String?,   // null = любое целевое устройство
    val chord: Chord,
    val action: ActionCode,
    val enabled: Boolean = true,
)

data class Profile(
    val id: String,
    val name: String,
    val mappings: List<Mapping>,
)

data class Settings(
    val schemaVersion: Int = 1,
    val targets: Set<String> = emptySet(),
    val deviceNames: Map<String, String> = emptyMap(),
    val profiles: List<Profile> = listOf(Profile("default", "Основной", emptyList())),
    val activeProfileId: String = "default",
    val defaultProfileId: String = "default",
    val appBindings: Map<String, String> = emptyMap(),   // package -> profileId (M3)
    val diagnosticsEnabled: Boolean = false,
)
```

`armed` сюда **не входит**: это runtime-флаг процесса, а не настройка (D-006). Он живёт
только в `RemapEngine` (`@Volatile`) и сбрасывается при старте процесса/подключении
службы.

### 5.2 Снимок для горячего пути

```kotlin
const val ANY_DEVICE = "*"

data class ChordKey(val modsMask: Int, val keyCode: Int)

data class ProfileRuntime(
    val id: String,
    val name: String,
    // ключ — descriptor или ANY_DEVICE; значение — готовый маппинг
    val byDevice: Map<String, Map<ChordKey, ActionCode>>,
)

data class RuntimeConfig(
    val generation: Long,
    val targets: Set<String>,
    val profiles: Map<String, ProfileRuntime>,
    val activeProfileId: String,
    val defaultProfileId: String,
    val appBindings: Map<String, String>,
)
```

`armed` сюда не входит: это отдельный `@Volatile`-флаг в `RemapEngine`, который не
перестраивается вместе с конфигом и сбрасывается при старте/подключении службы (D-006).

`generation` увеличивается при каждой публикации. Нужен для диагностики: в журнале видно,
какая версия конфига приняла решение.

### 5.3 Событие горячего пути

```kotlin
data class KeyInput(
    val keyCode: Int,
    val action: Int,              // ACTION_DOWN / ACTION_UP
    val repeatCount: Int,
    val metaState: Int,
    val deviceDescriptor: String?,
    val deviceId: Int,
    val scanCode: Int,
    val eventTime: Long,          // uptimeMillis, монотонно
)
```

### 5.4 Решение

```kotlin
sealed interface Verdict {
    data class Pass(val reason: PassReason) : Verdict
    data object Consume : Verdict
    data class ConsumeAndDispatch(val action: ActionCode) : Verdict
}

enum class PassReason {
    DISARMED, EMPTY_TARGETS, NO_DESCRIPTOR, FOREIGN_DEVICE,
    MODIFIER, KEY_UP, NO_MAPPING, REPEAT_OF_UNKNOWN, ERROR,
}
```

---

## 6. Горячий путь: алгоритм

`MouseManagerService.onKeyEvent` — тонкий: разбор `KeyEvent` и защитная обвязка.
Решение, журнал и dispatch одного события — в `KeyEventHandler` (M2.3.4), сама
логика вердикта — в `RemapEngine`.

```kotlin
override fun onKeyEvent(event: KeyEvent): Boolean {
    val input = try {
        KeyEventMapper.toKeyInput(event)
    } catch (t: Throwable) {
        Log.e(TAG, "mapping failed", t)
        recordMappingFailure(event)      // PASSED_ERROR, если возможно
        return false
    }
    return try {
        eventHandler.handle(input, event.device?.name ?: "unknown")
    } catch (t: Throwable) {
        engine.setArmed(false)           // fail-safe D-018
        false
    }
}

// KeyEventHandler, Android-free
fun handle(input: KeyInput, deviceName: String): Boolean {
    val armedAtDecision = engine.armed          // до решения (D-027)
    val runtime = engine.config                // единственный источник конфига
    val base = recordFor(input, deviceName, runtime, armedAtDecision)

    val verdict = try {
        decide(input)
    } catch (t: Throwable) {
        engine.setArmed(false)
        record(base.copy(decision = PASSED_ERROR))
        return false
    }

    return when (verdict) {
        is Pass -> { record(base.copy(decision = decisionFor(verdict))); false }
        Consume -> { record(base.copy(decision = CONSUMED)); true }
        is ConsumeAndDispatch -> {
            record(base.copy(decision = CONSUMED))          // сначала CONSUMED
            val ok = try { dispatcher.dispatch(verdict.action) } catch (t: Throwable) { false }
            if (!ok) record(base.copy(decision = ACTION_FAILED))
            true                                             // всегда consumed
        }
    }
}
```

`record` и `log` вызываются только через безопасные обёртки: их исключение не
меняет возвращаемое значение и не мешает dispatch.

`RemapEngine.onKey` — строгий порядок проверок (fail-safe, D-018):

```kotlin
fun onKey(input: KeyInput): Verdict {
    val cfg = config                       // @Volatile, без блокировок
    val tracker = tracker
    tracker.expire(input.eventTime)        // pending старше 1500 мс удаляется

    // 1. Без descriptor устройство не идентифицировать
    if (input.deviceDescriptor == null) return Pass(NO_DESCRIPTOR)

    // 2. Парный UP уже поглощённого DOWN — ДО armed/targets/конфига (D-005, D-026)
    if (input.action == ACTION_UP &&
        tracker.consumePending(input.deviceDescriptor, input.keyCode)) {
        return Consume
    }

    // 3. Повторный DOWN/repeat при pending — Consume без dispatch (D-026)
    if (input.action == ACTION_DOWN &&
        tracker.refreshPending(input.deviceDescriptor, input.keyCode, input.eventTime)) {
        return Consume
    }

    // 4. Глобальный выключатель (armed — @Volatile в engine, не из DataStore)
    if (!armed) {
        tracker.resetModifiers()           // pending не трогаем: он должен дожить до UP
        return Pass(DISARMED)
    }

    // 5. Идентификация устройства
    if (cfg.targets.isEmpty()) return Pass(EMPTY_TARGETS)
    if (input.deviceDescriptor !in cfg.targets) return Pass(FOREIGN_DEVICE)

    // 6. Модификаторы не поглощаем никогда (D-005)
    if (input.keyCode in MODIFIER_KEYCODES) {
        tracker.onModifier(input)          // только отслеживание
        return Pass(MODIFIER)
    }

    // 7. Остальные UP и MULTIPLE пропускаем
    if (input.action != ACTION_DOWN) return Pass(KEY_UP)

    // 8. Собираем аккорд
    val chord = ChordKey(
        modsMask = modsOf(input.metaState) or tracker.heldMods(input.deviceDescriptor),
        keyCode = input.keyCode,
    )

    // 9. Ищем маппинг: привязка приложения → активный профиль → default
    val action = cfg.resolve(input.deviceDescriptor, chord, foregroundPackage)
        ?: return Pass(NO_MAPPING)

    // 10. Repeat без pending не поглощаем: его первый DOWN видело приложение
    if (input.repeatCount > 0) return Pass(REPEAT_OF_UNKNOWN)

    // 11. Поглощаем DOWN и запоминаем пару для UP
    tracker.setPending(input.deviceDescriptor, input.keyCode, input.eventTime)
    return ConsumeAndDispatch(action)
}
```

Ключевые свойства:

- **Никакого I/O, блокировок, JSON, `suspend`.** Только чтение `@Volatile`-ссылки,
  несколько HashMap-поисков и, при необходимости, одна маленькая аллокация.
- **`descriptor` проверяется первым.** Парные UP и повторный DOWN проверяются ещё раньше
  `armed`, чтобы выключение перехвата, смена конфига или удаление устройства из целей не
  оставляли половину аккорда (D-026).
- **Повторный DOWN при pending поглощается без dispatch:** один DOWN — не более одного
  действия.
- **Repeat без pending проходит как `REPEAT_OF_UNKNOWN`:** мы не поглощаем повтор клавиши,
  чей первый DOWN не поглощали.
- **Pending не переживает переподключение службы:** `resetAll()` при connect/unbind;
  в остальное время — скользящее окно 1500 мс.
- **Модификаторы только наблюдаются.** Даже если маппинг «Ctrl+C», сам Ctrl система
  получает.
- **Поглощённый DOWN всегда имеет парный поглощённый UP** — приложение не видит
  «половину» буквы.
- **Смена конфига между DOWN и UP безопасна:** UP гасится трекером, а не конфигом.
- **Ошибка диспетчера не отменяет поглощение:** клавиша уже не должна уйти в приложение;
  ошибка логируется и показывается, но `onKeyEvent` не бросает исключение.

### 6.1 `ChordTracker`

Состояние на устройство (`descriptor`):

```kotlin
class ChordTracker {
    private val heldMods = HashMap<String, Int>()

    // descriptor -> (keyCode -> время последнего события этой клавиши)
    // Несколько записей: два аккорда могут пересекаться по времени.
    private val pendingUp = HashMap<String, HashMap<Int, Long>>()
    private var lastExpireTime = 0L

    fun onModifier(input: KeyInput)          // set/clear бита по ACTION_DOWN/UP
    fun heldMods(descriptor: String): Int
    fun setPending(descriptor: String, keyCode: Int, time: Long)

    /** true, если pending был; время обновляется как max(старое, time). */
    fun refreshPending(descriptor: String, keyCode: Int, time: Long): Boolean

    /** true, если pending был (и не истёк); запись удаляется. */
    fun consumePending(descriptor: String, keyCode: Int): Boolean

    /** true, если для пары есть живой pending; для тестов и диагностики. */
    fun hasPending(descriptor: String, keyCode: Int): Boolean

    /** Удаляет pending старше PENDING_TIMEOUT_MS; время только вперёд. */
    fun expire(now: Long)

    fun resetModifiers()                     // при disarm: модификаторы забываем
    fun resetAll()                           // при подключении/отключении службы
}

const val PENDING_TIMEOUT_MS = 1_500L
```

Почему нужен `pendingUp`, а не «просто гасить UP по keyCode»: между `C↓` и `C↑` приходит
`Ctrl↑`. Если гасить любой `C↑`, можно случайно съесть `C↑` от другой клавиатуры (её
`descriptor` другой — не съедим) или от этой же мыши, но другого аккорда (маловероятно).
Трекер привязывает ожидание к устройству, клавише и времени.

Несколько pending на устройство нужны, если пользователь быстро нажал обе боковые кнопки:
`C↓` и `V↓` могут идти почти одновременно, а их UP — с разным порядком. Одна запись
перезаписала бы другую, и один UP ушёл бы в приложение сиротой.

**Скользящее окно.** Timeout отсчитывается от последнего события по этой клавише, а не от
первого DOWN. Иначе при удержании клавиши repeat'ы (каждые ~30–50 мс) пережили бы окно,
и повторяющаяся буква ушла бы в приложение. `refreshPending` пишет `max(старое, eventTime)`,
а `expire` ведёт `lastExpireTime = max(now, lastExpireTime)`: пачечная доставка
(`PROJECT_STATUS.md` §2 п. 13) не может откатить время назад. Абсолютного потолка нет:
бесконечно продлевать pending можно только бесконечным потоком repeat, то есть на
неисправном устройстве, где подавление безопаснее утечки.

**Истёкший pending.** Опоздавший UP проходит как `Pass(KEY_UP)`: приложение не видело DOWN,
«залипания» нет (D-005). Tombstone не вводим — он требует неограниченного состояния и
может съесть UP новой пары.

`expire` вызывается на каждом событии: проверка нескольких pending (на практике ≤ 4) —
дешёвая. Если UP потерян (мышь отключилась), ожидание исчезает, ничего не залипает.
`resetModifiers` при выключении перехвата очищает модификаторы, но **не** pending: уже
поглощённый DOWN должен получить парный UP. `resetAll` — только при переподключении
службы, когда события всё равно больше не приходят.

**Поток.** `ChordTracker` используется только с главного потока (`onKeyEvent`,
`onServiceConnected`, `onUnbind`), поэтому внутри обычные `HashMap` без синхронизации.

### 6.2 Маска модификаторов

```kotlin
const val MOD_CTRL = 1
const val MOD_SHIFT = 2
const val MOD_ALT = 4
const val MOD_META = 8
```

Источники истины:

1. `KeyEvent.metaState` (наблюдали `0x3000` на DOWN) — основной.
2. Собственный трекер `heldMods` — страховка, если OEM не заполняет `metaState`.

Берётся объединение. Если пользователь нажал лишний модификатор (например, Shift), маска
не совпадёт с маппингом, и событие пройдёт — это правильное поведение: аккорд не тот.

---

## 7. Диспетчеризация действий

```kotlin
interface ActionDispatcher {
    fun dispatch(action: ActionCode): Boolean
}

class GlobalActionDispatcher(
    private val perform: (Int) -> Boolean,
    private val notifyFailure: () -> Unit,
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
    private val logFailure: (Throwable) -> Unit = {},
) : ActionDispatcher {

    private var lastFailureNotifiedAt: Long? = null

    override fun dispatch(action: ActionCode): Boolean {
        val globalAction = globalActionFor(action) ?: return true
        val performed = try {
            perform(globalAction)
        } catch (e: Exception) {
            logSafely(e)
            false
        }
        if (!performed) notifyFailureOnce()
        return performed
    }

    private fun notifyFailureOnce() {
        val current = now()
        val last = lastFailureNotifiedAt
        if (last != null && current - last < FAILURE_NOTIFY_INTERVAL_MS) return
        lastFailureNotifiedAt = current
        try {
            notifyFailure()
        } catch (e: Exception) {
            logSafely(e)
        }
    }

    private fun logSafely(error: Throwable) {
        try {
            logFailure(error)
        } catch (ignored: Exception) {
            // Диагностика не должна ломать горячий путь.
        }
    }
}

internal fun globalActionFor(action: ActionCode): Int? = when (action) {
    ActionCode.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
    ActionCode.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
    ActionCode.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
    ActionCode.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
    ActionCode.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
    ActionCode.POWER_DIALOG -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
    ActionCode.LOCK_SCREEN -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
    ActionCode.SCREENSHOT -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
    ActionCode.NONE -> null
}
```

- `ActionDispatcher` — интерфейс в `core`, `GlobalActionDispatcher` — в `service`. Android-обвязка
  (`perform`, `notifyFailure`, `now`, `logFailure`) внедряется лямбдами, поэтому логика
  тестируется обычным JUnit без Robolectric (D-028).
- Вызывается на главном потоке (так требует `performGlobalAction`); экземпляр не
  потокобезопасен.
- `NONE` → `globalActionFor == null` → `true`: системный API и Toast не вызываются.
- `false` или исключение → `dispatch` возвращает `false`, без исключения наружу и без
  повторной попытки. `logFailure` получает причину. `KeyEventHandler` пишет `CONSUMED`
  до dispatch и `ACTION_FAILED` второй записью, сохраняя поглощение; Toast показывает
  только диспетчер (D-027 п. 27.3, D-030).
- Rate limit: `lastFailureNotifiedAt: Long? = null` гарантирует первый Toast даже при
  `now() == 0`; дальше — не чаще одного раза в 2000 мс. Отметка ставится **до** вызова
  `notifyFailure`, поэтому исключение в Toast не приводит к повторному показу. Исключения
  `notifyFailure`/`logFailure` перехватываются как `Exception` и не выходят наружу.
- `SCREENSHOT` → `GLOBAL_ACTION_TAKE_SCREENSHOT` (значение 9, API 28; при `minSdk 29`
  проверка версии не нужна). `takeScreenshot()` (API 30) и `takeScreenshotOfWindow()`
  (API 34) требуют `canTakeScreenshot`, собственного сохранения bitmap через MediaStore
  и в v1 не используются: глобальное действие отдаёт снимок системе.
- Доступность `LOCK_SCREEN`/`SCREENSHOT` на HyperOS не гарантируется: проверка в M2.8,
  до неё поддержку не обещаем.
- `ActionDispatcher` — точка расширения: позже можно добавить `ShizukuDispatcher` или
  `IntentDispatcher`, не меняя `RemapEngine`.

---

## 8. Разрешение профиля

```
foregroundPackage (volatile, из TYPE_WINDOW_STATE_CHANGED)
        │
        ▼
appBindings[pkg] ──есть──► профиль привязки
        │ нет
        ▼
activeProfileId ──есть──► активный профиль
        │ нет
        ▼
defaultProfileId ──есть──► профиль по умолчанию
        │ нет
        ▼
        null → Pass(NO_MAPPING)
```

Внутри профиля:

```
byDevice[descriptor]?.get(chordKey)
        ?: byDevice[ANY_DEVICE]?.get(chordKey)
        ?: null
```

Всё предвычислено в `RuntimeConfig`; на горячем пути — только `get`.

**Foreground-пакет.** Служба подписана на `typeWindowStateChanged`; в
`onAccessibilityEvent` обновляет `@Volatile var foregroundPackage`. Содержимое окон не
читается (`canRetrieveWindowContent="false"`). Если событие не пришло (например, после
старта), пакет `null` → используется активный профиль.

---

## 9. Конфигурация: DataStore → снимок

```
DataStore<Preferences>
   │  Flow<Preferences>
   ▼
SettingsRepository.settings          (decode JSON, Dispatchers.Default)
   │  Flow<Settings>
   ▼
ConfigStore.runtime                  (build maps, Dispatchers.Default)
   │  StateFlow<RuntimeConfig>
   ▼
MouseManagerService: config = snapshot   (@Volatile)
```

Правила:

- **Один** `DataStore` на процесс. Делегат `preferencesDataStore("mouse_manager")` в
  `MouseManagerApp`.
- **`armed` не хранится** (D-006): это runtime-флаг в `RemapEngine`, он не входит ни в
  `Settings`, ни в `RuntimeConfig`. DataStore содержит только безопасные настройки
  (цели, профили, маппинги).
- Все записи — через `SettingsRepository` (suspend-функции).
- Декодирование — на `Dispatchers.Default`, не на главном.
- Ошибка декодирования → `ConfigError` в `StateFlow`, `armed=false`, значения по
  умолчанию. Файл не перезаписывается автоматически. Метод `resetToDefaults()` в
  `SettingsRepository` существует, но UI его пока не вызывает.
- `distinctUntilChanged()` по `Settings`, чтобы не перестраивать снимок на каждый чих.
- `generation++` при каждой публикации.

### 9.1 Формат JSON

Схема профиля, пример и миграции — D-007. Существенное для кода: `key` —
`KeyEvent.keyCode` (`C` = 31, `V` = 50); `device: null` — любое целевое устройство;
неизвестное действие при декодировании → `NONE` + предупреждение, а не падение.

### 9.2 Миграции

```kotlin
fun migrate(raw: Preferences): Settings
```

- `schema_version` отсутствует → считается `1`.
- `version > CURRENT` → режим только чтения, перехват выключен.
- `version < CURRENT` → последовательные миграции.
- Миграции покрываются unit-тестами на JSON-фикстурах.

---

## 10. Каталог устройств

```kotlin
data class MouseDevice(
    val descriptor: String,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val sources: Int,
    val isVirtual: Boolean,
)
```

`DeviceCatalog`:

- Начальная энумерация `InputManager.inputDeviceIds`.
- `registerInputDeviceListener` (main thread) для add/remove/changed.
- Фильтр: `sources` содержит `SOURCE_MOUSE` или `SOURCE_TOUCHPAD`.
- Виртуальные (`BUS_VIRTUAL`) исключаются.
- `StateFlow<List<MouseDevice>>` для UI.
- UI группирует по `descriptor`; если на один `descriptor` приходится несколько
  физических устройств, показывает предупреждение «одинаковые мыши неразличимы».
- Если устройства нет в списке, но оно есть в `targets`, UI показывает «не подключено» и
  предлагает переподключить.

---

## 11. Служба: жизненный цикл и статус

```kotlin
class MouseManagerService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = RemapEngine()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var configJob: Job? = null
    private var configSession = 0L              // токен сессии, меняется на main

    @Volatile
    private var observedGeneration: Long? = null  // для немедленного disarm

    private val dispatcher by lazy { GlobalActionDispatcher(/* performGlobalAction, Toast, Log.e */) }
    private val bridge = ServiceBridge(logError = { Log.e(TAG, "service bridge failed", it) })
    private val eventHandler by lazy {
        KeyEventHandler(engine, dispatcher, /* журнал */, disarm = { bridge.setArmed(false) })
    }

    private var disarmSession = 0L              // токен сессии, захваченный при планировании

    private val disarmIfEmpty = Runnable {
        if (DisarmGuard.shouldDisarm(disarmSession, configSession, engine.config.generation)) {
            bridge.setArmed(false)
        }
    }

    override fun onServiceConnected() {
        // Публикуем движок до disarm: мост выключает именно этот экземпляр.
        EngineHolder.attach(engine)
        bridge.setArmed(false)                 // D-006: каждое подключение — disarmed
        engine.resetState()                    // pending прошлой сессии забыт
        engine.config = RuntimeConfig.EMPTY    // новая сессия не наследует старый снимок
        engine.foregroundPackage = null
        observedGeneration = null

        val appliedFlags = applyKeyFilterFlag()
        val filterApplied =
            (appliedFlags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0
        if (!filterApplied) bridge.setArmed(false)  // D-018

        ServiceState.onConnected(appliedFlags) // только после attach и проверки фильтра
        observeConfig()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean { /* см. §6 */ }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        engine.foregroundPackage = pkg          // null/blank не затирают известное значение
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        stopSession()                          // scope не отменяем: возможен повторный connect
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopSession()
        scope.cancel()
        super.onDestroy()
    }

    /** Перезапуск подписки с новым токеном; активным остаётся один сборщик. */
    private fun observeConfig() {
        configJob?.cancel()
        val session = ++configSession
        configJob = scope.launch {
            app.configStore.runtime.collect { applyConfig(session, it) }
        }
    }

    /** Все записи состояния — на main; токен проверяется перед каждой записью. */
    private fun applyConfig(session: Long, runtime: RuntimeConfig) {
        if (Looper.myLooper() == Looper.getMainLooper()) applyConfigNow(session, runtime)
        else mainHandler.post { applyConfigNow(session, runtime) }
    }

    private fun applyConfigNow(session: Long, runtime: RuntimeConfig) {
        if (session != configSession) return   // коллектор прошлой сессии ничего не меняет
        engine.config = runtime
        ServiceState.onConfigApplied(runtime.generation)  // наблюдаемое зеркало для UI
        syncDisarm(runtime.generation)
    }

    /** Идемпотентно: onUnbind и onDestroy могут вызвать оба. */
    private fun stopSession() {
        configJob?.cancel()
        configJob = null
        configSession++                        // старый коллектор теряет право на запись
        mainHandler.removeCallbacks(disarmIfEmpty)
        bridge.setArmed(false)
        engine.resetState()
        engine.config = RuntimeConfig.EMPTY
        engine.foregroundPackage = null
        if (EngineHolder.engine === engine) ServiceState.onStopped()
        EngineHolder.detach(engine)
        observedGeneration = null
    }
}
```

**Конфигурация.** `engine.config` — единственный снимок для горячего пути; служба
своего `config` не хранит. `generation == 0` — fail-safe: начальный `EMPTY`
планирует disarm через 1000 мс, переход `> 0 → 0` выключает перехват немедленно.
В обоих случаях callback на main `Handler` проверяет токен сессии и актуальный
generation через `DisarmGuard`, поэтому устаревший callback не выключает новый
runtime. Изменение конфига никогда не включает `armed` (D-030).

**Сессия наблюдения.** `configSession` увеличивается при запуске и завершении
сессии. Коллектор `ConfigStore.runtime` может пережить `configJob.cancel()`, поэтому
мало отменить job: снимок применяется только в `applyConfigNow`, на главном потоке и
только при совпадении токена. `engine.config`, `observedGeneration` и планирование
`disarmIfEmpty` меняются лишь там же, где вызываются `onServiceConnected` и
`stopSession`; старый коллектор не может ни записать устаревший config, ни
запланировать/выполнить delayed-disarm. Начало и конец сессии дополнительно
сбрасывают `engine.config` в `RuntimeConfig.EMPTY` и `foregroundPackage` в `null`.

`ServiceState` — синглтон в процессе приложения:

```kotlin
data class ServiceStatus(
    val connected: Boolean,
    val appliedFlags: Int,
    val filterApplied: Boolean,   // appliedFlags and FLAG_REQUEST_FILTER_KEY_EVENTS != 0
    val connectedAt: Long,
    val armed: Boolean,           // зеркало engine.armed для UI (D-031)
    val configGeneration: Long,   // зеркало engine.config.generation (D-031 п. 31.14)
)
```

Служба и Activity по умолчанию в одном процессе, поэтому синглтон работает без binder'а.
Если когда-нибудь появится `android:process`, потребуется Messenger/AIDL — сейчас не
нужно.

**Публикация конфигурации.** Коллектор `ConfigStore.runtime` работает на
`Dispatchers.Default`, поэтому запись в движок идёт не в момент подключения, а на
следующем проходе main-`Handler` (по логам — 34 мс). Порядок в `applyConfigNow`:
`engine.config = runtime` → `ServiceState.onConfigApplied(runtime.generation)` →
`syncDisarm(runtime.generation)`. `configGeneration` нужен именно потому, что
`engine.config` — обычное поле: без зеркала в `StateFlow` Compose не узнал бы о
применении конфига и мог бы оставить на Home ложную причину `ENGINE_UNAVAILABLE` до
любой другой перерисовки. `onConnected()` создаёт статус с `configGeneration = 0`
(новая сессия ещё ничего не применила), `onStopped()` сбрасывает статус целиком.
Зеркало — только для наблюдения: включение Master всё равно проверяет движок
(`ServiceBridge.setArmed` ниже).

`EngineHolder` (M2.3.3) — process-wide синглтон в `service/`, хранящий только ссылку
на текущий `RemapEngine`:

```kotlin
object EngineHolder {
    val engine: RemapEngine?          // null — служба не подключена
    fun attach(engine: RemapEngine)   // последний attach побеждает
    fun detach(engine: RemapEngine)   // compareAndSet по identity
}
```

- Holder не создаёт движок, не хранит `armed`/`config`/`foregroundPackage` и не
  вызывает `setArmed()`/`resetState()`: единственный источник состояния — `RemapEngine`.
- `detach` старого экземпляра не снимает новый; повторный `attach` того же экземпляра
  идемпотентен.
- `EngineHolder.engine` можно читать из любого потока; сам `RemapEngine` и
  `ChordTracker` потокобезопасными не становятся (§13).
- С M2.3.4 holder подключён: `attach` в `onServiceConnected` до disarm (мост выключает
  именно опубликованный экземпляр), `detach` в идемпотентном `stopSession()`
  (`onUnbind` и `onDestroy`).

`ServiceBridge` (M2.4, D-031) — единственный публичный путь изменения `armed`:

```kotlin
class ServiceBridge(
    engineProvider: () -> RemapEngine?,      // по умолчанию EngineHolder.engine
    statusProvider: () -> ServiceStatus,     // по умолчанию ServiceState.status.value
    armedMirror: (Boolean) -> Unit,          // по умолчанию ServiceState::onArmed
    logError: (Throwable) -> Unit,           // по умолчанию пусто; служба передаёт Log.e
) {
    fun canArm(): Boolean                    // гейт для доступности тумблера
    fun setArmed(armed: Boolean): Boolean    // фактическое armed после вызова
}
```

- Мост не создаёт и не хранит `RemapEngine`: читает `EngineHolder.engine`, статус —
  из `ServiceState.status`, зеркало пишет через `ServiceState.onArmed`.
- Гейт включения двухуровневый:
  - `canArm()` для доступности тумблера читает только наблюдаемые поля
    (`connected`, `filterApplied`, `ServiceStatus.configGeneration > 0`) и наличие
    движка; `engine.config` не читается, чтобы UI перерисовывался по `StateFlow`;
  - `setArmed(true)` повторяет эти условия в момент вызова и дополнительно требует
    `engine.config.generation > 0`. Зеркало `configGeneration` не является источником
    истины для включения: расхождение всегда решается в пользу движка.
- Выключение разрешено всегда и немедленно; если движок недоступен, обновляется
  только зеркало. Включение при недоступном движке или исключении даёт `armed=false`;
  причина передаётся в `logError`, наружу исключение не выходит (D-018).
- `setArmed` возвращает фактическое состояние (`true` только если перехват включён),
  а не признак успеха операции. Движок и зеркало меняются в одном вызове на главном
  потоке (`@MainThread`); собственной синхронизации у моста нет.
- Служба использует тот же мост для всех disarm: подключение, неприменённый фильтр,
  ошибка `onKeyEvent`, `stopSession`, `disarmIfEmpty`. `KeyEventHandler` получает
  callback `disarm = { bridge.setArmed(false) }`, поэтому аварийное выключение не
  рассинхронизирует зеркало. Значение по умолчанию (`engine.setArmed(false)`)
  сохраняет поведение JVM-тестов.

`DisarmGuard` (M2.4, D-031 п. 31.11) — чистая проверка отложенного disarm:
`shouldDisarm(scheduledSession, currentSession, generation)` возвращает `true` только
при совпадении токена сессии и `generation == 0`. `disarmIfEmpty` захватывает токен
при планировании; `stopSession()` увеличивает `configSession` до записей и снимает
callback. Устаревший callback не выключает перехват новой сессии.

`MouseManagerApp` остаётся точкой сборки данных (`SettingsRepository`, `ConfigStore`,
`DeviceCatalog`, `DiagnosticsLog`) и инициализирует репозиторий один раз на процесс;
служба его не создаёт. Владелец движка — служба. Все disarm, включая
`onServiceConnected` и `stopSession()`, проходят через `ServiceBridge`.

**Гейт включения (M2.4).** UI и `ServiceBridge` не разрешают включение только по
`EngineHolder.engine != null`: holder публикует движок до проверки фильтра и не
отражает остановку процесса/службы. Включение разрешено лишь при активном состоянии
службы, `ServiceState.status.filterApplied == true`, применённом конфиге
(`ServiceStatus.configGeneration > 0` для UI) и доступном движке; при любом сомнении —
`armed = false`. Окончательное решение `setArmed(true)` дополнительно сверяет
`engine.config.generation > 0`, поэтому наблюдаемое зеркало ускоряет реакцию UI, но не
заменяет проверку источника истины.

**Идемпотентность.** `onServiceConnected` может вызываться повторно (переподключение).
Подписка каждый раз перезапускается с новым `configSession`, поэтому активным
остаётся один сборщик; трекер, `config` и `foregroundPackage` сбрасываются.

**Одна служба-фильтр.** В продуктовой конфигурации `FLAG_REQUEST_FILTER_KEY_EVENTS`
запрашивает только `MouseManagerService`. Вторая Accessibility-служба с этим флагом
добавляет к доставке клавиш около 500 мс и в продукт не входит (D-025).

---

## 12. UI

Одна Activity (`MainActivity`), Compose, Material3, без `navigation-compose` и без
ViewModel. Экраны S7: Home, Devices, Diagnostics, About. Mappings и Profiles появятся
в M2 вместе с API маппингов (D-011); в навигацию S7 они не входят.

### 12.0 Каркас и навигация

- `MouseManagerUi` — единственный `Scaffold`: `TopAppBar` с заголовком текущего экрана,
  навигация — `NavigationBar` в `bottomBar` или `NavigationRail` в теле.
- Экран — `enum class Screen` (`HOME`, `DEVICES`, `DIAGNOSTICS`, `ABOUT`); у каждого
  элемента `titleRes` (заголовок) и `labelRes` (пункт навигации) из `strings.xml`.
- Текущий экран — `rememberSaveable { mutableStateOf(Screen.HOME) }`: поворот и
  пересоздание Activity сохраняют раздел.
- `BackHandler(enabled = screen != Screen.HOME)` возвращает на Home; на самом Home
  системная кнопка работает как обычно.
- Адаптивность: `BoxWithConstraints`, порог `600.dp`. Шире — `NavigationRail` слева,
  уже — `NavigationBar` снизу. Пункты текстовые (иконок нет, чтобы не добавлять
  зависимость); порядок задаёт `Screen.entries`.
- Зависимости передаёт `MainActivity`, взяв их у `MouseManagerApp`: `ConfigStore`,
  `SettingsRepository`, `DeviceCatalog`, `DiagnosticsLog`. UI не создаёт собственных
  экземпляров (§9). `ServiceState` — объект-синглтон, Home читает его напрямую.
- Состояние собирается в composable через `collectAsState()`; записи — из
  `rememberCoroutineScope`. Собственных `CoroutineScope`/`ViewModel` нет, поэтому
  корутины живут в композиции и отменяются вместе с ней.

| Экран | Источники данных | Содержимое |
|---|---|---|
| **Home** | `ServiceState.status`, `ConfigStore.runtime`/`error`, `ServiceBridge` | Статус службы (подключена/нет) и реально применённый фильтр клавиш, кнопка перехода в системные настройки доступности, мастер-переключатель с подтверждением включения и причиной недоступности, сводка: число выбранных устройств и имя активного профиля |
| **Devices** | `DeviceCatalog.devices`, `ConfigStore.runtime.targets`, `SettingsRepository.setTargets` | Список подключённых мышей и тачпадов (имя, `vendor:product`, сокращённый `descriptor`), сохранённые, но отключённые цели, чекбокс цели; дубликаты `descriptor` склеены в одну строку |
| **Diagnostics** | `DiagnosticsLog.records`/`clear()` | Журнал `KeyRecord` (новые сверху): время, `Decision`, устройство, клавиша и `scanCode`, DOWN/UP, `metaState`, `isTarget`, `armed`; пустое состояние и очистка |
| **About** | `BuildConfig.VERSION_NAME` | Назначение приложения, версия, ограничения текущей версии (D-009, D-012, D-014, D-017, D-021) |

Чистые функции состояния (`homeConfigState`, `homeMasterState`, `deviceRows`,
`formatRecordTime`, `formatMetaState`, `decisionLabelRes`) вынесены из composable и
покрыты JVM-тестами. Все пользовательские строки — в `strings.xml` (D-019). Общая
карточка раздела — `ui/components/SectionCard.kt`.

### 12.1 Мастер-переключатель

Реализовано в M2.4 (D-031). `HomeScreen` читает `ServiceState.status` и
`ConfigStore.runtime`/`error`, а `armed` меняет только через `ServiceBridge`.

Чистая `homeMasterState(status, error, canArm)` возвращает `HomeMasterState`:

- `checked = status.armed`;
- `enabled = reason == null`;
- `reason` — первая причина по порядку: служба не подключена, фильтр не применён,
  ошибка конфигурации, конфигурация ещё не применена
  (`status.configGeneration == 0`), мост/движок не готов (`canArm == false`);
- `requiresConfirmation = enabled && !checked`.

Поведение UI:

- включение — только после `AlertDialog` с подтверждением; подтверждение не
  сохраняется и на следующую сессию не переносится;
- выключение — сразу, без диалога; при включённом перехвате переключатель остаётся
  доступным, даже если гейт успел закрыться (`enabled || checked`);
- при недоступности рядом показывается причина из `strings.xml`; включить при
  закрытом гейте нельзя;
- наличие `EngineHolder.engine != null` само по себе включение не разрешает: гейт
  проверяется в `ServiceBridge.canArm()`/`setArmed()` и включает `filterApplied`,
  применённый конфиг (`configGeneration > 0` для UI и `engine.config.generation > 0`
  в момент включения) и доступность движка (D-030 п. 30.12, D-031 п. 31.4, 31.14);
- готовность конфигурации приходит через `ServiceState.status`, поэтому после
  применения конфига причина `CONFIG_LOADING`/`ENGINE_UNAVAILABLE` исчезает сама, без
  смены вкладки или другого действия пользователя.

Известное замечание (P2): если гейт закрывается между открытием диалога и
подтверждением, `setArmed(true)` возвращает `false` без отдельного сообщения —
переключатель остаётся выключенным, причина обновляется по `ServiceState`.

### 12.2 Режим захвата аккорда (M2)

1. Пользователь нажимает «Добавить действие» в Mappings.
2. `CaptureController` включается только при подключённой службе и живёт на главном потоке.
3. `MouseManagerService.onKeyEvent` проверяет захват **до** `RemapEngine`: событие целевого
   устройства передаётся controller'у и возвращается системе (`false`).
4. Модификаторы игнорируются; первый не-модификатор DOWN фиксирует `Chord`
   (`modsMask` + `keyCode`).
5. Диалог: «Найдено: Ctrl+C» → выбор `ActionCode` → сохранение в активный профиль.
6. Таймаут 10 с и отмена при уходе с экрана; после таймаута захват выключается,
   событие проходит как обычно.

Захват не поглощает событие и не вызывает `ActionDispatcher`: добавление маппинга не
выполнит действие. Штатное copy/paste во время захвата может сработать — это ожидаемо,
подсказка в UI.

---

## 13. Потоки и производительность

| Контекст | Что делает | Требования |
|---|---|---|
| Main (служба) | `onKeyEvent`, `performGlobalAction`, `onAccessibilityEvent` | < 1 мс на событие, без I/O |
| Main (служба) | `ChordTracker`, `CaptureController` | только этот поток; без блокировок |
| Default (корутина службы) | сбор DataStore, построение `RuntimeConfig` | может быть медленным |
| Main (UI) | Compose, клики | без блокировок |
| Default/IO (репозиторий) | запись DataStore, JSON | вне главного |

Правила:

- В горячем пути нет `runBlocking`, `synchronized`, `Mutex`, `DataStore`, `JSON`.
- `@Volatile`-ссылка на неизменяемый снимок — единственная синхронизация.
- Журнал диагностики — тоже `@Volatile`/`StateFlow`, запись не блокирует.
- `expire()` для pending — O(1).
- `ChordTracker` и `CaptureController` живут только на главном потоке; синхронизация не нужна.

---

## 14. Отказоустойчивость

Полный набор инвариантов и сценариев — D-018. Ключевое:

| Сценарий | Поведение |
|---|---|
| Служба не включена / флаг не принят | перехвата нет, `filterApplied=false`, UI объясняет |
| Переподключение службы | `armed=false`, трекер сброшен, флаг применён заново |
| Конфиг не распарсился / `schema_version` новее | `armed=false`, fail-safe снимок, pass, сообщение |
| DataStore завис или ошибка записи | горячий путь его не читает, работает последний снимок |
| `targets` пуст, `descriptor == null`, чужое устройство, модификатор, нет маппинга | pass |
| `C↓` без `C↑` / `C↑` без `C↓` | pending истекает / pass; залипания нет |
| Два аккорда пересекаются | несколько pending на устройство, оба UP гасятся |
| Смена фокуса/окна | `onKeyEvent` глобальный, действие выполняется на DOWN |
| Мышь отключилась между DOWN и UP | pending истекает, привязка сохраняется |
| Мышь вернулась | тот же `descriptor` — работает сразу; другой — выбрать заново |
| Смена профиля или выключение перехвата между DOWN и UP | UP и повторный DOWN всё равно гасятся (D-026) |
| Повторный DOWN/repeat при pending | `Consume` без dispatch; время pending обновляется (D-026) |
| Repeat без pending | `Pass(REPEAT_OF_UNKNOWN)`: повтор не поглощаем |
| UP после истечения pending | `Pass(KEY_UP)`: сиротский UP безвреден (D-005) |
| Долгое удержание с repeat | pending продлевается скользящим окном; в приложение ничего не утекает |
| Исключение в `onKeyEvent` | pass + `armed=false` + запись |
| Исключение в `performGlobalAction` | `ACTION_FAILED` + Toast, без падения и retry |
| Падение процесса / перезагрузка / обновление HyperOS | `armed=false`; при регрессии — честный статус |

---

## 15. Приватность и безопасность

- Нет разрешения `INTERNET`. Данные не покидают устройство.
- Нет доступа к содержимому окон (`canRetrieveWindowContent="false"`).
- Журнал — всегда в памяти, не на диске; очищается при смерти процесса. В текущей
  версии служба пишет журнал всегда; переключатель `diagnosticsEnabled` появится
  в M4.
- В журнал пишутся коды клавиш и `descriptor`, не текст.
- `Log.d` — только в debug-сборке.
- Служба описывает в `accessibility_description`, зачем ей фильтр клавиш.

---

## 16. Тестирование

### 16.1 JVM unit-тесты (основное покрытие)

`RemapEngine`, `ChordTracker`, `GlobalActionDispatcher`, `EngineHolder`,
`KeyEventHandler`, `ServiceBridge`, `DisarmGuard` и `HomeMasterState` не зависят от
Android-рантайма (Android-обвязка и провайдеры внедряются лямбдами), поэтому
тестируются обычным JUnit. `ServiceState` использует только константу флага Android,
доступную в unit-тестах. Всего в M2.4 (после исправления A) — **220 тестов в 19
suites**, 0 failures / 0 errors / 0 skipped:

| Тест | Ожидание |
|---|---|
| `armed=false` | `Pass(DISARMED)` |
| чужой `descriptor` | `Pass(FOREIGN_DEVICE)` |
| `descriptor=null` | `Pass(NO_DESCRIPTOR)` |
| пустые `targets` | `Pass(EMPTY_TARGETS)` |
| Ctrl DOWN | `Pass(MODIFIER)` |
| Ctrl+C DOWN, маппинг есть | `ConsumeAndDispatch(BACK)` |
| C UP после поглощённого DOWN | `Consume` |
| C UP без DOWN | `Pass(KEY_UP)` |
| C DOWN без Ctrl | `Pass(NO_MAPPING)` |
| repeat при pending | `Consume`, без повторного dispatch |
| repeat без pending | `Pass(REPEAT_OF_UNKNOWN)` |
| повторный DOWN (`repeatCount = 0`) при pending | `Consume`, dispatch один (D-026) |
| серия repeat дольше 1500 мс без UP | pending продлевается, dispatch один |
| UP после истечения pending | `Pass(KEY_UP)` |
| `armed=false` + повторный DOWN при pending | `Consume` (D-026) |
| два устройства с одинаковым keyCode | pending не пересекаются |
| два keyCode на одном устройстве | оба pending независимы, оба UP гасятся |
| захват аккорда | событие не доходит до `RemapEngine` и не поглощается |
| `SCREENSHOT` в маппинге | dispatcher получает `GLOBAL_ACTION_TAKE_SCREENSHOT` (константа встраивается, Robolectric не нужен) |
| `ActionCode` → `GLOBAL_ACTION_*` | все восемь значений совпадают с константами `AccessibilityService` |
| `NONE` в `dispatch` | `true` без `perform` и без Toast |
| `performGlobalAction` вернул `false` | `dispatch=false`, одно уведомление, без retry |
| исключение в `perform` | `dispatch=false`, запись в лог, без retry |
| rate limit Toast | первый показ при `now=0`; повтор не раньше 2000 мс |
| исключение в Toast/логгере | не выходит наружу; отметка времени сохраняется |
| `EngineHolder` без экземпляра | `engine == null`; чтение безопасно |
| `EngineHolder.attach` | публикует тот же экземпляр; новый заменяет старый; повтор идемпотентен |
| `EngineHolder.detach` | снимает только текущий; чужой или старый экземпляр не трогает новый |
| `EngineHolder` и состояние движка | `attach`/`detach` не меняют `armed`, `config`, `foregroundPackage` и pending |
| `KeyEventHandler`: `Pass`/`Consume`/`ConsumeAndDispatch` | `false` / `true` / `true`; `Decision` берётся из `DecisionMapper` |
| `KeyEventHandler`: порядок записей | `CONSUMED` до dispatch; при отказе — вторая запись `ACTION_FAILED` |
| `KeyEventHandler`: ошибка записи | не меняет результат и не мешает dispatch |
| `KeyEventHandler`: исключение dispatch | `ACTION_FAILED`, событие остаётся consumed |
| `KeyEventHandler`: исключение engine | disarm-callback (в службе — `ServiceBridge.setArmed(false)`), `PASSED_ERROR`, `false` |
| `KeyEventHandler`: `armedAtDecision` | читается до решения, даже если решение выключает `armed` |
| `KeyEventHandler`: поля `ACTION_FAILED` | совпадают с `CONSUMED`; отличается только `decision` |
| `KeyEventHandler`: `isTarget` | `true` только для выбранного target при `generation > 0` |
| между C DOWN и C UP пришёл Ctrl UP | pending сохраняется |
| pending старше 1500 мс | сбрасывается |
| смена конфига между DOWN и UP | UP всё равно `Consume` |
| выключение перехвата между DOWN и UP | UP всё равно `Consume` (pending проверяется до `armed`) |
| два аккорда пересекаются | оба UP `Consume` (несколько pending) |
| повторный DOWN до UP | второй DOWN поглощается без повторного dispatch |
| `C↓` без `C↑`, затем новый `C↓` | старый pending истекает, новый работает |
| привязка приложения | выбирается профиль привязки |
| нет привязки | активный профиль |
| нет активного | default |
| нет маппинга | `Pass(NO_MAPPING)` |
| `ServiceBridge`: гейт | нет службы / фильтра / `configGeneration > 0` / движка → `canArm=false`, включение отклонено |
| `ServiceBridge`: окончательная проверка | готовое зеркало при `engine.config.generation == 0` → `canArm=true`, но `setArmed(true)` отклонён |
| `ServiceBridge`: включение | при выполненном гейте движок и зеркало получают `true` в одном вызове |
| `ServiceBridge`: выключение | разрешено всегда; при недоступном движке обновляется только зеркало |
| `ServiceBridge`: ошибки | исключения провайдеров, зеркала и логгера не выходят наружу, итог — `armed=false` |
| `ServiceState`: зеркало `armed` | `false` по умолчанию, `onStopped()` сбрасывает, переподключение не восстанавливает |
| `ServiceState`: `configGeneration` | публикуется `onConfigApplied`, сбрасывается при переподключении и остановке |
| `DisarmGuard` | disarm только при совпадении токена сессии и `generation == 0` |
| `HomeMasterState` | `checked`/`enabled`/`reason`/`requiresConfirmation` по состояниям службы, фильтра, конфига и `canArm` |
| `HomeMasterState`: готовность конфига | `CONFIG_LOADING` до публикации `configGeneration`, причина исчезает после неё; ложного `ENGINE_UNAVAILABLE` нет |

Плюс тесты `ConfigCodec`: round-trip, неизвестное действие, битый JSON, миграция версий.

### 16.2 Ручные тесты на устройстве

Механизм фильтра нельзя протестировать через adb (D-002), поэтому чек-лист:

Чек-лист M1/S7 (UI и отсутствие перехвата) — `docs/S7-DEVICE-CHECKLIST.md`.
Пункты ниже относятся к M2, когда перехват уже работает.

1. Служба включена, `filterApplied=true`.
2. Цель выбрана, перехват включён.
3. Боковая кнопка → действие выполнилось, в журнале `CONSUMED` для триггера.
4. Ctrl в журнале — `PASSED_MODIFIER` (или отсутствует, если не логируем).
5. Copy/paste этой мышью не срабатывают, пока перехват включён.
6. Кнопки громкости работают (`PASSED_FOREIGN_DEVICE`).
7. Переподключение мыши → `descriptor` тот же, маппинг работает.
8. Выключение перехвата → аккорд снова копирует/вставляет.
9. Перезапуск процесса → `armed=false` (D-006), перехват не восстановился сам.
10. Перезапуск службы (выключить/включить в настройках) → `armed=false`.
11. Смена профиля → новое действие.
12. (M3) Привязка приложения → переключение при смене foreground-пакета.
13. **Клавиатура (когда появится железо):** буквы, цифры, Enter, Backspace, Shift/Ctrl/
    Alt, Ctrl+C/Ctrl+V, IME-ввод, аппаратные сочетания. Это **непроверенное условие**:
    VOLUME_UP/DOWN доказывают только фильтрацию по устройству, а не совместимость с
    полноценной клавиатурой.
14. M2.4: тумблер включается/выключается; без маппингов ничего не гасится; после
    переподключения службы — `armed=false`. После включения службы в настройках и
    возврата в приложение Home не показывает «Служба ещё не готова к перехвату»:
    готовность конфига приходит наблюдаемым `configGeneration` без смены вкладки
    (исправление A).
15. M2.6: первый маппинг `Ctrl+C → BACK`; «голый Ctrl» — по чек-листу ниже.
16. Фоновая доставка: другое приложение на переднем плане, нажатие боковой кнопки;
    зафиксировать `descriptor` и `decision`; повторить после выключения/включения экрана.
17. Повторный DOWN/repeat на живой мыши: одно действие, нет залипания.
18. Смена профиля/выключение перехвата во время удержания: нет залипания, нет второго
    действия.
19. `LOCK_SCREEN` и `SCREENSHOT` на HyperOS: результат зафиксирован, без обещаний.

Пункт 14 — единственная незакрытая часть M2.4: JVM-тесты не покрывают lifecycle
службы и живой фильтр; проверка выполняется только по отдельному разрешению.
Исправление A (наблюдаемая готовность конфига) в JVM покрыто, но на устройстве ещё
не проверялось.

**Чек-лист «голого Ctrl» (к пункту 15).**

1. Редактор: боковая кнопка → действие; символ `c` не появился.
2. Журнал: `Ctrl` — `PASSED_MODIFIER`, `C` — `CONSUMED`, ошибок нет.
3. После действия — ввод экранной клавиатурой: строчные буквы, выделение, жесты;
   Ctrl не «залип».
4. Проверить в браузере, Telegram, Gboard/IME.
5. Выключить перехват: `Ctrl+C`/`Ctrl+V` работают штатно.
6. Включить снова: маппинг работает, copy/paste не срабатывает.
7. Быстро нажать обе боковые кнопки: два действия, без залипания.
8. Повторить при другом foreground-приложении и после выключения/включения экрана.
   Физическая клавиатура — SKIP.

### 16.3 Чего не тестируем автоматически

- Сам фильтр клавиш (только живая мышь).
- Полноценная клавиатура (только вручную, когда появится железо).
- `performGlobalAction` на конкретной прошивке (только вручную).
- Поведение при выключенном экране (вручную).

---

## 17. Сборка

Истина — `app/build.gradle.kts`; сводка версий — `PROJECT_STATUS.md` §3. Ключевое:
`namespace`/`applicationId` = `com.mousemanager`, compileSdk 36, targetSdk 35, minSdk 29,
buildTools 36.1.0, versionName 0.1.0, Java/Kotlin 17, `compose` и `buildConfig` включены.

Зависимости v1 — D-020. `debug` без minify; `release` minify включается в M4 после
проверки, что accessibility-служба и JSON-кодеки не пострадали от R8.

---

## 18. Точки расширения

| Что | Как | Когда |
|---|---|---|
| Действие «вперёд» / инъекция клавиш | `ActionDispatcher` + Shizuku/`INJECT_EVENTS` | Spike S3 (D-021) |
| Ремап средней кнопки | отдельный путь `onMotionEvent` + инъекция | Spike S3+, вне v1 |
| Импорт/экспорт профилей | JSON-файл через `ACTION_CREATE_DOCUMENT` | v2 |
| Per-app автоматизация | `Intent`/Shizuku | v2 |
| Несколько мышей с разными профилями | уже поддержано `descriptor` (кроме одинаковых моделей) | v1 |
| Быстрый panic | `TileService` в шторке | M4 |

---

## 19. Открытые архитектурные вопросы

1. **Panic tile** — обязателен или достаточно тумблера в приложении? (M4, опционально)
2. **Диагностика в release** — выключена по умолчанию; нужен ли экспорт в файл?
3. **Клавиатура** — когда появится железо, прогнать чек-лист §16.2 п. 13.
4. **«Вперёд»** — решение по итогам S3 (D-021), не раньше.
5. **Фоновая доставка клавиш.** `PROJECT_STATUS.md` §2 п. 13: в фоне колбэки приходят
   пачкой, у отложенных событий `descriptor=null`. Нужно проверить, работает ли ремап при
   другом foreground-приложении (M2.8); при необходимости — foreground-служба или
   исключение из оптимизации батареи.
6. **Диагностические коды для повторных событий** — закрыто в D-027: у всех девяти
   `PassReason` есть отдельный `Decision`, включая `PASSED_KEY_UP` и `PASSED_REPEAT`;
   `PASSED_NON_HARDWARE` остаётся резервом.

Решено и не обсуждается в v1: `armed` всегда выключается при старте (D-006); именованные
профили в v1, per-app — M3 (D-011); восемь глобальных действий (D-010); пакет
`com.mousemanager` подтверждён.
