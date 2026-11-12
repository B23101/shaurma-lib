package dev.shaurmalib.render.cmd;

import dev.shaurmalib.platform.ref.ResourceId;
import dev.shaurmalib.platform.text.Text;
import dev.shaurmalib.render.GuiCanvas;

/**
 * Дані одного виклику {@link GuiCanvas}. Кожна команда сама знає, як себе виконати
 * (поліморфізм замість switch) — запис кадру можна відтворити на будь-якому канвасі
 * і порівняти в тестах без гри.
 */
public sealed interface RenderCommand {
    void applyTo(GuiCanvas c);

    record Fill(int x, int y, int w, int h, int argb) implements RenderCommand {
        public void applyTo(GuiCanvas c) { c.fillRect(x, y, w, h, argb); }
    }
    record GradientV(int x, int y, int w, int h, int top, int bottom) implements RenderCommand {
        public void applyTo(GuiCanvas c) { c.fillGradientV(x, y, w, h, top, bottom); }
    }
    record DrawText(Text text, int x, int y, int argb, boolean shadow) implements RenderCommand {
        public void applyTo(GuiCanvas c) { c.drawText(text, x, y, argb, shadow); }
    }
    record Blit(ResourceId tex, int x, int y, int w, int h, float u, float v, float uw, float vh,
                int texW, int texH, int tint) implements RenderCommand {
        public void applyTo(GuiCanvas c) { c.blit(tex, x, y, w, h, u, v, uw, vh, texW, texH, tint); }
    }
    record Push() implements RenderCommand { public void applyTo(GuiCanvas c) { c.pushTransform(); } }
    record Pop() implements RenderCommand { public void applyTo(GuiCanvas c) { c.popTransform(); } }
    record Translate(float x, float y) implements RenderCommand { public void applyTo(GuiCanvas c) { c.translate(x, y); } }
    record Scale(float sx, float sy) implements RenderCommand { public void applyTo(GuiCanvas c) { c.scale(sx, sy); } }
    record RotateZ(float degrees) implements RenderCommand { public void applyTo(GuiCanvas c) { c.rotateZ(degrees); } }
    record PushScissor(int x, int y, int w, int h) implements RenderCommand {
        public void applyTo(GuiCanvas c) { c.pushScissor(x, y, w, h); }
    }
    record PopScissor() implements RenderCommand { public void applyTo(GuiCanvas c) { c.popScissor(); } }
}
