package dev.shaurmalib.forge.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "shaurma_lib")
public final class InventorySlotAllocationHooks {

    private InventorySlotAllocationHooks() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            InventorySlotAllocation.syncExisting(player);
        }
    }
}
