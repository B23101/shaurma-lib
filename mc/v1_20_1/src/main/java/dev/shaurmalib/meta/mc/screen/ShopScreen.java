package dev.shaurmalib.meta.mc.screen;

import dev.shaurmalib.meta.client.MetaClient;
import dev.shaurmalib.meta.cosmetics.CosmeticDef;
import dev.shaurmalib.meta.cosmetics.Rarity;
import dev.shaurmalib.meta.economy.ChestDef;
import dev.shaurmalib.meta.economy.Currency;
import dev.shaurmalib.meta.economy.MaterialDef;
import dev.shaurmalib.meta.economy.Recipe;
import dev.shaurmalib.meta.economy.Reward;
import dev.shaurmalib.meta.economy.ShopOffer;
import dev.shaurmalib.meta.economy.ShopSection;
import dev.shaurmalib.meta.io.J;
import dev.shaurmalib.meta.mc.MetaScreens;
import dev.shaurmalib.meta.mc.render.CosmeticRenderer;
import dev.shaurmalib.meta.mc.skin.MetaSkin;
import dev.shaurmalib.meta.mc.ui.MetaIcons;
import dev.shaurmalib.meta.mc.ui.MetaUi;
import dev.shaurmalib.meta.profile.MetaProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Map;

/**
 * Магазин і майстерня (вигляд — скін режиму).
 * <ul>
 *   <li>угорі — живий рядок останніх випадінь гравців сервера;</li>
 *   <li>скрині: скільки є, «Відкрити», купити, смужка гаранту («Гарант епічної 3/15»);</li>
 *   <li>розділи режиму в їхньому порядку: ротаційні («Пропозиції дня», «Нічний ринок») — таймер біля
 *       назви розділу й знижка на картці; звичайні («За уламки», «Каталог»);</li>
 *   <li>картка: сцена кольору рідкості, 3D-манекен у предметі, ярлик рідкості, назва, ціна (стара закреслена).</li>
 * </ul>
 * Сторінка гортається колесом. Ціну й наявність перевіряє сервер.
 */
public class ShopScreen extends MetaScreen {

    private boolean craft;
    private float scroll, scrollGoal;
    private int contentH = 540;
    private float tabTime;
    private String pending = "";
    private float pendingAt;
    private float chestFlash;
    private String chestOpened = "";
    private int craftPage;

    private static final int TOP = 84, CARD_W = 146, CARD_H = 196, GAP = 10, STAGE_H = 132;

    public ShopScreen(Screen parent, boolean craft) {
        super("Магазин", parent);
        this.craft = craft;
    }

    @Override protected String section() { return "shop"; }

    private MetaProfile p() { return me(); }

    @Override
    protected void draw(GuiGraphics g) {
        header(g, craft ? "МАЙСТЕРНЯ" : "МАГАЗИН", craft ? "КРАФТ КОСМЕТИКИ З ДЕТАЛЕЙ" : "КОСМЕТИКА · СКРИНІ · ПРОПОЗИЦІЇ");
        button(g, "tShop", 446, 54, 120, 20, "МАГАЗИН", !craft, () -> { if (craft) { craft = false; scrollGoal = scroll = 0; tabTime = time; click(); } });
        button(g, "tCraft", 572, 54, 120, 20, "МАЙСТЕРНЯ", craft, () -> { if (!craft) { craft = true; tabTime = time; click(); } });
        if (craft) { drawCraft(g); return; }

        scrollGoal = Math.max(0, Math.min(scrollGoal, Math.max(0, contentH - (DH - TOP - 6))));
        scroll = MetaUi.approach(scroll, scrollGoal, 12f, dt);
        clipTop = TOP;
        g.enableScissor((int) ox, (int) (oy + TOP * ui), (int) (ox + DW * ui), (int) (oy + DH * ui));
        int y = TOP + 4 - Math.round(scroll);
        y = drawFeed(g, y);
        y = drawChests(g, y);
        for (ShopSection s : mode.sections) {
            if (s.id().equals("chests")) continue;
            y = drawSection(g, s, y);
        }
        contentH = y + Math.round(scroll) - TOP;
        g.disableScissor();
        clipTop = 0;
        int view = DH - TOP - 6;
        if (contentH > view) {
            int th = Math.max(30, view * view / contentH), ty = TOP + Math.round((view - th) * scroll / Math.max(1, contentH - view));
            g.fill(950, TOP, 952, TOP + view, MetaUi.a(theme.border(), .6f));
            g.fill(950, ty, 952, ty + th, MetaUi.solid(theme.accent()));
        }
    }

    // ── стрічка випадінь ────────────────────────────────────────────────
    private int drawFeed(GuiGraphics g, int y) {
        List<Map<String, Object>> feed = MetaClient.feed();
        if (feed.isEmpty()) return y;
        panel(g, 14, y, 932, 26, false);
        int[] widths = new int[feed.size()];
        int total = 0;
        for (int i = 0; i < feed.size(); i++) { widths[i] = font.width(J.str(feed.get(i), "name", "?")) + 48; total += widths[i]; }
        int x = 16 - (int) ((System.currentTimeMillis() / 30) % Math.max(1, total));
        g.enableScissor((int) (ox + 16 * ui), (int) (oy + Math.max(TOP, y) * ui), (int) (ox + 944 * ui), (int) (oy + (y + 26) * ui));
        for (int rep = 0; rep < 4 && x < 944; rep++) {
            for (int i = 0; i < feed.size() && x < 944; i++) {
                Map<String, Object> e = feed.get(i);
                CosmeticDef d = mode.cosmetics.get(J.str(e, "item", ""));
                String name = J.str(e, "name", "?");
                int rc = d == null ? theme.dim() : d.rarity().color;
                MetaUi.text(g, font, name, x + 6, y + 9, MetaUi.solid(theme.text()), false);
                int ix = x + 12 + font.width(name);
                g.fill(ix, y + 4, ix + 18, y + 22, MetaUi.a(rc, .3f));
                Item it = d == null ? null : CosmeticRenderer.item(d.id());
                if (it != null) g.renderItem(CosmeticRenderer.stack(it, d.defaultColor()), ix + 1, y + 5);
                else icon(g, "aura", ix + 5, y + 9, 1, MetaUi.solid(rc));
                x += widths[i];
            }
        }
        g.disableScissor();
        return y + 36;
    }

    // ── скрині ──────────────────────────────────────────────────────────
    private int drawChests(GuiGraphics g, int y) {
        if (mode.chests.isEmpty()) return y;
        MetaProfile p = p();
        y += skin.sectionTitle(g, font, 16, y, 928, "СКРИНІ", null, time) + 4;
        chestFlash = Math.max(0, chestFlash - dt * 1.5f);
        int x = 14, w = 300, h = 96;
        for (ChestDef c : mode.chests.values()) {
            if (x + w > 946) { x = 14; y += h + GAP; }
            int rc = c.rarity().color;
            boolean flash = chestOpened.equals(c.id()) && chestFlash > 0;
            skin.card(g, x, y, w, h, rc, flash, 0, time);
            skin.stage(g, x + 2, y + 2, 92, h - 4, rc, time, flash ? chestFlash : 0);
            float bob = (float) Math.sin(time * 2 + x) * 2 - (flash ? 6 * chestFlash : 0);
            Preview.itemIcon(g, c.model(), x + 48, y + 46 + bob, 4.2f);
            int have = p == null ? 0 : p.chest(c.id());
            MetaUi.text(g, font, c.name().toUpperCase(), x + 104, y + 9, MetaUi.solid(theme.text()), false);
            MetaUi.textS(g, font, "у тебе: " + have, x + 104, y + 21, .8f, have > 0 ? MetaUi.solid(theme.gold()) : MetaUi.solid(theme.dim()), false);
            if (c.pityEvery() > 0 && c.pityRarity() != null) {
                long pity = p == null ? 0 : p.stats.get(c.pityKey());
                MetaUi.textS(g, font, "Гарант: " + c.pityRarity().title.toLowerCase(), x + 104, y + 35, .75f, MetaUi.solid(theme.dim()), false);
                MetaUi.textR(g, font, pity + "/" + c.pityEvery(), x + w - 10, y + 34, MetaUi.solid(theme.dim()), false);
                int bw = w - 114, seg = Math.max(1, c.pityEvery());
                for (int k = 0; k < seg; k++) {
                    int sx = x + 104 + k * bw / seg, ex = x + 104 + (k + 1) * bw / seg - 1;
                    g.fill(sx, y + 46, ex, y + 50, k < pity ? MetaUi.solid(c.pityRarity().color) : MetaUi.a(0, .5f));
                }
            }
            int by = y + h - 30;
            if (have > 0) primary(g, "open" + c.id(), x + 104, by, 88, 22, "ВІДКРИТИ", () -> {
                click();
                chestOpened = c.id();
                chestFlash = 1f;
                MetaClient.openChest(c.id());
            });
            ShopSection.Priced pr = mode.priced("chest:" + c.id(), System.currentTimeMillis());
            if (pr != null) priceButton(g, pr, have > 0 ? x + 198 : x + 104, by, have > 0 ? w - 208 : w - 114, 22);
            skin.cardOverlay(g, x, y, w, h, rc, flash, 0, time);
            x += w + GAP;
        }
        return y + h + 20;
    }

    // ── розділ ──────────────────────────────────────────────────────────
    private int drawSection(GuiGraphics g, ShopSection s, int y) {
        long now = System.currentTimeMillis();
        List<ShopSection.Priced> list = s.current(mode.id, mode.shop, now);
        list.removeIf(pr -> pr.offer().reward().type() == Reward.Type.COSMETIC && mode.cosmetics.get(pr.offer().reward().id()) == null);
        if (list.isEmpty()) return y;
        String right = s.rotating() ? "ОНОВЛЕННЯ ЧЕРЕЗ " + Preview.timer(s.endsAt(now) - now).toUpperCase() : null;
        y += skin.sectionTitle(g, font, 16, y, 928, s.title().toUpperCase(), right, time) + 4;
        int cols = (932 + GAP) / (CARD_W + GAP);
        int x0 = 14;
        for (int i = 0; i < list.size(); i++) {
            int cx = x0 + (i % cols) * (CARD_W + GAP), cy = y + (i / cols) * (CARD_H + GAP);
            card(g, list.get(i), cx, cy, i);
        }
        return y + ((list.size() + cols - 1) / cols) * (CARD_H + GAP) + 14;
    }

    private void card(GuiGraphics g, ShopSection.Priced pr, int x, int y, int idx) {
        if (y + CARD_H < TOP || y > DH) return;
        ShopOffer o = pr.offer();
        Reward r = o.reward();
        CosmeticDef d = r.type() == Reward.Type.COSMETIC ? mode.cosmetics.get(r.id()) : null;
        MetaProfile p = p();
        boolean owned = d != null && p != null && p.owns(d.id());
        Rarity rar = d != null ? d.rarity() : r.type() == Reward.Type.CHEST && mode.chest(r.id()) != null ? mode.chest(r.id()).rarity() : Rarity.COMMON;
        int rc = rar.color;
        float a = MetaUi.outCubic(MetaUi.clamp01((time - tabTime - .05f - idx * .04f) / .35f));
        if (a <= .02f) return;
        y += Math.round((1 - a) * 14);
        float hv = hv("card" + o.id(), over(x, y, CARD_W, CARD_H) && my >= TOP);
        skin.card(g, x, y, CARD_W, CARD_H, rc, false, hv, time);
        skin.stage(g, x + 3, y + 3, CARD_W - 6, STAGE_H, rc, time, hv);
        // 3D-модель нижче смуги ярликів, щоб знижка/рідкість не лягали на голову
        if (d != null) Preview.item(g, this, d, x + 3, y + 3 + 16, CARD_W - 6, STAGE_H - 16, 160 + (float) Math.sin(time * .9 + idx) * 25 + hv * 30);
        else icon(g, MetaScreens.rewardIcon(mode, r), x + CARD_W / 2 - 24, y + 40, 6, MetaUi.solid(MetaScreens.rewardColor(mode, r)));
        if (pr.discount() > 0) skin.tag(g, font, x + 7, y + 7, "-" + pr.discount() + "%", theme.bad(), true);
        skin.tag(g, font, x + 7, y + STAGE_H - 10, rar.title, rc, true);
        String name = d != null ? d.name() : MetaScreens.rewardText(mode, r);
        MetaUi.text(g, font, MetaUi.fit(font, name, CARD_W - 14), x + 7, y + STAGE_H + 9, MetaUi.solid(theme.text()), false);
        if (d != null) MetaUi.textS(g, font, d.slot().title, x + 7, y + STAGE_H + 21, .75f, MetaUi.solid(theme.dim()), false);
        int by = y + CARD_H - 28;
        if (owned) {
            skin.button(g, font, x + 7, by, CARD_W - 14, 22, "У ТЕБЕ Є", MetaSkin.Kind.SELECTED, 0, false, time);
        } else priceButton(g, pr, x + 7, by, CARD_W - 14, 22);
        skin.cardOverlay(g, x, y, CARD_W, CARD_H, rc, false, hv, time);
    }

    /** Кнопка ціни: тло — кнопка скіна; поверх — іконка валюти, нова ціна, стара закреслена. */
    private void priceButton(GuiGraphics g, ShopSection.Priced pr, int x, int y, int w, int h) {
        ShopOffer o = pr.offer();
        MetaProfile p = p();
        Currency c = mode.currency(o.currency());
        boolean can = p != null && p.wallet.canAfford(o.currency(), pr.price());
        boolean waiting = pending.equals(o.id()) && time - pendingAt < 2f;
        Runnable act = can && !waiting ? () -> { click(); pending = o.id(); pendingAt = time; MetaClient.buy(o.id()); } : null;
        float hv = hv("buy" + o.id(), act != null && zone(x, y, w, h, act));
        skin.button(g, font, x, y, w, h, "", MetaSkin.Kind.NORMAL, hv, act != null, time);
        int col = c == null ? theme.gold() : c.color();
        String price = waiting ? "…" : MetaUi.num(pr.price());
        String old = pr.discount() > 0 ? MetaUi.num(pr.oldPrice()) : "";
        int ow = old.isEmpty() ? 0 : 6 + (int) (font.width(old) * .75f);
        int tx = x + (w - (12 + font.width(price) + ow)) / 2;
        icon(g, c == null ? "coin" : c.icon(), tx, y + (h - 8) / 2, 1, MetaUi.solid(col));
        MetaUi.text(g, font, price, tx + 12, y + (h - 8) / 2, can ? MetaUi.solid(theme.text()) : MetaUi.solid(theme.bad()), false);
        if (!old.isEmpty()) {
            int ox2 = tx + 18 + font.width(price);
            MetaUi.textS(g, font, old, ox2, y + (h - 8) / 2 + 1, .75f, MetaUi.solid(theme.dim()), false);
            g.fill(ox2 - 1, y + h / 2, ox2 + (int) (font.width(old) * .75f) + 1, y + h / 2 + 1, MetaUi.solid(theme.dim()));
        }
    }

    // ── майстерня ───────────────────────────────────────────────────────
    private void drawCraft(GuiGraphics g) {
        MetaProfile p = p();
        panel(g, 14, TOP, 690, 424, false);
        panel(g, 716, TOP, 230, 424, false);
        skin.sectionTitle(g, font, 728, TOP + 10, 206, "СКЛАД ДЕТАЛЕЙ", null, time);
        int my = TOP + 34;
        for (MaterialDef m : mode.materials.values()) {
            int have = p == null ? 0 : p.material(m.id());
            skin.card(g, 726, my, 210, 24, m.rarity().color, false, 0, time);
            icon(g, m.icon(), 733, my + 8, 1, MetaUi.solid(m.rarity().color));
            MetaUi.text(g, font, MetaUi.fit(font, m.name(), 150), 747, my + 8, MetaUi.solid(theme.text()), false);
            MetaUi.textR(g, font, "×" + have, 928, my + 8, have > 0 ? MetaUi.solid(theme.gold()) : MetaUi.solid(theme.dim()), false);
            skin.cardOverlay(g, 726, my, 210, 24, m.rarity().color, false, 0, time);
            my += 28;
            if (my > 460) break;
        }
        MetaUi.textS(g, font, "Деталі випадають за матчі (переможцям — частіше)", 726, 486, .7f, MetaUi.solid(theme.dim()), false);
        MetaUi.textS(g, font, "і видаються в пропуску сезону.", 726, 494, .7f, MetaUi.solid(theme.dim()), false);

        if (mode.recipes.isEmpty()) { MetaUi.textC(g, font, "Рецептів поки немає", 359, 290, MetaUi.solid(theme.dim()), false); return; }
        int per = 4;
        int pages = (mode.recipes.size() + per - 1) / per;
        craftPage = Math.max(0, Math.min(craftPage, pages - 1));
        for (int i = craftPage * per; i < Math.min(mode.recipes.size(), (craftPage + 1) * per); i++) {
            int k = i - craftPage * per;
            recipeRow(g, mode.recipes.get(i), 24, TOP + 10 + k * 100, 670, 92, k);
        }
        if (pages > 1) MetaUi.textC(g, font, (craftPage + 1) + " / " + pages + "  ·  колесо — гортати", 359, 496, MetaUi.solid(theme.dim()), false);
    }

    private void recipeRow(GuiGraphics g, Recipe r, int x, int y, int w, int h, int idx) {
        MetaProfile p = p();
        float a = MetaUi.outCubic(MetaUi.clamp01((time - tabTime - idx * .06f) / .3f));
        x += Math.round((1 - a) * -24);
        Reward out = r.output();
        CosmeticDef d = out.type() == Reward.Type.COSMETIC ? mode.cosmetics.get(out.id()) : null;
        boolean owned = d != null && p != null && p.owns(d.id());
        int rc = d != null ? d.rarity().color : MetaScreens.rewardColor(mode, out);
        skin.card(g, x, y, w, h, rc, false, 0, time);
        skin.stage(g, x + 6, y + 6, 80, 80, rc, time, 0);
        if (d != null) Preview.item(g, this, d, x + 6, y + 6, 80, 80, 160 + (float) Math.sin(time + idx) * 20);
        else icon(g, MetaScreens.rewardIcon(mode, out), x + 22, y + 22, 6, MetaUi.solid(rc));
        MetaUi.textS(g, font, MetaUi.fit(font, d != null ? d.name() : MetaScreens.rewardText(mode, out), 260), x + 96, y + 12, 1.15f, MetaUi.solid(theme.text()), false);
        if (d != null) MetaUi.textS(g, font, d.rarity().title + " · " + d.slot().title, x + 96, y + 28, .8f, MetaUi.solid(rc), false);
        int mx2 = x + 96, my2 = y + 46;
        boolean enough = true;
        for (Map.Entry<String, Integer> e : r.materials().entrySet()) {
            MaterialDef m = mode.materials.get(e.getKey());
            int have = p == null ? 0 : p.material(e.getKey());
            boolean ok = have >= e.getValue();
            enough &= ok;
            mx2 += skin.chip(g, font, mx2, my2, (m == null ? e.getKey() : m.name()) + " " + have + "/" + e.getValue(),
                    m == null ? null : m.icon(), ok ? theme.good() : theme.bad()) + 6;
        }
        if (!r.currency().isEmpty() && r.cost() > 0) {
            Currency c = mode.currency(r.currency());
            boolean ok = p != null && p.wallet.canAfford(r.currency(), r.cost());
            enough &= ok;
            MetaUi.text(g, font, "+ " + MetaUi.num(r.cost()) + " " + (c == null ? r.currency() : c.name()), x + 96, y + 70,
                    ok ? MetaUi.solid(theme.gold()) : MetaUi.solid(theme.bad()), false);
        }
        int bx = x + w - 150, by = y + h / 2 - 13;
        if (owned) skin.button(g, font, bx, by, 138, 26, "У ТЕБЕ Є", MetaSkin.Kind.SELECTED, 0, false, time);
        else if (enough) primary(g, "craft" + r.id(), bx, by, 138, 26, "СКРАФТИТИ", () -> { click(); MetaClient.craft(r.id()); });
        else button(g, "craft" + r.id(), bx, by, 138, 26, "НЕ ВИСТАЧАЄ", MetaSkin.Kind.DANGER, null);
        skin.cardOverlay(g, x, y, w, h, rc, false, 0, time);
    }

    @Override
    public boolean mouseScrolled(double rx, double ry, double amount) {
        if (craft) { craftPage += amount < 0 ? 1 : -1; if (craftPage < 0) craftPage = 0; tabTime = time - .3f; }
        else scrollGoal -= (float) amount * 60f;
        return true;
    }

    @Override public void profileChanged() { pending = ""; }
}
