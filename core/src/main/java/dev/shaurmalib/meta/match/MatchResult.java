package dev.shaurmalib.meta.match;

import dev.shaurmalib.meta.economy.Reward;
import dev.shaurmalib.meta.io.J;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Готовий підсумок для екрана «Матч завершено». Сервер рахує його ОДИН раз і записує ті самі цифри,
 * що бачить клієнт, — екран і профіль не можуть розійтись.
 */
public final class MatchResult {

    /** Рядок «XP за дії»: іконка, назва, ×кількість, +XP. */
    public record Line(String action, String name, String icon, int count, int xp) {}

    /** Рядок монет: «+40 DarkCoins · Вбивства 4 × 10». */
    public record Coins(String currency, long amount, String note) {}

    public String mode = "";
    public boolean won;
    public String role = "";
    public String ladder = "";
    public int durationSeconds;
    public final List<Line> lines = new ArrayList<>();
    public final List<Coins> coins = new ArrayList<>();
    public long xpBefore, xpAfter;
    public int levelBefore, levelAfter;
    public int upgradePoints;
    /** Нові відкриті рівні доріжки (ще не забрані). */
    public final List<Integer> unlockedTrack = new ArrayList<>();
    /** Що видано автоматично за матч (деталі з шансом, івентові предмети). */
    public final List<Reward> drops = new ArrayList<>();

    public int xpGained() { return (int) Math.max(0, xpAfter - xpBefore); }

    public long coinsOf(String currency) {
        long n = 0;
        for (Coins c : coins) if (c.currency().equals(currency)) n += c.amount();
        return n;
    }

    public Map<String, Object> toJson() {
        List<Object> l = new ArrayList<>();
        for (Line x : lines) l.add(J.o("a", x.action(), "n", x.name(), "i", x.icon(), "c", x.count(), "x", x.xp()));
        List<Object> c = new ArrayList<>();
        for (Coins x : coins) c.add(J.o("c", x.currency(), "a", x.amount(), "n", x.note()));
        List<Object> d = new ArrayList<>();
        for (Reward r : drops) d.add(r.toJson());
        return J.o("mode", mode, "won", won, "role", role, "ladder", ladder, "dur", durationSeconds,
                "lines", l, "coins", c, "xb", xpBefore, "xa", xpAfter, "lb", levelBefore, "la", levelAfter,
                "up", upgradePoints, "track", new ArrayList<Object>(unlockedTrack), "drops", d);
    }

    public static MatchResult fromJson(Map<String, Object> o) {
        MatchResult r = new MatchResult();
        r.mode = J.str(o, "mode", "");
        r.won = J.bool(o, "won", false);
        r.role = J.str(o, "role", "");
        r.ladder = J.str(o, "ladder", "");
        r.durationSeconds = J.i(o, "dur", 0);
        for (Object x : J.arr(o, "lines")) {
            Map<String, Object> m = J.asObj(x);
            r.lines.add(new Line(J.str(m, "a", ""), J.str(m, "n", ""), J.str(m, "i", ""), J.i(m, "c", 0), J.i(m, "x", 0)));
        }
        for (Object x : J.arr(o, "coins")) {
            Map<String, Object> m = J.asObj(x);
            r.coins.add(new Coins(J.str(m, "c", ""), J.num(m, "a", 0), J.str(m, "n", "")));
        }
        for (Object x : J.arr(o, "drops")) r.drops.add(Reward.fromJson(J.asObj(x)));
        r.xpBefore = J.num(o, "xb", 0);
        r.xpAfter = J.num(o, "xa", 0);
        r.levelBefore = J.i(o, "lb", 1);
        r.levelAfter = J.i(o, "la", 1);
        r.upgradePoints = J.i(o, "up", 0);
        for (Object x : J.arr(o, "track")) if (x instanceof Number n) r.unlockedTrack.add(n.intValue());
        return r;
    }
}
