package dev.shaurmalib.common.overlay;

/**
 * Дефолтна анімація появи екрана/меню (план, п. 3.3, {@code MenuStyleTheme})
 * — перенесення реального паттерну з {@code GameSettingsScreen.animProgress}:
 * НЕ time-based (на відміну від {@link TimedOverlayEntry}), а
 * frame-delta накопичення в {@code render(g, mx, my, delta)}:
 * <pre>{@code animProgress = Math.min(1f, animProgress + delta * 0.1f);}</pre>
 * тобто швидкість появи залежить від частоти кадрів консистентно (той
 * самий {@code delta}, який Minecraft передає в {@code Screen.render}),
 * а не від абсолютного часу — свідомий вибір оригіналу, який рушій
 * лібу відтворює 1:1, а не переводить на {@code System.currentTimeMillis()}.
 * <p>
 * У оригіналі це поле копіювалось у кожен новий Screen-клас окремо
 * ({@code GameSettingsScreen}, і, за тим самим шаблоном, будь-який новий
 * екран мода) разом з {@code pulseTime} для hover-пульсації — тут це один
 * клас, інстанс якого кожен екран (мода-споживача, у {@code ModeSettingsScreen}
 * і в будь-якому кастомному {@code Screen}) створює собі один раз у
 * {@code init()}.
 * <p>
 * {@code speed} параметризований (в оригіналі захардкоджений {@code 0.1f})
 * — консюмер, якому потрібна швидша/повільніша появу власного екрана, не
 * форкає клас, а передає інше число.
 */
public final class ScreenOpenAnimator {

    private final float speed;
    private float progress = 0f;
    private float pulseTime = 0f;

    public ScreenOpenAnimator(float speed) {
        this.speed = speed;
    }

    /** 1:1 швидкість оригінального {@code GameSettingsScreen} (0.1f на кожен frame-delta). */
    public static ScreenOpenAnimator defaultSpeed() {
        return new ScreenOpenAnimator(0.1f);
    }

    /**
     * Викликати з {@code Screen.render(g, mx, my, delta)} на кожному кадрі —
     * 1:1 формула {@code animProgress = min(1, animProgress + delta * speed)}.
     */
    public void tick(float delta) {
        progress = Math.min(1f, progress + delta * speed);
        pulseTime += delta * 0.05f;
    }

    /** Сирий прогрес 0..1 (лінійний) — потрібен деяким віджетам оригіналу напряму, без easing (напр. заблокований стан). */
    public float rawProgress() {
        return progress;
    }

    /** Прогрес пройдений через {@link Easing#easeOutCubic(float)} — той самий, що йде на фон-панель і альфу тексту. */
    public float easedProgress() {
        return Easing.easeOutCubic(progress);
    }

    /** Альфа 0..255 для фонової панелі — 1:1 {@code (int)(easeOutCubic(animProgress) * 255)}. */
    public int panelAlpha255() {
        return (int) (easedProgress() * 255);
    }

    /** Синусоїдна пульсація hover-стану (0..1..0), що йде незалежно від появи екрана — {@code pulseTime} з оригіналу. */
    public float hoverPulse() {
        return (float) (0.5 + 0.5 * Math.sin(pulseTime * Math.PI * 2));
    }

    public boolean isFullyOpen() {
        return progress >= 1f;
    }

    /** Форсує мінімальний прогрес (в оригіналі — {@code animProgress = max(animProgress, 0.1f)} після певної дії, щоб уникнути "чорного миготіння"). */
    public void ensureMinimum(float minProgress) {
        progress = Math.max(progress, minProgress);
    }

    public void reset() {
        progress = 0f;
        pulseTime = 0f;
    }
}
