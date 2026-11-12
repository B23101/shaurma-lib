package dev.shaurmalib.meta.mc.screen;

import dev.shaurmalib.meta.client.MetaClient;
import dev.shaurmalib.meta.cosmetics.CosmeticDef;
import dev.shaurmalib.meta.economy.Reward;
import dev.shaurmalib.meta.economy.RewardTrack;
import dev.shaurmalib.meta.economy.UpgradeDef;
import dev.shaurmalib.meta.mc.MetaScreens;
import dev.shaurmalib.meta.mc.skin.MetaSkin;
import dev.shaurmalib.meta.mc.ui.MetaIcons;
import dev.shaurmalib.meta.mc.ui.MetaUi;
import dev.shaurmalib.meta.profile.MetaProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Пропуск сезону (лише безкоштовний — платної гілки немає) і покращення. Вигляд — скін режиму.
 * Шапка: «СЕЗОН · до 30 листопада», назва, «N/28» і сегментована смужка. Ряд карток-ступенів (колесо,
 * перетягування або стрілки), перша плитка — щоденний подарунок із таймером. Під картками — номери ступенів;
 * поточний підсвічений. Відкрита незабрана картка має «Забрати»; праворуч — твій персонаж у косметиці.
 */
public class TrackScreen extends MetaScreen {

    private boolean upgrades;
    private float offset = Float.NaN, offsetGoal;
    private boolean dragging;
    private final List<float[]> sparks = new ArrayList<>();
    private final Random rnd = new Random();
    private final Map<Integer, Float> claimedAt = new HashMap<>();

    private static final int TOP = 84, TILE = 108, GAP = 8, ROW_H = 122;
    private static final int VIEW_X = 112, VIEW_W = 628;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM", new Locale("uk"));

    public TrackScreen(Screen parent, boolean upgrades) {
        super("Пропуск", parent);
        this.upgrades = upgrades;
    }

    @Override protected String section() { return "track"; }

    @Override
    protected void draw(GuiGraphics g) {
        header(g, upgrades ? "ПОКРАЩЕННЯ" : "ПРОПУСК", upgrades ? "ОЧКИ ЗА НОВІ РІВНІ" : "НАГОРОДИ СЕЗОНУ");
        MetaProfile p = me();
        button(g, "tTrack", 446, 54, 120, 20, "ПРОПУСК", !upgrades, () -> { upgrades = false; click(); });
        button(g, "tUp", 572, 54, 140, 20, "ПОКРАЩЕННЯ" + (p != null && p.upgradePoints > 0 ? " · " + p.upgradePoints : ""), upgrades,
                () -> { upgrades = true; click(); });
        if (upgrades) drawUpgrades(g); else drawPass(g);
        drawSparks(g);
    }

    private int tier(MetaProfile p) {
        long sxp = p.season.equals(mode.track.id()) ? p.seasonXp : 0;
        return mode.track.tier(sxp, p.level(mode));
    }

    // ── пропуск ─────────────────────────────────────────────────────────
    private void drawPass(GuiGraphics g) {
        MetaProfile p = me();
        if (p == null) return;
        RewardTrack t = mode.track;
        List<Integer> tiers = new ArrayList<>(t.steps().keySet());
        int bx = 14, by = TOP, bw = 932, bh = 340;
        panel(g, bx, by, bw, bh, true);

        String season = "СЕЗОН" + (t.endsAt() > 0 ? " · ДО " + DATE.format(Instant.ofEpochMilli(t.endsAt()).atZone(ZoneId.systemDefault())).toUpperCase() : "");
        skin.header(g, font, bx + 16, by + 12, season, t.title().toUpperCase(), time);
        int tier = tier(p), max = Math.max(1, t.maxTier());
        MetaUi.textS(g, font, String.valueOf(tier), bx + 16, by + 60, 1.6f, MetaUi.solid(theme.text()), false);
        MetaUi.text(g, font, "/" + max, bx + 19 + (int) (font.width(String.valueOf(tier)) * 1.6f), by + 65, MetaUi.solid(theme.dim()), false);
        int segX = bx + 64, segW = 420, segs = Math.min(max, 40);
        float prog = t.seasonal() ? t.tierProgress(p.seasonXp) : mode.curve.progress(p.totalXp());
        for (int k = 0; k < segs; k++) {
            int sx = segX + k * segW / segs, ex = segX + (k + 1) * segW / segs - 2;
            int kTier = 1 + k * max / segs;
            boolean milestone = t.rewards(kTier).stream().anyMatch(r -> r.type() == Reward.Type.COSMETIC || r.type() == Reward.Type.CHEST);
            float f = kTier < tier ? 1f : kTier == tier ? prog : 0f;
            skin.bar(g, sx, by + 66, ex - sx, 5, f, milestone ? theme.gold() : theme.accent(), time);
        }
        if (t.seasonal()) MetaUi.textS(g, font, MetaUi.num(p.seasonXp % t.xpPerTier()) + " / " + MetaUi.num(t.xpPerTier()) + " XP до наступного ступеня",
                segX, by + 76, .75f, MetaUi.solid(theme.dim()), false);
        int claimable = t.claimable(tier, p.claimed).size();
        primary(g, "claimAll", bx + 540, by + 24, 180, 28, claimable > 0 ? "ЗАБРАТИ ВСЕ (" + claimable + ")" : "УСЕ ЗАБРАНО", claimable > 0 ? () -> {
            click();
            for (int lv : t.claimable(tier, p.claimed)) { claimedAt.put(lv, time); MetaClient.claim(lv); }
            burst(630, by + 38, theme.gold(), 50);
        } : null);

        int rowY = by + 104;
        dailyTile(g, p, bx + 14, rowY, 84, ROW_H);

        if (Float.isNaN(offset)) {
            int focus = 0;
            for (int i = 0; i < tiers.size(); i++) {
                int lv = tiers.get(i);
                if (lv <= tier && !p.claimed.contains(lv)) { focus = i; break; }
                if (lv <= tier) focus = i;
            }
            offsetGoal = Math.max(0, focus * (TILE + GAP) - TILE);
            offset = offsetGoal;
        }
        float maxOff = Math.max(0, tiers.size() * (TILE + GAP) - VIEW_W);
        offsetGoal = Math.max(0, Math.min(maxOff, offsetGoal));
        offset = MetaUi.approach(offset, offsetGoal, 10f, dt);
        int lineY = rowY + ROW_H + 24;
        g.enableScissor((int) (ox + VIEW_X * ui), (int) (oy + (rowY - 4) * ui), (int) (ox + (VIEW_X + VIEW_W) * ui), (int) (oy + (lineY + 12) * ui));
        int x0 = VIEW_X + 4 - Math.round(offset);
        int reached = -1;
        for (int i = 0; i < tiers.size(); i++) if (tiers.get(i) <= tier) reached = i;
        g.fill(VIEW_X, lineY - 1, VIEW_X + VIEW_W, lineY + 1, MetaUi.a(theme.border(), .9f));
        if (reached >= 0) g.fill(VIEW_X, lineY - 1, x0 + reached * (TILE + GAP) + TILE / 2, lineY + 1, MetaUi.solid(theme.accent()));
        for (int i = 0; i < tiers.size(); i++) {
            int x = x0 + i * (TILE + GAP);
            if (x + TILE < VIEW_X || x > VIEW_X + VIEW_W) continue;
            int lv = tiers.get(i);
            tile(g, p, lv, x, rowY, tier, i);
            int cx = x + TILE / 2;
            boolean cur = i == reached;
            String n = String.valueOf(lv);
            int w = font.width(n) + 12;
            skin.button(g, font, cx - w / 2, lineY - 8, w, 16, n, cur ? MetaSkin.Kind.SELECTED : MetaSkin.Kind.NORMAL, 0, true, time);
        }
        g.disableScissor();
        if (offsetGoal < maxOff) button(g, "next", VIEW_X + VIEW_W + 4, rowY + ROW_H / 2 - 14, 18, 28, ">", false, () -> { offsetGoal += TILE + GAP; click(); });
        if (offsetGoal > 0) button(g, "prev", VIEW_X - 22, rowY + ROW_H / 2 - 14, 18, 28, "<", false, () -> { offsetGoal -= TILE + GAP; click(); });

        if (minecraft != null && minecraft.player != null) {
            Preview.figure(g, this, 846, by + bh - 16, 90, 200 + (float) Math.sin(time * .5) * 14,
                    p.wardrobe.look(mode.variants.get(0)), 768, by + 2, 176, bh - 4, false, 0);
        }

        panel(g, 14, 434, 932, 74, false);
        skin.levelBadge(g, font, 50, 471, 22, p.level(mode), theme.accent(), 0, time);
        MetaUi.text(g, font, "РІВЕНЬ ГРАВЦЯ " + p.level(mode) + "   ·   " + MetaUi.num(mode.curve.intoLevel(p.totalXp())) + " / "
                + MetaUi.num(mode.curve.needed(p.level(mode))) + " XP", 86, 452, MetaUi.solid(theme.text()), false);
        skin.bar(g, 86, 465, 300, 4, mode.curve.progress(p.totalXp()), theme.accent(), time);
        MetaUi.textS(g, font, "Кожен матч дає досвід і в рівень, і в пропуск. Пропуск безкоштовний для всіх.", 86, 476, .8f, MetaUi.solid(theme.dim()), false);
        MetaUi.textS(g, font, "колесо / перетягування — гортати ступені", 86, 488, .75f, MetaUi.a(theme.dim(), .7f), false);
    }

    private void dailyTile(GuiGraphics g, MetaProfile p, int x, int y, int w, int h) {
        RewardTrack t = mode.track;
        boolean has = !t.daily().isEmpty();
        long left = p.lastDaily + t.dailyCooldownMs() - System.currentTimeMillis();
        boolean ready = has && left <= 0;
        skin.card(g, x, y, w, h, theme.gold(), ready, 0, time);
        MetaUi.textC(g, font, "ПОДАРУНОК", x + w / 2, y + 8, MetaUi.solid(theme.dim()), false);
        float pulse = ready ? .5f + .5f * (float) Math.sin(time * 4) : 0;
        Preview.itemIcon(g, "minecraft:chest", x + w / 2f, y + 46 - pulse * 3, 3.2f);
        if (has) {
            if (ready) primary(g, "daily", x + 6, y + h - 28, w - 12, 22, "ЗАБРАТИ", () -> {
                click();
                burst(x + w / 2f, y + 40, theme.gold(), 40);
                MetaClient.daily();
            });
            else {
                long s = Math.max(0, left / 1000);
                skin.button(g, font, x + 6, y + h - 28, w - 12, 22, String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60),
                        MetaSkin.Kind.NORMAL, 0, false, time);
            }
        }
        skin.cardOverlay(g, x, y, w, h, theme.gold(), ready, 0, time);
    }

    private void tile(GuiGraphics g, MetaProfile p, int lv, int x, int y, int tier, int i) {
        boolean open = lv <= tier, claimed = p.claimed.contains(lv), ready = open && !claimed;
        List<Reward> rewards = mode.track.rewards(lv);
        Reward main = rewards.isEmpty() ? null : rewards.get(0);
        float hv = hv("tile" + lv, over(x, y, TILE, ROW_H));
        Float at = claimedAt.get(lv);
        float sx = at == null ? 1 : Math.max(.05f, Math.abs((float) Math.cos(MetaUi.clamp01((time - at) / .5f) * Math.PI)));
        int cx = x + TILE / 2, cy = y + ROW_H / 2;
        int rc = main == null ? theme.border() : MetaScreens.rewardColor(mode, main);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(sx, 1, 1);
        g.pose().translate(-cx, -cy, 0);
        skin.card(g, x, y, TILE, ROW_H, ready ? theme.accent() : rc, ready, hv, time);
        int stageH = ROW_H - 34;
        skin.stage(g, x + 3, y + 3, TILE - 6, stageH, open ? rc : theme.border(), time, hv);
        if (main != null) {
            CosmeticDef d = main.type() == Reward.Type.COSMETIC ? mode.cosmetics.get(main.id()) : null;
            if (d != null && sx > .9f) Preview.item(g, this, d, x + 3, y + 3, TILE - 6, stageH, 160 + (float) Math.sin(time + i) * 20 + hv * 25);
            else if (main.type() == Reward.Type.CHEST && mode.chest(main.id()) != null)
                Preview.itemIcon(g, mode.chest(main.id()).model(), cx, y + 46 + (float) Math.sin(time * 2 + i) * 2, 3.2f);
            else icon(g, MetaScreens.rewardIcon(mode, main), cx - 16, y + 30 + (int) (Math.sin(time * 2 + i) * 2), 4,
                    open ? MetaUi.solid(rc) : MetaUi.a(rc, .45f));
            if (!ready) {
                String label = label(main) + (rewards.size() > 1 ? " +" + (rewards.size() - 1) : "");
                MetaUi.textC(g, font, MetaUi.fit(font, label, TILE - 14), cx, y + ROW_H - 21, claimed ? MetaUi.solid(theme.good()) : MetaUi.solid(theme.text()), false);
            }
        }
        if (!open) MetaUi.lock(g, x + TILE - 14, y + 7, MetaUi.a(theme.dim(), .9f));
        if (claimed) { g.fill(x + 3, y + 3, x + TILE - 3, y + 3 + stageH, MetaUi.a(0, .4f)); MetaUi.check(g, x + 8, y + 8, MetaUi.solid(theme.good())); }
        skin.cardOverlay(g, x, y, TILE, ROW_H, ready ? theme.accent() : rc, ready, hv, time);
        g.pose().popPose();
        if (ready && x >= VIEW_X && x + TILE <= VIEW_X + VIEW_W) {
            primary(g, "claim" + lv, x + 6, y + ROW_H - 28, TILE - 12, 22, "ЗАБРАТИ", () -> {
                click();
                claimedAt.put(lv, time);
                burst(cx, cy, theme.gold(), 50);
                MetaClient.claim(lv);
            });
        }
    }

    /** Короткий підпис плитки: «25», «Звичайна скриня», «Рідкісний». */
    private String label(Reward r) {
        return switch (r.type()) {
            case CURRENCY, MATERIAL, UPGRADE_POINTS -> MetaUi.num(r.amount());
            case CHEST -> { var c = mode.chest(r.id()); yield c == null ? "Скриня" : c.name(); }
            case COSMETIC -> { var d = mode.cosmetics.get(r.id()); yield d == null ? r.id() : d.rarity().title; }
        };
    }

    // ── покращення ──────────────────────────────────────────────────────
    private void drawUpgrades(GuiGraphics g) {
        MetaProfile p = me();
        if (p == null) return;
        panel(g, 14, TOP, 932, 424, true);
        icon(g, "upgrade", 30, TOP + 18, 2, MetaUi.solid(theme.accent()));
        MetaUi.textS(g, font, String.valueOf(p.upgradePoints), 54, TOP + 16, 1.8f, MetaUi.solid(theme.text()), false);
        MetaUi.text(g, font, "ОЧОК У СПІЛЬНОМУ ЗАПАСІ", 60 + (int) (font.width(String.valueOf(p.upgradePoints)) * 1.8f), TOP + 22, MetaUi.solid(theme.dim()), false);
        skin.divider(g, 28, TOP + 42, 904);
        if (mode.upgrades.isEmpty()) {
            MetaUi.textC(g, font, "Покращень у цьому режимі ще немає — очки накопичуються.", 480, 290, MetaUi.solid(theme.dim()), false);
            return;
        }
        List<String> groups = new ArrayList<>();
        for (UpgradeDef u : mode.upgrades) if (!groups.contains(u.group())) groups.add(u.group());
        int col = 0;
        for (String grp : groups) {
            final int gx = 28 + col * 306;
            skin.sectionTitle(g, font, gx, TOP + 54, 296, grp.toUpperCase(), null, time);
            int y = TOP + 76;
            for (UpgradeDef u : mode.upgrades) {
                if (!u.group().equals(grp)) continue;
                int rank = p.upgrade(u.id());
                boolean max = rank >= u.maxRank(), can = !max && p.upgradePoints >= u.costPerRank();
                skin.card(g, gx, y, 296, 72, theme.accent(), rank > 0, 0, time);
                icon(g, u.icon(), gx + 12, y + 14, 2, MetaUi.solid(theme.accent()));
                MetaUi.text(g, font, MetaUi.fit(font, u.name(), 170), gx + 44, y + 9, MetaUi.solid(theme.text()), false);
                int ly = y + 21;
                for (String line : MetaUi.wrap(font, u.description(), (int) (160 / .75f))) {
                    MetaUi.textS(g, font, line, gx + 44, ly, .75f, MetaUi.solid(theme.dim()), false);
                    ly += 8;
                    if (ly > y + 44) break;
                }
                for (int k = 0; k < u.maxRank(); k++) skin.bar(g, gx + 44 + k * 14, y + 56, 10, 6, k < rank ? 1f : 0f, theme.accent(), time);
                final int byy = y + 56;
                if (max) MetaUi.textR(g, font, "МАКС.", gx + 286, y + 54, MetaUi.solid(theme.good()), false);
                else button(g, "up" + u.id(), gx + 204, y + 46, 84, 20, "+1 · " + u.costPerRank() + " оч.", false,
                        can ? () -> { click(); burst(gx + 246, byy, theme.accent(), 30); MetaClient.upgrade(u.id()); } : null);
                skin.cardOverlay(g, gx, y, 296, 72, theme.accent(), rank > 0, 0, time);
                y += 80;
                if (y > 440) break;
            }
            if (++col >= 3) break;
        }
    }

    // ── іскри / ввід ────────────────────────────────────────────────────
    private void burst(float x, float y, int rgb, int n) {
        if (!skin.sparks()) return;
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, sp = 40 + rnd.nextDouble() * 160;
            sparks.add(new float[]{x, y, (float) (Math.cos(a) * sp), (float) (Math.sin(a) * sp - 40), .6f + rnd.nextFloat() * .7f, rgb});
        }
    }

    private void drawSparks(GuiGraphics g) {
        for (int i = sparks.size() - 1; i >= 0; i--) {
            float[] s = sparks.get(i);
            s[4] -= dt;
            if (s[4] <= 0) { sparks.remove(i); continue; }
            s[3] += 80 * dt;
            s[0] += s[2] * dt;
            s[1] += s[3] * dt;
            g.fill((int) s[0], (int) s[1], (int) s[0] + 2, (int) s[1] + 2, MetaUi.a((int) s[5], Math.min(1f, s[4] * 1.5f)));
        }
    }

    @Override
    public boolean mouseScrolled(double rx, double ry, double amount) {
        offsetGoal -= (float) amount * (TILE + GAP);
        return true;
    }

    @Override
    public boolean mouseClicked(double rx, double ry, int button) {
        if (super.mouseClicked(rx, ry, button)) return true;
        updateMouse(rx, ry);
        if (!upgrades && over(VIEW_X, TOP + 100, VIEW_W, ROW_H + 40)) { dragging = true; return true; }
        return false;
    }

    @Override
    public boolean mouseDragged(double rx, double ry, int button, double dx, double dy) {
        if (dragging) { offsetGoal -= (float) (dx / ui); offset = offsetGoal; return true; }
        return super.mouseDragged(rx, ry, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double rx, double ry, int button) {
        dragging = false;
        return super.mouseReleased(rx, ry, button);
    }
}
