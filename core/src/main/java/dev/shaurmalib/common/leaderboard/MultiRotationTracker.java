package dev.shaurmalib.common.leaderboard;

import java.util.HashMap;
import java.util.Map;

/**
 * MultiRotationTracker (план, п. 3.21) — узагальнення ротаційного
 * індексу мульти-бордів з {@code LeaderboardServerDispatcher}
 * snipers_shaurma ({@code multiIndex}/{@code tickMultiRotation}/
 * {@code resolveDisplayType}). Дозволяє одній точці голограми з типом
 * {@link LeaderboardType#MULTI} циклічно показувати кілька типів топу
 * по черзі (наприклад "вбивства" → "гроші" → "MVP" кожні 5 секунд).
 * <p>
 * Ключ — {@code long} (типово {@code BlockPos.asLong()} позиції точки,
 * як і в оригіналі) — бібліотека не залежить від Minecraft-типів у
 * lib-common, тому консюмер сам конвертує позицію в {@code long}.
 */
public final class MultiRotationTracker {

    private final Map<Long, Integer> index = new HashMap<>();

    /** Поточний елемент ротації для точки {@code key} серед {@code options} — без просування індексу. */
    public String current(long key, java.util.List<String> options) {
        if (options == null || options.isEmpty()) return null;
        int idx = index.getOrDefault(key, 0);
        return options.get(idx % options.size());
    }

    /** Просуває ротацію точки {@code key} на один крок (циклічно) серед {@code optionsSize} варіантів. */
    public void advance(long key, int optionsSize) {
        if (optionsSize <= 0) return;
        int idx = index.getOrDefault(key, 0);
        index.put(key, (idx + 1) % optionsSize);
    }

    /** Ручне перемикання (наприклад стрілками у редакторі) на {@code direction} кроків (може бути від'ємним). */
    public void shift(long key, int direction, int optionsSize) {
        if (optionsSize <= 0) return;
        int idx = index.getOrDefault(key, 0);
        index.put(key, ((idx + direction) % optionsSize + optionsSize) % optionsSize);
    }

    public void clear(long key) {
        index.remove(key);
    }

    public void clearAll() {
        index.clear();
    }
}
