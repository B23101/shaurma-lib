package dev.shaurmalib.meta;

import dev.shaurmalib.meta.cosmetics.CosmeticCatalog;
import dev.shaurmalib.meta.cosmetics.CosmeticDef;
import dev.shaurmalib.meta.cosmetics.CosmeticSlot;
import dev.shaurmalib.meta.cosmetics.Look;
import dev.shaurmalib.meta.cosmetics.Worn;
import dev.shaurmalib.meta.economy.Currency;
import dev.shaurmalib.meta.economy.MaterialDef;
import dev.shaurmalib.meta.economy.Recipe;
import dev.shaurmalib.meta.economy.Reward;
import dev.shaurmalib.meta.economy.RewardTrack;
import dev.shaurmalib.meta.economy.ShopOffer;
import dev.shaurmalib.meta.cosmetics.Rarity;
import dev.shaurmalib.meta.hud.Waypoint;
import dev.shaurmalib.meta.hud.WaypointCompass;
import dev.shaurmalib.meta.fx.PlusBurst;
import dev.shaurmalib.meta.io.J;
import dev.shaurmalib.meta.match.MatchReport;
import dev.shaurmalib.meta.match.MatchResult;
import dev.shaurmalib.meta.match.RewardRule;
import dev.shaurmalib.meta.net.MetaMessage;
import dev.shaurmalib.meta.profile.MetaProfile;
import dev.shaurmalib.meta.progress.RankLadder;
import dev.shaurmalib.meta.progress.XpCurve;
import dev.shaurmalib.meta.server.MetaPlatform;
import dev.shaurmalib.meta.server.MetaServer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Перевірки мета-двигуна без гри: нарахування, економіка, косметика, серіалізація, компас, ефекти. */
public final class MetaSmokeTest {

    private static int passed;

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("META FAIL: " + what);
        passed++;
    }

    public static void main(String[] a) throws Exception { run(); }

    public static void run() throws Exception {
        // ── крива й ранги
        XpCurve c = new XpCurve(50, 250);
        check(c.needed(1) == 300 && c.levelOf(299) == 1 && c.levelOf(300) == 2, "крива: рівень 1 = 300 XP");
        check(c.startOf(3) == 850 && c.intoLevel(900) == 50, "крива: початок рівня 3 і залишок");
        RankLadder lad = RankLadder.classic("s", "S", new String[]{"a", "b", "c", "d", "e", "f", "g"},
                new String[]{"A", "B", "C", "D", "E", "F", "G"}, new int[]{1});
        check(lad.ofLevel(1).roman().equals("I") && lad.ofLevel(4).roman().equals("IV") && lad.ofLevel(9).roman().equals("V"), "ранг: дивізіони I..V");
        check(lad.ofLevel(45).top() && lad.ofLevel(45).roman().isEmpty(), "ранг: найвищий без дивізіонів");

        // ── режим
        CosmeticCatalog cat = new CosmeticCatalog()
                .add(CosmeticDef.builder("hat", CosmeticSlot.HEAD, "Капелюх").model("x:hat").starter().build())
                .add(CosmeticDef.builder("ghost", CosmeticSlot.PET, "Привид").model("x:ghost").rarity(Rarity.EPIC).build())
                .add(CosmeticDef.builder("star", CosmeticSlot.AURA, "Зірка").aura(0, 0xE5383B).palette().build());
        MetaMode mode = MetaMode.builder("test", "Тест")
                .currency(new Currency("darkcoin", "DarkCoins", 0xE5383B, "blood"))
                .ladder(lad)
                .cosmetics(cat)
                .variant("survivor", "Виживий").variant("maniac", "Маніяк")
                .material(new MaterialDef("thread", "Нитки", "thread", Rarity.COMMON))
                .recipe(Recipe.of("craft_ghost", Reward.cosmetic("ghost"), "darkcoin", 10, "thread", 2))
                .offer(ShopOffer.cosmetic("star", "darkcoin", 50))
                .track(new RewardTrack().at(2, Reward.coins("darkcoin", 100), Reward.cosmetic("ghost")))
                .rule(RewardRule.flat("complete", "Завершення матчу", "flag", 100))
                .rule(RewardRule.flat("win", "Перемога", "trophy", 200))
                .rule(RewardRule.per("hook", "Повішено на гак", "hook", 35, 4))
                .rule(RewardRule.per("kill", "Вбивства", "skull", 50, 0).coins(10))
                .materialDropChance(0)
                .shards(new Currency("shard", "Уламки", 0x4DA3FF, "gem"))
                .chest(dev.shaurmalib.meta.economy.ChestDef.of("box", "Скриня", Rarity.COMMON, 3, Rarity.EPIC, Rarity.COMMON, 100))
                .section(dev.shaurmalib.meta.economy.ShopSection.rotating("daily", "Пропозиції дня", 24, 1, 10, 25))
                .offer(ShopOffer.cosmetic("ghost", "darkcoin", 100).in("daily"))
                .build();

        Path dir = Files.createTempDirectory("meta");
        MetaPlatform plat = new MetaPlatform() {
            public Path dataDir() { return dir; }
            public Collection<UUID> online() { return List.of(); }
            public boolean isAdmin(UUID p) { return false; }
            public void tell(UUID p, String t, boolean ab) { }
        };
        MetaServer srv = MetaServer.start(mode, plat);
        UUID me = UUID.randomUUID();
        srv.onJoin(me, "Steve", "");
        check(srv.profile(me).owns("hat"), "стартова косметика видається при вході");

        // ── матч: як на скріні (×2 гаки = 70, ×4 вбивства = 200, монети 4 × 10)
        MatchReport r = MatchReport.of(me, "Steve").track("maniac:x").ladder("s").won(true).duration(600)
                .action("complete", 1).action("win", 1).action("hook", 2).action("kill", 4).stat("maniac.x.downs", 3);
        MatchResult res = srv.finishMatch(List.of(r)).get(me);
        check(res.xpGained() == 100 + 200 + 70 + 200, "XP за дії: 570");
        check(res.coinsOf("darkcoin") == 40, "монети: 4 × 10 = 40");
        check(res.levelBefore == 1 && res.levelAfter == 2 && res.upgradePoints == 1, "новий рівень → +1 очко покращень");
        check(res.unlockedTrack.contains(2), "доріжка: рівень 2 відкрито");
        MetaProfile p = srv.profile(me);
        check(p.xp("maniac:x") == 570 && p.stats.get("maniac.x.downs") == 3 && p.stats.get("meta.wins") == 1, "статистика й доріжка XP записані");
        check(p.wallet.get("darkcoin") == 40, "гаманець поповнено");

        // ── гаки з лімітом 4
        check(mode.rule("hook").xpFor(9) == 35 * 4, "ліміт нагород: рахуються лише 4 гаки");

        // ── економіка
        check(srv.buy(me, "cosmetic:star") != null, "покупка без грошей відхиляється");
        check(srv.claim(me, 2) == null && p.wallet.get("darkcoin") == 140 && p.owns("ghost"), "доріжка: монети й улюбленець");
        check(srv.claim(me, 2) != null, "доріжка: двічі не забрати");
        check(srv.buy(me, "cosmetic:star") == null && p.owns("star") && p.wallet.get("darkcoin") == 90, "покупка списує рівно ціну");
        check(srv.buy(me, "cosmetic:star") != null, "повторна покупка косметики заборонена");
        p.addMaterial("thread", 2);
        check(srv.craft(me, "craft_ghost") != null, "крафт уже наявного предмета відхиляється");
        long before = p.wallet.get("shard");
        Reward dup = srv.give(p, Reward.cosmetic("ghost"));
        check(dup.type() == Reward.Type.CURRENCY && dup.id().equals("shard") && p.wallet.get("shard") == before + mode.duplicateRefund * 4, "дублікат епічної → уламки ×4");

        // ── косметика: межі й валідація
        Look look = new Look();
        Worn pet = new Worn("ghost", 0xFFFFFF);
        pet.params.put("x", 5f); pet.params.put("z", 5f); pet.params.put("scale", Float.NaN);
        look.put(CosmeticSlot.PET, pet);
        look.put(CosmeticSlot.HEAD, new Worn("ghost", 0));          // не той слот
        look.put(CosmeticSlot.BACK, new Worn("nope", 0));           // нема в каталозі
        look.validate(cat, id -> true);
        check(!look.has(CosmeticSlot.HEAD) && !look.has(CosmeticSlot.BACK), "образ: чужий слот і невідомий предмет прибрано");
        Worn v = look.get(CosmeticSlot.PET);
        check(Math.hypot(v.params.get("x"), v.params.get("z")) <= CosmeticSlot.PET_MAX_RADIUS + 1e-4, "улюбленець: радіус ≤ 1.2");
        check(v.params.get("scale") == .55f, "NaN → значення за замовчуванням");
        Look lk2 = new Look();
        lk2.put(CosmeticSlot.AURA, new Worn("star", 0x123456));
        lk2.validate(cat, id -> false);
        check(lk2.isEmpty(), "невласна косметика не одягається");

        // ── серіалізація профілю й мережі
        MetaProfile back = MetaProfile.fromJson(me, J.parse(J.write(p.toJson(true))));
        check(back.totalXp() == p.totalXp() && back.wallet.get("darkcoin") == p.wallet.get("darkcoin")
                && back.owned.equals(p.owned) && back.claimed.equals(p.claimed), "профіль: JSON туди-назад");
        MetaProfile disk = srv.store().load(me);
        check(disk.totalXp() == p.totalXp(), "профіль збережено на диск");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        MetaMessage.of(MetaMessage.Op.RESULT, "result", res.toJson()).write(new DataOutputStream(bos));
        MetaMessage m = MetaMessage.read(new DataInputStream(new ByteArrayInputStream(bos.toByteArray())));
        MatchResult res2 = MatchResult.fromJson(m.obj("result"));
        check(res2.xpGained() == res.xpGained() && res2.lines.size() == res.lines.size()
                && res2.lines.get(2).name().equals("Повішено на гак"), "результат: дріт туди-назад (кирилиця)");

        // ── скрині й гарант: ваги лише COMMON, але кожне 3-тє відкриття — EPIC (привид)
        srv.grant(me, "тест", Reward.chest("box", 3));
        check(p.chest("box") == 3, "скрині додано в профіль");
        long shardsBefore = p.wallet.get("shard");
        srv.openChest(me, "box"); srv.openChest(me, "box");
        check(p.stats.get("meta.pity.box") == 2, "гарант рахує відкриття без епічної");
        srv.openChest(me, "box");
        check(p.stats.get("meta.pity.box") == 0 && p.chest("box") == 0, "гарант спрацював на 3-му відкритті й скинувся");
        check(p.wallet.get("shard") > shardsBefore, "дублікат/порожній пул → уламки");
        check(srv.openChest(me, "box") != null, "без скрині не відкрити");

        // ── ротація: той самий день → той самий вибір і знижка
        long now = System.currentTimeMillis();
        var sec = mode.section("daily");
        var a1 = sec.current(mode.id, mode.shop, now);
        var a2 = sec.current(mode.id, mode.shop, now + 1000);
        check(a1.size() == 1 && a1.get(0).discount() >= 10 && a1.get(0).discount() <= 25 && a1.get(0).price() < 100
                && a1.get(0).discount() == a2.get(0).discount(), "ротація дня детермінована, знижка 10–25%");
        check(sec.endsAt(now) > now && sec.endsAt(now) - now <= 24 * 3_600_000L, "таймер ротації");

        // ── лідерборд
        List<Object> top = srv.leaderboard("level", 10);
        check(top.size() == 1 && J.str(J.asObj(top.get(0)), "name", "").equals("Steve"), "лідерборд за рівнем");

        // ── компас
        Waypoint wp = new Waypoint("heli", "Галявина", 0, 70, 100, "heli", 0xFFC83D, 1, false);
        WaypointCompass.Reading rd = WaypointCompass.read(wp, 0, 64, 0, 0f);
        check(Math.abs(rd.relYaw()) < 1e-6 && rd.vertical() == WaypointCompass.Vertical.ABOVE, "компас: ціль прямо попереду, вище");
        rd = WaypointCompass.read(wp, 0, 70, 0, 90f);   // дивимось на −X, ціль на +Z → праворуч? ні: ліворуч
        check(Math.abs(rd.relYaw() + 90) < 1e-6, "компас: поворот на 90° — ціль ліворуч");
        check(WaypointCompass.read(wp, 100, 70, 100, 0f).distanceText().equals("100 м"), "компас: дистанція");

        // ── плюсики
        PlusBurst.clear();
        PlusBurst.spawn(0, 1, 0, 8, 0x55FF77, .5);
        for (int i = 0; i < 20; i++) PlusBurst.tick(.05f);
        check(!PlusBurst.visible().isEmpty() && PlusBurst.visible().get(0).y > 1.2, "плюсики летять угору");
        for (int i = 0; i < 60; i++) PlusBurst.tick(.05f);
        check(PlusBurst.visible().isEmpty(), "плюсики зникають");

        MetaServer.stop();
        System.out.println("meta: " + passed + " перевірок пройдено");
    }
}
