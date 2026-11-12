package dev.shaurmalib.common.camera.replay;

/**
 * Одна ключова точка Replay-клипу: де стоїть камера і куди дивиться.
 *
 * @param intervalToNextMs скільки мс камера їде від ЦІЄЇ точки до НАСТУПНОЇ.
 *                         {@code <= 0} — взяти спільний інтервал клипу
 *                         ({@link ReplayClip#sharedIntervalMs()}). Для
 *                         останньої точки поле ігнорується.
 * @param fov              поле зору в градусах для цієї точки; {@code <= 0} —
 *                         не чіпати (лишається налаштування гравця).
 */
public record ReplayPoint(double x, double y, double z,
                          float yaw, float pitch,
                          float fov,
                          int intervalToNextMs) {

    public static ReplayPoint of(double x, double y, double z, float yaw, float pitch) {
        return new ReplayPoint(x, y, z, yaw, pitch, 0f, 0);
    }

    public ReplayPoint withInterval(int ms) {
        return new ReplayPoint(x, y, z, yaw, pitch, fov, ms);
    }

    public ReplayPoint withFov(float newFov) {
        return new ReplayPoint(x, y, z, yaw, pitch, newFov, intervalToNextMs);
    }
}
