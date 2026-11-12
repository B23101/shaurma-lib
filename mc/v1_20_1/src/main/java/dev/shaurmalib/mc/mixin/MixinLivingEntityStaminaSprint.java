package dev.shaurmalib.mc.mixin;

import dev.shaurmalib.common.stamina.StaminaGates;
import dev.shaurmalib.mc.McPlayerRef;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// НЕ СКОМПІЛЬОВАНО. Було 45 рядків із логікою сервер/клієнт — тепер лише делегування в core.
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityStaminaSprint {
    @ModifyVariable(method = "setSprinting(Z)V", at = @At("HEAD"), argsOnly = true)
    private boolean shaurma$gateSprint(boolean sprinting) {
        if (!sprinting || !((Object) this instanceof Player p)) return sprinting;
        return !StaminaGates.blockSprint(new McPlayerRef(p), p.level().isClientSide);
    }
}
