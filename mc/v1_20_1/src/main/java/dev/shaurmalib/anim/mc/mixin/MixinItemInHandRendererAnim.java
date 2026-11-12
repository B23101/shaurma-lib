package dev.shaurmalib.anim.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.shaurmalib.anim.mc.FirstPersonRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Перша особа: кліп з {@code first_person: model/hide} підміняє ванільні руки. Ваніль-рівень — однаково для всіх лоадерів. */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRendererAnim {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void shaurma$anim$firstPerson(float partial, PoseStack ps, MultiBufferSource.BufferSource buffers,
                                          LocalPlayer player, int light, CallbackInfo ci) {
        if (FirstPersonRenderer.render(partial, ps, buffers, player, light)) ci.cancel();
    }
}
