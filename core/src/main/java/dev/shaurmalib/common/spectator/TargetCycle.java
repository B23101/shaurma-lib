package dev.shaurmalib.common.spectator;

import java.util.List;

/**
 * Чиста (0 Minecraft-імпортів) логіка циклічного перемикання по списку
 * кандидатів (план, п. 3.34 — {@code SpectatorTargetCycler}). Forge-шар
 * бібліотеки постачає вже відфільтрований (через {@link SpectatorPolicy})
 * список кандидатів щоразу заново — цей клас лише рахує наступний/
 * попередній індекс, не тримає посилань на живі сутності.
 */
public final class TargetCycle {

    private TargetCycle() {}

    /**
     * @param candidates    поточний список доступних цілей (вже
     *                      відфільтрований консюмером через
     *                      {@link SpectatorPolicy}), у стабільному порядку.
     * @param currentTarget поточна ціль, або {@code null}, якщо спостереження
     *                      ще не почалось.
     * @return наступний елемент списку (з циклічним переходом в початок),
     *         або {@code null}, якщо список порожній. Якщо {@code currentTarget}
     *         відсутній у списку (наприклад ціль щойно вибула), повертає
     *         перший елемент списку.
     */
    public static <T> T next(List<T> candidates, T currentTarget) {
        return step(candidates, currentTarget, 1);
    }

    /** Симетричний до {@link #next}, у зворотному напрямку. */
    public static <T> T previous(List<T> candidates, T currentTarget) {
        return step(candidates, currentTarget, -1);
    }

    private static <T> T step(List<T> candidates, T currentTarget, int direction) {
        if (candidates.isEmpty()) {
            return null;
        }
        int currentIndex = currentTarget == null ? -1 : candidates.indexOf(currentTarget);
        if (currentIndex < 0) {
            // Ціль відсутня в поточному списку (вибула/недоступна) —
            // починаємо з першого елемента незалежно від напрямку, щоб
            // перемикання завжди давало передбачуваний валідний результат.
            return candidates.get(0);
        }
        int size = candidates.size();
        int nextIndex = ((currentIndex + direction) % size + size) % size;
        return candidates.get(nextIndex);
    }
}
