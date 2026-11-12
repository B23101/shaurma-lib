package dev.shaurmalib.meta.mc.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.shaurmalib.meta.fx.PlusBurst;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 3D-плюсики хілу: об'ємний хрест (дві перехрещені «цеглинки») з яскравим лицем, темнішими боками
 * й ореолом-світінням. Крутиться навколо вертикалі, гойдається, злітає вгору ({@link PlusBurst}).
 * Викликається з події рендера світу (після сутностей).
 */
public final class HealPlusRenderer {

    private static long last = System.nanoTime();

    private HealPlusRenderer() {}

    public static void render(PoseStack m, MultiBufferSource buffers, Vec3 cam) {
        long now = System.nanoTime();
        float dt = Math.min(.1f, (now - last) / 1e9f);
        last = now;
        PlusBurst.tick(dt);
        var list = PlusBurst.visible();
        if (list.isEmpty()) return;
        VertexConsumer solid = buffers.getBuffer(MetaRenderTypes.PLUS);
        VertexConsumer glow = buffers.getBuffer(MetaRenderTypes.GLOW);
        for (PlusBurst.Plus p : list) {
            float s = p.scale(), al = p.alpha();
            if (s <= .005f || al <= .01f) continue;
            m.pushPose();
            m.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            m.mulPose(Axis.YP.rotationDegrees(p.yaw));
            m.mulPose(Axis.ZP.rotationDegrees(p.roll()));
            m.scale(s, s, s);
            Matrix4f mat = m.last().pose();
            int face = p.color, side = shade(p.color, .62f), edge = mix(p.color, 0xFFFFFF, .55f);
            // хрест: вертикальна й горизонтальна цеглинки 1×3 одиниці, глибина .55
            box(solid, mat, -.5f, -1.5f, -.28f, .5f, 1.5f, .28f, face, side, edge, al);
            box(solid, mat, -1.5f, -.5f, -.28f, 1.5f, .5f, .28f, face, side, edge, al);
            // м'яке світіння позаду (білборд не потрібен: хрест і так крутиться)
            // слабке світіння: при десятках плюсів адитивний шар інакше зливається в білу пляму
            float g = 1.5f;
            int gc = a(p.color, .07f * al), gz = a(p.color, 0);
            quad(glow, mat, -g, -g, 0, gz, g, -g, 0, gz, g, g, 0, gz, -g, g, 0, gz, 0, 0, gc);
            m.popPose();
        }
    }

    /** Кубоїд із кольором лиця (±z), боків і світлішими ребрами вгорі. */
    private static void box(VertexConsumer vc, Matrix4f mat, float x0, float y0, float z0, float x1, float y1, float z1,
                            int face, int side, int top, float al) {
        int f = a(face, al), sd = a(side, al), tp = a(top, al), bt = a(shade(side, .8f), al);
        // перед / зад
        q(vc, mat, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, f);
        q(vc, mat, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, f);
        // боки
        q(vc, mat, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, sd);
        q(vc, mat, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, sd);
        // верх / низ
        q(vc, mat, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, tp);
        q(vc, mat, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, bt);
    }

    private static void q(VertexConsumer vc, Matrix4f mat, float ax, float ay, float az, float bx, float by, float bz,
                          float cx, float cy, float cz, float dx, float dy, float dz, int c) {
        v(vc, mat, ax, ay, az, c);
        v(vc, mat, bx, by, bz, c);
        v(vc, mat, cx, cy, cz, c);
        v(vc, mat, dx, dy, dz, c);
    }

    /** Чотирикутник-світіння з центром {@code (cx,cy)} яскравішим: дві половини, щоб градієнт ішов від центра. */
    private static void quad(VertexConsumer vc, Matrix4f mat, float x0, float y0, float z0, int c0, float x1, float y1, float z1, int c1,
                             float x2, float y2, float z2, int c2, float x3, float y3, float z3, int c3,
                             float cx, float cy, int cc) {
        float[][] pts = {{x0, y0, c0}, {x1, y1, c1}, {x2, y2, c2}, {x3, y3, c3}};
        for (int i = 0; i < 4; i++) {
            float[] p0 = pts[i], p1 = pts[(i + 1) % 4];
            v(vc, mat, cx, cy, 0, cc);
            v(vc, mat, p0[0], p0[1], 0, (int) p0[2]);
            v(vc, mat, p1[0], p1[1], 0, (int) p1[2]);
            v(vc, mat, cx, cy, 0, cc);
        }
    }

    private static void v(VertexConsumer vc, Matrix4f mat, float x, float y, float z, int argb) {
        vc.vertex(mat, x, y, z).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255).endVertex();
    }

    private static int a(int rgb, float alpha) { return ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | (rgb & 0xFFFFFF); }

    private static int shade(int rgb, float f) {
        return (Math.min(255, (int) (((rgb >> 16) & 255) * f)) << 16) | (Math.min(255, (int) (((rgb >> 8) & 255) * f)) << 8)
                | Math.min(255, (int) ((rgb & 255) * f));
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        return (r << 16) | (g << 8) | (int) ((a & 255) * (1 - t) + (b & 255) * t);
    }
}
