package dev.shaurmalib.forge.mode;

import dev.shaurmalib.common.lifecycle.DisconnectPolicy;
import net.minecraft.server.level.ServerPlayer;

/**
 * П. 3.8 плану: режим обирає одну з готових поведінок
 * {@link DisconnectPolicy} АБО пише власну реалізацію цього інтерфейсу,
 * якщо жодна з трьох стандартних не підходить (наприклад режиму-другу
 * потрібна проміжна поведінка на кшталт "зберегти інвентар, але скинути
 * прогрес поточного раунду").
 */
@FunctionalInterface
public interface DisconnectHandler {
    /**
     * Викликається, коли гравець повертається після виходу під час/після
     * активного матчу.
     *
     * @param player      гравець, що повернувся.
     * @param wasInMatch  чи перебував гравець у активному матчі на момент виходу.
     */
    void onPlayerReturn(ServerPlayer player, boolean wasInMatch);

    /** Обгортка готової {@link DisconnectPolicy} у {@link DisconnectHandler}. */
    static DisconnectHandler of(DisconnectPolicy policy, PlayerReturnFlow flow) {
        return (player, wasInMatch) -> flow.apply(policy, player, wasInMatch);
    }
}
