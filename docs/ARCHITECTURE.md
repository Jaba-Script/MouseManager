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
│   ├── Verdict.kt                  Pass / Consume / ConsumeAndDispatch
│   ├── ChordTracker.kt             состояние аккорда: модификаторы, ожидающий UP (M2)
│   ├── RemapEngine.kt              единственное место принятия решения (в M1 — заглушка)
│   └── RuntimeConfig.kt            неизменяемый снимок конфигурации
│
├── service/                        Android-обвязка
│   ├── MouseManagerService.kt      AccessibilityService, тонкий
│   ├── KeyEventMapper.kt           KeyEvent → KeyInput
│   ├── GlobalActionDispatcher.kt   ActionCode → performGlobalAction (M2)
│   ├── CaptureController.kt        «нажмите кнопку» для добавления маппинга (M2)
│   └── ServiceState.kt             статус службы (connected, flags) для UI
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
    │   ├── HomeScreen.kt           статус службы, неактивный мастер-переключатель
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

`MouseManagerService.onKeyEvent` — тонкий. Вся логика в `RemapEngine`.

```kotlin
override fun onKeyEvent(event: KeyEvent): Boolean {
    val input = KeyEventMapper.toKeyInput(event)
    val verdict = try {
        engine.onKey(input)
    } catch (t: Throwable) {
        diagnostics.recordError(t)
        engine.setArmed(false)
        return false
    }
    diagnostics.record(input, verdict, engine.snapshot())
    if (verdict is Verdict.ConsumeAndDispatch) {
        try {
            dispatcher.dispatch(verdict.action)
        } catch (t: Throwable) {
            // Клавиша уже поглощена; не пробрасываем исключение в InputDispatcher.
            diagnostics.recordError(t)   // ACTION_FAILED
        }
        return true
    }
    return verdict is Verdict.Consume
}
```

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
    private val service: AccessibilityService,
) : ActionDispatcher {

    override fun dispatch(action: ActionCode): Boolean {
        val globalAction = when (action) {
            ActionCode.BACK -> GLOBAL_ACTION_BACK
            ActionCode.HOME -> GLOBAL_ACTION_HOME
            ActionCode.RECENTS -> GLOBAL_ACTION_RECENTS
            ActionCode.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            ActionCode.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
            ActionCode.POWER_DIALOG -> GLOBAL_ACTION_POWER_DIALOG
            ActionCode.LOCK_SCREEN -> GLOBAL_ACTION_LOCK_SCREEN
            ActionCode.SCREENSHOT -> GLOBAL_ACTION_TAKE_SCREENSHOT
            ActionCode.NONE -> return true
        }
        return service.performGlobalAction(globalAction)
    }
}
```

- Вызывается на главном потоке (так требует `performGlobalAction`).
- `false` → запись в диагностику, без исключения и без повторной попытки.
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
    private lateinit var engine: RemapEngine
    private lateinit var dispatcher: GlobalActionDispatcher

    override fun onServiceConnected() {
        // 1. armed = false: каждое подключение начинает с выключенного перехвата (D-006)
        // 2. Применить FLAG_REQUEST_FILTER_KEY_EVENTS и прочитать, что реально применилось
        // 3. Если filterApplied == false -> armed = false + предупреждение в UI
        // 4. Инициализировать репозиторий/снимок (идемпотентно)
        // 5. Подписаться на RuntimeConfig
        // 6. Опубликовать ServiceState (connected, appliedFlags)
        // 7. chordTracker.resetAll()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean { /* см. §6 */ }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == TYPE_WINDOW_STATE_CHANGED) {
            foregroundPackage = event.packageName?.toString()
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        scope.cancel()
        ServiceState.onStopped()
        return super.onUnbind(intent)
    }
}
```

`ServiceState` — синглтон в процессе приложения:

```kotlin
data class ServiceStatus(
    val connected: Boolean,
    val appliedFlags: Int,
    val filterApplied: Boolean,   // appliedFlags and FLAG_REQUEST_FILTER_KEY_EVENTS != 0
    val connectedAt: Long,
)
```

Служба и Activity по умолчанию в одном процессе, поэтому синглтон работает без binder'а.
Если когда-нибудь появится `android:process`, потребуется Messenger/AIDL — сейчас не
нужно.

`RemapEngine` живёт в процессе как синглтон (`EngineHolder`), создаётся службой. UI
обращается к нему через тонкий `ServiceBridge`: `setArmed(true/false)`, `snapshot()`,
`resetDiagnostics()`. Если служба не подключена, `EngineHolder.engine == null`, и UI
блокирует тумблер.

`MouseManagerApp` остаётся точкой сборки данных (`SettingsRepository`, `ConfigStore`,
`DeviceCatalog`, `DiagnosticsLog`), но движок не создаёт: владелец — служба.
`setArmed(false)` вызывается в `onServiceConnected` и `onUnbind`; при `engine == null`
включить перехват из UI нельзя.

**Идемпотентность.** `onServiceConnected` может вызываться повторно (переподключение).
Инициализация репозитория и подписка защищены флагом; трекер сбрасывается.

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
| **Home** | `ServiceState.status`, `ConfigStore.runtime`/`error` | Статус службы (подключена/нет) и реально применённый фильтр клавиш, кнопка перехода в системные настройки доступности, неактивный мастер-переключатель, сводка: число выбранных устройств и имя активного профиля |
| **Devices** | `DeviceCatalog.devices`, `ConfigStore.runtime.targets`, `SettingsRepository.setTargets` | Список подключённых мышей и тачпадов (имя, `vendor:product`, сокращённый `descriptor`), сохранённые, но отключённые цели, чекбокс цели; дубликаты `descriptor` склеены в одну строку |
| **Diagnostics** | `DiagnosticsLog.records`/`clear()` | Журнал `KeyRecord` (новые сверху): время, `Decision`, устройство, клавиша и `scanCode`, DOWN/UP, `metaState`, `isTarget`, `armed`; пустое состояние и очистка |
| **About** | `BuildConfig.VERSION_NAME` | Назначение приложения, версия, ограничения текущей версии (D-009, D-012, D-014, D-017, D-021) |

Чистые функции состояния (`homeConfigState`, `deviceRows`, `formatRecordTime`,
`formatMetaState`, `decisionLabelRes`) вынесены из composable и покрыты JVM-тестами.
Все пользовательские строки — в `strings.xml` (D-019). Общая карточка раздела —
`ui/components/SectionCard.kt`.

### 12.1 Мастер-переключатель

В M1 `RemapEngine` — заглушка, `armed` не существует (D-006), поэтому переключатель
всегда выключен и неактивен (`checked = false`, `enabled = false`,
`onCheckedChange = null`), рядом — «Будет доступно в M2». Нажатие ничего не меняет и
настройки не пишет.

В M2 (план): переключатель недоступен, если служба не подключена или
`filterApplied=false`; при включении — диалог подтверждения, при выключении —
мгновенно; рядом — статус службы, флага и выбранной цели.

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

`RemapEngine` и `ChordTracker` не зависят от Android, поэтому тестируются обычным JUnit:

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
| `SCREENSHOT` в маппинге | dispatcher получает `GLOBAL_ACTION_TAKE_SCREENSHOT` (если константа доступна в JVM-тесте) |
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
    переподключения службы — `armed=false`.
15. M2.6: первый маппинг `Ctrl+C → BACK`; «голый Ctrl» — по чек-листу ниже.
16. Фоновая доставка: другое приложение на переднем плане, нажатие боковой кнопки;
    зафиксировать `descriptor` и `decision`; повторить после выключения/включения экрана.
17. Повторный DOWN/repeat на живой мыши: одно действие, нет залипания.
18. Смена профиля/выключение перехвата во время удержания: нет залипания, нет второго
    действия.
19. `LOCK_SCREEN` и `SCREENSHOT` на HyperOS: результат зафиксирован, без обещаний.

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
6. **Диагностические коды для повторных событий.** `PassReason.REPEAT_OF_UNKNOWN` и
   `KEY_UP` пока не имеют отдельного `Decision`; в M2.3 нужна явная таблица
   `PassReason → Decision` (см. D-017).

Решено и не обсуждается в v1: `armed` всегда выключается при старте (D-006); именованные
профили в v1, per-app — M3 (D-011); восемь глобальных действий (D-010); пакет
`com.mousemanager` подтверждён.
