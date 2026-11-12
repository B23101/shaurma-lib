package dev.shaurmalib.meta.mc.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.shaurmalib.meta.MetaTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Малювальний кит екранів мета-двигуна (порт візуальної мови гардероба мафії на GuiGraphics):
 * закруглені панелі-«скло», світіння, блиск, віньєтка, іскри, кутові скоби, конуси прожекторів,
 * шестикутна сітка. Усі кольори беруться з {@link MetaTheme} режиму — тож один код дає різні стилі.
 */
public final class MetaUi {

    private MetaUi() {}

    // ══ математика / анімація ══════════════════════════════════════════
    public static float clamp01(float t) { return t < 0f ? 0f : (t > 1f ? 1f : t); }
    public static float outCubic(float t) { t = clamp01(t); float u = 1f - t; return 1f - u * u * u; }
    public static float outBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
    public static float outElastic(float t) {
        t = clamp01(t);
        if (t == 0 || t == 1) return t;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - .75) * (2 * Math.PI / 3)) + 1);
    }
    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    public static float approach(float v, float target, float speed, float dt) {
        return v + (target - v) * Math.min(1f, dt * speed);
    }

    // ══ кольори ═════════════════════════════════════════════════════════
    public static int a(int rgb, float alpha) {
        return ((int) (clamp01(alpha) * 255f + .5f) << 24) | (rgb & 0xFFFFFF);
    }
    public static int solid(int rgb) { return 0xFF000000 | rgb; }
    public static int mix(int c1, int c2, float t) {
        t = clamp01(t);
        int a1 = c1 >>> 24, a2 = c2 >>> 24;
        int r = (int) lerp((c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF, t);
        int g = (int) lerp((c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF, t);
        int b = (int) lerp(c1 & 0xFF, c2 & 0xFF, t);
        return ((int) lerp(a1, a2, t) << 24) | (r << 16) | (g << 8) | b;
    }
    public static int bright(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((rgb & 0xFF) * f));
        return (rgb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // ══ закруглені прямокутники ════════════════════════════════════════
    private static final int[][] INSETS = new int[24][];

    private static int[] insets(int r) {
        if (r < 0) r = 0;
        boolean cache = r < INSETS.length;
        if (cache && INSETS[r] != null) return INSETS[r];
        int[] arr = new int[r];
        for (int i = 0; i < r; i++) {
            double dy = r - i - .5;
            double dx = Math.sqrt(Math.max(0, (double) r * r - dy * dy));
            arr[i] = Math.max(0, r - (int) Math.round(dx));
        }
        if (cache) INSETS[r] = arr;
        return arr;
    }

    private static int clampR(int r, int w, int h) { return Math.max(0, Math.min(r, Math.min(w, h) / 2)); }

    public static void rrect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        r = clampR(r, w, h);
        if (r <= 0) { g.fill(x, y, x + w, y + h, color); return; }
        int[] ins = insets(r);
        for (int i = 0; i < r; i++) {
            int o = ins[i];
            g.fill(x + o, y + i, x + w - o, y + i + 1, color);
            g.fill(x + o, y + h - 1 - i, x + w - o, y + h - i, color);
        }
        if (h - 2 * r > 0) g.fill(x, y + r, x + w, y + h - r, color);
    }

    public static void rborder(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 2 || h <= 2 || (color >>> 24) == 0) return;
        r = clampR(r, w, h);
        if (r <= 1) {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y + 1, x + 1, y + h - 1, color);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
            return;
        }
        int[] outer = insets(r), inner = insets(r - 1);
        for (int i = 0; i < r; i++) {
            int o = outer[i];
            if (i == 0) {
                g.fill(x + o, y, x + w - o, y + 1, color);
                g.fill(x + o, y + h - 1, x + w - o, y + h, color);
            } else {
                int in = 1 + inner[i - 1];
                g.fill(x + o, y + i, x + in, y + i + 1, color);
                g.fill(x + w - in, y + i, x + w - o, y + i + 1, color);
                g.fill(x + o, y + h - 1 - i, x + in, y + h - i, color);
                g.fill(x + w - in, y + h - 1 - i, x + w - o, y + h - i, color);
            }
        }
        if (h - 2 * r > 0) {
            g.fill(x, y + r, x + 1, y + h - r, color);
            g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        }
    }

    public static void rgrad(GuiGraphics g, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        r = clampR(r, w, h);
        int[] ins = r > 0 ? insets(r) : new int[0];
        for (int i = 0; i < h; i++) {
            int c = mix(top, bottom, h <= 1 ? 0f : i / (float) (h - 1));
            int o = i < r ? ins[i] : (i >= h - r ? ins[h - 1 - i] : 0);
            g.fill(x + o, y + i, x + w - o, y + i + 1, c);
        }
    }

    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int r, float s) {
        if (s <= .01f) return;
        for (int k = 5; k >= 1; k--) rrect(g, x - k, y - k + 2, w + 2 * k, h + 2 * k, r + k, a(0, s * .085f * (6 - k) / 5f));
    }

    public static void glow(GuiGraphics g, int x, int y, int w, int h, int r, int rgb, float s) {
        if (s <= .01f) return;
        for (int k = 6; k >= 1; k--) rrect(g, x - k, y - k, w + 2 * k, h + 2 * k, r + k, a(rgb, s * .07f * (7 - k) / 6f));
    }

    /** Панель у стилі теми: скло (мафія) або дерев'яна дошка з рамкою (маніяк). */
    public static void panel(GuiGraphics g, MetaTheme t, int x, int y, int w, int h, int accent, float glowAmt) {
        int r = t.sharp() ? 2 : 5;
        shadow(g, x, y, w, h, r, 1f);
        if (glowAmt > .01f) glow(g, x, y, w, h, r, accent, glowAmt);
        rgrad(g, x, y, w, h, r, a(t.panelHi(), .90f), a(t.bg(), .94f));
        rborder(g, x, y, w, h, r, mix(a(t.border(), .95f), a(accent, .9f), .3f + .4f * glowAmt));
        if (t.sharp()) rborder(g, x + 2, y + 2, w - 4, h - 4, 1, a(t.border(), .35f));
        int hr = Math.max(0, r - 1);
        if (w - 2 * hr > 2) g.fill(x + hr, y + 1, x + w - hr, y + 2, a(0xFFFFFF, .09f));
    }

    // ══ ефекти ══════════════════════════════════════════════════════════
    public static void gradH(GuiGraphics g, int x, int y, int w, int h, int left, int right) {
        if (w <= 0 || h <= 0) return;
        int step = w > 160 ? 4 : (w > 60 ? 2 : 1);
        for (int i = 0; i < w; i += step) g.fill(x + i, y, x + Math.min(w, i + step), y + h, mix(left, right, w <= 1 ? 0 : i / (float) (w - 1)));
    }

    public static void shimmer(GuiGraphics g, int x, int y, int w, int h, long now, long period, int rgb, float s) {
        if (w <= 4 || h <= 0 || s <= .01f) return;
        float t = (now % period) / (float) period;
        int band = Math.max(10, w / 4);
        int cx = x - band + (int) ((w + band * 2) * t);
        for (int i = -band; i <= band; i += 2) {
            int px = cx + i;
            if (px < x || px >= x + w) continue;
            float f = 1f - Math.abs(i) / (float) band;
            g.fill(px, y, Math.min(x + w, px + 2), y + h, a(rgb, s * f * f * .5f));
        }
    }

    public static void vignette(GuiGraphics g, int sw, int sh, float s) {
        if (s <= .01f) return;
        int ey = (int) (sh * .28f), ex = (int) (sw * .22f);
        g.fillGradient(0, 0, sw, ey, a(0, s), a(0, 0));
        g.fillGradient(0, sh - ey, sw, sh, a(0, 0), a(0, s));
        for (int i = 0; i < ex; i += 2) {
            float f = 1f - i / (float) ex;
            int col = a(0, s * f * f * .7f);
            g.fill(i, ey, i + 2, sh - ey, col);
            g.fill(sw - i - 2, ey, sw - i, sh - ey, col);
        }
    }

    public static float hash(int n) {
        n = (n << 13) ^ n;
        int v = (n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff;
        return v / 2147483648f;
    }

    public static void embers(GuiGraphics g, int x, int y, int w, int h, int count, int rgb, float alpha, long now) {
        if (alpha <= .01f || w <= 0 || h <= 0) return;
        for (int i = 0; i < count; i++) {
            float seed = hash(i * 7 + 3);
            float speed = .00005f + hash(i * 13 + 5) * .00008f;
            float phase = ((now * speed) + seed) % 1f;
            float sway = (float) Math.sin(phase * 9f + seed * 6.28f) * 8f;
            int px = x + (int) (hash(i * 3 + 1) * w + sway), py = y + (int) (h * (1f - phase));
            float al = (float) Math.sin(phase * Math.PI) * alpha;
            int size = hash(i * 11 + 9) > .8f ? 2 : 1;
            if (al > .02f) {
                g.fill(px, py, px + size, py + size, a(rgb, al * .9f));
                if (size == 2) g.fill(px - 1, py - 1, px + 3, py + 3, a(rgb, al * .18f));
            }
        }
    }

    public static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        if (r < 1 || (color >>> 24) == 0) return;
        int step = r > 40 ? 2 : 1;
        for (int dy = -r; dy <= r; dy += step) {
            int dx = (int) Math.sqrt(Math.max(0, (double) r * r - (double) dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + step, color);
        }
    }

    public static void ellipse(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        if (rx < 1 || ry < 1 || (color >>> 24) == 0) return;
        for (int dy = -ry; dy <= ry; dy++) {
            int dx = (int) (rx * Math.sqrt(Math.max(0, 1.0 - (dy * (double) dy) / (ry * (double) ry))));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    public static void glowDisc(GuiGraphics g, int cx, int cy, int r, int rgb, float alpha) {
        if (alpha <= .01f || r < 3) return;
        for (int i = 0; i < 6; i++) disc(g, cx, cy, Math.max(2, (int) (r * (1f - i / 6f))), a(rgb, alpha * .2f));
    }

    public static void glowEllipse(GuiGraphics g, int cx, int cy, int rx, int ry, int rgb, float alpha) {
        if (alpha <= .01f || rx < 3 || ry < 2) return;
        for (int i = 0; i < 5; i++) {
            float k = 1f - i / 5f;
            ellipse(g, cx, cy, Math.max(2, (int) (rx * k)), Math.max(1, (int) (ry * k)), a(rgb, alpha * .22f));
        }
    }

    public static void cone(GuiGraphics g, int ax, int ay, int bottomY, int halfW, int dxBottom, int rgb, float alpha) {
        if (alpha <= .01f || bottomY <= ay) return;
        int h = bottomY - ay;
        for (int row = 0; row < h; row += 3) {
            float t = row / (float) h;
            int half = 1 + (int) (halfW * t), mid = ax + (int) (dxBottom * t);
            g.fill(mid - half, ay + row, mid + half, Math.min(bottomY, ay + row + 3), a(rgb, alpha * (1f - t * .7f)));
        }
    }

    public static void brackets(GuiGraphics g, int x1, int y1, int x2, int y2, int len, int th, int color) {
        g.fill(x1, y1, x1 + len, y1 + th, color);  g.fill(x1, y1, x1 + th, y1 + len, color);
        g.fill(x2 - len, y1, x2, y1 + th, color);  g.fill(x2 - th, y1, x2, y1 + len, color);
        g.fill(x1, y2 - th, x1 + len, y2, color);  g.fill(x1, y2 - len, x1 + th, y2, color);
        g.fill(x2 - len, y2 - th, x2, y2, color);  g.fill(x2 - th, y2 - len, x2, y2, color);
    }

    public static void ring(GuiGraphics g, int cx, int cy, int rx, int ry, int col, int dashes, float rot) {
        int n = dashes * 2;
        for (int i = 0; i < n; i += 2) {
            double a0 = rot + i * Math.PI * 2 / n, a1 = rot + (i + 1) * Math.PI * 2 / n;
            for (double an = a0; an < a1; an += .04) {
                int x = cx + (int) Math.round(Math.cos(an) * rx), y = cy + (int) Math.round(Math.sin(an) * ry);
                g.fill(x, y, x + 1, y + 1, col);
            }
        }
    }

    public static void arc(GuiGraphics g, int cx, int cy, int r, double from, double sweep, int thick, int color) {
        if (r < 2 || thick < 1 || (color >>> 24) == 0 || sweep <= 0) return;
        int steps = Math.max(12, (int) (r * sweep / Math.max(1.0, thick * .6)));
        int hh = thick / 2;
        for (int i = 0; i <= steps; i++) {
            double an = from + sweep * i / steps;
            int x = cx + (int) Math.round(Math.cos(an) * r), y = cy + (int) Math.round(Math.sin(an) * r);
            g.fill(x - hh, y - hh, x - hh + thick, y - hh + thick, color);
        }
    }

    /** Лінія з товщиною (Брезенхем по крапках). */
    public static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int th, int color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int n = Math.max(dx, dy);
        for (int i = 0; i <= n; i++) {
            float t = n == 0 ? 0 : i / (float) n;
            int x = Math.round(lerp(x0, x1, t)), y = Math.round(lerp(y0, y1, t));
            g.fill(x, y, x + th, y + th, color);
        }
    }

    public static void hexGrid(GuiGraphics g, int cx, int cy, int R, int size, float reveal, int rgb, float alpha) {
        if (alpha <= .01f || reveal <= .01f) return;
        double w = size * Math.sqrt(3), vstep = size * 1.5;
        int rows = (int) (R / vstep) + 2, cols = (int) (R / w) + 2;
        for (int j = -rows; j <= rows; j++) {
            for (int i = -cols; i <= cols; i++) {
                double hx = i * w + ((j & 1) == 0 ? 0 : w / 2), hy = j * vstep;
                double dist = Math.sqrt(hx * hx + hy * hy);
                if (dist > R) continue;
                float f = (float) (dist / R);
                float on = clamp01((reveal - f * .7f) / .3f);
                if (on <= .02f) continue;
                int col = a(rgb, alpha * on * (1f - f * .55f));
                int px = cx + (int) hx, py = cy + (int) hy, s = size, hw = (int) (w / 2);
                line(g, px - hw, py - s / 2, px, py - s, 1, col);
                line(g, px, py - s, px + hw, py - s / 2, 1, col);
                line(g, px + hw, py - s / 2, px + hw, py + s / 2, 1, col);
            }
        }
    }

    /** Шестикутник-емблема рівня (як на екрані підсумків). */
    public static void hexagon(GuiGraphics g, int cx, int cy, int r, int fill, int border) {
        // гостра вершина зверху: середня смуга |dy| ≤ r/2 повної ширини, далі — звуження до вершини
        for (int dy = -r; dy <= r; dy++) {
            int ady = Math.abs(dy);
            float half = ady <= r / 2f ? r * .866f : r * .866f * (r - ady) / (r / 2f);
            g.fill(cx - (int) half, cy + dy, cx + (int) half, cy + dy + 1, fill);
        }
        for (int k = 0; k < 6; k++) {
            double a0 = -Math.PI / 2 + k * Math.PI / 3, a1 = a0 + Math.PI / 3;
            line(g, cx + (int) Math.round(Math.cos(a0) * r * .99), cy + (int) Math.round(Math.sin(a0) * r * .99),
                    cx + (int) Math.round(Math.cos(a1) * r * .99), cy + (int) Math.round(Math.sin(a1) * r * .99), 2, border);
        }
    }

    /** Павутиння в куті (декор теми {@link MetaTheme.Decor#WEBS}). corner: 0 лв-верх, 1 пр-верх, 2 лв-низ, 3 пр-низ. */
    public static void web(GuiGraphics g, int x, int y, int size, int corner, int color) {
        int sx = (corner & 1) == 0 ? 1 : -1, sy = corner < 2 ? 1 : -1;
        int spokes = 5;
        int[][] pts = new int[spokes][];
        for (int i = 0; i < spokes; i++) {
            double an = Math.PI / 2 * i / (spokes - 1);
            int ex = x + sx * (int) (Math.cos(an) * size), ey = y + sy * (int) (Math.sin(an) * size);
            line(g, x, y, ex, ey, 1, color);
            pts[i] = new int[]{ex, ey};
        }
        for (int ring = 1; ring <= 3; ring++) {
            float f = ring / 3.6f;
            for (int i = 0; i + 1 < spokes; i++) {
                int ax = x + (int) ((pts[i][0] - x) * f), ay = y + (int) ((pts[i][1] - y) * f);
                int bx = x + (int) ((pts[i + 1][0] - x) * f), by = y + (int) ((pts[i + 1][1] - y) * f);
                int mx = (ax + bx) / 2 - sx * ring, my = (ay + by) / 2 - sy * ring;   // провисання
                line(g, ax, ay, mx, my, 1, color);
                line(g, mx, my, bx, by, 1, color);
            }
        }
    }

    // ══ текст ═══════════════════════════════════════════════════════════
    public static void text(GuiGraphics g, Font f, String s, int x, int y, int argb, boolean shadow) {
        g.drawString(f, s, x, y, argb, shadow);
    }

    public static void textC(GuiGraphics g, Font f, String s, int cx, int y, int argb, boolean shadow) {
        g.drawString(f, s, cx - f.width(s) / 2, y, argb, shadow);
    }

    public static void textR(GuiGraphics g, Font f, String s, int rx, int y, int argb, boolean shadow) {
        g.drawString(f, s, rx - f.width(s), y, argb, shadow);
    }

    public static void textS(GuiGraphics g, Font f, String s, float x, float y, float scale, int argb, boolean shadow) {
        PoseStack m = g.pose();
        m.pushPose();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1f);
        g.drawString(f, s, 0, 0, argb, shadow);
        m.popPose();
    }

    public static void textSC(GuiGraphics g, Font f, String s, float cx, float y, float scale, int argb, boolean shadow) {
        textS(g, f, s, cx - f.width(s) * scale / 2f, y, scale, argb, shadow);
    }

    /** Великий заголовок із ореолом акценту. */
    public static void title(GuiGraphics g, Font f, String s, float x, float y, float scale, int accent, int textColor) {
        int[][] d = {{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-1, -1}, {1, 1}};
        for (int[] o : d) textS(g, f, s, x + o[0], y + o[1], scale, a(accent, .16f), false);
        textS(g, f, s, x, y, scale, solid(textColor), true);
    }

    public static void spaced(GuiGraphics g, Font f, String s, int x, int y, int spacing, int color) {
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            g.drawString(f, ch, x, y, color, false);
            x += f.width(ch) + spacing;
        }
    }

    public static String fit(Font f, String s, int maxW) {
        if (s == null) return "";
        if (f.width(s) <= maxW) return s;
        String e = "…";
        int ew = f.width(e);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (f.width(b.toString() + s.charAt(i)) + ew > maxW) break;
            b.append(s.charAt(i));
        }
        return b + e;
    }

    public static List<String> wrap(Font f, String s, int maxW) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String cand = line.length() == 0 ? word : line + " " + word;
            if (f.width(cand) > maxW && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(word); }
            else { line = new StringBuilder(cand); }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    public static String num(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }

    // ══ дрібниці ════════════════════════════════════════════════════════
    public static void check(GuiGraphics g, int x, int y, int argb) {
        int[][] p = {{0, 2}, {1, 3}, {2, 4}, {3, 3}, {4, 2}, {5, 1}, {6, 0}};
        for (int[] q : p) g.fill(x + q[0], y + q[1], x + q[0] + 1, y + q[1] + 1, argb);
    }

    public static void cross(GuiGraphics g, int x, int y, int argb) {
        for (int i = 0; i < 5; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, argb);
            g.fill(x + 4 - i, y + i, x + 5 - i, y + i + 1, argb);
        }
    }

    public static void lock(GuiGraphics g, int x, int y, int argb) {
        g.fill(x + 1, y, x + 5, y + 1, argb);
        g.fill(x, y + 1, x + 1, y + 4, argb);
        g.fill(x + 5, y + 1, x + 6, y + 4, argb);
        g.fill(x - 1, y + 4, x + 7, y + 10, argb);
        g.fill(x + 2, y + 6, x + 4, y + 8, 0xFF000000);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
