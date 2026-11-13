package dev.shaurmalib.anim.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.shaurmalib.anim.mc.bend.BendMeshRenderer;
import dev.shaurmalib.anim.mc.bend.BendablePart;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Згин кінцівки (лікоть/коліно): стан на частині + малювання розрізаної сітки замість ванільних 6 граней.
 * Без згину — нуль втручання (один boolean на виклик). Пріоритет 900 — раніше за типові 1000, щоб
 * зігнута частина не пішла в чужий швидкий шлях рендеру кубоїдів.
 */
@Mixin(value = ModelPart.class, priority = 900)
public abstract class MixinModelPartBend implements BendablePart {

    @Shadow @Final private List<ModelPart.Cube> cubes;

    @Unique private boolean shaurma$bent;
    @Unique private float shaurma$bx, shaurma$by, shaurma$bz;

    @Override
    public void shaurma$setBend(float rx, float ry, float rz) {
        shaurma$bx = rx;
        shaurma$by = ry;
        shaurma$bz = rz;
        shaurma$bent = rx != 0 || ry != 0 || rz != 0;
    }

    @Override
    public void shaurma$clearBend() {
        shaurma$bent = false;
        shaurma$bx = shaurma$by = shaurma$bz = 0;
    }

    @Override public boolean shaurma$hasBend() { return shaurma$bent; }
    @Override public float shaurma$bendX() { return shaurma$bx; }
    @Override public float shaurma$bendY() { return shaurma$by; }
    @Override public float shaurma$bendZ() { return shaurma$bz; }

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void shaurma$bend$compile(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay,
                                      float r, float g, float b, float a, CallbackInfo ci) {
        if (!shaurma$bent) return;
        BendMeshRenderer.render(cubes, this, pose, vc, light, overlay, r, g, b, a);
        ci.cancel();
    }

    /** Згин — частина пози: рукави/штани і броня отримують його разом з обертанням. */
    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void shaurma$bend$copy(ModelPart other, CallbackInfo ci) {
        BendablePart o = (BendablePart) (Object) other;
        if (o.shaurma$hasBend()) shaurma$setBend(o.shaurma$bendX(), o.shaurma$bendY(), o.shaurma$bendZ());
        else shaurma$clearBend();
    }

    @Inject(method = "resetPose", at = @At("TAIL"))
    private void shaurma$bend$reset(CallbackInfo ci) {
        shaurma$clearBend();
    }
}
