package dev.shaurmalib.forge.offline;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Усі Forge-підписки офлайн-присутності в одному місці (план, §5.3).
 * На відміну від {@code DamageGuardHooks}, це НЕ глобальний
 * {@code @Mod.EventBusSubscriber}: екземпляр реєструється лише коли консюмер
 * викликав {@code withOfflinePresence(...)}, тож консюмери без модуля нічого не платять.
 * <p>
 * Про урон: {@link OfflineAvatarBase#hurt} сам віддає удар модулю, тому підписок
 * на {@code LivingAttackEvent}/{@code LivingHurtEvent}/{@code LivingDeathEvent} тут немає:
 * ванільні події для тіла не спрацьовують.
 */
final class OfflinePresenceHooks {

    private final OfflinePresenceModule module;

    OfflinePresenceHooks(OfflinePresenceModule module) {
        this.module = module;
    }

    /** HIGHEST: раніше за обробник режиму, щоб {@code spawnWhen} бачив ще неушкоджений стан матчу. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            module.safe("onOwnerLogout", () -> module.onOwnerLogout(player));
        }
    }

    /** HIGHEST: раніше за обробник режиму, який на NORMAL питає {@code takeReturn}. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            module.safe("onOwnerLogin", () -> module.onOwnerLogin(player));
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            module.safe("tick", () -> module.tick(server));
        }
    }

    /** Осиротіле тіло не потрапляє у світ взагалі (рестарт, зміна режиму, закритий scope). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof OfflineAvatarBase avatar
                && OfflinePresenceModule.forAvatar(avatar) == module
                && OfflineAvatarSweeper.isOrphan(avatar)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        module.safe("onServerStarting", () -> module.onServerStarting(event.getServer()));
    }

    /** HIGHEST: ДО масового logout, інакше всі гравці залишили б тіла. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerStopping(ServerStoppingEvent event) {
        module.safe("onServerStopping", () -> module.onServerStopping(event.getServer()));
    }

    /** Новий сервер у тій самій JVM (одиночний світ) будує модуль заново: стару підписку знімаємо. */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        module.detach();
    }
}
