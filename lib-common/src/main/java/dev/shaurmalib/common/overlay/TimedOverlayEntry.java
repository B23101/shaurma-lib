package dev.shaurmalib.common.overlay;

/**
 * Один запис у стекованому фіді сповіщень (план, п. 3.4) — узагальнення
 * {@code AlertNotificationOverlay.AlertEntry} + {@code LootNotificationOverlay}
 * (обидва в оригіналі мали майже ідентичний slide-in/hold/fade-out цикл,
 * що відрізнявся лише таймінгами і кольором акценту).
 * <p>
 * Клас навмисно НЕ знає нічого про {@code Component}, шрифт чи GuiGraphics —
 * лише текст (String, форматування — відповідальність рендер-шару лібу
 * або самого мода) і числові параметри анімації. Це дозволяє тестувати
 * {@link #alphaAt(long)} / {@link #slideProgressAt(long)} юніт-тестами без
 * піднятого Minecraft-клієнта.
 */
public final class TimedOverlayEntry {

    private final String text;
    private final int accentArgb;
    private final long createdAtMs;
    private final OverlayTimings timings;
    private final Object payload;

    public TimedOverlayEntry(String text, int accentArgb, OverlayTimings timings) {
        this(text, accentArgb, timings, System.currentTimeMillis());
    }

    /** Конструктор з явним {@code createdAtMs} — для юніт-тестів і CineRecording-подібного реплею. */
    public TimedOverlayEntry(String text, int accentArgb, OverlayTimings timings, long createdAtMs) {
        this(text, accentArgb, timings, createdAtMs, null);
    }

    /**
     * @param payload довільний об'єкт-корисне навантаження (напр.
     *                {@code ChatEntry} для чат-сплеша), який САМ клас не
     *                інтерпретує — лишається {@code Object}, щоб
     *                {@code lib-common} не тягнув Minecraft-залежність.
     *                Рендер-шар (lib-forge) сам вирішує, чи скасувати
     *                текстовий рендер і намалювати {@code payload} власною
     *                верствою (напр. {@code ChatEntryRenderer} — та сама
     *                голова гравця й колір ніку, що й у журналі чату).
     *                {@code null} — звичайний текстовий рендер.
     */
    public TimedOverlayEntry(String text, int accentArgb, OverlayTimings timings, long createdAtMs, Object payload) {
        this.text = text;
        this.accentArgb = accentArgb;
        this.timings = timings;
        this.createdAtMs = createdAtMs;
        this.payload = payload;
    }

    public String text() {
        return text;
    }

    public int accentArgb() {
        return accentArgb;
    }

    public long createdAtMs() {
        return createdAtMs;
    }

    /** {@code null} для звичайного текстового запису — див. конструктор. */
    public Object payload() {
        return payload;
    }

    public long ageMs(long nowMs) {
        return nowMs - createdAtMs;
    }

    /** Чи цей запис уже повністю відіграв свій цикл (slide+hold+fade, або раніший collapse) і має бути видалений зі стеку. */
    public boolean isExpired(long nowMs) {
        long age = ageMs(nowMs);
        if (timings.hasCollapsePhase()) {
            long collapseEnd = timings.collapseAfterMs() + timings.collapseMs();
            if (age > collapseEnd + 100) return true;
        }
        return age > timings.totalMs() + 100;
    }

    /**
     * Прогрес фази «опускання» (0..1), 0 якщо профіль не має collapse-фази
     * або вона ще не почалась. Використовується рендером для довгих
     * (обрізаних до кількох рядків) чат-сплешів: через
     * {@code collapseAfterMs} після появи запис повільно з'їжджає вниз і
     * тане, замість того щоб займати місце в стеку аж до звичайного
     * {@code holdMs}-fade. Незалежна від {@link #fadeOutProgressAt(long)} —
     * рендер сам вирішує, як комбінувати вертикальний зсув із цим прогресом.
     */
    public float collapseProgressAt(long nowMs) {
        if (!timings.hasCollapsePhase()) return 0f;
        long age = ageMs(nowMs);
        if (age <= timings.collapseAfterMs()) return 0f;
        float t = (float) (age - timings.collapseAfterMs()) / timings.collapseMs();
        return Easing.easeInQuad(Math.min(1f, Math.max(0f, t)));
    }

    /**
     * Прогрес входу (0..1), eased через {@link Easing#easeOutBack(float)} —
     * як в оригінальному {@code AlertNotificationOverlay} (slide справа +
     * легкий перебіг).
     */
    public float slideProgressAt(long nowMs) {
        long age = ageMs(nowMs);
        float t = Math.min(1f, (float) age / timings.slideMs());
        return Easing.easeOutBack(t);
    }

    /**
     * Комбінована альфа (0..1) на цей момент часу — злиття slide-in-альфи
     * і fade-out-альфи, 1:1 формула з {@code AlertNotificationOverlay.render}:
     * перша половина slide-фази керує альфою через easeOutBack, після
     * {@code holdMs} починається fade через easeInQuad.
     */
    public float alphaAt(long nowMs) {
        long age = ageMs(nowMs);
        float slideT = Math.min(1f, (float) age / timings.slideMs());
        float slideEased = Easing.easeOutBack(slideT);
        float fadeT = age > timings.holdMs()
                ? Math.min(1f, (float) (age - timings.holdMs()) / timings.fadeMs())
                : 0f;
        float alpha = age < timings.slideMs() * 0.5f
                ? slideEased
                : (1f - Easing.easeInQuad(fadeT));
        alpha *= (1f - collapseProgressAt(nowMs));
        return Math.max(0f, Math.min(1f, alpha));
    }

    /** Множник для fade-фази окремо (потрібен рендеру, щоб згасити й горизонтальний slide-back одночасно). */
    public float fadeOutProgressAt(long nowMs) {
        long age = ageMs(nowMs);
        if (age <= timings.holdMs()) return 0f;
        return Easing.easeInQuad(Math.min(1f, (float) (age - timings.holdMs()) / timings.fadeMs()));
    }

    public OverlayTimings timings() {
        return timings;
    }
}
