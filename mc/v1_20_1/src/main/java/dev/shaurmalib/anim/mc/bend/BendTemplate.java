package dev.shaurmalib.anim.mc.bend;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.shaurmalib.anim.bend.BendMath;
import net.minecraft.client.model.geom.ModelPart;

import java.util.ArrayList;
import java.util.List;

/**
 * Сітка кубоїда, розрізана площиною шарніра (середина по Y). Будується один раз на кубоїд і кешується
 * на ньому: 6 граней → верх, низ і 4 бічні × 2 = 10 квадів. Нові вершини на шарнірі отримують
 * інтерпольовані UV, тож текстура скіна не рветься.
 *
 * Геометрію беремо, записуючи ванільний {@code Cube.compile} з одиничною матрицею, — без доступу до
 * {@code ModelPart.Polygon/Vertex} (на Forge вони пакетні, на Fabric їх відкриває access widener).
 * Заодно працює з будь-якою геометрією, яку кубоїд реально малює (прибрані грані, inflate).
 *
 * Усе у просторі частини, пікселі.
 */
public final class BendTemplate {

    /** Шарнір кубоїда. */
    public final float hx, hy, hz;
    /**
     * Півтовщина і півдовжина БЕЗ inflate (рукав/штани мають той самий півот, що й шкіра під ними —
     * інакше шари розійдуться на згині).
     */
    public final float halfX, halfZ, halfLen;
    public final int quads;
    /** На вершину: x, y, z. */
    final float[] pos;
    /** На вершину: u, v. */
    final float[] uv;
    /**
     * На вершину шарніра: du/dy, dv/dy уздовж її бічного ребра. Miter зсуває вершину по ребру на {@code t}
     * (верхній сегмент) — і UV мусить піти за нею, інакше текстура трапеції стискається й перекошується по
     * діагоналі трикутників. У нижньому сегменті та сама точка лежить на {@code −t} від шарніра.
     */
    final float[] duv;
    /** На вершину: −1 верх, 0 шарнір, 1 низ. */
    final byte[] region;
    /** На квад: нормаль у просторі частини. */
    final float[] normal;
    /** На квад: чи належить нижньому сегменту (нормаль обертається з ним). */
    final boolean[] lower;

    private BendTemplate(ModelPart.Cube cube, float hx, float hy, float hz, List<float[]> verts, List<float[]> quadInfo) {
        this.hx = hx; this.hy = hy; this.hz = hz;
        this.halfX = (cube.maxX - cube.minX) * 0.5f;
        this.halfZ = (cube.maxZ - cube.minZ) * 0.5f;
        this.halfLen = (cube.maxY - cube.minY) * 0.5f;
        this.quads = quadInfo.size();
        pos = new float[quads * 12];
        uv = new float[quads * 8];
        duv = new float[quads * 8];
        region = new byte[quads * 4];
        normal = new float[quads * 3];
        lower = new boolean[quads];
        for (int i = 0; i < quads * 4; i++) {
            float[] v = verts.get(i);
            pos[i * 3] = v[0]; pos[i * 3 + 1] = v[1]; pos[i * 3 + 2] = v[2];
            uv[i * 2] = v[3]; uv[i * 2 + 1] = v[4];
            region[i] = (byte) v[5];
            if (v.length > 7) { duv[i * 2] = v[6]; duv[i * 2 + 1] = v[7]; }
        }
        for (int q = 0; q < quads; q++) {
            float[] info = quadInfo.get(q);
            normal[q * 3] = info[0]; normal[q * 3 + 1] = info[1]; normal[q * 3 + 2] = info[2];
            lower[q] = info[3] > 0;
        }
    }

    /** Шаблон із кешу кубоїда (або збудувати). {@code null} — кубоїд нестандартний, малювати ванільно. */
    public static BendTemplate of(ModelPart.Cube cube) {
        BendableCube bc = (BendableCube) cube;
        BendTemplate t = bc.shaurma$bendTemplate();
        if (t == null && !bc.shaurma$bendTemplateFailed()) {
            t = build(cube);
            if (t == null) bc.shaurma$markBendTemplateFailed();
            else bc.shaurma$setBendTemplate(t);
        }
        return t;
    }

    static BendTemplate build(ModelPart.Cube cube) {
        Recorder rec = new Recorder();
        cube.compile(new PoseStack().last(), rec, 0, 0, 1, 1, 1, 1);
        if (rec.verts.isEmpty() || rec.verts.size() % 4 != 0) return null;

        float[] h = new float[3];
        BendMath.cubeHinge(cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ, h);
        float hy = h[1];
        List<float[]> verts = new ArrayList<>();
        List<float[]> quadInfo = new ArrayList<>();
        for (int p = 0; p < rec.verts.size(); p += 4) {
            float[][] c = new float[4][];
            for (int i = 0; i < 4; i++) c[i] = withRegion(rec.verts.get(p + i), hy);
            float[] n = rec.normals.get(p);
            int a = -1;
            if (crosses(c[0], c[1], hy) && crosses(c[2], c[3], hy)) a = 0;
            else if (crosses(c[1], c[2], hy) && crosses(c[3], c[0], hy)) a = 1;
            if (a < 0) {
                addQuad(verts, quadInfo, n, c[0], c[1], c[2], c[3]);
                continue;
            }
            float[] v0 = c[a], v1 = c[a + 1], v2 = c[(a + 2) & 3], v3 = c[(a + 3) & 3];
            float[] m01 = split(v0, v1, hy), m23 = split(v2, v3, hy);
            // порядок обходу зберігається: v0→m01→m23→v3 і m01→v1→v2→m23
            addQuad(verts, quadInfo, n, v0, m01, m23, v3);
            addQuad(verts, quadInfo, n, m01, v1, v2, m23);
        }
        return new BendTemplate(cube, h[0], h[1], h[2], verts, quadInfo);
    }

    /** {x, y, z, u, v} → {x, y, z, u, v, region}. */
    private static float[] withRegion(float[] v, float hy) {
        float d = v[1] - hy;
        int region = d < -BendMath.RING_EPS ? -1 : d > BendMath.RING_EPS ? 1 : 0;
        return new float[] { v[0], v[1], v[2], v[3], v[4], region };
    }

    private static boolean crosses(float[] a, float[] b, float hy) {
        return (a[1] - hy) * (b[1] - hy) < 0;
    }

    /** Точка на ребрі a→b рівно на висоті шарніра (UV інтерполюються) + нахил UV уздовж ребра по Y. */
    private static float[] split(float[] a, float[] b, float hy) {
        float dy = b[1] - a[1];
        float t = (hy - a[1]) / dy;
        return new float[] {
            a[0] + (b[0] - a[0]) * t, hy, a[2] + (b[2] - a[2]) * t,
            a[3] + (b[3] - a[3]) * t, a[4] + (b[4] - a[4]) * t, 0,
            (b[3] - a[3]) / dy, (b[4] - a[4]) / dy };
    }

    private static void addQuad(List<float[]> verts, List<float[]> quadInfo, float[] n,
                                float[] a, float[] b, float[] c, float[] d) {
        verts.add(a); verts.add(b); verts.add(c); verts.add(d);
        boolean lower = a[5] > 0 || b[5] > 0 || c[5] > 0 || d[5] > 0;
        quadInfo.add(new float[] { n[0], n[1], n[2], lower ? 1 : 0 });
    }

    /** Записує вершини ванільного {@code Cube.compile} (позиція назад у пікселі). */
    private static final class Recorder implements VertexConsumer {
        final List<float[]> verts = new ArrayList<>();
        final List<float[]> normals = new ArrayList<>();

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v,
                           int overlay, int light, float nx, float ny, float nz) {
            verts.add(new float[] { x * 16f, y * 16f, z * 16f, u, v });
            normals.add(new float[] { nx, ny, nz });
        }

        // Ванільний Cube.compile кличе лише пакетний vertex(...) вище; решта — заглушки інтерфейсу.
        @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
    }
}
