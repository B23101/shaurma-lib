package dev.shaurmalib.forge.offline;

import dev.shaurmalib.common.offline.OfflineRecord;
import dev.shaurmalib.common.offline.OfflineStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Один спосіб отримати «актора» за UUID (план, §5.7): живого гравця або його тіло.
 * Режим працює з UUID і не має «гілок для офлайну»; ця обгортка розв'язує UUID у
 * {@link LivingEntity}, де б не було гравця.
 * <p>
 * Місця, що шукали ціль через {@code getPlayers()}/{@code instanceof ServerPlayer}, мають
 * використовувати {@link #candidates} або {@link #resolve}. Цикли для <i>переглядачів</i>
 * (UI, чат, звук, видимість) лишаються на {@link #onlinePlayers}: тіла їм нічого не дають.
 */
public final class OfflineActors {

    private OfflineActors() {}

    /** Спершу онлайн-гравець, потім тіло (лише стоїть живим і завантажене). */
    public static Optional<LivingEntity> resolve(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            return Optional.of(online);
        }
        for (OfflinePresenceModule module : OfflinePresenceModule.live()) {
            Optional<OfflineRecord> record = module.record(uuid);
            if (record.isPresent() && record.get().status() == OfflineStatus.STANDING) {
                OfflineAvatarBase avatar = module.findAvatar(server, record.get());
                if (avatar != null) {
                    return Optional.of(avatar);
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isAvatar(Entity entity) {
        return entity instanceof OfflineAvatarBase;
    }

    /** UUID власника, якщо це тіло офлайн-гравця. */
    public static Optional<UUID> ownerOf(Entity entity) {
        return entity instanceof OfflineAvatarBase avatar ? avatar.getPlayerUUID() : Optional.empty();
    }

    /**
     * Онлайн-гравці й тіла офлайн-гравців в одному списку (для пошуку цілі удару, пасток, дротів).
     * Фільтр застосовується до UUID власника.
     */
    public static List<LivingEntity> candidates(MinecraftServer server, Predicate<UUID> filter) {
        List<LivingEntity> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (filter.test(player.getUUID())) {
                out.add(player);
                seen.add(player.getUUID());
            }
        }
        for (OfflinePresenceModule module : OfflinePresenceModule.live()) {
            for (UUID owner : module.standingOwners()) {
                if (seen.contains(owner) || !filter.test(owner)) {
                    continue;
                }
                module.avatar(owner).ifPresent(avatar -> out.add(avatar));
                seen.add(owner);
            }
        }
        return out;
    }

    /** Онлайн-гравці без тіл: для UI, чату, звуку. */
    public static List<ServerPlayer> onlinePlayers(MinecraftServer server) {
        return server.getPlayerList().getPlayers();
    }
}
