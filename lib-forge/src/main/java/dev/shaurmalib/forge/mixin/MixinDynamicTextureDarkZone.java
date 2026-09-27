package dev.shaurmalib.forge.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.shaurmalib.forge.overlay.DarkZoneEffect;
import dev.shaurmalib.forge.overlay.DarkZoneTextureAccess;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Справжня темрява (план, {@code DarkZoneEffect}) — заміна плоского
 * color-fill оверлея власне зниженням яскравості lightmap-текстури, як
 * у {@code WorldTintOverlay} НЕ реалізовано (той малює поверх картинки,
 * а не змінює освітлення). Прийом 1:1 із True Darkness (grondag):
 * перехопити {@code DynamicTexture.upload()} ПЕРЕД завантаженням на
 * GPU і затемнити 16×16 пікселі lightmap за block-/sky-індексом.
 * <p>
 * Спрацьовує лише для інстансу, поміченого
 * {@link DarkZoneTextureAccess#darkzone_markAsLightmap()} (див.
 * {@code MixinLightTextureDarkZone}) — довільні інші {@code upload()}
 * виклики (текстури предметів, GUI тощо) цей mixin не чіпає.
 */
@Mixin(DynamicTexture.class)
public class MixinDynamicTextureDarkZone implements DarkZoneTextureAccess {

    @Shadow
    private NativeImage pixels;

    private boolean darkzone_isLightmap = false;

    @Override
    public void darkzone_markAsLightmap() {
        darkzone_isLightmap = true;
    }

    /**
     * {@code upload()} для 16×16 lightmap затемнює r/g/b кожного block/sky
     * пікселя — АЛЕ пропускає кутовий піксель {@code (15,15)}. Той піксель
     * — це координата {@code LightTexture.FULL_BRIGHT} (packedLight
     * {@code 0xF000F0}), яку GUI-рендер предметів (включно з GeckoLib
     * {@code GeoItemRenderer} — див. {@code ItemGeoRenderer} клас-докстрінг:
     * рендерер малює й слот інвентарю/хотбару, не лише руку/землю) семплить
     * як "завжди максимально яскраво", незалежно від освітлення світу.
     * Раніше цей піксель теж затемнювався разом з рештою lightmap — тому
     * при активній темній зоні (factor до 0.92) іконки предметів у GUI
     * ставали видимо темними: FULL_BRIGHT координата вказувала вже не на
     * "яскраво", а на потемнілий піксель тієї самої текстури. 3D-сцена
     * від пропуску цього одного пікселя не постраждала — там реальний
     * block/sky-рівень майже ніколи не (15,15) одночасно.
     */
    @Inject(method = "upload", at = @At(value = "HEAD"))
    private void darkzone_onUpload(CallbackInfo ci) {
        if (!darkzone_isLightmap || pixels == null) return;

        float factor = DarkZoneEffect.currentDarkenFactor();
        if (factor <= 0f) return;

        for (int blockIndex = 0; blockIndex < 16; blockIndex++) {
            for (int skyIndex = 0; skyIndex < 16; skyIndex++) {
                if (blockIndex == 15 && skyIndex == 15) continue; // FULL_BRIGHT — не чіпати
                int argb = pixels.getPixelRGBA(blockIndex, skyIndex);
                pixels.setPixelRGBA(blockIndex, skyIndex, darken(argb, factor));
            }
        }
    }

    /**
     * Знижує r/g/b пікселя множником {@code (1 - factor)}, альфа й
     * канал-порядок не чіпає — NativeImage тут ABGR-компонований int
     * (як у {@code Darkness.darken}), тому маски/зсуви ідентичні
     * оригіналу, а не довільний RGB-порядок.
     */
    private static int darken(int abgr, float factor) {
        int a = abgr & 0xFF000000;
        int b = (abgr >> 16) & 0xFF;
        int g = (abgr >> 8) & 0xFF;
        int r = abgr & 0xFF;

        float keep = 1f - factor;
        r = Math.round(r * keep);
        g = Math.round(g * keep);
        b = Math.round(b * keep);

        return a | (b << 16) | (g << 8) | r;
    }
}
