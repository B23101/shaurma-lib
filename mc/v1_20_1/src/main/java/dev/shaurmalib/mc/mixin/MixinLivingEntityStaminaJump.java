package dev.shaurmalib.mc.mixin;

import dev.shaurmalib.common.stamina.StaminaGates;
import dev.shaurmalib.mc.McPlayerRef;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// НЕ СКОМПІЛЬОВАНО.
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityStaminaJump {
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void shaurma$gateJump(CallbackInfo ci) {
        if ((Object) this instanceof Player p && StaminaGates.blockJump(new McPlayerRef(p), p.level().isClientSide)) ci.cancel();
    }
}
