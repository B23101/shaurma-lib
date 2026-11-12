package dev.shaurmalib.meta;

import dev.shaurmalib.meta.cosmetics.CosmeticCatalog;
import dev.shaurmalib.meta.economy.ChestDef;
import dev.shaurmalib.meta.economy.Currency;
import dev.shaurmalib.meta.economy.MaterialDef;
import dev.shaurmalib.meta.economy.Recipe;
import dev.shaurmalib.meta.economy.Reward;
import dev.shaurmalib.meta.economy.RewardTrack;
import dev.shaurmalib.meta.economy.ShopOffer;
import dev.shaurmalib.meta.economy.ShopSection;
import dev.shaurmalib.meta.economy.UpgradeDef;
import dev.shaurmalib.meta.match.RewardRule;
import dev.shaurmalib.meta.progress.RankLadder;
import dev.shaurmalib.meta.progress.XpCurve;
import dev.shaurmalib.meta.stats.StatDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Опис мета-гри одного режиму: валюта, крива рівнів, ранги, статистика, косметика, крафт, магазин,
 * доріжка нагород, правила нагород за матч і стиль меню. Бібліотека — рушій; режим — дані.
 * Реєструється однаково на сервері й клієнті ({@link MetaModes#register}), бо обидві сторони мода мають той самий код.
 *
 * <p>Профілі режимів не перетинаються: файли лежать у {@code <світ>/data/shaurma_meta/<id>/}, валюта й
 * косметика однієї гри не видні в іншій.</p>
 */
public final class MetaMode {

    public final String id;
    public final String title;
    public final MetaTheme theme;
    public final Currency currency;
    public final List<Currency> currencies;
    public final XpCurve curve;
    public final Map<String, RankLadder> ladders;
    public final String defaultLadder;
    public final List<StatDef> stats;
    public final CosmeticCatalog cosmetics;
    public final List<String> variants;
    public final Map<String, String> variantNames;
    public final Map<String, MaterialDef> materials;
    public final List<Recipe> recipes;
    public final List<ShopOffer> shop;
    public final RewardTrack track;
    public final List<UpgradeDef> upgrades;
    public final List<RewardRule> rules;
    public final int pointsPerLevel;
    /** Монети за дублікат косметики (з доріжки чи крафту, якщо предмет уже є). */
    public final long duplicateRefund;
    /** Шанс (0..1) отримати випадкову деталь за матч; переможці — подвійний. */
    public final float materialDropChance;
    /** Валюта за дублікати («уламки»); порожньо — дублікати дають основну валюту. */
    public final String shardCurrency;
    public final Map<String, ChestDef> chests;
    public final List<ShopSection> sections;

    private MetaMode(Builder b) {
        id = b.id;
        title = b.title;
        theme = b.theme;
        currencies = List.copyOf(b.currencies);
        currency = currencies.get(0);
        curve = b.curve;
        ladders = Map.copyOf(b.ladders);
        defaultLadder = b.defaultLadder != null ? b.defaultLadder : (b.ladders.isEmpty() ? "" : b.ladders.keySet().iterator().next());
        stats = List.copyOf(b.stats);
        cosmetics = b.cosmetics;
        variants = List.copyOf(b.variants.keySet());
        variantNames = Map.copyOf(b.variants);
        materials = Map.copyOf(b.materials);
        recipes = List.copyOf(b.recipes);
        shop = List.copyOf(b.shop);
        track = b.track;
        upgrades = List.copyOf(b.upgrades);
        rules = List.copyOf(b.rules);
        pointsPerLevel = b.pointsPerLevel;
        duplicateRefund = b.duplicateRefund;
        materialDropChance = b.materialDropChance;
        shardCurrency = b.shardCurrency;
        chests = Map.copyOf(b.chests);
        sections = List.copyOf(b.sections);
    }

    public Currency currency(String id) {
        for (Currency c : currencies) if (c.id().equals(id)) return c;
        return null;
    }

    public RankLadder ladder(String id) {
        RankLadder l = ladders.get(id);
        return l != null ? l : ladders.get(defaultLadder);
    }

    public RewardRule rule(String action) {
        for (RewardRule r : rules) if (r.action().equals(action)) return r;
        return null;
    }

    public Recipe recipe(String id) {
        for (Recipe r : recipes) if (r.id().equals(id)) return r;
        return null;
    }

    public ShopOffer offer(String id) {
        for (ShopOffer o : shop) if (o.id().equals(id)) return o;
        return null;
    }

    public ChestDef chest(String id) { return id == null ? null : chests.get(id); }

    public ShopSection section(String id) {
        for (ShopSection s : sections) if (s.id().equals(id)) return s;
        return null;
    }

    /** Пропозиція, що ЗАРАЗ продається (з урахуванням ротації й знижки), або null. */
    public ShopSection.Priced priced(String offerId, long now) {
        ShopOffer o = offer(offerId);
        if (o == null) return null;
        ShopSection s = section(o.section());
        if (s == null) s = ShopSection.fixed(o.section(), "");
        for (ShopSection.Priced p : s.current(id, shop, now)) if (p.offer().id().equals(offerId)) return p;
        return null;
    }

    public UpgradeDef upgrade(String id) {
        for (UpgradeDef u : upgrades) if (u.id().equals(id)) return u;
        return null;
    }

    public StatDef stat(String key) {
        for (StatDef s : stats) if (s.key().equals(key)) return s;
        return null;
    }

    /** Рецепт, що видає цю косметику (для позначки «можна скрафтити» в гардеробі). */
    public Recipe recipeFor(String cosmeticId) {
        for (Recipe r : recipes) if (r.output().type() == Reward.Type.COSMETIC && r.output().id().equals(cosmeticId)) return r;
        return null;
    }

    public ShopOffer offerFor(String cosmeticId) {
        for (ShopOffer o : shop) if (o.reward().type() == Reward.Type.COSMETIC && o.reward().id().equals(cosmeticId)) return o;
        return null;
    }

    public static Builder builder(String id, String title) { return new Builder(id, title); }

    public static final class Builder {
        private final String id, title;
        private MetaTheme theme = MetaTheme.glass();
        private final List<Currency> currencies = new ArrayList<>();
        private XpCurve curve = new XpCurve(50, 250);
        private final Map<String, RankLadder> ladders = new LinkedHashMap<>();
        private String defaultLadder;
        private final List<StatDef> stats = new ArrayList<>();
        private CosmeticCatalog cosmetics = new CosmeticCatalog();
        private final Map<String, String> variants = new LinkedHashMap<>();
        private final Map<String, MaterialDef> materials = new LinkedHashMap<>();
        private final List<Recipe> recipes = new ArrayList<>();
        private final List<ShopOffer> shop = new ArrayList<>();
        private RewardTrack track = new RewardTrack();
        private final List<UpgradeDef> upgrades = new ArrayList<>();
        private final List<RewardRule> rules = new ArrayList<>();
        private int pointsPerLevel = 1;
        private long duplicateRefund = 25;
        private float materialDropChance = .35f;
        private String shardCurrency = "";
        private final Map<String, ChestDef> chests = new LinkedHashMap<>();
        private final List<ShopSection> sections = new ArrayList<>();

        Builder(String id, String title) {
            if (!id.matches("[a-z0-9_]{1,32}")) throw new IllegalArgumentException("id режиму: [a-z0-9_]{1,32}");
            this.id = id;
            this.title = title;
        }

        public Builder theme(MetaTheme t) { theme = t; return this; }
        public Builder currency(Currency c) { currencies.add(c); return this; }
        public Builder curve(XpCurve c) { curve = c; return this; }
        public Builder ladder(RankLadder l) { ladders.put(l.id(), l); return this; }
        public Builder defaultLadder(String id) { defaultLadder = id; return this; }
        public Builder stat(StatDef s) { stats.add(s); return this; }
        public Builder cosmetics(CosmeticCatalog c) { cosmetics = c; return this; }
        public Builder variant(String id, String name) { variants.put(id, name); return this; }
        public Builder material(MaterialDef m) { materials.put(m.id(), m); return this; }
        public Builder recipe(Recipe r) { recipes.add(r); return this; }
        public Builder offer(ShopOffer o) { shop.add(o); return this; }
        public Builder track(RewardTrack t) { track = t; return this; }
        public Builder upgrade(UpgradeDef u) { upgrades.add(u); return this; }
        public Builder rule(RewardRule r) { rules.add(r); return this; }
        public Builder pointsPerLevel(int n) { pointsPerLevel = Math.max(0, n); return this; }
        public Builder duplicateRefund(long n) { duplicateRefund = Math.max(0, n); return this; }
        /** Друга валюта для дублікатів (і власного розділу «За уламки»). */
        public Builder shards(Currency c) { currencies.add(c); shardCurrency = c.id(); return this; }
        public Builder chest(ChestDef c) { chests.put(c.id(), c); return this; }
        public Builder section(ShopSection s) { sections.add(s); return this; }
        public Builder materialDropChance(float c) { materialDropChance = Math.max(0, Math.min(1, c)); return this; }

        public MetaMode build() {
            if (currencies.isEmpty()) throw new IllegalStateException("режим " + id + ": потрібна хоча б одна валюта");
            if (variants.isEmpty()) variants.put("player", "Гравець");
            if (sections.stream().noneMatch(s -> s.id().equals(ShopOffer.CATALOG))) sections.add(ShopSection.fixed(ShopOffer.CATALOG, "Каталог"));
            return new MetaMode(this);
        }
    }
}
