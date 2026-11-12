package dev.shaurmalib.meta.progress;

import java.util.ArrayList;
import java.util.List;

/**
 * Драбина рангів однієї групи (наприклад «виживий» і «маніяк» у маніяку, «мирні» й «мафія» в мафії).
 * Ранг — похідна від рівня, окремо не зберігається. Ступінь (tier) ділиться на дивізіони I–V;
 * найвищий ступінь безмежний.
 */
public final class RankLadder {

    public record Tier(String id, String name, int minLevel, int color) {}

    /** @param division 1..divisions; для найвищого ступеня завжди 1 */
    public record Rank(String ladder, int tier, String id, String name, int division, boolean top, int color) {
        public String roman() {
            if (top) return "";
            return switch (division) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; default -> "V"; };
        }
        public String title() { return top ? name : name + " " + roman(); }
    }

    private final String id;
    private final String name;
    private final List<Tier> tiers;
    private final int divisions;

    public RankLadder(String id, String name, List<Tier> tiers, int divisions) {
        if (tiers.isEmpty()) throw new IllegalArgumentException("RankLadder без ступенів");
        List<Tier> sorted = new ArrayList<>(tiers);
        sorted.sort((a, b) -> Integer.compare(a.minLevel(), b.minLevel()));
        this.id = id;
        this.name = name;
        this.tiers = List.copyOf(sorted);
        this.divisions = Math.max(1, divisions);
    }

    public String id() { return id; }
    public String name() { return name; }
    public List<Tier> tiers() { return tiers; }
    public int divisions() { return divisions; }

    public Rank ofLevel(int level) {
        int lvl = Math.max(1, level);
        int t = 0;
        for (int i = 0; i < tiers.size(); i++) if (lvl >= tiers.get(i).minLevel()) t = i;
        Tier tier = tiers.get(t);
        if (t == tiers.size() - 1) return new Rank(id, t, tier.id(), tier.name(), 1, true, tier.color());
        int width = Math.max(1, tiers.get(t + 1).minLevel() - tier.minLevel());
        int into = lvl - tier.minLevel();
        int div = Math.min(divisions, 1 + into * divisions / width);
        return new Rank(id, t, tier.id(), tier.name(), div, false, tier.color());
    }

    /** Рівень, з якого починається наступний ступінь; -1 для найвищого. */
    public int nextTierLevel(int level) {
        Rank r = ofLevel(level);
        return r.top() ? -1 : tiers.get(r.tier() + 1).minLevel();
    }

    /** Сім ступенів на рівнях 1/5/10/15/20/30/40 — як у маніяку. */
    public static RankLadder classic(String id, String name, String[] ids, String[] names, int[] colors) {
        int[] min = {1, 5, 10, 15, 20, 30, 40};
        List<Tier> list = new ArrayList<>();
        for (int i = 0; i < Math.min(ids.length, min.length); i++) {
            list.add(new Tier(ids[i], names[i], min[i], colors[Math.min(i, colors.length - 1)]));
        }
        return new RankLadder(id, name, list, 5);
    }
}
