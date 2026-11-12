package dev.shaurmalib.forge.module;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Хук архітектурного контролю: перевіряє, що lib-common не імпортує
 * net.minecraftforge.*.
 *
 * Запускається як Gradle-таска в корені shaurma-lib, але сам клас
 * живе в lib-forge, щоб мати до нього доступ з конфігурації.
 */
public final class ArchitectureSniffer {

    private static final Pattern FORGE_IMPORT = Pattern.compile(
            "^\\s*import\\s+net\\.minecraftforge\\..*;"
    );

    private ArchitectureSniffer() {}

    /**
     * Сканує папку lib-common/src/main/java і папеками test, якщо задано.
     *
     * @param sourceDir корінь папки lib-common (наприклад, .../shaurma-lib/lib-common)
     * @param strict    якщо true — скасувати збірку при знаходженні імпорту;
     *                  якщо false — лише повідомити.
     * @return спеціальний стан: білий список (чистий / не чистий).
     */
    public static SnifferResult scan(Path sourceDir, boolean strict) throws IOException {
        Path src = sourceDir.resolve("src/main/java");
        Path test = sourceDir.resolve("src/test/java");

        List<String> violations = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(src)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".java"))
                  .forEach(p -> collectViolations(p, src, violations, "src/main/java"));
        }
        if (Files.isDirectory(test)) {
            try (Stream<Path> stream = Files.walk(test)) {
                stream.filter(Files::isRegularFile)
                      .filter(p -> p.toString().endsWith(".java"))
                      .forEach(p -> collectViolations(p, test, violations, "src/test/java"));
            }
        }

        boolean clean = violations.isEmpty();
        return new SnifferResult(clean, violations, sourceDir);
    }

    private static void collectViolations(Path file, Path root, List<String> violations, String module) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String rel = root.relativize(file).toString();
        String linePrefix = "[" + module + "/" + rel + "]";
        int lineNumber = 0;
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (FORGE_IMPORT.matcher(line).matches()) {
                    violations.add(linePrefix + ":" + lineNumber + " -> " + line.trim());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: ArchitectureSniffer <lib-common-root> [strict=true|false]");
            System.exit(1);
        }
        Path root = Paths.get(args[0]);
        boolean strict = args.length > 1 && Boolean.parseBoolean(args[1]);
        SnifferResult result = ArchitectureSniffer.scan(root, strict);
        System.out.println("Architecture Sniffer: " + (result.clean ? "PASS" : "FAIL"));
        result.violations.forEach(System.out::println);
        if (!result.clean && strict) {
            System.exit(1);
        }
    }

    public record SnifferResult(boolean clean, List<String> violations, Path root) {
        @Override
        public String toString() {
            return "SnifferResult{" +
                    "clean=" + clean +
                    ", violations=" + violations +
                    '}';
        }
    }
}
