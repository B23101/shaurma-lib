package dev.shaurmalib.meta.mc.skin;

import dev.shaurmalib.meta.MetaTheme;
import dev.shaurmalib.meta.mc.ui.MetaIcons;
import dev.shaurmalib.meta.mc.ui.MetaUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Скін за замовчуванням: темне скло з акцентними світіннями, закруглені панелі, прожектори, промені
 * (візуальна мова колишнього гардероба мафії). Палітра — {@link MetaTheme} режиму.
 */
public class GlassSkin implements MetaSkin {

    protected final MetaTheme t;

    public GlassSkin(MetaTheme theme) { this.t = theme; }

    @Override public MetaTheme theme() { return t; }

    @Override
    public void backdrop(GuiGraphics g, int w, int h, float time, float open) {
        float o = Math.max(.35f, open);
        g.fillGradient(0, 0, w, h, MetaUi.a(MetaUi.bright(t.bg(), .7f), .92f * o), MetaUi.a(t.bg(), .97f * o));
        int col = Math.round(w * .6f);
        MetaUi.gradH(g, w / 2 - col / 2, 0, col / 2, h, MetaUi.a(t.accent(), 0), MetaUi.a(t.accent(), .09f));
        MetaUi.gradH(g, w / 2, 0, col / 2, h, MetaUi.a(t.accent(), .09f), MetaUi.a(t.accent(), 0));
        int sc = MetaUi.a(0, .07f);
        for (int y = 0; y < h; y += 4) g.fill(0, y, w, y + 1, sc);
        MetaUi.vignette(g, w, h, .55f);
        MetaUi.embers(g, 0, 0, w, h, 40, t.accent(), .45f, System.currentTimeMillis());
    }

    @Override
    public void panel(GuiGraphics g, int x, int y, int w, int h, boolean main, float time) {
        MetaUi.panel(g, t, x, y, w, h, t.accent(), main ? .22f : .1f);
    }

    @Override
    public void header(GuiGraphics g, Font f, int x, int y, String overline, String title, float time) {
        MetaUi.title(g, f, title, x, y, 2.2f, t.accent(), t.text());
        MetaUi.shimmer(g, x, y, (int) (f.width(title) * 2.2f), 20, System.currentTimeMillis(), 3200, 0xFFFFFF, .5f);
        if (overline != null) MetaUi.spaced(g, f, overline, x + 1, y + 23, 2, MetaUi.solid(t.dim()));
    }

    @Override
    public int sectionTitle(GuiGraphics g, Font f, int x, int y, int w, String title, String right, float time) {
        MetaUi.textS(g, f, title, x, y, 1.4f, MetaUi.solid(t.text()), true);
        return 18;
    }

    @Override public int tabWidth(Font f, String label) { return f.width(label) + 30; }

    @Override
    public void tab(GuiGraphics g, Font f, int x, int y, int w, int h, String label, String icon, boolean active, float hover) {
        float k = Math.max(hover * .6f, active ? 1f : 0f);
        if (k > .02f) MetaUi.rrect(g, x, y, w, h, 4, MetaUi.a(t.accent(), .18f * k));
        if (active) g.fill(x + 4, y + h - 1, x + w - 4, y + h, MetaUi.solid(t.accent()));
        int col = MetaUi.mix(MetaUi.solid(t.dim()), 0xFFFFFFFF, k);
        if (icon != null) MetaIcons.draw(g, icon, x + 6, y + (h - 8) / 2, 1, col);
        MetaUi.text(g, f, label, x + (icon != null ? 18 : 8), y + (h - 8) / 2, col, false);
    }

    @Override
    public void button(GuiGraphics g, Font f, int x, int y, int w, int h, String label, Kind kind, float hv, boolean enabled, float time) {
        int r = 4;
        if (kind == Kind.PRIMARY) {
            int a = enabled ? t.accent() : t.dim();
            MetaUi.glow(g, x, y, w, h, r, a, .3f + .55f * hv);
            MetaUi.rgrad(g, x, y, w, h, r, MetaUi.mix(MetaUi.a(a, .96f), MetaUi.a(MetaUi.bright(a, 1.3f), .98f), hv), MetaUi.a(MetaUi.bright(a, .72f), .96f));
            MetaUi.rborder(g, x, y, w, h, r, MetaUi.a(0xFFFFFF, .35f + .4f * hv));
            g.fill(x + 4, y + 1, x + w - 4, y + 2, MetaUi.a(0xFFFFFF, .35f));
            MetaUi.shimmer(g, x + 2, y + 2, w - 4, h - 4, System.currentTimeMillis(), 1700, 0xFFFFFF, .5f);
            String s = MetaUi.fit(f, label, w - 12);
            MetaUi.text(g, f, s, x + (w - f.width(s)) / 2, y + (h - 8) / 2, 0xFF0A0A10, false);
            return;
        }
        int acc = kind == Kind.DANGER ? t.bad() : t.accent();
        float k = Math.max(hv, kind == Kind.SELECTED ? 1f : 0f);
        if (hv > .02f) MetaUi.glow(g, x, y, w, h, r, acc, hv * .7f);
        MetaUi.rgrad(g, x, y, w, h, r, MetaUi.mix(MetaUi.a(t.panelHi(), .92f), MetaUi.a(MetaUi.bright(acc, .30f), .95f), k * .75f),
                MetaUi.mix(MetaUi.a(t.bg(), .95f), MetaUi.a(MetaUi.bright(acc, .14f), .95f), k * .75f));
        MetaUi.rborder(g, x, y, w, h, r, MetaUi.mix(MetaUi.solid(t.border()), MetaUi.solid(acc), k));
        g.fill(x + 4, y + 1, x + w - 4, y + 2, MetaUi.a(0xFFFFFF, .08f + .08f * k));
        MetaUi.rrect(g, x, y + 3, 2 + Math.round(k * 2f), h - 6, 1, MetaUi.a(acc, .55f + .45f * k));
        String s = MetaUi.fit(f, label, w - 14);
        int col = !enabled ? MetaUi.solid(t.dim()) : MetaUi.mix(0xFFC8C8D8, 0xFFFFFFFF, k);
        MetaUi.text(g, f, s, x + (w - f.width(s)) / 2 + 1, y + (h - 8) / 2, col, true);
        if (hv > .5f) MetaUi.shimmer(g, x + 2, y + 2, w - 4, h - 4, System.currentTimeMillis(), 900, 0xFFFFFF, hv * .3f);
    }

    @Override
    public void card(GuiGraphics g, int x, int y, int w, int h, int color, boolean sel, float hv, float time) {
        float k = Math.max(hv * .7f, sel ? 1f : 0f);
        MetaUi.shadow(g, x, y, w, h, 4, .8f);
        if (k > .02f) MetaUi.glow(g, x, y, w, h, 4, color, k * .55f);
        MetaUi.rgrad(g, x, y, w, h, 4, MetaUi.mix(MetaUi.a(t.panelHi(), .95f), MetaUi.a(MetaUi.bright(color, .30f), .95f), k * .6f),
                MetaUi.mix(MetaUi.a(t.bg(), .97f), MetaUi.a(MetaUi.bright(color, .14f), .95f), k * .6f));
    }

    @Override
    public void cardOverlay(GuiGraphics g, int x, int y, int w, int h, int color, boolean sel, float hv, float time) {
        float k = Math.max(hv * .7f, sel ? 1f : 0f);
        MetaUi.rborder(g, x, y, w, h, 4, MetaUi.mix(MetaUi.solid(t.border()), MetaUi.solid(color), k));
        if (sel) MetaUi.brackets(g, x + 3, y + 3, x + w - 3, y + h - 3, 6, 1, MetaUi.a(color, .65f + .35f * (float) Math.sin(time * 4)));
        if (hv > .5f) MetaUi.shimmer(g, x + 2, y + 2, w - 4, h - 4, System.currentTimeMillis(), 1100, 0xFFFFFF, hv * .25f);
    }

    @Override
    public void stage(GuiGraphics g, int x, int y, int w, int h, int color, float time, float hv) {
        MetaUi.rgrad(g, x, y, w, h, 2, MetaUi.a(MetaUi.bright(color, .55f), .95f), MetaUi.a(MetaUi.bright(color, .22f), .97f));
        int cx = x + w / 2, cy = y + h * 3 / 5, len = Math.max(w, h);
        int col = MetaUi.a(MetaUi.bright(color, 1.25f), .14f + .08f * hv);
        for (int i = 0; i < 14; i++) {
            double an = time * .12 + i * Math.PI * 2 / 14;
            rayClip(g, cx, cy, cx + (int) (Math.cos(an) * len), cy + (int) (Math.sin(an) * len), x, y, w, h, col);
        }
        MetaUi.glowDisc(g, cx, cy, Math.min(w, h) / 3, MetaUi.bright(color, 1.3f), .25f);
    }

    static void rayClip(GuiGraphics g, int x0, int y0, int x1, int y1, int bx, int by, int bw, int bh, int col) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / 2;
        for (int i = 0; i <= n; i++) {
            float tt = i / (float) Math.max(1, n);
            int x = Math.round(x0 + (x1 - x0) * tt), y = Math.round(y0 + (y1 - y0) * tt);
            if (x < bx || y < by || x >= bx + bw - 2 || y >= by + bh - 2) break;
            g.fill(x, y, x + 1 + i * 6 / Math.max(1, n), y + 2, col);
        }
    }

    @Override
    public void viewport(GuiGraphics g, int x, int y, int w, int h, int fx, int fy, int color, float time, float open) {
        float sm = MetaUi.clamp01(open);
        MetaUi.glowDisc(g, x + w / 2, y + h / 2, 170, color, (.20f + .05f * (float) Math.sin(time * 1.6)) * sm);
        MetaUi.cone(g, x + w / 2, y - 14, fy, 120, (int) (Math.sin(time * .7) * 34), color, .13f * sm);
        MetaUi.hexGrid(g, x + w / 2, y + h / 2 - 20, 150, 20, MetaUi.clamp01((time - .3f) / 1.2f), color, .14f * sm);
        MetaUi.brackets(g, x, y, x + w, y + h, 14, 1, MetaUi.a(color, .75f * sm));
        MetaUi.glowEllipse(g, fx, fy + 4, 118, 26, color, .22f * sm);
        MetaUi.ring(g, fx, fy + 4, 108, 22, MetaUi.a(color, .62f * sm), 44, time * .35f);
        MetaUi.ring(g, fx, fy + 4, 78, 15, MetaUi.a(color, .42f * sm), 30, -time * .5f);
    }

    @Override
    public void bar(GuiGraphics g, int x, int y, int w, int h, float fill, int color, float time) {
        MetaUi.rrect(g, x, y, w, h, Math.min(3, h / 2), MetaUi.a(0, .5f));
        int fw = Math.round(w * MetaUi.clamp01(fill));
        if (fw > 0) {
            MetaUi.rgrad(g, x, y, fw, h, Math.min(3, h / 2), MetaUi.solid(MetaUi.bright(color, 1.25f)), MetaUi.solid(color));
            MetaUi.shimmer(g, x, y, fw, h, System.currentTimeMillis(), 1600, 0xFFFFFF, .35f);
        }
    }

    @Override
    public int chip(GuiGraphics g, Font f, int x, int y, String text, String icon, int color) {
        int w = f.width(text) + (icon != null ? 22 : 12);
        MetaUi.rrect(g, x, y, w, 14, 6, MetaUi.a(0, .5f));
        MetaUi.rborder(g, x, y, w, 14, 6, MetaUi.a(color, .55f));
        if (icon != null) MetaIcons.draw(g, icon, x + 5, y + 3, 1, MetaUi.solid(color));
        MetaUi.text(g, f, text, x + (icon != null ? 16 : 6), y + 3, 0xFFFFFFFF, false);
        return w;
    }

    @Override
    public void tag(GuiGraphics g, Font f, int x, int y, String text, int color, boolean solid) {
        int w = (int) (f.width(text) * .75f) + 8;
        MetaUi.rrect(g, x, y, w, 11, 2, solid ? MetaUi.solid(color) : MetaUi.a(color, .25f));
        MetaUi.textS(g, f, text, x + 4, y + 2, .75f, solid ? 0xFF101010 : MetaUi.solid(color), false);
    }

    @Override
    public void divider(GuiGraphics g, int x, int y, int w) {
        g.fillGradient(x, y, x + w / 2, y + 1, MetaUi.a(t.accent(), 0), MetaUi.a(t.accent(), .55f));
        g.fill(x, y, x + w, y + 1, MetaUi.a(t.accent(), .35f));
    }

    @Override
    public void levelBadge(GuiGraphics g, Font f, int cx, int cy, int r, int level, int color, float flash, float time) {
        MetaUi.glowDisc(g, cx, cy, r * 2, color, .25f + .35f * flash);
        MetaUi.hexagon(g, cx, cy, r + 4, MetaUi.a(MetaUi.bright(color, .45f), .95f), MetaUi.solid(color));
        MetaUi.hexagon(g, cx, cy, r, MetaUi.a(t.panelHi(), .98f), MetaUi.solid(MetaUi.bright(color, 1.15f)));
        float s = r >= 30 ? 2.6f : 1.5f;
        MetaUi.textSC(g, f, String.valueOf(level), cx, cy - 4 * s, s, 0xFFFFFFFF, true);
    }

    @Override
    public void focus(GuiGraphics g, int x, int y, int w, int h, int color, float time) {
        MetaUi.brackets(g, x, y, x + w, y + h, 7, 1, MetaUi.solid(color));
    }

    @Override
    public void toast(GuiGraphics g, int x, int y, int w, int h, float a, float time) {
        MetaUi.shadow(g, x, y, w, h, 5, a);
        MetaUi.glow(g, x, y, w, h, 5, t.accent(), (.4f + .3f * (float) Math.sin(time * 8)) * a);
        MetaUi.rgrad(g, x, y, w, h, 5, MetaUi.a(t.panelHi(), .95f * a), MetaUi.a(t.bg(), .96f * a));
        MetaUi.rborder(g, x, y, w, h, 5, MetaUi.a(t.accent(), .9f * a));
    }
}
