package dev.shaurmalib.forge.lobby;

import dev.shaurmalib.common.lobby.LobbySpawnPoint;
import dev.shaurmalib.common.lobby.LobbySpawnPointProvider;
import dev.shaurmalib.forge.teleport.TeleportReason;
import dev.shaurmalib.forge.teleport.TeleportService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.Objects;

/**
 * Лібова версія {@code sendToLobby(ServerPlayer)} і
 * {@code hideNameTag(ServerPlayer)} з
 * {@code org.example.snipers_shaurma.core.events.PlayerJoinEventHandler}.
 * <p>
 * <b>Що саме перенесено (і чому лише це):</b> це рівно та частина
 * лобі-логіки snipers_shaurma, яка є чистою механікою — "поставити
 * гравця в стан очікування у визначеній точці" — однаковою для будь-
 * якого режиму й будь-якого консюмера. Усе інше з
 * {@code PlayerJoinEventHandler} (реконект під час активного матчу,
 * SD-раундова доля, SR-картки відродження, SCN-таймери респавну,
 * штрафи економіки SC при виході) НЕ переноситься — це продуктові
 * рішення "що робити з гравцем у грі", які приймає сам мод, а не
 * бібліотека (див. розмову: "гра сама вирішує те що треба"). Тому тут
 * немає жодного {@code JoinPolicy}/{@code onPlayerJoin}-хука, що
 * вирішує ЗА мод, коли саме викликати {@link #sendToLobby} — консюмер
 * викликає це сам, у гілці свого {@code PlayerJoinEventHandler}, точно
 * в тому місці, де оригінал зараз викликає власний {@code sendToLobby(player)}.
 * <p>
 * <b>Перенесено буквально, без змін поведінки:</b>
 * <ul>
 *   <li>телепорт через {@link TeleportService} з {@link TeleportReason#LOBBY_JOIN}
 *       (в оригіналі — прямий {@code TeleportUtil.teleport(player, x, y, z)},
 *       без reason; бібліотечна версія додає лише сам reason-тег для
 *       логування/дебагу, механіка телепорту та сама);</li>
 *   <li>{@code setRespawnPosition(...)} на ту саму точку;</li>
 *   <li>очищення інвентаря та ефектів;</li>
 *   <li>скидання {@code MAX_HEALTH} до базових 20.0 (знімає всі
 *       модифікатори, які могли лишитись з попереднього раунду — кіти,
 *       банкірські бонуси тощо);</li>
 *   <li>{@code GameType.ADVENTURE} + повідомлення "очікування";</li>
 *   <li>приховування ніка через {@code scoreboard}-команду
 *       {@code sc_nametag_hide} (перейменовано на параметризовану назву
 *       команди — див. {@link #hideNameTag}, оригінал мав це ім'я
 *       захардкодженим).</li>
 * </ul>
 * <p>
 * <b>Свідомо НЕ перенесено з оригінального {@code sendToLobby}:</b>
 * {@code replaceForeignDrone(player)} — заміна предмета стороннього мода
 * (superbwarfare) на власний предмет snipers. Це продуктова інтеграція з
 * конкретним стороннім модом зброї (той самий клас проблем, що й
 * TACZ/SuperbWarfare-миксини — план, розділ 4: "бібліотека не повинна
 * залежати від жодного стороннього зброярського мода"), консюмер
 * викликає її сам одразу після {@link #sendToLobby}, якщо йому це
 * потрібно.
 */
public final class LobbyModule {

    /** Назва scoreboard-команди для приховування ніків — параметризована замість захардкодженого "sc_nametag_hide". */
    private final String nameTagHideTeamId;
    private final LobbySpawnPointProvider spawnPointProvider;

    /**
     * @param nameTagHideTeamId  ідентифікатор scoreboard-команди, що приховує
     *                           бирки ніків (в оригіналі — {@code "sc_nametag_hide"});
     *                           параметризовано, щоб два різних консюмери
     *                           (snipers, maniac) на одному сервері не
     *                           конфліктували об однакову scoreboard-команду.
     * @param spawnPointProvider постачальник поточної точки лобі — консюмер
     *                           сам вирішує, чи це спільна {@code lobbySpawn},
     *                           чи per-mode override (як SCN у snipers).
     */
    public LobbyModule(String nameTagHideTeamId, LobbySpawnPointProvider spawnPointProvider) {
        this.nameTagHideTeamId = Objects.requireNonNull(nameTagHideTeamId, "nameTagHideTeamId");
        this.spawnPointProvider = Objects.requireNonNull(spawnPointProvider, "spawnPointProvider");
    }

    /**
     * Відповідник {@code PlayerJoinEventHandler.sendToLobby(ServerPlayer)}.
     * Консюмер викликає це там, де оригінал викликав власний метод —
     * типово в гілці {@code else} після перевірки "чи гра зараз активна".
     */
    public void sendToLobby(ServerPlayer player) {
        LobbySpawnPoint spawn = spawnPointProvider.get();

        TeleportService.teleport(player, player.serverLevel(),
                spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), 0f, TeleportReason.LOBBY_JOIN);

        player.setRespawnPosition(player.level().dimension(),
                new BlockPos((int) spawn.x(), (int) spawn.y(), (int) spawn.z()),
                spawn.yaw(), true, false);

        player.getInventory().clearContent();
        player.removeAllEffects();

        AttributeInstance maxHpAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHpAttr != null) {
            maxHpAttr.removeModifiers();
            maxHpAttr.setBaseValue(20.0);
        }
        player.setHealth(player.getMaxHealth());

        player.setGameMode(GameType.ADVENTURE);
        player.displayClientMessage(Component.translatable("msg.snipers_shaurma.waiting"), false);
    }

    /**
     * Відповідник {@code PlayerJoinEventHandler.hideNameTag(ServerPlayer)}.
     * Викликається консюмером при кожному вході гравця — ніки приховані
     * завжди, навіть у лобі (та сама поведінка, що в оригіналі).
     */
    public void hideNameTag(ServerPlayer player) {
        Scoreboard scoreboard = player.getServer().getScoreboard();

        PlayerTeam hideTeam = scoreboard.getPlayerTeam(nameTagHideTeamId);
        if (hideTeam == null) {
            hideTeam = scoreboard.addPlayerTeam(nameTagHideTeamId);
            hideTeam.setNameTagVisibility(Team.Visibility.NEVER);
        }
        // Додаємо тільки якщо гравець ще не в жодній команді (соло режим);
        // якщо гравець вже в ігровій scoreboard-команді (team_0, team_1...),
        // вона сама має NEVER, і додавати сюди не треба — та сама умова,
        // що в оригінальному GameStateManager.hideAllNameTags.
        if (scoreboard.getPlayersTeam(player.getScoreboardName()) == null) {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), hideTeam);
        }
    }
}
