package dev.shaurmalib.meta.mc.screen;

import dev.shaurmalib.meta.MetaMode;
import dev.shaurmalib.meta.MetaTheme;
import dev.shaurmalib.meta.client.MetaClient;
import dev.shaurmalib.meta.economy.Currency;
import dev.shaurmalib.meta.mc.MetaScreens;
import dev.shaurmalib.meta.mc.skin.MetaSkin;
import dev.shaurmalib.meta.mc.skin.MetaSkins;
import dev.shaurmalib.meta.mc.ui.MetaUi;
import dev.shaurmalib.meta.profile.MetaProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * База екранів мета-двигуна: «дизайнові» координати 960×540, що рівномірно масштабуються під вікно,
 * клікабельні зони, плавні hover-стани, верхня навігація між розділами й гаманець. Усе МАЛЮВАННЯ —
 * через {@link MetaSkin} режиму, тож кожен мод показує ці екрани у власному стилі.
 */
public abstract class MetaScreen extends Screen {

    public static final int DW = 960, DH = 540;

    protected record Zone(int x, int y, int w, int h, Runnable action) {}

    protected final Screen parent;
    protected final MetaMode mode;
    protected final MetaSkin skin;
    protected final MetaTheme theme;
    protected float ui = 1, ox, oy;
    protected int mx, my;
    protected float dt, time;
    private long lastNanos = System.nanoTime();
    protected final List<Zone> zones = new ArrayList<>();
    private final Map<String, Float> hover = new HashMap<>();
    protected int accent;
    private String notice = "";
    private int noticeColor;
    private long noticeUntil;
    /** Показувати верхню навігацію між розділами. */
    protected boolean nav = true;
    /** Нижня межа клікабельної області (для прокручуваних списків під шапкою). */
    protected int clipTop = 0;

    protected MetaScreen(String title, Screen parent) {
        super(Component.literal(title));
        this.parent = parent;
        this.mode = MetaClient.mode();
        this.skin = MetaSkins.of(mode);
        this.theme = skin.theme();
        this.accent = theme.accent();
    }

    /** Ключ розділу для підсвітки в навігації: profile, wardrobe, shop, track, leaderboard. */
    protected abstract String section();

    protected abstract void draw(GuiGraphics g);

    protected MetaProfile me() { return MetaClient.self(); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        ui = Math.min(width / (float) DW, height / (float) DH);
        ox = (width - DW * ui) / 2f;
        oy = (height - DH * ui) / 2f;
    }

    @Override
    public void render(GuiGraphics g, int rawX, int rawY, float partial) {
        long now = System.nanoTime();
        dt = Math.min(.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        time += dt;
        mx = (int) Math.floor((rawX - ox) / ui);
        my = (int) Math.floor((rawY - oy) / ui);
        zones.clear();
        skin.backdrop(g, width, height, time, MetaUi.outCubic(time / .35f));
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(ui, ui, 1f);
        if (nav && mode != null) navBar(g);
        draw(g);
        drawNotice(g);
        g.pose().popPose();
        MetaScreens.renderToasts(g, width, height);
    }

    protected void panel(GuiGraphics g, int x, int y, int w, int h, boolean main) { skin.panel(g, x, y, w, h, main, time); }

    /** Іконка: спершу скін мода, інакше піксельна бібліотечна (8·px). */
    protected void icon(GuiGraphics g, String id, int x, int y, int px, int argb) {
        if (!skin.icon(g, id, x, y, 8 * px, argb, 0f)) dev.shaurmalib.meta.mc.ui.MetaIcons.draw(g, id, x, y, px, argb);
    }

    // ── навігація ────────────────────────────────────────────────────────
    private static final String[][] NAV = {
            {"profile", "ПРОФІЛЬ", "profile"}, {"wardrobe", "ГАРДЕРОБ", "head"}, {"shop", "МАГАЗИН", "shop"},
            {"track", "ПРОПУСК", "track"}, {"leaderboard", "ЛІДЕРИ", "board"}};

    /** Права межа навігації (ліворуч від гаманця). */
    private int navRight() {
        MetaProfile p = me();
        int w = 0;
        if (p != null && mode != null) for (Currency c : mode.currencies) w += font.width(MetaUi.num(p.wallet.get(c.id()))) + 32;
        return 946 - w - 8;
    }

    private void navBar(GuiGraphics g) {
        int total = 0;
        for (String[] n : NAV) total += skin.tabWidth(font, n[1]) + 4;
        int x = Math.min(330, navRight() - total), y = 10;
        for (String[] n : NAV) {
            int w = skin.tabWidth(font, n[1]);
            boolean act = n[0].equals(section());
            final String target = n[0];
            float hv = hv("nav" + n[0], zone(x, y, w, 20, () -> { if (!act) { click(); MetaScreens.open(target, parent); } }));
            skin.tab(g, font, x, y, w, 20, n[1], n[2], act, hv);
            x += w + 4;
        }
        wallet(g, 946, 13);
    }

    /** Гаманець праворуч зверху. */
    protected void wallet(GuiGraphics g, int right, int y) {
        MetaProfile p = me();
        if (p == null || mode == null) return;
        int x = right;
        for (int i = mode.currencies.size() - 1; i >= 0; i--) {
            Currency c = mode.currencies.get(i);
            String v = MetaUi.num(p.wallet.get(c.id()));
            int w = font.width(v) + 22;
            x -= w;
            skin.chip(g, font, x, y, v, c.icon(), c.color());
            x -= 6;
        }
    }

    // ── віджети ──────────────────────────────────────────────────────────
    protected boolean over(int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    protected boolean zone(int x, int y, int w, int h, Runnable action) {
        if (y + h <= clipTop) return false;
        zones.add(new Zone(x, Math.max(y, clipTop), w, h - Math.max(0, clipTop - y), action));
        return over(x, Math.max(y, clipTop), w, h - Math.max(0, clipTop - y));
    }

    protected float hv(String key, boolean on) {
        float v = hover.getOrDefault(key, 0f);
        v += ((on ? 1f : 0f) - v) * Math.min(1f, dt * 14f);
        hover.put(key, v);
        return v;
    }

    protected void button(GuiGraphics g, String key, int x, int y, int w, int h, String label, boolean active, Runnable action) {
        button(g, key, x, y, w, h, label, active ? MetaSkin.Kind.SELECTED : MetaSkin.Kind.NORMAL, action);
    }

    protected void button(GuiGraphics g, String key, int x, int y, int w, int h, String label, MetaSkin.Kind kind, Runnable action) {
        float hv = hv(key, action != null && zone(x, y, w, h, action));
        skin.button(g, font, x, y, w, h, label, kind, hv, action != null, time);
    }

    protected void primary(GuiGraphics g, String key, int x, int y, int w, int h, String label, Runnable action) {
        button(g, key, x, y, w, h, label, MetaSkin.Kind.PRIMARY, action);
    }

    protected void header(GuiGraphics g, String title, String sub) {
        skin.header(g, font, 16, 8, sub, title, time);
    }

    protected void say(String text, boolean error) {
        notice = text;
        noticeColor = error ? theme.bad() : theme.gold();
        noticeUntil = System.currentTimeMillis() + 3500;
    }

    private void drawNotice(GuiGraphics g) {
        long left = noticeUntil - System.currentTimeMillis();
        if (left <= 0 || notice.isEmpty()) return;
        float a = Math.min(1f, left / 400f);
        int w = font.width(notice) + 24, x = (DW - w) / 2, y = 512;
        skin.toast(g, x, y, w, 20, a, time);
        MetaUi.text(g, font, notice, x + 12, y + 6, MetaUi.a(noticeColor, a), true);
    }

    public void serverNotice(String text, String kind) { say(text, "error".equals(kind)); }

    protected void click() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    // ── ввід ─────────────────────────────────────────────────────────────
    protected void updateMouse(double rx, double ry) {
        mx = (int) Math.floor((rx - ox) / ui);
        my = (int) Math.floor((ry - oy) / ui);
    }

    @Override
    public boolean mouseClicked(double rx, double ry, int button) {
        updateMouse(rx, ry);
        if (button == 0) {
            for (int i = zones.size() - 1; i >= 0; i--) {
                Zone z = zones.get(i);
                if (over(z.x, z.y, z.w, z.h)) { z.action.run(); return true; }
            }
        }
        return super.mouseClicked(rx, ry, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    /** Перемалювати дані, коли сервер прислав новий профіль. */
    public void profileChanged() { }

    // ── доступ для Preview (той самий пакет) ─────────────────────────────
    float ui() { return ui; }
}
