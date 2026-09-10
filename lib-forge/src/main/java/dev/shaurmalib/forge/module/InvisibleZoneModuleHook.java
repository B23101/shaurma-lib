package dev.shaurmalib.forge.module;

import dev.shaurmalib.forge.entity.invisiblezone.InvisibleZoneEntityBase;

/**
 * Маркер підключення "сутність-зона без хітбоксу" (план, п. 3.28) до
 * {@link dev.shaurmalib.forge.ShaurmaLib.Builder}. Сам двигун —
 * {@link InvisibleZoneEntityBase} — не має власного реєстру екземплярів
 * (кожен консюмер реєструє свій {@code EntityType} через
 * {@code DeferredRegister}, як і решта Forge-сутностей, це лишається на
 * боці консюмера, як і всі конкретні предмети/блоки в плані), тому цей
 * хук лише документує намір і дозволяє {@code ShaurmaLib.Builder} кинути
 * зрозумілий виняток, якщо консюмер звертається до модуля без
 * {@code withInvisibleZones()}.
 */
public interface InvisibleZoneModuleHook {
    void onAttach(FMLModuleContext ctx);
}
