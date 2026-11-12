package dev.shaurmalib.anim.mc.emote;

import dev.shaurmalib.anim.ShaurmaAnim;
import dev.shaurmalib.anim.emote.Emote;
import dev.shaurmalib.anim.emote.EmoteWheel;
import dev.shaurmalib.anim.emote.ShaurmaEmotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Налаштування колеса: ліворуч — усі доступні емоції за категоріями, посередині — 3D-модель гравця, що
 * грає вибрану/наведену емоцію, праворуч — 8 слотів колеса.
 *
 * <ul>
 *   <li>перетягнути емоцію зі списку в слот (або слот у слот — поміняти місцями; зі слота за колесо — прибрати);</li>
 *   <li>або клацнути емоцію, потім слот;</li>
 *   <li>ПКМ по слоту — очистити. Зберігається одразу.</li>
 * </ul>
 */
public final class EmoteSettingsScreen extends Screen {

    private static final int ROW = 16, HEADER = 14;

    private final EmotePreview preview = new EmotePreview();
    private final List<Row> rows = new ArrayList<>();
    private Emote[] slots;
    private Emote selected, dragging, hoverEmote;
    private int dragFromSlot = -1, pressedSlot = -1, highlightSlot;
    private double pressX, pressY;
    private boolean pressedRow;
    private int scroll, listX, listY, listW, listH, contentH;
    private float cx, cy, r0, r1;

    private record Row(Emote emote, String category, int y) {}

    public EmoteSettingsScreen(int slot) {
        super(EmoteText.ui("settings", "Колесо емоцій — налаштування"));
        this.highlightSlot = slot;
    }

    @Override
    protected void init() {
        refresh();
        listX = 10;
        listY = 30;
        listW = Mth.clamp(Math.round(width * 0.27f), 110, 190);
        listH = height - listY - 52;
        r1 = Math.min(height * 0.32f, width * 0.18f);
        r0 = r1 * 0.42f;
        cx = width - r1 - 34; // місце для назв і номерів слотів зовні кільця
        cy = height / 2f;
        rows.clear();
        int y = 0;
        List<Emote> all = ShaurmaEmotes.available();
        for (String cat : ShaurmaEmotes.registry().categories()) {
            rows.add(new Row(null, cat, y));
            y += HEADER;
            for (Emote e : all) {
                if (!e.category().equals(cat) || !ShaurmaAnim.clips().has(e.clip())) continue;
                rows.add(new Row(e, cat, y));
                y += ROW;
            }
        }
        contentH = y;
        if (highlightSlot >= 0 && slots[highlightSlot] != null) selected = slots[highlightSlot];
        if (selected == null && !all.isEmpty()) selected = all.get(0);

        int bw = 90;
        addRenderableWidget(Button.builder(EmoteText.ui("done", "Готово"), b -> onClose())
            .bounds(width - bw - 10, height - 26, bw, 20).build());
        addRenderableWidget(Button.builder(EmoteText.ui("reset", "Скинути"), b -> {
            EmoteWheel w = ShaurmaEmotes.wheel();
            w.reset();
            w.fillEmpty(ShaurmaEmotes.available());
            EmoteClient.save();
            refresh();
        }).bounds(width - 2 * bw - 16, height - 26, bw, 20).build());
    }

    private void refresh() { slots = EmoteClient.resolvedSlots(); }

    @Override
    public void tick() {
        Emote show = dragging != null ? dragging : hoverEmote != null ? hoverEmote : selected;
        preview.show(show);
        preview.tick();
    }

    // ── Рендер ───────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        hoverEmote = null;

        // список
        g.fill(listX - 2, listY - 2, listX + listW + 2, listY + listH + 2, 0x90000000);
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (Row r : rows) {
            int y = listY + r.y - scroll;
            if (y + ROW < listY || y > listY + listH) continue;
            if (r.emote == null) {
                g.drawString(font, EmoteText.category(r.category).copy().withStyle(s -> s.withBold(true)), listX + 3, y + 3, 0xFFD9A441);
                continue;
            }
            boolean over = mx >= listX && mx < listX + listW && my >= y && my < y + ROW && my >= listY && my < listY + listH;
            if (over) hoverEmote = r.emote;
            int inWheel = ShaurmaEmotes.wheel().indexOf(r.emote.id());
            int bg = r.emote == selected ? 0xC0D9A441 : over ? 0x60FFFFFF : 0x30FFFFFF;
            g.fill(listX + 1, y + 1, listX + listW - 1, y + ROW - 1, bg);
            g.drawString(font, EmoteText.name(r.emote), listX + 6, y + 4, 0xFFFFFFFF);
            String tag = (r.emote.canMove() ? "↔ " : "■ ") + (inWheel >= 0 ? "#" + (inWheel + 1) : "");
            g.drawString(font, tag, listX + listW - 6 - font.width(tag), y + 4, inWheel >= 0 ? 0xFF8FD18F : 0xFF909090);
        }
        g.disableScissor();

        // колесо слотів
        int seg = WheelDraw.segmentAt(mx, my, cx, cy, r0 * 0.6f, r1 + 6);
        if (seg >= 0 && dragging == null && slots[seg] != null) hoverEmote = slots[seg];
        WheelDraw.wheel(g, slots, cx, cy, r0, r1, dragging != null ? seg : seg >= 0 ? seg : highlightSlot, null);
        WheelDraw.disc(g, cx, cy, r0 - 3, WheelDraw.COL_CENTER);
        for (int i = 0; i < EmoteWheel.SLOTS; i++) {
            float rad = WheelDraw.centerAngle(i) * Mth.DEG_TO_RAD;
            g.drawCenteredString(font, String.valueOf(i + 1), Math.round(cx + Mth.cos(rad) * (r1 + 11)),
                Math.round(cy + Mth.sin(rad) * (r1 + 11)) - 4, 0x90FFFFFF);
        }

        // 3D-превʼю посередині
        Minecraft mc = Minecraft.getInstance();
        int left = listX + listW + 8, right = Math.round(cx - r1 - 12);
        int pcx = (left + right) / 2;
        g.fill(left, listY - 2, right, listY + listH + 2, 0x50000000);
        Emote shown = preview.current();
        if (mc.player != null && right - left > 40) {
            int scale = Math.min(Math.round(listH * 0.36f), Math.round((right - left) * 0.42f));
            g.enableScissor(left, listY, right, listY + listH);
            preview.render(g, mc.player, pcx, listY + Math.round(listH * 0.84f), scale, -18f);
            g.disableScissor();
        }
        if (shown != null) {
            g.drawCenteredString(font, EmoteText.name(shown), pcx, listY + 4, 0xFFFFFFFF);
            Component how = shown.canMove() ? EmoteText.ui("can_move", "можна йти") : EmoteText.ui("full_body", "усе тіло");
            g.drawCenteredString(font, how, pcx, listY + 16, 0xFFA0A0A0);
        }

        Component hint = EmoteText.ui("settings.hint", "Перетягни емоцію в слот або вибери її і клацни слот · ПКМ по слоту — очистити");
        int hy = listY + listH + 6;
        for (var line : font.split(hint, width - 2 * 96 - 30)) {
            g.drawString(font, line, 10, hy, 0xFF909090);
            hy += 10;
        }
        super.render(g, mx, my, partial);

        if (dragging != null && (Math.abs(mx - pressX) > 3 || Math.abs(my - pressY) > 3)) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            Component n = EmoteText.name(dragging);
            int w = font.width(n) + 8;
            g.fill(mx - w / 2, my - 6, mx + w / 2, my + 7, 0xE0D9A441);
            g.drawCenteredString(font, n, mx, my - 3, 0xFFFFFFFF);
            g.pose().popPose();
        }
    }

    // ── Мишка ────────────────────────────────────────────────────────────

    private Emote rowAt(double mx, double my) {
        if (mx < listX || mx >= listX + listW || my < listY || my >= listY + listH) return null;
        for (Row r : rows) {
            int y = listY + r.y - scroll;
            if (r.emote != null && my >= y && my < y + ROW) return r.emote;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int seg = WheelDraw.segmentAt(mx, my, cx, cy, r0 * 0.6f, r1 + 6);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && seg >= 0) {
            ShaurmaEmotes.wheel().clear(seg);
            EmoteClient.save();
            refresh();
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        pressX = mx;
        pressY = my;
        Emote row = rowAt(mx, my);
        if (row != null) {
            selected = row;
            dragging = row;
            dragFromSlot = -1;
            pressedSlot = -1;
            pressedRow = true;
            return true;
        }
        if (seg >= 0) {
            pressedSlot = seg;
            pressedRow = false;
            dragging = slots[seg];
            dragFromSlot = slots[seg] != null ? seg : -1;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseReleased(mx, my, button);
        boolean moved = Math.abs(mx - pressX) > 3 || Math.abs(my - pressY) > 3;
        int seg = WheelDraw.segmentAt(mx, my, cx, cy, r0 * 0.6f, r1 + 6);
        EmoteWheel w = ShaurmaEmotes.wheel();
        boolean changed = false;
        if (moved && dragging != null) {
            if (seg >= 0) {
                if (dragFromSlot >= 0) w.swap(dragFromSlot, seg);
                else w.set(seg, dragging.id());
                highlightSlot = seg;
                changed = true;
            } else if (dragFromSlot >= 0 && rowAt(mx, my) == null) {
                w.clear(dragFromSlot); // витягли за межі колеса — прибрати
                changed = true;
            }
        } else if (!moved && !pressedRow && pressedSlot >= 0) {
            Emote inSlot = slots[pressedSlot];
            if (selected != null && selected != inSlot) {
                w.set(pressedSlot, selected.id());
                changed = true;
            } else if (inSlot != null) {
                selected = inSlot;
            }
            highlightSlot = pressedSlot;
        }
        dragging = null;
        dragFromSlot = pressedSlot = -1;
        pressedRow = false;
        if (changed) {
            EmoteClient.save();
            refresh();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= listX && mx < listX + listW) {
            scroll = Mth.clamp(scroll - (int) Math.round(delta * ROW * 2), 0, Math.max(0, contentH - listH));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void onClose() {
        EmoteClient.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Для автотесту ───────────────────────────────────────────────────

    public EmotePreview preview() { return preview; }

    public void debugSelect(Emote e) { selected = e; }

    public void debugAssign(int slot, Emote e) {
        ShaurmaEmotes.wheel().set(slot, e.id());
        EmoteClient.save();
        refresh();
        highlightSlot = slot;
    }
}
