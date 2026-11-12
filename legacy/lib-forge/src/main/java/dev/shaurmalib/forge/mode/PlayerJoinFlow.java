package dev.shaurmalib.forge.mode;

import dev.shaurmalib.common.lifecycle.JoinPolicy;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Узагальнення {@code org.example.snipers_shaurma.core.events.PlayerJoinEventHandler}
 * у частині "гравець зайшов під час активної гри → яку поведінку застосувати"
 * (п. 3.8 плану). Решта оригінального файлу (обмін скінами, hideNameTag,
 * kit-related events) — снайперс-специфічна, лишається в моді.
 */
public final class PlayerJoinFlow {

    private PlayerJoinFlow() {}

    /**
     * @param player  гравець, що приєднався.
     * @param policy  політика для гравців, що приєднуються під час активного матчу.
     * @param rejectMessage повідомлення при {@link JoinPolicy#REJECT} (може бути null для дефолтного).
     * @return true, якщо гравцю дозволено лишитись на сервері/у світі матчу; false — якщо його відхилено.
     */
    public static boolean apply(ServerPlayer player, JoinPolicy policy, Component rejectMessage) {
        return switch (policy) {
            case SPECTATE -> {
                player.setGameMode(GameType.SPECTATOR);
                yield true;
            }
            case QUEUE -> {
                // Мод-споживач сам вирішує, куди фізично помістити гравця в черзі
                // (наприклад TeleportService.teleport(..., TeleportReason.LOBBY_JOIN)) —
                // бібліотека лише позначає, що це дозволена дія.
                yield true;
            }
            case REJECT -> {
                player.connection.disconnect(rejectMessage != null
                        ? rejectMessage
                        : Component.literal("Match in progress — join rejected."));
                yield false;
            }
        };
    }
}
