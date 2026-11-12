package dev.shaurmalib.meta.progress;

/**
 * Крива рівнів. Рівень {@code L} (від 1) закривається, коли в ньому набрано {@code base + step·L} XP.
 * Загальний XP — одне число, рівень і прогрес у ньому — похідні (нічого зайвого не зберігається).
 * Маніяк: {@code new XpCurve(50, 250)} — рівень 1 = 300, 2 = 550, 3 = 800…
 */
public record XpCurve(int base, int step, int maxLevel) {

    public XpCurve {
        if (base < 0 || step < 0 || base + step <= 0) throw new IllegalArgumentException("XpCurve: base+step має бути > 0");
        if (maxLevel <= 0) maxLevel = 999;
    }

    public XpCurve(int base, int step) { this(base, step, 999); }

    /** XP, потрібний усередині рівня {@code level}, щоб перейти на наступний. */
    public int needed(int level) {
        return base + step * Math.max(1, level);
    }

    public int levelOf(long totalXp) {
        int level = 1;
        long left = Math.max(0L, totalXp);
        while (level < maxLevel && left >= needed(level)) { left -= needed(level); level++; }
        return level;
    }

    /** XP, уже набраний усередині поточного рівня. */
    public long intoLevel(long totalXp) {
        int level = 1;
        long left = Math.max(0L, totalXp);
        while (level < maxLevel && left >= needed(level)) { left -= needed(level); level++; }
        return left;
    }

    /** Сумарний XP, з якого починається рівень {@code level}. */
    public long startOf(int level) {
        long sum = 0;
        for (int l = 1; l < Math.min(level, maxLevel); l++) sum += needed(l);
        return sum;
    }

    /** 0..1 — заповнення смужки поточного рівня. */
    public float progress(long totalXp) {
        int lvl = levelOf(totalXp);
        if (lvl >= maxLevel) return 1f;
        return Math.min(1f, intoLevel(totalXp) / (float) needed(lvl));
    }
}
