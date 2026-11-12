package dev.shaurmalib.forge.module;

import dev.shaurmalib.forge.entity.skinnable.SkinnableEntityBase;

/**
 * Маркер підключення "сутність з рантайм-скіном гравця" (план, п. 3.22) до
 * {@link dev.shaurmalib.forge.ShaurmaLib.Builder}. Сам двигун —
 * {@link SkinnableEntityBase} — не має власного реєстру екземплярів
 * (кожен консюмер реєструє свій {@code EntityType} через
 * {@code DeferredRegister}, як і решта Forge-сутностей лібу), тому цей
 * хук лише документує намір і дозволяє {@code ShaurmaLib.Builder} кинути
 * зрозумілий виняток, якщо консюмер звертається до модуля без
 * {@code withSkinnableEntities()} — той самий принцип, що
 * {@link InvisibleZoneModuleHook} і {@link AnimatedBlockModuleHook}.
 */
public interface SkinnableEntityModuleHook {
    void onAttach(FMLModuleContext ctx);
}
