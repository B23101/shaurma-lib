package dev.shaurmalib.render.overlay;

import dev.shaurmalib.render.GuiCanvas;

/** HUD-шар. Реалізується один раз; кожна версія/лоадер лише викликає {@link OverlayRegistry#renderAll}. */
@FunctionalInterface
public interface Overlay {
    void render(GuiCanvas canvas, float partialTick);
}
