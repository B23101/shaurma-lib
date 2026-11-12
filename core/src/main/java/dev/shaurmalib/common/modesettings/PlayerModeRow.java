package dev.shaurmalib.common.modesettings;

/**
 * Один рядок вкладки "Гравці" (план, п. 3.32) — перенесення 1:1 запису
 * зі списку {@code ClientGameSettingsHandler.getPlayersList()}
 * snipers_shaurma. Вкладка сама по собі — вимикна (не кожен
 * консюмер/режим потребує ручного призначення режиму конкретним
 * гравцям), див. {@code ModeSettingsScreen.Tab.PLAYERS}.
 */
public final class PlayerModeRow {

    public final String name;
    public final boolean isSpectator;

    public PlayerModeRow(String name, boolean isSpectator) {
        this.name = name;
        this.isSpectator = isSpectator;
    }
}
