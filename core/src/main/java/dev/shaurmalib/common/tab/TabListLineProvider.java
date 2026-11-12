package dev.shaurmalib.common.tab;

import java.util.List;

/**
 * Контракт постачання даних для однієї Tab-таблиці (план, п. 3.3 + 3.7:
 * "{@code TabListLineProvider} — функціональний інтерфейс, режим
 * підставляє свої дані"). {@link dev.shaurmalib.forge.tab.TabListStyle}
 * (lib-forge) викликає це на кожному кадрі, поки Tab затиснутий —
 * консюмер сам вирішує сортування/фільтрацію/які колонки показувати
 * (SC: kit/money/kills/deaths; BR/SCN/SD: власні набори) — рушій рендеру
 * не знає нічого про доменний зміст клітинок.
 * <p>
 * Один провайдер = один render-варіант оригіналу (в оригіналі — одна з
 * 6 паралельних гілок {@code renderSoloGameTab}/{@code renderTeamGameTab}/
 * {@code renderBRSoloTab}/{@code renderBRTeamTab}/{@code renderSCNTab}/
 * {@code renderSDSoloTab}/{@code renderSDTeamTab}); яку гілку показати —
 * консюмер вирішує сам (той самий {@code if (isScn) ... else if (isBR) ...}
 * диспетчер, що лишається продуктовою логікою мода, план розділ 4).
 */
public interface TabListLineProvider {

    /** Ключ перекладу заголовка таблиці (у оригіналі — {@code "gui.snipers_shaurma.tab.title"} і подібні). */
    String titleKey();

    /** Заголовки колонок з їх X-офсетами; порожній список — таблиця без колонкового хедера (лобі-режим). */
    List<TabColumn> columns();

    /** Рядки гравців для поточного кадру, у порядку відображення (сортування — відповідальність консюмера). */
    List<TabRow> rows();

    /**
     * Командні панелі-заголовки, що чергуються з блоками рядків команд —
     * {@code null}/порожній список для соло-режимів без команд.
     * Порядок відповідає порядку команд у {@link #rows()} — консюмер сам
     * розбиває {@link #rows()} на блоки по команді (та сама "балансована
     * 1/2-колонкова розкладка команд" з оригіналу, план 3.3 залишає її
     * продуктовою специфікою — рушій не перерозподіляє рядки між
     * колонками сам).
     */
    default List<TabTeamBarSpec> teamBars() {
        return List.of();
    }
}
