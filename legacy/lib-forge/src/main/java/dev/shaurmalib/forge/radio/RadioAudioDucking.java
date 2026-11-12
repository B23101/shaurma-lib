package dev.shaurmalib.forge.radio;

import dev.shaurmalib.forge.sound.SoundCategoryDucker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * RadioAudioDucking (план, п. 3.33) — узагальнення
 * {@code core.client.sound.RadioDialogSoundManager} snipers_shaurma.
 * Керує голосом диктора (з мережевого {@code soundId}), опційним
 * циклічним фоновим шумом рації, і приглушенням усіх інших категорій
 * через {@link SoundCategoryDucker} на час репліки.
 * <p>
 * <b>Дизайн, перенесений 1:1 з оригіналу:</b>
 * <ul>
 *   <li>жоден таймер тривалості звуку ніде не зберігається — єдине
 *       джерело правди "чи голос ще триває" — сам
 *       {@code SoundManager.isActive(...)} рушія Minecraft, опитуваний
 *       щотіку; якщо консюмер підмінить .ogg-файли на довші/коротші,
 *       код продовжить працювати без змін;</li>
 *   <li>шум рації (якщо переданий) стартує разом із голосом і
 *       перезапускається в циклі, поки голос активний — щойно голос
 *       замовкає, шум вимикається і один раз програється звук
 *       завершення (start/noise/end-звуки — опційні параметри {@code SoundEvent},
 *       {@code null} — модуль просто пропускає цей крок, бібліотека не
 *       вимагає наявності всіх трьох);</li>
 *   <li>ducking застосовується лише якщо {@code volumeProvider.getVolume() > 0}
 *       — немає сенсу приглушувати фон заради голосу, який гравець і
 *       так вимкнув власним слайдером.</li>
 * </ul>
 * <p>
 * Не викликає {@code SoundManager.reload()} (той обриває геть усі
 * активні звуки, включно з UI-екранами) — той самий інваріант, що і в
 * оригіналі.
 */
@OnlyIn(Dist.CLIENT)
public final class RadioAudioDucking {

    private static final String DUCK_TAG = "shaurma_lib_radio_dialog";

    /** Гучність фонового шуму рації відносно гучності голосу диктора. */
    public static float noiseVolumeRatio = 0.40f;

    private static RadioVoiceVolumeProvider volumeProvider = () -> 1.0f;
    private static SoundEvent startSound;
    private static SoundEvent noiseSoundEvent;
    private static SoundEvent endSound;
    private static float duckRatio = 0.30f;

    private static SimpleSoundInstance currentVoice = null;
    private static SimpleSoundInstance noiseSound = null;
    private static boolean noisePlaying = false;
    private static boolean endPlayed = false;
    private static boolean ducked = false;

    private RadioAudioDucking() {}

    /**
     * Одноразове налаштування модуля — типово викликається з
     * {@code RadioModuleHook.onAttach(...)}. Усі параметри опційні
     * (можуть бути {@code null}), окрім {@code volumeProvider}.
     */
    public static void configure(RadioVoiceVolumeProvider volumeProvider,
                                  SoundEvent startSound, SoundEvent noiseSoundEvent, SoundEvent endSound) {
        RadioAudioDucking.volumeProvider = volumeProvider != null ? volumeProvider : () -> 1.0f;
        RadioAudioDucking.startSound = startSound;
        RadioAudioDucking.noiseSoundEvent = noiseSoundEvent;
        RadioAudioDucking.endSound = endSound;
    }

    /** Оновлює duck-ratio (0..1) — типово з {@code settings.duck_ratio} у {@code radio_dialogs.yml}. */
    public static void setDuckRatio(float ratio) {
        duckRatio = ratio;
    }

    public static void play(String soundId) {
        stop();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        float vol = volumeProvider.getVolume();
        // Якщо гравець вимкнув голос диктора — фон приглушувати нема сенсу.
        if (vol <= 0f) return;

        duckBackground();
        endPlayed = false;
        noisePlaying = false;

        playOneShot(mc, startSound, vol);

        if (soundId != null && !soundId.isEmpty()) {
            try {
                String ns = "shaurma_lib", path = soundId;
                if (soundId.contains(":")) {
                    String[] p = soundId.split(":", 2);
                    ns = p[0];
                    path = p[1];
                }
                SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(ns, path));
                if (se != null) {
                    currentVoice = SimpleSoundInstance.forUI(se, 1.0f, vol);
                    mc.getSoundManager().play(currentVoice);
                    startNoise(mc, vol);
                    noisePlaying = true;
                }
            } catch (Exception ignored) {
                // Некоректний ID звуку — просто без голосу, шум/ducking усе одно активовано.
            }
        }
    }

    /** Опитувати щотіку (типово з {@code RadioDialogOverlay.tick()}). */
    public static void tick() {
        if (!noisePlaying) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        boolean voiceStillTalking = currentVoice != null && isActiveSafe(mc, currentVoice);

        if (voiceStillTalking) {
            if (noiseSound == null || !isActiveSafe(mc, noiseSound)) {
                startNoise(mc, volumeProvider.getVolume());
            }
        } else {
            stopNoise(mc);
            noisePlaying = false;
            if (!endPlayed) {
                endPlayed = true;
                playOneShot(mc, endSound, volumeProvider.getVolume());
            }
        }
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (currentVoice != null) {
            try { mc.getSoundManager().stop(currentVoice); } catch (Exception ignored) {}
            currentVoice = null;
        }
        stopNoise(mc);
        noisePlaying = false;
        endPlayed = false;
        if (ducked) restoreBackground();
    }

    public static boolean isPlaying() {
        if (currentVoice == null) return false;
        try { return Minecraft.getInstance().getSoundManager().isActive(currentVoice); }
        catch (Exception e) { return false; }
    }

    private static void startNoise(Minecraft mc, float voiceVolume) {
        if (noiseSoundEvent == null) return;
        noiseSound = SimpleSoundInstance.forUI(noiseSoundEvent, 1.0f, voiceVolume * noiseVolumeRatio);
        mc.getSoundManager().play(noiseSound);
    }

    private static void stopNoise(Minecraft mc) {
        if (noiseSound != null) {
            try { mc.getSoundManager().stop(noiseSound); } catch (Exception ignored) {}
            noiseSound = null;
        }
    }

    private static void playOneShot(Minecraft mc, SoundEvent event, float vol) {
        if (event == null) return;
        try {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f, vol));
        } catch (Exception ignored) {}
    }

    private static boolean isActiveSafe(Minecraft mc, SimpleSoundInstance instance) {
        try { return mc.getSoundManager().isActive(instance); }
        catch (Exception e) { return false; }
    }

    private static void duckBackground() {
        if (ducked) return;
        for (SoundSource src : SoundSource.values()) {
            if (src == SoundSource.MASTER) continue;
            SoundCategoryDucker.beginDuck(DUCK_TAG, src, duckRatio);
        }
        ducked = true;
    }

    private static void restoreBackground() {
        SoundCategoryDucker.endDuckAll(DUCK_TAG);
        ducked = false;
    }
}
