package dev.shaurmalib.common.style;

/**
 * Обчислення позиції кастомної hotbar-панелі відносно ширини екрана
 * (план, п. 3.3) — НОВА функціональність, якої не було в оригінальному
 * {@code CustomHotbarOverlay} snipers_shaurma (там панель завжди
 * тримається по центру, як ванільний hotbar — див. клас-докстрінг
 * оригіналу: "тримається знизу по центру екрана — стандартна позиція").
 * План вимагає {@link dev.shaurmalib.common.style.HotbarLayout}
 * (LEFT/CENTER/RIGHT) — цей клас дає чисту математику обчислення
 * X-координати лівого краю панелі, консюмер передає лише готову ширину
 * вмісту (суму ширин слотів + gap-ів) і відступ від краю екрана.
 * <p>
 * Forge-незалежний навмисно (нуль {@code GuiGraphics}/{@code ForgeGui} —
 * лише {@code int} на вході й виході) — той самий принцип, що решта
 * {@code lib-common}: сама відмальовка слотів (cut-corner панелі,
 * selected-slot scale bonus, glitch-анімація появи) лишається
 * продуктовою специфікою консюмера в {@code lib-forge}/моді, тут — лише
 * геометрія позиції.
 */
public final class HotbarPanelRenderer {

    private HotbarPanelRenderer() {}

    /**
     * @param layout        обрана позиція (з {@code style.yml} консюмера).
     * @param guiScaledWidth  ширина екрана в GUI-координатах ({@code event.getWindow().getGuiScaledWidth()}).
     * @param contentWidth  повна ширина вмісту панелі (сума ширин слотів + gap-ів).
     * @param edgeMargin    відступ від краю екрана для LEFT/RIGHT (ігнорується для CENTER).
     * @return X-координата лівого краю панелі.
     */
    public static int resolveX(HotbarLayout layout, int guiScaledWidth, int contentWidth, int edgeMargin) {
        return switch (layout) {
            case LEFT -> edgeMargin;
            case RIGHT -> guiScaledWidth - contentWidth - edgeMargin;
            case CENTER -> (guiScaledWidth - contentWidth) / 2;
        };
    }
}
