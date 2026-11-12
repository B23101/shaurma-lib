package dev.shaurmalib.platform.math;

/**
 * Версійно-незалежний 3D-вектор (замінює {@code net.minecraft.world.phys.Vec3},
 * який у різних версіях гри має різний API). Імутабельний.
 */
public record Vec3d(double x, double y, double z) {
    public static final Vec3d ZERO = new Vec3d(0, 0, 0);

    public Vec3d add(Vec3d o) { return new Vec3d(x + o.x, y + o.y, z + o.z); }
    public Vec3d add(double dx, double dy, double dz) { return new Vec3d(x + dx, y + dy, z + dz); }
    public Vec3d subtract(Vec3d o) { return new Vec3d(x - o.x, y - o.y, z - o.z); }
    public Vec3d scale(double f) { return new Vec3d(x * f, y * f, z * f); }
    public Vec3d lerp(Vec3d to, double t) {
        return new Vec3d(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t);
    }
    public double lengthSqr() { return x * x + y * y + z * z; }
    public double length() { return Math.sqrt(lengthSqr()); }
    public double distanceTo(Vec3d o) { return subtract(o).length(); }
    public Vec3d normalize() {
        double l = length();
        return l < 1.0E-7 ? ZERO : scale(1.0 / l);
    }
}
