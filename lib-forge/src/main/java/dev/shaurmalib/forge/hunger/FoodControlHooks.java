package dev.shaurmalib.forge.hunger;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code Phase.END} — спрацьовує ПІСЛЯ ванільного {@code player.tick()}
 * (де й рахується {@code FoodData.tick()} з exhaustion/regen), тож будь-яке
 * ванільне зменшення голоду на цьому ж тіку миттєво перезаписується назад
 * {@link FoodControlService#tick}, якщо для гравця активний не-VANILLA режим.
 */
@Mod.EventBusSubscriber(modid = "shaurma_lib")
public final class FoodControlHooks {
    private FoodControlHooks() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            FoodControlService.tick(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FoodControlService.clear(player);
        }
    }
}
