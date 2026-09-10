package dev.shaurmalib.common.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Декларативний опис файлів, які належать одному режиму.
 * <p>
 * Замінює захардкоджені масиви {@code defaultFiles}/{@code sharedFiles}/
 * {@code eventFiles}/{@code airplaneFiles} і перевірки виду
 * {@code "sniper_duels".equals(resourceFolder)} з оригінального
 * {@code GameModeRegistry.copyDefaultConfigs(...)} (snipers_shaurma,
 * core/registry/GameModeRegistry.java — 294 рядки, з яких ~180 це саме
 * ця умовна логіка на 4 конкретні режими).
 * <p>
 * Кожен режим (SC/SR/SCN/SD у snipers, і будь-який режим у maniac-mode)
 * описує один такий об'єкт сам — {@link ShaurmaConfigTree} лише виконує
 * "скопіювати з ресурсів якщо файла ще нема, ніколи не перезаписувати
 * існуючий" для кожного шляху зі списку {@link #modeFiles()}, і окремо —
 * для {@link #sharedFiles()} у батьківську (namespace-рівня) папку.
 * <p>
 * Шляхи можуть бути вкладеними (наприклад {@code "events/money_zones.yml"}
 * або {@code "airplane/character_1_path.json"}) — {@link ShaurmaConfigTree}
 * створює батьківські директорії автоматично, як робив оригінальний метод
 * для {@code eventFiles}/{@code airplaneFiles}.
 */
public final class ModeConfigDescriptor {

    private final String modeFolder;
    private final List<String> modeFiles;
    private final List<String> sharedFiles;

    private ModeConfigDescriptor(String modeFolder, List<String> modeFiles, List<String> sharedFiles) {
        this.modeFolder = modeFolder;
        this.modeFiles = Collections.unmodifiableList(modeFiles);
        this.sharedFiles = Collections.unmodifiableList(sharedFiles);
    }

    /** Назва підпапки режиму в дереві конфігів (наприклад "sniper_duels"). */
    public String modeFolder() {
        return modeFolder;
    }

    /**
     * Файли, специфічні для цього режиму — копіюються в
     * {@code <namespace>/<modeFolder>/<file>}. Може містити вкладені шляхи
     * (наприклад "airplane/character_1_path.json").
     */
    public List<String> modeFiles() {
        return modeFiles;
    }

    /**
     * Файли, спільні для всіх режимів одного namespace (наприклад
     * sounds.yml, messages.yml, radio_dialogs.yml — у snipers_shaurma це
     * зараз захардкоджений масив {@code sharedFiles} в GameModeRegistry) —
     * копіюються в {@code <namespace>/<file>}, тобто в батьківську папку
     * відносно {@link #modeFolder()}.
     * <p>
     * Кілька режимів одного namespace можуть перелічити один і той самий
     * спільний файл — {@link ShaurmaConfigTree} копіює його лише один раз
     * (перевірка "файл вже існує" природно дедуплікує повторні виклики,
     * так само як в оригінальному коді).
     */
    public List<String> sharedFiles() {
        return sharedFiles;
    }

    public static Builder builder(String modeFolder) {
        return new Builder(modeFolder);
    }

    public static final class Builder {
        private final String modeFolder;
        private final List<String> modeFiles = new ArrayList<>();
        private final List<String> sharedFiles = new ArrayList<>();

        private Builder(String modeFolder) {
            this.modeFolder = modeFolder;
        }

        public Builder modeFile(String path) {
            modeFiles.add(path);
            return this;
        }

        public Builder modeFiles(String... paths) {
            Collections.addAll(modeFiles, paths);
            return this;
        }

        public Builder sharedFile(String path) {
            sharedFiles.add(path);
            return this;
        }

        public Builder sharedFiles(String... paths) {
            Collections.addAll(sharedFiles, paths);
            return this;
        }

        public ModeConfigDescriptor build() {
            return new ModeConfigDescriptor(modeFolder, modeFiles, sharedFiles);
        }
    }
}
