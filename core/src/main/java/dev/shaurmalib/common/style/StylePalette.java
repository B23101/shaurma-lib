package dev.shaurmalib.common.style;

/**
 * Кольорова палітра меню/HUD (план, п. 3.3) — перенесення констант
 * {@code OverlayStyle} snipers_shaurma (408 рядків, з яких константи й
 * колірна математика — Forge-незалежна частина, тому живе в
 * {@code lib-common}; сам рендер через {@code GuiGraphics} —
 * {@code dev.shaurmalib.forge.style.StyleTheme}).
 * <p>
 * Інстанс-based, не статичний клас констант, як в оригіналі — кожен
 * консюмер (і кожен режим у межах консюмера, якщо захоче) може мати
 * власну {@link StylePalette}, завантажену зі свого {@code style.yml}
 * через {@link #DEFAULT} як базові значення. Це напряму відповідає
 * плану (3.3): "{@code StyleTheme} — реєстр кольорів/шрифтів/іконок
 * одним YAML" — тут сама структура даних, завантаження з YAML —
 * відповідальність {@code dev.shaurmalib.common.config.YamlConfigSection}
 * (3.1), консюмер сам мапить прочитані значення в конструктор.
 */
public final class StylePalette {

    // ─── Базова палітра (перенесено 1:1 з OverlayStyle) ────────────────
    public final int bgBase;
    public final int bgMaxAlpha;
    public final int bg;

    public final int border;
    public final int borderLight;

    public final int accentNeutral;
    public final int accentCyan;
    public final int accentGreen;
    public final int accentRed;
    public final int accentOrange;
    public final int accentYellow;
    public final int accentWhite;
    public final int accentBlue;
    public final int accentPurple;

    public final int textMain;
    public final int textDim;
    public final int textGray;

    public final int rowHover;
    public final int rowSelected;

    public StylePalette(int bgBase, int bgMaxAlpha, int bg,
                         int border, int borderLight,
                         int accentNeutral, int accentCyan, int accentGreen, int accentRed,
                         int accentOrange, int accentYellow, int accentWhite, int accentBlue, int accentPurple,
                         int textMain, int textDim, int textGray,
                         int rowHover, int rowSelected) {
        this.bgBase = bgBase;
        this.bgMaxAlpha = bgMaxAlpha;
        this.bg = bg;
        this.border = border;
        this.borderLight = borderLight;
        this.accentNeutral = accentNeutral;
        this.accentCyan = accentCyan;
        this.accentGreen = accentGreen;
        this.accentRed = accentRed;
        this.accentOrange = accentOrange;
        this.accentYellow = accentYellow;
        this.accentWhite = accentWhite;
        this.accentBlue = accentBlue;
        this.accentPurple = accentPurple;
        this.textMain = textMain;
        this.textDim = textDim;
        this.textGray = textGray;
        this.rowHover = rowHover;
        this.rowSelected = rowSelected;
    }

    /**
     * Дефолтна палітра — точні значення з {@code OverlayStyle} snipers_shaurma
     * (та сама темна тема з ціан/зеленим/червоним акцентами). Консюмер, що
     * не хоче своєї теми, використовує це напряму; консюмер, що хоче іншу
     * "атмосферу" (наприклад режим-друга maniac), будує власний
     * {@link StylePalette} зі своїми значеннями того самого набору полів.
     */
    public static final StylePalette DEFAULT = new StylePalette(
            0x000000, 160, 0x90000000,
            0xFF2A2A2A, 0xFF3A3A3A,
            0x00000000, 0xFF26C6FF, 0xFF44E07A, 0xFFFF3344,
            0xFFFF8533, 0xFFFFD700, 0xFFFFFFFF, 0xFF3A7FD6, 0xFFCC44FF,
            0xFFE8EDF2, 0xFFA0B8CC, 0xFF999AAB,
            0xFF1A1A2A, 0xFF0A1525
    );

    // ─── Утиліти кольору (Forge-незалежні, чиста бітова арифметика) ────

    /** Застосовує alpha до кольору, зберігаючи його ВЛАСНИЙ alpha як базу (той самий {@code applyAlpha} з оригіналу). */
    public static int applyAlpha(int color, float targetAlpha) {
        int existingA = (color >> 24) & 0xFF;
        int newA = clamp((int) (existingA * targetAlpha), 0, 255);
        return (color & 0x00FFFFFF) | (newA << 24);
    }

    /** Застосовує явний int alpha (0..255) до кольору, ігноруючи попередній alpha-канал. */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (clamp(alpha, 0, 255) << 24);
    }

    /** Лінійна інтерполяція між двома ARGB кольорами. */
    public static int lerpColor(int a, int b, float t) {
        t = clamp(t, 0f, 1f);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >> 24) & 0xFF;
        return (((int) (aa + (ba - aa) * t)) << 24) | (((int) (ar + (br - ar) * t)) << 16)
                | (((int) (ag + (bg - ag) * t)) << 8) | ((int) (ab + (bb - ab) * t));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
