package dev.shaurmalib.meta.match;

/**
 * Скільки дає одна дія за матч. Рядок екрана підсумків: «Повішено на гак ×2  +70».
 *
 * @param action     ключ дії у {@link MatchReport#actions()} ({@code "hook"}, {@code "kill"}, {@code "win"})
 * @param name       підпис рядка
 * @param icon       id іконки
 * @param flatXp     XP, якщо дія була хоч раз (за перемогу, завершення матчу)
 * @param xpPerUnit  XP за кожне повторення
 * @param maxUnits   скільки повторень рахується (ліміт нагород; 0 — без ліміту)
 * @param coinsPerUnit монети за кожне повторення (в основній валюті режиму)
 */
public record RewardRule(String action, String name, String icon, int flatXp, int xpPerUnit, int maxUnits,
                         int coinsPerUnit) {

    public static RewardRule flat(String action, String name, String icon, int xp) {
        return new RewardRule(action, name, icon, xp, 0, 0, 0);
    }

    public static RewardRule per(String action, String name, String icon, int xpPerUnit, int maxUnits) {
        return new RewardRule(action, name, icon, 0, xpPerUnit, maxUnits, 0);
    }

    public RewardRule coins(int perUnit) {
        return new RewardRule(action, name, icon, flatXp, xpPerUnit, maxUnits, perUnit);
    }

    int counted(int count) { return maxUnits > 0 ? Math.min(count, maxUnits) : count; }

    public int xpFor(int count) {
        if (count <= 0) return 0;
        return flatXp + xpPerUnit * counted(count);
    }

    public int coinsFor(int count) { return count <= 0 ? 0 : coinsPerUnit * counted(count); }
}
