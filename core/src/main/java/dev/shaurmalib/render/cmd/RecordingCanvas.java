package dev.shaurmalib.render.cmd;

import dev.shaurmalib.platform.ref.ResourceId;
import dev.shaurmalib.platform.text.Text;
import dev.shaurmalib.render.GuiCanvas;
import java.util.ArrayList;
import java.util.List;

/**
 * Канвас, що не малює, а записує команди. Дві користі:
 * (1) юніт-тести рендер-логіки без Minecraft; (2) {@link #replay} на справжній канвас
 * (корисно для кешування статичних панелей).
 * Метрики тексту — фіксовані (6 px/символ, висота 9), як у ванільного шрифту приблизно.
 */
public final class RecordingCanvas implements GuiCanvas {
    private final int w, h;
    private final List<RenderCommand> cmds = new ArrayList<>();

    public RecordingCanvas(int w, int h) { this.w = w; this.h = h; }

    public List<RenderCommand> commands() { return cmds; }

    public void replay(GuiCanvas target) {
        for (RenderCommand c : cmds) c.applyTo(target);
    }

    @Override public int width() { return w; }
    @Override public int height() { return h; }
    @Override public void fillRect(int x, int y, int w, int h, int argb) { cmds.add(new RenderCommand.Fill(x, y, w, h, argb)); }
    @Override public void fillGradientV(int x, int y, int w, int h, int t, int b) { cmds.add(new RenderCommand.GradientV(x, y, w, h, t, b)); }
    @Override public void drawText(Text t, int x, int y, int argb, boolean s) { cmds.add(new RenderCommand.DrawText(t, x, y, argb, s)); }
    @Override public int textWidth(Text t) { return t.plain().length() * 6; }
    @Override public int lineHeight() { return 9; }
    @Override public void blit(ResourceId tex, int x, int y, int w, int h, float u, float v, float uw, float vh, int tw, int th, int tint) {
        cmds.add(new RenderCommand.Blit(tex, x, y, w, h, u, v, uw, vh, tw, th, tint));
    }
    @Override public void pushTransform() { cmds.add(new RenderCommand.Push()); }
    @Override public void popTransform() { cmds.add(new RenderCommand.Pop()); }
    @Override public void translate(float x, float y) { cmds.add(new RenderCommand.Translate(x, y)); }
    @Override public void scale(float sx, float sy) { cmds.add(new RenderCommand.Scale(sx, sy)); }
    @Override public void rotateZ(float d) { cmds.add(new RenderCommand.RotateZ(d)); }
    @Override public void pushScissor(int x, int y, int w, int h) { cmds.add(new RenderCommand.PushScissor(x, y, w, h)); }
    @Override public void popScissor() { cmds.add(new RenderCommand.PopScissor()); }
}
