package dev.shaurmalib.render.overlay;

import dev.shaurmalib.common.overlay.CountdownDigitEntry;
import dev.shaurmalib.render.Argb;
import dev.shaurmalib.render.GuiCanvas;
import java.util.function.LongSupplier;

/**
 * Анімований зворотний відлік по центру екрана.
 * <p>
 * Це 1:1 перенесена логіка {@code AnimatedCountdownSystem} (було: {@code GuiGraphics},
 * {@code Minecraft.getInstance().font}, {@code System.currentTimeMillis()} у Forge-модулі).
 * Тепер: жодного Minecraft, годинник інжектується — тож працює однаково на всіх версіях і лоадерах
 * і тестується без гри.
 */
public final class CountdownOverlay implements Overlay {
    public static final String ID = "shaurma_animated_countdown";

    private final LongSupplier clockMs;
    private CountdownDigitEntry active;
    private int accentArgb = 0xFFFFFF00;

    public CountdownOverlay(LongSupplier clockMs) { this.clockMs = clockMs; }
    public CountdownOverlay() { this(System::currentTimeMillis); }

    public void show(int digit, int accentArgbColor) {
        accentArgb = accentArgbColor;
        active = new CountdownDigitEntry(digit, clockMs.getAsLong(), 220, 620, 160);
    }
    public void clear() { active = null; }
    public boolean isActive() { return active != null; }

    @Override
    public void render(GuiCanvas c, float partialTick) {
        if (active == null) return;
        long now = clockMs.getAsLong();
        if (active.isExpired(now)) { active = null; return; }

        String text = String.valueOf(active.digit());
        float scale = active.scaleAt(now) * 4f;
        int argb = Argb.withAlpha(accentArgb, active.alphaAt(now));

        c.pushTransform();
        c.translate(c.width() / 2f, c.height() / 2f - 28);
        c.scale(scale, scale);
        c.drawText(text, -c.textWidth(dev.shaurmalib.platform.text.Text.literal(text)) / 2, 0, argb, true);
        c.popTransform();
    }
}
