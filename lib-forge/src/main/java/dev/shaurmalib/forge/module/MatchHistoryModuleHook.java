package dev.shaurmalib.forge.module;

import dev.shaurmalib.forge.history.MatchHistoryStore;

/**
 * Match history (план, п. 3.20) — на відміну від решти модулів лібу,
 * {@link MatchHistoryStore} НЕ підключається через
 * {@code ShaurmaLib.Builder.withXxx(...)}: це статичний доступ без
 * стану ініціалізації (так само як {@code TeleportService}) — консюмер
 * просто викликає {@code MatchHistoryStore.get(level, namespace)} у
 * потрібному місці (типово наприкінці фази завершення матчу) і одразу
 * отримує готове до запису сховище, без реєстрації в event-бас.
 * <p>
 * Приклад використання наприкінці матчу:
 * <pre>{@code
 * MatchRecord record = MatchRecord.builder(UUID.randomUUID().toString(), "scn")
 *     .teamMode(true)
 *     .timeRange(matchStartMs, System.currentTimeMillis())
 *     .winner(winner.getUUID().toString(), winner.getGameProfile().getName())
 *     .player(MatchPlayerEntry.builder(p.getUUID().toString(), p.getGameProfile().getName())
 *         .teamId("red").kills(7).deaths(3).assists(2)
 *         .extra("captures_done", 2)
 *         .extra("weapon_kills", weaponKillsMap)
 *         .build())
 *     .build();
 * MatchHistoryStore.get(server.overworld(), "snipers_shaurma").addRecord(record);
 * }</pre>
 * <p>
 * Цей інтерфейс лишається як маркер модуля бібліотеки (консистентно з
 * рештою пакету {@code module}), реального {@code onAttach}-виклику
 * не потребує.
 */
public interface MatchHistoryModuleHook {
    void onAttach(FMLModuleContext ctx);
}
