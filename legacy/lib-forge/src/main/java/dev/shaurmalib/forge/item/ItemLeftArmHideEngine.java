package dev.shaurmalib.forge.item;

import dev.shaurmalib.common.item.ArmOverride;
import dev.shaurmalib.common.item.HandSlot;
import dev.shaurmalib.common.item.ItemDefinition;
import dev.shaurmalib.common.item.ItemDefinitionRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animation.AnimationController;

import java.util.Map;

/**
 * Заміна {@code ItemLeftArmHideHandler} + {@code LeftArmHideState} з
 * оригіналу snipers_shaurma.
 * <p>
 * Оригінал (перевірено по коду, план описував це неточно):
 * <ul>
 *   <li>{@code LeftArmHideState.hideLeftArm} — статичний {@code volatile boolean},
 *       який ніде фактично не встановлюється/не читається в реальному
 *       рендер-шляху — мертвий код, залишений від попередньої ітерації.</li>
 *   <li>{@code ItemLeftArmHideHandler} — реальний механізм: жорсткий
 *       {@code Set<Class<?>>} з 4 конкретних класів предметів (MedkitItem,
 *       TabletItem, LotteryTicketItem, RespawnCardItem), перевірка щокадру
 *       через {@code RenderHandEvent}, ХОВАЄ ЛИШЕ {@code OFF_HAND}
 *       (реальну ліву руку). Права рука (MAIN_HAND) такого механізму не
 *       мала взагалі.</li>
 * </ul>
 * Тут: жодного списку класів. Рушій дивиться в
 * {@link ItemDefinitionRegistry} за класом предмета, читає
 * {@link ArmOverride} для потрібного {@link HandSlot} і ховає РЕАЛЬНУ
 * руку лише якщо {@code hideRealHand=true} і зараз на контролері
 * "mainCtrl" активна non-idle анімація — той самий критерій перевірки
 * (контролер не {@code STOPPED} і поточна анімація не "idle"), що і в
 * оригіналі, лише без прив'язки до конкретних класів.
 * <p>
 * <b>ВАЖЛИВО про те, ЯКИЙ предмет перевіряється.</b> {@code hand} — це
 * рука, яку зараз рендерить ванільний {@code ItemInHandRenderer}, і
 * {@link ArmOverride} шукається за ЇЇ слотом. Але сам предмет, що володіє
 * такою кісткою в моделі, НЕ обов'язково лежить саме в цій руці: у
 * оригіналі snipers аптечка/планшет/білет/картка респавну лежать у
 * MAIN_HAND, а їхня GeckoLib-модель анімує кістку {@code LeftArm} — тобто
 * приховати треба РЕАЛЬНУ ОБ-руку, коли в ОСНОВНІЙ руці такий предмет.
 * Якщо перевіряти лише {@code player.getItemInHand(hand)}, то для
 * {@code hand = OFF_HAND} (порожня ліва рука) перевірка завжди повертала б
 * {@code false} і стара проблема «двох лівих рук» лишалась би
 * невиправленою. Тому рушій перебирає ОБИДВІ руки гравця — ту саму
 * поведінку мав і оригінальний {@code ItemLeftArmHideHandler}
 * ({@code for (InteractionHand hand : InteractionHand.values())}).
 */
public final class ItemLeftArmHideEngine {

    private ItemLeftArmHideEngine() {}

    public static boolean shouldHideRealHand(AbstractClientPlayer player, InteractionHand hand) {
        if (player == null) return false;

        HandSlot renderedSlot = PlayerItemAccessImpl.toHandSlot(hand);

        // Перевіряємо ОБИДВІ руки: предмет, чия модель анімує кістку цієї
        // руки, може лежати в будь-якій з них (типово — у MAIN_HAND).
        for (InteractionHand holder : InteractionHand.values()) {
            if (holdsAnimatedOverrideFor(player, holder, renderedSlot)) return true;
        }
        return false;
    }

    /**
     * @param holder        рука, у якій лежить (можливий) предмет-власник моделі
     * @param renderedSlot  слот РЕАЛЬНОЇ руки, яку зараз рендерить ванільний рендерер
     */
    private static boolean holdsAnimatedOverrideFor(AbstractClientPlayer player,
                                                     InteractionHand holder,
                                                     HandSlot renderedSlot) {
        ItemStack stack = player.getItemInHand(holder);
        if (stack.isEmpty()) return false;

        ItemDefinition<?, ?> def = ItemDefinitionRegistry
                .<Object, Object>get(stack.getItem().getClass())
                .orElse(null);
        if (def == null) return false;

        ArmOverride override = def.armOverrides.get(renderedSlot);
        if (override == null || !override.hideRealHand()) return false;

        if (!(stack.getItem() instanceof GeoItem geoItem)) return false;

        long instanceId = GeoItem.getId(stack);
        var cache = geoItem.getAnimatableInstanceCache();
        var manager = cache.getManagerForId(instanceId);
        if (manager == null) return false;

        @SuppressWarnings("unchecked")
        Map<String, AnimationController<?>> controllers =
                (Map<String, AnimationController<?>>) (Map<?, ?>) manager.getAnimationControllers();
        AnimationController<?> ctrl = controllers.get(AnimatedGeoItem.MAIN_CONTROLLER);
        if (ctrl == null) return false;

        if (ctrl.getAnimationState() == AnimationController.State.STOPPED) return false;

        var currentAnim = ctrl.getCurrentAnimation();
        if (currentAnim == null) return false;

        return !AnimatedGeoItem.IDLE_ANIM_NAME.equals(currentAnim.animation().name());
    }
}
