package dev.shaurmalib.common.mode;

import dev.shaurmalib.common.config.ConfigException;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Лібова версія {@code org.example.snipers_shaurma.core.api.GameMode}.
 * <p>
 * Перенесення 1:1 по формі — оригінальний {@code GameMode} інтерфейс вже
 * був спроєктований мод-агностично (єдина snipers-специфіка була у
 * параметрах-типах {@code MatchSession}/{@code IPhase}, які самі є
 * snipers-класами і НЕ переносяться в lib — див. план, розділ 4). Тому тут
 * {@code createActivePhase}/{@code createStats} узагальнені через
 * {@code <S, P>} — кожен споживач (snipers_shaurma, maniac-mode) підставляє
 * власні типи сесії/фази при реалізації контракту, а бібліотека
 * (GameModeRegistry, ModeSettingsScreen тощо) працює лише з методами
 * контракту, що не залежать від конкретного типу сесії
 * ({@code id()}, {@code configFolder()}, прапорці можливостей і т.д.).
 * <p>
 * Розширення з плану (розділ 3.32, "налаштування режимів це іконка, колір,
 * id, назва") додано одразу тут, на Етапі 1 — щоб не переробляти контракт
 * вдруге, коли дійде черга до {@code ModeSettingsScreen} (Етап 2, крок 15):
 * {@link #menuIcon()} і {@link #accentColor()}.
 *
 * @param <S> тип ігрової сесії споживача (snipers: {@code MatchSession}).
 * @param <P> тип активної фази споживача (snipers: {@code IPhase}).
 */
public interface GameModeContract<S, P> {

    /** Унікальний ідентифікатор режиму: "sc", "sr", "scn", "sd", або довільний у maniac-mode. */
    String id();

    /** Відображуване ім'я: "Sniper Contract", "Sniper Royal", і т.д. */
    String displayName();

    /** Назва підпапки конфігів усередині namespace споживача: "sniper_contract". */
    String configFolder();

    /**
     * Іконка для лівої панелі вибору режиму в {@code ModeSettingsScreen}
     * (план 3.32) — заміна захардкоджених {@code ICON_SC}/{@code ICON_BR}/
     * {@code ICON_SCN}/{@code ICON_SD} ресурсів у поточному
     * {@code GameSettingsScreen.java}. Кожен режим (і snipers, і maniac)
     * повертає свій {@code ResourceLocation} у власному namespace.
     */
    Object menuIcon();

    /**
     * Колір панелі/рамки/hover для цього режиму в {@code ModeSettingsScreen}
     * (план 3.32) — ARGB int, як приймає Minecraft {@code GuiGraphics.fill(...)}.
     */
    int accentColor();

    /** Завантаження конфігів з папки режиму. Без fallback — {@link ConfigException}, якщо файл відсутній. */
    void loadConfig(Path modeConfigDir) throws ConfigException;

    /** Фабричний метод для статистики гравця цього режиму. */
    S createStats(java.util.UUID playerUuid);

    /** Фабричний метод для активної фази режиму. */
    P createActivePhase(Object session, Object level);

    // ── Можливості режиму (перенесено 1:1 з оригінального GameMode) ──────

    boolean canRespawn();
    boolean hasEconomy();
    boolean hasKits();
    boolean hasKitSelectPhase();

    /**
     * Режим завжди командний — session.teamMode примусово = true.
     * Дефолт {@code false}, як в оригіналі (SC/SR визначають з game.yml,
     * SCN перевизначає в {@code true}).
     */
    default boolean isAlwaysTeamMode() {
        return false;
    }

    /**
     * Режим використовує локацію "літак" (модель у небі + дроп гравців
     * з неї) — на COUNTDOWN_1 треба розмістити схему літака. Дефолт
     * {@code false}, як в оригіналі (наразі тільки SC = true).
     */
    default boolean usesAirplaneSchematic() {
        return false;
    }

    /** Режим-специфічні підкоманди кореневої команди бібліотеки (план 3.30). */
    Optional<?> getModeCommand();

    void onServerStarted(MinecraftServer server);

    void onModeActivated(MinecraftServer server);

    void onModeDeactivated(MinecraftServer server);
}
