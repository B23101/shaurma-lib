package dev.shaurmalib.anim.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.shaurmalib.anim.mc.McAnimHooks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Кістки {@code right_item}/{@code left_item} кліпа + ховання предмета, коли його замінює пропс. */
@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandLayerAnim {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void shaurma$anim$hide(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, HumanoidArm arm,
                                   PoseStack ps, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (McAnimHooks.hideHeld(entity, arm)) ci.cancel();
    }

    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void shaurma$anim$itemBone(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, HumanoidArm arm,
                                       PoseStack ps, MultiBufferSource buffers, int light, CallbackInfo ci) {
        McAnimHooks.itemBone(entity, ps, arm);
    }
}
