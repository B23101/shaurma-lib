package dev.shaurmalib.meta.match;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Що гравець зробив за матч. Режим заповнює звіт сам (він знає свої правила), бібліотека за ним
 * нараховує XP і монети, оновлює статистику й шле екран підсумків — один і той самий шлях для всіх режимів.
 *
 * <pre>{@code
 * MatchReport r = MatchReport.of(uuid, name)
 *     .track("maniac:deathwood").ladder("maniac").role("Маніяк").won(true)
 *     .action("complete", 1).action("win", 1).action("hook", 2).action("kill", 4)
 *     .stat("maniac.deathwood.downs", 3).best("maniac.deathwood.bestDowns", 3);
 * }</pre>
 */
public final class MatchReport {

    public final UUID player;
    public final String name;
    /** XP-доріжка: рівень спільний (сума доріжок), але XP пишеться в доріжку ролі. */
    public String track = "main";
    /** Яку драбину рангів показати на екрані підсумків. */
    public String ladder = "";
    /** Підпис ролі на екрані («Маніяк», «Шериф»). */
    public String role = "";
    public boolean won;
    public int durationSeconds;
    public final Map<String, Integer> actions = new LinkedHashMap<>();
    public final Map<String, Long> statAdd = new LinkedHashMap<>();
    public final Map<String, Long> statBest = new LinkedHashMap<>();
    /** Додаткові видачі поза правилами (івенти, бонуси режиму). */
    public final Map<String, Long> bonusCoins = new LinkedHashMap<>();
    /** Готові рядки XP від режиму (якщо режим рахує XP сам, за своїм конфігом). Додаються після правил. */
    public final java.util.List<MatchResult.Line> lines = new java.util.ArrayList<>();

    private MatchReport(UUID player, String name) {
        this.player = player;
        this.name = name == null ? "" : name;
    }

    public static MatchReport of(UUID player, String name) { return new MatchReport(player, name); }

    public MatchReport track(String t) { track = t == null || t.isEmpty() ? "main" : t; return this; }
    public MatchReport ladder(String l) { ladder = l == null ? "" : l; return this; }
    public MatchReport role(String r) { role = r == null ? "" : r; return this; }
    public MatchReport won(boolean w) { won = w; return this; }
    public MatchReport duration(int seconds) { durationSeconds = Math.max(0, seconds); return this; }

    public MatchReport action(String key, int count) {
        if (count > 0) actions.merge(key, count, Integer::sum);
        return this;
    }

    public MatchReport stat(String key, long delta) {
        if (delta != 0) statAdd.merge(key, delta, Long::sum);
        return this;
    }

    public MatchReport best(String key, long value) {
        statBest.merge(key, value, Math::max);
        return this;
    }

    public MatchReport bonus(String currency, long amount) {
        if (amount > 0) bonusCoins.merge(currency, amount, Long::sum);
        return this;
    }

    /** Готовий рядок «Назва ×count +xp» (поза правилами режиму). */
    public MatchReport line(String action, String name, String icon, int count, int xp) {
        if (xp > 0) lines.add(new MatchResult.Line(action, name, icon, count, xp));
        return this;
    }

    public int count(String action) { return actions.getOrDefault(action, 0); }
}
