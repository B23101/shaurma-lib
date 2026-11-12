package dev.shaurmalib.meta;

/**
 * Власний стиль меню режиму: усі екрани бібліотеки (гардероб, профіль, підсумки, магазин, лідерборди,
 * доріжка) малюються з цих кольорів — код один, вигляд у кожного режиму свій.
 * Кольори — RGB без альфи.
 *
 * @param decor оздоблення кутів і фону: {@link Decor#WEBS} — павутиння й гарбузи (Хеловін), {@link Decor#GLASS} — темне скло з прожектором
 */
public record MetaTheme(int accent, int accent2, int bg, int panel, int panelHi, int border,
                        int text, int dim, int gold, int good, int bad, Decor decor, boolean sharp) {

    public enum Decor { NONE, GLASS, WEBS }

    /** Мафія: темне скло, фіолетові/золоті акценти (стиль колишнього гардероба мафії). */
    public static MetaTheme glass() {
        return new MetaTheme(0xA77BFF, 0xFFC83D, 0x0A0A10, 0x14141C, 0x1E1E2A, 0x3A3A4C,
                0xF2F2F7, 0x9A9AAE, 0xFFC83D, 0x3DDC84, 0xE5383B, Decor.GLASS, false);
    }

    /** Маніяк: теплі дерев'яно-помаранчеві панелі з павутинням (як на скріні підсумків). */
    public static MetaTheme halloween() {
        return new MetaTheme(0xF08A24, 0xFFC36B, 0x140B06, 0x2A170C, 0x3A2212, 0x6B3F1E,
                0xF6E6D2, 0xB89A7A, 0xFFC36B, 0x7CD36B, 0xE04A3A, Decor.WEBS, true);
    }

    public MetaTheme withAccent(int rgb) {
        return new MetaTheme(rgb, accent2, bg, panel, panelHi, border, text, dim, gold, good, bad, decor, sharp);
    }
}
