package dev.shaurmalib.forge.camera.motion;

import dev.shaurmalib.common.camera.CameraCurveMath;
import dev.shaurmalib.common.camera.CameraPose;
import dev.shaurmalib.common.camera.Vec3Like;

/**
 * "Наближення до цілі" рух камери — квадратична Безьє-дуга (автоматично
 * піднята над прямою лінією {@code from→to}, щоб камера не летіла
 * напролом через рельєф) + кут погляду, що ДОГАНЯЄ ціль з обмеженою
 * кутовою швидкістю (не стрибає щотік на geometrically-correct напрям),
 * зі "snap" до точного фінального кута в останні 10% руху, щоб не було
 * видимого стрибка в момент {@code possess}/вселення. Джерело: approach-
 * фаза {@code SDRoundCameraManager} (round-intro наближення до гравця
 * перед вселенням в нього), узагальнено на будь-яке "наближення до
 * фіксованої цілі з фінальним точним кутом" (не лише SD).
 * <p>
 * Стан цього класу — лише поточний згладжений кут погляду
 * ({@code lookYaw}/{@code lookPitch}), що еволюціонує з кожним викликом
 * {@link #step}; позиція камери — чиста функція часу (bezier), без
 * стану. Тому екземпляр створюється один раз на сесію наближення і
 * викликається щотік — той самий патерн, що {@code beginApproach()}/
 * {@code tickApproach()} в оригіналі.
 */
public final class BezierApproachMotion {

    private final Vec3Like from;
    private final Vec3Like control;
    private final Vec3Like to;
    private final float finalYaw;
    private final float finalPitch;
    private final int durationMs;
    private final float turnSpeedDegPerSec;

    private float lookYaw;
    private float lookPitch;

    /**
     * @param from               стартова позиція камери
     * @param to                 фінальна позиція (ціль наближення)
     * @param startYaw           початковий кут погляду (з якого починається згладжене обертання)
     * @param startPitch         початковий pitch погляду
     * @param finalYaw           точний кут погляду одразу після завершення (напр. кут гравця при possess)
     * @param finalPitch         точний фінальний pitch
     * @param durationMs         тривалість наближення
     * @param turnSpeedDegPerSec максимальна кутова швидкість повороту камери (140°/сек у оригіналі —
     *                           з запасом покриває навіть розворот на 180° за типовий durationMs≈4000мс)
     */
    public BezierApproachMotion(Vec3Like from, Vec3Like to,
                                 float startYaw, float startPitch,
                                 float finalYaw, float finalPitch,
                                 int durationMs, float turnSpeedDegPerSec) {
        this.from = from;
        this.to = to;
        this.control = CameraCurveMath.buildArcControlPoint(from, to);
        this.finalYaw = finalYaw;
        this.finalPitch = finalPitch;
        this.durationMs = Math.max(1, durationMs);
        this.turnSpeedDegPerSec = turnSpeedDegPerSec;
        this.lookYaw = startYaw;
        this.lookPitch = startPitch;
    }

    /**
     * Прораховує один тік руху (типово раз на 50мс / 20 тіків/сек — та
     * сама частота, що зазвичай викликається з {@code ClientTickEvent}).
     * Мутує внутрішній згладжений кут погляду і повертає нову позу.
     *
     * @param elapsedMs час з початку наближення
     */
    public CameraPose step(long elapsedMs) {
        float t = Math.min(1f, (float) elapsedMs / durationMs);
        float eased = CameraCurveMath.easeInOutCubic(t);

        Vec3Like pos = CameraCurveMath.quadraticBezier(from, control, to, eased);

        // Геометричний напрямок "прямо зараз на ціль" — миттєвий, може
        // стрибати залежно від того, як рухається камера відносно цілі.
        Vec3Like toTarget = to.subtract(pos);
        float dirYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x(), toTarget.z()));
        float dirHDist = (float) Math.sqrt(toTarget.x() * toTarget.x() + toTarget.z() * toTarget.z());
        float dirPitch = (float) -Math.toDegrees(Math.atan2(toTarget.y(), dirHDist));

        // В останні 10% руху цільовим кутом стає ТОЧНИЙ фінальний кут,
        // щоб не було стрибка кута в момент завершення.
        float snapT = Math.max(0f, (t - 0.9f) / 0.1f); // 0→1 за останні 10%
        float targetYaw = CameraCurveMath.lerpAngleShortest(dirYaw, finalYaw, snapT);
        float targetPitch = CameraCurveMath.lerp(dirPitch, finalPitch, snapT);

        // Обертання камери ДОГАНЯЄ ціль з обмеженою кутовою швидкістю —
        // плавний поворот "з розгоном", а не миттєвий стрибок щотік.
        float maxStep = turnSpeedDegPerSec / 20f; // град/тік (20 тіків/сек)
        lookYaw = CameraCurveMath.stepTowardsAngle(lookYaw, targetYaw, maxStep);
        lookPitch = CameraCurveMath.stepTowards(lookPitch, targetPitch, maxStep);

        return new CameraPose(pos, lookYaw, lookPitch);
    }

    /** true, коли наближення досягло {@code to} (elapsedMs >= durationMs). */
    public boolean isFinished(long elapsedMs) {
        return elapsedMs >= durationMs;
    }

    public int durationMs() {
        return durationMs;
    }
}
