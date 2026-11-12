package dev.shaurmalib.anim.clip;

import dev.shaurmalib.anim.molang.Molang;
import dev.shaurmalib.anim.molang.MolangContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Канал (rotation / position / scale) однієї кістки: відсортовані ключові кадри XYZ.
 *
 * Правила інтерполяції — як у Blockbench (щоб у грі було рівно те, що бачив аніматор):
 * сегмент [a,b] іде від {@code a.post} до {@code b.pre}; STEP на {@code a} тримає значення;
 * CATMULLROM, якщо його має {@code a} або {@code b}; easing береться з {@code b}.
 * До першого кадру — значення першого, після останнього — останнього.
 */
public final class Channel {

    private final Keyframe[] frames;
    private final boolean constant;

    public Channel(List<Keyframe> keyframes) {
        List<Keyframe> sorted = new ArrayList<>(keyframes);
        sorted.sort(Comparator.comparingDouble(k -> k.time));
        this.frames = sorted.toArray(new Keyframe[0]);
        boolean c = true;
        for (Keyframe k : frames) {
            for (int a = 0; a < 3; a++) {
                if (!(k.pre[a] instanceof Molang.Const) || !(k.post[a] instanceof Molang.Const)) c = false;
            }
        }
        this.constant = c && frames.length == 1;
    }

    public boolean isEmpty() { return frames.length == 0; }

    public int size() { return frames.length; }

    public float lastTime() { return frames.length == 0 ? 0 : frames[frames.length - 1].time; }

    public Keyframe frame(int i) { return frames[i]; }

    /** Чи канал — одне незмінне значення (оптимізація й підказка для поз). */
    public boolean isConstant() { return constant; }

    /** @return false — канал порожній (кістку не чіпати). */
    public boolean sample(float t, MolangContext ctx, float[] out) {
        int n = frames.length;
        if (n == 0) return false;
        if (n == 1) {
            eval(frames[0].post, ctx, out);
            return true;
        }
        if (t <= frames[0].time) {
            eval(frames[0].pre, ctx, out);
            return true;
        }
        Keyframe last = frames[n - 1];
        if (t >= last.time) {
            eval(last.post, ctx, out);
            return true;
        }
        // двійковий пошук сегмента
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (frames[mid].time <= t) lo = mid; else hi = mid;
        }
        Keyframe a = frames[lo], b = frames[hi];
        float span = b.time - a.time;
        double f = span <= 1e-6f ? 1 : (t - a.time) / span;

        if (a.interp == Keyframe.Interp.STEP) {
            eval(a.post, ctx, out);
            return true;
        }
        f = b.easing.apply(f);

        boolean catmull = a.interp == Keyframe.Interp.CATMULLROM || b.interp == Keyframe.Interp.CATMULLROM
            || a.interp == Keyframe.Interp.BEZIER || b.interp == Keyframe.Interp.BEZIER;
        if (catmull) {
            Keyframe p0 = lo > 0 ? frames[lo - 1] : a;
            Keyframe p3 = hi + 1 < n ? frames[hi + 1] : b;
            for (int ax = 0; ax < 3; ax++) {
                double v0 = (p0 == a ? a.post : p0.post)[ax].eval(ctx);
                double v1 = a.post[ax].eval(ctx);
                double v2 = b.pre[ax].eval(ctx);
                double v3 = (p3 == b ? b.pre : p3.pre)[ax].eval(ctx);
                out[ax] = (float) catmullRom(f, v0, v1, v2, v3);
            }
        } else {
            for (int ax = 0; ax < 3; ax++) {
                double v1 = a.post[ax].eval(ctx);
                double v2 = b.pre[ax].eval(ctx);
                out[ax] = (float) (v1 + (v2 - v1) * f);
            }
        }
        return true;
    }

    private static void eval(Molang.Expr[] v, MolangContext ctx, float[] out) {
        out[0] = (float) v[0].eval(ctx);
        out[1] = (float) v[1].eval(ctx);
        out[2] = (float) v[2].eval(ctx);
    }

    static double catmullRom(double t, double p0, double p1, double p2, double p3) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }
}
