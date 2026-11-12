package dev.shaurmalib.anim.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.shaurmalib.anim.mc.CameraAnim;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Крен камери (у ванільного Camera його немає): той самий стек, де ваніль хитає камеру від удару. */
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererAnimRoll {

    @Inject(method = "bobHurt", at = @At("HEAD"))
    private void shaurma$anim$roll(PoseStack ps, float partial, CallbackInfo ci) {
        float roll = CameraAnim.roll();
        if (roll != 0) ps.mulPose(Axis.ZP.rotationDegrees(roll));
    }
}
