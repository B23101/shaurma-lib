package dev.shaurmalib.module;

import dev.shaurmalib.platform.Dist;
import dev.shaurmalib.platform.event.EventBus;

/**
 * Що отримує модуль при підключенні. Замінює {@code FMLModuleContext} (там були Forge {@code IEventBus} і
 * {@code MinecraftServer}): тепер ні лоадера, ні гри — лише шина ядра й сторона.
 */
public interface ModuleContext {
    EventBus bus();
    Dist dist();

    /** Лише клієнтські модулі (HUD, клієнтський стан) мають виконуватись на {@link Dist#CLIENT}. */
    default boolean isClient() { return dist() == Dist.CLIENT; }
}
