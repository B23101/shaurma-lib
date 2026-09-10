package dev.shaurmalib.forge.mixin;

import dev.shaurmalib.forge.sound.SoundCategoryDucker;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MixinSoundEngineDuckOnStart (план, п. 3.17 — toggle-миксин
 * {@link dev.shaurmalib.common.mixin.MixinId#SOUND_ENGINE_DUCK_ON_START}) —
 * 1:1 перенесення {@code org.example.snipers_shaurma.core.mixin.MixinSoundEngineDuckOnStart}.
 * <p>
 * ВИПРАВЛЕННЯ "звук іноді проскакує на повній гучності / йоршиться,
 * особливо коли звуків багато / особливо на старті звуку".
 * <p>
 * ІСТОРІЯ (з оригіналу): дві попередні версії цього фіксу ловили
 * гучність "згори" — або постфактум через
 * {@code ChannelAccess.ChannelHandle#execute(...)} одразу після
 * {@code play()}, або через {@code calculateVolume(...)}. Обидва
 * варіанти лишали вікно, де рушій встигає застосувати ПОВНУ (не
 * приглушену) гучність до каналу ще до того, як наш код встигає
 * подіяти — особливо помітно на старті звуку і коли грає багато звуків
 * одночасно.
 * <p>
 * РІШЕННЯ (звірено з референсним, перевіреним робочим кодом мода
 * Dynamic FPS — SoundEngineMixin звідти): перехоплювати не "згори", а
 * на САМОМУ НИЗЬКОМУ рівні — приватному методі
 * {@code SoundEngine#getVolume(SoundSource)}. Це метод, який
 * {@code calculateVolume(...)} викликає ВСЕРЕДИНІ себе для отримання
 * множника категорії — тобто справжнє єдине джерело правди про
 * гучність категорії для рушія. Підмінивши його тут, звук ФІЗИЧНО НЕ
 * МОЖЕ почати грати на повній гучності, незалежно від того, скільки
 * звуків стартує одночасно.
 * <p>
 * Множник береться з {@link SoundCategoryDucker#currentMultiplier(SoundSource)}
 * — той самий централізований ducking-реєстр бібліотеки (план, п. 3.5 +
 * 3.33), яким користуються і {@code RadioAudioDucking}, і будь-який
 * майбутній {@code CombatAreaAudioDucking}-еквівалент консюмера.
 * <p>
 * Додатково (теж за патерном Dynamic FPS): якщо множник для категорії
 * дорівнює 0 (повне вимкнення), скасовуємо сам виклик play/playDelayed
 * ще на HEAD — чистіше, ніж лишати канал зі звуком гучністю 0 (економить
 * канали рушія й уникає зайвого OpenAL-навантаження при масовому ducking-у).
 * <p>
 * Toggle: вимикається консюмером через
 * {@code ShaurmaLib.Builder.withMixins(MixinId.SOUND_ENGINE_DUCK_ON_START, false)},
 * якщо потрібен повністю власний шлях приглушення звуку або конфлікт з
 * іншим модом, що патчить {@link SoundEngine}.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(SoundEngine.class)
public abstract class MixinSoundEngineDuckOnStart {

    /**
     * Перехоплюємо низькорівневий getVolume(SoundSource) — множник
     * категорії, з якого calculateVolume(...) і будь-який інший шлях
     * рушія рахує фінальну гучність. Підміна тут діє з першої ж
     * мілісекунди існування звуку, без жодного вікна на "повну" гучність.
     */
    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private void shaurmaLib$applyDuck(SoundSource source, CallbackInfoReturnable<Float> cir) {
        try {
            SoundSource effectiveSource = source != null ? source : SoundSource.MASTER;
            float multiplier = SoundCategoryDucker.currentMultiplier(effectiveSource);
            if (multiplier >= 1.0f) return; // нема активного duck-а — нічого не міняємо

            float base = cir.getReturnValue();
            cir.setReturnValue(base * multiplier);
        } catch (Throwable ignored) {
            // Ніколи не ламаємо відтворення звуку через збій ducking-хука.
        }
    }

    /**
     * При повному вимкненні категорії (multiplier == 0) скасовуємо старт
     * звуку взагалі, ще до створення каналу — той самий підхід, що в
     * Dynamic FPS, аби не плодити нульові OpenAL-канали під час ducking-у.
     */
    @Inject(method = {"play", "playDelayed"}, at = @At("HEAD"), cancellable = true)
    private void shaurmaLib$cancelWhenMuted(SoundInstance instance, CallbackInfo ci) {
        try {
            if (instance == null) return;
            if (SoundCategoryDucker.currentMultiplier(instance.getSource()) <= 0.0f) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // Ніколи не ламаємо відтворення звуку через збій ducking-хука.
        }
    }
}
