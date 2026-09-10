package dev.shaurmalib.forge.radio;

import dev.shaurmalib.common.config.ShaurmaConfigTree;
import dev.shaurmalib.common.radio.RadioDialogRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * RadioDialogConfigLoader (план, п. 3.33) — узагальнення
 * {@code RadioDialogConfig.load(...)} snipers_shaurma над
 * {@link ShaurmaConfigTree}.
 * <p>
 * {@code radio_dialogs.yml} лишається СПІЛЬНИМ файлом на весь namespace
 * (не per-mode), рівно як в оригіналі ({@code sharedFiles} у
 * {@code GameModeRegistry}) — тому копіювання дефолту з jar-ресурсів
 * консюмера НЕ дублюється тут: воно вже відбувається природно через
 * {@link ShaurmaConfigTree#copyModeDefaults(dev.shaurmalib.common.config.ModeConfigDescriptor)},
 * якщо консюмер додав {@code "radio_dialogs.yml"} до {@code sharedFile(...)}
 * свого {@code ModeConfigDescriptor} (кожен режим може перелічити той
 * самий спільний файл — {@link ShaurmaConfigTree} копіює його лише
 * один раз, повторні виклики природно no-op завдяки перевірці
 * "файл вже існує"). Цей клас лише ЧИТАЄ вже наявний файл з
 * {@link ShaurmaConfigTree#namespaceDir()} і оновлює {@link RadioDialogRegistry}.
 * <p>
 * Якщо консюмер з якоїсь причини не викликав {@code copyModeDefaults(...)}
 * взагалі (наприклад підключив лише радіо-модуль без жодного
 * {@code GameModeContract}) — {@link #load} сам створює порожній
 * валідний конфіг як резервний варіант, щоб адмін мав де дописати
 * репліки, а не отримав виняток при старті сервера.
 */
public final class RadioDialogConfigLoader {

    private static final Logger LOGGER = LogManager.getLogger("shaurma_lib/radio");
    private static final String FILE_NAME = "radio_dialogs.yml";

    private static final String EMPTY_DEFAULT = """
            settings:
              default_language: uk_ua
              duck_ratio: 0.30
              model_item: ""

            dialogs: {}
            """;

    private RadioDialogConfigLoader() {}

    /**
     * Завантажує {@code radio_dialogs.yml} з кореня namespace і оновлює
     * {@link RadioDialogRegistry}. Викликати після
     * {@code configTree.copyModeDefaults(...)} для хоча б одного режиму
     * (типово — на старті сервера, разом з рештою reload-виклику
     * консюмера), і повторно на кожен {@code /reload}-подібний виклик
     * консюмера — рекомендовано підписати цей метод на
     * {@code ConfigModule.reloadBus()}.
     */
    public static void load(ShaurmaConfigTree configTree) {
        java.nio.file.Path namespaceDir = configTree.namespaceDir();
        File configFile = namespaceDir.resolve(FILE_NAME).toFile();

        if (!configFile.exists()) {
            try {
                java.nio.file.Files.createDirectories(namespaceDir);
                try (java.io.PrintWriter pw = new java.io.PrintWriter(configFile, StandardCharsets.UTF_8)) {
                    pw.print(EMPTY_DEFAULT);
                }
                LOGGER.info("[RadioDialog] Дефолтний конфіг ще не скопійовано ShaurmaConfigTree "
                        + "(sharedFile не заявлено жодним режимом?) — створено порожній: {}", configFile.getPath());
            } catch (Exception e) {
                LOGGER.error("[RadioDialog] Не вдалось створити порожній дефолтний конфіг", e);
                return;
            }
        }

        try (InputStream in = new FileInputStream(configFile)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) new Yaml()
                    .load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            RadioDialogRegistry.load(root);
            RadioAudioDucking.setDuckRatio(RadioDialogRegistry.getDuckRatio());
            LOGGER.info("[RadioDialog] Завантажено {} реплік з {}",
                    RadioDialogRegistry.getAll().size(), configFile.getPath());
        } catch (Exception e) {
            LOGGER.error("[RadioDialog] Помилка завантаження конфігу {}", configFile.getPath(), e);
        }
    }
}
