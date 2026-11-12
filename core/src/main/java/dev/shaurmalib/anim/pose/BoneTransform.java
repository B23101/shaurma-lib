package dev.shaurmalib.anim.pose;

/**
 * Трансформ кістки у просторі Blockbench: обертання (градуси, порядок ZYX), зсув (пікселі),
 * масштаб. Значення — ВІДНОСНО спокою моделі (як ключі в Blockbench).
 */
public final class BoneTransform {

    public float rx, ry, rz;
    public float px, py, pz;
    public float sx = 1, sy = 1, sz = 1;

    public BoneTransform identity() {
        rx = ry = rz = px = py = pz = 0;
        sx = sy = sz = 1;
        return this;
    }

    public BoneTransform set(BoneTransform o) {
        rx = o.rx; ry = o.ry; rz = o.rz;
        px = o.px; py = o.py; pz = o.pz;
        sx = o.sx; sy = o.sy; sz = o.sz;
        return this;
    }

    public boolean isIdentity() {
        return rx == 0 && ry == 0 && rz == 0 && px == 0 && py == 0 && pz == 0 && sx == 1 && sy == 1 && sz == 1;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "rot(%.2f %.2f %.2f) pos(%.2f %.2f %.2f) scale(%.2f %.2f %.2f)",
            rx, ry, rz, px, py, pz, sx, sy, sz);
    }
}
