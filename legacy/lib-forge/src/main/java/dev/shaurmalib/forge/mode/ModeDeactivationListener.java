package dev.shaurmalib.forge.mode;

import net.minecraft.server.MinecraftServer;

/**
 * Хук, що викликається {@link GameModeRegistry} ПІСЛЯ
 * {@code previousMode.onModeDeactivated(server)}, але ДО активації
 * наступного режиму — заміна snipers-специфічних викликів, які раніше
 * були захардкоджені прямо всередині оригінального
 * {@code GameModeRegistry.setActive(...)}:
 * <pre>
 *   AirdropManager.forceStopAndClear(server);
 *   MoneyZoneManager.forceStopAndClear(server);
 * </pre>
 * Бібліотека нічого не знає про ці snipers-класи — споживач реєструє
 * власні {@link ModeDeactivationListener} через
 * {@code GameModeRegistry.addDeactivationListener(...)} при ініціалізації
 * мода, і саме там snipers_shaurma підписує свій
 * {@code (server) -> { AirdropManager.forceStopAndClear(server); MoneyZoneManager.forceStopAndClear(server); }}.
 */
@FunctionalInterface
public interface ModeDeactivationListener {
    void onModeDeactivated(MinecraftServer server);
}
