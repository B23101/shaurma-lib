package dev.shaurmalib.common.modesettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Рядок налаштування (план, п. 3.32) — узагальнення
 * {@code GameSettingsScreen.Setting} snipers_shaurma. Іконка/назва/список
 * можливих значень/опційний tooltip-опис — рівно те, що ти сформулював як
 * "налаштування режимів це іконка, колір, id, назва" (тут — рядка
 * налаштування, колір/іконка режиму окремо на {@code GameModeContract},
 * див. {@code menuIcon()}/{@code accentColor()}).
 * <p>
 * Мод-споживач будує список через {@link #builder(String)} — бібліотека
 * лише рендерить те, що отримала, і зберігає обране значення (мережевий
 * шар — відповідальність консюмера, п. 3.32 плану: "лібу лише рендерить
 * список і зберігає значення через GameSettingsSavePacket-подібний
 * механізм", сам пакет — не бібліотечний, консюмер публікує його своїм
 * каналом).
 */
public final class SettingDefinition {

    public final String id;
    public final String displayNameKey;
    public final String iconPath;
    public final String descriptionKey;
    public final int defaultValue;
    public final List<SettingOption> options;

    private SettingDefinition(String id, String displayNameKey, String iconPath,
                               String descriptionKey, int defaultValue, List<SettingOption> options) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayNameKey = Objects.requireNonNull(displayNameKey, "displayNameKey");
        this.iconPath = iconPath;
        this.descriptionKey = descriptionKey;
        this.defaultValue = defaultValue;
        this.options = Collections.unmodifiableList(new ArrayList<>(options));
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private String displayNameKey;
        private String iconPath;
        private String descriptionKey;
        private int defaultValue;
        private final List<SettingOption> options = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder displayName(String translationKey) {
            this.displayNameKey = translationKey;
            return this;
        }

        /** Шлях іконки — {@code "item:<modid>:<path>"} для предметів або {@code "<modid>:<texture path>"} для текстур (той самий формат, що в оригіналі). */
        public Builder icon(String iconPath) {
            this.iconPath = iconPath;
            return this;
        }

        public Builder description(String translationKey) {
            this.descriptionKey = translationKey;
            return this;
        }

        public Builder option(int value, String displayTextKey) {
            options.add(new SettingOption(value, displayTextKey));
            return this;
        }

        public Builder option(int value, String displayTextKey, String iconPath) {
            options.add(new SettingOption(value, displayTextKey, iconPath));
            return this;
        }

        /** Значення, з яким рядок відкриється, якщо консюмер ще не завантажив реальне поточне значення. */
        public Builder defaultValue(int value) {
            this.defaultValue = value;
            return this;
        }

        public SettingDefinition build() {
            if (displayNameKey == null) {
                throw new IllegalStateException("SettingDefinition '" + id + "' не має displayName(...)");
            }
            return new SettingDefinition(id, displayNameKey, iconPath, descriptionKey, defaultValue, options);
        }
    }
}
