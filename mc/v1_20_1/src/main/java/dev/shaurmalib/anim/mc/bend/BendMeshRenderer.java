package dev.shaurmalib.anim.mc.bend;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.shaurmalib.anim.bend.BendMath;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Малює кубоїди зігнутої частини замість ванільного {@code ModelPart.compile}: верхній сегмент як є,
 * нижній обернутий навколо шарніра, кільце шарніра «на ус» ({@link BendMath}). Формат вершин —
 * ванільний ({@code VertexConsumer.vertex(...)} з нормаллю), тож шейдери/буфери бачать звичайну модель.
 *
 * Лише потік рендеру: робочі об'єкти статичні, без алокацій у кадрі.
 */
public final class BendMeshRenderer {

    private BendMeshRenderer() {}

    private static final BendMath MATH = new BendMath();
    private static final float[] P = new float[3], N = new float[3];
    private static final Vector4f V4 = new Vector4f();
    private static final Vector3f V3 = new Vector3f();

    public static void render(List<ModelPart.Cube> cubes, BendablePart bend, PoseStack.Pose pose, VertexConsumer vc,
                              int light, int overlay, float r, float g, float b, float a) {
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        float rx = bend.shaurma$bendX(), ry = bend.shaurma$bendY(), rz = bend.shaurma$bendZ();
        for (ModelPart.Cube cube : cubes) {
            BendTemplate t = BendTemplate.of(cube);
            if (t == null || !MATH.set(rx, ry, rz, t.hx, t.hy, t.hz, t.halfX, t.halfZ, t.halfLen)) {
                cube.compile(pose, vc, light, overlay, r, g, b, a);
                continue;
            }
            for (int q = 0; q < t.quads; q++) {
                float nx = t.normal[q * 3], ny = t.normal[q * 3 + 1], nz = t.normal[q * 3 + 2];
                if (t.lower[q]) {
                    MATH.lowerNormal(nx, ny, nz, N);
                    V3.set(N[0], N[1], N[2]);
                } else {
                    V3.set(nx, ny, nz);
                }
                nm.transform(V3);
                float side = t.lower[q] ? -1f : 1f;
                for (int k = 0; k < 4; k++) {
                    int i = q * 4 + k;
                    float x = t.pos[i * 3], y = t.pos[i * 3 + 1], z = t.pos[i * 3 + 2];
                    float u = t.uv[i * 2], v = t.uv[i * 2 + 1];
                    switch (t.region[i]) {
                        case 1 -> MATH.lower(x, y, z, P);
                        case 0 -> {
                            MATH.mid(x, y, z, P);
                            // UV за реальним місцем точки на ребрі свого жорсткого сегмента (twist Y не змінює)
                            float shift = (P[1] - y) * side;
                            u += t.duv[i * 2] * shift;
                            v += t.duv[i * 2 + 1] * shift;
                        }
                        default -> { P[0] = x; P[1] = y; P[2] = z; }
                    }
                    m.transform(V4.set(P[0] / 16f, P[1] / 16f, P[2] / 16f, 1f));
                    vc.vertex(V4.x(), V4.y(), V4.z(), r, g, b, a, u, v, overlay, light, V3.x(), V3.y(), V3.z());
                }
            }
        }
    }
}
