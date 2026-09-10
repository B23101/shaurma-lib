package dev.shaurmalib.forge.markers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import dev.shaurmalib.common.markers.BillboardOrientation.Vec3Component;

/**
 * Рушій примітивів для рендеру у світовому просторі (план, п. 3.13) —
 * узагальнення {@code quad}/{@code border}/{@code text}/{@code textRight}
 * з {@code LeaderboardWorldRenderer.drawBoard} (599 рядків, майже
 * половина класу — саме ці примітиви) в один переносний двигун. Кожен
 * консюмер (лідерборд, directional-мітки, C4-подібні мітки) описує лише
 * СВІЙ дизайн (кольори/розміри/лейаут карток), а координатну математику
 * (right/forward vectors → world-space vertex positions, z-bias шари,
 * billboard-текст без залежності від MC font-орієнтації) дає рушій.
 * <p>
 * <b>Не спрощення 1:1</b> — це прямий перенос тих самих обчислень
 * (той самий порядок вершин, той самий підхід до z-bias через зміщення
 * вздовж {@code forward}-нормалі, та сама текстова матриця, побудована
 * напряму з right/up векторів без тригонометрії в матриці) — оригінал
 * уже правильний і візуально перевірений, тому змінювати математику
 * ризиковано без потреби.
 * <p>
 * Усі методи статичні й без стану — консюмер викликає їх з власного
 * {@code RenderLevelStageEvent}-хука (як в оригіналі), рушій нічого не
 * підписує сам (той самий принцип "статичний сервіс", що
 * {@code TeleportService}).
 */
public final class WorldBillboardPrimitives {

    private WorldBillboardPrimitives() {}

    /**
     * Непрозорий кольоровий quad, зміщений на {@code bias} вздовж
     * {@code forward}-нормалі борду — z-bias шарування (фон=0,
     * картки/кнопки, обводки/лінії, аватар/текст), щоб копланарні
     * елементи не z-fighting-али. {@code x, y} — нижній лівий кут у
     * локальних координатах борду (вздовж {@code right} і вгору),
     * {@code w, h} — ширина/висота.
     */
    public static void quad(PoseStack ps, Vec3Component right, Vec3Component forward,
                             float bias, float x, float y, float w, float h, int argbColor) {
        if ((argbColor >>> 24) == 0) return;
        float rf = ((argbColor >> 16) & 0xFF) / 255f;
        float gf = ((argbColor >> 8) & 0xFF) / 255f;
        float bf = (argbColor & 0xFF) / 255f;
        float af = (argbColor >>> 24) / 255f;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();

        Tesselator t = Tesselator.getInstance();
        BufferBuilder buf = t.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = ps.last().pose();

        float x2 = x + w, y2 = y + h;
        float bx = (float) (forward.x() * bias);
        float bz = (float) (forward.z() * bias);

        float blX = (float) (right.x() * x) + bx, blZ = (float) (right.z() * x) + bz;
        float brX = (float) (right.x() * x2) + bx, brZ = (float) (right.z() * x2) + bz;

        buf.vertex(m, blX, y, blZ).color(rf, gf, bf, af).endVertex();
        buf.vertex(m, brX, y, brZ).color(rf, gf, bf, af).endVertex();
        buf.vertex(m, brX, y2, brZ).color(rf, gf, bf, af).endVertex();
        buf.vertex(m, blX, y2, blZ).color(rf, gf, bf, af).endVertex();
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
    }

    /** Тонка рамка навколо прямокутника {@code (x,y,w,h)} завтовшки {@code thickness}, з 4 окремих quad-ів. */
    public static void border(PoseStack ps, Vec3Component right, Vec3Component forward, float bias,
                               float x, float y, float w, float h, float thickness, int argbColor) {
        quad(ps, right, forward, bias, x, y + h - thickness, w, thickness, argbColor);
        quad(ps, right, forward, bias, x, y, w, thickness, argbColor);
        quad(ps, right, forward, bias, x, y, thickness, h, argbColor);
        quad(ps, right, forward, bias, x + w - thickness, y, thickness, h, argbColor);
    }

    /**
     * Текстурований quad (для іконок/аватарів) з довільним прямокутником
     * UV — узагальнення {@code avatar(...)}/{@code renderKitIcon(...)}
     * з оригіналу (обидва малювали текстурований quad, лише з різними UV).
     */
    public static void texturedQuad(PoseStack ps, Vec3Component right, Vec3Component forward, float bias,
                                     ResourceLocation texture,
                                     float x, float y, float w, float h,
                                     float u0, float v0, float u1, float v1, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();

        Tesselator t = Tesselator.getInstance();
        BufferBuilder buf = t.getBuilder();
        Matrix4f m = ps.last().pose();

        float x2 = x + w, y2 = y + h;
        float bx = (float) (forward.x() * bias);
        float bz = (float) (forward.z() * bias);
        float blX = (float) (right.x() * x) + bx, blZ = (float) (right.z() * x) + bz;
        float brX = (float) (right.x() * x2) + bx, brZ = (float) (right.z() * x2) + bz;

        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(m, blX, y, blZ).uv(u0, v1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, brX, y, brZ).uv(u1, v1).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, brX, y2, brZ).uv(u1, v0).color(1f, 1f, 1f, alpha).endVertex();
        buf.vertex(m, blX, y2, blZ).uv(u0, v0).color(1f, 1f, 1f, alpha).endVertex();
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableCull();
    }

    /**
     * Текст, розташований і орієнтований по площині борду через
     * матрицю, побудовану напряму з {@code right}/{@code forward}
     * векторів (без тригонометрії всередині матриці — той самий підхід,
     * що в оригіналі, з докладним поясненням орієнтації осей у
     * докстрінгу {@code LeaderboardWorldRenderer.text} — перенесено
     * буквально, бо цей вивід уже перевірений на всіх 4 значеннях yaw).
     *
     * @param bias    зміщення вздовж {@code forward}, той самий z-bias шар, що {@link #quad}.
     * @param centerH якщо {@code true} — {@code cx} це центр тексту по горизонталі, інакше лівий край.
     */
    public static void text(PoseStack ps, Font font,
                             Vec3Component right, Vec3Component forward, float bias,
                             float cx, float cy, String str, int argbColor,
                             boolean centerH, float scale) {
        if (str == null || str.isEmpty()) return;

        float originX = (float) (right.x() * cx) + (float) (forward.x() * bias);
        float originZ = (float) (right.z() * cx) + (float) (forward.z() * bias);

        ps.pushPose();
        ps.translate(originX, cy, originZ);

        ps.mulPoseMatrix(new Matrix4f(
                (float) right.x() * scale, 0f, (float) right.z() * scale, 0f,
                0f, -scale, 0f, 0f,
                (float) forward.x(), 0f, (float) forward.z(), 0f,
                0f, 0f, 0f, 1f
        ));

        int tw = font.width(str);
        float dx = centerH ? -tw / 2f : 0f;
        float dy = -font.lineHeight / 2f;

        MultiBufferSource.BufferSource src = Minecraft.getInstance().renderBuffers().bufferSource();
        font.drawInBatch(str, dx, dy, argbColor, false, ps.last().pose(), src,
                Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        src.endBatch();
        ps.popPose();
    }

    /** {@link #text} вирівняний по правому краю в {@code xRight} — узагальнення {@code textRight(...)} з оригіналу. */
    public static void textRight(PoseStack ps, Font font, Vec3Component right, Vec3Component forward, float bias,
                                  float xRight, float cy, String str, int argbColor, float scale) {
        if (str == null || str.isEmpty()) return;
        float tw = font.width(str) * scale;
        text(ps, font, right, forward, bias, xRight - tw, cy, str, argbColor, false, scale);
    }

    /**
     * Застосовує множник {@code alpha} до альфа-каналу {@code argbColor} —
     * той самий {@code applyA(...)} хелпер з оригіналу, потрібен майже
     * кожному виклику {@link #quad}/{@link #text} під час fade-in/out.
     */
    public static int applyAlpha(int argbColor, float alpha) {
        int a = (int) (((argbColor >>> 24) / 255f) * alpha * 255f);
        return (argbColor & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    /**
     * Двоколірний ромб (TRIANGLE_FAN, центр + 4 кути) з центром у поточній
     * позиції {@link PoseStack} — узагальнення ромбової мітки з
     * {@code C4MarkerRenderer} (свій-зелений/тіммейт-синій, з білим
     * центром-градієнтом). {@code right}/{@code up} — camera-facing осі
     * (типово з {@link dev.shaurmalib.common.markers.BillboardOrientation#cameraFacing}),
     * {@code size} — половина діагоналі ромба у світових одиницях.
     * <p>
     * На відміну від {@link #quad}, тут БЕЗ depth-test вимкнення керує
     * консюмер сам ДО виклику (в оригіналі мітка навмисно рендериться
     * крізь стіни — {@code RenderSystem.disableDepthTest()} викликаний
     * консюмером один раз перед циклом по сутностях, не всередині
     * примітиву, щоб не перемикати стан GL щокадру на кожну мітку).
     */
    public static void diamond(PoseStack ps, Vec3Component right, Vec3Component up,
                                float size, int centerArgb, int edgeArgb) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        Matrix4f mat = ps.last().pose();

        float cr = ((centerArgb >> 16) & 0xFF) / 255f, cg = ((centerArgb >> 8) & 0xFF) / 255f,
                cb = (centerArgb & 0xFF) / 255f, ca = (centerArgb >>> 24) / 255f;
        float er = ((edgeArgb >> 16) & 0xFF) / 255f, eg = ((edgeArgb >> 8) & 0xFF) / 255f,
                eb = (edgeArgb & 0xFF) / 255f, ea = (edgeArgb >>> 24) / 255f;

        float topX = (float) (up.x() * size), topY = (float) (up.y() * size), topZ = (float) (up.z() * size);
        float rgtX = (float) (right.x() * size), rgtY = (float) (right.y() * size), rgtZ = (float) (right.z() * size);
        float botX = -topX, botY = -topY, botZ = -topZ;
        float lftX = -rgtX, lftY = -rgtY, lftZ = -rgtZ;

        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(mat, 0, 0, 0).color(cr, cg, cb, ca).endVertex();
        buf.vertex(mat, topX, topY, topZ).color(er, eg, eb, ea).endVertex();
        buf.vertex(mat, rgtX, rgtY, rgtZ).color(er, eg, eb, ea).endVertex();
        buf.vertex(mat, botX, botY, botZ).color(er, eg, eb, ea).endVertex();
        buf.vertex(mat, lftX, lftY, lftZ).color(er, eg, eb, ea).endVertex();
        buf.vertex(mat, topX, topY, topZ).color(er, eg, eb, ea).endVertex();
        tess.end();
    }
}
