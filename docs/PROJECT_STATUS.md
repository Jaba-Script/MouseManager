# PROJECT_STATUS — Mouse Manager

> Единственный источник истины о состоянии. Обновляется после каждой задачи.
> Дата: **2026-11-12**.

## 1. Кратко

| Параметр | Значение |
|---|---|
| Фаза | **M0 завершён**, реализация **M1 не начата** |
| Следующий шаг | **M1 — скелет приложения**: служба, DataStore, каталог устройств, статус. Старт — по отдельному подтверждению |
| HEAD | `d6c72a3` «reorg: product root, spikes archived»; до него `ecceb12` baseline |
| M0 | Завершён 2026-11-11 (этапы 1–7), итоги — D-023 |
| Мастер-переключатель | Всегда `false` при старте процесса и подключении службы (D-006) |
| Профили | Именованные + ручное переключение в v1; per-app — M3 (D-011) |
| Действия v1 | 8 глобальных (D-010); «вперёд» нет, только S3 (D-021) |
| Пакеты | продукт `com.mousemanager` (корень) · S1 `…diagnostics` · S2 `…spike.s2` (архив `spikes/`) |
| Устройство | Xiaomi Pad 6 Pro `23046RP50C` (`liuqin`), Android 15 / API 35, HyperOS `OS3.0.7.0.VMYCNXM`, 1800×2880, 400 dpi, ландшафт |
| На планшете | S1 и S2 установлены; служба S2 включена, перехват **выключен** |

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

## 3. Окружение и сборка

| Что | Значение |
|---|---|
| ОС / JDK | Windows, JDK 17 (Temurin 17.0.21); Android Studio нет |
| Gradle / AGP / Kotlin | wrapper 8.14.6 · AGP 8.13.2 · Kotlin 2.3.21 + compose plugin |
| Compose / activity | BOM 2026.06.01 · `activity-compose` 1.13.0 |
| SDK | `D:\Android\Sdk` (platform 36, build-tools 36.1.0); compileSdk 36 / targetSdk 35 / minSdk 29 |
| adb | `D:\Android\Sdk\platform-tools\adb.exe` |
| DataStore (план) | `androidx.datastore:datastore-preferences:1.3.0` (`minCompileSdk=34`, `minAGP=8.1.1`) |
| Git | 2.46.0; локально `core.autocrlf=false`; identity `Jaba-Script <178173545+Jaba-Script@users.noreply.github.com>` |

```powershell
# сборка продукта
Set-Location D:\Projects\MouseManager; .\gradlew.bat :app:assembleDebug --no-daemon --console=plain
# установка — только с разрешения пользователя
& "D:\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
# лог (единый тег) и скриншот
& "D:\Android\Sdk\platform-tools\adb.exe" logcat -d -s MouseManager:D
& "D:\Android\Sdk\platform-tools\adb.exe" exec-out screencap -p > shot.png
```

Спайки собираются отдельно, каждый из своего каталога (`spikes/s1`, `spikes/s2`):
в корневую сборку не входят (`settings.gradle.kts` содержит только `include(":app")`).

## 4. Структура

```
D:\Projects\MouseManager\   ← корень репозитория и проект продукта
  app\                      ← продукт com.mousemanager (скелет M0, 7 файлов)
  docs\                     ← документация (вход — AGENTS.md)
  spikes\s1\, spikes\s2\    ← замороженный архив доказательств, отдельные сборки
  gradlew, gradlew.bat, gradle\wrapper\   ← wrapper 8.14.6
  local.properties          ← sdk.dir=D:/Android/Sdk, не коммитится
```

Резервная копия до M0: `D:\Projects\MouseManager-backup-20261111-135022` (67 файлов,
`_manifest-sha256.csv`, APK в `_artifacts/`) — не удаляется.

## 5. Не проверено и риски

| Вопрос / риск | Как закрывать |
|---|---|
| Полноценная клавиатура: буквы, модификаторы, IME, сочетания | чек-лист `ARCHITECTURE.md` §16.2 п. 13, когда появится железо |
| Побочный эффект «голого» Ctrl | живой тест в M2: браузер, редактор, IME, игры |
| `performGlobalAction(LOCK_SCREEN/SCREENSHOT)` на HyperOS | живой тест в M2 |
| Поведение при выключенном экране | живой тест |
| Две одинаковые мыши → один `descriptor` | ограничение; предупреждение в UI |
| DataStore повреждён/медленный | fail-safe (D-018), unit-тесты кодека |
| Обновление HyperOS меняет фильтр | проверка `flags` при подключении, честный статус |
| Забытый включённый перехват | `armed` не сохраняется (D-006), крупный статус, panic tile |

## 6. Открытые вопросы

1. **M1** — ждёт отдельного подтверждения на старт.
2. **Panic tile** — нужен ли Quick Settings tile для мгновенного отключения (M4)?
3. **S1/S2 на планшете** — оставить как архив или удалить после M1?
4. **Диагностика в release** — нужен ли экспорт журнала в файл?
5. **«Вперёд»** — только после спайка S3 (D-021).

## 7. Журнал

| Дата | Событие |
|---|---|
| 2026-11-11 | M0 этапы 1–7: backup, baseline `ecceb12`, архив S1/S2, скелет продукта, reorg `d6c72a3`; **M0 завершён** (D-023) |
| 2026-11-12 | Документация сжата: состояние — здесь, доказательства — `SPIKES.md`, runbook M0 — `archive/` |
| — | Следующий шаг — M1; реализация не начата |

История M0 по этапам — D-023 и `docs/archive/M0-runbook.md` §3.
