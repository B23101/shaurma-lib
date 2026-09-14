package dev.shaurmalib.forge.inventory;

import dev.shaurmalib.forge.network.packets.InventorySlotAllocationPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "shaurma_lib", value = Dist.CLIENT)
public final class InventorySlotAllocationClientHooks {

    private InventorySlotAllocationClientHooks() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof InventoryScreen
                && InventorySlotAllocationPacket.ClientHandler.isInventoryScreenBlocked()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHotbarPre(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())
                || !InventorySlotAllocationPacket.ClientHandler.isRestricted()) {
            return;
        }

        event.setCanceled(true);
        renderAllocatedHotbar(event.getGuiGraphics());
    }

    private static void renderAllocatedHotbar(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isSpectator()) return;

        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            if (InventorySlotAllocationPacket.ClientHandler.isAllowed(slot)) {
                slots.add(slot);
            }
        }
        if (slots.isEmpty()) return;

        int slotSize = 20;
        int left = (minecraft.getWindow().getGuiScaledWidth() - slots.size() * slotSize) / 2;
        int top = minecraft.getWindow().getGuiScaledHeight() - 22;

        for (int index = 0; index < slots.size(); index++) {
            int inventorySlot = slots.get(index);
            int x = left + index * slotSize;
            boolean selected = minecraft.player.getInventory().selected == inventorySlot;
            int background = selected ? 0xFFB0B0B0 : 0xAA202020;

            graphics.fill(x, top, x + slotSize, top + slotSize, background);
            graphics.fill(x + 1, top + 1, x + slotSize - 1, top + slotSize - 1, 0xAA000000);

            ItemStack stack = minecraft.player.getInventory().getItem(inventorySlot);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 2, top + 2);
                graphics.renderItemDecorations(minecraft.font, stack, x + 2, top + 2);
            }
        }
    }
}
