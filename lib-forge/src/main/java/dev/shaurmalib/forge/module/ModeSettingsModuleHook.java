package dev.shaurmalib.forge.module;

import dev.shaurmalib.common.modesettings.SettingsSyncBridge;
import dev.shaurmalib.forge.mode.GameModeRegistry;
import dev.shaurmalib.forge.mode.LifecycleModule;
import dev.shaurmalib.forge.modesettings.ModeSettingsModule;
import dev.shaurmalib.forge.style.StyleTheme;

/**
 * Точка вбудовування єдиного меню налаштування режиму (план, п. 3.32) —
 * {@link ModeSettingsModule} (фабрика {@link dev.shaurmalib.forge.modesettings.ModeSettingsScreen}),
 * заміна {@code GameSettingsScreen} (1059 рядків) snipers_shaurma.
 * <p>
 * Залежить від трьох попередніх модулів (Етап 1/2 плану): розширеного
 * {@link dev.shaurmalib.forge.mode.GameModeContract} ({@code menuIcon()}/
 * {@code accentColor()} — звідки ліва панель бере іконку й колір кожного
 * режиму без хардкоду), {@link StyleTheme} (рендер-примітиви панелей/
 * tooltip-ів) і {@link LifecycleModule} (питання "чи зараз IDLE" для
 * блокування редагування під час гри).
 * <p>
 * НЕ підключається через {@code ShaurmaLib.Builder.withXxx(...)} — той
 * самий принцип, що {@link StyleModuleHook}/{@link LeaderboardModuleHook}:
 * {@link ModeSettingsModule} не має підписки на event bus, консюмер
 * створює один екземпляр і зберігає його в полі клієнтського стану,
 * викликаючи {@code newScreen(title)} з мережевого хендлера
 * {@code OpenGameSettingsPacket}-подібного пакета.
 * <p>
 * Мережевий шар (метадані налаштувань поточного режиму, збереження,
 * список гравців, зміна режиму) — відповідальність консюмера через
 * {@link SettingsSyncBridge}; бібліотека не постачає власних пакетів
 * для цього модуля (докстрінг {@link SettingsSyncBridge} пояснює чому).
 */
public interface ModeSettingsModuleHook {
    void onAttach(FMLModuleContext ctx);

    static ModeSettingsModule create(StyleTheme style, GameModeRegistry modeRegistry,
                                      LifecycleModule lifecycleModule, SettingsSyncBridge bridge,
                                      boolean showPlayersTab) {
        return new ModeSettingsModule(style, modeRegistry, lifecycleModule, bridge, showPlayersTab);
    }
}
