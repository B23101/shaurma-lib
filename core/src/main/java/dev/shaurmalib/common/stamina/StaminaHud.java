package dev.shaurmalib.common.stamina;

import dev.shaurmalib.render.GuiCanvas;
import dev.shaurmalib.render.overlay.Overlay;
import java.util.Objects;

/**
 * HUD-смуга stamina — один раз для всіх версій (було: {@code GuiGraphics} + {@code Minecraft.font} у Forge-хуку).
 * Споживач може підмінити вигляд через {@link #renderer}.
 */
public final class StaminaHud implements Overlay {
    public static final String ID = "shaurma_stamina";

    @FunctionalInterface
    public interface Renderer { void render(GuiCanvas c, float stamina, float max, float partialTick); }

    private final StaminaClientState state;
    private volatile Renderer renderer = StaminaHud::renderDefault;

    public StaminaHud(StaminaClientState state) { this.state = Objects.requireNonNull(state); }

    public void renderer(Renderer r) { this.renderer = r == null ? StaminaHud::renderDefault : r; }

    @Override public void render(GuiCanvas c, float partialTick) {
        if (!state.isActive()) return;
        renderer.render(c, state.stamina(), state.maxStamina(), partialTick);
    }

    public static void renderDefault(GuiCanvas c, float stamina, float max, float partialTick) {
        if (max <= 0) return;
        int barW = 120, barH = 5, x = (c.width() - barW) / 2, y = c.height() - 42;
        int filled = Math.round(barW * Math.max(0f, Math.min(1f, stamina / max)));
        c.fillRect(x, y, barW, barH, 0xAA202020);
        c.fillRect(x, y, filled, barH, 0xFF35C9A0);
        c.drawCenteredText(dev.shaurmalib.platform.text.Text.literal(Math.round(stamina) + " / " + Math.round(max)),
                c.width() / 2, y - 10, 0xFFFFFFFF, true);
    }
}
