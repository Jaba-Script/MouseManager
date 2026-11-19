# PROJECT_STATUS — Mouse Manager

> Единственный источник истины о состоянии. Обновляется после каждой задачи.
> Дата: **2026-11-20**.

## 1. Кратко

| Параметр | Значение |
|---|---|
| Фаза | **M1 принята** 2026-11-13; **M2.3 завершён** (M2.3.1–M2.3.4 опубликованы, M2.3.4 — `f7eaaa2`); **M2.4 реализован и закоммичен** (`2a6ccf6`, документация — `46b5f4e`); **исправление A** (`de5e203`) прошло device-check 2026-11-19 (3 PASS); **device-check M2.4 по п. 14 §16.2 пройден 2026-11-20** (переподключение службы, OFF после него, 5/5 действий при OFF, два `PASSED_NO_MAPPING`, ошибок нет); **M2.4 не объявлен полностью принятым**: на устройстве не проверены перезапуск процесса (`armed=false` после старта) и пустые `targets` (`PASSED_NO_TARGETS`); проблема B открыта; далее — **M2.5** после этих двух проверок |
| Следующий шаг | **M2.4 — оставшиеся критерии на устройстве**: перезапуск процесса (`armed=false` после старта) и пустые `targets` (`PASSED_NO_TARGETS`), по отдельному подтверждению; затем M2.5 |
| HEAD | `d885a50` «docs: sync status after fix commit» (`main` = `origin/main`); исправление A — `de5e203`; P3 — `a9a096b` |
| M0 | Завершён 2026-11-11 (этапы 1–7), итоги — D-023 |
| M1 | **Принята на устройстве 2026-11-13** (S8): 13 PASS, 1 SKIP (физическая клавиатура); S1–S8 завершены (D-024, D-025) |
| Сборка / тесты | `:app:testDebugUnitTest --rerun-tasks` — **220 тестов в 19 suites**, 0 failures / 0 errors / 0 skipped (полный прогон 2026-11-19 на `d885a50`). Наборы: `ServiceBridgeTest` 15, `DisarmGuardTest` 5, `HomeMasterStateTest` 12; `ServiceStateTest` 9, `KeyEventHandlerTest` 17. `:app:assembleDebug` — BUILD SUCCESSFUL 2026-11-19; APK `app-debug.apk` 31 422 268 байт (29,967 MiB), SHA-256 `de417b2b7291e97764078da9019905d1789771f51796083b5404ede5cb95fead`, `com.mousemanager` 0.1.0 (versionCode 1) |
| UI | 4 экрана S7: Home, Devices, Diagnostics, About; навигация без `navigation-compose` (§12); Home получил рабочий мастер-переключатель (M2.4) |
| Мастер-переключатель | Реализован в M2.4 (D-031): активен только при выполненном гейте, включение — после диалога, выключение — сразу; готовность конфига приходит наблюдаемым `ServiceStatus.configGeneration`; `armed` не хранится и сбрасывается при старте процесса и подключении службы (D-006) |
| Профили | Именованные + ручное переключение в v1; per-app — M3 (D-011); UI — M2 |
| Действия v1 | 8 глобальных (D-010); «вперёд» нет, только S3 (D-021) |
| Пакеты | продукт `com.mousemanager` (корень) · S1 `…diagnostics` · S2 `…spike.s2` (архив `spikes/`) |
| Устройство | Xiaomi Pad 6 Pro `23046RP50C` (`liuqin`), Android 15 / API 35, HyperOS `OS3.0.7.0.VMYCNXM`, 1800×2880, 400 dpi, ландшафт |
| На планшете | продукт 0.1.0 сборки `d885a50` установлен 2026-11-19 (`adb install -r`, данные сохранены); device-check исправления A — **3 PASS** (2026-11-19), device-check M2.4 по п. 14 §16.2 — **PASS** (2026-11-20); ручная приёмка S7 — PASS; S1 и S2 установлены, **S2 выключена** (D-025) |

## 2. Проверено на устройстве

Полные измерения, обстоятельства и хэши — `docs/SPIKES.md`. Коротко:

1. Боковые кнопки VXE R1SE+ — аккорд `Ctrl+C` / `Ctrl+V` от устройства мыши, а не `BUTTON_BACK/FORWARD`.
2. `descriptor` мыши стабилен (`ce5035b0…3992`), `deviceId` меняется — привязка только по `descriptor`.
3. `FLAG_REQUEST_FILTER_KEY_EVENTS` работает на HyperOS 3: `flags=0x20`, клавиши реально гасятся.
4. `adb shell input` фильтру не виден → автотестов перехвата нет, только живая мышь.
5. Клавиши чужих устройств проходят: `gpio-keys`/`pmic_resin` → `VOLUME_UP/DOWN`, громкость 0 → 10.
6. Средняя кнопка — `ACTION_BUTTON_PRESS` + `BUTTON_TERTIARY`, мимо `onKeyEvent`.
7. `onMotionEvent` забирает события у системы → обычные кнопки не переназначаются (D-012).
8. Аккорд атомарный: 4 события за ~120–180 мс, «зажать кнопку» нельзя.
9. Перехват не переживает перезапуск процесса (S2: `armed` только в памяти).
10. S6: продуктовая служба подключается, `onServiceConnected flags=0x20 filterApplied=true`,
    `onKeyEvent` вызывается и всегда возвращает `false`.
11. S6: журнал `KeyRecord` пишет все проверенные события мыши (`dev=37/38`) как
    `PASSED_DISARMED`, `armed=false`, `isTarget=false`, `descriptor` заполнен; поглощения нет.
12. Задержка доставки клавиш в окно: 505–558 мс при включённой S2 и 6–28 мс при отключённой;
    ответ `onKeyEvent` — 2–18 мс (D-025).
13. В фоне колбэки откладываются и приходят пачкой при возврате приложения на передний план;
    у отложенных событий устройство уже отключено → `descriptor=null`, `isTarget=false`
    (fail-safe pass).
14. S8: APK 0.1.0 установлен через `adb install -r`; запуск без сбоев, данные предыдущей
    установки сохранены.
15. S8: Home — служба подключена, фильтр клавиш применён, переход в Accessibility Settings
    работает; master switch неактивен; сводка конфигурации корректна.
16. S8: Devices — VXE R1SE+ (`25a7:fa6c`) с `descriptor` исследованного устройства; выбор,
    сохранение после полного перезапуска и восстановление после переподключения работают.
17. S8: Diagnostics — реальные события `Ctrl+C`/`Ctrl+V` как клавиатурные, новые записи
    сверху, DOWN/UP, `scanCode`, `metaState`, `PASSED_DISARMED`; очистка работает.
18. S8: About, `NavigationRail`/`NavigationBar` (включая split-screen), портрет/ландшафт,
    светлая/тёмная темы — PASS. Физическая клавиатура — SKIP (оборудования нет).
19. M2.4, исправление A (2026-11-19): после подключения Accessibility Service сообщение
    «Служба ещё не готова к перехвату» исчезло само, без смены вкладки; Master Switch
    включился через диалог подтверждения и выключился. Проверка выполнялась без
    удаления приложения из Recents.
20. M2.4, device-check по п. 14 §16.2 (2026-11-20): переподключение Accessibility
    Service — PASS; после переподключения Master Switch остался OFF — PASS; при OFF
    обычные действия мыши — **5/5 PASS**; при `armed=true` и выбранном target получены
    две записи **`PASSED_NO_MAPPING`** (`Ctrl+C`, `Ctrl+V`). В логах **48 записей**:
    `PASSED_DISARMED` 40, `PASSED_MODIFIER` 4, `PASSED_KEY_UP` 2, `PASSED_NO_MAPPING` 2,
    `CONSUMED` 0. Ошибок `PASSED_ERROR`/`ACTION_FAILED` и сообщений `W`/`E` с тегом
    `MouseManager` нет. Отсутствие `CONSUMED` ожидаемо: mappings пока нельзя создать в UI
    (M2.6). Проверка выполнялась без удаления приложения из Recents.

## 3. Окружение и сборка

| Что | Значение |
|---|---|
| ОС / JDK | Windows, JDK 17 (Temurin 17.0.21); Android Studio нет |
| Gradle / AGP / Kotlin | wrapper 8.14.6 · AGP 8.13.2 · Kotlin 2.3.21 + compose plugin |
| Compose / activity | BOM 2026.06.01 · `activity-compose` 1.13.0 |
| SDK | `$env:ANDROID_HOME` (platform 36, build-tools 36.1.0); compileSdk 36 / targetSdk 35 / minSdk 29 |
| adb | `$env:ANDROID_HOME\platform-tools\adb.exe` |
| DataStore | `androidx.datastore:datastore-preferences:1.3.0` (D-020) |
| JSON в тестах | `org.json:json:20240303`, только `testImplementation` (D-024) |
| Git | 2.46.0; локально `core.autocrlf=false`; identity `Jaba-Script <178173545+Jaba-Script@users.noreply.github.com>` |

```powershell
# сборка продукта
Set-Location "<путь-к-репозиторию>"; .\gradlew.bat :app:assembleDebug --no-daemon --console=plain
# установка — только с разрешения пользователя
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
# лог (единый тег) и скриншот
& "$env:ANDROID_HOME\platform-tools\adb.exe" logcat -d -s MouseManager:D
& "$env:ANDROID_HOME\platform-tools\adb.exe" exec-out screencap -p > shot.png
```

Спайки собираются отдельно, каждый из своего каталога (`spikes/s1`, `spikes/s2`):
в корневую сборку не входят (`settings.gradle.kts` содержит только `include(":app")`).

## 4. Структура

```
<repo>\   ← корень репозитория и проект продукта
  app\                      ← продукт com.mousemanager: core, data, devices, diagnostics, service, ui
  docs\                     ← документация (вход — AGENTS.md)
  spikes\s1\, spikes\s2\    ← замороженный архив доказательств, отдельные сборки
  gradlew, gradlew.bat, gradle\wrapper\   ← wrapper 8.14.6
  local.properties          ← sdk.dir=<путь-к-Android-SDK>, не коммитится
```

Резервная копия до M0: `<repo-parent>/<repo-name>-backup-<YYYYMMDD-HHMMSS>` (67 файлов,
`_manifest-sha256.csv`, APK в `_artifacts/`) — не удаляется.

## 5. Не проверено и риски

| Вопрос / риск | Как закрывать |
|---|---|
| Физическая клавиатура: буквы, модификаторы, IME, сочетания | SKIP в S8 (клавиатуры нет); чек-лист `ARCHITECTURE.md` §16.2 п. 13 |
| Copy/paste при боковых кнопках в приложении-получателе | в S8 отдельно не фиксировался; в M1 поглощения нет (`PASSED_DISARMED`); проверяется в M2.6 после первого маппинга |
| Все 12 `Decision` в UI не наблюдались | в M1 виден `PASSED_DISARMED`; остальные подписи покрыты JVM-тестом |
| Побочный эффект «голого» Ctrl | живой тест в M2.6 — после первого рабочего маппинга (в M2.4 маппингов ещё нет); D-005 не меняется без отдельного ADR |
| `performGlobalAction(LOCK_SCREEN/SCREENSHOT)` на HyperOS | живой тест в M2.8; до проверки поддержку не обещать (D-010) |
| `armed` включается только тумблером | M2.4: гейт `connected && filterApplied && configGeneration > 0 && engine`; окончательное включение дополнительно проверяет `engine.config.generation`; без маппингов ничего не гасится (`EMPTY_TARGETS`/`NO_MAPPING`); после переподключения — `false` |
| Проблема B: служба выключается после закрытия приложения | **Открыто, не исправлялось.** Подтверждено логами: `force-stop` пакета с причиной `SwipeUpClean` (HyperOS `ProcessSceneCleaner`), после чего `enabled_accessibility_services=null` и `accessibility_enabled=0`. **Не установлено**, кто и почему очистил системные настройки и была ли очистка непосредственным следствием `force-stop`. Master Switch при этом корректно `false` (D-006). Device-check исправления A (2026-11-19) и M2.4 (2026-11-20) выполнялись без удаления приложения из Recents; отключение службы после удаления из Recents повторно не проверялось. Нужен отдельный шаг: детекция выключенной службы при старте и путь восстановления; device-check должен это учитывать |
| Гейт закрылся во время диалога подтверждения (P2) | включение отклоняется без отдельного сообщения; тумблер остаётся выключенным, причина обновляется; отдельная подсказка — потенциальное улучшение |
| Lifecycle службы не покрыт JVM-тестами (P3) | `onServiceConnected`, `stopSession`, планирование `disarmIfEmpty` проверяются вручную по п. 14 §16.2; чистая логика токена — `DisarmGuardTest` |
| `stopSession` выключает текущий `EngineHolder.engine` (P3) | identity-проверка защищает только `ServiceState.onStopped`; при штатном порядке Android безопасно; ownership-проверка — потенциальное улучшение |
| Поведение при выключенном экране | живой тест |
| Две одинаковые мыши → один `descriptor` | ограничение; предупреждение в UI |
| DataStore повреждён/медленный | fail-safe (D-018), unit-тесты кодека |
| Обновление HyperOS меняет фильтр | проверка `flags` при подключении, честный статус |
| Другая Accessibility-служба с фильтром клавиш включена | не запускать одновременно: +~500 мс к доставке (D-025) |
| Отложенные в фоне колбэки: `descriptor` уже недоступен | открытый риск: проверка в M2.8; при `descriptor=null` — fail-safe pass (D-018) |
| Фоновая доставка колбэков пачкой | открытый риск: проверить в M2.8; foreground-служба или исключение из оптимизации батареи — отдельное решение |
| Забытый включённый перехват | `armed` не сохраняется (D-006), крупный статус, panic tile |

## 6. Открытые вопросы

1. **M2** — выполняется: **M2.3.1 завершён** 2026-11-16 (D-027, `DecisionMapper`, 12 `Decision`; коммит `7c27b80`), **M2.3.2 завершён и опубликован** 2026-11-16 (`ActionDispatcher`, `GlobalActionDispatcher`, D-028; 154 теста, коммит `3c8b40f`), **M2.3.3 завершён и опубликован** 2026-11-16 (`EngineHolder`, D-029; 165 тестов, коммит `898d124`), **M2.3.4 завершён и опубликован** 2026-11-17 (`KeyEventHandler`, интеграция службы, D-030; **180 тестов в 16 suites, 0 failures / 0 errors / 0 skipped**; коммит `f7eaaa2`); M2.3 закрыт. **M2.4 реализован и закоммичен** 2026-11-17 (оба кодовых блока: `ServiceBridge`, `DisarmGuard`, зеркало `armed`, `HomeMasterState`, Home; коммиты `2a6ccf6` и `46b5f4e` опубликованы). **Исправление A** (наблюдаемая готовность конфигурации: `ServiceStatus.configGeneration`) внесено 2026-11-18, закоммичено (`de5e203`) и опубликовано (`d885a50`) — **220 тестов в 19 suites, 0 failures / 0 errors / 0 skipped**; **device-check 2026-11-19 — 3 PASS** (готовность конфига появляется без смены вкладки; включение через диалог; выключение). **M2.4 не объявлен полностью принятым**: device-check по п. 14 §16.2 пройден 2026-11-20 (переподключение службы, `armed=false` после него, 5/5 действий при OFF, два `PASSED_NO_MAPPING`, ошибок нет), но на устройстве не проверены перезапуск процесса (`armed=false` после старта) и пустые `targets` (`PASSED_NO_TARGETS`). Проблема B (служба выключается после `SwipeUpClean`) остаётся открытой. Далее — эти две проверки, затем **M2.5**. Этапы M2.1–M2.8 — `ROADMAP.md`, технические уточнения — `ARCHITECTURE.md` §6–§7, §11 и D-026–D-031.
2. **Panic tile** — нужен ли Quick Settings tile для мгновенного отключения (M4)?
3. **S1/S2 на планшете** — S2 выключена (D-025); оставить как архив или удалить после M1?
4. **Диагностика в release** — нужен ли экспорт журнала в файл?
5. **«Вперёд»** — только после спайка S3 (D-021).
6. **Фоновая доставка** — нужна ли foreground-служба или исключение из оптимизации батареи? Открытый риск; проверка в M2.8.

## 7. Журнал

| Дата | Событие |
|---|---|
| 2026-11-11 | M0 этапы 1–7: backup, baseline `ecceb12`, архив S1/S2, скелет продукта, reorg `d6c72a3`; **M0 завершён** (D-023) |
| 2026-11-12 | Документация сжата: состояние — здесь, доказательства — `SPIKES.md`, runbook M0 — `archive/` |
| 2026-11-12 | M1 S1–S6: ядро, конфиг, диагностика, каталог, `MouseManagerApp`, служба; 45 JVM-тестов, debug-APK собран |
| 2026-11-13 | **S6 принята** на устройстве: служба и фильтр работают, журнал `PASSED_DISARMED`, поглощения нет; задержка 6–28 мс без S2 (D-025) |
| 2026-11-13 | **S7 завершён**: 4 экрана (Home, Devices, Diagnostics, About), навигация `Screen` + `rememberSaveable` + `BackHandler`, Rail/Bar по 600dp; 61 JVM-тест, debug-APK собран |
| 2026-11-13 | **S8.1**: APK 0.1.0 установлен на планшет (`adb install -r`), запуск без сбоев, данные предыдущей установки сохранены |
| 2026-11-13 | **S8 (S8.2–S8.9)**: ручная приёмка S7 — 13 PASS, 1 SKIP (физическая клавиатура), 0 FAIL; **M1 принята** (`docs/S7-DEVICE-CHECKLIST.md`) |
| 2026-11-14 | **P3 завершён**: первый публичный коммит `a9a096b` — README, MIT `LICENSE`, `.gitattributes`, дополнен `.gitignore`; из файлов убраны личные данные (синтетический `descriptor` в тестах, без ADB serial, переносимые пути); `gradlew` → `100755`; 61 тест, 0 падений |
| 2026-11-15 | **M2.0.1–M2.0.3**: план M2 согласован; D-026 принят, уточнены D-005/D-018, исправлены D-001/D-010 (SCREENSHOT); обновлены `ARCHITECTURE.md` (§6, §6.1, §7, §11–§14, §16, §19), `ROADMAP.md` (M2.1–M2.8, оценка 6–9), `AGENTS.md`; код, Gradle и manifest не менялись |
| 2026-11-15 | **Commit M2.0.3**: `bbee9c4` «docs: finalize M2 implementation plan» — 5 документов, +294/−81; код, Gradle и manifest не менялись |
| 2026-11-15 | **M2.1 завершён**: `core/KeyCodes.kt` (действия, коды модификаторов, `MODIFIER_KEYCODES`, `modsOf`), `core/ChordTracker.kt` (pending `descriptor → keyCode`, скользящее окно 1500 мс, монотонный `expire`, `resetModifiers`/`resetAll`); тесты `KeyCodesTest` (8) и `ChordTrackerTest` (19); `:app:testDebugUnitTest` — **88 тестов в 11 suites, 0 failures / 0 errors / 0 skipped**; служба и `RemapEngine` не тронуты |
| 2026-11-15 | **Commit M2.1**: `18bc452` «feat: add key codes and chord tracker» — 4 файла кода/тестов + 2 документа, +605/−8; 88 тестов зелёные; опубликован на `origin/main` |
| 2026-11-15 | **M2.2 завершён**: `core/RemapEngine.kt` (строгий порядок §6: pending UP/repeat до `armed`/`targets`, скользящее окно 1500 мс, repeat только при pending, `NONE` — обычный маппинг), `RuntimeConfig.resolve` (§8: привязка приложения → активный → default; внутри профиля descriptor → `ANY_DEVICE`); тесты `RemapEngineTest` (38) и `RuntimeConfigTest` (13); `:app:testDebugUnitTest` — **132 теста в 12 suites, 0 failures / 0 errors / 0 skipped**; служба не тронута, dispatch не добавлен |
| 2026-11-16 | **M2.3.1 завершён**: `diagnostics/DecisionMapper.kt`, 4 новых `Decision` (`PASSED_NO_DESCRIPTOR`, `PASSED_NO_TARGETS`, `PASSED_KEY_UP`, `PASSED_REPEAT`), D-027; 138 тестов в 13 suites, 0 падений; коммит `7c27b80` «feat: map pass reasons to diagnostic decisions» — 8 файлов, +240/−12; опубликован на `origin/main` |
| 2026-11-16 | **M2.3.2 завершён и опубликован**: `core/ActionDispatcher.kt`, `service/GlobalActionDispatcher.kt` (маппинг 8 `GLOBAL_ACTION_*`, `NONE`, rate limit 2000 мс), `GlobalActionDispatcherTest` (16 тестов), D-028; `:app:testDebugUnitTest` — **154 теста в 14 suites, 0 failures / 0 errors / 0 skipped**; коммит `3c8b40f` «feat: add global action dispatcher» — 6 файлов, +580/−28; опубликован на `origin/main` |
| 2026-11-16 | **M2.3.3 завершён и опубликован**: `service/EngineHolder.kt` (process-wide `AtomicReference`, last-attach-wins, identity-detach, без хранения состояния движка), `EngineHolderTest` (11 тестов), D-029; `:app:testDebugUnitTest` — **165 тестов в 15 suites, 0 failures / 0 errors / 0 skipped**; коммит `898d124` «feat: add process-wide engine holder» |
| 2026-11-16 | **M2.3.4 реализован**: `service/KeyEventHandler.kt` (порядок `CONSUMED → dispatch → ACTION_FAILED`, изоляция диагностики, `armedAtDecision`), `KeyEventHandlerTest` (15 тестов), интеграция службы (`EngineHolder`, `GlobalActionDispatcher`, `DecisionMapper`, config generation, foreground package, `stopSession`), D-030 |
| 2026-11-17 | **M2.3.4 прошёл JVM-проверку**: `:app:testDebugUnitTest` — **180 тестов в 16 suites, 0 failures / 0 errors / 0 skipped**; `KeyEventHandlerTest` — 15/15; коммит `f7eaaa2` «feat: add key event handler and service integration» — 8 файлов, +1039/−129; опубликован на `origin/main` |
| 2026-11-17 | **M2.4, кодовый блок 1**: `ServiceState.armed` и `onArmed`, `ServiceBridge` (гейт, выключение всегда, ошибки → `armed=false`), `DisarmGuard`, интеграция службы (все disarm через мост, attach до disarm, токен сессии в `disarmIfEmpty`), disarm-callback в `KeyEventHandler`; тесты `ServiceBridgeTest` 14, `DisarmGuardTest` 5, `ServiceStateTest` +3, `KeyEventHandlerTest` +2 |
| 2026-11-17 | **M2.4, кодовый блок 2**: `HomeMasterState` (доступность, причина, подтверждение), `HomeScreen` (тумблер через `ServiceBridge`, `AlertDialog`, мгновенное выключение), `strings.xml`; `HomeMasterStateTest` 10; всего **214 тестов в 19 suites, 0 failures / 0 errors / 0 skipped** |
| 2026-11-17 | **M2.4, документация синхронизирована**: D-031 (реализация, проверки, замечания), `ARCHITECTURE.md` §11/§12.1/§16, этот файл и `ROADMAP.md`; код и тесты не менялись |
| 2026-11-17 | **M2.4 закоммичен**: `2a6ccf6` «feat: implement M2.4 master switch and home» — 17 файлов, +1312/−112; 214 JVM-тестов зелёные; коммит не опубликован (`origin/main` — `f7eaaa2`) |
| 2026-11-18 | **Диагностика A/B (read-only, без изменений)**: A — `ServiceState.onConnected` публикуется до записи `engine.config`, `canArm()` читал ненаблюдаемое `engine.config.generation` → ложный `ENGINE_UNAVAILABLE` до смены вкладки; B — в логах наблюдался `force-stop` пакета с причиной `SwipeUpClean` (HyperOS `ProcessSceneCleaner`), после чего `enabled_accessibility_services=null`, `accessibility_enabled=0`; кто и почему очистил настройки и было ли это следствием `force-stop`, не установлено; кода отключения службы в приложении нет, B оставлена открытой |
| 2026-11-18 | **Исправление A**: `ServiceStatus.configGeneration` + `ServiceState.onConfigApplied`, публикация в `applyConfigNow` после `engine.config`; `canArm()` читает наблюдаемое зеркало, `setArmed(true)` сохраняет проверку `engine.config.generation`; `homeMasterState(status, error, canArm)` определяет `CONFIG_LOADING` по зеркалу. `:app:testDebugUnitTest` — **220 тестов в 19 suites, 0 failures / 0 errors / 0 skipped** (+6); исправление закоммичено `de5e203` (на 2026-11-18 не опубликовано и на устройстве не проверялось; позже опубликовано с `d885a50`, device-check — 2026-11-19) |
| 2026-11-18 | **Документация исправления A синхронизирована**: D-031 (п. 31.4, 31.14, реализация, проверки), `ARCHITECTURE.md` §11/§12.1/§16.1, этот файл и `ROADMAP.md`; код и тесты не менялись, ADB не использовался |
| 2026-11-19 | **Полная проверка после публикации**: HEAD = `origin/main` = `d885a50`; `:app:testDebugUnitTest --rerun-tasks` — **220 тестов в 19 suites, 0 failures / 0 errors / 0 skipped**; `:app:assembleDebug` — BUILD SUCCESSFUL; APK `app-debug.apk` 31 422 268 байт (29,967 MiB), SHA-256 `de417b2b7291e97764078da9019905d1789771f51796083b5404ede5cb95fead`, `com.mousemanager` 0.1.0 (versionCode 1), compileSdk 36 / targetSdk 35 / minSdk 29 |
| 2026-11-19 | **APK установлен на планшет**: Xiaomi Pad 6 Pro (`23046RP50C`, Android 15 / API 35, HyperOS `OS3.0.7.0.VMYCNXM`), `adb install -r` → Success; `dumpsys package` подтвердил versionName 0.1.0 и versionCode 1, данные предыдущей установки сохранены |
| 2026-11-19 | **Device-check исправления A — 3 PASS**: (1) после подключения Accessibility Service сообщение «Служба ещё не готова к перехвату» исчезло самостоятельно, без смены вкладки; (2) Master Switch включился через диалог подтверждения; (3) Master Switch выключился. Проверка выполнялась без удаления приложения из Recents. Остальные пункты п. 14 §16.2 не выполнялись — **M2.4 не принят**; проблема B повторно не исследовалась и остаётся открытой |
| 2026-11-20 | **Device-check M2.4 по п. 14 §16.2 — PASS**: переподключение Accessibility Service; после переподключения Master Switch остался OFF; при OFF обычные действия мыши — 5/5 PASS; при `armed=true` и выбранном target — две записи `PASSED_NO_MAPPING` (`Ctrl+C`, `Ctrl+V`). В логах 48 записей: `PASSED_DISARMED` 40, `PASSED_MODIFIER` 4, `PASSED_KEY_UP` 2, `PASSED_NO_MAPPING` 2, `CONSUMED` 0; ошибок `PASSED_ERROR`/`ACTION_FAILED` и `W`/`E` с тегом `MouseManager` нет. `CONSUMED` 0 ожидаемо: mappings в UI пока нельзя создать (M2.6). Проверка без удаления приложения из Recents; проблема B повторно не проверялась. **M2.4 не объявлен полностью принятым**: не проверены перезапуск процесса (`armed=false`) и пустые `targets` |
| — | Следующий шаг — оставшиеся критерии M2.4 на устройстве (перезапуск процесса, пустые `targets`), затем M2.5; проблема B остаётся открытой |

История M0 по этапам — D-023 и `docs/archive/M0-runbook.md` §3.
