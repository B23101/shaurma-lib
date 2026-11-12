package dev.shaurmalib.anim.emote;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 8 слотів колеса, які налаштовує гравець. Зберігаються локально текстом (рядок на слот):
 * <pre>
 * # ShaurmaLib: колесо емоцій
 * 0=maniacmod:wave
 * 3=maniacmod:dance
 * </pre>
 * Слот 0 — угорі, далі за годинниковою стрілкою. Порожній слот або емоція, якої немає / яку
 * вимкнув режим, у колесі показується порожньою — налаштування гравця при цьому не губиться.
 */
public final class EmoteWheel {

    public static final int SLOTS = 8;

    private final String[] ids = new String[SLOTS];
    private boolean configured;

    public String get(int slot) { return inRange(slot) ? ids[slot] : null; }

    public void set(int slot, String emoteId) {
        if (!inRange(slot)) return;
        String id = emoteId == null || emoteId.isBlank() ? null : emoteId.trim();
        if (id != null) {
            // одна емоція — один слот: якщо вже десь стоїть, міняємо місцями
            int old = indexOf(id);
            if (old >= 0 && old != slot) ids[old] = ids[slot];
        }
        ids[slot] = id;
        configured = true;
    }

    public void clear(int slot) { set(slot, null); }

    public void swap(int a, int b) {
        if (!inRange(a) || !inRange(b) || a == b) return;
        String t = ids[a];
        ids[a] = ids[b];
        ids[b] = t;
        configured = true;
    }

    public int indexOf(String emoteId) {
        if (emoteId == null) return -1;
        for (int i = 0; i < SLOTS; i++) if (emoteId.equals(ids[i])) return i;
        return -1;
    }

    /** Емоція слота з урахуванням реєстру і фільтра режиму (null — порожньо/недоступно). */
    public Emote resolve(int slot, EmoteRegistry registry) {
        return registry.available(get(slot));
    }

    /** Чи гравець уже щось налаштовував (інакше — дефолтне заповнення). */
    public boolean configured() { return configured; }

    /** Заповнити ПОРОЖНІ слоти доступними емоціями, яких ще немає в колесі (порядок реєстру). */
    public void fillEmpty(List<Emote> available) {
        Set<String> used = new HashSet<>();
        for (String s : ids) if (s != null) used.add(s);
        int next = 0;
        for (Emote e : available) {
            if (used.contains(e.id())) continue;
            while (next < SLOTS && ids[next] != null) next++;
            if (next >= SLOTS) return;
            ids[next] = e.id();
            used.add(e.id());
        }
    }

    public void reset() {
        java.util.Arrays.fill(ids, null);
        configured = false;
    }

    public List<String> ids() {
        List<String> out = new ArrayList<>(SLOTS);
        for (String s : ids) out.add(s);
        return out;
    }

    // ── Збереження ───────────────────────────────────────────────────────

    public String serialize() {
        StringBuilder sb = new StringBuilder("# ShaurmaLib: колесо емоцій (слот=id емоції; 0 — угорі, за годинниковою)\n");
        for (int i = 0; i < SLOTS; i++) {
            if (ids[i] != null) sb.append(i).append('=').append(ids[i]).append('\n');
        }
        return sb.toString();
    }

    /** Читає текст {@link #serialize()}; биті рядки пропускає. */
    public void parse(String text) {
        java.util.Arrays.fill(ids, null);
        configured = false;
        if (text == null) return;
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            try {
                int slot = Integer.parseInt(line.substring(0, eq).trim());
                String id = line.substring(eq + 1).trim();
                if (inRange(slot) && !id.isEmpty() && indexOf(id) < 0) ids[slot] = id;
            } catch (NumberFormatException ignored) {
                // битий рядок — пропустити
            }
        }
        configured = true;
    }

    /** @return false — файлу немає (колесо лишається порожнім, {@link #configured()} = false) */
    public boolean load(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                reset();
                return false;
            }
            parse(Files.readString(file, StandardCharsets.UTF_8));
            return true;
        } catch (IOException | RuntimeException e) {
            reset();
            return false;
        }
    }

    /** Атомарно (тимчасовий файл → перейменування): збій посеред запису не псує налаштування. */
    public void save(Path file) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        if (dir != null) Files.createDirectories(dir);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, serialize(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean inRange(int slot) { return slot >= 0 && slot < SLOTS; }
}
