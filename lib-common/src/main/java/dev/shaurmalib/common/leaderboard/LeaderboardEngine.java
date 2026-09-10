package dev.shaurmalib.common.leaderboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * LeaderboardEngine (план, п. 3.21) — узагальнення обчислювальної
 * частини {@code core.statistics.leaderboard.LeaderboardServerDispatcher}
 * (253 рядки) і {@code StatisticsManager.getTopXxx(5)} snipers_shaurma.
 * <p>
 * Оригінал мав окремий іменований метод на кожну метрику
 * ({@code getTopKillsSC}, {@code getTopKillsSCN}, {@code getTopWinsBR},
 * {@code getTopCapturesSCN}, ... — 15+ методів у {@code StatisticsManager},
 * кожен — власна копія тієї самої логіки "відсортувати список за
 * геттером, взяти перші N"). Тут лишається ОДИН метод
 * {@link #topN(List, int, Comparator, Function)}, параметризований
 * джерелом даних (будь-який {@code List<T>}, не обов'язково
 * {@code PlayerStatistics}), компаратором (яку метрику порівнювати) і
 * функцією форматування рядка топу — режим викликає його з власним
 * порівнянням для КОЖНОЇ своєї метрики, без потреби додавати новий
 * метод до самого рушія бібліотеки.
 * <p>
 * Не робить жодних припущень про природу {@code T} — тип статистики
 * (гравець, команда, зброя, кіт) обирає консюмер; єдина вимога —
 * функція {@code toEntry} вміє перетворити один елемент {@code T} на
 * {@link LeaderboardEntry}.
 */
public final class LeaderboardEngine {

    private LeaderboardEngine() {}

    /**
     * Топ-N елементів {@code source}, відсортованих за спаданням
     * {@code comparator}, перетворених у {@link LeaderboardEntry} через
     * {@code toEntry}.
     *
     * @param source     джерело (наприклад список {@code PlayerStatistics} усіх відомих гравців).
     * @param limit      скільки рядків повернути (типово 5, як в оригіналі).
     * @param comparator порівняння за метрикою, що визначає порядок топу
     *                   (природний порядок зростання — рушій сам інвертує,
     *                   щоб найбільше значення було першим).
     * @param toEntry    перетворення одного елемента {@code T} на готовий рядок топу.
     */
    public static <T> List<LeaderboardEntry> topN(List<T> source, int limit,
                                                    Comparator<T> comparator,
                                                    Function<T, LeaderboardEntry> toEntry) {
        List<T> sorted = new ArrayList<>(source);
        sorted.sort(comparator.reversed());
        int end = Math.min(limit, sorted.size());
        List<LeaderboardEntry> result = new ArrayList<>(end);
        for (int i = 0; i < end; i++) {
            result.add(toEntry.apply(sorted.get(i)));
        }
        return result;
    }

    /**
     * Зручний варіант для найпоширенішого випадку: метрика — {@code int}/
     * {@code long} лічильник, відображуваний як є (без форматування типу
     * "$1.2M"). {@code uuidOf}/{@code nameOf} дістають ідентифікатор і
     * ім'я гравця, {@code metricOf} — саму метрику для сортування й показу.
     */
    public static <T> List<LeaderboardEntry> topNByLong(List<T> source, int limit,
                                                          Function<T, UUID> uuidOf,
                                                          Function<T, String> nameOf,
                                                          Function<T, Long> metricOf) {
        return topN(source, limit, Comparator.comparingLong(metricOf::apply),
                t -> new LeaderboardEntry(uuidOf.apply(t), nameOf.apply(t), String.valueOf(metricOf.apply(t))));
    }

    /**
     * Топ-N з {@code Map<String,Integer>} лічильників (аналог
     * {@code getTopKitPopularity(5)} з оригіналу — топ популярності
     * кітів/зброї/будь-якого рядкового ключа без прив'язки до
     * конкретного гравця). {@code labelFormatter} опційний
     * (наприклад "перша літера велика" з оригіналу) — {@code null}
     * лишає ключ як є.
     */
    public static List<LeaderboardEntry> topNFromCounts(Map<String, Integer> counts, int limit,
                                                          Function<String, String> labelFormatter) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int end = Math.min(limit, sorted.size());
        List<LeaderboardEntry> result = new ArrayList<>(end);
        for (int i = 0; i < end; i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            String label = labelFormatter != null ? labelFormatter.apply(e.getKey()) : e.getKey();
            result.add(new LeaderboardEntry(null, label, String.valueOf(e.getValue())));
        }
        return result;
    }

    /** {@code labelFormatter}, що робить першу літеру великою, решту малими (як в оригіналі для назв кітів). */
    public static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
