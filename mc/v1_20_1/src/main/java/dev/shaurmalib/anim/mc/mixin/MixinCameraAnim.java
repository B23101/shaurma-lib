package dev.shaurmalib.anim.mc.mixin;

import dev.shaurmalib.anim.mc.CameraAnim;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Камера першої особи за анімованою головою (див. {@link CameraAnim}). */
@Mixin(value = Camera.class, priority = 900)
public abstract class MixinCameraAnim {

    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow public abstract Vec3 getPosition();
    @Shadow public abstract float getYRot();
    @Shadow public abstract float getXRot();

    @Inject(method = "setup", at = @At("TAIL"))
    private void shaurma$anim$followHead(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                         float partial, CallbackInfo ci) {
        CameraAnim.Result r = CameraAnim.apply(entity, detached, partial, getPosition(), getYRot(), getXRot());
        if (r == null) return;
        setRotation(r.yRot(), r.xRot());
        setPosition(r.position());
    }
}
