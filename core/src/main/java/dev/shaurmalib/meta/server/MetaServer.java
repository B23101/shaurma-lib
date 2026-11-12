package dev.shaurmalib.meta.server;

import dev.shaurmalib.meta.MetaMode;
import dev.shaurmalib.meta.cosmetics.CosmeticDef;
import dev.shaurmalib.meta.cosmetics.Look;
import dev.shaurmalib.meta.cosmetics.Wardrobe;
import dev.shaurmalib.meta.cosmetics.Rarity;
import dev.shaurmalib.meta.economy.ChestDef;
import dev.shaurmalib.meta.economy.MaterialDef;
import dev.shaurmalib.meta.economy.ShopSection;
import dev.shaurmalib.meta.economy.Recipe;
import dev.shaurmalib.meta.economy.Reward;
import dev.shaurmalib.meta.economy.ShopOffer;
import dev.shaurmalib.meta.economy.UpgradeDef;
import dev.shaurmalib.meta.hud.Waypoint;
import dev.shaurmalib.meta.io.J;
import dev.shaurmalib.meta.match.MatchReport;
import dev.shaurmalib.meta.match.MatchResult;
import dev.shaurmalib.meta.match.RewardRule;
import dev.shaurmalib.meta.net.MetaMessage;
import dev.shaurmalib.meta.net.MetaMessage.Op;
import dev.shaurmalib.meta.net.MetaNetwork;
import dev.shaurmalib.meta.profile.MetaProfile;
import dev.shaurmalib.meta.profile.ProfileStore;
import dev.shaurmalib.meta.stats.StatDef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Серверний рушій мета-гри одного режиму. ЄДИНЕ місце, що змінює профілі: нарахування за матч, магазин,
 * крафт, доріжка, покращення, гардероб, адмін-видачі. Клієнт лише просить (C2S) і отримує копію.
 * Викликається з головного потоку сервера.
 *
 * <p>Підключення режиму: {@code MetaServer.start(mode, platform)} на старті сервера, далі
 * {@link #onJoin}/{@link #onLeave} з подій входу і {@link #finishMatch} наприкінці матчу.</p>
 */
public final class MetaServer {

    private static volatile MetaServer instance;

    public final MetaMode mode;
    private final MetaPlatform platform;
    private final ProfileStore store;
    private final Map<UUID, MetaProfile> online = new LinkedHashMap<>();
    private final Map<UUID, String> variant = new HashMap<>();
    private final Map<String, Board> boards = new HashMap<>();
    private final Random rnd = new Random();
    /** Політика перегляду чужих профілів (режим може заборонити, наприклад, посеред матчу). */
    private Predicate<UUID> profileViewAllowed = viewer -> true;
    /** Гравець, якому варто сховати косметику (привид, глядач) — рішення режиму, а не лише рендера. */
    private Predicate<UUID> cosmeticsHidden = p -> false;
    private final List<Consumer<MetaProfile>> joinHooks = new ArrayList<>();

    private record Board(long at, List<Object> rows) {}

    /** Стрічка останніх цінних випадінь (живий рядок над магазином). */
    private final java.util.ArrayDeque<Object> feed = new java.util.ArrayDeque<>();
    public static final int FEED_SIZE = 24;

    private MetaServer(MetaMode mode, MetaPlatform platform) {
        this.mode = mode;
        this.platform = platform;
        this.store = new ProfileStore(() -> platform.dataDir().resolve(mode.id));
    }

    private static final List<Consumer<MetaServer>> START_HOOKS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Режим налаштовує сервер тут (політики, міграції) — викликається на кожному старті, тож порядок
     * подій лоадера між модом режиму й бібліотекою не важить. Якщо сервер уже працює — викликається одразу.
     */
    public static void whenStarted(Consumer<MetaServer> hook) {
        START_HOOKS.add(hook);
        MetaServer s = instance;
        if (s != null) hook.accept(s);
    }

    public static MetaServer start(MetaMode mode, MetaPlatform platform) {
        instance = new MetaServer(mode, platform);
        for (Consumer<MetaServer> h : START_HOOKS) h.accept(instance);
        return instance;
    }

    public MetaPlatform platform() { return platform; }

    /** Поточний сервер або null (клієнт / сервер ще не стартував). */
    public static MetaServer get() { return instance; }

    public static void stop() {
        MetaServer s = instance;
        if (s != null) s.saveAll();
        instance = null;
    }

    public ProfileStore store() { return store; }

    public void setProfileViewPolicy(Predicate<UUID> p) { profileViewAllowed = p == null ? v -> true : p; }

    /** Хук на вхід (міграції старих форматів режиму): викликається до першої синхронізації. */
    public void onProfileLoad(Consumer<MetaProfile> hook) { joinHooks.add(hook); }

    // ══════════════════════════════════════════════════════════════════════
    //  Життєвий цикл гравця
    // ══════════════════════════════════════════════════════════════════════

    public void onJoin(UUID id, String name, String skin) {
        MetaProfile p = store.load(id);
        long now = System.currentTimeMillis();
        if (p.firstSeen == 0) p.firstSeen = now;
        p.lastSeen = now;
        if (name != null && !name.isEmpty()) p.name = name;
        if (skin != null && !skin.isEmpty()) p.skin = skin;
        for (Consumer<MetaProfile> h : joinHooks) {
            try { h.accept(p); } catch (RuntimeException e) { e.printStackTrace(); }
        }
        for (CosmeticDef d : mode.cosmetics.all()) if (d.starter()) p.owned.add(d.id());
        p.syncSeason(mode.track.id());
        p.wardrobe.validate(mode.cosmetics, p::owns, mode.variants);
        online.put(id, p);
        store.save(p);
        net().toPlayer(id, MetaMessage.of(Op.HELLO, "mode", mode.id));
        sendProfile(id, p, true);
        // новачок бачить образи всіх, усі бачать образ новачка
        net().toPlayer(id, looksMessage(online.keySet()));
        net().toAll(looksMessage(List.of(id)));
        net().toPlayer(id, MetaMessage.of(Op.LOOT_FEED, "entries", new ArrayList<>(feed)));
    }

    public void onLeave(UUID id) {
        MetaProfile p = online.remove(id);
        variant.remove(id);
        if (p != null) {
            p.lastSeen = System.currentTimeMillis();
            store.save(p);
        }
    }

    public void saveAll() { for (MetaProfile p : online.values()) store.save(p); }

    /** Профіль онлайн-гравця або завантажений з диска (офлайн). Ніколи null. */
    public MetaProfile profile(UUID id) {
        MetaProfile p = online.get(id);
        return p != null ? p : store.load(id);
    }

    /** Змінити профіль, зберегти й надіслати власнику. */
    public void edit(UUID id, Consumer<MetaProfile> change) {
        MetaProfile p = profile(id);
        change.accept(p);
        store.save(p);
        if (online.containsKey(id)) sendProfile(id, p, true);
        boards.clear();
    }

    public int level(UUID id) { return profile(id).level(mode); }

    /** Ступінь покращення (режим читає його, щоб застосувати ефект у грі). */
    public int upgradeRank(UUID id, String upgrade) { return profile(id).upgrade(upgrade); }

    // ══════════════════════════════════════════════════════════════════════
    //  Матч
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Нарахувати все за матч, записати, надіслати кожному його екран підсумків.
     * @return результат кожного учасника (ті самі цифри, що пішли гравцям) — для історії матчів режиму
     */
    public Map<UUID, MatchResult> finishMatch(Collection<MatchReport> reports) {
        Map<UUID, MatchResult> out = new LinkedHashMap<>();
        for (MatchReport r : reports) {
            MetaProfile p = profile(r.player);
            if (!r.name.isEmpty()) p.name = r.name;
            MatchResult res = compute(p, r);
            store.save(p);
            out.put(r.player, res);
            if (online.containsKey(r.player)) {
                net().toPlayer(r.player, MetaMessage.of(Op.RESULT, "result", res.toJson()));
                sendProfile(r.player, p, true);
            }
        }
        boards.clear();
        return out;
    }

    /** Чиста частина нарахування (тестується без мережі): змінює профіль і повертає підсумок. */
    public MatchResult compute(MetaProfile p, MatchReport r) {
        MatchResult res = new MatchResult();
        res.mode = mode.id;
        res.won = r.won;
        res.role = r.role;
        res.ladder = r.ladder.isEmpty() ? mode.defaultLadder : r.ladder;
        res.durationSeconds = r.durationSeconds;
        res.xpBefore = p.totalXp();
        res.levelBefore = mode.curve.levelOf(res.xpBefore);
        p.syncSeason(mode.track.id());
        int tierBefore = tier(p);

        int xp = 0;
        long coins = 0;
        List<String> notes = new ArrayList<>();
        for (RewardRule rule : mode.rules) {
            int count = r.count(rule.action());
            int x = rule.xpFor(count), c = rule.coinsFor(count);
            if (x > 0) res.lines.add(new MatchResult.Line(rule.action(), rule.name(), rule.icon(), count, x));
            if (c > 0) {
                coins += c;
                notes.add(rule.name() + " " + Math.min(count, rule.maxUnits() > 0 ? rule.maxUnits() : count) + " × " + rule.coinsPerUnit());
            }
            xp += x;
        }
        for (MatchResult.Line l : r.lines) { res.lines.add(l); xp += l.xp(); }
        if (coins > 0) {
            res.coins.add(new MatchResult.Coins(mode.currency.id(), coins, String.join(" · ", notes)));
            p.wallet.add(mode.currency.id(), coins);
        }
        for (Map.Entry<String, Long> b : r.bonusCoins.entrySet()) {
            res.coins.add(new MatchResult.Coins(b.getKey(), b.getValue(), "Бонус"));
            p.wallet.add(b.getKey(), b.getValue());
        }

        p.addXp(r.track, xp);
        p.seasonXp += xp;
        res.xpAfter = p.totalXp();
        res.levelAfter = mode.curve.levelOf(res.xpAfter);
        int ups = Math.max(0, res.levelAfter - res.levelBefore) * mode.pointsPerLevel;
        p.upgradePoints += ups;
        res.upgradePoints = ups;
        int tierAfter = tier(p);
        for (int l : mode.track.steps().keySet()) {
            if (l > tierBefore && l <= tierAfter && !p.claimed.contains(l)) res.unlockedTrack.add(l);
        }

        // статистика: те, що дав режим, + загальні лічильники бібліотеки (для лідербордів обох режимів)
        r.statAdd.forEach(p.stats::add);
        r.statBest.forEach(p.stats::max);
        String lad = res.ladder;
        p.stats.add("meta.matches", 1).add("meta.playtime", r.durationSeconds);
        if (r.won) p.stats.add("meta.wins", 1);
        if (!lad.isEmpty()) {
            p.stats.add("meta." + lad + ".matches", 1);
            if (r.won) p.stats.add("meta." + lad + ".wins", 1);
        }
        long streak = r.won ? p.stats.get("meta.streak") + 1 : 0;
        p.stats.set("meta.streak", streak).max("meta.bestStreak", streak);

        // випадкова деталь для крафту
        if (!mode.materials.isEmpty()) {
            float chance = mode.materialDropChance * (r.won ? 2f : 1f);
            while (chance > 0) {
                if (rnd.nextFloat() < Math.min(1f, chance)) {
                    MaterialDef m = rollMaterial();
                    if (m != null) {
                        p.addMaterial(m.id(), 1);
                        res.drops.add(Reward.material(m.id(), 1));
                    }
                }
                chance -= 1f;
            }
        }
        return res;
    }

    private MaterialDef rollMaterial() {
        List<MaterialDef> all = new ArrayList<>(mode.materials.values());
        int total = 0;
        for (MaterialDef m : all) total += weight(m);
        if (total <= 0) return null;
        int pick = rnd.nextInt(total);
        for (MaterialDef m : all) { pick -= weight(m); if (pick < 0) return m; }
        return all.get(0);
    }

    private static int weight(MaterialDef m) {
        return switch (m.rarity()) { case COMMON -> 60; case RARE -> 25; case EPIC -> 10; case LEGENDARY -> 4; case MYTHIC -> 1; };
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Видачі, магазин, крафт, доріжка, покращення
    // ══════════════════════════════════════════════════════════════════════

    /** Видати нагороду (дублікат косметики → монети). Профіль зберігає викликач. @return що реально видано */
    public Reward give(MetaProfile p, Reward r) {
        switch (r.type()) {
            case CURRENCY -> p.wallet.add(r.id(), r.amount());
            case MATERIAL -> p.addMaterial(r.id(), (int) r.amount());
            case UPGRADE_POINTS -> p.upgradePoints += (int) r.amount();
            case CHEST -> { if (mode.chest(r.id()) == null) return null; p.addChest(r.id(), (int) r.amount()); }
            case COSMETIC -> {
                CosmeticDef def = mode.cosmetics.get(r.id());
                if (def == null) return null;
                if (p.owned.contains(r.id())) {
                    String cur = mode.shardCurrency.isEmpty() ? mode.currency.id() : mode.shardCurrency;
                    long refund = mode.duplicateRefund * (1L << def.rarity().ordinal());
                    p.wallet.add(cur, refund);
                    return Reward.coins(cur, refund);
                }
                p.owned.add(r.id());
                p.unseen.add(r.id());
            }
        }
        return r;
    }

    /** Адмін/режим: видати й показати анімацію отримання. */
    public void grant(UUID id, String reason, Reward... rewards) {
        List<Object> given = new ArrayList<>();
        edit(id, p -> { for (Reward r : rewards) { Reward g = give(p, r); if (g != null) given.add(g.toJson()); } });
        if (!given.isEmpty()) net().toPlayer(id, MetaMessage.of(Op.GRANTED, "reason", reason, "rewards", given));
    }

    public String buy(UUID id, String offerId) {
        ShopSection.Priced pr = mode.priced(offerId, System.currentTimeMillis());
        if (pr == null) return mode.offer(offerId) == null ? "Такої пропозиції немає" : "Пропозиція вже змінилась — онови магазин";
        ShopOffer o = pr.offer();
        MetaProfile p = profile(id);
        if (o.reward().type() == Reward.Type.COSMETIC && p.owns(o.reward().id())) return "Уже є у тебе";
        if (!p.wallet.spend(o.currency(), pr.price())) return "Не вистачає " + currencyName(o.currency());
        give(p, o.reward());
        afterChange(id, p, "Куплено", o.reward());
        if (o.reward().type() == Reward.Type.COSMETIC) feed(p, o.reward(), "shop");
        return null;
    }

    public String craft(UUID id, String recipeId) {
        Recipe r = mode.recipe(recipeId);
        if (r == null) return "Такого рецепта немає";
        MetaProfile p = profile(id);
        if (r.output().type() == Reward.Type.COSMETIC && p.owns(r.output().id())) return "Уже є у тебе";
        for (Map.Entry<String, Integer> e : r.materials().entrySet()) {
            if (p.material(e.getKey()) < e.getValue()) return "Не вистачає деталей";
        }
        if (!r.currency().isEmpty() && !p.wallet.canAfford(r.currency(), r.cost())) return "Не вистачає " + currencyName(r.currency());
        if (!r.currency().isEmpty()) p.wallet.spend(r.currency(), r.cost());
        r.materials().forEach((k, v) -> p.addMaterial(k, -v));
        give(p, r.output());
        afterChange(id, p, "Скрафтовано", r.output());
        return null;
    }

    public String claim(UUID id, int level) {
        MetaProfile p = profile(id);
        List<Reward> rewards = mode.track.rewards(level);
        if (rewards.isEmpty()) return "На цьому рівні нагород немає";
        p.syncSeason(mode.track.id());
        if (tier(p) < level) return "Ступінь ще не відкрито";
        if (!p.claimed.add(level)) return "Уже забрано";
        List<Object> given = new ArrayList<>();
        for (Reward r : rewards) { Reward g = give(p, r); if (g != null) given.add(g.toJson()); }
        store.save(p);
        sendProfile(id, p, true);
        net().toPlayer(id, MetaMessage.of(Op.GRANTED, "reason", "Ступінь " + level, "rewards", given, "level", level));
        return null;
    }

    /** Ступінь пропуску гравця (сезонний або рівень). */
    public int tier(MetaProfile p) { return mode.track.tier(p.seasonXp, p.level(mode)); }

    /** Відкрити скриню: випадкова рідкість за вагами + гарант. */
    public String openChest(UUID id, String chestId) {
        ChestDef c = mode.chest(chestId);
        if (c == null) return "Такої скрині немає";
        MetaProfile p = profile(id);
        if (p.chest(chestId) <= 0) return "У тебе немає цієї скрині";
        p.addChest(chestId, -1);
        long pity = p.stats.get(c.pityKey()) + 1;
        Rarity rarity = c.pityEvery() > 0 && c.pityRarity() != null && pity >= c.pityEvery() ? c.pityRarity() : rollRarity(c);
        if (c.pityRarity() != null && rarity.ordinal() >= c.pityRarity().ordinal()) pity = 0;
        p.stats.set(c.pityKey(), pity);
        List<CosmeticDef> pool = poolOf(rarity);
        // нема предметів цієї рідкості — найближча рідкість, де вони є (спершу вища: скриня не має «згоріти» в уламки)
        for (int k = 1; pool.isEmpty() && k < Rarity.values().length; k++) {
            int up = rarity.ordinal() + k, down = rarity.ordinal() - k;
            if (up < Rarity.values().length) { pool = poolOf(Rarity.values()[up]); if (!pool.isEmpty()) rarity = Rarity.values()[up]; }
            if (pool.isEmpty() && down >= 0) { pool = poolOf(Rarity.values()[down]); if (!pool.isEmpty()) rarity = Rarity.values()[down]; }
        }
        Reward drop;
        if (pool.isEmpty()) {
            String cur = mode.shardCurrency.isEmpty() ? mode.currency.id() : mode.shardCurrency;
            drop = Reward.coins(cur, mode.duplicateRefund * (1L << rarity.ordinal()) * 2);
        } else {
            List<CosmeticDef> fresh = new ArrayList<>();
            for (CosmeticDef d : pool) if (!p.owns(d.id())) fresh.add(d);
            List<CosmeticDef> from = fresh.isEmpty() ? pool : fresh;   // нові — першими, щоб скриня не сипала дублікатами
            drop = Reward.cosmetic(from.get(rnd.nextInt(from.size())).id());
        }
        Reward got = give(p, drop);
        store.save(p);
        sendProfile(id, p, true);
        List<Object> given = new ArrayList<>();
        if (got != null) given.add(got.toJson());
        net().toPlayer(id, MetaMessage.of(Op.GRANTED, "reason", c.name(), "rewards", given, "chest", chestId, "rarity", rarity.name()));
        if (got != null && got.type() == Reward.Type.COSMETIC) feed(p, got, "chest");
        return null;
    }

    private List<CosmeticDef> poolOf(Rarity r) {
        List<CosmeticDef> pool = new ArrayList<>();
        for (CosmeticDef d : mode.cosmetics.all()) if (d.rarity() == r && !d.starter() && !d.hidden()) pool.add(d);
        return pool;
    }

    private Rarity rollRarity(ChestDef c) {
        int total = 0;
        for (int w : c.weights().values()) total += Math.max(0, w);
        if (total <= 0) return Rarity.COMMON;
        int pick = rnd.nextInt(total);
        for (Rarity r : Rarity.values()) {
            pick -= Math.max(0, c.weights().getOrDefault(r, 0));
            if (pick < 0) return r;
        }
        return Rarity.COMMON;
    }

    /** Щоденний подарунок пропуску. */
    public String daily(UUID id) {
        List<Reward> rewards = mode.track.daily();
        if (rewards.isEmpty()) return "Щоденних подарунків немає";
        MetaProfile p = profile(id);
        long now = System.currentTimeMillis();
        long left = p.lastDaily + mode.track.dailyCooldownMs() - now;
        if (left > 0) return "Наступний подарунок через " + (left / 3_600_000L) + " год " + (left / 60_000L % 60) + " хв";
        p.lastDaily = now;
        List<Object> given = new ArrayList<>();
        for (Reward r : rewards) { Reward g = give(p, r); if (g != null) given.add(g.toJson()); }
        store.save(p);
        sendProfile(id, p, true);
        net().toPlayer(id, MetaMessage.of(Op.GRANTED, "reason", "Щоденний подарунок", "rewards", given));
        return null;
    }

    private void feed(MetaProfile p, Reward r, String source) {
        CosmeticDef d = r.type() == Reward.Type.COSMETIC ? mode.cosmetics.get(r.id()) : null;
        if (d == null) return;
        Object e = J.o("name", p.name, "id", p.id.toString(), "item", d.id(), "src", source, "at", System.currentTimeMillis());
        feed.addFirst(e);
        while (feed.size() > FEED_SIZE) feed.removeLast();
        net().toAll(MetaMessage.of(Op.LOOT_FEED, "add", e));
    }

    public String upgrade(UUID id, String upgradeId) {
        UpgradeDef u = mode.upgrade(upgradeId);
        if (u == null) return "Такого покращення немає";
        MetaProfile p = profile(id);
        int rank = p.upgrade(u.id());
        if (rank >= u.maxRank()) return "Максимальний ступінь";
        if (p.upgradePoints < u.costPerRank()) return "Не вистачає очок покращень";
        p.upgradePoints -= u.costPerRank();
        p.upgrades.put(u.id(), rank + 1);
        store.save(p);
        sendProfile(id, p, true);
        return null;
    }

    private void afterChange(UUID id, MetaProfile p, String reason, Reward r) {
        store.save(p);
        sendProfile(id, p, true);
        net().toPlayer(id, MetaMessage.of(Op.GRANTED, "reason", reason, "rewards", List.of(r.toJson())));
    }

    private String currencyName(String id) {
        var c = mode.currency(id);
        return c == null ? id : c.name();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Косметика
    // ══════════════════════════════════════════════════════════════════════

    /** Який образ зараз на гравці (мафія — «player»; маніяк — «survivor»/«maniac» за роллю в матчі). */
    public void setVariant(UUID id, String v) {
        if (!mode.variants.contains(v)) return;
        if (v.equals(variant.put(id, v))) return;
        net().toAll(looksMessage(List.of(id)));
    }

    public String variant(UUID id) { return variant.getOrDefault(id, mode.variants.get(0)); }

    /** Сховати/показати косметику всім (режим перевіряє: привид, мертвий, глядач). */
    public void setCosmeticsHiddenPolicy(Predicate<UUID> hidden) { cosmeticsHidden = hidden == null ? p -> false : hidden; }

    public void resyncLooks() { net().toAll(looksMessage(online.keySet())); }

    public Look activeLook(UUID id) {
        MetaProfile p = online.get(id);
        if (p == null || cosmeticsHidden.test(id)) return new Look();
        return p.wardrobe.look(variant(id));
    }

    private MetaMessage looksMessage(Collection<UUID> ids) {
        Map<String, Object> players = new LinkedHashMap<>();
        for (UUID id : ids) players.put(id.toString(), activeLook(id).toJson());
        return MetaMessage.of(Op.LOOKS, "players", players);
    }

    private void saveWardrobe(UUID id, Map<String, Object> json) {
        MetaProfile p = online.get(id);
        if (p == null) return;
        Wardrobe w = Wardrobe.fromJson(json);
        w.validate(mode.cosmetics, p::owns, mode.variants);
        p.wardrobe = w;
        store.save(p);
        net().toAll(looksMessage(List.of(id)));
        net().toPlayer(id, MetaMessage.of(Op.NOTICE, "text", "Гардероб збережено", "kind", "saved"));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Лідерборди
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Топ за ключем: {@code "level"} (загальний XP), {@code "coins"} (основна валюта) або будь-яка
     * {@link StatDef} режиму. Кеш 20 с — повний перебір файлів не на кожен запит.
     */
    public List<Object> leaderboard(String key, int limit) {
        Board b = boards.get(key);
        long now = System.currentTimeMillis();
        if (b != null && now - b.at < 20_000) return b.rows;
        StatDef def = mode.stat(key);
        List<MetaProfile> all = new ArrayList<>();
        for (UUID id : store.ids()) {
            MetaProfile p = online.containsKey(id) ? online.get(id) : store.load(id);
            if (p.hasActivity()) all.add(p);
        }
        all.sort((x, y) -> Long.compare(value(y, key, def), value(x, key, def)));
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, all.size()); i++) {
            MetaProfile p = all.get(i);
            rows.add(J.o("id", p.id.toString(), "name", p.name, "skin", p.skin, "value", value(p, key, def),
                    "level", p.level(mode), "lastSeen", p.lastSeen));
        }
        boards.put(key, new Board(now, rows));
        return rows;
    }

    private long value(MetaProfile p, String key, StatDef def) {
        if ("level".equals(key)) return p.totalXp();
        if ("coins".equals(key)) return p.wallet.get(mode.currency.id());
        return def != null ? def.value(p.stats) : p.stats.get(key);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HUD: компас і ефекти
    // ══════════════════════════════════════════════════════════════════════

    /** Замінити цілі компаса гравця (порожній список — сховати компас). */
    public void setWaypoints(UUID id, List<Waypoint> list) {
        List<Object> l = new ArrayList<>();
        for (Waypoint w : list) l.add(w.toJson());
        net().toPlayer(id, MetaMessage.of(Op.WAYPOINTS, "list", l));
    }

    /** 3D-плюсики хілу над точкою — бачать усі поруч. */
    public void healFx(UUID near, double x, double y, double z, int count, int color) { healFx(near, x, y, z, count, color, count * .035f); }

    /** @param seconds тривалість «струменя» плюсів (наприклад, увесь час бинтування) */
    public void healFx(UUID near, double x, double y, double z, int count, int color, float seconds) {
        net().toNear(near, x, y, z, 48, MetaMessage.of(Op.FX_PLUS, "x", x, "y", y, "z", z, "n", count, "c", color, "t", seconds));
    }

    /** Відкрити гравцю екран бібліотеки: wardrobe, profile, shop, track, leaderboard, upgrades. */
    public void open(UUID id, String screen) { net().toPlayer(id, MetaMessage.of(Op.OPEN, "screen", screen)); }

    public void notice(UUID id, String text) { net().toPlayer(id, MetaMessage.of(Op.NOTICE, "text", text, "kind", "info")); }

    // ══════════════════════════════════════════════════════════════════════
    //  Вхідні повідомлення клієнта
    // ══════════════════════════════════════════════════════════════════════

    public void handle(UUID from, MetaMessage m) {
        if (!m.op().toServer() || !online.containsKey(from)) return;
        String err = null;
        switch (m.op()) {
            case REQ_PROFILE -> {
                UUID target = parse(m.str("target"), from);
                if (!target.equals(from) && !profileViewAllowed.test(from)) err = "Зараз не можна дивитись чужі профілі";
                else sendProfile(from, profile(target), target.equals(from));
            }
            case SAVE_WARDROBE -> saveWardrobe(from, m.obj("wardrobe"));
            case BUY -> err = buy(from, m.str("offer"));
            case CRAFT -> err = craft(from, m.str("recipe"));
            case CLAIM -> err = claim(from, (int) m.num("level"));
            case UPGRADE -> err = upgrade(from, m.str("upgrade"));
            case OPEN_CHEST -> err = openChest(from, m.str("chest"));
            case DAILY -> err = daily(from);
            case REQ_LEADERBOARD -> net().toPlayer(from, MetaMessage.of(Op.LEADERBOARD, "key", m.str("key"),
                    "rows", leaderboard(m.str("key"), 50)));
            case SEEN -> {
                MetaProfile p = online.get(from);
                for (Object o : J.arr(m.body(), "items")) if (o instanceof String s) p.unseen.remove(s);
                store.save(p);
            }
            default -> { }
        }
        if (err != null) net().toPlayer(from, MetaMessage.of(Op.NOTICE, "text", err, "kind", "error"));
    }

    private static UUID parse(String s, UUID def) {
        try { return s == null || s.isEmpty() ? def : UUID.fromString(s); } catch (IllegalArgumentException e) { return def; }
    }

    private void sendProfile(UUID to, MetaProfile p, boolean self) {
        Map<String, Object> json = p.toJson(false);
        if (!self) {   // чужим — без гардероба-пресетів і деталей (лише те, що видно в профілі)
            json.remove("materials");
            json.remove("unseen");
        }
        net().toPlayer(to, MetaMessage.of(Op.PROFILE, "self", self, "profile", json));
    }

    private static MetaNetwork net() { return MetaNetwork.get(); }
}
