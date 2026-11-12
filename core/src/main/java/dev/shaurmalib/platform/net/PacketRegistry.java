package dev.shaurmalib.platform.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Реєстр пакетів, відсортований за явним id на дроті (нові — лише в кінець, id не перевикористовувати).
 * Адаптер лоадера/версії проходить {@link #all()} і реєструє кожен у рідному каналі
 * (SimpleChannel / CustomPacketPayload / Fabric Networking).
 */
public final class PacketRegistry {
    private static final List<PacketType<?>> TYPES = new ArrayList<>();
    public static final String PROTOCOL_VERSION = "2";

    private PacketRegistry() {}

    public static synchronized <T> PacketType<T> register(PacketType<T> t) {
        for (PacketType<?> o : TYPES) {
            if (o.id() == t.id()) throw new IllegalStateException("Duplicate packet id " + t.id() + ": " + o.name() + " / " + t.name());
        }
        TYPES.add(t);
        TYPES.sort(java.util.Comparator.comparingInt(PacketType::id));
        return t;
    }
    public static synchronized List<PacketType<?>> all() { return Collections.unmodifiableList(new ArrayList<>(TYPES)); }
    @SuppressWarnings("unchecked")
    public static synchronized <T> PacketType<T> byClass(Class<T> c) {
        for (PacketType<?> t : TYPES) if (t.type() == c) return (PacketType<T>) t;
        throw new IllegalArgumentException("Unregistered packet: " + c);
    }
}
