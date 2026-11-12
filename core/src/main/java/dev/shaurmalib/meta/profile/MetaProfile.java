package dev.shaurmalib.meta.profile;

import dev.shaurmalib.meta.MetaMode;
import dev.shaurmalib.meta.cosmetics.Wardrobe;
import dev.shaurmalib.meta.economy.Wallet;
import dev.shaurmalib.meta.io.J;
import dev.shaurmalib.meta.stats.StatBook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Профіль гравця в одному режимі — єдине джерело правди про прогрес, гроші, косметику й статистику.
 * Рівень і ранг — похідні від {@link #totalXp()} і кривої режиму, тож не зберігаються.
 *
 * <p>XP ведеться доріжками ({@code "survivor"}, {@code "maniac:deathwood"}, {@code "main"}) — так режим
 * бачить, за кого гравець грав найбільше, — а рівень один на суму доріжок.</p>
 */
public final class MetaProfile {

    public static final int VERSION = 1;

    public final UUID id;
    public String name = "";
    /** base64-властивість {@code textures} — щоб малювати бюст офлайн-гравця в лідерборді. */
    public String skin = "";
    public long lastSeen;
    public long firstSeen;

    public final Map<String, Long> xp = new LinkedHashMap<>();
    public final StatBook stats = new StatBook();
    public final Wallet wallet = new Wallet();
    public final Map<String, Integer> materials = new LinkedHashMap<>();
    public final Set<String> owned = new LinkedHashSet<>();
    /** Предмети, отримані, але ще не переглянуті в гардеробі (позначка «НОВЕ»). */
    public final Set<String> unseen = new LinkedHashSet<>();
    public Wardrobe wardrobe = new Wardrobe();
    public final Set<Integer> claimed = new TreeSet<>();
    public int upgradePoints;
    public final Map<String, Integer> upgrades = new LinkedHashMap<>();
    /** Нерозкриті скрині: id → кількість. */
    public final Map<String, Integer> chests = new LinkedHashMap<>();
    /** Сезон пропуску, до якого належать {@link #seasonXp} і {@link #claimed}. */
    public String season = "";
    public long seasonXp;
    public long lastDaily;
    /** Позначки одноразових міграцій зі старих форматів режиму. */
    public final Set<String> flags = new LinkedHashSet<>();

    public MetaProfile(UUID id) { this.id = id; }

    public long totalXp() {
        long s = 0;
        for (long v : xp.values()) s += v;
        return s;
    }

    public long xp(String track) { return xp.getOrDefault(track, 0L); }

    public void addXp(String track, long amount) {
        if (amount > 0) xp.merge(track, amount, Long::sum);
    }

    public int level(MetaMode mode) { return mode.curve.levelOf(totalXp()); }

    public int material(String id) { return materials.getOrDefault(id, 0); }

    public void addMaterial(String id, int n) {
        int v = Math.max(0, material(id) + n);
        if (v == 0) materials.remove(id); else materials.put(id, v);
    }

    public boolean owns(String cosmetic) { return owned.contains(cosmetic); }

    public int chest(String id) { return chests.getOrDefault(id, 0); }

    public void addChest(String id, int n) {
        int v = Math.max(0, chest(id) + n);
        if (v == 0) chests.remove(id); else chests.put(id, v);
    }

    /** Новий сезон пропуску → прогрес і забрані ступені з нуля. @return true, якщо скинуто */
    public boolean syncSeason(String id) {
        if (id.equals(season)) return false;
        season = id;
        seasonXp = 0;
        claimed.clear();
        return true;
    }

    public int upgrade(String id) { return upgrades.getOrDefault(id, 0); }

    /** Чи було в гравця хоч щось — порожні профілі не засмічують лідерборди. */
    public boolean hasActivity() { return totalXp() > 0 || !stats.isEmpty(); }

    // ── JSON (файл і мережа — один формат) ───────────────────────────────

    public Map<String, Object> toJson(boolean full) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("v", VERSION);
        o.put("id", id.toString());
        o.put("name", name);
        if (full) {
            o.put("skin", skin);
            o.put("firstSeen", firstSeen);
            o.put("flags", new ArrayList<Object>(flags));
        }
        o.put("lastSeen", lastSeen);
        o.put("xp", new LinkedHashMap<String, Object>(xp));
        o.put("stats", new LinkedHashMap<String, Object>(stats.view()));
        o.put("wallet", new LinkedHashMap<String, Object>(wallet.view()));
        o.put("materials", new LinkedHashMap<String, Object>(materials));
        o.put("owned", new ArrayList<Object>(owned));
        o.put("unseen", new ArrayList<Object>(unseen));
        o.put("wardrobe", wardrobe.toJson());
        o.put("claimed", new ArrayList<Object>(claimed));
        o.put("points", upgradePoints);
        o.put("upgrades", new LinkedHashMap<String, Object>(upgrades));
        o.put("chests", new LinkedHashMap<String, Object>(chests));
        o.put("season", season);
        o.put("seasonXp", seasonXp);
        o.put("lastDaily", lastDaily);
        return o;
    }

    public static MetaProfile fromJson(UUID fallbackId, Map<String, Object> o) {
        UUID id = fallbackId;
        try { id = UUID.fromString(J.str(o, "id", String.valueOf(fallbackId))); } catch (IllegalArgumentException ignored) { }
        MetaProfile p = new MetaProfile(id);
        p.name = J.str(o, "name", "");
        p.skin = J.str(o, "skin", "");
        p.lastSeen = J.num(o, "lastSeen", 0);
        p.firstSeen = J.num(o, "firstSeen", 0);
        J.obj(o, "xp").forEach((k, v) -> { if (v instanceof Number n && n.longValue() > 0) p.xp.put(k, n.longValue()); });
        J.obj(o, "stats").forEach((k, v) -> { if (v instanceof Number n) p.stats.set(k, n.longValue()); });
        J.obj(o, "wallet").forEach((k, v) -> { if (v instanceof Number n) p.wallet.set(k, n.longValue()); });
        J.obj(o, "materials").forEach((k, v) -> { if (v instanceof Number n && n.intValue() > 0) p.materials.put(k, n.intValue()); });
        for (Object x : J.arr(o, "owned")) if (x instanceof String s) p.owned.add(s);
        for (Object x : J.arr(o, "unseen")) if (x instanceof String s) p.unseen.add(s);
        for (Object x : J.arr(o, "flags")) if (x instanceof String s) p.flags.add(s);
        p.wardrobe = Wardrobe.fromJson(J.obj(o, "wardrobe"));
        for (Object x : J.arr(o, "claimed")) if (x instanceof Number n) p.claimed.add(n.intValue());
        p.upgradePoints = Math.max(0, J.i(o, "points", 0));
        J.obj(o, "upgrades").forEach((k, v) -> { if (v instanceof Number n && n.intValue() > 0) p.upgrades.put(k, n.intValue()); });
        J.obj(o, "chests").forEach((k, v) -> { if (v instanceof Number n && n.intValue() > 0) p.chests.put(k, n.intValue()); });
        p.season = J.str(o, "season", "");
        p.seasonXp = Math.max(0, J.num(o, "seasonXp", 0));
        p.lastDaily = J.num(o, "lastDaily", 0);
        return p;
    }

    public MetaProfile copy() { return fromJson(id, J.parse(J.write(toJson(true)))); }

    /** Рядки статистики, що варто показати (непорожні). */
    public List<String> statKeys() { return new ArrayList<>(stats.view().keySet()); }
}
