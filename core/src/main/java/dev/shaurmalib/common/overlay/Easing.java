package dev.shaurmalib.common.overlay;

/**
 * Easing-функції для анімацій оверлеїв (план, п. 3.4). Перенесено 1:1 з
 * {@code OverlayStyle.easeOutBack/easeInQuad/...} snipers_shaurma — самі
 * формули вже правильні й багато разів перевірені візуально, тому
 * бібліотека не переізобрітає їх, а лише централізує в одному
 * Forge-незалежному класі (float in/float out, нуль Minecraft-типів),
 * щоб і {@code OverlayEngine} (lib-forge), і майбутній рендер-шар для
 * іншої платформи могли використовувати ту саму математику.
 */
public final class Easing {

    private Easing() {}

    public static float linear(float t) {
        return clamp01(t);
    }

    public static float easeInQuad(float t) {
        t = clamp01(t);
        return t * t;
    }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float f = 1f - t;
        return 1f - f * f * f;
    }

    public static float easeInOutQuad(float t) {
        t = clamp01(t);
        return t < 0.5f ? 2f * t * t : 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f;
    }

    /** Використовується для "punch-in"/slide-in анімацій (легкий перебіг за 1.0 і назад). */
    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f, c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }

    public static float easeOutElastic(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float c4 = (2f * (float) Math.PI) / 3f;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1);
    }

    private static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }
}
