package dev.shaurmalib.meta.profile;

import dev.shaurmalib.meta.io.J;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Файлове сховище профілів: {@code <dir>/<uuid>.json}. Запис атомарний (tmp → move), щоб обрив сервера
 * посеред запису не лишив гравця без профілю. Шлях дає платформа ({@code <світ>/data/shaurma_meta/<режим>}).
 */
public final class ProfileStore {

    private static final Logger LOG = Logger.getLogger("ShaurmaMeta");

    private final Supplier<Path> dir;

    public ProfileStore(Supplier<Path> dir) { this.dir = dir; }

    private Path file(UUID id) { return dir.get().resolve(id + ".json"); }

    /** @return профіль або порожній (ніколи null) */
    public MetaProfile load(UUID id) {
        Path f = file(id);
        if (!Files.exists(f)) return new MetaProfile(id);
        try {
            return MetaProfile.fromJson(id, J.parse(Files.readString(f, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            // Пошкоджений файл не стираємо: відкладаємо поруч, щоб адмін міг відновити вручну.
            LOG.log(Level.WARNING, "профіль " + id + " не читається, збережено копію .broken", e);
            try { Files.copy(f, f.resolveSibling(id + ".json.broken"), StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) { }
            return new MetaProfile(id);
        }
    }

    public void save(MetaProfile p) {
        try {
            Path d = dir.get();
            Files.createDirectories(d);
            Path tmp = d.resolve(p.id + ".json.tmp");
            Files.writeString(tmp, J.pretty(p.toJson(true)), StandardCharsets.UTF_8);
            Files.move(tmp, file(p.id), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Path d = dir.get();
                Files.move(d.resolve(p.id + ".json.tmp"), file(p.id), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                LOG.log(Level.SEVERE, "профіль " + p.id + " не збережено", e2);
            }
        }
    }

    /** Усі збережені профілі (для лідербордів). */
    public List<UUID> ids() {
        List<UUID> out = new ArrayList<>();
        Path d = dir.get();
        if (!Files.isDirectory(d)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(d, "*.json")) {
            for (Path f : ds) {
                String n = f.getFileName().toString();
                try { out.add(UUID.fromString(n.substring(0, n.length() - 5))); } catch (IllegalArgumentException ignored) { }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "не вдалося прочитати теку профілів", e);
        }
        return out;
    }
}
