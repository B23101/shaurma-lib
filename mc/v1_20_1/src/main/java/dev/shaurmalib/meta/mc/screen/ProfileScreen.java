package dev.shaurmalib.meta.mc.screen;

import com.mojang.authlib.GameProfile;
import dev.shaurmalib.meta.client.MetaClient;
import dev.shaurmalib.meta.mc.render.CosmeticRenderer;
import dev.shaurmalib.meta.mc.ui.MetaIcons;
import dev.shaurmalib.meta.mc.ui.MetaUi;
import dev.shaurmalib.meta.profile.MetaProfile;
import dev.shaurmalib.meta.progress.RankLadder;
import dev.shaurmalib.meta.stats.StatDef;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import dev.shaurmalib.meta.cosmetics.CosmeticSlot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Профіль гравця (власний або чужий): 3D-модель у його косметиці, емблема рівня, ранги всіх драбин
 * режиму, смужка XP, колекція, статистика розділами (за {@link StatDef} режиму). Вигляд — скін режиму.
 */
public class ProfileScreen extends MetaScreen {

    private static final int TOP = 84;

    private final UUID target;
    private final String targetName;
    private AbstractClientPlayer model;
    private float yaw = 200;
    private boolean dragging;
    private int group;
    private float shownXp = -1;

    public ProfileScreen(Screen parent, UUID target, String name) {
        super("Профіль", parent);
        this.target = target;
        this.targetName = name;
        MetaClient.requestProfile(target);
    }

    @Override protected String section() { return "profile"; }

    private boolean self() { return target == null || (minecraft != null && minecraft.player != null && target.equals(minecraft.player.getUUID())); }

    private MetaProfile profile() { return self() ? MetaClient.self() : MetaClient.profile(target); }

    @Override
    protected void init() {
        super.init();
        if (model != null || minecraft == null || minecraft.level == null || minecraft.player == null) return;
        if (self()) { model = minecraft.player; return; }
        var p = minecraft.level.getPlayerByUUID(target);
        if (p instanceof AbstractClientPlayer acp) { model = acp; return; }
        model = new RemotePlayer(minecraft.level, new GameProfile(target, targetName == null ? "?" : targetName)) {
            @Override public boolean isModelPartShown(PlayerModelPart part) { return true; }
        };
    }

    private List<String> groups() {
        List<String> out = new ArrayList<>();
        if (mode != null) for (StatDef s : mode.stats) if (!out.contains(s.group())) out.add(s.group());
        return out;
    }

    @Override
    protected void draw(GuiGraphics g) {
        MetaProfile p = profile();
        String name = p != null && !p.name.isEmpty() ? p.name : (targetName != null ? targetName : minecraft.player.getGameProfile().getName());
        header(g, "ПРОФІЛЬ", self() ? "ТВІЙ ПРОГРЕС" : "ГРАВЕЦЬ · " + name.toUpperCase());
        if (p == null) {
            MetaUi.textC(g, font, "Завантаження профілю…", 480, 260, MetaUi.solid(theme.dim()), false);
            return;
        }
        drawLeft(g, p, name);
        drawRight(g, p);
    }

    private void drawLeft(GuiGraphics g, MetaProfile p, String name) {
        int x = 14, y = TOP, w = 300, h = 424;
        panel(g, x, y, w, h, true);
        int vx = x + 10, vy = y + 10, vw = w - 20, vh = 214;
        skin.stage(g, vx, vy, vw, vh, theme.accent(), time, 0);
        if (!dragging) yaw += dt * 12f;
        if (model != null) {
            g.enableScissor((int) (ox + vx * ui), (int) (oy + vy * ui), (int) (ox + (vx + vw) * ui), (int) (oy + (vy + vh) * ui));
            boolean preview = !self() && MetaClient.look(target) == null && p.wardrobe != null && mode != null;
            if (preview) {
                CosmeticRenderer.preview = p.wardrobe.look(mode.variants.get(0));
                CosmeticRenderer.drawingPreview = true;
                CosmeticRenderer.previewYaw = yaw;
            }
            float by = model.yBodyRot, byo = model.yBodyRotO, hy = model.yHeadRot, hyo = model.yHeadRotO, yr = model.getYRot(), xr = model.getXRot();
            model.yBodyRot = model.yBodyRotO = model.yHeadRot = model.yHeadRotO = 0;
            model.setYRot(0);
            model.setXRot(0);
            try {
                Preview.render(g, vx + vw / 2, vy + vh - 16, 84, yaw, model,
                        preview && CosmeticRenderer.preview != null ? CosmeticRenderer.preview.get(CosmeticSlot.AURA) : null,
                        false, vw);
            } finally {
                model.yBodyRot = by; model.yBodyRotO = byo; model.yHeadRot = hy; model.yHeadRotO = hyo;
                model.setYRot(yr);
                model.setXRot(xr);
                CosmeticRenderer.drawingPreview = false;
                CosmeticRenderer.preview = null;
                g.disableScissor();
            }
        }
        zone(vx, vy, vw, vh, () -> dragging = true);

        int level = p.level(mode);
        int by = y + 234;
        skin.levelBadge(g, font, x + 38, by + 24, 22, level, theme.accent(), 0, time);
        MetaUi.textS(g, font, MetaUi.fit(font, name, 140), x + 72, by + 8, 1.3f, MetaUi.solid(theme.text()), false);
        RankLadder lad = mode.ladder(mode.defaultLadder);
        if (lad != null) {
            RankLadder.Rank rk = lad.ofLevel(level);
            MetaUi.text(g, font, rk.title().toUpperCase(), x + 72, by + 26, MetaUi.solid(rk.color()), false);
        }
        long total = p.totalXp();
        if (shownXp < 0) shownXp = mode.curve.startOf(level);
        shownXp = MetaUi.approach(shownXp, total, 3f, dt);
        long sx = (long) shownXp;
        int lvlShown = mode.curve.levelOf(sx);
        int bx = x + 14, bw = w - 28, bby = by + 54;
        skin.bar(g, bx, bby, bw, 6, mode.curve.progress(sx), theme.accent(), time);
        MetaUi.textS(g, font, MetaUi.num(mode.curve.intoLevel(sx)) + " / " + MetaUi.num(mode.curve.needed(lvlShown)) + " XP", bx, bby + 10, .8f, MetaUi.solid(theme.dim()), false);
        MetaUi.textR(g, font, "РІВЕНЬ " + (lvlShown + 1), bx + bw, bby + 10, MetaUi.solid(theme.dim()), false);

        int ry = bby + 26;
        for (RankLadder l : mode.ladders.values()) {
            RankLadder.Rank rk = l.ofLevel(level);
            skin.card(g, x + 14, ry, w - 28, 18, rk.color(), false, 0, time);
            MetaUi.text(g, font, l.name().toUpperCase(), x + 20, ry + 5, MetaUi.solid(theme.dim()), false);
            MetaUi.textR(g, font, rk.title(), x + w - 20, ry + 5, MetaUi.solid(rk.color()), false);
            skin.cardOverlay(g, x + 14, ry, w - 28, 18, rk.color(), false, 0, time);
            ry += 21;
            if (ry > y + h - 50) break;
        }
        int owned = 0, all = 0;
        for (var d : mode.cosmetics.all()) { if (!d.hidden() || p.owns(d.id())) all++; if (p.owns(d.id())) owned++; }
        int wy = y + h - 36;
        skin.divider(g, x + 14, wy - 6, w - 28);
        icon(g, "box", x + 16, wy + 2, 1, MetaUi.solid(theme.gold()));
        MetaUi.text(g, font, "Колекція: " + owned + " / " + all, x + 28, wy + 2, MetaUi.solid(theme.text()), false);
        if (self() && !mode.materials.isEmpty()) {
            int mats = 0;
            for (int v : p.materials.values()) mats += v;
            icon(g, "thread", x + 16, wy + 16, 1, MetaUi.solid(theme.dim()));
            MetaUi.text(g, font, "Деталей: " + mats + "   ·   Очок покращень: " + p.upgradePoints, x + 28, wy + 16, MetaUi.solid(theme.dim()), false);
        }
    }

    private void drawRight(GuiGraphics g, MetaProfile p) {
        int x = 326, y = TOP, w = 620, h = 424;
        panel(g, x, y, w, h, false);
        long matches = p.stats.get("meta.matches"), wins = p.stats.get("meta.wins");
        String[][] top = {
                {"МАТЧІВ", MetaUi.num(matches), "flag"}, {"ПЕРЕМОГ", MetaUi.num(wins), "trophy"},
                {"ВІДСОТОК ПЕРЕМОГ", matches == 0 ? "—" : Math.round(wins * 100.0 / matches) + "%", "star"},
                {"НАЙКРАЩА СЕРІЯ", MetaUi.num(p.stats.get("meta.bestStreak")), "bolt"},
                {"У ГРІ", StatDef.formatSeconds(p.stats.get("meta.playtime")), "clock"}};
        int cw = (w - 20 - 4 * 8) / 5;
        for (int i = 0; i < top.length; i++) {
            int cx = x + 10 + i * (cw + 8), cy = y + 12;
            skin.card(g, cx, cy, cw, 50, theme.accent(), false, 0, time);
            icon(g, top[i][2], cx + 8, cy + 8, 1, MetaUi.solid(theme.accent()));
            MetaUi.textS(g, font, MetaUi.fit(font, top[i][0], (int) ((cw - 24) / .75f)), cx + 20, cy + 9, .75f, MetaUi.solid(theme.dim()), false);
            MetaUi.textS(g, font, top[i][1], cx + 8, cy + 26, 1.4f, MetaUi.solid(theme.text()), false);
            skin.cardOverlay(g, cx, cy, cw, 50, theme.accent(), false, 0, time);
        }
        List<String> gs = groups();
        if (gs.isEmpty()) return;
        group = Math.max(0, Math.min(group, gs.size() - 1));
        int tx = x + 10, ty = y + 74;
        for (int k = 0; k < gs.size(); k++) {
            String gname = gs.get(k).toUpperCase();
            int tw = font.width(gname) + 24;
            final int kk = k;
            button(g, "grp" + k, tx, ty, tw, 20, gname, k == group, () -> { group = kk; click(); });
            tx += tw + 6;
        }
        skin.divider(g, x + 10, ty + 26, w - 20);
        String grp = gs.get(group);
        List<StatDef> list = new ArrayList<>();
        for (StatDef d : mode.stats) if (d.group().equals(grp)) list.add(d);
        int cols = 3, gw = (w - 20 - (cols - 1) * 8) / cols, gh = 40;
        for (int k = 0; k < list.size(); k++) {
            StatDef d = list.get(k);
            int cx = x + 10 + (k % cols) * (gw + 8), cy = ty + 34 + (k / cols) * (gh + 8);
            if (cy + gh > y + h - 8) break;
            skin.card(g, cx, cy, gw, gh, theme.accent(), false, 0, time);
            icon(g, d.icon(), cx + 9, cy + 12, 2, MetaUi.solid(theme.accent()));
            MetaUi.textS(g, font, MetaUi.fit(font, d.name().toUpperCase(), (int) ((gw - 44) / .75f)), cx + 36, cy + 8, .75f, MetaUi.solid(theme.dim()), false);
            MetaUi.text(g, font, d.format(d.value(p.stats)), cx + 36, cy + 22, MetaUi.solid(theme.text()), false);
            skin.cardOverlay(g, cx, cy, gw, gh, theme.accent(), false, 0, time);
        }
    }

    @Override
    public boolean mouseDragged(double rx, double ry, int button, double dx, double dy) {
        if (dragging) { yaw += (float) (dx / ui) * .9f; return true; }
        return super.mouseDragged(rx, ry, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double rx, double ry, int button) {
        dragging = false;
        return super.mouseReleased(rx, ry, button);
    }
}
