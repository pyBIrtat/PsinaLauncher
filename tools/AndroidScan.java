// PSINA: AndroidScan — что в jar клиента не поедет на Android.
// Те же правила, что в psina-mobile (core/AndroidCompat.kt). Java 21+, без зависимостей.
//   java AndroidScan.java <jar|папка> [...]
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class AndroidScan {

    record Rule(String tag, String needle, String why) {}

    static final List<Rule> RULES = List.of(
        new Rule("jna", "com/sun/jna/Native", "JNA тянет Windows-.dll"),
        new Rule("onnx", "ai/onnxruntime", "нужен onnxruntime под Android, а не desktop"),
        new Rule("catboost", "ai/catboost", "CatBoost без Android-нативов"),
        new Rule("discord", "discord-rpc", "Discord RPC (.dll)"),
        new Rule("discord", "discordrpc", "Discord RPC (.dll)"),
        new Rule("media", "MediaPlayerInfo", "Windows-медиасессия"),
        new Rule("awt", "java/awt/Desktop", "AWT Desktop на Android нет"),
        new Rule("awt", "java/awt/Robot", "AWT Robot на Android нет"),
        new Rule("awt", "javax/swing", "Swing на Android нет"),
        new Rule("oshi", "oshi/SystemInfo", "OSHI может падать на Android"),
        new Rule("process", "java/lang/ProcessBuilder", "запуск процессов (powershell/explorer)"),
        new Rule("process", "java/lang/Runtime", "Runtime.exec"),
        new Rule("winpath", "powershell", "Windows-команда"),
        new Rule("winpath", "explorer.exe", "Windows-команда"),
        new Rule("winpath", "SystemDrive", "Windows-путь"),
        new Rule("winpath", "LOCALAPPDATA", "Windows-путь"));

    static final long MAX_ENTRY = 12L * 1024 * 1024;

    record Report(String name, int classes, int maxMajor, List<String> natives,
                  List<String> scripts, Map<String, Integer> hits, Map<String, String> why,
                  boolean fabricMod, String modId, String entrypoints) {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) { System.err.println("usage: AndroidScan <jar|dir> ..."); System.exit(2); }
        List<Path> jars = new ArrayList<>();
        for (String a : args) {
            Path p = Path.of(a);
            if (Files.isDirectory(p)) try (var w = Files.walk(p)) {
                w.filter(f -> f.toString().endsWith(".jar")).sorted().forEach(jars::add);
            } else jars.add(p);
        }
        List<Report> reports = new ArrayList<>();
        for (Path j : jars) {
            try { reports.add(scan(j)); }
            catch (Exception e) { System.out.println("== " + j.getFileName() + " == ERROR " + e); }
        }
        reports.forEach(r -> print(r));
        System.out.println("\n==== СВОДКА ====");
        for (Report r : reports) System.out.printf("%-42s %-8s %s%n",
                r.name(), javaOf(r.maxMajor()), blockers(r).isEmpty() ? "можно пробовать" : String.join(", ", blockers(r)));
    }

    static Report scan(Path jar) throws IOException {
        int classes = 0, maxMajor = 0;
        List<String> natives = new ArrayList<>(), scripts = new ArrayList<>();
        Map<String, Integer> hits = new TreeMap<>();
        Map<String, String> why = new HashMap<>();
        boolean fabricMod = false;
        String modId = "", entrypoints = "";
        try (ZipFile z = new ZipFile(jar.toFile())) {
            var en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName(), low = name.toLowerCase(Locale.ROOT);
                if (low.endsWith("fabric.mod.json")) {
                    fabricMod = true;
                    String s = new String(z.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
                    modId = grab(s, "\"id\"");
                    entrypoints = grab(s, "\"entrypoints\"");
                }
                if (low.endsWith(".dll") || low.endsWith(".so") || low.endsWith(".dylib")
                        || low.endsWith(".exe") || low.endsWith(".jnilib")) { natives.add(name); continue; }
                if (low.endsWith(".ps1") || low.endsWith(".bat") || low.endsWith(".cmd")) { scripts.add(name); continue; }
                if (!low.endsWith(".class")) continue;
                if (e.getSize() > MAX_ENTRY || e.getSize() <= 0) continue;
                byte[] b = z.getInputStream(e).readAllBytes();
                classes++;
                if (b.length > 8 && (b[0] & 0xff) == 0xCA && (b[1] & 0xff) == 0xFE) {
                    int m = ((b[6] & 0xff) << 8) | (b[7] & 0xff);
                    if (m > maxMajor) maxMajor = m;
                }
                String t = new String(b, StandardCharsets.ISO_8859_1);
                for (Rule r : RULES) if (t.contains(r.needle())) {
                    hits.merge(r.tag(), 1, Integer::sum);
                    why.putIfAbsent(r.tag(), r.why());
                }
            }
        }
        return new Report(jar.getFileName().toString(), classes, maxMajor,
                natives.stream().distinct().toList(), scripts.stream().distinct().toList(),
                hits, why, fabricMod, modId, entrypoints);
    }

    static String grab(String json, String key) {
        int i = json.indexOf(key);
        if (i < 0) return "";
        int j = json.indexOf(':', i);
        if (j < 0) return "";
        int end = json.indexOf('\n', j);
        return json.substring(j + 1, end < 0 ? json.length() : end).trim().replaceAll("\\s+", " ");
    }

    static List<String> blockers(Report r) {
        List<String> out = new ArrayList<>();
        if (r.maxMajor() >= 69) out.add("Java 25");
        boolean hasSo = r.natives().stream().anyMatch(n -> n.toLowerCase().endsWith(".so"));
        if (!hasSo) {
            long win = r.natives().stream().filter(n -> {
                String l = n.toLowerCase();
                return l.endsWith(".dll") || l.endsWith(".exe");
            }).count();
            if (win > 0) out.add("Windows-нативы:" + win);
        }
        if (!r.scripts().isEmpty()) out.add("скрипты:" + r.scripts().size());
        r.hits().forEach((tag, n) -> {
            if (!tag.equals("process") || n > 3) {
                String w = r.why().get(tag);
                out.add(w + "(" + n + ")");
            }
        });
        return out.stream().distinct().toList();
    }

    static void print(Report r) {
        System.out.println("== " + r.name() + " ==");
        System.out.println("   классов: " + r.classes() + " · максимум " + r.maxMajor() + " (" + javaOf(r.maxMajor()) + ")"
                + (r.fabricMod() ? " · FABRIC mod id=" + r.modId() : " · НЕ fabric-мод"));
        if (!r.natives().isEmpty()) System.out.println("   нативы (" + r.natives().size() + "): "
                + r.natives().subList(0, Math.min(5, r.natives().size())));
        if (!r.scripts().isEmpty()) System.out.println("   скрипты: " + r.scripts());
        r.hits().forEach((tag, n) -> System.out.println("   [" + tag + "] " + n + " — " + r.why().get(tag)));
        List<String> b = blockers(r);
        System.out.println("   ANDROID: " + (b.isEmpty() ? "кандидат (тестировать на телефоне)" : String.join(", ", b)) + "\n");
    }

    static String javaOf(int major) {
        if (major >= 69) return "Java 25";
        if (major >= 65) return "Java 21";
        if (major >= 61) return "Java 17";
        if (major >= 55) return "Java 11";
        return "Java 8";
    }
}
