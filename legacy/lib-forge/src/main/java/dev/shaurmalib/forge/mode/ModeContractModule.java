package dev.shaurmalib.forge.mode;

import dev.shaurmalib.common.config.ConfigReloadBus;
import dev.shaurmalib.common.config.ShaurmaConfigTree;

/**
 * Робочий модуль контракту режимів (п. 3.18 плану) — обгортає
 * {@link GameModeRegistry}, з'єднану з тим самим {@link ShaurmaConfigTree}
 * і {@link ConfigReloadBus}, що й {@code ConfigModule} (п. 3.1) споживача —
 * реєстрація режимів і копіювання дефолтних конфігів завжди йдуть через
 * одне й те саме дерево конфігів namespace.
 */
public final class ModeContractModule {

    private final GameModeRegistry registry;

    public ModeContractModule(ShaurmaConfigTree configTree, ConfigReloadBus reloadBus, String modeStateFileName) {
        this.registry = new GameModeRegistry(configTree, reloadBus, modeStateFileName);
    }

    public GameModeRegistry registry() {
        return registry;
    }
}
