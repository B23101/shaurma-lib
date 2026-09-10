package dev.shaurmalib.forge.modesettings;

import dev.shaurmalib.common.modesettings.SettingsSyncBridge;
import dev.shaurmalib.forge.mode.GameModeRegistry;
import dev.shaurmalib.forge.mode.LifecycleModule;
import dev.shaurmalib.forge.style.StyleTheme;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Робочий модуль (план, п. 3.32) — тонкий контейнер, що тримає
 * залежності {@link ModeSettingsScreen} (стиль/реєстр режимів/lifecycle/
 * міст даних) і фабрикує готові екземпляри екрана, замість того щоб
 * консюмер збирав конструктор {@link ModeSettingsScreen} вручну кожного
 * разу, коли гравець відкриває меню налаштувань (в оригіналі —
 * {@code new GameSettingsScreen()} у {@code OpenGameSettingsPacket.ClientHandler}).
 * <p>
 * Той самий принцип, що {@link dev.shaurmalib.forge.lobby.LobbyModule}:
 * не вирішує ЗА мод, КОЛИ показати екран (окрема відповідальність
 * консюмера, типово мережевий пакет-хендлер) — лише збирає залежності
 * один раз при {@code ShaurmaLib.Builder.withModeSettings(...)}.
 */
public final class ModeSettingsModule {

    private final StyleTheme style;
    private final GameModeRegistry modeRegistry;
    private final LifecycleModule lifecycleModule;
    private final SettingsSyncBridge bridge;
    private final boolean showPlayersTab;

    private Runnable hoverSound;
    private Runnable clickSound;

    public ModeSettingsModule(StyleTheme style, GameModeRegistry modeRegistry,
                               LifecycleModule lifecycleModule, SettingsSyncBridge bridge,
                               boolean showPlayersTab) {
        this.style = Objects.requireNonNull(style, "style");
        this.modeRegistry = Objects.requireNonNull(modeRegistry, "modeRegistry");
        this.lifecycleModule = Objects.requireNonNull(lifecycleModule, "lifecycleModule");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.showPlayersTab = showPlayersTab;
    }

    /** Опційні hover/click звуки, застосовані до кожного нового екрана, зроблюваного {@link #newScreen(Component)}. */
    public void setSounds(Runnable hoverSound, Runnable clickSound) {
        this.hoverSound = hoverSound;
        this.clickSound = clickSound;
    }

    /**
     * Створює новий екземпляр екрана — викликати з мережевого хендлера
     * консюмера (типово {@code Minecraft.getInstance().setScreen(module.newScreen(title))}
     * після прийому пакета з поточними значеннями налаштувань).
     */
    public ModeSettingsScreen newScreen(Component title) {
        ModeSettingsScreen screen = new ModeSettingsScreen(
                title, style, modeRegistry, lifecycleModule.lifecycleBus(), bridge, showPlayersTab);
        screen.setSounds(hoverSound, clickSound);
        return screen;
    }
}
