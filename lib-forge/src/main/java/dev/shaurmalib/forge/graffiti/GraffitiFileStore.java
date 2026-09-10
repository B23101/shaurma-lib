package dev.shaurmalib.forge.graffiti;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Серверне сховище PNG-файлів графіті (план, п. 3.14) — узагальнення
 * {@code graffiti.server.GraffitiFileManager} snipers_shaurma (1:1
 * логіка), з єдиною відмінністю: {@code namespace} (раніше жорстко
 * "snipers_shaurma") тепер параметр конструктора, щоб той самий движок
 * обслуговував і snipers_shaurma, і майбутній maniac-mode кожен зі своєю
 * окремою PNG-текою, без конфлікту імен файлів між модами.
 * <p>
 * Структура на диску: {@code <world>/<namespace>/graffiti/*.png}.
 * <p>
 * Файли редагуються оператором сервера напряму на диску (кладе PNG у
 * папку) — цей клас лише читає їх за потреби. Запису мод сюди не робить
 * (окрім, за бажанням майбутньої функції завантаження з клієнта — не
 * входить у цей етап переносу, лишається можливим розширенням).
 */
public final class GraffitiFileStore {

    private static final Logger LOGGER = LogManager.getLogger("shaurma_lib/graffiti");
    private static final long MAX_FILE_SIZE = 4L * 1024 * 1024; // 4 MB запобіжник

    private final String namespace;

    public GraffitiFileStore(String namespace) {
        this.namespace = namespace;
    }

    public Path getGraffitiDir(MinecraftServer server) {
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        Path dir = worldDir.resolve(namespace + "/graffiti");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[Graffiti] Не вдалось створити папку {}", dir, e);
        }
        return dir;
    }

    /** Безпечна нормалізація імені файлу — блокує вихід за межі папки (../). */
    private static String sanitizeName(String name) {
        if (name == null) return null;
        String n = name.replace("\\", "/");
        int slash = n.lastIndexOf('/');
        if (slash >= 0) n = n.substring(slash + 1);
        if (n.isBlank() || n.contains("..")) return null;
        if (!n.toLowerCase().endsWith(".png")) return null;
        return n;
    }

    /** Список усіх доступних PNG (для меню вибору). Порядок — за іменем. */
    public List<String> listAvailable(MinecraftServer server) {
        Path dir = getGraffitiDir(server);
        List<String> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.toString().toLowerCase().endsWith(".png"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .forEach(result::add);
        } catch (IOException e) {
            LOGGER.warn("[Graffiti] Не вдалось прочитати {}", dir, e);
        }
        return result;
    }

    /** Читає байти PNG за іменем. Повертає null якщо файл відсутній/невалідний/завеликий. */
    public byte[] readImageBytes(MinecraftServer server, String imageName) {
        String safe = sanitizeName(imageName);
        if (safe == null) return null;
        Path file = getGraffitiDir(server).resolve(safe);
        if (!Files.isRegularFile(file)) return null;
        try {
            long size = Files.size(file);
            if (size <= 0 || size > MAX_FILE_SIZE) {
                LOGGER.warn("[Graffiti] Файл {} має недопустимий розмір {}", safe, size);
                return null;
            }
            return Files.readAllBytes(file);
        } catch (IOException e) {
            LOGGER.warn("[Graffiti] Помилка читання {}", safe, e);
            return null;
        }
    }

    public boolean exists(MinecraftServer server, String imageName) {
        String safe = sanitizeName(imageName);
        if (safe == null) return false;
        return Files.isRegularFile(getGraffitiDir(server).resolve(safe));
    }

    /** Дешевий "відбиток" файлу для клієнтського кешування (розмір + mtime), щоб
     *  розрізняти версії однієї й тієї ж назви після заміни адміном файлу на диску. */
    public long fingerprint(MinecraftServer server, String imageName) {
        String safe = sanitizeName(imageName);
        if (safe == null) return -1L;
        Path file = getGraffitiDir(server).resolve(safe);
        try {
            long size = Files.size(file);
            long mtime = Files.getLastModifiedTime(file).toMillis();
            return (mtime * 1_000_003L) ^ size;
        } catch (IOException e) {
            return -1L;
        }
    }
}
