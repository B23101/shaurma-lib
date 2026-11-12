package dev.shaurmalib.forge.module;

import dev.shaurmalib.forge.leaderboard.HologramLocationStore;

/**
 * Leaderboard core (план, п. 3.21) — як і {@link MatchHistoryModuleHook},
 * НЕ підключається через {@code ShaurmaLib.Builder.withXxx(...)}:
 * {@code dev.shaurmalib.common.leaderboard.LeaderboardEngine}/
 * {@code LeaderboardType}/{@code MultiRotationTracker} і
 * {@code dev.shaurmalib.common.rank.PlayerRankEngine} (lib-common) —
 * статичні/instance-утиліти без стану ініціалізації, а
 * {@link HologramLocationStore} — статичний доступ через
 * {@code get(level, namespace)}, так само як {@code MatchHistoryStore}.
 * <p>
 * Бібліотека дає ОБЧИСЛЮВАЛЬНЕ ядро (топ-N, ранги, multi-ротація,
 * персистентні позиції голограм) і низькорівневий рушій примітивів
 * рендеру ({@link dev.shaurmalib.forge.markers.WorldBillboardPrimitives},
 * план п. 3.13 — вже реалізований) — власне мережева синхронізація з
 * клієнтами і конкретний дизайн карток лідерборду (кольори по типу
 * метрики, розмір/лейаут) лишаються за консюмером: {@code
 * WorldBillboardPrimitives} дає координатну математику й
 * quad/border/text примітиви, консюмер компонує їх у свій
 * {@code drawBoard}-подібний метод так само, як і в оригінальному
 * {@code LeaderboardWorldRenderer}, лише без дублювання obscure
 * z-bias/billboard-текст математики.
 * <p>
 * {@code MultiRotationTracker} (ротація точки {@code MULTI} між
 * кількома типами топу) консюмер тримає як один {@code static final}
 * екземпляр (той самий принцип, що {@link StyleModuleHook} радить для
 * {@code StyleTheme}) — рушій сам не зберігає жоден стан ротації, тому
 * не потребує окремого підключення через
 * {@code ShaurmaLib.Builder}.
 * <p>
 * Приклад складання частин докупи (спрощено):
 * <pre>{@code
 * // Реєстрація типів (один раз при старті, для кожного режиму):
 * LeaderboardType.register("kills_sc", "Вбивства (Contract)");
 * LeaderboardType.registerAuto("kills_auto", "Вбивства (поточний режим)",
 *     () -> switch (GameModeRegistry.current().id()) {
 *         case "scn" -> "kills_scn";
 *         case "sr"  -> "kills_br";
 *         default    -> "kills_sc";
 *     });
 *
 * // Обчислення топу конкретного типу:
 * List<LeaderboardEntry> top = LeaderboardEngine.topNByLong(
 *     stats.allKnownPlayers(), 5,
 *     PlayerStatistics::getPlayerUUID, PlayerStatistics::getLastKnownName,
 *     s -> (long) s.getKillsSC());
 *
 * // Персистентні позиції голограм:
 * HologramLocationStore store = HologramLocationStore.get(level, "snipers_shaurma");
 * store.addPoint(pos, minY, yaw, 1.0f, "kills_sc", List.of());
 *
 * // Ротація точки MULTI (раз на 5с, з власного ServerTickEvent):
 * private static final MultiRotationTracker ROTATION = new MultiRotationTracker();
 * List<String> options = List.of("kills_sc", "money_sc", "mvp_sc");
 * String currentTypeId = ROTATION.current(pos.asLong(), options);
 * if (tickCounter % 100 == 0) ROTATION.advance(pos.asLong(), options.size());
 * }</pre>
 */
public interface LeaderboardModuleHook {
    void onAttach(FMLModuleContext ctx);
}
