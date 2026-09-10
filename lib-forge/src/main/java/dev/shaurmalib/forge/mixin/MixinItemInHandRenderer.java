package dev.shaurmalib.forge.mixin;

import dev.shaurmalib.forge.item.ItemLeftArmHideEngine;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * УВАГА — це НЕ повний перенос оригінального {@code MixinItemInHandRenderer}
 * з snipers_shaurma. Перевірка реального коду показала, що той миксин на
 * ~90% складається зі snipers-специфічної логіки (KitSelectCameraManager
 * freecam, RestrictedSpectatorState — підміна ItemStack цілі під час
 * spectator-режиму) — жодне з цього не є бібліотечним, це лишається в моді
 * snipers (той миксин там теж має продовжувати таргетити цей самий метод —
 * два незалежні {@code @Mixin(ItemInHandRenderer.class)} на різних
 * {@code @Inject} з різними cancellable-гілками сумісні, якщо кожен
 * скасовує подію лише за власною умовою; Mixin AP при конфлікті
 * пріоритету обробляє через {@code @Inject(... , at = @At("HEAD"))}
 * порядок за пріоритетом миксин-конфігу — задокументовано нижче).
 * <p>
 * Бібліотека переносить ЛИШЕ generic-частину: приховування реальної руки,
 * коли {@link ItemLeftArmHideEngine} каже, що для поточного предмета в цій
 * руці зареєстрований {@code ArmOverride(hideRealHand=true)} і зараз
 * активна non-idle анімація на "mainCtrl". Це замінює одразу і
 * {@code LeftArmHideState} (мертвий прапор в оригіналі), і жорсткий
 * 4-класовий whitelist {@code ItemLeftArmHideHandler} — обидва замінені
 * декларативним {@code ItemDefinition.armOverride(slot, bone)}.
 * <p>
 * Розширення проти оригіналу: тут перевіряється ОБИДВІ руки (MAIN_HAND і
 * OFF_HAND), бо в оригіналі права рука не мала такого механізму взагалі —
 * бібліотека дає його "з коробки" для нового режиму, якому знадобиться.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(ItemInHandRenderer.class)
public class MixinItemInHandRenderer {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void shaurmaLib_hideOverriddenArm(
            AbstractClientPlayer player, float partialTicks, float pitch,
            InteractionHand hand, float swingProgress,
            net.minecraft.world.item.ItemStack stack, float equippedProgress,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffer,
            int combinedLight, CallbackInfo ci) {

        if (ItemLeftArmHideEngine.shouldHideRealHand(player, hand)) {
            ci.cancel();
        }
    }
}
