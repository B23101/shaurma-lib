package dev.shaurmalib.forge.mixin;

import dev.shaurmalib.forge.stamina.StaminaService;
import dev.shaurmalib.forge.network.packets.StaminaSyncPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityStaminaJump {
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void shaurma$blockJumpWhenStaminaDepleted(CallbackInfo callbackInfo) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof Player player
                && (player.level().isClientSide
                ? StaminaSyncPacket.ClientState.shouldBlockJump()
                : StaminaService.shouldBlockJump(player))) {
            callbackInfo.cancel();
        }
    }
}
