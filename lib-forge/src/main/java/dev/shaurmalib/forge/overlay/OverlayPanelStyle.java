package dev.shaurmalib.forge.overlay;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Рендер-примітиви для панелей стилю snipers_shaurma — перенесено 1:1 з
 * {@code OverlayStyle.drawPanel}/{@code applyAlpha} (план, п. 3.3,
 * {@code StyleTheme}/{@code MenuStyleTheme}). Кольори фону/бордера тут —
 * дефолти бібліотеки; консюмер, якому потрібна інша палітра, передає
 * власні ARGB через параметризовані перевантаження, а не форкає клас.
 */
@OnlyIn(Dist.CLIENT)
public final class OverlayPanelStyle {

    private OverlayPanelStyle() {}

    public static final int BG_BASE = 0x14161A;
    public static final int BG_MAX_A = 235;
    public static final int BORDER = 0x66FFFFFF;
    public static final int ACCENT_NEUTRAL = 0x00000000;
    public static final int ACCENT_RED = 0xFFE05252;
    public static final int ACCENT_YELLOW = 0xFFFFD700;
    public static final int TEXT_DEFAULT = 0xFFE8EDF2;

    /** Застосовує {@code targetAlpha} (0..1) до кольору, зберігаючи його власний alpha-канал як базу. */
    public static int applyAlpha(int color, float targetAlpha) {
        int existingA = (color >> 24) & 0xFF;
        int newA = Mth.clamp((int) (existingA * targetAlpha), 0, 255);
        return (color & 0x00FFFFFF) | (newA << 24);
    }

    /**
     * Малює основну панель: фон + 1px border + опційний акцентний
     * зовнішній контур (accentColor alpha-канал == 0 → акцент вимкнено).
     */
    /** Застосовує явну int alpha (0..255) до кольору, ігноруючи власний alpha-канал. */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
    }

    /** 1px рамка по контуру прямокутника — 1:1 {@code OverlayStyle.border1px}. */
    public static void border1px(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h, float alpha, int accentColor) {
        int a = (int) (alpha * 255f);
        int bgA = Math.min(a, BG_MAX_A);
        int bg = (bgA << 24) | (BG_BASE & 0xFFFFFF);
        int border = applyAlpha(BORDER, alpha);

        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y - 1, x + w, y, border);
        g.fill(x, y + h, x + w, y + h + 1, border);
        g.fill(x - 1, y, x, y + h, border);
        g.fill(x + w, y, x + w + 1, y + h, border);

        if ((accentColor >>> 24) > 0) {
            int accent = applyAlpha(accentColor, alpha);
            g.fill(x - 1, y - 1, x + 1, y + h + 1, accent);
        }
    }

    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        drawPanel(g, x, y, w, h, alpha, ACCENT_NEUTRAL);
    }
}
