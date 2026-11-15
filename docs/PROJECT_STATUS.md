# PROJECT_STATUS — Mouse Manager

> Единственный источник истины о состоянии. Обновляется после каждой задачи.
> Дата: **2026-11-15**.

## 1. Кратко

| Параметр | Значение |
|---|---|
| Фаза | **M1 принята** 2026-11-13; M2 запланирована (план M2.1–M2.8), реализация не начата |
| Следующий шаг | **M2.1 — `KeyCodes` и `ChordTracker`**; этапы M2.1–M2.8 — в `ROADMAP.md`; старт — по отдельному подтверждению |
| HEAD | `6a57486` «docs: update project status after M2.0.3» (текущая вершина `main`); P3 — `a9a096b` |
| M0 | Завершён 2026-11-11 (этапы 1–7), итоги — D-023 |
| M1 | **Принята на устройстве 2026-11-13** (S8): 13 PASS, 1 SKIP (физическая клавиатура); S1–S8 завершены (D-024, D-025) |
| Сборка / тесты | `:app:assembleDebug` и `:app:testDebugUnitTest` проходят; 61 JVM-тест в 9 suites, 0 падений (последняя сборка S7) |
| UI | 4 экрана S7: Home, Devices, Diagnostics, About; навигация без `navigation-compose` (§12) |
| Мастер-переключатель | В UI неактивен и выключен до M2; `armed` не хранится и всегда `false` при старте процесса и подключении службы (D-006) |
| Профили | Именованные + ручное переключение в v1; per-app — M3 (D-011); UI — M2 |
| Действия v1 | 8 глобальных (D-010); «вперёд» нет, только S3 (D-021) |
| Пакеты | продукт `com.mousemanager` (корень) · S1 `…diagnostics` · S2 `…spike.s2` (архив `spikes/`) |
| Устройство | Xiaomi Pad 6 Pro `23046RP50C` (`liuqin`), Android 15 / API 35, HyperOS `OS3.0.7.0.VMYCNXM`, 1800×2880, 400 dpi, ландшафт |
| На планшете | продукт 0.1.0 (S7) установлен 2026-11-13; ручная приёмка S7 — PASS; S1 и S2 установлены, **S2 выключена** (D-025) |

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
| Все 8 `Decision` в UI не наблюдались | в M1 виден `PASSED_DISARMED`; остальные подписи покрыты JVM-тестом |
| Побочный эффект «голого» Ctrl | живой тест в M2.6 — после первого рабочего маппинга (в M2.4 маппингов ещё нет); D-005 не меняется без отдельного ADR |
| `performGlobalAction(LOCK_SCREEN/SCREENSHOT)` на HyperOS | живой тест в M2.8; до проверки поддержку не обещать (D-010) |
| Поведение при выключенном экране | живой тест |
| Две одинаковые мыши → один `descriptor` | ограничение; предупреждение в UI |
| DataStore повреждён/медленный | fail-safe (D-018), unit-тесты кодека |
| Обновление HyperOS меняет фильтр | проверка `flags` при подключении, честный статус |
| Другая Accessibility-служба с фильтром клавиш включена | не запускать одновременно: +~500 мс к доставке (D-025) |
| Отложенные в фоне колбэки: `descriptor` уже недоступен | открытый риск: проверка в M2.8; при `descriptor=null` — fail-safe pass (D-018) |
| Фоновая доставка колбэков пачкой | открытый риск: проверить в M2.8; foreground-служба или исключение из оптимизации батареи — отдельное решение |
| Забытый включённый перехват | `armed` не сохраняется (D-006), крупный статус, panic tile |

## 6. Открытые вопросы

1. **M2** — план согласован (M2.0.1–M2.0.3): этапы M2.1–M2.8 в `ROADMAP.md`, технические уточнения — `ARCHITECTURE.md` §6 и D-026. Реализация не начата; ждёт отдельного подтверждения.
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
| — | Следующий шаг — M2.1 (`KeyCodes`, `ChordTracker`); старт — по отдельному подтверждению |

История M0 по этапам — D-023 и `docs/archive/M0-runbook.md` §3.
