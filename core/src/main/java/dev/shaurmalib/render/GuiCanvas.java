package dev.shaurmalib.render;

import dev.shaurmalib.platform.ref.ResourceId;
import dev.shaurmalib.platform.text.Text;

/**
 * 2D-полотно HUD/екранів. ЄДИНА точка контакту з рендером ванілі:
 * у 1.20.1/1.21.1 його реалізує обгортка над {@code GuiGraphics}, у 26.x — над новим
 * render-pipeline. Уся логіка оверлеїв, віджетів і екранів пишеться лише проти цього
 * інтерфейсу — один раз для всіх версій і лоадерів.
 * <p>
 * Координати — у GUI-масштабованих пікселях. Колір — 0xAARRGGBB.
 */
public interface GuiCanvas {
    int width();
    int height();

    void fillRect(int x, int y, int w, int h, int argb);
    void fillGradientV(int x, int y, int w, int h, int topArgb, int bottomArgb);
    void drawText(Text text, int x, int y, int argb, boolean shadow);
    int textWidth(Text text);
    int lineHeight();
    /** Малює регіон текстури (u,v,uw,vh у пікселях атласу розміром texW×texH). */
    void blit(ResourceId texture, int x, int y, int w, int h,
              float u, float v, float uw, float vh, int texW, int texH, int tintArgb);

    void pushTransform();
    void popTransform();
    void translate(float x, float y);
    void scale(float sx, float sy);
    void rotateZ(float degrees);

    void pushScissor(int x, int y, int w, int h);
    void popScissor();

    // ----- зручні надбудови (default => адаптерам не треба повторювати) -----
    default void drawText(String s, int x, int y, int argb, boolean shadow) {
        drawText(Text.literal(s), x, y, argb, shadow);
    }
    default void drawCenteredText(Text t, int cx, int y, int argb, boolean shadow) {
        drawText(t, cx - textWidth(t) / 2, y, argb, shadow);
    }
    default void strokeRect(int x, int y, int w, int h, int th, int argb) {
        fillRect(x, y, w, th, argb);
        fillRect(x, y + h - th, w, th, argb);
        fillRect(x, y + th, th, h - 2 * th, argb);
        fillRect(x + w - th, y + th, th, h - 2 * th, argb);
    }
}
