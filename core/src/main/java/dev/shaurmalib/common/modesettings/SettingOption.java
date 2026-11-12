package dev.shaurmalib.common.modesettings;

/**
 * Один варіант значення налаштування (план, п. 3.32) — перенесення 1:1
 * {@code GameSettingsScreen.SettingOption} snipers_shaurma. Нуль
 * Minecraft/Forge-типів: {@code displayText} — ключ перекладу (не сирий
 * текст), який споживач резолвить через {@code Component.translatable(...)}
 * на клієнтському боці (сама бібліотека не хардкодить жодного тексту —
 * той самий принцип "без хардкоду тексту" з докстрінгу оригіналу).
 */
public final class SettingOption {

    public final int value;
    public final String displayTextKey;
    /**
     * Шлях іконки опції — той самий формат, що {@link SettingDefinition#iconPath},
     * {@code null}, якщо опція не має власної іконки (типовий випадок:
     * лише текст).
     */
    public final String iconPath;

    public SettingOption(int value, String displayTextKey, String iconPath) {
        this.value = value;
        this.displayTextKey = displayTextKey;
        this.iconPath = iconPath;
    }

    public SettingOption(int value, String displayTextKey) {
        this(value, displayTextKey, null);
    }
}
