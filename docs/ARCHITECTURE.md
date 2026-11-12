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
│   ├── ChordTracker.kt             состояние аккорда: модификаторы, ожидающий UP
│   ├── RemapEngine.kt              единственное место принятия решения
│   └── RuntimeConfig.kt            неизменяемый снимок конфигурации
│
├── service/                        Android-обвязка
│   ├── MouseManagerService.kt      AccessibilityService, тонкий
│   ├── KeyEventMapper.kt           KeyEvent → KeyInput
│   ├── GlobalActionDispatcher.kt   ActionCode → performGlobalAction
│   ├── CaptureController.kt        «нажмите кнопку» для добавления маппинга
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
    ├── App.kt                      экраны и навигация без библиотеки
    ├── screens/                    Home, Devices, Mappings, Profiles, Diagnostics, About
    ├── components/                 карточки, диалоги, переключатели
    └── theme/                      Material3, тёмная/светлая тема
```

**Правило зависимостей:** `core` не импортирует `android.view.KeyEvent`,
`android.accessibilityservice.*` и `androidx.datastore.*`. Это позволяет тестировать
решения на JVM. `service` знает про Android и `core`. `data` знает про DataStore и
`core`-модели. `ui` знает про `data`, `devices`, `diagnostics`.

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
    tracker.expire(input.eventTime)        // дешёвая проверка pending

    // 1. Без descriptor устройство не идентифицировать
    if (input.deviceDescriptor == null) return Pass(NO_DESCRIPTOR)

    // 2. Завершение уже поглощённого аккорда — ДО проверки armed/targets.
    //    Выключение перехвата или смена целей не должны оставлять половину аккорда.
    if (input.action == ACTION_UP &&
        tracker.consumePending(input.deviceDescriptor, input.keyCode)) {
        return Consume
    }

    // 3. Глобальный выключатель (armed — @Volatile в engine, не из DataStore)
    if (!armed) {
        tracker.resetModifiers()           // pending не трогаем: он должен дожить до UP
        return Pass(DISARMED)
    }

    // 4. Идентификация устройства
    if (cfg.targets.isEmpty()) return Pass(EMPTY_TARGETS)
    if (input.deviceDescriptor !in cfg.targets) return Pass(FOREIGN_DEVICE)

    // 5. Модификаторы не поглощаем никогда (D-005)
    if (input.keyCode in MODIFIER_KEYCODES) {
        tracker.onModifier(input)          // только отслеживание
        return Pass(MODIFIER)
    }

    // 6. Остальные UP и MULTIPLE пропускаем
    if (input.action != ACTION_DOWN) return Pass(KEY_UP)

    // 7. Собираем аккорд
    val chord = ChordKey(
        modsMask = modsOf(input.metaState) or tracker.heldMods(input.deviceDescriptor),
        keyCode = input.keyCode,
    )

    // 8. Ищем маппинг: привязка приложения → активный профиль → default
    val action = cfg.resolve(input.deviceDescriptor, chord, foregroundPackage)
        ?: return Pass(NO_MAPPING)

    // 9. Повтор уже поглощённого триггера не должен стрелять второй раз
    if (input.repeatCount > 0) return Consume

    // 10. Поглощаем DOWN и запоминаем, что нужно поглотить UP
    tracker.setPending(input.deviceDescriptor, input.keyCode, input.eventTime)
    return ConsumeAndDispatch(action)
}
```

Ключевые свойства:

- **Никакого I/O, блокировок, JSON, `suspend`.** Только чтение `@Volatile`-ссылки,
  несколько HashMap-поисков и, при необходимости, одна маленькая аллокация.
- **`descriptor` проверяется первым.** Pending UP — ещё раньше `armed`, чтобы выключение
  перехвата не оставляло половину аккорда.
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

    // descriptor -> (keyCode -> eventTime)
    // Несколько записей: два аккорда могут пересекаться по времени.
    private val pendingUp = HashMap<String, HashMap<Int, Long>>()

    fun onModifier(input: KeyInput)          // set/clear бита по ACTION_DOWN/UP
    fun heldMods(descriptor: String): Int
    fun setPending(descriptor: String, keyCode: Int, time: Long)

    /** true, если UP был ожидаем (свежий pending); запись удаляется. */
    fun consumePending(descriptor: String, keyCode: Int): Boolean

    fun expire(now: Long)                    // pending старше 1500 мс удаляется
    fun resetModifiers()                     // при disarm: модификаторы забываем
    fun resetAll()                           // при подключении/отключении службы
}
```

Почему нужен `pendingUp`, а не «просто гасить UP по keyCode»: между `C↓` и `C↑` приходит
`Ctrl↑`. Если гасить любой `C↑`, можно случайно съесть `C↑` от другой клавиатуры (её
`descriptor` другой — не съедим) или от этой же мыши, но другого аккорда (маловероятно).
Трекер привязывает ожидание к устройству, клавише и времени.

Несколько pending на устройство нужны, если пользователь быстро нажал обе боковые кнопки:
`C↓` и `V↓` могут идти почти одновременно, а их UP — с разным порядком. Одна запись
перезаписала бы другую, и один UP ушёл бы в приложение сиротой.

`expire` вызывается на каждом событии: проверка нескольких pending (на практике ≤ 4) —
дешёвая. Если UP потерян (мышь отключилась), ожидание исчезает, ничего не залипает.
`resetModifiers` при выключении перехвата очищает модификаторы, но **не** pending: уже
поглощённый DOWN должен получить парный UP. `resetAll` — только при переподключении
службы, когда события всё равно больше не приходят.

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
            ActionCode.SCREENSHOT -> GLOBAL_ACTION_SCREENSHOT
            ActionCode.NONE -> return true
        }
        return service.performGlobalAction(globalAction)
    }
}
```

- Вызывается на главном потоке (так требует `performGlobalAction`).
- `false` → запись в диагностику, без исключения и без повторной попытки.
- Для `SCREENSHOT` на API 34+ используется актуальная константа, на 29–33 — старая
  (детали в D-010).
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
  умолчанию. Файл не перезаписывается автоматически: в UI есть «Сбросить настройки».
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

**Идемпотентность.** `onServiceConnected` может вызываться повторно (переподключение).
Инициализация репозитория и подписка защищены флагом; трекер сбрасывается.

---

## 12. UI

Одна Activity, Compose, без navigation-compose. Экран — `enum class Screen`, состояние —
`rememberSaveable`. Системная кнопка «назад» обрабатывается через `BackHandler`
(возврат на Home).

| Экран | Содержимое |
|---|---|
| **Home** | Статус службы (подключена/нет, флаг принят/нет), мастер-переключатель, активный профиль, краткая сводка маппингов, предупреждение о побочном эффекте |
| **Devices** | Список мышей: имя, `descriptor` (сокращённо), vendor/product, чекбокс «перехватывать». Неподключённые цели — серым. Предупреждение об одинаковых мышах |
| **Mappings** | Маппинги активного профиля: аккорд → действие, устройство, вкл/выкл. «Добавить» → режим захвата |
| **Profiles** | Список профилей, создание/переименование/удаление, выбор активного; привязки приложений (M3) |
| **Diagnostics** | Журнал решений, статус службы, `appliedFlags`, история `deviceId` по `descriptor`, кнопка «Сбросить журнал» |
| **About** | Версия, ограничения (нет «вперёд», обычные кнопки не ремапятся), ссылка в настройки специальных возможностей |

### 12.1 Мастер-переключатель

- Крупный, с текстом последствий: «Пока перехват включён, Ctrl+C / Ctrl+V от этой мыши
  не копируют и не вставляют».
- Тумблер **недоступен**, если служба не подключена или `filterApplied=false`; рядом —
  причина и ссылка в настройки специальных возможностей.
- При включении — диалог подтверждения (один раз за сессию).
- При выключении — мгновенно: `engine.setArmed(false)` + обновление состояния UI.
- Статус рядом: «служба подключена», «флаг фильтра принят», «цель выбрана».

### 12.2 Режим захвата аккорда

1. Пользователь нажимает «Добавить действие».
2. `CaptureController` слушает `KeyInput` от целевых устройств (не поглощает).
3. Как только приходит не-модификатор DOWN, фиксируется `Chord` (модификаторы + keyCode).
4. Диалог: «Найдено: Ctrl+C. Какое действие?» → выбор `ActionCode` → сохранение.
5. Отмена — ничего не меняется.

Во время захвата штатное действие аккорда (копирование/вставка) может сработать. Это
ожидаемо и написано в подсказке.

---

## 13. Потоки и производительность

| Контекст | Что делает | Требования |
|---|---|---|
| Main (служба) | `onKeyEvent`, `performGlobalAction`, `onAccessibilityEvent` | < 1 мс на событие, без I/O |
| Default (корутина службы) | сбор DataStore, построение `RuntimeConfig` | может быть медленным |
| Main (UI) | Compose, клики | без блокировок |
| Default/IO (репозиторий) | запись DataStore, JSON | вне главного |

Правила:

- В горячем пути нет `runBlocking`, `synchronized`, `Mutex`, `DataStore`, `JSON`.
- `@Volatile`-ссылка на неизменяемый снимок — единственная синхронизация.
- Журнал диагностики — тоже `@Volatile`/`StateFlow`, запись не блокирует.
- `expire()` для pending — O(1).

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
| Смена профиля или выключение перехвата между DOWN и UP | UP всё равно гасится |
| Исключение в `onKeyEvent` | pass + `armed=false` + запись |
| Исключение в `performGlobalAction` | `ACTION_FAILED` + Toast, без падения и retry |
| Падение процесса / перезагрузка / обновление HyperOS | `armed=false`; при регрессии — честный статус |

---

## 15. Приватность и безопасность

- Нет разрешения `INTERNET`. Данные не покидают устройство.
- Нет доступа к содержимому окон (`canRetrieveWindowContent="false"`).
- Журнал — в памяти, не на диске; очищается при смерти процесса.
- В release журнал по умолчанию выключен (`diagnostics_enabled=false`), включается
  вручную.
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
| repeat | `Consume`, без повторного dispatch |
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

Решено и не обсуждается в v1: `armed` всегда выключается при старте (D-006); именованные
профили в v1, per-app — M3 (D-011); восемь глобальных действий (D-010); пакет
`com.mousemanager` подтверждён.
