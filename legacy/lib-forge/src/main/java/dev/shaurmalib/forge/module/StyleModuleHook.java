package dev.shaurmalib.forge.module;

import dev.shaurmalib.common.style.StylePalette;
import dev.shaurmalib.forge.style.StyleTheme;

/**
 * Точка вбудовування рушія стилів (план, п. 3.3) —
 * {@link StyleTheme} (панелі/cut-corner слоти/tooltip-и, {@code GuiGraphics}-
 * рендер) + {@link StylePalette} (Forge-незалежна кольорова палітра,
 * lib-common) + {@link dev.shaurmalib.common.style.HotbarLayout} /
 * {@link dev.shaurmalib.common.style.HotbarPanelRenderer} (LEFT/CENTER/
 * RIGHT позиціонування hotbar-панелі — нова функціональність, якої не
 * було в оригіналі, див. клас-докстрінг {@code HotbarPanelRenderer}).
 * <p>
 * Як і {@link LeaderboardModuleHook}/{@link MarkersModuleHook}, НЕ
 * підключається через {@code ShaurmaLib.Builder.withXxx(...)} —
 * {@link StyleTheme} не має підписки на event bus (не рендерить нічого
 * сам), консюмер створює один екземпляр
 * {@code new StyleTheme(StylePalette.DEFAULT)} (або власну
 * {@link StylePalette}) і зберігає його в полі свого клієнтського стану,
 * використовуючи для ВСІХ своїх {@code Screen}/{@code IGuiOverlay} так
 * само, як в оригіналі всі 40+ HUD-класів викликали статичні методи
 * {@code OverlayStyle}.
 * <p>
 * <b>Не перенесено в цій ітерації</b> (лишається продуктовою специфікою
 * консюмера до наступного етапу): {@code TabListStyle}/
 * {@code TabListLineProvider} (план, 3.3+3.7, джерело —
 * {@code CustomTabOverlay}, 1428 рядків, дуже продукто-специфічний
 * layout з командними кольорами/аватарами) і
 * {@code MenuStyleTheme}/{@code AbstractShaurmaScreen} базовий екран
 * (джерело — спільна частина {@code GameSettingsScreen}). Обидва
 * потребують окремого детального проходу через розмір і специфічність
 * оригіналів — {@link StyleTheme} тут дає фундамент (панелі/tooltip-и/
 * cut-corner примітиви), на якому вони будуватимуться.
 */
public interface StyleModuleHook {
    void onAttach(FMLModuleContext ctx);
}
