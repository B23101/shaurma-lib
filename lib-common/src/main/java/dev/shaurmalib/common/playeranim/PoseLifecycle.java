package dev.shaurmalib.common.playeranim;

/**
 * Чиста (без PAL, без Minecraft) стейт-машина життєвого циклу однієї пози
 * на одному шарі: {@code IDLE → PLAYING → FADING_OUT → IDLE}.
 * Вона лише <i>вирішує</i>, що робити далі; сама нічого не виконує —
 * виконує {@code PoseLayerRuntime} у lib-forge. Через це вона тестується
 * юніт-тестами без жодної залежності.
 *
 * <h3>Чому fade-out мусить бути пре-емптивним</h3>
 * {@code ModifierLayer.replaceAnimationWithFade} додає fade лише якщо
 * поточна анімація ще <b>активна</b>. One-shot, що вже сам зупинився, —
 * неактивний: fade не додасться, а {@code AnimationStack} пропускає
 * неактивний шар, тож поза зникла б миттєво. Тому fade-out стартує
 * <i>поки кліп ще грає</i>, за {@code fadeOutTicks} до його {@code
 * stopTick} (PAL гарантує {@code stopTick >= endTick + 3}, тобто хвіст
 * у кліпа завжди є). Якщо залишок менший за {@code fadeOutTicks} — fade
 * скорочується до залишку (мінімум 1 тік).
 *
 * <h3>Чому {@code FADING_OUT} рахує власний таймер</h3>
 * Саме в цей момент PAL може перестати тікати шар: {@code
 * AnimationStack.tick} тікає лише активні шари, а коли обидві анімації
 * fade-у неактивні, {@code canRemove()} модифікатора ніколи не
 * перевіряється. Тож завершення визначаємо власним лічильником, а не
 * питаємо PAL.
 *
 * <h3>Таблиця переходів</h3>
 * <pre>
 * IDLE      tick / requestStop            → NONE
 * PLAYING   tick, кліп неактивний         → FINISH            (fade вже неможливий)
 * PLAYING   tick, HOLD, кліп активний     → NONE              (сам не завершується)
 * PLAYING   tick, ONE_SHOT, fadeOut==0    → NONE              (чекаємо, доки кліп сам зупиниться)
 * PLAYING   tick, ONE_SHOT, залишок ≤ fadeOut → START_FADE_OUT (довжина = max(1, залишок))
 * PLAYING   requestStop, fadeOut&gt;0, кліп активний → START_FADE_OUT (довжина = min(fadeOut, залишок))
 * PLAYING   requestStop, fadeOut==0 або кліп неактивний → FINISH
 * FADING_OUT tick                         → NONE; лічильник−1; при ≤0 → FINISH
 * FADING_OUT requestStop                  → NONE              (не відтягуємо кінець)
 * будь-який begin()                       → PLAYING           (новий trigger посеред fade)
 * </pre>
 */
public final class PoseLifecycle {

    public enum State { IDLE, PLAYING, FADING_OUT }

    public enum Decision { NONE, START_FADE_OUT, FINISH }

    /** Значення "залишку" для кліпа, що ніколи не зупиняється сам (нескінченний). */
    public static final int UNBOUNDED = Integer.MAX_VALUE;

    private State state = State.IDLE;
    private boolean infinite;
    private int fadeOutTicks;
    private int fadeLength;
    private int fadeRemaining;

    /**
     * Починає (або перезапускає) життєвий цикл. Дозволений з будь-якого
     * стану, зокрема посеред {@code FADING_OUT} — новий trigger просто
     * скидає в {@code PLAYING}.
     *
     * @param infinite     {@code KeyframeAnimation.isInfinite} <b>самого кліпа</b>
     * @param fadeOutTicks довжина fade-out з {@link PoseSource}
     */
    public void begin(boolean infinite, int fadeOutTicks) {
        this.state = State.PLAYING;
        this.infinite = infinite;
        this.fadeOutTicks = Math.max(0, fadeOutTicks);
        this.fadeLength = 0;
        this.fadeRemaining = 0;
    }

    /**
     * Один тік життєвого циклу.
     *
     * @param clipActive  чи кліп ще грає ({@code KeyframeAnimationPlayer.isActive()})
     * @param ticksToStop скільки тіків лишилось до {@code stopTick} кліпа;
     *                    для нескінченного — {@link #UNBOUNDED}
     */
    public Decision tick(boolean clipActive, int ticksToStop) {
        switch (state) {
            case IDLE:
                return Decision.NONE;
            case FADING_OUT:
                fadeRemaining--;
                if (fadeRemaining <= 0) {
                    state = State.IDLE;
                    return Decision.FINISH;
                }
                return Decision.NONE;
            case PLAYING:
            default:
                if (!clipActive) {
                    // Кліп уже зупинився (або хтось його зупинив ззовні): fade
                    // додати вже не можна, тож знімаємо шар як є.
                    state = State.IDLE;
                    return Decision.FINISH;
                }
                if (infinite || fadeOutTicks <= 0) {
                    return Decision.NONE;
                }
                if (ticksToStop <= fadeOutTicks) {
                    return startFade(Math.max(1, ticksToStop));
                }
                return Decision.NONE;
        }
    }

    /** {@link #requestStop(boolean, int)} для кліпа, що грає й не має близького кінця. */
    public Decision requestStop() {
        return requestStop(true, UNBOUNDED);
    }

    /**
     * Консюмер просить зняти позу.
     *
     * @param clipActive  чи кліп ще грає
     * @param ticksToStop залишок до {@code stopTick} ({@link #UNBOUNDED} для нескінченного)
     */
    public Decision requestStop(boolean clipActive, int ticksToStop) {
        switch (state) {
            case IDLE:
            case FADING_OUT:
                return Decision.NONE;
            case PLAYING:
            default:
                if (fadeOutTicks <= 0 || !clipActive) {
                    state = State.IDLE;
                    return Decision.FINISH;
                }
                return startFade(Math.max(1, Math.min(fadeOutTicks, ticksToStop)));
        }
    }

    private Decision startFade(int length) {
        state = State.FADING_OUT;
        fadeLength = length;
        fadeRemaining = length;
        return Decision.START_FADE_OUT;
    }

    /** Жорстке скидання (смерть, logout): без fade, одразу {@code IDLE}. */
    public void reset() {
        state = State.IDLE;
        fadeLength = 0;
        fadeRemaining = 0;
    }

    public State state() {
        return state;
    }

    /**
     * Довжина fade-out, зафіксована в момент рішення {@link Decision#START_FADE_OUT}.
     * Не залежить від того, до чи після {@code tick} її запитали.
     */
    public int currentFadeLength() {
        return fadeLength;
    }

    /** Скільки тіків fade-out ще лишилось (для {@link State#FADING_OUT}; інакше 0). */
    public int fadeRemaining() {
        return state == State.FADING_OUT ? Math.max(0, fadeRemaining) : 0;
    }
}
