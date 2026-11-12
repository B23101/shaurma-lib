package dev.shaurmalib.meta.mc.screen;

import dev.shaurmalib.meta.client.MetaClient;
import dev.shaurmalib.meta.cosmetics.CosmeticDef;
import dev.shaurmalib.meta.cosmetics.CosmeticSlot;
import dev.shaurmalib.meta.cosmetics.Look;
import dev.shaurmalib.meta.cosmetics.ParamSpec;
import dev.shaurmalib.meta.cosmetics.Wardrobe;
import dev.shaurmalib.meta.cosmetics.Worn;
import dev.shaurmalib.meta.economy.Currency;
import dev.shaurmalib.meta.economy.Recipe;
import dev.shaurmalib.meta.economy.Reward;
import dev.shaurmalib.meta.economy.ShopOffer;
import dev.shaurmalib.meta.mc.MetaScreens;
import dev.shaurmalib.meta.mc.render.CosmeticRenderer;
import dev.shaurmalib.meta.mc.skin.MetaSkin;
import dev.shaurmalib.meta.mc.ui.MetaIcons;
import dev.shaurmalib.meta.mc.ui.MetaUi;
import dev.shaurmalib.meta.profile.MetaProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Гардероб (перенесено з мафії й зроблено керованим даними — працює з каталогом будь-якого режиму).
 * Каталог ліворуч, примірка посередині, налаштування праворуч, пресети. Жодних повзунків — усе прямо на моделі:
 * <ul>
 *   <li>ЛКМ на вільному місці — обертання; ПКМ — пересунути модель; колесо поза предметом — масштаб;</li>
 *   <li>ЛКМ на предметі й тягнути — посадка (Shift — уперед/назад для улюбленця);</li>
 *   <li>колесо над предметом — розмір (Ctrl — розмах польоту, Alt — швидкість).</li>
 * </ul>
 * Невласні предмети видно із замком і ціною: купити, скрафтити або «на доріжці, рівень N».
 * Зміни зберігаються самі за 0.8 с (сервер ще раз перевіряє межі й володіння).
 */
public class WardrobeScreen extends MetaScreen {

    private static final int VX = 446, VY = 84, VW = 284, VH = 374;
    private static final int VCX = VX + VW / 2, VFY = 438;
    private static final float BASE = 122f;
    private static final int PRESETS_TAB = -1;

    private final Wardrobe wardrobe;
    private String variant;
    private RemotePlayer mannequin;
    /** Індекс у CosmeticSlot.values() або {@link #PRESETS_TAB}. */
    private int cat = CosmeticSlot.HEAD.ordinal();
    private CosmeticDef selected;
    private int selectedPreset = -1;
    private float yaw = 160, panX, panY, zoom = 1, yawGoal = Float.NaN;
    private boolean moving, changed;
    private int drag;
    private float saveAt, jumpStart = -10f, curLift, curLean, catTime, selY;
    private int scroll;

    private static final class Spark { float x, y, vx, vy, life, max, size, grav; int rgb; }
    private final List<Spark> sparks = new ArrayList<>();
    private final Random rnd = new Random();
    private float ambient, flash, auraMix;

    public WardrobeScreen(Screen parent) {
        super("Гардероб", parent);
        MetaProfile p = me();
        wardrobe = p == null ? new Wardrobe() : p.wardrobe.copy();
        variant = mode == null ? "player" : mode.variants.get(0);
        selected = first(slot());
    }

    @Override protected String section() { return "wardrobe"; }

    private CosmeticSlot slot() { return cat == PRESETS_TAB ? null : CosmeticSlot.values()[cat]; }

    private Look look() { return wardrobe.look(variant); }

    private boolean owns(CosmeticDef d) { MetaProfile p = me(); return p != null && p.owns(d.id()); }

    private List<CosmeticDef> pieces(CosmeticSlot s) {
        List<CosmeticDef> out = new ArrayList<>();
        if (mode == null || s == null) return out;
        for (CosmeticDef d : mode.cosmetics.bySlot(s)) if (!d.hidden() || owns(d)) out.add(d);
        // власні — першими
        out.sort((a, b) -> Boolean.compare(owns(b), owns(a)));
        return out;
    }

    private CosmeticDef first(CosmeticSlot s) {
        List<CosmeticDef> l = pieces(s);
        return l.isEmpty() ? null : l.get(0);
    }

    private boolean worn(CosmeticDef d) { Worn w = look().get(d.slot()); return w != null && w.item.equals(d.id()); }

    private void wear(CosmeticDef d, boolean on) {
        if (on && !owns(d)) { say("Цей предмет ще не твій", true); return; }
        if (on) {
            Worn prev = look().get(d.slot());
            Worn w = new Worn(d.id(), d.defaultColor());
            if (prev != null) w.params.putAll(prev.params);   // посадка лишається при заміні предмета
            w.validate(d.slot());
            look().put(d.slot(), w);
        } else look().remove(d.slot());
        dirty();
        burstItem(on ? accent : theme.dim(), on ? 56 : 24);
    }

    private void dirty() { changed = true; saveAt = time + .8f; }

    private void save() {
        if (!changed) return;
        changed = false;
        MetaClient.saveWardrobe(wardrobe);
    }

    private void markSeen(CosmeticDef d) {
        MetaProfile p = me();
        if (d != null && p != null && p.unseen.contains(d.id())) MetaClient.seen(List.of(d.id()));
    }

    @Override
    protected void init() {
        super.init();
        if (mannequin == null && minecraft != null && minecraft.player != null && minecraft.level != null) {
            mannequin = new RemotePlayer(minecraft.level, minecraft.player.getGameProfile()) {
                @Override public boolean isModelPartShown(PlayerModelPart part) { return true; }
            };
        }
    }

    @Override
    public void tick() {
        if (mannequin != null) {
            mannequin.tickCount++;
            mannequin.walkAnimation.update(moving ? .7f : 0f, .4f);
        }
    }

    @Override
    public void onClose() {
        save();
        CosmeticRenderer.preview = null;
        CosmeticRenderer.drawingPreview = false;
        super.onClose();
    }

    @Override
    public void removed() {
        save();
        super.removed();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Рендер
    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void draw(GuiGraphics g) {
        CosmeticSlot s = slot();
        if (changed && time >= saveAt) save();
        if (!Float.isNaN(yawGoal)) {
            if (drag == 1) yawGoal = Float.NaN;
            else {
                float d = Mth.wrapDegrees(yawGoal - yaw);
                yaw += d * Math.min(1f, dt * 6f);
                if (Math.abs(d) < .5f) yawGoal = Float.NaN;
            }
        }
        float u = (time - jumpStart) / .55f;
        float jumpH = 0, lift = 0;
        if (u >= 0 && u <= 1) { jumpH = 4f * .45f * u * (1f - u); lift = -(4f * .45f * (1f - 2f * u) / .55f) * .05f; }
        curLift = lift;
        curLean += ((moving ? 9f : 0f) - curLean) * Math.min(1f, dt * 8f);
        auraMix += ((s == CosmeticSlot.AURA ? 1f : 0f) - auraMix) * Math.min(1f, dt * 5f);
        if (mannequin != null) {
            mannequin.yBodyRot = mannequin.yBodyRotO = 0;
            mannequin.yHeadRot = mannequin.yHeadRotO = 0;
            mannequin.setYRot(0);
            mannequin.setXRot(0);
        }
        updateFx();
        drawHeader(g);
        drawLeft(g);
        drawViewport(g, jumpH, lift);
        drawRight(g);
        drawHints(g);
        drawFx(g);
    }

    private void drawHeader(GuiGraphics g) {
        header(g, "ГАРДЕРОБ", "КОСМЕТИКА");
        int x = 446;
        if (mode != null && mode.variants.size() > 1) {
            for (String v : mode.variants) {
                String name = mode.variantNames.getOrDefault(v, v).toUpperCase();
                int w = font.width(name) + 24;
                boolean act = v.equals(variant);
                button(g, "var" + v, x, 54, w, 20, name, act, () -> { if (!act) { variant = v; click(); burstItem(accent, 30); } });
                x += w + 6;
            }
        }
        button(g, "move", Math.max(x + 6, 600), 54, 120, 20, moving ? "РУХ · ХОДЬБА" : "РУХ · СПОКІЙ", moving, () -> { moving = !moving; click(); });
        button(g, "resetView", 826, 54, 120, 20, "R · СКИНУТИ ВИД", false, () -> { resetView(); click(); });
    }

    private void resetView() { yaw = 160; panX = panY = 0; zoom = 1; }

    // ── ліва панель ─────────────────────────────────────────────────────
    private void drawLeft(GuiGraphics g) {
        panel(g, 14, 84, 424, 374, false);
        CosmeticSlot[] slots = CosmeticSlot.values();
        int rows = slots.length + 1;
        for (int i = 0; i < rows; i++) {
            final int idx = i < slots.length ? i : PRESETS_TAB;
            String title = i < slots.length ? slots[i].title.toUpperCase() : "ПРЕСЕТИ";
            String icon = i < slots.length ? iconOf(slots[i]) : "preset";
            boolean act = idx == cat;
            int y = 96 + i * 34;
            float hv = hv("cat" + i, zone(22, y, 84, 28, () -> {
                if (cat != idx) {
                    cat = idx;
                    selected = idx == PRESETS_TAB ? null : first(CosmeticSlot.values()[idx]);
                    markSeen(selected);
                    selectedPreset = -1;
                    catTime = time;
                    scroll = 0;
                    yawGoal = idx == CosmeticSlot.BACK.ordinal() ? 300f : 160f;
                    click();
                }
            }));
            skin.tab(g, font, 22, y, 84, 28, title, icon, act, hv);
            if (i < slots.length && hasUnseen(slots[i])) MetaUi.disc(g, 101, y + 5, 2, MetaUi.solid(theme.gold()));
        }
        g.fill(112, 96, 113, 446, MetaUi.a(theme.border(), .9f));

        String title = cat == PRESETS_TAB ? "ПРЕСЕТИ" : slot().title.toUpperCase();
        if (cat == PRESETS_TAB) {
            skin.sectionTitle(g, font, 122, 96, 306, title, null, time);
            for (int i = 0; i < Wardrobe.PRESETS; i++) presetCard(g, i, 122 + i * 104, 120);
            button(g, "clearAll", 122, 258, 306, 26, "ЗНЯТИ ВСЕ", MetaSkin.Kind.DANGER, () -> {
                click();
                look().clear();
                selected = null;
                dirty();
                say("Усе знято", false);
            });
            return;
        }
        List<CosmeticDef> list = pieces(slot());
        int owned = 0;
        for (CosmeticDef d : list) if (owns(d)) owned++;
        skin.sectionTitle(g, font, 122, 96, 306, title, owned + " / " + list.size(), time);
        if (list.isEmpty()) {
            MetaUi.text(g, font, "Предметів поки немає", 122, 124, MetaUi.solid(theme.dim()), false);
            MetaUi.textS(g, font, "Слідкуй за магазином і пропуском сезону.", 122, 138, .8f, MetaUi.a(theme.dim(), .7f), false);
            return;
        }
        int perRow = 3, maxRows = 2;
        int pages = (list.size() + perRow * maxRows - 1) / (perRow * maxRows);
        scroll = Math.max(0, Math.min(scroll, pages - 1));
        int start = scroll * perRow * maxRows;
        for (int i = start; i < Math.min(list.size(), start + perRow * maxRows); i++) {
            int k = i - start;
            pieceCard(g, list.get(i), 122 + (k % perRow) * 104, 120 + (k / perRow) * 134, k);
        }
        if (pages > 1) {
            MetaUi.textC(g, font, (scroll + 1) + " / " + pages, 275, 434, MetaUi.solid(theme.dim()), false);
            button(g, "pgPrev", 122, 428, 40, 20, "<", false, scroll > 0 ? () -> { scroll--; catTime = time; click(); } : null);
            button(g, "pgNext", 388, 428, 40, 20, ">", false, scroll < pages - 1 ? () -> { scroll++; catTime = time; click(); } : null);
        }
    }

    private boolean hasUnseen(CosmeticSlot s) {
        MetaProfile p = me();
        if (p == null || p.unseen.isEmpty() || mode == null) return false;
        for (CosmeticDef d : mode.cosmetics.bySlot(s)) if (p.unseen.contains(d.id())) return true;
        return false;
    }

    static String iconOf(CosmeticSlot s) {
        return switch (s) { case HEAD -> "head"; case BODY -> "body"; case LEGS -> "legs"; case BACK -> "back"; case PET -> "pet"; case AURA -> "aura"; };
    }

    private void pieceCard(GuiGraphics g, CosmeticDef d, int bx, int by, int idx) {
        int w = 98, h = 128, ph = 88;
        boolean sel = d == selected, own = owns(d);
        float hv = hv("card" + d.id(), zone(bx, by, w, h, () -> {
            click();
            selected = d;
            markSeen(d);
            if (own && !worn(d)) wear(d, true);
            else burstItem(accent, 18);
        }));
        float appear = MetaUi.outBack(MetaUi.clamp01((time - catTime - .1f - idx * .07f) / .45f));
        if (appear <= .02f) return;
        int x = bx, y = by + Math.round((1f - appear) * 24f);
        int rc = d.rarity().color;
        skin.card(g, x, y, w, h, rc, sel, hv, time);
        skin.stage(g, x + 2, y + 2, w - 4, ph, rc, time, hv);
        boolean au = d.slot() == CosmeticSlot.AURA;
        Worn w1 = worn(d) ? look().get(d.slot()).copy() : new Worn(d.id(), d.defaultColor());
        w1.validate(d.slot());
        figure(g, x + w / 2, y + (au ? 64 : 84), (au ? 28 : 32) + 2 * hv,
                faceYaw(d.slot()) + (float) Math.sin(time * 1.1 + idx) * 20f + hv * 35f, new Look().only(d.slot(), w1),
                x + 2, y + 2, w - 4, ph, 0, 0, au);
        if (!own) {
            g.fill(x + 2, y + 2, x + w - 2, y + 2 + ph, MetaUi.a(0, .42f));
            MetaUi.lock(g, x + w / 2 - 3, y + 38, MetaUi.a(0xFFFFFF, .85f));
        }
        skin.tag(g, font, x + 5, y + ph - 10, d.rarity().title, rc, true);
        int ny = y + ph + 6, lines = 0;
        for (String line : MetaUi.wrap(font, d.name(), (int) ((w - 12) / .85f))) {
            if (lines++ >= 2) break;
            MetaUi.textS(g, font, line, x + 6, ny, .85f, MetaUi.solid(theme.text()), false);
            ny += 9;
        }
        if (!own) {
            String price = priceText(d);
            if (!price.isEmpty()) MetaUi.textS(g, font, price, x + 6, y + h - 10, .75f, MetaUi.solid(theme.gold()), false);
        }
        if (worn(d)) MetaUi.check(g, x + w - 13, y + 7, MetaUi.solid(theme.good()));
        MetaProfile p = me();
        if (p != null && p.unseen.contains(d.id())) skin.tag(g, font, x + 5, y + 5, "НОВЕ", theme.gold(), true);
        skin.cardOverlay(g, x, y, w, h, rc, sel, hv, time);
    }

    private String priceText(CosmeticDef d) {
        if (mode == null) return "";
        ShopOffer o = mode.offerFor(d.id());
        if (o != null) return MetaUi.num(o.price()) + " " + currencyShort(o.currency());
        if (mode.recipeFor(d.id()) != null) return "МАЙСТЕРНЯ";
        Integer lvl = trackLevelOf(d);
        return lvl != null ? "ПРОПУСК · " + lvl : "";
    }

    private Integer trackLevelOf(CosmeticDef d) {
        for (Map.Entry<Integer, List<Reward>> e : mode.track.steps().entrySet()) {
            for (Reward r : e.getValue()) if (r.type() == Reward.Type.COSMETIC && r.id().equals(d.id())) return e.getKey();
        }
        return null;
    }

    private String currencyShort(String id) {
        Currency c = mode.currency(id);
        return c == null ? id : c.name();
    }

    private void presetCard(GuiGraphics g, int slot, int x, int y) {
        int w = 98, h = 128, ph = 88;
        boolean sel = slot == selectedPreset;
        float hv = hv("slot" + slot, zone(x, y, w, h, () -> { click(); selectedPreset = slot; }));
        skin.card(g, x, y, w, h, theme.gold(), sel, hv, time);
        skin.stage(g, x + 2, y + 2, w - 4, ph, theme.gold(), time, hv);
        Look s = wardrobe.preset(slot);
        if (s != null) figure(g, x + w / 2, y + 84, 32, 150, s, x + 2, y + 2, w - 4, ph, 0, 0, false);
        else MetaUi.textC(g, font, "порожньо", x + w / 2, y + 40, MetaUi.a(theme.dim(), .8f), false);
        MetaUi.text(g, font, "ПРЕСЕТ " + (slot + 1), x + 6, y + ph + 7, MetaUi.solid(theme.text()), false);
        MetaUi.textS(g, font, s != null ? "збережено" : "—", x + 6, y + ph + 20, .8f, s != null ? MetaUi.solid(theme.good()) : MetaUi.solid(theme.dim()), false);
        skin.cardOverlay(g, x, y, w, h, theme.gold(), sel, hv, time);
    }

    private static float faceYaw(CosmeticSlot s) { return s == CosmeticSlot.BACK ? 300f : 150f; }

    /** Манекен із образом, обрізаний прямокутником (у дизайнових координатах). */
    private void figure(GuiGraphics g, int cx, int fy, float size, float yawDeg, Look lk,
                        int clx, int cly, int clw, int clh, float lift, float lean, boolean tilt) {
        if (mannequin == null) return;
        g.enableScissor((int) (ox + clx * ui), (int) (oy + cly * ui), (int) (ox + (clx + clw) * ui), (int) (oy + (cly + clh) * ui));
        CosmeticRenderer.preview = lk;
        CosmeticRenderer.drawingPreview = true;
        CosmeticRenderer.previewLift = lift;
        CosmeticRenderer.previewLean = lean;
        CosmeticRenderer.previewYaw = yawDeg;
        CosmeticRenderer.previewTilt = tilt ? .42f : 0f;
        try {
            InventoryScreen.renderEntityInInventory(g, cx, fy, (int) size,
                    new Quaternionf().rotationZ((float) Math.PI).rotateY((float) Math.toRadians(yawDeg)), null, mannequin);
        } finally {
            CosmeticRenderer.drawingPreview = false;
            CosmeticRenderer.preview = null;
            CosmeticRenderer.previewLift = CosmeticRenderer.previewLean = CosmeticRenderer.previewTilt = 0;
            g.disableScissor();
        }
    }

    // ── центр: модель ───────────────────────────────────────────────────
    private double[] itemSpot(CosmeticSlot k) {
        if (mannequin == null || k == null) return null;
        Worn w = look().get(k);
        if (w == null) return null;
        float size = BASE * zoom;
        double ang = Math.toRadians(yaw), cx = VCX + panX, fy = VFY + panY;
        return switch (k) {
            case PET -> {
                double phase = (mannequin.tickCount + (minecraft == null ? 0 : minecraft.getFrameTime())) * .075 * w.param(k, "speed");
                double h = w.param(k, "y") + Math.sin(phase) * w.param(k, "bob") + curLift;
                yield new double[]{cx - size * (w.param(k, "x") * Math.cos(ang) + w.param(k, "z") * Math.sin(ang)), fy - size * h, size * w.param(k, "scale") * .62};
            }
            case BACK -> {
                double b = -CosmeticRenderer.backCenterZ(w);
                yield new double[]{cx - size * (b * Math.sin(ang)), fy - size * (1.126 + w.param(k, "y")), size * w.param(k, "scale") * .6};
            }
            case HEAD -> new double[]{cx, fy - size * (1.82 + w.param(k, "y")), size * .4 * w.param(k, "scale")};
            default -> null;
        };
    }

    private double[] spotUnderMouse() {
        double[] s = itemSpot(slot());
        if (s == null) return null;
        double dx = mx - s[0], dy = my - s[1];
        return dx * dx + dy * dy <= s[2] * s[2] ? s : null;
    }

    private void drawViewport(GuiGraphics g, float jumpH, float lift) {
        float sm = MetaUi.clamp01((time - .2f) / .8f);
        skin.viewport(g, VX, VY, VW, VH, VCX + (int) panX, VFY + (int) panY, accent, time, sm * (1f - auraMix * .7f));
        MetaUi.text(g, font, "ПРИМІРКА", VX + 8, VY + 6, MetaUi.a(theme.dim(), sm), false);
        if (mode != null && mode.variants.size() > 1) {
            String v = mode.variantNames.getOrDefault(variant, variant).toUpperCase();
            MetaUi.textR(g, font, v, VX + VW - 8, VY + 6, MetaUi.a(theme.gold(), sm), false);
        }
        float model = MetaUi.outBack(MetaUi.clamp01((time - .3f) / .9f));
        float size = BASE * zoom * (1f - .28f * auraMix);
        int fx = (int) (VCX + panX), fy = (int) (VFY + panY - size * jumpH + (1f - Math.min(1, model)) * 150f - 44f * auraMix);
        figure(g, fx, fy, size, yaw + (1f - Math.min(1, model)) * 320f, look(), VX, VY - 6, VW, VH + 12, lift, curLean, auraMix > .5f);

        boolean inView = over(VX, VY, VW, VH);
        double[] spot = drag == 3 ? itemSpot(slot()) : inView && drag == 0 ? spotUnderMouse() : null;
        if (spot != null) {
            int r = (int) (spot[2] * .62);
            skin.focus(g, (int) spot[0] - r, (int) spot[1] - r, 2 * r, 2 * r, theme.gold(), time);
        }
        String tip = null;
        if (drag == 3) tip = "ЛКМ утримується · тягни";
        else if (drag == 0 && inView) tip = spot != null ? percentText() + " · колесо" : "ЛКМ · повернути";
        if (tip != null) {
            int tw = (int) (font.width(tip) * .85f), tx = Math.min(mx + 12, 954 - tw - 8), ty = my + 10;
            skin.toast(g, tx - 4, ty - 3, tw + 8, 14, 1f, time);
            MetaUi.textS(g, font, tip, tx, ty, .85f, MetaUi.solid(theme.gold()), false);
        }
    }

    private String percentText() {
        CosmeticSlot s = slot();
        Worn w = s == null ? null : look().get(s);
        if (w == null) return "";
        if (s == CosmeticSlot.PET && Screen.hasControlDown()) return "розмах " + s.param("bob").percent(w.param(s, "bob")) + "%";
        if (s == CosmeticSlot.PET && Screen.hasAltDown()) return "швидкість " + s.param("speed").percent(w.param(s, "speed")) + "%";
        return s.param("scale").percent(w.param(s, "scale")) + "%";
    }

    // ── права панель ────────────────────────────────────────────────────
    private void drawRight(GuiGraphics g) {
        int x = 738, y = 84, w = 208, h = 374;
        if (cat == PRESETS_TAB) { drawPresetPanel(g, x, y, w, h); return; }
        panel(g, x, y, w, h, false);
        if (selected == null) {
            MetaUi.textC(g, font, "Обери предмет", x + w / 2, y + h / 2 - 4, MetaUi.a(theme.dim(), .8f), false);
            return;
        }
        CosmeticDef d = selected;
        boolean own = owns(d), on = worn(d);
        if (on) {
            g.fill(x + 14, y + 15, x + 20, y + 21, MetaUi.solid(theme.good()));
            MetaUi.text(g, font, "ОДЯГНЕНО", x + 26, y + 14, MetaUi.solid(theme.good()), false);
        } else skin.tag(g, font, x + 14, y + 13, d.rarity().title.toUpperCase(), d.rarity().color, true);
        float hvX = hv("x", zone(x + w - 28, y + 8, 20, 20, () -> { selected = null; click(); }));
        MetaUi.cross(g, x + w - 21, y + 15, MetaUi.mix(MetaUi.solid(theme.dim()), 0xFFFFFFFF, hvX));
        MetaUi.textS(g, font, MetaUi.fit(font, d.name(), 140), x + 14, y + 34, 1.3f, MetaUi.solid(theme.text()), true);
        skin.divider(g, x + 14, y + 50, w - 28);

        int pvY = y + 58, pvH = 146;
        skin.stage(g, x + 12, pvY, w - 24, pvH, d.rarity().color, time, 0);
        boolean au = d.slot() == CosmeticSlot.AURA;
        Worn cur = on ? look().get(d.slot()) : new Worn(d.id(), d.defaultColor());
        figure(g, x + w / 2, pvY + (au ? 110 : 134), au ? 48 : 58, faceYaw(d.slot()) + (float) Math.sin(time * .8) * 18f,
                new Look().only(d.slot(), cur), x + 12, pvY, w - 24, pvH, curLift, curLean, au);

        if (own) {
            MetaUi.text(g, font, "КОЛІР", x + 14, y + 212, MetaUi.solid(theme.dim()), false);
            int[] pal = d.palette().length == 0 ? new int[]{d.defaultColor(), 0xFFFFFF, 0xFF7A1A, 0xA77BFF, 0x55E5FF, 0x3DDC84} : d.palette();
            for (int i = 0; i < Math.min(6, pal.length); i++) {
                int rgb = pal[i], sx = x + 16 + i * 30, sy = y + 224;
                float hv = hv("col" + i, zone(sx, sy, 24, 22, () -> {
                    if (!worn(d)) wear(d, true);
                    Worn ww = look().get(d.slot());
                    if (ww != null) { ww.color = rgb; dirty(); burstItem(rgb, 40); }
                    click();
                }));
                boolean isCur = on && (look().get(d.slot()).color & 0xFFFFFF) == rgb;
                g.fill(sx, sy, sx + 24, sy + 22, MetaUi.solid(MetaUi.bright(rgb, .6f)));
                g.fill(sx + 1, sy + 1, sx + 23, sy + 21, MetaUi.solid(rgb));
                if (isCur || hv > .02f) skin.focus(g, sx - 3, sy - 3, 30, 28, isCur ? 0xFFFFFF : MetaUi.mix(0, 0xFFFFFF, hv), time);
            }
            button(g, "reset", x + 12, y + 258, w - 24, 24, "СКИНУТИ ПОСАДКУ", MetaSkin.Kind.DANGER, on ? () -> {
                Worn ww = look().get(d.slot());
                ww.params.clear();
                ww.color = d.defaultColor();
                ww.validate(d.slot());
                dirty();
                click();
                say("Налаштування предмета скинуто", false);
            } : null);
            primary(g, "wear", x + 12, y + 290, w - 24, 30, on ? "ЗНЯТИ" : "ОДЯГНУТИ", () -> { wear(d, !on); click(); });
        } else {
            int ly = y + 212;
            for (String line : MetaUi.wrap(font, d.description(), (int) ((w - 24) / .8f))) {
                MetaUi.textS(g, font, line, x + 12, ly, .8f, MetaUi.solid(theme.dim()), false);
                ly += 9;
                if (ly > y + 246) break;
            }
            ShopOffer offer = mode.offerFor(d.id());
            Recipe recipe = mode.recipeFor(d.id());
            Integer lvl = trackLevelOf(d);
            MetaProfile p = me();
            int by = y + 258;
            if (offer != null) {
                boolean can = p != null && p.wallet.canAfford(offer.currency(), offer.price());
                primary(g, "buy", x + 12, by, w - 24, 28, "КУПИТИ · " + MetaUi.num(offer.price()) + " " + currencyShort(offer.currency()),
                        can ? () -> { click(); MetaClient.buy(offer.id()); } : null);
                if (!can) MetaUi.textSC(g, font, "Не вистачає " + currencyShort(offer.currency()), x + w / 2, by + 32, .8f, MetaUi.solid(theme.bad()), false);
                by += 44;
            }
            if (recipe != null) {
                button(g, "craft", x + 12, by, w - 24, 22, "ДО МАЙСТЕРНІ", false, () -> { click(); MetaScreens.open("craft", parent); });
                by += 28;
            }
            if (lvl != null) MetaUi.textSC(g, font, "Нагорода пропуску · ступінь " + lvl, x + w / 2, by + 4, .8f, MetaUi.solid(theme.gold()), false);
        }
        String how = switch (d.slot()) {
            case PET -> "Тягни улюбленця на моделі. Shift — уперед/назад. Колесо — розмір.";
            case AURA -> "Аура під гравцем. Колесо над моделлю — розмір.";
            case BACK -> "Тягни вгору чи вниз; боком до моделі — відстань від спини.";
            default -> "Тягни предмет вгору або вниз. Колесо — розмір.";
        };
        int ly = y + 336;
        for (String line : MetaUi.wrap(font, how, (int) ((w - 24) / .75f))) {
            if (ly > y + h - 9) break;
            MetaUi.textS(g, font, line, x + 12, ly, .75f, MetaUi.solid(theme.dim()), false);
            ly += 8;
        }
    }

    private void drawPresetPanel(GuiGraphics g, int x, int y, int w, int h) {
        panel(g, x, y, w, h, false);
        if (selectedPreset < 0) {
            MetaUi.textC(g, font, "Обери пресет", x + w / 2, y + h / 2 - 4, MetaUi.a(theme.dim(), .8f), false);
            return;
        }
        int slot = selectedPreset;
        MetaUi.textS(g, font, "ПРЕСЕТ " + (slot + 1), x + 14, y + 16, 1.3f, MetaUi.solid(theme.text()), true);
        skin.divider(g, x + 14, y + 32, w - 28);
        Look s = wardrobe.preset(slot);
        skin.stage(g, x + 12, y + 58, w - 24, 146, theme.gold(), time, 0);
        if (s != null) figure(g, x + w / 2, y + 192, 58, 150 + (float) Math.sin(time * .8) * 18f, s, x + 12, y + 58, w - 24, 146, 0, 0, false);
        else MetaUi.textC(g, font, "порожньо", x + w / 2, y + 128, MetaUi.a(theme.dim(), .8f), false);
        button(g, "slotLoad", x + 12, y + 222, w - 24, 24, "ЗАСТОСУВАТИ", false, s == null ? null : () -> {
            click();
            Look copy = s.copy();
            MetaProfile p = me();
            if (p != null) copy.validate(mode.cosmetics, p::owns);
            wardrobe.setLook(variant, copy);
            dirty();
            say("Пресет застосовано", false);
        });
        primary(g, "slotSave", x + 12, y + 256, w - 24, 30, "ЗАПИСАТИ ПОТОЧНИЙ", () -> {
            click();
            wardrobe.setPreset(slot, look().copy());
            dirty();
            say("Пресет записано", false);
        });
    }

    private void drawHints(GuiGraphics g) {
        String hint;
        if (drag == 3) hint = "Не відпускаючи кнопку, тягни предмет, а потім відпусти, щоб закріпити посадку.";
        else if (drag == 1) hint = "Відпусти кнопку — модель залишиться під цим кутом. R — скинути вид.";
        else if (selected != null && !owns(selected)) hint = "Предмет ще не твій: купи в магазині, скрафти в майстерні або отримай у пропуску сезону.";
        else if (selected != null && worn(selected)) hint = "Зажми ліву кнопку на предметі й тягни, щоб змінити посадку. Колесо над предметом — розмір.";
        else hint = "Натисни на картку, щоб одягнути. ЛКМ біля моделі — повернути її.";
        int y = 468;
        for (String line : MetaUi.wrap(font, hint, 560)) { MetaUi.textC(g, font, line, VCX, y, MetaUi.solid(theme.text()), false); y += 11; }
        MetaUi.textSC(g, font, "ЛКМ — обертати · ПКМ — пересунути · колесо — масштаб · Пробіл — стрибок · R — скинути вид", 480, 524, .8f,
                MetaUi.a(theme.dim(), .8f), false);
        if (changed) MetaUi.text(g, font, "• Зберігаю…", 18, 468, MetaUi.solid(theme.dim()), false);
    }

    // ── ефекти ──────────────────────────────────────────────────────────
    private void burst(double x, double y, int rgb, int n) {
        if (!skin.sparks()) return;
        for (int i = 0; i < n && sparks.size() < 500; i++) {
            Spark k = new Spark();
            double an = rnd.nextDouble() * Math.PI * 2, sp = 40 + rnd.nextDouble() * 140;
            k.x = (float) x; k.y = (float) y; k.vx = (float) (Math.cos(an) * sp); k.vy = (float) (Math.sin(an) * sp - 30);
            k.max = k.life = .6f + rnd.nextFloat() * .7f; k.size = 1 + rnd.nextInt(3); k.grav = 70; k.rgb = rgb;
            sparks.add(k);
        }
        flash = Math.min(1f, flash + .4f);
    }

    private void burstItem(int rgb, int n) {
        double[] sp = itemSpot(slot());
        if (sp == null) sp = new double[]{VCX + panX, VFY + panY - BASE * zoom, 0};
        burst(sp[0], sp[1], rgb, n);
    }

    private void updateFx() {
        if (skin.sparks()) {
            ambient += dt;
            while (ambient > .06f) {
                ambient -= .06f;
                if (sparks.size() > 300) break;
                Spark k = new Spark();
                k.x = VX + rnd.nextFloat() * VW; k.y = VFY + 20;
                k.vx = (rnd.nextFloat() - .5f) * 14; k.vy = -(14 + rnd.nextFloat() * 30);
                k.max = k.life = 2.5f + rnd.nextFloat() * 2f; k.size = 1 + rnd.nextInt(2);
                k.rgb = rnd.nextInt(3) == 0 ? theme.gold() : accent;
                sparks.add(k);
            }
        }
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark k = sparks.get(i);
            k.life -= dt;
            if (k.life <= 0) { sparks.remove(i); continue; }
            k.vy += k.grav * dt;
            k.x += k.vx * dt + (k.grav == 0 ? (float) Math.sin(time * 2 + k.y * .05) * .25f : 0);
            k.y += k.vy * dt;
        }
        flash = Math.max(0f, flash - dt * 2.2f);
    }

    private void drawFx(GuiGraphics g) {
        g.enableScissor((int) (ox + VX * ui), (int) (oy + VY * ui), (int) (ox + (VX + VW) * ui), (int) (oy + (VY + VH) * ui));
        for (Spark k : sparks) {
            float a = Math.min(1f, k.life / k.max * (k.grav == 0 ? 1.6f : 1.2f));
            if (k.grav == 0) a *= Math.min(1f, (k.max - k.life) * 2f);
            int sz = Math.max(1, Math.round(k.size * (.5f + .5f * a)));
            g.fill((int) k.x, (int) k.y, (int) k.x + sz, (int) k.y + sz, MetaUi.a(k.rgb, a * .9f));
        }
        g.disableScissor();
        if (flash > .01f) g.fillGradient(VX, VY, VX + VW, VY + VH, MetaUi.a(0xFFFFFF, flash * .10f), 0x00FFFFFF);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Ввід
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double rx, double ry, int button) {
        updateMouse(rx, ry);
        if (button == 0) {
            for (int i = zones.size() - 1; i >= 0; i--) {
                Zone z = zones.get(i);
                if (over(z.x(), z.y(), z.w(), z.h())) { z.action().run(); return true; }
            }
        }
        if (over(VX, VY, VW, VH)) {
            if (button == 1) drag = 2;
            else if (button == 0) drag = spotUnderMouse() != null ? 3 : 1;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double rx, double ry, int button, double rdx, double rdy) {
        updateMouse(rx, ry);
        double dx = rdx / ui, dy = rdy / ui;
        if (drag == 1) { yaw += (float) dx * .9f; return true; }
        if (drag == 2) {
            panX = Math.max(-120, Math.min(120, panX + (float) dx));
            panY = Math.max(-80, Math.min(80, panY + (float) dy));
            return true;
        }
        if (drag == 3) {
            CosmeticSlot s = slot();
            Worn w = s == null ? null : look().get(s);
            if (w == null) return true;
            float size = BASE * zoom;
            double ang = Math.toRadians(yaw), cos = Math.cos(ang), sin = Math.sin(ang);
            Map<String, Float> before = Map.copyOf(w.params);
            switch (s) {
                case HEAD -> w.setParam(s, "y", w.param(s, "y") - (float) (dy / size));
                case BACK -> {
                    if (Screen.hasShiftDown() && Math.abs(sin) < .5) w.setParam(s, "z", w.param(s, "z") - (float) (dy / size));
                    else {
                        w.setParam(s, "y", w.param(s, "y") - (float) (dy / size));
                        if (Math.abs(sin) >= .35) w.setParam(s, "z", w.param(s, "z") + (float) (dx / size * Math.signum(sin)));
                    }
                }
                case PET -> {
                    boolean side = Math.abs(cos) >= Math.abs(sin);
                    float sc = (float) Math.signum(side ? cos : sin);
                    float hh = (float) (-dx / size * sc);
                    String a = side ? "x" : "z", b = side ? "z" : "x";
                    if (Screen.hasShiftDown()) {
                        float dd = (float) (side ? -dy / size * sc : dy / size * sc);
                        w.setParam(s, b, w.param(s, b) + dd);
                        w.setParam(s, a, w.param(s, a) + hh);
                    } else {
                        w.setParam(s, a, w.param(s, a) + hh);
                        w.setParam(s, "y", w.param(s, "y") - (float) (dy / size));
                    }
                }
                default -> { }
            }
            w.validate(s);
            if (w.params.equals(before) && (Math.abs(dx) + Math.abs(dy)) > 2) say("Досягнуто межі — далі не можна, щоб не заважати іншим", false);
            dirty();
            return true;
        }
        return super.mouseDragged(rx, ry, button, rdx, rdy);
    }

    @Override
    public boolean mouseReleased(double rx, double ry, int button) {
        if (drag != 0) {
            if (drag == 3) burstItem(theme.gold(), 14);
            drag = 0;
            return true;
        }
        return super.mouseReleased(rx, ry, button);
    }

    @Override
    public boolean mouseScrolled(double rx, double ry, double amount) {
        updateMouse(rx, ry);
        if (!over(VX, VY, VW, VH)) {
            if (over(116, 118, 318, 268)) { scroll += amount < 0 ? 1 : -1; catTime = time; return true; }
            return super.mouseScrolled(rx, ry, amount);
        }
        CosmeticSlot s = slot();
        Worn w = s == null ? null : look().get(s);
        float k = 1f + (float) amount * .06f;
        if (s == CosmeticSlot.AURA && w != null) { w.setParam(s, "scale", w.param(s, "scale") * k); dirty(); return true; }
        if (w != null && spotUnderMouse() != null) {
            if (s == CosmeticSlot.PET && Screen.hasControlDown()) w.setParam(s, "bob", Math.max(.005f, w.param(s, "bob")) * k);
            else if (s == CosmeticSlot.PET && Screen.hasAltDown()) w.setParam(s, "speed", w.param(s, "speed") * k);
            else {
                ParamSpec sp = s.param("scale");
                if (sp != null) w.setParam(s, "scale", w.param(s, "scale") * k);
            }
            dirty();
        } else zoom = Math.max(.6f, Math.min(1.4f, zoom + (float) amount * .05f));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_R) { resetView(); return true; }
        if (key == GLFW.GLFW_KEY_SPACE) { if (time - jumpStart > .6f) jumpStart = time; return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void profileChanged() {
        // Покупка/крафт додали предмет — лишаємо локальні зміни образу, але оновлюємо вибір.
        if (selected != null && owns(selected) && !worn(selected)) burstItem(theme.gold(), 60);
    }
}
