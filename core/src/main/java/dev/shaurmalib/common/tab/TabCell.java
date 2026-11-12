package dev.shaurmalib.common.tab;

/**
 * Одна клітинка одного рядка Tab-таблиці (план, п. 3.3 + 3.7,
 * {@code TabListStyle}/{@code TabListLineProvider}) — узагальнення
 * окремих {@code g.drawString(font, kitName, ox+offKit, ...)}/
 * {@code g.drawString(font, String.valueOf(stats[0]), ox+offKills, ...)}
 * викликів {@code CustomTabOverlay} snipers_shaurma в один DTO: текст
 * уже готовий (не translation key — режим сам вирішує форматування
 * money/kills/deaths конкретного стовпця, бібліотека не знає, що таке
 * "money" чи "kills"), ARGB-колір (з альфою анімації появи, застосованою
 * консюмером ДО передачі сюди — той самий {@code alpha(rowAlpha)}
 * патерн з оригіналу), і опційний прапорець перекресленого тексту
 * (замінює спектатора {@code drawStrikeSubtle}).
 */
public final class TabCell {

    public final String text;
    public final int argbColor;
    public final boolean struckThrough;

    public TabCell(String text, int argbColor, boolean struckThrough) {
        this.text = text;
        this.argbColor = argbColor;
        this.struckThrough = struckThrough;
    }

    public TabCell(String text, int argbColor) {
        this(text, argbColor, false);
    }

    public static TabCell empty() {
        return new TabCell("", 0);
    }
}
