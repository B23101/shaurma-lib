package dev.shaurmalib.meta.mc;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.shaurmalib.meta.client.MetaClient;
import dev.shaurmalib.meta.fx.PlusBurst;
import dev.shaurmalib.meta.mc.hud.CompassHud;
import dev.shaurmalib.meta.mc.render.CosmeticRenderer;
import dev.shaurmalib.meta.mc.render.HealPlusRenderer;
import dev.shaurmalib.meta.mc.screen.MetaScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Клієнтський клей мета-двигуна для 1.20.1: лоадер викликає ці методи зі своїх подій
 * (HUD, рендер світу після сутностей, тік, вихід із сервера).
 */
public final class McMetaClient {

    /** Колір плюсиків хілу за замовчуванням. */
    public static final int HEAL_GREEN = 0x4CFF6A;

    private McMetaClient() {}

    public static void init() {
        MetaClient.setUi(MetaScreens.INSTANCE);
    }

    public static void onHud(GuiGraphics g, float partial) {
        CompassHud.render(g, partial);
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof MetaScreen)) MetaScreens.renderToasts(g, g.guiWidth(), g.guiHeight());
    }

    public static void onWorld(PoseStack pose, MultiBufferSource buffers, Vec3 cam, float partial) {
        CosmeticRenderer.renderWorld(pose, buffers, cam, partial);
        HealPlusRenderer.render(pose, buffers, cam);
    }

    public static void onTick() { CosmeticRenderer.clientTick(); }

    public static void onDisconnect() {
        MetaClient.reset();
        CosmeticRenderer.reset();
    }

    /** Клієнтський сплеск плюсиків над сутністю (без пакета — для локальних ефектів). */
    public static void healBurst(Entity e, int count) {
        PlusBurst.spawn(e.getX(), e.getY() + e.getBbHeight() * .55, e.getZ(), count, HEAL_GREEN, e.getBbWidth() * .9);
    }
}
