package dev.shaurmalib.anim.clip;

import dev.shaurmalib.anim.math.Easing;
import dev.shaurmalib.anim.molang.Molang;

/**
 * Ключовий кадр каналу: час у секундах, значення «до» (pre) і «після» (post) — як у Bedrock,
 * де на одному кадрі може бути стрибок. Значення — {@link Molang.Expr} (константа або вираз).
 */
public final class Keyframe {

    public enum Interp {
        LINEAR, CATMULLROM, STEP, BEZIER;

        public static Interp parse(String s) {
            if (s == null) return LINEAR;
            switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "catmullrom": case "smooth": return CATMULLROM;
                case "step": return STEP;
                case "bezier": return BEZIER;
                default: return LINEAR;
            }
        }
    }

    public final float time;
    public final Molang.Expr[] pre;
    public final Molang.Expr[] post;
    public final Interp interp;
    /** Крива сегмента, що ЗАКІНЧУЄТЬСЯ цим кадром (семантика GeckoLib easing). */
    public final Easing easing;

    public Keyframe(float time, Molang.Expr[] pre, Molang.Expr[] post, Interp interp, Easing easing) {
        this.time = time;
        this.pre = pre;
        this.post = post;
        this.interp = interp == null ? Interp.LINEAR : interp;
        this.easing = easing == null ? Easing.LINEAR : easing;
    }

    public static Keyframe constant(float time, float x, float y, float z) {
        Molang.Expr[] v = { Molang.constant(x), Molang.constant(y), Molang.constant(z) };
        return new Keyframe(time, v, v, Interp.LINEAR, Easing.LINEAR);
    }
}
