package dev.shaurmalib.forge.inventory;

import dev.shaurmalib.forge.network.packets.InventorySlotAllocationPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = "shaurma_lib", value = Dist.CLIENT)
public final class InventorySlotAllocationClientHooks {

    @FunctionalInterface
    public interface HotbarRenderer {
        void render(GuiGraphics graphics, Minecraft minecraft, List<Integer> allowedSlots,
                    float partialTick, int screenWidth, int screenHeight);
    }

    private static volatile Supplier<? extends Screen> customInventoryScreen;
    private static volatile HotbarRenderer customHotbarRenderer;

    private InventorySlotAllocationClientHooks() {}

    public static void setCustomInventoryScreen(Supplier<? extends Screen> screenFactory) {
        customInventoryScreen = screenFactory;
    }

    public static void setCustomHotbarRenderer(HotbarRenderer renderer) {
        customHotbarRenderer = renderer;
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof InventoryScreen
                && InventorySlotAllocationPacket.ClientHandler.isInventoryScreenBlocked()) {
            Supplier<? extends Screen> factory = customInventoryScreen;
            if (factory == null) {
                event.setCanceled(true);
            } else {
                Screen replacement = factory.get();
                if (replacement == null) {
                    throw new IllegalStateException("Кастомний inventory screen factory повернув null.");
                }
                event.setNewScreen(replacement);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || !InventorySlotAllocationPacket.ClientHandler.isRestricted()) {
            return;
        }

        int selected = minecraft.player.getInventory().selected;
        if (InventorySlotAllocationPacket.ClientHandler.isAllowed(selected)) {
            return;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (InventorySlotAllocationPacket.ClientHandler.isAllowed(slot)) {
                minecraft.player.getInventory().selected = slot;
                return;
            }
        }
        minecraft.player.getInventory().selected = 0;
    }

    @SubscribeEvent
    public static void onHotbarPre(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())
                || !InventorySlotAllocationPacket.ClientHandler.isRestricted()) {
            return;
        }

        event.setCanceled(true);
        List<Integer> allowedSlots = allowedHotbarSlots();
        HotbarRenderer renderer = customHotbarRenderer;
        if (renderer != null) {
            renderer.render(event.getGuiGraphics(), Minecraft.getInstance(), allowedSlots,
                    event.getPartialTick(), event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight());
        } else {
            renderAllocatedHotbar(event.getGuiGraphics(), allowedSlots);
        }
    }

    private static List<Integer> allowedHotbarSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            if (InventorySlotAllocationPacket.ClientHandler.isAllowed(slot)) {
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
    }

    private static void renderAllocatedHotbar(GuiGraphics graphics, List<Integer> slots) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isSpectator()) return;

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
