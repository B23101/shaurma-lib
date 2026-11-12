package dev.shaurmalib.forge.overlay;

import dev.shaurmalib.common.overlay.CountdownDigitEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Узагальнена система pop-in цифрового відліку по центру екрана (план,
 * п. 3.4) — базовий каркас для {@code GameCountdownOverlay}-подібного
 * fallback-рендеру. Один статичний {@code AnimatedCountdownSystem.show(digit, accentArgb)}
 * замість жорсткого {@code YELLOW}-кольору й прямого виклику
 * {@code drawString} у оригіналі — консюмер передає свій акцентний колір
 * (напр. {@code accentColor()} з {@code GameModeContract}, план 3.18+3.32),
 * замість форку класу заради іншого кольору.
 * <p>
 * Це навмисно НЕ переносить glitch/chromatic-aberration шар
 * {@code CinematicCountdownOverlay} — той лишається кастомним
 * {@code CineHooks} у моді (план, п. 3.35), бо є художнім стилем snipers,
 * не універсальним патерном. {@link #show} дає лише робочий pop-in/pop-out
 * скелет, який консюмер може або показати як є (як фолбек), або
 * "обгорнути" власними VFX поверх.
 */
@OnlyIn(Dist.CLIENT)
public final class AnimatedCountdownSystem {

    private static CountdownDigitEntry active;
    private static int accentArgb = 0xFFFFFF00;

    private AnimatedCountdownSystem() {}

    public static void show(int digit, int accentArgbColor) {
        accentArgb = accentArgbColor;
        active = CountdownDigitEntry.cinematicDefault(digit);
    }

    public static void clear() {
        active = null;
    }

    public static boolean isActive() {
        return active != null;
    }

    /** Реєструє себе в {@link OverlayEngine} під фіксованим id — викликати один раз при клієнтському сетапі. */
    public static void attach() {
        OverlayEngine.register("shaurma_animated_countdown", (gui, graphics, partialTick, sw, sh) -> render(graphics, sw, sh));
    }

    private static void render(GuiGraphics g, int sw, int sh) {
        if (active == null) return;
        long now = System.currentTimeMillis();
        if (active.isExpired(now)) {
            active = null;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        String text = String.valueOf(active.digit());
        float scale = active.scaleAt(now) * 4f;
        float alpha = active.alphaAt(now);
        int argb = (Math.round(alpha * 255) << 24) | (accentArgb & 0xFFFFFF);

        g.pose().pushPose();
        g.pose().translate(sw / 2.0, sh / 2.0 - 28, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(mc.font, text, -mc.font.width(text) / 2, 0, argb, true);
        g.pose().popPose();
    }
}
