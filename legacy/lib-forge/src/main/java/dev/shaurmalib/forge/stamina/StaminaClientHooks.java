package dev.shaurmalib.forge.stamina;

import dev.shaurmalib.forge.network.packets.StaminaSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "shaurma_lib", value = Dist.CLIENT)
public final class StaminaClientHooks {
    @FunctionalInterface
    public interface Renderer {
        void render(GuiGraphics graphics, Minecraft minecraft, float stamina,
                    float maxStamina, float partialTick, int width, int height);
    }

    private static volatile Renderer renderer = StaminaClientHooks::renderDefault;

    private StaminaClientHooks() {}

    public static void setRenderer(Renderer customRenderer) {
        renderer = customRenderer == null ? StaminaClientHooks::renderDefault : customRenderer;
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())
                || !StaminaSyncPacket.ClientState.isActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        renderer.render(event.getGuiGraphics(), minecraft,
                StaminaSyncPacket.ClientState.getStamina(),
                StaminaSyncPacket.ClientState.getMaxStamina(),
                event.getPartialTick(), event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        StaminaSyncPacket.ClientState.clear();
    }

    /**
     * Чи треба заблокувати спринт цьому гравцю НА КЛІЄНТІ: лише для
     * власного гравця (стан stamina приходить тільки йому) і лише коли
     * сервер сказав, що спринт заборонений. Викликається з
     * {@code MixinLivingEntityStaminaSprint}.
     */
    public static boolean shouldBlockSprint(Player player) {
        return player == Minecraft.getInstance().player
                && StaminaSyncPacket.ClientState.shouldBlockSprint();
    }

    /**
     * Міксин не дає ВВІМКНУТИ спринт, але вже ввімкнений (подвійне W без
     * утримання клавіші — {@code setSprinting(true)} тоді більше не
     * викликається) сам не згасне. Тому на початку тіку власного гравця,
     * поки спринт заборонений, гасимо його явно. Клієнт сам повідомить
     * сервер (STOP_SPRINTING) у {@code sendPosition}.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !event.side.isClient()) {
            return;
        }
        Player player = event.player;
        if (player == Minecraft.getInstance().player
                && player.isSprinting()
                && StaminaSyncPacket.ClientState.shouldBlockSprint()) {
            player.setSprinting(false);
        }
    }

    private static void renderDefault(GuiGraphics graphics, Minecraft minecraft,
                                      float stamina, float maxStamina, float partialTick,
                                      int width, int height) {
        if (maxStamina <= 0) return;
        int barWidth = 120;
        int barHeight = 5;
        int x = (width - barWidth) / 2;
        int y = height - 42;
        int filled = Math.round(barWidth * Math.max(0, Math.min(1, stamina / maxStamina)));
        graphics.fill(x, y, x + barWidth, y + barHeight, 0xAA202020);
        graphics.fill(x, y, x + filled, y + barHeight, 0xFF35C9A0);
        graphics.drawCenteredString(minecraft.font,
                Math.round(stamina) + " / " + Math.round(maxStamina),
                width / 2, y - 10, 0xFFFFFFFF);
    }
}
