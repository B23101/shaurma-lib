package dev.shaurmalib.anim.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.shaurmalib.anim.mc.McAnimHooks;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Корінь тіла рушія + межі «зараз малюється ця сутність». */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRendererAnim<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow protected M model;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;prepareMobModel(Lnet/minecraft/world/entity/Entity;FFF)V"))
    private void shaurma$anim$begin(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                                    int light, CallbackInfo ci) {
        McAnimHooks.beginRender(entity, partialTick, poseStack, this.model);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void shaurma$anim$end(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                                  int light, CallbackInfo ci) {
        McAnimHooks.endRender();
    }
}
