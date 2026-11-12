package dev.shaurmalib.forge.offline;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Прибирання осиротілих тіл (план, §5.9): рестарт сервера, аварійне завершення,
 * зміна режиму, тіла в чанках, що були незавантажені в момент закриття scope.
 * Тіло осиротіле, якщо модуля немає, scope не збігається з активним або запису немає.
 * Усе безшумно: без дропу й без подій.
 */
final class OfflineAvatarSweeper {

    private static final Logger LOGGER = LogManager.getLogger("ShaurmaLib/OfflineSweeper");

    private OfflineAvatarSweeper() {}

    /**
     * Перевірка при вході тіла у світ ({@code EntityJoinLevelEvent}).
     *
     * @return {@code true}, якщо тіло осиротіле й не має потрапити у світ.
     */
    static boolean isOrphan(OfflineAvatarBase avatar) {
        OfflinePresenceModule module = OfflinePresenceModule.forAvatar(avatar);
        return module == null || !module.isRegistered(avatar);
    }

    /**
     * Проходить по завантажених рівнях і прибирає всі осиротілі тіла.
     *
     * @return скільки тіл прибрано.
     */
    static int sweepLoaded(MinecraftServer server) {
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<OfflineAvatarBase> orphans = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof OfflineAvatarBase avatar && !avatar.isRemoved() && isOrphan(avatar)) {
                    orphans.add(avatar);
                }
            }
            for (OfflineAvatarBase avatar : orphans) {
                avatar.discard();
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.info("Прибрано осиротілих тіл: {}", removed);
        }
        return removed;
    }
}
