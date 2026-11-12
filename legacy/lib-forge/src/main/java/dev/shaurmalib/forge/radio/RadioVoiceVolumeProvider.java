package dev.shaurmalib.forge.radio;

/**
 * Постачальник поточної гучності голосу диктора (0..1) — план, п. 3.33.
 * <p>
 * В оригіналі snipers_shaurma це був виклик
 * {@code ModSoundCategory.ANNOUNCER.getVolume()} — власний слайдер мода
 * в {@code ClientModOptions.Audio}, незалежний від ванільних Music/SFX/
 * Voice. Бібліотека не хардкодить конкретну систему опцій консюмера
 * (у майбутнього maniac-mode може взагалі не бути такого слайдера, або
 * він може називатись інакше) — {@link RadioAudioDucking} лише питає
 * цей інтерфейс щоразу, коли треба знати гучність.
 * <p>
 * Якщо консюмер не хоче окремого слайдера — {@code () -> 1.0f} завжди
 * вмикає голос на повну; {@code () -> mc.options.getSoundSourceVolume(SoundSource.VOICE)}
 * прив'язує до ванільного слайдера "Voice"/"Мова", якщо такий доречний.
 */
@FunctionalInterface
public interface RadioVoiceVolumeProvider {
    float getVolume();
}
