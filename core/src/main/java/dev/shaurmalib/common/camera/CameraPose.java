package dev.shaurmalib.common.camera;

/**
 * Позиція + ротація камери в один момент часу — спільний тип входу/
 * виходу для {@code PathMotion}/{@code LoopPathMotion}/
 * {@code BezierApproachMotion} (lib-forge, {@code camera.motion}
 * пакет). Немає прив'язки до Minecraft-типів — forge-адаптер
 * конвертує в/з {@code Vec3}+{@code float yaw/pitch} одним викликом.
 */
public record CameraPose(Vec3Like position, float yaw, float pitch) {

    public static CameraPose of(double x, double y, double z, float yaw, float pitch) {
        return new CameraPose(new Vec3Like(x, y, z), yaw, pitch);
    }
}
