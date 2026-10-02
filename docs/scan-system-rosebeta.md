# Разбор «System» и «RoseBeta» (2 октября 2026)

Цель: проверить на вирусы и решить, можно ли отдавать в лаунчер.
Метод: статический разбор Java-классов, разбор PE-файлов парсером `tools/pe-report.ps1`,
поиск строк по ключевым словам, проверка подписи Authenticode.

**Итог: `System` в лаунчер отдавать нельзя. `RoseBeta` — исходники, собираемые отдельно.**

---

## 1. System Ready1.21.11.jar

| | |
|---|---|
| Размер | 20 885 743 байт |
| sha256 | `31d38ec44e3e0463e05d689b0417970b4e96dcb2f27a50963cbece515239e37f` |
| fabric id | `mcgui` / «Minecraft GUI OneClick» |
| entrypoint | `gui.ClientEntry` |
| Файлов | 473 |

### 1.1 Java-часть — формально «чисто», но это дроппер

По профилям, по которым ранее была найдена слежка в Debuda, совпадений нет:

```
discord.com/api/webhooks -> 0      api.telegram.org -> 0
Runtime / ProcessBuilder -> 0      URLClassLoader   -> 0
hwid / accessToken       -> 0      leveldb / Login Data -> 0
```

Однако `gui.ClientEntry` делает ровно следующее:

```
java.io.tmpdir -> getProperty
"mcgui"        -> Files.createDirectories   => %TEMP%\mcgui
"PAYLOADS"     -> каталог ресурсов
getResourceAsStream -> FileOutputStream + write  => выгрузка payload на диск
sleep
client.Modules -> forName + getMethod + invoke   => рефлексия по строке
```

То есть клиент **выгружает вложенный payload из своих ресурсов в `%TEMP%\mcgui`** и
подгружает его рефлексией. Payload в jar — каталог `native/`:

```
native/ClientGUI.dll              27 361 280
native/MSVCP140.dll                  643 512
native/MSVCP140_ATOMIC_WAIT.dll       57 792
native/VCRUNTIME140.dll              178 616
native/VCRUNTIME140_1.dll             50 112
```

Четыре последних — легитимные Microsoft-распространяемые библиотеки (оттуда же все
«сертификаты Microsoft», которые находились поиском строк: `/pkiops/certs/...`,
`crl.microsoft.com`). Они не относятся к самому читу.

### 1.2 ClientGUI.dll — разобрана подробно

```
sha256      : 87495cae027bc7cf6dccb5af1c4eb50e7b4f278f8756b2e8c8ee4a0343ace255
machine     : x86-64, PE32+, 10 секций
timestamp   : 2026-09-25 17:46:37 UTC
subsystem   : CONSOLE (не GUI)
isDotNet    : false
подпись     : NotSigned (Authenticode отсутствует)
метаданные  : CompanyName / ProductName / FileVersion — пусто
```

Секции и энтропия:

```
  .text        4 117 504   энтропия 6.512
  .mmshell?        4 096   энтропия 4.830   <- нестандартное имя
  .rdata       9 203 200   энтропия 7.893
  .data          270 848   энтропия 4.248
  .pdata         195 072   энтропия 7.941
  .mmstate8          512   энтропия 0.452   <- нестандартная секция
  .vmp0       13 515 776   энтропия 7.100   <- VMProtect
  .rsrc              512   энтропия 4.734
  .reloc          28 672   энтропия 5.456
  .lablog          4 096   энтропия 0.450   <- нестандартная секция
```

**Секция `.vmp0` — это VMProtect.** 13.5 МБ кода виртуализировано/зашифровано:
статически этот код не анализируется ничем, включая антивирусы — в этом и смысл VMProtect.
Дополнительно два нестандартных имени секций (`.mmshell`, `.mmstate8`, `.lablog`) —
признак кастомной оболочки поверх.

Таблица импортов (23 DLL, 379 функций) — вскрыта и это самое показательное:

| Категория | Что импортирует |
|---|---|
| **Инъекции в процессы** | `CreateRemoteThread`, `VirtualAllocEx`, `WriteProcessMemory`, `OpenProcess`, `SetThreadContext` |
| **Захват экрана** | `PrintWindow`, `StretchBlt`, `SetStretchBltMode`, `CreateCompatibleDC`, `GetDC`, `GetSystemMetrics` |
| **Клавиатура** | `GetAsyncKeyState`, `GetKeyboardState`, `GetKeyState`, `MapVirtualKeyW` |
| **Сеть** | `InternetOpenA`, `InternetOpenUrlA`, `InternetReadFile`, `WS2_32.dll` (23 функции), `getaddrinfo` |
| **Идентификация машины** | `GetSystemFirmwareTable`, `DeviceIoControl`, `GetComputerNameA`, `GetUserNameA` |
| **Крипто** | 16 функций `bcrypt.dll`, включая `BCryptEncrypt`, `BCryptDecrypt`, `BCryptImportKeyPair`, `BCryptVerifySignature` |
| **JVM/JNI** | `jvm.dll`, `JNI_GetCreatedJavaVMs` |
| **Рендер** | `OPENGL32.dll` (30 функций), `wglGetProcAddress` |
| **Прочее** | `ShellExecuteA`, `CreateThread`, `IsDebuggerPresent`, `RegOpenKeyExA` |

Как это читать. Нативная OpenGL-отрисовка GUI **объясняет** `OPENGL32`, `wglGetProcAddress`,
`GetDC`, `CreateCompatibleDC`, `GetSystemMetrics`, а хоткеи — `GetAsyncKeyState`/`GetKeyState`.
Но **ничем не объясняются** `CreateRemoteThread` + `VirtualAllocEx` + `WriteProcessMemory` +
`SetThreadContext`: чтобы рисовать оверлей в своём же процессе, инъекция в **чужой** процесс
не нужна. Вместе с `PrintWindow`/`StretchBlt` (скриншот окна/экрана), чтением клавиатуры,
`InternetOpenUrlA` и HWID-функциями это полный набор примитивов «инжектор + захват экрана +
отправка данных», спрятанный под VMProtect.

Отдельно: `subsystem: CONSOLE` при заявленном «GUI OneClick» — тоже нехарактерно для
настоящего графического модуля.

### 1.3 Вердикт по System Ready1.21.11.jar

**Не добавлять в лаунчер.** Формулировка честная: это не «нашли вирус», это
«файл сконструирован так, чтобы его нельзя было проверить», и внутри — инъекция в процессы,
захват экрана, чтение клавиатуры и сетевой доступ к произвольному URL. Для читового DLL
такой набор — типовой профиль и инфостилера тоже, различить их статически невозможно.
Раздавать это другим людям через лаунчер — брать на себя риск, который не проверяется.

---

## 2. systemdlc.jar (System для 1.21.4)

| | |
|---|---|
| Размер | 8 864 044 байт |
| sha256 | `013e1ee7623427a0a3df07fc955b50a7a76da1d09cb62081daa51a60d4f8f105` |
| fabric id | `systemdlcrecovered` / «SystemDLC Recovered» |
| entrypoint | `preLaunch` → `recovered.fabric.nativebridge.SystemDlcPreLaunch` |
| Файлов | 982 |

- Классы **полностью обфусцированы**: `A.class` … `T.class` и далее.
- Пакет `recovered.fabric.nativebridge/`: `NativeLibrary` с методом `extract()` (достаёт и
  грузит нативный модуль), `LegacyCalls` (рефлексия: `forName` + `getDeclaredMethods` +
  `setAccessible`), `OriginalHookStatus` (рефлексия по приватным полям и картам).
- Пакет `recovered.fabric/offline/`: `ConfigFiles`, `ConfigMutations`, `OfflineConfigStore`,
  `OfflineRuntime` — работа с локальным состоянием.
- Внешних URL в jar не найдено; DLL внутри нет (берётся извне или из `%TEMP%`, куда его
  кладёт первый System).
- Подключается **до старта игры** (`preLaunch`), то есть имеет доступ к JVM на самом раннем этапе.

### Вердикт по systemdlc.jar

Тот же класс проблем, что и выше: нативный модуль + обфускация + рефлексия с `setAccessible`
до запуска игры. Само по себе не «вирус», но **не проверяемо**.

---

## 3. RoseBeta_src

Исходники Gradle-проекта Fabric-мода, MC `1.21.11`, группа `ru.white` («White» client).
Собранного jar нет — `gradle-wrapper.jar` это обёртка Gradle, не клиент.

```
build.gradle      : fabric-loom 1.17-SNAPSHOT, Java 21, mixinextras 0.4.1
gradle.properties : minecraft 1.21.11, yarn 1.21.11+build.5, loader 0.19.2,
                    fabric-api 0.141.4+1.21.11
libs/             : kotlin-stdlib-1.9.20.jar, kotlinx-serialization-core-jvm-1.6.3.jar,
                    media-player-info-0.1.0.jar
```

Исходники **декомпилированные** — переменные `v0`, `f999`, `xm`, в коде остались ссылки
на Vineflower. Это нормальный признак кракнутого/слитого клиента, не признак малвари.

Проверено:

- `discord.com/api/webhooks` — 0, `api.telegram.org` — 0
- Промо-ссылки: `t.me/RoseClients`, `discord.gg/Egn5puSMEh`, `github.com/RoseSolution`
- Локальный Discord RPC (`DiscordManager`, `DiscordRPC`) — показывает статус игры в Discord
- `alt/AltManager`, `alt/o.java` (`minotar.net/helm/<ник>/64.png`) — менеджер альт-аккаунтов
- `auth/Guard.java` — анти-анализ: `jdk.attach.allowAttachSelf=false`, детект `-javaagent`
  (`hasAgentArgs`), хеширование себя (`MessageDigest` + `ZipFile`), `LocalDate` (срок лицензии)
- `auth/LicenseServer.java`, `auth/NativeBridge.java`, `auth/StrDec.java` — лицензия
- Сетевого POST-кода в `auth/` — 0

### Вердикт по RoseBeta

Слежки и эксфильтрации в исходниках не видно. Есть промо-ссылки и анти-анализ (это анти-тампер,
не малварь). Отдельно стоит учесть: в `libs/media-player-info-0.1.0.jar` — та же библиотека,
что была в Debuda (`dev/redstones/mediaplayerinfo`), то есть клиенты из одного круга.

---

## 4. Попытка собрать RoseBeta (2 октября, 18:00-18:17)

| Шаг | Результат |
|---|---|
| Gradle wrapper | распределение `gradle-9.5.1-bin` уже в кэше, скачивать не пришлось |
| JDK | toolchain `~/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2` (JDK 21) |
| Зависимости | fabric-loom, fabric-api, yarn для 1.21.11 есть в кэше |
| Сеть | `maven.fabricmc.net` доступен (один прогон упал на TLS-хендшейке, повтор прошёл) |
| Итог | **BUILD FAILED: `Failed to setup Minecraft, java.lang.RuntimeException: Namespace mismatch, expected named got intermediary`** |

Что уже сделано при отладке:

- собран не тот проект (Gradle берёт проект из cwd, а не из расположения `gradlew`):
  случайно прогонялась сборка `D:\versionshax\1.21.11\rockstar` — в её папке `build/`
  остался свежий результат. Лечится флагом `-p <каталог>`.
- кэш Loom для 1.21.11 (`~/.gradle/caches/fabric-loom/1.21.11`) пересоздан дважды,
  ошибка та же — значит дело не в мусоре в кэше.
- в `build.gradle` объявлен `fabric-loom 1.17-SNAPSHOT`, а Gradle поднимает loom
  из кэша другой версии — при `1.17-SNAPSHOT` набор маппингов в слое `layered`
  не совпадает, отсюда `expected named got intermediary`.

Дальше два пути: (1) поставить loom версии из кэша и убрать `1.17-SNAPSHOT` из build.gradle;
(2) собрать с изолированным `GRADLE_USER_HOME` (`gradlew -g <свежий каталог>`), где loom
скачается целиком и не будет влиять на кэш других проектов.

---

## 5. Что дальше

- `System Ready1.21.11.jar` — **не публиковать**, хранить до решения.
- `systemdlc.jar` — **не публиковать** по той же причине.
- `RoseBeta_src` — можно собрать Gradle + fabric-loom и отдать как клиент; нативной части нет.
- `xahsnoisrev/` — старый склад исходников от 28 сентября: внутри `durex-1.21.11.jar`
  и десятки папок чужих клиентов. К лаунчеру не относится, кандидат на удаление.
