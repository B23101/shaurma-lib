package dev.shaurmalib.forge.module;

import dev.shaurmalib.forge.overlay.DarkZoneEffect;

/**
 * Маркер підключення "справжня темрява зони" — той самий статус, що
 * {@link dev.shaurmalib.forge.overlay.WorldTintOverlay}: статична
 * утиліта без {@code withX()}-гейту в {@link dev.shaurmalib.forge.ShaurmaLib.Builder},
 * бо немає ні event-listener'а, ні мережевого каналу, ні конфігу, який
 * треба було б умовно вмикати — mixin-и {@code MixinLightTextureDarkZone}
 * і {@code MixinDynamicTextureDarkZone} вже прописані статично в
 * {@code shaurma_lib.mixins.json} (клієнтський список) і бездіяльні,
 * поки жоден консюмер не викликав {@link DarkZoneEffect#setActive}.
 * <p>
 * Консюмер (напр. {@code DarkZoneModule} у shaurma-maniac) просто
 * викликає {@link DarkZoneEffect#setActive(String, boolean,
 * dev.shaurmalib.common.overlay.DarkZoneSpec)} щотіку зі свого
 * клієнтського пакет-хендлера — жодної реєстрації тут не потрібно.
 */
public interface DarkZoneModuleHook {
}
