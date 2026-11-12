package dev.shaurmalib.meta.economy;

/**
 * Покращення, що купується очками покращень (одне очко дається за кожен новий рівень).
 * Бібліотека веде лише ступені ({@code profile.upgrades}); що саме воно дає в грі — читає режим
 * ({@code MetaServer#upgradeRank}).
 *
 * @param group     розділ на екрані («Виживий», «Маніяк», «Загальне»)
 * @param maxRank   скільки ступенів
 * @param costPerRank очок за кожен ступінь
 */
public record UpgradeDef(String id, String name, String description, String group, String icon,
                         int maxRank, int costPerRank) {}
