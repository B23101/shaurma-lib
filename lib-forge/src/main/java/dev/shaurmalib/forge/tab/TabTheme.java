package dev.shaurmalib.forge.tab;

import dev.shaurmalib.common.tab.TabColumn;
import dev.shaurmalib.common.tab.TabTeamBarSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Тема Tab-таблиці — ЄДИНА точка, де консюмер shaurma-lib підміняє
 * ВИГЛЯД таблиці (палітра, рамка панелі, фон рядків, голови, ефекти),
 * не переписуючи рушій {@link TabListStyle}.
 * <p>
 * <b>Навіщо.</b> Раніше {@link TabListStyle} мав золоту палітру
 * snipers_shaurma зашитою у власні методи: моду з іншою естетикою
 * лишалось або терпіти золото, або копіювати клас цілком. Тепер
 * {@code TabListStyle} лише координує (анімація позицій/прозорості,
 * порядок викликів у {@link TabListStyle#drawRow}), а КОЖЕН
 * візуальний примітив делегує в тему.
 * <p>
 * <b>Як користуватись.</b> Найпростіше — успадкувати
 * {@link DefaultTabTheme} і перевизначити лише те, що треба:
 * <pre>{@code
 * final class MyTheme extends DefaultTabTheme {
 *     @Override public void drawPanel(GuiGraphics g, int x, int y, int w, int h, float anim, long tick) { ... }
 * }
 * TabListStyle style = new TabListStyle(this::resolveSkin, new MyTheme());
 * }</pre>
 * Тема безстанова щодо анімацій ПОЗИЦІЇ рядків (їх веде
 * {@link TabListStyle}), але може тримати власний візуальний стан
 * (частинки тощо) — instance створюється консюмером.
 * <p>
 * {@code tick} у всіх методах — лічильник кадрів {@link TabListStyle#tickCount()}
 * (той самий, що живить пульсації), а не гейм-тік світу: тому ефекти
 * не завмирають на паузі й не залежать від TPS.
 */
public interface TabTheme {

    // ── Метрики розкладки ────────────────────────────────────────────
    // Дефолтні значення збігаються з константами TabListStyle (HEAD_SIZE,
    // ROW_H, TITLE_H, HDR_H): існуючі консюмери не бачать різниці.

    default int headSize()          { return 12; }
    default int rowHeight()         { return 16; }
    default int titleHeight()       { return 28; }
    default int columnHeaderHeight(){ return 14; }

    /** X-зсув голови від лівого краю рядка (для {@link TabListStyle#drawRow}). */
    default int headOffsetX()       { return 14; }
    /** X-зсув імені від лівого краю рядка. */
    default int nameOffsetX()       { return 30; }
    /** X-зсув rank-тексту від лівого краю рядка. */
    default int rankOffsetX()       { return 3; }

    // ── Панель / заголовок ───────────────────────────────────────────

    void drawPanel(GuiGraphics g, int x, int y, int w, int h, float anim, long tick);

    void drawHeader(GuiGraphics g, Font font, int cx, int sy, float anim,
                    String titleKey, String subtitleKey, long tick);

    void drawOnlineCount(GuiGraphics g, Font font, int cx, int y, float anim,
                         String countKey, int count, long tick);

    void drawColumnHeaderBg(GuiGraphics g, int x, int y, int w, float anim, long tick);

    void drawColumnHeaders(GuiGraphics g, Font font, int ox, int y,
                           List<TabColumn> columns, float anim, long tick);

    // ── Рядок ────────────────────────────────────────────────────────

    void drawRowBg(GuiGraphics g, int x, int y, int w, int h, int rank,
                   boolean isMe, int teamTint, float anim, long tick);

    void drawGoalPulse(GuiGraphics g, int x, int y, int w, int h, float anim, long tick);

    void drawRespawning(GuiGraphics g, Font font, int x0, int rowW, int rowH,
                        int textX, int textY, String name, String respawnLabel,
                        float anim, long tick);

    void drawStrikeThrough(GuiGraphics g, Font font, String text, int x, int y,
                           float anim, long tick);

    // ── Команди / голова ─────────────────────────────────────────────

    void drawTeamBar(GuiGraphics g, Font font, int x, int y, int w, int h,
                     TabTeamBarSpec spec, float anim, long tick);

    /**
     * @param skin {@code null} — скін іще не відомий; тема сама вирішує,
     *             малювати заглушку чи нічого (дефолт — нічого).
     * @param size сторона квадрата голови, px
     */
    void drawHead(GuiGraphics g, ResourceLocation skin, int x, int y, int size,
                  float anim, long tick);
}
