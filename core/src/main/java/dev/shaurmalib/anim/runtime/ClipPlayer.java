package dev.shaurmalib.anim.runtime;

import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.math.Easing;
import dev.shaurmalib.anim.rig.BoneMask;

/**
 * Один кліп, що грає в шарі. Увесь стан — від годинника аніматора, тож вага й час обчислюються
 * АНАЛІТИЧНО для будь-якого моменту кадру ({@code envelope(clock)}), без накопичення похибки:
 * анімація плавна на будь-якому FPS і однакова на всіх клієнтах.
 */
public final class ClipPlayer {

    final AnimationClip clip;
    final BoneMask mask;
    final AnimationClip.LoopMode loop;
    final double start;
    final float speed, fadeIn, fadeOut, weight, look;
    float camPos, camRot;
    final boolean additive;
    final String tag;

    /** Момент, коли почалось згасання (stop або перехід на інший кліп); NaN — не згасає. */
    double fadeOutStart = Double.NaN;
    float fadeOutDuration;
    float weightAtFadeOut = 1f;

    ClipPlayer(AnimationClip clip, BoneMask mask, AnimationClip.LoopMode loop, double start, float speed,
               float fadeIn, float fadeOut, float weight, float look, boolean additive, String tag) {
        this.clip = clip;
        this.mask = mask;
        this.loop = loop;
        this.start = start;
        this.speed = speed;
        this.fadeIn = fadeIn;
        this.fadeOut = fadeOut;
        this.weight = weight;
        this.look = look;
        this.additive = additive;
        this.tag = tag;
    }

    public AnimationClip clip() { return clip; }
    public String tag() { return tag; }
    public boolean fadingOut() { return !Double.isNaN(fadeOutStart); }

    /** Неперервний (необгорнутий) локальний час, секунди. */
    float rawTime(double clock) {
        return (float) Math.max(0, (clock - start) * speed);
    }

    /** Час для семплінгу треків з урахуванням циклу/утримання. */
    float sampleTime(double clock) {
        float t = rawTime(clock);
        return switch (loop) {
            case LOOP -> clip.length() <= 0 ? 0 : t % clip.length();
            case HOLD, ONCE -> Math.min(t, clip.length());
        };
    }

    /** Вага 0..1 у момент {@code clock}: fade-in × fade-out × авто-згасання наприкінці ONCE. */
    float envelope(double clock) {
        double since = clock - start;
        float w = fadeIn <= 0 ? 1f : (float) Easing.SMOOTHSTEP.apply(since / fadeIn);
        if (loop == AnimationClip.LoopMode.ONCE) {
            float left = clip.length() - rawTime(clock);
            float ramp = fadeOut * Math.max(speed, 1e-3f);
            if (left <= 0) return 0;
            if (ramp > 0 && left < ramp) w *= (float) Easing.SMOOTHSTEP.apply(left / ramp);
        }
        if (fadingOut()) {
            if (fadeOutDuration <= 0) return 0;
            double k = (clock - fadeOutStart) / fadeOutDuration;
            if (k >= 1) return 0;
            w = Math.min(w, weightAtFadeOut * (float) (1 - Easing.SMOOTHSTEP.apply(k)));
        }
        return Math.max(0, w);
    }

    void beginFadeOut(double clock, float duration) {
        if (fadingOut()) return;
        weightAtFadeOut = envelope(clock);
        fadeOutStart = clock;
        fadeOutDuration = Math.max(0, duration);
    }

    boolean finished(double clock) {
        if (fadingOut() && clock - fadeOutStart >= fadeOutDuration) return true;
        return loop == AnimationClip.LoopMode.ONCE && rawTime(clock) >= clip.length();
    }
}
