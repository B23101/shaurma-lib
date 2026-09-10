package dev.shaurmalib.common.rank;

/**
 * PlayerRankEngine (план, п. 3.21) — узагальнення {@code PlayerRankSystem}
 * snipers_shaurma (93 рядки). Оригінал мав 51 захардкоджений
 * {@code Rank} (від "Rookie" до "Legend" з конкретними translation
 * key'ями {@code rank.snipers_shaurma.xxx} і кольорами) прямо в тілі
 * класу — це продуктова специфіка конкретної гри, тому в бібліотеку
 * переноситься лише РУШІЙ обчислення (поточний ранг/наступний
 * ранг/прогрес за кількістю очок), а масив рангів консюмер передає
 * сам через {@link Rank}.
 * <p>
 * Контракт з оригіналом лишається той самий: {@link Rank}, переданий у
 * {@link #of(Rank[])}, має бути відсортований за зростанням
 * {@code requiredPoints()}, і {@code ranks[0].requiredPoints()}
 * зазвичай {@code 0} (базовий ранг, який отримує кожен гравець одразу).
 * <p>
 * Приклад використання (snipers_shaurma після міграції):
 * <pre>{@code
 * private static final PlayerRankEngine RANKS = PlayerRankEngine.of(new Rank[] {
 *     new Rank(0, "rank.snipers_shaurma.rookie", 0, "§7"),
 *     new Rank(1, "rank.snipers_shaurma.private_1", 100, "§7"),
 *     // ... решта 49 рангів, дослівно перенесені з оригіналу
 * });
 * }</pre>
 */
public final class PlayerRankEngine {

    /** Один ранг: рівень, ключ перекладу назви, поріг очок, форматуючий колір-код (наприклад "§6"). */
    public record Rank(int level, String translationKey, int requiredPoints, String color) {}

    private final Rank[] ranks;

    private PlayerRankEngine(Rank[] ranks) {
        this.ranks = ranks;
    }

    /** @param ranks відсортований за зростанням {@code requiredPoints()} масив, {@code ranks[0]} — базовий ранг. */
    public static PlayerRankEngine of(Rank[] ranks) {
        if (ranks == null || ranks.length == 0) {
            throw new IllegalArgumentException("PlayerRankEngine потребує хоча б одного Rank (базового, requiredPoints=0)");
        }
        return new PlayerRankEngine(ranks);
    }

    /** Поточний ранг гравця з {@code points} очок — найвищий ранг, чий поріг досягнуто. */
    public Rank getRank(int points) {
        Rank current = ranks[0];
        for (Rank r : ranks) {
            if (points >= r.requiredPoints()) current = r;
            else break;
        }
        return current;
    }

    /** Наступний ранг (для прогрес-бару "до наступного рівня") — повертає найвищий ранг, якщо гравець уже на максимумі. */
    public Rank getNextRank(int points) {
        for (int i = 1; i < ranks.length; i++) {
            if (points < ranks[i].requiredPoints()) return ranks[i];
        }
        return ranks[ranks.length - 1];
    }

    public Rank[] getAllRanks() {
        return ranks.clone();
    }

    /** Прогрес до наступного рангу, 0.0..1.0. Повертає 1.0, якщо гравець уже на максимальному ранзі. */
    public float getProgress(int points) {
        Rank cur = getRank(points);
        Rank next = getNextRank(points);
        if (cur.level() == next.level()) return 1f;
        int range = next.requiredPoints() - cur.requiredPoints();
        int done = points - cur.requiredPoints();
        return range <= 0 ? 1f : Math.min(1f, (float) done / range);
    }
}
