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
| Клиенты | список по версиям, установка (скачивание + sha256), запуск, экспорт инстанса, удаление |
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

## Данные

Манифест и клиенты берутся из репозитория `pyBIrtat/PsinaLauncher`
(зеркала: jsdelivr -> raw.githubusercontent -> релиз `v1`).
