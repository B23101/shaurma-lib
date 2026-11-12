package dev.shaurmalib.common.playeranim;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реєстр {@link PoseAction} за ім'ям (той самий стиль, що
 * {@link dev.shaurmalib.common.item.ItemDefinitionRegistry}).
 * <p>
 * Дубль імені — це майже завжди конфлікт двох модів або помилка
 * копіпасту, тому {@link #register} кидає {@link IllegalStateException}
 * (а не мовчки перезаписує). Свідома заміна — {@link #replace}.
 */
public final class PoseActionRegistry {

    private static final Map<String, PoseAction> REGISTRY = new ConcurrentHashMap<>();

    private PoseActionRegistry() {}

    /** @throws IllegalStateException якщо дія з таким іменем уже зареєстрована */
    public static void register(PoseAction action) {
        PoseAction previous = REGISTRY.putIfAbsent(action.name(), action);
        if (previous != null) {
            throw new IllegalStateException("PoseAction '" + action.name()
                    + "' уже зареєстровано (шар " + previous.layer() + "). "
                    + "Для свідомої заміни використайте PoseActionRegistry.replace(...).");
        }
    }

    /** Реєструє або замінює. Повертає попередню дію, якщо була. */
    public static Optional<PoseAction> replace(PoseAction action) {
        return Optional.ofNullable(REGISTRY.put(action.name(), action));
    }

    public static Optional<PoseAction> get(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(REGISTRY.get(name));
    }

    public static boolean isRegistered(String name) {
        return name != null && REGISTRY.containsKey(name);
    }

    public static Set<String> names() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    /** Для тестів/hot-reload у dev-середовищі; звичайний runtime цим не користується. */
    public static void clear() {
        REGISTRY.clear();
    }
}
