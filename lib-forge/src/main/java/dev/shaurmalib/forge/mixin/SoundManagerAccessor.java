package dev.shaurmalib.forge.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor-mixin для {@link SoundManager} — 1:1 перенесення
 * {@code org.example.snipers_shaurma.core.mixin.SoundManagerAccessor}.
 * Єдина мета — дістатись до приватного {@code soundEngine}, щоб
 * {@link dev.shaurmalib.forge.sound.SoundCategoryDucker} міг далі піти
 * через {@link SoundEngineAccessor}.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {

    @Accessor("soundEngine")
    SoundEngine shaurmaLib$getSoundEngine();
}
