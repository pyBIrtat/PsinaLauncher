import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Проверка правила JarRewriter на настоящих jar'ах клиентов: сколько нативов
 *  вырежется, остаётся ли fabric.mod.json и entrypoint-класс, валиден ли zip. */
public class StripCheck {
    static final List<String> BAD_EXT = List.of(".dll", ".exe", ".ps1", ".bat", ".cmd", ".dylib", ".jnilib");
    static final List<String> DESKTOP_SO = List.of("linux", "osx", "darwin", "win32", "win64", "windows", "x86", "amd64", "i386", "i686", "freebsd");
    static final java.util.regex.Pattern SIG = java.util.regex.Pattern.compile("^META-INF/[^/]+\\.(SF|DSA|RSA)$", java.util.regex.Pattern.CASE_INSENSITIVE);

    static boolean desktopOnly(String entry) {
        String low = entry.replace('\\', '/').toLowerCase();
        for (String e : BAD_EXT) if (low.endsWith(e)) return true;
        if (low.endsWith(".so")) {
            if (low.contains("android") || low.contains("arm64") || low.contains("armeabi")) return false;
            for (String d : DESKTOP_SO) if (low.contains(d)) return true;
        }
        return false;
    }

    public static void main(String[] a) throws Exception {
        List<Path> jars = new ArrayList<>();
        for (String arg : a) {
            Path p = Path.of(arg);
            if (Files.isDirectory(p)) {
                try (var s = Files.walk(p)) { s.filter(x -> x.toString().endsWith(".jar")).forEach(jars::add); }
            } else jars.add(p);
        }
        Collections.sort(jars);
        for (Path jar : jars) {
            int removed = 0, kept = 0, classes = 0;
            boolean modJson = false;
            List<String> dropped = new ArrayList<>();
            Path tmp = Files.createTempFile("strip", ".jar");
            try (ZipFile z = new ZipFile(jar.toFile());
                 ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp))) {
                out.setLevel(Deflater.NO_COMPRESSION);
                out.setLevel(Deflater.NO_COMPRESSION);
                var es = z.entries();
                while (es.hasMoreElements()) {
                    ZipEntry e = es.nextElement();
                    String n = e.getName();
                    if (!e.isDirectory() && (desktopOnly(n) || SIG.matcher(n).matches())) {
                        removed++; dropped.add(n); continue;
                    }
                    kept++;
                    if (n.endsWith(".class")) classes++;
                    if (n.equals("fabric.mod.json")) modJson = true;
                    ZipEntry ne = new ZipEntry(n);
                    out.putNextEntry(ne);
                    if (!e.isDirectory()) try (InputStream in = z.getInputStream(e)) { in.transferTo(out); }
                    out.closeEntry();
                }
            }
            // проверяем, что результат — валидный zip
            int rc = 0;
            try (ZipFile z = new ZipFile(tmp.toFile())) { var ee = z.entries(); while (ee.hasMoreElements()) ee.nextElement(); }
            catch (Exception ex) { rc = 1; }
            System.out.printf("%-42s remove=%-3d keep=%-5d classes=%-5d modjson=%s zip=%s%n",
                    jar.getFileName(), removed, kept, classes, modJson ? "ok" : "-", rc == 0 ? "ok" : "ERR");
            if (!dropped.isEmpty() && dropped.size() <= 8)
                for (String d : dropped) System.out.println("      - " + d);
            Files.deleteIfExists(tmp);
        }
    }
}
