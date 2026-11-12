package dev.shaurmalib.anim.mc.emote;

import dev.shaurmalib.anim.emote.Emote;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Колесо емоцій. Відкривається утриманням клавіші (B): навів на сегмент — у центрі ти сам, у своєму скіні,
 * вже грає цю емоцію; відпустив — емоція йде на сервер і її бачать усі. Відпустив у центрі — нічого.
 * Коротке натискання, поки емоція грає, — зупинити її. ПКМ по сегменту — налаштування слотів.
 */
public final class EmoteWheelScreen extends Screen {

    /** Скільки тіків триває «коротке натискання». */
    private static final int TAP_TICKS = 5;

    private final boolean holdMode;
    private final EmotePreview preview = new EmotePreview();
    private Emote[] slots;
    private int hovered = -1, forcedHover = -2, age;
    private boolean done;
    private float cx, cy, r0, r1;

    /** @param holdMode true — закривається, коли відпустили клавішу (звичайне відкриття з гри) */
    public EmoteWheelScreen(boolean holdMode) {
        super(EmoteText.ui("wheel", "Емоції"));
        this.holdMode = holdMode;
    }

    @Override
    protected void init() {
        slots = EmoteClient.resolvedSlots();
        r1 = Math.min(width, height) * 0.40f;
        r0 = r1 * 0.46f;
        cx = width / 2f;
        cy = height / 2f + 6;
    }

    @Override
    public void tick() {
        age++;
        preview.show(hovered >= 0 ? slots[hovered] : EmoteClient.activeLocal());
        preview.tick();
        if (holdMode && !done && !EmoteClient.keyHeld()) release();
        if (!EmoteClient.canOpen()) onClose(); // впав / почалась дія — колесо зникає саме
    }

    /** Відпустили клавішу або клацнули: зіграти наведену, коротке натискання — зупинити. */
    private void release() {
        if (done) return;
        done = true;
        Emote e = hovered >= 0 ? slots[hovered] : null;
        if (e != null) EmoteClient.request(e);
        else if (age <= TAP_TICKS && EmoteClient.activeLocal() != null) EmoteClient.stop();
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        hovered = forcedHover >= -1 ? forcedHover : WheelDraw.segmentAt(mouseX, mouseY, cx, cy, r0 * 0.55f, 0);
        if (hovered >= 0 && slots[hovered] == null) hovered = -1;
        g.fillGradient(0, 0, width, height, 0x50000000, 0x70000000);

        WheelDraw.wheel(g, slots, cx, cy, r0, r1, hovered, EmoteClient.activeLocal());
        WheelDraw.disc(g, cx, cy, r0 - 3, WheelDraw.COL_CENTER);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            g.enableScissor(Math.round(cx - r0), Math.round(cy - r0), Math.round(cx + r0), Math.round(cy + r0));
            int scale = Math.round(r0 * 0.6f);
            preview.render(g, mc.player, Math.round(cx), Math.round(cy + r0 * 0.6f), scale, 0f);
            g.disableScissor();
        }

        Emote h = hovered >= 0 ? slots[hovered] : null;
        Component title = h != null ? EmoteText.name(h) : this.title;
        g.drawCenteredString(font, title, Math.round(cx), Math.round(cy - r1 - 18), 0xFFFFFFFF);
        Component hint = EmoteText.ui("wheel.hint", "Відпусти — зіграти · у центрі — скасувати · ПКМ — налаштувати");
        g.drawCenteredString(font, hint, Math.round(cx), Math.round(cy + r1 + 10), 0xFFB0B0B0);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int seg = WheelDraw.segmentAt(mx, my, cx, cy, r0 * 0.55f, 0);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (seg >= 0 && slots[seg] != null) {
                hovered = seg;
                release();
            } else if (seg < 0) {
                done = true;
                onClose();
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            done = true;
            EmoteClient.openSettings(seg);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // у режимі «не утримання» (відкрито кодом/тестом) повторне натискання клавіші колеса закриває його
        if (!holdMode && EmoteClient.KEY.matches(key, scan)) {
            release();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Для автотесту ───────────────────────────────────────────────────

    /** Навести сегмент без мишки (−1 — центр, −2 — знову мишка). */
    public void debugHover(int segment) { forcedHover = segment; hovered = segment; }

    /** Вибрати наведений сегмент, як при відпусканні клавіші. */
    public void debugRelease() { release(); }

    public EmotePreview preview() { return preview; }
}
