package dev.shaurmalib.forge.playeranim;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
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
 * {@link PlayerPoseController#layerFor} сам по собі вже детектує й
 * виправляє невідповідність стека при наступному {@code trigger(...)}
 * (див. його клас-докстрінг) — цей клас є другим, "прибиральницьким"
 * рівнем захисту: явно звільняє пам'ять і знімає старі шари одразу на
 * подію виходу, а не лише лениво при наступному використанні тієї самої
 * пози.
 * <p>
 * Завжди підписаний (як {@code ItemAnimationEngine}/{@code
 * InteractionLockHooks} інших модулів лібу) — бездіяльний, якщо жоден
 * консюмер жодного разу не викликав {@link PlayerPoseController#trigger}
 * (порожня мапа {@code LAYERS}), тому не потребує окремого
 * {@code withPlayerAnim()}-прапорця на {@link dev.shaurmalib.forge.ShaurmaLib.Builder}
 * — той самий принцип, що {@code TeleportService}: статичний сервіс,
 * тут лише подієва обв'язка навколо нього.
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

    /**
     * Смерть/зміна фази, коли жодна кастомна поза більше не актуальна —
     * НЕ підписано тут автоматично (клієнтського, надійного еквівалента
     * {@code LivingDeathEvent} немає у ванільному Forge-наборі подій;
     * кожен консюмер вже має власну точку "гравець помер" — типово
     * {@code ClientPlayerNetworkEvent.Clone} чи власний respawn-пакет).
     * Викликайте {@link PlayerPoseController#stopAll} напряму з тієї
     * точки — той самий принцип "мод вирішує коли", що
     * {@link dev.shaurmalib.forge.lock.InteractionLockModule} чи
     * {@link dev.shaurmalib.forge.fx.ScreenEffectPostChain}: бібліотека
     * не вгадує момент, лише надає безпечний примітив зняття пози.
     */
}
