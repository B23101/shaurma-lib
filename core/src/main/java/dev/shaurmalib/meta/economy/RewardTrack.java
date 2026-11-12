package dev.shaurmalib.meta.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Сезонний пропуск (безкоштовний, без платної гілки): ступені з нагородами, що відкриваються сезонним XP.
 * Кожен XP за матч іде і в рівень гравця, і в пропуск. Коли {@link #id()} сезону змінюється, прогрес пропуску
 * й забрані ступені в профілі обнуляються самі — рівень гравця лишається.
 *
 * <p>Якщо {@code xpPerTier == 0}, ступенем вважається рівень гравця (вічна доріжка без сезонів).
 * Забирання — кнопкою з анімацією, а не мовчки, щоб отримання відчувалось.</p>
 */
public final class RewardTrack {

    private final TreeMap<Integer, List<Reward>> steps = new TreeMap<>();
    private String id = "main";
    private String title = "Доріжка нагород";
    private long endsAt;
    private int xpPerTier;
    private final List<Reward> daily = new ArrayList<>();
    private long dailyCooldownMs = 20 * 3_600_000L;

    /** @param endsAt кінець сезону, мс epoch (0 — без кінця) */
    public RewardTrack season(String id, String title, long endsAt, int xpPerTier) {
        this.id = id;
        this.title = title;
        this.endsAt = endsAt;
        this.xpPerTier = Math.max(0, xpPerTier);
        return this;
    }

    /** Щоденний подарунок (кнопка 🎁 на початку пропуску); кулдаун за замовчуванням 20 год. */
    public RewardTrack daily(long cooldownMs, Reward... rewards) {
        daily.addAll(List.of(rewards));
        dailyCooldownMs = Math.max(60_000L, cooldownMs);
        return this;
    }

    public RewardTrack at(int tier, Reward... rewards) {
        steps.computeIfAbsent(tier, k -> new ArrayList<>()).addAll(List.of(rewards));
        return this;
    }

    public String id() { return id; }
    public String title() { return title; }
    public long endsAt() { return endsAt; }
    public int xpPerTier() { return xpPerTier; }
    public boolean seasonal() { return xpPerTier > 0; }
    public List<Reward> daily() { return daily; }
    public long dailyCooldownMs() { return dailyCooldownMs; }
    public int maxTier() { return steps.isEmpty() ? 0 : steps.lastKey(); }

    /** Ступінь пропуску: сезонний XP / xpPerTier + 1, або рівень гравця. */
    public int tier(long seasonXp, int playerLevel) {
        if (!seasonal()) return playerLevel;
        return (int) Math.min(Math.max(1, maxTier()), 1 + seasonXp / xpPerTier);
    }

    /** 0..1 — заповнення до наступного ступеня. */
    public float tierProgress(long seasonXp) {
        if (!seasonal()) return 0;
        if (tier(seasonXp, 1) >= maxTier()) return 1f;
        return (seasonXp % xpPerTier) / (float) xpPerTier;
    }

    public List<Reward> rewards(int tier) { return steps.getOrDefault(tier, List.of()); }

    public Map<Integer, List<Reward>> steps() { return java.util.Collections.unmodifiableMap(steps); }

    /** Ступені, що вже відкриті, але ще не забрані. */
    public List<Integer> claimable(int tier, java.util.Set<Integer> claimed) {
        List<Integer> out = new ArrayList<>();
        for (int l : steps.headMap(tier, true).keySet()) if (!claimed.contains(l)) out.add(l);
        return out;
    }

    public boolean isEmpty() { return steps.isEmpty(); }
}
