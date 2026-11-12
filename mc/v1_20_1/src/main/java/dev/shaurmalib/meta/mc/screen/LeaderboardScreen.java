package dev.shaurmalib.meta.mc.screen;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.shaurmalib.meta.client.MetaClient;
import dev.shaurmalib.meta.io.J;
import dev.shaurmalib.meta.mc.MetaScreens;
import dev.shaurmalib.meta.mc.ui.MetaIcons;
import dev.shaurmalib.meta.mc.ui.MetaUi;
import dev.shaurmalib.meta.stats.StatDef;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Лідерборди режиму: «Рівень» + кожна {@link StatDef} із {@code leaderboard=true}. Подіум для трійки,
 * далі таблиця; власний рядок підсвічений. Клік по рядку — профіль гравця. Сервер кешує топ 20 с.
 */
public class LeaderboardScreen extends MetaScreen {

    private final List<String[]> tabs = new ArrayList<>();
    private int tab;
    private int scroll;
    private final Map<String, ResourceLocation> skins = new HashMap<>();
    private float tabTime;

    public LeaderboardScreen(Screen parent) {
        super("Лідери", parent);
        tabs.add(new String[]{"level", "Рівень", "star"});
        if (mode != null) for (StatDef s : mode.stats) if (s.leaderboard()) tabs.add(new String[]{s.key(), s.name(), s.icon()});
        MetaClient.requestLeaderboard(tabs.get(0)[0]);
    }

    @Override protected String section() { return "leaderboard"; }

    @Override
    protected void draw(GuiGraphics g) {
        header(g, "ЛІДЕРИ", "НАЙКРАЩІ ГРАВЦІ СЕРВЕРА");
        // вкладки зліва
        panel(g, 14, 84, 200, 424, false);
        for (int i = 0; i < tabs.size(); i++) {
            String[] t = tabs.get(i);
            final int k = i;
            int y = 96 + i * 30;
            if (y > 480) break;
            boolean act = i == tab;
            float hv = hv("tab" + i, zone(22, y, 184, 24, () -> {
                if (tab != k) { tab = k; scroll = 0; tabTime = time; click(); MetaClient.requestLeaderboard(tabs.get(k)[0]); }
            }));
            skin.tab(g, font, 22, y, 184, 24, MetaUi.fit(font, t[1].toUpperCase(), 150), t[2], act, hv);
        }

        String key = tabs.get(tab)[0];
        List<Map<String, Object>> rows = MetaClient.board(key);
        panel(g, 226, 84, 720, 424, true);
        if (rows == null) { MetaUi.textC(g, font, "Завантаження…", 586, 260, MetaUi.solid(theme.dim()), false); return; }
        if (rows.isEmpty()) { MetaUi.textC(g, font, "Поки що ніхто не грав", 586, 260, MetaUi.solid(theme.dim()), false); return; }
        StatDef def = mode.stat(key);
        UUID me = minecraft.player == null ? null : minecraft.player.getUUID();

        // подіум
        int[] order = {1, 0, 2};
        int[] heights = {70, 90, 56};
        int[] colors = {0xC0C6D0, theme.gold(), 0xD08A4A};
        for (int i = 0; i < 3; i++) {
            int idx = order[i];
            if (idx >= rows.size()) continue;
            Map<String, Object> r = rows.get(idx);
            float a = MetaUi.outBack(MetaUi.clamp01((time - tabTime - .1f - i * .1f) / .45f));
            int cx = 446 + i * 140, base = 236, ph = Math.round(heights[i] * a);
            skin.card(g, cx - 55, base - ph, 110, ph, colors[i], idx == 0, 0, time);
            skin.cardOverlay(g, cx - 55, base - ph, 110, ph, colors[i], idx == 0, 0, time);
            MetaUi.textSC(g, font, String.valueOf(idx + 1), cx, base - ph + 6, 1.6f, MetaUi.solid(colors[i]), false);
            int fy = base - ph - 46;
            face(g, r, cx - 16, fy, 32);
            skin.focus(g, cx - 19, fy - 3, 38, 38, colors[i], time);
            String name = MetaUi.fit(font, J.str(r, "name", "?"), 104);
            MetaUi.textC(g, font, name, cx, base - ph + 24, MetaUi.solid(theme.text()), false);
            MetaUi.textC(g, font, value(key, def, r), cx, base - ph + 36, MetaUi.solid(theme.gold()), false);
            zone(cx - 55, fy, 110, base - fy, () -> openProfile(r));
        }

        // таблиця
        int ty = 250;
        MetaUi.text(g, font, "#", 240, ty, MetaUi.solid(theme.dim()), false);
        MetaUi.text(g, font, "ГРАВЕЦЬ", 290, ty, MetaUi.solid(theme.dim()), false);
        MetaUi.text(g, font, "РІВЕНЬ", 700, ty, MetaUi.solid(theme.dim()), false);
        MetaUi.textR(g, font, tabs.get(tab)[1].toUpperCase(), 930, ty, MetaUi.solid(theme.dim()), false);
        int visible = 9;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - 3 - visible)));
        for (int i = 0; i < visible; i++) {
            int idx = 3 + scroll + i;
            if (idx >= rows.size()) break;
            Map<String, Object> r = rows.get(idx);
            int y = ty + 14 + i * 26;
            boolean mine = me != null && me.toString().equals(J.str(r, "id", ""));
            float hv = hv("row" + idx, zone(234, y, 704, 24, () -> openProfile(r)));
            skin.card(g, 234, y, 704, 24, mine ? theme.accent() : theme.border(), mine, hv, time);
            skin.cardOverlay(g, 234, y, 704, 24, mine ? theme.accent() : theme.border(), mine, hv, time);
            MetaUi.text(g, font, String.valueOf(idx + 1), 242, y + 8, MetaUi.solid(theme.dim()), false);
            face(g, r, 266, y + 4, 16);
            MetaUi.text(g, font, MetaUi.fit(font, J.str(r, "name", "?"), 380), 290, y + 8, MetaUi.solid(theme.text()), false);
            MetaUi.text(g, font, String.valueOf(J.num(r, "level", 1)), 700, y + 8, MetaUi.solid(theme.text()), false);
            MetaUi.textR(g, font, value(key, def, r), 930, y + 8, MetaUi.solid(theme.gold()), false);
        }
    }

    private String value(String key, StatDef def, Map<String, Object> r) {
        long v = J.num(r, "value", 0);
        if ("level".equals(key)) return "рів. " + mode.curve.levelOf(v);
        return def != null ? def.format(v) : MetaUi.num(v);
    }

    private void openProfile(Map<String, Object> r) {
        click();
        try { MetaScreens.openProfile(this, UUID.fromString(J.str(r, "id", "")), J.str(r, "name", "")); }
        catch (IllegalArgumentException ignored) { }
    }

    private void face(GuiGraphics g, Map<String, Object> r, int x, int y, int size) {
        String id = J.str(r, "id", "");
        ResourceLocation skin = skins.get(id);
        if (skin == null) {
            UUID uuid;
            try { uuid = UUID.fromString(id); } catch (IllegalArgumentException e) { return; }
            PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(uuid);
            if (info != null) skin = info.getSkinLocation();
            else {
                GameProfile gp = new GameProfile(uuid, J.str(r, "name", "?"));
                String tex = J.str(r, "skin", "");
                if (!tex.isEmpty()) gp.getProperties().put("textures", new Property("textures", tex));
                skin = minecraft.getSkinManager().getInsecureSkinLocation(gp);
            }
            if (info != null) skins.put(id, skin);   // офлайн-скін вантажиться асинхронно — питаємо щокадру, доки не прийде
        }
        PlayerFaceRenderer.draw(g, skin, x, y, size);
    }

    @Override
    public boolean mouseScrolled(double rx, double ry, double amount) {
        scroll += amount < 0 ? 1 : -1;
        if (scroll < 0) scroll = 0;
        return true;
    }
}
