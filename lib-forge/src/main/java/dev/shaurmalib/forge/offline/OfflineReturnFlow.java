package dev.shaurmalib.forge.offline;

import dev.shaurmalib.common.offline.OfflineRecord;
import dev.shaurmalib.common.offline.OfflineReturn;
import dev.shaurmalib.common.offline.OfflineStatus;
import dev.shaurmalib.forge.teleport.TeleportReason;
import dev.shaurmalib.forge.teleport.TeleportService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * Логіка входу власника (план, §5.8).
 * <ol>
 *   <li>Забрати запис із реєстру ({@code consumeReturn}). Немає запису — нічого не робити.</li>
 *   <li>Прибрати тіло без дропу, зняти тікет чанка й ніктег.</li>
 *   <li>Телепортувати гравця туди, де тіло стоїть зараз.</li>
 *   <li>Якщо тіло вбите або вичерпало ліміт: очистити інвентар гравця (лут уже випав,
 *       а {@code <uuid>.dat} лишився зі старим інвентарем) і викликати
 *       {@code clearAfterKill}. Це робиться ДО будь-яких колбеків режиму: захист від дюпа.</li>
 *   <li>Колбеки режиму ({@code onReturn}, {@code listener.onOwnerReturned}).</li>
 *   <li>Зберегти результат для {@code takeReturn(uuid)}.</li>
 * </ol>
 */
final class OfflineReturnFlow {

    private OfflineReturnFlow() {}

    static void run(OfflinePresenceModule module, ServerPlayer player) {
        UUID owner = player.getUUID();
        MinecraftServer server = player.getServer();
        module.pendingReturns().remove(owner);

        Optional<OfflineRecord> consumed = module.registry().consumeReturn(owner);
        if (consumed.isEmpty() || server == null) {
            return;
        }
        OfflineRecord record = consumed.get();
        module.snapshots().remove(owner);
        module.missingOnce().remove(owner);

        // 2. Позиція тіла зараз (воно могло впасти чи відлетіти), тоді тіло прибираємо без дропу.
        double x = record.x();
        double y = record.y();
        double z = record.z();
        float yaw = record.yaw();
        float pitch = record.pitch();
        OfflineAvatarBase avatar = module.findAvatar(server, record);
        if (avatar != null) {
            x = avatar.getX();
            y = avatar.getY();
            z = avatar.getZ();
            yaw = avatar.getYRot();
            pitch = avatar.getXRot();
        }
        module.safe("return/removeBody", () -> module.removeBodySilently(server, record));
        module.anchors().release(server, owner);

        // 3. Гравець з'являється там, де тіло.
        ServerLevel target = OfflinePresenceModule.levelOf(server, record.dimensionKey());
        if (target == null) {
            target = player.serverLevel();
        }
        final ServerLevel level = target;
        final double tx = x;
        final double ty = y;
        final double tz = z;
        final float tyaw = yaw;
        final float tpitch = pitch;
        module.safe("return/teleport", () ->
                TeleportService.teleport(player, level, tx, ty, tz, tyaw, tpitch, TeleportReason.OFFLINE_RETURN));

        OfflineReturn result = OfflineReturn.from(record, server.getTickCount());

        // 4. Тіло вже віддало лут: гравець не має повернутись зі старим інвентарем. ДО колбеків режиму.
        if (result.finalStatus() == OfflineStatus.KILLED || result.finalStatus() == OfflineStatus.EXPIRED) {
            player.getInventory().clearContent();
            module.safe("return/clearAfterKill", () -> module.config().snapshotContributor().clearAfterKill(player));
        }

        // 5–6. Колбеки режиму. Результат лишається для takeReturn, щоб login-обробник режиму на NORMAL
        // бачив, що це повернення, а не новий гравець.
        module.pendingReturns().put(owner, result);
        module.safe("return/onReturn", () -> module.config().onReturn().accept(player, result));
        module.safe("return/listener", () -> module.config().listener().onOwnerReturned(record, result));
    }
}
