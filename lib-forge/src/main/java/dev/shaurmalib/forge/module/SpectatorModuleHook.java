package dev.shaurmalib.forge.module;

/**
 * Маркер підключення "спостереження за живим гравцем" (план, п. 3.34) до
 * {@link dev.shaurmalib.forge.ShaurmaLib.Builder}. Реальний рушій —
 * {@link dev.shaurmalib.forge.spectator.SpectatorSessionController} —
 * статичний сервіс без стану ініціалізації (як
 * {@link dev.shaurmalib.forge.teleport.TeleportService}), тому цей хук
 * лише документує намір; підключення відбувається через
 * {@code ShaurmaLib.Builder.withSpectator()}.
 */
public interface SpectatorModuleHook {
    void onAttach(FMLModuleContext ctx);
}
