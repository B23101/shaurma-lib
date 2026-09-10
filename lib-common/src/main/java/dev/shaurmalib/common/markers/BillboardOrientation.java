package dev.shaurmalib.common.markers;

/**
 * Чиста математика орієнтації "фіксованого yaw" білборду (план, п. 3.13) —
 * узагальнення обчислення {@code right}/{@code forward} векторів, що
 * дублювалось в оригіналі і в {@code LeaderboardWorldRenderer.onRenderLevel}
 * (борд лідерборду, що стоїть під заданим yaw, а не завжди повернутий до
 * гравця), і в {@code AttackDirectionOverlay}/{@code C4MarkerRenderer}
 * (де замість цього використовується camera-facing білборд — інший режим,
 * див. {@link #cameraFacing}).
 * <p>
 * Нуль Forge/Minecraft-типів навмисно — {@link Vec3Component} тут лише
 * найпростіший (x,y,z) noscript-тримач, щоб {@code lib-common} не залежав
 * від {@code net.minecraft.world.phys.Vec3} (план, розділ 0: Architecture
 * Sniffer вимагає це і для {@code net.minecraft.*}-типів, де без них можна
 * обійтись — тут можна, бо це проста тригонометрія).
 */
public final class BillboardOrientation {

    private BillboardOrientation() {}

    /** Найпростіший (x,y,z)-тримач без залежності на {@code net.minecraft.world.phys.Vec3}. */
    public record Vec3Component(double x, double y, double z) {

        public Vec3Component normalize() {
            double len = Math.sqrt(x * x + y * y + z * z);
            if (len < 1.0e-9) return new Vec3Component(0, 0, 0);
            return new Vec3Component(x / len, y / len, z / len);
        }

        public Vec3Component cross(Vec3Component o) {
            return new Vec3Component(
                    y * o.z - z * o.y,
                    z * o.x - x * o.z,
                    x * o.y - y * o.x);
        }

        public double dot(Vec3Component o) {
            return x * o.x + y * o.y + z * o.z;
        }
    }

    /**
     * Пара векторів (right, forward) для борду з фіксованим світовим yaw
     * (план, стиль {@code LeaderboardWorldRenderer}: борд стоїть на місці,
     * не обертається до гравця) — {@code right} вздовж ширини борду,
     * {@code forward} — нормаль борду, спрямована назовні (та сама умова,
     * що в оригіналі: {@code forward = right повернутий на -90° навколо Y}).
     *
     * @param yawDegrees yaw борду в градусах (той самий {@code point.yaw}, що
     *                   зберігається в конфігурованій точці розміщення мітки).
     */
    public static FixedYawAxes fixedYaw(float yawDegrees) {
        double yawRad = Math.toRadians(-yawDegrees);
        Vec3Component right = new Vec3Component(Math.cos(yawRad), 0, Math.sin(yawRad));
        Vec3Component forward = new Vec3Component(-Math.sin(yawRad), 0, Math.cos(yawRad));
        return new FixedYawAxes(right, forward);
    }

    public record FixedYawAxes(Vec3Component right, Vec3Component forward) {}

    /**
     * Пара векторів (right, up) для борду, що завжди повернутий до камери
     * (план, стиль {@code C4MarkerRenderer}/{@code AttackDirectionOverlay}) —
     * на відміну від {@link #fixedYaw}, тут орієнтація перераховується
     * щокадру з поточного напрямку погляду камери, а не зберігається
     * як статичний yaw мітки.
     *
     * @param cameraLookDirection нормалізований вектор напрямку погляду камери.
     */
    public static CameraFacingAxes cameraFacing(Vec3Component cameraLookDirection) {
        Vec3Component right = cameraLookDirection.cross(new Vec3Component(0, 1, 0)).normalize();
        Vec3Component up = right.cross(cameraLookDirection).normalize();
        return new CameraFacingAxes(right, up);
    }

    public record CameraFacingAxes(Vec3Component right, Vec3Component up) {}

    /**
     * Евклідова дистанція між точкою борду і камерою — той самий culling-
     * розрахунок, що {@code LeaderboardWorldRenderer.onRenderLevel}
     * ({@code CULL_DIST}) і {@code C4MarkerRenderer} ({@code dist < 0.3}
     * skip). Консюмер порівнює результат зі своїм власним cull/LOD-порогом
     * — рушій не нав'язує конкретні відстані, вони продуктово-специфічні
     * (лідерборд culls на 50 блоків, C4-мітка на 0.3 блока мінімум).
     */
    public static double distance(double dx, double dy, double dz) {
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * {@code true}, якщо точка позаду камери відносно напрямку погляду —
     * той самий backface-culling інваріант, що
     * {@code camFwd.dot(new Vec3(dx,dy,dz).normalize()) < -0.2} в оригіналі
     * {@code LeaderboardWorldRenderer}. {@code threshold} типово {@code -0.2}
     * (невеликий запас позаду прямого перпендикуляра, щоб борд не зникав
     * різко на межі 90°).
     */
    public static boolean isBehindCamera(Vec3Component cameraForward, Vec3Component toTargetNormalized, double threshold) {
        return cameraForward.dot(toTargetNormalized) < threshold;
    }
}
