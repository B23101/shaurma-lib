package dev.shaurmalib.anim.mc.mixin;

import dev.shaurmalib.anim.mc.PropLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Шар пропсів на кожен рендерер гравця (широкий і тонкий скін) — без подій лоадера. */
@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRendererAnim extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private MixinPlayerRendererAnim(EntityRendererProvider.Context ctx, PlayerModel<AbstractClientPlayer> model, float shadow) {
        super(ctx, model, shadow);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void shaurma$anim$addPropLayer(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        this.addLayer(new PropLayer(this));
    }
}
