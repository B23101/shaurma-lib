package dev.shaurmalib.common.overlay;

/**
 * Тайминги одного циклу показу (slide-in / hold / fade-out), у мілісекундах.
 * <p>
 * В оригіналі snipers_shaurma це були приватні {@code static final int}
 * константи, захардкоджені окремо в кожному з 5+ overlay-класів
 * ({@code AlertNotificationOverlay.SHOW_MS=4000}, {@code MultiKillOverlay.HOLD_MS=1100}
 * і т.д.) — щоб змінити тривалість показу конкретного типу сповіщення,
 * треба було редагувати Java-код і перекомпільовувати мод. Тут це —
 * параметр, який мод передає в {@link TimedOverlayEntry}, отже його можна
 * тримати в {@code style.yml} (план, п. 3.3, {@code StyleTheme}) і міняти
 * без правки коду; той самий рушій обслуговує будь-яку комбінацію
 * тайминг-профілів одночасно (короткий toast і довгий SCN-алерт можуть
 * співіснувати в одному {@link OverlayFeed}).
 */
public final class OverlayTimings {

    private final int slideMs;
    private final int holdMs;
    private final int fadeMs;

    public OverlayTimings(int slideMs, int holdMs, int fadeMs) {
        this.slideMs = slideMs;
        this.holdMs = holdMs;
        this.fadeMs = fadeMs;
    }

    /** 1:1 значення оригінального {@code AlertNotificationOverlay} (SLIDE=320, SHOW=4000, FADE=260). */
    public static OverlayTimings alertDefault() {
        return new OverlayTimings(320, 4000, 260);
    }

    /** 1:1 значення оригінального {@code LootNotificationOverlay} — коротший показ для дрібних подій луту. */
    public static OverlayTimings lootDefault() {
        return new OverlayTimings(280, 2600, 240);
    }

    public int slideMs() {
        return slideMs;
    }

    /** "hold" тут означає "момент з якого починається fade" — сумісно з {@code SHOW_MS} в оригіналі. */
    public int holdMs() {
        return holdMs;
    }

    public int fadeMs() {
        return fadeMs;
    }

    public int totalMs() {
        return holdMs + fadeMs;
    }
}
