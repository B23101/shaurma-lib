package dev.shaurmalib.common.tab;

/**
 * Заголовок однієї колонки таблиці (план, п. 3.3 + 3.7) — узагальнення
 * жорстко захардкоджених {@code offKit}/{@code offMoney}/{@code offKills}/
 * {@code offDeaths} зсувів з {@code CustomTabOverlay.renderSoloGameTab}
 * (та аналогічних наборів в інших 5 render-методів оригіналу — кожен
 * режим має власний набір колонок, тому це продуктова специфіка, не
 * бібліотечна константа). {@code offsetX} — уже проскейлений під
 * поточну ширину колонки (у оригіналі — {@code Math.round(140 * colScale)}),
 * консюмер рахує масштаб сам, {@link TabListStyle} не знає нічого про
 * {@code colScale}.
 */
public final class TabColumn {

    public final String headerTextKey;
    public final int offsetX;

    public TabColumn(String headerTextKey, int offsetX) {
        this.headerTextKey = headerTextKey;
        this.offsetX = offsetX;
    }
}
