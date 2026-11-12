package dev.shaurmalib.anim.emote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Усі емоції в порядку реєстрації. Реєструються в спільному коді (і клієнт, і сервер):
 * сервер перевіряє id із запиту клієнта саме тут, кліпів він не знає.
 *
 * Фільтр — для режиму гри: «у цьому режимі лише ці емоції» ({@link #setFilter}). Відфільтрована
 * емоція не показується в колесі й не приймається сервером, але лишається в реєстрі.
 */
public final class EmoteRegistry {

    private final Map<String, Emote> emotes = new LinkedHashMap<>();
    private volatile Predicate<Emote> filter = e -> true;

    public synchronized void register(Emote e) { emotes.put(e.id(), e); }

    public synchronized void unregister(String id) { emotes.remove(id); }

    public synchronized void clear() { emotes.clear(); }

    public synchronized Emote get(String id) { return id == null ? null : emotes.get(id); }

    /** Усі, без фільтра. */
    public synchronized List<Emote> all() { return List.copyOf(emotes.values()); }

    public void setFilter(Predicate<Emote> f) { filter = f == null ? e -> true : f; }

    public boolean isAvailable(Emote e) {
        if (e == null) return false;
        try {
            return filter.test(e);
        } catch (RuntimeException ex) {
            return false; // кривий фільтр мода не валить колесо
        }
    }

    /** Доступна за id (зареєстрована і пройшла фільтр) або null. */
    public Emote available(String id) {
        Emote e = get(id);
        return isAvailable(e) ? e : null;
    }

    /** Доступні в порядку реєстрації. */
    public List<Emote> available() {
        List<Emote> out = new ArrayList<>();
        for (Emote e : all()) if (isAvailable(e)) out.add(e);
        return Collections.unmodifiableList(out);
    }

    /** Категорії доступних емоцій у порядку першої появи. */
    public List<String> categories() {
        Set<String> out = new LinkedHashSet<>();
        for (Emote e : available()) out.add(e.category());
        return List.copyOf(out);
    }

    public synchronized int size() { return emotes.size(); }
}
