package dev.shaurmalib.meta.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Симуляція 3D-плюсиків хілу: кожен плюс злітає вгору, крутиться навколо вертикалі, трохи гойдається,
 * роздувається на старті й тане наприкінці. Лише математика (позиції, кути, альфа) — малює шар версії.
 * Час — секунди; {@link #tick} викликається щокадру з dt.
 */
public final class PlusBurst {

    public static final class Plus {
        public double x, y, z;
        public double vx, vy, vz;
        public float yaw, spin, wobblePhase;
        public float age, life, size;
        public int color;

        /** 0..1..0: швидке «вистрілювання» розміру і плавне згасання. */
        public float scale() {
            float t = age / life;
            float in = Math.min(1f, age / .18f);
            float pop = 1f + .35f * (float) Math.sin(Math.min(1f, age / .25f) * Math.PI);
            float out = t > .7f ? 1f - (t - .7f) / .3f : 1f;
            return size * easeOut(in) * pop * Math.max(0f, out);
        }

        public float alpha() {
            float t = age / life;
            return t > .6f ? Math.max(0f, 1f - (t - .6f) / .4f) : Math.min(1f, age / .1f);
        }

        /** Нахил «гойдання» в градусах. */
        public float roll() { return (float) Math.sin(wobblePhase + age * 5.0) * 12f; }
    }

    private static final List<Plus> LIVE = new ArrayList<>();
    private static final Random RND = new Random();
    public static final int MAX = 400;

    private PlusBurst() {}

    /**
     * Сплеск навколо точки (центр тіла гравця).
     * @param radius горизонтальний розкид
     */
    public static synchronized void spawn(double x, double y, double z, int count, int color, double radius) {
        spawn(x, y, z, count, color, radius, count * .035f);
    }

    /** @param duration за скільки секунд вилітають усі плюси (хіл, що триває, — довга черга) */
    public static synchronized void spawn(double x, double y, double z, int count, int color, double radius, float duration) {
        for (int i = 0; i < count && LIVE.size() < MAX; i++) {
            Plus p = new Plus();
            double a = RND.nextDouble() * Math.PI * 2, r = radius * (.35 + RND.nextDouble() * .65);
            p.x = x + Math.cos(a) * r;
            p.z = z + Math.sin(a) * r;
            p.y = y + (RND.nextDouble() - .5) * 1.2;
            p.vx = Math.cos(a) * .12;
            p.vz = Math.sin(a) * .12;
            p.vy = .75 + RND.nextDouble() * .55;
            p.yaw = RND.nextFloat() * 360f;
            p.spin = (120f + RND.nextFloat() * 200f) * (RND.nextBoolean() ? 1 : -1);
            p.wobblePhase = RND.nextFloat() * 6.28f;
            p.life = 1.1f + RND.nextFloat() * .7f;
            p.size = .09f + RND.nextFloat() * .07f;
            p.age = -i * (duration / Math.max(1, count));   // черга: вилітають не всі одночасно
            p.color = color;
            LIVE.add(p);
        }
    }

    public static synchronized void tick(float dt) {
        if (dt <= 0) return;
        dt = Math.min(dt, .1f);
        Iterator<Plus> it = LIVE.iterator();
        while (it.hasNext()) {
            Plus p = it.next();
            p.age += dt;
            if (p.age < 0) continue;
            if (p.age >= p.life) { it.remove(); continue; }
            double drag = Math.exp(-dt * 1.6);
            p.vx *= drag;
            p.vz *= drag;
            p.vy *= Math.exp(-dt * .9);
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.yaw += p.spin * dt;
            p.spin *= (float) Math.exp(-dt * .5);
        }
    }

    /** Знімок живих плюсів для малювання (без тих, що ще чекають своєї черги). */
    public static synchronized List<Plus> visible() {
        List<Plus> out = new ArrayList<>(LIVE.size());
        for (Plus p : LIVE) if (p.age >= 0) out.add(p);
        return out;
    }

    public static synchronized void clear() { LIVE.clear(); }

    static float easeOut(float t) { float u = 1f - t; return 1f - u * u * u; }
}
