package dev.shaurmalib.forge.overlay;

import dev.shaurmalib.common.overlay.WorldTintSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Узагальнений "зафарбувати екран кольором" рушій (план, п. 3.4 + 3.12)
 * — заміна {@code ScreenFadeOverlay} + {@code DeathFadeOverlay} +
 * {@code SniperStealthVignetteOverlay}. Один статичний
 * {@code WorldTintOverlay.start(WorldTintSpec)} замість п'яти окремих
 * фабричних методів ({@code startFade}, {@code startWhiteFade},
 * {@code startColorFade}, {@code startColorFadeWithKey},
 * {@code startFullOpacityFadeWithKey}) — самі варіанти (чорний/білий/
 * кольоровий+текст/full-opacity+текст) тепер конструктори
 * {@link WorldTintSpec}, а не окремі методи в рендер-класі.
 * <p>
 * Найновіше активне {@code start(...)} перекриває попереднє (як у
 * оригіналі — статичний стан, один активний тінт одночасно); якщо
 * потрібно кілька незалежних одночасних тінтів (рідкісний випадок),
 * консюмер реєструє окремий {@code tintId} через {@link #attach(String)}.
 */
@OnlyIn(Dist.CLIENT)
public final class WorldTintOverlay {

    private static final java.util.Map<String, Active> activeByTintId = new java.util.HashMap<>();

    private static final class Active {
        final WorldTintSpec spec;
        final long startedAt;

        Active(WorldTintSpec spec, long startedAt) {
            this.spec = spec;
            this.startedAt = startedAt;
        }
    }

    private WorldTintOverlay() {}

    /** Запускає тінт на дефолтному каналі {@code "default"}. */
    public static void start(WorldTintSpec spec) {
        start("default", spec);
    }

    public static void start(String tintId, WorldTintSpec spec) {
        activeByTintId.put(tintId, new Active(spec, System.currentTimeMillis()));
    }

    public static boolean isActive(String tintId) {
        return activeByTintId.containsKey(tintId);
    }

    /** Реєструє рендер каналу {@code tintId} в {@link OverlayEngine} — викликати один раз на кожен потрібний канал. */
    public static void attach(String tintId) {
        OverlayEngine.register("shaurma_world_tint_" + tintId,
                (gui, graphics, partialTick, sw, sh) -> render(tintId, graphics, sw, sh));
    }

    private static void render(String tintId, GuiGraphics g, int sw, int sh) {
        Active active = activeByTintId.get(tintId);
        if (active == null) return;

        long elapsed = System.currentTimeMillis() - active.startedAt;
        WorldTintSpec spec = active.spec;
        if (elapsed >= spec.totalMs()) {
            activeByTintId.remove(tintId);
            return;
        }

        float alpha = spec.backgroundAlphaAt(elapsed);
        int alphaInt = (int) (alpha * 255);
        int fillColor = (alphaInt << 24) | (spec.red() << 16) | (spec.green() << 8) | spec.blue();

        g.pose().pushPose();
        g.pose().translate(0, 0, 2000);
        g.fill(0, 0, sw, sh, fillColor);
        g.pose().popPose();

        if (spec.textKey() != null && !spec.textKey().isEmpty()) {
            float txtProgress = spec.textProgressAt(elapsed);
            if (txtProgress <= 0f) return;

            Minecraft mc = Minecraft.getInstance();
            MutableComponent message = Component.translatable(spec.textKey());
            String txt = message.getString();
            int txtAlpha = (int) (txtProgress * 255);
            int textColor = (txtAlpha << 24) | (spec.textRgb() & 0xFFFFFF);

            float scale = 1.5f;
            float txtW = mc.font.width(txt) * scale;
            float x = (sw - txtW) / 2f;
            float y = sh / 2f - (mc.font.lineHeight * scale) / 2f;

            g.pose().pushPose();
            g.pose().translate(0, 0, 2000);
            g.pose().scale(scale, scale, 1f);
            g.drawString(mc.font, txt, (int) (x / scale) + 1, (int) (y / scale) + 1,
                    (txtAlpha << 24), false);
            g.drawString(mc.font, txt, (int) (x / scale), (int) (y / scale), textColor, false);
            g.pose().popPose();
        }
    }
}
