package dev.shaurmalib.platform;

import dev.shaurmalib.platform.service.LootDropper;
import dev.shaurmalib.platform.service.TeleportBackend;
import dev.shaurmalib.platform.service.TextBridge;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Єдина точка, через яку ядро отримує все залежне від гри/лоадера.
 * <p>
 * Хто що ставить: адаптер версії ({@code mc-1.21.1} тощо) реєструє бекенди,
 * що залежать від ванілі; клей лоадера ({@code fabric}/{@code forge}/{@code neoforge})
 * реєструє {@link PlatformInfo}, події та мережу. Ядро лише читає.
 * Свідомо без {@code ServiceLoader}: порядок ініціалізації явний, тести підміняють
 * бекенди одним викликом.
 */
public final class Platform {
    private static volatile PlatformInfo info;
    private static volatile TeleportBackend teleport;
    private static volatile LootDropper loot;
    private static volatile TextBridge text;

    /**
     * Розширювані бекенди: новий ігровий сервіс = новий інтерфейс у core + {@link #register}, без зміни сигнатури
     * {@link #install}. Реєструє адаптер версії/лоадера, ядро читає через {@link #service}.
     */
    private static final Map<Class<?>, Object> SERVICES = new ConcurrentHashMap<>();

    private Platform() {}

    public static <T> void register(Class<T> type, T impl) {
        SERVICES.put(Objects.requireNonNull(type), Objects.requireNonNull(impl));
    }

    public static <T> T service(Class<T> type) {
        return type.cast(require(SERVICES.get(type), type.getSimpleName()));
    }

    public static <T> Optional<T> findService(Class<T> type) { return Optional.ofNullable(type.cast(SERVICES.get(type))); }

    public static void install(PlatformInfo i, TeleportBackend t, LootDropper l, TextBridge tb) {
        info = Objects.requireNonNull(i);
        teleport = Objects.requireNonNull(t);
        loot = Objects.requireNonNull(l);
        text = Objects.requireNonNull(tb);
        register(TeleportBackend.class, t);
        register(LootDropper.class, l);
        register(TextBridge.class, tb);
    }

    public static PlatformInfo info() { return require(info, "PlatformInfo"); }
    public static TeleportBackend teleport() { return require(teleport, "TeleportBackend"); }
    public static LootDropper loot() { return require(loot, "LootDropper"); }
    public static TextBridge text() { return require(text, "TextBridge"); }

    /** Лише для тестів. */
    public static void reset() { info = null; teleport = null; loot = null; text = null; SERVICES.clear(); }

    private static <T> T require(T v, String what) {
        if (v == null) throw new IllegalStateException(
                "Platform не ініціалізована (" + what + "): адаптер версії/лоадера не викликав Platform.install(...)");
        return v;
    }
}
