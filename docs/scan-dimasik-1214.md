# Скан Dimasik 1.21.4 (портативная сборка) — 2026-10-04

Образец: `C:\Users\stola\Downloads\Dimasik-1.21.4\dimasik_start\` (640 МБ, Windows-портатив).

## Вердикт: чистый, вредоносного кода нет

Ни удалённого доступа, ни автозапуска, ни кейлоггера, ни исполняемых файлов внутри jar.

## Что проверено и что найдено

**Состав пакета** — `dimasik.jar` (26 МБ, 25 418 записей), `jre/` (96 МБ), `libraries/` (79 jar, 77 МБ),
`natives/` (7 dll, 5.5 МБ), `game/` (437 МБ ассетов и сейвов), `start.bat`.

**`natives/`** — только стандартные LWJGL: `lwjgl.dll`, `lwjgl_opengl.dll`, `lwjgl_stb.dll`,
`glfw.dll`, `OpenAL.dll`, `freetype.dll`, `jemalloc.dll`. Лишнего нет.

**`libraries/`** — 79 обычных библиотек Minecraft/Fabric (authlib, netty, fastutil, log4j,
lwjgl, oauth2-oidc-sdk, msal4j, nimbus-jose-jwt, icu4j …). Подделок и лишних jar нет.

**`jre/`** — полноценный стандартный JDK (java.exe, javac.exe, jdb, jmap, api-ms-*.dll). Подмены нет.

**`game/`** — ни одного `.exe`, `.dll`, `.jar`, `.bat`.

**`dimasik.jar`** — внутри НЕТ ни одного `.dll`/`.exe`/`.so`/`.bat`/`.ps1`/`.vbs` (проверено разбором
всех 25 418 записей). Manifest: `Main-Class: Launch`.

Строковые индикаторы по всем классам (`_scan_tools/ScanJar.java`):
- `AnyDesk` / `AnyNet` / `ammyy` / `TeamViewer` — **не найдено** (в отличие от сборки 26.2, где AnyDesk был вшит).
- `Runtime.exec` / `ProcessBuilder` — встречается только в классах самой игры (`sg/`) и в
  `dvm/jmx/InitializedOperatingSystemMXBean.class` — это ванильный код, а не чит.
- `ServerSocket` — 2 класса, оба ванильные: кэш загрузок и netty-бутстрап встроенного сервера
  (синглплеер). Наружу ничего не слушает.
- автозапуск (`HKCU\...\Run`, `schtasks`, `Startup`-персистентность) — **не найдено**;
  строка `Startup` есть только в ванильных классах (имена потоков/семплов).
- кейлоггер-API (`GetAsyncKeyState`, `SetWindowsHookEx`) — **не найдено**.

## Защита стухла в заглушку (это хорошо)

Пакет `ru/dreamix/protection/` (Dreamix) — лицензионная защита клиента. Вот её методы:
`NativeAPI` полностью стабнут:

```
getUserIdentifier() -> 1
getUserName()       -> "DimasikUser"
getUserSubscribeTill() -> "01.01.2048"
getRole()           -> Role.ADMIN
```

То есть ни на какой сервер за подпиской сборка не ходит и ничего о железе не отправляет.
`Bootstrapper` создаёт свой `ClassLoader`, который `defineClass`-ит скрытые классы и вызывает
`sg.<obf>#ojRW("--username", …)` — это обычный самозапуск игры, а не подгрузка кода из сети.

Телеметрия обрезана ещё тем, кто ломал: единственный «странный» URL в jar —
`https://127.0.0.1/bot` (заменён на localhost). Остальные адреса — ванильные `aka.ms/*`,
`optifine.net`, `api.mojang.com`.

## Замечания (не вирус, но знать)

1. `start.bat` запускает java с `-noverify` — отключает проверку байткода. Гигиенически плохо;
   при добавлении в лаунчер этот флаг ставить не нужно.
2. `dimasik.jar` — **слитый** jar (игра + чит вместе, 25 418 классов), поэтому он НЕ кладётся в
   `mods/` как Fabric-мод, в отличие от сборки под 26.2. Это портативный дистрибутив.
3. Размер: 640 МБ (из них 437 МБ — ассеты). Zip будет ~500–600 МБ.

## Инструменты

`D:\versionshax\1.21.11\_scan_tools\ScanJar.java`, `ScanUrls.java`, `DumpClass.java` —
читают jar без распаковки (ZipFile + поиск индикаторов), пригодны для будущих проверок.
