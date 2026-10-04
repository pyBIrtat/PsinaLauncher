# Debuda (Isle) 1.21.4 — почему падает при запуске

Диагноз от 2026-10-04. Файл: `clients/1.21.4/debuda14-1.21.4-d05522ac.jar` (33 МБ, 1709 записей).

## Ошибка

```
java.lang.RuntimeException: Could not execute entrypoint stage 'main'
Caused by: java.lang.NoClassDefFoundError: debuda/offline/MintFFIBatch
    at ru.metaculture.protection.O00000000OO00O.<clinit>
    at ru.isle.core.Isle.O0000000000
    at ru.isle.core.Isle.onInitialize
Caused by: java.lang.ClassNotFoundException: debuda.offline.MintFFIBatch
```

## Причина: в сборке нет runtime защиты MintAntiLeak

`ru.metaculture.protection.O00000000OO00O` — это FFM-мост (Foreign Function & Memory) к нативной
библиотеке MintAntiLeak (`ua/mintantileak/native/mint-runtime.dll`, 351 КБ, там же `MintLoader`).
Он по контракту опирается на типы `debuda.offline.MintFFIBatch` и `debuda.offline.MintFFIBatch$Session`:

```
private static final java.lang.foreign.Linker LINKER;
private static final java.lang.foreign.Arena ARENA;
private static final debuda.offline.MintFFIBatch$Session BATCH_SESSION;
private static final java.lang.foreign.SymbolLookup LOOKUP;
```

Пакета `debuda/offline/` в jar **нет вообще** — ни одного класса. Искали по всему диску
(Downloads, D:\launcher, D:\versionshax, D:\mc-launcher; и по jar-листингам, и по сырым байтам
архивов) — `MintFFIBatch` не встречается нигде. В самой `mint-runtime.dll` его тоже нет
(её строки — только MBA-ключи `__mint_mba_key*` и таблица импортов).

Судя по имени (`...MintFFIBatch`, `$Session`) и по тому, что `MintLoader` умеет только
`System.load` / `loadLibrary`, класс генерируется обфускатором «MetaCulture» на сборке у автора.
Тот, кто дампил/ломал этот jar, вырезал пакет `debuda/offline/`, но все вызовы остались.

## Почему это нельзя починить с нашей стороны

`ru.isle.core.Isle` (entrypoint Fabric) содержит ~20 полей типа `ru.metaculture.protection.*`
и инициализирует их в конструкторе. Даже если заNoPить один вызов, следующий класс упрётся в ту же
отсутствующую зависимость. Подделать `MintFFIBatch` заглушкой нельзя: его поведение задаёт
нативный runtime, а не наш код, — получим `NoSuchMethodError` вместо честного запуска.

## Что реально можно сделать

1. Достать **полную** сборку Debuda (с пакетом `debuda/offline/`) — от автора клиента
   (`isleclient.su` / Discord) или из рабочей раздачи; положить её вместо текущего jar.
2. Либо взять другую версию/другой клиент.
3. Либо выключить защиту патчем байткода — но это десятки связанных классов, и клиент может
   отказаться работать: в `Isle` защита вплетена в инициализацию, а не в один вызов.

## Что уже сделано с этим jar

`tools/debuda-strip.pl` ранее заменил 7 адресов телеметрии/рекламы на мёртвый localhost
(равная длина строк, пул констант не поехал). К нашей проблеме это отношения не имеет —
класс отсутствовал бы и в оригинале.
