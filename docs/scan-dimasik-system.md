# Разбор «Dimasik» (26.2) и «System» — 3 октября 2026

Цель: проверить на вирусы и решить, можно ли отдавать в лаунчер.
Метод: статический разбор Java-классов (свой дампер constant pool + Vineflower-декомпиляция),
разбор PE-файлов парсером `tools/pe-report.ps1`/`pe-deep.ps1`, сверка сертификатов, разбор строк.

**Итог: `Dimasik` в лаунчер отдавать нельзя (встроенный удалённый доступ AnyDesk AnyNet).
`System` — по-прежнему нельзя (VMProtect + инжектор BlackBone).**

---

## 1. Dimasik-26.2-Cracked.jar

| | |
|---|---|
| Размер | 60 428 244 байт |
| sha256 | `c0e796a415c1ddae9ee2f6d1c58899775434af2562eba9158f824c0b5d11b2ac` |
| fabric id | `dimasik` / версия `26.2-1` |
| entrypoint | `sg.ec.02b2ZICK1LOH2v0X` |
| Minecraft | `~26.2` |
| Java | **`>=25`** (mixins `compatibilityLevel: JAVA_25`) |
| Файлов | 3972 (2419 assets, 1548 классов `sg/ec`, 2 класса `HdG`) |
| Обфускация | да (`sg.ec.*`, имена классов случайные); `HdG/P5iL` — hidef-заглушка (индирекция строк) |
| Ватермарк | `cracked by @soezproject & Vkontakt3` |

### 1.1 Опасное: встроенный удалённый доступ AnyDesk «AnyNet»

В jar лежит полноценный клиент релея AnyDesk:

```
sg/ec/3KFQ2ugKu4yYEXzz            TLSv1.2 relay-клиент (Socket/SSLSocket)
sg/ec/3KFQ2ugKu4yYEXzz$*          подклассы (состояния, кадры)
sg/ec/SdKi3rS3HOcktY95            TLS-идентичность (KeyManager) для соединения
sg/ec/8Sk4mEd3N6iIJgPV            модуль «AnyDesk / Обход проверок через AnyDesk»
assets/dimasik/anydesk/anynet-root-ca-2.pem   пиннутый корневой CA
```

Что делает код (по декомпиляту):

- подключается по TLS к **`boot-01.net.anydesk.com:443`** (реальный релей AnyDesk AnyNet);
- предъявляет клиентский сертификат, построенный на **своём** приватном ключе
  (`SdKi3rS3HOcktY95.Р().getKeyManagers()`), и проверяет сервер пиннутым CA;
- регистрируется, получает **AnyDesk ID**, и **автоматически принимает** входящее соединение:
  `Zu53NtHusCtdriRX.NONE → Awi1G1jsXYtlm7eR.APPROVED`, лог-строки
  `"Registered; automatic acceptance enabled"`, `"Approval sent; screen and input are not implemented"`;
- модуль зарегистрирован в списке модулей (`sg/ec/L2qTOUGNWUSuilNN`), т.е. доступен из GUI.

Сертификат — подлинный `CN=AnyNet Root CA 2, O=philandro Software GmbH, C=DE`
(philandro Software = разработчик AnyDesk). Это не подделка.

**Важная оговорка (честно):** строки `screen and input are not implemented` показывают, что
в этой сборке трансляция экрана и приём ввода **не реализованы** — модуль умеет только
регистрироваться на релее и отправлять «approval». То есть полноценного удалённого
управления «из коробки» нет, но архитектура — это мост в чужую инфраструктуру AnyDesk,
которым распоряжается автор чита (ID регистрируется под его аккаунтом AnyNet).

### 1.2 Прочее, что найдено

- **Редирект адресов серверов** — `sg/ec/7dCjHrlQt8mkhswh`: адреса
  `mc.svinworld.space`, `mc.svinworld.fun`, `46.174.48.116` подменяются на
  `redirect.bravohvh.fun` (аналог «AntiAgent»-подмены, как в Delta).
- **IRC-канал пати/эмоций** — `sg/ec/wOPwT7J4giBZsk46` (`dimasik-irc`) на
  `217.60.245.54:4892`, шифрованные сессии, синхронизация эмоций между игроками.
  Это **штатная фича клиента** (как MQTT-пати у SrHeldlc), не C2. Внутри jar — `org/luaj`
  (LuaJ, официальная библиотека) — для скриптов/эмоций.
- **`assets/dimasik/media/media_session.ps1`** (21 КБ) — легитимный Windows-мост
  «сейчас играет» (WinRT GSMTC + WASAPI-громкость, `-WindowStyle Hidden`). Запускается
  скриптом из ресурсов через `powershell.exe`, только на Windows. Не вредонос.
- **Сети:** `dimasclient.fun`, `t.me/dimasikdlc`, `vk.com/reallyworlds`,
  `discord.gg/dimasikclient`, `s13.gifyu.com` (аватарка RPC), сервера Funtime/HolyWorld/
  ReallyWorld (детект бренда). Бренды — водяные знаки/кнопки, не эксфильтрация.
- **Стилера НЕ найдено:** ноль `Login Data`, `leveldb`, `cookies.sqlite`, `wallet`,
  keylog/`GetAsyncKeyState`, Discord-вебхуков, Telegram-бот-API, чтения браузерных данных.
- HWID как таковой не отправляется (AnyDesk ID — идентификатор AnyNet, не HWID машины).

### 1.3 Обязательные моды (`Downloads/modsFORdimasik/`) — ЧИСТЫЕ

| Мод | Версия | Вердикт |
|---|---|---|
| fabric-api | 0.161.0+26.2 | официальный FabricMC, нативы/исполнения нет |
| ViaFabricPlus | 5.0.2 | официальный ViaVersion; URL — их github/докс |
| luaj-jse | 3.0.1 | официальная библиотека LuaJ (для скриптов чита) |
| onnxruntime | 1.27.0 | официальный ONNX Runtime; нативы — его же `.so/.dll` |

### 1.4 Вердикт по Dimasik

**В лаунчер не добавлять.** Формально инфостилера нет, но в jar встроен клиент удалённого
доступа AnyDesk AnyNet с автоматическим принятием и пиннутым CA — это мост в чужую
инфраструктуру, которым управляет автор чита. Плюс подмена адресов серверов. Отдавать это
друзьям через лаунчер — раздавать удалённый доступ третьей стороне. Если нужен именно этот
чит — только изолированно, после вырезания модулей `3KFQ2ugKu4yYEXzz`, `SdKi3rS3HOcktY95`,
`8Sk4mEd3N6iIJgPV`, `7dCjHrlQt8mkhswh` (и связанных) и проверки, что без них чит стартует.

---

## 2. System Ready1.21.11.jar (id `mcgui`)

Без изменений к прошлому разбору (`docs/scan-system-rosebeta.md`):

| | |
|---|---|
| sha256 | `31d38ec44e3e0463e05d689b0417970b4e96dcb2f27a50963cbece515239e37f` |
| нативы | `native/ClientGUI.dll` 27.4 МБ (VMProtect `.vmp0` 13.5 МБ), MSVC-рантайм |
| бэкенд | `pulsevisuals.pro` (api/ruapi/euapi/cosmetics) |
| внутр. | 2-й PE без экспортов, вкомпилен инжектор **BlackBone** + драйверы CE/Process Hacker |

**В лаунчер не добавлять** — тот же вердикт: нативный чит-клиент с инжектором под VMProtect,
непроверяемый статически, трафик/сессии идут на чужую инфраструктуру. `systemdlc` (1.21.4,
sha `013e1ee7…`) — тот же класс проблем.

---

## 3. Что дальше

- Dimasik — **не публиковать** в текущем виде. Варианты: (а) вырезать AnyDesk-модуль и
  подмену адресов и собрать «dimasik-clean»; (б) не добавлять вовсе.
- System / systemdlc — **не публиковать**.
- Для 26.2 нужен **Java 25+**: у лаунчера уже есть хук `game/_shared/jre-<mc>/bin/java.exe`
  (файл `lcore/GameLauncher.java`), т.е. достаточно положить JDK 25 в `jre-26.2`.
