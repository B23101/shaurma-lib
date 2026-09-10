package dev.shaurmalib.forge.mixin;

import com.mojang.blaze3d.audio.Listener;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/**
 * Accessor-mixin для {@link SoundEngine} (план, п. 3.5 + 3.33,
 * {@code RadioAudioDucking}) — 1:1 перенесення
 * {@code org.example.snipers_shaurma.core.mixin.SoundEngineAccessor}.
 * <p>
 * Дає доступ до приватних полів, потрібних щоб приглушити ВЖЕ активні
 * (запущені) звуки конкретної категорії "на льоту". Причина, чому це
 * взагалі потрібно, задокументована в {@link dev.shaurmalib.forge.sound.SoundCategoryDucker}:
 * ванільний {@code SoundManager.updateSourceVolume(SoundSource, float)}
 * у 1.20.1 не оновлює гучність каналів уже запущених звуків, лише
 * майбутніх — тому потрібен прямий доступ до {@code instanceToChannel} і
 * ручний {@code channel.setVolume(...)} на кожному активному каналі
 * (підхід підглянутий у моді Dynamic FPS, SoundEngineMixin.dynamic_fps$updateVolume).
 */
@OnlyIn(Dist.CLIENT)
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {

    @Accessor("instanceToChannel")
    Map<SoundInstance, ChannelAccess.ChannelHandle> shaurmaLib$getInstanceToChannel();

    @Accessor("listener")
    Listener shaurmaLib$getListener();

    @Invoker("calculateVolume")
    float shaurmaLib$calculateVolume(SoundInstance instance);
}
