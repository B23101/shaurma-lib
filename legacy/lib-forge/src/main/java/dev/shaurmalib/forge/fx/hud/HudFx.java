package dev.shaurmalib.forge.fx.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Процедурні HUD-ефекти для режимів: прожилки по краях екрана, радіальні лінії
 * швидкості, м'яка віньєтка-рамка, кільце-таймер, товсті лінії під довільним кутом.
 *
 * <p>Усе — чиста 2D-геометрія через {@link GuiGraphics} і {@code POSITION_COLOR}
 * без пост-шейдерів, тому працює з Iris/Oculus, у VR і на слабких ПК.
 * Кольори — {@code 0xRRGGBB}, прозорість окремо (0..1). Час — секунди
 * (наприклад {@code System.currentTimeMillis() / 1000f}), щоб анімація не
 * залежала від тіків. Детермінований {@code seed} дає однакову «форму»
 * прожилок між кадрами — вони ростуть, а не стрибають.</p>
 *
 * <pre>{@code
 * HudFx.edgeVeins(g, w, h, 0x7BD34A, 0.30f, t, 42, 0.6f);   // зелені корені з країв
 * HudFx.speedLines(g, w, h, 0xFFFFFF, 0.2f, t, 40);         // ривок
 * HudFx.edgeFrame(g, w, h, 0x1A120C, 0.35f, 0.12f);         // темна рамка «дупла»
 * HudFx.ringTimer(g, x, y, 9, 2, 0.7f, 0xB26CFF, 0.9f);     // кільце залишку часу
 * }</pre>
 */
@OnlyIn(Dist.CLIENT)
public final class HudFx {

    private HudFx() {}

    // ── Примітиви ────────────────────────────────────────────────────────

    /** Товста лінія з (x1,y1) до (x2,y2): чотирикутник із плавним згасанням альфи від a1 до a2. */
    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float width,
                            int rgb, float a1, float a2) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-3f) return;
        float nx = -dy / len * width * 0.5f, ny = dx / len * width * 0.5f;
        int r = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int A1 = alpha(a1), A2 = alpha(a2);
        begin();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(m, x1 + nx, y1 + ny, 0).color(r, gg, b, A1).endVertex();
        buf.vertex(m, x1 - nx, y1 - ny, 0).color(r, gg, b, A1).endVertex();
        buf.vertex(m, x2 - nx, y2 - ny, 0).color(r, gg, b, A2).endVertex();
        buf.vertex(m, x2 + nx, y2 + ny, 0).color(r, gg, b, A2).endVertex();
        Tesselator.getInstance().end();
        end();
    }

    /** Кільце-таймер: дуга від 12 години за годинниковою стрілкою, {@code fraction} 0..1. */
    public static void ringTimer(GuiGraphics g, float cx, float cy, float radius, float thickness,
                                 float fraction, int rgb, float alpha) {
        fraction = clamp01(fraction);
        int segs = Math.max(8, Math.round(48 * fraction));
        if (fraction <= 0f) return;
        int r = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, b = rgb & 0xFF, A = alpha(alpha);
        begin();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        float ri = radius - thickness * 0.5f, ro = radius + thickness * 0.5f;
        for (int i = 0; i <= segs; i++) {
            double a = -Math.PI / 2 + Math.PI * 2 * fraction * i / segs;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            buf.vertex(m, cx + c * ro, cy + s * ro, 0).color(r, gg, b, A).endVertex();
            buf.vertex(m, cx + c * ri, cy + s * ri, 0).color(r, gg, b, A).endVertex();
        }
        Tesselator.getInstance().end();
        end();
    }

    /** Заповнений диск (м'який край: центр {@code alphaIn}, край {@code alphaOut}). */
    public static void disc(GuiGraphics g, float cx, float cy, float radius, int rgb, float alphaIn, float alphaOut) {
        int r = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        begin();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(m, cx, cy, 0).color(r, gg, b, alpha(alphaIn)).endVertex();
        for (int i = 0; i <= 32; i++) {
            double a = Math.PI * 2 * i / 32;
            buf.vertex(m, cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius, 0)
                .color(r, gg, b, alpha(alphaOut)).endVertex();
        }
        Tesselator.getInstance().end();
        end();
    }

    // ── Ефекти ───────────────────────────────────────────────────────────

    /**
     * М'яка рамка по краях: від краю ({@code alpha}) до прозорого на глибині
     * {@code depthFrac} від меншої сторони екрана.
     */
    public static void edgeFrame(GuiGraphics g, int w, int h, int rgb, float alpha, float depthFrac) {
        if (alpha <= 0.003f) return;
        float d = Math.min(w, h) * depthFrac;
        int r = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int A = alpha(alpha);
        begin();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // верх
        quad(buf, m, 0, 0, w, 0, w, d, 0, d, r, gg, b, A, A, 0, 0);
        // низ
        quad(buf, m, 0, h - d, w, h - d, w, h, 0, h, r, gg, b, 0, 0, A, A);
        // ліво
        quad(buf, m, 0, 0, d, 0, d, h, 0, h, r, gg, b, A, 0, 0, A);
        // право
        quad(buf, m, w - d, 0, w, 0, w, h, w - d, h, r, gg, b, 0, A, A, 0);
        Tesselator.getInstance().end();
        end();
    }

    /**
     * Розгалужені прожилки, що ростуть від країв екрана до центру (коріння,
     * тріщини, вени). {@code growth} 0..1 — наскільки далеко дотягнулись;
     * легке «дихання» товщини з часом {@code time}.
     */
    public static void edgeVeins(GuiGraphics g, int w, int h, int rgb, float alpha, float time, long seed, float growth) {
        if (alpha <= 0.003f || growth <= 0f) return;
        java.util.Random rnd = new java.util.Random(seed);
        float unit = Math.min(w, h);
        int roots = 14;
        for (int i = 0; i < roots; i++) {
            // старт на периметрі
            float t = (i + rnd.nextFloat() * 0.6f) / roots;
            float x, y, ang;
            float per = t * 4f;
            if (per < 1f)      { x = per * w;            y = 0;  ang = (float) (Math.PI / 2); }
            else if (per < 2f) { x = w;  y = (per - 1f) * h;     ang = (float) Math.PI; }
            else if (per < 3f) { x = (3f - per) * w;     y = h;  ang = (float) (-Math.PI / 2); }
            else               { x = 0;  y = (4f - per) * h;     ang = 0f; }
            float len = unit * (0.10f + rnd.nextFloat() * 0.14f) * growth;
            float breathe = 0.85f + 0.15f * (float) Math.sin(time * 2.4f + i);
            branch(g, rnd, x, y, ang, len, unit * 0.006f * breathe, rgb, alpha, 0);
        }
    }

    private static void branch(GuiGraphics g, java.util.Random rnd, float x, float y, float ang, float len,
                               float width, int rgb, float alpha, int depth) {
        int steps = 5;
        float step = len / steps;
        for (int s = 0; s < steps; s++) {
            ang += (rnd.nextFloat() - 0.5f) * 0.7f;
            float nx = x + (float) Math.cos(ang) * step, ny = y + (float) Math.sin(ang) * step;
            float k0 = 1f - (float) s / steps, k1 = 1f - (float) (s + 1) / steps;
            line(g, x, y, nx, ny, Math.max(0.6f, width * (0.4f + 0.6f * k0)), rgb, alpha * k0, alpha * k1);
            if (depth < 2 && rnd.nextFloat() < 0.35f) {
                branch(g, rnd, nx, ny, ang + (rnd.nextBoolean() ? 0.8f : -0.8f), len * 0.45f, width * 0.6f,
                    rgb, alpha * k1 * 0.9f, depth + 1);
            }
            x = nx; y = ny;
        }
    }

    /**
     * Радіальні лінії швидкості (ривок, падіння): від країв до центру, мерехтять.
     * {@code count} — кількість ліній.
     */
    public static void speedLines(GuiGraphics g, int w, int h, int rgb, float alpha, float time, int count) {
        if (alpha <= 0.003f) return;
        float cx = w / 2f, cy = h / 2f;
        float rOut = (float) Math.sqrt(cx * cx + cy * cy);
        for (int i = 0; i < count; i++) {
            float hsh = hash(i * 31 + (int) (time * 30));
            float a = (float) (Math.PI * 2 * (i + hash(i * 7) * 0.8f) / count);
            float inner = rOut * (0.45f + 0.30f * hsh);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            line(g, cx + c * rOut, cy + s * rOut, cx + c * inner, cy + s * inner,
                1f + 1.5f * hash(i * 13), rgb, alpha * (0.5f + 0.5f * hsh), 0f);
        }
    }

    /** Спалах-хвиля від країв до центру (0..1 — фаза). Корисно для «мережа підключилась». */
    public static void edgeWave(GuiGraphics g, int w, int h, int rgb, float alpha, float phase) {
        if (phase <= 0f || phase >= 1f) return;
        float depth = 0.05f + 0.45f * phase;
        edgeFrame(g, w, h, rgb, alpha * (1f - phase), depth);
    }

    // ── Внутрішнє ────────────────────────────────────────────────────────

    private static void quad(BufferBuilder buf, Matrix4f m,
                             float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
                             int r, int g, int b, int a1, int a2, int a3, int a4) {
        buf.vertex(m, x1, y1, 0).color(r, g, b, a1).endVertex();
        buf.vertex(m, x4, y4, 0).color(r, g, b, a4).endVertex();
        buf.vertex(m, x3, y3, 0).color(r, g, b, a3).endVertex();
        buf.vertex(m, x2, y2, 0).color(r, g, b, a2).endVertex();
    }

    private static void begin() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void end() {
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static int alpha(float a) {
        return Math.max(0, Math.min(255, Math.round(a * 255f)));
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float hash(int n) {
        int x = n * 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA6B;
        x ^= x >>> 13;
        return (x & 0xFFFF) / 65535f;
    }
}
