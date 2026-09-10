package dev.shaurmalib.common.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Абстракція над деревом конфігів {@code worldRoot/config/<namespace>/}.
 * <p>
 * Пряме узагальнення {@code GameModeRegistry.copyDefaultConfigs(...)} з
 * snipers_shaurma (core/registry/GameModeRegistry.java): той метод мав
 * захардкоджені масиви файлів на 4 конкретні режими (SC/SR/SCN/SD) і
 * перевірки рядкового id ({@code "sniper_duels".equals(resourceFolder)}).
 * Тут це замінено на {@link ModeConfigDescriptor}, який кожен режим формує
 * сам — {@link #copyModeDefaults(ModeConfigDescriptor)} не знає нічого про
 * конкретні режими snipers чи maniac.
 * <p>
 * Поведінка копіювання — та сама, що в оригіналі: копіюємо ресурс лише
 * якщо цільового файлу ще немає на диску (користувацькі налаштування
 * НІКОЛИ не перезаписуються); якщо ресурсу немає в jar — мовчки
 * пропускаємо (файл не потрібен цьому режиму/споживачу).
 * <p>
 * {@code resourceLoader} — типово {@code MyModMainClass.class::getResourceAsStream},
 * бо самі .yml/.json ресурси лежать у jar-і споживача (snipers_shaurma або
 * maniac-mode), а не в lib-forge — бібліотека лише виконує механіку
 * копіювання/дерева тек, дані завжди постачає споживач.
 * <p>
 * Для фактичного ЧИТАННЯ й кешування YAML-файлів з диска (після того, як
 * {@link #copyModeDefaults} скопіював дефолти) — дивись
 * {@link CachedYamlLoader}, окремий клас з тим самим (mtime+size+хеш)
 * кешем, що мав оригінальний {@code ConfigLoader.readYamlCached}.
 */
public final class ShaurmaConfigTree {

    private static final Logger LOGGER = Logger.getLogger(ShaurmaConfigTree.class.getName());

    private final Path namespaceDir;
    private final String namespace;
    private final Function<String, InputStream> resourceLoader;

    /**
     * @param worldRoot      корінь конфігів світу (типово {@code worldRoot.resolve("config")}
     *                       вже перед викликом — сюди передається саме тека,
     *                       у якій буде створено {@code <namespace>/}).
     * @param namespace      простір імен споживача, наприклад "snipers_shaurma"
     *                       або "maniac_mode" — назва підпапки й одночасно
     *                       префікс ресурсного шляху {@code /config/<namespace>/...}.
     * @param resourceLoader постачальник {@link InputStream} для ресурсного
     *                       шляху виду {@code /config/<namespace>/<modeFolder>/<file>}.
     */
    public ShaurmaConfigTree(Path worldRoot, String namespace, Function<String, InputStream> resourceLoader) {
        this.namespaceDir = worldRoot.resolve(namespace);
        this.namespace = namespace;
        this.resourceLoader = resourceLoader;
    }

    /** Корінь дерева конфігів цього namespace (worldRoot/config/<namespace>). */
    public Path namespaceDir() {
        return namespaceDir;
    }

    /** Тека конкретного режиму: namespaceDir/<modeFolder>. */
    public Path modeDir(String modeFolder) {
        return namespaceDir.resolve(modeFolder);
    }

    /**
     * Копіює дефолтні конфіги режиму з ресурсів jar-а на диск — заміна
     * {@code GameModeRegistry.copyDefaultConfigs(...)}. Викликається один
     * раз на кожен зареєстрований режим при старті сервера (як в оригіналі
     * "копіюємо дефолти ВСІХ режимів при старті, щоб адмін бачив усі файли
     * одразу"), і повторно при {@code setActive(...)} для нового активного
     * режиму (щойно доданий режим міг ще не мати теки, якщо мод оновився).
     */
    public void copyModeDefaults(ModeConfigDescriptor descriptor) {
        Path modeDir = modeDir(descriptor.modeFolder());
        if (!ensureDir(modeDir)) return;

        for (String relativePath : descriptor.modeFiles()) {
            copyIfAbsent(modeDir, relativePath,
                    "/config/" + namespace + "/" + descriptor.modeFolder() + "/" + relativePath);
        }

        // Спільні файли — у батьківську (namespace-рівня) папку, не в modeDir.
        for (String relativePath : descriptor.sharedFiles()) {
            copyIfAbsent(namespaceDir, relativePath,
                    "/config/" + namespace + "/" + relativePath);
        }
    }

    private void copyIfAbsent(Path baseDir, String relativePath, String resourcePath) {
        Path target = baseDir.resolve(relativePath);
        if (Files.exists(target)) return; // ніколи не перезаписуємо існуюче

        Path parent = target.getParent();
        if (parent != null && !ensureDir(parent)) return;

        try (InputStream in = resourceLoader.apply(resourcePath)) {
            if (in == null) {
                // Ресурс не знайдено — файл не потрібен цьому конкретному
                // споживачу/режиму, мовчки пропускаємо (як в оригіналі).
                return;
            }
            Files.copy(in, target);
            LOGGER.log(Level.INFO, "[ShaurmaConfigTree] Скопійовано дефолтний конфіг: {0}", target);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "[ShaurmaConfigTree] Ресурс {0} не скопійовано: {1}",
                    new Object[]{resourcePath, e.getMessage()});
        }
    }

    private boolean ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[ShaurmaConfigTree] Не вдалось створити директорію {0}: {1}",
                    new Object[]{dir, e.getMessage()});
            return false;
        }
    }

    // ── Персистенція простих single-value YAML файлів (напр. mode.yml) ──

    /**
     * Читає одне значення {@code key} з YAML-файлу {@code fileName} у корені
     * namespace (не в modeDir). Повертає {@code null}, якщо файлу немає,
     * ключа немає або файл пошкоджений — викликач вирішує дефолт сам
     * (в оригіналі — {@code GameModeRegistry.initPersistence} читав
     * {@code mode.yml} саме таким чином).
     */
    public String loadSingleValue(String fileName, String key) {
        Path file = namespaceDir.resolve(fileName);
        if (!Files.exists(file)) return null;
        try (InputStream in = Files.newInputStream(file)) {
            Object root = new Yaml().load(in);
            if (root instanceof Map<?, ?> map) {
                Object value = map.get(key);
                return value != null ? String.valueOf(value) : null;
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "[ShaurmaConfigTree] Не вдалось прочитати {0}: {1}",
                    new Object[]{fileName, e.getMessage()});
        }
        return null;
    }

    /**
     * Зберігає одне значення {@code key} у YAML-файлі {@code fileName} у
     * корені namespace, перезаписуючи файл (файл належить бібліотеці/моду,
     * а не користувацьким налаштуванням — на відміну від дефолт-копіювання
     * {@link #copyModeDefaults}, тут перезапис очікуваний).
     */
    public void saveSingleValue(String fileName, String key, String value) {
        try {
            if (!ensureDir(namespaceDir)) return;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put(key, value);
            Files.writeString(namespaceDir.resolve(fileName), new Yaml().dump(data));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[ShaurmaConfigTree] Не вдалось зберегти {0}: {1}",
                    new Object[]{fileName, e.getMessage()});
        }
    }
}
