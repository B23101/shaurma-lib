package dev.shaurmalib.common.overlay;

/**
 * Одна цифра відліку з pop-in/hold/pop-out анімацією (план, п. 3.4,
 * {@code AnimatedCountdownSystem}) — узагальнення базового циклу з
 * {@code CinematicCountdownOverlay} (DIGIT_POPIN_MS/DIGIT_HOLD_MS/
 * DIGIT_POPOUT_MS), БЕЗ snipers-специфічного glitch-VFX (chromatic
 * aberration, шумові смуги, посимвольна підміна символів опису) —
 * той шар лишається кастомним {@code CineHooks} у моді-споживачі
 * (план, п. 3.35, {@code client.cinematic}), бо є вираженим художнім
 * стилем snipers, а не універсальним патерном "показати цифру".
 * <p>
 * Це дає рушію лібу простий, повторно використовуваний "скелет" —
 * будь-який мод отримує working pop-in-цифру з коробки (масштаб + альфа
 * за формулою нижче) і фарбує/декорує її на свій смак, не переписуючи
 * саму анімаційну математику.
 */
public final class CountdownDigitEntry {

    private final int digit;
    private final long shownAtMs;
    private final int popInMs;
    private final int holdMs;
    private final int popOutMs;

    public CountdownDigitEntry(int digit, int popInMs, int holdMs, int popOutMs) {
        this(digit, System.currentTimeMillis(), popInMs, holdMs, popOutMs);
    }

    public CountdownDigitEntry(int digit, long shownAtMs, int popInMs, int holdMs, int popOutMs) {
        this.digit = digit;
        this.shownAtMs = shownAtMs;
        this.popInMs = popInMs;
        this.holdMs = holdMs;
        this.popOutMs = popOutMs;
    }

    /** 1:1 тайминги оригінального {@code CinematicCountdownOverlay} (220/620/160мс). */
    public static CountdownDigitEntry cinematicDefault(int digit) {
        return new CountdownDigitEntry(digit, 220, 620, 160);
    }

    public int digit() {
        return digit;
    }

    public int totalMs() {
        return popInMs + holdMs + popOutMs;
    }

    public boolean isExpired(long nowMs) {
        return (nowMs - shownAtMs) >= totalMs();
    }

    /** Масштаб 0..1.15..1.0 — легкий "punch" при появі, стабільно на hold, легке стиснення на popOut. */
    public float scaleAt(long nowMs) {
        long age = nowMs - shownAtMs;
        if (age < popInMs) {
            float t = Easing.easeOutBack((float) age / popInMs);
            return 0.4f + 0.6f * t;
        } else if (age < popInMs + holdMs) {
            return 1.0f;
        } else {
            float t = (float) (age - popInMs - holdMs) / popOutMs;
            return 1.0f - 0.15f * Easing.easeInQuad(t);
        }
    }

    public float alphaAt(long nowMs) {
        long age = nowMs - shownAtMs;
        if (age < popInMs) {
            return Math.min(1f, Easing.easeOutBack((float) age / popInMs) + 0.2f);
        } else if (age < popInMs + holdMs) {
            return 1f;
        } else {
            float t = (float) (age - popInMs - holdMs) / popOutMs;
            return Math.max(0f, 1f - Easing.easeInQuad(t));
        }
    }
}
