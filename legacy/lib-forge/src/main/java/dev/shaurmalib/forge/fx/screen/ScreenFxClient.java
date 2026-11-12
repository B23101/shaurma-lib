package dev.shaurmalib.forge.fx.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Підключення двигуна екранних ефектів до клієнта Forge (бібліотека робить це сама). */
public final class ScreenFxClient {
    private ScreenFxClient() {}

    @Mod.EventBusSubscriber(modid = "shaurma_lib", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent e) {
            // Над світом і HUD, але під чатом/екранами — як в оригіналі EnhancedVisuals.
            e.registerAboveAll("screen_fx", (gui, g, partial, sw, sh) -> render(g, partial, sw, sh));
        }

        @SubscribeEvent
        public static void onReload(RegisterClientReloadListenersEvent e) {
            e.registerReloadListener((ResourceManagerReloadListener) rm -> ScreenFxBlur.invalidate());
        }
    }

    @Mod.EventBusSubscriber(modid = "shaurma_lib", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase == TickEvent.Phase.END) ScreenFx.tick();
        }

        @SubscribeEvent
        public static void onGuiPre(RenderGuiEvent.Pre e) {
            if (ScreenFx.isActive()) ScreenFxBlur.render(e.getPartialTick());
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            ScreenFx.clear();
        }
    }

    private static void render(GuiGraphics g, float partial, int sw, int sh) {
        if (!ScreenFx.isActive()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // серцебиття: повноекранна вінетка
        float hb = ScreenFxHeartbeat.overlayAlpha(partial);
        if (hb > 0.003f) fullscreen(g, 1f, 1f, 1f, hb, sw, sh);
        // частинки
        int n = ScreenFx.PARTICLES.size();
        for (ScreenFx.Particle p : ScreenFx.PARTICLES) {
            float a = p.opacity(partial);
            if (a <= 0.003f) continue;
            g.pose().pushPose();
            g.pose().translate(p.x + p.size / 2.0, p.y + p.size / 2.0, 0);
            if (p.rotation != 0f) g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(p.rotation));
            g.pose().translate(-p.size / 2.0, -p.size / 2.0, 0);
            RenderSystem.setShaderColor(((p.tint >> 16) & 0xFF) / 255f, ((p.tint >> 8) & 0xFF) / 255f, (p.tint & 0xFF) / 255f, a);
            g.blit(p.texture, 0, 0, 0, 0, p.size, p.size, p.size, p.size);
            g.pose().popPose();
        }
        // багато крові — темніють кути (квадратична крива)
        if (n >= 20) {
            float t = Math.min(1f, (n - 20) / 180f);
            if (t * t * 0.6f > 0.003f) fullscreen(g, 0f, 0f, 0f, t * t * 0.6f, sw, sh);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        // спалахи — суцільна заливка кольором
        for (ScreenFx.Pulse f : ScreenFx.FLASHES) {
            float a = f.value(partial);
            if (a > 0.003f) g.fill(0, 0, sw, sh, ((int) (a * 255) << 24) | (f.rgb & 0xFFFFFF));
        }
        RenderSystem.disableBlend();
    }

    private static void fullscreen(GuiGraphics g, float r, float gr, float b, float a, int sw, int sh) {
        RenderSystem.setShaderColor(r, gr, b, a);
        g.blit(ScreenFxTextures.LOWHEALTH, 0, 0, 0, 0, sw, sh, sw, sh);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
