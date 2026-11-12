package dev.shaurmalib.loader.forge;

import dev.shaurmalib.platform.Platform;
import dev.shaurmalib.platform.event.PlatformEvents;
import dev.shaurmalib.platform.service.RefFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Увесь Forge-клей stamina = ці 3 методи; жодної логіки (було: StaminaHooks + рішення всередині).
// Обгортки гравців дає RefFactory (реалізує mc:*), тож лоадер не залежить від mc:*.
// УВАГА: типи ПОДІЙ (TickEvent.PlayerTickEvent) у Forge 1.20.1 і NeoForge 1.21.1 різні → цей клас живе в loader/forge/<mc-версія>/.
@Mod.EventBusSubscriber(modid = "shaurma_lib")
public final class ForgeEventBridge {
    private ForgeEventBridge() {}
    private static RefFactory refs() { return Platform.service(RefFactory.class); }

    @SubscribeEvent public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p)
            PlatformEvents.BUS.post(new PlatformEvents.ServerPlayerTick(refs().player(p)));
    }
    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PlatformEvents.BUS.post(new PlatformEvents.PlayerJoin(refs().player(p)));
    }
    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PlatformEvents.BUS.post(new PlatformEvents.PlayerLeave(refs().player(p)));
    }
}
