package dev.shaurmalib.common.overlay;

/**
 * Специфікація одного "зафарбувати екран" ефекту (план, п. 3.4 + 3.12,
 * {@code WorldTintOverlay}) — узагальнення {@code ScreenFadeOverlay} +
 * {@code DeathFadeOverlay} + {@code SniperStealthVignetteOverlay}: усі
 * три мали ідентичний fade-in/hold/fade-out цикл, що відрізнявся лише
 * кольором, тривалостями і чи текст показувати поверх.
 * <p>
 * {@code fullOpacity} відтворює важливу деталь оригіналу
 * ({@code ScreenFadeOverlay.fullOpacity}): чорний fade (телепорт) і білий
 * "GO!"-fade (кінематика) повністю перекривають екран, тоді як кольорові
 * fade-и (перемога/поразка лотереї, підсвітка) обмежені альфою 25%, щоб
 * гравець і далі бачив гру крізь тінт — це НЕバг, а свідоме дизайн-
 * рішення оригіналу, тому лишається дефолтною поведінкою рушія, а не
 * винятком, який консюмер мав би вручну відтворювати.
 */
public final class WorldTintSpec {

    /** Максимальна альфа кольорових (не full-opacity) тінтів — 1:1 з оригінального {@code * 0.25f}. */
    public static final float COLORED_ALPHA_CAP = 0.25f;

    private final int red;
    private final int green;
    private final int blue;
    private final boolean fullOpacity;
    private final int fadeInMs;
    private final int holdMs;
    private final int fadeOutMs;
    private final String textKey;
    private final int textRgb;

    public WorldTintSpec(int red, int green, int blue, boolean fullOpacity,
                          int fadeInMs, int holdMs, int fadeOutMs,
                          String textKey, int textRgb) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.fullOpacity = fullOpacity;
        this.fadeInMs = fadeInMs;
        this.holdMs = holdMs;
        this.fadeOutMs = fadeOutMs;
        this.textKey = textKey;
        this.textRgb = textRgb;
    }

    /** Простий чорний fade (телепорт, як {@code ScreenFadeOverlay.startFade}) — завжди full-opacity. */
    public static WorldTintSpec black(int fadeInMs, int holdMs, int fadeOutMs) {
        return new WorldTintSpec(0, 0, 0, true, fadeInMs, holdMs, fadeOutMs, null, 0xFFFFFF);
    }

    /** Білий fade (кінематика "GO!") — full-opacity. */
    public static WorldTintSpec white(int fadeInMs, int holdMs, int fadeOutMs) {
        return new WorldTintSpec(255, 255, 255, true, fadeInMs, holdMs, fadeOutMs, null, 0x000000);
    }

    /** Кольоровий тінт з текстом, обмежений {@link #COLORED_ALPHA_CAP} (перемога/поразка, підсвітка). */
    public static WorldTintSpec coloredWithText(int red, int green, int blue,
                                                 int fadeInMs, int holdMs, int fadeOutMs,
                                                 String textKey, int textRgb) {
        return new WorldTintSpec(red, green, blue, false, fadeInMs, holdMs, fadeOutMs, textKey, textRgb);
    }

    public int red() { return red; }
    public int green() { return green; }
    public int blue() { return blue; }
    public boolean fullOpacity() { return fullOpacity; }
    public int fadeInMs() { return fadeInMs; }
    public int holdMs() { return holdMs; }
    public int fadeOutMs() { return fadeOutMs; }
    public int totalMs() { return fadeInMs + holdMs + fadeOutMs; }
    public String textKey() { return textKey; }
    public int textRgb() { return textRgb; }

    public boolean isColored() {
        return red > 0 || green > 0 || blue > 0;
    }

    /** Альфа (0..1) фону в момент {@code elapsedMs} після старту — 1:1 формула з {@code ScreenFadeOverlay.render}. */
    public float backgroundAlphaAt(long elapsedMs) {
        float alpha;
        if (elapsedMs < fadeInMs) {
            alpha = (float) elapsedMs / fadeInMs;
        } else if (elapsedMs < fadeInMs + holdMs) {
            alpha = 1f;
        } else {
            alpha = 1f - (float) (elapsedMs - fadeInMs - holdMs) / fadeOutMs;
        }
        alpha = Math.max(0f, Math.min(1f, alpha));
        return (isColored() && !fullOpacity) ? alpha * COLORED_ALPHA_CAP : alpha;
    }

    /** Прогрес появи тексту (0..1) — текст з'являється лише після того, як фон досяг альфи 0.2, за формулою оригіналу. */
    public float textProgressAt(long elapsedMs) {
        float bgAlphaUnclamped;
        if (elapsedMs < fadeInMs) {
            bgAlphaUnclamped = (float) elapsedMs / fadeInMs;
        } else if (elapsedMs < fadeInMs + holdMs) {
            bgAlphaUnclamped = 1f;
        } else {
            bgAlphaUnclamped = 1f - (float) (elapsedMs - fadeInMs - holdMs) / fadeOutMs;
        }
        bgAlphaUnclamped = Math.max(0f, Math.min(1f, bgAlphaUnclamped));
        if (bgAlphaUnclamped <= 0.2f) return 0f;
        return Math.min(1f, (bgAlphaUnclamped - 0.2f) / 0.3f);
    }
}
