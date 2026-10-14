// PSINA: ClientScan — быстрый статический скан чита на опасности (стиллер/удалённый доступ/HWID).
// По методике разборов docs/scan-*.md: сырые константные строки классов + список записей jar.
// НЕ декомпилирует: работает по строкам constant pool (javap-аналог без JDK-зависимостей).
//   java ClientScan.java <jar> [...]
// Вердикты: CLEAN (друзьям можно) / SUSPECT (смотри разделы) / DANGER (не публиковать).
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class ClientScan {

    record Rule(String tag, String needle, String why) {}

    static final List<Rule> RULES = List.of(
        // === стилеры ===
        new Rule("stealer", "Login Data", "Chrome-логины (стилер)"),
        new Rule("stealer", "Cookies.sqlite", "Firefox cookies (стилер)"),
        new Rule("stealer", "leveldb", "Chrome LocalStorage (стилер)"),
        new Rule("stealer", "discord/Local Storage", "Discord-токен хищение"),
        new Rule("stealer", "GetAsyncKeyState", "кейлоггер"),
        new Rule("stealer", "SetWindowsHookEx", "глобальный хук клавиатуры"),
        new Rule("stealer", "webhook", "Discord-webhook эксфильтрация"),
        new Rule("stealer", "api.telegram.org/bot", "Telegram-бот эксфильтрация"),
        new Rule("stealer", "wallet.dat", "крипто-кошелёк"),
        // === удалённый доступ/мост в чужую инфраструктуру ===
        new Rule("remote", "net.anydesk.com", "AnyDesk AnyNet релей"),
        new Rule("remote", "anydesk", "AnyDesk-модуль"),
        new Rule("remote", "teamviewer", "TeamViewer"),
        new Rule("remote", "ngrok", "ngrok-туннель"),
        // === подмена серверов ===
        new Rule("redirect", "redirect.", "подмена адресов серверов (проверь контекст)"),
        // === обфускация/упаковка ===
        new Rule("protect", "VMProtect", "VMProtect (натив нельзя разобрать)"),
        new Rule("protect", "vmp0", "VMProtect-секция"),
        new Rule("protect", "BlackBone", "инжектор BlackBone"),
        // === HWID/лицензии ===
        new Rule("hwid", "MachineGuid", "HWID-привязка (MachineGuid)"),
        new Rule("hwid", "wmic csproduct", "HWID через wmic"),
        // === некоторая живая сеть (не всегда плохо) ===
        new Rule("net", "raw.githubusercontent.com", "тянет файлы с GitHub"),
        new Rule("net", "pastebin.com", "тянет с pastebin"),
        new Rule("net", "ngrok.io", "туннель"),
        new Rule("exec", "powershell.exe", "Runtime exec PowerShell"),
        new Rule("exec", "cmd.exe", "Runtime exec cmd"),
        new Rule("exec", "Runtime.getRuntime", "Runtime.exec вызовы"),
        new Rule("exec", "ProcessBuilder", "запуск процессов"),
        new Rule("exec", "start Debuda", "старт Debuda")
    );

    static final long MAX_ENTRY = 16L * 1024 * 1024;
    static final int CLASS_MAGIC = 0xCAFEBABE;

    record Report(String name, long size, int classes, int natives, int scripts,
                  Map<String, List<String>> hits, Map<String, String> why, boolean fabricMod,
                  String modId, List<String> entrypoints) {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) { System.err.println("usage: java ClientScan.java <jar> [...]"); System.exit(2); }
        for (String a : args) {
            Path p = Path.of(a);
            if (Files.isDirectory(p)) {
                try (var w = Files.walk(p)) { w.filter(f -> f.toString().endsWith(".jar")).sorted().forEach(j -> scanSafe(j)); }
            } else scanSafe(p);
        }
    }

    static void scanSafe(Path jar) {
        try {
            Report r = scan(jar);
            verdict(r);
        } catch (Exception e) { System.out.println("== " + jar.getFileName() + " == ERROR " + e); }
    }

    static Report scan(Path jar) throws IOException {
        int classes = 0, natives = 0, scripts = 0;
        Map<String, List<String>> hits = new TreeMap<>();
        Map<String, String> why = new TreeMap<>();
        boolean fabricMod = false; String modId = ""; List<String> entrypoints = new ArrayList<>();
        long size = Files.size(jar);
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            var en = zf.entries();
            byte[] buf = new byte[8];
            List<byte[]> classesData = new ArrayList<>();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".class")) { classes++; if (e.getSize() <= MAX_ENTRY) classesData.add(zf.getInputStream(e).readAllBytes()); }
                if (lower.endsWith(".dll") || lower.endsWith(".exe") || lower.endsWith(".so") || lower.endsWith(".dylib") || lower.endsWith(".jnilib")) natives++;
                if (lower.endsWith(".ps1") || lower.endsWith(".bat") || lower.endsWith(".cmd") || lower.endsWith(".vbs") || lower.endsWith(".ahk")) scripts++;
                if (name.endsWith("fabric.mod.json")) {
                    fabricMod = true;
                    String j = new String(zf.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
                    modId = jsonField(j, "id");
                    entrypoints = jsonList(j, "entrypoints");
                }
            }
            // сканируем константные строки в классах — как в переписях строк декомпилятором
            Map<String, Boolean> seen = new TreeMap<>();
            for (byte[] data : classesData) {
                if (data.length < 8) continue;
                int magic = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                if (magic != CLASS_MAGIC) continue;
                String blob = new String(data, StandardCharsets.ISO_8859_1);
                for (Rule rule : RULES) {
                    if (blob.contains(rule.needle) && !seen.containsKey(tagKey(rule))) {
                        seen.put(tagKey(rule), true);
                        hits.computeIfAbsent(rule.tag, k -> new ArrayList<>()).add(rule.needle);
                        why.put(tagKey(rule), rule.why);
                    }
                }
            }
        }
        return new Report(jar.getFileName().toString(), size, classes, natives, scripts, hits, why, fabricMod, modId, entrypoints);
    }

    static String tagKey(Rule r) { return r.tag + "::" + r.needle; }

    static void verdict(Report r) {
        System.out.println("=== " + r.name() + " ===");
        System.out.println("  size " + (r.size() / 1024 / 1024) + " МБ, классов " + r.classes()
                + ", PE/нативов " + r.natives() + ", скриптов " + r.scripts());
        if (r.fabricMod()) System.out.println("  fabric.mod.json: id=" + r.modId() + ", entrypoints=" + r.entrypoints());
        if (r.hits().isEmpty()) {
            System.out.println("  попаданий нет");
        } else {
            Map<String, List<String>> byTag = new TreeMap<>();
            for (var kv : r.hits().entrySet()) {
                String tag = kv.getKey().split("::")[0];
                byTag.computeIfAbsent(tag, k -> new ArrayList<>()).addAll(kv.getValue());
            }
            for (var e : byTag.entrySet()) {
                System.out.println("  [" + e.getKey() + "] " + e.getKey() + ": " + e.getValue());
                String w = r.why().get(e.getKey() + "::" + e.getValue().get(0));
                if (w != null) System.out.println("        - " + w);
            }
        }
        // Вердикт: danger-теги — стоп, exec/hwid/net/protect — подозрение, иначе чисто.
        Set<String> tags = new TreeSet<>();
        for (String k : r.hits().keySet()) tags.add(k.split("::")[0]);
        String v = "CLEAN — в лаунчер можно";
        if (tags.contains("stealer") || tags.contains("remote")) v = "DANGER — в лаунчер НЕ публиковать (стилер/удалённый доступ)";
        else if (tags.contains("redirect") || tags.contains("protect") || tags.contains("hwid") || tags.contains("exec") && r.natives() > 0)
            v = "SUSPECT — нужен ручной разбор (см. hits выше)";
        System.out.println("  ВЕРДИКТ: " + v);
        System.out.println();
    }

    static String jsonField(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        int colon = json.indexOf(':', i);
        int start = json.indexOf('"', colon);
        int end = json.indexOf('"', start + 1);
        return (start < 0 || end < 0) ? "" : json.substring(start + 1, end);
    }

    static List<String> jsonList(String json, String key) {
        List<String> out = new ArrayList<>();
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return out;
        int start = json.indexOf('[', i);
        if (start < 0) return out;
        int end = json.indexOf(']', start);
        String inner = end > start ? json.substring(start, end) : "";
        for (String part : inner.split("\"")) if (!part.isBlank() && part.matches("[A-Za-z0-9_./$-]{4,}")) out.add(part);
        return out.size() > 5 ? out.subList(0, 5) : out;
    }
}
