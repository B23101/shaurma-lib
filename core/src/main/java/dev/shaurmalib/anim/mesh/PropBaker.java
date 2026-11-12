package dev.shaurmalib.anim.mesh;

import dev.shaurmalib.anim.clip.BoneTrack;
import dev.shaurmalib.anim.math.Mat4;

/**
 * Запікає анімований пропс у вершини, ВІДНОСНО півота батьківської кістки (простір сцени
 * Blockbench, Y вгору, блоки). Адаптер ставить PoseStack у півот кістки моделі гравця/geo
 * і лише переводить сцену в свій простір (для ванільної моделі — поворот на 180° навколо Z).
 *
 * Математика кісток — та сама послідовність, що {@code RenderUtils.prepMatrixForBone} у GeckoLib:
 * {@code T(-px,py,pz) · T(pivot) · Rz·Ry·Rx · S · T(-pivot)}.
 */
public final class PropBaker {

    private PropBaker() {}

    /** Семплер кісток пропса з кліпа (ключі у градусах/пікселях, як у файлі). */
    @FunctionalInterface
    public interface BoneSampler {
        boolean sample(String bone, BoneTrack.ChannelType type, float[] out);
    }

    @FunctionalInterface
    public interface QuadSink {
        void quad(float[] pos12, float[] uv8, float nx, float ny, float nz);
    }

    public static void bake(GeoMesh mesh, float[] parentPivotPx, BoneSampler sampler, QuadSink sink) {
        Mat4 root = new Mat4().translate(-parentPivotPx[0] / 16f, -parentPivotPx[1] / 16f, -parentPivotPx[2] / 16f);
        float[] pos = new float[12], tmp = new float[3], n = new float[3], anim = new float[3];
        for (GeoMesh.MeshBone b : mesh.roots) bone(b, root, sampler, sink, pos, tmp, n, anim);
    }

    private static void bone(GeoMesh.MeshBone b, Mat4 parent, BoneSampler sampler, QuadSink sink,
                             float[] pos, float[] tmp, float[] n, float[] anim) {
        Mat4 m = new Mat4().set(parent);
        float rx = b.rotX, ry = b.rotY, rz = b.rotZ;
        if (sampler != null && sampler.sample(b.name, BoneTrack.ChannelType.POSITION, anim)) {
            m.translate(-anim[0] / 16f, anim[1] / 16f, anim[2] / 16f);
        }
        m.translate(b.pivotX, b.pivotY, b.pivotZ);
        if (sampler != null && sampler.sample(b.name, BoneTrack.ChannelType.ROTATION, anim)) {
            rx += (float) Math.toRadians(-anim[0]);
            ry += (float) Math.toRadians(-anim[1]);
            rz += (float) Math.toRadians(anim[2]);
        }
        m.rotateZ(rz).rotateY(ry).rotateX(rx);
        boolean hidden = false;
        if (sampler != null && sampler.sample(b.name, BoneTrack.ChannelType.SCALE, anim)) {
            if (Math.abs(anim[0]) < 1e-4f && Math.abs(anim[1]) < 1e-4f && Math.abs(anim[2]) < 1e-4f) hidden = true;
            m.scale(anim[0], anim[1], anim[2]);
        }
        m.translate(-b.pivotX, -b.pivotY, -b.pivotZ);
        if (hidden) return; // масштаб 0 = кістку сховано разом з дітьми (класичний трюк Blockbench)

        for (GeoMesh.MeshCube c : b.cubes) {
            Mat4 cm = new Mat4().set(m)
                .translate(c.pivotX, c.pivotY, c.pivotZ)
                .rotateZ(c.rotZ).rotateY(c.rotY).rotateX(c.rotX)
                .translate(-c.pivotX, -c.pivotY, -c.pivotZ);
            for (GeoMesh.Quad q : c.quads) {
                for (int v = 0; v < 4; v++) {
                    cm.transformPoint(q.pos[v * 3], q.pos[v * 3 + 1], q.pos[v * 3 + 2], tmp);
                    pos[v * 3] = tmp[0];
                    pos[v * 3 + 1] = tmp[1];
                    pos[v * 3 + 2] = tmp[2];
                }
                cm.transformNormal(q.nx, q.ny, q.nz, n);
                sink.quad(pos, q.uv, n[0], n[1], n[2]);
            }
        }
        for (GeoMesh.MeshBone child : b.children) bone(child, m, sampler, sink, pos, tmp, n, anim);
    }
}
