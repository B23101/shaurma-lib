package dev.shaurmalib.common.sound;

/**
 * SoundStage — режим просторовості звуку (план, п. 3.5): те, що в
 * оригіналі snipers_shaurma було розкидано по окремих класах з різною
 * ручною побудовою {@code SimpleSoundInstance}
 * ({@code PlaySoundPacket} — {@code Attenuation.NONE} без позиції,
 * {@code AirdropPlaneSound} — ручний розрахунок гучності по дистанції,
 * {@code MenuSoundHelper}/{@code MenuSounds} — {@code forUI(...)}).
 * {@link dev.shaurmalib.forge.sound.SoundCenter} розгалужує побудову
 * {@code SoundInstance} по цьому полю, консюмер лише вказує його в
 * {@link SoundCue}, а не пише власну гілку побудови щоразу.
 */
public enum SoundStage {

    /**
     * Не-позиційний звук "у голові" гравця — UI-кліки, ховери, меню,
     * діалог диктора. Ідентичний {@code SimpleSoundInstance.forUI(...)}
     * (оригінал: {@code MenuSoundHelper}/{@code MenuSounds}). Грає з
     * однаковою гучністю незалежно від позиції гравця в світі.
     */
    NON_POSITIONAL,

    /**
     * Звук у 3D-просторі, прив'язаний до фіксованої точки (не рухається
     * після старту) — вибух, сповіщення на позиції події. Затухає з
     * відстанню за ванільною {@code Attenuation}-кривою.
     */
    POSITIONAL_FIXED,

    /**
     * Звук у 3D-просторі, прив'язаний до РУХОМОГО джерела (сутність,
     * що рухається — літак, техніка). Оригінал: {@code AirdropPlaneSound}
     * — короткі non-looping копії, що спавняться заново кожні N тіків
     * із поточною позицією джерела, з ручним розрахунком гучності по
     * дистанції (Attenuation.NONE, бо позиція і так рахується вручну
     * щотік). {@link dev.shaurmalib.forge.sound.SoundCenter#trackMovingSource}
     * інкапсулює цей повторний "тик кожні N тіків" патерн — консюмер
     * більше не пише свій {@code AirdropPlaneSound}-клас на кожне нове
     * рухоме джерело звуку.
     */
    POSITIONAL_MOVING,
}
