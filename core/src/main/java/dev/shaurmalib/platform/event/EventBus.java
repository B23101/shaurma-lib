package dev.shaurmalib.platform.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Мінімальна синхронна шина подій без залежності від лоадера (замінює IEventBus/MinecraftForge.EVENT_BUS
 * у логіці ядра). Слухач, що кинув виняток, не зриває решту (виняток іде в {@link #errorHandler}).
 * Слухачі зареєстровані на суперклас/інтерфейс події теж спрацьовують.
 */
public final class EventBus {
    private record Entry(Class<?> type, Priority priority, Consumer<Object> fn, long seq) {}

    private final Map<Class<?>, List<Entry>> cache = new ConcurrentHashMap<>();
    private final List<Entry> all = new CopyOnWriteArrayList<>();
    private long seq;
    private volatile Consumer<Throwable> errorHandler = Throwable::printStackTrace;

    public void errorHandler(Consumer<Throwable> h) { this.errorHandler = Objects.requireNonNull(h); }

    public interface Subscription { void unsubscribe(); }

    @SuppressWarnings("unchecked")
    public synchronized <E extends Event> Subscription subscribe(Class<E> type, Priority p, Consumer<? super E> fn) {
        Entry e = new Entry(type, p, (Consumer<Object>) (Consumer<?>) fn, seq++);
        all.add(e);
        cache.clear();
        return () -> { synchronized (EventBus.this) { all.remove(e); cache.clear(); } };
    }

    public <E extends Event> Subscription subscribe(Class<E> type, Consumer<? super E> fn) {
        return subscribe(type, Priority.NORMAL, fn);
    }

    public <E extends Event> E post(E event) {
        List<Entry> list = cache.computeIfAbsent(event.getClass(), c -> {
            List<Entry> l = new ArrayList<>();
            for (Entry e : all) if (e.type.isAssignableFrom(c)) l.add(e);
            l.sort(Comparator.comparingInt((Entry e) -> e.priority.ordinal()).thenComparingLong(Entry::seq));
            return l;
        });
        for (Entry e : list) {
            try { e.fn.accept(event); } catch (Throwable t) { errorHandler.accept(t); }
        }
        return event;
    }
}
