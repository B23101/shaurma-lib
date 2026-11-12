package dev.shaurmalib.meta.mc.skin;

import dev.shaurmalib.meta.MetaTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Скін меню мета-двигуна. Бібліотека відповідає за ЩО на екрані (дані, компонування, анімація станів,
 * ввід), мод — за ЯК воно намальоване. Кожен режим реєструє свій скін ({@link MetaSkins#register}) і
 * малює фон, панелі, кнопки, вкладки, картки, смужки, ярлики своїми примітивами — тож гардероб,
 * магазин, пропуск, профіль і підсумки виглядають як рідні меню цього мода, а не як чужий шаблон.
 *
 * <p>Координати — «дизайнові» (екран 960×540, масштабований рівномірно), крім {@link #backdrop}, що
 * отримує реальні пікселі GUI. Час {@code time} — секунди з відкриття екрана; {@code hover} — 0..1 плавно.</p>
 */
public interface MetaSkin {

    /** Вид кнопки. */
    enum Kind { NORMAL, PRIMARY, DANGER, SELECTED }

    /** Кольори тексту й станів (RGB). */
    MetaTheme theme();

    /** Повноекранний фон під меню (пікселі GUI). {@code open} 0..1 — поява. */
    void backdrop(GuiGraphics g, int w, int h, float time, float open);

    /** Панель-контейнер. {@code main} — головна панель екрана (може мати особливе оздоблення). */
    void panel(GuiGraphics g, int x, int y, int w, int h, boolean main, float time);

    /** Заголовок екрана: дрібний надрядок + великий заголовок. Займає висоту {@link #headerHeight()}. */
    void header(GuiGraphics g, Font f, int x, int y, String overline, String title, float time);

    default int headerHeight() { return 40; }

    /** Підзаголовок секції всередині панелі («СКРИНІ», «ПРОПОЗИЦІЇ ДНЯ»); повертає висоту. */
    int sectionTitle(GuiGraphics g, Font f, int x, int y, int w, String title, String right, float time);

    /** Ширина вкладки навігації з цим підписом. */
    int tabWidth(Font f, String label);

    void tab(GuiGraphics g, Font f, int x, int y, int w, int h, String label, String icon, boolean active, float hover);

    void button(GuiGraphics g, Font f, int x, int y, int w, int h, String label, Kind kind, float hover, boolean enabled, float time);

    /** Рамка картки предмета ({@code color} — колір рідкості). Малюється ДО вмісту. */
    void card(GuiGraphics g, int x, int y, int w, int h, int color, boolean selected, float hover, float time);

    /** Поверх вмісту картки (рамки виділення, блиск) — після 3D-моделі. */
    default void cardOverlay(GuiGraphics g, int x, int y, int w, int h, int color, boolean selected, float hover, float time) { }

    /** «Сцена» під 3D-прев'ю (картка, примірка, пропуск). */
    void stage(GuiGraphics g, int x, int y, int w, int h, int color, float time, float hover);

    /** Велике вікно примірки в гардеробі: фон за манекеном і помост під ногами. */
    void viewport(GuiGraphics g, int x, int y, int w, int h, int footX, int footY, int color, float time, float open);

    /** Смужка прогресу. */
    void bar(GuiGraphics g, int x, int y, int w, int h, float fill, int color, float time);

    /** Чіп (валюта, таймер, «у тебе: 3»); повертає ширину. */
    int chip(GuiGraphics g, Font f, int x, int y, String text, String icon, int color);

    /** Ярлик рідкості / знижки. */
    void tag(GuiGraphics g, Font f, int x, int y, String text, int color, boolean solid);

    void divider(GuiGraphics g, int x, int y, int w);

    /** Емблема рівня (шестикутник, ромб рангу…). {@code flash} 0..1 — спалах нового рівня. */
    void levelBadge(GuiGraphics g, Font f, int cx, int cy, int r, int level, int color, float flash, float time);

    /** Позначка «вибрано / наведено» навколо прямокутника. */
    void focus(GuiGraphics g, int x, int y, int w, int h, int color, float time);

    /** Спливаюча картка «Отримано» (пікселі GUI). */
    void toast(GuiGraphics g, int x, int y, int w, int h, float alpha, float time);

    /**
     * Власна іконка мода замість піксельної бібліотечної. {@code size} — сторона в пікселях (8 або 16…).
     * @return false — мод такої не має, бібліотека намалює свою
     */
    default boolean icon(GuiGraphics g, String id, int x, int y, int size, int argb, float hover) { return false; }

    /** Іскри/ефекти частинок у меню — дехто з модів не хоче «феєрверків». */
    default boolean sparks() { return true; }
}
