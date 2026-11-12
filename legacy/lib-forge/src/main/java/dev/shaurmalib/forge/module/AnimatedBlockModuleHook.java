package dev.shaurmalib.forge.module;

import dev.shaurmalib.forge.blocks.AnimatedBlockEntityBase;

/**
 * Маркер підключення "анімований GeckoLib-блок" (план, п. 3.27) до
 * {@link dev.shaurmalib.forge.ShaurmaLib.Builder}. Сам двигун —
 * {@link AnimatedBlockEntityBase} — не має власного реєстру екземплярів
 * (кожен консюмер реєструє свій {@code BlockEntityType} через
 * {@code DeferredRegister}, як і решта Forge-блоків), тому цей хук лише
 * документує намір і дозволяє {@code ShaurmaLib.Builder} кинути
 * зрозумілий виняток, якщо консюмер звертається до модуля без
 * {@code withAnimatedBlocks()} — той самий принцип, що
 * {@link InvisibleZoneModuleHook}.
 */
public interface AnimatedBlockModuleHook {
    void onAttach(FMLModuleContext ctx);
}
