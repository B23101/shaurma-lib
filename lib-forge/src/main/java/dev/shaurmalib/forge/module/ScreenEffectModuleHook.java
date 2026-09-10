package dev.shaurmalib.forge.module;

/**
 * Маркер підключення "повноекранні post-chain ефекти" (план, п. 3.23) до
 * {@link dev.shaurmalib.forge.ShaurmaLib.Builder}. Реальний рушій —
 * {@link dev.shaurmalib.forge.fx.ScreenEffectPostChain} — статичний
 * реєстр без стану ініціалізації (як
 * {@link dev.shaurmalib.forge.teleport.TeleportService}), тому цей хук
 * лише документує намір; підключення відбувається через
 * {@code ShaurmaLib.Builder.withScreenEffects()}.
 */
public interface ScreenEffectModuleHook {
    void onAttach(FMLModuleContext ctx);
}
