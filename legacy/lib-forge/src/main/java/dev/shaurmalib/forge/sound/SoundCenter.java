package dev.shaurmalib.forge.sound;

import dev.shaurmalib.common.sound.SoundCategoryRegistry;
import dev.shaurmalib.common.sound.SoundCue;
import dev.shaurmalib.common.sound.SoundCueRegistry;
import dev.shaurmalib.common.sound.SoundStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.BooleanSupplier;

/**
 * SoundCenter — єдина точка відтворення звуку бібліотеки (план, п.
 * 3.5, "звуковий центр"). Уся клієнтська логіка, яка в оригіналі
 * snipers_shaurma була розкидана по {@code PlaySoundPacket.ClientHandler}
 * (звук без позиції), {@code MenuSoundHelper}/{@code MenuSounds}
 * (UI-навігація), {@code AirdropPlaneSound} (рухоме джерело з ручним
 * розрахунком дистанції) — зведена в одну точку з єдиною моделлю
 * category-гучності через {@link SoundCategoryRegistry} +
 * {@link SoundCueRegistry}.
 * <p>
 * <b>Консюмер ніколи не будує {@code SimpleSoundInstance} сам.</b> Мод
 * реєструє свої {@code SoundEvent} (як і зараз, через власний
 * {@code DeferredRegister}) і опційно {@link SoundCue} (щоб прив'язати
 * звук до категорії-слайдера), а тоді викликає лише
 * {@link #play(String, SoundSource, float, float)} /
 * {@link #playAt} / {@link #trackMovingSource}. Це і є суть "бібліотека
 * керує звуковим центром, мод лише реєструє й каже що де" — жоден
 * консюмер (snipers, maniac) не пише власний
 * {@code SimpleSoundInstance}-конструктор вручну.
 * <p>
 * Не залежить від мережі напряму — {@link dev.shaurmalib.forge.network.packets.PlaySoundPacket}/
 * {@code StopSoundPacket} викликають ці статичні методи зі свого
 * {@code ClientHandler}, так само як консюмер може викликати їх напряму
 * на чисто клієнтських подіях (без мережі), якщо звук локальний.
 */
@OnlyIn(Dist.CLIENT)
public final class SoundCenter {

    private SoundCenter() {}

    // ── 1. Non-positional / positional-fixed відтворення за soundId ──────

    /**
     * Відтворює звук за його рядковим id, дивлячись у
     * {@link SoundCueRegistry} для визначення {@link SoundStage} і
     * category-гучності. Якщо {@code soundId} не зареєстрований —
     * поводиться як ванільний {@code SimpleSoundInstance} без категорії
     * мода (гучність 1.0, {@code Attenuation.NONE}, як в оригінальному
     * {@code PlaySoundPacket.ClientHandler} без запису в жодному
     * membership-списку).
     */
    public static void play(String soundId, SoundSource source, float volume, float pitch) {
        play(soundId, source, volume, pitch, null);
    }

    /**
     * Те саме, з опційним {@code suppressIf} — консюмер підключає сюди
     * власну перевірку "чи overlay вже грає цей звук сам" (у оригіналі:
     * countdown-звуки, які {@code GameStartAnnouncementOverlay}/
     * {@code CinematicCountdownOverlay} відтворюють локально, тому
     * серверний {@code PlaySoundPacket} для них треба ігнорувати —
     * бібліотека не знає імен цих overlay-класів, тому приймає
     * предикат замість хардкодженого списку).
     */
    public static void play(String soundId, SoundSource source, float volume, float pitch,
                             BooleanSupplier suppressIf) {
        if (suppressIf != null && suppressIf.getAsBoolean()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        SoundCue cue = SoundCueRegistry.find(soundId).orElse(null);
        float categoryVol = cue != null ? SoundCategoryRegistry.volumeOf(cue.category()) : 1.0f;
        if (categoryVol <= 0f) return;

        float finalVolume = volume * categoryVol;
        SoundEvent event = resolveEvent(soundId);

        SimpleSoundInstance instance = new SimpleSoundInstance(
                event.getLocation(), source, finalVolume, pitch,
                mc.level.random, false, 0,
                SoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0, true);
        mc.getSoundManager().play(instance);
    }

    /**
     * Позиційний звук у фіксованій точці 3D-простору
     * ({@link SoundStage#POSITIONAL_FIXED}) — на відміну від
     * {@link #play}, тут МК сам рахує затухання за
     * {@code Attenuation.LINEAR} по дистанції гравця до {@code x,y,z}.
     */
    public static void playAt(String soundId, SoundSource source, float volume, float pitch,
                               double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        SoundCue cue = SoundCueRegistry.find(soundId).orElse(null);
        float categoryVol = cue != null ? SoundCategoryRegistry.volumeOf(cue.category()) : 1.0f;
        if (categoryVol <= 0f) return;

        SoundEvent event = resolveEvent(soundId);
        SimpleSoundInstance instance = new SimpleSoundInstance(
                event, source, volume * categoryVol, pitch,
                mc.level.random, x, y, z);
        mc.getSoundManager().play(instance);
    }

    // ── 2. UI/menu — не-позиційний, forUI (план: MenuSoundHelper/MenuSounds) ──

    /**
     * UI-звук навігації (ховер/клік/підтвердження меню) —
     * {@code SimpleSoundInstance.forUI(...)}, узагальнення
     * {@code MenuSoundHelper}/{@code MenuSounds}. Працює навіть коли
     * {@code mc.player == null} (список серверів/головне меню) — на
     * відміну від {@link #play}, тут НЕ перевіряється {@code mc.level}.
     */
    public static void playUi(String soundId, float volume, float pitch) {
        try {
            Minecraft mc = Minecraft.getInstance();
            SoundCue cue = SoundCueRegistry.find(soundId).orElse(null);
            float categoryVol = cue != null ? SoundCategoryRegistry.volumeOf(cue.category()) : 1.0f;
            if (categoryVol <= 0f) return;

            SoundEvent event = resolveEvent(soundId);
            mc.getSoundManager().play(
                    SimpleSoundInstance.forUI(event, pitch, volume * categoryVol));
        } catch (Exception ignored) {
            // Оригінальна поведінка MenuSoundHelper/MenuSounds: звук не критичний
            // для функціоналу меню, тому мовчазний захист від NPE поза грою.
        }
    }

    // ── 3. Рухоме джерело (план: узагальнення AirdropPlaneSound) ─────────

    /**
     * Відтворює одну копію звуку рухомого джерела на поточній позиції
     * {@code x,y,z} з гучністю, що лінійно спадає до нуля на
     * {@code maxHearDistance} — 1:1 алгоритм {@code AirdropPlaneSound.computeVolume}.
     * <p>
     * Консюмер викликає це кожні {@code repeatTicks} тіків із власного
     * рендер/тік-хука (той самий патерн "нова коротка non-looping копія
     * замість одного looping instance", що в оригіналі) — бібліотека не
     * тримає власного тикаючого реєстру рухомих джерел, бо джерело
     * (сутність) належить консюмеру і вже має власний тік-хук.
     */
    public static void trackMovingSource(String soundId, SoundSource source,
                                          double x, double y, double z,
                                          float maxVolume, float maxHearDistance, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        double dx = x - mc.player.getX();
        double dy = y - mc.player.getY();
        double dz = z - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist >= maxHearDistance) return;

        SoundCue cue = SoundCueRegistry.find(soundId).orElse(null);
        float categoryVol = cue != null ? SoundCategoryRegistry.volumeOf(cue.category()) : 1.0f;
        if (categoryVol <= 0f) return;

        float t = (float) (dist / maxHearDistance);
        float volume = maxVolume * (1.0f - t) * categoryVol;
        if (volume <= 0.01f) return;

        SoundEvent event = resolveEvent(soundId);
        SimpleSoundInstance instance = new SimpleSoundInstance(
                event.getLocation(), source, volume, pitch,
                mc.level.random, false, 0,
                SoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0, true);
        mc.getSoundManager().play(instance);
    }

    // ── Спільне ────────────────────────────────────────────────────────

    private static SoundEvent resolveEvent(String soundId) {
        ResourceLocation rl = new ResourceLocation(soundId);
        SoundEvent event = ForgeRegistries.SOUND_EVENTS.getValue(rl);
        return event != null ? event : SoundEvent.createFixedRangeEvent(rl, 16.0f);
    }
}
