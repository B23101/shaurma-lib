package dev.shaurmalib.forge.mode;

import dev.shaurmalib.common.config.ConfigException;
import dev.shaurmalib.common.config.ConfigReloadBus;
import dev.shaurmalib.common.config.ShaurmaConfigTree;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Лібова версія {@code org.example.snipers_shaurma.core.registry.GameModeRegistry}.
 * <p>
 * Одна інстанція на одного споживача (namespace) — на відміну від оригіналу,
 * що був повністю статичним (single-consumer за дизайном; для двох модів,
 * що обидва хочуть свій набір режимів, статичний реєстр був би спільним і
 * конфліктував би). Кожен {@code @Mod}-споживач створює свій
 * {@code new GameModeRegistry(...)} через {@code ShaurmaLib.Builder}.
 * <p>
 * <b>Виправлений задокументований баг</b> (п. 3.18 плану): оригінальний
 * {@code setActive(String, Path)} викликав
 * {@code active.onModeDeactivated(server)} де {@code server} діставався через
 * {@code ServerLifecycleHooks.getCurrentServer()} — це могло повернути
 * {@code null} у вузькому вікні між зупинкою і повним стартом сервера, і код
 * після цього продовжував (частина деактивації просто мовчки пропускалась
 * через {@code if (server != null)}). Лібова версія вимагає non-null
 * {@link MinecraftServer} явним {@link Objects#requireNonNull} — якщо виклик
 * стався в невідповідний момент lifecycle, це кидає зрозумілий
 * {@link IllegalStateException} одразу, а не тихо ковтає частину логіки
 * очищення (що в оригіналі призводило до "застряглих" персистентних даних,
 * наприклад ordered crate координат, які ніколи не очищались).
 */
public final class GameModeRegistry {

    private static final Logger LOGGER = LogManager.getLogger("ShaurmaLib/GameModeRegistry");

    private final Map<String, GameModeContract> modes = new LinkedHashMap<>();
    private final List<ModeDeactivationListener> deactivationListeners = new CopyOnWriteArrayList<>();
    private final ConfigReloadBus reloadBus;
    private final ShaurmaConfigTree configTree;
    private final String modeStateFileName;

    private GameModeContract active;

    /**
     * @param configTree        дерево конфігів namespace цього споживача.
     * @param reloadBus         шина reload-подій (3.1) — публікується після
     *                          кожного успішного {@link #setActive}/{@link #reloadCurrentModeConfig}.
     * @param modeStateFileName ім'я файлу персистенції обраного режиму в
     *                          корені world (в оригіналі — {@code "mode.yml"}).
     */
    public GameModeRegistry(ShaurmaConfigTree configTree, ConfigReloadBus reloadBus, String modeStateFileName) {
        this.configTree = Objects.requireNonNull(configTree, "configTree");
        this.reloadBus = Objects.requireNonNull(reloadBus, "reloadBus");
        this.modeStateFileName = Objects.requireNonNull(modeStateFileName, "modeStateFileName");
    }

    public void register(GameModeContract mode) {
        modes.put(mode.id(), mode);
        // Перший зареєстрований режим є дефолтним — та сама поведінка, що в оригіналі.
        if (active == null) active = mode;
        LOGGER.info("Зареєстровано режим: {}", mode.id());
    }

    /** Підписатись на подію деактивації режиму (заміна хардкоду в оригінальному setActive). */
    public void onDeactivation(ModeDeactivationListener listener) {
        deactivationListeners.add(listener);
    }

    public GameModeContract current() {
        if (active == null) throw new IllegalStateException("Жоден режим не зареєстровано!");
        return active;
    }

    public Collection<GameModeContract> all() {
        return Collections.unmodifiableCollection(modes.values());
    }

    public Optional<GameModeContract> byId(String id) {
        return Optional.ofNullable(modes.get(id));
    }

    /**
     * Ініціалізація при старті сервера. Читає збережений режим або лишає
     * дефолт (перший зареєстрований). Копіює дефолти конфігів УСІХ
     * зареєстрованих режимів (щоб адмін бачив усі файли одразу — та сама
     * поведінка, що в оригіналі), і завантажує конфіг лише активного.
     */
    public void initPersistence() {
        String savedId = configTree.loadSingleValue(modeStateFileName, "mode");
        if (savedId != null && modes.containsKey(savedId)) {
            active = modes.get(savedId);
            LOGGER.info("Завантажено збережений режим: {}", savedId);
        } else {
            saveCurrentMode();
            LOGGER.info("Збережено дефолтний режим: {}", active != null ? active.id() : "none");
        }

        for (GameModeContract mode : modes.values()) {
            configTree.copyModeDefaults(mode.configDescriptor());
        }

        loadActiveConfigOrThrow();
        LOGGER.info("Активний режим: {} (конфіг: {})", active.id(), configTree.modeDir(active.configFolder()));
    }

    /**
     * Перемикання режиму — тільки в IDLE (перевірку IDLE виконує споживач
     * ДО виклику цього методу, наприклад через lifecycle-модуль 3.19;
     * реєстр режимів не знає нічого про {@code MatchLifecycleState}).
     * Автоматично перезавантажує конфіги нового режиму.
     *
     * @param server поточний {@link MinecraftServer}. Ніколи не {@code null} —
     *               на відміну від оригіналу, який діставав його сам через
     *               {@code ServerLifecycleHooks.getCurrentServer()} і міг
     *               отримати {@code null} у вузькому вікні lifecycle.
     *               Викликач (lib-forge command/menu hook) зобов'язаний
     *               передати реальний сервер, отриманий у контексті, де
     *               його наявність гарантована (наприклад з
     *               {@code CommandSourceStack.getServer()}).
     */
    public boolean setActive(String id, MinecraftServer server) {
        Objects.requireNonNull(server, "server — реєстр більше не приймає null (виправлений баг, див. клас-докстрінг)");

        GameModeContract next = modes.get(id);
        if (next == null) {
            LOGGER.warn("Невідомий режим: {}", id);
            return false;
        }

        if (active != null && active != next) {
            GameModeContract previous = active;
            previous.onModeDeactivated(server);
            for (ModeDeactivationListener listener : deactivationListeners) {
                listener.onModeDeactivated(server);
            }
        }

        active = next;
        configTree.copyModeDefaults(active.configDescriptor());
        loadActiveConfigOrThrow();
        saveCurrentMode();
        reloadBus.fireReload(active.id());
        LOGGER.info("Активний режим змінено на: {}", id);
        return true;
    }

    /** Перевантажує конфіг поточного активного режиму (виклик перед стартом матчу). */
    public void reloadCurrentModeConfig() {
        if (active == null) return;
        loadActiveConfigOrThrow();
        reloadBus.fireReload(active.id());
        LOGGER.info("Перезавантажено конфіг режиму: {}", active.id());
    }

    private void loadActiveConfigOrThrow() {
        Path modeConfigDir = configTree.modeDir(active.configFolder());
        try {
            active.loadConfig(modeConfigDir);
        } catch (ConfigException e) {
            LOGGER.error("Помилка завантаження конфігу {}: {}", active.id(), e.getMessage());
            throw e;
        }
    }

    public void saveCurrentMode() {
        if (active == null) return;
        configTree.saveSingleValue(modeStateFileName, "mode", active.id());
    }

    /**
     * Зручний хелпер для контекстів, де {@link MinecraftServer} треба дістати
     * через {@link ServerLifecycleHooks}, а не з прямого контексту виклику
     * (наприклад планувальник, що не має {@code CommandSourceStack}).
     * <p>
     * На відміну від оригінального коду, тут відсутність сервера НЕ
     * ігнорується мовчки — виклик кидає {@link IllegalStateException}, щоб
     * місце помилки було очевидним одразу, а не через "застряглі" дані,
     * які проявляться лише пізніше.
     */
    public boolean setActiveUsingCurrentServer(String id) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException(
                    "setActiveUsingCurrentServer() викликано поза lifecycle сервера (server == null). " +
                    "Викликайте setActive(id, server) напряму там, де сервер вже гарантовано доступний.");
        }
        return setActive(id, server);
    }
}
