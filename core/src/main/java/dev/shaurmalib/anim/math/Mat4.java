package dev.shaurmalib.anim.math;

/** Мінімальна афінна матриця 4×4 (рядкова, float) для запікання пропсів без залежностей. */
public final class Mat4 {

    public final float[] m = new float[16];

    public Mat4() { identity(); }

    public Mat4 identity() {
        java.util.Arrays.fill(m, 0);
        m[0] = m[5] = m[10] = m[15] = 1;
        return this;
    }

    public Mat4 set(Mat4 o) {
        System.arraycopy(o.m, 0, m, 0, 16);
        return this;
    }

    /** this = this × o */
    public Mat4 mul(float[] o) {
        float[] r = new float[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += m[i * 4 + k] * o[k * 4 + j];
                r[i * 4 + j] = s;
            }
        }
        System.arraycopy(r, 0, m, 0, 16);
        return this;
    }

    public Mat4 translate(float x, float y, float z) {
        if (x == 0 && y == 0 && z == 0) return this;
        return mul(new float[] { 1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1 });
    }

    public Mat4 scale(float x, float y, float z) {
        if (x == 1 && y == 1 && z == 1) return this;
        return mul(new float[] { x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1 });
    }

    public Mat4 rotateX(float rad) {
        if (rad == 0) return this;
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        return mul(new float[] { 1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1 });
    }

    public Mat4 rotateY(float rad) {
        if (rad == 0) return this;
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        return mul(new float[] { c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1 });
    }

    public Mat4 rotateZ(float rad) {
        if (rad == 0) return this;
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        return mul(new float[] { c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 });
    }

    public void transformPoint(float x, float y, float z, float[] out) {
        out[0] = m[0] * x + m[1] * y + m[2] * z + m[3];
        out[1] = m[4] * x + m[5] * y + m[6] * z + m[7];
        out[2] = m[8] * x + m[9] * y + m[10] * z + m[11];
    }

    /** Нормаль: верхня 3×3 + нормалізація (для рівномірного/помірного масштабу досить). */
    public void transformNormal(float x, float y, float z, float[] out) {
        float nx = m[0] * x + m[1] * y + m[2] * z;
        float ny = m[4] * x + m[5] * y + m[6] * z;
        float nz = m[8] * x + m[9] * y + m[10] * z;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) len = 1;
        out[0] = nx / len;
        out[1] = ny / len;
        out[2] = nz / len;
    }
}
