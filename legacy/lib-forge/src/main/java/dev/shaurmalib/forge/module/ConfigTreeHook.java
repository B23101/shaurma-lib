package dev.shaurmalib.forge.module;

import dev.shaurmalib.common.config.ShaurmaConfigTree;

/**
 * Хук конфігураційного дерева.
 */
public interface ConfigTreeHook {
    void onAttach(FMLModuleContext ctx);
}
