package dev.shaurmalib.common.item;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реєстр {@link ItemDefinition} по класу предмета — узагальнення
 * {@code AnimatedItemSystem.REGISTRY}/{@code register}/{@code get}.
 * <p>
 * Кожен мод-споживач (snipers, maniac) реєструє СВОЇ предмети сюди при
 * старті; сам движок ({@link UseSessionEngine}) і рендерер лише читають.
 */
public final class ItemDefinitionRegistry {

    private static final Map<Class<?>, ItemDefinition<?, ?>> REGISTRY = new ConcurrentHashMap<>();

    private ItemDefinitionRegistry() {}

    public static <P, H> void register(Class<?> itemClass, ItemDefinition<P, H> def) {
        REGISTRY.put(itemClass, def);
    }

    @SuppressWarnings("unchecked")
    public static <P, H> Optional<ItemDefinition<P, H>> get(Class<?> itemClass) {
        return Optional.ofNullable((ItemDefinition<P, H>) REGISTRY.get(itemClass));
    }

    public static boolean isRegistered(Class<?> itemClass) {
        return REGISTRY.containsKey(itemClass);
    }

    /** Для тестів/hot-reload у dev-середовищі; звичайний runtime цим не користується. */
    public static void clear() {
        REGISTRY.clear();
    }
}
