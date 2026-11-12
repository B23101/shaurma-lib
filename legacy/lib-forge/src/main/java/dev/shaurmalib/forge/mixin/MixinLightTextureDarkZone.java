package dev.shaurmalib.forge.mixin;

import dev.shaurmalib.forge.overlay.DarkZoneTextureAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ставить мітку {@link DarkZoneTextureAccess} на {@code lightTexture}
 * одразу після побудови {@code LightTexture} — див. докстрінг
 * {@link DarkZoneTextureAccess}. Не читає/не пише самі пікселі (це
 * робить {@code MixinDynamicTextureDarkZone.onUpload}) — тут лише
 * позначення "оце саме той інстанс".
 */
@Mixin(LightTexture.class)
public class MixinLightTextureDarkZone {

    @Shadow
    private DynamicTexture lightTexture;

    @Inject(method = "<init>*", at = @At(value = "RETURN"))
    private void darkzone_afterInit(GameRenderer gameRenderer, Minecraft minecraft, CallbackInfo ci) {
        ((DarkZoneTextureAccess) lightTexture).darkzone_markAsLightmap();
    }
}
