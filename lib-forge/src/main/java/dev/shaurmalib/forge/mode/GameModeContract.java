package dev.shaurmalib.forge.mode;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import dev.shaurmalib.common.config.ConfigException;
import dev.shaurmalib.common.config.ModeConfigDescriptor;

import java.util.Optional;

/**
 * Контракт режиму — переїзд {@code org.example.snipers_shaurma.core.api.GameMode}
 * у бібліотеку 1:1 по API, з розширенням із п. 3.32 плану, додаваним одразу
 * на цьому кроці (а не пізніше), щоб не переробляти інтерфейс вдруге:
 * {@link #menuIcon()} і {@link #accentColor()} — потрібні для лівої панелі
 * вибору режиму в {@code ModeSettingsScreen} (модуль 3.32, майбутній етап).
 * <p>
 * {@code createStats}/{@code createActivePhase} з оригінального {@code GameMode}
 * НЕ переносяться в контракт бібліотеки: обидва повертають типи
 * ({@code ModeStats}, {@code IPhase} з параметром {@code MatchSession}), які є
 * снайперс-специфічними доменними класами (економіка, кіти, MVP-очки — не
 * узагальнений бібліотечний концепт). Життєвий цикл фаз лишається повністю
 * на боці мода-споживача; бібліотека керує лише тим, ЩО спільне для всіх
 * режимів (id/назва/іконка/колір/конфіг-папка/можливості/persistence),
 * а не ЯК режим влаштований всередині.
 */
public interface GameModeContract {

    /** Унікальний ідентифікатор режиму: "sc", "sr", і т.д. */
    String id();

    /** Відображуване ім'я: "Sniper Contract", "Sniper Royal" і т.д. */
    String displayName();

    /** Назва підпапки конфігів (використовується як {@code modeFolder} у {@link ModeConfigDescriptor}). */
    String configFolder();

    /**
     * Іконка режиму для лівої панелі вибору в {@code ModeSettingsScreen}
     * (модуль 3.32). Пряме перенесення значень з існуючих
     * {@code ICON_SC}/{@code ICON_BR}/{@code ICON_SCN}/{@code ICON_SD}
     * ресурсів мода-споживача — сама бібліотека не постачає жодних текстур.
     */
    ResourceLocation menuIcon();

    /** Колір панелі/рамки/hover для цього режиму в меню налаштувань (ARGB int). */
    int accentColor();

    /** Дескриптор файлів конфігу цього режиму — заміна захардкоджених масивів. */
    ModeConfigDescriptor configDescriptor();

    /** Завантаження конфігів з папки режиму. Без fallback — {@link ConfigException}, якщо файл відсутній і обов'язковий. */
    void loadConfig(java.nio.file.Path modeConfigDir) throws ConfigException;

    // ── Можливості режиму (1:1 з оригінальним GameMode) ──────────────────

    boolean canRespawn();
    boolean hasEconomy();
    boolean hasKits();
    boolean hasKitSelectPhase();

    /**
     * Режим завжди командний, незалежно від game.yml (наприклад SCN: 2 або 4
     * команди визначаються team_layout, а не загальним teamMode).
     */
    default boolean isAlwaysTeamMode() { return false; }

    /**
     * Режим використовує локацію "літак" (модель у небі + дроп гравців) —
     * впливає на те, чи треба розміщувати/знімати відповідну схему на старті
     * раунду. Дефолт false для режимів без цієї механіки.
     */
    default boolean usesAirplaneSchematic() { return false; }

    /** Режим-специфічні підкоманди кореневої команди бібліотеки (модуль 3.30). */
    Optional<ModeCommandContribution> getModeCommand();

    void onServerStarted(MinecraftServer server);
    void onModeActivated(MinecraftServer server);
    void onModeDeactivated(MinecraftServer server);
}
