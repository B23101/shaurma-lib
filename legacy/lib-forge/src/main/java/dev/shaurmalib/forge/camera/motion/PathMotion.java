package dev.shaurmalib.forge.camera.motion;

import dev.shaurmalib.common.camera.CameraCurveMath;
import dev.shaurmalib.common.camera.CameraPose;
import dev.shaurmalib.common.camera.Vec3Like;

/**
 * Найпростіший camera-motion: пряме переміщення "з точки А в точку Б"
 * за задану тривалість, з ease-in-out по позиції та коротким-шляхом-
 * по-колу lerp по yaw (уникає перекруту 350°→10°). Джерело:
 * {@code EndGameCameraManager} — узагальнено на будь-який виклик, не
 * лише end-game сцену статистики.
 * <p>
 * Немає стану, окрім переданих у конструктор параметрів — споживач сам
 * зберігає екземпляр і викликає {@link #poseAt(long)} щотік (той самий
 * патерн, що {@code EndGameCameraManager.tick()} робив вручну).
 * <pre>{@code
 * PathMotion motion = new PathMotion(fromPose, toPose, 2500);
 * long startMs = System.currentTimeMillis();
 * // щотік:
 * CameraPose pose = motion.poseAt(System.currentTimeMillis() - startMs);
 * freeCamera.moveSmooth(pose.position().x(), pose.position().y(), pose.position().z(), pose.yaw(), pose.pitch());
 * if (motion.isFinished(elapsed)) { ... }
 * }</pre>
 */
public final class PathMotion {

    private final CameraPose from;
    private final CameraPose to;
    private final int durationMs;

    public PathMotion(CameraPose from, CameraPose to, int durationMs) {
        this.from = from;
        this.to = to;
        this.durationMs = Math.max(1, durationMs);
    }

    /** Поза камери на момент {@code elapsedMs} з початку руху (затискається до [0, durationMs]). */
    public CameraPose poseAt(long elapsedMs) {
        float t = Math.min(1f, (float) elapsedMs / durationMs);
        float eased = CameraCurveMath.easeInOutCubic(t);

        Vec3Like pos = from.position().lerp(to.position(), eased);
        float yaw = CameraCurveMath.lerpAngleShortest(from.yaw(), to.yaw(), eased);
        float pitch = CameraCurveMath.lerp(from.pitch(), to.pitch(), eased);

        return new CameraPose(pos, yaw, pitch);
    }

    /** true, коли рух досяг {@code to} (elapsedMs >= durationMs). */
    public boolean isFinished(long elapsedMs) {
        return elapsedMs >= durationMs;
    }

    public int durationMs() {
        return durationMs;
    }
}
