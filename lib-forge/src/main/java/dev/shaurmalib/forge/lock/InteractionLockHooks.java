package dev.shaurmalib.forge.lock;

import dev.shaurmalib.common.lock.InteractionLockRegistry;
import dev.shaurmalib.common.lock.LockType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Форг-хуки {@link InteractionLockRegistry} (план, п. 3.25) — узагальнення
 * {@code MovementBlockHandler} (частина руху), {@code BlockInteractHandler}
 * і {@code PhantomModeWeaponGuard} з оригіналу snipers_shaurma в один
 * централізований набір підписок.
 * <p>
 * На відміну від оригіналу, де кожен з трьох класів мав власний
 * {@code @Mod.EventBusSubscriber} і власну, ad-hoc перевірку "чи блокувати
 * зараз" (розкидану по {@code GamePhase}/{@code PhantomSlotData}), тут
 * перевірка єдина й уніфікована: {@link InteractionLockRegistry#isLocked}
 * для відповідного {@link LockType}. Консюмер (мод) сам вирішує КОЛИ
 * викликати {@code lock}/{@code unlock} (kit-select фаза, phantom-режим
 * параглайдера тощо) — ці хуки лише транслюють поточний стан реєстру в
 * скасування Forge-подій.
 * <p>
 * Завжди підписаний на bus (як {@link dev.shaurmalib.forge.item.ItemAnimationEngine}),
 * але бездіяльний, доки жоден гравець не має активних локів — нуль
 * накладних витрат для консюмерів, які взагалі не використовують
 * {@code withInteractionLock()}.
 */
@Mod.EventBusSubscriber(modid = "shaurma_lib")
public final class InteractionLockHooks {

    private InteractionLockHooks() {}

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        if (InteractionLockRegistry.isLocked(id, LockType.MOVEMENT)) {
            PlayerFreezeService.freeze(player);
        } else {
            PlayerFreezeService.clearResyncState(id);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (InteractionLockRegistry.isLocked(player.getUUID(), LockType.ATTACK)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (InteractionLockRegistry.isLocked(player.getUUID(), LockType.USE_ITEM)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (InteractionLockRegistry.isLocked(player.getUUID(), LockType.USE_ITEM)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ServerPlayer player = asServerPlayer(event);
        if (player != null && InteractionLockRegistry.isLocked(player.getUUID(), LockType.USE_ITEM)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ServerPlayer player = asServerPlayer(event);
        if (player == null) return;
        if (InteractionLockRegistry.isLocked(player.getUUID(), LockType.BLOCK_INTERACT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ServerPlayer player = asServerPlayer(event);
        if (player == null) return;
        if (InteractionLockRegistry.isLocked(player.getUUID(), LockType.BLOCK_INTERACT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        ServerPlayer player = asServerPlayer(event);
        if (player != null && InteractionLockRegistry.isLocked(player.getUUID(), LockType.ATTACK)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (InteractionLockRegistry.isLocked(player.getUUID(), LockType.BLOCK_INTERACT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (InteractionLockRegistry.isLocked(player.getUUID(), LockType.BLOCK_INTERACT)) {
            event.setCanceled(true);
        }
    }

    private static ServerPlayer asServerPlayer(PlayerInteractEvent event) {
        return event.getEntity() instanceof ServerPlayer sp ? sp : null;
    }
}
