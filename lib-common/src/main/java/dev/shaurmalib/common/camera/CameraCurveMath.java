package dev.shaurmalib.common.camera;

/**
 * Спільні математичні хелпери для будь-якої камерної системи бібліотеки
 * (п. 3.15 плану, "вільна камера" — фундамент, на якому будуються
 * {@code PathMotion}/{@code LoopPathMotion}/{@code BezierApproachMotion}
 * у lib-forge).
 * <p>
 * Узагальнення трьох незалежних копій, які вже існували в
 * snipers_shaurma з ІДЕНТИЧНОЮ математикою (кожна писана окремо для
 * свого менеджера камери, без спільного класу):
 * {@code core.client.camera.CameraMath} (lerp/easeInOutCubic/lerpAngleShortest),
 * {@code EndGameCameraManager} (ті самі 4 методи, скопійовані навмисно
 * "щоб не чіпати existing клас" — задокументовано в оригінальному
 * докстрінгу CameraMath.java), і частина математики
 * {@code SDRoundCameraManager} (stepTowardsAngle/stepTowards — обмежений
 * поворот "з розгоном", а не миттєвий стрибок).
 * <p>
 * Нуль стану, нуль Minecraft/Forge-типів — {@code double}/{@code float}
 * і {@link Vec3Like} лише як простий record-носій координат, щоб
 * lib-common не залежав від {@code net.minecraft.world.phys.Vec3}
 * (той факт, що Vec3 — офіційний Mojang-клас, а не Forge-обгортка,
 * означає що технічно можна було б використати його і тут напряму —
 * але для {@code lib-common} обрано власний {@link Vec3Like}, щоб
 * модуль лишався компільованим у чистому Java-модулі без будь-якої
 * Minecraft-залежності взагалі, на випадок non-Minecraft споживання
 * цієї математики (напр. інструмент реплею поза грою)).
 */
public final class CameraCurveMath {
    private CameraCurveMath() {}

    public static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Плавний in-out кубічний easing — прискорення на старті, гальмування наприкінці. */
    public static float easeInOutCubic(float t) {
        return t < 0.5f
                ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    /** Інтерполяція кута коротким шляхом по колу (уникає "перекруту" на 350°). */
    public static float lerpAngleShortest(float a, float b, float t) {
        float diff = ((b - a + 540f) % 360f) - 180f;
        return a + diff * t;
    }

    /**
     * Пересуває кутове значення {@code current} на щонайбільше
     * {@code maxStep} градусів у бік {@code target}, коротким шляхом по
     * колу (враховує обгортку 359°→0°). Джерело:
     * {@code SDRoundCameraManager.stepTowardsAngle} — обмежена кутова
     * швидкість повороту камери, щоб обертання "доганяло" ціль з
     * розгоном, а не стрибало на geometrically-correct кут щотік.
     */
    public static float stepTowardsAngle(float current, float target, float maxStep) {
        float diff = ((target - current + 540f) % 360f) - 180f; // (-180, 180]
        if (diff > maxStep) diff = maxStep;
        else if (diff < -maxStep) diff = -maxStep;
        return current + diff;
    }

    /**
     * Пересуває звичайне (нециклічне) значення {@code current} на
     * щонайбільше {@code maxStep} у бік {@code target} — для pitch, де
     * кутова обгортка 359°→0° не потрібна. Джерело:
     * {@code SDRoundCameraManager.stepTowards}.
     */
    public static float stepTowards(float current, float target, float maxStep) {
        float diff = target - current;
        if (diff > maxStep) diff = maxStep;
        else if (diff < -maxStep) diff = -maxStep;
        return current + diff;
    }

    /**
     * Позиція на замкненій Catmull-Rom кривій через {@code points}.
     * Гарантовано проходить точно через кожну задану точку, сама
     * згладжує кути між сегментами. Джерело: {@code SDCameraCurve.catmullRom}.
     *
     * @param points контрольні точки (мінімум 1; для 1-2 точок — виродженний випадок, див. нижче)
     * @param t      неперервний параметр, обгортається по {@code points.size()}
     */
    public static Vec3Like catmullRom(java.util.List<Vec3Like> points, double t) {
        int n = points.size();
        if (n == 1) return points.get(0);
        if (n == 2) {
            double tt = ((t % 1.0) + 1.0) % 1.0;
            return points.get(0).lerp(points.get(1), tt);
        }

        double wrapped = ((t % n) + n) % n; // гарантовано в [0, n)
        int i1 = (int) Math.floor(wrapped);
        double localT = wrapped - i1;

        int i0 = (i1 - 1 + n) % n;
        int i2 = (i1 + 1) % n;
        int i3 = (i1 + 2) % n;

        return catmullRomSegment(points.get(i0), points.get(i1), points.get(i2), points.get(i3), localT);
    }

    private static Vec3Like catmullRomSegment(Vec3Like p0, Vec3Like p1, Vec3Like p2, Vec3Like p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;

        double x = 0.5 * ((2 * p1.x()) + (-p0.x() + p2.x()) * t
                + (2 * p0.x() - 5 * p1.x() + 4 * p2.x() - p3.x()) * t2
                + (-p0.x() + 3 * p1.x() - 3 * p2.x() + p3.x()) * t3);
        double y = 0.5 * ((2 * p1.y()) + (-p0.y() + p2.y()) * t
                + (2 * p0.y() - 5 * p1.y() + 4 * p2.y() - p3.y()) * t2
                + (-p0.y() + 3 * p1.y() - 3 * p2.y() + p3.y()) * t3);
        double z = 0.5 * ((2 * p1.z()) + (-p0.z() + p2.z()) * t
                + (2 * p0.z() - 5 * p1.z() + 4 * p2.z() - p3.z()) * t2
                + (-p0.z() + 3 * p1.z() - 3 * p2.z() + p3.z()) * t3);

        return new Vec3Like(x, y, z);
    }

    /**
     * Проміжна контрольна точка для квадратичної Безьє наближення —
     * піднімає дугу над прямою лінією {@code from→to}, автоматично
     * масштабуючись від відстані (мінімум 4 блоки, або 12% довжини
     * перельоту — що більше). Джерело: {@code SDCameraCurve.buildApproachControlPoint}.
     */
    public static Vec3Like buildArcControlPoint(Vec3Like from, Vec3Like to) {
        Vec3Like mid = from.add(to).scale(0.5);
        double liftHeight = Math.max(4.0, from.distanceTo(to) * 0.12);
        return mid.add(0, liftHeight, 0);
    }

    /** Квадратична Безьє: from → control → to, параметр t ∈ [0,1]. Джерело: {@code SDCameraCurve.quadraticBezier}. */
    public static Vec3Like quadraticBezier(Vec3Like from, Vec3Like control, Vec3Like to, double t) {
        double oneMinusT = 1.0 - t;
        double x = oneMinusT * oneMinusT * from.x() + 2 * oneMinusT * t * control.x() + t * t * to.x();
        double y = oneMinusT * oneMinusT * from.y() + 2 * oneMinusT * t * control.y() + t * t * to.y();
        double z = oneMinusT * oneMinusT * from.z() + 2 * oneMinusT * t * control.z() + t * t * to.z();
        return new Vec3Like(x, y, z);
    }
}
