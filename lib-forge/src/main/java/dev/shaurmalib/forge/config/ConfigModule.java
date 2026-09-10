package dev.shaurmalib.forge.config;

import dev.shaurmalib.common.config.ConfigReloadBus;
import dev.shaurmalib.common.config.ShaurmaConfigTree;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Forge-обгортка, що зв'язує {@link ShaurmaConfigTree} (lib-common, чиста
 * Java) з {@link ConfigReloadBus} для конкретного споживача. Створюється
 * через {@code ShaurmaLib.Builder.withConfig(...)}.
 */
public final class ConfigModule {

    private final ShaurmaConfigTree configTree;
    private final ConfigReloadBus reloadBus = new ConfigReloadBus();

    public ConfigModule(Path worldRoot, String namespace, Function<String, InputStream> resourceLoader) {
        this.configTree = new ShaurmaConfigTree(worldRoot, namespace, resourceLoader);
    }

    public ShaurmaConfigTree configTree() {
        return configTree;
    }

    public ConfigReloadBus reloadBus() {
        return reloadBus;
    }
}
