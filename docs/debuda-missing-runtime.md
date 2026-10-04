# Debuda (debuda14) 1.21.4 — почему не запускается

Дата: 2026-10-04. Статус: **со стороны лаунчера починить нельзя** — не хватает
кода внутри самого jar. Причина не в лаунчере, не в Java и не в настройках.

## Что происходит

Воспроизведено запуском через лаунчер (`Main play 1.21.4 debuda14`) на текущем
`D:\mc-launcher\settings.json`:

```
java.lang.RuntimeException: Could not execute entrypoint stage 'main' due to errors,
  provided by 'isle-client' at 'ru.isle.core.Isle'!
Caused by: java.lang.NoClassDefFoundError: debuda/offline/MintFFIBatch
  at ru.metaculture.protection.O00000000OO00O.<clinit>
Caused by: java.lang.ClassNotFoundException: debuda.offline.MintFFIBatch
```

Minecraft успевает запуститься (Fabric грузит все API, LWJGL, окно), падает
именно на entrypoint клиента.

## Точный список отсутствующего

Пакета `debuda/` в jar нет вообще. Ссылки на него есть в 20 классах
`ru/metaculture/protection/`. Требуется ровно **3 класса**:

| класс | требуемые члены |
|---|---|
| `debuda.offline.MintFFIBatch` | `bind(int,int) -> MintFFIBatch$Session`, `privateLookup(Class) -> MethodHandles.Lookup`, `open(MemorySegment,int) -> MemorySegment`, `word(MemorySegment,int) -> long`, `result(MemorySegment,int,long) -> void`, `encode(Session,int) -> long`, `decode(Session,long) -> int` |
| `debuda.offline.MintFFIBatch$Session` | тип-сессия (используется как `BATCH_SESSION` — статическое поле в 18 классах) |
| `debuda.offline.OfflineHandles` | `findStatic(MethodHandles.Lookup,Class,String,MethodType,Class) -> MethodHandle`, `findVirtual(...)` — по 19 вызовов |

Вызовов всего ~273 штуки, но сигнатуры однотипные.

## Что осталось в jar

- `ua/mintantileak/native/mint-runtime.dll` (351 744 байта) — **нативный runtime
  на месте**. Внутри: экспорт `__mint_mba_key` и семейство `__mint_mba_key.N`,
  статусы `MINT_PENDING` / `MINT_THREW` / `MINT_DIV_BY_ZERO`.
- `ua/mintantileak/native/MintLoader` — умеет грузить DLL во временный файл
  (`System.load`), плюс `arena()` / `lookup()` (FFM).
- `ua/mintantileak/profile/Profile` — в `static {}` зашита строка
  **`crack by delivery`**, `uid = 1337`, роль `DEFAULT`.
- `ua/mintantileak/spk/Tether` — аннотация.

То есть это **взломанная сборка**: из неё вырезали Java-мосты лицензионной
защиты (`debuda/offline/*`), но оставили все вызовы. Нативный слой уцелел.

## Почему это нельзя обойти заглушкой

Классы `ru.metaculture.protection.*` — это уже переписанный (obfuscated) код,
который дергает `MintFFIBatch` как низкоуровневый мост в FFM. Просто вернуть
пустые методы мало: без настоящей реализации `bind/open/word/result/encode/decode`
клиент либо упадёт позже в рантайме, либо будет работать некорректно — этот слой
защиты вплетён в инициализацию `ru.isle.core.Isle` (~20 полей), а не вызывается
одной точкой.

## Проверено, что копии нигде нет

Просканированы все архивы на диске (`C:\Users\stola\Downloads`, `D:\launcher`,
`D:\mc-launcher`, `D:\versionshax`) — 6622 архива: ни в одном нет ни
`debuda/offline/MintFFIBatch.class`, ни `debuda/offline/OfflineHandles.class`,
ни вообще пакета `debuda/`. Оригинальной (полной) сборки на машине нет.

Дополнительно: `ua/mintantileak/native/mint-runtime.dll` на диске не встречается
вне этого jar, а сам jar (sha256 `d05522ac…`, 33 033 282 байта, 1709 записей)
не менялся.

## Что делать

1. **Взять полную сборку у автора** — она единственная рабочая.

> **ВНИМАНИЕ (не проверено):** эти сайты я не открывал и не проверял, они
> приведены только как ориентир — **качай на свой риск**. Никогда не запускай
> jar, скачанный со стороннего «зеркала», и не вводи логины.
>
> - сайт клиента: `isleclient.su` (в `fabric.mod.json` указан как contact;
>   на момент проверки DNS не резолвится — домен мог переехать);
> - Discord-сервер клиента (актуальная ссылка обычно в их TG/Discord).
>
> Сторонние «зеркала» и «фиксы» — с большой вероятностью тот же обрезанный jar
> либо чужой стиллер. Проверяй перед запуском.

2. **Либо взять другую версию/клиент.** На 1.21.4 в лаунчере уже есть рабочие:
   Alek DLC, Arbuz, BravoVisuals, Delta, Expensive, LiquidBounce, Meow, Destra,
   Rockstar, SrHeldlc, Velka, ExpA, Durex и др.

3. **Либо попросить у меня реализовать `debuda/offline/*` поверх уцелевшего
   `mint-runtime.dll`.** Это реально, но это разработка чужого протокола
   (обратная инженерия `__mint_mba_key`), а не «фикс лаунчера».

## Что было раньше (не причина)

`D:\launcher\tools\debuda-strip.pl` заменял 7 телеметрических/рекламных URL
на равные по длине заглушки `http://127.0.0.1:9/...`. К этому падению отношения
не имеет — оно происходит раньше и по другой причине.