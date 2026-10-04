# Psina Mobile — свой Android-лаунчер

Свой мобильный лаунчер для клиентов Psina (1.21.4 / 1.21.11 / 26.2). **Без Pojav**:
приложение само хранит клиентов, готовит инстанс, ведёт редактор кнопок и сенсы,
пингует сервера, ищет моды на Modrinth — а запуск отдаёт установленному движку
Java-Minecraft на Android.

## Сборка

APK собирается в GitHub Actions (на раннерах есть Android SDK):

```
Actions -> "Build Psina Mobile APK" -> Run workflow
```

Результат: `psina-mobile-<N>.apk` в релизе **v1** и в артефактах CI.

Локально (нужен Android SDK + Gradle 8.9):

```
cd android
gradle test
gradle assembleRelease
```

Подпись берётся из `keystore/psina.jks`; пароли переопределяются переменными
`PSINA_KEYSTORE_PASSWORD`, `PSINA_KEY_ALIAS`, `PSINA_KEY_PASSWORD`.

## Что умеет

| Раздел | Возможности |
|---|---|
| Клиенты | список по версиям, метка совместимости (телефон / β / Только ПК), установка (скачивание + sha256), запуск, проверка jar, экспорт инстанса, удаление |
| Управление | редактор кнопок: перетаскивание, размер, прозрачность, toggle, привязка клавиш и спец-кнопок (мышь/клавиатура), сенса, раскладки, экспорт/импорт |
| Сервера | адреса, пинг, копирование, выбор активного |
| Моды | поиск Modrinth под версию (Fabric), установка в инстанс |
| Ещё | ник, ОЗУ, java-аргументы, статус движка, место на диске, URL манифеста, сайты, лог |

## Архитектура

```
app/src/main/java/ru/psina/mobile/
  PsinaApp.kt            инициализация путей/настроек/лога
  MainActivity.kt        таббар и переключение экранов
  LogoLoader.kt          логотипы клиентов (диск -> jsdelivr -> raw github)
  IconLoader.kt          иконки модов
  core/
    Paths.kt             каталоги: instances, downloads, exports, logs
    Prefs.kt             настройки (SharedPreferences)
    Logx.kt              лог в файл
    Net.kt               OkHttp + зеркала манифеста + sha256
    Store.kt             манифест, состояние установки, сервера, раскладки
    ManifestRepo.kt      парсер launcher-online.json
    Installer.kt         установка клиента (jar + extra + requires + fabric-api)
    Modrinth.kt          поиск модов и подбор файла под версию
    Engine.kt            поиск/запуск движка, шаринг инстанса
  controls/
    LayoutModel.kt       модель раскладки (fx/fy долями экрана)
    LayoutExport.kt      экспорт/импорт формата v8 (Pojav-совместимый)
  export/
    InstanceExporter.kt  zip инстанса: mods, controlmap, метаданные, инструкция
  ui/                    экраны и редактор кнопок
```

Раскладка кнопок экспортируется в формат **v8** — его читают Zalith / Amethyst /
Mojo / Pojav, поэтому настройки кнопок переносимы.

## Клиенты «только для ПК»

Часть клиентов на ПК — это портативки со своим Windows-рантаймом (`jre/`,
`natives/`, `launch-*.ps1`). Оказалось, что **сам клиент в них — обычный
Fabric-мод**, поэтому на телефоне он запускается, если взять только моды и
отдать их движку.

Это описывается необязательным блоком `android` в манифесте — ПК-лаунчер его
игнорирует, поэтому формат обратно совместим:

```json
"android": {
  "status": "experimental",
  "notes": "что именно не работает на телефоне",
  "modsFromZip": ["run/mods/meow-offline.jar", "run/mods/sodium-...jar"],
  "libsFromZip": ["runtime/compatibility/offline-compatibility.jar"],
  "modsExclude": ["voicechat-fabric"],
  "extraRemove": ["onnxruntime"],
  "stripNatives": true,
  "jvmArgs": ["-Ddeluxe.username={nick}"],
  "mainClass": null
}
```

| Поле | Смысл |
|---|---|
| `status` | `ok` / `experimental` / `pc` — что показывать в списке |
| `modsFromZip` | какие файлы вытащить из zip портативки в `mods/` |
| `libsFromZip` | что положить в classpath, но не в `mods/` (compat-хелперы вроде VMBridge) |
| `modsExclude` | что на телефоне не нужно (Windows-нативы, голосовой чат) |
| `extraRemove` | какие `extra` из манифеста не ставить (подстрокой: `dimasik-onnxruntime.jar`) |
| `stripNatives` | вырезать из jar встроенные `.dll/.exe/.ps1/.bat` и десктопные `.so` |
| `jvmArgs` | доп. аргументы; `{nick}` подставляется ником |
| `mainClass` | свой главный класс вместо KnotClient |

### Почему `stripNatives` работает

Внутри jar'ов ПК-клиентов лежат Windows-библиотеки (`discord-rpc.dll`,
`MediaPlayerInfo.dll`, `onnxruntime.dll`, `catboost*.dll`) и вспомогательные
`.ps1`-скрипты. Мод грузит их **лениво**, только когда вызывается конкретная
фича, поэтому на телефоне достаточно вырезать сами файлы: фича отвалится с
ошибкой в логе, а клиент стартует. `stripNatives` переписывает скачанный jar
на месте (заодно снимая подписи `META-INF/*.SF`), удаляя:

- `.dll`, `.exe`, `.ps1`, `.bat`, `.cmd`, `.dylib`, `.jnilib`;
- `.so`/`.dylib` из десктопных папок (`linux-*`, `osx-*`, `win*`, `x86*`),
  при этом Android-нативы (`lib/arm64-v8a/...`) остаются.

Если блока нет: портативка считается «Только ПК», обычный Fabric-клиент —
готовым к телефону.

Запуск: приложение собирает `-cp` из всех jar'ов инстанса (`mods/` +
`libraries/`) и передаёт движку строку аргументов вместе с `--gameDir`,
`--username` и офлайн-UUID. Без classpath движок запустил бы чистую игру и
клиента бы не увидел.

## Данные

Манифест и клиенты берутся из репозитория `pyBIrtat/PsinaLauncher`
(зеркала: jsdelivr -> raw.githubusercontent -> релиз `v1`).
