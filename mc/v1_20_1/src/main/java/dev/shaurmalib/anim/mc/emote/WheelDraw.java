package dev.shaurmalib.anim.mc.emote;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.shaurmalib.anim.emote.Emote;
import dev.shaurmalib.anim.emote.EmoteWheel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Геометрія й малювання колеса (спільне для колеса і екрана налаштувань). */
public final class WheelDraw {

    private WheelDraw() {}

    public static final float SEGMENT = 360f / EmoteWheel.SLOTS;
    /** Проміжок між сегментами, градуси. */
    private static final float GAP = 1.6f;

    public static final int COL_SEGMENT = 0xB0141418, COL_HOVER = 0xE0D9A441, COL_EMPTY = 0x70141418,
        COL_CENTER = 0xC00B0B0E, COL_RIM = 0x60FFFFFF, COL_ACTIVE = 0xC0396B3A;

    /** Центр сегмента, градуси екрана (0 — праворуч, −90 — угорі). Слот 0 — угорі, далі за годинниковою. */
    public static float centerAngle(int slot) { return -90f + slot * SEGMENT; }

    /** Сегмент під курсором або −1 (у центрі ближче за {@code deadZone}). */
    public static int segmentAt(double mx, double my, float cx, float cy, float deadZone, float maxRadius) {
        double dx = mx - cx, dy = my - cy;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < deadZone || (maxRadius > 0 && d > maxRadius)) return -1;
        double a = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
        int i = (int) Math.floor((a + SEGMENT / 2) / SEGMENT);
        return Math.floorMod(i, EmoteWheel.SLOTS);
    }

    /** Заповнений сектор кільця (r0 = 0 — сектор круга). */
    public static void sector(GuiGraphics g, float cx, float cy, float r0, float r1, float a0, float a1, int argb) {
        g.flush(); // спершу намалювати те, що GuiGraphics накопичив, — інакше порядок шарів зламається
        Matrix4f m = g.pose().last().pose();
        float a = (argb >>> 24 & 255) / 255f, r = (argb >> 16 & 255) / 255f, gr = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int steps = Math.max(2, (int) Math.ceil(Math.abs(a1 - a0) / 3f));
        for (int i = 0; i <= steps; i++) {
            float t = (a0 + (a1 - a0) * i / steps) * Mth.DEG_TO_RAD;
            float c = Mth.cos(t), s = Mth.sin(t);
            buf.vertex(m, cx + c * r1, cy + s * r1, 0).color(r, gr, b, a).endVertex();
            buf.vertex(m, cx + c * r0, cy + s * r0, 0).color(r, gr, b, a).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void disc(GuiGraphics g, float cx, float cy, float r, int argb) { sector(g, cx, cy, 0, r, 0, 360, argb); }

    /**
     * Повне колесо: 8 сегментів з назвами/іконками.
     * @param hovered  підсвічений сегмент (−1 — жоден)
     * @param active   емоція, що зараз грає на гравці (підсвічується зеленим), або null
     */
    public static void wheel(GuiGraphics g, Emote[] slots, float cx, float cy, float r0, float r1,
                             int hovered, Emote active) {
        for (int i = 0; i < EmoteWheel.SLOTS; i++) {
            float c = centerAngle(i);
            Emote e = slots[i];
            int col = i == hovered ? COL_HOVER : e == null ? COL_EMPTY : e == active ? COL_ACTIVE : COL_SEGMENT;
            float grow = i == hovered ? 4 : 0;
            sector(g, cx, cy, r0, r1 + grow, c - SEGMENT / 2 + GAP, c + SEGMENT / 2 - GAP, col);
            sector(g, cx, cy, r1 + grow - 1.5f, r1 + grow, c - SEGMENT / 2 + GAP, c + SEGMENT / 2 - GAP, COL_RIM);
        }
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < EmoteWheel.SLOTS; i++) {
            Emote e = slots[i];
            float rad = centerAngle(i) * Mth.DEG_TO_RAD, mid = (r0 + r1) / 2;
            int lx = Math.round(cx + Mth.cos(rad) * mid), ly = Math.round(cy + Mth.sin(rad) * mid);
            if (e == null) {
                g.drawCenteredString(font, "·", lx, ly - 4, 0x80FFFFFF);
                continue;
            }
            int textY = ly - 4;
            ResourceLocation icon = e.icon() == null ? null : ResourceLocation.tryParse(e.icon());
            if (icon != null) {
                g.blit(icon, lx - 8, ly - 14, 0, 0, 16, 16, 16, 16);
                textY = ly + 4;
            }
            Component name = EmoteText.name(e);
            int maxW = Math.round((r1 - r0) * 1.25f);
            if (font.width(name) > maxW) {
                String s = font.plainSubstrByWidth(name.getString(), maxW - font.width("…")) + "…";
                g.drawCenteredString(font, s, lx, textY, i == hovered ? 0xFFFFFFFF : 0xFFE0E0E0);
            } else {
                g.drawCenteredString(font, name, lx, textY, i == hovered ? 0xFFFFFFFF : 0xFFE0E0E0);
            }
        }
    }
}
