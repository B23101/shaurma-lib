package dev.shaurmalib.anim.bend;

/**
 * Геометрія згину кінцівки (лікоть / коліно) — без гри, щоб перевірятись тестами.
 *
 * <h2>Модель</h2>
 * Кубоїд руки/ноги розрізається площиною шарніра, перпендикулярною до довгої осі Y (простір частини,
 * як у {@code ModelPart}: Y вниз, верх кінцівки — менші Y). Вершини діляться на три кільця:
 * <ul>
 *   <li><b>upper</b> ({@code y < hinge.y}) — не рухаються;</li>
 *   <li><b>lower</b> ({@code y > hinge.y}) — twist навколо осі кінцівки, потім swing навколо півота;</li>
 *   <li><b>mid</b> (на шарнірі) — «на ус» (miter): точка перетину бічного ребра з бісекторною площиною
 *       між сегментами. Так верхнє і нижнє ребра сходяться в одній точці — без щілин, без зламу текстури.</li>
 * </ul>
 *
 * Обертання розкладається на <b>swing</b> (власне згин, вісь ⊥ Y) і <b>twist</b> (поворот передпліччя
 * навколо себе): {@code q = swing · twist}. Twist шарнірне кільце бере наполовину, swing — через miter.
 *
 * <h2>Півот на зовнішньому ребрі</h2>
 * Якщо swing крутити навколо центру кінцівки, зовнішній бік ліктя подовжується на {@code h·tan(θ/2)} з
 * кожного боку (при 90° рука на третину довша, при 150° — шпиль). Справжній лікоть згинається навколо
 * зовнішньої поверхні: вона зберігає довжину, а внутрішній бік складається в згин. Тому
 * {@link #set(float, float, float, float, float, float, float, float, float)} зсуває півот swing з осі на
 * опуклий бік на півтовщину кубоїда в напрямку згину. Внутрішнє кільце не заходить далі за
 * {@code halfLen − }{@link #MID_MARGIN}, інакше грані вивертаються назовні.
 * Кут swing обмежено {@link #MAX_SWING_DEG}.
 *
 * Ейлерові кути — як у {@code ModelPart}/кістках рушія: градуси, порядок ZYX ({@code q = Rz·Ry·Rx}).
 * Кватерніони — {@code float[4] = (x, y, z, w)}.
 *
 * Клас — змінний стан на одну кінцівку на кадр: {@link #set} один раз, потім {@link #lower}/{@link #mid}
 * для кожної вершини. Без алокацій після створення.
 */
public final class BendMath {

    /** Найбільший кут згину (swing), градуси. */
    public static final float MAX_SWING_DEG = 160f;

    /** Допуск «вершина на шарнірі» по Y (пікселі). */
    public static final float RING_EPS = 1e-3f;

    /** Скільки пікселів лишити від кінця сегмента, коли внутрішнє кільце складається (див. клас). */
    public static final float MID_MARGIN = 0.25f;

    private static final float IDENTITY_EPS = 1e-6f;

    /** Центр обертання swing: шарнір, зсунутий на опуклий бік згину. */
    private final float[] pivot = new float[3];
    private float midLimit = Float.MAX_VALUE;

    /** Повне обертання нижнього сегмента (swing після обмеження · twist). */
    private final float[] full = { 0, 0, 0, 1 };
    /** Половина twist — для шарнірного кільця. */
    private final float[] halfTwist = { 0, 0, 0, 1 };
    /** Нормаль бісекторної площини (swing½ · Y). */
    private final float[] bisector = { 0, 1, 0 };
    private final float[] hinge = new float[3];
    private final float[] tmp = new float[3];
    private final float[] twist = new float[4], twistInv = new float[4], swing = new float[4], halfSwing = new float[4];
    private final float[] ax = new float[3];
    private static final float[] Y = { 0, 1, 0 };
    private boolean active;
    private float swingRad;

    /** Півот у центрі кінцівки, без обмеження шарнірного кільця (чиста геометрія miter). */
    public boolean set(float rxDeg, float ryDeg, float rzDeg, float hx, float hy, float hz) {
        return set(rxDeg, ryDeg, rzDeg, hx, hy, hz, 0, 0, Float.MAX_VALUE);
    }

    /**
     * @param rxDeg,ryDeg,rzDeg обертання нижнього сегмента відносно верхнього (ZYX, градуси)
     * @param hx,hy,hz          точка шарніра у просторі частини (пікселі) — центр кінцівки
     * @param halfX,halfZ       півтовщина кубоїда по X і Z (пікселі): на стільки півот іде на опуклий бік
     * @param halfLen           половина довжини кінцівки (від шарніра до торця), пікселі
     * @return чи є згин узагалі (false — малюй ванільно)
     */
    public boolean set(float rxDeg, float ryDeg, float rzDeg, float hx, float hy, float hz,
                       float halfX, float halfZ, float halfLen) {
        hinge[0] = hx; hinge[1] = hy; hinge[2] = hz;
        midLimit = Math.max(0, halfLen - MID_MARGIN);
        float[] q = full;
        eulerZYX((float) Math.toRadians(rxDeg), (float) Math.toRadians(ryDeg), (float) Math.toRadians(rzDeg), q);

        // twist навколо Y: проєкція кватерніона на вісь
        float tw = q[3], ty = q[1];
        float tl = (float) Math.sqrt(tw * tw + ty * ty);
        twist[0] = twist[1] = twist[2] = 0; twist[3] = 1;
        if (tl > IDENTITY_EPS) { twist[1] = ty / tl; twist[3] = tw / tl; }
        // swing = q · twist⁻¹
        mul(q, conj(twist, twistInv), swing);
        normalize(swing);
        if (swing[3] < 0) { swing[0] = -swing[0]; swing[1] = -swing[1]; swing[2] = -swing[2]; swing[3] = -swing[3]; }

        // обмеження кута swing
        float sw = Math.min(1f, swing[3]);
        float angle = 2f * (float) Math.acos(sw);
        float max = (float) Math.toRadians(MAX_SWING_DEG);
        ax[0] = swing[0]; ax[1] = swing[1]; ax[2] = swing[2];
        float axLen = len(ax);
        if (axLen < IDENTITY_EPS) { angle = 0; ax[0] = 1; ax[1] = 0; ax[2] = 0; }
        else { ax[0] /= axLen; ax[1] /= axLen; ax[2] /= axLen; }
        if (angle > max) {
            angle = max;
            axisAngle(ax, angle, swing);
        }
        swingRad = angle;
        mul(swing, twist, full);
        normalize(full);

        // половинний twist
        float twistAngle = 2f * (float) Math.atan2(twist[1], twist[3]);
        axisAngle(Y, twistAngle * 0.5f, halfTwist);

        // бісектор: swing½ · Y
        axisAngle(ax, angle * 0.5f, halfSwing);
        rotate(halfSwing, 0, 1, 0, bisector);

        // півот: куди йде нижній сегмент (swing·Y по горизонталі) — увігнутий бік; опуклий — протилежний
        rotate(swing, 0, 1, 0, tmp);
        float dl = (float) Math.sqrt(tmp[0] * tmp[0] + tmp[2] * tmp[2]);
        pivot[0] = hx; pivot[1] = hy; pivot[2] = hz;
        if (dl > 1e-5f) {
            float wx = -tmp[0] / dl, wz = -tmp[2] / dl;
            float shift = Math.abs(wx) * halfX + Math.abs(wz) * halfZ; // до ребра/грані кубоїда в цьому напрямку
            pivot[0] += wx * shift;
            pivot[2] += wz * shift;
        }

        active = angle > 1e-5f || Math.abs(twistAngle) > 1e-5f;
        return active;
    }

    public boolean active() { return active; }

    /** Кут згину після обмеження, радіани. */
    public float swingRadians() { return swingRad; }

    public float[] hinge() { return hinge; }

    /** Центр обертання swing (шарнір, зсунутий на опуклий бік). */
    public float[] pivot() { return pivot; }

    /** Повне обертання нижнього сегмента {@code (x,y,z,w)} — для нормалей і того, що висить нижче шарніра. */
    public float[] lowerRotation() { return full; }

    /** Twist (навколо осі кінцівки через {@link #hinge()}) і swing (навколо {@link #pivot()}) окремо. */
    public float[] twistRotation() { return twist; }

    public float[] swingRotation() { return swing; }

    /** -1 — upper, 0 — шарнір, 1 — lower (за координатою Y вершини у просторі частини). */
    public int region(float y) {
        float d = y - hinge[1];
        return d < -RING_EPS ? -1 : d > RING_EPS ? 1 : 0;
    }

    /** Вершина за кільцем, у яке вона потрапила. */
    public void transform(float x, float y, float z, float[] out) {
        switch (region(y)) {
            case -1 -> { out[0] = x; out[1] = y; out[2] = z; }
            case 1 -> lower(x, y, z, out);
            default -> mid(x, y, z, out);
        }
    }

    /** Нижній сегмент: twist навколо осі кінцівки, потім {@code pivot + swing·(p − pivot)}. */
    public void lower(float x, float y, float z, float[] out) {
        rotate(twist, x - hinge[0], y - hinge[1], z - hinge[2], tmp);
        rotate(swing, tmp[0] + hinge[0] - pivot[0], tmp[1] + hinge[1] - pivot[1], tmp[2] + hinge[2] - pivot[2], out);
        out[0] += pivot[0]; out[1] += pivot[1]; out[2] += pivot[2];
    }

    /**
     * Кільце шарніра: половина twist, потім точка на прямій ребра {@code a + t·Y}, що лежить у бісекторній
     * площині через півот: {@code n·(a − pivot + tY) = 0} → {@code t = −(n·(a − pivot))/(n·Y)}.
     * {@code |t|} обмежено, щоб внутрішній бік не вивертався за торець сегмента.
     */
    public void mid(float x, float y, float z, float[] out) {
        rotate(halfTwist, x - hinge[0], y - hinge[1], z - hinge[2], tmp);
        float ax = tmp[0] + hinge[0], ay = tmp[1] + hinge[1], az = tmp[2] + hinge[2];
        float[] n = bisector;
        float ny = Math.max(n[1], 1e-4f); // n·Y = cos(θ/2) > 0, бо θ ≤ MAX_SWING_DEG < 180
        float t = -(n[0] * (ax - pivot[0]) + n[1] * (ay - pivot[1]) + n[2] * (az - pivot[2])) / ny;
        if (t > midLimit) t = midLimit; else if (t < -midLimit) t = -midLimit;
        out[0] = ax;
        out[1] = ay + t;
        out[2] = az;
    }

    /** Нормаль нижнього сегмента (одиничний вектор). */
    public void lowerNormal(float nx, float ny, float nz, float[] out) {
        rotate(full, nx, ny, nz, out);
    }

    /** Шарнір кубоїда: центр по X/Z і середина довгої осі Y (пікселі, простір частини). */
    public static void cubeHinge(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float[] out) {
        out[0] = (minX + maxX) * 0.5f;
        out[1] = (minY + maxY) * 0.5f;
        out[2] = (minZ + maxZ) * 0.5f;
    }

    // ── кватерніони (x, y, z, w) ─────────────────────────────────────────

    /** {@code q = Rz(z)·Ry(y)·Rx(x)} — як {@code Quaternionf.rotationZYX(z, y, x)} у ModelPart. */
    public static float[] eulerZYX(float x, float y, float z, float[] q) {
        float sx = (float) Math.sin(x * 0.5f), cx = (float) Math.cos(x * 0.5f);
        float sy = (float) Math.sin(y * 0.5f), cy = (float) Math.cos(y * 0.5f);
        float sz = (float) Math.sin(z * 0.5f), cz = (float) Math.cos(z * 0.5f);
        q[0] = sx * cy * cz - cx * sy * sz;
        q[1] = cx * sy * cz + sx * cy * sz;
        q[2] = cx * cy * sz - sx * sy * cz;
        q[3] = cx * cy * cz + sx * sy * sz;
        return q;
    }

    public static float[] axisAngle(float[] axis, float rad, float[] q) {
        float s = (float) Math.sin(rad * 0.5f);
        q[0] = axis[0] * s; q[1] = axis[1] * s; q[2] = axis[2] * s;
        q[3] = (float) Math.cos(rad * 0.5f);
        return q;
    }

    /** {@code out = a·b} (спершу b, потім a). {@code out} не може бути {@code a} чи {@code b}. */
    public static float[] mul(float[] a, float[] b, float[] out) {
        out[0] = a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1];
        out[1] = a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0];
        out[2] = a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3];
        out[3] = a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2];
        return out;
    }

    public static float[] conj(float[] q, float[] out) {
        out[0] = -q[0]; out[1] = -q[1]; out[2] = -q[2]; out[3] = q[3];
        return out;
    }

    /** {@code out = q·v·q⁻¹} для одиничного q. {@code out} може бути довільним масивом довжини ≥ 3. */
    public static void rotate(float[] q, float vx, float vy, float vz, float[] out) {
        float qx = q[0], qy = q[1], qz = q[2], qw = q[3];
        // t = 2·(q.xyz × v)
        float tx = 2 * (qy * vz - qz * vy);
        float ty = 2 * (qz * vx - qx * vz);
        float tz = 2 * (qx * vy - qy * vx);
        out[0] = vx + qw * tx + (qy * tz - qz * ty);
        out[1] = vy + qw * ty + (qz * tx - qx * tz);
        out[2] = vz + qw * tz + (qx * ty - qy * tx);
    }

    private static void normalize(float[] q) {
        float l = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (l < IDENTITY_EPS) { q[0] = q[1] = q[2] = 0; q[3] = 1; return; }
        q[0] /= l; q[1] /= l; q[2] /= l; q[3] /= l;
    }

    private static float len(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }
}
