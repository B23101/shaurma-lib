package dev.shaurmalib.common.camera;

/**
 * Легкий носій 3D-координат для чистої математики {@link CameraCurveMath}
 * у {@code lib-common} — щоб цей модуль не тягнув
 * {@code net.minecraft.world.phys.Vec3} як залежність. У lib-forge
 * конвертація {@code Vec3 <-> Vec3Like} — однорядкові статичні методи
 * на боці forge-адаптера (не тут).
 */
public record Vec3Like(double x, double y, double z) {

    public Vec3Like add(Vec3Like other) {
        return new Vec3Like(x + other.x, y + other.y, z + other.z);
    }

    public Vec3Like add(double dx, double dy, double dz) {
        return new Vec3Like(x + dx, y + dy, z + dz);
    }

    public Vec3Like subtract(Vec3Like other) {
        return new Vec3Like(x - other.x, y - other.y, z - other.z);
    }

    public Vec3Like scale(double factor) {
        return new Vec3Like(x * factor, y * factor, z * factor);
    }

    public Vec3Like lerp(Vec3Like to, double t) {
        return new Vec3Like(
                x + (to.x - x) * t,
                y + (to.y - y) * t,
                z + (to.z - z) * t);
    }

    public double distanceTo(Vec3Like other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
