package dev.shaurmalib.forge.playeranim;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клієнтські хуки життєвого циклу для {@link PlayerPoseController} (план,
 * п. 3.24) — заповнює прогалину оригіналу: {@code DropAnimationEvents}
 * snipers_shaurma підписувався лише на СЕРВЕРНУ
 * {@code PlayerEvent.PlayerLoggedOutEvent}, яка нічого не знає про
 * клієнтський {@code PlayerAnimDropBridge.LAYERS} і ніколи не викликала
 * його {@code cleanup(...)} — клієнтського logout-хука для цього не
 * існувало взагалі. Це і є корінь бага "після виходу й повторного входу
 * анімація ламається/не показується": старий запис у мапі просто
 * лишався вічно з мертвим {@code AnimationStack} усередині.
 * <p>
 * Що підписано (завжди, бездіяльне, доки жоден консюмер не викликав
 * {@link PlayerPoseController#trigger} — порожня мапа шарів):
 * <ul>
 *   <li>{@link ClientPlayerNetworkEvent.LoggingOut} → {@code cleanup} (явне
 *       звільнення шарів гравця, а не лише ліниве виявлення relog);</li>
 *   <li>{@link TickEvent.ClientTickEvent} (END) → {@code tickAll}: рушій
 *       тікає стан шарів сам (авто-завершення one-shot, пре-емптивний
 *       fade-out, чистка модифікаторів);</li>
 *   <li>перезавантаження ресурсів (F3+T) → скидання кешу
 *       {@code KeyframeAnimation}, щоб змінений {@code .json} підхоплювався
 *       без перезапуску клієнта.</li>
 * </ul>
 * Смерть/зміна фази НЕ підписані автоматично: надійного клієнтського
 * еквівалента {@code LivingDeathEvent} у ванільному Forge нема, а кожен
 * консюмер уже має власну точку "гравець помер". Викликайте
 * {@link PlayerPoseController#stopAll(net.minecraft.client.player.AbstractClientPlayer, boolean)}
 * звідти — той самий принцип "мод вирішує коли", що
 * {@link dev.shaurmalib.forge.lock.InteractionLockModule}.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "shaurma_lib", value = Dist.CLIENT)
public final class PlayerPoseEvents {

    private PlayerPoseEvents() {}

    /**
     * Клієнтський вихід гравця (реконект, дисконект, вихід у меню) — той
     * самий момент, якого бракувало в оригінальному
     * {@code DropAnimationEvents} (там була лише серверна подія).
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getPlayer() != null) {
            PlayerPoseController.cleanup(event.getPlayer().getUUID());
        }
    }

    /** Щотік (кінець): тікаємо власні шари ззовні — PAL не тікає неактивні. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        PlayerPoseController.tickAll();
    }

    /**
     * Реєстрація слухача перезавантаження ресурсів — на MOD-шині (подія
     * {@link RegisterClientReloadListenersEvent} не ходить по FORGE-шині).
     */
    @Mod.EventBusSubscriber(modid = "shaurma_lib", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {

        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager ->
                    PlayerPoseController.invalidateAnimationCache());
        }
    }
}
