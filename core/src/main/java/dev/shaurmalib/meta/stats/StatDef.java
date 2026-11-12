package dev.shaurmalib.meta.stats;

/**
 * Опис статистики режиму: як її називати, малювати й агрегувати.
 *
 * @param key         ключ у {@link StatBook}, напр. {@code "survivor.escapes"}, {@code "mafia.kills"}
 * @param group       розділ у профілі («Виживий», «Мафія», «Загальне»)
 * @param icon        id іконки для {@code MetaIcons} (heart, skull, hook, star, clock, trophy, coin, eye, ...)
 * @param kind        як показувати число
 * @param leaderboard чи є окремий топ за цією статистикою
 */
public record StatDef(String key, String name, String group, String icon, Kind kind, boolean leaderboard) {

    public enum Kind {
        /** Звичайний лічильник. */
        COUNT,
        /** Секунди → «1 год 5 хв». */
        SECONDS,
        /** Найкращий результат (у книзі пишеться через {@link StatBook#max}). */
        BEST,
        /** Відсоток із двох ключів: {@code key} = "a/b" (перемоги/матчі). Сам не зберігається. */
        RATIO
    }

    public static StatDef count(String key, String name, String group, String icon) {
        return new StatDef(key, name, group, icon, Kind.COUNT, false);
    }

    public StatDef ranked() { return new StatDef(key, name, group, icon, kind, true); }

    /** Значення для показу (для RATIO — у відсотках 0..100). */
    public long value(StatBook book) {
        if (kind == Kind.RATIO) {
            int slash = key.indexOf('/');
            if (slash < 0) return 0;
            long a = book.get(key.substring(0, slash)), b = book.get(key.substring(slash + 1));
            return b <= 0 ? 0 : Math.round(a * 100.0 / b);
        }
        return book.get(key);
    }

    public String format(long v) {
        return switch (kind) {
            case SECONDS -> formatSeconds(v);
            case RATIO -> v + "%";
            default -> String.valueOf(v);
        };
    }

    public static String formatSeconds(long s) {
        if (s < 60) return s + " с";
        long m = s / 60;
        if (m < 60) return m + " хв " + (s % 60) + " с";
        return (m / 60) + " год " + (m % 60) + " хв";
    }
}
