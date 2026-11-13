package dev.shaurmalib.anim.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * «Кинув і працює»: читає експорт Blockbench-плагіна прямо з папки гри {@code <гра>/shaurma_anim/} —
 * {@code .zip}-архіви (не треба розпаковувати) і звичайні теки, без ресурс-паків і без перезапуску (F3+T).
 *
 * <p>Усередині шукається {@code assets/<ns>/...} на будь-якій глибині (архів може мати теку-обгортку).
 * Кліпи й моделі пропсів ідуть у {@link AnimResourceLoader} ПІСЛЯ ресурс-паків — тобто перекривають їх
 * (свіжий експорт важливіший). PNG з {@code textures/} повертаються адаптеру, який реєструє їх як текстури гри.
 */
public final class AnimPackScanner {

    public static final String FOLDER = "shaurma_anim";

    private AnimPackScanner() {}

    /** Результат: текстури {@code ns:textures/...png → байти} і перелік прочитаних джерел. */
    public record Result(Map<String, byte[]> textures, List<String> sources) {}

    public static Result scan(Path dir, AnimResourceLoader loader, Consumer<String> warn) {
        Map<String, byte[]> textures = new LinkedHashMap<>();
        List<String> sources = new ArrayList<>();
        if (dir == null) return new Result(textures, sources);
        try {
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir); // щоб людина бачила, куди класти
                return new Result(textures, sources);
            }
            List<Path> children;
            try (Stream<Path> s = Files.list(dir)) {
                children = s.sorted().toList(); // стабільний порядок: однакові файли — однаковий результат
            }
            for (Path p : children) {
                String name = p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    scanFolder(p, loader, textures, warn);
                    sources.add(name + "/");
                } else if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                    scanZip(p, loader, textures, warn);
                    sources.add(name);
                }
            }
        } catch (IOException e) {
            warn.accept(FOLDER + ": " + e);
        }
        return new Result(textures, sources);
    }

    private static void scanFolder(Path root, AnimResourceLoader loader, Map<String, byte[]> textures, Consumer<String> warn) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            for (Path f : (Iterable<Path>) s.filter(Files::isRegularFile).sorted()::iterator) {
                String rel = root.relativize(f).toString().replace('\\', '/');
                try (InputStream in = Files.newInputStream(f)) {
                    entry(rel, in, loader, textures);
                } catch (IOException e) {
                    warn.accept(FOLDER + "/" + root.getFileName() + "/" + rel + ": " + e);
                }
            }
        }
    }

    private static void scanZip(Path zip, AnimResourceLoader loader, Map<String, byte[]> textures, Consumer<String> warn) {
        try (ZipFile z = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            List<ZipEntry> entries = new ArrayList<>();
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                ZipEntry en = e.nextElement();
                if (!en.isDirectory()) entries.add(en);
            }
            entries.sort(java.util.Comparator.comparing(ZipEntry::getName));
            for (ZipEntry en : entries) {
                try (InputStream in = z.getInputStream(en)) {
                    entry(en.getName().replace('\\', '/'), in, loader, textures);
                }
            }
        } catch (IOException e) {
            warn.accept(FOLDER + "/" + zip.getFileName() + ": не вдалося прочитати архів — " + e.getMessage());
        }
    }

    /** {@code .../assets/<ns>/<шлях>} → у завантажувач кліпів або в текстури. */
    static void entry(String rel, InputStream in, AnimResourceLoader loader, Map<String, byte[]> textures) throws IOException {
        int a = rel.startsWith("assets/") ? 0 : rel.indexOf("/assets/") + 1;
        if (a < 0 || (a == 0 && !rel.startsWith("assets/"))) return;
        String rest = rel.substring(a + "assets/".length());
        int slash = rest.indexOf('/');
        if (slash <= 0) return;
        String ns = rest.substring(0, slash), path = rest.substring(slash + 1);
        if (path.startsWith("textures/") && path.endsWith(".png")) {
            textures.put(ns + ":" + path, in.readAllBytes());
        } else if (AnimResourceLoader.accepts(path)) {
            loader.accept(ns, path, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
