package dev.shaurmalib.forge.mode;

import dev.shaurmalib.common.lifecycle.DisconnectPolicy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Застосовує одну з трьох готових {@link DisconnectPolicy} поведінок.
 * Мод-споживач реалізує лише те, що є доменно-специфічним (як саме
 * "зберегти інвентар" — де саме зберігається знімок стану) через
 * {@link StatePreserver}; сама бібліотека керує лише gamemode-перемиканням,
 * спільним для всіх режимів.
 */
public final class PlayerReturnFlow {

    /**
     * Доменно-специфічне збереження/відновлення стану гравця під час матчу.
     * Мод-споживач реалізує, бо саме він знає структуру свого
     * {@code PlayerGameState}/{@code MatchSession} — бібліотека цього не знає.
     */
    public interface StatePreserver {
        void restoreState(ServerPlayer player);
    }

    private final StatePreserver statePreserver;

    public PlayerReturnFlow(StatePreserver statePreserver) {
        this.statePreserver = statePreserver;
    }

    public void apply(DisconnectPolicy policy, ServerPlayer player, boolean wasInMatch) {
        if (!wasInMatch) return; // гравець не був у матчі — нема що відновлювати/скидати.

        switch (policy) {
            case PRESERVE_STATE -> {
                if (statePreserver != null) statePreserver.restoreState(player);
            }
            case RESET_TO_SPECTATOR -> player.setGameMode(GameType.SPECTATOR);
            case KICK_FROM_MATCH -> {
                // Гравець лишається в поточному gamemode (типово SURVIVAL/ADVENTURE
                // лобі) — мод-споживач сам вирішує, куди його телепортувати
                // (напр. TeleportService.teleport(..., TeleportReason.LOBBY_JOIN)),
                // бібліотека лише позначає "цей гравець більше не в матчі".
            }
        }
    }
}
