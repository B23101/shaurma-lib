package dev.shaurmalib.forge.overlay;

import dev.shaurmalib.common.overlay.ActionBarMessageType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Узагальнена система actionbar-повідомлень над хотбаром (план, п. 3.4 +
 * 3.10) — заміна {@code StatusMessageOverlay} (переносить 1:1 shake-фізику
 * для {@code ERROR} і fade-in/fade-out для решти типів). Один статичний
 * API {@code ActionBarMessageSystem.show(type, text)} замість
 * {@code showMessage(json, type)} + окремий пакет {@code StatusMessagePacket}
 * на кожен виклик — консюмер сам вирішує, чи загортати виклик у мережевий
 * пакет (сервер→клієнт), рушій тут — суто клієнтський рендер.
 * <p>
 * Клас навмисно не прив'язаний до "кітів" (у snipers оригінал
 * {@code StatusMessageOverlay} обслуговував і помилки кіт-здібностей, і
 * інші статусні повідомлення) — будь-яка підсистема мода може викликати
 * {@link #show}, це загальний actionbar-рушій бібліотеки.
 * <p>
 * Де-дублікація: якщо той самий текст показано повторно, поки попередній
 * показ ще не зник — час показу лише продовжується (не запускається нова
 * анімація появи спочатку), як у {@code StatusMessageOverlay.showMessage}.
 */
@OnlyIn(Dist.CLIENT)
public final class ActionBarMessageSystem {

    private static final int DISPLAY_MS = 1000;
    private static final int FADE_MS = 200;
    private static final int SHAKE_MS = 300;
    private static final int ABOVE_HOTBAR = 16;

    private static String currentText = "";
    private static ActionBarMessageType currentType = ActionBarMessageType.INFO;
    private static long shownAt = 0L;
    private static Supplier extraTopOffset = () -> 0;

    private ActionBarMessageSystem() {}

    @FunctionalInterface
    public interface Supplier {
        int get();
    }

    /**
     * Дозволяє консюмеру зсунути позицію панелі вище (в оригіналі — коли
     * {@code RadioDialogOverlay} активний, {@code StatusMessageOverlay}
     * піднімався на {@code lineHeight+8}, щоб не перекривати субтитри
     * диктора). Рушій лібу сам не знає нічого про радіо-модуль, тому
     * консюмер передає supplier замість того, щоб рушій імпортував
     * конкретний клас іншого модуля лібу.
     */
    public static void setExtraTopOffsetSupplier(Supplier supplier) {
        extraTopOffset = supplier != null ? supplier : () -> 0;
    }

    public static void show(ActionBarMessageType type, String text) {
        if (text == null) return;
        long now = System.currentTimeMillis();
        if (text.equals(currentText) && now - shownAt < DISPLAY_MS + FADE_MS) {
            shownAt = now;
            return;
        }
        currentText = text;
        currentType = type;
        shownAt = now;
    }

    public static void reset() {
        currentText = "";
        shownAt = 0L;
    }

    static void render(GuiGraphics g, int sw, int sh) {
        if (currentText.isEmpty() || shownAt <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        long now = System.currentTimeMillis();
        long elapsed = now - shownAt;
        if (elapsed > DISPLAY_MS + FADE_MS) {
            currentText = "";
            return;
        }

        float alpha = 1f;
        float translateY = 0f;
        float offsetX = 0f;
        float offsetY = 0f;

        if (elapsed < DISPLAY_MS) {
            float t = Math.min(1f, elapsed / (float) FADE_MS);
            if (currentType.shakeOnShow()) {
                float shakeIntensity = 1f - Math.min(1f, elapsed / (float) SHAKE_MS);
                offsetX = (float) Math.sin(elapsed * 0.3) * shakeIntensity * 4f;
                offsetY = (float) Math.cos(elapsed * 0.5) * shakeIntensity * 2f;
            } else {
                alpha = Math.min(1f, t * 2f);
            }
        } else {
            float fadeProgress = (elapsed - DISPLAY_MS) / (float) FADE_MS;
            alpha = 1f - fadeProgress;
            translateY = fadeProgress * 12f;
        }

        g.pose().pushPose();
        g.pose().translate(offsetX, offsetY + translateY, 0);

        int argb = (Math.round(alpha * 255) << 24) | (currentType.defaultColorArgb() & 0x00FFFFFF);

        int textW = mc.font.width(currentText);
        int x = (sw - textW) / 2;
        int hotbarTop = sh - 22;
        int y = hotbarTop - ABOVE_HOTBAR - mc.font.lineHeight - 4 - extraTopOffset.get();

        g.drawString(mc.font, currentText, x + 1, y + 1, (Math.round(alpha * 80) << 24), false);
        g.drawString(mc.font, currentText, x, y, argb, false);

        g.pose().popPose();
    }

    /** Реєструє себе в {@link OverlayEngine} під фіксованим id — викликати один раз при клієнтському сетапі. */
    public static void attach() {
        OverlayEngine.register("shaurma_actionbar_message", (gui, graphics, partialTick, sw, sh) -> render(graphics, sw, sh));
    }
}
