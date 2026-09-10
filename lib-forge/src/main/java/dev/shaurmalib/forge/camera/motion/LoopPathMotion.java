package dev.shaurmalib.forge.camera.motion;

import dev.shaurmalib.common.camera.CameraCurveMath;
import dev.shaurmalib.common.camera.CameraPose;
import dev.shaurmalib.common.camera.Vec3Like;

import java.util.List;

/**
 * Безперервне гойдання камери по замкненій Catmull-Rom кривій через
 * список точок — "drone shot" ефект. Джерело: idle-фаза
 * {@code SDRoundCameraManager} (round-intro "гойдання перед possess"),
 * узагальнена на будь-яке використання (не лише SD): гарантовано
 * проходить точно через кожну задану точку, сама згладжує кути між
 * сегментами, без потреби вручну підбирати дотичні.
 * <p>
 * Опційний синусоїдний pitch-дрейф ({@link #withPitchDrift}) додає
 * кіношне "живе" коливання поверх базової траєкторії — той самий
 * ефект, що в оригіналі був захардкоджений як {@code DRIFT_AMPLITUDE_DEG}/
 * {@code DRIFT_SPEED}.
 * <p>
 * Немає стану екземпляра, окрім переданих точок — споживач сам зберігає
 * "поточний t" (типово {@code speed * elapsedSeconds}) і викликає
 * {@link #poseAt(double)} щотік.
 */
public final class LoopPathMotion {

    private final List<Vec3Like> points;
    private final List<Float> pointYaw;
    private final List<Float> pointPitch;

    private float driftAmplitudeDeg = 0f;
    private float driftSpeed = 0f;

    /**
     * @param points     контрольні точки (мінімум 3 для повноцінної петлі;
     *                   для 1–2 точок {@link dev.shaurmalib.common.camera.CameraCurveMath#catmullRom}
     *                   вироджується у статичну точку/лінійний lerp)
     * @param pointYaw   yaw у кожній відповідній точці (той самий розмір, що {@code points})
     * @param pointPitch pitch у кожній відповідній точці
     */
    public LoopPathMotion(List<Vec3Like> points, List<Float> pointYaw, List<Float> pointPitch) {
        if (points.size() != pointYaw.size() || points.size() != pointPitch.size()) {
            throw new IllegalArgumentException("points/pointYaw/pointPitch мають бути однакового розміру");
        }
        this.points = points;
        this.pointYaw = pointYaw;
        this.pointPitch = pointPitch;
    }

    /** Додає синусоїдний pitch-дрейф (кіношне "живе" коливання) поверх базової траєкторії. */
    public LoopPathMotion withPitchDrift(float amplitudeDeg, float speed) {
        this.driftAmplitudeDeg = amplitudeDeg;
        this.driftSpeed = speed;
        return this;
    }

    /**
     * Поза камери в момент {@code loopT} (неперервний параметр, що
     * обгортається по {@code points.size()} — типово
     * {@code speed * elapsedSeconds}, опційно з jitter-множником
     * швидкості, який рахує сам споживач перед викликом).
     *
     * @param elapsedSecondsForDrift секунди з початку руху, лише для
     *                               обчислення {@link #withPitchDrift}-
     *                               дрейфу (незалежний від {@code loopT},
     *                               бо швидкість петлі й швидкість
     *                               дрейфу — різні параметри).
     */
    public CameraPose poseAt(double loopT, double elapsedSecondsForDrift) {
        int n = points.size();
        float segF = (float) (loopT % n);
        int idx0 = ((int) Math.floor(segF) % n + n) % n;
        int idx1 = (idx0 + 1) % n;
        float frac = segF - (float) Math.floor(segF);

        Vec3Like pos = CameraCurveMath.catmullRom(points, loopT);
        float yaw = CameraCurveMath.lerpAngleShortest(pointYaw.get(idx0), pointYaw.get(idx1), frac);
        float pitch = CameraCurveMath.lerp(pointPitch.get(idx0), pointPitch.get(idx1), frac);

        if (driftAmplitudeDeg != 0f) {
            pitch += (float) (Math.sin(elapsedSecondsForDrift * driftSpeed) * driftAmplitudeDeg);
        }

        return new CameraPose(pos, yaw, pitch);
    }

    public int pointCount() {
        return points.size();
    }
}
