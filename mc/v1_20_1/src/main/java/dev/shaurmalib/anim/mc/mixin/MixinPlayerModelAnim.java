package dev.shaurmalib.anim.mc.mixin;

import dev.shaurmalib.anim.mc.McAnimHooks;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ванільна поза → база, шари рушія поверх. Пріоритет 900: інші моди з TAIL-ін'єкціями (з дефолтом 1000)
 * отримують модель уже з анімацією рушія і можуть її доповнити, а не навпаки.
 */
@Mixin(value = PlayerModel.class, priority = 900)
public abstract class MixinPlayerModelAnim {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void shaurma$anim$reset(LivingEntity entity, float limbSwing, float limbSwingAmount, float age,
                                    float headYaw, float headPitch, CallbackInfo ci) {
        McAnimHooks.beforeSetupAnim((HumanoidModel<?>) (Object) this);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void shaurma$anim$apply(LivingEntity entity, float limbSwing, float limbSwingAmount, float age,
                                    float headYaw, float headPitch, CallbackInfo ci) {
        McAnimHooks.afterSetupAnim((HumanoidModel<?>) (Object) this, entity);
    }
}
