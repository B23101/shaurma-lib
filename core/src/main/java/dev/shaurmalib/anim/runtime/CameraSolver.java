package dev.shaurmalib.anim.runtime;

import dev.shaurmalib.anim.math.Mat4;
import dev.shaurmalib.anim.pose.BoneTransform;
import dev.shaurmalib.anim.pose.Pose;
import dev.shaurmalib.anim.rig.Rig;

import java.util.ArrayList;
import java.util.List;

/**
 * Камера першої особи «за головою»: де опинилось око анімованої моделі і куди дивиться голова.
 *
 * Ланцюжок кісток від кореня до голови (для гравця: {@code body} → {@code head}) множиться в просторі
 * сцени Blockbench (Y вгору, обличчя до −Z, права рука на +X) — тими ж правилами, що Blockbench показує
 * кістки: обертання {@code (−x, −y, z)}, зсув {@code (−x, y, z)}, порядок ZYX навколо півота.
 * Голова береться БЕЗ процедурного погляду ({@link Pose#headNoLook}): погляд — це сама камера (мишка).
 *
 * Результат — абсолютна точка ока відносно ступнів моделі та кути відносно спокою. Адаптер переводить
 * це у світ за поворотом тіла. Абсолютна точка (а не «зсув від стоячого ока») — щоб камера правильно
 * лягала навіть тоді, коли гра вже сама опустила око (поза SWIMMING у лежачого).
 */
public final class CameraSolver {

    private CameraSolver() {}

    public static final class Frame {
        /** Око в просторі сцени (пікселі від ступнів; +X праворуч, +Y вгору, −Z вперед). */
        public float eyeX, eyeY, eyeZ;
        /** Кути відносно стоячої голови, градуси: yaw праворуч +, pitch вниз +, roll за годинниковою +. */
        public float yaw, pitch, roll;
        public float positionWeight, rotationWeight;

        public boolean active() { return positionWeight > 1e-3f || rotationWeight > 1e-3f; }
    }

    /** @return false — у ригу немає ока/голови або камера в кадрі не задіяна. */
    public static boolean solve(Pose pose, Frame out) {
        Rig rig = pose.rig();
        float[] eye = rig.eye();
        int head = rig.indexOf("head");
        out.positionWeight = pose.cameraWeight;
        out.rotationWeight = pose.cameraRotWeight;
        if (eye == null || head < 0 || !out.active()) return false;

        List<Integer> chain = new ArrayList<>();
        for (int b = head; b >= 0; b = rig.parent(b)) chain.add(0, b);
        Mat4 m = new Mat4();
        for (int b : chain) {
            BoneTransform t = b == head ? pose.headNoLook : pose.bone(b);
            float[] p = rig.pivot(b);
            m.translate(-t.px, t.py, t.pz)
             .translate(p[0], p[1], p[2])
             .rotateZ((float) Math.toRadians(t.rz))
             .rotateY((float) Math.toRadians(-t.ry))
             .rotateX((float) Math.toRadians(-t.rx))
             .scale(t.sx, t.sy, t.sz)
             .translate(-p[0], -p[1], -p[2]);
        }
        float[] v = new float[3];
        m.transformPoint(eye[0], eye[1], eye[2], v);
        out.eyeX = v[0];
        out.eyeY = v[1];
        out.eyeZ = v[2];

        float[] f = new float[3], r = new float[3];
        m.transformNormal(0, 0, -1, f);
        m.transformNormal(1, 0, 0, r);
        out.pitch = (float) -Math.toDegrees(Math.asin(clamp(f[1])));
        out.yaw = (Math.abs(f[0]) < 1e-5f && Math.abs(f[2]) < 1e-5f) ? 0f : (float) Math.toDegrees(Math.atan2(f[0], -f[2]));
        out.roll = (float) -Math.toDegrees(Math.asin(clamp(r[1])));
        return true;
    }

    private static float clamp(float v) { return v < -1 ? -1 : (v > 1 ? 1 : v); }
}
