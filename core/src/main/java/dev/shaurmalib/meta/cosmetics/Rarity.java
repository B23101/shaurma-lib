package dev.shaurmalib.meta.cosmetics;

/** Рідкість предмета: колір рамки картки й сила ефекту при отриманні. */
public enum Rarity {
    COMMON("Звичайний", 0x9A9AAE),
    RARE("Рідкісний", 0x4DA3FF),
    EPIC("Епічний", 0xA77BFF),
    LEGENDARY("Легендарний", 0xFFC83D),
    MYTHIC("Міфічний", 0xE5383B);

    public final String title;
    public final int color;

    Rarity(String title, int color) {
        this.title = title;
        this.color = color;
    }

    public static Rarity byName(String s) {
        for (Rarity r : values()) if (r.name().equalsIgnoreCase(s)) return r;
        return COMMON;
    }
}
