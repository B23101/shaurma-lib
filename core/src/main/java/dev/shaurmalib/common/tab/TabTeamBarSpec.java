package dev.shaurmalib.common.tab;

/**
 * Командна панель-заголовок (план, п. 3.3 + 3.7) — перенесення базового
 * {@code drawTeamBar} з {@code CustomTabOverlay} (кольорова смуга з
 * назвою команди зліва і опційним значенням справа — рахунок/очки).
 * <p>
 * <b>Не перенесено</b>: {@code drawTeamBarWithSquares} (SD-специфічний
 * варіант з квадратами прогресу перемог до {@code target}) — це
 * продуктова фіча конкретного режиму (round-wins), не базовий елемент
 * стилю; консюмер, якому потрібні квадрати прогресу, малює їх поверх
 * базової панелі власним викликом після {@link TabListStyle#drawTeamBar}.
 */
public final class TabTeamBarSpec {

    public final String nameText;
    public final String valueText;
    public final int colorRGB;

    public TabTeamBarSpec(String nameText, String valueText, int colorRGB) {
        this.nameText = nameText;
        this.valueText = valueText;
        this.colorRGB = colorRGB;
    }
}
