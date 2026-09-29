# clients-repo — манифест и лого лаунчера «Псина»

В git лежит только МЕЛОЧЬ (килобайты): манифесты + лого png.
Все jar-файлы скачиваются из **GitHub Releases** (до 2 ГБ на файл, лимита трафика нет).

## Состав репо
- `launcher.json` — локальный манифест (file:-пути, для zip-дистрибутива)
- `launcher-online.json` — сетевой манифест (URL на releases/latest/download)
- `clients/<mc>/<id>.png` — логотипы читов для GUI

## Первый раз: выложить всё на GitHub (5 минут)

1. Создай **приватный** репозиторий на github.com (например `clients-repo`).
2. В папке `D:\launcher\configs-repo`:
   ```bat
   git add -A
   git commit -m "manifest + logos"
   git remote add origin https://github.com/<ТВОЙ_НИК>/clients-repo.git
   git push -u origin main
   ```
3. Загрузи jar-файлы в Release. Вариант с gh CLI (одна команда):
   ```bat
   gh release create v1 --title "clients" --notes "auto"
   gh release upload v1 clients\1.21.4\*.jar clients\1.21.11\*.jar clients\26.2\*.jar --clobber
   ```
   Нет gh CLI — вручную: страница репо → Releases → New release → тег `v1` →
   перетащи все 17 jar из папок `clients/*/` → Publish.
4. Сгенерируй онлайн-манифест под СВОЙ ник (GenOnline внутри launcher.jar):
   ```bat
   java -cp D:\launcher\dist\launcher.jar lcore.GenOnline launcher.json <ТВОЙ_НИК> clients-repo
   git add launcher-online.json && git commit -m "online manifest" && git push
   ```
5. В лаунчере (или у друзей) пропиши манифест:
   - Настройки лаунчера нет в GUI → CLI:
   ```bat
   java -cp launcher.jar lcore.Main --settings settings.json set manifestUrl https://raw.githubusercontent.com/<ТВОЙ_НИК>/clients-repo/main/launcher-online.json
   ```
   - Токен (обязателен для приватного репо): GitHub → Settings → Developer settings →
     Fine-grained tokens → Generate → только право **Contents: read** на этот репо.
   ```bat
   java -cp launcher.jar lcore.Main --settings settings.json set githubToken github_pat_XXXX
   ```
6. Проверка: кнопка «Обновить манифест» в GUI должна сказать «Всё актуально ●».

## Обновить чит (например, новую сборку дурекса)
1. Замени jar в `configs-repo/clients/<версия>/` (имя файла НЕ меняй).
2. `gh release upload v1 clients\1.21.11\durex.jar --clobber` (или вручную в Releases).
3. У друзей: кнопка «Обновить манифест» → лаунчер скачает свежий jar и подменит в mods сам.

## Добавить новый чит
1. Положи jar в `clients/<версия>/`, лого `<id>.png` туда же.
2. Добавь запись в `launcher.json` (id/name/mc/jar/requires/extra).
3. Прогони `GenOnline` → закоммить оба манифеста → добавь jar в Release.
