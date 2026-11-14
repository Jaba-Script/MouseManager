# M0 — runbook (архив)

> Исторический порядок выполнения M0. Решение и мотивы — D-022, итоги — D-023.
> Читать только при восстановлении из резервной копии или повторении процедуры.
> Пути в скриптах исторические: до M0 S1 лежал в корне (`app/`), S2 — в `spike-s2/`.

## 1. Метод проверки резервной копии

Проверка выполняется до любых перемещений. Любая ошибка чтения/копирования —
**остановка**, а не пропуск файла: иначе потеря файла останется незамеченной.
`README-restore.txt` и `_manifest-*` в сверку не входят (создаются после).
Оба манифеста сортируются по `Rel`; сравнение идёт строго по парам
`Rel` + `Size` + `Hash`. Для каждого манифеста проверяются непустота и формат SHA-256
(ровно 64 hex-символа): пустой список или некорректный хэш — ошибка, а не «совпадение».

```powershell
$ErrorActionPreference = 'Stop'
$src = '<repo>'
$dst = '<repo-parent>/<repo-name>-backup-YYYYMMDD-HHMMSS'
$skipDirs  = @('build', '.gradle', '.kotlin', '.idea', '_artifacts')
$skipNames = @('local.properties', 'README-restore.txt')

function Get-Manifest([string]$root) {
    if (-not (Test-Path -LiteralPath $root)) { throw "Нет каталога: $root" }
    $files = Get-ChildItem -LiteralPath $root -Recurse -File -Force -ErrorAction Stop |
        Where-Object {
            $_.Name -notin $skipNames -and $_.Name -notlike '_manifest-*' -and
            -not (@($_.FullName.Split([IO.Path]::DirectorySeparatorChar)) |
                   Where-Object { $skipDirs -contains $_ })
        }
    foreach ($f in $files) {
        try {
            [pscustomobject]@{
                Rel  = $f.FullName.Substring($root.Length + 1)
                Size = $f.Length
                Hash = (Get-FileHash -LiteralPath $f.FullName -Algorithm SHA256 -ErrorAction Stop).Hash
            }
        } catch {
            throw "Не удалось прочитать '$($f.FullName)': $($_.Exception.Message)"
        }
    }
}

robocopy $src $dst /E /XD build .gradle .kotlin .idea _artifacts /XF local.properties `
    /R:1 /W:1 /NFL /NDL /NP /LOG:"$dst.log"
if ($LASTEXITCODE -ge 8) { throw "robocopy завершился с кодом $LASTEXITCODE" }

$a = @(Get-Manifest $src | Sort-Object Rel)
$b = @(Get-Manifest $dst | Sort-Object Rel)
if ($a.Count -eq 0) { throw "Манифест источника пуст" }
if ($b.Count -eq 0) { throw "Манифест копии пуст" }
if ($a.Count -ne $b.Count) { throw "Файлов: источник $($a.Count), копия $($b.Count)" }
$badA = @($a | Where-Object { $_.Hash -notmatch '^[0-9A-F]{64}$' })
if ($badA.Count) { throw "Некорректный SHA-256 (источник): $($badA.Rel -join ', ')" }
$badB = @($b | Where-Object { $_.Hash -notmatch '^[0-9A-F]{64}$' })
if ($badB.Count) { throw "Некорректный SHA-256 (копия): $($badB.Rel -join ', ')" }
$diff = Compare-Object -ReferenceObject $a -DifferenceObject $b -Property Rel, Size, Hash -PassThru
if ($diff) { throw "Расхождения: $($diff.Rel -join ', ')" }
"OK: $($a.Count) файлов, размеры и SHA-256 совпадают"

$a | Sort-Object Rel | Export-Csv -LiteralPath "$dst\_manifest-sha256.csv" -NoTypeInformation -Encoding UTF8
```

Ожидается **67 / 67** файлов. APK проверяются отдельно, теми же правилами «упасть при
любой ошибке»:

```powershell
$pairs = @(
    @{ Src = "$src\app\build\outputs\apk\debug\app-debug.apk";
       Dst = "$dst\_artifacts\s1-app-debug.apk" },
    @{ Src = "$src\spike-s2\app\build\outputs\apk\debug\app-debug.apk";
       Dst = "$dst\_artifacts\s2-app-debug.apk" }
)
New-Item -ItemType Directory -Force -Path "$dst\_artifacts" | Out-Null
foreach ($p in $pairs) {
    if (-not (Test-Path -LiteralPath $p.Src)) { throw "APK не найден: $($p.Src)" }
    Copy-Item -LiteralPath $p.Src -Destination $p.Dst -Force -ErrorAction Stop
    $hs = (Get-FileHash -LiteralPath $p.Src -Algorithm SHA256 -ErrorAction Stop).Hash
    $hd = (Get-FileHash -LiteralPath $p.Dst -Algorithm SHA256 -ErrorAction Stop).Hash
    if ($hs -ne $hd) { throw "SHA-256 APK не совпали: $($p.Src)" }
    "OK APK: $($p.Src) = $hs"
}
```

Эталонные хэши APK — `docs/SPIKES.md` §6.

## 2. `README-restore.txt`

Содержание (создаётся вместе с копией, в сверку не входит): фактический путь резервной
копии и время создания; что скопировано; что не входит (`build/`, `.gradle/`, `.kotlin/`,
`.idea/`, `local.properties`); как восстановить — скопировать содержимое обратно, создать
`local.properties` с `sdk.dir=<путь-к-Android-SDK>` в корне (и, после M0, в `spikes\\s1`,
`spikes\\s2`), сверить файлы с `_manifest-sha256.csv`; кэши не восстанавливаются; APK в
`_artifacts/` — готовые доказательные артефакты S1/S2.

## 3. Порядок этапов

1. Резервная копия + `_manifest-sha256.csv` + APK в `_artifacts/` + `README-restore.txt`.
2. `git init`; локально `git config --local core.autocrlf false`; `.gitignore`; baseline
   commit `ecceb12` (67 файлов).
3. Копирование S1 → `spikes/s1/`, S2 → `spikes/s2/`; сверка SHA-256 (60/60).
4. Сборка обоих спайков после переноса.
5. Удаление дубликатов из корня (`app/`, `spike-s2/`) только после успешных проверок 3–4.
6. Создание скелета продукта в корне (`com.mousemanager`, 7 файлов).
7. Commit `d6c72a3` «reorg: product root, spikes archived»; проверка дерева (82 файла).
