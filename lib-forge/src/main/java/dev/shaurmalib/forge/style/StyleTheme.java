package dev.shaurmalib.forge.style;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.shaurmalib.common.style.StylePalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Робочий рушій стилів (план, п. 3.3) — перенесення {@code GuiGraphics}-
 * рендер-методів {@code OverlayStyle} snipers_shaurma (панелі,
 * cut-corner слоти, tooltip-и, кольорові іконки) в переносний,
 * параметризований {@link StylePalette} клас. На відміну від оригіналу
 * (статичний клас з захардкодженою палітрою), тут instance-based —
 * консюмер створює {@code new StyleTheme(palette)} один раз (типово
 * зберігає в полі свого {@code @Mod}-класу або static-полі клієнтського
 * стану) і використовує той самий екземпляр для ВСІХ своїх Screen/Overlay,
 * так само, як в оригіналі всі 40+ HUD-класів викликали статичні методи
 * {@code OverlayStyle}.
 * <p>
 * Геометрія й формули малювання (cut-corner слоти, ромб-прогрес,
 * tooltip word-wrap) перенесені буквально — вони вже правильні й
 * візуально перевірені, план не вимагає їх переізобрітати, лише
 * прибрати хардкод конкретних кольорів консюмера з самого рушія.
 */
public final class StyleTheme {

    private final StylePalette palette;

    public StyleTheme(StylePalette palette) {
        this.palette = palette;
    }

    public StylePalette palette() {
        return palette;
    }

    // ─── Панелі ─────────────────────────────────────────────────────────

    /**
     * Основна панель: фон + 1px border + опційний акцентний лівий border.
     * @param alpha       0..1
     * @param accentColor лівий акцент (0x00000000 = вимкнено)
     */
    public void drawPanel(GuiGraphics g, int x, int y, int w, int h, float alpha, int accentColor) {
        int a = (int) (alpha * 255f);
        int bgA = Math.min(a, palette.bgMaxAlpha);
        int bg = (bgA << 24) | palette.bgBase;
        int border = StylePalette.applyAlpha(palette.border, alpha);

        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y - 1, x + w, y, border);
        g.fill(x, y + h, x + w, y + h + 1, border);
        g.fill(x - 1, y, x, y + h, border);
        g.fill(x + w, y, x + w + 1, y + h, border);

        if ((accentColor >>> 24) > 0) {
            int accent = StylePalette.applyAlpha(accentColor, alpha);
            g.fill(x - 1, y - 1, x + 1, y + h + 1, accent);
        }
    }

    /** Без акценту. */
    public void drawPanel(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        drawPanel(g, x, y, w, h, alpha, palette.accentNeutral);
    }

    /** Панель зі зрізаними 1px кутами — використовується для слотів/карток. */
    public void drawCutPanel(GuiGraphics g, int x, int y, int w, int h,
                              float alpha, int fillColor, int borderColor) {
        int fa = StylePalette.applyAlpha(fillColor, alpha);
        int ba = StylePalette.applyAlpha(borderColor, alpha);

        g.fill(x + 1, y, x + w - 1, y + h, fa);
        g.fill(x, y + 1, x + 1, y + h - 1, fa);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, fa);

        g.fill(x + 1, y, x + w - 1, y + 1, ba);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, ba);
        g.fill(x, y + 1, x + 1, y + h - 1, ba);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, ba);
    }

    /** Слот з вирізаними кутами + обводка (розмір {@code size} × {@code size}). */
    public void drawSlot(GuiGraphics g, int x, int y, int size, float alpha, int fillColor, int borderColor) {
        drawCutPanel(g, x, y, size, size, alpha, fillColor, borderColor);
    }

    /** Обводка слота без фону. */
    public void drawSlotBorder(GuiGraphics g, int x, int y, int size, float alpha, int borderColor) {
        int b = StylePalette.applyAlpha(borderColor, alpha);
        g.fill(x + 1, y, x + size - 1, y + 1, b);
        g.fill(x + 1, y + size - 1, x + size - 1, y + size, b);
        g.fill(x, y + 1, x + 1, y + size - 1, b);
        g.fill(x + size - 1, y + 1, x + size, y + size - 1, b);
    }

    /** Напівпрозоре затемнення всього екрану (для модальних Screen). */
    public void drawScreenDim(GuiGraphics g, int screenW, int screenH, float alpha) {
        g.fill(0, 0, screenW, screenH, (int) Mth.clamp(alpha * 160f, 0, 180) << 24);
    }

    // ─── Tooltip ────────────────────────────────────────────────────────

    /**
     * Tooltip поблизу курсора з заголовком і додатковими рядками —
     * автоматичний word-wrap і clamp у межах екрану. Перенесено 1:1 з
     * {@code OverlayStyle.drawTooltip}.
     */
    public void drawTooltip(GuiGraphics g, Font font, String header, String[] lines,
                             int mx, int my, int maxX, int maxY) {
        if (header == null || header.isEmpty()) return;

        int px = 6, py = 4;
        int lH = font.lineHeight + 2;
        int maxTipW = Math.min(220, maxX - 8);

        int tw = Math.min(font.width(header), maxTipW) + px * 2;
        if (lines != null) {
            for (String l : lines) tw = Math.max(tw, Math.min(font.width(l), maxTipW) + px * 2);
        }
        tw = Math.min(tw, maxTipW);

        int lineCount = countWrapped(font, header, maxTipW);
        if (lines != null) {
            for (String l : lines) lineCount += countWrapped(font, l, maxTipW);
        }
        int th = lineCount * lH + py * 2;

        int tx = mx - 4;
        int ty = my - th + 2;

        if (tx + tw > maxX - 2) tx = maxX - tw - 2;
        if (tx < 2) tx = 2;
        if (ty < 2) ty = my + 2;
        if (ty + th > maxY - 2) ty = maxY - th - 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        g.fill(tx + 2, ty + 2, tx + tw + 2, ty + th + 2, 0x66000000);
        g.fill(tx, ty, tx + tw, ty + th, 0xFF05060A);
        g.fill(tx + 1, ty, tx + tw - 1, ty + 1, palette.accentYellow);
        g.fill(tx, ty, tx + tw, ty + 1, 0xFF252530);
        g.fill(tx, ty + th - 1, tx + tw, ty + th, 0xFF252530);
        g.fill(tx, ty, tx + 1, ty + th, 0xFF252530);
        g.fill(tx + tw - 1, ty, tx + tw, ty + th, 0xFF252530);

        int dy = ty + py;
        dy = drawWrapped(g, font, header, tx + px, dy, maxTipW - px * 2, palette.textMain, lH);
        if (lines != null) {
            for (String l : lines) dy = drawWrapped(g, font, l, tx + px, dy, maxTipW - px * 2, 0xFF8899AA, lH);
        }

        g.pose().popPose();
    }

    /** Tooltip з одним рядком. */
    public void drawTooltip(GuiGraphics g, Font font, String text, int mx, int my, int maxX, int maxY) {
        drawTooltip(g, font, text, null, mx, my, maxX, maxY);
    }

    private static int countWrapped(Font font, String text, int maxW) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        String rem = text;
        while (!rem.isEmpty()) {
            if (font.width(rem) <= maxW) { count++; break; }
            String line = font.plainSubstrByWidth(rem, maxW);
            int sp = line.lastIndexOf(' ');
            if (sp > 0) line = line.substring(0, sp);
            rem = rem.substring(line.length()).trim();
            count++;
        }
        return count;
    }

    private static int drawWrapped(GuiGraphics g, Font font, String text, int x, int y, int maxW, int color, int lineH) {
        if (text == null || text.isEmpty()) return y;
        String rem = text;
        while (!rem.isEmpty()) {
            if (font.width(rem) <= maxW) {
                g.drawString(font, rem, x, y, color, false);
                y += lineH;
                break;
            }
            String line = font.plainSubstrByWidth(rem, maxW);
            int sp = line.lastIndexOf(' ');
            if (sp > 0) line = line.substring(0, sp);
            g.drawString(font, line, x, y, color, false);
            rem = rem.substring(line.length()).trim();
            y += lineH;
        }
        return y;
    }

    // ─── Прості примітиви ───────────────────────────────────────────────

    public void hline(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color);
    }

    public void vline(GuiGraphics g, int x, int y, int h, int color) {
        g.fill(x, y, x + 1, y + h, color);
    }

    public void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    public void border1px(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /**
     * 2D ромб через горизонтальні scanline-смужки (для прогрес-індикаторів
     * захоплення точки, наприклад SCN). {@code half} — половина діагоналі.
     */
    public static void drawDiamond2D(GuiGraphics g, int cx, int cy, int half,
                                      int fillColor, int borderColor, int borderW) {
        for (int dy = -half; dy <= half; dy++) {
            int maxX = half - Math.abs(dy);
            int x1 = cx - maxX;
            int x2 = cx + maxX + 1;
            int rowY = cy + dy;
            int innerMaxX = half - borderW - Math.abs(dy);
            if (innerMaxX < 0) {
                if (borderColor != 0) g.fill(x1, rowY, x2, rowY + 1, borderColor);
            } else {
                int ix1 = cx - innerMaxX;
                int ix2 = cx + innerMaxX + 1;
                if (borderColor != 0 && x1 < ix1) g.fill(x1, rowY, ix1, rowY + 1, borderColor);
                if (fillColor != 0 && ix1 < ix2) g.fill(ix1, rowY, ix2, rowY + 1, fillColor);
                if (borderColor != 0 && ix2 < x2) g.fill(ix2, rowY, x2, rowY + 1, borderColor);
            }
        }
    }

    /** {@link #drawDiamond2D} з прогрес-заповненням знизу вгору ({@code prog} 0..1). */
    public static void drawDiamond2DProgress(GuiGraphics g, int cx, int cy, int half,
                                              int fillColor, int progressColor, float prog,
                                              int borderColor, int borderW) {
        int cutY = cy + half - Math.round(prog * 2 * half);
        for (int dy = -half; dy <= half; dy++) {
            int maxX = half - Math.abs(dy);
            int x1 = cx - maxX;
            int x2 = cx + maxX + 1;
            int rowY = cy + dy;
            int innerMaxX = half - borderW - Math.abs(dy);
            if (innerMaxX < 0) {
                if (borderColor != 0) g.fill(x1, rowY, x2, rowY + 1, borderColor);
            } else {
                int ix1 = cx - innerMaxX;
                int ix2 = cx + innerMaxX + 1;
                if (borderColor != 0 && x1 < ix1) g.fill(x1, rowY, ix1, rowY + 1, borderColor);
                if (ix1 < ix2) {
                    int fc = (rowY >= cutY) ? progressColor : fillColor;
                    if (fc != 0) g.fill(ix1, rowY, ix2, rowY + 1, fc);
                }
                if (borderColor != 0 && ix2 < x2) g.fill(ix2, rowY, x2, rowY + 1, borderColor);
            }
        }
    }

    /**
     * Малює квадратну білу/сіру іконку, тоновану у заданий ARGB-колір
     * (множенням через {@code RenderSystem.setShaderColor}) — узагальнення
     * {@code blitTinted(...)} з оригіналу.
     */
    public static void blitTinted(GuiGraphics g, ResourceLocation texture,
                                   int x, int y, int w, int h, int argbColor, int alpha) {
        float a = ((argbColor >>> 24) & 0xFF) / 255f * (alpha / 255f);
        float r = ((argbColor >> 16) & 0xFF) / 255f;
        float gg = ((argbColor >> 8) & 0xFF) / 255f;
        float b = (argbColor & 0xFF) / 255f;
        RenderSystem.setShaderColor(r, gg, b, a);
        g.blit(texture, x, y, w, h, 0, 0, 16, 16, 16, 16);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
