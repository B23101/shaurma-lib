package dev.shaurmalib.forge.offline;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.UUID;

/**
 * Приховування нік-тега тіла через scoreboard-команду (план, §13).
 * <p>
 * Повторює логіку {@code LobbyModule.hideNameTag}: якщо власник уже в ігровій
 * команді (яка сама приховує ніки), тіло потрапляє в ту саму команду; якщо ні —
 * у команду приховування, коли її id задано в конфігурації. Для сутності, що не є
 * гравцем, scoreboard-ім'я — це рядок її UUID.
 */
final class OfflineNameTags {

    private OfflineNameTags() {}

    static void join(MinecraftServer server, Entity avatar, String ownerName, String hideTeamId) {
        Scoreboard scoreboard = server.getScoreboard();
        String entry = avatar.getScoreboardName();

        PlayerTeam ownerTeam = ownerName == null || ownerName.isEmpty() ? null : scoreboard.getPlayersTeam(ownerName);
        if (ownerTeam != null) {
            scoreboard.addPlayerToTeam(entry, ownerTeam);
            return;
        }
        if (hideTeamId == null || hideTeamId.isBlank()) {
            return;
        }
        PlayerTeam hideTeam = scoreboard.getPlayerTeam(hideTeamId);
        if (hideTeam == null) {
            hideTeam = scoreboard.addPlayerTeam(hideTeamId);
            hideTeam.setNameTagVisibility(Team.Visibility.NEVER);
        }
        scoreboard.addPlayerToTeam(entry, hideTeam);
    }

    /** Працює і без завантаженої сутності: scoreboard-ім'я тіла — це рядок UUID його сутності. */
    static void leave(MinecraftServer server, UUID avatarEntity) {
        Scoreboard scoreboard = server.getScoreboard();
        String entry = avatarEntity.toString();
        PlayerTeam team = scoreboard.getPlayersTeam(entry);
        if (team != null) {
            scoreboard.removePlayerFromTeam(entry, team);
        }
    }
}
